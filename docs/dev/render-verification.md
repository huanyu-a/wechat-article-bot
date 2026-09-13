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
**那一轮结论是「一支套件都跑不了」；第十四轮把脚本入库之后，这条已经不成立。**

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

```bash
node tools/render-verify/browser/summarize-all.mjs
node tools/render-verify/browser/summarize-combos.mjs
node tools/render-verify/browser/summarize-alt.mjs alt
node tools/render-verify/browser/summarize-alt.mjs registry
node tools/render-verify/round10_component_paths.mjs      # ← 终稿对照表，要在上面几条之后跑
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

---

## 七、这套方法本身的局限（如实写）

- **外部依赖上游**：63 个注册 ID 里 **38 个 `layout-*` 上游没实现**，本项目的应对是
  「技能文案禁用 + 保存自检拦截 + 给出替代写法」，**不是**把它们渲染出来——这不在本项目可控范围内。
- **`layout-*` 的结论是「当前构建的行为」**：上游改版后需要按 §3.6 重跑，不能当永久结论。
- **A 级证据只覆盖 13 / 63 个 ID**：其余 50 个停在 B 级（最小样例 + 真实浏览器）。
  B 级已经是真浏览器实测，不是 jsdom，但「真实稿件里被用于成稿」这一层没到。
- **9 条 `na` 的成因是上游未实现**，不是编辑器缺陷；编辑器侧对应判定是 `na`（无对照对象），
  不计入 pass 也不计入 fail——这一点容易被误读成「编辑器和上游一样坏」。
- ~~**技能文案的库内版本仍落后于源码**（5635 vs 6649），需重启应用一次才落库，见 §八 第 7 条。~~
  **第十四轮重启后已落库（`CHAR_LENGTH` = 6649、与源码一致），该项关闭。**
- **「两条路径都正确」有两层，别混起来读**：本文前面的判定（S 套件）量的是**最新构建的编辑器产物**；
  而用户打开应用时看到的是**应用实际提供的那份**。这两者可以不是同一次构建（§六 第 13、14 条）。
  第十四轮的复核把两者都量了（L 套件），结论一致；但**以后每次改编辑器扩展都要两条都跑**，
  只跑一条就是第十四轮那种「探针全绿、用户打开是坏的」。
