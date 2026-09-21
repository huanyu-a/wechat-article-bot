/**
 * 第二十二轮 · **真实应用界面**上的 11 条验收（不是探针页）。
 *
 * 为什么要有这一支：第十六~二十一轮的 r16 证据全部出自 `probe_r16.html`——那是我们自己起的
 * 一个静态页，里面 `new Editor({ extensions: SETS.current })`。虽然 `editor-setup.js` 引的是
 * **同一个** `webui/src/editorExtensions.js`、扩展集与配置也逐项相同，但它**不是**用户点开的那个界面。
 * 这一支就是去 `http://127.0.0.1:8081` 的编辑器里，用**同一组探针定义**（`r16-probes.js`）再量一遍，
 * 两边对不上就是新缺陷。
 *
 * **走的是用户真正走的那条路径**：应用打开文章时是
 *     GET /api/articles/<id>  →  editor.commands.setContent(payload.contentHtml, false)
 * 所以这一支不去「粘贴」，而是**在应用层伪造那次 GET 的返回**，把用例的 `contentHtml` 塞进 payload，
 * 让应用自己调 `setContent`。这样量到的就是「用户打开这篇文章时看到的画面」。
 *
 * （`--paste` 模式另走一条路：直接把 HTML 作为粘贴事件灌进去。两者结果**不一样**，
 *  已经实测到差异，见 `docs/dev/known-issues-handoff.md` §3.22。默认走 setContent 那条。）
 *
 * ⚠️ **不写库**——三层保证：
 *   1. `Page.addScriptToEvaluateOnNewDocument` 在应用脚本之前 patch 掉 `fetch` / `XMLHttpRequest`，
 *      非 GET 的 `/api/*` 全部本地伪造 200 应答（连请求都不发出去）；
 *   2. CDP `Fetch` 域在**网络栈**上再拦一层，非 GET 直接 `failRequest`（第一层漏了才会走到这里）；
 *   3. 跑完回读文章 `revision` / `updatedAt`，与跑前逐字比对，没变才算数。
 * 三层计数都写进产物；收尾还会**故意逼一次自动保存**来自证拦截真的生效。
 *
 * 用法：node tools/render-verify/browser/r16-live-editor.mjs [articleId] [--paste]
 * 产物：target/probe/browser/r16_live_result.json（或 r16_live_paste_result.json）、
 *       shots/r16-live/<id>.{png,zoom.png}
 *
 * 第二十三轮补了第四种模式 `--type`：**把用例的 Markdown 源文逐行敲进去**（真 `input` 事件 + 真回车键，
 * 不走剪贴板）。用意是把「用户到底是怎么把 `:::` 弄进编辑器的」这三条路各自量一遍：
 *   - `setContent`（默认）＝ 打开已有文章，后端 `contentHtml` 直接进编辑器 —— **用户实际走的那条**
 *   - `--type`            ＝ 在编辑器里直接键入 `:::changelog` 这类语法文本
 *   - `--paste`/`--paste-real` ＝ 从别处粘进来
 * 三条路的结果**不一样**，判定见 `docs/dev/known-issues-handoff.md` §3.23。
 */
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { resolve, join } from 'node:path'
import { launchBrowser, openPage } from './cdp.mjs'
import { OUT, BROWSER_OUT, RENDER_VERIFY, API, login } from '../paths.mjs'

const ARGS = process.argv.slice(2)
/**
 * `--type`：**逐行键入**用例的 Markdown 源文。
 * `Input.insertText` 走的是真实 `input` 事件（不是剪贴板 API），行间用真回车键（`Input.dispatchKeyEvent`）。
 * 这是「用户在编辑器里手打 `:::` 语法」那条路的忠实模拟。
 */
const TYPE_MODE = ARGS.includes('--type')
const PASTE_MODE = ARGS.includes('--paste')
/**
 * `--width-match`：真实界面的正文栏由 `.paper{max-width:820px}` 卡死，实测内宽 **684px**；
 * 探针页的编辑器栏是 **731px**。两边差 47px，所有「宽度派生」的探针值都会跟着平移，
 * 看起来像差异其实不是。这个开关把 `.paper` 临时放宽到 867px（= 731 + 左右 padding 132 + 边框 2），
 * 让两边**同宽同量**——差异消失才说明「不一致只是量具宽窄，不是行为不同」。
 * 只改内存里的样式，不写库、不改代码。
 */
