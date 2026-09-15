# 定时任务可靠性 + 效率优化 + 模型档案故障切换 —— 实机验收记录

方案：`.zcode/plans/plan-sess_e09c7784-7835-4597-9cea-4fe764d76dff.md`（六个 Phase 全部落地）
验收日期：2026-09-16
闸门：`.mvn/mvn-local.sh -o test` → **Tests run: 397, Failures: 0, Errors: 0**（基线 329，本轮 +68）

---

## 一、结论速览

| 验收项 | 结果 | 证据位置 |
|---|---|---|
| ① 新列 / 标准档案 / 7 个绑定 / 兜底标记 | **通过** | 本文 §二 |
| ② 强制故障切换反例（新功能的确定性证据） | **通过** | 本文 §三，run#122 |
| ③ 效率量化（COORDINATOR） | **通过** | 本文 §四，run#125 vs run#119 |
| ③ 效率量化（SINGLE） | **通过** | 本文 §五，run#126 vs run#116 |
| ④ 验收过程自身发现并修复 3 个缺陷 | **已修 + 有测试** | 本文 §六 |

**一句话**：故障切换这个新功能已被确定性证明可用（run#122 强制指向不存在的模型名，整轮仍成功）；COORDINATOR 链路比基线快约 15%；SINGLE 链路在修掉一个**本轮自己引入的误杀缺陷**后跑通（run#126，895 秒成功），该缺陷曾让两次运行在正常出字的情况下被判「停滞」中止。

---

## 二、实机① 重启后的落地状态

重启后查库（只读 SQL，见 `docs/dev/scheduled-task-failure-attribution.md` §五）：

**新列**：`LLM_PROFILE.IS_FALLBACK` 已由 smart-mybatis 自动补上（`tinyint(1)`）。

**标准档案**（`LlmProfileSeeder` 幂等创建）：

| ID | NAME | IS_DEFAULT | IS_FALLBACK | ENABLED | MODEL_NAME |
|---|---|---|---|---|---|
| 1 | 默认配置 | true | **true** | true | hy4-preview |
| 4 | deepseek-flash | false | false | true | deepseek-flash |
| 5 | glm-5.3-flash | false | false | true | glm-5.3-flash |

兜底档案 = 默认配置（hy4-preview，免费通道），符合「付费 Tier A 主用 + 免费兜底」的口径。

**7 个内置智能体绑定**（`AGENT_DEFINITION.LLM_PROFILE_ID`），与方案 §2.3 映射表逐条一致：

| ID | CODE | 绑定档案 |
|---|---|---|
| 1 | builtin_editor | 4 = deepseek-flash |
| 2 | builtin_scheduled_creator | 5 = glm-5.3-flash |
| 3 | builtin_researcher | 4 = deepseek-flash |
| 4 | builtin_writer | 5 = glm-5.3-flash |
| 5 | builtin_illustrator | 4 = deepseek-flash |
| 6 | builtin_reviewer | 5 = glm-5.3-flash |
| 7 | builtin_chief | 5 = glm-5.3-flash |

**列宽未收窄**：Phase 6 改的是实体注解，smart-mybatis 的 `MODIFY COLUMN` 分支会把声明同步到真实列。核对 `information_schema` 后确认 `ASSET.SOURCE_URL` 仍是 `varchar(1000)`（改前就是 1000，实体声明从错误的 2000 改为 1000 是**向真实值对齐**，不是收窄），其余 4 组同名字段改后无冲突。

---

## 三、实机② 强制故障切换反例（run#122）

这是新功能唯一的确定性证据，做法按方案第十节：临时建一个指向**不存在模型名**的档案，绑给某个智能体，跑一轮。

- 临时档案：`ZZ-故障切换反例-临时` / `zz-nonexistent-model-xyz`
- 绑定：`builtin_scheduled_creator`（任务 #7）
- 结果：`SUCCESS_WITH_WARNINGS`，产出文章 #61

日志里出现切换行：

