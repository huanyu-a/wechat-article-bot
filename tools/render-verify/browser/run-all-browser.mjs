/**
 * 全量 79 样例的真实浏览器核查（第七轮）。
 *
 * 与 `run-browser.mjs`（14 组「改前 / 改后」代表性样例）的分工：
 *   - 那份回答「这 10 项修复在真浏览器里成立吗」；
 *   - 这份回答「79 个组件**每一个**在真浏览器里画出来了吗」。
 *
 * 一行一个样例，左栏是渲染服务自己的产物（参照），右栏是编辑器往返后的结果。
 * 每行一张截图（`shots/all/<id>.png`），截图里两栏同框、表头带字数与「有盒子的元素数」，
 * 所以图本身就是断言的一部分，不是一张看不出所以然的全页图。
 *
 * 用法：
 *     (cd webui && npx vite build --config ../tools/render-verify/browser/vite.config.mjs)
 *     node tools/render-verify/browser/run-all-browser.mjs
 *     node tools/render-verify/browser/run-all-browser.mjs --selftest   # 闸自检，不开浏览器、不写产物
 * 产物：target/probe/browser/all_result.json、shots/all/<id>.png
 *
 * 退出码（**第二十九轮 E1 补**，此前这一支没有退出码——`2/9 EXIT=0` 与「79 条都收到了」无关）：
 *   0 = 失败项 0；1 = 条数不是 79、有截图没截到、有图没加载出来。判据见 `gates.mjs` 的 `收集器判据`。
 */
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs'
import { OUT, BROWSER_OUT, PROBE_DIST } from '../paths.mjs'
import { createServer } from 'node:http'
import { resolve, join, extname } from 'node:path'
import { launchBrowser, openPage } from './cdp.mjs'
import { 判据, 收集器判据, 收集器自检 } from '../gates.mjs'

const ARGS = process.argv.slice(2)

const PROBE = OUT
const COMPONENTS = resolve(PROBE, 'components')
const DIST = PROBE_DIST
const SHOTS = resolve(BROWSER_OUT, 'shots/all')

