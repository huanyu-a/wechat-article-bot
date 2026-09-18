# 图片模型按档案配置（配图师模型误会的修复）

> 起于一条用户反馈：「配图师的模型应该是 LLM 服务中设置的图片模型，而不是其它模型，或者在模型档案中增加图片模型档案」。
> 本文记录**核实结论**（反馈的第一半是误会、第二半是真实缺口）、**实施内容**与**验收证据**。

## 一、结论速览

| 问题 | 结论 |
| --- | --- |
| 配图实际用的模型是不是「别的模型」？ | **不是**。配图一直用的是全局设置里的图片模型 `step-image-edit-2`（`LLM_CONFIG.IMAGE_MODEL_NAME`），与智能体绑定的档案无关 |
| 那用户看到的「模型」是什么？ | 智能体卡片上显示的是**文本模型档案**（配图师绑定 id=1「默认配置」/ hy4-preview）。它决定的是「谁来写提示词、谁来调工具」，不决定「谁来画图」 |
| 真实缺口在哪？ | 界面上**没有任何地方**表达「这个智能体的配图模型是哪个」；且图片模型**只能**全局配一个，无法按智能体区分 → 采纳反馈的第二方案 |
| 本轮做了什么 | `LLM_PROFILE` 增加**可空**的 `image_model_name`；解析顺序为「档案声明了就用它 → 否则回落全局图片三件套」；档案页可编辑、智能体卡片显示实际生效的配图模型及其来源 |

**一句话**：图片渲染路径本来就是对的，但「模型档案」这个概念此前只管文本模型 —— 本轮把它扩展到图片，顺带把界面上那个让人误会的空白补上。

## 二、核实过程（先证伪，再动手）

### 2.1 图片渲染链路确实用的是全局图片模型

`ImageGenerationService.generate()/edit()` 构造请求体时用的就是 `config.imageModelName()`：

```java
byte[] body = MAPPER.writeValueAsBytes(Map.of(
        "model", config.imageModelName(), "prompt", prompt,
        "size", "1024x1024", "n", 1, "response_format", "b64_json"));
```

改造前 `requiredConfig()` 读的是 `LlmConfigService.runtime()`，而它只解析**默认档案**，图片三件套始终取自 `llm_config`。

### 2.2 开发库实测值（2026-09-16）

```sql
SELECT BASE_URL, MODEL_NAME, IMAGE_BASE_URL, IMAGE_MODEL_NAME, IMAGE_API_KEY_ENCRYPTED, ENABLED
FROM LLM_CONFIG;
```

| BASE_URL | MODEL_NAME | IMAGE_BASE_URL | IMAGE_MODEL_NAME | IMAGE_API_KEY | ENABLED |
| --- | --- | --- | --- | --- | --- |
| `https://nexus.bx9y.com.cn` | `hy4-preview` | NULL | **`step-image-edit-2`** | NULL | 1 |

- `IMAGE_BASE_URL` 为 NULL → 图片请求打到默认档案的 baseUrl
- `IMAGE_API_KEY_ENCRYPTED` 为 NULL → 图片密钥复用默认档案的 key
- 图片生成确实在工作：`ASSET` 表当天有 10 条 `AI_GENERATED` 记录（id 236–246）

### 2.3 配图师绑定的档案只管文本

```sql
SELECT ID, CODE, STAGE, LLM_PROFILE_ID FROM AGENT_DEFINITION;
```

| ID | CODE | STAGE | LLM_PROFILE_ID |
| --- | --- | --- | --- |
| 5 | `builtin_illustrator` | ILLUSTRATION | 1（「默认配置」/ hy4-preview） |

`LLM_PROFILE_ID` 的语义是「这个智能体用哪个**文本**模型」，档案表里此前**没有**任何图片维度 —— 所以「配图师的模型是别的模型」这个印象，来自卡片上那个标签与「配图」二字的自然联想，而不是运行时行为。

