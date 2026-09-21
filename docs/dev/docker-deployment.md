# 应用容器化部署说明（Podman）

> 面向「下一个接手的人」：照着本文可以从零把应用跑起来、看清数据在哪、以及出问题时怎么退回旧形态。
> 生成时间：2026-09-20（容器化改造当天，全部命令均已在本机实测通过）。

---

## 1. 当前形态一览

| 对象 | 名字 | 说明 |
| --- | --- | --- |
| 应用镜像 | `localhost/wechat-article-bot:local` | 由仓库根 `Dockerfile` 多阶段构建，ID `55c505534ffd`，309 MB |
| 应用容器 | `watb-app` | 重启策略 `unless-stopped`，当前 Up |
| 数据库容器 | `watb-docker-mysql` | `docker.io/library/mysql:8.0`，MySQL 8.0.46，已由上一环节迁入 |
| 专用网络 | `watb-net`（bridge） | app 落在 `10.89.0.4`，db 落在 `10.89.0.2` |
| 数据库卷 | `watb-mysql-data` | **新库**数据，`watb-docker-mysql:/var/lib/mysql` |
| 上传卷 | `watb-uploads` | 命名卷，容器内 `/app/data/uploads` |
| 旧库回滚卷 | `050c8ebd91877df65f48305b6fc095390dfa0463d06b19d365855badbe55d802` | 已停止的 `watb-dev-mysql` / `watb-test-mysql` 用的是同一个卷 |
| 宿主机端口 | `127.0.0.1:8081` → 容器 `8081` | 只绑 loopback，局域网不可达 |
| 访问地址 | <http://127.0.0.1:8081/> | 前端 SPA；`/api/health` 是公开探活 |

Podman 可执行文件：`C:\Program Files\RedHat\Podman\podman.exe`（下文命令里的 `podman` 指它，
在 Git Bash 里先 `export PATH="/c/Program Files/RedHat/Podman:$PATH"`，并设 `MSYS_NO_PATHCONV=1`
以免路径被改写）。

Podman 版本：client 5.8.3 / server 5.8.6，后端是 WSL2 虚拟机 `podman-machine-default`（Running）。
**宿主机没有 compose**，所以本文所有编排都是裸 `podman run`（见 §7）。

---

## 2. 应用容器是怎么起的

### 2.1 启动命令（可直接复制）

```bash
export PATH="/c/Program Files/RedHat/Podman:$PATH"
export MSYS_NO_PATHCONV=1
cd /d/project/wwwroot/wechat-article-bot

DB_URL='jdbc:mysql://watb-docker-mysql:3306/wechat-article?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true'

podman run -d \
  --name watb-app \
  --network watb-net \
  -p 127.0.0.1:8081:8081 \
  --restart unless-stopped \
  -v "D:/project/wwwroot/wechat-article-bot/.env:/app/.env:ro" \
  -v watb-uploads:/app/data/uploads \
  -e SPRING_APPLICATION_JSON="{\"spring\":{\"datasource\":{\"url\":\"$DB_URL\"}}}" \
  localhost/wechat-article-bot:local
```

四个关键点，缺一个都不对：

1. `--network watb-net` —— 不挂这个网，容器里解析不到 `watb-docker-mysql` 这个名字。
2. `-p 127.0.0.1:8081:8081` —— 与旧的本地部署**同一个地址**，外部调用方零改动。
3. `-v .../.env:/app/.env:ro` —— 只读挂 `.env`（见 §4）。
4. `-v watb-uploads:/app/data/uploads` —— 上传目录落在命名卷里。

### 2.2 数据库连接串：为什么必须改 `.env` 里的那一条

`.env` 里写的是：

```properties
ENV.MYSQL_URL=jdbc:mysql://localhost:3306/wechat-article?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
```

`localhost` 在本机（Java 进程直接在 Windows 上跑）指宿主机；但进了容器以后 `localhost`
指**容器自己**，那里没有人监听 3306 —— 直接用会连不上。所以只覆盖这一条，其余配置仍全部来自 `.env`：

```properties
# 宿主机 shell 里
SPRING_APPLICATION_JSON={"spring":{"datasource":{"url":"jdbc:mysql://watb-docker-mysql:3306/wechat-article?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"}}}
```

