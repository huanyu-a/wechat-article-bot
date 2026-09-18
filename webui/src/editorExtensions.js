import { Extension, Mark, Node, mergeAttributes } from '@tiptap/core'
import TipTapBold from '@tiptap/extension-bold'
import TipTapCodeBlock from '@tiptap/extension-code-block'
import { TableView } from '@tiptap/extension-table'
import { TextStyle as TipTapTextStyle } from '@tiptap/extension-text-style'
import { Plugin } from '@tiptap/pm/state'

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

/**
 * 打开正文时，把**块首**那一小段半角空格 / 制表符换成不会塌缩的等价字符。
 *
 * 为什么要做这件事：`setContent` 默认的解析会把文本里的连续空白塌缩掉，于是「段首手工缩进」
 * 在**第一次解析**（也就是用户打开文章的那一刻）就没了——保存出口里也没有，重新打开更看不到。
 * ProseMirror 的 `preserveWhitespace` 只有 `true` 与 `'full'` 两个取值，没有「只保留段首」这一档
 * （`prosemirror-model` 的 `wsOptionsFor(type, preserveWhitespace, base)` 只认布尔与 `'full'`），
 * 而 `'full'` 会连标签之间、标签内部的换行与缩进一起留下——那是动整篇排版，不是修一个缩进。
 * 所以这里做**定点替换**：只把块首那一小段换成等宽的不换行空格，其余一个字节都不碰。
 *
 * 三条边界（都是为了「不改没坏的东西」）：
 *   ① 只替换**块首**：`<p>文字 <span>还有字</span></p>` 里行内的空格不受影响；
 *   ② 纯空白的文本节点不碰：`<section>\r\n  <p>…</p></section>` 里那段换行 + 缩进要是被换成
 *      不换行空格，反而会凭空多出一段**可见**空白（第二十五轮量过 `preserveWhitespace:'full'`
 *      在 79 套样例上的同类副作用）；
 *   ③ `pre / code / textarea / script / style / svg` 整棵子树不碰，遇到就停：它们的空白本来就该原样保留。
 *
 * 制表符按**制表位宽度**展开成等宽的 `&nbsp;`（不是压成 1 个）。取舍与实测见
 * `docs/dev/known-issues-handoff.md` §3.27①/§3.28：
 *
 *   编辑器里段首一个制表符实测 **26.89px**（`white-space: break-spaces` · `tab-size: 8`），
 *   一个 `&nbsp;` 是 **3.38px**；实测 8 个 `&nbsp;` 与 1 个制表符的**首字符落点只差 0.01px**（224.88 vs 224.89，同一行同一列量出来的），视觉宽度不退。
 *   所以 `\t` → 8 个 `&nbsp;` 之后**缩进宽度一分不变**；压成 1 个才是 26.89 → 3.38 的退化。
 *
 *   `TAB_SIZE = 8` 是浏览器默认值，本项目 CSS **没有**覆盖 `tab-size`（`grep tab-size webui/src/` 只命中本段注释）。
 *   若将来有人在 CSS 里改了 `tab-size`，这里必须跟着改，否则段首制表符会与同段其余制表符对不齐。
 *   展开用的是**列推进**而不是「一个 `\t` 换 8 个」：`"\t  "` 是 8 + 2 = **10** 列，
 *   而 `" \t"` 是 1 → 8（第 1 列上的制表符推到第 8 列）= **8** 列，两种写法必须算出不同的宽度才对得上。
 */
const whitespaceOpaqueTags = new Set(['PRE', 'CODE', 'TEXTAREA', 'SCRIPT', 'STYLE', 'SVG'])

/** 与 `.ProseMirror` 的 `tab-size` 一致（浏览器默认 8；本项目 CSS 未覆盖）。 */
const TAB_SIZE = 8

/** 把一段「半角空格 + 制表符」按制表位推进算出**列数**，再吐同样多个数的 `&nbsp;`。 */
function leadingRunToNbsp(run) {
  let column = 0
  for (const char of run) {
    if (char === '\t') column += TAB_SIZE - (column % TAB_SIZE)
    else column += 1
  }
  return '\u00a0'.repeat(column)
}

