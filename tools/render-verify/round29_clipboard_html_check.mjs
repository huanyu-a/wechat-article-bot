/**
 * 第二十九轮 C · **「什么样的剪贴板 HTML 复制进来会触发」的可跑判据**（纯离线，不开浏览器、不连库）。
 *
 * 判据（本轮先立后测，逐字给定）：
 *
 *   **一份剪贴板 HTML，只要「段首有 `[ \t]`」且「不含 `white-space: pre*`」，就会触发**
 *   —— 触发的是第二十五~二十八轮一直在量的那件事：ProseMirror 在**第一次解析**时把段首的
 *   半角空格 / 制表符并掉（`&nbsp;` / U+3000 / 零宽不受影响），而 `white-space: pre*` 会让它
 *   自己走 `localPreserveWS` 分支、**原样保留**，所以那种载荷反而不触发。
 *
 * 为什么这个判据值得单独写成一支：第二十八轮的粘贴探针（U13）用的是**合成网页 + 真 Ctrl+C**，
 * 只能回答「我造的那 7 种写法里哪种会触发」；要回答「**任意一份**剪贴板 HTML 会不会触发」，
 * 需要一条**可以对输入直接跑**的判定，而不是再看一张表。
 *
 * 口径与实现对齐（**不另立一套**）：
 *   - 「段首空白」用 `round27_leading_ws_scan.mjs` 的 `leadingRuns()`
 *     ——那份口径与 `preserveLeadingWhitespace()` 逐字同构（含 `pre/code/textarea/script/style/svg`
 *     整棵子树跳过、纯空白文本节点不碰），第二十七、二十八两轮都用它，是本仓库的唯一一把尺子；
 *   - `white-space: pre*` 这一条来自 `prosemirror-model` 的解析分支（第二十八轮 B2 查到源码级证据）：
 *     `if (dom.tagName == "PRE" || /pre/.test(dom.style && dom.style.whiteSpace)) → localPreserveWS = true`。
 *
 * ⚠️ **一个必须说清楚的边界**：`leadingRuns()` 只认「元素」下的段首空白。真实复制里，
 * 选区内容常常**不带外层块标签**（`selectNodeContents(p)` 复制出来就是一串裸文本），
 * 这时按元素口径会数成 0 处。本支另加「**根级文本口径**」把它补上，并在输出里分别标注来源
 * （`元素` / `根级文本`）——第二十八轮那 7 种写法里 `plain` 落在这一格，正是口径差异造成的。
 *
 * ⚠️ **第二十九轮实测（这条判据在真实来源上几乎打不响，原因不在判据）**：
 * 用 `browser/r29-clipboard-sampler.mjs` 采了 **19 份真剪贴板 HTML**（8 个公开网页
 * example.com / MDN ×2 / 维基 zh·en / nodejs.org / iana.org / docs.python.org，
 * 11 个同浏览器的合成对照），**判「触发」的 0 份**；另外单独采了应用自己的
 * `/articles/43`，也是 0 份。根因**不是编辑器**，而是 **Blink 在写剪贴板之前就把
 * `white-space: normal` 内容的段首 `[ \t]` 并掉了**：同一个对照页里
 * 「`<p>  LEAD-SP-NORMAL</p>`」的选区文本是 `"LEAD-SP-NORMAL"`（空格已没了），
 * 而「`<p style="white-space:pre-wrap">  LEAD-SP-PREWRAP</p>`」的选区文本是
 * `"  LEAD-SP-PREWRAP"`（保住了）、剪贴板 HTML 里也是 1 处 + 带 `white-space: pre-wrap`
 * ⇒ **带得了段首 `[ \t]` 的载荷，必然同时带 `white-space: pre*`，于是按本判据都不触发**。
 *
 * 所以本判据回答的是「**给我一份剪贴板 HTML，它进来会不会丢段首空白**」，
 * 而「**从浏览器页面复制**」这条路本身已经被浏览器自己堵住了；还会走到这个判据上的，
 * 是那些**自己写 `text/html` 的应用**（编辑器 / 笔记软件 / 富文本工具直接 `setData`），
 * 它们不经 Blink 序列化，完全可能交出「段首 `[ \t]` 且无 `white-space: pre*`」的载荷。
 * 判据本身的**正例**用 `target/probe/r29/clip/z-判据正例_*.html` 钉住：把上面那份真实载荷里
 * 被浏览器并掉的两个空格**加回去**，其余一字不动 → 判据翻成「触发」。
 *
 * 用法：
 *     node tools/render-verify/round29_clipboard_html_check.mjs <文件.html> [...]
 *     node tools/render-verify/round29_clipboard_html_check.mjs --dir <目录>
 *     node tools/render-verify/round29_clipboard_html_check.mjs --stdin < 某份剪贴板原文
 *     （加 `--json` 输出机器可读结果；退出码：有触发 10 / 全部不触发 0 / 用法错 2）
 * 产物：无（只打印）。目录模式会给每一份一行结论，便于对一批采样做汇总。
 */
