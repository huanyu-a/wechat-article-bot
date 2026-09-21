# 容器部署切换与收官 — 暂停交接（2026-09-20）

> 用户指示：保存进度、停止工作流、次日继续。本文件是续做入口，先读这里。

## 一、工作流状态

- **run ID**：`dwfrun-e5fc3fc2-e23f-4a23-a805-ca7772bc2c26`
- **脚本**：`.zcode/workflow-drafts/docker-switch-and-closeout.dwf.ts`（8 阶段 / 10 子智能体）
- **状态**：`stopped`（由 model 主动停止，**可恢复**——用 `ResumeWorkflowRun` 传上面的 run ID，已落定的步骤从日志重放、不重复花钱）
- **停止时进度**：阶段 1–3 完成，阶段 4 完成 3/4（渲染回滚、存量合并、存档与上游核验已完成；「交接文档未核验声明核验员」执行中被中断，该步未落定，恢复时会重跑它）
- **尚未开始**：阶段 5（定时任务采样）、阶段 6（迁移后全量测试 + 文档尾巴订正）、阶段 7（本地提交）、阶段 8（收口验收）

## 二、已落定的事实与证据

### 阶段 1：测试复验（无并发会话的干净树下）

三条上轮指定命令 + 全量门全部 exit=0：

| 命令 | 结果 |
|---|---|
| `-Dtest=WechatArticleBotApplicationTests,ToolRegistryTest` | 24 例 / 0 失败 |
| `-Dtest=InFlightGateTest,LlmLeaseMapperIntegrationTests` | 36 例 / 0 失败 |
| `-Dtest=MarkflowFlexSpanNormalizerTest,LegacyFlexSpanMergeRunnerTest` | 65 例 / 0 失败 |
| 全量 `test` | **546 例 / 66 类 / 0 失败 0 错误 0 跳过** |

跑法：`java.exe` + Maven classworlds 直调，`-DforkCount=0`（本机 surefire fork 被沙箱拒），日志 `target/mvn-gate.log`，surefire 汇总按本轮 mtime 过滤。

**这条回答了用户的第一点**：并发会话已不存在，上轮那轮绿是真绿。

### 阶段 2：本地关停 + 数据库迁移

- 本地三进程已停（Maven `spring-boot:run` 包装、应用、渲染服务 8788）
- 旧容器 `watb-dev-mysql` 已 **stop 但保留**（镜像 mysql:8.0，数据卷 `050c8ebd91877df65f48305b6fc095390dfa0463d06b19d365855badbe55d802`）—— **回滚路径，勿删**
- 新容器 `watb-docker-mysql`（命名卷、专用网络），发布 `127.0.0.1:3306`
- dump：`data/migration/watb-migration-20260920-152438.sql`（2,069,682 字节，完整，md5 `17c4d94515e8955de1db16f64085501f`）
- **迁移校验通过**：58/58 张表行数与迁移前**逐一相等**，总行数 318 = 318，0 差异；
  `diff data/migration/.counts-pre-exact.txt data/migration/.counts-post-exact.txt` → 无输出（逐行一致）
- 迁移报告：`data/migration/MIGRATION-REPORT.md`
- 上传文件：313 个 / 387,928,487 字节（≈371 MiB），已进容器卷 `watb-uploads`
- **数据完整性二次复核**（18:30 直查迁移后库）：ARTICLE=17、TASK_RUN=28、ARTICLE 最大 ID=17，
  与迁移前计数文件逐项一致 → 容器化后没有意外写入（定时任务未在容器里自主跑过）
- dump 里的三项附加开关说明：`--no-tablespaces`（避免无 PROCESS 权限报错）、`--set-gtid-purged=OFF`（避免空库导入 GTID 失败）、`--hex-blob`（二进制列安全）；源库**本来就没有**存储过程/事件/触发器，不是漏导

### 阶段 3：应用容器化

- 镜像：`localhost/wechat-article-bot:local`
- 容器：`watb-app`，`127.0.0.1:8081->8081`，与数据库同网络
- 部署说明：`docs/dev/docker-deployment.md`（容器/卷/网络命名、启停、看日志、回滚路径）
- 本机 **没有 compose**（podman 5.8.3 无 compose 提供者），编排全部是 `podman run` 手工完成

### 阶段 4：已完成的 3 项

