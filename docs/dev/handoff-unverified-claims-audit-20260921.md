# 交接文档「未核验声明」核验记录（2026-09-21）

被核验对象：`docs/dev/known-issues-handoff.md`（6476 行，729 KB）
本文件性质：**只读核验记录**。核验期间不改既有文档、不跑构建/测试、不写数据库、不重启容器。

## 〇、口径说明（为什么不是「离散清单」）

任务原假设文档里存在带「未核验 / 待核验 / 未经复核 / 未确认」标记的离散清单。实测**不成立**：

| 标记词 | 全文命中数 | 说明 |
|--------|-----------|------|
| 未核验 | **0** | 不存在 |
| 待核验 | **0** | 不存在 |
| 未经复核 | **0** | 不存在 |
| 未确认 | **1** | 仅 §R10 表格里「用户当时用哪一种写法仍未确认」，是单条事实缺口，非清单 |
| 未验证 | 12 | 全部是各轮「本轮仍未验证」的**历史轮次小结**（§3.7/3.8/3.9⑥/3.10⑧/3.11⑧ 等），且多数在**后续轮次已被打掉**（如 §3.8 开头明写「打 3.7 自己列出的四条仍未验证」） |
| 未实测 | 1 | §一 里「公众号对 `<style>` 的过滤策略未实测」，单条 |

**结论：不存在离散的「未核验声明清单」，不存在可直接逐条勾销的待办表。**

因此按任务授权改用**第二口径**：核验文档里**断言性最强、最影响后续决策**的结论。选取依据（按优先级）：

1. §八「建议的下一步」第 1–24 条 —— 这是文档**唯一自认仍开放**的待办区，其中第 3 条原文即「**实测校验 I1–I10 的验收结论**」，即文档自己承认 I1/I4/I8 的验收结论只到「单测 + 代码审查」；
2. 最新几个提交所落地的缺陷（D53/D55/D56/D57、D49 存量合并、第 21/22 条的兜底档案与失败归类）—— 断言日期最新、影响面最大、最可能被后续决策直接采信；
3. 渲染与 MarkFlow 相关（D49/D53、技能文案落库、渲染令牌）；
4. 定时任务归因（I1 跨实例配额、I10 周期刷写、失败归类）；
5. 环境与部署类（容器名/端口/登录口令/端点可达性/依赖缺失）。

**已按任务说明直接引用、不重复核验的**：容器化部署本身、数据迁移 58/58 行数、渲染地址回滚到生产、全量测试 546 例/66 类（本轮已由其它子任务独立核验）。

## 一、核验结果（分批追加）

| # | 声明（含出处） | 结论 | 证据 | 备注 |
|---|---------------|------|------|------|

### 批次 1 —— 环境/部署类（§八 12 / 18 / 19 / 24、§八 18 第二条）

| # | 声明（含出处） | 结论 | 证据 | 备注 |
|---|---------------|------|------|------|
| 1 | 应用跑在容器 `watb-app`，发布 `127.0.0.1:8081`（§八 24） | **成立** | `podman ps`：`watb-app \| localhost/wechat-article-bot:local \| Up About an hour \| 127.0.0.1:8081->8081/tcp`；宿主机 `netstat -an` 有 `TCP 127.0.0.1:8081 LISTENING`；`curl -s -o /dev/null -w '%{http_code} %{size_download}' http://127.0.0.1:8081/` → `200 1021`（size 与文档所记 1021 逐字相同） | 端口只绑回环，与文档一致 |
| 2 | 数据库在 `watb-docker-mysql`，3306（§八 24） | **成立** | `podman ps`：`watb-docker-mysql \| docker.io/library/mysql:8.0 \| Up About an hour \| 127.0.0.1:3306->3306/tcp`；宿主机 `netstat -an` 有 `TCP 127.0.0.1:3306 LISTENING` | — |
| 3 | `watb-test-mysql` 仍声明 `0.0.0.0:3306` 映射，将来被启动会与 `watb-docker-mysql` 抢 3306（§八 24 已知边界） | **成立** | `podman ps -a`：`watb-test-mysql \| mysql:8.0 \| Exited (0) \| 0.0.0.0:3306->3306/tcp, 33060/tcp` | 当前是 Exited，冲突暂未发生；一旦 `podman start` 即成立 |
| 4 | 旧容器 `watb-dev-mysql` 有意保留作回滚路径、未清理（§八 24） | **成立** | `podman ps -a`：`watb-dev-mysql \| mysql:8.0 \| Exited (0) 19 hours ago \| 127.0.0.1:3306->3306/tcp` | — |
| 5 | `/api/health`、登录、8 个业务端点全部 200（§八 19） | **成立** | 用本地默认管理员账号登录取 token（`len=43`，凭据不落文档）后逐条探测：`/api/tasks` `/api/tasks/execution-modes` `/api/dashboard` `/api/articles` `/api/agents` `/api/llm-profiles` `/api/settings/llm` `/api/settings/render` **全部 200**；`/api/health` 200；登录本身 200 | 8/8 命中，与文档所列端点清单完全一致 |
| 6 | `.env` 里 `ENV.MYSQL_URL` 写 `localhost` 而不是 `127.0.0.1`（§八 18） | **成立** | 只读解析 `.env`（不输出口令）：`ENV.MYSQL_URL host= localhost`、`ENV.MYSQL_TEST_URL host= localhost` | 文档给出的「Podman 只把 3306 绑 IPv6 `[::1]`」是**当时 `watb-test-mysql` 的形态**；当前 `watb-docker-mysql` 已绑 `127.0.0.1:3306`（见 #2），故该条**成因已随容器切换而消失，但 `.env` 写 `localhost` 的事实仍在** |
| 7 | `frontend-maven-plugin:2.0.2` 未进本地仓库 ⇒ 离线 `mvn package` 必失败（§八 18 / §八 12 的 A 方案否决理由） | **成立** | `ls ~/.m2/repository/com/github/eirslett/` → `No such file or directory`（该 groupId 整个目录不存在） | 直接支持 §八 12 里「A 经证据否决」的结论：插件不在本地仓库，前移 phase 会打破 `mvn -o` 离线闸门 |
| 8 | 方案 B 已做：`WebUiArtifactCheck`（`@Order(35)`，启动自检，只 WARN 不阻塞）（§八 12 落地进展） | **成立** | `src/main/java/ink/icoding/wechat/article/configuration/WebUiArtifactCheck.java:45` → `@Order(35)`，`:46` → `public class WebUiArtifactCheck implements ApplicationRunner`，类注释 `:39` 自述「取方案 B：把『用户看到空白页』变成『启动日志里一句可操作的 WARN』」 | — |
| 9 | 方案 D 已做：`scripts/dev-start.sh`（构建前端到 `target/classes/static` 再启动；`--build-only`；无 node/npm 退出码 69；`index.html` 未落地退出码 70）（§八 12 落地进展） | **成立** | `scripts/dev-start.sh:31` 用法含 `--build-only`；`exit 69` 出现 3 处（`:53`/`:59`/`:63`）、`exit 70` 一处（`:79`）；`:84` 打印「`==> --build-only：不启动应用`」 | 未执行该脚本（边界要求不跑构建），只做静态核验 |
| 10 | 已建「验收示例-指令式排版」任务（id=4，绑 SKILL 1）（§八 23） | **成立**（实查见 #28） | 留此行以保持编号连续；实测结果在 #28：`SCHEDULE_TASK` 中 `4 \| 验收示例-指令式排版 \| 1` | 本行与 #28 是同一条断言，**统计时只计一次**（合计按 #28 计） |

