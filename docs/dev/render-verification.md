# 渲染验收复现手册（第十二轮新增；第十三轮**按本文实跑验证并修订**；第十四轮**脚本已入库，路径全面更新**）

> **这份文档解决什么问题**：前面十一轮的验证 harness、截图、汇总表全部落在 `target/probe/`，
> 而 `target/` 被 `.gitignore:2` 命中——**换一个人、或换一个会话，这些证据一个都拿不到**。
> 本文把「谁在验、验什么、怎么跑、跑出来应该是什么样」写进受版本控制的文档，
> 目标是一条硬标准：**一个没参与过前几轮的人，照本文能独立重跑出与当前一致的结论。**

> ✅ **第十四轮已把探针脚本搬进受版本控制的 `tools/render-verify/`，并照本文逐条重跑验证过。**
> 现在的分工是：**脚本与输入入库（`tools/render-verify/`），产物留在 gitignored 的 `target/probe/`**。
> 第三节的命令**全部换成新路径**，实测结果与第十三轮逐条相符（见 §3.10）。
> 第十三轮那条「只 clone 仓库的人跑不了」的结论**已经作废**——见下面的「零」。

- 适用对象：需要对「**所有组件在后端 API 与编辑器前端两条路径都渲染正确**」这条验收标准做复核的人。
- 结论本身（不跑命令也想看结果）：见 `docs/render-acceptance-report.md`（面向用户）与
  `docs/dev/known-issues-handoff.md` §3.9–§3.18（面向工程师，含每轮的原始数字）。
- 本文的命令/**不含任何生产数据写入**，全部只读：只调外部渲染 API、只读 MySQL、只在本地起临时静态服务与 Chrome。

---

## 零、`git clone` 下来能跑到什么程度 —— 先读这一节

第十三轮的实测方法：`git clone <本仓库> /tmp/clone`，然后**逐字照抄本文第三节的命令**。
**那一轮结论是「一支套件都跑不了」；第十四轮把脚本入库之后，这条已经不成立；
第十五轮真的用干净 clone 重跑了一遍，逐条数字见 §3.11①。**

| 你手上是什么 | 能复现什么 |
| --- | --- |
| **刚 clone 下来**（无 `target/`、无 `.env`、无 `node_modules`） | **脚本、样例清单、组件全集基准、引擎包存档都在**（`tools/render-verify/`）。按第一节补齐 3 项环境后，第三节的生成类套件（M/C/A/R）**可直接跑通**；浏览器套件要先跑构建（§3.3） |
| 只差 `.env`、`node_modules`、渲染令牌 | 补完即可跑**全部 10 类套件** |
| 需要**历史逐轮数字**而不是重跑 | 那些数字抄在 `docs/dev/known-issues-handoff.md` §3.9–§3.18；原始 JSON/截图仍在 `target/probe/`，但**不在版本控制里**，clone 拿不到 |

**仍然缺、且注定要自己补的（都不是脚本问题）**：

| 缺什么 | 影响的套件 | 怎么补 |
| --- | --- | --- |
| **① 渲染令牌** `~/.zcode/secrets/markflow-render-token` | 一切要打渲染 API 的套件（M/C/A/R/X） | 向渲染服务方索取（**不入库**，永远不进提交） |
| **② `webui/node_modules`** | W 构建、以及一切要编译探针 dist 的浏览器套件 | `(cd webui && npm ci)` |
| **③ `.env`**（含 `ENV.MYSQL_TEST_URL`） | G 后端全量测试 | 按 `src/main/resources/application.yaml` 里的 `${ENV.*}` 占位符补齐（**不入库**） |
| **④ 历史产物**（截图 196 张、样例 HTML、逐轮 JSON） | 只是「复查旧结论」时才需要 | 重跑第三节即可再产出；**不建议入库**（体积大且每次可重算） |

> **诚实结论（第十四轮更新）**：**「照文档能独立重跑」现在对「只 clone 了仓库的人」也成立**——
> 前提是按第一节补齐令牌、`node_modules`、`.env` 这三项环境。
> 第十三轮实测过的那条硬缺口（`target/probe/` 里 `git ls-files` = 0 个文件）已经关掉：
> 现在 `git ls-files tools/render-verify` 有脚本与 `spec/` 输入清单。

---

## 一、前置条件（逐项都要满足，附自检命令）

> 这一节是**在已经有完整工作树的机器上**的自检；clone 场景同样适用（第 7～9 条是 clone 后必补项）。
> 7 条自检命令第十三轮**逐条照抄执行过，全部可用**；第 9 条的判定在第十四轮已改写。

| # | 条件 | 为什么需要 | 自检命令 | 期望 |
| --- | --- | --- | --- | --- |
| 1 | Docker + 容器 `momo-mysql-dev` 在跑 | 真实稿件套件、以及任何要读库的核对 | `docker ps --format '{{.Names}}' \| grep momo-mysql-dev` | 输出 `momo-mysql-dev` |
| 2 | 应用在 `127.0.0.1:8081` 在跑 | **只有真实稿件套件**需要（`run-article.mjs` 走真实 SPA + 真实 API） | `curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8081/` | `200` |
| 3 | Chrome（本机 138.x） | 所有浏览器套件；`cdp.mjs` 自动在 4 个候选路径里找 | `ls "C:/Program Files/Google/Chrome/Application/chrome.exe"` | 文件存在 |
| 4 | Node ≥ 24 | `cdp.mjs` 用 Node 24 自带的全局 `WebSocket` + `fetch` 直连 CDP，**不装 playwright/puppeteer** | `node -v` | `v24.x` |
| 5 | Python 3（**建议**设 `PYTHONIOENCODING=utf-8`） | 终端是 GBK：探针脚本的 print 里带中文**值**时（如 `round11_crosscheck.py` 打印判定词）会输出乱码；`python -c` 内联打印中文可能直接 `UnicodeEncodeError`。**脚本本身不会崩，落盘文件始终是 UTF-8** | `python -V` | `Python 3.x` |
| 6 | 渲染令牌文件 `~/.zcode/secrets/markflow-render-token` | 调 `POST https://www.bx9y.com.cn/__markflow_render`；**令牌只从文件读，不进任何产物** | `test -f ~/.zcode/secrets/markflow-render-token && echo OK` | `OK` |
| 7 | `webui/node_modules` 已装 | 探针 dist 编译时要解析 `@tiptap/*`（clone 场景先 `npm ci`） | `ls -d webui/node_modules` | 目录存在 |
| 8 | 仓库根目录下 `.env` 存在 | **只有 G（后端全量测试）需要**；缺它会 `Could not resolve placeholder 'ENV.MYSQL_TEST_URL'` | `test -f .env && echo OK` | `OK` |
| 9 | `tools/render-verify/` 里有脚本（**第十四轮起随仓库分发，不用再自己补**） | 除 G/W 外的**全部**探针套件 | `ls tools/render-verify/spec/component_registry.json` | 文件存在 |

**不需要**：不需要外网可访问除 `www.bx9y.com.cn` 以外的站点；不需要装任何新依赖（这是历轮的硬约束）。

> ⚠️ 前置条件 2（8081）只服务真实稿件套件。应用在 8081 上跑着**本身也会触发副作用**：
> 启动时 `SkillSeeder` 会按 `builtin_key` upsert 技能文案。
> 这正是「技能文案未落库」缺口（§八 第 7 条）的关闭动作——**第十四轮已重启过一次并复核通过**：
> 库里 `SKILL.CONTENT` 的 `CHAR_LENGTH` 已是 **6649**、与源码一致，`UPDATED_AT` = `2026-09-13 20:36:15`。
> **该缺口已关闭**，不再是复现失败项。


---

## 二、套件总览

| # | 套件 | 回答什么问题 | 需要 8081？ | 数据来源 | 产物 |
| --- | --- | --- | --- | --- | --- |
| G | 后端全量测试 | 代码层门禁（含本轮新增的只读文案钉子） | 否 | Maven | `target/probe/round12_tests.log` |
| W | `webui` 构建 | 编辑器前端能编译、产物可用 | 否 | npm | `target/probe/round12_webui_build.log` |
| M | 79 个最小样例（后端产物） | 每个可写语法，渲染服务认不认 | 否 | 外部渲染 API | `target/probe/components/*.html` + `component_matrix.json` |
| C | 17 个组合用例（后端产物） | 嵌套/重复/超长/混排之后还认不认 | 否 | 外部渲染 API | `target/probe/combos/*` |
| A | 10 条替代写法（后端产物） | 「等上游」那几条改写法能不能绕开 | 否 | 外部渲染 API | `target/probe/alt/*` |
| R | 38 个 `layout-*` × 2 写法 = 76 组（后端产物） | 注册表整族到底有没有实现 | 否 | 外部渲染 API | `target/probe/registry/*` |
| M′/C′/A′/R′ | 上述四套在**真实 Chrome** 里逐行判定 | 同一份产物灌进编辑器往返后还在不在 | 否 | 本机 Chrome + 探针 dist | `browser/*_result.json`、`shots/<set>/*.png` |
| P | 终稿对照表 | 63 个注册 ID 逐格 → 「两路径通过 / 等上游 / 悬空」 | 否 | 上述全部 JSON | `target/probe/round10_component_paths.md/.json` |
| X | 独立交叉验证 | 用**另一条通道**复核「38 个 `layout-*` 上游没实现」 | 否 | 外部渲染 API + 引擎包 | `target/probe/round11_crosscheck.txt/.json` |
| S | 真实稿件套件 | 公式/轮播/在库稿在**真实 SPA** 里画没画出来 | **是** | 8081 + MySQL + `webui/dist` | `browser/articles_result.json`、`shots/articles/*.png` |
| L | 活体前端套件（第十四轮新增） | 应用**实际对外提供**的那份前端画没画出来（与 S 量的是两份不同构建） | **是** | 8081 + `target/classes/static` | `browser/live_app_result.json`、`shots/live/*.png` |
| U | 第十六轮：用户逐条标注的 11 条 | 每条语法**原样**打一次渲染 API，再灌进真实编辑器，逐条判定「渲染服务还是本项目编辑器」 | 否 | 外部渲染 API + 本机 Chrome | `target/probe/r16/*`、`browser/r16_result.json`、`shots/r16/zoom/*.png` |
| U′ | 第二十四轮：在**用户自己的文章 #38** 上逐条复验 | 那 8 条批注的症状**现在还在不在**（同一篇文章 / 同一窗口宽度 / 改前 bundle 与改后 bundle 各跑一遍 / 与后端产物同尺子对三列） | **是**（要 8081 起着） | 8081 + 本机 Chrome | `browser/r24_article38_*_result.json`、`browser/r24_inject_*_result.json`、`shots/r24-article38/<label>/` |
| U″ | 第二十五轮：**保存出口幂等性**（用户原话「看到的样子保存后还在不在」） | 拦下的 `PUT` 里的 `body.contentHtml` **原样**灌回，量**同一组 11 条探针**并与拦截那一刻的活 DOM 逐条对照；三条输入路径各一遍 | **是**（要 8081 起着） | 8081 + 本机 Chrome | `browser/r25_roundtrip_*_result.json`、`browser/r25_*_saveexit.html`、`browser/r25_dom_*/`、`r25_whitespace_probe.json`、`r25_preservewhitespace_trial.json` |
| U‴ | 第二十五轮：**存量影响面**（只读） | 库里还有多少篇正文带着旧往返指纹 / 缺声明；「重新渲染存量」这条路的前置条件满足几篇 | 否 | MySQL（**全程只读**） | `browser/r25_stock_scan.json` |

> U 与 M/C/A/R 的区别：M/C/A/R 的输入是**我们自己按 guide / bundle spec 造的样例**，
> U 的输入是**用户在实际编辑器里写的原文**（原样照录，不改写）——所以它同时是「用户报障的取证套件」。
> 判定口径与假阳性清单见 `known-issues-handoff.md` §3.20。

判定的三层证据强度（术语沿用第十轮）：
**A 级 = 真实稿件里真的写过 + 那篇在真实 SPA 里回归过**；
**B 级 = 最小样例在真实浏览器里实测过**；
**C 级 = 只跑过 jsdom（当前为 0 条）**。任何结论只有 B 级而没有 A 级时，报告里必须标注。

---

## 三、逐条可复制命令（按顺序）

在仓库根目录 `D:\project\wwwroot\wechat-article-bot` 下依次执行。
带 ⏱ 的耗时是第十二轮实测值，仅供判断「是不是卡住了」。

### 3.1 代码层门禁

> ⚠️ 这两条会把日志写进 `target/probe/`。**全新 clone 里该目录不存在，重定向会先失败**
> （第十三轮踩到：`bash: target/probe/round12_tests.log: No such file or directory`）。
> 先建目录，或把日志写到别处：

```bash
mkdir -p target/probe            # ← 第十三轮补充：clone 场景必须先建

# G 后端全量测试（禁止与其他测试并行跑；需要一个可用的 .env）
./.mvn/mvn-local.sh -o test > target/probe/round12_tests.log 2>&1

# W 编辑器前端构建
(cd webui && npm run build) > target/probe/round12_webui_build.log 2>&1
```

**期望输出**

```
[INFO] Tests run: 308, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  02:45 min          # ⏱ 第十二轮实测 2:45
```

```
✓ built in 405ms                      # ⏱ 第十二轮实测 405ms
(!) Some chunks are larger than 500 kB ...   # 既有警告，非报错
```

若看到 `Could not resolve placeholder 'ENV.MYSQL_TEST_URL'` 并伴随大批 Error：
**不是代码回归，是缺 `.env`**（见第零节）。

### 3.2 后端产物：重新打一遍外部渲染 API

四步都只调 `POST https://www.bx9y.com.cn/__markflow_render`，**不落库**。建议加上 UTF-8 环境变量：

```bash
export PYTHONIOENCODING=utf-8

# M —— 79 个最小样例
python tools/render-verify/gen/component_matrix.py           # ⏱ 约 3 分钟

# C —— 17 个组合用例
python tools/render-verify/gen/round8_combos.py

# A —— 10 条替代写法
python tools/render-verify/gen/round10_alternatives.py

# R —— 38 个 layout-* × 2 种写法 = 76 组
python tools/render-verify/gen/round10_registry_closure.py
```

**元素形态透传判据（`gen/passthrough.py`，第三十七轮）**：一个**不联网、不写产物**的共享纯函数，
被 `round8_combos.py` 的 `stats()` 调用，用来把「非标准元素」按**形态**分成两类：

```bash
python tools/render-verify/gen/passthrough.py --selftest   # 8 条已知形态，期望全过、EXIT=0
```

| | 形态 | 含义 |
| --- | --- | --- |
| 合法占位 | **自闭合、无内容**：`<slider … />` | 上游有意交给前端水合的占位符 |
| 真残留 | **成对、包着可见文字**：`<layout-hero>文字</layout-hero>` | 语法没被消费，原样透传 |

**为什么不用「元素名白名单」**（第 10 条原设想）：实测按「非 HTML5/SVG/MathML 即透传」在全量 552 份
产物上命中 44 份，其中 41 份是 `registry/tag-layout-*`——而这些样例的 `expect` 原文就是
「产物里必然留着字面语法」。收益 0、噪声 +44。且 `<slider … />`（合法）与 `<layout-hero>…</layout-hero>`
（残留）**都在 `component_registry.json` 里**，按名字无法区分。详见 `known-issues-handoff.md` §八 第 10 条。

> ⚠️ **它只作线索，不进判定**：判据回答 **Q1「语法被消费了吗」**（事实，形态可判），
> 不回答 **Q2「这算不算缺陷」**（取决于用例声明的 `must`/`mustHtml`）。
> 边界：「自闭合 ⇒ 占位」现有语料只有 `slider` 一个正例（n=1）；「包文本 ⇒ 透传」零反例但同理可能误报。

