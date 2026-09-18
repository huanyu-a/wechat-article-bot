package ink.icoding.wechat.article.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 前端静态产物启动自检（{@code render-verification.md} §3.10④ 方案 B）。
 *
 * <p><b>为什么需要它</b>：SPA 由 Maven 的 {@code frontend-maven-plugin} 在 {@code prepare-package}
 * 阶段构建进 {@code target/classes/static/}，而 {@code spring-boot:run} 只走到 {@code test-compile}，
 * <b>从不执行</b>那个阶段。于是应用对外提供的可能是几轮以前的前端，而 {@code webui/dist} 却是新的——
 * 第十四轮就是这么翻的车：探针量的是 {@code webui/dist}，全绿；用户打开的是应用实际提供的旧包，
 * {@code /articles/43} 的公式与 {@code /articles/44} 的轮播都不显示。
 *
 * <p>更严重的一种形态是<b>先清理再启动</b>：干净 clone（或 {@code mvn clean}）之后
 * {@code target/classes/static} 根本不存在，此时起来的是一个<b>没有前端</b>的应用——
 * 浏览器打开就是空白页，而日志里没有任何一行提示（第十五轮在干净 clone 里实测确认）。
 *
 * <p><b>本类只告警、不改构建</b>：治根方案是把 {@code build-webui} 的 phase 前移（方案 A），
 * 但那会改动 Maven 生命周期；且本机实测该插件<b>不在本地仓库</b>
 * （{@code com.github.eirslett:frontend-maven-plugin} 缺失），一旦绑到 {@code compile}，
 * 本仓库的闸门口径 {@code mvn -o test} 会因插件无法离线解析而<b>整体失败</b>。
 * 因此这里取方案 B：把「用户看到空白页」变成「启动日志里一句可操作的 WARN」。
 *
 * <p>判定是<b>纯函数</b>（{@link #evaluate}），与文件系统解耦，便于单测；任何异常都只告警，
 * 绝不影响启动（与 {@link ink.icoding.wechat.article.article.ArticleLongTextColumnRunner} 同构）。
 */
@Component
@Order(35)
public class WebUiArtifactCheck implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(WebUiArtifactCheck.class);

    /** classpath 里的入口文件：它的存在与否等价于「应用有没有前端」。 */
    static final String INDEX_LOCATION = "static/index.html";

    /** 前端工程目录（相对工作目录）。只有开发检出里才有；jar 部署下不存在，此时跳过陈旧判定。 */
    static final Path WEBUI_DIR = Paths.get("webui");

    /** 更新产物的准确命令（与 {@code render-verification.md} 坑 13 同源）。 */
    static final String REBUILD_COMMAND =
            "cd webui && npm run build -- --outDir ../target/classes/static --emptyOutDir";

    /** 自检结论。 */
    enum Status {
        /** 产物存在且不比源码旧。 */
        OK,
        /** 产物不存在：应用起来没有前端（空白页）。 */
        MISSING,
        /** 产物存在但比源码旧：用户看到的是旧界面。 */
        STALE
    }

    /** 结论 + 给运维看的一句话。 */
    record Verdict(Status status, String message) {
        boolean needsAttention() {
            return status != Status.OK;
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Verdict verdict = inspect();
            if (verdict.needsAttention()) {
                log.warn("{}", verdict.message());
            } else {
                log.debug("{}", verdict.message());
            }
        } catch (Exception exception) {
            log.warn("前端静态产物自检失败（跳过，不影响启动）", exception);
        }
    }

    /** 读 classpath 产物时间戳 + 源码时间戳，得出结论。 */
    static Verdict inspect() {
        ClassPathResource index = new ClassPathResource(INDEX_LOCATION);
        boolean present = index.exists();
        long builtAt = 0L;
        if (present) {
            try {
                builtAt = index.lastModified();
            } catch (Exception ignored) {
                // jar 内的时间戳可能取不到；取不到就不做陈旧判定（evaluate 据此返回 OK）
            }
        }
        return evaluate(present, builtAt, newestSourceMillis(WEBUI_DIR));
    }

    /**
     * 判定（纯函数，不触碰文件系统）。
     *
     * @param present            classpath 里是否存在 {@code static/index.html}
     * @param builtAtMillis      产物的构建时间；{@code <=0} 表示未知（此时不做陈旧判定）
     * @param newestSourceMillis {@code webui} 下最新的源文件时间；{@code <=0} 表示没有源码可比（非开发检出）
     */
    static Verdict evaluate(boolean present, long builtAtMillis, long newestSourceMillis) {
        if (!present) {
            return new Verdict(Status.MISSING, "前端静态产物缺失（classpath:" + INDEX_LOCATION
                    + " 不存在）：应用对外会返回空白页。请先构建前端再启动：" + REBUILD_COMMAND);
        }
        if (newestSourceMillis > 0 && builtAtMillis > 0 && newestSourceMillis > builtAtMillis) {
            return new Verdict(Status.STALE, "前端静态产物比源码旧（产物 " + at(builtAtMillis)
                    + " < 源码 " + at(newestSourceMillis) + "）：用户看到的可能仍是旧界面。"
                    + "请重新构建：" + REBUILD_COMMAND);
        }
        return new Verdict(Status.OK, "前端静态产物就绪（classpath:" + INDEX_LOCATION + "）");
    }

    /**
     * {@code webui} 下最新的源文件时间戳。
     *
     * <p>跳过 {@code node_modules} 与 {@code dist}：前者是依赖（不是源码），后者是另一次构建的产物，
     * 两者都会让「最新时间」恒为现在，判定就永远报陈旧。用 {@code walkFileTree} 而不是
     * {@code Files.walk} 是为了<b>整棵剪掉</b>这两个目录——{@code node_modules} 动辄数万文件，
     * 遍历一遍纯属浪费启动时间。
     *
     * @return 最新时间；目录不存在或没有可比文件时返回 {@code 0}
     */
    static long newestSourceMillis(Path webuiDir) {
        if (webuiDir == null || !Files.isDirectory(webuiDir)) return 0L;
        final long[] newest = {0L};
        try {
            Files.walkFileTree(webuiDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (dir.equals(webuiDir)) return FileVisitResult.CONTINUE;
                    String name = String.valueOf(dir.getFileName());
                    if ("node_modules".equals(name) || "dist".equals(name)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    long modified = attrs.lastModifiedTime().toMillis();
                    if (modified > newest[0]) newest[0] = modified;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exception) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            return 0L;
        }
        return newest[0];
    }

    /** 本地时区的可读时间（UTC 会让运维对不上日志）。 */
    private static String at(long millis) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).toString();
    }
}
