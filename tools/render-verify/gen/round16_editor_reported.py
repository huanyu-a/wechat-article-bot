"""第十六轮 · 用户在编辑器里逐条标注的 11 条渲染缺陷 —— 样例生成（后端产物侧）。

输入是用户**原文照录**的 11 段 MarkFlow 源码（不改写、不「修得好看一点」）。
本脚本只做一件事：把每一段原样打一次真实渲染 API，把产物落盘，并量出
「渲染服务到底认不认这个写法」的客观指标（字符数 / 可见文字长度 / 字面 `:::` 残留 /
字面标签残留 / svg / katex / 颜色命中）。

**不负责判「好不好看」**——那是下一步（`browser/probe_r16.js` 在真实浏览器里量计算样式）
和汇总脚本（`browser/summarize-r16.mjs`）的事。这里只保证输入原样、产物可复现。

只调渲染 API，**不落库、不改任何生产数据**。

用法：python tools/render-verify/gen/round16_editor_reported.py
产物：target/probe/r16/<id>.md、<id>.html、target/probe/r16/r16.json
"""
import json
import os
import re
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from paths import OUT, TOKEN_FILE, RENDER_URL  # noqa: E402

R16 = os.path.join(OUT, 'r16')
os.makedirs(R16, exist_ok=True)

TOKEN = open(TOKEN_FILE).read().strip()
URL = RENDER_URL

