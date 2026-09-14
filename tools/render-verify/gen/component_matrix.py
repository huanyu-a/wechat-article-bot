"""MarkFlow 组件全量清单 + 每个组件一个最小样例（第五轮：两条渲染路径对齐的基线）。

清单来源（不是印象，三条都可查）：
 1. 渲染引擎前端包 `spec/engine/mf_app.js`（官网 /markflow/ 的线上 bundle 存档）里的**组件注册表**——
    形如 `{Title_DA01:"title",PTitle_DA01:"title",...,"reading-path":"structure",breaking:"emph",...}`，
    共 **63 个组件 id**，按 category 分组。提取脚本见本文件 `--dump-registry`（结果 `component_registry.json`）。
 2. 同一 bundle 里的**语法匹配器**（`{name:"xxx",match:e=>/^<xxx\\b/.test(e)}`）——决定「写成什么才会被识别」。
    结果 `spec/component_matchers.json`。
 3. 渲染服务 `GET https://www.bx9y.com.cn/__markflow_render`（无 body）返回的 `guide`
    （`spec/guide_recheck.md`，13744 字节 / 7672 字符）——它是**给模型看的官方语法指令**，
    只列了 11 个块级标签 + 若干容器，另有一节数学公式。

本文件给出的是「可写进 Markdown 的语法」清单：注册表里的 63 个 id 中，
`layout-*` / `gov-header` / `hint` 等一批并不在 guide 里，需要单独验证它们到底吃不吃
——见 `SAMPLES` 里 category='?registry-only' 的几条，以及 `--dump-registry` 的输出。

用法（在仓库根目录下执行）：
    python tools/render-verify/gen/component_matrix.py            # 全部渲染一遍，落盘 target/probe/components/
    python tools/render-verify/gen/component_matrix.py --only math-inline,slider
    python tools/render-verify/gen/component_matrix.py --dump-registry
"""
import json
import os
import re
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from paths import OUT, SPEC, TOKEN_FILE, RENDER_URL  # noqa: E402

TOKEN = open(TOKEN_FILE).read().strip()
URL = RENDER_URL
OUTDIR = os.path.join(OUT, 'components')

