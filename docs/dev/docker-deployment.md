# 应用容器化部署说明（Podman）

> 面向「下一个接手的人」：照着本文可以从零把应用跑起来、看清数据在哪、以及出问题时怎么退回旧形态。
> 生成时间：2026-09-20（容器化改造当天，全部命令均已在本机实测通过）。
> **2026-09-21 更新**：新增本轮运维加固与两项定案（§10、§8.1），并把易过期的数字改成「现查为准」（§1.1）。
> 本文所有 IP / 行数 / 状态都是**时点值**，重建容器或跑一轮任务就会变 —— 以 §1.1 的现查命令为准。

---

## 1. 当前形态一览

| 对象 | 名字 | 说明 |
| --- | --- | --- |
| 应用镜像 | `localhost/wechat-article-bot:local` | 由仓库根 `Dockerfile` 多阶段构建，ID `55c505534ffd`，309 MB |
| 应用容器 | `watb-app` | 重启策略 `unless-stopped`，当前 Up |
| 数据库容器 | `watb-docker-mysql` | `docker.io/library/mysql:8.0`，MySQL 8.0.46，已由上一环节迁入 |
| 专用网络 | `watb-net`（bridge） | app 落在 `10.89.0.7`，db 落在 `10.89.0.6`（**2026-09-21 现查**；容器 IP 由 Podman 每次分配，重建容器就会变 —— 现查命令见 §1.1） |
| 数据库卷 | `watb-mysql-data` | **新库**数据，`watb-docker-mysql:/var/lib/mysql` |
| 上传目录 | 宿主 `data\uploads`（bind mount） | 容器内 `/app/data/uploads`；命名卷 `watb-uploads` 已不再被任何容器挂载（**未删**，留作对照，见 §10.3） |
| 旧库回滚卷 | `050c8ebd91877df65f48305b6fc095390dfa0463d06b19d365855badbe55d802` | 已停止的 `watb-dev-mysql` 用的是它（`watb-test-mysql` 已于 2026-09-21 **只删容器**，卷保留，见 §10.4） |
| 宿主机端口 | `127.0.0.1:8081` → 容器 `8081` | 只绑 loopback，局域网不可达 |
| 访问地址 | <http://127.0.0.1:8081/> | 前端 SPA；`/api/health` 是公开探活 |

Podman 可执行文件：`C:\Program Files\RedHat\Podman\podman.exe`（下文命令里的 `podman` 指它，
在 Git Bash 里先 `export PATH="/c/Program Files/RedHat/Podman:$PATH"`，并设 `MSYS_NO_PATHCONV=1`
以免路径被改写）。

Podman 版本：client 5.8.3 / server 5.8.6，后端是 WSL2 虚拟机 `podman-machine-default`（Running）。
**宿主机没有 compose**，所以本文所有编排都是裸 `podman run`（见 §7）。

### 1.1 这些值会变，怎么自己现查一遍

本文里凡是「容器 IP / 行数 / 状态」这类数字都是**时点值**，容器重建、任务跑一轮就会变。
**以现查为准**，命令如下（`podman` 的定位见本节开头的 `PATH` 与 `MSYS_NO_PATHCONV`）：

```bash
export PATH="/c/Program Files/RedHat/Podman:$PATH"
export MSYS_NO_PATHCONV=1

# 容器状态与健康（括号里的 (healthy) 就是 healthcheck 的结果）
podman ps -a --format '{{.Names}}|{{.Image}}|{{.Status}}|{{.Ports}}'

# 容器 IP（app / db 各查一次；重建容器后这两个值会变）
podman inspect watb-app          --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}={{$v.IPAddress}}{{end}}'
podman inspect watb-docker-mysql --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}={{$v.IPAddress}}{{end}}'

# 关键表的行数（口令从容器自身 env 取，命令行里不出现明文）
podman exec watb-docker-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -D wechat-article -N -B -e \
  "select (select count(*) from ARTICLE) ARTICLE, (select count(*) from ASSET) ASSET, \
          (select count(*) from SKILL) SKILL, (select count(*) from TASK_RUN) TASK_RUN, \
          (select count(*) from LLM_PROFILE) LLM_PROFILE;"'
```

2026-09-21 本机现查结果（本文 §1 表格与 §6.3 的数字就是从这里来的）：

```text
watb-docker-mysql|docker.io/library/mysql:8.0|Up 22 minutes (healthy)|127.0.0.1:3306->3306/tcp, 33060/tcp
watb-app|localhost/wechat-article-bot:local|Up 22 minutes (healthy)|127.0.0.1:8081->8081/tcp
app: watb-net=10.89.0.7   health=healthy
db : watb-net=10.89.0.6   health=healthy
ARTICLE 24 | ASSET 68 | SKILL 18 | TASK_RUN 32 | LLM_PROFILE 12
```

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
# 10.89.0.6       watb-docker-mysql.dns.podman      ← 2026-09-21 现查

# 2) MySQL 侧看到的连接来自应用容器的 IP，且库名正确
podman exec watb-docker-mysql mysql -uroot -p"$DB_PW" -e \
  "select id,user,host,db,command from information_schema.processlist order by id;"
