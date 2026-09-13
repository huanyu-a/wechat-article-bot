# spec/ —— 结论的「定义基准」与可离线复现的输入

这个目录里的东西**不是产物**，是「什么算组件全集」「什么语法才该被识别」的定义来源。
结论会随引擎改版而变，但**基准必须先看得见地改**，不能在产物里静默漂移。

| 文件 | 是什么 | 谁在用 | 换了怎么办 |
| --- | --- | --- | --- |
| `component_registry.json` | **63 个组件 ID**（唯一权威出处）。从引擎前端 bundle 的注册表里抽出来的 `[(id, category), …]` | `gen/round10_registry_closure.py`、`gen/round11_crosscheck.py`、`round10_component_paths.mjs` | 只有引擎真的加了/删了组件才改。先跑 `gen/component_matrix.py --dump-registry`，它会**逐字节比对**并报出差异，确认不是本机产物漂移之后再更新本文件 |
| `component_matchers.json` | 29 条语法匹配器：决定「写成什么才会被识别」 | 人工核对用 | 同上，从 bundle 里抽 |
| `guide_recheck.md` | 渲染服务 `GET /__markflow_render` 返回的官方语法指令（13744 字符）。**是给模型看的**，与注册表并不重合——`layout-*` / `gov-header` / `hint` 都不在里面 | 交叉验证通道 C | 上游改文案时重取 |
| `engine/mf_app.js` | 第五轮存档的引擎前端 bundle（641 KB，**第三方构建产物**） | `gen/round11_crosscheck.py` 通道 B1：证明那 38 个 `layout-*` 名字在引擎里**只以字符串出现**，没有语法匹配、没有渲染分支 | 见下 |

## 关于 `engine/mf_app.js`

它是**外部件**，提交进来只为「结论可复核」：没有它，交叉验证的通道 B1 就断了，
而 B1 正是「38 个 `layout-*` 上游没实现」这条结论的独立证据之一。

- 来源：官网 `/markflow/` 线上 bundle 的存档；**不是当前线上版本**（当前发的是另一个文件名，
  sha256 不同）。两者的 38 个 `layout-*` 名字**逐字一致**，说明这份清单来自组件定义而不是某次下载。
- 换构建时：把新包放到 `target/probe/round11_live_bundle.js`（gitignored），
  `gen/round11_crosscheck.py` 会用它跑通道 B2 做对照；两者 `sameAsArchived` 的结果就是判定。
- **不要把 tokens、cookie、请求头之类的东西混进来**——这里只放静态 JS 包。
