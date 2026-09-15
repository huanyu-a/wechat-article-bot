# 定时任务失败记录全量归因与解决方案

- 状态：**方案已确认并实施完毕**（2026-09-15 出具方案，用户确认后动工；代码侧全部落地，确定性闸门通过 329 例全绿）
- 数据源：开发库 `momo-mysql-dev` / `wechat-article` 的 `TASK_RUN` 全表（`TASK_RUN` 共 108 行）
- 结论一句话：**64 条失败里只有 3 条来自真实定时触发，而其中 2 条是同一个「SINGLE 模式单会话超时 + 摘要校验死循环」；真正需要在当前代码上修的只有 4 类（A/C/G/H），其余是上游抖动、配置期噪声或按设计工作的自愈。**

## 实施落地对照（2026-09-15）

| 项 | 落地形态 | 验证 |
|---|---|---|
| ①-1 SINGLE 超时 | `app.schedule.single-timeout-seconds`（默认 900）+ `ArticleAiService:568` 改走 7 参重载 | 配置项生效路径由既有 `AgentRunner` 单测覆盖 |
| ①-2 标题/摘要截断 | `ScheduledArticleTools.TITLE_MAX_LENGTH/DIGEST_MAX_LENGTH` + 新增 `saveWarnings` 字段 | `ScheduledArticleToolsDraftTests` 29 例（含渲染器回填摘要的二次截断） |
| ①-3 收尾宽限只计失败 | `ToolCallBudget.allowsTerminalPastBudget(attempts, failures)` | `ToolCallBudgetTest` 8 例（复刻 run#85 序列） |
| ②-1/②-2 重试扩围 | `AgentInvoker.isPermanent/isTransient`，次数 3→5、退避 `{1,2,4,8,16}s`，全部可配置 | `AgentInvokerTest` 23 例（含 402 额度耗尽不重试） |
| ②-3 长文本列扩容 | `ArticleLongTextColumnRunner`（幂等，覆盖 5 列 / 2 表：`ARTICLE` 与 `ARTICLE_REVISION` 的正文列）+ 4 个实体字段改 `columnType` | `ArticleLongContentPersistenceTests` 3 例（**含把列改回 TEXT 再跑迁移的反证**、**含正文同时落两表的断言**）；实机 5 列均 `mediumtext` 且 75,000 字节插入反证通过 |
| ③ 可观测性 | 本文件新增只读巡检 SQL；NPE 现场日志带广告工具名 | — |

> 确定性闸门：`.mvn/mvn-local.sh -o test` → **329 例，0 失败**；`webui` 共享导入自检通过。
> **待用户单独确认的动作**：②-3 的 DDL（`ArticleLongTextColumnRunner` 在启动时自动执行，等于「重启即生效」）、服务重启、线上部署。

---

## 一、失败全貌（先分清「真调度」与「测试噪声」）

`TASK_RUN` 共 108 行：FAILED **64**、SUCCESS 12、SUCCESS_WITH_WARNINGS 30、RUNNING 1。

按触发来源拆分，这是本次归因最重要的一刀：

| 触发方式 | 运行数 | 失败数 | 说明 |
|---------|-------|-------|------|
| **SCHEDULED**（真实定时触发） | 11 | **3** | 09-12 起每天 09:00 由 Quartz 触发 |
| MANUAL（手工「立即执行」） | 96 | 61 | 开发期自测，集中在 09-10 ~ 09-11 |

真实定时运行的成功率：**11 次里 8 次成功（4 SUCCESS + 4 SUCCESS_WITH_WARNINGS），3 次失败 = 73%**。

按任务拆分，失败高度集中：

| 任务 | 模式 | 定时运行 | 结果 |
|------|------|---------|------|
| #2 每日科技早报 | PIPELINE | 64 / 88 | 全部成功（88 带警告） |
| **#4 单智能体测试** | **SINGLE** | 65 / **85 / 89** | **3 次里 2 次失败（33%）** |
| #5 协调者测试 | COORDINATOR | 66 / **86** / 90 | 1 次失败（NPE） |
| #6 协调者测试2 | COORDINATOR | 67 / 87 / 91 | 全部成功 |

**失败不是均匀分布的：定时链路上唯一反复失败的是 #4（SINGLE 模式），两次都发生在 09:00 的定时触发上。**

---

## 二、64 条失败的分类归因

| 类别 | 条数 | 触发来源 | 当前代码是否仍可复现 | 根因一句话 |
|------|-----|---------|------------------|-----------|
| **A 会话超时** | 22 | 定时 2 + 手工 20 | **是**（今天 16:01 的 #105 仍复现） | SINGLE 模式整篇任务只给 300 秒，超时口径与 PIPELINE/COORDINATOR 不一致 |
| B SSE 连接失败 | 16 | 全部手工 | 部分是 | 上游网关各类错误（403/400/502/503/429/451/500），其中瞬时类没有重试 |
| C 未提交草稿 | 10 | 全部手工 | 少见 | 模型走完会话却没调 `save_article_draft`，`requireDraftSaved` 直接失败 |
| D 进程重启中止 | 5 | 全部手工 | 按设计 | 开发期反复重启，启动自愈把遗留 RUNNING 置为 FAILED |
| Z 未分类 | 3 | 全部手工 | 否 | 三条乱码中文，实为 D 类的旧手工清场记录 |
| I SSE 流停滞 | 2 | 全部手工 | 否 | 手工清理记录，非失败记录 |
| E 工具调用超限 | 2 | 全部手工 | **已修** | 上限 24 次时代的产物，现为 60/36/48/200 |
| F 并发额度等待超时 | 1 | 手工 | **已修** | 当时 `max-in-flight=1`，现为 4 |
| G 落库超长 | 1 | 手工 | **是** | `ARTICLE.CONTENT_HTML` 是 `TEXT`（64KB 上限），长文超限 |
| H NPE | 1 | **定时** | **是** | agent4j 对未知工具名不做判空 |
| J LLM 未配置 | 1 | 手工 | 否 | 开发期未配 Key |
| **合计** | **64** | | | |

