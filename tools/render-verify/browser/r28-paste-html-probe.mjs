/**
 * 第二十八轮 B · **粘贴 HTML 那条路（`§3.27③` 第 9 条）要不要修**——本轮只给依据，不改代码。
 *
 * 背景：第二十六轮的窄修法 `preserveLeadingWhitespace()` 只挂在**打开文章**那一处（`setContent`）。
 * 第二十七轮把三条输入入口量成一张表，结论是：粘贴 `text/html` 这条路，段首 `[ \t]`
 * **进出字节完全相同**（ProseMirror 的 HTML 分支用 `preserveWhitespace: false` 解析，
 * 段首空白被并掉）。当时的建议是把同一个函数挂到 `transformPastedHTML` 上。
 *
 * 本轮把「要不要修」摊成两件事，各给数字/源码依据，**判据先立**：
 *
 *   A. **暴露面**：用户从网页/文档复制粘贴、带段首缩进的 HTML，常见还是罕见？
 *      口径一（可控、可复现）：**能构造的「网页缩进写法」有哪几种，各自的剪贴板载荷里到底是什么**。
 *        做法是造一张合成网页 → 真 Ctrl+C → 读剪贴板 `text/html` 原文 → 再用**与实现同一把尺子**
 *        判「这段载荷里的段首算不算 `[ \t]`」。同时量每种写法在**源网页上**看起来有没有缩进——
 *        用户看不见的缩进，谈不上"粘贴后丢了"。
 *      口径二（真实语料）：仓库里已有的渲染产物与存量正文，段首 `[ \t]` 各有多少处
 *        （第二十七轮已量过，本支只**读回它的产物 JSON**，不重复连库）。
 *      ⚠️ 口径的边界写在产物里：本支**没有**去采样真实互联网页面，合成的这一张网页是
 *        「浏览器复制网页时的序列化行为」的替身，不是「网页长什么样」的样本。
 *        「网上有多少网页这么写」本支**给不出数字，也不编**。
 *
 *   B. **修法的代价**：`transformPastedHTML` 会不会影响**其他**粘贴内容（表格 / 代码块 / 公式 / SVG）？
 *      三份依据：
 *        B1 源码级：调用点在 `prosemirror-view/dist/index.js`，它拿到的是**一个 html 字符串**、
 *           返回的也是字符串，紧接着进 `readHTML()`；拖动（drop）走同一条路。逐条记 file:line。
 *        B2 语料级：把**真函数**（从 `webui/src/editorExtensions.js` 原样切出来的源码块）施在
 *           全部产物上，报「改了几个文档 / 每处落在什么标签链上 / 有没有落在 table/pre/code/svg/katex 里」。
 *        B3 DOM 级：挑 6 个代表载荷（表格 / 代码块 / KaTeX 公式 / SVG / checklist / 金句卡），
 *           在**真编辑器**里「原样粘贴」与「先过一遍变换再粘贴」各来一次，逐叶子比实时 DOM
 *           与保存出口——除段首空白本身外必须逐字相同。
 *
 * 全程**不写生产数据**：三层拦截（应用层 patch fetch + CDP `Fetch.failRequest` + 回读 revision/updatedAt）。
 *
 * 用法：node tools/render-verify/browser/r28-paste-html-probe.mjs [--port 9371]
 * 产物：target/probe/browser/r28_paste_html_probe.json
 */
import { readFileSync, writeFileSync, existsSync, readdirSync } from 'node:fs'
import { resolve, join } from 'node:path'
import { createHash } from 'node:crypto'
import { launchBrowser, openPage } from './cdp.mjs'
import { BROWSER_OUT, OUT, ROOT, API, login } from '../paths.mjs'
import { leadingRuns } from '../round27_leading_ws_scan.mjs'

const ARGS = process.argv.slice(2)
const argOf = (name, fallback = null) => {
  const at = ARGS.indexOf(name)
  return at >= 0 && ARGS[at + 1] ? ARGS[at + 1] : fallback
}
const PORT = Number(argOf('--port', '9371'))
const ARTICLE_ID = 38
const sleep = (ms) => new Promise((done) => setTimeout(done, ms))

// ---------- 把产品里的那个函数原样切出来（不另写一份实现） ----------
const SRC_FILE = resolve(ROOT, 'webui', 'src', 'editorExtensions.js')
const SOURCE = readFileSync(SRC_FILE, 'utf8')
const BLOCK_START = SOURCE.indexOf('const whitespaceOpaqueTags = new Set(')
const BLOCK_END = SOURCE.indexOf('export const PreservedInlineStyle')
if (BLOCK_START < 0 || BLOCK_END <= BLOCK_START) {
  console.error('切不出 preserveLeadingWhitespace 的源码块（编辑器扩展文件动过？）')
  process.exit(3)
}
const BLOCK = SOURCE.slice(BLOCK_START, BLOCK_END).replace(/^export /gm, '')
const BLOCK_SHA = createHash('sha256').update(BLOCK).digest('hex').slice(0, 16)
/** 让切出来的块在页面里把内部函数也交出来（代价分析要用同一对 `firstVisibleTextNode` / `leadingRunToNbsp`）。 */
const INJECT = `(() => {\n${BLOCK}\nreturn { preserveLeadingWhitespace, firstVisibleTextNode, leadingRunToNbsp, whitespaceOpaqueTags, TAB_SIZE };\n})()`

