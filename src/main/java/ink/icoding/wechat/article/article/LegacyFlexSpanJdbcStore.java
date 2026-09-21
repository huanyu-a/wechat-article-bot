package ink.icoding.wechat.article.article;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
/**
 * The {@link LegacyFlexSpanMergeRunner.Store} implementation used by the runner's standalone
 * {@code main} (plain JDBC, no Spring, no Web/Quartz context).
 *
 * <h2>Why not just boot the application</h2>
 * Booting it to change stored content would first run the schema sync, the seeders and Quartz.
 * Those are legitimate application start-up effects, but they are not part of a content migration,
 * and a migration that must be auditable ("what did this run write?") is easier to reason about
 * when nothing else writes. So the standalone path keeps one read, one write and nothing else.
 *
 * <h2>Configuration</h2>
 * The same variables the application resolves from {@code .env} (see
 * {@code spring.config.import}): {@code ENV.MYSQL_URL}, {@code ENV.MYSQL_USERNAME},
 * {@code ENV.MYSQL_PASSWORD}. The file location defaults to {@code .env} in the working directory
 * and can be overridden with {@code -Dlegacy-flex-merge.env=<path>}; real process environment
 * variables win when set.
 *
 * <h2>Table name</h2>
 * It is read from {@code information_schema} instead of being spelled out, because the entity
 * annotation says {@code article} while this project's development database actually stores
 * {@code ARTICLE}, and a table name is the one identifier that is case-sensitive on a Linux MySQL
 * server - see {@link ArticleLongTextColumnMapper} for the same reasoning.
 */
final class LegacyFlexSpanJdbcStore implements LegacyFlexSpanMergeRunner.Store {

    private static final String TABLE_LOOKUP =
            "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() "
                    + "AND UPPER(TABLE_NAME) = 'ARTICLE' ORDER BY TABLE_NAME LIMIT 1";

    private final String url;
    private final String username;
    private final String password;

    /** Cached after the first lookup; the standalone path is single threaded. */
    private String table;

    private LegacyFlexSpanJdbcStore(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    /** Builds the store from {@code ENV.MYSQL_*} (process environment first, then {@code .env}). */
    static LegacyFlexSpanJdbcStore fromEnvironment() {
        Properties dotenv = readDotEnv();
        String url = value(dotenv, "ENV.MYSQL_URL", null);
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("缺少 ENV.MYSQL_URL（在 .env 或进程环境变量里配置）");
        }
        return new LegacyFlexSpanJdbcStore(url,
                value(dotenv, "ENV.MYSQL_USERNAME", "root"),
                value(dotenv, "ENV.MYSQL_PASSWORD", ""));
    }

    @Override
    public List<LegacyFlexSpanMergeRunner.Store.ArticleRow> markflowFlexCandidates() {
        String sql = "SELECT id, title, content_html FROM " + quote(articleTable())
                + " WHERE deleted = 0 AND layout_engine = 'MARKFLOW'";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            List<LegacyFlexSpanMergeRunner.Store.ArticleRow> candidates = new ArrayList<>();
            while (rows.next()) {
                candidates.add(new LegacyFlexSpanMergeRunner.Store.ArticleRow(
                        rows.getLong("id"), rows.getString("title"), rows.getString("content_html")));
            }
            return candidates;
        } catch (SQLException failure) {
            throw new IllegalStateException("读取存量 MARKFLOW 稿件失败：" + failure.getMessage(), failure);
        }
    }

    @Override
    public int writeMergedContentHtml(Long id, String contentHtml) {
        if (id == null || contentHtml == null) return 0;
        String sql = "UPDATE " + quote(articleTable())
                + " SET content_html = ?, updated_at = ? WHERE id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, contentHtml);
            statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            statement.setLong(3, id);
            return statement.executeUpdate();
        } catch (SQLException failure) {
            throw new IllegalStateException("写回稿件 id=" + id + " 失败：" + failure.getMessage(), failure);
        }
    }

    // ---------- helpers ----------

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    private synchronized String articleTable() {
        if (table != null) return table;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(TABLE_LOOKUP);
             ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) {
                throw new IllegalStateException("数据库里找不到 ARTICLE 表（库：" + url + "）");
            }
            table = rows.getString(1);
            return table;
        } catch (SQLException failure) {
            throw new IllegalStateException("解析 ARTICLE 表名失败：" + failure.getMessage(), failure);
        }
    }

    private static String quote(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private static Properties readDotEnv() {
        Properties properties = new Properties();
        Path path = Path.of(System.getProperty("legacy-flex-merge.env", ".env"));
        if (!Files.isReadable(path)) return properties;
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int separator = trimmed.indexOf('=');
                if (separator <= 0) continue;
                properties.setProperty(trimmed.substring(0, separator).trim(),
                        trimmed.substring(separator + 1).trim());
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("读取 " + path + " 失败", failure);
        }
        return properties;
    }

    private static String value(Properties dotenv, String key, String fallback) {
        String fromEnvironment = System.getenv(key);
        if (fromEnvironment != null && !fromEnvironment.isBlank()) return fromEnvironment;
        String fromFile = dotenv.getProperty(key);
        return fromFile == null || fromFile.isBlank() ? fallback : fromFile;
    }
}
