/**
 * 第二十七轮 A · **段首制表符的暴露面**（全程只读）。
 *
 * 背景：第二十六轮的窄修法 `preserveLeadingWhitespace()`（`webui/src/editorExtensions.js`）把
 * 段首的 `[ \t]` 逐个换成 `&nbsp;`。**半角空格与 `&nbsp;` 同宽（实测都是 3.38px），所以那一种零代价**；
 * 但**一个制表符的视觉宽度是 26.89px**，换成 1 个 `&nbsp;` 只剩 3.38px——**这是那次修复新引入的差异**。
 *
 * 本支只回答一个问题：**这种东西在实际产物与存量正文里到底有几个。**
 * 没有暴露就不该继续为它加复杂度；有暴露才谈得上取舍。
 *
 * 口径（**与实现口径逐字对齐，不是另立一套**）：
 *   `preserveLeadingWhitespace()` 遍历文档里**每一个元素**，取它**第一个含可见字符的文本节点**，
 *   替换那个节点的**前导 `[ \t]`**；`pre/code/textarea/script/style/svg` 子树整棵跳过。
 *   本支用同一套规则（纯字符串扫描版）数：**每个「作为其父元素第一个可见文本」的文本节点，
 *   它的前导 `[ \t]` 长什么样**。等价说法：**这一次替换实际会改写哪些地方**。
 *
 * 两类输入：
 *   ① **渲染产物**：`target/probe/` 下各套件落盘的样例 HTML（components 237 + r16 12 = 249，
 *      外加 `combos/` `alt/` `registry/` `real/` 等，一并数，但**主数字用 249**）；
 *   ② **存量正文**：`ARTICLE.CONTENT_HTML`，只读。
 *
 * 用法：node tools/render-verify/round27_leading_ws_scan.mjs
 * 产物：target/probe/browser/r27_leading_ws_scan.json（只读）
 */
