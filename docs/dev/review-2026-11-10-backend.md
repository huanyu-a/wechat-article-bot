# 后端独立复查报告（review-backend / task-4）

- 复查对象：`wechat-article-bot` 后端（`src/main/java`、`src/test/java`），重点为本轮「模型档案 → 供应商 + 模型」改造。
- 复查人：review-backend（独立复查员，未参与本轮改造）。
- 时间：2026-11-10 14:0x–14:2x（Asia/Singapore）。
- 约束遵守：只读产品代码，未修改任何产品文件；未运行任何 `mvn`；未提交 git；未改数据库（只用 `docker exec ... mysql` 跑 `SELECT`/`information_schema`）；未重启/未写 18081；对 18081 只发了 1 次只读 `GET /api/health`；未打印任何密钥/口令（引用 `.env` 只说键名）。
- 本报告是本次复查唯一写入的文件。
- 结论：**有条件通过**。未发现 `[CRITICAL]`；发现 3 条 `[MAJOR]`（均为「静默失效/静默改写」类，非崩溃、非越权、非泄密）与 8 条 `[MINOR]`。鉴权、掩码、审计、`@MapperScan` 约束、列声明一致性、回填幂等、Runner 不阻塞启动、测试健康（583 例真断言）均**已核实为正常**。

---

## 0. 项目事实校正（与任务简报不同的一处）

| 项 | 简报 | 实测 | 证据 |
|---|---|---|---|
| Spring Boot 版本 | 「Spring Boot 3」 | **4.1.0** | `pom.xml:7-9`（`spring-boot-starter-parent` 4.1.0）；运行中实例的 argfile 里全是 `spring-boot-*-4.1.0.jar`（`Get-CimInstance` 取到的命令行 + argfile 内容）；测试里用的 `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` 也是 Boot 4 的包路径（`LlmProviderApiIntegrationTests.java:8`） |
| Java | 17 | 17（一致） | argfile 命令行 `C:\Program Files\Java\jdk-17\bin\java.exe` |
| 其它依赖 | — | mybatis-spring-boot-starter 3.0.5、smart-mybatis 3.0.1 | `pom.xml:52-61` |

对复查的影响：不存在；但引用「Boot 3 的默认行为」类结论时应改口径。

另有一条**查询陷阱**（供后续复查者省时间）：`smart-mybatis` 在 MySQL 上建的**表名是大写**（`LLM_PROVIDER` / `LLM_PROFILE`），而本机 MySQL `@@lower_case_table_names = 0`。用小写表名查 `wechat-article-smoke.llm_provider` 会报 `ERROR 1146 Table doesn't exist`（我实际踩到），必须用大写或 `UPPER(TABLE_NAME)=...`。

---

## 1. 问题清单

### [MAJOR] 1. 回填把「全局 LLM 开关」当成供应商启用位 ⇒ 与 llm_config 三元组相同的整组档案会在升级后从故障切换链上静默消失

- **位置**
  - `src/main/java/ink/icoding/wechat/article/agent/LlmProviderBackfillRunner.java:172-175`（`matchesConfig` 分支把 `config.getEnabled()` 传下去）
  - `src/main/java/ink/icoding/wechat/article/agent/LlmProviderService.java:212`（`effectiveEnabled = Boolean.TRUE.equals(enabled) && apiKeyEncrypted != null`）、`:221`、`:229`（写入 `enabled`）
  - 对照路径：`LlmProviderService.java:256`（`attachOrCreateProvider` 用「有没有 key」决定启用位）
  - 受影响读取点：`src/main/java/ink/icoding/wechat/article/agent/LlmProfileService.java:395`（`profileEnabled && provider.enabled()`）
- **现象**：回填时若某个档案组的 `(provider, 归一化 baseUrl, 明文 key)` 与 `llm_config` 当前行一致，代码会调 `resolveDefaultProviderId(..., config.getEnabled())`；当 `llm_config.enabled = 0` 时该供应商被建/改成 **停用**，于是这一组档案的 `runtime().enabled()` 变成 `false`、整组从 `failoverChain` 上消失。同一轮里其它组走 `attachOrCreateProvider`，按「有 key 即启用」被建成启用——**同一个回填器里两套启用位口径**。
- **为什么知道的**（怎么知道的）
  1. 逐行读 `LlmProviderBackfillRunner.backfill()/resolveProviderId()` 与 `LlmProviderService.resolveDefaultProviderId()`，确认 `config.getEnabled()` 是唯一来源；
  2. 对照契约 `docs/dev/llm-provider-refactor.md:206-207`（§5.4 规则 3）——规则只规定**命名**（`默认供应商` / host），完全没有说要同步启用位，说明这是实现自行加的口径；
  3. 读 `LlmProfileService.runtime():389-398` 确认 `enabled = 档案.enabled && 供应商.enabled`，停用供应商 ⇒ 档案在链上消失；
  4. 只读 SQL 排除「本次已触发」：`SELECT PROVIDER,BASE_URL,ENABLED FROM wechat-article-smoke.LLM_CONFIG` → `OPENAI_COMPATIBLE / https://settings.accept.test / 1`，而两个供应商名为 `nexus.bx9y.com.cn`、`api.anthropic.com`（host 命名 ⇒ 走的是 `attachOrCreateProvider` 分支），即**这条分支本次没有被真跑过**（这是推理，不是实测，见 §3 未验证项 3）。
