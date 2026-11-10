# 模型档案 → 供应商 + 模型：独立验证报告

> 验证者：verifier（独立于 task-1 后端 / task-2 前端实现者）
> 验证对象：`docs/dev/llm-provider-refactor.md`（契约冻结稿）§3–§7
> 验证时间：2026-11-10
> 原则：**不采信两位实现者的「已完成 / 全绿」结论**，全部自行执行、自行取证；凡未真跑的项在【未验证项】里明示。

---

## 【结论】

**有条件通过。**

契约 §3–§7 的每一条实质条款都在真实代码 / 真实 MySQL / 真实 HTTP 响应上逐字落实，583 例测试与前端 `check`+`build` 均由我独立复跑通过，六条反证中 a–f 全部按预期成立（含一条红绿验证）。

「有条件」的保留项，都是**范围与卫生问题，不是能力缺陷**：

1. **CRITICAL（须 Lead 处置）**：`.env.bak-20261110-101751` 含真实 MySQL root 口令、ADMIN 口令、`APP_SECRET_KEY`，且**未被 `.gitignore` 覆盖**（`.gitignore` 只 ignore 了 `.env` 本身）。当前处于 `?? ` 未跟踪状态，一次 `git add -A` 即入库。
2. **MAJOR（须 Lead 裁决）**：`compose.yaml` 有 7 行改动（绑定地址改 `127.0.0.1`、新增 `extra_hosts` 且**硬编码网关 IP `172.21.192.1`**），与本轮契约**毫无关系**，也不在 task-1（`src/**`）/ task-2（`webui/**`）任何写范围内——属越界改动，混在本轮 diff 里。
3. **MINOR**：README 把回填口径描述成「端点相同的档案自动收敛」，实际三元组还含 `provider` 与**解密后的明文 key**；表述会误导运维以为只按端点归组。

---

## 【逐条核对表】

判定：✅ 完全符合 / ⚠️ 符合但有保留 / ❌ 不符

### §3 数据模型

| 契约条款 | 判定 | 证据 |
|---|---|---|
| §3.1 `LlmProvider` 在包 `ink.icoding.wechat.article.agent` | ✅ | `LlmProvider.java:1`；`:31` `@TableName("llm_provider")` |
| §3.1 `name` 声明 `@TableField(length = 255)`，与既有 5 处同名声明的**逐字相同** | ✅ | 独立复算（源码级，非读测试）：`name` 在 `Skill.java:28`、`ScheduleTask.java:22`、`AgentDefinition.java:35`、`LlmProvider.java:44`、`LlmProfile.java:32`、`WechatAccount.java:22` **六处全部为 `@TableField(length = 255)`**，无第二种写法 |
| §3.1 `provider` 声明 50，与 `LlmConfig`/`RenderConfig`/`LlmProfile` 四处逐字相同 | ✅ | 四处全部 `@TableField(length = 50)`：`LlmConfig.java:25`、`RenderConfig.java:28`、`LlmProvider.java:53`、`LlmProfile.java:60` |
| §3.1 `baseUrl` 声明 500，同宽 | ✅ | 四处全部 `@TableField(length = 500)`：`LlmConfig.java:27`、`RenderConfig.java:30`、`LlmProvider.java:56`、`LlmProfile.java:63` |
| §3.1 `apiKeyEncrypted` 声明 65535，同宽 | ✅ | 三处全部 `@TableField(length = 65535)`：`LlmConfig.java:36`、`LlmProvider.java:59`、`LlmProfile.java:96` |
| §3.1 `enabled` 不加注解，与既有同名一致 | ✅ | 三处（`LlmConfig`/`LlmProfile`/`LlmProvider`）均无 `@TableField` |
| §3.1 `createdBy` / `createdAt` / `updatedAt` | ✅ | `LlmProvider.java:70-72` |
| §3.2 新增 `providerId`（列 `PROVIDER_ID`）**可空** | ✅ | `LlmProfile.java` 新增 `private Long providerId;`（无 `@TableField`，Long→bigint）；真实库实测 `PROVIDER_ID bigint null=YES`（见反证 f） |
| §3.2 旧三列 `provider`/`baseUrl`/`apiKeyEncrypted` **不删、不清空** | ✅ | `git diff LlmProfile.java` 显示三列字段全部保留，仅新增 javadoc 说明「冻结为存量快照」；无任何删除行 |
| §3.1 列声明一致性能被既有护栏覆盖 | ✅ | `EntityColumnDeclarationConsistencyTest` 2/2 通过；其 `previouslyCollidingFieldsStayAligned()` 已把 `name`/`provider` 钉死（`EntityColumnDeclarationConsistencyTest.java` 内 `assertDeclaration("name","len=255,colType=")` 等） |

### §4 运行时口径

