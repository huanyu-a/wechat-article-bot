import { Extension, Mark, Node, mergeAttributes } from '@tiptap/core'
import TipTapBold from '@tiptap/extension-bold'
import TipTapCodeBlock from '@tiptap/extension-code-block'

/**
 * 需要接管「内联样式里会被 TextAlign/ParagraphStyle 认领的那几条声明」的块级节点。
 * 加 `codeBlock` 是因为渲染服务的代码块把配色/内边距/圆角全写在 `<pre style>` 上，
 * 而 TipTap 的 CodeBlock 只声明 language 一个属性——不接管就整段样式丢掉。
 */
const styledBlockTypes = [
  'paragraph',
  'heading',
  'blockquote',
  'horizontalRule',
  'image',
  'styledSection',
  'styledDiv',
  'styledInlineDiv',
  'figure',
  'figureCaption',
  'codeBlock',
]

/**
 * 工具栏「段落样式」下拉读取状态用的类型（只用于读，不用于写）：按顺序取第一个有值的。
 * 比 {@link styledBlockTypes} 窄是有意的——拖到 styledSection/figure 上会把容器的
 * 外边距当成光标所在段落的值显示出来。
 */
export const paragraphStyleTypes = [
  'paragraph',
  'heading',
  'blockquote',
  'styledDiv',
  'styledInlineDiv',
  'figureCaption',
]

const paragraphStyleProperties = {
  marginTop: 'margin-top',
  marginRight: 'margin-right',
  marginBottom: 'margin-bottom',
  marginLeft: 'margin-left',
  textIndent: 'text-indent',
  blockLineHeight: 'line-height',
  letterSpacing: 'letter-spacing',
}

const managedBlockProperties = new Set([
  'text-align',
  'margin',
  ...Object.values(paragraphStyleProperties),
])

function readPreservedStyle(element) {
  if (!element?.style) return null

  const declarations = Array.from(element.style)
    // Dedicated extensions own these declarations so toolbar state stays readable.
    .filter(property => !managedBlockProperties.has(property))
    .map(property => {
      const value = element.style.getPropertyValue(property)
      const priority = element.style.getPropertyPriority(property)
      return `${property}: ${value}${priority ? ' !important' : ''}`
    })

  return declarations.length ? declarations.join('; ') : null
}

export const PreservedInlineStyle = Extension.create({
  name: 'preservedInlineStyle',
  addGlobalAttributes() {
    return [{
      types: styledBlockTypes,
      attributes: {
        inlineStyle: {
          default: null,
          parseHTML: readPreservedStyle,
          renderHTML: attributes => attributes.inlineStyle
            ? { style: attributes.inlineStyle }
            : {},
        },
      },
    }]
  },
})

// Color / BackgroundColor / FontSize / LineHeight 四个扩展只认这几条声明，
// 其余落在标记 span 上的样式（徽章的 padding / border-radius / border、
// 组件的 display / gap / white-space 等）若不接管，序列化时会被静默丢掉——
// 编辑器里「胶囊徽章」退化成一段染色文字，而正文一旦被改动就会把这个退化写进库。
const managedMarkStyleProperties = new Set([
  'color',
  'background',
  'background-color',
  'font-size',
  'line-height',
])

// 按 style 属性原文逐条保留，而不是读 element.style：后者会把简写展开成
// 十几条长写并丢掉 !important，产物和渲染服务给的对不上。
//
// 「被专用扩展接管的声明」只有落在 `<span>` 上时才过滤得掉：Color / BackgroundColor /
// FontSize / LineHeight 四个扩展的 types 默认就是 ['textStyle']，而 textStyle 的
// parseHTML 只认带 style 的 `<span>`。所以 `<strong style="font-size:60px">` 这类
// 语义标签上的声明没有任何扩展接手——再按同一张清单过滤就是纯丢
// （实测 `blk-p-title` 的巨型浅色章节号 `<strong style="font-size:60px;…">` 整条样式消失）。
const emptyPropertyFilter = new Set()