- **影响**：触发条件是「`llm_config` 有 key 且 `enabled=0`」+「存在与它三元组一致的档案组」。而「8 条档案的 `PROVIDER/BASE_URL/API_KEY_ENCRYPTED` 三列逐字复制自 llm_config」正是本项目存量库的已知形态（契约 §1 与 `LlmProfile.java:15-19` 都这么写）。命中时后果是**主模型组静默不可用**：不报错、不写日志、档案页看起来一切正常（档案自身 `enabled=true`），表现为智能体少一跳退路或直接报「LLM 尚未在系统设置中启用或未配置 API Key」（`AgentFactory.java:309`）。
- **建议修法**：回填路径改为「有 key 即启用」（与 `attachOrCreateProvider` 对齐），即 `resolveProviderId` 里不要传 `config.getEnabled()`，或给 `resolveDefaultProviderId` 增加一个显式的 `applyEnabled` 参数，仅由设置页写透路径传 `true`；若坚持同步，则在 `LlmProviderBackfillRunner` 里对该组只 warn 一次并跳过供应商停用（保持档案可用）。

---

### [MAJOR] 2. 设置页写透会改写**其它档案正在使用的通道**：「默认供应商」是一个可被任意档案引用、又会被整行改写的可变载体

- **位置**
  - `src/main/java/ink/icoding/wechat/article/agent/LlmProviderService.java:206-233`（尤其 `:213` `mapper.findByName(DEFAULT_PROVIDER_NAME)` 与 `:226-231` 的整行改写）
  - 调用点：`src/main/java/ink/icoding/wechat/article/agent/LlmProfileService.java:462-463`（`PUT /api/settings/llm` 写透）
  - 可引用性证据：`LlmProviderService.java:127-135`（删除护栏按 `llm_profile.provider_id = id` 计数 ⇒ 档案本来就可以挂到任意供应商，包括「默认供应商」那一行）
- **现象**：写透的第二步是「未命中三元组 ⇒ 复用名为 `默认供应商` 的那一行，把 `provider/baseUrl/apiKeyEncrypted/enabled` 全部写上去」。这一行**不专属设置页**：用户可以（在供应商页）把它当作普通供应商使用，并在「模型档案」页把若干条（非默认）档案挂到它上面。此后任何一次设置页保存（换了 key 或端点）都会把这一行整行改写 ⇒ 那些档案的端点与密钥**静默换成了设置页的值**，用户点的是「保存系统设置」，动的是另外几条档案的通道。
- **为什么知道的**
  1. 读 `resolveDefaultProviderId` 全流程：命中即 return（不改行），未命中即按**名字**取行并整行覆盖（`:226-231`），覆盖字段包含端点与密钥；
  2. 写「谁是默认供应商」只由名字 `"默认供应商"`（常量 `:45`）决定，没有任何「该行被 N 条档案引用」的校验；
  3. 用只读 SQL 确认档案引用的自由度：`smoke` 库 16 条档案全部 `PROVIDER_ID` 非空且 0 条悬空（`LLM_PROFILE p LEFT JOIN LLM_PROVIDER v ... WHERE v.ID IS NULL` → 0），即档案确实自由挂靠；
  4. 单元用例 `LlmProviderServiceTest.writeThroughReusesAndRewritesTheDefaultProviderRow`（`src/test/java/.../LlmProviderServiceTest.java:244-257`）**正面断言了这个改写行为**——所以这是有意为之，但该用例只覆盖「无第三者引用」的情形，没有覆盖「该行被别的档案引用」。
- **影响**：静默改错通道。请求打到用户没打算用的网关上，且不报错；因为密钥也一起换了，可能表现为 401/404 或计费打到别处。属于契约 §5.3 第 2 条没考虑到的副作用（契约只写了「复用/创建那一行」，没写「那一行可能被别的档案引用」）。
- **建议修法**（任选其一，按代价从小到大）
  1. 写透时先算 `usedModelCount(默认供应商 id)`，若 > 0 且其中存在**非默认档案**，则新建一行 `默认供应商 (2)` 并把默认档案指过去，绝不改写被别人引用的行；
  2. 或把「默认供应商」这一行做成设置页专属：档案写入接口（`LlmProfileService.apply`）拒绝 `providerId` 指向该行，前端下拉里也过滤掉它；
  3. 至少要在改写前 warn 出「该行还被 N 条档案引用」。

---

### [MAJOR] 3. 故障切换链有容错、紧随其后的回落路径没有：坏密钥让链清空后，回落分支再抛同一个异常 ⇒ 500，而不是降级到 llm_config

- **位置**
  - 容错侧：`src/main/java/ink/icoding/wechat/article/agent/LlmProfileService.java:144-150`（`addIfUsable` 里 catch `RuntimeException`，跳过脏档案）
  - 无容错侧：`src/main/java/ink/icoding/wechat/article/agent/AgentFactory.java:222-225`（链空 ⇒ `createModel(definition)`）与 `:293-300`、`:303-307`（`runtime(...)` 未包 try）；`src/main/java/ink/icoding/wechat/article/settings/LlmConfigService.java:79-80`、`:161-162`（`runtime()`/`view()` 未包 try）
  - 抛点：`src/main/java/ink/icoding/wechat/article/common/CryptoService.java:45-59`（`decrypt` 失败抛 `IllegalStateException`）
  - 兜底：`src/main/java/ink/icoding/wechat/article/common/GlobalExceptionHandler.java:37-41`（兜成 500 + 固定文案）
