/**
 * 第二十八轮 A · 「**打开 → 保存 → 再打开**」的往返稳定性闸（可重跑的常规断言，带退出码）。
 *
 * 为什么要有这一条：第二十五轮把问题从「编辑器有没有丢声明」换成了用户的原话
 * 「我在编辑器里看到的样子，保存之后还在不在」；第二十六、二十七两轮又量到——
 * **真正让用户感知到的丢失，多半不发生在「保存」那一刻，而发生在「再打开」那一刻**
 * （段首空白在第一次解析时就没了、列宽在保存出口里被拍平、金句卡的段落间距多出来……）。
 * 一个只在「打开」时成立的断言，抓不住这类问题。所以这里把**整圈**钉死。
 *
 * 判据（**先立后测**，不因测量结果调整；任一条不过 **exit 1**）：
 *   ① **往返稳定**：把保存出口原样灌回去（走应用自己的 `setContent`：伪造 GET 的返回 + 整页导航）
 *      之后的 DOM，与**保存那一刻**的 DOM **逐叶子值相同**（不做归一、不做容差）；
 *   ② **二次往返仍稳定**：再存一次、再灌一次，仍与第一次往返后**逐叶子值相同**
 *      —— 排除「要两圈才收敛到不动点」这种情况；
 *   ③ **入口保真**：每条样本各自声明「必须保住的量」，往返后仍然成立
 *      （段首空白的缩进、窄列表格的列宽声明、行首方框的盒尺寸、金句卡的高度）。
 *
 * ⚠️ **只有判据 ① 会漏掉第二十六轮那个 bug**：旧前端在「打开」和「再打开」时**都**丢段首空白，
 * A 与 B 两边一致地丢，①反而是过的。判据 ③ 就是为这种情况存在的——
 * 它比的是「入口声明的量」而不是「两次测量的自洽」。这一点由闸的自检（下）实测坐实。
 *
 * 「逐叶子值」的口径（与第二十七轮 C 的对账口径一致）：
 *   把 `.ProseMirror` 整棵树摊平成叶子——元素叶子 = `标签 + 逐属性（名字与值原文）`，
 *   文本叶子 = 文本节点原文；路径按「第几个子节点」拼出来，**结构一变路径就对不上，会如实报出来**。
 *   唯一的规范化是 `style` 属性值内部**声明按字典序排列**（`display:flex;margin:0` 与 `margin:0;display:flex`
 *   在 CSS 里是同一件事，而且浏览器在重新解析后的序列化顺序确实会变——第二十五轮已实测并记录）。
 *   除此之外**一个字节都不放宽**；两份原始 DOM 落盘，差异可以直接 diff。
 *
 * 样本（六条，覆盖点名的那几类）：
 *   甲 `plain` 普通正文（含行内标签、标题、列表）
 *   乙 `lead-space` 段首 2 个半角空格    丙 `lead-tab` 段首 1 个制表符
 *   丁 `table` 渲染服务的窄列表格产物（带列宽声明）
 *   戊 `checklist` 渲染服务的清单产物（行首方框 20×20）
 *   己 `quote-card` 渲染服务的金句卡产物（卡片高、`<p style="margin: 0px;">` 那一类）
 *
 * 闸的**自检**（防「写松了」）：
 *   ① 离线：拿第二十六轮之前的**存档实测**（`r27_entry_paths_before.json` 里 `setcontent` 那条路的
 *      真实 DOM 缩进 = 0px）套用判据③同一套阈值，必须判 **FAIL**；
 *      （⚠️ 本轮：该存档已随 `target/probe/` 清库丢失，不在时**跳过、不影响退出码**——行为与原设计一致。）
 *   ② 实跑：`--bundle target/probe/r26/before-dist`（修复前的整包前端）跑同一套断言，必须 **exit 1**；
 *   ③ **`--selftest`（本轮新立，纯离线）**：历史真跑存档 `r28_roundtrip_round26-before.json` 也已随清库丢失，
 *      改为合成一对「当前真测 + 被改坏副本（§3.26 修复前行为）」，用与主流程同一批纯函数
 *      （`leafDiffs` / `blockDiffs` / `invariants`，一字未改）判：真测不误报、坏副本上判据①② 仍 PASS
 *      （甲类盲区与声明一致）、判据③ 判红。历史真跑结论（修复前 ①② 0 处差异、③ 4 处不成立）定格不重写。
 *
 * ⚠️ **不写生产数据**：应用层拦下 `PUT`（拦到的 body 就是「保存出口」）+ CDP `Fetch.failRequest`
 * 兜底 + 跑完回读 `revision`/`updatedAt` 逐字比对。
 *
 * 用法：
 *     node tools/render-verify/browser/r28-roundtrip-gate.mjs
 *     node tools/render-verify/browser/r28-roundtrip-gate.mjs --label round26-before --bundle target/probe/r26/before-dist
 *     node tools/render-verify/browser/r28-roundtrip-gate.mjs --selftest   # 纯离线反例自检（合成），不开浏览器
 * 产物：target/probe/browser/r28_roundtrip_<label>.json
 *       target/probe/browser/r28_dom_<label>/<样本>.<第几圈>.html
 */
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs'
import { resolve, join } from 'node:path'
import { createHash } from 'node:crypto'
import { launchBrowser, openPage } from './cdp.mjs'
import { BROWSER_OUT, OUT, ROOT, API, login } from '../paths.mjs'
import { declaredWidth, widthsOf } from './colwidth-rules.mjs'