/**
 * 渐变/图片类背景不是 BackgroundColor 能往返的值，必须自己留着。
 *
 * `BackgroundColor` 读的是 `element.style.backgroundColor`，而 `background: linear-gradient(...)`
 * 这种简写按 CSSOM 会把没写到的长写设成初始值，于是它读到的是 `transparent`、
 * 回写出一句 `background-color: transparent`，**渐变底纹整条消失**。
 * 实测 `target/probe/components/in-em-hl.html`：渲染服务的
 * `<span style="background:linear-gradient(120deg,rgba(39,174,96,.1) 0%,rgba(39,174,96,.16) 100%);padding:0 6px;border-radius:4px">`
 * 进编辑器后高亮底色没了（`blk-cta` / `md-hr` / `ctn-breaking` 等 39 处、17 个样例，
 * 6 篇真实稿件里有 4 篇命中）。
 */
const roundedTripBackground = /gradient\(|url\(/i

function keepDeclaration(managed, property, value) {
  if (!managed.has(property)) return true
  return roundedTripBackground.test(value)
}

function readPreservedMarkStyle(element) {
  const raw = element.getAttribute?.('style')
  if (!raw) return null

  const managed = element.tagName === 'SPAN' ? managedMarkStyleProperties : emptyPropertyFilter
  const declarations = raw.split(';')
    .map(declaration => declaration.trim())
    .filter(Boolean)
    .filter(declaration => {
      const separator = declaration.indexOf(':')
      if (separator < 0) return false
      const property = declaration.slice(0, separator).trim().toLowerCase()
      // 值里可能带冒号（`url(data:image/png;base64,…)`），所以按第一个冒号切、其余整体回接。
      const value = declaration.slice(separator + 1)
      return keepDeclaration(managed, property, value)
    })

  return declarations.length ? declarations.join('; ') : null
}

/**
 * 标记（mark）上要接管的类型：不止 `textStyle`。
 *
 * `<strong>` / `<em>` / `<s>` / `<u>` / `<code>` / `<a>` 这些语义标签会各自落成一个
 * TipTap 标记，而它们身上的内联样式此前没有任何属性声明，parse → serialize 一次就没了。
 * `target/probe/components/blk-p-title.editor.html` 是最直接的证据：
 * 渲染服务的 `<strong style="display:block;font-size:60px;line-height:1;opacity:.25">01</strong>`
 * 到了编辑器只剩一个裸 `<strong>01</strong>`，章节号的叠印版式整段塌掉。
 */
const preservedMarkTypes = ['textStyle', 'bold', 'italic', 'strike', 'underline', 'code', 'link']

/**
 * 每个 `<span>` 一个「来源序号」，用来**阻止相邻同款 span 被合并**。
 *
 * ProseMirror 会把「标记集合完全相同」的相邻文本合并成一个文本节点，序列化时也就只剩一个 span。
 * 渲染服务却常把并列的**独立视觉盒子**写成相邻同款 span——最典型的是 `<badges>`：
 *
 *     <span style="display:inline-flex;padding:4px 12px;border-radius:999px">A</span>
 *     <span style="…一模一样…">B</span>
 *     <span style="…一模一样…">C</span>
 *
 * 三个胶囊的样式逐字符相同，于是被合并成**一个**胶囊、内容变成 `ABC`
 * （实测 `target/probe/components/blk-badges.editor.html`：后端 703 字符的样式 → 360，
 * 三个独立胶囊塌成一个）。这是渲染保真问题，不是文本问题。
 *
 * 给每个 span 派生一个互不相同的属性值，标记就不再「相等」，ProseMirror 也就不会合并它们。
 * 该属性只在编辑器内部存在：`renderHTML` 返回空对象，既不进 `getHTML()`（保存/预览的出口），
 * 也不进 `getJSON()` 派生的行文本与 `domBlocks[index].outerHTML`（见
 * `ArticleEditorView.vue:158-166` 的 `editorDocument()`）。
 */
let preservedSpanSequence = 0

function nextPreservedSpanId(element) {
  if (element.tagName !== 'SPAN') return null
  preservedSpanSequence += 1
  return preservedSpanSequence
}

export const PreservedMarkStyle = Extension.create({
  name: 'preservedMarkStyle',
  addOptions() {
    return { types: preservedMarkTypes }
  },
  addGlobalAttributes() {
    return [{
      types: this.options.types,
      attributes: {
        preservedStyle: {
          default: null,
          parseHTML: readPreservedMarkStyle,
          renderHTML: attributes => attributes.preservedStyle
            ? { style: attributes.preservedStyle }
            : {},
        },
        sourceSpanId: {
          default: null,
          parseHTML: nextPreservedSpanId,
          renderHTML: () => ({}),
        },
      },
    }]
  },
})

/**
 * 块级节点上的 `class`。渲染产物里用得极少（实测 77 个组件样例里只有
 * `blk-title` 的 `<section class="tableWrapper">` 一处），但丢了就是丢了——
 * 与 `data-render-id` 同理，这类「渲染器写着、编辑器却不认识」的属性一律接管。
 *
 * 只声明在块级节点上：公式那类 class 驱动的**行内**结构用标记接不住
 * （同名标记不能嵌套），已经整体交给 {@link RawMath}。
 */
const preservedClassBlockTypes = [
  ...styledBlockTypes,
  'bulletList', 'orderedList', 'listItem',
  'table', 'tableRow', 'tableCell', 'tableHeader',
]

export const PreservedClass = Extension.create({
  name: 'preservedClass',
  addOptions() {
    return { types: preservedClassBlockTypes }
  },
  addGlobalAttributes() {
    return [{
      types: this.options.types,
      attributes: {
        preservedClass: {
          default: null,
          parseHTML: element => element.getAttribute('class') || null,
          renderHTML: attributes => attributes.preservedClass
            ? { class: attributes.preservedClass }
            : {},
        },
      },
    }]
  },
})

/**
 * MARKFLOW 的公式（KaTeX）是**纯 class 驱动**的：渲染服务对 `$E=mc^2$` 产出的是
 * `<span class="katex"><span class="katex-html"><span class="base"><span class="mord">…`，
 * 版式规则全在 KaTeX 样式表里，元素本身几乎没有内联样式。
 *
 * 这类结构用 TipTap 的**标记**建模是做不到的：ProseMirror 的同名标记不能嵌套
 * （`prosemirror-model` 里 `excludes == null` 会让标记类型排除它自己，见源码
 * `type.excluded = excl == null ? [type] : …`），十几层 class span 最终只会剩下最里面那一个，
 * 外面全丢。实测（`target/probe/fe_markorder2.mjs`）三层 `<span class="a"><span class="b"><span class="c">X`
 * 解析后只剩 `classedSpan{c}`。
 *
 * 所以这里和 {@link RawSvg} 一样按**不透明原子节点**处理：整棵 `.katex-display` / `.katex`
 * 子树原样存、原样吐，保真度 100%。代价是编辑器里不能在公式内部打字（要改公式就重新渲染排版），
 * 但此前的行为是「打开就散架、改一次正文就把散架的结果写进库」——见
 * `target/probe/components/math-inline.editor.html` 的 471 字符版本：`\frac{1}{3}` 被压成并排的 `3` `1`。
 *
 * 两条规则给不同优先级：块级公式外面还有一层 `<span class="katex-display">`
 * （`text-align:center;margin:1em 0` 在它身上），必须由外层先认领，否则那层会被解包丢掉。
 */
const KATEX_DANGEROUS = /<script\b|on[a-z]+\s*=|javascript:/i

function katexSource(element) {
  const outer = element.outerHTML || ''
  return outer && !KATEX_DANGEROUS.test(outer) ? outer : null
}

function katexElement(source) {
  if (!source || KATEX_DANGEROUS.test(source)) return ['span', {}]
  const template = document.createElement('template')
  template.innerHTML = source
  const element = template.content.firstElementChild
  return element && element.tagName.toLowerCase() === 'span' ? element : ['span', {}]
}

export const RawMath = Node.create({
  name: 'rawMath',
  group: 'inline',
  inline: true,
  atom: true,
  addAttributes() {
    return {
      source: {
        default: null,
        parseHTML: katexSource,
        renderHTML: () => ({}),
      },
    }
  },
  parseHTML: () => [
    { tag: 'span.katex-display', priority: 120 },
    { tag: 'span.katex', priority: 110 },
  ],
  renderHTML: ({ node }) => katexElement(node.attrs.source),
})

/**
 * `<sub>` / `<sup>`：渲染服务的角标（化学式 `H~2~O`、平方 `m^2^`）用的是这两个语义标签，
 * 而编辑器 schema 里没有对应标记，未知标签被解包后只剩文字——下标变成正文数字。
 */
export const Subscript = Mark.create({
  name: 'subscript',
  parseHTML: () => [{ tag: 'sub' }],
  renderHTML: () => ['sub', 0],
})

export const Superscript = Mark.create({
  name: 'superscript',
  parseHTML: () => [{ tag: 'sup' }],
  renderHTML: () => ['sup', 0],
})

// `<svg>` 里的 script / 事件属性 / javascript: URI。渲染服务已先剥过一轮，这里是第二道
// （与后端 ArticleService.cleanMarkflowBody 同一套黑名单语义）。
// 故意不加 g 标志：带 g 的正则用 test() 会记忆 lastIndex，跨次调用结果飘忽。
const SVG_DANGEROUS = /<script\b|on[a-z]+\s*=|javascript:/i

/**
 * MARKFLOW 渲染产物里的 `<svg>`：轮播图（`:::slider` 的**整个组件本体就是一个 SVG 动画**）、
 * `<engage-card>` 的三个图标、任务列表的勾选框都靠它。
 *
 * ProseMirror 的 schema 里没有 svg 节点，未知元素一律被「解包」——标签丢掉、只留文字。
 * 实测 `target/probe/components/ctn-slider.editor.html`：后端 1144 字符的动画轮播
 * （`<animateTransform>` + 三个 `<foreignObject>`）回到编辑器只剩三张上下堆叠的裸 `<img>`，
 * 这正是用户报的「轮播图在编辑器里渲染不出来」；`blk-engage-card` 的三个 48×48 图标圈也全空了。
 *
 * svg 的内部结构（`animateTransform` / `foreignObject` / `path`）没法用常规 ProseMirror 节点建模，
 * 所以整体存成**不透明原子节点**：进编辑器时把 outerHTML 原样收进属性，出去时再解析回元素。
 * 代价是编辑器里不能选中 SVG 内部，但本来也没人需要在编辑器里改图标。
 */
export const RawSvg = Node.create({
  name: 'rawSvg',
  group: 'inline',
  inline: true,
  atom: true,
  addAttributes() {
    return {
      source: {
        default: null,
        parseHTML: element => {
          const outer = element.outerHTML || ''
          return outer && !SVG_DANGEROUS.test(outer) ? outer : null
        },
        // 不把整段 SVG 原文当成 DOM 属性吐出去，由 renderHTML 直接还原元素。
        renderHTML: () => ({}),
      },
    }
  },
  parseHTML: () => [{ tag: 'svg' }],
  renderHTML: ({ node }) => {
    const source = node.attrs.source
    if (!source || SVG_DANGEROUS.test(source)) return ['span', {}]
    const template = document.createElement('template')
    template.innerHTML = source
    const element = template.content.firstElementChild
    return element && element.tagName.toLowerCase() === 'svg' ? element : ['span', {}]
  },
})

/**
 * 空的 `<span style="…">`：渲染服务用它画**没有文字的子元素**，最典型的就是列表圆点——
 * `md-list` 的每一项是
 * `<section style="display:flex"><span style="width:6px;height:6px;border-radius:50%;background-color:#27ae60;margin-right:12px"></span><span style="flex:1">正文</span></section>`，
 * 圆点完全由这个空 span 的尺寸和底色画出来。
 *
 * ProseMirror 的标记只挂在**文本节点**上：一个没有子节点的 span 产不出文本，
 * 标记无处可挂，整个元素被静默丢弃——列表打开就没了圆点。
 * 实测 `target/probe/components/md-list.editor.html`：两个圆点 span 全丢，
 * 只剩 `<p><span style="flex: 1 1 0%;">ZQITEM1</span></p>`。
 * `reg-layout-toc` / `reg-layout-metrics`（技能提示词里的两条版面规则）后端产出的也是这种列表，
 * 同样中招；6 篇真实稿件里 `5.html` 有 7 处。
 *
 * 处理方式与 {@link RawSvg} 一致：整条元素存成**不透明原子节点**，原样进出。
 * 代价是不能选中它内部（本来也没有内部），换来圆点不再消失。
 */
const EMPTY_SPAN_DANGEROUS = /<script\b|on[a-z]+\s*=|javascript:/i

function emptySpanSource(element) {
  const outer = element.outerHTML || ''
  return outer && !EMPTY_SPAN_DANGEROUS.test(outer) ? outer : null
}

function emptySpanElement(source) {
  if (!source || EMPTY_SPAN_DANGEROUS.test(source)) return ['span', {}]
  const template = document.createElement('template')
  template.innerHTML = source
  const element = template.content.firstElementChild
  return element && element.tagName.toLowerCase() === 'span' ? element : ['span', {}]
}

export const PreservedEmptySpan = Node.create({
  name: 'preservedEmptySpan',
  group: 'inline',
  inline: true,
  atom: true,
  addAttributes() {
    return {
      source: {
        default: null,
        parseHTML: emptySpanSource,
        renderHTML: () => ({}),
      },
    }
  },
  parseHTML: () => [{
    tag: 'span[style]',
    // 低于 RawMath 的 110/120：`.katex` 子树里的空 span 归公式节点管，别在这里截胡。
    priority: 105,
    // 只有真的没有子节点时才认领；普通 span 交还给标记规则（返回 false = 本规则不适用）。
    getAttrs: element => (element.childNodes.length === 0 ? null : false),
  }],
  renderHTML: ({ node }) => emptySpanElement(node.attrs.source),
})

/**
 * 粗体标记：不再从 `font-weight` 内联样式推断。
 *
 * TipTap 自带的 Bold 有一条 `{ style: 'font-weight', getAttrs: value => /^(bold(er)?|[5-9]\d{2,})$/.test(value) }`
 * 规则（`@tiptap/extension-bold` 的 `parseHTML`）——**任何**元素只要内联 `font-weight` ≥ 500 就落成粗体标记。
 * MARKFLOW 渲染产物里这种写法极多（实测 77 个组件样例 + 6 篇真实稿件里共 **164 处**
 * `<span style="font-weight:600">` 这类标记，分布在 21 个样例、**6 篇真实稿件全部命中**），
 * 于是每个这样的 span 都被套上一层 `<strong>`：
 *
 *   - 渲染服务给的 `font-weight:600` 被 `<strong>` 的 `bold`(=700) 顶掉，字重变粗；
 *   - `font-weight:800` 的章节号反而被压到 700，变细；
 *   - 语义上也被改写成「加粗」，而它原本只是排版数值。
 *
 * 去掉这条规则后，`font-weight` 只由 {@link PreservedMarkStyle} 原样保留，
 * `<strong>` / `<b>` 两种真正的语义标签照旧解析。
 * `style: 'font-weight=400'` 那条保留——它负责在粗体里取消粗体，不能少。
 */
export const MarkflowBold = TipTapBold.extend({
  parseHTML() {
    return [
      { tag: 'strong' },
      { tag: 'b', getAttrs: node => node.style.fontWeight !== 'normal' && null },
      { style: 'font-weight=400', clearMark: mark => mark.type.name === this.name },
    ]
  },
})

/**
 * 代码块：保留语法高亮的配色 span 与 `data-lang`。
 *
 * TipTap 自带的 CodeBlock 规格里写死了 `marks: ""`（`@tiptap/extension-code-block`），
 * 节点 content 又是 `text*`——高亮标记无处可落，被 ProseMirror 直接丢掉。
 * 实测 `target/probe/components/md-code.html`：渲染服务的
 * `<pre data-lang="js"><code><span style="color:#c678dd">const</span> zqcode = <span style="color:#d19a66">1</span>;</code></pre>`
 * 进编辑器后 `data-lang` 与两个高亮 span 全没，只剩黑底白字的 `const zqcode = 1;`
 * （mermaid 图里的配色同理，见 `diagram-mermaid`：样式长度 463 → 308）。
 * 而 `ArticleEditorView.vue` 的保存走的是同一个 `getHTML()`，正文改动一次就把褪色结果写进库。
 *
 * 解法是**只放开 textStyle 一种标记**（`marks: 'textStyle'`）：
 *   - 颜色/字号能挂进去了，Syntax highlighting 的 `<span style="color:#…">` 原样往返；
 *   - 代码仍然是普通 text 节点、仍然可以正常编辑（`target/probe/fe_codeblock.mjs` 实测
 *     codeBlock 内可编辑字符数 17 → 17，不是被换成不可编辑的原子节点）；
 *   - 只放 textStyle 而不放开全部标记，是为了不让粗体/斜体/链接这类语义标记污染代码语义。
 *
 * `data-lang` 是渲染服务给代码块标的语言（复制按钮/语言标签用），此前没有任何属性声明，
 * 一进编辑器就丢，这里一并接管。
 */
export const MarkflowCodeBlock = TipTapCodeBlock.extend({
  marks: 'textStyle',
  addAttributes() {
    return {
      ...this.parent?.(),
      dataLang: {
        default: null,
        parseHTML: element => element.getAttribute('data-lang') || null,
        renderHTML: attributes => (attributes.dataLang ? { 'data-lang': attributes.dataLang } : {}),
      },
      // 内层 `<code>` 的内联样式。TipTap 的 CodeBlock 自己渲染 `<code>`，
      // 属性挂不到它身上，只能自己读、自己写。
      codeStyle: {
        default: null,
        parseHTML: element => element.firstElementChild?.getAttribute('style') || null,
        renderHTML: () => ({}),
      },
    }
  },
  renderHTML({ node, HTMLAttributes }) {
    const codeAttributes = {}
    if (node.attrs.language) codeAttributes.class = this.options.languageClassPrefix + node.attrs.language
    if (node.attrs.codeStyle) codeAttributes.style = node.attrs.codeStyle
    return [
      'pre',
      mergeAttributes(this.options.HTMLAttributes, HTMLAttributes),
      ['code', codeAttributes, 0],
    ]
  },
})

// MARKFLOW 渲染产物把整个组件设计放在 <table>/<td>/<th> 的内联样式里// （步骤卡的 width:33%、padding、background、border-radius，对比表的
// border-spacing 列间距）——TipTap 的表格节点只认识 colspan/rowspan/colwidth/align，
// 内联样式一律丢弃，于是「打开即看到卡片、改一次正文就变成裸表格并落库」。
// 这里把单元格与表格的原始 style 原文整体接管，保证往返后版式不退化。
export const PreservedTableStyle = Extension.create({
  name: 'preservedTableStyle',
  addOptions() {
    return { types: ['table', 'tableCell', 'tableHeader'] }
  },
  addGlobalAttributes() {
    return [{
      types: this.options.types,
      attributes: {
        preservedStyle: {
          default: null,
          parseHTML: element => element.getAttribute('style') || null,
          renderHTML: attributes => attributes.preservedStyle
            ? { style: attributes.preservedStyle }
            : {},
        },
      },
    }]
  },
})

// MARKFLOW 渲染产物最外层带 data-render-id="rN"（ArticleAiService.markRenderId 注入），
// AI 侧靠它把「渲染区段」与普通正文区分开，从而拒绝 HTML 回灌（read_blocks / read_article）。
// 该属性只被 ArticleService.clean() 的 safelist 放行，编辑器侧此前没有任何节点声明它，
// 于是 TipTap parse → serialize 一次就把它丢掉：用户在编辑器里保存一次，
// 渲染标记即消失、AI 回灌防护随之失效（不会报错，只有真实「渲染 + 手动保存 + AI 读回」组合才暴露）。
// 渲染产物的根元素可能是 section/div/p/h2/table 等任意一种（markRenderId 只保证有一个最外层），
// 因此对所有可能承载它的节点统一声明，而不是只补 section。
export const PreservedRenderId = Extension.create({
  name: 'preservedRenderId',
  addOptions() {
    return {
      types: [
        'paragraph', 'heading', 'blockquote', 'horizontalRule', 'image',
        'styledSection', 'styledDiv', 'styledInlineDiv', 'figure', 'figureCaption',
        'table', 'tableRow', 'tableCell', 'tableHeader',
        'bulletList', 'orderedList', 'listItem', 'codeBlock',
      ],
    }
  },
  addGlobalAttributes() {
    return [{
      types: this.options.types,
      attributes: {
        renderId: {
          default: null,
          parseHTML: element => element.getAttribute('data-render-id') || null,
          renderHTML: attributes => attributes.renderId
            ? { 'data-render-id': attributes.renderId }
            : {},
        },
      },
    }]
  },
})

export const ParagraphStyle = Extension.create({
  name: 'paragraphStyle',
  /**
   * 必须显式给出 types，否则这个扩展**一个属性都注册不上**：
   * TipTap 只把数组 / "*" / "nodes" / "marks" 认作类型列表，其余值（含 undefined）一律解析成空列表。
   * 后果是双重的——`PreservedInlineStyle` 认为这些声明「由专用扩展接管」而把它们从 inlineStyle 里过滤掉，
   * 专用扩展却又没接管，于是 margin / 首行缩进 / 行距 / 字间距在**每次正文有改动的编辑器往返**中被静默丢弃
   * （只改标题时会被 ArticleService.reconcileRenderedLayout 还原，所以不看正文的场景测不出来）；
   * 工具栏的段前/段后/首行/左缩/右缩/行距/字距七个下拉也因此永远读不到值、选中即抛异常。
   * 类型集合取 styledBlockTypes（= PreservedInlineStyle 过滤的那一批），保证「过滤掉的必有扩展接手」这条不变量。
   */
  addOptions() {
    return { types: styledBlockTypes }
  },
  addGlobalAttributes() {
    const attributes = Object.fromEntries(Object.entries(paragraphStyleProperties).map(([name, property]) => [
      name,
      {
        default: null,
        parseHTML: element => element.style.getPropertyValue(property) || null,
        renderHTML: values => values[name] ? { style: `${property}: ${values[name]}` } : {},
      },
    ]))
    return [{ types: this.options.types, attributes }]
  },
  addCommands() {
    return {
      setParagraphStyle: attributes => ({ commands }) => {
        const results = this.options.types.map(type => commands.updateAttributes(type, attributes))
        return results.some(Boolean)
      },
      unsetParagraphStyle: attributeNames => ({ commands }) => {
        const names = Array.isArray(attributeNames) ? attributeNames : [attributeNames]
        const results = this.options.types.map(type => commands.resetAttributes(type, names))
        return results.some(Boolean)
      },
    }
  },
})

export const StyledSection = Node.create({
  name: 'styledSection',
  group: 'block',
  content: 'block*',
  parseHTML: () => [{ tag: 'section' }],
  renderHTML: ({ HTMLAttributes }) => ['section', mergeAttributes(HTMLAttributes), 0],
})

export const StyledDiv = Node.create({
  name: 'styledDiv',
  group: 'block',
  content: 'block*',
  parseHTML: () => [{ tag: 'div' }],
  renderHTML: ({ HTMLAttributes }) => ['div', mergeAttributes(HTMLAttributes), 0],
})

const blockTags = new Set([
  'ADDRESS', 'ARTICLE', 'ASIDE', 'BLOCKQUOTE', 'DIV', 'FIGURE', 'FOOTER',
  'H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'HEADER', 'HR', 'OL', 'P',
  'PRE', 'SECTION', 'TABLE', 'UL',
])

export const StyledInlineDiv = Node.create({
  name: 'styledInlineDiv',
  priority: 101,
  group: 'block',
  content: 'inline*',
  parseHTML: () => [{
    tag: 'div',
    getAttrs: element => Array.from(element.children).some(child => blockTags.has(child.tagName))
      ? false
      : null,
  }],
  renderHTML: ({ HTMLAttributes }) => ['div', mergeAttributes(HTMLAttributes), 0],
})

export const FigureCaption = Node.create({
  name: 'figureCaption',
  content: 'inline*',
  parseHTML: () => [{ tag: 'figcaption' }],
  renderHTML: ({ HTMLAttributes }) => ['figcaption', mergeAttributes(HTMLAttributes), 0],
})

export const Figure = Node.create({
  name: 'figure',
  group: 'block',
  content: 'image figureCaption?',
  isolating: true,
  parseHTML: () => [{ tag: 'figure' }],
  renderHTML: ({ HTMLAttributes }) => ['figure', mergeAttributes(HTMLAttributes), 0],
})
