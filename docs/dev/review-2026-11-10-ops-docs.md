# 工程侧复查（配置 / 部署 / 文档 / 仓库卫生）

- 复查人：Team Lead（原 `review-ops` 的沙箱中途失去仓库访问，其结论未落盘，本报告**从零重新核实**，不采信任何转述）
- 日期：2026-11-10
- 范围：`pom.xml`、`.mvn/**`、`Dockerfile`、`compose*.yaml`、`.dockerignore`、`.gitignore`、`.gitattributes`、`deploy/**`、`README_CN.md`、`docs/**`
- 约束：未修改任何被审查文件；未运行 `mvn`；未输出任何密钥/口令值；未重启 18081；未改数据库

---

## 一、结论速览

| 级别 | 数量 | 条目 |
|---|---|---|
| [CRITICAL] | 1 | C-1 `APP_SECRET_KEY` 与 `admin-password` 有非空默认值（存量） |
| [MAJOR] | 3 | M-1 `frontend-maven-plugin` 无 skip → 离线 `mvn package` 必失败；M-2 CRLF 污染 86.6%；M-3 `compose.yaml` 硬编码 WSL 网关 IP |
| [MINOR] | 4 | m-1 `MYSQL_URL` 默认指向内网 IP；m-2 `.gitattributes` 覆盖面太窄；m-3 工具目录未忽略；m-4 文档与代码的历史漂移（本轮已修） |

**本轮改造本身没有引入工程侧缺陷**：所有 [CRITICAL]/[MAJOR] 条目均**先于本轮**存在（已用 `git show HEAD` + mtime + `git log` 三重确认）。

---

## 二、~~C-1~~ 已修复：主密钥与管理员口令的非空默认值（2026-11-10 17:20 收口）

**位置**：`src/main/resources/application.yaml:137`、`:140`

```yaml
136:    admin-username: ${ADMIN_USERNAME:admin}
137:    admin-password: ${ADMIN_PASSWORD:<非空默认值>}
140:    secret-key: ${APP_SECRET_KEY:<非空默认值>}
```

**我怎么知道的**：
- `grep -nE "admin-password|secret-key" src/main/resources/application.yaml` → 两行都带 `${VAR:默认值}` 形态且默认值非空（值本身不打印）。
- `git diff --ignore-cr-at-eol src/main/resources/application.yaml` → **输出为空**，即本轮未改。
- `git show HEAD:src/main/resources/application.yaml | grep -nE "admin-password|secret-key"` → `:137`、`:140` 两行**在 HEAD 中已存在**，同样带非空默认值。

⇒ **这是存量问题，不是本轮引入。**

**暴露面（已补验，重要）**：
`compose.yaml:14-15` 用的是
```yaml
      ADMIN_PASSWORD: ${ADMIN_PASSWORD:?ADMIN_PASSWORD is required}
      APP_SECRET_KEY: ${APP_SECRET_KEY:?APP_SECRET_KEY is required}
```
`:?` 是 **「缺失即报错退出」** 语法 ⇒ **走 Docker Compose 的部署不会落到这两个默认值**，忘记注入会直接起不来。
`deploy/env/*.env.example` 四个文件（dev/native/prod/staging）也都列出这两项为必填占位。

⇒ 因此 C-1 的**实际暴露面只有「裸 `java -jar` / `spring-boot:run` 且未注入环境变量」这一条路径**
（正好是我做升级演练时的启动方式）。**严重度由 CRITICAL 降为 MAJOR**：默认值仍应清掉（防止将来有人绕过 compose、
或把 jar 直接扔到服务器上跑），但它不是「默认部署就裸奔」。

**影响（按降级后的口径）**：
1. `APP_SECRET_KEY` 是 AES-GCM **主密钥**。它一旦是众所周知的默认值，库里全部 `api_key_encrypted`
   （所有 LLM/图片 API Key）与微信 AppSecret 的密文都可被**离线解密**——拿到库备份的人不需要任何其他东西。
2. `admin-password` 默认非空 ⇒ 全新部署若忘记注入，会得到一个用**公开默认口令**可登录的管理员
   （`BootstrapAdminRunner` 只在无管理员时创建，且仅在日志里提示改密码）。
3. 二者都**不报错**：应用正常启动、功能正常，问题只在「被利用」时才显形。

