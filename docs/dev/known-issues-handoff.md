# 交接文档：已知缺陷与未实现清单

- 生成时间：2026-09-11（本轮实施收尾后，并已合并并行会话 `sess_457cd5ec` 的只读现场证据）
- 适用代码：commit `5f88ca5`（2026-09-11 已推送到 `huanyu-a/wechat-article-bot` 的 `main`，提交状态见第四节）
- 一句话：**已修的缺陷不再需要重查（第二节给了索引与证据）；真正还欠的是第三节那 13 项——3 项卡在上游（agent4j / 网关），10 项是本仓库的缺口或架构限制。最先该修的是 I8（编辑器会话无硬超时）。**

## 〇、怎么读这份文档

| 你在做什么 | 看哪一节 |
|-----------|---------|
| 接手继续开发、想知道「还有哪些坑没填」 | 第三节（未实现 / 未根治） |
| 排查一个新报错，怀疑是老问题复发 | 第二节（已修缺陷索引），命中即说明是回归而非新缺陷 |
| 想知道根因、要看真实现场数据（线程栈 / run 号 / 停滞形态） | 第五节（现场证据与根因） |
| 需要自己动手抓现场（线程栈、探测网关、查库） | 第六节（可复用的现场诊断手法） |
| 要跑构建 / 跑测试 / 做实机验收 | 第四节（工程约束与验收方式） |
| 想找方案原文与逐轮自检记录 | 第七节（相关文档地图） |

优先级沿用项目惯例：**P0** 会造成数据错误或不可用；**P1** 有明显功能缺口/运维风险；**P2** 体验或可观测性瑕疵。

---

## 一、结论速览

**已经彻底解决、不需要再动的**（本节仅列结论，细节见第二节）：

- 网关 429 级联停滞 → 运行级并发闸门 + 429 指数退避
- 1800 秒挂死无止损 → 300 秒阶段超时 + 停滞卡点诊断（含最后一次活动与工具次数）
- 停滞失败时执行日志全丢 → 进度实时上报（`ProgressListener`）
- 被中止运行出现 8 小时**负**时长 → 时间戳统一为 JVM 本地时间
- 配图因描述超长失败、运行却记成功 → 描述写入前截断 + 新终态「成功（有警告）」
- MARKFLOW 排版退化／Markdown 源文丢失／编辑器往返丢内联样式与 `data-render-id`
- 文章版本回滚丢一半字段、且拿不回可重排的 Markdown 源文
- `/api/articles` 的 `skillIds` 读出来是字符串、原样 PUT 回去必 400
- 失败运行 `TOOL_CALL_COUNT` 恒为 0、COORDINATOR 无整轮工具预算

**仍然欠着的**（第三节逐条展开）：上游依赖 3 项（U1–U3）、本仓库缺口 10 项（I1–I10）。**其中 I8 优先级最高**——编辑器 AI 对话链路没有硬超时，是当前唯一一条「停滞即永久占掉一个网关名额、且不在任何历史记录里留痕」的路径，现场线程栈已抓到活例（挂了 79 分钟）。

根因与现场证据（run#33 的活体线程栈、停滞命中点、实施后 #35/#36/#38 的 300 秒止损形态）以及可复用的诊断手法（jstack 判读、网关停滞率探测、直查库）已合并进**第五、六节**。

---

## 二、已修缺陷索引（避免重复排查）

遇到下列症状先确认是不是回归：每条的「证据」都是当时可复现的量化事实，若今天重现，说明修复被回退了。

