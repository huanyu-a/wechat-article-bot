# 模型档案第二轮改造契约（2026-11-10）

本轮来源：用户看完第一轮成品后的四条直接反馈 + 三份独立复查报告的问题清单。

- 用户 ①「模型卡片超限了」
- 用户 ②「供应商下无法添加模型，要接入 v1/model 列表」
- 用户 ③「其他缺失功能以及 bug 请你自己发现」
- 用户 ④「系统设置中的 llm 设置，也应转移到模型档案中」

独立复查（问题编号沿用各报告）：
- 后端 `docs/dev/review-2026-11-10-backend.md`（M-1..M-4、m-1..m-12）
- 前端 `docs/dev/review-2026-11-10-frontend.md`（F1..F11）
- 工程侧：`review-ops` 的沙箱中途失去仓库访问，报告**未能落盘**；其 CRITICAL 结论（`application.yaml` 默认凭据）由 Lead 复核实为**存量问题且本轮未改**，不在本轮处理，单独立项（见 §9）。

本文是**冻结契约**。实现方不得单方面改动接口形状；发现契约与代码冲突时回报 Lead，不要自行二选一。

---

## 1. 用户 ①「模型卡片超限了」——卡片溢出容器

**现象（Lead 已实测）**：模型卡片 `footer` 里的 4 个按钮总宽大于卡片可用宽度，最后一个删除按钮**溢出卡片右边界**。

实测数据（viewport 2124，`.provider-model-grid` 五列，每列 272px）：

```
card      w=272  l=474  r=746
footer    w=236  l=492  r=728          ← footer 自身没超
button[0] 设为默认   w=102  r=594
button[1] 设为兜底   w=102  r=703
button[2] 编辑       w=30   r=739
button[3] 删除       w=30   r=775      ← 超出 card 右边界 746，超出 29px，也超出 footer 728
```

`footer` 计算样式 `display:flex; flex-wrap:nowrap; gap:6px; overflow:visible` → 三个按钮都不换行、也不收缩，于是第四个被推出卡片外。

**根因**：`webui/src/style.css` 的 `.provider-model-grid` 用 `repeat(auto-fill, minmax(min(250px,100%),1fr))`，在宽屏下摊到 5 列 → 每张卡只有 ~272px；而 `.profile-item footer` 是 `nowrap` 且按钮有 `padding` 撑出的固定宽度（`设为默认` 两个中文字 + 图标已 102px）。

**要求**：

1. **必须修**：模型卡片内的操作行在窄卡片下不得溢出。至少做到 `flex-wrap: wrap`（按钮换行）+ `min-width: 0` 允许收缩；溢出不得以 `overflow:hidden` 掩盖（会变成按钮被裁切、点不到）。
2. 「设为默认 / 设为兜底」两个长按钮在宽度不足时降级为**图标按钮 + `title`/`aria-label`**（图标用已有的 `Star` / `Shield`），或用短标签。`title` 与 `aria-label` 必须保留原语义（「设为默认档案」「设为兜底档案」）。
3. 验收判据（可复现）：在 **1280 / 1440 / 1920 / 2124** 四个视口宽度下，任一模型卡片的**每一个**按钮 `getBoundingClientRect().right <= card.right` 且 `left >= card.left`；页面 `document.documentElement.scrollWidth <= window.innerWidth`（不产生横向滚动条）。
4. 不许靠减少列数掩盖（那只是把阈值推后）。允许同时调整列宽下限，但**必须**同时做 1 与 2。

---

## 2. 用户 ②「供应商下无法添加模型」——入口缺失 + 接入 `GET /v1/models`

### 2.1 入口缺失（这是真 bug，不是错觉）

**现状**：`ModelProfilesView.vue:420` 的「新增模型」按钮渲染在 `<div v-if="isExpanded(group)" class="provider-models">` **内部**，即**只有展开该供应商后才看得到**；折叠状态下供应商行里没有任何「加模型」入口。用户看到的「供应商下无法添加模型」就是这个。

另外 `toggleExpand` 一次只展开一组（手风琴），折叠后按钮消失。

**要求**：

1. 供应商行（`<header class="provider-head">` 的 `.row-actions`）增加一个**常驻的「新增模型」图标按钮**（`Plus`），`title`/`aria-label` = `为供应商 ${name} 新增模型`。点击直接打开模型弹窗并预置 `providerId`。
2. 保留展开区底部那个「新增模型」按钮（大按钮更好点），两者并存不冲突。
3. 该按钮**仅对 `kind === 'provider'` 且 `isAdmin` 渲染**；`dangling` / `legacy` 不渲染（它们的档案必须先归位供应商）。
4. 空供应商（下挂 0 个模型）折叠时也必须能看到这个入口——这是当前 `api.anthropic.com` 那类情况。

### 2.2 接入 `GET {baseUrl}/v1/models` 模型发现（用户明确要求）

新增只读能力：**用供应商自己的端点与密钥拉取可用模型列表，供选择/填充「上游模型名称」**。参考 DSH 的「获取可用模型」。

**后端**

1. `LlmProviderService` 增 `List<String> discoverModels(Long providerId)`；失败时抛 `BusinessException`，消息必须是**面向用户的中文**且**不含密钥**。
2. 端点拼接规则（与既有 `normalizeBaseUrl` 的约定一致，baseUrl 已是根地址）：
   - `OPENAI_COMPATIBLE` / `OPENAI_RESPONSES` → `GET {baseUrl}/v1/models`，头 `Authorization: Bearer <明文key>`
   - `ANTHROPIC` → `GET {baseUrl}/v1/models`，头 `x-api-key: <明文key>` + `anthropic-version: 2023-06-01`
   - 若 `baseUrl` 本身已以 `/v1` 结尾（用户手工写了），**不得**拼成 `/v1/v1/models`——复用 `normalizeBaseUrl` 先归一再去重。
3. 响应解析：取 `data[].id`（OpenAI 形态）或 `data[].id` / `models[].name`（宽松兼容），去重、**按字典序排序**；空列表视为失败并给出「该供应商未返回任何模型」。
4. **护栏（硬要求）**：
   - 类级 `@PreAuthorize("hasRole('ADMIN')")` 已在 controller 上，端点沿用。
   - **超时**：connect 5s / request 10s，不得无限等。
   - **响应体上限**：读取上限 1 MiB，超出即失败（防上游返回巨型 body）。
   - **限流**：每用户每分钟 10 次（复用既有 `common/RateLimiter`，与 `/api/skills/preview` 同款用法）。
   - **不落库、不缓存**：这是查询动作，不得写 `llm_provider` 任何列。
   - **不泄密**：明文 key 只出现在出站请求头里；日志只许记 `providerId`、`baseUrl`、HTTP 状态码、耗时，**不得**记请求头、响应体全文或明文。
   - **不得跟随重定向到别的 host**（`HttpClient.Redirect.NEVER` 或限制同 host），避免把密钥带到第三方。
   - `enabled=false` 的供应商也允许探测（用户常在启用前先验证），但无 key 时必须直接失败并提示「请先配置 API Key」。