const ARGS = process.argv.slice(2)
const argOf = (name, fallback = null) => {
  const at = ARGS.indexOf(name)
  return at >= 0 && ARGS[at + 1] ? ARGS[at + 1] : fallback
}
const LABEL = argOf('--label', 'after')
const BUNDLE = argOf('--bundle') ? resolve(ROOT, argOf('--bundle')) : null
const PORT = Number(argOf('--port', '9367'))
const ARTICLE_ID = 38
if (BUNDLE && !existsSync(resolve(BUNDLE, 'index.html'))) { console.error('--bundle 目录里没有 index.html'); process.exit(3) }
const sleep = (ms) => new Promise((done) => setTimeout(done, ms))

/**
 * 只把 `style` 属性值里的声明按字典序重排（与叶子口径里那条唯一的规范化同一个东西）。
 * 用来回答「两份出口字节不同」到底是**声明顺序**还是**实质差异**——不作判据，只作解释。
 * （⚠️ 本轮从主流程中部上移到这里：`--selftest` 要在登录/起浏览器之前用到这批纯函数，函数本体一字未改。）
 */
const canonHtml = (html) => html.replace(/style="([^"]*)"/g, (whole, value) => 'style="'
  + value.split(';').map((part) => part.trim()).filter(Boolean).sort().join(';') + '"')
const leafDiffs = (a, b) => {
  const left = new Map(a.leaves.map((leaf) => [leaf.p, leaf]))
  const right = new Map(b.leaves.map((leaf) => [leaf.p, leaf]))
  const diffs = []
  for (const path of new Set([...left.keys(), ...right.keys()])) {
    const one = left.get(path)
    const other = right.get(path)
    if (JSON.stringify(one) === JSON.stringify(other)) continue
    diffs.push({ 路径: path, 保存时: one ?? '(没有这个节点)', 再打开后: other ?? '(没有这个节点)' })
  }
  return diffs
}
const blockDiffs = (a, b) => {
  const diffs = []
  const total = Math.max(a.blocks.length, b.blocks.length)
  for (let index = 0; index < total; index += 1) {
    const one = a.blocks[index]
    const other = b.blocks[index]
    if (JSON.stringify(one) === JSON.stringify(other)) continue
    diffs.push({ 第几块: index, 保存时: one ?? '(没有这一块)', 再打开后: other ?? '(没有这一块)' })
  }
  return diffs
}
const digest = (text) => createHash('sha256').update(text || '', 'utf8').digest('hex').slice(0, 16)

/** 判据③ 的三种谓词，全部在 Node 侧判，规则写死在样本声明里。 */
const invariants = (sample, snap, 入口HTML) => {
  const fails = []
  if (sample.期望缩进) {
    const hit = snap.blocks.find((block) => block.text.includes(sample.期望缩进.锚点))
    if (!hit) fails.push({ 项: '期望缩进', 说明: '往返后在正文里找不到锚点块「' + sample.期望缩进.锚点 + '」' })
    else if (hit.缩进 === null || hit.缩进 < sample.期望缩进.至少) {
      fails.push({ 项: '期望缩进', 说明: '锚点块「' + sample.期望缩进.锚点 + '」的段首缩进 ' + hit.缩进
        + 'px < 要求的 ' + sample.期望缩进.至少 + 'px' })
    }
  }
  if (sample.列宽一致) {
    // 入口的声明从样本 HTML 上读，往返后的声明从**实时 DOM 上的 `<col>` 元素**上读，
    // 两侧用的是同一份规则（colwidth-rules.mjs，与 U10 那道闸共用一个定义）。
    const entry = widthsOf(入口HTML)
    const here = snap.cols.map(declaredWidth)
    if (JSON.stringify(entry) !== JSON.stringify(here)) {
      fails.push({ 项: '列宽一致', 说明: '入口声明 ' + JSON.stringify(entry) + ' → 往返后 ' + JSON.stringify(here) })
    }
  }
  for (const rule of sample.元素盒 || []) {
    const boxes = snap.boxes[rule.选择器] || []
    const hit = boxes.filter(rule.谓词).length
    if (hit < rule.至少) {
      fails.push({ 项: '元素盒', 说明: rule.说明 + '：`' + rule.选择器 + '` 里满足条件的只有 ' + hit
        + ' 个（要求至少 ' + rule.至少 + ' 个）· 实得盒 ' + JSON.stringify(boxes.slice(0, 8)) })
    }
  }
  if (sample.至少零间距段落 && snap.段落margin零 < sample.至少零间距段落) {
    fails.push({ 项: '零间距段落', 说明: '往返后上下 margin 都是 0 的有文字的段落只剩 ' + snap.段落margin零
      + ' 个（要求至少 ' + sample.至少零间距段落 + ' 个）——金句卡里那层合成段落一旦重新拿到间距，卡片就会凭空变高' })
  }
  if (sample.至少顶层块 && snap.blocks.length < sample.至少顶层块) {
    fails.push({ 项: '顶层块数', 说明: '往返后只剩 ' + snap.blocks.length + ' 块（要求至少 ' + sample.至少顶层块 + '）' })
  }
  return fails
}

