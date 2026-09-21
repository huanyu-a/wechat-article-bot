# 对外诉求清单（可直接转发）

> **用途**：把本项目**改不动、只能对外提**的诉求汇成一份能直接提给对方（agent4j / MarkFlow / LLM 网关 / 微信平台）的清单。
> **口径**：每条沿用上游文档原编号（A/R/G），便于回填到 `docs/dev/upstream-issues.md` 与 `docs/dev/known-issues-handoff.md`。
> **素材**：`docs/dev/upstream-issues.md`（R1–R10 / A1–A5 / G1–G2 / V1 主账）、`docs/dev/known-issues-handoff.md`（U1/U2/U3、F6/F7）、
> `docs/dev/agent-session-timeout-handoff.md` 第五节、`docs/handoff/latest.json` 与 `docs/handoff/sess-*.json` 的上游 next/findings。
> **成文日期**：2026-09-20。本文件只做汇编与措辞订正，**未重新核验任何结论**（订正后的措辞见文末「提诉求时的注意事项」）。

---

## 结论速览

**A. 阻塞「根治」的硬诉求（3 条，都有活体证据）**

| 诉求 | 对象 | 为什么硬 |
|---|---|---|
| **U1 / A1（P0）**：agent4j 缺可取消与会话级读超时 | agent4j | 网关 SSE 停滞/零字符输出每轮都出现，`LLMResult.get` 永不返回；300s 阶段超时只能杀掉等待线程，连接与名额仍被占。`docs/handoff/latest.json:5`：**「根治卡 U1/U3 上游」** |
| **U3 / G1（P1）**：网关 max=6 | LLM 网关 | 429 报文原文存档（`docs/dev/agent-session-timeout-plan.md:43`），D1 事故物理根因；本仓库闸门只保证自己不打满，上限本身不变 |
| **W1（新增，无原编号）**：微信平台是否保留 MathML / `<style>` / `<svg>` | 微信平台 | 公式落库保留 MathML 与 KaTeX `<style>`，但平台侧是否过滤无登录会话可验（`docs/dev/known-issues-handoff.md:396`），直接决定公式类文章要不要内联样式兜底 |

> 三条都不阻塞「交付」（都有应用层兜底：并发闸门 4 + 300s 止损 + 退避重试；公式有内联样式兜底路径），但**每条都会让定时链路固定落在 `SUCCESS_WITH_WARNINGS`**，无法收口成干净成功。

**B. 只是改进项（不影响交付，按性价比提）**

- **A2 / U2（P2）**：`temperature` / `maxTokens` 只存不用（UI 已标注「待 agent4j 支持后接线」）。
- **A3（P2）**：`create_plan` / `create_sub_agent` 无开关，不可硬禁。
- **A4（P2）**：`ToolParam` 对非法 JSON 与形状不符零容错（run#73 JSON 非法、run#129 参数合法但形状不符）。
- **A5（P1）**：`handleToolCallsAndContinue` 的 tool 消息 id 读自 `ToolCallEntry`——**本项目 2026-09-18 起已用 `ReplayWireNormalizer` 完全绕过**（`src/main/java/ink/icoding/wechat/article/agent/ReplayWireNormalizer.java:155-178`），本条仅请上游补自身兜底，**不再是诉求重点**。
- **D56 衍生的上游健壮性项（无原编号，建议 P3）**：agent4j 对未知工具名 `toolMap.get(name)` 不判空直接 `fromTool(null)` → NPE。本项目已反射换成 `UnknownToolFallbackMap`（`docs/dev/known-issues-handoff.md:130`），纯属「上游顺手改更稳」。
- **G2（P2）**：流式链路静默停滞时无心跳，客户端无法区分「模型在思考」与「链路已死」。
- **R1–R10（MarkFlow）**：文档缺口 + 静默降级 + 6 条渲染缺陷（详见第二节）。

**C. 经核验已可关闭 / 不必外提的**

