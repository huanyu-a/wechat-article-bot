# 定时任务四类缺陷修复方案（run#129 证据链）

- 状态：**方案已实施完毕，全量闸门通过**（439 例 / 0 失败 / 0 错误，`BUILD SUCCESS`；
  基线 426 → 439，净增 13 例）
- 证据源：`target/scratch/run129.log`（主证据，107 行）、`target/run-20260916.log`（429 误判对照）、
  agent4j 2.3.3 字节码（`target/agent4j/oai-full.txt`、`session.txt`）
- 一句话结论：**run#129 的三段失败（配图 400、审核 7/7 失败、工具计数对不上）由四个互相独立的缺陷造成，
  全部已定位到字节码/日志级别的机制，并各自配了「拆掉修复即变红」的反证。**

## 一、缺陷清单与修复对照

| # | 缺陷 | 真实症状 | 根因（机制级） | 修复 | 反证 |
|---|---|---|---|---|---|
| 1 | 回放消息被网关拒收 | 配图阶段 `HTTP 400: messages[10]: missing field \`id\`` | agent4j 只在网关推送 id 分片时写 `ToolCallEntry.callId`，缺 id 时 `appendToolCall(null,…)` 不兜底 | `ToolCallArgumentGuard`（回放归一化：arguments / content / id） | `ToolCallArgumentsReplayTest` 5 例，含 2 条版本锚定 canary |
| 2 | 429 被误判成模型级错误 | 一次**无谓的换档案**（多花 246~691 秒） | 对整条错误消息做子串匹配，`chatcmpl-205af2300c034118b5a1f4015ac57f9f` 的十六进制体含 `401` | `AgentInvoker.stripOpaqueIds`（匹配前剥掉 request_id / 十六进制串） | `requestIdHexDoesNotCollideWithNumericErrorCodes` |
| 3 | `submit_review` 参数解析失败 | 审核阶段 7 次尝试 7 次失败 → 300 秒硬超时，审核无结论 | 模型把 `List<String>` 稳定地传成对象；`ToolParam.fromJsonString` 一抛就没有第二次机会 | `ToolParamRepair`（按声明字段驱动做形状归一化） | `ToolParamRepairTest` 7 例，4 例在拆掉修复后报出**与生产逐字相同**的 `Failed to parse tool param JSON` |
| 4 | 工具计数/在飞集合键碰撞 | 调研阶段 CALL=39 / DONE=43 / FAIL=9 对不上 | 缺 id 时键塌缩成 `name+"\n"+""`，同一工具的所有调用共用一个键 | `AgentInvoker.callKey`（缺 id 时按 descriptor **实例身份**分配序号） | `countsEverySameNameCallEvenWhenGatewayOmitsTheId`、`pairsInFlightKeysForCallsWithoutId` |

## 二、逐项方案

### 缺陷 1：回放守卫（`ToolCallArgumentGuard`）

**为什么三类缺陷必须一起修**：它们同源——agent4j 把流式产出**原样回放**，而网关逐条校验历史消息的形状。
`normalizeReplay` = `normalizeArguments` + `normalizeContent` + `normalizeToolCallIds`，返回修正条数。

两个修复点：

1. **进入 `ask` 时**归一化本轮下发的历史（修「上一轮遗留」的消息）；
2. **本轮工具执行前**（包装 `ToolExecutor`）归一化 `LLMResult.getAppendedMessages()`（修「本轮刚产生」的消息）。

第 2 点能修到 assistant 的 `tool_calls[].id`，靠的是一条**对象共享**事实：
`handleToolCallsAndContinue` 把**同一个** `Message` 实例同时放进「本轮消息列表」与 `appendedMessages`
（偏移 106-109 与 115-118 都是 `aload 8`），因此就地改 `appendedMessages` 里的对象就等于改了即将序列化的那份。

**曾经走过并被证伪的设计（记录下来，避免重走）**：最初实现了一个 `RepairingMessageList`——
包住传给 `delegate.ask(...)` 的 `List`，指望在请求构造时迭代消息那一刻补齐。
字节码显示 `OpenAIChatModel.lambda$ask$1` 偏移 3-8 先执行 `new ArrayList<>(messages)`，
**包装的 List 直接被丢弃**；红测也证实 `"tool_call_id":null` 仍然写到了线上。该实现已删除。

