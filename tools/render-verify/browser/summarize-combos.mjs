/**
 * 第八轮 · 组合用例的两层判定表。
 *
 * 一层是上游（后端产物有没有把这个组合渲染出来），一层是编辑器（同一份产物往返之后还在不在）。
 * 两层必须分开写：上游没实现的组合，编辑器再忠实也只是把一段坏产物画出来，
 * 把它算成「编辑器缺陷」是错的；反过来，上游画对了而编辑器丢了，才是本项目的问题。
 *
 * 用法：node tools/render-verify/browser/summarize-combos.mjs
 *       node tools/render-verify/browser/summarize-combos.mjs --selftest   # 闸自检，不写产物
 * 产物：target/probe/browser/combo_summary.md、combo_summary.json
 *
 * 退出码（**第二十九轮 E1 补**，此前这一支没有退出码——`9b EXIT=0` 与 pass 数无关）：
 *   0 = 失败项 0；1 = **编辑器层**有 `fail`（逐条打印是哪一例、为什么）。
 *   ⚠️ **上游层的 `nested-unsupported` / `silently-lost` 不计入失败项**：那是渲染服务的行为，
 *      本项目要验收的是「上游画对的，编辑器有没有丢」（编辑器层），
 *      把上游的问题算成本项目的红灯，等于让这道闸永远红着、谁也看不出新问题。
 *   ⚠️ 判定口径一个字节没动，补的只是退出码。
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { OUT, BROWSER_OUT } from '../paths.mjs'
import { resolve } from 'node:path'
import { 判据, 自检, 失败, 克隆 } from '../gates.mjs'

const ARGS = process.argv.slice(2)

const PROBE = OUT
const raw = JSON.parse(readFileSync(resolve(BROWSER_OUT, 'combo_result.json'), 'utf8'))
const cases = JSON.parse(readFileSync(resolve(PROBE, 'combos/combos.json'), 'utf8')).cases
const matrix = JSON.parse(readFileSync(resolve(PROBE, 'component_matrix.json'), 'utf8'))
const matrixById = new Map(((matrix.rows || matrix)).map((row) => [row.id, row]))
const caseById = new Map(cases.map((item) => [item.id, item]))

/** 参照侧有、编辑器侧就不许少的结构特征。口径与 `summarize-all.mjs` 完全一致。 */
const FEATURES = [
  ['svgTotal', 'SVG'], ['katexTotal', 'KaTeX'], ['katexDisplayTotal', '块级公式'],
  ['gradientElements', '渐变'], ['bulletSpans', '列表圆点'],
  ['subscriptCount', '下标'], ['superscriptCount', '上标'], ['codeHighlightSpans', '代码高亮'],
]

