/**
 * 把全量探针的 `all_result.json` 压成「79 个样例逐个判定」的结论表。
 *
 * 判定的原则：**只看两栏的实测差**，不做人工挑样本，也不拿「这个组件应该没问题」当理由。
 *   参照栏 = 渲染服务自己的产物（上游认为这个组件应该长什么样）
 *   编辑器栏 = 同一份产物经当前扩展集 setContent → getHTML 之后再渲染的结果
 *
 * 三种判定：
 *   pass  两栏归一化文字一致，且参照侧有的结构特征编辑器侧一个不少，且编辑器侧真有非零盒子
 *   na    参照侧自己就没有可谈的东西（产物 0 字符 / 参照侧就把语法当字面正文透传了）
 *   fail  其余（文字变了 / 特征丢了 / 一个盒子都没有）
 *
 * 用法：node tools/render-verify/browser/summarize-all.mjs
 * 产物：target/probe/browser/all_summary.md、all_summary.json
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { OUT, BROWSER_OUT } from '../paths.mjs'
import { resolve } from 'node:path'

const PROBE = OUT
const raw = JSON.parse(readFileSync(resolve(BROWSER_OUT, 'all_result.json'), 'utf8'))
const matrix = JSON.parse(readFileSync(resolve(PROBE, 'component_matrix.json'), 'utf8'))
const matrixById = new Map((matrix.rows || matrix).map((row) => [row.id, row]))

/**
 * 参照侧「有没有把这个语法当字面正文透传」。
 *
 * 判据是拿样例自己的 syntax 里那个可辨识的起始记号去参照侧可见文字里找：
 *   `<hint>（官方无此写法）` → 找 `<hint`；`:::table title=` → 找 `:::table`。
 * 找不到记号就没有可判的（返回 null）。
 */
function literalMarkers(syntax) {
  const markers = []
  const tag = syntax.match(/<([a-z][a-z0-9-]*)/i)
  if (tag) markers.push('<' + tag[1])
  const container = syntax.match(/:{3}([a-z][a-z0-9-]*)/i)
  if (container) markers.push(':::' + container[1])
  if (/:{3}/.test(syntax) && !container) markers.push(':::')
  return markers
}

/**
 * 「参照侧自己就没产出东西」的样例清单。
 *
 * 这些**不是**编辑器的问题：渲染服务收到这份写法后，产物里不是组件、而是把语法当正文吐了出来
 * （或者干脆 0 字符）。编辑器只是忠实地把一份已经坏掉的产物渲染出来——所以没有可判的「编辑器有没有画对」。
 *
 * 每一条都必须**当场用 check 复验**：check 返回 false 就说明我记的现场和实测对不上，
 * 这条会掉进 `unverified` 而不是悄悄算成 na。这样这份清单也能被反证。
 */
const NOT_IMPLEMENTED_UPSTREAM = [
  {
    id: 'blk-case-flow-badline',
    evidence: '产物 0 字符（行格式不合法时整块归零）；两次都是 ok:true + warnings:[]',
    check: (ref) => ref.chars === 0,
  },
  {
    id: 'blk-slider-selfclose',
    evidence: 'HTML 里留着字面 `<slider …>` 元素、0 个 `<svg>`、0 个非零盒子（上游 R4）',
    check: (ref) => /<slider\b/i.test(ref.html) && ref.svgTotal === 0 && ref.visibleNodeCount === 0,
  },
  {
    id: 'reg-hint-tag',
    evidence: 'HTML 里留着字面 `<hint>` 元素——官方只有容器式 `:::hint`，没有标签式',
    check: (ref) => /<hint\b/i.test(ref.html),
  },
  {
    id: 'reg-layout-hero-tag',
    evidence: 'HTML 里留着字面 `<layout-hero>` 元素——该 ID 在 bundle 里只有注册表条目，没有语法分支',
    check: (ref) => /<layout-hero\b/i.test(ref.html),
  },
  {
    id: 'ctn-success',
    evidence: '可见文字里留着字面 `:::success`（容器没被识别，产物是兜底段落）',
    check: (ref) => ref.text.includes(':::success'),
  },
  {
    id: 'ctn-danger',
    evidence: '可见文字里留着字面 `:::danger`（同上；matrix 里本来就标注了 guide 说不在支持列表）',
    check: (ref) => ref.text.includes(':::danger'),
  },
  {
    id: 'reg-layout-hero',
    evidence: '可见文字里留着字面 `:::layout-hero`——**产物那 576 字符是兜底段落，不是渲染**',
    check: (ref) => ref.text.includes(':::layout-hero'),
  },
  {
    id: 'reg-layout-toc',
    evidence: '可见文字里留着字面 `:::layout-toc`',
    check: (ref) => ref.text.includes(':::layout-toc'),
  },
  {
    id: 'reg-layout-metrics',
    evidence: '可见文字里留着字面 `:::layout-metrics`',
    check: (ref) => ref.text.includes(':::layout-metrics'),
  },
]
const naById = new Map(NOT_IMPLEMENTED_UPSTREAM.map((item) => [item.id, item]))

