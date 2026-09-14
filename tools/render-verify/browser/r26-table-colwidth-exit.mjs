/**
 * 第二十六轮 B · 窄列表格的**列宽**在保存出口里还在不在（可重跑的回归断言，不是一次性对照实验）。
 *
 * 背景：第六轮修的是「编辑器读不懂 `data-colwidth`」，第二十五轮的同题对照又证明
 * **旧编辑器确实会在保存那一刻把列宽丢掉**——
 *   旧编辑器出口：`<colgroup><col style="min-width: 25px;"><col style="min-width: 25px;"></colgroup>`
 *   现编辑器出口：`<colgroup><col style="min-width: 25px;"><col style="width: 90px;"></colgroup>`
 * 一次对照实验只能证明「当时是对的」，证明不了「以后改回去会被抓住」。本支就是那道闸。
 *
 * 判据（**先立后测**，不因测量结果调整）：
 *   ① 出口里**每一列的宽度声明**必须与入口里那一列的宽度声明一致
 *      （入口 `<col style="width:90px">` / `<col width="90">` / `<td data-colwidth="90">` 三种写法都算声明；
 *        入口没声明的列，出口也不该凭空多出一条宽度）；
 *   ② 出口再灌回去、再存一次，列宽声明必须**逐字不变**（不能第一次存住、第二次又塌）。
 *   两条都过才算 pass。任一条不过，打印是哪一列、差在哪，**退出码非 0**。
 *   判据只压在**渲染服务产物**那一条样本上——第二十五轮那个 bug 的现场就是它，
 *   用户看得见的路径也只有它。手写/粘贴来的 HTML 另算（见下）。
 *
 * 顺带答一个此前没人查过的问题：`data-colwidth` 只是**入口**写法，
 * **出口保不保留**它——本支把出口侧的出现次数一并量出来（不改判定）。
 *
 * 三个样本：
 *   甲 = 渲染服务给 `:::title type="DA01"` 的产物（`target/probe/r16/r16-06-title-da01.html`），
 *        窄列同时带 `data-colwidth="90"` 与 `<col style="width:90px">`——用户实际会遇到的形态。**判据样本**；
 *   乙 = 手写表格，窄列**只有** `<col style="width:90px">`（没有任何宽度属性）。
 *        **记录项，不参与退出码**：它考的不是第二十五轮那个 bug，而是一个本轮新量到的**入口缺口**——
 *        编辑器入口只认 `data-colwidth` / `colwidth` / `<col width>` 三种**属性**写法，
 *        `<col style="width:Npx">` 它读不到（见 §3.26 B 的实测与暴露面统计：
 *        249 个渲染产物里只有 2 个带列宽，且都带属性写法，产物侧暴露为 0）；
 *   丙 = 第廿五轮从**用户那篇文章**里量到的塌陷形态（两列都是 `min-width: 25px`）——
 *        入口就没有宽度声明，按判据①「一致」即通过，只作为**记录项**，用来钉住
 *        「存量数据不会自愈」这件事（要修的是数据，不是代码）。
 *
 * 另有**闸的自检**：拿第廿五轮存档的**旧编辑器出口**（`r25_setcontent_r16-06-title-da01_before_saveexit.html`，
 * 两列都被拍成 `min-width: 25px`）喂给本支同一套解析规则，必须被判 **FAIL**——
 * 否则说明这条闸已经被写松到「怎么塌都算过」。存档在就判，不在就跳过（不影响退出码）。
 *
 * 全程**不写生产数据**：`PUT /api/articles/38` 在页面内被拦下（拦到的 body 就是「保存出口」），
 * CDP 侧再把非 GET 请求一律 `Fetch.failRequest`，最后复查库里的 `revision` / `updatedAt`。
 *
 * 用法：
 *     node tools/render-verify/browser/r26-table-colwidth-exit.mjs
 *     node tools/render-verify/browser/r26-table-colwidth-exit.mjs --bundle target/probe/r26/before-dist   # 反向验闸
 * 产物：target/probe/browser/r26_colwidth_exit.json / r26_colwidth_exit_<id>_saveexit.html
 */
