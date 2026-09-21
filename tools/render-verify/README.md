# render-verify —— 渲染验收 harness（受版本控制）

这套东西回答一个问题：**渲染引擎现有组件，在后端 API 与编辑器前端两条路径上是不是都渲染正确。**

复现步骤、每个套件的预期数字、踩过的坑，全部写在 **`docs/dev/render-verification.md`**；结论页在
`docs/render-acceptance-report.md`。本文件只讲**这套脚本自己的摆放约定**。

## 为什么从 `target/probe/` 搬到这里

第十四轮之前，脚本、样例清单、截图、汇总全部躺在 `target/probe/`，而 `target/` 被 `.gitignore:2` 命中——
`git ls-files target/probe` 实测 **0 个文件**。后果不是「不方便」，是**结论不可复核**：
`git clone` 下来的人连一支套件都跑不了，连复现手册本身都拿不到。

搬运的一句话原则：**脚本与输入进版本控制，产物留在 gitignored 的 `target/probe/`。**

| 目录 | 在版本控制里 | 放什么 |
| --- | --- | --- |
| `gen/` | 是 | Python 生成器：打真实渲染 API，产出样例 HTML；另有两支**不带网络**的核验脚本（`@see` 下） |
| `browser/` | 是 | Node + CDP 驱动器：把产物灌进真实 Chrome 逐行判定（**不装 playwright/puppeteer**）；含四支单点诊断 |
| `spec/` | 是 | **输入**：组件清单、匹配器、官方 guide、引擎包存档 |
| `target/probe/` | **否** | **产物**：`components/*.html`、`browser/shots/*.png`、`*_summary.json`、日志 |

`gen/` 里的脚本按「出不出网」分两类。**不出网的**（第十五轮新增，用法见
`docs/dev/render-verification.md` §3.11⑤⑥）：

| 脚本 | 回答什么 | 产物 |
| --- | --- | --- |
| `gen/LayoutGuardCoverage.java` | 保存侧自检对 38 个 `layout-*` × 2 写法是不是**逐名**覆盖（不是抽样） | stdout → `target/probe/r15/layout_guard_coverage.txt` |
| `gen/round15_coverage_editor_verdict.py` | 全库 44 篇里被用到的 39 种写法，编辑器侧判定如何 | `target/probe/r15/coverage_editor_verdict.md/.json` |

**出网的**七支列在下面的命令链里；其中 `gen/round16_editor_reported.py`（第十六轮新增）
的输入是**用户在实际编辑器里写的原文**（原样照录、不改写），产物是 `target/probe/r16/r16.json` + `<id>.html`；
`gen/round18_infographic_variants.py`（第十八轮新增）把同一个组件**换 10 种写法**各打一次真实 API，
用来把「只试过一种写法所以复现不出」变成「试过 N 种」，产物 `target/probe/r16/infographic_variants.json`
被 `docs/dev/upstream-issues.md` §R10 引用。
`gen/round20_field_block_scalar.py`（第二十轮新增）是对 §R10 **自己上一轮结论的反向自查**：
问一拿 4 个带 `body:` 的组件 × 3 种写法，问二把块标量挪到 `label`/`title` 上并试 `>` / `|-` / 4 空格缩进，
产物 `target/probe/r16/r10_by_design.json` + `r10_blockscalar.json`——正是这两份数字把 §R10
从「渲染缺陷」降级为「文档未覆盖 + 静默失败」。

`browser/` 下这几支**单点诊断 / 复验**（第十六轮新增、宽度复验是第十七轮补的、真实界面验收与比对是第二十二轮补的、
#38 上的逐条复验是第二十四轮补的、保存出口幂等性是第二十五轮补的、列宽回归闸与段首空白窄修法的两支量法是第二十六轮补的、
段首空白的暴露面扫描与四条输入入口的对照是第二十七轮补的、往返稳定性闸与粘贴 HTML 的暴露面探针是第二十八轮补的，
被结论引用过所以进版本控制，用法见 `docs/dev/render-verification.md` §3.12 U5 / U7 / U8 / U10 / U12 / U13）。它们不参与终稿汇总，
只在「这条差异到底是真缺陷还是量法问题」「探针页算不算数」时按需跑，产物一律落 `target/probe/`（截图除外）：

