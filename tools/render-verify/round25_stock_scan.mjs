/**
 * 第二十五轮 C · **存量影响面**（全程只读，一条 UPDATE 都没有）。
 *
 * 回答两个问题：
 *   ① 现在库里的文章正文，有多少篇还带着「旧编辑器往返之后」的指纹、或者缺掉那些声明？
 *   ② 把它们重新渲染一遍这条路，走不走得通？
 *
 * 判据（每条都能只看 `ARTICLE.CONTENT_HTML` 一个字段自己重跑，不依赖浏览器、不依赖今天的时间点）：
 *   - `列宽塌成 25px`：正文里有 `<colgroup>`，且该 `<colgroup>` 里**每一个** `<col>` 都只有
 *     `min-width`（没有 `width:`）。这是**旧编辑器的保存出口指纹**——同一份产物在旧前端保存出口里
 *     就是 `<col style="min-width: 25px;"><col style="min-width: 25px;">`，新前端保留 `width: 90px`（见 §3.26 B）。
 *   - `changelog 卡片缺容器`：正文里出现 `v<数字>.<数字>.<数字>`，且它前面 4000 字里**一个 `<section` 都没有**。
 *   - `infographic 只有标签`：出现 `读者画像`，且其后 1200 字里 `border-radius: ?50%` 计数为 0。
 *   - `audience-fit 残留竖线`：出现 `|高` / `|中` / `|低`（上游没吃掉的第三列）。
 *
 * 「能不能重渲染」的判据在代码里也写死了：`ArticleService.rerender()` 要求
 * `layout_engine == MARKFLOW` **且** `content_markdown` 非空，否则抛
 * 「该文章没有留存 Markdown 源文，无法重新渲染」。所以本支把这两个字段的分布一并数出来。
 *
 * 用法：node tools/render-verify/round25_stock_scan.mjs
 * 产物：target/probe/browser/r25_stock_scan.json（只读）
 */
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { resolve } from 'node:path'
import { BROWSER_OUT, ROOT } from './paths.mjs'

const DB = 'wechat-article'
const CONTAINER = 'momo-mysql-dev'

