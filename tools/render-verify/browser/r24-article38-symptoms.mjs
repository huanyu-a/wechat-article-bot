/**
 * 第二十四轮 · **用户自己的文章 #38** 上的「症状还在不在」逐条实测。
 *
 * 为什么另起一支：第十六~二十三轮最硬的证据都出自 `probe_r16.html`（我们自己起的静态页，还要
 * `--width-match` 把两栏拉成同宽）。用户的原话是「我在编辑器里看见这些毛病」，所以这一支
 * **不灌任何用例内容**——就去 `http://127.0.0.1:8081` 打开**真实的那篇文章**（默认 #38），
 * 在**真实窗口宽度**下（不做 `--width-match` 那种人为同宽），对用户写在正文里的 8 条批注
 * 逐条量「他抱怨的那个症状，现在还在不在」。
 *
 * 三列同尺子：同一个浏览器、同一个页面宽度、同一组测量函数。差别只在**量谁**：
 *   - `editor`：真实编辑器里那一份（用户实际看到的）—— 改后值
 *   - `product`：后端渲染 API 的产物 HTML（`target/probe/r16/<case>.html`，与 #38 里的内容**逐字同源**），
 *     塞进一个与编辑器正文栏**等宽**的离屏容器里量 —— 产物值（即「原项目/公众号侧」应有的样子）
 *   - 改前值：同一支脚本加 `--bundle target/probe/r24/before-dist` 再跑一遍
 *     （那份前端是 `HEAD` 的三个源文件重新构建的，即用户报障当时的前端）
 *
 * ⚠️ **不写库**：与应用层三层拦截同款（patch fetch/XHR/sendBeacon + CDP Fetch failRequest +
 * 跑完回读 revision/updatedAt），收尾照旧故意逼一次自动保存自证。
 *
 * 用法：
 *   node tools/render-verify/browser/r24-article38-symptoms.mjs 38 --label after
 *   node tools/render-verify/browser/r24-article38-symptoms.mjs 38 --label before --bundle target/probe/r24/before-dist
 * 产物：target/probe/browser/r24_article38_<label>_result.json
 *       target/probe/browser/shots/r24-article38/<label>/<item>.png
 */
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs'
import { resolve, join } from 'node:path'
import { launchBrowser, openPage } from './cdp.mjs'
import { OUT, BROWSER_OUT, ROOT, API, login } from '../paths.mjs'
import { PROBE_SRC as PROBE_SRC_SOURCE } from './r24-probes.mjs'

const ARGS = process.argv.slice(2)
const ARTICLE_ID = Number(ARGS.find((item) => /^\d+$/.test(item)) || 38)
const LABEL = (() => {
  const at = ARGS.indexOf('--label')
  return at >= 0 && ARGS[at + 1] ? ARGS[at + 1] : 'after'
})()
/** `--bundle <dir>`：把**整个前端**换成这个目录里的构建产物（API 仍打真实应用）。 */
const BUNDLE = (() => {
  const at = ARGS.indexOf('--bundle')
  return at >= 0 && ARGS[at + 1] ? resolve(ROOT, ARGS[at + 1]) : null
})()
/**
 * `--inject <caseId>`：不打开 #38 的正文，改成把**该用例的产物 HTML** 经应用自己的 `setContent`
 * 灌进去（在应用层伪造那一次 GET 的返回，与应用打开文章走的是同一条路），然后量**保存出口**。
 * 用途：回答「这些结构现在是**编辑器还在丢**，还是 #38 存库那份**早就没有**了」。
 */
const INJECT = (() => {
  const at = ARGS.indexOf('--inject')
  return at >= 0 && ARGS[at + 1] ? ARGS[at + 1] : null
})()
/**
 * `--paste`：不用 `setContent` 灌，改成**粘贴**（合成 `ClipboardEvent`，与 `r16-live-editor.mjs`
 * 的粘贴模式同款）。必须与 `--inject` 一起用。用来回答第二十三轮留下的那个「是推理不是实测」的问题：
 * **粘贴那条路的保存出口（`getHTML()` 的实际落点）里，`<section>` 是不是也少了。**
 */
const PASTE = ARGS.includes('--paste')