import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs'
import { join } from 'node:path'
import { pathToFileURL } from 'node:url'
import { leadingRuns } from './round27_leading_ws_scan.mjs'

/** 剪贴板 HTML 里只有 `<!--StartFragment-->…<!--EndFragment-->` 之间才是这次选区。 */
function fragmentOf(raw) {
  const hit = /<!--StartFragment-->([\s\S]*?)<!--EndFragment-->/.exec(raw)
  return hit ? hit[1] : raw
}

/**
 * 根级文本口径：**没有被任何元素包着**的运行文本里，有没有段首 `[ \t]`。
 * 复制选区内容（`selectNodeContents`）时最常见的形态就是这个——外面没有块标签。
 *
 * 注意不能只看「第一个标签之前」：真实剪贴板 HTML 往往以 `<meta charset='utf-8'>` 开头，
 * 裸文本出现在它**之后**（`<meta>` 是空元素，栈仍是空的）。
 */
function rootLevelRuns(fragment) {
  const HEAD = /<([a-zA-Z][a-zA-Z0-9-]*)((?:"[^"]*"|'[^']*'|[^>"'])*)>/g
  const VOID = new Set(['meta', 'link', 'br', 'img', 'hr', 'input', 'col', 'source', 'base', 'area', 'wbr'])
  const runs = []
  let depth = 0
  let index = 0
  const text = (raw) => {
    if (depth !== 0) return
    const run = /^[ \t]+/.exec(raw)
    if (!run) return
    // 纯空白（后面没有可见字符）不算——与实现里「纯空白文本节点不碰」一致。
    if (!/\S/.test(raw.slice(run[0].length))) return
    runs.push({ tag: '(根级文本)', whitespace: run[0] })
  }
  HEAD.lastIndex = 0
  let hit
  while ((hit = HEAD.exec(fragment)) !== null) {
    if (hit.index > index) text(fragment.slice(index, hit.index))
    const tag = hit[1].toLowerCase()
    const closing = fragment[hit.index + 1] === '/'
    const selfClosing = /\/>$/.test(hit[0])
    if (closing) depth = Math.max(0, depth - 1)
    else if (!selfClosing && !VOID.has(tag)) depth += 1
    index = hit.index + hit[0].length
  }
  if (index < fragment.length) text(fragment.slice(index))
  return runs
}

/**
 * 判一份剪贴板 HTML。
 *
 * 返回：`触发`（粗判据：有段首 `[ \t]` 且整份不含 `white-space: pre*`）、
 * `逐处`（细判据：每一处段首空白所在元素链自己有没有 pre 形态——有的话那一处不丢）。
 */
export function checkClipboardHtml(raw, { 名称 = '(stdin)' } = {}) {
  const fragment = fragmentOf(raw)
  // ⚠️ 空元素（`<meta>` / `<br>` / `<img>`…）不可能「包着」文本，但共享尺子 `leadingRuns()`
  // 不认空元素表，会把紧跟其后的文本记到它名下（第二十七轮的扫描里 `components/in-icon.html`
  // 就出现过 `<img>" "`）。这种归属在本判据里是伪的，而且会与「根级文本口径」重复计数，
  // 所以这里**只对本支**把空元素名下的那几处剔掉——共享尺子本身一个字不改。
  const VOID_TAGS = new Set(['meta', 'link', 'br', 'img', 'hr', 'input', 'col', 'source', 'base', 'area', 'wbr'])
  const 元素处 = leadingRuns(fragment).filter((run) => !VOID_TAGS.has(run.tag)).map((run) => ({ ...run, 来源: '元素' }))
  const 根级处 = rootLevelRuns(fragment).map((run) => ({ ...run, 来源: '根级文本' }))
  const 段首空白处 = [...元素处, ...根级处]

  // `white-space: pre*` 的两处来源：内联 style，以及 `<style>` 块里的选择器（少数网页会把方案写在那里）。
  const inlinePre = /white-space\s*:\s*pre/i.test(fragment)
  const preTags = (fragment.match(/<pre[\s>]/gi) || []).length
  const 有Pre形态 = inlinePre || preTags > 0

  const 触发 = 段首空白处.length > 0 && !有Pre形态
  // 细判据：`leadingRuns()` 已经把 `pre/code/svg…` 子树整棵跳过，所以「元素」这一路的每一处
  // 都**不在** pre 子树里；剩下能把那一处保住的是同一个元素上的内联 `white-space: pre*`。
  const 逐处 = 段首空白处.map((run) => ({
    ...run,
    同元素声明了pre: run.来源 === '元素' && new RegExp(
      '<' + run.tag + '\\b[^>]*style="[^"]*white-space\\s*:\\s*pre', 'i').test(fragment),
  })).map((run) => ({ ...run, 会被并掉: !run.同元素声明了pre }))

  return {
    名称,
    片段长度: fragment.length,
    剪贴板里带制表符: /\t/.test(fragment),
    段首空白处,
    段首空白处数: 段首空白处.length,
    其中元素处: 元素处.length,
    其中根级文本处: 根级处.length,
    有Pre形态,
    内联pre声明: inlinePre,
    pre标签数: preTags,
    触发,
    逐处,
    证据片段: fragment.slice(0, 200),
  }
}

/** 采样与自检共用的一行结论。 */
export function summaryLine(row) {
  return (row.触发 ? '触发' : '不触发').padEnd(4)
    + '  段首空白 ' + String(row.段首空白处数).padStart(2) + ' 处'
    + '（元素 ' + row.其中元素处 + ' / 根级 ' + row.其中根级文本处 + '）'
    + '  含制表符 ' + (row.剪贴板里带制表符 ? '是' : '否')
    + '  white-space:pre* ' + (row.有Pre形态 ? '有（内联 ' + row.内联pre声明 + ' / <pre> ' + row.pre标签数 + '）' : '无')
    + '  ← ' + row.名称
}

// ---------- 自检：第二十八轮存档的 7 份真剪贴板载荷，结论必须与那一轮逐条一致 ----------
// 第二十八轮（U13）用**合成网页 + 真 Ctrl+C** 量过 7 种写法，落盘在
// `target/probe/browser/r28_paste_probe*.json` 的 `forms[]`（只有 prewrap 那一份剪贴板里带 `[ \t]`，
// 且它**同时**带 `white-space: pre-wrap`）。自检就是把本判据跑在那些**真载荷**上，看结论对不对得上。
function selfTest() {
  const dir = join(process.cwd(), 'target', 'probe', 'browser')
  if (!existsSync(dir)) return { 可用: false, 原因: '没有 target/probe/browser（先跑第二十八轮的粘贴探针）' }
  const file = readdirSync(dir).filter((name) => /^r28_paste.*\.json$/.test(name)).sort().pop()
  if (!file) return { 可用: false, 原因: '没有 r28_paste*.json 产物' }
  const payload = JSON.parse(readFileSync(join(dir, file), 'utf8'))
  const forms = payload?.['暴露面']?.['口径一_合成网页']?.['逐种'] || []
  const usable = forms.filter((row) => row['剪贴板原始片段'])
  if (!usable.length) return { 可用: false, 原因: file + ' 里没有 剪贴板原始片段 字段' }
  const rows = usable.map((form) => ({
    id: form.id,
    那一轮记的处数: form['会被修法改写处数'],
    那一轮记的pre: form['剪贴板有whiteSpacePre'],
    实测: checkClipboardHtml(form['剪贴板原始片段'], { 名称: form.id }),
  }))
  return { 可用: true, 文件: file, rows }
}

const ARGS = process.argv.slice(2)
const isMain = (() => {
  try { return import.meta.url === pathToFileURL(process.argv[1]).href } catch { return false }
})()
if (isMain) {
  const JSON_OUT = ARGS.includes('--json')
  const dirAt = ARGS.indexOf('--dir')
  const dir = dirAt >= 0 ? ARGS[dirAt + 1] : null
  let files = ARGS.filter((arg) => !arg.startsWith('--') && arg !== dir)
  if (dir) {
    files = readdirSync(dir).filter((name) => /\.html?$/i.test(name))
      .map((name) => join(dir, name)).filter((file) => statSync(file).isFile())
  }
  if (ARGS.includes('--selftest')) {
    const result = selfTest()
    console.log(result.可用
      ? '自检（第二十八轮的真剪贴板载荷 ' + result.文件 + '）：'
      : '自检跑不了：' + result.原因)
    if (result.可用) {
      let 对得上 = 0
      for (const row of result.rows) {
        const same = row.实测.段首空白处数 === row.那一轮记的处数
        if (same) 对得上 += 1
        console.log('  ' + summaryLine(row.实测)
          + ' · 该轮记的处数 ' + row.那一轮记的处数 + ' → ' + (same ? '对得上 ✅' : '对不上 ❌'))
      }
      console.log('  ── ' + 对得上 + ' / ' + result.rows.length + ' 份与第二十八轮逐条对得上')
    }
    process.exit(0)
  }
  if (ARGS.includes('--stdin')) {
    const raw = readFileSync(0, 'utf8')
    const row = checkClipboardHtml(raw, { 名称: '(stdin)' })
    console.log(JSON_OUT ? JSON.stringify(row, null, 1) : summaryLine(row))
    process.exit(row.触发 ? 10 : 0)
  }
  if (!files.length) {
    console.error('用法：node tools/render-verify/round29_clipboard_html_check.mjs <文件.html> [...]'
      + ' | --dir <目录> | --stdin  |  --selftest')
    process.exit(2)
  }
  const rows = []
  for (const file of files) {
    const raw = readFileSync(file, 'utf8')
    const row = checkClipboardHtml(raw, { 名称: file })
    rows.push(row)
    if (JSON_OUT) {
      console.log(JSON.stringify(row, null, 1))
    } else {
      console.log(summaryLine(row))
      for (const run of row.逐处) {
        console.log('       ' + run.来源 + ' <' + run.tag + '> 段首 ' + JSON.stringify(run.whitespace)
          + '（' + [...run.whitespace].map((ch) => ch === '\t' ? 'TAB' : 'SPACE').join('+') + '）'
          + ' · 会被并掉：' + (run.会被并掉 ? '是' : '否（同元素声明了 white-space: pre*）'))
      }
    }
  }
  if (!JSON_OUT) {
    const 触发 = rows.filter((row) => row.触发).length
    console.log('\n合计：' + rows.length + ' 份里 **' + 触发 + ' 份触发**（段首 `[ \\t]` 且不含 white-space: pre*）'
      + ' / ' + (rows.length - 触发) + ' 份不触发')
  }
  process.exit(rows.some((row) => row.触发) ? 10 : 0)
}
