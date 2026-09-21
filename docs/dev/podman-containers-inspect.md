# podman 容器运行态参考（还原料）

> 用途：本机 **没有任何 compose provider**（`docker compose` / `podman compose` 均不可用），
> 容器全部由手写 `podman run` 起。本文件是**一旦改坏时的还原料**：记录两个在跑容器的完整定义，
> 以及 `watb-test-mysql` / `watb-dev-mysql` 两个已停容器的定义。
>
> **env 中凡匹配 `PASSWORD|PASSWD|PWD|TOKEN|SECRET|KEY|CREDENTIAL` 的值一律打码**，
> `CreateCommand` 里的 `MYSQL_ROOT_PASSWORD` 同样打码。密码真值只在容器自身 env 里，取用方式见文末。
>
> 重新生成：
> ```bash
> PODMAN="/c/Program Files/RedHat/Podman/podman.exe"
> "$PODMAN" inspect watb-app watb-docker-mysql watb-test-mysql watb-dev-mysql > /tmp/inspect.json
> ```
> 抓取时间：2026-09-21（Asia/Shanghai）。podman client 5.8.3 / server 5.8.6。

## 0. 抓取时的现场

```
$ podman ps -a --format "{{.ID}}|{{.Names}}|{{.Image}}|{{.Status}}|{{.Ports}}"
df20129b5037|nostalgic_mcnulty|quay.io/podman/hello:latest|Exited (0) 4 days ago|
dc37ccfe148a|watb-test-mysql|docker.io/library/mysql:8.0|Exited (0) 292 years ago|0.0.0.0:3306->3306/tcp, 33060/tcp
ab2adaadffff|watb-dev-mysql|docker.io/library/mysql:8.0|Exited (0) 20 hours ago|127.0.0.1:3306->3306/tcp, 33060/tcp
f28eb62e9c01|watb-docker-mysql|docker.io/library/mysql:8.0|Up 2 hours|127.0.0.1:3306->3306/tcp, 33060/tcp
d26adaea2318|watb-app|localhost/wechat-article-bot:local|Up 2 hours|127.0.0.1:8081->8081/tcp
```

卷与网络：

```
$ podman volume ls
local  watb-mysql-data          # 数据库数据（watb-docker-mysql 在用）
local  watb-uploads             # 应用上传目录（迁移后成为唯一写入点）
local  050c8ebd91877df65f48305b6fc095390dfa0463d06b19d365855badbe55d802   # watb-dev-mysql 与 watb-test-mysql 共用（回滚路径）
local  be070a9a… / 67cbe396… / 3784ca18… / c0691fdb…   # 其它（非本项目，未触碰）

$ podman network ls
NETWORK ID    NAME        DRIVER
2f259bab93aa  podman      bridge
47285c55d119  watb-net    bridge
```

宿主机 `D:` 在 podman machine（Linux VM）里挂在 `/mnt/d`，所以宿主路径
`D:\project\wwwroot\wechat-article-bot\...` 在 inspect 里显示为 `/mnt/d/project/wwwroot/wechat-article-bot/...`。

## 1. watb-app（应用）

**作用**：应用容器。以 `10001:10001` 运行，监听 8081，只发布到 `127.0.0.1`。
`/app/.env` 由宿主 `.env` 只读 bind 进来；`SPRING_APPLICATION_JSON` 把 datasource URL 指向
`watb-docker-mysql:3306`（覆盖 `.env` 里的 `localhost`）。`/app/data/uploads` 原来挂的是卷 `watb-uploads`。

