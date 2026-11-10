package ink.icoding.wechat.article.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模型档案（llm_profile 表，skills-agent-plan 4.3）。
 * api_key 经 CryptoService（APP_SECRET_KEY）加密入库，API 仅返回掩码；
 * 全局唯一一条 is_default=true（应用层保证，LlmProfileService.setDefault 事务内重置）。
 */
@Data
@SmartMeta
@TableName("llm_profile")
public class LlmProfile extends PO {
    @ID
    private Long id;
    /**
     * 档案名称。声明 255 而非 100：与 {@code AgentDefinition.name} / {@code Skill.name} /
     * {@code WechatAccount.name} / {@code ScheduleTask.name} / {@code LlmProvider.name}
     * 共用 smart-mybatis 的字段名级列声明缓存（键是字段名单键），六处不一致时结果取决于初始化顺序，
     * 且同步会发 {@code MODIFY COLUMN} 把真实列改向赢家。实测 6 张表都是 varchar(255)，
     * 统一成 255 即与真实列宽一致，不发 DDL。
     */
    @TableField(length = 255)
    private String name;
    /**
     * 所属供应商（{@code llm_provider.id}）：端点与密钥的唯一归属地（llm-provider-refactor §3.2）。
     *
     * <p><b>可空</b>，且这条不是「暂时容忍脏数据」而是三种合法状态之一：
     * <ol>
     *   <li>存量尚未回填的行——启动回填器（{@code LlmProviderBackfillRunner}）挂供应商之前就是空；</li>
     *   <li>真正没有供应商可挂的存量行（例如 {@code base_url} 为空，造不出供应商）——它们永远走
     *       {@code provider/baseUrl/apiKeyEncrypted} 三列的旧口径，与改造前逐字一致；</li>
     *   <li>回滚路径——万一要退回改造前的版本，清掉这一列即可恢复旧口径，不必动数据。</li>
     * </ol>
     * 因此本列既不能 NOT NULL，也不能在读取时假定非空（{@code LlmProfileService.runtime} 有显式分支）。
     *
     * <p>不加 {@code @TableField}：{@code Long} 映射为 bigint，没有「列宽」这个概念
     * （同 {@code createdBy} 等既有 Long 字段的写法）。字段名 {@code providerId} 在本项目里只此一处，
     * 不存在列声明缓存跨实体共享的问题（{@code EntityColumnDeclarationConsistencyTest} 会继续替我们看着）。
     */
    private Long providerId;
    /**
     * 上游供应商。声明 50（与 {@code LlmConfig.provider} / {@code RenderConfig.provider} /
     * {@code LlmProvider.provider} 一致）：四处同名，声明文本必须逐字相同，否则列声明缓存会在四处间二选一。
     * 实测四张表都是 varchar(50)，与声明同向。
     *
     * <p><b>改造后本列冻结为存量快照</b>（契约 §3.2）：新口径只读 {@link #providerId}，本列仅在
     * {@code providerId} 为空时参与运行时解析。之所以不删也不清空：删字段会让 smart-mybatis 的列同步
     * 行为变得不可预测，清空则失去代码回滚时的端点。
     */
    @TableField(length = 50)
    private String provider;
    /** 上游端点基址；改造后**冻结为存量快照**（同 {@link #provider}），仅 {@code providerId} 为空时参与运行时解析。声明 500，与 {@code LlmConfig} / {@code RenderConfig} / {@code LlmProvider} 三处同名同宽（连同本处共四处，实测都是 varchar(500)）。 */
    @TableField(length = 500)
    private String baseUrl;
    /**
     * 上游模型名。声明 200（与 {@code LlmConfig.modelName} 一致）：两处同名，
     * 实测 LLM_CONFIG 与 LLM_PROFILE 的 MODEL_NAME 都是 varchar(200)。
     * {@code LlmConfig} 那处此前靠默认值 255，与本类不一致——不一致即意味着
     * 同步逻辑会按赢家对另一张表发 {@code MODIFY COLUMN}。
     */
    @TableField(length = 200)
    private String modelName;
    /**
     * 图片模型名（可空）：为空表示该档案不指定图片模型，配图回落全局设置
     * （{@code LLM_CONFIG.IMAGE_MODEL_NAME}）。
     *
     * <p>为什么只加模型名、不加配套的 baseUrl / apiKey：图片三件套里**只有模型名**是
     * 「换个模型」这一诉求的载体。端点与密钥沿用全局设置（同一网关、同一个 key），
     * 多带两列只会扩大迁移面，换不来任何能力。
     *
     * <p>为什么**不加** {@code @TableField}：同名字段 {@code LlmConfig.imageModelName}
     * 也不加注解，而 smart-mybatis 的列声明缓存按**字段名**单键共享——两处声明文本不一致时
     * 结果取决于实体初始化顺序，且同步会发 {@code MODIFY COLUMN} 把真实列改向赢家。
     * 实测 {@code LLM_CONFIG.IMAGE_MODEL_NAME} 与 smart-mybatis 默认值同为 {@code varchar(255)}，
     * 故「不加注解」既与真实列宽同向、也与另一处声明逐字一致。
     * （由 {@code EntityColumnDeclarationConsistencyTest} 钉住。）
     */
    private String imageModelName;
    /**
     * 这张档案**本身就是图片模型**（契约 §4.3 定稿：模型卡片上的复选框「这是图片模型」）。
     *
     * <p>为什么它是**档案**的属性而不是供应商的，也不是「另填一个图片模型名」：用户两次否决了前两版设计。
     * 第一版给供应商打 {@code purpose=TEXT|IMAGE} —— 被否，理由是「供应商会同时供应所有类型模型」
     * （一个网关同时提供 {@code gpt-4o} 与 {@code gpt-image-1}）。第二版在卡片上填「图片模型名称」
     * 文本框 —— 被否，理由是「不要这种，采用复选框」。定稿就是这张复选框：勾上即「这条档案是图片模型」，
     * 图片模型名**就是它的 {@code modelName}**，端点与密钥来自它所属的供应商。
     *
     * <p>标准配法因此是一个供应商下建两条档案：{@code deepseek-chat}（不勾，写正文）与
     * {@code gpt-image-1}（勾上，供配图），两条共用供应商那一份端点与密钥。
     *
     * <p>为什么可空：存量库由 smart-mybatis 补列，老行只能是 {@code NULL}。语义上
     * {@code NULL} 等于「不是图片模型」，所以**不需要迁移脚本**——读取一律用
     * {@code Boolean.TRUE.equals(...)}，绝不给字段赋默认值再落库。
     *
     * <p>为什么加 {@code @JsonProperty("isImageModel")}：与 {@link #isDefault} / {@link #isFallback}
     * 同一套写法。{@code Boolean} 包装类 + 显式名字，避免 {@code is} 前缀 getter 被 Jackson 剥成
     * {@code imageModel}——前端读不到就永远显示未勾选，而库里其实勾着。
     */
    @JsonProperty("isImageModel")
    private Boolean isImageModel;
    /**
     * 加密后的 API Key；改造后**冻结为存量快照**（同 {@link #provider}）。
     *
     * <p>为什么不跟着供应商一起更新这一列：那样会留下一份**旧 key 快照**——用户改完供应商的 key 之后，
     * 档案里还存着上一把，将来清空 {@code provider_id}（或回滚到改造前版本）时旧 key 会静默复活。
     * 停在这里 = 冻结，正是想要的语义。
     */
    @TableField(length = 65535)
    private String apiKeyEncrypted;
    private BigDecimal temperature;
    private Integer maxTokens;
    private Boolean enabled;
    /** 序列化为 isDefault（Boolean 包装类型避免 is 前缀 getter 歧义）。 */
    @JsonProperty("isDefault")
    private Boolean isDefault;
    /**
     * 兜底档案：主用档案全部不可用时的最后安全网（全局唯一，应用层保证，同 {@link #isDefault}）。
     *
     * <p>与默认档案的区别：默认档案是「没绑定档案的智能体用它」，兜底档案是
     * 「绑定档案和默认档案都失败了才用它」。因此兜底档案应当选**最稳、最便宜**的通道
     * （当前是免费的 hy4-preview），而不是最快的。
     */
    @JsonProperty("isFallback")
    private Boolean isFallback;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
