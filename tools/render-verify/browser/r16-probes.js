/**
 * 第十六轮 · 用户在编辑器里逐条标注的 11 条 —— **每一条到底在抱怨什么**的计算样式探针。
 *
 * 为什么单独一个文件：同一组探针现在有**两个**消费者，必须用同一把尺子，否则两组数字不能放在一张表里比：
 *   1. `probe_r16.js` —— 自建探针页（左右两栏：渲染服务产物 / 构建版编辑器）
 *   2. `r16-live-editor.mjs` —— **真实运行的应用**（`http://127.0.0.1:8081` 的编辑器页面）
 * 第二十二轮的验收要回答「探针页与真实界面表现是否一致」，前提就是两边跑的是同一组定义。
 * 这次搬迁是**纯搬家**，一个字符没改。
 *
 * 之所以不能只靠 `editor-setup.js` 的通用指标：这 11 条里有 8 条抱怨的是
 * **边框 / 圆角 / 背景 / 行高 / 列表符号**——这些在字符数、元素数、SVG 数量里几乎全是 0 差异，
 * 只有 `getComputedStyle` 量得出来。
 *
 * 三条通用量法（避免「选择器写死一层，编辑器多包一层就量不到」这种假阴性——
 * 第七轮和第十六轮的清单符号都踩过这个）：
 *   - `selector` 是**后代**匹配（`querySelectorAll`），不写 `:scope >` 这种层级断言；
 *   - 每一项都回带 `parent`（父元素标签 + 父元素的行内样式），于是「元素还在、但被挪进了一层 <p>」
 *     是看得见的，而不是变成 count=0；
 *   - 需要「这一行的孩子分别是什么」时用 `children: true`，直接列出直接子元素。
 */

export const CONTAINER_PROPS = ['display', 'width', 'height', 'flexShrink', 'marginTop', 'marginBottom',
  'borderRadius', 'backgroundColor', 'borderTopWidth', 'borderLeftWidth', 'textAlign']

export const short = (value, limit = 70) => {
  const text = String(value ?? '')
  return text.length > limit ? text.slice(0, limit) : text
}

export const describe = (element, props, boxes) => {
  const style = getComputedStyle(element)
  const parent = element.parentElement
  const row = {
    tag: element.tagName.toLowerCase(),
    parent: parent ? `${parent.tagName.toLowerCase()}${parent.className ? '.' + String(parent.className).split(' ')[0] : ''}` : null,
    parentInline: parent ? short(parent.getAttribute('style') || '', 90) : null,
  }
  if (boxes) {
    const rect = element.getBoundingClientRect()
    row.box = [Math.round(rect.width), Math.round(rect.height)]
  }
  for (const property of props) {
    row[property] = short(style[property])
  }
  if (element.getAttribute('style')) row.inline = short(element.getAttribute('style'), 150)
  return row
}

/** 一条探针在一个根节点上的求值。返回可 JSON 化的对象。 */
export const runProbe = (root, probe) => {
  if (probe.kind === 'count') {
    return { label: probe.label, kind: 'count', count: root.querySelectorAll(probe.selector).length }
  }
  if (probe.kind === 'img') {
    return {
      label: probe.label, kind: 'img',
      items: [...root.querySelectorAll(probe.selector)].map((image) => {
        const rect = image.getBoundingClientRect()
        const style = getComputedStyle(image)
        const wrapper = image.closest('section')
        return {
          src: short(image.getAttribute('src') || '', 60),
          complete: image.complete,
          natural: [image.naturalWidth, image.naturalHeight],
          box: [Math.round(rect.width), Math.round(rect.height)],
          width: style.width, height: style.height, objectFit: style.objectFit,
          borderRadius: style.borderRadius, display: style.display,
          wrapperBox: wrapper ? [Math.round(wrapper.getBoundingClientRect().width), Math.round(wrapper.getBoundingClientRect().height)] : null,
          parent: image.parentElement ? image.parentElement.tagName.toLowerCase() : null,
        }
      }),
    }
  }
  if (probe.kind === 'text') {
    const element = root.querySelector(probe.selector)
    return { label: probe.label, kind: 'text', text: element ? short(element.textContent.replace(/\s+/g, ' '), 80) : null }
  }

  const elements = [...root.querySelectorAll(probe.selector)]
  const picked = probe.all === false ? elements.slice(0, 1) : elements

  if (probe.children) {
    return {
      label: probe.label, kind: 'children', count: elements.length,
      items: picked.map((element) => ({
        row: describe(element, ['display', 'gap', 'marginBottom'], false),
        children: [...element.children].map((child) => ({
          tag: child.tagName.toLowerCase(),
          text: short(child.textContent.replace(/\s+/g, ''), 18),
          ...describe(child, CONTAINER_PROPS, true),
        })),
      })),
    }
  }

  return {
    label: probe.label, kind: 'style', count: elements.length,
    items: picked.map((element) => describe(element, probe.props, probe.boxes)),
  }
}