| 契约条款 | 判定 | 证据 |
|---|---|---|
| `runtime()` 三分支：供应商在 / 悬空 / 存量回退 | ✅ | `LlmProfileService.java:385-404`：`providerId != null` → `runtimeById`；返回 `null`（悬空）→ `new RuntimeProfile(false, null, null, ...)`（`:392`）；`providerId == null` → 读档案自身旧列（`:399-403`） |
| 悬空 ⇒ `enabled=false` ⇒ `available()==false` | ✅ | `:391-393` 直接构造 `enabled=false`；反证 e 实测 `available=false` |
| 供应商在 ⇒ provider/baseUrl 取自供应商，`enabled = 档案.enabled && 供应商.enabled` | ✅ | `:395` `profileEnabled && provider.enabled()`，`provider.provider()`、`provider.baseUrl()` |
| 存量回退 ⇒ provider/baseUrl/apiKey 取档案自身旧列，`enabled = 档案.enabled` | ✅ | `:399-403`；反证 a/b 真库实测 |
| `RuntimeProfile` 记录形状**逐字未变** | ✅ | 与 `git show HEAD:` 版本对比，字段列表与顺序完全一致（`boolean enabled, String provider, String baseUrl, String modelName, String apiKey, BigDecimal temperature, Integer maxTokens, boolean isDefault, String imageModelName`） |
| `AgentFactory` / `LlmConfigService` **调用点零改动** | ✅ | `git diff --stat -- AgentFactory.java LlmConfigService.java` 输出为**空**（[end]），两个文件未被触碰 |
| `available()` 收紧为**四条件** | ✅ | `:536-541`：`enabled && provider 非空 && baseUrl 非空 && apiKey 非空`；HEAD 版本仅 `enabled && apiKey 非空`（已对照 `git show` 行 279-281 证实是收紧而非改写形状） |
| 4 跳调用序列逐字不变 | ✅ | `:119-122` `bound` → `defaultProfile()` → `fallbackProfile()` → `profile`；且反证 d 证明该序列被脚本看守 |

### §5 接口形状

| 契约条款 | 判定 | 证据 |
|---|---|---|
| 五个端点齐备（GET 列表 / GET 单条 / POST / PUT / DELETE） | ✅ | `LlmProviderController.java:42,47,52,58,65` |
| 路径 `/api/llm-providers` + `hasRole('ADMIN')` | ✅ | `:31-32`（类级 `@PreAuthorize`） |
| 权限矩阵：ADMIN 通过、VIEWER 403、匿名 401 | ✅ | 真实 HTTP 实测：VIEWER GET/POST `/api/llm-providers` 与 `/api/llm-profiles` 全部 **403**；匿名 GET **401** |
| `ProviderRequest` 形状 | ✅ | `LlmProviderService.java:347-349`，字段名/顺序/注解与契约一致 |
| `ProviderView` 字段名与顺序 | ✅ | `:351-352`：`Long id, String name, String provider, String baseUrl, boolean hasApiKey, String apiKeyMasked, Boolean enabled, int modelCount, LocalDateTime updatedAt` — 逐字一致 |
| `RuntimeProvider` 形状 | ✅ | `:362-366` |
| `ProfileRequest` 移除 provider/baseUrl/apiKey/clearApiKey，增 providerId | ✅ | `LlmProfileService.java:503-506`：仅 `name, providerId, modelName, enabled, temperature, maxTokens, imageModelName` |
| `ProfileView` 字段名与顺序（增 providerId/providerName/providerEnabled，去 provider/baseUrl） | ✅ | `:515-519` 与契约 §5.2「之后」逐字一致 |
| 规则1 `name` 去空白后唯一，重复 → `供应商名称已存在：X` | ✅ | `:314-318`；真实 HTTP 实测 `HTTP=400 {"message":"供应商名称已存在：nexus-a"}` |
| 规则2 `provider` ∈ 三值 | ✅ | `:41-42` + `:298-300`；实测 `HTTP=400 "不支持的 LLM 服务类型：GROQ"` |
| 规则3 `baseUrl` 归一化（去尾斜杠 + 剥四种后缀 + 必须 http/https） | ✅ | `LlmProfileService.normalizeBaseUrl`（`:287-303`）；真实 HTTP 实测六种写法**全部**归一成 `https://norm.test`（含 `/v1/chat/completions`、`/v1/responses`、`/v1/messages`、`/v1`、尾斜杠） |
| 规则4 `apiKey` 非空即加密覆盖；`clearApiKey` 清空；启用中必须有 key | ✅ | `:305-311`；实测「启用但无 key」→ `HTTP=400 "启用供应商前必须配置 API Key"` |
| 规则4「`apiKey` 留空表示不修改」 | ✅ | `:306` 仅当非空才覆盖；反证 a 中 PUT 停用供应商时未传 apiKey，实测 `hasApiKey=True`（密钥保留） |
| 规则5 掩码：`未配置` / 长度≥12 → `••••••••`+末4位 / 否则全掩码 | ✅ | `:342-345`；实测长 key → `••••••••LEqE`，9 位 key → `••••••••••••`（全掩码） |
| 规则5 **任何响应不得回传明文或密文** | ✅ | 见反证 c |
| 规则6 DELETE 有档案时拒绝，消息含剩余模型数 | ✅ | `:126-135`；实测 `HTTP=400 "供应商「nexus-a」下还有 15 个模型档案..."`；空供应商 DELETE 实测 `HTTP=200` |
| 规则7 `modelCount` = `provider_id = id` 的行数 | ✅ | `:138-141`；实测供应商 1 的 `modelCount` 与实际 15 条一致 |
| §5.2 悬空取值：`providerName=null` / `providerEnabled=false` / `hasApiKey=false` | ✅ | 真实 HTTP 实测悬空档案：`"providerName":null,"providerEnabled":false,"hasApiKey":false,"apiKeyMasked":"未配置"`，HTTP=200 **不抛异常**（列表与单条 GET 均 200） |
| §5.2 存量回退取值：`providerName=null` / `providerEnabled=` **`true`** / `hasApiKey`=本行密文非空 | ✅ | `LlmProfileService.java:339` 缺省 `boolean providerEnabled = true`，仅在 `providerId != null` 分支才可能改 false；`LlmProfileServiceTest.java:490-502` 专项用例断言 `true` |
| §5.2 正常行取值 | ✅ | 实测：`providerName="nexus-a"`, `providerEnabled=True`, `hasApiKey=True` |
| §5.2 `providerId` 必填；指向不存在 → `供应商不存在` | ✅ | 实测：缺 providerId → `400 "providerId 不得为 null"`；`providerId=99999` → `400 "供应商不存在"` |
| §5.3 `syncDefaultFromConfig` 用三元组精确匹配 → 复用/创建 `默认供应商` | ✅ | `LlmProfileService.java:462-463` → `LlmProviderService.resolveDefaultProviderId`（`:206-233`），内部 `findByTriple` 明文比较 |
| §5.3 保留「找不到默认档案时**新建**而非劫持第一条用户档案」 | ✅ | `:464` 用 `mapper.findDefault()`（非 `defaultProfile()`）；javadoc `:431-447` 说明理由；三条对照用例在 `LlmProfileServiceTest.java:275/304/324` |
| §5.4 回填 `@Order(21)`，早于 Seeder(25) | ✅ | `LlmProviderBackfillRunner.java:41` `@Order(21)`；`LlmProfileSeeder.java:32` `@Order(25)`；`LlmProfileMigrationRunner.java:31` `@Order(20)`；另有断言用例 `orderOf(...).isLessThan(...)` |