| # | 症状 | 关键证据 | 修复轮次 | 关键文件 |
|---|------|---------|---------|---------|
| D1 | 定时任务连续 FAILED、正好 1800 秒、`tool_call_count=0` | run#29/#31/#32 全部停在第 1 次调用；网关原文 `HTTP 429 concurrent limit exceeded: running=7 max=6` | 本轮第①②期 | `common/InFlightGate`、`schedule/AgentInvoker`、`schedule/TaskExecutionService`、`schedule/ArticleAiService` |
| D2 | 停滞让工作线程放弃后 HTTP/SSE 流仍开着，占住网关名额，越卡越多（级联） | 8 次内容完全相同的极简流式请求里 1 次 >40 秒无响应、耗时 0.76–19.4 秒抖动；`javap` 证实 agent4j 无 `cancel`/读超时 | 本轮第①期治因（闸门限流），**根因仍在**（见 U1） | `common/InFlightGate` |
| D3 | 停滞失败的运行 `EXECUTION_LOG` 为空（修复前 #26 有 277 字符） | run#35 `EXECUTION_LOG` NULL；改用实时上报后 run#36 留存 248 字符 5 行 | 本轮第④期（本轮自查发现的回归） | `schedule/AgentRunner.ProgressListener`、`schedule/TaskWorkspace` |
| D4 | 被中止的运行 `FINISHED_AT` 比 `STARTED_AT` 早 8 小时 | run#28 `13:32 → 05:42`；根因 MySQL 会话时区 UTC 的 `NOW()` 与 JVM 本地时间混用 | 本轮第③期（十九轮 P2-5 同源） | `schedule/TaskRunMapper.abortStale/formatTimestamp` |
| D5 | 工具调用失败（如 `generate_image` 超长失败）但运行记为 SUCCESS | #30 执行日志含 `Data too long for column 'DESCRIPTION'`，配图未插入却成功 | 本轮第③期 | `schedule/TaskExecutionService.completion`、`webui/views/TasksView.vue` |
| D6 | 描述超过 500 字必失败，且「把实体改到 2000」也无效 | `javap` 证实 `DefaultSmartMapperInitializer.syncDatabaseStructure` 只建表/ADD 列、**不发 MODIFY COLUMN**；改回 2000 该测试立刻复现 `Data too long` | 本轮第③期 | `asset/Asset.java`（`DESCRIPTION_MAX_LENGTH=500`）、`asset/AssetService` |
| D7 | MARKFLOW 文章改个标题就把排版丢了（渲染产物未留存） | 真实令牌端到端验收 | 此前（P0） | `article/Article.contentMarkdown/layoutEngine`、`ScheduledArticleTools.renderBeforeDelivery` |
| D8 | 智能体重排后 Markdown 源文被丢弃，无法再渲染 | 落库断言 | 此前（P0） | `article/ArticleService`、`ArticleRevision` |
| D9 | 编辑器往返一次，渲染产物内联样式与 `data-render-id` 全丢，AI「HTML 回灌」防护失效 | CDP 量化：修复前后步骤区 `684×58 → 684×276`、字面 `###` 有→无、破损占位 6→0；`git stash` 反例证明测量有效 | 十五/十六/十九轮 | `webui/src/editorExtensions.js`、`ArticleEditorView.vue` |
| D10 | 文章回滚是「部分合并」：作者/来源 URL 保持当前值，且永远拿不回 Markdown 源文 | 回滚 10/10 用例 | 十九轮 ⑤ | `article/ArticleRevision`（补 4 列）、`ArticleService.rollback`（`restoreOr`） |
| D11 | GET `/api/articles` 的 `skillIds` 是字符串，原样 PUT 回去 400 | `javap -v` 能证明注解在方法上但响应仍是字符串——两个 databind 包并存（HTTP 走 Jackson 3） | 十九轮 ④ | `article/Article.getSkillIds`（**Jackson 3** 的访问器级序列化器） |
| D12 | 失败运行的工具调用数永远是 0；COORDINATOR 整轮预算可到约 240 次 | 计数器移到工作区后失败路径也写；新增 `MAX_TOTAL_TOOL_CALLS=120` | 十九轮 ③⑧ | `schedule/TaskWorkspace`、`ai/DelegateTools` |
| D13 | 排版技能文档推荐的 `:::steps` 实际渲染成破损圆形 | CDP 实测：`###` 与正文各拆成 38px 圆形；正解是 `<steps>`+`<badges>`、>3 步须显式 DA02 | 十七轮 | `webui` 内置技能内容、本机 `~/.zcode/skills/markflow-typeset/SKILL.md` |

**注意 D13 的教训**：「结论必须核对结构而不是只看有没有样式」——只看「有圆形」会得出错误结论，必须量到 DOM 结构与元素尺寸。

### 已核实**不属于**遗留（不要再当缺口排查）

排查过程中有 5 条曾被记为待办，现已确认落地或无需改动，列在这里省掉接手方的重复劳动：

| 曾被记为缺口 | 实际状态 | 证据 |
|-------------|---------|------|
| 站点公网 Base URL 未配置，渲染前无法把 `/uploads/` 变绝对直链 | **已实现** | `RenderConfig.siteBaseUrl` + `MarkFlowRenderService.absoluteImageUrls`（2026-09-08 Spike 定案：上游对相对路径原样透传） |
| 公众号摘要（digest）无法自动填充 | **已实现** | `ScheduledArticleTools.java:140` 用 `renderResult.summary()` 兜底 digest |
| `default_style` 存在但未接线 | **已接线** | `SkillPromptAssembler` 规则 8：账号 `defaultStyle` 非空注入一行 |
| `/api/skills/preview` 全员可触发出网 | **已限流** | `SkillController.PREVIEW_MAX_PER_MINUTE=30`/用户/分钟 |
| `delegate_research` 子智能体无独立限流 | **已覆盖，无需改动** | 会话级 `MAX_EDITOR_DELEGATE_CALLS=3` + `/chat` 端点 20 次/用户/分钟，两处叠加足够 |

---

## 三、仍然未实现 / 未根治

> 先看 **I8**（P0，编辑器链路无硬超时）——它与其他项不同：不是「体验差一点」，而是会静默吃掉并发额度直到进程重启。

### 3.1 上游依赖：本仓库改不动，需要对外提诉求

