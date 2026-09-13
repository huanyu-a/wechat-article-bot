# 上游问题清单（可直接提交给 MarkFlow / agent4j / 网关）

> 用途：把本项目**改不动、只能对外提**的问题攒成一份可以直接转发的清单。每条给出「现象 / 最小复现 / 期望行为 / 当前绕过方式」。
> 本文件只写**用真实令牌 + 真实渲染 API 测过、可被反证**的结论；探针原始输出都在 `target/probe/`（该目录在 `.gitignore` 里，需要时用同样的脚本重跑）。
> 采集时间：2026-09-11 ~ 2026-09-13。渲染 API：`POST https://www.bx9y.com.cn/__markflow_render`（header `X-Render-Token`）。

---

## 一、渲染服务（MarkFlow）

### R1（P1）`guide` 与 Web 组件注册表不一致：8 个容器组件服务端语法指令里一个字都没有

- **现象**：`GET /__markflow_render`（无 body）返回的 `guide`（实测 7672 字符）里，块级组件只有编号 1–11 的 11 个标签；而 Web 端组件库实际支持并会用到的这些容器**全文未出现**：
  `:::reading-path`、`:::steps-horizontal`、`:::steps-vertical`、`:::case-flow`、`:::slider`、`:::callout`、`:::align`、`:::code-block`。
  另有 `:::steps` 只以「使用规则」里的三列行格式出现，注册表里的标签形式 `<steps>` 有记载。
  `:::breaking` / `:::timeline` / `:::table` guide 有记载（第 8/9/10 条），但都不在编号组件表里。
- **最小复现**：`python target/probe/probe_guide_recheck.py` → `target/probe/guide_recheck.txt`（逐名计数）；
  对每个名字用注册表官方示例打一次渲染 API，都能出真实结构（编号圆点导航、SVG 轮播、彩色提示框…），见 `target/probe/probe_containers.py`。
- **期望行为**：`guide` 的组件清单与 Web 注册表对齐（至少把这 8 个容器补进「四、块级组件」），
  否则按 guide 写的模型永远只用最朴素的几种标签——这正是「AI 排版出来比官网示例素」的第一大原因。
- **当前绕过**：本项目在自己内置的技能提示里补齐了这 9 项写法，并在保存时做语法自检（`ScheduledArticleTools#markflowSyntaxHints`）。

### R2（P1）同一次渲染里，`:::` 容器类的错误几乎都不报 `meta.warnings`，只能靠产物反推

> **结论的边界（先读这段）**：下面是**本机穷举**的结论，**不是上游的承诺**，也没有任何上游文档背书。
> 穷举的输入清单、没覆盖到的组合、以及能证伪它的最小实验都写在 §R2-附。请按「已知反例集合」理解，
> 不要读成「除 compare 列数外上游一律不报警」——那是一个更强的、本文**没有**证明的命题。

- **现象**：下面这些写法都是 HTTP 200 + `ok:true` + **`meta` 里没有 `warnings`**，但产物已经坏掉（实测产物文本已给出）：

  | 写法 | 产物 | 产物大小 |
  |---|---|---|
  | `<compare>`（写成标签） | 产物里原样留着未知元素 `<compare></compare>`（屏幕上什么都不显示） | 396 字符 |
  | `:::` 容器不顶格 / 被 `>` 引用块包住 | 字面 `:::tip` 留在正文 | — |
  | `:::reading-path` 里有一行不带 `-`（含整块用 `*`） | 整块导航 **0 字符** | 0 |
  | `:::slider` 没给 `images` | 正文里留一个灰底框「请提供图片URL列表」 | 165 字符 |
  | `:::table` 里不是表格 | 正文里留一行灰字「表格至少需要表头行和一行数据」 | — |
  | `:::align align="bogus"` | 产物里是 `style="text-align:bogus"`（无效 CSS） | — |
  | `<badges></badges>`（空） | 该块静默消失 | 0 |
  | `:::compare` 的行只有 2 列 | 该行被忽略，**末列对应那一方的整列内容一起丢** | — |
  | `:::table` / `:::timeline` / `:::slider` / `:::compare` 的 `title="…"` | 该属性里的文字**一个字都不进产物**（容器本身正常渲染），而 `:::callout title=`、`:::steps-horizontal label/title/hint`、`:::code-block title=` 都会渲染——同一套语法里有的输出有的不输出，调用方无法从文档判断 | — |

- **最小复现**：`target/probe/probe_hunt_warn.py`、`probe_hunt_warn2.py`、`probe_rp_shape.py`、`probe_slider_empty.py`、`probe_compare_cols.py`、`degrade_edge_result.txt`。
- **第六轮端到端复核（`case-flow` 作为代表，真实渲染 API）**：同一个组件、同样的内容，只换行格式——
  `- [案例 01] 从零搭建个人知识库` 出 **1031 字符**（两行各一个 flex 卡片），
  去掉中括号标签后出 **0 字符**，两次都是 `ok:true` 且 `warnings: []`。
  输入输出原样落盘在 `target/probe/round6_r2r4.md`（`R2-A`…`R2-D` 四组），可逐条复核。
- **期望行为**：这些是「用户看得见的坏」，应当在 `meta.warnings` 里逐条返回（或直接 4xx），
  而不是让调用方自己去扫产物。
- **当前绕过**：本项目对产物做本地扫描（`MarkFlowRenderService#detectLeakedSyntax` / `detectDroppedBlocks`），
  并在保存草稿时用 `markflowSyntaxHints` 提前拦一遍。

#### R2-附 这条结论的覆盖范围（穷举输入清单 / 未覆盖组合 / 证伪实验）

**① 实际打过的输入（全部为真实渲染 API，逐条看 `meta.warnings`）**

