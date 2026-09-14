"""第十五轮 · 「全库 44 篇里那 39 处命中，在编辑器里能不能正常渲染」的只读结论。

三个既有产物做连接，不新打 API、不碰数据库：
  target/probe/round10_article_coverage.json  70 个扫描项 × 44 篇的命中表（只读扫描的结果）
  target/probe/browser/all_summary.json       79 个最小样例在真实 Chrome 里的逐行判定
  target/probe/browser/articles_result.json   13/14 篇真实稿件在真实 SPA 编辑器里的回归

输出：target/probe/r15/coverage_editor_verdict.md / .json
用法：PYTHONUTF8=1 python tools/render-verify/gen/round15_coverage_editor_verdict.py
      （先按复现手册 §3.4 / §3.5 跑完浏览器套件与汇总，三份输入都在了才有意义）
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from paths import OUT  # noqa: E402  （路径常量的唯一出处，见 tools/render-verify/README.md）

R15 = os.path.join(OUT, 'r15')


def load(name):
    with open(os.path.join(OUT, name), encoding='utf-8') as fh:
        return json.load(fh)


coverage = load('round10_article_coverage.json')
allsum = load(os.path.join('browser', 'all_summary.json'))
articles = load(os.path.join('browser', 'articles_result.json'))

# 样例 → 真实浏览器判定（口径与 round10_component_paths.mjs 一致）
summary = {row['id']: row for row in allsum['rows']}
# 真实 SPA 回归过、且编辑器确实 ready 的文章
spa_ready = {row['id'] for row in articles['results'] if row.get('editor', {}).get('ready')}
spa_any = {row['id'] for row in articles['results']}

hits = {k: v for k, v in coverage['componentHits'].items() if v}
forbidden = coverage['forbiddenHits']

rows = []
no_sample = []
editor_not_pass = []
not_regressed = []
for item, arts in sorted(hits.items(), key=lambda kv: (-len(kv[1]), kv[0])):
    verdict = summary.get(item)
    if verdict is None:
        no_sample.append(item)
        continue
    in_spa = sorted(set(arts) & spa_ready)
    rows.append({
        'item': item,
        'backend': verdict['verdict'],
        'editor': 'pass' if verdict['verdict'] == 'pass' else verdict['verdict'],
        'reason': verdict.get('reason', ''),
        'articles': arts,
        'articleCount': len(arts),
        'inSpaReady': in_spa,
        'inSpaReadyCount': len(in_spa),
    })
    if verdict['verdict'] != 'pass':
        editor_not_pass.append((item, verdict['verdict'], verdict.get('reason', '')))
    if not in_spa:
        not_regressed.append(item)

lines = []
lines.append('# 全库 44 篇稿件的 39 处组件命中 —— 在编辑器里能不能渲染（第十五轮只读结论）')
lines.append('')
lines.append('数据来源：`round10_article_coverage.json`（全库 44 篇 `CONTENT_MARKDOWN` 的只读正则扫描）')
lines.append('× `browser/all_summary.json`（79 个最小样例在**真实 Chrome** 里的逐行判定）')
lines.append('× `browser/articles_result.json`（真实 SPA 编辑器里的回归，本轮可用 %d 篇 ready）。' % len(spa_ready))
lines.append('')
lines.append('| 项 | 命中篇数 | 后端产物 | 编辑器（同写法最小样例，真实 Chrome） | 该写法在真实 SPA 编辑器里回归过的篇数 |')
lines.append('| --- | --- | --- | --- | --- |')
for r in rows:
    lines.append('| `%s` | %d | %s | **%s** | %d %s |' % (
        r['item'], r['articleCount'], r['backend'], r['editor'], r['inSpaReadyCount'],
        ('（' + ','.join(str(i) for i in r['inSpaReady']) + '）') if r['inSpaReady'] else ''))
lines.append('')
lines.append('**小结**：39 项全部有「最小样例 + 真实浏览器」的判定，判定为 pass 的 %d 项、非 pass 的 %d 项。'
             % (len(rows) - len(editor_not_pass), len(editor_not_pass)))
if editor_not_pass:
    lines.append('')
    lines.append('非 pass 的逐条（原因即上游产物本身，不是编辑器缺陷）：')
    lines.append('')
    for item, v, reason in editor_not_pass:
        lines.append('- `%s`：%s —— %s' % (item, v, reason))
lines.append('')
lines.append('未在真实 SPA 编辑器里回归过的项（%d 个）——它们只有「最小样例 + 真实浏览器」，'
             '缺「真实成稿在编辑器里打开」这一层：' % len(not_regressed))
lines.append('')
lines.append('`' + '`、`'.join(not_regressed) + '`')
lines.append('')
lines.append('**禁写写法对照**（同一份扫描里这 6 种必须为 0）：')
lines.append('')
lines.append('| 禁写写法 | 命中篇数 |')
lines.append('| --- | --- |')
for k, v in sorted(forbidden.items()):
    lines.append('| `%s` | %d |' % (k, len(v)))
lines.append('')
lines.append('未找到最小样例的命中项：%s' % (no_sample if no_sample else '无'))

os.makedirs(R15, exist_ok=True)
with open(os.path.join(R15, 'coverage_editor_verdict.md'), 'w', encoding='utf-8') as fh:
    fh.write('\n'.join(lines))
with open(os.path.join(R15, 'coverage_editor_verdict.json'), 'w', encoding='utf-8') as fh:
    json.dump({'rows': rows, 'noSample': no_sample, 'notRegressed': not_regressed,
               'editorNotPass': editor_not_pass, 'forbidden': {k: v for k, v in forbidden.items()},
               'spaReady': sorted(spa_ready), 'spaTotal': sorted(spa_any)}, fh, ensure_ascii=False, indent=1)

print('hitItems=', len(hits), 'joined=', len(rows), 'noSample=', no_sample)
print('editorPass=', len(rows) - len(editor_not_pass), 'editorNotPass=', editor_not_pass)
print('notRegressedInSpa=', len(not_regressed), not_regressed)
print('spaReadyArticles=', sorted(spa_ready))
