# 模型可用性实测（Phase 0 探测结果）

> 探测时间：2026-09-15 22:xx｜方法：用应用默认档案的密钥（AES-GCM 解密自 `LLM_PROFILE.API_KEY_ENCRYPTED`）
> 直连 `https://nexus.bx9y.com.cn/v1/chat/completions`，逐个模型发同一条最小流式请求。
> **只读，不打印密钥明文。** 探针源码：`target/scratch/ModelAvailabilityProbe.java`（临时工具，未纳入版本控制）。

## 一、为什么必须先探测

`Nexus模型评测表.md` 列了 17 个模型，但**评测表反映的是 nexus 全平台可用性，不等于本应用这个 key 的渠道组有权限**。
实测结果证实了这个担心：评测表里 Tier S 的 `glm-5.3`、Tier A 的 `deepseek-v4-flash-0731`、`LongCat-2.0`、`kimi-k3`
在本 key 下**全部返回 `503 model_not_found`（No available channel）**。

> ⚠️ 这正是 `AgentInvoker.isPermanent` 把 `model_not_found` 归为「永久不重试」的原因——
> 重发同一个请求结果一定相同。**但换一个模型就可能治好**，这正是本轮「模型档案故障切换」要解决的问题。

## 二、实测结果（本应用 key）

| 模型 | HTTP | TTFT | 总耗时 | 结论 |
|---|---|---|---|---|
| **agnes-2.5-flash** | 200 | **152ms** | 2590ms | 可用，首字极快 |
| **step-3.7-flash** | 200 | 409ms | 2861ms | 可用，快 |
| **deepseek-flash** | 200 | 1081ms | **2883ms** | ✅ 可用，综合分最高（72.4） |
| **dots3-note-prev** | 200 | 545ms | 3717ms | ⚠️ 可用，但评测表标注**多轮记忆测试失败**——智能体强依赖多轮记忆，排除 |
| **glm-5.3-flash** | 200 | 1532ms | 3565ms | ✅ 可用，能力分最高（57.5） |
| sensenova-6.8-flash-lite | 200 | 2764ms | 5563ms | ⚠️ 评测表标注「实测不稳定」，排除 |
| mimo-v2.5 | 200 | 3075ms | 7574ms | 可用，能力分偏低（42） |
| agnes-3.0-flash | 200 | 2085ms | 7657ms | 可用，能力分未公布 |
| qwen3.8-flash | 200 | 1375ms | 10099ms | 可用，能力分偏低（41） |
| **hy4-preview** | 200 | 2013ms | **13161ms** | 可用（当前默认），免费；但**总耗时是 deepseek-flash 的 4.6 倍** |
| step-router-v1 | 200 | 1974ms | 1984ms | ⚠️ 输出异常少（评测表 1/3 轮成功），排除 |
| glm-5.3 | **503** | — | — | ❌ 无可用渠道 |
| deepseek-v4-flash-0731 | **503** | — | — | ❌ 无可用渠道 |
| LongCat-2.0 | **503** | — | — | ❌ 无可用渠道 |
| kimi-k3 | **503** | — | — | ❌ 无可用渠道 |
| deepseek-v4-flash-vision-exp | **503** | — | — | ❌ 无可用渠道 |
| glm-4.7 | **503** | — | — | ❌ 无可用渠道 |

> 关于「总耗时」：同一条 prompt（输出 1~120 的数字）下的端到端耗时，**横向可比**。
> 探针里的「chars」计的是 SSE 原始行长度（含 JSON 包装），**不能当吞吐量读**，故本表不列。
> ~~真正的吞吐量以评测表的 TPS 为准：deepseek-flash 445 tok/s、glm-5.3-flash 153 tok/s、hy4-preview 23.5 tok/s。~~
> **⚠️ 这一句已被实测推翻，见第六节**——评测表的 tok/s 是纯正文场景，不能用于「工具调用为主」的
> 定时任务链路。实测 hy4-preview 在生成工具参数时并不比 glm-5.3-flash 慢。

## 三、据此修正的分配方案

原方案给审稿人/主编定的 `glm-5.3` **不可用**，改为 `glm-5.3-flash`（本 key 下能力分最高）。
可用模型里综合分前两名是 `deepseek-flash`（72.4）与 `glm-5.3-flash`（72.2），两者都可用。

