/**
 * 第二十六轮 A · 窄修法的**第二条判据**：段首空白是否真的活下来了（且要活过保存）。
 *
 * 第二十五轮已经证出：`<p>` 段首的**半角空格 / 制表符**在 `setContent` 的第一次解析里被吃掉
 * （`&nbsp;`、全角空格、零宽空格不受影响）。本支量的是窄修法生效**之后**这件事变没变。
 *
 * 判据（先立后测）：
 *   ① 打开后，段首带空白的段落，其**首字符 x** 必须大于同一行的左边界（＝空白真的占了位）；
 *   ② 保存出口（被拦下的 `PUT` 的 `body.contentHtml`）里**对应那一块**必须带着这段空白；
 *   ③ 把保存出口原样灌回去（＝用户保存后重新打开），首字符 x 与 ① 相同，**且这一段仍带着原文的段首空白**。
 *   三条都满足才算「段首空白存活」；哪一条不满足，逐条列出来，**exit 4**。
 *
 * ⚠️ **第二十九轮 A 收紧过 ②③**（原写法在已知坏版本上照样判 PASS，详见文件中部那段长注释）：
 *   · 原②里 `saved.includes(inputLeading.replace(/[ \t]/g, ''))` 的入参替换完是**空串**，
 *     `String.includes('')` 恒真 ⇒ 这条判据**不可能失败**（第二十六轮「改前」那一跑 ② 7/7 全过）；
 *   · 原③只比「灌回后 == 打开后」，两边同源 ⇒ 属**甲类盲区**，对「打开时就一致地丢」型 bug 天然免疫。
 * 收紧后同一批存档离线复判：`--bundle before-dist` 那一跑 ①/②/③ 各 **1/7**（只有全角空格 A5 存活），
 * 当前包与修复包各 **7/7**。反例自检：`--selftest`（离线读第二十六轮的坏版本存档，必须判 FAIL）。
 *
 * 对照口径：同一份正文、同一台浏览器，`--bundle` 换改前 / 改后两份前端各跑一遍。
 *
 * 用法：
 *   node tools/render-verify/browser/r26-leading-ws-effect.mjs --label before --bundle target/probe/r26/before-dist
 *   node tools/render-verify/browser/r26-leading-ws-effect.mjs --label after
 *   node tools/render-verify/browser/r26-leading-ws-effect.mjs --selftest   # 纯离线反例自检，不开浏览器
 * 产物：target/probe/browser/r26_effect_<label>.json + r26_effect_<label>_saveexit.html
 */
