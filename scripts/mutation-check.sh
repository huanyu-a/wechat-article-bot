#!/usr/bin/env sh
# 变异验证辅助：安全地「改一行 -> 跑测试 -> 还原」，并保证每一步都**真的重新编译**。
#
# 为什么需要它（第三十八轮实测教训）：
#   做变异验证时，如果「还原」用的是保留时间戳的复制（cp -p / Copy-Item 默认语义），
#   还原后的源文件 mtime 仍**早于**变异时编译出的 .class，Maven 增量编译就认为
#   「源没变、无需重编」——此后每次运行跑的都是**变异过的 class**。
#
#   表现极具误导性：测试稳定报「周期刷写 0 次」，而同一方法用反射单独调却是好的，
#   scheduleWithFixedDelay 的独立冒烟也正常——三个证据同时指向「生产接线坏了」，
#   真凶却是构建缓存。**源码「看起来对」不算数，要问编译器实际吃进去的是哪一份。**
#
# 用法：
#   sh scripts/mutation-check.sh <文件> '<原文>' '<变异文>' '<测试类>[#方法]'
#
# 例：
#   sh scripts/mutation-check.sh \
#     src/main/java/ink/icoding/wechat/article/schedule/TaskExecutionService.java \
#     'ScheduledFuture<?> progressFlush = scheduleProgressFlush(run, liveWorkspace);' \
#     'ScheduledFuture<?> progressFlush = null;' \
#     'TaskExecutionProgressWiringTest'
#
# 退出码：0 = 变异让测试变红、还原后变绿（变异被杀死，判据有效）
#         1 = 变异后测试仍绿（变异存活 -> 该测试钉不住这条行为）
#         2 = 用法/环境错误，或还原后没回到绿（结果不可信）
#
# 关键实现点：每次改动后都 `rm` 掉对应 .class，**强制**重新编译，不依赖 mtime。
set -eu

if [ $# -lt 4 ]; then
    echo "用法: sh scripts/mutation-check.sh <文件> '<原文>' '<变异文>' '<测试类>[#方法]'" >&2
    exit 2
fi

FILE=$1
ORIG=$2
MUTATED=$3
TEST=$4

cd "$(dirname "$0")/.."

if [ ! -f "$FILE" ]; then
    echo "错误：找不到文件 $FILE" >&2
    exit 2
fi

# 源文件在 git 里必须干净：这样「还原」可以交给 git，不依赖任何时间戳技巧
if ! git diff --quiet -- "$FILE"; then
    echo "错误：$FILE 有未提交改动。先提交或 stash，否则无法可靠还原。" >&2
    exit 2
fi

if ! grep -qF -- "$ORIG" "$FILE"; then
    echo "错误：在 $FILE 里找不到原文，无法施加变异：" >&2
    echo "  $ORIG" >&2
    exit 2
fi

# 由源文件路径推出要删的 class（同一顶层类的内部类/匿名类也一并删）
SRC_ROOT=src/main/java
REL=${FILE#"$SRC_ROOT"/}
CLASS_BASE="target/classes/${REL%.java}"

drop_classes() {
    rm -f "$CLASS_BASE".class "$CLASS_BASE"'$'*.class 2>/dev/null || true
}

# Maven 启动：直接调 classworlds launcher，不依赖 target/scratch 下的临时脚本
# （那是本机产物、不在版本库里，别人 clone 下来跑必然找不到）。
find_java() {
    for candidate in \
        "/c/Program Files/Java/jdk-17/bin/java.exe" \
        "/c/Program Files/Java/jdk-17/bin/java" \
        "$(command -v java 2>/dev/null || true)"
    do
        [ -n "$candidate" ] && [ -x "$candidate" ] && { echo "$candidate"; return 0; }
    done
    return 1
}

MVN_HOME=$(ls -d "$HOME"/.m2/wrapper/dists/apache-maven-*/*/ 2>/dev/null | while read -r d; do
    [ -f "$d/boot/plexus-classworlds-2.11.0.jar" ] && { echo "$d"; break; }
done)
JAVA_BIN=$(find_java || true)

if [ -z "$MVN_HOME" ] || [ -z "$JAVA_BIN" ]; then
    echo "错误：找不到 Maven 发行版（~/.m2/wrapper/dists）或 java，无法跑测试。" >&2
    echo "  MVN_HOME=${MVN_HOME:-<空>}  JAVA=${JAVA_BIN:-<空>}" >&2
    exit 2
fi

run_test() {
    drop_classes
    # 每个参数单独引号：datasource URL 里的 '?' 和 '&' 若走变量展开会被当成
    # glob/控制字符处理，必须逐字作为**独立 argv** 传入。
    # 测试参数与本地门禁保持一致：-o 离线、允许自附加、指向容器里的测试库。
    "$JAVA_BIN" \
        -cp "$MVN_HOME/boot/plexus-classworlds-2.11.0.jar" \
        "-Dclassworlds.conf=$MVN_HOME/bin/m2.conf" \
        "-Dmaven.home=$MVN_HOME" \
        "-Dmaven.multiModuleProjectDirectory=$PWD" \
        org.codehaus.plexus.classworlds.launcher.Launcher \
        -o test \
        "-Dtest=$TEST" \
        -DfailIfNoSpecifiedTests=false \
        -DargLine=-Djdk.attach.allowAttachSelf=true \
        "-Dspring.datasource.url=jdbc:mysql://localhost:3306/wechat-article-test?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
}

echo "== 1/3 施加变异 =="
# 用 python 做字面替换，避免 sed 对特殊字符（? < > $ * /）的转义地狱
python - "$FILE" "$ORIG" "$MUTATED" <<'PY'
import sys
path, orig, mutated = sys.argv[1], sys.argv[2], sys.argv[3]
text = open(path, encoding='utf-8').read()
if text.count(orig) != 1:
    sys.exit(f"错误：原文在 {path} 里出现 {text.count(orig)} 次，需要恰好 1 次")
open(path, 'w', encoding='utf-8', newline='').write(text.replace(orig, mutated))
print("  已替换")
PY

if run_test; then
    echo "  变异后测试**仍然通过** -> 变异存活"
    MUTANT_KILLED=no
else
    echo "  变异后测试失败 -> 变异被杀死（这是期望结果）"
    MUTANT_KILLED=yes
fi

echo "== 2/3 用 git 还原 =="
git checkout -- "$FILE"
if ! git diff --quiet -- "$FILE"; then
    echo "错误：git 还原后 $FILE 仍有差异，结果不可信" >&2
    exit 2
fi
echo "  已还原，git diff 为空"

echo "== 3/3 还原后复跑（必须回到绿） =="
if run_test; then
    echo "  还原后测试通过 -> 结果可信"
else
    echo "错误：还原后测试仍失败，说明失败与变异无关，本次变异验证不可信" >&2
    exit 2
fi

echo ""
if [ "$MUTANT_KILLED" = yes ]; then
    echo "结论：变异被杀死，该测试确实钉住了这条行为。"
    exit 0
else
    echo "结论：**变异存活** —— 删掉这条行为测试依然是绿的，说明它没有被任何测试钉住。"
    exit 1
fi
