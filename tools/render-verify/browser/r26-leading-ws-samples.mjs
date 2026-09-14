/**
 * 第二十六轮 A · 窄修法的**第一条判据**：79 套最小样例的块结构 / 几何逐条不变。
 *
 * 背景：段首的半角空格 / 制表符会在 `setContent` 的**第一次解析**时被吃掉（第二十五轮 §3.26①）。
 * 候选窄修法 = 「打开正文时，把**块首**那一小段空白换成不会塌缩的等价字符」。
 * 这个修法一旦生效，所有样例的正文都会多走一遍转换函数——所以必须先证明：
 * **在 79 套最小样例上，它一个字节都不改。**
 *
 * 判据（先立后测，不许事后调）：
 *   对 `component_matrix.json` 里的每一个 id，
 *   用**应用自己的加载路径**（伪造 `GET /api/articles/<id>` 的返回 → 整页导航 → 应用调
 *   `editor.commands.setContent(...)`）单独打开该样例，量：
 *     顶层块列表（tag / 相对 dy / 高 / 首字符 x）、`<section>` 层数、可见字数、DOM 字符数。
 *   **两份（改前 bundle / 改后 bundle）逐 id 逐项相同 ⇒ 判据满足；**
 *   有任何一处不同 ⇒ 逐条列出，并说明是修法引起的还是别的原因。
 *
 * 注意「单独打开」是有意的：79 份产物拼成一篇文章再量，拼接处的换行会自己造出额外的块，
 * 那量的是拼接方式、不是修法（第二十五轮的 `preserveWhitespace` 试验就踩过这个坑）。
 *
 * ⚠️ **第二十九轮 A 定性：本支属「甲类｜只比两侧一致」，对「改前改后一致地错」零效力。**
 *    它的用途本来就是**不变量**——证明窄修法不改动 79 个样例的可观测行为，这一点它做得到
 *    （`--compare` 喂一份被改动过的量测会如实判红，见 `r29_gate_audit` 第 9 行）。
 *    但「改前本来就错」这一格它管不了：两份都是错的、逐条相同，它照样判「一致」。
 *    那一格由 `r26-leading-ws-effect.mjs`（判据①）与 `r28-roundtrip-gate.mjs`（判据③）负责。
 *
 * 用法：
 *   node tools/render-verify/browser/r26-leading-ws-samples.mjs --label before --bundle target/probe/r26/before-dist
 *   node tools/render-verify/browser/r26-leading-ws-samples.mjs --label after
 *   node tools/render-verify/browser/r26-leading-ws-samples.mjs --compare before after    # 纯离线比对，不开浏览器
 * 产物：target/probe/browser/r26_samples_<label>.json
 */
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs'
import { resolve, join } from 'node:path'
import { launchBrowser, openPage } from './cdp.mjs'
import { OUT, BROWSER_OUT, ROOT, API } from '../paths.mjs'

const ARGS = process.argv.slice(2)
const argOf = (name, fallback = null) => {
  const at = ARGS.indexOf(name)
  return at >= 0 && ARGS[at + 1] ? ARGS[at + 1] : fallback
}
const LABEL = argOf('--label', 'after')
const BUNDLE = argOf('--bundle') ? resolve(ROOT, argOf('--bundle')) : null
const PORT = Number(argOf('--port', '9360'))
const ARTICLE_ID = 38
const SAMPLE_DIR = resolve(OUT, 'components')
const MATRIX = resolve(OUT, 'component_matrix.json')

const samples = (() => {
  const matrix = JSON.parse(readFileSync(MATRIX, 'utf8'))
  const rows = matrix.rows || matrix
  return rows.map((row) => ({
    id: row.id,
    html: readFileSync(join(SAMPLE_DIR, row.id + '.html'), 'utf8'),
  }))
})()

