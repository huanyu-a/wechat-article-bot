"""第二十轮 · §R10 的反向自查：`|` 到底是「引擎约定」还是「infographic 的实现缺陷」？

背景：第十八轮把 `:::infographic` 的 `body` 换写法后会整块丢正文这件事立成了 §R10，
定性写的是「渲染缺陷」。第二十轮要求对自己上一轮的结论持怀疑态度——因为
**`|` 本来就是 YAML 块标量的必需语法，缺了它按字面字符串处理是完全合理的**。
如果是这样，那 (b) 属「用户写错了」而不是「上游缺陷」，§R10 必须降级。

本脚本就是那次证伪尝试，分两问，各产出**可被反证**的数字：

问一（`_question1`，产物 `r10_by_design.json`）
    这是 infographic 一个组件的毛病，还是引擎对多行字段的统一约定？
    判据：拿 4 个都带 `body:` 键的组件（`infographic` / `verdict` / `notice` / `image-text`），
    每个各试 3 种写法。若 4 个组件**表现完全一致**，就是引擎级约定。

问二（`_question2`，产物 `r10_blockscalar.json`）
    `|` 是通用块标量，还是只对 `body` 生效的硬编码？
    判据：把块标量挪到 `label` / `title` 上、并试 `>` / `|-` / 4 空格缩进 / 带冒号的正文行，
    逐条看是否与 YAML 块标量语义一致。

结论（第二十轮实测）：两问都指向**统一约定**——4 个组件表现完全一致；`|`/`>`/`|-` 在
`label`/`title`/`body` 上一律生效；带冒号的正文行原样保住（说明缩进行是被真正的块标量读取器
整体吃掉的）。**因此缺 `|` 属「写法不合约定」，§R10 降级为「文档未覆盖 + 静默失败」（P3），
不主张上游渲染有 bug。**

只调渲染 API，不落库、不改生产数据。
用法：python tools/render-verify/gen/round20_field_block_scalar.py
产物：target/probe/r16/r10_by_design.json、target/probe/r16/r10_blockscalar.json
"""
import json
import os
import re
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from paths import OUT, TOKEN_FILE, RENDER_URL  # noqa: E402, read_token

TOKEN = read_token()

BODY_1 = '第一行内容甲乙丙'
BODY_2 = '第二行内容丁戊己'

INFO_HEAD = (':::infographic\nlabel: 读者画像\ntitle: 谁在看你的文章\n'
             'subtitle: 基于 12,000 份问卷的核心发现\n')


def render(markdown):
    request = urllib.request.Request(
        RENDER_URL, data=json.dumps({'markdown': markdown}).encode('utf-8'),
        headers={'Content-Type': 'application/json', 'X-Render-Token': TOKEN})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.loads(response.read().decode('utf-8'))


