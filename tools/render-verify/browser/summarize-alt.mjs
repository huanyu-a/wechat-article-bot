/**
 * 第十轮 · 「替代写法」/「注册表全族」两条路径的判定表（同一个脚本，靠命令行参数选集合）。
 *
 * 与 `summarize-combos.mjs` 同口径：一层看**后端产物**（渲染服务到底认不认这个写法），
 * 一层看**编辑器**（同一份产物往返之后还在不在）。
 *
 * 判定口径（与 79 样例 / 17 组合完全一致，不为本轮结论放宽）：
 *   - 后端：可见文字必须含 `must`、产物必须含 `mustHtml` 的结构、可见文字里 `:::` 一处都不能残留、
 *     原始产物里不能有 `forbidRaw` 的字面标签、声明的颜色必须真的出现；
 *   - 编辑器：先判 `na`（参照侧本身就 0 字符 / 参照侧就把语法当字面正文或字面元素透传 —— 没有可判的对象），
 *     再判 `pass`（文字逐字一致 + 结构特征一个不少 + 有非零盒子 + 颜色还在）/ `fail`。
 *
 * 用法：node tools/render-verify/browser/summarize-alt.mjs [alt|registry]
 *       node tools/render-verify/browser/summarize-alt.mjs [alt|registry] --selftest
 * 产物：target/probe/browser/<SET>_summary.md、<SET>_summary.json
 *
 * 退出码（**第二十九轮 E1 补**，此前这一支没有退出码——`9c/9d EXIT=0` 与 pass 数无关）：
 *   0 = 失败项 0；1 = **编辑器层**有 `fail`（逐条打印是哪一例、为什么）。
 *   ⚠️ **后端层的 `not-rendered` 不计入失败项**：注册表全族 76 组后端全是 not-rendered，
 *      那是「这一族在渲染服务里只有注册条目、没有语法分支」这一已确立的结论，
 *      把它算成本项目的红灯，等于让这道闸永远红着。本支要验收的是编辑器层。
 *   ⚠️ 编辑器层的 `na`（参照侧本身就是坏产物）同样不算失败——没有可判的对象。
 *   ⚠️ 判定口径一个字节没动，补的只是退出码。
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { OUT, BROWSER_OUT } from '../paths.mjs'
import { resolve } from 'node:path'
import { 判据, 自检, 失败, 克隆 } from '../gates.mjs'

const PROBE = OUT
const ARGS = process.argv.slice(2)
const SET = ARGS.find((item) => item === 'alt' || item === 'registry') || 'alt'
const raw = JSON.parse(readFileSync(resolve(BROWSER_OUT, SET + '_result.json'), 'utf8'))
const meta = JSON.parse(readFileSync(resolve(PROBE, SET, SET + '.json'), 'utf8'))
const caseById = new Map(meta.cases.map((item) => [item.id, item]))

const FEATURES = [
  ['svgTotal', 'SVG'], ['katexTotal', 'KaTeX'], ['katexDisplayTotal', '块级公式'],
  ['gradientElements', '渐变'], ['bulletSpans', '列表圆点'],
  ['subscriptCount', '下标'], ['superscriptCount', '上标'], ['codeHighlightSpans', '代码高亮'],
]
const squash = (text) => (text || '').replace(/\s+/g, '')

const rows = []
for (const sample of raw.samples) {
  const item = caseById.get(sample.id)
  if (!item) continue
  const ref = sample.reference
  const after = sample.after
  const backend = item.backend

  // ---- 后端层 ----
  const refText = squash(ref.text)
  const leakedColon = (refText.match(/:::/g) || []).length
  const missingMust = item.missingMust || []
  const missingHtml = item.missingHtml || {}
  const forbidden = (item.forbidRaw || []).filter((token) => squash(ref.html).includes(token))
  const colorsExpected = item.colors || {}
  const backendColorMissing = colorsExpected.backend
    ? !squash(ref.html).includes(squash(colorsExpected.backend)) : false
  // `leakRaw` 是「产物里留着字面标签元素」（标签式写法在上游的透传形态）：可见文字里看不见，
  // 但产物确实没把这个组件渲染出来，所以同样判 not-rendered。
  const backendOk = leakedColon === 0 && !missingMust.length && !Object.keys(missingHtml).length
    && !forbidden.length && !backendColorMissing && backend.leakRaw !== true

  // ---- 编辑器层 ----
  const textEqual = ref.text === after.text
  const missingFeatures = FEATURES
    .filter(([key]) => (ref[key] || 0) > 0 && (after[key] || 0) < (ref[key] || 0))
    .map(([key, label]) => `${label} ${ref[key]}→${after[key]}`)
  const lostEverything = after.visibleNodeCount === 0 && after.textLength === 0
  const editorForbidden = (item.forbidRaw || []).filter((token) => squash(after.html).includes(token))
  const editorColorMissing = colorsExpected.editor
    ? !squash(after.html).includes(squash(colorsExpected.editor)) : false

  let editorVerdict
  let reason
  if (ref.chars === 0) {
    // 参照侧自己就是 0 字符：上游没产出，编辑器无从谈起
    editorVerdict = 'na'
    reason = '参照侧（渲染服务产物）就是 0 字符——上游自己没产出，编辑器只忠实渲染一份坏产物'
  } else if (leakedColon > 0 || (backend.leakRaw && backend.leakedTag > 0) || (item.forbidRaw || []).some((token) => squash(ref.html).includes(token))) {
    // 参照侧把语法当字面正文 / 字面元素透传了 —— 没有「画得对不对」可判
    editorVerdict = 'na'
    reason = '参照侧本身就是坏产物（' + (leakedColon > 0 ? `可见文字里留着字面 \`:::\` ${leakedColon} 处` : '产物里留着字面标签元素')
      + '），编辑器只是忠实渲染，没有可判的对象'
  } else {
    const reasons = []
    if (!textEqual) reasons.push(`可见文字不一致：参照 ${ref.textLength} 字 / 编辑器 ${after.textLength} 字`)
    if (missingFeatures.length) reasons.push('编辑器侧少了参照侧有的结构：' + missingFeatures.join('；'))
    if (lostEverything) reasons.push('编辑器侧一个非零盒子都没有')
    if (editorForbidden.length) reasons.push('编辑器侧出现字面标签残留：' + editorForbidden.join('、'))
    if (editorColorMissing) reasons.push('编辑器侧颜色丢失（期望 ' + colorsExpected.editor + '）')
    editorVerdict = reasons.length ? 'fail' : 'pass'
    reason = reasons.length ? reasons.join('；')
      : `文字一致（${ref.textLength} 字），结构特征一个不少，${after.visibleNodeCount}/${after.nodeCount} 个元素有盒子`
  }

  rows.push({
    id: sample.id, replaces: item.replaces, upstreamItem: item.upstreamItem || '',
    label: item.label, expect: item.expect,
    backend: {
      verdict: backendOk ? 'ok' : 'not-rendered',
      chars: backend.chars, textLength: backend.textLength, svg: backend.svg,
      katex: backend.katex, leakedColon, leakedTag: backend.leakedTag,
      leakRaw: backend.leakRaw === true,
      missingMust, missingHtml, forbidden, colorMissing: backendColorMissing,
      colors: colorsExpected.backend || '',
      warnings: item.warnings,
    },
    editor: {
      verdict: editorVerdict, reason,
      refText: ref.textLength, afterText: after.textLength,
      refBoxes: `${ref.visibleNodeCount}/${ref.nodeCount}`, afterBoxes: `${after.visibleNodeCount}/${after.nodeCount}`,
      svg: `${ref.svgTotal}→${after.svgTotal}`, katex: `${ref.katexTotal}→${after.katexTotal}`,
      color: colorsExpected.backend ? `${colorsExpected.backend}→${colorsExpected.editor}` : '—',
    },
    shot: `shots/${SET}/${sample.id}.png`,
  })
}

const backendCounts = rows.reduce((acc, row) => { acc[row.backend.verdict] = (acc[row.backend.verdict] || 0) + 1; return acc }, {})
const editorCounts = rows.reduce((acc, row) => { acc[row.editor.verdict] = (acc[row.editor.verdict] || 0) + 1; return acc }, {})

// ===========================================================================
// 9c / 9d 的**判据**（纯函数；主流程与 `--selftest` 共用）。
// 与 9b 同口径：只看**编辑器层**的 `fail`。理由见文件头的退出码说明。
//
// ⚠️ **分母锚**（第三十二轮加）：应有条数不取 `rows.length`（那是从 `<SET>_result.json` 来的），
// 而是取**另一份产物** `<SET>/<SET>.json` 的 `cases`（由 `gen/round10_alternatives.py` /
// `gen/round10_registry_closure.py` 写的用例清单）——两个脚本各写一份，数对不上就红。
// 空表是这一条的特例（0 ≠ 应有）。
// ===========================================================================
const 应用例数 = meta.cases.length

const 判 = ({ rows: 表, 应有 }) => {
  const 清单 = 表.filter((row) => row.editor.verdict === 'fail')
    .map((row) => 失败('fail ' + row.id, row.editor.reason))
  if (表.length !== 应有) 清单.push(失败('用例数', 表.length + ' 条，应为 ' + 应有 + ' 条'
    + '——与 `' + SET + '/' + SET + '.json` 的用例清单对不上（分母变小/变大，'
    + '编辑器层的 pass / na / fail 分布也一起失真）。0 条是这一条的特例'))
  return 清单
}

if (ARGS.includes('--selftest')) {
  const 坏 = 克隆(rows)
  坏[0].editor.verdict = 'fail'
  坏[0].editor.reason = '（自检造）编辑器侧颜色丢失（期望 rgb(255, 0, 0)）'
  const 坏后端 = 克隆(rows)
  坏后端.forEach((row) => { row.backend.verdict = 'not-rendered' })   // 后端全没渲染：**不该**算本项目红灯
  process.exitCode = 自检('9' + (SET === 'alt' ? 'c' : 'd') + ' summarize-alt ' + SET, 判, [
    { 名: '当前存档（' + rows.length + ' 例）', 数据: { rows, 应有: 应用例数 }, 期望: 0,
      备注: '编辑器 pass ' + (editorCounts.pass || 0) + ' / na ' + (editorCounts.na || 0) + ' / fail ' + (editorCounts.fail || 0) },
    { 名: '把第 1 例的编辑器层改成 fail', 数据: { rows: 坏, 应有: 应用例数 }, 期望: 1, 备注: 坏[0].id },
    { 名: '把全部例子的后端层改成 not-rendered', 数据: { rows: 坏后端, 应有: 应用例数 }, 期望: 0,
      备注: '后端没渲染**不该**算本项目红灯（这正是判据不含后端层的原因）' },
    { 名: '空表（退化输入）', 数据: { rows: [], 应有: 应用例数 }, 期望: 1, 备注: '空输入不许判绿' },
    { 名: '分母变小：只留 1 例（第三十二轮加的用例）', 数据: { rows: 坏.slice(0, 1), 应有: 应用例数 }, 期望: 1,
      备注: '1 例 vs 应有 ' + 应用例数 + ' 例' },
  ])
} else {

const TITLES = { alt: '「等上游」9 条的替代写法', registry: '注册表 layout-* 全族（38 个名字 × 2 种写法）' }
const lines = []
lines.push(`# 第十轮 · ${TITLES[SET] || SET}（两条路径判定）`)
lines.push('')
lines.push(`- 浏览器：${raw.browser}（真实 Chrome，零新增依赖，自写 CDP 客户端）`)
lines.push(`- 用例数：**${rows.length}**，每例一张左（渲染服务产物）右（编辑器往返）同框截图 \`shots/${SET}/<id>.png\``)
lines.push(`- 图片：共 ${raw.page.images.total} 张有 \`src\` 的图片，成功 ${raw.page.images.loaded}，失败 ${raw.page.images.failed}`)
lines.push('')
lines.push(`## 汇总：后端产物 ok ${backendCounts.ok || 0} / not-rendered ${backendCounts['not-rendered'] || 0}；`
  + `编辑器 pass ${editorCounts.pass || 0} / na ${editorCounts.na || 0} / fail ${editorCounts.fail || 0}`)
lines.push('')
lines.push('| 用例 | 写法 | 后端产物 | 编辑器 | 文字 参照→编辑器 | 盒子 参照→编辑器 | 截图 |')
lines.push('| --- | --- | --- | --- | --- | --- | --- |')
for (const row of rows) {
  lines.push(`| \`${row.id}\` | ${row.label} | **${row.backend.verdict}**`
    + `（${row.backend.chars} 字符 / 文字 ${row.backend.textLength} / svg ${row.backend.svg} / \`:::\` 残留 ${row.backend.leakedColon}）`
    + ` | **${row.editor.verdict}** | ${row.editor.refText}→${row.editor.afterText} | ${row.editor.refBoxes}→${row.editor.afterBoxes}`
    + ` | [png](${row.shot}) |`)
}
lines.push('')
lines.push('## 编辑器层的 `na` 逐条理由')
lines.push('')
for (const row of rows.filter((item) => item.editor.verdict === 'na')) {
  lines.push(`- \`${row.id}\`：${row.editor.reason}`)
}
lines.push('')
lines.push('## 输入原文')
lines.push('')
lines.push('| 用例 | Markdown（原样） |')
lines.push('| --- | --- |')
for (const row of rows) {
  const markdown = readFileSync(resolve(PROBE, SET, row.id + '.md'), 'utf8')
  lines.push(`| \`${row.id}\` | \`${markdown.replace(/\n/g, '\\n').slice(0, 160)}\` |`)
}
lines.push('')

writeFileSync(resolve(BROWSER_OUT, SET + '_summary.md'), lines.join('\n'), 'utf8')
writeFileSync(resolve(BROWSER_OUT, SET + '_summary.json'), JSON.stringify({ backendCounts, editorCounts, rows }, null, 1), 'utf8')
console.log(lines.slice(0, 30).join('\n'))
console.log('\n后端汇总:', JSON.stringify(backendCounts), '编辑器汇总:', JSON.stringify(editorCounts))

process.exitCode = 判据('9' + (SET === 'alt' ? 'c' : 'd') + ' summarize-alt ' + SET
  + (SET === 'alt' ? '（等上游替代 10）' : '（注册表全族 76）'), 判, { rows, 应有: 应用例数 }) ? 1 : 0
}
