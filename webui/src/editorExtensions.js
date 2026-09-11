import { Extension, Node, mergeAttributes } from '@tiptap/core'

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
]

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
function readPreservedMarkStyle(element) {
  const raw = element.getAttribute?.('style')
  if (!raw) return null

  const declarations = raw.split(';')
    .map(declaration => declaration.trim())
    .filter(Boolean)
    .filter(declaration => !managedMarkStyleProperties.has(
      declaration.split(':')[0].trim().toLowerCase(),
    ))

  return declarations.length ? declarations.join('; ') : null
}

export const PreservedMarkStyle = Extension.create({
  name: 'preservedMarkStyle',
  addGlobalAttributes() {
    return [{
      types: ['textStyle'],
      attributes: {
        preservedStyle: {
          default: null,
          parseHTML: readPreservedMarkStyle,
          renderHTML: attributes => attributes.preservedStyle
            ? { style: attributes.preservedStyle }
            : {},
        },
      },
    }]
  },
})

// MARKFLOW 渲染产物把整个组件设计放在 <table>/<td>/<th> 的内联样式里
// （步骤卡的 width:33%、padding、background、border-radius，对比表的
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
        'bulletList', 'orderedList', 'listItem',
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