- **现象**：当某供应商行的 `API_KEY_ENCRYPTED` 解不开（`APP_SECRET_KEY` 轮换过、或手工写入过非法值）时：
  1. `failoverChain` 的每条候选都被 `addIfUsable` 跳过 ⇒ 链为空；
  2. `createModelCandidates` 发现 `models.isEmpty()` ⇒ 回落 `createModel(definition)`（设计意图是「回落到 llm_config」）；
  3. 但 `createModel` 内部又对同一条档案调 `llmProfileService.runtime(...)`（`:293`）或对默认档案调它（`:303`），**没有 try/catch**，于是抛出与第 1 步被吞掉的同一个 `IllegalStateException`；
  4. 结果：不是「降级到 llm_config」，而是整个智能体装配 500（`GlobalExceptionHandler:37-41`）。
  同类：`LlmConfigService.runtime()` 会在 `ArticleAiService.java:261` 的可用性判断处抛；`LlmConfigService.view()` 会让 `GET /api/settings/llm` 直接 500（设置页整页打不开）。
- **为什么知道的**
  1. 读 `addIfUsable` 的 catch 与 `AgentFactory.createModelCandidates/createModel` 的调用链，逐条确认回落分支没有等效保护；
  2. 读 `CryptoService.decrypt`：解不开一律 `IllegalStateException("敏感信息解密失败，请检查 APP_SECRET_KEY", cause)`（所以**不会泄密**，见 §2）；
  3. 全仓 grep `log.*apiKey|apiKey.*log` 无命中，确认这不是「已有日志泄露」，而是「错误处理口径不一致」。
- **影响**：与 [MAJOR] 1/2 同一类——静默/降级失效。改造前坏行只影响「那一条档案」；现在供应商是**共享行**，坏一把 key 的影响面扩大到该供应商下全部档案 + 设置页 + AI 可用性判断。
- **建议修法**：给 `AgentFactory.createModel(definition)` 与 `LlmConfigService.runtime()/view()` 里对 `runtime(...)` 的调用加同款 try/catch（catch 后按「不可用」继续回落 llm_config），或把 `CryptoService.decrypt` 在**读路径**上改为「失败返回 null」（写路径继续抛），让 `available()` 自然判 false——后者更彻底，且与 `normalizeBaseUrlOrNull`（`LlmProfileService.java:306-313`）已有的「读路径不抛」口径一致。

---

### [MINOR] 4. N+1：每条档案两次供应商查询；`list()` 按档案条数重复解密

- **位置**：`LlmProfileService.java:141-154`（`addIfUsable → runtime → LlmProviderService.runtimeById → mapper.findById`，每候选一次）+ `AgentFactory.java:213-221`（对链上每条**再**调一次 `runtime`）+ `LlmProfileService.java:59-63`、`:347-350`（列表里按档案逐条解密供应商密钥）
- **现象**：装配一组候选模型的查询数 ≈ `3 + M + K`（`M` = 已启用档案数，`K` = 链长），外加 `K` 次 AES-GCM 解密；其中 `M` 那部分是**重复劳动**——`failoverChain` 为了判断可用性已经把每条的 `RuntimeProfile` 算过一遍，循环里又算一遍。
- **为什么知道的**：逐行读调用链（`failoverChain` → `addIfUsable` → `runtime` → `runtimeById` → `mapper.findById`，以及 `AgentFactory:216` 的第二次 `runtime`）；`LlmProviderService.findByTriple:175` 的「全表读 + 逐行解密」也在这条路径上。
- **影响**：每次装配智能体（编辑器会话、每次定时任务运行）都付这份开销；档案条数线性增长（`createModelCandidates` 每次请求都会跑：`AgentFactory.java:136`、`:170`）。当前量级（十几条档案）无感，属可维护性问题而非现网性能问题。
- **建议修法**：让 `failoverChain` 返回 `(profile, runtime)` 对（或返回 `Map<Long, RuntimeProfile>`），消掉第二次解析；`view(profile, providerCache)` 里把解密结果按 `providerId` 缓存。

---

### [MINOR] 5. 唯一性只有应用层（先查后插）；两个「保留名」在写接口上没有任何保护