### 2.1 A 类（22 条，含 2 条定时失败）——SINGLE 模式的超时口径错配

三条链路的会话硬超时**不是同一件事**：

| 链路 | 超时来源 | 覆盖范围 | 实际值 |
|------|---------|---------|-------|
| PIPELINE | `runWithLimit(5 参)` → `stage-timeout-seconds` | **每个阶段**一个会话 | 300s × 最多 4 阶段（返工可到 19 个会话） |
| COORDINATOR | `runWithLimit(7 参)` → `coordinator-timeout-seconds` | 主编一次会话覆盖全部委托 | 1800s |
| **SINGLE** | `ArticleAiService:552` 用的是 **6 参** `runWithLimit` | **整个任务（检索+写作+配图）只此一个会话** | **300s** |

SINGLE 的一次会话要做完调研、写作、配图三件事，却只拿到 PIPELINE「一个阶段」的额度。实测数据完全吻合：

- SINGLE 成功运行最长 272 秒（6 次成功，均值 146s）——**从未有 SINGLE 成功运行超过 300 秒**；
- SINGLE 失败运行 12 次，**全部卡在 300 秒**（均值 239s）；
- 定时失败 #85：47 次工具调用、300 秒被杀；#89：31 次调用、300 秒被杀；
- 今天（09-15 16:01）的 #105 是同一形态：23 次调用、300 秒——**证明这不是历史遗留，在当前代码上仍会复现**。

对照 PIPELINE：整轮成功运行均值 533~1194 秒。SINGLE 被要求在 300 秒内做完 PIPELINE 要跑十几分钟的事。

### 2.2 #85 / #89 的第二重死因——摘要校验把模型逼进死路

`#85` 的执行日志尾部（原文）：

```
调用工具：search_web ×40（第 41 次起被预算拒绝）
调用工具：set_article_draft_cover | 预算已用尽，放行收尾工具
调用工具：save_article_draft     | 预算已用尽，放行收尾工具 → 工具失败：文章摘要不能超过120字
调用工具：save_article_draft     | 预算已用尽，放行收尾工具 → 工具失败：文章摘要不能超过120字
调用工具：save_article_draft     → 工具失败：子智能体工具调用超过上限 40 次（收尾宽限已用完）
```

`ScheduledArticleTools:611` 的校验：

```java
if (rawDigest != null && rawDigest.length() > 120) {
    throw new IllegalArgumentException("文章摘要不能超过120字");
}
```

三个问题叠在一起：

1. **错误信息不含实际长度**——模型不知道自己是 121 字还是 400 字，只能盲改，两次都没改对；
2. **没告诉模型 digest 是可选参数**（`@Param(required = false)`），它本可以直接省略；
3. **收尾宽限只有 3 次**（`ToolCallBudget.TERMINAL_GRACE`），两次摘要重试就烧掉 2 次，第三次直接被预算拒绝——而 `deliverableSubmitted` 仍为 false，整轮判定为「阶段中止」。

前 40 次检索全部成功，却因为一个可以自动截断的摘要字段丢掉整篇文章。

### 2.3 G 类（1 条）——`CONTENT_HTML` 的 64KB 上限

`ARTICLE.CONTENT_HTML` 是 `text`（上限 65535 **字节**）。实测全库最大 53253 字节（文章 #47），而 #98 那次 COORDINATOR 跑了 79 次工具、1655 秒，产出的富文本越过了 64KB：

```
### Error updating database. Cause: MysqlDataTruncation: Data too long for column 'CONTENT_HTML'
```

这是**容量问题，不是偶发**：文章越长、组件越多，越接近上限。中文 UTF-8 占 3 字节，64KB 只相当于约 2 万字正文——精排文章很容易越过。

⚠️ **修法有约束**：项目用 smart-mybatis 自动建表/补列，但它**不改已有列长**（记忆与既有实践均确认）。把 `text` 改成 `mediumtext` 必须走显式 DDL，不能指望启动时自动迁移。

### 2.4 H 类（1 条，定时）——agent4j 对未知工具名不判空

`#86` 的报错：

```
java.lang.NullPointerException: Cannot invoke "ink.icoding.llm.core.tool.Tool.getClass()" because "tool" is null
```

字节码已定位到确切调用点（`agent4j-2.3.3.jar`）：