### 批次 2 —— D55 / D49 / D53 / D56 代码级断言（§二 D55/D49 行、§3.35、提交 `73b7900`/`40817af`/`24f8c0e`）

| # | 声明（含出处） | 结论 | 证据 | 备注 |
|---|---------------|------|------|------|
| 11 | D55 ①：MEDIA 组不再包含 `search_web_images` / `import_web_image`，网图从工具层面不存在 | **成立** | `src/main/java/ink/icoding/wechat/article/agent/ToolRegistry.java:41-43`：`groups.put(MEDIA, new Group(MEDIA, "素材与联网工具", false, "搜索、浏览、图片检索/生成/编辑", List.of("search_web", "browse_webpage", "list_image_assets", "generate_image", "edit_image")))` —— 名单共 **5 件**，两个网图工具名**不在其中** | 与 D55 原文「过滤后只剩 5 件」逐字相符 |
| 12 | D55 追记：`ToolRegistry.filterByGroup(groupKey, tools)` 按组名单过滤，未知组键 **fail-closed** | **成立** | 同文件 `:103-114`：`Group group = groupKey == null ? null : GROUPS.get(groupKey); if (group == null \|\| tools == null \|\| tools.isEmpty()) return List.of();` —— 未知键/ null / 空入参一律返回**空列表**，不退回全量 | — |
| 13 | D55 追记：三个装配点一律走 `filterByGroup`，行号为 `ai/ArticleAiService.java:468`、`:659`、`ai/ScheduledAgentFactory.java:187` | **成立** | `rg -n "filterByGroup" src/main/java/` 的全部调用点恰好 3 处，行号**逐条命中**：`ArticleAiService.java:468`、`ArticleAiService.java:659`、`ScheduledAgentFactory.java:187` | 行号精确，无漂移 |
| 14 | D55 追记：`ImageGenerationService` 把 size 由硬编码 `1024x1024` 改成 `1024x768`（**生成/改图两处**） | **成立** | `src/main/java/ink/icoding/wechat/article/ai/ImageGenerationService.java:54`（生成，`"size", "1024x768"`）与 `:133`（改图，`field(output, boundary, "size", "1024x768")`）；`:51` 注释记「之前硬编码 1024x1024…巡检 run#33 实测产物 1024x1024」 | 恰好两处，与「生成/改图两处」相符 |
| 15 | D55 测试：`agent/ToolRegistryTest` 覆盖「过滤后只剩 5 件且剔除两个网图工具、其它组不受影响、未知键/null/空入参 fail-closed、组声明与 D55 一致」 | **成立** | `src/test/java/ink/icoding/wechat/article/agent/ToolRegistryTest.java`：`mediaGroupFilterDropsWebImageTools`(`:65`)、`groupFilterDoesNotTouchOtherGroups`(`:80`，含 `assertThat(registry.filterByGroup(MEDIA, draftTools)).isEmpty()` `:91`)、`groupFilterIsFailClosedForUnknownKeyAndEmptyInput`(`:96-100`，四类入参逐条断言空)、`mediaGroupDeclarationOmitsWebImageToolsOnly`(`:105`)、`protocolsForbidWebImageToolsConsistentWithToolLayer`(`:114`) | 五个用例名与文档列举的覆盖面**一一对应** |
| 16 | D49 修复：`MarkflowTextStyle = TextStyle.configure({ mergeNestedSpanStyles: false })` | **成立** | `webui/src/editorExtensions.js:333` → `export const MarkflowTextStyle = TipTapTextStyle.configure({ mergeNestedSpanStyles: false })`；`:318-331` 注释完整记录上游 `mergeNestedSpanStyles: true` 的拼接机制 | — |
| 17 | D49 修复：新增 `MarkflowInnerSpanStyle`，规则 priority **104**（先于 textStyle 消费嵌套样式 span），扩展 priority 默认 100 | **成立** | 同文件 `:370` → `export const MarkflowInnerSpanStyle = Mark.create({`；`:392` → `priority: 104`（在 `addRules()` 内，即**规则**优先级）；`:359-362` 注释明确「规则 priority 104：高于 textStyle 的默认 50」「扩展 priority 保持默认 100」 | 注意 `:1024` 另有一处 `priority: 101`（textStyle 包自带），文档在 D49 里也提到 101，不冲突 |
| 18 | D49 存量合并工具：`MarkflowFlexSpanNormalizer`（纯函数）+ `LegacyFlexSpanMergeRunner`（`@Order(41)` 的 `ApplicationRunner`，默认 dry-run，`--apply` 才写库）+ `LegacyFlexSpanJdbcStore` / `LegacyFlexSpanMapper` | **成立** | 四个文件均在 `src/main/java/ink/icoding/wechat/article/article/`；`LegacyFlexSpanMergeRunner.java:55` → `@Order(41)`，`:58-59` → `APPLY_OPTION = "apply"`「唯一把默认 dry-run 变成写库的开关」，`:71` → `boolean apply = applyRequested(args)`，`:76` → `apply ? "已写库" : "dry-run"`，`:87-91` → 独立 `main` 入口 | 类注释 `:31-47` 与文档表述逐条一致 |
| 19 | D49 测试：`MarkflowFlexSpanNormalizerTest` **18 例** + `LegacyFlexSpanMergeRunnerTest` **11 例** | **成立** | `rg -c '@Test'`：`MarkflowFlexSpanNormalizerTest.java` → **18**、`LegacyFlexSpanMergeRunnerTest.java` → **11** | 数字逐条相符 |
| 20 | D49 追记：`--apply` 已于 2026-09-20 执行，但**该步详细输出在停止通知里被截断、未取回**；可复核的实测只有 dry-run（13 篇 / 0 篇可合并） | **无法核验（如实保留）** | 文档自述的两处出处 `docs/handoff/sess-20260920-docker-switch-paused.md:55-56`、`docs/dev/docker-deployment.md:357-362` 我未逐行读；`--apply` 的原始 stdout 依其自述已丢失 | **不猜测**。可核验的替代路径是直查库看是否仍有「拆分形态」存量，但那是写库判定，超出本次只读边界 |
| 21 | D53：定时链路 MARKFLOW 稿补注入 `data-render-id` 渲染区段标记 | **成立** | `src/main/java/ink/icoding/wechat/article/ai/ArticleAiService.java:72-74`（标记正则 `data-render-id\s*=\s*["']([A-Za-z0-9_-]+)["']`）、`:76-91`（`wrapWithRenderId` 形态：最外层注入 `<section data-render-id="rN">`，已是 section 则补属性）、`:856`（「注入可识别标记：read_article/read_blocks 按 data-render-id 识别渲染区段（防 HTML 回灌）」）、`:1062`（读回识别，回退精确匹配） | 链路两端（注入 + 读回识别）都在 |
| 22 | D56：未知工具 NPE 兜底（`agent/UnknownToolFallbackMap`） | **成立** | `src/main/java/ink/icoding/wechat/article/agent/UnknownToolFallbackMap.java:32` → `public final class UnknownToolFallbackMap`；配套测试 `src/test/java/ink/icoding/wechat/article/agent/UnknownToolFallbackMapTest.java` 存在 | 仅确认类与测试存在，未逐行核验兜底语义 |

