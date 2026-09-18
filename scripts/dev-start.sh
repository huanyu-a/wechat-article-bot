#!/usr/bin/env sh
# title: 开发启动（前端产物 + 后端）
# desc: 先构建前端到 target/classes/static，再 spring-boot:run；避免「起来的是旧前端或没有前端」。
#       用法: dev-start.sh [--build-only] [-- <spring-boot:run 的额外参数>]
#
# 为什么需要这个脚本（`docs/dev/render-verification.md` §3.11 ④ 方案 D）：
#   `pom.xml` 里 `build-webui` 绑在 `prepare-package`，而 `spring-boot:run` 只走到 `test-compile`，
#   **从不执行**那个阶段。所以「改完前端直接 spring-boot:run」看到的是几轮以前的包，
#   而 `webui/dist` 却是新的——第十四轮就是这么翻的车（探针量 dist 全绿、用户打开的是旧包）。
#   `mvn clean` 之后更糟：`target/classes/static` 根本不存在，起来的是一个**没有前端**的应用。
#
# 与方案 A/B 的关系：A（把 phase 前移到 compile）已因 `frontend-maven-plugin` 不在本地仓库而
# **证据性否决**；B（`WebUiArtifactCheck` 启动告警）已落地，但它只告警、不修。
# 本脚本是「不动 Maven 生命周期」的兜底入口：想省事就走它，直接 `spring-boot:run` 时仍有 B 兜底。
#
# 注意：本脚本**不替代** `mvn` 的其他用法（打包、跑测试）。它只解决「启动前把前端构建对齐」。

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
root_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
cd "$root_dir"

# 前端产物落点与 `WebUiArtifactCheck.REBUILD_COMMAND`、`pom.xml` 的 build-webui 三处必须一致。
out_dir=target/classes/static

usage() {
  cat <<'EOF'
用法: scripts/dev-start.sh [--build-only] [-- <spring-boot:run 的额外参数>]

  --build-only   只构建前端到 target/classes/static，不启动应用
  --             其后的参数原样传给 spring-boot:run

示例:
  scripts/dev-start.sh                              # 构建前端并启动
  scripts/dev-start.sh --build-only                 # 只对齐前端产物（CI / 预检用）
  scripts/dev-start.sh -- --server.port=9090        # 换端口启动
EOF
}

build_only=0
while [ "$#" -gt 0 ]; do
  case "$1" in
    --build-only) build_only=1; shift ;;
    -h|--help) usage; exit 0 ;;
    --) shift; break ;;
    *) break ;;
  esac
done

if [ ! -d webui ]; then
  echo "找不到 webui/ ——本脚本只能在开发检出里跑（jar 部署不需要构建前端）" >&2
  exit 69
fi

# 前端构建需要 Node/npm。缺了要**明确报错**，而不是让后面报一句难懂的 npm 错误。
if ! command -v node >/dev/null 2>&1; then
  echo "PATH 里没有 node ——前端构建需要 Node.js（jar 部署不需要本脚本）" >&2
  exit 69
fi
if ! command -v npm >/dev/null 2>&1; then
  echo "PATH 里没有 npm ——前端构建需要 npm（jar 部署不需要本脚本）" >&2
  exit 69
fi

# 依赖没装就先装（等价于 Maven 的 npm-ci 执行；幂等，已装则跳过）。
if [ ! -d webui/node_modules ]; then
  echo "==> webui/node_modules 不存在，先 npm ci"
  (cd webui && npm ci)
fi

echo "==> 构建前端 → $out_dir"
# 与 pom.xml 的 build-webui 参数逐字一致（--emptyOutDir 保证删掉上一轮的残留 chunk）。
(cd webui && npm run build -- --outDir "../$out_dir" --emptyOutDir)

# 构建「成功」但产物没落地，是最容易骗过人的一种失败：这里当场断言，不让它留到启动后才发现。
if [ ! -f "$out_dir/index.html" ]; then
  echo "前端构建结束但 $out_dir/index.html 不存在——产物没落到 classpath 位置" >&2
  exit 70
fi
echo "==> 前端产物就绪：$out_dir/index.html"

if [ "$build_only" -eq 1 ]; then
  echo "==> --build-only：不启动应用"
  exit 0
fi

# Maven 入口的解析顺序（实测得出，别按直觉改）：
#   1. `.mvn/mvn-local.sh` —— 仓库自带的离线启动器，复用已解压的发行包，
#      不依赖 JAVA_HOME（自带 `JAVA_BIN` 兜底），在受限网络/无 JAVA_HOME 的环境里实测可用。
#   2. `./mvnw` —— 首次使用会去 repo.maven.apache.org 下载发行包，离线时**构建还没开始就失败**；
#      且实测在未设 JAVA_HOME 时直接报「JAVA_HOME ... is not defined correctly」。
#   3. PATH 里的 `mvn`。
# 本脚本优先选 1，正是为了在「没有 JAVA_HOME、又没网」的开发机上也能一把起来。
if [ -x ./.mvn/mvn-local.sh ]; then
  mvn_cmd=./.mvn/mvn-local.sh
elif [ -f ./mvnw ]; then
  mvn_cmd=./mvnw
else
  mvn_cmd=mvn
fi

echo "==> 启动应用：$mvn_cmd spring-boot:run $*"
exec "$mvn_cmd" spring-boot:run "$@"