export const PROBES = {
  'r16-01-changelog': [
    { label: '外层容器', selector: 'section', all: false,
      props: ['borderTopWidth', 'borderRightWidth', 'borderBottomWidth', 'borderLeftWidth', 'borderTopStyle', 'borderTopColor', 'borderRadius', 'backgroundColor', 'paddingTop'] },
    { label: '版本号徽标', selector: 'span', all: false, props: ['backgroundColor', 'borderRadius', 'color', 'paddingTop'] },
    { label: '分类标题（新增/变更）', selector: 'p', all: true, props: ['color', 'fontWeight', 'fontSize', 'display'] },
    { label: '条目胶囊', selector: 'section section span', all: true,
      props: ['backgroundColor', 'borderRadius', 'borderTopWidth', 'borderTopColor', 'lineHeight', 'display'] },
    { label: '分组行构成', selector: 'section > section', children: true },
  ],
  'r16-02-subscribe': [
    { label: '外层容器', selector: 'section', all: false,
      props: ['borderTopWidth', 'borderTopColor', 'borderRadius', 'backgroundImage', 'textAlign', 'paddingTop', 'display', 'flexDirection'] },
    { label: '标题', selector: 'p', all: false, props: ['fontSize', 'textAlign', 'fontWeight'] },
    { label: '正文行', selector: 'section p', all: true, props: ['textAlign', 'marginTop', 'marginBottom'] },
    { label: '假输入框', selector: 'section section section', all: true,
      props: ['background', 'borderTopWidth', 'borderTopColor', 'borderRadius', 'textAlign', 'display', 'paddingTop', 'maxWidth'] },
    { label: '真表单元素', kind: 'count', selector: 'input, button, textarea' },
    { label: '表单行构成', selector: 'section > section', children: true },
  ],
  'r16-03-author-card': [
    { label: '外层容器', selector: 'section', all: false,
      props: ['display', 'paddingTop', 'borderTopWidth', 'borderTopColor', 'borderRadius', 'backgroundColor', 'gap', 'alignItems'] },
    { label: '头像外框', selector: 'section section', all: false,
      props: ['width', 'height', 'borderRadius', 'overflow', 'flexShrink', 'borderTopWidth', 'borderTopColor', 'display'] },
    { label: '头像图', kind: 'img', selector: 'img' },
    { label: '标签胶囊', selector: 'section section span', all: true,
      props: ['borderRadius', 'borderTopWidth', 'backgroundColor', 'display', 'paddingTop'] },
    { label: '卡片构成', selector: 'section > section', children: true },
  ],
  'r16-04-quote-card': [
    { label: '卡片本体', selector: 'section', all: false,
      props: ['backgroundImage', 'backgroundColor', 'borderRadius', 'paddingTop', 'paddingLeft', 'borderTopWidth', 'position', 'overflow', 'display'] },
    { label: '引号装饰', selector: 'section span', all: false,
      props: ['fontSize', 'color', 'position', 'top', 'left', 'width', 'height', 'display', 'backgroundImage'] },
    { label: '金句正文', selector: 'p', all: true,
      props: ['fontStyle', 'textAlign', 'lineHeight', 'fontSize', 'marginTop', 'paddingLeft'] },
    { label: '卡片构成', selector: 'section > section', children: true },
  ],
  'r16-05-audience-fit': [
    { label: '外层容器', selector: 'section', all: false, props: ['display', 'flexDirection', 'gap', 'marginBottom'] },
    { label: '每一行卡片', selector: 'section > section', all: true,
      props: ['display', 'paddingTop', 'paddingLeft', 'backgroundColor', 'borderTopWidth', 'borderTopColor', 'borderRadius', 'gap', 'alignItems'] },
    { label: '评级徽标', selector: 'span', all: true,
      props: ['width', 'height', 'display', 'borderRadius', 'backgroundColor', 'borderTopWidth', 'borderTopColor', 'color', 'fontSize'] },
    { label: '行构成', selector: 'section > section', children: true },
  ],
  'r16-06-title-da01': [
    { label: '外层容器', selector: 'section', all: false,
      props: ['boxShadow', 'borderTopWidth', 'borderTopColor', 'borderRadius', 'backgroundImage', 'overflow', 'marginBottom', 'borderLeftWidth'] },
    { label: '内层白底', selector: 'section section', all: false, props: ['paddingTop', 'backgroundColor', 'borderRadius'] },
    { label: '表格', selector: 'table', props: ['borderCollapse', 'tableLayout', 'width', 'minWidth', 'borderTopWidth'] },
    { label: '列定义', kind: 'count', selector: 'colgroup > col' },
    { label: '单元格', selector: 'td', all: true, props: ['textAlign', 'verticalAlign', 'paddingTop', 'paddingLeft', 'width', 'borderTopWidth', 'display'] },
    { label: '阅读时长徽标', selector: 'td section', all: false,
      props: ['display', 'width', 'height', 'lineHeight', 'borderRadius', 'backgroundColor', 'boxShadow', 'textAlign'] },
    { label: '预计阅读数字', selector: 'td section span', all: false, props: ['fontSize', 'lineHeight', 'color', 'fontWeight'] },
    { label: '字数文案', kind: 'text', selector: 'td p:last-of-type' },
    { label: 'chips 容器', selector: 'td section:last-of-type', all: false, props: ['fontSize', 'lineHeight', 'paddingTop', 'display'] },
    { label: 'chips 项', selector: 'td section span span', all: true, props: ['display', 'fontSize', 'color', 'whiteSpace', 'marginRight'] },
    { label: '表格行构成', selector: 'tr', children: true },
  ],
  'r16-07-summary': [
    { label: '外层容器', selector: 'section', all: false,
      props: ['backgroundColor', 'borderTopWidth', 'borderTopColor', 'borderRadius', 'paddingTop'] },
    { label: '每一行', selector: 'section > section', all: true, props: ['display', 'gap', 'marginBottom', 'alignItems'] },
    { label: '行构成', selector: 'section > section', children: true },
    { label: '行首符号', selector: 'span', all: true,
      props: ['display', 'width', 'height', 'borderRadius', 'backgroundColor', 'flexShrink', 'marginTop', 'borderTopWidth', 'visibility'], boxes: true },
    { label: '行文字', selector: 'p', all: true, props: ['display', 'fontSize', 'lineHeight', 'marginTop', 'marginBottom'] },
    { label: '原生列表元素', kind: 'count', selector: 'ul, ol, li' },
    { label: '编辑器插入的分隔符', kind: 'count', selector: 'img.ProseMirror-separator, br' },
  ],
  'r16-08-checklist': [
    { label: '外层容器', selector: 'section', all: false,
      props: ['backgroundColor', 'borderTopWidth', 'borderTopColor', 'borderRadius', 'paddingTop'] },
    { label: '每一行', selector: 'section > section', all: true,
      props: ['display', 'paddingTop', 'paddingBottom', 'borderBottomWidth', 'borderBottomColor', 'borderLeftWidth', 'borderLeftColor', 'borderLeftStyle', 'gap', 'alignItems'] },
    { label: '行构成', selector: 'section > section', children: true },
    { label: '行首方框', selector: 'section > section span', all: true,
      props: ['display', 'width', 'height', 'flexShrink', 'borderRadius', 'backgroundColor',
        'borderTopWidth', 'borderTopColor', 'borderLeftWidth', 'borderLeftColor', 'borderLeftStyle', 'fontSize', 'lineHeight'],
      boxes: true },
    { label: '行文字', selector: 'section > section p', all: true,
      props: ['textDecorationLine', 'color', 'lineHeight', 'marginBottom', 'display'] },
    { label: '编辑器插入的分隔符', kind: 'count', selector: 'img.ProseMirror-separator, br' },
  ],
  'r16-09-table-card': [
    { label: '外层容器', selector: 'section', all: false,
      props: ['borderRadius', 'backgroundColor', 'boxShadow', 'overflow', 'overflowX', 'marginBottom', 'paddingTop', 'display'] },
    { label: '表格', selector: 'table', props: ['borderCollapse', 'width', 'borderSpacing', 'display', 'tableLayout'] },
    { label: '表头格', selector: 'th', all: true,
      props: ['paddingTop', 'paddingBottom', 'paddingLeft', 'lineHeight', 'fontSize', 'backgroundColor', 'borderRadius', 'display'] },
    { label: '数据格', selector: 'td', all: true,
      props: ['paddingTop', 'paddingBottom', 'paddingLeft', 'lineHeight', 'fontSize', 'borderBottomWidth', 'borderBottomColor', 'backgroundColor', 'display'] },
    { label: '每行高度', selector: 'tr', all: true, boxes: true, props: ['display', 'height'] },
    { label: '表头行构成', selector: 'tr', children: true },
    { label: '表格标题元素', kind: 'count', selector: 'section section > p' },
    { label: '编辑器插入的分隔符', kind: 'count', selector: 'img.ProseMirror-separator, br' },
  ],
  'r16-10-infographic': [
    { label: '外层容器', selector: 'section', all: false,
      props: ['backgroundImage', 'borderRadius', 'borderTopWidth', 'borderTopColor', 'paddingTop', 'textAlign', 'display'] },
    { label: 'label/title/subtitle', selector: 'section > p', all: true,
      props: ['fontSize', 'fontWeight', 'color', 'textAlign', 'lineHeight', 'letterSpacing', 'display'] },
    { label: 'body 三条', selector: 'section > section > section', all: true,
      props: ['display', 'gap', 'alignItems', 'marginTop'] },
    { label: 'body 行构成', selector: 'section > section > section', children: true },
    { label: 'body 圆点', selector: 'span', all: true,
      props: ['display', 'width', 'height', 'borderRadius', 'backgroundColor', 'flexShrink', 'marginTop'], boxes: true },
    { label: 'body 文字', selector: 'section > section p, section > section > section p', all: true, props: ['fontSize', 'lineHeight', 'display'] },
  ],
  'r16-11-steps-horizontal': [
    { label: '外层容器', selector: 'section', all: false,
      props: ['backgroundColor', 'borderRadius', 'borderTopWidth', 'borderTopColor', 'paddingTop', 'display'] },
    { label: '标题三段', selector: 'section > p', all: true,
      props: ['fontSize', 'fontWeight', 'color', 'letterSpacing', 'marginBottom', 'display'] },
    { label: '滚动容器', selector: 'section > section', all: false, props: ['overflowX', 'minWidth', 'display'] },
    { label: '表格', selector: 'table', props: ['borderCollapse', 'borderSpacing', 'minWidth', 'borderTopWidth', 'display'] },
    { label: '每一步', selector: 'td', all: true,
      props: ['borderTopWidth', 'borderTopColor', 'borderTopStyle', 'borderRadius', 'paddingTop', 'paddingLeft', 'backgroundColor', 'textAlign', 'width', 'verticalAlign', 'display'] },
    { label: '步内文字', selector: 'td p', all: true, props: ['fontSize', 'fontWeight', 'lineHeight', 'marginBottom', 'display'] },
    { label: '步内数字/标题', selector: 'td p span', all: true, props: ['fontSize', 'fontWeight', 'color', 'display'] },
    { label: '步骤表结构', selector: 'table', children: true },
  ],
}
