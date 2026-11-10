# 前端独立复查报告 — 模型档案「供应商 → 模型」两级结构（task-5）

- **复查对象**：`webui/**`（本轮改动面：`ModelProfilesView.vue`、`AgentsView.vue`、`SettingsView.vue`、`utils/profiles.js`、`style.css`、`scripts/check-profile-chain-parity.mjs`）
- **契约依据**：`docs/dev/llm-provider-refactor.md`（§4 运行时口径、§5.1/§5.2 接口、§6 前端契约）
- **环境**：实例 `http://127.0.0.1:18081`（用户正在浏览，全程只读）、库 2 个供应商 / 16 条档案、webbridge session `watb-review-frontend`
- **本轮未发现 [CRITICAL]**。有 3 条 [MAJOR]，其余为 [MINOR]。

## 0. 复查方法与只读声明

| 手段 | 具体做法 |
|---|---|
| 静态审查 | `read`/`grep` 逐个读本轮 6 个改动文件与后端 `LlmProfileService` / `LlmProviderService` / 两个 Controller / `GlobalExceptionHandler` |
| 自检脚本 | 在 `webui/` 跑 `npm run check`（只读，不写产物） |
| 反证实验（内存） | 用**脚本里同一对正则**对篡改后的后端源码（仅内存，不落盘）跑判定，测 A 段的拦截能力；用 `node --input-type=module -e` 把构造数据喂给 `profiles.js` 的 `imageChain` |
| 浏览器（只读） | webbridge 仅做：切路由、展开/收起、打开弹窗、按 Esc、读 DOM/计算样式/截图、用页面自己的 token 发 **GET** `/api/llm-providers`、`/api/llm-profiles`。实测整轮只产生 GET：`performance` 里出现的 API 只有 `auth/me`、`dashboard`、`llm-providers`、`llm-profiles`、`agents`、`agents/tool-groups`、`settings/llm`、`skills`、`settings/render`；结束时 `document.querySelector('.modal-backdrop') === null` |
| 禁止项遵守 | 未改任何产品文件（本报告是唯一写入）、未 git 提交、未碰数据库、未跑任何 mvn、未重启实例、未在界面上点保存/删除/启停。表单提交字段的对齐是「读两侧源码 + 读 DOM 字段」确认的，**没有发出真实写请求** |

**运行实例与源码同代（先确认这一点，否则浏览器证据无效）**：`webui/dist/assets/*` 与实例实际服务的 `target/classes/static/assets/*` 逐字节一致（对 `ModelProfilesView-D3P9cx-Y.js`、`AgentsView-BSwnuLMR.js`、`SettingsView-BcYCwtg9.js`、`index-BR9WzD6c.js` 四个文件 `cmp` 均为 same）；源文件 mtime（10:27–10:36）早于产物 mtime（dist 11:41 / static 12:08）；产物里能 grep 到本轮新字符串（`供应商已不存在`×4、`未指定供应商`×2、`所属供应商`×3）；浏览器里实拉到两级结构。故下述「实测」均为本轮代码的渲染结果。

---

## 1. 问题清单

| # | 级别 | 一句话 | 位置 |
|---|---|---|---|
| F1 | MAJOR | AgentsView 档案下拉不标注不可用档案，悬空与旧版还混进同一个 optgroup | `AgentsView.vue:108-116, 322-324` |
| F2 | MAJOR | 前端 `usable()` 与后端 `available()` 已不等价（缺 provider/baseUrl 非空），A 段自检对这个类别是盲区 | `utils/profiles.js:34` ↔ `LlmProfileService.java:536-539` |
| F3 | MAJOR | 悬空/旧版卡片上的启停开关必然失败；失败后开关视觉不回滚 | `ModelProfilesView.vue:397-400, 279` |
| F4 | MINOR | `providerModelCount` 取 max：同一屏可能出现「N 个模型」与可见卡片数不一致 | `ModelProfilesView.vue:76-80, 358, 365-367, 206-209` |
| F5 | MINOR | 可访问性：弹窗不接管焦点、无 `aria-controls`、页面级告警不播报 | `ModelProfilesView.vue:425-430, 447-452, 345-349, 313-314` |
| F6 | MINOR | 旧版档案缺 key 时卡片 pill 说「供应商未配置密钥」，与外层「未指定供应商」打架 | `ModelProfilesView.vue:395, 375-377` |
| F7 | MINOR | 新建模型的「上游模型名称」默认值不随供应商协议变化（Anthropic 下预填 `gpt-4.1-mini`） | `ModelProfilesView.vue:37, 469` |
| F8 | MINOR | 删除护栏按钮 `disabled` 时键盘不可达，禁用理由（title）读不到 | `ModelProfilesView.vue:365-367` |
| F9 | MINOR | 后端源码文件缺席时 A 段静默降级；`check-shared-imports` 覆盖范围有限 | `check-profile-chain-parity.mjs:35-41`、`check-shared-imports.mjs:56-60` |
| F10 | MINOR | CSS 特异性：`.provider-head` 的 `align-items/gap` 被 `.profile-item>header` 覆盖 | `style.css:152` ↔ `:168`、`:172` ↔ `:158-159` |
| F11 | MINOR | providers 接口「成功但非数组」时，全部档案会被渲染成悬空告警 | `ModelProfilesView.vue:139-144, 88-102` |

