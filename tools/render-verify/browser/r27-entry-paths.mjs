/**
 * 第二十七轮 B · **三条输入入口的对照**：`preserveLeadingWhitespace()` 只挂在打开文章那一处，
 * 另外两条入口（粘贴 / 手打）的**段首空白**是什么下场？
 *
 * 背景：第二十六轮的窄修法接在 `ArticleEditorView.vue` 的两处 `setContent` 上——
 * 那是**用户打开文章**这条路（真实的、也是当初出问题的那条）。
 * 但同一个编辑器还有另外两条进内容的路：粘贴、手打。本支把三条路量成一张表。
 *
 * 判据（**先立后测**，与第二十六轮同一形态）：
 *   ① **实时 DOM**：段首敲/粘进去的空白，当场在不在（用**首字符 x** 量，缩进真的占位才算在）；
 *   ② **保存出口**：被拦下的 `PUT` 的 `body.contentHtml` 里，那段空白还在不在；
 *   ③ **字数口径**：段首空白是「可见字符」还是被并掉，`textContent` 长度会如实反映。
 * 三条路各自给这三个数，**不挑好量的**、也不解释成「反正用户不会那么写」。
 *
 * ⚠️ 不写库：三层拦截同前几轮（应用层 patch fetch/XHR + CDP `Fetch.failRequest` + 回读 revision/updatedAt）。
 *
 * 用法：
 *   node tools/render-verify/browser/r27-entry-paths.mjs --label after
 *   node tools/render-verify/browser/r27-entry-paths.mjs --label before --bundle target/probe/r26/before-dist
 *   node tools/render-verify/browser/r27-entry-paths.mjs --label after --selftest   # 闸自检，不开浏览器、不写产物
 * 产物：target/probe/browser/r27_entry_paths_<label>.json
 *
 * 退出码（**第二十九轮 E3 补**）：
 *   0 = 失败项 0 且库未被改动；1 = 判据②有失败项，或 `revision`/`updatedAt` 变了。
 *   ⚠️ 判据② 的口径在本轮被**修正过**（详见下面 `量一条()` 的注释）：
 *      原式只认**字面半角空格**，而第二十六轮的窄修法正是把段首空白换成 `&nbsp;` ——
 *      于是「修好」会被它判成「丢了」。修正后认「字面 `[ \t]` 或 `&nbsp;`」，
 *      并且只对**实时 DOM 里真的量到了缩进**的那几条路生效。**这是修紧不是放宽**：
 *      原先它恒被 `void` 丢掉、根本不影响退出码，等于不存在。
 */