const WIDTH_MATCH = ARGS.includes('--width-match')
/** `--paste-real`：走**真剪贴板 + 真 Ctrl+V**，用来排除「合成 ClipboardEvent 才是差异来源」。 */
const PASTE_REAL = ARGS.includes('--paste-real')
const ARTICLE_ID = Number(ARGS.find((item) => /^\d+$/.test(item)) || 38)
/**
 * 模式名。**截图必须按模式分目录**：第二十二轮四种模式共用一个 `shots/r16-live/`，
 * 后跑的把先跑的画面覆盖掉了，文档里引用「真实界面截图」时实际指向的是**最后那次粘贴**的图。
 * 第二十三轮改成 `shots/r16-live/<mode>/`，并把各模式跑出来的图重新对齐到各自的结果文件。
 */
const MODE = TYPE_MODE ? 'typed' : PASTE_REAL ? 'paste-real' : PASTE_MODE ? 'paste' : WIDTH_MATCH ? 'widthmatch' : 'setcontent'
const SETDIR = resolve(OUT, 'r16')
const SHOTS = resolve(BROWSER_OUT, 'shots/r16-live', MODE)
const PROBES_SRC = resolve(RENDER_VERIFY, 'browser/r16-probes.js')
const RESULT_FILE = resolve(BROWSER_OUT, TYPE_MODE ? 'r16_live_type_result.json'
  : PASTE_REAL ? 'r16_live_pastereal_result.json'
  : PASTE_MODE ? 'r16_live_paste_result.json'
  : WIDTH_MATCH ? 'r16_live_widthmatch_result.json' : 'r16_live_result.json')

mkdirSync(SHOTS, { recursive: true })
const sleep = (ms) => new Promise((done) => setTimeout(done, ms))

// ---------------------------------------------------------------------------
// 登录 & 取真实 payload（只读；`contentHtml` 后面会被逐条换掉）
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

const basePayload = (await (await fetch(`${API}/api/articles/${ARTICLE_ID}`,
  { headers: { Authorization: `Bearer ${token}` } })).json()).data
console.log('payload 字段数:', Object.keys(basePayload).length)

// ---------------------------------------------------------------------------
// 应用层守卫：挡写请求 + 伪造这一篇的 GET 返回（把用例的 contentHtml 塞进去）
// ---------------------------------------------------------------------------
const GUARD = `(() => {
  window.__writeGuard = { blocked: [], seen: 0, served: 0 };
  const BASE = ${JSON.stringify(basePayload)};
  const ARTICLE_PATH = /^\\/api\\/articles\\/${ARTICLE_ID}(\\?|$)/;
  const mutating = (method, url) => {
    const verb = String(method || 'GET').toUpperCase();
    return verb !== 'GET' && verb !== 'HEAD' && /\\/api\\//.test(String(url || ''));
  };
  const json = (data) => new Response(JSON.stringify({ success: true, data }),
    { status: 200, headers: { 'Content-Type': 'application/json' } });
  const rawFetch = window.fetch;
  window.fetch = function (input, init) {
    let url = '', verb = 'GET';
    try {
      url = typeof input === 'string' ? input : (input && input.url) || '';
      verb = String((init && init.method) || (input && input.method) || 'GET').toUpperCase();
    } catch (error) { /* 拿不到就按原样放行 */ }
    window.__writeGuard.seen += 1;
    if (mutating(verb, url)) {
      window.__writeGuard.blocked.push(verb + ' ' + url);
      let body = {};
      try { body = init && init.body ? JSON.parse(init.body) : {}; } catch (error) { body = {}; }
      return Promise.resolve(json(Object.assign({}, body, { updatedAt: new Date().toISOString() })));
    }
    // 这一篇的正文读取：换成当前用例（localStorage 里放着），其余字段照旧
    const injected = localStorage.getItem('__r16_case');
    if (injected && ARTICLE_PATH.test(String(url))) {
      window.__writeGuard.served += 1;
      return Promise.resolve(json(Object.assign({}, BASE, { contentHtml: injected })));
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
  if (!localStorage.getItem('__r16_case')) localStorage.setItem('__r16_case', BASE.contentHtml);
  return 'guard installed';
})()`