const SETDIR = resolve(OUT, 'r16')
const SHOTS = resolve(BROWSER_OUT, 'shots/r24-article38', INJECT ? (PASTE ? 'paste-' : 'inject-') + INJECT : LABEL)
const RESULT_FILE = resolve(BROWSER_OUT, INJECT
  ? `r24_${PASTE ? 'paste' : 'inject'}_${INJECT}_${LABEL}_result.json` : `r24_article38_${LABEL}_result.json`)
mkdirSync(SHOTS, { recursive: true })
const sleep = (ms) => new Promise((done) => setTimeout(done, ms))

if (BUNDLE && !existsSync(join(BUNDLE, 'index.html'))) {
  console.error('--bundle 目录里没有 index.html：' + BUNDLE)
  process.exit(3)
}

// ---------------------------------------------------------------------------
// 登录 & 取真实 payload（这一支**不换**内容，整篇照原样打开）
// ---------------------------------------------------------------------------
if (!login.success) throw new Error('登录失败：' + JSON.stringify(login))
const token = await login()

const readArticle = async () => {
  const response = await fetch(`${API}/api/articles/${ARTICLE_ID}`, { headers: { Authorization: `Bearer ${token}` } })
  const data = (await response.json()).data || {}
  return { revision: data.revision, updatedAt: data.updatedAt, contentLength: (data.contentHtml || '').length }
}
const before = await readArticle()
console.log(`目标文章 #${ARTICLE_ID} 改前：revision=${before.revision} updatedAt=${before.updatedAt} contentHtml=${before.contentLength} 字符`)

// ---------------------------------------------------------------------------
// 11 条用例的**产物 HTML**（后端渲染 API 的输出），用来填「产物值」那一列。
// 与 #38 正文里的同源内容逐字一致（见 `gen/round16_editor_reported.py` 的 CASES）。
// ---------------------------------------------------------------------------
const meta = JSON.parse(readFileSync(join(SETDIR, 'r16.json'), 'utf8'))
const products = meta.cases.map((item) => ({
  id: item.id, name: item.name, complaint: item.complaint,
  html: readFileSync(join(SETDIR, item.id + '.html'), 'utf8'),
}))
console.log('产物用例数:', products.length)

// ---------------------------------------------------------------------------
// 应用层守卫 + （可选）整包换前端
// ---------------------------------------------------------------------------
const GUARD = `(() => {
  window.__writeGuard = { blocked: [], seen: 0, served: 0 };
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
    } catch (error) { /* 拿不到就按原样放行 */ }
    window.__writeGuard.seen += 1;
    if (mutating(verb, url)) {
      let body = {};
      try { body = init && init.body ? JSON.parse(init.body) : {}; } catch (error) { body = {}; }
      // **顺手留一份「保存出口」**：这就是应用真正要 PUT 出去的正文（= 编辑器 getHTML() 的落点）。
      // 它回答「所见 ≠ 所存」那类问题，且拿的是**真出口**、不是推理。
      window.__writeGuard.lastSave = { verb: verb, url: String(url), contentHtml: body.contentHtml || null };
      window.__writeGuard.blocked.push(verb + ' ' + url);
      return Promise.resolve(json(Object.assign({}, body, { updatedAt: new Date().toISOString() })));
    }
    // 注入模式：只把这一篇的正文换成用例产物，其余字段照旧，让**应用自己**去 setContent。
    const injected = localStorage.getItem('__r24_case');
    if (injected && ARTICLE_PATH.test(String(url))) {
      const base = JSON.parse(localStorage.getItem('__r24_base') || '{}');
      window.__writeGuard.served += 1;
      return Promise.resolve(json(Object.assign({}, base, { contentHtml: injected })));
    }
    return rawFetch.apply(this, arguments);
  };
  const rawOpen = XMLHttpRequest.prototype.open;
  const rawSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function (method, url) {
    this.__guard = { method: method, url: url };
    return rawOpen.apply(this, arguments);
  };
  XMLHttpRequest.prototype.send = function () {
    window.__writeGuard.seen += 1;
    if (this.__guard && mutating(this.__guard.method, this.__guard.url)) {
      window.__writeGuard.blocked.push('XHR ' + this.__guard.method + ' ' + this.__guard.url);
      this.abort();
      return;
    }
    return rawSend.apply(this, arguments);
  };
  const rawBeacon = navigator.sendBeacon && navigator.sendBeacon.bind(navigator);
  if (rawBeacon) navigator.sendBeacon = function (url, data) {
    if (mutating('POST', url)) { window.__writeGuard.blocked.push('BEACON POST ' + url); return true; }
    return rawBeacon(url, data);
  };
  return 'guard installed';
})()`