### F1 [MAJOR] AgentsView 的档案下拉不携带可用性信息，且把「悬空」与「旧版」混为一组

- **位置**：`webui/src/views/AgentsView.vue:108-116`（`profileGroups`）、`:322-324`（`optgroup`/`option`）；对照 `ModelProfilesView.vue:465`（供应商下拉会拼「（已停用）」后缀）。
- **现象**：下拉的 `<option>` 只有 `profile.name`（`isDefault` 时补「（默认）」），既不看 `providerEnabled`，也不看 `hasApiKey`；分组名是 `profile.providerName || '未归属供应商'`，于是**悬空**（providerName=null）与**旧版**（providerName=null）落进同一个组标签。
- **怎么知道的**：读代码；并在浏览器里实测——`/agents` → 点「新建智能体」打开表单（纯前端状态，无请求），读 DOM：
  `optgroups = [{label:"nexus.bx9y.com.cn", items:["打底档A",…,"nemotron-3-ultra-free"]}, {label:"api.anthropic.com", items:["异端档D"]}]`，`selectOptionCount = 17`（1 条「使用默认档案」+ 15 + 1），16 个档案 option 文本里**没有任何可用性后缀**。契约要求的三种取值由 `GET /api/llm-profiles` 实测确认（见 §2 的字段实测）。
- **影响**：管理员可以把智能体绑到「供应商已停用」或「供应商已不存在」的档案上；绑定后 `AgentsView.vue:283` 只显示档案名，看起来一切正常，而运行时 `failoverChain` 会静默跳过它、回落默认档案——即「界面报的模型 ≠ 实际用的模型」，正是 `utils/profiles.js:1-27` 与契约 §5.2 反复警告的那类失效。同一个档案在模型档案页是红色告警块（`ModelProfilesView.vue:393`），在智能体页却毫无标记，两页口径不一致。
- **建议修法**：`option` 文本按档案可用性追加后缀，例如 `providerEnabled === false && !providerName → '（供应商已不存在）'`、`providerEnabled === false → '（供应商已停用）'`、`!hasApiKey → '（供应商未配置密钥）'`；`profileGroups` 把「旧版档案（未指定供应商）」与「供应商已不存在的档案」分成两个组名（可与模型档案页的措辞对齐）。

### F2 [MAJOR] `usable()` 已与后端 `available()` 不等价，A/B 段自检结构上都覆盖不到

- **位置**：`webui/src/utils/profiles.js:34`（`usable = profile => Boolean(profile && profile.enabled && profile.hasApiKey && profile.providerEnabled !== false)`）、同文件 `:8-14`、`:26`、`:30`（自称「与后端 `addIfUsable` 同判据」）；后端 `LlmProfileService.java:533-539`（`available()` 已收紧为 `enabled && provider 非空 && baseUrl 非空 && apiKey 非空`）、`:399-403`（`providerId == null` 时 provider/baseUrl 取档案自身旧列，且 `normalizeBaseUrlOrNull` 会把空/非法 baseUrl 折成 `null`）。
- **现象**：前端判据里没有 `provider` / `baseUrl` 这两项。对**存量回退行**（`provider_id` 为空、自身旧列脏）——正是契约 §4 那次收紧针对的对象——前端会把后端根本不会选的档案算进链。
- **怎么知道的（反证实验，`node --input-type=module -e`，不落盘）**：
  ```
  chain(legacy dirty first) = 1:legacy-dirty, 2:healthy
  carrier  = img-FROM-DIRTY
  chain(baseUrl 合法)      = 1,2
  => baseUrl 是否影响前端判据: false
  ```
  同一行只把 `baseUrl` 由 `null` 换成合法 URL，链完全不变 → **baseUrl（以及 provider）确实不参与前端判据**。构造行：`{providerId:null, providerName:null, providerEnabled:true, provider:null, baseUrl:null, hasApiKey:true, enabled:true, imageModelName:"img-FROM-DIRTY"}`；后端对同一行会因 `available()` 失败而跳过它，于是 `imageCarrier` 给出的是 `img-HEALTHY`（或 null）——前端报出的是**后端不会用的图片模型名**。
