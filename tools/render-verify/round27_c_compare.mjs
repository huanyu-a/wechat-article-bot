/**
 * 第二十七轮 C · 把第二十四轮那张 **#38 十一条四列表**逐值复跑对账。
 *
 * 第二十四轮的表把「改前 / 改后 / 产物」三列放在一起，判据是**第 4 · 7 · 8 · 9 · 11 条与后两列对上**
 * （见 `docs/dev/known-issues-handoff.md` §3.24②）。产品代码此后又动过（第二十五~二十七轮），
 * 所以本轮要求**重跑一遍确认没有回退**，且那几条要逐项与第二十四轮的数字对上。
 *
 * 本脚本只做一件事：把两次 `r24-article38-symptoms.mjs` 的产物 `editor.items` **按叶子值逐项对账**，
 * 打印「同 / 不同」，不同处列出两边原值。**不做归一、不做容差**——数不一样就是不一样。
 *
 * 用法：
 *   node tools/render-verify/round27_c_compare.mjs <基线A.json> <复跑A.json> [<基线B.json> <复跑B.json> ...]
 *   node tools/render-verify/round27_c_compare.mjs --selftest    # 闸自检，用存档造坏输入，只打印、不写产物
 * 产物：无（只打印）
 *
 * 退出码（**第二十九轮 E2 补**，此前这一支没有退出码——`EXIT=0` 与「全表无差异」无关）：
 *   0 = 失败项 0（每一对都逐值相同）；1 = 有任一叶子值不同、或某一对的用例集合不同；
 *   2 = 用法不对（参数个数不是偶数）。
 *   ⚠️ **「不同就判红」不是新增判据**：本脚本存在的全部意义就是「两次跑必须逐值相同」，
 *      它一直在控制台印 `不同 N 个`，只是之前没有把这个数接到退出码上。
 *      对账口径（不归一、不容差）一个字节没动。
 */
