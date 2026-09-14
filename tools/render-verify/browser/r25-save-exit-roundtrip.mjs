/**
 * 第二十五轮 A · **保存出口的幂等性**（所见 = 所存 的直接判据）。
 *
 * 问题（用户真正关心的那句话）：「我在编辑器里看到的样子，保存之后还在不在？」
 *
 * 做法（三步，全在同一篇真实文章、同一个窗口宽度、同一台浏览器上）：
 *   ① 让编辑器进入某条路径的内容（`setContent` 灌产物 / 粘贴 / 手打源文）；
 *   ② 做一次**净零编辑**（在末段末尾敲一个字符再按退格，正文一字不差），
 *      等 1800ms 去抖把自动保存逼出来 —— 应用层拦截器把这次 `PUT` 的 body 原样留下，
 *      那份 `body.contentHtml` 就是**保存出口**（`editor.getHTML()` 的真实落点，不是推理）；
 *   ③ 把这份保存出口**原样灌回编辑器**（仍然走应用自己的 `setContent`：伪造那一次 GET 的返回，
 *      再整页导航 —— 这正是用户「保存后重新打开这篇文章」的路径），量**同一组可测量**，
 *      与 ②发生那一刻的**实时 DOM** 逐条比。
 *
 * 判据（先定死，不许为了让结论好看而放宽）：
 *   - 逐条**完全相同** → 保存出口幂等，所见 = 所存；
 *   - 有任何一条不同 → **逐条列出**，那是真缺陷。
 * 比较用的是第二十四轮那 11 条症状探针的**同一份源码**（`r24-probes.mjs`），
 * 数字方向、单位、取样元素全一致；不比「好不好看」，也不挑好量的可测量。
 *
 * ⚠️ **不写库**：三层拦截与第二十四轮同款（应用层 patch fetch/XHR/sendBeacon +
 * CDP `Fetch.failRequest` + 跑完回读 revision/updatedAt 逐字比对）。
 *
 * ⚠️ **第二十九轮 A 定性：判据属「甲类｜只比两侧一致」，对「打开时就一致地丢」型 bug 免疫。**
 *    「保存时实时 DOM」与「保存出口重灌后」出自**同一份前端**：段首空白若在 `setContent`
 *    第一次解析里就被吃掉，两侧一起没有，判据①照样报「11/11 完全相同、差异 0 条、exit 0」。
 *    实测（`r29_gate_audit`）：把整包前端换成第二十六轮**修复前**的 `target/probe/r26/before-dist`
 *    真跑一遍，判据① **11/11 全过、差异 0 条、exit 0**。
 *    这是**定位**决定的，不改判据（改了就不是在量「所见 = 所存」这个不变量了）；
 *    抓这类 bug 的是 `r26-leading-ws-effect.mjs` 判据① 与 `r28-roundtrip-gate.mjs` 判据③。
 *    另立 `--selftest`（离线，不重复判定逻辑）证明本支对**值级差异**确实会判红。
 *
 * 用法：
 *   node tools/render-verify/browser/r25-save-exit-roundtrip.mjs --inject all
 *   node tools/render-verify/browser/r25-save-exit-roundtrip.mjs --inject all --paste
 *   node tools/render-verify/browser/r25-save-exit-roundtrip.mjs --inject all --type
 *   node tools/render-verify/browser/r25-save-exit-roundtrip.mjs --selftest   # 反例自检，不开浏览器
 *   （`--inject all` = 把 11 条用例的产物拼成一篇正文，让 11 条探针都有锚点可量；
 *     只灌单条用例时其余探针返回 null，「完全相同」里会混进 null 对 null 的假覆盖。
 *     也可以给单条 id，如 `--inject r16-06-title-da01`，做单点复核。）
 * 可选：`--label <名>`（默认 `after`）、`--bundle <dir>`（整包换前端，用于跑「改前」那一份）。
 * 产物：target/probe/browser/r25_roundtrip_<mode>_<case>_<label>_result.json
 *       target/probe/browser/r25_<mode>_<case>_<label>_saveexit.html（保存出口原文，供离线比对）
 *       target/probe/browser/shots/r25-roundtrip/<mode>/<item>.{before,roundtrip}.png
 */