5. 端点：`GET /api/llm-providers/{id}/models` → `ApiResponse<List<String>>`。**注意路径**：`/{id}/models` 与既有 `/{id}` 不冲突，但需确认 Spring 不把 `models` 当成 id（用 `@PathVariable Long id` + 精确 `@GetMapping("/{id}/models")` 即可）。
6. **测试**：新增用例覆盖（用 mock 的本地 HTTP 服务，参照 `MarkFlowRenderServiceTest` 的写法）——正常解析、空列表、非 2xx、超时、响应体超限、`/v1` 结尾不重复拼、无 key 直接失败、日志不含 key。

**前端**

1. 供应商编辑/新增弹窗里，Base URL 与 API Key 之后加「**获取可用模型**」按钮：
   - 新建（尚无 id）时按钮禁用并提示「保存供应商后可获取模型列表」。
   - 点击后 loading 态；成功则把模型名列表缓存到该弹窗的 state，并显示「已获取 N 个模型」。
   - 失败按后端中文消息展示，**不得**把技术栈信息透给用户。
2. 模型弹窗的「上游模型名称」改为 **`<input list="...">` + `<datalist>`**（可自由输入，也可下拉选择）：
   - 数据源优先级：① 该弹窗打开时若已有探测结果 → 用它；② 否则用**本地已知的该供应商下已有档案的 `modelName` 去重集**（离线可用，不需要联网）。
   - 必须有「获取可用模型」入口（调 §2.2 的接口），成功后刷新 datalist；探测失败时**仍允许手工输入**，不得把输入框锁死。
   - **不得**在页面加载时自动探测（会凭空产生大量出站请求）。
3. 空态/错误态文案：探测失败时在 datalist 旁边给一行 `small` 提示，不要把整个弹窗变成错误态。

### 2.3 顺带修 F6/F7（同一处 UI 的两个错）

- **F6**：旧版档案（`group.kind === 'legacy'`）缺 key 时，卡片 pill 说「供应商未配置密钥」，与该组标题「旧版档案（未指定供应商）」打架。改成按 `group.kind` 分派：legacy → 「该档案未配置密钥」。
- **F7**：新建模型时 `modelName` 预填 `gpt-4.1-mini` 不随协议变化。改为 `modelName: ''`（留空 + `required` 拦住），placeholder 按 `currentProvider.provider` 给（`ANTHROPIC` → `claude-…`；`OPENAI_*` → `gpt-…`）。

---

## 3. 用户 ③「其他缺失功能以及 bug」——本轮必修清单

来源为两份已落盘复查报告。以下逐条**必须**修（除非标注「不修」并写明理由）。

### 3.1 后端

> 说明：`review-backend` 的磁盘报告（`docs/dev/review-2026-11-10-backend.md`，234 行）比它的口头简报更完整，下面补入它另外三条 MAJOR（B1/B2/B3 编号为 lead 所加，便于追踪）。**B1/B2 是静默失效类缺陷，必须修。**

| 编号 | 问题 | 要求 |
|---|---|---|
| **B1** | 回填把 `llm_config.enabled` 当成供应商启用位：`LlmProviderBackfillRunner` 的 `matchesConfig` 分支把 `config.getEnabled()` 传进 `resolveDefaultProviderId`，`enabled=0` 时该供应商被建成**停用** ⇒ 整组档案从故障切换链静默消失；同一回填器里另一分支（`attachOrCreateProvider`）却按「有 key 即启用」——**两套口径** | 回填路径统一为「有 key 即启用」，与 `attachOrCreateProvider` 对齐：不要传 `config.getEnabled()`；若 `resolveDefaultProviderId` 需要区分调用方，加显式参数（回填传 true，设置页写透传设置值）。补一条测试：`llm_config.enabled=0` + 存在与其三元组一致的档案组 ⇒ 回填后该组档案仍在 `failoverChain` 上 |
| **B2** | 设置页写透会整行改写名为「默认供应商」的那一行，而这一行**不专属设置页**：用户可以把它当普通供应商用并把其它档案挂上去，之后任何一次写透都会把那些档案的端点与密钥**静默换成设置页的值** | 写透时先查该行被多少条**非默认**档案引用；大于 0 时**新建**一行（如「默认供应商 (2)」）并让默认档案指过去，**绝不改写被别人引用的行**。补一条测试：默认供应商行被另一条档案引用 ⇒ 写透后该档案的 baseUrl/apiKey 保持不变 |
| **B3** | `addIfUsable` 有容错，但紧随其后的回落路径没有：坏密钥把链清空后，`AgentFactory.createModel(definition)` 与 `LlmConfigService.runtime()/view()` 对同一条档案**再调一次** `runtime(...)` 且无 try/catch ⇒ 抛 500，而不是降级到 `llm_config`；`GET /api/settings/llm` 也会 500 | 与本表 M-2 同根因，但**必须覆盖这三处调用点**：`AgentFactory.createModel`（`:222-225`、`:293`、`:303` 附近）、`LlmConfigService.runtime()`、`LlmConfigService.view()`。验收要能演示「密文解不开 ⇒ 接口仍 200、状态显示不可用」，不是 500 |
| **M-1** | `LlmProviderService.java:304` `provider.setEnabled(request.enabled() == null \|\| request.enabled())`——PUT 省略 `enabled` 会**静默重新启用**供应商；且无 key 的停用供应商**任何 PUT 都 400**（连改名都做不到） | `enabled` 为 `null` 时**保持原值**（`if (request.enabled() != null) setEnabled(...)`）；`enabled` 校验（启用必须有 key）只在该供应商**最终为启用**时判，且这条校验要允许「先存端点再补 key」的两次保存流程 |
| **M-2** | 密钥解密失败无降级：`LlmProviderService.view()` / `runtime()`、`LlmProfileService.view()` / `runtime()`、`LlmConfigService`、`AgentFactory` 共 7 处无 try/catch，`APP_SECRET_KEY` 轮换后**列表接口 500**，管理员失去唯一修复入口 | 加 `decryptOrNull()`（catch 后 `log.warn` 只记 provider/profile id 与「解密失败」，**不记密文/明文**，返回 null）；`view()` 用 `hasApiKey=false` + `apiKeyMasked="无法解密（请检查 APP_SECRET_KEY）"` 表达。`runtime()` 路径已有 `addIfUsable` 兜底跳过，但 `runtime()` 自身被 `AgentFactory` 直接调用处也必须不抛 |
| **M-4** | `ProviderRequest`（record）自动 `toString()` 会把明文 `apiKey` 带出来；与 `KeyGroup` 刻意不用 record 的自述矛盾 | 覆写 `toString()`，只输出 `name/provider/baseUrl/hasApiKey/clearApiKey/enabled`；`LlmConfigService.UpdateRequest` 同病，一并处理 |
| **m-8** | `hasReachableKey()` 不解密 → 坏密钥的档案仍被 Seeder 当「可用来源」，复制出一堆同样不可用的档案且不报错 | 改为「密文非空 **且能解密**」；解不开时 `log.warn` 一次并视为不可用 |
| **m-9** | `LlmProviderApiIntegrationTests` 清理不删 `SYS_USER`，第二个用例复用同名 `viewerToken` → 顺序依赖**假红** | 补 `DELETE FROM SYS_USER`（或按用户名唯一后缀），与该类既有清理风格一致 |
| **m-10** | `LlmProfileProviderColumnPersistenceTests.java:299` 对全库断言 `PROVIDER_ID IS NULL` 计数为 0，范围过宽会误伤其它类的残留 | 断言范围收窄到本类自己造的行（按 id 集合） |