| 智能体 | 主用档案 | 依据 |
|---|---|---|
| `builtin_researcher` 调研员 | **deepseek-flash** | 工具密集多轮检索，445 tok/s 最快；能力 54 |
| `builtin_illustrator` 配图师 | **deepseek-flash** | 以提示词与工具调用为主，速度优先 |
| `builtin_editor` 编辑器 | **deepseek-flash** | 用户在场，445 tok/s 交互最跟手 |
| `builtin_writer` 撰稿人 | **glm-5.3-flash** | 能力 57.5（可用模型最高）；写作质量关键 |
| `builtin_scheduled_creator` 定时创作(SINGLE) | **deepseek-flash**（2026-09-17 由 glm-5.3-flash 改） | 负载是**生成工具参数**（整篇正文即 `save_article_draft` 的 `content`），实测 273 vs 69~125 字符/秒 |
| `builtin_reviewer` 审稿人 | **glm-5.3-flash** | 判断力关键、调用量低，取能力优先 |
| `builtin_chief` 主编 | **glm-5.3-flash** | 规划质量决定整轮走向、调用量低 |
| **兜底 fallback** | **hy4-preview** | 免费通道、能力 57，最后安全网 |

> 本表的**「能力」理由全部成立**；但「速度」理由只对 `deepseek-flash` 成立——
> 它是唯一被实测证明更快的档（273 字符/秒）。`glm-5.3-flash` 的两条（writer/reviewer/chief）
> 都写的是「能力优先」，没有声称速度，因此**不需要修改**。见第六节。
>
> **`builtin_scheduled_creator` 是唯一的例外**：它当初按「一人做完调研+写作+配图、能力优先」
> 绑定，但这个理由**用错了负载口径**——该链路几乎没有「写正文」的自由生成，输出几乎全是
> 工具参数。按实际负载实测（`scheduled-task-reliability-round.md` §4.1），glm 在这个场景下
> 与免费的 hy4-preview 区间重叠，故改绑 `deepseek-flash`。见该文 §八.1。

共 **3 个档案**（2 主用 + 1 兜底），全部实测可用。`dots3-note-prev` / `sensenova-6.8-flash-lite` /
`step-router-v1` 虽返回 200 但分别有记忆缺陷、稳定性问题、输出异常，**不纳入**。

## 四、顺带确认的现状（改造前基线）

- `LLM_PROFILE` 仅 2 条：`默认配置`(hy4-preview, is_default=true) 与 `dots3-note-prev`。
- `AGENT_DEFINITION` 7 个内置智能体的 `LLM_PROFILE_ID` **全部为 NULL** —— 即「按智能体分配模型」的字段与解析链早已就绪，只是从未被写入。
- 运行耗时基线：task#4 SINGLE = **554 秒 / 36 次工具调用**（run#116）；task#5 COORDINATOR = **1801 秒 / 86 次工具调用**（run#119）。

## 五、代码侧落地（2026-09-15 完成，尚未实机验收）

代码已完成，闸门 **388 例全绿** + `webui` 自检/构建通过。**但下面这些只在实际重启后才生效**，
因此当前运行中的实例仍是改造前的行为——本文件里的耗时数字都还是基线值。

### 5.1 种子与绑定（重启后自动生效，幂等）

| 环节 | 类 | 行为 |
|---|---|---|
| 建标准档案 | `LlmProfileSeeder`（`@Order(25)`） | 按**名称**幂等创建 `deepseek-flash` / `glm-5.3-flash` 两条档案，`baseUrl`/`apiKey` 从默认档案复制（同一网关同一 key，只差模型名）；不存在才建，不覆盖用户修改 |
| 标记兜底 | 同上 | 完全没有兜底档案时，把**默认档案**（hy4-preview）标为兜底——只做一次，用户指定过就不动 |
| 绑定智能体 | `AgentSeeder`（`@Order(30)`） | `llm_profile_id` **仅在为空时**写入第 3 节的映射；用户改过就尊重用户选择（与 `skill_ids` 同一策略） |

