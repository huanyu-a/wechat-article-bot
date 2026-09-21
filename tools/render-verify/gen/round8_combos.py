"""第八轮 · 组合条件覆盖（每个样例此前只喂了一个最小写法）。

第七轮的 79 个样例全是「一个组件、一个最小写法」。真实成稿是嵌套的、重复的、超长的，
所以这一轮把「组合」当自变量再打一遍真实渲染 API，产物落到 `target/probe/combos/`，
再由 `run-combo-browser.mjs` 挂进真浏览器看画没画出来。

只调渲染 API（`POST https://www.bx9y.com.cn/__markflow_render`），**不落库、不改任何生产数据**。

用法：python tools/render-verify/gen/round8_combos.py
产物：target/probe/combos/<id>.md、<id>.html、target/probe/combos/combos.json
"""
import json
import os
import re
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from paths import OUT, TOKEN_FILE, RENDER_URL  # noqa: E402, read_token
# `gen/` 自己没有 __init__.py（本目录的脚本历来是各自独立执行的），
# 而脚本所在目录本来就在 sys.path[0]，所以直接按模块名导入同目录的兄弟文件。
from passthrough import counts as passthrough_counts  # noqa: E402

OUT = os.path.join(OUT, 'combos')
os.makedirs(OUT, exist_ok=True)

TOKEN = read_token()
URL = RENDER_URL
BANNER = 'https://robocopmao.github.io/r-markdown/banner4.webp'

LONG_PARAGRAPH = (
    '这一段是超长正文的压力测试：渲染服务需要在不丢字、不截断的前提下把整段包进带内联样式的段落里，'
    '任何长度上限都会在这里露出来。排版引擎的处理链路是「Markdown 解析 → 模块匹配 → 主题令牌注入 → 内联样式 HTML」，'
    '每一层都可能对长度敏感。这里再塞一些行内组件把水搅浑：**加粗的结论**、==高亮的要点==、'
    '行内代码 `renderMarkdown()`、下标 H~2~O、上标 m^2^，以及一个 $E=mc^2$ 行内公式。'
) * 12

LONG_TABLE_ROWS = '\n'.join(
    f'| 第{i:02d}行 | 指标{i:02d} | {i * 7}% | {"上升" if i % 2 else "持平"} |' for i in range(1, 21))