**不修并说明理由**（写进报告即可，不要动手）：
- **m-1/m-2/m-3/m-4 性能类**（重复解密、N+1、链上 2N 次查询、回填 O(N×G)）：当前量级（16 条档案 / 2 供应商，实测链构建 6-7ms）不值得引入缓存失效复杂度；**但** m-1「档案列表对每条档案重复解密同一供应商 key」可以顺手做成「一次 `list()` 内按 providerId 记忆化」，若实现成本 ≤ 10 行就做，超过就只写理由。
- **m-5/m-6/m-7 并发类**（无 DB 唯一约束、删除 TOCTOU、并发双默认）：属既有全局惯例（`LlmProfile.name`、`AgentDefinition.name` 同样只有应用层唯一），本轮不单独收紧；m-7 允许加一行 `@Transactional` 若有现成模式。
- **m-11/m-12**：跨类口令耦合是老问题；`CREATED_AT varchar(255)` 是全局设计债。

### 3.2 前端

| 编号 | 问题 | 要求 |
|---|---|---|
| **F1** | AgentsView 档案下拉不标可用性，悬空与旧版混成「未归属供应商」一组 | `option` 文案按可用性追加后缀（`（供应商已不存在）` / `（供应商已停用）` / `（供应商未配置密钥）`）；分组名分开：`旧版档案（未指定供应商）` 与 `供应商已不存在的档案`，措辞与模型档案页对齐 |
| **F2** | `usable()` 与后端 `available()` 不等价（缺 `provider`/`baseUrl` 非空），A 段自检对该类别是盲区 | **判据单点化（裁决：方案 C）**：`ProfileView` 增下发字段 `runtimeAvailable`（boolean，= 后端 `available()` 的求值结果），前端 `usable()` 改为以它为准：`p.runtimeAvailable !== false && p.enabled && p.hasApiKey && p.providerEnabled !== false`（保留后三项只为兼容不带该字段的旧快照）。**不要**用 `hasOwnProperty('provider')` 那类「字段在就校验」的隐式判据——它把「接口今天不下发」变成契约，后端哪天补字段就会静默改行为。配套：`check-profile-chain-parity.mjs` 的 B 段夹具加 `runtimeAvailable`，补用例「`runtimeAvailable:false` ⇒ 不在链上」与「缺省该字段 ⇒ 仍按三项判」；A 段加正则钉住 `addIfUsable` 判定体必须调用 `runtime()` |
 `usable()` 与后端 `available()` 不等价（缺 `provider`/`baseUrl` 非空），A 段自检对该类别是盲区 | ①`usable()` 补齐：`providerId == null` 的行要求 `provider`/`baseUrl` 非空；挂供应商的行要求 `providerEnabled !== false`（已有）**且** `hasApiKey`（已有）；②`check-profile-chain-parity.mjs` 的 B 段夹具加 `provider`/`baseUrl` 字段并补 1-2 条「legacy 行 baseUrl 为空 ⇒ 不在链上」用例；③A 段再加一条正则钉住 `addIfUsable` 判定体必须调用 `runtime()`（禁止退化为只看 `enabled`） |
| **F3** | 悬空/旧版卡片的启停开关必然 400，且失败后开关视觉不回滚 | `dangling`/`legacy` 卡片**不渲染开关**，改为一行「先归位供应商后才能启停」的提示；`toggleProfile` 失败分支显式回写 `event.target.checked`（或 `v-model` + 失败回滚） |
| **F4** | `providerModelCount` 取 max，可能出现「标题 N 个模型」与展开区卡片数不一致 | 显示口径统一用本地列表长度；删除护栏**单独**用 max；两者不等时行内加一行「后端记录 N 条、当前显示 M 条，请刷新」 |
| **F5** | 弹窗不接管焦点、无 `aria-controls`、页面级告警不播报 | 打开弹窗时 `focus()` 首个输入框、关闭时焦点还原到触发按钮；`.modal-backdrop` 背景加 `inert`（或用 `aria-hidden`）；caret 补 `aria-controls` 指向展开区 `id`；`message`/`error` 加 `role="status"`/`role="alert"` |
| **F8** | 删除护栏按钮 `disabled` 时键盘不可达、理由读不到 | 改 `aria-disabled="true"` + 保留可聚焦，点击时提示原因（或把原因直接渲染在行内） |
| **F10** | `.provider-head` 的 `align-items/gap` 被 `.profile-item>header` 覆盖（写了不生效） | 提高到 `.provider-item>.provider-head` 特异性，或删掉失效声明；`.provider-meta` 同 |
| **F11** | providers 返回「成功但非数组」时全部档案被渲染成悬空告警 | 非数组时也置 `providersError`，不要静默 `[]` |
| **F9** | A 段在 `src/main/java` 缺席时静默跳过（Dockerfile 前端阶段就是这种） | 改为「`src/main/java` 目录存在但目标文件缺失 → 失败」；目录不存在才跳过并打印原因。**不要**在 Docker 前端阶段强制失败 |

**不修**：F5 的「AgentsView 弹窗同样问题」属项目级存量模式，本轮只修模型档案页；**写进报告**。

