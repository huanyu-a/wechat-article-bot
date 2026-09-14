/**
 * 第十六轮 · 宽度对齐复验（用来**证伪**一类「编辑器把行撑高了」的误判）。
 *
 * 为什么需要它：`run-r16-browser.mjs` 的左右两栏**内层内容盒并不等宽**——
 *   参照栏 `.probe-canvas` **自己就是** `.ProseMirror`（无嵌套，`padding: 0`）；
 *   编辑器栏是 `.probe-canvas > .ProseMirror`（嵌套一层，`.ProseMirror` 另有 `padding: 28px 1px`）。
 * 于是编辑器的可用宽比参照窄 ~36px。凡是**卡在折行边界上**的单元格，编辑器会多折一行，
 * 行高看起来「被撑高」了——这不是布局缺陷，是量具本身宽度不等。
 *
 * 判据是**双向**的（单向不够，容易自我说服）：
 *   ① 把编辑器内层 `.ProseMirror` 的 `clientWidth` 对齐到参照栏 → 该行应掉回参照的高度；
 *   ② 把参照栏收窄到编辑器的 `clientWidth` → 该行应涨到编辑器的高度。
 * 两个方向都落在同一组数字上，才能判定「宽度假阳性」；只做①可能是巧合。
 *
 * 用法（先跑 `gen/round16_editor_reported.py` 与探针 vite build，见
 * `docs/dev/render-verification.md` §3.12 U1/U2）：
 *     node tools/render-verify/browser/r16-width-equiv-test.mjs [id]
 * 默认 `r16-09-table-card`（第 9 条 `:::table style="card"` 就是在这条上定的案）。
 * 产物：stdout（JSON）。
 * `docs/dev/known-issues-handoff.md` §3.20⑤c 的取证出自这里。
 */
import { readFileSync, existsSync } from 'node:fs'
import { OUT, PROBE_DIST } from '../paths.mjs'
import { createServer } from 'node:http'
import { resolve, extname, join } from 'node:path'
import { launchBrowser, openPage } from './cdp.mjs'

const SET = 'r16'
const SETDIR = resolve(OUT, SET)
const ID = process.argv[2] || 'r16-09-table-card'

const meta = JSON.parse(readFileSync(join(SETDIR, SET + '.json'), 'utf8'))
const payload = meta.cases.filter((item) => item.id === ID).map((item) => ({
  id: item.id, syntax: item.name, category: 'x', note: '', html: readFileSync(join(SETDIR, item.id + '.html'), 'utf8'),
}))
if (!payload.length) { console.error('未找到样例:', ID, '（可选 id 见 target/probe/r16/r16.json）'); process.exit(2) }

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

const { client, close } = await launchBrowser({ port: 9355 })
const page = await openPage(client)
await page.send('Emulation.setDeviceMetricsOverride', { width: 1600, height: 1200, deviceScaleFactor: 1, mobile: false })
await page.navigate(origin + '/probe_r16.html')
await page.evaluate(`window.mountAll(${JSON.stringify(payload)})`)

const report = await page.evaluate(`(() => {
  const row = document.querySelector('[data-sample="${ID}"]')
  const refRoot = row.querySelector('.probe-canvas[data-variant="reference"]')
  const edRoot = row.querySelector('.probe-pane.after .ProseMirror')
  const heights = (root) => [...root.querySelectorAll('tr')].map((tr) => Math.round(tr.getBoundingClientRect().height * 100) / 100)
  const boxes = (root) => ({
    clientWidth: root.clientWidth,
    padding: getComputedStyle(root).padding,
    tableWidth: Math.round(root.querySelector('table').getBoundingClientRect().width * 100) / 100,
    rowWidth: Math.round(root.querySelector('tr').getBoundingClientRect().width * 100) / 100,
  })

  const out = { id: ${JSON.stringify(ID)}, before: {
    reference: { ...boxes(refRoot), rows: heights(refRoot) },
    editor: { ...boxes(edRoot), rows: heights(edRoot) },
  } }

  // ① 编辑器内层内容盒 → 对齐参照栏
  const wasEd = edRoot.style.width
  edRoot.style.width = refRoot.clientWidth + 'px'
  out.alignEditorToReference = { editor: { ...boxes(edRoot), rows: heights(edRoot) } }
  edRoot.style.width = wasEd

  // ② 参照栏内容盒 → 收窄到编辑器栏
  const wasRef = refRoot.style.width
  refRoot.style.width = edRoot.clientWidth + 'px'
  out.narrowReferenceToEditor = { reference: { ...boxes(refRoot), rows: heights(refRoot) } }
  refRoot.style.width = wasRef

  return JSON.stringify(out, null, 1)
})()`)

console.log(report)
await page.close(); close(); server.close()