// ---------- `--selftest`：纯离线反例自检（合成一对「当前真测 + 被改坏副本」），不开浏览器、不连库 ----------
// ⚠️ 历史真跑存档 `r28_roundtrip_round26-before.json`（`--bundle target/probe/r26/before-dist` 那一跑）
//    已随 `target/probe/` 清库丢失，**不伪造存档**；历史结论（修复前 bundle：判据①② **0 处差异**
//    —— 两侧一致地丢，甲类盲区；判据③ 报 **4 处**不成立、整闸 exit 1）**已定格，不重写**。
// 反例改按 §3.26 的坏版本构造**合成**：用上面同一批纯函数（`leafDiffs` / `blockDiffs` / `invariants`，一字未改），
// 合成一对「当前真测（窄修法生效：段首空白活着）＋ 被改坏副本（修复前行为：段首空白两侧一致地被吃）」：
//   · 真测：判据①② 0 差异、判据③ 0 失败（不误报）
//   · 坏副本：判据①② 仍 0 差异（＝甲类盲区的演示：两侧一致地丢，①② 的口径结构性抓不住）
//             判据③ 失败（＝入口期望值这一格抓得住）
// 任一不符 **exit 1**。
if (ARGS.includes('--selftest')) {
  const 样本 = { id: 'lead-space', 说明: '段首 2 个半角空格（合成自检）',
    html: '<p>  LEAD-SP</p><p>PLAIN</p>', 期望缩进: { 锚点: 'LEAD-SP', 至少: 5 } }
  const INDENT = 6.72  // 2 个半角空格的实测宽度（第二十六轮）：窄修法生效时段首空白以 &nbsp; 活着并占位
  const 快照 = (存活) => ({
    left: 702, top: 0, whiteSpace: 'normal', html: '',
    leaves: [{ p: '/0/0', k: 't', v: 存活 ? '\u00A0\u00A0LEAD-SP' : 'LEAD-SP' }, { p: '/1/0', k: 't', v: 'PLAIN' }],
    blocks: [
      { tag: 'p', text: 存活 ? '\u00A0\u00A0LEAD-SP' : 'LEAD-SP', dy: 0, h: 27,
        首字符x: 存活 ? 702 + INDENT : 702, 缩进: 存活 ? INDENT : 0 },
      { tag: 'p', text: 'PLAIN', dy: 40, h: 27, 首字符x: 702, 缩进: 0 },
    ],
    boxes: {}, cols: [], 段落margin零: 2,
  })
  // 当前真测：A（保存时）/ B（第一次往返）/ C（第二次往返）三份快照逐叶子值相同。
  const A = 快照(true), B = 快照(true), C = 快照(true)
  // 被改坏副本（§3.26 修复前行为）：把 A/B/C 的段首空白**两侧一致地**剥掉——
  // 这正是旧前端「打开和再打开一致地丢」的形态，判据①② 对它结构性失明，判据③ 要接住。
  const 坏A = JSON.parse(JSON.stringify(A))
  for (const leaf of 坏A.leaves) if (leaf.p === '/0/0') leaf.v = 'LEAD-SP'
  坏A.blocks[0].text = 'LEAD-SP'
  坏A.blocks[0].缩进 = 0
  坏A.blocks[0].首字符x = 702
  const 坏B = JSON.parse(JSON.stringify(坏A)), 坏C = JSON.parse(JSON.stringify(坏A))

  const 真测一 = leafDiffs(A, B), 真测一块 = blockDiffs(A, B)
  const 真测二 = leafDiffs(B, C), 真测二块 = blockDiffs(B, C)
  const 真测三 = invariants(样本, B, 样本.html)
  const 坏一 = leafDiffs(坏A, 坏B), 坏一块 = blockDiffs(坏A, 坏B)
  const 坏二 = leafDiffs(坏B, 坏C), 坏二块 = blockDiffs(坏B, 坏C)
  const 坏三 = invariants(样本, 坏B, 样本.html)

  console.log('反例自检（**合成**「当前真测 + 被改坏副本」，非真跑存档）：构造 = §3.26 修复前行为（段首空白两侧一致地被吃）')
  console.log('  历史真跑存档 r28_roundtrip_round26-before.json 已随 target/probe 清库丢失；'
    + '历史结论（修复前 bundle：判据①② 0 处差异、判据③ 报 4 处不成立、整闸 exit 1）定格不重写。')
  console.log('  真测：判据① 叶子差异 ' + 真测一.length + '/块差异 ' + 真测一块.length
    + ' · 判据② 差异 ' + (真测二.length + 真测二块.length)
    + ' · 判据③ 失败 ' + 真测三.length + '（期望全 0：不误报）')
  console.log('  坏副本：判据① 叶子差异 ' + 坏一.length + '/块差异 ' + 坏一块.length
    + ' · 判据② 差异 ' + (坏二.length + 坏二块.length)
    + '（两侧一致地丢 → 按声明仍是 0：甲类盲区）· 判据③ 失败 ' + 坏三.length
    + (坏三.length ? '（' + 坏三.map((f) => f.项 + '：' + f.说明).join('；') + '）' : ''))
  const 不误报 = 真测一.length === 0 && 真测一块.length === 0 && 真测二.length === 0 && 真测二块.length === 0
    && 真测三.length === 0
  const 盲区如声明 = 坏一.length === 0 && 坏一块.length === 0 && 坏二.length === 0 && 坏二块.length === 0
  const 抓住 = 坏三.length > 0
  console.log('  → 真测不误报: ' + (不误报 ? '是 ✅' : '否 ❌'))
  console.log('  → 坏副本上判据①② 仍判 PASS（甲类盲区，与声明一致）: ' + (盲区如声明 ? '是 ✅' : '否 ❌'))
  console.log('  → 判据③抓住坏副本: ' + (抓住 ? '是 ✅' : '否 ❌（闸写松了）'))
  const 全对 = 不误报 && 盲区如声明 && 抓住
  console.log(全对 ? '→ 合成自检通过：判据①②的盲区与声明一致、判据③抓得住坏副本、真测不误报；历史结论定格不重写。'
    : '→ 合成自检**不通过**：本支的判定与上面任一条不符，必须查。')
  process.exit(全对 ? 0 : 1)
}