### 3.3 ProfileView 增两个只读字段（裁决，后端实现、前端消费）

`GET /api/llm-profiles` 的每条 `ProfileView` 末尾追加两个**计算得出、不落库**的字段：

| 字段 | 类型 | 含义 | 取值规则 |
|---|---|---|---|
| `runtimeAvailable` | `boolean` | 该档案此刻能否进入故障切换链。**就是后端 `LlmProfileService.RuntimeProfile.available()` 的求值结果**，不许另写一套判据 | 挂供应商 → `profile.enabled && providerEnabled && provider 有 baseUrl && 有 key`；legacy 行（`providerId == null`）→ `profile.enabled && 自身 provider/baseUrl 非空 && 自身 key 非空`；悬空 → `false` |
| `providerPurpose` | `String`，可空 | 所属供应商的用途（`TEXT`/`IMAGE`），供前端标注「配图模型来自图片专用供应商」 | 挂供应商 → 该行 `purpose`；legacy / 悬空 → `null` |

**为什么这样裁决**（而不是让前端自己拼判据）：
- 判据只能有一个真源。前端复刻一遍，就必然有第二次漂移——本轮 F2 正是这么来的。
- 同一份数据还能顺手解决 F1（智能体页下拉的可用性标注直接用 `runtimeAvailable`，不再靠 `providerEnabled`/`hasApiKey` 拼凑）与 §4.3 第②跳的可见性（`providerPurpose === 'IMAGE'`）。
- 两个字段都是**响应里多出来的**，旧前端忽略即可，`AgentFactory` / `RuntimeProfile` / `RuntimeConfig` 形状一律不动。


---

## 4. 用户 ④「系统设置里的 LLM 设置搬到模型档案」

**目标**：`系统设置 → LLM 服务` 整块下线，能力并入「模型档案」。设置页只保留与模型/供应商无关的部分（排版渲染服务、修改登录密码）。

### 4.1 要迁走的东西

`SettingsView.vue:41-58` 的整块 `LLM 服务`，含 13 个字段：

- LLM 侧：`provider` / `baseUrl` / `modelName` / `apiKey` / `clearApiKey` / `enabled` / `temperature` / `maxTokens`
- 图片侧：`imageBaseUrl` / `imageModelName` / `imageApiKey` / `clearImageApiKey`（+ `hasImageApiKey` / `imageApiKeyMasked` 两个只读态）

### 4.2 迁移后的归属（契约，不得自行改）

| 原设置项 | 迁到哪 | 怎么表达 |
|---|---|---|
| `provider` / `baseUrl` / `apiKey` / `enabled` | **供应商** | 已经是供应商的字段，复用；设置页不再有入口 |
| `modelName` / `temperature` / `maxTokens` | **默认档案** | 默认档案就是「默认用哪个模型」。模型档案页已有「设为默认」；本轮把 `temperature`/`maxTokens` 的编辑留在档案弹窗（已有） |
| `imageModelName` | **档案的 `imageModelName`**（已有字段） | 全局图片模型改为「默认档案的图片模型」；`imageCarrier` 已经是「档案优先、全局兜底」，迁移后全局那层只剩空值 |
| `imageBaseUrl` / `imageApiKey` / `clearImageApiKey` | **图片供应商** | 见 4.3 |

### 4.3 图片通道：模型卡片上的**复选框**（用户 2026-11-10 第二次修订）

> **修订记录**
> - 最初设计：`llm_provider.purpose = TEXT|IMAGE`。用户否决：「供应商会同时供应所有类型模型」。
> - 第二版：模型卡片上填「图片模型名称」文本框。用户**再次否决**：「不要这种，采用复选框，选择是否图片模型」。
> - **定稿：模型卡片上一个复选框**「这是图片模型」。勾选即表示这张档案本身就是图片模型，
>   `modelName` 就是图片模型名，端点与密钥来自它所属的供应商。

**数据模型**：`llm_profile` 新增 `is_image_model tinyint(1) NULL`（`Boolean isImageModel`），
与既有 `isDefault` / `isFallback` 同一套写法（`Boolean` 包装 + `@JsonProperty("isImageModel")`）。
存量库由 smart-mybatis 补列，老行是 `NULL` ⇒ 语义等于「不是图片模型」，**无需数据迁移脚本**。

**解析（两跳，全在 `if (config.enabled())` 门禁内）**：

1. **① 复选框**：沿**同一条故障切换链**找第一个 `isImageModel = true` **且可用**的档案
   → 用**它所属供应商**的 `baseUrl` + `apiKey`，图片模型名 = **它自己的 `modelName`**。
2. **①b 旧数据兼容（不删）**：若链上没有勾选的档案，再看是否有档案声明了非空 `imageModelName`
   → 用它所属供应商的端点与密钥 + 该 `imageModelName`。这是第二版留下的落点，
   **只读不改**，保证已经这样配过的部署不炸；界面不再暴露该输入框。
3. **② 回落 `llm_config` 图片三件套**：链上两者都没有时，行为与改造前逐字相同。

**一个供应商同时供文本与图片的标准配法**：在同一个供应商下建**两条档案**——
`deepseek-chat`（不勾选，写正文）与 `gpt-image-1`（**勾选「这是图片模型」**，供配图），
两条共用供应商那一份端点与密钥。

**删除清单**：
- 前端：模型弹窗里的「图片模型名称」文本框**换成复选框**；卡片上的「图片模型」信息行改显示
  「图片模型 · 是/否」或仅在勾选时显示「图片」徽标；`profileForm.imageModelName` 相关读写改 `isImageModel`。
- 后端：`ProfileRequest` **增加** `Boolean isImageModel`；**保留** `imageModelName` 字段——
  它是 ①b 兼容跳的载体，前端须**原值透传、界面不暴露修改入口**（见下方裁决）。
  `ProfileView` **增加** `isImageModel`。

  > **裁决（Lead 2026-11-10，修正本契约上一版措辞）**：`imageModelName` **原值透传，不传空串**。
  > 原措辞「界面不再传非空值」是写错的：该字段是 ①b 的载体，若传空串，
  > 用户只改一次温度就会把老部署的图片配置抹成 `null` —— 那是**数据丢失**而非兼容。
  > 界面上确实不暴露它的输入框，但已有值必须原样带回去（含 toggle 那条整条 PUT）。

**保留**：`llm_profile.image_model_name` 列与 `LlmConfig` 图片三件套（①b 与 ② 跳）。

**卡片提示文案**（两跳口径，不提文本框）：
> 「勾选后，这张档案本身就是图片模型：配图时用它所属供应商的端点与密钥，模型名就是上面的
> 「上游模型名称」。同一个供应商通常同时提供文本与图片模型，在它下面建两条档案即可——
> 一条写正文，另一条勾选这里供配图，共用一份端点与密钥。」