### 2.4 顺带排除的一个嫌疑

子智能体（配图师）是否被错配到主编的模型上？**否**。`DelegateTools` 按 `CODE` 解析子智能体，每个子智能体走自己的绑定（`AgentFactory.CODE_ILLUSTRATOR` → `agentDefinitionMapper.findByCode` → 自己的 `LLM_PROFILE_ID`）。

## 三、实施内容

### 3.1 数据模型（唯一的结构变更）

`LLM_PROFILE` 新增一列，由 smart-mybatis 启动时自动补齐：

| 列 | 类型 | 约束 |
| --- | --- | --- |
| `IMAGE_MODEL_NAME` | `VARCHAR(255)` | **可空**；为空 = 本档案不指定图片模型 |

**为什么只加模型名**：图片三件套里只有模型名是「换个模型」这一诉求的载体；端点和密钥沿用全局（同一网关、同一个 key），多带两列只会扩大迁移面。

**为什么必须可空**：存量档案补列后全是 NULL，而 NULL 正是「本档案不指定」的表达（也是「存量部署升级后配图行为不变」的依据）。

**为什么实体字段不加 `@TableField`**：`imageModelName` 这个字段名被 `LlmConfig` 与 `LlmProfile` 两处共用，而 smart-mybatis 的列声明缓存按**字段名**单键共享 —— 两处声明文本不一致时结果取决于实体初始化顺序，同步还会对真实列发 `MODIFY COLUMN`。两处都不加注解（同为默认 `len=255`），与实测列宽 `varchar(255)` 同向。

### 3.2 解析顺序

```
沿故障切换链找第一个声明了 image_model_name 的档案（绑定 → 默认 → 兜底 → 其余已启用）
  找到 → 用它的 baseUrl + image_model_name + apiKey（三者同源，不跨供应商拼接）
  没找到 → 回落 llm_config 的图片三件套（= 改造前的唯一来源）
```

复用同一条 `failoverChain` 而不是另写一套可用性判断：图片模型与文本模型面对同一个现实 —— 档案被停用/删掉/没配 key 时就不该再被使用。链上每一环都已过滤过，复用比另写一套更不容易漂移。

**全局 `enabled` 门禁原样保留**：改造前 `imageAvailable()` 要求 `llm_config.enabled` 为真，`ImageRuntime.enabled` 承接了这一条 —— 关掉 LLM 的部署不该还能生图。

### 3.3 透传路径

```
AgentFactory.CODE_EDITOR / CODE_SCHEDULED_CREATOR
  → ArticleAiService.imageProfileId(code)
ScheduledAgentFactory.build/buildCandidates（含 DELEGATE 委托的配图师）
  → ScheduledAgentFactory.imageProfileId(definition)
      → ArticleMediaTools.create(..., imageProfileId)
          → GenerateImageTool / EditImageTool
              → ImageGenerationService.generate/edit(..., profileId)
                  → LlmConfigService.imageRuntime(profileId)
```

`imageProfileId(definition)` 在**定义停用**时返回 null：停用的智能体走内置兜底装配（工具组由 stage 决定），那条路径上它已经不是「这个智能体」了。

### 3.4 界面

- **模型档案页**：卡片多一行「图片模型」（未设置时显示「跟随全局图片设置」）；编辑表单多一个「图片模型名称」输入框，留空即不指定。legend 补一条说明。
- **智能体卡片**：具备 MEDIA 工具组（能生图/修图）的智能体多一行「配图模型：X（来自档案 / 来自系统设置）」—— 直接回答「这个智能体配图到底用哪个模型」。
- **停用/启用开关**：`toggleProfile` 提交的是整条 PUT，后端 `apply()` 会把缺省字段当「清空」，因此必须原样带上 `imageModelName`，否则一次点开关就会悄悄抹掉已配好的图片模型。
- **null 归范（审查后补，见 §八）**：存量档案该列是 NULL，而 `Object.assign` 会把 `blankProfile()` 的 `''` **覆盖**成 `null`（null 会覆盖，不是被跳过），于是 `profileForm.imageModelName.trim()` 抛 TypeError，表现为「编辑任何存量档案都存不进去」。`openProfile` 与 `profilePayload` 两处都做 `|| ''` 归范。
- **前端档案链与后端逐跳同口径（审查后补，见 §八）**：卡片上的「配图模型」抄的是后端 `failoverChain` + `addIfUsable` 的完整判据（绑定 → 默认 → 兜底 → 其余已启用按 id 升序；每环要求 `enabled && hasApiKey`，按 id 去重）。少抄一跳或漏掉可用性过滤，界面就会报出一个后端根本不会用的模型名 —— 比不显示更糟，因为这张卡片存在的唯一目的就是如实告知。

