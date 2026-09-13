/**
 * 直接对着**真实运行的应用**（http://127.0.0.1:8081）取证，而不是对着探针自己起的静态服务。
 *
 * 为什么需要这一支：`run-article.mjs` 是把 `webui/dist` 用本地静态服务端起来的（见其第 19 行），
 * 它量到的是**最新构建的产物**；而应用实际对外提供的是 `target/classes/static`（Maven 的
 * `frontend-maven-plugin` 在 `prepare-package` 阶段写进去的那一份）。两者可能不是同一份构建 ——
 * 第十四轮就是这么翻的车：`spring-boot:run` 不经过 `prepare-package`，静态产物停在 9-11，
 * 而 `webui/dist` 已经是 9-13，于是「探针全绿、用户打开却是坏的」。
 *
 * 用法：node tools/render-verify/browser/verify-live-app.mjs [articleId ...]
 * 产物：target/probe/browser/live_app_result.json、shots/live/<id>.png
 */
import { writeFileSync, mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { launchBrowser, openPage } from './cdp.mjs'
import { BROWSER_OUT } from '../paths.mjs'

const LIVE = 'http://127.0.0.1:8081'
const SHOTS = resolve(BROWSER_OUT, 'shots/live')
mkdirSync(SHOTS, { recursive: true })

const TARGETS = process.argv.slice(2).length ? process.argv.slice(2).map(Number) : [43, 44]

const MEASURE = `JSON.stringify((() => {
  const box = (element) => { const r = element.getBoundingClientRect(); return [Math.round(r.width), Math.round(r.height)] }
  const nonZero = (element) => { const r = element.getBoundingClientRect(); return r.width > 0 && r.height > 0 }
  const dom = document.querySelector('.ProseMirror')
  const bodyText = document.body.innerText.replace(/\\s+/g, ' ').trim()
  if (!dom) return { ready: false, reason: '页面上没有 .ProseMirror（编辑器没挂载）', bodyText: bodyText.slice(0, 400) }
  const html = dom.innerHTML
  const svgs = [...dom.querySelectorAll('svg')]
  const katex = [...dom.querySelectorAll('.katex')]
  const text = dom.textContent.replace(/\\s+/g, '')
  // 编辑器 chunk 的指纹：这三个名字只存在于 webui/src/editorExtensions.js，
  // 打包后作为字符串常量活下来（Node.create 的 name 字段），可以直接判断「跑的是不是带修复的那份前端」
  const scripts = [...document.scripts].map((s) => s.src).filter(Boolean)
  return {
    ready: true,
    textLength: text.length,
    svgTotal: svgs.length,
    svgVisible: svgs.filter(nonZero).length,
    svgBoxes: svgs.map(box),
    animateTransform: (html.match(/<animateTransform/gi) || []).length,
    foreignObject: (html.match(/<foreignObject/gi) || []).length,
    katexTotal: katex.length,
    katexVisible: katex.filter(nonZero).length,
    katexHeights: katex.map((element) => Math.round(element.getBoundingClientRect().height)),
    katexSourceLeak: /\\$\\$|\\\\frac|\\\\begin\\{|\\\\sum_/.test(text),
    katexFontLoaded: document.fonts.check('16px KaTeX_Main'),
    scripts,
    pageUrl: location.href,
  }
})())`

/**
 * 前端 chunk 的指纹：带修复的那份含 rawSvg / rawMath / preservedEmptySpan，旧的没有。
 * 刻意**在 Node 侧**抓取而不是在页面里 fetch —— 页面里 window.fetch 可能被应用包装过，
 * 第十四轮实测在页面内 `fetch(...).text()` 会抛 `not a function`。
 */
async function fingerprintOf(origin) {
  const html = await (await fetch(origin + '/')).text()
  const entry = (html.match(/assets\/index-[A-Za-z0-9_-]+\.js/) || [])[0] || null
  if (!entry) return { entry: null, editorChunk: null }
  const entryText = await (await fetch(`${origin}/${entry}`)).text()
  const chunkName = (entryText.match(/ArticleEditorView-[A-Za-z0-9_-]+\.js/) || [])[0] || null
  if (!chunkName) return { entry, editorChunk: null }
  const chunkText = await (await fetch(`${origin}/assets/${chunkName}`)).text()
  return {
    entry, editorChunk: chunkName,
    hasRawSvg: /rawSvg/.test(chunkText),
    hasRawMath: /rawMath/.test(chunkText),
    hasPreservedEmptySpan: /preservedEmptySpan/.test(chunkText),
  }
}

const login = await (await fetch(LIVE + '/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'Admin@123' }),
})).json()
if (!login.success) throw new Error('登录失败：' + JSON.stringify(login))
const token = login.data.token