| 字段 | 值 |
|---|---|
| `ContainerName` | `watb-app` |
| `ContainerId` | `d26adaea2318b06c9e333b3ea501e70d06df768d6c8d012dd4cfd3c4ef78c9ed` |
| `ImageRef` | `localhost/wechat-article-bot:local` |
| `ImageId` | `55c505534ffdb94b8447c18e1254c2069b45d345840ff4a056a4c61a5e5b35ea` |
| `Created` | `2026-09-20T16:05:48.472400864+08:00` |
| `State.Status` | `running` |
| `State.ExitCode` | `0` |
| `RestartPolicy` | `unless-stopped` |
| `AutoRemove` | `False` |
| `Config.User` | `10001:10001` |
| `WorkingDir` | `/app` |
| `Entrypoint` | `["java", "-jar", "/app/app.jar"]` |
| `Cmd` | `null` |
| `ExposedPorts` | `["8081/tcp"]` |
| `PortBindings` | `{"8081/tcp": [{"HostIp": "127.0.0.1", "HostPort": "8081"}]}` |
| `Networks` | `["watb-net"]` |
| `Healthcheck` | `null` |
| `Privileged` | `False` |
| `UsernsMode` | `` |
| `ExtraHosts` | `[]` |
| `StopTimeout` | `` |
| `Labels` | `{"io.buildah.version": "1.43.2", "org.opencontainers.image.description": "AI-powered WeChat article management workspace", "org.opencontainers.image.revision": "unknown", "org.opencontainers.image.title": "wechat-article-bot", "org.opencontainers.image.version": "dev"}` |

**Env（密钥已打码）**

```
PATH=/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
container=podman
JAVA_HOME=/opt/java/openjdk
JAVA_VERSION=jdk-17.0.20+8
SERVER_PORT=8081
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai
STORAGE_PATH=/app/data/uploads
SPRING_APPLICATION_JSON={"spring":{"datasource":{"url":"jdbc:mysql://watb-docker-mysql:3306/wechat-article?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"}}}
LANGUAGE=en_US:en
LC_ALL=en_US.UTF-8
LANG=en_US.UTF-8
HOME=/app
HOSTNAME=d26adaea2318
```

**Mounts**

```json
[
 {
  "Type": "volume",
  "Name": "watb-uploads",
  "Source": "/home/user/.local/share/containers/storage/volumes/watb-uploads/_data",
  "Destination": "/app/data/uploads",
  "Driver": "local",
  "Mode": "",
  "Options": [
   "nosuid",
   "nodev",
   "rbind"
  ],
  "RW": true,
  "Propagation": "rprivate"
 },
 {
  "Type": "bind",
  "Source": "/mnt/d/project/wwwroot/wechat-article-bot/.env",
  "Destination": "/app/.env",
  "Driver": "",
  "Mode": "",
  "Options": [
   "rbind"
  ],
  "RW": false,
  "Propagation": "rprivate"
 }
]
```

**Binds**

```json
[
 "watb-uploads:/app/data/uploads:rprivate,nosuid,nodev,rbind",
 "/mnt/d/project/wwwroot/wechat-article-bot/.env:/app/.env:ro,rprivate,rbind"
]
```

**CreateCommand（密钥已打码）**

```
["C:\\Program Files\\RedHat\\Podman\\podman.exe", "run", "-d", "--name", "watb-app", "--network", "watb-net", "-p", "127.0.0.1:8081:8081", "--restart", "unless-stopped", "-v", "D:/project/wwwroot/wechat-article-bot/.env:/app/.env:ro", "-v", "watb-uploads:/app/data/uploads", "-e", "SPRING_APPLICATION_JSON={\"spring\":{\"datasource\":{\"url\":\"jdbc:mysql://watb-docker-mysql:3306/wechat-article?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true\"}}}", "localhost/wechat-article-bot:local"]
```

## 2. watb-docker-mysql（生产库）

**作用**：生产库容器（本机开发部署的事实库）。数据全在卷 `watb-mysql-data`，容器本身无状态。
只发布到 `127.0.0.1:3306`。

