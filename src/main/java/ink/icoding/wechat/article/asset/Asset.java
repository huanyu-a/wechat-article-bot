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
    @TableField(length = 2000)
    private String sourceUrl;
    @TableField(length = Asset.DESCRIPTION_MAX_LENGTH)
    private String description;
    private String wechatMediaId;
    @TableField(length = 1000)
    private String wechatContentUrl;
    private Long createdBy;
    private LocalDateTime createdAt;
}