### §5.4 回填细节

| 契约条款 | 判定 | 证据 |
|---|---|---|
| 归组键第三段是**解密后的明文 key**，不是密文 | ✅ | `LlmProviderBackfillRunner.java:137-138` 解密后放入 `KeyGroup.apiKeyPlain`；`:204-208` `matches()` 比明文；`LlmProviderService.findByTriple`（`:174-182`）同样比 `plaintextKeyOf` |
| **实测证明**：同明文 8 次加密得 8 个不同密文，仍收敛到 1 个供应商 | ✅ | 反证 b（详见下节）：8 个 MD5 全不相同 → 归入供应商 id=13；2 条不同 key → 供应商 id=14 |
| 幂等：只动 `provider_id IS NULL`，第二次启动无事可做 | ✅ | `:68` `findWithoutProvider()`；`LlmProfileMapper.java:53` `eq(null)`→`IS NULL`。**真库实测**：第二次启动**无任何「供应商回填」日志行**，供应商数仍为 3，绑定一字不变 |
| 供应商命名：与 `llm_config` 一致 ⇒ `默认供应商`；否则取 host；重名追加 ` (2)` | ✅ | `:167-178` + `LlmProviderService.nameFromBaseUrl`（`:272-280`）+ `uniqueName`（`:282-290`）。真库实测：新建出 `backfill.test` 与 `backfill.test (2)` |
| `baseUrl` 空 ⇒ 跳过（造不出供应商） | ✅ | `:129-132` |
| 只 `log.warn` 不抛，不阻塞启动 | ✅ | `:60-65` 整体 try/catch；`:104-108` 单组失败只跳过 |
| 归组用的明文不落库/不进日志/不进异常 | ✅ | `KeyGroup` **故意不是 record**（`:188`），避免 `toString()` 带明文；反证 c 全量日志扫描 0 命中 |
| `MigrationRunner` / `Seeder` 带上 `providerId` | ✅ | `LlmProfileMigrationRunner.java` `profile.setProviderId(resolveProviderId(config))`；`LlmProfileSeeder.java` `profile.setProviderId(source.getProviderId())` |

### §6 前端