## 四、测试与闸门

| 用例 | 覆盖点 |
| --- | --- |
| `LlmProfileServiceTest`（+7 例，共 13） | 档案链顺序/去重/跳过不可用者；`imageCarrier` 绑定优先、沿链下落、全链无人声明返回 null、空白不算声明、停用档案声明的图片模型失效 |
| `LlmConfigServiceImageRuntimeTest`（新增 7 例 + 审查后 2 例，共 9） | 档案优先且端点/密钥同源；未声明（载体在但没声明）/未绑定/档案不可用三种情况回落全局；全局未配或 `enabled=false` 时不可用；**档案路径也要过全局门禁**；空白模型名不算声明；档案路径不泄露全局模型 |
| `ArticleMediaToolsImageProfileTests`（新增 5 例） | 生图/修图两条传参路径都真的把档案 id 交给图片服务；null 保持 null；只读工具在「带档案」与「不带档案」两次构造下逐位相同；存量三参重载仍等价 |
| `LlmProfileImageModelColumnPersistenceTests`（新增 4 例，打真库） | 列存在且可空、与 `LLM_CONFIG.IMAGE_MODEL_NAME` 类型同向；NULL 往返；整行更新不丢图片模型；不干扰默认/兜底查找 |
| `AgentJsonContractTest`（+1 例） | `imageModelName` 的序列化契约（含 null 必须在场） |
| `EntityColumnDeclarationConsistencyTest`（既有） | 跨两张表的同名字段声明一致 |

**闸门**：`.mvn/mvn-local.sh -o test` 全绿（基线 401 → 424 → 审查修复后见 `target/gate-image-model-final.log`）；前端 `npm run check:imports` 通过 + `vite build` 成功。

> **两处 mock/替身的注意点（审查后补）**：`LlmConfigServiceImageRuntimeTest` 的替身必须复刻 `imageCarrier` 的真实判据（enabled + 有 key + 模型名非空白），否则那一跳被架空，「档案不可用 → 回落全局」的用例会退化成与「没绑定档案」同一条路径；需要打「载体被强行喂入」的第二道防线时用 `forcedCarrier`。

## 五、验收证据

### 5.1 新列落地（真库）

```sql
SELECT COLUMN_TYPE, IS_NULLABLE FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='wechat-article' AND TABLE_NAME='LLM_PROFILE'
  AND COLUMN_NAME='IMAGE_MODEL_NAME';
```

预期：`varchar(255) | YES`。由 `LlmProfileImageModelColumnPersistenceTests` 在测试库钉住；存量库的补列由 smart-mybatis 完成（该用例的边界见其类注释）。

### 5.2 API 往返

```bash
# 读：档案列表应带 imageModelName 字段（未设置时为 null）
curl -s -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8081/api/llm-profiles

# 写：给某档案设置图片模型 → 读回应为同一个值
curl -s -X PUT -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  --data-binary @target/scratch/profile-update.json \
  http://127.0.0.1:8081/api/llm-profiles/1
```

### 5.3 存量行为不变的证据