const artifact = (name) => {
  const file = resolve(OUT, 'r16', name + '.html')
  if (!existsSync(file)) throw new Error('缺产物样本：' + file)
  return readFileSync(file, 'utf8')
}

/**
 * 六条样本。`判据③` 只写「入口声明要保住的量」，写法统一成几种可判定的谓词：
 *   期望缩进  —— 该块往返后的首字符缩进（首字符 x − 内容盒左边界）至少多少 px
 *   列宽一致  —— 往返后 DOM 上每一列的宽度声明 = 入口 HTML 上的声明（规则见 colwidth-rules.mjs）
 *   元素盒    —— 选择器选中的元素里，满足谓词的有几个（至少几个）
 */
const SAMPLES = [
  {
    id: 'plain',
    说明: '普通正文（行内标签 + 标题 + 列表）',
    html: '<p>第一段普通正文，带一个 <strong>行内标签</strong>与<em>斜体</em>。</p>'
      + '<p>第二段普通正文。</p>'
      + '<h2>小标题</h2>'
      + '<ul><li><p>列表项一</p></li><li><p>列表项二</p></li></ul>',
    至少顶层块: 4,
  },
  {
    id: 'lead-space',
    说明: '段首 2 个半角空格',
    html: '<p>  LEAD-SP</p><p>PLAIN</p>',
    期望缩进: { 锚点: 'LEAD-SP', 至少: 5 },
  },
  {
    id: 'lead-tab',
    说明: '段首 1 个制表符（等宽展开后约 27px；压成 1 个 &nbsp; 只有 3.38px）',
    html: '<p>\tLEAD-TAB</p><p>PLAIN</p>',
    // 阈值 20 是三档之间的分界：丢了 = 0；压成 1 个 &nbsp; = 3.38；2 个半角空格 = 6.72；等宽展开 ≈ 27。
    期望缩进: { 锚点: 'LEAD-TAB', 至少: 20 },
  },
  {
    id: 'table',
    说明: '渲染服务产物 r16-06-title-da01（窄列带列宽声明）',
    html: artifact('r16-06-title-da01'),
    列宽一致: true,
  },
  {
    id: 'checklist',
    说明: '渲染服务产物 r16-08-checklist（行首方框 20×20）',
    html: artifact('r16-08-checklist'),
    元素盒: [{
      说明: '行首方框',
      选择器: 'span',
      谓词: (box) => box.w >= 18 && box.w <= 22 && box.h >= 18 && box.h <= 22,
      至少: 6,
    }],
  },
  {
    id: 'quote-card',
    说明: '渲染服务产物 r16-04-quote-card（卡片高 + <p style="margin: 0px;"> 那一类）',
    html: artifact('r16-04-quote-card'),
    至少零间距段落: 1,
    元素盒: [{
      说明: '金句卡本体（改前是 145px：多出 17px 段落间距）',
      选择器: 'section',
      谓词: (box) => box.h >= 100 && box.h <= 140,
      至少: 1,
    }],
  },
]

const DOM_DIR = resolve(BROWSER_OUT, `r28_dom_${LABEL}`)
mkdirSync(DOM_DIR, { recursive: true })
const RESULT_FILE = resolve(BROWSER_OUT, `r28_roundtrip_${LABEL}.json`)

