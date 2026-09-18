/**
 * D48 探针驱动器（第 40 轮）。
 *
 * 前置：`cd webui && npx vite build --config ../tools/render-verify/browser/vite.config.mjs`
 *（与全量套件同一条命令；r48-flex-span.html 已是 vite 的一个入口）。
 *
 * 用法：node tools/render-verify/browser/r48-flex-span.mjs
 * 产物：target/probe/browser/r48_flex_span.json、shots/r48-flex-span.png
 *
 * 退出码：0 = 全部判据通过；1 = 任一判据失败（判据见文件尾部 GATE 一节）。
 */
import { writeFileSync, mkdirSync, existsSync, readFileSync } from 'node:fs'
import { createServer } from 'node:http'
import { resolve, join, extname } from 'node:path'
import { BROWSER_OUT, PROBE_DIST } from '../paths.mjs'
import { launchBrowser, openPage } from './cdp.mjs'

const DIST = PROBE_DIST
if (!existsSync(join(DIST, 'r48-flex-span.html'))) {
  throw new Error('dist/r48-flex-span.html 不存在：先跑 vite build（入口见 vite.config.mjs）')
}

const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png', '.svg': 'image/svg+xml' }
const server = createServer((request, response) => {
  const path = request.url.split('?')[0]
  const file = resolve(DIST, '.' + (path === '/' ? '/r48-flex-span.html' : path))
  try {
    response.setHeader('Content-Type', MIME[extname(file)] || 'application/octet-stream')
    response.end(readFileSync(file))
  } catch {
    response.statusCode = 404
    response.end('not found')
  }
})
await new Promise((done) => server.listen(0, '127.0.0.1', done))
const origin = `http://127.0.0.1:${server.address().port}`

mkdirSync(resolve(BROWSER_OUT, 'shots'), { recursive: true })
const { client, version, close } = await launchBrowser({ port: 9345 })
console.log('浏览器:', version.Browser)
try {
  const page = await openPage(client)
  await page.navigate(`${origin}/r48-flex-span.html`)
  const raw = await page.evaluate('window.mountR48().then(JSON.stringify)')
  const results = JSON.parse(raw)
  if (!Array.isArray(results) || results.length === 0) throw new Error('探针没有产出结果（__r48Result 为空）')

  const shot = await client.send('Page.captureScreenshot', { format: 'png' }, page.sessionId)
  writeFileSync(resolve(BROWSER_OUT, 'shots/r48-flex-span.png'), Buffer.from(shot.data, 'base64'))

  // ── 判据 ──────────────────────────────────────────────────────────────────
  // G1（缺陷形态）：d48-flex-row-strong 的实时 DOM 与出口 HTML 都是每行 1 个 flex:1 span。
  // G2（对照）：d48-flex-row-plain 历史行为就是 1 栏，修复不得破坏。
  // G3（D35 守门）：badges 三连在出口里仍是 3 个独立 span。
  // G4（回归观察）：染色 + 加粗的出口仍同时含 strong 与带色 span（嵌套方向记录在案，不作硬断言）。
  const byId = Object.fromEntries(results.map((item) => [item.id, item]))
  const problems = []
  const check = (ok, message) => { if (!ok) problems.push(message) }

  const strong = byId['d48-flex-row-strong']
  check(strong.liveFlexCols.length === 1 && strong.liveFlexCols[0] === 1,
    `G1 实时 DOM flex 栏数应为 [1]，实际 ${JSON.stringify(strong.liveFlexCols)}`)
  check(strong.exitFlexCols.length === 1 && strong.exitFlexCols[0] === 1,
    `G1 出口 HTML flex 栏数应为 [1]，实际 ${JSON.stringify(strong.exitFlexCols)}`)
  const marksOrder = strong.marksOrder
  check(marksOrder.indexOf('textStyle') < marksOrder.indexOf('bold'),
    `G1 marks 键序中 textStyle 应在 bold 之前，实际 ${JSON.stringify(marksOrder)}`)

  const plain = byId['d48-flex-row-plain']
  check(plain.liveFlexCols.length === 1 && plain.liveFlexCols[0] === 1,
    `G2 对照样例 flex 栏数应为 [1]，实际 ${JSON.stringify(plain.liveFlexCols)}`)

  const badgeExit = byId['d48-badges-three'].exitHtml
  const badgeSpans = (badgeExit.match(/border-radius:\s*999px/g) || []).length
  check(badgeSpans === 3, `G3 badges 三连应保持 3 个独立 span，实际出口 ${badgeSpans} 个（${badgeExit}）`)

  const colored = byId['d48-colored-bold'].exitHtml
  // 值会被 CSSOM 规范化（#e74c3c → rgb(231, 76, 60)），判据两种形态都收
  // （render-verification.md §六 的既有教训：按无空格/原字面量 grep 会漏）。
  check(/<strong>/.test(colored) && /color:\s*(#e74c3c|rgb\(231,\s*76,\s*60\))/i.test(colored),
    `G4 染色加粗出口应同时含 strong 与色值，实际 ${colored}`)

  const out = { browser: version.Browser, results, problems }
  writeFileSync(resolve(BROWSER_OUT, 'r48_flex_span.json'), JSON.stringify(out, null, 1), 'utf8')

  for (const item of results) {
    console.log(`\n== ${item.id}`)
    console.log('   marks 键序:', JSON.stringify(item.marksOrder))
    console.log('   实时 flex 栏数:', JSON.stringify(item.liveFlexCols), '· 出口:', JSON.stringify(item.exitFlexCols))
    console.log('   出口 HTML:', item.exitHtml)
  }
  console.log('\n判据:', problems.length === 0 ? '全部通过' : problems)
  process.exitCode = problems.length === 0 ? 0 : 1
} finally {
  await close()
  server.close()
}