# root  10.89.0.7:53626  wechat-article  Sleep      ← 10.89.0.7 就是 watb-app（2026-09-21 现查）
```

> **IP 是现查值，不是固定值**：上面两个 `10.89.0.x` 由 Podman 在容器创建时分配，
> `podman rm` + 重新 `run` 之后就会变（本文 2026-09-20 那次是 app `10.89.0.4` / db `10.89.0.2`，
> 2026-09-21 现查已是 app `10.89.0.7` / db `10.89.0.6`）。现查命令见 §1.1。

如果第 1 条解析不到、或第 2 条里看不到 §1.1 现查到的那个 app IP 的连接，说明绕回了 `localhost` 或网络没接上。

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
>
> **（2026-09-21 追记：这一层现在有了 —— 两个容器都带健康检查，宿主重启后直接跑
> `sh scripts/watb-start.sh` 就会「起 db → 等 db healthy → 起/重启 app → 等 app healthy」，
> 不必再人工判断顺序；见 §10.1、§10.2。）**

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

> **（2026-09-21 变更：上传目录已改为宿主 `data\uploads` 的 bind mount，卷 `watb-uploads` 不再被任何容器挂载。
> 本节是 2026-09-20 的迁移记录，原文保留；现状与「为什么这样选」见 §10.3。）**

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

   > **⚠️ 上面这四个数是 2026-09-20 迁移当天的时点值，别当现状用。**
   > **2026-09-21 现查**：`ARTICLE 24`（其中 `DELETED=1` 的 3 篇是验收探针稿）、`ASSET 68`、
   > `SKILL 18`、`TASK_RUN 32`（同一时刻 `LLM_PROFILE 12`）。
   > 增长来自其后正常运行的定时任务与本轮验收探针，不是迁移错漏；取值命令见 §1.1。

---

## 7. 已知限制

1. **本机没有 compose**（podman/docker compose 均不可用），所有编排都是 §2.1 那样的裸
   `podman run`。没有 `compose.yaml`，也没有 `depends_on`；镜像和容器关系靠上文命令保存。
   **（2026-09-21 追记：起停顺序已由 `scripts/watb-start.sh` 用健康检查轮询兜住，见 §10.2、§10.5；
   容器定义仍然只存在于命令与还原料里，`podman rm` 掉就没有东西能把它变回来。）**
2. ~~**没有健康检查编排**~~ **已于 2026-09-21 补齐（两个容器都有 healthcheck，见 §10.1）**。
   原文保留作历史记录：`watb-docker-mysql` 当初 `podman run` 时没挂 HEALTHCHECK，
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

⚠️ 回滚是**有损方向**：容器跑起来之后新写入 `watb-mysql-data` 卷的内容不会自动回到旧卷。
要保留就先按 §6.2 的方式再 dump 一份。（上传目录自 2026-09-21 起已改为宿主 `data\uploads`
bind mount，见 §10.3 —— 这一侧不再存在「卷 vs 宿主目录」的分叉。）

### 8.1 回滚路径已验证：dump 恢复演练（2026-09-21）

在这之前，「旧容器 + 旧卷 + 一份 dump」只是**声称**能回滚 —— 没有人证明那份 dump 真的能恢复出一个可用的库。
2026-09-21 用**临时容器 + 临时卷**做了一次**完全非破坏性**的恢复演练，把它从「声称」变成「已验证」：

- dump 校验：`data/migration/watb-migration-20260920-152438.sql`，`wc -c` = 2,069,682 字节，
  `certutil -hashfile … MD5` = `17c4d94515e8955de1db16f64085501f`，`grep -c '^CREATE TABLE'` = 58，
  尾部 `-- Dump completed on 2026-09-20 15:24:39` 完整。
- 演练用的临时卷 `watb-drill-20260921` + 临时容器 `watb-drill-mysql-20260921`，镜像与生产库同源
  （`podman inspect watb-docker-mysql` → `docker.io/library/mysql:8.0`），端口发布 `127.0.0.1:13306`
  （**没有用 3306**，全程未连生产库做对比，只与基线文件比对）。
- 导入：`cat data/migration/watb-migration-20260920-152438.sql | podman exec -i watb-drill-mysql-20260921 mysql -uroot -p***`
  → `EXIT=0`，耗时 **4 秒**；恢复出 2 个库、58 张表；`SELECT VERSION()` = 8.0.46。
- **逐表行数一致**：演练库 58 张表逐表 `COUNT(*)` 与 `data/migration/.counts-post-exact.txt` 排序后 `diff`
  → **无输出**（58 = 58 张表，总行数 318 = 318）；不一致清单：**无**。
- 内容抽查：`RENDER_CONFIG` 1 行（内容是 dump 当时的快照）、`ARTICLE` 17 行 / `SUM(DELETED)=0` /
  `SUM(CHAR_LENGTH(CONTENT_HTML))` = 536794（正文不是空壳）；中文以 HEX 解码验证，字节层面完好。
- **演练残留已清理**：临时容器与临时卷都已删除，`podman volume ls` 恢复为演练前的 7 个卷；
  生产容器与旧库容器全程未被触碰。

**将来真要清理回滚路径时**（本轮【未执行】，等用户确认；顺序不能乱）：

```bash
# --- 第 0 步：删除前必须确认（这几项全过才动手）---
podman exec watb-docker-mysql mysqladmin -uroot -p$ENV_MYSQL_PASSWORD ping    # 期望 mysqld is alive
# 0.2 生产库逐表行数仍与基线一致（58 表 / 318 行）：现查后与基线排序比对
diff <(sort data/migration/.counts-post-exact.txt) <(sort /tmp/counts-now.txt) # 期望无输出
md5sum data/migration/watb-migration-20260920-152438.sql                       # 期望 17c4d945…501f
# 0.4 确认没有别的容器还引用旧卷（应为空）
podman ps -a --filter volume=050c8ebd91877df65f48305b6fc095390dfa0463d06b19d365855badbe55d802
podman volume ls > /tmp/volumes-before-cleanup.txt && podman ps -a > /tmp/ps-before-cleanup.txt

