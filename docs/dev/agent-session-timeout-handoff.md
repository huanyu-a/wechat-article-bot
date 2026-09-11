# 交接文档：定时任务智能体会话停滞——活体现场证据与实施进展

- 采集时间：2026-09-11 **16:14–17:30**
- 配套方案：[agent-session-timeout-plan.md](agent-session-timeout-plan.md)（根因分析、四期划分、风险）
- 采集方式：**全程只读**（未改源码、未重启服务、未动数据库）
- 一句话：根因（网关 SSE 间歇性停滞 + agent4j 无读超时/无取消）未消除；另一会话已落地闸门、300 秒超时与卡点诊断，但**编辑器链路仍无护栏**，且停滞目前仍以「整次运行失败」收场。

---

## 一、用户报告的运行 #33 的完整结论

「每日科技早报」（task#2，PIPELINE，MANUAL）：

| 项 | 值 |
|---|---|
| STARTED_AT | 2026-09-11 15:47:38.125 |
| FINISHED_AT | 2026-09-11 16:17:38.565（**正好 1800 秒**） |
| STATUS / MODE | FAILED / PIPELINE |
| TOOL_CALL_COUNT | **26** |
| EXECUTION_LOG | `【调研】启动智能体：调研员`（只有 1 行） |
| MESSAGE | 智能体会话超时（1800 秒未结束） |

### 1.1 活体线程栈（16:14:08，JVM PID 24700，本轮唯一一次抓到正在卡住的状态）

```
"agent-stage调研" #121  daemon  已存活 1589.90s
  java.lang.Thread.State: WAITING (parking)
    at java.util.concurrent.CompletableFuture.get(CompletableFuture.java:2072)
    at ink.icoding.llm.core.model.LLMResult.get(LLMResult.java:77)      ← 卡点
    at ink.icoding.llm.agent.AgentClientSession.executeCommand(AgentClientSession.java:165)
    at ink.icoding.llm.agent.AgentSessionResult.execute(AgentSessionResult.java:66)
    at ink.icoding.wechat.article.schedule.StageTimeout.lambda$await$0(StageTimeout.java:49)

"ForkJoinPool.commonPool-worker-5" #117  已存活 1590.29s
  java.lang.Thread.State: TIMED_WAITING (parking)
    at ink.icoding.wechat.article.schedule.StageTimeout.await(StageTimeout.java:58)   ← 护栏计时中
    at ink.icoding.wechat.article.schedule.AgentInvoker.awaitStage(AgentInvoker.java:125)
    at ink.icoding.wechat.article.schedule.AgentInvoker.run(AgentInvoker.java:106)
    at ink.icoding.wechat.article.schedule.PipelineExecutor.runStage(PipelineExecutor.java:154)
    at ink.icoding.wechat.article.schedule.PipelineExecutor.execute(PipelineExecutor.java:70)  ← 第①步「调研」
    at ink.icoding.wechat.article.schedule.TaskExecutionService.executeRun(TaskExecutionService.java:143)
    at ink.icoding.wechat.article.schedule.TaskExecutionService.lambda$start$0(TaskExecutionService.java:51)
```

三点可直接引用的事实：

1. 工作线程停在 **`LLMResult.get(LLMResult.java:77)`**，即 agent4j 在等模型的下一次响应，与该库「流被掐断却不回调 onFailure 时 future 永不完成」的行为一致；
2. 整份 dump（455 行 / 43 线程）里 **没有任何 OkHttp 读线程**——流已经没人读了，也不会再有人把 future 唤醒；
3. 编排线程停在 `StageTimeout.await`，即只剩护栏在计时，30 分钟后必然判失败。

### 1.2 更正一处此前的口头结论

我在 16:14 的报告里说 `#33`「卡在调研员的**第一次** LLM 调用」——**这是不准确的**。该运行最终记录 **26 次工具调用**（`TaskWorkspace.addToolCalls` 由 `onToolCalls` 实时累加，语义已核实），说明调研员已经完成了约 26 轮工具调用，**是后面某一次调用停滞耗尽了阶段预算**。

