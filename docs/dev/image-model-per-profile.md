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

## 四、测试与闸门

| 用例 | 覆盖点 |
| --- | --- |
| `LlmProfileServiceTest`（+7 例，共 13） | 档案链顺序/去重/跳过不可用者；`imageCarrier` 绑定优先、沿链下落、全链无人声明返回 null、空白不算声明、停用档案声明的图片模型失效 |
| `LlmConfigServiceImageRuntimeTest`（新增 7 例） | 档案优先且端点/密钥同源；未声明/未绑定/档案不可用三种情况回落全局；全局未配或 `enabled=false` 时不可用；档案路径不泄露全局模型 |
| `ArticleMediaToolsImageProfileTests`（新增 5 例） | 生图/修图两条传参路径都真的把档案 id 交给图片服务；null 保持 null；只读工具不受影响；存量三参重载仍等价 |
| `LlmProfileImageModelColumnPersistenceTests`（新增 4 例，打真库） | 列存在且可空、与 `LLM_CONFIG.IMAGE_MODEL_NAME` 类型同向；NULL 往返；整行更新不丢图片模型 |
| `AgentJsonContractTest`（+1 例） | `imageModelName` 的序列化契约（含 null 必须在场） |
| `EntityColumnDeclarationConsistencyTest`（既有） | 跨两张表的同名字段声明一致 |

**闸门**：`.mvn/mvn-local.sh -o test` 全绿（基线 401 → 见 `target/gate-image-model.log`）；前端 `npm run check:imports` 通过 + `vite build` 成功。

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