const GUARD = `(() => {
  window.__writeGuard = { blocked: [], lastSave: null };
  const mutating = (method, url) => {
    const verb = String(method || 'GET').toUpperCase();
    return verb !== 'GET' && verb !== 'HEAD' && /\\/api\\//.test(String(url || ''));
  };
  const json = (data) => new Response(JSON.stringify({ success: true, data }),
    { status: 200, headers: { 'Content-Type': 'application/json' } });
  const rawFetch = window.fetch;
  const ARTICLE_PATH = /^\\/api\\/articles\\/${ARTICLE_ID}(\\?|$)/;
  window.fetch = function (input, init) {
    let url = '', verb = 'GET';
    try {
      url = typeof input === 'string' ? input : (input && input.url) || '';
      verb = String((init && init.method) || (input && input.method) || 'GET').toUpperCase();
    } catch (error) { /* 放行 */ }
    if (mutating(verb, url)) {
      let body = {};
      try { body = init && init.body ? JSON.parse(init.body) : {}; } catch (error) { body = {}; }
      window.__writeGuard.lastSave = body.contentHtml || null;
      window.__writeGuard.blocked.push(verb + ' ' + url);
      return Promise.resolve(json(Object.assign({}, body, { updatedAt: new Date().toISOString() })));
    }
    const injected = sessionStorage.getItem('__r28_case');
    if (injected && ARTICLE_PATH.test(String(url))) {
      const stored = JSON.parse(sessionStorage.getItem('__r28_base') || '{}');
      return Promise.resolve(json(Object.assign({}, stored, { contentHtml: injected })));
    }
    return rawFetch.apply(this, arguments);
  };
  return 'installed';
})()`

/**
 * 一次性把这一屏该量的都量回去：
 *   - `leaves`：整棵树的叶子（元素 = 标签 + 逐属性原文；文本 = 节点原文），**唯一的规范化**是
 *     `style` 值里声明按字典序排序（理由见文件头）；
 *   - `blocks`：顶层块的几何 + **首字符 x**（段首空白有没有占位，看这个）；
 *   - `cols`：`<col>` 的逐条属性原文（判据③「列宽一致」用同一套规则再解析）；
 *   - `boxes`：各选择器选中的元素的盒（判据③「元素盒」用）。
 */
const snapshotOf = (选择器表) => `(() => {
  const dom = document.querySelector('.ProseMirror');
  const round = (value) => Math.round(value * 100) / 100;
  const canonical = (value) => value.split(';').map((part) => part.trim()).filter(Boolean).sort().join(';');
  const style = getComputedStyle(dom);
  const rect = dom.getBoundingClientRect();
  const left = round(rect.x + parseFloat(style.paddingLeft));
  const top = rect.y;
  const firstCharX = (el) => {
    const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
    let node;
    while ((node = walker.nextNode())) {
      const text = node.nodeValue || '';
      const at = text.search(/\\S/);
      if (at < 0) continue;
      const range = document.createRange();
      range.setStart(node, at); range.setEnd(node, at + 1);
      const r = range.getBoundingClientRect();
      return (r.width || r.height) ? round(r.x) : null;
    }
    return null;
  };
  const leaves = [];
  const walk = (node, path) => {
    let index = 0;
    for (const child of node.childNodes) {
      const at = path + '/' + index;
      index += 1;
      if (child.nodeType === 1) {
        const attrs = [...child.attributes].map((a) => a.name + '="'
          + (a.name === 'style' ? canonical(a.value) : a.value) + '"').sort().join(' ');
        leaves.push({ p: at, k: 'e', tag: child.tagName.toLowerCase(), attrs });
        walk(child, at);
      } else if (child.nodeType === 3) {
        leaves.push({ p: at, k: 't', v: child.nodeValue });
      }
    }
  };
  walk(dom, '');
  const blocks = [...dom.children].map((el) => {
    const r = el.getBoundingClientRect();
    const x = firstCharX(el);
    return {
      tag: el.tagName.toLowerCase(),
      text: (el.textContent || '').slice(0, 40),
      dy: round(r.y - top),
      h: round(r.height),
      首字符x: x,
      缩进: x !== null ? round(x - left) : null,
    };
  });
  const boxes = {};
  for (const selector of ${JSON.stringify(选择器表)}) {
    boxes[selector] = [...dom.querySelectorAll(selector)].slice(0, 200).map((el) => {
      const r = el.getBoundingClientRect();
      return { w: round(r.width), h: round(r.height) };
    });
  }
  return JSON.stringify({
    left, top, whiteSpace: style.whiteSpace,
    html: dom.innerHTML,
    leaves, blocks, boxes,
    cols: [...dom.querySelectorAll('col')].map((el) => el.outerHTML),
    段落margin零: [...dom.querySelectorAll('p')].filter((el) => {
      const s = getComputedStyle(el);
      return s.marginTop === '0px' && s.marginBottom === '0px' && (el.textContent || '').trim().length > 0;
    }).length,
  });
})()`

/**
 * 把光标放到**末段末尾**。A/B/C 三次快照前都做同一件事，
 * 免得「保存时在末尾、重开后回到开头」这种取景差混进判据（第二十五轮踩过）。
 */
