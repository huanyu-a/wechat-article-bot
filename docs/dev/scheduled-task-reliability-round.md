# 定时任务可靠性 + 效率优化 + 模型档案故障切换 —— 实机验收记录

方案：`.zcode/plans/plan-sess_e09c7784-7835-4597-9cea-4fe764d76dff.md`（六个 Phase 全部落地）
验收日期：2026-09-16
闸门：`.mvn/mvn-local.sh -o test` → **Tests run: 401, Failures: 0, Errors: 0**（基线 329，本轮 +72）。
<br>（收尾时补齐了方案 §九 漏掉的一项——`LLM_PROFILE.IS_FALLBACK` 的真库用例，见 §十一；397 → 401。）

---

## 一、结论速览

| 验收项 | 结果 | 证据位置 |
|---|---|---|
| ① 新列 / 标准档案 / 7 个绑定 / 兜底标记 | **通过** | 本文 §二 |
| ② 强制故障切换反例（新功能的确定性证据） | **通过** | 本文 §三，run#122 |
| ③ 效率提升（COORDINATOR / SINGLE） | **不成立**（两条链路对全量基线都更慢：COORDINATOR +246s、SINGLE +691s） | 本文 §四、§5.3 |
| ③ 功能改善（不再阶段中止 / 不再误杀） | **通过** | 本文 §四、§五 |
| ④ 验收过程自身发现并修复 3 个缺陷 | **已修 + 有测试** | 本文 §六 |

**一句话**：故障切换这个新功能已被确定性证明可用（run#122 强制指向不存在的模型名，整轮仍成功）；
本轮真正的收益是**功能与可靠性**（COORDINATOR 不再有阶段中止、SINGLE 不再被误杀），
**不是效率**——方案「换快模型就能提速」的假设经实测**不成立**（§4.1），两条链路的耗时对全量基线
都是**变慢**（COORDINATOR +246s、SINGLE +691s），且原先的「−15%」是拿最慢的一次成功运行当基线算出来的（§四）。

> ⚠️ 本文件在提交后经过一次**结论修正**：初版依据单点对照写了「COORDINATOR −15%」，并把 run#123/#124
> 判为「真实死流、阈值没有误杀」。两处结论都是错的，已在 §四、§5.1 写明错在哪、为什么错。
> 之后又修了**第三处同类错误**：SINGLE 的基线原先也只取了单样本（run#116），
> 换成 7 次成功均值后，SINGLE 同样是明显变慢（+691s），而不是「略慢」。
> 保留修正过程而不是抹掉，是因为「怎么错的」比「结论是什么」更值得复用——
> **这三处都是同一个毛病：拿 1 个样本当基线。**

---

## 二、实机① 重启后的落地状态

重启后查库（只读 SQL 见 `docs/dev/scheduled-task-failure-attribution.md` **§四**——该文件里全部 SQL 代码块都在 §四的「第 ③ 批」与「巡检 SQL」两个子节下；§五 只有验收叙述，没有任何 SQL）：

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

## 四、实机③ COORDINATOR：功能改善确认，效率提升**不成立**

run#125 与「基线 run#119」单点对照会得出「−15%」的结论，但**这个口径是错的**——
run#119（1801s）恰好是历史最慢的一次成功运行。把基线整体拉出来看：

| 口径 | 样本 | 耗时 |
|---|---|---|
| 基线（task#5 全部成功运行，id<125） | 9 次 | 均值 **1281s**，区间 457–1801s |
| 本轮 run#125 | 1 次 | **1527s** |

**run#125 比基线均值慢 246 秒（+19%），不是快 15%。** 基线区间本身横跨 4 倍（457s 到 1801s），
单次运行之间的差异远大于本轮改造可能带来的收益——**用 1 个样本去证明效率提升，在统计上不成立**。

**同一口径也适用于 SINGLE（task#4）。** 本文初版在这里只拿了 run#116（553s）这一个样本当基线，
和上面「用最慢一次成功运行当基线」是同一个错误，一并改正：

| 口径 | 样本 | 耗时 |
|---|---|---|
| 基线（task#4 全部成功运行，id<126） | 7 次 | 均值 **204s**，区间 66–553s |
| 本轮 run#126 | 1 次 | **895.4s** |

