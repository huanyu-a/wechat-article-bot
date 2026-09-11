# 方案：定时任务「智能体会话超时」根因与修复

- 状态：**已实施（2026-09-11，交接会话 `sess_8e9a49cf` 完成第①②③④期）**，实施记录与偏差见文末「八、实施记录」
- ⚠️ **配套现场证据另见 [agent-session-timeout-handoff.md](agent-session-timeout-handoff.md)**（16:14–17:30 只读采集）：活体线程栈、`#33` 完整结论、实施后的新症状，以及**仍未解决的缺口——编辑器链路（`ArticleAiService.chat`）没有硬超时护栏，停滞时会永久占住 `InFlightGate` 名额**（名额在 `finishSession` 释放，而它只在 `execute` 走到末尾时才被调用）
- 交接对象：会话 `sess_8e9a49cf-d9e1-4fa6-88d7-c0118ab2558b`（正在进行多线程加固，已改 `AgentInvoker` / `StageTimeout` / `ToolCallArgumentGuard` / `TaskExecutionService`）
- 排查时间：2026-09-11 14:30–15:00
- 结论一句话：**不是调研员这个智能体有缺陷，是上游网关并发上限（max=6）被打满后的连锁停滞；`StageTimeout` 只是把死锁变成了明确的 FAILED，并没有治因。**

---

## 一、现象

2026-09-11 手工触发的 4 个定时任务，3 失败 1 成功：

| run | task | 模式 | 结果 | 工具调用 | 耗时 | message |
|----|------|------|------|---------|------|---------|
| 29 | #2 | PIPELINE | FAILED | 0 | 1800s | 智能体会话超时（1800 秒未结束） |
| 30 | #4 | SINGLE | **SUCCESS** | 21 | 272s | （正常产出文章） |
| 31 | #5 | COORDINATOR | FAILED | 0 | 1800s | 智能体会话超时（1800 秒未结束） |
| 32 | #6 | COORDINATOR | FAILED | 0 | 1800s | 智能体会话超时（1800 秒未结束） |

`TASK_RUN.EXECUTION_LOG` 把卡点写得很明确：

```
#29 (PIPELINE)     【调研】启动智能体：调研员                       ← 到此为止
#31/#32 (COORD)    【协调】启动主编智能体：主编（协调者）
                   【协调】委托 #1：调研员
                   【委托·调研】启动子智能体：调研员                ← 到此为止
```

全部卡在 **调研员的第一次 LLM 调用**，`tool_call_count = 0`，正好 1800 秒（`app.schedule.stage-timeout-seconds`）被 `StageTimeout` 判定超时。

注意 `#31/#32` 的主编**自己那次调用是成功的**（否则不会产生「委托 #1」日志），失败的是子会话。

## 二、根因链

### 2.1 直接证据：网关并发上限 = 6

`TASK_RUN#26`（13:22，同一台机器）的报错原文：

```
java.lang.RuntimeException: SSE connection failed: HTTP 429:
{"error":{"message":"concurrent limit exceeded: running=7 max=6","type":"upstream_error","param":"","code":429}}
```

上游 `nexus.bx9y.com.cn` 的 `agnes-3.0-flash` **最多允许 6 个并发请求**。

### 2.2 独立复现：完全相同的请求会间歇性不返回

用与本应用相同的 JDK 17 / HTTP 栈（`java.net.http.HttpClient`，HTTP/2），对网关发 8 次**内容完全相同**的极简流式请求：

```
#1..5 DONE t=17695/15110/768/7753/19401ms
#6    HUNG/TIMEOUT after 40014ms (HttpTimeoutException，全程无任何响应)
#7..8 DONE t=1031/1747ms
SUMMARY ok=7 hung=1
```

约 10 分钟后再测同一脚本：`ok=8 hung=0`，耗时 0.7–1.9 秒。

两点结论：
1. 网关/网络链路存在**间歇性整段不响应**（不是慢，是首包都不来），命中率量级 1/8；
2. 同一请求耗时在 **0.76s–19.4s** 间剧烈抖动，说明网关处于高负载/排队状态。

同时排除了「请求体太大导致卡死」：71KB + 60 个工具的请求全部正常（2–14s）。提示词内容也排除：`AgentProtocols.RESEARCH` 与调研员工具集（`save_research_notes` + 素材组）均无异常。

### 2.3 为什么卡住后**不会自愈**：agent4j 2.3.3 既无读超时也无取消接口

已核实的库层事实（`javap` agent4j-2.3.3）：

