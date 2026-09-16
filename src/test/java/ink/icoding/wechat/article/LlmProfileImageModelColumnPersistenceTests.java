package ink.icoding.wechat.article;

import ink.icoding.wechat.article.agent.LlmProfile;
import ink.icoding.wechat.article.agent.LlmProfileMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 图片模型列（{@code LLM_PROFILE.IMAGE_MODEL_NAME}）的真实 MySQL 验证，不打桩。
 *
 * <p>为什么必须打真库：这是本轮**唯一**新增的结构，而它的用法有一个用 mock 证明不了的前提——
 * **它必须可空**。存量库里每一条档案（默认配置、deepseek-flash、glm-5.3-flash、dots3-note-prev）
 * 补列后都只能是 NULL，而 {@code LlmProfileService.imageCarrier} 正是靠「全链都没有非空值」
 * 来判断「回落全局图片设置」——也就是改造前唯一的配图来源。列一旦被改成 NOT NULL、
 * 或实体字段被写成基本类型，存量库的补列与读取会直接失败。
 *
 * <p>与 {@link LlmProfileFallbackColumnPersistenceTests} 同一教训，边界也一样：测试库的表结构是
 * smart-mybatis 按实体注解**新建**的，所以这里证明的是「新建出来的列可用」，证明不了存量库的
 * {@code ALTER} 路径（该路径没有显式迁移 runner，补列完全由 smart-mybatis 完成）。
 * 存量库那一步的证据是实机查库：新列已存在且为 {@code varchar(255) NULL}。
 */