> 为什么兜底取默认档案而不是新建：本部署的默认档案是 hy4-preview（免费、能力 57），
> 天然就是「最稳最便宜」的候选；新建一条同样的档案只会让档案列表多一条重复项。

### 5.2 故障切换（新功能）

档案链由 `LlmProfileService.failoverChain` 解析：**绑定档案 → 默认档案 → 兜底档案 → 其余已启用档案（按 id 升序）**，
去重、跳过未启用/无 key。切换判据是 `AgentInvoker.isModelLevelFailure`——`isPermanent` 的**严格子集**：

| 会切换 | 不切换（及原因） |
|---|---|
| `model_not_found`、`no available channel`、`invalid_api_key`、`401/402/403/404` | `451`/内容审查（换模型同样被拦）；`429`/`concurrent limit`（**账号级**并发，同一网关换模型无效）；任何 `toolCalls > 0`（可能已生图/落库，换模型会重复计费） |

零工具调用的**停滞**也切换（`StageTimeoutException`）：换模型比原地重试更对症，且省下一次白等整个会话超时
（SINGLE 900s / COORDINATOR 1800s）。

### 5.3 验收方式（重启后按此逐条核对）

1. 重启后确认：`LLM_PROFILE.IS_FALLBACK` 列已由 smart-mybatis 补上；标准档案已建
   （~~2 条~~ → **12 条**，见第七节；09-15 时只有 deepseek-flash 与 glm-5.3-flash 可用）；
   7 个内置智能体的 `LLM_PROFILE_ID` 已写入；默认档案 `IS_FALLBACK=1`。
2. **强制故障切换反例**（新功能的确定性证据）：把某个智能体的 `LLM_PROFILE_ID` 临时指向一个不存在的模型名
   → 触发运行 → 日志出现「模型档案不可用（…），切换：A → B」且**整轮仍然成功**。
3. 手工触发 #4（SINGLE）与 #5（COORDINATOR），与 **554s / 1801s** 对照，记录新耗时。
4. 用 `docs/dev/scheduled-task-failure-attribution.md` 里新增的巡检 SQL 第 5~8 条查看：
   按档案统计成功率与耗时、发生过切换的运行、阶段耗时明细、失败类别分布。


---

## 六、⚠️ 修正：本文件的 tok/s 不能外推到工具调用场景（2026-09-16 实测）

第一节「据此修正的分配方案」用**评测表的 tok/s**（deepseek-flash 445 / glm-5.3-flash 153 /
hy4-preview 23.5）当作选型依据。**这个依据对「工具调用为主」的链路是错的。**

定时任务里模型的输出**几乎全是工具参数**（`save_article_draft` 的 `content` 就是整篇文章），
而评测表的 tok/s 测的是**纯正文**场景。实测「让模型调用 `save_article_draft` 写 6000 字」
的真实生成速率（`target/scratch/ToolArgStreamProbe2.java`，把 arguments 的 JSON 反转义后
数**真实字符**——不能数 SSE 行长度，行长度随分片粒度变化，不是可比量）：

| 模型 | 实测生成速率（字符/秒） | 评测表 tok/s | 是否一致 |
|---|---|---|---|
| **deepseek-flash** | **273** | 445 | ✅ 确实是最快的档 |
| **glm-5.3-flash** | 69 / 111 / 125（三次） | 153 | ⚠️ 与 hy4-preview **重叠** |
| **hy4-preview**（免费兜底） | 86 / 77 / 95（三次） | 23.5 | ❌ **严重不符** |

**两个结论**：

1. **hy4-preview 的 23.5 tok/s 不适用于工具参数生成**——实测 77~95 字符/秒，
   与 glm-5.3-flash 的 69~125 区间重叠，样本量下无法区分。按 23.5 推算它应该慢 6 倍，实际不慢。
2. **只有 deepseek-flash 是真正快的档**（273，约 3 倍）。

**对分配方案的影响**：把 writer / reviewer / chief / scheduled_creator 绑到 `glm-5.3-flash` 的
**能力理由（57.5 分最高）仍然成立**，但**不能**再声称这是「速度选择」。
实机验收已印证：SINGLE 换到 glm-5.3-flash 后比基线**慢** 342 秒
（详见 `docs/dev/scheduled-task-reliability-round.md` §4.1 与 §5.3）。