- `ToolDescriptor.fromTool(Tool)` 第一件事就是 `tool.getClass()`（`javap` 偏移 14），**无判空**；
- `OpenAIChatModel.handleToolCallsAndContinue` 偏移 152-169：

```java
Tool t = toolMap.get(entry.toolName);   // 工具名不在本次广告的列表里 → null
ToolDescriptor d = ToolDescriptor.fromTool(t);   // → NPE
```

即：**模型（或网关）返回了一个我们没广告过的工具名，`toolMap.get` 返回 null，agent4j 直接 NPE。** 由于异常发生在工具执行之前，我们的 `onTool` 回调从未触发，所以运行记录里工具数是 0、执行日志只有一行「【协调】启动主编智能体」——与 #86 的现场完全一致。

这是**上游缺陷**（本项目无法在 agent4j 内部插桩），但可以在本项目侧做两件事：把「零工具调用的 NPE」纳入重试集合（重建会话后大概率成功），以及向上游提 issue。

### 2.5 B 类（16 条，全部手工）——上游错误谱系与「瞬时类不重试」

上游返回过的错误，逐条列出（这是判断「哪些该重试」的依据）：

| 上游响应 | 次数 | 是否瞬时 |
|---------|-----|---------|
| `Connection reset` | 2 | 是 |
| HTTP 200 但 Socket closed / stream reset INTERNAL_ERROR | 2 | 是 |
| HTTP 502（nginx） | 1 | 是 |
| HTTP 500 openai_error | 1 | 是 |
| HTTP 503 model_not_found（`LongCat-2.0` 无可用渠道） | 2 | 否（配置错） |
| HTTP 429 concurrent limit exceeded（running=7 max=6） | 1 | 是（已由闸门+退避覆盖） |
| HTTP 429 免费额度限流 | 1 | 是 |
| HTTP 403 IP 不在白名单 | 1 | 否（出口 IP 变更） |
| HTTP 400 缺 `***.content` | 1 | 否（回放守卫已覆盖） |
| HTTP 451 censorship_blocked | 2 | 否（内容审查） |
| 其余 SSE connection failed | 2 | — |

`AgentInvoker.run` 的重试条件（`AgentInvoker:104-124`）：

```java
boolean retryable = attempt.toolCalls() == 0;          // 零工具调用（无付费副作用）
if (retryable && rateLimited && rateLimitRetries < 3)  // 只认 429
if (retryable && failure instanceof StageTimeoutException && stallRetries < 1)  // 只认停滞
throw failure;   // ← 连接重置 / 502 / 500 / NPE 全部直接抛出
```

**「零工具调用」这个安全前提已经具备了，但只接了两类错误。** 连接被重置、502、500 这些明确瞬时的失败，明明没有产生任何副作用，却一次都不重试。

关于 503 `model_not_found`：该次运行（#107，16:01）用的模型是 `LongCat-2.0`，而 `LLM_PROFILE` 在 **16:06:16** 被改成了 `hy4-preview`——即该问题在事发 5 分钟后已由配置变更修掉，当前不可复现。

---

## 三、现有护栏的评估（哪些在起作用，哪些有缝）

| 护栏 | 设计意图 | 实测表现 | 缝在哪里 |
|------|---------|---------|---------|
| `StageTimeout`（300s/1800s） | 把「永久挂死」变成「明确失败」 | 有效：再没出现过 1800s 的 0 工具调用挂死（09-11 那 3 条之后归零） | **SINGLE 用 300s 覆盖整篇任务**，口径与另两条链路不一致 |
| `AgentInvoker` 有界重试 | 上游瞬时失败自动恢复 | 部分有效：429 与停滞已覆盖 | **只认 429 和 StageTimeout**；连接重置/502/500/NPE 不重试 |
| `ToolCallBudget`（60/36/48/200 + 宽限 3） | 拦住失控检索，不拦提交成果 | 有效：24 次时代的中断已消失 | **宽限 3 次会被「格式校验失败」白白烧掉**，且失败的工具不该计入宽限 |
| `InFlightGate`（4，DB 租约） | 永不打满上游 6 的并发 | 有效：租约回收日志正常，无超发证据 | 无（设计正确） |
| `StaleRunPolicy` + `StaleRunReaper` | 进程崩溃后自愈 | 有效：5 条遗留 RUNNING 全部在启动时回收 | 无（按设计工作） |
| 阶段降级（PIPELINE） | 单阶段失败不丢整篇文章 | 有效：#104 带 1 处降级仍交付成功 | **SINGLE 没有降级路径**——它没有「阶段」概念，一旦失败整篇全丢 |
| 终态判定（工具失败/降级/渲染警告） | 不把缺图的文章记成干净成功 | 有效 | 无 |

---

## 四、解决方案（分四批，按性价比排序）

> 原则沿用项目既有惯例：**降级必须可见**（写日志 + 计入 `degradations`/`toolFailures`，终态变 `SUCCESS_WITH_WARNINGS`），不静默改数据。

### 第 ① 批：止血——把「反复失败的那一个」救回来（最高优先，改动最小）

**①-1 SINGLE 链路给独立的会话超时额度**（对应 A 类 22 条，含 2 条定时失败）