# --- 第 1 步：先容器后卷（旧卷 LINKS=1，容器不删则卷删不掉）---
podman stop watb-dev-mysql
podman rm   watb-dev-mysql
podman volume rm 050c8ebd91877df65f48305b6fc095390dfa0463d06b19d365855badbe55d802

# --- 第 2 步：只有在确认 dump 已另有异地副本后，才删本机 dump 与计数文件 ---
# rm data/migration/watb-migration-20260920-152438.sql
# rm data/migration/.counts-post-exact.txt data/migration/.counts-pre-exact.txt \
#    data/migration/.last-dump-path data/migration/MIGRATION-REPORT.md

# --- 第 3 步：校验 ---
podman ps -a         # 期望只剩 nostalgic_mcnulty / watb-docker-mysql / watb-app
podman volume ls     # 期望只剩原有卷（watb-mysql-data / watb-uploads / be07… / 67cb… / 3784… / c069…）

# --- 严禁使用 ---
# podman volume prune / podman system prune --volumes   ← watb-uploads 当前 LINKS=0，会被一起删掉
# podman volume rm <简写或通配>                          ← 必须写全名，绝不要用 050c* 之类
```

旧卷占用（2026-09-21 现查）：**303.8 MB**（= 303,815,503 字节）——两个口径一致
（`podman system df -v` 与虚拟机内 `sudo -n du -sb <_data>`）。卷路径（虚拟机内）：
`/home/user/.local/share/containers/storage/volumes/050c8ebd91877df65f48305b6fc095390dfa0463d06b19d365855badbe55d802/_data`；
宿主机的实际落盘在 podman machine 的 `ext4.vhdx`
（`C:\Users\WIN11\.local\share\containers\podman\machine\wsl\wsldist\podman-machine-default\ext4.vhdx`，
当前 9,277,800,448 字节 —— 删卷后该 vhdx **不会自动收缩**）。

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
> **本节是 2026-09-20 的存档，里面的数字（`Network=10.89.0.4`、`ls … | wc -l` = 313、
> `[volume] watb-uploads…` 这条挂载）此后都已变** —— 现查见 §1.1、上传目录现状见 §10.3。

---

## 10. 本轮运维加固（2026-09-21）

> 本节记录 2026-09-21 这一轮对容器形态的加固与验证，**全部为本机实测**。
> 数字同样会过期 —— 现查命令见 §1.1；容器定义的完整还原料见 `docs/dev/podman-containers-inspect.md`。

### 10.1 健康检查：两个容器都有了

| 容器 | 探针（`Config.Healthcheck.Test`） | 参数 |
| --- | --- | --- |
| `watb-app` | `bash -c "exec 3<>/dev/tcp/127.0.0.1/8081 && printf \"GET /api/health HTTP/1.0\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n\" >&3 && grep -q \" 200 \" <&3"` | start-period 90s / interval 15s / timeout 5s / retries 5 |
| `watb-docker-mysql` | `mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" >/dev/null 2>&1`（凭据取容器自身 env，命令行里不出现明文） | start-period 60s / interval 10s / timeout 5s / retries 10 |

- app 的探针为什么不用 curl/wget：基础镜像 `eclipse-temurin:17-jre-noble` 里没有它们，但有 bash 5.2，
  `/dev/tcp` 是 bash 内建重定向，不需要任何额外二进制（注意是斜杠形式 `/dev/tcp/HOST/PORT`）。
- **这两个 healthcheck 是加在容器层的**（`podman run --health-cmd …`），与镜像无关。
- 镜像里也已经写了 `HEALTHCHECK`（`Dockerfile` 末尾），但**必须用 `podman build --format docker` 构建**才会生效 ——
  `podman build` 默认输出 OCI 格式，而 HEALTHCHECK 不是 OCI 规范的一部分，会被**静默忽略**（只打一句 warning）。
  实测对比见 `docs/dev/podman-containers-inspect.md` §A.3。
- 查看方式：

  ```bash
  podman ps --format '{{.Names}} {{.Status}}'                     # 括号里的 (healthy) 就是它
  podman inspect watb-app --format '{{.State.Health.Status}}'     # healthy / starting / unhealthy
  podman inspect watb-app --format '{{json .Config.Healthcheck}}' # 探针定义
  podman inspect watb-app --format '{{json .State.Health}}'       # 含最近几次探测的退出码与输出
  ```

  2026-09-21 现查：`watb-docker-mysql|Up 22 minutes (healthy)`、`watb-app|Up 22 minutes (healthy)`。

### 10.2 启停脚本：宿主重启后的标准恢复路径

本机没有 compose provider（见 §10.5），所以「谁先起来」这件事靠脚本兜：

- `scripts/watb-start.sh` —— **宿主重启后跑这一条**：起 db → 轮询到 db healthy → 起（或重启）app →
  轮询到 app healthy → 打印最终状态。
  - 幂等：全绿时再跑**不会**无谓重启；app 在跑但不 healthy（典型的「先于 db 起来、连不上库」）才 `restart`。
  - 只 `start` / `restart`，**从不 `rm`**。退出码：`0` 全部 healthy / `1` 超时未达 healthy 或容器没有 healthcheck /
    `69` 找不到 podman 或容器不存在（提示照还原料重建）。
  - 可选 `--timeout <秒>`（默认 240，可用 `WATB_TIMEOUT_S` 覆盖）。