| 探针 | 用例数 | 命中 warnings |
|---|---|---|
| `probe_hunt_warn.py`（16 例） | `callout type="bogus"`、`align="bogus"`、`:::table` 里不是表格、`:::code-block` 无围栏、`<compare>` 当标签、空 `<badges>`、空 `<title>`、`:::steps-vertical` 无竖线、`:::timeline` 4 列、`:::case-flow` 2 列、空 `:::reading-path`、`:::slider` 非法 URL、`:::breaking` 无标题、**`:::callout` 里嵌套 `:::compare`**、`<img>` 无 src、坏 mermaid | **0 例** |
| `probe_hunt_warn2.py`（12 例） | 成对/未成对标签：`<cta>`/`<lead>`/`<statement>`/`<p-title>`/`<badges>`/`<breaking>`/`<title>`/`<engage-card>`，未知标签 `<compare>` 与裸标签，`<steps>` 不闭合，`:::callout` 内容不合法 | **0 例** |
| `probe_rp_shape.py`（3 例） | `:::reading-path` 规范行、无竖线、裸文字行（→ **产物 0 字符**） | **0 例** |
| `probe_slider_empty.py`（2 例） | `:::slider` 规范、无 `images`（→ 产物只剩一句灰字提示） | **0 例** |
| `probe_compare_cols.py`（5 例） | `:::compare` 4 列 / 4 列带 accent 标记 / **3 列** / 5 列 / 2 列 | **2 例**（5 列、2 列） |
| `degrade_edge_result.txt`（4 例） | 空 `<badges>`、空 `:::reading-path`、`:::table` 无表体、`:::align` 非法值，均夹在正文中 | **0 例** |
| `attr_probe3.txt` + `attr_probe4.txt`（10 例） | 各容器的 `title=` 属性是否进产物：`table` / `table style="card"` / `timeline` / `slider` / `compare` → **不进**；`callout` / `steps-horizontal`（label/title/hint 三个）/ `code-block` → **进** | **0 例**（全部静默，且不是「报错」，是**根本不输出**） |

**② 一个容易读错的细节：`:::compare` 内部不是「列数一不对就报」**

4 列 → 1742 字符、无 warnings；3 列（表头与数据行都是 3 列）→ 1173 字符、**也无 warnings**——
**3 列是合法形态**，不是降级。报的是 5 列、2 列，以及**第 4 格取值不在 `{accent, default, 空}` 里**的行。
即「报不报」取决于行是否落在这个形状里，而不是「是否等于某个唯一正确的列数」。完整复测见下文 ④ 的表。

**③ 没覆盖到的写法（因此本文不能断言它们也不报）**

- **嵌套**：只试过「`:::callout` 内嵌 `:::compare`」一种，没试 `:::timeline` 内嵌 `:::table`、
  三层嵌套、容器内嵌 `:::slider` 等组合。
- **属性写法变体**：只试过双引号 + 单个「非法值」；没试单引号、无引号、缺 `=`、
  属性里带 `"` 或 `&quot;`、超长属性值、大小写混写的属性名。
- **容器定界符变体**：没试 4 个及以上冒号、`:::` 后带行尾空格、`:::` 后带注释、
  CRLF 换行、容器出现在文档首行/末行且无空行、缩进过的容器、被列表项或引用块包裹的容器。
- **规模类**：没试超长表格（几百行）、超多条目时间线、单次请求体接近上限。

**④ 证伪实验的结果：R2 收窄（不是作废，也不是原样保留）——2026-09-13 实打**

按 ④ 的第 3 条（**最该先做的一条**——它决定整节是否作废）实打了一遍，方法：
`POST /__markflow_render` 渲染「降级输入」与「干净输入」各若干，**把响应的顶层键、`meta`、`theme`、
全部响应头、以及 `preview` 字段整体 dump 出来**逐字段找报错通道。原始输出 `target/probe/r2_falsify_channels.txt`、
`r2_falsify_channels2.txt`、`r2_preview_context.txt`。

| 检查项 | 实际结果 |
|---|---|
| 顶层键 | `html` / `meta` / `ok` / `preview` / `theme` —— 与干净输入**完全相同**，没有 `warnings`/`errors` 兄弟字段 |
| `ok` | 降级与干净**都是 `true`** |
| `meta` 的字段 | 只有 `title` / `summary`（+ 命中时的 `warnings`）。**没有第二个报错字段** |
| `preview`（一个此前没被检查过的顶层字段） | 是一份完整的预览页 HTML（含复制按钮的 JS）。**里面没有任何错误块**——用 `失败` 一词扫出来的命中是**静态 UI 文案**「复制失败，请手动全选复制」，在**干净输入里同样存在**。`error`/`warn` 标签匹配数 **0** |
| 响应头 | 只有 nginx/EO 的常规头（`EO-LOG-UUID`、`EO-Cache-Status` 等），**没有承载告警的自定义头** |
| `html` 本身 | 整篇都是那一块坏语法时返回**空串**（0 字符）；坏块夹在正文中时 `html` 非空、只是那一块不见了 |

⇒ **不存在会推翻 R2 的第二报错通道，这部分结论保留。**

**但接着打 ④ 的第 1、2 条（未覆盖写法）时命中了反例，所以 R2 的说法必须收窄**——
「容器错误几乎都不报 warnings」这个措辞太强。把两条列数规则各自钉了一遍
（`target/probe/compare_cols_mixed.txt`、`timeline_cols.txt`）：

| 容器 | 合法行 | 实际告警 | 实测产物 |
|---|---|---|---|
| `:::compare` | **3 列或 4 列**都合法；4 列时**第 4 格只认 `accent` / `default` / 空**（大小写不敏感） | 列数出界（2 列或 ≥5 列）**或第 4 格取值不在白名单**都报，文本 `compare 有 N 行列数不是「维度 \| A方 \| B方 \| accent\|default」，多余或缺少的列已被忽略` | 全 3 列 → 1714 字符无告警；全 4 列 → 1742 无告警；全 5 列 / 全 2 列 → 1173 / 1171 且**报「有 2 行」**；第 4 格写 `强调` / `高亮` / `highlight` / `strong` → 报「有 1 行」，写 `accent` / `default` / `ACCENT` / 空 → 不报（`target/probe/compare_marker_probe.txt`） |
| `:::timeline` | 每行**至少 3 列**（4 列也接受，多余列被丢） | 只有**不足 3 列**的行才报，文本 `timeline 有 N 行不足 3 列（时间 \| 标题 \| 说明），已被忽略` | 全 3 列 / 全 4 列 → 1836 字符无告警；全 2 列 / 全 1 列 → **0 字符**且报「有 2 行」；3 列里混一行 2 列 → 1019 字符、报「有 1 行」 |

