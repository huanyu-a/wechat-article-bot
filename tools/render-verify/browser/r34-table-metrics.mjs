/**
 * 第三十四轮 · 用户报障「表格应自适应高度与宽度，留白不要太大」的**逐项取证**（只量不改）。
 *
 * 背景见文末「发现」一节；先讲量法。
 *
 * ## 这一支量什么
 *
 * 同一台真实 Chrome、同一组内容盒宽度，**两层**量法：
 *
 * **第一层 —— 单个用例的四个面 + 消融组**（回答「高出来的那一截是谁给的」）
 *
 * | 面 | 是什么 | 为什么要有 |
 * | --- | --- | --- |
 * | A 原项目 | 渲染服务 `preview` 整页里的 `#article` | **产品真值**，用户说「不如原项目」的那个原项目 |
 * | B 编辑器 | 探针页里 `setContent(产物)` 之后的实时 DOM | 用户在编辑器里真正看到的 |
 * | C 裸容器 | 产物原文注进一个**没有** `.ProseMirror` 的 div | A 的最小复现：证明差异来自样式，不是注入方式 |
 * | D 消融组 | 产物注进 `.ProseMirror`，逐条 `!important` 压掉兜底声明 | 把差异逐条归因到具体声明 |
 * | E 候选修法 | 与将来要写进 `style.css` 的选择器**同构**（不带 `!important`） | 修法落地前先量它能把数拉回多少 |
 *
 * **第二层 —— 全部 21 个真实产物过一遍候选修法**（回答「这一改动到几张表」）
 * 单看 r16-09 会得出「全改对」，而 21 个产物里有 8 张是 `border-spacing` 卡片布局
 * （步骤卡、标题卡那种），它们靠单元格自己的 `border-radius` + `border` 画卡片边——
 * 一律 `border:0` 会把卡片描边**画没**。所以第二层不是锦上添花，它是**驳回一个错修法**的依据。
 *
 * ## 为什么不能直接引用 `browser/r16_result.json`
 *
 * 那份对照表里的「参照（渲染服务产物）」是把产物注进**一个 `.ProseMirror` 容器**量的
 * （`probe.css` 的注释写明了这个用意：「为了让同一份 style.css 生效」）。于是参照侧**也吃到了
 * 编辑器那句 `.ProseMirror{font-size:15px;line-height:1.95}`**——`line-height` 是无单位数字，
 * 会按子元素自己的 `font-size` 重算，13px 的单元格拿到 `13×1.95=25.35px`。
 * 同理 `.ProseMirror table{table-layout:fixed}` 也让参照侧变成等宽列。
 *
 * **产品里的原项目没有这一句**：渲染服务的预览页（本用例的 `preview` 字段，一份独立 HTML）
 * 只有 `body{margin:0;font-family:...}` 与 `.page{max-width:677px;padding:32px 24px}`，
 * 正文容器上**没有** `font-size` / `line-height` / `table-layout`。
 *
 * 后果：拿带 `.ProseMirror` 的参照去比，等于**两边都带病**，比出来的「一致」只说明两栏都被
 * 同一套编辑器样式污染过；用户看到的「比原项目高一大截」正好被这个量法抹掉了。
 *
 * ## 纪律
 *
 * - **只量不改**：不发写请求、不动源码、不动库；所有消融都发生在页面内存里的隐藏容器上。
 * - `!important` 在第一层是**量具**不是修法（要压过 `.ProseMirror` 那几条规则才能看清单条贡献）；
 *   第二层刻意**不用** `!important`，用与真实选择器同构的写法。
 * - `preview.html` 由 `preview` 字段原样落盘、逐字节未改；产物原文由 `html` 字段落盘。
 *
 * 用法：
 *     node tools/render-verify/browser/r34-table-metrics.mjs                # 单用例四面对照 + 候选修法（主场）
 *     node tools/render-verify/browser/r34-table-metrics.mjs --sweep [--only <id>]  # 全部产物过一遍候选修法
 * 产物：stdout + `target/probe/r34/table_metrics.json`（或 `table_sweep.json`）
 */
import { readFileSync, writeFileSync, existsSync, mkdirSync, readdirSync } from 'node:fs'
import { createServer } from 'node:http'
import { resolve, extname, join } from 'node:path'
import { PROBE_DIST, OUT } from '../paths.mjs'
import { launchBrowser, openPage } from './cdp.mjs'

const R34 = resolve(OUT, 'r34')
const ARTIFACT_PATH = resolve(R34, 'render.html')
const PREVIEW_PATH = resolve(R34, 'preview.html')
mkdirSync(R34, { recursive: true })
if (!existsSync(ARTIFACT_PATH) || !existsSync(PREVIEW_PATH)) {
  console.error('缺输入：' + R34 + ' 下要有 render.html（产物原文）与 preview.html（preview 字段原样）')
  process.exit(2)
}
const ARTIFACT = readFileSync(ARTIFACT_PATH, 'utf8')

/** 标记类名：与 `webui/src/editorExtensions.js` 里 `applyPreservedTableStyle()` 用的那个**同一个字符串**。 */
const MARKER_CLASS = 'mf-preserved'
/**
 * 标记类的**判据**（与产品代码 `PRODUCT_TABLE_STYLE` 逐字同构）：表格级 style 里有
 * `border-collapse: collapse`。第一版写的是「有 style 属性就算」，被真实应用证伪——
 * TipTap 给手写表格写的 `min-width: 75px;` 也是 style，于是手写表格被打上类、网格线画没
 * （`target/probe/r34/table_handmade_regression.mjs`）。指纹的依据是 14/14 个产物表都带它。
 */
