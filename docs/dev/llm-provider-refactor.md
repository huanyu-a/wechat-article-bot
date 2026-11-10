# 模型档案 → 供应商 + 模型（接口契约冻结稿）

> 状态：**契约冻结**（Lead 产出，back/front 两侧据此并行施工；改动契约必须先改本文件并广播）
> 触发：用户要求「模型档案不要再按模型一个一个填 Base URL 与 Key，改成按供应商填，参考 dsh 的模型添加功能及列表」

---

## 1. 为什么改

存量库的实测形态（`data/migration/watb-migration-20260920-152438.sql`，`LLM_PROFILE` 表）：
8 条档案的 `PROVIDER` / `BASE_URL` / `API_KEY_ENCRYPTED` **三列逐字相同**，只有 `MODEL_NAME` 不同。

```
(1 ,'deepseek-flash'   ,'OPENAI_COMPATIBLE','https://nexus.bx9y.com.cn','deepseek-flash'   , ...,'<同一个密文>'...)
(13,'glm-5.3-flash'    ,'OPENAI_COMPATIBLE','https://nexus.bx9y.com.cn','glm-5.3-flash'    , ...,'<同一个密文>'...)
(14,'hy4-preview'      ,'OPENAI_COMPATIBLE','https://nexus.bx9y.com.cn','hy4-preview'      , ...,'<同一个密文>'...)
...（共 8 条，端点与密钥完全相同）
```

`LlmProfileSeeder` 的 javadoc 早就承认了这件事——「这些档案走的是同一个网关、同一个 key，**只有模型名不同**；
让用户为每个模型再填一遍 key 既冗余又容易填错」——只是当时用「复制」绕过去了，复制出来的是 8 份需要分别维护的密钥。
换一次 key 要改 8 处，漏一处就是一个只在故障切换时才暴露的坏档案。

改法：**端点与密钥属于供应商（provider），模型只声明「哪个模型」。**

## 2. 参考对象：DSH 的模型设置页

从 dsh 客户端插件 `@deepseek-ai/dsh-client-ui-settings-models`（`resources/app.asar` 内）提取的实际结构：

- 页面是**提供商行列表**，一行一个已配置的提供商，一次只展开一张编辑卡片。
- 每张卡片的主字段是**一个 API 密钥输入框**（只写、从不回显已存凭据），地址在折叠的「自定义设置」里。
- 新增入口唯一：「**添加模型提供商**」，卡片顶部是方式切换——
  「第三方模型提供商」（从内置目录选）与「**自定义模型 API**」（自建：Provider ID + API 地址 + **API 协议** + 至少一个模型）。
- API 协议以产品名显示：`OpenAI Chat Completions` / `OpenAI Responses` / `Anthropic Messages`。
- **模型是提供商下的一行行**：模型 ID、可选显示名称、上下文窗口、最大输出 token 数、输入类型、拖拽排序。
- 提供商级**没有**推理等级之类的按模型才有的字段。

落地到本项目：**供应商行 → 其下挂模型档案**。本项目没有「模型目录/显示名称」这一层，
档案的 `name` 就是显示名、`modelName` 就是上游模型名，保持不动。

**本轮不做**（明确列为后续项，避免验收歧义）：`获取可用模型`（探测 `/v1/models` 的发现器）、
上下文窗口/输入类型/推理等级、拖拽排序、Provider ID（本项目用自增 id）。

## 3. 数据模型

### 3.1 新表 `llm_provider`（实体 `LlmProvider`，包 `ink.icoding.wechat.article.agent`）

