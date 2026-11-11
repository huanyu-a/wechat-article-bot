package ink.icoding.wechat.article.assistant;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 全局助手的用户偏好（一份/用户）。目前只有「用哪个模型档案」一项。
 *
 * <p><b>为什么不塞进 {@code assistant_session}</b>：那张表的语义是「可被 reset 清空的对话上下文」，
 * 而 reset 的用途恰恰是「开个新话题」——把模型选择放进同一行，用户点「新对话」就会把模型选择一起
 * 冲掉，这显然是错的。两者的生命周期不同，就必须分表。
 *
 * <p><b>为什么不直接写 {@code agent_definition.llm_profile_id}</b>：那是**全局**配置（所有用户共用
 * 一个助手定义），一个人换了模型会改掉所有人的。偏好按 {@code user_id} 存，才是「我的助手用我的模型」。
 */
@Data
@SmartMeta
@TableName("assistant_preference")
public class AssistantPreference extends PO {
    @ID
    private Long id;
    private Long userId;
    /** 选中的模型档案 id；为空表示「没选过」，按定义绑定 → 默认 → 兜底的链条自动挑。 */
    private Long profileId;
    private LocalDateTime updatedAt;
}