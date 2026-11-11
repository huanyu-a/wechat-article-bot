# 全局助手（Global Assistant）实施方案

> 编写时间：2026-11-11。本方案落地「把编辑器里的 AI 助手提升为全站可用的项目助手」，
> 并回答「是否换用 pi-acp 等外部 agent」的选型问题（见 §8）。
> **范围约束（用户明确要求）**：只在本地开发、验证；不推送远端、不部署服务器。

---

## 1. 目标与非目标

### 1.1 目标

一个在**任意页面**都能打开的助手，能用自然语言完成三类项目级操作：

| 类别 | 典型请求 | 落点 |
| --- | --- | --- |
| 文章 | 「把 12 号文章的开头改得更口语」「新建一篇草稿，标题叫 X」 | `ArticleService.updateByAi` / `create` |
| 定时任务 | 「每天早 8 点生成一篇科技早报」「先别跑 3 号任务」 | `ScheduleTaskService` |
| 模型档案 | 「看看网关现在有哪些模型」「把 deepseek-flash 设为默认」 | `LlmProviderService` / `LlmProfileService` |

### 1.2 非目标（刻意不做）

- **不做发布与微信同步**：这两件事有真实外部副作用，必须由人点确认。助手只到「草稿」为止。
- ~~**不提供删除**~~（2026-11-11 已调整）：用户要求「助手对各个功能都可以操作」，删除工具已开放
  （文章软删、任务、智能体、技能、模型档案与供应商）。补齐方式见 §14；安全约束改为
  「破坏性操作前必须复述对象并经用户确认」，由提示词条目 10 与各工具描述约束，而不是靠**没有**这个能力。
- **不在对话里传密钥**：API Key 不进聊天通道（见 §5.4）。
- **不替换编辑器内助手**：原因见 §3.2（两个写者 = 丢更新）。

---

## 2. 现状（源码核验）

| 事实 | 位置 |
| --- | --- |
| 编辑器助手是**文章域**的：整条链路以 `articleId` 为外键 | `ai/ArticleAiController.java:14`（`/api/articles/{articleId}/ai`） |
| 编辑器助手的写工具**跑在浏览器里**（Tiptap 文档为真值） | `ai/ArticleEditorTools.java:36`、`ArticleEditorService` 的 `requestTool`（`ArticleAiService.java:940` 起） |
| 会话序列化按文章存一份 | `ai/ArticleAgentSession.java`（`article_id` 唯一语义） |
| 智能体装配统一走工厂 + 工具组注册表 | `agent/AgentFactory.java`、`agent/ToolRegistry.java` |
| 核心协议是代码常量，人设可编辑 | `agent/AgentProtocols.java`、`agent_definition.persona` |
| 已有并发闸门 / 会话超时 / 换档案重试 | `common/InFlightGate`、`schedule/StageTimeout`、`AgentRunner.runWithCandidates` |
| 权限点：任务 ADMIN/OPERATOR，模型 ADMIN，助手聊天 ADMIN/OPERATOR/EDITOR | `ScheduleTaskController.java:11`、`LlmProfileController.java:23`、`ArticleAiController.java:37` |

**结论**：现有 agent4j 栈已经把「装配、工具注册、协议隔离、超时、并发、换档案、乐观锁、版本快照、审计」
全部做完了。全局助手要做的是**换一个域 + 换一批工具**，不是换一套框架。

---

## 3. 架构

### 3.1 一句话

新增 `/api/assistant/*` 一域：**服务端工具**（不经浏览器） + **按角色裁剪的工具组** + **复用既有 SSE 事件契约**。

```
浏览器（任意页面）
   │  POST /api/assistant/chat  {instruction, context:{path, articleId, ...}}
   ▼
AssistantController ──► AssistantService
                          │ ① 取当前用户与角色 → 解析允许的工具组（fail-closed）
                          │ ② InFlightGate 抢名额（与定时/编辑器共用配额）
                          │ ③ AgentFactory.buildLabeledCandidatesByCode("builtin_assistant")
                          │      工具 = AssistantTools.resolve(groups, 用户, 上下文)
                          │ ④ 会话续接（assistant_session.serialized_session）
                          │ ⑤ SSE 流：state / delta / tool.call / tool.result / completed / error / heartbeat
                          ▼
                     AgentRunner（StageTimeout + 换档案）
                          │ 工具执行（服务端）
                          ▼
        ArticleService / ScheduleTaskService / LlmProviderService / LlmProfileService
```

### 3.2 为什么不把编辑器助手直接「搬」到全局（关键取舍）

编辑器助手的写工具作用于**浏览器里的 Tiptap 文档**，这是刻意的：用户可能正在编辑、有未保存改动，
服务端库里的 HTML 不是当前真值。把它变成全局就意味着「从任意页面写库」，而打开着编辑器的那篇
文章会同时有两个写者（浏览器自动保存 + 全局助手写库），结果是**丢更新**：后写的一方覆盖前者，
乐观锁只能让其中一方报 409，不能避免内容被覆盖。

