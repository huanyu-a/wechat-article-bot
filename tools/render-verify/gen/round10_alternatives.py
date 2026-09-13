"""第十轮 · 「等上游」清单的收敛实验：9 条里哪几条能靠**改写法**绕开。

第九轮把 9 条 `na` 做成「等上游」清单时，只回答了「上游没实现」，
没回答「那本项目的稿子该怎么写」。本脚本对每一条给出一个**替代写法**，
打真实渲染 API 拿到后端产物，再由 `run-set-browser.mjs` 把同一份产物灌进真实浏览器，
同时量「后端 API」与「编辑器前端」两条路径。

判据是**产出**而不是感觉：可见文字里必须出现指定片段、产物里必须有指定结构、
`:::` 一处都不能残留（残留 = 渲染器根本没认这个写法）。

只调渲染 API，**不落库、不改任何生产数据**。

用法：python tools/render-verify/gen/round10_alternatives.py
产物：target/probe/alt/<id>.md、<id>.html、target/probe/alt/alt.json
"""
import json
import os
import re
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from paths import OUT, TOKEN_FILE, RENDER_URL  # noqa: E402

OUT = os.path.join(OUT, 'alt')
os.makedirs(OUT, exist_ok=True)

TOKEN = open(TOKEN_FILE).read().strip()
URL = RENDER_URL
BANNER = 'https://robocopmao.github.io/r-markdown/banner4.webp'

CASES = [
    {
        'id': 'alt-callout-success', 'replaces': 'ctn-success', 'upstreamItem': '#1',
        'label': '用 :::callout type="success" 代替 :::success',
        'expect': '绿色（#16a34a）提示框画出来，标题与正文都在，无 ::: 残留',
        'markdown': ':::callout type="success" title="发布检查通过"\n十二项上线前检查全部通过，可以发版。\n:::\n',
        'must': ['发布检查通过', '可以发版'],
        'mustHtml': {},
        'colors': {'backend': '#16a34a', 'editor': 'rgb(22,163,74)'},
    },
    {
        'id': 'alt-callout-danger', 'replaces': 'ctn-danger', 'upstreamItem': '#2',
        'label': '用 :::callout type="danger" 代替 :::danger',
        'expect': '红色（#dc2626）提示框画出来，标题与正文都在，无 ::: 残留',
        'markdown': ':::callout type="danger" title="高危操作"\n这一步会清空历史版本，执行前务必备份。\n:::\n',
        'must': ['高危操作', '务必备份'],
        'mustHtml': {},
        'colors': {'backend': '#dc2626', 'editor': 'rgb(220,38,38)'},
    },
    {
        'id': 'alt-breaking-for-layout-hero', 'replaces': 'reg-layout-hero', 'upstreamItem': '#3 #4',
        'label': '用 :::breaking 代替 :::layout-hero / <layout-hero>',
        'expect': '开篇大卡画出来（badge / title / subtitle / chips 全在），无 ::: 残留',
        'markdown': ':::breaking badge="新" title="今日要闻" subtitle="三分钟读完本周的排版更新"\n'
                    '本期讲清楚渲染式排版与指令式排版的边界。\n:::\n',
        'must': ['今日要闻', '三分钟读完本周的排版更新'],
        'mustHtml': {},
    },
    {
        'id': 'alt-reading-path-for-layout-toc', 'replaces': 'reg-layout-toc', 'upstreamItem': '#5',
        'label': '用 :::reading-path 代替 :::layout-toc',
        'expect': '编号圆点导航画出来（`|` 之后的说明列按设计不显示，只出章节标题），无 ::: 残留',
        'markdown': ':::reading-path\n- 第一节 | 渲染链路怎么走\n- 第二节 | 版式从哪里来\n:::\n',
        'must': ['第一节', '第二节'],
        'mustHtml': {},
    },
    {
        'id': 'alt-compare-for-layout-metrics', 'replaces': 'reg-layout-metrics', 'upstreamItem': '#6',
        'label': '用 :::compare 代替 :::layout-metrics',
        'expect': '对比网格画出来（自绘 <section> 网格，不是 <table>），指标名与两列数值都在，无 ::: 残留',
        'markdown': ':::compare\n指标 | 本月 | 上月 | accent\n转化率 | 4.2% | 3.8% | default\n'
                    '留存率 | 31% | 29% | default\n:::\n',
        'must': ['转化率', '4.2%', '留存率'],
        'mustHtml': {},
    },
    {
        'id': 'alt-hint-container', 'replaces': 'reg-hint-tag', 'upstreamItem': '#7',
        'label': '用 :::hint 容器式代替 <hint> 标签式',
        'expect': '信息卡画出来（有边框），正文在，无 ::: 残留',
        'markdown': ':::hint\n这条提示只有容器式写法有效。\n:::\n',
        'must': ['只有容器式写法有效'],
        'mustHtml': {},
    },
    {
        'id': 'alt-slider-open-close', 'replaces': 'blk-slider-selfclose', 'upstreamItem': '#8',
        'label': '用 <slider …></slider>（开+闭）代替自闭合 <slider … />',
        'expect': '真轮播：1 个 <svg>、animateTransform 与 foreignObject 都在，无字面 <slider 残留',
        'markdown': '<slider images="%s,%s,%s" interval="3" width="600" height="200" type="1">\n</slider>\n'
                    % (BANNER, BANNER, BANNER),
        'must': [],
        'mustHtml': {'<svg': 1, '<animateTransform': 1},
        'forbidRaw': ['<slider'],
    },
    {
        'id': 'alt-slider-single-image', 'replaces': 'blk-slider-selfclose（单图场景）', 'upstreamItem': '#8',
        'label': '单图也用开+闭 <slider …></slider>（不写自闭合）',
        'expect': '退化成一张普通 <img>（单图本就不需要轮播），关键是**没有字面 <slider 残留**',
        'markdown': '<slider images="%s" interval="3" width="600" height="200" type="1">\n</slider>\n' % BANNER,
        'must': [],
        'mustHtml': {'<img': 1},
        'forbidRaw': ['<slider'],
    },
    {
        'id': 'alt-case-flow-container', 'replaces': 'blk-case-flow-badline', 'upstreamItem': '#9',
        'label': ':::case-flow 每行都写「行首 - + [标签]」',
        'expect': '两张案例卡片都画出来，标题文字在，无 ::: 残留',
        'markdown': ':::case-flow label="案例"\n- [案例 01] 从零搭建个人知识库\n- [案例 02] 用 AI 辅助选题\n:::\n',
        'must': ['从零搭建个人知识库', '用 AI 辅助选题'],
        'mustHtml': {},
    },
    {
        'id': 'alt-case-flow-tag', 'replaces': 'blk-case-flow-badline', 'upstreamItem': '#9',
        'label': '<case-flow> 标签式同样每行写「行首 - + [标签]」',
        'expect': '与容器式结果一致：两张卡片、标题文字在，无字面 <case-flow 残留',
        'markdown': '<case-flow label="案例">\n- [案例 01] 从零搭建个人知识库\n- [案例 02] 用 AI 辅助选题\n</case-flow>\n',
        'must': ['从零搭建个人知识库', '用 AI 辅助选题'],
        'mustHtml': {},
        'forbidRaw': ['<case-flow'],
    },
]


