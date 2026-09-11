package ink.icoding.wechat.article;

import ink.icoding.wechat.article.asset.Asset;
import ink.icoding.wechat.article.asset.AssetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 超长图片描述的落库验证（真实 MySQL 列，不打桩）。
 *
 * <p>为什么必须打真库：{@code AssetServicePathTests} 用 mock mapper，只能证明「截断逻辑被调用」，
 * 证明不了「截断后的值真能插进 ASSET.DESCRIPTION」。而事故恰恰出在真实列宽上——
 * {@code DESCRIPTION} 是 {@code varchar(500)}，生成图给出的长描述让整条 insert 失败，
 * 图片连素材库都进不去，运行却记 SUCCESS。
 *
 * <p>本用例同时钉住实体声明与真实列宽的一致性：若把 {@link Asset#DESCRIPTION_MAX_LENGTH}
 * 调大到超过实际列宽（smart-mybatis 不会改已有列的长度），这里的插入会重新报
 * {@code Data too long for column 'DESCRIPTION'}，用例立刻失败。
 */
@SpringBootTest
@ContextConfiguration(initializers = MySqlTestDatabaseInitializer.class)
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class AssetDescriptionPersistenceTests {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @Autowired
    private AssetService assetService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void overlongGeneratedDescriptionIsTruncatedSoTheImageStillLands() {
        String description = "生成图描述".repeat(200); // 1000 字符，远超真实列宽

        Asset saved = assetService.saveImage(3L, "ai-generated.png", "image/png", PNG,
                "GENERATED", null, description, 1L);

        assertThat(saved.getId()).isNotNull();
        String stored = jdbcTemplate.queryForObject(
                "SELECT DESCRIPTION FROM ASSET WHERE ID = ?", String.class, saved.getId());
        assertThat(stored).hasSize(Asset.DESCRIPTION_MAX_LENGTH);
        assertThat(stored).isEqualTo(description.substring(0, Asset.DESCRIPTION_MAX_LENGTH));

        jdbcTemplate.update("DELETE FROM ASSET WHERE ID = ?", saved.getId());
    }
}