- **自检为什么守不住（这是本条的真正重点）**：A 段的第二条正则（`check-profile-chain-parity.mjs:51-58`）只提取 `addIfUsable(chain, seen, X)` 的第三个实参**方法名**，第一条（`:45`）只钉 `findDefault() … findFirst()` 文本；B 段（`:66-182`）只喂给**前端**实现，且它的夹具工厂 `P()`（`:71-80`）里根本没有 `baseUrl`/`provider` 字段，结构上不可能产生这条差异。用脚本里**同一对正则**对内存中篡改的后端源码跑判定，结果：
  ```
  PASS(A 段不报错)   原始源码                              | A1=true hops=bound>defaultProfile>fallbackProfile>profile
  FAIL(A 段会拦下)   篡改1：交换第2/3跳顺序                | hops=bound>fallbackProfile>defaultProfile>profile
  FAIL(A 段会拦下)   篡改2：新增第5跳                      | hops=bound>defaultProfile>fallbackProfile>profile>defaultProfile
  FAIL(A 段会拦下)   篡改3：defaultProfile 去掉 findFirst   | A1=false
  PASS(A 段不报错)   篡改4：判定体 available() 改成 enabled() | A1=true hops=bound>defaultProfile>fallbackProfile>profile
  FAIL(A 段会拦下)   篡改5：findFirst 改成「第一条可用」     | A1=false
  ```
  即：**跳序类改动拦得住（A 段没有被削弱），但「判定体被改」这一类别 A 段明确不拦**（篡改4 PASS），而前端 `usable()` 正好已经在判定体上落后于后端。契约 §4（`llm-provider-refactor.md:87-93`）收紧 `available()` 时只在 §6.2（`:235-241`）要求前端加 `providerEnabled !== false`，**没有要求同步 provider/baseUrl 两项** → 这是契约与实现共同的缺口，不是实现单方写错。
- **影响**：升级窗口 / 手工改库产生的脏 legacy 行会让智能体页的「配图模型」卡片报出错误模型名（或反过来漏报），而故障切换链并不会用它。当前 smoke 库暂未显形（实测 16 条档案 `providerId` 全非空、`nullPid=0`、`providerEnabled` 全 true），属于**潜伏**缺陷。
- **建议修法**：①`usable()` 补落地性判据——`providerId == null` 的行要求 `provider`/`baseUrl` 非空，挂供应商的行要求 `providerName != null` 且供应商有 `baseUrl`（若 `/api/llm-profiles` 能直接回传一个「运行时可用」布尔则更省事，判据就只剩一处）；②B 段夹具增加 `baseUrl`/`provider` 字段并加一条「legacy 行 baseUrl 为空 ⇒ 不在链上」用例；③A 段再加一条正则钉住 `addIfUsable` 判定体里 `available()` 的存在（例如禁止出现 `runtime.enabled()` 单独作为判据），并同步更新契约 §6.2。

### F3 [MAJOR] 悬空/旧版卡片上的启停开关必然失败，且失败后开关不回滚