因此本方案是**并存 + 分工**：

| | 编辑器内助手（保留） | 全局助手（新增） |
| --- | --- | --- |
| 作用域 | 当前这一篇，浏览器文档 | 全项目，服务端库 |
| 写工具 | 浏览器执行（Tiptap 真值） | 服务端执行（带 revision 乐观锁 + 版本快照） |
| 页面 | 仅 `/articles/:id` | 除编辑器外的所有页面（编辑器页隐藏，见下行） |
| 为什么互斥 | 编辑器页隐藏全局助手：避免两个写者同时改同一篇 | — |

用户若希望「在编辑器里也能做项目级操作」，正确做法是后续给编辑器助手加**只读**的项目工具
（列任务、列模型），不要加写工具。

---

## 4. 后端契约

### 4.1 端点

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/assistant/chat` | SSE；body `{instruction, context}`；角色 ADMIN/OPERATOR/EDITOR |
| `GET` | `/api/assistant/messages?limit=50` | 本人的助手对话历史（新的在后） |
| `POST` | `/api/assistant/reset` | 清空本人的智能体会话（开新对话），不影响历史消息 |

SSE 事件与编辑器链路**逐字一致**，前端可复用同一套渲染：
`state{status,message}`、`delta{content}`、`tool.call{callId,name,arguments}`、
`tool.result{callId,name,status,message}`、`completed{message}`、`error{message}`、`heartbeat`。

### 4.2 数据表（smart-mybatis 自动建表）

```sql
ASSISTANT_MESSAGE(id, user_id, role, content, status, prompt_tokens, completion_tokens, created_at)
ASSISTANT_SESSION(id, user_id, serialized_session, updated_by, created_at, updated_at)
```

- 一份会话/用户（`findByUserId`），续接上下文；`reset` 清空。
- 消息表独立于 `ai_message`：后者以 `article_id` 为外键，塞 `NULL` 会让
  `findByArticleId` 语义变模糊（`Where.eq(null)` 的匹配行为不该被赌）。

### 4.3 工具组与工具

| 组键 | 允许角色 | 工具 |
| --- | --- | --- |
| `ASSISTANT_READ` | ADMIN/OPERATOR/EDITOR | `list_articles`、`read_article`、`read_article_blocks`、`list_accounts`、`list_skills`、`list_agents` |
| `ASSISTANT_ARTICLE_WRITE` | ADMIN/OPERATOR/EDITOR | `create_article`、`update_article_meta`、`insert_article_blocks`、`replace_article_blocks`、`delete_article_blocks` |
| `ASSISTANT_TASK` | ADMIN/OPERATOR | `list_schedule_tasks`、`list_task_runs`、`create_schedule_task`、`update_schedule_task`、`set_schedule_task_enabled`、`run_schedule_task_now` |
| `ASSISTANT_MODEL` | ADMIN | `list_llm_providers`、`list_llm_profiles`、`discover_llm_models`、`create_llm_provider`、`update_llm_provider`、`create_llm_profile`、`set_default_llm_profile`、`set_fallback_llm_profile` |

**角色裁剪在代码里，不靠提示词**：`AssistantRolePolicy.groupsFor(role)` 给出允许的组，
装配时取「定义声明的组 ∩ 角色允许的组」；未知角色返回空集（fail-closed，一个工具都不给）。
`agent_definition.tool_keys` 里的 `builtin_assistant` 声明全部四组，角色负责收敛。

### 4.4 文章内容块模型（服务端）

正文按**顶层元素**切块，与编辑器「逻辑行」同一语义：

- 读：`Jsoup.parse(contentHtml)` → `body().children()` → `{index, tag, text, html}`。
- 写：工具拿 `index`/`html` 重建整段 `contentHtml`（`outerHtml` 拼接），走
  `ArticleService.updateByAi(..., "AI 助手（全局）", userId)` → 自动 `updateContent(article, revision)`
  乐观锁 + `snapshot(...)` 版本快照。冲突返回可读错误，要求模型重读。
- **`layoutEngine == MARKFLOW` 的文章拒绝块级写**：正文真值是 `content_markdown` 经渲染服务产出的
  产物，服务端拼 HTML 会与 markdown 失配。只允许改标题/摘要/作者。

### 4.5 护栏

| 护栏 | 取值 | 复用 |
| --- | --- | --- |
| 会话硬超时 | `app.assistant.timeout-seconds`（默认 900） | `StageTimeout` |
| 单轮工具上限 | `app.assistant.max-tool-calls`（默认 30） | 计数在 `onTool` 回调 |
| 用户限流 | 20 次/分钟 | `common/RateLimiter` |
| 并发名额 | 与定时/编辑器共用 | `InFlightGate` |
| 同用户串行 | 按 user_id 加锁 | 编辑器同款 `articleSessionLocks` 思路 |
| 换档案 | 模型级错误且本次零工具调用 | `AgentFactory.buildLabeledCandidates` |

---

## 5. 安全设计

### 5.1 越权

工具实例**按角色构造**：不在角色允许组里的工具根本不存在于 `AgentClient.tools` 里，
模型看不到、也调不到。提示词里再声明一遍只是为了行为可预期，不承担安全职责。

### 5.2 外部副作用

工具清单里**没有**发布与微信同步——这两件事的外部副作用不可撤回，只能由人在页面上确认。

删除类工具**已开放**（§14），约束方式从「能力缺失」改为「行为约束」：
- 工具描述里写明「删除前先向用户确认」；提示词条目 10 再次声明；
- 服务层守住不可删的对象：内置智能体/技能、默认档案、仍有档案挂着的供应商、仍有绑定的档案；
- 每个删除工具都先 `required(id)` 解析对象再删，对不存在的 id 抛业务错误而不是 NPE
  （`everyDeleteToolRejectsUnknownIdsWithoutSideEffects` 固化这一点）。

### 5.3 审计

文章写入经 `ArticleService.updateByAi`，`change_source=AI` + 版本快照；
任务写入经 `ScheduleTaskService`（`@Transactional` + Quartz 重排）；
模型写入经既有服务（会校验供应商/档案引用）。HTTP 层另有 `AuditInterceptor` 记录 POST。

### 5.4 密钥绝不进对话

`create_llm_provider` / `update_llm_provider` **不接受 apiKey 参数**。原因：
对话内容会落 `assistant_message` 表、进 SSE 帧、进模型上下文（第三方）。
需要密钥时返回「请到『模型档案』页填写」的引导文本。

---

## 6. 前端

- 新增 `webui/src/components/GlobalAssistant.vue` + `assistant.css`，挂在 `App.vue` 的非公开、非编辑器路由上。
- 右下角浮动按钮 → 右侧滑出面板；消息区 / 工具卡 / 输入框复用编辑器助手的视觉语言
  （`style.css` 的 `.chat-message`、`.chat-composer` 与 `article-agent.css` 的 `.tool-card`）。
- 每次请求带上上下文 `{path, pageTitle, articleId?}`，让助手知道用户在哪一页。
- 写操作完成后给出「刷新」提示（简单可靠），不做跨组件事件总线。

---

## 7. 验收（本地，不部署）

1. `mvn -o -DskipTests -Dskip.frontend=true compile` 通过。
2. 新增单测：角色裁剪矩阵、块级写工具的乐观锁冲突、MARKFLOW 拒写、密钥参数不存在。
3. 真实 MySQL 测试库集成测试：工具真实落库（建任务/改文章标题）+ 冲突路径。
4. 本机起应用（8081），curl 验证：无 token 401、VIEWER 403（若存在）、EDITOR 仅文章组、
   ADMIN 全量；`/api/assistant/messages` 返回本人历史。
5. 前端 `npm run build` 通过；本地页面打开助手面板截图确认可用。
6. **不执行** `git push`、不构建/部署服务器镜像。

---

## 8. 选型：要不要换 pi-acp / 外部 agent

结论：**不作为本项目助手底座**。理由按证据排序：

| # | 事实 | 影响 |
| --- | --- | --- |
| 1 | pi-acp 是 **ACP（JSON-RPC over stdio）适配器**，宿主是 Zed 这类编辑器；它启动 `pi --mode rpc` 子进程 | 需要一个 Java ACP 客户端 + 每会话一个 Node 子进程；现有 SSE/工具契约全部重写 |
| 2 | 它的工具是**文件系统 + shell**（`edit`/读写文件/执行命令），无 ACP `fs/*`、`terminal/*` 委派 | 多用户 Web 后端把宿主 FS 与 shell 暴露给模型，等于绕过 RBAC/审计/乐观锁 |
| 3 | 它写的是**文件**，不是本项目的数据模型（MySQL + 版本快照 + 微信 API） | 要么让模型直接改库（更加危险），要么另写 MCP 服务暴露业务操作（等于把本方案的工具体再实现一遍） |
| 4 | 需要 Node 22+ 与全局 `pi` CLI、独立模型供应商配置 | 镜像多一层运行时；与本项目「模型档案统一管理」冲突 |
| 5 | 现有栈已内置**线上事故驱动的护栏**：回放守卫、未知工具兜底、工具参数修复、预算、停滞检测 | 换框架意味着把这些真实故障的补丁作废 |

**可选折中（不在本方案内）**：若确实想要「文件系统级编码智能体」，把它作为**独立本地工具**使用
（Zed/终端里跑 pi-acp），不要接进本项目的 Web 助手。两者定位不同：一个是改代码，一个是改内容数据。

---

## 9. 验收记录（2026-11-11，本地实测）

### 9.1 测试

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 后端全量 | `mvn -o -Dskip.frontend=true test` | **685 例，0 失败，0 错误，BUILD SUCCESS** |
| 新增工具真实执行 | `AssistantToolsIntegrationTests`（8 例，真实 MySQL） | 全绿：建文章→读块→改块→回读、乐观锁冲突拒绝、任务开关不清空 cron/prompt、供应商无密钥落停用 |
| 角色裁剪 | `AssistantRolePolicyTest`（13 例） | 全绿 |
| 块引擎 | `AssistantContentBlocksTest`（20 例） | 全绿 |
| 密钥参数 | `AssistantModelToolsSecretTest`（4 例，反射） | 全绿 |
| 前端 | `npm run build` | 通过；`index-*.js` 含 `global-assistant-fab` |

**跑测试的前置条件（本机缺一不可）**：
1. `ENV_MYSQL_TEST_URL / ENV_MYSQL_USERNAME / ENV_MYSQL_PASSWORD` 三个环境变量——`.env` 里**没有** `MYSQL_*`，
   缺了就报 `Could not resolve placeholder 'ENV.MYSQL_TEST_URL'`（`docs/dev/known-issues-handoff.md:2753` 记的同一缺口）。
2. **必须在盘符路径下跑，不能在 `/` UNC 路径下跑**。UNC 路径下 surefire fork 的
   JVM 当前目录会落到 `/mnt/c/Windows`，于是：`EntityColumnDeclarationConsistencyTest` 扫不到实体、
   `AssetDescriptionPersistenceTests` 报 `素材保存失败：/mnt/c/Windows/target`。实测映射盘符（`net use X: /`）后全部通过。

### 9.2 运行时（8082，`wechat-article-smoke` 库）

- 启动 12s 就绪；`GET /api/auth/me` 无 token → 401。
- `GET /api/assistant/messages` → 200 返回本人历史（先 USER 后 ASSISTANT 两条已落库）。
- `GET /api/agents/tool-groups` → **12 组**，其中 `ASSISTANT_*` 4 组、**25 个工具**，名字与 `ToolRegistry` 逐字一致。
- `/api/agents` 里 `builtin_assistant`（墨舟全局助手，stage=ASSISTANT）已落库，声明四组。
- `POST /api/assistant/chat`：SSE 依次收到 `state` → `heartbeat` → `error`。
  本次上游返回 `429 inference exceeds tpm/rpm limit`（模型额度，与代码无关）；
  错误走 `readableError` 落到 `assistant_message`，**已核对文本不含供应商 baseUrl、不含任何密钥**。
- 数据表由 smart-mybatis 自动建出：`ASSISTANT_MESSAGE`、`ASSISTANT_SESSION`；
  `CREATED_AT` 为 `varchar(255)`——与库内其余 **17 张表完全一致**（项目既有约定，非本次引入）。

### 9.3 本轮修正的缺陷（均由测试/实测暴露）

| # | 缺陷 | 证据 | 修法 |
| --- | --- | --- | --- |
| 1 | `delete_article_blocks` **永远失败**：`delete()` 委托 `replace(..., List.of())`，而清洗拒绝空列表 | `AssistantContentBlocksTest` 两例 ERROR | `delete()` 自建重建路径，不再经过替换的清洗 |
| 2 | 授权表比闸门宽：`AssistantRolePolicy` 给 REVIEWER/VIEWER `ASSISTANT_READ`，但 `/chat` 只允许 ADMIN/OPERATOR/EDITOR | 实测 VIEWER `GET /messages` 200 却无法发起对话 | 收敛为与 §4.3 表一致（两类角色空集），并注释「闸门更严时授权表不得更宽」 |
| 3 | `create_llm_provider` 把 `enabled` 兜底成 `true`，无密钥新建必被 `BusinessException` 打死，助手永远建不出「等着补 key」的供应商 | `AssistantToolsIntegrationTests` 实测报 `启用供应商前必须配置 API Key` | 原样透传 `null`（=停用），描述里改为「先建端点→用户补 key→再启用」 |
| 4 | 工具名与注册表不一致会静默丢工具 | `ToolRegistryTest.groupsCoverEveryDeclaredKey` 由 8 组扩到 12 组 | 显式列全，新增组必须同步 |

### 9.4 未做（按约束）

- **未** `git push`、**未**构建/部署服务器镜像、**未**改动正在运行的 8081 容器应用。
- 面板截图未重拍：本机 Chrome DevTools 端口（9222）未开，前端在早前一轮已用真实浏览器走通 SSE 全流程。

---

## 10. 追加：模型选择（2026-11-11 晚）

用户反馈：「我无法知道现在小助手用的哪个模型」。根因不是没实现，而是**默认链不可见**：
`builtin_assistant` 的 `llm_profile_id` 为空，实际按「默认档案 → 兜底 → 其余已启用」挑，
界面上没有任何一行字说明最终选了谁。

### 10.1 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/assistant/models` | 可选项 + 当前选择 + 当前生效 |
| PUT | `/api/assistant/model` | 保存选择；`profileId: null` = 清除，回到自动挑链 |
| POST | `/api/assistant/chat` | body 新增可选 `profileId`（本轮临时指定，优先于保存的偏好） |

返回体**不含**密钥、掩码、baseUrl——这是把列表开给 EDITOR 的前提，有集成测试逐字段断言。

### 10.2 两个 id 的区别（本节的要点）

`selectedId` 与 `currentId` 必须分开，否则界面会撒谎：

- `selectedId`：用户选中的档案，**可能此刻不可用**（先建档案、后补 Key 是正常中间态）。
- `currentId` / `currentLabel`：此刻真正会用的档案，由服务端按链算出。

只给一个 `currentId` 的后果（实测踩到）：选中一条没配密钥的档案后它为 null，下拉框跳回
「自动选择」，用户以为选择没保存，于是反复重选。前端据此显示「已选中的档案当前不可用，暂用 X」。

### 10.3 授权与落库

- 三个接口与 `/chat` 同权限（ADMIN/OPERATOR/EDITOR）。模型**管理**仍只有 ADMIN，
  这里只是「我的助手用哪个」。
- 偏好存新表 `assistant_preference`（一份/用户），**不**复用 `assistant_session`：
  后者会被「新对话」清空，模型选择不该跟着被清掉。
- 也不写 `agent_definition.llm_profile_id`：那是全局配置，一个人换模型会改掉所有人。

### 10.4 链首语义

指定档案 = 当作**绑定档案**，链仍是「所选 → 默认 → 兜底 → 其余已启用」。
用户选了模型不该因此失去「它挂了自动换一个」的能力。指定了却不存在的档案**抛错**
（`所选模型档案不存在或已被删除`），不静默回落——悄悄用 B 跑完是最难排查的不一致。

### 10.5 顺带修的错误文案

原始报文直接进面板，实测长这样：

```
java.lang.RuntimeException: SSE connection failed: HTTP 429: {"message":"inference exceeds
tpm/rpm limit","type":"rate_limit_error","param":"","code":"RateLimitExceeded.EndpointTPMExceeded"}
```

改为按类别给中文 + 下一步动作（限流/鉴权/额度/5xx/超时/地址不可达），未知错误仍原样截断
（保留排查线索，不压成「服务异常」）。切链提示用**短原因**（`供应商暂时不可用`），
因为它外面已经套了「当前模型不可用（…）」，再带括号就是双层括号——这一版改过两次才对。

### 10.6 验收

| 项 | 结果 |
| --- | --- |
| 后端全量 | **700 例，0 失败** |
| `AssistantServiceErrorTextTest`（11） | 全绿：429/401/403/5xx/超时/未知/截断/短原因无括号 |
| `AssistantModelSelectionIntegrationTests`（4） | 全绿：字段白名单无密钥、选择落库、清除回落、非法 id 400 |
| `AgentFactoryTest`（10） | 全绿：指定链首取代绑定、缺失抛错、链尾仍保留兜底 |
| 运行时 | `GET /models` 13 项无密钥；PUT 选 6 → 重新 GET 仍为 6；非法 id → 400 |
| SSE | `state` 带 `model`；切链后 `state.model` 更新为新模型 |
| 前端 | `npm run build` 通过，`index-*.js` 203.15 kB（+3.17，模型选择器） |

**未做**：未 `git push`、未部署。面板截图未重拍（本机 Chrome 调试端口未开）。

---

## 11. 追加：批量添加模型档案（2026-11-11 晚）

起因：用户要把网关 `http://127.0.0.1:3425` 上的 **34 个模型**加进档案，一条条点完才加完，
于是指出「缺少一个批量(多选)添加模型的功能」。

### 11.1 接口

`POST /api/llm-profiles/batch`，body `{providerId, modelNames[]}`：

```json
{ "createdCount": 30, "created": ["a","b"],
  "skipped": [{ "modelName": "x", "reason": "该供应商下已存在同名模型档案" }],
  "failed":  [{ "modelName": "y", "message": "档案名称已存在：y" }] }
```

### 11.2 三个刻意的设计选择

1. **逐条建、逐条记账**。34 个里有一个撞名就整批回滚，等于把用户赶回「一条条点」的老路——
   那正是要消灭的痛点。所以单条失败不影响其余，原因逐条带回。
2. **「已存在」不算失败**。批量操作天然会重复执行（探测两次、点两下按钮）。报成错误会让用户
   以为库里脏了，实际只是幂等。两者分开统计，界面才能说「新增 30，已存在 4」。
3. **名字就用模型名**，不做自动改名。自动加后缀会产生用户没要求过的名字，之后更难对上号；
   名称冲突如实报错，由人决定。单次上限 200，超出要求分批。

### 11.3 界面

档案页标题旁新增「批量添加」→ 选供应商 → 获取可用模型 → 多选列表（已建过的标「已添加」且不可选）
→ 全选/取消 → 「添加选中（N）」。完成后提示「新增 N 个，已存在 M 个」，失败的逐条列出。

### 11.4 同批修的错误文案

用户实测又碰到两类原始报文，都补了映射：

| 原始形态 | 现在 |
| --- | --- |
| `HTTP 429 ... inference exceeds tpm/rpm limit` | 触发了调用频率/额度限制，附下一步动作 |
| `HTTP 200, Socket closed`（工具跑完后流被掐） | 与上游的连接被提前中断，建议重试或换档案 |

### 11.5 验收

| 项 | 结果 |
| --- | --- |
| 后端全量 | **710 例，0 失败** |
| `LlmProfileBatchApiIntegrationTests`（5） | 全绿：一次建多、重复提交幂等、单条失败不连坐、空白/重复归一化、空选择拒绝 |
| `AssistantServiceErrorTextTest`（13） | 全绿，含断流与 5xx |
| 真实服务实测 | 临时供应商建 3 条 → 再提交 skipped=3 → 清理后回到 47 条 |
| 实际加模型 | 网关 34 个模型全部建档（33 新建 + 1 试建），供应商「111」下 34 条、全部运行时可用 |
| 助手可用性 | 新供应商下 `auto/deepseek-flash`、`auto/glm-5.3` 实测能正常回答 |

---

## 12. 工具调用后「Socket closed」的定位过程（2026-11-11）

用户判定「不是模型的问题，是你设计的问题」。这个判断是对的，但**具体是哪一处设计**，
我一开始猜错了两次，记录在这里免得再走一遍。

### 12.1 先被证伪的两个猜测

**猜测 A：上下文被工具结果撑爆。** 实测 47 个模型档案时 `list_llm_profiles`
返回 14,820 字符全字段，确实太大。于是加了 `AssistantToolBudget`（见 §12.2）。
但**修完仍然失败**——所以它不是根因，只是一个独立成立的缺陷。

**猜测 B：网关不接受「工具续话」请求。** 直接拿库里的密钥复刻 `CryptoService`
（AES/GCM，key=SHA-256(secret)，前 12 字节 IV），绕开应用手写四组请求打到网关上：

| 探针 | 报文 | 结果 |
| --- | --- | --- |
| PROBE-1 | 纯文本、不带 tools | 200 |
| PROBE-2 | 纯文本、带 tools | 200 |
| PROBE-3 | assistant tool_calls + tool 消息、非流式 | 200 |
| PROBE-4 | 同上、流式 | 200 |

更进一步，把应用**真实抓到的整份请求体**（26,764 字节、27 个工具、含 tool 结果）原样回放，
也是 200。结论：网关对工具续话完全接受，问题在客户端。

### 12.2 已落地的真实修复：工具输出预算

`AssistantToolBudget`：所有列表型工具返回 `{total, returned, items, truncationHint?}`，
默认 20 条、上限 100 条；`cap()` 兜底把任意超长序列化结果截断到 6000 字符。
理由：模型必须能回答「一共有多少」，所以 `total` 永远全量给出；但行不能全给。
`slim` 后的行从 ~315 字节降到 ~90 字节。回归用例
`AssistantToolsIntegrationTests.listLlmProfilesOutputIsBoundedEvenWithManyProfiles`
断言单次输出 < 6000 字符。

### 12.3 失败的真实位置（已定位到层，未定位到行）

在**本机自建假网关**上能稳定复现（不经过用户的隧道），因此可以排除网络与供应商：

```
state -> delta -> tool.call -> tool.result -> error(Socket closed)
```

堆栈（加临时日志抓取）：

```
RuntimeException: SSE connection failed: HTTP 200, Socket closed
  at OpenAIChatModel$1.onFailure(OpenAIChatModel.java:261)
  at okhttp3.internal.sse.RealEventSource.processResponse
Caused by: java.net.SocketException: Socket closed
  at sun.nio.ch.NioSocketImpl.ensureOpenAndConnected
  at okio.AsyncTimeout$source$1.read
```

`Socket closed` 是**本地关闭**信号，不是对端断开。已排除的因素：

- 换模型（deepseek-flash / glm-5.3）→ 同样失败，与模型无关；
- 关掉 `ReplayWireNormalizer` / `UnknownToolFallbackMap` / `ToolCallArgumentGuard`
  三个兜底 → 仍然失败；
- 假网关在发完 `[DONE]` 后挂住 8 秒不关连接 → 仍然失败；
- 假网关侧无任何 BrokenPipe/异常，说明响应数据写成功了。

结论：agent4j 2.3.3 在一次 `command()` 内的「工具轮次 → 续话」这一步，
会在读取第二个 SSE 流时把连接关掉。现象与「同一 `OkHttpClient` 上两个 `RealEventSource`
生命周期重叠」一致，但尚未拿到决定性证据。

### 12.4 顺带修掉的两个观感缺陷

1. **错误文案指向错误方向**：原文案说「上下文过大或网关超时」，把用户引向换模型这种
   无效动作。改为说明这是连接层问题，并明确「不是模型能力或上下文大小的问题」。
2. **面板显示 `上游连接被中断`**：`上游` 一词把责任推给供应商；改为 `连接被中断`。

### 12.5 遗留

- agent4j 工具轮次的连接关闭需要下一轮专门处理（自有工具循环，或等待库修复）。
- 连接级瞬断现在会**原地重试一次**（`isTransientConnectionFailure`，仅在本轮零工具调用时，
  避免重复写入）；工具轮次的失败不属于「零副作用」，因此不会被掩盖成成功。

---

## 13. 解决：升级 agent4j 2.3.3 → 2.4.0（2026-11-11）

§12 定位到「工具轮次 → 续话」这一步在客户端被关闭连接，但三个兜底层都够不着。
按「升级/修补」的方向查 Maven Central，`ink.icoding.llm:agent4j` 已有 2.3.4 ~ 2.4.0。

### 13.1 升级可行性的三项确认

| 检查项 | 结论 |
| --- | --- |
| 公开 API 差异 | `LLMModel` / `AgentClient` / `AgentClientSession` / `AgentSessionResult` / `Tool` / `ToolExecutor` / `ResultHandler` 签名**逐一比对，完全相同** |
| 依赖差异 | jackson-databind 2.21.4、okhttp / okhttp-sse 4.12.0，与 2.3.8 一致，无新增传递依赖 |
| 编译 | 只改版本号，`test-compile` 直接通过 |

因为改动面只有一个 `<version>`，属于可回退的最小升级：出问题把版本号改回 2.3.3 即可。

### 13.2 验证结果

同一条**原始故障指令**（`当前有多少个模型？`，档案 28），升级前 `tool.result` 之后必现
`Socket closed`；升级后：

```
state → heartbeat → delta → tool.call → tool.result → completed
```

回答正确：「共 47 个模型档案，分布在 2 个供应商下……」。另外补验四种情形，全部通过：

| 场景 | 结果 |
| --- | --- |
| deepseek-flash 单轮工具调用 | ✅ |
| 同会话追问（历史更长、需要翻页看完整列表） | ✅ 并正确转述了截断提示 |
| 换模型 glm-5.3 走工具（查定时任务） | ✅ |
| 不带工具的纯对话 | ✅ |
| **写操作**：让助手建一个定时任务 | ✅ 三次工具调用全部 COMPLETED，库里确认 `name/cron=0 0 9 * * ?/enabled=false/executionMode=SINGLE` 与指令一致 |

全量测试 **718 项、0 失败**（升级前基线 710 项 + 本轮新增 8 项）。

### 13.3 三处兜底在 2.4.0 上逐个复验（结论：全部保留）

**方法**：给 `AgentFactory` 加临时开关，每次只关一层，用假网关（可模拟正常 / 未知工具名 /
非法 JSON 参数 / 一轮两条工具调用）跑完整工具轮，并检查**真实出站报文**而不只是看是否成功。

| 场景 | 结果 | 报文检查 | 结论 |
| --- | --- | --- | --- |
| A 全开（基准） | 完成 | id 齐全、对齐、arguments 合法 | — |
| B 关回放兜底 `ReplayWireNormalizer` | 完成 | 与 A **完全一致** | 当前版本未触发，保留作防御 |
| C 关参数兜底 `ToolCallArgumentGuard` | 完成 | 合法 | 正常路径不需要 |
| D 未知工具 / 兜底开 | 完成 | — | 代答生效 |
| E 未知工具 / 兜底关 | **失败** | — | **必需**：`NullPointerException` |
| F 坏参数 / 兜底开 | 完成 | arguments 合法（被归一化成 `{}`） | — |
| G 坏参数 / 兜底关 | 完成 | **arguments 非法 JSON 原样发上网关** | **必需**：严格网关会整轮 400 |

补充边界：一轮返回**两条**工具调用时，关掉回放兜底后报文仍是
`tool_calls[0].id=call_mock_1 / [1]=call_mock_2`，与两条 `tool` 消息的 `tool_call_id` 逐一对齐，
字节数与开启时相同——2.4.0 已不再产生「一轮里最后一个工具调用缺 id」的老毛病。

**最终决定**：

- `UnknownToolFallbackMap`、`ToolCallArgumentGuard` —— **保留，证据充分**（关掉就退回到 2.3.3 的故障形态）；
- `ReplayWireNormalizer` —— **保留**，但性质变了：它现在是一层「无违规即原样放行」的防御
  （`intercept` 里 `normalized.equals(original)` 直接 `proceed`），代价仅一次 JSON 解析，
  删掉就失去对「模型又回放出缺 id 历史」的兜底。

三处复验结论已写进各自类的 Javadoc，避免以后重复验证。临时开关已删除，`AgentFactory` 恢复原状。

---

## 14. 追加：助手覆盖全部功能（2026-11-11 深夜）

### 14.1 起因

用户让助手「把智能体的模型都改成 111 供应商的」，助手回答「工具里只有查看智能体，没有修改绑定
的接口」，并让用户自己去页面点。用户的原话是：**「这都干不了么」**。

这不是模型能力问题：`AgentDefinitionService` 早就有 `update`，缺的是**一个只改绑定的工具**。
顺着这条线做了一次覆盖审计，把「页面能点、助手不能做」的缺口一次补齐。

### 14.2 缺口审计（改造前）

| 领域 | 改造前 | 缺口 |
| --- | --- | --- |
| 文章 | 建、改元信息、增删改内容块 | 修订列表、回滚版本、重排版、删除 |
| 智能体 | 仅列表 | 详情、新建、修改、克隆、删除、**换模型绑定** |
| 技能 | 仅列表 | 详情、新建、修改、克隆、删除 |
| 定时任务 | 建、改、启停、立即跑 | 删除 |
| 模型档案 | 建供应商、建档案、设默认/兜底、发现模型 | 改档案、删档案、删供应商 |

补齐后共 **42 个工具**，注册在四个组里（组键不变，见下）。

### 14.3 为什么不新开工具组

组键（`ASSISTANT_READ` 等）**已经持久化在 `agent_definition.tool_keys` 里**——内置助手定义就是
这么写的。改键名等于让库里已有的授权一夜之间失配（表现是「助手突然什么都不会了」）。

所以 `ASSISTANT_MODEL` 的键保持不变，扩的是它**管什么**：从「模型档案」扩为「系统配置」
（模型供应商/档案 + 智能体 + 技能）。组内工具名清单在 `ToolRegistry`，角色裁剪仍在
`AssistantRolePolicy`——ADMIN 四组、OPERATOR 三组（无配置组）、EDITOR 两组。

### 14.4 新增工具

**智能体**（`AssistantAgentTools`）：`read_agent`、`create_agent`、`update_agent`、`duplicate_agent`、
`delete_agent`、`set_agent_model`。

**技能**（`AssistantSkillTools`，新类）：`read_skill`、`create_skill`、`update_skill`、
`duplicate_skill`、`delete_skill`。

**文章**（并入 `AssistantArticleTools`）：`list_article_revisions`、`rollback_article_revision`、
`rerender_article`、`delete_article`。

**定时任务**：`delete_schedule_task`。**模型**：`update_llm_profile`、`delete_llm_profile`、
`delete_llm_provider`。

### 14.5 两条设计约束（沿用，未放松）

1. **密钥仍不进对话**：`create_llm_provider` / `update_llm_provider` 依旧没有 `apiKey` 参数，
   遇到需要密钥就引导用户去页面填。`AssistantModelToolsSecretTest` 继续用反射守着，
   只放行 `maxTokens`（生成长度上限，与凭据无关）。
2. **「只改一件事」的参数**：`set_agent_model` 只有 `agentId` + `profileId`；
   `update_agent` / `update_skill` / `update_llm_profile` 都是「只覆盖传了的字段」。
   服务层那几个 `update` 是**全量覆盖**（名称、人设、工具组必填），如果工具走
   「读全量 → 整体回写」，漏读一个字段就会把它静默清空。这一点由
   `setAgentModelChangesOnlyTheBinding` 逐字段断言（人设/工具组/技能/温度必须与改前相等）。

### 14.6 提示词同步

工具补齐还不够：提示词里原来写着「没有提供的操作（发布、同步微信、删除文章或任务）就是做不到」，
模型会照着这句话拒绝。`AgentProtocols.ASSISTANT` 已改为：

- 明确列出各领域**能做什么**，并要求「动手前先用 list/read 拿 id，不要凭印象说『我无法修改』」；
- 只剩两类真的做不到：需要填密钥/AppSecret、实际发布到公众号；
- 新增第 10 条：破坏性操作必须先复述对象并得到确认。

### 14.7 验证

- `AssistantModelToolsSecretTest`：工厂工具清单与注册表一致、参数类无密钥字段（11 个参数类）。
- `AssistantToolsIntegrationTests`：新增 `setAgentModelChangesOnlyTheBinding`、
  `setAgentModelCanUnbindWithNullProfile`、`everyDeleteToolRejectsUnknownIdsWithoutSideEffects`。
- 全量 **726 项、0 失败**。
- 端到端：让助手把 8 个智能体全部改到 111 供应商，10 次工具调用后核对——
  1/2/3/5/8 号 → 档案 28（auto/deepseek-flash），4/6/7 号 → 档案 31（auto/glm-5.3-flash），
  无一遗留旧绑定。
