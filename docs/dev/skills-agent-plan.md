# 墨舟 Skills + 多 Agent 架构开发方案

> 状态：**四期全部实施完成（第①期 2026-09-09、第②③④期 2026-09-09，见文末实施记录）**
> 日期：2026-09-08
> 范围：为墨舟（wechat-article-bot）增加「Skill 技能包」与「多 Agent 协作」能力
> 说明：本文档是完整开发方案，按四期交付；每期动工以明确指示（如「开始第①期」）为准。

---

## 目录

1. [背景与目标](#1-背景与目标)
2. [现状分析](#2-现状分析)
3. [总体架构设计](#3-总体架构设计)
4. [数据模型设计](#4-数据模型设计)
5. [后端详细设计](#5-后端详细设计)（含 5.10 MarkFlow 排版引擎）
6. [前端设计](#6-前端设计)
7. [四期交付计划](#7-四期交付计划)
8. [测试与质量门禁](#8-测试与质量门禁)
9. [风险与对策](#9-风险与对策)
10. [附录A：内置 Skill 种子清单](#附录a内置-skill-种子清单)
11. [附录B：内置 Agent 种子清单](#附录b内置-agent-种子清单)

---

## 1. 背景与目标

### 1.1 用户原始构想

> 我要给这个项目增加 skills 与 agent 功能：
>
> - **skills**：可以创建多个 skill，来控制文章风格、图片风格、文章排版等等，可以扩充维度
> - **agent**：不同 agent 负责不同的工作，当前主 agent 负责协调其他不同 agent 来完成文章创作

### 1.2 已确认的产品决策


| 决策点            | 结论                                                                                                                   |
| ----------------- | ---------------------------------------------------------------------------------------------------------------------- |
| 多 Agent 调度模式 | **双模式**：流水线（PIPELINE，代码固定编排）+ 协调者（COORDINATOR，主 Agent 自主调度），外加保留现状单智能体（SINGLE） |
| Skill 内容形态    | **自由文本指令 + 维度标签**（不做结构化表单）                                                                          |
| 覆盖范围          | **完整四期**：Skill 体系 → Agent 定义体系 → 定时链路多 Agent 协作 → 编辑器链路集成                                  |

### 1.3 设计目标

1. **风格可配置**：文章风格、图片风格、排版模板等以「Skill」形式由用户创建、组合、绑定，替代硬编码 prompt。
2. **Agent 可视化管理**：角色（调研/写作/配图/审核/协调）以数据定义（人设 + 工具组 + 默认 Skill + 模型档案），内置 7 个，可克隆可改不可删。
3. **多 Agent 协作落地**：定时创作支持三种执行模式；编辑器对话获得子 Agent 调研能力。
4. **成本可控**：不同 Agent 可绑定不同档位的模型档案（如调研用廉价模型、写作用主力模型）；流水线模式无协调层 LLM 开销。
5. **存量兼容**：所有新列可空/带默认值；未配置任何新功能的存量任务、公众号、文章行为与现状完全一致。

---

## 2. 现状分析

以下结论来自对当前代码库（main 分支，commit 78f560f）的完整探索，是本方案的事实基础。

### 2.1 技术栈

- 后端：Java 17、Spring Boot 4.1、Spring Security、smart-mybatis（实体注解自动建表/同步 DDL）、Quartz JDBC 集群、agent4j 2.3.3（`ink.icoding.llm:agent4j`，提供 `AgentClient`/`AgentClientSession`/`Tool`/`LLMModel` 等抽象）
- 前端：Vue 3.5（`<script setup>`，无 TS）、Vite 8、Pinia、TipTap 3、原生 fetch 封装（`webui/src/api.js`），**无 UI 组件库、无 i18n**（中文硬编码）
- 存储：MySQL 8 + 本地文件（uploads 卷）

### 2.2 文章生成的两条链路

没有传统「多阶段 prompt 流水线」，而是两个 **agentic 工具循环**，核心都在 `src/main/java/ink/icoding/wechat/article/ai/ArticleAiService.java`：

**A. 交互式编辑（人在环中，SSE）**

- 入口：`POST /api/articles/{id}/ai/chat`（`ArticleAiController.chat`）
- `ArticleAiService.chat()`（:154）→ 异步 `executeWithAgentSession()`（:212）→ `createArticleAgent()`（:298）`new AgentClient()`，name「墨舟微信公众号文章编辑智能体」，description = `AGENT_DESCRIPTION`
- 工具集 = 7 个浏览器编辑工具（`ArticleEditorTools.all()`：read_article/read_blocks/delete_blocks/insert_blocks/replace_blocks/update_metadata/update_cover）+ 7 个媒体工具（`ArticleMediaTools.create()`）
- **编辑工具在用户浏览器前端执行**：服务端经 SSE 下发 `tool.call` 事件，前端执行后回传 `POST .../sessions/{sessionId}/tools/{callId}/result`（90 秒超时）；单轮最多 24 次工具调用（`MAX_TOOL_CALLS`）
- 会话状态序列化在 `article_agent_session` 表（MEDIUMTEXT，跨请求多轮记忆）；完成后 `commitSession()` 落库并生成版本快照

**B. 定时/无人值守创作**

- 触发：Quartz（`ScheduleTaskJob` → `TaskExecutionService.execute()`）或手动 `POST /api/tasks/{id}/run`（runAsync 后台执行）
- `ArticleAiService.runScheduledAgent()`（:369）`new AgentClient()`「墨舟定时文章创作智能体」，description = `SCHEDULED_AGENT_DESCRIPTION`
- 工具集 = 3 个服务端草稿工具（`ScheduledArticleTools.all()`，纯内存 `DraftState` 工作区）+ 7 个媒体工具
- 用户指令由模板拼接：当前时间/目标公众号/outputMode 交付约束/任务 `aiPrompt`
- 成功后 `TaskExecutionService` 按 outputMode（LOCAL_DRAFT/WECHAT_DRAFT/AUTO_PUBLISH）落库交付

### 2.3 Prompt 现状（本方案的核心改造对象）

三个硬编码 Java 文本块常量，全部在 `ArticleAiService.java`：


| 常量                          | 行号     | 内容                                                                                                                  |
| ----------------------------- | -------- | --------------------------------------------------------------------------------------------------------------------- |
| `ARTICLE_STYLE_GUIDE`         | :55-88   | 公众号正文视觉模板（绿色 #07C160 章节号/左边线、16px/1.9 行高、figure 配图、禁 ul/ol/dl/table、含完整 HTML 结构示例） |
| `AGENT_DESCRIPTION`           | :89-108  | 编辑智能体 15 条规则 + 拼接视觉模板                                                                                   |
| `SCHEDULED_AGENT_DESCRIPTION` | :109-120 | 定时创作智能体 7 条规则 + 拼接视觉模板                                                                                |

配套约束：`ArticleContentPolicy.requireParagraphProse()`（`article/ArticleContentPolicy.java:14-20`）在 insert/replace/save 工具入口**强制拒绝** ul/ol/dl/table，与 prompt 规则双保险——排版模板承担引导模型产出纯段落 HTML 的职责，不可缺失。

### 2.4 现有「Agent」与「风格」概念

- **没有** AgentType/AgentRole 等类型体系；两个匿名 agent 现场构造，靠 name/description/工具集区分
- 两个 prompt 明确写着「不要创建计划或子智能体」（:104、:119）——当前设计刻意排除了多 agent 编排，本方案将解除此限制并引入受控协作
- `wechat_account.default_style` 字段已存在且账号管理页可编辑（前端「默认风格」文本框），但**未被任何 prompt 拼装使用**（未接线状态）
- 图片生成：`ImageGenerationService`（OpenAI 兼容 `/v1/images/generations`，尺寸硬编码 1024x1024）；风格仅靠工具描述引导模型自拟 prompt，系统不注入图片风格配置

### 2.5 配置与调度机制

- `llm_config` **单行表**（provider/baseUrl/modelName/加密 apiKey/temperature/maxTokens + 独立图片模型三件套），`LlmConfigService.runtime()`（`settings/LlmConfigService.java:68-78`）每次 agent 运行实时读取——**DB 改配置立即生效**
- API Key 经 `CryptoService`（AES，密钥 = `APP_SECRET_KEY`）加密入库，API 只返回掩码
- 建表方式：smart-mybatis `@TableName/@TableField/@SmartMeta` 注解自动同步（`application.yaml: mybatis.smart.auto-sync-db: true`），**无 Flyway/Liquibase**，加表加列只需新增实体/字段
- Quartz JDBC 集群模式，应用启动时 `QuartzTaskManager.restore()` 重注册 enabled 任务
- 前端 11 个视图（Dashboard/Accounts/Articles/ArticleEditor/Tasks/Followers/SystemUsers/Assets/Audit/Settings/Login），侧边栏在 `App.vue`，路由在 `router.js`

### 2.6 对本方案的关键推论

1. Skill 注入的天然入口是两处 `new AgentClient()` 的 description 组装 → 抽象为 `SkillPromptAssembler`
2. `ScheduledArticleTools.DraftState` 已验证「服务端工作区 + 工具集」模式 → 扩展为多阶段共享的 `TaskWorkspace`
3. smart-mybatis 自动 DDL → 新表/加列成本低，但 JSON 字段统一用 TEXT 存 JSON 字符串、代码层解析（对 JSON 列类型支持不确定）
4. `llm_config` 单行模式 → 扩展为多行「模型档案」，保留单行 API 兼容
5. 权限已有 5 级角色 + `@PreAuthorize` 体系 → 直接沿用

---

## 3. 总体架构设计

### 3.1 架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                              管理层（前端）                           │
│  SkillsView(技能库)   AgentsView(智能体)   SettingsView(模型档案)     │
│  TasksView(执行模式+阶段编排)  AccountsView(默认Skill)  编辑器(Skill) │
└──────────────┬──────────────────────────────────────────────────────┘
               │ REST API
┌──────────────▼──────────────────────────────────────────────────────┐
│                              后端                                    │
│                                                                      │
│  skill 包                    agent 包                                │
│  ┌──────────────┐   ┌──────────────┐   ┌────────────────────┐       │
│  │ SkillService │   │ AgentDefini- │   │ ToolRegistry       │       │
│  │ SkillSeeder  │   │ tionService  │   │ (工具键→分组→构造器)│       │
│  │ SkillPrompt- │   │ AgentSeeder  │   └─────────┬──────────┘       │
│  │ Assembler    │   │ LlmProfile   │             │                  │
│  └──────┬───────┘   │ AgentFactory │◄────────────┘                  │
│         │           └──────┬───────┘                                │
│         │                  │ 按 AgentDefinition 装配                 │
│         ▼                  ▼                                        │
│  ┌──────────────────────────────────┐                                │
│  │ agent4j AgentClient              │                                │
│  │ (persona + 核心协议 + Skill注入)  │                                │
│  └──────────────────────────────────┘                                │
│                                                                      │
│  schedule 包（执行策略）                                             │
│  ┌─────────────┐ ┌──────────────────┐ ┌─────────────────────┐       │
│  │ SingleAgent │ │ PipelineExecutor │ │ CoordinatorExecutor │       │
│  │ Executor    │ │ 代码固定编排      │ │ chief + delegate工具 │       │
│  │ (现状迁移)  │ │ 无协调LLM开销     │ │ 主Agent自主调度      │       │
│  └─────────────┘ └────────┬─────────┘ └──────────┬──────────┘       │
│                          ▼                        ▼                  │
│                 ┌────────────────────────────────────┐               │
│                 │ TaskWorkspace（共享工作区）        │               │
│                 │ 调研笔记 / 草稿 / 审核轮次 / 素材  │               │
│                 └────────────────────────────────────┘               │
└──────────────────────────────────────────────────────────────────────┘
```

### 3.2 三个核心概念

**Skill（技能包）** = 维度标签 + 一段完整自然语言指令。

- 例：「干货教程体」（维度=写作风格，内容="语气口语化但不轻浮；每章聚焦一个可上手执行的要点；多用具体数字和案例；开头直接给出『读完能获得什么』……"）
- 绑定层级（解析优先级从高到低，取并集去重）：**文章 article.skillIds > 定时任务 task.skillIds > 公众号 account.skillIds**
- 按维度分组注入系统提示；「排版模板」维度有保底规则（见 5.2）
- **排版引擎双轨**（2026-09-08 增补）：LAYOUT 维度的 Skill 声明排版引擎——`PROMPT`（指令式：Skill 文本即版式指令，Agent 直接产出内联样式 HTML，默认）或 `MARKFLOW`（渲染式：Agent 产出 MarkFlow 语法 Markdown，由 MarkFlow 渲染服务转成 HTML，详见 5.10）

**Agent（智能体定义）** = 可视化配置的一条 `agent_definition` 记录：

- **人设 persona**（可编辑，DB 存储）+ **核心协议**（代码内置常量，不可编辑：工具纪律、乐观锁、图片先入素材库等）分离，防止用户改人设破坏工具调用协议
- 工具组勾选（tool_keys，经 ToolRegistry 校验）
- 默认 Skill 集（skill_ids）+ 模型档案（llm_profile_id，可空=默认档案）+ temperature/maxTokens 覆盖
- 内置 7 个（is_builtin=true，不可删、可克隆、可改），按 stage 分类

**执行策略（ExecutionMode）** = 定时任务新增维度：


| 模式          | 说明                                                             | 成本结构                |
| ------------- | ---------------------------------------------------------------- | ----------------------- |
| `SINGLE`      | 单智能体，现状逻辑迁移（默认值，存量兼容）                       | 与现状相同              |
| `PIPELINE`    | 代码固定编排：调研→写作→配图（可选）→审核（可选，不过则返工） | 无协调层开销            |
| `COORDINATOR` | chief 智能体 + 4 个 delegate 工具，自主决定顺序/次数/返工        | 多一层主 Agent LLM 调用 |

### 3.3 Skill 维度白名单（可扩充）


| 维度 key     | 显示名   | 说明                                                                                           |
| ------------ | -------- | ---------------------------------------------------------------------------------------------- |
| `AUDIENCE`   | 目标读者 | 读者画像、知识水平、阅读场景                                                                   |
| `TOPIC`      | 选题策略 | 选题方向、热点偏好、领域边界                                                                   |
| `WRITING`    | 写作风格 | 语气、句式、人称、篇幅节奏                                                                     |
| `LANGUAGE`   | 语言习惯 | 用词偏好、禁用词、术语规范                                                                     |
| `TITLE`      | 标题风格 | 标题公式、长度、悬念/直白                                                                      |
| `OPENING`    | 开头写法 | 引言/提问/故事等开头模式                                                                       |
| `ENDING`     | 结尾写法 | 收束语、行动号召                                                                               |
| `DIGEST`     | 摘要风格 | 公众号摘要的写法                                                                               |
| `IMAGE`      | 图片风格 | 配图审美、生图 prompt 风格、图注习惯                                                           |
| `LAYOUT`     | 排版模板 | 正文版式（**保底维度**；双引擎：PROMPT 指令式 / MARKFLOW 渲染式，同一时刻仅一个生效，见 5.10） |
| `FACT_CHECK` | 事实核查 | 来源要求、引用规范                                                                             |
| `OTHER`      | 其他     | 自由扩展                                                                                       |

注入顺序按上表从上到下，但 LAYOUT（排版模板）刻意排到最后、紧邻生成指令——FACT_CHECK 与 OTHER 注入在 LAYOUT 之前（2026-09-08 审查修订：原「排版模板最后」与表中 LAYOUT 之后仍有两维度自相矛盾）。

---

## 4. 数据模型设计

### 4.1 新表 `skill`


| 字段                                 | 类型         | 约束/说明                                                                                                                                                           |
| ------------------------------------ | ------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| id                                   | BIGINT       | 主键自增                                                                                                                                                            |
| name                                 | VARCHAR(100) | 唯一（含内置）                                                                                                                                                      |
| dimension                            | VARCHAR(30)  | 维度 key，白名单校验                                                                                                                                                |
| description                          | VARCHAR(500) | 一句话说明（管理界面展示）                                                                                                                                          |
| content                              | TEXT         | 自由文本指令（prompt 主体）                                                                                                                                         |
| engine                               | VARCHAR(20)  | 排版引擎：`PROMPT`（默认）/ `MARKFLOW`；仅 LAYOUT 维度生效，其他维度忽略该字段                                                                                      |
| engine_config                        | TEXT (JSON)  | 引擎配置；MARKFLOW 时：`{"accentMode":"AUTO","accent":"#27ae60","dark":"#1e8449"}`（accentMode=AUTO=Agent 按内容主题自选主题色；FIXED=固定本 Skill 的 accent/dark） |
| enabled                              | BOOLEAN      | 默认 true；停用后绑定处不注入                                                                                                                                       |
| is_builtin                           | BOOLEAN      | 内置标记                                                                                                                                                            |
| builtin_key                          | VARCHAR(50)  | 唯一可空；Seeder 幂等键                                                                                                                                             |
| created_by / created_at / updated_at |              | 沿用现有实体惯例                                                                                                                                                    |

### 4.2 新表 `agent_definition`


| 字段                                 | 类型         | 约束/说明                                                                                         |
| ------------------------------------ | ------------ | ------------------------------------------------------------------------------------------------- |
| id                                   | BIGINT       | 主键自增                                                                                          |
| code                                 | VARCHAR(50)  | 唯一；程序引用（如`builtin_editor`）                                                              |
| name                                 | VARCHAR(100) | 显示名（如「墨舟编辑智能体」）                                                                    |
| stage                                | VARCHAR(30)  | `EDITOR` / `SCHEDULED_SINGLE` / `RESEARCH` / `WRITING` / `ILLUSTRATION` / `REVIEW` / `COORDINATE` |
| persona                              | TEXT         | 人设 prompt（可编辑）                                                                             |
| tool_keys                            | TEXT         | JSON 数组字符串，如`["MEDIA","DRAFT_READ","DRAFT_WRITE"]`，保存时经 ToolRegistry 校验                       |
| skill_ids                            | TEXT         | JSON 数组字符串；该 Agent 的默认 Skill（与任务/账号/文章 Skill 并集注入）                         |
| llm_profile_id                       | BIGINT       | 可空；空 = 默认模型档案                                                                           |
| temperature                          | DECIMAL      | 可空；空 = 用档案值                                                                               |
| max_tokens                           | INT          | 可空；空 = 用档案值                                                                               |
| enabled                              | BOOLEAN      | 默认 true                                                                                         |
| is_builtin                           | BOOLEAN      | 内置标记                                                                                          |
| builtin_key                          | VARCHAR(50)  | 唯一可空；Seeder 幂等键                                                                           |
| created_by / created_at / updated_at |              | 惯例字段                                                                                          |

### 4.3 新表 `llm_profile`（模型档案）


| 字段                                 | 类型         | 约束/说明                                                                |
| ------------------------------------ | ------------ | ------------------------------------------------------------------------ |
| id                                   | BIGINT       | 主键自增                                                                 |
| name                                 | VARCHAR(100) | 显示名（如「主力-深度推理」「廉价-批量调研」）                           |
| provider                             | VARCHAR(50)  | `OPENAI_COMPATIBLE` / `OPENAI_RESPONSES` / `ANTHROPIC`（沿用现有白名单） |
| base_url                             | VARCHAR(500) | 归一化逻辑复用`LlmConfigService`                                         |
| model_name                           | VARCHAR(200) |                                                                          |
| image_model_name                     | VARCHAR(255) | **可空**：该档案要用的图片模型。为空 = 本档案不指定，配图回落 `llm_config.IMAGE_MODEL_NAME`（2026-09-16 增补，见 4.3.1） |
| api_key_encrypted                    | TEXT         | AES 加密入库，API 返回掩码                                               |
| temperature / max_tokens             |              | 可空                                                                     |
| enabled                              | BOOLEAN      |                                                                          |
| is_default                           | BOOLEAN      | 全局唯一一条默认档案（应用层保证）                                       |
| is_fallback                          | BOOLEAN      | 全局唯一一条兜底档案（应用层保证，2026-09-16 增补）                       |
| created_by / created_at / updated_at |              | 惯例字段                                                                 |

**与现有 `llm_config` 的关系（重要，兼容设计）：**

- 启动 Seeder（`LlmProfileMigrationRunner`）检测：`llm_profile` 表为空且 `llm_config` 有数据 → 将该行迁移为「默认档案」（name="默认配置"，is_default=true）
- `LlmConfigService.runtime()` **签名不变**，内部改读默认档案 → 现有调用点零改动
- 存量 `GET/PUT /api/settings/llm` API 保留，读写映射到默认档案 → 前端现有设置页**不破坏**
- 图片模型三件套（imageBaseUrl/imageModelName/imageApiKey）**继续留在 `llm_config`**（避免图片配置的迁移面）；档案侧只增一个**可空**的 `image_model_name` 作为覆盖项，见 4.3.1

#### 4.3.1 图片模型：档案覆盖 + 全局兜底（2026-09-16 增补）

**要解决的问题**：配图师（`builtin_illustrator`）卡片上显示的模型档案是「默认配置」，用户据此以为配图用的是 hy4-preview —— 而实际生成图片的模型是全局设置里的 `step-image-edit-2`。两者都没错，但界面上**没有任何地方**表达「这个智能体的配图模型是哪个」。本节把图片模型变成档案的一个可选项，让「按智能体配图片模型」成为可能。

**解析顺序**（`LlmProfileService.imageCarrier` + `LlmConfigService.imageRuntime`）：

```
沿故障切换链找第一个声明了 image_model_name 的档案（绑定 → 默认 → 兜底 → 其余已启用）
  找到 → 用它的 baseUrl + image_model_name + apiKey（三者同源，不跨供应商拼接）
  没找到 → 回落 llm_config 的图片三件套（= 改造前的唯一来源，存量行为逐字不变）
```

**三条设计约束**：

1. **只加模型名，不加配套的 baseUrl/apiKey**：图片三件套里只有模型名是「换个模型」这一诉求的载体；端点和密钥沿用全局（同一网关、同一个 key），多带两列只会扩大迁移面。
2. **可空是硬要求**：存量档案补列后全是 NULL，而 NULL 正是「本档案不指定」的表达。列若变成 NOT NULL、或字段写成基本类型，存量库补列/读取会直接失败。由 `LlmProfileImageModelColumnPersistenceTests` 打真库钉住。
3. **全局设置仍是兜底而不是被取代**：存量部署的图片模型只配在全局里，若档案一出现就无视全局，升级当天所有配图都会失败。
4. **`image_model_name` 字段名跨两张表共用**（`LlmConfig` / `LlmProfile`）：两处**都不得**加 `@TableField(length=...)`，否则 smart-mybatis 的字段名级列声明缓存会任选一份、并对真实列发 `MODIFY COLUMN`。由 `EntityColumnDeclarationConsistencyTest` 钉住。

**透传路径**（装配点 → 工具 → 服务）：`AgentFactory.CODE_*` → `ArticleAiService.imageProfileId(code)` / `ScheduledAgentFactory.imageProfileId(definition)` → `ArticleMediaTools.create(..., imageProfileId)` → `GenerateImageTool`/`EditImageTool` → `ImageGenerationService.generate/edit(..., profileId)` → `LlmConfigService.imageRuntime(profileId)`。

### 4.4 新表 `render_config`（排版渲染服务配置，2026-09-08 增补）

单行表，模式沿用 `llm_config`（ADMIN 维护，运行时实时读取热生效）：


| 字段                                 | 类型         | 说明                                                                     |
| ------------------------------------ | ------------ | ------------------------------------------------------------------------ |
| id                                   | BIGINT       | 主键                                                                     |
| provider                             | VARCHAR(30)  | 目前仅`MARKFLOW`                                                         |
| base_url                             | VARCHAR(500) | 默认`https://www.bx9y.com.cn`，渲染端点为 `{base_url}/__markflow_render` |
| token_encrypted                      | TEXT         | 渲染令牌，AES 加密入库（密钥 = APP_SECRET_KEY），API 返回掩码            |
| site_base_url                        | VARCHAR(500) | **本站公网地址（2026-09-08 Spike 定案）**：MARKFLOW 渲染前把 markdown 中 `/uploads/…` 相对路径替换为 `{site_base_url}/uploads/…` 绝对直链（上游对相对路径图片原样透传不补域名，实测确认）；空则跳过绝对化并告警 |
| syntax_cache_ttl_seconds             | INT          | 语法指令缓存 TTL，默认 21600（6 小时）                                   |
| enabled                              | BOOLEAN      | 关闭后 MARKFLOW 引擎不可用（相关任务保存草稿时报明确错误）               |
| created_at / updated_at / updated_by |              | 惯例字段                                                                 |

> 来源说明：本表是对本机技能 `~/.zcode/skills/markflow-typeset/` 的产品化改造——该技能把渲染令牌放在本地文件 `~/.zcode/secrets/markflow-render-token`，产品化后改为系统设置内加密存储，不依赖部署机的用户主目录。
>
> 令牌注入路径（2026-09-08 审查补充）：同时支持环境变量 `MARKFLOW_RENDER_TOKEN`（优先级：环境变量 > 设置页存储），与 `APP_SECRET_KEY` 等 Docker 部署惯例一致——设置页留空而环境变量存在时以环境变量为准，GET 接口此时返回「已由环境变量注入」而非掩码。

### 4.5 现有表加列（全部可空或带默认值，smart-mybatis 自动同步）

**`schedule_task`：**


| 字段                | 类型        | 默认       | 说明                                                                                                                                                      |
| ------------------- | ----------- | ---------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| execution_mode      | VARCHAR(30) | `'SINGLE'` | SINGLE / PIPELINE / COORDINATOR                                                                                                                           |
| skill_ids           | TEXT (JSON) | null       | 任务级 Skill                                                                                                                                              |
| stage_agents        | TEXT (JSON) | null       | `{"research":1,"writing":2,"illustration":3,"review":4}` → agent_definition_id；某阶段缺省 = 用内置默认；显式 null/0 = 跳过该阶段（仅配图/审核支持跳过） |
| max_revision_rounds | INT         | 2          | 审核不通过的返工上限                                                                                                                                      |

**`wechat_account`：**


| 字段      | 类型        | 说明              |
| --------- | ----------- | ----------------- |
| skill_ids | TEXT (JSON) | 账号默认 Skill 集 |

`default_style` 字段保留：非空时作为一行「账号默认风格」注入 prompt（终于接线）。

**`article`：**


| 字段             | 类型         | 说明                                                                                                     |
| ---------------- | ------------ | -------------------------------------------------------------------------------------------------------- |
| skill_ids        | TEXT (JSON)  | 文章级 Skill（编辑器对话用，最高优先级）                                                                 |
| layout_engine    | VARCHAR(20)  | 排版引擎（PROMPT / MARKFLOW）。MARKFLOW 文章的正文由渲染服务生成，渲染产物无法反推回 Markdown，故必须留存 |
| content_markdown | TEXT (65535) | MARKFLOW 文章的 Markdown 源文；渲染产物被覆盖后仍可重排（2026-09-10 十四轮补：原先落库时被丢弃）          |

MARKFLOW 文章的保存语义（`ArticleService.reconcileRenderedLayout`）：编辑器只认识渲染产物 HTML，加载后会重新
序列化，保存一次就会把公众号版式降级成普通 HTML。因此保存时比较正文**纯文本**——未变（只改标题/摘要）保留库中
原有渲染产物与 Markdown 源文；确有改动则落库编辑器结果并清空已与正文不符的 Markdown 源文（避免后续重排静默
覆盖用户刚做的编辑）。

**`task_run`：**


| 字段           | 类型        | 说明                                                                              |
| -------------- | ----------- | --------------------------------------------------------------------------------- |
| mode           | VARCHAR(30) | 本次运行实际使用的执行模式                                                        |
| stages_summary | TEXT (JSON) | 各阶段 best-effort 摘要：`[{stage,agentName,toolCalls,tokens,durationMs,status}]` |

---

## 5. 后端详细设计

### 5.1 新增包结构

```
ink.icoding.wechat.article
├── skill/
│   ├── Skill.java                  (实体)
│   ├── SkillMapper.java
│   ├── SkillService.java           (CRUD + 校验 + 克隆)
│   ├── SkillController.java        (/api/skills)
│   ├── SkillSeeder.java            (内置种子，幂等)
│   ├── SkillPromptAssembler.java   (★ prompt 组装核心)
│   └── MarkFlowRenderService.java  (★ MarkFlow 渲染 API 封装：语法缓存 + 渲染 + renderId 缓存，2026-09-08 增补)
├── agent/
│   ├── AgentDefinition.java        (实体)
│   ├── AgentDefinitionMapper.java
│   ├── AgentDefinitionService.java
│   ├── AgentDefinitionController.java (/api/agents)
│   ├── AgentSeeder.java            (内置 7 agent，幂等)
│   ├── LlmProfile.java / LlmProfileMapper.java
│   ├── LlmProfileService.java / LlmProfileController.java (/api/llm-profiles)
│   ├── LlmProfileMigrationRunner.java (llm_config → 默认档案)
│   ├── ToolRegistry.java           (★ 工具键注册表)
│   └── AgentFactory.java           (★ 按 AgentDefinition 装配 AgentClient)
└── schedule/（扩展）
    ├── ScheduledExecutionStrategy.java (接口)
    ├── SingleAgentExecutor.java
    ├── PipelineExecutor.java
    ├── CoordinatorExecutor.java
    └── TaskWorkspace.java          (DraftState 扩展)

settings/（扩展，2026-09-08 增补）
    ├── RenderConfig.java / RenderConfigMapper.java
    └── RenderConfigService.java / RenderConfigController.java  (/api/settings/render，ADMIN)
```

### 5.2 SkillPromptAssembler（prompt 组装核心）

**输入**：`SkillContext { articleSkillIds, taskSkillIds, accountSkillIds, accountDefaultStyle, agentDefaultSkillIds, scene }`（scene = SCHEDULED / EDITOR）

**解析算法：**

```
1. 收集候选 Skill：
   enabled 且未删除的：
   - agent 定义自身的默认 skill_ids（底座）
   - account.skill_ids
   - task.skill_ids
   - article.skill_ids（仅 EDITOR 场景）
   全部按 id 并集去重（article > task > account > agent 的优先顺序仅决定同维度冲突时的排序）
2. 按 dimension 分组，组内多条件按上述优先级排序
   **LAYOUT 维度例外（2026-09-08 审查修订）：仅注入优先级最高的一枚 LAYOUT Skill，其余 LAYOUT Skill 连同内容一并丢弃**——否则 PROMPT/MARKFLOW 两套互斥排版指令会同时出现在系统提示里（双引擎混用矛盾，模型行为不可预测）；preview 接口返回被忽略的 LAYOUT Skill 清单便于排查；任务/账号/文章绑定保存时检测到多枚 LAYOUT Skill 给出提示
3. 组装为带维度标题的文本块：
   【目标读者】
   <skill1 内容>
   【写作风格】
   <skill2 内容>
   ……【排版模板】最后
4. 若账号 default_style 非空 → 追加一行「账号默认风格：<value>」
5. 保底规则：最终集合若不含 LAYOUT 维度 → 自动追加内置 default_layout skill
   （ArticleContentPolicy 强制纯段落约束依赖模板引导，不可缺失）
   DB 不可用时回落代码内置常量（从 ArticleAiService 迁移原文）
   若含 LAYOUT 维度 → 生效引擎取排序最前的 LAYOUT Skill 的 engine 字段（PROMPT/MARKFLOW 二选一，见 5.10.2）；
   MARKFLOW 时「排版模板」区注入 skill content + MarkFlow 实时语法 guide，并前置校验渲染服务可用性
6. 长度上限 60000 字符（口径：Skill content 合计；核心协议常量与 MARKFLOW 模式运行时注入的语法 guide 不计入该口径，但 preview 接口展示最终全文——2026-09-08 审查修订），超限抛 BusinessException（提示用户精简 Skill）
```

**输出拼接位置**：`核心协议常量（代码） + persona（agent 定义） + Skill 注入块` 替代现在的 `AGENT_DESCRIPTION`；`SCHEDULED_AGENT_DESCRIPTION` 同理。

**预览接口** `POST /api/skills/preview`：入参 `{skillIds, scene, accountDefaultStyle?}`，返回组装后的完整系统提示文本——供管理界面「预览注入效果」和开发验收使用。

### 5.3 ToolRegistry（工具注册表）

统一登记所有工具键并分组，AgentDefinition.tool_keys 保存时校验：


| 工具组 key       | 工具                                                                                                                 | 数量 | 执行位置                         | 说明                                                      |
| ---------------- | -------------------------------------------------------------------------------------------------------------------- | ---- | -------------------------------- | --------------------------------------------------------- |
| `BROWSER_EDITOR` | read_article / read_blocks / delete_blocks / insert_blocks / replace_blocks / update_metadata / update_cover         | 7    | **前端（SSE 回传）**             | 沿用现有机制                                              |
| `MEDIA`          | search_web / browse_webpage / search_web_images / list_image_assets / import_web_image / generate_image / edit_image | 7    | 服务端                           | `ArticleMediaTools.create()`                              |
| `DRAFT_READ`     | read_article_draft                                                                                                    | 1    | 服务端（TaskWorkspace）          | 只读草稿（协调者/审稿人等「检查不动笔」角色用）          |
| `DRAFT_WRITE`    | save_article_draft / set_article_draft_cover                                                                          | 2    | 服务端（TaskWorkspace）          | 读写草稿（撰稿人/配图师/定时单智能体用）                 |
| `RESEARCH`       | save_research_notes（**新增**）                                                                                      | 1    | 服务端（TaskWorkspace）          | 调研员落盘简报                                            |
| `REVIEW`         | submit_review（**新增**）                                                                                            | 1    | 服务端（TaskWorkspace）          | `{passed, issues[], suggestions[]}`                       |
| `DELEGATE`       | delegate_research / delegate_writing / delegate_illustration / delegate_review（**新增**）                           | 4    | 服务端（内嵌子 AgentClient）     | 仅协调者模式                                              |
| `RENDER`         | render_markflow（**新增**，2026-09-08 增补）                                                                         | 1    | 服务端（EditorSession 渲染缓存） | 编辑器链路 MarkFlow 排版：markdown → renderId，见 5.10.4 |

（2026-09-08 审查修订：原 DRAFT 为组粒度且含写工具，附录 B 中 chief「只读 draft」无法实现、审稿人会被授予写草稿能力；拆分后语义精确，协调者的返工预算护栏才不被绕过。）

**AgentFactory 职责：**

1. 读 AgentDefinition（enabled 校验、级联读 LlmProfile）
2. 按 tool_keys 从 ToolRegistry 实例化工具（浏览器编辑工具继续走现有 EditorSession 的 SSE 前端执行管道；DELEGATE 组传入 AgentFactory 自身引用以构造子 Agent）
3. description = 核心协议常量（按 stage 选择）+ persona + SkillPromptAssembler 产出
4. 模型 = 档案 + 覆盖项 → `LLMModel.create(...)`（复用现 `createModel()` 的 provider 映射）
5. 返回装配好的 `AgentClient`

**协议拆分示例**（现 `AGENT_DESCRIPTION` 15 条 → 保留为代码常量的部分）：

- 工具纪律：每次响应最多一个写工具、先读后改、等待结果再下一步、每步先说明意图（原第 2/4/5/10 条）
- 乐观锁：携带 documentVersion、过期重读（原第 3 条）
- 图片必须先进素材库、正文用 publicUrl / 封面用 assetId（原第 13/14 条）
- 输出约束：不要输出 ARTICLE_PATCH、完成条件（原第 8/9/11 条）
- **移入 default_layout skill**：视觉模板 + 禁列表/表格的排版规则（原第 15 条 + ARTICLE_STYLE_GUIDE）
- **删除**：「不要创建计划或子智能体」（多 Agent 协作的核心前提）

### 5.4 TaskWorkspace（共享工作区）

从 `ScheduledArticleTools.DraftState` 扩展（保持原 3 个工具行为不变）：

```java
public class TaskWorkspace {
    // 原 DraftState 全部字段与方法（title/author/digest/contentHtml/sourceUrl/coverAssetId/documentVersion/saved）
    private String researchNotes;       // 调研简报（Markdown），save_research_notes 写入
    private List<AssetRef> citedAssets; // 引用过的素材（供配图/审核阶段参考）
    private List<Round> reviewRounds;   // 审核轮次记录 [{round, passed, issues, suggestions, at}]
    private int revisionRound;          // 当前返工轮次
}
```

- `save_research_notes` 工具：调研员写入简报（追加式，带轮次标记）；写作 agent 的用户指令中注入「以下是调研简报」
- `submit_review` 工具：审稿人提交结论；JSON 结构化返回由代码解析（宽松解析：优先找 JSON 块，失败则回退"有条件通过+原文作为 issues"的保守策略）
- 工具描述与 `ArticleContentPolicy` 约束沿用

### 5.5 三种执行策略

`ScheduledExecutionStrategy` 接口：

```java
public interface ScheduledExecutionStrategy {
    ScheduledAgentResult execute(ScheduledAgentRequest request, TaskWorkspace workspace) throws Exception;
}
```

`TaskExecutionService` 按 `execution_mode` 路由（SINGLE/null → SingleAgentExecutor）。

**SingleAgentExecutor**：现 `runScheduledAgent()` 逻辑平移，prompt 改走 AgentFactory + 组装器，工具 = 内置 `scheduled_creator` 定义的 tool_keys（等价现状）。

**PipelineExecutor**（代码编排，无协调层 LLM 开销）：

```
① 调研阶段（research agent：RESEARCH+MEDIA 工具组）
   指令 = 任务 aiPrompt + 「先检索再浏览，产出结构化调研简报并调用 save_research_notes」
   产出：researchNotes + citedAssets
② 写作阶段（writing agent：DRAFT_READ + DRAFT_WRITE 工具组）
   指令 = aiPrompt + 调研简报 + Skill 注入（含排版模板）
   产出：save_article_draft
③ 配图阶段（可选，illustration agent：MEDIA + DRAFT_READ + DRAFT_WRITE 工具组）
   指令 = 「读取当前草稿，按图片风格 Skill 为每章配图，重存草稿并 set_article_draft_cover」
   跳过条件：stage_agents.illustration 显式为空
④ 审核阶段（可选，review agent：DRAFT_READ + MEDIA 工具组，可联网核查）
   submit_review{passed, issues}
   不通过且 revisionRound < max_revision_rounds：
   → 携 issues 回写作阶段返工（指令追加「上一稿审核意见：<issues>，请修改后重新提交」）
   → 再走 ③④（配图是否重跑由 issues 是否涉及图片决定：简单规则——issues 含图片相关关键词才重跑配图）
⑤ 结束：沿用现有落库 + outputMode 交付逻辑，写 stages_summary 与分阶段执行日志
```

**CoordinatorExecutor**（chief 自主调度）：

- chief agent（COORDINATE stage，DELEGATE + DRAFT_READ 工具组）
- 4 个 delegate 工具内部：经 AgentFactory 构造对应子 AgentClient，**同步运行**（`result.execute(); result.get()`），共享同一 TaskWorkspace
- delegate 工具描述向 chief 说明各子智能体的能力与适用场景；chief 在一条指令流内自主决定调用顺序、次数、是否带审核意见返工
- **预算护栏**（代码强制，超限工具直接返回错误文本引导 chief 收尾）：
  - 委托总次数 ≤ 8
  - 同一草稿的返工 ≤ max_revision_rounds
  - 子智能体单次工具调用 ≤ 24（对齐 MAX_TOOL_CALLS；注意该上限目前仅在编辑器浏览器工具路径强制——`ArticleAiService.requestTool`，定时子智能体侧为本期**新增**的强制点，2026-09-08 审查修订）
- chief 会话结束时草稿未保存 → 按现状报错「智能体没有通过 save_article_draft 提交文章」
- 每阶段经 AgentResultHandler 记录到 stages_summary（best-effort）

**前置 Spike（第③期开工首日，约半天）**：验证 agent4j 能否在 `Tool.execute()` 内嵌套运行子 `AgentClient`（线程/会话兼容性）。**若不通**：协调者退化为「chief 产出执行计划 JSON → 代码按计划顺序执行各子 Agent」的预编排方案——对外接口与数据模型完全不变，仅 CoordinatorExecutor 内部实现调整。

### 5.6 REST API 设计

**SkillController（`/api/skills`）**


| 方法   | 路径              | 权限           | 说明                                                                          |
| ------ | ----------------- | -------------- | ----------------------------------------------------------------------------- |
| GET    | ``                | 全员           | 列表（dimension/enabled 过滤）                                                |
| GET    | `/{id}`           | 全员           | 详情                                                                          |
| POST   | ``                | ADMIN/OPERATOR | 创建（name 唯一、dimension 白名单校验；LAYOUT 维度可带 engine/engine_config） |
| PUT    | `/{id}`           | ADMIN/OPERATOR | 更新（内置 skill 可改内容不可删）                                             |
| DELETE | `/{id}`           | ADMIN/OPERATOR | 删除（内置拒绝；被引用处见 5.7）                                              |
| POST   | `/{id}/duplicate` | ADMIN/OPERATOR | 克隆（内置 → 自定义副本）                                                    |
| POST   | `/preview`        | 全员           | 组装预览                                                                      |

**AgentDefinitionController（`/api/agents`）**


| 方法   | 路径              | 权限           | 说明                                                   |
| ------ | ----------------- | -------------- | ------------------------------------------------------ |
| GET    | ``                | 全员           | 列表（stage 过滤）                                     |
| GET    | `/{id}`           | 全员           | 详情                                                   |
| POST   | ``                | ADMIN/OPERATOR | 创建自定义 agent                                       |
| PUT    | `/{id}`           | ADMIN/OPERATOR | 更新（内置可改 persona/工具/Skill；code/stage 不可改） |
| DELETE | `/{id}`           | ADMIN/OPERATOR | 删除（内置拒绝；被任务引用见 5.7）                     |
| POST   | `/{id}/duplicate` | ADMIN/OPERATOR | 克隆                                                   |
| GET    | `/tool-groups`    | 全员           | ToolRegistry 分组清单（前端勾选用）                    |

**LlmProfileController（`/api/llm-profiles`）**：GET 列表/详情（key 掩码）、POST/PUT/DELETE、`POST /{id}/set-default`。**权限全部 ADMIN**。

**RenderConfigController（`/api/settings/render`，2026-09-08 增补）**：GET 读取（token 掩码）/ PUT 更新（provider/base_url/token，token AES 加密入库；**保存时失效语法 guide 缓存**，base_url 变更意味着切换到语法可能不同的服务实例）/ `POST /test`（探测渲染服务连通性与 token 有效性，返回 guide 摘要长度确认，**并顺带刷新语法 guide 缓存**——上游组件库更新后的手动失效入口）。**权限 ADMIN**。

**现有 API 扩展**：`/api/tasks` CRUD 与 detail 透传新字段；`/api/accounts` 透传 skillIds；`/api/articles` 透传 skillIds。

**审计（2026-09-08 审查补充）**：新增 `/api/skills`、`/api/agents`、`/api/llm-profiles`、`/api/settings/render` 的写操作自动被现有审计拦截器（拦截 `/api/**`）记录，无需额外开发；验收时确认 `audit_log` 有对应记录即可。

### 5.7 删除被引用资源的策略（防悬挂引用）

不建外键（项目惯例），改为**软引用 + 运行时容错**：

- Skill 被删除时：仅从绑定处 JSON 中移除该 id（Service 层在删除事务里扫 schedule_task/wechat_account/article 的 skill_ids JSON 并剔除——量级小可接受）；或更简单：**不主动清理，注入时静默跳过失效 id**（推荐，实现最小且天然幂等）
- AgentDefinition 被删除时：任务的 stage_agents 引用失效 → 执行时回落该 stage 的内置默认 agent，并在执行日志中记录「配置的智能体已删除，已回落内置」
- 采用推荐方案：**注入/装配时一律静默跳过失效 id**，管理界面列表页对失效引用显示「含失效配置」角标（best-effort）

### 5.8 权限矩阵


| 功能                      | VIEWER | EDITOR | REVIEWER | OPERATOR | ADMIN |
| ------------------------- | ------ | ------ | -------- | -------- | ----- |
| 查看 Skill/Agent/档案     | ✓     | ✓     | ✓       | ✓       | ✓    |
| Skill 增删改/克隆         | —     | —     | —       | ✓       | ✓    |
| Agent 定义增删改/克隆     | —     | —     | —       | ✓       | ✓    |
| 模型档案管理              | —     | —     | —       | —       | ✓    |
| 任务配置执行模式/阶段编排 | —     | —     | —       | ✓       | ✓    |
| 编辑器选文章级 Skill      | —     | ✓     | ✓       | ✓       | ✓    |

（沿用现有 `@PreAuthorize` 注解风格；EDITOR 在编辑器对话权限内，故允许选文章级 Skill。）

### 5.9 Seeder 与启动顺序

```java
@Order(10) SkillSeeder              // 内置 skill（default_layout 优先于一切）
@Order(20) LlmProfileMigrationRunner // llm_config → 默认档案（一次性）
@Order(30) AgentSeeder               // 内置 7 agent（依赖 skill 表存在）
// QuartzTaskManager.restore() 现有 ApplicationReadyEvent 时机不变
```

幂等性：按 builtin_key upsert（存在则比对关键字段并更新 seed 内容，保留用户对内置项的 persona 修改策略——**内置 skill 内容随 seed 更新（排版模板属系统行为），内置 agent 的 persona 用户改过则不覆盖**：用 `updated_at > created_at` 之外的简单策略——persona 与 seed 不同即视为用户改过，跳过）。

### 5.10 MarkFlow 排版引擎（2026-09-08 增补，来源：本机技能 markflow-typeset 产品化）

#### 5.10.1 定位与动机

本机技能 `~/.zcode/skills/markflow-typeset/` 提供了一条与「Agent 直接写内联 HTML」完全不同的排版路径：**Agent 产出 MarkFlow 扩展语法 Markdown → 远程渲染服务转成精良的内联样式 HTML**。它解决了 PROMPT 引擎的两个固有弱点：

1. LLM 手写内联 HTML 的版式质量不稳定（样式漂移、结构走样），MarkFlow 由确定性渲染引擎产出，版式质量恒定且支持步骤卡/对比卡/提示框等复杂组件；
2. 版式迭代成本高——换一套视觉主题在 PROMPT 引擎下要重写整段模板指令，MarkFlow 只需换 accent/dark 主题色。

融合定位：**MarkFlow 是 LAYOUT 维度的第二种排版引擎**，与 PROMPT 引擎并存、由排版 Skill 的 `engine` 字段声明。产品化改造要点：渲染令牌从本机文件 `~/.zcode/secrets/markflow-render-token` 迁移为系统设置加密存储（`render_config` 表），不再依赖部署机用户主目录。

**发布能力刻意不集成（边界说明）**：本机技能的 `__markflow_wechat_publish` 接口**不在融合范围**，仅集成渲染接口。原因：① 功能重叠——项目已有完整的微信交付链路（`WechatClient` 草稿/发布/素材 API、多账号加密凭据管理、SSE 进度、`wechat_status`/`wechat_publish_id` 发布状态追踪、版本快照、REVIEWER/OPERATOR/ADMIN 角色权限、操作审计），MarkFlow 发布接口只是这条链路的单发小子集；② 信任边界——该接口要求把公众号 AppID/AppSecret 提交给第三方服务（bx9y.com.cn），而本项目 AppSecret 经 APP_SECRET_KEY 加密、仅在本服务与微信官方 API 之间解密流转，README 明确承诺「数据和密钥掌握在自己手里」，密钥出第三方不可接受；③ 无功能损失——渲染接口返回的 `html` 正是发布接口的 `content` 入参，渲染产物入库后走现有链路交付（syncDraft/publish），端到端能力完整。实施时**不得**以任何形式把 AppSecret 发往渲染服务。

#### 5.10.2 引擎选择与生效规则

- LAYOUT 维度 Skill 新增 `engine` 字段：`PROMPT`（默认，现状行为）/ `MARKFLOW`。
- **同一上下文（任务/文章）最终生效的排版引擎以「排序最前的 LAYOUT Skill」为准**——解析优先级：article > task > account > agent 默认。禁止双引擎混用（两个引擎的正文产物范式互斥：一个产 HTML、一个产 Markdown）。
- 生效引擎为 MARKFLOW 时：
  - 系统 prompt 的「排版模板」区注入 = 该 Skill 的 content（作为风格与主题色策略的补充说明）+ **MarkFlow 语法指令 guide**（由 `MarkFlowRenderService` 实时 GET 并缓存，TTL 见 render_config；guide 与线上渲染引擎严格同步，绝不内置副本，防止语法漂移）。
  - Skill 的 `engine_config` 提供主题色：`accentMode=AUTO`（Agent 按内容主题自选，内置 Skill 的 content 附预设主题对照表）或 `FIXED`（固定使用 Skill 配置的 accent/dark）。
  - **前置校验**：`render_config.enabled=false`、token 未配置或渲染服务探测失败时，任务启动/对话发起即报明确错误（「排版技能需要 MarkFlow 渲染服务，请到系统设置配置或改用指令式排版技能」），不静默回落 PROMPT（静默换引擎会产出完全不同的版式，违背用户预期）。
- **EDITOR 场景的阶段限制（2026-09-08 审查修订）**：`render_markflow` 工具第④期才交付，此前 EDITOR 场景的 Skill 解析**过滤 MARKFLOW 引擎技能**（不注入其内容与 guide），编辑器 Skill 选择 UI 对其置灰并提示「渲染式排版暂仅支持定时创作」——否则窗口期内 Agent 收到「产出 Markdown」指令而编辑器工具链只接受 HTML，产物必然错乱。第④期交付后解除过滤。

#### 5.10.3 MarkFlowRenderService（新服务）

```
GET  {base_url}/__markflow_render          → 语法指令 guide（进程内缓存，TTL 可配）
POST {base_url}/__markflow_render          → {markdown, accent, dark} → {ok, html, meta{title,summary}, theme{accent,dark}, preview}
（preview 为自包含预览页 HTML，体积可能远大于 html——服务端反序列化后忽略、不入库不透传；测试 mock 按含 preview 的真实 payload 构造。2026-09-08 审查修订）
鉴权：X-Render-Token: <render_config.token_decrypted>
```

- **语法缓存三级失效策略**（2026-09-08 二次增补）：① TTL 过期自动失效（默认 6h）；② **渲染失败触发失效**——某次渲染返回 400 且错误信息疑似语法问题时，立即失效 guide 缓存，下一轮任务即取最新语法（应对上游刚增删/改名组件而本地缓存仍在 TTL 窗口内的滞后）；③ **手动失效**——`POST /api/settings/render/test` 测试连接时顺带刷新缓存，PUT 保存配置（base_url 变更）时同样失效，供运维在获知上游更新后主动触发。（注：「未知语法降级渲染」目前仅 mermaid 一例有文档依据，未知容器/标签的实际行为未定义——列为第①期 Spike 验证项；若实测为硬失败，则「渲染 400 触发缓存失效」策略为主要兜底。2026-09-08 审查修订：不再把降级断言为既定事实。）
- 渲染结果安全：html 入库前经服务端**危险元素定向剥离**（剥离 script/iframe/on* 事件属性/外部 JS 引用等；为**黑名单语义而非严格标签白名单**——刻意如此设计，使上游组件库新增组件产出的新标签可正常入库，无需随上游升级改代码）。第三方服务产出不可直接信任。
- 超时与重试：渲染超时 30s；失败向上抛 BusinessException（状态码语义映射：401 令牌无效 / 400 参数或语法非法 / **413 Markdown 超 2MB** / 5xx 服务端错误 / 超时；**另防御 HTTP 200 + body.ok=false 组合**——同服务的发布接口即「HTTP 恒 200、成败看 body.ok」契约，渲染客户端统一以 body.ok 为最终判据、不单信状态码。2026-09-08 审查修订）。
- 图片约束传导（2026-09-08 审查修订，**原「天然满足」判断有误**）：MarkFlow 要求图片为 http(s) **直链**，而现有素材 `publicUrl` 是**相对路径**（`AssetService` 存 `"/uploads/" + storageName`），远程渲染服务收到相对路径图片的行为未定义，且项目当前没有「站点公网 Base URL」配置。处理：① 第①期 Spike 验证含 `/uploads/` 相对路径图片的渲染行为；② 若不透传，补「站点 Base URL 配置 + 渲染前 URL 绝对化」子项（仅影响 MARKFLOW 渲染链路；微信交付不受影响——`ArticleService.prepareWechatContent` 本就会把 /uploads/ 本地图重传微信并替换 src，编辑器同源显示也不受影响）。工具描述中明确「正文图片一律使用素材工具返回的 publicUrl」。

#### 5.10.4 工具语义的引擎适配（关键设计：LLM 上下文零 HTML）

核心原则：**渲染发生在工具边界（服务端），LLM 上下文里永远只有 Markdown，不出现大段渲染后 HTML**——这同时控制 token 成本并避免 Agent 抄改渲染产物破坏版式。

**定时链路（save_article_draft / read_article_draft，DraftState/TaskWorkspace 扩展）**：

- MARKFLOW 模式下 `save_article_draft` 的 `contentHtml` 参数语义变为「MarkFlow 语法 Markdown」（工具描述随引擎动态生成）；新增可选参数 `accent`/`dark`（仅 AUTO 模式，Agent 依据主题对照表选定）。
- Workspace 内部持有引擎模式；**MARKFLOW 下 save_article_draft 仅保存 markdown 与主题参数，不即时渲染**（2026-09-08 审查修订：避免审核返工环内每次保存都外呼渲染服务），workspace 记 `contentMarkdown + renderTheme + renderedHtml(可 stale)`；**渲染延迟到交付前统一执行一次**（TaskExecutionService 落库前）——渲染成功才覆盖 renderedHtml，失败则任务失败并在执行日志保留 markdown 供定位重试（双份数据一致性由「渲染成功才覆盖」保证）。PROMPT → 现状逻辑不变。
- `read_article_draft` MARKFLOW 模式返回 markdown + renderTheme（延迟渲染下交付前无渲染 meta 可返回），不返回大 HTML。
- `ArticleContentPolicy.requireParagraphProse` 改为**引擎感知**：PROMPT 模式维持纯段落强校验；MARKFLOW 模式跳过该校验（渲染产物含步骤卡/列表/表格属预期），但保留基础安全清洗。校验入口传入当前引擎枚举，防止 Agent 在 PROMPT 模式下借 MarkFlow 语法绕过约束。
- 落库/微信交付：用 workspace 的 `contentHtml`（渲染后），下游（ArticleService、WechatClient、版本快照）零改动——它们只见 HTML，与现状一致。
- 摘要增强（顺手项，2026-09-08 审查补充）：MARKFLOW 模式下 Agent 未显式提供 digest 时，以渲染响应 `meta.summary` 自动填充。

**编辑器链路（第④期，render_markflow 工具 + 占位替换）**：

- 新增服务端工具 `render_markflow`（RENDER 工具组）：输入 markdown + 可选 accent/dark → 服务端渲染 → 返回 `{renderId, title, summary, theme}`（**不返回 html**）；渲染结果以 renderId 存 EditorSession 内存缓存。
- `insert_blocks` / `replace_blocks` 的 block 支持 `{{render:<renderId>}}` 占位：服务端在 SSE 下发 `tool.call` 事件**之前**将占位替换为完整 HTML——浏览器端拿到的仍是纯 HTML block，前端编辑器与工具执行链路**零改动**。
- 版本快照/落库存替换后的 HTML，`documentVersion` 乐观锁机制不变。
- 防 HTML 回灌（2026-09-08 审查补充）：渲染区段入库后，后续 `read_article` / `read_blocks` 对该区段返回**占位摘要**（「已渲染版式块 renderId=N，主题=X，约 N 字」）而非全文 HTML——否则大段 HTML 回灌上下文，Agent 可能抄改破坏版式，正是「LLM 上下文零 HTML」要防的事。
- 校验管道顺序（编辑器链路）：blocks 先过安全校验（占位符文本本身无害）→ 服务端占位替换为渲染 HTML → 替换后内容按 MARKFLOW 引擎策略跳过纯段落校验；**未解析的 `{{render:}}` 占位符按非法内容拒绝**（防 PROMPT 模式伪造占位符——renderId 仅在 MARKFLOW 模式下调用 render_markflow 后才存在）。

#### 5.10.5 与四期计划的并入关系

- **第①期**（Skill 体系）即交付定时链路的 MarkFlow 完整能力：render_config 表与设置 API、MarkFlowRenderService、save/read 草稿工具的引擎适配（在现有 DraftState 上实现，不依赖第③期的 TaskWorkspace）、引擎选择与保底规则（保底 default_layout 为 PROMPT 引擎）、SkillsView 引擎配置 UI、SettingsView 渲染服务配置区。第①期工作量 5-7 → **10-14 人日**（含 0.5 天前置 Spike，2026-09-08 审查重估）。
- **第④期**（编辑器集成）交付 `render_markflow` 工具与占位替换机制（编辑器场景的整篇/大块替换）。
- 流水线/协调者模式（第③期）天然继承：撰稿人 Agent 在 MARKFLOW 模式下写 Markdown，配图师/审稿人读到的草稿即 markdown + theme，审核「版式符合度」检查对象改为渲染主题与组件使用合理性。

---

## 6. 前端设计

沿用现有手写 CSS 风格、中文硬编码文案、原生 fetch（`api.js`）模式，不引入新依赖。

### 6.1 SkillsView（`/skills`，新增）

- 顶部：维度筛选 chips（全部/写作风格/排版模板/图片风格/…）+「新建技能」按钮
- 卡片列表：名称、维度标签、描述摘要、启用开关（行内切换）、内置徽标、操作（编辑/克隆/删除）；内置技能编辑表单顶部警示「内置技能内容会随系统版本更新被 seed 覆盖，如需持久定制请克隆为自定义技能」（2026-09-08 审查补充）
- 抽屉表单：名称、维度下拉（白名单）、描述（500 限）、**指令文本域**（content，带字数统计与 60000 上限提示）
- **排版引擎选择（仅 LAYOUT 维度显示，2026-09-08 增补）**：单选「指令式（Agent 直接写内联 HTML）」/「MarkFlow 渲染式（Agent 写 Markdown，由渲染服务排版）」；选渲染式 → 条件展开主题色配置（自动按内容选择 / 固定主题色 accent+dark 选色器）；选择渲染式但系统渲染服务未启用时表单顶部警示「需先在系统设置配置 MarkFlow 渲染服务」
- 「预览注入效果」：弹窗展示 `POST /api/skills/preview` 返回的完整系统提示（等宽字体滚动区）；MARKFLOW 技能预览含实时语法 guide

### 6.2 AgentsView（`/agents`，新增）

- 按 stage 分组展示卡片（编辑/定时创作/调研/写作/配图/审核/协调），显示工具组标签、绑定档案、内置徽标、启用开关
- 表单：基础信息（name/code 只读于内置）+ **人设文本域**（顶部说明「核心工具协议由系统内置保障，修改人设不会影响工具调用规则」）+ 工具组勾选（来自 `/api/agents/tool-groups`，浏览器编辑工具组标注「需在编辑器页面执行」）+ 默认 Skill 多选 chips + 模型档案下拉 + temperature/maxTokens 覆盖 + 启用开关

### 6.3 TasksView（改造）

- 任务表单新增区块「执行模式」：单选卡片（单智能体/流水线（推荐）/协调者）
- 选流水线/协调者 → 条件展开「阶段编排」：四个下拉（调研/写作/配图/审核，按 stage 过滤 agent 列表；配图/审核可显式选「跳过」）+ 返工轮次 number input（1-5）
- 任务 Skill 多选 chips（管理界面入口）
- 运行历史：mode 徽标 + stages_summary 分阶段时间线（agent 名/工具调用数/耗时）

### 6.4 AccountsView（改造）

- 表单新增「默认 Skill」多选 chips（写入 account.skillIds）；「默认风格」文本框保留，说明文案更新为「将以一句话注入 AI 指令」

### 6.5 ArticleEditorView（改造）

- AI 面板顶部：Skill 快捷选择 chips（写入 article.skillIds，保存即生效，影响后续对话的系统提示）；**第①-③期 MARKFLOW 排版技能置灰**并提示「渲染式排版暂仅支持定时创作」（编辑器渲染能力第④期交付，2026-09-08 审查修订）
- 第④期：聊天流中 delegate 类工具的调用卡片样式（「正在委托调研员…」+ 折叠的子 Agent 运行摘要）

### 6.6 SettingsView（改造）

- LLM 配置区下方新增「模型档案」列表区：卡片（名称/provider/model/掩码 key/默认标记）、新增/编辑表单、「设为默认」操作
- 现有「LLM 服务」表单保留（映射默认档案，文案加注「此为默认档案，可在智能体中指定其他档案」）
- **新增「排版渲染服务」配置区（2026-09-08 增补）**：Base URL、渲染令牌（掩码显示）、语法缓存 TTL、启用开关、「测试连接」按钮（探测 + 返回 guide 摘要）；启用后 SkillsView 的 MarkFlow 引擎方可选用

### 6.7 路由与导航

`router.js` 新增 `/skills`、`/agents` 路由（meta.title「技能库 · 墨舟」「智能体 · 墨舟」）；`App.vue` 侧边栏为**扁平 items 数组**（无分组结构，单层 v-for 渲染），本期直接追加两项——如需「智能体」分组须另行改造导航渲染，不做隐藏性假设（2026-09-08 审查修订）。

---

## 7. 四期交付计划

每期独立可用、独立验收、独立收尾（更新 README + 写 handoff）。

### 第①期：Skill 体系（预计 10-14 人日，含 MarkFlow 渲染引擎与前置 Spike）

**范围**：

0. **前置 Spike（0.5 人日，2026-09-08 审查新增）**：用真实渲染令牌验证三件事——① 含 `/uploads/` 相对路径图片的 Markdown 渲染行为（决定是否补「站点 Base URL 配置 + 渲染前 URL 绝对化」子项）；② 未知/已下线语法的实际行为（降级渲染还是硬失败）；③ `HTTP 200 + ok:false` 契约确认。结论记入本文档实施记录。
1. `skill` 表 + 实体 + CRUD API + 预览接口 + SkillSeeder（default_layout 从 ARTICLE_STYLE_GUIDE 迁移 + 2 个排版变体 + 2 个写作风格示例 + 1 个事实核查基线 + 1 个 MarkFlow 版式技能）
2. `SkillPromptAssembler`（含保底与上限规则、排版引擎解析与前置校验、LAYOUT 维度单注入、EDITOR 场景过滤 MARKFLOW 技能——2026-09-08 审查修订）
3. 两条链路 prompt 替换硬编码：定时（SCHEDULED_AGENT_DESCRIPTION）与编辑器（AGENT_DESCRIPTION）改走组装器；解除「不要创建子智能体」前的最小改动（本期不删该条）
4. `schedule_task.skill_ids` / `wechat_account.skill_ids` / `article.skill_ids` 加列 + API 透传
5. `default_style` 接线（非空时注入一行）
6. **MarkFlow 渲染引擎（定时链路完整能力，2026-09-08 增补）**：`render_config` 表 + `/api/settings/render`（含 test 探测）、`MarkFlowRenderService`（语法 guide 缓存 + 渲染 + 白名单清洗）、`DraftState` 的 save/read 工具引擎适配（MARKFLOW 模式存 markdown/渲染后 html 双份 + accent/dark 参数）、`ArticleContentPolicy` 引擎感知放宽、渲染服务不可用时的前置报错
7. 前端：SkillsView 新页面（含排版引擎选择与主题色配置）+ TasksView/AccountsView/ArticleEditorView 三处 Skill 绑定 UI + SettingsView 渲染服务配置区

**验收标准**：

- 新建「干货教程体」写作风格 Skill 绑定某任务 → 产物文风可感知变化（人工对比）
- 新建排版变体 Skill（如「极简黑白版式」）替换默认 → 产物版式变化
- **MarkFlow 链路（2026-09-08 增补，判据同日审查修订）**：配置渲染服务 → 新建 MARKFLOW 排版技能（含固定主题色）绑定任务 → 产物为渲染服务版式（步骤卡/提示框等组件可用）；图片按 Spike 结论可被渲染服务正确解析（直链透传或经绝对化处理）；实际生效主题以渲染响应 `theme` 字段为判据（AUTO 模式只传 accent 时 dark 由上游自动派生加深 25%，不要求与请求参数逐位一致）；渲染服务停用后该任务启动报明确错误
- 不选任何排版 Skill → 自动保底 default_layout，`ArticleContentPolicy` 校验仍通过
- 存量任务（无新字段值）行为与改造前一致
- `./mvnw test` 全绿 + `cd webui && npm run build` 通过

### 第②期：Agent 定义体系 + 模型档案（预计 6-8 人日）

**范围**：

0. **前置确认（2026-09-08 审查新增）**：翻 agent4j 2.3.3 API，确认 `LLMModel.create` 是否支持 temperature/maxTokens 传参——现有 `createModel()` 仅 4 参调用，`llm_config` 的温度/上限字段现在就未传给模型；若不支持，模型档案与 agent 覆盖的这两项暂不生效并在 UI 标注「待 agent4j 支持后接线」（不影响其余设计）。
1. `agent_definition` + `llm_profile` 表、实体、CRUD API
2. `ToolRegistry` + `AgentFactory` + 协议/人设拆分（协议常量类）
3. AgentSeeder（内置 7 个）+ LlmProfileMigrationRunner
4. 两条链路的 `new AgentClient()` 全部改走 AgentFactory（EDITOR 用内置 editor 定义，SCHEDULED_SINGLE 用 scheduled_creator 定义）
5. `LlmConfigService.runtime()` 内部改读默认档案；`/api/settings/llm` 兼容映射
6. 前端：AgentsView + SettingsView 档案区 + 侧边栏/路由

**验收标准**：

- 修改内置「编辑智能体」人设（如加「回复永远以『收到』开头」）→ 编辑器对话行为立即变化
- 工具组勾选变化实际生效（如去掉 MEDIA 组 → 对话中不再调用生图工具）
- 新建「廉价调研」档案 → 绑定某 agent → 从执行日志/HTTP 验证路由到新模型
- 默认档案迁移后：现有设置页读到的值 = 迁移前 llm_config 的值；编辑后对存量功能（编辑器对话、定时任务）生效
- 存量行为回归：无任何配置时与第①期末一致
- `./mvnw test` + 前端 build 全绿

### 第③期：定时链路多 Agent 协作（预计 7-10 人日）

**范围**：

1. **Spike（首日半天）**：agent4j Tool.execute 内嵌套子 AgentClient 验证 → 结论记入本文档实施记录
2. `TaskWorkspace`（DraftState 扩展）+ `save_research_notes` / `submit_review` 工具
3. `ScheduledExecutionStrategy` 接口 + Single/Pipeline/Coordinator 三执行器 + DELEGATE 工具组
4. `schedule_task` 加列（execution_mode/stage_agents/max_revision_rounds）+ API 透传
5. `task_run` 加列（mode/stages_summary）+ 分阶段执行日志（【调研】【写作】前缀）
6. 前端：TasksView 执行模式/阶段编排 UI + 运行历史分阶段时间线

**验收标准**：

- 流水线模式跑通：执行日志可见四阶段（调研简报已落盘 → 草稿生成 → 配图 → 审核通过），产物文章结构/配图符合 Skill 约束
- 构造必拒审核场景（审核 agent 人设加「本文只要出现『测试』二字一律不通过」+ 任务要求写含测试的内容）→ 验证自动返工 ≤ max_revision_rounds 且日志含审核意见回传
- 协调者模式：执行日志可见 delegate_* 工具调用链；chief 未让草稿落盘时报错信息正确
- 预算护栏：委托次数超 8 次时工具返回引导性错误、流程可正常收敛
- SINGLE 模式存量任务回归不变
- `./mvnw test`（含 Pipeline 返工环单测，经 AgentRunner seam 桩掉 agent4j）+ 前端 build 全绿

### 第④期：编辑器链路集成（预计 5-7 人日）

**范围**：

1. 编辑智能体（EDITOR stage）获得 `delegate_research` 服务端子智能体工具：编辑器对话可「先调研再写」，调研笔记回注当前对话上下文
2. **编辑器 MarkFlow 排版（2026-09-08 增补）**：`render_markflow` 服务端工具（markdown → renderId，结果存 EditorSession 渲染缓存）+ `insert_blocks`/`replace_blocks` 的 `{{render:<renderId>}}` 占位替换（服务端在 SSE tool.call 下发前替换为完整 HTML，前端零改动）+ read_article/read_blocks 对渲染区段返回占位摘要（防 HTML 回灌）+ 解除 EDITOR 场景的 MARKFLOW 技能过滤（6.5 置灰下线）
3. 文章级 Skill 快捷选择打磨（生效提示、与自动保存的联动）
4. README（中英）功能表更新、功能截图补充

**验收标准**：

- 编辑器对话「帮我调研 XX 的最新进展再写进文章」→ 可见 delegate_research 调用卡片 → 笔记内容出现在后续生成正文中
- **编辑器对话「用 MarkFlow 版式整篇重排本文」→ render_markflow 调用 → 编辑器内呈现渲染后版式、documentVersion 乐观锁正常**（2026-09-08 增补）
- 文章 Skill 切换后下一条对话的系统提示即变化（预览接口验证）。注（2026-09-08 审查处理）：代码形态上每次对话都新建 AgentClient（带最新 description）再从序列化恢复会话（`createArticleAgent` + `getSessionFromSerialization`），系统提示应天然随新 client 生效；但 agent4j 序列化会话是否携带独立系统提示无法本地确认——实施时以预览接口 + 实际对话各验证一次，若实测被固化，兜底为 Skill 变更时重建会话（丢弃该文章的 article_agent_session）
- SSE 流、工具回传、乐观锁链路无回归（既有编辑器手工回归清单）
- `./mvnw test` + 前端 build 全绿

**总计约 28-39 人日（单人全职）。**（2026-09-08 审查修订：第①期按 10-14 重估；原 24-34 尚有算术误差——四期下限之和实为 25。）

---

## 8. 测试与质量门禁

### 8.1 单元测试

- `SkillPromptAssemblerTest`：优先级并集、维度分组顺序、LAYOUT 保底、default_style 注入、60000 上限（含口径：guide/协议不计入）、失效 id 静默跳过、**排版引擎解析（多 LAYOUT skill 时取排序最前者的 engine）、LAYOUT 单注入（其余 LAYOUT skill 连内容一并丢弃）、EDITOR 场景过滤 MARKFLOW（第④期前）、渲染服务不可用前置报错**（2026-09-08 增补，同日审查修订扩充）
- `MarkFlowRenderServiceTest`（2026-09-08 增补）：mock HTTP——guide 缓存命中/TTL 过期、**渲染 400 疑似语法问题触发缓存失效、测试连接/保存配置的手动刷新失效**、渲染成功、401/400/**413 超 2MB**/5xx/超时错误映射、**HTTP 200 + ok:false 防御分支**、**preview 字段忽略（按含 preview 的真实 payload mock）**、html 危险元素定向剥离（script/iframe/on* 剥离，**上游新组件标签不被误杀**）、相对路径图片 URL 绝对化（若 Spike 判定需要）（后五项 2026-09-08 审查修订）
- `ArticleContentPolicyTest`（2026-09-08 增补）：PROMPT 引擎拒绝列表/表格；MARKFLOW 引擎放行；PROMPT 模式下含 MarkFlow 组件标记的 HTML 仍被拒绝（防绕过）
- `ToolRegistryTest`：tool_keys 校验（未知键拒绝、组内键正确解析）
- `PipelineExecutorTest`：经 `AgentRunner` 接口 seam（包一层 agent4j 调用）桩掉真实 LLM——返工环（review 不通过→写作→再审）、轮次上限熔断、阶段跳过
- 审核结论解析：标准 JSON / 带前后文的 JSON / 无 JSON 回退三分支

### 8.2 集成测试（沿用 CoreApiIntegrationTests 模式，真实 MySQL 测试库）

- `/api/skills`、`/api/agents`、`/api/llm-profiles` CRUD 全流程 + 角色矩阵（403 用例）
- 任务新字段（execution_mode/stage_agents/skill_ids）创建-读取-更新透传
- 默认档案迁移：预置 llm_config → 启动 → 断言 llm_profile 默认档案值等价

### 8.3 手工验收

按第 7 节各期验收清单执行，验收结果记录到当期 handoff。

### 8.4 门禁

每期收尾必须：`./mvnw test` 全绿 + `cd webui && npm run build` 通过 + 手工验收清单全过，才允许更新 README 与 handoff、宣布完成。

---

## 9. 风险与对策


| #  | 风险                                                                    | 等级 | 对策                                                                                                                                                                                 |
| -- | ----------------------------------------------------------------------- | ---- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 1  | 多 Skill 叠加导致系统提示超长、模型注意力稀释                           | 高   | 组装器 60000 字符硬上限 + preview 接口提前暴露 + 管理界面字数统计                                                                                                                    |
| 2  | 协调者模式 token 成本高（多 agent 各自完整会话）                        | 高   | UI 默认推荐流水线；委托≤8/返工≤轮次上限；stages_summary 记录各阶段 tokens 便于成本观测                                                                                             |
| 3  | agent4j 嵌套会话（Tool 内跑子 AgentClient）兼容性未知                   | 高   | 第③期首日 Spike；退化方案「chief 产计划 JSON + 代码顺序执行」对外接口不变                                                                                                           |
| 4  | 用户编辑内置 agent 人设破坏工具协议                                     | 中   | 核心协议代码内置 + persona 可编辑，物理隔离                                                                                                                                          |
| 5  | smart-mybatis 对 JSON 列类型支持不确定                                  | 中   | 统一 TEXT 存 JSON 字符串 + 代码层解析（已定案，规避）                                                                                                                                |
| 6  | 存量数据/行为兼容                                                       | 中   | 新列全可空/默认 SINGLE；Seeder 幂等；每期回归存量链路                                                                                                                                |
| 7  | Seeder 与 Quartz 任务恢复顺序（任务执行时 skill/agent 表未就绪）        | 低   | @Order(10/20/30) 保证先于恢复；装配器 DB 不可用回落代码内置常量                                                                                                                      |
| 8  | 删除 Skill/Agent 后悬挂引用                                             | 低   | 注入/装配静默跳过失效 id + 列表页「含失效配置」角标                                                                                                                                  |
| 9  | 前端编辑工具与 DELEGATE 服务端工具混用时 SSE 事件交错                   | 中   | 第④期才混用（编辑器 delegate_research）；delegate 工具纯服务端执行，不进 tool.call 前端管道，以 progress 事件呈现                                                                   |
| 10 | 审核结论非结构化导致返工环失控                                          | 中   | submit_review 工具描述强制 JSON 格式 + 宽松解析回退 + 轮次熔断兜底                                                                                                                   |
| 11 | MarkFlow 渲染服务不可用/超时导致任务失败（外部依赖，2026-09-08 增补）   | 高   | 启动前置探测报明确错误（不静默换引擎）；渲染 30s 超时 + 错误码语义映射（401/400/413/5xx/超时/200+ok:false）；render_config 可随时停用回退 PROMPT 技能（换 Skill 即换引擎，无迁移成本）；生产容器须保证出网可达渲染服务——Java HttpClient 不走系统代理环境变量，需经 HTTP 代理出网时 MarkFlowRenderService 显式配置 ProxySelector（2026-09-08 审查补充） |
| 12 | MarkFlow 产物含不安全 HTML（第三方服务，2026-09-08 增补）               | 中   | 服务端危险元素定向剥离（script/iframe/on* 剥离；黑名单语义而非严格白名单，避免误杀上游新组件）后再入库/下发，与现有编辑器 DOMPurify 前端清洗双保险                                   |
| 13 | Agent 在 PROMPT 模式下借 MarkFlow 语法绕过纯段落约束（2026-09-08 增补） | 中   | ArticleContentPolicy 按当前引擎选择校验策略，PROMPT 模式下组件标记同样拒绝                                                                                                           |
| 14 | MARKFLOW 模式下 Agent 输出 2MB 超限或图片用本地路径（2026-09-08 增补）  | 低   | 工具描述明确约束 + save 时长度校验（2MB 上限对齐 API）；图片必须 publicUrl 直链写入工具描述与语法 guide 注入块                                                                       |
| 15 | 上游 MarkFlow 组件库增删改导致语法漂移（2026-09-08 二次增补）           | 低   | guide 实时获取从不内置副本；TTL 缓存默认 6h；渲染 400 疑似语法问题即失效缓存、下一轮取最新；测试连接/保存配置手动刷新；未知语法行为以第①期 Spike 实测为准（文档仅明确 mermaid 降级）——若为硬失败则「400 触发缓存失效」为主兜底，若为降级则最坏结果是 TTL 窗口内版式退化（2026-09-08 审查修订：不再断言降级为既定事实） |

---

## 附录A：内置 Skill 种子清单


| builtin_key             | name              | dimension | content 摘要                                                                                                                                                                                                                                                                   |
| ----------------------- | ----------------- | --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `default_layout`        | 默认公众号版式    | LAYOUT    | 现`ARTICLE_STYLE_GUIDE` 原文迁移（绿色 #07C160 体系、章节号、figure、纯段落、HTML 示例）+ 禁 ul/ol/dl/table 规则；**engine=PROMPT**                                                                                                                                            |
| `minimal_layout`        | 极简黑白版式      | LAYOUT    | 黑白灰配色、无彩色装饰线、章节号改用细体数字、其余结构同默认；**engine=PROMPT**                                                                                                                                                                                                |
| `magazine_layout`       | 杂志图文版式      | LAYOUT    | 大图开篇、章节标题左对齐、段首强调色首字下沉风格说明（保持内联样式约束）；**engine=PROMPT**                                                                                                                                                                                    |
| `markflow_default`      | MarkFlow 精排版式 | LAYOUT    | **engine=MARKFLOW**，engine_config accentMode=AUTO；content = 排版风格指引（标题层级使用习惯、步骤卡/对比卡适用场景建议）+ 预设主题色对照表（13 组 accent/dark，供 Agent AUTO 模式就近选择）+ 图片一律 publicUrl 直链约束。语法本身不写入 content（由渲染服务实时 guide 注入） |
| `fact_check_default`      | 事实核查基线      | FACT_CHECK | 关键事实多源交叉验证；数据与引语须注明出处；时效敏感信息标注核实时间；无法核实的信息降级为「据报道」措辞并显式说明（2026-09-08 审查新增：附录 B 的 researcher/reviewer 引用此维度，原种子清单缺失会导致 Seeder 落空）                                                            |
| `practical_tutorial`    | 干货教程体        | WRITING   | 口语化、每章一个可执行要点、多用数字与案例、开头给出「读完能获得什么」                                                                                                                                                                                                         |
| `professional_analysis` | 深度分析体        | WRITING   | 克制冷静、论证链完整、数据引用规范、结尾给判断                                                                                                                                                                                                                                 |
| `photo_documentary`     | 纪实摄影风        | IMAGE     | 真实摄影质感（自然光、可信场景）；生图提示词按「主体/场景/光线/构图镜头/质感/负面约束」脚手架组织并显式含「真实摄影、无文字、无水印」；图注 ≤20 字说明画面与段落关系；每章至多 1 图、封面留标题安全区；禁同一篇混用摄影与插画（2026-09-11 新增：IMAGE 维度此前无种子，配图风格无处可选） |
| `flat_illustration`     | 扁平插画风        | IMAGE     | 扁平矢量插画（几何色块、克制留白、无渐变阴影）；提示词按「主题概念/场景隐喻/扁平矢量/几何色块/配色/负面约束」组织；配色跟随文章主题色、同一篇主色 ≤3；图注 ≤20 字点明抽象概念；封面四边留白（2026-09-11 新增） |
| `soft_three_d`          | 柔和 3D 渲染风    | IMAGE     | 柔和 3D 渲染（圆角造型、哑光磨砂材质、柔和漫射光、浅景深、简洁背景）；提示词按「主体造型/材质/打光/背景/视角景深/负面约束」组织；主题色仅作主体或点缀、背景低饱和；图注 ≤20 字说明物件在文中的角色（2026-09-11 新增） |

（内置 Skill 内容随版本 seed 更新；用户可克隆后自由修改。）

## 附录B：内置 Agent 种子清单


| builtin_key                 | code                      | name               | stage            | persona 要点                                                                                            | tool_keys                      | 默认 Skill       |
| --------------------------- | ------------------------- | ------------------ | ---------------- | ------------------------------------------------------------------------------------------------------- | ------------------------------ | ---------------- |
| `builtin_editor`            | builtin_editor            | 墨舟编辑智能体     | EDITOR           | 现 AGENT_DESCRIPTION 人设部分（交互式编辑定位）                                                         | BROWSER_EDITOR + MEDIA         | —               |
| `builtin_scheduled_creator` | builtin_scheduled_creator | 墨舟定时创作智能体 | SCHEDULED_SINGLE | 现 SCHEDULED_AGENT_DESCRIPTION 人设部分（自主研究创作定位）                                             | DRAFT_READ + DRAFT_WRITE + MEDIA | default_layout   |
| `builtin_researcher`        | builtin_researcher        | 调研员             | RESEARCH         | 信息检索与核实专员：多源交叉验证、时效性敏感、产出结构化简报（事实/来源/可引用素材/选题建议）           | RESEARCH + MEDIA               | fact_check_default |
| `builtin_writer`            | builtin_writer            | 撰稿人             | WRITING          | 公众号撰稿人：基于调研简报成稿，严格遵循排版模板与写作风格 Skill，完成即提交草稿                        | DRAFT_READ + DRAFT_WRITE       | default_layout   |
| `builtin_illustrator`       | builtin_illustrator       | 配图师             | ILLUSTRATION     | 视觉编辑：按图片风格 Skill 选图/生图/编辑，每章配图 + 封面，重存草稿                                    | MEDIA + DRAFT_READ + DRAFT_WRITE | —               |
| `builtin_reviewer`          | builtin_reviewer          | 审稿人             | REVIEW           | 资深内容审核：核对事实与来源、检查结构/风格/排版符合度、输出结构化审核结论（passed/issues/suggestions） | DRAFT_READ + MEDIA             | fact_check_default |
| `builtin_chief`             | builtin_chief             | 主编（协调者）     | COORDINATE       | 内容主编：理解任务目标，规划并委托调研/写作/配图/审核，依据审核结论决定返工，对最终交付负责             | DELEGATE + DRAFT_READ          | —               |

各 persona 完整文本在实施第②期时依据「协议拆分」（5.3 节）从现有两个 prompt 常量拆出并补写，保证语义不丢失。

---

## 实施记录（随各期追加）

- 2026-09-08 方案定稿并保存。尚未开始任何一期实施。
- 2026-09-08 增补：融合本机技能 `~/.zcode/skills/markflow-typeset/`（MarkFlow 渲染排版）为 LAYOUT 维度第二排版引擎。核心设计：排版 Skill 新增 engine/engine_config 字段（PROMPT/MARKFLOW 双轨）；`render_config` 表产品化存储渲染服务地址与令牌（替代本地 token 文件）；`MarkFlowRenderService` 实时缓存语法 guide（与线上引擎严格同步，不内置副本）；定时链路 save/read 草稿工具引擎适配（LLM 上下文只留 Markdown，渲染发生在工具边界，workspace 存 markdown/渲染 html 双份，下游交付零改动）；编辑器链路 `render_markflow` 工具 + `{{render:<renderId>}}` 占位替换（第④期）；`ArticleContentPolicy` 引擎感知校验。第①期范围与工期相应调整（7-9 人日），总工期 24-34 人日。仍未开始实施。
- 2026-09-08 二次增补（针对「上游组件库更新能否自动跟进」的评审问题）：① 语法缓存增加三级失效策略——TTL 过期（默认 6h）/ 渲染 400 疑似语法问题立即失效（应对 TTL 窗口内上游刚更新组件库的滞后）/ 测试连接与保存配置手动刷新；② 原「白名单清洗」措辞修正为「危险元素定向剥离」（黑名单语义、非严格标签白名单），明确该设计使上游新增组件产出的新标签无需改代码即可入库。组件库层面的上游更新可全自动跟进（最长滞后一个 TTL）；仅 API 响应字段契约变更需代码适配。
- 2026-09-08 三次增补：明确 MarkFlow 发布接口（`__markflow_wechat_publish`）刻意不集成——项目自有微信交付链路（WechatClient 草稿/发布、多账号加密凭据、状态追踪、权限与审计）已完整覆盖其能力，且该接口需把公众号 AppSecret 提交第三方服务，违反本项目密钥信任边界；仅集成渲染接口，渲染产物经现有链路交付。该边界已写入 5.10.1。
- 2026-09-08 审查回写（依据 docs/dev/skills-agent-plan-review.md，逐项核实代码后修订）：**5 处必修**——① publicUrl 实为相对路径（`AssetService:118` 存 `"/uploads/"+storageName`），与 MarkFlow http(s) 直链要求冲突，原「天然满足」判断有误；改为第①期 Spike 验证 + 「站点 Base URL/URL 绝对化」预案（微信交付不受影响，prepareWechatContent 本就重传本地图）。② LAYOUT 维度改单注入（其余 LAYOUT Skill 连内容丢弃），消除双引擎指令同现的混用矛盾。③ 第①-③期 EDITOR 场景过滤 MARKFLOW 技能并 UI 置灰，消除渲染能力真空窗口（render_markflow 第④期才交付）。④ 附录 A 补 `fact_check_default` 种子（原附录 B 引用落空，Seeder 会失败）。⑤ DRAFT 工具组拆分 DRAFT_READ/DRAFT_WRITE（chief「只读草稿」才可实现，返工护栏不被绕过）。**6 处 Minor**：侧边栏实为扁平 items 数组无分组（App.vue:16-26）；渲染响应含 preview 字段（服务端忽略、mock 按真实 payload）；「未知语法降级」系外推改为 Spike 验证项；MAX_TOOL_CALLS 现仅编辑器浏览器工具路径强制（requestTool:571-574），定时侧为新增强制点；temperature/maxTokens 现就未传给 LLMModel（createModel 仅 4 参，第②期前置确认 agent4j API）；补 413 与 HTTP 200+ok:false 防御。**4 处 MarkFlow 补充**：生产容器出网/HTTP 代理前提（Java HttpClient 不走代理环境变量，需显式 ProxySelector）；环境变量 MARKFLOW_RENDER_TOKEN 注入优先级；主题色验收以响应 theme 字段为判据（dark 由上游自动派生）；渲染延迟到交付前统一执行（省返工环外呼）。**设计细节**：60000 上限口径（不含 guide/协议）、内置 Skill 编辑将被 seed 覆盖的 UI 警示、read 对渲染区段返回占位摘要防 HTML 回灌、编辑器校验管道顺序与未解析占位符拒绝、digest 以 meta.summary 自动填充、新 API 纳入现有审计拦截。第①期重估 10-14 人日（含 0.5 天前置 Spike），总工期 28-39 人日（原 24-34 另有算术误差）。审查报告「会话固化 description」一项**部分驳回**：代码形态显示每次对话新建 AgentClient（带最新 description）再从序列化恢复会话状态（ArticleAiService:228-232），系统提示应随新 client 生效；agent4j 序列化会话是否携带独立系统提示无法本地确认（jar 不在本地仓库），已列为第④期验证点并附兜底（Skill 变更时重建会话）。
- 2026-09-08 **前置 Spike 实测结论**（真实令牌调渲染 API，第①期第 0 项完成）：
  1. **相对路径图片：上游原样透传**——`![x](/uploads/test-spike.png)` 渲染后 src 原样保留（未改写、未拼域名、未丢弃）。结论：**必须落地「站点 Base URL 配置 + 渲染前 URL 绝对化」**（否则远程渲染产物对最终读者仍是坏图）；实现点定在 save_article_draft 工具的 MARKFLOW 分支（服务端把 markdown 中 `](/uploads/…) / src="/uploads/…"` 替换为 `{base_url}` 前缀，base_url 来自新增 render_config 字段 `site_base_url`）。
  2. **未知语法：宽松透传不报错**——`:::nonexistent_widget` 容器内容按普通文本段落渲染；自造标签 `<unknown-tag>` 原样进 html（HTTP 200 + ok:true）。结论：上游无语法硬失败风险（低危），但**服务端危险元素剥离必须覆盖未知标签**（不能只剥 script/iframe，需按「非白名单属性 on*/javascript: 一律剥、未知标签降级为其内容」策略），已写入 MarkFlowRenderService 实现要求。
  3. **错误契约确认**：空 markdown → HTTP 400 + `{ok:false,error:"markdown 字段缺失或为空"}`；错误令牌 → HTTP 401 + `{ok:false,error:"X-Render-Token 无效"}`。状态码与 body.ok 一致，仍按「body.ok 为最终判据 + 状态码映射」双保险实现。
  4. **guide 获取**：GET 实测 200、guide 约 6.8k 字符（含 :::compare 等容器；未列 :::steps，2026-09-12 复测确认渲染器也**不支持** `:::steps`，见下 ⑬——guide 覆盖面以线上为准，坚持实时注入不内置副本的原则不变）。
  5. **正常渲染**：steps/TIP 提示框/figure 均正确渲染；meta.title/summary 稳定返回；theme 自动派生 dark（#27ae60 → #1d8348）；preview 字段确认存在（约 4.5k，服务端忽略）。
  6. 渲染接口平均耗时 0.2-0.5s，30s 超时设定充裕。
  附注：本机开发环境 JDK 17.0.11（`C:\Program Files\Java\jdk-17`）+ Node 24 + Docker MySQL 8.0（容器 momo-mysql-dev，root/change-me，库 wechat-article 与 wechat-article-test 已建）+ `.env` 已按 .env.example 配置（.env 已在 .gitignore）。
- 2026-09-09 **第①期实施完成**（Skill 体系 + MARKFLOW 渲染集成，多 agent 并行交付）：
  - **后端新增**：`skill` 包 12 文件（LayoutEngine / Skill / SkillMapper / SkillService / SkillController / SkillContext / SkillDimensions / SkillPromptAssembler / SkillPromptResult / SkillPreviewModels / MarkFlowRenderService / SkillSeeder），`settings` 包 4 文件（RenderConfig / RenderConfigMapper / RenderConfigService / RenderConfigController，端点 `/api/settings/render`）；三表（schedule_task / wechat_account / article）加列 `skill_ids`。
  - **两链路接线**：定时链路 runScheduledAgent 组装 SkillContext（task + account + defaultStyle，scene=SCHEDULED），按生效引擎构造 DraftState，MARKFLOW 延迟到交付前 `renderBeforeDelivery` 统一渲染；编辑器链路 editorSkillContext（article + account，scene=EDITOR，第④期前过滤 MARKFLOW）。核心协议拆分 EDITOR_CORE_PROTOCOL（14 条）/ SCHEDULED_CORE_PROTOCOL（7 条）代码常量，Skill 注入块按维度顺序（LAYOUT 最后、单注入、保底 default_layout）。
  - **前端**：SkillsView（CRUD+复制+预览）、SkillPicker（12 维度、enabledOnly、scene 置灰）、AgentsView 占位、TasksView/AccountsView/ArticleEditorView/SettingsView 接线；App.vue/router.js 注册。
  - **测试**：新增 25 个单测（SkillPromptAssemblerTest 13 / MarkFlowRenderServiceTest 8 / ArticleContentPolicyTest 4），全量 `mvnw test` 49 例仅 CoreApiIntegrationTests 1 例失败——**git stash 基线对照证实基线同样失败**（等待编辑器浏览器工具调用超时），定性为本机环境问题非本期回归；测试等待窗口 8s→30s 已放宽仍失败（异步结果 10s 未设值），留待环境修复后观察。
  - **reviewer 审查（FAIL→修复后达标）**：P0-1 skillIds 前后端契约不匹配（前端发数组、后端 String 字段会 400 + 读取丢显 + 列表 `.map` 崩溃）——已改为 `List<Long>` 接收（AccountRequest/TaskRequest/ArticleRequest），service 层归一为逗号分隔字符串落库，AccountView 返回 `parseSkillIds` 解析后的数组，ArticleService rollback 与 AI commitSession 补齐解析；P1-1 accent/dark 参数静默丢弃——save() MARKFLOW 分支现保存 saveAccent/saveDark，Draft record 改 themeAccent/themeDark；P1-2 前端 `is_builtin` 改 `isBuiltin`（Skill.isBuiltin 加 @JsonProperty("isBuiltin")）；P1-3 Seeder 不再覆盖用户对内置技能的 name/enabled 修改（仅同步 content/engine/engineConfig）；P2 修复：语法 TTL 前端 min=60 文案对齐后端 @Min(60)、测试连接文案改为「测试当前已保存配置」、JS_URI 清洗补无引号属性分支、normalizeBaseUrl 校验 http(s) scheme 并对 guide 路径生效、preview 忽略技能改轻量视图（不含 content）、删除死字段 renderTheme 与 getRenderTheme。P2-4（/preview 全员可触发出网）记录为待办：建议加每用户限流或限 ADMIN/OPERATOR。
  - **门禁终态**：`mvnw compile` 通过、单测全绿（除上述环境性 1 例）、`webui build` 零报错。
  - **教训记录**：多 agent 并行时 coder 两次挂起（112min 零产出 / 54min 中断），按 metadata updatedAt 停滞检测后 TaskStop + 重派 + 主 agent 接续完成；60000 上限校验曾重复实现两处，保留候选阶段一处。
- 2026-09-09 **第②③④期实施完成**（Agent 定义体系 + 模型档案 + 定时多 Agent 协作 + 编辑器链路集成）：
  - **第②期（agent 包 13 文件）**：`LlmProfile` / `LlmProfileMapper` / `LlmProfileService` / `LlmProfileController`（`/api/llm-profiles`，全 ADMIN）/ `LlmProfileMigrationRunner`（@Order(20) llm_config → 默认档案，幂等）；`AgentDefinition` / `AgentDefinitionMapper` / `AgentDefinitionService` / `AgentDefinitionController`（`/api/agents`，读全员写 ADMIN/OPERATOR，含 `/tool-groups`）/ `AgentProtocols`（7 个 stage 核心协议代码常量）/ `ToolRegistry`（8 个工具组，保存时校验）/ `AgentFactory`（按定义装配 AgentClient，定义缺失回落内置默认）/ `AgentSeeder`（@Order(30) 内置 7 agent 幂等 upsert，保留用户 persona/name/enabled 修改）。**两链路改走 AgentFactory**：编辑器用 `builtin_editor`、定时用 `builtin_scheduled_creator`；`LlmConfigService.runtime()` 内部改读默认档案、`GET/PUT /api/settings/llm` 双向映射默认档案（存量设置页不破坏），图片三件套仍留 llm_config。
  - **第③期（schedule 包 9 文件 + ai 包 2 文件）**：`TaskWorkspace`（DraftState 包装 + 调研简报/审核轮次/返工计数，全 synchronized）；`save_research_notes` / `submit_review` 工具（`TaskWorkspaceTools`）；`ScheduledExecutionStrategy`（**抽象类**）+ `SingleAgentExecutor` / `PipelineExecutor`（调研→写作→配图（可跳过）→审核，返工环含图片类 issues 才重跑配图、达上限停止、审稿人未提交视为通过）+ `CoordinatorExecutor`（chief + 4 个 delegate 工具）+ `ScheduledExecutionRouter`（`/api/tasks/execution-modes`）；`AgentRunner`（**抽象类** seam）+ `AgentInvoker`（会话运行器，带阶段日志前缀）；`DelegateTools`（委托预算 ≤8、返工 ≤maxRevisionRounds、子智能体工具调用 ≤24，超限返回引导文本）；`schedule_task` 加列 execution_mode/stage_agents/max_revision_rounds，`task_run` 加列 mode/stages_summary。
  - **第④期（ai 包 1 文件 + 编辑器接线）**：`EditorServiceTools`（`render_markflow` 渲染缓存 + `delegate_research` 委托）；`ArticleAiService` 的 EditorSession 新增渲染缓存与占位替换（`{{render:renderId}}` 在 SSE 下发前替换为 HTML，未解析占位符抛错）、read_article/read_blocks 对渲染区段返回占位摘要防 HTML 回灌、`render_markflow` 仅在 MARKFLOW 引擎下暴露；`ArticleEditorTools` 引擎感知（MARKFLOW 跳过纯段落校验）；`SkillPromptAssembler` **解除 EDITOR 场景 MARKFLOW 过滤**；`AgentProtocols.EDITOR` 补 2 条（delegate_research / render_markflow 用法）；内置 editor agent 工具组加 RENDER + DELEGATE。
  - **门禁**：`mvnw compile` 通过；新增单测 `ToolRegistryTest`(12) / `PipelineExecutorTest`(8) / `DelegateToolsTest`(6) / `RenderPlaceholderTest`(5) / `RateLimiterTest`(4) 全绿；新增集成测试 `AgentApiIntegrationTests`(5) 覆盖 `/api/agents` `/api/llm-profiles` `/api/tasks` 角色矩阵 403、内置 7 agent 种子、工具组校验、内置不可删、克隆、默认档案同步与掩码不回传 key、任务新字段透传与非法值拒绝；全量 **88 例全绿**。
  - **reviewer 审查（FAIL→修复后达标）**：**P0-1** 编辑器 `{{render:renderId}}` 占位替换未作用于 `editor.insert.delta` 流式通道（前端只消费 delta，字面量会落库）——已改为替换结果同时用于 `tool.call` 与 `streamBlocks`；**P0-2** MARKFLOW + PIPELINE/COORDINATOR 从不调用 `renderBeforeDelivery`（Markdown 源码落库交付微信）——已在 `TaskExecutionService` 落库前统一渲染（SINGLE 因 `rendered=true` 幂等跳过）；**P1-1** `task_run.mode`/`stages_summary` 因 `finishRun` 未复制而恒为 NULL——已补齐；**P1-2** 子智能体「≤24 次工具调用」只记日志未强制——新增 `AgentRunner.runWithLimit`，`AgentInvoker` 超限抛异常、`DelegateTools` 转为引导文本让 chief 收尾；**P1-3** Pipeline 后续轮次审稿人漏调 `submit_review` 会沿用上一轮旧结论反复返工——改为按「本轮审核轮次是否增长」判定，未提交即按通过；**P1-4** AgentSeeder 覆盖用户对内置 agent 工具组的增删——改为「并集补齐系统必需组」并仅在用户未设置时同步默认技能。**P2 修复**：档案掩码 <12 位 key 全掩码、`runtime()` 与 `view()` 口径统一（档案存在但不可用即视为不可用，不再静默回落）、`AgentFactory.defaultGroupsFor` 按 stage 精确兜底（审稿人/协调者不再拿到写草稿权限）、`/api/skills/preview` 每用户每分钟 30 次限流（新增 `RateLimiter`）、渲染区段识别改用 `data-render-id` 标记（TipTap 归一化后仍可识别）、`ArticleService.clean` 放行 `div` 与 `data-render-id`、`delegate_research` 事件改由通用 `serverToolStatus` 通道下发（避免前端双卡片）、`llmConfigService.runtime()` 移入 try 内（配置非法不再造成会话泄漏）、异步 chat 增加错误兜底、`ToolRegistry.groups()` 保留顺序、图片关键词去掉单字「图」防误触发。**P3 清理**：删除死代码 `AgentFactory.temperatureOf` / `RenderCache.cachedHtmls` / `SaveDraftTool.renderService` / `ScheduledAgentFactory.toolName` / `ArticleAiService` 未用注入、accent/dark 重复归一、名称比较空安全。
  - **关键工程约束（踩坑记录）**：① `@MapperScan("ink.icoding.wechat.article")` 会把该包下**所有顶层接口**注册为 MyBatis mapper bean——新增的 `AgentRunner`、`ScheduledExecutionStrategy` 因此改为**抽象类**，否则 Spring 上下文启动失败（`NoUniqueBeanDefinitionException: expected single matching bean but found 2`）；后续在该包下新增非 Mapper 顶层接口须沿用此约定。② `mvnw` 在本机离线环境无法重新下载 Maven 发行包（`curl: Failed to fetch apache-maven-3.9.16-bin.zip`），已新增 `.mvn/mvn-local.sh` 直接调用 `.m2` wrapper 缓存中已解压的 Maven（用法同 `./mvnw`，可加 `-o` 离线）。③ 两个 `@SpringBootTest` 集成测试类会改写全局状态（默认档案、管理员口令），已加 `@DirtiesContext(AFTER_CLASS)` 隔离，否则按类名顺序执行时互相污染（表现为定时 Agent 拿不到 LLM 配置而 `HTTP 200` 失败）。
  - **agent4j 能力确认（第②期第 0 项）**：`LLMModel.create` 仅 4 参（ModelType/baseUrl/modelName/apiKey），无 temperature/maxTokens 参数；AnthropicModel 硬编码 `max_tokens=4096`，OpenAI 两个实现未发送温度与上限。故 agent 定义与模型档案的 temperature/maxTokens 覆盖项**存而不生效**，前端已标注「待 agent4j 支持后接线」。
  - **遗留待办**：① temperature/maxTokens 待 agent4j 上游支持后接线；② `delegate_research` 的子智能体工具调用由 `runWithLimit` 强制上限，但尚无独立限流（`/api/skills/preview` 与 `/api/articles/{id}/ai/chat` 已各按每用户每分钟 30/20 次限流）。
  - **2026-09-09 自检优化补充**（第②③④期收尾后深度自检）：① 新增 `common/RateLimiter`（进程内滑动窗口）并对 `/api/skills/preview`（30/分）与 `/api/articles/{id}/ai/chat`（20/分）限流，4 个单测覆盖；② 清理死代码 `AgentFactory.temperatureOf`、`RenderCache.cachedHtmls`、`SaveDraftTool.renderService`、`ScheduledAgentFactory.toolName`、`ArticleAiService` 未用注入与 `ScheduledArticleTools` 兼容重载；③ accent/dark 归一去重；④ 定时链路 MEDIA 工具接入 `ToolMutationDeduplicator`（与编辑器一致，防重复生图计费）；⑤ 前端 `SkillPicker` 解除 EDITOR 场景 MARKFLOW 置灰（第④期能力已就绪），编辑器 `toolLabel` 补 `render_markflow` / `delegate_research` 友好文案；⑥ 新增 `.mvn/mvn-local.sh` 解决本机离线环境 `mvnw` 无法下载发行包的问题（直接调用 `.m2` 缓存中已解压的 Maven）。**终态门禁：`./.mvn/mvn-local.sh -o test` 93 例全绿 + `cd webui && npm run build` 零报错。** ⑧ **测试隔离根因修复（自检发现）**：多个 `@SpringBootTest` 类共享同一测试库，而 `MySqlTestDatabaseInitializer` 只在 **上下文首次创建** 时清库；Spring 上下文缓存导致后执行的类读到前一个类的残留数据（表现为 `revision expected:<1> but was:<2>`、`Table 'TASK_RUN' doesn't exist` 等随执行顺序波动的偶发失败）。修复：**全部 4 个集成测试类统一加 `@DirtiesContext(AFTER_CLASS)`**，每个类结束后销毁上下文、下一个类重新清库，测试顺序无关且可重复。 ⑨ **编辑器链路 `{{render:id}}` 端到端可验证性**：新增 `RenderPlaceholderTest`(5) 覆盖`markRenderId` 注入 / 裸文本包裹 / 空值 / 渲染结果 JSON 不含 HTML / 占位摘要无 HTML。 ⑦ **真实上游渲染产物核验（P2-7 闭环）**：用真实令牌实测 `:::steps`/`:::compare`/`:::warning`/图片渲染，产物标签仅 `h2/p/section/img`（**未使用 div**），内联样式含 flex/gap/linear-gradient 等；`ArticleService.clean()` 对 `section/figure/figcaption` 已放行，另补 `div` 与 `data-render-id` 属性以覆盖上游未来改用 div 的情形；新增 `ArticleHtmlSanitizeIntegrationTests`(2) 同时验证「div + data-render-id 存活 + script 剥离」与「真实产物内联样式不丢」。
  - **2026-09-09 二轮深度自检**（在上述基础上继续）：① **修复真实缺陷**：`PipelineExecutor.isSkipped` 原先允许 research/writing 配 0 静默跳过（方案 4.5 只允许配图/审核跳过），现改为必选阶段忽略跳过值并记日志；② `AgentDefinitionService` 名称唯一性比较改 `Objects.equals`（脏数据 name 为 null 不再 NPE）；③ `LlmConfigService.runtime()` 与 `view()` 口径统一——默认档案存在即以其为准（含「存在但停用/无 key」→ 返回不可用），不再出现「设置页显示 A、运行时用 B」；④ `AgentFactory` 新增 `preAssembled` 重载，编辑器链路复用已组装的 Skill 提示，避免 MARKFLOW 下一次装配重复拉取语法 guide；⑤ `ArticleAiService.requestTool` 占位符替换失败时清理 `pending`（原先 future 常驻到会话结束）；⑥ 删除 6 处未用 import 与 `SkillController.accountService`、`ScheduledAgentFactory.markFlowRenderService`、`ArticleAiService.agentDefinitionMapper` 等未用注入；⑦ `.mvn/mvn-local.sh` 增加 `MAVEN_HOME_DIR` 环境变量与多路径搜索（沙箱 HOME 与真实用户目录不一致时也能命中）；⑧ 新增 `AgentJsonContractTest`(3)：用真实 ObjectMapper 锁死 `isDefault`/`isBuiltin` 驼峰字段名（record 的 `isXxx` 访问器若被 Jackson 剥前缀会静默变成 `default`，前端徽标与删除保护会失效）。**二轮终态门禁：93 例全绿（含 5 个集成测试类）+ `webui build` 零报错，连续两轮全量测试稳定通过。** ⑨ **测试稳定性修复**：`CoreApiIntegrationTests` 的假 LLM SSE 服务原先用 `sendResponseHeaders(200, 0)`（分块流无长度）+ 默认 keep-alive，okhttp 偶发在流结束处报 `onFailure(HTTP 200)`，表现为「Tool Calling Agent 没有完成第二轮响应」的间歇性失败；改为 `Connection: close` + 显式 `Content-Length` 后连续 3 次单测通过。注意：**不要在同一个测试库上并行跑两个 Maven 进程**（会互相清库，表现为 ApplicationContext 批量加载失败）。 ⑩ **集成测试 Quartz 缺陷修复（两阶段定位）**：`@DirtiesContext(AFTER_CLASS)` 使每个集成测试类重建上下文，而 Quartz 表由清库流程与 Spring Boot 各自初始化，二者叠加出现两类间歇失败——a) `Table 'wechat-article-test.QRTZ_TRIGGERS' doesn't exist`（清库后表未及时重建）→ 在 `MySqlTestDatabaseInitializer` 清库后立即执行 `db/quartz/schema-mysql.sql`；b) 修 a 后暴露 `Couldn't determine trigger existence: Table definition has changed, please retry transaction`（任务创建接口 500）→ 根因是 **同一批 Quartz 表在 Scheduler 启动期间被重复 DDL**，故把测试配置 `spring.quartz.jdbc.initialize-schema` 改为 `never`（生产仍为 `always`），Quartz 表统一由测试初始化器负责。修复后连续 3 次全量 `93 例全绿`。⑪ **测试库并发保护（间歇性失败的最终根因）**：上述 a/b 修完后仍偶发 `tool-1 结果回传 401`（AUTH_TOKEN 表为空）与 `Agent4j Session 实际为 0`，定位为**同一测试库上存在并发的 Maven 进程**（自检时并行跑了多个全量测试，第二个进程的清库抹掉了第一个进程正在使用的 AUTH_TOKEN/会话数据）。`MySqlTestDatabaseInitializer` 现持有**JVM 生命周期级跨进程文件锁**（`java.io.tmpdir/wechat-article-test-db.lock`，静态字段持有 + shutdown hook 释放），第二个测试 JVM 会阻塞等待而非并发清库。**结论：测试必须串行执行，禁止在同一测试库上并行跑两个 Maven 进程。**
  - **2026-09-10 三轮深度自检（功能正确性 + 成本护栏 + 软引用容错）**：
    ① **调研简报未传入写作阶段**（功能缺陷）：`PipelineExecutor` 的写作指令只拼了任务要求与审核意见，调研员落盘的 `researchNotes` 从未注入——方案 5.5 明确「写作指令 = aiPrompt + 调研简报 + Skill 注入」。已修复并补单测 `writingCommandInjectsResearchNotes`。
    ② **agent 默认技能未生效**（功能缺陷）：方案 5.2 的候选优先级为「article > task > account > agent 默认」，但两条链路都把 `agentDefaultSkillIds` 传了空列表，内置 agent 绑定的默认技能（如 `default_layout`）形同虚设。现按内置 code 读取定义并注入（定时用 `builtin_scheduled_creator`、编辑器用 `builtin_editor`），读取失败降级为跳过并记日志。
    ③ **阶段工具调用无上限**（成本护栏缺口）：`PipelineExecutor` 各阶段原先用无限次的 `runner.run`，只有协调者子智能体受 24 次上限约束。现统一走 `runWithLimit`，并补单测 `stageRunsAreCappedByToolLimit`；`AgentInvoker` 追加「事后兜底检查」（回调异常可能被 agent4j 吞掉）。
    ④ **chief 自身无上限**：协调者主编新增 `MAX_CHIEF_TOOL_CALLS=48`（读草稿 + ≤8 次委托的合理余量）。
    ⑤ **媒体工具去重未跨子智能体**：每次装配都新建 `ToolMutationDeduplicator`，同一轮里 chief 与多个子智能体重复生图会被计费多次。现由 `CoordinatorExecutor` 整轮共用一个实例并下传。
    ⑥ **软引用容错缺失**（方案 5.7）：智能体绑定的模型档案被删除后，`AgentFactory` 原先用 `required()` 直接抛错，使该智能体不可用；现改为 `findById` 可空查找 + 回落默认档案并记日志。
    ⑦ **排版引擎静默降级**：`TaskExecutionService.resolveLayoutEngine` 原先吞掉异常回落 PROMPT，与方案 5.10.2「不静默回落（静默换引擎会产出完全不同的版式）」冲突；现让异常上抛，任务以明确错误失败。
    ⑧ **清理死代码**：删除无调用点的 `SkillPromptAssembler.resolveEngine` 与 `SkillPreviewModels.defaultStyleOf`。
    ⑨ 新增 `AgentFactoryTest`(5)：覆盖「按定义装配（协议+人设+技能拼接）」「定义缺失回落内置」「绑定档案被删除回落默认档案」「工具组键正确下传 resolver」「无档案时回落 llm_config」。**终态门禁：100 例全绿 + `webui build` 零报错。**
  - **2026-09-10 四轮深度自检（预算护栏全覆盖 + 宽松回退 + 空转防护）**：
    ① **定时单智能体无工具调用上限**：`runScheduledAgent` 原先只统计工具调用次数、从不限制，无人值守任务理论上可无限消耗；新增 `MAX_SCHEDULED_TOOL_CALLS=40`（该智能体独自完成调研+配图+写作，额度高于编辑器单轮 24），回调超限即抛错中止 + 事后兜底检查（agent4j 可能吞掉回调异常）。
    ② **`submit_review` 缺 passed 时硬失败**：审稿人没给结论会抛 `IllegalArgumentException` 让整轮任务失败、文章白写；现按方案 5.4 的宽松策略回退为「有条件通过 + 原文记为 issues」。
    ③ **空工具组可保存**：勾掉所有工具组能存下一个「什么都做不了」的智能体；后端新增拒绝（`请至少勾选一个工具组`）+ 前端同步拦截 + 集成测试回归用例。
    ④ 新增 `TaskWorkspaceTest`(7)：调研简报追加与轮次标记、空简报拒绝、审核轮次/返工计数/意见文本、`submit_review` 宽松回退与显式结论保留、`save_research_notes` 输入校验、工作区摘要字段。**终态门禁：107 例全绿 + `webui build` 零报错。**
  - **2026-09-10 五轮深度自检（主题色策略生效 + 服务端工具限流 + 缓存失效定向化）**：
    ① **`engine_config` 主题色策略从未注入**（功能不生效）：SkillsView 让用户选 AUTO/FIXED 与固定色值，但组装器只注入 guide、从不读取 `engine_config`——FIXED 主题色对 Agent 完全不可见。现注入「主题色策略」指令（FIXED 明示 accent/dark 并要求原样提交；AUTO 提示按对照表就近选择），配置非法时忽略并记日志。补 3 个单测。
    ② **编辑器服务端工具无额度**：`render_markflow` 与 `delegate_research` 不走浏览器管道因而不受 `MAX_TOOL_CALLS` 约束，可被无限调用（每次外呼渲染服务/拉起完整调研子会话）。新增 `MAX_EDITOR_RENDER_CALLS=10` 与 `MAX_EDITOR_DELEGATE_CALLS=3`。
    ③ **语法缓存失效过度**：`render()` 原先任何 `ok:false` 都清空 guide 缓存，主题色格式错误这类与语法无关的失败会白白触发一次额外外呼。现按错误关键词（语法/markdown/容器/未知/组件…）定向失效，空错误信息保守清缓存。补 2 个单测。
    ④ **限流器窗口无回收**：`RateLimiter` 的 `evictIdle` 只有测试调用；现改为 key 数超阈值时机会式清理（TTL 30 分钟），并保留公开方法供测试与运维使用。**终态门禁：112 例全绿 + `webui build` 零报错。**
  - **2026-09-10 六轮深度自检（P0 契约缺陷：engineConfig 对象 vs 字符串）**：
    **P0**：前端 `SkillsView.buildPayload()` 把 `engineConfig` 作为 **JSON 对象**提交（`{accentMode,accent,dark}`），
    而后端 `SkillRequest.engineConfig` 是 `String`——Jackson 无法把对象反序列化为 String（实测
    `MismatchedInputException: Cannot deserialize value of type java.lang.String from Object value`），
    **导致在界面上新建/编辑 MARKFLOW 排版技能（选固定主题色）必然 400**。这与第①期 P0-1（skillIds 数组 vs String）
    是同一类契约错位，此前所有轮次都未被发现，因为没有任何集成测试覆盖 `POST /api/skills` 的 engineConfig 字段。
    修复：`SkillRequest.engineConfig` 改为 `Object`，`normalizeEngineConfig` 同时接受 JSON 对象（前端）与
    JSON 字符串（脚本/外部 API），统一归一为紧凑 JSON 字符串入库。
    另新增 `SkillApiIntegrationTests`(7)：内置 7 技能种子与不可删、**engineConfig 对象形态与字符串形态各一例**、
    维度/引擎/内容校验、非 LAYOUT 维度引擎置空、更新与维度过滤、克隆命名、VIEWER 写 403 读 200、preview 组装。
    **终态门禁：119 例全绿 + `webui build` 零报错。**
  - **2026-09-10 七轮深度自检（跨端字段形态：skillIds 读取丢显 + TTL 0 被拒）**：
    ① **任务/文章页的技能绑定读取后丢失**（功能缺陷，与第①期 P0-1 同源的另一半）：后端 `skill` 持久化统一为
    「TEXT 存逗号分隔字符串」，但**各接口返回形态不一致**——`/api/accounts` 返回已解析数组（AccountView），
    而 `/api/tasks`、`/api/articles/{id}` 返回**原始逗号字符串**。前端三处用 `Array.isArray(x.skillIds) ? ... : []`
    判断，字符串一律被重置为 `[]`：**任务编辑表单里已绑定的技能被静默清空、下次保存即丢失绑定**；
    `TasksView.taskSkillNames(task)` 更会直接对字符串调 `.map` 抛 TypeError，**任务列表页在绑定技能后即崩**。
    修复：新增共享容错解析器 `utils/skills.parseSkillIds()`（兼容数组 / 逗号串 / JSON 串三种形态），
    TasksView / ArticleEditorView / AccountsView 统一改用；并新增 `SkillApiIntegrationTests.articleSkillIdsRoundTrip`
    锁死「文章级绑定落库为逗号串 + 读回同形态 + 更新后回读」。
    ② **渲染服务 TTL 清空即 400**：前端 `Number(x)||0` 把空输入框发成 0，后端 `@Min(60)` 直接拒绝；
    现前端归一为「空 → null（后端用默认 21600）、越界 → 明确提示」，并新增 `RenderConfigApiIntegrationTests`(7)
    覆盖令牌加密与掩码、TTL 归一与越界、clearToken、启用无令牌拒绝、VIEWER 403、/test 优雅失败。
    **终态门禁：127 例全绿 + `webui build` 零报错。**
  - **2026-09-10 八轮深度自检（解绑不可清空 + 渲染标记注入位置）**：
    ① **解绑技能无法保存**（功能缺陷）：`ArticleMapper.updateContent` 用 `if (changes.getSkillIds() != null)` 守卫写入，
    而「取消勾选全部技能」时值为 null → **解绑被静默忽略，用户以为已取消、刷新后又出现**。已改为直接赋值；
    并补 `clearingArticleSkillIdsPersists`（文章）+ `clearingTaskAndAccountSkillIdsPersists`（任务/账号）
    + `agentSkillIdsRoundTripAndClear`（智能体）三组回归，覆盖四个绑定层级。
    ② **渲染标记可能注入到注释里**：`markRenderId` 用 `indexOf('>')` 找第一个尖括号，若渲染产物以 `<!-- -->` 或
    `<!DOCTYPE` 开头，`data-render-id` 会被插进注释内部——产物结构被破坏，且 `read_article` 识别不到渲染区段（防回灌失效）。
    改为用 `FIRST_TAG` 正则定位真正的开始标签，并补 2 个单测（注释前缀 / DOCTYPE 前缀）。
    **终态门禁：131 例全绿 + `webui build` 零报错。**
  - **2026-09-10 九轮深度自检（空值 NPE 三处 + 账号字段白名单 + 服务端工具总额度）**：
    ① **`Set.of(...).contains(null)` 抛 NPE（会变 500）**：`AgentDefinitionService` 校验 stage 时先 `contains(request.stage())`，
    stage 缺失即 NPE；`ArticleAiService` 的 `BROWSER_TOOL_NAMES.contains(tool.getName())` 同理——后者更严重，
    一次空名字的工具事件会**打断整条 SSE 流**。两处均补显式判空（新增 `isServerSideTool` 辅助方法）。
    ② **账号类型/状态无白名单**：`AccountRequest.status` 任意字符串都会落库，而 `WechatClient` 用 `"ACTIVE".equals(status)` 判停用——
    写错一个值就**静默让公众号不可用**；`accountType` 同样无校验，且 create 分支把显式传入的 status 覆盖为 ACTIVE
    （语义是「收下但忽略」）。现补 SERVICE/SUBSCRIPTION 与 ACTIVE/DISABLED 白名单，create 尊重显式状态，
    并新增 `accountTypeAndStatusValidated` 集成测试。
    ③ **编辑器服务端工具无总额度**：`MAX_TOOL_CALLS` 只约束浏览器工具（走 `requestTool`），素材检索/导入/生图/改图
    等服务端工具不受限——一次对话可无限调用生图接口。新增 `MAX_SERVER_TOOL_CALLS=24` 独立计数 + 会话结束后兜底检查。
    ④ 前端 `AgentsView` 的本地 `parseSkillIds` 副本改用 `utils/skills` 共享实现（避免各页面重复实现再次漂移）。
    ⑤ **环境提示**：Docker Desktop 与 `momo-mysql-dev` 容器会随机器休眠停止，集成测试报
    `Communications link failure` 时先 `docker start momo-mysql-dev`（非代码缺陷）。
    **终态门禁：132 例全绿 + `webui build` 零报错。**
  - **2026-09-10 十轮深度自检（本地部署实机走查，发现编辑器页 P0 前端缺陷）**：
    本轮改用「本地起服务 + 浏览器真实走查」而非仅读代码/跑测试，覆盖 11 个页面 + 关键交互流。
    ① **`ArticleEditorView.vue` 用了 `parseSkillIds` 却没有 import**（P0，实机必现）：第⑦轮把文章级技能绑定
    改用共享解析器时，只改了调用点、漏了 import。`vite build` 与后端单测**都不会失败**——ESM 未导入的标识符
    只在浏览器运行时抛 `ReferenceError`，表现为编辑器页顶部常驻红色错误条
    `parseSkillIds is not defined`，且**文章技能绑定读写整条链路失效**（加载文章即抛错，`article` 未被赋值）。
    已补 import；实机复验：绑定 MarkFlow 技能 → 保存 → 数据库 `SKILL_IDS=4` → 解绑 → 保存 → `SKILL_IDS=NULL`，
    读写与清空均正常。
    ② **补构建期护栏，防止同类错误再次逃逸**：新增 `webui/scripts/check-shared-imports.mjs`，
    静态扫描 `src/utils/*.js` 的每个导出符号，若在某源文件中被使用却既未 import 也未本地声明则**报错退出**；
    已挂在 npm `prebuild` 上，因此 `npm run build` 与 CI 都会拦截。已用探针文件实测：
    缺 import 时构建以退出码 1 中断，删除探针后恢复通过。
    ③ **实机走查结论（11 个路由全部无错误横幅）**：工作台 / 公众号管理 / 文章管理 / 定时任务 / 技能库 /
    智能体 / 公众号用户 / 系统用户 / 素材库 / 操作审计 / 系统设置，均正常渲染，`main h1` 与导航完整。
    关键链路实机验证：登录 → 智能体 7 个内置（工具组 8 组、阶段筛选正常）→ 技能库 7 个内置
    （MARKFLOW 引擎与引擎配置表单单选、主题色 FIXED 色值预设按钮）→ 编辑 MarkFlow 技能改主题色并保存落库
    → 定时任务新建 PIPELINE（阶段编排四个下拉、返工轮次、执行模式单选）并保存、列表卡片显示
    `调研·调研员 · 写作·撰稿人 · 配图·配图师 · 审核·审稿人` → 系统设置（默认档案提示、模型档案 CRUD 与掩码、
    排版渲染服务区含 token TTL 归一）→ 新建文章 → 绑定/解绑技能往返。
    ④ **实机环境注意点（非代码缺陷）**：`spring-boot:run` 在离线模式（`-o`）下不可用，
    因为 `spring-boot-maven-plugin` 的 `loader-tools`/`buildpack-platform` 未在本地缓存；
    `./.mvn/mvn-local.sh` 去掉 `-o` 联网一次即可正常启动。另外**不要用 curl 从 Git Bash 发含中文的 JSON**
    （控制台编码会把 UTF-8 中文转成 GBK 单字节，服务端报 `Invalid UTF-8 start byte 0xb2` → 400），
    改用 `--data-binary @file.json` 传文件即可。
    **终态门禁：132 例全绿 + `webui build`（含新增 prebuild 自检）零报错 + 11 路由实机零错误横幅。**

  - **2026-09-10 十一轮深度自检（审查报告遗留项全量回核 + 文档完整性）**：
    本轮以 `docs/dev/skills-agent-plan-review.md` 的遗留关注点为清单逐项对照代码核销：
    ① **审查 2.1（渲染 URL 绝对化）已落地**：`MarkFlowRenderService.absoluteImageUrls` 渲染前把
    `](/uploads/…)` 与 `src="/uploads/…"` 替换为 `{site_base_url}/uploads/…`（未配置时跳过并 warn 日志）；
    设置页已有「本站公网地址（Site Base URL）」表单项。② **审查 4.x 已落地**：令牌环境变量
    `MARKFLOW_RENDER_TOKEN` 优先于设置页存储（`RenderConfigService.envToken`，GET 返回「已由环境变量注入」）；
    渲染以 `body.ok` 为最终判据（HTTP 200 + ok:false 同样视为失败）；413 单独映射为 2MB 上限中文提示。
    ③ **审查 5.7/5.8/5.10/5.11 已落地**：编辑器 `read_article/read_blocks` 对渲染区段回灌占位摘要
    （`data-render-id` 识别 + 回退精确匹配，`compactReadResult`）；占位符替换在 SSE 下发前完成且未解析占位符抛错；
    MARKFLOW 交付前 `renderBeforeDelivery` 统一渲染一次且 digest 缺失时以 `meta.summary` 自动填充；
    skill/agent/llm_profile/render_config CRUD 均经 `/api/**` 的 `AuditInterceptor` 纳入操作审计。
    ④ **架构核对结论（7 内置 Agent / 三执行策略 / 预算护栏 / 协议与人设分离）与两轮自检一致，无新缺陷**；
    深挖并发/资源生命周期：EditorSession 经 `finishSession`（finally 兜底）+ emitter 三回调双保险关闭，
    `pending` future 超时/异常路径均已清理；`AgentInvoker` 预算护栏有回调内 + 最终计数双重检查。
    ⑤ **修复 1 处文档/UX 缺口**：SkillsView 编辑内置技能时无「修改将被版本更新覆盖」警示（审查 5.5 明确要求），
    已在编辑弹窗顶部补警示条（含「先克隆再修改」引导）；`form.blank()` 补 `isBuiltin` 字段。
    ⑥ **修复文档完整性缺口**：两份 README 的「首次部署后的配置顺序」此前**缺少排版渲染服务步骤**，
    已插入「第三步：配置排版渲染服务（可选）」（Base URL / 令牌 / Site Base URL / 出网与代理前提），
    后续步骤顺延重编号；「关键环境变量」表补 `MARKFLOW_RENDER_TOKEN` 行。
    **终态门禁：132 例全绿 + `webui build`（含 prebuild 自检）零报错。**

  - **2026-09-10 十二轮深度自检（传输层测试盲区 + 演示资料陈旧）**：
    本轮从「测试覆盖是否只覆盖了桩、CI 绿是否等于真实可用」和「文档里的截图是否还代表当前产品」两个方向复查。
    ① **补齐渲染客户端传输层测试盲区（本轮唯一代码新增）**：原有 `MarkFlowRenderServiceTest` 通过覆写
    `httpGet`/`httpPost` 打桩，**永远走不到真实传输层**——请求头（`X-Render-Token`）、请求体内容
    （Markdown 是否已绝对化、主题色是否下发）、以及状态码→中文提示映射（401 / 413 / 5xx / 响应不可解析）
    全部无测试。新增 `MarkFlowRenderServiceHttpTest`：用 JDK `HttpServer` 起随机端口真实回声服务，
    服务端上下文 `/__markflow_render` 记录每次请求方法/路径/令牌头/Content-Type/请求体，
    响应体由预置队列 `responses.poll()` 出队（未预置时兜底 500）。7 例覆盖：
    真实 HTTP 下发令牌头 + 请求体含绝对化图片 URL 且不含 `](/uploads/` + 主题色 `#27ae60`；
    `fetchSyntaxGuide` 走 GET 且带令牌；guide 失败上抛上游错误文案；
    401 → 「鉴权失败」、413 → 含「2MB」、500 → 含「HTTP 500」与上游文本、非法 JSON → 「无法解析」。
    **测试总数 132 → 139。**
    ② **修复演示资料陈旧（文档与演示完整性缺口）**：`docs/images/*.png` 全部为 2026-09-07 拍摄，
    **早于 Skills / Agent 两期交付**，侧边栏里根本没有「技能库 / 智能体」两项，与当前产品不符。
    本轮在本地实例（后端 8081 + Vite 5173）用代表数据（公众号「产品手记」、素材 4 张、
    文章「写好一篇公众号文章的 3 个判断」、PIPELINE 定时任务「每日科技早报」）重拍全部 9 张旧图，
    并**新增 `skills.png`（技能库）与 `agents.png`（智能体定义）**两张；两份 README 的产品预览中
    新增「技能库与智能体」小节引用这两张图。拍摄方式记录：headless Chrome 的一次性 `--screenshot`
    会**在 Vue 挂载前抢跑**（DOM 已完整但截图全白），必须走 CDP：创建 target → 直连该 page target
    自己的 WebSocket（在 browser 端点用 `Target.attachToTarget(flatten)` 发页面级命令会报
    `'Page.enable' wasn't found`）→ 轮询等待目标选择器出现 → 关动画/等字体/等两帧 rAF → `Page.captureScreenshot`。
    ③ **仓库卫生**：确认上一轮误入工作区的 `t.json` 测试载荷已删除；演示过程中产生的
    `.tmp-demo/`（CDP 驱动脚本与截图）与 `webui/__shot.html`（headless 注入 token 用）已全部清理，
    `git status` 无临时文件残留。
    ④ **复核结论**：架构（7 内置 Agent / 三执行策略 / 预算护栏 / 协议与人设分离）、
    markflow-typeset 融合（仅集成渲染接口、发布接口因需上交 AppSecret 刻意不集成）、
    前后端契约（`skillIds` 形状容忍解析、`stageAgents` 数值 id）均无新问题；
    本轮未发现其他可改进点。
    **终态门禁：139 例全绿（`./.mvn/mvn-local.sh test`，BUILD SUCCESS，52s）+ `npm run build`（含 prebuild 共享导入自检）成功。**
  - **2026-09-10 十三轮深度自检（Docker 部署配置断链修复）**：
    ① **发现真实部署缺口**：README「第三步：配置排版渲染服务」承诺环境变量 `MARKFLOW_RENDER_TOKEN`
    「适合 Docker 部署」，但 `compose.yaml` 的 `environment:` 从未透传该变量——照文档在 env 文件里
    填写后容器内 `System.getenv` 读不到，环境变量优先机制在 Docker 路径上形同虚设。已在
    `compose.yaml` 增加 `MARKFLOW_RENDER_TOKEN: ${MARKFLOW_RENDER_TOKEN:-}`（不设为必填，
    不使用渲染式排版时留空即可），并在 `deploy/env/{dev,staging,prod,native}.env.example`
    四个样例文件补注释行说明可选性与优先级。`docker compose config` 语法校验通过。
    ② **核销确认**：两份 README 的环境变量表此前已有 `MARKFLOW_RENDER_TOKEN` 行（十一轮补）；
    `LlmProfileService.delete()` 对默认档案有防误删守卫；compose 截图/文案与代码行为一致。
    本轮其余深挖（DelegateTools 预算超限引导文本、AgentInvoker 双重检查、AuditInterceptor 覆盖面）
    无新缺陷。
    **终态门禁：139 例全绿（`./.mvn/mvn-local.sh test`，BUILD SUCCESS，1m19s）；compose 校验通过。**

  - **2026-09-10 十四轮深度自检（真实令牌端到端验收 + 渲染产物走查 + 版式持久化缺陷）**：
    本轮按「真实令牌走通渲染链路并人工走查渲染产物」执行，走查过程本身发现并修复了一处此前无人察觉的 P0 级数据缺陷。
    ① **真实渲染令牌端到端验收（补齐第②项长期只在「代码/桩」层验证的空白）**：令牌取自
    `~/.zcode/secrets/markflow-render-token`（48 字符），经产品自身 API 写入渲染配置
    （`PUT /api/settings/render` → `hasToken:true`、`tokenMasked:"••••••••299f"`、`enabled:true`），
    `POST /api/settings/render/test` 返回「连接正常，语法指令 6815 字符」。随后驱动完整定时智能体链路
    （任务「E2E 渲染式排版验收」，skill=MarkFlow 精排版式，SINGLE，LOCAL_DRAFT）对真实渲染服务产出文章：
    两次运行均 SUCCESS，产物 6585 字符全内联样式 HTML，主题色 `#0984e3` 已下发，
    `> [TIP]` 转为 `<section>` 提示框，Markdown 表格转为 `<table><thead>/<tbody>` 且带边框与斑马纹，
    无字面量 `:::` 残留。**同时验证了两条此前只在单测里声称成立的契约**：
    图片 URL 绝对化（渲染产物为 `http://localhost:8081/uploads/e2e-sample.png`，无相对 `src="/uploads/` 残留）；
    digest 缺省时由渲染服务 `meta.summary` 自动回填。
    ② **走查发现的 P0 缺陷：MARKFLOW 文章没有留存渲染源（本轮核心修复）**。编辑器只认识渲染产物 HTML，
    加载后会重新序列化——实测「只改标题」触发自动保存，正文被改写为编辑器 HTML
    （`<span>/<em>` → `<strong>/<p>`、内联样式重排、表格补 `colgroup`），渲染产物 24 处主题色
    `#0984e3` 变为 `rgb(9, 132, 227)` 且丢失 `<thead>` 语义，**改个标题就把公众号版式降级成了普通 HTML**；
    更严重的是文章表**从未保存 layout_engine 与 Markdown 源文**（`Draft` 里携带的 `contentMarkdown`/
    `layoutEngine` 在 `TaskExecutionService` 落库时被丢弃），渲染产物一旦被覆盖即无法重排，也无从判断某篇文章
    该不该走渲染。修复：`article` 表新增 `layout_engine` / `content_markdown` 两列；`ArticleRequest` 携带引擎与
    Markdown；`ArticleService.applyLayout()` 落库引擎与源文（请求不带引擎时不得把 MARKFLOW 抹回 PROMPT）；
    定时与编辑器 AI 两条交付路径均带上引擎与源文；`updateContent` 同步写这两列。
    ③ **配套的版式保真策略**：新增 `ArticleService.reconcileRenderedLayout()`——MARKFLOW 文章保存时比较
    正文**纯文本**：文本未变（只改标题/摘要等元数据）则保留库中原有渲染产物与 Markdown 源文，正文确实被改动时
    按编辑器结果落库并**清空已与正文不符的 Markdown 源文**（留着会让后续重排悄悄覆盖用户刚做的编辑）。
    实测复验：改标题后 `contentHtml` 仍为 6585 字符原渲染产物、`layoutEngine=MARKFLOW`、Markdown 源文保留。
    ④ **渲染器语法实测纠偏**：语言直取线上 `guide` 逐项探测后发现，`markflow-typeset` 技能与内置
    `MarkFlow 精排版式` 技能正文中「步骤用 :::steps / 提示用 :::tip / :::warning」的说法与渲染器实际能力不符——
    `:::compare` 渲染为对比网格（正确）；提示框的正确写法是引用块加标记
    `> [TIP] 内容` / `> [WARNING] 内容`（实测转为绿/橙底 + 左侧色条）+ 图标。已修正技能种子文案与
    `render_markflow` 工具描述（改为「以系统提示中的实时语法指令为准」并给出实测结论），重启后 Seeder
    已将修正同步进库。
    > **2026-09-12 更正（F9，详见 `known-issues-handoff.md` 的 F9）**：本轮判「`:::tip` / `:::warning`
    > 不被识别」**有误**。当时的探针把这几条容器写成了未闭合（或未顶格）的形态，落进产物的 `:::` 是
    > 写法问题，不是渲染器不认。逐种复测：`:::tip` / `:::note` / `:::info` / `:::warning` / `:::caution` /
    > `:::important` 六种**全部**渲染成与 `> [TIP]` 完全相同的提示框（`border-left:4px`），
    > `:::tip 自定义标题` 还能覆盖默认标题；真正不支持的只有 `:::danger` / `:::success`（字面输出 `::: danger`）。
    > `:::steps` 的结论同样要收窄：它确实不能用，但不是「渲染成编号圆点步骤流」，而是**整个容器退化成普通段落**
    > （0 个步骤节点）。六种提示框容器已从保存自检的「不支持的容器」名单移出——误报会把模型从一条好写法上劝退。
    ⑤ **可见性补强**：MARKFLOW 文章此前在文章列表与其他文章无任何区别，用户不知道编辑正文会改版式。
    列表来源列新增 MARKFLOW 徽标（title 提示「在编辑器里手工改动正文会变成普通 HTML 版式」）。
    ⑥ **验收与回归**：新增 `MarkFlowArticleLayoutPersistenceTests`(3)——引擎与 Markdown 源文随文章落库、
    只改标题不覆盖渲染产物、正文改动时落库新正文并丢弃过期 Markdown。**全量 142 例全绿**，`npm run build` 零报错；
    用真实浏览器复验标题保存后版式保持不变。
    ⑦ **本轮遗留（已记录，未在代码层解决）**：`MarkFlowRenderService` 未使用 MarkFlow 的 `preview` 字段，
    渲染产物入库后丢失 KaTeX 的 `aria-hidden` 与上游配套 CSS（实测正文中 `<style>` 会被 Jsoup 清洗剥离，
    数学公式在公众号侧需上游内联样式兜底）；`article.content_markdown` 目前是「留存可重排」的事实依据，
    但尚无「用 Markdown 重新渲染覆盖正文」的产品入口，重排能力待后续按需开放。

  - **2026-09-10 十五轮深度自检（样式层：SkillsView 主题色区 / TasksView 执行模式卡片 / 编辑器收起态）**：
    本轮起点是用户对「技能库 → 编辑排版技能 → 主题色模式」区域的主观反馈「这个样式不好看」，
    按「把主观感受变成可测量证据」的思路定位，结果是**一类跨页面的系统性样式失效**，而非单点观感问题。
    ① **根因（CSS 层叠/特异度陷阱）**：`style.css` 里的通用表单规则
    `.login-card label,.form-grid label{display:flex;flex-direction:column;gap:7px}` 与
    `.form-grid input,.form-grid select{height:42px;padding:0 11px}` 特异度均为 **(0,2,0)**，
    而本意覆写它们的裸类选择器 `.skill-engine-option`(0,1,0)、`.exec-mode-card`(0,1,0)、
    `.skill-accent-modes label`(0,1,1)、`.tool-group-option`(0,1,0) 都更低——
    只要这些卡片被放进 `.form-grid` 内（Skills/Tasks/Agents 三页的弹窗都是），
    就会被强制变成「radio 独占一行 + 文字换行 + 输入框 42px 高」的竖排布局。
    截图里看到的「pill 变高椭圆、radio 换行」正是这一条规则的产物。
    **修复手段统一为「把父容器类写进选择器」**：`.skill-engine-section .skill-engine-option`、
    `.skill-accent-block .skill-accent-mode`、`.agent-section .tool-group-option`、
    `.exec-mode-grid .exec-mode-card`（0,3,0），并在每条规则上留一行注释说明为何要带父级，
    避免后来者把它「化简」回裸类又复发。
    ② **SkillsView 主题色区重做**：原实现把 AUTO/FIXED 做成 `border-radius:999px` 的胶囊并塞进一行，
    两个输入框（accent/dark）也挤在同一行。现改为——`skill-engine-copy` / `skill-accent-copy` 双层文案结构，
    AUTO/FIXED 改成 1fr 1fr 网格卡片（radio 左、标题+说明右），AUTO 带「推荐」徽标，
    hint 文案随模式切换（FIXED 说明「所有文章统一套用下方配色」，AUTO 列出七个可选项）；
    FIXED 面板包成独立白底描边卡片，accent / dark **各占一行**，
    accent 行右侧加 `skill-swatch-pair` 双色圆点实时预览，预设色块区加虚线分隔。
    CDP 实测：`.skill-engine-option` = grid `16px 284.5px` / 336×63、`.skill-accent-mode` = flex row / 336×57、
    `.skill-accent-modes` 两列 335.5px、FIXED 面板 680 宽、两行色值行高 42、预设 7 个且高 31。
    ③ **TasksView 执行模式卡片（同源缺陷）**：`.exec-mode-card` 被压成竖排、卡片高 140px 且 radio 换行；
    提升特异度后实测 `flex-direction:row`、卡片 304×**77**、radio 16×16、三列 `304px 304px 304px`，
    视觉上「图标 + 标题（含 PIPELINE 推荐徽标）+ 描述」恢复正常横向排布。
    ④ **补齐整片缺失样式（此前从未写过 CSS，属静默退化）**：`task-mode-row` / `mode-badge`
    （含 `.mode-single/.mode-pipeline/.mode-coordinator` 三态配色，后者为紫色以区分协调模式）、
    `task-mode-note`、`exec-mode-icon` / `exec-mode-copy` / `exec-mode-recommend`、
    `stage-orchestration` / `stage-orch-head` / `stage-orch-warn` / `stage-orch-grid` / `stage-skip-hint` / `stage-rounds`、
    `run-stage-chips` / `run-log` / `run-log-lines`（含 `.log-error/.log-tool/.log-info` 三色）、
    `paragraph-toolbar`、`.editor-body.chat-closed`。这些类名在模板里早就存在，但样式表里定义为 0 条，
    构建与测试都不会报错——这也是本轮唯一「非用户直接反馈但确为真实缺陷」的部分。
    CDP 实测运行记录弹窗：7 个模式徽标（「单智能体」宽 57）、8 个阶段摘要 chip、日志区 `overflow-y:auto` 正常。
    ⑤ **编辑器侧核对**：`.editor-body.chat-closed{grid-template-columns:1fr}` 生效（实测 `grid-template-columns`
    由 `920px 360px` 变为 `1280px`，`.ai-panel` display:none，右下角「打开 AI 助手」按钮出现，无右侧留白）。
    ⑥ **构建与回归**：`npm run build`（含 prebuild 共享导入自检）通过，仅既有 chunk>500kB 警告；
    Java 侧本轮未改动，仍复跑全量测试以保持门禁记录一致。
    ⑦ **过程中排查的异常**：中途出现两次「刚写好的规则读回为 0 条」，根因是并发写入
    （另有清理任务同时触碰同一批文件），经文件哈希三次采样确认最终状态稳定后继续，未造成残留。
    **终态门禁：142 例全绿（`./.mvn/mvn-local.sh test`，BUILD SUCCESS，1m22s）；`npm run build` 零报错；四个视图（技能库/定时任务/运行记录/编辑器）CDP 实测与截图复核通过。**
  - **2026-09-10 十六轮深度自检（渲染组件语法实测纠偏 + Markdown 源文丢失 P0）**：
    起点是用户反馈 —— **`/articles/3` 页面「渲染的有问题」**。按「把主观感受变成可测量证据」的思路，
    用 CDP 在真实页面上量化（`getBoundingClientRect` + 子节点枚举 + 字面量探测），
    定位出**三个相互独立的缺陷**，其中一个是会永久损坏数据的 P0。
    ① **语法层实测结论（对线上渲染器逐项探测，共 17 组写法变体）**：此前十四轮记录的「`:::steps` 有效」
    **是错的**。`:::steps` 会把容器内**每一个段落**都拆成独立步骤，每个「步骤」只是一段文字塞进一个
    直径 38px 的圆形里（`width:38px;height:38px;border-radius:50%`，长文本直接溢出）；
    配上 `###` 小标题时，标题行与正文段被拆成两个这样的圆形，字面 `###` 也直接出现在产物里。
    可用的步骤写法只有 `<steps>` 标签：每步一个自然段、空行分隔、步骤内**不要**写 `###` 小标题。
    标签徽章用 `<badges type="accent">A|B</badges>`（竖线分隔的胶囊标签组）。
    提示框 `> [TIP]` / `> [WARNING]` 正确，双栏对比 `:::compare` 正确。实测对照表：

    | 写法 | 实测产物 | 结论 |
    |------|---------|------|
    | `:::steps` + 每步 `###` 标题 | <s>38px 圆形步骤</s> **实为退化成普通段落**；字面 `###` 入库 | ❌ 禁用 |
    | `:::steps` + 无标题自然段 | <s>3 个 38px 圆形</s> **实为退化成普通段落（0 个步骤节点）** | ❌ 禁用 |
    | `<steps>` + 每步一个自然段（3 步） | 编号步骤卡：1 张表 3 个 td、各 33% 宽，编号为 `font-size:22px;font-weight:900` | ✅ 采用 |
    | `<steps>` + 4 步（不给 type） | 仍是 4 列各 25% **横向** | ⚠️ 需显式 DA02 |
    | `<steps>` + 5 步（不给 type） | 仍是 5 列各 20% **横向** | ⚠️ 需显式 DA02 |
    | `<steps type="DA02">` + 4 步 | 竖向卡片：每步 32px 圆形编号 + 16px 内边距卡片 | ✅ >3 步采用 |
    | `<badge type="tip" title="推荐" />` | <s>渲染出的是 type 值</s> **实为渲染出 title「推荐」（`text=` 与 `<Badge>` 写法同样有效）** | ✅ 采用（须自闭合） |
    | `<badge type="tip">推荐</badge>`（成对） | 徽章本身正常，但 `</badge>` 原样吐进正文（产物文本 `tip推荐</badge>`） | ❌ 禁用 |
    | `<badges type="accent">A\|B</badges>` | 胶囊徽章（`border-radius:999px`），竖线分隔多项 | ✅ 采用 |
    | `> [TIP] 内容` | 绿底 + 左侧色条提示框 | ✅ 正确 |
    | `:::tip` / `:::warning` | <s>退化为普通段落</s> **实为与 `> [TIP]` 完全相同的提示框** | ✅ 识别 |

    > **2026-09-12 更正（F9，详见 `known-issues-handoff.md` 的 F9；被划掉的都是本轮推翻的旧结论）**：
    > 表中三处结论是当时探针的**写法问题**被记成了渲染器能力问题——
    > a) `:::tip` / `:::note` / `:::info` / `:::warning` / `:::caution` / `:::important` 六种容器**全部**渲染成
    > 与 `> [TIP]` 完全一致的提示框（`border-left:4px`），`:::tip 自定义标题` 还能覆盖默认标题；真正不支持的
    > 只有 `:::danger` / `:::success`（字面输出 `::: danger`）。旧探针写的是未闭合或未顶格的形态，落进产物的
    > `:::` 是写法问题。b) 行内 `<badge type="tip" title="推荐" />` 渲染出的就是 title「推荐」——旧结论误因是
    > 探针写成了成对标签，看到 `</badge>` 落进正文就推断「title 从未出现」。写成自闭合即可，**不必禁用**。
    > c) `:::steps` 该禁用的结论不变，但机制要说准：它**不再**拆成 38px 圆形，而是整个容器退化成普通段落。
    > 保存自检已同步：六种提示框容器从「不支持的容器」名单移出（原先会把正确写法误报成错误、把模型劝退），
    > 新增成对 `<badge>`/`<icon>` 与 `<steps>` 内 `###` 小标题、图注隔空行三条检查。

    **两个上游「文档与实现不一致」的坑（本轮实证）**：a) guide 第六节第 9 条明写
    「步骤超过 3 个时，系统自动切换为竖向布局（DA02）；也可以主动指定 type="DA02"」——
    实测 4 步与 5 步都**没有**自动切换，仍是 25% / 20% 的横向列，只有显式 `type="DA02"` 才变竖向；
    b) ~~guide 组件第 10 条记载行内徽章 `<badge type="tip" title="推荐" />`，实测渲染出的文字是 type 的值，
    title 从未出现~~——**此项 2026-09-12 已推翻，见上更正**：自闭合写法渲染正常，是当时探针写成了成对标签。
    **结论：语法指令也不是逐字可信的，凡是靠它下判断的地方都要用真实渲染产物核一遍；
    我们自己的技能文案已对这两条给出实测改正。**
    （2026-09-12 再补：guide 也有**漏写**——第一节说 ` ```mermaid ` 代码块「自动渲染为 SVG」，实测渲染器
    只做代码高亮、不产出任何图形。至于 `<timeline>`：F10 已证伪「渲染为空」，真因是每行必须三列。
    F10 还查出 guide 漏得更狠的一处：官网 Web 端组件注册表里的 `:::reading-path`、`:::steps-horizontal`、
    `:::steps-vertical`、`:::case-flow`、`:::slider`、`:::callout`、`:::align`、`:::code-block`
    共 8 个容器，guide 全文一次都没提（`:::breaking`/`:::timeline`/`:::table` guide 有记载，
    是本节之外的补记；2026-09-13 逐名复核见 `known-issues-handoff.md` D24 的复查更正）——
    这是「成品比官网示例素」
    的真正原因，详见 `known-issues-handoff.md` 的 D24。**新补进白名单的容器同样要逐条量行格式**：
    `:::reading-path` 只要有一行不带 `-`（含整块写成 `*`）整块产物就是 0 字符、上游零 warnings；
    `:::slider` 缺 `images` 会把「请提供图片URL列表」的灰框留在成稿里；
    `:::compare` 的行只接受 3–4 列，5 列/2 列会被整行忽略——2 列时末尾那一方的**整列内容直接丢失**。）

    **教训**：十四轮之所以误判，是因为只看到「有编号圆点流出来」就判定有效，没有核对**分段是否正确**——
    线上 guide 从头到尾都没记载过 `:::steps`，guide 是对的，是我们读错了自己的探测结果。
    后续任何「渲染器支持 X」的结论，都必须同时核对组件结构与分段/层级是否符合预期，不能只看有没有样式。
    ② **技能种子文案缺陷（错误结论的扩散源）**：内置 `MarkFlow 精排版式` 技能与
    `markflow-typeset` 技能都在推荐 `:::steps` + `###`，并宣称「超过 3 步自动竖排」——AI 按技能写作，
    缺陷会自动复制到每一篇新文章里。已按实测结论重写 `SkillSeeder.MARKFLOW_CONTENT` 第 2 条
    （四条踩坑结论 + 「与实时语法指令冲突时以指令为准」），并修正
    `EditorServiceTools.render_markflow` 的参数描述；Seeder 在启动时即同步进库。
    （2026-09-12 再改：第 2 条当时的四条里，①「`:::tip`/`:::warning` 不被识别」②「`<badge>` 渲染的是 type 值」
    两条**本身就是错的**，等于把模型从两条好写法上劝退，已按上面 ① 的更正重写——现文案把「实测结论」
    收窄为真正与 guide 不一致的那几条，并补上 `<steps>` 内禁 `###`、图注须紧贴图片、mermaid 不出图三处新实证。）
    ③ **P0 数据缺陷：Markdown 源文被静默丢弃**。`ArticleService.reconcileRenderedLayout` 原先在
    「正文文本变化」时**无条件**清空 `content_markdown`——而「按新 Markdown 重新渲染后覆盖正文」这条路径
    同样会改变正文文本，于是重排一次就把源文丢掉，文章**再也无法二次调整**（只剩 HTML 产物，无法反推 Markdown）。
    修复为「重排提交」判定：正文变化时，若提交的 Markdown **与库中相同**（手动编辑回显正文、
    AI 局部编辑的 `save_article_draft` 传原文，都属于这一类）才清空；**不同**则说明这是一份新产物的源文，
    必须留存。「是否不同」是可判定信号，不需要调用方额外声明。新增回归测试
    `markdownRerenderKeepsNewMarkdownSource`（重排提交后 Markdown 源文留存、`layoutEngine` 仍为 MARKFLOW）。
    ④ **live 数据修复**：文章 3 先用回滚接口退回 revision 1 恢复原始（坏）正文，再用修正后的 Markdown
    经真实令牌重渲染覆盖 → revision 5，`content_markdown` 421 字符已留存。
    CDP 修复前后对比：步骤区 `684×58` → `684×276`；字面 `###` 有 → 无；破损圆形占位 6 个 → 0 个；
    修复后产物为 2 张表格（步骤卡 + 徽章）、9 个 td；截图确认 3 张编号步骤卡 + 胶囊徽章显示正常。
    ⑤ **技能实体同步纠偏**：本机技能 `~/.zcode/skills/markflow-typeset/SKILL.md` 也在推荐 `:::steps` 与
    行内 `<badge>`（第 29、99 行），已按实测改正，并在「限制与注意」补一节「guide 与实现不一致的两处」；
    内置技能 `MarkFlow 精排版式` 的第 2 条同步细化为「`<steps>` 每步一个自然段 / 禁 `:::steps`（退化成普通段落）/
    >3 步建议显式 DA02 / 徽章用 `<badges>`」。
    （2026-09-12 再补：本机技能文件里的「两处不一致」其一也已被推翻——`<badge>` 是好的，只有**成对写法**才坏。）
    ⑥ **本轮遗留（已记录、未在代码层解决）**：a) `ArticleService.rollback()` 是**部分合并**回滚——
    只取目标版本的标题/摘要/正文，作者、来源 URL、引擎与 Markdown 都保留当前值，回滚结果并非目标版本的完整状态；
    b) `article_revision` 表没有 `layout_engine` / `content_markdown` 两列，回滚永远无法恢复 Markdown 源文；
    c) 编辑器 TipTap 往返会剥掉 `data-render-id`，AI「HTML 回灌」防护在手动保存一次后即失效。
    **终态门禁：`./.mvn/mvn-local.sh -o compile` 通过；143 例全绿（`./.mvn/mvn-local.sh test`，BUILD SUCCESS，
    1m22s / 复跑 1m29s 两次一致）；`webui` 本轮未改动。**
  - **2026-09-10 十六轮补充（编辑器往返丢失内联样式：14 轮 P0 的真正根因，前端修复）**：
    十六轮 ③ 只保住了「Markdown 源文」，但正文 HTML 一旦在编辑器里被改动仍会退化——本轮把退化量化到了具体声明级。
    ① **实测（渲染产物 → 编辑器序列化，同一篇 9 个单元格的步骤卡/对比表）**：`td` 上的内联声明**全部丢失**——
     `vertical-align` 9→0、`padding` 9→0、`background` 9→0、`border-bottom` 6→0、`font-size` 6→0、`color` 6→0、
     `border-radius` 5→0、`border` 3→0、`width` 3→0（步骤卡因此从「三列 33% 卡片」变成等宽裸表格）；
     表格自身的 `style="margin:0;border-collapse:separate;border-spacing:12px 0;border:none;min-width:360px"`
     被替换成 TipTap 自己算的 `min-width: 75px`；标记 span 上的 `padding / border-radius:999px / border / display:inline-flex`
     也只剩 `color / background-color / font-size / line-height` ——徽章从「胶囊」退化成一段染色文字。
    ② **根因**：TipTap 的 `tableCell`/`tableHeader`/`table` 节点只声明了 `colspan/rowspan/colwidth/align`，
     `textStyle` 标记只被 `Color/BackgroundColor/FontSize/LineHeight` 四个扩展接管，**其余内联声明
     在 parse→serialize 往返中没有任何属性持有它们**，于是被静默丢弃。MarkFlow 产物恰恰把全部版式放在内联样式里，
     两者正好冲突——这类退化不会报错、单测也测不到，只有「真实渲染产物 + 真实编辑器保存」组合才暴露。
    ③ **修复（`webui/src/editorExtensions.js` 新增两个保留扩展，`ArticleEditorView` 注册）**：
     - `PreservedMarkStyle`：接管 `textStyle` 标记上除 `color/background/background-color/font-size/line-height`
       之外的 style 声明。按 **style 属性原文**逐条切分保留，而不是读 `element.style`——后者会把简写展开成十几条长写、
       丢掉 `!important`，产物与渲染服务给的对不上。
     - `PreservedTableStyle`：整体接管 `table/tableCell/tableHeader` 的原始 style 原文（这些节点本来就没有
       需要保留的受管样式，整体接管最省事且不会与工具栏状态冲突）。
     两个扩展的 `renderHTML` 都走 TipTap 的 `mergeAttributes`，它会按分号合并 style，与四个颜色/字号扩展互不覆盖。
    ④ **修复后实测（同一篇、同一次「插入一段文字触发自动保存」的端到端往返）**：
     `td` 的 `vertical-align / padding / background / border-radius:10px / border / text-align / width:33%` 全部保留
     （td 实测 214px 宽、`border-radius:10px`、背景 `rgba(39,174,96,0.06)`）；表格 `border-spacing:12px 0` 保留；
     徽章 `border-radius:999px` 保留；步骤编号 `font-size:22px` 保留；`###` 字面量仍为 0。
     **落库前 vs 落库后都核过**：编辑器 `getHTML()` 与 `ARTICLE.CONTENT_HTML` 两层都确认样式在场（长度 5479 字符，
     含 colgroup——TipTap 给表格补的，不影响渲染）。
    ⑤ **新增的验收方法（可复用）**：把渲染产物与编辑器序列化产物做一次**内联声明计数 diff**
     （`style="…"` 逐条按属性名计数，对比 db vs editor），任何「打开正常、保存后掉样式」的问题都能被这一步量化定位；
     本轮先据此定位，修复后再跑同一 diff 归零。另外经 CDP 从 Vue 实例树取到 `ArticleEditorView` 的 `setupState.editor`
     直接调 `getHTML()`，可以拿到「尚未落库的序列化结果」，比只看 DOM 更接近真实保存内容。
    **门禁：`cd webui && npm run build` 零报错（仅既有 chunk>500kB 警告）；Java 侧本轮未改动，143 例全绿结论不变。**

  - **2026-09-11 十七轮（PIPELINE SSE 400 根因修复 + 图片风格 Skill 落地 + 两项运行时自愈）**：
    本轮起于「手工触发的 PIPELINE 任务报 `SSE connection failed: HTTP 400 … Assistant tool call arguments must be
    valid JSON`，且工具调用 0 次」的挂账排查，收尾时把 Skills 体系里唯一没有种子的维度（IMAGE）补齐。
    ① **JSON-400 根因（线上报文级复现）**：上游 OpenAI 兼容网关会校验**历史消息**里每条 assistant
    `tool_calls[].function.arguments` 是否为合法 JSON，非法即 400；agent4j 2.3.3 的 `OpenAIChatModel` 把流式
    增量原样累积进 `argsBuffer` 并在下一轮无校验回放，而供应商对**全可选参数**的工具（`read_article_draft` 等）
    会先发一个 `{"arguments":"", …}` 增量——于是首轮结束后历史里留下 `"arguments":""`，第二轮开场即 400。
    用 curl 复刻证明：`arguments:""` → 400、`arguments:"{}"` → 200；并落成离线确定性回归
    `ToolCallArgumentsReplayTest`（JDK HttpServer 模拟网关，第一轮发空 arguments 增量，第二轮按网关规则校验）。
    ② **修复 `ToolCallArgumentGuard`**：装饰 `LLMModel`，把非法/空 arguments 归一为 `{}`。关键点是**两个注入位**——
    入口处归一化回放历史，以及用包装 `ToolExecutor` 归一化**同一次 `ask` 内**追加的 assistant 消息：agent4j 的工具
    循环在 `ask` 内部继续，只包模型层覆盖不到，这一点是回归测试先失败后才定位到的。装饰点放在 `AgentFactory.createModel`
    单一路径（编辑器链路与定时链路、各阶段 agent 都经此）。
    ③ **阶段硬超时（第二个缺陷）**：SSE 被静默掐断时 agent4j 既不回调 onFailure/onClosed 也不完成 Future，
    阶段线程永久阻塞在 `LLMResult.get(LLMResult.java:77)`（线程栈实证），任务永久 RUNNING。`AgentInvoker.awaitStage`
    改为在工作线程执行会话并对 Future 施加 `app.schedule.stage-timeout-seconds`（默认 1800s）硬超时，超时抛明确异常。
    ④ **遗留运行自愈（第三个缺陷）**：进程被杀后 `task_run` 留在 RUNNING，而 `TaskRunMapper.findRunning` 是同一任务的
    并发闸门，任务从此再也触发不了（「任务正在执行，请勿重复启动」）。新增 `StaleRunPolicy`（纯函数、可单测）+
    `StaleRunReaper`（`@Order(40)`，在种子之后、Quartz 恢复之前）+ `createRun` 触发路径自愈：把 `started_at` 超过
    `app.schedule.stale-run-hours`（默认 6h）的 RUNNING 判为孤儿并中止。**刻意不用「启动时清空全部 RUNNING」**——
    Quartz 是 `isClustered=true`，无条件清空会把另一实例正在跑的任务误判为孤儿并打开并发窗口；时限判定保证只动
    「可证明已废弃」的行。
    ⑤ **图片风格 Skill 落地（本轮产品侧增量）**：`IMAGE` 维度早已在白名单、注入顺序与前端筛选里，但**从未有种子**，
    也没有任何协议要求 agent 遵守它——`builtin_illustrator` 的默认技能是空，等于「图片风格」只有壳。本轮补
    `photo_documentary` / `flat_illustration` / `soft_three_d` 三个内置种子（内容统一为「配图审美 + 生图提示词脚手架
    + 画面禁用项（文字/数字/logo/水印）+ 图注习惯 + 数量位置 + 封面标题安全区 + 全篇一致性」），并在
    `AgentProtocols.ILLUSTRATION` 增第 2 条「审美/提示词/图注严格遵循【图片风格】注入区，未绑定时用克制中性写实风格」、
    `EDITOR` 第 14 条把「图片风格」并入须遵循的注入区、`generate_image` 工具描述要求按脚手架组织提示词。
    内置种子数 7 → 10（`SkillApiIntegrationTests` 断言同步更新并新增 IMAGE 维度计数断言）。
    ⑥ **顺带修掉 `/api/skills/preview` 的静默失效（P1，用新技能做预览时才发现）**：该接口把请求的技能 id 放进
    `articleSkillIds`，而 `SkillPromptAssembler` 只在 EDITOR 场景参与该槽位——前端预览按钮发的是 `scene=SCHEDULED`，
    于是**点任何技能看到的都是内置保底版式**（LAYOUT 技能则显示默认版式而非所选版式）。改为放入 `taskSkillIds`
    （两种场景都参与并集）。原集成测试只断言 prompt 含「排版模板」，恰好被保底版式满足——是典型的假阳性用例，
    已连同断言改为「必须含被预览技能自身正文，且不得出现保底版式特征串」。
    ⑦ **真实环境验收（结论与限制）**：本轮用 300s 短超时跑真实 PIPELINE，观测到阶段超时按设计生效——
    任务 300 秒后以 `FAILED / 智能体会话超时（300 秒未结束）` 收尾（日志 `【调研】智能体会话超过 300 秒未结束，判定阶段超时中止`），
    且**立即再次触发成功创建新运行**，证实「崩溃/挂起 → 任务永久卡死」已解除；预览修复也用真实接口验证
    （`skillIds=[9,2]` 返回 `【图片风格】(纪实摄影风) + 极简黑白版式`，不再回落保底）。**但本轮未能取得一次全绿 PIPELINE**：
    本机出网经 TUN 代理，SSE 流在首轮即停滞（`netstat` 有 6 条到 443 的 ESTABLISHED 但无数据、无 outbound 报错、
    agent4j Future 不完成），网关 `GET /v1/models` 无鉴权 0.05s 返回 401，说明 HTTP 面正常、问题在流式链路，
    属环境而非代码；JSON 守卫因此未被线上流量触发（日志守卫命中 0），其正确性由离线报文级回归测试保证。
    同时确认 `agnes-3.0-flash` 已被网关下线（上一轮 `model_not_found`），排查期间 `LLM_PROFILE`（ID 1）的
    `MODEL_NAME` 曾临时改为 `deepseek-v4-flash-0731`，请在设置页确认目标模型。
    ⑧ **本轮遗留（已记录、未在代码层解决）**：a) 孤儿运行只在超过阈值（默认 6h）后才自愈——彻底方案是给
    `task_run` 增加 `instance_id`（取 Quartz `instanceId`）或心跳列，结合 `QRTZ_SCHEDULER_STATE` 的存活实例判定属主，
    可把窗口压到分钟级且仍满足集群安全；单实例部署可直接把 `STALE_RUN_HOURS` 调小。b) 多个 IMAGE 技能同时绑定会注入
    两段冲突风格（与 WRITING 等多注入维度一致，未做单注入；LAYOUT 因涉双引擎才单注入），如需强约束应在绑定处校验。
    **门禁：`./.mvn/mvn-local.sh -o compile` 通过；新增/改动用例全绿——`StaleRunPolicyTest`(6)、
    `ToolCallArgumentGuardTest`(4)、`ToolCallArgumentsReplayTest`(1)、`AgentFactoryTest`(5)、
    `SkillPromptAssemblerTest`(16)、`SkillApiIntegrationTests`(10)。**

  - **2026-09-11 十八轮（回放第二类缺陷 content=null + `/api/agents` 资源不可往返）**：
    十七轮修掉了 `arguments` 一类回放拒绝，但同一「agent4j 原样回放 / 网关逐条校验」根因还有第二处，
    本轮在真实环境复现、定位到字节码级并修掉。
    ① **现象（真机）**：SINGLE 手工运行在完成 4 次 `search_web` 后整轮失败
    `HTTP 400 … The request failed because it is missing \`***.content\` parameter`（code 400001）。
    ② **根因（字节码级，非猜测）**：`javap -p -c OpenAIChatModel` 显示 `appendNeutralMessage` 对
    「role=assistant 且 toolCalls 非空」的消息固定走
    `if (content != null && !content.isEmpty()) put("content", …) else putNull("content")`——
    **null 与空串都被写成 `content:null`**。而 agent4j 回放工具调用轮时用 `Message.fromAssistant()`（content 为 null）构造，
    于是每轮请求体里都是 `{"role":"assistant","content":null,"tool_calls":[…]}`。模型只发工具调用、
    不输出正文的轮次必然命中（定时任务调研阶段几乎全部如此）。
    ③ **真机网关行为实测（用项目自己的 key 直连 `POST /v1/chat/completions` 最小请求，四种取值各一次）**：
    `content:null` → **400 missing content**；`content:""` → 200；`content:" "` → 200；`content:"\u200b"` → 200，
    且单空格那次模型照常给出正常回复（`reasoning` 正常、无异常）。结论：网关只要求「字段存在且非 null」，
    但**空串在 agent4j 侧不可达**（`isEmpty()` 直接落到 `putNull`），因此占位值必须非空 → 取单空格。
    用 mock 网关无法得到这个结论：mock 只复刻已知错误，判不出「哪种取值能过」。
    ④ **修复**：`ToolCallArgumentGuard` 在同一「两处注入点」（进入 `ask` 的历史 + 工具执行前修本轮
    `appendedMessages`）之外补第三类归一化 `normalizeContent`：assistant 且带 toolCalls 时 content 为
    null/空串 → 单空格占位；其余角色 content 为 null → 补空串；`role=tool` 的线上 content 取自
    `toolResult.content`，故修的是 `toolResult`。`ask(Message)` 单参重载也一并归一化（原先漏网）。
    ⑤ **测试**：`ToolCallArgumentsReplayTest` 的 mock 网关升级为**同时**执行两条网关规则
    （arguments 必须合法 JSON + 每条消息必须有非 null content），第一轮发空 arguments 增量、
    第二轮校验；`ToolCallArgumentGuardTest` 扩到 11 例（含「空串也归一化为空格」「普通消息补空串」
    「toolResult 补空串」「轮内 assistant 消息在工具执行前修好」）。
    ⑥ **真机验收**：运行 #26 在修复后连续跑过 **5 个工具调用轮**
    （日志 `调用工具：create_plan | search_web ×2 | browse_webpage ×2`，`ToolCallArgumentGuard` WARN
    占位 5 次），**全程无 400**——同一路径在修复前的 #23 是第 2 轮即 400。随后撞到网关
    `HTTP 429 concurrent limit exceeded`（并发限流，与消息形状无关，属环境侧）。
    另外顺带实证：`EXECUTION_LOG` 在失败运行上留下了 177 字符的阶段日志（十七轮的局部日志落库修复有效）。
    ⑦ **`/api/agents` 资源不可往返（本轮一并修）**：GET 返回的 `toolKeys` 是 JSON 数组**字符串**、
    `skillIds` 是**逗号分隔字符串**，而 PUT（`AgentRequest`）要求两者都是数组——同一资源读出来写不回去。
    新增 `AgentDefinitionService.AgentView` 读视图（用 `parseToolKeys` / `WechatAccountService.parseSkillIds`
    还原成数组），controller 六个读端点统一返回；前端 `parseToolKeys`/`parseSkillIds` 本就兼容数组，无需改动。
    真机 `/api/agents/1` GET→PUT 回填同一份 body 返回 200 且 `code/stage` 未被改动。
    ⑨ **`/api/tasks` 同一缺陷（本轮一并修，修法是真机逼出来的）**：清理验证现场时要把任务 2 改回
    `PIPELINE/WECHAT_DRAFT`，于是 GET→PUT 原样回填，结果 PUT 直接 500：
    `JSON parse error: Cannot deserialize value of type java.util.ArrayList<java.lang.Long> from String value`
    ——任务表的 `skill_ids`（逗号串）与 `stage_agents`（JSON 对象串）同样只在写接口是结构化的。
    新增 `ScheduleTaskService.TaskView`（复用已有的 `parseSkillIds` / `parseStageAgents`），
    controller 的 list/get/create/update 统一返回视图；测试升级为真的往返断言
    （GET 的 `data` 原样 PUT 回去必须 200）。
    **说明**：`/api/articles` 存在同一形态（`ARTICLE.SKILL_IDS` 逗号串 + `ArticleRequest.skillIds` 为列表），
    但 `Article` 实体有 27 个字段，改视图的爆炸半径远大于任务/智能体，且现有用例显式接受字符串形态
    （“前端 parseSkillIds 两种都能解析”），本轮不动，列入遗留。
    ⑧ **本轮遗留（已记录、未在代码层解决）**：a) `TASK_RUN.TOOL_CALL_COUNT` 只在成功路径写入，
    失败运行永远是 0——运行历史里「调了几次工具」在失败时不可读（`EXECUTION_LOG` 已能给出明细，属轻缺陷）。
    b) reviewer 的 P2 债务仍在：P2-2（轮内修复依赖 agent4j `ask` 的懒执行顺序，仅经验证明，未加版本锚定的断言）、
    P2-4（`taskLocks` 是 JVM 本地锁，多实例下并发门禁可被击穿，且 reaper 不走该锁）、
    P2-5（`abortStale` 需要整实体，宜改定点 UPDATE）、P2-7（嵌套 COORDINATOR 的子 agent 预算 vs 整轮预算）。
    c) 模型可用性：排查期 `agnes-3.0-flash` 曾从网关下架（#17 `model_not_found`），期间改用
    `deepseek-v4-flash-0731`，用户随后换回并经直连探测确认 `agnes-3.0-flash` 恢复 200。
    另注：#24 的「未提交草稿」（模型整轮不发工具调用）在修复前的 #18 已出现过同类失败，属模型侧间歇行为，
    非本轮改动引入。d) `/api/articles` 的 `skillIds` 仍是「读字符串 / 写数组」，未做视图（理由见 ⑨）。
    **门禁：`./.mvn/mvn-local.sh -o test` 全量 173 例 0 失败（十七轮为 166）。**

  - **2026-09-11 十九轮（清账：把 ⑧b 的 P2 债务与 ⑧/⑨ 的遗留缺陷一次修完）**：
    本轮不新增功能，只清十八轮及更早记录的债务；每条都补了对应测试，测量类结论用真实令牌/真实浏览器取得。
    ① **P2-5 `abortStale` 改定点 CAS UPDATE（十八轮 ⑧b）**：原实现要求调用方先加载完整行再整实体回写，
    传入部分实体就会把其余列写成 null。改为
    `UPDATE TASK_RUN SET STATUS=?,MESSAGE=?,FINISHED_AT=NOW(6) WHERE ID=? AND STATUS='RUNNING'`：
    只写三列，且 `AND STATUS='RUNNING'` 让这次中止成为**比较并交换**——扫描到判定为孤儿之间该运行若已被属主
    正常收尾，返回 0 即不覆盖终态。`FINISHED_AT` 交给数据库 `NOW(6)` 生成，与 smart-mybatis 写本列（varchar）
    的 `yyyy-MM-dd HH:mm:ss.SSSSSS` 格式一致，避免 Java 侧格式化与驱动转换格式不同。
    同时修掉一个观测缺陷：返回 0 时不再打「判定为孤儿」WARN，否则一次正常完成会被误报成孤儿中止。
    ② **P2-4 多实例并发门禁（十八轮 ⑧b）**：`taskLocks` 只是 JVM 本地锁，Quartz 以 `isClustered=true`
    部署时另一实例可能同时通过检查。修法不需要任何跨实例锁：插入运行后按「RUNNING 中 ID 最小者为唯一属主」
    复核（`findEarliestRunning`），落败者撤回自己刚插入的行并抛与「未插入就抛错」相同的 `BusinessException`，
    历史里不留下被拒绝的空运行。ID 单调递增保证两个并发插入里恰好有一个赢家。**未消除**的是
    `taskLocks` 本身仍是本地锁（reaper 也不走该锁），但并发门禁已不依赖它。
    ③ **`TOOL_CALL_COUNT` 失败路径恒为 0（十八轮 ⑧a）+ COORDINATOR 子 agent 计数缺失（十八轮 ⑧b 的 P2-7 前置）**：
    计数器从 `AgentSessionResult` 移到 `TaskWorkspace`（成功/失败共用一份），`AgentInvoker` 在每次
    `ToolStatus.CALLING` 时经 `IntConsumer` **实时**上报（`countedCalls` 去重），因此会话随后因超限/断流/超时
    抛异常，调用方仍拿得到已完成的部分计数。`AgentRunner.runWithLimit` 增 6 参重载作为注入缝，
    SINGLE / PIPELINE / COORDINATOR（chief 与每个子 agent）四处均汇入。成功路径改取
    `workspace.toolCallCount()`，失败路径也写。
    ④ **`/api/articles` 资源可往返（十八轮 ⑨d 遗留）**：`ARTICLE.SKILL_IDS` 是逗号串而 `ArticleRequest.skillIds`
    是 `List<Long>`——GET 结果原样 PUT 回去必然 400。十八轮以「Article 有 27 个字段、改视图爆炸半径大」为由搁置，
    本轮改用**访问器级序列化器**（不动实体结构、不与视图长期漂移）。踩到两个坑并记录在 `Article.getSkillIds()` 注释里：
    a) 注解必须落在 getter 上（字段私有，Jackson 序列化主成员是访问器，字段上的注解不生效）；
    b) 必须用 **Jackson 3**（`tools.jackson`）的注解与基类——**Spring Boot 4 的 HTTP 层用 Jackson 3
    (`tools.jackson.core:jackson-databind:3.1.4`)，而本项目内部 JSON 走 Jackson 2**。两个 databind 包并存：
    `com.fasterxml.jackson.annotation` 是共用的，但 `...jackson.databind.annotation` 不是。
    第一版用 Jackson 2 的 `@JsonSerialize` **在字节码里能看到注解、响应却仍是字符串**（`javap -v` 已证注解在
    方法上），直到 `mvn dependency:tree | grep -i jackson` 看到两个 databind 版本才定位。
    真机验收：GET `skillIds` 是数组、原样 PUT 回 200。
    ⑤ **`article_revision` 缺列导致「部分合并回滚」（缺陷 G1+G2）**：快照只存 title/digest/contentHtml，
    回滚也只取这三项——作者、来源 URL 保留**当前值**（回滚换来半篇旧版），且 `article_revision` 根本没有
    `layout_engine` / `content_markdown` 两列，**MARKFLOW 文章回滚后永远拿不回可重排的 Markdown 源文**。
    补 `author`/`sourceUrl`/`layoutEngine`/`contentMarkdown` 四列，`snapshot()` 写全，`rollback()` 恢复完整版本。
    边界：封面（coverAssetId/coverUrl）与技能绑定**不随版本回滚**（属当前编辑决策与共享素材，不是某次正文修订的
    一部分）；账号与并发版本号取自当前行。升级前落库的旧版本四列为 null，用 `restoreOr` **保留当前值而不是抹成 null**
    ——「恢复不了」不应表现为「丢失数据」。**部署安全性已实证**：带新实体重启后 smart-mybatis 的
    `DefaultSmartMapperInitializer.syncDatabaseStructure` 在既有 dev `ARTICLE_REVISION` 上 ADD 出四列
    （`information_schema` 计数 = 4），无需手工 DDL。真机验收 10/10：MARKFLOW v1 → 改全部字段存 v2 → 回滚 v1，
    Markdown 源文、渲染产物、作者、引擎全部回到 v1。
    ⑥ **编辑器往返剥掉 `data-render-id`，AI「HTML 回灌」防护在手动保存一次后即失效（缺陷 G3，十四轮 ⑥）**：
    `data-render-id` 由 `ArticleAiService.markRenderId` 注入渲染产物最外层，`read_article`/`read_blocks` 靠它
    区分「渲染区段」与普通正文；`ArticleService.clean()` 的 safelist 已放行该属性，但**编辑器侧没有任何节点声明它**，
    于是 TipTap parse→serialize 一次就丢掉——不报错，只有「渲染 + 手动保存 + AI 读回」的组合才暴露。
    新增 `PreservedRenderId` 扩展，对**所有可能承载它的节点**统一声明（渲染根元素可能是
    section/div/p/heading/table 等任意一种，`markRenderId` 只保证有一个最外层）。
    **真机量化验收（正例 + 反例）**：建一篇 `contentHtml` 含 `<section data-render-id="r1">` 的临时文章，
    经 Vite dev server（5173 → 代理 8081）在真实浏览器打开编辑器，从 Vue 实例树取 `ArticleEditorView.setupState.editor`
    调 `getHTML()`：**正例**含该属性；点真实「保存」按钮走完 `editor.getHTML() → PUT` 后库内仍含（位置 10）；
    **反例**用 `git stash` 临时回退 `editorExtensions.js`+`ArticleEditorView.vue` 两个文件、经 HMR 重载后，
    **同一份库内 HTML** 往返回来变成裸 `<section>`——证明该项测量确实能发现此缺陷，而不是「恰好都是绿的」。
    反例后 `git stash pop` 复原并复测（属性回来）。
    ⑦ **P2-2 版本锚定 canary（十八轮 ⑧b）**：轮内修复依赖 agent4j `ask` 的懒执行顺序，此前只有经验证据。
    `ToolCallArgumentsReplayTest` 增 `validateArguments` 开关与一个**没有守卫服务**的用例：
    mock 网关只校验 arguments、不剔除 content，发送**未包裹**的模型，断言第一轮报文里确实是
    `"content":null` 且**仍被网关拒**（`rejectedAsMissingContent == 1`）。agent4j 若修好上游行为，
    该断言会失败并提醒撤销守卫——从「经验」变成「可证伪」。
    ⑧ **P2-7 COORDINATOR 整轮工具预算（十八轮 ⑧b）**：此前只有逐项额度——chief 48 次 + 每次委托子 agent 24 次
    + 最多 8 次委托，叠加上界约 240 次，比 SINGLE 的 `MAX_SCHEDULED_TOOL_CALLS=40` 高一个量级，
    无人值守的定时运行可能一次烧掉远超预期的额度，而「委托次数上限」拦不住「每次委托都跑满 24 次」。
    增 `MAX_TOTAL_TOOL_CALLS = 120`（≈ SINGLE 的 3 倍：够「调研+写作+配图+审核各跑满一轮」再加两次返工），
    在 `Budget.consume()` **前置**检查，命中时与「委托次数上限」同样**返回引导文本让 chief 收尾**而不是中断会话；
    `runSubAgent` 回填 `outcome.toolCalls()` 使子 agent 消耗计入总量。chief 自身额度仍由 `MAX_CHIEF_TOOL_CALLS` 单独约束。
    ⑨ **同维度多枚 IMAGE 技能冲突（十八轮 ⑧b 的 b）**：**有意不改行为**，维持并列注入并新增 WARN 点名冲突技能。
    理由：LAYOUT 之所以单注入是因为它直接决定用哪个排版引擎（双引擎指令必然打架）；而风格类冲突若「首个生效、
    其余丢弃」，用户会**看不见地**丢掉一半绑定，比冲突本身更糟。真要加强约束应落在绑定处校验，
    属产品决策，不单方面收紧（`SkillPromptAssemblerTest.sameDimensionSkillsOtherThanLayoutAreAllInjected` 固化现状）。
    ⑩ **`delegate_research` 限流（十四轮遗留）核实为已覆盖，无需改动**：会话级 `MAX_EDITOR_DELEGATE_CALLS=3`
    外加端点级每用户 20/分限流，两处叠加已足够。
    **门禁：`./.mvn/mvn-local.sh -o test` 全量 178 例 0 失败（十八轮 173）；
    `webui npm run build` 零报错（仅既有 >500 kB chunk 体积警告）；真机验收三轮全过——
    文章 GET→PUT 往返 + 版本回滚 13/13、MARKFLOW 回滚完整性 10/10、G3 正反例各一次（含 `git stash` 反例）。**
    **本轮遗留（仍未在代码层解决）**：a) `taskLocks` 仍是 JVM 本地锁且 reaper 不走该锁（并发门禁已不依赖它）；
    b) 孤儿运行的**实例感知**诊断仍需给 `task_run` 加 `instance_id`/心跳列，配合 `QRTZ_SCHEDULER_STATE` 判属主，
    才能把自愈窗口从 6h 压到分钟级；c) COORDINATOR 的返回值不含子 agent 工具计数（已由工作区计数器缓解）。
    环境侧：本机 SSE 流式链路仍受 TUN 代理影响（十八轮已记录），故 ⑧ 的整轮预算只有离线报文级回归，
    未取得一次全绿 COORDINATOR 实跑。清理：验收用临时文章与 `target/live_*.py` 已删除；本轮**未做任何提交**。
