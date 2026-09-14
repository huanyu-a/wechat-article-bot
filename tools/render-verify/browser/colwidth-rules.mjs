/**
 * 「一列声明了多宽」的**唯一一份**解析规则。
 *
 * 为什么单独一个文件：第二十六轮的列宽闸（U10）与第二十八轮的往返闸都要判这件事，
 * 两份各写一遍解析规则就等于**两把尺子**——闸与闸之间对不上时，没人说得清是哪把尺子偏了。
 *
 * 规则（第二十六轮先立后测时定死，未改过）：
 *   - 入口 `<col style="width:90px">` / `<col width="90">` / `<td data-colwidth="90">` 三种写法都算声明；
 *   - `min-width` **不算**——那正是塌陷之后的形态；
 *   - 顺序：`style` 里的 `width`（出口形态）→ `width` 属性 → `data-colwidth` 属性（入口形态）；
 *   - 正则要求 `width` 前面是行首或分号，就是为了不把 `min-width` 认成 `width`。
 */

/** `<colgroup>` 里的每一个 `<col …>` 标签原文。没有 `<colgroup>` 时返回 null（区别于「空表头」）。 */
export function colsOf(html) {
  const group = /<colgroup[\s\S]*?<\/colgroup>/i.exec(html)
  if (!group) return null
  return [...group[0].matchAll(/<col\b[^>]*>/gi)].map((hit) => hit[0])
}

/** 一条 `<col …>` / `<td …>` 标签声明了多宽；没声明返回 null。 */
export function declaredWidth(tag) {
  const style = /style="([^"]*)"/i.exec(tag)
  const fromStyle = style && /(?:^|;)\s*width\s*:\s*([\d.]+)px/i.exec(style[1])
  if (fromStyle) return Number(fromStyle[1])
  const attr = /\swidth="([\d.]+)"/i.exec(tag)
  if (attr) return Number(attr[1])
  const data = /\sdata-colwidth="([\d.]+)"/i.exec(tag)
  if (data) return Number(data[1])
  return null
}

/** 整段 HTML 的逐列宽度声明；没有 `<colgroup>` 返回 null。 */
export function widthsOf(html) {
  const cols = colsOf(html)
  return cols === null ? null : cols.map(declaredWidth)
}
