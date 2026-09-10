# 《墨舟 Skills + 多 Agent 架构开发方案》审查报告

> 审查日期：2026-09-08
> 审查对象：`docs/dev/skills-agent-plan.md`（843 行，2026-09-08 定稿，含三次增补）
> 核对基准：main 分支 commit `78f560f` + 本机技能 `~/.zcode/skills/markflow-typeset/`
> 审查方式：双线并行——reviewer 子代理对照代码库与技能文档做逐项事实核对（只读）；主 agent 做需求对齐与内部一致性审查

---

## 0. 总体结论

| 评估项 | 结论 |
| ------ | ---- |
| 需求理解 | **无偏差**，三个原始诉求全部忠实覆盖 |
| 现状分析（方案第 2 节） | 质量非常高，数十处类名/方法/行号/表名/配置项引用几乎全部精确 |
| 致命错误 | **0 个 Critical** |
| 开工前必须修正 | **5 处**（1 处事实错误 + 4 处设计缺口/内部矛盾） |
| 其他事实性错误（Minor） | 6 处 |
| MarkFlow 落地补充点 | 4 处（技能文档中存在、方案未覆盖） |
| 设计细节小问题 | 11 处（可实施时顺手修正） |
| 总体判定 | **方案不需要推翻，局部修订后即可开工** |

---

## 1. 需求对齐确认

用户原始需求三条，逐条核对：

1. **「可以创建多个 skill，控制文章风格、图片风格、文章排版等等，可以扩充维度」**
   → 方案 3.3 的 12 维度白名单完整覆盖：WRITING（文章风格）、IMAGE（图片风格）、LAYOUT（排版），并扩充了 AUDIENCE/TOPIC/LANGUAGE/TITLE/OPENING/ENDING/DIGEST/FACT_CHECK/OTHER。✓

2. **「不同 agent 负责不同的工作，主 agent 负责协调其他 agent 完成文章创作」**
   → COORDINATOR 模式（主编 chief + delegate_research/writing/illustration/review 四个委托工具）正是该形态；额外补充 PIPELINE（代码编排、无协调层 LLM 开销）与 SINGLE（存量兼容），已在 1.2 标记为确认决策。✓

3. **「排版接入本机技能 markflow-typeset」**
   → 5.10 双引擎设计（PROMPT/MARKFLOW）完整，含 render_config 产品化存储、语法 guide 实时缓存、工具边界渲染、编辑器占位替换。**「只集成渲染、不集成发布接口」的边界决策正确**——经核实，`__markflow_wechat_publish` 确实要求把公众号 AppSecret 提交给第三方服务（`references/publish-api.md:15-17`），与项目 README「数据和密钥掌握在自己手里」的信任边界冲突，且项目自有微信交付链路已完整覆盖其能力。✓

**一个解释性说明（非偏差，但需知悉）**：「你可以扩充维度」被解释为**设计期改代码扩充白名单**（dimension 为白名单校验字段），运行时用户只能用 OTHER 兜底，不能在界面上自定义新维度。此解释合理；若期望运营级维度扩充，需另行提出需求。

---

## 2. 开工前必须修正的 5 个问题

### 2.1 【事实错误 · Major】图片 publicUrl 不满足 MarkFlow 的 http(s) 直链要求

- **方案位置**：5.10.3（第 598 行）「现有素材 publicUrl（`/uploads/...`）天然满足，无需改造」；第①期验收标准（第 691 行）
- **实际情况**：publicUrl 是**相对路径**——`AssetService.java:118`：`asset.setPublicUrl("/uploads/" + storageName)`；而 MarkFlow 技能文档（`SKILL.md:100`）明确要求「图片一律用 http(s) 直链」。远程渲染服务（bx9y.com.cn）收到含相对路径图片的 Markdown 的行为**未在任何文档中定义**。且全项目**没有「站点公网 Base URL」配置项**，想拼绝对地址都缺原料——这是方案未覆盖的新增配置需求。
- **缓解因素**：微信交付不受影响（`ArticleService.prepareWechatContent()`，`ArticleService.java:260-281` 会把 `/uploads/` 本地图重传微信并替换 src）；同源 webui 编辑器里相对路径也能显示。风险集中在渲染环节。
- **修正建议**：第①期开工前用真实 token 做半天 Spike，验证渲染 API 对含 `/uploads/xxx.png` 图片的 Markdown 的行为；若不透传，方案需补「站点 Base URL 配置 + 渲染前 URL 绝对化」子项。

### 2.2 【设计缺口 · Major】「禁止双引擎混用」没有落实机制