准确的表述是：`#29` 是首个调用就卡（日志 39 字符、0 工具调用），`#33` 是跑到 26 次工具调用后才卡——**同一根因，两种命中点**。这也说明停滞与「第几次调用」无关，是概率性的。

### 1.3 为什么 `#33` 的日志只有一行、看不出跑了 26 次工具

`PipelineExecutor.runStage` 只在 `runner.runWithLimit(...)` **正常返回后**才 `executionLog.addAll(splitLines(outcome.executionLog()))`；抛异常时这些行整体丢失，而 `TOOL_CALL_COUNT` 因为走的是 `onToolCalls` 实时回调而幸存。于是历史里出现「26 次工具调用 / 1 行日志」的割裂。

> 该缺口已被另一会话的 `AgentRunner.ProgressListener` + `TaskWorkspace.progressListener()` 补上（`ArticleAiService.java:527` 已改为实时上报）。**#33 跑在补丁之前的实例上，所以仍表现为割裂。**

---

## 二、同一个 dump 里发现的第二个泄漏：编辑器链路

```
"ForkJoinPool.commonPool-worker-3" #67  已存活 4730.53s（约 78.8 分钟，约 14:55 起）
    at ink.icoding.llm.core.model.LLMResult.get(LLMResult.java:77)
    at ink.icoding.llm.agent.AgentClientSession.executeCommand(AgentClientSession.java:165)
    at ink.icoding.llm.agent.AgentSessionResult.execute(AgentSessionResult.java:66)
    at ink.icoding.wechat.article.ai.ArticleAiService.executeWithAgentSession(ArticleAiService.java:281)
    at ink.icoding.wechat.article.ai.ArticleAiService.execute(ArticleAiService.java:218)   ← 持锁中
    at ink.icoding.wechat.article.ai.ArticleAiService.lambda$chat$4(ArticleAiService.java:178)
```

这是**编辑器 AI 对话**（不是定时任务），从 14:55 起挂了近 79 分钟仍未结束。当时全库共 **2 条线程阻塞在 `LLMResult.get`**，即 2 个网关名额被停滞会话白占。

关键差别：编辑器链路是 `CompletableFuture.runAsync(() -> execute(session, instruction))`，**完全不经过 `StageTimeout`**，所以它没有超时、不会自愈、也不会在运行历史里留下任何痕迹。14:55 那条直到进程重启才消失。

> 目前 `InFlightGate` 已接入编辑器链路（`ArticleAiService.java:220`），但**闸门只管「同时开几个」，不回收已经卡住的**。编辑器链路仍缺硬超时护栏——这是当前最大的剩余缺口。

---

## 三、已实施进展（另一会话，16:00–17:20）

| 方案期 | 状态 | 证据 |
|---|---|---|
| 第①期 并发闸门 | **已实现并接入两条链路** | `common/InFlightGate.java` 源码 15:26:22 / class 16:10:04；默认 `DEFAULT_LIMIT=4`、`DEFAULT_ACQUIRE_TIMEOUT_SECONDS=60`；接入点 `TaskExecutionService.java:136`、`ArticleAiService.java:220` |
| 第②期 超时下调 | **已生效** | `application.yaml:43` `stage-timeout-seconds: ${STAGE_TIMEOUT_SECONDS:300}`（原 1800） |
| 第②期 耦合项同步 | **已同步** | `application.yaml:49` `stale-run-hours: ${STALE_RUN_HOURS:3}`（原 12）——符合「超时与孤儿阈值绑在同一估算式上，改一个必须改另一个」的要求 |
| 第④期 卡点诊断 | **已生效** | `#35/#36/#38` 的 message 带「卡点：最后活动为「收到模型输出 N 字符」，距今 X 秒；已调用工具 N 次」 |
| 第④期 实时日志 | **已实现** | `AgentRunner.ProgressListener`、`TaskWorkspace.progressListener()`、`ArticleAiService.java:527`、`AgentInvoker.java:75/80` |
| 第③期(数据正确性) | **未见** | 时间戳混源、工具失败记 SUCCESS 两项仍待处理（见第五节） |
| 第④期 agent4j 上游 | **未动** | 仍是唯一根治路径 |