import { readFileSync, writeFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { launchBrowser, openPage } from './cdp.mjs'
import { BROWSER_OUT, ROOT, API } from '../paths.mjs'
import { 判据, 自检, 失败 } from '../gates.mjs'

const ARGS = process.argv.slice(2)
const argOf = (name, fallback = null) => {
  const at = ARGS.indexOf(name)
  return at >= 0 && ARGS[at + 1] ? ARGS[at + 1] : fallback
}
const LABEL = argOf('--label', 'after')
const BUNDLE = argOf('--bundle') ? resolve(ROOT, argOf('--bundle')) : null
const PORT = Number(argOf('--port', '9364'))
const ARTICLE_ID = 38
if (BUNDLE && !existsSync(resolve(BUNDLE, 'index.html'))) { console.error('--bundle 目录里没有 index.html'); process.exit(3) }
const sleep = (ms) => new Promise((done) => setTimeout(done, ms))

/**
 * 判据②的**取数**：一条路的保存出口里，段首那段空白还在不在。`show()` 与 `--selftest` 共用这一个函数。
 *
 * 口径（第二十九轮 E3 修正，理由见文件头的退出码说明）：
 *   `段首空白在出口` = 保存出口 HTML 里有一个 `<p>`（带不带属性都行）**紧跟**着 `&nbsp;` 或 `[ \t]`。
 *   `inExit字面空格` = 旧口径，只认字面半角空格；**留着只为对照，不再参与判定**。
 *   `实时DOM量到了缩进` = 实时 DOM 里该路确实量到了非零的相对缩进；只有这种路才受判据②约束。
 */
const 量一条 = (路, row) => {
  const 出口 = (row && row.saveExit) || ''
  const 实时缩进 = ((row && row.entered && row.entered.blocks) || []).map((block) => block.缩进)
  const 段首有空白 = /<p[^>]*>(?:&nbsp;|[ \t])/.test(出口)
  return {
    路, 出口字符数: 出口.length || null,
    实时缩进,
    实时DOM量到了缩进: 实时缩进.some((value) => typeof value === 'number' && value > 0.5),
    段首空白在出口: 段首有空白,
    inExit字面空格: /<p>[^\S\n]*[ ]/.test(出口),
    出口: 出口.slice(0, 300),
  }
}

const 出口清单 = []

/** 判据②：实时 DOM 里量到了缩进的路，保存出口里那段空白必须还在。 */
const 判 = ({ 清单 }) => {
  const 失败项 = []
  if (!清单.length) {
    失败项.push(失败('一条路都没量到', '这个闸在空输入上必须是红的'))
    return 失败项
  }
  for (const 条 of 清单) {
    if (!条.实时DOM量到了缩进) continue
    if (!条.段首空白在出口) {
      失败项.push(失败(条.路 + ' 保存出口丢了段首空白',
        '实时 DOM 缩进 ' + JSON.stringify(条.实时缩进) + 'px，出口里既没有字面空白也没有 &nbsp;：'
        + JSON.stringify(String(条.出口).slice(0, 120))))
    }
  }
  return 失败项
}

const 路径名 = {
  setcontent: '路径一 · 打开文章（setContent）',
  paste: '路径二 · 粘贴（HTML）',
  pastePlain: '路径二之二 · 粘贴（纯文本）',
  pasteHtmlFixed: '路径二之三 · 粘贴（HTML，段首空白已换成 &nbsp;）',
  type: '路径三 · 手打（真按键）',
}

if (ARGS.includes('--selftest')) {
  // 坏输入取**真实存档**：粘贴 HTML 那一条路，出口里确实没有段首空白（真数据），
  // 把它的「实时DOM量到了缩进」捏成 true，就等价于「DOM 里明明有、保存时丢了」这个真缺陷形态。
  const 存档 = resolve(BROWSER_OUT, `r27_entry_paths_${LABEL}.json`)
  if (!existsSync(存档)) { console.error('自检要读真实存档，但找不到 ' + 存档); process.exit(3) }
  const 已跑 = JSON.parse(readFileSync(存档, 'utf8'))
  const 造 = (results) => Object.entries(路径名)
    .filter(([key]) => results[key]) .map(([key, 名]) => 量一条(名, results[key]))

  const 当前 = 造(已跑.results)
  const 捏造丢失 = 当前.map((条) => (条.路.includes('粘贴（HTML）')
    ? { ...条, 实时DOM量到了缩进: true } : 条))
  const 抹掉空白 = 当前.map((条) => (条.路.includes('手打')
    ? { ...条, 段首空白在出口: false } : 条))
  const 没量到缩进 = 当前.map((条) => ({ ...条, 实时DOM量到了缩进: false }))

  process.exitCode = 自检('27-B r27-entry-paths（判据②：保存出口的段首空白）', 判, [
    { 名: '当前存档（' + 当前.length + ' 条路）', 数据: { 清单: 当前 }, 期望: 0,
      备注: '其中「粘贴（HTML）」的实时 DOM 缩进本就是 0（空白在粘的那一刻被上游折叠，§3.27③ 第 9 条），不在判据②射程内' },
    { 名: '把「粘贴（HTML）」捏成「DOM 里量到了缩进」', 数据: { 清单: 捏造丢失 }, 期望: 1,
      备注: '这就是「DOM 里明明有、保存时丢了」的真缺陷形态' },
    { 名: '把「手打」那条的出口空白抹掉', 数据: { 清单: 抹掉空白 }, 期望: 1, 备注: '出口直接查' },
    { 名: '所有路都捏成「实时 DOM 没量到缩进」', 数据: { 清单: 没量到缩进 }, 期望: 0,
      备注: '没有可判对象时不该判红（但见下一条的退化保护）' },
    { 名: '空清单（退化输入）', 数据: { 清单: [] }, 期望: 1, 备注: '空输入不许判绿' },
  ])
} else {

/**
 * 三条路灌进编辑器的内容**尽量等价**：一段段首 2 个半角空格、一段段首 1 个制表符、一段对照。
 * 空白**写在同一层**（`<p>` 里直接就是空白），因为实现里「第一个含可见字符的文本节点」就是它。
 */
const DOC = '<p>  LEAD-SP</p><p>\tLEAD-TAB</p><p>PLAIN</p>'

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
    const injected = sessionStorage.getItem('__r27_case');
    if (injected && ARTICLE_PATH.test(String(url))) {
      const stored = JSON.parse(sessionStorage.getItem('__r27_base') || '{}');
      return Promise.resolve(json(Object.assign({}, stored, { contentHtml: injected })));
    }
    return rawFetch.apply(this, arguments);
  };
  return 'installed';
})()`

/** 版式量法：每个顶层块的首字符落点 x + 文本原文（含空白）。 */
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
  const left = (() => {
    const first = dom.children[0];
    if (!first) return null;
    const r = first.getBoundingClientRect();
    return round(r.x);
  })();
  return JSON.stringify({
    left,
    textLength: (dom.textContent || '').replace(/\\s+/g, '').length,
    blocks: [...dom.children].map((el) => ({
      tag: el.tagName.toLowerCase(),
      text: JSON.stringify(el.textContent.slice(0, 24)),
      首字符x: firstCharX(el),
      缩进: firstCharX(el) !== null && left !== null ? round(firstCharX(el) - left) : null,
    })),
  });
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
await page.evaluate(`sessionStorage.setItem('__r27_base', ${JSON.stringify(JSON.stringify(base))}); true`)

const openArticle = async (html) => {
  await page.evaluate(`sessionStorage.setItem('__r27_case', ${JSON.stringify(html)}); true`)
  await page.navigate(`${API}/articles/${ARTICLE_ID}`)
  for (let attempt = 0; attempt < 80; attempt += 1) {
    if (await page.evaluate(`!!document.querySelector('.ProseMirror')`) === true) break
    await sleep(250)
  }
  await page.evaluate(`document.fonts.ready`)
  await sleep(400)
}
const measure = async () => JSON.parse(await page.evaluate(READ))
/**
 * 先把焦点真正放回编辑器。
 * ⚠️ 这一步是量出来的教训：只 `Input.dispatchKeyEvent` 而不先点一下编辑器，
 * Ctrl+A / Backspace / 空格 / Tab **全都不生效**（第一次跑就是这样——粘贴与手打那两栏
 * 量到的其实是「注入时的原内容」，`PASTE-ANCHOR` 一直没被删掉）。
 */
const focusEditor = async () => {
  const box = JSON.parse(await page.evaluate(`(() => {
    const dom = document.querySelector('.ProseMirror');
    const r = dom.getBoundingClientRect();
    return JSON.stringify({ x: r.x + 40, y: r.y + 20 });
  })()`))
  await page.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: box.x, y: box.y, button: 'left', clickCount: 1 })
  await page.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: box.x, y: box.y, button: 'left', clickCount: 1 })
  await page.evaluate(`document.querySelector('.ProseMirror').focus(); true`)
  await sleep(150)
}
const selectAll = async () => {
  await focusEditor()
  for (const type of ['keyDown', 'rawKeyDown', 'keyUp']) {
    await page.send('Input.dispatchKeyEvent', {
      type, modifiers: 2, key: 'a', code: 'KeyA', windowsVirtualKeyCode: 65, nativeVirtualKeyCode: 65,
    })
  }
  await sleep(120)
}
const press = async (key, code, vk, text) => {
  await page.send('Input.dispatchKeyEvent', {
    type: 'keyDown', key, code, windowsVirtualKeyCode: vk, nativeVirtualKeyCode: vk, ...(text ? { text } : {}),
  })
  await page.send('Input.dispatchKeyEvent', { type: 'keyUp', key, code, windowsVirtualKeyCode: vk, nativeVirtualKeyCode: vk })
  await sleep(90)
}
/** 净零编辑逼出一次自动保存，并把保存出口取回来。 */
const saveAndCapture = async () => {
  await page.evaluate(`(() => {
    window.__writeGuard.lastSave = null;
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
  return JSON.parse(await page.evaluate(`JSON.stringify(window.__writeGuard.lastSave || null)`))
}

const results = {}

// ---------- 路径一：打开文章（setContent，第二十六轮已接上窄修法） ----------
await openArticle(DOC)
const setcontentEntered = await measure()
const setcontentExit = await saveAndCapture()
results.setcontent = { entered: setcontentEntered, saveExit: setcontentExit }

// ---------- 路径二：粘贴 ----------
await openArticle('<p>PASTE-ANCHOR</p>')
await selectAll()
for (const type of ['keyDown', 'keyUp']) {
  await page.send('Input.dispatchKeyEvent', {
    type, key: 'Backspace', code: 'Backspace', windowsVirtualKeyCode: 8, nativeVirtualKeyCode: 8,
  })
}
await sleep(200)
await page.evaluate(`(() => {
  const dom = document.querySelector('.ProseMirror');
  const html = ${JSON.stringify(DOC)};
  const ruler = document.createElement('div'); ruler.innerHTML = html;
  dom.focus();
  const transfer = new DataTransfer();
  transfer.setData('text/html', html);
  transfer.setData('text/plain', ruler.textContent);
  dom.dispatchEvent(new ClipboardEvent('paste', { clipboardData: transfer, bubbles: true, cancelable: true }));
  return true;
})()`)
await sleep(500)
const pasteText = await measure()
const pasteExitPasted = await saveAndCapture()
results.paste = { entered: pasteText, saveExit: pasteExitPasted }

// ---------- 路径二之二：粘贴**纯文本**（从记事本/代码编辑器复制，只有 `text/plain`） ----------
// 加这一支是因为「能不能把一个制表符弄进正文」这个问题，`text/html` 与 `text/plain` 走的解析器不同。
// 纯文本里的制表符是真实存在的（复制代码块的首行缩进就是它）。
const PLAIN = '  PLAIN-SP\n\tPLAIN-TAB\nPLAIN-TEXT'
await openArticle('<p>PLAIN-ANCHOR</p>')
await selectAll()
for (const type of ['keyDown', 'keyUp']) {
  await page.send('Input.dispatchKeyEvent', {
    type, key: 'Backspace', code: 'Backspace', windowsVirtualKeyCode: 8, nativeVirtualKeyCode: 8,
  })
}
await sleep(200)
await page.evaluate(`(() => {
  const dom = document.querySelector('.ProseMirror');
  dom.focus();
  const transfer = new DataTransfer();
  transfer.setData('text/plain', ${JSON.stringify(PLAIN)});
  dom.dispatchEvent(new ClipboardEvent('paste', { clipboardData: transfer, bubbles: true, cancelable: true }));
  return true;
})()`)
await sleep(500)
const pastePlainText = await measure()
const pastePlainExit = await saveAndCapture()
results.pastePlain = { entered: pastePlainText, saveExit: pastePlainExit }

// ---------- 路径二之三：粘贴 HTML，但载荷里的段首空白**已经换成了 `&nbsp;`** ----------
// 这一支不是产品行为，是**候选修法的可行性实测**：如果「粘贴时也走一遍定点替换」这个想法要做，
// 落点是 Tiptap 的 `transformPastedHTML`。这里直接喂一份「已经被替换过」的 HTML，
// 等价于问：**只要粘贴时肯替换，粘贴这条路的段首空白保得住吗**——保住才有得谈，保不住这条路直接出局。
// 下面那个字面量是 U+00A0（不换行空格）；每段前导空白只换**一个**——本支问的是「活不活得下来」，
// 不是「宽多少」，宽度归第二十六轮那套判据管。
const FIXED_DOC = DOC.replace(/(?<=>)([^\S\n]*)(?=[^\s<])/g, (run) => run.replace(/[ \t]/g, ' '))
await openArticle('<p>FIXED-ANCHOR</p>')
await selectAll()
for (const type of ['keyDown', 'keyUp']) {
  await page.send('Input.dispatchKeyEvent', {
    type, key: 'Backspace', code: 'Backspace', windowsVirtualKeyCode: 8, nativeVirtualKeyCode: 8,
  })
}
await sleep(200)
await page.evaluate(`(() => {
  const dom = document.querySelector('.ProseMirror');
  const html = ${JSON.stringify(FIXED_DOC)};
  const ruler = document.createElement('div'); ruler.innerHTML = html;
  dom.focus();
  const transfer = new DataTransfer();
  transfer.setData('text/html', html);
  transfer.setData('text/plain', ruler.textContent);
  dom.dispatchEvent(new ClipboardEvent('paste', { clipboardData: transfer, bubbles: true, cancelable: true }));
  return true;
})()`)
await sleep(500)
const pasteFixedText = await measure()
const pasteFixedExit = await saveAndCapture()
results.pasteHtmlFixed = { entered: pasteFixedText, saveExit: pasteFixedExit }

/** 把保存出口原样重开（＝用户保存后再打开），量第三遍。 */
const reopen = async (html) => {
  await openArticle(html)
  return measure()
}

// ---------- 路径三：手打（真按键：段首敲 2 个空格、再敲一次 Tab 键） ----------
await openArticle('<p>TYPE-ANCHOR</p>')
await selectAll()
for (const type of ['keyDown', 'keyUp']) {
  await page.send('Input.dispatchKeyEvent', {
    type, key: 'Backspace', code: 'Backspace', windowsVirtualKeyCode: 8, nativeVirtualKeyCode: 8,
  })
}
await sleep(200)
// 第 1 段：段首真敲 2 个空格 + 文字
await press(' ', 'Space', 32, ' ')
await press(' ', 'Space', 32, ' ')
await page.send('Input.insertText', { text: 'TYPE-SP' })
await sleep(150)
// 第 2 段：回车起新段，段首敲 Tab 键 + 文字
await press('Enter', 'Enter', 13, '\r')
await press('Tab', 'Tab', 9)
await page.send('Input.insertText', { text: 'TYPE-TAB' })
await sleep(400)
const typed = await measure()
const typeExit = await saveAndCapture()
results.type = { entered: typed, saveExit: typeExit, tabKey: await page.evaluate(`JSON.stringify(window.__r27Tab ?? null)`) }

// ---------- 三条路的保存出口「再打开」一遍 ----------
results.setcontent.reopened = await reopen(setcontentExit || '<p></p>')
results.paste.reopened = await reopen(pasteExitPasted || '<p></p>')
results.pastePlain.reopened = await reopen(pastePlainExit || '<p></p>')
results.pasteHtmlFixed.reopened = await reopen(pasteFixedExit || '<p></p>')
results.type.reopened = await reopen(typeExit || '<p></p>')

const dbAfter = await readArticle()
const unchanged = dbAfter.revision === dbBefore.revision && dbAfter.updatedAt === dbBefore.updatedAt

const show = (name, row) => {
  const 量到 = 量一条(name, row)
  出口清单.push(量到)
  console.log('\n===== ' + name + ' =====')
  console.log('  实时 DOM（左边界 x=' + row.entered.left + '）：')
  for (const block of row.entered.blocks) {
    console.log('    <' + block.tag + '> 文本=' + block.text + ' · 首字符 x=' + block.首字符x + ' · 相对缩进=' + block.缩进 + 'px')
  }
  console.log('  保存出口 ' + (row.saveExit ? row.saveExit.length + ' 字符' : '(没拿到)'))
  if (row.saveExit) console.log('    出口开头: ' + JSON.stringify(row.saveExit.slice(0, 200)))
  console.log('  判据② 段首空白在出口=' + 量到.段首空白在出口
    + '（实时 DOM 量到缩进=' + 量到.实时DOM量到了缩进
    + '，旧口径「字面半角空格」=' + 量到.inExit字面空格 + '）')
  console.log('  出口再打开：')
  for (const block of row.reopened.blocks) {
    console.log('    <' + block.tag + '> 文本=' + block.text + ' · 首字符 x=' + block.首字符x + ' · 相对缩进=' + block.缩进 + 'px')
  }
}

console.log('\n灌进去的原文（三条路等价）：' + JSON.stringify(DOC))
show('路径一 · 打开文章（setContent，窄修法挂着的那条）', results.setcontent)
show('路径二 · 粘贴（HTML）', results.paste)
show('路径二之二 · 粘贴（纯文本 ' + JSON.stringify(PLAIN) + '）', results.pastePlain)
show('路径二之三 · 粘贴（HTML，但段首空白已换成 &nbsp; · 候选修法可行性）', results.pasteHtmlFixed)
show('路径三 · 手打（真按键）', results.type)

console.log('\n库核对：改前 revision=' + dbBefore.revision + ' / 改后 revision=' + dbAfter.revision
  + ' · updatedAt ' + (unchanged ? '逐字未变 ✅' : '变了 ❌'))

writeFileSync(resolve(BROWSER_OUT, `r27_entry_paths_${LABEL}.json`), JSON.stringify({
  browser: version.Browser, origin: API, label: LABEL, bundle: BUNDLE || null, doc: DOC,
  results, dbBefore, dbAfter, dbUnchanged: unchanged, 出口清单,
}, null, 1), 'utf8')
console.log('\n产物:', resolve(BROWSER_OUT, `r27_entry_paths_${LABEL}.json`))
await page.close()
close()

const 闸红灯 = 判据('27-B r27-entry-paths ' + LABEL + '（判据②：保存出口的段首空白）',
  判, { 清单: 出口清单 }) ? 1 : 0
if (!unchanged) console.log('  ❌ 库被改动了：revision/updatedAt 与改前不一致')
process.exitCode = 闸红灯 || (unchanged ? 0 : 1)
}
