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
 * 每一跳都要求「已启用 + 有 key」，并按 id 去重。
 *
 * ⚠️ 第 2 跳的回落取的是**未过滤**的全表第一条，不是「第一条可用档案」：
 * 后端先 `findFirst()` 再交给 `addIfUsable` 判断，若第一条恰好被停用，
 * 这一跳就**什么都不加**（而不是顺延到第二条可用档案）。照抄成
 * `candidates[0]` 会让兜底档案的位置提前，从而报出错误的图片模型。
 */

/** 一条档案是否可用于候选链：与后端 `addIfUsable` 同判据。 */
const usable = profile => Boolean(profile && profile.enabled && profile.hasApiKey)

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
  // 与后端 addIfUsable 同判据：id 非空 + 已启用 + 有 key + 未入链
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
 * 链上第一个声明了 `imageModelName` 的档案；都没有则返回 null（调用方回落全局图片模型）。
 *
 * @param {Array} profiles 档案列表
 * @param {*} boundId 智能体绑定的档案 id
 */
export function imageCarrier(profiles, boundId) {
  return imageChain(profiles, boundId).find(p => (p.imageModelName || '').trim()) || null
}