| 条目 | 处置 | 关闭理由 |
|---|---|---|
| **I3「`preview` 字段语义未定」** | **关闭，不外提** | MarkFlow 自己的技能文档已写明语义：`html` 供程序化消费、`preview` 是交付文件（`C:/project/MarkFlow/tools/render-server/skill/SKILL.md:43-53`）。本项目按此接线即可，不再需要外部确认 |
| **R7（`:::table style="card" title=` 被丢）** | **拟关闭，待对方确认** | 第四十一轮 79 样例回归里「参照侧 title 0→1」，即当前上游 bundle 已修复该缺陷（`docs/handoff/sess-20260919-full-patrol-d56-d57-verify.json:5`、`:56`）。建议向 MarkFlow 确认修复版本，并把 r16 探针的「表格标题元素」组标记为「依赖上游版本，升级时复查」 |
| **V1（验收工具自身盲区）** | **删除，不外提** | `docs/dev/upstream-issues.md:766-770` 自己就要求「真要转给上游时请把这一整节删掉」——它是本项目判据的局限，不是上游缺陷 |
| **A1 里「完全没有读超时」的旧表述** | **改写后再提** | 构造器其实硬编码 `readTimeout(300, SECONDS)`（三个模型类都是）：是「硬编码、不可配置、非会话级」，不是「没有」 |

---

## 一、agent4j 2.3.3

| 编号 | 优先级 | 一句话诉求 | 当前状态与证据出处 | 为什么本仓库做不了 | 请求对方做什么 |
|---|---|---|---|---|---|
| **A1 / U1** | **P0** | 提供会话级可配置读超时，以及 `cancel()` / `close()` | **开放**。`docs/dev/upstream-issues.md:652`；`docs/dev/known-issues-handoff.md:169-176`；线程栈停在 `LLMResult.get(LLMResult.java:77)`（`docs/dev/agent-session-timeout-handoff.md:29-49`） | `AgentSessionResult` / `AgentClientSession` / `LLMResult` 均无 `cancel()`/`close()`；三个模型类构造器各自 `new` 自己的 `OkHttpClient`，本仓库只能反射重建它并挂拦截器（`ReplayWireNormalizer.java:155-178` 已在生产路径上这么做），无法在会话级接管生命周期 | `LLMModel.create` 增加 readTimeout 参数（或 `AgentClientSession` 暴露 `cancel()`/`close()`），让调用方能真正释放连接、不白占网关名额 |
| **A2 / U2** | P2 | `temperature` / `maxTokens` 要有透传入口 | **开放**。`docs/dev/upstream-issues.md:659`；`docs/dev/known-issues-handoff.md:178-183`；`LLMModel.create` 只收 provider/baseUrl/modelName/apiKey | 本项目只在 `AgentFactory.createModel` 留了注释与 WARN 指向接入点；UI 三处已标注「仅保存不生效」 | `LLMModel.create` 增加这两个参数（或等价的 builder） |
| **A3** | P2 | 给 `create_plan` / `create_sub_agent` 一个关闭开关或工具白名单 | **开放**。`docs/dev/upstream-issues.md:666`；`docs/dev/known-issues-handoff.md:408-412`（构造器无条件 `new`、`getAllTools()` 无条件追加，`javap` 实证） | 字节码层面无开关、无配置字段，装配 `AgentClient` 时也关不掉 | 构造器提供开关（如 `enableOrchestrationTools(false)`），或让 `AgentDefinition.getTools()` 成为白名单 |
| **A4** | P2 | `ToolParam` 解析失败时做安全修复或把原始参数回传给模型，不要直接判工具失败 | **开放（本仓库已侧向兜底）**。run#73：3 次「字符串值内嵌裸双引号」；run#129：7 次参数合法但形状不符（`List<String>` 被传成对象）（`docs/dev/upstream-issues.md:673-691`） | 报错来自 `ink.icoding.llm.core.tool.ToolParam`；本项目只能加 `ToolParamRepair` 在解析前归一化（调用方绕行） | 解析失败时：转义裸双引号 / 容忍尾随内容，或把原始参数回传让模型自纠，而不是计入工具失败 |
| **A5** | P1 | `handleToolCallsAndContinue` 在 `entry.callId` 为空时兜底（用 `descriptor.getCallId()` 或按位置合成），使 assistant 与 tool 两侧成对 | **上游缺陷仍在，但本项目已完全绕过**。`docs/dev/upstream-issues.md:693-744`；`ReplayWireNormalizer.java:155-178` 在报文发出前补齐两侧 id（`known-issues-handoff.md` A5 的状态更新） | 上游不兜底；本项目侧已用 okhttp `Interceptor` 改 JSON 自救 | 上游自行兜底（`descriptor.getCallId()` 或稳定合成 id），让不依赖调包拦截器的调用方也能用 |
| **（新增，无原编号）** | 建议 P3 | 未知工具名不要 NPE：`toolMap.get(name)` 应判空或返回可诊断的失败 | **本项目已修复**。`docs/dev/known-issues-handoff.md:130`（D56）；模型幻觉工具名 → `fromTool(null)` → NPE → run#35 整轮 FAILED | 本项目已反射把 `toolMap` 换成 `UnknownToolFallbackMap`；但要靠反射说明上游结构没有给这个口子 | 对未注册工具名返回可诊断失败而不是 NPE |

