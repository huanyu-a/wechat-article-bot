/**
 * 第二十五轮 B：**只读**取证 #38 的版本历史（`GET /api/articles/38/revisions`）。
 *
 * 回答一个问题：第 1 / 5 / 6 / 10 条批注对应的排版声明（changelog 的边框、DA01 的列宽、
 * infographic 的条目行、audience-fit 的第三列），是**旧编辑器保存时丢的**，
 * 还是**当时上游产物本来就没有**。
 *
 * 判据先定死（都在同一份存库 HTML 上按同一套正则数）：
 *   - `changelog 容器边框`：包住 `v2.6.0` 的那一层 `<p>` 之前最近的 `<section …>` 开标签里有没有 `border:`
 *   - `changelog 胶囊层标签`：`v2.6.0` 那一段是 `<span` 还是纯文本
 *   - `data-colwidth 数`：全文出现次数（第六轮修的就是编辑器读不懂它）
 *   - `infographic 圆点数`：`读者画像` 之后 1200 字内 `border-radius:50%` 的出现次数
 *   - `audience-fit 残留竖线`：`结构严谨` 所在段落里有没有 `|高` 这种上游没吃掉的第三列残留
 *   - `共 N 字 计数`：全文里 `共 … 字` 的文案（上游恒为 0 的那条缺陷）
 *
 * 用法：node tools/render-verify/browser/r25-article38-revisions.mjs 38
 * 产物：target/probe/browser/r25_article38_revisions.json（只读接口，不写库）
 */
import { writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { BROWSER_OUT, API } from '../paths.mjs'

const ARGS = process.argv.slice(2)
const ARTICLE_ID = Number(ARGS.find((item) => /^\d+$/.test(item)) || 38)

const login = await (await fetch(API + '/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'Admin@123' }),
})).json()
if (!login.success) throw new Error('登录失败：' + JSON.stringify(login))
const token = login.data.token
const get = async (path) => {
  const response = await fetch(API + path, { headers: { Authorization: `Bearer ${token}` } })
  const body = await response.json()
  if (!body.success) throw new Error(path + ' -> ' + JSON.stringify(body).slice(0, 200))
  return body.data
}

const article = await get(`/api/articles/${ARTICLE_ID}`)
const revisions = await get(`/api/articles/${ARTICLE_ID}/revisions`)
console.log(`文章 #${ARTICLE_ID}：revision=${article.revision} layoutEngine=${article.layoutEngine}`
  + ` contentMarkdown=${article.contentMarkdown ? article.contentMarkdown.length + ' 字符' : '(空)'}`)
console.log(`版本快照 ${revisions.length} 条`)

const count = (text, re) => (text.match(re) || []).length

/** `v2.6.0` 之前最近的那个 `<section …>` 开标签（changelog 卡片容器的候选）。 */
const enclosingSection = (html, anchor) => {
  const at = html.indexOf(anchor)
  if (at < 0) return null
  const before = html.slice(Math.max(0, at - 4000), at)
  const hits = [...before.matchAll(/<section[^>]*>/g)]
  return hits.length ? hits[hits.length - 1][0] : null
}

const fingerprint = (html) => {
  if (!html) return null
  const changelogSection = enclosingSection(html, 'v2.6.0')
  const at = html.indexOf('v2.6.0')
  const capsuleTag = at >= 0 ? (html.slice(Math.max(0, at - 400), at).match(/<(span|p|strong)[^>]*>(?=[^<]*$)/) || [null])[0] : null
  const info = html.indexOf('读者画像')
  const infoWindow = info >= 0 ? html.slice(info, info + 1200) : ''
  const audience = html.indexOf('结构严谨')
  const audienceWindow = html.indexOf('结构严谨') >= 0 ? html.slice(audience, audience + 400) : ''
  return {
    chars: html.length,
    sections: count(html, /<section/g),
    tables: count(html, /<table/g),
    colgroup: count(html, /<colgroup/g),
    dataColwidth: count(html, /data-colwidth/g),
    changelog容器标签: changelogSection,
    changelog容器有边框: changelogSection ? /border/.test(changelogSection) : null,
    changelog容器有圆角: changelogSection ? /border-radius/.test(changelogSection) : null,
    changelog胶囊标签: capsuleTag,
    infographic圆点数: count(infoWindow, /border-radius:50%/g),
    infographic文字: infoWindow.replace(/<[^>]*>/g, '').replace(/\s+/g, '').slice(0, 40),
    audience残留竖线: /[|｜]\s*高/.test(audienceWindow),
    audience片段: audienceWindow.replace(/<[^>]*>/g, '').replace(/\s+/g, '').slice(0, 60),
    共N字: (html.match(/共\s*\d+\s*字/) || [null])[0],
    summary圆点: count((html.slice(Math.max(0, html.indexOf('本文要点')), html.indexOf('本文要点') + 3000)), /border-radius:50%/g),
    checklist方框: count(html, /width:20px;height:20px;border-radius:6px/g),
  }
}

const rows = revisions.map((revision) => ({
  revision: revision.revision,
  createdAt: revision.createdAt,
  changeSource: revision.changeSource,
  engine: revision.layoutEngine,
  author: revision.author || null,
  changeSummary: revision.changeSummary || null,
  fingerprint: fingerprint(revision.contentHtml),
}))

console.log('\n轮次 | 时间 | 来源 | 字符 | data-colwidth | changelog 容器边框 | 胶囊标签 | infographic 圆点 | 共N字')
for (const row of rows) {
  const f = row.fingerprint
  console.log([
    row.revision, row.createdAt, row.changeSource, f.chars,
    f.dataColwidth, f.changelog容器有边框, f.changelog胶囊标签, f.infographic圆点数, f.共N字,
  ].join(' | '))
}

console.log('\n当前正文（API 返回，与版本 15 同源）关键片段：')
const current = fingerprint(article.contentHtml)
for (const [key, value] of Object.entries(current)) {
  if (key === 'changelog容器标签' || key === 'changelog胶囊标签') console.log('  ' + key + ':', value)
}
console.log('  changelog 有边框:', current.changelog容器有边框, '| infographic 圆点:', current.infographic圆点数,
  '| data-colwidth:', current.dataColwidth, '| audience 残留竖线:', current.audience残留竖线, '| 共N字:', current.共N字)
console.log('  audience 片段:', current.audience片段)
console.log('  infographic 片段:', current.infographic文字)

const file = resolve(BROWSER_OUT, `r25_article38_revisions.json`)
writeFileSync(file, JSON.stringify({
  articleId: ARTICLE_ID,
  article: {
    revision: article.revision, layoutEngine: article.layoutEngine,
    contentMarkdownChars: article.contentMarkdown ? article.contentMarkdown.length : 0,
    contentHtmlChars: (article.contentHtml || '').length,
    updatedAt: article.updatedAt, createdAt: article.createdAt,
  },
  rows,
  current,
}, null, 1), 'utf8')
console.log('\n产物:', file)