const NEUTRALIZE = `(() => {
  const dom = document.querySelector('.ProseMirror');
  const paragraphs = [...dom.querySelectorAll('p')].filter((el) => (el.textContent || '').trim().length > 0);
  const last = paragraphs[paragraphs.length - 1];
  if (!last) return 'no-p';
  dom.focus();
  const range = document.createRange();
  range.selectNodeContents(last); range.collapse(false);
  const selection = window.getSelection(); selection.removeAllRanges(); selection.addRange(range);
  return 'ok';
})()`

if (!login.success) throw new Error('登录失败：' + JSON.stringify(login))
const token = await login()
const readArticle = async () => (await (await fetch(`${API}/api/articles/${ARTICLE_ID}`,
  { headers: { Authorization: `Bearer ${token}` } })).json()).data
const base = await readArticle()
const dbBefore = { revision: base.revision, updatedAt: base.updatedAt }

const { client, version, close } = await launchBrowser({ port: PORT })
console.log('浏览器:', version.Browser, '· 目标:', API, '· 前端:', BUNDLE || '(应用自带的 target/classes/static)')
const page = await openPage(client)
await page.send('Runtime.enable')
await page.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
await page.send('Page.addScriptToEvaluateOnNewDocument', { source: GUARD })
const blockedAtNetwork = []
client.listeners.set('Fetch.requestPaused', [(message) => {
  const { requestId, request } = message.params
  const verb = String(request.method || 'GET').toUpperCase()
  if (verb !== 'GET' && verb !== 'HEAD') {
    blockedAtNetwork.push(verb + ' ' + request.url)
    client.send('Fetch.failRequest', { requestId, errorReason: 'Aborted' }, page.sessionId).catch(() => {})
    return
  }
  if (BUNDLE && String(request.url).startsWith(API)
    && !/^\/(api|uploads)\//.test(new URL(request.url).pathname)) {
    const path = new URL(request.url).pathname
    const asset = path.startsWith('/assets/') || /\.[a-z0-9]+$/i.test(path)
    const file = resolve(BUNDLE, asset ? path.slice(1) : 'index.html')
    if (file.startsWith(BUNDLE) && existsSync(file)) {
      const type = file.endsWith('.html') ? 'text/html'
        : file.endsWith('.js') ? 'text/javascript'
        : file.endsWith('.css') ? 'text/css'
        : file.endsWith('.svg') ? 'image/svg+xml'
        : file.endsWith('.png') ? 'image/png' : 'application/octet-stream'
      client.send('Fetch.fulfillRequest', {
        requestId, responseCode: 200,
        responseHeaders: [{ name: 'Content-Type', value: type + '; charset=utf-8' }],
        body: Buffer.from(readFileSync(file)).toString('base64'),
      }, page.sessionId).catch(() => {})
      return
    }
  }
  client.send('Fetch.continueRequest', { requestId }, page.sessionId).catch(() => {})
}])
await page.send('Fetch.enable', { patterns: [{ urlPattern: '*://*/*', requestStage: 'Request' }] })

await page.navigate(API + '/login')
await page.evaluate(`localStorage.setItem('wechat_bot_token', ${JSON.stringify(token)});`
  + `sessionStorage.setItem('__r28_base', ${JSON.stringify(JSON.stringify(base))}); true`)

/** 打开一次文章（应用自己 GET → `setContent`），这正是用户「打开文章」的路径。 */
const openArticle = async (html) => {
  await page.evaluate(`sessionStorage.setItem('__r28_case', ${JSON.stringify(html)}); true`)
  await page.navigate(`${API}/articles/${ARTICLE_ID}`)
  let loaded = false
  for (let attempt = 0; attempt < 80; attempt += 1) {
    if (await page.evaluate(`!!document.querySelector('.ProseMirror')`) === true) { loaded = true; break }
    await sleep(250)
  }
  await page.evaluate(`document.fonts.ready`)
  await sleep(400)
  return loaded
}
const snapshot = async (选择器表) => {
  await page.evaluate(NEUTRALIZE)
  await sleep(150)
  return JSON.parse(await page.evaluate(snapshotOf(选择器表)))
}
/** 净零编辑（末段末尾敲一个字符再退格）逼出一次自动保存，把保存出口取回来。 */
const saveAndCapture = async () => {
  await page.evaluate(`window.__writeGuard.lastSave = null; true`)
  await page.evaluate(NEUTRALIZE)
  await page.send('Input.insertText', { text: 'x' })
  await sleep(150)
  for (const type of ['keyDown', 'keyUp']) {
    await page.send('Input.dispatchKeyEvent', {
      type, key: 'Backspace', code: 'Backspace', windowsVirtualKeyCode: 8, nativeVirtualKeyCode: 8,
    })
  }
  await sleep(2600)
  return JSON.parse(await page.evaluate(`JSON.stringify(window.__writeGuard.lastSave || null)`))
}