/** 参照侧有、编辑器侧就不许少的结构特征。每一项都是「画出来了」的可测证据。 */
const FEATURES = [
  ['svgTotal', 'SVG（轮播 / 图标）'],
  ['katexTotal', 'KaTeX 公式'],
  ['katexDisplayTotal', '块级公式'],
  ['gradientElements', '渐变底纹'],
  ['bulletSpans', '列表圆点'],
  ['subscriptCount', '下标'],
  ['superscriptCount', '上标'],
  ['codeHighlightSpans', '代码高亮'],
]

const rows = []
for (const sample of raw.samples) {
  const ref = sample.reference
  const after = sample.after
  const row = matrixById.get(sample.id) || {}

  const textEqual = ref.text === after.text
  const missingFeatures = FEATURES
    .filter(([key]) => (ref[key] || 0) > 0 && (after[key] || 0) < (ref[key] || 0))
    .map(([key, label]) => `${label} ${ref[key]}→${after[key]}`)
  const gainedFeatures = FEATURES
    .filter(([key]) => (ref[key] || 0) === 0 && (after[key] || 0) > 0)
    .map(([key, label]) => `${label} 0→${after[key]}`)

  const markers = literalMarkers(sample.syntax || '')
  const leakedInReference = markers.length > 0 && markers.some((marker) => ref.text.includes(marker))
  const lostEverything = after.visibleNodeCount === 0 && after.textLength === 0

  const naRule = naById.get(sample.id)
  const naHolds = naRule ? naRule.check(ref) : false

  let verdict
  let reason
  if (naRule && naHolds) {
    verdict = 'na'
    reason = naRule.evidence
  } else if (naRule && !naHolds) {
    // 清单里记的现场和实测对不上——不能算 na，也不能悄悄放过
    verdict = 'unverified'
    reason = `na 清单里记的现场与本轮实测不符：声称「${naRule.evidence}」，`
      + `实测 产物 ${ref.chars} 字符 / 盒子 ${ref.visibleNodeCount}/${ref.nodeCount} / svg ${ref.svgTotal} / 文字「${ref.text.slice(0, 40)}」`
  } else if (ref.chars === 0) {
    verdict = 'na'
    reason = '参照侧（渲染服务产物）就是 0 字符——上游自己没产出，编辑器无从谈起'
  } else if (leakedInReference) {
    verdict = 'na'
    reason = `参照侧把语法当字面正文透传了（可见文字里含 ${markers.filter((m) => ref.text.includes(m)).join('、')}）——`
      + '这不是编辑器的问题，是这份样例的写法在上游根本没有对应实现'
  } else if (!textEqual) {
    verdict = 'fail'
    reason = `可见文字不一致：参照 ${ref.textLength} 字 / 编辑器 ${after.textLength} 字`
  } else if (missingFeatures.length) {
    verdict = 'fail'
    reason = '编辑器侧少了参照侧有的结构：' + missingFeatures.join('；')
  } else if (lostEverything) {
    verdict = 'fail'
    reason = '编辑器侧一个非零盒子都没有'
  } else {
    verdict = 'pass'
    reason = after.visibleNodeCount === after.nodeCount
      ? '文字与结构特征两侧一致，且所有元素都有非零盒子'
      : `文字与结构特征两侧一致（${after.visibleNodeCount}/${after.nodeCount} 个元素有盒子，`
        + `${after.zeroBoxNodes} 个零盒子元素是换行/定位占位）`
  }

  rows.push({
    id: sample.id, syntax: sample.syntax, category: sample.category, verdict, reason,
    glyphs: row.markers ? row.markers.length : null,
    textEqual, missingFeatures, gainedFeatures,
    refText: ref.textLength, afterText: after.textLength,
    refBoxes: `${ref.visibleNodeCount}/${ref.nodeCount}`, afterBoxes: `${after.visibleNodeCount}/${after.nodeCount}`,
    features: Object.fromEntries(FEATURES.map(([key, label]) => [label, `${ref[key]}→${after[key]}`])),
    svgForeignObjects: `${ref.svgForeignObjects}→${after.svgForeignObjects}`,
    svgAnimateTransform: `${ref.svgAnimateTransform}→${after.svgAnimateTransform}`,
    katexHeights: `${JSON.stringify(ref.katexHeightsAfterFonts || ref.katexHeights)}→${JSON.stringify(after.katexHeightsAfterFonts || after.katexHeights)}`,
    strongCount: `${ref.strongCount}→${after.strongCount}`,
    blockClasses: `${JSON.stringify(ref.blockClasses)}→${JSON.stringify(after.blockClasses)}`,
    afterTextSample: after.text.slice(0, 200),
    shot: `shots/all/${sample.id}.png`,
  })
}

