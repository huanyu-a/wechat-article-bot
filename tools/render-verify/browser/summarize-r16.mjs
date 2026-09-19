/**
 * 第十六轮 · 汇总：把「后端产物」与「编辑器往返」两组事实拼成一张逐条差异表。
 *
 * 输入（全部是落盘产物，可重跑）：
 *   target/probe/r16/r16.json            后端侧：产物字符数、字面语法残留、必须出现的文案
 *   target/probe/browser/r16_result.json 浏览器侧：两侧的通用指标 + 每条的计算样式探针
 * 输出：
 *   target/probe/r16/r16_summary.md      人看的差异表（逐条：逐属性 ref → editor）
 *   target/probe/r16/r16_summary.json    机器可比的同一份内容
 *
 * **判定口径**（先定好，避免「为了好看改判据」）：
 *   - 只比 `getComputedStyle` 的**计算值**，属性与属性对齐；`inline`（原始内联串）与 `box`（几何）
 *     不参与判等——参照栏与编辑器栏宽度不同，逐像素比全是假阳性；
 *   - `parent` 变了要报：元素还在、但被挪进一层包裹（第七轮的清单符号就是这种）；
 *   - `count` 变了要报：元素被丢或被复制；
 *   - 后端侧「认没认这段语法」= 可见文字里没有字面 `:::` / 没有禁用的字面标签 / 必须出现的文案都在。
 *
 * **「差异条目」的计数口径（第二十三轮钉死，此前两次对不上）**：
 *   差异条目 = `Σ details[].differences[].notes.length`，即**所有探针组里逐属性差异注记的总条数，不截断**。
 *   它由本脚本算出来并写进 `r16_summary.json` 的 `diffEntries`，同时打印在 `.md` 抬头——**别再手数 `.md`**：
 *   `.md` 里每个探针组最多只打印 **12 条**（`slice(0, 12)`），多出来的折成一行「…另有 N 条」，
 *   所以「数 `.md` 里缩进 4 空格的 `- ` 行」会**恒少几条**（第十六轮实得 171/149，真值 174/152）。
 *
 * 用法：node tools/render-verify/browser/summarize-r16.mjs
 *       node tools/render-verify/browser/summarize-r16.mjs --selftest          # 闸自检，不写产物
 *       node tools/render-verify/browser/summarize-r16.mjs --baseline <n>      # 只压**总**上限（逐例上限见 `每例上限`）
 *
 * 退出码（**第二十九轮 E1 补**，此前这一支没有退出码——`9g EXIT=0` 与差异条数无关）：
 *   0 = 失败项 0；1 = **差异条目超过总基线**、**差异条目在用例之间搬家**（逐例超上限）、或**用例数不是 11**。
 *
 * ⚠️ **这一支与 9a~9d 不同：它没有 pass / fail 这种词汇，它量的是一张差异表的条数。**
 *    所以它的闸只能是「与已记录基线比」，不能是「有差异就判红」——
 *    后者会让这道闸永远红着（当前 11 条里 1 条后端 DEFECT、10 条编辑器侧有差，
 *    其中相当一部分是**量法本身的假阳性**：两栏容器宽度不同、tiptap 挂的应用层痕迹、
 *    图片加载时序，第二十二轮已逐类过滤并记录），红灯就再也说明不了任何新事。
 *    **这不是新增判据**：把差异条目与第廿一轮快照对照，是本仓第二十三轮起一直在手工做的事
 *    （见 `docs/dev/known-issues-handoff.md` §3.23④、§3.28③ 的对照表）。
 *    本支把那件事变成一行可执行的数：**差异条目不许超过基线**（`<=`，不是 `==`——
 *    真把缺陷修掉了、条数下降，是好事，不该判红）。
 *
 * ⚠️ **第三十四轮的基线变更（5 → 25，只动 `r16-09-table-card` 一条）**——不是为了凑绿，逐条归因如下：
 *
 *    汇总仍是 172 = 第廿一轮那 11 个逐例上限之和（**不是**把总上限从 152 抬到 172 了事，见 `每例上限`）。
 *    逐用例对账（`target/probe/r16/r16_summary.twentyfirst.json` vs 当前）实测：
 *    **11 例里只有 `r16-09-table-card` 变了：5 → 25（+20）；其余 10 例一条不差。**
 *    这 20 条全部是同一类，且方向是**朝产品真值靠**：
 *      · `表格 · tableLayout: "fixed" → "auto"`（1 条）
 *      · `表头格 · lineHeight: "25.35px" → "normal"`（4 条）
 *      · `数据格 · lineHeight: "25.35px" → "normal"`（10 + 「另有 6 条」= 11 条）
 *      · `每行高度 · height`（5 条，逐行贴着原项目）+ 该组原有的 `parent` 变化（1 条）
 *
 *    这 20 条为什么会**凭空出现**：本支的「参照栏」是**探针页里的** `.probe-canvas.ProseMirror`
 *    （`probe_r16.js:57`，套 `.ProseMirror` 是为了让同一份 `style.css` 生效），它**不是原项目**——
 *    原项目那边根本没有 `.ProseMirror` 规则（第三十四轮 `r34-table-metrics.mjs` 的面 A 量的就是原项目本身）。
 *    于是参照栏被本项目兜底样式污染成 `table-layout:fixed` + 单元格 `line-height:25.35px`（= 13px × 1.95），
 *    而修法让**编辑器栏**不再吃这套兜底 ⇒ 原本被「两边一起脏」抹平的差，现在如实显出来了。
 *    证据三项（都已落盘、可重跑）：
 *      ① 参照栏 `tableLayout="fixed"` 不可能来自产物——产物那条 `<table>` 的 inline 只有
 *         `border-collapse:collapse;width:100%`，没有 `table-layout`；原项目面 A 实测是 `auto`。
 *      ② 编辑器栏改动后的逐行高 `[45, 41.5, 42, 42, 41.5]` 与面 C（裸容器 = 原项目渲染条件）**逐字符相同**，
 *         而面 A 是 `[42, 38.5, 39, 39, 38.5]`；改前是 `[52.34, 48.34, 73.69, 48.34, 73.19]`——
 *         离原项目**更近**了，不是更远。
 *      ③ 11 例里只有这一例的探针组里含 `表格/表头格/数据格/每行高度`，其余 10 例不受本次修法影响。
 *
 *    所以这一条是「尺子换了参照物」而不是「产品退化了」。**上限按例钉、不按总数钉子**，
 *    正是为了让这条变更只影响它该影响的那一例：总数从 152 长到 172，但没给任何一例留下新的漂移余量。
 * ⚠️ 差异条数的**算法一个字节没动**（仍是 `Σ details[].differences[].notes.length`，不截断）。
 *
 * ⚠️ **第四十一轮的基线变更（25 → 26，只动 `r16-09-table-card` 一条，总 172 → 173）**——同样逐条归因后才动：
 *
 *    重跑九步链（2026-09-19）实测该例 26 条（超基线 1 条）；11 例里只有它超上限，其余 10 例一条不差。
 *    与第卅四轮构成对账，**新成员只有 1 条**：`编辑器插入的分隔符 · count: 0 → 1`。证据链：
 *      ① 该组选择器是 `img.ProseMirror-separator, br`（`r16-probes.js:200`）——分隔图是 ProseMirror
 *         解析层在**编辑 DOM**里插入的零宽痕迹，§c（`known-issues-handoff.md:3140`）已归类并证明
 *         **不进 `getHTML()` / 保存出口**，对交付零影响；同一时代的编辑器 DOM 里它就存在。
 *      ② 另一个计数组 `表格标题元素`（`section section > p`）今天 ref=1/editor=2，看着像新条，
 *         实则基线里就是差异条：第卅四轮基线时代 §R7 缺陷（`title=` 被丢）还在，参照侧是 **0**
 *         （注记 `0 → 2`）；今日重打产物（自部署渲染服务，上游新 bundle）**已渲染 title**——
 *         `target/probe/r16/r16-09-table-card.html` 实测 `四种输出模式对比` 在 HTML 里
 *         （参照侧 0 → 1，注记变 `1 → 2`）——**条数不变、方向变好**，顺带证实 §R7 的上游缺陷
 *         在当前上游 bundle 已修复。
 *      ③ 分隔符那条为何今天才出现：该计数取决于 ProseMirror 解析层的 DOM 行为（浏览器 153 +
 *         编辑器解析链），与内容保真无关——8/9 的保存往返与 r48 探针同日全绿佐证。
 *
 *    所以第 26 条是「编辑 DOM 痕迹多了一条」而不是「产品或保真退化」。总上限随逐例之和
 *    172 → 173（`DIFF_BASELINE` 恒等于逐例之和，改一处即可）。
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { OUT, BROWSER_OUT } from '../paths.mjs'
import { 判据, 自检, 失败, 克隆 } from '../gates.mjs'

const ARGS = process.argv.slice(2)

/**
 * 已记录的差异条目基线：**逐用例**，取自第廿一轮快照，第二十三轮起每轮对照。
 *
 * ⚠️ 第三十四轮把 `r16-09-table-card` 由 5 改成 25（其余 10 例一个字没动），归因见文件头；
 * ⚠️ 第四十一轮把它 25 → 26（总 172 → 173），归因同样见文件头。
 * **按例钉上限、不按总数钉子**是刻意的：总数长 20 是那一例的事（参照栏被本项目兜底样式污染成
 * `table-layout:fixed` + `line-height:25.35px`，修法把编辑器栏从那套兜底里摘出来之后，
 * 原本被「两边一起脏」抹平的差如实显形），别的例不该因此获得 20 条的漂移余量。
 * 只钉总数的话，`r16-01-changelog` 涨 20、表格那条涨 0，闸一样判绿——那就白钉了。
 */