import { readFileSync, writeFileSync, mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { resolve, join } from 'node:path'
import { ROOT } from './paths.mjs'
import { 判据, 自检, 失败 } from './gates.mjs'

const ARGS = process.argv.slice(2)
const files = ARGS.filter((item) => item !== '--selftest')

/** 把 items 摊成 `id → (叶子路径 → 值)`；数组按下标展开，保证「同一个位置同一个量」。 */
const flatten = (items) => {
  const out = new Map()
  for (const item of items) {
    const flat = new Map()
    const walk = (node, path) => {
      if (node === null || typeof node !== 'object') {
        flat.set(path, node)
        return
      }
      if (Array.isArray(node)) {
        node.forEach((child, index) => walk(child, `${path}[${index}]`))
        return
      }
      for (const [key, value] of Object.entries(node)) walk(value, path ? `${path}.${key}` : key)
    }
    walk(item.value, '')
    out.set(item.id, { name: item.name, complaint: item.complaint, flat })
  }
  return out
}

const read = (file) => flatten(JSON.parse(readFileSync(resolve(ROOT, file), 'utf8')).editor.items)

/**
 * 跑一遍对账，返回**失败项清单**（`gates.mjs` 的 `失败(项, 详情)`）。
 * 主流程与 `--selftest` 走的是同一个函数——自检另写一套比较器不算数。
 */
function 对账(list) {
  const 清单 = []
  for (let at = 0; at < list.length; at += 2) {
    const [baseFile, rerunFile] = [list[at], list[at + 1]]
    const base = read(baseFile)
    const rerun = read(rerunFile)
    console.log('\n================ ' + baseFile + '\n            vs ' + rerunFile + ' ================')
    const ids = [...base.keys()]
    if (ids.join(',') !== [...rerun.keys()].join(',')) {
      console.log('❌ 用例集合不同：', ids.join(','), 'vs', [...rerun.keys()].join(','))
      清单.push(失败('用例集合不同 ' + baseFile + ' vs ' + rerunFile,
        ids.join(',') + ' → ' + [...rerun.keys()].join(',') + '（有案例被换掉/丢掉，逐值对账失去意义）'))
      continue
    }
    let same = 0
    const diffs = []
    for (const id of ids) {
      const left = base.get(id).flat
      const right = rerun.get(id).flat
      const keys = new Set([...left.keys(), ...right.keys()])
      const rowDiffs = []
      for (const key of keys) {
        const a = left.get(key)
        const b = right.get(key)
        if (JSON.stringify(a) === JSON.stringify(b)) { same += 1; continue }
        rowDiffs.push({ key, base: a, rerun: b })
      }
      const name = base.get(id).name
      if (rowDiffs.length === 0) {
        console.log(`  ✅ ${id} (${name}) · ${left.size} 个叶子值逐项相同`)
      } else {
        console.log(`  ❌ ${id} (${name}) · ${rowDiffs.length} / ${keys.size} 个叶子值不同`)
        for (const diff of rowDiffs.slice(0, 12)) {
          console.log(`        ${diff.key}: ${JSON.stringify(diff.base)} → ${JSON.stringify(diff.rerun)}`)
        }
        if (rowDiffs.length > 12) console.log(`        …还有 ${rowDiffs.length - 12} 处`)
        diffs.push({ id, name, count: rowDiffs.length })
        清单.push(失败(id + ' ' + name, rowDiffs.length + ' / ' + keys.size + ' 个叶子值不同——'
          + rowDiffs.slice(0, 3).map((diff) => diff.key).join('、') + (rowDiffs.length > 3 ? ' …' : '')))
      }
    }
    console.log(`  ── 合计：叶子值相同 ${same} 个；不同 ${diffs.reduce((sum, row) => sum + row.count, 0)} 个`
      + (diffs.length ? `（涉及 ${diffs.map((row) => row.name).join(' · ')}）` : '（全表无差异）'))
  }
  return 清单
}

/** 判据：`对账()` 返回的就是失败项清单，这里只是把它塞进 `自检()` 要的形状。 */
const 判 = ({ 结果 }) => 结果

if (ARGS.includes('--selftest')) {
  // 坏输入用**真实存档**造，不是编的：拿 #38 那一轮的产物复制两份，
  // 一份原样（自比必然全同）、一份改掉一个数值叶子、一份删掉一个用例。
  const 源 = resolve(ROOT, 'target/probe/browser/r24_article38_before_result.json')
  const 目录 = mkdtempSync(join(tmpdir(), 'r27c-'))
  const 原样 = join(目录, 'a.json')
  const 改叶子 = join(目录, 'b.json')
  const 少用例 = join(目录, 'c.json')
  const 存档 = JSON.parse(readFileSync(源, 'utf8'))
  writeFileSync(原样, JSON.stringify(存档), 'utf8')

  const 改过 = JSON.parse(JSON.stringify(存档))
  let 命中 = null
  const 改 = (node, path) => {
    if (命中 || node === null || typeof node !== 'object') return
    if (Array.isArray(node)) { node.forEach((child, index) => 改(child, path + '[' + index + ']')); return }
    for (const key of Object.keys(node)) {
      const value = node[key]
      if (命中) return
      if (typeof value === 'number') { node[key] = value + 1; 命中 = path + '.' + key; return }
      if (value && typeof value === 'object') 改(value, path + '.' + key)
    }
  }
  改(改过.editor.items[0].value, 'editor.items[0].value')
  writeFileSync(改叶子, JSON.stringify(改过), 'utf8')

  const 删掉 = JSON.parse(JSON.stringify(存档))
  const 被删 = 删掉.editor.items.pop()
  writeFileSync(少用例, JSON.stringify(删掉), 'utf8')

  process.exitCode = 自检('27-C round27_c_compare（逐值对账，不归一不容差）', 判, [
    { 名: '真实存档自比（' + 存档.editor.items.length + ' 条用例）', 数据: { 结果: 对账([原样, 原样]) }, 期望: 0,
      备注: '同一份文件对同一份文件' },
    { 名: '改掉一个数值叶子（' + 命中 + ' +1）', 数据: { 结果: 对账([原样, 改叶子]) }, 期望: 1,
      备注: '差一处也要判红' },
    { 名: '删掉一个用例（' + 被删.id + '）', 数据: { 结果: 对账([原样, 少用例]) }, 期望: 1,
      备注: '用例集合不同' },
  ])
} else {

if (files.length < 2 || files.length % 2 !== 0) {
  console.error('用法: node tools/render-verify/round27_c_compare.mjs <基线.json> <复跑.json> [<基线2> <复跑2> …]')
  console.error('      node tools/render-verify/round27_c_compare.mjs --selftest')
  process.exit(2)
}

process.exitCode = 判据('27-C round27_c_compare（' + (files.length / 2) + ' 对存档逐值对账）',
  判, { 结果: 对账(files) }) ? 1 : 0
}