- **位置**：`ModelProfilesView.vue:397-400`（开关对**所有** kind 的卡片都渲染，只判 `isAdmin`）、`:276-282`（`toggleProfile` 整条 PUT，载荷里带 `providerId: profile.providerId`）。
- **现象**：悬空档案的 `providerId` 指向已删除的供应商，旧版档案的 `providerId` 是 `null`；两者的 PUT 都过不去。
- **怎么知道的**：读载荷与后端校验路径——`LlmProfileController.java:44-48` 对 `update` 用 `@Valid`；`LlmProfileService.java:503-505` 的 `ProfileRequest` 是 `@NotNull Long providerId`，`:264` 又用 `providerService.required(request.providerId())`（`:88-92` 找不到就抛「供应商不存在」）。所以：悬空行 → 400「供应商不存在」；旧版行 → 400 字段级校验错误，消息由 `GlobalExceptionHandler.java:20-27` 取**第一个 fieldError** 拼成（形如 `providerId …`，字段名是英文）。前端把它渲染在页面顶部的 `.alert.error`（`:313`），既没提示「去编辑里改挂供应商」，也没禁用这个必然失败的控件。
- **附带（同一根因的放大）**：`<input type="checkbox" :checked="profile.enabled" @change=…>` 没有 `v-model`、也没有失败后的显式回写。Vue 的 `patchProps` 只在 vnode prop 变化时才写 DOM（`next !== prev`），请求失败时 `profile.enabled` 未变 → DOM 保留用户点出来的位置。于是「点开关 → 报错 → 开关看起来已经切过去了」，而数据没变，页面不刷新就一直是错的。
- **影响**：坏数据卡片上唯一看起来能「修」的控件是错的；错误提示是后端原文，不指向修复动作；开关视觉与真实状态不一致会二次误导（图例刚说过「停用供应商会同时停用它下面的全部模型」）。
- **建议修法**：`dangling`/`legacy` 卡片不渲染开关（改成「先归位供应商」的提示按钮），或在 `toggleProfile` 里对这两种 kind 直接给出引导性文案；失败分支里显式回写 `event.target.checked = profile.enabled`（或改用 `v-model` + 失败回滚）。**注**：视觉不回滚这一半是 Vue 补丁语义推理，未在只读约束下触发真实失败请求（见 §4 未验证项 3）。

### F4 [MINOR] `providerModelCount` 取 max，可能出现同屏两处数字不一致

- **位置**：`ModelProfilesView.vue:76-80`（`Math.max(declared, local)`）、`:358`（行内「N 个模型」）、`:365-367`（护栏 `disabled` 与 title）、`:206-209`（删除确认文案）。
- **现象**：行内计数与删除护栏用的是 max（后端 `ProviderView.modelCount` 与本地过滤结果取大），而展开区渲染的是 `group.models.length`（`:384`、`:106`）。
- **怎么知道的**：实测两组数字目前一致——`GET /api/llm-providers` 返回 `modelCount` 15 / 1，DOM 上「15 个模型」且该组渲染 15 张卡、「1 个模型」渲染 1 张卡；`sumDeclared = 16 = profileTotal`。但 `load()`（`:134-152`）是**先**取 providers、**后**取 profiles 的两个请求，两者之间若发生并发改动（另一标签页新建/改挂档案），就会出现「标题 3 个模型、展开只有 2 张卡」，且删除护栏的 title 会报出一个用户在页面上看不到的模型数，按钮持续禁用、用户按可见卡片删空后仍无法删除。
- **建议修法**：显示口径统一用本地列表长度，护栏单独取 max；两者不等时在行内加一句「后端记录 N 条、当前显示 M 条，请刷新」，或直接让 `load()` 用 `Promise.all` 取同一时刻的快照。

### F5 [MINOR] 可访问性：焦点管理 / `aria-controls` / 告警播报

- **位置**：`:425-430`、`:447-452`（`modal-backdrop` + `role="dialog" aria-modal="true"`）、`:345-349`（caret）、`:313-314`（页面级 `message`/`error`）、`:293-297`（Esc）。
- **怎么知道的（实测）**：打开「编辑模型档案」后 `document.activeElement` 仍是 `BODY`，`focusInsideModal = false` → 打开弹窗不移动焦点；`.modal-backdrop` 是 `position:fixed;inset:0;z-index:100`（`style.css:23`）的全屏遮罩，而弹窗 DOM 位置在列表**之后**（`:425`、`:447`），所以 Tab 会继续在被遮罩盖住的背景控件里走（焦点不可见）；caret 无 `aria-controls`，展开容器也无 `id`（实测 `caretAriaControls=false`、`expandedRegionId=null`）。**已实测正常**：Esc 能关供应商弹窗与模型弹窗（两个弹窗分别 `closedByEscape=true` / `providerClosedByEscape=true`）；caret 是真 `<button type="button">`，`aria-expanded` 随展开态正确翻转；开关有可读名称（实测 `aria-label="停用供应商 nexus.bx9y.com.cn"`）。
- **影响**：键盘/读屏用户打开弹窗后焦点落在遮罩后面，保存成功/失败与删除结果（`:313-314` 无 `role`/`aria-live`）不会被播报。
- **建议修法**：打开时 `focus()` 第一个输入框、关闭时焦点还原到触发按钮，背景容器加 `inert`（或 `aria-hidden`）；caret 补 `aria-controls` 指向展开区 `id`；`message`/`error` 加 `role="status"` / `role="alert"`。同类问题在 `AgentsView.vue:298-303` 也存在，属项目级存量模式（非本轮引入）。

### F6 [MINOR] 旧版档案缺 key 时，卡片 pill 与外层分组标题打架