⚠️ **一条容易踩的坑（也是本项目真实踩过的）**：`compare` 那条告警文本说的是**「列数不是…」**，
但实测触发条件里还有**取值不在白名单**这一半——第 4 格写中文「强调」时列数是 4、完全正常，
照着告警去数列数会查不出问题。本项目 run#81/#82/#83 连续三轮的渲染降级都是这个（技能提示把
「强调标记」当占位词教给了模型，模型照抄了「强调」两个字，见 `known-issues-handoff.md` D29）。

⚠️ **一条我一开始写错、复查时改回来的**：本节曾把「`:::compare` 3 列也会报」写进结论——
那是**误读**。旧探针 `probe_compare_cols.py` 的 `3col` 用例（`维度 | 甲 | 乙` / `价格 | 高 | 低`，
两行都是 3 列）本来就是**不报**的；我把它与另一条混了。**以本表的复测为准**。

**同一族的反面（仍然静默）**，这才是「几乎不报」的真实边界：

| 输入 | 产物 | warnings |
|---|---|---|
| `:::table` 里嵌套 `:::timeline` | 227 字符、字面 `:::` 残留在正文里 | **无** |
| `:::slider` 里嵌套 `:::callout` | 374 字符、字面 `:::` 残留 | **无** |
| 容器被两个空格缩进（`:::callout` / `:::compare`） | 594 / 590 字符、字面 `:::` 残留 | **无** |
| 容器被 `>` 引用块包住 | 524 字符、字面 `:::` 残留 | **无** |
| 用 4 个冒号开启（`::::callout … ::::`） | 590 字符、字面 `:::` 残留 | **无** |
| `:::reading-path` 的行不以 `-` 开头 | **0 字符**（整块导航消失） | **无**（见 §R2 主表） |
| `:::case-flow` 的行不以 `[标签]` 开头 | **0 字符** | **无**（见 §R2 主表） |
| 嵌套：`:::timeline` 内嵌 `:::table` | 2807 字符 | **有**（但报的是**内层表格行被当成 timeline 行**，不是「不支持嵌套」） |

**所以 R2 的准确表述是**：`meta.warnings` 只覆盖**它自己实现了校验的那几条规则**——
目前实测确认的只有 **2 类**：`compare` 行必须有 3–4 列、`timeline` 行必须 ≥3 列
（`:::reading-path` / `:::case-flow` 那两条「整块归零」的规则**不报**，见主表）。
**其余一切语法问题——缩进、引用、冒号个数、嵌套、属性写法——都不报**，只在产物里留下痕迹。

**⑤ 由此确认的两处新静默症状**

1. 整篇降级时 `html` 直接是空串（调用方至少能靠「空产物」发现这一种）。
2. `meta.summary` 会被**泄漏的容器语法污染**：`:::reading-path` 降级时 `summary` 是 `":readingpath"`、
   `:::compare` 时是 `":compare"`、`:::callout` 嵌套时是 `":callout type"tip" title"T""`——
   任何依赖 `meta.summary` 做摘要的调用方在降级时都会拿到这串垃圾。

**⑥ 仍未打的证伪实验**：`single-quote-attr` / `unquoted-attr` / `:::table` 内的非法值等
**属性值写法变体**只测了「能不能渲染」，没有逐条比 `meta.warnings`；规模类（超长表格、超多条目）
完全没测。上文 ③ 那张清单里剩下的项仍然是**未测**，不是「测过没问题」。

#### R2-附（第七轮补测）容器式的 `:::success` / `:::danger` / `:::layout-toc` / `:::layout-metrics` 上游同样没有实现

**这一条是 R1（`guide` 与注册表不一致）与 R2（静默降级）的又一个实例**，而且是容器式的——
不是「我们自己按标签式假设造出来的写法」（那 4 条已在 R4-附 驳回），所以这次能立得住。

第七轮把**全部 79 个样例**逐个挂上真实浏览器做「渲染服务产物 vs 编辑器渲染」对照（见
`docs/dev/known-issues-handoff.md` §3.11），其中 9 条判为 `na`——**参照侧自己就没产出组件**。
逐条当场复验后，其中 4 条的现场是**容器语法没被识别、产物是兜底段落、字面 `:::xxx` 留在可见文字里**：

| 样例 | 写法 | 参照侧（渲染服务产物）实测 |
|---|---|---|
| `ctn-success` | `:::success` | 可见文字里留着字面 `:::success`（21 字），产物是兜底段落 |
| `ctn-danger` | `:::danger` | 可见文字里留着字面 `:::danger`（22 字） |
| `reg-layout-toc` | `:::layout-toc` | 可见文字里留着字面 `:::layout-toc`（23 字） |
| `reg-layout-metrics` | `:::layout-metrics` | 可见文字里留着字面 `:::layout-metrics`（29 字） |

另有 `reg-layout-hero`（容器式）同样是**兜底段落**——上一版把它记成「576 字符正常渲染」，
第七轮量到那 576 字符里字面 `:::layout-hero` 还在，**「产物非空」不等于「渲染了」**。

**这四条的共同点**：两次渲染都是 `ok:true`、`warnings: []`——**又是 R2 那族静默处理**。
`ctn-success` / `ctn-danger` 在 `component_matrix.json` 里本就标注了「guide 说不在支持列表」，
所以对上游来说可能是有意不支持；**但「有意不支持」和「不报警告并把语法当正文吐出来」是两件事**。

**对上游的两条诉求**：
1. 要么实现这四个容器，要么在 `guide` 里明确写「不支持」并让渲染器**报 warning**（R1 + R2）；
2. `reg-layout-toc` / `reg-layout-metrics` / `reg-layout-hero` 三个 ID 在 bundle 注册表里有条目，
   但 `guide` 与语法分支都没有——**注册表条目不等于可用组件**，请澄清这三个到底是不是废弃 ID。