CASES = [
    # ---------- 一、容器套容器 ----------
    {
        'id': 'cmb-callout-timeline', 'family': '容器套容器', 'minimal': 'ctn-timeline',
        'label': ':::callout 里嵌 :::timeline',
        'expect': '外层 callout 与内层 timeline 都渲染出来；字面 ::: 不残留',
        'markdown': ':::callout title="嵌套容器"\n外层容器里放一条时间线：\n\n'
                    ':::timeline\n- 2024年01月 | 立项 | 完成需求分析\n'
                    '- 2024年06月 | 一期上线 | 核心功能发布\n- 2025年01月 | 二期 | 新增 AI 辅助\n:::\n\n'
                    '时间线到此结束。\n:::\n',
    },
    {
        'id': 'cmb-callout-steps', 'family': '容器套容器', 'minimal': 'blk-steps-2',
        'label': ':::callout 里嵌 <steps>',
        'expect': '外层 callout 与内层三步都渲染出来',
        'markdown': ':::callout title="嵌套步骤"\n下面是三步：\n\n'
                    '<steps>\n<step title="第一步">收集素材与选题</step>\n'
                    '<step title="第二步">结构化排版</step>\n<step title="第三步">发布到公众号</step>\n</steps>\n\n'
                    '步骤到此结束。\n:::\n',
    },
    {
        'id': 'cmb-timeline-table', 'family': '容器套容器', 'minimal': 'ctn-timeline',
        'label': ':::timeline 里嵌 :::table（上游 R2-附 记过的组合）',
        'expect': '已知上游会误报：内层表格行被当成 timeline 行。本轮量实际产物',
        'markdown': ':::timeline\n- 2024年01月 | 立项 | 完成需求分析\n\n'
                    ':::table title="时间线里的表"\n| 阶段 | 产出 |\n| --- | --- |\n| 一期 | 核心功能 |\n| 二期 | AI 辅助 |\n:::\n\n'
                    '- 2025年01月 | 二期 | 新增 AI 辅助\n:::\n',
    },
    {
        'id': 'cmb-compare-in-callout', 'family': '容器套容器', 'minimal': 'ctn-compare',
        'label': ':::callout 里嵌 :::compare',
        'expect': '外层 callout 与内层对比表都渲染出来',
        'markdown': ':::callout title="嵌套对比"\n下面是对比：\n\n'
                    ':::compare\n维度 | 甲方案 | 乙方案 | accent\n成本 | 高 | 低 | default\n'
                    '工期 | 长 | 短 | default\n:::\n\n对比到此结束。\n:::\n',
    },
    # ---------- 二、属性 / 语法交织 ----------
    {
        'id': 'cmb-caseflow-table-title', 'family': '属性交织', 'minimal': 'ctn-case-flow',
        'label': ':::table title= 落在 :::case-flow 里',
        'expect': 'case-flow 的两张卡片 + 带标题的表格都渲染出来',
        'markdown': ':::case-flow\n- [案例 01] 从零搭建个人知识库\n'
                    ':::table title="案例一的指标"\n| 指标 | 数值 |\n| --- | --- |\n| 收录 | 1200 条 |\n:::\n'
                    '- [案例 02] 用 AI 辅助选题\n:::\n',
    },
    {
        'id': 'cmb-callout-title-in-steps', 'family': '属性交织', 'minimal': 'blk-steps-2',
        'label': '<steps> 里放带 title 的 :::callout',
        'expect': '外层步骤与内层带标题的 callout 都渲染出来',
        'markdown': '<steps>\n<step title="第一步">先看提示：\n\n'
                    ':::callout title="注意事项"\n这一步最容易漏掉的是初始化。\n:::\n\n</step>\n'
                    '<step title="第二步">再执行。</step>\n</steps>\n',
    },
    {
        'id': 'cmb-table-title-in-callout', 'family': '属性交织', 'minimal': 'attr-table-title',
        'label': ':::callout title= 里放 :::table title=（两个 title 交织）',
        'expect': '两个 title 都生效，互不吞并',
        'markdown': ':::callout title="外层标题"\n:::table title="内层表格标题"\n'
                    '| 项目 | 数值 |\n| --- | --- |\n| 并发 | 4 |\n| 超时 | 300 秒 |\n:::\n:::\n',
    },
    {
        'id': 'cmb-breaking-table', 'family': '属性交织', 'minimal': 'ctn-breaking',
        'label': ':::breaking 里放 :::table title=',
        'expect': '开篇大卡与内层表格都渲染出来',
        'markdown': ':::breaking title="今日要闻"\n一句话说清今天最重要的事。\n\n'
                    ':::table title="关键数据"\n| 指标 | 变化 |\n| --- | --- |\n| 吞吐 | +38% |\n:::\n:::\n',
    },
    # ---------- 三、同一组件出现两次以上 ----------
    {
        'id': 'cmb-slider-twice', 'family': '同组件多次', 'minimal': 'ctn-slider',
        'label': '两个 :::slider（同一篇里出现两次）',
        'expect': '两个独立的 <svg> 轮播，各 600×200，各有 animateTransform 与 3 张图',
        'markdown': f'第一处轮播：\n\n:::slider images="{BANNER},{BANNER}" interval="3" width="600" height="200" type="1"\n:::\n\n'
                    f'中间一段正文把两个轮播隔开。\n\n'
                    f'第二处轮播：\n\n:::slider images="{BANNER},{BANNER}" interval="4" width="600" height="200" type="2"\n:::\n\n结束。\n',
    },
    {
        'id': 'cmb-engage-twice', 'family': '同组件多次', 'minimal': 'blk-engage-card',
        'label': '两个 <engage-card>（图标圈 SVG 出现两次）',
        'expect': '两个卡片、各自的图标圈都渲染出来',
        'markdown': '<engage-card title="第一张卡">第一张卡的正文。</engage-card>\n\n'
                    '中间正文。\n\n<engage-card title="第二张卡">第二张卡的正文。</engage-card>\n',
    },
    {
        'id': 'cmb-timeline-twice', 'family': '同组件多次', 'minimal': 'ctn-timeline',
        'label': '两个 :::timeline',
        'expect': '两条独立时间线，条目数与输入一致（3 + 2）',
        'markdown': ':::timeline\n- 2024年01月 | 立项 | 完成需求分析\n'
                    '- 2024年06月 | 一期上线 | 核心功能发布\n- 2025年01月 | 二期 | 新增 AI 辅助\n:::\n\n'
                    '第二条时间线：\n\n:::timeline\n- 上午 | 选题 | 确定切入角度\n- 下午 | 成稿 | 完成排版\n:::\n',
    },
    {
        'id': 'cmb-callout-thrice', 'family': '同组件多次', 'minimal': 'callout-container',
        'label': '三个同型 :::callout（连排）',
        'expect': '三个独立容器，三份标题文字都在',
        'markdown': ':::callout title="第一则"\n第一条内容。\n:::\n\n'
                    ':::callout title="第二则"\n第二条内容。\n:::\n\n'
                    ':::callout title="第三则"\n第三条内容。\n:::\n',
    },
    # ---------- 四、超长内容 ----------
    {
        'id': 'cmb-long-section', 'family': '超长内容', 'minimal': 'md-heading',
        'label': '单节超长正文（约 3 千字，含行内组件与公式）',
        'expect': '整段不截断；可见文字长度与输入量级一致；行内组件仍在',
        'markdown': '## 超长正文压力测试\n\n' + LONG_PARAGRAPH + '\n\n### 小节收尾\n\n最后一句。\n',
    },
    {
        'id': 'cmb-long-table', 'family': '超长内容', 'minimal': 'ctn-table',
        'label': '20 行 :::table（带标题）',
        'expect': '20 行数据一行不丢，表头与标题都在',
        'markdown': ':::table title="二十行指标表"\n| 序号 | 指标 | 数值 | 趋势 |\n| --- | --- | --- | --- |\n'
                    + LONG_TABLE_ROWS + '\n:::\n',
    },
    # ---------- 五、行内与容器混排 ----------
    {
        'id': 'cmb-inline-in-container', 'family': '行内混排', 'minimal': 'callout-container',
        'label': '容器里混排行内组件（粗体 / 高亮 / 下标 / 上标 / 徽章 / 链接）',
        'expect': '容器的版式在，行内组件的标记不被容器吞掉',
        'markdown': ':::callout title="行内混排"\n'
                    '这里有**加粗**、==高亮==、!!胶囊!!、^^靛蓝^^、~下标~、^上标^、`行内代码`，'
                    '还有 <Badge>新</Badge> 与 [一条链接](https://example.com/page)。\n:::\n',
    },
    {
        'id': 'cmb-math-in-container', 'family': '行内混排', 'minimal': 'math-block',
        'label': '容器里放行内公式与块级公式',
        'expect': '容器版式在，公式仍渲染成 KaTeX（不是原样文本）',
        'markdown': ':::callout title="公式与容器"\n行内公式是 $E=mc^2$，分式 $\\frac{1}{3}$。\n\n'
                    '$$\n\\int_0^1 x^2 \\,dx = \\frac{1}{3}\n$$\n\n'
                    '带标记的求和：\n\n$$\n\\sum_{i=1}^{n} i = \\frac{n(n+1)}{2}\n$$\n:::\n',
    },
    {
        'id': 'cmb-slider-in-callout', 'family': '行内混排', 'minimal': 'ctn-slider',
        'label': ':::callout 里放 :::slider（重 SVG 组件嵌容器）',
        'expect': '外层容器在，内层轮播的 <svg> 也要真的画出来',
        'markdown': f':::callout title="容器里的轮播"\n\n'
                    f':::slider images="{BANNER},{BANNER}" interval="3" width="600" height="200" type="1"\n:::\n\n'
                    f'轮播下面还有一句正文。\n:::\n',
    },
]