- **方案位置**：5.2 解析算法（规则 1「全部按 id 并集去重」、规则 3「按 dimension 分组组装」）vs 5.10.2（「生效引擎取排序最前的 LAYOUT Skill」「禁止双引擎混用」）
- **矛盾场景**：任务同时绑定 `markflow_default`（MARKFLOW）和 `default_layout`（PROMPT）时，按 5.2 规则两个 Skill 的 content **都会注入**「排版模板」区（并集注入，优先级只决定排序），引擎却判定为 MARKFLOW——系统提示里同时出现「产出内联样式 HTML」和「产出 MarkFlow Markdown」两套互斥指令，模型行为不可预测。
- **修正建议**：明确规则「LAYOUT 维度仅注入优先级最高的一个 Skill，其余 LAYOUT Skill 丢弃」（或在绑定保存时提示冲突），并在 8.1 的 `SkillPromptAssemblerTest` 补对应用例。

### 2.3 【设计缺口 · Major】第①-③期编辑器存在 MARKFLOW 真空窗口

- **方案位置**：第①期范围第 7 条（ArticleEditorView 的 Skill 绑定 UI 第①期交付）vs 5.10.5（编辑器渲染能力第④期才有）
- **问题**：第①期起 EDITOR 场景就能绑定 MARKFLOW 排版技能，但 `render_markflow` 工具与占位替换在第④期。窗口期内用户绑定后发起对话：系统提示注入 MarkFlow 语法 guide 并指示 Agent 产出 Markdown，而编辑器的 `insert_blocks` 期望 HTML、`ArticleContentPolicy` 处于 PROMPT 强校验模式——产物必然被拒或错乱。5.10.2 的前置校验只覆盖「渲染服务不可用」，不覆盖「链路不支持」。
- **修正建议**：第④期之前，EDITOR 场景组装时过滤 MARKFLOW 技能并在 UI 置灰/警示「渲染式排版暂仅支持定时创作」；写入第①期范围。

### 2.4 【内部矛盾 · Major】附录 B 引用了不存在的种子 Skill

- **方案位置**：附录 B——`builtin_researcher` 与 `builtin_reviewer` 的默认 Skill 均写「事实核查类 Skill」；附录 A 的 6 个种子（3 LAYOUT + 1 MARKFLOW + 2 WRITING）中**没有任何 FACT_CHECK 维度技能**。
- **后果**：第②期 AgentSeeder 实施时引用落空（失败或静默为空，行为未定义）。
- **修正建议**：附录 A 补一个 FACT_CHECK 种子（如 `fact_check_default`：多源交叉验证、数据须注明出处、时效性标注），或将附录 B 的引用改为「—」。

### 2.5 【机制缺失 · Minor 偏 Major】chief 的「DRAFT（只读 draft）」无法实现

- **方案位置**：附录 B `builtin_chief` 的 tool_keys「DELEGATE + DRAFT（只读 draft）」；5.3 ToolRegistry 按**组**粒度校验，而 DRAFT 组含写工具 `save_article_draft`。
- **问题**：现设计下 chief 要么拿到完整 DRAFT 组（能自己写草稿，与「只委托不动笔」的协调者语义冲突，且绕过返工预算护栏的语义），要么拿不到 `read_article_draft`（无法检查子 Agent 产物）。
- **修正建议**：拆分工具组（`DRAFT_READ` / `DRAFT_WRITE`）或在 ToolRegistry 支持工具级子集勾选；同步更新 5.3 表格与附录 B。

---

## 3. 其他事实性错误（Minor，实施时修正文档即可）

| # | 方案位置 | 方案陈述 | 实际情况（证据） |
| - | -------- | -------- | ---------------- |
| 1 | 6.7（:667） | 「App.vue 侧边栏『智能体』分组下新增两项」 | 侧边栏是**扁平 items 数组**（9 项，`App.vue:16-26`），单层 v-for 渲染，无分组结构；「分组」需先改造导航渲染 |
| 2 | 5.10.3（:591） | 渲染响应契约 `{ok, html, meta, theme}` | 实际还含 **`preview` 字段**（自包含预览页 HTML，体积可能远大于 html，`SKILL.md:41-44`）——服务端反序列化应忽略，8.1 测试 mock 按真实 payload 写 |
| 3 | 5.10.3（:595）、风险 15 | 「上游对未知语法为降级渲染而非硬失败」 | **外推**：文档只说 mermaid 降级为代码块（`SKILL.md:99`），并警告「guide 里没有的语法不要发明」；未知容器/标签的实际行为无文档依据，应并入 Spike 验证 |
| 4 | 5.5（:492） | 「子智能体单次工具调用 ≤ 24（沿用 MAX_TOOL_CALLS）」 | `MAX_TOOL_CALLS=24` 目前**只在编辑器浏览器工具路径**强制（`ArticleAiService.java:572-574`）；定时链路现无任何上限，「沿用」实为新增强制点 |
| 5 | 4.2、5.3 | temperature/maxTokens 覆盖「→ LLMModel.create(...)」 | 现有 `createModel()`（`ArticleAiService.java:442-450`）仅 4 参调用，llm_config 的 temperature/maxTokens **现在就没传给模型**；agent4j 2.3.3 是否有对应重载未验证——第②期开工前先翻 agent4j API |
| 6 | 5.10.3（:597）、8.1 | 错误映射只列 401/400/500/超时 | 技能文档明确 **413 = markdown 超 2MB**（`SKILL.md:68`）；风险 14 提了 2MB 但没落到状态码映射与测试用例 |