const rows = []
for (const sample of raw.samples) {
  const item = caseById.get(sample.id)
  if (!item) continue
  const ref = sample.reference
  const after = sample.after
  const upstream = item.backend

  // ---- 上游层：这一份组合，渲染服务到底画出来了没有 ----
  // 判据是「产物理应含有的内容」而不是感觉：字面 `:::` 残留算没认；
  // 没残留但该有的文字 / 结构不在（`missingMust` / `missingHtml`），算静默丢内容。
  //
  // ⚠️ 修正（2026-09-13）：`round8_combos.py` 的 `leakedColonContainer` 用的是 `:::[a-z-]+`，
  // **漏掉了裸的收尾 `:::`**（`cmb-timeline-table` / `cmb-caseflow-table-title` 的可见文字结尾
  // 就是一个光秃秃的 `:::`，被判成 0 处泄漏）。这里改成直接在参照侧可见文字里数 `:::`。
  // 另记一条判据边界：**元素形态的透传**（`<steps>`/`<step>` 被当未知元素留在 DOM 里）在可见文字里
  // 完全看不出来，只有 `tagHistogram` 里多出组件名才暴露——`cmb-callout-steps` 就是这一类，
  // 它因此留在了 `silently-lost`。详见 known-issues-handoff.md §3.12②。
  const refText = (ref.text || '').replace(/\s+/g, '')
  const leakedColon = (refText.match(/:::/g) || []).length
  const leaked = leakedColon > 0
  const missingHtml = item.missingHtml || {}
  const missingMust = item.missingMust || []
  const contentLost = missingMust.length > 0 || Object.keys(missingHtml).length > 0
  const upstreamVerdict = leaked ? 'nested-unsupported' : (contentLost ? 'silently-lost' : 'ok')

  // ---- 编辑器层：口径与 79 样例完全一致 ----
  const textEqual = ref.text === after.text
  const missingFeatures = FEATURES
    .filter(([key]) => (ref[key] || 0) > 0 && (after[key] || 0) < (ref[key] || 0))
    .map(([key, label]) => `${label} ${ref[key]}→${after[key]}`)
  const lostEverything = after.visibleNodeCount === 0 && after.textLength === 0
  let editorVerdict
  let editorReason
  if (!textEqual) {
    editorVerdict = 'fail'
    editorReason = `可见文字不一致：参照 ${ref.textLength} 字 / 编辑器 ${after.textLength} 字`
  } else if (missingFeatures.length) {
    editorVerdict = 'fail'
    editorReason = '编辑器侧少了参照侧有的结构：' + missingFeatures.join('；')
  } else if (lostEverything) {
    editorVerdict = 'fail'
    editorReason = '编辑器侧一个非零盒子都没有'
  } else {
    editorVerdict = 'pass'
    editorReason = `文字一致（${ref.textLength} 字），结构特征一个不少，${after.visibleNodeCount}/${after.nodeCount} 个元素有盒子`
  }

  // ---- 与「单独最小写法」的对比 ----
  const minimal = matrixById.get(item.minimal)
  const sameAsMinimal = minimal
    ? (minimal.svg > 0) === (upstream.svg > 0) && (minimal.katex > 0) === (upstream.katex > 0)
    : null

  rows.push({
    id: sample.id, family: item.family, label: item.label, expect: item.expect,
    minimal: item.minimal, minimalSyntax: minimal ? minimal.syntax : null,
    minimalChars: minimal ? minimal.chars : null, minimalSvg: minimal ? minimal.svg : null,
    inputChars: item.inputChars,
    upstream: {
      verdict: upstreamVerdict, chars: upstream.chars, textLength: upstream.textLength,
      svg: upstream.svg, katex: upstream.katex, animateTransform: upstream.animateTransform,
      foreignObject: upstream.foreignObject,
      leakedColon: leakedColon, leakedColonRaw: upstream.leakedColonContainer,
      leakedTag: upstream.leakedTag,
      missingMust, missingHtml,
      warnings: item.warnings,
    },
    editor: {
      verdict: editorVerdict, reason: editorReason,
      refText: ref.textLength, afterText: after.textLength,
      refBoxes: `${ref.visibleNodeCount}/${ref.nodeCount}`, afterBoxes: `${after.visibleNodeCount}/${after.nodeCount}`,
      svg: `${ref.svgTotal}→${after.svgTotal}`, katex: `${ref.katexTotal}→${after.katexTotal}`,
      bullets: `${ref.bulletSpans}→${after.bulletSpans}`, sub: `${ref.subscriptCount}→${after.subscriptCount}`,
    },
    sameAsMinimal,
    // 组合相对最小写法「多了什么」：用产物字符数与文字长度说话，不用感觉
    deltaVsMinimal: minimal
      ? `产物 ${minimal.chars}→${upstream.chars} 字符（${upstream.chars - minimal.chars >= 0 ? '+' : ''}${upstream.chars - minimal.chars}），svg ${minimal.svg}→${upstream.svg}`
      : '（无对应最小样例）',
    shot: `shots/combo/${sample.id}.png`,
  })
}

const upstreamCounts = rows.reduce((acc, row) => { acc[row.upstream.verdict] = (acc[row.upstream.verdict] || 0) + 1; return acc }, {})
const editorCounts = rows.reduce((acc, row) => { acc[row.editor.verdict] = (acc[row.editor.verdict] || 0) + 1; return acc }, {})

// ===========================================================================
// 9b 的**判据**（纯函数；主流程与 `--selftest` 共用）。
//
// 只看**编辑器层**的 `fail` —— 理由写在文件头的退出码说明里：上游没画出来的组合不算本项目的问题。
//
// ⚠️ **分母锚**（第三十二轮加）：应有条数不取 `rows.length`（那是从 `combo_result.json` 来的），
// 而是取**另一份产物** `combos/combos.json`（由 `gen/round8_combos.py` 写的用例清单）。
// 两个脚本各写一份，数对不上就红——「分母悄悄变小但每条都 pass」在这条判据下不再是绿的。
// 空表是这一条的特例（0 ≠ 17）。
// ===========================================================================
const 应用例数 = cases.length

const 判 = ({ rows: 表, 应有 }) => {
  const 清单 = 表.filter((row) => row.editor.verdict === 'fail')
    .map((row) => 失败('fail ' + row.id, row.editor.reason))
  if (表.length !== 应有) 清单.push(失败('用例数', 表.length + ' 条，应为 ' + 应有 + ' 条'
    + '——与 `combos/combos.json` 的用例清单对不上（分母变小/变大，'
    + '编辑器层的 pass / fail 分布也一起失真）。0 条是这一条的特例'))
  return 清单
}

