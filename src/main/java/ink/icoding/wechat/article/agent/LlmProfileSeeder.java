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
 * <p>为什么端点与密钥不在这里各写一份（而是**复制来源档案的 {@code providerId}**）：
 * 这些档案走的是同一个网关（nexus.bx9y.com.cn）、同一个 key，**只有模型名不同**。让用户为每个模型
 * 再填一遍 key 既冗余又容易填错；改造后端点与密钥归供应商（{@code llm_provider}），档案只保留
 * 「挂哪个供应商」这一条引用——换一次 key 只改供应商一行，12 条档案同时生效。因此这里复制的是
 * {@code providerId}（引用），不再是 baseUrl/key（副本）。
 *
 * <p>为什么只补不覆盖：用户可能已经手工调过某个档案的 key 或关掉了它，
 * 种子只负责「让它存在」，不做任何覆盖（与 {@code AgentSeeder} 的 persona 策略一致）。
 * 默认档案由用户配置，种子只在**完全没有兜底**时把默认档案标为兜底——这是种子替用户做的
 * 唯一一个决定，且只做一次（已有兜底就不动）。
 */
@Component
@Order(25)
public class LlmProfileSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LlmProfileSeeder.class);

    /** 标准档案：名称即幂等键，模型名是唯一实质差异。 */
    record Seed(String name, String modelName, boolean fallback) {
    }

    /**
     * 标准档案清单，按《模型综合评测表》（2026-09-18）的档位取舍，
     * 并逐条经 2026-09-18 实测复核（{@code docs/dev/model-availability-probe.md}）。
     *
     * <p>为什么是这一组：评测表按「综合分 / 质量 / 成本 / 速度」给了档位，但**能建档案的前提是
     * 这个模型在当前网关上真的能出字**。两者取交集后：
     * <ul>
     *   <li>S 档（能力优先）：hy4-preview、kimi-k3、qwen3.8-max、glm-5.3</li>
     *   <li>A 档（主力）：deepseek-flash（综合分最高且最快）、glm-5.3-flash（性价比最高）、
     *       dots3-note-prev、qwen3.8-27b</li>
     *   <li>B 档（备选）：step-3.7-flash、agnes-2.5-flash、qwen3.8-flash、nemotron-3-ultra-free</li>
     * </ul>
     *
     * <p>有意**不建**以下模型，理由分两类：
     * <ol>
     *   <li>实测不可用：{@code deepseek-v4-pro-0813}（网关返回「模型未找到」）、
     *       {@code kimi-k2.8-preview}（temperature 只允许 1，其它值 400）。</li>
     *   <li>评测表判为弱档、入链只会拖长失败路径：{@code sensenova-6.8-flash-lite}（30.2）、
     *       {@code step-router-v1}、{@code agnes-3.0-flash}、{@code fast-model}、
     *       {@code deep-model}、{@code balanced-model}、{@code cn:auto}、
     *       {@code Atria-Dawn-Preview}。另有 {@code glm-4.7}/{@code union-alpha}/
     *       {@code laguna-s-2.1-free} 网关已不提供。</li>
     * </ol>
     *
     * <p>{@code nemotron-3.5-lightning-free} 虽在 B 档，但实测单次响应 **111 秒**——
     * 放进故障切换链意味着「主用挂了要再等两分钟」，故不建。
     *
     * <p>注意这些模型名是 nexus 网关的命名；换网关后可能整组失效。种子只负责让档案**存在**，
     * 失效档案的表现是「被调用时报模型未找到」，与用户手工建错档案的表现一致，不会静默出错。
     */
    static List<Seed> seeds() {
        return List.of(
                // A 档主力：AgentSeeder 按**名称**绑定，这两个名字不能改（改了两处会对不上）
                new Seed("deepseek-flash", "deepseek-flash", false),
                new Seed("glm-5.3-flash", "glm-5.3-flash", false),
                // S 档（能力优先）。hy4-preview 兼兜底：限时免费 + Tier S，是最后一道安全网的最优候选
                new Seed("hy4-preview", "hy4-preview", true),
                new Seed("kimi-k3", "kimi-k3", false),
                new Seed("qwen3.8-max", "qwen3.8-max", false),
                new Seed("glm-5.3", "glm-5.3", false),
                // A 档其余
                new Seed("dots3-note-prev", "dots3-note-prev", false),
                new Seed("qwen3.8-27b", "qwen3.8-27b", false),
                // B 档
                new Seed("step-3.7-flash", "step-3.7-flash", false),
                new Seed("agnes-2.5-flash", "agnes-2.5-flash", false),
                new Seed("qwen3.8-flash", "qwen3.8-flash", false),
                new Seed("nemotron-3-ultra-free", "nemotron-3-ultra-free", false));
    }

    private final LlmProfileMapper mapper;
    private final LlmProfileService profileService;

    public LlmProfileSeeder(LlmProfileMapper mapper, LlmProfileService profileService) {
        this.mapper = mapper;
        this.profileService = profileService;
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
     *
     * <p><b>「有 key」的判据必须走运行时口径</b>（{@link LlmProfileService#hasReachableKey}），
     * 不能只看档案自己那一列。改造后档案的密钥搬到了供应商上，而新 UI 建的档案
     * **不再写自己那一列**（{@code LlmProfileService.apply} 有意不写）——若判据仍只看旧列，
     * 用户改走新 UI 建默认档案之后，种子会认为「库里没有可用来源」而**静默停机**：
     * 症状是开箱只有一条默认档案、7 个内置智能体全挤在同一个模型上、故障切换链退回一环，
     * 而且不报任何错（种子本来就只 log.debug 后 return）。
     */
    private LlmProfile defaultSource() {
        LlmProfile preferred = mapper.findDefault();
        if (hasReachableKey(preferred)) return preferred;
        for (LlmProfile profile : mapper.findAll()) {
            if (hasReachableKey(profile)) return profile;
        }
        return null;
    }

    private boolean hasReachableKey(LlmProfile profile) {
        return profileService.hasReachableKey(profile);
    }

    /**
     * 不存在才创建（按名称判重）；已存在一律不动，保留用户对供应商/启用状态的修改。
     *
     * <p>复制的是来源档案的 {@code providerId}：种子档案与来源共用同一个供应商，
     * 于是「换一次 key 改一处」对种子建出来的 12 条档案同样成立。
     * 来源没有供应商（{@code providerId} 为空，例如回填还没跑）时留空——这些档案走档案自身旧列的
     * 回退口径，与改造前逐字一致，不会因为「没有供应商」而变得不可用。
     */
    private void createIfAbsent(Seed seed, LlmProfile source) {
        if (mapper.findByName(seed.name()) != null) return;
        LlmProfile profile = new LlmProfile();
        profile.setName(seed.name());
        profile.setProviderId(source.getProviderId());
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
     * 未设置兜底档案时，把**种子声明的兜底档案**（{@code hy4-preview}）标为兜底。
     *
     * <p>为什么要改（2026-09-18 实机复现）：原实现是「把默认档案标为兜底」。当时默认档案恰好是
     * {@code hy4-preview}（免费），这个近似成立。但默认档案是可以被用户改成主力的——本轮就把默认
     * 档案设成了 {@code deepseek-flash}。此时「把默认档案标为兜底」会把**主用**档案同时标成兜底，
     * 故障切换链的「默认」与「兜底」两段指向同一条档案，被 {@code failoverChain} 按 id 去重后
     * 白白少掉一跳——主用一挂，链上直接跳到其它档案，兜底那段形同虚设。
     *
     * <p>现在按**声明**取：优先 {@link Seed#fallback()} 为真的那条档案；只有当它不存在
     * （用户删了、或种子没建起来）才退回「标记默认档案」，保证链尾永远有东西可用。
     *
     * <p>只在**完全没有兜底**时动手，用户手工指定过就尊重用户的选择。
     */
    private void markFallbackIfUnset(LlmProfile source) {
        if (mapper.findFallback() != null) return;

        LlmProfile declared = seeds().stream()
                .filter(Seed::fallback)
                .map(seed -> mapper.findByName(seed.name()))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);

        LlmProfile target = declared != null ? declared : source;
        target.setIsFallback(true);
        target.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(target);
        log.info("已把{}档案「{}」标记为兜底档案（档案链的最后一段）",
                declared != null ? "声明的兜底" : "默认", target.getName());
    }
}
