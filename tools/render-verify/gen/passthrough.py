"""元素形态透传判据（第三十七轮）：把「语法有没有被上游消费掉」变成可复用的纯函数。

## 这个模块解决什么

`known-issues-handoff.md` §八 第 10 条原本的设想是：要泛化「元素形态透传」的判据，
得先有一份「已知合法元素白名单」。**第三十七轮的实测推翻了这条设想**，并把它拆成两个
此前被混为一谈的问题：

  - **Q1「这段语法被消费了吗？」**——这是**关于 HTML 的事实**，可以用形态判断，本模块负责它。
  - **Q2「这算不算缺陷？」**——这是**判断**，取决于「这份输入本来就该渲染出来吗」，
    必须由用例自己声明（`must` / `mustHtml`），本模块**不做**这个判断。

## 为什么「元素名白名单」是错的路

按「非 HTML5/SVG/MathML 即透传」在全量 552 份产物上跑，命中 **44** 份，其中 **41** 份是
`registry/tag-layout-*`——而这些样例的 `expect` 原文就是「上游没有这一族的语法分支，
产物里必然留着字面语法（这就是『等上游』的判据）」。即：**收益 0、噪声 +44**。

更要命的是，合法元素与真残留在**名字上根本无法区分**：

  - `<slider images="…" interval="3" … />` 是**合法**的（上游有意留下的水合占位符，
    注册表里有、`component_matrix.json` 里 `ok:true`）
  - `<layout-hero>ZQLHT 内容</layout-hero>` 是**真残留**（上游没消费掉）
  - 两者**都在 `component_registry.json` 里**，也都是非标准元素。

## 真正的区别是形态

  - 合法占位：**自闭合、无内容**，属性齐全 —— `<slider … />`
  - 真残留　：**成对、包着可见文字**，通常无属性 —— `<layout-hero>内容</layout-hero>`

所以判据是：**非标准元素若包着可见文本 -> 语法没被消费；若自闭合且无文本 -> 占位符。**

## 已知边界（不要当成定理）

  1. 「自闭合 -> 占位」在现有语料里**只有 `slider` 一个正例（n=1）**。若上游将来真把某个
     未消费的元素自闭合输出，本判据会**漏报**。
  2. 「包文本 -> 透传」在现有语料里 100% 命中（38 份故意喂坏样例 + 2 个真残留），**零反例**；
     但若上游有意输出一个「包着文本的水合容器」，本判据会**误报**。
  3. 因此本模块的输出**只能作提示/线索**，不能单独作为判定依据。判定仍走 `must`/`mustHtml`。

用法：
    from passthrough import classify
    classify(html)   # -> {'placeholder': {...}, 'passthrough': {...}, 'total': {...}}

    python tools/render-verify/gen/passthrough.py --selftest
"""
import re
import sys
from collections import Counter

# ---- 标准元素表（W3C 规范，非本项目特有）----
HTML5 = set("""
a abbr address area article aside audio b base bdi bdo blockquote body br button canvas
caption cite code col colgroup data datalist dd del details dfn dialog div dl dt em embed
fieldset figcaption figure footer form h1 h2 h3 h4 h5 h6 head header hgroup hr html i iframe
img input ins kbd label legend li link main map mark menu meta meter nav noscript object ol
optgroup option output p picture pre progress q rp rt ruby s samp script search section select
slot small source span strong style sub summary sup table tbody td template textarea tfoot th
thead time title tr track u ul var video wbr
""".split())

SVG = set("""
svg g defs desc discard metadata symbol use switch view
animate animateMotion animateTransform set mpath
circle ellipse image line path polygon polyline rect text tspan
clipPath mask pattern marker
linearGradient radialGradient stop
filter feBlend feColorMatrix feComponentTransfer feComposite feConvolveMatrix feDiffuseLighting
feDisplacementMap feDistantLight feDropShadow feFlood feFuncA feFuncB feFuncG feFuncR
feGaussianBlur feImage feMerge feMergeNode feMorphology feOffset fePointLight
feSpecularLighting feSpotLight feTile feTurbulence
foreignObject textPath
""".split())

MATHML = set("""
math semantics annotation annotation-xml mrow mi mo mn ms mspace mtext
msup msub msubsup munder mover munderover mfrac msqrt mroot mstyle mpadded mphantom
mtable mtr mtd mlabeledtr mfenced menclose merror
""".split())

STANDARD = {e.lower() for e in HTML5 | SVG | MATHML}

# 开标签：名字 + 属性区（允许引号内的 > ）+ 可选自闭合斜杠
_OPEN_TAG = re.compile(r'<([a-zA-Z][a-zA-Z0-9:-]*)((?:"[^"]*"|\'[^\']*\'|[^<>])*?)(/?)>', re.S)
_TAG = re.compile(r'<[^>]+>')