### 批次 3 —— 库侧可核验断言（§八 7 / 20 / 21 / 23、D49 追记、§八 24 渲染地址）

> 查询方式：`podman exec watb-docker-mysql mysql -uroot -D 'wechat-article' -N -B -e "…"`，口令从 `.env` 读入环境变量传递，**全程未输出明文**。纯 `SELECT`，无写操作。

| # | 声明（含出处） | 结论 | 证据 | 备注 |
|---|---------------|------|------|------|
| 23 | §八 7：技能文案已落库，实测 `db_len = 6649`、五个 `LOCATE` 全部 `> 0`、`UPDATED_AT = 2026-09-13 20:36:15` | **部分不成立（数值已过期）；核心结论成立** | 实查 `SKILL WHERE ID=4`：`CHAR_LENGTH(CONTENT) = `**`7021`**（非 6649）、五个 `LOCATE` **全部 > 0**（`layout-*`/`76 组`/`行首的 \`-\``/`只有容器写法`/`方向相反` 逐条 `1`）、`UPDATED_AT = `**`2026-09-21 09:41:16`**（非 2026-09-13 20:36:15） | **不是证伪**：文案此后又被改过（本次时间戳即今日重启 09:41，与 `podman logs` 里同一时刻的启动行吻合）。原断言是**时点值**，取当前值必然不等。可核验的**实质结论**（新版文案确已 upsert 进库、五个标记位都在）**成立**。`SkillSeederMarkflowContentTest` 钉的长度区间 `[6200, 7500]` 也仍容纳 7021 |
| 24 | §八 20：共建 **12 条**模型档案 | **成立** | `SELECT COUNT(*) FROM LLM_PROFILE` → **12**；逐条列出 ID 1,13,14,18,19,20,22,25,27,28,29,30 | — |
| 25 | §八 20：默认档案 = `deepseek-flash`；兜底 = `hy4-preview`；图片模型 `sensenova-u1.5-lite` 挂在默认档案上 | **成立** | `SELECT ID,NAME,IS_DEFAULT,IS_FALLBACK,IMAGE_MODEL_NAME FROM LLM_PROFILE`：`1 \| deepseek-flash \| IS_DEFAULT=1 \| IS_FALLBACK=0 \| sensenova-u1.5-lite`；`14 \| hy4-preview \| IS_DEFAULT=0 \| IS_FALLBACK=1 \| NULL` | 三条断言一次查全，逐条命中 |
| 26 | §八 21：兜底档案修复后应为 `hy4-preview=1` / `deepseek-flash=0`（不再是「兜底=默认档案自己」） | **成立** | 同 #25：`hy4-preview` 的 `IS_FALLBACK=1`、`deepseek-flash` 的 `IS_FALLBACK=0`，且二者不是同一行 | 这是**修复后**的目标状态，实查即修复生效 |
| 27 | §八 23：用户自建的 3 个任务（id=1/2/3）**全部绑定了** SKILL id=4 | **成立** | `SELECT ID,NAME,SKILL_IDS FROM SCHEDULE_TASK`：`1 \| 本地验证-I10接线 \| 4,6,11`、`2 \| github项目推荐 \| 4`、`3 \| 每日行业头条 \| 4` —— 三个任务的 `SKILL_IDS` 均含 `4` | 任务 1 另绑 6,11，不影响「都绑了 4」这条 |
| 28 | §八 23：已建「验收示例-指令式排版」（任务 id=4，绑 SKILL 1） | **成立** | 同 #27：`4 \| 验收示例-指令式排版 \| 1` | 名称、id、绑定**三者逐字相符** |
| 29 | D49 追记：启动期 dry-run 实测日志 `[存量flex拆分合并] 完成（dry-run）：MARKFLOW 稿件扫描 13 篇，受影响 0 篇、0 个容器，flex:1 顶级 span 0 → 0 个`，`podman restart` 后同一行复现 | **成立（拿到活体证据）** | `podman logs watb-app \| grep 存量flex` → **6 行**，跨 3 次启动（`2026-09-20T16:05:54`、`2026-09-20T16:10:01`、`2026-09-21T09:41:16`），**每行与文档所记逐字相同**；且每次都是「扫描 13 篇」 | 「restart 后同一行复现」得到证实：三次启动输出完全一致 |
| 30 | D49 追记隐含前提：dry-run 报「扫描 13 篇」应与库里 MARKFLOW 稿件数一致 | **成立** | 实查 `ARTICLE WHERE LAYOUT_ENGINE='MARKFLOW'`：共 **16** 行，但按 `CREATED_AT` 切分，**09-21 09:41 那次启动之前只有 13 篇**（ID 3–17，最新 `2026-09-20 13:11:37`）；ID **18/20/21** 分别创建于 `2026-09-21 09:54:20` / `10:26:36` / `10:33:32`，**均晚于最后一次启动** | **曾怀疑是漏扫，查后排除**：`LegacyFlexSpanMapper.markflowFlexCandidates()` 的过滤条件是 `deleted=false AND layout_engine=MARKFLOW`，无其它条件；13 = 当时真实的候选数。当前总数 16 只是因为这之后又产出 3 篇 |
| 31 | §八 24：渲染地址已回滚到生产 `https://www.bx9y.com.cn` | **成立（交叉印证）** | `SELECT ID, BASE_URL FROM RENDER_CONFIG` → `1 \| https://www.bx9y.com.cn` | 本条任务已说明可引用别处核验，此处顺手做实查，结论一致 |