**为什么用 `SPRING_APPLICATION_JSON` 而不是直接传 `ENV.MYSQL_URL`：**

- `application.yaml:7` 是 `spring.datasource.url: ${ENV.MYSQL_URL:<默认值>}`，即 `.env` 里的键名
  和 Spring 的属性名不是同一个东西，直传 `-e ENV.MYSQL_URL=...` 依赖 Podman 接受带点/带
  大小写的环境变量名（`.dockerignore` 排除了 `.env`，容器里本就没有它，全靠 bind mount 进去）。
  本次实测直接走 `SPRING_APPLICATION_JSON`，确定生效，不再赌变量名形态。
- 覆盖面精准：只动 `spring.datasource.url`，用户名、口令、`SERVER_PORT`、`STORAGE_PATH`、
  管理员账号、`APP_SECRET_KEY` 等仍从 `.env` 读（`SPRING_APPLICATION_JSON` 的优先级高于
  `spring.config.import` 进来的 `.env`，所以这一条能盖住 `.env`，别的也不会被带偏）。

### 2.3 验证「数据库连的确实是容器名」

```bash
# 1) 应用容器里能解析到数据库容器名
podman exec watb-app getent hosts watb-docker-mysql
# 10.89.0.2       watb-docker-mysql.dns.podman

# 2) MySQL 侧看到的连接来自应用容器的 IP，且库名正确
podman exec watb-docker-mysql mysql -uroot -p"$DB_PW" -e \
  "select id,user,host,db,command from information_schema.processlist order by id;"
# root  10.89.0.4:58642  wechat-article  Sleep   ← 10.89.0.4 就是 watb-app
```

如果第 1 条解析不到、或第 2 条里看不到 `10.89.0.4` 的连接，说明绕回了 `localhost` 或网络没接上。

---

## 3. 启停与看日志

```bash
# 看状态
podman ps -a --filter name=watb-app --format '{{.Names}} {{.Status}} {{.Ports}}'

# 停止 / 启动 / 重启（restart 最快，不需要重新 podman run）
podman stop watb-app
podman start watb-app
podman restart watb-app

# 看日志（-f 跟踪；--tail 200 只看尾部；--since 1h 看最近一小时）
podman logs -f watb-app
podman logs --tail 200 watb-app
podman logs --since 30m watb-app

# 进容器排查（容器内用户是 10001，看文件用 --user 0:0）
podman exec watb-app sh -c 'ls /app/data/uploads | wc -l'
podman exec --user 0:0 watb-app sh -c 'ls -la /app/data/uploads | head'

# 资源占用
podman stats watb-app --no-stream
# watb-app cpu=15.27% mem=518.8MB / 8.326GB
```

启动到就绪的实测耗时：**约 5 秒**（`Started WechatArticleBotApplication in 4.665 seconds`），
`podman restart` 后 10 秒内 `/api/health` 即返回 200。

> 注意：`--restart unless-stopped` 只保证「进程退出 / 宿主机重启后容器会被拉起」。
> 它**不保证宿主机重启后 watb-app 一定排在 watb-docker-mysql 之后起来** ——
> 本机 compose 不可用，没有 `depends_on` + healthcheck 这一层。真出现「app 反复重启、
> 日志里 Hikari 连接失败」，`podman restart watb-app` 一次即可。

---

## 4. 配置是怎么进容器的

分两路，互不覆盖：

| 配置来源 | 进容器的方式 | 覆盖了什么 |
| --- | --- | --- |
| `.env`（宿主机 `D:\project\wwwroot\wechat-article-bot\.env`） | bind mount 到 `/app/.env`（**只读**） | 除下面那条以外的全部配置 |
| 数据库连接串 | `-e SPRING_APPLICATION_JSON=...` | 仅 `spring.datasource.url` |

链路：`src/main/resources/application.yaml:3` 是
`spring.config.import: optional:file:.env[.properties]`，容器 `WORKDIR /app`，
所以 `.env` 挂到 `/app/.env` 正好被这条 import 读到。

`.env` 当前含的键（值已省略）：`ENV.MYSQL_URL`、`ENV.MYSQL_TEST_URL`、`ENV.MYSQL_USERNAME`、
`ENV.MYSQL_PASSWORD`、`SERVER_PORT`、`ADMIN_USERNAME`、`ADMIN_PASSWORD`、`APP_SECRET_KEY`、
`TOKEN_TTL_HOURS`、`STORAGE_PATH`。