- **位置**：`LlmProviderService.java:174-182`（`findByTriple`）、`:282-290`（`uniqueName`）、`:314-319`（`validateNameUnique`）；`LlmProfileService.java:275-280`（档案同名校验）；`LlmProviderService.java:45`（`DEFAULT_PROVIDER_NAME = "默认供应商"`）、`LlmProfileService.java:37`（`DEFAULT_PROFILE_NAME = "默认配置"`）
- **现象**：名字唯一性完全靠「先 SELECT 再 INSERT」，且**数据库上没有唯一索引**；`默认供应商` / `默认配置` 这两个被代码当标识符用的字符串，在创建/更新接口上没有被拒绝。
- **为什么知道的**：只读 SQL `information_schema.STATISTICS` → `LLM_PROVIDER` 只有 `PRIMARY(ID)`，没有任何其它索引；`LLM_PROFILE` 在 `NAME / IS_DEFAULT / IS_FALLBACK / PROVIDER_ID` 上也查不到索引（`LLM_PROVIDER` 复现方式：`SELECT INDEX_NAME,NON_UNIQUE,COLUMN_NAME ... TABLE_NAME='LLM_PROVIDER'`，输出只有 `PRIMARY 0 ID`）。
- **影响**：（a）并发（多实例、或两个管理员同时提交）下可能建出重名行或两条 `is_default`，而 `findByName`/`findDefault` 用的是 `selectFirst`，命中哪一行取决于库的返回顺序 ⇒ 「设置页写透改的是哪一行」变得不确定；（b）`usedModelCount` 的护栏与 `defaultProfile()` 的回落都建立在「唯一」这个未经 DB 保证的前提上。
- **现实性**：当前**不构成本机故障**——我实测两台应用实例连的是不同库（`information_schema.PROCESSLIST`：`wechat-article` 10 连接来自 172.18.0.2 容器；`wechat-article-smoke` 10 连接来自 172.18.0.1 = 宿主机，即 18081 那个 Windows 原生 JVM），所以没有共享库的并发写入。但项目本身带 `LlmLease`（`common/LlmLeaseMapper.java`）说明设计上允许多实例，属潜在面。
- **建议修法**：给 `LLM_PROVIDER.NAME` 加唯一索引（并在应用层把 `DuplicateKeyException` 转成 `BusinessException("供应商名称已存在")`）；`is_default` / `is_fallback` 若要继续用「应用层唯一」，至少加一条集成断言；`默认供应商`/`默认配置` 建议在写接口上拒绝或自动改名。

---

### [MINOR] 6. 写透「收养同名档案」时会覆盖用户自建档案的业务字段（有可达性门槛）

- **位置**：`LlmProfileService.java:466-477`（`findDefault() == null` ⇒ `findByName("默认配置")` 命中就 `setIsDefault(true)` 收养）配合 `:478-487`（无条件覆盖 `providerId / provider / baseUrl / modelName / apiKeyEncrypted / temperature / maxTokens / enabled`）
- **现象**：若库里存在一条用户自己建的、名为 `默认配置` 的**非默认**档案，则下一次设置页保存会把它收养为默认档案，并用设置页的值覆盖它的模型名/温度/令牌上限/供应商绑定。这是任务清单里问的「同步设置页写透是否可能劫持用户的非默认档案」——**答案是「能，但需要特定状态」**。
- **为什么知道的**
  1. 读 `syncDefaultFromConfig` 全流程（`:458-494`），确认收养分支只补 `isDefault`、不区分「这条档案是迁移来的」还是「用户自建的」，且随后无条件覆盖业务字段；
  2. 逐条检查 `is_default` 的所有写入点（`create:181` 置 false、`setDefault:210-221` 清其它/置本条、`delete:200-206` 拒绝删默认、`update:189-197` 不动该列）后确认：**通过 HTTP API 走不出「有档案但一条 is_default 都没有」这个状态**，因此该分支现实可达性低——只在手工改库/恢复备份/历史脏数据时成立。故定级 `[MINOR]` 而非 `[MAJOR]`；
  3. `AgentApiIntegrationTests.java:636-643` 的 javadoc 也确认「不再回落 findFirst」这个既有结论仍然成立。
- **影响**：该状态下用户档案被静默改写，且因为变成默认档案而**不可删除**（`LlmProfileService.java:200-206`）。
- **建议修法**：收养时只补 `isDefault` 标记，不覆盖业务字段；或对 `默认配置` 这个保留名在档案写接口上做校验（同 [MINOR] 5）。

---

### [MINOR] 7. 潜在泄密面：4 个持有**明文 key** 的 `record` 没有遮蔽 `toString()`

- **位置**：`LlmProviderService.java:362-366`（`RuntimeProvider`）、`LlmProfileService.java:533-542`（`RuntimeProfile`）、`LlmConfigService.java:213-219`（`RuntimeConfig`）、`:236-240`（`ImageRuntime`）
- **现象**：这四个 record 都带 `String apiKey`（明文），record 自动生成的 `toString()` 会把明文打出来。
- **为什么知道的**
  1. 读这四个 record 的字段列表；
  2. 用 `grep -rn "log\." src/main/java | grep -i "runtime\|provider\|profile"` 逐条核对全部日志语句，**当前没有任何调用点打印它们**（最近的几条见 `LlmProfileService.java:147`、`LlmProviderService.java:260`、`LlmProfileBackfillRunner.java:110`），所以这不是现存泄露；
  3. 但 `LlmProviderBackfillRunner.java:183-187` 的注释明确把「record 自动 `toString()` 会把明文密钥写进日志」列为已知风险，并据此把归组暂存类写成普通类——同一个风险模型在这四个 record 上没有落地。
- **影响**：任何一次 `log.debug("{}", runtime)`、或把 `RuntimeProfile` 塞进异常消息/断言消息，明文 key 就会进日志（掩码规则会被绕过）。
- **建议修法**：这四个 record 显式 `override toString()`（输出 `••••`+末 4 位或 `hasKey=true/false`）；或让 record 不持有明文，改为持有「已解密后的凭据对象」并在该对象上遮蔽。