~~**已知残留（上游限制，本项目改不动）**：与 assistant 的 id **成对**出现的 tool 消息 `tool_call_id`，
在本轮内**够不着**：~~

> **2026-09-18 更正：残留已消除，结论从「改不动」改为「换个接缝就能改」。**
> 下面三条字节码事实**依然成立**（轮内确实够不着），被推翻的是由它推出的「本项目改不动」——
> 当时只枚举了 `ToolExecutor` / `ResultHandler` 两个钩子，漏掉了报文发出前的 okhttp 接缝。
>
> - ~~它由 `handleToolCallsAndContinue` 偏移 254 的 `Message.fromTool().withToolResult(entry.callId, …)` 构造，
>   读的是 `ToolCallEntry.callId` 而**不是**传给回调的 `ToolDescriptor`（`getCallId` 在 `OpenAIChatModel`
>   字节码里**从未出现**，只有 L290/L419 的 `setCallId`）；~~
>   → **仍然成立**。补充：偏移 259 的 `getfield ToolCallEntry.callId` 直接取字段，
>   偏移 181 的 `setCallId` 只是一份**无人再读**的副本，所以改 `ToolDescriptor` 无效。
> - ~~构造发生在偏移 231 的 `toolExecutor.execute` **返回之后**，而下一轮请求在偏移 293 的 `executeAgentLoop`
>   里**立刻**发出——两者之间不存在任何回调钩子（`ToolExecutor` 已返回；`ResultHandler` 只有
>   `onTool`/`onToolError` 且都在执行前触发）；~~
>   → **仍然成立**（`onTool` 确实只带 `ToolDescriptor` + `ToolStatus`，且在偏移 80 即执行前触发）。
> - ~~`AgentClientSession.executeCommand` 也只在 `get()` 之后才 `history.addAll(appendedMessages)`（偏移 99-110）。~~
>   → **仍然成立**。
>
> **被证伪的是前提本身**：「网关不校验 tool 侧字段」是错的。渲染令牌配好之后，定时任务连续两次
> 整轮 400，报的正是 tool 侧：
> ```
> messages[20]: missing field `tool_call_id` at line 233 column 3   （run#7，13 次工具调用）
> messages[27]: missing field `tool_call_id` at line 331 column 3   （run#9，20 次工具调用）
> ```
> 而且这条 400 是**终局**的：`AgentInvoker` 的重试前置条件是
> `retryable = attempt.toolCalls() == 0`（`AgentInvoker.java:199`），已经跑过工具就不再重试；
> 400 既非 permanent 也非 transient，没有任何路径兜住它 → 任务直接 FAILED。
> 所以「下一轮会收敛」在真实故障里**不成立**——任务根本活不到下一轮。
>
> **修复**：新增 `ReplayWireNormalizer`（okhttp `Interceptor`），在报文发出前直接改 JSON，
> 补齐 assistant 的 `tool_calls[].id` 与 tool 消息的 `tool_call_id`。
> 挂载点仍是 `AgentFactory.createModel`（`AgentFactory.java:320-329`）——`executeAgentLoop` 偏移 77-80
> 每次请求都重新 `getfield client`，所以反射换掉 `private final OkHttpClient` 立即生效
> （`private final` 实例字段可被 `setAccessible(true)` 改写，已用探针实测）。
> 轮内归一化**保留**（它先修历史，两层幂等且互补）。canary 已从 `isNull()` 翻转为
> 「非 null 且与 assistant 侧 id 配对一致」。

~~该 tool 消息会在**下一次**进入 `ask` 时被补上，因此多工具调用轮次与后续轮次都能收敛，
只有「一轮里最后一次工具调用」的 tool 消息可能带着 null 发出。~~
→ **更正**：该假设只在「网关容忍 null」时才成立；真机证明不容忍，任务在发出那一刻就死了。

