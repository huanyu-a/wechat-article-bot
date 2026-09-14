/**
 * 第十六轮 · 两栏逐层量高（用来证伪一个**误判**）。
 *
 * 症状：从行截图上量，编辑器栏比参照栏高一大截（第 7 条 508px vs 298px 等），
 * 看起来像「编辑器把内容撑高了 200px」。
 *
 * 实测原因不是内容高：探针页的 CSS 只把 `.probe-canvas > div > .ProseMirror` 的
 * `min-height` 归零（见 `probe.css`），而编辑器栏的 `.ProseMirror` 是**嵌套**的，
 * 那条选择器够不到，于是编辑器栏始终被 `.ProseMirror { min-height: 480px }` 撑到 480px 以上。
 * 真正的内容高度要看 `inner` / `lastChildBottom`，不是 `canvas`。
 *
 * 这一支就是为纠正这一条写的：把两栏按「画布高 / 内层高 / 分隔图数 / 尾随 br 数 /
 * 顶层逐个孩子高」一起打出来，谁把高度撑开的，一眼能看出来。
 *
 * 用法（先跑 `gen/round16_editor_reported.py` 与探针 vite build，见
 * `docs/dev/render-verification.md` §3.12 U1/U2）：
 *     node tools/render-verify/browser/r16-measure-heights.mjs [id...]   # 默认全部 11 条
 * 产物：stdout（JSON）。
 * `docs/dev/known-issues-handoff.md` §3.20⑤ 的「480px 最小高度」一节出自这里。
 */
import { readFileSync, existsSync } from 'node:fs'
import { OUT, PROBE_DIST } from '../paths.mjs'
import { createServer } from 'node:http'
import { resolve, extname, join } from 'node:path'
import { launchBrowser, openPage } from './cdp.mjs'

const SET = 'r16'
const SETDIR = resolve(OUT, SET)

const meta = JSON.parse(readFileSync(join(SETDIR, SET + '.json'), 'utf8'))
const wanted = process.argv.length > 2 ? process.argv.slice(2) : meta.cases.map((item) => item.id)
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

const { client, close } = await launchBrowser({ port: 9352 })
const page = await openPage(client)
await page.send('Emulation.setDeviceMetricsOverride', { width: 1600, height: 1200, deviceScaleFactor: 1, mobile: false })
await page.navigate(origin + '/probe_r16.html')
await page.evaluate(`window.mountAll(${JSON.stringify(payload)})`)
// 等图片（第 3 条头像）落定再量，否则量到的高度偏小
await new Promise((done) => setTimeout(done, 2000))

const report = await page.evaluate(`JSON.stringify(${JSON.stringify(wanted)}.map((id) => {
  const row = document.querySelector('[data-sample="' + id + '"]')
  const out = { id }
  for (const pane of row.querySelectorAll('.probe-canvas')) {
    const rect = pane.getBoundingClientRect()
    const inner = pane.querySelector('.ProseMirror') || pane.firstElementChild
    const innerRect = inner ? inner.getBoundingClientRect() : null
    out[pane.dataset.variant] = {
      canvas: Math.round(rect.height),
      innerTag: inner ? inner.tagName.toLowerCase() + '.' + (inner.className || '') : null,
      inner: innerRect ? Math.round(innerRect.height) : null,
      // 编辑器栏里被 ProseMirror 插进来的分隔图 / 尾随 <br> 各有多少
      separators: pane.querySelectorAll('img.ProseMirror-separator').length,
      trailingBreaks: pane.querySelectorAll('br.ProseMirror-trailingBreak').length,
      scrollHeight: pane.scrollHeight,
      lastChildBottom: inner && inner.lastElementChild ? Math.round(inner.lastElementChild.getBoundingClientRect().bottom - innerRect.top) : null,
      // 顶层孩子逐个量：谁把高度撑开的，一眼能看出来
      topLevel: inner ? [...inner.children].map((child) => {
        const childRect = child.getBoundingClientRect()
        const style = getComputedStyle(child)
        return {
          tag: child.tagName.toLowerCase(),
          h: Math.round(childRect.height),
          display: style.display,
          marginTop: style.marginTop, marginBottom: style.marginBottom,
          inline: (child.getAttribute('style') || '').slice(0, 90),
          // 第 3 层：行容器里的直接孩子
          // 第 3 层：行容器里的直接孩子。dy = 相对**顶层容器顶边**的位移——
          // 「编辑器多包了一层 p」到底动没动画面，看这个数（第二十三轮第 4 条用它定案）。
          grand: [...child.children].slice(0, 12).map((grand) => {
            const grandRect = grand.getBoundingClientRect()
            const grandStyle = getComputedStyle(grand)
            return { tag: grand.tagName.toLowerCase(), h: Math.round(grandRect.height),
              dy: Math.round(grandRect.top - childRect.top),
              position: grandStyle.position, top: grandStyle.top, left: grandStyle.left,
              display: grandStyle.display, mb: grandStyle.marginBottom }
          }),
        }
      }) : null,
    }
  }
  return out
}))`)

console.log(JSON.stringify(JSON.parse(report), null, 1))
await page.close(); close(); server.close()