| 字段 | 列 | 声明 | 说明 |
|---|---|---|---|
| `id` | `ID` | `@ID` | |
| `name` | `NAME` | `@TableField(length = 255)` | 供应商显示名，应用层唯一。与 `AgentDefinition.name` 等 5 处同名同宽 |
| `provider` | `PROVIDER` | `@TableField(length = 50)` | API 协议：`OPENAI_COMPATIBLE` / `OPENAI_RESPONSES` / `ANTHROPIC`。与 `LlmConfig.provider` / `RenderConfig.provider` / `LlmProfile.provider` 四处同名，**声明必须逐字相同** |
| `baseUrl` | `BASE_URL` | `@TableField(length = 500)` | 与 `LlmConfig.baseUrl` 同名同宽 |
| `apiKeyEncrypted` | `API_KEY_ENCRYPTED` | `@TableField(length = 65535)` | 与 `LlmConfig` / `LlmProfile` 同名同宽 |
| `enabled` | `ENABLED` | 默认 | |
| `createdBy` / `createdAt` / `updatedAt` | | | |

> ⚠️ `EntityColumnDeclarationConsistencyTest` 按**字段名**对列声明做跨实体一致性校验，
> 上面每一个字段名都已存在于其它实体。抄错宽度会让 smart-mybatis 对真实列发 `MODIFY COLUMN`。
> 新增实体后必须跑该测试。

### 3.2 `llm_profile` 变更

新增：`private Long providerId;`（列 `PROVIDER_ID`，可空）。

保留但**冻结为存量快照**：`provider` / `baseUrl` / `apiKeyEncrypted` 三列**不删、不清空**。
理由：删除字段会让 smart-mybatis 的列同步行为变得不可预测；清空则失去代码回滚时的端点。
运行时只在 `provider_id IS NULL` 时读它们（见 §4）。

## 4. 运行时解析（唯一口径）

`LlmProfileService.runtime(LlmProfile)` 保持返回 `RuntimeProfile`（**记录形状不变**，
`AgentFactory` / `LlmConfigService` 调用点零改动），但取值口径改为：

```
provider_id != null  →  取供应商：
                        不存在（被删/脏数据） ⇒ enabled=false ⇒ available()==false（调用方跳过）
                        否则 provider/baseUrl 取自供应商行；
                             enabled = 档案.enabled && 供应商.enabled
provider_id == null  →  存量回退：provider/baseUrl/apiKey 取档案自身的旧列，
                             enabled = 档案.enabled
```

`available()` 仍是 `enabled && apiKey 非空`，因此 **「供应商被停用/没配 key ⇒ 其全部模型在故障切换链上消失」**
是自动成立的，不需要在链上再加判断。

> ⚠️ **收紧（Lead 2026-11-10 追加）**：`available()` 必须同时要求 **provider 非空且 baseUrl 非空**，
> 即 `enabled && provider 非空 && baseUrl 非空 && apiKey 非空`。
> 理由：legacy 分支的空 baseUrl 已改为「返回 null 而不抛」（脏行不该让整条链抛），
> 若 `available()` 不挡它，`AgentFactory.createModelCandidates` 会先判它可用、
> 再在 `createModel` 的 `switch (provider)` 上对 null 抛 `BusinessException` ——
> **一条脏行把整个请求打挂**，比原来在归一化处抛更危险（抛出点挪到了「已经选定它之后」）。
> 供应商路径天然满足该条件（创建时 provider/baseUrl 都是 `@NotBlank`），故这条只对 legacy 脏行生效。
> 代价：`LlmProfileServiceTest` 的夹具必须补 provider/baseUrl 默认值（那是正确的红）。

**故障切换链的 4 跳调用序列不得改动**：`addIfUsable(chain, seen, bound)` →
`defaultProfile()` → `fallbackProfile()` → `profile`。`webui/scripts/check-profile-chain-parity.mjs`
的结构自检按正则匹配这四个实参名。只允许改 `addIfUsable` 的**判定体**（改为走 §4 的运行时解析）。

## 5. 后端接口契约