镜像里已经固化的两个环境变量（`Dockerfile:32-34`）：

```dockerfile
ENV SERVER_PORT=8081 \
    STORAGE_PATH=/app/data/uploads \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"
```

- `SERVER_PORT=8081`：与 `.env` 的 `SERVER_PORT=8081` 一致，无冲突。
- `STORAGE_PATH=/app/data/uploads`：**Dockerfile 的 ENV 优先于 `.env` 里的 `STORAGE_PATH=./data/uploads`**，
  所以存的就是卷，不是容器内的相对目录。改存储位置必须改镜像 ENV 或环境变量，改 `.env` 无效。
- `.dockerignore` 明确排除了 `.env`、`.env.*`、`data`、`webui/dist`、`target`、`node_modules` ——
  镜像里既不带配置也不带上传文件。这是刻意的：配置走 mount，数据走卷。

### 4.1 改配置以后怎么办

- 改 `.env` 的**非连接串内容**：改宿主机 `.env`，然后 `podman restart watb-app`
  （bind mount 是实时的，不需要重建镜像/容器）。
- 改数据库连接串（比如换了库容器名）：`podman rm -f watb-app`，按 §2.1 用新的
  `SPRING_APPLICATION_JSON` 重新 `podman run`（环境变量无法热改）。
- 改**代码**：重新 build 镜像 → `podman rm -f watb-app` → 用新 tag 重新 run。见 §5。

---

## 5. 重建镜像

```bash
export PATH="/c/Program Files/RedHat/Podman:$PATH"
export MSYS_NO_PATHCONV=1
cd /d/project/wwwroot/wechat-article-bot

# 三个基础镜像需要联网拉；给足 10 分钟以上
podman build -f Dockerfile -t wechat-article-bot:local .
# 若某个基础镜像拉取失败，单独重试：
podman pull node:22-bookworm-slim
podman pull maven:3.9.16-eclipse-temurin-17-noble
podman pull eclipse-temurin:17-jre-noble
```

本次实测：三个基础镜像均已拉到本地（`node:22-bookworm-slim` 233 MB、
`maven:3.9.16-eclipse-temurin-17-noble` 511 MB、`eclipse-temurin:17-jre-noble` 268 MB），
一次 `podman build` 通过，`BUILD SUCCESS`，产物 `localhost/wechat-article-bot:local`（309 MB）。
慢在 maven 阶段要现拉依赖树（首次约 8 分钟），第二次起快得多。

镜像内部要点（`podman inspect` 实测）：

- 运行用户 `10001:10001`，非 root。
- 端口 `8081/tcp`，`ENTRYPOINT ["java","-jar","/app/app.jar"]`，jar 40,371,253 字节。
- `VOLUME ["/app/data/uploads"]`，我们再用命名卷显式挂它。
- 前端产物已打进 jar：`BOOT-INF/classes/static/` 下 42 个条目（`index.html` + `assets/*.js|css`）。
  启动日志 `Adding welcome page: class path resource [static/index.html]` 即证明它在。

Podman 的 buildkit 后端**支持** `Dockerfile:10` 的 `RUN --mount=type=cache`（npm 缓存）
与 `Dockerfile:19` 的 maven 缓存，无需改 Dockerfile；已用最小样例单独验证过。

---

## 6. 数据位置与本次迁移记录

### 6.1 上传文件（`watb-uploads`）

| 项 | 值 |
| --- | --- |
| 源目录 | `D:\project\wwwroot\wechat-article-bot\data\uploads` |
| 文件数（迁移前，宿主机） | 313 |
| 文件数（卷内，迁移后） | 313 |
| 字节数（源，`du -sb`） | 387,928,487 |
| 字节数（源，逐文件 `%s` 累加） | 387,928,487 |
| 字节数（卷内，逐文件累加） | 387,928,487 |
| 扩展名分布 | png 291 / jpg 18 / webp 4 |
| 内容校验 | 313 个文件名逐一求 `sha256sum`，宿主机侧与卷内**完全一致**（`diff` 退出码 0，0 行差异） |

