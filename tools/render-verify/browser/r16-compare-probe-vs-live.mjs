/**
 * 第二十二轮 · **探针页 vs 真实应用界面**的逐组比对（纯离线，不开浏览器、不打 API）。
 *
 * 为什么要有这一支：`r16-live-editor.mjs` 只负责「在真实界面里把探针量一遍」并落盘，
 * 「两边到底差几组、差在哪」是另一件事。把判定从驱动器里拆出来，好处是**改判据不用重跑浏览器**
 * ——本轮的结论正是靠反复改判据收敛的（先是 14/70，同宽后 1/70，最后确认那 1 组是图片加载时序）。
 *
 * 用法：
 *   node tools/render-verify/browser/r16-compare-probe-vs-live.mjs [liveResult.json] [--verbose]
 *   node tools/render-verify/browser/r16-compare-probe-vs-live.mjs --selftest   # 反例自检，不重复判定逻辑
 * 默认比对 `target/probe/browser/r16_live_widthmatch_result.json`（同宽那一支——
 * 只有同宽，两边数字才有可比性；异宽时几乎所有「宽度派生值」都会平移，见 README）。
 *
 * ⚠️ **第二十九轮 A 定性：本支属「甲类｜只比两侧一致」，对「两条路径一起错」天然免疫。**
 *    它量的是「探针页与真实应用界面是否同源一致」，而两边跑的是**同一份前端**——
 *    前端整体错，两边一起错，本支照样报 0 组差异、exit 0。
 *    实测（`r29_gate_audit`）：喂第二十二轮修复前的真实界面量测，**70 组里 0 组有差异、exit 0**。
 *    这是**定位**决定的，不改判据（改了就不是在量「两条入口是否同源」）；
 *    真正管「值对不对」的是 `summarize-r16.mjs`（参照栏 = 渲染服务产物，乙类）。
 *    另立 `--selftest` 证明它对**值级差异**确实会判红（不免疫 ≠ 瞎）。
 */
