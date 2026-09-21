# 待用户拍板决策简报（两件）

> 编写时间：2026-09-20。本简报只做决策呈现，不执行任何改动。
> 两条均为等用户一句话回答的事项：用户拍板后再由后续轮次落地。
>
> **事实来源**：带 `file:line` 的结论为本轮读源码核验；「渲染配置已改为本地」「连接测试 ok:true / 语法指令 8010 字符」「渲染服务跑在宿主 node 进程上」等来自任务交接材料（标注「已核验」），本简报未重跑连接测试、未改任何配置。

---

## TL;DR

1. **D53 跨会话渲染缓存**：现状是刻意安全设计（定时稿渲染区段对任何编辑器会话都是「缓存外值」，防护维持原状）；建议**维持现状不动**，代价仅是跨会话再读时看到摘要而非 HTML，无正确性风险。
2. **渲染地址是否回滚到生产**：本轮联调已把渲染配置改到本地 `http://127.0.0.1:8788`，**渲染服务跑在宿主 node 会话上，会话结束后本地渲染会失效**；建议**回滚到生产 `https://www.bx9y.com.cn`**，回滚材料已备好（`target/render_config_row_b64.txt` 整行 SQL / `target/render_config_before.txt` 行元数据），一句话即可执行。

---

## 决策一：D53 跨会话渲染缓存 —— 定时稿渲染区段要不要跨编辑器会话可见

### 现状

MARKFLOW 引擎的定时交付链路在交付前渲染，并在产物上打渲染区段标记：

- `src/main/java/ink/icoding/wechat/article/ai/ScheduledArticleTools.java:35-40`：`RENDER_ID_SEQUENCE` / `nextRenderId()`，产出 `sched-rN` 形式的 id，**与编辑器链路的 `rN` 前缀刻意区分**。
- `ScheduledArticleTools.java:776`：`renderBeforeDelivery` 里 `contentHtml = ArticleAiService.markRenderId(result.html(), nextRenderId())`——即定时稿落库时 HTML 上确实带有 `data-render-id="sched-rN"` 锚点。
- 源码注释（`ScheduledArticleTools.java:770-775`）写明了这个前缀差异的原因：`renderIdOf` 按值查「当前会话」的渲染缓存，撞号会让别的会话把定时稿的区段错认成自己渲染的产物。

编辑器侧的防护完全建立在**会话私有缓存**上：

- `src/main/java/ink/icoding/wechat/article/ai/ArticleAiService.java:816`：`renderCache` 是 `EditorSession` 的私有字段（`renderId → 渲染 HTML`，会话建、会话销）。
- `ArticleAiService.java:847-849`：编辑器链路 `render_markflow` 每次调用生成 `rN` 并 `renderCache.put(renderId, markRenderId(result.html(), renderId))`。
- `ArticleAiService.java:1055-1066`：`renderIdOf(html)` 先用正则抓 `data-render-id`，随后 `renderCache.containsKey(renderId)` **确认命中才返回**；不通则回退整串精确匹配。
- `ArticleAiService.java:1023-1043`：`compactReadResult`（对 `read_article` / `read_blocks` 生效）把命中缓存的 `html` 字段替换成 `placeholderSummary(renderId, html.length())`——即「渲染区段占位摘要」防护：大段渲染 HTML 不进 LLM 上下文，防止被抄改。

**推论（已由代码结构核验）**：定时稿的 `sched-rN` 不在任何编辑器会话的 `renderCache` 里，所以新会话读回定时稿时，`renderIdOf` 两条路径都落空 → 返回 null → `compactReadResult` 不做摘要替换 → 防护**维持原状**（与今天编辑历史里的旧稿完全一致：看得见 HTML，不替换）。这不是新增破坏，而是「没有因此变更」的定性结论。

### 选项