if (ARGS.includes('--selftest')) {
  // 自检不需要探针 dist，也不开浏览器：只读样例清单（应有条数）+ 上一轮的产物（好输入）。
  const 清单 = JSON.parse(readFileSync(resolve(PROBE, 'component_matrix.json'), 'utf8'))
  const 应有 = (清单.rows || 清单).length
  const 存档 = resolve(BROWSER_OUT, 'all_result.json')
  const 已跑 = existsSync(存档) ? JSON.parse(readFileSync(存档, 'utf8')) : null
  process.exitCode = 收集器自检('2/9 run-all-browser', '样例', 应有, 已跑
    ? { 条数: 已跑.samples.length, 截图失败: 已跑.shotFailures,
        图片失败: 已跑.page.images.failed, 图片总数: 已跑.page.images.total }
    : { 条数: 应有, 截图失败: 0, 图片失败: 0, 图片总数: 0 })
} else {

if (!existsSync(DIST)) throw new Error('dist 不存在，先跑 vite build')
if (!existsSync(join(DIST, 'probe_all.html'))) throw new Error('dist/probe_all.html 不存在，vite 入口没配全')

// 样例清单以 component_matrix.json 为准（它是 79 条的唯一出处），顺序也照它来。
const matrix = JSON.parse(readFileSync(resolve(PROBE, 'component_matrix.json'), 'utf8'))
const rows = matrix.rows || matrix
const payload = rows.map((row) => ({
  id: row.id,
  syntax: row.syntax,
  category: row.category,
  note: `渲染服务产物 ${row.chars} 字符 / warnings ${row.warnings.length} 条 / svg ${row.svg} / katex ${row.katex}`,
  html: readFileSync(join(COMPONENTS, row.id + '.html'), 'utf8'),
}))
console.log('样例数:', payload.length)

const 判 = 收集器判据(rows.length, '样例')

// ---------- 1. 静态服务 ----------
const MIME = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.png': 'image/png', '.svg': 'image/svg+xml', '.woff2': 'font/woff2' }
const server = createServer((request, response) => {
  const path = request.url.split('?')[0]
  const file = resolve(DIST, '.' + (path === '/' ? '/probe_all.html' : path))
  if (!file.startsWith(DIST) || !existsSync(file)) { response.writeHead(404); response.end('not found'); return }
  response.writeHead(200, { 'Content-Type': MIME[extname(file)] || 'application/octet-stream' })
  response.end(readFileSync(file))
})
await new Promise((done) => server.listen(0, '127.0.0.1', done))
const origin = `http://127.0.0.1:${server.address().port}`
console.log('探针页:', origin, '/probe_all.html')

mkdirSync(SHOTS, { recursive: true })

// ---------- 2. 真浏览器 ----------
const { client, version, close } = await launchBrowser({ port: 9336 })
console.log('浏览器:', version.Browser)
const page = await openPage(client)
// 视口高一点，减少 captureBeyondViewport 要合成的行数
await page.send('Emulation.setDeviceMetricsOverride', { width: 1600, height: 1200, deviceScaleFactor: 1, mobile: false })
await page.navigate(origin + '/probe_all.html')

const fontsReadyBeforeMount = await page.evaluate(`(async () => { await document.fonts.ready; return document.fonts.check('16px KaTeX_Main') })()`)
console.log('KaTeX webfont 已加载（挂载前）:', fontsReadyBeforeMount)

const started = Date.now()
const results = await page.evaluate(`window.mountAll(${JSON.stringify(payload)})`)
console.log('渲染完成，样例数:', results.length, '耗时', Math.round((Date.now() - started) / 1000) + 's')

// 图片解码完再截图。轮播图（`<svg><foreignObject><img>`）里的 banner 是公网图，
// 不等就会拍到一张空白——第一次跑就是这样，量到的 SVG 盒子是对的、截图却是白的。
//
// 两处坑，都是量出来才发现的：
//   1. **ProseMirror 自己会插 `<img class="ProseMirror-separator">`**（给空行内内容一个光标位），
//      它没有 src，`naturalWidth` 恒为 0——不过滤掉就会把「PM 的内部占位」记成「图没加载」。
//      所以统计口径是 `document.images` 里**有 src 属性**的那些。
//   2. 79 行里同一张公网图会被请求几十次，不加图片会并发断连；重试一次，仍然失败的才记下来。
const imageState = await page.evaluate(`(async () => {
  const real = () => [...document.images].filter((image) => image.getAttribute('src'))
  const settle = (list) => Promise.race([
    Promise.all(list.map((image) => image.complete ? null : new Promise((done) => {
      image.addEventListener('load', done, { once: true })
      image.addEventListener('error', done, { once: true })
    }))),
    new Promise((done) => setTimeout(done, 25000)),
  ])
  const broken = () => real().filter((image) => image.complete && image.naturalWidth === 0)
  await settle(real())
  // 重试：把 src 原样再赋一次，触发一次新的请求
  for (const image of broken()) { const src = image.src; image.src = ''; image.src = src }
  await new Promise((done) => setTimeout(done, 400))
  await settle(broken())
  const all = real()
  const perSample = {}
  for (const row of document.querySelectorAll('.probe-row')) {
    const images = [...row.querySelectorAll('img')].filter((image) => image.getAttribute('src'))
    perSample[row.dataset.sample] = {
      total: images.length,
      loaded: images.filter((image) => image.complete && image.naturalWidth > 0).length,
      sizes: [...new Set(images.map((image) => image.naturalWidth + 'x' + image.naturalHeight))],
    }
  }
  return JSON.stringify({
    total: all.length,
    loaded: all.filter((image) => image.complete && image.naturalWidth > 0).length,
    failed: all.filter((image) => image.complete && image.naturalWidth === 0).length,
    failedUrls: [...new Set(broken().map((image) => image.currentSrc || image.src))],
    // 顺带记一下：ProseMirror 给空行内内容插的光标占位图有几张（不是「图没加载」）
    proseMirrorSeparators: document.querySelectorAll('img.ProseMirror-separator').length,
    perSample,
  })
})()`)
console.log('图片等待结果:', imageState)
const 图片 = JSON.parse(imageState)
await new Promise((done) => setTimeout(done, 800))

// ---------- 3. 逐行截图（裁到那一行，左右两栏同框） ----------
let shotFailures = 0
for (const result of results) {
  const clip = await page.evaluate(`(() => {
    const row = document.querySelector('[data-sample="${result.id}"]')
    if (!row) return 'null'
    const r = row.getBoundingClientRect()
    return JSON.stringify({ x: r.x, y: r.y + window.scrollY, width: r.width, height: r.height })
  })()`)
  if (clip === 'null') { console.log('  截图跳过（找不到行）:', result.id); shotFailures++; continue }
  const box = JSON.parse(clip)
  try {
    const shot = await client.send('Page.captureScreenshot', {
      format: 'png',
      // 必须带 captureBeyondViewport：行在视口之外时，只给 clip 会拍到一张空白页
      // （第一次跑就是这样：10 张截图字节数完全相同，全是背景色）。
      captureBeyondViewport: true,
      // scale 0.35：79 行里最长的一行（engage-card）到 1:1 时能到 3 MB 以上，
      // 缩到 0.35 仍然看得清两栏的排版与文字。
      clip: { x: Math.max(0, box.x), y: Math.max(0, box.y), width: box.width, height: box.height, scale: 0.35 },
    }, page.sessionId)
    writeFileSync(join(SHOTS, result.id + '.png'), Buffer.from(shot.data, 'base64'))
  } catch (error) {
    console.log('  截图失败:', result.id, error.message)
    shotFailures++
  }
}
console.log('截图完成:', SHOTS, '失败', shotFailures)

const meta = await page.evaluate(`JSON.stringify({
  userAgent: navigator.userAgent,
  viewport: [window.innerWidth, window.innerHeight],
  // 挂载之后才量：挂载前页面上没有公式，浏览器根本不会去下载 KaTeX 的 webfont，
  // 那一刻 check() 必然是 false（第一次跑就是这样，差点误判成「字体没加载」）。
  katexFontLoaded: document.fonts.check('16px KaTeX_Main'),
  katexCssHref: [...document.querySelectorAll('link[rel=stylesheet]')].map(l => l.href),
  pageHeight: Math.round(document.documentElement.scrollHeight),
})`)

writeFileSync(resolve(BROWSER_OUT, 'all_result.json'),
  JSON.stringify({
    browser: version.Browser,
    page: { ...JSON.parse(meta), katexFontLoadedBeforeMount: fontsReadyBeforeMount, images: JSON.parse(imageState) },
    shots: results.length, shotFailures, samples: results,
  }, null, 1), 'utf8')

// ---------- 4. 控制台摘要 ----------
for (const sample of results) {
  const r = sample.reference, a = sample.after
  console.log(`\n${sample.id}  [${sample.syntax}]`)
  console.log(`  参照: 文字 ${r.textLength} · 盒子 ${r.visibleNodeCount}/${r.nodeCount} · svg ${r.svgTotal}`
    + ` · katex ${r.katexTotal}/${r.katexDisplayTotal} · 渐变 ${r.gradientElements} · 圆点 ${r.bulletSpans}`
    + ` · 高亮span ${r.codeHighlightSpans}`)
  console.log(`  编辑器: 文字 ${a.textLength} · 盒子 ${a.visibleNodeCount}/${a.nodeCount} · svg ${a.svgTotal}`
    + ` · katex ${a.katexTotal}/${a.katexDisplayTotal} · 渐变 ${a.gradientElements} · 圆点 ${a.bulletSpans}`
    + ` · 高亮span ${a.codeHighlightSpans}`)
}

await page.close()
close()
server.close()
console.log('\n结果:', resolve(BROWSER_OUT, 'all_result.json'))

process.exitCode = 判据('2/9 run-all-browser（全量 ' + rows.length + ' 样例 · 截图失败 ' + shotFailures
  + ' · 图片失败 ' + 图片.failed + '）', 判, {
  条数: results.length, 截图失败: shotFailures,
  图片失败: 图片.failed, 图片总数: 图片.total,
}) ? 1 : 0
}
