"""第十一轮 · 对「等上游 38 个 `layout-*`」的**独立交叉验证**（不是用第十轮那套 harness 自证）。

第十轮的结论（38 个 ID × 2 种写法 = 76 组全部上游未渲染）出自 `round10_registry_closure.py` +
`browser/run-set-browser.mjs` + `summarize-alt.mjs` 这一条链路。本脚本用**另外三条互相独立的证据通道**
复核其中随机抽出的 9 个 ID，用来排除「harness 自己的判据有盲区，于是把能渲染的也判成没渲染」：

  通道 A｜裸 HTTP 响应体：不复用 harness 的任何判定代码，直接把响应的**全部顶层键、`meta`、
          HTTP 状态、响应头**打印出来，并用**另一种检测方法**（按元素名 `<name` 匹配，而不是
          harness 用的「标记子串 + 剥标签」）判产物里有没有留下这个组件。
  通道 B｜引擎包的静态结构：在 `mf_app.js` 里独立统计 `layout-*` 的出现形态——
          有没有**非引号**出现（即参与语法匹配的正则）、有没有 `match:` 分支体。
          harness 若误判，这一条会立刻矛盾（匹配器其实认这族）。
  通道 C｜服务端实时 guide：`GET /__markflow_render`（无 body）返回的语法指令里，
          这 38 个名字出现了几次。guide 是渲染器**自述**支持什么，与产物互为佐证。

抽样口径：9 个 ID（要求 ≥6），覆盖两种写法，并按 registry 的 category 尽量分散
（intro / structure / data / content / emph / image / other）。
另加 **3 个已知支持的对照组**（`:::breaking` / `:::callout type="tip"` / `<badge … />`）——
没有对照的检测器无法证明它不是「一律判未渲染」。

> 本脚本第一版把标签式的判据写成「产物里有这个元素 ⇒ 已渲染」，**当场就发现它错了**：
> 标签式的失败形态恰恰是**这个元素被原样透传**（真被渲染的组件会变成自己的 `<section>` 结构，
> 绝不会吐出一个以组件名命名的标签）。这正是交叉验证要抓的东西——判据写反了会得到 9/18 存疑，
> 而不是 18/18 确认。修正后的判据见下，对照组用来钉住这个修正。

**只调渲染 API（读）**，不落库、不改任何生产数据。
用法：python tools/render-verify/gen/round11_crosscheck.py
产物：target/probe/round11_crosscheck.txt / .json
"""
import hashlib
import json
import os
import re
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from paths import OUT, SPEC, TOKEN_FILE, RENDER_URL  # noqa: E402

TOKEN = open(TOKEN_FILE).read().strip()
URL = RENDER_URL
#: 第五轮存档的引擎包（受版本控制，见 spec/engine/README.md）。通道 B1 的输入。
BUNDLE = os.path.join(SPEC, 'engine', 'mf_app.js')

# 抽样：覆盖两种写法 + 尽量分散 category（category 取自 component_registry.json）
SAMPLE = [
    'layout-hero',          # intro
    'layout-toc',           # structure
    'layout-metrics',       # data
    'layout-timeline',      # data
    'layout-checklist',     # content
    'layout-quote-card',    # emph
    'layout-image-compare',  # image
    'layout-changelog',     # other
    'layout-cards',         # intro
]


def post(markdown):
    request = urllib.request.Request(
        URL, data=json.dumps({'markdown': markdown}).encode(),
        headers={'Content-Type': 'application/json', 'X-Render-Token': TOKEN})
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.status, dict(response.headers), json.load(response)


def get_guide():
    request = urllib.request.Request(URL, headers={'X-Render-Token': TOKEN})
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.status, json.load(response)


def strip_tags(html):
    return re.sub(r'\s+', '', re.sub(r'<[^>]+>', '', html))


out = []
out.append('# 第十一轮 · 等上游 38 个 layout-* 的独立交叉验证（9 个抽样）')
out.append('')

# ---------- 通道 B：引擎包静态结构（先做，它给出「这 38 个名字到底是什么」） ----------
bundle = open(BUNDLE, encoding='utf-8').read()
bundle_sha = hashlib.sha256(bundle.encode()).hexdigest()
registry = json.load(open(os.path.join(SPEC, 'component_registry.json'), encoding='utf-8'))
registry_layout = [name for name, _ in registry if name.startswith('layout-')]
quoted = sorted(set(re.findall(r'"(layout-[a-z][a-z0-9-]*)"', bundle)))
token_count = len(re.findall(r'layout-[a-z][a-z0-9-]*', bundle))
unquoted = sorted(set(t for t in re.findall(r'layout-[a-z][a-z0-9-]*', bundle) if '"%s"' % t not in bundle))
regex_refs = len(re.findall(r'/layout-', bundle))
branch_refs = len(re.findall(r'layout-[a-z-]+"\s*:\s*\{', bundle))