// ---------------------------------------------------------------------------
// 探针定义：直接吃 `r16-probes.js` 的源码，**不复制**，保证与探针页同一把尺子。
// 该文件没有 import，去掉顶层的 `export ` 前缀即可整体放进一个函数作用域。
// ---------------------------------------------------------------------------
const PROBE_BUNDLE = readFileSync(PROBES_SRC, 'utf8').replace(/^export /gm, '')
const INJECT_PROBES = `(() => { ${PROBE_BUNDLE}
  window.__r16 = { PROBES: PROBES, runProbe: runProbe };
  return Object.keys(PROBES).length;
})()`

/** `--paste` 模式的灌入方式：Ctrl+A（由 CDP 发真键盘事件）+ 带 text/html 的 paste 事件。 */
const pasteInto = (html, text) => `(() => {
  const dom = document.querySelector('.ProseMirror');
  if (!dom) return JSON.stringify({ ok: false, reason: '页面上没有 .ProseMirror' });
  const html = ${JSON.stringify(html)};
  const ruler = document.createElement('div');
  ruler.innerHTML = html;
  const expectedTextLength = ruler.textContent.replace(/\\s+/g, '').length;
  dom.focus();
  const transfer = new DataTransfer();
  transfer.setData('text/html', html);
  transfer.setData('text/plain', ${JSON.stringify(text)});
  const handled = !dom.dispatchEvent(new ClipboardEvent('paste',
    { clipboardData: transfer, bubbles: true, cancelable: true }));
  const actual = dom.textContent.replace(/\\s+/g, '').length;
  return JSON.stringify({ ok: true, handled, textLength: actual, expectedTextLength,
    replaced: actual === expectedTextLength });
})()`

/** 等编辑器把这条用例挂上去（`setContent` 那条路要等应用自己拉完 payload）。 */
const waitLoaded = (expected) => `(() => {
  const dom = document.querySelector('.ProseMirror');
  if (!dom) return JSON.stringify({ loaded: false });
  const text = dom.textContent.replace(/\\s+/g, '').length;
  return JSON.stringify({ loaded: text === ${expected}, textLength: text,
    guard: window.__writeGuard || null });
})()`

const measureCase = (id) => `(() => {
  const dom = document.querySelector('.ProseMirror');
  if (!dom) return JSON.stringify({ error: 'no .ProseMirror' });
  const style = getComputedStyle(dom);
  return JSON.stringify({
    textLength: dom.textContent.replace(/\\s+/g, '').length,
    html: dom.innerHTML,
    nodeCount: dom.querySelectorAll('*').length,
    separatorCount: dom.querySelectorAll('img.ProseMirror-separator').length,
    panelWidth: Math.round(dom.getBoundingClientRect().width),
    panelInnerWidth: Math.round(dom.getBoundingClientRect().width - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight)),
    probes: (window.__r16.PROBES[${JSON.stringify(id)}] || []).map((probe) => ({
      probe: probe.label, editor: window.__r16.runProbe(dom, probe),
    })),
  });
})()`

const readState = () => `(() => {
  const dom = document.querySelector('.ProseMirror');
  const clip = dom ? (() => { const r = dom.getBoundingClientRect();
    return { x: r.x + window.scrollX, y: r.y + window.scrollY, width: r.width, height: Math.min(r.height, 6000) } })() : null;
  return JSON.stringify({ clip, url: location.href, loaded: !!dom,
    textLength: dom ? dom.textContent.replace(/\\s+/g, '').length : 0,
    guard: window.__writeGuard || null });
})()`

// ---------------------------------------------------------------------------

