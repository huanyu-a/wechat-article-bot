/**
 * 第八轮 · 组合用例的真实浏览器核查。
 *
 * 与 `run-all-browser.mjs`（79 个最小写法样例）的分工：
 *   - 那一份回答「每个组件单独一个最小写法，画出来了没有」；
 *   - 这一份回答「**嵌起来、重复出现、超长、和行内组件混排**之后，还画得出来吗」。
 *
 * 判定分两层，必须分开看，否则会把上游的问题算到编辑器头上：
 *   ① 上游层（后端产物）：`round8_combos.py` 已经量过——产物里有没有字面 `:::` 残留、有没有 SVG。
 *      这一层**不是编辑器的责任**，它坏就是上游没实现；
 *   ② 编辑器层（本脚本）：把同一份产物 setContent → getHTML 之后，两栏的文字与结构特征是否还一致。
 *
 * 页面复用 `probe_all.html` 里的 `window.mountAll`（同一套扩展集、同一套测量口径），
 * 只是喂进去的样例换成组合用例。
 *
 * 用法：
 *     (cd webui && npx vite build --config ../tools/render-verify/browser/vite.config.mjs)
 *     node tools/render-verify/browser/run-combo-browser.mjs
 *     node tools/render-verify/browser/run-combo-browser.mjs --selftest   # 闸自检，不开浏览器、不写产物
 * 产物：target/probe/browser/combo_result.json、shots/combo/<id>.png
 *
 * 退出码（**第二十九轮 E1 补**，此前这一支没有退出码——`3/9 EXIT=0` 与「17 条都收到了」无关）：
 *   0 = 失败项 0；1 = 条数不是 17、有截图没截到、有图没加载出来。判据见 `gates.mjs` 的 `收集器判据`。
 */
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs'
import { OUT, BROWSER_OUT, PROBE_DIST } from '../paths.mjs'
import { createServer } from 'node:http'
import { resolve, join, extname } from 'node:path'
import { launchBrowser, openPage } from './cdp.mjs'
import { 判据, 收集器判据, 收集器自检 } from '../gates.mjs'

const ARGS = process.argv.slice(2)

const PROBE = OUT
const COMBOS = resolve(PROBE, 'combos')
const DIST = PROBE_DIST
const SHOTS = resolve(BROWSER_OUT, 'shots/combo')