import { readFileSync, writeFileSync, mkdirSync, existsSync, readdirSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { resolve, join } from 'node:path'
import { launchBrowser, openPage } from './cdp.mjs'
import { OUT, BROWSER_OUT, ROOT, API } from '../paths.mjs'
import { PROBE_SRC } from './r24-probes.mjs'

const ARGS = process.argv.slice(2)
const ARTICLE_ID = Number(ARGS.find((item) => /^\d+$/.test(item)) || 38)
const INJECT = (() => {
  const at = ARGS.indexOf('--inject')
  return at >= 0 && ARGS[at + 1] ? ARGS[at + 1] : null
})()
const PASTE = ARGS.includes('--paste')
const TYPE = ARGS.includes('--type')
const MODE = TYPE ? 'type' : PASTE ? 'paste' : 'setcontent'
const LABEL = (() => {
  const at = ARGS.indexOf('--label')
  return at >= 0 && ARGS[at + 1] ? ARGS[at + 1] : 'after'
})()
const BUNDLE = (() => {
  const at = ARGS.indexOf('--bundle')
  return at >= 0 && ARGS[at + 1] ? resolve(ROOT, ARGS[at + 1]) : null
})()
if (!INJECT && !ARGS.includes('--selftest')) { console.error('必须给 --inject <caseId>（本支只量某一条用例的三条输入路径）'); process.exit(3) }

const SETDIR = resolve(OUT, 'r16')
const SHOTS = resolve(BROWSER_OUT, 'shots/r25-roundtrip', MODE)
const RESULT_FILE = resolve(BROWSER_OUT, `r25_roundtrip_${MODE}_${INJECT}_${LABEL}_result.json`)
const SAVE_EXIT_FILE = resolve(BROWSER_OUT, `r25_${MODE}_${INJECT}_${LABEL}_saveexit.html`)
mkdirSync(SHOTS, { recursive: true })
const sleep = (ms) => new Promise((done) => setTimeout(done, ms))
if (BUNDLE && !existsSync(join(BUNDLE, 'index.html'))) { console.error('--bundle 目录里没有 index.html：' + BUNDLE); process.exit(3) }

// ===========================================================================
// 判据的**唯一一把尺子**（纯函数，主流程与 `--selftest` 共用）。
//
// ⚠️ 第二十九轮 A：把这段从主流程里提出来，就是为了让 `--selftest` 能拿**存档离线**套同一套规则
//    ——这是「反例自检」的硬要求：自检必须走真判据，另写一套「看起来一样」的比较器不算数。
//
// ⚠️ 逐条表的**分母取自这一支自己量到的条数**，不挑「好量的」——不设容差、不筛样本，
//    差多少写多少。这样它才配得上「所见 = 所存」这个不变量的名字。
// ===========================================================================
const strip = (value) => JSON.parse(JSON.stringify(value))

/** 逐条比对两组探针结果（不做任何容差放宽）。返回差异列表。 */
function diffItems(liveAtSave, liveRoundTrip) {
  const differences = []
  for (const [index, item] of liveAtSave.items.entries()) {
    const other = liveRoundTrip.items[index]
    const a = strip(item.value), b = strip(other.value)
    if (JSON.stringify(a) !== JSON.stringify(b)) {
      const keys = new Set([...Object.keys(a || {}), ...Object.keys(b || {})])
      const perKey = []
      for (const key of keys) {
        if (JSON.stringify(a?.[key]) !== JSON.stringify(b?.[key])) perKey.push({ 项: key, 保存时: a?.[key] ?? null, 重灌后: b?.[key] ?? null })
      }
      differences.push({ id: item.id, name: item.name, complaint: item.complaint, errorA: item.error, errorB: other.error, 差异项: perKey })
    }
  }
  return differences
}

/** 顶层块几何（y / 高 / 首字符 x）逐块比对。段首空白被吃掉的话，首字符 x 会往左跳。 */
function diffBlocks(liveAtSave, liveRoundTrip) {
  const blockDiffs = []
  for (const [index, block] of liveAtSave.blocks.entries()) {
    const other = liveRoundTrip.blocks[index]
    if (JSON.stringify(block) !== JSON.stringify(other)) blockDiffs.push({ 第几块: index, 保存时: block, 重灌后: other })
  }
  return blockDiffs
}

// ---------------------------------------------------------------------------
// `--selftest`：**反例自检** —— 离线，不开浏览器、不连库、不打 API。
//
// 输入 = 第二十九轮在**修复前整包**上真跑出来的那份存档
// （`r25_roundtrip_setcontent_all_r29blindcheck_result.json`，bundle = r26/before-dist）。
// 它在判据①上判了什么，正是本支盲区的实据；这里同时要证明尺子另一头是灵的：
//   ① 自比                    → 差异必须 **0**（不误报）
//   ② 把「重灌后」改一个叶子  → 差异必须 **恰好 1 条**且 id 对得上（不瞎）
//   ③ 报出该存档的真实结论    → 这就是「甲类对『打开时就一致地丢』免疫」的实测数字
// ①② 任一条不符即 exit 1。
// ---------------------------------------------------------------------------
if (ARGS.includes('--selftest')) {
  const file = resolve(BROWSER_OUT, 'r25_roundtrip_setcontent_all_r29blindcheck_result.json')
  if (!existsSync(file)) { console.error('没有存档 ' + file + '（先跑 `--inject all --label r29blindcheck --bundle target/probe/r26/before-dist`）'); process.exit(3) }
  const payload = JSON.parse(readFileSync(file, 'utf8'))
  const { liveAtSave, liveRoundTrip, verdict } = payload
  if (!liveAtSave?.items || !liveRoundTrip?.items) { console.error('存档里没有 liveAtSave / liveRoundTrip 的逐条量测'); process.exit(3) }

  console.log('反例自检（存档）:', file)
  console.log('  bundle = ' + String(payload.bundle || '(未记)').split(/[\\/]/).pop() + '（第二十六轮修复前的整包前端）')

  const 自比 = diffItems(liveAtSave, liveRoundTrip)
  const 自比块 = diffBlocks(liveAtSave, liveRoundTrip)
  const 过自比 = 自比.length === 0 && 自比块.length === 0
  console.log('  ① 自比：逐条差异 ' + 自比.length + ' 条 · 顶层块差异 ' + 自比块.length + ' 块 → '
    + (过自比 ? '0（不误报）✅' : '**非 0**❌ 尺子自己会误报'))

  // 造坏版本：只把「重灌后」那一侧的一个叶子值 +1，其余一字不动。
  const 坏 = JSON.parse(JSON.stringify(liveRoundTrip))
  const 叶子 = (node, path = '') => {
    if (!node || typeof node !== 'object') return null
    for (const key of Object.keys(node)) {
      const value = node[key]
      const at = path ? `${path}.${key}` : key
      if (typeof value === 'number') return { 宿主: node, 键: key, at, 值: value }
      if (value && typeof value === 'object') { const hit = 叶子(value, at); if (hit) return hit }
    }
    return null
  }
  const 目标 = 坏.items.map((item) => ({ item, hit: 叶子(item.value) })).find((row) => row.hit)
  if (!目标) { console.error('  ② 未做：存档里找不到可改的数值叶子'); process.exit(1) }
  目标.hit.宿主[目标.hit.键] = 目标.hit.值 + 1
  const 改后 = diffItems(liveAtSave, 坏)
  const 过造坏 = 改后.length === 1 && 改后[0].id === 目标.item.id
  console.log('  ② 把「重灌后」的 ' + 目标.item.id + '.' + 目标.hit.at + ' 从 ' + 目标.hit.值
    + ' 改成 ' + (目标.hit.值 + 1) + '：差异 ' + 改后.length + ' 条（期望恰好 1 条，id=' + 目标.item.id + '）→ '
    + (过造坏 ? '判红 ✅' : '**没抓住**❌ 闸写松了'))

  console.log('  ③ 该存档在判据①上的真实结论：' + verdict.identical + ' / ' + verdict.compared
    + ' 条完全相同（真正量到数的 ' + verdict.coveredAtSave + ' 条）· 差异 ' + verdict.differences.length + ' 条')
  console.log('     → 修复前的整包前端上**照样判过**——这就是「甲类盲区」的实测实据，'
    + '抓这类 bug 的是 r26-leading-ws-effect 判据① 与 r28-roundtrip-gate 判据③。')

  const 全对 = 过自比 && 过造坏
  console.log(全对 ? '→ 反例自检通过：尺子不误报、对值级差异会判红；盲区是「两侧一起丢」（定位决定）。'
    : '→ 反例自检**不通过**：本支的判定与上面任一条不符，必须查。')
  process.exit(全对 ? 0 : 1)
}

const login = await (await fetch(API + '/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'Admin@123' }),
})).json()
if (!login.success) throw new Error('登录失败：' + JSON.stringify(login))
const token = login.data.token
const readArticle = async () => {
  const data = (await (await fetch(`${API}/api/articles/${ARTICLE_ID}`,
    { headers: { Authorization: `Bearer ${token}` } })).json()).data || {}
  return { revision: data.revision, updatedAt: data.updatedAt, contentLength: (data.contentHtml || '').length }
}
const before = await readArticle()
console.log(`目标文章 #${ARTICLE_ID} 改前：revision=${before.revision} updatedAt=${before.updatedAt} contentHtml=${before.contentLength} 字符`)