- 现状：`ArticleAiService:552` 走 6 参 `runWithLimit`，吃默认 300s。
- 改法：新增配置 `app.schedule.single-timeout-seconds`（默认建议 **900**），SINGLE 走 7 参重载显式传入。900 的依据：PIPELINE 整轮成功运行均值 533~1194s，SINGLE 做同样三件事；同时仍给失控会话封顶。
- 联动：**不需要改 `stale-run-hours`**（此处更正初稿的错误推断）。初稿写「900 × 19 ≈ 4.75h > 3h，必须同步调整」——错了：`StaleRunPolicy.MAX_STAGES_PER_RUN = 19` 建模的是 **PIPELINE 的阶段循环**（4 基础阶段 + 5 轮返工 × 3），而 `thresholdTooSmall` 的实参是 `stage-timeout-seconds`（300s，见 `StaleRunReaper:58`），**`singleTimeoutSeconds` 不参与这个估算**。SINGLE 只有一次会话（900s = 0.25h），离 3h 阈值远得很。`stale-run-hours: 3` 原样保留（已加注释说明 SINGLE 不在该上限内）。

**①-2 摘要校验改为「可见截断」而不是硬拒绝**（对应 #85/#89 的第二重死因）

- 现状：`ScheduledArticleTools:611` 超 120 字直接抛异常。
- 改法：超过 120 字时截断到 120 字，并在工作区记一条警告，运行终态变 `SUCCESS_WITH_WARNINGS`，日志写明「摘要 X 字已截断为 120 字」。标题 64 字同理。
- **警告字段必须新建，不能塞进 `renderWarnings`**（此处更正初稿的挂载建议）：`renderWarnings` 会被清空两次——`ScheduledArticleTools:629` 每次 save 时重置、`:683` 交付前整体覆盖为渲染器返回的警告。MARKFLOW 链路上保存期的警告必丢。故 `DraftState` 新增独立字段 `saveWarnings`（快照 `snapshot()` / `adopt()` / `Draft` record 三处同步），`TaskWorkspace.summary()` 出计数，`TaskExecutionService.completion(...)` 出新分支。
- 理由：摘要只是列表页的预览文案，截断的代价远小于丢掉整篇 40 次检索换来的文章；而「可见降级」保证了不静默改数据。
- 备选（若坚持不自动改内容）：错误信息补上**实际字数**与**可省略**提示，即 `"文章摘要不能超过120字（当前 187 字；也可直接省略 digest）"`——最小改动，但依赖模型第二次改对。

**①-3 收尾宽限只对「成功提交」计数**（对应 #85 的第三次调用被拒）

- 现状：`AgentInvoker:172` 的 `terminalGraceUsed` 在**每次**收尾工具调用时自增，无论成败。
- 改法：改为只在收尾工具**失败**时计数，或在 `COMPLETED` 时回退计数——避免「格式校验失败」烧掉提交机会。
- 收益：配合 ①-2 后基本不会触发，但作为独立护栏更稳。

### 第 ② 批：补护栏的缝（中等优先）

**②-1 瞬时上游失败纳入零副作用重试**（对应 B 类里的瞬时子集）

- 在 `AgentInvoker.run` 的重试判定里，把 `retryable == true`（零工具调用）时的可重试集合从「仅 429」扩展到「429 + 连接重置 + HTTP 5xx（502/503/500）」。
- 保持安全前提不变：**只要调用过任何工具就不重试**，这条线不动。
- 需明确排除：403（IP 白名单）、451（内容审查）、503 model_not_found（配置错）、400 缺 content——重试无意义。

**②-2 零工具调用的 NPE 纳入重试**（对应 H 类，定时失败 #86）

- 在 `AgentInvoker` 里把 `NullPointerException`（且 `toolCalls == 0`）视为可重试：重建会话后模型大概率不会再返回那个未知工具名。
- 同时：在 `AgentInvoker` 捕获该异常时，把**本次广告的工具名列表**写进执行日志——否则下次再遇到仍然查不出是哪个名字越界（当前 NPE 信息里没有工具名）。
- 向上游（agent4j）提 issue：`ToolDescriptor.fromTool` 与 `handleToolCallsWithContinue` 的 `toolMap.get` 缺判空。

**②-3 文章正文长文本列扩容到 `MEDIUMTEXT`**（对应 G 类，**已实施，DDL 待你确认后随重启生效**）

- 落地形态是**幂等 `ApplicationRunner`**（`ArticleLongTextColumnRunner`，`@Order(25)`，仿 `LlmProfileMigrationRunner`）：查 `information_schema` 判当前类型，已是 MEDIUMTEXT/LONGTEXT 就跳过，否则 `ALTER TABLE ... MODIFY COLUMN ...`；失败只告警不影响启动。表名/列名从 `information_schema` 回读（不拿实体注解值拼 DDL：开发库是 `ARTICLE`、注解是 `article`），可空性原样保留。
- **范围是 5 列 2 表，不是最初计划的 1 列**。同一份正文会落到多处，漏一处只是把报错换个列名：
  1. `ARTICLE.CONTENT_HTML` + `ARTICLE.CONTENT_TEXT`：两者在**同一条 INSERT** 上（落库前用 Jsoup 从 HTML 抽纯文本），只扩前者会把报错换成 `Data too long for column 'CONTENT_TEXT'`——这不是推断，是扩容用例当场跑出来的。
  2. `ARTICLE_REVISION.CONTENT_HTML`：`ArticleService.createWithUser:134` 建完文章立刻调 `snapshot()`，把**同一份 HTML** 写进版本表（`:450`）。实测该列已存 **62118 字节**（占 TEXT 上限 95%），只扩 ARTICLE 等于把 run#98 的失败从主表平移到版本表，长文照样整轮作废。**这一项是复核时才发现的**，原计划漏了。
  3. 两个 `CONTENT_MARKDOWN`：与 HTML 同源同写（`snapshot()` 一次写两表）。实测最大 14677 字节未越界，但 Markdown 约为 HTML 的 28%，HTML 放宽后它会跟着越界——同族隐患不留下。