const MARKER_FINGERPRINT = /border-collapse\s*:\s*collapse\b/i

/** 内容盒宽度：629 = 原项目 `.page` 内宽（677 − 24×2）；684 = 真实应用正文栏；731 = 探针页编辑器栏。 */
const WIDTHS = [629, 684, 731]

/** 面 F 的字体组：探针页继承的那串 vs 原项目预览页 `body` 上写的那串，再拆成单字体看是谁在撑行盒。 */
const FONT_GROUPS = [
  { key: 'f1-探针页继承', css: '' },
  { key: 'f2-原项目预览页那串', css: 'system-ui,-apple-system,"Segoe UI","Microsoft YaHei",sans-serif' },
  { key: 'f3-仅 system-ui', css: 'system-ui' },
  { key: 'f4-仅 sans-serif', css: 'sans-serif' },
  { key: 'f5-仅 Microsoft YaHei', css: '"Microsoft YaHei"' },
]

const MIME = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.png': 'image/png', '.svg': 'image/svg+xml', '.woff2': 'font/woff2' }
/** `/probe/*` → 探针 dist（带完整 style.css）；`/ref/*` → 本轮取证目录（preview.html 与产物原文）。 */
const server = createServer((request, response) => {
  const path = request.url.split('?')[0]
  const isRef = path.startsWith('/ref/')
  const base = isRef ? R34 : PROBE_DIST
  const file = resolve(base, '.' + (isRef ? path.slice(4) : (path === '/' ? '/probe_r16.html' : path)))
  if (!file.startsWith(base) || !existsSync(file)) { response.writeHead(404); response.end('not found'); return }
  response.writeHead(200, { 'Content-Type': MIME[extname(file)] || 'application/octet-stream' })
  response.end(readFileSync(file))
})
await new Promise((done) => server.listen(0, '127.0.0.1', done))
const origin = `http://127.0.0.1:${server.address().port}`

// ---------------------------------------------------------------------------
// 量具：把承载产物的根摆到**内容盒恰好等于 width**，再量表格
// ---------------------------------------------------------------------------
const MEASURE = `(function measure(root, width) {
  if (!root) return null;
  const round = (value) => Math.round(value * 100) / 100;
  const save = { width: root.style.width, maxWidth: root.style.maxWidth, boxSizing: root.style.boxSizing };
  const s0 = getComputedStyle(root);
  // 直接设 width 会因 padding/border 而让内容盒变窄；补上差值，让 clientWidth 精确落在 width 上。
  root.style.boxSizing = 'content-box';
  root.style.width = (width
    - parseFloat(s0.paddingLeft) - parseFloat(s0.paddingRight)
    - parseFloat(s0.borderLeftWidth) - parseFloat(s0.borderRightWidth)) + 'px';
  root.style.maxWidth = 'none';
  const table = root.querySelector('table');
  const rows = table ? [...table.querySelectorAll('tr')] : [];
  /**
   * ProseMirror 的表格 NodeView（TableView）构造时会给 table/colgroup 打**内联 style**，
   * 这些是**编辑器自己**写的、产物里没有的。要判「修法能不能压过它们」，就必须看到它们。
   * updateColumnsOnResize 在 colwidth 全空时写的是 width:'' + min-width:<N>px。
   */
  const liveInjected = table ? {
    tableStyleAttr: table.getAttribute('style'),
    colGroup: [...table.querySelectorAll('colgroup > col')].map((col) => col.getAttribute('style')),
  } : null;
  const cellStyle = (cell) => {
    if (!cell) return null;
    const s = getComputedStyle(cell);
    return {
      fontSize: s.fontSize, lineHeight: s.lineHeight, padding: s.padding,
      fontFamily: s.fontFamily,
      borders: [s.borderTopWidth, s.borderRightWidth, s.borderBottomWidth, s.borderLeftWidth].join(' '),
      borderColors: [s.borderTopColor, s.borderRightColor, s.borderBottomColor, s.borderLeftColor].join(' '),
      verticalAlign: s.verticalAlign,
    };
  };
  /**
   * 单元格里**第一行文本的行盒**高度。用途：把「每行比原项目高 3px」这类差异
   * 从行内边距/边框里剥出来——padding 与 border 都是明面上的数字，剩下的那截只能来自
   * 字体的行盒本身（line-height:normal 时由字体度量决定）。不量这个，
   * 「换字体能不能救」就只能靠猜。
   */
  const textLineBox = (cell) => {
    if (!cell || !cell.firstChild) return null;
    const range = document.createRange();
    range.selectNodeContents(cell.firstChild.nodeType === 3 ? cell.firstChild : cell.firstChild);
    const rect = range.getBoundingClientRect();
    if (!rect.height) return null;
    return { h: round(rect.height), w: round(rect.width) };
  };
  const result = {
    contentWidth: round(root.clientWidth),
    tableWidth: table ? round(table.getBoundingClientRect().width) : null,
    tableLayout: table ? getComputedStyle(table).tableLayout : null,
    tableBorderCollapse: table ? getComputedStyle(table).borderCollapse : null,
    tableInline: table ? table.getAttribute('style') : null,
    /** 修法是否真的落到了这个 DOM 上：PreservedTableView 构造/更新时打的标记类。 */
    tableClass: table ? table.className : null,
    tableHeight: table ? round(table.getBoundingClientRect().height) : null,
    /**
     * 「留白」不是表格盒高度能回答的：表格自己的 margin、外面那层 div.tableWrapper 的 margin、
     * 产物的 <section style="margin:16px 0px"> 都算。所以另外量三个数——
     * 承载根的**总高**、表格**上沿到根上沿**、表格**下沿到根下沿**。三个面同宽同根时可直接相减。
     */
    rootHeight: round(root.getBoundingClientRect().height),
    tableTopFromRoot: table ? round(table.getBoundingClientRect().top - root.getBoundingClientRect().top) : null,
    tableBottomToRootBottom: table ? round(root.getBoundingClientRect().bottom - table.getBoundingClientRect().bottom) : null,
    rowHeights: rows.map((tr) => round(tr.getBoundingClientRect().height)),
    headWidths: rows[0] ? [...rows[0].children].map((c) => round(c.getBoundingClientRect().width)) : [],
    bodyWidths: rows[1] ? [...rows[1].children].map((c) => round(c.getBoundingClientRect().width)) : [],
    headStyle: cellStyle(rows[0] && rows[0].children[0]),
    bodyStyle: cellStyle(rows[1] && rows[1].children[0]),
    headLineBox: textLineBox(rows[0] && rows[0].children[0]),
    bodyLineBox: textLineBox(rows[1] && rows[1].children[0]),
    liveInjected,
  };
  root.style.width = save.width; root.style.maxWidth = save.maxWidth; root.style.boxSizing = save.boxSizing;
  return result;
})`