```
模型档案不可用（…HTTP 503…model_not_found…），切换：ZZ-故障切换反例-临时/zz-nonexistent-model-xyz → 默认配置/hy4-preview
```

`TASK_RUN.STAGES_SUMMARY`（run#122）：

```json
{"toolCalls":4,"profilesUsed":["ZZ-故障切换反例-临时/zz-nonexistent-model-xyz","默认配置/hy4-preview"],
 "switchedProfile":true,
 "stages":[{"stage":"SCHEDULED_SINGLE","seconds":74.9,"toolCalls":4,
            "profiles":["ZZ-故障切换反例-临时/zz-nonexistent-model-xyz","默认配置/hy4-preview"]}],
 "saved":true}
```

三个字段同时为真才算证明：`profilesUsed` 有两段（主用失败 + 切换后成功）、`switchedProfile=true`、整轮 `SUCCESS` 且 `saved=true`。

**验收后已清理**：临时档案删除、绑定恢复为 5（glm-5.3-flash），已查库确认。

---

## 四、实机③ COORDINATOR 效率量化（run#125 vs 基线 run#119）

| 指标 | 基线 run#119 | 本轮 run#125 | 变化 |
|---|---|---|---|
| 状态 | SUCCESS_WITH_WARNINGS | **SUCCESS** | 不再有阶段中止 |
| 墙钟耗时 | 1801s | **1527s** | **−274s，约 −15%** |
| 工具调用数 | 86 | 112 | +26 |
| 产出 | 文章（含 1 个阶段中止，内容可能不完整） | 文章 #62，HTML 49729 字节，MARKFLOW | 交付完整 |

`STAGES_SUMMARY` 的分阶段明细（run#125）：

| 阶段 | 耗时 | 工具调用 |
|---|---|---|
| DELEGATE_RESEARCH | 196.4s | 57 |
| DELEGATE_WRITING | 480.0s | 65 |
| DELEGATE_ILLUSTRATION | 126.1s | 10 |
| DELEGATE_REVIEW | 198.7s | 82 |
| DELEGATE_ILLUSTRATION（返工轮） | 172.0s | 13 |
| COORDINATE（主编总控） | 1525.1s | 8 |

**口径提醒**：这里省下的 274 秒主要来自模型提速（deepseek-flash 445 tok/s、glm-5.3-flash 153 tok/s，对照 hy4-preview 23.5 tok/s），但**工具调用数反而从 86 涨到 112**，说明调用次数并不是耗时的唯一驱动——单次调用的模型等待时间才是。这轮的工具治理（去重/循环检测）压的是「同参数重复检索」这类空转，对本来就不重复的调研没有减少次数，这是符合预期的。

---

## 五、实机③ SINGLE 链路：一次误杀缺陷 + 修复后的成功样本

任务 #4 本轮触发三次：run#123 / run#124 **失败**，run#126 **成功**。三次的差别不在上游，而在**代码是否包含一处修复**。

| run | 状态 | 耗时 | 工具调用 | 档案 | saved |
|---|---|---|---|---|---|
| 123 | FAILED | 544.4s | 33 | glm-5.3-flash | false |
| 124 | FAILED | 405.6s | 21 | glm-5.3-flash | false |
| **126** | **SUCCESS_WITH_WARNINGS** | **895.4s** | **33** | glm-5.3-flash | **true** |

### 5.1 根因：推理型模型的思维链不算「进展」（本轮自己引入的缺陷）

第一次排查时的怀疑方向是「上游死流」，并做了两个只读探针想排除阈值误杀：

| 探针 | 测什么 | 结果 | 结论 |
|---|---|---|---|
| `SseGapProbe` | 单次生成 198 秒，SSE **原始行**间隔最大值 | maxGap 1538ms | 传输层没断 |
| `TtftProbe` | 上下文 103k 字符的首字延迟 | TTFT 5818ms | prefill 不是瓶颈 |