### 4.4 API 层

- **保留** `GET /api/settings/llm`（兼容旧脚本与回滚；前端不再渲染该块）。
- **保留** `PUT /api/settings/llm`（同上；且第一轮已验证「连续两次相同保存不新增供应商」）。
- **设置页不再调用它们** —— 这是本轮唯一的行为变化。删除 `SettingsView.vue` 里该块的表单、`saveLlm()` 与该块的 `llm` reactive（保留 `load()` 里对其他配置的读取）。
- 若删除后 `SettingsView.vue` 有未使用的 import / 死代码，一并清理。

### 4.4b 连带文案（Lead 独立审查发现，不在两份复查报告里）

设置页一旦没有 LLM 入口，所有**仍在把用户指向「系统设置」解决 LLM 问题**的文案就变成了死路——用户照着做会找不到入口。逐处改掉，指向「模型档案」：

| 位置 | 现在的文案 | 改成 |
|---|---|---|
| `AgentFactory.java:309` | `LLM 尚未在系统设置中启用或未配置 API Key` | `LLM 尚未配置可用的模型档案（请到「模型档案」页检查默认档案与它的供应商）` |
| `ArticleAiService.java:262` | 同上（SSE 错误事件） | 同上 |
| `ArticleAiService.java:639` | 同上 | 同上 |
| `ImageGenerationService.java:96` | `尚未在系统设置中配置图片模型` | `尚未配置图片模型（请在「模型档案」页为某个档案填写图片模型，或配置图片专用供应商）` |
| `ModelProfilesView.vue:321` | 图例里「配图沿用『系统设置』里的图片模型」 | 按 §4.3 的新口径改写（档案自身 → 图片供应商 → 旧配置回落） |
| `ModelProfilesView.vue:470` | 模型弹窗里「图片仍走『系统设置』里的图片模型」 | 同上 |
| `AgentsView.vue:284` | `（来自系统设置）` | 按 §4.3 改成「（来自图片供应商）」/「（来自旧配置）」等准确措辞 |
| `AgentsView.vue:15`、`:117` | 注释里说「系统设置里的图片模型」 | 同步改注释 |

**不改**的：`MarkFlowRenderService.java:108/111`、`TaskExecutionService.java:597`、`SkillsView.vue:201` 里指向「系统设置 → 排版渲染服务」的文案——排版渲染**仍留在设置页**，这些是对的。`SettingsView.vue:37` 的页面副标题「集中管理 AI 服务和账号安全」也要改（AI 服务已不在这里）。

> 为什么这条值得单列：改文案本身是一分钟的事，但**漏掉**的后果是「用户按报错去了设置页，发现没有入口，然后来问为什么」——而这正是本轮第 4 条需求最容易产生的副作用。

### 4.5 迁移与兼容

1. **已有部署**：`llm_config` 的 LLM 四件套此前已由 `syncDefaultFromConfig` 写透到默认档案 + 默认供应商（第一轮）。本轮**不需要数据迁移**，只下线入口。
2. **回滚**：回滚到上一版本后 `llm_config` 仍有值（因为它一直在写），设置页恢复可见 —— 所以 `syncDefaultFromConfig` 的写入**必须继续**（`PUT /api/settings/llm` 保留的意义）。
3. **文档**：`README_CN.md` 的「第二步」与「第四步图片模型」必须改写为「在模型档案页维护」；`docs/dev/llm-provider-refactor.md` 增一节说明本轮覆盖了它的 §5.3。

---

### 4.6 升级路径实证（Lead 已实测，2026-11-10 15:00）

这份证据补上了两份复查报告都明确列为「未验证」的最大空白（后端报告 §3 未验证项 2、前端报告 §4 未验证项 1）。

**做法**：造 `wechat-article-upgrade-rehearsal` 库 —— 用 `wechat-article`（dev，旧形态）的 29 张表 schema 铺底（**无** `LLM_PROVIDER` 表、**无** `LLM_PROFILE.PROVIDER_ID` 列），灌入 4 条存量档案 + 1 行 `llm_config`，`API_KEY_ENCRYPTED` 故意写成不可解密值，然后让当前代码连它启动（端口 18083）。

**结果一：DDL 增量升级成立**
- `LLM_PROVIDER` 表已创建；`LLM_PROFILE.PROVIDER_ID bigint NULL` 已补上
- **原有 15 列的类型与宽度逐字未变**（`NAME varchar(255)` / `PROVIDER varchar(50)` / `BASE_URL varchar(500)` / `MODEL_NAME varchar(200)` / `API_KEY_ENCRYPTED text` / `CREATED_AT varchar(255)` …）
- **4 条存量数据完好无损**，一条没丢、一列没改
- 应用**启动成功**（12.6 秒），没有因为老库而起不来
- ⇒ smart-mybatis 在真实存量库上只做加法，没有误发 `MODIFY COLUMN`

**结果二：回填在坏密钥下「静默全跳过」（新增缺陷 B4）**
- 4 条档案各一条 WARN「该档案的端点与密钥无法用于归组（跳过该行）」，根因 `IllegalStateException: 敏感信息解密失败` → `IllegalArgumentException: Illegal base64 character 2d`
- 终态：**16/16 条档案 `PROVIDER_ID` 全为 NULL**，`LLM_PROVIDER` 表 0 行（种子档案也因无可继承绑定而全 NULL）
- 「跳过坏行」这个**行为本身是对的**（不该把解不开的密钥归组），但**汇总日志说「0 条归组 / 0 个供应商」，与管理员的读法「没有需要回填的行」无法区分**
- 要求：回填结束时，若存在「因解密失败被跳过」的行，汇总日志必须是 **WARN 级并明确计数**（例如「N 条档案因密钥无法解密被跳过回填，请检查 APP_SECRET_KEY」）

**结果三：B3 的后果从「读代码推理」升级为「实测确认」**

同一坏库上（管理员可正常登录，LOGIN 返回 200）：

```
GET /api/settings/llm   => HTTP 500   *** 非 200 ***
GET /api/llm-profiles   => HTTP 500   *** 非 200 ***
GET /api/llm-providers  => HTTP 200  {"success":true,"data":[]}
```

**影响**：`APP_SECRET_KEY` 一旦轮换或写错，管理员**连唯一的修复入口（模型档案页 / 设置页）都打不开**——两个页面全 500，而供应商列表因为是空表反而 200。这是本轮最值得修的一条。
**验收**：同库下 `GET /api/settings/llm` 与 `GET /api/llm-profiles` 必须 **200**，坏行显示为「不可用 / 无法解密」而不是整页 500。