- `LLMModel.create(ModelType, String, String, String[, boolean])` —— **无超时参数**，第 5 参为 debug 开关；
- `OpenAIChatModel` 的 `OkHttpClient client` 是 **final 且构造器内自建**，外部无法注入带 `readTimeout` 的客户端；
- `AgentSessionResult` / `AgentClientSession` 公开方法里**没有 cancel / close**（只有 `then/error/execute/get/complete/completeExceptionally/getHandler/getErrorHandler/addUsage/getUsages/getLastUsage`）；
- `LLMResult.get()` 阻塞在 `CompletableFuture.get()`（`LLMResult.java:77`），future 只在流正常收尾时被 `complete()`；流被掐断且不回调 onFailure 时**永不完成**。

因此 `StageTimeout` 的 `worker.interrupt()` 只是放弃等待：线程被中断，**但 HTTP 连接与服务端 SSE 流仍然开着**。

### 2.4 级联：每个卡住的会话泄漏一个网关名额

```
网关 max=6
  ↓ 某次调用命中 2.2 的停滞
该会话占住名额（无法取消）
  ↓ 并发名额减少
新会话更易撞上限额 → 要么 429，要么同样停滞
  ↓
PIPELINE / COORDINATOR 的首个调用（正是调研员）停滞 → 1800s 超时 → 又泄漏一个
```

`#27` / `#28` 两条记录的消息是人工写的「SSE 流停滞（模型侧无响应）…清场」「SSE 流停滞（网络代理侧无数据），清场」——即上一次事故是**靠手工改库清场**才恢复的，与本节推断一致。

**为什么 SINGLE 成功**：`#30` 在 13:55:29 启动，抢到了空闲名额并一路跑完；`#29/#31/#32` 的首个调用恰好落在名额耗尽/停滞窗口内。三条失败链路「恰好都在调研员处卡住」，是因为调研员在 PIPELINE 里是第 1 阶段、在 COORDINATOR 里是第 1 次委托——**它是两条链路里最早发生的那次 LLM 调用，不是它本身有问题**。

### 2.5 放大因素

- 单次 COORDINATOR 运行至少占用 **2 个**并发名额（主编会话 + 子智能体会话），子智能体再委托还会更多；
- 手工几乎同时点了 4 个任务（13:51/13:55/13:56/13:56），叠加上一条即突破 max=6；
- `AgentFactory.createModel` 每装配一个智能体就 `LLMModel.create` 一次，**每个智能体一个新 OkHttpClient**（独立连接池与线程），无复用。

---

## 三、顺带发现的两个独立缺陷（与本次故障无关，但确凿）

### 3.1 超时/中止的运行时间戳为负

`#25/#27/#28` 出现 `STARTED_AT` 13:03–13:32、`FINISHED_AT` 05:19–05:42（8 小时差）。原因：写入源不一致——

- `started_at` 与正常收尾用 JVM 本地时间（`TaskRunMapper.java:24` `run.setFinishedAt(LocalDateTime.now())`）；
- `abortStale` 用数据库时间（`TaskRunMapper.java:71` `FINISHED_AT = NOW(6)`）；
- 实测本机 MySQL 会话时区为 **UTC**（`SELECT NOW()` → `06:57`，本机 `14:57`）。

两者相差固定的 8 小时，于是被中止的运行出现负数时长。

### 3.2 工具失败但整次运行仍记 SUCCESS

`#30`（SUCCESS）的执行日志里含：

```
工具失败：generate_image - 素材保存失败：... Data truncation: Data too long for column 'DESCRIPTION'
```

`ASSET.DESCRIPTION` 为 `varchar(500)`，生成图描述超长。配图实际没插进去，运行却记为 SUCCESS——**用户看到的是成功，交付物是缺图的**。

---

## 四、目标与非目标

**目标**

1. 并发触发多个定时任务时，不再触碰网关 `max=6` 上限；
2. 单个阶段停滞时，能**快速止损并尝试恢复**，而不是白等 1800 秒；
3. 运行历史能如实反映「卡在第几次调用」与「是否有工具失败」；
4. 时间戳与资产字段不再产生错误数据。

**非目标**

- 不改 agent4j 源码（本仓库不持有该库）；只能包一层或向上游提需求；
- 不引入 Redis（当前单实例部署足够，多实例另议）；
- 不改变「调研员 / 主编」的提示词与工具集——它们没有问题。

---

## 五、分期实施

> 与在制工作重叠提示：第①、②期直接落在 `schedule/AgentInvoker.java`、`schedule/StageTimeout.java`、`schedule/TaskExecutionService.java`、`schedule/StaleRunPolicy.java`，这些文件正被 `sess_8e9a49cf` 修改，**动工前必须确认该会话已停手**，避免互相覆盖。