**run#126 比基线均值慢 691 秒，而且是 SINGLE 有记录以来最慢的一次成功运行**（前最慢是 run#116 的 553s）。
两条链路的方向一致：**本轮改造没有带来耗时上的收益**。合理解释见 §4.1——被绑给 SINGLE 的
`glm-5.3-flash` 在「生成工具参数」这个场景下并不比 `hy4-preview` 快，工具调用次数也几乎没变
（run#116 = 36 次、run#126 = 33 次），所以耗时增长不是「多调了工具」，而是单位生成更慢或本轮任务更重。

功能层面的改善是确凿的（这一条与耗时无关）：

| 指标 | 基线 run#119 | 本轮 run#125 |
|---|---|---|
| 状态 | SUCCESS_WITH_WARNINGS（1 个阶段中止） | **SUCCESS**（无阶段中止） |
| 产出 | 内容可能不完整 | 文章 #62，HTML 49729 字节，MARKFLOW，交付完整 |

`STAGES_SUMMARY` 的分阶段明细（run#125）：

| 阶段 | 耗时 | 工具调用 |
|---|---|---|
| DELEGATE_RESEARCH | 196.4s | 57 |
| DELEGATE_WRITING | 480.0s | 65 |
| DELEGATE_ILLUSTRATION | 126.1s | 10 |
| DELEGATE_REVIEW | 198.7s | 82 |
| DELEGATE_ILLUSTRATION（返工轮） | 172.0s | 13 |
| COORDINATE（主编总控） | 1525.1s | 8 |

### 4.1 为什么「换快模型就能提速」在这条链路上不成立

方案的效率假设是「hy4-preview 23.5 tok/s 太慢，换成 deepseek-flash 445 / glm-5.3-flash 153 就能提速」。
这个假设**建立在一个未经验证的外推上**：评测表的 tok/s 是**纯正文**场景测出来的，
而定时任务里模型的输出**几乎全是工具参数**（`save_article_draft` 的 `content` 就是整篇文章）。

所以直接测了一次「让模型调用 `save_article_draft` 写 6000 字」的真实生成速率
（`target/scratch/ToolArgStreamProbe2.java`，把 arguments 的 JSON 反转义后数**真实字符**，
而不是数 SSE 行长度——行长度随分片粒度变化，不是可比量）：

| 模型 | 第 1 次 | 第 2 次 | 第 3 次 | 结论 |
|---|---|---|---|---|
| **deepseek-flash** | 273 字符/秒 | — | — | ✅ 确实快，约 3 倍 |
| **glm-5.3-flash** | 69 | 111 | 125 | ⚠️ 与 hy4-preview **重叠** |
| **hy4-preview**（基线） | 86 | 77 | 95 | ⚠️ 并不比 glm 慢 |

**结论**：只有 `deepseek-flash` 是真正快的档；`glm-5.3-flash` 在「生成工具参数」这个场景下
**并不比基线的 hy4-preview 快**（69~125 与 77~95 的区间重叠，样本量下无法区分）。
方案 §2.3 把写作类智能体绑到 glm-5.3-flash 是**能力优先**的选择，不是速度选择——
这一点在 §5.3 的 SINGLE 实测里得到印证（换过去之后反而更慢）。

**这是本轮最值得记下的一条**：模型速度不能从评测表的 tok/s 外推到工具调用场景。
要判断「换模型能不能提速」，必须按**实际工作负载**（这里是生成工具参数）测。

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

**「不是死流」最直接的证据来自服务日志**：run#124 在 01:11:27 被判停滞中止，而 **01:14:27**（3 分钟后）
仍有它那条线程在调用 `ScheduledArticleTools` 做 MARKFLOW 草稿校验——判死之后它还活着。
`TASK_RUN` 表也佐证：run#124 于 01:11:27 结束、run#125 于 01:16:21 启动，**01:14:27 时没有任何运行在册**，
所以那条日志只能是被判死却仍在跑的那个线程发的。agent4j 无取消接口，判死只是「放弃等待」，线程照跑。

`ReasonGapProbe` 直接按检测器的判据（只算正文）重算了一遍，缺陷立刻现形
（下表是我自己复跑的数字，与首次排查时的量级一致；括号内是首测值）：

| 模型 | 生成时长 | 思维链增量 | 正文增量 | **按正文计的最大空档** |
|---|---|---|---|---|
| hy4-preview（兜底档案） | 232s | 2939 条 | 331 条 | **206s**（首测 254s） |
| glm-5.3-flash（#123/#124/#126 实际用的） | 66s | 3954 条 | 2008 条 | **55s**（首测 58s） |