---

## 4. MarkFlow 落地补充点（技能文档中存在、方案未覆盖）

1. **部署容器出网要求**：渲染服务在公网（www.bx9y.com.cn），Docker 生产部署需保证 app 容器可出网；Java `HttpClient` 默认**不走系统代理环境变量**，若生产环境需经 HTTP 代理出网，`MarkFlowRenderService` 要显式配置 ProxySelector——风险 11 只写了「不可用/超时」，未提出网/代理前提。
2. **令牌注入路径**：技能原生支持环境变量 `MARKFLOW_RENDER_TOKEN`（优先于本地文件，`SKILL.md:15,18`）。产品化后建议 render_config 同时支持环境变量注入（与 `APP_SECRET_KEY` 等惯例一致，Docker 部署下比设置页手工录入更顺），方案未提。
3. **`HTTP 200 + ok:false` 防御分支**：同一服务的发布接口契约是「HTTP 恒 200，成败看 body.ok」（`publish-api.md:26`）；渲染接口虽文档给了真实错误码，两种约定在同一服务并存，客户端除状态码外还应处理 200+ok:false 组合——5.10.3 只写了状态码语义映射。
4. **dark 自动派生与验收判据**：只传 accent 时上游自动派生 dark（加深 25%，`SKILL.md:66`）。AUTO 模式下实际生效主题以渲染响应 `theme` 字段为准，第①期验收标准「主题色与配置一致」应写明以响应 theme 为判据。

---

## 5. 设计细节小问题（11 项，实施时顺手处理）

| # | 问题 | 位置 |
| - | ---- | ---- |
| 1 | 注入顺序自相矛盾：「按上表从上到下（排版模板最后……）」，但维度表中 LAYOUT 之后还有 FACT_CHECK、OTHER | 3.3（:208） |
| 2 | 总人日算术错误：四期合计 7-9 + 6-8 + 7-10 + 5-7 = **25-34**，方案写 24-34 | :753 |
| 3 | 第①期 7-9 人日偏乐观：含 Skill 全套 CRUD + 组装器 + 两条链路 prompt 替换 + MarkFlow 引擎（表/服务/工具适配/策略改造）+ 4 个前端页面 + 测试；叠加本报告 2.1/2.2/2.3 修正后现实约 **10-14 人日** | §7 |
| 4 | 60000 字符上限未说明是否包含 MarkFlow 实时语法 guide（guide 可能数千字符），超限校验口径需定义 | 5.2 规则 6 |
| 5 | 内置 Skill 内容会被启动 seed 覆盖（已定案设计），但用户经 PUT 修改内置 Skill 后会在下次部署**静默丢失**——UI 需警示「内置技能修改将被版本更新覆盖」，方案未提 | 5.9 |
| 6 | `article_agent_session` 将 description 固化在序列化会话中；「文章 Skill 切换后下一条对话系统提示即变化」（第④期验收）需要说明会话重组/重建机制，否则验收会卡住 | 6.5、§7 |
| 7 | 「LLM 上下文零 HTML」在编辑器链路只对单次工具调用成立：渲染 HTML 入文档后，后续 read_article/read_blocks 会把大段 HTML 带回上下文，Agent 可能抄改破坏版式——正是该设计想避免的，未给防护机制（如 read_blocks 对渲染区段返回占位标记） | 5.10.4 |
| 8 | 第④期 `{{render:<renderId>}}` 占位替换与 `ArticleContentPolicy` 校验的先后顺序未定义：替换前校验看到占位符，替换后校验看到含列表/表格的渲染 HTML（需引擎感知放行）——校验管道需明确 | 5.10.4 |
| 9 | 每次 `save_article_draft` 都触发远程渲染：返工环中写作 agent 多次保存 = 多次外呼 + 30s 超时风险 + 浪费；可考虑延迟渲染（仅最终交付时渲染）；渲染抛异常时 workspace 双份数据的状态一致性未写 | 5.10.4 |
| 10 | MARKFLOW 模式下 digest（公众号摘要）来源未说明——渲染响应含 `meta.summary` 可自动填充，是个顺手的增强点 | 5.10.4 |
| 11 | 新增的 skill/agent/llm_profile/render_config CRUD 是否纳入现有操作审计（Audit 视图）未提 | §5.6 |

