/**
 * 第三十五轮 · 粘贴段首空白修法的**反例自检 + 暴露面**（纯离线，不开浏览器、不碰 8081）。
 *
 * 要回答三件事，每条都要**可判红**：
 *
 *   ① 修法在**旧包**（改动前的 `webui/dist`）上判得红吗？
 *      —— 判据若是「打开时缩进在不在」，旧包上必然也在（那是 `setContent` 那条路，第二十六轮修的），
 *      所以本支的判据必须是**粘贴那一路**：同一份载荷、旧包粘出来缩进 0、新包粘出来缩进 > 0。
 *      存档取自 `r27-entry-paths.mjs` 的两次实跑（`--bundle target/probe/r35/before-dist` 与 `--bundle webui/dist`），
 *      同一支脚本、同一份载荷、同一台浏览器，**唯一的自变量是前端包**。
 *
 *   ② 修法会不会**改坏别的粘贴内容**？
 *      —— 拿 `editorExtensions.js` 里**原样切出来的真函数**跑全部产物（`components` / `r16` / `alt` /
 *      `combos` / `registry`）的指纹，报「改写了几处、落在什么标签链上、有没有落在 `pre/code/table/svg/katex` 里」。
 *      口径与第二十八轮 `r28-paste-html-probe.mjs` 的 B2 语料级一节相同，便于跨轮比数。
 *
 *   ③ 修法**改动的是哪一行的行为**？
 *      —— 从 `prosemirror-view/dist/index.js` 逐行读：`transformPastedHTML` 的调用点、
 *      拖动是否同路、`preserveWhitespace` 那一行的取值来源。逐条给 file:line。
 *
 * 用法：node tools/render-verify/browser/r35-paste-fix-audit.mjs
 * 产物：target/probe/r35/paste_fix_audit.json + .txt
 */