> **本节最需要注意的两处措辞订正**（详见文末「注意事项」）：
> ① A1 不是「没有读超时」，而是「每次 socket 读超时硬编码 300s、不可配置、非会话级」；
> ② A1 不是「本仓库完全做不了」——反射重建 `OkHttpClient` 这条路本项目已经走通（`ReplayWireNormalizer.java:171`），
> 缺少的是**上游的取消/登记入口**，否则即使拿到了 `Call` 也无从让 `future` 完成。

---

## 二、MarkFlow（渲染服务）

> **前提**：线上公网渲染点是 `https://www.bx9y.com.cn/__markflow_render`（`tools/render-verify/paths.py:34`），探针结论都出自该端点；
> 本仓库生产链路自 2026-09-19 起可指向**自部署**实例（`paths.py:30` 支持 `MARKFLOW_RENDER_URL`，当前跑在宿主 8788，
> 见 `docs/handoff/latest.json` environment.render_server；构建源是 `C:/project/MarkFlow`）。因此本节的「对方」既是公网服务方，
> 也是 MarkFlow 仓库维护方——**同一条诉求两边都可以提**，自部署实例意味着部分缺陷可能先于公网被修掉。
> 另：补跑新探针需要 `MARKFLOW_RENDER_TOKEN`，该令牌**只能向渲染服务方索取**（外部输入，见 `docs/handoff/sess-followups-20260917-round5.json:196`）。