- 实体注解同步改为 `@TableField(columnType = "MEDIUMTEXT")`（4 个字段）：`length` 表达不出 MEDIUMTEXT（`MysqlDialect.javaTypeToSql` 只有 VARCHAR/TEXT/LONGTEXT 三档），`columnType` 优先级最高。

**本次复核推翻了仓库里「smart-mybatis 不改已有列」这条旧结论。** 字节码依据：`SqlDialects.buildAlterTable`（default 方法，偏移 303-411）对已存在列比较 `normalizeType(声明)` 与 `normalizeType(库中实际)`，不等即发 `buildAlterColumn` → `MODIFY COLUMN`；`MapperUtil.updateTable` 拿到非空 DDL 就执行。而 `normalizeType` 只做 trim + 压空白 + 转小写，所以「声明 TEXT / 库中 text」相等、不会改；**声明与库中类型不同时一定会发 DDL**。

由此牵出一条更隐蔽的库行为，本次扩容正是踩在它上面：

> **`MapperUtil.getColumnDeclaration(Field)` 的缓存键是 `field.getName()` 单键，不含类名**（字节码偏移 0-12：`FIELD_COLUMN_DECLARATION_MAP.get(field.getName())`）。因此**不同实体的同名字段共用一份列声明**，谁先初始化谁说了算。

实测证据链（三条互相独立）：

1. 把 `Article.contentHtml` 声明成 MEDIUMTEXT 后，**只启动 Spring 上下文、不写任何数据**，`ARTICLE_REVISION.CONTENT_HTML` 就从 `text` 变成了 `mediumtext`（`ArticleLongTextColumnRunner` 当时只处理 `ARTICLE`，不可能是它做的）。
2. 现有库里的 `Asset.sourceUrl` 声明 `length = 2000`，真实列却是 `varchar(1000)`——被 `Article`/`ArticleRevision` 的同名字段（1000）收窄。这是该行为早已在库中生效的独立佐证。
3. 扫描 18 个实体，同名字段声明不一致的有 6 组（`contentHtml`、`contentMarkdown`、`author`、`name`、`provider`、`modelName`、`sourceUrl` 等）。修完本次的 4 个字段后仍有 5 组，全部与本轮无关。

**为什么必须连实体注解一起改**：若声明停在 TEXT 而列已放宽，同步逻辑会发 `MODIFY COLUMN ... TEXT` 把列**收窄回去**（长文再次写不进）；更糟的是收窄时若表中已有超长行，MySQL 会直接报错，风险从「落库失败」升级为**启动期 DDL 失败**。所以 DDL 负责存量库、注解负责让声明不再反向，两条路同向才安全。

- 未同批处理但需留意的同类列：`TASK_RUN.EXECUTION_LOG`、`TASK_RUN.MESSAGE`（`trimMessage` 已在 60000 字截断，说明这条链上已有过同类踩坑）、`AI_MESSAGE.CONTENT`。
- 这是**改库结构**的操作（runner 启动时自动执行，等于「重启即生效」）。

**②-4 停滞重试的「零工具调用」前提放宽（可选，需你拍板）**

- 现状：`已调用工具 1 次` 的停滞（实测 3 条，卡了 273~274 秒）不重试，因为 `toolCalls != 0`。
- 权衡：放宽到「零工具调用 **或** 最后一次活动超过 N 秒且未发生付费工具（`generate_image`/`import_web_image`/`delegate_*`）」可以救回这类；但会引入「重跑一段检索」的 token 成本，且判定逻辑变复杂。
- 我倾向**先不做**，等 ①-1 落地后看 A 类是否自然下降。

### 第 ③ 批：可观测性（低成本，建议同批做）

**③-1 运行历史里显式标注触发来源与超时口径**：`TASK_RUN` 已有 `TRIGGER_TYPE`，但失败信息里不体现「本次用的是哪个超时」。建议把生效的超时值与链路模式写进 `message`（例如「SINGLE 会话超时（900 秒未结束）」已含数值，但 PIPELINE 的失败信息不说是哪个阶段用了 300s）。

**③-2 只读巡检 SQL**（已写入本文档，见下方）：一键列出「定时触发」的运行及其分类，把「真调度失败率」变成可定期查看的数字——本次归因最大的成本就是 61 条手工噪声淹没了 3 条真信号。

**巡检 SQL（只读，直接贴进客户端跑；`TRIGGER_TYPE` 是区分定时/手工的唯一判据）**