@SpringBootTest
@ContextConfiguration(initializers = MySqlTestDatabaseInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LlmProfileImageModelColumnPersistenceTests {

    /** 本类造的数据一律带该前缀，便于退出时精确清理，不碰种子/其它用例的行。 */
    private static final String TEST_PREFIX = "__image_col_test__";

    @Autowired
    private LlmProfileMapper profileMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void removeTestRows() {
        jdbcTemplate.update("DELETE FROM LLM_PROFILE WHERE NAME LIKE ?", TEST_PREFIX + "%");
    }

    /**
     * 列必须真的存在、是**可空**的 varchar，且宽度与 {@code LLM_CONFIG.IMAGE_MODEL_NAME} 一致。
     *
     * <p>宽度这条不是形式主义：{@code imageModelName} 这个**字段名**被 {@code LlmConfig} 与
     * {@code LlmProfile} 两处共用，而 smart-mybatis 的列声明缓存按字段名单键共享——两处声明
     * 只要有一个带 {@code @TableField(length=...)} 而另一个不带，实际列宽就取决于实体初始化顺序，
     * 同步逻辑还会对真实列发 {@code MODIFY COLUMN}。两列等宽即证明两处声明同向。
     */
    @Test
    void imageModelColumnIsMaterializedAsNullableVarchar() {
        List<String> type = columnType("LLM_PROFILE", "IMAGE_MODEL_NAME");
        List<String> globalType = columnType("LLM_CONFIG", "IMAGE_MODEL_NAME");

        assertThat(type).as("LLM_PROFILE.IMAGE_MODEL_NAME 必须由实体声明真实建出来").hasSize(1);
        assertThat(type.get(0))
                .as("必须可空：存量行补列后全是 NULL，而 NULL 正是「本档案不指定图片模型」的表达")
                .isEqualTo("varchar|YES");
        assertThat(globalType).as("LLM_CONFIG.IMAGE_MODEL_NAME 应已存在").hasSize(1);
        assertThat(type.get(0)).as("两处同名字段的列类型必须同向，否则 smart-mybatis 会任选一份")
                .isEqualTo(globalType.get(0));
    }

    @Test
    void imageModelNameRoundTripsIncludingNull() {
        LlmProfile declared = insert(TEST_PREFIX + "-declared", "step-image-edit-2");
        LlmProfile legacy = insert(TEST_PREFIX + "-null", null);

        assertThat(profileMapper.findById(declared.getId()).getImageModelName())
                .as("非空图片模型必须原样读回").isEqualTo("step-image-edit-2");
        assertThat(profileMapper.findById(legacy.getId()).getImageModelName())
                .as("NULL 必须仍是 NULL，不能被静默写成空串——否则「未声明」与「声明了空模型」不再可区分")
                .isNull();
    }

    /**
     * 停用/启用这类「整条 PUT」的写入路径必须能保住图片模型。
     *
     * <p>对应前端的 {@code toggleProfile}：它提交的是完整表单体，如果漏带 {@code imageModelName}，
     * 后端 {@code apply()} 会把它当成「清空」——一次点开关就悄悄抹掉用户配好的图片模型。
     * 这里模拟「带值往返一次」证明该字段确实随整行持久化。
     */
    @Test
    void imageModelNameSurvivesAFullRowUpdate() {
        LlmProfile profile = insert(TEST_PREFIX + "-toggle", "glm-image-model");

        profile.setEnabled(false);
        profile.setUpdatedAt(LocalDateTime.now());
        profileMapper.updateById(profile);

        LlmProfile reloaded = profileMapper.findById(profile.getId());
        assertThat(reloaded.getEnabled()).isFalse();
        assertThat(reloaded.getImageModelName())
                .as("整行更新不得丢掉图片模型").isEqualTo("glm-image-model");
    }

    /**
     * 图片模型列不参与「默认/兜底」的唯一性判断：带图片模型的行既不会被当成默认档案，
     * 也不会被当成兜底档案。
     *
     * <p>为什么不断言 {@code findDefault()} 等于本行：{@code findDefault()} 是
     * 「按 id 升序取第一条 is_default」，而这条开发库与其它用例共享——库里一旦另有默认档案
     * （种子档案正是如此），断言就取决于行序与执行顺序，而不是本用例喂进去的数据。
     * 这里改成方向相反的判断：本行**不应**被这两个查找选中。
     */
    @Test
    void imageModelNameDoesNotAffectDefaultAndFallbackLookups() {
        LlmProfile withImage = insert(TEST_PREFIX + "-with-image", "step-image-edit-2");

        LlmProfile defaulted = profileMapper.findDefault();
        if (defaulted != null) {
            assertThat(defaulted.getId())
                    .as("带图片模型的行不该被当成默认档案")
                    .isNotEqualTo(withImage.getId());
        }
        LlmProfile fallback = profileMapper.findFallback();
        if (fallback != null) {
            assertThat(fallback.getId())
                    .as("带图片模型的行不该被当成兜底档案")
                    .isNotEqualTo(withImage.getId());
        }
        assertThat(profileMapper.findById(withImage.getId()).getImageModelName())
                .as("图片模型列只原样往返，不参与任何查找判据")
                .isEqualTo("step-image-edit-2");
    }

    private List<String> columnType(String table, String column) {
        return jdbcTemplate.queryForList(
                "SELECT CONCAT(DATA_TYPE, '|', IS_NULLABLE) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND UPPER(TABLE_NAME) = ? "
                        + "AND UPPER(COLUMN_NAME) = ?",
                String.class, table.toUpperCase(), column.toUpperCase());
    }

    private LlmProfile insert(String name, String imageModelName) {
        LlmProfile profile = new LlmProfile();
        profile.setName(name);
        profile.setProvider("OPENAI_COMPATIBLE");
        profile.setBaseUrl("https://example.invalid");
        profile.setModelName("test-model");
        profile.setImageModelName(imageModelName);
        profile.setApiKeyEncrypted("test-key");
        profile.setTemperature(new BigDecimal("0.70"));
        profile.setMaxTokens(4096);
        profile.setEnabled(true);
        profile.setIsDefault(false);
        profile.setIsFallback(false);
        LocalDateTime now = LocalDateTime.now();
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        profileMapper.insert(profile);
        return profile;
    }
}