#### R2-附（第八轮补测）`layout-*` 家族是**整整一族**，不是 3 条孤立 ID；另 `:::callout type=` 无白名单

第八轮把第七轮那 3 条 ID 的假设放大成全族穷举（脚本 `target/probe/round8_unknown_tags.py`，
产物 `round8_unknown_tags.txt`）：**16 个 `layout-*` 名字 × {容器式, 标签式} = 32 组，全部泄漏**
（`leak=True`），`ok:true`、`meta.warnings` 为空。两种失败形态：

| 形态 | 实测产物 | 例子 |
|---|---|---|
| 容器式 | 可见文字**就是**那一行语法 | `:::layout-hero` → 576 字符 / `:::layout-heroZQLH内容:::`；`:::layout-toc` → 751 / `:::layout-tocZQLT1\|甲:::`；`:::layout-metrics` → 759 |
| 标签式 | **标签作为未知元素原样透传**（不是被转义成文字） | `<layout-hero>` → 219 字符 / 产物 `<p …><layout-hero>ZQLHT 内容</layout-hero></p>`；屏幕上只剩子文本 `ZQLHT 内容` |

> ⚠️ **一处口径更正（第十轮按原始产物复核）**：本表初版把标签式的形态写成「字面标签作为**文本节点**留在 `<p>` 里」——
> 这是**错的**。直接看产物 HTML：`<p style="…"><layout-hero>甲 乙</layout-hero></p>`，
> `<` 与 `>` **没有被转义**，标签是作为**未知元素**留在 DOM 里的。
> 教训不变、措辞要改：不论是「当文本输出」还是「当元素透传」，`soup.get_text()` 都拿不到标签名
> （获取的是元素的后代文本），**泄漏检测必须扫原始产物 HTML**。
> 两者的区别在于**用户看到的**：文本形态至少会显示一行 `<layout-hero>`，元素形态**屏幕上什么都不显示**
> ——后者更隐蔽，用户只会觉得「这里少了一块」。

16 个名字（全部实测泄漏）：`layout-hero`、`layout-toc`、`layout-metrics`、`layout-cards`、
`layout-part`、`layout-label-title`、`layout-infographic`、`layout-compare`、`layout-steps`、
`layout-timeline`、`layout-checklist`、`layout-stat-row`、`layout-verdict`、`layout-myth-fact`、
`layout-image-annotate`、`layout-audience-fit`。

> ⚠️ **一个检测口径上的坑，值得写进上游的验收标准**：标签式泄漏**不能靠 `soup.get_text()` 找 `<layout-hero`**
> ——标签是作为**未知元素**留在 `<p>` 里的（见上表下方的口径更正），`get_text()` 返回的是元素的后代文本，
> 标签名与属性都会丢。泄漏检测必须扫**原始产物 HTML**。

**同批测出的另一条**（`round8_callout_types.py` → `round8_callout_types.txt`）：`:::callout type=` **没有白名单**——

| type | 边框色 | 图标 | 结果 |
|---|---|---|---|
| `tip` / `note` / `info` / `warning` / `caution` / `important` / `danger` / `success` | `#16a34a` / `#2563eb` / `#0ea5e9` / `#ea580c` / `#dc2626` / `#7c3aed` / `#dc2626` / `#16a34a` | 💡 📝 ℹ️ ⚠️ 🚨 ❗ ❌ ✅ | 各自配色 |
| `error` / `bogus` / **不写 type** | 全部 `#0ea5e9` | 全部 ℹ️ | **静默回退成 `info`** |

这一条**不算缺陷**（优雅降级是合理的），但它在 `guide` 里没有任何记载：按 guide 写，
`type="error"` 看起来像「错误提示」，实际拿到的是蓝色信息卡，且**没有任何 warning 提示这是回退**。
属于 R1 那族「`guide` 与实现不一致」，建议在 `guide` 里补上合法 type 清单。

#### R2-附（第八轮补测之二）嵌套组合里的两种新形态：**裸的收尾 `:::`** 与 **原样透传的元素**

第八轮把 17 个「组合条件」用例（容器套容器 / 属性交织 / 同组件多次 / 超长 / 行内混排）逐个打真实渲染 API
（脚本 `round8_combos.py`，两级判定表 `known-issues-handoff.md` §3.12②），又补出两种**此前没记过**的静默降级形态：

| 形态 | 实测 | 为什么值得单记 |
|---|---|---|
| **光秃秃的收尾 `:::`** | `:::timeline` 里嵌 `:::table`（产物 4830 字符）与 `:::case-flow` 里嵌 `:::table title=`（1287 字符），**可见文字结尾都挂着一个裸 `:::`** | 开头的 `:::[名字]` 反而被消费掉了，只剩收尾那一行原样输出——**按 `:::[a-z-]+` 检索一处都数不到**。本项目第一版判据就是这么漏的（少判 2 例，见 §3.12② 的修正说明） |
| **原样透传的组件元素** | `:::callout` 里嵌 `<steps>`（485 字符）：可见文字**一个不少**、`:::` **一处没有**，但 `<steps>` / `<step>` 作为**未知元素**留在产物 DOM 里（`tagHistogram` 有 `steps:1` `step:3`），三个 `<step title="…">` 的**标题字面全部丢失** | 「产物干净」和「渲染正确」在这里彻底分家：**只有量 DOM 才能发现**。这属于 §R2 那族静默降级，而且是最难被用户说清的一种（他看到的是「步骤没排版」，不是「少了东西」） |

**对上游的诉求**（并入 R2）：`:::` 容器嵌套未实现时，要么渲染要么报 warning，
**不要留下一个裸收尾**——那比整块透传更难被发现。另 `<steps>` / `<step>` 未闭合或不被消费时，
也应在 `meta.warnings` 里给一条。

#### R2-附（第十轮补测）`layout-*` 是**整族 38 个名字**（不是 16 个），并且和引擎自己声明的组件全集对不上

第八轮把假设放大到 **16 个** `layout-*` 名字；第十轮把它放大到**从引擎前端 bundle 里读出来的全族**
（脚本 `target/probe/round10_registry_closure.py`，产物 `target/probe/round10_registry_closure.txt`）：