#### U1（P0）agent4j 2.3.3 既无读超时也无取消接口
- **症状**：阶段超时（300 秒）到点后，工作线程只能中断并放弃等待；底层 `OkHttpClient` 是 `OpenAIChatModel` 内 `final` 自建的，HTTP 连接与服务端 SSE 流仍然开着。若流随后恢复，它可能与**下一次**运行（可能是重试后的新会话）同时产出。
- **影响**：停滞后仍占网关名额（正是 D2 级联的来源之一）；重试后若两个流都恢复，可能重复执行付费副作用（生图、图片编辑）。
- **根因**：`LLMModel.create` 仅 4/5 参且无超时项；`AgentSessionResult` / `AgentClientSession` 无 `cancel()`/`close()`；全项目不存在 readTimeout。
- **当前规避**：运行级并发闸门把总在飞请求压到 4，配合 300 秒止损，让「卡死」不再无限累积；停滞重试加「零工具调用」前置条件，避免在已有副作用时重试。
- **根治方案**：向上游索取 `cancel()`/`close()` 与可配置读超时；本仓库侧接入点已预留（`AgentInvoker.attempt` 的失败分支与 `StageTimeout`）。
- **还需一并确认的两件事**（来自现场采集，见 5.1）：①核实 agent4j 的流式解析是否**只读 `delta.content` 而忽略 `reasoning_content`**，并用「带工具调用的流式响应」验证工具调用返回体的形状（若确有此问题，会让带推理的模型出现「吐出 0–2 字符后无输出」的假停滞）；②在读层补 readTimeout / 心跳 / 兜底 `onFailure`，并让 `runSubAgent` 的**内层超时与外层超时在报错里可区分**。
- **涉及文件**：`schedule/AgentInvoker.java`、`schedule/StageTimeout.java`、`agent/AgentFactory.java`、`ai/DelegateTools.java`

#### U2（P2）temperature / maxTokens 只存不用
- **症状**：设置页与 agent 覆盖项的这两个字段保存成功但不生效。
- **根因**：agent4j 2.3.3 的 `LLMModel.create` 只接受 4 个参数（provider/baseUrl/modelName/apiKey），没有温度与上限入口。
- **当前规避**：UI 已显式标注「待 agent4j 支持后接线，当前仅保存不生效」（三处：`SettingsView.vue` 的模型档案与 LLM 配置、`AgentsView.vue` 的 agent 覆盖，另有引擎说明条）。
- **根治方案**：上游支持参数透传后，在 `AgentFactory.createModel` 接线（该处已有注释与 WARN 日志指向此点）。
- **涉及文件**：`agent/AgentFactory.java`、`agent/LlmProfile.java`、`agent/AgentDefinition.java`、`webui/src/views/SettingsView.vue`、`webui/src/views/AgentsView.vue`

#### U3（P1）上游网关并发上限 max=6
- **症状**：同时超过 6 个在飞 LLM 请求即被拒：`HTTP 429 concurrent limit exceeded: running=7 max=6`。
- **影响**：这是 D1 事故的**物理根因**。本仓库的闸门只是「不让自己的进程打满它」，上限本身没变。
- **当前规避**：`app.llm.max-in-flight` 默认 4（留 2 个余量给编辑器会话与其他调用方）；429 有界退避（1s/2s/4s，共 4 次尝试）。
- **根治方案**：向网关提额，或申请独立配额；提额后可同步抬高 `LLM_MAX_IN_FLIGHT`。
- **涉及文件**：`resources/application.yaml`、`common/InFlightGate.java`

### 3.2 本仓库未实现 / 未根治

#### I1（P1）多实例部署下并发门禁不完整
- **症状**：`InFlightGate` 是**进程内**信号量；Quartz 以 `isClustered=true` 多实例部署时，N 个实例各自放行 4 个，合计可能远超网关上限。`taskLocks` 同样只是 JVM 本地锁，且孤儿运行回收器（reaper）不走该锁。
- **根因**：没有跨实例的共享配额/租约设施。
- **当前规避**：单实例部署完全有效；「RUNNING 中 ID 最小者为唯一属主」的复核逻辑（`findEarliestRunning`）已保证多实例不会同时插出两个运行，但**并发额度**仍不共享。任务级并发门禁已不依赖 `taskLocks`。
- **根治方案**：把额度改为 Redis 信号量/租约（或数据库行级租约），`taskLocks` 一并上移。
- **涉及文件**：`common/InFlightGate.java`、`schedule/TaskExecutionService.java`、`schedule/TaskRunMapper.java`

#### I2（P1）孤儿运行的判定是纯时间阈值，不是「属主是否还活着」
- **症状**：进程被杀后 `task_run` 留在 RUNNING，只能等超过 `STALE_RUN_HOURS`（当前 3 小时）才被启动自愈中止。单次运行的最坏耗时估算式是 `stageTimeout(300s) × 19 ≈ 1.6h`，所以 3 小时是安全但保守的值——**故障后最长要等 3 小时**才能在历史里看到终态。
- **根因**：`task_run` 没有实例标识或心跳列，无法判断「属主实例是否还活着」，只能按时间猜。
- **当前规避**：阈值从 12h 降到 3h；`abortStale` 是定点 CAS UPDATE（`WHERE ID=? AND STATUS='RUNNING'`），不会覆盖属主已正常写入的终态。
- **根治方案**：给 `task_run` 增加 `instance_id`（取 Quartz `instanceId`）或心跳列，配合 `QRTZ_SCHEDULER_STATE` 的存活实例判定属主；可把自愈窗口从小时级压到分钟级，同时仍满足集群安全。单实例部署也可直接把 `STALE_RUN_HOURS` 调更小。
- **涉及文件**：`schedule/TaskRun.java`、`schedule/TaskRunMapper.java`、`schedule/StaleRunPolicy.java`