```sql
-- 1) 定时触发的成功率总览（先看这一条，别被手工噪声带偏）
SELECT COUNT(*)                                                AS 定时总次数,
       SUM(STATUS IN ('SUCCESS', 'SUCCESS_WITH_WARNINGS'))      AS 成功,
       SUM(STATUS = 'FAILED')                                   AS 失败,
       ROUND(100 * SUM(STATUS = 'FAILED') / COUNT(*), 1)         AS 失败率百分比
FROM TASK_RUN
WHERE TRIGGER_TYPE = 'SCHEDULED';

-- 2) 定时失败逐条明细（按任务聚合，一眼看出是不是同一个任务反复挂）
SELECT TASK_ID,
       COUNT(*)                    AS 失败次数,
       MIN(STARTED_AT)             AS 首次,
       MAX(STARTED_AT)             AS 最近,
       MAX(LEFT(MESSAGE, 200))     AS 最近一条失败信息
FROM TASK_RUN
WHERE TRIGGER_TYPE = 'SCHEDULED' AND STATUS = 'FAILED'
GROUP BY TASK_ID
ORDER BY 失败次数 DESC;

-- 3) 失败原因分型：把消息里的超时秒数抽出来，验证「SINGLE 恒卡 300s」是否已消失
SELECT TASK_ID,
       REGEXP_SUBSTR(MESSAGE, '[0-9]+ ?秒')  AS 报错里的秒数,
       COUNT(*)                             AS 条数
FROM TASK_RUN
WHERE TRIGGER_TYPE = 'SCHEDULED' AND STATUS = 'FAILED'
GROUP BY TASK_ID, 报错里的秒数
ORDER BY 条数 DESC;

-- 4) 落库失败是否还在（长文扩容的验收判据）
SELECT ID, TASK_ID, STARTED_AT, LEFT(MESSAGE, 200) AS 失败信息
FROM TASK_RUN
WHERE STATUS = 'FAILED' AND MESSAGE LIKE '%Data too long%'
ORDER BY STARTED_AT DESC;
```

> `TRIGGER_TYPE` 的取值以库中实际数据为准（本次归因用的是 `SCHEDULED` / 手工触发两类）；若你的库里手工触发是别的字面量，把上面几处等值条件改掉即可。**只读，不改任何数据。**

### 巡检 SQL（第二轮补充：模型档案与阶段耗时）

> 背景：2026-09-15 引入「模型档案故障切换」后，运行历史里新增了两项可统计的数据——
> `STAGES_SUMMARY.profilesUsed`（本轮实际用过的档案链）与 `STAGES_SUMMARY.stages`（每阶段耗时 / 工具数）。
> 下面 4 条回答「哪个模型在拖后腿」「时间花在哪一阶段」这两个此前只能靠人读日志的问题。
> **只读。** 需要 MySQL 8（用到 `JSON_EXTRACT` / `JSON_TABLE`）。

```sql
-- 5) 按模型档案统计成功率与耗时（档案链里的第一个即主用档案）
--    限定 profilesUsed 存在，否则会混入改造前的运行（那些没有这项数据）
SELECT JSON_UNQUOTE(JSON_EXTRACT(STAGES_SUMMARY, '$.profilesUsed[0]'))  AS 主用档案,
       COUNT(*)                                                          AS 运行次数,
       SUM(STATUS IN ('SUCCESS', 'SUCCESS_WITH_WARNINGS'))                AS 成功,
       ROUND(AVG(TIMESTAMPDIFF(SECOND, STARTED_AT, FINISHED_AT)))         AS 平均耗时秒,
       MAX(TIMESTAMPDIFF(SECOND, STARTED_AT, FINISHED_AT))                AS 最长耗时秒,
       ROUND(AVG(TOOL_CALL_COUNT))                                        AS 平均工具次数
FROM TASK_RUN
WHERE STAGES_SUMMARY LIKE '%profilesUsed%'
GROUP BY 主用档案
ORDER BY 运行次数 DESC;

-- 6) 发生过档案切换的运行：主用档案当时不可用或停滞，这些是最该关注的那几条
SELECT ID, TASK_ID, STARTED_AT, STATUS,
       JSON_UNQUOTE(JSON_EXTRACT(STAGES_SUMMARY, '$.profilesUsed')) AS 档案链,
       LEFT(MESSAGE, 200)                                           AS 说明
FROM TASK_RUN
WHERE JSON_LENGTH(JSON_EXTRACT(STAGES_SUMMARY, '$.profilesUsed')) > 1
ORDER BY STARTED_AT DESC;

-- 7) 阶段耗时明细：一次运行里哪个阶段最慢（COORDINATOR 每次委托各一条 DELEGATE_* 记录）
SELECT R.ID                                          AS 运行,
       R.TASK_ID,
       R.STARTED_AT,
       JSON_UNQUOTE(JSON_EXTRACT(S.VALUE, '$.stage')) AS 阶段,
       JSON_EXTRACT(S.VALUE, '$.seconds')             AS 耗时秒,
       JSON_EXTRACT(S.VALUE, '$.toolCalls')           AS 工具次数
FROM TASK_RUN R
         JOIN JSON_TABLE(R.STAGES_SUMMARY, '$.stages[*]'
              COLUMNS (VALUE JSON PATH '$')) AS S
WHERE R.STAGES_SUMMARY LIKE '%"stages"%'
ORDER BY R.STARTED_AT DESC, 耗时秒 DESC;

-- 8) 失败类别分布：MESSAGE 自 2026-09-15 起带【类别】前缀，据此看失败集中在哪一类
SELECT REGEXP_SUBSTR(MESSAGE, '^【[^】]+】') AS 失败类别,
       COUNT(*)                             AS 条数,
       MAX(STARTED_AT)                      AS 最近一次
FROM TASK_RUN
WHERE STATUS = 'FAILED' AND MESSAGE LIKE '【%】%'
GROUP BY 失败类别
ORDER BY 条数 DESC;
```