const counts = rows.reduce((acc, row) => { acc[row.verdict] = (acc[row.verdict] || 0) + 1; return acc }, {})
const byCategory = {}
for (const row of rows) {
  byCategory[row.category] = byCategory[row.category] || { pass: 0, fail: 0, na: 0, unverified: 0 }
  byCategory[row.category][row.verdict] += 1
}

const lines = []
lines.push('# 全量 79 样例 · 真实浏览器逐个判定')
lines.push('')
lines.push(`- 浏览器：${raw.browser}（Chrome 138，无头；零新增依赖，驱动是自写的 CDP 客户端）`)
lines.push(`- 视口：${raw.page.viewport.join('×')}`)
lines.push(`- 样例数：**${rows.length}**（= \`target/probe/components/\` 里全部样例，逐个一行，没有挑）`)
lines.push(`- 截图：每行一张 \`shots/all/<id>.png\`，左右两栏同框、表头带字数与「有盒子的元素数」`)
lines.push(`- 图片：共 ${raw.page.images.total} 张**有 src 的真实图片**，成功 ${raw.page.images.loaded}，失败 ${raw.page.images.failed}`
  + `（横幅图实测 1080×784 已解码；另有 ${raw.page.images.proseMirrorSeparators} 个 \`<img class="ProseMirror-separator">\` 是`
  + ' ProseMirror 给空行内内容插的光标占位、没有 src，**不计入图片统计**——第一次跑就是把它算成「图没加载」了）')
lines.push(`- \`document.fonts.check('16px KaTeX_Main')\`：挂载前 ${raw.page.katexFontLoadedBeforeMount}`
  + ` → 量高度时 **${raw.page.katexFontLoaded}**（挂载前页面上还没有公式，浏览器不会去下载 webfont，那一刻必然是 false）`)
lines.push('')
lines.push('## 判定口径')
lines.push('')
lines.push('| 判定 | 含义 |')
lines.push('| --- | --- |')
lines.push('| `pass` | 两栏归一化文字一致；参照侧有的结构特征（SVG / KaTeX / 渐变 / 圆点 / 上下标 / 代码高亮）编辑器侧一个不少；编辑器侧真有非零盒子 |')
lines.push('| `na` | **参照侧自己就没产出东西**：产物 0 字符，或把语法当正文吐了出来。编辑器只是忠实渲染一份已经坏掉的产物，没有「画得对不对」可判 |')
lines.push('| `fail` | 其余：可见文字变了 / 特征丢了 / 一个盒子都没有 |')
lines.push('| `unverified` | na 清单里记的现场与本轮实测对不上（不该出现；出现即说明结论过期） |')
lines.push('')
lines.push(`## 汇总：pass ${counts.pass || 0} / na ${counts.na || 0} / fail ${counts.fail || 0}`
  + ` / unverified ${counts.unverified || 0}`)