灌入方式（借一个一次性 busybox 容器做拷贝，避免在宿主机上装别的工具）：

```bash
podman volume create watb-uploads
podman run --rm \
  -v "D:/project/wwwroot/wechat-article-bot/data/uploads:/src:ro" \
  -v watb-uploads:/dst \
  docker.io/library/busybox:latest sh -c 'cp -a /src/. /dst/'
```

> `du -sb` 在卷内读出的数字有时比源大 20 KiB（宿主机 NTFS 与 overlayfs 对目录 inode 的
> 统计口径不同）。**以逐文件 `%s` 累加与 sha256 为准**，两者都是 387,928,487 / 313 项零差异。

**⚠️ 方向性提醒：容器起来以后，新上传的文件写进 `watb-uploads` 卷，宿主机
`data/uploads` 变成旧的静态快照。** 以后要「把容器里新产生的东西同步回宿主机」，
必须显式反向拷贝，别指望它们自动同步：

```bash
mkdir -p .verify
podman run --rm -v "D:/project/wwwroot/wechat-article-bot/.verify:/v" \
  -v watb-uploads:/dst:ro \
  docker.io/library/busybox:latest sh -c 'ls /dst > /v/names.txt; wc -l < /v/names.txt'
```

### 6.2 数据库（`watb-mysql-data`，上一环节完成）

- dump：`data/migration/watb-migration-20260920-152438.sql`
  2,069,682 字节，`md5 = 17c4d94515e8955de1db16f64085501f`，
  58 张 CREATE TABLE（`wechat-article` 29 张 + `wechat-article-test` 29 张），
  结尾 `Dump completed on 2026-09-20 15:24:39` 完整，导入 `exit=0`。
- 迁移前后行数对照：`data/migration/.counts-pre-exact.txt` 与
  `data/migration/.counts-post-exact.txt` **逐表相同**（`diff` 输出为空），
  `TOTAL_ALL_TABLES = 318 = 318`。非零行：`AGENT_DEFINITION 7`、`ARTICLE 17`、
  `ARTICLE_REVISION 17`、`ASSET 58`、`AUDIT_LOG 69`、`AUTH_TOKEN 47`、
  `LLM_CONFIG 1`、`LLM_PROFILE 8`、`QRTZ_CRON_TRIGGERS 4`、`QRTZ_JOB_DETAILS 4`、
  `QRTZ_LOCKS 2`、`QRTZ_TRIGGERS 4`、`RENDER_CONFIG 1`、`SCHEDULE_TASK 4`、
  `SKILL 18`、`SYS_USER 1`、`TASK_RUN 28`，以及 `wechat-article-test` 库的
  `AGENT_DEFINITION 7 / QRTZ_LOCKS 1 / SKILL 18 / SYS_USER 1`。
- 更详细过程见 `data/migration/MIGRATION-REPORT.md`。

### 6.3 启动时应用自己写库的两件事（要知道，别误判成数据损坏）

1. `SmartMapperInitializer` 会在启动时同步表结构（日志 `Synchronized database structure
   for mapper: ...`），`application.yaml:29-31` 的 `mybatis.smart.auto-sync-db: true`。
2. `LlmProfileSeeder` 首次启动时补建了 4 条标准模型档案
   （`kimi-k3` id=27、`qwen3.8-max` id=28、`glm-5.3` id=29、`nemotron-3-ultra-free` id=30）。
   `wechat-article.LLM_PROFILE` 因此由迁移后的 8 行变成 12 行（id 1,13,14,18,19,20,22,25,27,28,29,30）。
   **这是应用的既有引导逻辑，不是迁移错漏**；它幂等 —— 已重启过一次容器，行数仍为 12，未继续追加。
   `ARTICLE 17`、`ASSET 58`、`SKILL 18`、`TASK_RUN 28` 未被改动。

---

## 7. 已知限制

1. **本机没有 compose**（podman/docker compose 均不可用），所有编排都是 §2.1 那样的裸
   `podman run`。没有 `compose.yaml`，也没有 `depends_on`；镜像和容器关系靠上文命令保存。
