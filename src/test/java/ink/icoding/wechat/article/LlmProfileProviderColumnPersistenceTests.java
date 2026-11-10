package ink.icoding.wechat.article;

import ink.icoding.wechat.article.agent.LlmProfile;
import ink.icoding.wechat.article.agent.LlmProfileMapper;
import ink.icoding.wechat.article.agent.LlmProfileSeeder;
import ink.icoding.wechat.article.agent.LlmProfileService;
import ink.icoding.wechat.article.agent.LlmProvider;
import ink.icoding.wechat.article.agent.LlmProviderBackfillRunner;
import ink.icoding.wechat.article.agent.LlmProviderMapper;
import ink.icoding.wechat.article.agent.LlmProviderService;
import ink.icoding.wechat.article.common.CryptoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 供应商外键（{@code LLM_PROFILE.PROVIDER_ID}）与供应商表（{@code LLM_PROVIDER}）的真实 MySQL 验证，
 * 全部走真实 {@link CryptoService}（不打桩），对应 llm-provider-refactor §7 的第 2、3、4 条验收。
 *
 * <p><b>为什么必须打真库</b>：
 * <ol>
 *   <li><b>列声明只对真实建表有效</b>。smart-mybatis 的列声明缓存**按字段名共享**，而本轮新增的
 *       {@code name} / {@code provider} / {@code baseUrl} / {@code apiKeyEncrypted} 四个字段在
 *       {@code LlmProvider} 与 {@code LlmProfile} / {@code LlmConfig} 里同名：一旦哪一处的
 *       {@code @TableField(length=)} 与其它处不一致，映射到的可能是另一方声明的宽度，
 *       表现为真实库里出现 {@code MODIFY COLUMN} 收紧既有列。这条只有真实列的
 *       {@code information_schema} 才能证伪（mock 与纯编译都看不见）。</li>
 *   <li><b>「同一明文 key 归到一个供应商」的真实性来自随机 IV</b>。本类用真实
 *       {@link CryptoService} 把同一明文加密 8 次，密文两两不同，再断言回填仍收敛成 1 个供应商
 *       ——这是本次改造在存量库上的核心动作（8 条 nexus 档案 → 1 个供应商）。</li>
 *   <li><b>升级窗口</b>：{@code provider_id} 为空的老行必须继续用自己那两列跑（真实解密路径），
 *       否则「升级当天全部智能体不可用」。</li>
 * </ol>
 *
 * <p>与 {@link LlmProfileFallbackColumnPersistenceTests} 同样的边界说明：测试库的表是 smart-mybatis
 * 按实体注解**新建**的，所以这里证明的是「新建出来的列可用」，不能替代存量库的 ALTER 实证。
 */