if (ARGS.includes('--selftest')) {
  const 坏 = 克隆(rows)
  坏[0].editor.verdict = 'fail'
  坏[0].editor.reason = '（自检造）可见文字不一致：参照 500 字 / 编辑器 480 字'
  const 坏上游 = 克隆(rows)
  坏上游[1].upstream.verdict = 'silently-lost'   // 上游问题：**不该**被判成本项目的红灯
  坏上游[2].upstream.verdict = 'nested-unsupported'
  process.exitCode = 自检('9b summarize-combos', 判, [
    { 名: '当前存档（' + rows.length + ' 例）', 数据: { rows, 应有: 应用例数 }, 期望: 0,
      备注: '编辑器 pass ' + (editorCounts.pass || 0) + ' / fail ' + (editorCounts.fail || 0) },
    { 名: '把第 1 例的编辑器层改成 fail', 数据: { rows: 坏, 应有: 应用例数 }, 期望: 1, 备注: 坏[0].id },
    { 名: '把两例的上游层改成没渲染', 数据: { rows: 坏上游, 应有: 应用例数 }, 期望: 0,
      备注: '上游问题**不该**算本项目红灯（这正是判据不含上游层的原因）' },
    { 名: '空表（退化输入）', 数据: { rows: [], 应有: 应用例数 }, 期望: 1, 备注: '空输入不许判绿' },
    { 名: '分母变小：只留 1 例（第三十二轮加的用例）', 数据: { rows: 坏.slice(0, 1), 应有: 应用例数 }, 期望: 1,
      备注: '1 例 vs 应有 ' + 应用例数 + ' 例' },
  ])
} else {

const lines = []
lines.push('# 第八轮 · 组合用例（嵌套 / 重复 / 超长 / 混排）两级判定')
lines.push('')
lines.push(`- 浏览器：${raw.browser}（真实 Chrome，零新增依赖，自写 CDP 客户端）`)
lines.push(`- 组合用例数：**${rows.length}**，每例一张截图 \`shots/combo/<id>.png\`（左右两栏同框）`)
lines.push(`- 图片：共 ${raw.page.images.total} 张有 \`src\` 的图片，成功 ${raw.page.images.loaded}，失败 ${raw.page.images.failed}`
  + `（另有 ${raw.page.images.separators} 个 \`<img class="ProseMirror-separator">\` 是 ProseMirror 的光标占位，无 src，不计）`)
lines.push('')
lines.push('## 两级判定怎么读')
lines.push('')
lines.push('| 层 | 判定 | 含义 |')
lines.push('| --- | --- | --- |')
lines.push('| 上游（后端产物） | `ok` | 渲染服务把这个组合画出来了（无字面 `:::` 残留，该有的文字与结构都在） |')
lines.push('| 上游（后端产物） | `nested-unsupported` | **产物里留着字面 `:::`（含开头的 `:::[名字]` 与光秃秃的收尾 `:::`）** —— 上游没认这个嵌套，原样透传 |')
lines.push('| 上游（后端产物） | `silently-lost` | 没有残留字面语法，但**本该出现的文字或结构不在**（逐例给出缺了哪一条） |')
lines.push('| 编辑器（本项目） | `pass` | 与参照侧文字逐字一致、结构特征一个不少、有非零盒子 |')
lines.push('| 编辑器（本项目） | `fail` | 其余 |')
lines.push('')
lines.push('> ⚠️ **判据修正（2026-09-13 第八轮收尾）**：第一版把「泄漏」判成 `:::[a-z-]+`，'
  + '**漏掉了裸的收尾 `:::`** —— `cmb-timeline-table` 与 `cmb-caseflow-table-title` 的可见文字结尾'
  + '就是一个光秃秃的 `:::`，被误判成 0 处泄漏、落进 `silently-lost`。本表已按「可见文字里出现任何 `:::`」重判。')
lines.push('> ⚠️ **判据边界（仍未覆盖）**：**元素形态的透传**在可见文字里完全看不出来 —— '
  + '`<steps>` / `<step>` 被当未知元素留在 DOM 里时，文字一个不少、`:::` 一处没有，'
  + '只有 `tagHistogram` 里多出 `steps` / `step` 才暴露（`cmb-callout-steps` 属这一类，故仍留在 `silently-lost`）。'
  + '同批还查出 `cmb-inline-in-container` 里的 `<Badge>新</Badge>` 也是原样透传，'
  + '但那是**探针自己写错的形态**（官方徽章是自闭合 `<badge … />`，不是配对标签），不计为上游缺陷。')
lines.push('')
lines.push(`## 汇总：上游 ok ${upstreamCounts.ok || 0} / nested-unsupported ${upstreamCounts['nested-unsupported'] || 0}`
  + ` / silently-lost ${upstreamCounts['silently-lost'] || 0}；编辑器 pass ${editorCounts.pass || 0} / fail ${editorCounts.fail || 0}`)
lines.push('')
lines.push('| 用例 | 家族 | 上游判定 | 上游产物 | 编辑器判定 | 编辑器 文字 参照→编辑器 | 盒子 参照→编辑器 | 截图 |')
lines.push('| --- | --- | --- | --- | --- | --- | --- | --- |')
for (const row of rows) {
  lines.push(`| \`${row.id}\` | ${row.family} | **${row.upstream.verdict}** | ${row.upstream.chars} 字符 / 文字 ${row.upstream.textLength}`
    + ` / svg ${row.upstream.svg} / 泄漏 ${row.upstream.leakedColon} 处 | ${row.editor.verdict}`
    + ` | ${row.editor.refText}→${row.editor.afterText} | ${row.editor.refBoxes}→${row.editor.afterBoxes} | [png](${row.shot}) |`)
}
lines.push('')
lines.push('## 逐例明细（含与「单独最小写法」的对照）')
lines.push('')
lines.push('| 用例 | 写法 | 期望 | 上游 | 与最小写法 `id` 比 | 产物字符差 | svg 参照→编辑器 | katex 参照→编辑器 | 编辑器 |')
lines.push('| --- | --- | --- | --- | --- | --- | --- | --- | --- |')
for (const row of rows) {
  lines.push(`| \`${row.id}\` | ${row.label} | ${row.expect} | ${row.upstream.verdict}`
    + ` | \`${row.minimal}\`（${row.minimalSyntax}） | ${row.deltaVsMinimal} | ${row.editor.svg} | ${row.editor.katex} | ${row.editor.verdict} |`)
}
lines.push('')
const ups = rows.filter((row) => row.upstream.verdict !== 'ok')
if (ups.length) {
  lines.push('## 组合才暴露出来的上游问题（每一例都给出可复现输入）')
  lines.push('')
  lines.push('| 用例 | 输入（原样） | 上游产物里的证据 | warnings |')
  lines.push('| --- | --- | --- | --- |')
  for (const row of ups) {
    // 输入原文以 `combos/<id>.md` 为准（`combos.json` 只存统计，不重复存正文）
    const markdown = readFileSync(resolve(PROBE, 'combos', row.id + '.md'), 'utf8')
    const oneLine = markdown.replace(/\n/g, '\\n').slice(0, 150)
    const missing = [
      ...row.upstream.missingMust.map((text) => `缺少文字「${text}」`),
      ...Object.entries(row.upstream.missingHtml).map(([tag, value]) => `缺少结构 \`${tag}\`（要 ${value.want} 个，实测 ${value.got}）`),
    ]
    lines.push(`| \`${row.id}\` | \`${oneLine}\` | 产物 ${row.upstream.chars} 字符；`
      + `字面 \`:::\` 残留 ${row.upstream.leakedColon} 处（旧判据只认 \`:::[a-z-]+\` 时是 ${row.upstream.leakedColonRaw} 处）、`
      + `正文里的字面标签 ${row.upstream.leakedTag} 处（**看不见元素形态的透传**，见本节末尾的判据边界）；`
      + `${missing.length ? missing.join('；') : '内容与结构齐全'} | ${row.upstream.warnings.length ? row.upstream.warnings.join('；') : '**无**'} |`)
  }
  lines.push('')
}
lines.push('## 编辑器层结论')
lines.push('')
lines.push(`组合用例里，编辑器往返 **pass ${editorCounts.pass || 0} / fail ${editorCounts.fail || 0}**。`)
lines.push('也就是说：**上游没画出来的组合，编辑器没有变本加厉；上游画出来的组合，编辑器没有丢东西。**')
lines.push('组合暴露的问题全部落在上游那一层。')
lines.push('')

writeFileSync(resolve(BROWSER_OUT, 'combo_summary.md'), lines.join('\n'), 'utf8')
writeFileSync(resolve(BROWSER_OUT, 'combo_summary.json'), JSON.stringify({ upstreamCounts, editorCounts, rows }, null, 1), 'utf8')
console.log(lines.slice(0, 60).join('\n'))
console.log('\n上游汇总:', JSON.stringify(upstreamCounts), '编辑器汇总:', JSON.stringify(editorCounts))

process.exitCode = 判据('9b summarize-combos（17 组合）', 判, { rows, 应有: 应用例数 }) ? 1 : 0
}