def nonstandard(html):
    """产物里出现过的非标准元素名 -> 次数。"""
    return Counter(t.lower() for t in re.findall(r'<([a-zA-Z][a-zA-Z0-9:-]*)', html)
                   if t.lower() not in STANDARD)


def classify(html):
    """把非标准元素按**形态**分成「占位」与「透传」两类。

    返回 {'placeholder': Counter, 'passthrough': Counter}：
      - placeholder：自闭合、或成对但中间没有可见文字 —— 上游有意留下的水合占位
      - passthrough：成对且包着可见文字 —— 语法没被消费，原样透传

    注意闭标签要用**大小写不敏感**匹配：产物里真的出现过 `<Badge>新</Badge>`，
    而元素名规范化后是小写 `badge`，用大小写敏感的 `</badge>` 去找会找不到，
    于是把「成对包文本」误判成「自闭合占位」——这个 bug 在开发本模块时真实踩到过一次。
    """
    placeholder, passthrough = Counter(), Counter()
    for m in _OPEN_TAG.finditer(html):
        name = m.group(1).lower()
        if name in STANDARD:
            continue
        if m.group(3):                                     # 显式自闭合 <x … />
            placeholder[name] += 1
            continue
        close = re.compile(r'</%s\s*>' % re.escape(name), re.I).search(html, m.end())
        if not close:                                      # 没有闭标签，按占位处理
            placeholder[name] += 1
            continue
        inner = html[m.end():close.start()]
        if _TAG.sub('', inner).strip():
            passthrough[name] += 1                         # 包着可见文字 -> 语法没被消费
        else:
            placeholder[name] += 1
    return {'placeholder': placeholder, 'passthrough': passthrough}


def counts(html):
    """扁平化的计数，方便直接塞进产物 JSON。"""
    c = classify(html)
    return {
        'elementPassthrough': sum(c['passthrough'].values()),
        'elementPlaceholder': sum(c['placeholder'].values()),
        'elementPassthroughNames': sorted(c['passthrough']),
        'elementPlaceholderNames': sorted(c['placeholder']),
    }


# ---------------------------------------------------------------- selftest

def _selftest():
    """`--selftest`：用几条**已知形态**的输入证明判据是活的。不联网、不读产物。"""
    cases = [
        # (说明, html, 期望 passthrough 数, 期望 placeholder 数)
        ('自闭合占位（真实形态：合法 slider）',
         '<section><p><slider images="a.webp,b.webp" interval="3" width="600" height="200" type="1" /></p></section>',
         0, 1),
        ('成对包文本（真实形态：残留 layout-hero）',
         '<section><p><layout-hero>ZQLHT 内容</layout-hero></p></section>',
         1, 0),
        # 注意这里是 4 不是 1：外层 <steps> 剥掉内部标签后仍剩「收集排版发布」这段可见文字，
        # 所以它自己也算透传，加上 3 个 <step>，合计 4 —— 与真实产物 cmb-callout-steps 的 4 处一致。
        ('成对包文本（真实形态：残留 steps，3 个 step 嵌在里面）',
         '<section><steps><step title="一">收集</step><step title="二">排版</step><step title="三">发布</step></steps></section>',
         4, 0),
        ('大小写混合的闭标签（开发本模块时踩过的 bug：<Badge>新</Badge>）',
         '<section><p>还有 <Badge>新</Badge> 与链接</p></section>',
         1, 0),
        ('成对但**空**内容 -> 占位，不是透传',
         '<section><layout-toc></layout-toc></section>',
         0, 1),
        ('成对但只含标签、无可见文字 -> 占位',
         '<section><layout-cards><span leaf=""></span></layout-cards></section>',
         0, 1),
        ('纯标准元素 -> 两者都是 0（不误报）',
         '<section><p>普通<strong>加粗</strong>与<code>代码</code></p></section>',
         0, 0),
        ('退化输入：空串 -> 0/0（不许在空输入上乱报）',
         '', 0, 0),
    ]
    print('[判据自检] 元素形态透传 passthrough.classify')
    ok = True
    for label, html, want_pass, want_ph in cases:
        got = counts(html)
        good = got['elementPassthrough'] == want_pass and got['elementPlaceholder'] == want_ph
        ok = ok and good
        print('  %-46s 透传=%d 占位=%d（期望 %d/%d） %s'
              % (label, got['elementPassthrough'], got['elementPlaceholder'],
                 want_pass, want_ph, '[OK]' if good else '[!!] 判据不符'))
    print('-> 判据是活的：占位不误报成透传、透传不漏判。' if ok
          else '-> **自检不通过**：形态判据与已知输入不符，必须查。')
    return 0 if ok else 1


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(_selftest())
    print(__doc__)
    sys.exit(0)