| 编号 | 优先级 | 一句话诉求 | 当前状态与证据出处 | 为什么本仓库做不了 | 请求对方做什么 |
|---|---|---|---|---|---|
| **R1** | P1 | `guide` 与 Web 组件注册表不一致：8 个容器组件在 guide 里一个字都没有 | **开放**。`docs/dev/upstream-issues.md:40-53`（实时 guide 7672 字符，七名命中 0 次） | `guide` 是上游返回的服务端指令；本项目只能在自己技能提示里补齐 | guide 的块级组件清单与注册表对齐（至少补这 8 个容器） |
| **R2** | P1 | 语法错误几乎不进 `meta.warnings`，只能靠产物反推 | **开放，且与上游自己的文档冲突**。`docs/dev/upstream-issues.md:55-83`、`:164-167`（实测只有 `compare` 列数、`timeline` 不足 3 列两类会报）；而 MarkFlow 技能文档承诺「容器未闭合、语法不符被降级为普通段落、缺列行被忽略等」都会进 `meta.warnings`（`SKILL.md:48`） | 报错通道在上游侧；本项目只能本地扫产物（`MarkFlowRenderService.detectLeakedSyntax` / `detectDroppedBlocks`） | 把文档承诺的降级情形逐条接到 `meta.warnings`（或直接 4xx）；尤其是整块归零（`reading-path`/`case-flow`）与嵌套容器留裸 `:::` 这两类 |
| **R2-附（layout-* 全族）** | 并入 R1/R2 | 澄清 38 个 `layout-*` ID 的定位；不支持就写进 guide 并告警 | **开放**。`docs/dev/upstream-issues.md:261-287`（38 名字 × 2 写法 = 76 组全部未渲染），三条独立通道互证 `:289-304` | 这族 ID 只存在于 Web 注册表；本项目已全部标「等上游」并给出替代写法（**不阻塞交付**） | 澄清是「Web 专用 / 服务端不支持」还是「已废弃未删」；若不支持，guide 明写 + 渲染器报 warning |
| **R3** | P2 | `:::reading-path` 在 API 与 Web 导出里排版不一致 | **开放（影响面已收窄）**。`docs/dev/upstream-issues.md:320-363`——只有 `reading-path` 一项是「同组件两套值」，其余可比组件除主题色外逐项一致 | 控制实验已排除参数路径（`type/style/variant/size` 全部同字节），输入侧无法弥补 | 两条链路用同一实现，或明确文档化差异 |
| **R4** | P2 | 标签式 `<slider>` 自闭合写法被当正文透传 | **开放**。`docs/dev/upstream-issues.md:365-412`（判别条件是**有没有 `</slider>`**，与自闭合斜杠无关） | 匹配器与渲染分支不一致在 bundle 内；本项目用保存自检 + 技能提示只给容器式绕过 | 自闭合要么正常渲染，要么报「`<slider>` 未闭合」的 warning |
| **R5** | P1 | `Title_DA01` 的 `box-shadow:` 属性名被吃掉 → 卡片没有投影 | **开放**。`docs/dev/upstream-issues.md:471-492`（同一字段 `:::table style="card"` 的 `box-shadow:` 完好，bundle 模板里属性名也在） | 上游模板拼接问题，本项目改不动；**无绕过**，这是「没有原项目好看」的可复现成因之一 | 修模板拼接，让属性名不丢；或让 `Ke.card`/`Ke.float` 与 `:::table` 的写法一致 |
| **R6** | P1 | `Title_DA01` 字数恒为「共 0 字」 | **开放**。`docs/dev/upstream-issues.md:494-501` | 同上（模板变量 `o` 取值问题） | `o` 取本次输入正文长度；API 路径拿不到正文时**不渲染这一格**，而不是渲染 0 |
| **R7** | P2 | `:::table style="card" title=` 的 `title` 被丢弃 | **疑似已修复，待确认**。原缺陷记录 `docs/dev/upstream-issues.md:503-513`；第四十一轮「参照侧 title 0→1 ⇒ §R7 在当前上游 bundle 已修复」（`docs/handoff/sess-20260919-full-patrol-d56-d57-verify.json:5`、`:56`） | — | 确认修复版本；若已修，请告知版本号，本仓库据此关闭该条并调整 r16 探针基线 |
| **R8** | P2 | `:::audience-fit` 第三列（评级）只做配色、不作为文字输出 | **开放**。`docs/dev/upstream-issues.md:515-524`（评级值被消费——绿 ✓ / 琥珀 ●，但文字 0 次） | 上游组件实现；本项目只能记录 | 评级作为文字输出（角标/右侧标签），或在 guide 里写明它只是配色选择器 |
| **R9** | P2 | `:::subscribe` 产出假表单：没有 `<input>`、没有 `<button>` | **开放**。`docs/dev/upstream-issues.md:526-539`（还带 `text-align:center;text-align:justify` 自相矛盾双声明） | 上游组件实现 | 二选一：给语义正确的表单元素，或承认是静态卡片并去掉双声明 |
| **R10** | P3 | 字段级多行值必须写 `\|` 块标量，文档没写且失败静默 | **开放（文档缺口 + 静默失败，不主张渲染 bug）**。`docs/dev/upstream-issues.md:541-607`（第二十轮反向自查证明这是引擎统一约定，故降级） | 文档与告警都在上游侧 | 任选其一：a) 在 guide 写明这条规则；b) `meta.warnings` 给一句「该字段的多行值需要 `\|` 块标量」 |
| **（新增，无原编号）** | 建议 P2 | 模型漏传工具参数导致 `save_article_draft` 被校验拒绝 | **观察项**。`docs/handoff/latest.json:5`、`docs/handoff/sess-20260919-full-patrol-d56-d57-verify.json:5`：空正文/空标题是模型漏传参数被校验按设计拒绝（`toolParamParseFailures=0`，重试即自纠） | 属模型侧行为，本项目只能靠校验与重试吸收 | 作为「模型质量」观察项反馈给网关方；不要求改协议 |

---

## 三、LLM 网关

