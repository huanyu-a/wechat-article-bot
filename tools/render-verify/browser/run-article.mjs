/**
 * 真实稿件侧的浏览器取证（第七轮）。
 *
 * 一份脚本干三件事，因为三件事走的是同一条链路（真实后端 8081 + 真实 MySQL 里那篇文章 + 真实 SPA 产物）：
 *   1. **公式**：文章 43 —— 后端落库 HTML 里有 KaTeX，编辑器里必须真的排成公式而不是原样文本；
 *   2. **轮播**：文章 44 —— 容器式 `:::slider` 的产物在编辑器里必须是真的轮播（SVG + foreignObject + 真图），
 *      不是空盒子也不是静态占位；
 *   3. **在库稿件回归 + 软删稿件**：同一套量法跑一遍，顺便看软删稿打开时给不给得出清楚的提示。
 *
 * 用法：node tools/render-verify/browser/run-article.mjs
 *       node tools/render-verify/browser/run-article.mjs --selftest   # 闸自检，不开浏览器、不写产物
 * 产物：target/probe/browser/articles_result.json、shots/articles/<id>.png
 *
 * 退出码（**第二十九轮 E1 补**，此前这一支没有退出码——`6/9 EXIT=0` 与「13 篇都打开了」无关）：
 *   0 = 失败项 0；1 = 下列任一：**稿件数不是 14**、**非软删稿件编辑器没挂载**、
 *       **可见文字里残留公式源码**、**有图没加载出来**。
 *   这四项都是脚本本来每次就逐行印出来的量（`ready=` / `公式源码残留=` / `轮播图=`），
 *   补的只是退出码，**判定口径一个字节没动**。
 *   ⚠️ 软删稿件（`kind` 含「软删」）本来就打不开编辑器——那不是失败，是预期行为，故豁免 ready。
 */
import { readFileSync, writeFileSync, mkdirSync, existsSync, createReadStream, statSync } from 'node:fs'
import { createServer, request as httpRequest } from 'node:http'
import { resolve, join, extname, normalize } from 'node:path'
import { launchBrowser, openPage } from './cdp.mjs'
import { BROWSER_OUT, WEBUI_DIST, login } from '../paths.mjs'
import { 判据, 自检, 失败, 克隆 } from '../gates.mjs'

const ARGS = process.argv.slice(2)

const DIST = WEBUI_DIST
const API = 'http://127.0.0.1:8081'
const SHOTS = resolve(BROWSER_OUT, 'shots/articles')
// ===========================================================================
// 6/9 的**判据**（纯函数；主流程与 `--selftest` 共用）。口径见文件头的退出码说明。
// 软删稿件（`kind` 含「软删」）本来就打不开编辑器，豁免 ready；其余三项目前每篇都该成立。
// ===========================================================================
const 判 = ({ 应有, 结果 }) => {
  const 清单 = []
  if (结果.length !== 应有) {
    清单.push(失败('稿件数', 结果.length + ' 篇，应为 ' + 应有 + ' 篇——有稿件根本没取到证'))
  }
  for (const 条 of 结果) {
    const 软删 = String(条.kind || '').includes('软删')
    if (软删) continue
    if (!条.editor?.ready) {
      清单.push(失败('#' + 条.id + ' 编辑器没挂载', 'ready=false：' + (条.editor?.reason || '（未给出理由）')))
      continue
    }
    if (条.editor.katexSourceLeak) {
      清单.push(失败('#' + 条.id + ' 残留公式源码', '可见文字里出现 $$ / \\frac / \\begin{ / \\sum_ ——公式没渲染，是原样文本'))
    }
    if (条.images && 条.images.loaded < 条.images.total) {
      清单.push(失败('#' + 条.id + ' 有图没加载', 条.images.loaded + ' / ' + 条.images.total
        + ' 张——图没解码，量到的盒子与截图都不是真的'))
    }
  }
  return 清单
}