| 口径 | 数量 | 出处 |
|---|---|---|
| 引擎 bundle 里**声明的组件 ID 全集** | **63** | `target/probe/component_registry.json`（形如 `{Title_DA01:"title", …, "layout-changelog":"other"}`） |
| 其中 `layout-*` | **38** | 同上 |
| 渲染器里**实际有语法分支**的组件（正则匹配器） | **29** | `target/probe/component_matchers.json`——**里面没有任何一条 `layout-*` 分支** |
| 第十轮实测的 `layout-*` 写法 | **38 名字 × {容器式, 标签式} = 76 组** | `target/probe/registry/registry.json` |
| 76 组里上游**渲染成功**的 | **0** | 逐组 `ok:true`、`meta.warnings` 为空、产物里留着字面语法 |

⇒ **这 38 个 ID 只存在于 Web 端组件注册表里，服务端既没有语法分支、`guide` 里也没有记载。**
`:::` 容器式在可见文字里留着字面 `:::`（且**开标签与结尾的 `:::` 各自单独成段**，合计 2 处），
标签式则是**未知元素原样透传**（见上一小节的形态更正）。
两种形态的完整清单与逐组产物见 `target/probe/registry/*.html` 与 `round10_registry_closure.txt`。

**对上游的诉求（R1 + R2 的具体化）**：
1. **澄清这 38 个 `layout-*` ID 的定位**——是「Web 端专用、服务端不支持」，还是「已废弃但没从注册表删掉」？
   注册表里有条目、`guide` 与语法分支里没有，调用方**无法从任何一份公开来源判断**；
2. 若确实不支持，请在 `guide` 里明确列出，并让渲染器在遇到这些写法时报 `meta.warnings`
   （不要静默把它们当正文吐出来——容器式会留下可见的字面 `:::`，标签式则是一处**屏幕上完全看不见**的空白）。

**本项目侧的处置**：这一族已全部标为「等上游」，并**逐条给出不改判据的替代写法**（第十轮实测
**10 条替代写法在两条路径上全部通过**，见 `known-issues-handoff.md` §3.14④），
因此它**不阻塞**本项目的交付，只影响「能不能用这几个 ID 本身」。

#### R2-附（第十一轮复核）用独立通道再确认一次：38 个 `layout-*` 没有任何一条能在服务端渲染

第十轮那份结论（38 名字 × 2 写法 = 76 组全部上游未渲染）和判据代码出自同一条链路。
第十一轮换了**三条互相独立的证据通道**复核（脚本 `target/probe/round11_crosscheck.py`，
产物 `round11_crosscheck.txt` / `.json`），结论**逐条一致**：

| 通道 | 量 | 结果 |
|---|---|---|
| 裸 HTTP 响应体（另写一份脚本、另换一套判据：数「产物里有没有以组件名命名的元素」与「可见文字里有没有字面 `:::`」） | 抽 9 个 ID × 2 写法 = 18 组 | **18 组全部「上游未渲染」**；HTTP 全是 200、`ok:true`、`meta` 只有 `title`/`summary`（无 `warnings`） |
| 引擎包静态结构 | `mf_app.js` 里 `layout-*` 的出现形态 | 38 处**全部带引号**；**不带引号 0 处**；正则字面量 `/layout-…/` **0 条**；`layout-x":{…}` 匹配分支 **0 条** |
| 服务端实时 guide | `GET /__markflow_render` 的 `guide`（7672 字符）里数这 38 个名字 | 出现 **0 次** |

**判据的反证**：同批另跑 3 个**已知会被渲染**的写法（`:::breaking` / `:::callout type="tip"` /
`<badge type="tip" title="推荐" />`），同一套判据下**全部判成「上游已渲染」**——
用于排除「判据一律判未渲染」这种假阳性（该假阳性本轮真的出现过一次，见
`known-issues-handoff.md` §4.2 第 11 条）。

**清单的可复现性**（别人要拿到同一份 38 个 ID）：

```bash
# 1) 从官网页面找当前 bundle 名，再下载
curl -s https://www.bx9y.com.cn/markflow/ | grep -o 'assets/[^"]*\.js'
curl -s https://www.bx9y.com.cn/markflow/assets/<上一步拿到的名字> -o mf_app.js
# 2) 抽注册表（脚本按 {…Engage_DA02:"cta"…} 这个字面量定位）
python target/probe/component_matrix.py --dump-registry   # → component_registry.json，63 个 ID / 38 个 layout-*
```

第十一轮实测：**重新下载的线上 bundle 与第五轮存档的那份不是同一个构建**
（sha256 `3d660b13…` vs `d3478dfe…`），但两条抽取路径（注册表解析 / 直接扫带引号字面量）
都给出**同一份 38 个 `layout-*`**——这份清单不是某一次下载的偶然产物。

### R3（P2）同一个组件在 API 与 Web 导出里的排版不一致

**R3-a 确认的版本差（只有 `:::reading-path` 一类命中）**

- **现象**：`:::reading-path` 的节点几何两边完全一致（节点宽 126px、编号圆点 34×34px、连接线 32×1px + `linear-gradient(90deg,#94a3b859,#94a3b8d9)`、容器 `padding:14px 12px 12px` / `border-radius:14px`），
  但微观排版不同：

  | 属性 | Web 导出（官网示例） | 渲染 API 实测 |
  |---|---|---|
  | 编号圆点 `border-radius` | `999px` | `50%`（等效） |
  | 编号字号 / 字距 | `11px` / `letter-spacing:1.2px` | `12px` / 无 |
  | 章节标题字重 | `800` | `700` |
  | 章节标题颜色 | `#111827` | `#1a1a1a` |
  | 容器边框色 | `#e5e7eb` | `#e2e8f0` |
  | 容器投影 | `rgba(15,23,42,0.08) 0 12px 30px` | 无 |

- **最小复现**：`target/probe/probe_rp_control.py`（同一段 `:::reading-path` 连续渲染 4 种参数组合 + 2 种主题色，输出 `reading_path_control.txt`）；
  对照文件是用户从 `https://www.bx9y.com.cn/markflow/` 导出的官网示例 HTML。
  另测过 `type="DA01|DA02"`、`style="card"`、`variant="2"`、`size="lg"` 与 `dark` 色：产物字节数完全相同——**没有哪个属性能把 API 侧切成 Web 侧那套值**。