### 第①期：并发闸门 + 429 退避（止血，最高优先）

1. **新增进程内「在飞 LLM 会话」信号量**（不是现成的 `common/RateLimiter`——它是滑动窗口计数，不做在途并发控制）。建议新建 `common/InFlightGate`（`Semaphore` + 超时 `tryAcquire`），默认上限 **4**（给 max=6 留 2 个余量给编辑器链路）。
2. 在 `AgentInvoker.run(...)`（唯一的会话执行入口，三条链路都走它）`acquire` / `finally release`；取不到额度时按可配置超时排队，超时则**明确失败并说明原因**（不要静默卡住）。
3. **429 视为可重试**：识别 `HTTP 429` / `concurrent limit exceeded`，指数退避重试（建议 3 次，1s/2s/4s），退避后仍失败才让阶段失败。当前 429 直接以 `RuntimeException` 抛出并终止整次运行（见 `#26`）。
4. 可选：把上限做成配置项（`app.llm.max-in-flight`），便于按网关配额调整。

**验收**：并发触发 4 个任务（含 COORDINATOR）时，日志中不再出现 `running > 6`；429 被退避消化；4 次触发全部得到明确终态。

### 第②期：把「停滞」与「失败」分开，并做有界重试

1. `StageTimeout` 超时抛出的 `IllegalStateException` 目前与「模型返回错误」同型，`DelegateTools.runSubAgent` 只捕这一种（`DelegateTools.java` 内 `catch (IllegalStateException)`）。建议引入专用异常（如 `StageTimeoutException extends IllegalStateException`），使调用方能区分：
   - **停滞/超时** → 可重试；
   - **模型/工具错误** → 直接失败。
2. 阶段级**有界重试**：同一阶段停滞最多重试 1 次，且**必须重建 `AgentClient`**（不要复用可能挂着坏连接的实例）。仍停滞才让运行失败。
3. **下调 1800s**：`STAGE_TIMEOUT_SECONDS` 从 1800 降到 **300s** 量级。当前 1800s 意味着每次事故都白等半小时，且期间占着并发闸门。
   - ⚠️ **必须同步调整 `STALE_RUN_HOURS`**：`StaleRunPolicy.worstCaseRunSeconds = stageTimeout × 19`（4 基础阶段 + 5 轮返工 × 3）。1800×19 = 9.5h < 12h 是当前 12h 的由来；降到 300 后最坏仅 1.58h，`stale-run-hours` 应同步降到 ~3–4h，否则孤儿运行会长期占住并发闸门。启动时的 WARN 校验会提示。

**验收**：注入停滞（stub / 限流场景）后，阶段先重试一次；仍失败时在 300s 量级内给出明确错误，日志写明卡在哪一阶段哪一次调用。

### 第③期：数据正确性

1. **时间戳统一来源**：`abortStale`（`TaskRunMapper.java:71`）改为写 JVM 时间，或在 mapper 层统一用 `LocalDateTime.now()`，禁止与 DB `NOW(6)` 混用（本机 MySQL 是 UTC）。
2. **工具失败不得整体记 SUCCESS**：`AgentInvoker` 的 `onToolError` 已在收集失败（`AgentInvoker.java:100`），把失败计数带进 `AgentRunner.Outcome`，由 `TaskExecutionService.executeRun` 决定终态。终态语义需产品决策——建议 `SUCCESS`（无失败）/ `SUCCESS_WITH_WARNINGS`（有工具失败但仍交付）/ `FAILED`（关键工具失败）。
3. **`ASSET.DESCRIPTION` 扩容**至 `varchar(2000)`，或在生图描述写入前截断到 500。

**验收**：新跑运行的 `FINISHED_AT >= STARTED_AT`；含工具失败运行的状态符合约定且执行日志可查；`generate_image` 不再因描述超长失败。

### 第④期：可观测性 + 上游诉求

1. **调用级日志**：当前只有阶段级日志，看不出卡在第几次模型调用。建议在 `AgentInvoker` 里为每次 `ask`（或每次工具轮次）记录「开始/结束 + 耗时 + 累计调用序号」，让 `EXECUTION_LOG` 能直接定位停滞点。
2. **向上游 agent4j 提需求**（唯一根治路径）：
   - `LLMModel.create` 支持 `readTimeout` / `callTimeout`；
   - `AgentSessionResult` 提供 `cancel()` / `close()`。
   否则「停滞即泄漏一个网关名额」无法根治。