| 契约条款 | 判定 | 证据 |
|---|---|---|
| **模型表单不得再有 Base URL 与 API Key 输入框** | ✅ | `ModelProfilesView.vue:447-480`（模型 modal 全文）只有：档案名称、所属供应商(select)、上游模型名称、图片模型名称、Temperature、最大输出 Token、启用。`:454-460` 是**只读**「所属供应商」区块（`<p>` 文本，无 `<input>`）。**Base URL/API Key 输入框只存在于供应商 modal**（`:435-437`） |
| `profilePayload()` 不含 baseUrl/apiKey/clearApiKey | ✅ | `:239-244` 仅 7 个字段 |
| 供应商行字段：名称·协议标签·Base URL·API Key 掩码·N 个模型·启用开关·[编辑][删除] | ✅ | `:351-368`（titles/protocolLabel/modelCount/switch/编辑/删除）+ `:371-374`（Base URL、API Key 掩码） |
| 展开是浏览态，编辑走 modal | ✅ | `:382-421` 展开区只读渲染 + 「新增模型」按钮；编辑按钮 `:412` 开 modal |
| 供应商删除按钮 `modelCount > 0` 时禁用并给 title | ✅ | `:365-367` `:disabled="group.modelCount > 0"` + `:title` 含剩余数 |
| 下挂模型按 `id` 升序 | ✅ | `byIdAsc`，`:93/:94` 排序 |
| 默认/兜底徽标、启停、删除交互保持现状 | ✅ | `:388-389` 徽标、`:410-413` 操作按钮 |
| 悬空行渲染成管理员告警态且**不崩** | ✅ | `:350/355/378-380` `kind==='dangling'` 分支 + `role="alert"` 文案 |
| 存量行有独立分组与说明 | ✅ | `:356/:375-377` `kind==='legacy'` |
| `usable()` 增加 `providerEnabled !== false`（缺省视为 true） | ✅ | `webui/src/utils/profiles.js` `const usable = profile => Boolean(profile && profile.enabled && profile.hasApiKey && profile.providerEnabled !== false)` |
| `P()` 工厂补 `providerEnabled: true` | ✅ | `check-profile-chain-parity.mjs` diff |
| 新增两条行为用例（供应商停用 / 缺 key） | ✅ | diff 中 label「供应商被停用 ⇒ 该档案不在链上（key 仍在也不例外）」「供应商缺 key（hasApiKey=false）⇒ 该档案不在链上」；`npm run check` 报「15 个行为用例」 |
| **A 段结构自检未被削弱** | ✅ | `check-profile-chain-parity.mjs:43-64` A1（`findDefault()` 回落 `findFirst()`）+ A2（4 跳实参名正则）**两块都完整保留**；且反证 d 证明其仍能抓跳序变化 |
| `AgentsView` 下拉按供应商分组 | ✅ | `optgroup` 分组（`profileGroups` computed，组名缺省「未归属供应商」），选项值仍是档案 id（绑定语义不变） |
| `SettingsView` 文案按新结构修正 | ✅ | 说明改为「默认档案与它的默认供应商…」，并保留 Base URL 输入（符合 §8：设置页作为「默认档案+默认供应商」快捷入口） |

### §7 验收标准

| 条款 | 判定 | 证据 |
|---|---|---|
| 1. `./.mvn/mvn-local.sh -o test` 全绿 | ✅ | 我独立复跑：`EXIT=0`、`BUILD SUCCESS`、`run=583 fail=0 err=0 skip=0`（自跑前已 `rm -rf target/surefire-reports`） |
| 2. `npm run check` + `npm run build` | ✅ | `check`：`✓ 共享工具导入自检通过（10 个导出符号）` + `✓ 档案链一致性自检通过（后端 4 跳结构 + 15 个行为用例）`；`build`：`✓ built in 612ms` |
| 3. 真库证明 `llm_provider` 表与 `PROVIDER_ID` 列真实建成 | ✅ | 见反证 f |
| 3. 同一供应商下 3 条档案共享一份密钥 | ✅ | 真实 HTTP：3 条档案 `hasApiKey=True masked=••••••••LEqE`（同一供应商） |
| 3. 停用供应商后 `failoverChain` 不再返回其模型 | ✅ | 见反证 a |
| 4. 同三元组收敛为 1 个供应商、key 不同分成两个、只跑一次 | ✅ | 见反证 b |
| 5. `AgentApiIntegrationTests` 继续通过 | ✅ | 该测试类在 583 例内且无失败；3 个新增测试类也确证执行（`LlmProviderApiIntegrationTests` 4 例、`LlmProfileProviderColumnPersistenceTests` 6 例、`LlmProviderServiceTest` 15 例） |
| 6. 任何接口响应无明文/密文 key | ✅ | 见反证 c |
| 7. `EntityColumnDeclarationConsistencyTest` 通过 | ✅ | `Tests run: 2, Failures: 0, Errors: 0` |

---

## 【反证记录】

### a. 停用供应商 ⇒ 其模型真的从链上消失

**怎么做的**：不用读代码推断。我起了**真实 Spring Boot 实例**（`target/classes` + 离线 classpath，端口 18080），连一个**独立新建的空库** `watb_verify`（不动 Lead 的真库），再写一个**位于源码树之外**的临时探针类（`/tmp/watbprobe`，只读调用 `LlmProfileService`）打印真实 `failoverChain(null)`。

**看到了什么**（停用前 → 停用后）：

```
停用前：@@ CHAIN size=15 ids=[1,6,2,3,4,5,7,8,9,10,11,12,13,14,15,]
停用供应商1（PUT enabled=false）后：
        @@ PROF 1 ... rt.enabled=false rt.hasKey=true  available=false
        @@ PROF 6 ... rt.enabled=false rt.hasKey=true  available=false
        @@ CHAIN size=0 ids=[]
        档案接口：providerEnabled=False, hasApiKey=True（key 仍在，但已不可用）
重新启用后：@@ CHAIN size=15（恢复）
```

**说明什么**：`providerEnabled` 是**独立于 key** 的判据——key 保留、档案 `enabled=true`，仅供应商停用就使**整组 15 条**模型的 `available()` 变 false 并退出链。且开关**可逆**（重新启用后 15 条全回来）。这是实测行为，不是推断。

### b. 同三元组收敛（明文 key 归组，而非密文）