- **期望行为**：两条链路用同一个渲染实现（或明确文档化差异），否则「照官网示例对齐视觉」这件事在服务端做不到。

**R3-b 其余组件：逐项比对结论（2026-09-13 复测，样本=官网示例 vs 文章 25/26/28/29）**

| 组件 | 结论 | 说明 |
|---|---|---|
| `callout` 提示卡 | **逐项一致** | 容器 `margin:16px 0` / `padding:16px 14px` / `border-left:4px solid` / `border-radius:0 10px 10px 0`、正文 16px/700，全部相同 |
| `p-title` 段落标题 | **逐项一致** | 30px/900/`#111827`/`-0.5px`；副标题 11px/700/大写/`1.6px` |
| `statement` 金句 | **逐项一致** | `text-align:center` / 18px / 700 / `rgb(51,65,85)` / `line-height:1.6` |
| `table` 表格 | **除主题色外逐项一致** | `border-collapse:collapse`/`width:100%`；表头 `padding:13px 14px`/13px/600/`#fff`/首末格圆角 `12px 0 0 0`·`0 12px 0 0`；单元格 `padding:11px 14px`/13px/`#475569`/`border-bottom:1px solid #f8fafc`/隔行 `#fafafe`。差异只有表头底色（官网 `#f39c12` = 其主题色，我们 `#0984e3` = 我们主题色） |
| `timeline` 时间线 | **除主题色外逐项一致** | `margin-left:9px` / `padding-left:18px` / `border-left:2px solid`（透明度 .3）；日期 13px/700/`letter-spacing:0.5px`；标题 17px/800/`rgb(17,24,39)`/`line-height:1.4`；说明 14px/`rgb(100,116,139)`/`line-height:1.6`。差异只有强调色 |
| `lead` 引导块 | **除主题色外逐项一致** | 16px/`rgb(85,85,85)`/`line-height:1.8`/`letter-spacing:0.5px`/`text-align:justify`/`padding:16px`/`border-left:3px solid`/`margin:14px 0` |
| `steps` 竖向步骤卡 | **无我方样本** | 官网示例有 4 张，我们 4 个样本（25/26/28/29）一张没有——是「模型没用」，不是渲染差异 |
| `code` 代码块 / `title` 标题卡 | **无我方样本** | 官网示例各 1，我方 4 个样本均为 0 |
| `breaking` 开篇大卡 | **无官网样本，改自比** | 官网示例里 `:::breaking` 用了 **0 次**，不存在可比对值。改用跨轮次自比：文章 26（run#72）与文章 28（run#74）**逐条声明完全一致**（容器 `24px 0`/`30px 20px`/16px 圆角、`radial-gradient(circle 60px at 92% 30px,…)` + `linear-gradient(135deg,…)`、角标 11px/700/`letter-spacing:1px`、主标题 22px/800/`#1a1a1a`、副标题 14px/`#64748b`、标签 `border-radius:12px`/11px、正文 14px/`#475569`/`line-height:1.8`） |

⇒ **只有 `reading-path` 一张表是真的「同一组件、两套值」**；其余可比组件要么完全一致，要么差异只来自主题色。
这缩小了 R3 的影响面：**不需要全量对齐渲染实现，只需要看 `reading-path` 那一处**。

⚠️ **两条不能混进来的**：主题色差（官网示例用的是 `#f39c12` 橙色主题，我们文章是 `#0984e3` 蓝色）
不是缺陷，是我们自己的主题配置；`steps`/`code`/`title` 的「官网有我们没有」是**模型选型问题**（已在
D24/F10 的技能提示里覆盖），不是渲染器问题。

**R3-c 当前绕过**：`reading-path` 那几项差异，本仓库侧无法通过输入弥补（R3-a 的控制实验已排除参数路径），
只能记录；其余组件无需绕过。

### R4（P2）标签式 `<slider>` 必须有闭合标签，自闭合写法被当普通正文原样透传

- **现象**：`<slider images="…" interval="3" … />`（自闭合，斜杠前有无空格都一样）**不被识别**——
  整行标签连同属性留在 `<p>` 里（356 字符、无 `<svg>`），HTTP 200 + `ok:true`，`meta` 里没有 `warnings`。
  **同一个 open tag 只要补上 `</slider>` 就出 1141 字符的完整 SVG 轮播**，与官方容器式 `:::slider` 字节数完全相同。
- **判别实验（10 组逐个隔离变量，`target/probe/r4_slider_discriminator.py` → `.json` / `.txt`）**：

  | # | 写法 | 产物 | `<svg>` | 结论 |
  |---|---|---|---|---|
  | 1 | `<slider …>\n</slider>` | 1141 | 1 | 正常 |
  | 2 | `<slider …></slider>` | 1141 | 1 | 正常 |
  | 3 | `<slider … ></slider>` | 1141 | 1 | 正常（`>` 前空格无害） |
  | 4 | `<slider …/>` | 321 | 0 | **字面透传** |
  | 5 | `<slider … />` | 322 | 0 | **字面透传** |
  | 6 | `<slider …/></slider>` | 1141 | 1 | 正常——**斜杠本身无害** |
  | 7 | `<slider …>`（无闭合、无斜杠） | 320 | 0 | **字面透传** |
  | 8 | `<slider images="单图"></slider>` | 214 | 0 | 正常（单图 → 普通 `<img>`，合理） |
  | 9 | `<slider images="单图"/>` | 255 | 0 | **字面透传** |
  | 10 | `:::slider …`（官方容器式，对照） | 1141 | 1 | 正常 |

  ⇒ 判别条件是**有没有 `</slider>` 闭合标签**，不是属性个数、不是自闭合斜杠前的空格（第 6 行排除了斜杠这个变量）。