### 5.1 新：`/api/llm-providers`（`@PreAuthorize("hasRole('ADMIN')")`，与 `/api/llm-profiles` 同款）

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| GET | `/api/llm-providers` | — | `ApiResponse<List<ProviderView>>` |
| GET | `/api/llm-providers/{id}` | — | `ApiResponse<ProviderView>` |
| POST | `/api/llm-providers` | `ProviderRequest` | `ApiResponse<ProviderView>` |
| PUT | `/api/llm-providers/{id}` | `ProviderRequest` | `ApiResponse<ProviderView>` |
| DELETE | `/api/llm-providers/{id}` | — | `ApiResponse<Void>` |

```java
record ProviderRequest(@NotBlank String name, @NotBlank String provider, @NotBlank String baseUrl,
                       String apiKey, Boolean clearApiKey, Boolean enabled) {}

record ProviderView(Long id, String name, String provider, String baseUrl, boolean hasApiKey,
                    String apiKeyMasked, Boolean enabled, int modelCount, LocalDateTime updatedAt) {}

record RuntimeProvider(boolean enabled, String provider, String baseUrl, String apiKey) {
    boolean available();   // enabled && apiKey 非空
}
```

规则：
1. `name` 去空白后唯一（重复 → `BusinessException("供应商名称已存在：X")`）。
2. `provider` 必须 ∈ `{OPENAI_COMPATIBLE, OPENAI_RESPONSES, ANTHROPIC}`。
3. `baseUrl` 走与 `LlmProfileService.normalizeBaseUrl` 同一套归一化（去尾斜杠 + 剥 `/v1/chat/completions`、`/v1/responses`、`/v1/messages`、`/v1`；必须 http/https）。
4. `apiKey` 非空即加密覆盖；`clearApiKey=true` 清空。**启用中的供应商必须有 key**，否则拒绝。
5. `apiKeyMasked` 规则与档案一致：`未配置` / 长度 ≥12 时 `••••••••` + 末 4 位 / 否则全掩码。**任何响应不得回传明文或密文**。
6. `DELETE`：该供应商下仍有档案时**拒绝**（`BusinessException`，消息含剩余模型数），不做级联删除——级联会静默带走智能体的绑定。
7. `modelCount` = `llm_profile` 中 `provider_id = id` 的行数。

### 5.2 变更：`/api/llm-profiles`

```java
// 之前
record ProfileRequest(String name, String provider, String baseUrl, String modelName,
                      String apiKey, Boolean clearApiKey, Boolean enabled,
                      BigDecimal temperature, Integer maxTokens, String imageModelName) {}
// 之后：provider/baseUrl/apiKey/clearApiKey 全部移除（密钥与端点已归供应商）
record ProfileRequest(@NotBlank String name, @NotNull Long providerId, @NotBlank String modelName,
                      Boolean enabled, BigDecimal temperature, Integer maxTokens, String imageModelName) {}

// 之前
record ProfileView(Long id, String name, String provider, String baseUrl, String modelName,
                   boolean hasApiKey, String apiKeyMasked, BigDecimal temperature, Integer maxTokens,
                   Boolean enabled, boolean isDefault, boolean isFallback, String imageModelName,
                   LocalDateTime updatedAt) {}
// 之后
record ProfileView(Long id, String name, Long providerId, String providerName, boolean providerEnabled,
                   String modelName, boolean hasApiKey, String apiKeyMasked, BigDecimal temperature,
                   Integer maxTokens, Boolean enabled, boolean isDefault, boolean isFallback,
                   String imageModelName, LocalDateTime updatedAt) {}
```

- `hasApiKey` 语义 = **该档案在运行时拿得到密钥**（供应商有 key；存量回退行 = 自身有 key）。
- `providerEnabled` = 供应商启用状态；存量回退行一律 `true`（把判断留在档案自身的 `enabled`）。
- `providerName` 供前端分组/落位；供应商已删时返回 `null`。
- `providerId` 必填；**创建/更新时**指向不存在的供应商 → `BusinessException("供应商不存在")`。

**悬空与存量两种行的确切取值**（前端链的判据依赖它，务必逐字实现）：