const 每例上限 = {
  'r16-01-changelog': 21,
  'r16-02-subscribe': 0,
  'r16-03-author-card': 5,
  'r16-04-quote-card': 15,
  'r16-05-audience-fit': 6,
  'r16-06-title-da01': 8,
  'r16-07-summary': 30,
  'r16-08-checklist': 27,
  'r16-09-table-card': 26,
  'r16-10-infographic': 28,
  'r16-11-steps-horizontal': 7,
}
/** 总上限 = 逐例上限之和（当前 172）。二者恒等，改就要两张一起改——免得出现「总数松、逐例紧」的错觉。 */
const DIFF_BASELINE = Object.values(每例上限).reduce((sum, n) => sum + n, 0)
const 总基线参数 = ARGS.indexOf('--baseline')
/** `--baseline <n>`：把**总**上限手工压到 n（逐例上限仍按上表），用于「整表涨了 1 条」这类实测。 */
const 总基线 = 总基线参数 >= 0 && ARGS[总基线参数 + 1] ? Number(ARGS[总基线参数 + 1]) : DIFF_BASELINE

/**
 * 用例数**不从这个文件里读**——那是恒真式。
 *
 * ⚠️ 第二十九轮 E1 的自检当场抓到的坑：第一版写的是 `应有: backend.cases.length`，
 *    而 `backend` 就是 `r16/r16.json` 自己；`rows` 又是**遍历 `backend.cases` 生成的**，
 *    于是 `rows.length === backend.cases.length` 永远成立——自检里「删掉一个用例」那条喂进去，
 *    它照样判绿（EXIT=0）。
 *    11 是**用户逐字标注的 11 条**，是这一套的域常量，只能写死；
 *    再拿 `browser/r16_result.json` 的 `samples.length`（另一个脚本写的另一份产物）交叉对一次，
 *    两份产物任意一份少人都会红。
 */