import { readFileSync, writeFileSync, existsSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { resolve } from 'node:path'
import { BROWSER_OUT, ROOT } from '../paths.mjs'

const ARGS = process.argv.slice(2)
const VERBOSE = ARGS.includes('--verbose')
const LIVE = resolve(BROWSER_OUT, ARGS.find((item) => item.endsWith('.json')) || 'r16_live_widthmatch_result.json')
const PROBE = resolve(BROWSER_OUT, 'r16_result.json')

/** 只留可比较的标量；`box` / `wrapperBox` 是像素值，异宽时必然不同，同宽时才纳入。 */
const IGNORED_KEYS = new Set(['inline', 'parentInline', 'src'])

// ---------------------------------------------------------------------------
// `--selftest`：**反例自检** —— 证明这把尺子不是恒绿的。
//
// 做法：拿三份输入 spawn 本脚本自己（`process.argv[1]`），**用的就是同一把尺子**，
// 不复制判定逻辑——这是第二十九轮 A 对「反例自检」的要求：自检必须走真判据，
// 不能另写一套「看起来一样」的比较器。
//   ① 当前包（同宽）             → 必须 exit 0（不误报）
//   ② 第二十二轮**异宽**那次量测 → 必须 exit 1（真能判红：宽度派生值成片平移）
//   ③ 现造一份改掉一个叶子的量测 → 必须 exit 1（真能判红：值级差异）
// 三条都成立才 exit 0；任何一条相反即说明闸写松了。
// ---------------------------------------------------------------------------
if (ARGS.includes('--selftest')) {
  const 本脚本 = resolve(process.argv[1])
  const run = (file) => spawnSync(process.execPath, file ? [本脚本, file] : [本脚本],
    { encoding: 'utf8', cwd: ROOT, timeout: 120000 })

  /** 在探针量测里找**一个**非图片、非忽略键的数值叶子——改它一个就够了。 */
  const 找叶子 = (node, path = '') => {
    if (!node || typeof node !== 'object') return null
    for (const key of Object.keys(node)) {
      if (IGNORED_KEYS.has(key)) continue
      const value = node[key]
      const at = path ? `${path}.${key}` : key
      if (typeof value === 'number' && !/(^|\.)(complete|natural|box|wrapperBox)(\.|$)/.test(at)) {
        return { 宿主: node, 键: key, at, 值: value }
      }
      if (value && typeof value === 'object') {
        const hit = 找叶子(value, at)
        if (hit) return hit
      }
    }
    return null
  }

  const bogus = (() => {
    const source = resolve(BROWSER_OUT, 'r16_live_widthmatch_result.json')
    if (!existsSync(source)) return null
    const payload = JSON.parse(readFileSync(source, 'utf8'))
    for (const item of payload.results) {
      for (const entry of item.editor.probes) {
        if (entry.editor?.kind === 'img') continue
        const hit = 找叶子(entry.editor)
        if (!hit) continue
        hit.宿主[hit.键] = hit.值 + 1
        const file = resolve(BROWSER_OUT, 'r16_live_r29bogus_result.json')
        writeFileSync(file, JSON.stringify(payload, null, 1), 'utf8')
        return { file, 改了: `${item.id}/${entry.probe}.${hit.at} ${hit.值}→${hit.值 + 1}` }
      }
    }
    return null
  })()

  const 用例 = [
    { 名: '① 当前包（同宽）· 不误报', 期望: 0, 参数: [] },
    { 名: '② 第二十二轮异宽量测 · 应判红', 期望: 1, 参数: ['r16_live_result.json'], 前置: 'r16_live_result.json' },
    { 名: '③ 现造改一个叶子的量测 · 应判红', 期望: 1, 参数: bogus ? [bogus.file] : null },
  ]
  if (bogus) console.log('反例自检 · 现造坏量测改的是：' + bogus.改了 + '（' + bogus.file + '）')
  let 全对 = true
  for (const item of 用例) {
    if (!item.参数) { console.log(`  ${item.名}：未做（缺前置产物）`); 全对 = false; continue }
    if (item.前置 && !existsSync(resolve(BROWSER_OUT, item.前置))) {
      console.log(`  ${item.名}：未做（缺 ${item.前置}）`); 全对 = false; continue
    }
    const out = run(item.参数[0])
    const line = (out.stdout || '').split('\n').find((text) => /有差异/.test(text)) || ''
    const 实际 = out.status === 0 ? 0 : 1
    const ok = 实际 === item.期望
    全对 = 全对 && ok
    console.log(`  ${item.名}：exit=${out.status}（期望 ${item.期望 === 0 ? '0' : '非 0'}）`
      + `${ok ? ' ✅' : ' ❌ 闸写松了'} · ${line.trim()}`)
  }
  console.log(全对 ? '→ 反例自检通过：本支对值级差异会判红，盲区是「两边一起错」（定位决定）。'
    : '→ 反例自检**不通过**：本支的判定与上面任一条不符，必须查。')
  process.exit(全对 ? 0 : 1)
}

for (const file of [LIVE, PROBE]) {
  if (!existsSync(file)) {
    console.error('缺少产物:', file)
    console.error('  探针页那份由 `browser/run-r16-browser.mjs` 产出；真实界面那份由 `browser/r16-live-editor.mjs` 产出。')
    process.exit(3)
  }
}

const live = JSON.parse(readFileSync(LIVE, 'utf8'))
const probe = JSON.parse(readFileSync(PROBE, 'utf8'))

/**
 * 逐键比较两个探针结果。返回差异键列表。
 *
 * ⚠️ 已知的三类**假阳性**（判定时必须先排除，否则会把量法问题当成缺陷）：
 *   1. 图片加载时序——`complete` / `natural` / `box` 取决于量的时候图有没有下完。
 *      探针页那份落盘早（`complete:false, natural:[0,0]`），真实界面等到了远端 WebP。
 *   2. 容器宽度派生值——`.paper{max-width:820px}` 把真实界面卡到内宽 684px，探针页 731px，
 *      差 47px；换行、列宽、行高都会跟着变。
 *   3. **`draggable` / `<div class="tableWrapper">` / 行内 style 属性顺序**——tiptap 在真实
 *      编辑器里会挂这些应用层痕迹，探针页也挂但顺序不同。它们是 DOM 结构差异，不是渲染差异。
 */
const diffKeys = (a, b, path = '', out = []) => {
  if (a === b) return out
  if (typeof a !== 'object' || typeof b !== 'object' || a === null || b === null) {
    out.push({ path, left: a, right: b })
    return out
  }
  const keys = new Set([...Object.keys(a), ...Object.keys(b)])
  for (const key of keys) {
    if (IGNORED_KEYS.has(key)) continue
    diffKeys(a[key], b[key], path ? `${path}.${key}` : key, out)
  }
  return out
}

const probeById = new Map(probe.samples.map((item) => [item.id, item]))
let groupsTotal = 0
let groupsDiffer = 0
let groupsTimingOnly = 0
const rows = []

for (const item of live.results) {
  const reference = probeById.get(item.id)
  if (!reference) { rows.push({ id: item.id, note: '探针页没有这一条' }); continue }
  const refProbes = new Map(reference.probes.map((entry) => [entry.probe, entry]))
  const own = []
  for (const entry of item.editor.probes) {
    groupsTotal += 1
    const left = refProbes.get(entry.probe)
    if (!left) { groupsDiffer += 1; own.push({ probe: entry.probe, diffs: [{ path: '（探针页缺这一组）' }] }); continue }
    const diffs = diffKeys(left.editor, entry.editor)
    // 图片加载时序：只有 complete / natural / box / wrapperBox 上的差，且本条探针确实是 img。
    // 探针页那份落盘早（图还没下完），真实界面等到了远端图 —— 是**记账时刻**，不是渲染行为。
    const isImage = left.editor?.kind === 'img'
    const timingOnly = isImage && diffs.length > 0 && diffs.every((diff) =>
      /(^|\.)(complete|natural|box|wrapperBox)(\.|$)/.test(diff.path))
    if (!diffs.length) continue
    if (timingOnly) { groupsTimingOnly += 1; own.push({ probe: entry.probe, diffs, timingOnly: true }); continue }
    groupsDiffer += 1
    own.push({ probe: entry.probe, diffs })
  }
  // 图片类探针单独统计：只报「加载时序」这一种解释
  const images = item.editor.probes.filter((entry) => entry.editor?.kind === 'img')
    .flatMap((entry) => entry.editor.items.map((image) => ({
      id: item.id, complete: image.complete, natural: image.natural,
    })))
  rows.push({ id: item.id, probes: item.editor.probes.length, differ: own,
    textLength: [item.editor.textLength, item.expectedTextLength], images })
}

console.log(`真实界面：${LIVE}`)
console.log(`探针页  ：${PROBE}`)
console.log(`模式 ${live.mode} · 两栏同宽 ${live.widthMatch ? '是' : '否'} · 文章 #${live.articleId}`)
console.log(`探针组数 ${groupsTotal} · 有差异 **${groupsDiffer}**`
  + `${groupsTimingOnly ? ` · 另有 ${groupsTimingOnly} 组只差图片加载时序（不计入）` : ''}`)
console.log('')
for (const row of rows) {
  const head = `  ${row.id}  探针 ${row.probes ?? '-'} 组 · 差异 ${row.differ?.length ?? '-'} 组`
    + ` · 正文 ${row.textLength ? row.textLength.join('/') + ' 字' : ''}`
  console.log(head)
  for (const entry of row.differ || []) {
    const mark = entry.timingOnly ? 'ⓘ' : '✗'
    const suffix = entry.timingOnly ? '  ← 只差图片加载时序，不计入差异' : ''
    console.log(`      ${mark} ${entry.probe}  ${entry.diffs.map((diff) => `${diff.path}=${JSON.stringify(diff.left)}→${JSON.stringify(diff.right)}`).join(' ; ')}${suffix}`)
  }
  for (const image of row.images || []) {
    if (VERBOSE || !image.complete) console.log(`      ⓘ 图片完整=${image.complete} 固有尺寸=${JSON.stringify(image.natural)}`)
  }
  if (row.note) console.log('      ' + row.note)
}

const allComplete = rows.flatMap((row) => row.images || []).every((image) => image.complete)
console.log('')
console.log(allComplete ? '图片全部加载完整 ✅' : '⚠️ 有图片在量的时候还没加载完（属加载时序，不是渲染差异）')

// ---------------------------------------------------------------------------
// `--dom`：换一把尺子再量一遍——直接比 DOM 字符串。
//
// ⚠️ **这一支不是承重的判据**，因为两边量的**根本不是同一个出口**：
//   - 探针页 `editor-setup.js:151` 量的是 `editor.getHTML()` —— **保存/导出出口**；
//   - 真实界面那一支量的是 `dom.innerHTML` —— **实时 DOM 出口**。
// 实时 DOM 上多出来的东西全是 ProseMirror 自己的编辑期管道（分隔图、尾随 `br`、
// `contenteditable="false"`、表格包裹层），**不参与保存，也不参与用户看到的渲染**。
// 所以这里**只做差异分类，不给通过/不通过**；真正的判定在探针比对那一支（计算样式，两边同源）。
// 需要「保存出口 vs 保存出口」的严格比对时，应当让驱动器改取 `editor.getHTML()`，
// 但那会改变真实界面那一支的量法，本轮**刻意没动**（本轮定位是只验收）。
// ---------------------------------------------------------------------------
if (ARGS.includes('--dom')) {
  /** 只抹掉**编辑期管道**，值本身一个字符不碰。 */
  const strip = (html) => html
    .replace(/\s*contenteditable="(?:true|false)"/g, '')
    .replace(/<img class="ProseMirror-separator"[^>]*>/g, '')
    .replace(/<br class="ProseMirror-trailingBreak">/g, '')
    .replace(/\s*draggable="true"/g, '')
    .replace(/<div class="tableWrapper">([\s\S]*?)<\/div>/g, '$1')
    .replace(/style="([^"]*)"/g, (_, value) => `style="${value.split(';').map((part) => part.trim())
      .filter(Boolean).sort().join(';')}"`)
    .replace(/\s+>/g, '>')
    .trim()
  const liveById = new Map(live.results.map((item) => [item.id, item.editor.html]))
  let same = 0
  const differing = []
  for (const item of probe.samples) {
    const right = liveById.get(item.id)
    if (right === undefined) continue
    if (strip(item.after.html) === strip(right)) same += 1
    else differing.push({ id: item.id, left: strip(item.after.html), right: strip(right) })
  }
  console.log('')
  console.log(`抹掉编辑期管道后一致：**${same}/${probe.samples.length}**`)
  for (const item of differing) {
    let at = 0
    while (at < item.left.length && item.left[at] === item.right[at]) at += 1
    console.log(`  ✗ ${item.id}（首个不同在第 ${at} 字符）`)
    if (VERBOSE) {
      console.log(`      探针页(getHTML) ${JSON.stringify(item.left.slice(at, at + 80))}`)
      console.log(`      真实界面(live) ${JSON.stringify(item.right.slice(at, at + 80))}`)
    }
  }
}

process.exitCode = groupsDiffer === 0 ? 0 : 1