**期望输出**：`component_matrix.py` 打 79 行 `len=… warn=… miss=- svg=… katex=…`，
末尾 `wrote target/probe/component_matrix.json (+ 79 rows)`；校验：

```bash
python -c "import json,io; r=json.load(io.open('target/probe/component_matrix.json',encoding='utf-8')); print(len(r), sum(1 for x in r if x.get('error')), sum(len(x.get('warnings') or []) for x in r))"
# 期望：79 0 1     ← 79 条、0 条异常、1 条 warning（attr-compare-marker-cn，上游行为，已归类）
```

```bash
python -c "import glob; print([ (d,len(glob.glob('target/probe/%s/*.html'%d))) for d in ('combos','alt','registry')])"
# 期望：[('combos', 17), ('alt', 10), ('registry', 76)]
```

### 3.3 编译探针 dist（浏览器套件的前提）

```bash
(cd webui && npx vite build --config ../tools/render-verify/browser/vite.config.mjs)
# 产物：target/probe/browser/dist/{probe.html,probe_all.html,...}
```

### 3.4 真实浏览器套件（**必须串行**，抢占同一个 CDP 端口 9333）

**一条一条跑，不要并排贴进同一个终端**（第十三轮提醒：下面这一整块如果并行执行会互相踢掉 9333 端口）：

```bash
node tools/render-verify/browser/run-all-browser.mjs          # 79 样例
```

```bash
node tools/render-verify/browser/run-combo-browser.mjs        # 17 组合
```

```bash
node tools/render-verify/browser/run-set-browser.mjs alt      # 10 替代写法
```

```bash
node tools/render-verify/browser/run-set-browser.mjs registry # 76 组 layout-*
```

```bash
node tools/render-verify/browser/run-article.mjs              # 真实稿件（需 8081 在跑）
```

```bash
node tools/render-verify/browser/verify-live-app.mjs 43 44    # 活体前端（需 8081 在跑，见 §3.10②）
```

> `run-article.mjs` 只做 `POST /api/auth/login` + `GET /api/articles/{id}`，**不写库**；
> 它读的 14 篇稿件是库里既有数据，不会新建或修改任何文章。
>
> ⚠️ 这两支**量的不是同一份前端**：`run-article.mjs` 静态服务端的是 `webui/dist`（最新构建），
> `verify-live-app.mjs` 直接打开 `http://127.0.0.1:8081`（应用实际提供的那份）。
> 改了编辑器扩展之后必须跑后者，否则会得到「探针全绿、用户打开是坏的」——第十四轮的翻车原因，见 §3.10②。

每个脚本会起一个临时静态服务 + 无头 Chrome，逐行截图，截图本身就是断言的一部分。

#### 3.4.1 探针浏览器的收尾办法（第二十九轮补进手册）

**一句话：本次启动专用 `--user-data-dir` → 退出时全机匹配这个路径收进程；下次启动前先扫一遍残留。**

`target/probe/browser/cdp.mjs` 里的三步（这是**唯一**的权威实现，照抄即可）：

1. **起**：每次 `launchBrowser()` 都用 `mkdtempSync(join(tmpdir(), 'probe-chrome-'))` 新造一个
   user-data-dir，并通过 `--user-data-dir=` 传给它。前缀 `probe-chrome-` 就是**本支的身份证**。
2. **收**：`close()` → `killTree()` 按这个**本次专用路径**全机匹配后 `Stop-Process -Force`。
   ⚠️ **不能拿 `spawn()` 返回的 pid 去 kill**：Chrome 的启动器进程会另起真正的浏览器进程再自己退出，
   `close()` 时那个 pid 早已是死进程，真正的浏览器一个都收不掉（Windows 上实测）。
3. **清残留**：`launchBrowser()` 第一行是 `await sweepLeftovers()`——启动前先数一遍
   `probe-chrome-` 的残留，**有几只报几只**并清掉，再继续。避免上一次异常退出攒下几千个。

> ⚠️ **这三步只管「进程」，不管「目录」**（第三十三轮回归复查查明，**只记录、未修**）：
> `cdp.mjs` 里造出来的 `%TEMP%\probe-chrome-*` **从来没有人删**——`killTree()` 只 `Stop-Process`，
> 全文件 `rmSync` 出现 **0 次**。后果是每跑一次浏览器套件就往系统临时目录里留一个几十 MB 的 profile：
> 第三十三轮清理前实测 **7 个目录、约 105 MB**。这不是第三十二轮引入的（`git show HEAD:…/cdp.mjs` 里同样如此），
> 本轮按边界**只记录不顺手修**，登记在 `docs/dev/known-issues-handoff.md` §3.34⑦，
> 与 §3.33⑤ 的 D4（清理链静默）同源，可一并拍板。

**全机匹配的 PowerShell 片段（必须同时限定可执行名）**：

```powershell
$p = 'probe-chrome-'
Get-CimInstance Win32_Process | Where-Object {
  $_.Name -in @('chrome.exe','msedge.exe','crashpad_handler.exe') -and
  $_.CommandLine -and $_.CommandLine.Contains($p)
} | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
```

> ⚠️ **`$_.Name -in …` 这一半不能省**（第二十九轮实测踩过）：**只按 `CommandLine.Contains($p)`
> 判定会「自己数自己」**——发这条查询的 `powershell.exe`，命令行里原样嵌着 `'probe-chrome-'` 这个
> 字符串，于是它把自己也数进去：**计数永远留 1**（那 1 个就是正在执行的 powershell 本身），
> 而清理脚本还会 `Stop-Process` 打到自己、半路把自己干掉，剩下的进程反而清不完。
> 本轮用 `target/probe/r29/_sweep_diag.mjs` 逐轮打印 PID/PPID/Name 坐实：第 1 轮后 9 个
> `chrome.exe` 全灭，剩下那只 Name 是 `powershell.exe`、PID 每轮都换。

**自检**（想验证这套还在起作用就跑它，`target/probe/r29/_sweep_check.mjs`）：造一只带前缀的孤儿 +
一只**不带**前缀的对照浏览器，调 `launchBrowser()`，判据是
**孤儿清成 0 / 对照那只有没有误伤（必须还活着）**。本轮实测 `EXIT=0`（孤儿 12 → 0、对照 10 存活），
九步链跑完 `probe-chrome-` 残留 **0**、机器上 `chrome.exe` 总数 **0**。

**期望输出**（截图落点在同一行打印的文字里）：

| 套件 | 期望截图 | 期望数量 |
| --- | --- | --- |
| `run-all-browser.mjs` | `target/probe/browser/shots/all/<id>.png` | **79** |
| `run-combo-browser.mjs` | `target/probe/browser/shots/combo/<id>.png` | **17** |
| `run-set-browser.mjs alt` | `target/probe/browser/shots/alt/<id>.png` | **10** |
| `run-set-browser.mjs registry` | `target/probe/browser/shots/registry/<id>.png` | **76** |
| `run-article.mjs` | `target/probe/browser/shots/articles/<id>.png` | **14** |
| `verify-live-app.mjs`（第十四轮新增） | `target/probe/browser/shots/live/<id>.png` | 按参数，默认 **2**（43/44） |

### 3.5 汇总判定（纯文件处理，不联网）

> ⚠️ **顺序陷阱（第十五轮干净 clone 实测踩到）**：`round10_component_paths.mjs` 会读
> `target/probe/round10_article_coverage.json`，而那份产物由 **§3.7** 产出——它在本文档里排在**后面**。
> 严格照本文从上往下跑，这一条会以
> `Error: ENOENT: no such file or directory ... round10_article_coverage.json` 失败。
> **修法**：先跑 §3.7 的只读导出与 `round10_article_coverage.py`，再回到这里跑最后一条。
> （只跑前四条不受影响。）

```bash
node tools/render-verify/browser/summarize-all.mjs
node tools/render-verify/browser/summarize-combos.mjs
node tools/render-verify/browser/summarize-alt.mjs alt
node tools/render-verify/browser/summarize-alt.mjs registry
node tools/render-verify/round10_component_paths.mjs      # ← 需要 §3.7 的 round10_article_coverage.json
```

**期望输出（第十二轮实测，第十三轮**照抄重跑，逐条逐字相符**）**

| 套件 | 判定 | 数字 |
| --- | --- | --- |
| 79 样例（编辑器路径） | `pass` / `na` / `fail` | **70 / 9 / 0** |
| 17 组合（上游层） | `ok` / `nested-unsupported` / `silently-lost` | **8 / 8 / 1** |
| 17 组合（编辑器层） | `pass` | **17 / 17**（fail 0） |
| 10 替代写法（后端） | `ok` | **10 / 10** |
| 10 替代写法（编辑器） | `pass` | **10 / 10**（fail 0） |
| 76 组 `layout-*`（后端） | `not-rendered` | **76 / 76** |
| 76 组 `layout-*`（编辑器） | `na` | **76 / 76**（fail 0） |
| 终稿对照表 | — | **组件总数 63 / 两路径均通过 25 / 等上游 38 / 悬空 0** |

```bash
python -c "import json,io; d=json.load(io.open('target/probe/round10_component_paths.json',encoding='utf-8')); print({k:d[k] for k in ('registryTotal','tierA','tierB','upstreamRows','backendOk','editorPass','dangling')})"
# 期望：{'registryTotal': 63, 'tierA': 13, 'tierB': 50, 'upstreamRows': 38, 'backendOk': 25, 'editorPass': 25, 'dangling': 0}
```

### 3.6 独立交叉验证（换一条通道，防止自证）

```bash
python tools/render-verify/gen/round11_crosscheck.py
# 期望：summary: groups=18 notRendered=18 doubtful=0 controlFP=0
```

三条通道互相独立：裸 HTTP 响应体（另一种检测方法）/ 引擎包 `spec/engine/mf_app.js` 静态结构 /
服务端 `GET /__markflow_render` 的 guide。**自带 3 条已知支持的对照组**
（`:::breaking`、`:::callout type="tip"`、`<badge … />`）——对照组若被判成「未渲染」，
说明检测器有假阳性，上面的 18/18 一律作废。这条设计来自第十一轮的一次真实翻车：
第一版判据把标签式写反，当场给出 9/18「存疑」，是对照组把方向掰回来的。

### 3.7 真实稿件覆盖（只读导出，可选）

```bash
# 只读导出全库 44 篇（含软删 6 篇）的 Markdown 源文；-N -B 会把换行转义成 \n，脚本侧再还原
docker exec momo-mysql-dev mysql -uroot -pchange-me --default-character-set=utf8mb4 \
  -D wechat-article -N -B \
  -e "SELECT ID, DELETED, LAYOUT_ENGINE, CONTENT_MARKDOWN FROM ARTICLE ORDER BY ID;" \
  > target/probe/round10_articles.tsv

python tools/render-verify/gen/round10_article_coverage.py
# 期望：articles = 44；70 个扫描项里 39 有命中 / 31 无；6 种「禁写」写法命中全为 0
```

### 3.8 端到端验收演练（挑一篇真实稿件，两条路径各走一遍）

以**公式（稿件 43）**与**轮播（稿件 44）**为例。两条路径分别取证，不互相依赖：

```bash
# 路径一 · 后端渲染 API：把库里的 Markdown 源文（只读）打一次真实渲染接口
docker exec momo-mysql-dev mysql -uroot -pchange-me --default-character-set=utf8mb4 \
  -D wechat-article -N -B \
  -e "SELECT ID, CONTENT_MARKDOWN FROM ARTICLE WHERE ID IN (43,44);" > target/probe/r13_articles.tsv
# 再用 POST /__markflow_render 打这两段源文，量产物里的 <svg> / katex / 字面 :::

# 路径二 · 真实编辑器：run-article.mjs（见 §3.4）在真实 Chrome 里量 DOM，结果落在
# target/probe/browser/articles_result.json → results[].editor
```

**第十三轮实测结果（两条路径独立复现）**：

| 稿件 | 后端渲染 API | 编辑器 DOM（真实 Chrome） |
| --- | --- | --- |
| **43 公式** | HTTP 200 / `ok=true`；源文 166 字符 → 产物 **10266** 字符；`katex` 类名出现 **12** 次；可见文字里字面 `:::` **0** | `.katex` **5 个全部可见**（3 行内 + 2 块级），高度 **[22,22,22,45,53]**，源码未泄漏，字体已加载，`ready=true` |
| **44 轮播** | HTTP 200 / `ok=true`；源文 196 字符 → 产物 **1437** 字符；`<svg>` **1** 个；可见文字里字面 `:::` **0** | `<svg>` **1 个可见**、盒 **600×200**、viewBox `0 0 600 200`、动画元素 **1**、foreignObject **3**、内嵌图 **3/3 加载**，`ready=true` |

> 口径说明：后端产物里的 `katex` **12 次**是 HTML 里带 `katex` 的 class 名计数（一行公式会产生多个 class），
> 编辑器侧的 **5 个**是渲染后真实 `.katex` 元素的个数——**两者不是一回事，别当成对不上**。
> 真正的判据是编辑器侧「元素存在 + 高度非零 + 源码没泄漏」这三条同时成立。

### 3.9 第十三轮「照本文实跑」的结果

用一个全新 shell、不带任何既有环境变量，**逐字照抄第三、六节的全部命令**跑了一遍。

**照抄成功率：探针与汇总命令 21/21 全部可直接复制执行，且预期数字逐条相符；只有 `mkdir -p target/probe` 这一条是本次新补的（clone 场景必需）。**

| 检查点 | 文档预期 | 实跑 |
| --- | --- | --- |
| `component_matrix.py` 校验 | `79 0 1` | **`79 0 1`** |
| 三套产物文件数 | `[('combos',17),('alt',10),('registry',76)]` | **完全一致** |
| 截图张数 | 79 / 17 / 10 / 76 / 14 | **79 / 17 / 10 / 76 / 14** |
| 79 样例判定 | pass 70 / na 9 / fail 0 | **`{'pass': 70, 'na': 9}`** |
| 17 组合判定 | 上游 8/8/1；编辑器 17 pass | **`{'nested-unsupported':8,'silently-lost':1,'ok':8}` / `{'pass':17}`** |
| 10 替代写法 | 后端 10 ok / 编辑器 10 pass | **`{'ok':10}` / `{'pass':10}`** |
| 76 组 `layout-*` | 后端 76 not-rendered / 编辑器 76 na | **`{'not-rendered':76}` / `{'na':76}`** |
| 终稿对照表 | 63/13/50/38/25/25/悬空 0 | **完全一致** |
| 交叉验证 | `groups=18 notRendered=18 doubtful=0 controlFP=0` | **完全一致** |
| 真实稿件覆盖 | 44 篇；70 项 39 命中 / 31 无；6 禁写全 0 | **44；39/31；0** |
| 环境自检 7 条 | 全部可用 | **全部可用** |

**本轮发现并已修掉的文档缺陷（5 条）**：

| # | 缺陷 | 修法 |
| --- | --- | --- |
| 1 | §3.1 的重定向写进 `target/probe/`，**clone 场景首条命令就失败** | 补 `mkdir -p target/probe` 并加警告 |
| 2 | 前置条件里**没写 `.env`**，而缺它会 `Could not resolve placeholder 'ENV.MYSQL_TEST_URL'`、205 例里 44 个 Error | 新增前置条件 8，并在 §3.1 说明「这不是代码回归」 |
| 3 | 把 `PYTHONIOENCODING` 说成"不设会 `UnicodeEncodeError`"，**与实测不符**（探针脚本不崩，只是中文值输出乱码；只有内联 `python -c` 打中文才会抛异常） | 改成准确表述「建议设；脚本不崩，落盘文件始终 UTF-8」 |
| 4 | §3.1 说「需要 node_modules」但**没说怎么来** | 补 `npm ci`（用仓库自带 lockfile，不算新增依赖） |
| 5 | §3.4 五条命令贴在同一代码块里，**容易被并行执行**（会抢 9333 端口） | 拆成 5 个独立代码块并加提醒 |