- `scripts/watb-stop.sh` —— 停两个容器，**先 app 后 db**（让应用先把在途请求收干净），只 `stop` 不 `rm`、不删卷。
  停之前会查「在途采集任务」条数（`TASK_RUN` 里 `STATUS not in ('SUCCESS','SUCCESS_WITH_WARNINGS','FAILED')`），
  非 0 就拒绝停止，`--force` 才强停。
- 以前宿主重启后要人工判断顺序并 `podman restart watb-app`（见 §3 末尾），现在一条命令。

### 10.3 上传目录：宿主 `data\uploads` 是唯一事实来源

`watb-app` 的 `/app/data/uploads` 现在是**宿主目录 bind mount**
（`-v D:/project/wwwroot/wechat-article-bot/data/uploads:/app/data/uploads`），不再是命名卷 `watb-uploads`。

为什么这样选（实测依据见 `docs/dev/podman-containers-inspect.md` §A.2）：切之前两边已经**分叉** ——
卷里 322 个文件、宿主 313 个，宿主是严格子集、共有文件内容 **0 处不同**；而卷里多出的 9 个是**活的**
（9 个在 `ASSET` 表都有行，其中 3 篇 `ARTICLE.CONTENT_HTML` 引用了它们），直接切会让 9 个 URL 404。
按内容比对补齐 9 个（宿主 313 → 322）之后再切，`diff -rq` 两侧完全一致。
于是「看宿主即看全部」，不用再反向同步（§6.1 那套反向拷贝因此只对历史快照有意义）。

现查（2026-09-21）：宿主 `data\uploads` **323** 个文件、容器内 `/app/data/uploads` **323** 个 —— 同一份目录。
卷 `watb-uploads` **未删**，留作对照/回退。

### 10.4 `watb-test-mysql` 已移除（只删容器，卷保留）

- **原因**：它声明着 `0.0.0.0:3306` 端口映射，一旦被启动就会和 `watb-docker-mysql`（`127.0.0.1:3306`）
  抢 3306 —— 是隐患而不是现状（它当时处于 `Exited`）。
- **处置**：`podman rm watb-test-mysql`，**只删容器**。它挂的卷是 `050c8ebd…`，
  **与 `watb-dev-mysql` 是同一个卷**（回滚路径），所以卷保留未动。
- 2026-09-21 现查：`podman ps -a` 里已无 `watb-test-mysql`。
- 它的**原始容器定义**（字段表 + `CreateCommand`）保存在 `docs/dev/podman-containers-inspect.md` §3，
  要复原照那份还原料抄。

### 10.5 为什么用脚本而不是 compose

本机 **没有任何 compose provider**：`docker compose` / `podman compose` 都不可用。容器是手写 `podman run`
起的，没有 `compose.yaml`、没有 `depends_on`、没有编排器帮我们排顺序。所以：

- 「db 先于 app 就绪」由 `scripts/watb-start.sh` 用健康检查轮询实现（等价于 `depends_on: condition: service_healthy`）；
- 容器定义本身仍然只存在于命令与还原料里（`docs/dev/podman-containers-inspect.md`），
  `podman rm` 掉就没有东西能把它变回来 —— 这也是两个脚本坚持「只 stop，从不 rm」的原因。

### 10.6 定案：`site_base_url`（本站公网地址）留空

`RENDER_CONFIG.site_base_url` 原为 `http://127.0.0.1:8081`（回环地址）。2026-09-21 定案**清空**（库中为 `NULL`）：

- **实测上游对图片 URL 是纯透传**：不可达主机（`https://probe.invalid.test/uploads/…`）与可达公网图
  （`https://www.baidu.com/img/flexible/logo/pc/result.png`）都在渲染产物里**逐字原样保留** ——
  没有 CDN 转存、没有 `data:` base64。所以「绝对化」对「渲染能否成功」没有任何作用。
- **回环值是假的「已配置」信号**：它恰好压掉了 `MarkFlowRenderService.java:237` 那条有意的告警
  （「未配置站点公网地址 site_base_url」），而且当站内素材名不是 32 位 hex 时，`127.0.0.1` 会真的被烧进正文 ——
  实测复现：`ARTICLE` id=24 的 `CONTENT_HTML` 里 `LOCATE('127.0.0.1')=828`，清空后同一篇为 0。
- **当时（2026-09-21 上午）仓库与配置里确实找不到公网地址**：`README_CN.md` 只说「生产建议前置 HTTPS 反向代理」，
  `deploy/env/*.env.example` 只有 `APP_BIND_ADDRESS`，所以没有可填的真值 —— 留空胜过填一个假的/回环的地址。
  **这个判断当天晚上就被推翻了**：服务器上一直有一条 aaPanel 反代规则指向 8081，域名是
  `https://mozhou.bx9y.com.cn`（详见附录 A.6）。也就是说真值是有的，只是当时没找到。
- **已于 2026-09-21 当晚填上**：`PUT /api/settings/render` 把 `siteBaseUrl` 设为
  `https://mozhou.bx9y.com.cn`（服务器容器，经 `probe_api.py` 走应用自己的接口，凭据未打印）。
  库里现在是 `SITE_BASE_URL=https://mozhou.bx9y.com.cn`、`BASE_URL=https://www.bx9y.com.cn`、
  `ENABLED=0`、`TOKEN_ENCRYPTED=NULL` —— **渲染服务本身仍未启用**（没有令牌，启用会被拒），
  `site_base_url` 只是先把真值备好，等配了令牌一起生效。
  **只改了服务器那份**：本机 podman 没有公网地址，继续留 NULL，填域名反而会让本机文章的图片指向服务器。