| 字段 | 值 |
|---|---|
| `ContainerName` | `watb-docker-mysql` |
| `ContainerId` | `f28eb62e9c014ea87eb35f0af76959f45852ce02075dbfe168d2996794c72b6a` |
| `ImageRef` | `docker.io/library/mysql:8.0` |
| `ImageId` | `6cd09145362dfe6831b14545de3d5fd6cc75c37cfd6ef8561429c1fc73518b39` |
| `Created` | `2026-09-20T15:32:05.85098237+08:00` |
| `State.Status` | `running` |
| `State.ExitCode` | `0` |
| `RestartPolicy` | `no` |
| `AutoRemove` | `False` |
| `Config.User` | `` |
| `WorkingDir` | `/` |
| `Entrypoint` | `["docker-entrypoint.sh"]` |
| `Cmd` | `["mysqld"]` |
| `ExposedPorts` | `["3306/tcp", "33060/tcp"]` |
| `PortBindings` | `{"3306/tcp": [{"HostIp": "127.0.0.1", "HostPort": "3306"}]}` |
| `Networks` | `["watb-net"]` |
| `Healthcheck` | `null` |
| `Privileged` | `False` |
| `UsernsMode` | `` |
| `ExtraHosts` | `[]` |
| `StopTimeout` | `` |
| `Labels` | `null` |

**Env（密钥已打码）**

```
MYSQL_VERSION=8.0.46-1.el9
MYSQL_SHELL_VERSION=8.0.46-1.el9
GOSU_VERSION=1.19
MYSQL_ROOT_PASSWORD=<REDACTED:9 chars>
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
container=podman
MYSQL_MAJOR=8.0
HOME=/root
HOSTNAME=f28eb62e9c01
```

**Mounts**

```json
[
 {
  "Type": "volume",
  "Name": "watb-mysql-data",
  "Source": "/home/user/.local/share/containers/storage/volumes/watb-mysql-data/_data",
  "Destination": "/var/lib/mysql",
  "Driver": "local",
  "Mode": "",
  "Options": [
   "nosuid",
   "nodev",
   "rbind"
  ],
  "RW": true,
  "Propagation": "rprivate"
 }
]
```

**Binds**

```json
[
 "watb-mysql-data:/var/lib/mysql:rprivate,nosuid,nodev,rbind"
]
```

**CreateCommand（密钥已打码）**

```
["C:\\Program Files\\RedHat\\Podman\\podman.exe", "run", "-d", "--name", "watb-docker-mysql", "--network", "watb-net", "-v", "watb-mysql-data:/var/lib/mysql", "-e", "MYSQL_ROOT_PASSWORD=<REDACTED>", "-p", "127.0.0.1:3306:3306", "docker.io/library/mysql:8.0"]
```

## 3. watb-test-mysql（已 Exited，仅删容器）

**作用**：历史遗留容器，`Exited`。**它声明了 `0.0.0.0:3306` 端口映射**，一旦被启动就会和
`watb-docker-mysql` 抢 3306。它挂的卷是 `050c8ebd91877df65f48305b6fc095390dfa0463d06b19d365855badbe55d802`
——**与 `watb-dev-mysql` 同一个卷**，即数据不在容器内、不在本项目专用卷里。因此**只删容器、不删卷**。

| 字段 | 值 |
|---|---|
| `ContainerName` | `watb-test-mysql` |
| `ContainerId` | `dc37ccfe148a334ef65dc1d8d9ee94145df14abd3255ee231e52750fca49c9d4` |
| `ImageRef` | `docker.io/library/mysql:8.0` |
| `ImageId` | `6cd09145362dfe6831b14545de3d5fd6cc75c37cfd6ef8561429c1fc73518b39` |
| `Created` | `2026-09-17T16:51:56.988120367+08:00` |
| `State.Status` | `exited` |
| `State.ExitCode` | `0` |
| `RestartPolicy` | `no` |
| `AutoRemove` | `False` |
| `Config.User` | `` |
| `WorkingDir` | `/` |
| `Entrypoint` | `["docker-entrypoint.sh"]` |
| `Cmd` | `["mysqld"]` |
| `ExposedPorts` | `["3306/tcp", "33060/tcp"]` |
| `PortBindings` | `{"3306/tcp": [{"HostIp": "0.0.0.0", "HostPort": "3306"}]}` |
| `Networks` | `["pasta"]` |
| `Healthcheck` | `null` |
| `Privileged` | `False` |
| `UsernsMode` | `` |
| `ExtraHosts` | `[]` |
| `StopTimeout` | `` |
| `Labels` | `null` |