**怎么做的**：先通过真实 HTTP 创建 8 个供应商，每次都用**同一个明文 key**（应用各自独立加密）；再从库里取出这 8 份密文，分别塞进 8 条 `provider_id IS NULL` 的存量档案；另 2 条用**不同明文 key** 的密文；然后跑真实启动回填。

**看到了什么**：

```
=== 8 份密文的 MD5（同一明文加密 8 次）===
id=4  MD5=a7fa64b52afc524ba161301570391100
id=5  MD5=9d8ed658f18930c6c5e00eaae64af4e3
id=6  MD5=86d4af905ad62b347a3c17ae4d742ca7
id=7  MD5=4478b39f72d2fb501bd49fed5f3eb278
id=8  MD5=8468f88f8339822ade209b3643c620e4
id=9  MD5=98299b09302bb585a54aba75de809896
id=10 MD5=dc43a0bb4ee2b98a7ea2f695c9cb5b45
id=11 MD5=f9070c9f4e3a6b5e97ed4ae3facdb81e
=> 不同密文数 = 8 / 8

=== 回填日志 ===
存量模型档案供应商回填完成：10 条档案归入 2 个供应商（跳过 0 组），本次看到 10 条待回填档案

=== 结果 ===
8 条同 key（密文各不同）→ provider_id 全部 = 13（供应商名 backfill.test）  供应商个数 = 1
2 条不同 key            → provider_id 全部 = 14（供应商名 backfill.test (2)）供应商个数 = 1
两组交集 = set()         命名规则 = baseUrl 的 host + 重名追加 " (2)"
provider_id IS NULL 的行数 = 0

=== 幂等（第二次启动）===
第二次启动无任何「供应商回填」日志行；供应商总数仍为 3；10 条档案绑定一字不变
```

**说明什么**：①归组键确实是**明文**——若按密文归组，8 份不同密文会分裂成 8 个供应商；实测收敛为 1 个。②同一个 baseUrl 但 key 不同确实分成**两个**供应商（13 vs 14），未误合并。③命名规则与幂等性均成立。

### c. 不泄密

**怎么做的**：三条独立通道全扫——①真实 HTTP 响应（含错误响应）；②审计日志表 `AUDIT_LOG.detail`；③应用日志文件。

**看到了什么**：

```
GET /api/llm-providers      HTTP=200  明文=False 密文键名(apiKeyEncrypted)=False
GET /api/llm-providers/1    HTTP=200  明文=False 密文键名=False
GET /api/llm-profiles       HTTP=200  明文=False 密文键名=False
GET /api/llm-profiles/1     HTTP=200  明文=False 密文键名=False
GET /api/settings/llm       HTTP=200  明文=False 密文键名=False
GET /api/audit              HTTP=200  明文=False 密文键名=False
POST 非法协议   HTTP=400 明文=False  BODY={"message":"不支持的 LLM 服务类型：BAD_PROTO"}
DELETE 受护栏   HTTP=400 明文=False
POST 档案指向不存在供应商 HTTP=400 明文=False  BODY={"message":"供应商不存在"}
GET 无 token    HTTP=401 明文=False

=== AUDIT_LOG（拦截器记录了所有 POST/PUT/DELETE，共 8 条）===
id=1 POST   LLM-PROVIDERS  detail=HTTP 200   明文命中=False
id=2 POST   LLM-PROVIDERS  detail=HTTP 200   明文命中=False
id=3 POST   LLM-PROFILES   detail=HTTP 200   ...
id=6 PUT    LLM-PROVIDERS  detail=HTTP 200
id=8 DELETE LLM-PROVIDERS  detail=HTTP 200
detail 含明文 key 的行数: 0
detail LIKE '%apiKey%' = 0 ；'%API_KEY%' = 0 ；'%sk-%' = 0

=== 应用日志（含我故意制造的 AEAD 解密失败堆栈）===
watb-verify-app.log  明文命中=0  疑似Base64长串=0
watb-probe.log       明文命中=0  疑似Base64长串=0
watb-probe2/3/4.log  明文命中=0  疑似Base64长串=0
```

**说明什么**：审计拦截器只记 `action` / 资源标识 / `HTTP 200`，**不记请求体**，因此供应商与档案的 POST/PUT **没有把 `apiKey` 写进审计**。`CryptoService` 的解密失败异常消息（`敏感信息解密失败，请检查 APP_SECRET_KEY`）与底层 `AEADBadTagException` 堆栈里**都不含密文或明文**——这一点我特意用「错误的 APP_SECRET_KEY」触发了一批真实解密失败来取证，而不是只看正常路径。

> 注：我最初一次探针运行遗漏了 `APP_SECRET_KEY`，导致 15 条档案全部 `AEADBadTagException`。这**不是产品缺陷**（是我临时库的环境漏配），但它意外提供了一个高价值证据：**解密失败路径也不泄密**。补齐密钥后 0 异常、链恢复 15 条。

### d. 一致性脚本真的能抓后端跳序变化（红绿验证）

**怎么做的**：临时把 `LlmProfileService.failoverChain` 里的 `defaultProfile()` 与 `fallbackProfile()` 对调一行，跑 `node webui/scripts/check-profile-chain-parity.mjs`，然后**立即原样还原**。

**红（改动后）**：