**演练环境已清理**：`wechat-article-upgrade-rehearsal` 已 DROP，18082/18083 已停，18081 用户实例健康（HTTP 200，PID 42216 未变），`target/classes` 未被本次演练改动（后端至今零编译）。

## 5. 不改的东西（明确边界）

- 不动 `llm_profile` 的旧三列（`provider` / `base_url` / `api_key_encrypted`），不清空、不删除。
- 不动 `failoverChain` 的四跳**调用序列**（`bound → defaultProfile() → fallbackProfile() → profile`），只允许改判定体。
- 不动 `RuntimeProfile` / `RuntimeConfig` 的 record 形状（`AgentFactory` / 既有调用点零改动）。
- 不引入 DSH 的上下文窗口 / 输入类型 / 推理强度 / 拖拽排序（第一轮已划出范围，本轮维持）。
- 不做模型发现的**自动定时刷新**，只做用户显式点击。
- 不动 `compose.yaml`（11-04 的既有本地改动，与本轮无关，单独评审）。

---

## 6. 写范围（互不重叠）

| 任务 | 写范围 |
|---|---|
| 后端 | `src/main/java/**`、`src/test/java/**` |
| 前端 | `webui/**`（含 `scripts/`） |
| 文档 | `README_CN.md`、`docs/dev/**`（只改本轮文档，不动两份 review 报告与两份第一轮文档的既有结论，只**追加**本轮章节） |

---

## 7. 验收判据（可执行）

1. **卡片不溢出**：§1 第 3 条的四个视口宽度断言全过（浏览器实测，给按钮矩形数值）。
2. **模型发现可用**：`GET /api/llm-providers/{id}/models` 对真实供应商返回模型名列表；对无 key 供应商返回中文错误；后端单测覆盖 §2.2 第 6 条全部场景；日志无明文 key（实测抓一次日志）。
3. **加入口**：折叠态的供应商行可见「为供应商 X 新增模型」按钮，点击后模型弹窗 `providerId` 已预置（浏览器实测）。
4. **设置页无 LLM 块**：`/settings` 只剩「排版渲染服务」「修改登录密码」；`GET /api/settings/llm` 仍 200 且返回原形状（curl 实测）。
5. **图片供应商**：`purpose='IMAGE'` 的供应商能被创建并在模型档案页显示「图片」徽标；`imageRuntime` 的回落顺序有单测覆盖（①档案 → ②图片供应商 → ③`llm_config`）。
6. **复查清单关闭**：§3.1 与 §3.2 表中每一条都有「已修 + 证据」或「不修 + 理由」。
7. **全量构建与测试通过**：`mvn -o test` 全绿（583 + 本轮新增）；`cd webui && npm run check && npm run build` 全绿。
8. **无泄密**：本轮新增/改动的接口，响应里不含明文 key；日志里不含明文 key（含模型发现失败路径）。

---

## 8. 实施结果（2026-11-10 收尾，数字均为实测）

### 8.1 构建与测试

| 项 | 结果 |
|---|---|
| 后端全量 `./.mvn/mvn-local.sh -o test` | **626 例 / 0 失败 / 0 错误 / 0 跳过**，BUILD SUCCESS，71 个测试类（基线 583 / 69 → +43 例、+2 类） |
| 测试时段 | 2026-11-10 15:46:10 → 15:54:24（Asia/Singapore） |
| 前端 `npm run check` | ✓ 共享工具导入自检（10 个导出符号）；✓ 档案链一致性自检（后端 4 跳结构 + **20** 个行为用例） |
| 前端 `npm run build` | ✓ built in 882ms |

期间出现过一次 626/1 失败（`LlmProfileFallbackColumnPersistenceTests`），定性为**契约 m-8 判据收紧暴露了过时的测试夹具（伪密文）**，修的是夹具不是产品行为；单类连跑 3 次（15:54:35 / 15:55:07 / 15:55:36）均 4/4 全绿。

### 8.2 §1 卡片溢出（用户 ①）

Lead 在 2124 视口独立实测（15 张卡满网格）：

| 状态 | 越界按钮数 | 最严重溢出 |
|---|---|---|
| 修复前（旧产物） | **15** | 「删除档案」溢出 **28.9px** |
| 修复后 | **0** | 最外按钮距卡片右缘内缩 **18.1px** |

四视口全验（CDP `Emulation.setDeviceMetricsOverride`）：

| 目标视口 | innerWidth | scrollWidth | 横向滚动条 | 越界按钮 |
|---|---|---|---|---|
| 1280 | 1280 | 1263 | 无 | 0 |
| 1440 | 1440 | 1423 | 无 | 0 |
| 1920 | 1920 | 1903 | 无 | 0 |
| 2124 | 2124 | 2107 | 无 | 0 |

实现：`flex-wrap:wrap` + `min-width:0` + container query（296px 阈值 = 2×102.2 + 2×36 + 3×6 ≈ 294）把两个长按钮降级为 36px 图标按钮，保留原 `title`/`aria-label` 语义；**未使用** `overflow:hidden`。

### 8.3 §2 模型发现（用户 ②）

Lead 用本地 mock 上游（返回乱序且重复的 `mock-model-b, mock-model-a, mock-model-a`）端到端验证：

| 场景 | 结果 |
|---|---|
| 正常解析 | `mock-model-a, mock-model-b`（**去重 + 字典序**，符合 §2.2 第 3 条） |
| `baseUrl` 已以 `/v1` 结尾 | 200，未拼成 `/v1/v1/models` |
| 无 API Key | 400「该供应商未配置 API Key，请先配置 API Key 后再获取可用模型」 |
| 上游返回 401（真实供应商） | 400「供应商返回错误（HTTP 401），未能获取模型列表。请检查 Base URL 与 API Key 是否正确」 |
| 常量态入口 | `api.anthropic.com` **折叠态**下行内仍渲染「为供应商 api.anthropic.com 新增模型」 |

后端单测 `ModelDiscoveryClientTest` 11/11（真实 socket：超时 / 1 MiB 上限 / 302 不跟随 / 不可达 / 密钥不进 path 等）。

### 8.4 §3 复查必修项