const caseHtml = (() => {
  if (INJECT !== 'all') return readFileSync(join(SETDIR, INJECT + '.html'), 'utf8')
  // `--inject all`：把 11 条用例的产物**原样拼**成一篇正文。
  // 这样做的理由：只灌一条用例时，另外 10 条探针在本篇里找不到锚点、返回 null，
  // 「11/11 完全相同」就变成了「1 条真测 + 10 条 null 对 null」——那是假覆盖。
  // 拼接不改任何一条用例的内容，只是让 11 条探针**都落在有东西可量**的正文上。
  return readdirSync(SETDIR).filter((name) => /^r16-\d\d-[a-z0-9-]+\.html$/.test(name)).sort()
    .map((name) => readFileSync(join(SETDIR, name), 'utf8')).join('\n')
})()
const caseMd = (() => {
  if (INJECT !== 'all') return readFileSync(join(SETDIR, INJECT + '.md'), 'utf8')
  return readdirSync(SETDIR).filter((name) => /^r16-\d\d-[a-z0-9-]+\.md$/.test(name)).sort()
    .map((name) => readFileSync(join(SETDIR, name), 'utf8')).join('\n\n')
})()
console.log(`用例 ${INJECT}：产物 ${caseHtml.length} 字符 / 源文 ${caseMd.length} 字符 · 路径 = ${MODE}`)

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
      // 保存出口：应用真正要 PUT 出去的正文（= editor.getHTML() 的落点）。
      window.__writeGuard.lastSave = { verb: verb, url: String(url), contentHtml: body.contentHtml || null };
      window.__writeGuard.blocked.push(verb + ' ' + url);
      return Promise.resolve(json(Object.assign({}, body, { updatedAt: new Date().toISOString() })));
    }
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