/** 一个元素里**第一个含可见字符**的文本节点；遇到不该碰的子树就停（返回 null，宁可不改）。 */
function firstVisibleTextNode(element) {
  for (const child of element.childNodes) {
    if (child.nodeType === 3) {
      if (/\S/.test(child.nodeValue)) return child
      continue
    }
    if (child.nodeType !== 1) continue
    if (whitespaceOpaqueTags.has(child.tagName)) return null
    const hit = firstVisibleTextNode(child)
    if (hit) return hit
  }
  return null
}

export function preserveLeadingWhitespace(html) {
  if (typeof html !== 'string' || !/[ \t]/.test(html)) return html
  const holder = document.createElement('template')
  holder.innerHTML = html
  let changed = false
  for (const element of holder.content.querySelectorAll('*')) {
    if (whitespaceOpaqueTags.has(element.tagName)) continue
    const node = firstVisibleTextNode(element)
    if (!node) continue
    // 替换完之后首字符是不换行空格，再进这个分支也不会命中——所以多层嵌套重复走是幂等的。
    const replaced = node.nodeValue.replace(/^[ \t]+/g, leadingRunToNbsp)
    if (replaced === node.nodeValue) continue
    node.nodeValue = replaced
    changed = true
  }
  return changed ? holder.innerHTML : html
}

/**
 * 把同一个 `preserveLeadingWhitespace()` 挂到**粘贴**这条路上（用户原话：「粘贴 HTML 时段首空白丢失要修」）。
 *
 * 为什么打开那条路挂了、粘贴这条路没挂：`setContent` 走的是 `DOMParser.fromSchema(...).parse(...)`，
 * 由调用方决定要不要先处理；而粘贴走的是 `prosemirror-view/dist/index.js` 的 `parseFromClipboard()`，
 * 它在 HTML 分支里写死 `preserveWhitespace: !!(asText || sliceData)` —— 从网页复制过来的 HTML
 * `asText` 为假、没有 `data-pm-slice` 时 `sliceData` 也是空，于是 `preserveWhitespace: false`，
 * 段首那段 `[ \t]` 在**解析那一刻**就被并掉（第二十七轮把三条入口量成一张表时定位到的）。
 *
 * `transformPastedHTML` 正是这条路唯一的官方挂点：它在 `html` 进 `readHTML()` **之前**调用
 * （`prosemirror-view/dist/index.js:2864` `view.someProp("transformPastedHTML", f => { html = f(html, view); })`），
 * 拿到的是剪贴板原文、返回的也是字符串；**拖动（drop）走同一条路**（同文件 :3842），所以一并覆盖。
 *
 * 为什么复用同一个函数而不是另写一份：两条路必须同一把尺子——否则「打开时看到缩进、粘进来却没有」
 * 这类不一致会换个形式再回来。跳过名单（`pre/code/textarea/script/style/svg` 整棵子树）与幂等性
 * 也一并继承（见 `preserveLeadingWhitespace()` 的三条边界）。
 */
export const PastedLeadingWhitespace = Extension.create({
  name: 'pastedLeadingWhitespace',
  transformPastedHTML(html) {
    return preserveLeadingWhitespace(html)
  },
})

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
 * D48 修复（第 1 半）：关掉 TipTap v3 TextStyle 自带的「嵌套 span 样式合并」。
 *
 * 上游默认 `mergeNestedSpanStyles: true`：parse 时把父 span 的 style **文本拼接**进每个子
 * span（`父样式;子样式`，见 @tiptap/extension-text-style 的 `mergeNestedSpanStyles`）。
 * 实测（r48 探针，`tools/render-verify/browser/r48-flex-span-probe.js`）：
 * `<span style="flex:1">a<span style="color:#e74c3c">b</span>c</span>` 解析后 b 的
 * textStyle mark = `{preservedStyle:"flex:1", color:"#e74c3c", sourceSpanId:<新号>}`——
 * 与 a/c 的 `{preservedStyle:"flex:1"}` **属性不等**。
 *
 * 而 ProseMirror 的 DOMSerializer（`prosemirror-model` to_dom.js `serializeFragment`）只保持
 * 「打开栈」里与 node.marks **按序相等的前缀**：属性一变，整个打开栈（含外层 span）关闭重开。
 * 于是同一源 span 被拆成多个兄弟 span，**每个都带一份 flex:1**——在 `display:flex` 容器里
 * 就是「圆点 + 一栏正文」变成 N 栏（known-issues-handoff.md D48，真实应用实测 [2,2]/[3]）。
 *
 * 关掉合并后，嵌套 span 不再被拼进父样式；它自己的样式由下方的
 * {@link MarkflowInnerSpanStyle} 以**可嵌套的独立标记**承载，外层身份保持唯一一份。
 */
