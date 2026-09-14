/**
 * 第二十五轮 A 的**修复前评估**：把 `setContent` 的 `parseOptions.preserveWhitespace` 打开会不会有副作用。
 *
 * 起因：`r25-whitespace-probe.mjs` 证出「段首的**半角空格 / 制表符**在第一次解析时就被吃掉」
 * （`&nbsp;`、零宽空格、全角空格不受影响）。Tiptap v3 的 `setContent` 默认 `parseOptions = {}`，
 * 于是走的是「塌缩空白」的解析；一句话的候选修法就是加载时显式传
 * `{ parseOptions: { preserveWhitespace: 'full' } }`。
 *
 * 但 `preserveWhitespace: 'full'` 是全局性的：产物 HTML 是**带换行和缩进**的，
 * 打开之后标签之间的换行也可能被当成文本留下 —— 那就不是修一个空格的事，是动整篇排版。
 * 本支就量这一件事：**同一篇正文，两种 parseOptions 下解析出来的 DOM 差多少**。
 *
 * 判据（先定死）：
 *   - 两种解析下 `section` 层数、可见字数、逐块几何**全部相同** ⇒ 打开 preserveWhitespace 对产物正文无影响，可以改；
 *   - 有任何一项不同 ⇒ 不能直接改，风险写进文档交用户拍板。
 *
 * 用法：node tools/render-verify/browser/r25-preservewhitespace-trial.mjs
 * 产物：target/probe/browser/r25_preservewhitespace_trial.json（只读接口 + 只在页面内存里改，不保存、不写库）
 */
import { readFileSync, writeFileSync, readdirSync } from 'node:fs'
import { resolve, join } from 'node:path'
import { launchBrowser, openPage } from './cdp.mjs'
import { OUT, BROWSER_OUT, API } from '../paths.mjs'

const ARTICLE_ID = 38
const sleep = (ms) => new Promise((done) => setTimeout(done, ms))
const SETDIR = resolve(OUT, 'r16')

const corpus = readdirSync(SETDIR).filter((name) => /^r16-\d\d-[a-z0-9-]+\.html$/.test(name)).sort()
  .map((name) => readFileSync(join(SETDIR, name), 'utf8')).join('\n')
// 再加一段「段首空格」的样本，否则这次试验量不到它要修的那个东西。
const probeDoc = corpus + '\n<p>  LEADING-SPACES</p><p>\tLEADING-TAB</p>'

const GUARD = `(() => {
  const json = (data) => new Response(JSON.stringify({ success: true, data }),
    { status: 200, headers: { 'Content-Type': 'application/json' } });
  const rawFetch = window.fetch;
  const ARTICLE_PATH = /^\\/api\\/articles\\/${ARTICLE_ID}(\\?|$)/;
  window.__writeGuard = { blocked: [] };
  window.fetch = function (input, init) {
    let url = '', verb = 'GET';
    try {
      url = typeof input === 'string' ? input : (input && input.url) || '';
      verb = String((init && init.method) || (input && input.method) || 'GET').toUpperCase();
    } catch (error) { /* 放行 */ }
    if (verb !== 'GET' && verb !== 'HEAD' && /\\/api\\//.test(url)) {
      window.__writeGuard.blocked.push(verb + ' ' + url);
      return Promise.resolve(json({}));
    }
    const injected = localStorage.getItem('__pw_case');
    if (injected && ARTICLE_PATH.test(String(url))) {
      const base = JSON.parse(localStorage.getItem('__pw_base') || '{}');
      return Promise.resolve(json(Object.assign({}, base, { contentHtml: injected })));
    }
    return rawFetch.apply(this, arguments);
  };
  return 'installed';
})()`

/**
 * 找那个活着跑着的编辑器实例。
 * Tiptap 把 Editor 直接挂在内容 DOM 上（`document.querySelector('.ProseMirror').editor`），
 * 这是它自己的约定，比从 Vue 组件树里翻 setupState 稳。
 */
