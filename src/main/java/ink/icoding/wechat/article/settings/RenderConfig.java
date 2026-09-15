package ink.icoding.wechat.article.settings;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 排版渲染服务配置（render_config 单行表，模式沿用 llm_config）。
 * 渲染令牌 AES 加密入库（密钥 = APP_SECRET_KEY），API 返回掩码；
 * site_base_url 为本站公网地址，MARKFLOW 渲染前把 /uploads/ 相对路径改写为绝对直链（2026-09-08 Spike 定案）。
 */
@Data
@SmartMeta
@TableName("render_config")
public class RenderConfig extends PO {
    @ID
    private Long id;
    /**
     * 渲染服务供应商标识。显式声明 50（与 {@code LlmConfig.provider} / {@code LlmProfile.provider} 一致）：
     * 三处同名共用 smart-mybatis 的字段名级列声明缓存，声明文本不一致时结果取决于初始化顺序。
     * 实测三张表都是 varchar(50)，显式写出即与真实列宽同向。
     */
    @TableField(length = 50)
    private String provider;
    @TableField(length = 500)
    private String baseUrl;
    @TableField(length = 65535)
    private String tokenEncrypted;
    @TableField(length = 500)
    private String siteBaseUrl;
    private Integer syntaxCacheTtlSeconds;
    private Boolean enabled;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