> 第 8 条的类别前缀由 `TaskExecutionService.failureMessage` 写入（2026-09-15 起）；
> 更早的失败行没有前缀，会被这条 SQL 过滤掉，属预期。
> 第 7 条的 `STAGES_SUMMARY` 在改造前是「工作区摘要」的早期版本（无 `stages`），同样被过滤掉。

### 第 ④ 批：需要你决策的事项（不是缺陷，是配置）

**④-1 四个任务全部启用且全部在 `0 0 9 * * ?` 同时触发**

`SCHEDULE_TASK` 里 #2/#4/#5/#6 四个任务 `ENABLED=1`，cron 完全相同，且四条的 `AI_PROMPT` 几乎一致（都是「每天检索过去 24 小时的 AI 产品与行业动态，写一篇公众号文章」）。结果是每天 09:00 有 4 个智能体运行同时抢 4 个并发名额，产出 4 篇高度相似的文章。

请确认这是否是有意为之：
- 若只是想验证三条链路（SINGLE/PIPELINE/COORDINATOR）都能跑通 → 建议只保留 1~2 个，其余停用或错开时间；
- 若确实要每天 4 篇 → 那 09:00 的并发正好打满闸门（limit=4），需要留意编辑器链路会排队。

**④-2 摘要字段的产品预期**：确认「自动截断到 120 字」可接受（①-2 的推荐方案），还是宁可失败也不改内容（则采用备选方案）。

---

## 五、执行顺序与验收方式

1. ~~第 ① 批（①-1 / ①-2 / ①-3）~~ —— **已完成**。注：①-1 经核实**不需要**同步复核 `stale-run-hours`（见 ①-1 的更正说明）。
2. ~~跑确定性闸门~~ —— **已通过**：`.mvn/mvn-local.sh -o test` → **329 例全绿**；`webui` 共享导入自检通过。
3. ~~第 ② 批（②-1 / ②-2 代码侧）~~ —— **已完成**；②-3 的代码侧（幂等 runner + 实体注解）已完成并打了真库反证。
4. ~~改库 + 重启~~ —— **已执行**（2026-09-15 19:05，新实例 PID 22548）。实机结果与预期有一处出入，值得记下来：
   - **实际执行 DDL 的是 smart-mybatis 自己的结构同步，不是 `ArticleLongTextColumnRunner`。** 因为实体注解已改成 `columnType = "MEDIUMTEXT"`，同步阶段在 `ApplicationRunner` 之前就把 5 列 `MODIFY` 成 `mediumtext` 了；runner 随后查 `information_schema` 发现类型已足够，按设计走「跳过」分支（`log.debug`，故启动日志里**看不到**预期的 5 行扩容记录）。这正是「注解与真实列必须同向」那条结论的又一次自证——两边都声明 MEDIUMTEXT 时，谁先执行都能收敛到同一结果。
   - 实测 5 列均已 `mediumtext`（`CHARACTER_MAXIMUM_LENGTH = 16777215`）：`ARTICLE.CONTENT_HTML/CONTENT_TEXT/CONTENT_MARKDOWN`、`ARTICLE_REVISION.CONTENT_HTML/CONTENT_MARKDOWN`。
   - 活体反证：在开发库上对上述 5 列各插入 25,000 个汉字（**75,000 字节**，超出旧 TEXT 上限 65,535 字节），5 列全部插入成功，随后 `ROLLBACK`，未污染开发数据。