### 批次 4 —— §八 3（I1/I4/I8 验收结论）、§八 13/15/17/22、§八 5/16、D57

| # | 声明（含出处） | 结论 | 证据 | 备注 |
|---|---------------|------|------|------|
| 32 | §八 17：新增 `TaskExecutionProgressWiringTest`（**2 个用例**），钉住「执行线程自己周期刷 `updateProgress`」与「`progress-flush-seconds` 真被用上」；接线点在 `TaskExecutionService.java:247` | **成立** | `rg -c '@Test' TaskExecutionProgressWiringTest.java` → **2**；两个方法名逐条命中：`progressIsFlushedByTheExecutorItselfWhileRunning`（`:214`）、`flushHonoursTheConfiguredInterval`（`:261`）；`src/main/java/ink/icoding/wechat/article/schedule/TaskExecutionService.java:247` → `ScheduledFuture<?> progressFlush = scheduleProgressFlush(run, liveWorkspace);`（**行号精确命中**） | 文档自述「改这一行为 `= null` → 正例红」的变异点即此行 |
| 33 | §八 22：失败归类新增 `【渲染服务未就绪】` 分支，且匹配条件**刻意收窄**为只认 `配置令牌`/`渲染令牌`（**不**用 `MarkFlow`/`渲染服务` 宽匹配） | **成立** | `TaskExecutionService.java:527` → `static String failureMessage(Throwable error)`；`:588-598` → 注释「排版渲染服务未就绪：这是**环境配置**缺失，不是内容不合格」「必须排在下面那条 BusinessException 兜底之前」；`:595` → `if (text.contains("配置令牌") \|\| text.contains("渲染令牌"))`，`:596` → 返回 `"【渲染服务未就绪】…"`。**全文再无 `MarkFlow`/`渲染服务` 的宽匹配分支** | 收窄要求逐字落实 |
| 34 | §八 22：新增两条测试 `unconfiguredRenderServiceIsNotBlamedOnTheDeliverable` / `genuineRenderFailureIsNotMisfiledAsMissingConfiguration` | **成立** | `src/test/java/ink/icoding/wechat/article/schedule/TaskFailureMessageTest.java:141` 与 `:158` 两个方法名逐字命中 | — |
| 35 | §八 13：修复 `summarize-all.mjs` 遍历 `[...raw.samples].sort(by id)`，只改产物行序、不改判定 | **成立** | `tools/render-verify/browser/summarize-all.mjs:129` → `for (const sample of [...raw.samples].sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0)))`；`:121` 注释「**按 id 排序**（第三十六轮加）」 | 只做了静态核验，未重跑套件（边界要求不跑测试） |
| 36 | §八 15：审计脚本 `audit_read_as_write.py`（Q1 64 / Q2 0）与反证脚本 `prove_audit_is_alive.py`（7/7） | **无法核验（脚本已不存在）** | `ls target/scratch` → `No such file or directory`（整个 `target/scratch/` 目录已不在，`target/` 下只剩 `classes`/`logs`/`.log` 等构建产物）。§八 10/15 引用的其余 scratch 脚本（`predicate_by_form.py`、`leakedtag_scope_all.py`、`diff_combos_r37.py` 等）**同样缺失** | **缺的就是脚本本体**。`target/` 被 `.gitignore` 命中，这些一次性脚本从未入库 ⇒ 其输出（64 / 0 / 7-7）**当前不可复现**。**不猜测其真伪**。注意 §八 15 的结论本身（「仓库里没有第二处读语义当写目标」）是**负面结论**，无法靠读代码正面证实 |
| 37 | §八 15 的另一半：`tools/render-verify/gen/passthrough.py`（`classify()`/`counts()`/`--selftest`）**已入库**，可复用 | **成立** | `tools/render-verify/gen/passthrough.py` 存在；`:94` → `def classify(html)`、`:125` → `def counts(html)`、`:138` → `def _selftest()`、`:183-184` → `--selftest` 入口 | 与 #36 形成对照：**入库的留下了，`target/scratch/` 下的一次性脚本丢了** |
| 38 | §八 5/16：远端渲染服务活着且可达，`GET`/`POST` 均返回 **401**，带**假 token 也是 401** ⇒ 在按 token 正常鉴权，不是服务挂了 | **成立** | `curl -o /dev/null -w '%{http_code}' https://www.bx9y.com.cn/__markflow_render` → **401**（GET）；`-X POST -d '{}'` → **401**；带 `X-Render-Token: fake-token` → **401** 且响应体 `{"ok":false,"error":"X-Render-Token 无效"}`（与文档所记响应体**逐字相同**） | 三条实测全中，是本批次里证据最干净的一条 |
| 39 | §八 3 / I1：多实例并发门禁修复——`common/LlmLease` + `LlmLeaseMapper` 存在；「RUNNING 中 ID 最小者为唯一属主」的 `findEarliestRunning` 复核逻辑存在 | **成立（代码存在性）** | `src/main/java/ink/icoding/wechat/article/common/LlmLease.java`、`LlmLeaseMapper.java`、`InFlightGate.java` 三个文件均存在；`TaskRunMapper.java:48` → `default TaskRun findEarliestRunning(Long taskId)`；调用点 `TaskExecutionService.java:184` → `TaskRun owner = runMapper.findEarliestRunning(taskId);` | **边界**：§八 3 要的是「**在真实多实例上再采样一次**」的**行为**验证。本次只核到**代码在位**，**未做跨实例实跑** ⇒ 「跨实例配额真的共享/互斥」这一条**未覆盖**（见报告未覆盖段） |
| 40 | §八 3 / I8：编辑器链路包上 `StageTimeout`，超时释放并发名额并发 `error` 事件 | **成立（代码存在性）** | `ArticleAiService.java:348-354` → 注释「硬超时护栏（I8）：编辑器链路此前直接 `result.execute()/result.get()`，完全绕过 StageTimeout」+ `StageTimeout.await(result, editorTimeoutSeconds, "编辑器")`；`:753` → `catch` 里判 `StageTimeoutException`（发 `error` 事件） | 同 #39：只核到代码在位，**未做真实超时实跑** |
| 41 | §八 3 / I4：存在「用留存 Markdown 重新渲染覆盖正文」的产品入口 | **成立（代码存在性）** | `ArticleController.java:80-85` → `@PostMapping("/{id}/rerender")` + `service.rerender(id, body.accent(), body.dark())` | 未做重渲染版式的真跑 |
| 42 | D57：7 个智能体改绑 **7 个互不相同**的稳定模型（editor=qwen3.8-27b、scheduled_creator=glm-5.3-flash、researcher=qwen3.8-flash、writer=hy4-preview、illustrator=agnes-3.0-flash、reviewer=step-3.7-flash、chief=dots3-note-prev） | **不成立（当前库状态与断言不符）** | 实查 `AGENT_DEFINITION`（7 行，`BUILTIN_KEY` 与 7 个 `AgentFactory.CODE_*` 常量逐条对应 ✓）的 `LLM_PROFILE_ID`：`1→1, 2→1, 3→1, 4→13, 5→1, 6→13, 7→13`。即 **7 个智能体只落在 2 条档案上**（档案 1 = `deepseek-flash` 被 4 个智能体共用；档案 13 = `glm-5.3-flash` 被 3 个共用），**不是 7 个互不相同** | 文档 D57 行末尾自述「应用内 AGENT_DEFINITION/LLM_PROFILE 绑定（**用户界面随时可调**）」，且 §八 21 记录过「删掉全部档案与绑定 → 重启 → 种子重建」、§八 20 又重建了 12 条档案并把默认改成 `deepseek-flash`。**故这不是修复被回退，而是后续档案重建把绑定改回了「按默认档案」形态**；但**D57 原文那句「7 个互不相同」作为当前事实已不成立**，后续若有人照它推断「每智能体独立模型」会错。另外原文的「默认档案 #16→#1 hy4-preview」也已过期：现 ID 1 = `deepseek-flash`，`hy4-preview` 是 ID 14 |