默认档案（id=1）的 `IMAGE_MODEL_NAME` 为 NULL 时，`imageCarrier` 沿链找不到声明者 → `imageRuntime` 返回全局三件套 → 配图请求体里的 `model` 仍是 `step-image-edit-2`。**升级前后逐字相同**，由 `undeclaredProfileFallsBackToTheGlobalImageTrio` 与 `nullProfileIdFallsBackToTheGlobalImageTrio` 钉住。

## 六、明确不做

- **图片 baseUrl / apiKey 的档案级覆盖**：同一网关同一个 key，加了只是扩大迁移面（见 3.1）。
- **把配图师绑定的档案改成「图片档案」**：`LLM_PROFILE_ID` 的语义是文本模型，配图师仍需要它来写提示词、调工具；图片模型是**额外**的一个维度，不是替换。
- **`builtin_illustrator` 的绑定调整**：本轮不改任何内置智能体的档案绑定（属计划外调参）。
- **`git push`**：本地 commit，推送需单独授权。

## 七、相关文档

- `docs/dev/skills-agent-plan.md` §4.3 / §4.3.1 —— 档案表结构与本节决策
- `docs/dev/scheduled-task-reliability-round.md` —— 档案故障切换链的来源
- `docs/dev/known-issues-handoff.md` —— 未实现清单（I8 编辑器链路无硬超时等）

## 八、审查轮与修复（2026-09-16，commit `9ee6bc0` 之后）

首次提交后按「重大功能强制审查」跑了一轮只读 reviewer。**结论：pass-with-issues**；接线（§3.3）与密钥处理两维干净，`imageRuntime` 的端点/密钥同源、`ImageRuntime.available()` 的空值检查均无问题。以下 findings 逐条**先验真再修**，假阳性明确驳回。

### 8.1 已验真并修复

| # | 严重度 | 问题 | 验真方式 | 修复 |
| --- | --- | --- | --- | --- |
| 1 | **CRITICAL** | 编辑**任何存量档案**都存不进去 | `node -e` 复现：`Object.assign` 会把 `''` 覆盖成 `null` → `.trim()` 抛 TypeError。**这是本次引入的回归**（改动前 `profilePayload()` 没有这个字段）。我原先的实机验收走的是 raw `curl`，恰好绕过了这条 UI 路径 | `openProfile` 与 `profilePayload` 两处 `|| ''` 归范 |
| 2 | **MAJOR** | 档案路径硬编码 `enabled=true`，绕过全局「启用 AI 服务」开关 | 对比 `HEAD~1` 的 `imageAvailable()`：它读 `RuntimeConfig.enabled`，而该值在有默认档案时来自**默认档案的可用性**（设置页把 `llm_config` 写透到默认档案）。故档案路径确实能在一个「已停用」的部署上继续调付费生图接口 | `imageRuntime` 先判 `config.enabled()` 再找承载档案 |
| 3 | **MAJOR** | 前端配图模型解析只走两跳、且不筛可用性，会显示后端不会用的模型与错误来源 | 逐行比对 `failoverChain` + `addIfUsable` 与前端 `chain.push` | 前端改为逐跳同口径（绑定→默认→兜底→其余已启用按 id 升序；`enabled && hasApiKey`；按 id 去重）。**⚠️ 此修复不完整，仍漏了 `findFirst()` 回落一跳，见 §8.4.1** |
| 4 | MINOR | `ArticleAiService.imageProfileId` 不看 `enabled`，与 `ScheduledAgentFactory` 同名助手规则相反，且与自己的 javadoc 不符 | 两处源码并排对照 | 统一为「定义缺失或停用 → null」 |
| 5 | MINOR | 设置页「图片模型」留空文案「留空则禁用 AI 画图和图片编辑」已不成立 | 档案路径不读全局图片模型名 | 文案改为说明「档案声明了就用档案的」 |
| 6 | MINOR | 三处测试断言过松：替身架空了 `imageCarrier` 那一跳、`isNotEqualTo` 弱断言、只读工具用例无区分力 | 读替身实现：`when(service.imageCarrier(any())).thenReturn(carrier)` 无条件返回 | 替身复刻真实判据；新增 `forcedCarrier`；弱断言改 `isEqualTo`；只读工具改为两次构造逐位对比 |
| 7 | MINOR | 真库用例自造第二个 `is_default` 行并断言 `findDefault()` 等于它 —— `findDefault()` 是按 id 升序取首，断言取决于行序 | 读 `LlmProfileMapper.findDefault()` | 改为反向判断「本行不应被选中」，并直接断言 `findById` 往返 |
| 8 | MINOR | carrier 路径的模型名取未 trim 的原值（判定用 `blankToNull`、传值用原串，自相不一致） | 读 `LlmConfigService:126-127` | 统一用 `blankToNull` 后的值 |

