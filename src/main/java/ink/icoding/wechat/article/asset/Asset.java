package ink.icoding.wechat.article.asset;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@SmartMeta
@TableName("asset")
public class Asset extends PO {
    /**
     * 图片描述的最大长度（字符）。生图/导图工具写入的描述可能很长，而描述只是元数据——
     * 一旦超长导致 insert 失败，**图片本身就进不了素材库**（实测 generate_image 报
     * {@code Data too long for column 'DESCRIPTION'}，配图缺失但整次运行还记了成功）。
     * 因此实体长度与写入侧截断共用本常量，任何一侧调整都不会漏掉另一侧。
     *
     * <p>为什么是 500 而不是更大：smart-mybatis 只做「建表 / 补缺失列」，**不会改已有列的长度**
     * （{@code DefaultSmartMapperInitializer.syncDatabaseStructure} 里没有 MODIFY COLUMN），
     * 所以把这里改成 2000 只会让实体声明与真实列（实测 {@code varchar(500)}）不一致——
     * 1500 字的描述照样插不进去。方案 ③3 因此取「写入前截断到列长」这一支。
     * 若确实需要更长描述，必须先加一次显式的 {@code ALTER TABLE ASSET MODIFY DESCRIPTION ...}。
     */
    public static final int DESCRIPTION_MAX_LENGTH = 500;

    @ID
    private Long id;
    private Long accountId;
    private String originalName;
    private String storageName;
    @TableField(length = 1000)
    private String storagePath;
    @TableField(length = 1000)
    private String publicUrl;
    private String contentType;
    private Long fileSize;
    private String sourceType;
    /**
     * 素材来源页 URL（导入网页图片时记录）。
     *
     * <p><b>声明必须与真实列宽同向</b>：{@code sourceUrl} 这个字段名在 {@link ink.icoding.wechat.article.article.Article}
     * 与 {@code ArticleRevision} 里也存在，而 smart-mybatis 的列声明缓存按**字段名**共享
     * （{@code MapperUtil.getColumnDeclaration} 的键是 {@code field.getName()} 单键，不含类名），
     * 谁先初始化谁说了算。此处曾声明 2000 而另两处是 1000，实测结果就是
     * {@code ASSET.SOURCE_URL} 被**静默收窄成 varchar(1000)**——声明与真实列宽不一致，
     * 而 {@code MysqlDialect.buildAlterColumn} 会发 {@code MODIFY COLUMN}，
     * 意味着另两处哪天先初始化，这一列的长度还会随初始化顺序漂移。
     *
     * <p>取 1000 与另两处对齐（实测最大来源 URL 94 字符，余量充足）：三处声明一致后，
     * 同步结果与初始化顺序无关，也不会对任何一张表发出收窄 DDL。
     * 若将来确需更长的来源 URL，**必须三处一起改并配一次显式 DDL**，不能只改这里。
     */
    @TableField(length = 1000)
    private String sourceUrl;
    @TableField(length = Asset.DESCRIPTION_MAX_LENGTH)
    private String description;
    private String wechatMediaId;
    @TableField(length = 1000)
    private String wechatContentUrl;
    private Long createdBy;
    private LocalDateTime createdAt;
}