| 编号 | 优先级 | 一句话诉求 | 当前状态与证据出处 | 为什么本仓库做不了 | 请求对方做什么 |
|---|---|---|---|---|---|
| **G1 / U3** | P1 | 并发上限 max=6 太低，超出直接 429 | **开放**。`docs/dev/upstream-issues.md:750-755`；`docs/dev/known-issues-handoff.md:185-190`；429 原文 `{"error":{"message":"concurrent limit exceeded: running=7 max=6",...}}`（`docs/dev/agent-session-timeout-plan.md:43`，网关 `nexus.bx9y.com.cn`） | 上限在网关侧；本项目闸门（默认 4）+ 有界退避只能防自己打满。且上限 6 也是本仓库调参的天花板（`docs/dev/agent-session-timeout-handoff.md:142`） | 提额，或申请独立配额；提额后本仓库同步抬高 `LLM_MAX_IN_FLIGHT` |
| **G2** | P2 | 流式链路静默停滞时发心跳/保活帧 | **开放**。`docs/dev/upstream-issues.md:757-762`；`docs/dev/known-issues-handoff.md` §3.2 I7 / §6.3 探测手法（本轮测得停滞率 1/8，复测 4/4 正常 ⇒ 间歇性） | 服务端行为；客户端只能靠 300s 阶段超时止损，无法区分「思考中」与「链路已死」 | 在 SSE 流上发心跳/保活帧；停滞时显式关闭流并给出错误事件 |
| **（G1 的现场伴随项）** | 并入 U1/U3 | 模型零字符输出 / 网关持续停滞 | **开放**。`docs/handoff/latest.json:5`：`StageTimeout`（调研停滞换档、`generate_image` 挂起 119–271s、模型 0 字符输出）是网关侧问题，被既有自愈吸收 | 见 A1/U1 | 与 U1/U3 一并向网关方反馈；本仓库侧继续采样（`agent-session-timeout-handoff.md:146`：agent4j 上游 `readTimeout`/`cancel` 仍是唯一根治路径） |

---

## 四、微信平台

| 编号 | 优先级 | 一句话诉求 | 当前状态与证据出处 | 为什么本仓库做不了 | 请求对方做什么 |
|---|---|---|---|---|---|
| **W1（新增，源文档无编号）** | 建议 P1 | 明确公众号是否保留 MathML、`<style>`、`<svg>` | **开放（确需外部确认）**。`docs/dev/known-issues-handoff.md:395`：MathML 结构可原样落库，但公众号编辑器是否保留未验证；KaTeX 配套 `<style>` 已放行，平台过滤策略未实测；`<svg>` 是否出现未取到样本。同文件 `:433` 明确这三项「本机都无法验证」 | 本机无公众号登录会话；`C:/project/MarkFlow/tools/render-server/wechat/validate_gzh_html.py:20-34` 的禁用清单只规定 `<style>`/`<script>`/`<div>`/`class`/`float`/CSS 变量等**不应**出现，**没有**涉及 MathML 与 `<svg>`，无法据此推断平台真实行为 | 给出三者的平台侧处置（保留 / 剥离 / 改写）的权威结论，最好附一份最小样例（含公式与内联 SVG）在真实编辑器的粘贴结果 |

> **本条不要与别条混淆**：`validate_gzh_html.py:20-34` 是 MarkFlow 自己的**约定**（「`<style>` 会被过滤，样式必须内联」），
> 不是微信平台的官方口径；本仓库据此不放行 `<style>` 之外的宽松化，但**公式兜底到底要不要改，取决于 W1 的答复**。

---

## 五、提诉求时的注意事项（措辞订正，别把错的事实提给对方）

1. **A1/U1 的「没有读超时」必须改成「读超时硬编码 300s、不可配置、非会话级」**。
   依据：三个模型类的构造器各自 `new` 了带 `readTimeout(300, SECONDS)` 的 `OkHttpClient`（本 ask 已核验结论）。
   提法建议：「现在每次 socket 读超时被写死成 300 秒，既不能按会话调整，也没有取消入口——请提供可配置的读超时 + `cancel()`/`close()`」。
   写成「完全没有读超时」会让对方去查一个不存在的问题，也会让本仓库 300s 阶段止损的存在显得矛盾。

