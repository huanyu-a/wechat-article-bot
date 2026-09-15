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
    /**
     * 作者名。显式声明 255（与 {@link ArticleRevision#getAuthor()} 一致）而不是靠默认值：
     * 两个同名字段的声明文本必须**逐字相同**，否则 smart-mybatis 的列声明缓存
     * （键是字段名的单键）会在两者间二选一，结果取决于实体初始化顺序。
     * 当前两处都是 255，实测列宽也是 varchar(255)，显式写出可让这个巧合变成保证。
     */
    @TableField(length = 255)
    private String author;
    @TableField(length = 500)
    private String digest;
    /**
     * 文章正文 HTML。
     *
     * <p>为什么是 {@code MEDIUMTEXT} 而不是按 {@code length} 映射：{@code MysqlDialect.javaTypeToSql}
     * 的长度映射只有三档（&le;16383 → VARCHAR、&le;65535 → TEXT、其余 → LONGTEXT），**表达不出
     * MEDIUMTEXT**，所以必须用 {@code columnType} 直接指定（它在映射里优先级最高，见其字节码：
     * 先取 {@code columnType()}，非空即返回）。
     *
     * <p>为什么必须扩容：TEXT 的上限是 65535 **字节**，中文 UTF-8 占 3 字节，只相当于约 2 万字正文。
     * 实测全库最大文章已到 53253 字节，COORDINATOR 跑出的富文本因此报
     * {@code Data too long for column 'CONTENT_HTML'}（run#98，79 次工具调用、1655 秒全部作废）。
     * 扩容由 {@code ArticleLongTextColumnRunner} 以幂等 DDL 完成；本注解的作用是让**声明**与真实列宽
     * 同向——smart-mybatis 会按声明对已存在列发 {@code MODIFY COLUMN}，声明若停在 TEXT 会把列收窄回去
     * （见该 runner 的「字段同名共享声明」一节）。
     */
    @TableField(columnType = "MEDIUMTEXT")
    private String contentHtml;
    /**
     * 正文的纯文本（落库前由 Jsoup 从 {@link #contentHtml} 抽出）。
     *
     * <p>与 {@code contentHtml} 同在一条 INSERT 上，因此必须一起扩容：只扩 HTML 只会把报错
     * 从 {@code CONTENT_HTML} 换成 {@code CONTENT_TEXT}，长文照样进不去
     * （这是扩容用例当场跑出来的，不是推断）。
     */
    @TableField(columnType = "MEDIUMTEXT")
    private String contentText;
    /**
     * 排版引擎（PROMPT / MARKFLOW）。MARKFLOW 文章的正文由渲染服务生成，
     * 渲染产物无法反推回 Markdown，故必须留存引擎与 Markdown 源文（见 contentMarkdown）。
     */
    @TableField(length = 20)
    private String layoutEngine;
    /**
     * MARKFLOW 文章的 Markdown 源文：渲染产物被覆盖后仍可重排，也是版式保真的唯一依据。
     *
     * <p>与 {@link #contentHtml} 同为 MEDIUMTEXT：一是两者同源同写（同一次渲染产出、同一条快照写两表），
     * 二是本字段名与 {@code ArticleRevision.contentMarkdown} 同名，而 smart-mybatis 的列声明缓存按
     * 字段名共享——两处声明不一致时谁先初始化谁说了算，必然留下一个被悄悄改型的列
     * （见 {@code ArticleLongTextColumnRunner} 的「字段同名共享声明」一节）。
     */
    @TableField(columnType = "MEDIUMTEXT")
    private String contentMarkdown;
    /**
     * MARKFLOW 渲染时**实际生效**的主题色（渲染服务响应里的 theme.accent/theme.dark）。
     *
     * <p>为什么不落这两列就不行：主题色是渲染参数而不是正文内容，渲染产物 HTML 里反推不出来
     * （颜色散落在几十个内联样式里）。此前它只活在「模型这一轮传了什么」的内存态里，落库时被丢掉，
     * 于是 `POST /api/articles/{id}/rerender`（换模板/换主题后重排，I4）只能传 null，
     * 渲染服务按默认色渲染——一篇科技蓝的文章重排一次就整篇漂成默认绿，
     * 这正是「重排后的版式没有复刻原来的 MarkFlow 渲染」。
     */
    @TableField(length = 20)
    private String themeAccent;
    /** 见 {@link #themeAccent}；未显式指定时由渲染服务派生，同样必须留存。 */
    @TableField(length = 20)
    private String themeDark;
    @TableField(length = 500)
    private String coverUrl;
    private Long coverAssetId;
    /**
     * 文章来源 URL。与 {@code ArticleRevision.sourceUrl}、{@code Asset.sourceUrl} 同名，
     * 三处**必须都是 1000**（实测三张表的列宽也都是 varchar(1000)）。
     * 此前 {@code Asset} 那处声明 2000，结果 ASSET.SOURCE_URL 被静默收窄成 1000——见 Asset 的说明。
     */
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