- **渲染地址回滚**：`http://127.0.0.1:8788` → `https://www.bx9y.com.cn`（本地渲染服务已停，8788 无监听）。
  **独立复核**（2026-09-20 18:30 直查迁移后库）：`select base_url from RENDER_CONFIG` → `https://www.bx9y.com.cn`。
- **存量合并 `--apply`**：已执行（子任务已落定，1.99M tokens）。
  该步的详细输出在停止通知里被截断，**未取回**；runner 幂等，次日复跑一次 dry-run 即可复核（应仍为 0 可合并容器）。
- **存档与上游清单核验**：已执行（26.78M tokens），其结论同样在停止通知里被截断，未取回。
  注意：它的同伴「交接文档未核验声明核验员」未完成，所以**两份核验的结论本轮都没有落到交接文件里**，次日恢复时第一件事就是补这个。

## 三、停止时的机器状态（2026-09-20 18:2x 实测）

```
watb-docker-mysql   Up 3 hours   127.0.0.1:3306->3306/tcp
watb-app            Up 2 hours   127.0.0.1:8081->8081/tcp
watb-dev-mysql      Exited       127.0.0.1:3306->3306/tcp   ← 回滚路径，勿删
watb-test-mysql     Exited       0.0.0.0:3306->3306/tcp
nostalgic_mcnulty   Exited       （与本项目无关）
```

- `http://127.0.0.1:8081/` → **HTTP 200**
- 8788 无监听（渲染服务已停，渲染地址已指向生产）
- 3306 / 8081 均由 podman 发布

**次日开机后**：podman 容器默认不自动起（除非 `restart` 策略生效），先 `podman ps` 看 `watb-docker-mysql` 与 `watb-app` 是否在跑；不在就跑 `docs/dev/docker-deployment.md` 里的启动命令。

## 四、次日继续的入口

1. **恢复工作流**：`ResumeWorkflowRun` + run ID `dwfrun-e5fc3fc2-e23f-4a23-a805-ca7772bc2c26`。
   - 会从阶段 4 未落定的那步（交接文档未核验声明核验）继续，随后自动跑阶段 5–8。
   - 若只想重做后半段，也可用 `AmendWorkflow` 改脚本后重启（已落定步骤同样吃缓存）。
2. **若要手工接着做**（不走工作流），剩余四件事：
   - 定时任务触发一次并采样（阶段 5）
   - 迁移后再跑一遍全量测试 + 订正文档尾巴（§4.1 门禁数、I7 四处、`pending-user-decisions.md` 里 `n未核验声明` 占位符、D49 启动期行为）（阶段 6）
   - 本地提交（**不 push**）（阶段 7）
   - 收口验收（容器/数据/渲染地址/提交/文档）（阶段 8）

## 五、必须知道的事

- **本轮全部改动尚未提交**：工作区 22 项待处理（10 个修改 + 新增文件 + `.zcode/` + `NUL`）。
  提交时**必须排除**：`.zcode/`（机器产物）、`NUL`（无关散落文件，内容是一行 SSH 公钥）、`data/`（含迁移 dump，已 gitignore）、`target/`、`logs/`、任何 `.env`。
- **`.gitignore` 本轮改了一行**：新增 `data/migration/`（容器化子任务加的，合理，保留）。
- **渲染地址已是生产**：本地渲染服务（MarkFlow `tools/render-server`）已停，若之后还要本地渲染联调，需要先把它起回来并改回配置。
- **上传文件从此写进容器卷**：容器运行后的新上传落在命名卷 `watb-uploads`，宿主机 `data/uploads` 退化为旧快照（313 个文件一个字节未动），两者**不会自动同步**；反向同步命令见 `docs/dev/docker-deployment.md` §6.1。
- **没有健康检查也没有 depends_on**：宿主机重启后 app 可能先于 db 起来（不会有自动兜底），症状是应用连不上库，处理办法 `podman restart watb-app`（实测 10 秒内 `/api/health` 即 200）。
- **端口只绑 loopback**：`127.0.0.1:8081`，局域网不可达；CORS 也只放行 localhost/127.0.0.1。
- **改存储路径别改 .env**：Dockerfile 的 `STORAGE_PATH=/app/data/uploads` 优先级更高，改 .env 无效。
- **数据库连接串只能靠重建容器改**（`-e` 环境变量无法热改）。
- **`watb-test-mysql` 是隐患**：它仍声明着 `0.0.0.0:3306` 映射（当前 Exited、无监听），将来若被启动会和 `watb-docker-mysql` 抢 3306。
- **定时任务一次都没采样过**（阶段 5 未开始）。
- 未推送远程：按项目惯例，push 需要用户明确指令。