# 每一例「正确渲染应该长什么样」的判据，**写成可断言的内容**，不靠肉眼：
#   must      —— 可见文字里必须出现的片段
#   must_html —— 产物 HTML 里至少要有几个某结构（单写法的产物量过之后才能定这个数）
#
# 拿单写法当基准是有依据的：`components/<minimal>.html` 里这些结构确实存在，所以
# 「两次就该是两倍」「模板里该有表格」都是可算的判据，不是我觉得应该有。
MARKERS = {
    'cmb-callout-timeline': {'must': ['嵌套容器', '立项', '二期'], 'must_html': {}},
    # 单写法 `blk-steps-2` 的产物里三个 step 标题都在（ZQSTEP1/ZQSTEP2 + 说明），所以标题消失是可判的
    'cmb-callout-steps': {'must': ['第一步', '第二步', '第三步'], 'must_html': {}},
    # 单写法 `ctn-timeline` 产物无表格；这一例内层是 :::table，正确渲染就必须有 <table>
    'cmb-timeline-table': {'must': ['立项', '二期'], 'must_html': {'<table': 1}},
    'cmb-compare-in-callout': {'must': ['嵌套对比', '甲方案', '乙方案'], 'must_html': {}},
    'cmb-caseflow-table-title': {'must': ['案例 01', '案例 02', '收录', '1200'], 'must_html': {'<table': 1}},
    'cmb-callout-title-in-steps': {'must': ['第一步', '第二步', '注意事项'], 'must_html': {}},
    'cmb-table-title-in-callout': {'must': ['外层标题'], 'must_html': {'<table': 1}},
    'cmb-breaking-table': {'must': ['今日要闻'], 'must_html': {'<table': 1}},
    # 单写法 `ctn-slider` 产物：1 个 <svg> / 1 个 <animateTransform> / 3 个 <foreignObject>。
    # 这里第二处用的是 `type="2"`，而实测 type 本身就是自变量（见 `round8_slider_type_discriminator.txt`：
    # type1 → 3 个 foreignObject，type2 / type3 → 2 个，与「有几个轮播」无关），所以两次合计是 3 + 2 = 5。
    'cmb-slider-twice': {'must': ['第一处轮播', '第二处轮播'],
                         'must_html': {'<svg': 2, '<animateTransform': 2, '<foreignObject': 5}},
    # 单写法 `blk-engage-card` 产物 3 个 <svg>（图标圈）→ 两次即 6 个
    'cmb-engage-twice': {'must': ['第一张卡', '第二张卡'], 'must_html': {'<svg': 6}},
    'cmb-timeline-twice': {'must': ['立项', '二期', '上午', '下午'], 'must_html': {}},
    'cmb-callout-thrice': {'must': ['第一则', '第二则', '第三则'], 'must_html': {}},
    'cmb-long-section': {'must': ['加粗的结论', '高亮的要点', '最后一句'], 'must_html': {'class="katex"': 12}},
    # 单写法 `ctn-table` 产物是 <table> + 2 个 <tr>；表头 + 20 行 = 21 个
    'cmb-long-table': {'must': ['第01行', '第20行'], 'must_html': {'<tr': 21}},
    'cmb-inline-in-container': {'must': ['行内混排', '加粗', '高亮', '下标', '上标', '行内代码'], 'must_html': {}},
    'cmb-math-in-container': {'must': ['公式与容器'], 'must_html': {'class="katex"': 4, 'katex-display': 2}},
    'cmb-slider-in-callout': {'must': ['容器里的轮播'], 'must_html': {'<svg': 1}},
}
for _case in CASES:
    _marker = MARKERS.get(_case['id'])
    if _marker is None:
        raise SystemExit('组合用例缺少判据：' + _case['id'])
    _case.update(_marker)