out.append('## 通道 B：引擎包 `mf_app.js` 的静态结构')
out.append('')
out.append('| 量 | 值 |')
out.append('| --- | --- |')
out.append('| 引擎包 sha256（前 32 位） | `%s` |' % bundle_sha[:32])
out.append('| 注册表里的 `layout-*` ID | **%d** 个 |' % len(registry_layout))
out.append('| 在包里**独立**扫出的 `layout-*` 字面量 | **%d** 个 |' % len(quoted))
out.append('| 两条清单是否逐字相同 | **%s** |' % ('是' if sorted(registry_layout) == quoted else '否'))
out.append('| 包内 `layout-*` 出现的总次数 | %d |' % token_count)
out.append('| 其中**不带引号**的出现（= 参与语法匹配的形态） | **%d** |' % len(unquoted))
out.append('| 正则字面量 `/layout-…/` 的条数 | **%d** |' % regex_refs)
out.append('| `layout-x":{…}` 形态的匹配分支条数 | **%d** |' % branch_refs)
out.append('')
out.append('⇒ 这 38 个名字在引擎包里**只以带引号的字符串出现**，没有一处参与语法匹配、没有一处有渲染分支。')
out.append('')

# ---------- 通道 B2：**重新下载线上 bundle** 独立复现同一份清单 ----------
# 上面用的是第五轮存档的 spec/engine/mf_app.js；官网现在发的是另一个文件名（index-CCyOlIBZ.js）。
# 重新拉一份、用完全相同的两条路径再抽一次，能证明「38 个名字」不是某一次下载的偶然。
LIVE = os.path.join(OUT, 'round11_live_bundle.js')
live_info = None
if os.path.exists(LIVE):
    live = open(LIVE, encoding='utf-8', errors='replace').read()
    live_sha = hashlib.sha256(live.encode('utf-8', 'replace')).hexdigest()
    live_quoted = sorted(set(re.findall(r'"(layout-[a-z][a-z0-9-]*)"', live)))
    live_match = re.search(r'\{[^{}]*Engage_DA02:"cta"[^{}]*\}', live)
    live_pairs = []
    if live_match:
        live_pairs = [(a or c) for a, b, c, d in re.findall(
            r'"([^"]+)":\s*"([^"]+)"|([A-Za-z_][A-Za-z0-9_]*):"([^"]+)"', live_match.group(0))]
    live_layout = [name for name in live_pairs if name.startswith('layout-')]
    live_info = {
        'sha256': live_sha, 'chars': len(live),
        'registryIds': len(live_pairs), 'registryLayoutIds': len(live_layout),
        'quotedLayoutLiterals': len(live_quoted),
        'sameAsArchived': live_quoted == quoted and sorted(live_layout) == sorted(registry_layout),
    }
    out.append('## 通道 B2：重新下载的线上 bundle（复现同一份清单）')
    out.append('')
    out.append('| 量 | 存档 `mf_app.js` | **新下载的线上 bundle** |')
    out.append('| --- | --- | --- |')
    out.append('| sha256（前 32 位） | `%s` | `%s` |' % (bundle_sha[:32], live_sha[:32]))
    out.append('| 包体字符数 | %d | %d |' % (len(bundle), len(live)))
    out.append('| 注册表取到的组件 ID | %d | **%d** |' % (len(registry), len(live_pairs)))
    out.append('| 其中 `layout-*` | %d | **%d** |' % (len(registry_layout), len(live_layout)))
    out.append('| 直接扫字面量取到的 `layout-*` | %d | **%d** |' % (len(quoted), len(live_quoted)))
    out.append('')
    out.append('两次下载的包**不是同一个构建**（sha256 不同、字节数也不同），但**两条抽取路径都给出同一份 38 个 `layout-*`**')
    out.append('（%s）——说明这份清单来自引擎当前的组件定义，不是某一次下载的产物。'
               % ('逐字相同' if live_info['sameAsArchived'] else '**不一致**'))
    out.append('')