- **位置**：`ModelProfilesView.vue:395`（`v-else-if="!profile.hasApiKey"` → 「供应商未配置密钥」）、`:375-377`（外层分组标题「旧版档案（未指定供应商）」）。
- **怎么知道的**：读代码 + 后端契约：`providerId == null` 时 `hasApiKey` 来自档案自身旧列（`LlmProfileService.java:352-355`、契约 `llm-provider-refactor.md:167`）。因此一条**没有供应商**的旧版档案如果自身没 key，卡片会显示「供应商未配置密钥」，而它所在的分组标题刚说过「未指定供应商」。
- **影响**：管理员会去供应商列表里找一个并不存在的供应商。
- **建议修法**：按 `group.kind` 分派文案（旧版 → 「该档案未配置密钥」；悬空 → 已有 `:393` 的「供应商已不存在」分支覆盖）。

### F7 [MINOR] 新建模型的默认上游模型名不随供应商协议变化

- **位置**：`ModelProfilesView.vue:37`（`blankProfile()` 的 `modelName:'gpt-4.1-mini'`）、`:469`（同值 placeholder）。
- **怎么知道的（实测）**：在 `api.anthropic.com`（协议 ANTHROPIC）分组下点「新增模型」，读表单：`newInputs` 里「上游模型名称」`value="gpt-4.1-mini"`、`required=true`。直接保存就会得到一个 Anthropic 供应商下的 OpenAI 模型名。
- **影响**：新档案一出生就可能指向不存在的模型（到运行时报 model not found 才暴露）。
- **建议修法**：按 `currentProvider.provider` 预填（ANTHROPIC → `claude-*`，OPENAI_* → `gpt-*`），或留空让 `required` 拦住。

### F8 [MINOR] 删除护栏按钮 `disabled` 时键盘不可达

- **位置**：`ModelProfilesView.vue:365-367`。
- **怎么知道的（实测）**：两个供应商的删除按钮均为 `disabled=true`，`title="该供应商下仍有 N 个模型，请先删除或改挂到别的供应商"`（N 与行内计数一致）。`disabled` 的 button 不进 tab 序列，读屏也拿不到 `title`，`aria-label` 只写了「该供应商下仍有模型，不可删除」。
- **建议修法**：用 `aria-disabled` + 点击后提示（或把原因直接渲染在行内），保留按钮可聚焦。

### F9 [MINOR] 自检脚本的边界（本轮未削弱，但需知道它守不住什么）

- **位置**：`webui/scripts/check-profile-chain-parity.mjs:35-41`、`webui/scripts/check-shared-imports.mjs:56-60`。
- **怎么知道的**：读脚本 + Dockerfile。实测 `npm run check` 通过：
  ```
  ✓ 共享工具导入自检通过（10 个导出符号）
  ✓ 档案链一致性自检通过（后端 4 跳结构 + 15 个行为用例）
  ```
  A 段**没有被削弱**（拦截能力见 F2 的实验表：跳序交换/多跳/去掉 findFirst 回落/把回落改成“第一条可用”都会 FAIL）；`prebuild: npm run check` 挂在 `package.json:13`，`Dockerfile:12` 的 `npm run build` 会带上它。但两条边界：①`Dockerfile:7-12` 的 frontend 阶段只 `COPY webui/`，后端源码不在场 → A 段打印「跳过结构自检」后照跑 B（`:32-41` 的注释说明这是刻意的）；同一个 `ENOENT` 分支也会顺带吞掉「文件被改名/移动」的情形，建议以「`src/main/java` 目录是否存在」判断是否完整检出；②`check-shared-imports.mjs` 只扫 `src/utils/*.js` 的导出，管不到「导入了已被删除的导出」「非 utils 目录的共享符号」。
- **建议修法**：A 段加「完整检出则文件必须存在」的断言；如需更强保障，把 F2 的判定体纳入 A 段正则（见 F2 建议③）。

### F10 [MINOR] CSS 特异性：`.provider-head` 的部分声明被覆盖

