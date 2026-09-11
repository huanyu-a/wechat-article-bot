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

    /**
     * skillIds 的对外 JSON 视图：库内是「逗号分隔字符串」，接口必须与写入侧
     * （{@code ArticleRequest.skillIds} 为 {@code List<Long>}）同形，否则调用方把 GET 结果原样 PUT
     * 回来会被 Jackson 以「不能把 String 反序列化成 ArrayList&lt;Long&gt;」拒绝。
     * AgentView / TaskView 是同一问题的另一种修法（那两个实体字段少，改成视图记录更直观）；
     * Article 有 27 个字段，改用访问器级序列化器可避免视图与实体长期漂移。
     * 只影响序列化方向——Article 只作响应体，请求体一律走 ArticleRequest。
     *
     * <p>两个容易踩空的点：
     * <ol>
     *   <li>注解必须落在 getter 上：字段是私有的，Jackson 的序列化主成员是访问器，
     *       字段上的 {@code @JsonSerialize} 不生效。手写 getter 后 Lombok 不再生成同名方法，
     *       字段映射不受影响（smart-mybatis 反射读字段）。</li>
     *   <li>必须用 <b>Jackson 3</b>（{@code tools.jackson}）的注解与基类：Spring Boot 4 的 HTTP 层
     *       用 Jackson 3，而本项目内部 JSON 走 Jackson 2（{@code com.fasterxml.jackson.databind}）。
     *       两个 databind 包并存——{@code com.fasterxml.jackson.annotation} 是共用的（{@code @JsonProperty} 两边都认），
     *       但 {@code ...jackson.databind.annotation} 不是：用 Jackson 2 的 {@code @JsonSerialize}
     *       会被 HTTP 层静默忽略（实测：注解在字节码里、响应仍是字符串）。</li>
     * </ol>
     */
    @tools.jackson.databind.annotation.JsonSerialize(using = SkillIdsArraySerializer.class)
    public String getSkillIds() {
        return skillIds;
    }

    /** {@link #getSkillIds()} 的数组序列化实现（Jackson 3，见该方法注释）。 */
    public static final class SkillIdsArraySerializer
            extends tools.jackson.databind.ValueSerializer<String> {
        @Override
        public void serialize(String value, tools.jackson.core.JsonGenerator generator,
                              tools.jackson.databind.SerializationContext context) {
            generator.writeStartArray();
            for (Long id : ink.icoding.wechat.article.account.WechatAccountService.parseSkillIds(value)) {
                generator.writeNumber(id.longValue());
            }
            generator.writeEndArray();
        }
    }
}