import { readFileSync, writeFileSync, existsSync, readdirSync, mkdirSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { resolve, join } from 'node:path'
import { ROOT, OUT } from '../paths.mjs'

const R35 = resolve(OUT, 'r35')
mkdirSync(R35, { recursive: true })

const read = (path) => JSON.parse(readFileSync(path, 'utf8'))
const BEFORE = resolve(OUT, 'browser', 'r27_entry_paths_r35-before.json')
const AFTER = resolve(OUT, 'browser', 'r27_entry_paths_r35-after.json')
for (const path of [BEFORE, AFTER]) {
  if (!existsSync(path)) { console.error('缺输入：' + path + '（先跑 r27-entry-paths.mjs 的 before / after 两次）'); process.exit(2) }
}
const before = read(BEFORE)
const after = read(AFTER)

// ---------------------------------------------------------------------------
// ① 反例自检：同一份载荷，旧包 vs 新包，贴在**粘贴那一路**上
// ---------------------------------------------------------------------------
const indentOf = (row) => row.blocks.map((block) => block.缩进)
const 粘贴 = {
  载荷: before.doc,
  旧包_实时缩进: indentOf(before.results.paste.entered),
  新包_实时缩进: indentOf(after.results.paste.entered),
  旧包_保存出口: before.results.paste.saveExit,
  新包_保存出口: after.results.paste.saveExit,
  旧包_再打开缩进: indentOf(before.results.paste.reopened),
  新包_再打开缩进: indentOf(after.results.paste.reopened),
}
/** 判据：旧包「三个块一个缩进都没有」，新包「前两块有缩进、第三块（本来就是平的）没有」。 */
const 旧包判红 = 粘贴.旧包_实时缩进.every((value) => value === 0)
  && !/<p[^>]*>(?:&nbsp;|[ \t])/.test(粘贴.旧包_保存出口)
const 新包判绿 = 粘贴.新包_实时缩进[0] > 0.5 && 粘贴.新包_实时缩进[1] > 0.5 && 粘贴.新包_实时缩进[2] === 0
  && /^<p[^>]*>&nbsp;/.test(粘贴.新包_保存出口)
/** 反向对照：**另外四条路一个字节都不许变**——修法的射程只在「粘贴 HTML」。 */
const 其余四路 = ['setcontent', 'pastePlain', 'pasteHtmlFixed', 'type']
const 未动 = 其余四路.filter((key) => ['entered', 'saveExit', 'reopened'].every((phase) =>
  JSON.stringify(before.results[key][phase]) === JSON.stringify(after.results[key][phase])))

// ---------------------------------------------------------------------------
// ② 误伤面：真函数施在全部产物上会改写什么
// ---------------------------------------------------------------------------
/** 与 r28 的 B2 同一把尺子：从 `const whitespaceOpaqueTags` 切到 `export const PreservedInlineStyle`。 */
const SRC_FILE = resolve(ROOT, 'webui', 'src', 'editorExtensions.js')
const SOURCE = readFileSync(SRC_FILE, 'utf8')
const BLOCK_START = SOURCE.indexOf('const whitespaceOpaqueTags = new Set(')
const BLOCK_END = SOURCE.indexOf('export const PreservedInlineStyle')
const 切得出 = BLOCK_START >= 0 && BLOCK_END > BLOCK_START
const 源码块 = 切得出
  ? SOURCE.slice(BLOCK_START, BLOCK_END).replace(/^export /gm, '')
  : ''
const 源码块sha = createHash('sha256').update(源码块).digest('hex').slice(0, 16)

/**
 * 纯 Node 无 DOM，不能直接跑真函数。这里做两件事：
 *   · **可判红的指纹**：把「段首 `[ \t]` 出现在 `pre/code/…` 之外」的地方数出来（同一条正则、同一份产物）；
 *   · **把 r28 的实测结论读回来**（浏览器里跑的），两者一起判。
 *
 * ⚠️ 两支切源码块所用的**边界不同**（r28 切到 `export const PreservedInlineStyle`），
 * 所以 sha **不可直接比**。真函数字节是否与第二十八轮测爆炸半径时逐字节相同，
 * 用「切块区间内把本轮新插的那一段整体切掉」自证（见 `真函数未动`）。
 * 第二十八轮记录的切块 sha 是 `bae2e6188257bd6f` / 1590 字节。
 */
const 切块 = SOURCE.slice(BLOCK_START, BLOCK_END)
const 真函数未动 = (() => {
  const at = 切块.indexOf('export const PastedLeadingWhitespace')
  if (at < 0) return { 可比: false, 说明: '切块里找不到本轮新加的扩展' }
  const docStart = 切块.lastIndexOf('/**', at)
  if (docStart < 0) return { 可比: false, 说明: '新加段的注释定位失败' }
  const region1 = 切块.slice(0, docStart).replace(/^export /gm, '')
  const sha = createHash('sha256').update(region1).digest('hex').slice(0, 16)
  // 差值 = 新加段前面多出来的那个空行（`}\r\n\r\n`），逐字可比：1590 + 7 = 1597
  return {
    可比: true, 第二十八轮sha前16位: 'bae2e6188257bd6f', 本轮sha前16位: sha,
    第二十八轮字节: 1590, 本轮字节: region1.length, 差值: region1.length - 1590,
    相同: sha === 'bae2e6188257bd6f',
    说明: 'sha 逐字相同 ⇒ 「真函数一个字节都没动」',
  }
})()
const 跳过标签 = /<(pre|code|textarea|script|style|svg)\b/i
const 产物目录 = ['components', 'r16', 'alt', 'combos', 'registry']
const 语料 = { 文件数: 0, 有段首空白的文件: [], 段首空白处数: 0, 浏览器侧实跑: null, 备注: '纯 Node 无 DOM，本支只给指纹；真函数实跑见 r28 B2 与 r35 浏览器侧' }
for (const dir of 产物目录) {
  const at = resolve(OUT, dir)
  if (!existsSync(at)) continue
  for (const name of readdirSync(at)) {
    if (!name.toLowerCase().endsWith('.html')) continue
    const html = readFileSync(join(at, name), 'utf8')
    语料.文件数 += 1
    // 逐段找「>」之后紧跟的空白（段首），跳过 pre/code/… 子树内的那些
    let hits = 0
    const 标签栈 = []
    const re = /<\/?([a-zA-Z][\w-]*)\b[^>]*>|[^<]+/g
    let match
    while ((match = re.exec(html))) {
      const token = match[0]
      if (token[0] === '<') {
        const closing = token[1] === '/'
        const tag = match[1].toUpperCase()
        if (closing) { const at2 = 标签栈.lastIndexOf(tag); if (at2 >= 0) 标签栈.splice(at2) } else if (!/\/>$/.test(token)) 标签栈.push(tag)
      } else if (标签栈.length && !标签栈.some((tag) => 跳过标签.test('<' + tag + ' ')) && /^[ \t]/.test(token)) {
        hits += 1
      }
    }
    if (hits) { 语料.有段首空白的文件.push({ file: dir + '/' + name, 处数: hits }); 语料.段首空白处数 += hits }
  }
}
const r28 = existsSync(resolve(OUT, 'browser', 'r28_paste_html_probe.json'))
  ? read(resolve(OUT, 'browser', 'r28_paste_html_probe.json')).代价.语料级 : null
语料.浏览器侧实跑 = r28 && {
  源码块: 'bae2e6188257bd6f（第二十八轮）',
  文件数: r28.文件数, 被改写文件数: r28.被改写文件.length, 命中总数: r28.命中总数,
  落在table_pre_code_katex_svg: r28.落在table_pre_code_katex_svg.length,
  镜像自检全部一致: r28.镜像自检全部一致,
}

// ---------------------------------------------------------------------------
// ③ 源码级：改动到底落在哪一行
// ---------------------------------------------------------------------------
const viewPath = resolve(ROOT, 'webui', 'node_modules', 'prosemirror-view', 'dist', 'index.js')
const viewLines = existsSync(viewPath) ? readFileSync(viewPath, 'utf8').split('\n') : []
const findLine = (needle) => viewLines.findIndex((line) => line.includes(needle)) + 1
const 源码级 = {
  文件: 'webui/node_modules/prosemirror-view/dist/index.js',
  调用点_粘贴: findLine('view.someProp("transformPastedHTML"'),
  拖动同路: findLine('slice = parseFromClipboard(view, getText(event.dataTransfer)'),
  解析时空白开关: findLine('preserveWhitespace: !!(asText || sliceData)'),
  变换结果进解析: findLine('dom = readHTML(html)'),
}
const 源码级齐全 = ['调用点_粘贴', '拖动同路', '解析时空白开关', '变换结果进解析']
  .every((key) => typeof 源码级[key] === 'number' && 源码级[key] > 0)

// ---------------------------------------------------------------------------
// 组装
// ---------------------------------------------------------------------------
const 修法挂载 = (() => {
  const vue = readFileSync(resolve(ROOT, 'webui', 'src', 'views', 'ArticleEditorView.vue'), 'utf8')
  return {
    扩展已定义: /export const PastedLeadingWhitespace\s*=\s*Extension\.create/.test(SOURCE),
    变换已接到粘贴路: /transformPastedHTML\s*\(html\)\s*\{\s*return preserveLeadingWhitespace\(html\)/.test(SOURCE),
    已注册进编辑器: /PastedLeadingWhitespace,/.test(vue),
  }
})()

const 反例自检通过 = 旧包判红 && 新包判绿 && 未动.length === 其余四路.length
const 结论 = {
  轮次: '第三十五轮',
  求: '粘贴 HTML 时段首空白丢失要修',
  反例自检: { ...粘贴, 旧包判红, 新包判绿, 未动的路: 未动, 通过: 反例自检通过 },
  暴露面: { ...语料, 源码块sha, 真函数未动, 修法挂载 },
  源码级: { ...源码级, 齐全: 源码级齐全 },
  通过: 反例自检通过 && 源码级齐全 && 真函数未动.相同
    && Object.values(修法挂载).every(Boolean),
}
const lines = ['', '第三十五轮 · 粘贴段首空白修法审计', '-'.repeat(92),
  '① 反例自检（同一份载荷，唯一自变量是前端包）',
  '  载荷: ' + JSON.stringify(粘贴.载荷),
  '  旧包 实时缩进: ' + JSON.stringify(粘贴.旧包_实时缩进),
  '       保存出口: ' + 粘贴.旧包_保存出口,
  '  新包 实时缩进: ' + JSON.stringify(粘贴.新包_实时缩进),
  '       保存出口: ' + 粘贴.新包_保存出口,
  '       再打开缩进: ' + JSON.stringify(粘贴.新包_再打开缩进),
  '  旧包判红(必须): ' + 旧包判红 + '   新包判绿(必须): ' + 新包判绿,
  '  另外四条路逐字节未变: ' + JSON.stringify(未动) + '  应含 ' + JSON.stringify(其余四路),
  '',
  '② 暴露面与「只加了新段」的自证',
  '  产物 ' + 语料.文件数 + ' 个 · 段首 [ \\t] 在敏感子树之外的 ' + 语料.有段首空白的文件.length
    + ' 个文件 / ' + 语料.段首空白处数 + ' 处',
  '  浏览器侧实跑（第二十八轮，同一把尺子）: ' + JSON.stringify(语料.浏览器侧实跑),
  '  真函数未动: ' + JSON.stringify(真函数未动),
  '  修法挂载: ' + JSON.stringify(修法挂载),
  '',
  '③ 源码级（' + 源码级.文件 + '）',
  '  调用点 _粘贴: :' + 源码级.调用点_粘贴 + '  `view.someProp("transformPastedHTML", f => { html = f(html, view); })`',
  '  拖动同路: :' + 源码级.拖动同路 + '  `slice = parseFromClipboard(view, getText(event.dataTransfer), …)`',
  '  解析空白开关: :' + 源码级.解析时空白开关 + '  `preserveWhitespace: !!(asText || sliceData)` ← 粘贴这条 HTML 路此前在这里被并掉',
  '  变换结果进解析: :' + 源码级.变换结果进解析 + '  `dom = readHTML(html)`',
  '',
  '总判：' + (结论.通过 ? '通过 ✅' : '未通过 ❌'),
  '']
console.log(lines.join('\n'))
writeFileSync(resolve(R35, 'paste_fix_audit.json'), JSON.stringify(结论, null, 1), 'utf8')
writeFileSync(resolve(R35, 'paste_fix_audit.txt'), lines.join('\n'), 'utf8')
console.log('产物：' + resolve(R35, 'paste_fix_audit.json'))
process.exitCode = 结论.通过 ? 0 : 1