```
✗ 前端档案链与后端 failoverChain 不一致：
  - 后端 failoverChain 的跳序变了：期望 [bound, defaultProfile, fallbackProfile, profile]，
    实际 [bound, fallbackProfile, defaultProfile, profile]。前端 profiles.js 必须同步调整。
[exit code: 1]
```

**绿（还原后）**：

```
✓ 档案链一致性自检通过（后端 4 跳结构 + 15 个行为用例）
SCRIPT_EXIT=0
```

**说明什么**：A 段结构自检**没有失效**——它能精确报出被对调的两个跳名，而不是笼统失败。这是「自检脚本本身有没有退化」的红绿证明。

**还原证据**（详见【临时改动复原声明】）：

```
改动前 sha256: b30eeb978b90636debf1296d7b0321c2cd43e53f7dfbbe6044a6617832ae1848
还原后 sha256: b30eeb978b90636debf1296d7b0321c2cd43e53f7dfbbe6044a6617832ae1848   ← 完全一致
diff 行数（改动前）= 426 ；diff 行数（还原后）= 426                          ← 未增加任何行
还原后 4 跳：119 bound / 120 defaultProfile() / 121 fallbackProfile() / 122 profile  ← 已归位
diff 中新增行里不存在 fallbackProfile/defaultProfile 跳序行                  ← 我的临时改动零残留
```

### e. 悬空 `provider_id`（供应商被删）不 NPE、不静默用旧端点

**怎么做的**：在库里造一条档案 `dangling-probe`，`provider_id=3` 指向一个供应商，且**该档案自己保留旧端点 `https://old-endpoint.example.com` 与旧密文**；然后**从库里直接删掉供应商**（绕过 API 护栏，模拟真实脏数据），再用真实上下文跑 `runtime()` 与 `failoverChain()`。

**看到了什么**：

```
@@ PROF 16 name=dangling-probe providerId=3
          rt.enabled=false  rt.provider=null  rt.baseUrl=null  rt.hasKey=false  available=false  err=-
NPE 计数 = 0                  ← 不抛 NullPointerException
日志中出现 "old-endpoint" 次数 = 0   ← 没有静默回退到旧端点
@@ CHAIN size=15 ids=[1,6,2,...,15]  ← 悬空档案被排除在链外

真实 HTTP：
GET /api/llm-profiles/16  ->  HTTP=200（不抛）
{"providerId":3,"providerName":null,"providerEnabled":false,"hasApiKey":false,
 "apiKeyMasked":"未配置","enabled":true,...}
```

**说明什么**：悬空时 `runtime()` 既不 NPE，也**没有**因为档案自己还留着旧端点就偷偷用上它——而是明确判成 `enabled=false` / `provider=null` / `baseUrl=null`（`LlmProfileService.java:391-393` 的显式分支）。这正是契约 §4 javadoc 里「宁可判不可用，也不替用户猜端点」的口径，且**降级形态是安全的**（不可用 ≠ 用错端点）。同时列表接口照常返回该坏数据，管理员看得见。

### f. `llm_provider` 表在真实 MySQL 中确实建成、列类型正确

**怎么做的**：直接查 `information_schema`（`pymysql`，root，`wechat-article-test` 库——真实 MySQL 实例）。另用一个**全新空库** `watb_verify` 让 smart-mybatis 自动建表，再用 `information_schema` 核对列类型。

**看到了什么**：

```
=== wechat-article-test（真实 MySQL）===
-- LLM_PROVIDER (9 列):
   ID               bigint         null=NO
   NAME             varchar(255)   null=YES
   PROVIDER         varchar(50)    null=YES
   BASE_URL         varchar(500)   null=YES
   API_KEY_ENCRYPTED text          null=YES
   ENABLED          tinyint(1)     null=YES
   CREATED_BY       bigint         null=YES
   CREATED_AT       varchar(255)   null=YES
   UPDATED_AT       varchar(255)   null=YES
-- LLM_PROFILE (16 列): …  PROVIDER_ID  bigint  null=YES  ← 可空，符合 §3.2

=== watb_verify（全新空库，由应用自动建表）===
自动建表: ['LLM_PROFILE', 'LLM_PROVIDER']
-- LLM_PROVIDER: ID bigint(不可空) / NAME varchar(255) / PROVIDER varchar(50) /
                BASE_URL varchar(500) / API_KEY_ENCRYPTED text / ENABLED tinyint(1)
-- LLM_PROFILE : ID bigint / NAME varchar(255) / PROVIDER_ID bigint(可空) / PROVIDER varchar(50) /
                BASE_URL varchar(500) / API_KEY_ENCRYPTED text / ENABLED tinyint(1)
```

**说明什么**：表与列**由实体声明真实建出来**，且 `NAME/PROVIDER/BASE_URL/API_KEY_ENCRYPTED` 的宽度与既有同名列**完全同型**（255/50/500/text），没有出现 smart-mybatis 因声明分叉而收窄的情况。`PROVIDER_ID` 确为可空 `bigint`——这是回填器靠 `IS NULL` 找行的前提。

---

## 【发现的问题】

### CRITICAL

**C-1　`.env.bak-20261110-101751` 含真实凭据且未被 `.gitignore` 覆盖，可被误提交入库**