export const MarkflowTextStyle = TipTapTextStyle.configure({ mergeNestedSpanStyles: false })

/**
 * 元素是否嵌套在某个带 style 的 SPAN 里。中间隔着**无样式**的 span 不影响判定：
 * 无样式 span 本来就不产生 textStyle 身份（textStyle 的规则要求有 style 属性）。
 * 渲染产物的结构语义是「嵌套在样式 span 里的 span 是内联格式，不是新的独立盒子」——
 * flex 项（圆点、徽章胶囊）在产物里是**兄弟** span，不是嵌套。
 */
function nestedInStyledSpan(element) {
  let node = element.parentElement
  while (node) {
    if (node.tagName === 'SPAN' && node.hasAttribute('style')) return true
    node = node.parentElement
  }
  return false
}

/**
 * D48 修复（第 2 半）：嵌套在带样式 span 里的内层样式 span。
 *
 * 职责：把内层 span **自己的** style 原文原样进出，渲染成一个**嵌套**在外层 span 里的
 * `<span style="…">`。它与 textStyle 是不同类型，可以嵌套——序列化时外层 span 保持打开，
 * 内层只开一个 span，产物形态与渲染服务一致：
 * `<span style="flex:1"><strong>时间</strong>：每天 <span style="color:#e74c3c">09:00</span> 晨练</span>`。
 *
 * 两条优先级的配合（缺一不可）：
 *   - **规则** priority 104：高于 textStyle 的默认 50 —— 嵌套 span 先被本规则**认领并消费**
 *     （prosemirror-model `matchTag` 按规则优先级试、默认 consuming 命中即止），
 *     textStyle 的 span 规则不再命中它，不再产生新身份；
 *   - **扩展** priority 保持默认 100（< textStyle 的 101）：schema rank 排在 textStyle 之后，
 *     序列化时 textStyle 先开、本标记开在其**内层**。
 *   （PreservedEmptySpan 的规则 105 在本规则之前：空 span 仍归列表圆点节点管；
 *   RawMath 的 110/120 更高：公式子树整体归原子节点管。）
 *
 * sourceSpanId 仍按元素派**新号**：相邻同款内层 span（两个相同颜色的片段）不合并——
 * 与 D35「相邻同款 span 被合并」同一条守则。该属性只在编辑器内部存在，不进 getHTML()。
 */
export const MarkflowInnerSpanStyle = Mark.create({
  name: 'markflowInnerSpanStyle',
  addAttributes() {
    return {
      innerStyle: {
        default: null,
        // 内层样式要**原样**往返，不按 managedMarkStyleProperties 过滤：
        // 颜色/字号这类声明在外层 span 上由专用扩展接管，但内层 span 除了本标记没有任何
        // 渲染出口，过滤掉就是纯丢。
        parseHTML: element => element.getAttribute('style'),
        renderHTML: attributes => attributes.innerStyle ? { style: attributes.innerStyle } : {},
      },
      sourceSpanId: {
        default: null,
        parseHTML: nextPreservedSpanId,
        renderHTML: () => ({}),
      },
    }
  },
  parseHTML() {
    return [{
      tag: 'span[style]',
      priority: 104,
      getAttrs: element => (nestedInStyledSpan(element) ? null : false),
    }]
  },
  renderHTML({ HTMLAttributes }) {
    return ['span', HTMLAttributes, 0]
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
    return [
      {
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
      },
      {
        types: ['tableCell', 'tableHeader'],
        attributes: {
          // 渲染服务给窄列写的是 `data-colwidth="90"`（标题卡右下那个 64×64 阅读时长格），
          // 而 TipTap 的 `TableCell.colwidth.parseHTML` 只认两种来源：`colwidth` 属性（它
          // 自己 renderHTML 输出的那个名字）和 `<colgroup><col width>`。`data-colwidth`
          // 落在这两者之外，于是加载时被丢掉 —— 实时 DOM 的 colgroup 退化成
          // `min-width: 25px`（cellMinWidth），产物里的 637px / 90px 被平分成 344.5 / 344.5。
          // 属性名**必须**叫 `colwidth`：`updateColumns()` 是按这个名字从 `node.attrs` 里取的；
          // 本扩展注册在 TableKit 之后，同名属性后者胜，故这里能覆盖 TableCell 的解析。
          // 不写 renderHTML，序列化行为与 TipTap 原实现一致（输出 `colwidth="90"`）。
          colwidth: {
            default: null,
            parseHTML: element => {
              const raw = element.getAttribute('data-colwidth')
              if (!raw) return null
              const widths = raw.split(',').map(value => parseInt(value, 10)).filter(Number.isFinite)
              return widths.length ? widths : null
            },
          },
        },
      },
    ]
  },
})

