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
| `gen/` | 是 | Python 生成器：打真实渲染 API，产出样例 HTML |
| `browser/` | 是 | Node + CDP 驱动器：把产物灌进真实 Chrome 逐行判定（**不装 playwright/puppeteer**） |
| `spec/` | 是 | **输入**：组件清单、匹配器、官方 guide、引擎包存档 |
| `target/probe/` | **否** | **产物**：`components/*.html`、`browser/shots/*.png`、`*_summary.json`、日志 |

路径常量集中在 `paths.py` / `paths.mjs`，脚本里**不允许**再出现裸相对路径（如 `'target/probe/xxx'`）——
原脚本大量用 `HERE = dirname(__file__)` 把输入和产物混在一个目录里，搬完必须拆开，否则 cwd 一变就崩。

## 跑之前要有什么

1. **渲染令牌** `~/.zcode/secrets/markflow-render-token`（只读，**不进任何产物、不提交**）；
2. `webui/node_modules`（`(cd webui && npm ci)`）；
3. 跑真实稿件套件时，应用要在 `127.0.0.1:8081` 起着；
4. Node ≥ 24（`cdp.mjs` 用 Node 自带的全局 `WebSocket` + `fetch` 直连 CDP）。

## 一条命令都不许跳过的顺序

浏览器套件读的是**落盘的产物文件**，不是现打 API，所以顺序是硬的：

```
gen/component_matrix.py        → target/probe/components/*.html + component_matrix.json
gen/round8_combos.py           → target/probe/combos/
gen/round10_alternatives.py    → target/probe/alt/
gen/round10_registry_closure.py→ target/probe/registry/
        ↓（产物落盘之后）
(cd webui && npx vite build --config ../tools/render-verify/browser/vite.config.mjs)
        ↓
browser/run-*.mjs（**必须串行**，抢同一个 CDP 端口）
        ↓
browser/summarize-*.mjs + round10_component_paths.mjs
```

`browser/verify-live-app.mjs` 是**另加的一条**，与上面那套的目的不同：上面量的是 `webui/dist`，
它量的是**应用实际对外提供的那份前端**。第十四轮就是这么翻的车——`spring-boot:run` 不经过
Maven 的 `prepare-package`，静态产物停在旧构建，而 `webui/dist` 已经是新的，于是「探针全绿、
用户打开却是坏的」。回归时两支都要跑。

## 不要把产物提交进来

`target/probe/` 下的截图有 196 张、样例 HTML 几百个，都是每次可重算的中间件。
真要长期留存，只归档**汇总层**（`*_summary.md`、`round10_component_paths.md`、`round11_crosscheck.txt`）。