- **怎么设**：系统设置 → 排版渲染服务 →「本站公网地址（Site Base URL）」
  （`webui/src/views/SettingsView.vue:65`），填本应用对公网可访问的**根地址**，形如 `https://article.example.com` ——
  只填 scheme + host，**不带结尾斜杠、不带 `/uploads/`、不带任何路径**（代码自己拼 `{该值}/uploads/…`；
  见 `MarkFlowRenderService.absoluteImageUrls`）。等价的接口写法是 `PUT /api/settings/render` 的 `siteBaseUrl`，
  传空串即清空（不会触碰令牌密文）。
- 复验（2026-09-21 实跑）：清空后 `POST /api/articles/23/rerender` → HTTP 200 `success=true`，
  产物两张站内图都是 `<img src="/uploads/…">`；落库正文仍是相对路径；告警按设计出现。
  令牌与 `base_url` 未动（密文长度与哈希指纹改动前后一致），21 篇历史文章一行未改。
- 回滚材料：`target/probe-site-base-url/render-config-before.json`（只记结构化字段、密文长度与哈希指纹，
  **不含明文令牌与密文**）；一键恢复 `python target/probe-site-base-url/probe_api.py put 'http://127.0.0.1:8081'`
  （已实跑验证；该命令只回传 baseUrl/siteBaseUrl/TTL/enabled，token 传空串即不修改令牌）。

> 注：`webui/src/views/SettingsView.vue:65` 的提示语本轮**没有改**（本轮不改代码），它仍写着
> 「用于把文章图片 /uploads/ 相对路径转为渲染服务可访问的绝对直链」。按上面的实测，这句话只在
> 「上游改成真的抓图」或「站内素材不是 32 位 hex 命名」时才成立。

---

# 附录：服务器部署（腾讯云，与本地并存）

> **两个环境现在同时在跑，别混淆**：
> - **本机**：podman，`watb-app` + `watb-docker-mysql`（本文正文 §1–§10）。
> - **服务器**：腾讯云（SSH 别名 `tencent`），Docker Compose，`wechat-article-bot-app-1` + `wechat-article-bot-mysql-1`。
>
> 2026-09-21 的经过：上午先在服务器上替换部署（`guoshengkai/wechat-article-bot:latest` → 本地构建的
> `wechat-article-bot:20260921`）；当天稍晚按用户决定**整体下线**（「全部以本地为主，线上的全部清除都可以，
> 里边是测试数据」），容器/网络/镜像/工程目录全删；**当天晚些时候又要求重新部署**，于是按本附录重建，
> 镜像 tag 改为 `20260921-r2`（commit `ab50556`），**数据库是全新的空库**（29 张表由应用启动时自建，
> SKILL 18 行内置技能，ARTICLE/ASSET/TASK_RUN/RENDER_CONFIG/LLM_PROFILE 均为 0）。
>
> 下线前的那份旧数据 dump 仍在服务器 `/www/dk_project/dk_app/backups/wechat-article-bot-final-20260921/`
> （`wechat-article-final.sql`，918K，md5 `15fc87e78aedd309153abcf1a13132e6`，29 张表，另有 env 原件）。
> **没有导入**——上传文件已随下线删除，导回来只会得到一堆图裂的旧文章。需要时再手动导入。
>
> 本附录真正要留的两条经验与服务器在不在无关：**schema 漂移会让应用直接起不来**（A.3）、
> **换镜像后必须同步 env 文件里的 `IMAGE_REPOSITORY` / `IMAGE_TAG`**（A.2 末尾）。

## A.1 服务器与工程位置

- SSH 别名 `tencent`（`~/.ssh/config`，root）；主机 `VM-8-13-opencloudos`，x86_64，4C3.6G，Docker 28.0.1 + Compose v2.39.1。
  **这台机器上还跑着 new-api / WeKnora / favshub / qinglong 等一堆别的容器，操作前先 `docker ps` 核对名字。**
- 工程目录：`/www/dk_project/dk_app/wechat-article-bot`，compose 工程名 `wechat-article-bot`。
- 容器：`wechat-article-bot-app-1`（127.0.0.1:8081→8081，`USER 10001:10001`，`unless-stopped`）与
  `wechat-article-bot-mysql-1`（mysql:8.4，127.0.0.1:13307→3306，带 healthcheck）。
- 数据是 **bind mount**（不是命名卷）：`./data/uploads → /app/data/uploads`、`./data/mysql → /var/lib/mysql`。
  **换机器/重建容器不会丢数据，但删目录会。**
- compose 是 **`compose.yaml` + `compose.dev.yaml` 两份叠加**（dev 那份提供 app 的 build 段、
  `depends_on: mysql:service_healthy`、mysql 服务定义），env 文件是 `deploy/env/dev.env`（600 权限，真实凭据在里面）。
- ⚠️ 服务器上的 git 检出跟踪的是 `github.com/onlyGuo/wechat-article-bot`（旧上游，commit `78f560f`），
  且 `compose.yaml` / `compose.dev.yaml` **有本地未提交改动**。**不要在服务器上 `git pull`** —— 会覆盖掉本地适配。

## A.2 部署方式：在服务器上本地构建，不推镜像仓库

本机没有 registry 凭据，所以不走「推 Docker Hub → 服务器 pull」，而是**源码上传 + 服务器本地 build**：