**本轮已实测的佐证**：升级演练时我造的空库启动后，日志出现
`已创建初始管理员账号 admin，请登录后尽快修改默认密码` —— 证明这条路径真实可达。

**已修复（用户 2026-11-10 批准后实施）**：

| 文件 | 改动 |
|---|---|
| `src/main/resources/application.yaml:141` | `admin-password: ${ADMIN_PASSWORD:}`（默认值置空，附说明注释） |
| `src/main/resources/application.yaml:151` | `secret-key: ${APP_SECRET_KEY:}`（默认值置空，附说明注释） |
| `common/CryptoService.java:19-28` | 构造即校验：`secret == null \|\| secret.isBlank()` → 抛 `IllegalStateException` **fail-fast** |
| `auth/BootstrapAdminRunner.java:36-42` | 口令为空时**不创建**管理员，ERROR 日志说明「注入 ADMIN_PASSWORD 并重启前无法登录」；成功创建的日志由「请尽快修改默认密码」改为「已创建（口令来自 ADMIN_PASSWORD）」，因为不再有默认口令可改 |

**验证**：

1. 新增单测 `CryptoServiceSecretRequiredTest` **3/3**（空串 / 纯空白 / null 三种都必须抛、且消息含 `APP_SECRET_KEY`）。
2. 新增单测 `BootstrapAdminRunnerTest` **3/3**：口令为 `""`/`"   "`/`null` 时 `insert` 与 `encode` **一次都不被调用**；正常口令仍创建；已有用户时不重复创建。
3. **集成实证 fail-fast 真的生效**：以 `--app.security.secret-key= `（显式空值）启动 →
   `Caused by: BeanInstantiationException: Failed to instantiate [CryptoService]` /
   `at CryptoService.<init>(CryptoService.java:25)` → `BUILD FAILURE`，
   日志中 `Started WechatArticleBotApplication` 出现 **0 次**。⇒ 忘记注入的部署**起不来**，不会带弱密钥起来。
4. 全量回归 **636 例 0 失败 / 73 类**（改前 621/71；含 C-1 的 6 例，以及图片复选框改造的回归）。

**顺带发现（新，值得记）**：`application.yaml:3` 有
```yaml
  config:
    import: optional:file:.env[.properties]
```
⇒ 应用会**自动加载工作目录下的 `.env`**。所以本机上即使 `unset APP_SECRET_KEY` 也照样能启动
（密钥从 `.env` 来），我第一次的「不注入即失败」试验正是被它挡住的。
`.env` 已被 `.gitignore` 忽略（§五已证），所以这不是泄漏；但**运维须知**：
「没设环境变量」≠「没配密钥」——排查 C-1 类问题时必须连 `.env` 一起确认。

---

## 三、[MAJOR]

### M-1：`frontend-maven-plugin` 没有 skip 开关，离线环境 `mvn package` 必失败

**位置**：`pom.xml:31-34`（版本属性）、`:111-115`（插件配置）

**我怎么知道的**：
- `grep -n -A3 "frontend-maven-plugin" pom.xml` → 配了 `workingDirectory=webui`、`installDirectory=${project.build.directory}/frontend`、`node.version`/`npm.version`。
- `grep -n "skip" pom.xml` → **零命中**，即整个 pom 没有任何 skip 属性接线。

**影响**：
- 插件的默认行为是**下载**指定版本的 node 与 npm 到 `target/frontend`。离线环境下无法下载 ⇒
  `mvn package` / `mvn install` 直接失败。本机之所以能跑测试，是因为只用了 `mvn test`（不触发该插件的执行阶段）。
- 实际后果：**本机永远无法用 Maven 产出完整可运行包**，前端必须先手工
  `cd webui && npm run build -- --outDir ../target/classes/static --emptyOutDir` 再打 jar。
  这个隐式流程没有写在 README 里（本轮已在 README 明确），新人必然踩。

**建议修法**：加 `<skip>${skip.frontend}</skip>` 属性（默认 `false`），离线/CI 分离构建时 `-Dskip.frontend=true`；
或把该插件绑定到 `package` 之外由 Dockerfile 内的多阶段构建承担（`Dockerfile:7` 已有 `FROM ${NODE_IMAGE} AS frontend`，说明镜像构建路径本来就走前端阶段，Maven 侧其实**可以**跳过）。