| 场景 | providerId | providerName | providerEnabled | hasApiKey |
|---|---|---|---|---|
| 正常（供应商在） | 非空 | 供应商名 | 供应商 `enabled` | 供应商有 key |
| **悬空**（供应商被删） | 非空但查不到 | `null` | `false` | `false` |
| **存量回退**（provider_id 为空） | `null` | `null` | **`true`** | 本行 `api_key_encrypted` 非空 |

- 悬空行**不得抛异常**：列表接口必须能列出坏数据，否则用户看不到问题也无法修复。前端把它渲染成管理员告警态（「供应商已不存在，运行时不可用」）。
- 存量回退行的 `providerEnabled` **必须是 `true`**：前端 `usable()` 写的是
  `p.enabled && p.hasApiKey && p.providerEnabled !== false`（缺省视为真）。
  若这里填 `false`，升级窗口内所有存量档案会在前端链上凭空消失、而后端仍可用——
  前端报的模型名与后端实际用的不一致，正是这条链最忌讳的失效模式。
- `LlmProviderBackfillRunner`（@Order 21）必须早于 `LlmProfileSeeder`（@Order 25），
  否则种子复制出的档案拿到 `provider_id = null`，会长期停在回退路径、在供应商页面上看不见。
  需有断言种子档案 `providerId` 非空的用例钉住这个顺序。
- 前端「展开」是**浏览态**（看该供应商下的模型列表），编辑仍走 modal；不要做成半编辑态。
- `/api/llm-profiles/{id}/set-default`、`/set-fallback` 不变。

### 5.3 存量兼容：`/api/settings/llm`（`LlmConfigService`）

`PUT /api/settings/llm` 的写透目标（`syncDefaultFromConfig`）改为：
1. 在 `llm_provider` 里按 `(provider, baseUrl, 解密后的明文 key)` **三元组精确匹配**已有供应商：命中则复用；
   （同理不能按密文比较：AES-GCM 随机 IV，同明文不同密文）
2. 未命中则复用/创建名为 `默认供应商` 的那一条（常量 `DEFAULT_PROVIDER_NAME`），把三元组写上去；
3. 把默认档案的 `provider_id` 指过去，并同步 `modelName` / `temperature` / `maxTokens` / `enabled`。

`view()` / `runtime()` 无需改动（`RuntimeProfile` 形状未变）。
**「找不到默认档案时新建而不是劫持第一条用户档案」这条既有结论必须保留**（见 `LlmProfileServiceTest` 的对应用例）。

### 5.4 启动迁移

新增 `LlmProviderBackfillRunner`，`@Order(21)`（`LlmProfileMigrationRunner` 是 20，`LlmProfileSeeder` 是 25）。
逐条处理 `provider_id IS NULL` 的档案：

1. `baseUrl` 空 ⇒ 跳过（造不出供应商）。
2. 按 `(provider, 归一化 baseUrl, **解密后的明文 key**)` 归组：**同组只建一个供应商**。
   这就是 8 条 nexus 档案收敛成 1 个供应商的那一步。

   > ⚠️ **不能按密文归组**（Lead 2026-11-10 实测修正）。`CryptoService` 的 AES-GCM 每次加密用随机 IV，
   > 同一明文 key 两次加密得到**不同密文**；实测同一 key POST 两次，`MD5(API_KEY_ENCRYPTED)`
   > 分别是 `1330b996…` / `5bf3392e…` / `fcc4ed9f…`。真实 dump 里 8 行密文相同，只是因为
   > `LlmProfileSeeder` 当年是**逐字复制**那一列，不是加密具有确定性。
   > 按密文归组 ⇒ 用户手工建的档案一条一个供应商，正是本次要消灭的重复。
   > 归组用的明文只在内存里做比较，不落库、不进日志、不进异常消息。
3. 供应商命名：三元组与 `llm_config` 当前行一致 ⇒ `默认供应商`；否则取 baseUrl 的 host
   （`https://nexus.bx9y.com.cn` → `nexus.bx9y.com.cn`），重名时追加 ` (2)`、` (3)`… 保证唯一。
