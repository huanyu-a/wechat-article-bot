/**
 * 模型档案候选链（前端侧）。
 *
 * 这张卡片存在的唯一目的，是**如实**告诉用户「配图会用哪个模型」。因此这里的顺序必须
 * 与后端 `LlmProfileService.failoverChain` 逐跳一致：少抄一跳、或漏掉可用性过滤，
 * 界面就会报出一个后端根本不会用的模型名——那比不显示更糟。
 *
 * 后端顺序（`failoverChain` → `addIfUsable`）：
 *   1. 智能体绑定的档案
 *   2. 默认档案 —— 注意 `defaultProfile()` 是 `findDefault() ?? findFirst()`，
 *      **没有 is_default 时回落「全表第一条」**（这是最容易漏掉的一跳）
 *   3. 兜底档案（is_fallback）
 *   4. 其余已启用档案，按 id 升序
 * 每一跳都要求「已启用 + 有 key + 供应商未停用（+ 端点可拼出）」，并按 id 去重。
 *
 * ⚠️ 第 2 跳的回落取的是**未过滤**的全表第一条，不是「第一条可用档案」：
 * 后端先 `findFirst()` 再交给 `addIfUsable` 判断，若第一条恰好被停用，
 * 这一跳就**什么都不加**（而不是顺延到第二条可用档案）。照抄成
 * `candidates[0]` 会让兜底档案的位置提前，从而报出错误的图片模型。
 *
 * ⚠️ 端点与密钥自「供应商 + 模型」改造后归供应商所有（`llm_provider`）。后端 §4 的
 * 运行时解析把「供应商停用」折进 `enabled`、「供应商没配 key」折进 `apiKey`、并把
 * 「provider/baseUrl/apiKey 是否齐备」的最终结论折成 `runtimeAvailable`。判据仍然只有那一句
 * `usable()`，但它以 `runtimeAvailable` 为准（见下），**不再由前端自己复刻**。
 *
 * `providerEnabled` 另供界面标注（契约 §3.3，随同一接口下发、不需要 ADMIN）：
 *   供应商已停用/已被删时为 false，legacy 行一律 true。
 *
 * 图片模型走**两跳**（契约 §4.3）：① 链上第一个勾选了「这是图片模型」的档案（`isImageModel`）
 * → ①b 旧数据里声明过 `imageModelName` 的档案 → ② 回落旧的全局图片配置。
 * 这个文件只负责链的构造（`imageChain` / `imageCarrier` / `carrierModelName`）；
 * 「同一供应商同时供文本与图片」是靠**两条档案**表达的，不需要给供应商分「用途」。
 */

/**
 * 一条档案是否可用于候选链：与后端 `addIfUsable` 的判定体同判据。
 *
 * **判据只有一个真源**：后端在 `ProfileView` 里下发 `runtimeAvailable`，它就是
 * `RuntimeProfile.available()`（`enabled && provider && baseUrl && apiKey` 四项）的求值结果。
 * 前端不再自己复刻那四项——复刻过的判据已经漂移过一次（F2：后端收紧 `available()` 时
 * 前端仍在用 `enabled + hasApiKey + providerEnabled`，多算进了「有 key 但端点为空」的脏行）。
 *
 * 保留 `enabled` / `hasApiKey` / `providerEnabled` 三项的**唯一**理由，是兼容**不带新字段**
 * 的旧快照与自检夹具：它们缺省为 `true`（与 `providerEnabled !== false` 同一套「缺省宽松」
 * 口径），三个条件与后端折叠给这两个布尔时是等价的。带新字段时以 `runtimeAvailable` 为准。
 *
 * ⚠️ 不要用「字段存在就校验、不存在就放过」那类隐式判据（例如 `hasOwnProperty('provider')`）：
 * 它把「接口今天没下发这个字段」变成了契约，后端哪天补上字段就会**静默改变前端行为**——
 * 那正是 F2 这类漂移的成因。
 */
const usable = profile => Boolean(profile
  && profile.runtimeAvailable !== false
  && profile.enabled
  && profile.hasApiKey
  && profile.providerEnabled !== false)

const byIdAsc = profiles => profiles.slice().sort((a, b) => Number(a.id) - Number(b.id))

/**
 * 候选链：绑定档案 → 默认档案（无 is_default 时回落全表第一条）→ 兜底档案 → 其余已启用（id 升序）。
 *
 * @param {Array} profiles 档案列表（后端 `/api/llm-profiles` 的 `data`）
 * @param {*} boundId 智能体绑定的档案 id，可为空
 * @returns {Array} 去重、已过滤可用性后的候选链
 */
export function imageChain(profiles, boundId) {
  const ordered = byIdAsc(Array.isArray(profiles) ? profiles : [])
  const chain = []
  const seen = new Set()
  // 与后端 addIfUsable 同判据：id 非空 + runtimeAvailable + enabled + hasApiKey + 供应商未停用 + 未入链
  const add = profile => {
    if (!profile || profile.id == null || !usable(profile)) return
    if (seen.has(String(profile.id))) return
    seen.add(String(profile.id))
    chain.push(profile)
  }
  if (boundId != null && boundId !== '') add(ordered.find(p => String(p.id) === String(boundId)))
  // defaultProfile() = findDefault() ?? findFirst()：回落的是全表第一条，再由 add 判可用性
  add(ordered.find(p => p.isDefault) || ordered[0] || null)
  add(ordered.find(p => p.isFallback) || null)
  ordered.forEach(add)
  return chain
}

/**
 * 链上「图片载体」档案；都没有则返回 null（调用方回落全局图片模型）。两跳，与后端同序：
 *
 *  - **① 复选框**（契约 §4.3 ①）：第一个 `isImageModel === true` 且可用的档案。
 *    勾选即表示「这张档案本身就是图片模型」，图片模型名就是它自己的 `modelName`。
 *  - **①b 旧数据兼容**（契约 §4.3 ①b）：没有勾选的档案时，再看有没有声明过非空
 *    `imageModelName` 的档案。这是第二版界面留下的落点，**只读不改**，只为让已经那样
 *    配过的部署不炸；界面已经不再提供该输入框。
 *
 * @param {Array} profiles 档案列表
 * @param {*} boundId 智能体绑定的档案 id
 */
export function imageCarrier(profiles, boundId) {
  const chain = imageChain(profiles, boundId)
  return chain.find(p => p.isImageModel === true)
    || chain.find(p => (p.imageModelName || '').trim())
    || null
}

/**
 * 从载体档案取出真正发给上游的图片模型名（与 `imageCarrier` 的两跳对应）。
 *
 * ① 勾选的档案：`modelName`（它自己的模型名就是图片模型名）；
 * ①b 旧数据：遗留的 `imageModelName` 字段。
 *
 * @param {Object|null} profile `imageCarrier` 的返回值
 */
export function carrierModelName(profile) {
  if (!profile) return ''
  if (profile.isImageModel === true) return (profile.modelName || '').trim()
  return (profile.imageModelName || '').trim()
}