lines.push('')
lines.push('| 分类 | pass | na | fail | unverified |')
lines.push('| --- | --- | --- | --- | --- |')
for (const [category, value] of Object.entries(byCategory)) {
  lines.push(`| ${category} | ${value.pass} | ${value.na} | ${value.fail} | ${value.unverified} |`)
}
lines.push('')
lines.push('## na 的逐条理由（每条的现场都用 check 当场复验过）')
lines.push('')
lines.push('| 样例 | 写法 | 参照侧实测现场 | 编辑器侧实测 | 截图 |')
lines.push('| --- | --- | --- | --- | --- |')
for (const row of rows.filter((item) => item.verdict === 'na')) {
  const rule = naById.get(row.id)
  lines.push(`| ${row.id} | \`${row.syntax}\` | ${rule.evidence} | 文字 ${row.afterText} 字 / 盒子 ${row.afterBoxes} | [png](${row.shot}) |`)
}
lines.push('')
if (counts.fail || counts.unverified) {
  lines.push('## 未通过的样例（逐条给出实测差）')
  lines.push('')
  lines.push('| 样例 | 写法 | 判定 | 原因 | 参照盒子 | 编辑器盒子 | 截图 |')
  lines.push('| --- | --- | --- | --- | --- | --- | --- |')
  for (const row of rows.filter((item) => item.verdict === 'fail' || item.verdict === 'unverified')) {
    lines.push(`| ${row.id} | \`${row.syntax}\` | ${row.verdict} | ${row.reason} | ${row.refBoxes} | ${row.afterBoxes} | [png](${row.shot}) |`)
  }
  lines.push('')
}
lines.push('## 逐条明细（79 行）')
lines.push('')
lines.push('| 样例 | 写法 | 判定 | 可见文字 参照→编辑器 | 盒子 参照→编辑器 | svg | katex | 渐变 | 圆点 | 高亮span | 截图 |')
lines.push('| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |')
for (const row of rows) {
  lines.push(`| ${row.id} | \`${row.syntax}\` | ${row.verdict} | ${row.refText}→${row.afterText} | ${row.refBoxes}→${row.afterBoxes}`
    + ` | ${row.features['SVG（轮播 / 图标）']} | ${row.features['KaTeX 公式']} | ${row.features['渐变底纹']}`
    + ` | ${row.features['列表圆点']} | ${row.features['代码高亮']} | [png](${row.shot}) |`)
}
lines.push('')
lines.push('## 附：公式与轮播的实测高度（证明不是「有元素但没画出来」）')
lines.push('')
lines.push('| 样例 | KaTeX 高度 参照→编辑器（等 webfont 之后） | svg foreignObject 参照→编辑器 | svg animateTransform 参照→编辑器 |')
lines.push('| --- | --- | --- | --- |')
for (const row of rows.filter((item) => item.id.startsWith('math-') || item.id.includes('slider'))) {
  lines.push(`| ${row.id} | ${row.katexHeights} | ${row.svgForeignObjects} | ${row.svgAnimateTransform} |`)
}
lines.push('')

writeFileSync(resolve(BROWSER_OUT, 'all_summary.md'), lines.join('\n'), 'utf8')
writeFileSync(resolve(BROWSER_OUT, 'all_summary.json'), JSON.stringify({ counts, byCategory, rows }, null, 1), 'utf8')
console.log(lines.slice(0, 40).join('\n'))
console.log('\n判定汇总:', JSON.stringify(counts))
for (const row of rows.filter((item) => item.verdict !== 'pass')) {
  console.log(`  ${row.verdict}  ${row.id.padEnd(24)} ${row.reason}`)
}
