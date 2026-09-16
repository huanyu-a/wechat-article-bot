package ink.icoding.wechat.article;

import ink.icoding.wechat.article.agent.LlmProfile;
import ink.icoding.wechat.article.agent.LlmProfileMapper;
import ink.icoding.wechat.article.agent.LlmProfileSeeder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 兜底档案列（{@code LLM_PROFILE.IS_FALLBACK}）的真实 MySQL 验证，不打桩。
 *
 * <p>为什么必须打真库：这个列是本轮（Phase 2）**唯一**新增的结构，而它的用法有一个
 * 用 mock 证明不了的前提——**它是可空的**。开发库里就真实存在 {@code IS_FALLBACK = NULL}
 * 的存量行（smart-mybatis 补列时老行只能是 NULL），而
 * {@link LlmProfileMapper#findFallback()} 据此选「最后安全网」。若哪天有人把这个类字段
 * 改成基本类型、或给列加上 NOT NULL，存量库的补列/写入会直接失败——那种故障只会出现在
 * 真实列上，mock 用例永远绿。
 *
 * <p>与 {@link ArticleLongContentPersistenceTests} 同一教训，但**验证能力有边界**：
 * 测试库的表结构是 smart-mybatis 按实体注解**新建**的，所以这里能证明「新建出来的列可用」，
 * 证明不了「存量库的 ALTER 路径」（本列没有显式迁移 runner，补列完全由 smart-mybatis 完成。
 * 存量库那一步的证据是 2026-09-16 的实机查库：新列已存在、兜底档案恰好 1 条）。
 *
 * <p>本用例同时钉住 {@link LlmProfileSeeder} 里**唯一**一处「种子替用户做决定」的行为：
 * 未设置兜底时把默认档案标为兜底，且只做一次。
 */
@SpringBootTest
@ContextConfiguration(initializers = MySqlTestDatabaseInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LlmProfileFallbackColumnPersistenceTests {

    /** 本类造的数据一律带该前缀，便于退出时精确清理，不碰种子/其它用例的行。 */
    private static final String TEST_PREFIX = "__fallback_col_test__";

    @Autowired
    private LlmProfileMapper profileMapper;

    @Autowired
    private LlmProfileSeeder seeder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void removeTestRows() {
        jdbcTemplate.update("DELETE FROM LLM_PROFILE WHERE NAME LIKE ?", TEST_PREFIX + "%");
    }

    /**
     * 列必须真的存在、且是**可空的 tinyint**。
     *
     * <p>{@code IS_NULLABLE} 这条不是形式主义：存量行补列后就是 NULL，列一旦变成 NOT NULL，
     * 存量库的补列会失败或老行被判非法；而 {@code findFallback()} 依赖的正是「NULL 行不算兜底」。
     */
    @Test
    void fallbackColumnIsMaterializedAsNullableTinyint() {
        List<String> type = jdbcTemplate.queryForList(
                "SELECT CONCAT(DATA_TYPE, '|', IS_NULLABLE) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND UPPER(TABLE_NAME) = 'LLM_PROFILE' "
                        + "AND UPPER(COLUMN_NAME) = 'IS_FALLBACK'",
                String.class);

        assertThat(type)
                .as("LLM_PROFILE.IS_FALLBACK 必须由实体声明真实建出来")
                .hasSize(1);
        assertThat(type.get(0))
                .as("必须是可空 tinyint：存量库补列后老行为 NULL，且 NULL 行不能被当成兜底档案")
                .isEqualTo("tinyint|YES");
    }

    /**
     * true / false / NULL 三种状态都要能原样往返。
     *
     * <p>NULL 这一档是关键——它模拟的正是开发库里那条存量行（{@code dots3-note-prev}）。
     */
    @Test
    void fallbackFlagRoundTripsThroughMapperIncludingNull() {
        LlmProfile flagged = insert(TEST_PREFIX + "-true", true);
        LlmProfile unflagged = insert(TEST_PREFIX + "-false", false);
        LlmProfile legacy = insert(TEST_PREFIX + "-null", null);

        assertThat(profileMapper.findById(flagged.getId()).getIsFallback())
                .as("true 必须原样读回").isTrue();
        assertThat(profileMapper.findById(unflagged.getId()).getIsFallback())
                .as("false 必须原样读回").isFalse();
        assertThat(profileMapper.findById(legacy.getId()).getIsFallback())
                .as("NULL 必须仍是 NULL，不能被静默写成 false（否则存量行会被当成「明确不是兜底」）")
                .isNull();
    }

    /**
     * {@code findFallback()} 只认 true：false 与 NULL 的行都不能干扰它。
     *
     * <p>这正是故障切换链第三段（兜底）的取数逻辑，取错意味着「主用挂了以后挂到一个没配 key 的废档案」。
     */
    @Test
    void findFallbackIgnoresNullAndFalseRows() {
        insert(TEST_PREFIX + "-null", null);
        insert(TEST_PREFIX + "-false", false);
        LlmProfile expected = insert(TEST_PREFIX + "-true", true);

        LlmProfile found = profileMapper.findFallback();

        assertThat(found).as("必须命中被标记为兜底的那一条").isNotNull();
        assertThat(found.getId()).isEqualTo(expected.getId());
        assertThat(found.getName()).isEqualTo(TEST_PREFIX + "-true");
    }

    /**
     * 种子行为：库里还没有兜底时，把默认档案标为兜底；已有兜底则不动；重复执行幂等。
     *
     * <p>「只做一次」很重要——用户手工指定过兜底档案后，种子不能每次启动都改回去。
     */
    @Test
    void seederMarksDefaultAsFallbackOnlyOnceAndNeverOverridesTheUser() {
        LlmProfile defaultProfile = insert(TEST_PREFIX + "-default", false);
        defaultProfile.setIsDefault(true);
        profileMapper.updateById(defaultProfile);

        seeder.run(null);

        LlmProfile afterFirstRun = profileMapper.findById(defaultProfile.getId());
        assertThat(afterFirstRun.getIsFallback())
                .as("未设置兜底时，种子应把默认档案标为兜底")
                .isTrue();

        // 用户改选另一条作为兜底
        LlmProfile userChoice = insert(TEST_PREFIX + "-user-choice", true);
        jdbcTemplate.update("UPDATE LLM_PROFILE SET IS_FALLBACK = 0 WHERE ID = ?", afterFirstRun.getId());

        seeder.run(null);

        assertThat(profileMapper.findById(afterFirstRun.getId()).getIsFallback())
                .as("已有兜底时，种子不得覆盖用户的选择")
                .isFalse();
        assertThat(profileMapper.findById(userChoice.getId()).getIsFallback())
                .as("用户指定的兜底档案必须保持不动").isTrue();
    }

    private LlmProfile insert(String name, Boolean isFallback) {
        LlmProfile profile = new LlmProfile();
        profile.setName(name);
        profile.setProvider("OPENAI_COMPATIBLE");
        profile.setBaseUrl("https://example.invalid");
        profile.setModelName("test-model");
        profile.setApiKeyEncrypted("test-key");
        profile.setEnabled(true);
        profile.setIsDefault(false);
        profile.setIsFallback(isFallback);
        LocalDateTime now = LocalDateTime.now();
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        profileMapper.insert(profile);
        return profile;
    }
}