```bash
# 1) 本机生成干净源码（git archive，排除 target/node_modules）
git archive HEAD | tar -x -C target/deploy-src
tar -czf target/wab-src.tar.gz -C target/deploy-src .
scp target/wab-src.tar.gz tencent:/www/dk_project/dk_app/wechat-article-bot/

# 2) 服务器上构建（4 核约 6 分钟）
ssh tencent 'cd /www/dk_project/dk_app/wechat-article-bot && mkdir -p /tmp/wab-build && \
  tar -xzf wab-src.tar.gz -C /tmp/wab-build && \
  cd /tmp/wab-build && DOCKER_BUILDKIT=1 docker build --target runtime \
    --build-arg APP_VERSION=20260921 --build-arg VCS_REF=$(git rev-parse HEAD) \
    -t wechat-article-bot:20260921 -t wechat-article-bot:latest .'

# 3) 替换 app 容器（mysql 不动）
ssh tencent 'cd /www/dk_project/dk_app/wechat-article-bot && \
  IMAGE_REPOSITORY=wechat-article-bot IMAGE_TAG=20260921 \
  docker compose --env-file deploy/env/dev.env -f compose.yaml -f compose.dev.yaml up -d --no-deps app'
```

**第 3 步之后必须改 `deploy/env/dev.env` 的 `IMAGE_REPOSITORY` / `IMAGE_TAG`**，让它们指向本次构建的镜像。
否则下次任何人跑 `docker compose up -d`，compose 会按 dev.env 里的旧值（原为
`docker.io/guoshengkai/wechat-article-bot:latest`）把容器**拉回 Docker Hub 的旧镜像**。

`docker build` 默认产出 docker 格式镜像，所以 `Dockerfile` 里的 `HEALTHCHECK` 会生效
（本机 podman 默认 OCI 格式反而会静默忽略它，见 §10.1）。

## A.3 ⚠️ 升级前必须对比列长（schema 漂移会直接搞挂生产）

生产库是旧版本代码建的，列长可能和当前实体声明不一致。2026-09-21 替换时实际撞到一次：

| 项 | 生产实际 | 当前代码声明 |
| --- | --- | --- |
| `ASSET.DESCRIPTION` | `varchar(1000)`，3 行超 500 字符（最长 689） | `varchar(500)`（`Asset.DESCRIPTION_MAX_LENGTH`） |

smart-mybatis 启动同步会执行 `MODIFY COLUMN ... VARCHAR(500)`，MySQL 拒绝收缩已有数据 →
**应用启动失败、按 `unless-stopped` 反复重启**（症状：`docker ps` 显示 `Restarting (1)`，日志里
`Data truncated for column 'DESCRIPTION' at row 2`）。

处理顺序：**先备份 → 截断超长值 → 再启动**。

```bash
# 备份（整库 + 单独存档超长行）
docker exec wechat-article-bot-mysql-1 sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysqldump -uroot \
  --single-transaction --quick wechat-article' > data/backup/pre-deploy.sql
docker exec -i wechat-article-bot-mysql-1 sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot -D wechat-article' <<'SQL'
UPDATE ASSET SET DESCRIPTION = LEFT(DESCRIPTION, 500) WHERE CHAR_LENGTH(DESCRIPTION) > 500;
SQL
```

**下次升级涉及声明了 `@TableField(length=...)` 的实体时，先在生产执行 `show create table <表>` 对比列长。**
顺带记一笔：`Asset.java` 的注释声称 smart-mybatis「不会改已有列的长度」，与实测不符（3.0.1 会 MODIFY），
那条注释本身是错的，别信它做决策。

## A.4 旧数据备份与回滚

服务器 2026-09-21 重建时用的是**全新空库**，下线前那份旧数据留在
`/www/dk_project/dk_app/backups/wechat-article-bot-final-20260921/`：

| 文件 | 内容 |
| --- | --- |
| `wechat-article-final.sql` | 下线前的整库 dump（918K，md5 `15fc87e78aedd309153abcf1a13132e6`，29 张表） |
| `dev.env` | 服务器那份 env 原件（含真实凭据，600 权限） |
| `asset-long-descriptions-20260921.txt` | 因列长冲突被截断的 3 行图片描述原文 |
| `uploads-count.txt` | 下线时上传文件数（41） |

**导入旧库前先想清楚**：上传文件已随下线删除，导回来会得到一堆图裂的旧文章。真要导：
`docker exec -i wechat-article-bot-mysql-1 sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot' < dump.sql`。
确认不再需要就整套删掉这个目录。

## A.5 重建后的数据现状（2026-09-21 晚）

**全新空库**：29 张表由应用启动时自建；SKILL 18 行（内置技能，随启动写入），
ARTICLE / ASSET / TASK_RUN / LLM_PROFILE **均为 0 行**；上传目录为空（uid 10001 可写，已实测）。
即：服务器上是一个干净的起点，**LLM 配置、定时任务都还没配**——要真正用起来，
需要登录后配 LLM。

**2026-09-21 晚又动了两处**：

