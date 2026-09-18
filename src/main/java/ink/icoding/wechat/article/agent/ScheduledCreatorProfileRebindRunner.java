package ink.icoding.wechat.article.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 一次性迁移：把「定时创作智能体」（{@code builtin_scheduled_creator}）的默认绑定
 * 从 {@code glm-5.3-flash} 改绑到 {@code deepseek-flash}。
 *
 * <p><b>为什么需要单独一个 Runner，而不是改 {@link AgentSeeder} 的种子就够了</b>：
 * {@code AgentSeeder.upsert} 只在 {@code llm_profile_id} **为空时**写入默认绑定
 * （用户换过的模型不能被种子改回去，见 {@code AgentSeederTest.existingProfileBindingIsNeverOverwritten}）。
 * 而存量库里这个字段早已是 glm 的 id，所以**只改种子对已有部署完全无效**——
 * 改的是「新装默认值」，不是「当前生效值」。这里把「存量改绑」单独做成一次性迁移。
 *
 * <p><b>为什么可以安全地覆盖</b>：只在当前绑定**恰好等于被取代的旧种子默认值**
 * （{@code glm-5.3-flash} 这个档案的 id）时才动手。也就是说，只有在
 * 「这一行从未被用户改过、还是种子当初写进去的那个值」时才改绑；
 * 用户一旦换成了任何别的档案，绑定值就不等于旧默认值，迁移直接跳过——
 * {@code AgentSeeder} 的「不覆盖用户选择」策略在这里同样成立。
 *
 * <p><b>幂等性</b>：首次改绑后绑定值变成 {@code deepseek-flash} 的 id，不再等于旧默认值，
 * 因此第二次启动必然跳过；无需额外的迁移标记表。
 *
 * <p>为什么定时创作智能体该用快档：它的负载是**生成工具参数**（{@code save_article_draft}
 * 的 {@code content} 就是整篇文章），实测 deepseek-flash 273 字符/秒、glm-5.3-flash 仅 69~125、
 * 基线 hy4-preview 77~95（{@code docs/dev/scheduled-task-reliability-round.md} §4.1）。
 * 分配给它的理由已写在 {@link AgentSeeder} 的 {@code seeds()} 上。
 *
 * <p>约定同其它迁移 Runner：只 WARN、不阻塞启动（{@link LlmProfileMigrationRunner} 同款）。
 */
@Component
@Order(32)
public class ScheduledCreatorProfileRebindRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ScheduledCreatorProfileRebindRunner.class);

    /** 被取代的旧种子默认值——只有绑定恰好等于它时才认为是「种子写的、用户没动过」。 */
    static final String SUPERSEDED_PROFILE_NAME = "glm-5.3-flash";

    /** 新的种子默认值：唯一被实测证明在「生成工具参数」场景下真正快的档。 */
    static final String TARGET_PROFILE_NAME = "deepseek-flash";

    /** 目标智能体：SINGLE 链路的执行者，负载几乎全是工具参数生成。 */
    static final String AGENT_BUILTIN_KEY = "builtin_scheduled_creator";

    private final AgentDefinitionMapper agentMapper;
    private final LlmProfileMapper profileMapper;

    public ScheduledCreatorProfileRebindRunner(AgentDefinitionMapper agentMapper, LlmProfileMapper profileMapper) {
        this.agentMapper = agentMapper;
        this.profileMapper = profileMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            rebind();
        } catch (Exception exception) {
            log.warn("定时创作智能体模型档案改绑失败（跳过，不影响启动）", exception);
        }
    }

    private void rebind() {
        AgentDefinition agent = agentMapper.findByBuiltinKey(AGENT_BUILTIN_KEY);
        if (agent == null) return; // 种子还没建出来（或已被删除），下次启动再说
        Long supersededId = enabledProfileId(SUPERSEDED_PROFILE_NAME);
        Long targetId = enabledProfileId(TARGET_PROFILE_NAME);
        if (!shouldRebind(agent.getLlmProfileId(), supersededId, targetId)) {
            log.debug("定时创作智能体无需改绑（当前绑定 {}，旧默认 {}，目标 {}）",
                    agent.getLlmProfileId(), supersededId, targetId);
            return;
        }
        Long previous = agent.getLlmProfileId();
        agent.setLlmProfileId(targetId);
        agent.setUpdatedAt(LocalDateTime.now());
        agentMapper.updateById(agent);
        log.info("已把定时创作智能体「{}」的模型档案从 {}（{}）改绑为 {}（{}）："
                        + "该链路负载是生成工具参数，实测快档快约 3 倍",
                agent.getName(), previous, SUPERSEDED_PROFILE_NAME, targetId, TARGET_PROFILE_NAME);
    }

    /**
     * 是否改绑：**只在当前绑定恰好等于旧种子默认值**时为真。
     *
     * @param currentBinding 当前 {@code llm_profile_id}
     * @param supersededId   旧种子默认档案（{@code glm-5.3-flash}）的 id，缺失/被禁用时为 null
     * @param targetId       目标档案（{@code deepseek-flash}）的 id，缺失/被禁用时为 null
     */
    static boolean shouldRebind(Long currentBinding, Long supersededId, Long targetId) {
        // 绑定为空：交给 AgentSeeder 按新种子补默认值，这里不插手
        if (currentBinding == null) return false;
        // 旧档案已不存在 → 无法判断「是不是种子写的」，宁可不改
        if (supersededId == null) return false;
        // 目标档案不可用 → 改了反而让绑定悬空
        if (targetId == null) return false;
        return currentBinding.equals(supersededId);
    }

    /** 档案名 → 已启用档案的 id；不存在或已禁用返回 null。 */
    private Long enabledProfileId(String profileName) {
        LlmProfile profile = profileMapper.findByName(profileName);
        if (profile == null || !Boolean.TRUE.equals(profile.getEnabled())) return null;
        return profile.getId();
    }
}