def dump(name, rows):
    path = os.path.join(OUT, 'r16', name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(rows, fh, ensure_ascii=False, indent=1)
    print(f'产物：{path}')


def _question1():
    """问一：4 个带 body 的组件 × 3 种写法，看是不是同一个约定。"""
    variants = [
        ('infographic', 'inline 单行（body: 文本）',
         f':::infographic\nlabel: 读者画像\ntitle: 谁在看你的文章\nbody: {BODY_1}\n:::\n'),
        ('infographic', '| + 缩进（引擎示例写法）',
         f'{INFO_HEAD}body: |\n  {BODY_1}\n  {BODY_2}\n:::\n'),
        ('infographic', '无 | + 缩进（被质疑的写法）',
         f'{INFO_HEAD}body:\n  {BODY_1}\n  {BODY_2}\n:::\n'),

        ('verdict', 'inline 单行（body: 文本）',
         f':::verdict\nlabel: 最终判断\ntitle: 排版的本质\nbody: {BODY_1}\n:::\n'),
        ('verdict', '| + 缩进',
         f':::verdict\nlabel: 最终判断\ntitle: 排版的本质\nbody: |\n  {BODY_1}\n  {BODY_2}\n:::\n'),
        ('verdict', '无 | + 缩进（被质疑的写法）',
         f':::verdict\nlabel: 最终判断\ntitle: 排版的本质\nbody:\n  {BODY_1}\n  {BODY_2}\n:::\n'),

        ('notice', 'inline 单行（body: 文本）',
         f':::notice\ntitle: 主题系统 v2.0 已上线\nbody: {BODY_1}\n:::\n'),
        ('notice', '| + 缩进',
         f':::notice\ntitle: 主题系统 v2.0 已上线\nbody: |\n  {BODY_1}\n  {BODY_2}\n:::\n'),
        ('notice', '无 | + 缩进（被质疑的写法）',
         f':::notice\ntitle: 主题系统 v2.0 已上线\nbody:\n  {BODY_1}\n  {BODY_2}\n:::\n'),

        ('image-text', 'inline 单行（body: 文本）',
         ':::image-text\nsrc: https://robocopmao.github.io/r-markdown/banner4.webp\n'
         f'title: 移动端阅读体验优化\nbody: {BODY_1}\n:::\n'),
        ('image-text', '| + 缩进',
         ':::image-text\nsrc: https://robocopmao.github.io/r-markdown/banner4.webp\n'
         f'title: 移动端阅读体验优化\nbody: |\n  {BODY_1}\n  {BODY_2}\n:::\n'),
        ('image-text', '无 | + 缩进（被质疑的写法）',
         ':::image-text\nsrc: https://robocopmao.github.io/r-markdown/banner4.webp\n'
         f'title: 移动端阅读体验优化\nbody:\n  {BODY_1}\n  {BODY_2}\n:::\n'),
    ]

    rows = []
    print('=== 问一：4 组件 × 3 写法')
    for component, how, markdown in variants:
        payload = render(markdown)
        html = payload.get('html') or ''
        text = payload.get('text') or re.sub(r'<[^>]+>', '', html)
        row = {
            'component': component, 'how': how, 'markdown': markdown, 'html': html,
            'chars': len(html),
            'line1': BODY_1 in html, 'line2': BODY_2 in html,
            'literalKeys': len(re.findall(r'(label|title|body)\s*[:：]', text)),
            'warnings': (payload.get('meta') or {}).get('warnings'),
        }
        rows.append(row)
        print(f"{component:12s} {how:26s} 字符={row['chars']:5d} 第1行={int(row['line1'])} "
              f"第2行={int(row['line2'])} 字面键名={row['literalKeys']} warnings={row['warnings']}")
    print()
    return rows


def _question2():
    """问二：`|` 是通用块标量还是只认 body 的硬编码。"""
    cases = [
        ('P1 基准 body: | + 2 空格缩进',
         INFO_HEAD + f'body: |\n  {BODY_1}\n  {BODY_2}\n:::\n', [BODY_1, BODY_2]),
        ('P2 正文行里带半角冒号',
         INFO_HEAD + f'body: |\n  {BODY_1}: 后段\n  {BODY_2}\n:::\n', [f'{BODY_1}: 后段', BODY_2]),
        ('P3 正文行里带全角冒号',
         INFO_HEAD + f'body: |\n  {BODY_1}：后段\n  {BODY_2}\n:::\n', [f'{BODY_1}：后段', BODY_2]),
        ('P4 label 上试 block scalar',
         ':::infographic\ntitle: 谁在看你的文章\nsubtitle: 基于 12,000 份问卷的核心发现\n'
         f'label: |\n  标签甲\n  标签乙\nbody: |\n  {BODY_1}\n  {BODY_2}\n:::\n',
         ['标签甲', '标签乙', BODY_1, BODY_2]),
        ('P5 title 上试 block scalar',
         ':::infographic\nlabel: 读者画像\nsubtitle: 基于 12,000 份问卷的核心发现\n'
         f'title: |\n  标题甲\n  标题乙\nbody: |\n  {BODY_1}\n  {BODY_2}\n:::\n',
         ['标题甲', '标题乙', BODY_1, BODY_2]),
        ('P6 body: > 折叠标量',
         INFO_HEAD + f'body: >\n  {BODY_1}\n  {BODY_2}\n:::\n', [BODY_1, BODY_2]),
        ('P7 body: |- 去尾换行',
         INFO_HEAD + f'body: |-\n  {BODY_1}\n  {BODY_2}\n:::\n', [BODY_1, BODY_2]),
        ('P8 body: | + 4 空格缩进',
         INFO_HEAD + f'body: |\n    {BODY_1}\n    {BODY_2}\n:::\n', [BODY_1, BODY_2]),
        ('P9 body: | 后面又跟一个非缩进字段',
         INFO_HEAD + f'body: |\n  {BODY_1}\n  {BODY_2}\nnote: 尾巴\n:::\n', [BODY_1, BODY_2]),
        ('P10 body: 同行给首行 + 下一行缩进',
         INFO_HEAD + f'body: {BODY_1}\n  {BODY_2}\n:::\n', [BODY_1, BODY_2]),
        ('P11 body: | 后紧跟 ::: 收尾（无正文）',
         INFO_HEAD + 'body: |\n:::\n', []),
    ]

    rows = []
    print('=== 问二：块标量边界（11 组）')
    for name, markdown, expect in cases:
        payload = render(markdown)
        html = payload.get('html') or ''
        text = re.sub(r'<[^>]+>', '', html)
        hit = [e for e in expect if e in html]
        missing = [e for e in expect if e not in html]
        row = {
            'case': name, 'markdown': markdown, 'html': html, 'chars': len(html),
            'expect': expect, 'hit': hit, 'missing': missing,
            'literalColon': len(re.findall(r'(label|title|subtitle|body|note)\s*[:：]', text)),
            'warnings': (payload.get('meta') or {}).get('warnings'),
        }
        rows.append(row)
        verdict = 'OK' if not missing else 'MISS'
        print(f'{verdict:4s} {name:30s} 字符={row["chars"]:5d} 期望={len(expect)} 命中={len(hit)} '
              f'缺={missing!r} 字面键名={row["literalColon"]} warnings={row["warnings"]}')
    print()
    return rows


if __name__ == '__main__':
    dump('r10_by_design.json', _question1())
    dump('r10_blockscalar.json', _question2())