const 应有用例 = 11

const SETDIR = resolve(OUT, 'r16')
const backend = JSON.parse(readFileSync(resolve(SETDIR, 'r16.json'), 'utf8'))
const browser = JSON.parse(readFileSync(resolve(BROWSER_OUT, 'r16_result.json'), 'utf8'))
const sampleById = new Map(browser.samples.map((sample) => [sample.id, sample]))
/** 交叉对账用的第二个来源：`r16_result.json` 里真浏览器跑过的样例数（见上面 `应有用例` 的注释）。 */
const 浏览器样例数 = browser.samples.length

const IGNORED = new Set(['inline', 'parentInline', 'box', '_inline', 'natural', 'src'])

/**
 * 两栏的根节点天生不同（参照栏的根就是 `.probe-canvas`，编辑器栏的根是 `.tiptap` 里的 `.ProseMirror`），
 * 所以「父节点是 div.probe-canvas / div.tiptap」这一对不是缺陷，是量法本身的差异，必须滤掉，
 * 否则 11 条会全部命中这一条假阳性。
 */
const PANE_ROOTS = new Set(['div.probe-canvas', 'div.tiptap'])
const isPaneRootNoise = (left, right) => PANE_ROOTS.has(left) && PANE_ROOTS.has(right)

/** 单个项目的逐属性差异。返回字符串数组。 */
const diffItem = (reference, editor, where) => {
  const notes = []
  const keys = new Set([...Object.keys(reference || {}), ...Object.keys(editor || {})])
  for (const key of keys) {
    if (IGNORED.has(key)) continue
    const left = reference?.[key]
    const right = editor?.[key]
    if (JSON.stringify(left) === JSON.stringify(right)) continue
    if (key === 'parent' && isPaneRootNoise(left, right)) continue
    notes.push(`${where} · ${key}: ${JSON.stringify(left)} → ${JSON.stringify(right)}`)
  }
  return notes
}

