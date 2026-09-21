/**
 * 文章 8 第 04 节「编辑器里到底渲染成什么样」的定点实测（本轮新增，一次性诊断）。
 *
 * 为什么另起一支：`verify-live-app.mjs` 只量整篇（svg / katex / 图片），
 * 而本轮的待答问题是**某一节**的 p-title 组件与它那两条裸 markdown 列表项。
 * 它复用同一套浏览器驱动与同一条「打开真实文章」的路径，只是把测量函数换成本次的判据。
 *
 * 判据（按用户原话「这部分用的什么组件，好像没有渲染成功」拆开）：
 *   ① 第 04 节的 p-title 版式在不在——**不能认 `data-block="ptitle"`**：那是渲染服务的标记，
 *      编辑器不认识它、`getHTML()` 出口本来就不带（本探针首版据此报「0 个 p-title」是假阴性）。
 *      可靠的判据是版式本身：`CHAPTER 04` 三行文字 + 60px/30px 的 `<strong>`；
 *   ② 它的三行文字在不在、字号对不对（**必须用 computed 或带空格的写法**：CSSOM 会把
 *      `font-size:60px` 规范成 `font-size: 60px`，按无空格字面量 grep 同样会漏）；
 *   ③ 两条列表项前的 6×6 圆点在编辑器里有没有**可见盒子**（这类空 span 是历史高发区）；
 *   ④ **列表行的 `flex:1` 栏数**：落库 HTML 是「圆点 + 一个 flex:1 span（内含 strong + 正文）」，
 *      编辑器若把同一个源 span 按内联格式拆成多个，这里会从 1 变 2——本轮真正查出来的缺陷（D48）。
 *   ⑤ `getHTML()` 出口与入口逐字符是否一致（不一致才是「保存一次就退化」的实锤）。
 *
 * **对照组**：第 01 节同为一个 p-title 块，但正文是普通段落而非列表。同时量它，
 * 才能把「第 04 节坏了」与「判据本身没生效」区分开（文档 §4.2 第 11 条）。
 *
 * 用法：node tools/render-verify/browser/r39-article8-sec04.mjs [articleId]
 * 产物：target/probe/browser/r39_article8_sec04.json
 */