# probe token：每个样例里埋一个唯一 ASCII 串，用来判断「内容有没有进产物」
# （终端是 GBK，中文比对结果不可读，所以用 ASCII 判据 + 落 UTF-8 文件）。
#
# 字段：id, category, 语法形态, markdown, 必须出现在产物里的标记
SAMPLES = [
    # ---------- 一、标准 Markdown ----------
    ('md-heading', 'standard', 'markdown', '## ZQHEAD2 ZQ01\n\n正文段落。\n', ['ZQHEAD2']),
    ('md-list', 'standard', 'markdown', '- ZQITEM1\n- ZQITEM2\n', ['ZQITEM1', 'ZQITEM2']),
    ('md-ordered', 'standard', 'markdown', '1. ZQORD1\n2. ZQORD2\n', ['ZQORD1']),
    ('md-task-list', 'standard', 'markdown', '- [x] ZQDONE\n- [ ] ZQTODO\n', ['ZQDONE']),
    ('md-quote', 'standard', 'markdown', '> ZQQUOTE 引用正文\n', ['ZQQUOTE']),
    ('md-table', 'standard', 'markdown', '| 列甲 | 列乙 |\n| --- | --- |\n| ZQCELL1 | ZQCELL2 |\n\n空行后的正文。\n', ['ZQCELL1']),
    ('md-hr', 'standard', 'markdown', '上文。\n\n---\n\n下文。\n', []),
    ('md-code', 'standard', 'markdown', '```js\nconst zqcode = 1;\n```\n', ['zqcode']),
    ('md-image', 'standard', 'markdown', '![ZQIMGALT](https://robocopmao.github.io/r-markdown/banner4.webp)\n', ['ZQIMGALT']),
    ('md-image-sized', 'standard', 'markdown', '![ZQIMGSZ](https://robocopmao.github.io/r-markdown/banner4.webp)[100% 250px]\n', ['ZQIMGSZ']),
    ('md-link', 'standard', 'markdown', '[ZQLINK](https://example.com)\n', ['ZQLINK']),
    ('md-multi-image', 'standard', 'markdown', '< ![ZQGAL1](https://robocopmao.github.io/r-markdown/banner4.webp), ![ZQGAL2](https://robocopmao.github.io/r-markdown/banner4.webp) >\n', ['ZQGAL1', 'ZQGAL2']),

    # ---------- 二、行内语法 ----------
    ('in-em-hl', 'inline', '==x==', '正文里的 ==ZQHL== 强调。\n', ['ZQHL']),
    ('in-pill', 'inline', '!!x!!', '正文里的 !!ZQPILL!! 胶囊。\n', ['ZQPILL']),
    ('in-indigo', 'inline', '^^x^^', '正文里的 ^^ZQIND^^ 加重。\n', ['ZQIND']),
    ('in-glow', 'inline', '::x::', '正文里的 ::ZQGLOW:: 柔光。\n', ['ZQGLOW']),
    ('in-bold', 'inline', '**x**', '正文里的 **ZQBOLD** 粗体。\n', ['ZQBOLD']),
    ('in-italic', 'inline', '*x*', '正文里的 *ZQITAL* 斜体。\n', ['ZQITAL']),
    ('in-bold-italic', 'inline', '***x***', '正文里的 ***ZQBI*** 粗斜。\n', ['ZQBI']),
    ('in-underline-accent', 'inline', '__x__', '正文里的 __ZQUNDA__ 主题色下划线。\n', ['ZQUNDA']),
    ('in-underline-html', 'inline', '<u>x</u>', '正文里的 <u>ZQUNDU</u> 普通下划线。\n', ['ZQUNDU']),
    ('in-strike', 'inline', '~~x~~', '正文里的 ~~ZQSTRIKE~~ 删除线。\n', ['ZQSTRIKE']),
    ('in-sub', 'inline', '~x~', '分子式 H~2~O，标记 ZQSUB。\n', ['ZQSUB']),
    ('in-sup', 'inline', '^x^', '平方 m^2^，标记 ZQSUP。\n', ['ZQSUP']),
    ('in-code', 'inline', '`x`', '正文里的 `ZQICODE` 行内代码。\n', ['ZQICODE']),
    ('in-badge', 'inline', '<Badge/>', '正文里的 <Badge type="tip" text="ZQBADGE" /> 徽章。\n', ['ZQBADGE']),
    ('in-icon', 'inline', '<Icon/>', '图标 <icon name="material-symbols:star" size="2em" /> 后面 ZQICON。\n', ['ZQICON']),

    # ---------- 三、提示框 ----------
    ('callout-quote-tip', 'callout', '> [TIP]', '> [TIP] ZQCTIP 标题\n> ZQCBODY 提示正文\n', ['ZQCTIP', 'ZQCBODY']),
    ('callout-quote-warning', 'callout', '> [WARNING]', '> [WARNING] ZQCWARN\n> ZQCWBODY\n', ['ZQCWARN']),
    ('callout-container', 'callout', ':::callout', ':::callout type="tip" title="ZQCOUT"\nZQCOUTBODY 正文\n:::\n', ['ZQCOUT', 'ZQCOUTBODY']),
    ('callout-tip-container', 'callout', ':::tip', ':::tip ZQCTIPC\nZQCTIPB 正文\n:::\n', ['ZQCTIPC']),

    # ---------- 四、块级标签 ----------
    ('blk-title', 'block-tag', '<title>', '<title type="DA01" label="ZQLABEL" subtitle="ZQSUB2" chips="甲|乙|丙">ZQTITLE 主标题</title>\n', ['ZQTITLE', 'ZQLABEL']),
    ('blk-p-title', 'block-tag', '<p-title>', '<p-title number="01" title="ZQPTITLE" subtitle="ZQPSUB" level="1" size="normal"></p-title>\n', ['ZQPTITLE', 'ZQPSUB']),
    ('blk-cta', 'block-tag', '<cta>', '<cta label="ZQCTALABEL" title="ZQCTATITLE" action="ZQCTAACTION"></cta>\n', ['ZQCTATITLE']),
    ('blk-badges', 'block-tag', '<badges>', '<badges type="accent">ZQBDG1|ZQBDG2|ZQBDG3</badges>\n', ['ZQBDG1', 'ZQBDG3']),
    ('blk-statement', 'block-tag', '<statement>', '<statement>ZQSTMT 这是一句金句。</statement>\n', ['ZQSTMT']),
    ('blk-lead', 'block-tag', '<lead>', '<lead>ZQLEAD 引导文字正文。</lead>\n', ['ZQLEAD']),
    ('blk-engage-label', 'block-tag', '<engage-label>', '<engage-label title="ZQENGLABEL" label="ZQENGTAG"></engage-label>\n', ['ZQENGLABEL']),
    ('blk-engage-card', 'block-tag', '<engage-card>', '<engage-card title="ZQENGCARD" subtitle="ZQENGSUB"></engage-card>\n', ['ZQENGCARD']),
    ('blk-engage-tag', 'block-tag', '<engage>', '<engage type="DA02">ZQENGTAIL</engage>\n', ['ZQENGTAIL']),
    ('blk-img', 'block-tag', '<img>', '<img src="https://robocopmao.github.io/r-markdown/banner4.webp" alt="ZQIMGTAG" width="100%" height="auto" radius="8px" fit="cover" align="center" />\n', ['ZQIMGTAG']),
    ('blk-steps-2', 'block-tag', '<steps>', '<steps label="ZQSTEPSL" title="ZQSTEPST">\n- ZQSTEP1 | 第一步说明\n- ZQSTEP2 | 第二步说明\n</steps>\n', ['ZQSTEP1', 'ZQSTEP2']),
    ('blk-steps-5', 'block-tag', '<steps> 5步', '<steps label="ZQSTEPSL" title="ZQSTEPST">\n- ZQST1 | 说明一\n- ZQST2 | 说明二\n- ZQST3 | 说明三\n- ZQST4 | 说明四\n- ZQST5 | 说明五\n</steps>\n', ['ZQST5']),
    ('blk-timeline', 'block-tag', '<timeline>', '<timeline>\nZQTL1 | 事件甲 | 说明甲\nZQTL2 | 事件乙 | 说明乙\n</timeline>\n', ['ZQTL1', 'ZQTL2']),
    # 标签式 slider **必须带闭合标签**：同一个 open tag，写了 `</slider>` 出 1141 字符 SVG 轮播，
    # 写成自闭合 `<slider … />` 则字面透传（356 字符、无 svg）。判别实验见 r4_slider_discriminator.py。
    ('blk-slider', 'block-tag', '<slider>', '<slider images="https://robocopmao.github.io/r-markdown/banner4.webp,https://robocopmao.github.io/r-markdown/banner4.webp" interval="3" width="600" height="200" type="1">\n</slider>\n', []),
    ('blk-slider-selfclose', 'block-tag', '<slider/>（自闭合）', '<slider images="https://robocopmao.github.io/r-markdown/banner4.webp,https://robocopmao.github.io/r-markdown/banner4.webp" interval="3" width="600" height="200" type="1" />\n', []),
    # 行格式用 bundle spec 的官方写法 `- [标签] 标题`；第六轮复核发现第五轮那版
    # `[ZQCF1] 标题 | 描述` 是自造的，渲染 API 对不匹配的行格式**整块归零**（见 badline 样例）。
    ('blk-case-flow', 'block-tag', '<case-flow>', '<case-flow label="ZQCFL">\n- [案例 01] ZQCF1 案例标题甲\n- [案例 02] ZQCF2 案例标题乙\n</case-flow>\n', ['ZQCF1']),
    ('blk-case-flow-badline', 'block-tag', '<case-flow>（行格式不合法）', '<case-flow label="ZQCFL">\n[ZQCF1] 案例标题甲 | 案例描述甲\n[ZQCF2] 案例标题乙 | 案例描述乙\n</case-flow>\n', ['ZQCF1']),
    # 属性名取 bundle spec 的官方字段（issuer 必填 / doc-no / classification / urgency / signer）；
    # 第五轮那版用的 `title`/`subtitle` 不是该组件的字段，产物只剩那根 4px 红条属预期行为。
    ('blk-gov-header', 'block-tag', '<gov-header>', '<gov-header issuer="ZQGOVT机关" doc-no="ZQGOVS号" classification="绝密" urgency="特急" signer="张三"></gov-header>\n', ['ZQGOVT']),

    # ---------- 五、容器式 ----------
    ('ctn-breaking', 'container', ':::breaking', ':::breaking badge="ZQBADGE2" title="ZQBREAK" subtitle="ZQBREAKSUB" chips="甲|乙"\nZQBREAKBODY 正文一句。\n:::\n', ['ZQBREAK', 'ZQBREAKBODY']),
    ('ctn-reading-path', 'container', ':::reading-path', ':::reading-path\n- ZQRP1 | 第一节说明\n- ZQRP2 | 第二节说明\n:::\n', ['ZQRP1', 'ZQRP2']),
    ('ctn-steps-h', 'container', ':::steps-horizontal', ':::steps-horizontal label="ZQSHL" title="ZQSHT" hint="ZQSHH" active="2"\n- ZQSH1 | 说明一\n- ZQSH2 | 说明二\n:::\n', ['ZQSH1', 'ZQSH2']),
    ('ctn-steps-v', 'container', ':::steps-vertical', ':::steps-vertical label="ZQSVL" title="ZQSVT"\n- ZQSV1 | 说明一\n- ZQSV2 | 说明二\n- ZQSV3 | 说明三\n:::\n', ['ZQSV1']),
    ('ctn-case-flow', 'container', ':::case-flow', ':::case-flow\n- [ZQCFC1] 案例甲 | 描述甲\n- [ZQCFC2] 案例乙 | 描述乙\n:::\n', ['ZQCFC1']),
    ('ctn-timeline', 'container', ':::timeline', ':::timeline\n- ZQTC1 | 标题甲 | 说明甲\n- ZQTC2 | 标题乙 | 说明乙\n:::\n', ['ZQTC1', 'ZQTC2']),
    ('ctn-slider', 'container', ':::slider', ':::slider images="https://robocopmao.github.io/r-markdown/banner4.webp,https://robocopmao.github.io/r-markdown/banner4.webp" interval="3"\n:::\n', []),
    ('ctn-table', 'container', ':::table', ':::table style="card"\n| 列甲 | 列乙 |\n| --- | --- |\n| ZQTB1 | ZQTB2 |\n:::\n', ['ZQTB1']),
    ('ctn-compare', 'container', ':::compare', ':::compare\n维度 | 甲 | 乙 | accent\nZQCP1 | 高 | 低 | default\n:::\n', ['ZQCP1']),
    ('ctn-code-block', 'container', ':::code-block', ':::code-block lang="js" title="ZQCB1"\n```js\nconst zqcbc = 1;\n```\n:::\n', ['zqcbc']),
    ('ctn-align', 'container', ':::align', ':::align align="center"\nZQALIGN 居中文字\n:::\n', ['ZQALIGN']),
    ('ctn-hint', 'container', ':::hint', ':::hint\nZQHINT 提示内容\n:::\n', ['ZQHINT']),
    ('ctn-cta', 'container', ':::cta', ':::cta label="ZQCTL" title="ZQCTT"\nZQCTBODY\n:::\n', ['ZQCTT']),
    ('ctn-lead', 'container', ':::lead', ':::lead\nZQLEADC 引导容器正文\n:::\n', ['ZQLEADC']),
    ('ctn-note', 'container', ':::note', ':::note ZQNOTE 标题\nZQNOTEBODY\n:::\n', ['ZQNOTE']),
    ('ctn-success', 'container', ':::success（guide 说不在支持列表）', ':::success ZQSUCC\n正文\n:::\n', ['ZQSUCC']),
    ('ctn-danger', 'container', ':::danger（guide 说不在支持列表）', ':::danger ZQDANGER\n正文\n:::\n', ['ZQDANGER']),

    # ---------- 六、数学公式 ----------
    ('math-inline', 'math', '$...$', '行内公式 $E=mc^2$ 与 $\\frac{1}{3}$，标记 ZQMATH。\n', ['ZQMATH']),
    ('math-block', 'math', '$$...$$', '$$\n\\int_0^1 x^2 \\,dx = \\frac{1}{3}\n$$\n', []),
    ('math-block-label', 'math', '$$...$$ 带标记', '$$\nZQMBLOCK_LABEL = \\alpha + \\beta\n$$\n', []),

    # ---------- 七、图表 ----------
    ('diagram-mermaid', 'diagram', '```mermaid', '```mermaid\nflowchart LR\n  A[ZQMA] --> B[ZQMB] --> C[ZQMC]\n```\n', ['ZQMA']),

    # ---------- 八、只在注册表里、guide 没写的（验证到底吃不吃） ----------
    ('reg-layout-hero', '?registry-only', ':::layout-hero', ':::layout-hero\nZQLH 内容\n:::\n', ['ZQLH']),
    # 下面两个 ID 只有容器式：官方 guide、bundle 的组件 spec 与语法分支里都**没有**标签式。
    # 保留标签式样例是为了留一条"错误写法"的反证（API 按未知行原样透传），不是上游缺陷。
    ('reg-layout-hero-tag', '?registry-only', '<layout-hero>（官方无此写法）', '<layout-hero>ZQLHT 内容</layout-hero>\n', ['ZQLHT']),
    ('reg-layout-toc', '?registry-only', ':::layout-toc', ':::layout-toc\n- ZQLT1 | 甲\n:::\n', ['ZQLT1']),
    ('reg-layout-metrics', '?registry-only', ':::layout-metrics', ':::layout-metrics\n- ZQLM1 | 甲 | 1\n:::\n', ['ZQLM1']),
    ('reg-hint-tag', '?registry-only', '<hint>（官方无此写法）', '<hint>ZQHINTTAG</hint>\n', ['ZQHINTTAG']),
    ('reg-align-tag', '?registry-only', '<align>', '<align align="center">ZQALIGNTAG</align>\n', ['ZQALIGNTAG']),


    # ---------- 九、D28/D29 相关：属性是否进产物 ----------
    ('attr-table-title', 'attribute', ':::table title=', ':::table style="card" title="ZQTBLTITLE"\n| 甲 | 乙 |\n| --- | --- |\n| ZQTBL1 | ZQTBL2 |\n:::\n', ['ZQTBL1']),
    ('attr-callout-title', 'attribute', ':::callout title=', ':::callout type="tip" title="ZQCALTITLE"\nZQCALBODY\n:::\n', ['ZQCALTITLE']),
    ('attr-compare-marker-cn', 'attribute', ':::compare 中文标记', ':::compare\n维度 | 甲 | 乙 | 强调\nZQCPCN | 高 | 低 | accent\n:::\n', ['ZQCPCN']),
]