2. **A1/U1 的「本仓库改不动」必须改成「缺少上游取消/登记入口」**。
   依据：`src/main/java/ink/icoding/wechat/article/agent/ReplayWireNormalizer.java:155-178` 已在生产路径上用反射重建那个 `private final OkHttpClient` 并挂上拦截器（`:171` 的 `field.set(model, client.newBuilder().addInterceptor(...).build())`）。
   正确的是：本仓库侧取消/登记 `Call` 在技术上是**可达路径（尚未实现）**；缺的是上游把 `Call` / `future` 暴露给调用方，否则拿到了 `Call` 也无从让 `LLMResult` 的 future 完成。
   写成「本仓库什么也做不了」既不准确，也削弱了诉求的正当性。

3. **I3 的「`preview` 语义未定」已可关闭，不要再外提**。
   依据：`C:/project/MarkFlow/tools/render-server/skill/SKILL.md:43-53`——`html` 是「全内联样式的正文片段，供程序化使用」，`preview` 是「**交付文件用它**」的自包含预览页。
   本项目按语义接线即可（发布用 `html`、交付用 `preview`），这条不需要任何外部答复。

4. **R7 提之前先确认是否已修**。
   依据：第四十一轮实测「参照侧 title 0→1」表明当前上游 bundle 已不再丢 `title`（`docs/handoff/sess-20260919-full-patrol-d56-d57-verify.json:5`、`:56`）。
   直接照抄旧条目去提，会提一个对方已经改掉的问题；建议在诉求里写成「R7 现象疑似已在某版本修复，请确认版本号」。

5. **R2 要引用上游自己的文档当依据，而不是只举本机穷举**。
   MarkFlow 技能文档 `SKILL.md:48` 明确承诺 `meta.warnings` 覆盖「容器未闭合、语法不符被降级为普通段落、缺列行被忽略等」且「**交付前必须检查该字段**」，而实测报的只有 `compare` 列数与 `timeline` 不足 3 列两类（`docs/dev/upstream-issues.md:164-167`）。
   用「你的文档说要报、实际不报」提，比用「我们测了 40 例只有 2 例报」更有力。

6. **不要把 V1（验收工具自身盲区）带进对外清单**。
   `docs/dev/upstream-issues.md:766-770` 自己写明：这一节性质不同，「真要转给上游时请把这一整节删掉」。

7. **R10 不要提成「渲染 bug」**。
   第二十轮反向自查已证明缺 `\|` 属于「不合引擎的字段级块标量约定」，诉求只保留「文档未覆盖 + 静默失败」这半截（`docs/dev/upstream-issues.md:541-547`）。
   另外用户当时用的到底是哪种写法**至今未确认**（他给过来的那段 `body: |` 能正常渲染），本条不能用来解释用户的抱怨。

8. **提 MarkFlow 诉求时说明取样端点与自部署现状**。
   所有 R 系列证据取自公网端点 `https://www.bx9y.com.cn/__markflow_render`（`tools/render-verify/paths.py:34`），
   而本仓库生产链已可切到自部署实例（`paths.py:30`，宿主 8788，源码 `C:/project/MarkFlow`，
   见 `docs/handoff/latest.json` 的 environment.render_server）——同一条诉求，自部署实例可能先被修掉，对方答复时请注明版本。

9. **所有「当前状态」都带日期**。
   本文引用的 handoff 记录写明「现场不可复核」的情形（如 run#74/#76-#78 的实跑属历史存档证据，
   见 `docs/handoff/sess-followups-20260917-round3.json:73`），引用时保留原措辞，不要把存档证据说成当下可复现。

---

## 六、回填位置（便于对方答复后更新主账）

| 文档 | 位置 |
|---|---|
| `docs/dev/upstream-issues.md` | R1–R10 `:40-607`；A1–A5 `:652-744`；G1–G2 `:750-762`；V1（不外提）`:771` |
| `docs/dev/known-issues-handoff.md` | U1 `:169`、U2 `:178`、U3 `:185`；F6/F7 `:391-433`；D56 `:130`；§八 第 1 条 `:6018` |
| `docs/dev/agent-session-timeout-handoff.md` | 第五节剩余工作 `:140-146`（第 6 条即 agent4j `readTimeout`/`cancel`） |
| `docs/dev/agent-session-timeout-plan.md` | 429 报文原文 `:43` |
| `docs/handoff/latest.json` | `next` 数组（U1/U3 上游条目）与环境说明（8788 自部署渲染服务） |