**Env（密钥已打码）**

```
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
container=podman
GOSU_VERSION=1.19
MYSQL_MAJOR=8.0
MYSQL_VERSION=8.0.46-1.el9
MYSQL_SHELL_VERSION=8.0.46-1.el9
MYSQL_ROOT_PASSWORD=<REDACTED:9 chars>
HOME=/root
HOSTNAME=dc37ccfe148a
```

**Mounts**

```json
[
 {
  "Type": "volume",
  "Name": "050c8ebd91877df65f48305b6fc095390dfa0463d06b19d365855badbe55d802",
  "Source": "/home/user/.local/share/containers/storage/volumes/050c8ebd91877df65f48305b6fc095390dfa0463d06b19d365855badbe55d802/_data",
  "Destination": "/var/lib/mysql",
  "Driver": "local",
  "Mode": "",
  "Options": [
   "nodev",
   "exec",
   "nosuid",
   "rbind"
  ],
  "RW": true,
  "Propagation": "rprivate"
 }
]
```

**Binds**

```json
[
 "050c8ebd91877df65f48305b6fc095390dfa0463d06b19d365855badbe55d802:/var/lib/mysql:rprivate,rw,nodev,exec,nosuid,rbind"
]
```

**CreateCommand（密钥已打码）**

```
["C:\\Program Files\\RedHat\\Podman\\podman.exe", "run", "-d", "--name", "watb-test-mysql", "-e", "MYSQL_ROOT_PASSWORD=<REDACTED>", "-p", "3306:3306", "mysql:8.0"]
```

## 4. watb-dev-mysql（回滚路径 —— 禁止触碰）

**作用**：迁移前的旧库容器，`Exited`（StoppedByUser）。**这是回滚路径，任何情况下不得删除、不得启动、
不得触碰其卷。**它和 `watb-test-mysql` 共用卷 `050c8ebd91877df65f48305b6fc095390dfa0463d06b19d365855badbe55d802`。

| 字段 | 值 |
|---|---|
| `ContainerName` | `watb-dev-mysql` |
| `ContainerId` | `ab2adaadffff68c3ea3fa5660edb68d909594c3aef3984aa9f788bd565febb99` |
| `ImageRef` | `docker.io/library/mysql:8.0` |
| `ImageId` | `6cd09145362dfe6831b14545de3d5fd6cc75c37cfd6ef8561429c1fc73518b39` |
| `Created` | `2026-09-20T09:36:36.935682425+08:00` |
| `State.Status` | `exited` |
| `State.ExitCode` | `0` |
| `RestartPolicy` | `no` |
| `AutoRemove` | `False` |
| `Config.User` | `` |
| `WorkingDir` | `/` |
| `Entrypoint` | `["docker-entrypoint.sh"]` |
| `Cmd` | `["mysqld"]` |
| `ExposedPorts` | `["3306/tcp", "33060/tcp"]` |
| `PortBindings` | `{"3306/tcp": [{"HostIp": "127.0.0.1", "HostPort": "3306"}]}` |
| `Networks` | `["podman"]` |
| `Healthcheck` | `null` |
| `Privileged` | `False` |
| `UsernsMode` | `` |
| `ExtraHosts` | `[]` |
| `StopTimeout` | `` |
| `Labels` | `null` |

**Env（密钥已打码）**

```
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
container=podman
GOSU_VERSION=1.19
MYSQL_MAJOR=8.0
MYSQL_VERSION=8.0.46-1.el9
MYSQL_SHELL_VERSION=8.0.46-1.el9
MYSQL_ROOT_PASSWORD=<REDACTED:9 chars>
TZ=Asia/Shanghai
HOME=/root
HOSTNAME=ab2adaadffff
```