- **bundle 侧的对应位置**（读 `target/probe/mf_app.js`，**未在浏览器里跑**）：
  `{name:"slider",match:e=>/^<slider\b/.test(e),render:(e,t,n,i)=>{const r=xe(n,i,/^<slider\b([^>]*)>(.*)$/,/<\/slider>/);…}}`
  ——**匹配器接受自闭合行，渲染分支却要求找得到 `</slider>`**，两者不一致：自闭合行会被认成 `slider` 元素、
  随后取不到闭合标签。Web 编辑器在这种输入下的实际表现**第六轮已在真实浏览器里补测**：
  编辑器把未知元素解包，标签消失、一个字符都不剩（产物是一处空白），见「当前绕过」第 4 条与
  `target/probe/browser/browser_summary.md` 的对照表。
- **最小复现**：`python target/probe/r4_slider_discriminator.py`；组件样例见
  `component_matrix.py` 的 `blk-slider`（成对写法，1141 字符）与 `blk-slider-selfclose`（自闭合，356 字符）。
- **期望行为**：自闭合写法要么正常渲染，要么在 `meta.warnings` 里报一条「`<slider>` 未闭合」，
  而不是把标签当正文塞进 `<p>`。
- **当前绕过（2026-09-13 第六轮已补齐，可端到端复核）**：
  1. **技能提示只给容器式**：`SkillSeeder` 的 MarkFlow 技能（库里 `SKILL` 表 ID=4）写的是
     `:::slider images="图1直链,图2直链" interval="3" …`，全文**没有** `<slider` 这种写法
     （`SELECT LOCATE('<slider', CONTENT) FROM SKILL WHERE ID=4` → 0；`LOCATE(':::slider', …)` → 2810）。
  2. **保存自检就地拦下标签式**（第六轮新补的，`ScheduledArticleTools.markflowSyntaxHints`）：
     自闭合 `<slider … />` 与「只开不闭」`<slider …>` 都会在保存那一刻拿到一条提示，指向容器式写法。
     判据是**有没有 `</slider>` 闭合标签**，不是有没有自闭合斜杠——`<slider …/></slider>`（斜杠与闭合标签
     同时在）实测同样出 1141 字符，按斜杠判会误报。**在此之前这个自检根本不看标签式**，
     模型写了也收不到任何反馈，这条绕过等于只做了一半（详见 `known-issues-handoff.md` D40）。
  3. **端到端复核（真实 API + 真实 MySQL + 真实浏览器）**：容器式产出的文章（ID 42）落库
     `CONTENT_HTML` 2662 字符、含 1 个 `<svg>` / 1 个 `<animateTransform>` / 3 个 `<foreignObject>`；
     编辑器侧（真实 SPA）里同一个 `<svg>` 有 600×200 的真实盒子。原始输入输出见
     `target/probe/round6_r2r4.md`、`target/probe/round6_e2e.md`、`target/probe/browser/editor_result.json`。
  4. **模型写错时用户看到的是什么**（第六轮真实浏览器实拍）：不是一行看得见的标签，而是**一处空白**——
     后端把 `<slider … />` 当正文透传成未知 HTML 元素，浏览器把它渲染成空的 inline 盒子，
     编辑器解包后一个字符都不剩（`target/probe/browser/shots/blk-slider-selfclose.png`）。
     这正是不靠肉眼、必须在保存时自检的原因。

### R4-附（驳回）第五轮曾误判为「上游不渲染」的另外 4 条，复核后**不成立**

> 这一节记录的是**我们自己**的误判，写在这里是为了不让一份"直接转给上游"的清单里混进假缺陷。
> 第一版 R4 把 5 个标签式样例都记成上游缺陷；定稿前回查官网 bundle `mf_app.js` 的组件 `spec`，
> 发现其中 4 条的输入是**我们自己按「标签式」这个假设造出来的**，官方并没有这种写法。

| 第五轮的记录 | 实际测的输入 | 复核结论 | 证据 |
|---|---|---|---|
| `<case-flow>` 标签式产物 0 字符 | `<case-flow label="…">` + 行 `[ZQCF1] 标题 \| 描述` | **输入错**：官方行格式是 `- [标签] 标题`（bundle spec 的 `example` 原文）。换成官方行格式 → **1034 字符**正常渲染 | `r4_recheck.py` 的 `case-flow/A`、`C1`；`component_matrix.py` 的 `blk-case-flow` |
| `<gov-header>` 丢 `title`/`subtitle` | `<gov-header title="…" subtitle="…">` | **输入错**：该组件的字段是 `issuer`（**必填**）/ `doc-no` / `classification` / `urgency` / `signer`，**根本没有** `title` / `subtitle`。换成官方字段 → **941 字符** | `r4_recheck.py` 的 `gov-header/C1`；`component_matrix.py` 的 `blk-gov-header` |
| `<hint>` 标签式不渲染 | `<hint>ZQHINTTAG</hint>` | **写法不存在**：官方只有容器式 `:::hint type=… title=…`（bundle spec 的 `example` 原文）→ 349 字符正常 | `r4_recheck.py` 的 `hint/A`；样例 `ctn-hint` |
| `<layout-hero>` 标签式不渲染 | `<layout-hero>…</layout-hero>` | **写法不存在**：这个 ID 在 bundle 里**只有注册表条目**（`"layout-hero":"intro"`），没有 `spec`、也没有语法分支 → 容器式 `:::layout-hero` 576 字符正常 | `target/probe/mf_app.js`；样例 `reg-layout-hero` |

**其中仍然成立的一条**：`<case-flow>` 的行不匹配官方格式时，**整块归零**（0 字符）且 `ok:true`、无 `warnings`
（样例 `blk-case-flow-badline`）。这不是「标签式不渲染」，而是 R2 已经记过的**静默归零**族，
故不在此重复立项。

**这一节的教训**：「某个写法不渲染」这个结论，**必须先确认该写法在官方来源（`guide` / bundle `spec` / bundle 语法分支）里有定义**，
否则测出来的是自己的假设。本轮据此把出问题的 4 条样例按 bundle `spec` 的 `example` 原文重新对齐，
并在两路径对照表里新增 `blk-slider-selfclose` / `blk-case-flow-badline` 两个反例样例保留证据。
**注意范围**：只回查了这 4 条，其余 75 个样例的写法**没有逐条回查官方定义**。