const records = []
const failures = []
for (const sample of SAMPLES) {
  console.log('\n===== ' + sample.id + ' · ' + sample.说明 + ' =====')
  const 选择器表 = (sample.元素盒 || []).map((rule) => rule.选择器)
  const opened = await openArticle(sample.html)
  if (!opened) { failures.push({ id: sample.id, 项: '文章打不开（编辑器没出现）' }); console.log('  ❌ 编辑器没出现'); continue }
  const A = await snapshot(选择器表)
  writeFileSync(join(DOM_DIR, sample.id + '.A-保存时.html'), A.html, 'utf8')
  console.log('  A 保存时：块 ' + A.blocks.length + ' · 内宽 ' + A.left + ' · white-space ' + A.whiteSpace)

  const exit1 = await saveAndCapture()
  if (!exit1) { failures.push({ id: sample.id, 项: '拿不到第一次保存出口' }); console.log('  ❌ 没拿到保存出口'); continue }

  await openArticle(exit1)
  const B = await snapshot(选择器表)
  writeFileSync(join(DOM_DIR, sample.id + '.B-第一次往返.html'), B.html, 'utf8')
  const exit2 = await saveAndCapture()
  if (!exit2) { failures.push({ id: sample.id, 项: '拿不到第二次保存出口' }); console.log('  ❌ 第二次保存出口没拿到'); continue }
  // 两份出口都落盘：字节不同时不必重跑就能 diff，也不必猜「大概是声明顺序」。
  writeFileSync(join(DOM_DIR, sample.id + '.出口1.html'), exit1, 'utf8')
  writeFileSync(join(DOM_DIR, sample.id + '.出口2.html'), exit2, 'utf8')

  await openArticle(exit2)
  const C = await snapshot(选择器表)
  writeFileSync(join(DOM_DIR, sample.id + '.C-第二次往返.html'), C.html, 'utf8')

  const diffsAB = leafDiffs(A, B)
  const diffsBC = leafDiffs(B, C)
  const blockAB = blockDiffs(A, B)
  const blockBC = blockDiffs(B, C)
  const invB = invariants(sample, B, sample.html)
  const invC = invariants(sample, C, sample.html)

  const A_ = (snap) => '叶子 ' + snap.leaves.length + ' · 块 ' + snap.blocks.length + ' · DOM ' + snap.html.length + ' 字符'
  console.log('  A 保存时   ' + A_(A) + ' · 指纹 ' + digest(A.html))
  console.log('  B 第一次往返 ' + A_(B) + ' · 指纹 ' + digest(B.html)
    + (digest(A.html) === digest(B.html) ? '' : '（原始字节不同：含 style 声明顺序，**不参与判定**，判定看逐叶子值）'))
  console.log('  C 第二次往返 ' + A_(C) + ' · 指纹 ' + digest(C.html)
    + (digest(B.html) === digest(C.html) ? '' : '（原始字节不同：同上）'))
  console.log('  出口1 ' + exit1.length + ' 字符 / 出口2 ' + exit2.length + ' 字符'
    + (exit1 === exit2 ? ' · 两次出口逐字节相同'
      : ' · 两次出口字节不同，重排 style 声明后：'
        + (canonHtml(exit1) === canonHtml(exit2) ? '相同（差异只是声明书写顺序，CSS 语义等价）' : '❌ 仍然不同（实质差异）')))
  console.log('  判据① 往返前后逐叶子值相同：' + (diffsAB.length ? '❌ ' + diffsAB.length + ' 处不同' : '✅ ' + A.leaves.length + ' 个叶子逐项相同'))
  for (const diff of diffsAB.slice(0, 6)) console.log('       ' + diff.路径 + '：' + JSON.stringify(diff.保存时) + ' → ' + JSON.stringify(diff.再打开后))
  if (diffsAB.length > 6) console.log('       …其余 ' + (diffsAB.length - 6) + ' 处见结果 JSON')
  console.log('  判据①附 顶层块几何（dy/h/首字符 x）：' + (blockAB.length ? '❌ ' + blockAB.length + ' 块不同' : '✅ 逐块相同'))
  for (const diff of blockAB.slice(0, 6)) console.log('       第 ' + diff.第几块 + ' 块：' + JSON.stringify(diff.保存时) + ' → ' + JSON.stringify(diff.再打开后))
  console.log('  判据② 二次往返仍稳定：' + (diffsBC.length ? '❌ ' + diffsBC.length + ' 处不同' : '✅ 逐叶子值相同')
    + (blockBC.length ? ' · 顶层块 ' + blockBC.length + ' 块不同 ❌' : ' · 顶层块逐块相同 ✅'))
  for (const diff of diffsBC.slice(0, 4)) console.log('       ' + diff.路径 + '：' + JSON.stringify(diff.保存时) + ' → ' + JSON.stringify(diff.再打开后))
  console.log('  判据③ 入口保真：' + (invB.length === 0 && invC.length === 0 ? '✅' : '❌ ' + JSON.stringify([...invB, ...invC])))
  if (sample.期望缩进) {
    const 找 = (snap) => { const hit = snap.blocks.find((b) => b.text.includes(sample.期望缩进.锚点)); return hit ? hit.缩进 : null }
    console.log('       段首缩进 A/B/C = ' + 找(A) + ' / ' + 找(B) + ' / ' + 找(C)
      + ' px（要求 ≥ ' + sample.期望缩进.至少 + '）')
  }
  if (sample.列宽一致) console.log('       列宽声明 入口 ' + JSON.stringify(widthsOf(sample.html))
    + ' → 往返后 ' + JSON.stringify(B.cols.map(declaredWidth)) + '（' + B.cols.length + ' 个 <col>）')
  if (sample.至少零间距段落) console.log('       上下 margin 都是 0 的有文字段落 A/B/C = '
    + A.段落margin零 + ' / ' + B.段落margin零 + ' / ' + C.段落margin零)

  if (diffsAB.length || blockAB.length) failures.push({ id: sample.id, 项: '判据① 往返前后不同', 叶子差异: diffsAB.length, 块差异: blockAB.length })
  if (diffsBC.length || blockBC.length) failures.push({ id: sample.id, 项: '判据② 二次往返不稳定', 叶子差异: diffsBC.length, 块差异: blockBC.length })
  for (const fail of [...invB, ...invC]) failures.push({ id: sample.id, 项: '判据③ ' + fail.项, 说明: fail.说明 })

  records.push({
    id: sample.id, 说明: sample.说明, 入口字符数: sample.html.length,
    A: { 叶子: A.leaves.length, 块: A.blocks.length, 字符: A.html.length, 指纹: digest(A.html), blocks: A.blocks },
    B: { 叶子: B.leaves.length, 块: B.blocks.length, 字符: B.html.length, 指纹: digest(B.html), blocks: B.blocks },
    C: { 叶子: C.leaves.length, 块: C.blocks.length, 字符: C.html.length, 指纹: digest(C.html), blocks: C.blocks },
    出口1字符: exit1.length, 出口2字符: exit2.length, 两次出口逐字节相同: exit1 === exit2,
    两次出口重排style后相同: canonHtml(exit1) === canonHtml(exit2),
    判据一_叶子差异: diffsAB, 判据一_块差异: blockAB, 判据二_叶子差异: diffsBC, 判据二_块差异: blockBC,
    判据三_不成立: [...invB.map((f) => ({ ...f, 哪一圈: 'B' })), ...invC.map((f) => ({ ...f, 哪一圈: 'C' }))],
  })
}

