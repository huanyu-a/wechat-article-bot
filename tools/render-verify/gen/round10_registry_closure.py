"""第十轮 · 组件注册表全集的收口核对（63 个注册 ID 逐个落到「已验」或「等上游」）。

背景：第五轮从官网前端 bundle 抽出 **63 个组件 ID**，把它当作「引擎自己声明的组件全集」，
再展开成 79 个可写语法样例。问题是这 63 个 ID 里有一族 `layout-*`（38 个），
第八轮只实测了其中 **16 个名字**（`round8_unknown_tags.py`），
剩下 **22 个**既没验过、也没写进「等上游」清单——按第十轮的验收口径，这叫**悬空**。

本脚本把这 22 个补上（连同已测的 16 个再打一遍，凑齐整族 38 个 × 2 种写法 = 76 组），
**每条都落盘产物 HTML**，交给 `run-set-browser.mjs registry` 把同一份产物灌进真实浏览器，
量「后端 API」与「编辑器前端」两条路径——这样 63 个注册 ID 的每一格都有实测证据。

只调渲染 API（`POST https://www.bx9y.com.cn/__markflow_render`），**不落库、不改任何生产数据**。

用法：python tools/render-verify/gen/round10_registry_closure.py
产物：target/probe/registry/<id>.md、<id>.html、target/probe/registry/registry.json
     target/probe/round10_registry_closure.json、target/probe/round10_registry_closure.txt
"""
import json
import os
import re
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from paths import OUT as OUTROOT, SPEC, TOKEN_FILE, RENDER_URL  # noqa: E402, read_token

OUT = os.path.join(OUTROOT, 'registry')
os.makedirs(OUT, exist_ok=True)

TOKEN = read_token()
URL = RENDER_URL

# 非 layout-* 的注册 ID → 覆盖它的样例 id（79 个样例里的哪一个，第五轮已验、第七/九/十轮已上真浏览器）。
# 这张表就是「注册表的 25 个非 layout ID 没有一个漏网」的证据本身。
NON_LAYOUT_COVERED = {
    'Title_DA01': ['blk-title'],
    'Title_DA02': ['blk-title'],
    'PTitle_DA01': ['blk-p-title'],
    'Lead_DA01': ['blk-lead', 'ctn-lead'],
    'CTA_DA01': ['blk-cta', 'ctn-cta'],
    'Engage_DA01': ['blk-engage-label'],
    'Engage_DA02': ['blk-engage-tag'],
    'Statement_DA01': ['blk-statement'],
    'Img_DA01': ['blk-img', 'md-image', 'md-image-sized', 'md-multi-image'],
    'Badge_DA01': ['in-badge'],
    'Badges_DA01': ['blk-badges'],
    'Icon_DA01': ['in-icon'],
    'reading-path': ['ctn-reading-path'],
    'breaking': ['ctn-breaking'],
    'steps-horizontal': ['ctn-steps-h'],
    'steps-vertical': ['ctn-steps-v'],
    'case-flow': ['ctn-case-flow', 'blk-case-flow'],
    'timeline': ['ctn-timeline', 'blk-timeline'],
    'slider': ['ctn-slider', 'blk-slider'],
    'gov-header': ['blk-gov-header'],
    'callout': ['callout-container', 'callout-tip-container',
                'callout-quote-tip', 'callout-quote-warning'],
    'table': ['ctn-table'],
    'code-block': ['ctn-code-block'],
    'hint': ['ctn-hint'],
    'align': ['ctn-align'],
}


def render(markdown):
    request = urllib.request.Request(URL, data=json.dumps({'markdown': markdown}).encode(),
                                     headers={'Content-Type': 'application/json',
                                              'X-Render-Token': TOKEN})
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.load(response)


def stripped(html):
    return re.sub(r'\s+', '', re.sub(r'<[^>]+>', '', html))


registry = json.load(open(os.path.join(SPEC, 'component_registry.json'), encoding='utf-8'))
layout_ids = [name for name, _ in registry if name.startswith('layout-')]
other_ids = [name for name, _ in registry if not name.startswith('layout-')]
missing = [name for name in other_ids if name not in NON_LAYOUT_COVERED]
if missing:
    raise SystemExit('注册 ID 没有登记覆盖样例：' + ', '.join(missing))