2. **没有健康检查编排**：`watb-docker-mysql` 当初 `podman run` 时没挂 HEALTHCHECK，
   `watb-app` 也没有 `--health-cmd`。所以「宿主机重启后 app 可能先于 db 起来」这一种
   失败模式没有自动兜底，需要人工 `podman restart watb-app`（见 §3 末尾）。
3. **端口只绑 loopback**：`127.0.0.1:8081`。要给别人访问得改 `-p 0.0.0.0:8081:8081`
   并自己想清楚安全后果（`SecurityConfig` 的 CORS 也只放行 `localhost` / `127.0.0.1`）。
4. **Podman 跑在 WSL2 虚拟机里**，容器看到的路径不是 Windows 路径。bind mount 的
   Windows 绝对路径由 podman CLI 自己翻译；`podman inspect` 里 `watb-app` 的 `.env`
   源显示为 `/mnt/d/project/wwwroot/wechat-article-bot/.env`，这是**正常的**，不是配错。
5. **卷内文件属主是 root、权限 0777**（从 Windows NTFS 拷过去的）。容器内应用跑
   uid 10001，靠 world-write 读写。能跑，但不优雅；要收紧需在建卷后用
   `--user 0:0` 显式 `chown -R 10001:10001`。
6. **`data/uploads` 宿主机副本已与新写入脱钩**（见 §6.1 提醒）。
7. **镜像 tag 带 `localhost/` 前缀**：Podman 会把它补全成
   `localhost/wechat-article-bot:local`，`podman images` 里 `REPOSITORY` 显示为
   `localhost/wechat-article-bot`。写命令时用不加前缀的 `wechat-article-bot:local`
   或加前缀都认。
8. **`podman build` 的 `-t` 不接受下划线开头的 tag**（实测 `parsing reference "_t:1":
   invalid reference format`），本项目 tag 无此问题。
9. 应用容器内存上限：`JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0`，即吃满 Podman 虚拟机
   可用内存的 75%。本机实测稳态约 519 MB， Podman 虚拟机有 8 GB，余量充足。

---

## 8. 回滚路径

**应用侧**（回到没容器化的本地形态）：`podman stop watb-app`（或 `podman rm -f watb-app`），
本地进程即可重新占用 `127.0.0.1:8081`。上传文件仍在宿主机 `data/uploads`，配置仍在 `.env`，
无需额外动作。

**数据库侧**（回到旧库）：

```bash
# 两个容器都映射 127.0.0.1:3306，同启会端口冲突，必须先停新的
podman stop watb-docker-mysql
podman start watb-dev-mysql     # 旧容器仍在，其卷 050c8ebd… 完整保留
```

旧库数据一个字节都没动：`watb-dev-mysql` 与 `watb-test-mysql` 均为 `exited` 状态，
共用卷 `050c8ebd91877df65f48305b6fc095390dfa0463d06b19d365855badbe55d802`（290 MB，
内含 `wechat@002darticle`、`wechat@002darticle@002dtest` 两个库目录）。
回滚后要把应用容器里的 `SPRING_APPLICATION_JSON` 指回 `localhost:3306`（旧库端口同样
发布在 `127.0.0.1:3306`），或直接把 app 退回本地进程 + `.env` 原值。

**上传文件侧**（回到宿主机目录）：`podman stop watb-app` 后删掉
`-v watb-uploads:/app/data/uploads` 这条挂载即可，源文件没动过。

⚠️ 回滚是**有损方向**：容器跑起来之后新写入 `watb-uploads` 卷 / `watb-mysql-data` 卷的内容
不会自动回到旧卷。要保留就先按 §6.1 的方式反向同步、并按 §6.2 的方式再 dump 一份。

---

## 9. 验收证据（本次实测）

```bash
# 应用根路径 —— SPA index.html
$ curl -s -o /dev/null -w "HTTP %{http_code} size=%{size_download}\n" http://127.0.0.1:8081/
HTTP 200 size=1021

# 公开探活接口
$ curl -s -w "\nHTTP %{http_code}\n" http://127.0.0.1:8081/api/health
{"success":true,"data":{"time":"2026-09-20T08:06:30.330127971Z","status":"UP"},"message":null}
HTTP 200
```

启动日志关键行（`podman logs watb-app`，时区 +08:00）：

