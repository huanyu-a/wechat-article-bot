package ink.icoding.wechat.article.agent;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型供应商（llm_provider 表）：**端点与密钥的唯一归属地**，模型档案只声明「用哪个模型」。
 *
 * <p><b>为什么要把端点与密钥从档案里搬到供应商</b>：存量库的实测形态是 8 条档案的
 * {@code PROVIDER} / {@code BASE_URL} / {@code API_KEY_ENCRYPTED} 三列**逐字相同**，只有模型名不同
 * （{@code docs/dev/llm-provider-refactor.md} §1）。当时靠 {@code LlmProfileSeeder} 逐字复制那一列
 * 绕过去了，代价是同一份密钥在库里存了 8 份、需要分别维护——换一次 key 要改 8 处，
 * 漏一处就是一个**只在故障切换时才暴露**的坏档案。现在换 key 只改本表一行。
 *
 * <p><b>本类不含「模型目录 / 显示名称 / 上下文窗口 / 推理等级」等字段</b>（契约 §2 明确列为后续项）：
 * 本项目的档案 {@code name} 就是显示名、{@code modelName} 就是上游模型名，多带一层
 * 只会扩大迁移面，换不来任何能力。
 *
 * <p>密钥经 {@code CryptoService}（APP_SECRET_KEY）加密入库，API 仅返回掩码；任何响应、日志、
 * 异常消息都不得出现明文或密文 key（沿用档案页的掩码规则，见
 * {@link LlmProviderService#maskApiKey(String)}）。
 */
@Data
@SmartMeta
@TableName("llm_provider")
public class LlmProvider extends PO {
    @ID
    private Long id;
    /**
     * 供应商显示名，应用层唯一（{@code LlmProviderService.validateNameUnique}）。
     *
     * <p>声明 255 而非 100：与 {@code AgentDefinition.name} / {@code Skill.name} /
     * {@code WechatAccount.name} / {@code ScheduleTask.name} / {@code LlmProfile.name}
     * 共用 smart-mybatis 的字段名级列声明缓存（键是字段名单键），六处不一致时结果取决于初始化顺序，
     * 且同步会发 {@code MODIFY COLUMN} 把真实列改向赢家。实测那 5 张表都是 varchar(255)，
     * 统一成 255 即与真实列宽一致，不发 DDL。
     */
    @TableField(length = 255)
    private String name;
    /**
     * API 协议：{@code OPENAI_COMPATIBLE} / {@code OPENAI_RESPONSES} / {@code ANTHROPIC}。
     *
     * <p>声明 50（与 {@code LlmConfig.provider} / {@code RenderConfig.provider} /
     * {@code LlmProfile.provider} 一致）：四处同名，声明文本必须逐字相同，否则列声明缓存会在四处间二选一。
     * 实测四张表都是 varchar(50)，与声明同向。
     */
    @TableField(length = 50)
    private String provider;
    /** 上游端点基址（已归一化：去尾斜杠并剥离 /v1/chat/completions 等后缀）。声明 500，与 {@code LlmConfig} / {@code RenderConfig} / {@code LlmProfile} 三处同名同宽。 */
    @TableField(length = 500)
    private String baseUrl;
    /** 加密后的 API Key。声明 65535（TEXT），与 {@code LlmConfig} / {@code LlmProfile} 两处同名同宽：AES-GCM 密文是 Base64，长度随明文线性增长，窄列会在某天写不进去。 */
    @TableField(length = 65535)
    private String apiKeyEncrypted;
    /**
     * 启用开关。
     *
     * <p>不加 {@code @TableField}：同名字段 {@code LlmConfig.enabled} / {@code LlmProfile.enabled}
     * 也不加注解（Boolean → tinyint，没有「列宽」这个概念），三处声明文本必须逐字一致
     * （由 {@code EntityColumnDeclarationConsistencyTest} 钉住）。停用供应商 ⇒ 其下全部模型
     * 在故障切换链上消失（{@link LlmProfileService#runtime} 把 {@code enabled} 与供应商的状态相与）。
     */
    private Boolean enabled;
    /**
     * 注意：库里还留着一列 {@code LLM_PROVIDER.PURPOSE}，但**实体不再映射它**。
     *
     * <p>它曾用来给供应商打「文本 / 图片」用途标签（契约 §4.3 的旧设计）。用户看图后否决：
     * 「供应商会同时供应所有类型模型」——一个网关通常同时提供 {@code gpt-4o}（文本）与
     * {@code gpt-image-1}（图片），所以用途是**模型的属性**，不是供应商的。图片模型改由
     * {@code llm_profile.image_model_name} 声明（见 §4.3 的新两跳口径）。
     *
     * <p>为什么表列不跟着删：smart-mybatis 只做加法、不删列；而在生产库上手工 {@code DROP COLUMN}
     * 是有风险的操作（老版本回滚时会缺列）。字段从实体移除后该列成为**无害孤儿**——
     * 既不会被读写，也不会因为 NOT NULL 约束咬人（它本来就可空）。新增环境不会建出这一列，
     * 因此任何 SQL 都不得依赖它存在。
     */
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
