/**
 * 全量探针：把 `target/probe/components/` 里的 **79 个样例全部**放进真实浏览器，
 * 一行一个样例、左右两栏：
 *
 *   左 = 参照（渲染服务自己的 HTML 产物，原样注入，套同一个 `.ProseMirror` 样式上下文）
 *   右 = 编辑器（当前扩展集把同一份产物 setContent 之后再 getHTML 的结果）
 *
 * 判定口径（只看两栏的实测差，不做人工挑样本）：
 *   - 参照侧自己就是 0 字符 → 上游没产出，编辑器无从谈起；
 *   - 两栏归一化文字不同 → 编辑器改了可见内容；
 *   - 参照侧有的结构特征（SVG / KaTeX / 渐变 / 圆点 / 胶囊 / 上下标 / 代码高亮）在编辑器侧没了 → 有损；
 *   - 编辑器侧一个非零盒子都没有 → 没画出来。
 */
import './probe.css'
import '../../../webui/src/style.css'
import { Editor } from '@tiptap/core'
import { SETS, measureDom, measure, remeasureKatexAfterFonts } from './editor-setup.js'

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

    // ---- 左：渲染服务自己的产物 ----
    const refPane = document.createElement('div')
    refPane.className = 'probe-pane reference'
    const refHeader = document.createElement('header')
    // 套 .ProseMirror 是为了让同一份 style.css 生效——否则参照侧量到的是「没样式」的裸 DOM，
    // 拿一个没样式的基线去比编辑器，比的就不是同一件事了。
    const refCanvas = document.createElement('div')
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

    // editor 不销毁：销毁会把 DOM 清掉，截图就没东西了。页面用完即弃。
    results.push({ id: sample.id, syntax: sample.syntax, category: sample.category, note: sample.note, reference, after })
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