const { client, version, close } = await launchBrowser({ port: 9341 })
console.log('浏览器:', version.Browser, '· 目标:', LIVE)
const page = await openPage(client)
await page.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
await page.navigate(LIVE + '/login')
await page.evaluate(`localStorage.setItem('wechat_bot_token', ${JSON.stringify(token)})`)

const fingerprint = await fingerprintOf(LIVE)
console.log('前端入口:', fingerprint.entry)
console.log('编辑器 chunk:', fingerprint.editorChunk,
  'rawSvg=' + fingerprint.hasRawSvg, 'rawMath=' + fingerprint.hasRawMath,
  'preservedEmptySpan=' + fingerprint.hasPreservedEmptySpan)

const results = []
for (const id of TARGETS) {
  await page.navigate(`${LIVE}/articles/${id}`)
  let state = null
  for (let attempt = 0; attempt < 40; attempt += 1) {
    state = JSON.parse(await page.evaluate(MEASURE))
    if (state.ready && state.textLength > 0) break
    await new Promise((done) => setTimeout(done, 500))
  }
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
  await page.evaluate(`(async () => { await document.fonts.ready })()`)
  await new Promise((done) => setTimeout(done, 500))
  state = JSON.parse(await page.evaluate(MEASURE))

  const clip = state.ready
    ? JSON.parse(await page.evaluate(`(() => {
        const r = document.querySelector('.ProseMirror').getBoundingClientRect()
        return JSON.stringify({ x: r.x + window.scrollX, y: r.y + window.scrollY, width: r.width, height: Math.min(r.height, 7000) })
      })()`))
    : { x: 0, y: 0, width: 1440, height: 1000 }
  const shot = await client.send('Page.captureScreenshot', {
    format: 'png', captureBeyondViewport: true, clip: { ...clip, scale: state.ready ? 0.5 : 1 },
  }, page.sessionId)
  writeFileSync(resolve(SHOTS, `${id}.png`), Buffer.from(shot.data, 'base64'))

  results.push({ id, images, shot: `shots/live/${id}.png`, editor: state, fingerprint })
  console.log(`\n#${id}  实际打开的是 ${state.pageUrl}`)
  console.log(`  ready=${state.ready} 文字=${state.textLength ?? 0}`
    + ` svg=${state.svgTotal ?? 0}(${state.svgVisible ?? 0} 可见) katex=${state.katexTotal ?? 0}(${state.katexVisible ?? 0} 可见)`
    + ` 轮播图=${images.loaded}/${images.total} 公式源码残留=${state.katexSourceLeak}`)
  console.log('  公式高度:', JSON.stringify(state.katexHeights ?? []), ' svg 盒子:', JSON.stringify(state.svgBoxes ?? []))
}

writeFileSync(resolve(BROWSER_OUT, 'live_app_result.json'),
  JSON.stringify({ browser: version.Browser, origin: LIVE, fingerprint, results }, null, 1), 'utf8')
console.log('\n结果:', resolve(BROWSER_OUT, 'live_app_result.json'))

await page.close()
close()