// ---------------------------------------------------------------------------
// 症状探针：**一个测量函数，量任何根元素**（真实编辑器 / 产物离屏容器 用的是同一份）。
// 全部返回 px / 计数 / 布尔，不返回「好看/难看」这类主观词。
// ---------------------------------------------------------------------------
const PROBE_SRC = PROBE_SRC_SOURCE

/** 把产物 HTML 塞进一个与编辑器正文栏**等宽、同一个 CSS 上下文**的离屏容器，用同一把尺子量。 */
const measureProducts = (itemsJson) => `(() => {
  const dom = document.querySelector('.ProseMirror');
  const style = dom ? getComputedStyle(dom) : null;
  const width = dom
    ? Math.round((dom.getBoundingClientRect().width - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight)) * 100) / 100
    : 0;
  const holder = document.createElement('div');
  holder.id = '__r24_ref';
  // **带上与编辑器相同的 class**：编辑器那条 p 段落样式规则必须同样生效，
  // 否则「产物值」那一列量的是没有样式上下文的裸 HTML，跟编辑器那列不是一把尺子。
  holder.className = dom ? dom.className : '';
  holder.setAttribute('style', 'position:absolute;left:-99999px;top:0;visibility:visible;'
    + 'width:' + width + 'px;padding:0;margin:0;');
  document.body.append(holder);
  const products = ${itemsJson};
  const out = [];
  for (const [index, product] of products.entries()) {
    holder.innerHTML = product.html;
    const probe = window.__r24.ITEMS[index].run(holder);
    const node = [...holder.querySelectorAll('*')].filter((el) => (el.textContent || '').includes(window.__r24.ITEMS[index].anchor))[0] || null;
    const nodeStyle = node ? getComputedStyle(node) : null;
    out.push({ id: product.id, name: product.name, anchorHtmlChars: product.html.length, value: probe,
      root: node ? { tag: node.tagName.toLowerCase(),
        边框: nodeStyle.borderTopWidth + ' ' + nodeStyle.borderTopStyle,
        圆角: Math.round(parseFloat(nodeStyle.borderRadius) * 100) / 100 } : null });
  }
  holder.remove();
  return JSON.stringify({ contentWidth: width, products: out });
})()`

// ---------------------------------------------------------------------------

const { client, version, close } = await launchBrowser({ port: 9356 })
console.log('浏览器:', version.Browser, '· 目标:', API, '· 前端:', BUNDLE || '(应用自带的 target/classes/static)')
const page = await openPage(client)
// 控制台留痕：换包跑的时候，前端起不来必须能一眼看见原因，不能只看到「正文 0 字」。
const consoleMessages = []
client.listeners.set('Runtime.consoleAPICalled', [(message) => {
  consoleMessages.push(message.params.type + ': '
    + (message.params.args || []).map((arg) => arg.value ?? arg.description ?? '').join(' ').slice(0, 300))
}])
client.listeners.set('Runtime.exceptionThrown', [(message) => {
  const detail = message.params.exceptionDetails || {}
  consoleMessages.push('exception: ' + String(detail.text || '') + ' ' + String(detail.exception && detail.exception.description || '').slice(0, 300))
}])
await page.send('Runtime.enable')
// **真实窗口宽度**：不做 --width-match，正文栏就是用户看到的那一栏。
await page.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
await page.send('Page.addScriptToEvaluateOnNewDocument', { source: GUARD })