阈值是 180 秒。**hy4-preview 的 206~254 秒空档必然误杀**，这一条无争议。

glm-5.3-flash 的 55 秒单看没有超阈值，但它**不能**用来给「误杀」翻案，因为：

1. **它只是一次冷启动请求**：上下文很小、没有累积。真实会话是 20~30 次工具调用串联，
   每轮都要带着全部历史重新 prefill，且思维链越到后面越长。
2. **同一探针（`ToolArgStreamProbe2`）量到 glm 在「工具参数开始下发之前」经常要花两分钟**：
   三次同题测量的「首 token → 首个 arguments 分片」间隔分别是 **13.8s / 124.4s / 121.8s**
   （三次的 TTFT 都只有 4.5~5.1s，所以这段几乎全是思维链输出）。
   而修复前，这段窗口里 `onMessage`/`onTool` **一个事件都不会触发**——
   两次就有一次直接顶到 180 秒阈值边上，而真实会话的上下文比这个冷请求大得多。
3. **两次真实运行就是死在 180 秒整**（run#123 的卡点「工具完成 set_article_draft_cover，距今 180 秒」），
   这是「发生过」的直接证据，不需要靠外推。

所以两件事要分开说：**hy4-preview 是「确定会误杀」，glm-5.3-flash 是「已经误杀了两次」。**

run#123 的执行日志显示它在 `set_article_draft_cover` 之后（第 33 次工具调用）进入下一轮生成，
随后 180 秒无正文事件被判停滞——**而线程当时一直在正常出字**（判死之后仍有它的日志，见上）。

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

**反证（红→绿）**：把 `onThink` 的实现临时改成空方法后重跑，`AgentInvokerTest` 从 35 例全绿
变成 **35 例 1 错**，且失败的正是新加的那一条（`AgentInvokerTest.java:254`），
异常栈落在 `StageTimeout.inactivityTimeout` → `AgentInvoker.fail`：

```
StageTimeoutException: 智能体会话停滞（1 秒无任何事件）；卡点：最后活动为「会话已启动，等待模型首个响应」，距今 1 秒；已调用工具 0 次
```

与 run#123/#124 的真实报错同形（那两条是「距今 180 秒」）。改回实现即恢复 35 例全绿。
这证明该测试真的能拦住这个缺陷，不是一条跟着实现走的空断言。

### 5.3 修复后的成功样本（run#126）

修复编译进 `target/classes`（01:49:25）后，服务于 01:56:12 重启，run#126 于 01:57:52 启动：

| 指标 | 基线 run#116 | 本轮 run#126 | 变化 |
|---|---|---|---|
| 状态 | SUCCESS_WITH_WARNINGS | SUCCESS_WITH_WARNINGS | 持平 |
| 墙钟耗时 | 553s（单样本）／204s（7 次成功均值） | **895.4s** | **+342s ／ +691s（两种口径都更慢）** |
| 工具调用数 | 36 | 33 | −3 |
| 产出 | 文章，saved=true | 文章 #63（5392 字），saved=true | 均交付 |
| 会话停滞事件 | — | **0 次** | 不再误杀 |

**修复有效的证据不是耗时，而是「零停滞事件 + 成功交付」**：同一个任务，修复前在 405s / 546s
被判停滞中止，修复后跑满 895.4s 并成功保存文章——而 895.4s 已逼近 `single-timeout-seconds=900`
的硬上限。**若没有这次修复，它必然会在 900s 被硬超时杀掉。**