def render(markdown):
    request = urllib.request.Request(URL, data=json.dumps({'markdown': markdown}).encode(),
                                     headers={'Content-Type': 'application/json',
                                              'X-Render-Token': TOKEN})
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.load(response)


def stats(html, case):
    text = re.sub(r'\s+', '', re.sub(r'<[^>]+>', '', html))
    raw_nospace = re.sub(r'\s+', '', html)
    return {
        'chars': len(html),
        'textLength': len(text),
        'svg': html.count('<svg'),
        'animateTransform': html.count('<animateTransform'),
        'foreignObject': html.count('<foreignObject'),
        'katex': html.count('class="katex"'),
        # 判据「渲染器根本没认」：可见文字里残留字面容器语法（含裸收尾 :::）
        'leakedColon': len(re.findall(r':::', text)),
        # 标签式的字面残留要扫**原始产物**（剥标签会把它一起洗掉，第七轮踩过这个坑）
        'leakedTag': sum(raw_nospace.count(item) for item in case.get('forbidRaw', [])),
        'colors': {name: raw_nospace.count(value.replace(' ', ''))
                   for name, value in (case.get('colors') or {}).items() if name == 'backend'},
    }


records = []
for case in CASES:
    response = render(case['markdown'])
    html = response.get('html') or ''
    open(os.path.join(OUT, case['id'] + '.md'), 'w', encoding='utf-8').write(case['markdown'])
    open(os.path.join(OUT, case['id'] + '.html'), 'w', encoding='utf-8').write(html)
    backend = stats(html, case)
    text = re.sub(r'\s+', '', re.sub(r'<[^>]+>', '', html))
    missingMust = [item for item in case['must'] if re.sub(r'\s+', '', item) not in text]
    missingHtml = {key: {'want': value, 'got': html.count(key)} for key, value in case['mustHtml'].items()
                   if html.count(key) < value}
    record = {
        'id': case['id'], 'replaces': case['replaces'], 'upstreamItem': case['upstreamItem'],
        'label': case['label'], 'expect': case['expect'], 'syntax': case['label'],
        'inputChars': len(case['markdown']), 'must': case['must'], 'mustHtml': case['mustHtml'],
        'colors': case.get('colors') or {}, 'forbidRaw': case.get('forbidRaw', []),
        'missingMust': missingMust, 'missingHtml': missingHtml,
        'ok': bool(response.get('ok')),
        'warnings': (response.get('meta') or {}).get('warnings') or [],
        'backend': backend,
    }
    records.append(record)
    print('%-32s chars=%-6d text=%-4d svg=%-3d ::: %-3d tagResidue=%-3d %s'
          % (case['id'], backend['chars'], backend['textLength'], backend['svg'],
             backend['leakedColon'], backend['leakedTag'],
             'OK' if not missingMust and not missingHtml and not backend['leakedColon']
             and not backend['leakedTag'] else 'CHECK'))

payload = {
    'cases': records,
    'summary': {
        'cases': len(records),
        'backendClean': sum(1 for r in records if not r['missingMust'] and not r['missingHtml']
                            and not r['backend']['leakedColon'] and not r['backend']['leakedTag']),
    },
}
open(os.path.join(OUT, 'alt.json'), 'w', encoding='utf-8').write(
    json.dumps(payload, ensure_ascii=False, indent=1))
print('summary:', payload['summary'])