- **位置**：`style.css:152`（`.profile-item>header{…align-items:flex-start…gap:10px}`，特异性 0-1-1）↔ `:168`（`.provider-head{…align-items:center;gap:9px;flex-wrap:wrap}`，0-1-0）；`:158-159`（`.profile-item dl / dl div`，0-1-1/0-1-2）↔ `:172`（`.provider-meta{gap:7px}`，0-1-0）。
- **怎么知道的（实测计算样式）**：`providerHead = {alignItems:"flex-start", gap:"10px", flexWrap:"wrap", justifyContent:"space-between"}`（作者写的是 `center`/`9px` → 被覆盖），`providerMeta.gap = "8px"`（作者写 7px → 被覆盖）；`.provider-item` 的 `padding:16px 17px`、`gap:12px` 生效正常（`:167` 在 `:149` 之后，同特异性后者胜）。截图确认整体布局无破位。
- **影响**：仅视觉（供应商行内元素顶对齐而非垂直居中；Base URL/API Key 两行间距比作者意图大 1px）。
- **建议修法**：提高选择器特异性（`.provider-item>.provider-head`）或删掉失效声明，避免后来人误以为改这几行能改布局。

### F11 [MINOR] providers 返回「成功但非数组」时，全部档案会被渲染成悬空

- **位置**：`ModelProfilesView.vue:139-144`（`Array.isArray(list) ? list : []`，且只有 `catch` 才写 `providersError`）、`:88-102`（悬空判据 = 在供应商列表里找不到对应 id）。
- **怎么知道的**：读代码。`api()`（`src/api.js:19-23`）只在 `!response.ok || payload.success === false` 时抛错，`data:null` 这类响应不会抛 → `providers` 变成空数组且没有错误标志 → `groups` 会把所有档案判成 `dangling`，渲染成红色「供应商已不存在 / 运行时不可用」告警块。当前后端恒返回 `List`（实测 2 条），故现状不可达。
- **建议修法**：拿到非数组时也置 `providersError`；或当「供应商为空但存在 providerId 非空的档案」时不直接判悬空，先提示刷新/检查权限。

---

## 2. 字段契约逐条核对（已核实为正常）

接口实测（浏览器内用页面自身 token 发 GET，**不含任何密钥明文**）：

```
providerKeys = [id, name, provider, baseUrl, hasApiKey, apiKeyMasked, enabled, modelCount, updatedAt]
profileKeys  = [id, name, providerId, providerName, providerEnabled, modelName, hasApiKey, apiKeyMasked,
                temperature, maxTokens, enabled, isDefault, isFallback, imageModelName, updatedAt]
providers    = [{id:1, name:"nexus.bx9y.com.cn", proto:"OPENAI_COMPATIBLE", baseUrl:"https://nexus.bx9y.com.cn",
                 hasApiKey:true, maskSet:true, enabled:true, modelCount:15},
                {id:2, name:"api.anthropic.com", proto:"ANTHROPIC", baseUrl:"https://api.anthropic.com",
                 hasApiKey:true, maskSet:true, enabled:true, modelCount:1}]
profiles     = 16 条，providerId 全非空，providerEnabled 全为 true，hasApiKey 全 true，
               imageModelName 全为 null，temperature 全 0.7，maxTokens 全 4096，nullPid=0
```

