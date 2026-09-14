"""第十八轮 · 第 10 条 `:::infographic` 的**换写法补测**（后端产物侧）。

背景：用户说「原项目就没有正确显示」，但用**他给的那一种写法**在真实渲染 API 上复现不出缺陷。
这一步是**换几种写法再打一遍真实 API**，看有没有哪一支能产出结构不完整的产物——
即把「未能复现」从「只试过一种写法」变成「试过 N 种写法都复现不出」。

只调渲染 API，**不落库、不改任何生产数据**。原样保留每种写法的产物，便于人工复核。
产物 `target/probe/r16/infographic_variants.json` 被 `docs/dev/upstream-issues.md` §R10 与
`docs/dev/known-issues-handoff.md` §3.20② 第 10 条引用为取证依据 → 故本脚本**进版本控制**。

用法：python tools/render-verify/gen/round18_infographic_variants.py
产物：target/probe/r16/infographic_variants.json（含每种的产物 HTML 与结构指标）
"""
import json
import os
import re
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from paths import OUT, TOKEN_FILE, RENDER_URL  # noqa: E402

TOKEN = open(TOKEN_FILE).read().strip()

# 用户原文那一种（基线）——与 round16_editor_reported.py 里的 CASES 逐字相同
BASELINE = (
    ':::infographic\n'
    'label: 读者画像\n'
    'title: 谁在看你的文章\n'
    'subtitle: 基于 12,000 份问卷的核心发现\n'
    'body: |\n'
    '  78% 的读者会在 5 秒内判断是否继续阅读\n'
    '  排版质量直接影响信任度评分（r=0.71）\n'
    '  手机端阅读占比 83%，但大多数文章按桌面端设计\n'
    ':::\n'
)

VARIANTS = [
    ('A 用户原文（body: | 块标量）', BASELINE),
    ('B body 用 YAML 列表', BASELINE.replace(
        'body: |\n'
        '  78% 的读者会在 5 秒内判断是否继续阅读\n'
        '  排版质量直接影响信任度评分（r=0.71）\n'
        '  手机端阅读占比 83%，但大多数文章按桌面端设计\n',
        'body:\n'
        '  - 78% 的读者会在 5 秒内判断是否继续阅读\n'
        '  - 排版质量直接影响信任度评分（r=0.71）\n'
        '  - 手机端阅读占比 83%，但大多数文章按桌面端设计\n')),
    ('C body 无 | 直接换行缩进', BASELINE.replace('body: |', 'body:')),
    ('D 缺 subtitle', BASELINE.replace('subtitle: 基于 12,000 份问卷的核心发现\n', '')),
    ('E 缺 label', BASELINE.replace('label: 读者画像\n', '')),
    ('F 缺 body（只有标题三段）', re.sub(r'body: \|.*?:::', ':::', BASELINE, flags=re.S)),
    ('G 字段顺序颠倒（body 在前）',
     ':::infographic\n'
     'body: |\n'
     '  78% 的读者会在 5 秒内判断是否继续阅读\n'
     '  排版质量直接影响信任度评分（r=0.71）\n'
     'label: 读者画像\n'
     'title: 谁在看你的文章\n'
     'subtitle: 基于 12,000 份问卷的核心发现\n'
     ':::\n'),
    ('H 标签式 <infographic>',
     '<infographic label="读者画像" title="谁在看你的文章" subtitle="基于 12,000 份问卷的核心发现">\n'
     '78% 的读者会在 5 秒内判断是否继续阅读\n'
     '排版质量直接影响信任度评分（r=0.71）\n'
     '手机端阅读占比 83%，但大多数文章按桌面端设计\n'
     '</infographic>\n'),
    ('I label/title/subtitle 用引号', BASELINE.replace(
        'label: 读者画像', 'label: "读者画像"').replace(
        'title: 谁在看你的文章', 'title: "谁在看你的文章"').replace(
        'subtitle: 基于 12,000 份问卷的核心发现', 'subtitle: "基于 12,000 份问卷的核心发现"')),
    ('J body 只给一行', BASELINE.replace(
        '  排版质量直接影响信任度评分（r=0.71）\n'
        '  手机端阅读占比 83%，但大多数文章按桌面端设计\n', '')),
]


def render(markdown):
    body = json.dumps({'markdown': markdown}).encode('utf-8')
    request = urllib.request.Request(
        RENDER_URL, data=body,
        headers={'Content-Type': 'application/json', 'X-Render-Token': TOKEN})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.loads(response.read().decode('utf-8'))


def metrics(markdown, payload):
    html = payload.get('html') or ''
    text = payload.get('text') or re.sub(r'<[^>]+>', '', html)
    return {
        'chars': len(html),
        'textLength': len(re.sub(r'\s+', '', text)),
        # 四段是否都产出
        'has_label': '读者画像' in html,
        'has_title': '谁在看你的文章' in html,
        'has_subtitle': '基于 12,000 份问卷的核心发现' in html,
        'body_lines': len(re.findall(r'78%|r=0\.71|83%', html)),
        # 失败形态：字面残留 / 退化
        'literal_colon': html.count(':::'),
        'literal_keys': len(re.findall(r'(label|title|subtitle|body)\s*[:：]', text)),
        'flex_rows': html.count('display:flex'),
        'dots_6x6': len(re.findall(r'width:6px;height:6px', html)),
        'warnings': payload.get('meta', {}).get('warnings') if isinstance(payload.get('meta'), dict) else None,
    }


results = []
for name, markdown in VARIANTS:
    payload = render(markdown)
    results.append({'variant': name, 'markdown': markdown, 'html': payload.get('html'),
                    'metrics': metrics(markdown, payload)})
    m = results[-1]['metrics']
    print(f"{name:34s} 字符={m['chars']:5d} label={int(m['has_label'])} title={int(m['has_title'])} "
          f"subtitle={int(m['has_subtitle'])} body行={m['body_lines']} 字面:::={m['literal_colon']} "
          f"字面键名={m['literal_keys']} flex={m['flex_rows']} 圆点={m['dots_6x6']}")

out = os.path.join(OUT, 'r16', 'infographic_variants.json')
with open(out, 'w', encoding='utf-8') as fh:
    json.dump(results, fh, ensure_ascii=False, indent=1)
print('\n产物：', out)
