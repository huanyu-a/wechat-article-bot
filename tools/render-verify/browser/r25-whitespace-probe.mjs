/**
 * 第二十五轮 A 的**收窄定位**：保存出口重灌后，到底哪一类空白被吃掉了、哪一类活下来。
 *
 * 背景：`--inject all --type` 那一次重灌比对里，11 条症状探针全过，
 * 但逐块几何抓到了 3 处不同 —— 段首两个空格在「重新打开」时消失（首字符 x 204.73 → 198）。
 * 本支把这件事收窄成一个可判定的问题：**是不是所有空白都丢，还是只丢某一种写法**。
 *
 * 做法：灌一篇由「只有空白写法不同」的段落组成的正文，
 * 读回编辑器 DOM 里每一块的文本（JSON.stringify 打出来，空格看得见），
 * 再逼出一次保存、把保存出口**原样**灌回去，同样读一次，两边逐块对照。
 *
 * 判据：某一种写法两边不同 → 这种写法在「重新打开」时会丢；
 * 两边相同 → 这种写法安全（也是可用的替代写法）。
 *
 * 用法：node tools/render-verify/browser/r25-whitespace-probe.mjs
 * 产物：stdout + target/probe/browser/r25_whitespace_probe.json（全程只读接口，不写库）
 */
import { writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { launchBrowser, openPage } from './cdp.mjs'
import { BROWSER_OUT, API, login } from '../paths.mjs'

const ARTICLE_ID = 38
const sleep = (ms) => new Promise((done) => setTimeout(done, ms))

/**
 * 每一种写法只差「空白写在哪、写成什么字符」，其余一模一样。
 * `JSON.stringify` 打出来时空格 / 制表符 / 零宽字符都看得见。
 */
const doc = [
  '<p>  A1-plain2</p>',                       // 段首 2 个半角空格 ← 上一轮 `--type` 路径上丢的就是这一种
  '<p>&nbsp;&nbsp;A2-nbsp2</p>',              // 段首 2 个不换行空格
  '<p>​​A3-zwsp2</p>',              // 段首 2 个零宽空格
  '<p>\tA4-tab1</p>',                         // 段首 1 个制表符
  '<p>　　A5-ideo2</p>',              // 段首 2 个全角空格
  '<p><span>  A6-inSpan</span></p>',          // 空格写在行内标签**内部**
  '<p>A7-mid <span>ner</span></p>',           // 段**中间** 1 个空格（对照组）
  '<p>A8-trail2  </p>',                       // 段尾 2 个空格
  '<section><p>  A9-inSection</p></section>', // 段首空格 + 外层 section
].join('')

const GUARD = `(() => {
  window.__writeGuard = { blocked: [], lastSave: null };
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
    if (verb !== 'GET' && verb !== 'HEAD' && /\\/api\\//.test(url)) {
      let body = {};
      try { body = init && init.body ? JSON.parse(init.body) : {}; } catch (error) { body = {}; }
      window.__writeGuard.lastSave = body.contentHtml || null;
      window.__writeGuard.blocked.push(verb + ' ' + url);
      return Promise.resolve(json(Object.assign({}, body, { updatedAt: new Date().toISOString() })));
    }
    const injected = localStorage.getItem('__ws_case');
    if (injected && ARTICLE_PATH.test(String(url))) {
      const base = JSON.parse(localStorage.getItem('__ws_base') || '{}');
      return Promise.resolve(json(Object.assign({}, base, { contentHtml: injected })));
    }
    return rawFetch.apply(this, arguments);
  };
  return 'installed';
})()`

/** 把每一块的文本原样（含空白）取回来，前 40 字。 */
const READ = `(() => JSON.stringify(
  [...document.querySelector('.ProseMirror').children]
    .map((el) => ({ tag: el.tagName.toLowerCase(), text: JSON.stringify(el.textContent.slice(0, 40)) }))))()`

if (!login.success) throw new Error('登录失败：' + JSON.stringify(login))
const token = await login()
const base = (await (await fetch(`${API}/api/articles/${ARTICLE_ID}`,
  { headers: { Authorization: `Bearer ${token}` } })).json()).data
const before = { revision: base.revision, updatedAt: base.updatedAt }

const { client, version, close } = await launchBrowser({ port: 9358 })
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

/** 打开一次文章，等编辑器就绪 + 字体加载完，再读回每个顶层块的文本。 */
const load = async (html) => {
  await page.evaluate(`localStorage.setItem('__ws_case', ${JSON.stringify(html)}); true`)
  await page.navigate(`${API}/articles/${ARTICLE_ID}`)
  for (let attempt = 0; attempt < 80; attempt += 1) {
    const ready = await page.evaluate(`!!document.querySelector('.ProseMirror')`)
    if (ready === true) break
    await sleep(250)
  }
  await page.evaluate(`document.fonts.ready`)
  await sleep(500)
  return JSON.parse(await page.evaluate(READ))
}

await page.navigate(API + '/login')
await page.evaluate(`localStorage.setItem('wechat_bot_token', ${JSON.stringify(token)});`
  + `localStorage.setItem('__ws_base', ${JSON.stringify(JSON.stringify(base))}); true`)

console.log(`灌进去的原文 ${doc.length} 字符，${doc.match(/<p/g).length} 个 <p>：`)
console.log('  ' + JSON.stringify(doc))
const parsed = await load(doc)
console.log('\n① 灌进去之后（编辑器解析结果）：')
for (const [index, block] of parsed.entries()) console.log('  [' + index + '] ' + block.tag + ' ' + block.text)

// 逼出一次保存：末段末尾敲一个字符再退格（净零编辑），写请求被拦下并留下保存出口。
await page.evaluate(`(() => {
  const paragraphs = [...document.querySelectorAll('.ProseMirror p')]
    .filter((el) => (el.textContent || '').trim().length > 0);
  const last = paragraphs[paragraphs.length - 1];
  if (!last) return 'no-p';
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
const saved = JSON.parse(await page.evaluate(`JSON.stringify(window.__writeGuard.lastSave || null)`))
if (!saved) throw new Error('没拿到保存出口')
console.log('\n保存出口 ' + saved.length + ' 字符：')
console.log('  ' + JSON.stringify(saved))

const again = await load(saved)
console.log('\n② 保存出口原样灌回之后：')
for (const [index, block] of again.entries()) console.log('  [' + index + '] ' + block.tag + ' ' + block.text)

console.log('\n逐块对照（① 解析后 vs ② 重灌后）：')
const total = Math.max(parsed.length, again.length)
let same = 0
for (let index = 0; index < total; index += 1) {
  const a = parsed[index] ? parsed[index].text : '(缺)'
  const b = again[index] ? again[index].text : '(缺)'
  if (a === b) { same += 1; continue }
  console.log('  ❌ [' + index + '] ' + a + '  →  ' + b)
}
console.log('  相同 ' + same + ' / ' + total + ' 块')

writeFileSync(resolve(BROWSER_OUT, 'r25_whitespace_probe.json'), JSON.stringify({
  browser: version.Browser, docChars: doc.length, saveExitChars: saved.length,
  doc, parsed, again,
}, null, 1), 'utf8')

const after = (await (await fetch(`${API}/api/articles/${ARTICLE_ID}`,
  { headers: { Authorization: `Bearer ${token}` } })).json()).data
console.log('\n库核对：改前 revision=' + before.revision + ' / 改后 revision=' + after.revision
  + ' · updatedAt ' + (after.updatedAt === before.updatedAt ? '逐字未变 ✅' : '变了 ❌'))
await page.close()
close()