- **B3（本轮最重）**：坏密文进真库后，`GET /api/settings/llm` 与 `GET /api/llm-profiles` 修复前均 **500**、修复后均 **200**，坏行显示「无法解密（请检查 APP_SECRET_KEY）」，响应不含密文（`LlmBrokenCiphertextReadPathTests` 4/4，Lead 亦独立实测两个 200）。
- **B1/B2/B4**：`backfillEntryEnablesProvidersByKeyPresenceOnly`、`writeThroughNeverRewritesAProviderReferencedByNonDefaultProfiles`、`nullIsDefaultStillCountsAsAReferenceForTheRewriteGuard`、`writeThroughStillRewritesTheDefaultRowWhenOnlyTheDefaultProfileUsesIt` 等用例覆盖；B4 的回填汇总日志已区分「无待处理行」与「N 条被跳过」。
- **M-1/M-2/M-4/m-8/m-9/m-10** 均有对应用例（`LlmProviderServiceTest` 30 例、`LlmProfileServiceTest` 28 例等）。
- **前端 F1/F2/F3/F4/F5/F8/F9/F10/F11** 均带浏览器实测证据；A3/A4 的拦截力经**篡改实验**独立复现（本案 lead 亲验：`available→enabled`、不调 `runtime()`、去掉 `baseUrl` 三种篡改全部 FAIL 被拦下）。
- **未修并说明理由**：m-1..m-3 性能、m-5/m-6/m-7 并发、m-11/m-12；F5 的 AgentsView 同类问题（项目级存量模式）。

### 8.5 §4 设置页下线（用户 ④）

| 检查项 | 结果 |
|---|---|
| 设置页剩余区块 | 「排版渲染服务」「修改登录密码」 |
| 有 LLM 表单 / API Key 输入 / 模型输入 | 均无 |
| 页面是否仍有「LLM 服务」字样 | 无 |
| 副标题 | 已改为「管理排版渲染服务与账号安全。模型与供应商已迁到「模型档案」页…」 |
| 设置页是否仍请求 `/api/settings/llm` | **不再请求**（实测 `apiCallsToLlm:0`） |
| `GET /api/settings/llm` | **仍 200**，13 个字段全保留（含图片三件套），回滚能力完整 |
| `purpose=IMAGE` 供应商 | 可创建；UI 徽标实测 `purpose-image`→「图片」、`purpose-text`→「文本」 |

### 8.6 升级演练（§4.6 的原始记录）

旧形态库（29 表、无 `LLM_PROVIDER`、无 `PROVIDER_ID`）→ 启动当前代码：表自动建、`PROVIDER_ID bigint NULL` 自动补、**原 15 列类型宽度逐字未变**、4 条存量数据完好、应用启动成功（12.6s）。回填在坏密钥下全跳过（已由 B4 修复为可感知）。演练库已 DROP，18082/18083 已停。

### 8.7 遗留项（明确未做）

1. `LlmProfileFallbackColumnPersistenceTests` 的 `@BeforeEach/@AfterEach` 兜底快照仍为全表——无真缺陷支撑，按最小改动原则不改（lead 已撤回该重构建议）。
2. A5 自检无法识别「`runtimeAvailable` 整字段改名」——源码上「改名」与「未实现」不可区分；要彻底堵死需后端提供稳定锚点，另议。
3. 前端 AgentsView / SkillsView 等页面的弹窗仍无焦点接管与 `inert`（项目级存量模式）。
4. `AGENTS`/部署侧的默认凭据与 CRLF 治理不在本轮范围，另立。

### 8.8 用户修订：撤销供应商级「用途」（2026-11-10 16:05）

**用户原话**：「去掉这里，文本图片可在模型卡片设置，因为供应商会同时供应所有类型模型」

**判断成立**：一个网关通常同时提供 `gpt-4o`（文本）与 `gpt-image-1`（图片）。把「用途」做成供应商属性后，
同一个供应商就无法同时供两类模型，用户被迫把同一份端点+密钥拆成两个供应商——**正好违背本轮第一条需求
（消除 Base URL 与 Key 的重复录入）**。这是设计错误，不是命名问题。

**已撤销**：
- `llm_provider.purpose` 字段、`ProviderView.purpose`、`ProviderRequest.purpose`、
  `purposeOf` / `resolvePurpose` / `PURPOSE_*`、`ProfileView.providerPurpose`
- `ImageProviderChannel` / `imageProviderChannel` / `imageRuntimeFromProvider`（原第②跳）
- 前端「用途」单选、「文本/图片」徽标、`purposeOf` / `purposeLabel` / `PURPOSES`、
  `AgentsView` 的 `providerPurpose === 'IMAGE'` 分支

**改由模型卡片承载**：`llm_profile.image_model_name`（本来就有）成为图片通道的**唯一主路径**。
典型配法写进 README 与卡片提示：同一供应商下建两条档案——`deepseek-chat`（正文）与
`gpt-image-1`（模型名 + 图片模型名称都填，配图），共用一份端点与密钥。

**图片解析收敛为两跳**：① 链上首个声明 `imageModelName` 的档案（用其所属供应商的端点+密钥）
→ ② 回落 `llm_config` 图片三件套。门禁 `config.enabled()` 不变。

**保留**：`LLM_PROVIDER.PURPOSE` 表列不删（smart-mybatis 只加不减），实体不再映射 → 无害孤儿列。

**教训**：设计新属性前要先问「这属性属于哪一层」。端点与密钥属于供应商，**模型名属于模型**；
把模型的属性上提到供应商层，就会把「共用一份凭据」变成「必须拆成多份」。

### 8.9 第三次修订：图片模型改为**复选框**并落地（2026-11-10 17:50）

**用户原话**：「不要这种，采用复选框，选择，是否图片模型这种」
（针对第二版的「图片模型名称」文本框）。

**落地形状**
- `llm_profile` 新增 `IS_IMAGE_MODEL tinyint(1) NULL`（`Boolean isImageModel`，`@JsonProperty("isImageModel")`）
- 模型弹窗：**复选框「这是一个图片模型」**，位于「上游模型名称」之后、Temperature 之前；**无任何图片文本框**
- 卡片：仅在勾选时显示「图片」徽标（不再常驻「图片模型 是/否」一行）
- 解析：① 链上首个 `isImageModel=TRUE` **且可用**的档案 → 用它所属供应商的端点密钥，图片模型名 = 它自己的 `modelName`；
  ①b 链上无人勾选时，再用遗留 `imageModelName`（**只读不改**，保老部署）；② 回落 `llm_config` 三件套。全在门禁内。

**Lead 独立实测（18081 真实实例）**
| 项 | 结果 |
|---|---|
| `POST {"isImageModel":true}` → 返回 | `true` |
| `GET` 读回 | `true` |
| 整条 `PUT` 停用（带 `isImageModel:true`） | `enabled:false`、**`isImageModel` 仍 true**（toggle 不抹标志） |
| `PUT {"isImageModel":false}` | `false` |
| 存量 16 行（NULL） | 全部归一为 `false`，无需迁移 |
| 弹窗控件 | `type:checkbox`、默认未勾选、**无图片文本框** |
| 徽标边界 | 建 1 条勾选档案 ⇒ 16 张卡中**恰 1 张**有「图片」徽标；删除后 0 |