const readState = `(() => {
  const dom = document.querySelector('.ProseMirror');
  return JSON.stringify({
    loaded: !!dom,
    textLength: dom ? dom.textContent.replace(/\\s+/g, '').length : 0,
    sections: dom ? dom.querySelectorAll('section').length : 0,
    htmlChars: dom ? dom.innerHTML.length : 0,
  });
})()`
const measureNow = `(() => {
  const dom = document.querySelector('.ProseMirror');
  const style = getComputedStyle(dom);
  /** 每个顶层块的几何 + **首字符落点**：段首空白有没有被吃掉，看这个 x 就知道。 */
  const firstCharX = (el) => {
    const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
    let node;
    while ((node = walker.nextNode())) {
      const text = node.nodeValue || '';
      const at = text.search(/\\S/);
      if (at < 0) continue;
      const range = document.createRange();
      range.setStart(node, at); range.setEnd(node, at + 1);
      const r = range.getBoundingClientRect();
      return (r.width || r.height) ? Math.round(r.x * 100) / 100 : null;
    }
    return null;
  };
  const round = (value) => Math.round(value * 100) / 100;
  const domTop = dom.getBoundingClientRect().y;
  const blocks = [...dom.children].map((el) => {
    const r = el.getBoundingClientRect();
    // 用**相对编辑器顶边**的 y：绝对 y 里混着「保存前光标在末尾、重开后回到顶部」的滚动差，
    // 那是取景差异不是排版差异，留在判据里会把 100% 的行都误报成不同。
    return { tag: el.tagName.toLowerCase(), x: round(r.x), dy: round(r.y - domTop), h: round(r.height), 首字符x: firstCharX(el) };
  });
  return JSON.stringify({
    panelWidth: Math.round(dom.getBoundingClientRect().width * 100) / 100,
    panelInnerWidth: Math.round((dom.getBoundingClientRect().width
      - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight)) * 100) / 100,
    whiteSpace: style.whiteSpace,
    sectionCount: dom.querySelectorAll('section').length,
    textLength: dom.textContent.replace(/\\s+/g, '').length,
    // 逐字节判据：探针只量了 11 个点，两点之间还可能有别的东西变了。
    // 把整段 DOM 序列化带回来做哈希——「同一组探针全过」只是必要条件，不是充分条件。
    html: dom.innerHTML,
    blocks,
    items: window.__r24.measureAll(dom),
  });
})()`
const settle = `(async () => {
  await document.fonts.ready;
  await Promise.all([...document.images].map((img) => img.complete ? 0
    : new Promise((done) => { img.onload = done; img.onerror = done; setTimeout(done, 8000) })));
  return true })()`