// ---------- 合成「网页」：段首缩进的 7 种写法 ----------
// 每种写法都要能回答两个问题：① 在**源网页上**看得见缩进吗（量首字符落点）；② 复制出来的剪贴板 HTML 里，
// 段首到底是什么字符（`&nbsp;` / 真半角空格 / U+3000 / CSS / 根本没有）。
const WEB_FORMS = [
  { id: 'nbsp', 靠什么: '空白字符', 说明: 'HTML 里的 &nbsp; 缩进（网页最常用的「手动缩进」）', 元素: 'p', html: '<p>&nbsp;&nbsp;NBSP-甲</p>' },
  { id: 'fullwidth', 靠什么: '空白字符', 说明: '全角空格 U+3000 缩进（中文网页常见）', 元素: 'p', html: '<p>\u3000\u3000FULLWIDTH-乙</p>' },
  { id: 'prewrap', 靠什么: '空白字符', 说明: 'CSS white-space:pre-wrap + 真半角空格（缩进看得见）', 元素: 'p', html: '<p style="white-space:pre-wrap">  PREWRAP-丙</p>' },
  { id: 'plain', 靠什么: '空白字符', 说明: '普通 HTML 里的真半角空格（CSS 折叠，缩进看不见）', 元素: 'p', html: '<p>  PLAIN-丁</p>' },
  { id: 'textindent', 靠什么: 'CSS', 说明: 'CSS text-indent:2em（缩进看得见，不靠空白字符）', 元素: 'p', html: '<p style="text-indent:2em">TEXTINDENT-戊</p>' },
  { id: 'padding', 靠什么: 'CSS', 说明: 'CSS padding-left:32px（缩进看得见，不靠空白字符）', 元素: 'div', html: '<div style="padding-left:32px">PADDING-己</div>' },
  { id: 'pre', 靠什么: '空白字符', 说明: '<pre> + 真半角空格（缩进看得见，且在修法的跳过名单里）', 元素: 'pre', html: '<pre>  PRE-庚</pre>' },
]
const WEB_PAGE = `<!doctype html><html><head><meta charset="utf-8"><title>r28 合成网页</title>
<style>body{font:16px/1.6 system-ui,sans-serif;margin:0;padding:8px} p,div,pre{margin:0 0 6px}</style>
<script>
window.__copies = [];
document.addEventListener('copy', (event) => {
  try {
    window.__copies.push({
      html: event.clipboardData ? event.clipboardData.getData('text/html') : null,
      text: event.clipboardData ? event.clipboardData.getData('text/plain') : null,
    });
  } catch (error) { window.__copies.push({ error: String(error) }); }
});
<\/script></head><body>
${WEB_FORMS.map((form) => '<div id="wrap-' + form.id + '">' + form.html + '</div>').join('\n')}
</body></html>`

// ---------- 代价 B3 的 6 个代表载荷 ----------
const COST_SAMPLES = [
  { id: 'table', 说明: '窄列表格（标题 DA01 产物）', 文件: join(OUT, 'r16', 'r16-06-title-da01.html') },
  { id: 'code', 说明: '代码块', 文件: join(OUT, 'components', 'ctn-code-block.html') },
  { id: 'katex', 说明: 'KaTeX 公式块', 文件: join(OUT, 'components', 'math-block.html') },
  { id: 'svg', 说明: '内嵌 SVG 的卡片', 文件: join(OUT, 'components', 'blk-engage-card.html') },
  { id: 'checklist', 说明: 'checklist', 文件: join(OUT, 'r16', 'r16-08-checklist.html') },
  { id: 'quote', 说明: '金句卡', 文件: join(OUT, 'r16', 'r16-04-quote-card.html') },
].filter((sample) => existsSync(sample.文件))

// ---------- 写保护（同前几轮的三层拦截） ----------
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
    const injected = sessionStorage.getItem('__r28_case');
    if (injected !== null && ARTICLE_PATH.test(String(url))) {
      const stored = JSON.parse(sessionStorage.getItem('__r28_base') || '{}');
      return Promise.resolve(json(Object.assign({}, stored, { contentHtml: injected })));
    }
    return rawFetch.apply(this, arguments);
  };
  return 'installed';
})()`

/** 版式量法：每个顶层块的首字符落点 x，以及相对块左边界 x 的缩进。 */
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
  return JSON.stringify([...dom.children].map((el) => {
    const box = el.getBoundingClientRect();
    const x = firstCharX(el);
    return {
      tag: el.tagName.toLowerCase(),
      text: JSON.stringify(el.textContent.slice(0, 24)),
      首字符x: x,
      缩进: x === null ? null : round(x - box.x),
    };
  }));
})()`

/** 逐叶子转储（只给本支的代价对照用；判据不依赖它）。 */
const LEAVES = `(() => {
  const dom = document.querySelector('.ProseMirror');
  const out = [];
  const walk = (node) => {
    for (const child of node.childNodes) {
      if (child.nodeType === 3) { out.push('T:' + child.nodeValue); continue }
      if (child.nodeType !== 1) continue;
      const attrs = [...child.attributes].map((a) => a.name + '=' + a.value).join('|');
      out.push('E:' + child.tagName + '[' + attrs + ']');
      walk(child);
    }
  };
  walk(dom);
  return JSON.stringify(out);
})()`

/** 合成一次粘贴（与第二十七轮的粘贴支同形态；真 Ctrl+V 走不通时的兜底，会在产物里标出来）。 */
const syntheticPaste = (html) => `(() => {
  const dom = document.querySelector('.ProseMirror');
  const html = ${JSON.stringify(html)};
  const ruler = document.createElement('div'); ruler.innerHTML = html;
  dom.focus();
  const transfer = new DataTransfer();
  transfer.setData('text/html', html);
  transfer.setData('text/plain', ruler.textContent);
  dom.dispatchEvent(new ClipboardEvent('paste', { clipboardData: transfer, bubbles: true, cancelable: true }));
  return true;
})()`

// ---------- 登录 + 基线 ----------
if (!login.success) throw new Error('登录失败：' + JSON.stringify(login))
const token = await login()
const readArticle = async () => (await (await fetch(`${API}/api/articles/${ARTICLE_ID}`,
  { headers: { Authorization: `Bearer ${token}` } })).json()).data
const base = await readArticle()
const dbBefore = { revision: base.revision, updatedAt: base.updatedAt }

const { client, version, close } = await launchBrowser({ port: PORT })
console.log('浏览器:', version.Browser, '· 目标:', API, '· 前端: (应用自带的 target/classes/static)')
console.log('切入的源码块:', SRC_FILE, '·', BLOCK.length, '字节 · sha256:' + BLOCK_SHA)

// 剪贴板权限（真 Ctrl+C/真 Ctrl+V 要用）
try {
  await client.send('Browser.grantPermissions', {
    origin: API, permissions: ['clipboardReadWrite', 'clipboardSanitizedWrite'],
  })
} catch (error) { console.log('⚠️ 剪贴板权限没授上:', String(error.message || error)) }

