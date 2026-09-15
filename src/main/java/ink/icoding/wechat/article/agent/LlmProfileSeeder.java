package ink.icoding.wechat.article.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 标准模型档案种子（Phase 2）：按**名称**幂等创建主用/兜底档案，并把默认档案标记为兜底。
 *
 * <p>为什么需要它：「按智能体分配模型」（{@link AgentDefinition#getLlmProfileId()}）与故障切换
 * （{@link LlmProfileService#failoverChain}）都要求档案**存在且可用**，但开箱只有一条「默认配置」，
 * 于是 7 个内置智能体全挤在同一个模型上，档案链也只有一环——主用一挂就整轮失败。
 *
 * <p>为什么 baseUrl/apiKey 从默认档案**复制**而不是让用户填：三个标准档案走的是同一个网关
 * （nexus.bx9y.com.cn）、同一个 key，**只有模型名不同**。让用户为每个模型再填一遍 key
 * 既冗余又容易填错；复制默认档案后用户随时可在设置页单独改。
 *
 * <p>为什么只补不覆盖：用户可能已经手工调过某个档案的 key 或关掉了它，
 * 种子只负责「让它存在」，不做任何覆盖（与 {@code AgentSeeder} 的 persona 策略一致）。
 * 默认档案在本 key 下是 hy4-preview（免费、慢），它同时是最好的兜底候选，因此未设置兜底时
 * 自动把它标为兜底——这是**唯一**一处种子替用户做的决定，且只做一次（已有兜底就不动）。
 */
@Component
@Order(25)
public class LlmProfileSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LlmProfileSeeder.class);

    /** 标准档案：名称即幂等键，模型名是唯一实质差异。 */
    record Seed(String name, String modelName, boolean fallback) {
    }

    /**
     * 标准档案清单（模型可用性已由 Phase 0 实测确认，见 {@code docs/dev/model-availability-probe.md}）。
     *
     * <p>两个主用档案分别覆盖「工具密集、速度优先」与「成稿/判断、能力优先」两类负载；
     * 兜底取默认档案（hy4-preview）——它免费、能力 57，是最后的安全网。
     *
     * <p>有意不建 {@code glm-5.3}：实测返回 503（无可用渠道），建了也只会占档案链一格、
     * 白耗一次切换。同理排除 {@code dots3-note-prev}（多轮记忆失败）、
     * {@code sensenova-6.8-flash-lite}（不稳定）、{@code step-router-v1}（输出异常少）。
     */
    static List<Seed> seeds() {
        return List.of(
                new Seed("deepseek-flash", "deepseek-flash", false),
                new Seed("glm-5.3-flash", "glm-5.3-flash", false));
    }

    private final LlmProfileMapper mapper;

    public LlmProfileSeeder(LlmProfileMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            LlmProfile source = defaultSource();
            if (source == null) {
                // 无默认档案（全新库、llm_config 也空）：不创建——没有 baseUrl/key 的档案是废档案，
                // 用户配好默认档案后下次启动会自动补上。
                log.debug("尚无默认模型档案，跳过标准档案种子");
                return;
            }
            for (Seed seed : seeds()) {
                try {
                    createIfAbsent(seed, source);
                } catch (Exception exception) {
                    log.warn("标准模型档案 {} 创建失败（跳过，不影响启动）", seed.name(), exception);
                }
            }
            markFallbackIfUnset(source);
        } catch (Exception exception) {
            log.warn("标准模型档案种子执行失败（跳过，不影响启动）", exception);
        }
    }

    /**
     * 复制来源：优先默认档案，其次第一条。
     *
     * <p>不直接用 {@code defaultProfile()} 的回落语义：它会退回「第一条」，
     * 而这里需要的是「一条**有 key** 的档案」——库里若存在无 key 的空档案，
     * 拿它当来源会复制出一个同样不可用的档案。
     */
    private LlmProfile defaultSource() {
        LlmProfile preferred = mapper.findDefault();
        if (hasKey(preferred)) return preferred;
        for (LlmProfile profile : mapper.findAll()) {
            if (hasKey(profile)) return profile;
        }
        return null;
    }

    private static boolean hasKey(LlmProfile profile) {
        return profile != null && profile.getApiKeyEncrypted() != null
                && !profile.getApiKeyEncrypted().isBlank();
    }

    /** 不存在才创建（按名称判重）；已存在一律不动，保留用户对 key/启用状态的修改。 */
    private void createIfAbsent(Seed seed, LlmProfile source) {
        if (mapper.findByName(seed.name()) != null) return;
        LlmProfile profile = new LlmProfile();
        profile.setName(seed.name());
        profile.setProvider(source.getProvider());
        profile.setBaseUrl(source.getBaseUrl());
        profile.setModelName(seed.modelName());
        profile.setApiKeyEncrypted(source.getApiKeyEncrypted());
        profile.setTemperature(source.getTemperature());
        profile.setMaxTokens(source.getMaxTokens());
        profile.setEnabled(true);
        profile.setIsDefault(false);
        profile.setIsFallback(seed.fallback());
        profile.setCreatedBy(source.getCreatedBy());
        LocalDateTime now = LocalDateTime.now();
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        mapper.insert(profile);
        log.info("已创建标准模型档案「{}」（模型 {}，id={}）", seed.name(), seed.modelName(), profile.getId());
    }

    /**
     * 未设置兜底档案时，把默认档案标为兜底。
     *
     * <p>为什么用默认档案而不是新建一条：默认档案在本部署下是 hy4-preview（免费、能力 57），
     * 天然就是「最稳最便宜」的兜底候选；新建一条同样的档案只会让档案列表多一条重复项。
     * 只在**完全没有兜底**时动手，用户手工指定过就尊重用户的选择。
     */
    private void markFallbackIfUnset(LlmProfile source) {
        if (mapper.findFallback() != null) return;
        source.setIsFallback(true);
        source.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(source);
        log.info("已把默认模型档案「{}」标记为兜底档案（档案链的最后一段）", source.getName());
    }
}