| 脚本 | 回答什么 | 固定端口 |
| --- | --- | --- |
| `browser/r16-shot-zoom.mjs` | 按 **1:1** 拍左右两栏，用来肉眼定案「哪一侧真缺了边框/圆角」 | 9349 |
| `browser/r16-dump-live-table.mjs` | 实时 DOM 上那张 `<table>` 的 `style` 属性、`<colgroup>` 列宽、各 `td` 宽度、`editor.state.doc` 的 table attrs | 9348 |
| `browser/r16-measure-heights.mjs` | 两栏「画布高 / 内层高 / 分隔图数 / 尾随 `br` 数 / 顶层逐个孩子高」 | 9352 |
| `browser/r16-width-equiv-test.mjs` | 把两栏的**内层内容盒**对齐后重量行高，用来**双向证伪**「编辑器把行撑高了」 | 9355 |
| `browser/r16-live-editor.mjs` | 真实应用界面（8081）上的 11 条验收：探针页与用户真正看到的界面是否一致 | 9351 |
| `browser/r24-article38-symptoms.mjs` | 在**用户自己的文章 #38**（真实窗口宽度）上逐条回答「症状还在不在」：`--bundle` 整包换成改前前端跑「改前」列，`--inject` 灌用例产物，`--paste` 走粘贴，并把写保护拦下的 `PUT` 正文当**保存出口**留指纹 | 9356 |
| `browser/r25-save-exit-roundtrip.mjs` | 用户原话「**我在编辑器里看到的样子，保存之后还在不在**」：把拦下的 `PUT` 里的 `body.contentHtml` **原样**灌回，量**同一组 11 条探针**并与拦截那一刻的活 DOM 逐条对照；`--inject all` 灌全部 11 例、`--bundle <dist>` 换前端包 | 9357 |
| `browser/r25-whitespace-probe.mjs` | 收窄定位「哪一类空白在**第一次解析**时就丢」：半角空格 / 制表符 / `&nbsp;` / 零宽 / 全角 / 段中 / 段尾各一段，① 解析后 vs ② 保存出口重灌后逐块对照 | 9358 |
| `browser/r25-preservewhitespace-trial.mjs` | 候选修法 `parseOptions.preserveWhitespace:'full'` 的**副作用**：同一篇正文两种解析下的 section 层数 / 可见字数 / 逐块几何（**只在页面内存里改，从不保存**） | 9359 |
| `browser/r26-leading-ws-effect.mjs` | 第二十六轮 A 的判据：窄修法之后**段首空白活没活**——11 段写法各自「打开后有没有占位 / 保存出口里带没带 / 灌回去还一样吗」，外加同排版上下文里空格·不换行空格·制表符的**实测宽度** | 9361 |
| `browser/r26-leading-ws-samples.mjs` | 第二十六轮 A 的另一条判据：**79 套样例的块结构 / 几何逐条不变**（顶层块数、逐块 `dy`/`h`/首字符 `x`、`section` 层数、`htmlChars`、`textLength`）；`--compare before after` 是纯离线比对，**有差异时退出码 4** | 9360 / 9362 |
| `browser/r26-table-colwidth-exit.mjs` | 第二十六轮 B 的**回归闸**（U10）：窄列表格的列宽在**保存出口**里还在不在。判据①出口列宽 = 入口列宽、②再存一次不变；**自带反例自检**（旧编辑器出口必须被判 FAIL）。`--bundle <dist>` 换前端包 | 9363 |
| `browser/r27-entry-paths.mjs` | 第二十七轮 B：`preserveLeadingWhitespace()` 的**覆盖面**——`setContent` / 粘贴 HTML / 粘贴纯文本 / 手打 四条入口各给「实时 DOM / 保存出口 / 再打开」三列；`--bundle <dist>` 换前端包 | 9364 / 9366 |
| `browser/r28-roundtrip-gate.mjs` | 第二十八轮 A 的**回归闸**（U12）：把「**打开 → 保存 → 再打开**」整圈钉死。判据①往返稳定（逐叶子值相同）/②二次往返仍稳定/**③入口保真**；**自带反例自检**（旧整包必须 exit 1）。⚠️ **判据① 有盲区**：两边一致地丢时它照样通过，抓 bug 的是判据③。`--bundle <dist>` 换前端包 | 9367 |
| `browser/r28-paste-html-probe.mjs` | 第二十八轮 B（U13）：第 9 条「粘贴 HTML 的段首空白」**要不要修 `transformPastedHTML`**——真 `Ctrl+C` 读剪贴板原文（7 种写法）＋ 真 `Ctrl+V` 落进编辑器（7 份真载荷 ＋ 2 份手写载荷）＋ 249 个产物过一遍候选修法 ＋ 6 个代表载荷「原样 vs 先过变换」逐叶子比。**只量不改**，结论是**建议不修** | 9371 |
| `browser/r16-compare-probe-vs-live.mjs` | **纯离线**比对上面那支的产物与探针页的产物，回答「差几组、差在哪」（不开浏览器、不打 API） | — |

`browser/r24-probes.mjs`（第二十四轮）是那 11 条症状探针的**唯一一份定义**（`export const PROBE_SRC`），
第二十四、二十五两轮都 `import` 它——**换尺子就没法跨轮比数**。
只读取证脚本：`browser/r25-article38-revisions.mjs`（#38 的 `article_revision` 历史）、
`browser/r25-compare-fragments.mjs`（存库片段 vs 今天的产物）。
`round25_stock_scan.mjs`（在 `tools/render-verify/` 根下，与 `round10_component_paths.mjs` 同一层）
是**纯 DB 的只读存量扫描**，判据每条都只是 `ARTICLE.CONTENT_HTML` 的纯函数，见 `docs/dev/known-issues-handoff.md` §3.26③。
同层的 `round27_leading_ws_scan.mjs`（第二十七轮）是**段首空白的暴露面**扫描（口径与
`preserveLeadingWhitespace()` 逐字同构，支持 `--check <file>` 只跑口径不碰库）；
`round27_c_compare.mjs` 是**纯离线对账器**：把两份 `r24-article38-symptoms.mjs` 的结果按叶子值逐项比，
**不做归一、不做容差**，用来回答「产品代码改过之后，原来那张表还成不成立」。

`browser/r16-probes.js` 是第十六轮那 11 条各自的**计算样式探针定义**（`PROBES` / `runProbe`），
第二十二轮从 `probe_r16.js` 里**纯搬家**出来，因为现在有两个消费者（探针页 + 真实界面），
必须用同一组定义两边数字才能放在一张表里比。

`browser/r16-live-editor.mjs` 与前四支的区别是**它不打探针页，打真实运行的应用**：开 8081、登录、
在应用层把 `GET /api/articles/<id>` 的返回换成用例的 `contentHtml`，让**应用自己**调
`editor.commands.setContent(...)` —— 这正是用户打开一篇文章的路径。探针定义直接读
`browser/r16-probes.js` 的源码，保证两边同一把尺子。默认 `--width-match` 把 `.paper` 临时放宽到 867px，
让两边正文栏同宽（真实界面 684px / 探针页 731px）。**四种输入路径**：
默认 `setContent`（用户打开文章的真实路径）、`--width-match`（setContent + 同宽，**判据用这份**）、
`--paste`（合成 `ClipboardEvent`）、`--paste-real`（真剪贴板 + 真 `Ctrl+V`）、
`--type`（真 `input` 事件逐行手打 Markdown 源文，不走剪贴板）。
**截图按模式分目录**（`shots/r16-live/<mode>/`）——第二十二轮四种模式共用一个目录，后跑的覆盖先跑的，
文档里引用的「真实界面截图」实际指向最后那次粘贴的图；第二十三轮改成分子目录并把四种模式全部重跑对齐。
**跑它必须应用在 8081 起着**；命令与判定见 `docs/dev/render-verification.md` §3.12 U7、
三条输入路径的逐条结论见 `docs/dev/known-issues-handoff.md` §3.22 / §3.23①。

`browser/r16-compare-probe-vs-live.mjs` 是上面那支的**后半截**，拆出来是因为「量」和「判」是两件事：
改判据不该重跑浏览器。本轮结论正是靠反复改判据收敛的——先是 13/70，同宽后 0/70
（两种跑法都另有 1 组只差图片加载时序，单列不计入）。它默认读 `--width-match` 那份产物**（只有同宽，两边数字才有可比性；
异宽时几乎所有宽度派生值都会平移 47px）**，并过滤三类已知假阳性（图片加载时序 / 宽度派生值 /
tiptap 应用层的 `draggable`、`tableWrapper`、行内 style 属性顺序）。**`--width-match` 那份退出码 0。**
`--dom` 加一段 DOM 字符串比对，但那段**不是承重判据**（两边量的是不同出口），理由见文件头注释。

**第三十四轮补进版本控制的两支**（原先只落在 gitignored 的 `target/probe/r34/`，被结论引用过所以搬进来）：
`browser/r34-table-handmade-regression.mjs`（**反作用闸**：工具栏「插入 3×3 表格」的手写表格会不会被
`mf-preserved` 标记类误伤——判据③「重开后拿不到类」＋ 判据④**同源正向对照**「同一份出口只多一个
`border-collapse:collapse`，类必须出现」，一负一正同跑，防「脚本走空了也说绿」）与
`r34_table_fingerprint_scan.mjs`（**纯离线**：把候选指纹在全部产物上数一遍，回答「拿什么当
『这是渲染服务产物表』的指纹」——实测表级 `style` 带 `border-collapse` 的 **14/14**，
手写表格 `min-width: 75px;` 一条不占）。两支的用法与结论见 `docs/dev/known-issues-handoff.md` §3.35。

路径常量集中在 `paths.py` / `paths.mjs`，脚本里**不允许**再出现裸相对路径（如 `'target/probe/xxx'`）——
原脚本大量用 `HERE = dirname(__file__)` 把输入和产物混在一个目录里，搬完必须拆开，否则 cwd 一变就崩。

## 跑之前要有什么

1. **渲染令牌** `<渲染令牌文件，路径与值均不入库>`（只读，**不进任何产物、不提交**）；
2. `webui/node_modules`（`(cd webui && npm ci)`）；
3. 跑真实稿件套件时，应用要在 `127.0.0.1:8081` 起着；
4. Node ≥ 24（`cdp.mjs` 用 Node 自带的全局 `WebSocket` + `fetch` 直连 CDP）。

## 一条命令都不许跳过的顺序

> **第三十轮起，这套顺序有了常设入口**：`bash tools/render-verify/run-suite.sh`
> （九步链 + 收尾的 `round29_gate_audit.mjs`，串行，末尾逐步骤打印退出码；日志 `target/probe/run_suite.log`）。
> 在此之前它只存在于 gitignored 的 `target/probe/r29/_run_suite.sh`——干净 clone 里没有这一步。
> 细节与「各步退出码是什么时候才有判据含义」的口径见 `docs/dev/render-verification.md` §3.14。
> ⚠️ **它不是「干净 clone 一条命令跑到底」**：链的起点是探针 dist 重建，**不含 `gen/` 那几支出网的生成器**，
> 所以 `target/probe/components/*` 与 `component_matrix.json` 必须**已经存在**（干净 clone 里要先照
> 手册 §3.2 / §3.4 把上一级产物打出来）。第三十一轮复查查明，如实记在手册 §3.14。

浏览器套件读的是**落盘的产物文件**，不是现打 API，所以顺序是硬的：

```
gen/component_matrix.py        → target/probe/components/*.html + component_matrix.json
gen/round8_combos.py           → target/probe/combos/
gen/round10_alternatives.py    → target/probe/alt/
gen/round10_registry_closure.py→ target/probe/registry/
gen/round16_editor_reported.py → target/probe/r16/（用户逐条标注的 11 条，输入原样照录）
        ↓（产物落盘之后）
(cd webui && npx vite build --config ../tools/render-verify/browser/vite.config.mjs)
        ↓
browser/run-*.mjs（**必须串行**，抢同一个 CDP 端口）
        ↓
browser/summarize-*.mjs + round10_component_paths.mjs
        ↓（可选，只在复核单条差异时）
browser/r16-shot-zoom.mjs / r16-dump-live-table.mjs / r16-measure-heights.mjs
        ↓（可选，验收「用户真正看到的界面」时；**要求 8081 起着**）
browser/r16-live-editor.mjs [articleId] [--width-match] [--paste | --paste-real | --type]
        ↓（判：与探针页差几组、差在哪；**纯离线**）
browser/r16-compare-probe-vs-live.mjs [r16_live_widthmatch_result.json]
        ↓（可选，把验收拉回**用户自己的文章**；**要求 8081 起着**）
browser/r24-article38-symptoms.mjs 38 --label after
browser/r24-article38-symptoms.mjs 38 --label before --bundle target/probe/r24/before-dist
        ↓（可选，答用户原话「看到的样子保存后还在不在」；**要求 8081 起着**）
browser/r25-save-exit-roundtrip.mjs 38 --inject all --label after
browser/r25-save-exit-roundtrip.mjs 38 --inject all --paste --label after
browser/r25-save-exit-roundtrip.mjs 38 --inject all --type  --label after
        ↓（可选，只在①出现差异时收窄定位；两支都**不保存**，只动页面内存）
browser/r25-whitespace-probe.mjs
browser/r25-preservewhitespace-trial.mjs
        ↓（可选，存量影响面；**纯 DB、不开浏览器、全程只读**）
round25_stock_scan.mjs
        ↓（**回归闸，第二十六轮起的常规项**；**要求 8081 起着**，exit 0 = 过）
browser/r26-table-colwidth-exit.mjs
        ↓（可选，段首空白的覆盖面；**纯 DB/文件、全程只读**）
round27_leading_ws_scan.mjs
        ↓（可选，把「打开/粘贴/手打」四条输入入口量成一张三列表；**要求 8081 起着**）
browser/r27-entry-paths.mjs --label after
browser/r27-entry-paths.mjs --label before --bundle target/probe/r26/before-dist
        ↓（可选，两份结果 JSON 的逐叶子值对账；**纯离线**）
round27_c_compare.mjs <基线.json> <复跑.json>
        ↓（**回归闸，第二十八轮起的常规项**；**要求 8081 起着**，exit 0 = 过）
browser/r28-roundtrip-gate.mjs --label after
browser/r28-roundtrip-gate.mjs --label round26-before --bundle target/probe/r26/before-dist   # 反例，必须 exit 1
        ↓（可选，只在要重新回答第 9 条「粘贴这条路丢不丢、修法有没有副作用」时跑；**只量不改**）
browser/r28-paste-html-probe.mjs
        ↓（**审计器，第二十九轮新建；纯离线**——回答「今天的绿灯有多少是真绿」）
round29_gate_audit.mjs [--json]
        ↓（可选，剪贴板 HTML 会不会丢段首空白；**纯离线**，对任意一份 HTML 直接判定）
round29_clipboard_html_check.mjs <文件.html> [...] | --dir <目录> | --stdin | --selftest
        ↓（可选，真实来源采样：真选中 → 真 Ctrl+C → 读剪贴板 text/html；**要求 8081 起着**）
browser/r29-clipboard-sampler.mjs
        ↓（可选，三条甲类闸的**反例自检**，证明「已知盲」≠「尺子瞎了」；**纯离线**）
browser/r25-save-exit-roundtrip.mjs --selftest
browser/r16-compare-probe-vs-live.mjs --selftest
browser/r26-leading-ws-effect.mjs --selftest
        ↓（**回归闸，第三十四轮起的常规项**；**要求 8081 起着**，exit 0 = 手写表格没被误伤且指纹判据是活的）
browser/r34-table-handmade-regression.mjs
        ↓（可选，表格修法的**剂量-反应量法**：原项目 / 编辑器 / 裸容器 / 消融组 / 候选修法 / 字体六个面；
         自带锚洁净闸与反证说明。**要求 8081 起着**）
browser/r34-table-metrics.mjs
        ↓（可选，标记类判据的取证扫描；**纯离线**，只读落盘产物）
r34_table_fingerprint_scan.mjs
        ↓（**第三十五轮新增**：粘贴 HTML 的段首空白修法——反例自检 ＋ 真函数未动 ＋ 暴露面；**纯离线**。
          exit 0 = 旧包判红 / 新包判绿 / 另四条路逐字节未变 / 真函数 sha 与第二十八轮记录相同 / 源码级四个行号都在）
browser/r35-paste-fix-audit.mjs
```

> **第二十九轮加的那条审计器要说清楚它凭什么重要**：`round29_gate_audit.mjs` 把全库
> **21 条验收断言**逐条判类（甲｜只比两侧一致 / 乙｜与外部真值对照 / 丙｜自声明期望值 / 丁｜无判据）、
> 逐条喂**仓库里真实存在过的坏版本**、逐条问「它判了什么」。**第二十九轮当时的结论**是：
> **九步链 15 个步骤里只有 1/9 的构建真正能判红**，其余 14 步（2/9~9g）全部没有退出码，
> 所以「九步 EXIT=0」并不等于任何断言成立。**接手人先看这张表再引用绿灯。**
> 依据与三条工程项（E1~E3）见 `docs/dev/known-issues-handoff.md` §3.30。
> ⚠️ **上面那段的「14 步没有退出码」是第二十九轮的实况，第三十轮起已不成立**：14 步的退出码
> 全部接上（判定口径一个字没改，只把各脚本本来就在打印的 fail 数接到退出码上），
> 逐行真喂坏输入后 **能判红 18/21（86%）**。当前口径见 `docs/dev/render-verification.md` §3.14
> 与 `known-issues-handoff.md` §3.31①；本文件不改写历史段落，只在此处标出口径分界。
> **第三十四轮在这张表上又动了两格**：① 第 15 行（`r16-compare-probe-vs-live`）的反例喂法原先
> 是**跨代混喂**（新探针页 + 旧真实界面量测），它报出的 FAIL 是喂法的产物、不是闸的表现，
> 已改成同代成对喂；② **9g 的差异条目基线由「一个总上限」改成「逐例上限」**——
> 原先只钉总数时「甲例涨 20、乙例降 20」照样判绿，而这两件事的含义完全相反
> （实测：加上逐例锚之后，那条自检用例由绿转红）。逐例上限的当前值与被修订那一例的归因
> 写在 `browser/summarize-r16.mjs` 文件头。**21 条断言里能判红 18 条（86%）这个比例没变。**
> **第三十五轮再动一处产品代码**：把第二十六轮的窄修法 `preserveLeadingWhitespace()` 从
> `setContent`（打开文章）**扩到粘贴这条路**（`PastedLeadingWhitespace` 扩展的 `transformPastedHTML`）。
> **判据换了口径**——第二十八轮判的是「收益」（结论：建议不修），本轮用户明确要求修，
> 判据改成「坏在哪 + 修了会不会坏别的」；新立的审计器是 `browser/r35-paste-fix-audit.mjs`（U19）。
> **第二十八轮那两个数字（7 种真复制载荷里 1 份会被改写、249 个产物改写 4 文件 8 处）继续有效**，
> 只是不再用来决定做不做。

> `gen/` 里**只有** `component_matrix.py` / `round8_combos.py` / `round10_alternatives.py` /
> `round10_registry_closure.py` / `round16_editor_reported.py` / `round18_infographic_variants.py` /
> `round20_field_block_scalar.py`
> 这七支会出网；
> `round11_crosscheck.py` 与 `round15_coverage_editor_verdict.py` 只用落盘产物。
> `round16_editor_reported.py` 的输入是**用户在实际编辑器里写的原文**（原样、不改写），
> 所以它同时是「用户报障的取证套件」——判定口径与两类已知假阳性见
> `docs/dev/known-issues-handoff.md` §3.20，被 11 条打回上游的部分见 `docs/dev/upstream-issues.md` 一·续。

⚠️ `round10_component_paths.mjs` 除了上面这些，还要读 `target/probe/round10_article_coverage.json`，
而它由 `gen/round10_article_coverage.py` 产出（该脚本要先手工导出 `round10_article_coverage` 用的
`round10_articles.tsv`，见复现手册 §3.7）。**漏了这一步，终稿表会以 `ENOENT` 失败**——
第十五轮在干净 clone 里就是这么踩到的（手册 §3.5 与 §六 第 18 条已记录）。

`browser/verify-live-app.mjs` 是**另加的一条**，与上面那套的目的不同：上面量的是 `webui/dist`，
它量的是**应用实际对外提供的那份前端**。第十四轮就是这么翻的车——`spring-boot:run` 不经过
Maven 的 `prepare-package`，静态产物停在旧构建，而 `webui/dist` 已经是新的，于是「探针全绿、
用户打开却是坏的」。回归时两支都要跑。

## 不要把产物提交进来

`target/probe/` 下的截图有 196 张、样例 HTML 几百个，都是每次可重算的中间件。
真要长期留存，只归档**汇总层**（`*_summary.md`、`round10_component_paths.md`、`round11_crosscheck.txt`）。