# 用户标注的 11 条，`markdown` 一律**原样照录**（含其中的空行与全角空格）。
# `complaint` 是用户的原始描述（照录），`probe` 说明这一条在浏览器侧要量什么来取证。
CASES = [
    {
        'id': 'r16-01-changelog',
        'order': 1,
        'name': 'changelog 更新日志',
        'complaint': '缺少边框',
        'probe': '容器自身的 border（宽度 / 样式 / 颜色）与各版本卡片的 border / 背景色',
        'upstreamNote': False,
        'markdown': ':::changelog\n'
                    '{"version":"v2.6.0","date":"2026-09-12","added":["新增 4 套主题：明黄、正青、咖啡棕、复古纸",'
                    '"新增「复古人文」分组，收编优雅三件套、留白禅意、抹茶、墨黑手记"],"changed":["主题总数为 64 套",'
                    '"原「暗色」分组更名为「高亮」：均为高饱和亮色，输出仍为白底、并非暗色渲染",'
                    '"12 套主题重命名使名称与渲染色相符（如靛蓝→长春花蓝、薄荷绿→翡翠绿、优雅紫→酒红）",'
                    '"Tailwind/琥珀亮/Vercel 微调主题色以错开同 accent 色对，旧配色将回落自定义"],"fixed":[]}\n'
                    ':::\n',
        'must': ['v2.6.0', '主题总数为 64 套'],
        'forbidRaw': [],
    },
    {
        'id': 'r16-02-subscribe',
        'order': 2,
        'name': 'subscribe 订阅卡',
        'complaint': '原项目就没有正确显示，我已经告诉原项目了，你注意观察',
        'probe': '订阅表单的结构（输入框 / 按钮 / 标题 / 副标题）是否真的产出',
        'upstreamNote': True,
        'markdown': ':::subscribe\n'
                    'title: 关注「极客旅程」\n'
                    'subtitle: 每周更新排版技巧与内容策略\n'
                    'body: 已有 12,000+ 创作者订阅。不打扰，只发干货。\n'
                    'placeholder: 输入邮箱地址\n'
                    'btn: 立即订阅\n'
                    ':::\n',
        'must': ['关注「极客旅程」', '每周更新排版技巧与内容策略', '12,000+'],
        'forbidRaw': [],
    },
    {
        'id': 'r16-03-author-card',
        'order': 3,
        'name': 'author-card 作者卡',
        'complaint': '头像没有渲染完整',
        'probe': '头像 <img> 的天然尺寸（naturalWidth/Height）、显示尺寸、是否被裁切',
        'upstreamNote': False,
        'markdown': ':::author-card\n'
                    'title: 极客旅程\n'
                    'avatar: https://robocopmao.github.io/r-markdown/banner4.webp\n'
                    'bio: 专注内容排版与知识管理工具链，帮助创作者用更少的时间做出更好的内容。\n'
                    'role: 主理人 · 全栈开发者\n'
                    'tags: 排版|知识管理|效率工具\n'
                    ':::\n',
        'must': ['极客旅程', '主理人 · 全栈开发者'],
        'forbidRaw': [],
    },
    {
        'id': 'r16-04-quote-card',
        'order': 4,
        'name': 'quote-card 金句卡',
        'complaint': '与原项目渲染不一样，缺少底色等',
        'probe': '卡片自身的背景色 / 边框 / 圆角，以及引号装饰元素',
        'upstreamNote': False,
        'markdown': ':::quote-card\n'
                    '{"text":"如果你不能向一个六岁孩子解释清楚，那你就是没真正理解。",'
                    '"source":"理查德·费曼 · 诺贝尔物理学奖得主"}\n'
                    ':::\n',
        'must': ['如果你不能向一个六岁孩子解释清楚', '理查德·费曼 · 诺贝尔物理学奖得主'],
        'forbidRaw': [],
    },
    {
        'id': 'r16-05-audience-fit',
        'order': 5,
        'name': 'audience-fit 读者匹配',
        'complaint': '原项目就没有正确显示，我已经告诉原项目了，你注意观察',
        'probe': '三行「人群 | 说明 | 评级」是否被拆成三栏结构',
        'upstreamNote': True,
        'markdown': ':::audience-fit\n'
                    '技术团队 | 结构严谨、代码块清晰、API 文档可直接复制 | 高\n'
                    '运营人员 | 步骤卡片 + 指标看板，一眼看到关键数据和行动项 | 高\n'
                    'C 端读者 | 标题吸睛、金句醒目、图文混排降低阅读疲劳 | 中\n'
                    ':::\n',
        'must': ['技术团队', '运营人员', 'C 端读者'],
        'forbidRaw': [],
    },
    {
        'id': 'r16-06-title-da01',
        'order': 6,
        'name': 'title DA01 标题卡',
        'complaint': '没有原项目的好看',
        'probe': '标题卡的 label / subtitle / chips 三段是否为独立结构且有样式',
        'upstreamNote': True,
        'markdown': '<title type="DA01" label="GUIDE" subtitle="涵盖标题卡片、步骤流程、时间线、对比卡片、'
                    '代码块、提示框等全部 61 个排版组件，每个组件均提供可复制的语法模板与属性说明。" '
                    'chips="公众号排版|长图文|组件化|知识分享">MarkFlow 排版组件完全指南</title>\n',
        'must': ['MarkFlow 排版组件完全指南', 'GUIDE'],
        'forbidRaw': ['<title'],
    },
    {
        'id': 'r16-07-summary',
        'order': 7,
        'name': 'summary 要点回顾',
        'complaint': '每一项的前边缺少列表符号',
        'probe': '有序列表项的 marker（list-style-type / ::marker 内容 / 是否有自绘圆点）',
        'upstreamNote': False,
        'markdown': ':::summary\n'
                    '核心要点回顾：\n'
                    '\n'
                    '1. **模块化排版的本质**是降低读者的认知成本，而非堆砌装饰\n'
                    '2. **61 个排版组件**覆盖从开篇吸引到结尾转化的完整阅读旅程\n'
                    '3. **64 套主题**让你一键切换品牌气质，无需设计背景\n'
                    '4. **导出链路**支持富文本、长图、A4 文档、卡片等多种成品形态\n'
                    '\n'
                    '下一步：打开组件库，挑一个模块试写你的第一段排版。\n'
                    ':::\n',
        'must': ['核心要点回顾', '模块化排版的本质', '下一步'],
        'forbidRaw': [],
    },
    {
        'id': 'r16-08-checklist',
        'order': 8,
        'name': 'checklist 清单',
        'complaint': '每一项的前边都多了一条竖线',
        'probe': '每一行左侧那条竖线是什么元素（border-left / ::before / 字面字符）',
        'upstreamNote': False,
        'markdown': ':::checklist\n'
                    '☐ 确定文章核心观点（一句话能说清） | true\n'
                    '☐ 搭建大纲框架（3-5 个主段落） | true\n'
                    '☐ 为每个段落选择合适的排版模块 | false\n'
                    '☐ 填充正文内容并调整字段 | false\n'
                    '☐ 预览移动端显示效果 | false\n'
                    '☐ 复制富文本到公众号后台 | false\n'
                    ':::\n',
        'must': ['确定文章核心观点', '复制富文本到公众号后台'],
        'forbidRaw': [],
    },
    {
        'id': 'r16-09-table-card',
        'order': 9,
        'name': 'table style=card 表格',
        'complaint': '输出的格式太难看了，行间距太大了，整个表太高了，不如原项目的好',
        'probe': '每行行高、单元格 padding、行内 line-height——并与**上游产物自带的内联样式**逐项对齐',
        'upstreamNote': False,
        'markdown': ':::table style="card" title="四种输出模式对比"\n'
                    '| 输出方式 | 适合场景 | 输出格式 | 特点 |\n'
                    '|----------|----------|----------|------|\n'
                    '| 复制富文本 | 公众号、知乎、语雀 | HTML 内联样式 | 保留完整排版，粘贴即用 |\n'
                    '| 导出长图 | 知识星球、社群传播 | PNG 长图 | 整篇内容一张图，方便转发 |\n'
                    '| A4 文档 | 正式报告、打印交付 | PDF | 自动分页，支持页码页眉 |\n'
                    '| 自由画布 | 网页 PPT、品牌页面 | HTML 源码 | 高度视觉化，可嵌入任意网页 |\n'
                    ':::\n'
                    '\n'
                    '数据来源：MarkFlow 使用统计（2026 年 6 月）\n',
        'must': ['四种输出模式对比', '复制富文本', '自由画布', '数据来源'],
        'forbidRaw': [],
    },
    {
        'id': 'r16-10-infographic',
        'order': 10,
        'name': 'infographic 信息图',
        'complaint': '原项目就没有正确显示，我已经告诉原项目了，你注意观察',
        'probe': 'label / title / subtitle / body 四段是否产出，body 的三行是否分行',
        'upstreamNote': True,
        'markdown': ':::infographic\n'
                    'label: 读者画像\n'
                    'title: 谁在看你的文章\n'
                    'subtitle: 基于 12,000 份问卷的核心发现\n'
                    'body: |\n'
                    '  78% 的读者会在 5 秒内判断是否继续阅读\n'
                    '  排版质量直接影响信任度评分（r=0.71）\n'
                    '  手机端阅读占比 83%，但大多数文章按桌面端设计\n'
                    ':::\n',
        'must': ['读者画像', '谁在看你的文章', '78% 的读者会在 5 秒内判断是否继续阅读'],
        'forbidRaw': [],
    },
    {
        'id': 'r16-11-steps-horizontal',
        'order': 11,
        'name': 'steps-horizontal 横向步骤',
        'complaint': '下列这个每一步的边框没有加圆角',
        'probe': '每个步骤卡的 border-radius（以及是否真有边框）',
        'upstreamNote': False,
        'markdown': ':::steps-horizontal label="HOW IT WORKS" title="从零到发布只需 5 步" '
                    'hint="按顺序完成即可" active="2" color="#2563eb"\n'
                    '- 写作 | 在编辑器中用 Markdown 完成正文和标题层级\n'
                    '- 增强 | 从组件库挑选合适的排版模块，替换字段内容\n'
                    '- 预览 | 右侧实时查看渲染效果，同步调整移动端显示\n'
                    '- 导出 | 一键复制富文本到公众号，或导出长图/PDF\n'
                    '- 发布 | 粘贴到公众号后台，封面和合集设置后即可发布\n'
                    ':::\n',
        'must': ['从零到发布只需 5 步', 'HOW IT WORKS', '粘贴到公众号后台'],
        'forbidRaw': [],
    },
]