#### I3（P2）MarkFlow 渲染服务的 `preview` 字段未使用，KaTeX 上游配套 CSS 丢失
- **症状**：渲染产物入库后，数学公式丢失 KaTeX 的 `aria-hidden` 标记与上游配套 `<style>`（实测 `<style>` 会被 Jsoup 清洗剥离），公众号侧公式需依赖上游内联样式兜底。
- **当前规避**：渲染响应里的 `html`/`meta`/`theme` 都已使用，只是 `preview` 未用；`sanitizeHtml` 是黑名单语义，未知标签保留。
- **根治方案**：按上游语义决定是否用 `preview`（需要先确认它与 `html` 的差异与适用场景），或对公式类节点做定向样式内联。
- **涉及文件**：`skill/MarkFlowRenderService.java`

#### I4（P2）没有「用留存 Markdown 重新渲染覆盖正文」的产品入口
- **现状**：`article.content_markdown` 已随文章落库（可重排的事实依据），但只有智能体交付前的自动渲染会写它；用户无法在编辑器里发起「拿源文重新渲染一遍」。也就是说：**改了排版模板/主题色后，已有 MARKFLOW 文章不能批量重排**，只能重新跑一次智能体。
- **根治方案**：新增一个「重新渲染」接口/按钮，读 `content_markdown` → 调 `MarkFlowRenderService` → 写回 `content_html` 并生成新 revision。
- **涉及文件**：`article/ArticleService.java`、`skill/MarkFlowRenderService.java`、`webui` 编辑器

#### I5（P2）同维度绑定多枚 IMAGE 技能会注入两段冲突风格（有意不改）
- **现状**：WRITING 等多注入维度也是并列注入；IMAGE 目前同样并列，只新增 WARN 点名冲突技能。仅 LAYOUT 单注入（因为它直接决定排版引擎，双引擎指令必然打架）。
- **为什么不自动收紧**：若改成「首个生效、其余丢弃」，用户会**看不见地**丢掉一半绑定，比冲突本身更糟。
- **根治方案**：属产品决策——应在绑定处（账号/任务的技能选择 UI）校验并提示，而不是在组装层静默丢弃。测试 `SkillPromptAssemblerTest.sameDimensionSkillsOtherThanLayoutAreAllInjected` 固化了当前行为。
- **涉及文件**：`skill/SkillPromptAssembler.java`、`webui` 技能选择组件

#### I6（P2）COORDINATOR 子智能体的局部日志与计数
- **现状 a**：子智能体的日志仍由 `DelegateTools` 事后整体追加（`ProgressListener.toolCallsOnly` 有意只上报计数、忽略日志行，避免与 `runSubAgent` 的追加重复）。**子智能体停滞时，它自己的局部日志仍会丢**——但停滞原因会以引导文本写进日志，所以不是完全不可诊断。
- **现状 b**：COORDINATOR 会话的返回值不含子 agent 的工具计数（已由工作区累计计数器缓解，历史里能看到总数）。
- **根治方案**：给子智能体也开一条带日志的实时上报通道，并在追加时做去重（例如按会话 id 分流）。
- **涉及文件**：`ai/DelegateTools.java`、`schedule/CoordinatorExecutor.java`、`schedule/AgentRunner.java`

#### I7（P1，环境阻塞）本机 TUN 代理导致 COORDINATOR 拿不到一次全绿实跑
- **症状**：本机出网经 TUN 代理，SSE 流在首轮即停滞（`netstat` 有 6 条到 443 的 ESTABLISHED 但无数据、无 outbound 报错、agent4j Future 不完成），而网关 `GET /v1/models` 无鉴权 0.05 秒返回 401——说明 HTTP 面正常，问题在流式链路。
- **影响**：COORDINATOR 的整轮预算（`MAX_TOTAL_TOOL_CALLS=120`）与委托链路只有**离线报文级回归测试**，没有一次真实全绿实跑。
- **根治方案**：换直连网络（或给 JVM 配正确的 ProxySelector 出网路径）后跑一次完整 COORDINATOR 任务，把工具预算与委托计数的实机证据补上。
- **涉及文件**：无（环境问题）；验收脚本可用 `target/live_*.py` 的模式（本轮已删除临时脚本）

#### I8（P0）编辑器 AI 对话链路没有硬超时：停滞即永久占名额且不留痕迹
- **症状**：编辑器聊天走的是 `CompletableFuture.runAsync(() -> execute(session, instruction))`，`executeWithAgentSession` 里直接 `result.execute(); result.get();`——**完全不经过 `StageTimeout`**。一旦上游 SSE 停滞，这个线程就永久 WAITING，而它持有的 `InFlightGate` 名额不会被释放（名额在 `finishSession` 才释放，而那个方法永远不会被调用）。因为它不是「运行」，所以 `TASK_RUN` 里没有任何记录，用户只会看到「AI 一直不回」。
- **证据**：16:14 的活体线程栈里 `ForkJoinPool.commonPool-worker-3` 已存活 **4730 秒（约 79 分钟，约 14:55 起）**，栈顶为 `LLMResult.get(LLMResult.java:77)` → `AgentClientSession.executeCommand` → `ArticleAiService.executeWithAgentSession` → `ArticleAiService.execute`（持锁中）；该 dump 里共有 2 条线程阻塞在 `LLMResult.get`，即 2 个网关名额被白占。
- **影响**：并发额度默认只有 4，一个卡死的编辑器会话就永久吃掉 1/4；叠加定时链路后对可用额度的影响是持续的、要重启进程才消失。
- **根治方案**：把 `StageTimeout.await` 复用到编辑器会话（可直接包住 `result.execute()/result.get()`）；超时时释放名额、并通过 SSE 的 `error` 事件告诉浏览器「会话已超时」，而不是静默挂住。注意编辑器是**交互式**的，超时值应与定时链路不同（用户在场，等 300 秒太短），建议单独一个配置项而不是复用 `stage-timeout-seconds`。
- **涉及文件**：`ai/ArticleAiService.java`（`execute` / `executeWithAgentSession` / `finishSession`）、`schedule/StageTimeout.java`