const { client, close } = await launchBrowser({ port: 9372 })
const page = await openPage(client)
await page.send('Emulation.setDeviceMetricsOverride', { width: 1600, height: 1200, deviceScaleFactor: 1, mobile: false })

// ---- 面 A：原项目预览页（产品真值）----------------------------------------
/**
 * 量的是**原项目那个页面本身**：它只有自己那 5 条规则（`body` / `.toolbar` / `.page` / 按钮），
 * `.page #article` 下面**一条表格规则都没有**——产物的表格在那边长什么样，完全由产物自己的
 * 行内样式决定。webui 的 `style.css` 只导进探针页（`probe_r16.js:23`），这一页没有，
 * 所以面 A 不需要额外做什么隔离。
 */
await page.navigate(origin + '/ref/preview.html')
const A = JSON.parse(await page.evaluate(`JSON.stringify({
  pageWidth: getComputedStyle(document.querySelector('.page')).width,
  pagePadding: getComputedStyle(document.querySelector('.page')).padding,
  articleFontSize: getComputedStyle(document.querySelector('#article')).fontSize,
  articleLineHeight: getComputedStyle(document.querySelector('#article')).lineHeight,
  bodyFontFamily: getComputedStyle(document.body).fontFamily,
  loadedSheets: [...document.styleSheets].map((sheet) => (sheet.href || 'inline') + ' #' + (sheet.cssRules ? sheet.cssRules.length : -1)),
  byWidth: ${JSON.stringify(WIDTHS)}.map((width) => ({ width, metrics: ${MEASURE}(document.querySelector('#article'), width) })),
})`))

// ---- 面 B：编辑器实时 DOM -------------------------------------------------
const meta = JSON.parse(readFileSync(resolve(OUT, 'r16', 'r16.json'), 'utf8'))
const CASE = meta.cases.find((item) => item.id === 'r16-09-table-card')
await page.navigate(origin + '/probe_r16.html')
await page.evaluate(`window.mountAll(${JSON.stringify([{ id: CASE.id, syntax: CASE.name, category: 'x', note: '', html: ARTIFACT }])})`)
const B = JSON.parse(await page.evaluate(`JSON.stringify(${JSON.stringify(WIDTHS)}.map((width) => ({
  width,
  metrics: ${MEASURE}(document.querySelector('[data-sample="r16-09-table-card"] .probe-canvas[data-variant="after"] .ProseMirror'), width),
})))`))

// ---- 面 C / D：裸容器 + 消融组 --------------------------------------------
/**
 * 消融是**逐条**的：每组只压掉一条兜底声明，看行高/列宽哪一条动。
 * 选择器里的 `data-abl` 用**短键**（`c0`…`c5`），与 `build()` 传进去的一致。
 */
const ABLATIONS = [
  { key: 'c0', note: '产物注进 .ProseMirror，什么都不压（= 面 B 的离线复现）', css: '' },
  { key: 'c1', note: '压掉 .ProseMirror table{table-layout:fixed}', css: '[data-abl="c1"] table{table-layout:auto !important}' },
  { key: 'c2', note: '压掉 .ProseMirror th,td{border:1px solid #dce2de} —— 四边归零，让产物自己的 border-bottom 说话', css: '[data-abl="c2"] th,[data-abl="c2"] td{border:0 !important}' },
  { key: 'c3', note: '压掉 .ProseMirror 的 line-height:1.95 对单元格的继承', css: '[data-abl="c3"] th,[data-abl="c3"] td{line-height:normal !important}' },
  { key: 'c4', note: '诊断项：压掉产物自己的内边距（**不是修法**，只为量出 padding 占多少高度）', css: '[data-abl="c4"] th,[data-abl="c4"] td{padding:0 !important}' },
  { key: 'c5', note: '诊断项：把正文容器字体换成原项目预览页那一串（量出「字体差」贡献多少高度）', css: '[data-abl="c5"]{font-family:system-ui,-apple-system,"Segoe UI","Microsoft YaHei",sans-serif !important}' },
  { key: 'c123', note: '三条一起压掉；若结果 ≈ 面 C（裸容器），则差异已全部归因', css: '[data-abl="c123"] table{table-layout:auto !important}\n[data-abl="c123"] th,[data-abl="c123"] td{border:0 !important;line-height:normal !important}' },
]
const ABLATION_CSS = ABLATIONS.map((item) => item.css).filter(Boolean).join('\n')