**关于耗时不能宣称变快**：run#126 比基线 run#116 慢 342 秒；换成「7 次成功运行的均值 204s」这个更严格的口径，
慢 691 秒，且它本身就是 SINGLE 最慢的一次成功运行（见 §四）。原因已在 §4.1 用实测数据说明——
`glm-5.3-flash` 在生成工具参数这个场景下**并不比 hy4-preview 快**（69~125 对 77~95 字符/秒）。
想真正提速，唯一被实测证明有效的是把 SINGLE 换到 `deepseek-flash`（273 字符/秒，约 3 倍）。
这属于调参，本轮有意没做（方案 §2.3 选的是能力优先）。

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
| 2 | 按智能体分配模型 + 兜底 | `LlmProfile.isFallback`、`LlmProfileSeeder`、`AgentSeeder`（仅当为空才写）· 真库用例见 §十一 |
| 3 | 效率：无进展检测 / 简报截断 / 专用线程池 / 预算统一 / 提示只组装一次 | `StageTimeout`、`ArticleAiService`、`TaskExecutionService`、`ToolCallBudget` |
| 4 | 工具治理：只读去重 / 循环检测 / 预算提示 / 协议补 JSON 示例 / 封面校验上提 | `ToolCallGovernor`、`AgentProtocols`、`TaskExecutionService` |
| 5 | 可观测性：分阶段耗时+工具数+档案 / 失败分类消息 / 巡检 SQL / MODE 落库时机 | `TaskWorkspace`、`TaskExecutionService`、`TasksView.vue` |
| 6 | 遗留缺陷：5 组同名字段声明冲突归零 | 各实体注解 + `EntityColumnDeclarationConsistencyTest` |

Phase 6 的回归护栏 `EntityColumnDeclarationConsistencyTest` 已做**反证**：把 `Asset.sourceUrl` 改回 2000，测试立刻报 2 处失败；改回 1000 即绿。这证明这条护栏真的能拦住收窄。

> Phase 3 的落地状态需要一句补充：**它的主要产物（无进展检测）在本轮反而是故障源**，
> 见 §六缺陷 3。其余四项（简报截断、专用线程池、预算统一、提示只组装一次）未单独做量化验证。

---

## 八、遗留项与未做

1. **SINGLE 链路仍是「能力优先」档**：run#126 全程用 `glm-5.3-flash`，比单样本基线慢 342 秒、
   比 7 次成功均值慢 691 秒（见 §四、§5.3）。若要 SINGLE 也快，应把 `builtin_scheduled_creator` 改绑
   `deepseek-flash`——**这是唯一被实测证明更快的档**（273 字符/秒 vs glm 的 69~125、hy4 的 77~95）。
   本轮已积累两条独立支持该改动的证据（§4.1 的速率实测 + §四/SINGLE 基线双双变慢），
   但它属调参，本轮有意不动（方案 §2.3 选了能力优先）。**建议下一轮把它作为第一优先项。**
2. **`glm-5.3-flash` 的定位需要复核**：实测它在「生成工具参数」场景下并不比免费的 hy4-preview 快
   （§4.1）。方案把它绑给 writer / reviewer / chief / scheduled_creator 的理由是**能力分**（57.5），
   这个理由仍然成立；但若后续想要效率，**不能**指望换成 glm 来提速。
3. **效率提升本轮没有兑现**：原方案的效率假设（换快模型 → 更快）已被实测推翻（§4.1）。
   真正能省时间的方向是「减少每轮的上下文重发」（如调研简报截断，Phase 3 已做）与
   「用 deepseek-flash 跑工具密集阶段」，而不是整体换档。
4. **自然日 09:00 定时触发：仍未拿到「自然触发成功」的样本，但**（2026-09-16 09:35 补充）**拿到了停机场景的确定性行为**——
   当天 08:58 机器重启，Docker Desktop 与 `momo-mysql-dev` 容器都没起来，应用侧 09:00 无进程在跑，
   所以**今天的 09:00 触发整轮没有发生**（查库确认 09-16 只有 run#122~#126 五条手工运行、无
   `TRIGGER_TYPE=SCHEDULED` 记录）。09:35 恢复三级启动后，日志立刻打出
   `Handling 4 trigger(s) that missed their scheduled fire-time.`，但**没有补跑**，也没有新增运行。
   根因是预期的：项目只配了 `org.quartz.jobStore.misfireThreshold: 60000`、未写 misfire 指令，
   于是走 `SMART_POLICY`——对 cron 触发器它等价于 `DO_NOTHING`（错过即跳过，次日按正常计划触发）。
   **结论**：停机导致的错过是**设计行为、不是缺陷**；「等一个自然日看成功率」这一项仍需在
   **不停机**的前提下再观察一次。
5. **编辑器链路的模型切换**（方案 §1.5，可选）：已按方案默认包含，但**未做实机验证**——
   需要一次真实的编辑器交互才能观察到。
6. **`temperature`/`maxTokens` 仍是死字段**：agent4j 2.3.3 的 `LLMModel.create` 只收 4 个参数，
   需上游改，本轮明确不做。