### M-2：CRLF 污染 —— 209 个「已修改」里只有 31 个是真改动（85.2% 噪音）

**我怎么知道的**（口径很重要，我第一次也算错了）：
- `git status --porcelain | grep -c '^ M'` → **209**
- `git diff --ignore-cr-at-eol --numstat | wc -l` → **31**
- 差值 **178** 个文件**只有行尾变化**（占 85.2%）。

**⚠ 口径陷阱（我本人踩过，记录给后来人）**：`--ignore-cr-at-eol` 只影响 `--stat`/`--numstat`，
**不影响 `--name-only`**。实测同一时刻：
```
git diff --ignore-cr-at-eol --name-only | wc -l   → 209   ← 等于没过滤
git diff --ignore-cr-at-eol --numstat  | wc -l   →  31   ← 真实改动
```
⇒ **要量化噪音只能用 `--numstat`**。用 `--name-only` 会得出「209 个都是真改动」的错误结论。

**影响**：
- `git diff` 对任何一次代码复查几乎不可读；`git stash`/`cherry-pick`/`merge` 极易产生大面积假冲突。
- `git blame` 被整文件重写冲刷，责任人追溯失效。
- 本轮所有复查都必须显式带 `--ignore-cr-at-eol` 或 `--numstat`，这本身就是负担。

**建议修法**：在 `.gitattributes` 增加 `* text=auto` 并对文本类型显式 `eol=lf`
（`*.java`/`*.vue`/`*.js`/`*.md`/`*.yml`/`*.xml`），然后**单独一个提交**做行尾归一化（`git add --renormalize .`）。
**必须与业务改动分开提交**，否则这次归一化会污染本轮的 diff。

**当前阻塞（2026-11-10 实测）**：本仓库 **`git config user.name` / `user.email` 均为空**
（`--local` 与 `--global` 都无 `user.*` 配置）。⇒ **任何提交都无法执行**，归一化提交因此**未做**。
前置动作需由用户决定（谁作为作者），命令：
```bash
git config user.name "<你的名字>"
git config user.email "<你的邮箱>"
```
之后按以下三步执行（**业务改动必须先提交**，否则会被卷进同一次提交）：
```bash
# 1) 先提交本轮业务改动（真实内容 31 个文件）
git add -A && git commit -m "feat: 模型档案第二轮（卡片布局/模型发现/设置页下线/两跳图片）"
# 2) 补行尾规则
#    .gitattributes 追加:  * text=auto   +  *.{java,vue,js,mjs,md,yml,yaml,xml,json,css,html} text eol=lf
# 3) 归一化作为独立提交（可用 `git diff --stat` 确认无内容变更）
git add --renormalize . && git commit -m "chore: 归一化行尾 CRLF→LF（无内容变更，179 文件）"
```
**未执行原因**：本次会话的既定约束是「未经允许不做 git 提交」；用户虽批准了「另开一轮」，
但作者身份仍需其本人确认，故只把步骤备好。

### M-3：`compose.yaml` 硬编码 WSL 网关 IP

**位置**：`compose.yaml:24-25`

```yaml
    extra_hosts:
      - "host.docker.internal:${WSL_HOST_GATEWAY:-172.21.192.1}"
```

**我怎么知道的（三重判定「是不是本轮产物」）**：
1. mtime：`ls -la compose.yaml` → **Nov 4 16:17**（本轮工作在 **Nov 10**）
2. `git log -1 --date=short -- compose.yaml` → 最后提交 **2026-09-10**（`0ca6a8f`），说明工作区改动一直未提交
3. `git diff --ignore-cr-at-eol compose.yaml` → 7 行改动：端口绑定 `0.0.0.0`→`127.0.0.1`（更安全，**正面**）+ 上述 `extra_hosts`

⇒ **不是本轮产物**，是 11-04 遗留的本地改动。

**影响**：
- `172.21.192.1` 是**某一台** WSL2 实例的网关地址，换机器/重启 WSL 后网关可能变成其它网段 ⇒ 容器里的
  `host.docker.internal` 指向一个不存在的地址，且**不报错**（只是连不上模型服务）。