- 文件：仓库根 `.env.bak-20261110-101751`（untracked，`?? .env.bak-20261110-101751`）
- 复现：`git check-ignore -v .env.bak-20261110-101751` → 无输出（未被 ignore）；`git status --porcelain=v1 .env.bak-20261110-101751` → `?? .env.bak-20261110-101751`
- 内容：含 `ENV.MYSQL_PASSWORD`、`ADMIN_PASSWORD`、`APP_SECRET_KEY`、`ENV.MYSQL_URL` 等 11 个键（值不在此复述）
- 根因：`.gitignore` 只写了 `.env`（第 3 行）与 `deploy/env/*.env`（第 4 行），未覆盖 `.env.bak*` / `.env.*` 这类备份命名
- 影响：任何一次 `git add -A` / `git add .` 都会把 **MySQL root 口令 + 管理员口令 + 系统加密主密钥** 提交进版本历史；`APP_SECRET_KEY` 一旦泄露，库里所有 AES-GCM 密文（含全部 API Key）可被离线解密
- 建议（由 Lead 执行，我不改源码/配置）：① 删除该备份文件；② `.gitignore` 增补 `.env.bak*` 或更宽的 `.env.*`（白名单方式）；③ 若该文件曾被提交过，需轮换 `APP_SECRET_KEY` 与全部口令
- 附带：同目录还有 `NUL`（99 字节，Windows 保留名误创建）与 `.zcode/`，也是未跟踪垃圾，建议一并清理

### MAJOR

**M-1　`compose.yaml` 存在与本轮契约无关的越界改动，且硬编码了环境相关的网关 IP**

- 文件：`compose.yaml:7`、`compose.yaml:21-25`
- 改动内容：
  - 端口绑定默认值 `0.0.0.0` → `127.0.0.1`
  - 新增 `extra_hosts: - "host.docker.internal:${WSL_HOST_GATEWAY:-172.21.192.1}"`
- 复现：`git diff -- compose.yaml`
- 问题：① 本轮契约（`docs/dev/llm-provider-refactor.md`）全文未提及 compose/部署；② 不在 task-1（`src/main/java/**`、`src/test/java/**`）与 task-2（`webui/**`）任何写范围内；③ `172.21.192.1` 是某台开发机的 WSL 网关地址，作为**默认值**硬编码进仓库，对其它环境无意义且会误导；④ 绑定地址改 `127.0.0.1` 是**行为变更**（容器不再对局域网暴露），若是有意为之应单独提交并说明。
- 建议：从本轮 diff 中剥离，单独作为一个部署类改动评审；`WSL_HOST_GATEWAY` 若确需保留，默认值应为空并由 `deploy/env/*.env` 提供。
- 同类但轻微：`README_CN.md` 也在两个写范围之外被改（12+/9-）。内容方向上与新结构一致、值得保留，但严格说同样越界——见 M-2。

**M-2　README 对回填口径的描述不准确，会让运维误判**

- 文件：`README_CN.md`（「第二步：配置 LLM 服务」末尾新增段落）
- 原文：`应用启动时会把 provider_id 为空、且端点相同的档案自动收敛到同一个供应商下`
- 实际（契约 §5.4 规则 2 + 代码 `LlmProviderBackfillRunner.java:204-208`）：归组键是 **`(provider, 归一化 baseUrl, 解密后的明文 key)` 三元组**，不是「端点相同」
- 影响：只按端点理解时，运维会以为「两个不同 key 的档案也会被并到一起」，从而不去核对——实际上它们会分成两个供应商（我用反证 b 实测确认了 13 vs 14 两个供应商）。表述偏差本身不影响代码正确性，但会误导升级核对。
- 建议：改为「端点、协议与密钥都相同的档案会自动收敛到同一个供应商下」。

### MINOR

**m-1　`LLM_PROVIDER.NAME` 只有应用层唯一，没有数据库唯一约束**

- 文件：`LlmProvider.java`（无 `unique` 声明）、`LlmProviderService.java:314-318`（应用层 `findByName` 校验）
- 复现：并发两次 `POST /api/llm-providers` 同名（`validateNameUnique` 与 `mapper.insert` 之间无锁、无 DB 约束）→ 可产生同名供应商
- 判断：**与既有惯例一致**（`LlmProfile.name`、`AgentDefinition.name` 同样只有应用层唯一），不是本轮引入的退化，故定 MINOR 而非 MAJOR。记录以备将来加索引时一并处理。

**m-2　测试库 `wechat-article-test` 残留 2 行供应商数据**

- 现状：`LLM_PROVIDER` 残留 `(2,'默认供应商','https://from.settings.test')` 与 `(3,'cheap-provider','https://cheap.test')`
- 来源：`AgentApiIntegrationTests` 的 `legacySettingsWriteThroughDoesNotHijackAUserProfile` 会 `DELETE FROM LLM_PROFILE` 并触发 `PUT /api/settings/llm`（→ 建供应商），但其 `finally` 只清 `LLM_PROFILE`（`AgentApiIntegrationTests.java:363`），**没有清 `LLM_PROVIDER`**
- 影响：**不造成假红**。`MySqlTestDatabaseInitializer.clearAllTables()` 在每次上下文启动前对**所有已存在表** `TRUNCATE`（`MySqlTestDatabaseInitializer.java:95-112`），所以残留不会渗入下一次测试。属卫生问题。
- 建议：在该用例（或类级 `@AfterEach`）补一条 `DELETE FROM LLM_PROVIDER`，与本轮新增测试的清理风格对齐（`LlmProviderApiIntegrationTests.java:57-61` 做对了）。

