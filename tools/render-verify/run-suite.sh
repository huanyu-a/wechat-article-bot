#!/usr/bin/env bash
# 渲染验收 **九步链 + 收尾审计** 的常设入口（第三十轮 E1 补完各步退出码后入库）。
#
# 为什么要入库：这条链原来只存在于 gitignored 的 `target/probe/r29/_run_suite.sh`，
# 换台机器 / 干净 clone 之后**根本没有这一步**——而「九步全绿」是本项目对外最常引用的一句话。
#
# 用法：bash tools/render-verify/run-suite.sh
#       bash tools/render-verify/run-suite.sh --no-build     # 跳过 1/9 的探针 dist 重建（省 2 秒）
#
# 前置：应用在 127.0.0.1:8081 起着（6/9 与 7/9 要真应用）；探针 dist 由 1/9 自己重建。
# 产物：日志 target/probe/run_suite.log；各步自己的产物落 target/probe/ 下（gitignored）。
#
# 退出码（**第三十轮起**）：0 = 每一步都是 0；1 = 有步骤非零（末尾会逐个列出来）。
#   ⚠️ 各步的退出码从第三十轮起才真正含判据；第二十九轮及以前，「EXIT=0」只表示脚本没抛异常。
#   已知的长期红灯见 `docs/dev/known-issues-handoff.md` §3.31②（当前是 6/9：
#   #16/#35 引用的外链图 13/13 返回 404，外部图床失效，不是本项目缺陷）。
#
# 第三十七轮补记：收尾层多了一步 **`9i passthrough 判据自检`**（纯函数、不联网、不写产物）。
# 所以严格说现在是「九步链 + 收尾审计（9a–9i）」，「九步」指的是前 9 步浏览器/产物层，
# 收尾层的字母编号本来就多于 9 个（9a–9h 早已如此），不另改名以免打乱既有文档引用。
set -u

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$(dirname "$HERE")/.." || exit 9   # 仓库根
LOG=target/probe/run_suite.log
mkdir -p target/probe
: > "$LOG"

# ⚠️ 数组名必须用 ASCII：bash 的标识符只允许 [A-Za-z0-9_]，中文变量名会直接
# `syntax error near unexpected token '('`（第三十轮实测踩到，与 JS 里 ①② 不能当标识符同一类坑）。
declare -a STEP_NAMES=()
declare -a STEP_CODES=()
say() { echo "$*" | tee -a "$LOG"; }
run() {  # run <标签> <命令...>
  local label="$1"; shift
  say ""
  say "===== [$label] $* ====="
  local t0=$SECONDS
  "$@" >>"$LOG" 2>&1
  local code=$?
  say "----- [$label] EXIT=$code  用时 $((SECONDS - t0))s"
  STEP_NAMES+=("$label"); STEP_CODES+=("$code")
  return 0
}

say "开始：$(date '+%F %T')  应用 8081: $(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8081/)"

if [ "${1:-}" != "--no-build" ]; then
  run "1/9 探针 dist 重建" bash -c 'cd webui && npx vite build --config ../tools/render-verify/browser/vite.config.mjs'
fi
run "2/9 全量样例 79"      node tools/render-verify/browser/run-all-browser.mjs
run "3/9 组合条件 17"      node tools/render-verify/browser/run-combo-browser.mjs
run "4/9 等上游替代 10"    node tools/render-verify/browser/run-set-browser.mjs alt
run "5/9 注册表全族 76"    node tools/render-verify/browser/run-set-browser.mjs registry
run "6/9 真实稿件 14"      node tools/render-verify/browser/run-article.mjs
run "7/9 活体前端 43 44"   node tools/render-verify/browser/verify-live-app.mjs 43 44
run "8/9 r16 本体 11"      node tools/render-verify/browser/run-r16-browser.mjs

say ""
say "===== 汇总层 ====="
run "9a summarize-all"           node tools/render-verify/browser/summarize-all.mjs
run "9b summarize-combos"        node tools/render-verify/browser/summarize-combos.mjs
run "9c summarize-alt alt"       node tools/render-verify/browser/summarize-alt.mjs alt
run "9d summarize-alt reg"       node tools/render-verify/browser/summarize-alt.mjs registry
run "9e round10_component_paths" node tools/render-verify/round10_component_paths.mjs
run "9f round11_crosscheck"      python tools/render-verify/gen/round11_crosscheck.py
run "9g summarize-r16"           node tools/render-verify/browser/summarize-r16.mjs
# 9i 是**纯函数判据**的自检（不联网、不写产物）：证明「元素形态透传」判据是活的——
# 合法占位不误报成透传、透传不漏判。它不进判定，所以和上面几步并列而不是并入其中。
run "9i passthrough 判据自检"    python tools/render-verify/gen/passthrough.py --selftest

say ""
say "===== 收尾审计（这一步回答「今天的绿灯有多少是真绿」） ====="
run "9h round29_gate_audit"      node tools/render-verify/round29_gate_audit.mjs

say ""
say "结束：$(date '+%F %T')"

# 逐步骤退出码汇总 —— 这一张表才是「九步链」这句话的全部含义。
say ""
say "===== 各步退出码 ====="
FAILED=0
for i in "${!STEP_NAMES[@]}"; do
  mark="ok"
  if [ "${STEP_CODES[$i]}" != "0" ]; then mark="**非零**"; FAILED=$((FAILED + 1)); fi
  say "  ${STEP_NAMES[$i]} → EXIT=${STEP_CODES[$i]}  $mark"
done
say "共 ${#STEP_NAMES[@]} 步，非零 $FAILED 步"
say "真产物一个字节没动；每一步的判据口径见各自脚本头部。"

# 收尾：确认探针浏览器没剩。
# ⚠️ 数法必须和 `cdp.mjs` 里的 `probeMatcher` 一致：**同时限定可执行名是浏览器**。
# 只按「命令行里带 probe-chrome-」数会**自己数自己**——发这条查询的 powershell 自己，
# 命令行里就嵌着这个前缀，于是永远多数出 1（第二十九轮实测）。
PROBE_COUNT='$p='"'probe-chrome-'"'; (Get-CimInstance Win32_Process | Where-Object { $_.Name -in @('"'"'chrome.exe'"'"','"'"'msedge.exe'"'"','"'"'crashpad_handler.exe'"'"') -and $_.CommandLine -and $_.CommandLine.Contains($p) } | Measure-Object).Count'
say "跑完残留的探针浏览器: $(powershell -NoProfile -NonInteractive -Command "$PROBE_COUNT" | tr -d '\r')"
say "机器上全部 chrome.exe: $(powershell -NoProfile -NonInteractive -Command "(Get-CimInstance Win32_Process -Filter \"Name='chrome.exe'\").Count" | tr -d '\r')"

exit $(( FAILED > 0 ? 1 : 0 ))