**Mounts**

```json
[
 {
  "Type": "volume",
  "Name": "050c8ebd91877df65f48305b6fc095390dfa0463d06b19d365855badbe55d802",
  "Source": "/home/user/.local/share/containers/storage/volumes/050c8ebd91877df65f48305b6fc095390dfa0463d06b19d365855badbe55d802/_data",
  "Destination": "/var/lib/mysql",
  "Driver": "local",
  "Mode": "",
  "Options": [
   "nosuid",
   "nodev",
   "rbind"
  ],
  "RW": true,
  "Propagation": "rprivate"
 }
]
```

**Binds**

```json
[
 "050c8ebd91877df65f48305b6fc095390dfa0463d06b19d365855badbe55d802:/var/lib/mysql:rprivate,nosuid,nodev,rbind"
]
```

**CreateCommand（密钥已打码）**

```
None
```

## 5. 密码真值怎么取（不要写进任何仓库文件）

容器自身 env 里带着凭据，用下面这条**不把明文回显到终端**的方式取：

```bash
PODMAN="/c/Program Files/RedHat/Podman/podman.exe"
PW=$("$PODMAN" inspect watb-docker-mysql --format '{{range .Config.Env}}{{println .}}{{end}}' \
     | grep '^MYSQL_ROOT_PASSWORD=' | sed 's/^MYSQL_ROOT_PASSWORD=//')
# 容器内直接用自身的 env，命令行里完全不出现密码：
"$PODMAN" exec watb-docker-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "select 1"'
```

## 6. 还原料 → 原样重建（**仅供回滚，不要日常执行**）

重建前先确认没有在跑的采集任务（见 `docs/dev/docker-deployment.md`）：

```sql
select count(*) from `wechat-article`.TASK_RUN where STATUS not in ('SUCCESS','SUCCESS_WITH_WARNINGS','FAILED');
```

`watb-app` 原始 run 命令（把 `<HOST>` 换成宿主路径；密码不在 app 的 env 里，无需处理）：

```bash
PODMAN="/c/Program Files/RedHat/Podman/podman.exe"
MSYS_NO_PATHCONV=1 "$PODMAN" run -d --name watb-app --network watb-net \
  -p 127.0.0.1:8081:8081 --restart unless-stopped \
  -v "<HOST>/wechat-article-bot/.env:/app/.env:ro" \
  -v watb-uploads:/app/data/uploads \
  -e 'SPRING_APPLICATION_JSON={"spring":{"datasource":{"url":"jdbc:mysql://watb-docker-mysql:3306/wechat-article?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"}}}' \
  localhost/wechat-article-bot:local
```

`watb-docker-mysql` 原始 run 命令（**密码从旧容器 env 取，不要手打**）：

```bash
PW=$("$PODMAN" inspect watb-docker-mysql --format '{{range .Config.Env}}{{println .}}{{end}}' \
     | grep '^MYSQL_ROOT_PASSWORD=' | sed 's/^MYSQL_ROOT_PASSWORD=//')
"$PODMAN" run -d --name watb-docker-mysql --network watb-net \
  -v watb-mysql-data:/var/lib/mysql \
  -e MYSQL_ROOT_PASSWORD="$PW" \
  -p 127.0.0.1:3306:3306 docker.io/library/mysql:8.0
```

> **硬约束**：不删卷、不删镜像、不碰 `watb-dev-mysql`。

---

# 附录 A：2026-09-21 变更后的运行态（本节由变更当天追加）

上面 §1–§4 是**变更前**的抓取，保留作为还原料。本节记录变更**之后**的实际状态，方便对照。
变更项（详见 §A.4 的动机）：