- 该默认值会随仓库传播到别人的机器上，属于「在我这儿能跑」的典型隐患。

**建议修法**：去掉硬编码默认值，改成 `${WSL_HOST_GATEWAY:?请设置 WSL_HOST_GATEWAY}`（缺失即报错）；
或改由 `deploy/env/*.env` 注入、`.env.example` 里给出「如何取网关」的命令。
**注意**：端口绑定改成 `127.0.0.1` 那半**不要回退**，它是安全改进。

---

## 四、[MINOR]

### m-1：`MYSQL_URL` 默认值指向一个内网 IP

`application.yaml:7` 的默认值是 `jdbc:mysql://<一个内网地址>:3306/wechat-article?...`。属存量（HEAD 同）：
在没有注入 `ENV.MYSQL_URL` 时，应用会尝试连一个对绝大多数人不可达的地址，报错信息是连接超时而非「你没配数据库」。
建议默认值改为 `localhost` 或直接置空 + fail-fast。**不打印该 IP 的实际值**。

### m-2：`.gitattributes` 覆盖面太窄

现有仅 3 行：`/mvnw text eol=lf`、`/scripts/*.sh text eol=lf`、`*.cmd text eol=crlf`。
**没有任何规则约束 `*.java`/`*.vue`/`*.js`/`*.md`/`*.yml`** —— 这正是 M-2 的成因。修 M-2 时一并补。

### m-3：工具目录未忽略

`.claude/`（`settings.local.json`）与 `.zcode/`（`cmdline.txt`、`jstack-u1.txt`、`workflow-*.ts`、`workflow-runs/` 约 560 KB）
均**未被 ignore**（`git check-ignore -v` 无命中），`git status` 里是 `??` 未跟踪。属工具副产物，非源码。
建议加入 `.gitignore`。

### m-4：文档与代码的漂移（本轮已修）

- `README_CN.md` 的「第二步」原本仍写「系统设置 → LLM 服务仍然可用」「图片模型三件套仍在系统设置里维护」——
  与 §4 下线设置页入口**直接矛盾**。本轮已整节改写为「模型档案」流程，并点明「系统设置里已没有 LLM 配置入口」。
- 顺带修正两处措辞：协议显示名统一为 `Responses / Codex`（与代码 `MODEL_PROVIDERS` 一致）；
  `normalizeBaseUrl` 只剥 `/v1/chat/completions`、`/v1/responses`、`/v1/messages`、`/v1` **四种**后缀，
  旧文案「不要包含 /v1、/responses、/chat/completions」会被读成「任意后缀都会自动剥离」，已改为明确列出四种。
- `docs/images/settings.png` 是旧版 UI：已在 README 的引用处加注「图示为旧版」。

---

## 五、已核实为正常

| 项 | 证据 |
|---|---|
| 敏感文件确实被忽略 | `git check-ignore -v .env` → `.gitignore:3:.env`；`git check-ignore -v deploy/env/dev.env` → `.gitignore:10:deploy/env/*.env`。**两者都命中** |
| `.env.bak-*` 类备份不会入库 | `.gitignore` 有 `.env.*` + `!.env.example`，另有 `deploy/env/*.bak*`（第一轮补的） |
| Dockerfile **非 root** 运行 | `Dockerfile:39 USER 10001:10001` |
| 「无幽灵 surefire 报告」 | 本次全量前执行了 `rm -rf target/surefire-reports`；运行后 71 份报告 mtime 全部落在 **16:30–16:39** 区间内，最早=16:30 与最晚=16:39，**不存在早于本次运行的残留** |
| `.dockerignore` 存在 | 140 字节（Sep 7），非本轮改动 |
| `compose.yaml` 端口绑定收紧 | `0.0.0.0`→`127.0.0.1` 是安全改进，**不应回退** |
| 本轮源码改动量 | `--numstat`（带 CR 过滤）**31** 个文件；其中前端 6 个（`ModelProfilesView` 541+/75−、`check-profile-chain-parity.mjs` 150+/8−、`style.css` 68+/4−、`AgentsView` 68+/11−、`profiles.js` 35+/4−、`SettingsView` 7+/27−） |

---

## 六、升级 / 回滚清单（可直接照做）

### 升级（老库 → 本轮版本）

