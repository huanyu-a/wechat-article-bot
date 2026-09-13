/**
 * 探针页面共用的「扩展集 + 测量」。
 *
 * 抽出来的原因：`probe.js`（14 组「改前 / 改后」对照）与 `probe_all.js`（全量 79 样例）
 * 必须用**同一套**扩展集与**同一套**测量口径，否则两组数字不能放在一张表里比。
 */
import StarterKit from '@tiptap/starter-kit'
import Image from '@tiptap/extension-image'
import Link from '@tiptap/extension-link'
import TextAlign from '@tiptap/extension-text-align'
import { TableKit } from '@tiptap/extension-table'
import { BackgroundColor, Color, FontSize, LineHeight, TextStyle } from '@tiptap/extension-text-style'

import * as current from '../../../webui/src/editorExtensions.js'
import * as legacy from './legacyExtensions.js'

const optional = (module, name) => (module[name] ? [module[name]] : [])

/** @param {'legacy'|'current'} variant */
export function extensionSet(variant) {
  const m = variant === 'legacy' ? legacy : current
  return [
    StarterKit.configure({ link: false, codeBlock: !m.MarkflowCodeBlock, bold: !m.MarkflowBold }),
    ...optional(m, 'MarkflowBold'),
    m.StyledSection, m.StyledDiv, m.StyledInlineDiv,
    ...optional(m, 'RawSvg'), ...optional(m, 'RawMath'), ...optional(m, 'PreservedEmptySpan'),
    ...optional(m, 'MarkflowCodeBlock'),
    m.FigureCaption, m.Figure,
    Image.configure({ inline: false, allowBase64: false }),
    Link.configure({ openOnClick: false }),
    TableKit.configure({ table: { resizable: true } }),
    TextStyle, Color, BackgroundColor, FontSize, LineHeight, m.ParagraphStyle,
    TextAlign.configure({ types: ['heading', 'paragraph', 'blockquote', 'styledSection', 'styledDiv', 'styledInlineDiv', 'figure', 'figureCaption'] }),
    m.PreservedInlineStyle, m.PreservedMarkStyle,
    ...optional(m, 'PreservedClass'), ...optional(m, 'Subscript'), ...optional(m, 'Superscript'),
    m.PreservedTableStyle, m.PreservedRenderId,
  ]
}

export const SETS = { legacy: extensionSet('legacy'), current: extensionSet('current') }

const visible = (element) => {
  const rect = element.getBoundingClientRect()
  return rect.width > 0 && rect.height > 0
}

/**
 * 一个 DOM 子树的「真的画出来了吗」。全部是浏览器侧的量：
 * 盒子尺寸、计算样式、SVG 的 getBBox——jsdom 里这些要么是 0 要么根本没实现。
 *
 * @param {Element} dom      被测量的根（编辑器里是 `.ProseMirror`；参照侧就是容器本身）
 * @param {string}  html     该根的 HTML 快照（参照侧直接传原串，编辑器侧传 `editor.getHTML()`）
 * @param {Element} canvas   `.probe-canvas`，用来往上找整行做 tallest 定位
 */