### 批次 5 —— §八 19/20/22/23 的「实机验收」类断言（库侧活体取证）

| # | 声明（含出处） | 结论 | 证据 | 备注 |
|---|---------------|------|------|------|
| 43 | §八 20：实机触发一次 SINGLE 任务 → `status=SUCCESS`、**38 次工具调用**、**1 篇文章 + 3 张配图**（全落本地 `/uploads/`、**0 占位图 0 外链图**）、耗时 **205.8 秒**、`switchedProfile=false` | **成立（逐项命中，证据链最完整的一条）** | `SELECT ID,TASK_ID,MODE,STATUS,TOOL_CALL_COUNT,GENERATED_COUNT,ARTICLE_ID,… FROM TASK_RUN` → **ID=2**：`TASK_ID=1 \| SINGLE \| SUCCESS \| TOOL_CALL_COUNT=38 \| GENERATED_COUNT=1 \| ARTICLE_ID=1`，`TIMESTAMPDIFF(SECOND,STARTED_AT,FINISHED_AT)=205`，`STARTED_AT=2026-09-18 09:26:17`；`STAGES_SUMMARY` 原文：`{"toolCalls":38,…,"profilesUsed":["deepseek-flash/deepseek-flash"],"switchedProfile":false,"stages":[{"stage":"SCHEDULED_SINGLE","seconds":205.8,"toolCalls":38}],"saved":true}` —— **`205.8` 与文档所记逐字相同**；文章侧 `ARTICLE WHERE ID=1`：`<img` 计数 **3**、`src="/uploads/` 计数 **3**、`src="http` 计数 **0** | 38 / 205.8 / switchedProfile=false / 1 篇 / 3 图 / 0 外链 —— **六项全部对上** |
| 44 | §八 19：`TASK_RUN` 行里 `MODE='SINGLE'` **在插入时就已落库**（「看不出这轮走的是哪条链路」缺陷的修复点） | **成立** | `SELECT MODE, COUNT(*) FROM TASK_RUN GROUP BY MODE` → `SINGLE 20 / PIPELINE 7 / COORDINATOR 5`，合计 32 = 全表行数，**没有任何一行 MODE 为空** | 修复点即「插入时即写 MODE」；若仍是后补，历史上必然留下空值行，实测为空值 0 行 |
| 45 | §八 22：实机复核「同一任务重跑，`message` 已由『【业务校验未通过】…』变为『【渲染服务未就绪】…』」 | **成立（拿到红→绿两行原始数据）** | 查 `TASK_RUN WHERE TOOL_CALL_COUNT=0` 的全部 4 行：ID=3/4 为 `【业务校验未通过】交付内容不满足落库要求…原始信息：排版技能需要 MarkFlow 渲染服务…`（**改前**）；ID=5 为 `【渲染服务未就绪】本次绑定了 MarkFlow 渲染式排版技能，但「排版渲染服务」未启用或未配置令牌，排版指令取不到，任务在开工前即中止。…`（**改后**）。ID=1 是另一类（`LLM 尚未在系统设置中启用或未配置 API Key`），不参与本对照 | 改前/改后**两行都在库里**，不是回忆 |
| 46 | §八 23：**「当前唯一阻塞本地端到端验收的因素：MarkFlow 渲染令牌」**（`hasToken:false`、任何绑 SKILL id=4 的任务都会在开工前中止） | **不成立（已解除，本条已过期）** | ① `GET /api/settings/render` → `{"provider":"MARKFLOW","baseUrl":"https://www.bx9y.com.cn","hasToken":`**`true`**`,"tokenMasked":"••••••••299f","enabled":`**`true`**`,"updatedAt":"`**`2026-09-20T17:35:40`**`"}`；② 库侧 `RENDER_CONFIG`：`ENABLED=1`、`TOKEN_ENCRYPTED` 非空（长度 104）、`BASE_URL=https://www.bx9y.com.cn`；③ 令牌配好之后，绑 SKILL id=4 的任务**已能产出 MARKFLOW 成稿**：`TASK_RUN` ID=29/30/31 分别产出 `ARTICLE` ID=18/20/21，三者 `LAYOUT_ENGINE='MARKFLOW'` 且均 SUCCESS/SUCCESS_WITH_WARNINGS | 本条写于 2026-09-18，令牌在 **2026-09-20 17:35** 被配上（与 §八 24 的容器化同日）。**该条不能再当「当前阻塞项」用**——否则接手方会去索取一个已经有了的令牌。（口径提醒：令牌只在**本机**配好，换机器仍需索取，§八 16 关于「令牌属外部输入」的结论不受影响） |
| 47 | §八 20：I10 周期刷写的实机现象「`toolCallCount` 在运行中持续落库（**0→3→12→20→29→34→35→38**）、`heartbeatAt` 每 15 秒更新一次」 | **无法核验（序列不可复原）** | `SELECT CHAR_LENGTH(EXECUTION_LOG) FROM TASK_RUN WHERE ID=2` → **1753**，`RIGHT(EXECUTION_LOG,120)` 只看到收尾几步（`generate_image` → `save_article_draft` → `set_article_draft_cover`）。周期刷写是**覆盖式**写回，中间态（3/12/20/29/34/35）被最后一次刷写覆盖，**库里已无痕迹** | **缺的是历史中间态**。终值 38 与 `STAGES_SUMMARY` 已由 #43 证实；**「运行中确实在刷」这件事本身由 §八 17 的 `TaskExecutionProgressWiringTest` 钉住（见 #32）**，但那条刷写**序列**作为原始证据已不可复现 |