---

### [MINOR] 8. 掩码规则有两套，同一把 key 在两个页面显示不一致

- **位置**：`LlmProviderService.java:342-345`（`未配置` / 长度 ≥12 → `••••••••`+末 4 位 / 否则全掩码）vs `LlmConfigService.java:165`（档案路径**硬编码** `"••••••••••••"`）与 `:179-181`（`mask(String encrypted)` 收的是**密文**，返回恒定掩码）
- **现象**：供应商页显示 `••••••••klmn`，设置页对同一把 key 显示 `••••••••••••`；`mask()` 的参数名/语义（传密文、不看内容）容易误用。
- **为什么知道的**：读两处实现；集成用例 `LlmProviderApiIntegrationTests.java:134`、`:152` 与 `AgentApiIntegrationTests`（`cheap-key` 9 位 → 全掩码）分别钉住了两套规则的存在。
- **影响**：不泄密（更保守的一侧生效），但用户会以为「设置页没读到我的 key」，也是后续「统一掩码」改动的隐患点。
- **建议修法**：全部走 `LlmProviderService.maskApiKey(String plaintext)`，删掉 `LlmConfigService.mask`。

---

### [MINOR] 9. 回填复杂度：O(P×G) 线性归组 + 每组一次供应商全表读/逐行解密

- **位置**：`LlmProviderBackfillRunner.java:126-158`（对每个待回填档案线性扫已有分组）、`LlmProviderService.java:174-182`（`findByTriple` 每次调用 `mapper.findAll()` 并逐行解密）、`:282-290`（`uniqueName` 逐序号查库）
- **现象**：`P` 条待回填档案、`G` 个分组时约 `P×G/2` 次三元组比较；每个分组在 `attachOrCreateProvider`/`resolveDefaultProviderId` 里各做一次「全表读 + 逐行 AES-GCM 解密」（`G×V` 次解密）。
- **为什么知道的**：读三处实现；归组键必须用**明文**比较这一点已在 `LlmProviderBackfillRunner.java:26-29`、`LlmProviderService.java:24-29` 说明（AES-GCM 随机 IV ⇒ 密文不可用于归组），所以「全表解密」是设计代价而非疏漏——但「每组重复全表解密」不是必需的。
- **影响**：当前量级（smoke 库 16 条 / 2 组）毫秒级；按 `P=1000、G=100` 估算约 5 万次比较 + 上万次解密，仍是可接受范围，但写法上是平方级，属可维护性债。
- **建议修法**：在 `backfill()` 内维护一个「(provider, baseUrl, 明文 key) → providerId」的内存映射（明文只在栈上、不入日志），把 `findByTriple` 的全表扫描从每组一次降为整轮回填一次。

---

### [MINOR] 10. 日志噪声：一条脏档案会让**每次建链**都打一条带栈的 warn

- **位置**：`LlmProfileService.java:147-148`
- **现象**：`addIfUsable` 对解析失败的档案 `log.warn(..., exception)`（带完整堆栈）。而 `failoverChain` 每次装配智能体都会跑（`AgentFactory.java:136`、`:170`、`ScheduledAgentFactory.java:102-124`、`PipelineExecutor.java:54`、`CoordinatorExecutor.java:80`…）。
- **为什么知道的**：读 `addIfUsable` 与 `failoverChain` 的全部调用点；确认该 warn 位于「每条链、每条候选」的路径上，而非一次性的启动路径。
- **影响**：一条永久脏行 + 高频请求 ⇒ 日志刷屏、真正的告警被淹没；同时每条 warn 都要构造一次异常栈（有 CPU/GC 代价）。
- **建议修法**：首次（按 id 去重）打一次 warn 带栈，之后降为 `debug` 且不带栈；或把该档案的失败结果缓存一个短 TTL。

---

### [MINOR] 11. 改造目标只达成一半：密钥副本数量没有下降

- **位置**：`LlmProfileSeeder.java:161-164`（种子档案仍**逐字复制**来源档案的 `provider / baseUrl / apiKeyEncrypted` 三列）、`LlmProfileService.java:481-484`（设置页写透也继续写这三列）
- **现象**：新口径的档案运行时不再读这三列，但它们**仍被写入**。库里同一把 key 的密文副本数没有减少。
- **为什么知道的**：读上述两处；只读 SQL 实测 smoke 库 `SELECT COUNT(*), SUM(API_KEY_ENCRYPTED IS NOT NULL), COUNT(DISTINCT API_KEY_ENCRYPTED) FROM LLM_PROFILE` → `16 / 16 / 4`，即 16 条档案全部持有密钥密文列（其中 12 条是种子从同一来源复制出来的同一份密文）。
- **影响**：这是**有意取舍**（契约 `docs/dev/llm-provider-refactor.md:66-68` 说保留三列是为了回滚），且副本是密文、需要 `APP_SECRET_KEY` 才能解；但「同一份密钥在库里存了 8 份」这个被引为改造动机的现象（`LlmProvider.java:15-19`）在库里依然成立，只是不再被读。安全上属「攻击面未缩小」，运维上属「换 key 后留下旧 key 的密文快照」。
- **建议修法**：明确写进契约（现状只有「冻结为快照」一句，没说 `LlmProfileSeeder` 还会**新写**副本）；后续可考虑新口径档案不再写入这三列（`LlmProfileService.apply` 已经不写了，种子与写透是例外）。