```text
16:05:51.349  com.zaxxer.hikari.HikariDataSource  : HikariPool-1 - Starting...
16:05:51.630  com.zaxxer.hikari.pool.HikariPool   : HikariPool-1 - Added connection com.mysql.cj.jdbc.ConnectionImpl@4d4df0f4
16:05:51.632  com.zaxxer.hikari.HikariDataSource  : HikariPool-1 - Start completed.
16:05:52.678  SmartMapperInitializer             : Synchronized database structure for mapper: …article.ArticleMapper
16:05:53.818  o.s.boot.tomcat.TomcatWebServer     : Tomcat started on port 8081 (http) with context path '/'
16:05:53.851  org.quartz.core.QuartzScheduler    : Scheduler WechatArticleScheduler_$_d26adaea23181789891553085 started.
16:05:53.873  i.i.w.a.WechatArticleBotApplication : Started WechatArticleBotApplication in 4.665 seconds (process running for 5.192)
16:05:54.538  i.i.w.a.a.LegacyFlexSpanMergeRunner : [存量flex拆分合并] 完成（dry-run）：MARKFLOW 稿件扫描 13 篇，受影响 0 篇、0 个容器，flex:1 顶级 span 0 → 0 个
```

`LegacyFlexSpanMergeRunner`（`src/main/java/ink/icoding/wechat/article/article/LegacyFlexSpanMergeRunner.java:55`，
`@Order(41)`）的 dry-run 行如上：13 篇 MARKFLOW 稿件、0 篇受影响、未写库。`podman restart` 后
同一行再次出现，行为可复现。

用应用自己的接口读回库里的数据（登录态来自 `.env` 的 `ADMIN_USERNAME` / `ADMIN_PASSWORD`）：

```bash
$ curl -s -X POST http://127.0.0.1:8081/api/auth/login -H 'Content-Type: application/json' \
    -d '{"username":"<ADMIN_USERNAME>","password":"<ADMIN_PASSWORD>"}'
{"success":true,"data":{"token":"…","user":{"id":1,"username":"admin","displayName":"系统管理员","role":"ADMIN"}},…}

$ curl -s -H "Authorization: Bearer <token>" http://127.0.0.1:8081/api/dashboard
{"success":true,"data":{"accounts":0,"drafts":17,"published":0,"failed":0,"activeTasks":4,
 "followers":0,"aiTokens":0,"recentRuns":[ …task_run 真实历史行… ]},…}

$ curl -s -H "Authorization: Bearer <token>" http://127.0.0.1:8081/api/articles
# data.items 共 17 篇，含 id 17「GitHub 一日榜：这 5 个开源 AI 项目…」等真实标题
$ curl -s -H "Authorization: Bearer <token>" http://127.0.0.1:8081/api/assets
# data 为长度 58 的列表
```

`drafts=17`、`activeTasks=4`、articles 17 篇、assets 58 条，与 `wechat-article` 库的
`ARTICLE=17`、`SCHEDULE_TASK=4`、`ASSET=58` 一一对应。

上传文件全量走 HTTP 复扫：卷内 313 个文件名逐一 `GET /uploads/<name>`，
**HTTP 200 = 313，非 200 = 0**（首个请求约 7 ms/个）。

容器形态核对：

```bash
$ podman inspect watb-app --format 'RestartPolicy={{.HostConfig.RestartPolicy.Name}} Network={{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}} Ports={{.HostConfig.PortBindings}}'
RestartPolicy=unless-stopped Network=10.89.0.4 Ports=map[8081/tcp:[{127.0.0.1 8081}]]

$ podman inspect watb-app --format '{{range .Mounts}}[{{.Type}}] {{.Name}}{{.Source}} -> {{.Destination}} rw={{.RW}}{{println}}{{end}}'
[volume] watb-uploads/…/volumes/watb-uploads/_data -> /app/data/uploads rw=true
[bind] /mnt/d/project/wwwroot/wechat-article-bot/.env -> /app/.env rw=false
```

```bash
$ podman exec watb-app sh -c 'ls /app/data/uploads | wc -l'
313
```

> 说明：以上所有命令均在 2026-09-20 本次改造中实际执行，输出为原样摘录。
> 文档里的 `<token>` / `<ADMIN_USERNAME>` 等占位是刻意的脱敏，没有把任何口令写进文档。