> 另有 1 条**不是文档缺陷、但必须让人知道**的事实，当年写进了第零节：
> `target/probe/` 里 `git ls-files` 实测 **0 个文件**，clone 场景**必然复现不了全部 10 类探针套件**；
> 且当时这些改动**尚未提交**（clone 的 HEAD 早于第五～十三轮，测试类文件 36 vs 45）。
> **第十四轮已把这条事实消掉**——脚本与输入清单入库到 `tools/render-verify/`，见 §3.10。
> **第三十五轮（2026-09-14）把「尚未提交」这半句也消掉**：累积改动已一次性提交并推送
> （`b591c40` → `huanyu/main`，推送后 `huanyu/main...main` = `0 0`）。
> ⚠️ 但**「干净 clone 一条命令跑到底」仍不成立**：`run-suite.sh` 的起点是探针 dist 重建，
> **不含 `gen/` 那几支出网的生成器**，`target/probe/components/*` 与 `component_matrix.json` 必须先手工打，
> 见 §3.14。

### 3.10 第十四轮「探针入库 + 活体前端取证」的结果

两件事，都是对既有结论的**加固**，不是新判据。

**① 探针脚本搬进受版本控制的 `tools/render-verify/`（并照第三节重跑验证）**

搬迁原则：**脚本与输入入库，产物留在 gitignored 的 `target/probe/`**；
命令里的脚本路径整体从 `target/probe/…` 换成 `tools/render-verify/…`，
但**产物落点与搬之前逐字节相同**——所以历史各轮的截图路径、汇总数字与本文的预期值都不用改。

| 检查点 | 文档预期 | 第十四轮实跑（新路径） |
| --- | --- | --- |
| `gen/component_matrix.py` 校验 | `79 0 1` | **`79 0 1`** |
| 三套产物文件数 | `[('combos',17),('alt',10),('registry',76)]` | **完全一致** |
| 截图张数 | 79 / 17 / 10 / 76 / 14 | **79 / 17 / 10 / 76 / 14** |
| 79 样例判定 | pass 70 / na 9 / fail 0 | **`{'pass': 70, 'na': 9}`** |
| 17 组合判定 | 上游 8/8/1；编辑器 17 pass | **`{'nested-unsupported':8,'silently-lost':1,'ok':8}` / `{'pass':17}`** |
| 10 替代写法 | 后端 10 ok / 编辑器 10 pass | **`{'ok':10}` / `{'pass':10}`** |
| 76 组 `layout-*` | 后端 76 not-rendered / 编辑器 76 na | **`{'not-rendered':76}` / `{'na':76}`** |
| 终稿对照表 | 63/13/50/38/25/25/悬空 0 | **完全一致** |
| 交叉验证 | `groups=18 notRendered=18 doubtful=0 controlFP=0` | **完全一致** |
| 真实稿件覆盖 | 44 篇；70 项 39 命中 / 31 无；6 禁写全 0 | **44；39/31；0** |
| 真实稿件套件 | 14 篇取数、13 篇 ready | **14 / 13 ready** |

**入库时踩到的两个坑**（都已修，写在这里省下一次返工）：

| # | 坑 | 修法 |
| --- | --- | --- |
| 1 | 探针入口 `probe.js` / `probe_all.js` 另有两个**同目录依赖** `probe.css`、`editor-setup.js`（后者又依赖 `./legacyExtensions.js`），漏搬会让 `vite build` 报 `UNRESOLVED_IMPORT`；`probe.css` 是空文件也可能被忽略 | 三个文件一并入库；`vite.config.mjs` 的 `webui` 路径改用 `paths.mjs` 里的 `ROOT`，不再靠 `../../../` 数层数 |
| 2 | `target/` 在 `.gitignore` 里是**无前导斜杠**的模式，会命中任意深度的同名目录——搬运过程中在 `tools/render-verify/target/` 下误建过目录，会被静默忽略 | 用 `paths.py`/`paths.mjs` 集中算路径，脚本内禁止裸相对路径 |

**② 新增 `browser/verify-live-app.mjs`：量「应用实际提供的那份前端」**

这一支回答的是既有套件**结构性量不到**的问题：`run-article.mjs` 是把 `webui/dist` 用本地静态服务
端起来的（见其 `DIST` 那一行），量到的是**最新构建的产物**；而应用实际对外提供的是
Maven `frontend-maven-plugin` 在 `prepare-package` 阶段写进 `target/classes/static/` 的那一份。
两者**可以不是同一次构建**。

第十四轮就撞上了：`spring-boot:run` **不经过** `prepare-package`，于是
`target/classes/static/` 停在旧构建，而 `webui/dist` 已经是新的——
**探针全绿，用户打开 `/articles/43`、`/articles/44` 却是坏的**。取证方式是查编辑器 chunk 的指纹
（`rawSvg` / `rawMath` / `preservedEmptySpan` 这三个名字只存在于 `webui/src/editorExtensions.js`，
打包后作为字符串常量活下来）：

| 前端来源 | 入口 | 编辑器 chunk | `rawSvg` / `rawMath` |
| --- | --- | --- | --- |
| 重启前 8081 实际提供的（旧构建） | `assets/index-CX6OBQPX.js` | `ArticleEditorView-9ZqsQRgK.js` | **都缺**（`katex` 字样也是 0 次） |
| 重启后 8081 实际提供的 | `assets/index-ChPmeBR_.js` | `ArticleEditorView-BfXgtpun.js` | **都在** |

重启后对**真实 8081 页面**（而不是探针自起的服务）复量，稿件的两条路径判定与第十三轮一致：

| 稿件 | 活体页面实量 |
| --- | --- |
| **43 公式** | `.katex` **5 个全部可见**，高度 **[22,22,22,45,53]**，不含 `$$`/`\frac` 残留 |
| **44 轮播** | `<svg>` **1 个可见**、盒 **600×200**、内嵌图 **3/3 加载** |

> **给后续回归的硬要求**：改了编辑器扩展（`webui/src/editorExtensions.js` 一类）之后，
> **不能只跑 `run-article.mjs`**——它量的不是应用提供的那份。要么跑 `verify-live-app.mjs`，
> 要么先 `npm run build -- --outDir ../target/classes/static --emptyOutDir` 再重启应用。

---

### 3.11 第十五轮「干净 clone 复现 + 收尾核验」的结果

#### ① 干净 clone 照本文第三节跑一遍 —— 逐条数字

方法：`git clone <本仓库> wechat-article-bot-r15clone`（HEAD = `b6d5975`），
**不复制任何 `target/` 产物**，按第一节补 `webui/node_modules`（`npm ci`，7 秒 / 106 包）后逐条执行第三节。

| 套件 | clone 实测 | 与工作仓库既有判定 |
| --- | --- | --- |
| W `webui` 构建 | **✓ built in 418ms** | — |
| M 79 个最小样例 | `79 0 1`（79 条 / 0 异常 / 1 warning） | 一致 |
| C / A / R 后端产物 | `[('combos',17),('alt',10),('registry',76)]` | 一致 |
| M′ 79 样例（真 Chrome） | **pass 70 / na 9 / fail 0**，截图 79 张 | 一致 |
| C′ 17 组合 | 上游 `ok 8 / nested-unsupported 8 / silently-lost 1`；编辑器 **pass 17** | 逐字节相同 |
| A′ 10 替代 | 后端 **ok 10**；编辑器 **pass 10** | 逐字节相同 |
| R′ 76 组 `layout-*` | 后端 **not-rendered 76**；编辑器 **na 76**，截图 76 张 | 逐字节相同 |
| S 真实稿件 | 14 篇：**13 ready + 1 不 ready（第 5 篇，已知软删）** | 一致（唯一差异见下） |
| L 活体前端 43/44 | 43 → `.katex` 5/5、高度 `[22,22,22,45,53]`；44 → `svg` 1 可见、`[[600,200]]`、图 3/3 | 逐字段相同 |
| P 终稿对照表 | `{registryTotal:63, tierA:13, tierB:50, upstreamRows:38, backendOk:25, editorPass:25, dangling:0}` | 一致 |
| X 独立交叉验证 | `groups=18 notRendered=18 doubtful=0 controlFP=0` | 一致 |
| S′ 真实稿件覆盖 | 44 篇 / 70 项里 **39 命中 31 无** / 6 种禁写全 0 | 逐字节相同 |

**两处非路径差异（如实记录，都不影响判定）**：

1. `browser/all_summary.json` 与 `round10_component_paths.json` **行序不同**（79 行集合完全相同，
   首个位置差异在第 46 行：`blk-case-flow` ↔ `blk-slider-selfclose`）。原因是这两份汇总按
   `target/probe/components/` 的**目录列举顺序**排列，而 clone 里这些文件是新建的，NTFS 列举顺序与工作仓库不同。
   **判定数字一格未变**；要逐字节可复现的话，给汇总脚本加一次按 id 排序即可（未改，属可选优化）。

   > **第三十六轮订正**：上面「按目录列举顺序排列」这个**成因是错的**（观测本身没错）。
   > 实测两支脚本都不读 `target/probe/components/` 来定行序；真因是 `gen/component_matrix.py` 的
   > 产物合并历史（`by_id` 先灌 `existing` 再逐条覆盖 ⇒ 老行在前、新行追加在后）。
   > 且 `round10_component_paths.json` 本来就不随输入行序变（它遍历手写清单 `component_matrix.json`）。
   > `summarize-all.mjs` 已加按 id 排序修复。详见 **§六「已知的坑」第 17 条**。
2. `browser/articles_result.json` 里**第 38 篇的内嵌图从 `0/0` 变成 `2/2`**（clone 连跑两次都是 `2/2`）。
   其余 13 篇逐篇一致。第 38 篇的两张图确实能加载，因此 clone 这次读到的值更准；
   工作仓库那份是更早一轮的读数。**不影响任何判定**（该套件判的是 `editor.ready`）。

**G（后端全量测试）在没补 `.env` 的 clone 里跑不了**，实测报错与文档预期一致：
`Could not resolve placeholder 'ENV.MYSQL_TEST_URL'`（大批 integration test 报 Error）。
补上 `.env` 即可，不是脚本问题——这正是第一节前置条件第 8 条要拦的情况。

#### ② 干净 clone 暴露的一处文档缺陷（已在本轮修好上面那条）

严格按本文顺序执行时，§3.5 的最后一条 `round10_component_paths.mjs` 会因缺少
`target/probe/round10_article_coverage.json`（由**后面的** §3.7 产出）而 `ENOENT` 失败。
§3.5 已加显式顺序警告。**这不是脚本缺陷，是文档的步骤顺序自相矛盾。**

#### ③ 重启到底会不会退回旧前端 —— 实测

| 动作 | 结果 |
| --- | --- |
| 记录 `target/classes/static` 全部 **39 个文件**的 sha256 → 跑 `./.mvn/mvn-local.sh -o test-compile`（= `spring-boot:run` 在 Maven 侧实际执行的最后阶段） | **39 个文件逐字节不变**；日志确认 `resources:resources` 确实执行了，但 `src/main/resources/static` 目录**不存在**，没有东西能覆盖 `target/classes/static` |
| 再真正停止应用（PID 21344）→ `./.mvn/mvn-local.sh -o spring-boot:run`（新 PID 66136，3.5 秒起来） | 哈希仍**逐字节不变**，8081 提供的仍是 `index-ChPmeBR_.js` |

**结论：普通重启不会退回旧前端。** 真正的复发条件是**先清理再启动**：
第十五轮在干净 clone 里实测 `test-compile` 之后 `target/classes/static` **根本不存在**——
也就是说 `mvn clean` + `spring-boot:run` 起来的是一个**没有前端**的应用（页面空白），
要等下一次 `package` 才会由 `frontend-maven-plugin` 构建出来。

#### ④ 这一条的修复方案（**本轮只出方案，未改任何构建配置**）

| 方案 | 改动位置 | 效果 | 风险 | 回滚 |
| --- | --- | --- | --- | --- |
| **A. 构建期对齐**（推荐）：把 `build-webui` 的 `<phase>` 从 `prepare-package` 改成 `compile`（或给 `spring-boot:run` 绑一个 `executions` 到 `process-classes`） | `pom.xml` 的唯一一处 `<phase>` | 之后任何 `spring-boot:run` 都自带最新前端，从根上消掉这一类故障 | 每次启动都跑一次前端构建（约 0.4 秒，可接受）；若本机无 Node 会让 `spring-boot:run` 直接失败——**这正是要拍板的地方** | 改回 `prepare-package` |
| **B. 启动自检**：应用启动时检查 `classpath:static/index.html` 是否存在/是否比 `webui/src` 旧，缺失或陈旧就打 WARN（或直接拒绝启动） | 新增一个 `ApplicationRunner`；不动 `pom.xml` | 故障从「用户看到空白页」变成「启动日志里一句明确的 WARN」，排查成本大降 | 只是告警，不自动修；要能真修就得再挂构建 | 删掉该类 |
| **C. 只改文档**：在 README/复现手册写明「`mvn clean` 之后启动前必须先跑一次前端构建」 | 文档 | 零代码风险，立刻可做 | 靠人记；第十五轮已经证明「记」不住 | 无 |
| **D. 兜底脚本**（可与 A/B 并存）：提供一个 `dev-start.sh`，内部先 `npm run build -- --outDir ../target/classes/static` 再 `spring-boot:run` | 新增脚本 | 与 A 等效，但不动 Maven 生命周期 | 多一个入口，别人仍可能直接 `spring-boot:run` | 删脚本 |

**建议**：A + B 一起做（A 治根、B 兜底），C 无论如何都该做。**这几项都属结构改动，等用户拍板后再动。**

#### ④-1 决策落地（2026-09-17）：**A 经证据否决；B / C / D 均已做**

- **B 已实现**：`WebUiArtifactCheck`（`@Component @Order(35)`，`ApplicationRunner`）。
  启动时检查 `classpath:static/index.html` 是否存在、是否比 `webui/` 源码旧
  （跳过 `node_modules`/`dist`），命中就 WARN 并打印重建命令；**只告警、不阻塞启动**
  （与 `LlmProfileMigrationRunner` / `ArticleLongTextColumnRunner` 同一约定）。
  单测 `WebUiArtifactCheckTest`（7 例）用纯函数 `evaluate(present, builtAt, newestSource)` 覆盖
  缺失/陈旧/新鲜/无源码/时间戳未知/排除目录六种情形。
- **A 已否决，理由是证据而非偏好**：`frontend-maven-plugin` **不在本地 Maven 仓库**
  （`C:\Users\WIN11\.m2\repository\com\github\eirslett` 不存在）。实测
  `mvn -o com.github.eirslett:frontend-maven-plugin:2.0.2:install-node-and-npm` 报
  `Cannot access central ... in offline mode`。把 `build-webui` 绑到 `compile`
  （或给 `spring-boot:run` 绑 `process-classes`）会让**每一次 `mvn -o test` 都在插件解析阶段失败**
  ——为了修「前端陈旧」而打破项目自己的**离线闸门**，代价大于收益。
  若将来要重启 A，前提是先把该插件装进本地仓库（或放弃离线闸门）。