**m-3　`.gitignore` 未覆盖 `NUL` 这类 Windows 保留名误创建文件**

- 现状：仓库根有 untracked 的 `NUL`（99 字节）与 `.zcode/`
- 影响：轻微，同上；建议清理并视需要 ignore。

---

## 【未验证项】

以下均为**我没有实际执行**的，明确标注，不含糊带过：

1. **浏览器端 UI 实操未验证**。我没有打开浏览器点「新增供应商 / 展开 / 新增模型 / 删除护栏禁用态 / 悬空告警态」的真实交互。前端结论来自：`npm run check` + `npm run build` 实跑、模板与脚本的逐行阅读、以及独立的 `node` 侧行为实测（`imageChain`/`usable()`）。**视觉呈现、Escape 关闭、可访问性实测、响应式布局均未验证。**
2. **非管理员口径的前端渲染未验证**（后端 403 已实测，但前端「只读 + 提示」的界面表现未看）。
3. **`PUT /api/settings/llm` 在真实 HTTP 上的写透落点未单独实测**。§5.3 的判定依据是源码（`mapper.findDefault()` 而非 `defaultProfile()`）+ 既有单测三条对照用例 + `AgentApiIntegrationTests` 通过；我**没有**在真实实例上跑一遍「库里只有非默认档案时 PUT 设置页」的端到端复现。
4. **图片模型（`imageCarrier`）走供应商密钥的真实端到端未验证**。仅验证了前端 `imageCarrier` 在 `providerEnabled=false` 时确实跳过该档案。
5. **Docker 构建 / `compose.yaml` 的实际行为未验证**（且见 M-1：该文件有越界改动，本轮不应作为验收依据）。
6. **`target/classes/static`（Lead 浏览器验收用的前端产物）没有被我重新构建覆盖验证**——我执行了 `npm run build`，产物在 `webui/dist`；`target/classes/static` 里的既有产物我**未替换**，也未验证两者是否同步。
7. **并发场景未验证**：并发创建同名供应商、并发回填（多实例同时启动）、并发改供应商 key 与同时读链，均未做压测/竞态验证（见 m-1 的分析是静态推断）。
8. **回填在「超大规模存量」下的行为未验证**（N 条档案逐行解密比较是 O(N×供应商数)，我只在 10 条量级上实测）。

---

## 【临时改动复原声明】

本轮做反证 d 时**临时修改过 1 个源码文件 1 行**，其余源码/配置/前端**一律未改**（本报告的写范围只有本文件）。

| 项 | 内容 |
|---|---|
| 临时改动 | `src/main/java/ink/icoding/wechat/article/agent/LlmProfileService.java` — 将 `failoverChain` 中 `addIfUsable(chain, seen, defaultProfile());` 与 `addIfUsable(chain, seen, fallbackProfile());` 两行**对调** |
| 还原方式 | 改动前先 `cp` 原文件到 `/tmp/LlmProfileService.java.orig` 并记录 sha256；验证完成后 `cp` 回原位（**逐字节还原**，非手工重打） |
| 还原前 sha256 | `b30eeb978b90636debf1296d7b0321c2cd43e53f7dfbbe6044a6617832ae1848` |
| **还原后 sha256** | `b30eeb978b90636debf1296d7b0321c2cd43e53f7dfbbe6044a6617832ae1848`（**完全一致**） |
| 该文件相对 HEAD 的 diff 行数 | 改动前 `426` → 还原后 `426`（**未增加任何行**） |
| 残留检查 | `git diff --ignore-cr-at-eol -- <file> \| grep -nE "^\+.*(fallbackProfile\|defaultProfile)\(\)\);"` → **无输出**（我的临时改动零残留） |
| 归位确认 | 还原后 4 跳：`119 bound` / `120 defaultProfile()` / `121 fallbackProfile()` / `122 profile` |
| 绿验证 | 还原后 `npm run check:chain` → `✓ 档案链一致性自检通过（后端 4 跳结构 + 15 个行为用例）` |

**其它临时物**（均已清理，未进入源码树）：

- 探针类 `FailoverProbe.java` 与 `.class`：写在 `/tmp/watbprobe`（WSL）+ `C:\Users\WIN11\AppData\Local\Temp\watbprobe`（编译产物），已删除；`git status` 确认**无任何探针文件进入 `src/`**
- 临时数据库 `watb_verify`：已 `DROP DATABASE`（现存仅 `wechat-article`、`wechat-article-test`）
- 真实实例：端口 18080 的 java 进程已 `Stop-Process`；确认无本项目残留 java 进程
- 测试产物：`rm -rf target/surefire-reports` 后重跑（这是消除 2026-09-17 幽灵报告 `ExecutePathProbeTest` 的必要步骤，我确认重跑后该报告不存在，总数 `583` 而非受污染的 `587`）；**未执行 `mvn clean`**，`target/classes/static` 原样保留供 Lead 浏览器验收

**未做 git 提交。未修改 `docs/dev/llm-provider-refactor.md`（契约本身）。**