export function measureDom(dom, html, canvas) {
  const all = [...dom.querySelectorAll('*')]

  const svgs = [...dom.querySelectorAll('svg')]
  const katex = [...dom.querySelectorAll('.katex')]
  const katexDisplays = [...dom.querySelectorAll('.katex-display')]
  const gradients = all.filter((element) => /gradient\(/.test(getComputedStyle(element).backgroundImage))
  // 列表圆点：渲染服务用空的 span 画，靠 border-radius + 背景色成形
  const bullets = all.filter((element) => {
    if (element.tagName !== 'SPAN' || element.childNodes.length) return false
    const style = getComputedStyle(element)
    return parseFloat(style.borderRadius) >= 3 && style.backgroundColor !== 'rgba(0, 0, 0, 0)' && visible(element)
  })
  // 字重：只数「内联写了字重、但没有被 TipTap 提成 <strong>」的元素，以及多出来的 <strong>
  const weighted = all.filter((element) => {
    const inline = /font-weight/.test(element.getAttribute('style') || '')
    return inline && !['STRONG', 'B'].includes(element.tagName)
  })
  const strongs = [...dom.querySelectorAll('strong')]
  // 代码块里的语法高亮：<pre><code> 内带内联样式的 span
  const codeSpans = [...dom.querySelectorAll('pre code span')].filter((element) => element.getAttribute('style'))
  const row = canvas && canvas.closest('.probe-row')

  return {
    html,
    chars: html.length,
    // 归一化文字：只比可见字符（编辑器会在块之间补 <p>、给标记补空格，按空白比全是假阳性）
    text: dom.textContent.replace(/\s+/g, ''),
    textLength: dom.textContent.replace(/\s+/g, '').length,
    nodeCount: all.length,
    visibleNodeCount: all.filter(visible).length,
    zeroBoxNodes: all.filter((element) => !visible(element)).length,
    tagHistogram: all.reduce((acc, element) => {
      const tag = element.tagName.toLowerCase()
      acc[tag] = (acc[tag] || 0) + 1
      return acc
    }, {}),
    svgTotal: svgs.length,
    svgVisible: svgs.filter(visible).length,
    svgBoxes: svgs.map((element) => { const r = element.getBoundingClientRect(); return [Math.round(r.width), Math.round(r.height)] }),
    svgFirstTags: svgs.slice(0, 1).map((element) => [...element.querySelectorAll('*')].slice(0, 6).map((child) => child.tagName.toLowerCase())),
    // 轮播图的特征：<svg> 里套 <foreignObject> + <animateTransform>；普通图标圈两者都没有
    svgForeignObjects: [...dom.querySelectorAll('svg foreignObject')].length,
    svgAnimateTransform: [...dom.querySelectorAll('svg animateTransform')].length,
    katexTotal: katex.length,
    katexVisible: katex.filter(visible).length,
    katexDisplayTotal: katexDisplays.length,
    katexHeights: katex.map((element) => Math.round(element.getBoundingClientRect().height)).filter((h) => h > 0),
    // 公式在编辑器里有没有被「原样当文本」：可见文字里出现 $ 或 \frac 之类就是没渲染
    katexSourceLeak: /\$\$|\\frac|\\begin\{|\\sum_/.test(dom.textContent),
    gradientElements: gradients.length,
    gradientSamples: gradients.slice(0, 3).map((element) => getComputedStyle(element).backgroundImage.slice(0, 90)),
    bulletSpans: bullets.length,
    bulletBoxes: bullets.map((element) => { const r = element.getBoundingClientRect(); return [Math.round(r.width), Math.round(r.height)] }),
    // D35：相邻同款 span 被合并 → 三个胶囊会塌成一个；胶囊 = 有圆角的行内元素
    pillBoxes: all.filter((element) => ['SPAN', 'STRONG', 'EM'].includes(element.tagName)
      && parseFloat(getComputedStyle(element).borderRadius) >= 6 && visible(element))
      .map((element) => ({ text: element.textContent.trim().slice(0, 24), box: [Math.round(element.getBoundingClientRect().width), Math.round(element.getBoundingClientRect().height)] })),
    weightedInline: weighted.length,
    weightedValues: [...new Set(weighted.map((element) => getComputedStyle(element).fontWeight))].sort(),
    strongCount: strongs.length,
    strongText: strongs.slice(0, 6).map((element) => element.textContent.trim().slice(0, 24)),
    // D37：<strong> 上的内联样式（font-size / display / line-height）保住了没有
    strongStyles: strongs.slice(0, 4).map((element) => ({
      text: element.textContent.trim().slice(0, 12),
      fontSize: getComputedStyle(element).fontSize,
      fontWeight: getComputedStyle(element).fontWeight,
      display: getComputedStyle(element).display,
      opacity: getComputedStyle(element).opacity,
      inline: (element.getAttribute('style') || '').slice(0, 80),
    })),
    subscriptCount: dom.querySelectorAll('sub').length,
    superscriptCount: dom.querySelectorAll('sup').length,
    subBox: [...dom.querySelectorAll('sub')].map((element) => { const r = element.getBoundingClientRect(); return [Math.round(r.width), Math.round(r.height)] }),
    supBox: [...dom.querySelectorAll('sup')].map((element) => { const r = element.getBoundingClientRect(); return [Math.round(r.width), Math.round(r.height)] }),
    codeHighlightSpans: codeSpans.length,
    codeHighlightColors: [...new Set(codeSpans.map((element) => getComputedStyle(element).color))],
    codeLang: dom.querySelector('pre')?.getAttribute('data-lang') || null,
    // 行高异常时用来定位「是谁把盒子撑开的」：整行里最高的 5 个元素
    // （扫整行而不是只扫 .ProseMirror——撑开行高的可能就在编辑器外层）
    tallest: (row || dom).querySelectorAll ? [...(row || dom).querySelectorAll('*')].map((element) => ({
      tag: element.tagName.toLowerCase(),
      cls: (element.getAttribute('class') || '').slice(0, 40),
      h: Math.round(element.getBoundingClientRect().height),
    })).filter((item) => item.h > 250).sort((a, b) => b.h - a.h).slice(0, 5) : [],
    // D39：块级 class。**必须看 getHTML() 而不是实时 DOM**——TipTap 的表格节点视图
    // 会在实时 DOM 里自己套一层 `.tableWrapper`，混进来就分不清「保住的」和「它自己加的」。
    blockClasses: [...new Set(all.flatMap((element) => [...element.classList]).filter((name) => name !== 'ProseMirror' && name !== 'tiptap'))],
    htmlClasses: [...new Set([...html.matchAll(/class="([^"]*)"/g)].flatMap((match) => match[1].split(/\s+/)).filter(Boolean))],
    katexFontLoaded: document.fonts ? document.fonts.check('16px KaTeX_Main') : null,
  }
}

/** 编辑器面板：从 `.ProseMirror` 量，HTML 用 `editor.getHTML()`（保存出口的真值）。 */
export function measure(editor, canvas) {
  return measureDom(canvas.querySelector('.ProseMirror'), editor.getHTML(), canvas)
}

/** 字体加载完成后再量一次 KaTeX 高度——不等 webfont，量到的是 fallback 字体的高度。 */
export async function remeasureKatexAfterFonts(results, getCanvas) {
  await document.fonts.ready
  await new Promise((resolve) => setTimeout(resolve, 250))
  for (const result of results) {
    const canvas = getCanvas(result.id)
    if (!canvas) continue
    for (const [side, root] of [['after', canvas.after], ['reference', canvas.reference]]) {
      const katex = [...root.querySelectorAll('.katex')]
      if (result[side]) result[side].katexHeightsAfterFonts = katex.map((element) => Math.round(element.getBoundingClientRect().height)).filter((h) => h > 0)
    }
  }
}
