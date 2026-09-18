package ink.icoding.wechat.article.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 前端静态产物自检的单测（方案 B）。
 *
 * <p>为什么要专门钉住这个类：它的判定结果直接决定「用户看到空白页时日志里有没有线索」。
 * 第十四/十五轮的真实故障（应用提供的旧前端 ≠ 探针验证的新前端）之所以难查，
 * 正是因为<b>日志里一行提示都没有</b>。这些用例把三种结论各自的触发条件固定下来。
 */
class WebUiArtifactCheckTest {

    private static final long NOW = 1_800_000_000_000L;

    /** 产物缺失（干净 clone / mvn clean 之后直接 spring-boot:run）：必须报 MISSING 且给出可照抄的命令。 */
    @Test
    void missingArtifactIsReportedWithTheRebuildCommand() {
        WebUiArtifactCheck.Verdict verdict = WebUiArtifactCheck.evaluate(false, 0L, NOW);

        assertThat(verdict.status()).isEqualTo(WebUiArtifactCheck.Status.MISSING);
        assertThat(verdict.needsAttention()).isTrue();
        assertThat(verdict.message())
                .as("提示里必须带上能直接照抄的构建命令，否则等于没说")
                .contains("空白页")
                .contains("npm run build")
                .contains("target/classes/static");
    }

    /** 产物比源码旧（改了前端没重建就重启）：报 STALE。 */
    @Test
    void staleArtifactIsReported() {
        WebUiArtifactCheck.Verdict verdict =
                WebUiArtifactCheck.evaluate(true, NOW - 60_000L, NOW);

        assertThat(verdict.status()).isEqualTo(WebUiArtifactCheck.Status.STALE);
        assertThat(verdict.needsAttention()).isTrue();
        assertThat(verdict.message()).contains("比源码旧").contains("npm run build");
    }

    /** 产物比源码新（正常情况）：OK，且不打扰运维（needsAttention=false）。 */
    @Test
    void freshArtifactIsOkAndQuiet() {
        WebUiArtifactCheck.Verdict verdict =
                WebUiArtifactCheck.evaluate(true, NOW, NOW - 60_000L);

        assertThat(verdict.status()).isEqualTo(WebUiArtifactCheck.Status.OK);
        assertThat(verdict.needsAttention()).as("正常情况不该产生 WARN 噪音").isFalse();
    }

    /**
     * 没有源码可比（jar 部署 / 非开发检出）时<b>不做陈旧判定</b>，只确认产物在。
     *
     * <p>这条是防误报的关键：线上只跑 jar，没有 {@code webui} 目录。若把「取不到源码时间」
     * 当成陈旧，每次启动都会刷一条无意义的 WARN，真正的告警会被淹掉。
     */
    @Test
    void absentSourcesDisableTheStalenessComparison() {
        assertThat(WebUiArtifactCheck.evaluate(true, NOW, 0L).status())
                .as("无源码可比时应判 OK").isEqualTo(WebUiArtifactCheck.Status.OK);
    }

    /**
     * 产物时间戳取不到（jar 内条目无 mtime）时也不误报陈旧。
     *
     * <p>与上一条同理但方向相反：这里「未知」的是产物侧。
     */
    @Test
    void unknownArtifactTimestampDoesNotCauseAFalseAlarm() {
        assertThat(WebUiArtifactCheck.evaluate(true, 0L, NOW).status())
                .as("产物时间未知时不该断言它旧").isEqualTo(WebUiArtifactCheck.Status.OK);
    }

    /**
     * {@code node_modules} 与 {@code dist} 必须被剪掉。
     *
     * <p>若不剪：{@code node_modules} 里任何一个刚写入的文件都会让「最新源码时间」变成现在，
     * 于是<b>每次启动都报陈旧</b>——一个必然误报的告警比没有告警更糟。
     */
    @Test
    void nodeModulesAndDistAreExcludedFromTheSourceTimestamp(@TempDir Path webui) throws IOException {
        Path src = Files.createDirectories(webui.resolve("src"));
        Path sourceFile = src.resolve("App.vue");
        Files.writeString(sourceFile, "<template/>");
        setModified(sourceFile, NOW - 10_000L);

        // 依赖目录与另一次构建的产物：都比源码新，但都不是源码
        Path modules = Files.createDirectories(webui.resolve("node_modules/pkg"));
        Path depFile = modules.resolve("index.js");
        Files.writeString(depFile, "// dep");
        setModified(depFile, NOW);

        Path dist = Files.createDirectories(webui.resolve("dist"));
        Path distFile = dist.resolve("index.html");
        Files.writeString(distFile, "<html/>");
        setModified(distFile, NOW);

        long newest = WebUiArtifactCheck.newestSourceMillis(webui);

        assertThat(newest)
                .as("必须只反映 src 下的真实源码时间，而不是 node_modules/dist")
                .isEqualTo(NOW - 10_000L);
    }

    /** 目录不存在（jar 部署）：返回 0 而不是抛异常。 */
    @Test
    void missingWebuiDirectoryYieldsZero() {
        assertThat(WebUiArtifactCheck.newestSourceMillis(Path.of("no-such-webui-dir-xyz"))).isZero();
        assertThat(WebUiArtifactCheck.newestSourceMillis(null)).isZero();
    }

    private static void setModified(Path file, long millis) throws IOException {
        Files.setLastModifiedTime(file, FileTime.fromMillis(millis));
    }
}