> 教训：**模型速度不能从评测表外推**。要判断「换模型能不能提速」，必须按实际工作负载测。
> 本文件第 40 行那句「真正的吞吐量以评测表的 TPS 为准」在本项目的场景下是不成立的。


---

## 七、复测：换 key 后可用性**整体改变**（2026-09-18）

第二节那张表是 2026-09-15 用**当时那把 key** 测的。2026-09-18 换用新 key 复测，结论大面积反转：
**上一轮判「503 无可用渠道」的模型，本轮全部可用。**

| 模型 | 09-15 结论 | 09-18 复测 | 耗时 |
|---|---|---|---|
| `glm-5.3` | ❌ 503 无可用渠道 | ✅ 200 正常出字 | 1.07s（探针）/ 4.45s（应用口径） |
| `kimi-k3` | ❌ 503 无可用渠道 | ✅ 200 正常出字 | 2.41s |
| `dots3-note-prev` | ⚠️ 多轮记忆失败，排除 | ✅ 200 正常出字 | 1.36s |
| `deepseek-v4-flash-0731` | ❌ 503 | ✅ 200 正常出字 | 0.41s |
| `deepseek-v4-pro-0813` | （未列） | ❌ 400「模型未找到」 | — |
| `kimi-k2.8-preview` | （未列） | ❌ 400 只接受 `temperature=1` | — |
| `step-image-edit-2` | （未列） | ❌ 503 `engine_overloaded` | — |

> **这不是「上一轮测错了」。** 两次都测对了，测的是**不同的渠道组**。
> 教训要记在方法上：**可用性结论必须带「哪把 key / 哪个渠道组」这个前提**，
> 否则文档会互相打脸，而且看不出是谁错了。本文件第二节的结论只对 09-15 那把 key 成立。

### 7.1 复测方法（与第二节的差异）

第二轮用 `target/scratch/probe_nexus_models.py`：先 `GET /v1/models` 拿**网关自报的**模型清单（31 个），
再逐个发最小 chat 请求；图片模型单独打 `/v1/images/generations`。结果 28 通过 / 3 失败。

关键改进：**探针参数按应用真实下发值取**（`temperature=0.70`、`maxTokens=8192`），而不是
探针惯用的 `temperature=0`。`kimi-k2.8-preview` 就是被这个差异抓出来的——它只接受
`temperature=1`，而档案默认下发 0.70，用 0 去测同样会 400，但错误信息会指向参数而非模型能力。

### 7.2 图片模型

`/v1/models` 里 5 个图片模型实测：

| 模型 | 结果 | 返回形态 |
|---|---|---|
| **`sensenova-u1.5-lite`** | ✅ 30.5s | `b64_json`（5.7 MB） |
| `qwen-image-3.0` | ✅ 65.5s | 仅 `url` |
| `qwen-image-3.0-pro` | ✅ 54.7s | 仅 `url` |
| `qwen-image-2.0-pro` | ✅ 69.5s | 仅 `url` |
| `step-image-edit-2` | ❌ 503 | `engine_overloaded` |

选 `sensenova-u1.5-lite` 作图片模型：**返回 `b64_json` 直接落盘**，不依赖第三方图床 URL 的
有效期与可达性（`qwen-image-*` 只回 `url`，多一个外部依赖）；且它比 qwen 系快约 2 倍。
`ImageGenerationService.parseImageResponse` 两种形态都支持，所以换回 qwen 系也不改代码。

### 7.3 据此建立的档案

`LlmProfileSeeder.seeds()` 已同步为这份清单（S/A/B 三档共 12 条，按评测表档位取交集），
默认档案 `deepseek-flash`，兜底档案 `hy4-preview`，图片模型挂在默认档案上。
**有意不建**的模型与理由写在 `seeds()` 的 javadoc 里（实测不可用 + 评测表弱档 + 单次响应 111 秒的
`nemotron-3.5-lightning-free`）。

> 实机验收（2026-09-18）：用这份档案跑通了一次完整 SINGLE 定时任务——
> 38 次工具调用、生成 1 篇文章 + 3 张配图、`status=SUCCESS`，全程无故障切换
> （`profilesUsed: ["deepseek-flash/deepseek-flash"]`、`switchedProfile: false`）。
