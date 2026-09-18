#!/usr/bin/env sh
# 陈旧 class 检测：确认 target/classes 里编译出来的，确实是 src/main/java 里现在这份源码。
#
# 为什么需要它（第三十八轮实测教训）：
#   做变异验证时，如果「还原」用的是保留时间戳的复制（cp -p / Copy-Item 默认语义），
#   还原后的源文件 mtime 仍**早于**变异时编译出的 .class，Maven 增量编译就会认为
#   「源没变、无需重编」——此后每次运行跑的都是**变异过的 class**。
#
#   表现极具误导性：测试稳定报「周期刷写 0 次」，而同一个方法用反射单独调却是好的，
#   scheduleWithFixedDelay 的独立冒烟也正常——三个证据同时指向「生产接线坏了」，
#   真凶却是构建缓存。**源码「看起来对」不算数，要问编译器实际吃进去的是哪一份。**
#
# 用法：sh scripts/check-stale-classes.sh
#   退出码 0 = 没有比源码更新的 class（正常）
#   退出码 1 = 存在陈旧 class，需要清掉后重编
#
# 注意：本脚本只做「源码比 class 新」这一种最易踩的判定（mtime 比较）。
# 要判定「变异是否真的生效/还原」，最终仍应以反汇编为准：
#   javap -c -p -classpath target/classes <全限定类名>
set -eu

cd "$(dirname "$0")/.."
SRC=src/main/java
OUT=target/classes

if [ ! -d "$OUT" ]; then
    echo "OK: $OUT 不存在（还没编译过），无需检查"
    exit 0
fi

stale=0
checked=0
# 逐个源文件比对：源比同名 class 新 -> 该 class 是陈旧的
find "$SRC" -name '*.java' -print | while IFS= read -r java; do
    rel=${java#"$SRC"/}
    cls="$OUT/${rel%.java}.class"
    [ -f "$cls" ] || continue
    if [ "$java" -nt "$cls" ]; then
        echo "STALE: $rel  (源码比 $cls 新)"
        echo "STALE" >> /tmp/.stale-classes-flag
    fi
done

# 子 shell 里的计数传不出来，用标记文件汇总
if [ -f /tmp/.stale-classes-flag ]; then
    rm -f /tmp/.stale-classes-flag
    echo ""
    echo "结论：存在陈旧 class —— 增量编译可能跳过了它们，当前跑的不是最新源码。"
    echo "处理：删除对应 .class（或整个 target/classes）后重新编译。"
    exit 1
fi

echo "OK: 没有比源码更新的 class"
exit 0