---

## 2. 已核实为正常

每条附「怎么知道的」。

1. **权限与越权面**
   - `/api/llm-providers` 五个端点全部 ADMIN：`LlmProviderController.java:32` 类级 `@PreAuthorize("hasRole('ADMIN')")`；方法级安全确已开启：`auth/SecurityConfig.java:24` `@EnableMethodSecurity`；URL 级规则 `/api/**` 需认证（`:43-49`）。
   - 端到端实证：`LlmProviderApiIntegrationTests.java:242-259` 断言 VIEWER 读列表/详情 → 403、匿名 GET/POST → 401（surefire 报告 `target/surefire-reports/ink.icoding.wechat.article.LlmProviderApiIntegrationTests.txt`：4 例 0 失败）。
   - 越权路径检查：`LlmProviderController` 无任何按 id 绕过角色的分支；`usedModelCount` 只用于护栏。
2. **不泄密（响应面）**
   - 供应商视图只回 `hasApiKey` + 掩码（`LlmProviderService.java:328-334`、`:342-345`）；档案视图同理（`LlmProfileService.java:335-362`）。
   - 实证：`LlmProviderApiIntegrationTests.java:138-149` 断言创建/列表响应**不含明文也不含密文**，`:141-144` 直接从库读密文并用真实 `CryptoService` 解回明文比对；`:164-165` 断言更新后密文未变；`AgentApiIntegrationTests.java:275-284` 断言档案列表不含密文。这些用例 12:36 全绿。