这两个探针都指向「阈值有百倍余量」，于是当时的结论写成「是真实死流，阈值没有误杀」。**这个结论是错的**——
两个探针测的都是**传输层**（socket 上有没有数据），而无进展检测看的是**回调层**（`AgentResultHandler` 有没有被调用）。
两者之间隔着一层分派：agent4j 的 `OpenAIChatModel` 把 `reasoning_content` / `reasoning` / `thinking` /
`thinking_content` 四种字段**路由到 `onThink`**，只有正文才走 `onMessage`。本仓库当时只把
`onMessage` / `onTool` 计为活动，`onThink` 从未被覆写——**推理型模型「正在思考」与「流已断」在检测层完全同形**。

`ReasonGapProbe` 直接按检测器的判据（只算正文）重算了一遍，缺陷立刻现形：

| 模型 | 生成时长 | 思维链增量 | 正文增量 | **按正文计的最大空档** |
|---|---|---|---|---|
| hy4-preview（兜底档案） | 270s | 3322 条 | 195 条 | **254s** |
| glm-5.3-flash（#123/#124/#126 实际用的） | 70s | 4446 条 | 2042 条 | **58s** |

阈值是 180 秒。hy4-preview 的 254 秒空档**必然误杀**；glm-5.3-flash 的 58 秒单看没有超阈值，
但它只是「一次请求」的量级——真实会话是 20~30 次工具调用串联，每次请求都要先输出大段思维链，
累积效应同样会把会话推过阈值。run#123 的执行日志证实了这一点：它在 `set_article_draft_cover` 之后
（第 33 次工具调用）进入下一轮生成，随后 180 秒无正文事件被判停滞——**而线程当时一直在正常出字**：

- 01:02:33 判死（run#123）
- 01:05:46 仍有草稿落库降级日志
- 01:14:27 仍有 MARKFLOW 语法校验日志

判死之后 3 分钟、11 分钟仍有该会话的日志，说明它根本没死，是被自己的检测器放弃的
（agent4j 无取消接口，只能放弃线程）。

### 5.2 修法

`AgentInvoker.attempt` 覆写 `onThink`，**只刷新活动时间、不写入 `assistantText`**：

```java
@Override
public void onThink(String thought) {
    if (thought == null) return;
    markActivity(lastActivity, lastActivityAt, "收到思维链输出 " + thought.length() + " 字符");
}
```

判据由此从「吐正文了吗」回到它本该是的「上游还活着吗」。思维链是模型的草稿纸、不是交付内容，
因此不混进正文（否则会污染草稿与终态消息）。

**反证（红→绿）**：把 `onThink` 的实现临时改成空方法后，新增回归测试
`AgentInvokerTest.reasoningOnlyOutputCountsAsProgressNotStall` 立刻失败，报错信息与 run#123/#124 同形：

```
StageTimeout: 智能体会话停滞（1 秒无任何事件）；卡点：最后活动为「会话已启动，等待模型首个响应」；已调用工具 0 次
```

改回实现即绿。这证明该测试真的能拦住这个缺陷，不是一条跟着实现走的空断言。

### 5.3 修复后的成功样本（run#126）

修复编译进 `target/classes`（01:49:25）后，服务于 01:56:12 重启，run#126 于 01:57:52 启动：

| 指标 | 基线 run#116 | 本轮 run#126 | 变化 |
|---|---|---|---|
| 状态 | SUCCESS_WITH_WARNINGS | SUCCESS_WITH_WARNINGS | 持平 |
| 墙钟耗时 | 553s | **895.4s** | **+342s（变慢）** |
| 工具调用数 | 36 | 33 | −3 |
| 产出 | 文章，saved=true | 文章 #63（5392 字），saved=true | 均交付 |
| 会话停滞事件 | — | **0 次** | 不再误杀 |

**诚实的读法**：这一次比基线**慢了 342 秒**，不能宣称「SINGLE 变快了」。原因有两条，都能从数据里看出来：

1. **基线 553 秒是改造前的模型跑出来的**，而 run#126 全程用 `glm-5.3-flash`——它是「能力优先」的写作档，
   不是速度档（153 tok/s 对 hy4-preview 的 23.5 tok/s 是快，但思维链本身要占大量输出 token）。