### 3.1 闸门默认值与 `#39` 的冲突

`#39`（COORDINATOR，17:08:44）**5 秒即失败**：

```
LLM 并发额度等待超时（上限 1，已等待 5 秒）：同一时刻进行中的智能体运行占满了名额。请稍后重试，或调大 app...
```

上限 1 / 等待 5 秒与 `application.yaml` 的默认 4 / 60 不符，判断是**测试用的环境变量覆盖**（`LLM_MAX_IN_FLIGHT=1` 等）。留档目的：说明闸门本身工作正常。

> **更正（2026-09-11 17:40，经核实）**：本节初稿曾推断「一次 COORDINATOR 运行天然要占 ≥2 个名额（主编 + 子智能体），故 `limit=1` 下起不来」。**该推断错误**。闸门只在两处取名额——`TaskExecutionService.java:136`（**运行级**，一次运行占一个）与 `ArticleAiService.java:220`（编辑器会话占一个）；`AgentRunner`/`AgentInvoker` 中**没有任何 `inFlightGate` 引用**，嵌套的子智能体在同一运行内不重复占位。因此 `#39` 失败只是因为先到的 `#38` 占住了唯一名额（`limit=1`）。**默认 4 是够用的，不必按「一次 COORDINATOR 占 2」上调**，上限也不得超过网关的 6。

---

## 四、实施后的新症状：停滞仍在，只是被更快止损

`#35/#36/#38` 三次 SINGLE 运行都在 **300 秒准时失败**：

| run | 时段 | 工具调用 | 卡点（新诊断字段） |
|---|---|---|---|
| 35 | 16:32:59 → 16:37:59 | 1 | 最后活动为「收到模型输出 **2** 字符」，距今 0 秒 |
| 36 | 16:58:17 → 17:03:18 | 1 | 最后活动为「收到模型输出 **2** 字符」，距今 67 秒 |
| 38 | 17:08:44 → 17:13:44 | 1 | 最后活动为「收到模型输出 **0** 字符」，距今 0 秒 |

**读法**：模型已经吐出 0–2 个字符，然后流就断了——这正是「SSE 流在响应中途停滞」的形态，与第一节的线程栈指向同一个根因。区别只是现在 5 分钟就止损，而不是白等 30 分钟。

结论：**止损变快了，可恢复性还没有**。这三条运行与 `#33` 一样，都以「整次运行失败」收场。

### 4.1 `#36` 顺带暴露一个独立的工具缺陷

`#36` 的执行日志里有：

```
工具失败：browse_webpage - 网页请求失败（HTTP 403）
工具失败：browse_webpage - 网页请求失败（HTTP 404）
```

`browse_webpage` 抓取失败（403/404）。这是与 SSE 停滞无关的独立问题，建议单列排查（是否缺 User-Agent、是否被目标站反爬、404 是否应视为可跳过而计为失败）。

---

## 五、剩余工作（按优先级）