3. **网关侧**：申请提高 `max=6`，或为定时链路单独配额（需与网关方沟通）。

---

## 六、风险与边界

| 风险 | 说明 | 缓解 |
|------|------|------|
| 并发闸门降低吞吐 | 定时任务会排队等待 | 排队 + 超时，而非硬拒绝；上限可配 |
| 阶段重试引入重复付费副作用 | 重试可能重复生图（`ToolMutationDeduplicator` 只在单次运行内共享，跨会话不共享） | 重试前判定该阶段是否已产生媒体产物；或只对「调研」等无付费副作用的阶段先开重试 |
| 下调超时改动了既有语义 | `StaleRunPolicy` 的最坏时长估算与孤儿判定阈值耦合 | 第②期明确要求同步改 `STALE_RUN_HOURS` 并跑 `StageTimeoutTest` / `StaleRunRecoveryIntegrationTests` |
| 多实例部署 | 进程内信号量是「每实例」的，Quartz 集群下两实例仍可能合计超过 6 | 与本方案非目标一致；如需全局配额需换 Redis |

---

## 七、交接说明（给接手会话）

1. **本次排查为只读**：未改动任何源码，未跑编译/测试，未重启服务。
2. **不要重复排查**：根因已定位并有独立复现脚本；重点从第①期开始。
3. **先确认在制工作已停手**：`AgentInvoker` / `StageTimeout` / `TaskExecutionService` / `StaleRunPolicy` / `TaskRunMapper` 正被另一会话修改。
4. **本次事故期间临时起过一版后端**：13:05 曾重启 8081（当时为修「打开页面 500」——`target/classes/static` 缺前端产物，已用 `vite build` 补齐）。`TASK_RUN#25` 的「进程终止导致遗留」很可能就是那次杀进程造成的。
5. 复现脚本（临时文件，可重跑）：8 次相同流式请求统计挂死比例，用 JDK 17 + `java.net.http.HttpClient`，约 6 分钟跑完。若需要，可由接手会话固化为一个可重复的诊断脚本放进 `scripts/`。

---

## 八、实施记录（2026-09-11，交接会话 `sess_8e9a49cf`）

四期全部落地，测试 205 项全绿（实施前 178），webui 构建通过；每条修复都做了实机（真实网关 + 真实 MySQL + 真实浏览器）验收。

### 第①期 并发闸门 + 429 重试

| 落地物 | 说明 |
|--------|------|
| `common/InFlightGate`（新增） | 公平信号量 + 有界等待 + 幂等 `Lease`；`app.llm.max-in-flight`（默认 4）/`app.llm.acquire-timeout-seconds`（默认 60） |
| `TaskExecutionService.executeRun` | 运行级取名额（`gateLabel` = 任务/运行号）；取不到即写 `FAILED` + 「LLM 并发额度等待超时（上限 N，已等待 M 秒）」；`finally` 先 `lease.close()` 再落终态 |
| `ArticleAiService.execute` | 编辑器会话同一池取名额；`finishSession` 是成功/失败共同收尾点，统一释放 |
| `AgentInvoker` | 429/`concurrent limit` 指数退避重试（1s/2s/4s，共 4 次尝试），**每次重试重建会话**；`isRateLimited` 沿 cause 链最多 10 层 |

**与方案初稿的偏差（依据字节码，不是推断）**：闸门加在「运行」而不是「会话」上。`javap` 证实 `OpenAIChatModel.handleToolCallsAndContinue` 执行完工具后**递归**进 `executeAgentLoop` 重新开流（偏移 231 执行工具、293 递归），即一次运行任意时刻最多一个在飞请求，委托子智能体期间父会话的流已关闭。按会话逐个取牌会让「父已不占配额」被误算成占配额，且父等子取不到牌时自锁；按运行取牌天然可重入（嵌套会话在同一运行内）。代价：名额在整个运行期持有（含渲染/落库），偏保守。

### 第②期 停滞止损

| 落地物 | 说明 |
|--------|------|
| `StageTimeoutException`（新增） | 继承 `IllegalStateException`，既让既有 catch 点行为不变，又让调用方分得清「停滞（可重试）」与「模型报错（不可重试）」 |
| `StageTimeout` | 超时/被中断抛新异常；`FALLBACK_TIMEOUT_SECONDS = 300`（配置 ≤0 时兜底而不是立刻失败） |
| `AgentInvoker` | 停滞只重试 **1** 次并重建会话；**零工具调用才可重试**（已产生付费副作用就宁可失败） |
| 配置 | `stage-timeout-seconds` 1800→**300**；`stale-run-hours` 12→**3**（同一估算式两端：300s × 19 ≈ 1.6h） |

