/**
 * 第三十四轮 · 表格修法的**反作用**自查：手写表格（编辑器工具栏「插入 3×3 表格」）有没有被误伤。
 *
 * 修法第二版给表格贴一个标记类，判据是**产物指纹** `border-collapse: collapse`
 * （`editorExtensions.js` 的 `PRODUCT_TABLE_STYLE`）。第一版判据是「`preservedStyle` 非空」，
 * 而 `preservedStyle` 的 parseHTML 就是 `element.getAttribute('style')`——**只要 `<table>` 上有
 * style 就算**。本支就是用来证伪第一版的那支，现在留下来当**回归闸**。
 *
 * 要回答的、可被证伪的问题：**工字栏插出来的手写表格，重开后会不会被当成产物表？**
 *   - 不会 ⇒ 手写表格走兜底，行为与改前逐字节相同；
 *   - 会 ⇒ 后果是**用户可感知的**：`.ProseMirror table.mf-preserved th,td{border:0}`
 *     会把网格线画没（第一版实测逐格边框 `1px 1px 1px 1px` → `0px 0px 0px 0px`）。
 *
 * 量法用**真实应用**（8081）、**真编辑器**，不猜：工具栏同一句
 * `editor.chain().focus().insertTable({rows:3,cols:3,withHeaderRow:true}).run()` 之后的
 * `getHTML()`，再把这份 HTML 灌回去（= 用户重开这篇文章），看实时 DOM。
 * `PUT` 在页面内被拦下——**全程不写生产数据**。
 *
 * 为了不让「没被打类」被一个空跑蒙过去，第 ④ 步是**同一份出口的正向对照**：只往
 * `<table style>` 里加一个 `border-collapse:collapse`，其余逐字节不动——类**必须**出现。
 * 一负一正同源同跑，说明闸门是那个指纹在管，不是脚本自己走空了。
 *
 * 用法：node tools/render-verify/browser/r34-table-handmade-regression.mjs
 * 产物：target/probe/r34/table_handmade_regression.json / .txt
 */