### 批次 6 —— 存量治理范围 / 渲染侧未闭合项 / 上游依赖（§八 6 / 12 / 14、§一 F6、§八 1 U1、D55 ④）

| # | 声明（含出处） | 结论 | 证据 | 备注 |
|---|---------------|------|------|------|
| 48 | §八 6：全库 **44 篇**里 **31 篇**正文含绝对 URL，唯一外部 URL **62 个**、探活 404×21，裂图集中 4 篇 20 张（16/21/26/35），另有 1 篇坏链（13） | **不成立（作为当前事实；分母已不存在）** | 当前活动库 `SELECT COUNT(*) FROM ARTICLE` → **21 篇**（且 `DELETED` 全 0），**不存在** 44 篇的分母，也不存在 id=26/35 的稿件（最大 id=21）。按当前库实测：含绝对 URL（`src="http` 或 `href="http`）的稿件 **5 篇** | 这些数字描述的是**迁移前那份 44 篇的开发库**（§八 24：迁移时 `ARTICLE=17`）。旧库可能仍在**有意保留的 `watb-dev-mysql` 容器**里，但该容器处于 `Exited` 状态，**本次边界不允许我启动它**（属重启容器），故**未去旧库核对**。**结论限于「当前活动库对不上」，不等于原断言当时是错的** |
| 49 | §八 6 末句：`localhost:8081` 那 **19 篇**「烧域名」的存量稿件（D42）**同样未回改** | **不成立（当前库为 0 篇）** | `SELECT COUNT(*) FROM ARTICLE WHERE CONTENT_HTML LIKE '%localhost:8081%'` → **0** | 同上：旧库不在此处。就当前活动库而言，**这条存量治理待办已经没有对象**（要么未随迁移带过来、要么已被清掉），接手方若按此条去找 19 篇会一无所获 |
| 50 | §八 12：`src/main/resources/static` **不存在**（所以 `process-resources` 补不出前端） | **成立** | `ls -d src/main/resources/static` → `No such file or directory` | 这是「`mvn clean` 后只跑 `spring-boot:run` 会空白页」的结构性前提，确认无误 |
| 51 | §八 12 表：普通重启前后 `target/classes/static` 的 **39 个文件** sha256 逐字节不变 | **部分成立（文件数已漂移，相等性未复验）** | `find target/classes/static -type f \| wc -l` → **40**（顶层 4 项：`assets`/`favicon.svg`/`icons.svg`/`index.html`） | 现为 **40** 而非 39（前端此后又构建过）。**「重启不退回旧前端」这条本身我没复验**——它需要重启容器，超出边界。文件数只是说明原记录是时点值 |
| 52 | §八 14：保存侧语法自检现状是**提示而非拒绝**，且**只覆盖智能体 `save_article_draft`，编辑器 REST 保存路径没有这项检查** | **成立** | `rg -n "markflowSyntaxHints" src/main/java/` 的**全部**命中只有两处，都在智能体侧：`ai/ScheduledArticleTools.java:249`（定义）与 `:717`（调用）。在 `article/*.java`（含 `ArticleController`/`ArticleService`）与 `ai/ArticleEditorTools.java` 里搜索该符号 → **零命中** | 「只覆盖智能体路径」得到结构性证实：编辑器保存链路里根本没有这个符号 |
| 53 | D55 ④：**巡检心跳增加图片来源合规检查**（日志出现搜图/导图工具即违规） | **无法核验（不在版本控制内）** | 全仓搜索「巡检」：`src/main/java` 下**无任何命中**；命中的全是 `docs/`（文档、`docs/handoff/*.json` 会话记录）。心跳被记录为一个**外部 automation**（`docs/dev/pending-user-decisions.md:102` 提到 `automation-6fe375ff`），其定义与运行记录**都不在本仓库** | 缺的是**心跳本体的配置/日志**。仓库内可核验的只有 D55 的①②③（见 #11–#15）；④**不能据仓库断定已生效** |
| 54 | §八 1 / U1：**agent4j 2.3.3 既无读超时也无取消接口** | **不成立（字面过宽；成立的是它的窄版本）** | 直接扫 jar（`~/.m2/repository/ink/icoding/llm/agent4j/2.3.3/agent4j-2.3.3.jar`，81 个 class / 1354 个标识符，`python` 提取常量池字符串；**先跑 sanity 确认探测器活着**：`execute`/`get`/`AgentClientSession`/`SerializationData` 均能命中）：<br>① `ink/icoding/llm/agent/` 包（11 个 class，即应用真正驱动的 agent 会话层）内 `cancel`/`timeout`/`close`/`shutdown`/`interrupt`/`abort` **全部 0 命中**；<br>② **但整个 jar 内有**：`readTimeout`/`connectTimeout`/`writeTimeout` → 位于 `ink/icoding/llm/core/model/impl/OpenAIChatModel.class`、`OpenAIResponseModel.class`、`AnthropicModel.class`；`cancel`/`cancelRequested`/`isClientCancelledStream` → 位于 `.../impl/OpenAIResponseModel$1.class`、`AnthropicModel$1.class`、`OpenAIChatModel$1.class` | **准确的表述应是**：**模型层**已有连接/读/写超时与流取消机制，**缺的是 `AgentClientSession` 这一层**的读超时与取消。U1 按字面读会让人去向上游要一个「已经存在于模型层的读超时」，而真正的缺口是**会话层**。建议订正 U1 措辞（未改文档，仅记录）。项目侧代码注释与窄版本一致：`ArticleAiService.java:353`「`StageTimeout` 只能放弃工作线程（agent4j 无 cancel）」 |
| 55 | §一 F6 / `:396`：公式的 MathML 能原样落库，但**公众号编辑器是否保留 MathML** 未验证；KaTeX 配套 `<style>` 已放行，但**公众号对 `<style>` 的过滤策略未实测** | **无法核验（缺外部输入，且文档已自认）** | 该条在 `docs/dev/upstream-requests-checklist.md:101` 被登记为 **W1「开放（确需外部确认）」**，理由原文即「**本机无公众号登录会话**」；`known-issues-handoff.md:433` 亦自述这三项（MathML/`<style>`/`<svg>`）「本机都无法验证」。我这边**没有任何公众号登录会话或平台侧凭证**，也无从构造「粘进真实公众号编辑器」的观测 | **不是文档的疏漏，是物理上缺一条外部通道**。文档处理得当（已挂到对外诉求清单）。缺的就是**公众号侧权威结论**，属外部输入 |
| 56 | 支持本文件「口径」判断的旁证：`docs/dev/pending-user-decisions.md:100-102` 自述「上一轮那批未核验声明里还躺着约 8 条产品决策类待拍板项（**那批声明的总条数不写数字——本简报未找到该清单的可核对出处，不引用无法核对的数**）」 | **成立（独立旁证）** | `docs/dev/pending-user-decisions.md:100` 标题即「其他已知待拍板事项（**未核验**）」，`:102` 原文如上 | **另一份文档独立确认了「那批未核验声明没有可核对的出处」**——与我在 §〇 的实测（`未核验`/`待核验`/`未经复核` 命中数全为 0）互相印证。本文件改用「高影响断言」口径因此是**唯一可行**的口径，而不是取巧 |