const FIND_EDITOR = `(() => {
  const dom = document.querySelector('.ProseMirror');
  const editor = dom && dom.editor;
  if (!editor || !editor.commands || !editor.schema) return 'not-found';
  window.__editor = editor;
  return 'found:.ProseMirror.editor';
})()`

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
    leadingSpaceParagraphs: [...dom.querySelectorAll('p')]
      .filter((el) => /^[ \\t]/.test(el.textContent || '')).length,
    段首空格样本: [...dom.querySelectorAll('p')]
      .filter((el) => /LEADING-(SPACES|TAB)/.test(el.textContent || ''))
      .map((el) => JSON.stringify((el.textContent || '').slice(0, 20))),
    blocks: [...dom.children].map((el) => {
      const r = el.getBoundingClientRect();
      return { tag: el.tagName.toLowerCase(), dy: round(r.y - domTop), h: round(r.height), 首字符x: firstCharX(el) };
    }),
  });
})()`

const login = await (await fetch(API + '/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'Admin@123' }),
})).json()
const token = login.data.token
const base = (await (await fetch(`${API}/api/articles/${ARTICLE_ID}`,
  { headers: { Authorization: `Bearer ${token}` } })).json()).data
const before = { revision: base.revision, updatedAt: base.updatedAt }

const { client, version, close } = await launchBrowser({ port: 9359 })
const page = await openPage(client)
await page.send('Runtime.enable')
await page.send('Page.addScriptToEvaluateOnNewDocument', { source: GUARD })
client.listeners.set('Fetch.requestPaused', [(message) => {
  const { requestId, request } = message.params
  if (String(request.method || 'GET').toUpperCase() !== 'GET') {
    client.send('Fetch.failRequest', { requestId, errorReason: 'Aborted' }, page.sessionId).catch(() => {})
    return
  }
  client.send('Fetch.continueRequest', { requestId }, page.sessionId).catch(() => {})
}])
await page.send('Fetch.enable', { patterns: [{ urlPattern: '*://*/*', requestStage: 'Request' }] })

await page.navigate(API + '/login')
await page.evaluate(`localStorage.setItem('wechat_bot_token', ${JSON.stringify(token)});`
  + `localStorage.setItem('__pw_base', ${JSON.stringify(JSON.stringify(base))});`
  + `localStorage.setItem('__pw_case', ${JSON.stringify(probeDoc)}); true`)
await page.navigate(`${API}/articles/${ARTICLE_ID}`)
for (let attempt = 0; attempt < 80; attempt += 1) {
  const ready = await page.evaluate(`!!document.querySelector('.ProseMirror')`)
  if (ready === true) break
  await sleep(250)
}
await page.evaluate(`document.fonts.ready`)
await sleep(500)

const editorHandle = await page.evaluate(FIND_EDITOR)
console.log('编辑器实例：', editorHandle)
if (!String(editorHandle).startsWith('found:')) {
  console.error('!! 拿不到编辑器实例，本支无法继续（不猜、不绕）')
  await page.close(); close(); process.exit(2)
}

const baseline = JSON.parse(await page.evaluate(MEASURE))
console.log('\n基线（应用本次加载用的默认 parseOptions）：section ' + baseline.sections
  + ' · 可见字 ' + baseline.textLength + ' · DOM ' + baseline.htmlChars + ' 字符 · 段首带空白的段落 '
  + baseline.leadingSpaceParagraphs + ' 个 · 段首空格样本 ' + JSON.stringify(baseline.段首空格样本))
console.log('  顶层块 ' + baseline.blocks.length + ' 个')

const runWith = async (label, options) => {
  const handle = await page.evaluate(`(() => {
    window.__editor.commands.setContent(${JSON.stringify(probeDoc)}, ${JSON.stringify(options)});
    return 'ok';
  })()`)
  if (handle !== 'ok') throw new Error(label + ' 执行失败')
  await page.evaluate(`document.fonts.ready`)
  await sleep(700)
  return JSON.parse(await page.evaluate(MEASURE))
}

const withDefault = await runWith('默认 parseOptions', {})
console.log('\n[甲] setContent(正文, {}) —— 应用现在的行为：section ' + withDefault.sections
  + ' · 可见字 ' + withDefault.textLength + ' · DOM ' + withDefault.htmlChars + ' 字符 · 段首带空白的段落 '
  + withDefault.leadingSpaceParagraphs + ' 个 · 段首空格样本 ' + JSON.stringify(withDefault.段首空格样本))

const withFull = await runWith('preserveWhitespace=full', { parseOptions: { preserveWhitespace: 'full' } })
console.log('[乙] setContent(正文, {parseOptions:{preserveWhitespace:\'full\'}})：section ' + withFull.sections
  + ' · 可见字 ' + withFull.textLength + ' · DOM ' + withFull.htmlChars + ' 字符 · 段首带空白的段落 '
  + withFull.leadingSpaceParagraphs + ' 个 · 段首空格样本 ' + JSON.stringify(withFull.段首空格样本))

const sameBlocks = JSON.stringify(withDefault.blocks) === JSON.stringify(withFull.blocks)
const verdict = {
  sectionsSame: withDefault.sections === withFull.sections,
  textSame: withDefault.textLength === withFull.textLength,
  blocksSame: sameBlocks,
  fixesLeadingSpace: withFull.leadingSpaceParagraphs > withDefault.leadingSpaceParagraphs,
}
console.log('\n判据：section ' + (verdict.sectionsSame ? '相同 ✅' : '不同 ❌')
  + ' · 可见字 ' + (verdict.textSame ? '相同 ✅' : '不同 ❌')
  + ' · 逐块几何 ' + (verdict.blocksSame ? '相同 ✅' : '不同 ❌')
  + ' · 段首空格 ' + (verdict.fixesLeadingSpace ? '被救回来了 ✅' : '没变化'))
if (!sameBlocks) {
  const n = Math.max(withDefault.blocks.length, withFull.blocks.length)
  console.log('  块级差异（前 12 处）：')
  let shown = 0
  for (let i = 0; i < n && shown < 12; i += 1) {
    const a = JSON.stringify(withDefault.blocks[i] || null)
    const b = JSON.stringify(withFull.blocks[i] || null)
    if (a === b) continue
    console.log('   [' + i + '] 甲 ' + a + '\n        乙 ' + b); shown += 1
  }
}

writeFileSync(resolve(BROWSER_OUT, 'r25_preservewhitespace_trial.json'), JSON.stringify({
  browser: version.Browser, corpusChars: probeDoc.length, editorHandle, verdict,
  default: withDefault, full: withFull,
}, null, 1), 'utf8')

const after = (await (await fetch(`${API}/api/articles/${ARTICLE_ID}`,
  { headers: { Authorization: `Bearer ${token}` } })).json()).data
console.log('\n库核对：改前 revision=' + before.revision + ' / 改后 revision=' + after.revision
  + ' · updatedAt ' + (after.updatedAt === before.updatedAt ? '逐字未变 ✅' : '变了 ❌')
  + '（本支只在页面内存里 setContent，从未触发保存）')
await page.close()
close()