import { writeFileSync, existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { launchBrowser, openPage } from './cdp.mjs'
import { OUT, API, login } from '../paths.mjs'

const R34 = resolve(OUT, 'r34')
const PORT = 9374
const ARTICLE_ID = 38
const sleep = (ms) => new Promise((done) => setTimeout(done, ms))
if (!login.success) throw new Error('登录失败：' + JSON.stringify(login))
const token = await login()
const readArticle = async () => (await (await fetch(`${API}/api/articles/${ARTICLE_ID}`,
  { headers: { Authorization: `Bearer ${token}` } })).json()).data
const base = await readArticle()
const dbBefore = { revision: base.revision, updatedAt: base.updatedAt }

/** 拦下一切写请求，并把 `GET /api/articles/38` 换成我们准备的正文。 */
const GUARD = `(() => {
  window.__writeGuard = { blocked: [], lastSave: null };
  const mutating = (method, url) => {
    const verb = String(method || 'GET').toUpperCase();
    return verb !== 'GET' && verb !== 'HEAD' && /\\/api\\//.test(String(url || ''));
  };
  const json = (data) => new Response(JSON.stringify({ success: true, data }),
    { status: 200, headers: { 'Content-Type': 'application/json' } });
  const rawFetch = window.fetch;
  const ARTICLE_PATH = /^\\/api\\/articles\\/${ARTICLE_ID}(\\?|$)/;
  window.fetch = function (input, init) {
    let url = '', verb = 'GET';
    try {
      url = typeof input === 'string' ? input : (input && input.url) || '';
      verb = String((init && init.method) || (input && input.method) || 'GET').toUpperCase();
    } catch (error) { /* 放行 */ }
    if (mutating(verb, url)) {
      let body = {};
      try { body = init && init.body ? JSON.parse(init.body) : {}; } catch (error) { body = {}; }
      window.__writeGuard.lastSave = body.contentHtml || null;
      window.__writeGuard.blocked.push(verb + ' ' + url);
      return Promise.resolve(json(Object.assign({}, body, { updatedAt: new Date().toISOString() })));
    }
    const injected = sessionStorage.getItem('__r34_case');
    if (injected && ARTICLE_PATH.test(String(url))) {
      const stored = JSON.parse(sessionStorage.getItem('__r34_base') || '{}');
      return Promise.resolve(json(Object.assign({}, stored, { contentHtml: injected })));
    }
    return rawFetch.apply(this, arguments);
  };
  return 'installed';
})()`

/** 页面内：量一张表的关键项。 */
const MEASURE = `(sel) => {
  const table = document.querySelector(sel + ' table');
  if (!table) return { error: 'no-table' };
  const cell = table.querySelector('tbody td') || table.querySelector('th');
  const s = cell ? getComputedStyle(cell) : null;
  return {
    tableStyleAttr: table.getAttribute('style'),
    tableClassName: table.className,
    tableLayout: getComputedStyle(table).tableLayout,
    cellBorders: s ? [s.borderTopWidth, s.borderRightWidth, s.borderBottomWidth, s.borderLeftWidth].join(' ') : null,
    cellPadding: s ? s.padding : null,
    cellLineHeight: s ? s.lineHeight : null,
    tableHeight: Math.round(table.getBoundingClientRect().height * 100) / 100,
    cells: table.querySelectorAll('thead th, tbody td').length,
  };
}`

const { client, version, close } = await launchBrowser({ port: PORT })
console.log('浏览器:', version.Browser, '· 目标:', API)
const page = await openPage(client)
await page.send('Runtime.enable')
await page.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
await page.send('Page.addScriptToEvaluateOnNewDocument', { source: GUARD })
client.listeners.set('Fetch.requestPaused', [(message) => {
  const { requestId, request } = message.params
  const verb = String(request.method || 'GET').toUpperCase()
  if (verb !== 'GET' && verb !== 'HEAD') {
    client.send('Fetch.failRequest', { requestId, errorReason: 'Aborted' }, page.sessionId).catch(() => {})
    return
  }
  client.send('Fetch.continueRequest', { requestId }, page.sessionId).catch(() => {})
}])
await page.send('Fetch.enable', { patterns: [{ urlPattern: '*://*/*', requestStage: 'Request' }] })

await page.navigate(API + '/login')
await page.evaluate(`localStorage.setItem('wechat_bot_token', ${JSON.stringify(token)}); true`)
await page.evaluate(`sessionStorage.setItem('__r34_base', ${JSON.stringify(JSON.stringify(base))}); true`)

/** 打开一篇正文只有一段的文章（内容不重要，我们要的是编辑器实例）。 */
const openEditor = async (html) => {
  await page.evaluate(`sessionStorage.setItem('__r34_case', ${JSON.stringify(html)}); true`)
  await page.navigate(`${API}/articles/${ARTICLE_ID}`)
  for (let attempt = 0; attempt < 80; attempt += 1) {
    if (await page.evaluate(`!!document.querySelector('.ProseMirror')`) === true) break
    await sleep(250)
  }
  await page.evaluate(`document.fonts.ready`)
  await sleep(400)
}

// ---- ① 用工具栏同一句插入一张手写表格，量「插入后」的实时 DOM + 保存出口 ----
await openEditor('<p>X34HAND</p>')
// 编辑器实例没挂到 `window` 上，所以点工具栏那颗按钮（`ArticleEditorView.vue:271 insertTable()` 的入口）。
const clicked = await page.evaluate(`(() => {
  const button = [...document.querySelectorAll('.editor-toolbar button')]
    .find((el) => (el.getAttribute('title') || '').indexOf('表格') >= 0);
  if (!button) return 'no-button';
  button.click();
  return 'clicked';
})()`)
if (clicked !== 'clicked') throw new Error('点不到「插入表格」按钮：' + clicked)
await sleep(500)

const afterInsert = JSON.parse(await page.evaluate(`JSON.stringify((${MEASURE})('.ProseMirror'))`))
/** 保存出口：逼出一次保存（末段末尾敲一个字符再退格，净零编辑）。 */
const triggerSave = async () => {
  await page.evaluate(`window.__writeGuard.lastSave = null`)
  await page.evaluate(`(() => {
    const dom = document.querySelector('.ProseMirror');
    const blocks = [...dom.querySelectorAll('p')].filter((el) => (el.textContent || '').trim().length > 0);
    const last = blocks[blocks.length - 1] || dom.lastElementChild;
    if (!last) return 'no-block';
    dom.focus();
    const range = document.createRange(); range.selectNodeContents(last); range.collapse(false);
    const selection = window.getSelection(); selection.removeAllRanges(); selection.addRange(range);
    return 'ok';
  })()`)
  await page.send('Input.insertText', { text: 'x' })
  await sleep(120)
  for (const type of ['keyDown', 'keyUp']) {
    await page.send('Input.dispatchKeyEvent', {
      type, key: 'Backspace', code: 'Backspace', windowsVirtualKeyCode: 8, nativeVirtualKeyCode: 8,
    })
  }
  await sleep(2600)
  return JSON.parse(await page.evaluate(`JSON.stringify(window.__writeGuard.lastSave || null)`) || 'null')
}
const exitHtml = await triggerSave()

// ---- ② 把保存出口原样灌回去（= 用户重开这篇文章），量「重开后」 ----
let afterReload = null
if (exitHtml) {
  await openEditor(exitHtml)
  afterReload = JSON.parse(await page.evaluate(`JSON.stringify((${MEASURE})('.ProseMirror'))`))
}

// ---- ③ 正向对照：同一份出口 + 一个产物指纹，类**必须**出现 ----
/**
 * 没有这一条，「没被打类」可能是脚本走空了而不是闸门管住了。
 * 只往出口那句 `<table style="min-width: 75px;">` 里插一个 `border-collapse:collapse`，
 * 其余一个字节都不动——判据（指纹）若真在管，类就必须出现，网格线也就必须消失。
 */
let positiveControl = null
if (exitHtml && /<table[^>]*style="([^"]*)"/i.test(exitHtml)) {
  const seeded = exitHtml.replace(/(<table[^>]*style=")([^"]*)(")/i, '$1border-collapse:collapse;$2$3')
  await openEditor(seeded)
  positiveControl = JSON.parse(await page.evaluate(`JSON.stringify((${MEASURE})('.ProseMirror'))`))
}