4. 回填 `provider_id` 并 `updateById`。
5. 幂等：只动 `provider_id IS NULL` 的行，第二次启动无事可做。
6. 只 `log.warn` 不抛，不阻塞启动（与其它 Runner 同款约定）。

`LlmProfileMigrationRunner`（llm_config → 默认档案）与 `LlmProfileSeeder`（标准档案）同步改为**带上 `providerId`**：
迁移器建完档案后交给回填器补供应商；种子从来源档案**复制 `providerId`**。

## 6. 前端契约（`webui/`）

### 6.1 `ModelProfilesView.vue` 结构

```
[页头] 模型档案 / 说明（切换顺序不变）  [新增供应商]
[图例] 默认档案 / 兜底档案 / 图片模型 / 供应商
── 供应商区块（v-for provider）──
   行：名称 · 协议标签 · Base URL · API Key 掩码 · N 个模型 · 启用开关 · [编辑][删除]
   展开后：该供应商的模型列表（沿用现有 profile-item 卡片的字段与操作）
           [新增模型]（落在该供应商下）
[新增供应商] 卡片 modal：名称 / API 协议 / Base URL / API Key / 启用
[模型] modal：档案名称 / 上游模型名 / 图片模型 / temperature / maxTokens / 启用
             （不再有 Base URL 与 API Key 输入框；顶部只读显示所属供应商）
```

- 供应商删除按钮在 `modelCount > 0` 时禁用并给出 title 说明（后端也会拒绝，前端先拦一道）。
- 一个供应商的下挂模型按 `id` 升序；默认/兜底徽标、设为默认/兜底、启停、删除的交互**保持现状**。
- 非管理员口径不变（只读 + 提示）。

### 6.2 `utils/profiles.js` 与其自检

`usable(p)` 由 `p.enabled && p.hasApiKey` 改为 `p.enabled && p.hasApiKey && p.providerEnabled !== false`
（`providerEnabled` 缺省视为 `true`，兼容老快照）。
`scripts/check-profile-chain-parity.mjs`：用例工厂 `P()` 补 `providerEnabled: true` 默认值，
并**新增**两条行为用例——「供应商被停用 ⇒ 该档案不在链上」与「供应商缺 key ⇒ 同上」。
A 段结构自检（4 跳 + `findFirst()` 回落）不改，且必须继续通过。

## 7. 验收标准

1. `./.mvn/mvn-local.sh -o test`（Git Bash + Windows JDK17）**全绿**，包含既有 88+ 用例。
2. `webui && npm run check && npm run build` 通过（`check` = `check:imports` + `check:chain`）。
3. 真实 MySQL 集成用例证明：**`llm_provider` 表本身真实建成**、`llm_profile.PROVIDER_ID` 列真实建成；
   供应商下 3 条档案共享一份密钥；停用供应商后 `failoverChain` 不再返回其模型。
4. 存量回填用例证明：同 `(provider, baseUrl, 明文 key)` 的档案收敛为 1 个供应商，
   而 key 不同的档案分开成两个；且只跑一次。
5. `AgentApiIntegrationTests`（`/api/llm-profiles` 的角色矩阵、掩码、set-default）继续通过。
6. 任何接口响应里不出现明文或密文密钥。
7. `EntityColumnDeclarationConsistencyTest` 通过。

## 8. 已知取舍

- **档案不能再单独指定端点/密钥**。要「同一智能体文本走 A、配图走 B」，做法是给 B 建一个供应商 +
  一条声明了图片模型的档案——`imageCarrier` 沿整条链找，本来就是这个语义，能力没有丢。
- 存量 `llm_profile.base_url` / `api_key_encrypted` 保留为死列（见 §3.2）。
- `/api/settings/llm` 设置页保留，作为「默认档案 + 默认供应商」的快捷入口；图片三件套仍归它管。
- 本轮不含模型发现（`获取可用模型`）、上下文窗口、输入类型、推理等级、拖拽排序。

---