// ---------- 页面一：真应用（粘贴落点） ----------
const app = await openPage(client)
await app.send('Runtime.enable')
await app.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
await app.send('Page.addScriptToEvaluateOnNewDocument', { source: GUARD })
client.listeners.set('Fetch.requestPaused', [(message) => {
  const { requestId, request } = message.params
  const verb = String(request.method || 'GET').toUpperCase()
  if (verb !== 'GET' && verb !== 'HEAD') {
    client.send('Fetch.failRequest', { requestId, errorReason: 'Aborted' }, app.sessionId).catch(() => {})
    return
  }
  client.send('Fetch.continueRequest', { requestId }, app.sessionId).catch(() => {})
}])
await app.send('Fetch.enable', { patterns: [{ urlPattern: '*://*/*', requestStage: 'Request' }] })
await app.navigate(API + '/login')
await app.evaluate(`localStorage.setItem('wechat_bot_token', ${JSON.stringify(token)}); true`)
await app.evaluate(`sessionStorage.setItem('__r28_base', ${JSON.stringify(JSON.stringify(base))}); true`)

/**
 * 打开一篇文章（内容由本支注入，不碰库）。
 *
 * ⚠️ 两点是踩出来的：
 *   ① 导航前先把应用页 bringToFront——页面在后台时 Chrome 会节流，SPA 可能迟迟不挂载编辑器；
 *   ② 20 秒等不到就**重开一次**再等一轮。第三版就是在这里偶发拿到 `null.children`
 *      （上一次跑同样的顺序是好的），所以这里不再假设「等够久就一定起来」。
 */