- **C 已做**：见本文件「坑 13」。
- **D 已做（第三十六轮）**：`scripts/dev-start.sh`。它是 A 被否决后**唯一能真正「自动修」**的路径，
  且不动 Maven 生命周期，因此不需要重新论证 A。
  - 行为：先 `npm run build -- --outDir ../target/classes/static --emptyOutDir`（与 `pom.xml` 的
    `build-webui`、`WebUiArtifactCheck.REBUILD_COMMAND` 三处**逐字一致**），再 `spring-boot:run`。
  - `--build-only` 只对齐前端产物不启动；`--` 之后的参数原样传给 `spring-boot:run`。
  - **脚本自己解析仓库根目录**，因此在任何 cwd 下都能跑（实测从 `$env:TEMP` 调用也正常）——
    这一点是刻意的，因为「必须站在根目录才能跑」正是这类兜底脚本最容易失效的地方。
  - **构建「成功」但产物没落地**是最容易骗过人的失败，所以脚本在构建后**当场断言**
    `target/classes/static/index.html` 存在，不存在就 `exit 70`，不把问题留到启动之后。
  - 依赖缺失给**明确的退出码与中文原因**（无 `webui/` → 69；无 `node`/`npm` → 69），
    而不是让 npm 抛一句难懂的错。
  - **Maven 入口的选择顺序是实测结论，别按直觉改**：优先 `.mvn/mvn-local.sh`（仓库自带的离线启动器，
    复用已解压发行包、**不依赖 `JAVA_HOME`**，实测可用），其次 `./mvnw`（离线时**构建还没开始就失败**，
    且未设 `JAVA_HOME` 时报 `JAVA_HOME ... is not defined correctly`），最后 PATH 里的 `mvn`。
  - 验证：`sh -n` 语法通过；`--help` / `--build-only` 退出码 0；`node`、`npm` 缺失两条守卫各返回 69；
    参数解析（`--build-only -- --server.port=9090`）实测把 `--server.port=9090` 原样留下；
    产物 mtime 晚于 `webui/src` 最新源文件 ⇒ `WebUiArtifactCheck` 不再告警。
  - 定位不变：**它是兜底入口，不是强制路径**——直接 `spring-boot:run` 的人仍有 B 的启动告警兜住。

#### ⑤ 保存侧自检对 38 个 `layout-*` 的全量覆盖（不是抽样）

第十五轮用反射直接调生产代码 `ScheduledArticleTools.markflowSyntaxHints`，
对注册表里 **38 个名字 × 2 种写法 = 76 组**逐条断言「至少产出一条提示」，并用 **19 个受支持容器**做对照组：

```
registryLayoutNames=38
containerForm_withoutHint=0 []
tagForm_withoutHint=0 []
supportedControlGroup_size=19 falsePositives=0 []
VERDICT=FULL-COVERAGE
```

实现方式是**按前缀整族拦**（容器式落到「不支持的容器语法」那条通用提示；标签式由
`UNSUPPORTED_LAYOUT_TAG = <\s*layout-[a-z][a-z-]*\b` 命中），所以不存在「漏列某个名字」的可能。
原始输出 `target/probe/r15/layout_guard_coverage.txt`，脚本 `tools/render-verify/gen/LayoutGuardCoverage.java`。

复现方式（**在仓库根目录**，先确保 `target/classes` 是新的）：

```bash
./.mvn/mvn-local.sh -o test-compile
./.mvn/mvn-local.sh -o -q dependency:build-classpath -Dmdep.outputFile=target/probe/r15_cp.txt
"/c/Program Files/Java/jdk-17/bin/java" -Dfile.encoding=UTF-8 \
  -cp "target/classes;$(cat target/probe/r15_cp.txt)" \
  tools/render-verify/gen/LayoutGuardCoverage.java
# 期望末行：VERDICT=FULL-COVERAGE
```

> ⚠️ 同时查清一件**措辞不准确**的事：这条自检是**提示而非拒绝**——
> `ScheduledArticleTools.save()` 不会因此抛错，只是把改写建议放进工具返回值的 `warnings` 里
> （另打一条 `log.warn`）。**且它只覆盖智能体走 `save_article_draft` 的路径；编辑器 REST 保存路径没有这项检查。**
> `docs/render-acceptance-report.md` 第五节已按此更正。

#### ⑥ 全库 44 篇里那 39 处命中，编辑器侧能不能渲染

三个既有产物做连接（不新打 API、不写库）：`round10_article_coverage.json` × `browser/all_summary.json`
× `browser/articles_result.json`：

- **39 项全部有「最小样例 + 真实浏览器」的判定，且判定全部为 `pass`**（`editorPass=39`，非 pass **0** 项）；
- 其中 **7 项**（`md-link`、`md-image`、`blk-title`、`in-em-hl`、`ctn-case-flow`、`ctn-steps-h`、`ctn-steps-v`）
  命中的文章**都不在**真 SPA 回归过的 13 篇里 → 证据强度停在 **B 级**，如实标注；
- 6 种禁写写法的命中数**全为 0**。

产物：`target/probe/r15/coverage_editor_verdict.md` / `.json`，
脚本 `tools/render-verify/gen/round15_coverage_editor_verdict.py`
（用法：`PYTHONUTF8=1 python tools/render-verify/gen/round15_coverage_editor_verdict.py`；
它读的三份输入分别是 §3.4 的 `browser/*.json`、§3.5 的 `all_summary.json` 与 §3.7 的覆盖产物，
**先把那三步跑完**）。

### 3.12 第十六轮：用户逐条标注的 11 条（套件 U）

输入是**用户在真实编辑器里原样标注的 11 段 MarkFlow 源码**，不改写、不「修得好看一点」
（原话与源码都在 `tools/render-verify/gen/round16_editor_reported.py` 的 `CASES` 里）。