#### I9（P2）`browse_webpage` 抓取失败（403/404）被计为工具失败
- **症状**：run#36 的执行日志里出现 `工具失败：browse_webpage - 网页请求失败（HTTP 403）` 与 `（HTTP 404）`。这是与 SSE 停滞无关的独立缺陷，但会让运行落到「成功（有警告）」，噪声掩盖真正的失败。
- **待查**：是否缺 User-Agent 导致被目标站反爬；404 是否应视为「可跳过」而不计失败。
- **涉及文件**：`ai/ArticleMediaTools`/相关网页抓取工具、`schedule/TaskWorkspace.addToolFailures`

#### I10（P2）RUNNING 期间 `TASK_RUN` 无任何进度可读
- **症状**：`TASK_RUN` 的 `MODE`、`EXECUTION_LOG`、`TOOL_CALL_COUNT` 都只在收尾时落库，所以一条 **RUNNING 的行永远是 `MODE=NULL`、日志为空、计数 0**。运行期间想知道「它跑到哪一步了」只能抓线程栈（jstack）。
- **说明**：本轮的 `ProgressListener` 让**失败时**日志能留下（D3），但没有改变「运行中不落库」这件事——工作区是内存态。
- **根治方案**：给运行中的进度做周期落库（例如每 N 秒或每 N 行刷一次，或单独一张 `task_run_progress` 表），或提供一个查询运行中工作区的诊断端点。
- **涉及文件**：`schedule/TaskExecutionService.java`、`schedule/TaskRunMapper.java`、`schedule/TaskWorkspace.java`

### 3.3 只影响开发环境、不影响产品行为的已知事项

- **模型上下架**：排查期间 `agnes-3.0-flash` 曾从网关消失（`model_not_found`），临时改用 `deepseek-v4-flash-0731`，随后用户换回并经直连探测确认恢复 200。**请在设置页确认当前 `LLM_PROFILE`(ID 1) 的 `MODEL_NAME` 是预期模型**。
- **模型间歇性不发工具调用**：#18/#24 出现过「整轮不发工具调用导致未提交草稿」的失败，属模型侧间歇行为，非代码缺陷。
- **开发库留有验收数据**：`TASK_RUN` 中 #34（自愈中止）、#35/#36/#38（停滞）、#39（闸门拒绝）是本次实机验收的真实证据，**未清理**，请勿误认为线上脏数据；#37 是已删除的 UI 夹具。

### 3.4 对并行会话「剩余工作」的逐条核销

并行会话（2026-09-11 16:14–17:30，只读）抓了活体线程栈，产出 `docs/dev/agent-session-timeout-handoff.md`，其实体内容**已合并进本份第五、六节**。它「第五节 剩余工作」的 7 项与本份的逐条对应关系：

| 它的剩余工作 | 本份状态 |
|---|---|
| 1. 编辑器链路加硬超时护栏 | **仍开放** → 即 I8（本份置为最高优先） |
| 2. 让停滞可恢复（阶段级有界重试） | **定时链路已完成**（停滞重试 1 次 + 每次重建会话，第②期）；编辑器链路随 I8 一并做；**根治仍待 U1** |
| 3. 闸门策略产品决策 | **其前提需更正**，见下 |
| 4. 工具失败不得记 SUCCESS | **已修** → D5（本轮第③期） |
| 5. 时间戳混源 | **已修** → D4（本轮第③期） |
| 6. agent4j readTimeout / cancel | **仍开放** → U1 |
| 7. `browse_webpage` 403/404 | **仍开放** → I9 |

> 第 4、5 项在它采集时记为「未见 / 待处理」，是因为当时运行的是补丁前的实例；本份给出的证据（run#34 的时间戳、`SUCCESS_WITH_WARNINGS` 终态）晚于它，**以本份为准**。

**一处曾经的错误推断**：它初稿依据 run#39 在 `LLM_MAX_IN_FLIGHT=1` 下 5 秒失败，推断「一次 COORDINATOR 运行天然要占 ≥2 个名额（主编 + 子智能体）」。这在当前设计下**不成立**——闸门只在两处取名额：`TaskExecutionService.java:136`（**运行级，一次运行占一个**）与 `ArticleAiService.java:220`（编辑器会话占一个）；`AgentRunner.runWithLimit` 本身不取名额，嵌套的子智能体在同一个运行内不重复占位。run#39 失败只是因为先到的 run#38 占住了唯一名额。**因此默认 4 是够的，不必按「一次 COORDINATOR 占 2」上调**；上限也不能超过网关的 6（见 U3）。该会话已于 17:40 自行更正此点。

---

## 四、工程约束与验收方式（接手人须知）