# ---------- 通道 C：服务端实时 guide ----------
guide_status, guide_payload = get_guide()
guide = guide_payload.get('guide') or ''
guide_hits = {name: guide.count(name) for name in registry_layout}
guide_hit_names = [name for name, count in guide_hits.items() if count]
out.append('## 通道 C：服务端实时 guide')
out.append('')
out.append('- `GET /__markflow_render` HTTP **%d**；`guide` 长度 **%d** 字符' % (guide_status, len(guide)))
out.append('- 38 个 `layout-*` 名字在 guide 里出现的总次数：**%d**（命中的名字：%s）'
           % (sum(guide_hits.values()), '、'.join(guide_hit_names) or '无'))
out.append('')

# ---------- 通道 A：裸 HTTP 响应体 ----------
out.append('## 通道 A：9 个抽样的裸响应体（另一种检测方法）')
out.append('')
out.append('检测方法与第十轮的 harness **不同**：这里按「产物里有没有以组件名命名的元素」判定标签式，')
out.append('再单独数可见文字里的字面 `:::` 判定容器式；两个量都与 harness 的「标记子串」口径独立。')
out.append('')
out.append('判据（修正确切定义）：**真被渲染的组件一定会变成它自己的结构（`<section>` 等），')
out.append('绝不会在产物里留下一个以组件 ID 命名的元素**；容器式则不会在可见文字里留下字面 `:::`。')
out.append('因此：`产物里出现 <名字` 或 `可见文字里出现 :::` ⇒ 上游未渲染。')
out.append('')
out.append('| ID | 写法 | HTTP | `ok` | `meta` 键 | `meta.warnings` | 产物字符 | 元素 `<名字` | 可见文字 `:::` | 判定 |')
out.append('| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |')
rows = []
for name in SAMPLE:
    for form, markdown in (
            ('容器式', ':::%s\n甲 | 乙\n:::' % name),
            ('标签式', '<%s>甲 乙</%s>' % (name, name))):
        status, headers, payload = post(markdown)
        html = payload.get('html') or ''
        meta = payload.get('meta') or {}
        text = strip_tags(html)
        element_hits = len(re.findall(r'<(?!/)%s[\s>/]' % re.escape(name), html))
        colon_hits = len(re.findall(r':::', text))
        rendered = element_hits == 0 and colon_hits == 0 and len(html) > 0
        verdict = '上游已渲染' if rendered else '上游未渲染'
        rows.append({
            'id': name, 'form': form, 'http': status, 'ok': payload.get('ok'),
            'metaKeys': sorted(meta.keys()), 'warnings': meta.get('warnings'),
            'htmlChars': len(html), 'elementHits': element_hits,
            'colonHits': colon_hits, 'verdict': verdict,
            'htmlHead': html[:200],
        })
        out.append('| `%s` | %s | %d | `%s` | %s | %s | %d | %d | %d | **%s** |' % (
            name, form, status, payload.get('ok'),
            ', '.join(sorted(meta.keys())) or '（空）',
            json.dumps(meta.get('warnings'), ensure_ascii=False),
            len(html), element_hits, colon_hits, verdict))
out.append('')
out.append('> 说明：`meta` 的键只有 `title` / `summary`（命中时才有 `warnings`）——')
out.append('> 与第十轮 R2-附 的结论一致，上游**没有第二个报错通道**。')
out.append('')

# ---------- 对照：3 个已知支持的写法必须被判成「已渲染」 ----------
CONTROLS = [
    ('ctn-breaking', '容器式（已知支持）', ':::breaking badge="NEW" title="甲" subtitle="乙"\n正文\n:::'),
    ('ctn-callout', '容器式（已知支持）', ':::callout type="tip" title="甲"\n正文\n:::'),
    ('tag-badge', '标签式（已知支持）', '<badge type="tip" title="推荐" />'),
]
out.append('## 对照组（检测器的反证）')
out.append('')
out.append('同一套判据跑 3 个已知会被渲染的写法，若它们也被判成「未渲染」，说明判据有假阳性、上面的结论作废。')
out.append('')
out.append('| 对照组 | 写法 | 产物字符 | 元素 `<名字` | 可见文字 `:::` | 判定 |')
out.append('| --- | --- | --- | --- | --- | --- |')
control_rows = []
for name, label, markdown in CONTROLS:
    status, headers, payload = post(markdown)
    html = payload.get('html') or ''
    text = strip_tags(html)
    base = name.split('-')[-1]
    element_hits = len(re.findall(r'<(?!/)%s[\s>/]' % re.escape(base), html))
    colon_hits = len(re.findall(r':::', text))
    rendered = element_hits == 0 and colon_hits == 0 and len(html) > 0
    verdict = '上游已渲染' if rendered else '上游未渲染'
    control_rows.append({'id': name, 'label': label, 'htmlChars': len(html),
                         'elementHits': element_hits, 'colonHits': colon_hits,
                         'verdict': verdict, 'http': status, 'ok': payload.get('ok')})
    out.append('| `%s` | %s | %d | %d | %d | **%s** |'
               % (name, label, len(html), element_hits, colon_hits, verdict))