| # | 对象 | 变更 |
|---|---|---|
| 1 | `watb-app` 容器 | `/app/data/uploads` 由卷 `watb-uploads` 改成**宿主 bind mount** `D:\project\wwwroot\wechat-article-bot\data\uploads`；新增 healthcheck |
| 2 | `watb-docker-mysql` 容器 | 新增 healthcheck（`mysqladmin ping`，凭据取容器自身 env）；其余（卷/网络/端口/env/restart）原样保留 |
| 3 | `Dockerfile` | 新增 `HEALTHCHECK`（**必须 `podman build --format docker`，否则被静默忽略**，见 §A.3） |
| 4 | `watb-test-mysql` 容器 | **已 `podman rm`（只删容器）**；它挂的卷 `050c8ebd…` 是 `watb-dev-mysql` 的卷，卷保留 |
| 5 | `watb-uploads` 卷 | 仍在（**未删**），但已不再被任何容器挂载——它是变更前上传目录的快照，留作对照 |
| 6 | 新增脚本 | `scripts/watb-start.sh`、`scripts/watb-stop.sh`（替代不存在的 compose） |
| 7 | `data/uploads` 宿主目录 | 从 313 个文件补到 **322 个**（从 `watb-uploads` 卷按内容比对后补齐 9 个，见 §A.2） |

## A.1 变更后的容器定义

### watb-app（变更后）

| 字段 | 值 |
|---|---|
| `ContainerId` | `645c928db81da211129f437177fe580b5a6041e9f468b762fb71e9a86d85dc4d` |
| `State` | `running` |
| `RestartPolicy` | `unless-stopped` |
| `Config.User` | `10001:10001` |
| `PortBindings` | `{"8081/tcp": [{"HostIp": "127.0.0.1", "HostPort": "8081"}]}` |
| `Networks` | `["watb-net"]` |
| `Healthcheck` | `{"Test": ["CMD-SHELL", "bash -c \"exec 3<>/dev/tcp/127.0.0.1/8081 && printf \\\"GET /api/health HTTP/1.0\\r\\nHost: 127.0.0.1\\r\\nConnection: close\\r\\n\\r\\n\\\" >&3 && grep -q \\\" 200 \\\" <&3\""], "StartPeriod": 90000000000, "Interval": 15000000000, "Timeout": 5000000000, "Retries": 5}` |
| `State.Health` | `{"Status": "healthy", "FailingStreak": 0}` |
| `Binds` | `["/mnt/d/project/wwwroot/wechat-article-bot/.env:/app/.env:ro,rprivate,rbind", "/mnt/d/project/wwwroot/wechat-article-bot/data/uploads:/app/data/uploads:rprivate,rbind"]` |

**Env（密钥已打码）**

```
container=podman
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai
SERVER_PORT=8081
STORAGE_PATH=/app/data/uploads
JAVA_HOME=/opt/java/openjdk
SPRING_APPLICATION_JSON={"spring":{"datasource":{"url":"jdbc:mysql://watb-docker-mysql:3306/wechat-article?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"}}}
PATH=/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
LANG=en_US.UTF-8
LANGUAGE=en_US:en
LC_ALL=en_US.UTF-8
JAVA_VERSION=jdk-17.0.20+8
HOME=/app
HOSTNAME=645c928db81d
```

**CreateCommand（密钥已打码）**

```
["C:\\Program Files\\RedHat\\Podman\\podman.exe", "run", "-d", "--name", "watb-app", "--network", "watb-net", "-p", "127.0.0.1:8081:8081", "--restart", "unless-stopped", "-v", "D:/project/wwwroot/wechat-article-bot/.env:/app/.env:ro", "-v", "D:/project/wwwroot/wechat-article-bot/data/uploads:/app/data/uploads", "-e", "SPRING_APPLICATION_JSON={\"spring\":{\"datasource\":{\"url\":\"jdbc:mysql://watb-docker-mysql:3306/wechat-article?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true\"}}}", "--health-cmd", "bash -c \"exec 3<>/dev/tcp/127.0.0.1/8081 && printf \\\"GET /api/health HTTP/1.0\\r\\nHost: 127.0.0.1\\r\\nConnection: close\\r\\n\\r\\n\\\" >&3 && grep -q \\\" 200 \\\" <&3\"", "--health-interval", "15s", "--health-timeout", "5s", "--health-retries", "5", "--health-start-period", "90s", "localhost/wechat-article-bot:local"]
```