### 4.1 构建与测试
- **必须用** `./.mvn/mvn-local.sh -o <goal>` 离线构建；`./mvnw` 会因 `JAVA_HOME` 未定义而失败，`mvn` 不在 PATH 上。`mvn-local.sh` 默认用 `/c/Program Files/Java/jdk-17`。
- 测试库（`wechat-article-test`）由**跨进程文件锁串行化**，**禁止并行跑测试**；Quartz 表由测试初始化器统一重建。
- 抽象基类要规避 smart-mybatis 的 MapperScan 扫描；需要真实 DB 的用例用 `@DirtiesContext` 隔离。
- **不要用 `grep` 直接搜应用日志里的中文**：ANSI 码会让 grep 判定为二进制，且 Git Bash 会弄坏中文编码——用 `grep -a` + ASCII 片段。
- 当前门禁（2026-09-11）：`mvn-local.sh -o test` 全量 **205 例 0 失败**（实施前 178）；`webui npm run build` 零报错（仅既有 >500 kB chunk 体积警告）。

### 4.2 验收原则（本项目已多次踩坑，务必沿用）
1. **测量类结论必须用真实令牌 + 真实数据库 + 真实浏览器**，不接受「看代码推断」。
2. **测量必须能被反证**：改回原状后测量要能复现缺陷（如 `DESCRIPTION_MAX_LENGTH` 反例、`data-render-id` 的 `git stash` 反例），否则不能排除「恰好都是绿的」。
3. **结论要核对结构，不能只看有没有样式**（D13 的教训）：量 DOM 结构、元素尺寸、computed style，必要时用 CDP。
4. **内联样式类改动用「声明计数 diff」**来量化，而不是截图目测。

### 4.3 当前提交状态
- **全部改动已提交并推送**：commit `5f88ca5`（2026-09-11），推送到 `huanyu-a/wechat-article-bot` 的 `main`。该提交含 63 个文件（22 新增 / 41 修改）——除本轮四期修复外，也把此前多轮未提交的改动（ToolCallArgumentGuard、StaleRunPolicy/Reaper、DelegateTools 整轮预算、articles/agents 资源可往返等）一并纳入，因为它们是同一批未提交的工作树状态。
- 本轮修复涉及后端 12 个文件、前端 4 个文件、测试 8 个文件，新增 6 个类与 6 个测试类。
- 新增主类：`common/InFlightGate`、`schedule/StageTimeoutException`、`schedule/AgentRunner.ProgressListener`、`schedule/TaskExecutionService.RunCompletion`、`schedule/TaskRunMapper.formatTimestamp`、`asset/AssetService.truncateDescription`。
- 新增测试：`common/InFlightGateTest`、`schedule/AgentInvokerTest`、`schedule/TaskRunCompletionTest`、`schedule/TaskRunMapperTimestampTest`、`asset/AssetDescriptionPersistenceTests`，并扩充 `PipelineExecutorTest` / `StageTimeoutTest` / `StaleRunPolicyTest` / `AssetServicePathTests`。
- 验收用临时脚本与夹具已清理（`target/live_*.py`、UI 夹具 run#37 已删）。
- 目标仓库是 **public** 的 fork，提交前做过密钥扫描：`.env`、`data/`、`logs/` 均在 `.gitignore` 中；手写文档里出现过的网关密钥值片段已在提交前从记录中剔除（只保留解密手法，不含密钥值）。

---

## 五、现场证据与根因（吸收自并行会话 `sess_457cd5ec` 的只读采集）

> 本节把 [agent-session-timeout-handoff.md](agent-session-timeout-handoff.md)（2026-09-11 16:14–17:30 只读采集）的实体内容合并进来，避免两份文档并存。**原始文件仍保留**——该会话 17:42 还在编辑它，且它自己的「当前状态」类结论以其标注时刻为准。

### 5.1 根因（已确立）

1. 网关 `https://nexus.bx9y.com.cn/v1/chat/completions`（`PROVIDER=OPENAI_COMPATIBLE`，模型 `agnes-3.0-flash`）**间歇性地不发流结束信号**：既无 `[DONE]` 也无 `finish_reason`。
2. agent4j 2.3.3 **既无读超时也无 cancel/close**：`LLMResult.get()` 阻塞在 `future.get()`，只有流正常结束才会完成；流被掐断且没有 `onFailure` 回调时**永久挂住**。
3. 网关并发上限 max=6，被放弃的会话其 SSE 流仍然开着、继续占名额 → 「卡一个 → 少一个名额 → 下一个更易卡」的级联（`#26` 原文 `HTTP 429 concurrent limit exceeded: running=7 max=6`）。
4. `StageTimeout` 在 1800 秒时**按设计工作**，但它只是把永久 RUNNING 变成 FAILED，**不是治因**。

**已排除**（省得重复怀疑）：`OpenAIChatModel` 对 `reasoning_content` 的处理正确；TLS 证书正常（有效期 2026-08-11 → 2026-11-09，早期的证书告警是 Python 侧本地 CA 问题）；与请求体大小无关（小请求同样命中）；`AgentProtocols.RESEARCH` 的提示词与工具集正常——**调研员是受害者，不是缺陷源**。

### 5.2 运行 #33（用户报告的那次）完整结论

| 项 | 值 |
|---|---|
| 任务 | task#2「每日科技早报」，MODE=PIPELINE，MANUAL |
| STARTED / FINISHED | 2026-09-11 15:47:38.125 → 16:17:38.565（**正好 1800 秒**） |
| STATUS | FAILED |
| TOOL_CALL_COUNT | **26** |
| EXECUTION_LOG | `【调研】启动智能体：调研员`（只有 1 行） |
| MESSAGE | 智能体会话超时（1800 秒未结束） |

