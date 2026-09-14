/**
 * 第二十五轮 B（对照打印）：把**产物的**那一段与 **#38 存库正文**的那一段并排打出来，
 * 只看结构（把长样式截断、把标签缩进），用来判断「声明缺失」到底是**编辑器解析时丢的**
 * 还是**源内容本来就没有**。
 *
 * 判据：如果是编辑器丢的，两侧应当是**同一个 DOM 骨架**、只差几条声明；
 * 如果骨架本身就不同（少了一整层、少了一整行），那就不是「丢声明」。
 *
 * 用法：node tools/render-verify/browser/r25-compare-fragments.mjs 38 r16-01-changelog v2.6.0
 * 产物：stdout（不写文件）
 */
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { OUT, API } from '../paths.mjs'

const ARGS = process.argv.slice(2)
const ARTICLE_ID = Number(ARGS.find((item) => /^\d+$/.test(item)) || 38)
const CASE = ARGS.find((item) => /^r16-/.test(item)) || 'r16-01-changelog'
const ANCHOR = ARGS.find((item) => item !== String(ARTICLE_ID) && !/^r16-/.test(item)) || ''

const login = await (await fetch(API + '/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'Admin@123' }),
})).json()
const token = login.data.token
const article = (await (await fetch(`${API}/api/articles/${ARTICLE_ID}`,
  { headers: { Authorization: `Bearer ${token}` } })).json()).data

/** 结构骨架：标签名 + 缩进 + 截断后的 style；文本保留前 20 字。 */
const skeleton = (html, anchor, before = 300, after = 2600) => {
  const at = anchor ? html.indexOf(anchor) : 0
  const start = Math.max(0, at - before)
  const slice = html.slice(start, at + after)
  const out = []
  let depth = 0
  const tokens = slice.split(/(<[^>]*>)/).filter((part) => part !== '')
  for (const token of tokens) {
    if (token.startsWith('</')) {
      depth = Math.max(0, depth - 1)
      out.push('  '.repeat(depth) + token.slice(0, 40))
    } else if (token.startsWith('<')) {
      const tag = (token.match(/^<([a-zA-Z0-9]+)/) || [null, '?'])[1]
      const style = (token.match(/style="([^"]*)"/) || [null, ''])[1]
      const open = tag.toLowerCase()
      const selfClosing = /\/>$/.test(token) || ['img', 'br', 'col', 'hr', 'input'].includes(open)
      out.push('  '.repeat(depth) + '<' + tag + (style ? ' style=' + style.slice(0, 120) : '') + '>')
      if (!selfClosing) depth += 1
    } else {
      const text = token.replace(/\s+/g, ' ').trim()
      if (text) out.push('  '.repeat(depth) + '"' + text.slice(0, 24) + '"')
    }
  }
  return { start, out: out.slice(0, 90) }
}

const product = readFileSync(resolve(OUT, 'r16', CASE + '.html'), 'utf8')
const stored = article.contentHtml || ''

for (const [label, html] of [['产物（后端渲染 API 今天返回的）', product], [`#38 存库正文（revision ${article.revision}）`, stored]]) {
  console.log('\n================ ' + label + ' · 锚点「' + ANCHOR + '」 ================')
  const view = skeleton(html, ANCHOR)
  console.log(view.out.join('\n'))
}
console.log('\n产物字符数:', product.length, '| 存库字符数:', stored.length)
