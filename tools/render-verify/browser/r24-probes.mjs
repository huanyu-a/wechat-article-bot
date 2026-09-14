/**
 * 第二十四轮那 11 条症状探针的**唯一定义**（第二十五轮从 `r24-article38-symptoms.mjs` 里原样抽出来）。
 *
 * 抽出来的理由不是「整洁」，是**同尺子**这件事必须有代码上的保证：
 * 第二十四轮的「改前 / 改后 / 产物」三列、第二十五轮的「实时 DOM / 保存出口重灌」两列，
 * 都必须在**同一组测量函数**下取数，否则「差 0.3px」这种结论没法归因。
 * 抽成模块之后两边 import 同一份源码，**改一处两边同时变**，不会再出现「两份拷贝悄悄分叉」。
 *
 * `PROBE_SRC` 是一段**字符串源码**，由 CDP 注入页面执行（不是本 Node 进程里的函数）——
 * 因为测量必须发生在真实页面的 CSS 上下文里。
 */
export const PROBE_SRC = `(() => {
  const px = (value) => {
    const n = parseFloat(value);
    return Number.isFinite(n) ? Math.round(n * 100) / 100 : null;
  };
  const box = (el) => {
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return [Math.round(r.width * 100) / 100, Math.round(r.height * 100) / 100];
  };
  /** 文档序里第一个「包含这段文字」的元素 —— 也就是包住它的最外层那一块。 */
  const outermost = (root, anchor) => {
    if (!root) return null;
    const hits = [...root.querySelectorAll('*')].filter((el) => (el.textContent || '').includes(anchor));
    return hits.length ? hits[0] : null;
  };
  const tableOf = (root) => root && root.querySelector('table');
  const rowsOf = (root) => (root ? [...root.querySelectorAll('tr')].map((tr) => px(tr.getBoundingClientRect().height)) : []);
  const styled = (root, fragment) => root
    ? [...root.querySelectorAll('[style]')].filter((el) => (el.getAttribute('style') || '').replace(/\\s/g, '').includes(fragment))
    : [];

  const ITEMS = [
    {
      id: 'r16-01-changelog', name: 'changelog', complaint: '缺少边框',
      anchor: 'v2.6.0',
      /** 抱怨的可测量：容器边框（宽/线型/圆角）+ 版本胶囊的画法（inline vs 块级、实际盒尺寸）。 */
      run: (root) => {
        const card = outermost(root, 'v2.6.0');
        if (!card) return null;
        const style = getComputedStyle(card);
        const capsule = [...card.querySelectorAll('*')].find((el) => el.textContent.trim() === 'v2.6.0') || null;
        const capsuleStyle = capsule ? getComputedStyle(capsule) : null;
        const capsules = [...card.querySelectorAll('*')].filter((el) => /^v?\\d+\\.\\d+\\.\\d+$/.test(el.textContent.trim()));
        return {
          容器边框宽: px(style.borderTopWidth), 容器边框线型: style.borderTopStyle, 容器圆角: px(style.borderRadius),
          胶囊数: capsules.length,
          胶囊display: capsuleStyle && capsuleStyle.display,
          胶囊盒: box(capsule),
          可见胶囊数: capsules.filter((el) => el.getBoundingClientRect().width >= 40 && el.getBoundingClientRect().height >= 18).length,
        };
      },
    },
    {
      id: 'r16-02-subscribe', name: 'subscribe', complaint: '原项目就没有正确显示',
      anchor: '关注「极客旅程」',
      /** 抱怨的可测量：真表单元素个数（产物里就是假控件）。 */
      run: (root) => {
        const card = outermost(root, '关注「极客旅程」');
        if (!card) return null;
        return {
          输入框数: card.querySelectorAll('input').length,
          按钮数: card.querySelectorAll('button').length,
          多行输入数: card.querySelectorAll('textarea').length,
          二维码图数: card.querySelectorAll('img').length,
        };
      },
    },
    {
      id: 'r16-03-author-card', name: 'author-card', complaint: '头像没有渲染完整',
      anchor: '主理人 · 全栈开发者',
      /** 抱怨的可测量：头像图实际盒 / 外框盒 / 裁切方式 / 原图尺寸。 */
      run: (root) => {
        const card = outermost(root, '主理人 · 全栈开发者');
        if (!card) return null;
        const image = card.querySelector('img');
        const frame = image ? image.parentElement : null;
        const style = image ? getComputedStyle(image) : null;
        return {
          头像图盒: box(image), 头像外框盒: box(frame),
          裁切: style && style.objectFit, 圆角: style ? px(getComputedStyle(frame).borderRadius) : null,
          原图: image ? [image.naturalWidth, image.naturalHeight] : null,
          已加载: image ? image.complete : null,
        };
      },
    },
    {
      id: 'r16-04-quote-card', name: 'quote-card', complaint: '与原项目渲染不一样，缺少底色等',
      anchor: '如果你不能向一个六岁孩子解释清楚',
      /** 抱怨的可测量：卡片底色（渐变）/ 圆角 / 卡片实际高度 / 包裹段落的行间距 / 首行相对卡顶位移。 */
      run: (root) => {
        const card = outermost(root, '如果你不能向一个六岁孩子解释清楚');
        if (!card) return null;
        const style = getComputedStyle(card);
        const cardTop = card.getBoundingClientRect().top + window.scrollY;
        const line = [...card.querySelectorAll('p')].find((el) => (el.textContent || '').includes('六岁孩子')) || null;
        // **每一层直接子元素**逐个量：卡片高出来的那几像素到底长在谁身上，一眼可辨。
        const children = [...card.children].map((el) => {
          const childStyle = getComputedStyle(el);
          return {
            tag: el.tagName.toLowerCase(),
            box: box(el),
            margin上: px(childStyle.marginTop), margin下: px(childStyle.marginBottom),
            position: childStyle.position,
            文字: (el.textContent || '').trim().slice(0, 12),
          };
        });
        return {
          卡片盒: box(card), 卡片高: box(card) && box(card)[1],
          底色: /linear-gradient/.test(style.backgroundImage) ? style.backgroundImage.slice(0, 60) : 'none',
          圆角: px(style.borderRadius), 内边距上: px(style.paddingTop),
          直接子元素数: card.children.length,
          直接子元素: children,
          首行相对卡顶: line ? Math.round((line.getBoundingClientRect().top + window.scrollY - cardTop) * 100) / 100 : null,
        };
      },
    },
    {
      id: 'r16-05-audience-fit', name: 'audience-fit', complaint: '原项目就没有正确显示',
      anchor: '结构严谨、代码块清晰',
      /** 抱怨的可测量：评级徽标（圆）的实际盒 / 圆角 / 边框；以及被上游丢掉的评级文字在不在。 */
      run: (root) => {
        const card = outermost(root, '结构严谨、代码块清晰');
        if (!card) return null;
        const badges = [...card.querySelectorAll('[style]')].filter((el) => /border-radius:50%/.test((el.getAttribute('style') || '').replace(/\\s/g, '')));
        const badge = badges[0] || null;
        const style = badge ? getComputedStyle(badge) : null;
        return {
          徽标数: badges.length, 徽标盒: box(badge),
          徽标圆角: style && px(style.borderRadius), 徽标边框: style && style.borderTopWidth + ' ' + style.borderTopStyle,
          可见文字: (card.textContent || '').replace(/\\s+/g, '').length,
        };
      },
    },
    {
      id: 'r16-06-title-da01', name: 'title DA01', complaint: '没有原项目的好看',
      anchor: 'GUIDE',
      /** 客观差异只有三项：投影在不在、两列列宽、字数。**「好不好看」是主观项，本探针不碰。** */
      run: (root) => {
        const card = outermost(root, 'GUIDE');
        if (!card) return null;
        const style = getComputedStyle(card);
        const table = tableOf(card);
        const cols = table ? [...table.querySelectorAll('col')].map((col) => px(col.getBoundingClientRect().width)) : [];
        const cells = table ? [...table.querySelectorAll('tr:first-of-type > *')].map((cell) => px(cell.getBoundingClientRect().width)) : [];
        const counter = [...card.querySelectorAll('*')].map((el) => el.textContent.trim()).find((text) => /^共\\s*\\d+\\s*字$/.test(text)) || null;
        return {
          投影: style.boxShadow, 表列宽: cols, 首行单元格宽: cells, 字数文案: counter,
        };
      },
    },
    {
      id: 'r16-07-summary', name: 'summary', complaint: '每一项的前边缺少列表符号',
      anchor: '本文要点',
      /** 抱怨的可测量：行首圆点的实际盒尺寸与可见个数（塌成 0 宽就是「缺少符号」）。 */
      run: (root) => {
        const card = outermost(root, '本文要点');
        if (!card) return null;
        const dots = styled(card, 'border-radius:50%');
        const sizes = dots.map(box);
        return {
          圆点元素数: dots.length,
          可见圆点数: dots.filter((el) => el.getBoundingClientRect().width >= 6 && el.getBoundingClientRect().height >= 6).length,
          圆点盒: sizes[0] || null,
          全部圆点盒: sizes.slice(0, 8),
        };
      },
    },
    {
      id: 'r16-08-checklist', name: 'checklist', complaint: '每一项的前边都多了一条竖线',
      anchor: '确定文章核心观点',
      /** 抱怨的可测量：行首方框的实际盒（被压成一条线时宽 >10px 但高 ≈1px）。 */
      run: (root) => {
        const card = outermost(root, '确定文章核心观点');
        if (!card) return null;
        const squares = styled(card, 'width:20px').filter((el) => el.getBoundingClientRect().width > 0);
        const style = squares[0] ? getComputedStyle(squares[0]) : null;
        const sizes = squares.map(box);
        return {
          方框数: squares.length,
          方框盒: sizes[0] || null,
          全部方框盒: sizes.slice(0, 8),
          圆角: style && px(style.borderRadius),
          塌成线: sizes.filter((size) => size && size[1] < 5).length,
        };
      },
    },
    {
      id: 'r16-09-table-card', name: 'table style="card"', complaint: '行间距太大、整个表太高',
      anchor: '输出方式',
      /** 抱怨的可测量：每一行的实测行高、单元格内边距、行高声明条数。 */
      run: (root) => {
        const card = outermost(root, '输出方式');
        if (!card) return null;
        const table = tableOf(card);
        if (!table) return null;
        const head = table.querySelector('th');
        const body = table.querySelector('td');
        const headStyle = head ? getComputedStyle(head) : null;
        const bodyStyle = body ? getComputedStyle(body) : null;
        return {
          表盒: box(table), 行数: table.querySelectorAll('tr').length, 行高: rowsOf(table),
          表头内边距: headStyle && [px(headStyle.paddingTop), px(headStyle.paddingBottom)],
          正文内边距: bodyStyle && [px(bodyStyle.paddingTop), px(bodyStyle.paddingBottom)],
          行高声明: table.querySelectorAll('[style*="line-height"]').length,
        };
      },
    },
    {
      id: 'r16-10-infographic', name: 'infographic', complaint: '原项目就没有正确显示',
      anchor: '读者画像',
      /** 抱怨的可测量：正文圆点的实际盒尺寸（塌了就看不见）。 */
      run: (root) => {
        const card = outermost(root, '读者画像');
        if (!card) return null;
        const dots = styled(card, 'border-radius:50%');
        const sizes = dots.map(box);
        return {
          圆点元素数: dots.length,
          可见圆点数: dots.filter((el) => el.getBoundingClientRect().width >= 5 && el.getBoundingClientRect().height >= 5).length,
          圆点盒: sizes[0] || null,
          标签在: (card.textContent || '').includes('读者画像'),
        };
      },
    },
    {
      id: 'r16-11-steps-horizontal', name: 'steps-horizontal', complaint: '每一步的边框没有加圆角',
      anchor: 'HOW IT WORKS',
      /** 抱怨的可测量：每一步单元格的圆角（px）与边框；表级的列间距三件套。 */
      run: (root) => {
        const card = outermost(root, 'HOW IT WORKS');
        if (!card) return null;
        const table = tableOf(card);
        if (!table) return null;
        const tableStyle = getComputedStyle(table);
        const cells = [...table.querySelectorAll('td')].slice(0, 5);
        const radii = cells.map((cell) => px(getComputedStyle(cell).borderRadius));
        return {
          表格盒: box(table),
          每一步圆角: radii,
          有圆角的步数: radii.filter((value) => value && value > 0).length,
          每步边框: cells[0] ? getComputedStyle(cells[0]).borderTopWidth + ' ' + getComputedStyle(cells[0]).borderTopStyle : null,
          表格折叠: tableStyle.borderCollapse, 表格列间距: tableStyle.borderSpacing, 表格最小宽: px(tableStyle.minWidth),
          表格实时style: table.getAttribute('style'),
        };
      },
    },
  ];

  /** 量一个根元素（编辑器 DOM 或离屏产物容器）。 */
  const measureAll = (root) => ITEMS.map((item) => {
    let value = null;
    let error = null;
    try { value = item.run(root); } catch (problem) { error = String(problem && problem.message || problem); }
    // 「锚点落在哪个元素上」也要留痕：量错了元素，上面的数字全是废话。
    const node = outermost(root, item.anchor);
    const nodeStyle = node ? getComputedStyle(node) : null;
    return {
      id: item.id, name: item.name, complaint: item.complaint, anchor: item.anchor, value, error,
      root: node ? {
        tag: node.tagName.toLowerCase(),
        盒: box(node),
        边框: nodeStyle.borderTopWidth + ' ' + nodeStyle.borderTopStyle,
        圆角: px(nodeStyle.borderRadius),
        上文: (node.textContent || '').replace(/\\s+/g, '').slice(0, 24),
      } : null,
    };
  });

  window.__r24 = { ITEMS, measureAll };
  return ITEMS.length;
})()`