/** 本轮要取证的稿件。`kind` 只影响报告里的分组，量法完全一样。 */
const TARGETS = [
  { id: 43, kind: '公式（本轮新产）', expect: '编辑器里 5 个 .katex（3 行内 + 2 块级），高度非零，可见文字里不出现 $$ / \\frac' },
  { id: 44, kind: '轮播（本轮新产）', expect: '编辑器里 1 个 <svg> 600×200，含 foreignObject 与 animateTransform，内嵌图有真实像素' },
  // 第六轮实拍过的 5 篇，本轮复跑做对照——同一套量法、同一轮浏览器
  { id: 24, kind: '在库回归（第六轮已拍）', expect: '3 个 <svg> 图标圈有真实盒子；class 不被丢' },
  { id: 30, kind: '在库回归（第六轮已拍）', expect: '同上' },
  { id: 35, kind: '在库回归（第六轮已拍）', expect: '同上' },
  { id: 38, kind: '在库回归（第六轮已拍）', expect: '同上' },
  { id: 40, kind: '在库回归（第六轮已拍）', expect: '同上' },
  // 不同篇幅 / 不同题材的新样本：短稿、长稿、表格多的、代码多的
  { id: 3, kind: '在库回归（本轮新增）', expect: '短稿（Markdown 421 字符）整体渲染正常' },
  { id: 11, kind: '在库回归（本轮新增）', expect: '长稿（Markdown 4423 字符）整体渲染正常' },
  { id: 13, kind: '在库回归（本轮新增）', expect: '最长稿（Markdown 9142 字符）整体渲染正常' },
  { id: 16, kind: '在库回归（本轮新增）', expect: '短稿（3909 字符）整体渲染正常' },
  { id: 37, kind: '在库回归（本轮新增）', expect: '长稿（7717 字符）整体渲染正常' },
  { id: 12, kind: '在库回归（本轮新增）', expect: '含 3 个 <svg> 图标圈的稿子' },
  // 软删稿件：编辑器打开时的表现要合理（给得出提示，不能白屏）
  { id: 5, kind: '软删稿件（DELETED=1）', expect: 'API 404；页面要给得出清楚的提示或明确跳走，不能白屏' },
]

if (ARGS.includes('--selftest')) {
  // 「好输入」用**合成的干净一轮**（14 篇全 ready、无公式源码泄漏、图全加载）——不能用当前存档：
  // 当前存档本身是红的（见下面的「存档实况」一行，那是真发现，不是闸写松了）。
  const 好输入 = {
    应有: TARGETS.length,
    结果: TARGETS.map((条) => ({
      id: 条.id, kind: 条.kind,
      editor: { ready: !String(条.kind).includes('软删'), katexSourceLeak: false },
      images: { total: 2, loaded: 2 },
    })),
  }
  const 少一篇 = 克隆(好输入)
  少一篇.结果 = 少一篇.结果.slice(0, 少一篇.结果.length - 1)
  const 有稿件没挂载 = 克隆(好输入)
  ;(有稿件没挂载.结果.find((条) => !String(条.kind).includes('软删')) || 有稿件没挂载.结果[0])
    .editor = { ready: false, reason: '（自检造）页面上没有 .ProseMirror' }
  const 漏源码 = 克隆(好输入)
  ;(漏源码.结果.find((条) => !String(条.kind).includes('软删')) || 漏源码.结果[0]).editor.katexSourceLeak = true
  const 图没加载 = 克隆(好输入)
  图没加载.结果[0].images = { total: 3, loaded: 2 }
  const 软删没挂载 = 克隆(好输入)
  ;(软删没挂载.结果.find((条) => String(条.kind).includes('软删')) || 软删没挂载.结果[0])
    .editor = { ready: false, reason: '（自检造）软删稿打不开是**预期行为**' }
  process.exitCode = 自检('6/9 run-article', 判, [
    { 名: '合成的好一轮（' + 好输入.结果.length + ' 篇全 ready / 图全加载）', 数据: 好输入, 期望: 0,
      备注: '软删 ' + 好输入.结果.filter((条) => String(条.kind).includes('软删')).length + ' 篇豁免 ready' },
    { 名: '少取一篇稿', 数据: 少一篇, 期望: 1, 备注: 少一篇.结果.length + ' 篇 vs 应有 ' + TARGETS.length },
    { 名: '有一篇非软删稿没挂载', 数据: 有稿件没挂载, 期望: 1, 备注: '软删稿豁免，非软删稿不豁免' },
    { 名: '软删稿没挂载（预期行为，不该判红）', 数据: 软删没挂载, 期望: 0, 备注: '#5 本来就打不开编辑器' },
    { 名: '有一篇残留公式源码', 数据: 漏源码, 期望: 1, 备注: '公式没渲染' },
    { 名: '有一篇的图没加载全', 数据: 图没加载, 期望: 1, 备注: '2 / 3 张' },
  ])
  // 存档实况：只打印、不断言——它是「当前数据是什么样」，不是「闸对不对」。
  const 存档 = resolve(BROWSER_OUT, 'articles_result.json')
  if (existsSync(存档)) {
    const 已跑 = JSON.parse(readFileSync(存档, 'utf8'))
    const 实况 = 判({ 应有: TARGETS.length, 结果: 已跑.results })
    console.log('  [存档实况] 当前 articles_result.json（' + 已跑.results.length + ' 篇）失败项 ' + 实况.length)
    for (const 条 of 实况) console.log('             · ' + 条.项 + '：' + 条.详情)
    console.log('             ↑ 这一行是**真实数据**，不参与自检结论；缘由见 docs/dev/known-issues-handoff.md §3.30')
  }
} else {

mkdirSync(SHOTS, { recursive: true })

const PROXY_PREFIXES = ['/api', '/uploads']
const MIME = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.png': 'image/png', '.svg': 'image/svg+xml',
  '.webp': 'image/webp', '.woff2': 'font/woff2', '.json': 'application/json',
}