**一处需要更正的早期判断**：`#33` **不是**卡在调研员的第一次调用。它已完成约 26 轮工具调用，是**后面某一次**调用停滞耗尽了阶段预算。对比 `#29`（首个调用就卡、0 工具调用、日志 39 字符）——**同一根因、两种命中点**，说明停滞与「第几次调用」无关，是概率性的。

「26 次工具调用 / 1 行日志」的割裂原因：`PipelineExecutor.runStage` 只在 `runWithLimit` **正常返回后**才合并阶段日志，抛异常时整批丢失，而计数走实时回调所以幸存。该缺口已由 `ProgressListener` 补上（见 D3），**#33 跑在补丁前的实例上**。

### 5.3 活体线程栈（唯一一次抓到正在卡住的状态）

`jstack 24700` @ 16:14:08，PID 24700，**455 行 / 43 线程**：

```
"agent-stage调研" #121 daemon，已存活 1589.90s，WAITING (parking)
  CompletableFuture.get → LLMResult.get(LLMResult.java:77)            ← 卡点
  AgentClientSession.executeCommand(AgentClientSession.java:165)
  AgentSessionResult.execute(AgentSessionResult.java:66)
  StageTimeout.lambda$await$0(StageTimeout.java:49)

"ForkJoinPool.commonPool-worker-5" #117（护栏线程，存活 1590s）
  StageTimeout.await(StageTimeout.java:58) → AgentInvoker.awaitStage(:125) → run(:106)
  PipelineExecutor.runStage(:154) → execute(:70)     ← 第①步「调研」
  TaskExecutionService.executeRun(:143)

"ForkJoinPool.commonPool-worker-3" #67，存活 4730.53s（≈79 分钟，约 14:55 起）
  LLMResult.get(LLMResult.java:77)
  AgentClientSession.executeCommand(AgentClientSession.java:165)
  ArticleAiService.executeWithAgentSession(:281) → execute(:218)   ← 编辑器会话，无 StageTimeout 护栏，持锁中
```

判读结论：阻塞在 `LLMResult.get` 的线程数 = **2**，即当时有 2 个网关名额被停滞会话白占；整份 dump 里 **OkHttp 读线程 = 0**，说明流已无人读取、也不会有人唤醒 future。

**闸门时效性提醒**（解释 `#33` 为什么看起来「没有闸门」）：`InFlightGate` 源码 15:26:22、class 编译 16:10:04，而当时在跑的 JVM 约 14:33 启动（`elapsed≈6072s`）、dump 里 `InFlightGate` 出现 **0 次**——**#33 跑在闸门之前**。又因 agent4j 没有 cancel，**重启进程是回收被占名额最快的办法**。

### 5.4 实施后的新症状：止损变快，但可恢复性没有

`#35/#36/#38` 三次 SINGLE 运行都在 **300 秒准时失败**：

| run | 时段 | 工具调用 | 卡点（新增诊断字段） |
|---|---|---|---|
| #35 | 16:32:59 → 16:37:59 | 1 | 最后活动为「收到模型输出 **2** 字符」，距今 0 秒 |
| #36 | 16:58:17 → 17:03:18 | 1 | 最后活动为「收到模型输出 **2** 字符」，距今 67 秒 |
| #38 | 17:08:44 → 17:13:44 | 1 | 最后活动为「收到模型输出 **0** 字符」，距今 0 秒 |

读法：模型吐出 0–2 个字符后流就断了——正是「SSE 流响应中途停滞」的形态，与 5.3 的线程栈指向同一根因。区别只是现在 5 分钟止损、而不是白等 30 分钟。**这三条与 #33 一样，最终都以「整次运行失败」收场。**

### 5.5 现场状态（17:27，供判断时效）

应用已重启（JVM PID 41352），**无任何线程阻塞在 `LLMResult.get`**、无 RUNNING 运行；网关探测 4/4 正常（0.64–2.40 秒）。即系统当时空闲且健康——**问题属间歇性发作，不是常态故障**。这也意味着任何「修好了」的结论都需要多轮采样才能支撑。

---

## 六、可复用的现场诊断手法

### 6.1 抓活体线程栈（唯一能看到「卡在哪一次调用」的手段）

```bash
"/c/Program Files/Java/jdk-17/bin/jps.exe" -l -v     # 找真实 PID（不要用 Maven wrapper 的 PID）
"/c/Program Files/Java/jdk-17/bin/jstack.exe" <pid> > dump.txt
grep -a "agent-stage\|LLMResult.get\|StageTimeout.await\|InFlightGate" dump.txt
```

判读要点：

- 停在 `LLMResult.get(LLMResult.java:77)` = agent4j 在等模型响应；
- **若同时没有任何 OkHttp 读线程**，说明流已被掐断且无人回调，future 不会完成（`#33` 即此形态）；
- `grep -c "LLMResult.get" dump.txt` 的计数 = 当前被停滞会话占掉的 LLM 名额数；
- **必须在真正卡住时抓**——重启过的 JVM 里现场就没了。

### 6.2 判断「运行中的实例是哪个版本」

`jps -l -v` 拿真实 PID 与启动时长，再把 `target/classes` 里新类的 mtime 与实例启动时刻比对（或直接在线程栈里 grep 新类名）。并行会话就是靠这个发现 `#33` 跑在闸门之前：源码 15:26 / class 16:10，但实例 14:33 启动、栈里 `InFlightGate` 出现 0 次。