### watb-docker-mysql（变更后）

| 字段 | 值 |
|---|---|
| `ContainerId` | `817dd2f4f9110ed01ba2c681a882c1f37ff6f8c5889941a0c1efb02c294e28ae` |
| `State` | `running` |
| `RestartPolicy` | `no` |
| `Config.User` | `` |
| `PortBindings` | `{"3306/tcp": [{"HostIp": "127.0.0.1", "HostPort": "3306"}]}` |
| `Networks` | `["watb-net"]` |
| `Healthcheck` | `{"Test": ["CMD-SHELL", "mysqladmin ping -h 127.0.0.1 -uroot -p\"$MYSQL_ROOT_PASSWORD\" >/dev/null 2>&1"], "StartPeriod": 60000000000, "Interval": 10000000000, "Timeout": 5000000000, "Retries": 10}` |
| `State.Health` | `{"Status": "healthy", "FailingStreak": 0}` |
| `Binds` | `["watb-mysql-data:/var/lib/mysql:rprivate,nosuid,nodev,rbind"]` |

**Env（密钥已打码）**

```
MYSQL_SHELL_VERSION=8.0.46-1.el9
MYSQL_ROOT_PASSWORD=<REDACTED:9 chars>
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
container=podman
GOSU_VERSION=1.19
MYSQL_MAJOR=8.0
MYSQL_VERSION=8.0.46-1.el9
HOME=/root
HOSTNAME=817dd2f4f911
```

**CreateCommand（密钥已打码）**

```
["C:\\Program Files\\RedHat\\Podman\\podman.exe", "run", "-d", "--name", "watb-docker-mysql", "--network", "watb-net", "-v", "watb-mysql-data:/var/lib/mysql", "-e", "MYSQL_ROOT_PASSWORD=<REDACTED>", "-p", "127.0.0.1:3306:3306", "--health-cmd", "mysqladmin ping -h 127.0.0.1 -uroot -p\"$MYSQL_ROOT_PASSWORD\" >/dev/null 2>&1", "--health-interval", "10s", "--health-timeout", "5s", "--health-retries", "10", "--health-start-period", "60s", "docker.io/library/mysql:8.0"]
```



## A.2 上传目录：单一事实来源

**结论：宿主目录 `data\uploads` 是唯一事实来源**（bind mount 进容器的 `/app/data/uploads`）。

变更前的缺陷：容器写进卷 `watb-uploads`，宿主 `data\uploads` 停在迁移前的旧快照，两边分叉。
实测分叉量（`diff -rq /vol /host`，卷 vs 宿主）：

- 卷 322 个文件，宿主 313 个，**宿主是严格的子集**；
- **共有文件内容 0 处不同**（`diff -rq` 只报 `Only in /vol:` 9 行，`Files … differ` 计数为 0）；
- 卷里多出的 9 个文件**是活的**——9 个都在 `ASSET` 表里有行，其中 3 篇 `ARTICLE.CONTENT_HTML`
  引用了它们。所以「先补齐再切 bind mount」是必须的，直接切会让 9 个 URL 404。

补齐后 `diff -rq /vol /host` 返回 0（两侧内容完全一致）。

**写权限（本地开发专用，生产镜像不受影响）**：宿主 `data\uploads` 在 podman machine 里经 drvfs 挂载，
`stat` 显示 `owner=0 group=0 mode=777`，所以容器里的 `uid 10001` 直接可写：

