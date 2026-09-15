package ink.icoding.wechat.article;

import ink.icoding.wechat.article.article.Article;
import ink.icoding.wechat.article.article.ArticleLongTextColumnRunner;
import ink.icoding.wechat.article.article.ArticleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 超长正文的落库验证（真实 MySQL 列，不打桩）。
 *
 * <p>为什么必须打真库：{@code ARTICLE.CONTENT_HTML} 原本是 {@code TEXT}，上限 65535 **字节**——
 * 中文 UTF-8 占 3 字节，约合 2 万字正文。实测全库最大文章已到 53253 字节，run#98（COORDINATOR，
 * 79 次工具调用、1655 秒）产出的富文本越过上限，报
 * {@code Data too long for column 'CONTENT_HTML'}，整轮作废。
 * 用 mock mapper 的用例只能证明「字段被赋值了」，证明不了「这个长度真能插进去」——
 * 而事故恰恰出在真实列宽上（与 {@link AssetDescriptionPersistenceTests} 同一教训）。
 *
 * <p>本用例同时钉住实体声明与真实列宽的一致性：若把 {@code Article.contentHtml} 的
 * {@code @TableField(columnType)} 改回按 length 映射（即 TEXT），这里的插入会重新报
 * {@code Data too long}，用例立刻失败。
 */
@SpringBootTest
@ContextConfiguration(initializers = MySqlTestDatabaseInitializer.class)
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ArticleLongContentPersistenceTests {

    /** 超过 TEXT 的 65535 字节上限：中文 3 字节/字，2.5 万字约 7.5 万字节。 */
    private static final int OVERLONG_CHARS = 25_000;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ArticleLongTextColumnRunner runner;

    /** 无论用例怎么改列，退出时都恢复成迁移的目标形态，避免污染同类中的其它用例。 */
    @org.junit.jupiter.api.AfterEach
    void restoreWideColumns() {
        runner.run(null);
    }

    @Test
    void everyBodyColumnIsWideEnoughForLongRenderedContent() {
        for (ArticleLongTextColumnRunner.Target target : ArticleLongTextColumnRunner.TARGETS) {
            assertThat(currentType(target))
                    .as("%s 必须宽于 TEXT，否则长文落库必然报 Data too long", target)
                    .isIn("mediumtext", "longtext");
        }
    }

    /**
     * 迁移路径本身的验证。
     *
     * <p>上面那条只证明「新建库时列是宽的」——建表走实体注解，压根不经过 {@code ALTER}。
     * 而事故发生在**存量库**（列早已是 TEXT），所以必须把列真的改回 TEXT，再让迁移跑一遍，
     * 才能证明扩容 DDL 有效；顺带证明幂等（第二遍不再改）。
     */
    @Test
    void narrowColumnsAreWidenedByIdempotentMigration() {
        for (ArticleLongTextColumnRunner.Target target : ArticleLongTextColumnRunner.TARGETS) {
            jdbcTemplate.execute("ALTER TABLE " + target.table() + " MODIFY COLUMN " + target.column() + " TEXT");
            assertThat(currentType(target)).as("反例前置：%s 已被改回 TEXT", target).isEqualTo("text");

            runner.run(null);

            assertThat(currentType(target))
                    .as("迁移必须把存量库的 %s 从 TEXT 扩到 MEDIUMTEXT", target)
                    .isEqualTo("mediumtext");

            // 幂等：已是 MEDIUMTEXT 时再跑一遍不应报错、也不应改变类型
            runner.run(null);
            assertThat(currentType(target)).as("重复执行必须幂等").isEqualTo("mediumtext");
        }
    }

    /**
     * 正文会**同时**落进 ARTICLE 与 ARTICLE_REVISION，两张表都必须撑得住。
     *
     * <p>这条是只扩主表时漏掉的：{@code ArticleService.createWithUser} 建完文章立刻
     * {@code snapshot()}，把同一份 HTML 写进版本表。只扩 ARTICLE 的修法会让 run#98 的失败
     * 从主表平移到版本表，长文照样整轮作废——所以插入之后必须连版本行一起验。
     */
    @Test
    void overlongContentLandsInBothArticleAndItsFirstRevision() {
        String html = overlongHtml();

        ArticleService.ArticleRequest request = new ArticleService.ArticleRequest(
                3L, "长文落库验证", null, "摘要", html,
                null, null, null, null, null, null, null, null, null);
        Article saved = articleService.createForTask(request, 1L);

        assertThat(saved.getId()).isNotNull();
        assertLongerThanTextLimit("SELECT CONTENT_HTML FROM ARTICLE WHERE ID = ?", saved.getId(),
                "主表正文");
        // 建文即快照，版本表存的是同一份 HTML：它同样要越过 65535 字节
        assertLongerThanTextLimit(
                "SELECT CONTENT_HTML FROM ARTICLE_REVISION WHERE ARTICLE_ID = ? ORDER BY ID LIMIT 1",
                saved.getId(), "版本表正文");

        jdbcTemplate.update("DELETE FROM ARTICLE_REVISION WHERE ARTICLE_ID = ?", saved.getId());
        jdbcTemplate.update("DELETE FROM ARTICLE WHERE ID = ?", saved.getId());
    }

    private String overlongHtml() {
        String paragraph = "<p>" + "长文正文".repeat(200) + "</p>"; // 每段 800 字
        StringBuilder html = new StringBuilder();
        while (html.length() < OVERLONG_CHARS * 3) html.append(paragraph);
        return html.toString();
    }

    private void assertLongerThanTextLimit(String sql, Long id, String what) {
        String stored = jdbcTemplate.queryForObject(sql, String.class, id);
        // 用字节数断言才是真正的判据：TEXT 的上限是字节而不是字符
        assertThat(stored.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .as("%s 必须超过 TEXT 的 65535 字节上限，才证明扩容真的生效", what)
                .isGreaterThan(65_535);
    }

    private String currentType(ArticleLongTextColumnRunner.Target target) {
        return jdbcTemplate.queryForObject(
                "SELECT DATA_TYPE FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND UPPER(TABLE_NAME) = UPPER(?) "
                        + "AND UPPER(COLUMN_NAME) = UPPER(?)",
                String.class, target.table(), target.column());
    }
}
