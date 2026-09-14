import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * 第十五轮 · 保存侧自检对 layout-* 全族的覆盖核验（只读、不写库、不产生数据）。
 *
 * 直接反射调用生产代码 ScheduledArticleTools.markflowSyntaxHints（package-private static），
 * 对注册表里全部 38 个 layout-* 名字 × 容器/标签两种写法逐条断言「至少产出一条提示」，
 * 并用 19 个已知受支持容器做对照组，确认不产生「不是渲染器支持的容器语法」这类误报。
 *
 * 用法（**在仓库根目录**执行，先 `mvn -o test-compile` 保证 target/classes 是新的）：
 *
 *   ./.mvn/mvn-local.sh -o -q dependency:build-classpath -Dmdep.outputFile=target/probe/r15_cp.txt
 *   java -Dfile.encoding=UTF-8 -cp "target/classes;$(cat target/probe/r15_cp.txt)" \
 *        tools/render-verify/gen/LayoutGuardCoverage.java
 *
 * 产物：stdout（第十五轮实测落地为 target/probe/r15/layout_guard_coverage.txt）。
 * 只读：不连库、不写任何业务数据。
 */
public class LayoutGuardCoverage {

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        String registry = Files.readString(Paths.get("tools/render-verify/spec/component_registry.json"));
        Set<String> names = new TreeSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"(layout-[a-z-]+)\"").matcher(registry);
        while (m.find()) names.add(m.group(1));

        Class<?> cls = Class.forName("ink.icoding.wechat.article.ai.ScheduledArticleTools");
        Method hints = cls.getDeclaredMethod("markflowSyntaxHints", String.class);
        hints.setAccessible(true);

        List<String> containerMiss = new ArrayList<>();
        List<String> tagMiss = new ArrayList<>();
        Map<String, String> sampleContainer = new LinkedHashMap<>();
        Map<String, String> sampleTag = new LinkedHashMap<>();
        int containerEmpty = 0;
        int tagEmpty = 0;
        // 反向误报：受支持容器不得被判成「不支持的容器」
        List<String> falsePositives = new ArrayList<>();

        for (String name : names) {
            String containerBody = ":::" + name + "\n正文段落\n:::\n";
            List<String> ch = (List<String>) hints.invoke(null, containerBody);
            if (ch.isEmpty()) { containerEmpty++; containerMiss.add(name); }
            else sampleContainer.putIfAbsent(ch.get(0), name + " => " + ch.get(0));

            String tagBody = "<" + name + " title=\"x\">正文</" + name + ">\n";
            List<String> th = (List<String>) hints.invoke(null, tagBody);
            if (th.isEmpty()) { tagEmpty++; tagMiss.add(name); }
            else sampleTag.putIfAbsent(th.get(0), name + " => " + th.get(0));
        }

        String[] supported = {"compare", "tip", "note", "info", "warning", "caution", "important",
                "breaking", "timeline", "table", "steps", "reading-path", "steps-horizontal",
                "steps-vertical", "case-flow", "slider", "callout", "align", "code-block"};
        for (String s : supported) {
            List<String> h = (List<String>) hints.invoke(null, ":::" + s + "\n正文段落\n:::\n");
            for (String one : h) {
                if (one.contains("不是渲染器支持的容器语法")) falsePositives.add(s + " => " + one);
            }
        }

        System.out.println("registryLayoutNames=" + names.size());
        System.out.println("containerForm_withoutHint=" + containerEmpty + " " + containerMiss);
        System.out.println("tagForm_withoutHint=" + tagEmpty + " " + tagMiss);
        System.out.println("supportedControlGroup_size=" + supported.length
                + " falsePositives=" + falsePositives.size() + " " + falsePositives);
        System.out.println("--- 容器式提示文案（去重，每种类别一条） ---");
        sampleContainer.values().forEach(v -> System.out.println("  " + v));
        System.out.println("--- 标签式提示文案（去重） ---");
        sampleTag.values().forEach(v -> System.out.println("  " + v));

        boolean ok = names.size() == 38 && containerEmpty == 0 && tagEmpty == 0 && falsePositives.isEmpty();
        System.out.println("VERDICT=" + (ok ? "FULL-COVERAGE" : "GAPS-FOUND"));
    }
}