1. `RENDER_CONFIG` 从 0 行变成 1 行（调用设置接口时由 `RenderConfigService.required()` 自动建行），
   当前值 `PROVIDER=MARKFLOW`、`BASE_URL=https://www.bx9y.com.cn`、`SITE_BASE_URL=https://mozhou.bx9y.com.cn`、
   `ENABLED=1`、`TOKEN_ENCRYPTED` 已存（104 字符）、`SYNTAX_CACHE_TTL_SECONDS=21600`。
   **渲染服务至此是可用的**：令牌取自本机那份（本机 `RENDER_CONFIG` 里加密存的，用本机 `APP_SECRET_KEY`
   解密取出明文，再经服务器的 `PUT /api/settings/render` 用**服务器自己的** `APP_SECRET_KEY` 重新加密落库 ——
   密文不能跨实例拷贝，必须走明文重加密）。
   验证做了三层：应用自带的 `POST /api/settings/render/test` 返回 `ok=true`（语法指令 8010 字符）；
   从**服务器出口**用同一令牌 POST 真实渲染成功（`ok=true`，323 字节 HTML）；
   对照组（错误令牌）被拒 401 `X-Render-Token 无效`。
   注意渲染服务的 `GET /__markflow_render` 是**公开**的（不带/带错令牌都返回语法指令），
   **只有 POST 才校验令牌** —— 所以「GET 通」不能当令牌有效的证据，必须 POST。
2. 管理员密码已重置（详见 A.7）。

**仍待配置**：定时任务（`TASK_RUN` 0 行）。LLM 与渲染服务均已配好可用（LLM 见 A.8）。

旧环境（已删除）当时的数据：ARTICLE 19 / ASSET 41 / TASK_RUN 18（SUCCESS 17 + FAILED 1）/ LLM_PROFILE 13，
一个 trigger（`task-trigger-1`，原下次触发 2026-09-22 09:00），`RENDER_CONFIG` 0 行。备份见 A.4。

## A.6 公网域名与反代（aaPanel / 宝塔）

**域名 `https://mozhou.bx9y.com.cn` 一直存在**，反代规则在 **aaPanel（宝塔）的 vhost** 里，不在 `/etc/nginx`：

```
/www/server/panel/vhost/nginx/mozhou.bx9y.com.cn.conf      # 生效的规则（注意有 .conf 后缀）
/www/server/panel/vhost/cert/mozhou.bx9y.com.cn/            # fullchain.pem + privkey.pem
/www/wwwlogs/mozhou.bx9y.com.cn.log                         # 访问日志
```

规则本体：`listen 80` + `listen 443 ssl http2`，`server_name mozhou.bx9y.com.cn`，
`location ^~ /` 整站 `proxy_pass http://127.0.0.1:8081`，即**所有路径都透传给 app 容器**
（`/api/*`、`/assets/*`、`/uploads/*` 一视同仁，没有按路径拆分到别处）。
两个值得记住的点：

- `proxy_set_header Origin "";` —— 上游 `SecurityConfig` 的 CORS 白名单只放了 localhost，
  同域部署下浏览器发的 ES module 请求带 `Origin` 会被 403，所以反代主动抹掉它。
- `proxy_buffering off` + 读写超时 600s —— 长文章渲染/生成接口需要。

**证书**：CN=`mozhou.bx9y.com.cn`，SAN 只有这一个 DNS，2026-09-07 签发、**2026-12-06 到期**，
路径 `/www/server/panel/vhost/cert/mozhou.bx9y.com.cn/`。到期要在 aaPanel 里续，别等它自己红。

**续期是 acme.sh + HTTP-01 webroot 验证，不是 DNS 验证**（`/root/.acme.sh/mozhou.bx9y.com.cn_ecc/mozhou.bx9y.com.cn.conf`
里 `Le_Webroot=/www/wwwroot/mozhou.bx9y.com.cn`），下次自动续期 **2026-11-06**，crontab 每天 0/6/12/18 点跑
`acme.sh --cron`。**这意味着 80 端口的 `/.well-known/acme-challenge/` 必须保持明文可达** ——
谁要是图省事写一个「80 全站 301 到 https」，续期会在 11 月静默失败，然后在 12 月证书过期时才发现。
下面的强制跳转就是为这条开的例外。

**强制跳转（2026-09-21 加）**：原来 80 和 443 在同一个 server 块里，`http://` 直接就是 200。
现在拆成两个 server 块，80 只做 `return 301 https://$host$request_uri;`，
**例外是 `location ^~ /.well-known/acme-challenge/`**（照旧从 webroot 供文件，保续期）。
改动只新增了 80 块并从原块里删掉 `listen 80;` 一行，**443 块逐行未动**（改前改后各 96 行、逐行相等，脚本比对过）。
原件备份在 `/www/server/panel/vhost/nginx/mozhou.bx9y.com.cn.conf.bak-20260921`。

> 注意这个文件在 aaPanel 里注册过，**在面板上再点一次这个站点的 SSL/反代设置可能覆盖手改**。
> 真要改，优先用面板自带的「强制 HTTPS」开关（它生成的形态和这里一致，只是**不开** acme 例外，
> 开了之后要自己把例外补回去）。

**实测（2026-09-21，全部经公网域名）**：

| 探测 | 结果 |
| --- | --- |
| `GET /api/health` | 200，`{"status":"UP"}`，71 ms |
| `GET /` | 200，SPA `index.html`（Vite 产物，引用 `/assets/index-*.js`） |
| `GET /assets/index-Dwrpkxos.js` | 200，121 KB（前端是真的在镜像里，见 `Dockerfile:18`） |
| `GET /assets/index-Bf2z4UXT.css` | 200，58 KB |
| `GET /favicon.svg` | 200，9.5 KB |
| `GET /api/articles`（无 token） | 401（鉴权按设计拦截） |
| `http://`（80 端口） | **301 → 同址 https**（2026-09-21 加的强制跳转，例外见上） |
| `http:///.well-known/acme-challenge/<file>` | 200，**不跳转**，原样返回 challenge 文件内容（保 acme.sh 续期） |

