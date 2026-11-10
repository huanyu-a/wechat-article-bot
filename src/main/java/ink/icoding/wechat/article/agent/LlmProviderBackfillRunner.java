package ink.icoding.wechat.article.agent;

import ink.icoding.wechat.article.common.CryptoService;
import ink.icoding.wechat.article.settings.LlmConfig;
import ink.icoding.wechat.article.settings.LlmConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 启动回填：把存量「{@code provider_id} 为空」的模型档案按三元组归组，收敛成供应商并回填外键
 * （契约 §5.4）。这就是存量库里 8 条 nexus 档案收敛成 1 个供应商的那一步。
 *
 * <p><b>为什么需要它</b>：改造前每条档案各自持有端点与密钥，因此存量库里有 N 条「同一个网关、同一个 key、
 * 只有模型名不同」的档案。改造后档案只声明「哪个模型」，端点在供应商上；存量行必须被挂到供应商上，
 * 否则它们永远走「档案自身旧列」的回退路径——供应商页面上看不到它们，用户改了供应商的 key 也影响不到它们。
 *
 * <p><b>为什么归组键是</b> {@code (provider, 归一化 baseUrl, 解密后的明文 key)}：{@code CryptoService}
 * 用 AES-GCM + 每次随机 IV，**同一明文加密两次得到不同密文**（实测 3 条同 key 档案的密文 MD5 三个都不同），
 * 所以按密文归组会把每条档案分成一个供应商——那不是收敛，只是把重复换了个地方。明文只在内存里比较，
 * 不写日志、不进异常、不落库（密文保持原样直接复用）。
 *
 * <p><b>顺序</b>：{@code @Order(21)} 紧跟在 {@link LlmProfileMigrationRunner}（20）之后、且在
 * {@link LlmProfileSeeder}（25）之前。这个次序是硬要求：种子档案从来源档案**复制 {@code providerId}**，
 * 回填若晚于种子，种子建出来的档案会一直是 {@code provider_id = null}（永远走回退路径、
 * 供应商页面看不见它们）。因此这里只处理「本次启动能看到的空 provider_id 行」，不依赖任何标记表；
 * 幂等性由输入条件本身保证——第二次启动这些行已经有 {@code provider_id}，无事可做。
 *
 * <p>约定同其它 Runner：只 {@code log.warn}、不抛、不阻塞启动；单组失败只跳过该组，
 * 不连累其它组（回填是「能救多少救多少」，不是全有全无）。
 */