def render(markdown):
    request = urllib.request.Request(
        URL, data=json.dumps({'markdown': markdown}).encode(),
        headers={'Content-Type': 'application/json', 'X-Render-Token': TOKEN})
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.load(response)


def stats(html, case):
    text = re.sub(r'\s+', '', re.sub(r'<[^>]+>', '', html))
    raw_nospace = re.sub(r'\s+', '', html)
    inline_styles = re.findall(r'style="([^"]*)"', html)
    return {
        'chars': len(html),
        'textLength': len(text),
        'svg': html.count('<svg'),
        'katex': html.count('class="katex"'),
        'img': html.count('<img'),
        # 「渲染器根本没认」：可见文字里残留字面容器语法（含裸收尾 :::）
        'leakedColon': len(re.findall(r':::', text)),
        # 标签式的字面残留扫原始产物（剥标签会一起洗掉，第七轮踩过）
        'leakedTag': sum(raw_nospace.count(token) for token in case.get('forbidRaw', [])),
        # 上游产物自带的样式声明：第 9 条「行距/高度」的归属判定要用它做对齐比较
        'styleDecls': len(inline_styles),
        'marginDecls': len(re.findall(r'margin[^;:]*:', ' '.join(inline_styles))),
        'paddingDecls': len(re.findall(r'padding[^;:]*:', ' '.join(inline_styles))),
        'lineHeightDecls': len(re.findall(r'line-height\s*:', ' '.join(inline_styles))),
        'borderDecls': len(re.findall(r'border(?:-[a-z]+)?\s*:', ' '.join(inline_styles))),
        'radiusDecls': len(re.findall(r'border-radius\s*:', ' '.join(inline_styles))),
        'backgroundDecls': len(re.findall(r'background(?:-color)?\s*:', ' '.join(inline_styles))),
        'listStyleDecls': len(re.findall(r'list-style[^;:]*:', ' '.join(inline_styles))),
    }