**测试**：后端全量 **636 例 / 0 失败 / 73 类**；其中 `AgentApiIntegrationTests.imageModelCheckboxRoundTripsOverHttp`（11/11）
走的正是上面这条 HTTP 闭环。前端 `check` 全绿，B 段行为用例 **20 → 24**（新增 4 条专钉 ①/①b 优先级与取值来源，
并通过篡改实验证明非摆设）。

**两个易错点（记录）**
1. **toggle 走整条 PUT**，必须带上 `isImageModel` 原值，否则用户点一次启停就会抹掉勾选（已有 HTTP 用例钉住）。
2. **`ProfileRequest` 不需要 `@JsonProperty`**：Jackson 对 record 组件按参数名反序列化，`isImageModel` 直接能对上；
   `LlmProfile` 实体上那个标注是为了**输出**时避免 `is` 前缀 getter 把它写成 `imageModel`。

### 8.10 上游模型名称：原生 datalist 换成自绘下拉（2026-11-10 18:20）

**用户原话**：「这个下拉跟当前 ui 不匹配」（附截图：原生 datalist 弹出层为系统默认样式）。

**原因**：`<datalist>` 的弹出层由**浏览器绘制**，页面 CSS 完全够不到——它会以系统样式浮在设计系统之上，
配色/圆角/字体/高亮都不受控。§2.2 当初选 datalist 是为了「零成本可自由输入 + 可选」，代价就是这个。

**改为自绘 combobox**（`.model-combobox`，`ModelProfilesView.vue` + `style.css`）：
- 列表用 `--paper` 白底、`--line` 边框、10px 圆角、`rgba(20,38,31,.13)` 阴影，与弹窗内其它控件同一套观感
- 高亮用 `--green-soft` 底 + `--green-dark` 字 + 左侧 2px 绿色细条（与侧栏 current 项同一视觉语言）
- 有值时输入框右侧出现清空按钮（`.model-combobox-clear`）
- ARIA 补齐：`role="combobox"` + `aria-autocomplete="list"` + `aria-expanded` + `aria-controls` + `role="option"` + `aria-selected`

**交互口径（三条都是实测踩出来的，不是设计出来的）**：
1. **聚焦即展示全部候选，打字后才过滤**。最初按「输入框当前值」无条件过滤，
   结果编辑已有档案时（值 `model-a`）一聚焦只剩 `model-a` 自己——「换一个模型」这个下拉的主用途没了。
   现在用 `modelSuggest.typing` 区分「用户正在敲字」与「聚焦/程序回填」。
2. **每次输入都重置高亮**。否则候选集变短后旧下标越界，方向键绕回 0，
   会出现「明明输入了 glm，回车却选中 agnes-2.5-flash」。
3. **Esc 分层**：下拉开着时 Esc 只收起下拉，不关弹窗（弹窗级 `onKeydown` 里先判 `modelSuggest.open`）。
   否则刚敲一半的模型名会连弹窗一起丢。

**验证（18081 真实实例，CDP 真实鼠标/键盘，非 JS 派发）**
| 场景 | 实测 |
|---|---|
| 编辑态聚焦（值 `model-a`） | 15 个候选全出（修复前只剩 1 个） |
| 输入 `glm-5` | 过滤到 `glm-5.3` / `glm-5.3-flash` |
| ArrowDown ×2 | 依次高亮过滤后的两项（修复前会跳到 `agnes-2.5-flash`） |
| Enter | 写回 `glm-5.3-flash`，列表收起 |
| Esc | 只关下拉，弹窗存活 |
| Tab / 点外部 | 只收起，不选中 |
| 计算样式 | 高亮 `rgb(229,243,236)` 底 + `rgb(21,89,67)` 字 + 2px 内嵌绿条 |

**保留**：`webui/scripts/check-profile-chain-parity.mjs` 未受影响（24 例仍绿），本改动不触及档案链语义。

### 8.11 下拉不再列「已经添加过的模型」（2026-11-10 18:30）

**用户原话**：「已添加过的模型不要在下拉中出现」。

**改法**（`ModelProfilesView.vue` 的 `profileModelOptions`）：
- 该供应商下已有档案的 `modelName` 组成 `taken` 排除集，**不再贡献候选**；
- 候选只来自 ① 本次探测结果（`profileModal.discovered`），逐条减去 `taken`；
- **编辑态例外**：正在编辑的那条档案自己的模型名要从 `taken` 里剔除、并补回候选，
  否则用户打开编辑连「原本是哪个模型」都看不到（它已在库里，会被当占位排掉）。

**配套文案**（都是必要条件，否则用户会以为下拉坏了）：
- 字段下方提示补「列表只显示还没添加过的」；
- 探测成功后的状态行改成「已获取 N 个模型，M 个已添加，下拉里还有 K 个可选」；
  全部加过时改为「已获取 N 个模型，但都添加过了，没有可选的」。

**顺带修掉一个 Vue 响应式陷阱**（`openModelSuggest`）：
判断「有没有候选」**不能**读 `filteredModelOptions.value`。`onModelFocus` 把 `typing` 置 false 后
**同一个同步块内**立刻调用它，此时 computed 尚未重算，读到的还是上一次（`typing=true`、关键词非空）
的过滤结果 ⇒ 「聚焦时列表不弹」。改为读未过滤的 `profileModelOptions`，它不含关键词逻辑、不受 `typing` 影响。

**实测（18081，真实点击 + 真实键盘）**
| 场景 | 结果 |
|---|---|
| nexus 下探测 10 个模型，其中 `gpt-6-sol` 已建档案 | 候选 **9 个**，`gpt-6-sol` 被排除 |
| 状态行 | 「已获取 10 个模型，**1 个已添加**，下拉里还有 9 个可选」 |
| 编辑已有档案（`model-a`，同供应商另有 14 条） | 候选只有 `model-a` 自己（自己必须可见） |
| 新建弹窗聚焦（未探测） | 不弹空壳列表 |

> 排查记录：中途一次实测「排除没生效」，真因是**页面外**用 API 建了档案、`profiles.value` 没刷新，
> 不是过滤逻辑错。刷新页面后同一场景立即通过。这条值得记住——用浏览器外的手段造数据后，
> 必须先 reload 再验证依赖页面状态的行为。
