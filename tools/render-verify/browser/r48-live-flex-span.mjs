/**
 * D48 定点实测（真实应用路线，第 40 轮）。
 *
 * r39（`r39-article8-sec04.mjs`）量的是当时那台 8081 应用上文章 8 的实际表现，结论 [2,2]；
 * 但开发库与 target/probe 取证已不存在，且第十四轮的教训正是「应用实际对外提供的前端 ≠
 * 探针验证的前端」。本支的做法是：**先用 API 建一批按 D48 记载形态（及其变体）复刻的稿件，
 * 再在当前这台应用的真实编辑器里量**——量的是「当前源码构建出的前端」对「D48 形态」的行为，
 * 与文章 8 的正文一字不差与否无关（判据是结构性的：同一源 span 是否拆成多个 flex 栏）。
 *
 * 每个形态量两轮：
 *   ① 打开即量（实时 DOM 的每行 flex 栏数 + getHTML() 出口）；
 *   ② 把 ① 的出口经 PUT /api/articles/{id} 送回服务端（过 Jsoup clean）再重开量一遍
 *      ——完整的「编辑器 → 服务端 → 编辑器」往返。
 *
 * 用法：node tools/render-verify/browser/r48-live-flex-span.mjs
 * 产物：target/probe/browser/r48_live_flex_span.json、shots/r48-live-flex-<id>.png
 * 退出码：0 = 全部形态每行 1 栏；1 = 任一形态出现 ≥2 栏（D48 复现）或判据异常。
 */