| # | 方案 | 做法 | 代价 | 风险 |
|---|------|------|------|------|
| A | **维持现状（不改）** | 无 | 零改动成本。定时稿在编辑器新会话读回时，渲染区段是普通 HTML 进上下文（防护只在缓存命中时生效，本来就是这个行为） | 无正确性风险；只是错过「跨会话也能摘要化」的收益 |
| B | **进程级渲染注册表** | 把 renderCache 提到进程级共享注册表，定时交付链路渲染后也登记；`renderIdOf` 查共享表而非会话表 | 需要 Entry 淘汰策略 / 淘汰时机；ML 触发全链路需要重测 | 引入**「回显展开成过期 HTML」**：缓存是渲染时快照，用户（或后续自动流程）改过 HTML 后，旧 renderId 仍指向过期产物，`htmlOf`/占位解析会把旧 HTML 当成当前真值回灌。定时稿由无人值守链路产出、无人工复核，这个坑比编辑器链路更难发现 |
| C | **定时链路上线前先渲染并登记** | 只把「登记」这半件事加到定时链路（渲染照旧，产物 HTML 一并进共享表），不动编辑器链路 | 比 B 少改编辑器侧 | 与 B 共享同一个过期快照问题，没有消除，只是缩小触发面；且仍需跨会话共享组件 |

### 建议

**维持现状（方案 A）。** 理由：

1. `sched-rN` 的偏移前缀本身就是防跨会话串号的显式设计，说明作者当时已权衡并以「会话内可见」为界。改动方向正是把界打破。
2. 收益侧（跨会话摘要化）只是省上下文 token 的优化；风险侧是「把旧 HTML 当真值回灌进无人值守链路」的正确性问题——正反不对等。
3. 方案 B/C 都需要处理 Eviction/Aging 策略，还要重跑 Render/Serialization/Agent 全链路回归，工作量和风险都大于本项收益。

### 待用户回答

> **「D53 是就按『会话内可见、跨会话维持现状』收口，还是要启动进程级共享渲染表（接受过期 HTML 回灌风险）？」**

---

## 决策二：渲染地址是否从本地联调环境回滚到生产

### 现状

本轮联调已把渲染服务地址从生产切到本地：

- 已核验：本轮联调经 `PUT /api/settings/render` 把渲染配置从 `https://www.bx9y.com.cn` 改为 `http://127.0.0.1:8788`，连接测试 `ok:true`、语法指令 8010 字符校验通过。
- 路由在 `src/main/java/ink/icoding/wechat/article/settings/RenderConfigController.java:18`（`@RequestMapping("/api/settings/render")`），`:35` 为 PUT 更新、`:41` 为 `POST /test` 连通性验证。控制器说明（`:15`）：「GET 返回令牌掩码；POST /test 真实 GET 一次语法指令验证连通性并顺带刷新缓存。」
- **风险锚点**：渲染服务现在跑在宿主 node 进程上（宿主会话产物），**会话结束该进程可能随之消失**。届时所有走渲染的链路（编辑器的 `render_markflow`、定时链路的 `renderBeforeDelivery`）都会因 `http://127.0.0.1:8788` 不可达而失败；已落库的 HTML 不受影响，新渲染静默失败/抛错（`ScheduledArticleTools.java:765-769` 对 `renderService == null` 抛 `IllegalStateException`，MARKFLOW 定时稿会因此交付失败）。

### 回滚材料（位置与用法）

任务要求这里给出纯文档说明；**不要**在本简报动作中改配置。