### 批次 7（收尾）—— §八 12 方案 C、§八 20 密钥存储、§八 21 兜底档案修复

| # | 声明（含出处） | 结论 | 证据 | 备注 |
|---|---------------|------|------|------|
| 57 | §八 21：`LlmProfileSeeder.markFallbackIfUnset()` 改为**按 `seeds()` 里的声明取**（`Seed.fallback()` 为真的那条），只有它不存在才退回「标记默认档案」 | **成立** | `agent/LlmProfileSeeder.java:35` → `record Seed(String name, String modelName, boolean fallback)`；`:151` → `profile.setIsFallback(seed.fallback())`；`:111` → `markFallbackIfUnset(source)`；`:174` → 方法定义；`:169` javadoc 原文「现在按**声明**取：优先 `Seed#fallback()` 为真的那条档案；只有当它不存在…」 | 实现与文档描述逐条一致 |
| 58 | §八 21：新增两条测试 `exactlyOneDeclaredFallbackProfileAndItIsNotAPrimaryBinding` / `fallsBackToMarkingTheDefaultWhenDeclaredFallbackIsMissing`；并修掉 `LlmProfileFallbackColumnPersistenceTests` 的隐含全局状态依赖（`@BeforeEach` 快照清空兜底标志、`@AfterEach` 还原） | **成立** | 两个用例名逐字命中：`src/test/java/ink/icoding/wechat/article/agent/AgentSeederTest.java:227` 与 `:254`；`src/test/java/ink/icoding/wechat/article/LlmProfileFallbackColumnPersistenceTests.java`（注意包名是 `ink.icoding.wechat.article`，不在 `.agent` 下）`:72` → `@BeforeEach`、`:80` → `@AfterEach` | 文档未写明这两个用例所在文件，实测在 `AgentSeederTest` 里；不影响结论 |
| 59 | §八 20：密钥经 API 写入、落库为 `API_KEY_ENCRYPTED`（**AES-GCM**）、**源码里没有硬编码密钥** | **成立** | ① 库侧：`SELECT SUM(API_KEY_ENCRYPTED IS NOT NULL AND API_KEY_ENCRYPTED<>''), COUNT(*) FROM LLM_PROFILE` → **12 / 12**（12 条档案全部有加密密钥，无空值）；② 源码侧：`rg -l "AES/GCM\|GCMNoPadding\|AES_GCM" src/main/java/` → **只有 `common/CryptoService.java` 一处**（加解密集中在一个类，符合「没有散落的硬编码密钥」） | 未在输出中读取或打印任何密钥明文 |
| 60 | §八 12 方案 C 已做：README.md / README_CN.md 已写明「直接 `spring-boot:run` 不会构建前端」 | **成立** | `README.md:302-303` → 「Plain `./mvnw spring-boot:run` does **not** build the frontend: `build-webui` is bound to `prepare-package` in `pom.xml`, while `spring-boot:run` only reaches `test-compile`.」；`README_CN.md:298` → 「`scripts/dev-start.sh`　# 先构建前端到 `target/classes/static`，再 spring-boot:run」 | 与 `WebUiArtifactCheck`（#8）、`scripts/dev-start.sh`（#9）合起来，方案 B/C/D 三条都实证在位 |

---

## 二、统计

| 结论 | 条数 | 编号 |
|------|------|------|
| **成立** | **47** | 1–9, 11–19, 21, 22, 24–35, 37–41, 43–45, 50, 52, 56–60 |
| **不成立** | **6** | 23（部分：数值过期）、42、46、48、49、54 |
| **无法核验** | **5** | 20、36、47、53、55 |
| **部分成立** | **1** | 51（文件数已漂移 39→40；相等性未复验） |
| 合计逐条核验 | **59** | #10 是与 #28 重复的同一断言，未重复计数 |

「不成立」的 6 条里，**没有一条是「当时就写错了」**——逐条成因如下（这个区分很重要，别当成 6 个缺陷）：

| 编号 | 声明 | 不成立的成因 |
|------|------|-------------|
| 23 | §八 7 `db_len=6649` / `UPDATED_AT=2026-09-13 20:36:15` | **时点值**，文案此后又改过（现 7021 / 2026-09-21 09:41:16）。核心结论（已落库、五个标记位都在）仍成立 |
| 42 | D57「7 个智能体绑 7 个互不相同模型」 | **后续档案重建**把绑定改回按默认档案（现 7 智能体只落 2 条档案）。文档自己标注过该绑定「用户界面随时可调」 |
| 46 | §八 23「当前唯一阻塞项＝渲染令牌缺失」 | **已被解决**：令牌于 2026-09-20 17:35 配上（`hasToken:true`），此后 MARKFLOW 稿正常产出。该条**不能再当阻塞项用** |
| 48 | §八 6 存量外链治理范围（44 篇 / 31 篇 / 62 URL / 404×21） | **分母已不存在**：当前活动库只有 21 篇。数字描述的是迁移前那份旧库 |
| 49 | §八 6「19 篇烧 `localhost:8081` 域名未回改」 | 同上；**当前活动库实测 0 篇**，该待办已无对象 |
| 54 | §八 1 / U1「agent4j 2.3.3 既无读超时也无取消接口」 | **字面过宽**：jar 的**模型层**有 `readTimeout`/`connectTimeout`/`writeTimeout` 与流取消；缺的是 **agent 会话层**。建议订正措辞 |