1. **给编辑器链路加硬超时护栏**（当前最大缺口）。它是唯一一条**停滞即永久占名额且不留痕迹**的路径；`#33` 期间的 worker-3 就是活例。可直接复用 `StageTimeout`。
2. **让停滞可恢复**：现在 300 秒止损后整次运行失败。建议阶段级有界重试（同一阶段最多 1 次，且必须**重建 `AgentClient`**，不要复用挂着坏连接的实例）；对 COORDINATOR 的子智能体停滞，`DelegateTools.runSubAgent` 已把 `IllegalStateException` 转成引导文本让主编收尾，这条路径也要一并复核。
3. **闸门策略需产品决策**：`max-in-flight=4` 在并发手工触发时会给出「并发额度等待超时」（`#39` 即此形态）。需要在「排队更久 / 直接拒绝 / 调大上限」之间定一个。**上限不能超过网关的 6。**（注：初稿认为 4 对 COORDINATOR 偏紧，该判断已按第三节的更正撤回——运行级取牌一次只占 1 个。）
4. **工具失败不得记 SUCCESS**（第③期）：`#30` 有 `generate_image` 因 `ASSET.DESCRIPTION varchar(500)` 超长失败却记 SUCCESS；`#36/#38` 有 `browse_webpage` 失败。现在已有 `TaskWorkspace.addToolFailures` 可依托。
5. **时间戳混源**（第③期）：`abortStale` 用 DB `NOW(6)`（`TaskRunMapper.java:71`）而 `started_at`/正常收尾用 JVM 本地时间（`TaskRunMapper.java:24`），本机 MySQL 会话时区是 UTC（实测 `NOW()`=06:57 时本机 14:57）。`#33` 记录里仍可见 `#34` 的 `STARTED_AT 2026-09-10 16:26:05`——那是被 16:28 重启自愈中止的遗留行。
6. **agent4j 上游**：`readTimeout` / `cancel`——唯一能根治「停滞即泄漏名额」的路径。
7. **`browse_webpage` 403/404**（见 4.1）。

---

## 六、可复用的验证手法

**抓活体线程栈**（唯一能看到「卡在哪一次调用」的手段，因为 RUNNING 行不落库进度）：

```bash
"/c/Program Files/Java/jdk-17/bin/jps.exe" -l          # 找 WechatArticleBotApplication 的 pid
"/c/Program Files/Java/jdk-17/bin/jstack.exe" <pid> > dump.txt
grep -a "agent-stage\|LLMResult.get\|StageTimeout.await\|InFlightGate" dump.txt
```

判读要点：
- 停在 `LLMResult.get(LLMResult.java:77)` = agent4j 在等模型响应；
- **若同时没有任何 OkHttp 读线程**，说明流已被掐断且无人回调，future 不会完成（`#33` 即此形态）；
- `grep -c "LLMResult.get"` 的计数 = 当前被停滞会话占掉的 LLM 名额数。

**探测网关是否在劣化**：解密 `LLM_PROFILE.API_KEY_ENCRYPTED`（AES/GCM/NoPadding，密钥 = SHA-256(APP_SECRET_KEY)，Base64 解码后前 12 字节为 IV），用 JDK `HttpClient` 发流式请求，按是否收到 `[DONE]` 判定。重复 8 次可量化停滞率（本轮测得 1/8；17:27 复测 4/4 正常，0.6–2.4 秒——**属间歇性**）。

**观测缺口提醒**：`TASK_RUN` 的 `MODE` 与 `EXECUTION_LOG` 只在收尾时落库，所以 **RUNNING 行永远是 `MODE=NULL`、日志为空、`TOOL_CALL_COUNT=0`**。运行期间想看进度只能抓线程栈。

---

## 七、当前状态（17:27）

- 应用已重启（JVM PID 41352），**没有任何线程阻塞在 `LLMResult.get`**，无 RUNNING 运行；
- 网关探测 4/4 正常（0.64–2.40 秒）；
- 即：**系统此刻是空闲且健康的，问题属间歇性发作**，不是常态故障。

---

## 八、交接声明

- 本文与配套方案均为**只读采集**产出：未修改任何源码、未重启服务、未改动数据库；
- 采集期间解密出的 API Key 临时文件已删除；
- 采集时段内另一会话正在同一仓库编译/迭代，本文所有「当前状态」均标注了时刻，请以时刻为准判断时效。