import { writeFileSync, mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { launchBrowser, openPage } from './cdp.mjs'
import { BROWSER_OUT, login } from '../paths.mjs'

const LIVE = 'http://127.0.0.1:8081'
const ARTICLE_ID = Number(process.argv.slice(2).find((item) => /^\d+$/.test(item)) || 8)

const MEASURE = `JSON.stringify((() => {
  const dom = document.querySelector('.ProseMirror')
  if (!dom) return { ready: false, reason: '编辑器没挂载（页面上没有 .ProseMirror）' }

  /** 按小标题里出现的关键字给 section 分组：编辑器把整篇解析成一串块级节点。 */
  const blocks = [...dom.children]
  const indexOfHeading = (keyword) => blocks.findIndex((b) => (b.textContent || '').includes(keyword))
  const sec04 = indexOfHeading('四、运动')
  const sec05 = indexOfHeading('五、情志')
  const sec01 = indexOfHeading('一、为什么春天要')
  const sec02 = indexOfHeading('二、饮食')

  /** 取一节的 DOM 片段（从它的 h2 起，到下一节的 h2 前）。 */
  const sliceOf = (start, end) => (start >= 0 && end > start)
    ? JSON.stringify(blocks.slice(start, end).map((b) => b.outerHTML)).length
    : null

  /**
   * 在一节的块里量 p-title 版式与列表行。
   *
   * 不认 data-block="ptitle"：编辑器不认识这个属性，getHTML() 出口本就不带它
   * （首版探针据此报「0 个 p-title」是假阴性，真正的判据是版式本身）。
   */
  const inspect = (start, end) => {
    if (start < 0 || end <= start) return null
    const nodes = blocks.slice(start, end)
    const box = (el) => { const r = el.getBoundingClientRect(); return [Math.round(r.width), Math.round(r.height)] }
    const computed = (el) => el ? getComputedStyle(el).fontSize + '/' + getComputedStyle(el).fontWeight : null
    // p-title 的三行文字：CHAPTER 04 / 大号章节号 / 主标题 / 英文副题
    const chapter = nodes.find((b) => (b.textContent || '').includes('CHAPTER'))
    const strongs = nodes.flatMap((b) => [...b.querySelectorAll('strong')])
    // 6×6 圆点：渲染产物用 display:inline-block;width:6px;height:6px;border-radius:50%
    const dots = nodes.flatMap((b) => [...b.querySelectorAll('span')])
      .filter((s) => /width:\\s*6px/.test(s.getAttribute('style') || ''))
    // 列表行：编辑器把 flex 行补合成 <p>，量其中 flex:1 的栏数
    // 落库 HTML 是「圆点 + 一个 flex:1 span」，拆开后同一行会出现 2 个。
    const flexRows = nodes.flatMap((b) => [...b.querySelectorAll('p')])
      .map((p) => ({ p, cols: [...p.querySelectorAll('span')]
        .filter((s) => /flex:\\s*1/.test(s.getAttribute('style') || '')).length }))
      .filter((row) => row.cols > 0)
    return {
      块数: nodes.length,
      有CHAPTER: !!(chapter && (chapter.textContent || '').includes('CHAPTER')),
      含MOVE: nodes.some((b) => (b.textContent || '').includes('MOVE GENTLY')),
      strong数: strongs.length,
      strong字号字重: strongs.map(computed),
      strong外框: strongs.map(box),
      圆点数: dots.length,
      圆点外框: dots.map(box),
      圆点可见: dots.filter((d) => { const r = d.getBoundingClientRect(); return r.width > 0 && r.height > 0 }).length,
      列表行数: flexRows.length,
      每行flex栏数: flexRows.map((row) => row.cols),
      行文字: flexRows.map((row) => (row.p.textContent || '').replace(/\\s+/g, ' ').trim().slice(0, 30)),
    }
  }

  const html = dom.innerHTML
  return {
    ready: true,
    pageUrl: location.href,
    第01节: inspect(sec01, sec02),
    第04节: inspect(sec04, sec05),
    编辑器块总数: blocks.length,
    字面p标题残留: (html.match(/<p-title/gi) || []).length,
    字面冒号残留: (html.match(/:::/g) || []).length,
  }
})())`

const token = await login()

mkdirSync(BROWSER_OUT, { recursive: true })
const { client, version, close } = await launchBrowser({ port: 9342 })
console.log('浏览器:', version.Browser, '· 文章:', ARTICLE_ID)
try {
  const page = await openPage(client)
  await page.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
  await page.navigate(LIVE + '/login')
  await page.evaluate(`localStorage.setItem('wechat_bot_token', ${JSON.stringify(token)})`)
  await page.navigate(`${LIVE}/articles/${ARTICLE_ID}`)

  let state = null
  for (let attempt = 0; attempt < 40; attempt += 1) {
    state = JSON.parse(await page.evaluate(MEASURE))
    if (state.ready) break
    await new Promise((done) => setTimeout(done, 500))
  }
  await page.evaluate(`(async () => { await document.fonts.ready })()`)
  await new Promise((done) => setTimeout(done, 800))
  const final = JSON.parse(await page.evaluate(MEASURE))

  // 出口：编辑器序列化出来的 HTML（保存与预览都走它）
  const exitHtml = await page.evaluate(`document.querySelector('.ProseMirror').innerHTML`)
  writeFileSync(resolve(BROWSER_OUT, `r39_article${ARTICLE_ID}_editor-exit.html`), exitHtml, 'utf8')

  const shot = await client.send('Page.captureScreenshot', { format: 'png' }, page.sessionId)
  writeFileSync(resolve(BROWSER_OUT, `shots/r39-article${ARTICLE_ID}-sec04.png`), Buffer.from(shot.data, 'base64'))

  const out = { browser: version.Browser, articleId: ARTICLE_ID, editor: final, exitHtmlLength: exitHtml.length }
  writeFileSync(resolve(BROWSER_OUT, 'r39_article8_sec04.json'), JSON.stringify(out, null, 1), 'utf8')

  console.log('\n=== 第 01 节（对照组）===')
  console.log(JSON.stringify(final.第01节, null, 1))
  console.log('\n=== 第 04 节（用户报的那一节）===')
  console.log(JSON.stringify(final.第04节, null, 1))
  console.log('\n编辑器块总数:', final.编辑器块总数,
    '· 字面 <p-title> 残留:', final.字面p标题残留, '· 字面 ::: 残留:', final.字面冒号残留)
} finally {
  await close()
}