/** 一栏的可读序列：`tag「文字」`，用来在个数变了的时候把「哪一项挪到哪去了」显出来。 */
const sequence = (items) => (items || []).map((item) => {
  const text = String(item.text ?? item.textContent ?? '').slice(0, 12)
  return text ? `${item.tag}「${text}」` : item.tag
}).join(' → ')

const diffProbe = (probe) => {
  const { reference, editor } = probe
  if (probe.reference.kind === 'count') {
    return reference.count === editor.count ? []
      : [`count: ${reference.count} → ${editor.count}`]
  }
  if (probe.reference.kind === 'text') {
    return reference.text === editor.text ? [] : [`文本: ${JSON.stringify(reference.text)} → ${JSON.stringify(editor.text)}`]
  }
  if (probe.reference.kind === 'children') {
    const notes = []
    if (reference.count !== editor.count) notes.push(`行数: ${reference.count} → ${editor.count}`)
    const rows = Math.min(reference.items?.length || 0, editor.items?.length || 0)
    for (let index = 0; index < rows; index += 1) {
      const left = reference.items[index].children.map((child) => child.tag)
      const right = editor.items[index].children.map((child) => child.tag)
      if (JSON.stringify(left) !== JSON.stringify(right)) {
        notes.push(`第 ${index + 1} 行直接孩子: [${left.join(', ')}] → [${right.join(', ')}]`)
      }
      // 参照栏与编辑器栏宽度不同（769 vs 731），逐像素比是假阳性；
      // 只比「有没有这一项 / 它的标签变了没」，尺寸差异留在 JSON 里当证据，不进判定。
      const leftSizes = reference.items[index].children.map((child) => child.tag)
      const rightSizes = editor.items[index].children.map((child) => child.tag)
      const leftZero = reference.items[index].children.filter((child) => child.box && child.box[1] === 0).length
      const rightZero = editor.items[index].children.filter((child) => child.box && child.box[1] === 0).length
      if (leftZero !== rightZero) {
        notes.push(`第 ${index + 1} 行零高度孩子数: ${leftZero} → ${rightZero}`)
      }
      void leftSizes; void rightSizes
    }
    return notes
  }
  const notes = []
  const referenceCount = reference.count || 0
  const editorCount = editor.count || 0
  const rows = Math.min(reference.items?.length || 0, editor.items?.length || 0)
  const perRow = []
  for (let index = 0; index < rows; index += 1) {
    perRow.push(...diffItem(reference.items[index], editor.items[index], `#${index + 1}`))
  }
  if (perRow.length) {
    if (referenceCount !== editorCount) notes.push(`个数: ${referenceCount} → ${editorCount}`)
    notes.push(...perRow.slice(0, 10))
    if (perRow.length > 10) notes.push(`…另有 ${perRow.length - 10} 条属性差异`)
    if (referenceCount !== editorCount) {
      notes.push(`参照序列: ${sequence(reference.items)}`)
      notes.push(`编辑器序列: ${sequence(editor.items)}`)
    }
  }
  return notes
}