// —— `--compare`：纯离线比对两份产物，不开浏览器 ——
if (ARGS.includes('--compare')) {
  const [a, b] = [argOf('--compare'), ARGS[ARGS.indexOf('--compare') + 2]]
  const load = (name) => JSON.parse(readFileSync(resolve(BROWSER_OUT, `r26_samples_${name}.json`), 'utf8'))
  const left = load(a), right = load(b)
  const diffs = []
  for (const id of Object.keys(left.samples)) {
    const x = left.samples[id], y = right.samples[id]
    if (!y) { diffs.push({ id, 说明: '改后那份没量到这一条' }); continue }
    for (const key of ['sections', 'textLength', 'htmlChars']) {
      if (x[key] !== y[key]) diffs.push({ id, 项: key, 改前: x[key], 改后: y[key] })
    }
    if (JSON.stringify(x.blocks) !== JSON.stringify(y.blocks)) {
      const at = []
      const n = Math.max(x.blocks.length, y.blocks.length)
      for (let i = 0; i < n; i += 1) {
        const p = JSON.stringify(x.blocks[i] ?? null), q = JSON.stringify(y.blocks[i] ?? null)
        if (p !== q) at.push({ 第几块: i, 改前: JSON.parse(p), 改后: JSON.parse(q) })
      }
      diffs.push({ id, 项: 'blocks', 差异块: at })
    }
  }
  console.log(`79 套样例结构比对：${a}（改前） vs ${b}（改后）`)
  console.log(`  样例数 ${Object.keys(left.samples).length} / ${Object.keys(right.samples).length}`)
  console.log(`  逐条不同：${diffs.length} 条`)
  for (const diff of diffs.slice(0, 20)) console.log('  ❌ ' + JSON.stringify(diff).slice(0, 400))
  writeFileSync(resolve(BROWSER_OUT, `r26_samples_${a}_vs_${b}.json`), JSON.stringify({
    left: a, right: b, sampleCount: Object.keys(left.samples).length, differences: diffs,
  }, null, 1), 'utf8')
  process.exit(diffs.length ? 4 : 0)
}

if (!existsSync(MATRIX)) throw new Error('没有 ' + MATRIX)
if (BUNDLE && !existsSync(join(BUNDLE, 'index.html'))) { console.error('--bundle 目录里没有 index.html：' + BUNDLE); process.exit(3) }
const sleep = (ms) => new Promise((done) => setTimeout(done, ms))
mkdirSync(BROWSER_OUT, { recursive: true })

const login = await (await fetch(API + '/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'Admin@123' }),
})).json()
if (!login.success) throw new Error('登录失败：' + JSON.stringify(login))
const token = login.data.token
const base = (await (await fetch(`${API}/api/articles/${ARTICLE_ID}`,
  { headers: { Authorization: `Bearer ${token}` } })).json()).data
const dbBefore = { revision: base.revision, updatedAt: base.updatedAt }

/** 与第二十五轮同款的三层写保护：本支**只读**，一次写请求都不许真的出去。 */
const GUARD = `(() => {
  window.__writeGuard = { blocked: [] };
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
      window.__writeGuard.blocked.push(verb + ' ' + url);
      return Promise.resolve(json({}));
    }
    const injected = sessionStorage.getItem('__r26_case');
    if (injected && ARTICLE_PATH.test(String(url))) {
      const stored = JSON.parse(sessionStorage.getItem('__r26_base') || '{}');
      return Promise.resolve(json(Object.assign({}, stored, { contentHtml: injected })));
    }
    return rawFetch.apply(this, arguments);
  };
  return 'installed';
})()`

const readState = `(() => {
  const dom = document.querySelector('.ProseMirror');
  return JSON.stringify({
    loaded: !!dom,
    textLength: dom ? dom.textContent.replace(/\\s+/g, '').length : 0,
  });
})()`

/**
 * 与第二十五轮 `r25-preservewhitespace-trial.mjs` 的 MEASURE 保持同一把尺子
 * （同样的 `dy` 相对基准、同样的首字符 x 取法），否则两轮的数字没法放在一起读。
 */