---

## 6. 核实无误的关键点（抽查确认，增强开工信心）

- **2.2 两条链路机制**：SSE 前端工具执行 + 回传端点、90s 超时、会话序列化跨请求记忆、commitSession + 版本快照、Quartz DisallowConcurrentExecution、手动 run 异步——全部与代码一致，**方法级行号全部精确**（chat():154 / executeWithAgentSession():212 / createArticleAgent():298 / runScheduledAgent():369）。
- **2.3 Prompt 现状**：三常量行号区间、条数（15 条/7 条）、内容要点（#07C160、16px/1.9、figure、禁列表表格）逐项吻合；「不要创建计划或子智能体」确在 :104、:119。
- **工具清单**：编辑 7 + 媒体 7 + 草稿 3，与 `@ToolInfo` 注解逐一吻合（注：`ArticleMediaTools.create()` 是实例方法非静态，不影响落地）。
- **4.5 加列核对**：四张目标表实体均存在，新列与现有字段**无重名、无语义重复**；四张新表无同名冲突。
- **2.4/2.5**：default_style 存在且未接线（全库 grep 仅 CRUD 与表单引用）、llm_config 单行热读取、AES 加密掩码、smart-mybatis 自动 DDL 无 Flyway、11 个视图清单——全部准确。
- **兼容性设计的事实前提成立**：`LlmConfigService.runtime()` 签名、provider 白名单三种、`normalizeBaseUrl`、`readableLlmError`、`CryptoService`（密钥=APP_SECRET_KEY）、Seeder 机制先例（`BootstrapAdminRunner` + @Order）。
- **markflow-typeset 技能实体**：仅 SKILL.md + references/publish-api.md 两个文件，**纯 prompt 规则 + HTTP API，无 Node 脚本/二进制/字体/本地运行时依赖**——方案将其理解为纯 HTTP 产品化（Java 侧只需 HTTP 客户端）是正确的；附录 A「13 组主题对照表」与 SKILL.md 表格行数一致，engine_config 示例默认色与 API 缺省一致。
- **测试基建**：CoreApiIntegrationTests 真 MySQL 模式、mvnw、npm run build 均属实；核对基准 commit 78f560f 与方案第 56 行声称一致。

## 7. 值得肯定的部分

1. 现状分析基于真实代码探索，行号级引用全部命中——方案的事实基础扎实。
2. 存量兼容设计严谨：新列全可空/默认 SINGLE、`runtime()` 签名不变、旧 API 映射默认档案、每期回归存量链路。
3. 安全边界决策正确：发布接口不集成（AppSecret 不出域）、渲染产物服务端危险元素剥离、token AES 加密 + 掩码返回、三级语法缓存失效策略。
4. 工程成熟度高：agent4j 嵌套会话 Spike 前置 + 退化方案（对外接口不变）、风险表 15 项均有对策、四期独立验收 + 门禁。

---

## 8. 建议的下一步

1. **回写修订**：将第 2 节 5 处必须修正项（及第 3-5 节相应条目）更新进 `skills-agent-plan.md`——均为局部修订，不动总体架构。
2. **第①期前置 Spike（半天，建议写入交付计划第 0 项）**：用真实渲染 token 验证两件事——① 含 `/uploads/` 相对路径图片的 Markdown 渲染行为；② 未知语法/组件是降级渲染还是硬失败。Spike 结论决定是否需要「站点 Base URL 配置 + URL 绝对化」子项。
3. **第②期前置确认**：翻 agent4j 2.3.3 API，确认 `LLMModel` 是否支持 temperature/maxTokens（关系到模型档案覆盖机制与 stages_summary 的 tokens 统计能否成立）。
4. **工期重估**：第①期按 10-14 人日重估，总工期相应调整为约 28-38 人日。

> 审查记录已按 Handoff 协议写入 `.zcode/handoff/latest.json`。本报告为只读审查产物，方案文档本身未做任何修改。