**修复的确定性验证**：新增用例 `carrierPathIsBlockedWhenTheGlobalLlmIsDisabled` 做了**反例证明** —— 把全局门禁改回硬编码 `true` 后重跑，该用例失败（`Expecting value to be false but was true`），确认断言真的能抓到这条缺陷，而不是恒真。随后已还原文件并核对与原文件逐字节相同。

### 8.2 驳回的 finding

- **「`RuntimeConfig.enabled` 是字面上的 `llm_config.enabled`」**：reviewer 以此为前提推导「fallback 路径措辞不成立」。**前半成立、结论驳回**：`runtime()` 在有默认档案时取的是默认档案的 `available()`，不是 `llm_config.enabled`。原 javadoc 的表述确实不准（已改正），但**行为本身没有变化** —— 修复 #2 的依据正是这条事实（旧的 `imageAvailable()` 门禁 = 「得有一个可用档案」），而不是「旧代码读的是 `llm_config.enabled`」。
- **「`AgentJsonContractTest` 用进程内 `ObjectMapper`，未证明 HTTP 层也按 null 在场输出」**：属**有效的覆盖缺口**，但不是缺陷 —— 已由实机 `GET /api/llm-profiles` 实测确认（4 条档案的 `imageModelName` 字段在场且为 null，见 §5.2）。

### 8.3 本轮之后仍存在的已知边界

> **两条均已在 §8.4 收口**（2026-09-17）。本节保留原样以记录当时的真实状态。

- **HTTP 层「null 在场」无自动化用例**（§8.2 第二条）：当前只有实机证据，未加集成测试。
- **前端档案链逻辑无自动化用例**：`webui` 无测试框架，前端闸门只有 `check:imports` + `vite build`，因此 #3 的修复靠人工比对与构建产物核对（已确认 `|| ''` 与链逻辑都在产物里）。

### 8.4 边界收口（2026-09-17）

§8.3 的两条边界本轮均已收口。**其中第 2 条在收口过程中查出一个残留缺陷**，记在这里以免后人以为 #3 已经彻底修好。

| # | 边界 | 收口方式 |
| --- | --- | --- |
| 1 | HTTP 层「null 在场」无自动化用例 | `AgentApiIntegrationTests.nullableProfileFieldsArePresentAsNullOverHttp`（集成测试，走真实 Spring MVC 栈） |
| 2 | 前端档案链逻辑无自动化用例 | 抽出 `webui/src/utils/profiles.js` + 闸门 `webui/scripts/check-profile-chain-parity.mjs`（接入 `prebuild`） |

#### 8.4.1 残留缺陷：前端漏抄了「无 is_default 时回落第一条」这一跳

§8.1 的 #3 声称前端已「改为逐跳同口径」。**这个说法不完整**：前端抄了「绑定 → 默认 → 兜底 → 其余已启用」的顺序，但第 2 跳只写了 `usable.find(p => p.isDefault)`，**漏掉了后端 `defaultProfile()` 的 `?? findFirst()` 回落**（`LlmProfileService:62-65`）。

后果是真实可见的：当**没有任何档案带 `is_default` 标记**、且**兜底档案声明了 `imageModelName`** 时——