### 6.3 探测网关是否在劣化（间歇性，需多轮采样）

- 解密 `LLM_PROFILE.API_KEY_ENCRYPTED`：`key = SHA-256(APP_SECRET_KEY)`，AES/GCM/NoPadding，Base64 解码后前 12 字节为 IV。
- 用 JDK `HttpClient` 发流式请求，**按是否收到 `[DONE]` 判定**；重复 8 次可量化停滞率。
- 实测样本：某轮 `ok=7 hung=1`（其中一次 >40 秒）、延迟 0.76–19.4 秒；10 分钟后同脚本 8/8 正常（0.7–1.9 秒）；再测 `ok=4 hung=0`（0.6–2.4 秒）。
- 也可用 `curl -s -N` 探测，但要留意 Python/本机 CA 造成的假告警（证书本身正常）。
- ⚠️ **不要把解密出来的 API Key 写进任何文档或提交**——这段只在临时文件里用，用完删掉。

### 6.4 直查开发库

```bash
docker exec momo-mysql-dev mysql -uroot -p"$PW" -D wechat-article -e "SELECT ... FROM TASK_RUN WHERE ID IN (...) \G"
```

`$PW` 取自 `.env` 的 `MYSQL_PASSWORD`；表名全大写；会话时区是 UTC；等号两边不要有空格（否则 shell 会把路径粘进库名）。注意 `LLM_PROFILE` 的密钥列名是 **`API_KEY_ENCRYPTED`**（排查时曾用错列名）。

### 6.5 观测缺口（与 I10 同源）

`TASK_RUN` 的 `MODE`、`EXECUTION_LOG`、`TOOL_CALL_COUNT` 都只在收尾时落库，所以 **RUNNING 行永远是 `MODE=NULL`、日志为空、计数 0**——运行期间想知道进度只能抓线程栈。

---

## 七、相关文档地图

| 文档 | 内容 |
|------|------|
| `docs/dev/agent-session-timeout-plan.md` | 429 级联停滞的根因与四期修复方案；文末「八、实施记录」含本轮逐期落地物、与初稿的偏差（闸门加在运行而非会话，附字节码证据）、实机验收证据表与遗留 5 条 |
| `docs/dev/agent-session-timeout-handoff.md` | 并行会话（只读）的现场证据原文；**实体内容已合并进本份第五、六节**（含 run#33 活体线程栈、编辑器链路第二处泄漏、jstack 判读要点与网关停滞率探测手法）。原文件保留是因为该会话仍在编辑它 |
| `.zcode/handoff/main-live-evidence.json` | 并行会话的旁路 JSON 记录——它**有意没有覆盖** `latest.json`（默认视为本会话所有） |
| `docs/dev/skills-agent-plan.md` | Skills/Agent 体系方案（1413 行）；「实施记录」按轮次（十二～十九轮等）自检，第三节各条遗留的原始表述在此 |
| `docs/dev/skills-agent-plan-review.md` | 方案评审报告（只读产物）；含 MarkFlow 落地补充点与方案未覆盖项，多数已在后续轮次落地（见第二节「不属于遗留」表） |
| `.zcode/handoff/latest.json` | Handoff 协议的结构化记录。**注意：该文件为并行 ZCode 会话共享的单文件**，若你在另一个会话工作，请先读再写，避免覆盖对方的记录 |

---

## 八、建议的下一步（按性价比排序）

1. **I8（编辑器链路加硬超时）**（P0，最该先做，成本也最低）：复用 `StageTimeout` 就能堵住「停滞即永久占名额且不留痕」这条唯一没有护栏的路径。现场栈已经抓到 79 分钟的活例，不修的话并发额度会被静默侵蚀。
2. **I2 + I1 一起做**（P1）：给 `task_run` 加 `instance_id`/心跳列，顺带把并发额度换成 Redis 全局租约——两项共用同一套「实例身份」设施，一起做最省。完成后自愈窗口从 3 小时降到分钟级，多实例部署才真正安全。
3. **I4（重新渲染入口）**（P2，产品价值高）：源文已经在库里，接一个接口 + 编辑器按钮就能让「换主题批量重排」成为可能，成本低、可见收益大。
4. **I7（补一次 COORDINATOR 全绿实跑）**（P1，取决于网络）：把工具预算与委托计数的实机证据补上，这是当前唯一「只有离线回归、没有线上证据」的链路。
5. **U1 / U3 对外提诉求**：向 agent4j 索取 `cancel()`/读超时，向网关申请提高并发上限。这两项不做，本仓库只能在应用层「绕」，无法根治。
6. **I9 / I10 / I3 / I5 / I6**（P2）：按实际使用中的痛点决定优先级；I5 是产品决策，需要先定「用户绑了冲突技能时期望什么行为」。
7. **继续采样，把「间歇性」变成可量化的结论**（支撑前 5 项）：在网关抖动窗口反复跑停滞率探测（6.3），累积 `hung` 样本；并趁某次运行真卡住时**同步抓一次调研员线程的真实阻塞帧**——目前只有 `#33` 一次现场（5.3），单样本不足以支撑「停滞与第几次调用无关」以外的更多推断。诊断手法的具体命令见 6.1–6.4。