/**
 * **候选修法的精确模拟**（不是消融，是要落进 `style.css` 的那两条的等价物）：
 * 给「产物自带样式」的表格挂一个标记类，让三条兜底声明在**有产物样式时**让位——
 * 而不是删掉它们（手打的空表格还得靠它们长网格线）。
 *
 * 为什么 `padding` 不在里面：产物 171 个单元格**全都**自带 `padding`，行内声明的特异性本来就
 * 高过类选择器，不需要让位；留着兜底反而能保住「产物没写 padding」的表格。
 * 为什么 `line-height` 要在里面：兜底的是**继承来的** `1.95`，行内没写就一定会中招。
 * 为什么 `table-layout` 要在里面：产物 6 种表格里只有 2 种自己写了 `table-layout:fixed`
 * （`blk-title` / `r16-06-title-da01`），它们靠内联声明胜出；其余 4 种需要 auto。
 */
const CANDIDATE_CSS = '.ProseMirror table.' + MARKER_CLASS + '{table-layout:auto}'
  + '\n.ProseMirror table.' + MARKER_CLASS + ' th,.ProseMirror table.' + MARKER_CLASS + ' td{border:0;line-height:normal}'

const CD = JSON.parse(await page.evaluate(`(() => {
  const artifact = ${JSON.stringify(ARTIFACT)};
  const host = document.createElement('div');
  host.style.cssText = 'position:absolute;left:-99999px;top:0;width:1400px';
  document.body.appendChild(host);
  const style = document.createElement('style');
  style.textContent = ${JSON.stringify(ABLATION_CSS)};
  document.head.appendChild(style);
  const measure = ${MEASURE};
  const widths = ${JSON.stringify(WIDTHS)};

  /** 造一个承载产物的根：\`bare\` 不带 .ProseMirror，其余组都带。 */
  const build = (className, withProseMirror) => {
    const wrap = document.createElement('div');
    wrap.setAttribute('data-abl', className);
    wrap.className = withProseMirror ? 'ProseMirror bare-probe' : 'bare-probe';
    wrap.style.cssText = 'min-height:0;padding:0;margin:0;position:static';
    wrap.innerHTML = artifact;
    host.appendChild(wrap);
    return wrap;
  };

  const bare = build('bare', false);
  const cBare = widths.map((width) => ({ width, metrics: measure(bare, width), html: bare.innerHTML === artifact }));
  const perGroup = {};
  for (const group of ${JSON.stringify(ABLATIONS.map((item) => ({ key: item.key, note: item.note })))}) {
    const key = group.key;
    const node = build(key, true);
    perGroup[group.key] = { note: group.note, byWidth: widths.map((width) => ({ width, metrics: measure(node, width) })) };
  }
  return JSON.stringify({ cBare, perGroup });
})()`))

// ---- 面 E：候选修法（不带 !important 的那两条，逐宽度看）--------------------
/**
 * 模拟方式必须与**真要写进 `style.css` 的选择器**同构，否则量的是别的东西：
 * 同一条规则 `table.mf-preserved{table-layout:auto}` / `th,td{border:0;line-height:normal}`，
 * 靠给表格加一个 `mf-preserved` 类来开关——**没有 `!important`**，因为是另一条更特指的选择器
 * （`table.mf-preserved` 比兜底的 `table` 多一个类），这才是候选修法在层叠里的真实位置。
 * 同时保留兜底规则，逐条量「产物自带样式的表」与「不带样式的表」分别会变成什么样。
 */
const E = JSON.parse(await page.evaluate(`(() => {
  const artifact = ${JSON.stringify(ARTIFACT)};
  const host = document.createElement('div');
  host.style.cssText = 'position:absolute;left:-99999px;top:0;width:1400px';
  document.body.appendChild(host);
  const style = document.createElement('style');
  style.textContent = ${JSON.stringify(CANDIDATE_CSS)};
  document.head.appendChild(style);
  const measure = ${MEASURE};
  const widths = ${JSON.stringify(WIDTHS)};
  const make = (withClass, withProseMirror) => {
    const wrap = document.createElement('div');
    wrap.className = withProseMirror ? 'ProseMirror' : '';
    wrap.style.cssText = 'min-height:0;padding:0;margin:0;position:static';
    wrap.innerHTML = artifact;
    if (withClass) for (const table of wrap.querySelectorAll('table')) {
      if (${MARKER_FINGERPRINT}.test(table.getAttribute('style') || '')) table.classList.add(${JSON.stringify(MARKER_CLASS)});
    }
    host.appendChild(wrap);
    return wrap;
  };
  const e1 = make(true, true);
  const e0 = make(false, true);
  return JSON.stringify({
    e0_不打类: widths.map((width) => ({ width, metrics: measure(e0, width) })),
    e1_打类: widths.map((width) => ({ width, metrics: measure(e1, width) })),
    classApplied: [...e1.querySelectorAll('table')].map((t) => t.className),
  });
})()`))