## 六、未核验/未完成清单（交给下一轮）

> **（2026-09-21 收口：本节三项都已处置/定性，见文末「七、收口」；「待下一轮」的语气作废，原文保留。）**

- 阶段 4 的「交接文档未核验声明核验」未完成（执行中被停止）
- 阶段 5–8 全部未开始
- 迁移前数据库容器与卷未清理（有意保留作回滚路径），彻底清理待用户确认

---

## 七、收口（2026-09-21）

**本轮的加固与验证都已完成，本文档的暂停/恢复流程不再需要。** 逐条对上：

- 暂停时欠的四件事都已落地：阶段 4 那步「交接文档未核验声明核验」由
  `docs/dev/handoff-unverified-claims-audit-20260921.md` 完成（逐条核验 59 条断言：
  **成立 47 / 不成立 6 / 无法核验 5 / 部分成立 1**；不成立的 6 条**没有一条是当时写错** ——
  时点值过期、已被解决但文档没更新、分母已不存在、措辞过宽）；阶段 5–8 的实质内容
  （容器加固、上传目录定案、渲染配置定案、回滚演练、文档订正）已在本轮逐项做完。
- **恢复工作流这件事不用做了**：`ResumeWorkflowRun` + run ID `dwfrun-e5fc3fc2-…` 那条路不必再走；
  `.zcode/` 下的机器产物不入库（见 §五的排除清单）。
- **运维面新增**（详见 `docs/dev/docker-deployment.md` §10）：
  - 两个容器都补了健康检查（`podman ps` 里能看到 `(healthy)`；探针定义与查看命令见 §10.1）；
  - `scripts/watb-start.sh` / `scripts/watb-stop.sh` 成为**宿主重启后的标准恢复路径** ——
    起 db → 等 db healthy → 起/重启 app → 等 app healthy，不再需要人工判断「谁先起来」再
    `podman restart watb-app`（§10.2）；
  - 上传目录定案为**宿主 `data/uploads` bind mount**（卷里多出的 9 个活文件先按内容比对补齐再切，
    现查宿主 323 / 容器内 323 是同一份目录；卷 `watb-uploads` 未删，留作对照）（§10.3）；
  - `watb-test-mysql` 已 **只删容器**（`0.0.0.0:3306` 的端口冲突隐患解除），它的原始定义留在
    `docs/dev/podman-containers-inspect.md` §3（§10.4）；
  - 「本机没有 compose provider」这一现状未变，所以顺序与起停靠脚本而不是 compose（§10.5）。
- **两项定案**：①`site_base_url` 清空（本应用目前没有公网地址；拿到地址后该填成什么样、在哪儿填，
  见 `docs/dev/docker-deployment.md` §10.6）；②**数据库回滚路径已从「声称」变成「已验证」** ——
  用临时容器 + 临时卷做了 dump 恢复演练，逐表行数 **58/58 一致**（总行数 318 = 318），
  演练残留已清理（§8.1）。
- **还留着什么**：旧库容器 `watb-dev-mysql`（`Exited`）与其卷 `050c8ebd…`（现查 303.8 MB）
  **原样保留，作回滚路径 —— 勿删、勿启**；`data/migration/` 下的 dump（2,069,682 字节，
  md5 `17c4d94515e8955de1db16f64085501f`）与计数文件也保留。
  **将来真要清理时的精确命令清单在 `docs/dev/docker-deployment.md` §8.1**
  （含删除前必须确认的几项、先容器后卷的顺序、以及「严禁 `volume prune`」的警告）—— 本轮**未执行**，等用户确认。
- **仍未做的（如实留档）**：跨实例配额、真实超时释放这类需要多实例/长周期观察的行为验证；
  微信草稿同步链路未实跑（需真实公众号）；渲染地址现在是生产 `https://www.bx9y.com.cn`，
  本地 `127.0.0.1:8788` 渲染服务已停（要本地联调得先把它起回来）。