```bash
# U1 —— 后端产物：11 段原样打真实渲染 API（需要令牌；只读，不落库）
python tools/render-verify/gen/round16_editor_reported.py
# 产物：target/probe/r16/r16-{01..11}-*.md/.html、r16.json

# U2 —— 编译探针 dist（与 §3.3 同一条命令，改了编辑器代码就必须重跑）
(cd webui && npx vite build --config ../tools/render-verify/browser/vite.config.mjs)

# U3 —— 真实浏览器：产物灌进编辑器往返，逐行量计算样式（与 §3.4 的套件**串行**跑）
node tools/render-verify/browser/run-r16-browser.mjs
node tools/render-verify/browser/summarize-r16.mjs
# 期望首屏：后端侧 10/11 recognised；编辑器侧 1/11 逐属性一致（口径说明见 §3.20②⑤）

# U4（可选）—— 1:1 对照截图，肉眼看「哪一侧真缺了东西」
node tools/render-verify/browser/r16-shot-zoom.mjs r16-01-changelog r16-07-summary r16-08-checklist r16-11-steps-horizontal
# 产物：target/probe/browser/shots/r16/zoom/<id>.{after,reference}.png

# U5（可选）—— 三条被结论引用的单点诊断，排查时按需跑
node tools/render-verify/browser/r16-dump-live-table.mjs r16-06-title-da01 r16-11-steps-horizontal
node tools/render-verify/browser/r16-measure-heights.mjs r16-07-summary
node tools/render-verify/browser/r16-width-equiv-test.mjs r16-09-table-card
# 产物：stdout（JSON）

# U6（可选）—— §R10 的反向自查（第二十轮新增）：`|` 是引擎约定还是实现缺陷
# 只调渲染 API，不需要应用起着、不需要浏览器；跑一次约 13 秒
python tools/render-verify/gen/round20_field_block_scalar.py
# 产物：target/probe/r16/r10_by_design.json（4 组件 × 3 写法）
#       target/probe/r16/r10_blockscalar.json（11 组块标量边界）

# U7（可选）—— 真实应用界面上的 11 条验收（第二十二轮新增）
# **要求应用在 127.0.0.1:8081 起着**（其余套件只读落盘产物，这一支是真的开应用）
# 默认模式：应用层伪造 GET 返回 → 应用自己 setContent → 这就是用户打开文章的路径
node tools/render-verify/browser/r16-live-editor.mjs 38 --width-match
# 另两条**输入路径**（结果与上面**不一样**，见 §3.22⑤ / §3.23①；第二十三轮补了 --type）：
node tools/render-verify/browser/r16-live-editor.mjs 38 --paste       # 合成 ClipboardEvent
node tools/render-verify/browser/r16-live-editor.mjs 38 --paste-real  # 真剪贴板 + 真 Ctrl+V
node tools/render-verify/browser/r16-live-editor.mjs 38 --type        # 真 input 事件逐行手打源文
# 产物：target/probe/browser/r16_live{,_widthmatch,_paste,_pastereal,_type}_result.json
#       target/probe/browser/shots/r16-live/<mode>/<id>.{png,zoom.png}
#       （<mode> = setcontent | widthmatch | paste | paste-real | typed；分子目录是因为
#         第二十二轮四种模式共用一个目录会互相覆盖，第二十三轮改的）

# U7-判（第二十二轮新增）—— 纯离线比对「真实界面」与「探针页」，不开浏览器、不打 API：
node tools/render-verify/browser/r16-compare-probe-vs-live.mjs            # 默认读 --width-match 那份
node tools/render-verify/browser/r16-compare-probe-vs-live.mjs r16_live_result.json   # 异宽那份
# exit 0 = 70 组探针全一致；exit 1 = 有差异（逐组打印差在哪）；exit 3 = 产物缺失

# U8（可选）—— 保存出口幂等性：用户原话「我在编辑器里看到的样子，保存之后还在不在」（第二十五轮新增）
# **要求应用在 127.0.0.1:8081 起着**。三步：拦下 PUT 取出 body.contentHtml → 原样 setContent 灌回
# → 量同一组 11 条探针，并与拦截那一刻的活 DOM 逐条对照。判据：逐条相同 = 所见即所存。
node tools/render-verify/browser/r25-save-exit-roundtrip.mjs 38 --inject all --label after
node tools/render-verify/browser/r25-save-exit-roundtrip.mjs 38 --inject all --paste --label after
node tools/render-verify/browser/r25-save-exit-roundtrip.mjs 38 --inject all --type  --label after
# --inject <单个文件名> 灌一条用例；--bundle <dist> 换前端包；产物落 target/probe/browser/r25_*
# ⚠️ 三种路径的**覆盖度不同**：手打时 `:::` 保持字面文本、不生成 section，
#    会有 3 条探针量不到（记 null，**不计入「相同」**），别把 11/11 读成全覆盖。

# U8-定位（可选，只在上面出现差异时跑；两支都**只在页面内存里改，从不保存、不写库**）
node tools/render-verify/browser/r25-whitespace-probe.mjs        # 哪一类空白在第一次解析时就丢
node tools/render-verify/browser/r25-preservewhitespace-trial.mjs # 候选修法 preserveWhitespace:'full' 的副作用

# U9（可选）—— 存量影响面（第二十五轮新增）：**纯 DB、不开浏览器、全程只读**
node tools/render-verify/round25_stock_scan.mjs
# 产物：target/probe/browser/r25_stock_scan.json（判据都是 CONTENT_HTML 的纯函数，可自己重跑）

# U10（**回归闸**，第二十六轮新增）—— 窄列表格的**列宽**在保存出口里还在不在
# **要求应用在 127.0.0.1:8081 起着**。跑完 **exit 0 = 通过**、exit 1 = 不过（打印差在哪一列）。
# 判据：① 出口每一列的宽度声明 = 入口那一列的宽度声明；② 出口再灌回去再存一次，逐字不变。
# 内置**闸的自检**：拿第二十五轮存档的旧编辑器出口（两列都塌成 min-width: 25px）
# 喂给同一套解析规则，必须判 FAIL——否则说明这条闸被写松到「怎么塌都算过」。
node tools/render-verify/browser/r26-table-colwidth-exit.mjs
node tools/render-verify/browser/r26-table-colwidth-exit.mjs --bundle target/probe/r26/before-dist  # 换前端包
# 产物：target/probe/browser/r26_colwidth_exit.json 与 r26_colwidth_exit_<样本>_saveexit.html
# ⚠️ 样本 `yi-hand`（手写表格，窄列**只有** `<col style="width:90px">`）是**记录项、不参与退出码**：
#    它考的是本轮新量到的**入口缺口**（编辑器入口只认 `data-colwidth` / `colwidth` / `<col width>`
#    三种属性写法，读不到 `<col style>`），**产物侧暴露为 0**（249 个渲染产物里带列宽的只有 2 个，
#    且都带属性写法）。要不要补，见 `known-issues-handoff.md` §3.27② 与 §3.27③ 的待拍板单一列表。

# U11（第二十七轮新增）—— 段首空白的**暴露面**与**三条输入入口**，外加两次复跑的对账

# U11-a（**纯文件 + 只读 DB，不开浏览器**）—— 段首制表符到底有多少
# 扫描口径与 preserveLeadingWhitespace() **逐字同构**（遍历每个元素 → 第一个含可见字符的文本节点 →
# 看它的前导 [ \t]；pre/code/textarea/script/style/svg 整棵跳过）。第二十七轮实测：
# 249 个产物主口径里含段首空白的 5 个文件 / 9 处、**含制表符 0 处**；全部 403 个产物文件 **0 个**；
# 38 篇存量正文里 4 篇 / 36 处、**含制表符 0 篇**。
node tools/render-verify/round27_leading_ws_scan.mjs
node tools/render-verify/round27_leading_ws_scan.mjs --check <某个.html>   # 只跑口径本身，不碰数据库
# 产物：target/probe/browser/r27_leading_ws_scan.json

# U11-b（**要求 8081 起着**，端口 9364/9366）—— 四条输入入口各量「实时 DOM / 保存出口 / 再打开」三列
node tools/render-verify/browser/r27-entry-paths.mjs --label after
node tools/render-verify/browser/r27-entry-paths.mjs --label before --bundle target/probe/r26/before-dist
# ⚠️ `Input.dispatchKeyEvent` **必须先点一下编辑器**（脚本里的 `focusEditor()`），否则 Ctrl+A / Backspace /
#    空格 / Tab 全部静默失效，量到的其实是「注入时的原内容」——第一次跑就是这么拿到一屏假数据的。
# 产物：target/probe/browser/r27_entry_paths_{before,after}.json

# U11-c（**纯离线**）—— 两份结果 JSON 逐叶子值对账，**不做归一、不做容差**
node tools/render-verify/round27_c_compare.mjs <基线.json> <复跑.json> [<基线2.json> <复跑2.json> …]

# U12（**回归闸**，第二十八轮新增）—— 「打开 → 保存 → 再打开」的**往返稳定性**
# **要求应用在 127.0.0.1:8081 起着**。跑完 **exit 0 = 通过**、exit 1 = 不过（打印差在哪条样本的哪个位置）。
# 判据：① 把保存出口原样灌回去之后（走应用自己的 setContent）与**保存那一刻**的 DOM **逐叶子值相同**；
#       ② 再存一次、再灌一次仍与第一次往返后相同；③ 每条样本**自己声明要保住的量**仍然成立。
# 「逐叶子值」＝元素叶子(标签+逐属性原文) / 文本叶子(文本节点原文)，路径按「第几个子节点」拼；
# **唯一**的规范化是 style 属性值内部**声明按字典序排列**，其余一个字节都不放宽。
# 内置**闸的自检**：① 离线拿第二十六轮之前的存档实测（段首缩进 0px）套判据③，必须判 FAIL；
#                   ② `--bundle` 换修复前的整包前端实跑，必须 exit 1。
node tools/render-verify/browser/r28-roundtrip-gate.mjs --label after
node tools/render-verify/browser/r28-roundtrip-gate.mjs --label round26-before --bundle target/probe/r26/before-dist
# 产物：target/probe/browser/r28_roundtrip_<label>.json ＋ r28_dom_<label>/<样本>.<第几圈>.html
# ⚠️ **这条闸有盲区，写在这里免得后人以为它包打天下**：旧前端在「打开」和「再打开」时**都**丢段首空白，
#    A 与 B 两边一致地丢 ⇒ **判据① 在修复前的 bundle 上照样通过**。抓住这类 bug 的是**判据③**。
#    反向跑实测坐实：`--bundle …/before-dist` 下六条样本判据① 0 条叶子差异、判据② 全过，
#    而判据③ 恰好报 4 处失败（`lead-space` / `lead-tab` 各 2 处，缩进 0px）。

# U13（第二十八轮新增）—— 第 9 条「粘贴 HTML 的段首空白」的**暴露面与代价**（**本轮不改代码**）
# **要求 8081 起着**，端口 9371。回答两个问题：① 从网页复制粘贴这条路上，段首空白是真丢还是只是看着像丢；
# ② 如果改 `transformPastedHTML`，会不会碰到别的粘贴内容（表格 / 代码块 / 公式 / SVG）。
# 真 Ctrl+C 读剪贴板 text/html 原文（7 种写法）＋ 真 Ctrl+V 落进编辑器（7 份真载荷 ＋ 2 份手写载荷）＋
# 249 个产物过一遍候选修法 ＋ 6 个代表载荷「原样 vs 先过变换」逐叶子比。内置自检：切出来的真函数
# 在页面里跑三条样例断言，不过 **exit 4**。
node tools/render-verify/browser/r28-paste-html-probe.mjs
# 产物：target/probe/browser/r28_paste_html_probe.json
# 第二十八轮结论：**建议不修**——7 份真复制载荷里只有 1 份（`prewrap`）会被修法改写，
# 而那份本来就靠 `white-space: pre*` 存活（prosemirror-model/dist/index.js:2844 `localPreserveWS`）。
# 依据全文见 `docs/dev/known-issues-handoff.md` §3.29②。

# U14（第二十九轮新增）—— 「什么样的剪贴板 HTML 复制进来会丢段首空白」的**可跑判据**（纯离线，不开浏览器）
# 判据：一份剪贴板 HTML，只要「段首有 [ \t]」且「不含 white-space: pre*」，就会触发。
# 「段首空白」用的是与实现同构的那把共享尺子（`round27_leading_ws_scan.mjs` 的 `leadingRuns()`），
# 另补一路「根级文本口径」（复制选区常常不带外层块标签）。
node tools/render-verify/round29_clipboard_html_check.mjs <文件.html> [...]   # 也可 --dir <目录> / --stdin / --selftest
# 退出码：有触发 10 / 全部不触发 0 / 用法错 2
# 内置自检：跑在第二十八轮存档的 7 份**真**剪贴板载荷上，处数必须逐条对得上（本轮 7/7 ✅）。
node tools/render-verify/round29_clipboard_html_check.mjs --selftest

# U14-采（第二十九轮新增）—— 真实来源采样：真选中 → 真 Ctrl+C → 读剪贴板 text/html（**需 8081 起着**，端口 9373）
# 8 个公开网页 + 11 个同浏览器的合成对照；载荷原文落 target/probe/r29/clip/，可离线复判。
node tools/render-verify/browser/r29-clipboard-sampler.mjs
node tools/render-verify/browser/r29-clipboard-sampler.mjs --url <网址> [--选择器 <css>]   # 单点；产物另存 _single.json
# 产物：target/probe/browser/r29_clip_samples.json
# ⚠️ 采样器必须先 `Browser.grantPermissions`（不限定 origin），否则 `navigator.clipboard.read()`
#    要么直接挂到超时、要么 `NotAllowedError: Read permission denied`。
# 第二十九轮结果：19/19 采到载荷，**判「触发」的 0 份**——根因是 Blink 写剪贴板**之前**就并掉了
# `white-space: normal` 内容的段首 [ \t]（同一页里 `pre-wrap` 那份则同时保住了空白和 pre 声明）。
# 判据的**正例**用 `target/probe/r29/clip/z-判据正例_*.html` 钉住：把真实载荷里被并掉的两个空格加回去，
# 其余一字不动 → 判据翻成「触发」。所以这条路对「从浏览器网页复制」基本无效，
# 对**自己直接写 text/html 的应用**（编辑器 / 笔记软件）才可能成立。详见 §3.29③。

# U15（第二十九轮新增）—— **全库验收断言的分类 ＋ 反例自检**（纯离线：不开浏览器、不连库、不打渲染 API）
# 逐条回答三个问题：属于哪一类（甲｜只比两侧一致 / 乙｜与外部真值对照 / 丙｜自声明期望值 / 丁｜根本没有判据）、
# 能不能判红（有没有非零退出码）、**拿仓库里真实存在过的坏版本喂给它，它判了什么**。
# 判 PASS 的就是松的。退出码 0 = 每条断言的实际表现都与它自己声明的相符（1 = 表该更新了）。
node tools/render-verify/round29_gate_audit.mjs [--json]
# 产物：target/probe/browser/r29_gate_audit.json
# ⚠️ **本表是「今天的绿灯有多少是真绿」的唯一入口**。结论：21 条断言里能判红的只有 9 条（43%）；
#    更要紧的是——**九步链 15 个步骤里只有 1/9 的构建真正能判红**，其余 14 步（2/9~9g）全部没有退出码，
#    「EXIT=0」＝脚本没抛异常，不含任何通过/不通过信息。真正带判据的闸全在九步链**之外**。
# 依据与工程项（E1~E3）全文见 `docs/dev/known-issues-handoff.md` §3.30。
# ⚠️ **上面这段是第二十九轮的实况。第三十轮已把 E1~E3 补完**：九步链 14 步的退出码全部接上、
#    `round27_c_compare` / `r27-entry-paths` 也接回，本表现改为**逐行真喂坏输入看退出码**。
#    重跑后：**能判红 18/21（86%）**。当前口径见 §3.14 与 `known-issues-handoff.md` §3.31①。

# U15-b（第二十九轮新增）—— 三条甲类闸的**反例自检**（纯离线，与各自主流程**共用同一把尺子**）
# 甲类＝「只比两侧一致」，对「两侧一致地错」天然免疫。这三条闸的**定位不改**（改了就不是在量它原本量的事），
# 但它们各自补了 `--selftest`，用来证明两件事：**不误报** ＋ **对值级差异会判红**——
# 把「已知盲」和「尺子瞎了」分开。
node tools/render-verify/browser/r25-save-exit-roundtrip.mjs --selftest          # 判据①
node tools/render-verify/browser/r16-compare-probe-vs-live.mjs --selftest        # 探针页 vs 真实界面
# `r26-leading-ws-effect.mjs --selftest`（第二十九轮收紧判据②③时一起立的）见 §3.12 的 U12 段。
# 三条都 **EXIT=0**。`r26-leading-ws-samples.mjs --compare` 同样属甲类，但它的职责是**不变量**
# （证修法不改动 79 个样例的可观测行为），`--compare` 喂坏量测会如实判红，所以只补了头部说明。

# U16（**回归闸**，第三十四轮新增）—— 表格修法的**反作用**：手写表格会不会被标记类误伤
# **要求应用在 127.0.0.1:8081 起着**，端口 9374。跑完 **exit 0 = 手写表格没被误伤且指纹判据是活的**。
# 判据：③ 工具栏「插入 3×3 表格」的保存出口**原样灌回去**（= 用户重开这篇文章）后，
#       `table.className` 里**没有** `mf-preserved`、网格线仍是 `1px`；
#       ④ **同源正向对照**：同一份出口只多一个 `border-collapse:collapse`，类**必须**出现。
#       一负一正同跑——只有 ③ 时，「没打类」也可能只是脚本走空了。
node tools/render-verify/browser/r34-table-handmade-regression.mjs
# 产物：target/probe/r34/table_handmade_regression.json / .txt
# ⚠️ 判据是**产物指纹**（表级 style 里有没有 `border-collapse: collapse`），**不是**「<table> 上有没有 style」——
#    后者 TipTap 自己也会写（手写表格的出口就是 `<table style="min-width: 75px;">`），
#    第一版判据（`preservedStyle` 非空）就是这么把手写表格误伤成「网格线整片画没」的（逐格边框 1px → 0px）。

# U17（第三十四轮新增）—— 表格「比原项目高」的**逐项取证**与标记类指纹的**离线扫描**

# U17-a（**要求 8081 起着**）—— 六个面（原项目 / 编辑器 / 裸容器 / 消融组 / 候选修法 / 字体）＋ 全产物 sweep
node tools/render-verify/browser/r34-table-metrics.mjs                        # 单用例四面对照 + 候选修法（主场）
node tools/render-verify/browser/r34-table-metrics.mjs --sweep [--only <id>]  # 全部产物过一遍候选修法
# 预期数字（第三十四轮实测）：单用例 原项目 **197** / 编辑器 **212**（改前 347.09）/ 裸容器 **212**；
#   sweep **PASS 11 · WARN 0 · FAIL 0**；表级 `table-layout` 改前 `fixed` → 改后 `auto`；
#   采样格边框 `1px 1px 1px 1px` → **`0px 0px 1px 0px`**（与原项目逐字符相同）；
#   列宽 四等分 `[157,157,157,157]` → `[105.67,165.64,133.83,223.86]`（原项目 `[105.02,165.13,136.34,222.52]`）。
# ⚠️ 锚是**裸容器**而不是「改前」：拿「改前」当基准，量到的 `fixed` + `1px` 边框**其实是兜底样式本身**，
#    不是产物真值。脚本自带**锚洁净闸**（裸容器被污染就整条判 FAIL，不拿脏基准去判别人）；
#    把锚故意污染回第一版的样子 → 实测翻成 **PASS 4 · FAIL 7**，七条全报「锚被污染」。
# 产物：target/probe/r34/table_metrics.json / table_sweep.json

# U17-b（**纯离线**，只读落盘产物）—— 拿什么当「这是渲染服务产物表」的指纹
node tools/render-verify/r34_table_fingerprint_scan.mjs
# 预期数字：有表格的产物 **14** 个，表级 style 带 `border-collapse` 的 **14/14**，且每格都自带 style
#   （`r16-09` 20/20、`cmb-long-table` 84/84）；手写表格 `min-width: 75px;` **一条不占**。
# 产物：target/probe/r34/table_fingerprint_scan.txt / .json

# U18（第三十四轮订正）—— 9g 的差异条目基线**由「一个总上限」改成「逐例上限」**
# 原先只钉总数（`<= 152`）时，「甲例涨 20、乙例降 20」总数不变、闸照样判绿——而这两件事含义完全相反。
# 现在 `browser/summarize-r16.mjs` 里是 `每例上限` 映射（总和即总上限），另加第 6 条自检用例
# 「总数不变、差异在两条用例之间搬家」：**只钉总数时判绿、钉到例之后判红**（实测）。
node tools/render-verify/browser/summarize-r16.mjs --selftest   # 六条用例全过、EXIT=0
# 当前逐例上限与该轮被修订那一例（`r16-09-table-card` 5 → 25）的逐条归因写在脚本文件头。

# U19（第三十五轮新增）—— 粘贴 HTML 的**段首空白修法**：反例自检 ＋ 真函数未动 ＋ 暴露面（纯离线，不开浏览器）
# 修法本体在 `webui/src/editorExtensions.js` 的 `PastedLeadingWhitespace`（`transformPastedHTML` 一行挂到粘贴路）。
# 本支只回答三件事：① 旧包判红 / 新包判绿（**唯一自变量是前端包**，同一份载荷、同一支脚本两次实跑）；
# ② 真函数**字节未动**（把新增那段切掉后切块 sha 与第二十八轮记录逐字相同）＋ 段首空白在敏感子树外的暴露面；
# ③ 源码级四个行号（调用点 / 拖动同路 / `preserveWhitespace` 开关 / `readHTML`）都在。
node tools/render-verify/browser/r35-paste-fix-audit.mjs      # 退出码 0 = 三件事全过
# 前置输入（缺了会 exit 2）：`target/probe/browser/r27_entry_paths_r35-before.json`（旧包）
#   与 `..._r35-after.json`（新包）——分别由下面两条命令产出：
#   node tools/render-verify/browser/r27-entry-paths.mjs --bundle target/probe/r35/before-dist --label r35-before
#   node tools/render-verify/browser/r27-entry-paths.mjs --label r35-after
# 第三十五轮实测：旧包实时缩进 **[0,0,0]** / 出口无 `&nbsp;`（判红 ✅）；
#   新包 **[6.72, 26.88, 0]** / 出口 `<p>&nbsp;&nbsp;LEAD-SP</p>` / 再打开仍 [6.72,26.88,0]（判绿 ✅）；
#   另外四条路（setcontent / pastePlain / pasteHtmlFixed / type）**15 个相位逐字节未变**；
#   真函数切块 sha 前 16 位 **`bae2e6188257bd6f`**（第二十八轮记录的同一个值，1590 字节）；
#   段首 `[ \t]` 在敏感子树之外的暴露面：352 个产物里 57 个文件 / 91 处。
# 产物：target/probe/r35/paste_fix_audit.json / .txt
# ⚠️ **部署这一步不能省**：`spring-boot:run` 不跑 `prepare-package`，改完前端必须自己把产物送进去
#    （`npm run build -- --outDir ../target/classes/static --emptyOutDir`），否则界面还是旧包——
#    第三十五轮实测：改完不部署时 8081 发的是 `ArticleEditorView-CGjhQ8ZC.js`，部署+重启后才是 `CGOKQBSD.js`。

> **判据为什么要单独一支**：改判据不该重跑浏览器。第二十二轮的结论正是靠反复改判据收敛的——
> 异宽时 **13/70** 组有差（全部是 47px 宽度派生值），`--width-match` 下 **0/70**
> （两种跑法都另有 1 组只差图片加载时序，已单列不计入）。**`--width-match` 那份退出码 0。**
> 该脚本会主动过滤三类已知假阳性：图片加载时序、宽度派生值、tiptap 应用层痕迹
> （`draggable`、`<div class="tableWrapper">`、行内 style 属性顺序）。
>
> `--dom` 加一段 **DOM 字符串**比对。⚠️ **它不是承重判据**：探针页量的是 `editor.getHTML()`
> （保存出口），真实界面量的是 `dom.innerHTML`（实时 DOM 出口），两边出口不同，
> 实测 **0/11**；抹掉 ProseMirror 编辑期管道（`ProseMirror-separator` / `ProseMirror-trailingBreak` /
> `contenteditable` / `tableWrapper`）后 **10/11**。**两个数字都不该拿来当结论**，
> 承重的是上面那张计算样式表。剩下那 1 条是真差异，见 §3.22②。

> **U7 不写库**：脚本在应用脚本之前 patch 掉 `fetch`/`XMLHttpRequest`（非 GET 的 `/api/*` 本地伪造 200），
> 网络栈上再用 CDP `Fetch` 域拦一层，跑完回读文章 `revision`/`updatedAt` 逐字比对，
> 收尾还故意敲一次键逼出自动保存来自证拦截生效。**换文章 ID 也可以**（默认 38），
> 但那一篇在跑之前会被读两遍（改前 / 改后），别拿正在编辑的稿子跑。

> **U4 / U5 各支已进版本控制**，因为它们的输出被写进了结论：第 6 条的 `liveCols`
> （`min-width:25px` + `width:90px`）、第 11 条的 `borderCollapse/borderSpacing/minWidth`、
> 第 7/8 条「480px 最小高度是测量假象」、第 9 条「余下那条高度差是两栏宽度不等引起的」
> 都直接取自这里。按本目录的摆放原则，**被结论引用的脚本
> 不能在 clone 里缺席**，否则「换台机器复现」到这几条就断。
> 纯排查稿 `target/probe/r16/dump-summary-row.mjs`、`diag-plugin.mjs`、`row3-*.mjs`、
> `pane-inner-widths.mjs` 留在 gitignored 目录，用完即弃。
> 要长期留存的是 `gen/round16_editor_reported.py`、`gen/round18_infographic_variants.py`、
> `gen/round20_field_block_scalar.py`、`browser/probe_r16.js`、`probe_r16.html`、
> `run-r16-browser.mjs`、`summarize-r16.mjs`、`r16-shot-zoom.mjs`、`r16-dump-live-table.mjs`、
> `r16-measure-heights.mjs`、`r16-width-equiv-test.mjs`、`r16-probes.js`、
> `r16-live-editor.mjs`、`r16-compare-probe-vs-live.mjs` **十四支**。
> 其中 `r16-probes.js` 与 `probe_r16.js` 是**一对**：前者放探针定义、后者放「怎么摆两栏」，
> 第二十二轮拆开的理由见 `tools/render-verify/README.md`。

> **第二十三轮补的三件事**（都在 §3.23）：
> ① **三条输入路径的量法**——`setContent` / 粘贴 / 手打**结果不同**（同宽探针差异 0 / 47 / 65 组；
>   11 条用例的容器 `<section>` 合计 51 / 32 / **0**），判据与逐条结论在 `known-issues-handoff.md` §3.23①；
>   用户实际走哪条路由**他那篇文章的存库正文**定案，不靠推断。
> ② **第 4 条 `:::quote-card` 已修**——修的是 `webui/src/editorExtensions.js` 的
>   `syntheticParagraphStyle`（合成段落里的行内内容整层脱离文档流时不给段落间距）。
>   验高低的量法用 `r16-measure-heights.mjs`（**不是** `r16-live-editor.mjs`：
>   后者的 `editor` 侧只带计算样式项，`kind:children` 的「卡片构成」探针 `count` 恒为 0，拿不到几何）。
> ③ **差异条目口径钉死**——`summarize-r16.mjs` 现在把
>   `Σ details[].differences[].notes.length` 算成 `diffEntries` 写进 `r16_summary.json` 并打在 `.md` 表头。
>   **不要手数 `.md` 的条目**：它每组最多打 12 条（`notes.slice(0, 12)`），手数会少算 3 条（152 数成 149）。

> **第二十四轮补的两件事**（都在 `known-issues-handoff.md` §3.24）：
> ① **验收对象换成用户自己的文章 #38**（真实窗口宽度，不用 `--width-match` 造同宽）——
>   新脚本 `browser/r24-article38-symptoms.mjs`：`--label <名>` 给产物打标、
>   `--bundle <dir>` 用 CDP `Fetch.fulfillRequest` **整包替换前端**（拿改前 bundle 跑出「改前」列）、
>   `--inject <caseId>` 把某条用例的产物灌成 #38 的正文（走应用自己的 `setContent`）、
>   `--paste`（配合 `--inject`）走粘贴。**换的确实是两份 bundle** 靠打印
>   `performance.getEntriesByType('resource')` 里的 chunk 名自证（改前 `ArticleEditorView-BfXgtpun.js` /
>   改后 `ArticleEditorView-BFvBiRez.js`）。⚠️ 整包替换时**所有非 `/api` 非静态资源路径都要映射到 bundle 的
>   `index.html`**，否则第二次导航会被应用自己的 `index.html` 接走、静默加载**修好的** bundle，两边量出来一样。
> ② **保存出口实测**——`--paste` 路径下在写保护拦下 `PUT` 时留 `body.contentHtml` 数 `<section>`：
>   **32 层 ＝ 实时 DOM 的 32 层**（产物 51），把第二十三轮那句「`getHTML()` 也一样少」由推理变成实测。

> **第二十五轮补的三件事**（结论都在 `known-issues-handoff.md` §3.26）：
> ① **第二十四轮④的量法有盲区**：它是「拿今天新产的 HTML 灌成正文再量」，量出来当然等于产物，
>   只能证明「编辑器不再丢声明」，**证不了「用户看到的样子存下去还在不在」**。
>   U8 补的就是这一刀：把拦下的 `PUT` 里的 `body.contentHtml` **原样**灌回、量**同一组 11 条探针**、
>   与拦截那一刻的活 DOM 逐条对照（整段 DOM 还要**重排 `style` 声明顺序**后才逐字节比——
>   浏览器序列化 `#fff`→`rgb(255, 255, 255)`、`#e2e8f0`→`rgb(226, 232, 240)` 并会重排声明，
>   这是**去掉「同语义不同字节」的噪声，不是放宽判据**）。
>   **`setContent` / 粘贴两条路径结论一致：幂等**；手打那条有差异，但收窄实验证明
>   **丢在「第一次解析」（还没保存就丢了），保存出口本身仍是幂等的**（①解析后 vs ②重灌后 10/10 相同）。
>   根因是 `setContent` 默认 `parseOptions = {}` 走塌缩空白的解析路径。**未改代码**：
>   显式打开 `preserveWhitespace:'full'` 能救回段首空白，但会**动整篇块结构**（顶层块 14→15、`dy` 整体位移、
>   块高 280.69 → 87.75 / 1548.69），副作用已记在 §3.26① 交用户拍板。
> ② **同题对照才能定「是谁丢的」**：同一份探针输入分别喂改前 / 改后 bundle 各逼一次保存出口，逐字节比。
>   结论是**两类**不是一类——列宽塌成 25px 确实是**旧编辑器保存时丢的**；changelog 缺容器**不是**
>   （旧编辑器出口里 `border-radius:12px` / `rgb(226,232,240)` / `1px solid` 计数与改后**完全相同**），
>   属「正文停在更早的渲染版本」。**改判了第二十四轮「旧编辑器把 changelog 边框丢了」那句，范围收窄到 #38 那一份数据。**
> ③ **存量影响面只看一个字段**：`round25_stock_scan.mjs` 的每条判据都只是 `ARTICLE.CONTENT_HTML` 的纯函数
>   （不信今天的时间点、不启浏览器，谁都能重跑）。38 篇未删除文章里命中任意一条的 **2 篇（5.3%）**；
>   但 `rerender()` 的两个硬前置条件（MARKFLOW + 有 Markdown）恰好**这两篇都不满足**——
>   「重渲染存量」这条现有路径**碰不到任何一个能修的对象**。

> **第二十六轮补的两件事**（结论都在 `known-issues-handoff.md` §3.26 A / §3.27）：
> ① **段首空白真的修掉了**，而且是本书判据先立后测跑出来的。窄修法落在
>   `webui/src/editorExtensions.js` 的 `preserveLeadingWhitespace()`（只替换**每个块里第一个含可见字符的文本节点**的
>   前导 `[ \t]` → `&nbsp;`；`pre`/`code`/`textarea`/`script`/`style`/`svg` 一律不碰，
>   纯空白文本节点也不碰——否则会凭空造出可见空白），接在 `ArticleEditorView.vue` 的两处 `setContent` 上。
>   **不走 `parseOptions`**：那条路只有 `wsOptionsFor(type, preserveWhitespace, base)` 一档布尔 / `'full'`，
>   没有「只作用于前导」的粒度（第二十五轮已用源码否决）。
>   两条判据都过：**段首空白存活 1/7 → 7/7**、**79 套样例的块结构/几何逐条不变 0 条不同**
>   （顶层块 203、`section` 338、`htmlChars` 129351、`textLength` 3316 四项合计改前改后逐字相同）。
>   ⚠️ 量「改后」那一列必须让 8081 真的发新包：`webui/dist` 建好不等于 8081 在发它。
> ② **①②③④⑤⑥⑦⑧⑨⑩⑪ 之外的第十二处回归面（列宽）进了常规套件**：`r26-table-colwidth-exit.mjs`
>   是**带退出码的闸**（U10），不是一次性对照实验；它自带反例自检（旧编辑器出口必须被判 FAIL）。
>   **顺带量到一个此前没人查过的侧问题**：`data-colwidth` **只是入口写法，出口不保留它**——
>   出口侧出现 0 次；保命的是 TipTap 自己输出的 `colwidth="90"` 属性与 `<col style="width: 90px;">`。

> **第二十七轮补的三件事**（结论都在 `known-issues-handoff.md` §3.28）：
> ① **上一轮那处代价收掉了**：`preserveLeadingWhitespace()` 里「1 个 `\t` → 1 个 `&nbsp;`」改成
>   **按制表位等宽展开**（`TAB_SIZE = 8`，与 `.ProseMirror` 的 `tab-size` 一致；`"\t  "` = 10 列、`" \t"` = 8 列）。
>   ⚠️ **改 `TAB_SIZE` 必须同时改 CSS 的 `tab-size`**，否则缩进宽度会跟着偏。
>   先量暴露面才决定改法：段首制表符在 249 个产物、403 个产物文件、38 篇存量正文里**出现 0 次**（U11-a），
>   所以「含制表符就不替换」那条路净亏损，被否决。三条判据全过：**存活 7/7**、
>   **79 套样例 0 条不同**、制表符首字符落点 **224.89 → 224.88**（差 0.01px，半角空格逐值不变）。
> ② **判据的「输入入口」这一维补齐了**（U11-b）：`setContent` / 粘贴 HTML / 粘贴纯文本 / 手打 四条路
>   各给「实时 DOM / 保存出口 / 再打开」三列。两个可复用的结论：**`text/html` 走 DOMParser（空白折叠）、
>   `text/plain` 走 prosemirror-view 的文本解析器（保留前导空白）**；以及修复挂在 `setContent` 上，
>   但**顺带修好了「粘贴纯文本 → 保存 → 再打开」与「手打 → 保存 → 再打开」**这两条往返丢失。
> ③ **产品代码变了就全文重验**（U11-c 就是为此加的离线对账器）：#38 十一条四列表**改前/改后两列各 125 个叶子值逐项全同**；
>   七套回归照旧与第廿一轮快照一致（四份摘要**逐字节相同**，`r16_summary` 仍只差自算字段 `diffEntries=152`，
>   `r16_result` 仍只差 `r16-04-quote-card` 那个 `<p style="margin: 0px;">`）。

> **第二十八轮补的三件事**（结论都在 `known-issues-handoff.md` §3.29；**本轮没改产品代码**）：
> ① **整圈钉成一道带退出码的常规闸**（U12）：三条判据（往返稳定 / 二次往返仍稳定 / **入口保真**）＋
>   反例自检（离线存档 ＋ `--bundle` 旧整包），过 **exit 0**、不过 **exit 1**。
>   ⚠️ **必须知道它的盲区**：只比「两次测量自洽」的判据（①）**抓不住**「打开和再打开都丢」这类 bug——
>   反向跑实测旧 bundle 上判据① 照样 0 差异通过，抓住它的是**判据③ 入口保真**。别把①当万能。
> ② **第 9 条的「修不修」有了完整依据**（U13）：结论**建议不修**。7 种缩进写法里真复制载荷只有 1 份
>   （`prewrap`）会被候选修法改写，而那一份本来就靠 `white-space: pre*` 存活
>   （`prosemirror-model/dist/index.js:2844` → `localPreserveWS = true`）；249 个产物过一遍修法只有
>   4 个文件 / 8 处被改写，命中 `table/pre/code/katex/svg` 的 **0 处**。**收益为 0，代价是动一条没坏的全局路径。**
> ③ **探针浏览器进程泄漏已修**（`browser/cdp.mjs`）：Windows 上 Chrome 的启动器会另起浏览器进程再自己退出，
>   `browser.kill()` 打空 ⇒ 一轮跑下来机器上攒过 **1828 个 `chrome.exe`**，把 CDP 事件压到超时、
>   量出来的全是环境噪声。现在 `close()` 按本次启动专用的 `--user-data-dir` **全机匹配**收进程（`spawnSync` 等它收完），
>   跑完自检残留 **0 个**。⚠️ **跑浏览器套件前先看这一条**：若发现 `probe-chrome-*` 进程堆积，先清干净再量。
>   ⚠️ **第三十三轮补充**：它只收**进程**、**不收目录**——`cdp.mjs:34` 的 `mkdtempSync(tmpdir(),'probe-chrome-')`
>   每跑一次浏览器套件就在系统临时目录留下一个 profile（实测攒到 7 个 / 约 105 MB），全文件 `rmSync` 出现 **0 次**。
>   同类还有 `round27_c_compare.mjs:114` 的 `r27c-*`（每次 `--selftest` 漏一个）。两处都**只记录、未修**，
>   见 `known-issues-handoff.md` §3.34⑦。

> **第三十四轮补的四件事**（结论都在 `known-issues-handoff.md` §3.35；**本轮改了产品代码**，所以整条链当轮重跑）：
> ① **用户点名的第 9 条（表格）修法**：`style.css` 那套给**手写表格**用的兜底（`table-layout:fixed`、
>   单元格 `1px` 边框、`line-height` 继承 `1.95`）会把渲染服务写下的表格排版整片盖住。修法是打开正文时
>   把产物 `<table>` 的 `preservedStyle` 原样贴回并打标记类 `mf-preserved`，兜底那三条改成**只对没有这个类的表格生效**
>   （`editorExtensions.js` 的 `applyPreservedTableStyle()` / `PreservedTableView` + `style.css` 两条规则）。
>   两条判据动手前写死：① 几何朝原项目靠；② 手写表格**逐字节不受影响**。
> ② **判据的锚是「裸容器」不是「改前」**（U17-a）——这一处是本轮最值得复用的方法要点：
>   拿「改前」当基准，量到的 `fixed` + `1px` 边框**其实是兜底样式自己**，不是产物真值。
>   锚定裸容器后判据是可证伪的一句话：**打类后的逐边边框必须与裸容器逐字符相同**。
>   ⚠️ 第一版 sweep 的 `make(withClass)` **只有一个参数**，调用处却写 `make(false, false)`——
>   第二个实参被静默忽略，那个所谓「裸容器」也带 `.ProseMirror`，7 个产物被误报成「网格线被画没」。
>   修法是加 `withProseMirror` 形参并把「锚洁净」做成判据的一部分（锚脏就整条 FAIL），修后 7 个 FAIL 全翻 PASS。
> ③ **标记类的判据是产物指纹，不是「有没有 style」**：TipTap 给手写表格自己就会写
>   `<table style="min-width: 75px;">`，所以第一版「`preservedStyle` 非空就按产物表处理」会误伤手写表格
>   （`.mf-preserved th,td{border:0}` 把网格线整片画没）。指纹取**表级 style 里有没有 `border-collapse: collapse`**
>   （`PRODUCT_TABLE_STYLE`），纯离线取证 14/14 命中、手写表格一条不占（U17-b）。
> ④ **回归**：九步链 **16 步 / 1 步非零**（仍只有 6/9——`#35`、`#16` 的 13 张外链图 13/13 返回 404，外部图床失效，
>   **保留红灯不豁免**）；9g 由 1 → 0（差异 172 > 152，**11 例里只有 `r16-09-table-card` 变了 5 → 25**，
>   其余 10 例一条不差；成因是本支参照栏（探针页的 `.probe-canvas.ProseMirror`）**不是原项目**、
>   被兜底样式污染成 `fixed` + `25.35px`，修法让编辑器栏不再吃兜底 ⇒ 原本被「两边一起脏」抹平的差如实显形）；
>   9h 由 1 → 0（第 15 行跨代混喂改成同代成对喂 ＋ 第 5 行补记逐例锚）。
>   部署用仓库既有的手工补救命令（`rm -rf target/classes/static && cp -r webui/dist target/classes/static`），
>   **不是**改构建配置。

> **第三十五轮补：段首空白在「粘贴」这条路上也修好了**（用户原话「粘贴 HTML 时段首空白丢失要修」）。
> 第二十六轮的窄修法只挂在 `setContent`（打开文章）那一条路上；粘贴走的是
> `prosemirror-view` 的 `parseFromClipboard()`，它自己的 `preserveWhitespace: !!(asText || sliceData)`
> 会在解析那一刻把段首 `[ \t]` 并掉。修法是给 `transformPastedHTML` 挂同一个函数
> （`editorExtensions.js` 的 `PastedLeadingWhitespace`）。**判据与量法见本手册 U19**。
> 部署这一步**必须自己做**：改完前端跑
> `cd webui && npm run build -- --outDir ../target/classes/static --emptyOutDir`，再重启应用；
> 否则 8081 发的还是旧 chunk（第三十五轮实测：不部署时是 `ArticleEditorView-CGjhQ8ZC.js`，
> 部署后是 `ArticleEditorView-CGOKQBSD.js`）。
> 该修法连同第十五 ~ 三十四轮的累积改动已于 2026-09-14 提交并推送（`b591c40` → `huanyu/main`）。

**本轮实测（2026-09-13，Chrome/138.0.7204.100，视口 1600×1200，图片 2/2）**：

| 项 | 值 |
| -- | -- |
| 后端侧 | 11 条里 **10** 条语法被正常识别（第 9 条 `:::table style="card" title=` 因 `title` 被丢判 DEFECT） |
| 编辑器侧 | 11 条里 **1** 条逐属性一致（第 2 条 `:::subscribe`）——这是**含已知假阳性**的口径 |
| 差异条目总数 | **174 → 152**（改动前 → 改动后） |
| 修好的可见症状 | 第 7/8 条的圆点与方框恢复（summary 可见元素 19→31、infographic 12→18）、第 11 条的表级声明（`border-collapse:separate` / `border-spacing:12px 0` / `min-width:600px`）全部贴回、第 6 条列宽 `344.5/344.5 → 599/90`（产物是 `637/90`） |
| 第 9 条的收尾（第十七轮） | 行高差异 6→2 后余下 2 条**都不是编辑器缺陷**：1 条是表头取样错位，1 条经 `r16-width-equiv-test.mjs` **双向证伪**为两栏内容盒差 38px 引起的多折一行（见 `known-issues-handoff.md` §3.20⑤c） |
| 未修 / 等上游 | 上游渲染缺陷 **§R5–§R9**（5 条），另 **§R10（P3）**：第 10 条在第十八轮从「未复现、不立项」改判为立项，**第二十轮又对这条改判本身做反向自查**，证明缺 `\|` 属「写法不合引擎的字段级块标量约定」，遂把 §R10 **降级为「文档未覆盖 + 静默失败」，不主张渲染 bug**（脚本 `gen/round20_field_block_scalar.py`，数字见 `target/probe/r16/r10_by_design.json` + `r10_blockscalar.json`） |

**改动前后的红绿对照**（每一条都是同一支探针、同一台浏览器，不是推断）见
`docs/dev/known-issues-handoff.md` §3.20③。**改完之后 M/C/A/R 全部重跑，数字逐项不变**（§3.20⑥）。

### 3.13 自查「8081 现在发的是不是最新包」（**重启应用之后跑这一节**）

**为什么需要**：改了 `webui/src/` 之后，「源码改了」与「用户打开 8081 看到的」之间有两道独立的坎——
① 前端要**重新构建**（`npx vite build`）；② 构建产物要进 `target/classes/static/`（`spring-boot:run`
**不经过** Maven 的 `prepare-package`，见 §3.10②）。任何一道没跨过，用户看到的就还是旧界面，而探针可能全绿。

**一条命令判生死**——下面这段把「8081 实际服务的那份」与「源产物」「目标产物」三者做**哈希比对**，
再在服务出的 chunk 里 grep 本轮的关键标识。三者哈希一致且标识命中 ⇒ 用户看到的就是最新包。

```bash
# 1) 8081 现在引用了哪些 chunk
curl -s http://127.0.0.1:8081/ | grep -o 'assets/[A-Za-z0-9_.-]*\.\(js\|css\)' | sort -u

# 2) 逐文件比对：8081 实际服务 vs 源 webui/dist vs 目标 target/classes/static
for f in index-m7nGXL7H.js index-DQb2H7fJ.css ArticleEditorView-BfA0z7Us.js; do
  curl -s -o /tmp/served "http://127.0.0.1:8081/assets/$f"
  printf '%-32s 8081=%s dist=%s classes=%s\n' "$f" \
    "$(sha256sum /tmp/served | cut -c1-16)" \
    "$(sha256sum "webui/dist/assets/$f" | cut -c1-16)" \
    "$(sha256sum "target/classes/static/assets/$f" | cut -c1-16)"
done   # 三个哈希必须两两相同

# 3) 关键标识必须在服务出的 chunk 里（示例：第十六轮的四条修复）
curl -s http://127.0.0.1:8081/assets/ArticleEditorView-BfA0z7Us.js \
  | grep -o -e syntheticBlockStyle -e preservedTableStyle -e data-colwidth -e ProseMirror-separator | sort | uniq -c
curl -s http://127.0.0.1:8081/assets/index-DQb2H7fJ.css | grep -o 'img\.ProseMirror-separator{[^}]*}'
```

**2026-09-14 实跑结果（第十八轮）**：四个文件三处哈希**两两相同**，四个标识**全部命中**——

| 文件 | 8081 服务 | 源 `webui/dist` | 目标 `target/classes/static` |
| --- | --- | --- | --- |
| `index-m7nGXL7H.js` | `0fedc56ddfeb3efe` | 同 | 同 |
| `index-DQb2H7fJ.css` | `206c31387a2e8d9e` | 同 | 同 |
| `ArticleEditorView-BfA0z7Us.js` | `37749c42564f131f` | 同 | 同 |
| `ArticleEditorView-CHS7y3f9.css` | `5efbda46a46afe0e` | 同 | 同 |

**时序佐证**（哈希之外的第二条独立证据）：源码 `webui/src/editorExtensions.js` mtime `09-13 23:40:18`
→ `webui/dist/…BfA0z7Us.js` 构建 `23:52:13` → `target/classes/static/…` 落地 `23:58:42`。**产物晚于源码**，
故不可能出现「源码改了、8081 还在发旧包」。

> **注意第 2 步的文件名要换**：chunk 名带内容哈希，**每次构建都会变**。上面的名字是本次构建的，
> 换构建后先用第 1 步拿到新名字。同理，第 3 步 grep 的标识名也要换成**你这次改动引入的名字**
> （只 grep 历史名字，会得到「旧包也命中」的假绿）。
>
> **已知的例外**：`mvn clean` 之后只跑 `spring-boot:run`，`target/classes/static/` **根本不存在**，
> 应用起来是空白界面——这不是「旧包」而是「无包」，见 §3.11③④。

### 3.14 常设命令链（**第三十轮入库**，一条命令跑完九步 + 收尾审计）

> ⚠️ **编号更正（第三十一轮复查）**：本节最初写成 `§3.13`，与上面那节
> 「3.13 自查 8081 现在发的是不是最新包」**撞了号**（同一份文档里两个 `### 3.13`）。
> 第三十一轮盘点文档与代码一致性时发现并改成 **§3.14**；README 与交接文档里的交叉引用同步更正。
>
> ⚠️ **在此之前，这条链根本不在版本控制里**：它只存在于 gitignored 的
> `target/probe/r29/_run_suite.sh`，干净 clone 之后**没有这一步**——而「九步全绿」是本项目
> 对外最常引用的一句话。第三十轮把它搬进来，并各步的退出码一并补完（见下）。
>
> ⚠️ **但它不是「干净 clone 一条命令跑到底」**（第三十一轮查明，如实记下）：链的起点是
> `1/9` 探针 dist 重建，**不含 `gen/` 那几支出网的生成器**（`component_matrix.py` 等）。
> 所以 `2/9`~`9g` 读的 `target/probe/components/*`、`component_matrix.json` 必须**已经存在**；
> 干净 clone 里要先照 §3.2 / §3.4 把上一级的产物打出来，再跑本节这条链。
> 详见下面「这条链的起点在哪」。

#### 这条链的起点在哪（第三十一轮复查补记）

`run-suite.sh` 里 **没有任何一行引用 `target/probe/r29/_run_suite.sh` 或那个目录**
（`target/probe/r29` 只在脚本第 4 行的说明注释里出现过一次），所以「搬进版本控制」这层是干净的。
但它**只覆盖了九步链本身，不覆盖链的前置产物**——逐支读一遍输入，硬前置是这六份：

| 前置产物 | 由谁产出 | 链里有没有 |
| --- | --- | --- |
| `target/probe/components/*.html` ＋ `component_matrix.json` | `gen/component_matrix.py`（**出网**，打真实渲染 API） | ❌ 没有（§3.2） |
| `target/probe/combos/` | `gen/round8_combos.py`（**出网**） | ❌ 没有 |
| `target/probe/alt/` | `gen/round10_alternatives.py`（**出网**） | ❌ 没有 |
| `target/probe/registry/` | `gen/round10_registry_closure.py`（**出网**） | ❌ 没有 |
| `target/probe/r16/` | `gen/round16_editor_reported.py`（**出网**） | ❌ 没有 |
| `target/probe/round10_article_coverage.json` | `gen/round10_article_coverage.py`（**要先手工导出 TSV**，见 §3.7） | ❌ 没有 |

结论：**「干净 clone 里有一条命令能跑完九步链」这句话，第三十轮之后成立；但「一条命令从零复现全部产物」不成立**
——那需要出网打渲染 API（要令牌），第三十一轮**没有扩链**，只把这句话的真实边界写在这里。
取一份现成的 `target/probe/` 产物树之后，`run-suite.sh` 是可以独立跑通的（本轮实测，见 §3.14 下方的实测结果）。

```bash
# 前置：应用在 127.0.0.1:8081 起着（6/9、7/9 要真应用）。探针 dist 由 1/9 自己重建。
bash tools/render-verify/run-suite.sh
bash tools/render-verify/run-suite.sh --no-build    # 跳过 1/9 的探针 dist 重建
# 日志：target/probe/run_suite.log
# 退出码：0 = 每一步都是 0；1 = 有步骤非零（末尾逐个列出）。
```

链的组成（**串行，不要并行**）：`1/9` 探针 dist 重建 → `2/9` 全量样例 79 → `3/9` 组合条件 17 →
`4/9` 等上游替代 10 → `5/9` 注册表全族 76 → `6/9` 真实稿件 14 → `7/9` 活体前端 43/44 →
`8/9` r16 本体 11 → 汇总层 `9a`~`9g` → **收尾审计 `9h`**：

```bash
node tools/render-verify/round29_gate_audit.mjs          # = 链里的 9h
node tools/render-verify/round29_gate_audit.mjs --json   # 另落 target/probe/browser/r29_gate_audit.json
# 退出码：0 = 每条断言的实际表现都与它自己声明的相符；1 = 表该更新了
```

**这一步回答的是「今天的绿灯有多少是真绿」**：它逐条给全库 21 条验收断言分类
（甲｜只比两侧一致 / 乙｜与外部真值对照 / 丙｜自声明期望值 / 丁｜没有判据）、标出谁能判红，
并且**真的喂一份坏输入**去看它判了什么——浏览器各支跑自己的 `--selftest`（不开浏览器），
汇总层与两条离线比较器把产物**复制**到临时目录改坏一处再跑
（`RENDER_VERIFY_PROBE_DIR` 这个环境变量就是为这件事开的口子，见 `paths.mjs`；**真产物一个字节不动**）。

> ⚠️ **口径分界线（第三十轮）**：九步链 **15 个步骤在第二十九轮及以前只有 1/9 的构建真正能判红**，
> 其余 14 步（2/9~9g）**没有退出码**——那时的 `EXIT=0` 只表示「脚本没抛异常」，不含任何通过/不通过判据。
> **第三十轮把 14 步的退出码全部补齐**（判定口径一个字没改，只是把脚本本来就在打印的 fail 数接到退出码上），
> 并把 `round27_c_compare`（E2）与 `r27-entry-paths`（E3）的退出码也接回。
> **从第三十轮起，「九步全绿」才可以按字面读。** 历史汇报里的那些数字本身仍然是真的
> （用例数、pass/na/fail 分布、截图、逐条差异表都在），收窄的只是「`EXIT=0` ⇒ 这一步通过」这个推论；
> `docs/render-acceptance-report.md` §五 第 12 条加了一条注记说明当时那个数字的真实含义，**历史结论不重写**。
>
> **第三十轮的实测结果**：16 步里 **6/9 是真红**（`EXIT=1`）——`#16` / `#35` 正文引用的 13 个外链图
> （`robocopmao.github.io`）**13/13 全部返回 404**（站点根 `/` 仍是 200，图没了），属**外部图床失效**，
> 不是本项目缺陷。**保留红灯，不豁免、不放宽判据**；其余 15 步 `EXIT=0`。

---

## 四、第十二轮数字快照（本轮全部当轮重跑，未复用旧值）

| 项 | 值 | 日志/产物 |
| --- | --- | --- |
| 后端全量测试 | **308 例 / 0 失败 / 0 错误 / 0 跳过 / BUILD SUCCESS，2:45** | `target/probe/round12_tests.log` |
| `webui npm run build` | **✓ 405ms** | `target/probe/round12_webui_build.log` |
| 79 样例 | pass 70 / na 9 / fail 0（79 张截图） | `browser/all_summary.md` |
| 17 组合 | 上游 8/8/1；编辑器 17/17 pass | `browser/combo_summary.md` |
| 10 替代写法 | 后端 10 ok / 编辑器 10 pass | `browser/alt_summary.md` |
| 76 组 `layout-*` | 后端 not-rendered 76 / 编辑器 na 76 / fail 0 | `browser/registry_summary.md` |
| 终稿对照表 | 63 / 13 / 50 / 38 / 25 / 25 / 悬空 0 | `round10_component_paths.md` |
| 独立交叉验证 | 18 组全部确认 / 0 存疑 / 对照组 0 误判 | `round11_crosscheck.txt` |
| 真实稿件套件 | 14 篇取数、13 篇 ready（1 篇为软删对照 D41） | `browser/articles_result.json` |
| `SKILL` 库内内容 | 第十二轮时**仍是旧版 5635 字符**（第十一轮缺口，待重启落库）→ **第十四轮重启后已变 6649、与源码一致，缺口关闭** | §八 第 7 条 |

历史各轮的耗时（每处都能对到自己的日志文件，未做跨轮混用）：

| 轮次 | 全量测试 | 日志 |
| --- | --- | --- |
| 第八轮 | 303 例 / 2:51 | `round8_tests.log` |
| 第九轮 | 303 例 / 2:46 | `round9_tests.log` |
| 第十轮 | 303 例 / 2:48 | `round10_tests.log` |
| 第十一轮 | 308 例 / 3:10 | `round11_tests.log` |
| 第十二轮 | 308 例 / 2:45 | `round12_tests.log` |
| **第十三轮** | **308 例 / 2:46** | `round13_tests.log` |

> **第十三轮**（照本文实跑）：探针侧全部重跑，**每一条预期数字逐字相符**（明细见 §3.9）；
> 门禁也重跑了一遍——**308 例 / 0 失败 / 2:46**（`round13_tests.log`）、`webui` **✓ 386ms**。
> 即本节数字被第十二、十三两轮各自独立验证过。**上表的 2:45 / 405ms 是第十二轮的原值，保留不改**，
> 两轮的差异只是机器负载波动，判定数字（308 / 0 失败）完全一致。

---

## 五、`tools/render-verify/` 里有什么、`target/probe/` 里还剩什么

**第十四轮已按「脚本与输入入库、产物不入库」做完搬运。** 现状：

| 类别 | 具体文件 | 在版本控制里？ | 放在哪 |
| --- | --- | --- | --- |
| 驱动脚本（Python） | `component_matrix.py`、`round8_combos.py`、`round10_alternatives.py`、`round10_registry_closure.py`、`round10_article_coverage.py`、`round11_crosscheck.py` | **是** | `tools/render-verify/gen/` |
| 共享纯函数（Python） | `passthrough.py`（元素形态透传判据；**不联网、不写产物**，带 `--selftest`） | **是** | `tools/render-verify/gen/` |
| 驱动脚本（Node） | `cdp.mjs`、`run-*.mjs`、`verify-live-app.mjs`、`summarize-*.mjs`、`probe*.html/js`、`editor-setup.js`、`legacyExtensions.js`、`probe.css`、`vite.config.mjs` | **是** | `tools/render-verify/browser/` |
| 终稿对照表脚本 | `round10_component_paths.mjs` | **是** | `tools/render-verify/` |
| 输入清单 | `component_registry.json`（63 ID 的唯一出处）、`component_matchers.json`（29 匹配器）、`guide_recheck.md`、`engine/mf_app.js` | **是** | `tools/render-verify/spec/` |
| 实测产物 | `components/*.html`、`combos/*`、`alt/*`、`registry/*`、`browser/*_result.json`、`browser/*_summary.{md,json}`、`round10_component_paths.{md,json}` | 否 | `target/probe/` |
| 截图 | `browser/shots/{all,combo,alt,registry,articles,live}/*.png`（190+ 张） | 否 | `target/probe/` |
| 日志 | `round*_tests.log`、`round*_webui_build.log`、`*_browser_run*.log` | 否 | `target/probe/` |
| 引擎包**另一构建** | `round11_live_bundle.js`（线上现发版本，用来做通道 B2 对照） | 否 | `target/probe/`（可重新下载，**sha256 会变**） |

> 判定规则很简单：**能重新算出来的（产物、截图、日志）不入库；算不出来的（脚本、定义基准）必须入库。**
> `spec/engine/mf_app.js` 是唯一的例外——它是外部构建产物，但它是交叉验证通道 B1 的输入，
> 没了就断证据链，所以入库并在 `spec/README.md` 里写清了来源与换版办法。

**clone 之后想跑通第三节，只需按这个顺序补**：

| 顺序 | 要补什么 | 怎么补 | 不补的后果 |
| --- | --- | --- | --- |
| 1 | `webui/node_modules` | `(cd webui && npm ci)`（用仓库自带的 `package-lock.json`） | W 构建与所有浏览器套件跑不了 |
| 2 | 渲染令牌文件 | 向渲染服务方索取，放 `~/.zcode/secrets/markflow-render-token`（**不要提交**） | 所有要打渲染 API 的套件跑不了 |
| 3 | `.env` | 按 `src/main/resources/application.yaml` 里的 `${ENV.*}` 占位符补齐（**不要提交**） | G 后端全量测试报 `Could not resolve placeholder`、大批 Error |
| 4 | `target/probe/` 目录本身 | `mkdir -p target/probe` | 日志重定向会先失败（脚本自己会建子目录，但顶层要存在） |

**顺序上的硬约束**：`§3.2 四支生成脚本` → `§3.3 编译探针 dist` → `§3.4 浏览器套件`。
**这三步不能省、不能换序**——浏览器套件读的是**落盘的产物文件**，不是现打 API。

**关于归档**：`target/probe/` 下的东西都可以重算，**不建议入库**（截图与样例 HTML 会让仓库膨胀到几百 MB）。
真要长期留存，只归档**汇总层**（`*_summary.md`、`round10_component_paths.md`、`round11_crosscheck.txt`）。

**如果以后再搬一次**（比如换目录结构），照这四条走：
1. `git mv` 现在可用了（文件已入库）；
2. 路径一律走 `paths.py` / `paths.mjs`，**不要在脚本里留裸相对路径**；
3. **产物落点要保持不变**，否则历史各轮的截图路径与本文的预期值全部作废；
4. **搬完必须按第三节完整重跑一遍才算搬成功**——第十四轮就是这么验的，见 §3.10①。


---

## 六、已知的坑（踩过一次就别再踩）

1. **控制台是 GBK**：`export PYTHONIOENCODING=utf-8` 更省心。**注意实测口径**：探针脚本本身不会崩
   （落盘文件始终 UTF-8），只是 print 出来的中文**值**会变乱码；`python -c` 内联打印中文才可能直接
   `UnicodeEncodeError`。
2. **clone 场景的第一条命令就会挂**：`target/probe/` 不存在，重定向先失败。先 `mkdir -p target/probe`。
3. **缺 `.env` 会让 44 个测试 Error**，报 `Could not resolve placeholder 'ENV.MYSQL_TEST_URL'`——
   这是环境问题，不是代码回归，别当成「上一轮改坏了」。
4. **浏览器套件不能并行**：`cdp.mjs` 默认占 9333 端口，并行会互相踢掉；也不要与 `mvn test` 并行（同一份 MySQL）。
5. **`mvn test` 不能并行跑**（项目既定约束），串行执行，单轮约 3 分钟。
6. **真实稿件套件依赖 8081**：应用没起时 `run-article.mjs` 会拿不到文章，看起来像「渲染坏了」，其实是环境没起。
7. **软删稿件永远转圈**：套件里 `5.png` 的 `ready=false` 是**已知缺陷 D41 的证据**，不是脚本失败。期望 14 篇里 13 篇 ready。
8. **判定方向不能想当然**：标签式的「未渲染」形态是**元素被原样透传**（真渲染的组件会变成自己的 `<section>` 结构）。
   把「产物里出现该元素」当成「已渲染」会得到完全相反的结论——所以每套判据都配对照组。
9. **上游会静默**：语法没被识别时 API 仍返回 200、`ok=true`、无 `warnings`，只在产物里留着字面 `:::` 或标签。
   判据必须看**产物**，不能看状态码。
10. **同一件事在不同层的计数口径不同**：稿件 43 的公式，**后端产物**里带 `katex` 的 class 出现 **12** 次，
    **编辑器里**真实 `.katex` 元素是 **5** 个——不是对不上，是两回事。判据是「元素存在 + 高度非零 + 源码没泄漏」。
11. **引擎包会换构建**：官网当前发的是 `/markflow/assets/index-CCyOlIBZ.js`，与第五轮存档的 `spec/engine/mf_app.js` **sha256 不同**。
   两者抽出的 38 个 `layout-*` 逐字一致，说明清单来自组件定义而非某次下载；更换构建时请重跑 §3.6 确认。
12. **`target/` 不进版本控制，但它的匹配是「任意深度」的**：`.gitignore:2` 的 `target/` **没有前导斜杠**，
    会命中仓库里**任意位置**的同名目录（不只看仓库根）。脚本与输入因此进了 `tools/render-verify/`，
    产物留在根目录的 `target/probe/`；搬运时若在别处建出 `…/target/`，会被静默忽略、白忙一场。
13. **`spring-boot:run` 不经过 Maven 的 `prepare-package`**：SPA 是 `frontend-maven-plugin` 在
    `prepare-package` 阶段构建进 `target/classes/static/` 的，而 `spring-boot:run` 只到 `test-compile`。
    结果就是**应用对外提供的可能是几轮以前的前端**，而 `webui/dist` 却是新的。
    要更新前端产物，显式跑：
    `(cd webui && npm run build -- --outDir ../target/classes/static --emptyOutDir)`，然后重启应用。
14. **`run-article.mjs` 量的不是应用实际提供的前端**（它静态服务端的是 `webui/dist`）。
    改了编辑器扩展之后只跑它，会得到「探针全绿、用户打开是坏的」——第十四轮的真实翻车。
    改为跑 `verify-live-app.mjs`（直接打开 `http://127.0.0.1:8081`），或先按上一条把产物更新到
    `target/classes/static` 再重启。**判据先看指纹**：编辑器 chunk 里是否含 `rawSvg` / `rawMath`。
15. **重启应用有副作用（但这次是想要的那个）**：启动时 `SkillSeeder` 会 upsert 技能文案。
    第十四轮重启后 `SKILL.CONTENT` 的 `CHAR_LENGTH` 由 5635 变 **6649**、与源码一致，
    第十一轮那条「待重启落库」缺口**已关闭**。以后重启仍会重跑这段 upsert，属预期行为。
16. **写代码注释时别把反引号放进模板字符串**：`verify-live-app.mjs` 里的 `MEASURE` 是一整段模板字符串，
    注释里出现 `` `name: 'rawSvg'` `` 会直接让 Node 报 `SyntaxError: Unexpected identifier`。
    这类脚本调试成本高（要跑起来才知道），写的时候留意。
17. ~~**汇总产物不是逐字节可复现的**~~ **已修复（第三十六轮）**，且原文的成因**写错了**：
    - 原文说两支产物都「按 `target/probe/components/` 的**目录列举顺序**排列」。实测：这两支脚本
      **都不读那个目录**来定行序（`readdirSync` 只出现在 `r25-*` / `r28-*` / `r34-*` 等浏览器驱动器里，
      且大多已 `.sort()`）。真因是 `gen/component_matrix.py` 的**产物合并历史**——
      `by_id = {row['id']: row for row in existing}` 再逐条覆盖，dict 插入序 = 老行在前、新行追加在后。
    - 原文把 `all_summary.json` 与 `round10_component_paths.json` 并列，但**后者本来就不随输入变**：
      它遍历的是 `component_matrix.json`（手写清单），不是 `all_result.json` 的 `samples`。
      实测反转 `samples` 后，`all_summary.{md,json}` 的 SHA256 均变、`round10_component_paths.{md,json}` 均不变。
    - 修复：`summarize-all.mjs` 遍历 `[...raw.samples].sort(by id)`。**只改行序，不改判定**——
      已验 `counts` 相同（pass 70 / na 9）、79 条 verdict 差异 0 条、`byCategory` 值全等。
      反转输入后产物 SHA256 由 DIFFERS 变 IDENTICAL；`--selftest` 与 `round29_gate_audit` 的 9a 红队项仍正常。
      现在可以按字面 diff 这两份产物了。
18. **干净 clone 照 §3.5 跑会 ENOENT**：`round10_component_paths.mjs` 依赖 §3.7 才产出的
    `round10_article_coverage.json`，而 §3.7 在文档里排在后面。§3.5 已加顺序警告。
19. **`mvn clean` 之后直接 `spring-boot:run`，应用没有前端**：`src/main/resources/static` 不存在，
    所以 `process-resources` 覆盖不了也补不出 `target/classes/static`；第十五轮在干净 clone 里实测
    `test-compile` 后该目录**根本不存在**。普通重启（不 clean）**不会**退回旧前端——实测 39 个文件
    逐字节不变。修复方案见 §3.11④（**A 已否决；B/C/D 已做，见 §3.11 ④-1**）。
20. **探针浏览器收不干净，且「数残留」的写法本身会自证为 1**（第二十九轮）：两件事一起踩的。
    ① **不能拿 `spawn()` 的 pid 收**——Chrome 启动器先起真浏览器、自己再退出，`close()` 时那个 pid
    已经是死进程；唯一稳的标识是本次启动专用的 `--user-data-dir`（`mkdtempSync(…, 'probe-chrome-')`）。
    ② **只按 `CommandLine.Contains('probe-chrome-')` 数/清会数到自己**——发查询的 `powershell.exe`
    命令行里嵌着这个字符串，于是计数永远留 1、清理脚本还会把自己 `Stop-Process` 掉。**必须同时限定
    `$_.Name -in @('chrome.exe','msedge.exe','crashpad_handler.exe')`**。完整片段与自检见 §3.4.1。
21. **退出码非零 ≠ 判据判红**（第三十二/三十三轮反复踩）：本套件各脚本把判词打成 `[闸] …` 行并以
    **非零退出码**表示不通过；但**脚本自己崩了**（`TypeError` / `ENOENT` / `spawnSync` 起不来）同样是非零。
    引用「某一步非零」之前**先看那一步有没有留下 `[闸]` 行**。第三十三轮实测的正例：
    `round10_component_paths.mjs` 在「浏览器实测比清单少一行」和「`all_summary` 空表」两种输入下
    都是在 `:148` 的 `verdict.backend` 上 `TypeError` 崩溃，退出码 1、**没有任何判词**——
    与「闸抓住了坏输入」是两回事。明细见 `docs/dev/known-issues-handoff.md` §3.33③ / §3.34②③。
22. **每次跑浏览器套件会往 `%TEMP%` 留一个 profile 目录**（第三十三轮查明，未修）：`cdp.mjs` 只清进程、
    不删 `probe-chrome-*` 目录；同一趟 9h 还会因 `round27_c_compare.mjs:114` 多留一个 `r27c-*`。
    攒着会占盘（实测 7 个约 105 MB），**定期手动清或等拍板修**，见 §3.4.1 的 ⚠️ 与 §3.34⑦。
23. **`#38` 的正文会被用户改，套件的前置条件会因此失效**（第三十四轮实测）：`r24-article38-symptoms.mjs`
    有一道「编辑器挂上正文」的前置判据 `minText = INJECT ? 10 : 1000`（可见字符数 > 1000），
    而 2026-09-14 16:24 之后 `#38` 的正文只剩 **196 个可见字**（`revision=20`、`CONTENT_HTML` **6,211** 字符；
    revision 17 时还是 32,583 字符 / 1,727 字）。**这不是探针写的**：那三次 `MANUAL`/「手动编辑」落库
    在两次探针运行之间，且每个探针脚本对非 GET 都有应用层 + 网络层双层拦截、跑完回读 `revision` 未变；
    但也没法从服务端确认操作者是谁——详见手册 §六 第 23 条与 `known-issues-handoff.md` §3.35⑦。
    于是不带 `--inject` 直接跑会在 80 次轮询后打印「编辑器就绪: false」并 `exit 2`——
    **那不是编辑器坏了，是前置条件不成立**。要量这一篇就带 `--inject <caseId>`（阈值降到 10），
    或者先跟用户确认这篇正文还要不要保持这个形状。`round25_stock_scan.mjs` 的存量判据也读同一个字段，
    它的结论同样随这篇正文变化（重跑它即可刷新）。

---

## 七、这套方法本身的局限（如实写）

- **外部依赖上游**：63 个注册 ID 里 **38 个 `layout-*` 上游没实现**，本项目的应对是
  「技能文案禁用 + 保存自检给出改写提示 + 给出替代写法」，**不是**把它们渲染出来——这不在本项目可控范围内。
  （是**提示不是拒绝**：保存仍然成功；且只覆盖智能体 `save_article_draft` 路径，
  编辑器人工保存路径没有这项检查。全族 76 组逐名核验见 §3.11⑤。）
- **`layout-*` 的结论是「当前构建的行为」**：上游改版后需要按 §3.6 重跑，不能当永久结论。
- **A 级证据只覆盖 13 / 63 个 ID**：其余 50 个停在 B 级（最小样例 + 真实浏览器）。
  B 级已经是真浏览器实测，不是 jsdom，但「真实稿件里被用于成稿」这一层没到。
- **9 条 `na` 的成因是上游未实现**，不是编辑器缺陷；编辑器侧对应判定是 `na`（无对照对象），
  不计入 pass 也不计入 fail——这一点容易被误读成「编辑器和上游一样坏」。
- ~~**技能文案的库内版本仍落后于源码**（5635 vs 6649），需重启应用一次才落库，见 §八 第 7 条。~~
  **第十四轮重启后已落库（`CHAR_LENGTH` = 6649、与源码一致），该项关闭。**
- **「全库 44 篇里被用到的写法，编辑器侧都能渲染」是 B 级结论**（第十五轮补，见 §3.11⑥）：
  39 项命中的判定全部为 `pass`，但其中 **7 项**（`md-link`、`md-image`、`blk-title`、`in-em-hl`、
  `ctn-case-flow`、`ctn-steps-h`、`ctn-steps-v`）命中它们的文章**都不在**真 SPA 回归过的 13 篇里，
  等于缺「真实成稿在编辑器里打开」这一层。要升级只能让调度轮次真的产出用到这些写法的成稿。
- **「两条路径都正确」有两层，别混起来读**：本文前面的判定（S 套件）量的是**最新构建的编辑器产物**；
  而用户打开应用时看到的是**应用实际提供的那份**。这两者可以不是同一次构建（§六 第 13、14 条）。
  第十四轮的复核把两者都量了（L 套件），结论一致；但**以后每次改编辑器扩展都要两条都跑**，
  只跑一条就是第十四轮那种「探针全绿、用户打开是坏的」。