@Component
@Order(21)
public class LlmProviderBackfillRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LlmProviderBackfillRunner.class);

    private final LlmProfileMapper profileMapper;
    private final LlmConfigMapper llmConfigMapper;
    private final LlmProviderService providerService;
    private final CryptoService cryptoService;

    public LlmProviderBackfillRunner(LlmProfileMapper profileMapper, LlmConfigMapper llmConfigMapper,
                                     LlmProviderService providerService, CryptoService cryptoService) {
        this.profileMapper = profileMapper;
        this.llmConfigMapper = llmConfigMapper;
        this.providerService = providerService;
        this.cryptoService = cryptoService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            backfill();
        } catch (Exception exception) {
            log.warn("存量模型档案的供应商回填失败（跳过，不影响启动）", exception);
        }
    }

    void backfill() {
        List<LlmProfile> pending = profileMapper.findWithoutProvider();
        if (pending.isEmpty()) {
            // 明确说「无待处理行」：与「全部被跳过」共用同一句汇总时，管理员无法区分二者（缺陷 B4）
            log.info("存量模型档案供应商回填：无待处理行（所有档案的 provider_id 都已填）");
            return;
        }

        GroupResult grouped = group(pending);
        List<KeyGroup> groups = grouped.groups();
        int skippedProfiles = grouped.skipped();
        // llm_config 当前行只解析一次（三元组与它一致的那一组取名为「默认供应商」，
        // 契约 §5.4 规则 3）：既避免每组重复解密，也避免一次解析失败把某一组带走。
        LlmConfig config = llmConfigMapper.current();
        String configBaseUrl = null;
        String configKeyPlain = null;
        if (config != null) {
            try {
                configBaseUrl = config.getBaseUrl() == null || config.getBaseUrl().isBlank()
                        ? null : LlmProfileService.normalizeBaseUrl(config.getBaseUrl());
                configKeyPlain = config.getApiKeyEncrypted() == null
                        ? null : cryptoService.decrypt(config.getApiKeyEncrypted());
            } catch (RuntimeException exception) {
                // 解析不出来就当作「没有可比的配置行」：宁可取 host 名新建一个供应商，
                // 也不能靠猜把某一组认定成设置页那一行（那会改写设置页的端点与密钥）
                log.warn("llm_config 当前行的端点或密钥无法解析，本次回填不认定「默认供应商」", exception);
                config = null;
            }
        }

        int attachedProfiles = 0;
        int attachedGroups = 0;
        int failedGroups = 0;
        for (KeyGroup group : groups) {
            try {
                Long providerId = resolveProviderId(group, config, configBaseUrl, configKeyPlain);
                for (LlmProfile profile : group.profiles) {
                    profile.setProviderId(providerId);
                    profile.setUpdatedAt(LocalDateTime.now());
                    profileMapper.updateById(profile);
                    attachedProfiles++;
                }
                attachedGroups++;
            } catch (Exception exception) {
                failedGroups++;
                log.warn("存量档案的供应商回填失败（跳过该组 {} 条档案，不影响启动）",
                        group.profiles.size(), exception);
            }
        }
        log.info("存量模型档案供应商回填完成：{} 条档案归入 {} 个供应商（失败 {} 组，未能归组 {} 条），"
                        + "本次看到 {} 条待回填档案",
                attachedProfiles, attachedGroups, failedGroups, skippedProfiles, pending.size());
        if (skippedProfiles > 0) {
            // 缺陷 B4：这类行**永远不会**被回填，直到密钥能解开。原来的汇总只说「0 条归组 / 0 个供应商」，
            // 读起来像「没有需要回填的行」，而这个 WARN 才是管理员需要看到的动作提示。
            log.warn("有 {} 条模型档案因端点或密钥无法解密被跳过回填，请检查 APP_SECRET_KEY 是否变更过；"
                    + "它们的 provider_id 仍为空，修好后下次启动会自动重试", skippedProfiles);
        }
    }

    /**
     * 归组：同三元组的档案进同一组（同组只建一个供应商）。
     *
     * <p>线性查找而不是 {@code HashMap}：组数就是供应商数（个位到十位），
     * 而用 Map 就得让**明文 key** 参与 hashCode/equals——那意味着一个可以被打进日志的键类型，
     * 代价与收益不成比例。
     *
     * <p>逐条容错：baseUrl 为空/不能归一化、密钥解不开的档案**留在原地**（{@code provider_id} 仍为空）
     * 并告警，而不是中断整轮回填。这类行下次启动还会被看到，人工修好之后自动补上。
     * 注意「解不开」与「没有 key」必须区分：前者跳过（否则它会带着一段解不开的密文去建供应商），
     * 后者按「无 key」归组（契约修正明确要求）。
     */
    private GroupResult group(List<LlmProfile> pending) {
        List<KeyGroup> groups = new ArrayList<>();
        int skipped = 0;
        for (LlmProfile profile : pending) {
            if (profile.getBaseUrl() == null || profile.getBaseUrl().isBlank()) {
                // 造不出供应商（没有端点）：留着走档案自身旧列的回退路径，与改造前一致
                skipped++;
                continue;
            }
            String baseUrl;
            String plainKey;
            try {
                baseUrl = LlmProfileService.normalizeBaseUrl(profile.getBaseUrl());
                plainKey = profile.getApiKeyEncrypted() == null
                        ? null : cryptoService.decrypt(profile.getApiKeyEncrypted());
            } catch (RuntimeException exception) {
                log.warn("档案「{}」(id={}) 的端点或密钥无法用于归组（跳过该档案）",
                        profile.getName(), profile.getId(), exception);
                skipped++;
                continue;
            }
            KeyGroup group = null;
            for (KeyGroup candidate : groups) {
                if (candidate.matches(profile.getProvider(), baseUrl, plainKey)) {
                    group = candidate;
                    break;
                }
            }
            if (group == null) {
                group = new KeyGroup(profile.getProvider(), baseUrl, plainKey, profile.getApiKeyEncrypted());
                groups.add(group);
            }
            group.profiles.add(profile);
        }
        return new GroupResult(groups, skipped);
    }

    /**
     * 归组结果：能归的组 + 被跳过的档案数。
     *
     * <p>为什么要带计数（缺陷 B4）：只报「0 条归组 / 0 个供应商」时，管理员读到的结论是
     * 「没有需要回填的行」，而真实情况可能是「全部失败」。这两个状态需要完全不同的动作，
     * 所以必须能在日志里分开。
     */
    private record GroupResult(List<KeyGroup> groups, int skipped) {
    }

    /**
     * 该组挂到哪个供应商上。
     *
     * <p>三元组与 {@code llm_config} 当前行一致时走「默认供应商」那条 upsert（与设置页写透同一个方法，
     * 保证两处对「默认供应商」的认定不会漂移）——因为这一组的端点与密钥就是设置页显示的端点与密钥，
     * 让设置页与供应商页指向同一行是唯一自洽的结果。其余组按 baseUrl 的 host 新建，绝不改写别的供应商。
     */
    private Long resolveProviderId(KeyGroup group, LlmConfig config, String configBaseUrl, String configKeyPlain) {
        boolean matchesConfig = config != null
                && Objects.equals(config.getProvider(), group.provider)
                && Objects.equals(configBaseUrl, group.baseUrl)
                && Objects.equals(configKeyPlain, group.apiKeyPlain);
        if (matchesConfig) {
            // 缺陷 B1：这里**不许**传 config.getEnabled()。llm_config.enabled 表达「整站关掉 AI」，
            // 让它决定供应商的启用位，会让「AI 关着的库」里回填出来的供应商全是停用，
            // 那些档案因此在故障切换链上整组静默消失。回填口径只有一个：有 key 即启用。
            return providerService.resolveDefaultProviderIdForBackfill(group.provider, group.baseUrl,
                    group.apiKeyEncrypted);
        }
        return providerService.attachOrCreateProvider(group.provider, group.baseUrl, group.apiKeyEncrypted,
                LlmProviderService.nameFromBaseUrl(group.baseUrl));
    }

    /**
     * 一个回填组：同一 {@code (provider, 归一化 baseUrl, 明文 key)} 的档案集合。
     *
     * <p><b>为什么故意不是 record</b>：record 会自动生成 {@code toString()}，任何一次
     * {@code log.info("...{}", group)}、或异常链里带上它，都会把**明文密钥**写进日志；
     * 普通类靠 {@code Object.toString()}（类名 + 哈希）天然兜住这条约定。字段也不必做成不可变集合
     * ——它只活在本次 {@code run()} 的栈上，比完即弃。
     */
    private static final class KeyGroup {
        private final String provider;
        private final String baseUrl;
        /** 明文 key：仅用于内存内相等比较。不得出现在日志、异常消息、响应或新列里。 */
        private final String apiKeyPlain;
        /** 代表行的密文，直接交给新建的供应商（GCM 密文自带 IV，解出来就是同一个明文）。 */
        private final String apiKeyEncrypted;
        private final List<LlmProfile> profiles = new ArrayList<>();

        private KeyGroup(String provider, String baseUrl, String apiKeyPlain, String apiKeyEncrypted) {
            this.provider = provider;
            this.baseUrl = baseUrl;
            this.apiKeyPlain = apiKeyPlain;
            this.apiKeyEncrypted = apiKeyEncrypted;
        }

        private boolean matches(String provider, String baseUrl, String apiKeyPlain) {
            return Objects.equals(this.provider, provider)
                    && Objects.equals(this.baseUrl, baseUrl)
                    && Objects.equals(this.apiKeyPlain, apiKeyPlain);
        }
    }
}