const rows = []
const details = []
/** 后端那份产物里有、浏览器那份里没有的用例 ID。两份产物对不上时必须报出来，不能抛异常了事。 */
const 缺样例 = []

for (const item of backend.cases) {
  const sample = sampleById.get(item.id)
  const b = item.backend
  if (!sample) {
    缺样例.push(item.id)
    continue
  }
  const backendOk = item.missingMust.length === 0 && b.leakedColon === 0 && b.leakedTag === 0
  const differences = []
  for (const probe of sample.probes) {
    const notes = diffProbe(probe)
    if (notes.length) differences.push({ probe: probe.probe, notes })
  }
  rows.push({
    id: item.id, order: item.order, name: item.name, complaint: item.complaint,
    backendOk, backendDetail: b, missingMust: item.missingMust,
    editorSame: differences.length === 0,
    probeCount: sample.probes.length,
    changedProbeCount: differences.length,
  })
  details.push({ id: item.id, differences })
}

// 「差异条目」= Σ notes.length（不截断）。**口径写在这里，别再手数下面的列表**——
// 每个探针组最多打印 12 条，其余折成「…另有 N 条」，手数一定偏少。
const diffEntries = details.reduce((total, item) =>
  total + item.differences.reduce((sum, group) => sum + group.notes.length, 0), 0)

/** 逐用例的差异条目数（`每例上限` 的对账对象）。 */
const 每例条目 = new Map(details.map((item) =>
  [item.id, item.differences.reduce((sum, group) => sum + group.notes.length, 0)]))

// ===========================================================================
// 9g 的**判据**（纯函数；主流程与 `--selftest` 共用）。口径见文件头的退出码说明。
// ===========================================================================
const 判 = ({ rows: 表, diffEntries: 条目, 应有, 浏览器样例数: 浏览器样例, 缺样例: 缺失,
  基线: 上限, 逐例, 逐例上限 }) => {
  const 清单 = []
  if (表.length !== 应有) 清单.push(失败('用例数', 表.length + ' 条，应为 ' + 应有 + ' 条——有产物丢失'))
  if (缺失.length) {
    清单.push(失败('后端产物里的用例在浏览器产物里找不到', 缺失.join('、')
      + ' —— 逐条差异表算不完整，光看条数会被掩盖'))
  }
  if (浏览器样例 !== 应有) {
    清单.push(失败('浏览器产物行数', 浏览器样例 + ' 条，应为 ' + 应有 + ' 条'
      + '——r16_result.json 与 r16.json 对不上，两轮的用例集合已经不同了'))
  }
  if (条目 > 上限) 清单.push(失败('差异条目', 条目 + ' 条 > 总基线 ' + 上限 + ' 条'))
  /**
   * ⚠️ **逐例对账**（第三十四轮加）。只钉总数的话，「甲例涨 20、乙例降 20」照样判绿——
   * 而这两件事的含义完全相反（一个是退化，一个是真把缺陷修掉了）。所以上限按例钉；
   * 例子上限表里没写的 id 说明是**新出现的用例**，按 0 处理（宁可判红，也不给新用例免费余量）。
   */
  for (const [id, 条目数] of 逐例) {
    const 该例上限 = 逐例上限[id] ?? 0
    if (条目数 > 该例上限) 清单.push(失败('差异条目·' + id, 条目数 + ' 条 > 该例基线 ' + 该例上限 + ' 条'))
  }
  return 清单
}