// `PreservedTableStyle` 只保证了**序列化**不丢：`Table.renderHTML` 见到 HTMLAttributes.style
// 就直接把它当表格样式输出，所以 getHTML() 里的 `<table>` 是对的。
// 但编辑器**实时 DOM** 不是 renderHTML 造的——`resizable: true` 时 Table 的 addNodeView()
// 返回 null、改由 columnResizing 插件构造 NodeView，而它读的是 `node.attrs.style`
// （本项目的样式存在 `preservedStyle` 里，`style` 恒为空），于是走 else 分支自己写一句
// `min-width: 125px`，渲染器的 `border-collapse:separate` / `border-spacing:12px 0` /
// `min-width:600px` 全部落空。后果是可见的：`.ProseMirror table{border-collapse:collapse}`
// 生效后单元格的 `border-radius` 不再画圆角——用户原话「每一步的边框没有加圆角」；
// 列间距也从 12px 掉到默认的 2px，五张步骤卡挤在一起。
// 把 View 换成这个子类，构造与每次 update 之后把原文声明逐条贴回实时 DOM。
export class PreservedTableView extends TableView {
  constructor(node, cellMinWidth, view, HTMLAttributes) {
    super(node, cellMinWidth, view, HTMLAttributes)
    applyPreservedTableStyle(this.table, node)
  }

  update(node) {
    const updated = super.update(node)
    if (updated) applyPreservedTableStyle(this.table, node)
    return updated
  }
}

// 「这张表的版式由产物说了算」的标记类。样式表里 `.ProseMirror table` 那套（`table-layout:fixed`
// / 单元格 1px 描边 / 从 `.ProseMirror` 继承的 1.95 行高）是给**手写表格**兜底的；产物表自带整份
// 设计，被那套压住就会走形——用户实测同一张对比表：编辑器 347.09px、原项目 197px，列宽还被拉成
// 四等分。类名只贴实时 DOM，**不进 `getHTML()`**：产物本身没有这个类，写进保存出口就是凭空多个属性。
const PRESERVED_TABLE_CLASS = 'mf-preserved'

/**
 * 产物表指纹。**必须比「有没有 style 属性」严**，这条是被实测逼出来的：
 * 第一版判据写成「`preservedStyle` 非空 ⇒ 打类」，注释里还写了「手写表格两样都拿不到」——
 * 用真实应用验（`target/probe/r34/table_handmade_regression.mjs`）发现是错的：工具栏插一张
 * 3×3 表，TipTap 自己的 `createColGroup` 就会写出 `<table style="min-width: 75px;">`，
 * 于是手写表格也被打上类，`.mf-preserved th,td{border:0}` 把网格线整片画没（逐格边框实测
 * `1px 1px 1px 1px` → `0px 0px 0px 0px`）。用户插一张表就掉线，这个代价太大。
 *
 * 指纹取「表格级 style 含 `border-collapse: collapse`」：渲染服务的网格表格**每张**都写它
 * （全量扫描 14/14 个产物、14/14 张表，见 `target/probe/r34/table_fingerprint_scan.txt`），
 * 而手写表格的出口那串 `min-width: 75px;` 里没有。
 *
 * 为什么**不**要求 `separate` 也算：`border-collapse:separate` 是卡片布局（步骤卡/标题卡/
 * 侧栏卡）的写法，它们靠单元格自己的 `border` + `border-radius` 画卡片边，行的内联 `border`
 * 本来就压得住兜底那套——放它们进来只是徒增风险面。**判 false 的方向是安全的**：那正是改前的
 * 行为，一格不差。
 */