const dbAfter = await readArticle()
const unchanged = dbAfter.revision === dbBefore.revision && dbAfter.updatedAt === dbBefore.updatedAt

const tableTag = exitHtml ? (exitHtml.match(/<table[^>]*>/i) || ['(出口里没有 <table>)'])[0] : '(没拿到出口)'
const clean = Boolean(afterReload) && !afterReload.tableClassName.includes('mf-preserved')
const seeded = Boolean(positiveControl) && positiveControl.tableClassName.includes('mf-preserved')
const verdict = !afterReload ? '拿不到保存出口，判不了'
  : !clean ? '喂了类名（手写表格被当成产物表——指纹没管住，网格线被画没）'
    : !positiveControl ? '没喂类名，但正向对照没跑起来（判据是否在管无法确认）'
      : !seeded ? '没喂类名，但正向对照**也没喂**——闸门失效（判据不生效，脚本可能在空跑）'
        : '没喂类名，且正向对照喂上了（手写表格走兜底，与改前一致）'

const lines = [
  '# 第三十四轮 · 手写表格有没有被修法误伤（编辑器工具栏「插入 3×3 表格」）', '',
  '- 浏览器：' + version.Browser + ' · 目标：' + API + ' · 文章 #' + ARTICLE_ID,
  '- 插入方式：工具栏按钮 `title="插入 3×3 表格"`（与 `ArticleEditorView.vue:271 insertTable()` 同一句命令）',
  '- 判据（与产品代码 `PRODUCT_TABLE_STYLE` 同构）：`<table style>` 里有没有 `border-collapse: collapse`',
  '',
  '## ① 插入之后（还没保存）',
  '- 实时 DOM `<table>` 的 style 属性：`' + afterInsert.tableStyleAttr + '`',
  '- 实时 DOM `table.className`：`' + afterInsert.tableClassName + '`',
  '- `table-layout`：' + afterInsert.tableLayout + ' · 逐格边框：' + afterInsert.cellBorders,
  '- 逐格 padding：' + afterInsert.cellPadding + ' · line-height：' + afterInsert.cellLineHeight,
  '- 表高：' + afterInsert.tableHeight + ' · 格数：' + afterInsert.cells,
  '',
  '## ② 保存出口里的 `<table>` 标签原文',
  '',
  '    ' + tableTag,
  '',
  '## ③ 把出口原样灌回去（用户重开这篇文章）',
  afterReload
    ? ['- `table.className`：`' + afterReload.tableClassName + '`',
       '- 解析出的 style 属性：`' + afterReload.tableStyleAttr + '`',
       '- `table-layout`：' + afterReload.tableLayout + ' · 逐格边框：' + afterReload.cellBorders,
       '- 逐格 padding：' + afterReload.cellPadding + ' · line-height：' + afterReload.cellLineHeight,
       '- 表高：' + afterReload.tableHeight + ' · 格数：' + afterReload.cells].join('\n')
    : '- （没拿到保存出口，这一步没跑）',
  '',
  '## ④ 正向对照（同一份出口，只多一个 `border-collapse:collapse`）',
  positiveControl
    ? ['- `table.className`：`' + positiveControl.tableClassName + '`',
       '- `table-layout`：' + positiveControl.tableLayout + ' · 逐格边框：' + positiveControl.cellBorders,
       '- 表高：' + positiveControl.tableHeight].join('\n')
    : '- （出口里没有 `<table style>`，这一条没跑）',
  '',
  '## 判',
  '- **' + verdict + '**',
  '- 两条判据都是**可证伪**的，且**一负一正同源**：③ 说「手写表格拿不到类」，④ 说「同一个指纹一旦出现，类就出现」。',
  '  只有 ③ 没有 ④ 时，「没打类」也可能只是脚本走空了；④ 是防这个的。',
  '  逐格边框给出**可见代价**：类一旦落到手写表格上，`border:0` 会把网格线画没（`1px 1px 1px 1px` → `0px 0px 0px 0px`）。',
  '',
  '库核对：改前 revision=' + dbBefore.revision + ' / 改后 revision=' + dbAfter.revision
    + ' · updatedAt ' + (unchanged ? '逐字未变 ✅' : '变了 ❌'),
]
console.log(lines.join('\n'))
writeFileSync(resolve(R34, 'table_handmade_regression.json'),
  JSON.stringify({ afterInsert, exitHtml, afterReload, positiveControl, tableTag, verdict,
    db: { before: dbBefore, after: dbAfter } }, null, 1), 'utf8')
writeFileSync(resolve(R34, 'table_handmade_regression.txt'), lines.join('\n') + '\n', 'utf8')

await page.close(); close()
/** 闸门要的是**两条都成立**：手写表格没拿到类，且同一个指纹出现时类必到。 */
process.exitCode = clean && seeded ? 0 : 1