const server = createServer((incoming, response) => {
  const path = incoming.url.split('?')[0]
  if (PROXY_PREFIXES.some((prefix) => path.startsWith(prefix))) {
    const proxied = httpRequest(API + incoming.url, { method: incoming.method, headers: incoming.headers },
      (upstream) => { response.writeHead(upstream.statusCode, upstream.headers); upstream.pipe(response) })
    proxied.on('error', (error) => { response.writeHead(502); response.end(String(error)) })
    incoming.pipe(proxied)
    return
  }
  let file = resolve(DIST, '.' + normalize(path))
  if (!file.startsWith(DIST) || !existsSync(file) || statSync(file).isDirectory()) file = join(DIST, 'index.html')
  response.writeHead(200, { 'Content-Type': MIME[extname(file)] || 'application/octet-stream' })
  createReadStream(file).pipe(response)
})
await new Promise((done) => server.listen(0, '127.0.0.1', done))
const origin = `http://127.0.0.1:${server.address().port}`
console.log('前端:', origin, '· 后端代理 →', API)

const MEASURE = `JSON.stringify((() => {
  const box = (element) => { const r = element.getBoundingClientRect(); return [Math.round(r.width), Math.round(r.height)] }
  const nonZero = (element) => { const r = element.getBoundingClientRect(); return r.width > 0 && r.height > 0 }
  const dom = document.querySelector('.ProseMirror')
  const bodyText = document.body.innerText.replace(/\\s+/g, ' ').trim()
  if (!dom) {
    return {
      ready: false,
      reason: '页面上没有 .ProseMirror（编辑器没挂载）',
      // 拿不到编辑器时，把整页可见文字带回来——白屏 / 报错 / 还在转圈，靠这段话区分
      bodyText: bodyText.slice(0, 600),
      hasSpinner: !!document.querySelector('.spin'),
      hasLoading: !!document.querySelector('.page-loading'),
      // D41：打开失败时页面应该报错，而不是一直转圈
      openError: !!document.querySelector('.page-loading.open-error'),
      hasEditorShell: !!document.querySelector('.editor-page'),
      hasAlert: !!document.querySelector('.editor-alert, .toast, .alert, [class*=alert]'),
    }
  }
  const html = dom.innerHTML
  const svgs = [...dom.querySelectorAll('svg')]
  const katex = [...dom.querySelectorAll('.katex')]
  const katexDisplays = [...dom.querySelectorAll('.katex-display')]
  const carouselImgs = [...dom.querySelectorAll('svg foreignObject img, svg image')]
  const text = dom.textContent.replace(/\\s+/g, '')
  return {
    ready: true,
    reason: '',
    textLength: text.length,
    bodyText: bodyText.slice(0, 600),
    svgTotal: svgs.length,
    svgVisible: svgs.filter(nonZero).length,
    svgBoxes: svgs.map(box),
    svgViewBoxes: svgs.map((element) => element.getAttribute('viewBox')),
    animateTransform: (html.match(/<animateTransform/gi) || []).length,
    foreignObject: (html.match(/<foreignObject/gi) || []).length,
    carouselImgCount: carouselImgs.length,
    carouselImgs: carouselImgs.map((element) => ({
      tag: element.tagName.toLowerCase(),
      box: box(element),
      natural: element.naturalWidth !== undefined ? [element.naturalWidth, element.naturalHeight] : null,
      src: (element.currentSrc || element.getAttribute('src') || '').slice(0, 120),
    })),
    katexTotal: katex.length,
    katexVisible: katex.filter(nonZero).length,
    katexDisplayTotal: katexDisplays.length,
    katexHeights: katex.map((element) => Math.round(element.getBoundingClientRect().height)),
    katexDisplayHeights: katexDisplays.map((element) => Math.round(element.getBoundingClientRect().height)),
    // 公式没被渲染时最典型的痕迹：LaTeX 源码原样留在可见文字里
    katexSourceLeak: /\\$\\$|\\\\frac|\\\\begin\\{|\\\\sum_/.test(text),
    katexFontLoaded: document.fonts.check('16px KaTeX_Main'),
    classNameCount: (html.match(/ class="/g) || []).length,
    htmlChars: html.length,
    hasEditorShell: true,
    pageLoading: !!document.querySelector('.page-loading'),
  }
})())`

/** 直接问后端这个 ID 是什么状态——页面表现要和 API 的真实状态对得上。 */
async function apiStatus(id, token) {
  const response = await fetch(`${API}/api/articles/${id}`, { headers: { Authorization: 'Bearer ' + token } })
  let payload = null
  try { payload = await response.json() } catch { /* 非 JSON */ }
  return { httpStatus: response.status, success: payload?.success ?? null, message: payload?.message ?? null }
}