// 第 2 层：网络栈拦截；**同时**（可选）把整包前端换成指定构建产物
const blockedAtNetwork = []
const servedFromBundle = []
client.listeners.set('Fetch.requestPaused', [(message) => {
  const { requestId, request } = message.params
  const verb = String(request.method || 'GET').toUpperCase()
  if (verb !== 'GET' && verb !== 'HEAD') {
    blockedAtNetwork.push(verb + ' ' + request.url)
    client.send('Fetch.failRequest', { requestId, errorReason: 'Aborted' }, page.sessionId).catch(() => {})
    return
  }
  // `/api`、`/uploads` 一律放行给真实应用（它们不是前端资源）。
  if (BUNDLE && String(request.url).startsWith(API)
    && !/^\/(api|uploads)\//.test(new URL(request.url).pathname)) {
    const path = new URL(request.url).pathname
    // 静态资源按原路径取；其余一律回 `index.html`——与应用的 SPA 兜底同规则。
    // （第二十四轮踩过的坑：只映射 `/` 与 `/index.html` 时，第二次整页导航到
    //  `/articles/38` 会落到**应用自己那份** index.html，于是「换包」静默失效、两组数字一模一样。
    //  第一版修完又把 `/api/articles/38` 也当成 SPA 路由回了一份 HTML，前端直接崩。）
    const asset = path.startsWith('/assets/') || /\.[a-z0-9]+$/i.test(path)
    const wanted = asset ? path.slice(1) : 'index.html'
    const file = resolve(BUNDLE, wanted)
    if (file.startsWith(BUNDLE) && existsSync(file)) {
      const type = file.endsWith('.html') ? 'text/html'
        : file.endsWith('.js') ? 'text/javascript'
        : file.endsWith('.css') ? 'text/css'
        : file.endsWith('.svg') ? 'image/svg+xml'
        : file.endsWith('.png') ? 'image/png' : 'application/octet-stream'
      servedFromBundle.push(path)
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
await page.evaluate(`localStorage.setItem('wechat_bot_token', ${JSON.stringify(token)})`)
if (INJECT && !PASTE) {
  const caseHtml = readFileSync(join(SETDIR, INJECT + '.html'), 'utf8')
  const base = (await (await fetch(`${API}/api/articles/${ARTICLE_ID}`, { headers: { Authorization: `Bearer ${token}` } })).json()).data
  await page.evaluate(`localStorage.setItem('__r24_base', ${JSON.stringify(JSON.stringify(base))});`
    + `localStorage.setItem('__r24_case', ${JSON.stringify(caseHtml)}); true`)
  console.log('注入用例:', INJECT, '·', caseHtml.length, '字符')
}
await page.navigate(`${API}/articles/${ARTICLE_ID}`)

let ready = { loaded: false, textLength: 0 }
const minText = INJECT ? 10 : 1000
for (let attempt = 0; attempt < 80; attempt += 1) {
  ready = JSON.parse(await page.evaluate(`(() => {
    const dom = document.querySelector('.ProseMirror');
    return JSON.stringify({ loaded: !!dom && dom.textContent.replace(/\\s+/g, '').length > ${minText},
      textLength: dom ? dom.textContent.replace(/\\s+/g, '').length : 0 });
  })()`))
  if (ready.loaded) break
  await sleep(250)
}
console.log('编辑器就绪:', ready.loaded, '· 正文', ready.textLength, '字')
if (!ready.loaded) { console.error('!! 编辑器没挂上正文'); console.error(consoleMessages.join('\n')); process.exit(2) }
// **自证前端到底是哪一份**：换包跑的时候，「量出来一样」必须先排除「包没换成」。
const loadedChunks = JSON.parse(await page.evaluate(`(() => JSON.stringify(
  performance.getEntriesByType('resource').map((entry) => entry.name)
    .filter((name) => /ArticleEditorView|\\/assets\\/index-/.test(name))))()`))
console.log('实际加载的编辑器 chunk:', loadedChunks.map((name) => name.split('/').pop()).join(' | '))
await page.evaluate(`(async () => {
  await document.fonts.ready;
  // 图片也必须等：头像那一条抱怨的就是图，图没下完量出来是 0×0（会误判成「还在」）。
  await Promise.all([...document.images].map((img) => img.complete ? 0
    : new Promise((done) => { img.onload = done; img.onerror = done; setTimeout(done, 8000) })));
  return true })()`)
await sleep(500)

const probeCount = await page.evaluate(PROBE_SRC)
console.log('症状探针数:', probeCount)

/** 真 Ctrl+A（只改浏览器选择不算——ProseMirror 的内部 selection 不会跟着走）。 */
const selectAll = async () => {
  await page.evaluate(`(() => { document.querySelector('.ProseMirror').focus(); return true })()`)
  for (const type of ['keyDown', 'keyUp']) {
    await page.send('Input.dispatchKeyEvent', { type, modifiers: 2, key: 'a', code: 'KeyA', windowsVirtualKeyCode: 65, nativeVirtualKeyCode: 65 })
  }
  await sleep(120)
}
let pasteReport = null
if (PASTE) {
  if (!INJECT) { console.error('--paste 需要同时给 --inject <caseId>'); process.exit(3) }
  const caseHtml = readFileSync(join(SETDIR, INJECT + '.html'), 'utf8')
  await page.evaluate(`(async () => { await document.fonts.ready })()`)
  await selectAll()
  pasteReport = JSON.parse(await page.evaluate(`(() => {
    const dom = document.querySelector('.ProseMirror');
    const html = ${JSON.stringify(caseHtml)};
    const ruler = document.createElement('div'); ruler.innerHTML = html;
    dom.focus();
    const transfer = new DataTransfer();
    transfer.setData('text/html', html);
    transfer.setData('text/plain', ruler.textContent);
    dom.dispatchEvent(new ClipboardEvent('paste', { clipboardData: transfer, bubbles: true, cancelable: true }));
    const actual = dom.textContent.replace(/\\s+/g, '').length;
    return JSON.stringify({ textLength: actual, expected: ruler.textContent.replace(/\\s+/g, '').length,
      sections: dom.querySelectorAll('section').length });
  })()`))
  console.log('粘贴结果：正文', pasteReport.textLength, '/', pasteReport.expected, '字 · 实时 DOM 模块',
    pasteReport.sections, '个')
  await sleep(300)
}

const measured = JSON.parse(await page.evaluate(`(() => {
  const dom = document.querySelector('.ProseMirror');
  const style = getComputedStyle(dom);
  return JSON.stringify({
    panelWidth: Math.round(dom.getBoundingClientRect().width * 100) / 100,
    panelInnerWidth: Math.round((dom.getBoundingClientRect().width - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight)) * 100) / 100,
    paperWidth: document.querySelector('.paper') ? Math.round(document.querySelector('.paper').getBoundingClientRect().width * 100) / 100 : null,
    nodeCount: dom.querySelectorAll('*').length,
    sectionCount: dom.querySelectorAll('section').length,
    items: window.__r24.measureAll(dom),
  });
})()`))
console.log('正文栏内宽:', measured.panelInnerWidth, 'px · .paper:', measured.paperWidth, 'px · 模块', measured.sectionCount, '个')

const productSide = JSON.parse(await page.evaluate(measureProducts(JSON.stringify(products.map((item) => ({ id: item.id, html: item.html }))))))
console.log('产物侧等比容器宽:', productSide.contentWidth, 'px')

// 逐条截图（整篇太长，只按条目裁）
const clipOfRule = (anchor, index) => `(() => {
  const dom = document.querySelector('.ProseMirror');
  const anchors = ${JSON.stringify(measured.items.map((item, at) => [at, products[at].id]))};
  const item = window.__r24.ITEMS[${index}];
  const hits = [...dom.querySelectorAll('*')].filter((el) => (el.textContent || '').includes(item.anchor));
  if (!hits.length) return null;
  const r = hits[0].getBoundingClientRect();
  return JSON.stringify({ x: r.x + window.scrollX, y: r.y + window.scrollY, width: r.width, height: Math.min(r.height, 2400) });
})()`
for (const [index, item] of measured.items.entries()) {
  const clip = await page.evaluate(clipOfRule(item.anchor, index))
  if (!clip || clip === 'null') continue
  const shot = await client.send('Page.captureScreenshot', {
    format: 'png', captureBeyondViewport: true, clip: { ...JSON.parse(clip), scale: 0.5 },
  }, page.sessionId)
  writeFileSync(resolve(SHOTS, `${item.id}.png`), Buffer.from(shot.data, 'base64'))
}

// 写拦截自证
console.log('\n等 2.6s 逼出一次自动保存，验证写拦截真的生效…')
await page.evaluate(`(() => { const dom = document.querySelector('.ProseMirror');
  const range = document.createRange(); range.setStart(dom, 0); range.collapse(true);
  const selection = window.getSelection(); selection.removeAllRanges(); selection.addRange(range);
  dom.focus(); return true })()`)
await page.send('Input.insertText', { text: 'x' })
await sleep(2600)
const selfTest = JSON.parse(await page.evaluate(`JSON.stringify(window.__writeGuard || null)`))
console.log('  自动保存被挡下 ' + (selfTest?.blocked?.length ?? 0) + ' 次：' + (selfTest?.blocked || []).slice(0, 3).join(' | '))

// 「所见 ≠ 所存」实测：拿**真保存出口**（被挡下的那次 PUT 的正文）与实时 DOM 比模块层数。
// 指纹一并留下：换包跑的时候，「结构有没有被保存这一关吃掉」要能逐项对上，不能只看层数。
const count = (text, re) => (text.match(re) || []).length
const fingerprint = (text) => text === null ? null : {
  chars: text.length,
  sections: count(text, /<section/g),
  表格: count(text, /<table/g),
  colgroup片段: (text.match(/<colgroup>.{0,160}/) || [null])[0],
  圆点元素: count(text, /border-radius:50%/g),
  绝对定位元素: count(text, /position:\s*absolute/g),
  有e2e8f0边框: /border:\s*1px solid rgb\(226,\s*232,\s*240\)/.test(text),
  有渐变: count(text, /linear-gradient/g),
}
const saveExitHtml = selfTest?.lastSave?.contentHtml || null
const saveFingerprint = fingerprint(saveExitHtml)
const productFingerprint = INJECT ? fingerprint(products.find((item) => item.id === INJECT)?.html ?? null) : null
console.log('  保存出口指纹:', JSON.stringify(saveFingerprint))
if (productFingerprint) console.log('  产物指纹    :', JSON.stringify(productFingerprint))
const liveSections = measured.sectionCount
const saveSections = saveExitHtml ? (saveExitHtml.match(/<section/g) || []).length : null
console.log('  保存出口 vs 实时 DOM 的 <section> 层数：'
  + (saveSections === null ? '(没拿到保存正文)' : saveSections + ' vs ' + liveSections
    + (saveSections === liveSections ? ' ✅ 一致' : ' ❌ 不一致')))

await page.navigate(`${API}/articles/${ARTICLE_ID}`)
await sleep(1500)
await page.evaluate(`localStorage.removeItem('__r24_case'); true`)
const after = await readArticle()
const unchanged = after.revision === before.revision && after.updatedAt === before.updatedAt
console.log('库核对：改前 revision=' + before.revision + ' / 改后 revision=' + after.revision
  + ' · updatedAt ' + (unchanged ? '逐字未变 ✅' : '变了 ❌'))

writeFileSync(RESULT_FILE, JSON.stringify({
  browser: version.Browser, origin: API, articleId: ARTICLE_ID, label: LABEL,
  bundle: BUNDLE || null, servedFromBundle: servedFromBundle.length,
  writeGuard: { appLayer: selfTest?.blocked || [], networkLayer: blockedAtNetwork, dbUnchanged: unchanged },
  db: { before, after },
  editor: measured,
  product: productSide,
  saveExit: {
    got: saveExitHtml !== null,
    chars: saveExitHtml ? saveExitHtml.length : null,
    sections: saveSections,
    liveSections,
    same: saveSections === liveSections,
    fingerprint: saveFingerprint,
    productFingerprint,
    injected: INJECT || null,
    pasted: PASTE,
  },
  pasteReport,
}, null, 1), 'utf8')
console.log('结果:', RESULT_FILE)

await page.close()
close()
if (!unchanged) { console.error('!! 文章被改动了，必须人工核查'); process.exit(1) }