@SpringBootTest
@ContextConfiguration(initializers = MySqlTestDatabaseInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LlmProfileProviderColumnPersistenceTests {

    /** 本类造的数据一律带该前缀，便于退出时精确清理，不碰其它用例的行。 */
    private static final String TEST_PREFIX = "__provider_col_test__";

    /** 回填用例的两组端点：同端点、不同 key ⇒ 两个供应商。用独立 host，避免与种子/其它用例撞名。 */
    private static final String BACKFILL_BASE_URL = "https://provider-backfill.test";
    private static final String BACKFILL_KEY_SHARED = "sk-backfill-shared-1234";
    private static final String BACKFILL_KEY_OTHER = "sk-backfill-other-5678";

    @Autowired
    private LlmProfileMapper profileMapper;

    @Autowired
    private LlmProviderMapper providerMapper;

    @Autowired
    private LlmProviderService providerService;

    @Autowired
    private LlmProfileService profileService;

    @Autowired
    private LlmProviderBackfillRunner backfillRunner;

    @Autowired
    private LlmProfileSeeder seeder;

    @Autowired
    private CryptoService cryptoService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void removeTestRows() {
        // 先删档案再删供应商：反过来会让档案留下悬空的外键（DDL 无外键约束，但语义上不该制造它）
        jdbcTemplate.update("DELETE FROM LLM_PROFILE WHERE NAME LIKE ?", TEST_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM LLM_PROFILE WHERE PROVIDER_ID IN "
                + "(SELECT ID FROM LLM_PROVIDER WHERE NAME LIKE ? OR BASE_URL = ?)", TEST_PREFIX + "%", BACKFILL_BASE_URL);
        jdbcTemplate.update("DELETE FROM LLM_PROVIDER WHERE NAME LIKE ?", TEST_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM LLM_PROVIDER WHERE BASE_URL = ?", BACKFILL_BASE_URL);
    }

    /**
     * 列必须真的按声明建出来，且与**同名的既有列**同型。
     *
     * <p>{@code PROVIDER_ID} 必须可空：存量库补列时老行只能是 NULL，而
     * {@code LlmProfileMapper.findWithoutProvider()} 正是靠 {@code IS NULL} 找到待回填的行。
     * 列一旦变成 NOT NULL，回填器就永远看不到它们（或者补列直接失败）。
     *
     * <p>同型断言针对的是 smart-mybatis 的字段名共享缓存：{@code name} / {@code provider} /
     * {@code baseUrl} / {@code apiKeyEncrypted} 在 {@code LlmProvider} 与 {@code LlmProfile}
     * 里同名，声明一旦分叉，真实库里的列宽就会被其中一处改写（最坏是把 {@code varchar(500)}
     * 收窄成 50，端点被截断而请求静默打错地址）。
     */
    @Test
    void providerBindingColumnsAreMaterializedAsDeclared() {
        assertThat(column("LLM_PROFILE", "PROVIDER_ID"))
                .as("存量行补列后是 NULL，回填器靠 IS NULL 找它们")
                .isEqualTo("bigint|-1|YES");

        assertThat(column("LLM_PROVIDER", "NAME")).isEqualTo(column("LLM_PROFILE", "NAME")).startsWith("varchar|255|");
        assertThat(column("LLM_PROVIDER", "PROVIDER")).isEqualTo(column("LLM_PROFILE", "PROVIDER")).startsWith("varchar|50|");
        assertThat(column("LLM_PROVIDER", "BASE_URL")).isEqualTo(column("LLM_PROFILE", "BASE_URL")).startsWith("varchar|500|");
        assertThat(column("LLM_PROVIDER", "API_KEY_ENCRYPTED"))
                .as("密文列与既有档案/配置的密文列必须同型（GCM 密文长度随明文增长）")
                .isEqualTo(column("LLM_PROFILE", "API_KEY_ENCRYPTED"));
        assertThat(column("LLM_PROVIDER", "ENABLED")).startsWith("tinyint|");
        assertThat(column("LLM_PROVIDER", "CREATED_BY")).isEqualTo("bigint|-1|YES");

        // 契约 §4.3 修订（用户否决供应商级用途）：实体不再映射 LLM_PROVIDER.PURPOSE，
        // 该列在**升级过的库**里成为无害孤儿列，在**新建的库**里根本不存在——两种状态都合法。
        //
        // 因此这里断言的是「**如果**它还在，就必须可空」，而不是「它必须在」：
        // 后者会在全新库/CI 上假红（字段已删，smart-mybatis 不会建这一列），
        // 前者才守得住真正要防的那件事——有人给这个孤儿列补上 NOT NULL，升级时 ALTER 直接失败，
        // 存量库升级当天起不来（不是「用途错」，是完全启动不了）。
        List<String> purpose = optionalColumn("LLM_PROVIDER", "PURPOSE");
        if (!purpose.isEmpty()) {
            assertThat(purpose.get(0))
                    .as("孤儿列若仍存在，必须可空：老行补列后只能是 NULL，NOT NULL 会让升级 ALTER 失败")
                    .isEqualTo("varchar|20|YES");
        }
    }

    /**
     * 升级窗口：{@code provider_id} 为空的老行，必须继续用自己那两列（真实解密）跑起来。
     *
     * <p>这条是「回填还没跑 / 端点为空造不出供应商」的行在真实密钥下的行为；
     * 如果哪一轮改动把回退分支删了，症状是升级当天所有存量智能体报「没有可用模型」。
     */
    @Test
    void legacyRowsStillResolveTheirOwnEndpointAndKey() {
        LlmProfile legacy = insertProfile(TEST_PREFIX + "-legacy", null,
                "https://legacy-provider.test", cryptoService.encrypt("sk-legacy-1234"));

        LlmProfileService.RuntimeProfile runtime = profileService.runtime(legacy);

        assertThat(runtime.available()).isTrue();
        assertThat(runtime.baseUrl()).isEqualTo("https://legacy-provider.test");
        assertThat(runtime.apiKey()).isEqualTo("sk-legacy-1234");
    }

    /**
     * 验收 §7.3 第 2 条：同一供应商下的多条档案**共享一份密钥**，改一次全部生效。
     *
     * <p>这就是本次改造的全部意义。断言分两段：新建之后三条都用供应商的 key；
     * 轮换供应商的 key 之后，三条**无需任何档案改动**就都换成了新 key。
     * 反过来（每条档案各存一份 key）时第二段必然失败——那正是改造前的行为。
     */
    @Test
    void profilesUnderOneProviderShareItsEndpointAndKeyAndFollowKeyRotation() {
        LlmProviderService.ProviderView provider = providerService.create(new LlmProviderService.ProviderRequest(
                TEST_PREFIX + "-shared", "OPENAI_COMPATIBLE", "https://shared.test/v1/", "sk-shared-9876", null, true), null);
        LlmProfile first = insertProfile(TEST_PREFIX + "-shared-a", provider.id(), null, null);
        LlmProfile second = insertProfile(TEST_PREFIX + "-shared-b", provider.id(), null, null);
        LlmProfile third = insertProfile(TEST_PREFIX + "-shared-c", provider.id(), null, null);

        for (LlmProfile profile : List.of(first, second, third)) {
            LlmProfileService.RuntimeProfile runtime = profileService.runtime(profile);
            assertThat(runtime.available()).isTrue();
            assertThat(runtime.baseUrl()).as("端点来自供应商，且写入时已剥掉 /v1").isEqualTo("https://shared.test");
            assertThat(runtime.apiKey()).isEqualTo("sk-shared-9876");
        }

        providerService.update(provider.id(), new LlmProviderService.ProviderRequest(
                TEST_PREFIX + "-shared", "OPENAI_COMPATIBLE", "https://shared.test", "sk-rotated-0001", null, true));

        for (LlmProfile profile : List.of(first, second, third)) {
            assertThat(profileService.runtime(profile).apiKey())
                    .as("轮换供应商的 key 后，三条档案必须同时生效（这是供应商化要解决的问题本身）")
                    .isEqualTo("sk-rotated-0001");
        }
    }

    /**
     * 验收 §7.3 第 3 条：停用供应商 ⇒ 其全部模型在故障切换链上消失；重新启用 ⇒ 又回来。
     *
     * <p>停用是运维「整组下线」的开关，链路必须自动成立，而不是靠用户逐条去停用档案。
     */
    @Test
    void disablingTheProviderRemovesItsModelsFromTheFailoverChain() {
        LlmProviderService.ProviderView provider = providerService.create(new LlmProviderService.ProviderRequest(
                TEST_PREFIX + "-toggle", "OPENAI_COMPATIBLE", "https://toggle.test", "sk-toggle-1234", null, true), null);
        LlmProfile first = insertProfile(TEST_PREFIX + "-toggle-a", provider.id(), null, null);
        LlmProfile second = insertProfile(TEST_PREFIX + "-toggle-b", provider.id(), null, null);
        Set<Long> ids = Set.of(first.getId(), second.getId());

        assertThat(profileService.failoverChain(null)).extracting(LlmProfile::getId).containsAll(ids);

        providerService.update(provider.id(), new LlmProviderService.ProviderRequest(
                TEST_PREFIX + "-toggle", "OPENAI_COMPATIBLE", "https://toggle.test", null, null, false));

        assertThat(profileService.failoverChain(null))
                .as("停用供应商后它的模型必须整组消失（链上不许留一条必然失败的一跳）")
                .extracting(LlmProfile::getId).doesNotContainAnyElementsOf(ids);
        assertThat(profileService.runtime(first).available()).isFalse();

        providerService.update(provider.id(), new LlmProviderService.ProviderRequest(
                TEST_PREFIX + "-toggle", "OPENAI_COMPATIBLE", "https://toggle.test", null, null, true));

        assertThat(profileService.failoverChain(null))
                .as("重新启用后必须回来（开关是可逆的，不能把档案改坏）")
                .extracting(LlmProfile::getId).containsAll(ids);
    }

    /**
     * 验收 §7.4：回填把「同三元组」的存量档案收敛成一个供应商，且**幂等**。
     *
     * <p>8 条同端点同 key 的档案，key 是同一明文**独立加密 8 次**（密文两两不同——这正是开发库里的
     * 真实形态，也是「按密文归组」行不通的原因）。回填后它们必须共用一个供应商；
     * 另外 2 条同端点不同 key 的档案必须落到**另一个**供应商（通道不同不能合并）。
     * 再跑一遍不得有任何变化：回填没有标记表，幂等性完全由「只处理 provider_id IS NULL」保证。
     */
    @Test
    void backfillConvergesSameTripleProfilesIntoOneProviderAndIsIdempotent() {
        List<String> ciphertexts = new java.util.ArrayList<>();
        List<LlmProfile> shared = new java.util.ArrayList<>();
        for (int index = 1; index <= 8; index++) {
            String ciphertext = cryptoService.encrypt(BACKFILL_KEY_SHARED);
            ciphertexts.add(ciphertext);
            shared.add(insertProfile(TEST_PREFIX + "-bf-" + index, null, BACKFILL_BASE_URL, ciphertext));
        }
        assertThat(Set.copyOf(ciphertexts))
                .as("前提：AES-GCM 随机 IV，同一明文的 8 次加密必须得到 8 个不同密文（否则本用例证明不了归组键的正确性）")
                .hasSize(8);

        List<LlmProfile> other = List.of(
                insertProfile(TEST_PREFIX + "-bf-x1", null, BACKFILL_BASE_URL, cryptoService.encrypt(BACKFILL_KEY_OTHER)),
                insertProfile(TEST_PREFIX + "-bf-x2", null, BACKFILL_BASE_URL, cryptoService.encrypt(BACKFILL_KEY_OTHER)));

        backfillRunner.run(null);

        Set<Long> sharedProviderIds = shared.stream()
                .map(profile -> profileMapper.findById(profile.getId()).getProviderId())
                .collect(Collectors.toSet());
        Set<Long> otherProviderIds = other.stream()
                .map(profile -> profileMapper.findById(profile.getId()).getProviderId())
                .collect(Collectors.toSet());

        assertThat(sharedProviderIds).as("8 条同 key 档案必须收敛到同一个供应商").hasSize(1).doesNotContainNull();
        assertThat(otherProviderIds).as("2 条同 key 档案必须收敛到同一个供应商").hasSize(1).doesNotContainNull();
        assertThat(sharedProviderIds).as("同端点不同 key 是两个通道，不能合并成一个供应商")
                .doesNotContainAnyElementsOf(otherProviderIds);

        List<LlmProvider> providers = providerMapper.findAll().stream()
                .filter(provider -> BACKFILL_BASE_URL.equals(provider.getBaseUrl())).toList();
        assertThat(providers).hasSize(2);
        assertThat(providers).extracting(provider -> providerService.runtime(provider).apiKey())
                .as("回填复用原密文，两个供应商各自解出自己的 key")
                .containsExactlyInAnyOrder(BACKFILL_KEY_SHARED, BACKFILL_KEY_OTHER);
        assertThat(providers).allSatisfy(provider -> assertThat(provider.getEnabled()).isTrue());

        // 幂等：再跑一遍，供应商与档案的绑定必须一字不变
        backfillRunner.run(null);

        assertThat(shared.stream().map(profile -> profileMapper.findById(profile.getId()).getProviderId())
                .collect(Collectors.toSet())).isEqualTo(sharedProviderIds);
        assertThat(providerMapper.findAll().stream()
                .filter(provider -> BACKFILL_BASE_URL.equals(provider.getBaseUrl())).count())
                .as("回填不得每次都新建供应商（幂等性靠「只处理 provider_id IS NULL」）")
                .isEqualTo(2L);
    }

    /**
     * Lead 2026-11-10 补充的 C 条：回填必须**早于**档案种子，且种子档案要继承供应商绑定。
     *
     * <p>为什么次序是硬要求：种子是按**名称**幂等的（已存在就不动），它从来源档案复制
     * {@code provider_id}。回填若晚于种子，种子建出来的 12 条标准档案会一直是
     * {@code provider_id = NULL}——它们永远走「档案自身旧列」的回退路径、在供应商页面上看不见，
     * 而**不会报任何错**。这类静默错位只能靠一条断言钉住。
     *
     * <p>同时钉住本条改动的另一半：来源档案自己的旧列是空的（新 UI 建立的档案就是这样），
     * 所以「来源是否可用」的判据必须走 {@code hasReachableKey}（供应商有 key 也算），
     * 否则种子会认为「没有可用来源」而静默停机。
     */
    @Test
    void providerBackfillRunsBeforeTheProfileSeederAndSeededProfilesInheritTheBinding() {
        assertThat(orderOf(LlmProviderBackfillRunner.class))
                .as("回填必须先于种子，否则种子档案永远拿不到 provider_id")
                .isLessThan(orderOf(LlmProfileSeeder.class));

        LlmProviderService.ProviderView provider = providerService.create(new LlmProviderService.ProviderRequest(
                TEST_PREFIX + "-seed", "OPENAI_COMPATIBLE", "https://seed.test", "sk-seed-1234", null, true), null);
        LlmProfile source = insertProfile(TEST_PREFIX + "-seed-source", provider.id(), null, null);
        source.setIsDefault(true);
        profileMapper.updateById(source);

        seeder.run(null);

        // 断言用两个锚点：deepseek-flash 是 AgentSeeder 的 A 档主用（按名称绑定，不能改名），
        // 再加上「跑完种子后实际存在的那条兜底档案」。
        // 2026-11-10：原来这里写死 hy4-preview，兜底改成 nexus-auto 后 NPE；
        // 而 seeds()/Seed 是 agent 包的内部细节，本类在包外不该依赖，故改为取实际兜底行。
        LlmProfile declaredFallback = profileMapper.findFallback();
        List<LlmProfile> seeded = List.of(profileMapper.findByName("deepseek-flash"), declaredFallback);
        assertThat(seeded).as("种子应把声明的标准档案都建出来").doesNotContainNull();
        assertThat(seeded).allSatisfy(profile -> assertThat(profile.getProviderId())
                .as("种子档案必须继承来源档案的供应商绑定（否则它们只在回退路径上活着）")
                .isEqualTo(provider.id()));
        // 复查项 m-10：断言范围收窄到**本用例自己造的行**。
        // 原来查的是全库 COUNT(*)——任何其它测试类遗留的空绑定档案都会让它假红，
        // 而失败信息只说「不等于 0」，看不出到底是谁的行。按 id 集合断言才指向真正的主体。
        List<Long> ownIds = new java.util.ArrayList<>();
        ownIds.add(source.getId());
        for (LlmProfile seededProfile : seeded) ownIds.add(seededProfile.getId());
        List<Long> unbound = jdbcTemplate.queryForList(
                "SELECT ID FROM LLM_PROFILE WHERE PROVIDER_ID IS NULL AND ID IN ("
                        + ownIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")",
                Long.class);
        assertThat(unbound)
                .as("本用例造的档案（来源档 + 种子档）不该有任何一条是空绑定：种子必须继承 provider_id")
                .isEmpty();
    }

    // ---------- 脚手架 ----------

    private LlmProfile insertProfile(String name, Long providerId, String baseUrl, String apiKeyEncrypted) {
        LlmProfile profile = new LlmProfile();
        profile.setName(name);
        profile.setProviderId(providerId);
        // 新口径的档案：协议/端点/密钥三列留空（端点与密钥归供应商）；存量回退行才写它们
        profile.setProvider(providerId == null ? "OPENAI_COMPATIBLE" : null);
        profile.setBaseUrl(baseUrl);
        profile.setModelName("test-model");
        profile.setApiKeyEncrypted(apiKeyEncrypted);
        profile.setEnabled(true);
        profile.setIsDefault(false);
        profile.setIsFallback(false);
        LocalDateTime now = LocalDateTime.now();
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        profileMapper.insert(profile);
        return profile;
    }

    /**
     * 与 {@link #column} 同理，但**允许列不存在**（返回空列表）。
     *
     * <p>只用于「孤儿列」这类合法缺席的列：实体已不再声明它，新建库不会有，
     * 升级过的库还留着——断言必须同时接受这两种状态，否则测试就绑死在某台机器的历史上了。
     */
    private List<String> optionalColumn(String table, String columnName) {
        return jdbcTemplate.queryForList(
                "SELECT CONCAT(DATA_TYPE, '|', IFNULL(CHARACTER_MAXIMUM_LENGTH, -1), '|', IS_NULLABLE) "
                        + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND UPPER(TABLE_NAME) = ? AND UPPER(COLUMN_NAME) = ?",
                String.class, table, columnName);
    }

    /** {@code DATA_TYPE|字符长度(-1 表示无)|是否可空}，用于证明列真按声明建了出来。 */
    private String column(String table, String columnName) {
        List<String> types = jdbcTemplate.queryForList(
                "SELECT CONCAT(DATA_TYPE, '|', IFNULL(CHARACTER_MAXIMUM_LENGTH, -1), '|', IS_NULLABLE) "
                        + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND UPPER(TABLE_NAME) = ? AND UPPER(COLUMN_NAME) = ?",
                String.class, table, columnName);
        assertThat(types).as(table + "." + columnName + " 必须由实体声明真实建出来").hasSize(1);
        return types.get(0);
    }

    private int orderOf(Class<?> type) {
        Order order = type.getAnnotation(Order.class);
        assertThat(order).as(type.getSimpleName() + " 必须声明 @Order（启动 Runner 的次序是硬约束）").isNotNull();
        return order.value();
    }
}
