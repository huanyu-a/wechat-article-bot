package ink.icoding.wechat.article.agent;

import ink.icoding.wechat.article.settings.LlmConfig;
import ink.icoding.wechat.article.settings.LlmConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 存量迁移（skills-agent-plan 4.3 / 5.9）：llm_profile 表为空且 llm_config 有数据时，
 * 把 llm_config 那一行迁移为「默认配置」档案（is_default=true）。一次性、幂等。
 * 图片模型三件套继续留在 llm_config（方案推荐的改动最小方案）。
 *
 * <p>本轮（llm-provider-refactor §5.4）此处的唯一变化：建档案时**顺手把供应商也建出来**
 * （三元组与 llm_config 完全一致 ⇒ 复用/创建「默认供应商」那一行），让默认档案从第一刻起就挂靠在
 * 供应商上，而不是先造一条 {@code provider_id} 为空的档案、等下一个 Runner 去补。
 * 这与 {@link LlmProviderBackfillRunner}（{@code @Order(21)}）不冲突：回填只处理
 * {@code provider_id} 为空的行，这里已经填好的行它不会再动；两边对「默认供应商」用的是同一个
 * upsert（{@code LlmProviderService.resolveDefaultProviderId}），不会各自建出一个同名的。
 *
 * <p>拿不到供应商时不阻塞：供应商解析失败只记 warn，档案照建、{@code providerId} 留空
 * ——那条档案走「档案自身旧列」的回退口径（{@code baseUrl}/{@code apiKeyEncrypted} 就在同一行里，
 * 正是从 llm_config 复制的），运行时行为与改造前逐字相同；下次启动回填器再补。
 */
@Component
@Order(20)
public class LlmProfileMigrationRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LlmProfileMigrationRunner.class);

    private final LlmProfileMapper profileMapper;
    private final LlmConfigMapper llmConfigMapper;
    private final LlmProviderService providerService;

    public LlmProfileMigrationRunner(LlmProfileMapper profileMapper, LlmConfigMapper llmConfigMapper,
                                     LlmProviderService providerService) {
        this.profileMapper = profileMapper;
        this.llmConfigMapper = llmConfigMapper;
        this.providerService = providerService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (profileMapper.findFirst() != null) return; // 已有档案，无需迁移
            LlmConfig config = llmConfigMapper.current();
            if (config == null || config.getModelName() == null || config.getModelName().isBlank()) return;
            LlmProfile profile = new LlmProfile();
            profile.setName("默认配置");
            profile.setProvider(config.getProvider());
            profile.setBaseUrl(config.getBaseUrl());
            profile.setModelName(config.getModelName());
            profile.setApiKeyEncrypted(config.getApiKeyEncrypted());
            profile.setTemperature(config.getTemperature());
            profile.setMaxTokens(config.getMaxTokens());
            profile.setEnabled(config.getEnabled());
            profile.setIsDefault(true);
            profile.setProviderId(resolveProviderId(config));
            LocalDateTime now = LocalDateTime.now();
            profile.setCreatedAt(now);
            profile.setUpdatedAt(now);
            profileMapper.insert(profile);
            log.info("已把存量 llm_config 迁移为默认模型档案（id={}，供应商 {}）",
                    profile.getId(), profile.getProviderId());
        } catch (Exception exception) {
            log.warn("llm_config → 默认模型档案迁移失败（跳过，不影响启动）", exception);
        }
    }

    /** 供应商 id；端点为空/不能归一化/解析失败一律返回 null（不阻塞迁移，留给回填器与回退口径）。 */
    private Long resolveProviderId(LlmConfig config) {
        if (config.getBaseUrl() == null || config.getBaseUrl().isBlank()) return null;
        try {
            return providerService.resolveDefaultProviderId(config.getProvider(),
                    LlmProfileService.normalizeBaseUrl(config.getBaseUrl()), config.getApiKeyEncrypted(),
                    config.getEnabled());
        } catch (Exception exception) {
            log.warn("默认档案的供应商未能建立（档案照常创建，运行时走档案自身旧列）", exception);
            return null;
        }
    }
}