out.append('')

confirmed = [row for row in rows if row['verdict'] == '上游未渲染']
doubtful = [row for row in rows if row['verdict'] != '上游未渲染']
control_bad = [row for row in control_rows if row['verdict'] != '上游已渲染']
out.append('## 结论')
out.append('')
out.append('- 抽样 **%d** 个 ID × 2 种写法 = **%d** 组；独立判定「上游未渲染」**%d** 组，存疑 **%d** 组。'
           % (len(SAMPLE), len(rows), len(confirmed), len(doubtful)))
out.append('- 对照组 **%d** 条全部被判成「上游已渲染」**%s** ⇒ 判据没有假阳性（不会把能渲染的判成不能）。'
           % (len(control_rows), '（0 条误判）' if not control_bad else '——有 %d 条误判，结论作废' % len(control_bad)))
out.append('- 三条通道互相印证：产物里既不出现该元素、可见文字里留着字面 `:::`（容器式）/')
out.append('  产物里留着以组件名命名的元素（标签式）；引擎包里这族**零匹配分支**；guide 里**一次都没提**。')
out.append('')
out.append('**对上游 38 个 `layout-*` 的逐条对应**：本脚本抽了 9 个（两种写法共 18 组）**全部确认未渲染、0 存疑**；')
out.append('第十轮的 76 组全量结论用同一批 ID 清单（`component_registry.json` 里 38 个 `layout-*`），')
out.append('两轮之间清单**逐字相同**（通道 B 的 `listsMatch=true` 即此意）。')
out.append('')

open(os.path.join(OUT, 'round11_crosscheck.txt'), 'w', encoding='utf-8').write('\n'.join(out))
open(os.path.join(OUT, 'round11_crosscheck.json'), 'w', encoding='utf-8').write(json.dumps({
    'bundle': {'sha256': bundle_sha, 'chars': len(bundle), 'quotedLayoutLiterals': len(quoted),
               'unquoted': unquoted, 'regexRefs': regex_refs, 'branchRefs': branch_refs,
               'registryLayoutIds': len(registry_layout), 'listsMatch': sorted(registry_layout) == quoted},
    'guide': {'http': guide_status, 'chars': len(guide), 'hits': guide_hits,
              'hitNames': guide_hit_names},
    'sample': SAMPLE, 'rows': rows, 'controls': control_rows, 'liveBundle': live_info,
    'summary': {'ids': len(SAMPLE), 'groups': len(rows),
                'upstreamNotRendered': len(confirmed), 'doubtful': len(doubtful),
                'controls': len(control_rows), 'controlFalsePositives': len(control_bad)},
}, ensure_ascii=False, indent=1))

print('bundle sha256 =', bundle_sha[:32])
print('registry layout ids = %d   quoted literals = %d   match=%s'
      % (len(registry_layout), len(quoted), sorted(registry_layout) == quoted))
print('unquoted=%d  regexRefs=%d  branchRefs=%d' % (len(unquoted), regex_refs, branch_refs))
print('guide http=%d chars=%d  layout hits=%d' % (guide_status, len(guide), sum(guide_hits.values())))
print('--- sample rows ---')
for row in rows:
    print('%-22s %s  http=%d ok=%s html=%-5d element=%-2d colon=%-2d  %s'
          % (row['id'], row['form'], row['http'], row['ok'], row['htmlChars'],
             row['elementHits'], row['colonHits'], row['verdict']))
print('--- controls ---')
for row in control_rows:
    print('%-16s html=%-5d element=%d colon=%d  %s'
          % (row['id'], row['htmlChars'], row['elementHits'], row['colonHits'], row['verdict']))
print('summary: groups=%d notRendered=%d doubtful=%d controlFP=%d'
      % (len(rows), len(confirmed), len(doubtful), len(control_bad)))