// ---------- 闸的自检：拿第二十六轮之前的**存档实测**套用同一套判据③，必须判 FAIL ----------
const ARCHIVE = resolve(BROWSER_OUT, 'r27_entry_paths_before.json')
let selfTest = null
if (existsSync(ARCHIVE)) {
  const archived = JSON.parse(readFileSync(ARCHIVE, 'utf8'))
  const blocks = archived?.results?.setcontent?.entered?.blocks || []
  /** 存档里那两段的「相对缩进」就是判据③要看的量（0px = 空白已经没了）。 */
  const 存档缩进 = {}
  for (const sample of SAMPLES.filter((row) => row.期望缩进 && row.期望缩进.至少 > 0)) {
    const hit = blocks.find((block) => String(block.text).includes(sample.期望缩进.锚点))
    存档缩进[sample.id] = hit ? hit.缩进 : null
  }
  const judged = Object.entries(存档缩进).some(([id, value]) => {
    const sample = SAMPLES.find((row) => row.id === id)
    return value === null || value < sample.期望缩进.至少
  })
  selfTest = { 存档: ARCHIVE, 存档缩进, 判为FAIL: judged }
  console.log('\n闸的自检①（离线：拿第二十六轮之前的存档实测套同一套判据③）')
  console.log('  存档里那两段的段首缩进：' + JSON.stringify(存档缩进))
  console.log('  → ' + (judged ? '判 FAIL ✅（判据③抓得住「打开就把段首空白吃掉」这个 bug）' : '判 PASS ❌（闸写松了）'))
  if (!judged) failures.push({ id: 'self-test', 项: '第二十六轮之前的存档被判成了 PASS' })
} else {
  console.log('\n闸的自检①：存档 ' + ARCHIVE + ' 不在，跳过（不影响退出码）')
}

const dbAfter = await readArticle()
const unchanged = dbAfter.revision === dbBefore.revision && dbAfter.updatedAt === dbBefore.updatedAt
console.log('\n库核对：改前 revision=' + dbBefore.revision + ' / 改后 revision=' + dbAfter.revision
  + ' · updatedAt ' + (unchanged ? '逐字未变 ✅' : '变了 ❌'))
console.log('网络层另行拦下的写请求：' + blockedAtNetwork.length + ' 次')

writeFileSync(RESULT_FILE, JSON.stringify({
  browser: version.Browser, origin: API, label: LABEL, bundle: BUNDLE || null,
  samples: records, selfTest, failures,
  networkBlocked: blockedAtNetwork, dbBefore, dbAfter, dbUnchanged: unchanged,
}, null, 1), 'utf8')
console.log('结果:', RESULT_FILE)

await page.close()
close()
if (failures.length) {
  console.error('\n❌ 往返闸未过：' + failures.length + ' 处')
  for (const fail of failures) console.error('   · ' + JSON.stringify(fail))
  process.exit(1)
}
if (!unchanged) { console.error('\n❌ 库里的 revision/updatedAt 变了'); process.exit(1) }
console.log('\n✅ 往返闸通过：六条样本「打开 → 保存 → 再打开」逐叶子值相同、二次往返仍稳定、入口声明的量都还在')