// ---- 面 F：字体行盒 —— 「E1 比 A 高 15px」这截到底是谁的 ----------------------
/**
 * E1（打类）已经和整页裸容器（面 C）逐格相同，却仍比面 A 的**整页**高 15px。
 * 面 C 与面 A 的差别只剩一条：字体。A 的 `body{font-family:system-ui,-apple-system,
 * 'Segoe UI','Microsoft YaHei',sans-serif}`，C/E 在探针页里吃的是 webui 的
 * `'DM Sans','Noto Sans SC',system-ui,sans-serif`。
 *
 * 所以这里把同一份产物在**同一台浏览器**里按字体逐组量：每个字体组量一次单元格里
 * 第一行文本的**行盒高度**（`Range.getBoundingClientRect()`，把 padding/border 排除在外）。
 * 判据是数字本身：若换字体能让行盒从 ~15px 掉回 ~12.5px，那 15px 那截就是字体差，
 * 修表格 CSS 救不了它，也不该由本轮去救。
 */
const F = JSON.parse(await page.evaluate(`(() => {
  const artifact = ${JSON.stringify(ARTIFACT)};
  const host = document.createElement('div');
  host.style.cssText = 'position:absolute;left:-99999px;top:0;width:1400px';
  document.body.appendChild(host);
  const families = ${JSON.stringify(FONT_GROUPS)};
  const measure = ${MEASURE};
  const out = [];
  for (const family of families) {
    const wrap = document.createElement('div');
    wrap.className = 'ProseMirror';
    wrap.style.cssText = 'min-height:0;padding:0;margin:0;position:static';
    wrap.innerHTML = artifact;
    for (const table of wrap.querySelectorAll('table')) {
      if (${MARKER_FINGERPRINT}.test(table.getAttribute('style') || '')) table.classList.add(${JSON.stringify(MARKER_CLASS)});
    }
    if (family.css) wrap.style.fontFamily = family.css;
    host.appendChild(wrap);
    const metrics = measure(wrap, 629);
    out.push({ key: family.key, family: family.css || getComputedStyle(wrap).fontFamily,
      metrics: { tableHeight: metrics.tableHeight, rowHeights: metrics.rowHeights,
        headLineBox: metrics.headLineBox, bodyLineBox: metrics.bodyLineBox,
        bodyFontFamily: metrics.bodyStyle.fontFamily } });
  }
  return JSON.stringify(out);
})()`))

const EFIX = JSON.parse(await page.evaluate(`(() => {
  const artifact = ${JSON.stringify(ARTIFACT)};
  const host = document.createElement('div');
  host.style.cssText = 'position:absolute;left:-99999px;top:0;width:1400px';
  document.body.appendChild(host);
  const style = document.createElement('style');
  style.textContent = ${JSON.stringify(CANDIDATE_CSS)};
  document.head.appendChild(style);
  const measure = ${MEASURE};
  const wrap = document.createElement('div');
  wrap.className = 'ProseMirror';
  wrap.style.cssText = 'min-height:0;padding:0;margin:0;position:static;font-family:system-ui,-apple-system,"Segoe UI","Microsoft YaHei",sans-serif';
  wrap.innerHTML = artifact;
  for (const table of wrap.querySelectorAll('table')) {
    if (${MARKER_FINGERPRINT}.test(table.getAttribute('style') || '')) table.classList.add(${JSON.stringify(MARKER_CLASS)});
  }
  host.appendChild(wrap);
  return JSON.stringify(measure(wrap, 629));
})()`))

const result = { 轮次: '第三十四轮', 用例: CASE.id, 报障原文: CASE.complaint, 求: '表格应自适应高度与宽度，留白不要太大，可与原项目对比一下',
  产物: ARTIFACT_PATH, 原项目预览页: PREVIEW_PATH, 宽度: WIDTHS, 面A_原项目: A, 面B_编辑器: B, 面C_裸容器: CD.cBare,
  面D_消融组: CD.perGroup, 面E_候选修法: E, 面F_字体: F, 候选修法加原项目字体: EFIX }

