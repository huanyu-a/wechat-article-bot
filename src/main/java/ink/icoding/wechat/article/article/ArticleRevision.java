package ink.icoding.wechat.article.article;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@SmartMeta
@TableName("article_revision")
public class ArticleRevision extends PO {
    @ID
    private Long id;
    private Long articleId;
    private Integer revision;
    private String title;
    /**
     * 版本作者。随版本快照才能让回滚恢复「该版本当时的完整状态」，
     * 否则回滚只换来标题/摘要/正文，作者仍是当前值（部分合并）。
     */
    @TableField(length = 255)
    private String author;
    @TableField(length = 500)
    private String digest;
    /**
     * 该版本正文的 HTML。
     *
     * <p>必须与 {@code Article.contentHtml} 同为 MEDIUMTEXT：{@code ArticleService.snapshot()} 把
     * **同一份正文**同时写进两张表，主表放宽而这里停在 TEXT，等于把「正文超 64KB」的失败原样搬到版本表。
     * 实测该列已存 62118 字节（占 TEXT 上限 95%），而 run#98 的富文本在 75KB 以上。
     *
     * <p>另一层原因：本字段与 {@code Article.contentHtml} **同名**，而 smart-mybatis 的列声明缓存
     * 按字段名共享（{@code MapperUtil.getColumnDeclaration} 的键是 {@code field.getName()} 单键，
     * 不含类名），两处声明不一致时结果取决于实体初始化顺序——同步逻辑会按「赢家」的声明对另一张表发
     * MODIFY，声明窄的一方会把已放宽的列收窄回去。声明必须与真实列宽同向。
     */
    @TableField(columnType = "MEDIUMTEXT")
    private String contentHtml;
    /** 版本来源 URL，同 author：属于随版本变化的可编辑状态。 */
    @TableField(length = 1000)
    private String sourceUrl;
    /** 排版引擎（PROMPT/MARKFLOW）。缺此列时回滚无法判断该版本该用哪个引擎。 */
    @TableField(length = 20)
    private String layoutEngine;
    /**
     * MARKFLOW 版本的 Markdown 源文；缺此列时回滚只能恢复渲染产物、永远拿不回可重排的源文。
     *
     * <p>与 {@link #contentHtml} 同为 MEDIUMTEXT：Markdown 与 HTML 同源同写（同一次渲染、同一次快照），
     * 且本字段名与 {@code Article.contentMarkdown} 同名而共享列声明（见 {@link #contentHtml}）。
     */
    @TableField(columnType = "MEDIUMTEXT")
    private String contentMarkdown;
    /** 版本渲染时实际生效的主题色；缺此列时回滚后的重排会用默认色，与回滚到的版式对不上。 */
    @TableField(length = 20)
    private String themeAccent;
    @TableField(length = 20)
    private String themeDark;
    private String changeSource;
    @TableField(length = 500)
    private String changeSummary;
    private Long createdBy;
    private LocalDateTime createdAt;
}
