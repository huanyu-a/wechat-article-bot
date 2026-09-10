package ink.icoding.wechat.article.article;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import ink.icoding.wechat.article.account.WechatAccount;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@SmartMeta
@TableName("article")
public class Article extends PO {
    @ID
    private Long id;
    private Long accountId;
    @TableField(exist = false, link = WechatAccount.class, linkField = "name", self = "accountId", target = "id")
    private String accountName;
    private String title;
    private String author;
    @TableField(length = 500)
    private String digest;
    @TableField(length = 65535)
    private String contentHtml;
    @TableField(length = 65535)
    private String contentText;
    /**
     * 排版引擎（PROMPT / MARKFLOW）。MARKFLOW 文章的正文由渲染服务生成，
     * 渲染产物无法反推回 Markdown，故必须留存引擎与 Markdown 源文（见 contentMarkdown）。
     */
    @TableField(length = 20)
    private String layoutEngine;
    /** MARKFLOW 文章的 Markdown 源文：渲染产物被覆盖后仍可重排，也是版式保真的唯一依据。 */
    @TableField(length = 65535)
    private String contentMarkdown;
    @TableField(length = 500)
    private String coverUrl;
    private Long coverAssetId;
    @TableField(length = 1000)
    private String sourceUrl;
    private String sourceType;
    /** 文章级创作技能 id 的 JSON 数组字符串（编辑器 AI 对话用，最高优先级），skills-agent-plan 4.5。 */
    @TableField(length = 2000)
    private String skillIds;
    private String businessStatus;
    private String workflowStatus;
    private String wechatStatus;
    private String wechatMediaId;
    private String wechatArticleId;
    private String wechatPublishId;
    private Integer revision;
    private Boolean deleted;
    private LocalDateTime publishedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
