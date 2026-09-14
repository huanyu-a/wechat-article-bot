/**
 * 第三十四轮 · 标记类判据的**取证扫描**：拿什么当「这是渲染服务产物表」的指纹？
 *
 * 起因是一次被证伪的假设。第一版判据写成「`node.attrs.preservedStyle` 非空 ⇒ 打类」，
 * 注释里还写了「手写表格两样都拿不到」。实测（`table_handmade_regression.mjs`）把它推翻了：
 * 工具栏插一张 3×3 表，保存出口是 `<table style="min-width: 75px;">` ——**TipTap 自己就会写 style**。
 * 于是手写表格也会被打上类，`.mf-preserved th,td{border:0}` 把网格线画没（实测逐格边框 1px → 0px）。
 *
 * 所以需要一个**只在产物表上成立**的指纹。本支把候选指纹在**全部产物**上数一遍：
 *   ① 表级 `style` 里有没有 `border-collapse`（渲染服务对每张表都写）；
 *   ② 单元格是不是**每一格**都自带 `style`（手写表格的格是裸的，产物表每格都带设计）。
 *
 * 用法：node target/probe/r34/table_fingerprint_scan.mjs
 * 产物：target/probe/r34/table_fingerprint_scan.txt / .json
 */
import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { OUT } from './paths.mjs'

const R34 = resolve(OUT, 'r34')
const ROOTS = ['components', 'r16', 'alt', 'combos', 'registry']

const walk = dir => {
  const found = []
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) found.push(...walk(full))
    else if (entry.endsWith('.html') && !entry.endsWith('.editor.html')) found.push(full)
  }
  return found
}

const files = ROOTS.flatMap(root => {
  try { return walk(join(OUT, root)) } catch { return [] }
})

const rows = []
for (const file of files) {
  const html = readFileSync(file, 'utf8')
  if (!/<table[\s>]/i.test(html)) continue
  const tables = html.match(/<table\b[^>]*>/gi) || []
  const cells = html.match(/<(?:td|th)\b[^>]*>/gi) || []
  const styledCells = cells.filter(cell => /style="[^"]+"/i.test(cell)).length
  rows.push({
    file: file.slice(OUT.length + 1).replace(/\\/g, '/'),
    tables: tables.length,
    borderCollapse: tables.filter(tag => /border-collapse\s*:/i.test(tag)).length,
    cells: cells.length,
    styledCells,
    tableTags: tables.map(tag => (tag.match(/style="([^"]*)"/i) || [null, '(无 style)'])[1]),
  })
}

const all = rows.length
const okCollapse = rows.filter(row => row.borderCollapse === row.tables).length
const okCells = rows.filter(row => row.cells > 0 && row.styledCells === row.cells).length
const both = rows.filter(row => row.borderCollapse === row.tables && row.styledCells === row.cells).length

const lines = ['# 标记类指纹扫描（第三十四轮）', '',
  `- 有表格的产物：**${all}** 个`,
  `- ① 每张表的表级 style 都带 \`border-collapse\`：**${okCollapse}/${all}**`,
  `- ② 每一格都自带 style：**${okCells}/${all}**`,
  `- ①② 同时成立：**${both}/${all}**`,
  '',
  '| 产物 | 表数 | 带 border-collapse | 带 style 的格 / 总格 | 表级 style |',
  '| --- | --- | --- | --- | --- |',
  ...rows.map(row => `| ${row.file} | ${row.tables} | ${row.borderCollapse} | ${row.styledCells}/${row.cells} | \`${row.tableTags.join('` ‖ `')}\` |`),
  '',
  '对照（手写表格，来自 `table_handmade_regression.mjs` 的保存出口）：',
  '- `<table style="min-width: 75px;">` —— **没有 `border-collapse`**，格也是裸的（`<th colspan="1" rowspan="1">`）。',
  '- 所以「表级 style 里有 `border-collapse`」这个指纹能把两类分开；把 `<table>` 上「有没有 style」当指纹则分不开。',
]
console.log(lines.join('\n'))
writeFileSync(resolve(R34, 'table_fingerprint_scan.txt'), lines.join('\n') + '\n', 'utf8')
writeFileSync(resolve(R34, 'table_fingerprint_scan.json'), JSON.stringify({ rows, 汇总: { all, okCollapse, okCells, both } }, null, 1), 'utf8')