3. **不泄密（审计面）**：`audit/AuditInterceptor.java:26-33` 只写 method / URI / status / IP，**不记录请求体**，因此 `POST /api/llm-providers` 的 `apiKey` 不会进审计表。
4. **不泄密（异常面）**：`common/GlobalExceptionHandler.java:15-18` 只回 `BusinessException` 的自定义文案（文案里从不含 key：`LlmProviderService.java:297-311`、`LlmProfileService.java:261`）；`:37-41` 兜底返回固定文案，不透传异常消息；`CryptoService.java:57` 的解密失败消息也不含密文。
5. **`@MapperScan` 约束被遵守**：`WechatArticleBotApplication.java:9` `@MapperScan("ink.icoding.wechat.article")` 会把该包下所有顶层接口注册成 mapper。我用脚本逐个检查主源码里 `^public interface` 的 21 个顶层接口，**全部** `extends SmartMapper`（`grep -rln "^public interface " | while ... grep -q "extends SmartMapper"` → 无输出即无非 mapper 接口），其中新增的 `LlmProviderMapper.java:18` 合规；本轮新增的其余四个顶层类型（`LlmProvider` / `LlmProviderService` / `LlmProviderController` / `LlmProviderBackfillRunner`）都是 `class`，不会被扫描。
6. **列声明一致性有护栏且覆盖新实体**：`common/EntityColumnDeclarationConsistencyTest.java:106-134` 自动扫描 `target/classes` 下所有 `PO` 子类（不是硬编码清单 ⇒ 新实体自动纳入），`:42-73` 断言同名字段声明逐字一致，`:77-88` 另钉 5 组历史冲突（含 `name`/`provider`/`modelName`）。实测库侧同向：`information_schema` 显示 `LLM_PROVIDER` 的 `NAME=varchar(255)`、`PROVIDER=varchar(50)`、`BASE_URL=varchar(500)`、`API_KEY_ENCRYPTED=text`，与 `LLM_PROFILE` 同名列完全一致（我对照查询过两张表）。
7. **回填幂等性与「只动空绑定行」**：`LlmProfileMapper.java:53-55` 的输入条件（`WHERE PROVIDER_ID IS NULL`）本身就是幂等载体；`:44-52` 的注释说明 `eq(null)` 走 `IS NULL`。实测 smoke 库：16 条档案 `PROVIDER_ID` 全非空、0 条悬空 ⇒ 第二次启动无事可做；`LlmProfileProviderColumnPersistenceTests.backfillConvergesSameTripleProfilesIntoOneProviderAndIsIdempotent`（6 例全绿）用 8 条「同一明文、8 个不同密文」的档案跑了两遍回填并断言绑定一字不变。
8. **Runner 顺序与「不阻塞启动」**：`grep @Order src/main/java` 实测 `LlmProfileMigrationRunner=20`、`LlmProviderBackfillRunner=21`、`LlmProfileSeeder=25`（还有 10/30/32/35/40/41 的既有 Runner），与契约 `docs/dev/llm-provider-refactor.md:193` 一致；三个 Runner 的 `run()` 都有 `try/catch` 兜底（`LlmProviderBackfillRunner.java:60-64`、`LlmProfileMigrationRunner.java:48-71`、`LlmProfileSeeder.java:104-118`），单组失败只 warn 不抛出 ⇒ 不会让应用起不来。顺序必要性另有断言：`LlmProfileProviderColumnPersistenceTests.java:277-303` 直接比较两个 `@Order` 值并断言种子档案 `providerId` 非空。
9. **悬空 `provider_id` 的处理（不抛、不回退旧列）**：`LlmProfileService.java:389-398` 对查不到的供应商返回 `RuntimeProfile(false, null, null, ...)`，`:536-541` 的 `available()` 因 provider/baseUrl/apiKey 为空而 false；不会回退到档案自身的过期端点（理由见 `:376-384`）。测试覆盖两处：`LlmProfileServiceTest.danglingProviderIdIsUnavailableAndNeverFallsBackToStaleColumns`、`viewFlagsADanglingProviderInsteadOfThrowing`。
10. **存量回退行仍然可用（升级窗口底线）**：`LlmProfileService.java:399-403` + 集成用例 `LlmProfileProviderColumnPersistenceTests.legacyRowsStillResolveTheirOwnEndpointAndKey`（真实 `CryptoService` 解出 `https://legacy-provider.test` / `sk-legacy-1234`，12:36 绿）。
11. **`available()` 的收紧（Lead 追加项）已落地**：`LlmProfileService.java:536-541` 同时要求 provider 与 baseUrl 非空，避免「有 key 但端点空」的脏行在 `AgentFactory.createModel` 的 `switch (provider)` 上炸（`AgentFactory.java:320-326`）；用例 `LlmProfileServiceTest.legacyRowWithKeyButBlankBaseUrlIsSkippedWithoutThrowing` 同时钉住「不可用」与「不抛」。
12. **测试健康（本轮重点）**
   - 数量自证：源码 `@Test` 计数 = **583**；`target/surefire-reports/*.txt`（69 个类）我逐文件聚合 = **Tests 583 / Failures 0 / Errors 0 / Skipped 0**，与 Lead 报的一致（我自己 `awk` 求和，不是转述）。
   - 无壳测试：全仓 `grep -rn "@Disabled|@Ignore|assumeTrue|@Test(enabled"` → **0 命中**；新增的 4 个测试类共 49 例（`LlmProfileProviderColumnPersistenceTests` 6、`LlmProviderApiIntegrationTests` 4、`LlmProfileServiceTest` 24、`LlmProviderServiceTest` 15），我逐个读过用例体：全部有实质断言（`hasSize(1)`/`doesNotContainNull()`/`isNotEqualTo`/`doesNotContain(明文)`/`verify(mapper, never())` 等），没有「只调一次不断言」的空壳。
   - 关键断言不是自证循环：`LlmProviderServiceTest.java:170-184` 用**真实** `CryptoService` 加密两次、先断言两个密文不同（把「随机 IV」这个前提本身钉住），再断言仍收敛到同一供应商；`LlmProfileProviderColumnPersistenceTests.java:218-225` 同款（8 次加密断言 8 个不同密文）。这类「前提断言 + 结论断言」是本轮测试里最有价值的部分。
   - mock 是否过度：`LlmProfileServiceTest.crypto()` 用了「明文=密文」的恒等替身，但**同一性质在 `LlmProviderServiceTest` 用真实实现钉住**（javadoc `:35-36` 说明了理由），属合理分工；`AgentSeederTest.profileService()` 是替身，但 `hasReachableKey` 的真实口径另有集成断言。未发现「用 mock 把待测性质从测试里删掉」的用例。
   - 隔离与清理：所有 15 个 `@SpringBootTest` 类都带 `@DirtiesContext(AFTER_CLASS)`（我逐文件 grep 类级注解确认），配合 `MySqlTestDatabaseInitializer.java:95-112` 在**每次上下文创建时 TRUNCATE 测试库全部表** + `:75-88` 的跨进程文件锁 ⇒ 类与类之间隔离成立、且并发跑两个 mvn 进程会被串行化。类内用例大多用 `@AfterEach` 按前缀删自己造的行（`LlmProviderApiIntegrationTests.java:57-61`、`LlmProfileProviderColumnPersistenceTests.java:87-95`），个别用例用 `try/finally`（`AgentApiIntegrationTests.java:687-696`，且注释说明了「先删档案再删供应商」的顺序原因）。唯一瑕疵：`LlmProviderApiIntegrationTests.viewerToken()`（`:78-94`）创建的 `SYS_USER` 行没有在 `@AfterEach` 清理，靠下一次上下文创建的 TRUNCATE 兜住——够用，但依赖外部机制。
   - **测试证据的新鲜度**：最新产品源码 mtime 11:42（`LlmProfileService.java`）< `target/classes` 12:09 < surefire 报告 12:36–12:38 ⇒ 这 583 例跑的是**当前源码**，没有「报告比代码旧」的问题。