import { readFileSync, writeFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { launchBrowser, openPage } from './cdp.mjs'
import { BROWSER_OUT, ROOT, API } from '../paths.mjs'

const ARGS = process.argv.slice(2)
const argOf = (name, fallback = null) => {
  const at = ARGS.indexOf(name)
  return at >= 0 && ARGS[at + 1] ? ARGS[at + 1] : fallback
}
const LABEL = argOf('--label', 'after')
const BUNDLE = argOf('--bundle') ? resolve(ROOT, argOf('--bundle')) : null
const PORT = Number(argOf('--port', '9361'))
const ARTICLE_ID = 38
if (BUNDLE && !existsSync(resolve(BUNDLE, 'index.html'))) { console.error('--bundle 目录里没有 index.html'); process.exit(3) }
const sleep = (ms) => new Promise((done) => setTimeout(done, ms))

// ===========================================================================
// 判据 ①②③ 的**唯一一把尺子**（纯函数）。
//
// 写成纯函数是为了让 `--selftest` 能拿**已知坏版本**的存档离线套同一套规则——
// 这正是第二十九轮 A 要求的「反例自检」：闸必须在那一份上**判 FAIL**。
//
// ⚠️ 第二十九轮 A 收紧了两条（都因为「在已知坏版本上照样判 PASS」）：
//
//   · **判据② 原来是恒真的**。原文是
//       `saved.includes(inputLeading.replace(/[ \t]/g, '')) || saved.includes('&nbsp;')`
//     而 `inputLeading` 本身就是纯空格 / 制表符，`replace` 之后是**空串**，
//     `String.prototype.includes('')` 恒为 `true` ⇒ 这一条**不可能失败**。
//     第二十六轮「改前」那一跑判据② 7/7 全过，就是这么来的。
//     现改为**逐块判**：把保存出口按同一套块规则切开，第 index 块自己的段首空白必须非空。
//
//   · **判据③ 原来是甲类盲区**。它只比「灌回后 == 打开后」，两边都出自同一份前端，
//     段首空白在「打开」那一步就丢了的旧前端，灌回后当然还是丢的 ⇒ 照样 PASS
//     （第二十九轮实测：`r26_effect_before.json` 的判据③ 7/7 全过）。现补上**入口期望值**：
//     灌回后这一块必须仍带着**原文里的那段段首空白**。
//
// 收紧后的实测（同一批存档，离线套用）：`before` 判据①/②/③ 各 **1/7**（唯一活下来的是
// 全角空格 A5，与第二十五轮的结论一致），`deployed` 与 `after` 各 **7/7**。
// ===========================================================================
const BLOCK_RE = () => /<section>[\s\S]*?<\/section>|<p[^>]*>[\s\S]*?<\/p>/g
const plainOf = (html) => html.replace(/<[^>]+>/g, '')
/** 出口里段首空白是以 `&nbsp;` 实体写下来的，判之前先还原成真字符。 */
const decodeEntities = (text) => text.replace(/&nbsp;/gi, ' ')
const leadingOf = (html) => {
  const hit = /^[\s 　]+/.exec(plainOf(html))
  return hit ? hit[0] : ''
}
const leadingOfSaved = (html) => {
  const hit = /^[\s 　]+/.exec(decodeEntities(plainOf(html)))
  return hit ? hit[0] : ''
}
const leadingInText = (text) => {
  const hit = /^[\s 　]+/.exec(text || '')
  return hit ? hit[0] : ''
}

/**
 * 逐块判三条。`parsed` / `again` 是 `READ` 量出来的行（`text` 是 JSON 串），
 * `saveExit` 是被拦下的那份 `PUT` 的 `body.contentHtml`。
 *
 * ⚠️ 逐条表的**分母取自「灌进去的原文」**，不是「打开之后还剩什么」。
 * 若按后者取，段首空白已经被吃掉的段落根本不会进表——那是拿结果筛样本，
 * 「存活 2 / 2」会变成一句好听的空话（改前那一跑就会现原形）。
 */
function judge({ doc, parsed, again, saveExit }) {
  const INPUT_BLOCKS = doc.match(BLOCK_RE()) || []
  const SAVED_BLOCKS = (saveExit || '').match(BLOCK_RE()) || []
  const rows = []
  for (const [index, html] of INPUT_BLOCKS.entries()) {
    const inputLeading = leadingOf(html)
    if (!inputLeading || !/\S/.test(plainOf(html))) continue
    const opened = parsed[index], back = again[index]
    const openedText = opened ? JSON.parse(opened.text) : ''
    const backText = back ? JSON.parse(back.text) : ''
    const stillThere = /^[\s 　]/.test(openedText.replace(/​/g, ''))
    rows.push({
      第几块: index,
      原文里的段首空白: JSON.stringify(inputLeading),
      打开后: opened ? opened.text : '(缺)',
      打开后空白还在: stillThere,
      灌回后: back ? back.text : '(缺)',
      首字符x_打开后: opened ? opened.首字符x : null,
      首字符x_灌回后: back ? back.首字符x : null,
      判据一_占位: stillThere && !!opened && opened.首字符x !== null,
      判据二_出口里带着: leadingOfSaved(SAVED_BLOCKS[index] || '').length > 0,
      判据二_出口里的段首空白: JSON.stringify(leadingOfSaved(SAVED_BLOCKS[index] || '')),
      判据三_灌回后相同: !!back && backText === openedText && back.首字符x === opened.首字符x
        && leadingInText(backText).length > 0,
    })
  }
  return rows
}

// ---------- `--selftest`：纯离线，拿**已知坏版本**的存档套同一套判据，必须判 FAIL ----------
// 存档 = 第二十六轮 `--bundle target/probe/r26/before-dist` 那一跑（段首空白在「打开」时就被吃掉）。
// 判据：① 收紧后的判据②在它上面**至少要失败一条**；② 收紧后的判据③同样至少要失败一条。
// 任一条「全过」即说明闸又写松了，**exit 1**。
if (ARGS.includes('--selftest')) {
  const 存档 = resolve(BROWSER_OUT, 'r26_effect_before.json')
  if (!existsSync(存档)) { console.error('没有存档 ' + 存档 + '（先跑第二十六轮的 --bundle before-dist）'); process.exit(3) }
  const payload = JSON.parse(readFileSync(存档, 'utf8'))
  const rows = judge(payload)
  const count = (key) => rows.filter((row) => row[key]).length
  const c1 = count('判据一_占位'), c2 = count('判据二_出口里带着'), c3 = count('判据三_灌回后相同')
  console.log('反例自检（已知坏版本存档）:', 存档)
  console.log('  bundle = ' + (payload.bundle || '(未记)'))
  console.log('  条数 ' + rows.length + ' · 判据① ' + c1 + ' · 判据② ' + c2 + ' · 判据③ ' + c3)
  for (const row of rows) {
    console.log('    [' + String(row.第几块).padStart(2) + '] 原文 ' + row.原文里的段首空白.padEnd(6)
      + ' 出口里 ' + row.判据二_出口里的段首空白.padEnd(8)
      + ' ①' + (row.判据一_占位 ? '通过' : '失败') + ' ②' + (row.判据二_出口里带着 ? '通过' : '失败')
      + ' ③' + (row.判据三_灌回后相同 ? '通过' : '失败'))
  }
  const caught2 = c2 < rows.length, caught3 = c3 < rows.length
  console.log('  → 判据②抓住坏版本: ' + (caught2 ? '是 ✅' : '否 ❌（闸写松了）'))
  console.log('  → 判据③抓住坏版本: ' + (caught3 ? '是 ✅' : '否 ❌（闸写松了）'))
  process.exit(caught2 && caught3 ? 0 : 1)
}

/**
 * 每一种写法只差「空白写在哪、写成什么字符」，其余一模一样。
 * 与第二十五轮 `r25-whitespace-probe.mjs` 的语料**逐字相同**——
 * 这样「改前」那一列可以直接与上一轮的数字对齐，不是新造一组好量的样本。
 * 后两段 `LEADING-SPACES` / `LEADING-TAB` 是第二十五轮那个副作用试验用的样本，一并留着。
 */
const doc = [
  '<p>  A1-plain2</p>',                       // 段首 2 个半角空格 ← 用户可感知的那一种
  '<p>&nbsp;&nbsp;A2-nbsp2</p>',              // 段首 2 个不换行空格
  '<p>​​A3-zwsp2</p>',              // 段首 2 个零宽空格
  '<p>\tA4-tab1</p>',                         // 段首 1 个制表符
  '<p>　　A5-ideo2</p>',              // 段首 2 个全角空格
  '<p><span>  A6-inSpan</span></p>',          // 空格写在行内标签**内部**
  '<p>A7-mid <span>ner</span></p>',           // 段**中间** 1 个空格（对照组）
  '<p>A8-trail2  </p>',                       // 段尾 2 个空格
  '<section><p>  A9-inSection</p></section>', // 段首空格 + 外层 section
  '<p>  LEADING-SPACES</p>',                  // 第二十五轮副作用试验的样本
  '<p>\tLEADING-TAB</p>',
].join('')

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
    const injected = sessionStorage.getItem('__r26_case');
    if (injected && ARTICLE_PATH.test(String(url))) {
      const stored = JSON.parse(sessionStorage.getItem('__r26_base') || '{}');
      return Promise.resolve(json(Object.assign({}, stored, { contentHtml: injected })));
    }
    return rawFetch.apply(this, arguments);
  };
  return 'installed';
})()`

/** 每一块的文本原样（含空白）与**首字符落点 x**：段首空白在不在，这两项一起看。 */
const READ = `(() => {
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
  return JSON.stringify([...dom.children].map((el) => ({
    tag: el.tagName.toLowerCase(),
    text: JSON.stringify(el.textContent.slice(0, 40)),
    首字符x: firstCharX(el),
    html: el.outerHTML.slice(0, 160),
  })));
})()`

const login = await (await fetch(API + '/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'Admin@123' }),
})).json()
if (!login.success) throw new Error('登录失败：' + JSON.stringify(login))
const token = login.data.token
const base = (await (await fetch(`${API}/api/articles/${ARTICLE_ID}`,
  { headers: { Authorization: `Bearer ${token}` } })).json()).data
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
await page.evaluate(`sessionStorage.setItem('__r26_base', ${JSON.stringify(JSON.stringify(base))}); true`)

const load = async (html) => {
  await page.evaluate(`sessionStorage.setItem('__r26_case', ${JSON.stringify(html)}); true`)
  await page.navigate(`${API}/articles/${ARTICLE_ID}`)
  for (let attempt = 0; attempt < 80; attempt += 1) {
    const ready = await page.evaluate(`!!document.querySelector('.ProseMirror')`)
    if (ready === true) break
    await sleep(250)
  }
  await page.evaluate(`document.fonts.ready`)
  await sleep(400)
  return JSON.parse(await page.evaluate(READ))
}

console.log('\n灌进去的原文（' + doc.length + ' 字符）：')
console.log('  ' + JSON.stringify(doc))
const parsed = await load(doc)
console.log('\n① 打开后（应用自己的 setContent 解析结果）：')
for (const [index, block] of parsed.entries()) console.log('  [' + index + '] ' + block.tag + ' ' + block.text + '  · 首字符 x=' + block.首字符x)

// —— 宽度对照：制表符 / 半角空格 / 不换行空格在**同一个排版上下文**里各占多宽 ——
// 这一段是为了把「制表符换成 1 个不换行空格」的代价**量出来**，而不是嘴上说一句「会变窄」。
// 量法：借 `.ProseMirror` 的计算样式造一个离屏容器，用 Range 量一个字符的宽度——
// 用的是编辑器自己的 `white-space` / `tab-size` / 字体，所以数字与编辑器里看到的是同一套。
const widths = JSON.parse(await page.evaluate(`(() => {
  const dom = document.querySelector('.ProseMirror');
  const style = getComputedStyle(dom);
  const host = document.createElement('span');
  host.style.whiteSpace = style.whiteSpace;
  host.style.tabSize = style.tabSize;
  host.style.font = style.font;
  host.style.fontSize = style.fontSize;
  host.style.fontFamily = style.fontFamily;
  host.style.letterSpacing = style.letterSpacing;
  host.style.wordSpacing = style.wordSpacing;
  host.style.position = 'absolute';
  host.style.visibility = 'hidden';
  host.style.left = '-9999px';
  document.body.appendChild(host);
  const widthOf = (text) => {
    host.textContent = text + 'X';
    const end = host.firstChild;
    const range = document.createRange();
    range.setStart(end, 0); range.setEnd(end, 1);
    return Math.round(range.getBoundingClientRect().width * 100) / 100;
  };
  const result = {
    whiteSpace: style.whiteSpace,
    tabSize: style.tabSize,
    space: widthOf(' '),
    nbsp: widthOf(String.fromCharCode(160)),
    tab: widthOf(String.fromCharCode(9)),
  };
  host.remove();
  return JSON.stringify(result);
})()`))
console.log('  宽度对照（编辑器自己的排版上下文）：半角空格 ' + widths.space + 'px · 不换行空格 '
  + widths.nbsp + 'px · 制表符 ' + widths.tab + 'px（white-space=' + widths.whiteSpace
  + ' · tab-size=' + widths.tabSize + '）')

// 逼出一次保存：末段末尾敲一个字符再退格（净零编辑），写请求被拦下并留下保存出口。
await page.evaluate(`(() => {
  const dom = document.querySelector('.ProseMirror');
  const paragraphs = [...dom.querySelectorAll('p')].filter((el) => (el.textContent || '').trim().length > 0);
  const last = paragraphs[paragraphs.length - 1];
  if (!last) return 'no-p';
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
const saveExit = await page.evaluate(`JSON.stringify(window.__writeGuard.lastSave || null)`)
const saved = JSON.parse(saveExit)
if (!saved) { console.error('!! 没拿到保存出口'); process.exit(2) }
const SAVE_EXIT_FILE = resolve(BROWSER_OUT, `r26_effect_${LABEL}_saveexit.html`)
writeFileSync(SAVE_EXIT_FILE, saved, 'utf8')
console.log('\n② 保存出口（' + saved.length + ' 字符）：')
console.log('  ' + JSON.stringify(saved.slice(0, 400)))

const again = await load(saved)
console.log('\n③ 保存出口原样灌回后：')
for (const [index, block] of again.entries()) console.log('  [' + index + '] ' + block.tag + ' ' + block.text + '  · 首字符 x=' + block.首字符x)

// —— 判据 ①②③ 逐条判（尺子在文件顶部，`--selftest` 用的是同一把）——
const verdict = judge({ doc, parsed, again, saveExit: saved })
console.log('\n判据（**灌进去时**段首带空白的段落逐条）：')
for (const row of verdict) {
  console.log('  ' + (row.判据一_占位 && row.判据二_出口里带着 && row.判据三_灌回后相同 ? '✅' : '❌')
    + ' 第 ' + String(row.第几块).padStart(2) + ' 块  原文空白=' + row.原文里的段首空白
    + ' · 打开后=' + row.打开后 + ' · 灌回后=' + row.灌回后
    + ' · 出口里=' + row.判据二_出口里的段首空白
    + ' · 首字符 x ' + row.首字符x_打开后 + ' → ' + row.首字符x_灌回后)
}
const openedKept = verdict.filter((row) => row.打开后空白还在).length
const savedKept = verdict.filter((row) => row.判据二_出口里带着).length
const roundTripKept = verdict.filter((row) => row.判据三_灌回后相同).length
const 未过 = verdict.filter((row) => !(row.判据一_占位 && row.判据二_出口里带着 && row.判据三_灌回后相同))
console.log('  判据① 打开后空白还在占位：' + openedKept + ' / ' + verdict.length + ' 条')
console.log('  判据② 保存出口里带着这段空白：' + savedKept + ' / ' + verdict.length + ' 条')
console.log('  判据③ 打开→保存→重开后仍带着且位置不变：' + roundTripKept + ' / ' + verdict.length + ' 条')
if (未过.length) console.log('  ❌ 未过的块：' + 未过.map((row) => row.第几块 + '（原文 ' + row.原文里的段首空白 + '）').join('、'))

const dbAfter = (await (await fetch(`${API}/api/articles/${ARTICLE_ID}`,
  { headers: { Authorization: `Bearer ${token}` } })).json()).data
const unchanged = dbAfter.revision === dbBefore.revision && dbAfter.updatedAt === dbBefore.updatedAt
console.log('\n库核对：改前 revision=' + dbBefore.revision + ' / 改后 revision=' + dbAfter.revision
  + ' · updatedAt ' + (unchanged ? '逐字未变 ✅' : '变了 ❌'))

writeFileSync(resolve(BROWSER_OUT, `r26_effect_${LABEL}.json`), JSON.stringify({
  browser: version.Browser, origin: API, label: LABEL, bundle: BUNDLE || null,
  doc, parsed, saveExit: saved, again, verdict,
  openedKept, savedKept, roundTripKept, 未过: 未过.map((row) => row.第几块),
  total: verdict.length,
  dbBefore, dbAfter, dbUnchanged: unchanged,
}, null, 1), 'utf8')
console.log('产物:', resolve(BROWSER_OUT, `r26_effect_${LABEL}.json`), '·', SAVE_EXIT_FILE)
await page.close()
close()
if (!unchanged) process.exit(1)
// 三条判据里有任何一条不成立就判红（第二十九轮 A 之前这一支**没有退出码**，
// 「跑完 exit 0」只说明脚本没抛异常，不说明判据成立）。
if (未过.length) process.exit(4)