def render(markdown):
    body = json.dumps({'markdown': markdown}).encode()
    request = urllib.request.Request(URL, data=body, headers={
        'Content-Type': 'application/json', 'X-Render-Token': TOKEN})
    return json.load(urllib.request.urlopen(request, timeout=120))


def stats(html):
    text = re.sub(r'<[^>]+>', '', html)
    text = re.sub(r'\s+', '', text)
    return {
        'chars': len(html),
        'textLength': len(text),
        'svg': html.count('<svg'),
        'animateTransform': html.count('<animateTransform'),
        'foreignObject': html.count('<foreignObject'),
        'katex': html.count('class="katex"'),
        'katexDisplay': html.count('katex-display'),
        # 判定「渲染器根本没认」：可见文字里残留字面容器语法。
        # `:::` 要在**剥掉标签后的文本**里找 —— 它本来就是文字，不是标签（见 round11_crosscheck.py:234 同款写法）。
        'leakedColonContainer': len(re.findall(r':::[a-z-]+', text)),
        # 判定「元素形态的透传」：产物里残留**字面标签**。
        #
        # ⚠ 这里必须在 `html` 上找，**不能在 `text` 上找**（第三十六轮修正）。
        # 原实现写的是 `text`，而 `text` 是上一行刚用 `re.sub(r'<[^>]+>', '', html)` 剥掉全部标签的结果——
        # 一个已经没有 `<` 的字符串里当然找不到 `<steps`，所以这个计数**恒为 0**：
        # 它报的 0 不是「测出来没有残留」，而是「根本算不出来」，属结构性失效（不是漏报个案）。
        # 实测：`cmb-callout-steps` 的产物里真有 4 处（`<steps>` + 3×`<step>`），修正前报 0、修正后报 4。
        # 对照写法见 `round11_crosscheck.py:233`（element_hits 在 html 上找、colon_hits 在 text 上找）。
        'leakedTag': len(re.findall(r'<(?:steps|step|case-flow|timeline|slider|engage-card|badge)\b', html, re.I)),
        # 第三十七轮：上面这条 `leakedTag` 是**按元素名**数的（7 个硬编码名字），本轮补一条**按形态**数的。
        #
        # 为什么不能只靠 `leakedTag`：它把「合法占位符」和「真残留」一视同仁——
        # `<slider … />` 是上游有意留下、交给前端水合的**合法**元素，`<layout-hero>文字</layout-hero>`
        # 才是没被消费掉的残留。两者**都在注册表里**、都是非标准元素，按名字区分不了。
        #
        # 形态判据（见 `gen/passthrough.py` 的模块文档）：非标准元素**包着可见文本** => 语法没被消费；
        # **自闭合且无文本** => 占位符。这条判据**不需要任何元素名清单**。
        #
        #  它只作**线索**，不参与 `upstreamVerdict`：Q1「语法被消费了吗」是事实问题，可以用形态判；
        # Q2「这算不算缺陷」取决于「这份输入本来就该渲染吗」，必须由用例声明的 `must`/`mustHtml` 回答。
        **passthrough_counts(html),
    }