### 第③期 数据正确性

1. **时间戳同源**：`TaskRunMapper.abortStale` 改为写 JVM 的 `LocalDateTime`（经 `formatTimestamp` 统一为 `yyyy-MM-dd HH:mm:ss.SSSSSS`），不再混用 MySQL `NOW()`（会话时区 UTC）。
2. **工具失败不再记成干净的成功**：`AgentRunner.Outcome` 带 `toolFailures`，`TaskWorkspace` 累计，终态判定抽成 `TaskExecutionService.completion(toolFailures, reply)`——有失败即 `SUCCESS_WITH_WARNINGS`；前端 `TasksView` 映射为「成功（有警告）」+ 琥珀色三角图标。
3. **`ASSET.DESCRIPTION`**：改走方案给出的另一支——**写入前截断**。实测 `DefaultSmartMapperInitializer.syncDatabaseStructure` 只建表/补缺失列，**没有 MODIFY COLUMN**，因此「扩到 2000」会让实体声明与真实列（`varchar(500)`）不一致、1500 字描述照样插不进去；`DESCRIPTION_MAX_LENGTH` 因此定为 500，截断时留 WARN。

### 第④期 可观测性

`AgentInvoker` 记录「最后一次有效事件 + 距今秒数 + 已调用工具次数」，超时时拼进异常消息（如 `卡点：最后活动为「调用工具 browse_webpage」，距今 12 秒；已调用工具 1 次`）。
另**新增进度实时上报**（`AgentRunner.ProgressListener` + `TaskWorkspace.progressListener()`）：工具计数与日志行逐条落工作区。起因是实机验收发现停滞失败的运行 `EXECUTION_LOG` 为空——局部日志随异常丢弃；三条链路（PIPELINE / COORDINATOR / SINGLE）现共用同一监听器。

### 实机验收（真实网关 + 真实库 + 真实浏览器）

| 验收项 | 证据 |
|--------|------|
| 闸门真的挡住 | 以 `LLM_MAX_IN_FLIGHT=1 LLM_ACQUIRE_TIMEOUT_SECONDS=5` 重启后并发触发两个任务：先到的 run#38 持牌 RUNNING，后到的 run#39 在 5 秒后 `FAILED`，message 为「LLM 并发额度等待超时（上限 1，已等待 5 秒）…」；整段日志 `HTTP 429` 出现 **0** 次 |
| 停滞止损 | run#35/#36/#38 均在 **300 秒**（原 1800）终止，message 带卡点（如 `最后活动为「收到模型输出 2 字符」，距今 67 秒；已调用工具 1 次`） |
| 失败也留日志 | run#36（停滞失败）`EXECUTION_LOG` 248 字符 5 行，含 `调用工具：create_plan`、`工具失败：browse_webpage - HTTP 403/404` |
| 时间戳不再为负 | 启动自愈中止遗留 run#34：`FINISHED_AT 2026-09-11 16:28:48` > `STARTED_AT 2026-09-10 16:26:05`（修复前 run#28 是 `13:32 → 05:42` 的负时长） |
| 新终态可见 | 浏览器读取运行记录：`成功（有警告）`；`.warn-text` 命中 `lucide-triangle-alert-icon`，computed color `rgb(190,123,36)` = `--amber` |
| 描述不再丢图 | `AssetDescriptionPersistenceTests` 打真实 `varchar(500)` 列插入 1000 字描述成功；把常量临时改回 2000 时该用例复现 `Data too long for column 'DESCRIPTION'`（反证测量有效） |

### 遗留（未根治）

1. **agent4j 无取消接口**：超时后工作线程只能中断并放弃，若流随后恢复仍可能与下一次运行重叠。根治需上游提供 `cancel()`/`close()` 与读超时。
2. **按会话取牌会自锁**（字节码已证），因此上限定为运行级；若将来要抬高并发，先确认网关 `max=6` 是否上调。
3. **进程内信号量是每实例的**：Quartz 多实例部署时合计仍可能超配额，需换 Redis 之类的全局配额。
4. **COORDINATOR 子智能体的日志**仍由 `DelegateTools` 事后整体追加（`ProgressListener.toolCallsOnly` 有意忽略日志行），避免与 `runSubAgent` 的追加重复；子智能体停滞时其局部日志仍会丢，但停滞原因会以引导文本写进日志。
5. 本次验收在开发库留下 run#34–#39 的真实记录（#34 为自愈中止、#35/#36/#38 为停滞、#37 为已删除的 UI 夹具、#39 为闸门拒绝），未做清理。