- 后端 `defaultProfile()` 回落到全表第一条 → 用它（或它之后的兜底档案）；
- 前端第 2 跳什么都不加 → 兜底档案被提前到第 2 位 → 界面显示兜底档案的图片模型。

也就是说「配图模型」卡片会报出一个**后端不会用的模型名**——正是 #3 当初要修的那个毛病，只是换了个触发条件。这个状态在真库里可达：`setDefault` 是应用层保证唯一的，但清掉 `is_default` 标记（或建库后从未设过默认）并不违反任何约束。

**验真方式**：把两套实现并排跑同一批档案（12 个用例，含「无 is_default」「第一条被停用」「绑定档无 key」等边界），旧前端实现与后端在 2 个用例上分歧（链顺序 `[2,1]` vs `[1,2]`、图片来源 `Y` vs `X`）。

**修复**：`imageChain` 的第 2 跳改为 `ordered.find(p => p.isDefault) || ordered[0] || null`。这里有个**易错点**：回落取的必须是**未过滤**的全表第一条，而不是「第一条可用档案」——后端是「先 `findFirst()`、再交给 `addIfUsable` 判断」，若第一条恰好被停用，这一跳就**什么都不加**（不会顺延到第二条可用档案）。照抄成「第一条可用档案」会让兜底档案的位置提前，重新引入同类错误。`E first disabled, no is_default` 这个用例专门钉住它。

**确定性验证（双向红证）**：

- 把前端第 2 跳改回漏掉回落的旧写法 → 闸门失败，报 4 条（2 个用例的链顺序与图片来源各 1 条）；
- 把后端 `failoverChain` 的「默认」与「兜底」两跳对调 → 闸门的**结构自检**失败，提示前端需同步调整。

两次均随后还原并核对 `git diff` 为空。

#### 8.4.2 闸门设计说明

`check-profile-chain-parity.mjs` 与既有的 `check-shared-imports.mjs` 同风格（纯 node、无测试框架依赖、失败 `exit 1` + `console.error`），做两道检查：

- **结构自检**：解析 `LlmProfileService.java`，断言 `failoverChain` 仍是那 4 跳、且 `defaultProfile()` 仍保留 `findDefault() → findFirst()` 回落。**后端改顺序时前端会被提醒**，避免「前端悄悄过期而闸门仍绿」。
- **行为自检**：把 12 组档案喂给前端实现，断言链顺序与图片模型来源。

结构自检是必要的：只测行为的话，后端改了顺序而前端没改，只要那 12 个用例恰好没覆盖新顺序，闸门就会放行——而这正是 §8.4.1 那个缺陷能存活至今的原因。

#### 8.4.3 一个测试隔离现象，最终查明是**生产缺陷**（不是测试脆性）

新增 HTTP 契约用例时踩到一个现象：`AgentApiIntegrationTests` 的
`llmProfileCrudAndSettingsLlmCompatMapping` 隐含依赖「库里只有它自己造的档案」——它的
`PUT /api/settings/llm` 走 `syncDefaultFromConfig → defaultProfile() → findFirst()`，
若库中先存在别的档案，写透就会落到那条档案上而不标记 `is_default`，于是它的
`$.data[?(@.isDefault==true)]` 断言落空。

它平时不暴露，是因为 JUnit 5 的方法执行顺序由**方法名哈希**决定：新增一个方法会改变顺序。
新用例最初「先建一条档案、不清理」，恰好排到它前面就把它挤红了。

**第一轮（2026-09-17 上午）把它判成了「测试脆性」并只绕开**（新用例改为自建档案 +
`try/finally` 删除）。**这个判断是错的**——那条 `findFirst()` 落回不是测试才有的路径，
它是 `defaultProfile()` 的正常语义，因此**生产上完全一样会发生**。绕开测试只是把症状藏起来。

#### 8.4.4 真缺陷：存量设置页会静默改写用户自建的档案