records = []
for case in CASES:
    response = render(case['markdown'])
    html = response.get('html') or ''
    with open(os.path.join(R16, case['id'] + '.md'), 'w', encoding='utf-8') as handle:
        handle.write(case['markdown'])
    with open(os.path.join(R16, case['id'] + '.html'), 'w', encoding='utf-8') as handle:
        handle.write(html)

    backend = stats(html, case)
    text = re.sub(r'\s+', '', re.sub(r'<[^>]+>', '', html))
    missingMust = [item for item in case['must'] if re.sub(r'\s+', '', item) not in text]
    records.append({
        'id': case['id'], 'order': case['order'], 'name': case['name'],
        'complaint': case['complaint'], 'probe': case['probe'],
        'upstreamNote': case['upstreamNote'],
        'label': case['name'], 'syntax': case['name'],
        'inputChars': len(case['markdown']), 'must': case['must'],
        'forbidRaw': case['forbidRaw'], 'missingMust': missingMust,
        'ok': bool(response.get('ok')),
        'warnings': (response.get('meta') or {}).get('warnings') or [],
        'backend': backend,
    })
    print('%-26s chars=%-6d text=%-4d svg=%-3d img=%-3d ::: %-3d tagResidue=%-3d %s'
          % (case['id'], backend['chars'], backend['textLength'], backend['svg'],
             backend['img'], backend['leakedColon'], backend['leakedTag'],
             'OK' if not missingMust and not backend['leakedColon'] and not backend['leakedTag']
             else 'CHECK ' + str(missingMust)))

payload = {
    'cases': records,
    'summary': {
        'cases': len(records),
        'backendRecognised': sum(1 for r in records
                                 if not r['missingMust'] and not r['backend']['leakedColon']
                                 and not r['backend']['leakedTag']),
    },
}
with open(os.path.join(R16, 'r16.json'), 'w', encoding='utf-8') as handle:
    handle.write(json.dumps(payload, ensure_ascii=False, indent=1))
print('summary:', payload['summary'])
