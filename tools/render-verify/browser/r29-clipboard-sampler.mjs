/**
 * 第二十九轮 C · **真实来源的剪贴板 HTML 采样**：对一批公开网页做「真选中 → 真 Ctrl+C → 读剪贴板 text/html」，
 * 把每一份载荷落盘，并用 `round29_clipboard_html_check.mjs` 的统一判据逐份判定。
 *
 * 为什么不能只靠合成网页（第二十八轮 U13 那 7 种写法）：那一轮证明的是「**我造的**写法里哪种会触发」，
 * 回答不了「**真实网页**复制进来的载荷长什么样」。本支补的就是这一格：**浏览器实际交付到剪贴板上的字节**。
 *
 * 采样口径（原样照录，不做加工）：
 *   - 取 `<!--StartFragment-->…<!--EndFragment-->` 之间的原文（没有标记就取全文）；
 *   - 判据用 `round29_clipboard_html_check.mjs` 的 `checkClipboardHtml()`，与实现同构；
 *   - 载荷原文落 `target/probe/r29/clip/`，可离线复判。
 *
 * 另带一组**合成对照组**（同一支浏览器、同一套复制动作），用来把「判据本身会不会判」钉住：
 * 其中 `lead-sp-normal` 是「段首两个半角空格、没声明 white-space」——如果连它都采不到 `[ \t]`，
 * 说明**浏览器在写剪贴板那一步**就把这段空白并掉了（不是编辑器丢的），这条边界必须写进结论。
 *
 * 用法：
 *     node tools/render-verify/browser/r29-clipboard-sampler.mjs
 *     node tools/render-verify/browser/r29-clipboard-sampler.mjs --url <网址> [--选择器 <css>]
 * 产物：target/probe/browser/r29_clip_samples.json + target/probe/r29/clip/*.html
 */