const PRODUCT_TABLE_STYLE = /border-collapse\s*:\s*collapse\b/i

/** 把 `<table style>` 的原文声明贴到实时 DOM 上；渲染器没写的属性仍由 TipTap 的列宽逻辑决定。 */
function applyPreservedTableStyle(table, node) {
  const source = node && node.attrs ? node.attrs.preservedStyle : null
  if (!table) return
  // 类名与行内样式**不同判**：行内声明是「有几个字就贴几个字」，产物与手写表都得贴（贴错了也
  // 只是把 TipTap 自己写的那句原样写回，无副作用）；类名才决定兜底那套让不让路，必须认准。
  table.classList.toggle(PRESERVED_TABLE_CLASS, PRODUCT_TABLE_STYLE.test(String(source || '')))
  if (!source) return
  for (const declaration of String(source).split(';')) {
    const at = declaration.indexOf(':')
    if (at < 0) continue
    table.style.setProperty(declaration.slice(0, at).trim(), declaration.slice(at + 1).trim())
  }
}

// 父容器的行内样式原文（`inlineStyle` 里不含 margin，margin 由 ParagraphStyle 分开存）。
const parentStyleText = parent => {
  if (!parent) return ''
  const chunks = [parent.attrs.inlineStyle || '']
  for (const [name, property] of Object.entries(paragraphStyleProperties)) {
    if (parent.attrs[name]) chunks.push(`${property}: ${parent.attrs[name]}`)
  }
  return chunks.filter(Boolean).join('; ')
}

const readDeclarations = text => {
  const declarations = new Map()
  for (const chunk of String(text || '').split(';')) {
    const at = chunk.indexOf(':')
    if (at < 0) continue
    declarations.set(chunk.slice(0, at).trim().toLowerCase(), chunk.slice(at + 1).trim())
  }
  return declarations
}

const FLEX_CONTEXT_PROPERTIES = [
  'display', 'flex-direction', 'flex-wrap', 'justify-content', 'align-items', 'align-content',
  'gap', 'row-gap', 'column-gap', 'grid-template-columns', 'grid-template-rows', 'grid-auto-flow',
]
const FLEX_CONTAINER = /^(inline-)?(flex|grid)$/
const TABLE_CELL_TYPES = new Set(['tableCell', 'tableHeader'])
const SYNTHETIC_STYLE_ATTRIBUTES = ['inlineStyle', ...Object.keys(paragraphStyleProperties)]

/** 这个段落是不是 ProseMirror 自己造的（不是渲染产物里那一份）。 */
const isSyntheticParagraph = node => node.type.name === 'paragraph'
  && Boolean(node.type.attrs && node.type.attrs.inlineStyle)
  && node.content.size > 0
  && SYNTHETIC_STYLE_ATTRIBUTES.every(name => node.attrs[name] == null)

/**
 * 合成段落里的行内内容是不是**整块都脱离文档流**。
 *
 * 渲染产物允许把纯装饰性的绝对定位元素直接挂在块容器下——`:::quote-card` 的大引号就是
 * `<section style="…;position:relative;overflow:hidden">` 下的
 * `<span style="position:absolute;top:8px;left:16px;font-size:72px">"</span>`。
 * 这种元素在产品里不占行、不贡献高度；被合成段落包起来之后它仍然不占位，
 * 可编辑器会给这个**凭空的段落**补一句 `margin: 0 0 1.15em`，卡片于是高出 17px。
 */
const OUT_OF_FLOW = /position\s*:\s*(?:absolute|fixed)\b/i

const inlineIsOutOfFlow = node => {
  if (node.isText) {
    return node.marks.some(mark => OUT_OF_FLOW.test(String((mark.attrs || {}).preservedStyle || '')))
  }
  const attrs = node.attrs || {}
  return OUT_OF_FLOW.test(String(attrs.preservedStyle || attrs.inlineStyle || ''))
}