| 检查项 | 结论 | 证据 |
|---|---|---|
| 键名与顺序与 `ProviderView`/`ProfileView` 逐字一致 | ✔ | 上面 `*Keys` 与 `LlmProviderService.java:351-352`、`LlmProfileService.java:515-518` 完全相同，顺序也一致 |
| 供应商表单提交字段 = `ProviderRequest`（6 个） | ✔ | `ModelProfilesView.vue:172-177`（name/provider/baseUrl/apiKey/clearApiKey/enabled）↔ `LlmProviderService.java:347-349`；DOM 实测弹窗字段：供应商名称 / API 协议 / Base URL / API Key / 删除已保存的 API Key / 启用该供应商 |
| 档案表单提交字段 = `ProfileRequest`（7 个） | ✔ | `:239-244`（name/providerId/modelName/imageModelName/enabled/temperature/maxTokens）↔ `LlmProfileService.java:503-505`，无多无少 |
| 模型表单**没有** Base URL / API Key 输入框，改为只读「所属供应商」 | ✔ | DOM 实测编辑弹窗：`hasPasswordInput=false`，字段只有档案名称/所属供应商/上游模型名称/图片模型名称/Temperature/最大输出 Token/启用该档案；只读段落文本「所属供应商 nexus.bx9y.com.cn · https://nexus.bx9y.com.cn …」（契约 §6.1 `llm-provider-refactor.md:227-228`） |
| 编辑时「所属供应商」下拉的值绑定正确 | ✔ | 实测编辑「异端档D」：`select.value="2"`、选中项文本 `api.anthropic.com`，只读段落同源；新建时无下拉、`providerId` 由分组预置（`:225`、`:420`），实测只读段落显示 `api.anthropic.com` |
| `apiKey` 留空 = 不修改；`clearApiKey` = 清空；两者同时给时新 key 胜 | ✔ | `LlmProviderService.java:305-308`（先清空再写入）与前端 `:182-183` 的拦截（启用+清空+空 key → 前端先报错）一致；后端不变量 `:309-311` |
| 启停供应商走整条 PUT 不会误清密钥 | ✔ | `:197-200` 原样带 name/provider/baseUrl，`apiKey:''`+`clearApiKey:false` → `apply()` 不覆盖、不清空 |
| 停用/启用档案走整条 PUT 不会抹掉图片模型 | ✔ | `:279` 带上了 `imageModelName: profile.imageModelName || ''`（`null` 归范过，见下） |
| `ProviderView.enabled` 是可空 `Boolean` 的处理 | ✔ | 前端一律用 `=== true`（`:108`）/ `!== true`（`:199`、`:458`），与后端 `Boolean.TRUE.equals`（`LlmProviderService.java:157`、`:304`）同义；实测值为 `true` |
| `ProfileView.providerEnabled`/`hasApiKey` 是 primitive `boolean`，`providerEnabled !== false` 不会误判 | ✔ | 契约表格 `llm-provider-refactor.md:163-167`；实测两条供应商与 16 条档案里该字段恒存在且为布尔；方向也安全（缺省视为可用，与契约「存量回退行必须 true」的要求一致） |
| 悬空行的三键取值与前端 pill 分支匹配 | ✔ | 悬空 ⇒ `providerName=null, providerEnabled=false, hasApiKey=false`（`LlmProfileService.java:352-355`）→ 命中 `:393`「供应商已不存在」，不会掉到 `:395`「供应商未配置密钥」（`v-else-if` 链保证） |
| 旧版行 `imageModelName: null` 的 `null` 归范确实防住了「编辑即崩」 | ✔ | `:222-225`、`:242` 两处归范；实测 16 条档案 `imageModelName` 全为 `null`，编辑弹窗正常渲染（若漏归范会在 `.trim()` 抛 TypeError） |
| 删除护栏：默认档案不可删 / 供应商有模型不可删 | ✔ | 前端 `:413`、`:365` ↔ 后端 `LlmProfileService.java:200-206`、`LlmProviderService.java:127-135`（后端还有一道拒绝）；实测两个删除按钮均 `disabled` 且 title 数字与行内一致 |
| 两级分组与展开逻辑 | ✔ | 实测：默认展开第一个供应商；`aria-expanded` 正确翻转；一次只展开一组（点开 api.anthropic.com 后 nexus 自动收起，`region:false`）；dangling 恒展开且用非按钮的 `<span aria-hidden>` 占位（`:345-350`、`:122`）；删除/改名后 `ensureExpanded()` 回落到 `keys[0]`（`:127-132`） |
| 模型卡片按 id 升序（与后端「其余已启用」跳同序） | ✔ | `:66-68`、`:93-94`；实测 nexus 组顺序 打底档A/B/C、deepseek-flash… 与 `GET /api/llm-profiles` 的 id 升序一致 |
| 加载/空/错误三态互不冒充 | ✔ | `:327-339`：loading → providersError → profilesError → 空态；两个接口各自记错（`:134-152`），providers 取不到时不会把档案误判成悬空；实测两个请求均成功 |
| 非管理员口径 | ✔ | `auth.user?.role === 'ADMIN'`（`:13`）与后端角色字面量一致（`BootstrapAdminRunner.java:36`、`SystemUserService.java:14`）；所有写按钮都在 `v-if="isAdmin"` 内（`:310`、`:359`、`:363`、`:397`、`:409`、`:420`）；无写权限时接口报错会走 `:328-335` 的提示面板，文案自洽（「非管理员可忽略此项」） |
| 其它页面无残留读取被删字段 | ✔ | 全前端 grep `apiKeyMasked/hasApiKey/.baseUrl/.provider/providerName/providerEnabled/providerId`：除两个模型档案相关页面外，只有 `SettingsView.vue` 的同名键，而那是 `llm_config` 的字段，不是 `ProfileView` |
| `children` `imageChain` 复用点仍只有一处 | ✔ | `AgentsView.vue:5`、`:71`；`check-shared-imports` 通过 |
| 契约 §6.1 要求的行内元素齐全 | ✔ | 实测供应商行含：名称 · 协议标签（Chat Completions / Anthropic Messages）· Base URL · API Key 掩码 · N 个模型 · 启用开关 · [编辑][删除]；Base URL 与掩码值与 API 返回逐字相等 |
| A 段自检未被削弱且当前通过 | ✔ | `npm run check` 通过；内存反证实验（F2）显示跳序类篡改仍会 FAIL |