`/favicon.ico`、`/assets`（当目录列）、`/uploads/` 会返回 500 —— 这三个路径本来就不存在
（图标是 `/favicon.svg`，`/assets` 不是文件），容器内直连 8081 同样行为，与反代无关。

**教训（我在这上面错过两次）**：这台机器的 nginx 配置分散在三处 —— `/etc/nginx/`（几乎没有业务站点）、
`/www/dk_project/dk_app/*/nginx.conf`（各个 Docker 项目自带的）、以及 **aaPanel 的
`/www/server/panel/vhost/nginx/*.conf`**。找反代规则必须三个地方都搜，
`grep -rl "域名\|8081" /www/server/panel/vhost/nginx/` 一行就够，漏了它就会得出「没有域名」的错误结论。
另外服务器上**没有装 `rg`**，`grep -r` 才是可靠选项。

## A.7 管理员凭据（2026-09-21 重置）

用户名 **`huanyu@2026`**，密码是 20 位随机字母数字，**密码不明文写在任何文档/仓库里**。
明文只落在服务器一个文件：`/www/dk_project/dk_app/backups/admin-credential-20260921.txt`
（600，仅 root 可读，含 username/password 两行）。抄走后可删。

> 用户名的经过：先是把默认的 `admin` 改成 12 位随机小写字母，随后用户指定为 `huanyu@2026`。
> 每次改动都同步了 `deploy/env/dev.env` 的 `ADMIN_USERNAME`（备份 `.bak-before-rename2`），
> 并实测「新用户名登得上、旧用户名登不上」。

同步做了两件事，否则会留下「能登录但文件是旧值」的坑：

- `deploy/env/dev.env` 的 `ADMIN_USERNAME` / `ADMIN_PASSWORD` 已改成当前值
  （原件备份 `dev.env.bak-20260921-pwreset` / `dev.env.bak-before-rename2`），
  并用文件里的凭据实测登录 200 闭环。
- 应用侧走的是 `PUT /api/auth/password`（需要当前密码），**不是直接改库**。
  这个接口会顺手 `tokenMapper.deleteByUserId()` 把所有现存 token 作废 —— 改完自己重新登录即可。

**重置密码不会在下次重启后被环境变量覆盖**：`BootstrapAdminRunner.run()` 第一行是
`if (userMapper.count() > 0) return;`，只在用户表为空时才建号。所以改过的密码是持久的，
`ADMIN_PASSWORD` 只在**全新空库首次启动**时才起作用。

**没有改用户名的 HTTP 接口**（`AuthController` 只有 login / logout / changePassword），
所以用户名是直接改库的：`UPDATE SYS_USER SET USERNAME='<新值>' WHERE ID=1;`。
这么做是安全的，因为：① 只有 `SYS_USER.USERNAME` 存用户名，`AUTH_TOKEN` / `AUDIT_LOG`
都只存 `USER_ID`；② `AuthService.authenticate()` 是按 token 里的 userId 查人再取 username，
所以**改用户名不会让已登录的 token 失效**（实测改后仍有 12 个未过期 token 正常）。
用户名**没有任何格式/长度校验**（只有 `@NotBlank`，列是 `varchar(255)`），
所以 `huanyu@2026` 这种带 `@` 的值可以直接用，JSON 传参也安全。
改完必须重新登录一次确认新用户名能登、旧用户名登不上。

> ⚠️ **这台机器有文件可见性延迟**：刚写好的文件，紧接着在新进程里可能读不到
> （`ls` / `stat` / `find` 都看得见，`cp` / Python `open()` 却报不存在），隔几秒恢复。
> 改完凭据/env 文件**不要在同一进程里立刻读回校验**，分两步、中间留几秒。
> 根因未明（不是权限、不是挂载，相关路径同在 `/dev/vda1`）。

## A.8 LLM 配置与模型档案（2026-09-21 从本机迁移）

服务器原本 `LLM_PROFILE` 0 行、`LLM_CONFIG` 是默认空值。按用户要求把本机那份搬了过去，
**全部走应用自己的 API，没有直接写库**：

- 12 条模型档案经 `POST /api/llm-profiles` 逐条创建，然后
  `POST /{id}/set-default`（`deepseek-flash`）与 `POST /{id}/set-fallback`（`hy4-preview`）。
- 全局设置经 `PUT /api/settings/llm` 写入（provider / baseUrl / modelName / apiKey /
  temperature / maxTokens / enabled + 图片三件套 imageBaseUrl / imageModelName / imageApiKey）。
  注意全局的图片模型是 `sensenova-u1.5-fast`，而默认档案的图片模型是 `sensenova-u1.5-lite`，
  **两者不同，都按原样搬了** —— 前者管「没指定档案时」的配图，后者是默认档案自己声明的。

**密钥为什么必须走 API 而不是拷密文**：`CryptoService` 是 AES-256-GCM，
key = `SHA256(APP_SECRET_KEY)`，两台机器的 `APP_SECRET_KEY` 不同，密文搬过去解不开。
正确路径：本机库 `*_ENCRYPTED` → 用**本机** key 解密出明文 → 明文经 API 传给服务器 →
服务端用**服务器自己的** key 重新加密落库。（渲染令牌那次是同样的路子。）

**验证做了真调用，不是看字段**：用新凭据登录后 `POST /api/articles/{id}/ai/chat`
发一条「不要调用工具、直接回复指定六个字」的指令，SSE 流式逐字返回了预期内容
（7 个事件、0 错误）。自检文章已删除，`ARTICLE` 回到 0 行。
另外复核了 12 条档案 `hasApiKey` 全部为真、默认/兜底标记正确。