const isOutOfFlowOnly = node => {
  let total = 0
  let inFlow = 0
  node.forEach(child => {
    total += 1
    if (!inlineIsOutOfFlow(child)) inFlow += 1
  })
  return total > 0 && inFlow === 0
}

/** 合成段落该顶替的样式；null = 不动它。 */
const syntheticParagraphStyle = (node, parent) => {
  if (!parent) return null
  // 渲染产物的 <td> 里根本没有段落，合成出来的这个不该贡献任何行间距。
  if (TABLE_CELL_TYPES.has(parent.type.name)) return 'margin: 0'
  const declarations = readDeclarations(parentStyleText(parent))
  if (FLEX_CONTAINER.test(declarations.get('display') || '')) {
    const inherited = FLEX_CONTEXT_PROPERTIES
      .filter(property => declarations.has(property))
      .map(property => `${property}: ${declarations.get(property)}`)
    inherited.push('margin: 0')
    return inherited.join('; ')
  }
  // 非 flex 容器：只有这个合成段落**完全没有在流内容**时（里面只剩一个绝对定位的装饰元素），
  // 才把编辑器补的那句 `margin: 0 0 1.15em` 归零——它在产品里从不占行，段落间距纯属凭空。
  // 有在流内容时保持原样：段落间距是编辑器对普通正文的既有排版，不在这里动。
  return isOutOfFlowOnly(node) ? 'margin: 0' : null
}

/**
 * 合成的包裹段落：把父容器的 flex/grid 上下文原样复制到它身上。
 *
 * ProseMirror 的 schema 不允许块级容器里直接放行内内容，于是渲染产物里直接挂在
 * `<section style="display:flex">` 下的空 `<span>`（列表圆点、清单方框、信息图圆点）
 * 与裸文本 `<span>`（版本徽标、标签胶囊、评级徽标）在解析时一律被套进一个**它自己造出来的** `<p>`：
 *
 *   - 空 span 落进普通块盒子后退回 `display: inline`，而 inline 元素**不接受 width/height**——
 *     8×8 的圆点塌成零宽、20×20 的方框只剩左右边框叠成一条竖线
 *     （用户原话「每一项的前边缺少列表符号」「每一项的前边都多了一条竖线」正是这个）；
 *   - 胶囊虽然还画得出底色，但它不再是 flex 项，容器的 `gap` 与换行节奏一起失效，
 *     连排的同色胶囊糊成一条（用户原话「缺少边框」）；
 *   - 编辑器自己的 `.ProseMirror p{margin:0 0 1.15em}` 还会给这个凭空的段落补上 1.15em 下间距。
 *
 * 复制 flex 上下文之后，包裹段落的子项构成与渲染产物**一致**（尺寸、gap、对齐由同一组声明决定），
 * 而且这份样式跟着 `getHTML()` 一起落库——公众号侧的版式同样是对的，不只是编辑器里好看。
 */
export const SyntheticBlockStyle = Extension.create({
  name: 'syntheticBlockStyle',
  addProseMirrorPlugins() {
    return [new Plugin({
      appendTransaction: (transactions, _oldState, newState) =>
        transactions.some(transaction => transaction.docChanged) ? stampSyntheticParagraphs(newState) : null,
    })]
  },
})

function stampSyntheticParagraphs(state) {
  const pending = []
  // 必须走 `doc.nodesBetween`：同版本的 `doc.descendants` 只回调 (node, pos)，
  // 第三个参数（父节点）恒为 null，拿它判「父容器是不是 flex」会一条都命中不了。
  state.doc.nodesBetween(0, state.doc.content.size, (node, pos, parent) => {
    if (!isSyntheticParagraph(node)) return true
    const style = syntheticParagraphStyle(node, parent)
    if (style) pending.push({ pos, style })
    return true
  })
  if (!pending.length) return null
  const transaction = state.tr.setMeta('addToHistory', false)
  // 只改属性、不改文档长度，因此位置不会因为前一条改动而失效。
  for (const item of pending) transaction.setNodeAttribute(item.pos, 'inlineStyle', item.style)
  return transaction
}

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
