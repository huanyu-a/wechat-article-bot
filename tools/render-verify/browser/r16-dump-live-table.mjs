/**
 * 第十六轮 · 单样例的**实时 DOM 单点诊断**（表格专用）。
 *
 * 为什么需要它：`run-r16-browser.mjs` 量的是 `getComputedStyle` 的**结果**，
 * 量不出「属性到底有没有落在实时 DOM 上」——第 6 条（列宽）与第 11 条（表级声明）
 * 的症状都是「实时 DOM 上没有这段声明」，只看计算结果会误判成「样式没生效」。
 * 这一支把 `.ProseMirror` 里那张 `<table>` 的 `style` 属性、`<colgroup>` 各列的宽度、
 * 每个 `<td>` 的实际宽度，以及 `editor.state.doc` 里 table 节点的 attrs 一起打出来。
 *
 * 用法（先跑 `gen/round16_editor_reported.py` 与探针 vite build，见
 * `docs/dev/render-verification.md` §3.12 U1/U2）：
 *     node tools/render-verify/browser/r16-dump-live-table.mjs r16-06-title-da01 [more ids...]
 * 产物：stdout（JSON），不进文件。
 * `docs/dev/known-issues-handoff.md` §3.20② 第 6 / 11 条的 `liveCols` / `borderCollapse` 证据出自这里。
 */
import { readFileSync, existsSync } from 'node:fs'
import { OUT, PROBE_DIST } from '../paths.mjs'
import { createServer } from 'node:http'
import { resolve, extname, join } from 'node:path'
import { launchBrowser, openPage } from './cdp.mjs'

const SET = 'r16'
const SETDIR = resolve(OUT, SET)

const meta = JSON.parse(readFileSync(join(SETDIR, SET + '.json'), 'utf8'))
const wanted = process.argv.slice(2)
if (!wanted.length) { console.error('用法: node tools/render-verify/browser/r16-dump-live-table.mjs <id> [more ids...]'); process.exit(2) }
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

const { client, close } = await launchBrowser({ port: 9348 })
const page = await openPage(client)
await page.send('Emulation.setDeviceMetricsOverride', { width: 1600, height: 1200, deviceScaleFactor: 1, mobile: false })
await page.navigate(origin + '/probe_r16.html')
await page.evaluate(`window.mountAll(${JSON.stringify(payload)})`)

const report = await page.evaluate(`JSON.stringify(${JSON.stringify(wanted)}.map((id) => {
  const row = document.querySelector('[data-sample="' + id + '"]')
  const canvas = row.querySelector('.probe-canvas[data-variant="after"]')
  const root = canvas.querySelector('.ProseMirror')
  const table = root.querySelector('table')
  const editor = window.__r16Editors[id]
  let attrs = null
  try {
    editor.state.doc.descendants((node) => {
      if (!attrs && node.type.name === 'table') attrs = { ...node.attrs }
      return !attrs
    })
  } catch (error) { attrs = { error: String(error) } }
  return {
    id,
    nodeAttrs: attrs,
    editorIsEditable: editor.isEditable,
    viewDomClassName: editor.view.dom.className,
    hasCustomNodeView: Boolean(editor.view.docView && editor.view.docView.children && editor.view.docView.children.find((child) => child.node && child.node.type.name === 'table' && child.constructor && child.constructor.name !== 'NodeViewDesc')),
    liveTableStyle: table ? table.getAttribute('style') : null,
    liveTableAttrs: table ? [...table.attributes].map((attribute) => attribute.name) : null,
    liveTableParentClass: table && table.parentElement ? table.parentElement.className : null,
    liveComputed: table ? {
      borderCollapse: getComputedStyle(table).borderCollapse,
      borderSpacing: getComputedStyle(table).borderSpacing,
      minWidth: getComputedStyle(table).minWidth,
      tableLayout: getComputedStyle(table).tableLayout,
    } : null,
    liveCols: table ? [...table.querySelectorAll('colgroup > col')].map((col) => col.getAttribute('style')) : [],
    liveCellWidths: table ? [...table.querySelectorAll('td')].map((td) => getComputedStyle(td).width) : [],
    liveCellInline: table ? [...table.querySelectorAll('td')].slice(0, 6).map((td) => td.getAttribute('style')) : [],
  }
}))`)

console.log(JSON.stringify(JSON.parse(report), null, 1))
await page.close(); close(); server.close()