if (!login.success) throw new Error('登录失败：' + JSON.stringify(login))
const token = await login()

const { client, version, close } = await launchBrowser({ port: 9340 })
console.log('浏览器:', version.Browser)
const page = await openPage(client)
await page.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
// 先落到同源页面再写 token：路由守卫读的是 localStorage，写在跳转之前才不会被踢回 /login
await page.navigate(origin + '/login')
await page.evaluate(`localStorage.setItem('wechat_bot_token', ${JSON.stringify(token)})`)

const results = []
for (const target of TARGETS) {
  const status = await apiStatus(target.id, token)
  await page.navigate(origin + '/articles/' + target.id)
  // 编辑器是异步加载的（ArticleEditorView 是路由懒加载 chunk），轮询到内容真的进来为止；
  // 软删稿永远等不到内容，轮询次数用完就按当时的页面状态定格。
  let state = null
  for (let attempt = 0; attempt < 40; attempt += 1) {
    state = JSON.parse(await page.evaluate(MEASURE))
    if (state.ready && state.textLength > 0) break
    if (state.openError) break // 打开失败已经是终态，不用再等
    await new Promise((done) => setTimeout(done, 500))
  }

  // 等图（轮播那两张是公网图），并且只统计真有 src 的——ProseMirror 的 separator 没有 src
  const images = JSON.parse(await page.evaluate(`(async () => {
    const real = [...document.images].filter((image) => image.getAttribute('src'))
    await Promise.race([
      Promise.all(real.map((image) => image.complete ? null : new Promise((done) => {
        image.addEventListener('load', done, { once: true }); image.addEventListener('error', done, { once: true })
      }))),
      new Promise((done) => setTimeout(done, 15000)),
    ])
    return JSON.stringify({ total: real.length, loaded: real.filter((i) => i.complete && i.naturalWidth > 0).length })
  })()`))
  // **等完再量一次**：轮播里的图是 `naturalWidth` 只有解码后才知道，KaTeX 的 webfont 也是
  // 页面出现公式之后才会去下载——轮询那一刻量到的必然偏小（第一次跑 3 张 banner 图全是 0×0）。
  await page.evaluate(`(async () => { await document.fonts.ready })()`)
  await new Promise((done) => setTimeout(done, 500))
  state = JSON.parse(await page.evaluate(MEASURE))

  // 截图：编辑器有内容就裁编辑器，没有就拍整屏（白屏/报错现场就是要拍的东西）
  let clip
  if (state.ready) {
    clip = JSON.parse(await page.evaluate(`(() => {
      const r = document.querySelector('.ProseMirror').getBoundingClientRect()
      return JSON.stringify({ x: r.x + window.scrollX, y: r.y + window.scrollY, width: r.width, height: Math.min(r.height, 7000) })
    })()`))
  } else {
    const metrics = await client.send('Page.getLayoutMetrics', {}, page.sessionId)
    const size = metrics.cssContentSize || metrics.contentSize
    clip = { x: 0, y: 0, width: Math.ceil(size.width), height: Math.min(Math.ceil(size.height), 1400) }
  }
  const shot = await client.send('Page.captureScreenshot', {
    format: 'png', captureBeyondViewport: true,
    clip: { ...clip, scale: state.ready ? 0.5 : 1 },
  }, page.sessionId)
  writeFileSync(join(SHOTS, `${target.id}.png`), Buffer.from(shot.data, 'base64'))

  const row = { ...target, api: status, images, shot: `shots/articles/${target.id}.png`, editor: state }
  results.push(row)
  console.log(`\n#${target.id} ${target.kind}  API ${status.httpStatus}`
    + `  ready=${state.ready} 文字=${state.textLength ?? 0}`
    + ` svg=${state.svgTotal ?? 0}(${state.svgVisible ?? 0} 可见) katex=${state.katexTotal ?? 0}`
    + ` 轮播图=${state.carouselImgCount ?? 0} 公式源码残留=${state.katexSourceLeak}`)
  if (!state.ready) console.log(`   页面文字：${state.bodyText}`)
}

writeFileSync(resolve(BROWSER_OUT, 'articles_result.json'),
  JSON.stringify({ browser: version.Browser, origin, api: API, results }, null, 1), 'utf8')
console.log('\n截图目录:', SHOTS)
console.log('结果:', resolve(BROWSER_OUT, 'articles_result.json'))

await page.close()
close()
server.close()

process.exitCode = 判据('6/9 run-article（' + TARGETS.length + ' 篇真实稿件 · 软删 '
  + TARGETS.filter((条) => String(条.kind).includes('软删')).length + ' 篇豁免 ready）',
  判, { 应有: TARGETS.length, 结果: results }) ? 1 : 0
}
