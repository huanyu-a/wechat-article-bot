/**
 * 第十六轮 · 用户在编辑器里逐条标注的 11 条渲染缺陷 —— 真实浏览器取证。
 *
 * 与 `probe_all.js` 的关系：**共用同一套扩展集与同一套基础测量**（`editor-setup.js`），
 * 另外给每一条挂一组「这条到底在抱怨什么」的**计算样式探针**（`PROBES`）。
 * 之所以不能只靠通用指标：这 11 条里有 8 条抱怨的是**边框 / 圆角 / 背景 / 行高 / 列表符号**
 * ——这些在字符数、元素数、SVG 数量里几乎全是 0 差异，只有 `getComputedStyle` 量得出来。
 *
 * 仍然是一行一条、左右两栏：
 *   左 = 渲染服务的 HTML 产物（原样注入，套同一个 `.ProseMirror` 样式上下文）
 *   右 = 同一份产物 `setContent` 进当前编辑器再量
 *
 * **探针只记事实，不下结论**；归属判定在 `summarize-r16.mjs`。
 *
 * 三条通用量法（避免「选择器写死一层，编辑器多包一层就量不到」这种假阴性——
 * 第七轮和第十六轮的清单符号都踩过这个）：
 *   - `selector` 是**后代**匹配（`querySelectorAll`），不写 `:scope >` 这种层级断言；
 *   - 每一项都回带 `parent`（父元素标签 + 父元素的行内样式），于是「元素还在、但被挪进了一层 <p>」
 *     是看得见的，而不是变成 count=0；
 *   - 需要「这一行的孩子分别是什么」时用 `children: true`，直接列出直接子元素。
 */
import './probe.css'
import '../../../webui/src/style.css'
import { Editor } from '@tiptap/core'
import { SETS, measureDom, measure, remeasureKatexAfterFonts } from './editor-setup.js'

// 探针定义（`PROBES` / `runProbe`）已搬到 `r16-probes.js` —— 真实应用那一支（`r16-live-editor.mjs`）
// 必须跑**同一组定义**，两边数字才能放在一张表里比。文件里剩下的只有「怎么摆两栏」。
import { PROBES, runProbe } from './r16-probes.js'

window.mountAll = async (samples) => {
  const root = document.getElementById('probe-root')
  root.innerHTML = ''
  const results = []

  for (const sample of samples) {
    const row = document.createElement('section')
    row.className = 'probe-row'
    row.dataset.sample = sample.id
    const title = document.createElement('h2')
    title.textContent = `${sample.id} · ${sample.syntax}`
    const note = document.createElement('p')
    note.className = 'probe-note'
    note.textContent = sample.note || ''
    const panes = document.createElement('div')
    panes.className = 'probe-panes'
    row.append(title, note, panes)
    root.append(row)

    // ---- 左：渲染服务的产物 ----
    const refPane = document.createElement('div')
    refPane.className = 'probe-pane reference'
    const refHeader = document.createElement('header')
    const refCanvas = document.createElement('div')
    // 套 .ProseMirror 是为了让同一份 style.css 生效——否则参照侧量到的是「没样式」的裸 DOM，
    // 拿一个没样式的基线去比编辑器，比的就不是同一件事了。
    refCanvas.className = 'probe-canvas ProseMirror'
    refCanvas.dataset.variant = 'reference'
    refCanvas.innerHTML = sample.html
    refPane.append(refHeader, refCanvas)
    panes.append(refPane)
    const reference = measureDom(refCanvas, sample.html, refCanvas)
    refHeader.innerHTML = `<span>参照（渲染服务产物）</span><span>${reference.textLength} 字 · `
      + `${reference.visibleNodeCount}/${reference.nodeCount} 个元素有盒子</span>`

    // ---- 右：编辑器往返 ----
    const edPane = document.createElement('div')
    edPane.className = 'probe-pane after'
    const edHeader = document.createElement('header')
    const canvas = document.createElement('div')
    canvas.className = 'probe-canvas'
    canvas.dataset.variant = 'after'
    edPane.append(edHeader, canvas)
    panes.append(edPane)

    const editor = new Editor({ element: canvas, extensions: SETS.current, content: '<p></p>' })
    editor.commands.setContent(sample.html, false)
    const after = measure(editor, canvas)
    edHeader.innerHTML = `<span>编辑器（当前扩展集）</span><span>${after.textLength} 字 · `
      + `${after.visibleNodeCount}/${after.nodeCount} 个元素有盒子</span>`

    // 这一条的专属探针：两侧同题同量
    const editorRoot = canvas.querySelector('.ProseMirror')
    // 探针只能看到渲染结果；「节点属性里到底有什么」要往回看编辑器状态。
    // 第十六轮定位「表格 preservedStyle 为什么没进实时 DOM」时用得上。
    window.__r16Editors = window.__r16Editors || {}
    window.__r16Editors[sample.id] = editor
    const probes = (PROBES[sample.id] || []).map((probe) => ({
      probe: probe.label,
      reference: runProbe(refCanvas, probe),
      editor: runProbe(editorRoot, probe),
    }))

    results.push({ id: sample.id, syntax: sample.syntax, category: sample.category, note: sample.note, reference, after, probes })
  }

  await remeasureKatexAfterFonts(results, (id) => {
    const row = document.querySelector(`[data-sample="${id}"]`)
    if (!row) return null
    return {
      reference: row.querySelector('.probe-canvas[data-variant="reference"]'),
      after: row.querySelector('.probe-canvas[data-variant="after"]'),
    }
  })
  window.__probeDone = true
  return results
}

document.getElementById('probe-meta').textContent =
  `浏览器 ${navigator.userAgent.match(/Chrome\/[\d.]+/)?.[0] || navigator.userAgent}`
  + ` · 视口 ${window.innerWidth}×${window.innerHeight}`
  + ` · KaTeX 字体已加载：${document.fonts ? document.fonts.check('16px KaTeX_Main') : '未知'}`