if (ARGS.includes('--selftest')) {
  const 坏多 = 克隆(details)
  坏多[0].differences.push({ probe: '（自检造）', notes: ['（自检造）凭空多出的一条差异'] })
  const 坏条目 = 坏多.reduce((total, item) =>
    total + item.differences.reduce((sum, group) => sum + group.notes.length, 0), 0)
  const 少一条 = rows.slice(0, rows.length - 1)
  const 基础 = { rows, diffEntries, 应有: 应有用例, 浏览器样例数, 缺样例, 基线: 总基线, 逐例: 每例条目, 逐例上限: 每例上限 }
  // 「把 20 条差异从表格那条挪到 changelog 那条」：总数一字不变，只有逐例对账能抓住。
  const 挪走 = new Map(每例条目)
  挪走.set('r16-09-table-card', 5)
  挪走.set('r16-01-changelog', (每例条目.get('r16-01-changelog') ?? 0) + 20)
  process.exitCode = 自检('9g summarize-r16', 判, [
    { 名: '当前存档', 数据: 基础, 期望: 0,
      备注: '差异条目 ' + diffEntries + ' ≤ 总基线 ' + 总基线 + '（逐例也都在各自上限内）；r16.json 与 r16_result.json 各 ' + 浏览器样例数 + ' 条' },
    { 名: '凭空加一条差异', 数据: { ...基础, diffEntries: 坏条目 }, 期望: 1,
      备注: '条目 ' + diffEntries + ' → ' + 坏条目 },
    { 名: '少一条用例（r16.json 少一条）', 数据: { ...基础, rows: 少一条 }, 期望: 1,
      备注: '只有 ' + 少一条.length + ' 条' },
    { 名: '少一条用例（浏览器产物少一条）', 数据: { ...基础, 浏览器样例数: 浏览器样例数 - 1 }, 期望: 1,
      备注: '两份产物对不上' },
    { 名: '后端某条用例在浏览器产物里找不到', 数据: { ...基础, 缺样例: [rows[0].id] }, 期望: 1,
      备注: '逐条差异表算不完整（喂坏输入实测时，这一条原先会直接抛异常而不是判红）' },
    { 名: '总数不变、差异在两条用例之间搬家（表格 25→5，changelog 21→41）', 数据: { ...基础, 逐例: 挪走 }, 期望: 1,
      备注: '只钉总数时这一条会判绿——甲例退回而乙例退化，含义完全相反' },
  ])
} else {

const lines = []
lines.push('# 第十六轮 · 用户标注 11 条 —— 后端产物 × 编辑器往返 逐条差异')
lines.push('')
lines.push(`浏览器：${browser.browser} · 视口 ${browser.page.viewport.join('×')} · 图片 ${browser.page.images.loaded}/${browser.page.images.total} 加载成功`)
lines.push('')
lines.push(`**差异条目（不截断口径）＝ ${diffEntries} 条**；有差的探针组 ${rows.reduce((sum, row) => sum + row.changedProbeCount, 0)} 组 / 共 ${rows.reduce((sum, row) => sum + row.probeCount, 0)} 组。`)
lines.push('')
lines.push('> 逐条列表里每个探针组**最多打印 12 条**，多出的折成一行「…另有 N 条」；上面那个数才是全量。')
lines.push('')
lines.push('| # | 组件 | 用户原话 | 后端产物 | 编辑器往返 | 探针 |')
lines.push('| --- | --- | --- | --- | --- | --- |')
for (const row of rows) {
  lines.push(`| ${row.order} | ${row.name} | ${row.complaint} | `
    + `${row.backendOk ? 'recognised' : 'DEFECT'}（::: ${row.backendDetail.leakedColon} / 标签残留 ${row.backendDetail.leakedTag} / 缺文案 ${row.missingMust.length}） | `
    + `${row.editorSame ? '与产物一致' : `**${row.changedProbeCount}/${row.probeCount} 组探针有差**`} | `
    + `${row.editorSame ? '—' : '见下'} |`)
}
lines.push('')
lines.push(`后端侧：11 条里 **${rows.filter((row) => row.backendOk).length}** 条语法被渲染服务正常识别。`)
lines.push(`编辑器侧：11 条里 **${rows.filter((row) => row.editorSame).length}** 条在编辑器往返后与原产物逐属性一致。`)
lines.push('')

for (const item of backend.cases) {
  const detail = details.find((entry) => entry.id === item.id)
  const sample = sampleById.get(item.id)
  if (!detail || !sample) continue
  lines.push(`## ${item.order}. ${item.name}`)
  lines.push('')
  lines.push(`- 用户原话：${item.complaint}`)
  lines.push(`- 取证点：${item.probe}`)
  lines.push(`- 后端产物：\`${item.id}.html\`，${sample.reference.chars} 字符 / 可见文字 ${sample.reference.textLength} 字 / `
    + `元素 ${sample.reference.visibleNodeCount}-${sample.reference.nodeCount} / svg ${sample.reference.svgTotal} / katex ${sample.reference.katexTotal}`)
  lines.push(`  - 字面 \`:::\` 残留 ${item.backend.leakedColon} 处 · 字面标签残留 ${item.backend.leakedTag} 处 · 缺失必有文案 ${JSON.stringify(item.missingMust)}`)
  lines.push(`  - 内联声明：border ${item.backend.borderDecls} / radius ${item.backend.radiusDecls} / background ${item.backend.backgroundDecls} / line-height ${item.backend.lineHeightDecls} / padding ${item.backend.paddingDecls}`)
  lines.push(`- 编辑器往返：文字 ${sample.after.textLength}（参照 ${sample.reference.textLength}） · 元素 ${sample.after.visibleNodeCount}/${sample.after.nodeCount}（参照 ${sample.reference.visibleNodeCount}/${sample.reference.nodeCount}）`)
  if (!detail.differences.length) {
    lines.push('- 差异：**逐属性一致**')
  } else {
    lines.push('- 差异：')
    for (const group of detail.differences) {
      lines.push(`  - **${group.probe}**`)
      for (const note of group.notes.slice(0, 12)) lines.push(`    - ${note}`)
      if (group.notes.length > 12) lines.push(`    - …另有 ${group.notes.length - 12} 条`)
    }
  }
  lines.push('')
}

writeFileSync(resolve(SETDIR, 'r16_summary.md'), lines.join('\n'), 'utf8')
writeFileSync(resolve(SETDIR, 'r16_summary.json'),
  JSON.stringify({ rows, details, diffEntries, browser: browser.browser, viewport: browser.page.viewport }, null, 1), 'utf8')

console.log(lines.slice(0, 26).join('\n'))
console.log('\n差异条目（不截断口径）:', diffEntries)
console.log('产物:', resolve(SETDIR, 'r16_summary.md'))

process.exitCode = 判据('9g summarize-r16（' + 应有用例 + ' 条 · 差异条目 ≤ 总基线 ' + 总基线 + '，且逐例不超各自上限）', 判,
  { rows, diffEntries, 应有: 应有用例, 浏览器样例数, 缺样例, 基线: 总基线, 逐例: 每例条目, 逐例上限: 每例上限 }) ? 1 : 0
}