if (ARGS.includes('--selftest')) {
  const 清单 = JSON.parse(readFileSync(join(COMBOS, 'combos.json'), 'utf8'))
  const 存档 = resolve(BROWSER_OUT, 'combo_result.json')
  const 已跑 = existsSync(存档) ? JSON.parse(readFileSync(存档, 'utf8')) : null
  process.exitCode = 收集器自检('3/9 run-combo-browser', '组合用例', 清单.cases.length, 已跑
    ? { 条数: 已跑.samples.length, 截图失败: 已跑.shotFailures,
        图片失败: 已跑.page.images.failed, 图片总数: 已跑.page.images.total }
    : { 条数: 清单.cases.length, 截图失败: 0, 图片失败: 0, 图片总数: 0 })
} else {

const meta = JSON.parse(readFileSync(join(COMBOS, 'combos.json'), 'utf8'))
const payload = meta.cases.map((item) => ({
  id: item.id,
  syntax: item.label,
  category: item.family,
  note: `上游产物 ${item.backend.chars} 字符 / 字面 ::: 残留 ${item.backend.leakedColonContainer} 处`
    + ` / svg ${item.backend.svg} / katex ${item.backend.katex}`,
  html: readFileSync(join(COMBOS, item.id + '.html'), 'utf8'),
}))
console.log('组合用例数:', payload.length)

const 判 = 收集器判据(meta.cases.length, '组合用例')

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

const { client, version, close } = await launchBrowser({ port: 9342 })
console.log('浏览器:', version.Browser)
const page = await openPage(client)
await page.send('Emulation.setDeviceMetricsOverride', { width: 1600, height: 1200, deviceScaleFactor: 1, mobile: false })
await page.navigate(origin + '/probe_all.html')

const started = Date.now()
const results = await page.evaluate(`window.mountAll(${JSON.stringify(payload)})`)
console.log('渲染完成，耗时', Math.round((Date.now() - started) / 1000) + 's')

const imageState = await page.evaluate(`(async () => {
  const real = () => [...document.images].filter((image) => image.getAttribute('src'))
  const settle = (list) => Promise.race([
    Promise.all(list.map((image) => image.complete ? null : new Promise((done) => {
      image.addEventListener('load', done, { once: true }); image.addEventListener('error', done, { once: true })
    }))),
    new Promise((done) => setTimeout(done, 25000)),
  ])
  await settle(real())
  const broken = () => real().filter((image) => image.complete && image.naturalWidth === 0)
  for (const image of broken()) { const src = image.src; image.src = ''; image.src = src }
  await new Promise((done) => setTimeout(done, 400))
  await settle(broken())
  const all = real()
  return JSON.stringify({
    total: all.length,
    loaded: all.filter((image) => image.complete && image.naturalWidth > 0).length,
    failed: broken().length,
    separators: document.querySelectorAll('img.ProseMirror-separator').length,
  })
})()`)
console.log('图片:', imageState)
const 图片 = JSON.parse(imageState)
await new Promise((done) => setTimeout(done, 800))

let shotFailures = 0
for (const result of results) {
  const clip = await page.evaluate(`(() => {
    const row = document.querySelector('[data-sample="${result.id}"]')
    if (!row) return 'null'
    const r = row.getBoundingClientRect()
    return JSON.stringify({ x: r.x, y: r.y + window.scrollY, width: r.width, height: r.height })
  })()`)
  if (clip === 'null') { console.log('  截图跳过:', result.id); shotFailures++; continue }
  const box = JSON.parse(clip)
  try {
    const shot = await client.send('Page.captureScreenshot', {
      format: 'png', captureBeyondViewport: true,
      clip: { x: Math.max(0, box.x), y: Math.max(0, box.y), width: box.width, height: box.height, scale: 0.35 },
    }, page.sessionId)
    writeFileSync(join(SHOTS, result.id + '.png'), Buffer.from(shot.data, 'base64'))
  } catch (error) {
    console.log('  截图失败:', result.id, error.message)
    shotFailures++
  }
}
console.log('截图完成:', SHOTS, '失败', shotFailures)

const pageMeta = await page.evaluate(`JSON.stringify({
  userAgent: navigator.userAgent,
  viewport: [window.innerWidth, window.innerHeight],
  katexFontLoaded: document.fonts.check('16px KaTeX_Main'),
})`)

writeFileSync(resolve(BROWSER_OUT, 'combo_result.json'),
  JSON.stringify({
    browser: version.Browser,
    page: { ...JSON.parse(pageMeta), images: JSON.parse(imageState) },
    shots: results.length, shotFailures, samples: results,
  }, null, 1), 'utf8')

for (const sample of results) {
  const r = sample.reference, a = sample.after
  console.log(`\n${sample.id}  [${sample.category}]`)
  console.log(`  参照: 文字 ${r.textLength} · 盒子 ${r.visibleNodeCount}/${r.nodeCount} · svg ${r.svgTotal} · katex ${r.katexTotal}`)
  console.log(`  编辑器: 文字 ${a.textLength} · 盒子 ${a.visibleNodeCount}/${a.nodeCount} · svg ${a.svgTotal} · katex ${a.katexTotal}`)
}

await page.close()
close()
server.close()
console.log('\n结果:', resolve(BROWSER_OUT, 'combo_result.json'))

process.exitCode = 判据('3/9 run-combo-browser（' + meta.cases.length + ' 组合 · 截图失败 ' + shotFailures
  + ' · 图片失败 ' + 图片.failed + '）', 判, {
  条数: results.length, 截图失败: shotFailures, 图片失败: 图片.failed, 图片总数: 图片.total,
}) ? 1 : 0
}