7. **`onThink` 之外是否还有其他未接的通道**：本次只修了思维链这一条。`ResultHandler` 还有
   `onUsage` / `onContextCompression` 两个回调（这两个是「用量的汇总上报」，不是逐 token 增量，
   不适合当活动信号）。agent4j 后续若新增流式通道，同样要一并接上。
8. **git push 未执行**：本轮只做本地 commit。推送到 `huanyu/main`（public 仓库）需单独授权，
   且推送前要扫密钥。
9. **`.zcode/handoff/latest.json` 未改动**：并行会话共用单文件，本轮只写旁路文件
   `.zcode/handoff/sess-e09c7784-reliability-round.json`。

---

## 九、复现本文结论的只读 SQL

见 `docs/dev/scheduled-task-failure-attribution.md` **§四 的「巡检 SQL」小节（编号 5)～8)）**：按档案统计成功率与耗时、发生过切换的运行、`JSON_TABLE` 拆 `stages_summary` 看分阶段明细、失败类别分布。
（该文件只有 §一~§五 五个大节，巡检 SQL 在 §四 的 `###` 子节里——不要按「第五～八节」去找。）

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
| `ToolArgStreamProbe` / `…2` | 工具参数是增量下发还是整段缓冲；真实生成速率 | 排除「长文保存被误判停滞」；证伪「换模型更快」 |

`ReasonGapProbe` 是其中唯一能给误杀定案的一个：它复刻了检测器的算法而不是另立一套，
因此它量出来的空档就是检测器会看到的空档。**排查「护栏误杀」类问题时，探针必须按护栏的判据算，
否则测出来的「一切正常」恰恰会掩盖问题。**

`ToolArgStreamProbe` 的原始用途是排除「`save_article_draft` 的长参数被网关整段缓冲导致误杀」——
实测**不是**：6000 字参数的下发窗口里最大间隔只有 **1059ms**（阈值 180s，余量 169 倍）。
它的 v2 顺手证伪了方案的效率假设（§4.1），属于计划外的收获。

---

## 十一、收尾补齐：`IS_FALLBACK` 的真库用例（方案 §九 的漏项）

对照方案 §九 的测试清单逐项核实时，发现只有一项**没有落地**：

> **打真库用例**（仿 `ArticleLongContentPersistenceTests`）：`IS_FALLBACK` 列由迁移幂等补上。

已补上 `src/test/java/ink/icoding/wechat/article/LlmProfileFallbackColumnPersistenceTests.java`（4 例）：

| 用例 | 钉住什么 |
|---|---|
| `fallbackColumnIsMaterializedAsNullableTinyint` | 列真实存在，且是**可空** `tinyint` |
| `fallbackFlagRoundTripsThroughMapperIncludingNull` | true / false / **NULL** 三态原样往返 |
| `findFallbackIgnoresNullAndFalseRows` | 故障切换链第三段取数正确，NULL/false 行不干扰 |
| `seederMarksDefaultAsFallbackOnlyOnceAndNeverOverridesTheUser` | 种子「唯一一处替用户做决定」且不覆盖用户选择 |

**为什么 NULL 那一档值得单列**：开发库里**真实存在** `IS_FALLBACK = NULL` 的存量行
（smart-mybatis 补列时老行只能是 NULL，实测 `dots3-note-prev` 即如此）。若有人把类字段改成
基本类型、或给列加 NOT NULL，`findFallback()` 的行为与存量库的补列都会崩——而那种故障
只出现在真实列上，mock 用例永远绿。这正是 §六 那组「实体声明与真实列必须同向」结论的延续。

**反例验证（红→绿）**：把断言里的 `tinyint|YES` 改成 `tinyint|NO` 后单跑该用例，
实测 `expected: "tinyint|NO" but was: "tinyint|YES"` 并 FAILURE——证明它读的是真实列值，
不是一条恒真的空断言。还原后逐字节一致。

**该用例的验证边界（写清楚免得被高估）**：测试库的表结构由 smart-mybatis 按实体注解**新建**，
所以它证明的是「新建出来的列可用」，**证明不了存量库的 ALTER 路径**——本列没有显式迁移 runner，
补列完全由 smart-mybatis 完成，那一步的证据是 2026-09-16 的实机查库（新列已存在、兜底档案恰好 1 条）。

**闸门**：397 → **401 例全绿**，`find src -newermt` 确认闸门后源码未再变动。