触发前提在生产上可达，且**不需要任何异常操作**：用户先在档案页建了一条自己的档案、
却从没点过「设为默认」（或建库后从未迁移过），此时 `LLM_PROFILE` 里有行、但没有一行 `is_default=1`。
此后只要打开**旧的系统设置页**保存一次 LLM 配置：

| 步骤 | 实际发生的事 |
| --- | --- |
| `PUT /api/settings/llm` | `LlmConfigService` 写 `llm_config`，再调 `syncDefaultFromConfig` |
| 选写透目标 | `defaultProfile()` = `findDefault()` **`?? findFirst()`** → 落到**第一条用户档案** |
| 写入 | 该档案的 `provider`/`baseUrl`/`modelName`/`apiKey` 被**整条覆盖** |
| 标记 | **不打** `is_default` —— 它仍是普通档案 |
| 用户可见的后果 | 请求全部成功、设置页回显也对（读路径同样回落 `findFirst`），但用户下次打开档案页会发现自己的档案被动过，且旧 key 已丢 |

**为什么危险**：`apiKey` 被覆盖是不可逆的（只存加密值，没有原文可回滚）；而且因为读路径也用同一个
回落，**回显与写入看起来完全自洽**，没有任何一处会报错或告警。

**修复**：`syncDefaultFromConfig` 改用 `mapper.findDefault()`（只认真正的默认档案），
找不到时按 javadoc 说的**创建**：若已存在一条名为「默认配置」的档案就**收养**它并补标记
（避免建出重名档案、让 `findByName` 变歧义），否则新建一条带 `is_default` 的。

**为什么不「把第一条就地提升为默认」**：`delete` 拒绝删除默认档案，就地提升会把用户自建的档案
变成**不可删**——等于替用户做了一个他没同意过的决定。新建一条只多一行且随时可删，代价更小。

**这条结论在仓库里已经写过一次**：`LlmProfileSeeder.defaultSource()` 的 javadoc 明确写着
「不直接用 `defaultProfile()` 的回落语义」，理由是「库里若存在无 key 的空档案，拿它当来源会复制出
一个同样不可用的档案」。**同一类错误、同一个方法、同一份结论，在 `syncDefaultFromConfig` 里漏用了**。
根因是 `defaultProfile()` 这个名字听起来像「默认档案」这一**实体**，实际语义却是「运行时挑一条来用」
这一**动作**——它的回落对**读**是容错，对**写**就是改错对象。

**确定性验证**：

| 层面 | 用例 | 反例证明 |
| --- | --- | --- |
| 单测 | `legacyWriteThroughCreatesDefaultInsteadOfHijackingTheFirstProfile`、`legacyWriteThroughUpdatesTheExistingDefault`（对照：正常路径不得变成「每次新建」）、`legacyWriteThroughAdoptsAnExistingSameNamedProfile` | 把 `findDefault()` 改回 `defaultProfile()` → 第 1 例失败：`expected: "用户原来的模型" but was: "settings-model"` |
| HTTP 端到端 | `legacySettingsWriteThroughDoesNotHijackAUserProfile`（造出「有档案但无 is_default」的真实库状态） | 同上改动 → 该例失败，报 `expected: "用户原来的模型" but was: "settings-model"` |

**为什么必须留 HTTP 那条**：触发前提是**数据库状态**（库里存在非默认档案），单测只能证明逻辑分支，
证明不了这个状态在真实表里会走到那条分支。而 §8.4.3 那个现象本身也说明：只靠单测 + 一次「单独跑就绿」
的观察，很容易把生产缺陷误判成测试顺序问题。

**顺带修正**：`llmProfileCrudAndSettingsLlmCompatMapping` 现在不再依赖执行顺序了——它脆的根因就是
这个缺陷（写透落到别人的档案上）。本轮**没有**给它加 `@TestMethodOrder`：那是给症状打补丁。
修好写透目标后，它无论在什么顺序下都成立。