const { client, version, close } = await launchBrowser({ port: 9357 })
console.log('浏览器:', version.Browser, '· 目标:', API, '· 前端:', BUNDLE || '(应用自带的 target/classes/static)')
const page = await openPage(client)
const consoleMessages = []
client.listeners.set('Runtime.consoleAPICalled', [(message) => {
  consoleMessages.push(message.params.type + ': '
    + (message.params.args || []).map((arg) => arg.value ?? arg.description ?? '').join(' ').slice(0, 300))
}])
client.listeners.set('Runtime.exceptionThrown', [(message) => {
  const detail = message.params.exceptionDetails || {}
  consoleMessages.push('exception: ' + String(detail.text || '') + ' '
    + String(detail.exception && detail.exception.description || '').slice(0, 300))
}])
await page.send('Runtime.enable')
await page.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
await page.send('Page.addScriptToEvaluateOnNewDocument', { source: GUARD })

const blockedAtNetwork = []
client.listeners.set('Fetch.requestPaused', [(message) => {
  const { requestId, request } = message.params
  const verb = String(request.method || 'GET').toUpperCase()
  if (verb !== 'GET' && verb !== 'HEAD') {
    blockedAtNetwork.push(verb + ' ' + request.url)
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

const base = (await (await fetch(`${API}/api/articles/${ARTICLE_ID}`,
  { headers: { Authorization: `Bearer ${token}` } })).json()).data
await page.navigate(API + '/login')
await page.evaluate(`localStorage.setItem('wechat_bot_token', ${JSON.stringify(token)});`
  + `localStorage.setItem('__r24_base', ${JSON.stringify(JSON.stringify(base))});`
  + `localStorage.setItem('__r24_case', ${JSON.stringify(caseHtml)}); true`)

/** 打开一次文章：应用会自己 GET → setContent(那份 contentHtml)。 */
const openArticle = async (expectText) => {
  await page.navigate(`${API}/articles/${ARTICLE_ID}`)
  let state = { loaded: false, textLength: 0 }
  for (let attempt = 0; attempt < 80; attempt += 1) {
    state = JSON.parse(await page.evaluate(readState))
    if (state.loaded && state.textLength >= expectText) break
    await sleep(250)
  }
  await page.evaluate(settle)
  await sleep(400)
  // 整页导航会把上一轮的 window.__r24 一起清掉，探针必须**重灌**——
  // 灌的是同一份 PROBE_SRC 源码，所以两次测量仍然同一把尺子。
  await page.evaluate(PROBE_SRC)
  return state
}

await openArticle(10)
console.log('编辑器就绪 · 正文', JSON.parse(await page.evaluate(readState)).textLength, '字（注入模式）')
const chunks = JSON.parse(await page.evaluate(`(() => JSON.stringify(
  performance.getEntriesByType('resource').map((entry) => entry.name)
    .filter((name) => /ArticleEditorView|\\/assets\\/index-/.test(name))))()`))
console.log('实际加载的编辑器 chunk:', chunks.map((name) => name.split('/').pop()).join(' | '))

/** 真 Ctrl+A（只改浏览器选择不算——ProseMirror 的内部 selection 不会跟着走）。 */
const selectAll = async () => {
  await page.evaluate(`(() => { document.querySelector('.ProseMirror').focus(); return true })()`)
  for (const type of ['keyDown', 'keyUp']) {
    await page.send('Input.dispatchKeyEvent', { type, modifiers: 2, key: 'a', code: 'KeyA', windowsVirtualKeyCode: 65, nativeVirtualKeyCode: 65 })
  }
  await sleep(120)
}

// —— 第二、三条路径：把内容「粘贴」或「手打」进去（第一条路径就是上面的 setContent）——
if (PASTE || TYPE) {
  if (TYPE) {
    await selectAll()
    for (const type of ['keyDown', 'keyUp']) {
      await page.send('Input.dispatchKeyEvent', {
        type, key: 'Backspace', code: 'Backspace', windowsVirtualKeyCode: 8, nativeVirtualKeyCode: 8,
      })
    }
    await sleep(150)
    const lines = caseMd.replace(/\n+$/, '').split('\n')
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
  } else {
    await selectAll()
    await page.evaluate(`(() => {
      const dom = document.querySelector('.ProseMirror');
      const html = ${JSON.stringify(caseHtml)};
      const ruler = document.createElement('div'); ruler.innerHTML = html;
      dom.focus();
      const transfer = new DataTransfer();
      transfer.setData('text/html', html);
      transfer.setData('text/plain', ruler.textContent);
      dom.dispatchEvent(new ClipboardEvent('paste', { clipboardData: transfer, bubbles: true, cancelable: true }));
      return true;
    })()`)
  }
  await sleep(400)
  const entered = JSON.parse(await page.evaluate(readState))
  console.log(`${TYPE ? '手打' : '粘贴'}后：正文 ${entered.textLength} 字 · 模块 ${entered.sections} 个`)
}

// —— ① 保存前的实时 DOM ——
const liveBefore = JSON.parse(await page.evaluate(measureNow))
console.log('① 实时 DOM（保存前）：内宽', liveBefore.panelInnerWidth, 'px · 模块', liveBefore.sectionCount, '个 · 正文', liveBefore.textLength, '字')

// —— 净零编辑：在**末段末尾**敲一个字符再退格，正文一字不差，只为逼出一次自动保存 ——
// 不选开头是有原因的：文档首块若是个块容器（如 changelog 的卡片 section），
// 在位置 0 打字会让 ProseMirror 凭空插一层合成段落，那是「编辑」而不是「净零」。
const caretPlaced = JSON.parse(await page.evaluate(`(() => {
  const dom = document.querySelector('.ProseMirror');
  const paragraphs = [...dom.querySelectorAll('p')].filter((el) => (el.textContent || '').trim().length > 0);
  const last = paragraphs[paragraphs.length - 1];
  if (!last) return JSON.stringify({ ok: false });
  dom.focus();
  const range = document.createRange();
  range.selectNodeContents(last); range.collapse(false);
  const selection = window.getSelection(); selection.removeAllRanges(); selection.addRange(range);
  return JSON.stringify({ ok: true, tail: (last.textContent || '').slice(-16) });
})()`))
console.log('净零编辑落点：末段「…' + caretPlaced.tail + '」')
await page.send('Input.insertText', { text: 'x' })
await sleep(120)
for (const type of ['keyDown', 'keyUp']) {
  await page.send('Input.dispatchKeyEvent', {
    type, key: 'Backspace', code: 'Backspace', windowsVirtualKeyCode: 8, nativeVirtualKeyCode: 8,
  })
}
console.log('等 2.6s 逼出一次自动保存（写请求会被三层拦下）…')
await sleep(2600)
const liveAtSave = JSON.parse(await page.evaluate(measureNow))
const guard = JSON.parse(await page.evaluate(`JSON.stringify(window.__writeGuard || null)`))
console.log('  自动保存被挡下 ' + (guard?.blocked?.length ?? 0) + ' 次：' + (guard?.blocked || []).slice(0, 3).join(' | '))
console.log('② 实时 DOM（保存发生那一刻）：模块', liveAtSave.sectionCount, '个 · 正文', liveAtSave.textLength, '字'
  + (liveAtSave.textLength === liveBefore.textLength && liveAtSave.sectionCount === liveBefore.sectionCount
    ? ' · 与①逐项相同（净零编辑成立）✅' : ' · ❌ 与①不同，净零编辑没做到'))

const saveExitHtml = guard?.lastSave?.contentHtml || null
if (!saveExitHtml) { console.error('!! 没拿到保存出口'); process.exit(2) }
writeFileSync(SAVE_EXIT_FILE, saveExitHtml, 'utf8')
console.log('保存出口：', saveExitHtml.length, '字符 · 模块',
  (saveExitHtml.match(/<section/g) || []).length, '个 →', SAVE_EXIT_FILE)

// 逐条截图（保存前 / 重灌后各一张，按条目裁）
const clipOf = (index) => `(() => {
  const dom = document.querySelector('.ProseMirror');
  const item = window.__r24.ITEMS[${index}];
  const hits = [...dom.querySelectorAll('*')].filter((el) => (el.textContent || '').includes(item.anchor));
  if (!hits.length) return null;
  const r = hits[0].getBoundingClientRect();
  return JSON.stringify({ x: r.x + window.scrollX, y: r.y + window.scrollY, width: r.width, height: Math.min(r.height, 2400) });
})()`
const shoot = async (suffix) => {
  for (const [index, item] of liveAtSave.items.entries()) {
    const clip = await page.evaluate(clipOf(index))
    if (!clip || clip === 'null') continue
    const shot = await client.send('Page.captureScreenshot', {
      format: 'png', captureBeyondViewport: true, clip: { ...JSON.parse(clip), scale: 0.5 },
    }, page.sessionId)
    writeFileSync(resolve(SHOTS, `${item.id}.${suffix}.png`), Buffer.from(shot.data, 'base64'))
  }
}
await shoot('before')

// —— ③ 把保存出口**原样灌回编辑器**（走应用自己的 setContent：伪造 GET 的返回 + 整页导航）——
await page.evaluate(`localStorage.setItem('__r24_case', ${JSON.stringify(saveExitHtml)}); true`)
const reopened = await openArticle(Math.max(10, Math.floor(liveAtSave.textLength * 0.98)))
console.log('③ 重灌后：正文', reopened.textLength, '字 · 模块', reopened.sections, '个 · 源码',
  liveAtSave.textLength === reopened.textLength ? '字数与②相同 ✅' : `❌ 字数不同（②${liveAtSave.textLength} vs ③${reopened.textLength}）`)
const liveRoundTrip = JSON.parse(await page.evaluate(measureNow))
await shoot('roundtrip')

/** 整段 DOM 的哈希（探针之间的空隙也要有判据）。 */
const digest = (text) => createHash('sha256').update(text || '', 'utf8').digest('hex').slice(0, 16)
/**
 * 把 `style="…"` 里的声明**按字典序重排**再比。
 * 理由：浏览器序列化 `style` 属性的顺序，和 `setContent` 再解析一次之后的顺序可以不同，
 * `display:flex;margin:0` 与 `margin:0;display:flex` 在 CSS 里是同一件事。
 * 这一步只吃掉「声明的书写顺序」，不吃掉任何一条声明本身——所以它不放松判据，
 * 只是把「语义相同、字节不同」的噪声去掉；去掉之后若还有差，那就是真差。
 */
const normalizeStyleOrder = (html) => html.replace(/style="([^"]*)"/g, (whole, value) => 'style="'
  + value.split(';').map((part) => part.trim()).filter(Boolean).sort().join(';') + '"')
const domAtSave = digest(liveAtSave.html)
const domAfterRoundTrip = digest(liveRoundTrip.html)
const domVsSaveExit = digest(saveExitHtml)
const normalizedIdentical = normalizeStyleOrder(liveAtSave.html) === normalizeStyleOrder(liveRoundTrip.html)
// 两份 DOM 都落盘（在 gitignored 的 target/ 里），哈希不等时能直接 diff，不必重跑。
const DOM_DIR = resolve(BROWSER_OUT, `r25_dom_${MODE}_${INJECT}_${LABEL}`)
mkdirSync(DOM_DIR, { recursive: true })
writeFileSync(resolve(DOM_DIR, 'at-save.html'), liveAtSave.html, 'utf8')
writeFileSync(resolve(DOM_DIR, 'after-roundtrip.html'), liveRoundTrip.html, 'utf8')
console.log('\n整段 DOM 指纹（同一把尺子的补充判据，不进 11 条探针的结论）：')
console.log('  保存那一刻编辑器 DOM   : ' + domAtSave + ' · ' + liveAtSave.html.length + ' 字符')
console.log('  保存出口（拦截到的那份）: ' + domVsSaveExit + ' · ' + saveExitHtml.length + ' 字符'
  + (domAtSave === domVsSaveExit ? ' · 与编辑器 DOM 逐字节相同 ✅' : ' · 与编辑器 DOM 不同（getHTML 序列化差异，见下）'))
console.log('  重灌后的编辑器 DOM     : ' + domAfterRoundTrip + ' · ' + liveRoundTrip.html.length + ' 字符'
  + (domAtSave === domAfterRoundTrip ? ' · 与保存那一刻逐字节相同 ✅' : ' · 字节不同'))
console.log('  重排 style 声明序后     : ' + (normalizedIdentical
  ? '逐字节相同 ✅（唯一的字节差是 `style` 里声明的书写顺序，CSS 语义等价）'
  : '❌ 仍有实质差异，必须逐条查'))

// 几何判据：顶层块的 y/高 与**首字符落点 x**。段首空白被吃掉的话，这个 x 会往左跳。
const blockDiffs = diffBlocks(liveAtSave, liveRoundTrip)
console.log('  顶层块几何（.ProseMirror 的 ' + liveAtSave.blocks.length + ' 个直接子元素，含首字符 x）：'
  + (blockDiffs.length ? '❌ ' + blockDiffs.length + ' 块不同' : '逐块相同 ✅')
  + ' · .ProseMirror white-space = ' + liveAtSave.whiteSpace)
for (const diff of blockDiffs.slice(0, 12)) {
  console.log('       第 ' + diff.第几块 + ' 块 ' + diff.保存时.tag
    + '：保存时 ' + JSON.stringify(diff.保存时) + ' → 重灌后 ' + JSON.stringify(diff.重灌后))
}
if (blockDiffs.length > 12) console.log('       …其余 ' + (blockDiffs.length - 12) + ' 块见结果 JSON')

// —— 逐条比对（同一组探针、同一把尺子；不做任何容差放宽，差多少写多少）——
const differences = diffItems(liveAtSave, liveRoundTrip)
const sameCount = liveAtSave.items.length - differences.length
// 覆盖度必须一起报：探针返回 null 表示这一条在本篇正文里找不到锚点，
// 「null 对 null」不算测得，别让它混进「完全相同」里充数。
const covered = (side) => side.items.filter((item) => item.value !== null && item.error === null).length
console.log('\n逐条比对（保存时实时 DOM vs 保存出口重灌后）：' + sameCount + ' / ' + liveAtSave.items.length + ' 条完全相同'
  + `（其中真正量到数的：保存时 ${covered(liveAtSave)} 条 / 重灌后 ${covered(liveRoundTrip)} 条）`)
for (const diff of differences) {
  console.log('  ❌ ' + diff.id + '（' + diff.name + ' · 用户原话「' + diff.complaint + '」）')
  for (const key of diff.差异项) console.log('       ' + key.项 + '：保存时 ' + JSON.stringify(key.保存时) + ' → 重灌后 ' + JSON.stringify(key.重灌后))
}

await page.evaluate(`localStorage.removeItem('__r24_case'); true`)
const after = await readArticle()
const unchanged = after.revision === before.revision && after.updatedAt === before.updatedAt
console.log('库核对：改前 revision=' + before.revision + ' / 改后 revision=' + after.revision
  + ' · updatedAt ' + (unchanged ? '逐字未变 ✅' : '变了 ❌'))

writeFileSync(RESULT_FILE, JSON.stringify({
  browser: version.Browser, origin: API, articleId: ARTICLE_ID, label: LABEL, mode: MODE,
  bundle: BUNDLE || null, case: INJECT,
  writeGuard: { appLayer: guard?.blocked || [], networkLayer: blockedAtNetwork, dbUnchanged: unchanged },
  db: { before, after },
  saveExit: { chars: saveExitHtml.length, sections: (saveExitHtml.match(/<section/g) || []).length },
  domDigest: {
    atSave: domAtSave, afterRoundTrip: domAfterRoundTrip, saveExit: domVsSaveExit,
    htmlCharsAtSave: liveAtSave.html.length, htmlCharsAfterRoundTrip: liveRoundTrip.html.length,
    identical: domAtSave === domAfterRoundTrip,
    normalizedIdentical,
    saveExitMatchesEditor: domAtSave === domVsSaveExit,
  },
  // 原始 innerHTML 不落盘（两份各 30KB 会把结果文件撑成日志），只留哈希与长度。
  liveBefore: { ...liveBefore, html: undefined },
  liveAtSave: { ...liveAtSave, html: undefined },
  liveRoundTrip: { ...liveRoundTrip, html: undefined },
  blockDiffs,
  verdict: {
    compared: liveAtSave.items.length, identical: sameCount,
    coveredAtSave: covered(liveAtSave), coveredAfterRoundTrip: covered(liveRoundTrip),
    differences,
  },
}, null, 1), 'utf8')
console.log('结果:', RESULT_FILE)

await page.close()
close()
if (!unchanged) { console.error('!! 文章被改动了，必须人工核查'); process.exit(1) }
if (differences.length) process.exit(4)