---

## 3. 已核实为正常（汇总）

1. **表单契约完全对齐**：模型表单 7 字段、供应商表单 6 字段，与后端 record 逐字一致；模型表单已无 Base URL / API Key 输入框，改为只读「所属供应商」段落（实测 DOM）。
2. **供应商下拉的停用标注**：`ModelProfilesView.vue:465` 对 `enabled !== true` 的供应商拼「（已停用）」，实测当前无停用供应商故未显形，但分支与 `ProviderView.enabled` 的可空语义一致。
3. **`getComputedStyle` 层面的两级布局生效**：`.provider-item` 的 padding/gap、`.provider-model-grid` 的自适应列、展开区底色均按 CSS 意图生效（截图与计算样式一致）。
4. **Esc / 展开 / 焦点前状态可复现**：Esc 关两个弹窗、caret 单展开、打开弹窗前 `activeElement` 为 BODY 且关闭后无残留弹窗。
5. **只读性**：整轮浏览器操作只产生 GET，弹窗全部正常关闭，未产生任何写请求。
6. **两个自检脚本仍挂在 `npm run check` 与 `prebuild` 上并通过**；A 段的拦截能力经内存反证实验确认未被削弱。

## 4. 未验证项（明确没做/做不到的）

1. **悬空 / 旧版两种分组的真实渲染未见到**：实测库 `nullPid=0`、无 `providerId` 指向缺失供应商的行，因此红色告警块、虚线 legacy 块、卡片「供应商已不存在」pill、legacy 卡片额外显示自身 API Key（`:405`）这几条只有代码级核对，**没有真实渲染证据**。
2. **所有写路径未执行**：保存供应商/档案、启停、设为默认/兜底、删除一律没点（含界面上的确认框）；所以「提交字段与后端 record 对齐」是读两侧源码 + 读 DOM 字段得出的，未验证 400/403 等错误码路径与成功回显文案。
3. **开关失败后不回滚视觉状态**（F3 后半）是 Vue `patchProps` 语义推理，未实测——只读约束下无法在不发写请求的前提下触发一次失败。
4. **非管理员视角未实测**：需要另一个账号登录（不允许），`providersError`/`profilesError` 面板与「无任何写按钮」只有代码级证据。
5. **F2 的脏 legacy 行未在真实实例上观察**：当前库里不存在 `baseUrl` 为空的 legacy 行，实验是用 `node` 构造数据做的（这已足以证明判据不等价，但不能证明线上会产生错误展示）。
6. **未重跑 `vite build` 做字节级复现**：以「源文件 mtime 早于产物」「产物含本轮新字符串」「dist 与 `target/classes/static` 逐字节一致」「live DOM 呈新结构」四点判定实例与源码同代。
7. **窄屏/移动端布局未实测**（`style.css:192` 新增 `.provider-model-grid` 单列断点只在代码里读过）。
8. **未跑后端构建/测试**（约束禁止 mvn），因此 F2/F3 的后端行为均以源码与契约为据，未由后端测试佐证。

## 5. 复现命令（只读，供 Lead 抽查）

```bash
# 1) 前端自检（不写产物）
cd webui && npm run check

# 2) F2 判据不等价（前端链会把 baseUrl 为 null 的 legacy 行收进链）
cd webui && node --input-type=module -e '
import { imageChain, imageCarrier } from "./src/utils/profiles.js";
const legacyDirty = {id:1,providerId:null,providerName:null,providerEnabled:true,provider:null,baseUrl:null,
                     hasApiKey:true,enabled:true,imageModelName:"img-FROM-DIRTY"};
const healthy = {id:2,providerId:1,providerName:"p",providerEnabled:true,provider:"OPENAI_COMPATIBLE",
                 baseUrl:"https://x",hasApiKey:true,enabled:true,imageModelName:"img-HEALTHY"};
console.log(imageChain([legacyDirty,healthy],null).map(p=>p.id+":"+p.name).join(", "));
console.log((imageCarrier([legacyDirty,healthy],null)||{}).imageModelName);'

# 3) 实例服务的产物与当前 dist 是否同代
for f in ModelProfilesView-D3P9cx-Y.js AgentsView-BSwnuLMR.js SettingsView-BcYCwtg9.js index-BR9WzD6c.js; do
  cmp -s webui/dist/assets/$f target/classes/static/assets/$f && echo "same: $f" || echo "DIFF: $f"; done
```