const meta = JSON.parse(readFileSync(join(SETDIR, 'r16.json'), 'utf8'))
const cases = meta.cases.map((item) => {
  const html = readFileSync(join(SETDIR, item.id + '.html'), 'utf8')
  const text = readFileSync(join(SETDIR, item.id + '.md'), 'utf8')
  // 「正文该有多少字」是「应用/编辑器有没有真的把这一条挂上去」的判据。
  // **三种模式的判据不同**：setContent / paste 进编辑器的是 HTML 产物；`--type` 敲进去的是 Markdown 源文。
  const expectedTextLength = html.replace(/<[^>]*>/g, '').replace(/&[a-z]+;|&#\d+;/gi, 'x').replace(/\s+/g, '').length
  const typedTextLength = text.replace(/\s+/g, '').length
  return {
    id: item.id, name: item.name, complaint: item.complaint, probe: item.probe,
    backendChars: item.backend?.chars ?? null,
    html, text, expectedTextLength, typedTextLength,
    expectTextLength: TYPE_MODE ? typedTextLength : expectedTextLength,
  }
})
console.log('用例数:', cases.length, '· 模式:',
  TYPE_MODE ? '键入 Markdown 源文（真 input 事件 + 真回车，不走剪贴板）'
    : PASTE_REAL ? '粘贴（真剪贴板 + 真 Ctrl+V）' : PASTE_MODE ? '粘贴（合成 ClipboardEvent）' : 'setContent（用户打开文章的真实路径）',
  WIDTH_MATCH ? '· 两栏同宽' : '')

const { client, version, close } = await launchBrowser({ port: 9351 })
console.log('浏览器:', version.Browser, '· 目标:', API)
const page = await openPage(client)
await page.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
await page.send('Page.addScriptToEvaluateOnNewDocument', { source: GUARD })

// 第 2 层：网络栈拦截（第 1 层没挡住才会到这里）
const blockedAtNetwork = []
client.listeners.set('Fetch.requestPaused', [(message) => {
  const { requestId, request } = message.params
  const verb = String(request.method || 'GET').toUpperCase()
  if (verb !== 'GET' && verb !== 'HEAD') {
    blockedAtNetwork.push(verb + ' ' + request.url)
    client.send('Fetch.failRequest', { requestId, errorReason: 'Aborted' }, page.sessionId).catch(() => {})
  } else {
    client.send('Fetch.continueRequest', { requestId }, page.sessionId).catch(() => {})
  }
}])
await page.send('Fetch.enable', { patterns: [{ urlPattern: '*/api/*', requestStage: 'Request' }] })

await page.navigate(API + '/login')
await page.evaluate(`localStorage.setItem('wechat_bot_token', ${JSON.stringify(token)})`)

const results = []
let mismatches = 0
if (PASTE_REAL) {
  await page.send('Browser.grantPermissions', { origin: API, permissions: ['clipboardReadWrite', 'clipboardSanitizedWrite'] })
}
/** 真 Ctrl+A（只改浏览器选择不算——ProseMirror 的内部 selection 不会跟着走，于是新内容会接在旧光标后）。 */
const selectAll = async () => {
  await page.evaluate(`(() => { document.querySelector('.ProseMirror').focus(); return true })()`)
  for (const type of ['keyDown', 'keyUp']) {
    await page.send('Input.dispatchKeyEvent', { type, modifiers: 2, key: 'a', code: 'KeyA', windowsVirtualKeyCode: 65, nativeVirtualKeyCode: 65 })
  }
  await sleep(120)
}
/**
 * 逐行键入 Markdown 源文：每行一次真实 `input` 事件（`Input.insertText`），行间一个真回车键。
 * **不走剪贴板**，所以它答的是「用户手打 `:::` 会怎样」。
 */
const typeSource = async (source) => {
  await selectAll()
  // 先按一次退格把选中的内容删掉再敲。**不是为了「看起来干净」**：ProseMirror 会从选区起点的
  // stored marks 继承格式，直接覆盖式键入会让第一段带上原文首段的颜色/底色/字距（实测确实如此），
  // 那是「在已有文章上改写」的痕迹，不是「手打 `:::` 会怎样」的答案。删空后 caret 在文档开头，
  // stored marks 为空，敲出来的才是纯文本。
  for (const type of ['keyDown', 'keyUp']) {
    await page.send('Input.dispatchKeyEvent', {
      type, key: 'Backspace', code: 'Backspace', windowsVirtualKeyCode: 8, nativeVirtualKeyCode: 8,
    })
  }
  await sleep(120)
  const lines = source.replace(/\n+$/, '').split('\n')
  for (const [index, line] of lines.entries()) {
    if (index) {
      for (const type of ['keyDown', 'keyUp']) {
        await page.send('Input.dispatchKeyEvent', {
          type, key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13, nativeVirtualKeyCode: 13, text: '\r',
        })
      }
    }
    if (line) await page.send('Input.insertText', { text: line })
  }
  return JSON.parse(await page.evaluate(`(() => {
    const dom = document.querySelector('.ProseMirror');
    const actual = dom.textContent.replace(/\\s+/g, '').length;
    return JSON.stringify({ ok: true, handled: true, textLength: actual,
      expectedTextLength: ${JSON.stringify(source.replace(/\s+/g, '').length)},
      replaced: actual === ${JSON.stringify(source.replace(/\s+/g, '').length)} });
  })()`))
}
for (const item of cases) {
  if (TYPE_MODE || PASTE_MODE || PASTE_REAL) {
    if (results.length === 0) {
      await page.navigate(`${API}/articles/${ARTICLE_ID}`)
      for (let attempt = 0; attempt < 60; attempt += 1) {
        if (JSON.parse(await page.evaluate(readState())).loaded) break
        await sleep(500)
      }
      await page.evaluate(INJECT_PROBES)
    }
    let pasted
    if (TYPE_MODE) {
      pasted = await typeSource(item.text)
    } else if (PASTE_REAL) {
      await selectAll()
      // 真剪贴板 + 真 Ctrl+V：用来排除「差异是我这个合成 ClipboardEvent 造成的」
      await page.evaluate(`(async () => {
        const html = ${JSON.stringify(item.html)};
        await navigator.clipboard.write([new ClipboardItem({
          'text/html': new Blob([html], { type: 'text/html' }),
          'text/plain': new Blob([${JSON.stringify(item.text)}], { type: 'text/plain' }),
        })]);
        return true })()`)
      for (const type of ['keyDown', 'keyUp']) {
        await page.send('Input.dispatchKeyEvent', {
          type, modifiers: 2, key: 'v', code: 'KeyV', windowsVirtualKeyCode: 86, nativeVirtualKeyCode: 86,
        })
      }
      pasted = JSON.parse(await page.evaluate(`(() => {
        const dom = document.querySelector('.ProseMirror');
        const ruler = document.createElement('div'); ruler.innerHTML = ${JSON.stringify(item.html)};
        const actual = dom.textContent.replace(/\\s+/g, '').length;
        return JSON.stringify({ ok: true, handled: true, textLength: actual,
          expectedTextLength: ruler.textContent.replace(/\\s+/g, '').length, replaced: actual === ruler.textContent.replace(/\\s+/g, '').length });
      })()`))
    } else {
      await selectAll()
      pasted = JSON.parse(await page.evaluate(pasteInto(item.html, item.text)))
    }
    await sleep(300)
  } else {
    // 把这一条塞进 localStorage，然后整页重开 —— 应用自己 GET 到它、自己 setContent
    await page.evaluate(`localStorage.setItem('__r16_case', ${JSON.stringify(item.html)}); true`)
    await page.navigate(`${API}/articles/${ARTICLE_ID}`)
    let ready = { loaded: false }
    for (let attempt = 0; attempt < 80; attempt += 1) {
      ready = JSON.parse(await page.evaluate(waitLoaded(item.expectedTextLength)))
      if (ready.loaded) break
      await sleep(250)
    }
    if (!ready.loaded) console.log(`  !! ${item.id} 没等到正文挂上去（当前 ${ready.textLength} 字，应为 ${item.expectedTextLength}）`)
    await page.evaluate(INJECT_PROBES)
  }
  if (WIDTH_MATCH) {
    await page.evaluate(`(() => {
      let tag = document.getElementById('__r16_widthmatch');
      if (!tag) { tag = document.createElement('style'); tag.id = '__r16_widthmatch';
        tag.textContent = '.paper{max-width:867px !important}'; document.head.append(tag); }
      return true })()`)
    await sleep(250)
  }
  await page.evaluate(`(async () => { await document.fonts.ready })()`)
  await sleep(400)

  const measured = JSON.parse(await page.evaluate(measureCase(item.id)))
  const now = JSON.parse(await page.evaluate(readState()))
  if (measured.textLength !== item.expectTextLength) mismatches += 1

  if (now.clip) {
    for (const [scale, suffix] of [[0.5, ''], [1, '.zoom']]) {
      const shot = await client.send('Page.captureScreenshot', {
        format: 'png', captureBeyondViewport: true, clip: { ...now.clip, scale },
      }, page.sessionId)
      writeFileSync(resolve(SHOTS, `${item.id}${suffix}.png`), Buffer.from(shot.data, 'base64'))
    }
  }

  results.push({
    id: item.id, name: item.name, complaint: item.complaint, probePoint: item.probe,
    backendChars: item.backendChars, expectedTextLength: item.expectedTextLength,
    typedTextLength: item.typedTextLength, expectTextLength: item.expectTextLength,
    editor: measured,
    shots: { full: `shots/r16-live/${MODE}/${item.id}.png`, zoom: `shots/r16-live/${MODE}/${item.id}.zoom.png` },
  })
  console.log(`  ${item.id}  正文 ${measured.textLength}/${item.expectTextLength} 字`
    + `${measured.textLength === item.expectTextLength ? '' : ' ❌'}`
    + ` · 元素 ${measured.nodeCount} 个 · 模块 ${(measured.html.match(/<section/g) || []).length} 个`
    + ` · 面板 ${measured.panelWidth}px（内 ${measured.panelInnerWidth}px）· 探针 ${measured.probes.length} 组`)
}

// 收尾前做一次**写拦截自证**：自动保存是「停笔 1800ms 后触发」，上面每条只停几百毫秒，
// 一路下来一次都没触发过——那等于三层保证一层都没被验证。这里多停 2.6s 逼它真的发一次 PUT。
console.log('\n等 2.6s 逼出一次自动保存，验证写拦截真的生效…')
await page.evaluate(`(() => { const dom = document.querySelector('.ProseMirror');
  const range = document.createRange(); range.setStart(dom, 0); range.collapse(true);
  const selection = window.getSelection(); selection.removeAllRanges(); selection.addRange(range);
  dom.focus(); return true })()`)
await page.send('Input.insertText', { text: 'x' })
await sleep(2600)
const selfTest = JSON.parse(await page.evaluate(readState())).guard
console.log('  自动保存/输入触发的写请求被挡下 ' + (selfTest?.blocked?.length ?? 0) + ' 次：'
  + (selfTest?.blocked || []).slice(0, 3).join(' | '))
console.log('  网络层独立计数：' + blockedAtNetwork.length + ' 次')

// 收尾：清掉注入，恢复到打开时的样子（只改内存），再核对库里没动过
await page.evaluate(`localStorage.removeItem('__r16_case'); true`)
await page.navigate(`${API}/articles/${ARTICLE_ID}`)
await sleep(2000)
const after = await readArticle()

const unchanged = after.revision === before.revision && after.updatedAt === before.updatedAt
console.log('\n库核对：改前 revision=' + before.revision + ' / 改后 revision=' + after.revision
  + ' · updatedAt ' + (unchanged ? '逐字未变 ✅' : '变了 ❌'))

writeFileSync(RESULT_FILE, JSON.stringify({
  browser: version.Browser, origin: API, articleId: ARTICLE_ID,
  mode: TYPE_MODE ? 'typed-markdown' : PASTE_REAL ? 'paste-real-clipboard' : PASTE_MODE ? 'paste-synthetic' : 'setContent',
  modeDir: MODE, widthMatch: WIDTH_MATCH,
  writeGuard: { appLayer: selfTest?.blocked || [], networkLayer: blockedAtNetwork, dbUnchanged: unchanged },
  db: { before, after },
  mismatches, results,
}, null, 1), 'utf8')
console.log('结果:', RESULT_FILE)

await page.close()
close()
if (!unchanged) { console.error('!! 文章被改动了，必须人工核查'); process.exit(1) }
if (mismatches) { console.error('!! 有 ' + mismatches + ' 条正文没挂对'); process.exit(2) }