// ---- stdout 摘要：一屏能读完的对照 -----------------------------------------
const lines = ['', '宽度 | 面 | 表高 | 根总高 | 表上留白 | 表下留白 | 逐行高 | layout | 单元格边框 | 单元格行高', '-'.repeat(140)]
const row = (label, width, m) => `${String(width).padStart(4)} | ${label} | ${String(m.tableHeight).padStart(7)} | ${String(m.rootHeight).padStart(7)} | ${String(m.tableTopFromRoot).padStart(6)} | ${String(m.tableBottomToRootBottom).padStart(6)} | [${m.rowHeights.join(', ')}] | ${m.tableLayout.padEnd(5)} | ${m.bodyStyle.borders} | ${m.bodyStyle.lineHeight}`
for (const item of A.byWidth) lines.push(row('A 原项目', item.width, item.metrics))
for (const item of B) lines.push(row('B 编辑器', item.width, item.metrics))
for (const item of CD.cBare) lines.push(row('C 裸容器', item.width, item.metrics))
for (const group of Object.keys(CD.perGroup)) {
  const item = CD.perGroup[group].byWidth.find((entry) => entry.width === 629)
  lines.push(row('D ' + group, 629, item.metrics))
}
lines.push('', '候选修法（不带 !important，与要落进 style.css 的选择器同构）：')
for (const item of E.e0_不打类) lines.push(row('E0 不打类', item.width, item.metrics))
for (const item of E.e1_打类) lines.push(row('E1 打类', item.width, item.metrics))
lines.push('', '列宽（内容盒 629px）:')
lines.push('  A 原项目 表头 ' + JSON.stringify(A.byWidth.find((i) => i.width === 629).metrics.headWidths))
lines.push('  B 编辑器 表头 ' + JSON.stringify(B.find((i) => i.width === 629).metrics.headWidths))
lines.push('  E1 打类  表头 ' + JSON.stringify(E.e1_打类.find((i) => i.width === 629).metrics.headWidths))
lines.push('  A 原项目 首行 ' + JSON.stringify(A.byWidth.find((i) => i.width === 629).metrics.bodyWidths))
lines.push('  B 编辑器 首行 ' + JSON.stringify(B.find((i) => i.width === 629).metrics.bodyWidths))
lines.push('', '原项目正文容器: font-size=' + A.articleFontSize + ' line-height=' + A.articleLineHeight
  + ' family=' + A.bodyFontFamily)
lines.push('原项目 .page: width=' + A.pageWidth + ' padding=' + A.pagePadding)
lines.push('裸容器注入后与产物原文逐字节相同: ' + CD.cBare.every((item) => item.html))
lines.push('E1 打类后的 table.className: ' + JSON.stringify(E.classApplied))
lines.push('E1 与 A 的表高差（三个宽度）: ' + E.e1_打类.map((item, index) =>
  `${item.width}→${Math.round((item.metrics.tableHeight - A.byWidth[index].metrics.tableHeight) * 100) / 100}`).join('  '))
lines.push('', '面 F · 字体（都是内容盒 629px，且都打了 mf-preserved 类）：')
for (const item of F) {
  lines.push('  ' + item.key.padEnd(18) + ' 表高 ' + String(item.metrics.tableHeight).padStart(7)
    + ' | 首行行盒 ' + JSON.stringify(item.metrics.bodyLineBox) + ' | 首行行高 ' + JSON.stringify(item.metrics.rowHeights.slice(0, 2)))
}
lines.push('  候选修法 + 原项目字体: 表高 ' + EFIX.tableHeight + ' | 逐行高 ' + JSON.stringify(EFIX.rowHeights)
  + ' | 首行行盒 ' + JSON.stringify(EFIX.bodyLineBox))
/** 判据看**实时 DOM 上那张表**有没有拿到标记类——这是「修法真的在跑」的证据，不是模拟。 */
const stamped = B.map((item) => item.metrics.tableClass)
  .filter((value, index, all) => all.indexOf(value) === index)
lines.push('B 面实时 DOM 的 table.className: ' + JSON.stringify(stamped) + '（模拟层不参与这一项）')

console.log(lines.join('\n'))
writeFileSync(resolve(R34, 'table_metrics.json'), JSON.stringify(result, null, 1), 'utf8')
writeFileSync(resolve(R34, 'table_metrics.txt'), lines.join('\n') + '\n', 'utf8')

// ---------------------------------------------------------------------------
// 第二层：全部真实产物过一遍候选修法
// ---------------------------------------------------------------------------
/**
 * 判据是**逐表双向**的：
 *   - ② `border-spacing` 类（卡片布局）**一格都不许动**——它们靠单元格自己的
 *     `border:1px solid …;border-radius:10px` 画卡片，一律 `border:0` 会把描边画没；
 *   - ① 其余表格的**逐行高与逐列宽**往面 A 靠，`table-layout` 必须变成 `auto`。
 *
 * 判据完全由「产物原文 + 当前 style.css 里那三条兜底规则」算出，没有硬编码的期望值；
 * 用 `--only <id前缀>` 可以只跑一条（调试用）。
 */