| 测试 | 容器内身份 | 结果 |
|---|---|---|
| 写测试（`busybox` + 宿主目录 bind） | `uid=10001(10001) gid=10001(10001)` | `WRITE_OK` / `APPEND_OK` / `UNLINK_OK` |
| 回退测试 | `--user 0:0` | `WRITE_OK`（**未使用**，uid 10001 已够） |
| 往返测试 | `uid 10001` 写 → 宿主 Windows 侧 | 文件可见、内容一致（md5 相同） |

> ⚠️ 这是 **WSL/drvfs 的 777 语义**带来的便利，只适用于本机开发部署。
> 换成真实 Linux 宿主（ext4）时，宿主目录的属主/权限必须让 `10001` 可写
> （例如 `chown -R 10001:10001 data/uploads`），否则应用上传会失败。
> 生产镜像是 `USER 10001:10001` + 镜像内 `/app/data/uploads`，与本机这个 bind mount 无关。

**以后怎么对齐（不需要再手工比对）**：宿主目录就是容器写入的位置，看宿主即看全部。

## A.3 Dockerfile 的 HEALTHCHECK 有个坑：OCI 格式下被静默忽略

`podman build` / `buildah bud` **默认输出 OCI 镜像格式**，而 `HEALTHCHECK` 不是 OCI 规范的一部分。
同一条 Dockerfile 分别构建，实测结果：

| 构建命令 | 产物 `ManifestType` | 产物 `Healthcheck` |
|---|---|---|
| `podman build -t X .`（默认） | `application/vnd.oci.image.manifest.v1+json` | `null` —— 并只打一句 warning：`level=warning msg="HEALTHCHECK is not supported for OCI image format and will be ignored. Must use \`docker\` format"` |
| `podman build --format docker -t X .` | `application/vnd.docker.distribution.manifest.v2+json` | `{"Test":["CMD","bash","-c","exec 3<>/dev/tcp/127.0.0.1/${SERVER_PORT:-8081} …"],"StartPeriod":90s,"Interval":15s,"Timeout":5s,"Retries":5}` |

现有 `localhost/wechat-article-bot:local` 是 **OCI 格式**（`ManifestType` 为 `application/vnd.oci.image.manifest.v1+json`），
即按 `docs/dev/docker-deployment.md:187` 的 `podman build -f Dockerfile -t wechat-article-bot:local .` 构建出来的镜像
**不带健康检查**。所以以后本地重建镜像要写成：

```bash
MSYS_NO_PATHCONV=1 podman build --format docker -f Dockerfile -t wechat-article-bot:local .
```

`Test` 里那条命令串已在运行中的 `watb-app` 内以**逐字相同**的 argv 执行验证过：正常时 `exit 0`，
端口不通 / 非 200 时 `exit 1`，都不会挂住（`SERVER_PORT` 为空时回落到 8081 也通过）。

> 本次**没有重新构建镜像**（按任务要求），所以镜像层里的 `HEALTHCHECK` 尚未生效；
> 两个在跑容器的 healthcheck 是用 `podman run --health-cmd …` 在**容器层**加上的，与镜像无关。

## A.4 回滚到变更前

```bash
PODMAN="/c/Program Files/RedHat/Podman/podman.exe"
MSYS_NO_PATHCONV=1

# 1) 停掉两个容器（只 stop 不 rm）
sh scripts/watb-stop.sh

# 2) app 换回卷 watb-uploads（去掉 bind mount 与 healthcheck），命令见 §6
#    （即把 -v "<HOST>/wechat-article-bot/data/uploads:/app/data/uploads" 换回 -v watb-uploads:/app/data/uploads）
# 3) mysql 去掉 --health-* 参数即可（卷/网络/端口/env 本来就是原样）
# 4) 起回来
sh scripts/watb-start.sh    # 卷版 app 没有 healthcheck，脚本会拒绝等待 —— 见脚本里的提示，改用 §6 的原始 run
```

`watb-uploads` 卷**一直保留未删**，所以「换回卷」这条路随时可走，且卷里仍是变更前那份 322 文件的快照。
