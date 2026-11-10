package ink.icoding.wechat.article;

import ink.icoding.wechat.article.agent.LlmProfile;
import ink.icoding.wechat.article.agent.LlmProfileMapper;
import ink.icoding.wechat.article.agent.LlmProfileService;
import ink.icoding.wechat.article.agent.LlmProvider;
import ink.icoding.wechat.article.agent.LlmProviderMapper;
import ink.icoding.wechat.article.agent.LlmProviderService;
import ink.icoding.wechat.article.common.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 缺陷 B3 的端到端验收：{@code APP_SECRET_KEY} 轮换/写错之后，两个**修复入口**必须还能打开。
 *
 * <p><b>为什么这是本轮最值得修的一条</b>（Lead 2026-11-10 在真实坏库上实测）：
 * <pre>
 * GET /api/settings/llm   =&gt; HTTP 500
 * GET /api/llm-profiles   =&gt; HTTP 500
 * GET /api/llm-providers  =&gt; HTTP 200   （表是空的，没有密文可解，所以它反而是好的）
 * </pre>
 * 也就是说管理员既进不去设置页、也进不去模型档案页——而那正是唯一能改回密钥的地方。
 * 修法不是「吞异常」，而是**读路径容错解密**：解不开就降级成「不可用 / 无法解密」，
 * 让人进得去、看得见、改得掉。
 *
 * <p><b>为什么必须打真库</b>：这条缺陷的触发前提是「库里存在一段无法解密的密文」，
 * 那是一个**数据状态**，只有真实 INSERT 造得出来。单测里喂一个假密文只能证明某个方法不抛，
 * 证明不了「两个接口都还是 200」。
 *
 * <p>本类自带清理：造的行一律带 {@link #TEST_PREFIX}，退出时按前缀删掉。
 * 清理**必须**做到，否则一段坏密文留在共享测试库里，会让别的用例的读取路径持续报警。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = MySqlTestDatabaseInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LlmBrokenCiphertextReadPathTests {

    private static final String TEST_PREFIX = "__broken_cipher_test__";

    /** 既不是合法 Base64、也不可能是合法 GCM 密文：解密必然抛（正是真实坏库的形态）。 */
    private static final String BROKEN_CIPHERTEXT = "BROKEN-CIPHERTEXT-NOT-BASE64";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LlmProfileMapper profileMapper;

    @Autowired
    private LlmProviderMapper providerMapper;

    @Autowired
    private LlmProviderService providerService;

    @Autowired
    private LlmProfileService profileService;

    @AfterEach
    void removeTestRows() {
        jdbcTemplate.update("DELETE FROM LLM_PROFILE WHERE NAME LIKE ?", TEST_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM LLM_PROFILE WHERE PROVIDER_ID IN "
                + "(SELECT ID FROM LLM_PROVIDER WHERE NAME LIKE ?)", TEST_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM LLM_PROVIDER WHERE NAME LIKE ?", TEST_PREFIX + "%");
    }

    private String adminToken() throws Exception {
        for (String password : new String[]{"Admin@123", "Admin@456"}) {
            var result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"" + password + "\"}"))
                    .andReturn();
            if (result.getResponse().getStatus() == 200) {
                Matcher matcher = Pattern.compile("\\\"token\\\":\\\"([^\\\"]+)\\\"")
                        .matcher(result.getResponse().getContentAsString());
                if (matcher.find()) return "Bearer " + matcher.group(1);
            }
        }
        throw new AssertionError("无法以 admin 身份登录");
    }

    /**
     * 核心用例：库里同时存在「坏密钥的存量档案」与「坏密钥的供应商」时，
     * 两个修复入口必须都返回 200，且坏行显示为「不可用 / 无法解密」。
     *
     * <p>三件事一起钉：①不是 500（能进去）；②坏行**仍在列表里**（能看见问题）；
     * ③掩码是「无法解密」而不是「未配置」（能定位到真正的原因）。
     */
    @Test
    void modelProfileAndSettingsEndpointsStayReadableWithUndecryptableKeys() throws Exception {
        String admin = adminToken();
        LlmProvider brokenProvider = insertProvider();
        insertLegacyBrokenProfile();
        insertAttachedProfile(brokenProvider.getId());

        String listed = mockMvc.perform(get("/api/llm-profiles").header("Authorization", admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(listed)
                .as("坏数据必须仍能被列出：用户看不到问题就无从修复")
                .contains(TEST_PREFIX + "-legacy").contains(TEST_PREFIX + "-attached");
        assertThat(listed)
                .as("坏密钥必须显式提示，且密文一个字都不能回传")
                .contains(LlmProviderService.UNDECRYPTABLE_MASK)
                .doesNotContain(BROKEN_CIPHERTEXT);

        mockMvc.perform(get("/api/settings/llm").header("Authorization", admin))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/llm-providers").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name=='" + TEST_PREFIX + "-broken')].hasApiKey").value(false))
                .andExpect(jsonPath("$.data[?(@.name=='" + TEST_PREFIX + "-broken')].apiKeyMasked")
                        .value(LlmProviderService.UNDECRYPTABLE_MASK));
    }

    /**
     * 运行时与发现接口的同一口径：坏密钥 = 不可用，但**不抛到外面**。
     *
     * <p>运行时若抛，一条脏行就会带走整条故障切换链（链的存在意义正是「总还有一条能用」）；
     * 模型发现则相反，它是用户点按钮发起的，必须给一条可读的业务错误，而不是 500。
     */
    @Test
    void runtimeDegradesAndDiscoveryReportsAReadableBusinessError() {
        LlmProvider brokenProvider = insertProvider();

        assertThat(providerService.runtime(providerMapper.findById(brokenProvider.getId())).available())
                .as("运行时按「不可用」降级，绝不抛")
                .isFalse();
        assertThatThrownBy(() -> providerService.discoverModels(brokenProvider.getId()))
                .isInstanceOf(BusinessException.class)
                .as("必须与「未配置 API Key」区分开：两者的修复动作完全不同")
                .hasMessageContaining("无法解密");
    }

    /** 单测层面的同一件事：供应商视图与档案视图都降级成「无法解密」，不抛。 */
    @Test
    void viewsDegradeInsteadOfThrowing() {
        LlmProvider brokenProvider = insertProvider();
        LlmProviderService.ProviderView providerView = providerService.get(brokenProvider.getId());
        assertThat(providerView.hasApiKey()).isFalse();
        assertThat(providerView.apiKeyMasked()).isEqualTo(LlmProviderService.UNDECRYPTABLE_MASK);

        LlmProfile legacy = insertLegacyBrokenProfile();
        LlmProfileService.ProfileView profileView = profileService.get(legacy.getId());
        assertThat(profileView.hasApiKey()).isFalse();
        assertThat(profileView.apiKeyMasked())
                .as("档案页与供应商页必须给出同一个提示字符串")
                .isEqualTo(LlmProviderService.UNDECRYPTABLE_MASK);
    }

    /**
     * 设置页自身的两列也写坏之后，读接口仍要能开——这是「改错了还能改回来」的最后一道保障。
     *
     * <p>两种回落形态都覆盖：默认档案**存在**（设置页显示档案的值，掩码要委托档案服务，
     * 两页必须给出同一个提示）与默认档案**不存在**（设置页显示 {@code llm_config} 自己的两列）。
     *
     * <p>注意顺序：{@code GET} 会顺手把缺失的 {@code llm_config} 行建出来，所以必须先 GET 一次
     * 确保行存在，再 UPDATE 那两列；反过来 UPDATE 会打在 0 行上，用例便成了假绿（或像本用例第一版
     * 那样假红）。原值在退出时按 id 逐字恢复，绝不把 {@code ENABLED} 留成 1 影响其它用例。
     */
    @Test
    void settingsWithBrokenCiphertextStillLeaveTheReadPathsUsable() throws Exception {
        String admin = adminToken();
        // ① 先 GET 一次，确保 llm_config 行存在（不存在时它会被创建成默认值）
        mockMvc.perform(get("/api/settings/llm").header("Authorization", admin)).andExpect(status().isOk());

        Long configId = jdbcTemplate.queryForObject("SELECT ID FROM LLM_CONFIG LIMIT 1", Long.class);
        String originalApiKey = jdbcTemplate.queryForObject(
                "SELECT API_KEY_ENCRYPTED FROM LLM_CONFIG WHERE ID = ?", String.class, configId);
        String originalImageKey = jdbcTemplate.queryForObject(
                "SELECT IMAGE_API_KEY_ENCRYPTED FROM LLM_CONFIG WHERE ID = ?", String.class, configId);
        Integer originalEnabled = jdbcTemplate.queryForObject(
                "SELECT ENABLED FROM LLM_CONFIG WHERE ID = ?", Integer.class, configId);
        try {
            jdbcTemplate.update("UPDATE LLM_CONFIG SET API_KEY_ENCRYPTED = ?, IMAGE_API_KEY_ENCRYPTED = ?, "
                    + "ENABLED = 1 WHERE ID = ?", BROKEN_CIPHERTEXT, BROKEN_CIPHERTEXT, configId);

            // ② 默认档案存在：设置页显示档案的值（这里没有默认档案，所以落到 llm_config 分支）
            mockMvc.perform(get("/api/settings/llm").header("Authorization", admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.hasApiKey").value(false))
                    .andExpect(jsonPath("$.data.hasImageApiKey").value(true))
                    .andExpect(jsonPath("$.data.apiKeyMasked").value(LlmProviderService.UNDECRYPTABLE_MASK))
                    .andExpect(jsonPath("$.data.imageApiKeyMasked").value(LlmProviderService.UNDECRYPTABLE_MASK));

            mockMvc.perform(get("/api/llm-profiles").header("Authorization", admin))
                    .andExpect(status().isOk());
        } finally {
            // 逐字恢复原值：直接写 NULL/0 会污染共享测试上下文里的其它用例
            jdbcTemplate.update("UPDATE LLM_CONFIG SET API_KEY_ENCRYPTED = ?, IMAGE_API_KEY_ENCRYPTED = ?, "
                    + "ENABLED = ? WHERE ID = ?", originalApiKey, originalImageKey, originalEnabled, configId);
        }
    }

    // ---------- 脚手架 ----------

    private LlmProvider insertProvider() {
        LlmProvider provider = new LlmProvider();
        provider.setName(TEST_PREFIX + "-broken");
        provider.setProvider("OPENAI_COMPATIBLE");
        provider.setBaseUrl("https://broken.test");
        provider.setApiKeyEncrypted(BROKEN_CIPHERTEXT);
        provider.setEnabled(true);
        providerMapper.insert(provider);
        return provider;
    }

    private LlmProfile insertLegacyBrokenProfile() {
        LlmProfile profile = new LlmProfile();
        profile.setName(TEST_PREFIX + "-legacy");
        // 存量回退行：三列自带端点与（坏）密钥，provider_id 为空
        profile.setProvider("OPENAI_COMPATIBLE");
        profile.setBaseUrl("https://broken.test");
        profile.setModelName("broken-model");
        profile.setApiKeyEncrypted(BROKEN_CIPHERTEXT);
        profile.setEnabled(true);
        profile.setIsDefault(false);
        profile.setIsFallback(false);
        profile.setCreatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        profileMapper.insert(profile);
        return profile;
    }

    private LlmProfile insertAttachedProfile(Long providerId) {
        LlmProfile profile = new LlmProfile();
        profile.setName(TEST_PREFIX + "-attached");
        profile.setProviderId(providerId);
        profile.setModelName("broken-model");
        profile.setEnabled(true);
        profile.setIsDefault(false);
        profile.setIsFallback(false);
        profile.setCreatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        profileMapper.insert(profile);
        return profile;
    }
}