/** 密码从 `.env` 现读，不落盘、不打印、不进产物。 */
const password = (() => {
  const line = readFileSync(resolve(ROOT, '.env'), 'utf8').split(/\r?\n/)
    .find((row) => row.startsWith('ENV.MYSQL_PASSWORD='))
  if (!line) throw new Error('.env 里没有 ENV.MYSQL_PASSWORD')
  return line.slice('ENV.MYSQL_PASSWORD='.length).trim().replace(/^["']|["']$/g, '')
})()
const username = (() => {
  const line = readFileSync(resolve(ROOT, '.env'), 'utf8').split(/\r?\n/)
    .find((row) => row.startsWith('ENV.MYSQL_USERNAME='))
  return line ? line.slice('ENV.MYSQL_USERNAME='.length).trim().replace(/^["']|["']$/g, '') : 'root'
})()

const query = (sql) => execFileSync('docker', [
  'exec', CONTAINER, 'mysql', `-u${username}`, `-p${password}`,
  '--default-character-set=utf8mb4', '-N', '-B', '-D', DB, '-e', sql,
], { encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 })

/** `-N -B` 把字段用 tab 分隔、把换行转义成 `\n`，所以按 tab 切、再把 `\n` 还原。 */
const unescapeTab = (value) => value.replace(/\\n/g, '\n').replace(/\\t/g, '\t').replace(/\\\\/g, '\\')

console.log('只读扫描（容器 ' + CONTAINER + ' / 库 ' + DB + '）…')
const raw = query(`SELECT ID, TITLE, LAYOUT_ENGINE, REVISION,
  IF(CONTENT_MARKDOWN IS NULL OR CHAR_LENGTH(CONTENT_MARKDOWN) = 0, 0, 1) AS HAS_MD,
  CHAR_LENGTH(CONTENT_HTML) AS HTML_LEN, CONTENT_HTML
  FROM ARTICLE WHERE DELETED = 0 ORDER BY ID;`)

const rows = raw.split('\n').filter(Boolean).map((line) => {
  const parts = line.split('\t')
  const [id, title, engine, revision, hasMd, htmlLen] = parts
  // 正文里若有制表符，会被多切出来；最后一段才是 HTML。
  const html = unescapeTab(parts.slice(6).join('\t'))
  return {
    id: Number(id), title, engine: engine || null, revision: Number(revision),
    hasMarkdown: hasMd === '1', htmlLength: Number(htmlLen), html,
  }
})
if (!rows.length) throw new Error('没读到任何未删除的文章')

const count = (text, re) => (text.match(re) || []).length

/**
 * `colgroup` 是否「列宽塌了」：该 colgroup 里每一个 col 都只有 min-width、没有 width。
 * 用正则切出每个 `<colgroup>…</colgroup>` 单独判，避免把别的表算进来。
 */
const colgroupCollapsed = (html) => {
  const groups = [...html.matchAll(/<colgroup>([\s\S]*?)<\/colgroup>/g)].map((hit) => hit[1])
  if (!groups.length) return false
  return groups.some((group) => {
    const cols = [...group.matchAll(/<col\b[^>]*>/g)].map((hit) => hit[0])
    if (!cols.length) return false
    // 注意 `\bwidth:` 会命中 `min-width:`（`-` 与 `w` 之间也是词边界），必须排除前面是 `-` 或字母的那种。
    return cols.every((col) => !/(?<![-\w])width\s*:/.test(col)) && cols.some((col) => /min-width/.test(col))
  })
}
const changelogMissingCard = (html) => {
  const at = html.search(/v\d+\.\d+\.\d+/)
  if (at < 0) return false
  return !html.slice(Math.max(0, at - 4000), at).includes('<section')
}
const infographicLabelOnly = (html) => {
  const at = html.indexOf('读者画像')
  if (at < 0) return false
  return count(html.slice(at, at + 1200), /border-radius:\s*50%/g) === 0
}
const audienceResidualPipe = (html) => /[|｜]\s*[高中低]\s*</.test(html) || /[|｜]\s*[高中低]/.test(html)

const marks = rows.map((row) => {
  const html = row.html || ''
  return {
    id: row.id, title: row.title, engine: row.engine, revision: row.revision,
    hasMarkdown: row.hasMarkdown, htmlLength: row.htmlLength,
    colgroupCount: count(html, /<colgroup/g),
    dataColwidth: count(html, /data-colwidth/g),
    列宽塌成25px: colgroupCollapsed(html),
    changelog卡片缺容器: changelogMissingCard(html),
    infographic只有标签: infographicLabelOnly(html),
    audience残留竖线: audienceResidualPipe(html),
    共0字: count(html, /共\s*0\s*字/g),
  }
})

const total = marks.length
const share = (n) => (total ? Math.round(n / total * 1000) / 10 : 0)
const criteria = ['列宽塌成25px', 'changelog卡片缺容器', 'infographic只有标签', 'audience残留竖线']
const summary = {}
for (const key of criteria) summary[key] = { 篇数: marks.filter((row) => row[key]).length }
for (const key of criteria) summary[key].占比 = share(summary[key].篇数) + '%'
const anyOf = marks.filter((row) => criteria.some((key) => row[key])).length
summary['命中任意一条'] = { 篇数: anyOf, 占比: share(anyOf) + '%' }

console.log(`\n未删除文章 ${total} 篇\n`)
console.log('判据'.padEnd(24) + '篇数'.padStart(6) + '占比'.padStart(9))
for (const key of criteria) console.log(key.padEnd(24) + String(summary[key].篇数).padStart(6) + summary[key].占比.padStart(9))
console.log('命中任意一条'.padEnd(24) + String(anyOf).padStart(6) + summary['命中任意一条'].占比.padStart(9))

console.log('\n逐篇（只列命中的）：')
console.log('  ' + 'ID'.padStart(4) + ' | ' + '引擎'.padEnd(9) + ' | ' + '留存MD'.padEnd(6) + ' | 正文长度 | 命中判据')
for (const row of marks) {
  const hits = criteria.filter((key) => row[key])
  if (!hits.length) continue
  console.log('  ' + String(row.id).padStart(4) + ' | ' + String(row.engine).padEnd(9) + ' | '
    + String(row.hasMarkdown ? '有' : '**空**').padEnd(6) + ' | ' + String(row.htmlLength).padStart(8) + ' | ' + hits.join('、'))
}

// 「重新渲染存量」这条路走不走得通：直接数 rerender() 的前置条件。
const byEngine = {}
for (const row of marks) byEngine[row.engine || '(空)'] = (byEngine[row.engine || '(空)'] || 0) + 1
const rerenderable = marks.filter((row) => row.engine === 'MARKFLOW' && row.hasMarkdown).length
console.log('\n重新渲染这条路的前置条件（`ArticleService.rerender()` 两个硬条件）：')
console.log('  layout_engine 分布:', JSON.stringify(byEngine))
console.log('  两个条件同时满足（engine=MARKFLOW 且 留存了 Markdown）:', rerenderable, '/', total)
console.log('  留存了 Markdown 的篇数:', marks.filter((row) => row.hasMarkdown).length, '/', total)

mkdirSync(BROWSER_OUT, { recursive: true })
const file = resolve(BROWSER_OUT, 'r25_stock_scan.json')
writeFileSync(file, JSON.stringify({
  scannedAt: new Date().toISOString(), database: DB, container: CONTAINER, total,
  summary, byEngine, rerenderable, rows: marks,
}, null, 1), 'utf8')
console.log('\n产物:', file)