const openArticle = async (html) => {
  await app.evaluate(`sessionStorage.setItem('__r28_case', ${JSON.stringify(html)}); true`)
  for (let round = 1; round <= 2; round += 1) {
    await client.send('Page.bringToFront', {}, app.sessionId)
    await app.navigate(`${API}/articles/${ARTICLE_ID}`)
    for (let attempt = 0; attempt < 80; attempt += 1) {
      if (await app.evaluate(`!!document.querySelector('.ProseMirror')`) === true) {
        await app.evaluate(`document.fonts.ready`)
        await sleep(400)
        return
      }
      await sleep(250)
    }
    console.log('  ⚠️ 第 ' + round + ' 次打开文章没等到编辑器，重开一次')
  }
  throw new Error('文章页 40 秒里都没挂载 .ProseMirror，后面的量都不成立')
}
const focusEditor = async () => {
  await client.send('Page.bringToFront', {}, app.sessionId)
  const box = JSON.parse(await app.evaluate(`(() => {
    const dom = document.querySelector('.ProseMirror');
    const r = dom.getBoundingClientRect();
    return JSON.stringify({ x: r.x + 40, y: r.y + 20 });
  })()`))
  await app.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: box.x, y: box.y, button: 'left', clickCount: 1 })
  await app.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: box.x, y: box.y, button: 'left', clickCount: 1 })
  await app.evaluate(`document.querySelector('.ProseMirror').focus(); true`)
  await sleep(150)
}
const selectAllAndClear = async () => {
  await focusEditor()
  for (const type of ['keyDown', 'rawKeyDown', 'keyUp']) {
    await app.send('Input.dispatchKeyEvent', {
      type, modifiers: 2, key: 'a', code: 'KeyA', windowsVirtualKeyCode: 65, nativeVirtualKeyCode: 65,
    })
  }
  await sleep(120)
  for (const type of ['keyDown', 'keyUp']) {
    await app.send('Input.dispatchKeyEvent', {
      type, key: 'Backspace', code: 'Backspace', windowsVirtualKeyCode: 8, nativeVirtualKeyCode: 8,
    })
  }
  await sleep(200)
}
/** 真 Ctrl+V（前提：剪贴板里刚被页面二写过东西）。快捷键同 `copyForm` 只用 `rawKeyDown` + `keyUp`。 */
const realPaste = async () => {
  await focusEditor()
  for (const type of ['rawKeyDown', 'keyUp']) {
    await app.send('Input.dispatchKeyEvent', {
      type, modifiers: 2, key: 'v', code: 'KeyV', windowsVirtualKeyCode: 86, nativeVirtualKeyCode: 86,
    })
  }
  await sleep(600)
}
const saveAndCapture = async () => {
  await app.evaluate(`(() => {
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
  await app.send('Input.insertText', { text: 'x' })
  await sleep(120)
  for (const type of ['keyDown', 'keyUp']) {
    await app.send('Input.dispatchKeyEvent', {
      type, key: 'Backspace', code: 'Backspace', windowsVirtualKeyCode: 8, nativeVirtualKeyCode: 8,
    })
  }
  await sleep(2600)
  return JSON.parse(await app.evaluate(`JSON.stringify(window.__writeGuard.lastSave || null)`))
}

// ---------- 页面二：合成的「网页」 ----------
const web = await openPage(client)
await web.send('Runtime.enable')
await web.send('Emulation.setDeviceMetricsOverride', { width: 1200, height: 900, deviceScaleFactor: 1, mobile: false })
await web.navigate(API + '/login')          // 先落在应用源上，好让它是个「正常网页」而不是 about:blank
await web.evaluate(`document.open(); document.write(${JSON.stringify(WEB_PAGE)}); document.close(); true`)
await sleep(200)
// 把切出来的函数挂到 window 上（页面一/页面二共用同一份源码块）
await web.evaluate(`window.__r28 = ${INJECT}; 'ok'`)
/** 注入自检：`<p>  X</p>` 必须是 2 个 nbsp，`<p>\\tX</p>` 必须是 8 个（列推进口径）。 */
const 注入自检 = JSON.parse(await web.evaluate(`JSON.stringify({
  两个半角空格: window.__r28.preserveLeadingWhitespace('<p>  X</p>'),
  一个制表符: window.__r28.preserveLeadingWhitespace('<p>\\tX</p>'),
  不动代码块: window.__r28.preserveLeadingWhitespace('<pre>  X</pre>'),
})`))
const 自检通过 = 注入自检.两个半角空格 === '<p>' + '&nbsp;'.repeat(2) + 'X</p>'
  && 注入自检.一个制表符 === '<p>' + '&nbsp;'.repeat(8) + 'X</p>'
  && 注入自检.不动代码块 === '<pre>  X</pre>'
console.log('注入了真函数的源码块（sha256:' + BLOCK_SHA + '）· 自检:',
  JSON.stringify(注入自检), 自检通过 ? '✅' : '❌')
if (!自检通过) { console.error('注入的源码块行为不对，后面的数字全部不可信，停在这里'); process.exit(4) }

/** 网页上：每种写法「看起来」有没有缩进（首字符落点 − 块左边界）。 */
const 网页侧版式 = JSON.parse(await web.evaluate(`(() => {
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
  return JSON.stringify(Object.fromEntries(${JSON.stringify(WEB_FORMS.map((form) => form.id))}.map((id) => {
    const el = document.querySelector('#wrap-' + id + ' > *');
    const box = el.getBoundingClientRect();
    const x = firstCharX(el);
    return [id, { 块左边界x: round(box.x), 首字符x: x, 视觉缩进: x === null ? null : round(x - box.x) }];
  })));
})()`))

/** 在合成网页上选中某一块 → 真 Ctrl+C → 读剪贴板。 */
const copyForm = async (id) => {
  await client.send('Page.bringToFront', {}, web.sessionId)
  await web.evaluate(`(() => {
    const el = document.querySelector('#wrap-${id} > *');
    const range = document.createRange();
    range.selectNodeContents(el);
    const selection = window.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
    return true;
  })()`)
  await sleep(120)
  // ⚠️ 快捷键只发 `rawKeyDown` + `keyUp`：
  //   - `rawKeyDown` 与 `keyDown` **都发**会让 Chrome 各触发一次，粘贴内容因此被灌两遍
  //     （第一版跑出来是 `NBSP-甲 NBSP-甲`）；
  //   - 只发 `keyDown` 则连 copy 事件都不触发（第二版跑到这里就卡在下面的 clipboard.read()）。
  for (const type of ['rawKeyDown', 'keyUp']) {
    await web.send('Input.dispatchKeyEvent', {
      type, modifiers: 2, key: 'c', code: 'KeyC', windowsVirtualKeyCode: 67, nativeVirtualKeyCode: 67,
    })
  }
  await sleep(350)
  // 优先取 copy 事件里读到的序列化结果；没有再退回 navigator.clipboard.read()
  const captured = JSON.parse(await web.evaluate(`JSON.stringify(
    (window.__copies && window.__copies.length) ? window.__copies[window.__copies.length - 1] : null)`))
  let viaClipboard = null
  if (!captured || !captured.html) {
    // 兜底路径。加超时是因为 headless 下没授到权限时 `clipboard.read()` 不 reject、只挂住，
    // 第二版就卡死在这里（整支跑了 10 分钟没有任何输出）。
    try {
      viaClipboard = await Promise.race([
        web.evaluate(`(async () => {
          try {
            const items = await navigator.clipboard.read();
            for (const item of items) {
              if (item.types.includes('text/html')) return await (await item.getType('text/html')).text();
            }
            return null;
          } catch (error) { return 'ERR:' + String(error); }
        })()`),
        sleep(5000).then(() => 'ERR:clipboard.read() 超时（headless 下没授到权限时它会挂住）'),
      ])
    } catch (error) { viaClipboard = 'ERR:' + String(error) }
  }
  const raw = (captured && captured.html) || (typeof viaClipboard === 'string' && !viaClipboard.startsWith('ERR:') ? viaClipboard : null)
  return { raw, 取自: captured && captured.html ? 'copy 事件里的 clipboardData' : 'navigator.clipboard.read()',
    错误: !captured && typeof viaClipboard === 'string' && viaClipboard.startsWith('ERR:') ? viaClipboard : null }
}

console.log('\n================ A · 暴露面（口径一：合成网页的 7 种缩进写法）================')
console.log('合成网页（真 Ctrl+C 复制，读剪贴板 text/html 原文）：')
const forms = []
for (const form of WEB_FORMS) {
  const copy = await copyForm(form.id)
  const raw = copy.raw
  // 剪贴板 HTML 里只有 StartFragment..EndFragment 之间才是这次选区；取出来再判。
  const fragment = raw === null ? null
    : (() => {
        const hit = /<!--StartFragment-->([\s\S]*?)<!--EndFragment-->/.exec(raw)
        return hit ? hit[1] : raw
      })()
  const runs = fragment === null ? null : leadingRuns(fragment)
  const 会被修法改写 = runs === null ? null : runs.length
  // 段首第一个字符到底是什么（人眼可读的字面量）
  const 段首首字符 = fragment === null ? null : (() => {
    const hit = /^<[^>]+>([\s\S]{0,3})/.exec(fragment.trim())
    if (!hit) return null
    const chars = [...hit[1]].slice(0, 3).map((ch) => {
      const code = ch.codePointAt(0).toString(16).toUpperCase().padStart(4, '0')
      return ch === '\u00a0' ? 'NBSP(U+00A0)' : ch === '\u3000' ? 'IDEOGRAPHIC-SPACE(U+3000)' : ch === '\t' ? 'TAB(U+0009)' : ch === ' ' ? 'SPACE(U+0020)' : ch
    })
    return chars.join(' ')
  })()
  const 视觉缩进 = 网页侧版式[form.id] ? 网页侧版式[form.id].视觉缩进 : null
  const 行 = {
    id: form.id, 缩进靠什么: form.靠什么, 说明: form.说明, 源网页视觉缩进px: 视觉缩进,
    源网页看得见缩进: 视觉缩进 !== null && 视觉缩进 >= 3,
    剪贴板取自: copy.取自, 剪贴板HTML长度: raw === null ? null : raw.length,
    剪贴板片段: fragment === null ? null : fragment.slice(0, 160),
    剪贴板段首前几个字符: 段首首字符,
    剪贴板里带制表符: fragment === null ? null : /\t/.test(fragment),
    剪贴板有whiteSpacePre: fragment === null ? null : /white-space:\s*pre/.test(fragment),
    剪贴板文本前40字: fragment === null ? null : fragment.replace(/<[^>]*>/g, '').slice(0, 40),
    会被修法改写处数: 会被修法改写,
    剪贴板原始片段: fragment,
    剪贴板错误: copy.错误 || null,
  }
  forms.push(行)
  console.log('  · ' + form.id.padEnd(11) + ' 源网页缩进 ' + String(视觉缩进).padStart(6) + 'px'
    + ' · 剪贴板段首 ' + String(段首首字符) + ' · 会被修法改写 ' + 会被修法改写 + ' 处')
  console.log('      ' + form.说明)
  console.log('      剪贴板片段: ' + JSON.stringify(fragment === null ? null : fragment.slice(0, 120)))
}

// 口径一收口：剪贴板里真的带 `[ \t]` 且**源网页上看得见**的写法，才是真暴露
// （字段名是 `会被修法改写处数`；第一版这里写成 `会被修法改写`，filter 恒为 undefined>0，
//   汇总打成 0 种——与逐种明细自相矛盾，已修。）
const 真暴露 = forms.filter((row) => row.会被修法改写处数 > 0 && row.源网页看得见缩进)
const 看得见但不丢 = forms.filter((row) => row.源网页看得见缩进 && !(row.会被修法改写处数 > 0))
console.log('\n口径一汇总：' + forms.length + ' 种写法里')
console.log('  · 源网页上**看得见**段首缩进的：' + forms.filter((row) => row.源网页看得见缩进).length + ' 种')
console.log('  · 剪贴板里带 `[ \\t]`（修法会改写）：' + forms.filter((row) => row.会被修法改写处数 > 0).length + ' 种')
console.log('  · 剪贴板里声明了 `white-space: pre*` 的（ProseMirror 会据此**原样保留空白**）：'
  + forms.filter((row) => row.剪贴板有whiteSpacePre).length + ' 种'
  + ' → ' + forms.filter((row) => row.剪贴板有whiteSpacePre).map((row) => row.id).join(', '))
console.log('  · **两者同时成立**（真暴露）：' + 真暴露.length + ' 种'
  + (真暴露.length ? ' → ' + 真暴露.map((row) => row.id).join(', ') : ''))
console.log('  · 看得见缩进但剪贴板里不是 `[ \\t]`（修法帮不上，也不该帮）：'
  + 看得见但不丢.map((row) => row.id).join(', ') + ' 共 ' + 看得见但不丢.length + ' 种')

// ---------- 口径二：仓库已有的真实语料（读第二十七轮的产物，不重复连库） ----------
const R27_JSON = resolve(BROWSER_OUT, 'r27_leading_ws_scan.json')
let 语料 = null
if (existsSync(R27_JSON)) {
  const raw = JSON.parse(readFileSync(R27_JSON, 'utf8'))
  语料 = {
    来源: R27_JSON,
    渲染产物: raw.products,
    存量正文: raw.articles ? {
      篇数: raw.articles.篇数, 含段首空白的篇数: raw.articles.含段首空白的篇数,
      含段首制表符的篇数: raw.articles.含段首制表符的篇数, 逐处合计: raw.articles.逐处合计,
    } : null,
  }
  console.log('\n口径二（真实语料，读回第二十七轮产物 ' + R27_JSON + '）：')
  console.log('  ' + JSON.stringify(语料.渲染产物))
  if (语料.存量正文) console.log('  存量正文 ' + JSON.stringify(语料.存量正文))
} else {
  console.log('\n口径二：没找到 ' + R27_JSON + '，跳过（不重连库；第二十七轮那组数字见 docs/dev/known-issues-handoff.md §3.27）')
}

console.log('\n================ B · 落点侧（这些载荷粘进编辑器后丢不丢）================')
const results = []
/** 真 Ctrl+V 是否可用：拿第一种载荷试一次，落不下去就整体退回合成事件（会在产物里标明）。 */
await openArticle('<p>PASTE-PROBE</p>')
await selectAllAndClear()
await copyForm(WEB_FORMS[0].id)
await realPaste()
const realOk = (await app.evaluate(`document.querySelector('.ProseMirror').textContent`)).includes('NBSP')
console.log('真 Ctrl+V：' + (realOk ? '可用 ✅（下面全部用真键）' : '落不下去 ❌（退回合成 ClipboardEvent，产物里标出）'))

/**
 * 落点侧的两类载荷：
 *   甲类 = **浏览器真复制出来的**（`copyForm` 真 Ctrl+C + `realPaste` 真 Ctrl+V）——用户能走的唯一那条路；
 *   乙类 = **手写的**（合成 ClipboardEvent 注入）——第二十七轮那张表用的就是这种。
 *          加这一类是因为它是「粘贴会丢空白」这个结论的原始依据，必须和甲类并排出现，
 *          才能看出那句话到底在说什么：**丢的是手写载荷，不是浏览器会产出的载荷**。
 */
const EXTRA_PAYLOADS = [
  { id: 'hand-written-p', 说明: '手写载荷 <p>  X</p>（第二十七轮那张表的形态；浏览器复制不出来这种载荷）', html: '<p>  HAND-P</p>' },
  { id: 'hand-written-prewrap', 说明: '手写载荷 <p style="white-space:pre-wrap">  X</p>（只差一个 white-space 声明）', html: '<p style="white-space:pre-wrap">  HAND-PREWRAP</p>' },
]

for (const form of WEB_FORMS) {
  const copy = await copyForm(form.id)
  const raw = copy.raw
  const fragment = raw === null ? null
    : (() => {
        const hit = /<!--StartFragment-->([\s\S]*?)<!--EndFragment-->/.exec(raw)
        return hit ? hit[1] : raw
      })()
  const payload = fragment === null ? form.html : fragment
  await openArticle('<p>ANCHOR</p>')
  await selectAllAndClear()
  let 粘贴方式
  if (realOk) { await realPaste(); 粘贴方式 = '真 Ctrl+V（载荷=浏览器复制出来的原文）' }
  else {
    await app.evaluate(syntheticPaste(payload))
    await sleep(500)
    粘贴方式 = '合成 ClipboardEvent'
  }
  const entered = JSON.parse(await app.evaluate(READ))
  const exit = await saveAndCapture()
  // 出口原样重开（＝用户存完再打开），再把重开后的出口也抓一次——
  // 回答「打开时那道修法会不会把**粘进来**的真空格改写成 nbsp」。
  await openArticle(exit || '<p></p>')
  const reopened = JSON.parse(await app.evaluate(READ))
  const exit2 = await saveAndCapture()
  const 落点 = { id: form.id, 类型: '浏览器真复制', 缩进靠什么: form.靠什么, 说明: form.说明, 粘贴方式, 载荷: payload,
    实时: entered, 出口: exit, 再打开: reopened, 再存出口: exit2 }
  results.push(落点)
  const 实时缩进 = entered.length ? entered[0].缩进 : null
  const 再打开缩进 = reopened.length ? reopened[0].缩进 : null
  console.log('  · ' + form.id.padEnd(11) + ' 实时缩进 ' + String(实时缩进).padStart(7) + 'px'
    + ' · 再打开 ' + String(再打开缩进).padStart(7) + 'px'
    + ' · 出口 ' + (exit ? exit.length + ' 字符' : '(没拿到)'))
  console.log('      实时块: ' + JSON.stringify(entered.slice(0, 2)))
  console.log('      再存出口里那一块: '
    + JSON.stringify((exit2 || '').match(/.{0,30}(NBSP|FULLWIDTH|PREWRAP|PLAIN|TEXTINDENT|PADDING|PRE-)[^<]{0,20}/)?.[0] ?? '(没找到)'))
}

for (const extra of EXTRA_PAYLOADS) {
  await openArticle('<p>ANCHOR</p>')
  await selectAllAndClear()
  await app.evaluate(syntheticPaste(extra.html))
  await sleep(600)
  const entered = JSON.parse(await app.evaluate(READ))
  const exit = await saveAndCapture()
  const 落点 = { id: extra.id, 类型: '手写载荷', 缩进靠什么: '空白字符', 说明: extra.说明, 粘贴方式: '合成 ClipboardEvent', 载荷: extra.html,
    实时: entered, 出口: exit, 再打开: null, 再存出口: null }
  results.push(落点)
  console.log('  · ' + extra.id.padEnd(20) + ' 实时缩进 ' + String(entered.length ? entered[0].缩进 : null).padStart(7) + 'px'
    + ' · 出口 ' + JSON.stringify((exit || '').slice(0, 90)))
  console.log('      ' + extra.说明)
}

console.log('\n落点侧汇总（甲类＝浏览器真复制出来的载荷）：')
console.log('  （判「丢了」的口径：源网页上缩进 ≥3px，粘/存/再打开之后 <3px）')
for (const row of results.filter((r) => r.类型 === '浏览器真复制')) {
  const src = forms.find((form) => form.id === row.id)
  const 源 = src ? src.源网页视觉缩进px : null
  const 实时 = row.实时.length ? row.实时[0].缩进 : null
  const 再开 = row.再打开 && row.再打开.length ? row.再打开[0].缩进 : null
  const verdict = (源 !== null && 源 >= 3 && (实时 === null || 实时 < 3)) ? '丢了' : '保住'
  console.log('  · ' + row.id.padEnd(11) + ' 源网页 ' + String(源).padStart(6) + 'px → 粘进来 '
    + String(实时).padStart(6) + 'px → 再打开 ' + String(再开).padStart(6) + 'px  ' + verdict)
}
const 甲类丢了 = results.filter((row) => row.类型 === '浏览器真复制').filter((row) => {
  const src = forms.find((form) => form.id === row.id)
  const 实时 = row.实时.length ? row.实时[0].缩进 : null
  return src && src.缩进靠什么 === '空白字符' && src.源网页视觉缩进px >= 3 && (实时 === null || 实时 < 3)
})
const 甲类丢了的CSS = results.filter((row) => row.类型 === '浏览器真复制').filter((row) => {
  const src = forms.find((form) => form.id === row.id)
  const 实时 = row.实时.length ? row.实时[0].缩进 : null
  return src && src.缩进靠什么 === 'CSS' && src.源网页视觉缩进px >= 3 && (实时 === null || 实时 < 3)
})
console.log('  甲类里**空白字符类**缩进被丢掉的：' + 甲类丢了.length + ' 种'
  + (甲类丢了.length ? ' → ' + 甲类丢了.map((row) => row.id).join(', ') : '（一种都没丢——这条要判的就是它）'))
console.log('  甲类里**CSS 类**缩进被丢掉的：' + 甲类丢了的CSS.length + ' 种'
  + (甲类丢了的CSS.length ? ' → ' + 甲类丢了的CSS.map((row) => row.id).join(', ')
    + '（另一个问题：内联样式没保住，与本条的段首空白无关，只是本轮顺带量到）' : ''))

/**
 * 修法施在**真实剪贴板载荷**上会发生什么（离线，不碰编辑器）——
 * 这是「加了 hook 之后，用户真实粘贴会被改到哪些」的直接答案。
 */
const 真实载荷代价 = []
for (const row of forms) {
  if (!row.剪贴板原始片段) { 真实载荷代价.push({ id: row.id, 有载荷: false }); continue }
  const out = await web.evaluate('window.__r28.preserveLeadingWhitespace(' + JSON.stringify(row.剪贴板原始片段) + ')')
  const 剥标签 = (text) => text.replace(/<[^>]*>/g, '').slice(0, 30)
  真实载荷代价.push({
    id: row.id, 有载荷: true, 会被改写: out !== row.剪贴板原始片段,
    改前文本: 剥标签(row.剪贴板原始片段), 改后文本: out !== row.剪贴板原始片段 ? 剥标签(out) : null,
  })
}
console.log('\n把候选修法（同一个函数）施在**真实剪贴板载荷**上：')
for (const row of 真实载荷代价) {
  if (!row.有载荷) continue
  console.log('  · ' + row.id.padEnd(11) + (row.会被改写 ? ' 会被改写 → ' + JSON.stringify(row.改后文本) : ' 原样不动'))
}
const 载荷被改写的 = 真实载荷代价.filter((row) => row.会被改写)
console.log('  7 种里会被改写的：' + 载荷被改写的.length + ' 种'
  + (载荷被改写的.length ? ' → ' + 载荷被改写的.map((row) => row.id).join(', ') : ''))

console.log('\n================ B1 · 源码级（transformPastedHTML 的影响面）================')
const 源码行 = (() => {
  const view = resolve(ROOT, 'webui', 'node_modules', 'prosemirror-view', 'dist', 'index.js')
  const text = existsSync(view) ? readFileSync(view, 'utf8').split('\n') : []
  const find = (needle) => text.findIndex((line) => line.includes(needle)) + 1
  return {
    文件: 'webui/node_modules/prosemirror-view/dist/index.js',
    定义: find('function parseFromClipboard('),
    唯一调用点_粘贴: find('view.someProp("transformPastedHTML"'),
    拖动也走这条路: find('slice = parseFromClipboard(view, getText(event.dataTransfer)'),
    HTML分支的判断: find('let asText = !!text && (plainText || inCode || !html)'),
    解析时的空白折叠: find('preserveWhitespace: !!(asText || sliceData)'),
    变换结果去哪: find('dom = readHTML(html)'),
  }
})()
/**
 * 关键机制（本支新查到的）：**ProseMirror 解析时自己认 `white-space: pre*`**。
 * `prosemirror-model` 的 `addElement` 里：元素是 `<pre>` 或 `style.whiteSpace` 匹配 `/pre/`，
 * 就把这一支切成「保留空白」——所以剪贴板 HTML 只要带了 `white-space: pre-wrap`，
 * 段首真空格在**粘贴**这条路上本来就不会被并掉。浏览器复制时恰好会把这条声明一起写上（见甲类实测）。
 */
const 模型源码行 = (() => {
  const model = resolve(ROOT, 'webui', 'node_modules', 'prosemirror-model', 'dist', 'index.js')
  const text = existsSync(model) ? readFileSync(model, 'utf8').split('\n') : []
  const find = (needle) => text.findIndex((line) => line.includes(needle)) + 1
  return {
    文件: 'webui/node_modules/prosemirror-model/dist/index.js',
    whiteSpace判断: find('if (dom.tagName == "PRE" || /pre/.test(dom.style && dom.style.whiteSpace))'),
    取局部保留开关: find('this.localPreserveWS = true;'),
  }
})()
console.log('  另外查到（本支的关键机制）：' + 模型源码行.文件 + ':' + 模型源码行.whiteSpace判断
  + ' `if (dom.tagName == "PRE" || /pre/.test(dom.style && dom.style.whiteSpace))` → 下一行 `this.localPreserveWS = true;`')
console.log('  ——即：**粘贴时 ProseMirror 自己认 `white-space: pre*` 并原样保留空白**；'
  + '浏览器复制带可见空格缩进的网页时会把这条声明一起写进剪贴板 HTML，所以那条路本来就不丢。')
console.log('  调用点: ' + 源码行.文件 + ':' + 源码行.唯一调用点_粘贴 + '  `view.someProp("transformPastedHTML", f => { html = f(html, view); })`')
console.log('  它在 ' + 源码行.文件 + ':' + 源码行.HTML分支的判断 + ' 的 HTML 分支里（asText 为假才走），紧接着 ' + 源码行.变换结果去哪 + ' 行 `dom = readHTML(html)`')
console.log('  拖动: 同文件 :' + 源码行.拖动也走这条路 + '  也调 parseFromClipboard —— **拖动粘贴同样受影响**')
console.log('  解析时 ' + 源码行.解析时的空白折叠 + ' 行 `preserveWhitespace: !!(asText || sliceData)` 正是段首空白被并掉的地方（HTML 分支 asText 为假）')

console.log('\n================ B2 · 语料级（真函数施在全部产物上）================')
const PRODUCT_DIRS = ['components', 'r16']
const 语料行 = []
for (const dir of PRODUCT_DIRS) {
  const at = join(OUT, dir)
  if (!existsSync(at)) continue
  for (const name of readdirSync(at).filter((file) => file.endsWith('.html'))) {
    语料行.push({ file: dir + '/' + name, html: readFileSync(join(at, name), 'utf8') })
  }
}
// 在页面里跑真函数（同一份源码块），并顺带记录「哪一处被改了、落在什么标签链上」
await web.evaluate(`window.__r28scan = (rows) => rows.map(({ file, html }) => {
  const { preserveLeadingWhitespace, firstVisibleTextNode, leadingRunToNbsp, whitespaceOpaqueTags } = window.__r28;
  const out = preserveLeadingWhitespace(html);
  if (out === html) return { file, 改写处数: 0, 命中: [], 镜像自检: true };
  const holder = document.createElement('template');
  holder.innerHTML = html;
  const 命中 = [];
  for (const element of holder.content.querySelectorAll('*')) {
    if (whitespaceOpaqueTags.has(element.tagName)) continue;
    const node = firstVisibleTextNode(element);
    if (!node) continue;
    const run = /^[ \\t]+/.exec(node.nodeValue);
    if (!run) continue;
    node.nodeValue = node.nodeValue.replace(/^[ \\t]+/g, leadingRunToNbsp);
    const 链 = [];
    for (let el = element; el && el.parentElement; el = el.parentElement) 链.unshift(el.tagName.toLowerCase());
    命中.push({ tag: element.tagName.toLowerCase(), 链: 链.join('>'), 空白: JSON.stringify(run[0]), 列数: leadingRunToNbsp(run[0]).length });
  }
  return { file, 改写处数: 命中.length, 命中, 镜像自检: holder.innerHTML === out };
})`)
const BATCH = 20
const 语料结果 = []
for (let at = 0; at < 语料行.length; at += BATCH) {
  const batch = 语料行.slice(at, at + BATCH)
  const rows = JSON.parse(await web.evaluate(`JSON.stringify(window.__r28scan(${JSON.stringify(batch)}))`))
  语料结果.push(...rows)
}
const 被改写 = 语料结果.filter((row) => row.改写处数 > 0)
const 镜像不一致 = 语料结果.filter((row) => row.镜像自检 === false)
const 命中总数 = 被改写.reduce((sum, row) => sum + row.改写处数, 0)
const 落在表格或代码或公式或SVG里 = 被改写.flatMap((row) => row.命中
  .filter((hit) => /table|pre|code|katex|math|svg/i.test(hit.链 + '>' + hit.tag))
  .map((hit) => ({ file: row.file, ...hit })))
console.log('  产物文件 ' + 语料结果.length + ' 个 · 被改写的 ' + 被改写.length + ' 个 · 改写 ' + 命中总数 + ' 处')
console.log('  镜像自检（记录用的循环与真函数输出逐字节一致）：'
  + (镜像不一致.length === 0 ? '全部一致 ✅' : '❌ ' + JSON.stringify(镜像不一致.map((row) => row.file))))
for (const row of 被改写) {
  console.log('    ' + row.file + ' → ' + row.改写处数 + ' 处: '
    + row.命中.map((hit) => hit.链 + ' ' + hit.空白 + '→' + hit.列数 + '个nbsp').join(' | '))
}
console.log('  命中落在 table/pre/code/katex/math/svg 里的：' + 落在表格或代码或公式或SVG里.length + ' 处'
  + (落在表格或代码或公式或SVG里.length ? ' → ' + JSON.stringify(落在表格或代码或公式或SVG里) : ''))

console.log('\n================ B3 · DOM 级（6 个代表载荷：原样 vs 先过变换）================')
const 代价 = []
for (const sample of COST_SAMPLES) {
  const html = readFileSync(sample.文件, 'utf8')
  const transformed = await web.evaluate(`window.__r28.preserveLeadingWhitespace(${JSON.stringify(html)})`)
  const arms = [
    { 臂: '原样', 载荷: html },
    { 臂: '先过变换', 载荷: transformed },
  ]
  const rows = []
  for (const arm of arms) {
    await openArticle('<p>ANCHOR</p>')
    await selectAllAndClear()
    await app.evaluate(syntheticPaste(arm.载荷))
    await sleep(700)
    const leaves = JSON.parse(await app.evaluate(LEAVES))
    const exit = await saveAndCapture()
    rows.push({ 臂: arm.臂, 载荷: arm.载荷, leaves, 出口: exit })
  }
  const [a, b] = rows
  const 不同的叶子 = []
  const max = Math.max(a.leaves.length, b.leaves.length)
  for (let at = 0; at < max; at += 1) {
    if (a.leaves[at] !== b.leaves[at]) 不同的叶子.push({ at, 原样: a.leaves[at], 变换后: b.leaves[at] })
  }
  const 载荷变了 = a.载荷 !== b.载荷
  const 出口一样 = a.出口 === b.出口
  const row = {
    id: sample.id, 说明: sample.说明, 文件: sample.文件,
    载荷字节: a.载荷.length, 变换后字节: b.载荷.length, 载荷是否被改写: 载荷变了,
    叶子数: [a.leaves.length, b.leaves.length], 不同的叶子,
    出口是否逐字相同: 出口一样, 出口长度: [a.出口 ? a.出口.length : null, b.出口 ? b.出口.length : null],
  }
  代价.push(row)
  console.log('  · ' + sample.id.padEnd(10) + ' ' + sample.说明
    + ' · 载荷被改写 ' + (载荷变了 ? '是' : '否')
    + ' · 叶子 ' + a.leaves.length + '/' + b.leaves.length + ' 处不同 ' + 不同的叶子.length
    + ' · 出口逐字相同 ' + (出口一样 ? '是' : '否'))
  for (const 差 of 不同的叶子.slice(0, 4)) {
    console.log('      第 ' + 差.at + ' 个叶子: 原样=' + JSON.stringify(差.原样) + ' → 变换后=' + JSON.stringify(差.变换后))
  }
}

// ---------- 收口 ----------
const dbAfter = await readArticle()
const unchanged = dbAfter.revision === dbBefore.revision && dbAfter.updatedAt === dbBefore.updatedAt
console.log('\n库核对：改前 revision=' + dbBefore.revision + ' / 改后 revision=' + dbAfter.revision
  + ' · updatedAt ' + (unchanged ? '逐字未变 ✅' : '变了 ❌'))

const 结论 = {
  源码块: { 文件: SRC_FILE, 字节: BLOCK.length, sha256前16位: BLOCK_SHA, 注入自检: 注入自检, 自检通过 },
  暴露面: {
    口径一_合成网页: {
      说明: '把「网页上能写出段首缩进」的写法穷举成 7 种，真 Ctrl+C 复制，读剪贴板 text/html 原文',
      边界: '这是「浏览器复制网页时的序列化行为」的替身，不是「网页长什么样」的样本；'
        + '本支没有采样真实互联网页面，「网上有多少网页这么写」给不出数字，也不编',
      逐种: forms,
      看得见缩进的种数: forms.filter((row) => row.源网页看得见缩进).length,
      剪贴板带制表符的种数: forms.filter((row) => row.剪贴板里带制表符 === true).length,
      会被修法改写的种数: forms.filter((row) => row.会被修法改写 > 0).length,
      真暴露_两者同时成立: 真暴露.map((row) => row.id),
      看得见但剪贴板里不是制表符或空格: 看得见但不丢.map((row) => row.id),
    },
    真实剪贴板载荷过一遍修法: 真实载荷代价,
    口径二_真实语料: 语料,
  },
  落点侧: { 真CtrlV可用: realOk, 逐种: results,
    甲类里空白字符类缩进被丢掉的: 甲类丢了.map((row) => row.id),
    甲类里CSS类缩进被丢掉的: 甲类丢了的CSS.map((row) => row.id) },
  代价: { 源码级: { ...源码行, whiteSpace机制: 模型源码行 }, 语料级: { 文件数: 语料结果.length, 被改写文件: 被改写, 命中总数, 镜像自检全部一致: 镜像不一致.length === 0, 落在table_pre_code_katex_svg: 落在表格或代码或公式或SVG里 }, DOM级: 代价 },
  dbBefore, dbAfter, dbUnchanged: unchanged,
}
writeFileSync(resolve(BROWSER_OUT, 'r28_paste_html_probe.json'), JSON.stringify(结论, null, 1), 'utf8')
console.log('\n产物:', resolve(BROWSER_OUT, 'r28_paste_html_probe.json'))

await app.close()
await web.close()
close()
if (!unchanged) { console.error('\n❌ 库里的 revision/updatedAt 变了'); process.exit(1) }