def call(url, data=None):
    body = json.dumps(data).encode() if data is not None else None
    headers = {'X-Render-Token': TOKEN}
    if body:
        headers['Content-Type'] = 'application/json'
    request = urllib.request.Request(url, data=body, headers=headers)
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.load(response)


def render(markdown):
    return call(URL, {'markdown': markdown})


def dump_registry():
    """从存档的引擎包里抽注册表。

    写两份：产物侧 `OUT/component_registry.json`（本轮实抽），并与**受版本控制的**
    `SPEC/component_registry.json` 逐字节比对——它是全部结论的「组件全集」定义基准，
    引擎换版本时这里必须先在输出里看得见差异，而不是静默改掉基准。
    """
    src = open(os.path.join(SPEC, 'engine', 'mf_app.js'), encoding='utf-8').read()
    m = re.search(r'\{[^{}]*Engage_DA02:"cta"[^{}]*\}', src)
    if not m:
        raise SystemExit('组件注册表没找到——bundle 换版本了？')
    pairs = []
    for a, b, c, d in re.findall(r'"([^"]+)":\s*"([^"]+)"|([A-Za-z_][A-Za-z0-9_]*):"([^"]+)"', m.group(0)):
        pairs.append((a or c, b or d))
    dumped = json.dumps(pairs, ensure_ascii=False, indent=1)
    os.makedirs(OUT, exist_ok=True)
    open(os.path.join(OUT, 'component_registry.json'), 'w', encoding='utf-8').write(dumped)
    spec_path = os.path.join(SPEC, 'component_registry.json')
    spec = open(spec_path, encoding='utf-8').read() if os.path.exists(spec_path) else None
    print('registry ids =', len(pairs))
    if spec is None:
        print('（spec/component_registry.json 不存在，跳过比对）')
    elif spec.strip() == dumped.strip():
        print('与 spec/component_registry.json 一致')
    else:
        print('!! 与 spec/component_registry.json 不一致——引擎换版本了，先确认基准再更新 spec')
    for name, category in pairs:
        print('  %-24s %s' % (name, category))