import { writeFileSync, mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { launchBrowser, openPage } from './cdp.mjs'
import { BROWSER_OUT, ROOT, API } from '../paths.mjs'
import { checkClipboardHtml, summaryLine } from '../round29_clipboard_html_check.mjs'

const PORT = 9373
const CLIP_DIR = resolve(ROOT, 'target/probe/r29/clip')
mkdirSync(CLIP_DIR, { recursive: true })
const sleep = (ms) => new Promise((done) => setTimeout(done, ms))

const ARGS = process.argv.slice(2)
const argOf = (name) => { const at = ARGS.indexOf(name); return at >= 0 ? ARGS[at + 1] : null }
const 单点 = argOf('--url')

/** 合成对照组：写在本地文件里、走 `file://` 打开，与真实网页同一套复制动作。 */
const CONTROL_PAGE = `<!doctype html><html><head><meta charset="utf-8"><title>r29 对照页</title>
<style>body{font:16px/1.6 system-ui,sans-serif;margin:0;padding:8px} p,div,pre{margin:0 0 6px}</style>
</head><body>
<div id="c1"><p>  LEAD-SP-NORMAL</p></div>
<div id="c2"><p>MID-SP  IN-LINE</p></div>
<div id="c3"><p style="white-space:pre-wrap">  LEAD-SP-PREWRAP</p></div>
<div id="c4"><pre>  LEAD-SP-PRE-TAG</pre></div>
<div id="c5"><p>　　LEAD-IDEOGRAPHIC</p></div>
<div id="c6"><p>&nbsp;&nbsp;LEAD-NBSP</p></div>
<div id="c7"><p>ALPHA</p><p>  LEAD-SECOND-P</p></div>
<div id="c8"><ul><li>  LEAD-LI</li></ul></div>
<div id="c9"><div>  LEAD-DIV</div></div>
<div id="c10" style="white-space:pre-line"><p>  LEAD-PRE-LINE</p></div>
<div id="c11"><p>ALPHA</p><p>	LEAD-TAB-SECOND-P</p></div>
</body></html>`
const CONTROL_FILE = resolve(ROOT, 'target/probe/r29/clip/_control.html')
writeFileSync(CONTROL_FILE, CONTROL_PAGE, 'utf8')

// 对照组走**应用源 + `document.write`**（第二十八轮同款）：`file://` 上 `navigator.clipboard.read()`
// 拿不到权限，兜底路径会直接挂住；换成 `http://127.0.0.1:8081` 这个安全上下文才读得到。
const CONTROL_内联 = true
const CONTROLS = [
  { id: 'control/lead-sp-normal', 说明: '段首 2 个半角空格，未声明 white-space（**判据的正例**）', 选择器: '#c1 > p' },
  { id: 'control/mid-sp', 说明: '段中 2 个空格（对照：不该被判成段首）', 选择器: '#c2 > p' },
  { id: 'control/lead-sp-prewrap', 说明: '段首 2 个空格 + white-space:pre-wrap（应**不触发**）', 选择器: '#c3 > p' },
  { id: 'control/lead-sp-pre-tag', 说明: '`<pre>` 里的段首空格（应**不触发**）', 选择器: '#c4 > pre' },
  { id: 'control/lead-ideographic', 说明: '段首全角空格 U+3000（不在 `[ \\t]` 里）', 选择器: '#c5 > p' },
  { id: 'control/lead-nbsp', 说明: '段首 `&nbsp;`（不在 `[ \\t]` 里）', 选择器: '#c6 > p' },
  { id: 'control/lead-sp-second-block', 说明: '两个块一起选，第二块段首 2 空格（**探「浏览器会不会并」的边界**）', 选择器: '#c7' },
  { id: 'control/lead-li', 说明: '列表项段首 2 空格', 选择器: '#c8 > ul' },
  { id: 'control/lead-div', 说明: '块级 div 段首 2 空格', 选择器: '#c9 > div' },
  { id: 'control/lead-pre-line', 说明: '`white-space: pre-line` 下的段首 2 空格（`pre*` 家族，应**不触发**）', 选择器: '#c10 > p' },
  { id: 'control/lead-tab-second-block', 说明: '两个块一起选，第二块段首 **制表符**', 选择器: '#c11' },
].map((row) => ({ ...row, url: '(应用源 + document.write)', 内联: CONTROL_PAGE, 说明: row.说明 + '｜对照组' }))

/** 真实网页：优先选正文容器，选不到就选 `body`。 */
const SITES = [
  { id: 'example.com', url: 'https://example.com/', 选择器: 'body' },
  { id: 'mdn-js-guide', url: 'https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide', 选择器: '#content' },
  { id: 'mdn-array', url: 'https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array', 选择器: '#content' },
  { id: 'wikipedia-zh-js', url: 'https://zh.wikipedia.org/wiki/JavaScript', 选择器: '#mw-content-text' },
  { id: 'wikipedia-en-html', url: 'https://en.wikipedia.org/wiki/HTML', 选择器: '#mw-content-text' },
  { id: 'nodejs-learn', url: 'https://nodejs.org/en/learn/getting-started/introduction-to-nodejs', 选择器: 'main' },
  { id: 'iana-example', url: 'https://www.iana.org/help/example-domains', 选择器: 'main' },
  { id: 'python-docs', url: 'https://docs.python.org/3/tutorial/introduction.html', 选择器: 'div.body' },
]

const TARGETS = 单点
  ? [{ id: 'single', url: 单点, 选择器: argOf('--选择器') || 'body', 说明: '命令行指定' }]
  : [...CONTROLS, ...SITES.map((site) => ({ ...site, 说明: '公开网页' }))]

const { client, version, close } = await launchBrowser({ port: PORT })
console.log('浏览器:', version.Browser, '· 采样数:', TARGETS.length)

// ⚠️ 剪贴板权限必须先授，否则 `navigator.clipboard.read()` 抛 `NotAllowedError: Read permission denied`
// （第二十九轮实测：没授时前几个目标直接挂到超时，后面全部 NotAllowedError）。
// **不限定 `origin`**：CDP 的语义是「不给 origin 就对所有源生效」。真实网页那 8 条各自的源都不同，
// 逐个授不现实；这是一次性 headless 探针 profile，放宽只影响本支自己。
// 与第二十八轮的粘贴探针同款（`r28-paste-html-probe.mjs` 只授了 API 那一个源，因为它只在应用里复制）。
try {
  await client.send('Browser.grantPermissions', {
    permissions: ['clipboardReadWrite', 'clipboardSanitizedWrite'],
  })
} catch (error) { console.log('⚠️ 剪贴板权限没授上（退回到只授应用源）:', String(error.message || error)) }

const page = await openPage(client)
await page.send('Runtime.enable')
await page.send('Page.enable')
await page.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
await page.send('Page.addScriptToEvaluateOnNewDocument', {  source: `(() => {
    window.__copies = [];
    document.addEventListener('copy', (event) => {
      try {
        window.__copies.push({
          html: event.clipboardData ? event.clipboardData.getData('text/html') : null,
          text: event.clipboardData ? event.clipboardData.getData('text/plain') : null,
        });
      } catch (error) { window.__copies.push({ error: String(error) }); }
    });
    return true;
  })()`,
})

const rows = []
for (const target of TARGETS) {
  const row = { id: target.id, url: target.url, 选择器: target.选择器 || null, 说明: target.说明 || '' }
  try {
    if (target.内联) {
      await page.navigate(API + '/login')
      await page.evaluate(`document.open(); document.write(${JSON.stringify(target.内联)}); document.close(); true`)
      await sleep(300)
    } else {
      const loaded = client.once('Page.loadEventFired', 45000)
      await page.send('Page.navigate', { url: target.url })
      await loaded.catch(() => {})
      await page.evaluate(`document.fonts.ready.then(() => true)`).catch(() => {})
      await sleep(400)
    }
    await client.send('Page.bringToFront', {}, page.sessionId)
    // 选中正文容器（选不到就退回 body）
    const picked = await page.evaluate(`(() => {
      const sel = ${JSON.stringify(target.选择器 || 'body')};
      const el = document.querySelector(sel) || document.body;
      const range = document.createRange();
      range.selectNodeContents(el);
      const selection = window.getSelection();
      selection.removeAllRanges(); selection.addRange(range);
      return JSON.stringify({ tag: el.tagName.toLowerCase(), 字符数: (el.textContent || '').length });
    })()`)
    row.选中 = JSON.parse(picked)
    // 同一时刻把**选区里的原文**也记下来：用来分辨「这段空白是被我选漏了」还是
    // 「浏览器在写剪贴板那一步就并掉了」——前者选区文本也没有空白，后者选区有、剪贴板没有。
    row.选区原文前20 = JSON.parse(await page.evaluate(
      `JSON.stringify((window.getSelection() ? window.getSelection().toString() : '').slice(0, 20))`))
    await sleep(150)
    for (const type of ['rawKeyDown', 'keyUp']) {
      await page.send('Input.dispatchKeyEvent', {
        type, modifiers: 2, key: 'c', code: 'KeyC', windowsVirtualKeyCode: 67, nativeVirtualKeyCode: 67,
      })
    }
    await sleep(500)
    const captured = JSON.parse(await page.evaluate(
      `JSON.stringify((window.__copies && window.__copies.length) ? window.__copies[window.__copies.length - 1] : null)`))
    // ⚠️ 兜底：headless 下 copy 事件里的 `clipboardData.getData('text/html')` 实测**恒为空串**
    // （第二十八轮那 7 份载荷也全部来自这条兜底，见其产物的 `剪贴板取自` 字段）。
    // `navigator.clipboard.read()` 在应用源（127.0.0.1，属安全上下文）上能读到系统剪贴板里
    // 刚被 Ctrl+C 写进去的那一份；加超时是因为没授到权限时它不 reject、只挂住。
    let viaClipboard = null
    if (!captured || !captured.html) {
      try {
        viaClipboard = await Promise.race([
          page.evaluate(`(async () => {
            try {
              const items = await navigator.clipboard.read();
              for (const item of items) {
                if (item.types.includes('text/html')) return await (await item.getType('text/html')).text();
              }
              return null;
            } catch (error) { return 'ERR:' + String(error) }
          })()`),
          sleep(5000).then(() => 'ERR:clipboard.read() 超时'),
        ])
      } catch (error) { viaClipboard = 'ERR:' + String(error) }
    }
    const rawHtml = (captured && captured.html) || (typeof viaClipboard === 'string' && !viaClipboard.startsWith('ERR:') ? viaClipboard : null)
    row.剪贴板取自 = captured && captured.html ? 'copy 事件里的 clipboardData' : (rawHtml ? 'navigator.clipboard.read()' : '拿不到')
    row.剪贴板HTML长度 = rawHtml ? rawHtml.length : 0
    row.纯文本长度 = captured && captured.text ? captured.text.length : 0
    if (rawHtml) {
      const file = resolve(CLIP_DIR, target.id.replace(/[^a-z0-9._-]/gi, '_') + '.html')
      writeFileSync(file, rawHtml, 'utf8')
      row.载荷文件 = file
      row.判定 = checkClipboardHtml(rawHtml, { 名称: target.id })
    } else {
      row.错误 = (typeof viaClipboard === 'string' && viaClipboard.startsWith('ERR:') ? viaClipboard : null)
        || (captured ? (captured.error || '拷贝事件里没有 text/html，兜底也读不到') : '没有触发 copy 事件')
    }
  } catch (error) {
    row.错误 = String(error && error.message ? error.message : error).slice(0, 200)
  }
  rows.push(row)
  console.log('  ' + (row.判定 ? summaryLine(row.判定) : ('✗ 采不到载荷：' + row.错误))
    + '   ← ' + target.id + (row.说明 ? '（' + row.说明 + '）' : ''))
}

const 有载荷 = rows.filter((row) => row.判定)
const 触发 = 有载荷.filter((row) => row.判定.触发)
const 带空白 = 有载荷.filter((row) => row.判定.段首空白处数 > 0)
console.log('\n================ 汇总 ================')
console.log('目标数 ' + rows.length + ' · 采到剪贴板 HTML 的 ' + 有载荷.length + ' 份'
  + '（采不到的 ' + (rows.length - 有载荷.length) + ' 个：' + rows.filter((r) => !r.判定).map((r) => r.id).join(', ') + '）')
console.log('  其中剪贴板里**带段首 `[ \\t]`** 的：' + 带空白.length + ' 份'
  + (带空白.length ? ' → ' + 带空白.map((r) => r.id).join(', ') : ''))
console.log('  其中**判据判「触发」**（段首 `[ \\t]` 且不含 white-space:pre*）的：' + 触发.length + ' 份'
  + (触发.length ? ' → ' + 触发.map((r) => r.id).join(', ') : ''))

// ⚠️ 单点模式（`--url`）另存一份文件名：否则一次随手采样会把上面那份完整采样的产物**覆盖掉**
// （第二十九轮踩过：拿应用页试了一发，`r29_clip_samples.json` 就从 19 条变成 1 条）。
const OUT_FILE = resolve(BROWSER_OUT, 单点 ? 'r29_clip_samples_single.json' : 'r29_clip_samples.json')
writeFileSync(OUT_FILE, JSON.stringify({
  browser: version.Browser, targets: rows,
  汇总: {
    目标数: rows.length, 采到载荷: 有载荷.length, 带段首空白: 带空白.length, 触发: 触发.length,
    触发清单: 触发.map((row) => row.id),
  },
}, null, 1), 'utf8')
console.log('产物:', OUT_FILE, '· 载荷目录:', CLIP_DIR)

await page.close()
close()