## 第二轮覆盖说明（2026-11-10）

本轮（`docs/dev/model-profiles-round2.md`）对本契约的以下条款做出修订，**以第二轮的约定为准**：

1. **§5.3 设置页写透入口已下线（API 保留）**
   前端「系统设置 → LLM 服务」整块已移除，设置页只剩「排版渲染服务」与「修改登录密码」。
   `PUT /api/settings/llm` 与 `GET /api/settings/llm` **仍保留且仍工作**，用于回滚与旧脚本；
   `syncDefaultFromConfig` 的写入路径继续存在（`llm_config` 的 LLM 四件套照常写）。
   连带：`AgentFactory`、`ArticleAiService`、`ImageGenerationService` 里指向「系统设置」的 LLM 报错文案已改指「模型档案」。

2. **图片模型改在「模型卡片」上用复选框指定，`llm_config` 旧列保留**
   最终形状见第二轮契约 §4.3（经**两次**用户否决后定稿）：
   - 曾设计 `llm_provider.purpose=IMAGE` → 用户否决「供应商会同时供应所有类型模型」，已全部撤销；
   - 曾改为档案上的「图片模型名称」文本框 → 用户否决「采用复选框，选择是否图片模型」，已改掉。
   **定稿**：`llm_profile.IS_IMAGE_MODEL`（`Boolean`，可空）+ 模型卡片上的复选框「这是一个图片模型」。
   `imageRuntime` 的取值：① 链上首个勾选且可用的档案（用它所属供应商的端点与密钥，图片模型名 = 它自己的
   `modelName`）→ ①b 链上无人勾选时再用遗留 `imageModelName`（只读，保老部署）→ ② 回落 `llm_config` 三件套。
   全部在「全局门禁 `config.enabled()`」之内 —— 关掉 AI 服务就不会继续产生生图费用。
   标准配法：同一供应商下建两条档案（文本一条不勾选、图片一条勾选），共用一份端点与密钥。
   `LlmConfig` 的 `imageBaseUrl` / `imageModelName` / `imageApiKeyEncrypted` **不删、不清**。
   注：`LLM_PROVIDER.PURPOSE` 列在短暂实现期被创建过，实体已不再映射它 → 无害孤儿列，不做删列。

3. **§6.1 模型卡片布局被第二轮 §1 修订**
   原布局在同屏多列时会让卡片操作栏溢出卡片边界（实测 2124 视口下「删除档案」溢出 28.9px）。
   现改为：`footer` 允许换行 + `min-width:0`，并用 container query 在窄卡片下把「设为默认 / 设为兜底」
   降级为图标按钮（保留原 `title`/`aria-label` 语义）。「不溢出」现在是硬性验收判据（四个视口 0 越界）。

4. **§5.2 的 `ProfileView` 追加两个只读字段（第二轮 §3.3）**
   `runtimeAvailable`（= `RuntimeProfile.available()` 的求值结果）与 `providerPurpose`（所属供应商用途）。
   目的是把「档案是否可用」的判据收敛到后端一处，前端不再复刻 —— 第一轮遗留的
   `webui/src/utils/profiles.js` 判据漂移（漏 `provider`/`baseUrl` 非空）由此消除。
   `RuntimeProfile` / `RuntimeConfig` 的 record 形状**未变**。

5. **`hasReachableKey` 收紧（第二轮 m-8）**
   从「密文非空白」改为「密文非空白 **且能解密**」。解不开的密钥不再被种子当成「可用来源」。
   这导致 `LlmProfileFallbackColumnPersistenceTests` 的伪密文夹具失效，已改为真实 `CryptoService` 加密。

6. **读路径不再因解密失败抛 500（第二轮 M-2/B3）**
   新增 `LlmProviderService.decryptOrNull`。`GET /api/llm-profiles` 与 `GET /api/settings/llm`
   在密文解不开时仍返回 **200**（修复前均 500），坏行显示「无法解密（请检查 APP_SECRET_KEY）」。