- `target/render_config_row_b64.txt`：render_config 表**整行**的 base64，冒号分隔，解码后 8 个字段（对照 `RenderConfig.java:20-40` 的列结构：id、provider、baseUrl、tokenEncrypted、siteBaseUrl、syntaxCacheTtlSeconds、enabled、updatedBy；无时间戳列）。用途：作为**密码学副本**——回滚时先解码确认令牌密文逐字节不变，再把 baseUrl 字段写回生产值 `https://www.bx9y.com.cn`（不得重新编码整行、不得触碰密文字段）。
- `target/render_config_before.txt`：回滚前**行元数据**，tab 分隔，内容为
  `1	MARKFLOW	https://www.bx9y.com.cn	104	<密文指纹，已脱敏>	http://127.0.0.1:8081	3600	1	1	2026-09-18 10:38:31.529388`
  逐列对应（对照 `RenderConfig.java:21-40`）：id=1、provider=MARKFLOW、baseUrl=`https://www.bx9y.com.cn`、令牌密文字节数=104（该密文 140 字符 base64 解码得 104 字节，已用 python 核验）、密文前 12 字符指纹（**原文此处有值，已脱敏**；真值只在服务器上那份备份里，需要时现查）、siteBaseUrl=`http://127.0.0.1:8081`、syntaxCacheTtlSeconds=3600、enabled=1、updatedBy=1、updatedAt 时间戳。用途：回滚时与当前库中行逐列比对，**仅改 baseUrl 一列**，其余列全部保持原样。
  **（2026-09-21 追记：本行是当时的「改动前快照」，别再当现状 —— ①`baseUrl` 已于 2026-09-20 回滚到生产，现查 `RENDER_CONFIG.BASE_URL=https://www.bx9y.com.cn`；②`siteBaseUrl` 已于 2026-09-21 定案**清空**（库中为 NULL）。定案理由与「将来拿到公网地址后怎么设」见 `docs/dev/docker-deployment.md` §10.6。）**
- **这两份文件含令牌密文/指纹，属敏感物**：复制、贴日志、写 issue 时不得带明文密文，只引用路径。

### 选项

| 选项 | 谁受影响 | 回滚/生效怎么做 | 风险 |
|------|---------|----------------|------|
| **留在本地自部署** | 所有依赖渲染的链路，且强绑宿主 node 会话生命周期：编辑器 `render_markflow`、定时稿 `renderBeforeDelivery`（`ScheduledArticleTools.java:765-797`）。无人值守链路尤其敏感——维护者下班后定时任务触发，本地已退出，MARKFLOW 定时稿整篇渲染失败 | 无显式回滚动作；须把渲染服务常驻化（守护/开机自启）才算落地 | 服务依赖外部会话存活 = 隐性单点；断点难定位（定时任务里表现为「交付失败」，不一定直连根因）。生产环境的渲染依赖该宿主机网络通达 |
| **回滚到生产** | 同上全部链路恢复指向 `https://www.bx9y.com.cn`（本轮改动前的原状）；本轮联调期在 `127.0.0.1:8788` 上的行为差异（若本地渲染服务版本/主题色与生产不一致，早前按本地渲染结果存下的产物是最终态，不会因回滚被改） | 用 `target/render_config_before.txt` 逐列比对，仅回写地址列；或 PUT `/api/settings/render` 把地址改为生产值后 `POST /test` 复验连通性与语法指令 | 若本次联调正是为本地发现的生产侧 bug 囤积改动，回滚会推迟其暴露；但联调本身已留结论（连接 ok、8010 字符语法指令通过），且回滚后生产侧行为与本轮改动前逐字节等价，无结构漂移 |

### 建议

**回滚到生产。** 理由：

1. 本地渲染是**会话级联调环境**，本就不是为长跑/无人值守设计的；定时链路按计划会持续触发，把它锁在一个可能消失的 node 进程上等于隐性单点。
2. 回滚材料齐备（整行 + 行元数据两份、含密码学副本），操作可逆且改动面被限制在单列地址——成本极低。
3. 本轮联调目标（验证本地渲染链 + 连通性）已达成（`ok:true`、8010 字符语法指令），继续挂本地没有增量收益。
4. 回滚后用 `POST /api/settings/render/test`（`RenderConfigController.java:41`）复验即可收口。

### 待用户回答

> **「渲染地址现在回滚到生产 `https://www.bx9y.com.cn`，还是维持本地 `127.0.0.1:8788` 并把它常驻化？」**

---

## 其他已知待拍板事项（未核验）

上一轮那批未核验声明里还躺着约 8 条产品决策类待拍板项（**那批声明的总条数不写数字**——本简报未找到该清单的可核对出处，不引用无法核对的数）：**图片外链治理**、**保存侧自检是否覆盖编辑器 REST 路径**、**闸门 max-in-flight 取值**、**巡检心跳 automation-6fe375ff 是否恢复**等。这些条目本简报只做登记、未逐条核验，不在本简报范围内，需要用户后续明确处理渠道后再立专项。
