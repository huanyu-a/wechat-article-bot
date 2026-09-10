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
 */
@Component
@Order(20)
public class LlmProfileMigrationRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LlmProfileMigrationRunner.class);

    private final LlmProfileMapper profileMapper;
    private final LlmConfigMapper llmConfigMapper;

    public LlmProfileMigrationRunner(LlmProfileMapper profileMapper, LlmConfigMapper llmConfigMapper) {
        this.profileMapper = profileMapper;
        this.llmConfigMapper = llmConfigMapper;
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
            LocalDateTime now = LocalDateTime.now();
            profile.setCreatedAt(now);
            profile.setUpdatedAt(now);
            profileMapper.insert(profile);
            log.info("已把存量 llm_config 迁移为默认模型档案（id={}）", profile.getId());
        } catch (Exception exception) {
            log.warn("llm_config → 默认模型档案迁移失败（跳过，不影响启动）", exception);
        }
    }
}