2. **本轮多花了思维链的钱**：修复让会话不再被误杀，代价就是那些原本会被中断的思维链现在要跑完。

真正的效率收益在 COORDINATOR 链路（§四，−274s）和「不再误杀」这件事上，而不在 SINGLE 的墙钟数字上。
把 SINGLE 也调快的正确做法是给它换速度档档案（`deepseek-flash`），这属于调参、不属于本轮范围。

---

## 六、验收过程自身发现并修复的 3 个缺陷

### 缺陷 1：失败提示谎报「已切换模型档案」

`TaskExecutionService.failureHint` 的【会话停滞】分支原文写「系统已自动尝试切换备用模型档案」。但实测 run#123/#124 是**已调用 33/21 次工具后的停滞**，按安全边界有意不切换。

修法：措辞改为陈述**机制**而非**已发生**，并指向真实数据源：

```
【会话停滞】模型长时间没有返回任何内容，已按无进展中止（不再白等整个超时）。
常见原因是上游网关抖动或该模型当前不可用。
注意：若本次已调用过工具，系统**不会**切换模型档案重跑（那可能重复生图/落库计费）；
本次实际用过的档案见 stages_summary.profilesUsed。
```

回归测试 `TaskFailureMessageTest.stallMessageDoesNotFalselyClaimAProfileSwitch` 断言消息含「不会」与「profilesUsed」、且**不含**「已自动尝试切换」。

### 缺陷 2：失败路径不留「用过哪个模型」

切换日志发生在 `attempt` 之外，那次尝试的局部日志列表已随失败作废；失败路径又拿不到 `Outcome`，所以 `profilesUsed` 为空，事后无法归因。

修法：给 `AgentRunner.ProgressListener` 加 `default void profileUsed(String label)`；`AgentInvoker` 在**主用候选启动时**与**两个切换点**各调一次 `reportProfileUsed(...)`；`TaskWorkspace.progressListener()` 覆写该方法，实时汇入运行级档案链（成功路径仍经 `addProfilesUsed(Outcome)` 再报一次，`addProfilesUsed` 内部去重）。

回归测试：`AgentInvokerTest.reportsProfilesUsedEvenWhenAllCandidatesFail`、`reportsTheSingleCandidateWhenItFails`。

### 缺陷 3：无进展检测把「正在思考」误判为「停滞」（本轮引入、本轮修掉）

详见 §五。一句话：新加的无进展检测只把正文与工具事件算作进展，而推理型模型的思维链走
`onThink` 这个从未被覆写的回调——于是正常出字的会话被判停滞中止（run#123/#124）。

修法：覆写 `onThink` 并刷新活动时间（不写入正文）。回归测试
`AgentInvokerTest.reasoningOnlyOutputCountsAsProgressNotStall`，已做红→绿反证。

**这条的教训值得单独记下**：新加的「安全护栏」本身也会成为故障源，而且它的失效方式很隐蔽——
护栏杀死的是一个**健康**的会话，日志上看起来与「上游真的卡死」一模一样。
`StageTimeout` 的类注释里原本写着「只要有一条路径忘了上报，长任务就会被误杀」，这次正是这句话应验了：
不是忘了上报，而是**有一整类输出压根没接上报的线**。给护栏加判据时，必须把「数据源有多少条通道」
一起数清楚，只覆盖其中一条通道就等于给另一条通道上的正常流量判死刑。

---

## 七、六个 Phase 的落地位置