~~**已在 `ToolCallArgumentsReplayTest` 里用 canary 钉住现状**（`toolMessageIdIn(...)` 断言为 `isNull()`），
若上游哪天补了兜底，该断言会翻转。~~
→ **已翻转**为正向断言（`isNotNull()` 且与 assistant 侧相等），并新增
`wireNormalizerFillsToolCallIdForLastToolCallOfRound` 与 `wireNormalizerAttachesItselfToTheOkHttpClient`。

~~> **关于 mock 契约的一处自我纠正**：该测试早期版本额外要求 `role:"tool"` 消息带非 null `tool_call_id`，
> 但那条规则**在任何真实证据里都不存在**——全量检索 150 个生产日志/产物，`tool_call_id` 只作为
> agent4j 字节码里的常量出现，**从未**出现在网关报错里（真实的 400 一律是 assistant 侧
> `missing field \`id\``）。把没观测到的规则写进 mock，等于用一个自己发明的契约把实现判红。
> 现已把 `findToolCallMissingId` 收窄到只校验 assistant 侧，并在注释里写明理由。~~

> **2026-09-18 二次更正：上面这段「自我纠正」本身才是错的，已回滚。**
> 当时的检索范围（150 个生产日志/产物）里确实没有 tool 侧 400，但那是**采样偏差**：
> 那些运行大多在更早的环节就失败了，根本没跑到「多工具调用 + 长历史」的阶段。
> 渲染令牌修好、任务能真正跑完长链路之后，tool 侧 400 立刻现形（run#7 / run#9）。
> **教训**：「在现有日志里没观测到」只能推出「尚未观测到」，推不出「不存在」——
> 尤其当现有样本的失败点系统性地早于待验证环节时。
> 现已把 `findToolCallMissingId` **恢复为两侧都查**（assistant 的 `id` + tool 的 `tool_call_id`），
> 并在注释里写明真机证据。

### 缺陷 2：`stripOpaqueIds`（`AgentInvoker`）

三个模式（匹配前依次剥掉，只动「明确是标识符」的部分）：

- `REQUEST_ID`：`request_id: xxx` / `request_id=xxx` / `"request_id":"xxx"`；
- `CHAT_COMPLETION_ID`：`chatcmpl-` / `resp_` / `req_` / `call_` / `trace_` / `span_` 开头的网关 id；
- `LONG_HEX`：长度 ≥12 的连续十六进制串。

阈值取 12 的理由：3 位关键字在 12 位窗口内出现的概率已降到 0.006% 量级，而真正的错误码不会以
12 位以上裸十六进制出现（HTTP 状态码 3 位、`401008` 这类只有 6 位且带引号与字段名）。

**对照样本（同一次真实运行，错误体逐字相同，只有 request_id 不同）**：

| 样本 | request_id | 修复前行为 | 修复后 |
|---|---|---|---|
| `run-20260916.log` L603 | `chatcmpl-205af2300c034118b5a1f4015ac57f9f` | 含子串 `401` → **误判**模型档案不可用，白换一次 | 正确判为 429 限流 |
| 同文件 L322 | `chatcmpl-1a425c40e6c547ecad0fb5271c8ca7be` | 不含关键字 → 正确地走瞬时重试 | 不变 |

两者都是 `code:"rate_limit_rpm_exceeded"`——**账号级**限流，换档案无效。
碰撞率估算：单个 3 位码约 0.73%，`401`~`404` 合计约 2.9%，再加 `500`~`504` 约 6.4%。

**同类问题此前已有记录但没除根**：`docs/dev/scheduled-task-failure-attribution.md` 记 run#117 的 402
只是「误打误撞」被拦下（错误体的 `code:"401008"` 含 `401`）——那次补的是关键字，
**病根（对整条消息做子串匹配）没除**，这次一并修掉。

### 缺陷 3：`ToolParamRepair`

按 `descriptor.getParamClass()` 的**真实字段声明**驱动（不硬编码字段名），三步：

1. **解嵌套**：某声明字段的值是对象、且该对象里出现了**其它声明字段名** → 把内层字段提到顶层（不覆盖已有）；
2. **收形状**：声明为 `List<String>` 的字段拿到对象/单值 → 收成文本数组（保信息，不丢内容）；
3. **纠字面量**：声明为 `Boolean` 的字段拿到 `"true"`/`"false"` 文本 → 还原为布尔。