**最需要接手方立刻知道的两条**：#46（渲染令牌已配好，别再去索取）与 #54（U1 措辞会误导对外诉求）。

## 三、未覆盖清单（本轮没做到 / 做不到的）

### A. 因缺外部输入而无法核验（不是文档的疏漏）

1. **公众号侧的三项平台行为**（§一 F6 `:396`，已由 `upstream-requests-checklist.md:101` 登记为 W1）：公众号编辑器是否保留 **MathML**、是否保留 KaTeX 配套 `<style>`、是否保留 `<svg>`。**缺的是公众号登录会话与真实编辑器**，本机没有，无法构造观测。（对应 #55）
2. **D55 ④「巡检心跳图片来源合规检查」**：心跳是**版本控制之外的外部 automation**（`automation-6fe375ff`），其配置与运行日志都不在仓库里。**缺的是心跳本体**。（对应 #53）
3. **D49 追记里 `--apply` 那一步的详细输出**：文档自述「在停止通知里被截断、未取回」，原始 stdout 已丢失。可复核的只有 dry-run（这一条我已拿到活体日志，见 #29/#30）。

### B. 因脚本/产物不存在而无法核验

4. **§八 15 的两份审计脚本及其输出**（`target/scratch/audit_read_as_write.py` 的 **Q1 64 / Q2 0**、`prove_audit_is_alive.py` 的 **7/7**）：整个 `target/scratch/` 目录已不存在，这些一次性脚本**从未入库**（`target/` 被 `.gitignore` 命中）。同一批的 `predicate_by_form.py`、`leakedtag_scope_all.py`、`diff_combos_r37.py`、`recompute_combos_backend.py` 等**同样缺失**。（对应 #36）
   - **特别提醒**：§八 15 的结论是一个**负面结论**（「仓库里没有第二处读语义当写目标」）。负面结论**无法靠读代码正面证实**，而它赖以成立的脚本又已丢失 ⇒ **这条在当前状态下不可复现**。文档自己也反复强调过「0 既可能是真干净，也可能是判据打不着东西」，此处正落进它警告的形态。
5. **§八 20 的 I10 刷写序列 `0→3→12→20→29→34→35→38`**：`EXECUTION_LOG` 是覆盖式写回，中间态已被覆盖（现长 1753 字符）。**缺的是历史中间态**。（对应 #47）

### C. 因本次只读边界而未做（做得到，但需授权）

6. **§八 3 明写的「在真实多实例 / 真实令牌 / 真实浏览器上再采样」**：本次只核到 I1/I4/I8 的**代码在位**（#39/#40/#41），**未做**跨实例配额、真实重渲染版式、真实超时释放的**行为**验证。这需要起第二个实例 / 制造真实超时，属会改变运行状态的操作。
7. **迁移前那份 44 篇旧库**：`watb-dev-mysql` 容器处于 `Exited`（有意保留作回滚路径）。启动它就能核对 §八 6 的原始数字（#48/#49），但**启动容器超出「不重启容器」的边界**，故未做。
8. **§八 12 的「普通重启不退回旧前端」**：需要重启 `watb-app` 并逐文件比对 sha256，超出边界；只核到结构性前提（`src/main/resources/static` 不存在）与文件数漂移（39→40）。
9. **全量测试口径**：文档里的「308 例 / 467 例 / 470 例 / 485 例 / 501 例 / 507 例 / 510 例」是**各轮时点值**，而本轮已知基线为 **546 例 / 66 类**（由别的子任务独立核验，我未复跑）。**两者不矛盾**（测试数逐轮递增），但接手方不应把文档里任一旧数字当当前基线。

### D. 明确没做的

10. **没跑任何构建/测试**（含 `mvn`、`npm run build`、`tools/render-verify/` 下任何套件、`scripts/dev-start.sh`）——按边界要求。
11. **没写库、没 `git add`/`commit`、没改任何既有文档**。本文件是唯一新增产物。
12. **没核验 `docs/dev/known-issues-handoff.md` 里 §三 各轮的浏览器套件数字**（79 样例 pass 70 / na 9、组合 17/17、`layout-*` 76/76、crosscheck 18/18 等）。这些需要跑套件，属边界外；且文档已把它们与 `docs/dev/render-verification.md` 的复现手册绑定，可由会跑的人一次性复算。

## 四、方法说明（可复现）

- **定位**：`rg -c` 逐词统计 `未核验`/`待核验`/`未经复核`/`未确认`/`未验证`/`未实测`（结果见 §〇），并用 `rg -n "^#{1,4} "` 取全文 300+ 条标题建立结构图。
- **代码类**：`rg -n` 定位符号与行号，`sed -n` 读窗口核对上下文；行号一律**现测**，不引用文档里的行号（文档 §3.34⑥ 自己记录过行号会因插入文字而整体漂移）。
- **库类**：`podman exec watb-docker-mysql mysql -uroot -D 'wechat-article' -N -B -e "SELECT …"`，口令从 `.env` 读入 shell 变量后经 `podman exec -e` 传入，**未出现在命令行参数、未输出明文**；全部为 `SELECT`。
- **jar 类**：`python` + `zipfile` + `re` 提取 class 常量池字符串做标识符扫描；**每次都先跑 sanity 探针**（确认探测器能命中 `execute`/`get`/已知类名）再采信 `0` 命中——本项目已两次栽在「计数器坏了比没有计数器更糟」（§八 17 的 `leakedTag`、`passthrough.py` 的大小写 bug），故此处刻意照办。
- **网络类**：`curl -s -o /dev/null -w '%{http_code}'`，对 `127.0.0.1:8081`（本应用）与 `https://www.bx9y.com.cn/__markflow_render`（远端渲染服务）分别探测。
- **日志类**：`podman logs watb-app` 只读检索（`grep -a`，避免二进制字节导致「binary file matches」静默丢结果）。