13. **运行时实例与库的对应关系（环境核实）**
   - 18081 是 **Windows 原生 JVM**（不是 WSL/docker 进程）：WSL 内 `ss -ltnp` 看不到 18081；`netstat.exe -ano` 显示 PID 42216 `LISTENING 0.0.0.0:18081` 且有 3 条 `ESTABLISHED`（用户浏览器正在连）。
   - 只读 `GET http://127.0.0.1:18081/api/health` → `200`，body `{"success":true,"data":{"status":"UP"},...}`（这是我的唯一一次 API 调用，GET 只读）。
   - 它连的库：`information_schema.PROCESSLIST` 显示 `wechat-article-smoke` 有 10 条连接、来源 IP `172.18.0.1`（docker 网桥网关 = 宿主机），而 `wechat-article` 的 10 条来自 `172.18.0.2`（docker 里的 app 容器）⇒ 与「18081 连 smoke 库」一致。
   - 本轮改造在 smoke 库上的真实效果：2 个供应商（`nexus.bx9y.com.cn` / `api.anthropic.com`，都是回填的 host 命名）、16 条档案全部挂上 `provider_id`、0 条悬空；12 条 `deepseek-flash … nemotron-3-ultra-free` 是种子档案、全部继承 `provider_id=1` ⇒ **「回填早于种子、种子继承绑定」这条硬要求在生产形态的库上确实成立**（不是只靠测试）。
   - `wechat-article`（dev）库：`LLM_PROFILE` 0 行、**没有 `LLM_PROVIDER` 表**、`LLM_PROFILE` 也**没有 `PROVIDER_ID` 列** ⇒ docker 里那个 app 容器跑的是改造前的 jar，未受本轮影响，也没有被写脏。
14. **`info/` 与既有约定**：`CREATED_AT`/`UPDATED_AT` 在本库**所有**表上都是 `varchar(255)`（我查了 `wechat-article` 与 `wechat-article-smoke` 两张库里所有含这两列的表，逐一列出共 50+ 行，全部 varchar(255)），`LlmProvider` 与它们一致 ⇒ 新实体**没有引入**新的类型漂移；smoke 库里 `LLM_PROVIDER.CREATED_AT` 的实际值是 `2026-11-10 11:03:01.363086`，往返可用。

---

## 3. 未验证项

明确列出我没有验证的东西，不要当成已通过。

1. **没有跑任何 `mvn`**（硬约束）。583 例 / 0 失败来自我**读** `target/surefire-reports/*.txt` 原文后自己聚合，不是我复现的运行；我能证明的只有「报告时间戳晚于源码 mtime」（§2.12），无法证明「报告与当前源码逐位对应」。
2. **没有真实存量库的 `ALTER TABLE` 实证**。dev 库当前既无 `LLM_PROVIDER` 表也无 `PROVIDER_ID` 列（information_schema 证据），所以「smart-mybatis 对真实存量库补列、且不对同名已放宽的列误发 `MODIFY COLUMN`」这条只有 smoke 库（新造）与测试库（新建表）的证据。`LlmProfileProviderColumnPersistenceTests.java:47-48` 自己也声明了这个边界。
3. **[MAJOR] 1 的触发条件未实测**：smoke 库 `LLM_CONFIG.BASE_URL=https://settings.accept.test` 与任何档案组都不一致、两个供应商都是 host 命名 ⇒ `resolveProviderId` 的 `matchesConfig` 分支没有被真跑过。该条结论是**读代码 + 契约对照**得出的推理，不是观测。
4. **[MAJOR] 3 的后果未实测**：我没有构造「解不开的密文」去验证 500（需要写库，被约束禁止），也没有在 18081 上触发它。异常传播链是读代码得出的。
5. **前端不在我的范围**：`webui/` 对 `providerName`/`providerEnabled` 的消费、`webui/scripts/check-profile-chain-parity.mjs` 的实际自检结果我都没有运行（脚本要 node）；4 跳调用序列的**字面**实参名我在后端侧核对过（`LlmProfileService.java:119-122` 与契约 `:96-98` 一致），但脚本的正则是否真的匹配、A 段自检是否绿，未验证。
6. **并发未实测**：[MINOR] 5 的多实例并发场景我没有构造（当前两台实例连不同库，无法自然复现）；「两个管理员同时建同名供应商」的竞态是推理。
7. **18081 的供应商/档案页面未做端到端确认**：`GET /api/llm-providers` 需要 ADMIN token，而获取 token 必须 `POST /api/auth/login`（写请求，被约束禁止），所以「用户浏览器里看到的供应商页渲染是否与后端契约一致」我**没有验证**，只验证了 `/api/health` 与库侧数据。
8. **[MINOR] 11 的性能口径未压测**：回填在 `P=1000/G=100` 下的耗时是估算，没有实测数据。
9. 未验证 `APP_SECRET_KEY` 轮换的真实运维流程（是否会重建 `llm_config`/`llm_profile`/`llm_provider` 三处的密文），因此 [MAJOR] 3 的「多久会遇到」无法给出频率判断。
10. 未检查 `docs/dev/llm-provider-refactor-verification.md`（独立验证报告）里的 6 组反证是否与我的结论一致——我的结论只基于代码、库与测试报告原文，未把它当作依据。

---

## 4. 建议的处理顺序（给 Lead 的参考）

1. 先定 [MAJOR] 1 与 [MAJOR] 3 的修法（都在 `LlmProviderService.resolveDefaultProviderId` / `AgentFactory.createModel` 附近，改动面小、可被现有测试覆盖），因为它们的共同形态是「静默不可用 / 降级失效」，最不容易在验收中发现。
2. 再定 [MAJOR] 2 的产品口径（是「设置页专属那一行」还是「被引用就另建」），这需要和前端下拉的行为一起决定，可能要回到契约层。
3. [MINOR] 4/7/8/10 属于同一批低风险清理，可以一次改完并各补一条断言（避免二次漂移）。
4. [MINOR] 5/6/11 涉及保留名与列语义，建议本轮只补「唯一索引 + 保留名校验」这类防御，不改运行时口径。
