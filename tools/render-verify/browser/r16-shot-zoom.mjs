/**
 * 第十六轮 · 1:1 对照截图（`target/probe/browser/shots/r16/zoom/`）。
 *
 * 为什么需要它：`run-r16-browser.mjs` 拍的行截图是 **0.35 倍缩放**，边框、圆角、
 * 底色这类 1~2px 的差别在那张图上根本看不清，于是「到底哪一侧真缺了边框」只能靠猜。
 * 这一支把指定的样例按 **1:1** 左右两栏各拍一张，用来肉眼定案。
 *
 * 左栏 = 渲染服务的真实产物（后端 API 路径），右栏 = 同一份产物灌进真编辑器后的实时画面。
 *
 * 用法（必须先跑 `gen/round16_editor_reported.py` 与探针 vite build，见
 * `docs/dev/render-verification.md` §3.12 U1/U2）：
 *     node tools/render-verify/browser/r16-shot-zoom.mjs r16-01-changelog [more ids...]
 * 产物：target/probe/browser/shots/r16/zoom/<id>.{reference,after}.png
 * 被 `docs/dev/known-issues-handoff.md` §3.20②「1:1 对照截图」一列引用为取证依据。
 */
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs'
import { OUT, BROWSER_OUT, PROBE_DIST } from '../paths.mjs'
import { createServer } from 'node:http'
import { resolve, extname, join } from 'node:path'
import { launchBrowser, openPage } from './cdp.mjs'

const SET = 'r16'
const SETDIR = resolve(OUT, SET)
const SHOTS = resolve(BROWSER_OUT, 'shots/' + SET, 'zoom')
mkdirSync(SHOTS, { recursive: true })

const meta = JSON.parse(readFileSync(join(SETDIR, SET + '.json'), 'utf8'))
const wanted = process.argv.slice(2)
if (!wanted.length) { console.error('用法: node tools/render-verify/browser/r16-shot-zoom.mjs <id> [more ids...]'); process.exit(2) }
const payload = meta.cases.filter((item) => wanted.includes(item.id)).map((item) => ({
  id: item.id, syntax: item.name, category: 'x', note: '', html: readFileSync(join(SETDIR, item.id + '.html'), 'utf8'),
}))

const MIME = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.png': 'image/png', '.svg': 'image/svg+xml', '.woff2': 'font/woff2' }
const server = createServer((request, response) => {
  const path = request.url.split('?')[0]
  const file = resolve(PROBE_DIST, '.' + (path === '/' ? '/probe_r16.html' : path))
  if (!file.startsWith(PROBE_DIST) || !existsSync(file)) { response.writeHead(404); response.end('not found'); return }
  response.writeHead(200, { 'Content-Type': MIME[extname(file)] || 'application/octet-stream' })
  response.end(readFileSync(file))
})
await new Promise((done) => server.listen(0, '127.0.0.1', done))
const origin = `http://127.0.0.1:${server.address().port}`

const { client, close } = await launchBrowser({ port: 9349 })
const page = await openPage(client)
await page.send('Emulation.setDeviceMetricsOverride', { width: 1600, height: 1200, deviceScaleFactor: 1, mobile: false })
await page.navigate(origin + '/probe_r16.html')
await page.evaluate(`window.mountAll(${JSON.stringify(payload)})`)
// 第 3 条头像依赖外链图片；给足加载时间，否则拍到的是「没加载」而不是「渲染丢了」
await new Promise((done) => setTimeout(done, 2500))

for (const id of wanted) {
  const boxes = JSON.parse(await page.evaluate(`(() => {
    const row = document.querySelector('[data-sample="${id}"]')
    if (!row) return 'null'
    const panes = [...row.querySelectorAll('.probe-canvas')]
    return JSON.stringify(panes.map((pane) => {
      const r = pane.getBoundingClientRect()
      return { variant: pane.dataset.variant, x: r.x, y: r.y + window.scrollY, width: r.width, height: Math.min(r.height, 700) }
    }))
  })()`))
  if (!boxes) { console.log('未找到样例:', id); continue }
  for (const box of boxes) {
    const shot = await client.send('Page.captureScreenshot', {
      format: 'png', captureBeyondViewport: true,
      clip: { x: Math.max(0, box.x), y: Math.max(0, box.y), width: box.width, height: box.height, scale: 1 },
    }, page.sessionId)
    writeFileSync(join(SHOTS, `${id}.${box.variant}.png`), Buffer.from(shot.data, 'base64'))
    console.log('shot', `${id}.${box.variant}.png`, Math.round(box.width) + 'x' + Math.round(box.height))
  }
}

await page.close(); close(); server.close()