5. **实机验收 —— 已通过**（2026-09-15 20:14~21:28，run#116 + run#119）：
   - ~~确认 5 列为 `mediumtext`~~ —— **已完成**（见上，含 75,000 字节活体插入反证）。
   - **SINGLE 链路端到端跑通（#4，run#116）**：终态 `SUCCESS_WITH_WARNINGS`，`ARTICLE_ID = 58`。
     - **耗时 554 秒（20:14:18 → 20:23:32）、36 次工具调用**。这是本轮最关键的一条对照：旧配置下 SINGLE 从未成功跑过 300 秒（12 次失败全部卡在 300s），**554 秒的成功运行证明 ①-1 的 900 秒超时确实解除了天花板**。
     - **①-2 截断与可见警告实机生效**：终态消息为 `有 1 次工具调用失败，有 1 处保存降级（摘要 129 字已截断为 120 字）（详见执行日志）`——正是设计中的行为：不再硬拒绝，而是截断并在终态显式提示。落库核对 `ARTICLE.ID=58` 的 `DIGEST` 字符数正好是 **120**。
   - **COORDINATOR 链路端到端跑通（#5，run#119；即 #98 所属任务）**：终态 `SUCCESS_WITH_WARNINGS`，`ARTICLE_ID = 59`。
     - **耗时 1,801 秒（20:58:07 → 21:28:08）、86 次工具调用**——单次运行近半小时，远超 SINGLE 的旧 300 秒额度，也远超 PIPELINE 的单阶段 300 秒。
     - 终态消息 `有 3 次工具调用失败，有 1 个阶段中止并已按现有产出继续（详见执行日志），交付内容可能不完整。协调者已完成本次文章创作`，同样带**可见警告**而非静默截断。
     - **#98 的 `Data too long` 未复现**：文章 59 落库成功，`CONTENT_HTML` 41,327 字节、`CONTENT_TEXT` 10,122 字节、`CONTENT_MARKDOWN` 11,631 字节；`ARTICLE_REVISION` 同篇 `CONTENT_HTML` 41,327 / `CONTENT_MARKDOWN` 11,631 字节——**主表与版本表都写入成功且数值一致**。
   - **长文落库的完整证据链**（两次成功运行都只用到 41~46KB，未触及旧 64KB 上限，故补列级反证）：5 列均为 `mediumtext`（上限 16,777,215 字节），且对 5 列各插入 **75,000 字节**（超出旧 TEXT 上限 65,535 字节）全部成功、随后 `ROLLBACK`。两者合起来覆盖「上限已抬高」与「真实写入路径可用」。
   - **重试机制已实机生效**：启动日志出现两条 `上游瞬时错误（第 1/5 次重试，1000 ms 后重建会话）：... HTTP 429`（19:17:36 无前缀 / 19:29:27 `【协调】`），证明 `transient-max: 5` 与退避配置已加载并触发。未重试的几次是**设计使然**：它们已发生 1~59 次工具调用，不满足「零工具调用」的重试前提（可能有付费副作用），护栏按预期拒绝了重试。
   - **过程中澄清的两类上游状况（均非本轮缺陷）**：
     - 验收初期的 429：`HTTP 429 rate_limit_error / 429006`（并发名额打满）。直连探测网关（`GatewayProbe`：解密库中密钥后发最小请求）确认**网关本身在探测时刻是正常的**——单次非流式、连发 12 次、50KB 大 prompt 流式、带 tools 的流式请求**全部返回 HTTP 200**；`LLM_LEASE` 表为空（无泄漏名额）。**两种解释都成立、本轮未能区分**：(a) 网关对长时间占用的智能体会话按并发名额限流，短请求不占名额；(b) 网关间歇性拥塞，探测恰好落在空闲窗口。无论哪种，都不是本轮改动引入的，也不需要改代码——名额释放后运行即跑通。
     - 中途的 402：试用额度耗尽（`free trial quota ... exhausted and postpaid billing is not enabled`），run#117（59 次工具调用后）与 run#118（1 次后）各撞一次，探测显示时好时坏、其余模型名一律 503（未开通），属账号级状态，与本轮改动无关。
   - **顺带补的一个分类器缺口**：run#117 的 402 此前只是**误打误撞**被拦下——`isPermanent` 的关键字表里并没有 402，是错误体里的 `code: "401008"` 含 `"401"` 才命中。已显式加入 `402` / `free trial quota` / `postpaid billing` / `permission_error`，并补了对应的不重试用例（`AgentInvokerTest` 23 例）。**若不加这一条，一旦上游把错误码改成不含 `401` 的形式，402 会被当成瞬时错误重试 5 次、每次白烧 31 秒。**
   - 等一个自然日的 09:00 定时触发，用本文件的巡检 SQL 看 #4 是否转绿（本轮只验证了手工触发路径）。

### 第 ⑤ 项：本轮发现但**未修**的遗留（同名字段声明冲突，需你决定是否纳入下轮）

用 `FieldCollision` 扫描 18 个实体后，除本轮修掉的 `contentHtml`/`contentMarkdown` 外，**还有 5 组**同名字段声明不一致（`author`、`modelName`、`name`、`provider`、`sourceUrl`）。这是「`MapperUtil.getColumnDeclaration` 按字段名缓存、不区分实体类」这条机制的直接后果。

其中 **`sourceUrl` 已经造成了实际收窄**：`Article.java`/`ArticleRevision.java` 声明 `length = 1000`，`Asset.java` 声明 `length = 2000`，而开发库里 `ASSET.SOURCE_URL` 的实际宽度是 **varchar(1000)** —— 声明 2000 的实体对应的列被压回了 1000，这正是「声明与真实列必须同向」的反面案例。当前 `ASSET.SOURCE_URL` 里最长值远未触顶，所以尚未爆出故障，属于**静默隐患**。

本轮**不动**它们，原因有二：一是超出「定时任务失败归因」的范围；二是修它要动 3 个实体 + 可能触发 `MODIFY`，需按 ②-3 同样的流程（先反证、再改库）单独走一轮。建议下轮处理，优先级 `sourceUrl` > 其余四组（那四组只影响新建表的宽度，存量库暂未收窄）。

> 涉及改库结构（②-3）、重启服务、部署到线上——**均需你单独确认**。本轮已获「改库 + 重启」授权并执行；**部署到线上仍未授权**。