import { readFileSync, writeFileSync, readdirSync, existsSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { resolve, join } from 'node:path'
import { pathToFileURL } from 'node:url'
import { BROWSER_OUT, OUT, ROOT } from './paths.mjs'

const OPAQUE = new Set(['pre', 'code', 'textarea', 'script', 'style', 'svg'])
const HEAD = /<([a-zA-Z][a-zA-Z0-9-]*)((?:"[^"]*"|'[^']*'|[^>"'])*)>/g

/**
 * 扫一遍 HTML，返回「会被 `preserveLeadingWhitespace()` 改写」的那些位置的**前导空白原文**。
 *
 * 做法与实现同构：维护一个打开元素栈，每个元素只在**第一次遇到可见文本**时取一次；
 * 文本落在 `pre/code/…` 里，则把它所在的整条链都标成「不取」（对应实现里的「返回 null，宁可不改」）。
 */
export function leadingRuns(html) {
  const stack = [{ tag: '#root', taken: false, opaque: false }]
  const runs = []
  let index = 0
  const text = (raw) => {
    if (stack.some((node) => node.opaque)) return
    const visible = /\S/.test(raw)
    const run = /^[ \t]+/.exec(raw)
    if (visible) {
      // 一个文本节点可能同时是好几层元素的「第一个可见文本」（如 `<section><p>  x</p></section>`）。
      // 实现里对它只会改写一次（替换后首字符不再是 `[ \t]`，幂等），所以这里也**按文本节点计一次**，
      // 标签记最内层那个（也就是它真正的父元素）。
      let deepest = null
      for (const node of stack) {
        if (node.taken || node.opaque || node.tag === '#root') continue
        node.taken = true
        deepest = node.tag
      }
      if (run && deepest) runs.push({ tag: deepest, whitespace: run[0] })
    } else if (run) {
      // 纯空白文本节点：实现里显式不碰（否则会凭空造出可见空白），这里也不计入。
      return
    }
  }
  HEAD.lastIndex = 0
  let hit
  while ((hit = HEAD.exec(html)) !== null) {
    if (hit.index > index) text(html.slice(index, hit.index))
    const tag = hit[1].toLowerCase()
    const closing = html[hit.index + 1] === '/'
    const selfClosing = /\/>$/.test(hit[0])
    if (closing) {
      if (stack.length > 1) stack.pop()
    } else if (!selfClosing) {
      // 自闭合不是 HTML 的常规写法，但产物里有 `<col …/>` 这类，按「不入栈」处理。
      const parentOpaque = stack[stack.length - 1].opaque
      stack.push({ tag, taken: false, opaque: parentOpaque || OPAQUE.has(tag) })
    }
    index = hit.index + hit[0].length
  }
  if (index < html.length) text(html.slice(index))
  return runs
}

const summarize = (runs) => ({
  处数: runs.length,
  含制表符: runs.filter((run) => run.whitespace.includes('\t')).length,
  只含半角空格: runs.filter((run) => !run.whitespace.includes('\t')).length,
  制表符总个数: runs.reduce((sum, run) => sum + (run.whitespace.match(/\t/g) || []).length, 0),
})

// ---------- ① 渲染产物 ----------
// 被复用时只取上面的 `leadingRuns`，不要跑整支扫描（会在 import 时打一整屏日志）。
const PRODUCT_DIRS = ['components', 'r16', 'combos', 'alt', 'registry', 'real', 'na', 'browser']
const MAIN = ['components', 'r16']

async function main() {
const products = []
for (const dir of PRODUCT_DIRS) {
  const at = join(OUT, dir)
  if (!existsSync(at)) continue
  for (const name of readdirSync(at).filter((file) => file.endsWith('.html'))) {
    const html = readFileSync(join(at, name), 'utf8')
    products.push({ dir, name, 是主数字口径: MAIN.includes(dir), html })
  }
}
const scanOf = (rows) => rows.map((row) => ({ ...row, runs: leadingRuns(row.html) }))
const scanned = scanOf(products)
const mainRows = scanned.filter((row) => row.是主数字口径)
const withTab = scanned.filter((row) => row.runs.some((run) => run.whitespace.includes('\t')))
const mainWithTab = mainRows.filter((row) => row.runs.some((run) => run.whitespace.includes('\t')))
const mainWithAny = mainRows.filter((row) => row.runs.length > 0)

console.log('=== ① 渲染产物（`target/probe/`）===')
console.log('主数字口径（components + r16）:', mainRows.length, '个文件')
console.log('  含段首空白（会被改写）的文件:', mainWithAny.length, '个，合计', summarize(mainRows.flatMap((row) => row.runs)).处数, '处')
console.log('  其中**段首含制表符**:', mainWithTab.length, '个文件 ·', summarize(mainWithTab.flatMap((row) => row.runs)).含制表符, '处')
console.log('  逐处分布:', JSON.stringify(summarize(mainRows.flatMap((row) => row.runs))))
console.log('全部产物目录（' + PRODUCT_DIRS.join('/') + '）:', scanned.length, '个文件')
console.log('  含段首制表符的文件:', withTab.length, '个' + (withTab.length ? ' → ' + withTab.map((row) => row.dir + '/' + row.name).join(', ') : ''))

// ---------- ② 存量正文 ----------
const DB = 'wechat-article'
const CONTAINER = 'momo-mysql-dev'
const envLine = (key) => {
  const line = readFileSync(resolve(ROOT, '.env'), 'utf8').split(/\r?\n/)
    .find((row) => row.startsWith(key + '='))
  if (!line) throw new Error('.env 里没有 ' + key)
  return line.slice(key.length + 1).trim().replace(/^["']|["']$/g, '')
}
const username = envLine('ENV.MYSQL_USERNAME') || 'root'
const password = envLine('ENV.MYSQL_PASSWORD')
const query = (sql) => execFileSync('docker', [
  'exec', CONTAINER, 'mysql', `-u${username}`, `-p${password}`,
  '--default-character-set=utf8mb4', '-N', '-B', '-D', DB, '-e', sql,
], { encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 })

console.log('\n=== ② 存量正文（只读，容器 ' + CONTAINER + ' / 库 ' + DB + '）===')
const raw = query('SELECT ID, TITLE, CONTENT_HTML FROM ARTICLE WHERE DELETED = 0 ORDER BY ID;')
const articles = raw.split('\n').filter(Boolean).map((line) => {
  const parts = line.split('\t')
  const id = parts[0]
  const title = parts[1]
  const html = parts.slice(2).join('\t').replace(/\\n/g, '\n').replace(/\\t/g, '\t').replace(/\\\\/g, '\\')
  return { id, title, runs: leadingRuns(html) }
})
const artWithTab = articles.filter((row) => row.runs.some((run) => run.whitespace.includes('\t')))
const artWithAny = articles.filter((row) => row.runs.length > 0)
console.log('未删除文章:', articles.length, '篇')
console.log('  含段首空白（会被改写）:', artWithAny.length, '篇 ·', articles.flatMap((row) => row.runs).length, '处')
console.log('  其中**段首含制表符**:', artWithTab.length, '篇 ·', summarize(articles.flatMap((row) => row.runs)).含制表符, '处')
for (const row of artWithTab) {
  console.log('    #' + row.id, JSON.stringify(row.title).slice(0, 40),
    row.runs.filter((run) => run.whitespace.includes('\t')).map((run) => '<' + run.tag + '>' + JSON.stringify(run.whitespace)).join(' '))
}

const result = {
  productDirs: PRODUCT_DIRS,
  products: {
    主数字口径文件数: mainRows.length,
    含段首空白的文件数: mainWithAny.length,
    含段首制表符的文件数: mainWithTab.length,
    逐处合计: summarize(mainRows.flatMap((row) => row.runs)),
    全部目录文件数: scanned.length,
    全部目录含制表符文件: withTab.map((row) => row.dir + '/' + row.name),
  },
  articles: {
    篇数: articles.length,
    含段首空白的篇数: artWithAny.length,
    含段首制表符的篇数: artWithTab.length,
    逐处合计: summarize(articles.flatMap((row) => row.runs)),
    逐篇: articles.filter((row) => row.runs.length > 0)
      .map((row) => ({ id: row.id, runs: row.runs })),
  },
}
writeFileSync(resolve(BROWSER_OUT, 'r27_leading_ws_scan.json'), JSON.stringify(result, null, 1), 'utf8')
console.log('\n产物:', resolve(BROWSER_OUT, 'r27_leading_ws_scan.json'))
}

// `--check <文件>` 只跑扫描口径本身（用来校验「扫描口径 = 实现口径」），不碰数据库。
//
// 第二十八轮 B 的粘贴探针要**复用同一个 `leadingRuns`**（「剪贴板里的段首空白算不算数」必须和本支同一把尺子），
// 所以主流程加一道「是不是本文件被直接运行」的判断——`import` 进来时不再顺带跑一遍全量扫描 + 连库。
const 是本文件被直接运行 = (() => {
  try { return import.meta.url === pathToFileURL(process.argv[1]).href } catch { return false }
})()
const ARGS = process.argv.slice(2)
if (是本文件被直接运行 && ARGS[0] === '--check') {
  console.log(JSON.stringify(leadingRuns(readFileSync(resolve(ROOT, ARGS[1]), 'utf8')), null, 1))
} else if (是本文件被直接运行) {
  await main()
}