print('registry ids = %d（layout-* %d / 其他 %d）' % (len(registry), len(layout_ids), len(other_ids)))

cases = []
for name in layout_ids:
    for form, markdown, marker in (
            ('container', ':::%s\n甲 | 乙\n:::' % name, ':::' + name),
            ('tag', '<%s>甲 乙</%s>' % (name, name), '<' + name)):
        case_id = '%s-%s' % (form, name)
        response = render(markdown)
        html = response.get('html') or ''
        text = stripped(html)
        open(os.path.join(OUT, case_id + '.md'), 'w', encoding='utf-8').write(markdown)
        open(os.path.join(OUT, case_id + '.html'), 'w', encoding='utf-8').write(html)
        leak_raw = marker in html
        leak_text = marker in text
        cases.append({
            'id': case_id,
            'registryId': name,
            'form': form,
            'label': ':::%s 容器式' % name if form == 'container' else '<%s> 标签式' % name,
            'replaces': name,
            'expect': '上游没有这一族的语法分支，产物里必然留着字面语法（这就是「等上游」的判据）',
            'markdown': markdown,
            'must': [],
            'mustHtml': {},
            'forbidRaw': [],
            'missingMust': [],
            'missingHtml': {},
            'ok': bool(response.get('ok')),
            'warnings': (response.get('meta') or {}).get('warnings') or [],
            'backend': {
                'chars': len(html),
                'textLength': len(text),
                'svg': html.count('<svg'),
                'animateTransform': html.count('<animateTransform'),
                'foreignObject': html.count('<foreignObject'),
                'katex': html.count('class="katex"'),
                'leakedColon': len(re.findall(r':::', text)),
                'leakedTag': 1 if (leak_raw and not leak_text) else 0,
                'leakRaw': leak_raw,
                'leakText': leak_text,
            },
        })
        print('%-34s chars=%-6d text=%-4d leakRaw=%-5s leakText=%-5s'
              % (case_id, len(html), len(text), leak_raw, leak_text))

unsupported = [c for c in cases if c['backend']['leakRaw'] or c['backend']['leakText']]
open(os.path.join(OUT, 'registry.json'), 'w', encoding='utf-8').write(
    json.dumps({'cases': cases, 'summary': {
        'registryTotal': len(registry),
        'layoutIds': len(layout_ids),
        'otherIds': len(other_ids),
        'forms': len(cases),
        'upstreamUnsupported': len(unsupported),
    }}, ensure_ascii=False, indent=1))

open(os.path.join(OUTROOT, 'round10_registry_closure.json'), 'w', encoding='utf-8').write(
    json.dumps({
        'registryTotal': len(registry),
        'layoutIds': layout_ids,
        'otherIds': other_ids,
        'nonLayoutCovered': NON_LAYOUT_COVERED,
        'cases': cases,
        'summary': {
            'layoutIdsTested': len(layout_ids),
            'forms': len(cases),
            'upstreamUnsupported': len(unsupported),
            'supported': len(cases) - len(unsupported),
        },
    }, ensure_ascii=False, indent=1))

lines = [
    'registry ids = %d  (layout-* %d / other %d)' % (len(registry), len(layout_ids), len(other_ids)),
    'layout-* forms tested = %d   upstream-unsupported = %d   supported = %d'
    % (len(cases), len(unsupported), len(cases) - len(unsupported)),
    '',
]
for case in cases:
    lines.append('%-34s chars=%-6d text=%-4d leakRaw=%-5s leakText=%-5s'
                 % (case['id'], case['backend']['chars'], case['backend']['textLength'],
                    case['backend']['leakRaw'], case['backend']['leakText']))
open(os.path.join(OUTROOT, 'round10_registry_closure.txt'), 'w', encoding='utf-8').write('\n'.join(lines))
print('summary: forms=%d unsupported=%d supported=%d'
      % (len(cases), len(unsupported), len(cases) - len(unsupported)))