def main():
    args = sys.argv[1:]
    if '--dump-registry' in args:
        dump_registry()
        return
    only = None
    if '--only' in args:
        only = set(args[args.index('--only') + 1].split(','))
    os.makedirs(OUTDIR, exist_ok=True)
    rows = []
    for cid, category, syntax, markdown, markers in SAMPLES:
        if only and cid not in only:
            continue
        try:
            result = render(markdown)
            html = result.get('html') or ''
            meta = result.get('meta') or {}
            warnings = meta.get('warnings') or []
            ok = result.get('ok')
        except Exception as exception:  # 网络/上游异常也要留痕，不能静默跳过
            rows.append({'id': cid, 'category': category, 'syntax': syntax, 'error': str(exception)})
            print('%-24s ERROR %s' % (cid, exception))
            continue
        open(os.path.join(OUTDIR, cid + '.html'), 'w', encoding='utf-8').write(html)
        open(os.path.join(OUTDIR, cid + '.md'), 'w', encoding='utf-8').write(markdown)
        missing = [marker for marker in markers if marker not in html]
        rows.append({
            'id': cid, 'category': category, 'syntax': syntax, 'ok': ok,
            'chars': len(html), 'warnings': warnings,
            'markers': markers, 'missingMarkers': missing,
            'svg': html.count('<svg'), 'katex': html.count('katex'),
            'mathTag': html.count('<math'), 'dataBlocks': re.findall(r'data-block="([^"]+)"', html),
        })
        print('%-24s len=%-6d warn=%-2d miss=%s svg=%d katex=%d math=%d' % (
            cid, len(html), len(warnings), ','.join(missing) or '-',
            html.count('<svg'), html.count('katex'), html.count('<math')))
    out = os.path.join(OUT, 'component_matrix.json')
    existing = []
    if os.path.exists(out):
        existing = json.load(open(out, encoding='utf-8'))
    by_id = {row['id']: row for row in existing}
    for row in rows:
        by_id[row['id']] = row
    json.dump(list(by_id.values()), open(out, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print('\nwrote', out, '(+', len(rows), 'rows)')


if __name__ == '__main__':
    main()
