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
    @TableField(length = 65535)
    private String contentHtml;
    /** 版本来源 URL，同 author：属于随版本变化的可编辑状态。 */
    @TableField(length = 1000)
    private String sourceUrl;
    /** 排版引擎（PROMPT/MARKFLOW）。缺此列时回滚无法判断该版本该用哪个引擎。 */
    @TableField(length = 20)
    private String layoutEngine;
    /** MARKFLOW 版本的 Markdown 源文；缺此列时回滚只能恢复渲染产物、永远拿不回可重排的源文。 */
    @TableField(length = 65535)
    private String contentMarkdown;
    private String changeSource;
    @TableField(length = 500)
    private String changeSummary;
    private Long createdBy;
    private LocalDateTime createdAt;
}
