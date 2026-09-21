#!/usr/bin/env sh
# title: 容器停止（只 stop，不 rm）
# desc: 停掉两个容器：先 app 后 db（顺序与启动相反，让应用先把在途请求收干净）。
#       **只 stop，绝不 rm、绝不删卷** —— 容器定义是还原料，卷里是数据库与上传文件。
#       重启请用 `scripts/watb-start.sh`（它会按健康检查串行起 db → app）。
#       用法: watb-stop.sh [--timeout <秒>] [--help]
#
# 为什么不做 rm：
#   本机没有 compose provider，容器不是从 compose 文件声明式生成的——`podman rm` 掉就**没有东西能把它变回来**，
#   只能照 `docs/dev/podman-containers-inspect.md` 里的原始 run 命令手抄一遍。stop 是安全、可逆的，rm 不是。
#
# 停之前请确认没有在跑的采集任务（被打断会留下半截 TASK_RUN）：
#   $PODMAN exec watb-docker-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -B \
#       -e "select count(*) from \`wechat-article\`.TASK_RUN where STATUS not in (\"SUCCESS\",\"SUCCESS_WITH_WARNINGS\",\"FAILED\")"'
#   返回 0 才能停。本脚本会做这个检查并拒绝在非 0 时继续（--force 可跳过）。

set -eu

db_container=${WATB_DB_CONTAINER:-watb-docker-mysql}
app_container=${WATB_APP_CONTAINER:-watb-app}
stop_timeout_s=${WATB_STOP_TIMEOUT_S:-30}
force=0

usage() {
  cat <<'EOF'
用法: scripts/watb-stop.sh [--timeout <秒>] [--force]

  --timeout <秒>   传给 podman stop -t 的优雅退出等待，默认 30（可用 WATB_STOP_TIMEOUT_S 覆盖）
  --force          跳过「在途采集任务」检查直接停（**可能打断正在跑的采集任务**）
  -h, --help       显示本帮助

环境变量覆盖:
  WATB_DB_CONTAINER=watb-docker-mysql   db 容器名
  WATB_APP_CONTAINER=watb-app           app 容器名
  PODMAN=...                            podman 可执行文件路径

退出码:
  0  已停（或本来就没在跑）
  1  检测到在途采集任务，拒绝停止（未加 --force）
  69 找不到 podman
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --timeout) stop_timeout_s=$2; shift 2 ;;
    --timeout=*) stop_timeout_s=${1#*=}; shift ;;
    --force) force=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1（用 --help 看用法）" >&2; exit 64 ;;
  esac
done

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

MSYS_NO_PATHCONV=1
export MSYS_NO_PATHCONV

say() { echo "[watb-stop] $*"; }
state_of() { "$podman" inspect "$1" --format '{{.State.Status}}' 2>/dev/null || echo missing; }

# ---- 在途采集任务检查：db 在跑才查得到 ----
if [ "$force" -eq 0 ] && [ "$(state_of "$db_container")" = running ]; then
  inflight=$("$podman" exec "$db_container" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -B -e "select count(*) from \`wechat-article\`.TASK_RUN where STATUS not in (\"SUCCESS\",\"SUCCESS_WITH_WARNINGS\",\"FAILED\")"' 2>/dev/null || echo unknown)
  case "$inflight" in
    0) say "在途采集任务检查：0 条，可以停" ;;
    unknown) say "在途采集任务检查：查询失败（库刚起来？）—— 加 --force 可跳过检查" >&2; exit 1 ;;
    *) echo "在途采集任务检查：$inflight 条未结束 —— 停止会打断它们。等它跑完，或加 --force 明确要求强停。" >&2; exit 1 ;;
  esac
fi

# ---- 先 app 后 db ----
for c in "$app_container" "$db_container"; do
  case "$(state_of "$c")" in
    running) say "停止 $c（优雅等待 ${stop_timeout_s}s）"; "$podman" stop -t "$stop_timeout_s" "$c" >/dev/null ;;
    missing) say "$c 不存在，跳过" ;;
    *)       say "$c 本来就没在跑（$(state_of "$c")），跳过" ;;
  esac
done

say "=== 最终状态（容器与卷都还在） ==="
"$podman" ps -a --filter "name=^${db_container}$" --filter "name=^${app_container}$" \
  --format '  {{.Names}}  {{.Status}}'
say "已停止。重启：scripts/watb-start.sh"