1. **备份数据库**（必须）：`mysqldump` 全库。`llm_profile` 的 15 个旧列一条都不会被改，但备份是唯一退路。
2. **确认 `APP_SECRET_KEY` 与库中密文匹配**：保持原值不动。若换了值，读路径**不会 500**
   （已修 B3），但所有密钥会显示「无法解密（请检查 APP_SECRET_KEY）」，且回填会跳过这些行。
3. **直接启动即可**，无需手工 DDL：
   - smart-mybatis 会创建 `LLM_PROVIDER` 表、给 `LLM_PROFILE` 补 `PROVIDER_ID bigint NULL`
   - **实测**：旧形态库（29 表、无 `LLM_PROVIDER`、无 `PROVIDER_ID`）上启动 → 表自动建、列自动补、
     原有 15 列**类型与宽度逐字未变**、4 条存量数据完好、启动成功（12.6s）
4. **看启动日志的回填汇总**：
   - 「无待处理行」= 全部已归位，正常
   - 「N 条因端点或密钥无法解密被跳过回填」= `APP_SECRET_KEY` 不匹配，修好后**下次启动自动重试**，不丢数据
5. **验证两个只读接口返回 200**：`GET /api/llm-profiles`、`GET /api/settings/llm`（修复前坏密钥下都是 500）。
6. **不要**把 `LLM_PROVIDER.PURPOSE` 改成 NOT NULL：它是短暂实现期的遗留列（实体已不映射），
   存量库补列后老行只能是 NULL，列一旦 NOT NULL **升级当天直接起不来**。

### 回滚（本轮版本 → 上一版本）

1. `llm_config` 的 LLM 四件套与图片三件套**一直在写**（`syncDefaultFromConfig` 保留），所以回滚后设置页能正常读到旧值。
2. `GET/PUT /api/settings/llm` **未下线**（实测 200、13 字段含图片三件套），旧脚本与旧前端仍可用。
3. `llm_profile` 的三个旧列（`provider`/`base_url`/`api_key_encrypted`）**不删不清**，回滚后旧代码照常读。
4. **回滚后会静默降级**（不报错，但行为变了）：`llm_provider` 表与其绑定不再被读 → 所有档案回到「各读各的旧列」，
   第一轮消除的「同一端点密钥重复录入」问题会**重新出现**（数据没丢，只是界面又变回逐条填）。
5. 新增的 `LLM_PROVIDER` 表与 `LLM_PROFILE.PROVIDER_ID` 列留在库里无害（旧代码不读未知列）。

---

## 七、未验证项

1. **Docker 构建与运行**：本轮未执行 `docker build`/`compose up`（会与用户正在看的 18081 实例抢资源，且需要外网拉 node 镜像）。
   `Dockerfile` 的多阶段、`USER 10001:10001`、`HEALTHCHECK` 的注释只做了**静态**核对，未实测。
2. **CRLF 归一化的实际影响面**：只统计了「当前工作区 vs HEAD」的行尾差异，未评估一次 `--renormalize` 提交会产生多大的 diff（预估 181 文件，未实测）。
3. **`deploy/env/*.env.example` 是否已把两项标为必填**：只核对了 `.gitignore` 命中关系，未逐个读 example 文件的字段说明。
4. ~~生产部署是否真的会走到默认凭据~~ **已补验**：`compose.yaml:14-15` 对 `ADMIN_PASSWORD`/`APP_SECRET_KEY`
   均用 `${VAR:?...}`（缺失即报错），故 Docker 路径不会走到默认值；暴露面仅剩裸 `java -jar`/`spring-boot:run` 且未注环境变量。
   详见 §二「暴露面」。

---

## 八、给用户的建议顺序

1. **C-1**（默认密钥/口令置空 + fail-fast）—— Docker 路径已被 compose 的 `:?` 阻断，但裸跑路径仍裸奔；改动小，建议做。
2. **M-2 + m-2**（`.gitattributes` 补规则 + 单独一次行尾归一化提交）—— 不做的话，后续每次复查都要跟 181 个假改动搏斗。
3. **M-1**（`frontend-maven-plugin` 加 skip）—— 影响构建可复现性。
4. **M-3**（去掉硬编码网关默认值）、**m-1**（内网 IP 默认值）、**m-3**（工具目录忽略）。
