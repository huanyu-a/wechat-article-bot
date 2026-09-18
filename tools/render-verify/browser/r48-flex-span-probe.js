/**
 * D48 定点探针（第 40 轮）：「编辑器把一个带样式的 <span> 按内联格式拆成多个」。
 *
 * 缺陷（known-issues-handoff.md D48）：渲染产物
 *     <section style="display:flex">…<span style="flex:1"><strong>时间</strong>：每天…</span></section>
 * 进编辑器后，同一个源 span 拆成两个 `flex:1 1 0%` 的兄弟 span，flex 行从
 * 「圆点 + 一栏正文」变成「圆点 + 左标签 / 右正文」两栏。
 *
 * 机制假设：ProseMirror 的 DOMSerializer 只保持「打开栈」里与 node.marks **按序相等的前缀**
 * （prosemirror-model to_dom.js serializeFragment 的 keep 循环），而 node.marks 按 schema rank
 * 排序；TipTap 的 marks 对象键序 = 扩展按 priority 降序稳定排序后的注册序。本项目
 * MarkflowBold 注册在 TextStyle 之前 ⇒ bold 的 rank 在 textStyle 之前 ⇒ 文本从 bold 段
 * 进入非 bold 段时整个栈（含外层 span）被关闭重开 ⇒ 同一源 span 拆成两个。
 *
 * 判据（每个样例都量）：
 *   ① schema 的 marks 键序（Object.keys(editor.state.schema.marks)）——修复前 bold 在
 *      textStyle 前，修复后反过来；
 *   ② 实时 DOM 里每个 flex 行的 `flex:1` 栏数——缺陷形态是 [2,…]，正确形态是 [1,…]；
 *   ③ getHTML() 出口里每行的 `flex:1` span 数——与 ② 同判（保存走的就是它）；
 *   ④ 出口与入口的逐字符一致率（sanity，不强求相等：CSSOM 展开简写是既有行为）。
 *
 * 样例 4（badges 三连，D35 的守门样例）必须在修复后**保持 3 个独立 span**——
 * 修复只改 mark 的嵌套顺序，不允许把「相邻同款 span 重新合并」这个旧缺陷带回来。
 *
 * 用法：见同目录 r48-flex-span.mjs（驱动器）。产物：window.__r48Result。
 */
import './probe.css'
import '../../../webui/src/style.css'
import { Editor } from '@tiptap/core'
import { SETS, extensionSet } from './editor-setup.js'

const SAMPLES = [
  {
    id: 'd48-flex-row-strong',
    note: 'D48 原始形态：一个 flex:1 源 span 内含 <strong>时间</strong> + 正文',
    html: '<section style="display:flex;align-items:center">'
      + '<span style="width:6px;height:6px;border-radius:50%;background-color:#27ae60;margin-right:12px"></span>'
      + '<span style="flex:1"><strong>时间</strong>：每天 09:00 更新</span>'
      + '</section>',
  },
  {
    id: 'd48-nested-colored',
    note: '嵌套带色子 span：实测会把 flex 行拆成 3 栏的形态',
    html: '<section style="display:flex;align-items:center">'
      + '<span style="width:6px;height:6px;border-radius:50%;background-color:#27ae60;margin-right:12px"></span>'
      + '<span style="flex:1"><strong>时间</strong>：每天 <span style="color:#e74c3c">09:00</span> 更新</span>'
      + '</section>',
  },
  {
    id: 'd48-flex-row-plain',
    note: '无内联标记的对照：flex:1 源 span 内只有纯文本（历史行为就是 1 栏）',
    html: '<section style="display:flex;align-items:center">'
      + '<span style="width:6px;height:6px;border-radius:50%;background-color:#27ae60;margin-right:12px"></span>'
      + '<span style="flex:1">：每天 09:00 更新</span>'
      + '</section>',
  },
  {
    id: 'd48-badges-three',
    note: 'D35 守门样例：三个相邻同款 span 必须保持 3 个独立盒子（不允许被合并回去）',
    html: '<span style="display:inline-flex;padding:4px 12px;border-radius:999px">A</span>'
      + '<span style="display:inline-flex;padding:4px 12px;border-radius:999px">B</span>'
      + '<span style="display:inline-flex;padding:4px 12px;border-radius:999px">C</span>',
  },
  {
    id: 'd48-colored-bold',
    note: '染色 + 加粗（回归观察项）：修复会改变 strong/span 的嵌套方向，形状必须仍完整',
    html: '<p><span style="color:#e74c3c"><strong>红色加粗</strong>与普通文字</span></p>',
  },
]

/** 量一个编辑器实例：marks 键序、每个 flex 行的栏数（实时 DOM + 出口 HTML）、出口 HTML。 */
function measureSample(sample, editor) {
  const dom = editor.view.dom

  const flexRowCount = (rootHtml) => {
    const holder = document.createElement('div')
    holder.innerHTML = rootHtml
    return [...holder.querySelectorAll('p')]
      .map((p) => [...p.querySelectorAll('span')].filter((s) => /flex:\s*1/.test(s.getAttribute('style') || '')).length)
      .filter((cols) => cols > 0)
  }

  const liveRows = [...dom.querySelectorAll('p')]
    .map((p) => [...p.querySelectorAll('span')].filter((s) => /flex:\s*1/.test(s.getAttribute('style') || '')).length)
    .filter((cols) => cols > 0)

  const exitHtml = editor.getHTML()
  const collectTextNodes = (node, out) => {
    if (node.type === 'text') out.push({ text: node.text, marks: node.marks })
    ;(node.content || []).forEach((child) => collectTextNodes(child, out))
  }
  const docMarks = []
  collectTextNodes(editor.state.doc.toJSON(), docMarks)
  ;(window.__r48Editors = window.__r48Editors || {})[sample.id] = editor
  return {
    marksOrder: Object.keys(editor.state.schema.marks),
    liveFlexCols: liveRows,
    exitFlexCols: flexRowCount(exitHtml),
    exitHtml,
    docMarks,
    textLength: editor.state.doc.textContent.length,
  }
}

window.mountR48 = async () => {
  const root = document.getElementById('probe-root')
  root.innerHTML = ''
  const results = []

  for (const sample of SAMPLES) {
    const row = document.createElement('section')
    row.className = 'probe-row'
    row.dataset.sample = sample.id
    const title = document.createElement('h2')
    title.textContent = sample.id
    const note = document.createElement('p')
    note.className = 'probe-note'
    note.textContent = sample.note
    const canvas = document.createElement('div')
    canvas.className = 'probe-canvas'
    row.append(title, note, canvas)
    root.append(row)

    // 只用 current 扩展集：D48 量的是「当前代码在真浏览器里的行为」。
    const editor = new Editor({ element: canvas, extensions: extensionSet('current'), content: '<p></p>' })
    editor.commands.setContent(sample.html, false)
    results.push({ id: sample.id, note: sample.note, ...measureSample(sample, editor) })
    // 不销毁：销毁会把 DOM 清掉，截图就没东西了。
  }

  document.getElementById('probe-meta').textContent =
    `浏览器 ${navigator.userAgent.match(/Chrome\/[\d.]+/)?.[0] || navigator.userAgent}`
    + ` · 视口 ${window.innerWidth}×${window.innerHeight}`
  window.__r48Result = results
  window.__r48Done = true
  return results
}

// SETS 仍被引入以保持与其它探针同一份扩展集来源；标记已用，避免被当成死代码裁掉。
void SETS