| Phase | 内容 | 主要文件 |
|---|---|---|
| 0 | 模型可用性探测与基线 | `docs/dev/model-availability-probe.md` |
| 1 | 档案故障切换 | `LlmProfileService.failoverChain`、`AgentFactory/ScheduledAgentFactory.buildCandidates`、`AgentInvoker.runWithCandidates` |
| 2 | 按智能体分配模型 + 兜底 | `LlmProfile.isFallback`、`LlmProfileSeeder`、`AgentSeeder`（仅当为空才写） |
| 3 | 效率：无进展检测 / 简报截断 / 专用线程池 / 预算统一 / 提示只组装一次 | `StageTimeout`、`ArticleAiService`、`TaskExecutionService`、`ToolCallBudget` |
| 4 | 工具治理：只读去重 / 循环检测 / 预算提示 / 协议补 JSON 示例 / 封面校验上提 | `ToolCallGovernor`、`AgentProtocols`、`TaskExecutionService` |
| 5 | 可观测性：分阶段耗时+工具数+档案 / 失败分类消息 / 巡检 SQL / MODE 落库时机 | `TaskWorkspace`、`TaskExecutionService`、`TasksView.vue` |
| 6 | 遗留缺陷：5 组同名字段声明冲突归零 | 各实体注解 + `EntityColumnDeclarationConsistencyTest` |

Phase 6 的回归护栏 `EntityColumnDeclarationConsistencyTest` 已做**反证**：把 `Asset.sourceUrl` 改回 2000，测试立刻报 2 处失败；改回 1000 即绿。这证明这条护栏真的能拦住收窄。

---

## 八、遗留项与未做

1. **SINGLE 链路仍是「能力优先」档**：run#126 全程用 `glm-5.3-flash`，比基线慢 342 秒（见 §5.3）。
   若要 SINGLE 也快，应把 `builtin_scheduled_creator` 改绑 `deepseek-flash`（445 tok/s）——
   属调参，本轮不动（方案 §2.3 有意选了能力优先）。
2. **自然日 09:00 定时触发未观察**（方案第十节第 4 项）：需要等一个自然日，本轮时间窗内没等到。
3. **编辑器链路的模型切换**（方案 §1.5，可选）：已按方案默认包含，但**未做实机验证**——
   需要一次真实的编辑器交互才能观察到。
4. **`temperature`/`maxTokens` 仍是死字段**：agent4j 2.3.3 的 `LLMModel.create` 只收 4 个参数，
   需上游改，本轮明确不做。
5. **`onThink` 之外是否还有其他未接的通道**：本次只修了思维链这一条。`ResultHandler` 还有
   `onUsage` / `onContextCompression` 两个回调（这两个是「用量的汇总上报」，不是逐 token 增量，
   不适合当活动信号）。agent4j 后续若新增流式通道，同样要一并接上。
6. **git push 未执行**：本轮只做本地 commit。推送到 `huanyu/main`（public 仓库）需单独授权，
   且推送前要扫密钥。
7. **`.zcode/handoff/latest.json` 未改动**：并行会话共用单文件，本轮只写旁路文件
   `.zcode/handoff/sess-e09c7784-reliability-round.json`。

---

## 九、复现本文结论的只读 SQL

见 `docs/dev/scheduled-task-failure-attribution.md` 第五～八节：按档案统计成功率与耗时、发生过切换的运行、`JSON_TABLE` 拆 `stages_summary` 看分阶段明细、失败类别分布。

---

## 十、临时探针（`target/scratch/`，不纳入版本控制）

本次验收用到 5 个只读探针。它们的价值在于**同一现象换判据看**——§5.1 那次误判正是因为
两个探针都测了传输层，而检测器看的是回调层：

| 探针 | 测什么 | 用于 |
|---|---|---|
| `ModelAvailabilityProbe` | 逐个模型发最小流式请求 | Phase 0：本 key 到底能用哪些模型 |
| `SseGapProbe` | SSE 原始行的间隔分布 | 传输层是否断流 |
| `TtftProbe` | 大上下文首字延迟 | prefill 是否是瓶颈 |
| `RawSseProbe` | 流里有哪些 delta 字段 | 发现 `reasoning_content` 的存在 |
| `ReasonGapProbe` | **按检测器的判据**（只算正文）重算空档 | 定位误杀根因 |

`ReasonGapProbe` 是其中唯一能定案的一个：它复刻了检测器的算法而不是另立一套，
因此它量出来的空档就是检测器会看到的空档。**排查「护栏误杀」类问题时，探针必须按护栏的判据算，
否则测出来的「一切正常」恰恰会掩盖问题。**