#### R4-附（第八轮补测）单图轮播的两种写法复测 + 那张「方向相反」的自闭合规则

第八轮的 `na` 判别器（`target/probe/round8_na_discriminator.py` → `.txt`）用**单图**又打了一遍这两种写法，
R4 上表的两行**逐项复现**（单图时闭合写法本来就该退化成普通 `<img>`，不是 SVG 轮播）：

| 写法（单图 `banner4.webp`） | 产物 | 可见文字 | `<svg>` | 残留 |
|---|---|---|---|---|
| `<slider … />`（自闭合） | 303 字符 | 0 | 0 | **字面 `<slider` 留在正文** |
| `<slider …>\n</slider>`（开+闭，空体） | 214 字符 | 0 | 0 | 无 → 渲染成一张普通 `<img>` |

⇒ R4 的判别条件（**有没有 `</slider>` 闭合标签**）在单图场景下同样成立，**无需更正**。

**本轮真正要记的是本项目自己的一处坑**（已修，`known-issues-handoff.md` D45）：
技能文本 ③ 那条只写了「`<badge>` / `<icon>` **必须自闭合**」，模型很容易把它当成通用规则——
而对 `<slider>` 来说**方向恰好相反**（自闭合会把整行标签当正文吐出来，轮播完全不出现）。
技能已改为明确点出「容器式优先；标签式 `<slider>` 必须配对 `</slider>`，且这条与 ③ 方向相反」。

---

## 二、agent4j 2.3.3

### A1（P0）既没有读超时、也没有取消接口

- **现象**：阶段超时到点后，调用方只能放弃等待；底层 `OkHttpClient` 是 `OpenAIChatModel` 内部 `final` 自建的，HTTP 连接与服务端 SSE 流保持打开。流若随后恢复，可能与**下一次运行**（重试后的新会话）同时产出。
- **最小复现**：任一次上游停止发流的会话——`LLMResult.get()` 永不返回；`jstack` 可见线程停在 `LLMResult.get(LLMResult.java:77)` → `AgentClientSession.executeCommand`。本项目 `docs/dev/known-issues-handoff.md` §5.3 有活体线程栈。
- **期望行为**：`LLMModel.create` 支持 readTimeout（或 `AgentClientSession` 提供 `cancel()`/`close()`），让调用方能真正释放连接。
- **当前绕过**：本项目在运行级做并发闸门（默认 4 个在飞）+ 300 秒阶段止损 + 「零工具调用」才重试，避免卡死无限累积与重复副作用。

### A2（P2）`temperature` / `maxTokens` 只存不用

- **现象**：配置页保存成功但不生效。
- **最小复现**：`LLMModel.create` 只有 4 个参数（provider / baseUrl / modelName / apiKey）。
- **期望行为**：提供参数透传入口。
- **当前绕过**：UI 上显式标注「待 agent4j 支持后接线，当前仅保存不生效」；`AgentFactory.createModel` 处留了注释与 WARN 日志指向接入点。

### A3（P2）`create_plan` / `create_sub_agent` 在调用方无法关闭

- **现象**：这两个内置工具在定时链路里无条件可用，本项目关不掉（已核实到字节码）。
- **最小复现**：见 `docs/dev/known-issues-handoff.md` §3.5 F6 第 2 条。
- **期望行为**：提供关闭开关或工具白名单。
- **当前绕过**：靠提示词与预算约束引导，不能硬禁。

### A4（P2）`ToolParam` 解析模型返回的非法 JSON 时直接判工具失败，长参数更容易踩

- **现象**：run#73（2026-09-13，审核阶段）连续 3 次出现
  `【审核】工具失败：submit_review - Failed to parse tool param JSON: {...}`，
  直接导致返工轮次用尽、按「有 5 次工具调用失败」交付。
  失败原因在参数本身：模型把审核结论写成 JSON 时，**字符串值里嵌了未转义的英文双引号**
  （如 `"严重事实错误：正文称"约翰·特努斯…"——…"`），是非法 JSON。
- **最小复现**：让模型产出一个「字符串值里含双引号」的长 JSON 参数（审核结论、引文、代码片段都容易触发），
  用 agent4j 的 `ToolParam` 解析即可复现。报错文本来自 `ink.icoding.llm.core.tool.ToolParam`
  （`unzip -p agent4j-2.3.3.jar` 后可见该字符串常量），不是本项目代码。
- **期望行为**：解析失败时做一次安全修复（转义字符串值内的裸双引号 / 容忍尾随内容），
  或把原始参数回传给模型让它自我修正，而不是直接计入工具失败。
- **当前绕过**：本项目只能把工具失败数如实记进 `stages_summary.toolFailures` 并继续（run#73 记了 5 次）。

---

## 三、网关

### G1（P1）并发上限 max=6，超出直接 429

- **现象**：`HTTP 429 concurrent limit exceeded: running=7 max=6`。这是本项目 D1 事故的物理根因。
- **最小复现**：并发发起 7 个以上 LLM 请求即可复现（本项目用 `app.llm.max-in-flight` 压到 4 来避免）。
- **期望行为**：提额，或申请独立配额。
- **当前绕过**：`app.llm.max-in-flight=4`（留 2 个余量给编辑器会话）+ 有界退避重试（1s/2s/4s，共 4 次）。

### G2（P2）流式链路在部分网络环境下静默停滞

- **现象**：本机经 TUN 代理出网时，SSE 首轮即停滞：`netstat` 有 ESTABLISHED 到 443 的连接但无数据、无 outbound 报错、agent4j 的 Future 不完成；而同一时刻 `GET /v1/models` 无鉴权 0.05 秒返回 401（HTTP 面正常）。
- **最小复现**：见 `docs/dev/known-issues-handoff.md` §3.2 I7 的复现步骤（直连与代理各跑一次对照）。
- **期望行为**：服务端在流上发心跳/保活帧，让客户端能区分「模型在思考」与「链路已死」。
- **当前绕过**：换直连网络；客户端侧靠 300 秒阶段超时止损。