const MEASURE = `(() => {
  const dom = document.querySelector('.ProseMirror');
  const round = (value) => Math.round(value * 100) / 100;
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
      return (r.width || r.height) ? round(r.x) : null;
    }
    return null;
  };
  const domTop = dom.getBoundingClientRect().y;
  return JSON.stringify({
    sections: dom.querySelectorAll('section').length,
    textLength: dom.textContent.replace(/\\s+/g, '').length,
    htmlChars: dom.innerHTML.length,
    blocks: [...dom.children].map((el) => {
      const r = el.getBoundingClientRect();
      return { tag: el.tagName.toLowerCase(), dy: round(r.y - domTop), h: round(r.height), 首字符x: firstCharX(el) };
    }),
  });
})()`

const { client, version, close } = await launchBrowser({ port: PORT })
console.log('浏览器:', version.Browser, '· 目标:', API, '· 前端:', BUNDLE || '(应用自带的 target/classes/static)')
console.log('样例:', samples.length, '条 · 标签:', LABEL)
const page = await openPage(client)
const errors = []
client.listeners.set('Runtime.exceptionThrown', [(message) => {
  errors.push(String(message.params.exceptionDetails?.text || ''))
}])
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
await page.evaluate(`sessionStorage.setItem('__r26_base', ${JSON.stringify(JSON.stringify(base))}); true`)
const chunks = []
await page.navigate(`${API}/articles/${ARTICLE_ID}`)

const results = {}
for (const [index, sample] of samples.entries()) {
  await page.evaluate(`sessionStorage.setItem('__r26_case', ${JSON.stringify(sample.html)}); true`)
  await page.navigate(`${API}/articles/${ARTICLE_ID}`)
  let state = { loaded: false, textLength: 0 }
  for (let attempt = 0; attempt < 80; attempt += 1) {
    state = JSON.parse(await page.evaluate(readState))
    if (state.loaded && state.textLength > 0) break
    await sleep(120)
  }
  await page.evaluate(`document.fonts.ready`)
  await sleep(160)
  results[sample.id] = JSON.parse(await page.evaluate(MEASURE))
  if (!chunks.length) {
    chunks.push(...JSON.parse(await page.evaluate(`(() => JSON.stringify(
      performance.getEntriesByType('resource').map((entry) => entry.name)
        .filter((name) => /ArticleEditorView/.test(name))))()`)))
  }
  if ((index + 1) % 10 === 0 || index === samples.length - 1) {
    console.log(`  ${index + 1}/${samples.length} …`)
  }
}

console.log('实际加载的编辑器 chunk:', chunks.map((name) => name.split('/').pop()).join(' | '))
const file = resolve(BROWSER_OUT, `r26_samples_${LABEL}.json`)
writeFileSync(file, JSON.stringify({
  browser: version.Browser, origin: API, articleId: ARTICLE_ID, label: LABEL,
  bundle: BUNDLE || null, chunk: chunks.map((name) => name.split('/').pop()),
  dbBefore, blockedWrites: errors.length,
  sampleCount: samples.length, samples: results,
}, null, 1), 'utf8')

const empty = Object.entries(results).filter(([, row]) => row.blocks.length === 0)
console.log('没量到块结构的样例:', empty.length ? empty.map(([id]) => id).join('、') : '无')
const dbAfter = (await (await fetch(`${API}/api/articles/${ARTICLE_ID}`,
  { headers: { Authorization: `Bearer ${token}` } })).json()).data
console.log('库核对：改前 revision=' + dbBefore.revision + ' / 改后 revision=' + dbAfter.revision
  + ' · updatedAt ' + (dbAfter.updatedAt === dbBefore.updatedAt ? '逐字未变 ✅' : '变了 ❌'))
console.log('产物:', file)
await page.close()
close()
if (dbAfter.updatedAt !== dbBefore.updatedAt) process.exit(1)
