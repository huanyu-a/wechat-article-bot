"""第十轮 · 每个组件在**真实稿件**里出现过没有（只读）。

第十轮要区分三个验证层级：真实稿件验过 / 最小样例在真实浏览器里验过 / 只跑过 jsdom。
前两层好证，第三层要有反证——本脚本就是「真实稿件」那一层的证据来源：

把全库 44 篇（含软删）的 `CONTENT_MARKDOWN` 拉下来（`target/probe/round10_articles.tsv`，
由 `docker exec … mysql -N -B` 只读导出），对每个组件按它的**规范写法**扫一遍，
给出「哪些文章里真的写过这个组件」。

这一步只读，不改任何数据。用法：python tools/render-verify/gen/round10_article_coverage.py
产物：target/probe/round10_article_coverage.json、target/probe/round10_article_coverage.md
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from paths import OUT  # noqa: E402, read_token

# 组件的「规范写法」在真实稿件里长什么样（正则都带行首/词边界，避免子串误命中）。
PATTERNS = {
    'md-heading': r'(?m)^#{1,6}\s',
    'md-list': r'(?m)^\s*[-*+]\s',
    'md-ordered': r'(?m)^\s*\d+\.\s',
    'md-task-list': r'(?m)^\s*[-*+]\s*\[[ xX]\]',
    'md-quote': r'(?m)^>\s',
    'md-table': r'(?m)^\|.+\|',
    'md-hr': r'(?m)^-{3,}\s*$',
    'md-code': r'```',
    'md-image': r'!\[[^\]]*\]\(',
    'md-image-sized': r'!\[[^\]]*\]\([^)]+\)\[[^\]]+\]',
    'md-multi-image': r'<\s*!\[',
    'md-link': r'\[[^\]]+\]\(https?://',
    'in-em-hl': r'==[^=\n]+==',
    'in-pill': r'!![^!\n]+!!',
    'in-indigo': r'\^\^[^\^\n]+\^\^',
    'in-glow': r'(?<!:):{2}[^:\n]+:{2}(?!:)',
    'in-bold': r'\*\*[^*\n]+\*\*',
    'in-bold-italic': r'\*\*\*[^*\n]+\*\*\*',
    'in-italic': r'(?<!\*)\*[^*\n]+\*(?!\*)',
    'in-underline-accent': r'__[^_\n]+__',
    'in-underline-html': r'<u>',
    'in-strike': r'~~[^~\n]+~~',
    'in-sub': r'~[^~\n]+~',
    'in-sup': r'\^[^\^\n]+\^',
    'in-code': r'`[^`\n]+`',
    'in-badge': r'<badge\b',
    'in-icon': r'<icon\b',
    'callout-quote-tip': r'(?mi)^>\s*\[!?(?:TIP|NOTE|INFO|WARNING|CAUTION|IMPORTANT)\]',
    'callout-container': r':::callout\b',
    'blk-title': r'<title\b',
    'blk-p-title': r'<p-title\b',
    'blk-cta': r'<cta\b',
    'ctn-cta': r':::cta\b',
    'blk-badges': r'<badges\b',
    'blk-statement': r'<statement\b',
    'blk-lead': r'<lead\b',
    'ctn-lead': r':::lead\b',
    'blk-engage-label': r'<engage-label\b',
    'blk-engage-card': r'<engage-card\b',
    'blk-engage-tag': r'<engage\b',
    'blk-img': r'<img\b',
    'blk-steps-2': r'<steps\b',
    'blk-timeline': r'<timeline\b',
    'ctn-timeline': r':::timeline\b',
    'blk-slider': r'<slider\b',
    'ctn-slider': r':::slider\b',
    'blk-case-flow': r'<case-flow\b',
    'ctn-case-flow': r':::case-flow\b',
    'blk-gov-header': r'<gov-header\b',
    'ctn-breaking': r':::breaking\b',
    'ctn-reading-path': r':::reading-path\b',
    'ctn-steps-h': r':::steps-horizontal\b',
    'ctn-steps-v': r':::steps-vertical\b',
    'ctn-table': r':::table\b',
    'ctn-compare': r':::compare\b',
    'ctn-code-block': r':::code-block\b',
    'ctn-align': r':::align\b',
    'ctn-hint': r':::hint\b',
    'ctn-note': r':::note\b',
    'ctn-tip': r':::tip\b',
    'ctn-info': r':::info\b',
    'ctn-warning': r':::warning\b',
    'ctn-caution': r':::caution\b',
    'ctn-important': r':::important\b',
    'diagram-mermaid': r'```mermaid',
    'math-inline': r'\$[^$\n]+\$',
    'math-block': r'\$\$',
    'attr-table-title': r':::table\b[^\n]*title=',
    'attr-callout-title': r':::callout\b[^\n]*title=',
    'attr-compare-marker-cn': r':::compare\b[\s\S]{0,600}?(accent|强调|默认)',
}

# 「禁写」的写法在真实稿件里出现过没有（期望都是 0，那是最强的反证）。
FORBIDDEN = {
    'layout-* 家族': r'layout-[a-z]',
    ':::success': r':::success\b',
    ':::danger': r':::danger\b',
    '<hint> 标签式': r'<hint\b',
    '<slider/> 自闭合': r'<slider\b[^>]*/>',
    'case-flow 行首漏 -': r'(?m)^<case-flow\b[\s\S]*?^[^-\s<]',
}

rows = []
for line in open(os.path.join(OUT, 'round10_articles.tsv'), encoding='utf-8'):
    parts = line.rstrip('\n').split('\t')
    if len(parts) < 4:
        continue
    article_id, deleted, engine, markdown = parts[0], parts[1], parts[2], parts[3]
    # mysql 批处理模式把换行转义成字面 \n，还原回来再扫
    markdown = markdown.replace('\\n', '\n').replace('\\t', '\t').replace('\\\\', '\\')
    rows.append({'id': int(article_id), 'deleted': deleted == '1', 'engine': engine, 'markdown': markdown})

print('articles =', len(rows))

hits = {}
for key, pattern in PATTERNS.items():
    compiled = re.compile(pattern)
    matched = [row['id'] for row in rows if compiled.search(row['markdown'])]
    hits[key] = matched

forbid_hits = {}
for key, pattern in FORBIDDEN.items():
    compiled = re.compile(pattern)
    matched = [row['id'] for row in rows if compiled.search(row['markdown'])]
    forbid_hits[key] = matched

payload = {
    'articles': {'total': len(rows), 'deleted': sum(1 for row in rows if row['deleted']),
                 'markflow': sum(1 for row in rows if row['engine'] == 'MARKFLOW')},
    'componentHits': hits,
    'forbiddenHits': forbid_hits,
}
open(os.path.join(OUT, 'round10_article_coverage.json'), 'w', encoding='utf-8').write(
    json.dumps(payload, ensure_ascii=False, indent=1))

lines = ['# 真实稿件里出现过哪些组件（全库 %d 篇，含软删 %d 篇）'
         % (payload['articles']['total'], payload['articles']['deleted']), '',
         '| 组件（最小样例 id） | 命中篇数 | 文章 ID |', '| --- | --- | --- |']
for key in sorted(hits, key=lambda k: (-len(hits[k]), k)):
    lines.append('| `%s` | %d | %s |' % (key, len(hits[key]),
                                         ', '.join(str(i) for i in hits[key]) or '—'))
lines += ['', '## 禁写写法在真实稿件里的命中（期望全 0）', '',
          '| 写法 | 命中篇数 | 文章 ID |', '| --- | --- | --- |']
for key in sorted(forbid_hits):
    lines.append('| %s | %d | %s |' % (key, len(forbid_hits[key]),
                                       ', '.join(str(i) for i in forbid_hits[key]) or '—'))
open(os.path.join(OUT, 'round10_article_coverage.md'), 'w', encoding='utf-8').write('\n'.join(lines))

print('--- components present in real articles ---')
for key in sorted(hits, key=lambda k: (-len(hits[k]), k)):
    if hits[key]:
        print('%-24s %2d  %s' % (key, len(hits[key]), ','.join(str(i) for i in hits[key][:12])))
print('--- components NOT present ---')
print(', '.join(k for k in sorted(hits) if not hits[k]) or '(none)')
print('--- forbidden ---')
for key in sorted(forbid_hits):
    print('%-24s %2d  %s' % (key, len(forbid_hits[key]), ','.join(str(i) for i in forbid_hits[key][:12])))