import { writeFileSync, mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { launchBrowser, openPage } from './cdp.mjs'
import { BROWSER_OUT, API, login } from '../paths.mjs'

const DOT = '<span style="width:6px;height:6px;border-radius:50%;background-color:#27ae60;margin-right:12px"></span>'
const flexRow = (label, text) => '<section style="display:flex;align-items:center">' + DOT
  + `<span style="flex:1"><strong>${label}</strong>：${text}</span></section>`

/**
 * 形态族：D48 记载的是第一条（flex:1 span 内含 strong + 正文）。其余是同一形态的
 * 可能变体——文章 8 的原文已不可考（开发库与 target/probe 取证均已不存在），
 * 逐个量过去，把「当前源码上这条链路还会不会拆」一次问全。
 */
const SAMPLES = [
  {
    id: 'strong-plain',
    note: 'D48 原始形态：flex:1 span 内含 <strong>标签</strong> + 正文',
    html: '<h2>四、运动：让身体动起来</h2>'
      + flexRow('时间', '每天 09:00 晨练 30 分钟')
      + flexRow('强度', '以微微出汗为度，避免剧烈运动')
      + '<h2>五、情志：保持心情舒畅</h2><p>收尾段落。</p>',
  },
  {
    id: 'strong-styled',
    note: '变体：strong 自带内联样式（font-weight 数值，D34 的高发形态）',
    html: '<h2>四、运动：让身体动起来</h2>'
      + '<section style="display:flex;align-items:center">' + DOT
      + '<span style="flex:1"><strong style="font-weight:700">时间</strong>：每天 09:00 晨练 30 分钟</span></section>'
      + '<h2>五、情志：保持心情舒畅</h2><p>收尾段落。</p>',
  },
  {
    id: 'two-bold-runs',
    note: '变体：一个 flex:1 span 内两段加粗（更多 mark 边界）',
    html: '<h2>四、运动：让身体动起来</h2>'
      + '<section style="display:flex;align-items:center">' + DOT
      + '<span style="flex:1"><strong>时间</strong>：每天 <strong>09:00</strong> 晨练 30 分钟</span></section>'
      + '<h2>五、情志：保持心情舒畅</h2><p>收尾段落。</p>',
  },
  {
    id: 'nested-colored-span',
    note: '变体：flex:1 span 内还有带色子 span（三层嵌套）',
    html: '<h2>四、运动：让身体动起来</h2>'
      + '<section style="display:flex;align-items:center">' + DOT
      + '<span style="flex:1"><strong>时间</strong>：每天 <span style="color:#e74c3c">09:00</span> 晨练</span></section>'
      + '<h2>五、情志：保持心情舒畅</h2><p>收尾段落。</p>',
  },
]

const MEASURE = `JSON.stringify((() => {
  const dom = document.querySelector('.ProseMirror')
  if (!dom) return { ready: false, reason: '编辑器没挂载（页面上没有 .ProseMirror）' }
  const blocks = [...dom.children]
  const sec04 = blocks.findIndex((b) => (b.textContent || '').includes('四、运动'))
  const sec05 = blocks.findIndex((b) => (b.textContent || '').includes('五、情志'))
  if (sec04 < 0 || sec05 <= sec04) return { ready: false, reason: '没找到第 04 节边界' }
  const nodes = blocks.slice(sec04, sec05)
  const rows = nodes.flatMap((b) => [...b.querySelectorAll('p')])
    .map((p) => ({ p, cols: [...p.querySelectorAll('span')]
      .filter((s) => /flex:\\s*1/.test(s.getAttribute('style') || '')).length }))
    .filter((row) => row.cols > 0)
  return {
    ready: true,
    第04节块数: nodes.length,
    每行flex栏数: rows.map((row) => row.cols),
    行文字: rows.map((row) => (row.p.textContent || '').replace(/\\s+/g, ' ').trim().slice(0, 20)),
    第04节HTML: nodes.map((b) => b.outerHTML).join(''),
  }
})())`

if (!login.success) throw new Error('登录失败：' + JSON.stringify(login))
const token = await login()
const auth = { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token }

mkdirSync(resolve(BROWSER_OUT, 'shots'), { recursive: true })
const { client, version, close } = await launchBrowser({ port: 9347 })
console.log('浏览器:', version.Browser)

const results = []
try {
  const page = await openPage(client)
  await page.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
  await page.navigate(API + '/login')
  await page.evaluate(`localStorage.setItem('wechat_bot_token', ${JSON.stringify(token)})`)

  for (const sample of SAMPLES) {
    const created = await (await fetch(API + '/api/articles', {
      method: 'POST', headers: auth,
      body: JSON.stringify({ title: `D48 探针 · ${sample.id}`, digest: 'D48 定点探针', author: 'probe', contentHtml: sample.html }),
    })).json()
    if (!created.success) throw new Error('建稿失败：' + JSON.stringify(created))
    const articleId = created.data.id

    const measureOnce = async () => {
      await page.navigate(`${API}/articles/${articleId}`)
      let state = null
      for (let attempt = 0; attempt < 40; attempt += 1) {
        state = JSON.parse(await page.evaluate(MEASURE))
        if (state.ready) break
        await new Promise((done) => setTimeout(done, 500))
      }
      await new Promise((done) => setTimeout(done, 800))
      const final = JSON.parse(await page.evaluate(MEASURE))
      const exitHtml = await page.evaluate(`document.querySelector('.ProseMirror').innerHTML`)
      return { measure: final, exitHtml }
    }

    const open = await measureOnce()
    const shot = await client.send('Page.captureScreenshot', { format: 'png' }, page.sessionId)
    writeFileSync(resolve(BROWSER_OUT, `shots/r48-live-flex-${sample.id}.png`), Buffer.from(shot.data, 'base64'))

    // 第二轮：把出口送回服务端（走 PUT，等价于编辑器保存），再重开量一遍。
    const saved = await (await fetch(API + '/api/articles/' + articleId, {
      method: 'PUT', headers: auth,
      body: JSON.stringify({ ...created.data, contentHtml: open.exitHtml, revision: created.data.revision }),
    })).json()
    if (!saved.success) throw new Error('回存失败：' + JSON.stringify(saved))
    const reopen = await measureOnce()

    const openCols = open.measure.每行flex栏数 || []
    const reopenCols = reopen.measure.每行flex栏数 || []
    const pass = openCols.length > 0 && reopenCols.length > 0
      && openCols.every((c) => c === 1) && reopenCols.every((c) => c === 1)
    results.push({
      id: sample.id, note: sample.note, articleId,
      打开: { 每行flex栏数: openCols, 第04节HTML: open.measure.第04节HTML },
      保存重开: { 每行flex栏数: reopenCols, 第04节HTML: reopen.measure.第04节HTML },
      verdict: pass ? '每行1栏' : 'D48形态复现（拆分）',
    })
    console.log(`\n== ${sample.id}（稿件 ${articleId}）`)
    console.log('   打开:', JSON.stringify(openCols), '· 保存重开:', JSON.stringify(reopenCols),
      '·', pass ? '每行1栏' : '拆分复现')
  }
} finally {
  await close()
}

const problems = results.filter((item) => item.verdict !== '每行1栏')
const out = { browser: version.Browser, results, problems: problems.map((item) => item.id) }
writeFileSync(resolve(BROWSER_OUT, 'r48_live_flex_span.json'), JSON.stringify(out, null, 1), 'utf8')
console.log('\n总判定:', problems.length === 0 ? '全部形态每行 1 栏（D48 在当前源码上未复现）' : out.problems)
process.exitCode = problems.length === 0 ? 0 : 1