records = []
for case in CASES:
    response = render(case['markdown'])
    html = response.get('html') or ''
    open(os.path.join(OUT, case['id'] + '.md'), 'w', encoding='utf-8').write(case['markdown'])
    open(os.path.join(OUT, case['id'] + '.html'), 'w', encoding='utf-8').write(html)
    backend = stats(html)
    # 两边都去掉空白再比：渲染器会在数字与汉字之间补空格（「第01行」→「第 01 行」），
    # 按原样比会把排版差异记成「内容丢了」。
    text = re.sub(r'\s+', '', re.sub(r'<[^>]+>', '', html))
    missingMust = [item for item in case['must'] if re.sub(r'\s+', '', item) not in text]
    missingHtml = {key: {'want': value, 'got': html.count(key)} for key, value in case['must_html'].items()
                   if html.count(key) < value}
    record = {
        'id': case['id'], 'family': case['family'], 'minimal': case['minimal'],
        'label': case['label'], 'expect': case['expect'],
        'syntax': case['label'], 'inputChars': len(case['markdown']),
        'must': case['must'], 'mustHtml': case['must_html'],
        'missingMust': missingMust, 'missingHtml': missingHtml,
        'ok': bool(response.get('ok')),
        'warnings': (response.get('meta') or {}).get('warnings') or [],
        'backend': backend,
    }
    records.append(record)
    print('%-28s ok=%-5s chars=%-6d text=%-6d svg=%d katex=%d leak=%d/%d 缺文字=%d 缺结构=%d warnings=%d'
          % (case['id'], record['ok'], backend['chars'], backend['textLength'],
             backend['svg'], backend['katex'], backend['leakedColonContainer'], backend['leakedTag'],
             len(missingMust), len(missingHtml), len(record['warnings'])))

json.dump({'cases': records}, open(os.path.join(OUT, 'combos.json'), 'w', encoding='utf-8'),
          ensure_ascii=False, indent=1)
print('\n产物:', OUT)
