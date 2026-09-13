# 交接文档：已知缺陷与未实现清单

- 生成时间：2026-09-11（本轮实施收尾后，并已合并并行会话 `sess_457cd5ec` 的只读现场证据）；**同日晚追加第二批（工具预算分档 / PIPELINE 降级 / MarkFlow 保真 / 主题色留存）；2026-09-12 凌晨再追加 F4（渲染式任务的工具说明与引擎不一致——「精排没复刻」的真正主因）与 F5（预算触顶后产出作废）与 F6（剩余未闭合项）与 F7（渲染降级静默：语法没被识别时产物里留着字面标签而运行仍记 SUCCESS），并对 F4/F5 各做了一轮活体验收（run#63，见 3.5），见 3.5；**2026-09-13 再追加第五轮「组件渲染能力全量核查」——77 项组件逐项对照后端 API 与编辑器前端两条路径，编辑器侧修掉 D30–D39 共 10 类渲染保真缺陷，见 3.9；同日晚追加第六轮「上真浏览器实测」——D30–D39 由 jsdom 结论升级为 14 组真实浏览器实拍对照、R2/R4 两条上游绕过走通真实 API+MySQL+SPA 全链路、第五轮表里剩余 19 条有损逐条归类（并因此补掉保存自检的一处缺口 D40），见 3.10；**同日晚追加第七轮「全量浏览器实拍 + 公式/轮播真实链路取证」——79 个样例全部上真实浏览器逐个判定（pass 70 / na 9 / fail 0 / unverified 0，na 的 9 条逐条复验确认是上游没实现），公式与轮播各自走通「真实渲染 API → 真实 MySQL → 真实 SPA 编辑器」全链路，真实稿件回归扩到 13 篇，并量出软删稿件打开时永远转圈的缺陷 D41，见 3.11**；**2026-09-13 第八轮「组合条件覆盖 + 9 条 `na` 的技能/自检核对」——17 个组合用例（嵌套/属性交织/同组件多次/超长/混排）在真实浏览器里两级判定（上游 ok 8 / nested-unsupported 8 / silently-lost 1，编辑器 17/17 pass、0 fail，组合暴露的差异全在上游）；9 条 `na` 做成逐条「等上游」清单并据此修掉技能/自检侧缺口 D43–D45；只读盘清外部失效资源（影响 5 篇、20 张裂图，未动数据）；修掉 D42（MARKFLOW 成稿把站点域名烧进正文，全库 19 篇）并给出红→绿反证，见 3.12**；**2026-09-13 第九轮「对第八轮的回归复查与收口」——全量门禁、79 样例套件、17 组合套件、9 条 `na` 的上游复验全部重跑刷新（数字与第八轮一致，无上游漂移）；把第八轮的泄漏判据修正自证了一遍（两版跑同一批产物，差异 2/17 条、方向全部变严、0 条变松、0 条编辑器侧误伤）；D42–D45 各补一次红→绿（D45 的技能文案部分受「不改生产数据」约束尚未落库，需下次重启生效）；并核出两处文档与代码不一致已改正，见 3.13**；**2026-09-13 第十轮「组件渲染清单收口」——把第八轮留下的 **22 个「悬空」组件**（引擎注册表 63 个 ID 里，`layout-*` 有 22 个既没验过、也没标「等上游」）补齐成整族 **38 个名字 × 2 种写法 = 76 组**真实实测，**悬空 22 → 0**；终稿对照表给出汇总数字 **组件总数 63 / 两条路径均通过 25 / 等上游 38 / 悬空 0**，并按「真实稿件证过（13 个 ID）/ 最小样例真浏览器（50 个 ID）/ 仅 jsdom（**0**）」三个层级分开计数；9 条「等上游」逐条给出**只改写法、不改判据**的替代方案，**10 条全部在两条路径上通过**（后端 ok 10 / 编辑器 pass 10 / fail 0）；回归全部重跑刷新——**303 例 0 失败（2:48）**、`webui` ✓434ms、79 样例 pass 70 / na 9 / fail 0、17 组合编辑器 17/17 pass，见 3.14**；**2026-09-13 第十一轮「缺口钉死 + 独立交叉验证」——把第九/十轮那处「技能文案未落库」缺口补上**源码侧自动化证据**（新增 `SkillSeederMarkflowContentTest` 5 例，**不连库**，钉住 `layout-*` 全族 / `case-flow` 行首 `-` / `:::hint` 只有容器写法三段与「38 名字 / 76 组 / 五种写法」三个数字；顺带更正源码文案里 16→38、32→76、四种→五种、hint 透传形态四处与实测不符的表述），并在 §八 第 7 条给出**只读**的「源码 ↔ 库中内容」复验片段（SQL 五个 `LOCATE` + `CHAR_LENGTH`，预期 `db_len=6649`、五个 `LOCATE > 0`）与 `POST /api/skills/preview` 的 curl；**判定该缺口不阻塞验收**（依据：系统提示确实取 `SKILL.CONTENT`，但受影响的写法在上游本就不支持，改文案只影响模型选型、不影响渲染能力；且保存侧自检已生效、44 篇真实稿件禁写命中全 0），优先级由 P0 下调为 P1。对「等上游 38 个 `layout-*`」做**独立通道**交叉验证（裸响应体 / 引擎包静态结构 / 服务端 guide 三条互相独立，9 个 ID × 2 写法 = 18 组**全部确认、0 存疑**，另有 3 个已知支持写法作对照组、0 误判；38 个 ID 的出处与复现步骤已写清），并给表 B 的 40 条非注册语法定性（编辑器侧 35 pass / 5 na / **0 fail**，无需按「组件」验收）。回归 **308 例 0 失败（3:10）**、`webui` ✓407ms，见 3.15**；**2026-09-13 第十二轮「终稿落地 + 证据可复现」——不加新判据，只做两件收尾：① 产出**面向用户**的终稿 `docs/render-acceptance-report.md`（一句话结论「**能验收**」、63 / 25 / 38 / 悬空 0、公式与轮播逐项证据、已知局限、需用户做的唯一动作是重启一次应用）与**面向工程**的复现手册 `docs/dev/render-verification.md`（7 项前置条件 + 10 行套件总览 + 逐条可复制命令 + 预期数字 + `target/probe/` 会丢文件清单与归档建议 + 9 条已知坑 + 5 条局限）；② 把整条生成链**当轮从头重跑**（后端产物重打 → 探针 dist 重编 → 5 个浏览器套件 → 5 个汇总脚本与终稿表 → 独立交叉验证），**逐条判定与第十一轮完全一致、无上游漂移**（79 样例 70/9/0、17 组合 上游 8/8/1 + 编辑器 17/17、10 替代写法 10/10、76 组 `layout-*` 76 not-rendered / 76 na、终稿表 63/13/50/38/25/25/悬空 0、交叉验证 18/18）。回归 **308 例 0 失败（2:45）**、`webui` ✓405ms；**计时口径专项核对**（全部 `N:NN` 逐一与各自日志对齐，无跨轮混用）与**一致性核对六项全过**（63 / 38 / 25 / 悬空 0 / 测试 308 / `SKILL` 库内仍 5635 字符），见 3.16**；**2026-09-13 第十三轮「拿自己写的复现手册当新人演练」——用全新 shell、不带既有环境变量，逐字照抄第十二轮那份手册跑一遍：**探针与汇总命令 21/21 可直接复制执行、预期数字逐条相符**（79 样例 70/9/0、17 组合 8/8/1 + 17 pass、10 替代写法 10/10、76 组 76 not-rendered / 76 na、终稿表 63/13/50/38/25/25/0、交叉验证 18/18/0/0、覆盖 44 篇 39/31、截图 79/17/10/76/14）；同时用 `git clone` 造了个真干净副本做反证——**clone 场景 10 类探针套件一条都跑不了**（`git ls-files target/probe` = 0 个文件；无 `.env` 导致 205 例里 44 个 Error；无 `node_modules`；且 HEAD 的测试类 36 vs 工作树 45，手册与终稿报告本身今天也 clone 不到），这条「手册目前只对拿到完整工作树的人成立」已写进手册**第零节**并给出「需先补齐」四步清单（只写建议、未搬文件）；本轮修掉 **5 条文档缺陷**（重定向目录不存在 / 缺 `.env` 前置条件 / `PYTHONIOENCODING` 表述与实测不符 / 缺 `npm ci` / 串行命令易被并行），并对**稿件 43 公式 / 44 轮播**做了两条路径各自独立的端到端演练（后端 HTTP 200 + 产物 10266 / 1437 字符；编辑器 `.katex` 5 个高度 [22,22,22,45,53]、`<svg>` 600×200 + 动画 1 + foreignObject 3 + 图 3/3）；终稿报告按「非工程师可读」逐条检查并改写 4 处（去掉裸缺陷编号、解释 jsdom/渲染盒/viewBox 等术语、补后端路径数字、给「能验收」加边界）。回归 **308 例 0 失败（2:46）**、`webui` ✓386ms，见 3.17**
- 适用代码：基线 commit `5f88ca5`；其上 **I1–I10、第二批修复与第五轮前端修复均在工作树中（未提交）**，见 3.5、3.9 与 4.3。
- 一句话：**已修的缺陷不再需要重查（第二节给了索引与证据）；真正还欠的原本是第三节那 13 项——3 项卡在上游（agent4j / 网关）、10 项本仓库缺口。2026-09-11 本轮已把 10 项本地缺口（I1–I10）全部落地（见 3.2 各条的「已修复」），并在同日晚的第二批里补掉了 run#46 触顶事故、MarkFlow 精排版式保真与主题色留存；2026-09-12 凌晨定位并修复了「精排没复刻」的最后一环（F4：MARKFLOW 任务复用着指令式排版的工具说明，模型据此把模板 HTML 当 Markdown 源文提交），仅剩 U1–U3 需对外提诉求、I7 受本机网络阻塞。**自动化链路已活体跑通**：修复后 PIPELINE（run#63，task#2「每日科技早报」）一次跑完全部四阶段、`degradations:0`、文章 17 正常落库且是真精排。

## 〇、怎么读这份文档

| 你在做什么 | 看哪一节 |
|-----------|---------|
| 接手继续开发、想知道「还有哪些坑没填」 | 第三节（未实现 / 未根治） |
| 排查一个新报错，怀疑是老问题复发 | 第二节（已修缺陷索引），命中即说明是回归而非新缺陷 |
| 想知道根因、要看真实现场数据（线程栈 / run 号 / 停滞形态） | 第五节（现场证据与根因） |
| 需要自己动手抓现场（线程栈、探测网关、查库） | 第六节（可复用的现场诊断手法） |
| 要跑构建 / 跑测试 / 做实机验收 | 第四节（工程约束与验收方式） |
| 想找方案原文与逐轮自检记录 | 第七节（相关文档地图） |

优先级沿用项目惯例：**P0** 会造成数据错误或不可用；**P1** 有明显功能缺口/运维风险；**P2** 体验或可观测性瑕疵。

---

## 一、结论速览

**已经彻底解决、不需要再动的**（本节仅列结论，细节见第二节）：

- 网关 429 级联停滞 → 运行级并发闸门 + 429 指数退避
- 1800 秒挂死无止损 → 300 秒阶段超时 + 停滞卡点诊断（含最后一次活动与工具次数）
- 停滞失败时执行日志全丢 → 进度实时上报（`ProgressListener`）
- 被中止运行出现 8 小时**负**时长 → 时间戳统一为 JVM 本地时间
- 配图因描述超长失败、运行却记成功 → 描述写入前截断 + 新终态「成功（有警告）」
- MARKFLOW 排版退化／Markdown 源文丢失／编辑器往返丢内联样式与 `data-render-id`
- 文章版本回滚丢一半字段、且拿不回可重排的 Markdown 源文
- `/api/articles` 的 `skillIds` 读出来是字符串、原样 PUT 回去必 400
- 失败运行 `TOOL_CALL_COUNT` 恒为 0、COORDINATOR 无整轮工具预算
- 子智能体工具调用超过 24 次导致整轮 PIPELINE 失败 → 工具预算按阶段分档（调研 40→60）+ 阶段失败降级为「成功（有警告）」+ **触顶后收尾工具仍放行有限次（成果不再作废）**（D14/D19）+ **额度按实测重定（单阶段 24→36、整轮 120→200），且「被拒过但成果已交出」不再记中止**（D21）
- COORDINATOR 主编在第 300 秒被硬超时杀掉 → 主编独立 1800 秒预算（D15）
- **编辑器侧渲染能力全量核查**（D30–D39）：公式（KaTeX）/ 轮播图与图标（`<svg>`）/ 列表圆点（空 span）/ 渐变高亮底纹 / 字重改写 / 相邻胶囊合并 / 代码块语法高亮 / 语义标签内联样式 / `<sub>`·`<sup>` / 块级 `class` —— 77 项组件逐项对照后端 API 与编辑器前端两条路径，**真实稿件上 `<svg>` 9→9（原 9→0）、`class` 88→88（原 88→0）、样式长度由 −10.0% 转为 +11.6%**，详见 §3.9
- **上真浏览器实测**（第六轮）：第五轮的 D30–D39 全部结论此前**只在 jsdom 里跑过**，本轮用系统已装的 Chrome 138 + 自写的 CDP 驱动（**零新增依赖**）把 14 组样例做成「改前不渲染 / 改后渲染」并排实拍，两侧断言全绿；R2/R4 两条上游绕过走通「真实渲染 API → 真实 MySQL → 真实 SPA 编辑器」全链路；第五轮表里剩余 19 条有损**逐条**归类（16 条归一化 + 3 条上游），并因此发现并补掉保存自检的一处缺口（D40）。详见 §3.10
- **全量浏览器实拍 + 两处真实链路取证**（第七轮）：第六轮只实拍了 14 组代表性样例，**其余 65 个仍是 jsdom 结论**——本轮把**全部 79 个样例**逐个挂上真实浏览器，一行一张截图，判定 **pass 70 / na 9 / fail 0 / unverified 0**；9 条 `na` 每条的现场都写成 `check()` 当场复验（不通过就掉进 `unverified` 而不是悄悄算成 `na`），确认**是上游没实现，不是编辑器画错**。真实稿件侧补掉了前六轮的空缺：**公式**（文章 43，后端落库 HTML 里就有 KaTeX 结构、编辑器里 5 个 `.katex` 高度 [22,22,22,45,53]、无源码残留）与**轮播**（文章 44，容器式 `:::slider` → `<svg>` 600×200 + `foreignObject`×3 + `animateTransform`×1 + 3 张 `naturalWidth` 1080×784 的真图）各自走通全链路；真实稿件回归从 5 篇扩到 13 篇。顺带**量出**软删稿件打开时永远转圈的缺陷（D41）并修掉。详见 §3.11
- **组合条件覆盖 + 9 条 `na` 的技能/自检核对**（第八轮）：第七轮每行只喂了一个最小样例，本轮补上 **17 个组合用例**（容器套容器 / 属性交织 / 同组件多次 / 超长内容 / 行内混排），在真实浏览器里做**两级判定**——上游（后端产物）**ok 8 / nested-unsupported 8 / silently-lost 1**，编辑器（本项目）**17/17 pass、0 fail**：**组合暴露的差异 100% 在上游**（容器套容器是结构性不支持，同组件重复出现则不退化，SVG 计数精确翻倍）；把 9 条 `na` 做成**逐条「等上游」清单**（各附一句「上游修复后本项目要做的验证动作」），并据此核对技能提示与保存自检，**新修 D43–D45**；只读盘清外部失效资源（44 篇 / 31 篇含绝对 URL / 62 个唯一外部 URL 探活 200×37·404×21·403×2·401×2 / 影响 5 篇，未动数据）；**修掉 D42（MARKFLOW 成稿把站点域名烧进正文，全库 19 篇）并给出红→绿反证**。详见 §3.12
- **对第八轮的回归复查与收口**（第九轮）：全量门禁、79 样例套件、17 组合套件、9 条 `na` 的上游复验**全部重跑刷新**（数字与第八轮一致，无上游漂移）；第八轮的泄漏判据修正**自证**完成——两版跑同一批产物，差异 **2/17** 条、**方向全部变严、0 条变松、0 条编辑器侧误伤**；D42–D45 各补一次红→绿（**D45 的技能文案部分受「不改生产数据」约束尚未落库**，需下次重启生效）；核出两处文档与代码不一致并改正。详见 §3.13
- **组件渲染清单收口 + 「等上游」改写法收敛**（第十轮）：第八轮结束时 `layout-*` 只实测过 16 个名字，**剩下 22 个既没验过、也没标「等上游」= 悬空**；本轮把这 22 个补齐成整族 **38 个名字 × 2 种写法 = 76 组**，每组真打渲染 API 并把产物灌进**真实 Chrome**——**后端 76/76 `not-rendered`、编辑器 `na 76 / fail 0`**，**悬空 22 → 0**（另有 `component_matchers.json` 的 29 条匹配器里**零个 `layout-*` 分支**作结构性印证）。终稿对照表 `round10_component_paths.md` 给出 **组件总数 63 / 两路径均通过 25 / 等上游 38 / 悬空 0**，并把证据分成三层：**A 真实稿件（13 个 ID）/ B 最小样例真浏览器（50 个 ID + 表 B 40 条）/ C 仅 jsdom（0）**；A 级依据是只读导出全库 44 篇的 `CONTENT_MARKDOWN` 后逐个正则匹配（70 项里 39 有命中、31 无），**6 种「禁写」写法在真实稿件里命中全为 0**。9 条「等上游」逐条给出**只改写法**的替代方案，**10 条全部通过两条路径**（后端 ok 10 / 编辑器 pass 10 / fail 0）。详见 §3.14
- **缺口钉死 + 独立交叉验证**（第十一轮）：第九/十轮那处「技能文案未落库」缺口补上**源码侧自动化证据**——新增 `SkillSeederMarkflowContentTest`（5 例，**不连库**），钉住 `layout-*` 全族、`case-flow` 行首 `-`、`:::hint` 只有容器写法三段说明与「38 名字 / 76 组 / 五种写法」三个数字；顺带更正源码文案里四处与实测不符的表述（16→38、32→76、四种→五种、`<hint>` 的透传形态）。**阻塞性判定：不阻塞验收**（受影响的写法在上游本就不支持，改文案只影响模型选型；保存侧自检已生效、44 篇真实稿件禁写命中全 0），优先级 P0→P1。§八 第 7 条给出**只读**复验片段（SQL 预期 `db_len=6649` 且五个 `LOCATE > 0`；外加 preview 的 curl）。对「等上游 38 个 `layout-*`」用**三条独立通道**（裸响应体 / 引擎包静态结构 / 服务端 guide）复核，**9 个 ID × 2 写法 = 18 组全部确认、0 存疑**，另有 3 个已知支持写法作对照组、**0 误判**；表 B 的 40 条非注册语法也给了定性（编辑器侧 35 pass / 5 na / **0 fail**，无需按「组件」验收）。详见 §3.15
- **终稿落地 + 证据可复现**（第十二轮）：不加新判据，只做两件收尾。① **面向用户的终稿** `docs/render-acceptance-report.md`——用非工程师能懂的话回答「每个组件在后端 API 与编辑器前端是否都能正确渲染」：**组件总数 63 / 两路径均正确 25 / 不能渲染 38（原因归类：38 条全部是上游没实现，0 条待重启落库、0 条其他）/ 悬空 0**；用户最初点名的**公式**（稿件 #43：5 个 `.katex` 全部可见、块级 2 个、高度 [22,22,22,45,53]、源码未泄漏、字体已加载，`shots/articles/43.png`）与**轮播**（稿件 #44：SVG 600×200 可见、animateTransform 1、foreignObject 3、3 张图 loaded 3/3，`shots/articles/44.png`）单独给结论与 DOM 证据；已知局限与「只有 B 级证据（无真实稿件）」的条目逐条标注；末尾给「**现在能验收**」的一句话判断与需用户做的唯一动作（**重启一次应用**关闭技能文案落库）。② **复现手册** `docs/dev/render-verification.md`——前十一轮的证据全在 `target/probe/`（被 `.gitignore:2` 命中，等于证据链在版本控制里是断的），手册写清 7 项前置条件（各带自检命令）、10 行套件总览、逐条可复制命令、每个套件的**预期数字**、`target/probe/` 会丢的文件清单与长期归档建议（**本轮只写建议、未搬动**）、9 条已知的坑与 5 条方法局限，标准是「没参与过前几轮的人照本文能独立重跑出同一结论」。本轮**把整条生成链当轮重跑**，逐条判定与第十一轮一致、无上游漂移。详见 §3.16
- 精排版式「没有 100% 复刻」→ 主题色随文章落库、MARKFLOW 正文改黑名单清洗、编辑器往返保住段落样式、**F4：渲染式任务不再复用指令式排版的工具说明**、**F7：渲染器没认出的语法不再静默落库**、**F8：降级判据在真实产物上校准过（当时结论是零误报，后被 D27 修正为「漏了一类实体误报」）**、**F9：项目技能与官方语法指令逐条对齐（推翻两条已被渲染器改掉的旧结论，补上容器白名单）**、**F10：找到「成品不如官网示例好看」的真因——guide 漏写了官网在用的 8 个容器组件（外加只写了标签形式的 `:::steps`），技能已补齐（并推翻 `<timeline>`/图注/`:::steps` 三条旧结论），活体跑已产出首个带结构组件的成稿**、**F11：`stages_summary.renderWarnings` 取在渲染之前的错序（该字段此前结构性恒为 0）**（D16/D17/D18/D20/D22/D23/D24/D25；2026-09-13 复查又补 D26/D27）

**仍然欠着的**：上游依赖 3 项（U1–U3，本仓库改不动）、环境阻塞 1 项（I7）。**本仓库缺口 10 项（I1–I10）已于 2026-09-11 本轮全部落地**，各条修复要点与对应测试见 3.2；I8（编辑器链路硬超时）作为当时的最高优先项已一并修复——编辑器会话现在也包上 `StageTimeout`，超时会释放并发名额并向浏览器发 `error` 事件，不再静默吃掉额度。

根因与现场证据（run#33 的活体线程栈、停滞命中点、实施后 #35/#36/#38 的 300 秒止损形态）以及可复用的诊断手法（jstack 判读、网关停滞率探测、直查库）已合并进**第五、六节**。

---

## 二、已修缺陷索引（避免重复排查）

遇到下列症状先确认是不是回归：每条的「证据」都是当时可复现的量化事实，若今天重现，说明修复被回退了。

| # | 症状 | 关键证据 | 修复轮次 | 关键文件 |
|---|------|---------|---------|---------|
| D1 | 定时任务连续 FAILED、正好 1800 秒、`tool_call_count=0` | run#29/#31/#32 全部停在第 1 次调用；网关原文 `HTTP 429 concurrent limit exceeded: running=7 max=6` | 本轮第①②期 | `common/InFlightGate`、`schedule/AgentInvoker`、`schedule/TaskExecutionService`、`schedule/ArticleAiService` |
| D2 | 停滞让工作线程放弃后 HTTP/SSE 流仍开着，占住网关名额，越卡越多（级联） | 8 次内容完全相同的极简流式请求里 1 次 >40 秒无响应、耗时 0.76–19.4 秒抖动；`javap` 证实 agent4j 无 `cancel`/读超时 | 本轮第①期治因（闸门限流），**根因仍在**（见 U1） | `common/InFlightGate` |
| D3 | 停滞失败的运行 `EXECUTION_LOG` 为空（修复前 #26 有 277 字符） | run#35 `EXECUTION_LOG` NULL；改用实时上报后 run#36 留存 248 字符 5 行 | 本轮第④期（本轮自查发现的回归） | `schedule/AgentRunner.ProgressListener`、`schedule/TaskWorkspace` |
| D4 | 被中止的运行 `FINISHED_AT` 比 `STARTED_AT` 早 8 小时 | run#28 `13:32 → 05:42`；根因 MySQL 会话时区 UTC 的 `NOW()` 与 JVM 本地时间混用 | 本轮第③期（十九轮 P2-5 同源） | `schedule/TaskRunMapper.abortStale/formatTimestamp` |
| D5 | 工具调用失败（如 `generate_image` 超长失败）但运行记为 SUCCESS | #30 执行日志含 `Data too long for column 'DESCRIPTION'`，配图未插入却成功 | 本轮第③期 | `schedule/TaskExecutionService.completion`、`webui/views/TasksView.vue` |
| D6 | 描述超过 500 字必失败，且「把实体改到 2000」也无效 | `javap` 证实 `DefaultSmartMapperInitializer.syncDatabaseStructure` 只建表/ADD 列、**不发 MODIFY COLUMN**；改回 2000 该测试立刻复现 `Data too long` | 本轮第③期 | `asset/Asset.java`（`DESCRIPTION_MAX_LENGTH=500`）、`asset/AssetService` |
| D7 | MARKFLOW 文章改个标题就把排版丢了（渲染产物未留存） | 真实令牌端到端验收 | 此前（P0） | `article/Article.contentMarkdown/layoutEngine`、`ScheduledArticleTools.renderBeforeDelivery` |
| D8 | 智能体重排后 Markdown 源文被丢弃，无法再渲染 | 落库断言 | 此前（P0） | `article/ArticleService`、`ArticleRevision` |
| D9 | 编辑器往返一次，渲染产物内联样式与 `data-render-id` 全丢，AI「HTML 回灌」防护失效 | CDP 量化：修复前后步骤区 `684×58 → 684×276`、字面 `###` 有→无、破损占位 6→0；`git stash` 反例证明测量有效 | 十五/十六/十九轮 | `webui/src/editorExtensions.js`、`ArticleEditorView.vue` |
| D10 | 文章回滚是「部分合并」：作者/来源 URL 保持当前值，且永远拿不回 Markdown 源文 | 回滚 10/10 用例 | 十九轮 ⑤ | `article/ArticleRevision`（补 4 列）、`ArticleService.rollback`（`restoreOr`） |
| D11 | GET `/api/articles` 的 `skillIds` 是字符串，原样 PUT 回去 400 | `javap -v` 能证明注解在方法上但响应仍是字符串——两个 databind 包并存（HTTP 走 Jackson 3） | 十九轮 ④ | `article/Article.getSkillIds`（**Jackson 3** 的访问器级序列化器） |
| D12 | 失败运行的工具调用数永远是 0；COORDINATOR 整轮预算可到约 240 次 | 计数器移到工作区后失败路径也写；新增 `MAX_TOTAL_TOOL_CALLS=120` | 十九轮 ③⑧ | `schedule/TaskWorkspace`、`ai/DelegateTools` |
| D13 | 排版技能文档推荐的 `:::steps` 实际渲染成破损圆形 | CDP 实测：`###` 与正文各拆成 38px 圆形；正解是 `<steps>`+`<badges>`、>3 步须显式 DA02 | 十七轮 | `webui` 内置技能内容、本机 `~/.zcode/skills/markflow-typeset/SKILL.md` |
| D14 | PIPELINE 任务整轮失败，消息为「子智能体工具调用超过上限 24 次，已中止」 | run#46（task#2）：中止发生在**第 25 次**调用，前 25 次**全部成功**（`search_web`×20 + `browse_webpage`×5，零失败零重试）；run#55 复现同一条消息、`TOOL_CALL_COUNT=33` | 第二批 ① | `schedule/ToolCallBudget`、`schedule/PipelineExecutor` |
| D15 | COORDINATOR 整轮停在第 300 秒被杀，卡点都是「主编侧无事件、子智能体正在运行」 | run#41/#42/#47/#48 全部正好 300 秒；主编一次会话覆盖全部委托，量级与单阶段会话不同 | 第二批 ② | `resources/application.yaml`（`coordinator-timeout-seconds=1800`）、`schedule/CoordinatorExecutor` |
| D16 | MARKFLOW 精排版式「没有 100% 复刻渲染能力」：公式/图表组件被拆、颜色整篇漂移 | jsdom 往返探针 14 项丢 10 项；`<math>`/`<svg>`/`<mark>`/`<kbd>` 落库即被解包；重排后主题由科技蓝 `#0984e3` 漂成默认绿 `#27ae60` | 第二批 ③④ | `article/ArticleService.cleanMarkflowBody`、`webui/src/editorExtensions.js`、`article/Article.themeAccent` |
| D17 | MARKFLOW 段落间距被 `formatForWechat` 无条件覆盖（步骤卡/徽章/对比卡内部段落全部粘成 16px） | 追加的 `margin-bottom:16px` 排在内联样式末尾、优先级更高 | 第二批 ③ | `article/ArticleContentPolicy.formatForWechat` |
| D18 | MARKFLOW 定时任务交付的文章**根本没有精排版式**，且改主题色无效 | 文章 11/13/14 的 `CONTENT_MARKDOWN` 全是 `style="` 密度极高的手写模板 HTML（`grep -c 'style="'` = 0 才是 MarkFlow 语法）；同库文章 12 因模型恰好照系统提示写了 MarkFlow 语法，渲染出 `data-block="ptitle"`、渐变大卡片等真精排 | 第二批 ⑤ | `ai/ScheduledArticleTools`（`SaveMarkflowDraftTool`/`SaveMarkflowDraftParam`） |
| D19 | 预算触顶后**整段工作的产出全部作废**，且模型读到「已中止」后继续白调工具 | run#62：调研 47 次尝试 / 40 次成功 / 零失败，随后连调 3 次 `save_research_notes` 全被同一套预算拒绝——简报一个字没存下，写作阶段只能硬写；被拒后模型又白调 7 次工具（4×`search_web` + 3×`save_research_notes`）。**修复后 run#63 同一位置被走到且不再作废**：`【审核】预算已用尽，放行收尾工具：submit_review`，`degradations:0` | 第二批 ⑥ | `schedule/AgentInvoker`（收尾工具宽限 + 可执行错误文本）、`schedule/ToolCallBudget`（`TERMINAL_TOOLS`/`TERMINAL_GRACE`、调研额度 40→60）、`agent/AgentProtocols` |

| D20 | 渲染器没认出的语法**静默**落库：产物里字面留着 `<steps>`/`:::compare`，运行仍记 SUCCESS | 探针（2026-09-12）：未闭合的 `:::compare` → 产物里 `<p>:::compare</p>` 且后续整段被吞，1219 字符 → 405 字符；`meta.warnings` 上游**未返回**（两次刻意降级都没有）；此时落库 HTML 依旧满是内联样式，只看「有没有样式」看不出来 | 第二批 ⑦ | `skill/MarkFlowRenderService`（`detectLeakedSyntax`/`parseWarnings`）、`ai/ScheduledArticleTools`（`markflowSyntaxHints` + `renderWarnings` 传递）、`schedule/TaskExecutionService`（终态）、`schedule/TaskWorkspace`（`stages_summary`） |
| D21 | 「子智能体工具调用超过上限 24 次，已中止」：额度按个案定得太紧，**收尾宽限变成了主路径**；且「被拒过」被当成「这一阶段白干」 | 实测 run#63 评审单会话 25 次、run#68 配图单会话 26 次（都在旧上限 24 之上），run#68 整轮 110 次逼近旧上限 120——两轮的成果都是靠宽限才交出来的 | 第二批 ⑧ | `schedule/ToolCallBudget`（stage 24→36、total 120→200）、`ai/DelegateTools`（三个常量同步）、`resources/application.yaml`、`schedule/AgentInvoker`（`deliverableSubmitted`） |
| D22 | 渲染降级判据在真实产物上有 6 处误报；`<timeline>` 的内容被静默丢掉却无人知晓 | 判据打在 2026-09-12 的真实产物上：误报三种成因（探针带空白、跨行拼接被序号隔断、把渲染器自带文案与不输出的 `label` 当成丢失）；`<timeline>` 四种写法产物长度**都是 0**，与正文混排时前后正文都在、时间线的字一个不剩。**F10 更正**：真因是**每行必须三列**，三列时产物 1030 字符、内容完整（四种旧写法恰好都缺列） | 第二批 ⑧ | `skill/MarkFlowRenderService`（`longestLine`/`SELF_COPY_COMPONENTS`/`TEXT_ATTRIBUTE`）、`ai/ScheduledArticleTools`（时间线三列自检）、`article/ArticleService.rerender`（降级写进版本说明） |
| D23 | 项目技能里「与语法指令冲突时以这四条为准」的两条已被渲染器推翻，且方向都是**劝模型放弃本来能用的写法** | 逐条打真实渲染 API：`:::tip` 等六种容器**都渲染成提示框**（`border-left:4px`，与 `> [TIP]` 同款），技能却说「不会被识别」；`<badge type="tip" title="推荐" />` 产物文本就是「推荐」，技能却说「渲染出的是 type 值」；`<steps>` 4 步 / 5 步确实不自动竖向（这条技能对），但「必须显式 DA02」是排版取舍不是硬规则 | 第二批 ⑨ | `skill/SkillSeeder`（技能四条重写）、`ai/ScheduledArticleTools`（容器白名单 + 三条新自检）、`skill/MarkFlowRenderService`（`LEAKED_CLOSING`）、`ai/EditorServiceTools`（参数说明） |
| D24 | 产出「不如 MarkFlow 官网示例好看」：**同一台渲染器**，但官网在用的 8 个容器组件 `guide` 全文一次都没提（只提了 `:::compare`/`:::breaking`/`:::timeline`/`:::table`），模型自然只会用最朴素的几种 | 官网示例首屏是「阅读时长 + 大标题 + 话题标签 + `:::reading-path` 章节导航」，其后是 `:::steps-horizontal` 步骤卡、`:::case-flow` 案例条、`:::timeline` 时间线、`:::slider` SVG 轮播；本项目文章只有 `<badges>`/`<lead>`/`<statement>`/`<img>`。用官网组件注册表里的官方示例逐条打真实渲染 API，**每一种都产出了对应结构**（轮播图甚至产出 `<animateTransform>`）——guide 漏写 ≠ 渲染器不支持。**活体复核（run#71 → 文章 25）**：修完以后第一次产出结构组件（`:::reading-path` 渲染出 5 个编号圆点 + 连接线的章节导航），注入侧 12224 字符的系统提示也确认含这 8 个组件名 | 第二批 ⑩ | `skill/SkillSeeder`（第 2 条新增第 ⑥ 组清单）、`ai/ScheduledArticleTools`（容器白名单 10→19、连字符名修复、`case-flow`/`timeline`/表格后空行/`reading-path` 行格式/`slider` 缺图/`compare` 列数 六条自检）、`ai/EditorServiceTools` |
| D24-附 | 随之推翻三条旧结论：`<timeline>`「渲染为空」、图注「隔空行不识别」、`:::steps`「不能用」/「>3 步不自动竖排」 | `<timeline>` 写三列产物 1030 字符、两列才 0 字符（旧探针四种写法恰好都缺列）；图注隔不隔空行产物**逐字节相同**；`:::steps` 用官方行格式产物 1462 字符（圆点 + 连线步骤流）；`<steps>` 4 步/5 步**自动竖排**，与显式 `DA02` 产物完全一致 | 同上 | 同上（旧自检与用例已删除/改正） |
| D24-再附 | 新补的容器自己也有一类「静默失败」：`:::reading-path` 有一行不带 `-` → 整块 **0 字符**；`:::slider` 缺 `images` → 成稿里留一句「请提供图片URL列表」；`:::compare` 行数不在 3–4 列 → 该行整行忽略（2 列时对比方整列内容丢失） | 真实渲染 API：`reading-path` 两行 `- 章节甲 \| 说明` → 2251 字符；其中一行换成裸文字、或整块换成 `*` → **0 字符**且 `ok:true`、零 warnings；`slider` 无 `images` → 产物 165 字符，就是那个灰底提示框；`compare` 4 列 1742 字符 vs 5 列/2 列 1173 字符且只报一句 `meta.warnings` | 同上 | `ai/ScheduledArticleTools`（`readingPathWithoutBullets` / `sliderWithoutImages` / `compareRowsWithWrongColumnCount`）、`skill/SkillSeeder`（⑥ 与 ④ 写明行格式）、`ai/ScheduledArticleToolsDraftTests` |
| D25 | `task_run.stages_summary.renderWarnings` **结构性恒为 0**：摘要在 `renderBeforeDelivery` 之前取，而 PIPELINE/COORDINATOR 从不提前渲染 | run#71 自我矛盾：摘写 `"renderWarnings":0`，同一行的运行说明写「有 1 处 MarkFlow 渲染降级（compare 有 5 行列数不是…）」。字段本意是「不翻日志就知道版式有没有打折」，恒为 0 反而会让人先怀疑渲染服务 | F11 | `schedule/TaskExecutionService`（`renderAndSummarize`：渲染在前、摘要紧随；失败路径也补摘要）、新增 `schedule/TaskExecutionFinishTest` |
| D26 | 「只有容器写法」的 6 个组件被写成 XML 标签时，保存侧**没有任何提示**，而渲染器不认这种标签，**这个未知元素会原样留进落库 HTML** | run#73 的 F11 活体取证：正文里写 `<compare>…</compare>`（本期提示词指定的格式）→ 文章 27 的落库 HTML 里就是 `<p style="…"><compare></compare></p>`——**元素形态原样透传**（不是把标签名当文字输出；该元素没有子节点，屏幕上什么都不显示，比「留一行字」更隐蔽）。`renderWarnings:1`。保存时自检当时一条都不报（guide 第六节第 6 条明明提醒过「不是 `<compare>` 标签」） | 2026-09-13 复查 | `ai/ScheduledArticleTools`（`CONTAINER_ONLY_AS_TAG` + 新提示）、`ai/ScheduledArticleToolsDraftTests`（新增 1 例 20 断言组） |
| D27 | 降级判据的**误报**：源码里的 HTML 实体（`&quot;` / `&amp;` / `&lt;`…）不解码就拿去比对**已解码**的产物，于是把渲染得好好的组件报成「内容丢失」 | run#75（2026-09-13）实报「组件属性里的文字「pacethefrontier&quot」在渲染产物里找不到」，而同一份 HTML 里 `"pace the frontier"` **明明渲染出来了**（文章 29 的 `<p-title subtitle="关于&quot;pace the frontier&quot;">`）。**反证**：把 `longestToken` 的实体解码去掉，单测复现出的探针文本与 run#75 的告警**逐字相同**（`pacethefrontier&quot`）；加回解码后，拿文章 29 的**真实源文 + 真实产物**跑判据 → **0 条**（`target/probe/dropped_probe_result.txt`）。根因是源码侧与产物侧文本形态不对称：`flatten` 解码、探针不解码，而 `MULTI_VALUE_SEPARATOR` 又会先按 `;` 把 `&quot;` 劈成 `&quot` | 2026-09-13 复查 | `skill/MarkFlowRenderService`（新增 `decodeEntities`，`longestToken` 先解码再取词）、`skill/MarkFlowRenderServiceTest`（新增 1 例，17→18） |
| D28 | **技能提示词教模型写一个渲染器不输出的属性**：`:::table style="card" title="…"` 的 `title=` 里的文字在产物里一个字都没有，上游不报错也不 warning | 真实渲染 API（2026-09-13，`target/probe/attr_probe3.txt` / `attr_probe4.txt`）：`:::table`（含 `style="card"`）、`:::timeline`、`:::slider`、`:::compare` 的 `title=` **都不渲染**（HTTP 200、`ok:true`、零 warnings）；反面 `:::callout title=`、`:::steps-horizontal label/title/hint`、`:::code-block title=` **都会渲染**。代价是真实可见的：run#76 因此背了一条 `renderWarnings:1`（告警文本「组件属性里的文字「数学能力基准测试演进」在渲染产物里找不到」），而那句话本来就不该写 | 2026-09-13 第三轮复查 | `ai/ScheduledArticleTools`（`CONTAINER_DROPPED_TITLE` + 新提示）、`skill/SkillSeeder`（表格容器示例去掉 `title=` 并写明原因）、`ai/ScheduledArticleToolsDraftTests`（新增 1 例 + 修正 1 处旧用例） |
| D29 | **同类问题再现：技能提示词教模型往 `:::compare` 第 4 格写「强调标记」这个中文占位词，渲染器只认 `accent` / `default`**，于是那一行被判坏整行忽略、整轮记 `SUCCESS_WITH_WARNINGS` | run#81 / run#82（2026-09-13 07:24 / 07:52，改版技能文本后的两轮新采样）**连续两轮**的 `renderWarnings` 都是「compare 有 1 行列数不是「维度 \| A方 \| B方 \| accent\|default」」，两篇落库源文（文章 35 / 36）的 compare **表头第 4 格写的都是「强调」**。最小复现 `target/probe/probe_compare_marker.py`：4 列第 4 格写 `强调` / `高亮` / `highlight` / `strong` → 报；写 `accent` / `default` / `ACCENT` / 留空 → 不报。**上游告警说「列数不是」，实际是取值不在白名单里**——照着告警去数列数会改错方向。旧判据只查列数（3–4），这一格因为「4 列」而通过，两轮都没拦住 | 2026-09-13 第四轮复查 | `ai/ScheduledArticleTools`（新增 `COMPARE_MARKER_VALUES`，`compareRowsWithWrongColumnCount` → `compareRowsWithBadShape`，提示文本改为写明白名单）、`skill/SkillSeeder`（compare 示例改成 `维度 \| A方 \| B方 \| accent` 并写明「只认英文 accent/default」）、`ai/ScheduledArticleToolsDraftTests`（新增 1 例，21→22） |

| D30 | **编辑器里公式渲染不出来**：`\frac{1}{3}` 塌成并排的 `3` `1`，整棵 KaTeX 树散架（用户报告的两个症状之一） | 真实库稿件 `target/probe/real/5.html`（含 5 个公式）：往返 **10001 → 6045 字符（−40%）**、`class` **77 → 0**、`katex` 5 → 0 | 第五轮 | `webui/index.html`（补 `katex@0.17.0` 样式表）、`webui/src/editorExtensions.js`（新增 `RawMath` 不透明原子节点） |
| D31 | **编辑器里轮播图/图标渲染不出来**：`<svg>` 被解包（用户报告的两个症状之二） | `components/ctn-slider.editor.html`：后端 1144 字符的 `animateTransform` 动画 → 只剩 403 字符的三张裸 `<img>`；三篇真实稿件 `<svg>` **9 → 0** | 第五轮 | `webui/src/editorExtensions.js`（新增 `RawSvg`） |
| D32 | **列表圆点消失**（`md-list`、`:::layout-toc`、`:::layout-metrics`）：渲染服务的圆点是一个**空 span**，ProseMirror 的标记只挂文本节点，空 span 产不出文本即被丢弃 | `components/md-list.editor.html` 两个圆点 span 全丢；真实稿件 `5.html` 有 7 处 | 第五轮 | `webui/src/editorExtensions.js`（新增 `PreservedEmptySpan`） |
| D33 | **高亮底纹消失**：`background: linear-gradient(…)` 被 `BackgroundColor` 归一化成 `background-color: transparent` | `components/in-em-hl.editor.html` 样式长度 287 → 275；影响面（`round5_impact.mjs` 口径）79 个样例 13 处 / 11 个样例（改前丢 2 处）、6 篇真实稿件 30 处 / 6 篇全中（改前丢 9 处） | 第五轮 | `webui/src/editorExtensions.js`（`readPreservedMarkStyle` 改为按值判断是否交给专用扩展） |
| D34 | **字重被改写**：`font-weight:600` 的 span 被 TipTap 的 Bold 提成 `<strong>`（渲染成 700） | 内联 `font-weight`≥500 共 116 处 / 41 个样例，6 篇真实稿件 409 处全部命中；改前编辑器凭空多出 `<strong>` 124 个（真实稿件 446 vs 后端 190），改后为 0 / 190 = 后端原值 | 第五轮 | `webui/src/editorExtensions.js`（新增 `MarkflowBold`，去掉 `style: 'font-weight'` 那条 `parseHTML` 规则） |
| D35 | **相邻同款 span 被合并**：`<badges>` 三个独立胶囊塌成一个、内容连成 `ABC` | `components/blk-badges.editor.html` 样式长度 **703 → 360** | 第五轮 | `webui/src/editorExtensions.js`（`PreservedMarkStyle` 增内部属性 `sourceSpanId`） |
| D36 | **代码块语法高亮丢失**、`data-lang` 丢失（`md-code` / `diagram-mermaid` / `ctn-code-block`） | `md-code` 样式长度 **564 → 308**；`diagram-mermaid` 463 → 308 | 第五轮 | `webui/src/editorExtensions.js`（新增 `MarkflowCodeBlock`，`marks: 'textStyle'` + `data-lang` + 内层 `<code>` 的 style） |
| D37 | **语义标签上的内联样式整条丢失**：`<strong style="font-size:60px">` 只剩裸 `<strong>` | `components/blk-p-title.editor.html` 的巨型章节号叠印版式整段塌掉 | 第五轮 | `webui/src/editorExtensions.js`（受管清单只对 `SPAN` 生效 + `preservedMarkTypes` 扩到 7 种标记） |
| D38 | **`<sub>` / `<sup>` 被解包**：`H₂O` → `H2O`、`m²` → `m2` | `in-sub` / `in-sup` 两个样例 | 第五轮 | `webui/src/editorExtensions.js`（新增 `Subscript` / `Superscript`） |
| D39 | **块级 `class` 被丢弃**（`<section class="tableWrapper">`） | `blk-title` 的 `tableWrapper` 丢失 | 第五轮 | `webui/src/editorExtensions.js`（新增 `PreservedClass`，只声明在块级节点上） |
| D40 | **保存自检漏了标签式 `<slider>`**：R4 这条绕过的文档写着「保存前有语法自检」，但自检实际只看 `:::slider` 容器（缺 `images` 时提示），**标签式自闭合的写法完全没进判据**——模型真写了，整行标签会被后端当正文透传，成稿里留一处**空白**（浏览器把未知元素渲染成空 inline 盒子），而模型收不到任何反馈 | 红→绿：```markflowSyntaxHints('<slider images="…" interval="3" />')``` 改前 `[]`、改后 1 条指向容器式的提示；成对写法 `<slider …></slider>`、`<slider …/></slider>`、单图成对写法仍返回 `[]`（不误报）。真实浏览器对照见 `target/probe/browser/browser_summary.md` 的 `blk-slider-selfclose` 行；渲染 API 侧 `R4-A` 356 字符 / 0 `<svg>` vs `R4-B`/`R4-C` 1141 字符 / 1 `<svg>` 见 `target/probe/round6_r2r4.md` | 第六轮 | `ai/ScheduledArticleTools`（新增 `SLIDER_TAG_OPEN` / `SLIDER_TAG_CLOSE` 与 `sliderTagWithoutClosingTag`，判据是「有没有 `</slider>`」而不是「有没有自闭合斜杠」）、`ai/ScheduledArticleToolsDraftTests`（新增 1 例，22→23） |

| D41 | **打开稿件失败时编辑器永远转圈（软删稿件最容易撞上）**：`ArticleEditorView.vue` 的 `load()` 失败只把消息写进 `error`，模板末尾却是**无条件**的「正在打开文章…」转圈块——没有 `v-else-if="error"` 分支，失败态被加载态盖住，用户看到的是**一个永远不会结束的转圈**，一个字的提示都没有 | 红→绿：对**软删稿件**（文章 5，`DELETED=1`，API 404 `文章不存在`）跑真实 SPA，改前 `openError=false` / `hasSpinner=true` / 页面文字 `正在打开文章…`；改后 `openError=true` / `hasSpinner=false` / 页面文字 `文章不存在 返回文章列表`。量法与截图见 `target/probe/browser/run-article.mjs` → `articles_result.json`、`shots/articles/5.png` | 第七轮 | `webui/src/views/ArticleEditorView.vue`（新增 `v-else-if="error"` 错误态 + `CircleAlert` 图标 + 「返回文章列表」按钮）、`webui/src/style.css`（`.page-loading.open-error`） |
| D42 | **MARKFLOW 成稿把站点域名烧进正文**：`CONTENT_HTML` 里的图片地址是 `http://localhost:8081/uploads/<32位hex>.png`，换部署/换域名后编辑器与预览整片裂图（微信同步侧反而一直正常，因为它只看路径不看域名） | 全库 **19 篇** MARKFLOW 稿无一例外带着该前缀（含当天 09:29 生成的最新一篇）；同库 PROMPT 稿是干净的相对路径。红→绿反证：把判据改回修复前，`ArticleLocalAssetUrlTests` 立刻 4 例失败，`but was` 的字符串与库内 19 篇**逐字节同形**（`round8_url_fix_redgreen.txt`） | 第八轮 | `article/ArticleService`（`LOCAL_ASSET_SRC` 的域名部分改为可选）、新增 `article/ArticleLocalAssetUrlTests`（6 例） |
| D43 | **`:::case-flow` 的保存自检把行首 `-` 当成可选**：`*` / `+` / `1.` / 裸行 / 漏 `[标签]` 五种写法上游**整块归零**（产物 0 字符、`ok:true`、`warnings:[]`）却一路放行 | `round8_caseflow_bullet.txt`：7 种列表符号 × 容器式/标签式逐个实测，只有「行首 `-` + `[标签]`」有产出，其余 5 种全 0 字符（两式结果逐项相同） | 第八轮 | `ai/ScheduledArticleTools`（`CASE_FLOW_LABEL` 收紧为 `^-\s*\[\s*\S`、新增 `CASE_FLOW_TAG_BLOCK` 覆盖标签式、抽出 `blockHasRowWithoutPrefix`）、`skill/SkillSeeder`、`ai/ScheduledArticleToolsDraftTests` |
| D44 | **技能与自检都不知道 `layout-*` 家族**：16 个名字 × {容器式, 标签式} = **32 组全部把语法原样留在正文**，`meta.warnings` 为空；`guide` 全文不提这些名字 | `round8_unknown_tags.txt`：32 组全部 `leak=True`（容器式如 `:::layout-hero` → 可见文字 `:::layout-heroZQLH内容:::`；标签式 → 字面 `<layout-hero>` 留在正文） | 第八轮 | `ai/ScheduledArticleTools`（新增 `UNSUPPORTED_LAYOUT_TAG` + 替代方案提示）、`skill/SkillSeeder`（新增禁写名单）、`ai/ScheduledArticleToolsDraftTests`（`unsupportedLayoutFamilyIsReported`） |
| D45 | **技能的「自闭合」规则方向写反**：只说了 `<badge>`/`<icon>` 必须自闭合，模型很容易当成通用规则——而 `<slider>` **自闭合整行标签会留在正文且轮播完全不出现**（方向相反）；另 `<hint>` 只有容器式，`:::callout type="danger|success"` 的替代方案措辞有误 | `round8_na_discriminator.txt`：`na-slider-selfclose` 产物 303 字符 / 文字 **0** / svg **0** / 残留 `<slider`；对照标签式「开+闭」（单图）214 字符、svg **0**（无残留，渲染成一张普通 `<img>`）。`round8_callout_types.txt`：`danger`=`#dc2626` ❌、`success`=`#16a34a` ✅，「渲染器不认」的旧措辞不成立 | 第八轮 | `ai/ScheduledArticleTools`（`CONTAINER_ONLY_AS_TAG` 6→7 加 `hint`）、`skill/SkillSeeder`（轮播条点明方向相反、`hint` 入名单、type 列表补 `danger`/`success` —— **技能文案要等下次应用重启才落库，见 §3.13③**）、`ai/ScheduledArticleToolsDraftTests` |

**↑ D30–D39 是 2026-09-13 第五轮（组件渲染能力全量核查）的成果，逐条红→绿证据、77 项组件清单、
两条路径对照表与未修项，见 §3.9。** 这一轮的改动**全在 `webui/`**，Java 侧一行未动。
**↑ D40 是 2026-09-13 第六轮（上真浏览器实测 + R2/R4 端到端复核）的成果，改动在 Java 侧
（保存自检），红→绿证据与全链路复核见 §3.10。**
**↑ D41 是 2026-09-13 第七轮（79 样例全量浏览器实拍 + 公式/轮播真实链路取证）的成果，
改动在 `webui/`（一处模板分支），它是本轮唯一改到业务代码的地方，见 §3.11 ⑤。**
**↑ D42–D45 是 2026-09-13 第八轮（组合条件覆盖 + 9 条 `na` 的技能/自检核对）的成果，
改动在 Java 侧（落库归一 + 保存自检 + 技能文本），组合用例本身没有产生本项目侧的新缺陷，
D43–D45 是从「9 条 `na` 逐条核对」里查出来的，见 §3.12。**

**注意 D13 的教训**：「结论必须核对结构而不是只看有没有样式」——只看「有圆形」会得出错误结论，必须量到 DOM 结构与元素尺寸。

### 已核实**不属于**遗留（不要再当缺口排查）

排查过程中有 5 条曾被记为待办，现已确认落地或无需改动，列在这里省掉接手方的重复劳动：

| 曾被记为缺口 | 实际状态 | 证据 |
|-------------|---------|------|
| 站点公网 Base URL 未配置，渲染前无法把 `/uploads/` 变绝对直链 | **已实现** | `RenderConfig.siteBaseUrl` + `MarkFlowRenderService.absoluteImageUrls`（2026-09-08 Spike 定案：上游对相对路径原样透传） |
| 公众号摘要（digest）无法自动填充 | **已实现** | `ScheduledArticleTools.java:140` 用 `renderResult.summary()` 兜底 digest |
| `default_style` 存在但未接线 | **已接线** | `SkillPromptAssembler` 规则 8：账号 `defaultStyle` 非空注入一行 |
| `/api/skills/preview` 全员可触发出网 | **已限流** | `SkillController.PREVIEW_MAX_PER_MINUTE=30`/用户/分钟 |
| `delegate_research` 子智能体无独立限流 | **已覆盖，无需改动** | 会话级 `MAX_EDITOR_DELEGATE_CALLS=3` + `/chat` 端点 20 次/用户/分钟，两处叠加足够 |

---

## 三、仍然未实现 / 未根治

> **2026-09-11 更新**：本节原 13 项中，10 项本仓库缺口（I1–I10）已全部修复，逐条修复要点标在各自标题后的「✅ 已修复」下；**仍开放的是 U1–U3（上游/网关，需对外提诉求）与 I7（受本机 TUN 代理阻塞的实跑验收）**。

### 3.1 上游依赖：本仓库改不动，需要对外提诉求

#### U1（P0）agent4j 2.3.3 既无读超时也无取消接口
- **症状**：阶段超时（300 秒）到点后，工作线程只能中断并放弃等待；底层 `OkHttpClient` 是 `OpenAIChatModel` 内 `final` 自建的，HTTP 连接与服务端 SSE 流仍然开着。若流随后恢复，它可能与**下一次**运行（可能是重试后的新会话）同时产出。
- **影响**：停滞后仍占网关名额（正是 D2 级联的来源之一）；重试后若两个流都恢复，可能重复执行付费副作用（生图、图片编辑）。
- **根因**：`LLMModel.create` 仅 4/5 参且无超时项；`AgentSessionResult` / `AgentClientSession` 无 `cancel()`/`close()`；全项目不存在 readTimeout。
- **当前规避**：运行级并发闸门把总在飞请求压到 4，配合 300 秒止损，让「卡死」不再无限累积；停滞重试加「零工具调用」前置条件，避免在已有副作用时重试。
- **根治方案**：向上游索取 `cancel()`/`close()` 与可配置读超时；本仓库侧接入点已预留（`AgentInvoker.attempt` 的失败分支与 `StageTimeout`）。
- **还需一并确认的两件事**（来自现场采集，见 5.1）：①核实 agent4j 的流式解析是否**只读 `delta.content` 而忽略 `reasoning_content`**，并用「带工具调用的流式响应」验证工具调用返回体的形状（若确有此问题，会让带推理的模型出现「吐出 0–2 字符后无输出」的假停滞）；②在读层补 readTimeout / 心跳 / 兜底 `onFailure`，并让 `runSubAgent` 的**内层超时与外层超时在报错里可区分**。
- **涉及文件**：`schedule/AgentInvoker.java`、`schedule/StageTimeout.java`、`agent/AgentFactory.java`、`ai/DelegateTools.java`

#### U2（P2）temperature / maxTokens 只存不用
- **症状**：设置页与 agent 覆盖项的这两个字段保存成功但不生效。
- **根因**：agent4j 2.3.3 的 `LLMModel.create` 只接受 4 个参数（provider/baseUrl/modelName/apiKey），没有温度与上限入口。
- **当前规避**：UI 已显式标注「待 agent4j 支持后接线，当前仅保存不生效」（三处：`SettingsView.vue` 的模型档案与 LLM 配置、`AgentsView.vue` 的 agent 覆盖，另有引擎说明条）。
- **根治方案**：上游支持参数透传后，在 `AgentFactory.createModel` 接线（该处已有注释与 WARN 日志指向此点）。
- **涉及文件**：`agent/AgentFactory.java`、`agent/LlmProfile.java`、`agent/AgentDefinition.java`、`webui/src/views/SettingsView.vue`、`webui/src/views/AgentsView.vue`

#### U3（P1）上游网关并发上限 max=6
- **症状**：同时超过 6 个在飞 LLM 请求即被拒：`HTTP 429 concurrent limit exceeded: running=7 max=6`。
- **影响**：这是 D1 事故的**物理根因**。本仓库的闸门只是「不让自己的进程打满它」，上限本身没变。
- **当前规避**：`app.llm.max-in-flight` 默认 4（留 2 个余量给编辑器会话与其他调用方）；429 有界退避（1s/2s/4s，共 4 次尝试）。
- **根治方案**：向网关提额，或申请独立配额；提额后可同步抬高 `LLM_MAX_IN_FLIGHT`。
- **涉及文件**：`resources/application.yaml`、`common/InFlightGate.java`

### 3.2 本仓库未实现 / 未根治

#### I1（P1）多实例部署下并发门禁不完整 ✅ 已修复（2026-09-11）
- **修复**：新增 `llm_lease` 固定槽位租约表（`common/LlmLease`、`common/LlmLeaseMapper`），`InFlightGate` 在注入 Mapper 时改为**数据库租约**模式——槽位号 1..上限 由主键保证跨实例唯一，N 个实例合计不会超过 `app.llm.max-in-flight`。租约带 TTL 心跳（`app.llm.lease-ttl-seconds`，默认 120s），实例崩溃后其它实例可回收名额。DB 不可用时**降级为进程内闸门**（仍守住本实例上限，不会让请求裸奔）；无 Mapper 的单测仍走进程内信号量，旧行为不变。
- **测试**：`InFlightGateTest`（跨实例共享配额、DB 故障降级）、`LlmLeaseMapperIntegrationTests`（同槽位单赢家、TTL 回收、新鲜心跳不被误回收）。
- **残留限制**：过期租约只在**每次申请开始时**回收一次，而 `acquire-timeout-seconds`（60s）小于 `lease-ttl-seconds`（120s）——若实例刚崩溃、四个槽位全被其租约占住，当前这次申请会等到超时后**明确失败**（不会静默卡住），下一次触发才回收成功。需要更快的恢复可把 `LLM_LEASE_TTL_SECONDS` 调小于申请超时。
- **症状**：`InFlightGate` 是**进程内**信号量；Quartz 以 `isClustered=true` 多实例部署时，N 个实例各自放行 4 个，合计可能远超网关上限。`taskLocks` 同样只是 JVM 本地锁，且孤儿运行回收器（reaper）不走该锁。
- **根因**：没有跨实例的共享配额/租约设施。
- **当前规避**：单实例部署完全有效；「RUNNING 中 ID 最小者为唯一属主」的复核逻辑（`findEarliestRunning`）已保证多实例不会同时插出两个运行，但**并发额度**仍不共享。任务级并发门禁已不依赖 `taskLocks`。
- **根治方案**：把额度改为 Redis 信号量/租约（或数据库行级租约），`taskLocks` 一并上移。
- **涉及文件**：`common/InFlightGate.java`、`schedule/TaskExecutionService.java`、`schedule/TaskRunMapper.java`

#### I2（P1）孤儿运行的判定是纯时间阈值，不是「属主是否还活着」 ✅ 已修复（2026-09-11）
- **修复**：`task_run` 新增 `INSTANCE_ID`（本 JVM 启动时生成的 UUID，写入 `TaskExecutionService.INSTANCE_ID`）与 `HEARTBEAT_AT` 两列；执行线程每 `app.schedule.progress-flush-seconds`（默认 15s）刷一次心跳。`StaleRunPolicy.isStale(run, staleHours, now, heartbeatTimeoutSeconds)` **优先按心跳判定**：心跳早于 `heartbeatTimeoutSeconds`（默认 180s）即判定属主失联、可中止——故障自愈窗口从小时级压到分钟级；心跳新鲜则即使开始时间已很久也不动它（多实例下的长任务不会被误杀）。升级前落库的旧行没有心跳列，回退到原有「开始时间 + staleHours」保守判据。
- **测试**：`StaleRunPolicyTest`（心跳新鲜/停止/边界/旧行回退）、`StaleRunRecoveryIntegrationTests`（心跳停止即刻中止、心跳新鲜保护 24h 长任务）。
- **症状**：进程被杀后 `task_run` 留在 RUNNING，只能等超过 `STALE_RUN_HOURS`（当前 3 小时）才被启动自愈中止。单次运行的最坏耗时估算式是 `stageTimeout(300s) × 19 ≈ 1.6h`，所以 3 小时是安全但保守的值——**故障后最长要等 3 小时**才能在历史里看到终态。
- **根因**：`task_run` 没有实例标识或心跳列，无法判断「属主实例是否还活着」，只能按时间猜。
- **当前规避**：阈值从 12h 降到 3h；`abortStale` 是定点 CAS UPDATE（`WHERE ID=? AND STATUS='RUNNING'`），不会覆盖属主已正常写入的终态。
- **根治方案**：给 `task_run` 增加 `instance_id`（取 Quartz `instanceId`）或心跳列，配合 `QRTZ_SCHEDULER_STATE` 的存活实例判定属主；可把自愈窗口从小时级压到分钟级，同时仍满足集群安全。单实例部署也可直接把 `STALE_RUN_HOURS` 调更小。
- **涉及文件**：`schedule/TaskRun.java`、`schedule/TaskRunMapper.java`、`schedule/StaleRunPolicy.java`

#### I3（P2）MarkFlow 渲染服务的 `preview` 字段未使用，KaTeX 上游配套 CSS 丢失 ✅ 已修复（2026-09-11）
- **修复**：`ArticleService.clean(html, markflow)` 在**请求声明为 MARKFLOW 时**额外放行 `<style>` 标签，并为 `:all` 补上 `aria-hidden`/`aria-label`/`role` 属性——公式的配套样式与无障碍标记不再被 Jsoup 整段剥离。非 MARKFLOW 文章的 `<style>` 仍照旧剥离（不放宽注入面）。`preview` 字段的语义上游未明确，本轮不接入（保留为已知差异）。
- **测试**：`ArticleRerenderTests`（MARKFLOW 保留 `<style>`/`aria-hidden`；普通文章仍剥离 `<style>`）。
- **症状**：渲染产物入库后，数学公式丢失 KaTeX 的 `aria-hidden` 标记与上游配套 `<style>`（实测 `<style>` 会被 Jsoup 清洗剥离），公众号侧公式需依赖上游内联样式兜底。
- **当前规避**：渲染响应里的 `html`/`meta`/`theme` 都已使用，只是 `preview` 未用；`sanitizeHtml` 是黑名单语义，未知标签保留。
- **根治方案**：按上游语义决定是否用 `preview`（需要先确认它与 `html` 的差异与适用场景），或对公式类节点做定向样式内联。
- **涉及文件**：`skill/MarkFlowRenderService.java`

#### I4（P2）没有「用留存 Markdown 重新渲染覆盖正文」的产品入口 ✅ 已修复（2026-09-11）
- **修复**：新增 `POST /api/articles/{id}/rerender`（`ArticleController.rerender` → `ArticleService.rerender`）：读 `content_markdown` → 调 `MarkFlowRenderService.render` → 写回 `content_html` 并生成 `RERENDER` revision；摘要为空时用渲染结果的 `summary()` 兜底；非 MARKFLOW 或缺源文时**明确拒绝**。编辑器头部新增「重新渲染」按钮（`ArticleEditorView.vue`，仅 MARKFLOW 文章显示）。
- **注意**：重渲染**刻意绕过** `reconcileRenderedLayout`（编辑器往返防降级逻辑）——否则「只改主题色、正文文本不变」会被误判为降级并把新产物还原成旧版式，等于没重排。为此 `updateWithUser` 增加 `reconcileLayout` 参数，仅重渲染路径传 `false`。
- **测试**：`ArticleRerenderTests`（非 MARKFLOW/缺源文拒绝、仅改主题色也落库新 HTML、源文留存、摘要兜底）。
- **现状**：`article.content_markdown` 已随文章落库（可重排的事实依据），但只有智能体交付前的自动渲染会写它；用户无法在编辑器里发起「拿源文重新渲染一遍」。也就是说：**改了排版模板/主题色后，已有 MARKFLOW 文章不能批量重排**，只能重新跑一次智能体。
- **根治方案**：新增一个「重新渲染」接口/按钮，读 `content_markdown` → 调 `MarkFlowRenderService` → 写回 `content_html` 并生成新 revision。
- **涉及文件**：`article/ArticleService.java`、`skill/MarkFlowRenderService.java`、`webui` 编辑器

#### I5（P2）同维度绑定多枚 IMAGE 技能会注入两段冲突风格 ✅ 已修复（2026-09-11，按产品决策落在绑定侧）
- **修复**：新增 `SkillBindingValidator`，在**绑定处**（`ArticleService`、`WechatAccountService`、`ScheduleTaskService`、`AgentDefinitionService` 的保存路径）校验：同一绑定点选择多枚启用中的 IMAGE 技能即拒绝并提示具体技能名，而不是在组装层静默丢弃一半绑定。其余维度（如多个写作风格）保持可叠加，行为不变。读取技能失败时跳过校验，不因校验设施故障阻断绑定。
- **测试**：`SkillBindingValidatorTest`（两枚 IMAGE 拒绝、单枚/重复 id/禁用项放行、其他维度可叠加、读失败容错）。
- **现状**：WRITING 等多注入维度也是并列注入；IMAGE 目前同样并列，只新增 WARN 点名冲突技能。仅 LAYOUT 单注入（因为它直接决定排版引擎，双引擎指令必然打架）。
- **为什么不自动收紧**：若改成「首个生效、其余丢弃」，用户会**看不见地**丢掉一半绑定，比冲突本身更糟。
- **根治方案**：属产品决策——应在绑定处（账号/任务的技能选择 UI）校验并提示，而不是在组装层静默丢弃。测试 `SkillPromptAssemblerTest.sameDimensionSkillsOtherThanLayoutAreAllInjected` 固化了当前行为。
- **涉及文件**：`skill/SkillPromptAssembler.java`、`webui` 技能选择组件

#### I6（P2）COORDINATOR 子智能体的局部日志与计数 ✅ 已修复（2026-09-11）
- **修复**：`CoordinatorExecutor` 的子智能体运行器改用全量 `workspace.progressListener()`（不再用 `toolCallsOnly`），子智能体的日志行**实时**汇入共享工作区，停滞/超时的那次尝试其局部日志不再随异常丢弃。为避免 `DelegateTools.runSubAgent` 事后 `addAll` 造成同一行记两遍，运行器代理返回的 `Outcome.executionLog()` 置空（日志已实时落盘）。
- **测试**：由既有 `DelegateToolsTest` / `PipelineExecutorTest` 与全量回归覆盖（日志不重复、计数一致）。
- **现状 a**：子智能体的日志仍由 `DelegateTools` 事后整体追加（`ProgressListener.toolCallsOnly` 有意只上报计数、忽略日志行，避免与 `runSubAgent` 的追加重复）。**子智能体停滞时，它自己的局部日志仍会丢**——但停滞原因会以引导文本写进日志，所以不是完全不可诊断。
- **现状 b**：COORDINATOR 会话的返回值不含子 agent 的工具计数（已由工作区累计计数器缓解，历史里能看到总数）。
- **根治方案**：给子智能体也开一条带日志的实时上报通道，并在追加时做去重（例如按会话 id 分流）。
- **涉及文件**：`ai/DelegateTools.java`、`schedule/CoordinatorExecutor.java`、`schedule/AgentRunner.java`

#### I7（P1）COORDINATOR 缺一次真实全绿实跑 ✅ 已闭合（2026-09-13；run#74 首证，run#76/#77/#78 连续三轮复证见 3.8 ①）

- **结果**：run#74（task#5「协调者测试」，`EXECUTION_MODE=COORDINATOR`，2026-09-13 03:37:38 → 03:58:10）
  **端到端跑完**：78 次工具调用、**0 次预算触顶**、`stagesSummary` = `{"revisionRound":2,"researchNotesRounds":1,
  "reviewRounds":1,"toolCalls":78,"toolFailures":2,"degradations":0,"renderWarnings":0,"saved":true}`、
  产出**文章 28**（`MARKFLOW`，5 个 `p-title` + 3 个 `callout` + 1 个 `:::breaking` + 1 处 `<statement>` + 4 个 `<lead>`）。
  主编依次委托了 调研员 / 撰写师 / 配图师 / 审核员 四个角色，并在审核后走了一轮返工（`revisionRound:2`）——
  委托计数与整轮预算这两件「只有实跑才能看到」的事，这次都拿到了实机证据。
- **状态为什么是 `SUCCESS_WITH_WARNINGS` 而不是 `SUCCESS`**：2 次工具失败，都不是降级、也没有卡住：
  ① `browse_webpage - 网页读取失败（HTTP 429）`（目标站限流，属 I9 保留的传输层失败）；
  ② `save_article_draft - 文章摘要不能超过120字`（模型一次写超，下一次调用即成功）。
  `degradations:0`、`renderWarnings:0`、`saved:true` —— 没有「预算触顶」或「阶段中止」。
- **可复现性（本次为什么能跑通，以及跑不通时怎么判定）**：
  - **网络条件是决定因素，不是代码**。本次为**直连**出网；此前失败时经本机 TUN 代理，
    形态是 SSE 首轮即停滞：`netstat -ano | grep :443` 有 ESTABLISHED 连接但无数据、无 outbound 报错、
    agent4j 的 Future 不完成；而同一时刻 `GET /v1/models` 0.05 秒返回 401（HTTP 面正常）。
  - **先跑 SINGLE 再跑 COORDINATOR 做隔离**：`POST /api/tasks/4/run`（单智能体测试，几分钟一轮）。
    若 SINGLE 也在首轮停滞，问题在网络链路；若 SINGLE 正常而 COORDINATOR 卡，才是编排逻辑问题。
  - **照做的步骤**（脚本化在 `target/probe/` 里的 `poll_f10.py` 可改 taskId 复用）：
    1. 确认应用在 8081：`curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOK" http://localhost:8081/api/tasks/5` → 期望 200；
    2. 触发：`curl -s -X POST -H "Authorization: Bearer $TOK" http://localhost:8081/api/tasks/5/run`，返回体的 `id` 即 run id；
    3. 取数：`curl -s -H "Authorization: Bearer $TOK" http://localhost:8081/api/tasks/5/runs`
       （或直查库 `SELECT ID,STATUS,TOOL_CALL_COUNT,ARTICLE_ID,STAGES_SUMMARY,EXECUTION_LOG FROM task_run WHERE ID=?`）；
    4. **判绿四条**：`STATUS` 为 `SUCCESS`（或确有降级项的 `SUCCESS_WITH_WARNINGS`）／`STAGES_SUMMARY.degradations=0` 且
       `saved=true`／`TOOL_CALL_COUNT` 未达整轮预算（COORDINATOR 总 200、主编 48）／`ARTICLE_ID` 非空且文章 HTML 里能看到当轮组件。
- **附带观察（未当缺陷处理）**：返工轮里主编把整条委托链（含调研员）又跑了一遍，所以 `toolCalls` 从首轮的约 40 涨到 78。
  是否只重跑「写作 + 审核」属于产品取舍，本轮未改。
- 证据文件：`target/probe/run74_evidence.txt`（含全量 170 行执行日志与逐条委托记录）。
- **对上游的诉求**：流式链路在 TUN 代理下静默停滞这一条已按「现象 / 最小复现 / 期望行为 / 当前绕过」写进
  `docs/dev/upstream-issues.md` **G2**（四条诉求的汇总清单也在同一文件：渲染 R1–R3、agent4j A1–A4、网关 G1–G2）。
- **涉及文件**：无（环境问题，不是代码缺陷）；此前的「换直连网络后跑一次」诉求到此完成。
- **未验证**：只跑通了 1 次时留下的这条，已在 **3.8 ①** 用连续 3 轮（run#76/#77/#78）打掉：
  4 轮全部正常结束、0 次预算触顶、0 次停滞；残留风险只有联网工具失败与 A4 参数解析失败（都属上游）。
  n≥10 的失败率仍未测。

#### I8（P0）编辑器 AI 对话链路没有硬超时：停滞即永久占名额且不留痕迹 ✅ 已修复（2026-09-11）
- **修复**：`ArticleAiService.executeWithAgentSession` 不再直接 `result.execute(); result.get();`，改为 `StageTimeout.await(result, editorTimeoutSeconds, "编辑器")`；超时抛 `StageTimeoutException` → catch 转成友好文案（`readableLlmError`）→ 发 SSE `error` 事件 → `finally` 走 `finishSession` **释放并发名额**。超时门槛独立配置 `app.llm.editor-timeout-seconds`（默认 1800s，交互式会话不能复用定时链路的 300s）。
- **测试**：由 `StageTimeoutTest` 覆盖硬超时行为；编辑器超时与释放路径为 `finally` 保证（代码审查确认）。
- **症状**：编辑器聊天走的是 `CompletableFuture.runAsync(() -> execute(session, instruction))`，`executeWithAgentSession` 里直接 `result.execute(); result.get();`——**完全不经过 `StageTimeout`**。一旦上游 SSE 停滞，这个线程就永久 WAITING，而它持有的 `InFlightGate` 名额不会被释放（名额在 `finishSession` 才释放，而那个方法永远不会被调用）。因为它不是「运行」，所以 `TASK_RUN` 里没有任何记录，用户只会看到「AI 一直不回」。
- **证据**：16:14 的活体线程栈里 `ForkJoinPool.commonPool-worker-3` 已存活 **4730 秒（约 79 分钟，约 14:55 起）**，栈顶为 `LLMResult.get(LLMResult.java:77)` → `AgentClientSession.executeCommand` → `ArticleAiService.executeWithAgentSession` → `ArticleAiService.execute`（持锁中）；该 dump 里共有 2 条线程阻塞在 `LLMResult.get`，即 2 个网关名额被白占。
- **影响**：并发额度默认只有 4，一个卡死的编辑器会话就永久吃掉 1/4；叠加定时链路后对可用额度的影响是持续的、要重启进程才消失。
- **根治方案**：把 `StageTimeout.await` 复用到编辑器会话（可直接包住 `result.execute()/result.get()`）；超时时释放名额、并通过 SSE 的 `error` 事件告诉浏览器「会话已超时」，而不是静默挂住。注意编辑器是**交互式**的，超时值应与定时链路不同（用户在场，等 300 秒太短），建议单独一个配置项而不是复用 `stage-timeout-seconds`。
- **涉及文件**：`ai/ArticleAiService.java`（`execute` / `executeWithAgentSession` / `finishSession`）、`schedule/StageTimeout.java`

#### I9（P2）`browse_webpage` 抓取失败（403/404）被计为工具失败 ✅ 已修复（2026-09-11）
- **修复**：`SafeWebService` 改用真实浏览器 User-Agent 并补 `Accept-Language`（降低被反爬误拒）；403/404/410 抛专用 `PageUnavailableException`（携带状态码），`browse_webpage` 捕获后返回 `{"skipped":true,"message":"该网页当前不可访问（HTTP N…），已跳过…"}` 的引导文本，**不再计入工具失败**；5xx 等传输层故障仍按失败抛出。
- **测试**：`BrowseWebpageToolTests`（403/404 跳过、500 仍失败）。
- **症状**：run#36 的执行日志里出现 `工具失败：browse_webpage - 网页请求失败（HTTP 403）` 与 `（HTTP 404）`。这是与 SSE 停滞无关的独立缺陷，但会让运行落到「成功（有警告）」，噪声掩盖真正的失败。
- **待查**：是否缺 User-Agent 导致被目标站反爬；404 是否应视为「可跳过」而不计失败。
- **涉及文件**：`ai/ArticleMediaTools`/相关网页抓取工具、`schedule/TaskWorkspace.addToolFailures`

#### I10（P2）RUNNING 期间 `TASK_RUN` 无任何进度可读 ✅ 已修复（2026-09-11）
- **修复**：`TaskExecutionService.executeRun` 在取得并发名额后启动一个周期任务（`progressExecutor`，间隔 `app.schedule.progress-flush-seconds` 默认 15s）：工作区已建立则 `TaskRunMapper.updateProgress` 刷新 `MODE`/`EXECUTION_LOG`/`TOOL_CALL_COUNT`/心跳，未建立时只刷心跳；收尾 `finally` 先取消该任务再 `finishRun`。`WHERE ID=? AND STATUS='RUNNING'` 的 CAS 保证终态不被运行中快照覆盖。
- **测试**：`StaleRunRecoveryIntegrationTests`（运行中字段可读、收尾后迟到快照不生效）。
- **症状**：`TASK_RUN` 的 `MODE`、`EXECUTION_LOG`、`TOOL_CALL_COUNT` 都只在收尾时落库，所以一条 **RUNNING 的行永远是 `MODE=NULL`、日志为空、计数 0**。运行期间想知道「它跑到哪一步了」只能抓线程栈（jstack）。
- **说明**：本轮的 `ProgressListener` 让**失败时**日志能留下（D3），但没有改变「运行中不落库」这件事——工作区是内存态。
- **根治方案**：给运行中的进度做周期落库（例如每 N 秒或每 N 行刷一次，或单独一张 `task_run_progress` 表），或提供一个查询运行中工作区的诊断端点。
- **涉及文件**：`schedule/TaskExecutionService.java`、`schedule/TaskRunMapper.java`、`schedule/TaskWorkspace.java`

### 3.3 只影响开发环境、不影响产品行为的已知事项

- **模型上下架**：排查期间 `agnes-3.0-flash` 曾从网关消失（`model_not_found`），临时改用 `deepseek-v4-flash-0731`，随后用户换回并经直连探测确认恢复 200。**请在设置页确认当前 `LLM_PROFILE`(ID 1) 的 `MODEL_NAME` 是预期模型**。
- **模型间歇性不发工具调用**：#18/#24 出现过「整轮不发工具调用导致未提交草稿」的失败，属模型侧间歇行为，非代码缺陷。
- **开发库留有验收数据**：`TASK_RUN` 中 #34（自愈中止）、#35/#36/#38（停滞）、#39（闸门拒绝）是本次实机验收的真实证据，**未清理**，请勿误认为线上脏数据；#37 是已删除的 UI 夹具。

### 3.4 对并行会话「剩余工作」的逐条核销

并行会话（2026-09-11 16:14–17:30，只读）抓了活体线程栈，产出 `docs/dev/agent-session-timeout-handoff.md`，其实体内容**已合并进本份第五、六节**。它「第五节 剩余工作」的 7 项与本份的逐条对应关系：

| 它的剩余工作 | 本份状态 |
|---|---|
| 1. 编辑器链路加硬超时护栏 | **仍开放** → 即 I8（本份置为最高优先） |
| 2. 让停滞可恢复（阶段级有界重试） | **定时链路已完成**（停滞重试 1 次 + 每次重建会话，第②期）；编辑器链路随 I8 一并做；**根治仍待 U1** |
| 3. 闸门策略产品决策 | **其前提需更正**，见下 |
| 4. 工具失败不得记 SUCCESS | **已修** → D5（本轮第③期） |
| 5. 时间戳混源 | **已修** → D4（本轮第③期） |
| 6. agent4j readTimeout / cancel | **仍开放** → U1 |
| 7. `browse_webpage` 403/404 | **仍开放** → I9 |

> 第 4、5 项在它采集时记为「未见 / 待处理」，是因为当时运行的是补丁前的实例；本份给出的证据（run#34 的时间戳、`SUCCESS_WITH_WARNINGS` 终态）晚于它，**以本份为准**。

**一处曾经的错误推断**：它初稿依据 run#39 在 `LLM_MAX_IN_FLIGHT=1` 下 5 秒失败，推断「一次 COORDINATOR 运行天然要占 ≥2 个名额（主编 + 子智能体）」。这在当前设计下**不成立**——闸门只在两处取名额：`TaskExecutionService.java:136`（**运行级，一次运行占一个**）与 `ArticleAiService.java:220`（编辑器会话占一个）；`AgentRunner.runWithLimit` 本身不取名额，嵌套的子智能体在同一个运行内不重复占位。run#39 失败只是因为先到的 run#38 占住了唯一名额。**因此默认 4 是够的，不必按「一次 COORDINATOR 占 2」上调**；上限也不能超过网关的 6（见 U3）。该会话已于 17:40 自行更正此点。

### 3.5 第二批（2026-09-11 晚）：本轮新发现并已全部修复

> 触发点：用户报「好几个定时任务都失败到『子智能体工具调用超过上限 24 次，已中止』」＋「MarkFlow 精排版式没有 100% 复刻 MARKFLOW 的渲染能力」。以下四条均已落地并验证，**不是待办**。

#### F1 工具预算按阶段分档 + PIPELINE 阶段失败可降级（D14）✅ 已修复
- **定性证据**：run#46（task#2「每日科技早报」，PIPELINE）的调研子智能体在第 25 次调用被中止，**前 25 次全部成功**（`search_web`×20 + `browse_webpage`×5，零失败零重试）——是在正常换关键词检索、逐个打开来源核对，不是重试循环。上限 24 对写作/审核是合理额度，对宽口径调研低于正常工作量；且 PIPELINE 是**唯一**让整轮失败而非降级的链路（COORDINATOR 的 `DelegateTools.runSubAgent` 会把超限转成引导文本）。
- **修复 a（额度）**：新增 `schedule/ToolCallBudget`，按阶段取额度——调研 `40` / 写作·配图·审核 `24` / 主编 `48` / 整轮（子智能体累计）`120`；配置键 `app.schedule.tool-calls.*`。`PipelineExecutor.runStage` 与 `DelegateTools.Budget.subAgentLimit` **同源**取额度，避免「执行器按 40 跑完、这里按 24 判超限」的自相矛盾日志。
- **修复 b（降级）**：`PipelineExecutor.runStageOrContinue`——调研/配图/审核阶段会话中止（超限 / 停滞 / 硬超时）时，只要现有产出仍可用就记降级并继续后续阶段；写作阶段仅在草稿已落盘（`save_article_draft` 已调用）时可降级，否则原样抛出。降级写入执行日志 + 计入 `TaskWorkspace.degradationCount()`，运行终态成为 **`SUCCESS_WITH_WARNINGS`** 而不是干净的 `SUCCESS`。
- **测试**：`ToolCallBudgetTest`、`PipelineExecutorTest`（阶段降级、写作阶段不可降级、终态带警告）、`DelegateToolsTest`（整轮预算）。

#### F2 COORDINATOR 主编超时 300 → 1800 秒（D15）✅ 已修复
- **定性证据**：run#41/#42/#47/#48 的失败消息与卡点完全一致——「智能体会话超时（300 秒未结束）；卡点：最后活动为『调用工具 delegate_*』，距今 0–1 秒；已调用工具 1–7 次」，即**主编侧无事件、子智能体正在正常运行**时被硬超时杀掉。
- **修复**：`app.schedule.coordinator-timeout-seconds` 独立配置，默认 `1800`（主编一次会话覆盖全部委托，量级与单阶段会话不同；编辑器链路同量级本就用 1800）。单阶段会话仍用 `stage-timeout-seconds=300`。
- **注意**：该值与 `stale-run-hours` 绑在同一个耗时估算式上（注释已写明），改一个必须同步改另一个。

#### F3 MarkFlow 精排版式保真（D16 / D17）✅ 已修复
- **主题色（版式漂移的真凶）**：渲染产物 HTML 里**反推不出**主题色（颜色散落在几十条内联样式里），此前它只活在「本轮调用方传了什么」的内存态里、落库即丢，于是 `POST /api/articles/{id}/rerender` 只能传 `null`，渲染服务回落默认翡翠绿 `#27ae60`——一篇科技蓝 `#0984e3` 的文章重排一次就整篇漂色。
  - 新增 `article.theme_accent` / `article.theme_dark` 与 `article_revision` 的同名列；贯通 `ArticleRequest`、`applyLayout`（请求不带时沿用库中值）、`updateWithUser`、`sameEditableContent`、`snapshot`、`rollback`、`ArticleMapper.updateContent`、`ArticleAiService.commitSession`、`TaskExecutionService`、`ArticleService.rerender`（缺省取留存值，落库取**渲染服务回报的实际生效值**）。
  - `ScheduledArticleTools.DraftState.renderBeforeDelivery` 改为留存**渲染服务回报的实际生效色**而非请求值（模型没显式给色时请求值就是 `null`）。
  - ⚠️ **踩坑记录**：`ArticleMapper.updateContent` 里一度漏掉这两列的赋值，主题「看起来传了、实际没落库」。`ArticleRerenderTests` 只断言「传了什么」测不出来，是新加的**服务端集成测试** `MarkFlowArticleLayoutPersistenceTests` 断言落库值才暴露的。**教训：断言传参 ≠ 断言落库；单测到 mapper 边界就断了，关键状态要补一条走真实库的集成测试。**
- **MARKFLOW 正文清洗从白名单换黑名单**：Jsoup 白名单对不认识的标签是**解包**——标签与它的内联样式一起消失、只留文字；公式的 MathML/KaTeX 结构、图表的 `<svg>`、`<mark>/<kbd>` 都是这样被拆掉的。渲染服务本身是黑名单语义（`MarkFlowRenderService.sanitizeHtml` 注释：「上游新增组件标签不被误杀」），落库侧必须一致。新的 `ArticleService.cleanMarkflowBody` 保留未知标签，但仍移除 `script/iframe/object/embed/link/meta/base/form/noscript/template`、`on*` 事件属性与 `srcdoc`/`formaction`，并把 `javascript:`/`vbscript:` 的 `href`/`src` 置为 `#`。**PROMPT 引擎仍走原白名单，行为不变**（不放宽注入面）。
- **编辑器往返丢段落样式（前端）**：`webui/src/editorExtensions.js` 的 `ParagraphStyle` 扩展**一个属性都注册不上**——TipTap 的 `addGlobalAttributes().types` 只认数组 / `"*"` / `"nodes"` / `"marks"`，其余值（含 `undefined`）一律解析成空列表。该扩展没有 `addOptions()`，于是 margin / 首行缩进 / 行距 / 字间距既没被专用扩展接管、又被 `PreservedInlineStyle` 当作「已由专用扩展接管」过滤掉，**每次正文有改动的编辑器往返都静默丢失**（只改标题时会被 `reconcileRenderedLayout` 还原，所以不看正文的场景测不出来）；工具栏的七个段落样式下拉也因此永远读不到值、选中即抛异常。修复：`addOptions()` 显式给出 `types: styledBlockTypes`，保证「被过滤掉的必有扩展接手」这条不变量。同时 `styledBlockTypes` 与 `PreservedRenderId` 补上 `codeBlock`（渲染服务的 `<pre style>` 配色/内边距/圆角此前整段丢失）。
- **`formatForWechat` 引擎感知**（D17）：MARKFLOW 正文只给**完全没有 margin 声明**的段落补 `margin-bottom:16px`，渲染器声明过的段落一律尊重原值。
- **编辑器「重新渲染」按钮**：`ArticleEditorView.vue` 在 `layoutEngine==='MARKFLOW'` 时显示，先落库未保存的改动再 `POST /api/articles/{id}/rerender`。
- **测试**：`ArticleRerenderTests`（非 MARKFLOW/缺源文拒绝、仅改主题色也落库新 HTML、主题沿用/覆盖/派生/回退、MathML 与 `<mark>`/`<kbd>` 保留、危险元素与 `javascript:` 仍被剥离、PROMPT 仍剥 `<style>`）、`MarkFlowArticleLayoutPersistenceTests`（主题随文章落库、编辑器保存不带主题字段不丢、切回 PROMPT 清主题）、`ScheduledArticleToolsDraftTests`（留存渲染器回报色、上游未回报时退回请求值、幂等、PROMPT 跳过）。

#### F4 渲染式排版任务的 `save_article_draft` 说明与实际引擎不一致（D18）✅ 已修复
- **根因（这是用户报的「精排版式没有 100% 复刻」的最后一环，也是真正的主因）**：`save_article_draft` 的工具说明与 `contentHtml` 参数说明是**硬编码的指令式排版文案**——「正文必须使用系统提示中的公众号视觉模板生成完整内联样式HTML……禁止使用ul、ol、dl或table」。`@ToolInfo`/`@Param` 是**类级注解、跟着类走**，MARKFLOW 任务因此复用了这一份。工具说明比系统提示里的【MarkFlow 语法指令】更具体，模型照它执行：**把公众号模板 HTML 当成 Markdown 源文提交**。渲染服务对 HTML 是原样透传（Markdown 允许内联 HTML），于是精排一次都没生效、颜色写死在 HTML 里导致 `accent` 完全无效。
- **对照证据（同库同时段，能反证）**：文章 11/13/14 的 `CONTENT_MARKDOWN` 首行分别是 `<article>`/`<p style="text-align: center; …">01</p>`/`<div style="max-width: 100%; … color: #1a1a1a; …">`——手写模板 HTML；文章 12 恰好是 `<lead>`/`<p-title number="01" …>` 的真 MarkFlow 语法。喂 HTML 的那几篇渲染出来只有外层 `<section><p style="…">` 包装；喂 MarkFlow 语法的第 12 篇渲染出了 `data-block="ptitle"`、`font-size:60px` 的淡色章节号、`CHAPTER 01` eyebrow、flex 布局——**同一套渲染服务，差别只在输入的语法**。
- **修复**：`ScheduledArticleTools.all(state)` 按 `DraftState.layoutEngine()` 选择保存工具——PROMPT 走原来的 `SaveDraftTool`/`SaveDraftParam`（说明文案一字未改），MARKFLOW 走新的 `SaveMarkflowDraftTool`/`SaveMarkflowDraftParam`（工具名同为 `save_article_draft`，正文参数改名为 `contentMarkdown`，说明文案改为「必须是 MarkFlow 语法 Markdown 源文，不要手写 HTML 标签、内联样式、颜色或字体，版式与配色由渲染服务按主题色生成」）。保存语义仍只由 `layoutEngine` 决定（与调用方用了哪个参数类无关），避免模型用参数类把引擎语义带偏。
- **可观测性**：MARKFLOW 正文命中「内联样式块」判据（`<(div|section|p|h[1-6]|table|ul|ol)\b[^>]*\bstyle\s*=`，MarkFlow 组件标签不带 `style` 属性，不会误伤）时 `log.warn`，下次若再有模型写 HTML 能直接在日志里看到，而不是只能靠事后翻库。
- **活体验收（2026-09-11 23:50，新构建重启后）**：
  - task#4（SINGLE，绑定 skill#4「MarkFlow 精排版式」）run#61 → **SUCCESS**，34 次工具调用，产出文章 15。
  - 文章 15 的 `CONTENT_MARKDOWN`：`grep -c 'style="'` = **0**，含 YAML front-matter、`<title type="DA02" label="BREAKING">`、`<p-title number="01" …>`、`<lead>`；`THEME_ACCENT=#27ae60`、`THEME_DARK=#1e8449`。
  - 文章 15 的 `CONTENT_HTML` 是**真精排**：`box-shadow`、`border-radius:14px`、`linear-gradient(135deg, …)` 的大卡片、主题色 `#27ae60` 落在 eyebrow 与阅读时长上。
  - 主题色这次真的生效：`POST /api/articles/15/rerender {"accent":"#e74c3c"}` → `has #e74c3c: True | has #27ae60: False`，`revision 1 → 2`，`contentMarkdown` 仍是 MarkFlow 语法。**反例对照**：同一条接口打在修复前的文章 14（HTML 源文）上是 `has #e74c3c: False | has #0984e3: True`——那正是「换主题无效」的现场。
  - **第二次活体验收（run#63，PIPELINE，2026-09-12 凌晨）**：文章 17 的 `LAYOUT_ENGINE=MARKFLOW`、`THEME_ACCENT=#27ae60`，`CONTENT_MARKDOWN` 中 `style="` 计数 = **0**，正文是真 MarkFlow 语法（`<lead>` / `<statement>` / `<engage-card title=… subtitle=…>` + Markdown 章节），`CONTENT_HTML` 含 `border-radius` 等渲染器产物。**这说明 F4 的修法在两条不同链路上都成立**（SINGLE 的文章 15 与 PIPELINE 的文章 17）。
- **测试**：`ScheduledArticleToolsDraftTests` 新增 4 例（断言**送给模型的 schema 文本**：MARKFLOW 必须含「MarkFlow 语法 Markdown」且不得出现 `contentHtml`/「内联样式HTML」、参数名为 `contentMarkdown` 且说明写明「不是 HTML」；PROMPT 侧锁死原文案；MARKFLOW 工具提交的正文原样成为 `content_markdown`；手写 HTML 判据与 MarkFlow 组件语法可分）。
- **教训**：**工具 schema 是模型真正遵循的那一份说明，它必须与系统提示同向**；注解是静态的，凡随运行时状态变的语义（引擎、场景）都要在装配层分支，不能指望「系统提示里写了」就够——两份互相矛盾的指令，模型不一定选你以为的那份（文章 12 与 14 的差别就是这种不确定性的实证）。

#### F5 预算触顶不再等于白干：收尾工具宽限 + 可执行的超限提示 + 调研额度按实测上浮（D19）✅ 已修复

> 来源：run#62（task#2「每日科技早报」，PIPELINE，新构建）的 240 行执行日志——**修复 F4 之后的实跑**，所以这里是「流水线已经能跑完」之后剩下的问题。

- **症状**：调研子智能体总共发起 **47 次**调用，**前 40 次全部成功、零失败**（在换关键词检索、逐个打开来源），然后连调 **3 次 `save_research_notes` 想把简报交出来**，每一次都被同一套预算拦下——40 次成功检索的产出**一个字都没存下来**，写作阶段拿到的是一句「未产出调研简报，写作阶段将在没有它的情况下继续」。**预算是用来拦失控检索的，不是用来拦「把已有成果交出来」的。**
- **第二个问题**：模型读到「子智能体工具调用超过上限 40 次，已中止」后并不明白该收手，又连调 7 次工具（4 次 `search_web` + 3 次 `save_research_notes`），全部被同一句拒绝。这段文字**会作为工具结果回给模型**（回调里抛出的异常经 agent4j 转成该工具的失败原因），所以它必须给出下一步动作，而不是只宣告终止。
- **修复 a（收尾工具宽限）**：`ToolCallBudget.TERMINAL_TOOLS` = `save_research_notes` / `save_article_draft` / `submit_review` / `set_article_draft_cover`，预算用尽后仍放行 `TERMINAL_GRACE = 3` 次；超限的**非**收尾工具照旧拒绝。宽限必须有界——`save_research_notes` 是**追加**语义，不封顶会被重复简报刷满工作区。
- **修复 b（兜底判据换口径）**：`AgentInvoker` 的兜底护栏原本按**总计数**判超限，会把「预算用尽后放行的收尾工具」也判成超限、让正常收尾变成阶段失败；改为按**被拒次数**（`rejectedOverBudget`）判。
- **修复 c（可执行提示）**：超限文本改为「…已中止工具：X。预算已用尽，不要再检索、浏览或读取；请立即用收尾工具（save_research_notes / save_article_draft / submit_review）提交已有成果，或直接输出最终回复。」这也让 PIPELINE/SINGLE 的处置与 COORDINATOR 一致——`DelegateTools.Budget` 早就是「超限返回引导性错误文本而非中断会话」。
- **修复 d（额度按实测上浮）**：`DEFAULT_RESEARCH` 40 → 60，`application.yaml` 同步。40 是按 run#46「需要 >24」定的；run#62 的 47 次尝试说明它对宽口径任务仍不够，而「每次都换关键词、零失败」的检索不是失控。
- **修复 e（协议补一条）**：`AgentProtocols.RESEARCH` / `WRITING` 各加一条「工具调用有预算上限：先提交成果再决定是否继续；被拒绝的工具不要反复重试同一参数」。事先告知比事后拒绝便宜。
- **测试**：`AgentInvokerTest` 新增 2 例（超限后收尾工具放行且会话正常结束、宽限有界——第 4 次收尾调用被拒），并把超限提示的可执行文案纳入断言；`ToolCallBudgetTest` 新增 1 例（收尾工具集合覆盖四个阶段的交付入口且宽限有界）。
- **活体验收（run#63，task#2「每日科技早报」，PIPELINE，2026-09-12 凌晨，修复后重启的构建）**：终态 `SUCCESS_WITH_WARNINGS`，90 次工具调用，产出文章 17，`stagesSummary` 为 `{revisionRound:2, researchNotesRounds:1, reviewRounds:3, degradations:0, saved:true}`。
  - **宽限真的被走到了**：执行日志第 155 行 `【审核】预算已用尽，放行收尾工具：submit_review`——审核阶段把 24 次预算用尽后，`submit_review` 仍被放行，审核结论落盘、返工轮次正常推进。**若没有这次修复，这里就是又一次「审核结论整段作废」**（与 run#62 的调研简报同型）。
  - 调研阶段这次没有触顶（60 次额度内自行收尾）：日志 `【调研】调研简报已落盘（2663 字）`，**没有**再出现「未产出调研简报」。
  - 全轮 3 次工具失败都是外部网络问题，非缺陷：`import_web_image - HTTP 403`、`search_web - Connection reset` ×2，模型均自行重试/绕开。
  - 收尾时 `degradations:0`——这轮没有再出现阶段降级，PIPELINE 从「会整轮 FAILED」到现在「一次跑完且产出完整」。
- **后续活体验收（run#68，task#2，PIPELINE，2026-09-12 12:2x，F7 的构建）**：终态 **`SUCCESS`（干净的成功，无警告）**，110 次工具调用、**0 次工具失败**，文章 22，`stagesSummary = {revisionRound:2, researchNotesRounds:1, reviewRounds:3, toolCalls:110, toolFailures:0, degradations:0, renderWarnings:0, saved:true}`。
  - **宽限这次走了两次，而且是同一个阶段的连续收尾**：`【配图】预算已用尽，放行收尾工具：set_article_draft_cover`、`【配图】预算已用尽，放行收尾工具：save_article_draft`——配图阶段把 24 次预算用尽后，**封面与含图的成稿都交了出来**。修复前这一阶段的配图工作会整段作废（正是 D19 的形态）。
  - 调研阶段在额度内自行收尾：`【调研】调研简报已落盘（1903 字）`。
  - 文章 22 是**真精排**：`LAYOUT_ENGINE=MARKFLOW`、主题科技蓝 `#0984e3`/`#0769b5`；源文 3194 字符、`style="` 计数 **0**、含 6 个 `<p-title>` 与 `<lead>`；产物 18599 字符、`data-block` ×6、`border-radius` ×14、`linear-gradient` ×1、**字面 `:::` 0 处**。
  - `stages_summary` 里的 `renderWarnings: 0` 说明 F7 的字段已接通，且这次渲染确实没有降级（与产物零字面语法互相印证）。

#### F6 仍未闭合的差异与降级源（明确列出，避免重复排查）

**MarkFlow 侧（渲染能力本身）**
- 渲染服务的 `preview` 字段语义上游未定，本项目未接入（见 I3）。
- 公式的 MathML 结构能原样落库了，但**公众号编辑器是否保留 MathML** 未验证（公众号侧可能仍需内联样式兜底）；KaTeX 配套 `<style>` 已放行，但公众号对 `<style>` 的过滤策略未实测。
- `<svg>` 是否真的出现在 MarkFlow 产物里未取到样本；活体产物（文章 15/16）观察到的是**纯内联样式、无 class 承载版式**，与 D17 的修法方向一致。
- **（2026-09-12 探针更新）`meta.warnings` 在两次刻意制造的降级里都没有出现**：未闭合的 `:::compare`、`:::steps` 都返回 HTTP 200 + `ok:true`，`meta` 里只有 `title`/`summary`。上游语法指令（技能文档）把它写成硬要求，但**在本部署上取不到**。因此项目侧的降级判据改为以「产物里有没有字面语法」为准（见 F7），`meta.warnings` 保留为「有则收录」。
- **（2026-09-12 探针更新）上游语法指令实测 6813 字符**（`GET /__markflow_render`），结构为「一、标准 Markdown / 二、容器 / 三、组件（编号 1–11）/ 四、… / 五、数学公式 / 六、使用规则 / 七、内容组织建议」。**编号组件表只有 11 个**，而 `<steps>`/`<timeline>`/`:::compare` 只在第六节「使用规则」里被提到——**组件清单本身是不完整的**，别把「不在编号表里」当成「不支持」。
- `render_markflow` 的前端链路是「服务端替换占位符后随 SSE 下发」，前端无需改动——这条**不是缺口**（曾一度被记为待办）。

**调度侧（流水线已跑通，剩下的是降级成本）**

> 来源：run#59（165 行日志）与 run#62（240 行日志），都是 task#2「每日科技早报」PIPELINE。两轮终态都是 `SUCCESS_WITH_WARNINGS`、文章正常落库——**PIPELINE 已经不会再因单阶段失败而整轮 FAILED**（D14 的目标，已达成）。下面是剩下的成本，留给接手人按性价比决定。

1. **写作阶段的 300 秒预算偏紧**。run#62 里写作阶段的停摆形态是「卡点：最后活动为『最终回复模型 0 字符』，距今 276 秒」——这是 U1 那次上游不发流结束信号的**真停滞**，300s 杀掉正是设计意图。但 run#59 还出现过另一种：「卡点：最后活动为『调用工具 create_sub_agent』，距今 1 秒；已调用工具 7 次」——**这条不是停滞**，外层 300s 覆盖了整棵子会话树而把正常工作的会话误杀，与 D15（主编一次会话覆盖全部委托）同构。
   - 建议方向：一是给写作阶段单独一档超时（照 `ToolCallBudget` 的分档形状），二是把「总时长超时」改成「**无活动超时**」（`AgentInvoker` 的 `lastActivityAt` 已经现成，只差 `StageTimeout` 改成轮询判空闲）——后者能同时避免「真停滞白等」和「正常工作中被误杀」，但会失去「无限输出的会话永远不结束」这道保险，需要保留一个绝对上限做兜底，属**语义变更**，改前先按 4.2 多采几轮。
   - 注意 `stale-run-hours` 的估算式绑在 `stage-timeout-seconds` 上（`application.yaml` 注释里写的是「阶段超时 300s × 阶段数上界 19」），改一个必须同步改另一个。
2. **agent4j 内置的 `create_plan` / `create_sub_agent` 在定时链路里无条件可用，本项目关不掉**（已核实到字节码，属上游限制）。证据：
   - 两个工具来自 agent4j 自带的 `ink.icoding.llm.core.tool.builtin.CreatePlanTool` / `CreateSubAgentTool`，由 `ink.icoding.llm.core.tool.builtin.skill.OrchestrationSkill` 引入；
   - `AgentClientSession` 构造器**无条件 `new` 这两个工具**，并在私有方法 `getAllTools()` 里 `agent.getTools()` 之后直接 `List.add` 追加，**没有任何开关、配置或开关字段**（`javap -p -c` 反编译可见，常量池里也搜不到对应的 boolean 字段）；
   - 因此「在装配 `AgentClient` 时显式关掉」做不到，只能向 agent4j 提诉求（与 U1 同类），或接受它并给写作阶段留出子会话的时间余量（即上面第 1 条）。
   - 影响：既拉长阶段耗时，也让「工具调用数」的口径难以预估（子会话的调用是否计入需要单独确认）。
3. 另有两处**不是缺陷**的失败调用，不要误判：`save_article_draft - 文章摘要不能超过120字`（模型给了超长摘要，工具按 120 字上限拒绝，模型第二次调用即成功）与 `submit_review - Failed to parse tool param JSON`（模型自己把 JSON 截断了，重试即成功）。

#### F7 渲染降级是静默的：语法没被识别时产物里留着字面标签，而运行仍记 SUCCESS（D20）✅ 已修复

> 这一条直击用户报的「精排没有 100% 复刻」的第二层：F4 修的是「模型没按 MarkFlow 语法写」，
> F7 修的是「写了但渲染器没认出来时，没有任何人知道」。

- **实测（2026-09-12，直接打渲染 API 的探针，可反证）**：
  - 未闭合的 `:::compare` 容器 → 产物里是 `<p style="…">:::compare</p>`，**后续整段内容被吞进容器**：同样输入的规范写法产物 1219 字符，未闭合只有 405 字符；
  - `:::steps` 不在语法指令里（guide 只定义 `:::compare`，步骤流是 `<steps>` 标签）→ 容器标记字面输出；
  - 规范写法（`<steps>` 的五种写法、`:::compare` 闭合、`<lead>`/`<statement>`/`<engage-card>`）产物里**都不留字面语法**——「产物里有没有字面语法」因此是一条干净的判据。
- **两个静默点**：
  1. `MarkFlowRenderService` **完全不读** `meta.warnings`。上游语法指令把「交付前必须检查该字段」写成了硬要求，我们一条都没看过。⚠️ 但探针的两次刻意降级（未闭合容器、`:::steps`）上游**都没有返回这个字段**——它不能当成唯一信号（见 F6 的更新）。
  2. 产物里字面留着 `<steps>` / `:::compare` 时，落库的 HTML **依旧满是内联样式**（外层段落样式是正常的），肉眼与「有没有样式」的粗判都看不出问题，运行终态也照记 SUCCESS。这与 D13 的教训同源：**只看有没有样式会得出错误结论**。
- **修复 a（本地兜底判据）**：`MarkFlowRenderService.detectLeakedSyntax(html)` 扫描产物里未被识别的容器语法与组件标签（代码块内的字面语法是正文内容，扫描前剔除），与上游 `meta.warnings`（`parseWarnings`，有则收录）合并成 `RenderResult.warnings`。命中即 `log.warn`。
- **修复 b（传到运行终态）**：`RenderResult.warnings` → `DraftState.renderWarnings()` → `Draft` 快照 → `TaskExecutionService.completion(..., renderWarnings, ...)`：**非空即 `SUCCESS_WITH_WARNINGS`**，消息写明「有 N 处 MarkFlow 渲染降级（<第一条原因>）」；`stages_summary` 增加 `renderWarnings` 计数。`Draft`/`RenderResult` 都保留旧签名的兼容构造，避免调用方为加一个字段而全量改。
- **修复 c（事前拦截）**：`ScheduledArticleTools.markflowSyntaxHints(markdown)` 在**保存那一刻**就把渲染器一定识别不了的写法回给模型——容器未闭合/多收尾、`:::steps` 这类不存在的容器、未顶格（含 `>` 引用前缀）、以及原有的手写 HTML 告警。保存**不会**因此失败（拦下等于作废这一稿，与 D19 同一教训），但工具结果里带 `warnings` 与「请按提示修正后重新调用 save_article_draft 覆盖保存」。渲染发生在交付前、那时模型已无修正机会，工具结果是它一定能读到的下一段输入。
- **测试**：`MarkFlowRenderServiceTest` 新增 4 例（泄漏判据、代码块不误伤、上游 warnings 解析的容错、渲染结果携带 warnings）；`ScheduledArticleToolsDraftTests` 新增 3 例（警告随快照传递且重新保存后清空、五类语法自检、保存结果带下一步动作且不拦保存）；`TaskRunCompletionTest` 新增 2 例（渲染降级降级终态并说明原因、无降级仍是干净的 SUCCESS）。
- **真实产物复核（临时探针，用完已删）**：把判据打在 2026-09-12 渲染 API 的真实产物上——`<steps>` 三种规范写法、闭合的 `:::compare`、线上文章 17 的落库 HTML 全部 `[]`（零误报），未闭合容器那条命中 1 条。**这条复核是这次修复可信的全部依据**，不要只跑单测就认为判据成立。
- ⚠️ **上面这条「零误报」在 2026-09-13 被 D27 打穿**：那批探针恰好都没写 HTML 实体，所以漏掉了「源码实体不解码」这一类误报（run#75 真报了一次）。判据的可信度只能由**真实轮次的产物**给，探针集的覆盖面永远是个假设——接手时请按 D27 的方式复跑一次。
- **仍不闭合的部分**：见 F6——上游到底会不会在别的降级形态下发 `meta.warnings`、公众号编辑器是否保留 MathML/`<svg>`，本机都无法验证。编辑器「重新渲染」那条小口子已在 F8 补上（降级写进版本说明 + 带文章 id 的日志）。

#### F8 预算额度按真实工作量重定 + 降级判据在真实产物上校准（D21/D22）✅ 已修复

> 来源：用户复跑多个定时任务后报「子智能体工具调用超过上限 24 次，已中止」，并同时指出
> 「MarkFlow 精排版式没有 100% 复刻」。F5/F7 的修复方向是对的，但**额度是按一次事故的个案定的、
> 判据只在手写样例上验过**——这一轮把两件事都拉回实测。

- **D21 额度定得太紧，宽限变成了常态**：F5 的收尾工具宽限本意是「预算拦的是失控检索，不是提交成果」，
  但实测两轮都走到它：run#63 评审单会话 **25 次**、run#68 配图单会话 **26 次**（旧上限 24 之上），
  run#68 整轮 **110 次**也逼近旧上限 120。**宽限天天被用到，说明它不是例外而是主路径**——
  于是把额度定到实测之上：`DEFAULT_STAGE` 24 → **36**、`DEFAULT_TOTAL` 120 → **200**
  （`DEFAULT_RESEARCH` 60 / `DEFAULT_CHIEF` 48 不变），`DelegateTools` 的三个常量与
  `application.yaml` 同步改（`ToolCallBudgetTest.defaultsStayAlignedWithTheDelegateToolConstants` 钉住两处不许漂移）。
- **D21 附带一条判据修正**：「超限被拒」与「这一阶段白干」是两件事。`AgentInvoker` 的兜底护栏原先只看
  `rejectedOverBudget > 0`，会把「被拒过、但随后用收尾工具把成果交出来了」的会话也判成中止——
  一次成功的交付被报成降级，排查的人会去找一个不存在的失败阶段。现在由 `deliverableSubmitted`
  （收尾工具走到 `COMPLETED`）分开：成果已交出 → 记一行「预算超限被拒 N 次，但收尾工具已提交成果，
  本次按已交付处理」后正常返回；没有成果 → 照旧失败。
- **D22 降级判据在真实产物上有 6 处误报**：`detectDroppedBlocks` 写完后拿 2026-09-12 渲染 API 的
  24 个组件真实产物 + 线上文章 22 的落库正文复核（临时探针，用完已删），命中 6 条，逐条查证后
  **全部是判据自己的问题，不是渲染降级**，三种成因：
  1. **比较基准不一致**：产物侧去空白、探针侧没去 —— `<p-title subtitle="ONE SOURCE">` 因探针里那个
     空格被判成「内容丢失」。修法：`longestToken` 在返回前也去掉空白（这是文章 22 的两条误报的成因，
     不是「前言元数据」，别再往那个方向查）。
  2. **把渲染器插入的内容当成了丢失**：`<steps>` 产物是「1第一步内容。2第二步内容。」，条目之间被插了
     序号，源文里跨行拼出来的整块文本永远匹配不上。修法：组件正文**按行**取探头（`longestLine`），不跨行拼接。
  3. **把上游的既定行为当成了丢失**：`<engage type="DA02">感谢阅读</engage>` 的内容由渲染器替换成
     自带文案（语法指令六.8/七.5 推荐的就是只给 type 不给内容），`<breaking label="BREAKING">` 的
     `label` 渲染器根本不输出。修法：`SELF_COPY_COMPONENTS` 排除内容自带的组件；属性探头只认
     `title`/`subtitle`（实测这两个会进产物）。
  校准后同一批真实产物的结果：**24 个组件里只有 `<timeline>` 命中，文章 22 零命中**。
- **D22 顺带证实 `<timeline>` 是静默丢内容**：语法指令六.8 推荐「正文穿插 `<steps>`、`<timeline>`」，
  实测四种写法（`2024年 | 事件`、`<item label>`、纯段落、`type="DA02"`）**产物长度都是 0**，
  与正文混排时前后正文都在、时间线的字一个字不剩，HTTP 200 + `ok:true`，上游也不报 `meta.warnings`。
  这是 `detectLeakedSyntax` 覆盖不到的那一类（标签被**认得**、内容被丢掉），正是 F7 第二条判据的存在意义。
  > **2026-09-12 更正（F10）**：真正的原因是**每行必须三列**「时间 | 标题 | 说明」，不是「`<timeline>` 不支持」。
  > `<timeline>` 写三列实测产物 1030 字符、内容完整；写成两列才 0 字符，且上游会报一条
  > 「timeline 有 N 行不足 3 列（时间 | 标题 | 说明），已被忽略」——F8 记的四种写法恰好都是缺列/无列。
  > 当时据此写进自检的那句「`<timeline>` 渲染为空，请改用 `<steps>`」已删除，改为只报「不足三列」。
- **编辑器重渲染的降级留痕**（补 F7 的最后一个小口子）：`ArticleService.rerender` 此前只在服务端日志里
  留一条 WARN、且日志里没有文章 id。现在有降级时把「N 处版式降级：<首条原因>」写进**版本说明**
  （编辑器版本历史里可见），日志同时带上文章 id。
- **测试**：`MarkFlowRenderServiceTest` +3（静默丢内容的判据、渲染器改写内容不误报、只对会进产物的属性做判据）、
  `AgentInvokerTest` +2（被拒但已交付不算失败 / 没有交付仍失败）、`ToolCallBudgetTest` +1（额度必须留出实测工作量的余量）、
  `ArticleRerenderTests` +1（降级写进版本说明）。
- **活体验收（run#69，task#2「每日科技早报」，PIPELINE，2026-09-12 15:26，F8 的构建）**：终态 `SUCCESS_WITH_WARNINGS`，产出文章 23，`stagesSummary = {revisionRound:2, researchNotesRounds:1, reviewRounds:2, toolCalls:78, toolFailures:0, degradations:1, renderWarnings:0, saved:true}`。
  - **用户报的那条症状没有再现**：全轮 78 次工具调用、**0 次「超过上限 N 次，已中止」**，也没有走到收尾宽限（单会话峰值约 14 次，新额度 36 有充足余量）——上一轮 run#63/#68 的 25/26 次说明旧额度 24 确实压到了正常工作上。
  - **唯一的降级是已知的上游停滞，不是预算**：`【写作】阶段中止（智能体会话超时（300 秒未结束）；卡点：最后活动为「收到模型输出 0 字符」，距今 11 秒；已调用工具 7 次），已按现有产出继续后续阶段`——正是 F6 第 1 条（U1 的不发流结束信号 + 写作阶段的 300 秒预算），流水线按设计降级续跑并交付了文章。
  - 文章 23 是**真精排**：`LAYOUT_ENGINE=MARKFLOW`、源文 2976 字符、`style="` 计数 **0**、含 `<badges>`×2 / `<lead>` / `<statement>` / `<engage-card>` / `<img>`×3；产物 16547 字符、**字面 `:::` 0 处**。
  - `renderWarnings: 0` 与「产物零字面语法」互相印证——**F8 校准后的判据在真实生产路径上跑了一遍，零误报**（这是除离线探针之外的第二份真实证据）。⚠️ 2026-09-13 的 D27 说明这一段的「零误报」只覆盖那一批文章，不能外推（run#75 就报了一次实体误报）。
  - 另注意到一处**已知**现象（U1 的后果，不是新缺陷）：写作阶段被超时放弃后，那次会话的子智能体工具仍在日志里继续出现（第 160/165 行）——agent4j 无法取消，流还在跑，正是 U1 描述的那条。
- **仍不闭合的部分**：额度 36/200 是按 run#63/#68 两轮实测定的，再多几轮宽口径任务后要回看是否还需要上调；
  `<timeline>` 缺列会丢内容属上游行为，本仓库只能提示、不能修（与 U1–U3 同类的对外诉求；F10 已把提示改准）。

#### F9 项目技能与官方语法指令逐条对齐：技能里四条「实测结论」有两条已被渲染器推翻（D23）✅ 已修复

> 来源：用户把官方语法指令原文贴进来问「技能与这个有啥区别」。逐条对完发现差异不在措辞，
> 而在**技能里那四条「与语法指令冲突时以这四条为准」**——它们是十七轮按当时的渲染器记下的，
> 其中两条今天已不成立，而且方向都是**劝模型放弃一条本来能用的好写法**。

- **D23 被推翻的两条**（2026-09-12 逐条打真实渲染 API 复核，可反证）：
  1. **「`:::tip` / `:::warning` 不会被识别」——错**。实测 `:::tip`/`:::note`/`:::info`/`:::warning`/`:::caution`/`:::important`
     六种**都渲染成提示框**，产物里是与 `> [TIP]` 完全相同的 `border-left:4px` 样式，`:::tip 自定义标题`
     还能覆盖默认标题。渲染器支持的容器因此是 **compare + 六种提示框**，不是只有 compare。
     技能原文把模型从一条简写语法上劝退，而 guide 第三节本来就把这六种列为「提示框可用类型」。
  2. **「步骤超过 3 个必须显式写 DA02」——半错**：guide 六.9 说的「超过 3 步自动切换竖向布局」实测确实不生效
     （4 步 = 4 列各 25%、5 步 = 5 列各 20%），但技能里的「**必须**」同样过头——那是**排版取舍**，不是渲染成败。
     已改成「拥挤时主动写 DA02」，不再作为硬规则。
     > **2026-09-12 更正（F10）**：这条**又反过来了**。用 guide 六.9 的官方行格式（每步一行 `- 名称 | 描述`）
     > 复测：3 步 = 3 列各 33%（横向 DA01），**4 步与 5 步都自动切换成竖向卡片**，
     > 产物与显式 `type="DA02"` **完全一致**（len=3105、4 个 32px 圆形编号）。
     > 原先「不自动切换」的观察是旧探针用「每步一个自然段、空行分隔」的过时行格式测的。
     > guide 六.9 是对的，技能文案已改回「4 步及以上自动竖排，不必手动加 type」。
  3. **「`<badge>` 渲染出的文字是 type 值」——错**。实测 `<badge type="tip" title="推荐" />` 产物文本就是
     「推荐」；`text=` 属性、大写 `<Badge>` 也都正常（错误属性会回落到默认样式，不会写错文字）。
     真正会坏的是**写法**而非属性：`<badge type="tip">推荐</badge>`（成对标签）会把 `</badge>` 原样吐进正文
     ——产物文本是 `tip推荐</badge>`，与技能原来描述的「文字变成 tip」是两回事。
  4. **「`:::steps` 把每段拆成直径 38px 的圆形」——不复现**。今天 `:::steps` 一律**退化成普通段落**
     （产物 264 字符、圆形计数 0），四种失败形态（纯段落 / 带序号 / 带列表 / 带 `###`）都试过。
     结论方向没变（**不要用 `:::steps`**），但失效后果已经轻得多：内容还在，只是没有步骤版式。
     > **2026-09-12 更正（F10）**：`:::steps` **本身是对的**，缺的是行格式。用 guide 六.9 的官方行格式
     > （每行 `- 名称 | 描述`，等价于「序号 | 步骤名 | 说明」三列）实测产物 1462 字符、
     > 编号圆点 + 连接线 + 标题/描述两行俱全，与官网示例的步骤流**同一种**。
     > 缺竖线的行才整块降级为普通段落（上游会在 `meta.warnings` 里报一句）。
     > 「不要用 `:::steps`」已从技能删除——保留正确写法、只在行格式不对时提醒；
     > 需要标题栏时改用 `:::steps-horizontal` / `:::steps-vertical`（见 F10）。
- **F9 同时补上 guide 没写、实测会坑人的两条**（都进了技能与保存时自检）：
  - **`<steps>` 的步骤里不能写 `###` 小标题**：产物扁平文本是「1###第一步：准备2准备工作的正文。」——
    `###` 字面留在正文，这一步还被拆成两个格子。这条技能原本写了，保留（并补上新的实测产物形态）。
  - **图注必须紧跟图片/代码块、中间不能有空行**：实测 `![图](url)` 换行接 `图 1: 说明` 会渲染成
    `data-caption-kind="image"` 的居中图注；隔一个空行就退化成普通段落。guide 第一节只说了图注写法，没说这条约束。
    > **2026-09-12 更正（F10）**：这条**不成立**。两种写法（紧跟 / 中间空一行）产物**逐字节相同**，
    > 都是 `data-caption-kind="image"` 的居中图注。据此新增的那条自检已删除（留着会误报正确写法），
    > 对应用例改为 `captionSpacingIsNotReported`（两种写法都断言不报）。
- **顺带核实的一条 guide 不准确处（本仓库只能提示，改不了上游）**：guide 第一节写「流程图用 mermaid，
  系统自动渲染为 SVG 图表」，实测四种 mermaid 写法（`flowchart LR` / `graph TD` / `sequenceDiagram`）
  产物里**都没有 `<svg>`**，只有 `data-block="code"` + `<pre data-lang="mermaid">` 的高亮代码块。
  也就是说流程图是「高亮代码」而不是「渲染成图」——已写进技能，让模型别把关键信息只放在 mermaid 里。
- **代码改动**：
  - `SkillSeeder.MARKFLOW_CONTENT`：四条硬规则重写为「guide 没写清楚或写得不准的地方」，逐条标注实测结论，
    并补上容器白名单、图注约束、mermaid 实情、`<badge>` 自闭合要求。
    （F10 又改了这组文案：`<timeline>` 那条**删除**——真正的问题是缺列；`<steps>` 的「>3 步必须 DA02」也**改准**
    ——实测 4 步及以上渲染器**确实**会自动竖排，guide 六.9 是对的。）
  - `ScheduledArticleTools.markflowSyntaxHints`：容器白名单 `SUPPORTED_CONTAINER="compare"` →
    `SUPPORTED_CONTAINERS`（compare + 六种提示框），报错文案同时说明支持哪些；新增三条自检
    （`<steps>` 内 `###` 小标题、图注与图/代码块隔空行、`<badge>`/`<icon>` 写成成对标签）。
  - `MarkFlowRenderService.detectLeakedSyntax`：新增 `LEAKED_CLOSING` 判据——此前只扫开标签，
    看不见「开标签被认掉、只剩 `</badge>` 留在正文」这种形态。
  - `EditorServiceTools.RenderMarkflowParam` 的参数说明同步（原先写「不要使用行内 `<badge>`」，与实测相反）。
- **测试**：`ScheduledArticleToolsDraftTests` +3（六种提示框容器**不再误报**——这是防回归的核心用例、
  图注隔空行只在断开时报、`<steps>` 内标题与成对 `<badge>` 各自报出）；`MarkFlowRenderServiceTest` +1
  （收尾标签泄漏判据）。
- **真实产物复核（临时探针，用完已删）**：新的收尾标签判据打在 109 个真实产物上——
  **只有两个刻意写出成对标签的样例命中，其余 107 个为零**；技能改动本身的依据是同一批探针
  （`skill_diff_result.txt` / `skill_diff_result2.txt` / `container_result.txt` / `steps_container_result.txt` / `mermaid_result.txt` / `badge_result.txt`）。
- **仍不闭合的部分**：`:::danger` / `:::success` 不在渲染器支持列表（会字面输出 `::: danger`），
  guide 第三节却把 danger 列进了「提示框可用类型」——这是**上游 guide 与渲染器不一致**，
  本仓库只能在自检里拦住，改不了 guide 本身；mermaid 不渲染成图同理。

#### F10 「成品不如 MarkFlow 官网示例好看」的根因：guide 漏写了官网整整一组容器组件（D24）✅ 已修复

> 来源：用户拿官网 `/markflow/` 生成的示例（`MarkFlow 长图文排版工作流.wechat.html`）来对比，
> 指出本项目的产出「没有这个好」。逐块对比后确认不是渲染器能力差异——**同一台渲染器、同一个接口**，
> 差别在**我们喂给模型的语法清单少了官网自己在用的那一整组容器**。

- **对比结论（同一主题、同一渲染器）**：官网示例首屏是「预计阅读时长卡 + 大标题 + 副标题 + 话题标签 +
  阅读路线导航（编号圆点 + 连接线）」，其后是带 `HOW IT WORKS` 小标的步骤卡、`.case-flow` 案例条、
  `:::timeline` 时间线、`:::slider` SVG 轮播。本项目产出的文章（如文章 23）只有 `<badges>`/`<lead>`/
  `<statement>`/`<engage-card>`/`<img>`——**结构组件一个都没用上**，因为模型不知道它们存在。
- **根因（可反证）**：服务端语法指令（`GET /__markflow_render` 的 `guide`，实测 7672 字符）的
  「块级组件」编号表只列了 11 个**标签**组件（`<title>`/`<p-title>`/`<cta>`/`<badges>`/`<statement>`/
  `<lead>`/`<engage-label>`/`<engage-card>`/`<img>`/`<badge>`/`<icon>`），容器只列了 `:::compare`。
  而官网 Web 端（`/markflow/` 的前端 bundle `index-mn0wyJ53.js`）**自己有一份组件注册表**，
  里头多出来的容器里，有 **8 个 guide 全文一次都没提过**：`:::reading-path`、`:::steps-horizontal`、
  `:::steps-vertical`、`:::case-flow`、`:::slider`、`:::callout`、`:::align`、`:::code-block`。
  模型只看得见 guide + 技能，自然只会用最朴素的那几种。
- **⚠️ 2026-09-13 复查更正（措辞曾经说宽了）**：本节原先写作「guide 一个都没写」并列了 11 个名字，
  **其中 3 个其实 guide 有记载**——`:::breaking` 与 `:::timeline` 在第八节「合理搭配组件」下各有一段
  「最小示例」，`:::table` 在第六节第 10 条（表格后必须空一行）里被点名；这三个在 F10 之前就已在白名单里。
  `:::align` 虽然在 guide 里出现过两次，但都是 `<img>` 的 **align 属性**（第九节属性表），
  不是容器，所以仍算「guide 未记载」。以 2026-09-13 重新拉取的 guide 逐名核过：
  `reading-path`/`steps-horizontal`/`steps-vertical`/`case-flow`/`slider`/`callout`/`code-block` 出现 0 次。
  **新增白名单共 9 项 = 上述 8 个容器 + `:::steps` 容器形式**（guide 只写了 `<steps>` 标签，见第九节，
  这正是 F9 时期把 `:::steps` 误判成「不支持」的来源）。
  （证据：`target/probe/probe_guide_recheck.py` → `guide_recheck.txt`。）
- **每一条都用真实渲染 API 核过产物**（2026-09-12，`target/probe/probe_web_components.py` 等，可反证）：
  | 容器 | 实测产物 |
  |------|---------|
  | `:::reading-path` | 编号圆点 + 连接线的章节导航（`width:126px` 节点、`border-radius:50%` 序号） |
  | `:::steps-horizontal` | 横向步骤卡（3 步 → 3 个 `<td>` 各 33%），带 label/title/hint 标题栏 |
  | `:::steps-vertical` | 竖向步骤卡（`border-radius:50%` 32px 序号） |
  | `:::case-flow` | 案例条（**每行必须以 `[标签]` 开头**，否则整块 0 字符） |
  | `:::slider` | `<svg>` + `<animateTransform>` + `foreignObject` 轮播动画，公众号里可动 |
  | `:::callout` | 彩色提示框（与 `> [TIP]` 同款，多一个自定义标题；`type="danger"` 也有效） |
  | `:::align` | 居中/靠右段落 |
  | `:::code-block` | 带 title 的代码块容器 |
- **两处旧结论随之被推翻**（都是「guide/旧探针的写法问题」被记成了「渲染器不支持」）：
  1. **`<timeline>` 不是「渲染为空」**：真相是**每行必须三列**「时间 | 标题 | 说明」。
     实测 `<timeline>` 三列 → 产物 1030 字符、内容完整；两列 → 0 字符且上游报
     「timeline 有 N 行不足 3 列（时间 | 标题 | 说明），已被忽略」。F8 记的「四种写法产物都是 0」
     正是四种写法都缺列/无列。自检已改为**报「不足三列」**而不是劝退 `:::` / `<timeline>` 本身。
  2. **图注与图片之间隔不隔空行都会渲染**：`![图](url)\n图 1: 说明` 与中间加空行两种写法，
     产物**逐字节相同**（都是 `data-caption-kind="image"` 的居中图注）。F9 新增的「图注隔空行」自检
     已删除——那是把旧探针的写法问题当成了渲染约束，留着会误报正确写法。
- **代码改动**：
  - `SkillSeeder.MARKFLOW_CONTENT` 第 2 条新增第 ⑥ 组（**guide 漏写但渲染器支持的容器组件**清单，
    每条附用法与判断场景），并把它与 ①–⑤（guide 写得不准处）区分开——这是「成品比官网素」的直接原因。
  - `ScheduledArticleTools.SUPPORTED_CONTAINERS` 从 10 个扩到 19 个（**新增 9 项 = 8 个 guide 全文未记载的
    容器 + `:::steps` 容器形式**，见上面的复查更正），
    报错文案改为列出全部可用容器；`CONTAINER_LINE` 的名字正则补上连字符
    （原先 `[A-Za-z]*` 会把 `:::steps-horizontal` 截成 `steps`，把**正确写法误判成不支持**）。
  - 新增两条「内容会静默消失」的保存时自检：`:::case-flow` 行缺 `[标签]`、时间线行不足三列
    （`<timeline>` 标签与 `:::timeline` 容器同一条规则）。
  - 新增一条「表格后不空行」自检（guide 六.10；紧随表格的正文会被当成表格注释）。
  - `EditorServiceTools.RenderMarkflowParam` 参数说明同步（补 `:::timeline` 等写法）。
- **活体验证（run#71，2026-09-12 23:41 → 2026-09-13 00:05，PIPELINE，task#2）**：整轮 **158 次工具调用、
  0 次预算中止**，终态 `SUCCESS_WITH_WARNINGS`、`degradations:1`、`saved:true`，产出**文章 25**
  （证据：`target/probe/trigger_f10_run.json`、`poll_f10.txt`、`art25.md`、`art25.html`）。
  正文里首次出现 `:::reading-path`（此前任何一篇文章都没有用过结构组件），渲染产物是完整的
  「READING PATH / 阅读路线 / 5 个章节」导航：5 个 `width:126px` 的节点、`border-radius:50%` 的 01–05
  编号圆点、节点之间的 32px 渐变连接线——与 API 单独探针量到的结构完全一致；另有 `:::compare` 三栏对比卡。
  对照修复前：文章 23 的语法只有 `<lead>` / `<badges>`×2 / `<statement>` / `<engage-card>`，
  一个结构组件都没有。**注入侧也核过**：`POST /api/skills/preview`（task#2 的 skillIds=[4,5]）
  组装出的系统提示 12224 字符，`reading-path`/`steps-horizontal`/`case-flow`/`slider`/`callout`/
  `code-block`/`:::timeline`/`:::table` 全部出现——即「模型看得见这些组件」是实测的，不是推断的。
- **第二次活体（run#72，2026-09-13 01:31 → 01:58，同样 PIPELINE/task#2，跑在补完本轮全部改动之后）**：
  161 次工具调用、0 次预算中止，产出**文章 26**，用的是**另一个**新组件 `:::breaking`——
  产物是完整的开篇大卡（`radial-gradient` 底 + 圆角 16px + 徽章药丸 + 22px/800 大标题 + 副标题 + chips 行），
  即官网示例首屏的那张卡。两轮之间模型分别选了 `reading-path` 与 `breaking`，说明 ⑥ 清单是被**按内容选用的**，
  不是「每次固定堆同一个」。（证据：`target/probe/last_run.txt`、`art26.md`、`art26.html`。）
- **活体跑出来的三个新坑（同一类「静默失败」，已补判据）**：新补进白名单的这些容器并非写什么都安全：
  1. **`:::reading-path` 只要有一行不带 `-` 列表符号，整块产物就是 0 字符**（实测：`- 章节甲 | 说明`
     两行 → 2251 字符；把其中一行换成裸文字、或整块换成 `*` 列表 → **0 字符**，HTTP 200、`ok:true`、
     上游零 warnings）。行内的 `| 说明` 反而是可省的（`- 章节甲` 单独一行同样 2251 字符）。
  2. **`:::slider` 缺 `images` → 渲染器原样吐出一个灰底提示框「请提供图片URL列表」**（产物 165 字符
     就是那个框），这句话会直接留在成稿里。
  3. **`:::compare` 的行只接受 3–4 列**（2026-09-13 实测）：4 列 → 1742 字符；5 列与 2 列 → 1173 字符
     且上游只给一句 `meta.warnings`，其中 **2 列时末尾那一方的整列内容在产物里完全找不到**
     （对比方「乙」的字一个都没有）。run#71 就踩到过（运行说明里的「compare 有 5 行列数不是…」）。
  三条都加进了保存时自检（`readingPathWithoutBullets` / `sliderWithoutImages` /
  `compareRowsWithWrongColumnCount`），技能 ⑥ 的 `reading-path` 条目与 ④ 的 `compare` 条目也写明了行格式。
  （证据：`target/probe/probe_container_shape.py` → `container_shape_result.txt`、
  `probe_rp_shape.py` → `rp_shape_result.txt`、`probe_slider_empty.py` → `slider_empty_result.txt`、
  `probe_compare_cols.py` → `compare_cols_result.txt`。）
- **测试**：`ScheduledArticleToolsDraftTests` 19 例（+`containerCheckAcceptsComponentsTheGuideNeverMentions`
  ——11 个 guide 从未提及的容器逐个断言**不误报**，这是本轮的防回归核心；
  +`containerRowsThatRenderToNothingAreReported`（含 `:::reading-path` 三种写法）、
  +`sliderWithoutImagesIsReported`、+`compareRowsWithTheWrongColumnCountAreReported`、
  +`textRightAfterATableIsReported`、
  `captionSpacingIsNotReported` 取代原先那条基于错误结论的图注用例）。
- **仍不闭合的部分**：guide 是上游生成物，这 8 个容器仍然不会出现在实时语法指令里——**只能靠技能补齐**。
  已列为上游诉求（与 U1–U3 同类）：请把 Web 端组件注册表与接口 guide 对齐。
  另：`:::reading-path` 带属性的一次请求返回过 0 字符，复测 4 次均正常（2253 字符），
  判定为偶发、未编码进判据。

---

#### F11 运行摘要里的 `renderWarnings` 结构性恒为 0（F10 活体跑出来的第二处缺陷）（D25）✅ 已修复

> 来源：run#71 的**自带矛盾**——`stages_summary` 写着 `"renderWarnings":0`，同一行的运行说明却写着
> 「有 1 处 MarkFlow 渲染降级（compare 有 5 行列数不是「维度 | A方 | B方 | accent|default」，多余或缺少的列已被忽略）」。
> 两个字段描述的是同一次运行，不该打架。

- **根因（一行错序）**：`TaskExecutionService.execute` 先 `run.setStagesSummary(workspaceSummary(workspace))`，
  **之后**才 `workspace.draftState().renderBeforeDelivery(...)`。而 `DraftState.renderWarnings` 正是渲染的产出，
  PIPELINE / COORDINATOR 两条链路**从不提前渲染**（SINGLE 才会），于是这个字段对它们恒为 0 ——
  任何真实的版式降级在摘要里都看不见，只有去看运行说明那一长串才碰得到。
- **为什么值得修**：这个字段的用途就是「不用翻日志、看一眼摘要就知道这一篇版式有没有打折」。
  一个恒为 0 的字段比没有字段更糟——它会让排查先怀疑渲染服务。
- **修复**：把「交付前渲染 + 取摘要」收成一个顺序不可拆的方法
  `TaskExecutionService.renderAndSummarize(workspace, renderService, run)`，渲染在前、取摘要紧随其后；
  失败路径也补上摘要（原先只补日志与工具调用数，「失败 + 无摘要」比「失败 + 半份摘要」更难排查）。
- **测试**：新增 `schedule/TaskExecutionFinishTest`（2 例）——用 mock 渲染服务返回 1 条降级警告，
  断言 `stages_summary` 里是 `"renderWarnings":1`；另一例渲染干净时断言 `"renderWarnings":0`（判据可反证）。
  把两行调换回去，第一例立刻变红。
- **活体复核（run#72）**：`stages_summary` 与运行说明现在一致（该轮 `renderWarnings:0`，
  运行说明里也确实没有降级条款）。但这一轮**恰好没有产生渲染降级**，只证明了「两边不再打架」。
- **正面反例（2026-09-13 补，回应「单测不算真实渲染」的质疑）**：原先的正面证据只有上面那条 mock 单测。
  现补一个**脱离测试套件**的独立探针 `target/probe/probe-src/.../schedule/F11PositiveProbe.java`：
  它直接调真实的 `renderAndSummarize` + 真实 `MarkFlowRenderService`（真实令牌、真实上游渲染），
  用**故意写坏**的输入造出真实降级，产物落在 `target/probe/f11_positive_result.txt`：

  | 输入 | `DraftState.renderWarnings` | `stages_summary` | 运行状态 | 摘要⇔说明 |
  |---|---|---|---|---|
  | `:::compare` 每行 5 列（meta.warnings 型） | 1 条 | `"renderWarnings":1` | SUCCESS_WITH_WARNINGS | 一致 ✅ |
  | `:::compare` 未闭合（本地 detectLeakedSyntax 型） | 2 条 | `"renderWarnings":2` | SUCCESS_WITH_WARNINGS | 一致 ✅ |
  | 对照：三条写法全对 | 0 条 | `"renderWarnings":0` | SUCCESS | 一致 ✅ |

  即：修复前那种「摘要 0 / 说明 1」的分裂在真实渲染下已无法复现；把 `renderAndSummarize` 的两行调换回去，
  第一行场景立刻回到「摘要 0 / 说明 1」。两条降级路径（上游 `meta.warnings`、本地泄漏语法检测）都覆盖到了。
- **未做**：run#71 的既有记录不做回填（历史快照按当时代码写就），修复只对之后的运行生效。

### 3.6 2026-09-13 回归复查（并行会话提出的五问，逐条核销）

> 目的：F10/F11 落地后，由另一个会话复跑一遍并专挑「上一轮自己承认没证据」的地方。结论：**五问全部核销，
> 无需改代码**（只改了技能提示里一处措辞与本文档）。所有改动仍在工作树，未提交、未 push（遵并行会话的约束）。

1. **门禁复核**：`./.mvn/mvn-local.sh -o test` → **291 例 0 失败 / BUILD SUCCESS**（与 4.1 一致）；
   点名的两处：`schedule/TaskExecutionFinishTest` 2 例全绿、`ai/ScheduledArticleToolsDraftTests` 19 例全绿。
2. **F11 正面反例**（上一轮唯一的短板：只有 mock 单测、没有真实渲染降级）：已用独立探针补齐，
   真实令牌 + 真实上游渲染造出 1 条 / 2 条两种降级，摘要与运行说明均一致；对照样例为干净 `SUCCESS`。
   输入输出落在 `target/probe/f11_positive_result.txt`，探针源码 `target/probe/probe-src/.../schedule/F11PositiveProbe.java`
   （刻意放在 `src/test` 之外，不进测试套件）。详见 3.5 F11 的正面反例表。
3. **三条静默失败自检**经**真实保存路径**验证（同一份 `f11_positive_result.txt` 第二节）：
   `:::reading-path` 有一行不带 `-`、整块换成 `*`、`:::slider` 缺 `images`、`:::compare` 只给 2 列，
   四例都拿到可读 hint 并出现在模型可见的保存回执里；三条都写对的对照例 `warnings=[]`。
   注意：`saved` 仍为 `true`（自检是提示而非拦截，与 D19 的既有设计一致——先让模型有机会自己改，而不是硬拒导致整轮白干）。
4. **文档/代码一致性**：逐项核过，并**修掉一处上一轮写宽的结论**——曾写「guide 漏写了 9 个容器、一个都没写」，
   用实时 guide（7672 字符，`target/probe/guide_recheck.txt`）逐名复核后更正为：
   **8 个容器 guide 全文一次都没提**，`:::breaking` / `:::timeline` / `:::table` guide 有记载（`align` 只作为
   `<img>` 属性出现）——所以白名单新增 9 项 = 8 个未记载容器 + `:::steps` 的容器形式。
   同步更正了 `skills-agent-plan.md`，以及 `skill/SkillSeeder#MARKFLOW_CONTENT` ⑥ 的同类措辞。
   其余核对项均与代码一致：`SKILL.BUILTIN_KEY`（大写列名，已直查开发库确认）、容器行格式约束
   （`compare` 3–4 列、`timeline` 3 列、`case-flow` 需 `[标签]` 前缀、`reading-path` 需 `-`）、
   `renderAndSummarize` 的渲染先于取摘要。
5. **遗留（明确未闭合，别当成已验证）**：
   - 真实运行里**尚未**自然出现过「有降级 + 摘要也 >0」的一轮（F11 的正面证据目前来自探针的构造输入，
     不是真实任务的偶发降级）；run#72 是干净轮次，只能证明不打架。→ **已在 3.7 第 1 条闭合**。
   - I7（COORDINATOR 全绿实跑）仍被本机 TUN 代理阻塞；U1/U2/U3 与 F10 新增的上游诉求未提交给上游。
     → **已在 3.7 第 3 条闭合**（可复现性改写 + `docs/dev/upstream-issues.md`）。
   - 「成品不如官网示例好看」只在 run#71/#72 两轮上验到结构组件开始被使用，**视觉层面的逐项对齐**
     （间距/字号/装饰层级）没有做量化比对，仍属主观判断。→ **已在 3.7 第 2 条闭合**（量化表格 + 上游 R3）。

### 3.7 2026-09-13 第二轮复查（打 3.6 自己列出的三条「仍未验证」）

> 三靶：① F11 的真实轮次正面证据；② 视觉对齐量化；③ I7 与上游诉求收口。
> 结论：①③ 已闭合，② 拆出了一个本地可修项（已修）与一个上游项（本地修不动）。
> 本轮改动仍在工作树，未 commit、未 push。

#### 1）F11 真实轮次正面证据（run#73，文章 27）

- **怎么造的**：用产品自己的接口把 task#2 的 `AI_PROMPT` 换掉（原文备份在 `target/probe/task2_view.json`，
  跑完已原样还原），要求正文**原样照抄**一段 `<compare>…</compare>`。
  选这个输入而不是「5 列的 `:::compare`」是有原因的：保存侧自检**对 `<compare>` 标签没有判据**（见 D26），
  所以它一定能穿到交付渲染；而 5 列 `:::compare` 会当场被自检拦下并要求模型改，**不保证**留在成稿里。
- **两处取值**：

  | 字段 | 原文 |
  |---|---|
  | `TASK_RUN.STAGES_SUMMARY` | `{"revisionRound":2,"researchNotesRounds":1,"reviewRounds":3,"toolCalls":143,"toolFailures":5,"degradations":0,"renderWarnings":1,"saved":true}` |
  | 运行说明（`message`） | 「有 5 次工具调用失败，有 1 处 MarkFlow 渲染降级（组件标签 `<compare>` 未被识别，已作为正文文字输出（请核对语法指令里的写法））（详见执行日志），交付内容可能不完整。流水线执行完成：调研 → 写作 → 配图 → 审核」 |

- **一致性结论**：`renderWarnings:1` ⇔ 运行说明里的「1 处渲染降级」，**两处都是 1，不再打架**（D25 修复有效）。
- **可反证**：文章 27 的落库 HTML 里确实有一行 `<compare></compare>`（`target/probe/art27.html`），
  即「降级」不是告警文案，是产物里真发生的事；把 `renderAndSummarize` 的两行调换回去，这一轮就会回到「摘要 0 / 说明 1」。
- **与 run#72 的区别**：run#72 是干净轮次，只能证明「不打架」；run#73 是**真实链路自己产生的降级**，证明字段是活的。
- **本轮附带发现**：同一轮里 3 次 `submit_review` 因模型给出非法 JSON（字符串值里嵌了未转义的英文双引号）
  被 agent4j 直接判工具失败 —— 已记入 `docs/dev/upstream-issues.md` A4；它也正是逼出 D26 那条自检缺口的那次运行。
- 证据文件：`target/probe/run73_evidence.txt`（run id / 摘要 / message / 日志）、`art27.md`、`art27.html`、
  `task2_prompt_injected.txt`、`task2_put.json`、`task2_restore.json`。

#### 2）视觉对齐量化（把「不如官网示例好看」拆成数字）

- **方法**：官网示例（用户从 `https://www.bx9y.com.cn/markflow/` 导出的那份 HTML）与本项目文章 25/26
  都是**同一台渲染器**的产物，所以逐节点比对内联样式就能把「主观觉得素」变成可核的数字。
  脚本 `target/probe/visual_align.py` → `target/probe/visual_align_report.txt`（另见 `visual_diff_*.txt`）。

- **① 组件清单对照（同一渲染器，差别只能来自模型写了什么）**

  | 组件 | 官网示例 | 文章 25 | 文章 26 |
  |---|---|---|---|
  | reading-path 阅读路线 | 9 节点 | 5 | 0 |
  | steps 竖向步骤卡 | 4 | 0 | 0 |
  | p-title 段落标题 | 9 | 0 | 4 |
  | callout 提示卡 | 2 | 0 | 3 |
  | lead 引导块 | 1 | 10 | 1 |
  | timeline 时间线 | 3 | 0 | 0 |
  | statement 金句 | 1 | 0 | 1 |
  | title 标题卡 | 1 | 0 | 0 |
  | breaking 开篇大卡 | 0 | 0 | 1 |
  | table 表格 | 1 | 1 | 0 |
  | code 代码块 | 1 | 0 | 0 |

- **② `:::reading-path` 逐项数值**（同一组件、两边都渲染成功了）

  | 属性 | 官网示例 | 文章 25 | 差值 |
  |---|---|---|---|
  | 节点宽 | 126px | 126px | 0 |
  | 编号圆点尺寸 | 34×34px | 34×34px | 0 |
  | 圆点圆角 | 999px | 50% | 等效 |
  | 圆点字号 / 字距 | 11px / 1.2px | 12px / 无 | +1px / 缺 |
  | 圆点字重 / 颜色 | 900 / #ffffff | 900 / white | 0 |
  | 章节标题字号 / 行高 | 13px / 1.55 | 13px / 1.55 | 0 |
  | 章节标题字重 / 颜色 | 800 / #111827 | 700 / #1a1a1a | **−100** / 略 |
  | 连接线 | 32×1px，`linear-gradient(90deg,#94a3b859,#94a3b8d9)` | 同 | 0 |
  | 容器 padding / 圆角 / 背景 | 14px 12px 12px / 14px / `linear-gradient(#ffffff,#f8fafc)` | 同 | 0 |
  | 容器边框色 / 投影 | #e5e7eb / `rgba(15,23,42,0.08) 0 12px 30px` | #e2e8f0 / 无 | 近似 / **缺** |

  控制实验（`target/probe/reading_path_control.txt` + `rp_variants.txt`）：把同一段 `:::reading-path` 用
  `type="DA01|DA02"`、`style="card"`、`variant="2"`、`size="lg"`、带 `label/title`、换主题色、加 `dark`
  各渲染一遍，**产物字节数完全相同**——没有任何输入参数能把 API 侧切成官网那套值。
  ⇒ 这几项差异是**渲染器版本差**，本仓库修不动，已升级为上游诉求 **R3**。

- **③ 其余组件的逐项数值**（2026-09-13 补全；完整版同时写进了 `docs/dev/upstream-issues.md` R3-b）

  | 组件 | 官网示例 | 本项目 | 结论 |
  |---|---|---|---|
  | callout 容器 | `margin:16px 0` / `padding:16px 14px` / `border-left:4px solid` / `border-radius:0 10px 10px 0` | 同（仅颜色随主题：`#16a34a` vs `#2563eb`） | **逐项一致** |
  | callout 正文 | 16px / 700 / 主题色 | 同 | 一致 |
  | p-title 标题 | 30px / 900 / #111827 / -0.5px | 同 | 一致 |
  | p-title 副标题 | 11px / 700 / 大写 / 1.6px | 同 | 一致 |
  | p-title 编号 | 无 | 60px / opacity .25 + `CHAPTER 01` 小标 | 变体差异（官网用 `level="1"` 不带 `number`，我们用带 `number` 的） |
  | statement 金句 | `text-align:center` / 18px / 700 / `rgb(51,65,85)` / `line-height:1.6` | 同 | **逐项一致**（文章 26/28/29 各 1 处，三个样本都一样） |
  | timeline 时间线 | `margin-left:9px` / `padding-left:18px` / `border-left:2px solid`（透明度 .3）；日期 13px/700/`letter-spacing:0.5px`；标题 17px/800/`rgb(17,24,39)`/`line-height:1.4`；说明 14px/`rgb(100,116,139)`/`line-height:1.6` | 逐项相同 | 除主题色（官网 `#f39c12` / 我们 `#0984e3`，日期色与边线色同步）外**完全一致** |
  | table 表格 | 表头 `padding:13px 14px`/13px/600/`#fff`/首末格 `12px 0 0 0`·`0 12px 0 0`；单元格 `padding:11px 14px`/13px/`#475569`/`border-bottom:1px solid #f8fafc`/隔行 `#fafafe` | 逐项相同 | 除表头底色（主题色）外**完全一致** |
  | lead 引导块 | 16px / `rgb(85,85,85)` / `line-height:1.8` / `letter-spacing:0.5px` / `justify` / `padding:16px` / `border-left:3px solid` / `margin:14px 0` | 逐项相同（文章 26/29） | 除边线主题色外**一致**；另有一类 `padding:12px 16px`/`background:transparent`/`border-radius:0 8px 8px 0` 的变体出现在文章 25/28，是图注/注脚写法，两边没有同款样本可比 |

- **③-bis 靠「跨轮次自比」而不是官网对照的项（单独标出）**

  | 项 | 为什么只能自比 | 结论 |
  |---|---|---|
  | `breaking` 开篇大卡 | **官网示例一次都没用**（清单 ① 里计数为 0），没有官网值可比 | 文章 26（run#72）vs 文章 28（run#74）**逐条声明完全一致**，说明渲染器确定、差异只在「用没用」 |
  | `steps` 竖向步骤卡 | 官网有 4 张，**我们 4 个样本一张都没有** | 只能证明「没用到」，不能证明「渲染差异」；属选型问题（D24/F10） |
  | `code` 代码块 / `title` 标题卡 | 官网各 1，我方 4 样本均为 0 | 同上 |
  | `reading-path` 之外的一切 | — | 见下：`reading-path` 是**唯一**确认存在版本差的组件 |

  ⇒ 这一节的实用结论：**「同一组件两套值」只在 `reading-path` 上成立**，其余可比组件要么一致、
  要么差异只来自主题色。R3 的影响面因此比原先写的小得多。

- **③-bis `breaking` 开篇大卡：这一类做不了「官网示例值 vs 本系统产出值」**

  **官网那份示例里 `:::breaking` 用了 0 次**（清单见 ①，实测计数为 0），所以不存在可比对的官网值。
  留一条跨轮次的**自比**（同一渲染器、同一任务链，不同轮），用来证明「同组件在不同轮次里几何是否稳定」：

  | 属性 | 文章 26（run#72） | 文章 28（run#74） |
  |---|---|---|
  | 容器 `margin` / `padding` / `border-radius` | 24px 0 / 30px 20px / 16px | 同 |
  | 容器背景 | `radial-gradient(circle 60px at 92% 30px,#0984e326 96%,transparent 100%)` + `linear-gradient(135deg,…)` | 同 |
  | 容器边框 | `1px solid #0984e333` | 同 |
  | 角标 | `padding:4px 12px` / `11px` / `700` / `letter-spacing:1px` | 同 |
  | 主标题 | `22px` / `800` / `#1a1a1a` / `line-height:1.4` | 同 |
  | 副标题 | `14px` / `#64748b` | 同 |
  | 话题标签 | `flex` + `gap:8px` / 每枚 `padding:4px 12px` / `border-radius:12px` / `11px` | 同 |
  | 正文 | `14px` / `#475569` / `line-height:1.8` | 同 |

  ⇒ 两条产物**逐条声明完全一致**，说明渲染器对同一组件是确定的，差别只可能是「用没用、用了几次」。
  **未验证**：官网自己那一版 `breaking`（Web 端注册表里叫 `:::breaking`，guide 第 8 条有记载）长什么样，
  本仓库没有样本可比——若后续拿到，R3 那张差异表可能要再加一行。

- **④ 装饰密度（「素」的可量化根因）**

  | 来源 | 带样式元素 | 正文字数 | 每千字样式节点 | 组件种类 |
  |---|---|---|---|---|
  | 官网示例 | 388 | 2253 | **172.2** | 11 |
  | 文章 25（run#71） | 279 | 4444 | 62.8 | 4 |
  | 文章 26（run#72） | 169 | 2829 | 59.7 | 5 |

  ⇒ 我们的成稿装饰密度是官网示例的 **1/2.7 ~ 1/2.9**；差的是「用了几种、用在哪」，不是渲染器少给了样式。

- **已修（本地可修的那一半）**：`SkillSeeder#MARKFLOW_CONTENT` ⑥ 的「用法判断」原写「按内容选 3-5 种用足即可」，
  现改为：**每个二级章节（`##`）的标题都用 `<p-title>` 承接**（官网示例 2253 字用了 9 个 p-title，
  我们此前的成稿 0–4 个）+ 逐类内容的选型清单（步骤用 `:::steps-*`、时间线用 `:::timeline`、案例用
  `:::case-flow`、结论用 `<statement>`、对照用 `:::table`/`:::compare`、注意事项用 `:::callout`、结尾用 `<engage-card>`）
  + 密度参考（每千字 172 个样式节点是官网水平，我们此前约 60；2500–3000 字用满 6–8 种合理）。
- **修不动的部分**：官网示例本身是「组件能力展示」型文章，密度天然偏高，2.7 倍这个数不能直接当成
  新闻稿的目标值；`reading-path` 的微观排版差异（R3）只能提上游。
- **密度改动已用真实轮次验证（run#75，2026-09-13 04:05→04:26，task#2 PIPELINE）**：
  改完提示词后**先重启应用**（`SkillSeeder` 只在启动时按 `BUILTIN_KEY` 覆盖 `content`，不重启等于没改——
  这条坑本身也记在 4.2），再触发 run#75：142 次工具调用、`saved:true`、产出**文章 29**。

  | 来源 | 组件种类 | 带样式元素 | 正文字数 | 每千字样式节点 |
  |---|---|---|---|---|
  | 官网示例 | 11 | 388 | 2253 | 172.2 |
  | 文章 25（run#71，改前） | 4 | 279 | 4444 | 62.8 |
  | 文章 26（run#72，改前） | 5 | 169 | 2829 | 59.7 |
  | **文章 29（run#75，改后）** | **6** | **188** | **2157** | **87.2** |

  ⇒ 相对同任务的改前两轮（62.8 / 59.7）**提高到 1.39× / 1.46×**，种类从 4–5 种到 6 种；
  但**离官网的 172.2 仍差 2.0 倍，没有达标**。定性看两件事确实被遵守了：
  正文里的 `##` 二级标题**归零**（5 个章节全部由 `<p-title>` 承接，改前是 0–4 个），
  并新生成了 `:::timeline`（4 项）这类改前没出现过的结构组件。
  没上去的原因是本轮**稿子只有 2157 字**（提示词的密度参考说的是 2500–3000 字），组件总数被篇幅封顶，
  且「官网示例」本身是组件能力展示型文章、密度天然偏高——**87 不是「已对齐」，只是「方向正确、幅度不够」**。
  下一位接手人可以继续压这一项，但**不要再拿「官网 172」当新闻稿的目标值**。
  ⚠️ **本节这一段的「差 2.0 倍」结论已在 3.8 ② 被替换**：补到 n=3 并做了受控实验后，
  公平目标是「同篇幅参考值的 85–100%」（1300–4400 字即 72–99 每千字），不是 172。以 3.8 ② 为准。
- **顺带抓到一条真缺陷（D27）**：run#75 的 `renderWarnings:1` 是**误报**——告警说
  「组件属性里的文字「pacethefrontier&quot」在渲染产物里找不到」，而文章 29 的 HTML 里
  `"pace the frontier"` 渲染得好好的。根因是源码侧的 HTML 实体没解码就去比对已解码的产物。
  已修（`MarkFlowRenderService.decodeEntities`）+ 加了单测，并用文章 29 的真实源文/产物复核到 0 条。
  **这条也说明 run#75 实际是「零真实降级」的一轮**。
- 证据文件：`visual_align_report.txt`、`visual_diff_reading_path.txt`、`visual_diff_ptitle.txt`、
  `reading_path_control.txt`、`rp_variants.txt`、`ptitle_variants.txt`、`tag_vs_container_result.txt`、
  `density_check.py` + `density_check_report.txt`（改后新加的量化脚本，可对任意文章重跑）、
  `art29_db.html` / `art29.md`（文章 29 的渲染产物与源文）。

### 3.8 2026-09-13 第三轮复查（打 3.7 自己列出的四条「仍未验证」）

前置：改完 D27 后**重启应用**（04:53:43 起，日志 `target/probe/app-run-round3.log`），确认新技能文本已 seed
（`GET /api/skills` 里 `markflow_default` 的 content 含新句子）。随后用 `target/probe/round3_driver.py`
**顺序**驱动 5 次真实运行（刻意不并发——并发自己会造 429，把稳定性数据的因果搅浑），
逐轮完整记录落在 `target/probe/round3_runs.jsonl`。全部 5 轮结束时间 06:59。

#### ① COORDINATOR 稳定性：连跑 3 轮（含上一轮 run#74 共 n=4）

| 轮次 | run | 起止 | 耗时 | 工具调用 | 预算触顶 | 委托次数 | degradations | renderWarnings | 文章 | 终态 |
|---|---|---|---|---|---|---|---|---|---|---|
| — | #74 | 03:37:38→03:58:10 | 20m32s | 78 | 否 | 6 | 0 | 0 | 28 | SUCCESS_WITH_WARNINGS |
| 1 | #76 | 04:55:38→05:22:48 | 27m09s | 143 | 否 | 8 | 0 | 1 | 30 | SUCCESS_WITH_WARNINGS |
| 2 | #77 | 05:23:10→05:49:48 | 26m38s | 134 | 否 | 8 | 0 | 1 | 31 | SUCCESS_WITH_WARNINGS |
| 3 | #78 | 05:50:11→06:06:24 | 16m13s | 78 | 否 | 6 | 0 | 0 | 32 | SUCCESS_WITH_WARNINGS |

（委托次数取自执行日志里的 `【协调】委托 #N：<角色>` 行；run#76 的运行说明自称「共涉及 5 次委托」，
与日志的 8 次对不上——**以日志为准**，模型自述的统计不可信。）

**逐次工具失败明细（这才是「概率性风险」的所在）**

| run | 失败合计 | 类型明细 |
|---|---|---|
| #76 | 4 | `browse_webpage` HTTP 103 ×1；`browse_webpage` HTTP 429 ×1；`submit_review` JSON 解析失败 ×2 |
| #77 | 7 | `browse_webpage` HTTP 429 / connect timed out / HTTP 103 / EOF reached while reading 各 ×1；`save_article_draft` 摘要超 120 字 ×2；`submit_review` JSON 解析失败 ×1 |
| #78 | 4 | `search_web` connect timed out ×1；`search_web` EOF reached while reading ×1；`browse_webpage` HTTP 401 ×1；`submit_review` JSON 解析失败 ×1 |
| 合计 | 15 | `browse_webpage` 7、`search_web` 2、`submit_review` 4、`save_article_draft` 2 |

- **确定性通过的**：3/3 轮**都没有**预算触顶、都没有停滞、都 `saved:true` 并产出文章；
  `degradations` 恒为 0。整轮 200 次工具的预算在最坏的一轮（143）也只用掉 72%。
  「子智能体工具调用超过上限」这条原始故障在本轮 **0 次复现**（n=3，且含两轮把 8 次委托额度用满的）。
- **概率性风险的**（两轮里都出现，与网络质量相关，非本系统缺陷）：
  ① **联网工具失败**——3 轮共 **9** 次（`browse_webpage` 7 + `search_web` 2），形态是 429 / HTTP 103 /
  connect timed out / EOF / 401，全部来自 `search_web` / `browse_webpage` 的外部站点，
  属上游/网络，已并入 `upstream-issues.md` G2 一类；
  ② **`submit_review` 参数 JSON 解析失败**——3 轮共 **4** 次（2+1+1），是 A4（模型在 JSON 字符串值里写未转义引号），
  已单列 `upstream-issues.md` A4；
  ③ **`save_article_draft` 摘要超 120 字**——3 轮共 **2** 次（都在 run#77），模型自查自纠，
  下一次调用即成功，不算风险。
  （三类相加 9+4+2 = 15 = `toolFailures` 合计。）
- **一个值得注意的边界**：`delegate` 的**每轮上限是 8**，#76/#77 两轮都正好用满。
  本次没有因触顶中止，但两轮都是「返工把整条链重跑一遍」才逼近这个数——
  如果后续把返工轮次调高，**8 这个额度会先于 200 的工具预算成为瓶颈**（这一点 run#74 时还看不出来）。
  **已查清：是硬编码，不是配置**——`ai/DelegateTools.java:29` 的
  `public static final int MAX_DELEGATIONS = 8`，`application.yaml` 里**没有**对应开关，
  全仓库只有 `DelegateTools` 自己与 `DelegateToolsTest` 引用它。
  **本轮未改**（peer 明确要求只记录、不动常量；改它属于产品取舍，需要用户决定：
  返工时是「只重跑写作+审核」还是「整条链重跑」，这条决策会直接改变 8 够不够）。
- **结论**：COORDINATOR 的**功能性**已经确定性通过（连续 3 轮全交付），残留的是**外部依赖的概率性失败**，
  且这两种失败都不会让任务中止，只体现在 `toolFailures` 与 `SUCCESS_WITH_WARNINGS` 上。
  **未验证**：连续更多轮（n≥10）下的失败率、以及网关 429 与并发度的量化关系。

#### ② 装饰密度：样本扩到 n=3，并给出公平目标值（替换原来那句「差 2.0 倍」）

> **⚠️ 2026-09-13 第四轮复查：本节的字数与密度已按统一口径重算，旧数不可复现。**
> 旧表写的是文章 29/33/34 = **87.2 / 72.1 / 59.2**（正文字数 2157 / 3216 / 4428），
> 但用 `density_check.py`（字数为 `soup.get_text('', strip=True)`）对**同一份落库 HTML** 重跑，
> 得到的是 **103.2 / 76.5 / 69.9**（正文字数 1821 / 3034 / 3750）——**带样式节点数完全一致**
> （188 / 232 / 262），差的只是分母。三篇文章的 `UPDATED_AT` 都停在创建时刻，内容没变过，
> 所以是**旧表的字数用了另一套口径**（具体是哪种已查不出，旧的中转文件被覆盖了）。
> 后果是实质性的：**目标区间那一列是用 `density_check.py` 的口径算出来的**
> （重跑 `density_target_ref*.html` 得到 99.1 / 91.5 / 87.1 / 85.2，与表一致），
> 拿另一套分母的密度去和它比，得到的「差 18%」是**无效比较**。下面是同一口径下的复测。

**统一口径：`density_check.py`，输入为**重新从库里 `SELECT CONTENT_HTML` 导出的**同一批样本**
（`target/probe/art*_db.html`，2026-09-13 08:00 一次性重导）。凡本节数字，一律以此为准。

**改技能文本之前（改前样本）**

> 旧文里「改前三个样本是 62.8 / 59.7 / 55.1（文章 25/26/27）」同样是旧口径，重算后是 64.3 / 63.8 / 57.2；
> 下表补上了同一批里的文章 28。

| 文章 | 来源 | 正文字数 | 带样式节点 | 每千字节点 | 同篇幅目标 | 达标 |
|---|---|---|---|---|---|---|
| 25 | run#71 | 4342 | 279 | 64.3 | ~4300 → 72–85 | 差 11% |
| 26 | run#72 | 2647 | 169 | 63.8 | ~2600 → 76–90 | 差 16% |
| 27 | run#73 | 2712 | 155 | 57.2 | ~2700 → 76–90 | 差 25% |
| 28 | run#74 | 4316 | 248 | 57.5 | ~4300 → 72–85 | 差 20% |

**改技能文本之前的三篇（本节原表，重算后）**

| 文章 | 来源 | 正文字数 | 带样式节点 | 每千字节点 | 同篇幅目标 | 达标 |
|---|---|---|---|---|---|---|
| 29 | run#75 PIPELINE | 1821 | 188 | **103.2** | ~1800 → 80–94 | 达标（超上限） |
| 33 | run#79 PIPELINE | 3034 | 232 | **76.5** | ~3000 → 74.7–88 | 达标（贴下限） |
| 34 | run#80 PIPELINE | 3750 | 262 | **69.9** | ~3750 → 73–86 | 差 4% |

⇒ **重算后「越长越稀疏」这条结论弱得多**：34 只差 4%（不是 18%），33 达标。
原来的说法是被口径混用放大的。

**改技能文本之后的三篇（本轮新采样，n=3，同一份改版技能文本、同一台渲染器）**

| 文章 | 来源 | 正文字数 | 带样式节点 | 每千字节点 | 同篇幅目标 | 达标 |
|---|---|---|---|---|---|---|
| 35 | run#81 PIPELINE | 4108 | 208 | **50.6** | ~4100 → 71–83 | **差 29%** |
| 36 | run#82 PIPELINE | 3441 | 217 | **63.1** | ~3400 → 73–86 | **差 14%** |
| 37 | run#83 PIPELINE | 5509 | 285 | **51.7** | ≥4400 → 72–85 | **差 28%** |
| 39 | run#84 PIPELINE（D29 验证轮） | 2349 | 236 | **100.5** | ~2350 → 77–91 | 达标（超上限） |

三篇的组件种类分别是 5 / 6 / 6 种（够 ≥6 的线），**掉的是「每千字节点」这一列**——
即组件种类没少，但相对篇幅铺得不够密。

**明确回答「新规则有没有改善长文变稀疏」：没有改善，而且问题就是「长」。**
四篇里**唯一的短稿（39，2349 字）密度 100.5、直接超过目标上限**；三篇长稿（3441 / 4108 / 5509 字）
全部不达标，且**篇幅越长越低**。对照改前同长度档的文章 33（3034 字 → 76.5）、34（3750 字 → 69.9），
改后并没有变好。失败点已定位（不是「种子没生效」、也不是「目标区间不合理」）：

- **种子确实生效**：应用 2026-09-13 07:14:23 重启，`SKILL` 表 `markflow_default` 的 `UPDATED_AT=07:14:27`、
  正文 5394 字符、含新句子；run#81 在 07:24:43 起跑，晚于 seed。
- **是模型没照提示词做**：文章 35 的落库源文有 6 个 `##` 章节，**每节 777 / 750 / 1057 / 314 / 448 / 207 字**
  ——提示词要求 3000 字以上「每节控制在 400 字以内」，前 3 节全部超出一倍以上；
  「每节至少配一个结构组件」也没做（6 节里只有第 3、5 节各有一个结构组件，其余只有 `<img>` + `<p-title>`）。
  即**分节粒度没压下来，组件密度自然上不去**——这与受控实验（每节 106 字 → 260.9、每节 188 字 → 147.3）方向一致。
  短稿（39）之所以达标，正是因为它本来就只有 2349 字。
- 35/36/37 三轮另外各被 compare 判坏行拖了后腿（见 D29，三篇的第 4 格写的都是中文「强调」），
  但那只是 1 行，不足以解释 14–29% 的缺口。

⇒ **下一轮该改的是「篇幅」与「分节」这两条指令，而不是继续往清单里加组件**：
要么把成稿字数压到 2500 字以内（短稿实测达标），要么把「3000 字以上每节 ≤400 字」写成
能被机械执行的结构要求（例如「N 个二级章节，每章再分 3 个 `###` 小块」）。**加组件清单已经证明无效。**

**方法论：官网 172.2 不是「同体量同题材」的可比目标**，三条理由——

1. **文体不同**：官网那份是**组件能力展示体**，2253 字里用满 11 种组件、9 个 `<p-title>`（每 250 字一个标题）。
2. **结构粒度不同**：我们的成稿每 431–886 字才一个章节标题，官网是 250 字。
3. **受控实验证明粒度就是主因**：用**同一台渲染器**、按技能推荐清单构造参考稿，只改篇幅（`probe_density_target.py`
   →`density_target_ref*.html`，组件种类稳定在 7–9）：

   | 参考稿字数 | 带样式节点 | 每千字节点 |
   |---|---|---|
   | 1322 | 131 | 99.1 |
   | 2076 | 190 | 91.5 |
   | 3228 | 281 | 87.1 |
   | 4380 | 373 | 85.2 |

   ⇒ **1300–4400 字的新闻稿，把推荐组件用满也只到 85–99**，随篇幅缓慢下降。
   另有两个刻意做密的对照（`probe_density_dense.py`）：每节 106 字 → **260.9**、每节 188 字 → **147.3**。
   也就是说，**172 追得上，但要求每节 150–200 字**（≈2200 字写 12–14 节），那是清单体不是新闻稿。

**给出的目标区间（按篇幅分档，推导依据＝上表同篇幅参考值的 85–100%）**

| 稿件篇幅 | 同篇幅参考值 | 建议目标（每千字带样式节点） | 对应组件种类 |
|---|---|---|---|
| ~1300 字 | 99.1 | **85–99** | ≥6 |
| ~2100 字 | 91.5 | **78–92** | ≥6 |
| ~3200 字 | 87.1 | **74–87** | ≥6 |
| ~4400 字 | 85.2 | **72–85** | ≥6 |

中间篇幅按相邻两档线性内插（本节各表里的「同篇幅目标」就是这么来的）。

- **已据此改掉技能里的目标**：`SkillSeeder#MARKFLOW_CONTENT` 的密度段不再写「官网 172 / 差 2.7–2.9 倍」，
  改为「不要拿 172 当目标 + 每节 `<p-title>` + 每节至少一个结构组件 + 每千字 ≥80 节点 + 种类 ≥6 +
  3000 字以上靠多分节（每节 ≤400 字）补」。
  （改完已在 2026-09-13 07:14 重启应用并核过 seed：`SKILL` 表 `markflow_default` 的 `UPDATED_AT=07:14:27`、
  content 5394 字符、含新句子、旧句「差 2.7–2.9 倍」已不在，见 `target/probe/skill_seed_check.txt`。）
  **改后已经跑过真实轮次了**（run#81/#82/#83，文章 35/36/37）——结果见上面那张「改技能文本之后」的表：
  **n=2 时没有改善**，且失败点已定位到「分节粒度没压下来」（模型没执行「每节 ≤400 字」）。
- **仍未验证（这一段是本节最该被认真读的部分）**：
  - 密度与「读者观感」的关系**没有任何证据**。本文从头到尾只用它当**结构完整度的代理指标**，
    它是「带内联样式的元素数 ÷ 正文字数」这么一个机械比值，**与「读者觉得好看」「打开率高」之间的关联
    本项目一次都没测过**（要做观感验证得上人工评分或眼动/点击实验，没有做）。所以它只能当
    **回归护栏**（防止越写越素），**不能当质量结论**，也不能拿去和别家文章横向比（文体不同）。
  - **样本小且题材单一**：目前全部样本（25–37）**清一色是科技新闻**；
    **财经 / 健康 / 教育 / 情感题材一篇都没测**。不同题材适配的组件清单很可能不同
    （`:::timeline` 在财经稿里未必像在科技稿里那么自然），**不能假定这套清单跨题材通用**。
    每组样本量也只有 1–4 篇，"达标/不达标"这种结论本身就不稳。
  - **指标本身还有假阳性，别把它当精确尺**：`density_check.py` 的「组件种类 / 组件块数」两列已被证实
    有重复计数与误判——名为「reading-path 阅读路线」的指纹（`width == '126px'`）实测命中的是
    **`<p-title>` 的章节号方块**，而 `p-title` 同时又被 `[data-block=ptitle]` 计了一次，
    同一个组件被算两遍（复核脚本 `density_check_v2.py`，去掉这条后文章 29 是 5 种 / 12 块、35 是 5 种 / 13 块）。
    「steps 竖向步骤卡」指纹也在文章 35 上命中了一个空 `<span>`，属误报。
    **「每千字节点」这一列不受影响**（它数的是所有带 `style` 属性的元素，不依赖这组指纹），
    本文的比较结论都建立在这一列上。

#### ③ 官网 `:::breaking` 样本缺失 + 其余组件差异表补全 → 见 `docs/dev/upstream-issues.md` R3-b

已在 R3 里补齐 **statement / timeline / table / lead** 四类（超过要求的 2 类），并新增 R3-b 汇总表。
要点：其中 **statement / table / timeline / lead 除主题色外逐项一致**，只有 `reading-path` 是真的「同一组件两套值」
（R3-a 的 6 项差异）。**官网版 `:::breaking` 无可比样本**这一点已单列（R3-b 表内 + 3.7 ③-bis），
并把它归入「靠跨轮次自比」的一组，同组还有 `steps` / `code` / `title`（我方样本里根本没有）。

#### ④ R2 结论的边界 → 见 `docs/dev/upstream-issues.md` R2-附

已把「除 compare 列数外一律不报 warnings」改成带边界的表述，并新增三个小节：
**① 实际打过的输入清单**（R2-附表共 7 行探针 / 52 个用例——其中 6 个探针 42 例问「报不报 warnings」，
第 7 行 `attr_probe3/4` 10 例问「属性进不进产物」；逐条列出写法，命中 warnings 的只有 2 例）、
**② 一个容易读错的细节**（`:::compare` 3 列**也**静默降级，只有 5 列与 2 列报 warnings——连唯一命中的判据都比看起来窄）、
**③ 没覆盖到的组合**（嵌套只试过 1 种、属性写法变体、定界符变体、规模类）。
并给出 **3 条能证伪它的最小实验**，其中**第 3 条（「是否存在 `meta` 之外的报错通道」——
它的结果决定整节是否作废）已于第四轮实打，结论是 R2 保留**：详见 `upstream-issues.md` R2-附 ④。
（打之前先怀疑的两处都排除了：顶层没有 `warnings`/`errors` 兄弟字段；此前从未检查过的顶层
`preview` 字段里也没有错误块——用它扫出来的「失败」是静态 UI 文案，干净输入里同样存在。
附带确认一个正向通道：整篇降级时 `html` 返回**空串**。）

#### ⑤ 本轮新发现的缺陷：D28（技能提示词教模型写一个不渲染的属性）

run#76 的 `renderWarnings:1` 顺藤摸瓜查出来的：告警说「组件属性里的文字「数学能力基准测试演进」
在渲染产物里找不到」，实测确认 **`:::table` 的 `title=` 本来就不渲染**（`attr_probe3.txt` / `attr_probe4.txt`），
而**我们自己的技能提示**恰好写着 `:::table style="card" title="四种输出模式对比"`。
已修三处：技能示例去掉 `title=` 并写明原因、保存侧新增自检、降级告警文案补上「也可能是渲染器本来就不输出这个属性」。
详见缺陷索引 D28。

- 证据文件：`round3_driver.py` / `round3_runs.jsonl` / `round3_summary.txt` / `round3_deleg.txt`、
  `run76_raw.json` / `run78_raw.json` / `run79_raw.json`、`art30.md`+`art30.html`+`art30_warncheck.txt`、
  `art33_db.html` / `art34_db.html`、`density_check_report.txt`、`density_target_*.html`、
  `attr_probe3.txt` / `attr_probe4.txt` / `rp_title_control.txt`。

#### ⑥ 第四轮：同类缺陷再现（D29）+ 改后技能文本的密度对照 + 上游 R2 的证伪复查

**（a）D29——又是「我们自己的提示词教出一个会被判坏的写法」**

改版技能文本上线后连跑三轮（run#81/#82/#83），**三轮的 `renderWarnings` 全是同一条**：
`compare 有 1 行列数不是「维度 | A方 | B方 | accent|default」`。查落库源文，三篇（文章 35/36/37）
的 compare **表头第 4 格写的都是中文「强调」**——技能提示里那句「每行写成『维度 | A方 | B方 | 强调标记』」
被模型**照字面抄进了正文**。

最小复现 `target/probe/probe_compare_marker.py`（`compare_marker_probe.txt`）：第 4 格
写 `accent` / `default` / `ACCENT` / 留空 → 不报；写 `强调` / `高亮` / `highlight` / `strong` → 报。
**上游告警说「列数不是」，实际触发条件是取值不在白名单里**，照着告警去数列数会查不出问题。
旧判据只查列数（3–4），这一格因为「4 列」而通过，所以三轮都没拦住。已修三处（见 D29）。

**验证（可证伪）**：改完重启（08:38:21 seed 生效）跑 run#84 → 文章 39，
`stages_summary = {"degradations":0,"renderWarnings":0,...}`，**源文里「强调」两个字已经不出现**，
第 4 格写的是 `accent` / `default`。改前 3/3 轮命中、改后 1/1 轮干净。

**（b）改后技能文本的密度对照** → 见 §3.8 ② 的「改技能文本之后」那张表，
结论是**没有改善**，且失败点已定位为「篇幅/分节」而不是「组件清单」。

**（c）上游 R2 的证伪复查** → 见 `docs/dev/upstream-issues.md` §R2-附 ④：
「不存在第二报错通道」这部分**保留**（顶层键、`ok`、`meta`、`preview`、响应头全部 dump 过），
但「容器错误几乎都不报 warnings」这个说法**收窄**——实测确认 `compare` 行形状与 `timeline` 行不足 3 列
这两条**是会报的**，而缩进/引用/冒号个数/嵌套/属性写法仍然全静默。

**（d）第四轮新增/更新的证据文件**：`round4_driver.py` / `round4_driver.log` / `round4_runs.jsonl`、
`round5_driver.py`（D29 验证轮）/ `round5_runs.jsonl`、`probe_compare_cols_mixed.py`（`compare_cols_mixed.txt`）、
`probe_timeline_cols.py`（`timeline_cols.txt`）、`probe_compare_marker.py`（`compare_marker_probe.txt`）、
`density_check_v2.py`（`density_v2_result.txt`）、`fp_audit.txt`、`compare_blocks_art35_36_37.txt`、
`art29/33/34/35/36/37/39_db.html`（2026-09-13 08:00 起一次性从库重导）。
**注意**：`art29_db.html` / `art33_db.html` / `art34_db.html` 是本轮**重新导出覆盖**过的，
此前基于旧文件的中间结果若需复现，重新 `SELECT CONTENT_HTML` 即可（文章内容自创建后未再变更）。

---

### 3.9 2026-09-13 第五轮：组件渲染能力全量核查（后端 API / 编辑器前端两条路径）

**这一轮的问题不是"哪个组件坏了"，而是"你到底知道哪些组件、每个组件在两条路径上分别是什么样"**。
用户原话：编辑器的渲染能力不如 MarkFlow 渲染服务（公式、轮播图等"在编辑器里渲染不出来"），
要求「审阅所有组件的渲染能力，一个一个测试，发现一个问题，修复一个问题，直至所有组件都能正确渲染」，
并且「不止后端 api 能正确渲染，编辑器的前端也要能正确渲染出来」。

本轮**只做渲染能力**，密度那条线路的收尾（3.8 结尾留下的未验证项）原样顺延，未动。

#### ① 组件清单怎么来的（不凭印象）

三条来源合并，去重后得 **79 个最小样例**，每个样例一个独立文件、不混在长文里：

| 来源 | 是什么 | 证据文件 |
|---|---|---|
| MarkFlow Web 端**组件注册表** | 从官网前端 bundle 里抽出的组件 ID → 分类映射，共 **63 个 ID** | `target/probe/component_registry.json`（`mf_app.js` 里的 `{Title_DA01:"title",…,"layout-changelog":"other"}`） |
| 引擎的**语法匹配器** | 渲染器识别语法的 29 条正则（`:::` 容器 / `<tag>` 标签 / Markdown 行内标记） | `target/probe/component_matchers.json` |
| 官方 `guide` 与 bundle 的**组件 `spec`** | `GET /__markflow_render` 返回的语法指令原文；以及 bundle 里每个组件的 `spec.example`（官方示例原文） | `target/probe/guide.md`、`guide_now.md`、`mf_guide_full.txt`、`mf_app.js` |

63 个 ID 去重后是约 30 种真实语法形态；再按「同一组件的容器式 / 标签式 / 属性变体各建一个样例」展开成 79 个。
最小可用写法就是 `target/probe/components/<id>.md` 的第一行（下表逐条列出）。

**注意这 79 个是"最小样例"而不是"官方全集"**：注册表里还有一批只在 Web 侧存在、
渲染 API 根本不认的 ID（`layout-toc` / `layout-metrics` / `hint` / `layout-hero` 的标签形式等），
它们也按相同方式打了样例——正是这一批先暴露了"我们造了一个官方并不存在的写法"这个坑（见 ⑤ 与 `upstream-issues.md` R4-附）。

**样例在定稿前做过一次回查**：写 `upstream-issues.md` 的 R4 时回查 bundle `spec`，
发现 4 个样例的"最小写法"是我们自己假设的、官方来源里查无实据，已按 `spec.example` 原文改正，
并新增 2 个反例样例（`blk-slider-selfclose`、`blk-case-flow-badline`）保留证据。
**回查范围只有出问题的那 4 条**，其余 75 个样例的写法**没有逐条回查官方定义**。

#### ② 两条路径分别怎么建基线

| | 路径 A：后端 API | 路径 B：编辑器前端 |
|---|---|---|
| 入口 | `MarkFlowRenderService` → `POST https://www.bx9y.com.cn/__markflow_render`（真实令牌） | `webui/src/views/ArticleEditorView.vue` 的 `extensions:[…]` |
| 产物 | 渲染服务返回的 `html` 字段（就是落进 `CONTENT_HTML` 的那份） | TipTap `setContent(html,false)` → `getHTML()`，与 `ArticleEditorView.vue:96` 保存、`:369` 预览同一个出口 |
| 探针 | `target/probe/component_matrix.py` → `component_matrix.json` / `.txt`（后端每次返回的 `ok` / 字符数 / `missingMarkers` / `warnings` / `svg` / `katex`） | `target/probe/fe_harness.mjs` → `fe_matrix.json`，另存每个样例的 `<id>.editor.html` |
| 真实数据 | 6 篇**库里真稿件**（`SELECT CONTENT_HTML`，文章 5/24/30/35/38/40） | `target/probe/fe_real.mjs`，同一份 HTML 分别灌进「改动前扩展集」(`target/probe/legacy/editorExtensions.js` = `git show HEAD:webui/src/editorExtensions.js`) 与当前扩展集 |

编辑器路径的探针是**真编辑器**：`fe_harness.mjs` 直接 `import` 真实的 `webui/src/editorExtensions.js`，
扩展数组与 `ArticleEditorView.vue:47-72` 逐项对齐（Vue 那层只是 `useEditor` 包壳，schema 由这批扩展决定），
在 jsdom 里建 headless `Editor`，不靠读代码猜。jsdom 装在 `target/probe/feharness/node_modules`
（该目录在 `.gitignore` 里，**故意不装进 `webui/node_modules`**，免得动到 `package.json`/`package-lock.json`）。

**怎么跑**（可复现）：

```bash
python target/probe/component_matrix.py            # 全部 79 个样例打一次真实渲染 API；--only a,b 只跑指定的
node target/probe/fe_harness.mjs                   # 全部 79 个样例；可跟 id 逗号列表只跑指定的
node target/probe/fe_harness.mjs --legacy          # 同一批样例灌进改动前的扩展集，做红→绿对照
node target/probe/round5_impact.mjs                # D33 / D34 影响面复算（口径 + 前后对照）
node target/probe/fe_real.mjs --legacy             # 真实稿件，改动前扩展集
node target/probe/fe_real.mjs                      # 真实稿件，当前扩展集
node target/probe/fe_idempotence.mjs               # 往返幂等性（回吐的 HTML 再喂一遍，看会不会持续劣化）
node target/probe/round5_table.mjs                 # 用上面两个 JSON 生成下表
python target/probe/r4_recheck.py                  # 回查：5 条被判"上游"的样例，官方写法 vs 我们的写法
python target/probe/r4_slider_discriminator.py     # 判别实验：<slider> 到底吃什么写法（10 组）
```

原始产物全部留在 `target/probe/` 下：`components/<id>.html`（后端产物）、
`components/<id>.editor.html`（编辑器回吐）、`components/<id>.md`（最小源文）、
`real/<id>.html` / `<id>.editor.html` / `<id>.legacy.editor.html`（真实稿件两份对照）。

#### ③ 逐项对照表（79 个样例）

判定口径：**✅ 一致** = 文字不丢、无标签被吞、内联样式不缩水、后端不缺标记；
**❌ 差异** = 上述任一条不满足；**➖** = 该行不适用（写法本身在官方来源里不存在，或是探针标记词的问题）。

"后端 API 结果"里的 **缺标记** 指该样例的探针标记词没在产物里出现——即**组件整个没渲染出来**；
"编辑器前端结果"里的样式是**内联样式属性值总长度**，比"style 出现几次"更能反映丢没丢。

| 样例 id | 形式 | 最小可用写法 | 后端 API 结果 | 编辑器前端结果 | 结论 | 差异原因 |
|---|---|---|---|---|---|---|
| `attr-callout-title` | 容器式 `:::callout title=` | `:::callout type="tip" title="ZQCALTITLE"` | OK / 354 字符 | 435 字符 / 样式 261→327 | ✅ | 一致 |
| `attr-compare-marker-cn` | 容器式 `:::compare 中文标记` | `:::compare` | OK / 1205 字符 / 警告 1 | 1442 字符 / 样式 1013→1243 | ✅ | 一致 |
| `attr-table-title` | 容器式 `:::table title=` | `:::table style="card" title="ZQTBLTITLE"` | OK / 996 字符 | 1387 字符 / 样式 782→1008 / 丢标签 thead | ✅ | 归一化：`<thead>` 被 TipTap 拆成 `tableRow`+`tableHeader`，序列化时重新生成，语义等价（styleLen 782→1008） |
| `blk-badges` | 标签式 `<badges>` | `<badges type="accent">ZQBDG1\|ZQBDG2\|ZQBDG3</ba…` | OK / 878 字符 | 1046 字符 / 样式 703→920 | ✅ | 一致 |
| `blk-case-flow-badline` | 标签式 `<case-flow>` | `<case-flow label="ZQCFL">` | OK / 0 字符 / **缺标记 ZQCF1** | 7 字符 / 样式 0→0 | ❌ | **上游静默**：`<case-flow>` 里每行不以 `- [标签] ` 开头时**整块归零**（0 字符），`ok:true`、无 warnings；换成官方行格式 `- [案例 01] 标题` → 1034 字符。同族问题见 `upstream-issues.md` R2 |
| `blk-case-flow` | 标签式 `<case-flow>` | `<case-flow label="ZQCFL">` | OK / 1034 字符 | 1138 字符 / 样式 728→911 | ✅ | 一致 |
| `blk-cta` | 标签式 `<cta>` | `<cta label="ZQCTALABEL" title="ZQCTATITLE" act…` | OK / 616 字符 | 642 字符 / 样式 440→515 | ✅ | 一致 |
| `blk-engage-card` | 标签式 `<engage-card>` | `<engage-card title="ZQENGCARD" subtitle="ZQENG…` | OK / 3886 字符 | 3993 字符 / 样式 1901→2337 | ✅ | 一致 |
| `blk-engage-label` | 标签式 `<engage-label>` | `<engage-label title="ZQENGLABEL" label="ZQENGT…` | OK / 1897 字符 | 1984 字符 / 样式 796→932 | ✅ | 一致 |
| `blk-engage-tag` | 标签式 `<engage>` | `<engage type="DA02">ZQENGTAIL</engage>` | OK / 3890 字符 / **缺标记 ZQENGTAIL** | 3998 字符 / 样式 1901→2337 | ➖ | **非缺陷**：`<engage>` 是固定文案组件（产物 3890 字符正常），标签内的文字本就被忽略，缺的 `ZQENGTAIL` 是探针标记而非内容 |
| `blk-gov-header` | 标签式 `<gov-header>` | `<gov-header issuer="ZQGOVT机关" doc-no="ZQGOVS号"…` | OK / 941 字符 | 1176 字符 / 样式 696→948 | ✅ | 一致 |
| `blk-img` | 标签式 `<img>` | `<img src="https://robocopmao.github.io/r-markd…` | OK / 232 字符 | 253 字符 / 样式 114→130 | ✅ | 一致 |
| `blk-lead` | 标签式 `<lead>` | `<lead>ZQLEAD 引导文字正文。</lead>` | OK / 254 字符 | 283 字符 / 样式 196→227 | ✅ | 一致 |
| `blk-p-title` | 标签式 `<p-title>` | `<p-title number="01" title="ZQPTITLE" subtitle…` | OK / 1060 字符 | 1057 字符 / 样式 629→772 | ✅ | 一致 |
| `blk-slider-selfclose` | 标签式 `<slider>` | `<slider images="https://robocopmao.github.io/r…` | OK / 356 字符 | 215 字符 / 样式 140→164 / 丢标签 slider | ❌ | **上游缺陷**：标签式 `<slider … />`（自闭合）后端不识别，整行标签字面留在 `<p>` 里（356 字符、无 `<svg>`）；**同一个 open tag 只要补上 `</slider>` 就出 1141 字符的完整 SVG 轮播**，与容器式 `:::slider` 字节数相同。见 `upstream-issues.md` R4 与 `target/probe/r4_slider_discriminator.py` |
| `blk-slider` | 标签式 `<slider>` | `<slider images="https://robocopmao.github.io/r…` | OK / 1141 字符 | 1127 字符 / 样式 170→170 | ✅ | 一致 |
| `blk-statement` | 标签式 `<statement>` | `<statement>ZQSTMT 这是一句金句。</statement>` | OK / 157 字符 | 178 字符 / 样式 99→113 | ✅ | 一致 |
| `blk-steps-2` | 标签式 `<steps>` | `<steps label="ZQSTEPSL" title="ZQSTEPST">` | OK / 1475 字符 | 1614 字符 / 样式 968→1200 / 丢标签 span | ✅ | 归一化：渲染器的空 `<span leaf="">` 标记不承载内容与样式，被编辑器解包；文字与内联样式均完整保留 |
| `blk-steps-5` | 标签式 `<steps>` | `<steps label="ZQSTEPSL" title="ZQSTEPST">` | OK / 4076 字符 | 4162 字符 / 样式 2768→3169 | ✅ | 一致 |
| `blk-timeline` | 标签式 `<timeline>` | `<timeline>` | OK / 1829 字符 | 1712 字符 / 样式 1088→1277 / 丢标签 span | ✅ | 归一化：渲染器的空 `<span leaf="">` 标记不承载内容与样式，被编辑器解包；文字与内联样式均完整保留 |
| `blk-title` | 标签式 `<title>` | `<title type="DA01" label="ZQLABEL" subtitle="Z…` | OK / 2799 字符 | 2516 字符 / 样式 1674→1951 | ✅ | 一致 |
| `callout-container` | 容器式 `:::callout` | `:::callout type="tip" title="ZQCOUT"` | OK / 354 字符 | 435 字符 / 样式 261→327 | ✅ | 一致 |
| `callout-quote-tip` | 标准 Markdown | `> [TIP] ZQCTIP 标题` | OK / 356 字符 | 437 字符 / 样式 261→327 | ✅ | 一致 |
| `callout-quote-warning` | 标准 Markdown | `> [WARNING] ZQCWARN` | OK / 351 字符 | 431 字符 / 样式 261→327 | ✅ | 一致 |
| `callout-tip-container` | 容器式 `:::tip` | `:::tip ZQCTIPC` | OK / 536 字符 | 634 字符 / 样式 401→491 | ✅ | 一致 |
| `ctn-align` | 容器式 `:::align` | `:::align align="center"` | OK / 73 字符 | 91 字符 / 样式 33→37 | ✅ | 一致 |
| `ctn-breaking` | 容器式 `:::breaking` | `:::breaking badge="ZQBADGE2" title="ZQBREAK" s…` | OK / 1270 字符 | 1450 字符 / 样式 937→1194 | ✅ | 一致 |
| `ctn-case-flow` | 容器式 `:::case-flow` | `:::case-flow` | OK / 1032 字符 | 1136 字符 / 样式 728→911 | ✅ | 一致 |
| `ctn-code-block` | 容器式 `:::code-block` | `:::code-block lang="js" title="ZQCB1"` | OK / 959 字符 | 1090 字符 / 样式 689→824 | ✅ | 一致 |
| `ctn-compare` | 容器式 `:::compare` | `:::compare` | OK / 1204 字符 | 1441 字符 / 样式 1013→1243 | ✅ | 一致 |
| `ctn-cta` | 容器式 `:::cta` | `:::cta label="ZQCTL" title="ZQCTT"` | OK / 393 字符 | 419 字符 / 样式 281→342 / 丢标签 span | ✅ | 归一化：渲染器的空 `<span leaf="">` 标记不承载内容与样式，被编辑器解包；文字与内联样式均完整保留 |
| `ctn-danger` | 容器式 `:::danger` | `:::danger ZQDANGER` | OK / 575 字符 | 654 字符 / 样式 420→492 | ✅ | 一致 |
| `ctn-hint` | 容器式 `:::hint` | `:::hint` | OK / 349 字符 | 431 字符 / 样式 261→329 | ✅ | 一致 |
| `ctn-lead` | 容器式 `:::lead` | `:::lead` | OK / 254 字符 | 283 字符 / 样式 196→227 | ✅ | 一致 |
| `ctn-note` | 容器式 `:::note` | `:::note ZQNOTE 标题` | OK / 538 字符 | 636 字符 / 样式 401→491 | ✅ | 一致 |
| `ctn-reading-path` | 容器式 `:::reading-path` | `:::reading-path` | OK / 2255 字符 | 2588 字符 / 样式 1732→2079 | ✅ | 一致 |
| `ctn-slider` | 容器式 `:::slider` | `:::slider images="https://robocopmao.github.io…` | OK / 1141 字符 | 1127 字符 / 样式 170→170 | ✅ | 一致 |
| `ctn-steps-h` | 容器式 `:::steps-horizontal` | `:::steps-horizontal label="ZQSHL" title="ZQSHT…` | OK / 1551 字符 | 1686 字符 / 样式 1016→1265 / 丢标签 span | ✅ | 归一化：渲染器的空 `<span leaf="">` 标记不承载内容与样式，被编辑器解包；文字与内联样式均完整保留 |
| `ctn-steps-v` | 容器式 `:::steps-vertical` | `:::steps-vertical label="ZQSVL" title="ZQSVT"` | OK / 2578 字符 | 2640 字符 / 样式 1752→2017 | ✅ | 一致 |
| `ctn-success` | 容器式 `:::success` | `:::success ZQSUCC` | OK / 574 字符 | 653 字符 / 样式 420→492 | ✅ | 一致 |
| `ctn-table` | 容器式 `:::table` | `:::table style="card"` | OK / 996 字符 | 1387 字符 / 样式 782→1008 / 丢标签 thead | ✅ | 同 `attr-table-title`（两者是同一组件的不同写法） |
| `ctn-timeline` | 容器式 `:::timeline` | `:::timeline` | OK / 1829 字符 | 1712 字符 / 样式 1088→1277 / 丢标签 span | ✅ | 归一化：渲染器的空 `<span leaf="">` 标记不承载内容与样式，被编辑器解包；文字与内联样式均完整保留 |
| `diagram-mermaid` | 围栏代码块 | ````mermaid` | OK / 733 字符 | 835 字符 / 样式 463→576 | ✅ | 一致 |
| `in-badge` | 行内标记 `<Badge/>` | `正文里的 <Badge type="tip" text="ZQBADGE" /> 徽章。` | OK / 435 字符 | 508 字符 / 样式 332→419 | ✅ | 一致 |
| `in-bold-italic` | 行内标记 `***x***` | `正文里的 ***ZQBI*** 粗斜。` | OK / 303 字符 | 304 字符 / 样式 169→205 / 丢标签 span | ✅ | 归一化：渲染器的空 `<span leaf="">` 标记不承载内容与样式，被编辑器解包；文字与内联样式均完整保留 |
| `in-bold` | 行内标记 `**x**` | `正文里的 **ZQBOLD** 粗体。` | OK / 275 字符 | 297 字符 / 样式 169→205 / 丢标签 span | ✅ | 归一化：渲染器的空 `<span leaf="">` 标记不承载内容与样式，被编辑器解包；文字与内联样式均完整保留 |
| `in-code` | 行内标记 ``x`` | `正文里的 `ZQICODE` 行内代码。` | OK / 344 字符 | 422 字符 / 样式 260→331 | ✅ | 一致 |
| `in-em-hl` | 行内标记 `==x==` | `正文里的 ==ZQHL== 强调。` | OK / 387 字符 | 424 字符 / 样式 287→338 | ✅ | 一致 |
| `in-glow` | 行内标记 `::x::` | `正文里的 ::ZQGLOW:: 柔光。` | OK / 271 字符 | 295 字符 / 样式 169→207 | ✅ | 一致 |
| `in-icon` | 行内标记 `<Icon/>` | `图标 <icon name="material-symbols:star" size="2e…` | OK / 366 字符 | 411 字符 / 样式 205→237 | ✅ | 一致 |
| `in-indigo` | 行内标记 `^^x^^` | `正文里的 ^^ZQIND^^ 加重。` | OK / 258 字符 | 279 字符 / 样式 153→188 / 丢标签 span | ✅ | 归一化：渲染器的空 `<span leaf="">` 标记不承载内容与样式，被编辑器解包；文字与内联样式均完整保留 |
| `in-italic` | 行内标记 `*x*` | `正文里的 *ZQITAL* 斜体。` | OK / 229 字符 | 239 字符 / 样式 140→164 / 丢标签 span | ✅ | 归一化：渲染器的空 `<span leaf="">` 标记不承载内容与样式，被编辑器解包；文字与内联样式均完整保留 |
| `in-pill` | 行内标记 `!!x!!` | `正文里的 !!ZQPILL!! 胶囊。` | OK / 388 字符 | 458 字符 / 样式 286→370 | ✅ | 一致 |
| `in-strike` | 行内标记 `~~x~~` | `正文里的 ~~ZQSTRIKE~~ 删除线。` | OK / 256 字符 | 275 字符 / 样式 153→190 / 丢标签 del,span | ✅ | 归一化：`<del>` 被 TipTap 的 Strike 标记统一输出为 `<s>`，颜色内联样式保留（color:#94a3b8 原样） |
| `in-sub` | 行内标记 `~x~` | `分子式 H~2~O，标记 ZQSUB。` | OK / 233 字符 | 243 字符 / 样式 140→164 / 丢标签 span | ✅ | 归一化：渲染器的空 `<span leaf="">` 标记不承载内容与样式，被编辑器解包；文字与内联样式均完整保留 |
| `in-sup` | 行内标记 `^x^` | `平方 m^2^，标记 ZQSUP。` | OK / 231 字符 | 241 字符 / 样式 140→164 / 丢标签 span | ✅ | 归一化：渲染器的空 `<span leaf="">` 标记不承载内容与样式，被编辑器解包；文字与内联样式均完整保留 |
| `in-underline-accent` | 行内标记 `__x__` | `正文里的 __ZQUNDA__ 主题色下划线。` | OK / 327 字符 | 359 字符 / 样式 221→260 | ✅ | 一致 |
| `in-underline-html` | 标签式 `<u>` | `正文里的 <u>ZQUNDU</u> 普通下划线。` | OK / 209 字符 | 240 字符 / 样式 140→164 | ✅ | 一致 |
| `math-block-label` | 行内/块级公式 | `$` | OK / 2295 字符 | 2348 字符 / 样式 714→746 | ✅ | 一致 |
| `math-block` | 行内/块级公式 | `$` | OK / 2921 字符 | 2974 字符 / 样式 747→779 | ✅ | 一致 |
| `math-inline` | 行内/块级公式 | `行内公式 $E=mc^2$ 与 $\frac{1}{3}$，标记 ZQMATH。` | OK / 2197 字符 | 2228 字符 / 样式 469→493 | ✅ | 一致 |
| `md-code` | 行内标记 `markdown` | ````js` | OK / 772 字符 | 866 字符 / 样式 564→662 | ✅ | 一致 |
| `md-heading` | 行内标记 `markdown` | `## ZQHEAD2 ZQ01` | OK / 300 字符 | 349 字符 / 样式 221→263 | ✅ | 一致 |
| `md-hr` | 行内标记 `markdown` | `上文。` | OK / 506 字符 | 585 字符 / 样式 384→456 | ✅ | 一致 |
| `md-image-sized` | 行内标记 `markdown` | `![ZQIMGSZ](https://robocopmao.github.io/r-mark…` | OK / 255 字符 | 282 字符 / 样式 140→160 | ✅ | 一致 |
| `md-image` | 行内标记 `markdown` | `![ZQIMGALT](https://robocopmao.github.io/r-mar…` | OK / 227 字符 | 250 字符 / 样式 111→127 | ✅ | 一致 |
| `md-link` | 行内标记 `markdown` | `[ZQLINK](https://example.com)` | OK / 333 字符 | 412 字符 / 样式 219→261 / 丢标签 span | ✅ | 归一化：渲染器的空 `<span leaf="">` 标记不承载内容与样式，被编辑器解包；文字与内联样式均完整保留 |
| `md-list` | 行内标记 `markdown` | `- ZQITEM1` | OK / 669 字符 | 728 字符 / 样式 483→521 | ✅ | 一致 |
| `md-multi-image` | 行内标记 `markdown` | `< ![ZQGAL1](https://robocopmao.github.io/r-mar…` | OK / 448 字符 | 483 字符 / 样式 248→276 | ✅ | 一致 |
| `md-ordered` | 行内标记 `markdown` | `1. ZQORD1` | OK / 557 字符 | 654 字符 / 样式 369→445 | ✅ | 一致 |
| `md-quote` | 行内标记 `markdown` | `> ZQQUOTE 引用正文` | OK / 290 字符 | 312 字符 / 样式 215→230 | ✅ | 一致 |
| `md-table` | 行内标记 `markdown` | `\| 列甲 \| 列乙 \|` | OK / 1190 字符 | 1605 字符 / 样式 921→1171 / 丢标签 thead | ✅ | 同 `attr-table-title` |
| `md-task-list` | 行内标记 `markdown` | `- [x] ZQDONE` | OK / 931 字符 | 1020 字符 / 样式 387→469 | ✅ | 一致 |
| `reg-align-tag` | 标签式 `<align>` | `<align align="center">ZQALIGNTAG</align>` | OK / 71 字符 | 89 字符 / 样式 33→37 | ✅ | 一致 |
| `reg-hint-tag` | 标签式 `<hint>` | `<hint>ZQHINTTAG</hint>` | OK / 206 字符 | 224 字符 / 样式 140→164 / 丢标签 hint | ➖ | **写法无效（非缺陷）**：该组件的标签式写法在官方 `guide`、bundle 的组件 `spec` 与语法分支里都查无实据，渲染 API 按未知行原样透传；这两个 ID 只实现了容器式（`:::hint` 349 字符、`:::layout-hero` 576 字符，均正常）。本行是第五轮误判为上游缺陷的**反证**，见 `known-issues-handoff.md` 3.9 ⑤ |
| `reg-layout-hero-tag` | 标签式 `<layout-hero>` | `<layout-hero>ZQLHT 内容</layout-hero>` | OK / 219 字符 | 223 字符 / 样式 140→164 / 丢标签 layout-hero | ➖ | **写法无效（非缺陷）**：该组件的标签式写法在官方 `guide`、bundle 的组件 `spec` 与语法分支里都查无实据，渲染 API 按未知行原样透传；这两个 ID 只实现了容器式（`:::hint` 349 字符、`:::layout-hero` 576 字符，均正常）。本行是第五轮误判为上游缺陷的**反证**，见 `known-issues-handoff.md` 3.9 ⑤ |
| `reg-layout-hero` | 容器式 `:::layout-hero` | `:::layout-hero` | OK / 576 字符 | 655 字符 / 样式 420→492 | ✅ | 一致 |
| `reg-layout-metrics` | 容器式 `:::layout-metrics` | `:::layout-metrics` | OK / 759 字符 | 842 字符 / 样式 538→607 | ✅ | 一致 |
| `reg-layout-toc` | 容器式 `:::layout-toc` | `:::layout-toc` | OK / 751 字符 | 834 字符 / 样式 538→607 | ✅ | 一致 |

**读表要点**（避免误判）：

- 「丢标签 `span` / `thead` / `del`」在这张表里**大多是假的**：`span` 指渲染器的空 `<span leaf="">` 标记被解包
  （不承载内容与样式）；`thead` 是 TipTap 把 `<thead>` 拆成 `tableRow`+`tableHeader`、序列化时再生成；
  `del` 是 TipTap 的 Strike 标记统一输出成 `<s>`（颜色内联样式保留）。这三类已在下表按「归一化」标注。
- 「字符数变多」不是问题：编辑器会给块补 `<p>`、把 `#27ae60` 写成 `rgb(39, 174, 96)`、给属性补空格。
  真正要看的是**文字有没有丢、内联样式长度有没有缩水**。
- 后端一栏的 `警告` 是 `meta.warnings` 的条数。**它只在 2 类规则上会报**（compare 列数/取值、timeline 列数），
  其余全部静默——详见 `upstream-issues.md` R2。
- **表里唯一的两条 ❌ 都在后端**（`blk-slider-selfclose`、`blk-case-flow-badline`），编辑器侧在这两条上都是"照单全收"，
  也就是说这两条**不是编辑器的问题**：后端没吐出来的东西，编辑器无从渲染。

#### ④ 本轮修掉的（每一条都有红→绿的前后对照）

改动**全部在编辑器前端**（`webui/index.html`、`webui/src/editorExtensions.js`、`webui/src/views/ArticleEditorView.vue`），
后端 API 路径一行没动——因为这一轮查出来的后端问题**全在上游**（见 ⑤）。

| # | 症状 | 根因（定位到文件行） | 红（改前） | 绿（改后） |
|---|---|---|---|---|
| **D30** | 编辑器里**公式渲染不出来**：`\frac{1}{3}` 塌成并排的 `3` `1`，整棵 KaTeX 树散架 | 两层都缺：① `webui/index.html` 没有 KaTeX 样式表——渲染服务给的是 `<span class="katex">…` 纯 class 结构，元素本身几乎没有内联样式，不带样式表就是一串普通 span；② `editorExtensions.js` 对"只有 class 的 span"没有任何声明，TipTap 解析→序列化一次全丢 | `components/math-inline.editor.html`：**2197 → 471 字符** | **2197 → 2228 字符**，`\frac{1}{3}` 结构完整。修法：`index.html` 加 `katex@0.17.0` 样式表（版本与渲染服务预览页用的那一条相同，见 `target/probe/katex_css_probe.txt`）；`editorExtensions.js` 新增 `RawMath`——**不透明原子节点**整体存 `outerHTML` 再原样吐回 |
| **D31** | **轮播图在编辑器里渲染不出来**，`<engage-card>` 的三个图标圈全空 | ProseMirror 的 schema 里没有 svg 节点，未知元素一律"解包"（标签丢掉、只留文字） | `components/ctn-slider.editor.html`：后端 1144 字符的动画轮播（`<animateTransform>` + 三个 `<foreignObject>`）→ **只剩 403 字符的三张上下堆叠裸 `<img>`**；三篇真实稿件 `<svg>` **9 → 0** | 新增 `RawSvg`（同为不透明原子节点，带 `<script>`/`on*=`/`javascript:` 黑名单二次校验）。组件样例 **1 → 1**，真实稿件 `<svg>` **9 → 9** |
| **D32** | **列表圆点消失**（`md-list`、`:::layout-toc`、`:::layout-metrics` 全部中招） | 渲染服务的列表项是 `<section style="display:flex"><span style="width:6px;height:6px;border-radius:50%;background:#27ae60"></span><span style="flex:1">正文</span></section>`——圆点完全由那个**空 span** 画出来，而 ProseMirror 的标记只挂在**文本节点**上，没有子节点的 span 产不出文本、标记无处可挂，整个元素被静默丢弃 | `components/md-list.editor.html`：两个圆点 span 全丢，只剩 `<p><span style="flex: 1 1 0%;">ZQITEM1</span></p>`；真实稿件 `5.html` 有 **7 处** | 新增 `PreservedEmptySpan`（`tag: 'span[style]'` + `childNodes.length === 0` 才认领，priority 105 低于 `RawMath` 的 110/120）。`md-list` 样式长度 **483 → 521**，两个圆点原样保留 |
| **D33** | **高亮底纹整条消失**：`==文字==` 的渐变背景没了 | `BackgroundColor` 读的是 `element.style.backgroundColor`，而 `background: linear-gradient(…)` 简写按 CSSOM 会把没写到的长写设成初始值，于是它读到 `transparent`、回写一句 `background-color: transparent`。而 `readPreservedMarkStyle` 又把 `background` 当成"由专用扩展接管"过滤掉了 | `components/in-em-hl.editor.html`：样式长度 **287 → 275**（渐变没了） | `readPreservedMarkStyle` 改为**按值判断**：被接管的声明只有值是纯色时才过滤，含 `gradient(` / `url(` 的自己留着。样式长度 **287 → 338**。影响面（口径见 `round5_impact.mjs`）：79 个样例里 `gradient(` **13 处 / 11 个样例**（改前丢 2 处）；6 篇真实稿件 **30 处 / 6 篇全中**（改前丢 9 处） |
| **D34** | **字重被改写**：`font-weight:600` 的 span 被套 `<strong>`（渲染成 700，变粗）；`font-weight:800` 的章节号反被压到 700（变细） | TipTap 自带 Bold 有一条 `{ style: 'font-weight', getAttrs: value => /^(bold(er)?|[5-9]\d{2,})$/.test(value) }` 规则——**任何**元素只要内联 `font-weight` ≥ 500 就落成粗体标记 | 实测内联 `font-weight` ≥ 500 共 **116 处 / 41 个样例**（6 篇真实稿件 **409 处**，全部命中）；改前编辑器**凭空多出 `<strong>` 124 个**（真实稿件 446 个 vs 后端的 190 个）；`blk-badges` 的三个胶囊里凭空多出 `<strong>` 把 600 顶成 700 | 新增 `MarkflowBold = TipTapBold.extend({ parseHTML: […] })`，**去掉最后那条 style 规则**（保留 `<strong>`/`<b>` 与 `font-weight=400` 的 clearMark）。改后组件样例的 `<strong>` **0 个**、真实稿件 **190 个 = 后端原值**，`font-weight` 原值保留 |
| **D35** | **相邻同款 span 被合并**：`<badges>` 的三个独立胶囊塌成**一个**胶囊、内容连成 `ABC` | ProseMirror 会把"标记集合完全相同"的相邻文本合并成一个文本节点——三个胶囊的样式逐字符相同，于是被合并（这是渲染保真问题，不是文本问题） | `components/blk-badges.editor.html`：样式长度 **703 → 360**，三个胶囊只剩一个 | `PreservedMarkStyle` 增一个**只在编辑器内部存在**的 `sourceSpanId`（每个 `span` 一个递增序号，`renderHTML` 返回空对象），让相邻 span 的标记不再"相等"。样式长度 **703 → 920**，三个胶囊各自独立。该属性既不进 `getHTML()`（保存/预览出口）也不进 `editorDocument()` 的 `domBlocks[index].outerHTML`（`ArticleEditorView.vue:158-166`） |
| **D36** | **代码块语法高亮丢失**：`<span style="color:#c678dd">const</span>` 进去只剩黑底白字，`data-lang` 也没了 | TipTap 的 CodeBlock 规格里写死 `marks: ""`（`@tiptap/extension-code-block`），节点 `content` 又是 `text*`，高亮标记无处可落；`data-lang` 与内层 `<code>` 的 style 没有任何属性声明 | `components/md-code.editor.html` 样式长度 **564 → 308**；`diagram-mermaid` **463 → 308**；`ctn-code-block` **689 → 350** | 新增 `MarkflowCodeBlock = TipTapCodeBlock.extend({ marks: 'textStyle', … })`：**只放开 textStyle 一种标记**（代码仍是普通 text 节点、仍可编辑，实测 codeBlock 内可编辑字符数 17 → 17），并接管 `data-lang` 与内层 `<code>` 的 style。`md-code` **564 → 662**、`diagram-mermaid` **463 → 576**、`ctn-code-block` **689 → 824**，两个高亮 span 与 `data-lang="js"` 原样往返 |
| **D37** | **语义标签上的内联样式整条丢失**：`<strong style="font-size:60px">` 只剩裸 `<strong>`，章节号叠印版式塌掉 | `PreservedMarkStyle` 的过滤清单只在**落在 `<span>` 上**时成立（Color / BackgroundColor / FontSize / LineHeight 四个扩展的 `types` 默认就是 `['textStyle']`，只认带 style 的 `<span>`），`<strong>`/`<em>`/`<s>` 上的声明再按同一张清单过滤就是纯丢 | `components/blk-p-title.editor.html`：`<strong style="display:block;font-size:60px;line-height:1;opacity:.25">01</strong>` → `<strong>01</strong>` | `readPreservedMarkStyle` 只对 `SPAN` 应用受管清单，并把 `preservedMarkTypes` 从 `['textStyle']` 扩到 `['textStyle','bold','italic','strike','underline','code','link']` |
| **D38** | **`<sub>` / `<sup>` 被解包**：`H₂O` 变成 `H2O`，`m²` 变成 `m2` | schema 里没有这两个标记，未知标签被解包、只剩文字 | `in-sub` 233 → 233 但 `<sub>` 消失 | 新增 `Subscript` / `Superscript` 两个 `Mark`。`in-sub` 233 → 243、`in-sup` 231 → 241，`<sub>`/`<sup>` 原样 |
| **D39** | 渲染产物里的**块级 `class` 被丢弃**（`<section class="tableWrapper">`） | 与 `data-render-id` 同理：渲染器写着、编辑器不认识，parse → serialize 一次就没了 | `blk-title` 的 `tableWrapper` 丢失 | 新增 `PreservedClass`（**只声明在块级节点上**：公式那类 class 驱动的行内结构用标记接不住——同名标记不能嵌套，已整体交给 `RawMath`） |

**79 个组件样例上的红→绿总账**（同一批后端产物，分别灌进改前/改后扩展集，`fe_harness.mjs` 与 `fe_harness.mjs --legacy`）：

| | 字符数 | 内联样式长度 | `<svg>` | 编辑器侧有损样例 |
|---|---|---|---|---|
| 后端产物（基准） | 70181 | 41598 | 13 | — |
| 改前（`--legacy`） | 52874（**−24.7%**） | 35316（**−15.1%**） | **0** | **27 / 79** |
| 改后 | 75519（+7.6%，补的是 `<p>` 包装与 `rgb()` 展开） | 49833（+19.8%） | **13** | 19 / 79（余下全是归一化标注 + ④ 里那 2 条上游） |

**真实稿件上的红→绿总账**（同一份库内 HTML，改动前扩展集 vs 当前扩展集）：

| | 字符数 | 内联样式长度 | `<svg>` | `class` | 可见文字 |
|---|---|---|---|---|---|
| 改前（`--legacy`） | 194969 → **174262**（−10.6%） | 119009 → **107052**（−10.0%） | **9 → 0** | **88 → 0** | 6/6 完整 |
| 改后 | 194969 → **204271**（+4.8%，补的多是 `<p>` 包装与 `rgb()` 展开） | 119009 → **132814**（+11.6%） | **9 → 9** | **88 → 88** | 6/6 完整 |

单篇最直观的是**文章 5**（含 5 个公式、77 个 `class`）：改前 10001 → **6045 字符**（−40%，公式整棵毁掉、`class` 77→0），
改后 10001 → **9957 字符**、`class` 77 → 77、`katex` 5 → 5。

#### ⑤ 未修 / 不修（含原因）

**先说结论**：这一轮**没有留下"编辑器侧还能修但没修"的项**——表里 ❌ 的 2 条都在后端，且根因全在上游；
另外 4 条曾经记成"上游缺陷"，定稿前复核发现是**我们自己的样例写错了**，已更正。

| 项 | 为什么不动 |
|---|---|
| 标签式 `<slider … />`（自闭合）被当正文透传 | **真上游缺陷**，`upstream-issues.md` R4。判别实验（`r4_slider_discriminator.py`）定位到条件是**缺 `</slider>` 闭合标签**：成对写法 1141 字符带 SVG 轮播、自闭合 356 字符、官方容器式同为 1141。本项目技能提示一律用容器式；**保存自检原先并不看标签式，第六轮补上了（D40），并在真实浏览器里确认「模型写错时用户看到的是一处空白」**，详见 §3.10 |
| `<case-flow>` 行格式不合法 → 整块 0 字符 | **真上游静默**（R2 已记同族）：`ok:true`、无 `warnings`。本项目技能提示按官方行格式 `- [标签] 标题` 写；**第六轮用真实渲染 API 做了 4 组端到端对照（官方行格式 1031 字符 / 去掉标签 0 字符），并用真实库文章确认落库与编辑器两侧都正常**，详见 §3.10 |
| `<hint>` / `<layout-hero>` 的**标签式**写法不渲染 | **不是缺陷，是第五轮的误判**：这两个写法在官方 `guide`、bundle 的组件 `spec` 与语法分支里**都查无实据**，渲染 API 按未知行原样透传。这两个 ID 只有容器式（`:::hint` 349 字符、`:::layout-hero` 576 字符，均正常）。样例保留下来当反例、表里标 ➖。→ `upstream-issues.md` R4-附 |
| `<gov-header title=… subtitle=…>` 丢标题 | **不是缺陷，是第五轮的误判**：该组件字段是 `issuer`（必填）/ `doc-no` / `classification` / `urgency` / `signer`，**没有** `title`/`subtitle`。换成官方字段 → 941 字符正常。样例已改 |
| `<case-flow label=…>` + `[X] 标题 \| 描述` 行 → 0 字符 | **不是缺陷，是第五轮的误判**：官方行格式是 `- [标签] 标题`。换成官方格式 → 1034 字符正常。样例已改，另留 `blk-case-flow-badline` 反例样例记录"行格式不对就静默归零"这件事 |
| 表格单元格 `border-bottom-width/style/color` 被缩写成 `border-bottom: medium` | **等价归一化，不是丢失**：`medium none currentcolor` 与 `medium` 渲染结果相同（都是没有边框）。成因是读到 `element.style` 后属性被重新序列化（`target/probe` 里用 jsdom 复现过：`setAttribute` 再碰一次 CSSOM，读回来就变成合并形式）。影响 24/38 两篇各 3 个单元格、共 204 字符样式长度 |
| 渲染产物上的 `data-*`（`data-block` / `data-caption-kind`） | 本仓库**没有任何消费方**（全仓库 grep 无引用），对渲染无影响；只在编辑器往返里丢掉。真实稿件里共 38 处（`data-block` 27 + `data-caption-kind` 11）。本轮已修的是**有渲染影响**的那几类（class / style / svg / 公式 / 圆点），这一类留作已知残留 |
| `<engage type="DA02">文字</engage>` 里的文字 | **不是缺陷**：`<engage>` 是固定文案组件（产物 3890 字符正常），标签内的文字本来就被忽略。探针报"缺标记"是标记词设计问题，表里标 ➖ |
| `:::success` / `:::danger` | 上游不支持、字面输出 `::: danger`；项目技能提示里已明确禁用并有保存自检（`ScheduledArticleTools`）。见 3.5 D29 一族 |

#### ⑥ 未验证 / 没覆盖到的（别读成"测过没问题"）

- **只在 jsdom 里跑的编辑器路径**：探针用的是 headless 编辑器 + jsdom，**没有开真实浏览器**
  （chrome-devtools MCP 本轮连不上，报 `-32000 Connection closed`）。
  结论（文字/样式/标签/svg 是否保留）由 ProseMirror 的 schema 与 `DOMSerializer` 决定，与渲染引擎无关，
  但**像素级版式**（flex 行内元素的实际换行、KaTeX 的 `vlist` 高度）没有验证。
  → **第六轮已补**：用系统已装的 Chrome 138 + 自写 CDP 驱动做了 14 组真实浏览器实拍（零新增依赖），
  D30–D39 每一项都有「改前不渲染 / 改后渲染」两侧断言，见 §3.10 ①。
- **75 个样例的写法没有逐条回查官方定义**：本轮回查的只有出问题的那 4 条（见 ① 与 `upstream-issues.md` R4-附）。
  表里那些 ✅ 只能说明"两条路径对同一份输入是一致的"，**不能说明这份输入就是官方推荐写法**。
- **`<slider>` 自闭合在 Web 编辑器里的表现未验证**：bundle 里 `match` 与 `render` 两处正则对自闭合行的处理不一致
  （见 R4），但 Web 侧实际怎么显示**没跑浏览器确认**，只记录了正则位置。
  → **第六轮已补**：真实浏览器里编辑器把未知元素解包，**一个字符都不剩（成稿里是一处空白，不是看得见的一行标签）**，
  截图与断言见 `target/probe/browser/browser_summary.md` 的 `blk-slider-selfclose` 行、§3.10 ①。
- **`<p>` 包装造成的版式偏移**：渲染服务把列表项直接写成 `<section style="display:flex">` 的两个 inline 子元素，
  而编辑器把 `<section>` 解析成块级容器、行内子元素被包进一个 `<p>`——圆点所以还是丢的（D32 已修）但
  flex 的直接子元素层级变了，**多行列表的悬挂缩进可能有肉眼可见的差别，未逐一实测**。
- **79 个样例之外的语法组合**：嵌套容器、属性写法变体（单引号/无引号）、CRLF、超长表格都没覆盖，
  与 `upstream-issues.md` §R2-附 ③ 的未测清单同源。
- **幂等性只测到"第二遍不劣化"**：89 个产物文件里 88 个第二遍完全一致，`in-icon` 只差 1 个空格；
  抽 3 个做三连往返确认 `pass2 === pass3`（收敛，不持续劣化）。第三遍之后的形态未逐一验证。
- **图片/外链组件**（`md-image` / `md-multi-image` / `md-link`）在本轮里是"一致"，但它们的 `src` 走的是
  站点绝对直链（`RenderConfig.siteBaseUrl`），**探针里网络不可达时的表现未单独验证**。

#### ⑦ 收尾数字

- 全量测试：`./.mvn/mvn-local.sh -o test` → **Tests run: 295, Failures: 0, Errors: 0, Skipped: 0，BUILD SUCCESS**（02:45 min）。
  本轮改动**全在 `webui/` 与文档**，Java 源码与测试一行未动。
- 前端构建：`cd webui && npm run build` → **✓ built in 398ms**（`ArticleEditorView` chunk 541.79 kB）。
- 新增两个**直接依赖**（此前只是 StarterKit 的传递依赖，本项目约定直接 import 的 TipTap 包一律声明）：
  `@tiptap/extension-code-block@3.28.0`、`@tiptap/extension-bold@3.28.0`；`package.json` 与 `package-lock.json` 各 +2 行。
- `git status --porcelain`：`target/probe` 在 `.gitignore` 里，不出现在列表；`src/` 与 `webui/src/` 下**没有**本轮之外的意外新增文件
  （工作树里另有前几轮未提交的 Java 改动，属 3.5–3.8 的成果，本轮未动）。

---

### 3.10 2026-09-13 第六轮：把「编辑器渲染能力」从 jsdom 结论升级为真实浏览器实拍 + R2/R4 绕过端到端复核

**这一轮要回答的是三个「你说是这么说，凭什么」**：
第五轮的 D30–D39 全部结论**只在 jsdom 里跑过**（jsdom 里元素盒子恒为 0，「画出来了没有」根本量不出来）；
R2/R4 两条上游绕过的文档写着「技能提示 + 保存自检」，但**没有一条走过真实链路**；
79 项对照表里还留着 19 条带「丢标签」的行，此前被一句归一化说明**按标签名统一盖住**。

#### ① D30–D39 的真实浏览器实拍（14 组样例，改前 / 改后两侧都断言）

**没有引入任何新依赖**：仓库与全局都没有 playwright/puppeteer（第五轮已确认），
所以写了一份约 150 行的 CDP 客户端（`target/probe/browser/cdp.mjs`），驱动系统已装的
**Chrome/138.0.7204.100**（`C:\Program Files\Google\Chrome\Application\chrome.exe`）。
探针页面自己一份 Vite 配置（`browser/vite.config.mjs`，root 指向探针目录、靠一条 alias 从
`webui/node_modules` 解析 `@tiptap/*`），**文件不落进 `webui/`**；`before` 侧的扩展集由
`prepare-legacy.mjs` 从 `git HEAD` 现生成。

```bash
node target/probe/browser/prepare-legacy.mjs                              # 生成 HEAD 版扩展集
(cd webui && npx vite build --config ../target/probe/browser/vite.config.mjs)
node target/probe/browser/run-browser.mjs                                 # 逐行「改前 / 改后」并排渲染 + 截图
node target/probe/browser/summarize.mjs                                   # 逐项断言两侧，产出结论表
```

**结论表：`target/probe/browser/browser_summary.md`，截图 `target/probe/browser/shots/<id>.png`（14 张），原始量：`browser_result.json`。**
断言写法本身就带着「能被反证」的要求——每一项都写成代码，**「改前坏了」和「改后好了」两侧分别断言**，
任一侧不成立就打印实测值并判不通过（`summarize.mjs`）。当前 **14/14 全部两侧通过**。

| 修复项 | 改前（HEAD 扩展集） | 改后（当前扩展集） |
|---|---|---|
| D30 行内 / 块级公式 | `.katex` 0 个 | `.katex` 2 个（高 21/21）、`.katex-display` 1 个（高 45） |
| D31 轮播 `<svg>` | 0 个 | 1 个，盒子 600×200，子元素 `g / animateTransform / foreignObject / img` |
| D31 图标圈 `<svg>` | 0 个 | 3 个，盒子 24×24 / 28×28 / 24×24 |
| D32 列表圆点 | 0 个 | 2 个，盒子 6×6 |
| D33 渐变底纹 | 计算样式里 0 处 gradient | 1 处 |
| D34/D35 字重与胶囊 | 1 个合并胶囊 184×33、`<strong>` 1 个 | 3 个胶囊 78×33、`<strong>` 0 个 |
| D36 代码高亮 | 0 个高亮 span、无 `data-lang` | 2 个 span，配色 `rgb(198,120,221)` / `rgb(209,154,102)`，`data-lang=js` |
| D37 语义标签内联样式 | `<strong>01</strong>` 15px | 60px、`display:block`、`opacity:0.25` |
| D38 `<sub>` / `<sup>` | 0 个 | 各 1 个，盒子 8×19 |
| D39 块级 `class` | `getHTML()` 里 `[]` | `["tableWrapper"]` |

**顺带关掉的两个旧问题**：
- **KaTeX webfont 确实加载了**（而不是像之前记录的没加载）：
  `document.fonts.check('16px KaTeX_Main')` 在真实浏览器里返回 `true`，
  公式高度在字体就绪后重新量过一次（`katexHeightsAfterFonts`）。
- **`<slider>` 自闭合在 Web 编辑器里的表现**（R4 里明确标着「未验证」的那条）：
  不是「留一行看得见的标签」，而是**一处空白**——后端把它当正文透传成未知 HTML 元素，
  浏览器渲染成空的 inline 盒子，编辑器解包后一个字符都不剩（可见文字 0 字）。
  截图 `shots/blk-slider-selfclose.png`，该行在结论表里作为**对照项**保留（两侧都不该画出来），
  哪天它冒出 `<svg>` 就说明上游修了 R4、保存自检那条提示可以撤。

**踩过并修掉的三个坑（都在探针代码里留了注释，避免下次重踩）**：
1. `Page.captureScreenshot` 只给 `clip`、不给 `captureBeyondViewport: true` 时，**视口外的行会拍成空白页**
   （12 张截图的字节数一模一样，全是背景色）。补上之后 14 张各不相同。
2. 轮播图的 banner 是公网图，**不等解码完就截会拍到一张白图**（盒子量得对、图是白的）。
   现在截图前等 `image.complete` + load/error 事件（最长 15 秒兜底）。
3. 「行高异常」不一定是缺陷：`ctn-slider` 那一行 1607 px，探针的 `tallest` 诊断证明是**改前那一栏**三张
   竖排的 600×200 `<img>` 撑的，与渲染正确性无关。

#### ② R4 绕过端到端：技能提示 + 保存自检 + 真实落库 + 真实编辑器

分四段，每段都有「用什么输入、得到什么输出」：

| 环节 | 输入 | 输出 | 证据 |
|---|---|---|---|
| 技能提示 | 库里 `SKILL` 表的 MarkFlow 技能（ID=4） | 含 `:::slider images="…" interval="3" …`（位置 2810）；**全文 0 处 `<slider`** | `SELECT LOCATE(':::slider', CONTENT), LOCATE('<slider', CONTENT) FROM SKILL WHERE ID=4` → `2810, 0`（`SkillSeeder` 每次启动按 `builtin_key` 覆盖更新，库里就是当前源码那份） |
| 保存自检 | `<slider images="…" interval="3" />`（自闭合） | **改前 `[]`**（什么都不报）→ **改后 1 条**指向容器式写法的提示 | `ScheduledArticleToolsDraftTests#sliderTagWithoutClosingTagIsReported`，红→绿各跑一次 |
| 落库 | `POST /api/articles` + `POST /api/articles/42/rerender`（真实渲染服务） | 输入 285 字符 Markdown；`CONTENT_HTML` **2662 字符、`<svg>`×1 / `<animateTransform>`×1 / `<foreignObject>`×3**；直查 MySQL 与接口返回**逐字节等长**；`:::slider` 在 `CONTENT_MARKDOWN` 位置 42 | `target/probe/round6_e2e.{py,json,md}`（文章 ID 42，标题带 `[probe]` 前缀，可随时删） |
| 编辑器 | 真实 SPA（`webui/dist` 产物，静态服务 + `/api` 代理到 8081）打开 `/articles/42` | `.ProseMirror` 里 **1 个 `<svg>`、可见、盒子 600×200**，`<animateTransform>`×1、`<foreignObject>`×3 | `target/probe/browser/editor_result.json`、截图 `shots/editor-article.png` |

**同时确认了「这条绕过为什么必要」**：真实渲染 API 上同一个 open tag，
`<slider … />`（自闭合）→ **356 字符、0 个 `<svg>`**；补上 `</slider>` → **1141 字符、1 个 `<svg>`**；
容器式 `:::slider` → **1141 字符、1 个 `<svg>`**。原始输入输出见 `target/probe/round6_r2r4.md`。

#### ③ R2 绕过端到端：`<case-flow>` 官方行格式不再归零

同一份内容、只换行格式，打真实渲染 API（`target/probe/round6_r2r4.md` 的 `R2-A`…`R2-D`）：

| 输入 | 产物字符数 | `<svg>` | 后果 |
|---|---|---|---|
| `:::case-flow` + 官方行格式 `- [案例 01] 从零搭建个人知识库` | **1031** | 0 | 两行各渲染成一张 flex 卡片 |
| `<case-flow>` + 官方行格式 | **1031** | 0 | 与容器式同量级 |
| `<case-flow>` + 行里少了 `[标签]` | **0** | 0 | **整块静默消失**，`ok:true`、`warnings: []` |
| `:::case-flow` + 行里少了 `[标签]` | **0** | 0 | 同上（容器式与标签式同一条规则） |

落库侧同样走通：文章 42 的 `CONTENT_HTML` 里 `:::case-flow` 两行各产出一张 flex 卡片
（`<tr>/<th>` 之外的结构：标签胶囊 + 标题，`caseFlowRows: 2`）；
编辑器侧两个标签胶囊都在（文字「案例 01」「案例 02」，盒子 75×31，行高 89）。
保存自检对「少 `[标签]` 的行」的拦截在第五轮就有（`caseFlowWithoutLabel`），本轮未改。

#### ④ 第五轮表里剩余 19 条「丢标签」逐条归类（不再按标签名统一盖住）

判据落到**实例**上，而不是标签名上（`target/probe/round6_lossy_audit.mjs` → `.json` / `.md`）：
① 把后端产物里被判「丢了」的标签逐个捞出来，看它带几条内联样式、带几个 `leaf` 以外的属性；
② 文字（去标签去空白）两边是否逐字一致；③ 内联样式总长度是否缩水；
④ 换标签名的还要核对「换过之后语义与样式还在不在」。**19 条全部成立，没有一条落在「不知道所以略过」**：

| 归类 | 条数 | 是什么 | 代表证据 |
|---|---|---|---|
| 归一化：后端 `<span leaf="">` 纯标记元素被解包 | **12** | 渲染器给自己写的定位标记，**零条内联样式、`leaf` 之外没有任何属性**，解包它不丢东西 | `in-bold` 的 `<span leaf="">` 内含「ZQBOLD」，编辑器输出 `<strong style="font-weight:800;color:#123">ZQBOLD</strong>`，文字 13→13、样式 169→205 |
| 归一化：同语义换标签名 | **4** | `<del style="color:#94a3b8">` → `<s style="color: rgb(148, 163, 184);">`（同一个颜色值）；`<thead>` 元素被换成表头行落进 `<tbody>`，但 `<tr>` / `<th>` / `<td>` 数量、表头文字、`<th>` 上的 9 条内联样式**逐条保留** | `in-strike` 颜色集合前后一致；`attr-table-title` / `ctn-table` / `md-table`：`tr` 2→2、`th` 2→2、`td` 2→2、表头文字一致、`<th>` 样式声明数 `[9,9]→[9,9]` |
| 上游 R4 | **1** | `blk-slider-selfclose` | 见 ② |
| 上游 R2 | **2** | `reg-hint-tag` / `reg-layout-hero-tag`：写法在官方来源里查无实据，渲染器按未知行**原样透传且不报 warnings**——这正是 R2 那条「静默处理」的又两个实例 | `round6_lossy_audit.md` 里两条的实例与文字对照 |

#### ⑤ 真实稿件回归：6 篇库内稿件，改动前后对照（jsdom 复算 + 浏览器实测）

**jsdom（`fe_real.mjs`，口径与第五轮完全一致，可复现第五轮表里的数字）**：

| | 字符数 | 内联样式长度 | `<svg>` | `class` | 可见文字 |
|---|---|---|---|---|---|
| 改前（`--legacy`） | 194969 → **174262**（−10.6%） | 119009 → **107052**（−10.0%） | **9 → 0** | **88 → 0** | 6/6 完整 |
| 改后 | 194969 → **204271**（+4.8%） | 119009 → **132814**（+11.6%） | **9 → 9** | **88 → 88** | 6/6 完整 |

**真实浏览器（`target/probe/browser/run-real-editor.mjs`）**——这一份量的是「真的画出来了没有」，
jsdom 量不出来（盒子恒为 0）：

| 稿件 | 可见文字 | `<svg>` 可见/总数 | 盒子 | `class="` 出现次数（实时 DOM 口径） |
|---|---|---|---|---|
| 24 | 2150 | 0/0 | — | 6 |
| 30 | 4965 | **3/3** | 28×28 / 32×32 / 28×28 | 4 |
| 35 | 3777 | **3/3** | 28×28 / 32×32 / 28×28 | 10 |
| 38 | 2150 | 0/0 | — | 6 |
| 40 | 5518 | **3/3** | 24×24 / 28×28 / 24×24 | 12 |
| **合计（5 篇挂起来的）** | — | **9/9** | — | 38 |

- **文章 5 在这张表里缺席是事实，不是遗漏**：它在库里已软删（`DELETED=1`），接口 404、编辑器挂不起来，
  只有历史 HTML 快照（`target/probe/real/5.html`）能测，由上面那份 jsdom 表覆盖
  （改前 10001→6045、`class` 77→0；改后 10001→10564、`class` 77→77）。
- **两处数字口径不同，别混读**：jsdom 那列数的是 `getHTML()` 里的 `class="`；浏览器这列数的是**实时 DOM** 里的，
  会多出 TipTap 自己给表格加的 `tableWrapper` 包装，所以 35 那篇是 10 而不是 7。两边都不能直接相减。
- **浏览器侧 `katex` 全是 0**：库里现存的 5 篇没有一篇带公式（唯一带公式的文章 5 已软删）。
  公式的浏览器实拍只来自 ① 里的两个合成样例，**不要读成「真实稿件的公式也验过了」**。

结论：**已修项在真实稿件上没有把原本正常的组件弄坏**——6 篇可见文字 6/6 完整，
`<svg>` 从 9 掉到 0 的那 3 篇现在 9/9、`class` 从 88 掉到 0 的现在全部保住。

#### ⑥ 本轮新发现并修掉的缺陷：D40（保存自检漏了标签式 `<slider>`）

「若发现有第三条真缺陷，本轮修掉」——这一条是查 R4 的绕过时查出来的：
**文档写着「保存前有语法自检」，代码里却根本没有标签式的判据**（`sliderWithoutImages` 只匹配 `:::slider` 容器，
`CONTAINER_ONLY_AS_TAG` 又特意把 `slider` 排除在外）。也就是说这条绕过此前**只做了一半**：
技能提示要求容器式，但模型真写成标签式时，保存这一步不会有任何反馈，而成稿里是一处空白。

- **红**：先加用例 `sliderTagWithoutClosingTagIsReported`，跑 `-Dtest=ScheduledArticleToolsDraftTests`
  → `Tests run: 23, Failures: 1`，实测 `[]`。
- **绿**：`ScheduledArticleTools` 新增 `SLIDER_TAG_OPEN` / `SLIDER_TAG_CLOSE` 与 `sliderTagWithoutClosingTag`，
  判据是「**有没有 `</slider>` 闭合标签**」而不是「有没有自闭合斜杠」——实测 `<slider …/></slider>`
  （斜杠与闭合标签同时在）同样出 1141 字符，按斜杠判会误报。改完同一条用例 `Failures: 0`。
- **同时守住不误报**：成对写法、`<slider …/></slider>`、单图成对写法、容器式四种输入仍返回 `[]`（同一个用例里断言）。

#### ⑦ 收尾数字

- 全量测试：`./.mvn/mvn-local.sh -o test` → **Tests run: 296, Failures: 0, Errors: 0, Skipped: 0，BUILD SUCCESS**（02:53 min）。
  比第五轮的 295 多 1，就是 D40 新增的那条用例；日志留档 `target/probe/round6_full_test.log`。
- 前端构建：`cd webui && npm run build` → **✓ built in 381ms**（`ArticleEditorView` chunk 541.79 kB）。
- `git status --porcelain`：`target/`（含 `target/probe`）在 `.gitignore` 里，**不出现在列表**；
  出现在列表里的 `src/`、`webui/src/`、`docs/` 改动**全部属于前几轮与本轮计划内的文件**，
  没有探针文件漏进 `src/` 或 `webui/src/`（本轮的探针一律写在 `target/probe/` 下）。
- 本轮**未提交、未推送**，改动全部留在工作树。

#### ⑧ 第六轮仍未验证 / 没覆盖到的

- **两条路径的「剩余 75 个样例写法是否官方」仍没回查**：第五轮回查了 4 条，本轮没有再回查。
- **真实浏览器覆盖的是 14 组代表性样例 + 5 篇在库真实稿件**，不是全部 79 个样例。
  其余样例的浏览器侧结论**仍然是 jsdom 结论**（占比：79 个样例里 14 个有浏览器实拍，其余 65 个没有）。
- **`katex` 在真实稿件上的浏览器表现没有样本**（在库稿件全无公式，见 ⑤）。
- **`<p>` 包装造成的多行列表悬挂缩进差异**仍未逐一实测（第五轮 ⑥ 已列，本轮未动）。
- **上游 R2 的证伪实验清单**（`upstream-issues.md` §R2-附 ③）本轮未扩充；嵌套容器、属性引号变体、CRLF 仍未覆盖。
- **落库验证用的是 `[probe]` 探针文章（ID 42）而不是真实定时轮次**：本轮选了「真实 API + 真实渲染服务 +
  真实 MySQL + 真实 SPA」这条确定性路径（用户口径里「走真实轮次或真实 API 都行」）；
  「模型在自然轮次里会不会真去写标签式」这个问题，靠的是技能提示文本 + 保存自检，不是成品统计。

---

### 3.11 2026-09-13 第七轮：浏览器实拍覆盖到全部 79 个样例 + 公式 / 轮播的真实链路取证 + D41

第六轮留了三个口子（§3.10⑧）：浏览器只实拍了 14 组代表性样例，**其余 65 个样例的结论仍然是 jsdom 结论**；
`katex` 在真实稿件上没有样本；真实稿件回归只跑了 5 篇。本轮就是来关这三个口子的，
外加逐条核对 §3.10 与探针原始输出是否一致。

#### ① 79 个样例逐个上真实浏览器（不再挑样本）

同一套 CDP 客户端、同一台 Chrome/138.0.7204.100，**零新增依赖**。
一行一个样例，左栏是渲染服务自己的产物（参照），右栏是同一份产物经当前扩展集
`setContent → getHTML` 之后再渲染的结果；每行一张截图，表头带字数与「有盒子的元素数」。

```bash
(cd webui && npx vite build --config ../target/probe/browser/vite.config.mjs)   # 双入口：probe + probe_all
node target/probe/browser/run-all-browser.mjs      # 79 行挂载 + 逐行截图 + 图片等待
node target/probe/browser/summarize-all.mjs        # 逐行判定
```

**结论表 `target/probe/browser/all_summary.md`，逐行截图 `shots/all/<id>.png`（79 张），
原始量 `all_result.json`。汇总：pass 70 / na 9 / fail 0 / unverified 0。**

| 分类 | pass | na | fail | unverified | 合计 |
|---|---|---|---|---|---|
| standard | 12 | 0 | 0 | 0 | 12 |
| inline | 15 | 0 | 0 | 0 | 15 |
| callout | 4 | 0 | 0 | 0 | 4 |
| block-tag | 16 | 2 | 0 | 0 | 18 |
| container | 15 | 2 | 0 | 0 | 17 |
| math | 3 | 0 | 0 | 0 | 3 |
| diagram | 1 | 0 | 0 | 0 | 1 |
| ?registry-only | 1 | 5 | 0 | 0 | 6 |
| attribute | 3 | 0 | 0 | 0 | 3 |
| **合计** | **70** | **9** | **0** | **0** | **79** |

判定口径（写成代码，不靠人眼）：

| 判定 | 含义 |
|---|---|
| `pass` | 两栏归一化文字一致；参照侧有的结构特征（SVG / KaTeX / 渐变 / 圆点 / 上下标 / 代码高亮）编辑器侧一个不少；编辑器侧真有非零盒子 |
| `na` | **参照侧自己就没产出东西**：产物 0 字符，或把语法当正文吐了出来。编辑器只是忠实渲染一份已经坏掉的产物，没有「画得对不对」可判 |
| `fail` | 其余：可见文字变了 / 特征丢了 / 一个盒子都没有 |
| `unverified` | na 清单里记的现场与本轮实测对不上（不该出现；出现即说明结论过期） |

**9 条 `na` 的现场逐条当场复验**（每条都写成 `check(ref)`，复验不通过就掉进 `unverified` 而不是悄悄算成 `na`，
所以这份清单也是能被反证的）：

| 样例 | 写法 | 参照侧实测现场 | 编辑器侧实测 | 截图 |
|---|---|---|---|---|
| `ctn-success` | `:::success` | 可见文字里留着字面 `:::success`（容器没被识别，产物是兜底段落） | 文字 21 字 / 盒子 7/8 | [png](shots/all/ctn-success.png) |
| `ctn-danger` | `:::danger` | 同上（matrix 里本来就标注了 guide 说不在支持列表） | 文字 22 字 / 盒子 7/8 | [png](shots/all/ctn-danger.png) |
| `reg-layout-hero` | `:::layout-hero` | 可见文字里留着字面 `:::layout-hero`——**产物那 576 字符是兜底段落，不是渲染** | 文字 23 字 / 盒子 7/8 | [png](shots/all/reg-layout-hero.png) |
| `reg-layout-hero-tag` | `<layout-hero>` | HTML 里留着字面 `<layout-hero>` 元素——该 ID 在 bundle 里只有注册表条目，没有语法分支 | 文字 7 字 / 盒子 3/4 | [png](shots/all/reg-layout-hero-tag.png) |
| `reg-layout-toc` | `:::layout-toc` | 可见文字里留着字面 `:::layout-toc` | 文字 23 字 / 盒子 10/11 | [png](shots/all/reg-layout-toc.png) |
| `reg-layout-metrics` | `:::layout-metrics` | 可见文字里留着字面 `:::layout-metrics` | 文字 29 字 / 盒子 10/11 | [png](shots/all/reg-layout-metrics.png) |
| `reg-hint-tag` | `<hint>` | HTML 里留着字面 `<hint>` 元素——官方只有容器式 `:::hint`，没有标签式 | 文字 9 字 / 盒子 3/4 | [png](shots/all/reg-hint-tag.png) |
| `blk-slider-selfclose` | `<slider/>` | 字面 `<slider …>` 元素、0 个 `<svg>`、0 个非零盒子（上游 R4） | 文字 0 字 / 盒子 3/5 | [png](shots/all/blk-slider-selfclose.png) |
| `blk-case-flow-badline` | `<case-flow>` 行格式不合法 | 产物 0 字符；两次都是 `ok:true` + `warnings:[]` | 文字 0 字 / 盒子 1/2 | [png](shots/all/blk-case-flow-badline.png) |

**79 行逐条明细**（`可见文字` 与 `盒子` 两列都是 `参照→编辑器`；完整表含渐变 / 圆点 / 高亮 span
列，见 `target/probe/browser/all_summary.md`）：

| 样例 | 写法 | 判定 | 可见文字 参照→编辑器 | 盒子 参照→编辑器 | SVG | KaTeX |
|---|---|---|---|---|---|---|
| `md-heading` | `markdown` | pass | 16→16 | 3/3→4/5 | 0→0 | 0→0 |
| `md-list` | `markdown` | pass | 14→14 | 7/7→10/11 | 0→0 | 0→0 |
| `md-ordered` | `markdown` | pass | 16→16 | 7/7→10/11 | 0→0 | 0→0 |
| `md-task-list` | `markdown` | pass | 12→12 | 13/13→14/15 | 2→2 | 0→0 |
| `md-quote` | `markdown` | pass | 11→11 | 3/3→4/5 | 0→0 | 0→0 |
| `md-table` | `markdown` | pass | 25→25 | 13/13→21/22 | 0→0 | 0→0 |
| `md-hr` | `markdown` | pass | 6→6 | 5/5→6/7 | 0→0 | 0→0 |
| `md-code` | `markdown` | pass | 14→14 | 6/8→9/12 | 0→0 | 0→0 |
| `md-image` | `markdown` | pass | 0→0 | 0/2→1/4 | 0→0 | 0→0 |
| `md-image-sized` | `markdown` | pass | 0→0 | 0/2→1/4 | 0→0 | 0→0 |
| `md-link` | `markdown` | pass | 6→6 | 4/4→4/5 | 0→0 | 0→0 |
| `md-multi-image` | `markdown` | pass | 0→0 | 1/3→2/5 | 0→0 | 0→0 |
| `in-em-hl` | `==x==` | pass | 11→11 | 4/4→4/5 | 0→0 | 0→0 |
| `in-pill` | `!!x!!` | pass | 13→13 | 4/4→4/5 | 0→0 | 0→0 |
| `in-indigo` | `^^x^^` | pass | 12→12 | 4/4→4/5 | 0→0 | 0→0 |
| `in-glow` | `::x::` | pass | 13→13 | 4/4→4/5 | 0→0 | 0→0 |
| `in-bold` | `**x**` | pass | 13→13 | 4/4→4/5 | 0→0 | 0→0 |
| `in-italic` | `*x*` | pass | 13→13 | 4/4→4/5 | 0→0 | 0→0 |
| `in-bold-italic` | `***x***` | pass | 11→11 | 6/6→5/6 | 0→0 | 0→0 |
| `in-underline-accent` | `__x__` | pass | 17→17 | 4/4→5/6 | 0→0 | 0→0 |
| `in-underline-html` | `<u>x</u>` | pass | 16→16 | 3/3→4/5 | 0→0 | 0→0 |
| `in-strike` | `~~x~~` | pass | 16→16 | 4/4→4/5 | 0→0 | 0→0 |
| `in-sub` | `~x~` | pass | 15→15 | 4/4→4/5 | 0→0 | 0→0 |
| `in-sup` | `^x^` | pass | 13→13 | 4/4→4/5 | 0→0 | 0→0 |
| `in-code` | `` `x` `` | pass | 16→16 | 3/3→4/5 | 0→0 | 0→0 |
| `in-badge` | `<Badge/>` | pass | 14→14 | 4/4→4/5 | 0→0 | 0→0 |
| `in-icon` | `<Icon/>` | pass | 11→11 | 3/3→5/6 | 0→0 | 0→0 |
| `callout-quote-tip` | `> [TIP]` | pass | 21→21 | 3/3→5/6 | 0→0 | 0→0 |
| `callout-quote-warning` | `> [WARNING]` | pass | 17→17 | 3/3→5/6 | 0→0 | 0→0 |
| `callout-container` | `:::callout` | pass | 20→20 | 3/3→5/6 | 0→0 | 0→0 |
| `callout-tip-container` | `:::tip` | pass | 18→18 | 5/5→6/7 | 0→0 | 0→0 |
| `blk-title` | `<title>` | pass | 40→40 | 31/31→26/27 | 0→0 | 0→0 |
| `blk-p-title` | `<p-title>` | pass | 25→25 | 14/14→12/13 | 0→0 | 0→0 |
| `blk-cta` | `<cta>` | pass | 31→31 | 7/7→6/7 | 0→0 | 0→0 |
| `blk-badges` | `<badges>` | pass | 18→18 | 7/7→6/7 | 0→0 | 0→0 |
| `blk-statement` | `<statement>` | pass | 13→13 | 2/2→3/4 | 0→0 | 0→0 |
| `blk-lead` | `<lead>` | pass | 13→13 | 2/2→3/4 | 0→0 | 0→0 |
| `blk-engage-label` | `<engage-label>` | pass | 26→26 | 17/17→16/19 | 3→3 | 0→0 |
| `blk-engage-card` | `<engage-card>` | pass | 50→50 | 33/33→45/52 | 3→3 | 0→0 |
| `blk-engage-tag` | `<engage>` | pass | 54→54 | 33/33→45/52 | 3→3 | 0→0 |
| `blk-img` | `<img>` | pass | 0→0 | 1/2→2/4 | 0→0 | 0→0 |
| `blk-steps-2` | `<steps>` | pass | 42→42 | 23/23→20/21 | 0→0 | 0→0 |
| `blk-steps-5` | `<steps>` 5 步 | pass | 61→61 | 55/55→44/45 | 0→0 | 0→0 |
| `blk-timeline` | `<timeline>` | pass | 22→22 | 19/19→20/21 | 0→0 | 0→0 |
| `blk-slider` | `<slider>` | pass | 0→0 | 8/9→9/12 | **1→1** | 0→0 |
| `blk-case-flow` | `<case-flow>` | pass | 28→28 | 7/7→10/11 | 0→0 | 0→0 |
| `blk-gov-header` | `<gov-header>` | pass | 30→30 | 8/8→9/11 | 0→0 | 0→0 |
| `ctn-breaking` | `:::breaking` | pass | 45→45 | 13/13→12/13 | 0→0 | 0→0 |
| `ctn-reading-path` | `:::reading-path` | pass | 34→34 | 21/21→23/24 | 0→0 | 0→0 |
| `ctn-steps-h` | `:::steps-horizontal` | pass | 33→33 | 25/25→21/22 | 0→0 | 0→0 |
| `ctn-steps-v` | `:::steps-vertical` | pass | 37→37 | 35/35→28/29 | 0→0 | 0→0 |
| `ctn-case-flow` | `:::case-flow` | pass | 26→26 | 7/7→10/11 | 0→0 | 0→0 |
| `ctn-timeline` | `:::timeline` | pass | 22→22 | 19/19→20/21 | 0→0 | 0→0 |
| `ctn-slider` | `:::slider` | pass | 0→0 | 8/9→9/12 | **1→1** | 0→0 |
| `ctn-table` | `:::table` | pass | 14→14 | 11/11→19/20 | 0→0 | 0→0 |
| `ctn-compare` | `:::compare` | pass | 11→11 | 9/9→10/11 | 0→0 | 0→0 |
| `ctn-code-block` | `:::code-block` | pass | 20→20 | 9/9→11/12 | 0→0 | 0→0 |
| `ctn-align` | `:::align` | pass | 11→11 | 1/1→3/4 | 0→0 | 0→0 |
| `ctn-hint` | `:::hint` | pass | 14→14 | 3/3→5/6 | 0→0 | 0→0 |
| `ctn-cta` | `:::cta` | pass | 10→10 | 5/5→4/5 | 0→0 | 0→0 |
| `ctn-lead` | `:::lead` | pass | 13→13 | 2/2→3/4 | 0→0 | 0→0 |
| `ctn-note` | `:::note` | pass | 20→20 | 5/5→6/7 | 0→0 | 0→0 |
| `ctn-success` | `:::success` | na | 21→21 | 6/6→7/8 | 0→0 | 0→0 |
| `ctn-danger` | `:::danger` | na | 22→22 | 6/6→7/8 | 0→0 | 0→0 |
| `math-inline` | `$...$` | pass | 23→23 | 35/51→36/53 | 0→0 | **2→2** |
| `math-block` | `$$...$$` | pass | 12→12 | 44/68→48/75 | 0→0 | **1→1** |
| `math-block-label` | `$$...$$` 带标记 | pass | 18→18 | 33/45→37/52 | 0→0 | **1→1** |
| `diagram-mermaid` | mermaid 代码块 | pass | 38→38 | 8/8→9/10 | 0→0 | 0→0 |
| `reg-layout-hero` | `:::layout-hero` | na | 23→23 | 6/6→7/8 | 0→0 | 0→0 |
| `reg-layout-hero-tag` | `<layout-hero>` | na | 7→7 | 3/3→3/4 | 0→0 | 0→0 |
| `reg-layout-toc` | `:::layout-toc` | na | 23→23 | 8/8→10/11 | 0→0 | 0→0 |
| `reg-layout-metrics` | `:::layout-metrics` | na | 29→29 | 8/8→10/11 | 0→0 | 0→0 |
| `reg-hint-tag` | `<hint>` | na | 9→9 | 3/3→3/4 | 0→0 | 0→0 |
| `reg-align-tag` | `<align>` | pass | 10→10 | 1/1→3/4 | 0→0 | 0→0 |
| `attr-table-title` | `:::table title=` | pass | 14→14 | 11/11→19/20 | 0→0 | 0→0 |
| `attr-callout-title` | `:::callout title=` | pass | 21→21 | 3/3→5/6 | 0→0 | 0→0 |
| `attr-compare-marker-cn` | `:::compare` 中文标记 | pass | 12→12 | 9/9→10/11 | 0→0 | 0→0 |
| `blk-slider-selfclose` | `<slider/>` | na | 0→0 | 0/3→3/5 | 0→0 | 0→0 |
| `blk-case-flow-badline` | `<case-flow>` 行格式不合法 | na | 0→0 | 0/0→1/2 | 0→0 | 0→0 |

**「有元素」不等于「画出来了」——公式与轮播单独再量一次高度**（等 webfont 就绪之后）：

| 样例 | KaTeX 高度 参照→编辑器 | svg `foreignObject` 参照→编辑器 | svg `animateTransform` 参照→编辑器 |
|---|---|---|---|
| `math-inline` | [22,22]→[22,22] | 0→0 | 0→0 |
| `math-block` | [45]→[45] | 0→0 | 0→0 |
| `math-block-label` | [23]→[23] | 0→0 | 0→0 |
| `blk-slider` | — | **3→3** | **1→1** |
| `ctn-slider` | — | **3→3** | **1→1** |
| `blk-slider-selfclose` | — | 0→0 | 0→0（上游 R4，见 `na` 表） |

**图片统计专门修过一次口径**：第一版数 `document.images`，把 11 个
`<img class="ProseMirror-separator">`（ProseMirror 给空行内内容插的光标占位、**没有 `src`**）
算成了「图没加载」。现在只统计**有 `src` 属性的**：共 24 张，成功 24，失败 0（横幅图实测 1080×784 已解码）。

**KaTeX webfont 也修过一次时序**：挂载前 `document.fonts.check('16px KaTeX_Main')` 恒为 `false`
（那一刻页面上还没有公式，浏览器根本不会去下载 webfont），所以现在**挂载之后再量**：
挂载前 `false` → 量高度时 **`true`**。同样的时序坑在真实稿件那份脚本里也修了（见 ③）。

**另外两个踩过的坑**（注释都留在探针代码里）：`Page.captureScreenshot` 必须带
`captureBeyondViewport: true`，否则视口外的行拍出来是一张空白页（10 张截图字节数完全一样）；
79 行里同一张公网图会被请求几十次，不加等待/重试会把「网络抖动」记成「组件没渲染」。

#### ② 公式端到端（真实文章 43：后端 → 数据库 → 编辑器）

前六轮**公式只在合成样例上实拍过**，真实稿件里一篇都没有（§3.10⑤ 明写了这一点）。本轮补上：
用 `target/probe/round7_e2e.py` 造了一篇真文章（ID 43，标题带 `[probe]` 前缀，可随时删），
Markdown 里同时有行内公式与块级公式，走**真实渲染服务 + 真实 MySQL**。

| 环节 | 实测 |
|---|---|
| 后端落库 HTML | `CONTENT_HTML` **10280 字符**，含 `class="katex"` ×5、`katex-display` ×2、`katex-html` ×5 |
| 源码残留 | HTML 里 `$` / `\frac` **0 处**——说明确实被渲染成公式结构，而不是原样透传 |
| 接口一致性 | `GET /api/articles/43` 返回的 HTML 长度与直查 MySQL **逐字节等长** |
| 编辑器（真实 SPA） | `.katex` **5 个、5 个可见**，高度 **[22,22,22,45,53]**（3 行内 + 2 块级），`.katex-display` 2 个 |
| 编辑器里的源码残留 | `katexSourceLeak: false`（可见文字里没有 `$$` / `\frac` / `\begin{` / `\sum_`） |
| webfont | 等 `document.fonts.ready` 之后 `document.fonts.check('16px KaTeX_Main')` = **true**，高度是等完之后量的 |

证伪方式：把同一个输入喂回渲染服务，产物里 `katex` 出现次数若为 0 就说明「公式是前端自己画的」，
实测不是——**公式结构在后端产物里就有了**。截图 `shots/articles/43.png`（`ready=true`、正文可见）。

#### ③ 轮播端到端（真实文章 44：容器式 `:::slider` → 数据库 → 编辑器）

同事点名要先查「库里那 9 个 `<svg>` 里有没有真的轮播产物」——**查过了，没有**：

```sql
SELECT ID, (LENGTH(CONTENT_HTML)-LENGTH(REPLACE(CONTENT_HTML,'<svg','')))/4 AS svg,
       (LENGTH(CONTENT_HTML)-LENGTH(REPLACE(CONTENT_HTML,'animateTransform','')))/16 AS anim
FROM ARTICLE WHERE DELETED=0;
```

结果：文章 12 / 15 / 17–23 / 25–40 每篇恰好 3 个 `<svg>`、`animateTransform` 与 `foreignObject` **各 0 个**——
那是文章 30 里那种 engage-card 图标路径（`<svg viewBox="0 0 24 24" width="28" height="28">…fill="currentColor"`）。
**只有第六轮那两篇 `[probe]` 文章（41/42）才有真轮播**。所以本轮另造一篇走完整链路：

| 环节 | 实测 |
|---|---|
| 输入 | 容器式 `:::slider images="…, …" interval="3" width="600" height="200" type="1"` |
| 后端产物 / 数据库 | `CONTENT_HTML` **1419 字符**，`<svg>` ×1、`<animateTransform>` ×1、`<foreignObject>` ×3、`viewBox="0 0 600 200"`，字面 `:::slider` **0 处** |
| 编辑器（真实 SPA） | `<svg>` **1 个、可见、盒子 600×200**；`animateTransform` ×1、`foreignObject` ×3 |
| 轮播内的图 | 3 张内嵌 `<img>`，**盒子 600×436、`naturalWidth` 1080×784、3/3 已解码**——不是空盒子也不是静态占位 |
| 动画 | `<animateTransform>` 真的在 DOM 里（轮播的位移是靠它，不是 CSS 假动效） |

**这里也修过一个时序坑**：轮播里的图 `naturalWidth` 只有解码后才知道，第一次跑量到的是
`[0,0]`——现在脚本会在资源等完之后**再量一次**。截图 `shots/articles/44.png`。

#### ④ 真实稿件回归：13 篇（原 5 篇 + 本轮新增 6 篇 + 软删 1 篇 + 2 篇新产）

```bash
node target/probe/browser/run-article.mjs     # 产物 articles_result.json、shots/articles/<id>.png
```

| 稿件 | 题材 / 篇幅 | API | ready | 可见文字 | `<svg>` 可见/总数 | 盒子 | `class="` |
|---|---|---|---|---|---|---|---|
| 43 | 公式（新产） | 200 | true | 81 | 0/0 | — | 206 |
| 44 | 轮播（新产） | 200 | true | 13 | **1/1** | **600×200** | 3 |
| 24 | 在库（第六轮已拍） | 200 | true | 2150 | 0/0 | — | 6 |
| 30 | 在库（第六轮已拍） | 200 | true | 4965 | **3/3** | 28×28 / 32×32 / 28×28 | 4 |
| 35 | 在库（第六轮已拍） | 200 | true | 3777 | **3/3** | 28×28 / 32×32 / 28×28 | 10 |
| 38 | 在库（第六轮已拍） | 200 | true | 2150 | 0/0 | — | 6 |
| 40 | 在库（第六轮已拍） | 200 | true | 5518 | **3/3** | 24×24 / 28×28 / 24×24 | 12 |
| 3 | 短稿（Markdown 421 字符） | 200 | true | 281 | 0/0 | — | 6 |
| 11 | 长稿（4423 字符） | 200 | true | 2248 | 0/0 | — | 54 |
| 13 | 最长稿（9142 字符） | 200 | true | 2279 | 0/0 | — | 143 |
| 16 | 短稿（3909 字符） | 200 | true | 2402 | 0/0 | — | 1 |
| 37 | 长稿（7717 字符） | 200 | true | 5019 | **3/3** | 24×24 / 28×28 / 24×24 | 15 |
| 12 | 含图标圈 | 200 | true | 2542 | **3/3** | 28×28 / 32×32 / 28×28 | 11 |
| 5 | **软删稿（`DELETED=1`）** | **404** | **false** | — | — | — | — |

**24 / 30 / 35 / 38 / 40 这五篇是本轮复跑，五个数字（可见文字、`<svg>` 可见数、盒子、`class="` 计数）
与 §3.10⑤ 那张表逐格一致**——同一套量法、同一轮浏览器，第六轮的结论没有被推翻。

**软删稿件（文章 5）终于有了正确表现**：API 返回 404 `文章不存在`；编辑器页面
`openError: true`、`hasSpinner: false`、可见文字就是 `文章不存在 返回文章列表`——
**给得出清楚的提示，不是白屏也不是一直转圈**。这条是靠下面这个缺陷修出来的。

#### ⑤ 本轮新发现并修掉的缺陷：D41（软删稿件在编辑器里永远转圈）

**发现方式不是读代码，是量出来的**：对文章 5 跑真实 SPA，实测
`ready=false`、页面文字是「正在打开文章…」、`hasSpinner=true`、**一个错误提示都没有**，
而同一个 ID 的接口返回 404 + `文章不存在`。

根因：`ArticleEditorView.vue` 的 `load()` 失败时只把消息写进 `error`，模板末尾却是一个**无条件**的
「正在打开文章…」转圈块——没有 `v-else-if="error"` 分支，于是失败态被加载态盖住，
用户看到的是**一个永远不会结束的转圈**。软删稿只是最容易撞上的一种（网络断、没权限同样会撞）。

- **红**：`run-article.mjs` 对文章 5 实测 `openError=false` / `hasSpinner=true` / `bodyText="正在打开文章…"`。
- **绿**：`ArticleEditorView.vue` 加 `v-else-if="error"` 错误态（带 `CircleAlert` 图标、
  显示 `error` 原文、给一个「返回文章列表」按钮），`style.css` 补 `.page-loading.open-error{color:#a9434d}`。
  重跑同一脚本：`openError=true` / `hasSpinner=false` / `bodyText="文章不存在 返回文章列表"`。
- 这是**本轮唯一改动业务代码的地方**，就是 §六 里三个真实缺陷的第三个（D30–D41）。

#### ⑥ 与 §3.10 的交叉核对（用探针原始输出逐项比对，不是靠回忆）

| 被核对的数字 | §3.10 里的说法 | 探针原始输出 | 结论 |
|---|---|---|---|
| 样例数 | 79 | `component_matrix.json` 79 行、`components/*.md` 79 个、`all_result.json` 79 条、`shots/all/*.png` 79 张，四者 ID 集合**完全相同** | 一致 |
| 14 组浏览器实拍 | 14/14 两侧通过 | `browser_result.json` 14 组 | 一致 |
| 19 行有损分类 | 12 / 4 / 1 / 2 | `round6_lossy_audit.json` 19 条，`mechanism` 分布 `normalized-marker-span` 12、`normalized-tag-swap` 4、`upstream-r4` 1、`upstream-r2` 2，19 条 `satisfies` 全为 true | 一致 |
| 真实稿件 5 篇的数字 | 24/30/35/38/40 | 本轮复跑，五篇逐格相同（见 ④） | 一致 |
| 上一轮「公式在真实稿件上没样本」 | §3.10⑤ 明写 | 本轮造了文章 43 补上（见 ②） | 已闭合 |

**一处口径需要说明（不是矛盾）**：§3.10⑤ 那列 `class="` 数的是**实时 DOM**，
比 `getHTML()` 口径多出 TipTap 给表格加的 `tableWrapper` 包装，所以同一篇在两处的数不一样，
**两边不能直接相减**——本轮沿用实时 DOM 口径，与 §3.10⑤ 逐格可比。

#### ⑦ 收尾数字

- 全量测试：`./.mvn/mvn-local.sh -o test` → **Tests run: 296, Failures: 0, Errors: 0, Skipped: 0，BUILD SUCCESS**。
  与第六轮同为 296（D41 是纯前端修复，没有 Java 用例）。日志 `target/probe/round7_tests.log`。
  输出里那几行 `ERROR … StageTimeout` 是 `StageTimeoutTest` **自己**在验超时路径，不是失败。
- 前端构建：`cd webui && npm run build` → **✓ built in 433ms**（`ArticleEditorView` chunk 542.03 kB，
  有一条 chunk > 500 kB 的既有告警，与本次改动无关）。
- 本轮**未提交、未推送**，改动全部留在工作树；探针一律写在 `target/probe/`（该目录在 `.gitignore` 里）。

#### ⑧ 第七轮仍未闭合 / 没覆盖到的

按「距离『所有组件都能正确渲染』还差什么」逐条写：

1. **9 条样例永远不会有「编辑器渲染结论」**，因为**上游自己就没实现**（见 `na` 表）：
   `ctn-success` / `ctn-danger` / `reg-layout-toc` / `reg-layout-metrics` / `reg-layout-hero`
   连容器都没识别（产物是兜底段落，字面 `:::xxx` 留在可见文字里）；
   `reg-hint-tag` / `reg-layout-hero-tag` 只有注册表条目没有语法分支；
   `blk-slider-selfclose` 是 R4；`blk-case-flow-badline` 整块归零。
   **这 9 条要变成 pass，只能等上游**，不是本项目前端能修的。
2. **轮播只在 2 个合成样例 + 2 篇 `[probe]` 真文章上验过**：库里**现存**的文章一篇真轮播都没有
   （那 9 个 `<svg>` 全是 engage-card 图标）。也就是说「模型在自然轮次里会不会真去写 `:::slider`」
   这个问题，仍然只有技能提示 + 保存自检 + 探针文章作证，**没有成品统计**。
3. **公式同理**：真实稿件里只有本轮造的探针文章 43，自然轮次产出过公式的稿件一篇都没有。
4. **组件「特定条件下才渲染」的路径没系统覆盖**：本轮每行只喂了**一个**最小样例。
   同一组件在属性组合、嵌套容器、超长内容下的表现（比如 `:::table` 带 `title` 又在 `:::case-flow` 里、
   5 步以上的 `<steps>`）没有逐个实测。第五轮列的两条也仍然挂着：
   `<p>` 包装造成的多行列表悬挂缩进差异、上游 R2 证伪实验清单（嵌套容器 / 属性引号变体 / CRLF）。
5. **两篇在库稿件引用的图挂了，与本项目代码无关但会显示成空白**：文章 16 和 35 的 `<img src>`
   指向 `https://robocopmao.github.io/…`，`curl` 实测 **404**（返回的是 GitHub Pages 的 404 页，9379 字节），
   所以编辑器里 `images loaded` 只有 1 —— 这是**历史内容里烧死的绝对 URL**，
   换域名/丢仓库就会这样，前端和渲染服务都救不回来。要么改内容、要么做图片本地化。
6. **`<p>` 包裹**：本轮未动（见 ④）。
7. **两条路径的「剩余 75 个样例写法是否官方」仍没回查**（第五轮回查了 4 条）。

**一句话验收口径**：79 个样例在真实浏览器里 **70 个确认渲染正确、9 个确认是上游没实现（不是本项目的问题）、0 个渲染失败**；
真实稿件侧的公式与轮播**都已走通后端 → 数据库 → 编辑器全链路**；
距离「所有组件都能正确渲染」剩下的差距**全在第 1、4 条（上游实现 + 组合条件下的路径覆盖）**，
不在本项目的编辑器渲染链路上。

### 3.12 2026-09-13 第八轮：组合条件覆盖（17 例）/ 9 条 `na` 的「等上游」清单与技能核对 / 外部失效资源盘点 / 绝对 URL 归一修复

本轮打的就是 §3.11⑧ 自己列的「仍未闭合」——第 1、3、4、5、6 条。所有产物在 `target/probe/`（gitignore）。
**本轮没有 commit、没有 push，也没有改生产数据**；除 ⑤ 的代码修复外，全部是只读测量。

#### ① 先补第七轮没写全的两处结论（公式 / 轮播，真实稿件）

这两条第七轮已经跑完并写进 §3.11②③，本轮复核数字无变化，照单回填以便本节自洽：

| 项 | 真实稿件 | 后端产物（真实渲染 API → 落库 `CONTENT_HTML`） | 编辑器（真实 SPA，Chrome 138） | 判定 |
|----|---------|------------------------------------------|---------------------------|------|
| 公式 | 文章 43 | 落库 HTML 里就有 KaTeX 结构（`<math>`/`.katex` 配套标记，不是原样文本） | 5 个 `.katex`，高度 `[22,22,22,45,53]`；`$$` 源码残留 0 处、字面 `\frac` 0 处 | **渲染** |
| 轮播 | 文章 44 | 容器式 `:::slider` → `<svg>` 600×200 + `foreignObject`×3 + `animateTransform`×1 | SVG 在 DOM 里；3 张图 `naturalWidth` 1080×784（真图、非占位） | **渲染** |

口径：两篇都不是样例、不是 jsdom，是**真实令牌 + 真实渲染 API + 真实 MySQL + 真实浏览器**四级链路。
逐字节证据见 `target/probe/round7_e2e.json` / `.md` 与 `target/probe/browser/articles_result.json`。

#### ② 组合条件覆盖：17 例，两级判定

针对 §3.11⑧ 第 4 条（「每行只喂了一个最小样例」）补的。用例脚本 `target/probe/round8_combos.py`，
浏览器侧 `target/probe/browser/run-combo-browser.mjs`，判定 `summarize-combos.mjs`。
产物：`target/probe/browser/combo_summary.md` / `combo_result.json` / `shots/combo/*.png`（17 张，左右两栏同框）。

**两级判定必须分开读**——上游（后端产物）判「有没有画出来」，编辑器（本项目）判「拿到的产物有没有画错」。
合成一个「pass」会把上游的锅盖到编辑器头上。

**上游 ok 8 / nested-unsupported 8 / silently-lost 1；编辑器 pass 17 / fail 0。**

> ⚠️ **判据修正（本节收尾时才发现的，原表已按修正后重判）**：第一版把「泄漏」判成正则 `:::[a-z-]+`，
> **漏掉了裸的收尾 `:::`** —— `cmb-timeline-table` 与 `cmb-caseflow-table-title` 的可见文字结尾
> 就是一个光秃秃的 `:::`，被算成「0 处泄漏」、错落进 `silently-lost`。正确分类后
> `nested-unsupported` 由 6 变 8、`silently-lost` 由 3 变 1。`combo_summary.md` 已同步重生成。
> **这条修正本身就是一次「产物非空 ≠ 渲染了」的复发**：缺陷在产物里明明白白写着，是判据睡着了。

| 用例 | 家族 | 上游判定 | 上游产物特征 | 与「单独最小写法」的差异 | 编辑器 |
|------|------|---------|------------|---------------------|-------|
| `cmb-callout-timeline` | 容器套容器 | **nested-unsupported** | 843 字符 / `:::` 残留 2 处 | 比 `ctn-timeline` 少 986 字符；内层 timeline 被当普通段落透传 | pass |
| `cmb-callout-steps` | 容器套容器 | **silently-lost** | 485 字符 / `:::` 残留 0 处 | 比 `blk-steps-2` 少 **990** 字符：**三个步骤的标题（`第一步`/`第二步`/`第三步`）在产物里找不到**（`<step>` 的 `title` 属性没被消费），`<steps>`/`<step>` 作为**未知元素原样透传**（`tagHistogram` 里 `steps:1` `step:3`）。步骤**正文**文字（`收集素材与选题` …）还在，只是变成没有版式的裸文本 | pass |
| `cmb-timeline-table` | 容器套容器 | **nested-unsupported** | 4830 字符 / `:::` 残留 1 处 | 比 `ctn-timeline` 多 3001 字符：`:::table` **没有渲染成 `<table>`**（`tagHistogram` 里没有 table），表格行被摊成时间线条目，**收尾的 `:::` 留在了可见文字里**（R2-附 记过的同一条） | pass |
| `cmb-compare-in-callout` | 容器套容器 | **nested-unsupported** | 813 字符 / `:::` 残留 2 处 | 比 `ctn-compare` 少 391 字符 | pass |
| `cmb-caseflow-table-title` | 属性交织 | **nested-unsupported** | 1287 字符 / `:::` 残留 1 处 | 内层 `:::table title=` **未渲染**（无 `<table>`，`收录`/`1200` 两处文字缺），**收尾 `:::` 留在可见文字里**；+255 字符只是 case-flow 卡片本身 | pass |
| `cmb-callout-title-in-steps` | 属性交织 | **nested-unsupported** | 2957 字符 / `:::` 残留 2 处 | 比 `blk-steps-2` 多 1482 字符（外层被透传） | pass |
| `cmb-table-title-in-callout` | 属性交织 | **nested-unsupported** | 614 字符 / `:::` 残留 2 处 | 比 `attr-table-title` 少 382 字符：两个 `title` 不互吞，但内层整块没渲染 | pass |
| `cmb-breaking-table` | 属性交织 | **nested-unsupported** | 727 字符 / `:::` 残留 2 处 | 比 `ctn-breaking` 少 543 字符 | pass |
| `cmb-slider-twice` | 同组件多次 | **ok** | 2773 字符 / **svg 2** | 比 `ctn-slider` 多 1632 字符，svg 1→2：**两个轮播各自独立，各 600×200、各带 `animateTransform` 与 3 张图** | pass |
| `cmb-engage-twice` | 同组件多次 | **ok** | 7957 字符 / **svg 6** | 比 `blk-engage-card` 多 4071 字符，svg 3→6：两份图标圈都在 | pass |
| `cmb-timeline-twice` | 同组件多次 | **ok** | 4687 字符 / `:::` 残留 0 处 | 两条独立时间线，条目数 3+2 与输入一致（不串行、不合并） | pass |
| `cmb-callout-thrice` | 同组件多次 | **ok** | 1035 字符 / `:::` 残留 0 处 | 三个独立容器，三份标题文字都在 | pass |
| `cmb-long-section` | 超长内容 | **ok** | 19207 字符 / 文字 2309 | 单节约 3 千字**不截断**；12 个 KaTeX 一个不少、行内组件仍在 | pass |
| `cmb-long-table` | 超长内容 | **ok** | 13309 字符 / 文字 273 | 20 行 `:::table`（带标题）**一行不丢**，表头与标题都在 | pass |
| `cmb-inline-in-container` | 行内混排 | **ok** | 1262 字符 / `:::` 残留 0 处 | 容器版式在，粗体/高亮/下标/上标/徽章/链接的标记**没有被容器吞掉**（注：本例的 `<Badge>新</Badge>` 是**探针自己写的非官方形态**，官方徽章是自闭合 `<badge … />`；它同样被原样透传，但不计为上游缺陷——见下方判据边界） | pass |
| `cmb-math-in-container` | 行内混排 | **ok** | 7766 字符 / `:::` 残留 0 处 | 4 个 KaTeX 在容器里仍然渲染成公式，不是原样文本 | pass |
| `cmb-slider-in-callout` | 行内混排 | **nested-unsupported** | 895 字符 / `:::` 残留 2 处 / **svg 0** | 比 `ctn-slider` 少 246 字符且 **svg 1→0**：外层 callout 在，内层轮播整块没画出来 | pass |

三条结论：

1. **「容器套容器」是上游的结构性不支持，不是概率性失败**：8 例全部命中，7 例在产物里留着字面 `:::`，
   1 例（`cmb-callout-steps`）不留字面但把 `steps` 组件整块降级成裸文本。**两种失败形态都要防**——
   `MarkFlowRenderService.detectLeakedSyntax` 只认第一种，第二种要靠逐字比对才看得出来（见第 3 条）。
2. **「同一组件出现两次以上」不退化**：`slider`／`engage-card`／`timeline`／`callout` 四例全 ok，
   包括重 SVG 组件的 svg 计数精确翻倍（1→2、3→6）。这条把 §3.11⑧ 第 2 条「轮播只在 2 个样例上验过」
   的口径补厚了一层：**重复出现不会把 SVG 挤掉**。
3. **编辑器侧 0 缺陷**：17 例逐例比对「参照侧（同一份后端产物直接塞进 iframe）→ 编辑器侧」，
   可见文字**逐字一致**、结构特征一个不少、盒子全非零。**本轮组合用例暴露的缺陷 100% 在上游。**

> ⚠️ **教训（本轮第二次踩）：`pass` 不等于「渲染正确」，产物非空更不等于渲染了。**
> `cmb-callout-steps` 的产物 485 字符**看起来完全正常**——外层 callout 的边框、配色、标题都在，
> 编辑器判定也确实是 `pass`。但内层 `<steps>` / `<step>` 是**当未知元素原样透传**的，
> 三个步骤的标题（`第一步`/`第二步`/`第三步`）**在产物里一个字都找不到**，编号圆点与连线全无。
> 只看「有没有样式」「产物长不为 0」「编辑器没报错」这三样，这一例会被读成绿的。
> 判据必须是**与最小写法逐字对齐**（本例：990 字符差 + 三个标题逐个点名），
> 而不是「和参照侧一致」——参照侧用的就是同一份残缺产物，自己跟自己比当然一致。
> 这与 §4.2 第 3 条（结论要核对结构）是同一条教训的第二次发作，写进验收原则。
>
> ⚠️ **判据边界（仍未覆盖）**：**元素形态的透传在可见文字里完全看不出来**。
> `<steps>` / `<step>` 留在 DOM 里时文字一个不少、`:::` 一处没有，只有 `tagHistogram` 里
> 多出组件名才暴露——`cmb-callout-steps` 就因为这一条还留在 `silently-lost` 里
> （严格说它也是「语法没被消费」，只是残留形态是元素不是文本）。
> 同批还查出 `cmb-inline-in-container` 的 `<Badge>新</Badge>` 也是原样透传，
> 但那是**探针自己写错的形态**（官方是自闭合 `<badge … />`），按 R4-附 的教训不计为上游缺陷。

#### ③ 9 条 `na` 的「等上游」清单（**不改判据凑 pass**）

`na` 的判定口径与逐条复验理由在 `target/probe/browser/all_summary.md`，本轮**一条都没有改判**。
本节做的是另一件事：确认**技能提示与保存自检已经能让模型绕开这 9 种写法**。

判据来源：`target/probe/round8_na_discriminator.py` → `round8_na_discriminator.txt`（16 个用例，
直接打真实渲染 API，泄漏检测扫**原始产物 HTML**——先剥标签会把「标签被当未知元素原样透传」这种情况洗掉
（那是第七轮踩过的坑）。第十轮复核更正：标签式写法在上游的表现是**元素形态的透传**，
`<p style="…"><layout-hero>子文本</layout-hero></p>`——可见文字里只剩子文本、看不见标签名，
只有扫**原始产物 HTML** 才留得住证据；容器式则是可见文字里留着字面 `:::`。
`round8_unknown_tags.py` → `round8_unknown_tags.txt`（`layout-*` 家族 32 组全量）；
`round8_caseflow_bullet.py` → `round8_caseflow_bullet.txt`（7 种列表符号 × 容器式/标签式）；
`round8_callout_types.py` → `round8_callout_types.txt`（`:::callout type=` 取值逐个打）。

##### 逐条清单

| # | 样例 id | 写的是什么 | 上游实测证据 | 若上游修复，本项目要做的验证动作 |
|---|--------|-----------|------------|---------------------------|
| 1 | `ctn-success` | 容器式 `:::success` | 产物 570 字符，可见文字**就是** `:::success标题正文:::`，开收两行都留；`warnings` 空 | 把该样例重跑一遍 `run-all-browser.mjs`，确认 `leak=0` 且出现提示框 `border-left:4px` 结构 |
| 2 | `ctn-danger` | 容器式 `:::danger` | 产物 569 字符，可见文字 `:::danger标题正文:::`；`warnings` 空 | 同上，另把 `:::callout type="danger"` 的**并存关系**再测一次（若上游两种都支持，技能文本要说明取舍） |
| 3 | `reg-layout-hero` | 容器式 `:::layout-hero` | 产物 576 字符，可见文字 `:::layout-heroZQLH内容:::`；32 组 `layout-*` 全量实测**无一例外** | 重跑 `round8_unknown_tags.py` 32 组，全部 `leak=False`；再决定技能里那条「禁写名单」是删掉还是改成「已支持」 |
| 4 | `reg-layout-hero-tag` | 标签式 `<layout-hero>` | 产物 219 字符：`<p …><layout-hero>ZQLHT 内容</layout-hero></p>` —— **标签被当未知元素原样透传**（不是把标签名当文字输出：可见文字只有子文本「内容」，元素本身在屏幕上不可见，要扫**原始产物 HTML** 才看得见） | 同 #3，且要单独确认标签式与容器式**是否同批实现**（历史上两者常不同步） |
| 5 | `reg-layout-toc` | 容器式 `:::layout-toc` | 产物 751 字符，可见文字 `:::layout-tocZQLT1\|甲:::` | 同 #3 |
| 6 | `reg-layout-metrics` | 容器式 `:::layout-metrics` | 产物 759 字符，可见文字 `:::layout-metricsZQLM1\|甲\|1:::` | 同 #3 |
| 7 | `reg-hint-tag` | 标签式 `<hint>` | 产物 206 字符，字面 `<hint>` 留在正文；对照 `:::hint` 容器式 349 字符**正常渲染**（蓝色信息卡，等同 `:::info`） | 重跑 `round8_unknown_tags.py` 的 `tag:hint`，确认 `leak=False`；同时看 `:::hint` 与 `:::info` 是否合并成一个（若合并，技能里「第 7 个只有容器形式的名称」要去掉） |
| 8 | `blk-slider-selfclose` | 自闭合 `<slider … />` | 产物 303 字符、**文字 0、svg 0**，字面 `<slider` 留在正文；对照标签式「开+闭」（单图）214 字符、svg 0、**无字面残留**（渲染成一张普通 `<img>`） | 重跑 `round8_na_discriminator.py` 的 `na-slider-selfclose`，确认 svg ≥ 1；注意这是**唯一一条「技能提示方向写反」**的样例，修好后技能里那条警告要改成「自闭合也行」；单图场景下本来就该退化成普通 `<img>`（`<svg>` 计数为 0 是正确的），判据看**有没有字面 `<slider` 残留**而不是看 `<svg>` 个数 |
| 9 | `blk-case-flow-badline` | `case-flow` 行首漏 `-`（或漏 `[标签]`） | 产物 **0 字符**、`ok:true`、`warnings:[]`——**静默归零**；7 种列表符号实测：只有行首 `-` + `[标签]` 有产出，`*`/`+`/`1.`/裸行/漏标签**全部 0 字符**（容器式与标签式结果逐项相同） | 重跑 `round8_caseflow_bullet.py`，确认 `*`/`+`/`1.` 三种也能出内容；若能，`ScheduledArticleTools.CASE_FLOW_LABEL` 可以再放宽 |

**这 9 条的定位没有变：全部是上游没实现，本项目前端一条都修不了。**清单的作用是：上游哪天修了，
照「验证动作」那一列逐个打一遍就能确认，不用重新做一轮调研。

##### 技能提示与保存自检的核对结果（本轮的实际补丁）

逐条核对 `SkillSeeder.MARKFLOW_CONTENT` 与 `ScheduledArticleTools` 的自检，结果分三类：

| 类别 | 样例 | 核对结果 | 本轮动作 |
|------|------|---------|---------|
| **技能从没提过** | #3–#6（`layout-*` 家族）、#7（`<hint>` 标签式）、#9（`case-flow` 行首 `-`） | 技能文本里 `layout` / `hint` 一次都没出现；`case-flow` 只写「每行 `- [案例 01] 标题`」，**没说漏了 `-` 会怎样**，也没说 `*`/`+`/`1.` 不行 | 三处都补：① 新增「唯一需要背下来的『禁止写』名单是 `layout-*` 家族」——16 个名字、容器式与标签式 **32 组实测全部把语法原样留在正文**；② `hint` 加进「只有容器写法」名单（第 7 个名称）；③ `:::case-flow` 那条写明**每行都必须是「行首的 `-` + `[标签]`」**，点明 `*`/`+`/`1.`/漏 `-`/漏 `[标签]` **五种写法整块 0 字符** |
| **技能方向写反** | #8（`<slider … />` 自闭合） | 技能 ③ 那条只说了 `<badge>` / `<icon>` 必须自闭合，模型很容易把「自闭合」当成本项目组件的通用规则——**方向恰好相反**：`<slider>` 自闭合会把整行标签留在正文且轮播完全不出现 | 技能轮播条明确写成「**用容器式**；标签式 `<slider>` 必须配对 `</slider>`，自闭合或只开不闭整行标签会原样留在正文」，并点名「这条与 ③ 的 `<badge>`/`<icon>` **方向相反**」 |
| **技能已覆盖但措辞有误** | #1 #2（`:::success` / `:::danger`） | 技能已写「渲染器不认这两个容器名」，但**替代方案那半句是错的** | 改成「需要危险/成功配色时改用 `:::callout type=\"danger\"` / `:::callout type=\"success\"`」，并给出实测配色（`danger` = `#dc2626` ❌、`success` = `#16a34a` ✅） |

保存侧自检（`ScheduledArticleTools`）同步收紧了四处：

- `CONTAINER_ONLY_AS_TAG` 从 6 个名字扩到 7 个（加 `hint`）：标签式写这 7 个名字会在保存前拦下并提示改用容器式。
- 新增 `UNSUPPORTED_LAYOUT_TAG`（`<\s*layout-[a-z][a-z-]*\b`）：正文里出现任何 `layout-*` 标签就提示，
  并给出替代方案清单（`:::breaking` / `:::reading-path` / `:::steps-horizontal` / `<steps>` / `:::timeline` /
  `:::table` / `:::compare`）。容器式另有既有的「不支持的容器」提示兜底。
- `CASE_FLOW_LABEL` 由 `^\s*[-*]?\s*\[` **收紧为** `^-\s*\[\s*\S`——旧判据把 `-` 写成可选、
  还放过 `*`，等于**默认放行四种会归零的写法**。同时新增 `CASE_FLOW_TAG_BLOCK`，
  让 `<case-flow label="…">` 标签式走同一条检查（实测两者行为逐项相同）。
- 新增 `blockHasRowWithoutPrefix(Pattern, CharSequence, Pattern)` 复用两端（容器式、标签式）。

**`:::callout type=` 的一处附带发现**（`round8_callout_types.txt`）：type 取值**没有白名单**——
`tip`/`note`/`info`/`warning`/`caution`/`important`/`danger`/`success` 都有各自的边框色与图标，
但 `error`、`bogus`、以及**不写 type** 会**静默回退成 `info`**（`#0ea5e9` ℹ️），不报错、不警告。
技能里此前只列了 6 个**容器名**写法（`:::tip` / `:::note` / `:::info` / `:::warning` / `:::caution` / `:::important`），
从未提过 `:::callout type=` 的取值集合——但 ④ 那条偏偏又让模型「需要危险/成功配色时改用 `:::callout type=`」，
等于指向了一个没有白名单说明的属性。现已把这 8 个合法取值写明，并注明未知值静默回落 `info`。

##### 模型在真实轮次里写这几类语法的概率

不能给「概率」——没有成品统计能支撑这个数。能给的是**可以直接反证的事实**：
拿全库 44 篇稿件（含软删）的 `CONTENT_MARKDOWN` / `CONTENT_HTML` 逐个扫，

| 扫的写法 | 命中篇数 |
|---------|---------|
| `layout-`（`layout-*` 家族） | **0** |
| `:::success` | **0** |
| `:::danger` | **0** |
| `<hint` | **0** |
| `<slider` | **0** |
| `case-flow` | **2**（文章 41/42，都是本轮探针稿，其中 41 已软删） |

结论：**这 9 种写法里，除 `case-flow` 外模型从来没在自然轮次里写过；`case-flow` 也只出现在探针稿里。**
所以本轮补的技能文本与自检**不是在追已经发生的事故，是在堵一条尚未走到的岔路**——
`layout-*` 那 16 个名字来自 MarkFlow Web 端**另一套组件库**，只有把官网前端的写法当成本项目语法时才会写出来，
而技能里现在有明确的「禁写名单」+ 自检会拦。这条要说清楚的边界是：
**没有观察到 ≠ 不会发生**，本节能给的最强证据是「加提示前后都没有真实产出命中」，不是「概率是 0」。

#### ④ 外部失效资源：只查范围、只给方案，**未动任何数据**

脚本 `target/probe/round8_dead_links.mjs`，产物 `round8_dead_links.md` / `.json`、`round8_url_status.txt`。

**影响面（口径：全库 44 篇含软删，正则抽 `CONTENT_HTML` 里的 `src` 与 `href` 绝对 URL）**

- 正文含绝对 URL 的稿件 **31 篇**，涉及 **32 个域名**。
- 唯一外部 URL **62 个**，逐个 `curl` 探活：**200 × 37 / 404 × 21 / 403 × 2 / 401 × 2**。
- 按位置：`<img src>` 117 次 / 29 篇；`<a href>` 104 次 / 7 篇。

**404 的域名分布（21 条）**

| 域名 | 404 条数 | 形态 | 说明 |
|------|---------|------|------|
| `robocopmao.github.io` | **20** | 全部 `<img src>`，8 个 `/wechat-article-bot/uploads/…` + 12 个 `/r-markdown/*.jpg|png` | GitHub Pages 的 404 页（9379 字节）。**这是历史内容里烧死的绝对 URL** |
| `www.theverge.com` | 1 | `<a href>` | 文章 13 的一条失效链接 |

**受影响稿件：5 篇（均未软删）= 4 篇裂图 20 张 + 1 篇坏链 1 条**

| 稿件 | 404 条数 | 形态 | 域 |
|------|---------|------|----|
| 16 | 6 | `<img>` 全裂 | robocopmao.github.io |
| 21 | 2 | `<img>` 全裂 | robocopmao.github.io |
| 26 | 5 | `<img>` 全裂（同篇另有 5 条 200 的外链） | robocopmao.github.io |
| 35 | 7 | `<img>` 全裂 | robocopmao.github.io |
| 13 | 1 | `<a href>` 坏链 | www.theverge.com |

**不算缺陷的两类**：`openai.com` 2 条 403（反爬）、`wsj.com` / `reuters.com` 2 条 401（付费墙）
—— 站点活着，只是拒绝无名请求，**不要当成失效资源去修**。

**根因（与 §3.11⑧ 第 5 条一致，本轮把范围量清了）**：文章 16/21/26/35 的图片 URL 是
`https://robocopmao.github.io/r-markdown/<名字>_gen.png` 这种**模型自己编的远程地址**，
不是素材工具返回的 `/uploads/<32位hex>.<ext>`。文章 35 的 `CONTENT_MARKDOWN` 里能直接看到
`https://robocopmao.github.io/r-markdown/tesla_roadster_2026_gen.png` —— 模型没调 `generate_image`，
直接把「想象中的图」写成了 URL。14 张 `*_gen.png` / `*_gen.jpg` 名字本身就是生成式命名的痕迹。

**三个可选修复方案**

| 方案 | 做什么 | 改动面 | 风险 |
|------|-------|-------|------|
| **A. 只治未来（推荐，最小）** | 在保存边界加一条自检：`<img src>` 指向非本站域名 → 提示模型改用素材工具；正文里的 `robocopmao.github.io` 前缀一律按可疑处理 | 约 20 行（`ScheduledArticleTools` 一条自检 + 技能一句话）；**不碰存量数据** | 存量 5 篇仍然是裂的；对「模型故意引用外站真实图片」会误报（可用「域名属于本仓库的 GitHub Pages」缩窄） |
| **B. 治未来 + 存量改写** | A 之上，再用一条一次性脚本把这 20 个 404 的 `<img>` 换成站内占位图或直接删掉 | A + 一条 UPDATE 脚本（20 行数据，5 篇） | **动生产数据**：改写正文会改变版本历史语义（旧版本仍指向死链）；占位图未必比空白更好看 |
| **C. 做图片本地化（最重）** | 保存/重渲染时把外链图片抓回本地 `ASSET` 再换 URL，从机制上根治 | 新服务 + 落库路径改动 + 抓取失败的降级策略；`ArticleService.clean` 要接网络 IO | 抓取会把保存路径变成不可控的同步网络调用（与 §3.2 I9 的 `browse_webpage` 是同类问题）；动态生成的 URL（如本批 404）本来就抓不到，C 对这批**无效** |

**推荐 A**：本批 20 张图**已经 404 了，抓不回来**——B 和 C 都救不了它们，只能换内容；
而 A 能挡住的是同一类错误的**下一次**，且改动面最小、不碰数据。B/C 都要在 §3.2 的
「动生产数据」问题上再做一次产品决策，建议单独提。

**另有一条已恢复**：第七轮记录的 `robocopmao.github.io/r-markdown/banner4.webp` 本轮实测 **200**
（上游仓库又上线了）。所以那批 404 里**不能假定「永远 404」**，方案 B 的「直接删掉」因此更该谨慎。

#### ⑤ D42：绝对 URL 烧进正文（本轮修掉，含红→绿反证）

**症状**：库里 19 篇 MARKFLOW 稿的 `CONTENT_HTML` 里，图片地址是
`http://localhost:8081/uploads/<32位hex>.png` —— **站点域名被烧进了正文**。
同库的 PROMPT 稿则是干净的相对路径 `/uploads/…`。

**根因**：不是模型写的，是**渲染链路自己加的**。
`MarkFlowRenderService.absoluteImageUrls`（送渲染前）把 `/uploads/…` 绝对化成
`{site_base_url}/uploads/…`（上游对相对路径原样透传、不补域名），这个绝对地址随渲染产物直接写回
`CONTENT_HTML`。而落库边界 `ArticleService.clean` 的判据此前**只认相对形式**，于是绝对形态原样穿了过去。
**每一篇 MARKFLOW 成稿都会命中**（全库 19 篇，含 2026-09-13 09:29 生成的最新一篇，无一例外）。

**为什么是 P1**：换部署 / 换域名后，编辑器与预览整片裂图；而微信同步那边其实一直是正常的
（`ArticleService.localStorageName` 只看路径是不是 `/uploads/<32位hex>.<ext>`，不看域名）。
两边口径不一致，问题只会在换域名那天爆发。

**修复**：把 `LOCAL_ASSET_SRC` 的域名部分改成**可选**，落库时统一归一成相对路径：
```java
private static final Pattern LOCAL_ASSET_SRC = Pattern.compile(
        "(?i)(\\bsrc\\s*=\\s*)([\"'])(?:https?://[^\"'/]+)?(/uploads/[a-f0-9]{32}\\.(?:jpg|png|gif|webp))\\2");
```
判据**刻意不认域名**——这与 `localStorageName` 完全一致（那正是「换 CDN 域名后旧稿仍能同步」的原因）。
两边口径必须一致，否则会出现「归一后微信认、不归一时编辑器认」的撕裂。命中后仍走既有的哨兵域名往返
（`LOCAL_ASSET_ORIGIN`），保证 PROMPT 引擎的 Jsoup 白名单不把相对 URL 当非法协议剥掉。

**前后对照（红 → 绿，反证已做）**：新增 `src/test/java/.../article/ArticleLocalAssetUrlTests.java`（6 例，
走真实落库入口 `createForTask` → `createWithClean` 的同一条路径，只把 Mapper 换成桩）。
把正则临时改回修复前形式后立刻复现：

```
Tests run: 6, Failures: 4   →  BUILD FAILURE
expected: "<section><img src="/uploads/01c6fec…fe2.png" width="600"></section>"
 but was: "<section><img src="http://localhost:8081/uploads/01c6fec…fe2.png" width="600"></section>"
```

`but was` 里的字符串与库里 19 篇稿件的 `CONTENT_HTML` **逐字节同形**——缺陷从库到测试是同一个东西，
不是构造出来的相似物。改回修复后判据：**Tests run: 6, Failures: 0 → BUILD SUCCESS**。
完整两轮记录在 `target/probe/round8_url_fix_redgreen.txt`。

**边界（本轮没做的）**：没有跑一次真实调度轮次产出新稿作对照——那需要重启应用 + 新建文章，
即**改生产数据**，与本轮约束冲突。所以「新产出的稿件不会再烧域名」的证据是三条：
① 落库入口只有一个（调度与编辑器共用 `clean`）；② 反证已做；③ 库里 19 篇存量本轮**未回改**。
**存量 19 篇的清洗要不要做，是一个单独的产品决策**（同 ④ 的方案 B/C 一类），本轮不做。

#### ⑥ 本轮新修缺陷汇总

| # | 症状 | 关键证据 | 关键文件 |
|---|------|---------|---------|
| **D42** | MARKFLOW 成稿把站点域名烧进 `CONTENT_HTML`（`http://localhost:8081/uploads/…`），换域名即整片裂图 | 全库 19 篇无一例外（含当天 09:29 最新一篇）；同库 PROMPT 稿干净。红→绿反证见 `round8_url_fix_redgreen.txt` | `article/ArticleService`（`LOCAL_ASSET_SRC`）、新增 `article/ArticleLocalAssetUrlTests`（6 例） |
| **D43** | `:::case-flow` 的行首 `-` 在**保存自检**里是可选的，`*` / `+` / `1.` / 裸行 / 漏 `[标签]` 五种写法**整块归零**（0 字符、`ok:true`、零 warnings）却一路放行 | `round8_caseflow_bullet.txt`：7 种列表符号 × 容器式/标签式，只有「行首 `-` + `[标签]`」有产出 | `ai/ScheduledArticleTools`（`CASE_FLOW_LABEL` 收紧为 `^-\s*\[\s*\S`、新增 `CASE_FLOW_TAG_BLOCK`、`blockHasRowWithoutPrefix`）、`skill/SkillSeeder`、`ai/ScheduledArticleToolsDraftTests` |
| **D44** | 技能提示与保存自检**都不知道** `layout-*` 家族的存在：32 组写法把语法原样留在正文、零警告 | `round8_unknown_tags.txt`：16 个 `layout-*` 名 × {容器, 标签} = 32 组**全部泄漏**；`guide` 全文不提，模型只能靠猜 | `ai/ScheduledArticleTools`（新增 `UNSUPPORTED_LAYOUT_TAG` + 替代方案提示）、`skill/SkillSeeder`（新增禁写名单）、`ai/ScheduledArticleToolsDraftTests`（`unsupportedLayoutFamilyIsReported`） |
| **D45** | 技能的「自闭合」规则方向**写反了**：`<badge>`/`<icon>` 必须自闭合，`<slider>` 自闭合则会整行标签留在正文、轮播不出现；另 `<hint>` 只有容器式、`:::callout type=` 的 `danger`/`success` 替代方案措辞有误 | `round8_na_discriminator.txt`（`na-slider-selfclose` 303 字符 / 文字 0 / svg 0 / 残留 `<slider`）、`round8_callout_types.txt`（`danger`=`#dc2626`、`success`=`#16a34a`，「不支持」的旧措辞不成立） | `ai/ScheduledArticleTools`（`CONTAINER_ONLY_AS_TAG` 6→7 加 `hint`）、`skill/SkillSeeder`（轮播条点明方向相反、`hint` 入名单、type 列表补 `danger`/`success`）、`ai/ScheduledArticleToolsDraftTests` |

**组合用例本身没有产生任何本项目侧的新缺陷**（编辑器 17/17 pass）——② 暴露的 9 例差异全在上游。
D43–D45 是 ③ 的「9 条 `na` 核对」查出来的**技能/自检侧**缺口：上游不修的前提下，
本项目能做的是让模型别去写它们。

#### ⑦ 收尾数字

- 全量测试：`./.mvn/mvn-local.sh -o test` → **Tests run: 303, Failures: 0, Errors: 0, Skipped: 0，
  BUILD SUCCESS**（2:51）。日志 `target/probe/round8_tests.log`。
  较第七轮的 296 增加 7 例：`ArticleLocalAssetUrlTests` 新增 6 例、
  `ScheduledArticleToolsDraftTests` 23→24（新增 `unsupportedLayoutFamilyIsReported`，
  并扩展了 `containerRowsThatRenderToNothingAreReported` 的列表符号覆盖）。
- 前端构建：`cd webui && npm run build` → **✓ built in 393ms**（仅既有 >500 kB chunk 告警）。
- 样例交叉核对（逐条对得上）：`all_summary.json` **79 行**、
  `Counter({'pass': 70, 'na': 9})`、`fail`/`unverified` 均为 0；`shots/all/*.png` **79 张**、
  `shots/combo/*.png` **17 张**、`target/probe/components/*.md` **79 个**。
- 本轮**未提交、未推送**；`git status --porcelain` 确认 `src/` 与 `webui/src/` 下**没有探针文件泄漏**——
  `target/probe` 只出现在 4 个 Java 文件（13 处）与 `webui/src/editorExtensions.js`（9 处）的
  **注释/Javadoc 引用**里，没有任何运行时读取、import 或资源路径；
  `git check-ignore -v target/probe/…` → `.gitignore:2:target/`。

#### ⑧ 第八轮仍未闭合 / 没覆盖到的

1. **③ 的 9 条要变成 pass，只能等上游**（清单与验证动作已在上表）。本轮**没有为了凑 pass 改任何判据**。
2. **④ 的 5 篇裂稿件与 ⑤ 的 19 篇烧域名稿件都还在库里**，本轮按约束未动数据；
   修不修、怎么修，是独立的产品决策（方案 A/B/C 见 ④）。
3. **⑤ 的修复没有真实轮次对照**（原因见该节「边界」）：需要一次应用重启 + 一篇新成稿才能给出端到端前后对照。
4. **组合覆盖只做了 17 例，不是全组合**：`ScheduledArticleTools.SUPPORTED_CONTAINERS` 有 19 个容器名，
   两两嵌套（含自嵌套）共 19×19 种，本轮按五个家族各挑了代表。
   未覆盖的值得下一轮补的是：**三层嵌套**（容器套容器套容器）、**`:::table title=` 嵌在 `:::timeline` 里**
   （`cmb-timeline-table` 的反方向）、以及**超长内容 + 嵌套**的叠加（本轮的超长是纯 markdown 单节）。
5. **`:::hint` 与 `:::info` 是否同一个东西没有定论**：实测两者产物同族（349 / 341 字符、同为信息卡配色），
   但没逐字节比对。若上游确认合并，技能里 ③ 那条「第 7 个只有容器形式的名称」要简化。
6. **第五轮列的两条仍然挂着**：`<p>` 包装造成的多行列表悬挂缩进差异、上游 R2 证伪实验清单
   （嵌套容器 / 属性引号变体 / CRLF）——后者本轮做了一部分（嵌套见 ②），属性引号变体与 CRLF 仍未做。
7. **79 个样例里「写法是否官方」没回查**（第五轮回查了 4 条，其余 75 条未动）。

**一句话验收口径**：79 个样例在真实浏览器里 **70 个确认渲染正确、9 个确认是上游没实现、0 个渲染失败**；
组合条件下 **编辑器侧 17/17 pass、9 例差异全在上游**；
公式与轮播的真实稿件链路**都已走通**；
本轮**新修 4 个本地缺陷（D42–D45）**，剩下的差距全部落在**上游实现**与**存量数据治理**两件事上。

### 3.13 2026-09-13 第九轮：对第八轮改动的回归复查与收口（判据自证 / D42–D45 红绿 / 文档对齐）

本轮**没有引入任何新改动**，只做四件事：重跑受影响的验证、把第八轮的判据修正自证一遍、
给 D42–D45 各补一次红→绿、把文档与工作树对齐。所有产物在 `target/probe/`（gitignore）。
本轮同样**未 commit、未 push、未加依赖、未改生产数据**。

#### ① 重跑受影响的验证（全部刷新，不复用第八轮数字）

| 项 | 第八轮 | **第九轮（本轮实跑）** | 日志 / 产物 |
|----|-------|------------------|-----------|
| 后端全量测试 | 303 / 0 失败 / 2:51 | **303 / 0 失败 / 0 错误 / 0 跳过 / BUILD SUCCESS，2:46** | `target/probe/round9_tests.log` |
| `webui npm run build` | ✓ 393ms | **✓ 417ms**（仅既有 >500 kB chunk 告警） | — |
| 79 个最小写法样例（真实浏览器） | pass 70 / na 9 / fail 0 | **pass 70 / na 9 / fail 0，79 张截图** | `browser/all_summary.json` / `.md`、`shots/all/*.png` |
| 17 个组合用例（真实浏览器） | 上游 8 / 8 / 1，编辑器 17 pass | **上游 ok 8 / nested-unsupported 8 / silently-lost 1；编辑器 pass 17 / fail 0，17 张截图** | `browser/combo_summary.md` / `combo_result.json`、`shots/combo/*.png` |
| 9 条 `na` 的上游复验（真实渲染 API，16 例） | — | **逐项与第八轮相同**（无上游漂移） | `round8_na_discriminator.txt`（重跑覆盖）、`round8_caseflow_bullet.txt`、`round8_callout_types.txt`、`round8_unknown_tags.txt` |

> 重跑前做的准备：`npx vite build --config ../target/probe/browser/vite.config.mjs`（170ms）重建探针 dist。
> 两个套件都是**真跑真实 Chrome**（Chrome 138.0.7204.100 + 自写 CDP），不是读第八轮的落盘结果。
>
> 注：`round8_*.py` 的脚本名沿用第八轮，本轮是**重跑覆盖**同名产物文件（内容全刷新）。

**「修正后的判据不产生新的 fail」这条断言的结论**：**成立，而且是结构性成立**——
泄漏判据只作用在**上游那一列**（产物有没有把语法原样吐出来），
编辑器那一列（pass / fail）由「可见文字逐字比对 + 结构特征比对 + 非零盒子」得出，两者没有交集。
实测印证：判据变严后 `nested-unsupported` 由 6 变 8，**编辑器仍是 pass 17 / fail 0**，
79 样例套件也仍是 `fail 0` / `unverified 0`。没有一条从 pass 掉成 fail，
也没有一条编辑器侧的失败被这次修正掩盖（`fail` 那一列两轮都是 0）。

#### ② 第八轮判据修正的自证：两版跑同一批产物

脚本 `target/probe/round9_leak_criterion_diff.py` → `round9_leak_criterion_diff.txt`。
**关键设计：两版跑的是同一批已落盘的产物**（`combo_result.json` 里第八轮那一次的参照侧 HTML），
不重新请求渲染 API——否则差异可能来自上游抖动，而不是判据本身。

| 判据 | 正则 |
|------|------|
| 第一版 | `re.findall(r":::[a-z-]+", 可见文字)` —— 只认「`:::` + 名字」 |
| 修正版 | `re.findall(r":::", 可见文字)` —— 连收尾那一行**光秃秃的 `:::`** 一起认 |

**差异：2 / 17 条**，方向全部一致（都是变严）：

| 用例 | 旧判据数到 | 修正版数到 | 旧 → 新 |
|------|-----------|-----------|--------|
| `cmb-timeline-table` | 带名字 0 处 | 裸收尾 **1** 处 | silently-lost → **nested-unsupported** |
| `cmb-caseflow-table-title` | 带名字 0 处 | 裸收尾 **1** 处 | silently-lost → **nested-unsupported** |

方向核对（这是本节要回答的两个「有没有」）：

- **有没有把「上游不支持」错记成「编辑器缺陷」？没有。** 两版判据**都只看上游产物**，
  编辑器那一列压根不在这个文件里参与计算；实测编辑器仍是 pass 17 / fail 0。
- **有没有反向漏判？没有。** 统计口径：**0 条**从 `nested-unsupported` / `silently-lost` 变成 `ok`
  （判据没有变松），**0 条** `ok` 变严（没误伤正常渲染的组合），只有上面那 2 条从「静默丢内容」
  改判成「语法没被消费」——人工复核两条的产物可见文字，结尾确实各挂着一个字面 `:::`。

**顺带记一条判据边界（仍未覆盖，写进 §4.2 第 10 条了）**：修好之后仍有一类**元素形态的透传**
是这套判据看不见的——`<steps>` / `<step>` 被当未知元素留在产物 DOM 里时，
可见文字一个不少、`:::` 一处没有，只有 `tagHistogram` 里多出组件名才暴露
（`cmb-callout-steps` 就是这一类，所以它仍留在 `silently-lost`）。
同批还查到 `cmb-inline-in-container` 的 `<Badge>新</Badge>` 也原样透传，
但那是**探针自己写错的形态**（官方是自闭合 `<badge … />`），按 R4-附 的教训不计为上游缺陷。

#### ③ D42–D45 的红→绿（本轮重跑，逐条给「改前 / 改后」）

完整原文在 `target/probe/round9_redgreen.txt`，含每条的命令、退出码、断言原文与日志路径。
做法统一：**把判据改回修复前的形式 → 跑该条的哨兵用例 → 原样改回**，两次运行之间无其他改动。

| # | 改前（复现） | 改后（现在） |
|---|------------|------------|
| **D42** 站点域名烧进正文 | ① 只读 SQL：**19 篇 / 69 处** `http://localhost:8081/uploads/` 仍在库里；② 把 `LOCAL_ASSET_SRC` 的域名部分改回「不允许」→ `ArticleLocalAssetUrlTests` **6 例 4 失败**，`but was` 与库里那 69 处**逐字节同形**（`round9_d42_red.log`） | 域名部分改为可选 → **6 例 0 失败 / BUILD SUCCESS**（`round9_d42_green.log`） |
| **D43** `case-flow` 行首 `-` | `CASE_FLOW_LABEL` 改回 `^\s*[-*]?\s*\[` → `containerRowsThatRenderToNothingAreReported` 失败于 `*` 用例（实测上游 0 字符）；即旧判据对 5 种归零写法**一条都不报**（`round9_d43_red.log`） | `^-\s*\[\s*\S` + `CASE_FLOW_TAG_BLOCK` → **24 例 0 失败** |
| **D44** `layout-*` 家族自检 | 把 `UNSUPPORTED_LAYOUT_TAG` 换成永不匹配的图案（模拟「自检不知道这族」）→ `unsupportedLayoutFamilyIsReported` 失败于 `<layout-hero>`；对应上游 32 组全部泄漏（本轮重打 API 复验）（`round9_d44_red.log`） | `<\s*layout-[a-z][a-z-]*\b` → **24 例 0 失败** |
| **D45** `<slider>` 自闭合方向 + `<hint>` | 把 `hint` 从 `CONTAINER_ONLY_AS_TAG` 名单去掉 → `containerOnlyComponentsWrittenAsTagsAreReported` 失败于 `<hint>`（`round9_d45_red.log`） | 名单 6→7 → **24 例 0 失败**（`round9_d4345_green.log` 是同一次绿跑的日志） |

> ⚠️ **D45 只有一半生效，必须单独说清楚**：D45 的另一半是**技能文案**（`SkillSeeder.MARKFLOW_CONTENT`），
> 它在**应用启动时**才 upsert 进 `SKILL` 表。本轮约束是「不要改生产数据」，所以**没有重启应用**，
> 也就没有触发那次写入。只读 SQL 实测：`SELECT LOCATE('layout-', CONTENT), LOCATE('行首的 `-`', CONTENT)
> FROM SKILL WHERE ID=4` → **`0, 0`**，库里的技能文案仍是旧版；而源码里两处都在
> （`SkillSeeder.java:130` 的「行首的 `-`」、以及新增的 `layout-*` 禁写名单）。
> **结论：D43/D44/D45 的 Java 自检代码在工作树里已生效且有测试钉住；技能文案要等下一次应用重启才生效。**
> 这是 §4.2 第 5 条记过的既有语义，不是本轮新引入的缺口——在那之前，模型看到的系统提示仍是旧文案，
> 只能靠保存侧自检兜住。

#### ④ 文档与工作树的一致性核对（逐条对完，改了两处）

按「文档里写的 / 工作树里实际是什么」逐条比对本节与 §3.12 涉及的全部断言：

| 文档断言 | 工作树实际 | 结论 |
|---------|-----------|------|
| `CONTAINER_ONLY_AS_TAG` 6→7 个名字，含 `hint` | 正则里 7 个：`compare\|reading-path\|steps-horizontal\|steps-vertical\|callout\|code-block\|hint` | ✅ |
| `UNSUPPORTED_LAYOUT_TAG = <\s*layout-[a-z][a-z-]*\b` | 一致 | ✅ |
| `CASE_FLOW_LABEL` 收紧为 `^-\s*\[\s*\S` | 一致 | ✅ |
| 新增 `CASE_FLOW_TAG_BLOCK` + `blockHasRowWithoutPrefix` | 两者都在，容器式与标签式各调一次 | ✅ |
| `LOCAL_ASSET_SRC` 域名部分可选 | 一致 | ✅ |
| `ScheduledArticleToolsDraftTests` 23→24 | 实跑 24 例 | ✅ |
| 新增 `ArticleLocalAssetUrlTests` 6 例 | 实跑 6 例 | ✅ |
| 全量门禁 303 例 | 实跑 303 | ✅ |
| 16 个 `layout-*` 名字 × 2 种写法 = 32 组全泄漏 | 脚本重跑复现 | ✅ |
| `:::callout type=` 8 个合法值、未知回落 info | 重跑复现 | ✅ |
| 「技能里此前只列了 6 个 **type**」 | 实为 6 个**容器名**（`:::tip`…`:::important`），`:::callout type=` 的取值集合从未提过 | ❌ **已改文档**（见下） |
| 上游清单里 `<slider>` 单图那组写作「对照容器式 `<slider></slider>`」 | 该组是**标签式开+闭**（214 字符、svg 0、无残留），不是容器式 | ❌ **已改文档**（§3.12③ #8、D45 两处措辞） |

两处已直接改正；其余逐条一致。`docs/dev/upstream-issues.md` 新增的三小节
（`layout-*` 全族、`:::callout type=` 无白名单、嵌套组合的两种新形态）也已逐条对着
`round8_unknown_tags.txt` / `round8_callout_types.txt` / `combo_summary.md` 核过。

#### ⑤ 收尾清点

| 指标 | 数字 | 口径 |
|------|------|------|
| **组件总数** | **79** | `target/probe/component_matrix.json` 的行数（第五轮建的清单，唯一出处） |
| **已验证数** | **70 pass** | 79 个样例在真实浏览器里逐行判定；另 17 个组合用例**编辑器侧 17/17 pass** |
| **上游未解数** | **9（样例）+ 9（组合）** | 9 条 `na` 逐条复验确认上游没实现；组合里 8 条 `nested-unsupported` + 1 条 `silently-lost` 也全在上游。**两类都是 0 条能由本项目前端修好的** |
| **本地已修数** | **本轮 4（D42–D45）；累计编号 D1–D45** | 本轮新增 4 条，其中 D45 的**技能文案部分尚未落库**（见 ③ 的警告） |
| **编辑器侧 fail 数** | **0** | 79 样例套件 `fail 0 / unverified 0`；17 组合套件 `fail 0` |

**9 条「等上游」清单的验证动作栏仍然有效**（§3.12③ 的表格原样保留）：
每条都写着「上游修复后本项目要跑哪个脚本、看哪个量」——
`ctn-success` / `ctn-danger` / `reg-layout-*` 重跑 `run-all-browser.mjs` 看 `leak=0`，
`reg-hint-tag` 重跑 `round8_unknown_tags.py` 看 `tag:hint` 的 `leak=False`，
`blk-slider-selfclose` 重跑 `round8_na_discriminator.py` 看 `na-slider-selfclose` 的 `<svg> ≥ 1`，
`blk-case-flow-badline` 重跑 `round8_caseflow_bullet.py` 看 `*` / `+` / `1.` 是否也出内容。
本轮重跑这四个脚本，9 条的结论**逐条未变**，清单不需要改写。

#### ⑥ 第九轮仍未闭合

1. **D45 的技能文案尚未生效**（需应用重启触发 `SkillSeeder` upsert；本轮受「不改生产数据」约束未做）
   —— 这是本轮唯一一处真实缺口，完整描述见 ⑦。
2. **D42 缺真实轮次对照**（同上：要重启 + 新建文章）。
3. **存量数据仍在**：19 篇 / 69 处烧域名 + 5 篇裂图（20 张 404 图 + 1 条坏链），本轮只读复核、未改。
4. **元素形态的透传仍无判据**（`<steps>` / `<step>` 这类；见 ② 末尾）。
5. 第八轮 §3.12⑧ 里其余各条（三层嵌套未覆盖、`:::hint` 与 `:::info` 是否同一、属性引号变体与 CRLF、
   79 样例里 75 条写法未回查官方定义）——本轮**没有推进**，原样保留。

#### ⑦ 第九轮唯一一处真实缺口：D45 的技能文案没进库（写全）

第九轮汇报在「一处真实缺口，如实记录」处截断，这里把它写完整。**先给结论：这是一个真缺口，不撤回。**
第八轮的 `### 3.12` 里没有对应条目（D45 编号本身是第九轮才立的），所以本节是首次成文，不存在重复条目。

**完整描述.** D43 / D44 / D45 这一批「模型写错写法」的治理分**两半**落地：

| 半边 | 载体 | 生效时机 | 第九轮时的状态 |
|------|------|---------|---------------|
| **保存侧自检（Java）** | `ScheduledArticleTools` 的 `CASE_FLOW_LABEL` / `CASE_FLOW_TAG_BLOCK` / `UNSUPPORTED_LAYOUT_TAG` / `CONTAINER_ONLY_AS_TAG` 等正则，在 `save_article_draft` 时扫描并告警 | 改完源码、重新编译即生效 | ✅ **已生效**，且有 `ScheduledArticleToolsDraftTests` 24 例钉住（第九轮红→绿各跑一次） |
| **注入侧文案（技能）** | `SkillSeeder.MARKFLOW_CONTENT` → 启动时 upsert 进 `SKILL` 表 → `SkillPromptAssembler` 组装成模型看到的系统提示 | **只在应用启动时**按 `BUILTIN_KEY` 覆盖 `content` / `engine` / `engineConfig` | ❌ **未生效**：工作树源码改了、库里还是旧文案 |

也就是说：源码里已经写好了「不要写 `layout-*`」「`case-flow` 行首必须是 `-`」这些禁令，
但**模型根本看不到**——它读到的是库里那份没有这些段落的旧文案。缺口的实质是
**「写对了代码，但没把改正后的文案交给模型」**，而不是代码没改。

**影响范围.** 明确落在**后端（写入侧 → 系统提示这一条链路）**，另两侧不涉及：

| 侧 | 是否受影响 | 依据 |
|----|-----------|------|
| **后端** | ✅ 受影响 | 组装系统提示取的是 `SKILL.CONTENT`（`SkillPromptAssembler` 从库读），不是 `SkillSeeder` 的字符串常量。库里没有禁令 → 模型仍可能写出 `:::success` / `:::danger` / `layout-*` / 行首漏 `-` 的 `case-flow` |
| **编辑器前端** | ❌ 不受影响 | 编辑器只忠实渲染后端产物；本轮 76 组 layout-* 与 9 条替代写法实测，编辑器侧 `fail` 一直是 0 |
| **渲染 API（上游）** | ❌ 不受影响 | 上游认不认这些写法与技能文案无关，见 `docs/dev/upstream-issues.md` |
| **文档** | ⚠️ 只是「要写清楚」 | 本条即文档侧的交代；另见 §4.2 第 5 条（这条语义第七轮就踩过并写进验收原则） |

**当前状态：已修 / 未落库（待重启触发），不属于「等上游」。**
源码 `SkillSeeder.MARKFLOW_CONTENT` 已含全部新增段落（7919 字符；`layout-` 名单在第 81–82 行、
「行首的 `-`」在第 130 行）；缺的只是那次启动 upsert。

**可反证的实测证据（只读 SQL，`SKILL` 表 ID=4 = `markflow_default`）：**

```sql
SELECT CHAR_LENGTH(CONTENT),
       LOCATE('layout-', CONTENT),
       LOCATE('行首的 `-`', CONTENT),
       LOCATE('danger', CONTENT),
       UPDATED_AT
FROM SKILL WHERE ID = 4;
```

结果 `5635 / 0 / 0 / 1242 / 2026-09-13 08:38:21`——**库里的技能文案仍是 D43/D44/D45 之前那一版**
（`layout-` 与「行首的 `-`」两处都搜不到，`5635` 远小于源码的 `7919`）。
这条查询可被反证：重启应用后再跑，两个 `LOCATE` 必须同时 `> 0`；若仍为 0，则说明 upsert 本身有问题
（而不是「没重启」），那才是另一类缺陷。

**为什么没有在本轮修掉.** 第九 / 第十轮的约束是「不要改生产数据（除只读查询）」。
触发 `SkillSeeder` upsert 需要**重启应用**，而重启会写 `SKILL` 表——属于生产数据写入，本轮不做。
**唯一的修复动作**（下一轮可直接执行）：重启应用 → 跑上面那条 SQL 确认两个 `LOCATE > 0` →
再用 `POST /api/skills/preview` 看组装出的系统提示里确实出现新禁令（§4.2 第 5 条要求的验证方式）。

**风险敞口.** 在重启之前，模型仍可能写出 D43/D44/D45 要防的那几种写法；
但**保存侧自检是活的**，所以后果被限制在「告警」而不至于静默落库——这也是本轮敢把它留作待办的依据。
已写进 §八 第 7 条。

> **第十一轮补记**：上面那句「只能靠肉眼比对」已经不成立——第十一轮新增了
> `SkillSeederMarkflowContentTest`（5 例，**不连库**）把「源码确实写了这几段」变成自动化断言，
> 并在 §八 第 7 条给出库 ↔ 源码的只读复验片段；**阻塞性判定为「不影响『所有组件两条路径都渲染正确』这条验收」**，
> 优先级相应从 P0 下调为 P1。详见 §3.15①②。

---

### 3.14 2026-09-13 第十轮：组件渲染清单收口（悬空 22→0）/「等上游」清单靠改写法收敛 / 回归

本轮是**纯测量与文档轮**：没有改任何生产代码、没有加依赖、没有改生产数据（只读 SQL 除外），
改动全部落在工作树里（`docs/` + gitignore 的 `target/probe/`）。目标是把那句验收标准
——**「所有组件在后端 API 与编辑器前端两条路径都渲染正确」**——的覆盖面彻底钉死：
**逐组件、逐路径、每一格都有实测证据**，而不是只有一句结论。

#### ① 补的是第八轮留下的真空：22 个「悬空」组件

「悬空」的定义：**既没被验证过、也没被标记成「等上游」**。第八轮结束时确实存在，本轮清零：

| 口径 | 第八轮 | **第十轮** |
|------|-------|-----------|
| 引擎注册表里的组件 ID（**组件全集的唯一出处**） | 63 | **63** |
| 其中 `layout-*` | 38 | 38 |
| 实测过的 `layout-*` 名字 | **16**（`round8_unknown_tags.py`） | **38 个名字 × 2 种写法 = 76 组** |
| **悬空** | **22** | **0** |

`target/probe/round10_registry_closure.py` 把 38 个名字 × {容器式 `:::name`、标签式 `<name>`} 共 76 组
逐个打真实渲染 API，每组落盘产物 HTML；再由 `target/probe/browser/run-set-browser.mjs registry`
把同一份产物灌进**真实 Chrome**。结果：

- **后端产物 76 / 76 `not-rendered`**（容器式在可见文字里留着字面 `:::`，标签式在产物里留着字面元素），支持这族的 **0 组**；
- **编辑器 `na 76 / fail 0`**——参照侧本身就是坏产物，编辑器只是忠实渲染，**没有编辑器缺陷**；
- 与 bundle 里的语法匹配器互相印证：`component_matchers.json` 的 **29 条匹配器里没有任何 `layout-*` 分支**。

> 判据更正（本轮在文档里改了 3 处措辞）：`layout-*` 的标签式写法在上游**不是**「把标签名当文字输出」，
> 而是**元素形态的透传**——`<p style="…"><layout-hero>子文本</layout-hero></p>`，
> 可见文字里只剩子文本、看不见标签名，只有扫**原始产物 HTML** 才留得住证据。
> 这直接影响泄漏判据的写法（先剥标签会把这一类洗掉），见 §4.2 第 10 条 ①。

#### ② 对照表终稿：组件全集 × 两条路径

终稿在 `target/probe/round10_component_paths.md` / `.json`（由 `round10_component_paths.mjs` 生成，
输入全是实测产物，没有一项来自印象）。它分两张表：

- **表 A —— 注册表 63 个 ID 逐个 × 两条路径**（每一行都带覆盖样例、真实稿件命中文章号、证据文件路径）；
- **表 B —— 不在注册表内、但渲染引擎真认的 40 条语法**（标准 Markdown、行内标记、公式、图表、属性变体，
  以及 2 条**故意写错的反例**）。

**汇总数字（终稿）：**

| 指标 | 数字 |
|------|------|
| **组件总数（注册表 ID 全集）** | **63** |
| 表 B 另计的非注册语法样例 | 40（79 样例里的其余部分） |
| **两条路径均通过** | **25**（后端 API 画出 + 编辑器前端往返后仍在） |
| **等上游** | **38**（全部是 `layout-*` 家族，63 − 25 = 38，无残余） |
| **悬空** | **0** |

#### ③ 三个验证层级必须分开说（这是本轮要求的口径）

| 层级 | 含义 | 本轮计数 |
|------|------|---------|
| **A｜真实稿件证过** | 该组件写法在**全库真实稿件的 `CONTENT_MARKDOWN` 里出现过**，且那篇文章**在真实 SPA 里回归过**（`browser/articles_result.json`，13 篇 ready） | 注册表 **13** 个 ID |
| **B｜最小样例（真实浏览器）** | 79 个最小样例 + 本轮新增的 76 组 `layout-*`，全部在**真 Chrome** 里逐行判定（不是 jsdom） | 注册表 **50** 个 ID + 表 B 全部 40 条 |
| **C｜仅 jsdom** | 只跑过 jsdom、没进真实浏览器 | **0**（79 个样例第七轮已全部上真浏览器，第九、十轮各重跑一次） |

A 级的证据来源是**只读扫描**：`docker exec … mysql -N -B` 导出全库 44 篇（含软删 6 篇）的
`CONTENT_MARKDOWN` 到 `target/probe/round10_articles.tsv`，再由 `round10_article_coverage.py`
按每个组件的规范写法逐个正则匹配（**没有改任何数据**）。结果：

- 70 个扫描项里 **39 个在真实稿件里出现过**、**31 个没有**（对应表 A/表 B 里标 `—` 的那些）；
- **6 种「禁写」写法在真实稿件里的命中全部为 0**——`layout-*`、`:::success`、`:::danger`、`<hint>` 标签式、
  `<slider/>` 自闭合、`case-flow` 行首漏 `-`。这是「模型至今没写出这些错法」最强的反证，
  也说明第八、九轮那些禁令是**预防性**的。（禁令未落库的问题见 §3.13⑦。）

#### ④ 「等上游」清单的收敛：能靠改写法绕开的，就不必等

第九轮遗留 9 条 `na`（上游不认的写法）。本轮逐条试「**只改写法、不改判据、不等上游修**」，
10 条替代写法（9 条里的第 8 条拆成多图/单图两例）**在两条路径上全部通过：后端 ok 10 / 编辑器 pass 10 / fail 0**：

| 清单 # | 等上游的写法 | 推荐替代写法 | 后端产物 | 编辑器 |
|-------|-------------|-------------|---------|-------|
| #1 | `:::success` | `:::callout type="success"`（`#16a34a` → `rgb(22,163,74)`） | ok（359 字符） | **pass** |
| #2 | `:::danger` | `:::callout type="danger"`（`#dc2626` → `rgb(220,38,38)`） | ok（358 字符） | **pass** |
| #3 / #4 | `:::layout-hero` / `<layout-hero>` | `:::breaking` | ok（771 字符） | **pass** |
| #5 | `:::layout-toc` | `:::reading-path` | ok（2251 字符） | **pass** |
| #6 | `:::layout-metrics` | `:::compare` | ok（1756 字符） | **pass** |
| #7 | `<hint>` 标签式 | `:::hint` 容器式 | ok（352 字符） | **pass** |
| #8（多图） | `<slider … />` 自闭合 | `<slider …></slider>` 开+闭（1 个 `<svg>` + `animateTransform`） | ok（1407 字符） | **pass** |
| #8（单图） | `<slider … />` 自闭合 | 同上写法（降级为普通 `<img>`，**产物里 0 处字面 `<slider` 残留**） | ok（214 字符） | **pass** |
| #9（容器式） | `case-flow` 行首漏 `-` | `:::case-flow`，每行写成 `- [标签]` | ok（1030 字符） | **pass** |
| #9（标签式） | `case-flow` 行首漏 `-` | `<case-flow>` 同规则（产物与容器式**逐字节相同**） | ok（1030 字符） | **pass** |

**结论**：9 条「等上游」在**写对写法**的前提下全部可以在本项目侧绕开，不构成阻塞。
清单**保留**（上游若修复这些写法，仍按第九轮记的验证动作复跑），但**降级为「可选优化」**——
不影响「所有组件两条路径都渲染正确」这个验收标准的达成。
原始输入、两条路径的实测数字与截图见 `browser/alt_summary.md` 与 `shots/alt/<id>.png`。

> 本轮**没有为了让替代写法通过而放宽任何判据**：判定口径与 79 样例 / 17 组合完全同一套
> （`summarize-alt.mjs`，见文件头注释）。过程中修正的是**我自己写错的期望值**两处：
> `alt-reading-path-for-layout-toc`（`|` 分隔的描述列本来就不该显示）与
> `alt-compare-for-layout-metrics`（它渲染成自绘的 `<section>` 网格，**不是** `<table>`）——
> 都是删掉写错的断言，不是削弱判据。
> 另修一处**判据漏洞**：标签式 `layout-*` 的泄漏是元素形态、可见文字里看不见，
> 最初被 `summarize-alt.mjs` 判成后端 `ok`；补上 `leakRaw` 条件后，registry 汇总正确显示
> **后端 ok 0 / not-rendered 76**。

#### ⑤ 回归确认（本轮全部重跑，不复用第九轮数字）

| 项 | 第八轮 | 第九轮 | **第十轮（本轮实跑）** | 日志 |
|----|-------|-------|--------------------|------|
| 后端全量测试 | 303 / 2:51 | 303 / 2:46 | **303 / 0 失败 / 0 错误 / 0 跳过 / BUILD SUCCESS，2:48** | `round10_tests.log` |
| `webui npm run build` | ✓ 393ms | ✓ 417ms | **✓ 434ms**（仅既有 >500 kB chunk 告警） | `round10_webui_build.log` |
| 79 个最小写法样例（真实浏览器） | pass 70 / na 9 / fail 0 | 同 | **pass 70 / na 9 / fail 0 / unverified 0**，79 张截图 | `browser/all_summary.md`、`shots/all/*.png` |
| 17 个组合用例（真实浏览器） | 8 / 8 / 1，编辑器 17 pass | 同 | **上游 ok 8 / nested-unsupported 8 / silently-lost 1；编辑器 pass 17 / fail 0**，17 张截图 | `browser/combo_summary.md`、`shots/combo/*.png` |

**「第九轮的 79 样例 / 17 组合结论没有被本轮改动破坏」这条断言成立**，而且是**结构性成立**：
本轮**只动了 gitignore 的探针脚本与文档**，没有触碰任何被测代码路径（`git status` 里
`target/probe/` 一律被 `git check-ignore` 命中、HEAD 仍是 `f27380c`），
两个套件都是**当轮重新跑真实 Chrome**、重新判定的，不是读旧结果。

**零漂移自证**：重跑 `component_matrix.py` 生成 79 个样例的新产物后，与第九轮落盘副本
（`round10_component_matrix_before.json`）逐字段比对，`chars` / `svg` / `katex` / `mathTag` /
`ok` / `warnings` / `missingMarkers` **全部 0 diff**——说明上游这几小时没有漂移，
浏览器套件的重跑结果与上一轮可直接对比。

#### ⑥ 第十轮仍未闭合

1. **D45 的技能文案仍未落库**（需重启应用触发 `SkillSeeder` upsert）——完整写在 §3.13⑦，本轮**未推进**（受「不改生产数据」约束）。
2. **D42 缺真实轮次对照**（同上：要重启 + 新建文章）。
3. **存量数据仍在**：19 篇 / 69 处烧域名 + 5 篇裂图（20 张 404 图 + 1 条坏链），本轮只读复核、未改。
4. **31 个组件没有真实稿件样本**（表 A/表 B 里真实稿件命中为 `—` 的那些）：它们只到 B 级（最小样例 + 真实浏览器）。
   要让它们升到 A 级，需要真的产出含这些组件的文章——**属内容侧工作量，不是缺陷**。
5. **元素形态的透传仍无通用判据**（`<steps>` / `<step>` 这类）：本轮的 `leakRaw` 是**按用例声明标记**判的，
   不是「扫出任意未注册元素」的通用规则；泛化它需要一份「已知合法元素白名单」，尚未做。
6. 第八轮 §3.12⑧ 与第九轮 §3.13⑥ 的其余各条（三层嵌套未覆盖、`:::hint` 与 `:::info` 是否同一、
   属性引号变体与 CRLF、79 样例里 75 条写法未回查官方定义）——本轮**没有推进**，原样保留。

---

### 3.15 2026-09-13 第十一轮：把「未落库」缺口钉成自动化证据 / 等上游 38 的独立交叉验证 / 表 B 定性

本轮只做了三件事：给第九/十轮那处真缺口补**源码侧自动化证据**、对「等上游 38 个 `layout-*`」
做一次**不用同一套 harness 的**交叉验证、把表 B 的 40 条非注册语法定性。
源码改动仅 1 个文件（`SkillSeeder.java` 的三处**事实性更正**）+ 1 个新测试类；
其余是测量与文档。**未 commit、未 push、未加依赖、未改生产数据、未重启应用。**

#### ① 缺口收口：源码侧有测试钉住；库侧给出可直接复制的复验片段

**新增测试 `SkillSeederMarkflowContentTest`（5 例，纯 JUnit，不启 Spring、不连库）**：

| 用例 | 钉住什么 |
|------|---------|
| `contentIsTheExpandedVersion` | 文案长度落在 `[6200, 7500]`（**第十一轮实测源码 `String.length()` = 6649**，库里旧版是 **5635**），且 1–5 条与主题色策略几段都在——**落回旧值附近即失败** |
| `layoutFamilyIsBannedWithFullFamilyEvidence` | `layout-*` 家族禁写名单；容器式 `:::layout-hero` 与标签式 `<layout-hero>` 都点名；**数字必须是「38 个名字 / 76 组」**；失败形态写作「原样留在产物里」；并给出「按 ⑥ 换写法」的指引 |
| `caseFlowDashRuleIsDocumented` | `:::case-flow` + 「行首的 `-`」+ `[标签]`；反例列出 `` `*` / `+` / `1.` ``；**「五种写法整块都是 0 字符」**；后果是静默归零 |
| `hintIsContainerOnly` | `:::hint` 是蓝色信息卡、**只有容器写法**；标签式「未知元素原样留在产物里」（屏幕上不显示） |
| `sliderClosingRuleIsDocumented` | 自闭合 `<slider … />` 被点名，且**明写与 `<badge>` / `<icon>` 方向相反** |

**为什么断言源码而不是库**：库里的内容是 `SkillSeeder` 在**应用启动时**按 `builtin_key` upsert 进去的
（§4.2 第 5 条），重启前库里**一直**是旧版——这是**预期状态**，不是缺陷。
若这条测试因为「库里是旧内容」而失败，说明断言对象写错了（去断言了库）。
库 ↔ 源码的一致性由 §八 第 7 条那组**只读**命令复核。

**顺带更正源码文案里三处与实测不符的表述**（本轮唯一的源码改动，都是事实更正，不是放宽判据）：

| 位置 | 改前 | 改后 | 依据 |
|------|------|------|------|
| 第 2 条 `layout-*` 名单 | 「这 **16** 个名字……**32** 组实测」 | 「注册表里共 **38** 个名字，容器式与标签式 **76** 组实测」 | 第十轮 `round10_registry_closure.py`（38 名字 × 2 写法全族实测）；第八轮只测了 16 个，写 16 会让模型以为剩下的不在名单里 |
| ⑥ `case-flow` | 「**四种**写法整块都是 0 字符」 | 「**五种**写法整块都是 0 字符」 | `round8_caseflow_bullet.txt` 实测归零形态是**五个**：`*` / `+` / `1.` / 行首漏 `-` / 漏 `[标签]`（`indented` 反而**不是**归零，547 字符） |
| ⑥ `hint` | 「标签会原样留在**正文里**」 | 「标签会作为**未知元素**原样留在**产物里**，屏幕上什么都不显示」 | 第十轮口径更正（见 §3.14①、`upstream-issues.md` R2-附）：产物是 `<p …><hint>…</hint></p>`，元素形态而非文本形态 |

**库端现状（本轮只读实测，与第九轮一致，未变）**：

```sql
SELECT CHAR_LENGTH(CONTENT), LOCATE('layout-*', CONTENT), LOCATE('76 组', CONTENT),
       LOCATE('行首的 `-`', CONTENT), LOCATE('只有容器写法', CONTENT),
       LOCATE('方向相反', CONTENT), UPDATED_AT
FROM SKILL WHERE ID = 4;
-- → 5635 | 0 | 0 | 0 | 0 | 0 | 2026-09-13 08:38:21.193584
```

⇒ 库里仍是 D43/D44/D45 之前的版本；**源码 6649 / 库 5635 的差值是 1014 字符**，
重启后 `db_len` 应当正好变成 **6649**、五个 `LOCATE` 全部 `> 0`。复验步骤见 §八 第 7 条。

#### ② 这条缺口的阻塞性：**不阻塞**「所有组件在两条路径都渲染正确」这条验收

判断依据三条（详版在 §八 第 7 条）：

1. **组装系统提示取的确实是库里的 `SKILL.CONTENT`**——`SkillPromptAssembler` 对 LAYOUT 维度拼的是
   `effectiveLayout.getContent()`（`SkillPromptAssembler.java:120`），`effectiveLayout` 来自
   `skillMapper.findByIds(...)`（第 159 行，读库）。**这一条成立，所以缺口的定位是准确的。**
2. **但它只影响「模型选哪种写法」，不影响两条路径的渲染能力**——受影响的写法
   （`layout-*` 全族、`:::success` / `:::danger`、标签式 `<hint>`、自闭合 `<slider … />`）
   **在上游本来就不支持**；改文案能让模型不去写，**不可能**让它们渲染成功。
   §3.14 的「25 个两路径均通过 / 38 个等上游」与技能文案无关。
3. **不落库的实际后果被保存侧自检兜住**——真写了那些写法，`save_article_draft` 的
   `markflowSyntaxHints` 自检（**已生效**，24 例测试钉住）会当场告警；
   且全库 44 篇真实稿件里这 6 种禁写写法的命中数**全是 0**（§3.14③ 只读扫描）。

⇒ 它是 **P1 的内容质量风险项，不是验收阻塞项**；下一次正常重启即自动闭合，不必为它单独安排重启。
（第十一轮把 §八 第 7 条的优先级从 P0 下调为 P1，依据就是上面第 3 条。）

#### ③ 等上游 38 个 `layout-*`：独立交叉验证（换第二、第三条证据通道）

**先解决「这 38 个 ID 是哪来的、别人怎么复现同一份清单」**：

| 步骤 | 具体 | 可复现性 |
|------|------|---------|
| ① 取引擎前端包 | 官网 `https://www.bx9y.com.cn/markflow/` 的线上 bundle（存档副本 `target/probe/mf_app.js`；本轮另按页面里的 `<script src="/markflow/assets/index-CCyOlIBZ.js">` 重新下载了一份 `round11_live_bundle.js`） | 打开该页面看 `script src` 即可拿到**当时的**包名 |
| ② 抽组件注册表 | `python target/probe/component_matrix.py --dump-registry` → `target/probe/component_registry.json`（**63 个 ID**，其中 `layout-*` **38 个**） | 脚本按 `{…Engage_DA02:"cta"…}` 这个可辨识的字面量定位，再用正则抓键 |
| ③ 独立复核同一份清单 | 不锚定那个对象，**直接在包里扫所有带引号的 `"layout-*"` 字面量** → 同样 **38 个**，与 ② 逐字相同 | `round11_crosscheck.py` 的通道 B |
| ④ 换一份包再抽一遍 | 本轮**重新下载的线上 bundle 是另一个构建**（sha256 `3d660b13…` vs 存档 `d3478dfe…`，字节数 651262 vs 641107），两条路径仍给出**同一份 38 个** | 通道 B2，见 `round11_crosscheck.txt` |

**三条互相独立的证据通道**（都不复用第十轮 harness 的判定代码）：

| 通道 | 做法 | 结果 |
|------|------|------|
| **A｜裸 HTTP 响应体** | 另写一份脚本，直接打印响应的 HTTP 状态、顶层键、`meta` 全文；判据换成「产物里有没有**以组件名命名的元素**」（标签式）与「可见文字里有没有字面 `:::`」（容器式），与 harness 的「标记子串 + 剥标签」口径不同 | 抽 **9 个 ID × 2 种写法 = 18 组，全部「上游未渲染」，0 存疑** |
| **B｜引擎包静态结构** | 在 `mf_app.js` 里统计 `layout-*` 的出现形态 | 38 处**全部带引号**；**不带引号的 0 处**（= 没有参与语法匹配）；正则字面量 `/layout-…/` **0 条**；`layout-x":{…}` 匹配分支 **0 条** |
| **C｜服务端实时 guide** | `GET /__markflow_render`（无 body）返回的语法指令里数这 38 个名字 | HTTP 200、guide **7672 字符**、38 个名字出现 **0 次** |

抽样覆盖两种写法与多个 category：`layout-hero`(intro) / `layout-toc`(structure) / `layout-metrics`(data) /
`layout-timeline`(data) / `layout-checklist`(content) / `layout-quote-card`(emph) /
`layout-image-compare`(image) / `layout-changelog`(other) / `layout-cards`(intro)。

**对照组（判据的反证）**：另跑 3 个**已知会被渲染**的写法（`:::breaking` / `:::callout type="tip"` /
`<badge type="tip" title="推荐" />`），同一套判据下**全部判成「上游已渲染」**——
没有对照就无法排除「判据一律判未渲染」。

> **一处当场发现的判据事故，值得记进 §4.2**：本脚本第一版把标签式的判据写成
> 「产物里有以组件名命名的元素 ⇒ **已渲染**」，跑出来 9 组「存疑」。
> 这个方向是**反的**——真被渲染的组件会变成自己的 `<section>` 结构，**绝不会**在产物里留下一个
> 以组件 ID 命名的元素；那个元素存在恰恰是被透传的证据。改对之后 18/18 确认。
> **是交叉验证本身抓出了这个错**（换一套判据写，就会暴露原判据里说不清的部分），
> 而**对照组**钉住了修正后的判据不是「一律判未渲染」。已写进 §4.2 第 11 条。

**结论：38 个全部确认「上游未渲染」，存疑 0 个。** 逐组数字见 `target/probe/round11_crosscheck.txt` / `.json`。

#### ④ 表 B 的 40 条非注册语法：定性

**一句话**：表 B 是「**不在引擎组件注册表的 63 个 ID 之内、但渲染器确实认**的可写语法」——
标准 Markdown 9 条（标题/列表/有序列表/任务列表/引用/表格/分隔线/代码/链接）、行内标记 13 条
（高亮/胶囊/靛蓝/辉光/粗斜体/下划线/删除线/上下标/行内代码等）、数学 3 条、Mermaid 1 条、
块级标签 5 条、`:::` 容器 4 条、属性变体 3 条、registry-only 2 条。

**为什么不在 63 之内**：那 63 个是引擎**前端组件库**（Web 端的组件选择器）能拖出来的「组件」；
表 B 里这些是**渲染器认的语法原语**——标准 Markdown 与行内标记根本不是「组件」，
数学/图表走的是另外的渲染分支，属性变体是同一组件的写法变体。
两者是**两套口径**，不是「漏掉的组件」。

**是否需要按「组件」标准验收**：**不需要**。表 B 的两条验收口径（后端产物 ok、编辑器往返 pass）
已经逐个跑过——**后端 35 ok / 5 not-rendered，编辑器 35 pass / 5 na / `fail` 0**。
⇒ **编辑器侧没有任何一条渲染异常，本轮没有新增缺陷。**
那 5 条 `na` 是上游没产出的那几种写法（`:::success`、`:::danger`、标签式 `<hint>`、
自闭合 `<slider … />`、`case-flow` 行首漏 `-`），其中 2 条（后两条）还是**故意写错的反例样例**，
用来当判据的哨兵；5 条都在「等上游」清单里逐条有替代写法（§3.14④）。

#### ⑤ 回归确认（本轮全部重跑）

| 项 | 数字 | 日志 |
|----|------|------|
| 后端全量测试（**含本轮新增的 5 例**） | **308 例 / 0 失败 / 0 错误 / 0 跳过 / BUILD SUCCESS，3:10**（303 + 5） | `target/probe/round11_tests.log` |
| 其中 `SkillSeederMarkflowContentTest` | **5 例 0 失败**（`Tests run: 5, Failures: 0`） | 同上 |
| `webui npm run build` | **✓ 407ms**（仅既有 >500 kB chunk 告警） | `target/probe/round11_webui_build.log` |

**「新增测试不会因为库里是旧版而失败」已在实测中确认**：全量 308 例全绿，而同一时刻库里仍是旧版
（`CHAR_LENGTH=5635`，五个 `LOCATE` 全 0，见 §3.15①）——正因为这条测试**不连库**，
它才既能在「未落库」的当前状态下通过、又能在源码被改回旧版时失败。这也反证了断言对象没写错。

---

### 3.16 2026-09-13 第十二轮：终稿验收报告 / 证据可复现性 / 当轮全量重跑

第十一轮把「未落库」缺口钉成了源码侧自动化证据、并对 38 个 `layout-*` 做了独立交叉验证。
第十二轮**不再新增判据**，只做两件收尾的事：把结论落成**可直接给人看的终稿**，
并把「换个人就复现不了」这件事解决掉——因为前十一轮的 harness、截图、汇总表**全在 `target/probe/`，
而 `target/` 被 `.gitignore:2` 命中**，等于证据链在版本控制里是断的。

#### ① 本轮新增的两份文档（都受版本控制）

| 文档 | 读者 | 回答什么 |
| ---- | ---- | -------- |
| `docs/render-acceptance-report.md` | **非工程师** | 一页说清「63 个组件里多少个两条路径都对 / 多少个不能渲染及原因 / 悬空多少」，公式与轮播单独给结论与证据，已知局限如实列，末尾给「能不能验收」的一句话判断与需要用户做的一个动作 |
| `docs/dev/render-verification.md` | 工程师 | **复现手册**：7 项前置条件（各带自检命令）、10 行套件总览、**逐条可复制命令**、每个套件的预期输出、当轮数字快照、`target/probe/` 里会丢的文件清单与长期归档建议、9 条已知的坑、以及这套方法本身的 5 条局限 |

`render-verification.md` 里明确写了「一个没参与过前几轮的人，照本文能独立重跑出与当前一致的结论」这条标准，
并把每一条命令的**预期数字**都写死（如 `component_matrix.py` 期望 `79 0 1`、终稿表期望
`{'registryTotal': 63, 'tierA': 13, 'tierB': 50, 'upstreamRows': 38, 'backendOk': 25, 'editorPass': 25, 'dangling': 0}`），
这样重跑的人不必先相信任何人。

**文件搬迁：本轮按要求只写建议、不搬动文件。** 手册第五节给了清单与去向
（驱动脚本 → `tools/render-verify/`，输入清单 → `tools/render-verify/spec/`，
汇总层证据 → `docs/dev/evidence/`），并写明「产出与截图不入库（体积）」「搬完必须按第三节完整重跑一遍才算搬成功」。

#### ② 当轮全量重跑（**没有复用任何旧数字**）

本轮把生成链从头跑了一遍：先重新打 4 组后端产物（79 样例 / 17 组合 / 10 替代写法 / 76 组 `layout-*`），
再重编探针 dist、重跑 5 个浏览器套件、重跑 5 个汇总脚本与终稿对照表、重跑独立交叉验证。

| 套件 | 第十二轮实测 | 与上一轮 |
| ---- | ------------ | -------- |
| 后端全量测试 | **308 例 / 0 失败 / 0 错误 / 0 跳过 / BUILD SUCCESS，2:45**（`round12_tests.log`） | 数字同，耗时波动 |
| `webui npm run build` | **✓ 405ms**（`round12_webui_build.log`） | 数字同 |
| 79 个最小样例（后端 79 行 / 0 异常 / 1 warning） | 编辑器 **pass 70 / na 9 / fail 0**（79 张截图） | 逐条一致 |
| 17 个组合用例 | 上游 **ok 8 / nested-unsupported 8 / silently-lost 1**；编辑器 **pass 17 / fail 0** | 逐条一致 |
| 10 条替代写法 | 后端 **ok 10 / 10**；编辑器 **pass 10 / 10 / fail 0** | 逐条一致 |
| 76 组 `layout-*` | 后端 **not-rendered 76 / 76**；编辑器 **na 76 / fail 0**（76 张截图） | 逐条一致 |
| 终稿对照表 | **63 / 13 / 50 / 38 / 25 / 25 / 悬空 0** | 逐条一致 |
| 独立交叉验证 | **18 组全部确认 / 0 存疑 / 对照组 0 误判** | 逐条一致 |
| 真实稿件套件 | 14 篇取数、**13 篇 ready**（第 5 篇为软删对照 D41） | 逐条一致 |

⇒ **本轮没有任何一条结论因为「上游漂移」而变化**：外部渲染 API 在两次重跑之间给出的产物没有可观测差异。
这一点本身就是复现性的一部分——同一份输入必须给出同一份判定，否则「复现手册」没有意义。

#### ③ 公式与轮播的当轮实测证据（用户最初点名的两个组件）

| 组件 | 稿件 | 后端 | 编辑器（真实 Chrome） | 截图 |
| ---- | ---- | ---- | -------------------- | ---- |
| 公式（KaTeX） | **#43** | HTTP 200 / success | `.katex` **5 个全部可见**，块级 **2** 个，高度 **[22,22,22,45,53]**、块级 **[45,53]**，**源码未泄漏**（正文无 `$$` / `\frac`），字体已加载 | `shots/articles/43.png` |
| 轮播（Slider） | **#44** | HTTP 200 / success | `<svg>` **1 个可见**、盒 **600×200**、viewBox `0 0 600 200`、**animateTransform 1**、**foreignObject 3**、内嵌 **3 张图 loaded 3/3**（盒 600×436，原始 1080×784） | `shots/articles/44.png` |

两篇都是**真实 MySQL 里的真实稿件**、走**真实 SPA 编辑器**，不是样例页。

#### ④ 计时口径核对（本轮专项）

第十一轮末尾把文档里的 `2:44` 统一改成了 `3:10`，本轮把全部形如 `N:NN` 的计时重新核了一遍：

- 用 `grep -oE "[0-9]+:[0-9]{2}"` 全量取出后逐类分拣——除了 5 处「耗时」外，其余全是**时钟**（如 `08:38`、`16:14`）与任务表里的时间戳，不是耗时，未做改动。
- 5 处耗时逐一与**各自的日志文件**对齐，**没有跨轮混用**：`2:51`↔`round8_tests.log`、`2:46`↔`round9_tests.log`、`2:48`↔`round10_tests.log`、`3:10`↔`round11_tests.log`、`2:45`↔`round12_tests.log`；`webui` 的 `434ms`/`407ms`/`405ms` 同理。全部 `grep` 实测，无一处对不上。
- 结论：**文档里每一处耗时都与它自己那一轮的实跑一致**；本轮新增的 `2:45` / `405ms` 已写进 §4.1 与 §4.3。

#### ⑤ 一致性核对（文档 ↔ 工作树 ↔ 脚本实际输出）

| 核对点 | 文档写的 | 实际 | 结论 |
| ------ | -------- | ---- | ---- |
| 组件总数 | 63 | `component_paths.json.registryTotal=63`；`component_registry.json` 独立数出 63 | 一致 |
| 等上游 | 38 | `upstreamRows=38`，且 tableA 里 38 行**全部** `layout-*`、判定 `not-rendered` | 一致 |
| 两路径通过 | 25 | `backendOk=25` / `editorPass=25`（13 个 A 级 + 12 个 B 级） | 一致 |
| 悬空 | 0 | `dangling=0`；76 组里 editor `fail=0` | 一致 |
| 测试总数 | 308 | `round12_tests.log`：`Tests run: 308, Failures: 0, Errors: 0, Skipped: 0` | 一致 |
| `SKILL` 库内仍是旧版 | 5635 字符 | 只读 SQL：`CHAR_LENGTH=5635`、`UPDATED_AT=2026-09-13 08:38:21`、五个 `LOCATE` **全 0**；源码常量 6649、五个关键片段各命中 1 次 | 一致（缺口仍在，未落库） |

⇒ 六项全部一致，**本轮未发现需要改文档的冲突**。

---

### 3.17 2026-09-13 第十三轮：拿自己写的复现手册当新人演练 / 端到端复现 / 报告可读性

第十二轮写了一份复现手册 `docs/dev/render-verification.md`。**但「写了一份手册」不等于「手册能用」**——
本轮换一个全新 shell、不带任何既有环境变量，**逐字照抄手册里的命令**跑一遍，用手册自己的标准检验手册。

#### ① 全新克隆演练：`git clone` 之后能复现多少

用 `git clone <仓库> /tmp/r13-clone` 造了一个真的干净副本，再逐条照抄：

| 照抄的命令 | 在全新 clone 里的实际结果 | 判定 |
| ---------- | ------------------------- | ---- |
| `./.mvn/mvn-local.sh -o test > target/probe/round12_tests.log 2>&1` | `bash: target/probe/round12_tests.log: No such file or directory`（重定向目标目录不存在） | **首条即挂** |
| `python target/probe/component_matrix.py` | `can't open file '.../target/probe/component_matrix.py'` | 挂 |
| `(cd webui && npx vite build --config ../target/probe/browser/vite.config.mjs)` | vite 配置不存在 | 挂 |
| `node target/probe/browser/run-all-browser.mjs` | `Cannot find module '.../run-all-browser.mjs'` | 挂 |
| `(cd webui && npm run build)` | `'vite' 不是内部或外部命令`（无 `node_modules`） | 挂 |
| 第一节的 7 条环境自检命令 | 全部可用 | **通过** |
| `./.mvn/mvn-local.sh -o test`（去掉重定向） | `Could not resolve placeholder 'ENV.MYSQL_TEST_URL'` → **205 例里 44 个 Error / BUILD FAILURE** | 环境缺口 |

**根因四条（实测，不是推断）**：

| # | 缺什么 | 证据 | 影响 |
| - | ------ | ---- | ---- |
| ① | `target/probe/` 全部脚本与样例清单 | `git ls-files target/probe` = **0 个文件** | **10 类探针套件全部复现不了** |
| ② | `.env`（`ENV.MYSQL_TEST_URL` 等） | `.env` 被 `.gitignore` 命中；clone 里跑测试报 placeholder 未解析 | 后端全量测试 |
| ③ | `webui/node_modules` | clone 里没有；需 `npm ci`（用仓库自带 lockfile） | 构建与所有浏览器套件 |
| ④ | **尚未提交的工作树改动** | clone（HEAD `f27380c`）测试类 **36** 个 vs 工作树 **45** 个；`ToolCallBudget` / `SkillBindingValidator` / `LlmLease` / `SkillSeederMarkflowContentTest` / `ScheduledArticleToolsDraftTests` / `ArticleLocalAssetUrlTests` 在 HEAD 上**都不存在** | **一切**——连手册与终稿报告本身今天也 clone 不到（都是未跟踪文件） |

⇒ **诚实结论**：手册的「照本文能独立重跑」这条标准，**目前只对「拿到完整工作树的人」成立**；
对「只 clone 了仓库的人」**不成立**。这不是可以含糊过去的事，已写进手册**第零节**（放在最前面，
而不是藏在末尾），并给出「需先补齐」的四步清单与「脚本入库、产物截图不入库」的建议
（**本轮仍按要求只写建议、未搬动或新增任何脚本**）。

#### ② 完整工作树上的照抄演练：21/21，预期数字逐条相符

在**已经有过若干轮验证的工作树**上，用全新 shell 逐字照抄第三、六节的全部命令：

| 检查点 | 手册写的预期 | 实跑 |
| ------ | ------------ | ---- |
| `component_matrix.py` 校验一行式 | `79 0 1` | **`79 0 1`** |
| 三套产物文件数一行式 | `[('combos',17),('alt',10),('registry',76)]` | **完全一致** |
| 五套截图张数 | 79 / 17 / 10 / 76 / 14 | **79 / 17 / 10 / 76 / 14** |
| 79 样例判定 | pass 70 / na 9 / fail 0 | **`{'pass': 70, 'na': 9}`** |
| 17 组合判定 | 上游 8/8/1；编辑器 17 pass | **`{'nested-unsupported':8,'silently-lost':1,'ok':8}` / `{'pass':17}`** |
| 10 替代写法 | 后端 10 ok / 编辑器 10 pass | **`{'ok':10}` / `{'pass':10}`** |
| 76 组 `layout-*` | 后端 76 not-rendered / 编辑器 76 na | **`{'not-rendered':76}` / `{'na':76}`** |
| 终稿对照表校验一行式 | `63/13/50/38/25/25/0` | **完全一致** |
| 交叉验证 | `groups=18 notRendered=18 doubtful=0 controlFP=0` | **完全一致** |
| 真实稿件覆盖 | 44 篇；70 项 39 命中 / 31 无；6 禁写全 0 | **44；39/31；0** |

⇒ **探针与汇总命令 21/21 可直接复制执行，且预期数字逐条相符**；`R` 脚本自己也打印
`summary: forms=76 unsupported=76 supported=0`，与手册一致。**没有一条预期数字过期。**

#### ③ 本轮修掉的 5 条文档缺陷

| # | 缺陷（手册原文） | 为什么会踩到 | 修法 |
| - | ---------------- | ------------ | ---- |
| 1 | §3.1 把日志重定向进 `target/probe/`，却没让人先建目录 | clone 场景首条命令失败 | 补 `mkdir -p target/probe` + 警告 |
| 2 | 前置条件里**没有 `.env`** | clone 里跑测试报 `ENV.MYSQL_TEST_URL` 未解析，44 个 Error，**看起来像代码回归** | 新增前置条件 8；§3.1 增「这不是代码回归」的判别说明 |
| 3 | 把 `PYTHONIOENCODING` 写成「不设会 `UnicodeEncodeError`」 | 实测**探针脚本不会崩**（落盘文件始终 UTF-8），只是 print 出的中文**值**变乱码；只有内联 `python -c` 打中文才会抛异常 | 改成准确表述 |
| 4 | 说「需要 `node_modules`」但没说怎么来 | clone 里 `npm run build` 直接失败 | 补 `(cd webui && npm ci)`，并注明用仓库自带 lockfile、不算新增依赖 |
| 5 | §3.4 五条串行命令贴在同一代码块 | 容易被一起并行执行，抢 9333 端口 | 拆成 5 个独立代码块 + 提醒 |

#### ④ 端到端验收演练（稿件 43 公式 / 44 轮播，两条路径各自独立取证）

路径一 = **真实渲染 API**（只读取库里的 `CONTENT_MARKDOWN` → `POST /__markflow_render`）；
路径二 = **真实编辑器**（`run-article.mjs` 在真实 Chrome 里量 DOM）。两条路径互不依赖。

| 稿件 | 路径一 · 后端渲染 API | 路径二 · 编辑器 DOM（真实 Chrome） |
| ---- | --------------------- | --------------------------------- |
| **43 公式** | HTTP **200** / `ok=true`；源文 166 字符 → 产物 **10266** 字符；`katex` 类名出现 **12** 次；可见文字里字面 `:::` **0** | `.katex` **5 个全部可见**（3 行内 + 2 块级），高度 **[22,22,22,45,53]**，块级 **[45,53]**，源码未泄漏，字体已加载，`ready=true` |
| **44 轮播** | HTTP **200** / `ok=true`；源文 196 字符 → 产物 **1437** 字符；`<svg>` **1** 个；可见文字里字面 `:::` **0** | `<svg>` **1 个可见**、盒 **600×200**、viewBox `0 0 600 200`、动画元素 **1**、foreignObject **3**、内嵌图 **3/3 加载**，`ready=true` |

⇒ 两篇稿件在**两条路径上都独立复现出「正确渲染」**，与第十二轮的结论一致。
**口径提醒**：路径一的 `katex` 12 次是 HTML 里 class 名计数，路径二的 5 个是渲染后真实 `.katex` 元素个数，
**两者不是一回事**，已写进手册第六节第 10 条避免误读。

#### ⑤ 终稿报告可读性检查（`docs/render-acceptance-report.md`）

按「非工程师能不能读懂」逐条过，**改了 4 处**：

| 问题 | 原文 | 改后 |
| ---- | ---- | ---- |
| **出现内部缺陷编号且无解释** | 「已知缺陷 D41」 | 去掉编号，改成「删除过的文章，打开时界面会一直转圈（一个已知的旧问题）」 |
| **未解释的术语** | 「jsdom」「渲染盒」「viewBox」「组件注册表」 | 分别改成「软件模拟浏览器（不开真浏览器）」「实际显示尺寸」「矢量图容器」「引擎自带的『支持哪些组件』清单」 |
| **缺少后端路径的数字** | 公式/轮播只给了编辑器侧证据 | 补上路径一的 HTTP 状态、源文/产物字符数、`<svg>` 数、字面 `:::` 残留数 |
| **「能验收」范围含糊** | 只写结论没说边界 | 新增局限第 6 条：明确「证明的是引擎支持的 25 个组件两条路径都正确 + 38 个不支持的已查清并给出替代」，**不等于**「任何写法都渲染正确」 |

核对项全部通过：无未解释术语 ✅、无裸露的内部编号 ✅、公式与轮播各有独立结论 ✅、
明确写了「已知局限」✅、明确写了「要用户做什么」（重启一次应用）✅、
「现在能不能验收」有明确判断（能）✅。

#### ⑥ 本轮数字

| 项 | 值 | 产物 |
| -- | -- | ---- |
| 后端全量测试 | **308 例 / 0 失败 / 0 错误 / 0 跳过 / BUILD SUCCESS，2:46**（303 + 第十一轮新增的 5 例） | `target/probe/round13_tests.log` |
| `webui npm run build` | **✓ 386ms**（仅既有 >500 kB chunk 告警） | `target/probe/round13_webui_build.log` |
| 探针侧全部套件 | 与第十二轮**逐条一致**（见 ②④） | `target/probe/round13_*.log` 等 |

**本轮未新增判据、未改任何判定口径**——修的全是文档与表述；也没有 commit / push（改动仍在工作树）。

---

### 3.18 2026-09-13 第十四轮：探针脚本入库 / 用户报的 43-44 渲染故障定位与修复 / 重启落库

这一轮**有两件互不相同的事**，都要记清楚：一件是把验证 harness 搬进版本控制（补上第十三轮发现的
最大缺口），另一件是**用户当场报的真实故障**——而后者恰好暴露了前十三轮验证方法的一处结构性盲区。

#### ① 探针脚本搬进 `tools/render-verify/`（受版本控制），产物仍留 `target/probe/`

**做法**：`mkdir -p tools/render-verify && cp …` 再 `git add`（文件原本不在版本控制里，`git mv` 用不了）。
搬运原则一句话：**脚本与输入入库，产物不入库**。

| 类别 | 去处 | 内容 |
| ---- | ---- | ---- |
| 生成器（Python，6 支） | `tools/render-verify/gen/` | `component_matrix.py`、`round8_combos.py`、`round10_alternatives.py`、`round10_registry_closure.py`、`round10_article_coverage.py`、`round11_crosscheck.py` |
| 浏览器驱动器（Node） | `tools/render-verify/browser/` | `cdp.mjs`、`run-*.mjs`、`verify-live-app.mjs`（本轮新增）、`summarize-*.mjs`、`probe*.html/js`、`editor-setup.js`、`legacyExtensions.js`、`probe.css`、`vite.config.mjs` |
| 终稿对照表脚本 | `tools/render-verify/` | `round10_component_paths.mjs` |
| **输入清单（定义基准）** | `tools/render-verify/spec/` | `component_registry.json`（63 ID）、`component_matchers.json`（29 匹配器）、`guide_recheck.md`、`engine/mf_app.js`（641 KB 引擎包存档） |
| 路径常量 | `tools/render-verify/` | `paths.py` / `paths.mjs`——**脚本内禁止裸相对路径** |
| 产物（截图/HTML/JSON/日志） | `target/probe/`（**仍 gitignored**） | 落点**与搬之前逐字节相同** |

`target/probe/` → `tools/render-verify/` 的**命令路径换了，但产物落点一个没动**——这是刻意的：
否则历史各轮的截图路径、汇总数字与复现手册里的预期值全部要重写。

**照新路径整体重跑一遍验证（判定数字与第十三轮逐条相符）**：

| 检查点 | 手册预期 | 第十四轮实跑（新路径） |
| ------ | -------- | ---------------------- |
| `gen/component_matrix.py` 校验 | `79 0 1` | **`79 0 1`** |
| 三套产物文件数 | `[('combos',17),('alt',10),('registry',76)]` | **完全一致** |
| 截图张数 | 79 / 17 / 10 / 76 / 14 | **79 / 17 / 10 / 76 / 14** |
| 79 样例判定 | pass 70 / na 9 / fail 0 | **`{'pass': 70, 'na': 9}`** |
| 17 组合判定 | 上游 8/8/1；编辑器 17 pass | **`{'nested-unsupported':8,'silently-lost':1,'ok':8}` / `{'pass':17}`** |
| 10 替代写法 | 后端 10 ok / 编辑器 10 pass | **`{'ok':10}` / `{'pass':10}`** |
| 76 组 `layout-*` | 后端 76 not-rendered / 编辑器 76 na | **`{'not-rendered':76}` / `{'na':76}`** |
| 终稿对照表 | 63/13/50/38/25/25/悬空 0 | **完全一致** |
| 交叉验证 | `groups=18 notRendered=18 doubtful=0 controlFP=0` | **完全一致** |
| 真实稿件覆盖 | 44 篇；70 项 39 命中 / 31 无；6 禁写全 0 | **44；39/31；0** |
| 真实稿件套件 | 14 篇取数、13 篇 ready | **14 / 13 ready** |

**搬运时踩的两个坑（已修，写下来省一次返工）**：

| # | 坑 | 修法 |
| - | -- | ---- |
| 1 | 探针入口 `probe.js` / `probe_all.js` 另有两个**同目录依赖** `probe.css`、`editor-setup.js`（后者又依赖 `./legacyExtensions.js`），漏搬直接 `UNRESOLVED_IMPORT`；`probe.css` 体积小容易被忽略 | 三个文件一并入库 |
| 2 | `.gitignore:2` 的 `target/` **没有前导斜杠**，命中**任意深度**的同名目录；搬运过程中在 `tools/render-verify/target/` 误建过目录，会被静默忽略 | 路径一律走 `paths.py` / `paths.mjs`，脚本内不留裸相对路径 |

> **第十三轮那条「只 clone 仓库的人跑不了任何一支探针套件」的结论，本轮正式作废**：
> 脚本、样例清单、组件全集基准、引擎包存档现在都在版本控制里；clone 之后只差三样环境
> （渲染令牌、`webui/node_modules`、`.env`），手册第零/第一节写清了怎么补。

#### ② 用户报的真实故障：`/articles/44` 轮播不生效、`/articles/43` 公式不生效

**这不是渲染能力问题，是「应用实际提供的前端」与「探针验证的前端」不是同一份构建。**

**取证**（三条互相独立，都不是猜）：

| 证据 | 量法 | 结果 |
| ---- | ---- | ---- |
| 应用对外的入口 | `curl -s http://127.0.0.1:8081/` 取 `assets/index-*.js` | **`index-CX6OBQPX.js`** = `target/classes/static/` 里那份（**9-11 构建**） |
| 磁盘上的最新构建 | `webui/dist/assets/` | **`index-ChPmeBR_.js`**（**9-13 构建**）——**应用根本没在用它** |
| 编辑器 chunk 的能力指纹 | 在入口里正则找 `ArticleEditorView-*.js`，再抓该 chunk 查 `rawSvg` / `rawMath` / `preservedEmptySpan` / `katex` | 旧包 `ArticleEditorView-9ZqsQRgK.js`：**四种标记全部 0 次**；新包 `ArticleEditorView-BfXgtpun.js`：**全部存在** |

`rawSvg` / `rawMath` / `preservedEmptySpan` 这三个名字只存在于 `webui/src/editorExtensions.js`，
打包后作为字符串常量活下来——**它们是「这份前端有没有公式与矢量图保真处理」的可靠指纹**。

**根因**：SPA 是 Maven `frontend-maven-plugin` 在 **`prepare-package` 阶段**构建进
`target/classes/static/` 的；而应用是用 **`spring-boot:run`** 起的，该 goal 只到 `test-compile`，
**不经过 `prepare-package`**。于是 `target/classes/static/` 停在几天前的构建，
`webui/dist` 却是最新的——**探针全绿，用户打开是坏的**。

**为什么前十三轮没发现**：`run-article.mjs` 是把自己起的静态服务指向 `webui/dist` 的
（见该文件 `DIST` 一行），**它量的从来不是应用实际提供的那份前端**。这是一处**验证方法的盲区**，
不是某一轮跑错了。

**修复与复验**：

```bash
# 1) 把最新前端构建进应用真正读取的目录（与 Maven 那条 npm 命令参数完全一致）
(cd webui && npm run build -- --outDir ../target/classes/static --emptyOutDir)

# 2) 重启应用（同时触发 SkillSeeder 的 upsert，见 ③）
./.mvn/mvn-local.sh -o spring-boot:run
```

**复验用的是真实 8081 页面**（不是探针自起的服务），用本轮新增的 `verify-live-app.mjs`：

| 稿件 | 活体页面实量（真实 Chrome，`http://127.0.0.1:8081/articles/<id>`） |
| ---- | ----------------------------------------------------------------- |
| **43 公式** | `.katex` **5 个全部可见**，高度 **[22,22,22,45,53]**（3 行内 + 2 块级），可见文字里无 `$$` / `\frac` 残留，`ready=true` |
| **44 轮播** | `<svg>` **1 个可见**、盒 **600×200**、内嵌图 **3/3 加载成功**、含 `animateTransform` 与 `foreignObject`，`ready=true` |

同时核对：重启后 8081 的入口已变成 **`index-ChPmeBR_.js`**、编辑器 chunk 为
**`ArticleEditorView-BfXgtpun.js`**、三个能力指纹**全部为真**。

> **防复发**：新增 `browser/verify-live-app.mjs`（直接打开 8081 量 DOM），与 `run-article.mjs`
> **量的不是同一份前端**，两支都要跑；并把「`spring-boot:run` 不跑 `prepare-package`」
> 写进复现手册已知坑第 13、14 条。

#### ③ 重启落库：第十一轮那条缺口关闭

重启触发了 `SkillSeeder` 的 upsert（按 `builtin_key`）。§八 第 7 条那组只读复核命令实测：

| 列 | 重启前（第十三轮实测） | **重启后（本轮实测）** |
| -- | ---------------------- | ---------------------- |
| `CHAR_LENGTH(CONTENT)` | `5635` | **`6649`**（= 源码 `MARKFLOW_CONTENT.length()`，与 `SkillSeederMarkflowContentTest` 钉的值一致） |
| `UPDATED_AT` | `2026-09-13 08:38:21` | **`2026-09-13 20:36:15`**（本次重启时刻） |
| 五个 `LOCATE` 关键片段 | 全 `0` | **全部命中** |

⇒ **该缺口关闭**，验收报告中「唯一需要用户动手的事」已消掉（报告第五节第 3 条、第六节已同步改写）。

#### ④ 本轮数字

| 项 | 值 | 产物 |
| -- | -- | ---- |
| 后端全量测试 | **308 例 / 0 失败 / 0 错误 / 0 跳过 / BUILD SUCCESS，2:54** | `target/probe/r14_tests.log` |
| `webui npm run build` | **✓ 393ms**（仅既有 >500 kB chunk 告警） | `target/probe/r14_webui_build.log` |
| 探针侧全部套件 | 与第十三轮**逐条一致**（见 ①） | `target/probe/r14_*.log` |
| 活体前端套件（新增） | 43 公式 5/5 可见、44 轮播 1 个 600×200 + 3/3 图 | `target/probe/browser/live_app_result.json` |
| `SKILL` 库内内容 | **6649 字符，与源码一致**（缺口关闭） | §八 第 7 条 |

**未改任何 Java 业务代码**（本轮改动集中在 `tools/render-verify/`、`docs/`、`.gitignore`）；
**未新增或放松任何判定判据**；**未 push**。

---

## 四、工程约束与验收方式（接手人须知）

### 4.1 构建与测试
- **必须用** `./.mvn/mvn-local.sh -o <goal>` 离线构建；`./mvnw` 会因 `JAVA_HOME` 未定义而失败，`mvn` 不在 PATH 上。`mvn-local.sh` 默认用 `/c/Program Files/Java/jdk-17`。
- 测试库（`wechat-article-test`）由**跨进程文件锁串行化**，**禁止并行跑测试**；Quartz 表由测试初始化器统一重建。
- 抽象基类要规避 smart-mybatis 的 MapperScan 扫描；需要真实 DB 的用例用 `@DirtiesContext` 隔离。
- **不要用 `grep` 直接搜应用日志里的中文**：ANSI 码会让 grep 判定为二进制，且 Git Bash 会弄坏中文编码——用 `grep -a` + ASCII 片段。
- 当前门禁（2026-09-13 **第十四轮**复测，含 D26–D29、D40–D45 的全部判据之后）：`./.mvn/mvn-local.sh -o test` 全量 **308 例 0 失败 / BUILD SUCCESS**（第十四轮重跑 2:54，日志 `target/probe/r14_tests.log`；第十三轮 2:46，日志 `target/probe/round13_tests.log`；第十二轮 2:45，日志 `target/probe/round12_tests.log`；第十一轮 3:10，**303 + 本轮新增的 5 例**（`SkillSeederMarkflowContentTest`，见 §3.15①），日志 `target/probe/round11_tests.log`；第十轮 303 例 2:48，日志 `target/probe/round10_tests.log`；第九轮 2:46，日志 `target/probe/round9_tests.log`；第八轮 2:51，日志 `target/probe/round8_tests.log`）。303 的构成（**第十一轮的 308 = 303 + 新增的 5 例 `SkillSeederMarkflowContentTest`，不连库的源码文案钉子，见 §3.15①**）：第八轮 +7（`ArticleLocalAssetUrlTests` 新增 6 例（D42）、`ScheduledArticleToolsDraftTests` 23→24（D44 新增 `unsupportedLayoutFamilyIsReported`，并扩展了 D43 的列表符号覆盖））；再往前 D40 把 `ScheduledArticleToolsDraftTests` 从 22 推到 23，D29 那例 21→22、D28 那例 20→21、D27 那例把 `MarkFlowRenderServiceTest` 从 17 推到 18、D26 那例把 `ScheduledArticleToolsDraftTests` 从 19 推到 20；第七轮 296（D41 是纯前端修复，没有新增 Java 用例）、第四轮 08:50 复测时是 295，07:10 那一轮是 294，F11 首轮 290、F10 首轮 287、F9 时是 284、F8 时 280，再往前 273 / 264 / 261 / 233。`webui npm run build` 零报错（仅既有 >500 kB chunk 体积警告；第十四轮 393ms、第十三轮 386ms、第十二轮 405ms、第十一轮 407ms、第十轮 434ms、第九轮 417ms、第八轮 393ms）。**第十轮另跑的浏览器套件**：79 样例 pass 70 / na 9 / fail 0（79 张截图）+ 17 组合编辑器 17/17 pass（17 张截图）+ **新增 layout-* 全族 76 组（后端 not-rendered 76 / 编辑器 na 76 / fail 0，76 张截图）+ 10 条替代写法（后端 ok 10 / 编辑器 pass 10 / fail 0，10 张截图）**，重跑前先 `npx vite build --config ../target/probe/browser/vite.config.mjs` 重建探针 dist。**第十一轮没有新增浏览器套件**，改为对 38 个 `layout-*` 抽样做**独立通道**交叉验证（9 个 ID × 2 写法 = 18 组，全部确认上游未渲染、0 存疑；3 个对照组 0 误判），见 §3.15③。**第十二轮没有新增判据，但把整条生成链从头重跑了一遍**（重新打 4 组后端产物 → 重编探针 dist → 重跑 5 个浏览器套件 → 重跑 5 个汇总脚本与终稿表 → 重跑独立交叉验证），**逐条判定与上一轮完全一致、无上游漂移**：79 样例 pass 70 / na 9 / fail 0、17 组合 上游 8/8/1 + 编辑器 17/17、10 替代写法 后端 10 / 编辑器 10、76 组 `layout-*` 后端 not-rendered 76 / 编辑器 na 76、终稿表 63/13/50/38/25/25/悬空 0、交叉验证 18/18。复现步骤与预期数字见 `docs/dev/render-verification.md`，见 §3.16②。**第十三轮照 `docs/dev/render-verification.md` 把探针侧全部重跑，逐条与第十二轮一致；并修掉 5 条文档缺陷、用 `git clone` 证明 clone 场景复现不了探针套件，见 §3.17。** **第十四轮把探针脚本与输入清单搬进受版本控制的 `tools/render-verify/`（产物仍留 `target/probe/`），照新路径整体重跑、判定数字逐条不变；同时定位并修掉用户当场报的 `/articles/43` 公式、`/articles/44` 轮播不显示——根因是「应用实际提供的前端（`target/classes/static`，`spring-boot:run` 不跑 `prepare-package`）≠ 探针验证的前端（`webui/dist`）」，并新增 `verify-live-app.mjs` 直接量 8081 堵住这处方法盲区；重启同时把技能文案落库（5635 → 6649），见 §3.18。**
- 跑的类名用逗号分隔（`-Dtest=A,B`）；`-Dtest=A+B` 会直接 BUILD FAILURE，别被这个假象误导。
- 实测两条与「渲染降级判据」相关的经验，改这块之前先看：① **判据的误报要靠真实产物兜**——D22 那次「24 个组件零误报」的结论在 D27 上被推翻（那批探针恰好都没写 HTML 实体）；② **D22 的 `renderWarnings:0` 不能读成「一切正常」**，只能说「没命中已知的几类降级」。
- ⚠️ **`CoreApiIntegrationTests.loginThenCreateAndQueryArticle` 会偶发失败**（2026-09-12 21:34 那次全量即挂在这一例）：报错是「等待浏览器工具调用超时」，`llmRequests=0`、SSE 只收到 heartbeat——即**这一例依赖真实 LLM 与浏览器工具链，网络一断就必挂**，与本轮改动无关（同一提交下一次复跑 52s 通过）。遇到全量挂在这一例，先单独复跑该例确认，不要当成代码回归。

### 4.2 验收原则（本项目已多次踩坑，务必沿用）
1. **测量类结论必须用真实令牌 + 真实数据库 + 真实浏览器**，不接受「看代码推断」。
2. **测量必须能被反证**：改回原状后测量要能复现缺陷（如 `DESCRIPTION_MAX_LENGTH` 反例、`data-render-id` 的 `git stash` 反例），否则不能排除「恰好都是绿的」。
3. **结论要核对结构，不能只看有没有样式**（D13 的教训）：量 DOM 结构、元素尺寸、computed style，必要时用 CDP。
4. **内联样式类改动用「声明计数 diff」**来量化，而不是截图目测。
5. **改了 `SkillSeeder` 里的内置技能文案，必须重启应用才生效**：`SkillSeeder.upsert` 只在启动时按
   `BUILTIN_KEY` 覆盖 `content` / `engine` / `engineConfig`，改完直接触发定时任务用的还是旧文案
   （2026-09-13 踩过：文章 27/28 仍停在 55 千字密度，重启后 run#75 才升到 87.2）。验证方式是
   `POST /api/skills/preview` 看组装出的系统提示里有没有新句子，而不是看数据库里的 `SKILL.CONTENT` 已经变了。
6. **模型自己写的「本次共委托 N 次 / 共 N 次工具调用」这类自述统计不可信，一律以执行日志与
   `stages_summary` 为准**。2026-09-13 实测：run#76 的运行说明自称「共涉及 **5** 次委托」，
   同一轮执行日志里的 `【协调】委托 #N` 是 **8** 次（#77 同样自称与实际不符）。模型的总结是**生成**的，
   不是从日志里读的；把它当数据用会直接污染测量结论。
7. **测量「某轮用了什么写法」要看落库源文，不要看模型的交付说明**：同一轮里模型会声称
   「使用了 6 种组件」，实际源文里可能只有 4 种。
8. **同一个指标出现在两张表里时，先确认分母是同一套口径**。2026-09-13 第四轮复查踩到：
   §3.8 ② 的「目标区间」是用 `density_check.py`（正文数 = `soup.get_text('', strip=True)`）
   从参考稿算出来的，而同一张表里的文章密度却用了另一套字数（2157 vs 重算的 1821），
   于是「文章 34 差 18%」这个结论**整个是无效比较**，真实差距是 4%。
   教训：**密度/占比这类比值，分母口径必须写进脚注，并在同一轮里用同一支脚本重算全部样本**；
   隔了几轮才回头比，先重算一遍再下结论，不要直接沿用表里的旧数。
9. **指标本身也会有假阳性，别把它当精确尺**：`density_check.py` 的「组件种类」列已证实
   把 `p-title` 数了两遍（名为 reading-path 的指纹实际命中的是 `p-title` 的章节号方块，
   而 `p-title` 又按 `data-block` 计了一次，见 `density_check_v2.py` 与 §3.8 ② 末尾）。
   用之前先抽一两个样本 dump 出实际命中的元素看一眼，别只看汇总数字。
10. **「产物非空 ≠ 渲染了」「编辑器 pass ≠ 渲染正确」**（2026-09-13 第八轮连踩两次，务必分开记）：
    ① **语法没被识别时，产物通常照样非空**——`:::danger` 的产物 569 字符、`<layout-hero>` 219 字符、
    `<slider … />` 303 字符，全都是「语法没被消费、原样留进产物」（容器式是可见文字里留着字面 `:::`，
    标签式是**未知元素原样透传**、屏幕上连字都看不见——见 §3.14① 的口径更正），样式齐全、`meta.warnings` 为空。
    连**剥标签再找关键字**都会漏判：`<slider … />` 的标签是**作为未知元素**留在 `<p>` 里的
    （产物原文 `<p …><slider images="…" /></p>`，`<`/`>` 未被转义），
    先 `soup.get_text()` 再找 `<slider` 找不到——泄漏检测必须扫**原始产物 HTML**（第七轮已踩，第八轮复发）。
    第十轮按原始产物复核时更正了这里的措辞：**不是「当文本输出」而是「当元素透传」**，
    屏幕上连一行字都看不见（见 §3.14① 的判据更正说明、`docs/dev/upstream-issues.md` R2-附）。
    ② **编辑器的 `pass` 只说明「拿到的产物被画对了」，不说明产物本身是对的**：
    组合用例 `cmb-callout-steps` 的产物 485 字符、外层 callout 的边框配色标题一应俱全、编辑器判定 `pass`，
    但**内层三个步骤标题一个字都没剩下**。参照侧用的是同一份残缺产物，「和自己比一致」永远是绿的。
    判据要能抓住这类缺陷，必须**与最小写法逐字对齐并点名缺了哪一条**（本例：990 字符差 + 步骤标题逐个点），
    而不是看「有没有样式」「产物长不为 0」「有没有报错」这三样。这与第 3 条是同一教训的第二次发作。
    ③ **判据本身也会「睡着了」**：第八轮组合用例的泄漏检测写的是 `:::[a-z-]+`，
    于是**光秃秃的收尾 `:::` 一处都数不到**——`cmb-timeline-table` / `cmb-caseflow-table-title`
    的可见文字结尾明明就挂着 `:::`，却被判成「0 处泄漏」，分类从 `nested-unsupported` 掉进 `silently-lost`
    （8 / 1 而不是 6 / 3，见 §3.12②）。**写判据时要把「失败的形态有几种」列全再写正则**，
    并且**每一条判据都要留一个「已知会失败的样例」当哨兵**——本轮那 17 个组合用例恰好就是干这个的。
11. **重要结论要换一条独立通道复核，并且必须带「对照组」**（2026-09-13 第十一轮现场踩出）：
    第十一轮对「38 个 `layout-*` 上游不支持」做交叉验证时，新写的判据**方向写反了**——
    把「产物里有以组件名命名的元素」当成**已渲染**，而它恰恰是**被透传**的证据（真渲染出来的组件
    会变成自己的 `<section>` 结构）。结果是 9 组「存疑」。
    两件事把它救了回来：① **换一套判据写**（不复用原 harness 的代码），没有这一步就不会重看这个量；
    ② **3 个已知会被渲染的对照组**（`:::breaking` / `:::callout` / `<badge>`）——没有对照组，
    「一律判未渲染」的坏判据和「真的都不渲染」的真结论**看起来一模一样**。
    推论：**凡是要拿去做验收结论的测量，都要同时在「已知会通过的样例」上跑一遍**；
    只有失败样例的绿不算绿。这与第 2 条（可反证）是同一件事的两面：第 2 条要求**证明缺陷能复现**，
    这条要求**证明判据不会误伤**。

### 4.3 当前提交状态
- **上一轮已提交并推送**：commit `5f88ca5`（2026-09-11），推送到 `huanyu-a/wechat-article-bot` 的 `main`。该提交含 63 个文件（22 新增 / 41 修改）——除四期修复外，也把此前多轮未提交的改动（ToolCallArgumentGuard、StaleRunPolicy/Reaper、DelegateTools 整轮预算、articles/agents 资源可往返等）一并纳入，因为它们是同一批未提交的工作树状态。
- **本轮（I1–I10 修复 + 第二批 F1–F5 + F7 + F8）尚未提交**：改动仍在工作树中（未 `git commit`、未 push）。新增主类 `common/LlmLease`、`common/LlmLeaseMapper`、`skill/SkillBindingValidator`、`schedule/ToolCallBudget`；修改 `common/InFlightGate`、`schedule/TaskExecutionService`（**F5/F7 的运行终态**）、`schedule/TaskRun`、`schedule/TaskRunMapper`、`schedule/StaleRunPolicy`、`schedule/CoordinatorExecutor`、`schedule/PipelineExecutor`、`schedule/AgentInvoker`（**F5 的收尾工具宽限与可执行超限提示、F8 的 `deliverableSubmitted` 判据**）、`schedule/TaskWorkspace`（**F7 的 `stages_summary.renderWarnings`**）、`ai/ArticleAiService`、`ai/SafeWebService`、`ai/ArticleMediaTools`、`ai/DelegateTools`（**F8 的 24→36 / 120→200 常量同步**）、`ai/ScheduledArticleTools`（**F4 的 `SaveMarkflowDraftTool`/`SaveMarkflowDraftParam`、F7 的 `markflowSyntaxHints`/`renderWarnings`、F8 的 `<timeline>` 提示**）、`agent/AgentProtocols`（预算提示）、`skill/MarkFlowRenderService`（**F7 的 `detectLeakedSyntax`/`parseWarnings`/`RenderResult.warnings`、F8 的 `detectDroppedBlocks` 判据校准**）、`article/ArticleService`（**F8 的重渲染降级写进版本说明**）、`article/ArticleMapper`（主题两列）、`article/ArticleController`、`article/ArticleContentPolicy`、`webui/src/editorExtensions.js`、`webui/.../ArticleEditorView.vue`、`resources/application.yaml`（**F8 的 `tool-calls` 四个值**）。
- 新增/扩充测试：`common/LlmLeaseMapperIntegrationTests`、`skill/SkillBindingValidatorTest`、`schedule/ToolCallBudgetTest`（7 例）、`schedule/AgentInvokerTest`（14 例）、`schedule/PipelineExecutorTest`（15 例）、`ai/ScheduledArticleToolsDraftTests`（**当前 22 例**）、`skill/MarkFlowRenderServiceTest`（**当前 18 例**）、`schedule/TaskRunCompletionTest`（8 例）、`article/ArticleRerenderTests`（13 例）、`article/MarkFlowArticleLayoutPersistenceTests`（8 例）、`ai/BrowseWebpageToolTests`，并扩充 `common/InFlightGateTest`、`schedule/StaleRunPolicyTest`、`StaleRunRecoveryIntegrationTests`、`ai/DelegateToolsTest`、`schedule/TaskWorkspaceTest`。
  （两个加粗数字是 2026-09-13 第四轮按源码 `@Test` 计数核对后的当前值——上面这段最初写的时候是 14 / 17，
  D26/D27/D28/D29 各补了用例；逐次增量见 4.1。同批还新增了 `schedule/TaskExecutionFinishTest`（F11），
  主类侧同批还改了 `skill/SkillSeeder`（F10 的容器清单、D28 的表格标题示例、D29 的 compare 示例）。）
  **第十一轮另新增** `skill/SkillSeederMarkflowContentTest`（5 例，见 §3.15①），并改了 `skill/SkillSeeder`
  的三处**文案事实更正**（`layout-*` 16 名字/32 组 → 38 名字/76 组；`case-flow` 四种 → 五种归零写法；
  `<hint>` 标签式的失败形态写成「未知元素原样留在产物里」）与 `ai/ScheduledArticleTools`、
  `ai/ScheduledArticleToolsDraftTests` 里引用的同两个数字（**只动注释与文案，未动任何判据正则**）。
- ⚠️ 提交前仍需按 4.2 做一次真实验收采样；提交时沿用既有密钥扫描习惯（`.env`、`data/`、`logs/` 均 gitignore）。
- **本轮已完成的验收采样（可直接作为提交依据）**：门禁按 4.1 的最新数字（**295 例 0 失败**，2026-09-13 08:50；07:10 时为 294）；F4 活体两例（SINGLE 文章 15、PIPELINE 文章 22，源文均为 MarkFlow 语法、`style="` 计数 0，主题色可改可落库）；F5 活体两例（run#63 审核阶段触顶后 `submit_review` 经宽限落盘；run#68 配图阶段触顶后 `set_article_draft_cover` 与 `save_article_draft` 均经宽限落盘，`degradations:0`、`saved:true`，且整轮是干净 `SUCCESS`）；F7/F8 判据在真实渲染产物上的复核（24 个组件的真实产物 + 线上文章 22 落库正文：**只有 `<timeline>` 命中、其余零误报**，见 3.5 F8）；F8 活体一例（run#69 PIPELINE 全轮 78 次工具调用、**0 次预算中止**，文章 23 为真精排、`renderWarnings:0`，见 3.5 F8）；**F10 活体一例 + 注入侧核验（run#71 PIPELINE，158 次工具调用、0 次预算中止、`saved:true`，产出文章 25；正文首次出现 `:::reading-path` 并渲染成编号圆点导航，对照文章 23 一个结构组件都没有；`POST /api/skills/preview` 组装出的 12224 字符系统提示确认含全部 9 个新补容器名，见 3.5 F10）**；**F10 第二轮活体（run#72，161 次工具调用、0 次预算中止，产出文章 26，用的是另一个新组件 `:::breaking`，渲染出完整的开篇大卡；同轮 `stages_summary` 与运行说明不再自相矛盾，见 3.5 F10/F11）**。
- **第七轮追加的验收采样（同样是提交依据）**：门禁 **296 例 0 失败 / BUILD SUCCESS**（2026-09-13，日志 `target/probe/round7_tests.log`）+ `webui npm run build` ✓ 433ms；
  **79 个样例全部**在真实浏览器里逐个判定（pass 70 / na 9 / fail 0 / unverified 0，79 张截图）；
  公式（文章 43）与轮播（文章 44）各走通「真实渲染 API → 真实 MySQL → 真实 SPA 编辑器」全链路；
  13 篇真实稿件（含软删稿 5）在真实 SPA 里复跑；D41 的红→绿各一次实测。
  逐条数字见 §3.11，原始产物在 `target/probe/`（该目录 gitignore，不进提交）。
- **第八轮追加的验收采样（同样是提交依据，2026-09-13）**：门禁 **303 例 0 失败 / BUILD SUCCESS**（日志 `target/probe/round8_tests.log`）+ `webui npm run build` ✓ 393ms；
  **17 个组合用例**（嵌套 / 属性交织 / 同组件多次 / 超长 / 行内混排）在真实浏览器里两级判定 —— 上游 ok 8 / nested-unsupported 8 / silently-lost 1，**编辑器 17/17 pass、0 fail**，17 张同框截图；
  **9 条 `na` 逐条实测复验 + 技能与自检核对**（`round8_na_discriminator.txt` / `round8_unknown_tags.txt` 32 组 / `round8_caseflow_bullet.txt` / `round8_callout_types.txt`），据此修掉 D43–D45；
  **D42 的红→绿反证**（`round8_url_fix_redgreen.txt`：把判据改回修复前 → 4 例失败且 `but was` 与库内 19 篇逐字节同形；改回后 6 例全绿）；
  **外部失效资源只读盘点**（44 篇 / 31 篇含绝对 URL / 62 个唯一外部 URL 探活 200×37·404×21·403×2·401×2 / 影响 5 篇 —— **未动任何数据**）。
  逐条数字见 §3.12，原始产物在 `target/probe/`（该目录 gitignore，不进提交）。
- **第九轮追加的验收采样（回归复查，同样是提交依据，2026-09-13）**：门禁重跑 **303 例 0 失败 / BUILD SUCCESS**（2:46，日志 `target/probe/round9_tests.log`）+ `webui npm run build` ✓ 417ms；
  79 样例套件与 17 组合套件**重新跑了一遍真实浏览器**（pass 70 / na 9 / fail 0；编辑器 17/17 pass），摘要文件已重生成；
  **泄漏判据两版对照**（`round9_leak_criterion_diff.txt`：差异 2/17、方向全变严、0 条变松）；
  **D42–D45 各一次红→绿**（`round9_redgreen.txt`，日志 `round9_d42_red.log` / `round9_d42_green.log` / `round9_d43_red.log` / `round9_d44_red.log` / `round9_d45_red.log` / `round9_d4345_green.log`）；
  **D45 技能文案未落库**已实测并如实记录（只读 SQL：`SKILL` 表 ID=4 里 `layout-` 与「行首的 `-`」均为 0 处）。
  逐条数字见 §3.13，原始产物在 `target/probe/`（该目录 gitignore，不进提交）。
- **第十轮追加的验收采样（组件清单收口，同样是提交依据，2026-09-13）**：门禁重跑 **303 例 0 失败 / BUILD SUCCESS**（2:48，日志 `target/probe/round10_tests.log`）+ `webui npm run build` ✓ 434ms；
  **`layout-*` 全族 38 个名字 × 2 种写法 = 76 组**逐个真打渲染 API 并把产物灌进真实 Chrome（后端 `not-rendered` 76 / 编辑器 `na` 76 / **`fail` 0**，76 张同框截图，`browser/registry_summary.md`）；
  **终稿对照表**（`round10_component_paths.md` / `.json`：注册表 63 个 ID 逐个 × 两条路径 + 表 B 40 条非注册语法）给出 **组件总数 63 / 两路径均通过 25 / 等上游 38 / 悬空 0**；
  **9 条「等上游」的 10 条替代写法**在两条路径上判定 —— 后端 ok 10 / 编辑器 pass 10 / **fail 0**（`browser/alt_summary.md`，含 `:::callout type=` 两个配色 `#16a34a`→`rgb(22,163,74)`、`#dc2626`→`rgb(220,38,38)` 的实际出现）；
  **A 级证据只读导出**（全库 44 篇 `CONTENT_MARKDOWN` → `round10_articles.tsv` → `round10_article_coverage.py`：70 个扫描项 39 有命中 / 31 无；**6 种禁写写法命中全为 0** —— **未动任何数据**）；
  **零漂移自证**（重跑 `component_matrix.py` 与上一轮落盘副本逐字段比对，79 个样例 `chars`/`svg`/`katex`/`mathTag`/`ok`/`warnings`/`missingMarkers` **全部 0 diff**）；
  79 样例套件与 17 组合套件**当轮重新跑真实 Chrome**（pass 70 / na 9 / fail 0；编辑器 17/17 pass）。
  逐条数字见 §3.14，原始产物在 `target/probe/`（该目录 gitignore，不进提交）。
- **第十一轮追加的验收采样（缺口钉死 + 独立交叉验证，同样是提交依据，2026-09-13）**：门禁重跑 **308 例 0 失败 / BUILD SUCCESS**（3:10 = 上一轮 303 + 本轮新增 5 例，日志 `target/probe/round11_tests.log`）+ `webui npm run build` ✓ 407ms；
  **新增 `SkillSeederMarkflowContentTest` 5 例全绿**（`Tests run: 5, Failures: 0`，纯 JUnit 不连库）；
  **库端只读复核**：`SKILL` ID=4 仍是 `CHAR_LENGTH=5635`、五个 `LOCATE` 全 `0`、`UPDATED_AT=2026-09-13 08:38:21`——**全量 308 例在这个状态下全绿**，反证新测试断言的是源码不是库；
  **38 个 `layout-*` 的独立交叉验证**（`round11_crosscheck.txt` / `.json`）：9 个 ID × 2 写法 = 18 组**全部确认上游未渲染、0 存疑**；3 个对照组（`:::breaking` / `:::callout` / `<badge>`）全部判成已渲染、**0 误判**；引擎包**零匹配分支**、guide **零提及**；**换一份新下载的线上 bundle 抽出同一份 38 个 ID**。
  逐条数字见 §3.15，原始产物在 `target/probe/`（该目录 gitignore，不进提交）。
- **第十二轮追加的验收采样（终稿报告 + 证据可复现性，同样是提交依据，2026-09-13）**：门禁重跑 **308 例 0 失败 / BUILD SUCCESS**（2:45，日志 `target/probe/round12_tests.log`）+ `webui npm run build` ✓ 405ms；
  **整条生成链当轮重跑、未复用旧数字**：后端产物重打（79 样例 79 行 0 异常 / 17 组合 / 10 替代写法 / 76 组 `layout-*`）、探针 dist 重编、5 个浏览器套件重跑（79+17+10+76+14 张截图）、5 个汇总脚本与终稿对照表重跑、独立交叉验证重跑——**逐条判定与第十一轮完全一致，无上游漂移**（79 样例 pass 70 / na 9 / fail 0；17 组合 上游 8/8/1 + 编辑器 17/17；10 替代写法 后端 10 / 编辑器 10；76 组后端 not-rendered 76 / 编辑器 na 76；终稿表 **63 / 13 / 50 / 38 / 25 / 25 / 悬空 0**；交叉验证 18/18、0 存疑、对照组 0 误判）；
  **本轮新增两份受版本控制的文档**：面向用户的终稿 `docs/render-acceptance-report.md`（一句话结论「能验收」+ 63/25/38/0 + 公式与轮播逐项证据 + 已知局限 + 需用户做的唯一动作：重启一次应用）、面向工程的复现手册 `docs/dev/render-verification.md`（7 项前置条件 + 10 行套件总览 + 逐条可复制命令 + 预期数字 + `target/probe/` 会丢文件清单与归档建议 + 9 条已知坑 + 5 条局限）；
  **计时口径专项核对**：全量 `grep` 出所有 `N:NN`，5 处耗时逐一与各自日志对齐（`2:51`/`2:46`/`2:48`/`3:10`/`2:45`），其余均为时钟或时间戳，**无一处跨轮混用**；
  **一致性核对六项全过**：63 / 38 / 25 / 悬空 0 / 测试 308 / `SKILL` 库内仍为 5635 字符，文档与工作树、脚本输出三者一致。
  逐条见 §3.16，原始产物在 `target/probe/`（该目录 gitignore，不进提交；归档建议见 `docs/dev/render-verification.md` 第五节）。
- **第十三轮追加的验收采样（复现手册实跑演练 + 端到端复现 + 报告可读性，同样是提交依据，2026-09-13）**：门禁重跑 **308 例 0 失败 / BUILD SUCCESS**（2:46，日志 `target/probe/round13_tests.log`）+ `webui npm run build` ✓ 386ms；
  **拿第十二轮那份复现手册当新人演练**：全新 shell 逐字照抄 → 探针与汇总命令 **21/21 可直接复制、预期数字逐条相符**（含截图 79/17/10/76/14、终稿表 63/13/50/38/25/25/0、交叉验证 `18/18/0/0`）；
  **`git clone` 反证**：`git ls-files target/probe` = **0 个文件** → **10 类探针套件在 clone 场景一条都跑不了**；无 `.env` → `ENV.MYSQL_TEST_URL` 未解析、**205 例 44 个 Error**；无 `node_modules`；HEAD 测试类 36 vs 工作树 45。结论「手册只对拿到完整工作树的人成立」已写进手册**第零节**并列出「需先补齐」四步（**只写建议、未搬文件**）；
  **修掉 5 条文档缺陷**（§3.1 重定向目录不存在 / 缺 `.env` 前置条件 / `PYTHONIOENCODING` 表述与实测不符 / 缺 `npm ci` / §3.4 串行命令易被并行）；
  **端到端演练（两条路径各自独立）**：稿件 **43 公式** 后端 HTTP 200 / 源文 166 → 产物 10266 字符，编辑器 `.katex` **5 个全可见**、高度 **[22,22,22,45,53]**、源码未泄漏、字体已加载；稿件 **44 轮播** 后端 HTTP 200 / 源文 196 → 产物 1437 字符 + `<svg>` 1，编辑器 `<svg>` 600×200 可见、动画 **1**、foreignObject **3**、内嵌图 **3/3**；
  **终稿报告可读性**逐条过并改写 4 处（去掉裸缺陷编号 D41、解释 jsdom/渲染盒/viewBox/组件注册表、补后端路径数字、给「能验收」加范围边界）；
  **未新增判据、未改判定口径**。详见 §3.17

---

## 五、现场证据与根因（吸收自并行会话 `sess_457cd5ec` 的只读采集）

> 本节把 [agent-session-timeout-handoff.md](agent-session-timeout-handoff.md)（2026-09-11 16:14–17:30 只读采集）的实体内容合并进来，避免两份文档并存。**原始文件仍保留**——该会话 17:42 还在编辑它，且它自己的「当前状态」类结论以其标注时刻为准。

### 5.1 根因（已确立）

1. 网关 `https://nexus.bx9y.com.cn/v1/chat/completions`（`PROVIDER=OPENAI_COMPATIBLE`，模型 `agnes-3.0-flash`）**间歇性地不发流结束信号**：既无 `[DONE]` 也无 `finish_reason`。
2. agent4j 2.3.3 **既无读超时也无 cancel/close**：`LLMResult.get()` 阻塞在 `future.get()`，只有流正常结束才会完成；流被掐断且没有 `onFailure` 回调时**永久挂住**。
3. 网关并发上限 max=6，被放弃的会话其 SSE 流仍然开着、继续占名额 → 「卡一个 → 少一个名额 → 下一个更易卡」的级联（`#26` 原文 `HTTP 429 concurrent limit exceeded: running=7 max=6`）。
4. `StageTimeout` 在 1800 秒时**按设计工作**，但它只是把永久 RUNNING 变成 FAILED，**不是治因**。

**已排除**（省得重复怀疑）：`OpenAIChatModel` 对 `reasoning_content` 的处理正确；TLS 证书正常（有效期 2026-08-11 → 2026-11-09，早期的证书告警是 Python 侧本地 CA 问题）；与请求体大小无关（小请求同样命中）；`AgentProtocols.RESEARCH` 的提示词与工具集正常——**调研员是受害者，不是缺陷源**。

### 5.2 运行 #33（用户报告的那次）完整结论

| 项 | 值 |
|---|---|
| 任务 | task#2「每日科技早报」，MODE=PIPELINE，MANUAL |
| STARTED / FINISHED | 2026-09-11 15:47:38.125 → 16:17:38.565（**正好 1800 秒**） |
| STATUS | FAILED |
| TOOL_CALL_COUNT | **26** |
| EXECUTION_LOG | `【调研】启动智能体：调研员`（只有 1 行） |
| MESSAGE | 智能体会话超时（1800 秒未结束） |

**一处需要更正的早期判断**：`#33` **不是**卡在调研员的第一次调用。它已完成约 26 轮工具调用，是**后面某一次**调用停滞耗尽了阶段预算。对比 `#29`（首个调用就卡、0 工具调用、日志 39 字符）——**同一根因、两种命中点**，说明停滞与「第几次调用」无关，是概率性的。

「26 次工具调用 / 1 行日志」的割裂原因：`PipelineExecutor.runStage` 只在 `runWithLimit` **正常返回后**才合并阶段日志，抛异常时整批丢失，而计数走实时回调所以幸存。该缺口已由 `ProgressListener` 补上（见 D3），**#33 跑在补丁前的实例上**。

### 5.3 活体线程栈（唯一一次抓到正在卡住的状态）

`jstack 24700` @ 16:14:08，PID 24700，**455 行 / 43 线程**：

```
"agent-stage调研" #121 daemon，已存活 1589.90s，WAITING (parking)
  CompletableFuture.get → LLMResult.get(LLMResult.java:77)            ← 卡点
  AgentClientSession.executeCommand(AgentClientSession.java:165)
  AgentSessionResult.execute(AgentSessionResult.java:66)
  StageTimeout.lambda$await$0(StageTimeout.java:49)

"ForkJoinPool.commonPool-worker-5" #117（护栏线程，存活 1590s）
  StageTimeout.await(StageTimeout.java:58) → AgentInvoker.awaitStage(:125) → run(:106)
  PipelineExecutor.runStage(:154) → execute(:70)     ← 第①步「调研」
  TaskExecutionService.executeRun(:143)

"ForkJoinPool.commonPool-worker-3" #67，存活 4730.53s（≈79 分钟，约 14:55 起）
  LLMResult.get(LLMResult.java:77)
  AgentClientSession.executeCommand(AgentClientSession.java:165)
  ArticleAiService.executeWithAgentSession(:281) → execute(:218)   ← 编辑器会话，无 StageTimeout 护栏，持锁中
```

判读结论：阻塞在 `LLMResult.get` 的线程数 = **2**，即当时有 2 个网关名额被停滞会话白占；整份 dump 里 **OkHttp 读线程 = 0**，说明流已无人读取、也不会有人唤醒 future。

**闸门时效性提醒**（解释 `#33` 为什么看起来「没有闸门」）：`InFlightGate` 源码 15:26:22、class 编译 16:10:04，而当时在跑的 JVM 约 14:33 启动（`elapsed≈6072s`）、dump 里 `InFlightGate` 出现 **0 次**——**#33 跑在闸门之前**。又因 agent4j 没有 cancel，**重启进程是回收被占名额最快的办法**。

### 5.4 实施后的新症状：止损变快，但可恢复性没有

`#35/#36/#38` 三次 SINGLE 运行都在 **300 秒准时失败**：

| run | 时段 | 工具调用 | 卡点（新增诊断字段） |
|---|---|---|---|
| #35 | 16:32:59 → 16:37:59 | 1 | 最后活动为「收到模型输出 **2** 字符」，距今 0 秒 |
| #36 | 16:58:17 → 17:03:18 | 1 | 最后活动为「收到模型输出 **2** 字符」，距今 67 秒 |
| #38 | 17:08:44 → 17:13:44 | 1 | 最后活动为「收到模型输出 **0** 字符」，距今 0 秒 |

读法：模型吐出 0–2 个字符后流就断了——正是「SSE 流响应中途停滞」的形态，与 5.3 的线程栈指向同一根因。区别只是现在 5 分钟止损、而不是白等 30 分钟。**这三条与 #33 一样，最终都以「整次运行失败」收场。**

### 5.5 现场状态（17:27，供判断时效）

应用已重启（JVM PID 41352），**无任何线程阻塞在 `LLMResult.get`**、无 RUNNING 运行；网关探测 4/4 正常（0.64–2.40 秒）。即系统当时空闲且健康——**问题属间歇性发作，不是常态故障**。这也意味着任何「修好了」的结论都需要多轮采样才能支撑。

---

## 六、可复用的现场诊断手法

### 6.1 抓活体线程栈（唯一能看到「卡在哪一次调用」的手段）

```bash
"/c/Program Files/Java/jdk-17/bin/jps.exe" -l -v     # 找真实 PID（不要用 Maven wrapper 的 PID）
"/c/Program Files/Java/jdk-17/bin/jstack.exe" <pid> > dump.txt
grep -a "agent-stage\|LLMResult.get\|StageTimeout.await\|InFlightGate" dump.txt
```

判读要点：

- 停在 `LLMResult.get(LLMResult.java:77)` = agent4j 在等模型响应；
- **若同时没有任何 OkHttp 读线程**，说明流已被掐断且无人回调，future 不会完成（`#33` 即此形态）；
- `grep -c "LLMResult.get" dump.txt` 的计数 = 当前被停滞会话占掉的 LLM 名额数；
- **必须在真正卡住时抓**——重启过的 JVM 里现场就没了。

### 6.2 判断「运行中的实例是哪个版本」

`jps -l -v` 拿真实 PID 与启动时长，再把 `target/classes` 里新类的 mtime 与实例启动时刻比对（或直接在线程栈里 grep 新类名）。并行会话就是靠这个发现 `#33` 跑在闸门之前：源码 15:26 / class 16:10，但实例 14:33 启动、栈里 `InFlightGate` 出现 0 次。

### 6.3 探测网关是否在劣化（间歇性，需多轮采样）

- 解密 `LLM_PROFILE.API_KEY_ENCRYPTED`：`key = SHA-256(APP_SECRET_KEY)`，AES/GCM/NoPadding，Base64 解码后前 12 字节为 IV。
- 用 JDK `HttpClient` 发流式请求，**按是否收到 `[DONE]` 判定**；重复 8 次可量化停滞率。
- 实测样本：某轮 `ok=7 hung=1`（其中一次 >40 秒）、延迟 0.76–19.4 秒；10 分钟后同脚本 8/8 正常（0.7–1.9 秒）；再测 `ok=4 hung=0`（0.6–2.4 秒）。
- 也可用 `curl -s -N` 探测，但要留意 Python/本机 CA 造成的假告警（证书本身正常）。
- ⚠️ **不要把解密出来的 API Key 写进任何文档或提交**——这段只在临时文件里用，用完删掉。

### 6.4 直查开发库

```bash
docker exec momo-mysql-dev mysql -uroot -p"$PW" -D wechat-article -e "SELECT ... FROM TASK_RUN WHERE ID IN (...) \G"
```

`$PW` 取自 `.env` 的 `MYSQL_PASSWORD`；表名全大写；会话时区是 UTC；等号两边不要有空格（否则 shell 会把路径粘进库名）。注意 `LLM_PROFILE` 的密钥列名是 **`API_KEY_ENCRYPTED`**（排查时曾用错列名）。

### 6.5 观测缺口（与 I10 同源）

`TASK_RUN` 的 `MODE`、`EXECUTION_LOG`、`TOOL_CALL_COUNT` 都只在收尾时落库，所以 **RUNNING 行永远是 `MODE=NULL`、日志为空、计数 0**——运行期间想知道进度只能抓线程栈。

---

## 七、相关文档地图

| 文档 | 内容 |
|------|------|
| `docs/dev/agent-session-timeout-plan.md` | 429 级联停滞的根因与四期修复方案；文末「八、实施记录」含本轮逐期落地物、与初稿的偏差（闸门加在运行而非会话，附字节码证据）、实机验收证据表与遗留 5 条 |
| `docs/dev/agent-session-timeout-handoff.md` | 并行会话（只读）的现场证据原文；**实体内容已合并进本份第五、六节**（含 run#33 活体线程栈、编辑器链路第二处泄漏、jstack 判读要点与网关停滞率探测手法）。原文件保留是因为该会话仍在编辑它 |
| `.zcode/handoff/main-live-evidence.json` | 并行会话的旁路 JSON 记录——它**有意没有覆盖** `latest.json`（默认视为本会话所有） |
| `docs/dev/skills-agent-plan.md` | Skills/Agent 体系方案（1413 行）；「实施记录」按轮次（十二～十九轮等）自检，第三节各条遗留的原始表述在此 |
| `docs/dev/skills-agent-plan-review.md` | 方案评审报告（只读产物）；含 MarkFlow 落地补充点与方案未覆盖项，多数已在后续轮次落地（见第二节「不属于遗留」表） |
| `target/probe/browser/all_summary.md` | **79 个样例逐个的真实浏览器判定表**（pass 70 / na 9 / fail 0），含每行的写法、字数、盒子数、结构特征对照与 `shots/all/<id>.png` 截图链接；判定口径与 na 逐条复验理由都在里面。原始量 `all_result.json`，生成脚本 `run-all-browser.mjs` + `summarize-all.mjs` |
| `target/probe/browser/articles_result.json` | 13 篇真实稿件在真实 SPA 里的实测（公式 / 轮播 / 在库回归 / 软删稿），截图 `shots/articles/<id>.png`，脚本 `run-article.mjs` |
| `target/probe/round7_e2e.json` / `.md` | 第七轮两篇探针文章（43 公式、44 轮播）的 Markdown 源文、渲染 API 返回值与落库实测数字，脚本 `round7_e2e.py` |
| `target/probe/browser/combo_summary.md` | **第八轮 17 个组合用例的两级判定表**（上游 ok 8 / nested-unsupported 8 / silently-lost 1；编辑器 pass 17），含每例的写法、上游产物特征、**与「单独最小写法」的逐项差异**与 `shots/combo/<id>.png` 同框截图。原始量 `combo_result.json`，脚本 `round8_combos.py` + `run-combo-browser.mjs` + `summarize-combos.mjs` |
| `target/probe/round8_na_discriminator.txt` | **9 条 `na` 逐条复验的原始产物**（字符数 / 可见文字 / 泄漏标记 / warnings），16 个用例直接打真实渲染 API；泄漏检测扫**原始产物 HTML**（先剥标签会漏判，见 §4.2 第 10 条） |
| `target/probe/round8_unknown_tags.txt` | `layout-*` 家族 **32 组全量实测**（16 个名字 × {容器式, 标签式}），结论：全部把语法原样留在正文 |
| `target/probe/round8_caseflow_bullet.txt` | `case-flow` 的 **7 种列表符号 × {容器式, 标签式}** 实测，结论：只有「行首 `-` + `[标签]`」有产出 |
| `target/probe/round8_callout_types.txt` | `:::callout type=` **11 种取值**逐个实测（含 `error` / `bogus` / 不写），结论：无白名单，未知值静默回退 `info` |
| `target/probe/round8_dead_links.md` / `.json` | **外部绝对 URL 只读盘点**：31 篇含绝对 URL / 32 个域名 / 62 个唯一外部 URL 探活结果 / 按位置与逐篇分布。脚本 `round8_dead_links.mjs`，配套 `round8_url_status.txt` |
| `target/probe/round8_url_fix_redgreen.txt` | **D42 的红→绿反证原文**：把 `LOCAL_ASSET_SRC` 改回修复前 → 4 例失败且 `but was` 与库内 19 篇逐字节同形；改回后 6 例全绿 |
| `target/probe/round9_tests.log` | **第九轮全量测试日志**（303 例 0 失败 / BUILD SUCCESS / 2:46），与第八轮同数字——第九轮只做回归复查，未新增用例 |
| `target/probe/round9_leak_criterion_diff.py` / `.txt` | **泄漏判据两版自证**：`:::[a-z-]+`（只会漏认裸收尾）vs `:::` 对**同一批落盘产物**各跑一次，逐条差异表 + 方向核对（2/17 变严、0 条变松、0 条编辑器侧误伤） |
| `target/probe/round9_redgreen.txt` | **D42–D45 四条的红→绿反证原文**（本轮重跑）：每条给「改前会怎样 / 改后现在怎样」+ 命令、退出码、断言原文、日志路径；末段附与第八轮的数字对照，并明确记下 D45 技能文案**未落库** |
| `target/probe/round9_d42_red.log` / `round9_d42_green.log` / `round9_d43_red.log` / `round9_d44_red.log` / `round9_d45_red.log` / `round9_d4345_green.log` | 第九轮红绿跑的各次完整输出（D43/D44/D45 共用一次绿跑 `round9_d4345_green.log`） |
| `target/probe/round9_all_browser.log` | 第九轮重跑 79 样例套件 + 17 组合套件的真实浏览器输出（含重建探针 dist 的那一步） |
| `docs/dev/upstream-issues.md` | **只记「上游（渲染服务 / agent4j / 网关）的根因」的台账**——本项目侧不越界改上游，凡属上游的结论一律落在这里而不写进本份的缺陷索引。含 `layout-*` 全族（第十轮扩到 38 个名字 × 2 种写法 = 76 组）、`:::callout type=` 无白名单、嵌套组合的两种新形态等 |
| `target/probe/round10_component_paths.md` / `.json` | **第十轮对照表终稿**：注册表 **63 个 ID × 两条路径**逐格（表 A）+ 非注册语法 40 条（表 B），每行带覆盖样例、真实稿件命中文章号、证据文件路径；含「组件总数 / 两路径均通过 / 等上游 / 悬空」四个汇总数与三个验证层级的计数。生成脚本 `round10_component_paths.mjs`（输入全是实测产物） |
| `target/probe/browser/registry_summary.md` / `registry_result.json` / `shots/registry/` | **`layout-*` 全族 76 组（38 名字 × 容器式/标签式）的两条路径判定**：后端 `not-rendered` 76 / 编辑器 `na` 76 / **`fail` 0**，76 张同框截图。用例源 `target/probe/registry/registry.json`，脚本 `round10_registry_closure.py` + `run-set-browser.mjs registry` |
| `target/probe/browser/alt_summary.md` / `alt_result.json` / `shots/alt/` | **9 条「等上游」的 10 条替代写法判定**：后端 ok 10 / 编辑器 pass 10 / **fail 0**，10 张同框截图；含 `:::` 残留数、文字与盒子对照、配色实测（`#16a34a`→`rgb(22,163,74)`、`#dc2626`→`rgb(220,38,38)`）与输入原文。用例源 `target/probe/alt/alt.json`，脚本 `round10_alternatives.py` + `summarize-alt.mjs` |
| `target/probe/round10_registry_closure.txt` / `.json` | 76 组 `layout-*` 的后端产物量（字符数 / 可见文字长度 / `leakRaw` / `leakText`），逐行可查；含 `NON_LAYOUT_COVERED` 表（25 个非 `layout-*` 注册 ID 各自由哪个样例覆盖） |
| `target/probe/round10_article_coverage.md` / `.json` + `round10_articles.tsv` | **A 级（真实稿件）证据来源**：全库 44 篇 `CONTENT_MARKDOWN` 的只读导出（含软删 6 篇）与逐组件正则命中表——70 个扫描项 39 有命中 / 31 无；**6 种「禁写」写法命中全为 0**。脚本 `round10_article_coverage.py`（只读） |
| `target/probe/round10_component_matrix_before.json` | 第九轮落盘的 79 样例产物流水副本，用于**零漂移自证**（重跑后逐字段比对，全部 0 diff） |
| `target/probe/round10_tests.log` / `round10_webui_build.log` | **第十轮全量测试日志**（303 例 0 失败 / BUILD SUCCESS / 2:48）与 `webui npm run build` 输出（✓ 434ms） |
| `target/probe/round10_all_browser.log` / `round10_combo_browser.log` / `round10_registry_browser.log` | 第十轮三个浏览器套件（79 样例 / 17 组合 / 76 组 `layout-*`）的真实 Chrome 原始输出 |
| `src/test/java/ink/icoding/wechat/article/skill/SkillSeederMarkflowContentTest.java` | **第十一轮新增的源码文案钉子**（5 例，纯 JUnit **不连库**）：钉住 `layout-*` 全族禁写、`case-flow` 行首 `-`、`:::hint` 只有容器写法三段与「38 名字 / 76 组 / 五种写法」三个数字，并断言文案长度在 `[6200, 7500]`（实测 6649；库里旧版 5635）。**它证明「源码已改」，库是否已落由 §八 第 7 条复核** |
| `target/probe/round11_crosscheck.txt` / `.json` + `round11_crosscheck.py` | **第十一轮对 38 个 `layout-*` 的独立交叉验证**：三条独立通道（裸响应体 / 引擎包静态结构 / 服务端 guide）+ 9 个 ID × 2 写法抽样 + 3 个对照组；含引擎包与 `round11_live_bundle.js` 的 sha256 与抽取结果（两份不同构建给出同一份 38 个 ID） |
| `target/probe/round11_live_bundle.js` | 第十一轮**重新下载**的官网线上 bundle（`/markflow/assets/index-CCyOlIBZ.js`，sha256 `3d660b13…`，与第五轮存档的 `mf_app.js` `d3478dfe…` **不是同一个构建**），用于复现同一份 38 个 `layout-*` 清单 |
| `target/probe/round11_tests.log` / `round11_webui_build.log` | **第十一轮全量测试日志**（308 例 0 失败 / BUILD SUCCESS / 3:10）与 `webui npm run build` 输出（✓ 407ms） |
| `docs/render-acceptance-report.md` | **第十二轮新增 · 面向用户的终稿验收报告**（非工程师可读）：一句话结论「能验收」、组件总数 63 / 两路径通过 25 / 不能渲染 38（全部归因上游未实现）/ 悬空 0、公式与轮播的逐项证据（稿件 43、44）、已知未覆盖与局限、需用户做的唯一动作（重启一次应用） |
| `docs/dev/render-verification.md` | **第十二轮新增 · 渲染验收复现手册**（硬要求交付物）：7 项前置条件（各带自检命令）、10 行套件总览（代码门禁 2 / 后端产物 4 / 真实浏览器 1 行含 5 个套件 / 汇总 1 / 交叉验证 1 / 真实稿件 1）、逐条可复制命令与预期输出、当轮数字快照、`target/probe/` 中会随 `target/` 被清掉的文件清单与长期归档建议（本轮只写建议、未搬动）、9 条已知的坑、5 条方法局限 |
| `target/probe/round12_tests.log` / `round12_webui_build.log` | **第十二轮全量测试日志**（308 例 0 失败 / 0 错误 / 0 跳过 / BUILD SUCCESS / 2:45）与 `webui npm run build` 输出（✓ 405ms） |
| `target/probe/round13_tests.log` / `round13_webui_build.log` | **第十三轮全量测试日志**（308 例 0 失败 / 0 错误 / 0 跳过 / BUILD SUCCESS / 2:46）与 `webui npm run build` 输出（✓ 386ms）——按复现手册照抄重跑所得 |
| `target/probe/round12_matrix.log` / `round12_probe_regen.log` / `round12_probe_dist.log` / `round12_browser_run{1,2,3}.log` | **第十二轮生成链重跑的原始输出**：后端产物重打（79 行 0 异常）→ 17 组合 / 10 替代写法 / 76 组 `layout-*` 重打 → 探针 dist 重编 → 三个浏览器批次 → 汇总与终稿表 → 独立交叉验证，逐条判定与第十一轮一致 |
| `target/probe/browser/editor-setup.js` | 探针的**共享扩展集与测量模块**——14 组对照与 79 组扫测用的是同一份，测量口径不会两边漂 |
| `.zcode/handoff/latest.json` | Handoff 协议的结构化记录。**注意：该文件为并行 ZCode 会话共享的单文件**，若你在另一个会话工作，请先读再写，避免覆盖对方的记录 |

---

## 八、建议的下一步（按性价比排序）

> I1–I10 已于 2026-09-11 落地（见 3.2）。以下为尚存事项。

1. **U1 / U3 对外提诉求**（仍开放）：向 agent4j 索取 `cancel()`/读超时，向网关申请提高并发上限。这两项不做，本仓库只能在应用层「绕」，无法根治停滞与配额上限。
2. **I7（补一次 COORDINATOR 全绿实跑）**（P1，取决于网络）：把工具预算与委托计数的实机证据补上，这是当前唯一「只有离线回归、没有线上证据」的链路。
3. **实测校验 I1–I10 的验收结论**：本轮以单元/集成测试 + 代码审查交付；按 4.2 的原则，涉及并发、心跳回收与重渲染的结论应在真实多实例/真实令牌/真实浏览器上再采样一次（尤其 I1 的跨实例配额、I4 的重渲染版式、I8 的超时释放）。
4. **继续采样，把「间歇性」变成可量化的结论**：在网关抖动窗口反复跑停滞率探测（6.3），累积 `hung` 样本；并趁某次运行真卡住时同步抓一次真实阻塞帧（目前只有 `#33` 一次现场）。
5. ~~**补「组合条件下的组件渲染」覆盖**（第七轮新增，P2）~~ **第八轮已做，见 §3.12②**：17 个组合用例在真实浏览器里两级判定，编辑器侧 17/17 pass，9 例差异全在上游（容器套容器是结构性不支持）。**剩下的**：只挑了代表，`:::` 容器两两组合共 19×18 种未穷举；下一轮值得补的是**三层嵌套**、`:::table title=` 嵌在 `:::timeline` 里（`cmb-timeline-table` 的反方向）、以及**超长内容 + 嵌套**的叠加（见 §3.12⑧ 第 4 条）。第九轮把 17 例连同 79 例**重跑了一遍真实浏览器**（结论与第八轮逐项一致），并自证了第八轮的泄漏判据修正（差异 2/17、方向全变严），但**没有新增组合用例**，上一条「剩下的」原样有效。**第十轮**同样没有新增组合用例（它做的是注册表全族收口：把 63 个注册 ID 的每一格补上实测证据，见 §3.14①②），上一条「剩下的」依旧有效。
6. **在库稿件的图片外链治理**（第七轮发现、第八轮量清范围，P2，**仍未动数据**）：全库 44 篇里 **31 篇**正文含绝对 URL，唯一外部 URL 62 个探活 **404 × 21**；**裂图集中在 4 篇 20 张**（16/21/26/35，全是 `robocopmao.github.io`），另有 1 篇坏链（13）。根因是模型自己编了远程图片地址而不是调素材工具（见 §3.12④）。**三个方案已列，推荐 A（只治未来、不碰存量）**；B/C 都要在「动生产数据」上再做一次产品决策。另：`localhost:8081` 那 19 篇**烧域名**的存量稿件（D42）同样未回改——机制已修，存量清洗是独立决策。
7. ~~**让 D45 的技能文案真正落库**~~（第九轮新增、第十一轮做成可自动化验证的待办、**第十四轮已闭合**，P1）：
   **已于 2026-09-13 晚随「修 43/44 故障」的那次重启落库**，实测 `db_len = 6649`、五个 `LOCATE` 全部 `> 0`、
   `UPDATED_AT = 2026-09-13 20:36:15`（原始读数见 §3.18③）。**这一条不再是待办**，
   下面保留完整的复核方法，供下次改文案后复用。
   `SkillSeeder.MARKFLOW_CONTENT` 的改动（`layout-*` 全族禁写名单、`case-flow` 行首 `-`、`hint` 入名单、
   `::callout type=` 的 `danger`/`success`）只在**应用启动时**按 `builtin_key` upsert 进 `SKILL` 表。
   第九轮实测库里仍是旧文案；第十一轮复核仍是旧文案（见 §3.15①）。
   **源码侧现在有测试钉住**（`SkillSeederMarkflowContentTest`，5 例，不连库），源码与库的一致性则用下面这组只读命令复核。

   **① 源码 ↔ 库中内容一致性（只读，重启后跑一次）**——容器名 `momo-mysql-dev`，库 `wechat-article`：

   ```bash
   docker exec momo-mysql-dev mysql -uroot -pchange-me --default-character-set=utf8mb4 -D wechat-article -N -B -e "
     SELECT CHAR_LENGTH(CONTENT)                                              AS db_len,
            LOCATE('layout-*', CONTENT)                                       AS has_layout_family,
            LOCATE('76 组', CONTENT)                                          AS has_76,
            LOCATE('行首的 \`-\`', CONTENT)                                   AS has_caseflow_dash,
            LOCATE('只有容器写法', CONTENT)                                   AS has_hint_container_only,
            LOCATE('方向相反', CONTENT)                                       AS has_slider_direction,
            UPDATED_AT
     FROM SKILL WHERE ID = 4;"
   ```

   **预期结果**（三列都要满足才算落库成功）：

   | 列 | 重启前（2026-09-13 实测） | 重启后应为 | 第十四轮实测 |
   |----|--------------------------|-----------|-------------|
   | `db_len` | `5635` | **`6649`**——等于 `SkillSeeder.MARKFLOW_CONTENT.length()`（`SkillSeederMarkflowContentTest` 里钉的就是这个值） | ✅ `6649` |
   | `has_layout_family` / `has_76` / `has_caseflow_dash` / `has_hint_container_only` / `has_slider_direction` | 全部 `0` | **全部 `> 0`** | ✅ 全部 `> 0` |
   | `UPDATED_AT` | `2026-09-13 08:38:21`（旧版时间） | 变成**本次重启的时刻** | ✅ `2026-09-13 20:36:15` |

   > `db_len` 比源码值小很多、或 `LOCATE` 仍为 0 ⇒ upsert 没跑到（而不是「文案没改」）——那时要查的是启动日志里
   > `SkillSeeder` 的 upsert 有没有异常，而不是回去改源码。

   **② 注入侧复验（同样只读，会出网一次）**——直接看**组装出来的系统提示**里有没有这几段，
   而不是看库变没变（§4.2 第 5 条：库变了不等于注入生效）：

   ```bash
   TOKEN=$(curl -s -X POST http://localhost:8081/api/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"username":"admin","password":"Admin@123"}' \
     | python -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

   curl -s -X POST http://localhost:8081/api/skills/preview \
     -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
     -d '{"skillIds":[4],"scene":"SCHEDULED"}' \
     | python -c "import sys,json; p=json.load(sys.stdin)['data']['prompt']; \
                  print('promptChars=', len(p)); \
                  print('layoutFamily=', 'layout-*' in p); \
                  print('caseFlowDash=', ':::case-flow' in p and '76' in p); \
                  print('hintContainerOnly=', ':::hint' in p and '<hint>' in p)"
   ```

   预期：后三行**全为 `True`**；`promptChars` 应比落库前**多出约 1014 字符**
   （源码 `6649` − 库里旧版 `5635`；这个差值可直接反证「注入的确实换成了新版」）。
   落库前先跑一次记下 `promptChars` 作为基线，重启后再跑一次对比即可。

   **③ 这条缺口的阻塞性判断：不阻塞「所有组件在两条路径都渲染正确」这条验收。** 依据三条：

   | 判断 | 证据 |
   |------|------|
   | 系统提示取的**确实是** `SKILL.CONTENT` | `SkillPromptAssembler` 对 LAYOUT 维度拼的是 `effectiveLayout.getContent()`（`SkillPromptAssembler.java:120`），而 `effectiveLayout` 来自 `skillMapper.findByIds(...)`（第 159 行，读库）——库里那份旧文案就是模型看到的东西。**这一条成立** |
   | 但它只影响**模型选哪种写法**，不影响两条路径的渲染能力 | 受影响的写法（`layout-*` 全族、`:::success` / `:::danger`、标签式 `<hint>`、自闭合 `<slider … />`）**在上游本来就不支持**——这是上游的事实，不是文案造成的。改文案能让模型**不去写**它们，但**不可能**让它们渲染成功。因此这条缺口对「渲染能力」这项验收的结论没有任何影响（§3.14：25 个两路径均通过、38 个等上游，与文案无关） |
   | 不落库的实际后果，被保存侧自检兜住 | 模型若真写了那些写法，`save_article_draft` 时的 `markflowSyntaxHints` 自检（**已生效**，24 例测试钉住）会当场告警；且**全库 44 篇真实稿件里这 6 种禁写写法的命中数全是 0**（§3.14③ 的只读扫描）。所以截至第十轮，**实际影响为 0 篇**，风险是「未来某轮可能写出降级稿」而不是「已产出坏稿」 |

   ⇒ **它是 P1 的内容质量风险项，不是验收阻塞项。** 第十四轮那次正常重启已把它自动闭合，
   全程无需为它单独安排重启——印证了下面这条判断（原始读数 §3.18③）。
8. **补 D42 的真实轮次对照**（第九轮新增，P1，需重启 + 新建文章）：目前「新产出不会再烧域名」的证据是「落库入口唯一 + 单元红绿 + 存量未回改」三条（见 §3.13③），**缺一次真实调度轮次产出新稿的对照**。执行代价是要重启应用并产生生产数据，属于产品决策，先记在这里。
9. **让 31 个组件升到 A 级证据**（第十轮新增，P2，内容侧工作量）：终稿对照表里注册表 63 个 ID 中只有 **13 个**有「真实稿件写过 + 那篇在真实 SPA 里回归过」的 A 级证据，其余 50 个停在 B 级（最小样例 + 真实浏览器）。表 B 的 40 条非注册语法同理。这不是缺陷——B 级已经是真实浏览器实测——只是**证据强度**可以再上一层。做法是让调度轮次真的产出含这些组件的成稿，逐篇补进 `round10_article_coverage.py` 的命中表。
10. **把「元素形态透传」的判据泛化**（第十轮新增，P2）：本轮的 `leakRaw` 是**按用例声明的标记**判的（`round10_registry_closure.py` 里逐条给 `marker`），不是「产物里出现任何未注册元素就报警」的通用规则。泛化它需要一份**已知合法元素白名单**（含 `svg`/`foreignObject`/`animateTransform`/`katex` 等上游会自造的元素），否则误报会淹掉真信号。泛化后 `silently-lost` 那一条（`cmb-callout-steps` 的 `<steps>`/`<step>`）才有可能被自动抓住，见 §3.13② 末尾。
11. **「等上游」清单已降级**（第十轮结论，无需动作，仅记录）：9 条里每一条都有**只改写法**的替代方案且 10 条全部通过两条路径（§3.14④），所以它们**不阻塞**「所有组件两条路径都渲染正确」这个验收标准。上游若哪天实现了这些写法，仍按 §3.12③ 表里记的验证动作复跑；不复跑也不影响当前交付。