import { readFileSync, writeFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { launchBrowser, openPage } from './cdp.mjs'
import { BROWSER_OUT, OUT, ROOT, API } from '../paths.mjs'
import { widthsOf } from './colwidth-rules.mjs'

const ARGS = process.argv.slice(2)
const argOf = (name, fallback = null) => {
  const at = ARGS.indexOf(name)
  return at >= 0 && ARGS[at + 1] ? ARGS[at + 1] : fallback
}
const BUNDLE = argOf('--bundle') ? resolve(ROOT, argOf('--bundle')) : null
const PORT = Number(argOf('--port', '9363'))
const ARTICLE_ID = 38
if (BUNDLE && !existsSync(resolve(BUNDLE, 'index.html'))) { console.error('--bundle 目录里没有 index.html'); process.exit(3) }
const sleep = (ms) => new Promise((done) => setTimeout(done, ms))

const DA01 = resolve(OUT, 'r16', 'r16-06-title-da01.html')
if (!existsSync(DA01)) throw new Error('缺样本甲：' + DA01)

/** 乙（手写，只有 `<col style>`）、丙（用户那篇的塌陷形态）。甲 从渲染服务产物读。 */
const HAND = '<section style="margin:0px 0px 30px">'
  + '<section class="tableWrapper">'
  + '<table style="border:0px;border-collapse:collapse;table-layout:fixed;width:100%">'
  + '<colgroup><col><col style="width:90px;"></colgroup>'
  + '<tbody><tr>'
  + '<td valign="top" style="vertical-align:top;border:0px;padding:0px"><p>B1-left</p></td>'
  + '<td valign="top" style="vertical-align:top;border:0px;padding:0px"><p>B2-right</p></td>'
  + '</tr></tbody></table></section></section>'

const COLLAPSED = '<section style="margin:0px 0px 30px">'
  + '<section class="tableWrapper">'
  + '<table style="border:0px;border-collapse:collapse;table-layout:fixed;width:100%">'
  + '<colgroup><col style="min-width: 25px;"><col style="min-width: 25px;"></colgroup>'
  + '<tbody><tr>'
  + '<td valign="top" style="vertical-align:top;border:0px;padding:0px"><p>C1-left</p></td>'
  + '<td valign="top" style="vertical-align:top;border:0px;padding:0px"><p>C2-right</p></td>'
  + '</tr></tbody></table></section></section>'

const SAMPLES = [
  { id: 'jia-da01', 说明: '渲染服务产物 DA01（data-colwidth + <col style>）', html: readFileSync(DA01, 'utf8'), 判据: true },
  { id: 'yi-hand', 说明: '手写表格（只有 <col style="width:90px">，无任何属性写法）', html: HAND, 判据: false },
  { id: 'bing-collapsed', 说明: '用户那篇的塌陷形态（入口就没有宽度声明）', html: COLLAPSED, 判据: false },
]

/** 第二十五轮存档的旧编辑器出口：闸的自检拿它当反例。 */
const LEGACY_EXIT = resolve(BROWSER_OUT, 'r25_setcontent_r16-06-title-da01_before_saveexit.html')

// ---------- 入口 / 出口两侧都按同一套规则取「这一列声明了多宽」 ----------
// 解析规则**不在这里定义**：第二十八轮往返闸（U11-b）要用同一条规则，抽到 colwidth-rules.mjs 里，
// 免得两份闸各写一遍、对不上时说不清是哪把尺子偏了。规则本身一个字没改。
const colgroupsOf = (html) => {
  const group = /<colgroup[\s\S]*?<\/colgroup>/i.exec(html)
  return group ? group[0] : null
}
const countOf = (html, needle) => html.split(needle).length - 1

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
    const injected = sessionStorage.getItem('__r26_col_case');
    if (injected && ARTICLE_PATH.test(String(url))) {
      const stored = JSON.parse(sessionStorage.getItem('__r26_col_base') || '{}');
      return Promise.resolve(json(Object.assign({}, stored, { contentHtml: injected })));
    }
    return rawFetch.apply(this, arguments);
  };
  return 'installed';
})()`

/** 逼出一次保存：末段末尾敲一个字符再退格（净零编辑）。 */
const TRIGGER = `(() => {
  window.__writeGuard.lastSave = null;
  const dom = document.querySelector('.ProseMirror');
  const blocks = [...dom.querySelectorAll('p')].filter((el) => (el.textContent || '').trim().length > 0);
  const last = blocks[blocks.length - 1];
  if (!last) return 'no-p';
  dom.focus();
  const range = document.createRange(); range.selectNodeContents(last); range.collapse(false);
  const selection = window.getSelection(); selection.removeAllRanges(); selection.addRange(range);
  return 'ok';
})()`

const login = await (await fetch(API + '/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'Admin@123' }),
})).json()
if (!login.success) throw new Error('登录失败：' + JSON.stringify(login))
const token = login.data.token
const readArticle = async () => (await (await fetch(`${API}/api/articles/${ARTICLE_ID}`,
  { headers: { Authorization: `Bearer ${token}` } })).json()).data
const base = await readArticle()
const dbBefore = { revision: base.revision, updatedAt: base.updatedAt }

const { client, version, close } = await launchBrowser({ port: PORT })
console.log('浏览器:', version.Browser, '· 目标:', API, '· 前端:', BUNDLE || '(应用自带的 target/classes/static)')
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
  if (BUNDLE && String(request.url).startsWith(API)
    && !/^\/(api|uploads)\//.test(new URL(request.url).pathname)) {
    const path = new URL(request.url).pathname
    const asset = path.startsWith('/assets/') || /\.[a-z0-9]+$/i.test(path)
    const file = resolve(BUNDLE, asset ? path.slice(1) : 'index.html')
    if (file.startsWith(BUNDLE) && existsSync(file)) {
      const type = file.endsWith('.html') ? 'text/html'
        : file.endsWith('.js') ? 'text/javascript'
        : file.endsWith('.css') ? 'text/css'
        : file.endsWith('.svg') ? 'image/svg+xml'
        : file.endsWith('.png') ? 'image/png' : 'application/octet-stream'
      client.send('Fetch.fulfillRequest', {
        requestId, responseCode: 200,
        responseHeaders: [{ name: 'Content-Type', value: type + '; charset=utf-8' }],
        body: Buffer.from(readFileSync(file)).toString('base64'),
      }, page.sessionId).catch(() => {})
      return
    }
  }
  client.send('Fetch.continueRequest', { requestId }, page.sessionId).catch(() => {})
}])
await page.send('Fetch.enable', { patterns: [{ urlPattern: '*://*/*', requestStage: 'Request' }] })

await page.navigate(API + '/login')
await page.evaluate(`localStorage.setItem('wechat_bot_token', ${JSON.stringify(token)}); true`)
await page.evaluate(`sessionStorage.setItem('__r26_col_base', ${JSON.stringify(JSON.stringify(base))}); true`)

const load = async (html) => {
  await page.evaluate(`sessionStorage.setItem('__r26_col_case', ${JSON.stringify(html)}); true`)
  await page.navigate(`${API}/articles/${ARTICLE_ID}`)
  for (let attempt = 0; attempt < 80; attempt += 1) {
    if (await page.evaluate(`!!document.querySelector('.ProseMirror')`) === true) break
    await sleep(250)
  }
  await page.evaluate(`document.fonts.ready`)
  await sleep(400)
}
const saveAndCapture = async () => {
  await page.evaluate(TRIGGER)
  await page.send('Input.insertText', { text: 'x' })
  await sleep(120)
  for (const type of ['keyDown', 'keyUp']) {
    await page.send('Input.dispatchKeyEvent', {
      type, key: 'Backspace', code: 'Backspace', windowsVirtualKeyCode: 8, nativeVirtualKeyCode: 8,
    })
  }
  await sleep(2600)
  const exit = await page.evaluate(`JSON.stringify(window.__writeGuard.lastSave || null)`)
  return JSON.parse(exit)
}

const failures = []
const records = []
for (const sample of SAMPLES) {
  console.log('\n===== ' + sample.id + ' · ' + sample.说明 + ' =====')
  const entryWidths = widthsOf(sample.html)
  console.log('  入口列宽声明:', JSON.stringify(entryWidths), '· 入口 data-colwidth 出现', countOf(sample.html, 'data-colwidth'), '次')

  await load(sample.html)
  const exitHtml = await saveAndCapture()
  if (!exitHtml) { console.error('  !! 没拿到保存出口'); failures.push({ id: sample.id, 项: '拿不到保存出口' }); continue }
  writeFileSync(resolve(BROWSER_OUT, `r26_colwidth_exit_${sample.id}_saveexit.html`), exitHtml, 'utf8')

  const exitWidths = widthsOf(exitHtml)
  const exitDataColwidth = countOf(exitHtml, 'data-colwidth')
  console.log('  出口列宽声明:', JSON.stringify(exitWidths))
  console.log('  出口 <colgroup>:', colgroupsOf(exitHtml))
  console.log('  出口 data-colwidth 出现', exitDataColwidth, '次（入口是', countOf(sample.html, 'data-colwidth'), '次；出口只作记录，不参与判定）')

  await load(exitHtml)
  const exit2 = await saveAndCapture()
  const exit2Widths = exit2 ? widthsOf(exit2) : null
  console.log('  出口再存一次:', JSON.stringify(exit2Widths), '· <colgroup>', exit2 ? colgroupsOf(exit2) : '(缺)')

  const same = JSON.stringify(entryWidths) === JSON.stringify(exitWidths)
  const stable = JSON.stringify(exitWidths) === JSON.stringify(exit2Widths)
  console.log('  判据① 出口 = 入口：' + (same ? '✅' : '❌ ' + JSON.stringify(entryWidths) + ' → ' + JSON.stringify(exitWidths)))
  console.log('  判据② 再存一次不变：' + (stable ? '✅' : '❌ ' + JSON.stringify(exitWidths) + ' → ' + JSON.stringify(exit2Widths)))
  if (!sample.判据 && !same) {
    console.log('  ⚠️ 记录项：这一条不走判据（' + (sample.id === 'yi-hand'
      ? '入口缺口——`<col style>` 写法编辑器读不到，产物侧暴露为 0'
      : '入口本就没有宽度声明，一致即通过') + '）')
  }
  records.push({
    id: sample.id, 说明: sample.说明, 是判据: sample.判据,
    入口列宽: entryWidths, 出口列宽: exitWidths, 再存列宽: exit2Widths,
    入口dataColwidth次数: countOf(sample.html, 'data-colwidth'), 出口dataColwidth次数: exitDataColwidth,
    判据一: same, 判据二: stable,
  })
  if (sample.判据 && !same) failures.push({ id: sample.id, 项: '判据① 出口列宽与入口不一致', 入口: entryWidths, 出口: exitWidths })
  if (sample.判据 && !stable) failures.push({ id: sample.id, 项: '判据② 再存一次列宽变了', 出口: exitWidths, 再存: exit2Widths })
}

// —— 闸的自检：存档的旧编辑器出口必须被判 FAIL，否则这条闸已经写松了 ——
const DA01_WIDTHS = widthsOf(readFileSync(DA01, 'utf8'))
let selfTest = null
if (existsSync(LEGACY_EXIT)) {
  const legacyWidths = widthsOf(readFileSync(LEGACY_EXIT, 'utf8'))
  const judged = JSON.stringify(DA01_WIDTHS) !== JSON.stringify(legacyWidths)
  selfTest = { 存档: LEGACY_EXIT, 产物应该长这样: DA01_WIDTHS, 旧编辑器出口: legacyWidths, 判为FAIL: judged }
  console.log('\n闸的自检（旧编辑器出口必须被判 FAIL）：')
  console.log('  产物该有的列宽 ' + JSON.stringify(DA01_WIDTHS) + ' · 旧编辑器出口 ' + JSON.stringify(legacyWidths)
    + ' → ' + (judged ? '判 FAIL ✅（这条闸抓得住这个 bug）' : '判 PASS ❌（闸写松了）'))
  if (!judged) failures.push({ id: 'self-test', 项: '旧编辑器出口被判成了 PASS' })
}

const dbAfter = await readArticle()
const unchanged = dbAfter.revision === dbBefore.revision && dbAfter.updatedAt === dbBefore.updatedAt
console.log('\n库核对：改前 revision=' + dbBefore.revision + ' / 改后 revision=' + dbAfter.revision
  + ' · updatedAt ' + (unchanged ? '逐字未变 ✅' : '变了 ❌'))

writeFileSync(resolve(BROWSER_OUT, 'r26_colwidth_exit.json'), JSON.stringify({
  browser: version.Browser, origin: API, bundle: BUNDLE || null,
  samples: records, selfTest, failures, dbBefore, dbAfter, dbUnchanged: unchanged,
}, null, 1), 'utf8')

await page.close()
close()
if (failures.length) {
  console.error('\n❌ 列宽闸未过：' + JSON.stringify(failures))
  process.exit(1)
}
if (!unchanged) { console.error('\n❌ 库里的 revision/updatedAt 变了'); process.exit(1) }
console.log('\n✅ 列宽闸通过：产物样本「出口列宽 = 入口列宽」且再存一次不变；旧编辑器出口仍被判 FAIL')
