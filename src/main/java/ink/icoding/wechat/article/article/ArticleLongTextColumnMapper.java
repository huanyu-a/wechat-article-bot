package ink.icoding.wechat.article.article;

import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 文章正文长文本列的列类型查询与扩容（见 {@link ArticleLongTextColumnRunner}）。
 *
 * <p>为什么单独一个 Mapper 而不是挂在 {@code ArticleMapper} 上：这是**一次性结构迁移**，
 * 与文章的业务读写无关；混进业务 Mapper 会让「为什么文章 Mapper 里有一条 ALTER TABLE」变成
 * 后来者的疑问。查询走 {@code information_schema} 而不是尝试解析异常——先判类型再决定是否 DDL，
 * 才能保证幂等且不依赖「报错即说明没改过」这种脆弱推断。
 */
@Mapper
public interface ArticleLongTextColumnMapper extends SmartMapper<Article> {

    /**
     * 列在库中的真实形态。
     *
     * @param tableName  库里真实的表名。**不能假设大小写**：本仓库的开发库是 {@code ARTICLE}、
     *                   实体注解却是 {@code article}，而 Linux 上表名大小写敏感，拼错就是「表不存在」。
     *                   所以表名从 {@code information_schema} 回读，而不是拿注解值去拼 DDL。
     * @param columnName 库里真实的列名，同上。
     * @param dataType   小写类型名（{@code text} / {@code mediumtext} / {@code longtext} …）。
     * @param nullable   是否允许 NULL。扩容时必须原样保留，否则会把可空列悄悄改成 NOT NULL。
     */
    record ColumnState(String tableName, String columnName, String dataType, boolean nullable) {
    }

    /**
     * 读某列在库中的真实形态（表名/列名大小写以库为准）。
     *
     * @return 表或列不存在时返回 {@code null}
     */
    default ColumnState columnState(String tableName, String columnName) {
        List<Map<String, Object>> rows = queryBySql(
                "SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND UPPER(TABLE_NAME) = UPPER(?) AND UPPER(COLUMN_NAME) = UPPER(?)",
                tableName, columnName);
        if (rows == null || rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        return new ColumnState(
                text(row, "TABLE_NAME"), text(row, "COLUMN_NAME"), text(row, "DATA_TYPE"),
                "YES".equalsIgnoreCase(text(row, "IS_NULLABLE")));
    }

    /**
     * 把指定列扩到 MEDIUMTEXT（幂等由调用方先判类型保证）。
     *
     * <p>列名来自 {@link ArticleLongTextColumnRunner#TARGETS} 这个常量白名单，不接受外部输入；
     * 仍显式校验，避免将来有人把它改成「从配置读表名列名」时把拼接口子留下。
     */
    default int modifyToMediumText(ColumnState state) {
        boolean allowed = ArticleLongTextColumnRunner.TARGETS.stream()
                .anyMatch(target -> target.table().equalsIgnoreCase(state.tableName())
                        && target.column().equalsIgnoreCase(state.columnName()));
        if (!allowed) {
            throw new IllegalArgumentException("不允许扩容的列：" + state.tableName() + "." + state.columnName());
        }
        // 可空性原样保留：TEXT 家族的可空与否是语义，不是排版细节，顺手改成 NOT NULL 会让存量
        // 「正文为空」的草稿在下次写入时直接失败。这里各列实测都是可空、无默认值。
        return executeSql("ALTER TABLE " + quote(state.tableName())
                + " MODIFY COLUMN " + quote(state.columnName()) + " "
                + ArticleLongTextColumnRunner.TARGET_TYPE + (state.nullable() ? " NULL" : " NOT NULL"));
    }

    /** 标识符加反引号；表名/列名只来自 information_schema，这里只是防御性处理。 */
    private static String quote(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    /** 按忽略大小写的键名取值：驱动/MySQL 版本不同，返回的列名大小写会变。 */
    private static String text(Map<String, Object> row, String key) {
        Object direct = row.get(key);
        if (direct != null) return direct.toString();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue() == null ? null : entry.getValue().toString();
            }
        }
        return null;
    }
}