if (process.argv.includes('--sweep')) {
  const only = process.argv.includes('--only') ? process.argv[process.argv.indexOf('--only') + 1] : null
  const DIRS = ['components', 'r16', 'alt', 'combos', 'registry']
  const files = []
  for (const dir of DIRS) {
    const full = resolve(OUT, dir)
    if (!existsSync(full)) continue
    for (const name of readdirSync(full)) {
      if (!name.endsWith('.html') || name.endsWith('.editor.html')) continue // `.editor.html` 是编辑器自己的输出，不是上游产物
      if (!/<table[\s>]/i.test(readFileSync(resolve(full, name), 'utf8'))) continue // 连表格都没有的产物不该进这张表
      if (only && !name.startsWith(only)) continue
      files.push({ dir, name, path: resolve(full, name) })
    }
  }
  const sweep = JSON.parse(await page.evaluate(`(() => {
    const host = document.createElement('div');
    host.style.cssText = 'position:absolute;left:-99999px;top:0;width:1400px';
    document.body.appendChild(host);
    const style = document.createElement('style');
    style.textContent = ${JSON.stringify(CANDIDATE_CSS)};
    document.head.appendChild(style);
    const measure = ${MEASURE};
    const cases = ${JSON.stringify(files.map((item) => ({ dir: item.dir, name: item.name, html: readFileSync(item.path, 'utf8') })))};
    const fingerprint = ${MARKER_FINGERPRINT};
    const marker = ${JSON.stringify(MARKER_CLASS)};
    const out = [];
    for (const item of cases) {
      /** withProseMirror=false 时那个 div 上**一条**编辑器样式都吃不到，就是原项目那边的条件。 */
      const make = (withClass, withProseMirror) => {
        const wrap = document.createElement('div');
        wrap.className = withProseMirror === false ? '' : 'ProseMirror';
        wrap.style.cssText = 'min-height:0;padding:0;margin:0;position:static';
        wrap.innerHTML = item.html;
        // 与 applyPreservedTableStyle() 逐字同构：**只有带产物指纹的表**才打类。
        // 判据必须是 border-collapse:collapse 而不是「有 style 属性」——后者会把 TipTap
        // 给手写表格写的 min-width:75px 也算进去，实测后果是网格线被画没。
        if (withClass) for (const table of wrap.querySelectorAll('table')) {
          if (fingerprint.test(table.getAttribute('style') || '')) table.classList.add(marker);
        }
        host.appendChild(wrap);
        return wrap;
      };
      const before = make(false), after = make(true);
      /**
       * **面 C 的逐产物版**：同一份产物注进一个**没有** .ProseMirror 的 div。
       * 这就是「原项目那边的渲染条件」——渲染服务的预览页里没有任何 .ProseMirror 规则，
       * 表格长什么样完全由产物自己的行内样式决定。所以它是**产品真值**，不是又一层模拟。
       * 有它才能把「描边对不对」判成**看得见的事实**，而不是「跟改前比少了没」——
       * 后者会把「原项目本来就没有网格线、是 webui 兜底多画的」误判成丢失。
       */
      const bare = make(false, false);
      const styles = [...item.html.matchAll(/<table[^>]*style="([^"]*)"/gi)].map((m) => m[1]);
      const tableStyle = styles.join(' || ');
      // 有产物指纹 ⇒ 打类；没有 ⇒ 不打类、继续吃兜底。这条判据与修法里的 PRODUCT_TABLE_STYLE 必须逐字同构。
      const preservedCount = [...before.querySelectorAll('table')]
        .filter((t) => fingerprint.test(t.getAttribute('style') || '')).length;
      const totalTables = before.querySelectorAll('table').length;
      const beforeMetrics = measure(before, 629), afterMetrics = measure(after, 629), bareMetrics = measure(bare, 629);
      /**
       * **锚的洁净度**：bare 那个 div 上必须**一个类都没有**——一旦它带上了 .ProseMirror，
       * 它就又吃回了编辑器那套兜底样式，锚便被污染成「改前」的副本，整条判据都在自证。
       * 这一点必须交出来，因为第一版正是这么错的：make() 当时忽略第二个参数，
       * 于是「裸容器」量到的 fixed + 1px 1px 1px 1px 其实是兜底样式，不是产物真值。
       */
      const anchorClass = bare.className;
      const anchorClean = anchorClass === '';
      /** 采样格的逐边边框原文（0px 0px 1px 0px 这种），直接与裸容器比。 */
      const borders = (metrics) => [metrics.headStyle && metrics.headStyle.borders,
        metrics.bodyStyle && metrics.bodyStyle.borders].filter(Boolean).join(' / ');
      out.push({
        dir: item.dir, name: item.name,
        cellCount: (item.html.match(/<(?:td|th)(?=[\\s>])/g) || []).length,
        tableStyle, totalTables, preservedCount,
        /** 卡片布局：靠单元格自己的 border/border-radius 画卡片；网格表格：靠拼接画网格。 */
        kind: ${MARKER_FINGERPRINT}.test(tableStyle) ? '网格表格（有产物指纹）'
          : /border-spacing/.test(tableStyle) ? '卡片布局（border-spacing，不打类）'
          : '卡片布局（单元格自带边框，不打类）',
        before: beforeMetrics, after: afterMetrics, bare: bareMetrics,
        anchorClass, anchorClean,
        边框: { before: borders(beforeMetrics), after: borders(afterMetrics), bare: borders(bareMetrics) },
        布局: { before: beforeMetrics.tableLayout, after: afterMetrics.tableLayout, bare: bareMetrics.tableLayout },
      });
    }
    return JSON.stringify(out);
  })()`))

  /** 逐条判：卡片布局「一格都不许动」；网格表格「行高列宽要往 auto 靠」。 */
  const judged = sweep.map((item) => {
    const before = item.before, after = item.after
    const moved = before.rowHeights.join(',') !== after.rowHeights.join(',')
      || before.headWidths.join(',') !== after.headWidths.join(',')
      || before.tableLayout !== after.tableLayout
    /** 首行单元格样式：有的表只有一行（标题卡那种），bodyStyle 会是 null，用表头顶上。 */
    const cell = after.bodyStyle || after.headStyle
    if (item.kind.startsWith('卡片布局')) {
      // 指纹收紧后的**新契约**：卡片布局根本不满足 `border-collapse:collapse`，拿不到类，
      // 所以 `after` 必须与 `before` 逐项相同。判据落在「动没动」上——包括单元格边框
      // （它们靠 border 画卡片边，类一旦误落到这里就会被 `border:0` 画没）。
      const borderKept = Boolean(cell) && cell.borders.split(' ').some((value) => parseFloat(value) > 0)
      const untouched = !moved && borderKept && item.preservedCount === 0
      return { ...item, 判: untouched ? 'PASS 不打类、逐项未动' : 'FAIL 卡片布局被打类或被动过（描边还在：' + borderKept + '）', 变了: moved, 描边还在: borderKept }
    }
    const layoutOk = item.after.tableLayout === 'auto'
      // 产物**自己**写了 table-layout 的表（blk-title / r16-06 标题卡的 `table-layout:fixed`）：
      // 行内声明特异性高过类选择器，修法本来就压不动、也不该压——那是上游故意的双列布局。
      || /table-layout/.test(item.tableStyle)
    const movedOk = moved
    /**
     * 描边判据的**锚**是裸容器（= 原项目那边的渲染条件，见上面 bare 的注释），不是「改前」。
     * 拿改前当锚会得出与事实相反的结论：webui 兜底那句 border:1px solid #dce2de **一直在替产物
     * 画网格线**，所以改前的「7 条边框」里有相当一部分是编辑器凭空加的，产物和原项目根本没有。
     * 锚定到裸容器之后，判据变成可证伪的一句话：**打类后的逐边边框必须与裸容器逐字符相同**。
     *   - 少了 ⇒ 修法把产物自己的描边吃掉了（真丢失）；
     *   - 多了 ⇒ 兜底还在和产物抢着画（真穿帮）。
     *
     * ⚠️ **锚自身必须是干净的**，否则这条判据是在拿改前比改后、自我印证。第一版就这么错过了：
     * 当时 make() 只有一个参数、`bare` 那个 div 也带着 .ProseMirror，于是「裸容器」量到的
     * fixed + 1px 1px 1px 1px 其实是兜底样式本身——而它偏偏与「改前」逐字符相同，所以
     * 7 个产物被误报成 FAIL（「网格线被画没」）。改成真的裸 div 之后
     * （`anchorClean` 落进产物 JSON，可复查），7 个 FAIL 全部翻 PASS。
     */
    const borderParity = item.边框.after === item.边框.bare
    const layoutParity = item.布局.after === item.布局.bare
    const anchorOk = item.anchorClean && borderParity && layoutParity
    return { ...item, 判: !item.anchorClean ? 'FAIL 锚被污染（bare 容器带类：' + item.anchorClass + '）'
      : !layoutOk ? 'FAIL 仍是 fixed'
      : !anchorOk ? 'FAIL 与裸容器不一致（边框 after=' + item.边框.after + ' vs 裸=' + item.边框.bare
        + '；layout ' + item.布局.after + ' vs ' + item.布局.bare + '）'
        : movedOk ? 'PASS 已自适应 + 与裸容器一致' : 'WARN 布局变了但几何没动',
      变了: moved, 锚: (item.anchorClean ? '干净' : '有类') + '·' + (borderParity ? '边框=裸' : '边框≠裸') }
  })

  const sl = ['', '第二层 · 全部真实产物过一遍候选修法（内容盒 629px）', '-'.repeat(110),
    '产物 | 格数 | 类型 | 表高 before→after | layout before→after→裸容器 | 采样格边框 after vs 裸容器 | 表头列宽 before→after | 判']
  for (const item of judged) {
    sl.push(`${item.name.padEnd(30)} | ${String(item.cellCount).padStart(3)} | ${item.kind} | `
      + `${item.before.tableHeight} → ${item.after.tableHeight} | ${item.布局.before} → ${item.布局.after} → ${item.布局.bare} | `
      + `[${item.边框.after}] vs [${item.边框.bare}] | [${item.before.headWidths.join(', ')}] → [${item.after.headWidths.join(', ')}] | ${item.判}`)
  }
  const pass = judged.filter((item) => item.判.startsWith('PASS')).length
  const warn = judged.filter((item) => item.判.startsWith('WARN')).length
  const fail = judged.filter((item) => item.判.startsWith('FAIL'))
  sl.push('', `共 ${judged.length} 个产物：PASS ${pass} · WARN ${warn} · FAIL ${fail.length}`)
  for (const item of fail) sl.push('  FAIL ' + item.name + ' → ' + item.判)
  sl.push('候选 CSS：', CANDIDATE_CSS.split('\n').map((line) => '  ' + line).join('\n'))
  /**
   * **反证**（判据不是在自证）：把「裸容器」污染成带 .ProseMirror —— 即第一版 bug 的样子 ——
   * 七个产物必须立刻翻成 FAIL。不自证的关键就在这里：本脚本自己不作证，改坏一个叶子再跑一遍
   * 才算数。跑法（人工一次，结论记在这）：
   *     把 `wrap.className = withProseMirror === false ? '' : 'ProseMirror'` 改成 `= 'ProseMirror'`，
   *     重跑 `--sweep`：实测 **PASS 4 · FAIL 7**，七条全是「锚被污染（bare 容器带类：ProseMirror）」。
   * 这也顺带说明第一版那七个 FAIL 是**判据自己有病**，不是产物有问题——两版读数逐字符相同。
   */
  sl.push('反证：把裸容器污染成带 .ProseMirror，本判据实测翻成 PASS 4 · FAIL 7（详见源码注释）')
  console.log(sl.join('\n'))
  writeFileSync(resolve(R34, 'table_sweep.json'), JSON.stringify({ 候选CSS: CANDIDATE_CSS, 宽度: 629, 结果: judged }, null, 1), 'utf8')
  writeFileSync(resolve(R34, 'table_sweep.txt'), sl.join('\n') + '\n', 'utf8')
  process.exitCode = fail.length ? 1 : 0
}

await page.close(); close(); server.close()
