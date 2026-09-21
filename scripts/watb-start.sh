#!/usr/bin/env sh
# title: 容器启动（db → app，按健康检查串行）
# desc: 本机**没有任何 compose provider**（docker compose / podman compose 都不可用），容器是手写
#       `podman run` 起的，所以没有 depends_on、也没有编排器帮我们排顺序。本脚本就是宿主重启后的
#       **标准恢复路径**：
#         起 db → 轮询直到 db healthy → 起（或重启）app → 轮询直到 app healthy → 打印最终状态。
#       用法: watb-start.sh [--timeout <秒>] [--help]
#
# 为什么需要「等 db healthy」这一步（`docs/dev/podman-containers-inspect.md`）：
#   两个容器原先都没有 healthcheck，也没有 depends_on。宿主重启后 `watb-app` 带着
#   `--restart unless-stopped` 会自己起来，而 `watb-docker-mysql` 的 restart 策略是 `no`、不会起，
#   于是 app 连不上库、起不来，症状就是要手工 `podman restart watb-app`。本脚本把这件事变成一条命令。
#
# 幂等性：全绿时再跑一遍**不会**无谓重启——
#   app 已经 healthy 就原样留着；app 在跑但不 healthy（典型的「先于 db 起来」导致的坏状态）才 restart；
#   没跑就 start。db 同理。
#
# 只 start / restart，**从不 rm**（删容器是破坏性操作，见 `docs/dev/podman-containers-inspect.md` 的还原料）。
# 停容器请用 `scripts/watb-stop.sh`。

set -eu

# ---- 常量（要改容器名/超时，用同名环境变量覆盖，别改这里） ----
db_container=${WATB_DB_CONTAINER:-watb-docker-mysql}
app_container=${WATB_APP_CONTAINER:-watb-app}
network=${WATB_NETWORK:-watb-net}
timeout_s=${WATB_TIMEOUT_S:-240}
poll_s=${WATB_POLL_S:-3}

usage() {
  cat <<'EOF'
用法: scripts/watb-start.sh [--timeout <秒>]

  --timeout <秒>   单个容器等待 healthy 的上限，默认 240（可用 WATB_TIMEOUT_S 覆盖）
  -h, --help       显示本帮助

环境变量覆盖:
  WATB_DB_CONTAINER=watb-docker-mysql   db 容器名
  WATB_APP_CONTAINER=watb-app           app 容器名
  WATB_NETWORK=watb-net                 网络名（仅用于提示，不创建）
  WATB_POLL_S=3                         轮询间隔秒数
  PODMAN=...                            podman 可执行文件路径

退出码:
  0  全部 healthy
  1  某个容器在超时内没到 healthy（或该容器没有 healthcheck）
  69 找不到 podman，或缺容器（提示用 docs/dev/podman-containers-inspect.md 的还原料重建）
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --timeout) timeout_s=$2; shift 2 ;;
    --timeout=*) timeout_s=${1#*=}; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1（用 --help 看用法）" >&2; exit 64 ;;
  esac
done

# ---- 定位 podman：环境变量 > PATH > 本机安装位置 ----
if [ -n "${PODMAN:-}" ]; then
  podman=$PODMAN
elif command -v podman >/dev/null 2>&1; then
  podman=podman
elif [ -x "/c/Program Files/RedHat/Podman/podman.exe" ]; then
  podman="/c/Program Files/RedHat/Podman/podman.exe"
elif [ -x "/mnt/c/Program Files/RedHat/Podman/podman.exe" ]; then
  podman="/mnt/c/Program Files/RedHat/Podman/podman.exe"
else
  echo "找不到 podman。请设置 PODMAN=<podman 可执行文件绝对路径> 后重试。" >&2
  exit 69
fi

# Git Bash 会把 /xxx 形态的参数当路径改写，podman 的 -v / 健康检查串都可能被误伤，统一关掉。
MSYS_NO_PATHCONV=1
export MSYS_NO_PATHCONV

say() { echo "[watb-start] $*"; }

exists() { "$podman" container exists "$1" >/dev/null 2>&1; }

state_of()  { "$podman" inspect "$1" --format '{{.State.Status}}' 2>/dev/null || echo missing; }
health_of() { "$podman" inspect "$1" --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' 2>/dev/null || echo missing; }
has_healthcheck() {
  test "$("$podman" inspect "$1" --format '{{if .Config.Healthcheck}}yes{{else}}no{{end}}' 2>/dev/null)" = yes
}

# 轮询到 healthy（或超时）。$1=容器名 $2=人类可读的用途
wait_healthy() {
  c=$1; who=$2
  if ! has_healthcheck "$c"; then
    echo "$who（$c）没有 healthcheck —— 无法判断「真的可用」。重建方式见 docs/dev/podman-containers-inspect.md" >&2
    return 1
  fi
  start=$(date +%s)
  while :; do
    h=$(health_of "$c"); s=$(state_of "$c"); now=$(date +%s)
    if [ "$h" = healthy ]; then
      say "$who（$c）healthy，用时 $((now - start))s"
      return 0
    fi
    if [ "$s" != running ]; then
      echo "$who（$c）状态是 $s，不是 running —— 起来就退出了，先看 \`$podman logs $c\`" >&2
      return 1
    fi
    if [ $((now - start)) -ge "$timeout_s" ]; then
      echo "$who（$c）等待 ${timeout_s}s 仍是 $h（未达 healthy）—— 先看 \`$podman logs $c\`" >&2
      return 1
    fi
    say "等 $who（$c）：status=$s health=$h，已等 $((now - start))s"
    sleep "$poll_s"
  done
}

# ---- 前置检查：容器必须已经存在（本脚本只 start，不 create） ----
for c in "$db_container" "$app_container"; do
  if ! exists "$c"; then
    echo "容器 $c 不存在。本脚本只 start/restart，不负责创建。" >&2
    echo "重建用 docs/dev/podman-containers-inspect.md 里记录的原始 run 命令（含卷/网络/env/端口）。" >&2
    exit 69
  fi
done
"$podman" network exists "$network" >/dev/null 2>&1 \
  || say "注意：网络 $network 不存在，容器可能起不来（本脚本不会创建它）"

# ---- 1) db ----
say "=== 1/3 数据库容器 $db_container ==="
case "$(state_of "$db_container")" in
  running) say "$db_container 已经在跑" ;;
  *)       say "启动 $db_container"; "$podman" start "$db_container" >/dev/null ;;
esac
wait_healthy "$db_container" "数据库" || exit 1

# ---- 2) app ----
say "=== 2/3 应用容器 $app_container ==="
case "$(state_of "$app_container")" in
  running)
    if [ "$(health_of "$app_container")" = healthy ]; then
      say "$app_container 已经在跑且 healthy，保持不动"
    else
      say "$app_container 在跑但不 healthy（典型原因：它先于 db 起来了，连不上库）—— restart 一次"
      "$podman" restart "$app_container" >/dev/null
    fi
    ;;
  *) say "启动 $app_container"; "$podman" start "$app_container" >/dev/null ;;
esac
wait_healthy "$app_container" "应用" || exit 1

# ---- 3) 最终状态 ----
say "=== 3/3 最终状态 ==="
"$podman" ps -a --filter "name=^${db_container}$" --filter "name=^${app_container}$" \
  --format '  {{.Names}}  {{.Status}}  {{.Ports}}'
say "全部 healthy。"