/**
 * 真实浏览器里的编辑器渲染对照（14 组「改前 / 改后」，D30–D39 的代表性样例）。
 *
 * 与 jsdom 探针（`target/probe/fe_harness.mjs`）的区别——这一份跑在真 Chrome 里：
 *   - 真的有 CSS 层叠与布局，能拿到 `getBoundingClientRect()` 的真实盒子尺寸；
 *   - 真的解析 SVG、真的算 `linear-gradient`、真的加载 KaTeX 的 webfont；
 *   - 真的跑 ProseMirror 的 DOM 观察器与视图更新。
 * jsdom 只能证明「扩展注册与解析链路对」，证明不了「真的画出来了」——像素/盒子是这一份的事。
 *
 * 扩展集与测量口径都在 `./editor-setup.js`（与全量探针 `probe_all.js` 共用同一份）。
 *   before = `./legacyExtensions.js`（由 prepare-legacy.mjs 从 git HEAD 生成）
 *   after  = `../../../webui/src/editorExtensions.js`（当前工作树）
 */
import './probe.css'
import '../../../webui/src/style.css'
import { Editor } from '@tiptap/core'
import { SETS, measure, remeasureKatexAfterFonts } from './editor-setup.js'

/** 在页面上把一批样例「改前 / 改后」并排渲染出来，返回测量结果。 */
window.mountProbe = async (samples) => {
  const root = document.getElementById('probe-root')
  root.innerHTML = ''
  const results = []

  for (const sample of samples) {
    const row = document.createElement('section')
    row.className = 'probe-row'
    row.dataset.sample = sample.id
    const title = document.createElement('h2')
    title.textContent = `${sample.id} · ${sample.fix}`
    const note = document.createElement('p')
    note.className = 'probe-note'
    note.textContent = sample.note || ''
    const panes = document.createElement('div')
    panes.className = 'probe-panes'
    row.append(title, note, panes)
    root.append(row)

    const panesResult = {}
    for (const variant of ['legacy', 'current']) {
      const pane = document.createElement('div')
      pane.className = 'probe-pane ' + (variant === 'legacy' ? 'before' : 'after')
      const header = document.createElement('header')
      const canvas = document.createElement('div')
      canvas.className = 'probe-canvas'
      canvas.dataset.variant = variant
      pane.append(header, canvas)
      panes.append(pane)

      const editor = new Editor({ element: canvas, extensions: SETS[variant], content: '<p></p>' })
      editor.commands.setContent(sample.html, false)
      const data = measure(editor, canvas)
      header.innerHTML = `<span>${variant === 'legacy' ? '改前（git HEAD 扩展集）' : '改后（当前扩展集）'}</span>`
        + `<span>${data.textLength} 字 · ${data.visibleNodeCount}/${data.nodeCount} 个元素有盒子</span>`
      // editor 不销毁：销毁会把 DOM 清掉，截图就没东西了。页面用完即弃。
      panesResult[variant] = data
    }
    results.push({ id: sample.id, fix: sample.fix, before: panesResult.legacy, after: panesResult.current })
  }

  await remeasureKatexAfterFonts(results, (id) => {
    const canvas = document.querySelector(`[data-sample="${id}"] .probe-canvas[data-variant="current"]`)
    if (!canvas) return null
    return { after: canvas, reference: canvas }
  })
  window.__probeDone = true
  return results
}

document.getElementById('probe-meta').textContent =
  `浏览器 ${navigator.userAgent.match(/Chrome\/[\d.]+/)?.[0] || navigator.userAgent}`
  + ` · 视口 ${window.innerWidth}×${window.innerHeight}`
  + ` · KaTeX 字体已加载：${document.fonts ? document.fonts.check('16px KaTeX_Main') : '未知'}`