只在形状确实与声明不符时才改写（合法输入**逐字原样返回**）；无法解析或 `paramClass` 为 null 时原样透传
（归一化自己绝不能成为新的失败点）。

**实测畸形形状（run#129 审核阶段，7/7 全失败）**：

| 形状 | 日志位置 | 处理 |
|---|---|---|
| `{"issues":{"suggestions":[…],"summary":"…","passed":false,"issues":[…]}}` | L85/L87/L89 | 解嵌套 |
| `{"issues":{"1":"草稿为空，无法审核：…"}}` | L93 | 收数组（数字键只取文本） |
| `{"issues":{"<某句问题描述>":""},"passed":false,"suggestions":{…}}` | L97 | 收数组（值为空时取键，保住原文） |
| `{"passed":"false",…}` | — | 纠字面量 |

> **为什么光靠提示词消不掉**：`AgentProtocols.REVIEW` 里已经写明了这个错误原文和「必须是数组字面量」的要求，
> 仍然 7/7 失败——必须在解析侧容错。

### 缺陷 4：`callKey`（`AgentInvoker`）

键函数：有 `callId` 时用 `name + "\n" + callId`；缺 id 时退回到**实例身份**，
用 `IdentityHashMap` + `synchronized` 分配 `name + "\n#anonymous" + 序号`。

- **为什么用实例身份而不是 `System.identityHashCode`**：后者可能碰撞；且 `ToolDescriptor`
  未覆写 `equals/hashCode`（字节码确认），agent4j 对**每次工具调用都新建一个 descriptor**
  （偏移 169-194），同一实例贯穿该次调用的 `PREPARING`/`CALLING`/`COMPLETED` 或 `onToolError`
  （`ToolExecutor.defaultExecute` 三处都传同一个 `descriptor`）。
- **为什么必须 `synchronized`**：同一 descriptor 理论上只由一条 SSE 线程回调，但缺 id 时
  **不同**调用会并发走到这里，check-then-put 必须原子，否则两次拿到同一个序号。
- **两层后果**：① 执行日志与 `TOOL_CALL_COUNT` 少计，预算护栏在缺 id 时形同虚设；
  ② `inFlightTools` 键碰撞会让**先结束的调用把仍在运行的同名调用的键摘掉**，
  无进展检测误以为没有工具在跑——而它正是用来保护「主编等待子智能体」不被误杀的。

## 三、验证

| 测试 | 例数 | 覆盖 |
|---|---|---|
| `ToolCallArgumentsReplayTest` | 5 | 缺陷 1（含 2 条上游版本 canary + 1 条残留 canary） |
| `ToolParamRepairTest` | 7 | 缺陷 3（4 条真实畸形形状 + 合法透传 + 不可修复透传 + 幂等） |
| `AgentInvokerTest` | +4 | 缺陷 2（碰撞 + 真实错误码仍切换）、缺陷 4（计数 + 成对） |

**红→绿反证**（拆掉修复，确认对应用例确实变红）：

| 修复 | 拆法 | 结果 |
|---|---|---|
| `ToolParamRepair` | 首行改为 `return paramJson` | 7 例中 **4 例失败**，报错文本与生产逐字相同：`Failed to parse tool param JSON: …` |
| `stripOpaqueIds` | `matchesAny` 里去掉 `stripOpaqueIds(...)` 调用 | `requestIdHexDoesNotCollideWithNumericErrorCodes` 失败（`Expecting false but was true`） |
| `callKey` | 首行改为 `return name + "\n" + safeCallId(...)` | 缺陷 4 的 **2 例全失败**（3 次调用塌缩成 1 次） |

**全量闸门**：`439 例 / 0 失败 / 0 错误`（`BUILD SUCCESS`）。

> 工具链备注（本机环境，与本次改动无关）：
> - Mockito 的 inline mock maker 无法自附加，需 `-DargLine=-Djdk.attach.allowAttachSelf=true`
>   才能跑 `AgentInvokerTest`。
> - 集成测试需要一个可连的 MySQL 测试库（`spring.datasource.url` = `ENV.MYSQL_TEST_URL`）。
>   本机 `127.0.0.1:3306` 原本无监听，13 个 DB 集成测试类会以
>   `Communications link failure / Connection refused` 报 63 个错误——**这是环境问题，不是代码缺陷**。
>   本轮用 `podman run -d --name watb-test-mysql -e MYSQL_ROOT_PASSWORD=… -p 3306:3306 mysql:8.0`
>   起了一个**临时测试库**（注意 Podman 只把端口绑到 IPv6 回环，测试 URL 要用 `localhost` 而非
>   `127.0.0.1`），跑完即删。
> - `Copy-Item` 会保留源文件时间戳，Maven 增量编译会因此**跳过重编译**——做拆装反证时必须确认
>   `target/classes` 里的字节码真的变了（`javap` 看调用点），否则会得出错误结论。本轮就踩过一次：
>   一度以为 `stripOpaqueIds` 没生效，实际是跑的还是被拆过的旧 class。

## 四、待办 / 未决

> **2026-09-17 更新（第二轮收尾）**：下列 4 项已逐项核对真实状态并处理，
> 其中 **1 项是过期条目（早已修好）**、3 项已落地。保留原始条目并标注结论，便于对照。

- **缺陷 1 的 tool 侧残留**：需上游 agent4j 修复（本会话已证明无可达钩子）。已记入 `docs/dev/upstream-issues.md`。
- **全量闸门**：`.mvn/mvn-local.sh -o test`（本机经 `target/scratch/mvn.ps1` 调用）。
  本轮基线 **460 例 / 0 失败 / 0 错误**（上一轮基线 439 例；`+7` 来自 `WebUiArtifactCheckTest`，
  `+11` 来自 `ScheduledCreatorProfileRebindRunnerTest`，`+3` 来自 `QuartzMisfirePolicyTests`）。
- **未纳入本次改动（独立问题）** —— 逐项结论：

  1. **`builtin_scheduled_creator` 重新绑定到 `deepseek-flash`** → ✅ **已做**。
     `AgentSeeder.seeds()` 改默认值（新装生效）＋新增一次性迁移
     `ScheduledCreatorProfileRebindRunner`（`@Order(32)`，改绑存量库，只在绑定==旧默认值时才动，
     幂等、不覆盖用户选择）。理由见 `scheduled-task-reliability-round.md` §八.1。
  2. **5 处同名实体字段冲突** → ✅ **早已修复，原条目过期**。
     修复在 commit `94d9a94`（`Asset.java` / `ArticleRevision.java` 对齐声明），
     并已由 `EntityColumnDeclarationConsistencyTest` 钉住（2 例，本轮复跑通过）。
     `sourceUrl` 是**向真实列对齐**（2000→1000），不是收窄。
  3. **`frontend-maven-plugin` 静态产物陈旧** → ✅ **已做（选方案 B，方案 A 经证据否决）**。
     新增启动自检 `WebUiArtifactCheck`（`@Order(35)`）：产物缺失/比 `webui/` 源码旧时打 WARN
     并给出重建命令，只告警、不阻塞启动。
     **方案 A（把 `build-webui` 提前到 `compile` 阶段）已否决**：该插件**不在本地 Maven 仓库**
     （`com\github\eirslett` 目录不存在），任何在 `mvn -o test` / `spring-boot:run` 期间执行的绑定
     都会因离线解析插件失败而**打破项目自己的离线闸门**。
  4. **09:00 自然触发的 cron 观测** → ✅ **已由确定性测试取代**。
     `QuartzMisfirePolicyTests` 用真实 Quartz（RAMJobStore）钉死：① 落库 trigger 的
     `misfireInstruction == DO_NOTHING`；② `DO_NOTHING` 下错过即跳过；③ 反证 `SMART_POLICY`
     会**补跑**。顺带更正了 `scheduled-task-reliability-round.md` §八.4 的根因笔误
     （原文称「未写 misfire 指令、走 SMART_POLICY≡DO_NOTHING」，两处都不成立：
     代码**显式**配了 DO_NOTHING，且 SMART_POLICY 对 cron 是 `FIRE_ONCE_NOW`）。
