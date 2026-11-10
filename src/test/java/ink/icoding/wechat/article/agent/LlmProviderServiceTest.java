package ink.icoding.wechat.article.agent;

import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.common.CryptoService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 供应商单测（llm-provider-refactor §5.1 / §5.3 / §5.4）：唯一名、协议白名单、启用必须有 key、
 * 删除护栏、掩码不泄密、baseUrl 归一化与 host 命名，以及**本轮最要紧的一条**
 * ——按三元组归组时第三段必须是**解密后的明文 key**。
 *
 * <p><b>为什么归组那条必须用真实 {@code CryptoService}</b>：这条判据的整个理由就是
 * 「AES-GCM 每次随机 IV，同一明文加密两次得到不同密文」（实测 3 条同 key 档案的密文 MD5 三个不同值）。
 * 用一个「明文=密文」的替身去测，等于把这条性质从测试里删掉——它永远绿，而真实部署里
 * 每个用户手工建的档案都会各分到一个供应商（本次改造的意义直接归零）。因此这里用真实实现加密两次、
 * 断言密文不同、再断言仍然收敛到同一个供应商。
 *
 * <p>为什么删除护栏值得单测：级联删除会**静默带走智能体的档案绑定**——用户点一次「删除供应商」，
 * 几个智能体就悄悄回落默认档案了，而成功响应不会提示任何事。
 */
class LlmProviderServiceTest {

    /** 真实加解密：见类注释，这条依赖随机 IV 的真实行为。 */
    private static final CryptoService CRYPTO = new CryptoService("llm-provider-service-test-secret");

    private static final String BASE_URL = "https://nexus.bx9y.com.cn";
    private static final String LONG_KEY = "sk-abcdefghijklmn";

    // ---------- CRUD 规则 ----------

    @Test
    void duplicateNameIsRejected() {
        LlmProviderMapper mapper = inMemoryMapper();
        LlmProviderService service = service(mapper);
        LlmProvider existing = new LlmProvider();
        existing.setId(1L);
        existing.setName("nexus");
        when(mapper.findByName("nexus")).thenReturn(existing);

        assertThatThrownBy(() -> service.create(request("nexus", "OPENAI_COMPATIBLE", BASE_URL, LONG_KEY, null, true), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商名称已存在");
    }

    /** 更新自己时不该被自己判成重名（排除自身 id）。 */
    @Test
    void updateDoesNotTreatItselfAsADuplicateName() {
        LlmProvider existing = new LlmProvider();
        existing.setId(1L);
        existing.setName("nexus");
        existing.setProvider("OPENAI_COMPATIBLE");
        existing.setBaseUrl(BASE_URL);
        existing.setApiKeyEncrypted(CRYPTO.encrypt(LONG_KEY));
        existing.setEnabled(true);
        LlmProviderMapper mapper = inMemoryMapper(existing);
        LlmProviderService service = service(mapper);
        when(mapper.findByName("nexus")).thenReturn(existing);

        assertThat(service.update(1L, request("nexus", "OPENAI_COMPATIBLE", "https://other.test", null, null, true))
                .name()).isEqualTo("nexus");
    }

    @Test
    void unsupportedProtocolIsRejected() {
        LlmProviderService service = service(inMemoryMapper());

        assertThatThrownBy(() -> service.create(request("x", "GEMINI", BASE_URL, LONG_KEY, null, true), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的 LLM 服务类型");
    }

    /**
     * 启用中的供应商必须有 key：否则会造出「启用却永远不可用」的行，
     * 管理员在页面上看到的是启用态、运行时却整组消失。
     */
    @Test
    void enabledProviderWithoutApiKeyIsRejected() {
        LlmProviderService service = service(inMemoryMapper());

        assertThatThrownBy(() -> service.create(request("x", "OPENAI_COMPATIBLE", BASE_URL, null, null, true), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("启用供应商前必须配置 API Key");

        // 停用态允许没有 key（设置页那种「还没配好」的存量形态）
        assertThat(service.create(request("x", "OPENAI_COMPATIBLE", BASE_URL, null, null, false), 1L).hasApiKey())
                .isFalse();
    }

    @Test
    void deleteIsRejectedWhileModelsAreStillAttached() {
        LlmProviderMapper mapper = inMemoryMapper();
        LlmProviderService service = service(mapper, profileCount(3L));
        Long id = service.create(request("nexus", "OPENAI_COMPATIBLE", BASE_URL, LONG_KEY, null, true), 1L).id();

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(BusinessException.class)
                .as("必须拒绝级联删除，并在消息里给出剩余模型数")
                .hasMessageContaining("3");
        verify(mapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteSucceedsWhenNothingIsAttached() {
        LlmProviderMapper mapper = inMemoryMapper();
        LlmProviderService service = service(mapper, profileCount(0L));
        Long id = service.create(request("nexus", "OPENAI_COMPATIBLE", BASE_URL, LONG_KEY, null, true), 1L).id();

        service.delete(id);

        verify(mapper).deleteById(id);
    }

    // ---------- baseUrl 归一化 / 掩码 ----------

    @Test
    void baseUrlIsNormalizedOnWrite() {
        LlmProviderService service = service(inMemoryMapper());

        assertThat(service.create(request("x", "OPENAI_COMPATIBLE", BASE_URL + "/v1/", LONG_KEY, null, true), 1L)
                .baseUrl()).isEqualTo(BASE_URL);
        assertThatThrownBy(() -> service.create(request("y", "OPENAI_COMPATIBLE", "nexus.bx9y.com.cn", LONG_KEY, null, true), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("http://");
    }

    /**
     * 视图只给掩码，且掩码规则与档案页逐字一致（长度 ≥12 → 后 4 位；短 key 全掩码）。
     *
     * <p>短 key 全掩码不是洁癖：末 4 位对一段 8 位 key 就是一半的内容。
     */
    @Test
    void viewMasksTheApiKeyAndNeverReturnsIt() {
        LlmProviderService service = service(inMemoryMapper());
        LlmProviderService.ProviderView view = service.create(
                request("nexus", "OPENAI_COMPATIBLE", BASE_URL, LONG_KEY, null, true), 1L);

        assertThat(view.apiKeyMasked()).isEqualTo("••••••••klmn");
        assertThat(view.apiKeyMasked()).doesNotContain(LONG_KEY);
        assertThat(view.hasApiKey()).isTrue();

        LlmProviderService.ProviderView shortKey = service.create(
                request("short", "OPENAI_COMPATIBLE", "https://short.test", "12345678", null, true), 1L);
        assertThat(shortKey.apiKeyMasked())
                .as("短 key 必须全掩码，否则末 4 位会泄露一半内容")
                .isEqualTo("••••••••••••");
    }

    // ---------- 三元组归组（第三段 = 解密后的明文 key） ----------

    /**
     * **本轮的核心判据**：同一明文 key 的两次加密得到不同密文，但必须归到同一个供应商。
     *
     * <p>这条用例就是「存量 8 条 nexus 档案收敛成 1 个供应商」的最小复现。若哪天有人把匹配键
     * 换成密文（看起来更省事：不用解密），这条会红，而真实部署的后果是每个用户手工建的档案
     * 各分到一个供应商——供应商页面变成档案页的镜像，改造白做。
     */
    @Test
    void samePlaintextKeyInDifferentCiphertextsStillReusesOneProvider() {
        String first = CRYPTO.encrypt("sk-same-key-1234");
        String second = CRYPTO.encrypt("sk-same-key-1234");
        assertThat(first)
                .as("前提：AES-GCM 每次随机 IV，同一明文得到不同密文（否则这条用例证明不了任何东西）")
                .isNotEqualTo(second);

        LlmProviderMapper mapper = inMemoryMapper(provider(1L, "nexus.bx9y.com.cn", true, first));
        LlmProviderService service = service(mapper);

        Long providerId = service.attachOrCreateProvider("OPENAI_COMPATIBLE", BASE_URL, second, "nexus.bx9y.com.cn");

        assertThat(providerId).isEqualTo(1L);
        verify(mapper, never()).insert(any(LlmProvider.class));
    }

    /** 明文不同 ⇒ 不同供应商（同端点、不同 key 是两个通道，不能合并）。 */
    @Test
    void differentPlaintextKeysProduceDifferentProviders() {
        LlmProviderMapper mapper = inMemoryMapper(provider(1L, "nexus.bx9y.com.cn", true, CRYPTO.encrypt("sk-key-a")));

        Long providerId = service(mapper).attachOrCreateProvider("OPENAI_COMPATIBLE", BASE_URL,
                CRYPTO.encrypt("sk-key-b"), "nexus.bx9y.com.cn");

        assertThat(providerId).isNotEqualTo(1L);
    }

    /** key 为空的档案按「无 key」归一：两条都无 key 的同端点档案归到一个供应商，而不是各建一个。 */
    @Test
    void keylessProfilesGroupIntoASingleProvider() {
        LlmProviderMapper mapper = inMemoryMapper();
        LlmProviderService service = service(mapper);

        Long first = service.attachOrCreateProvider("OPENAI_COMPATIBLE", BASE_URL, null, "nexus.bx9y.com.cn");
        Long second = service.attachOrCreateProvider("OPENAI_COMPATIBLE", BASE_URL, null, "nexus.bx9y.com.cn");

        assertThat(second).isEqualTo(first);
        assertThat(mapper.findAll()).hasSize(1);
        assertThat(mapper.findAll().get(0).getEnabled())
                .as("没有 key 的供应商建成停用：启用却没有 key 与 CRUD 的校验规则自相矛盾")
                .isFalse();
    }

    /** 回填命名：取 baseUrl 的 host；重名时追加 (2)、(3)…，绝不改写别人的三元组。 */
    @Test
    void backfillNamingUsesHostAndAppendsSuffixWhenTaken() {
        String untouchedCiphertext = CRYPTO.encrypt("sk-other");
        LlmProviderMapper mapper = inMemoryMapper(provider(1L, "nexus.bx9y.com.cn", true, untouchedCiphertext));
        LlmProviderService service = service(mapper);

        Long providerId = service.attachOrCreateProvider("OPENAI_COMPATIBLE", BASE_URL,
                CRYPTO.encrypt("sk-mine"), "nexus.bx9y.com.cn");

        LlmProvider created = mapper.findById(providerId);
        assertThat(created.getName()).isEqualTo("nexus.bx9y.com.cn (2)");
        assertThat(mapper.findById(1L).getApiKeyEncrypted())
                .as("撞名只换名，不得改写已存在供应商的密钥")
                .isEqualTo(untouchedCiphertext);
    }

    @Test
    void nameFromBaseUrlFallsBackToANeutralNameForUnparsableValues() {
        assertThat(LlmProviderService.nameFromBaseUrl(BASE_URL)).isEqualTo("nexus.bx9y.com.cn");
        assertThat(LlmProviderService.nameFromBaseUrl("https://")).isEqualTo("供应商");
    }

    // ---------- 设置页写透 / 默认供应商 ----------

    /**
     * 写透：三元组未命中时**复用「默认供应商」那一行并改写它的三元组**（而不是每保存一次就多一个供应商）。
     *
     * <p>为什么这里允许改写、而回填不允许：这一行是设置页快捷入口的载体，设置页保存的值就是这个三元组；
     * 回填面对的是用户自建的档案，改写别人的行等于静默改变另一个通道。
     */
    @Test
    void writeThroughReusesAndRewritesTheDefaultProviderRow() {
        LlmProvider def = provider(5L, LlmProviderService.DEFAULT_PROVIDER_NAME, true, CRYPTO.encrypt("sk-old"));
        def.setBaseUrl("https://old.test");
        LlmProviderMapper mapper = inMemoryMapper(def);
        LlmProviderService service = service(mapper);

        Long providerId = service.resolveDefaultProviderId("OPENAI_COMPATIBLE", BASE_URL,
                CRYPTO.encrypt("sk-new"), true);

        assertThat(providerId).isEqualTo(5L);
        assertThat(def.getBaseUrl()).isEqualTo(BASE_URL);
        verify(mapper, never()).insert(any(LlmProvider.class));
    }

    /** 写透：启用开关跟随设置页，但**没有 key 一律落成停用**（守住「启用必须有 key」的不变量）。 */
    @Test
    void writeThroughNeverCreatesAnEnabledProviderWithoutKey() {
        LlmProviderMapper mapper = inMemoryMapper();
        LlmProviderService service = service(mapper);

        Long providerId = service.resolveDefaultProviderId("OPENAI_COMPATIBLE", BASE_URL, null, true);

        assertThat(mapper.findById(providerId).getEnabled()).isFalse();
    }

    // ---------- M-1：enabled 的三态语义 ----------

    /**
     * M-1：新建时 {@code enabled=null} 必须落成**停用**，否则「先存端点、再补 key」两步保存走不通。
     *
     * <p>改造前的写法是 {@code enabled == null || enabled}——null 被当成 true，而无 key 的启用态
     * 必然被「启用供应商前必须配置 API Key」打成 400。于是用户永远建不出一个等着补 key 的供应商，
     * 只能一次填完全部字段，而那时他还没验证过端点是否可达。
     */
    @Test
    void createWithoutEnabledLandsDisabledSoTheTwoStepSaveWorks() {
        LlmProviderService service = service(inMemoryMapper());

        LlmProviderService.ProviderView created = service.create(
                request("two-step", "OPENAI_COMPATIBLE", BASE_URL, null, null, null), 1L);

        assertThat(created.enabled()).isFalse();
        assertThat(created.hasApiKey()).isFalse();

        LlmProviderService.ProviderView enabled = service.update(created.id(),
                request("two-step", "OPENAI_COMPATIBLE", BASE_URL, LONG_KEY, null, true));
        assertThat(enabled.enabled()).isTrue();
        assertThat(enabled.hasApiKey()).isTrue();
    }

    /**
     * M-1：更新时 {@code enabled=null} 必须**保持原值**。
     *
     * <p>前端开关每次显式带 enabled，所以这条主要保护脚本/旧调用方：一次只改名字的 PUT
     * 不该把一条启用中的供应商静默停掉——那样它下面所有档案会在故障切换链上消失，
     * 而响应里只有「改名成功」。
     */
    @Test
    void updateWithoutEnabledKeepsTheExistingValue() {
        LlmProvider existing = provider(1L, "nexus", true, CRYPTO.encrypt(LONG_KEY));
        LlmProviderMapper mapper = inMemoryMapper(existing);
        LlmProviderService service = service(mapper);
        when(mapper.findByName("nexus")).thenReturn(existing);

        LlmProviderService.ProviderView renamed = service.update(1L,
                request("nexus", "OPENAI_COMPATIBLE", "https://renamed.test", null, null, null));

        assertThat(renamed.enabled()).as("只改端点不该动启用位").isTrue();
        assertThat(renamed.baseUrl()).isEqualTo("https://renamed.test");
    }

    /** M-1 的边界：显式「清空 key」+ 仍是启用态 ⇒ 必须拒绝（启用的供应商必须有 key）。 */
    @Test
    void clearingTheKeyWhileStillEnabledIsRejected() {
        LlmProvider existing = provider(1L, "nexus", true, CRYPTO.encrypt(LONG_KEY));
        LlmProviderMapper mapper = inMemoryMapper(existing);
        LlmProviderService service = service(mapper);
        when(mapper.findByName("nexus")).thenReturn(existing);

        assertThatThrownBy(() -> service.update(1L,
                request("nexus", "OPENAI_COMPATIBLE", BASE_URL, null, true, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("启用供应商前必须配置 API Key");
    }

    // ---------- B2：写透不得改写被引用的「默认供应商」 ----------

    /**
     * B2：名为「默认供应商」的那一行**已被非默认档案引用**时，写透必须另建一行，而不是原地改写。
     *
     * <p>缺陷现场：用户把「默认供应商」当普通供应商挂到了自己的档案上（它长得就像一个普通供应商），
     * 之后每次在设置页保存都会**改写这一行的端点与密钥**——那些档案的请求被静默换到另一个网关，
     * 而且不报错。正确动作是另建「默认供应商 (2)」承载设置页的值。
     */
    @Test
    void writeThroughNeverRewritesAProviderReferencedByNonDefaultProfiles() {
        String userCiphertext = CRYPTO.encrypt("sk-user");
        LlmProvider def = provider(5L, LlmProviderService.DEFAULT_PROVIDER_NAME, true, userCiphertext);
        def.setBaseUrl("https://user-channel.test");
        LlmProviderMapper mapper = inMemoryMapper(def);
        LlmProviderService service = service(mapper, profileRefs(5L, false, 2));

        Long providerId = service.resolveDefaultProviderId("OPENAI_COMPATIBLE", BASE_URL,
                CRYPTO.encrypt("sk-settings"), true);

        assertThat(providerId).as("必须另建一行，而不是复用被引用的那一行").isNotEqualTo(5L);
        assertThat(mapper.findById(5L).getBaseUrl())
                .as("用户档案引用的那一行，端点一个字都不能改").isEqualTo("https://user-channel.test");
        assertThat(mapper.findById(5L).getApiKeyEncrypted())
                .as("用户档案引用的那一行，密钥也不能被换掉").isEqualTo(userCiphertext);
        assertThat(mapper.findById(providerId).getName())
                .isEqualTo(LlmProviderService.DEFAULT_PROVIDER_NAME + " (2)");
        assertThat(mapper.findById(providerId).getBaseUrl()).isEqualTo(BASE_URL);
    }

    /**
     * B2 的边界：{@code is_default} 为 NULL 的存量脏行同样算「在引用」，护栏必须拦住。
     *
     * <p>这条同时钉住实现细节：谓词不能写成 SQL 的 {@code IS_DEFAULT <> 1}——它在 NULL 上求值为
     * {@code NULL}，那些行会被静默漏掉，正好是本护栏最该拦住的一类。
     */
    @Test
    void nullIsDefaultStillCountsAsAReferenceForTheRewriteGuard() {
        LlmProvider def = provider(5L, LlmProviderService.DEFAULT_PROVIDER_NAME, true, CRYPTO.encrypt("sk-user"));
        LlmProviderMapper mapper = inMemoryMapper(def);
        LlmProviderService service = service(mapper, profileRefs(5L, null, 1));

        assertThat(service.resolveDefaultProviderId("OPENAI_COMPATIBLE", BASE_URL, CRYPTO.encrypt("sk-new"), true))
                .isNotEqualTo(5L);
    }

    /** 只有默认档案引用 ⇒ 允许原地改写：这正是设置页写透的正常形态，护栏不得误伤。 */
    @Test
    void writeThroughStillRewritesTheDefaultRowWhenOnlyTheDefaultProfileUsesIt() {
        LlmProvider def = provider(5L, LlmProviderService.DEFAULT_PROVIDER_NAME, true, CRYPTO.encrypt("sk-old"));
        def.setBaseUrl("https://old.test");
        LlmProviderMapper mapper = inMemoryMapper(def);
        LlmProviderService service = service(mapper, profileRefs(5L, true, 1));

        assertThat(service.resolveDefaultProviderId("OPENAI_COMPATIBLE", BASE_URL, CRYPTO.encrypt("sk-new"), true))
                .isEqualTo(5L);
        assertThat(mapper.findById(5L).getBaseUrl()).isEqualTo(BASE_URL);
    }

    // ---------- B1：回填口径「有 key 即启用」 ----------

    /**
     * B1：回填入口不看 {@code llm_config.enabled}，只看「有没有 key」。
     *
     * <p>{@code llm_config.enabled=false} 表示「整站关掉 AI」。第一版把它当成供应商的启用位，
     * 于是「AI 关着的库」回填出来的供应商全是停用——那些档案在故障切换链上**整组静默消失**：
     * 界面上档案都在、模型名也对，但每个请求都失败。
     */
    @Test
    void backfillEntryEnablesProvidersByKeyPresenceOnly() {
        LlmProviderService withKey = service(inMemoryMapper());
        withKey.resolveDefaultProviderIdForBackfill("OPENAI_COMPATIBLE", BASE_URL, CRYPTO.encrypt(LONG_KEY));
        assertThat(withKey.list().get(0).enabled())
                .as("有 key 就必须启用（与 llm_config 的开关无关）").isTrue();

        LlmProviderService keyless = service(inMemoryMapper());
        keyless.resolveDefaultProviderIdForBackfill("OPENAI_COMPATIBLE", "https://keyless.test", null);
        assertThat(keyless.list().get(0).enabled())
                .as("没有 key 一律停用，守住「启用必须有 key」的不变量").isFalse();
    }

    // ---------- M-4：toString 不得带出明文密钥 ----------

    /**
     * M-4：持有明文密钥的 record 必须手写 {@code toString()}。
     *
     * <p>不是洁癖：诊断线上问题时最常见的一手就是 {@code log.warn("...{}", request)}，
     * 一旦那个调用点出现，密钥就直接落进日志文件，而且**很久以后才会被发现**。
     */
    @Test
    void toStringOfKeyHoldersNeverCarriesThePlaintextKey() {
        assertThat(request("n", "OPENAI_COMPATIBLE", BASE_URL, LONG_KEY, null, true).toString())
                .doesNotContain(LONG_KEY).contains("apiKey=***");
        assertThat(new LlmProviderService.RuntimeProvider(true, "OPENAI_COMPATIBLE", BASE_URL, LONG_KEY).toString())
                .doesNotContain(LONG_KEY).contains("apiKey=***");
    }

    // ---------- M-2：解不开的密文 ----------

    /**
     * M-2：密文存在却解不开时，视图给的是**可行动的**提示，而不是「未配置」，且不抛异常。
     *
     * <p>「未配置」会把管理员引向「我没填过 key」，而真正的原因是 {@code APP_SECRET_KEY} 变了
     * ——两个方向的修复动作完全不同。
     */
    @Test
    void undecryptableViewShowsTheActionableMaskInsteadOfUnconfigured() {
        LlmProviderMapper mapper = inMemoryMapper(provider(1L, "broken", true, "BROKEN-CIPHERTEXT"));
        LlmProviderService service = service(mapper);

        LlmProviderService.ProviderView view = service.get(1L);

        assertThat(view.hasApiKey()).isFalse();
        assertThat(view.apiKeyMasked()).isEqualTo(LlmProviderService.UNDECRYPTABLE_MASK);
        assertThat(service.runtime(mapper.findById(1L)).available())
                .as("运行时不抛，只判不可用（否则一条脏行会带走整条故障切换链）")
                .isFalse();
    }

    /** 解不开密钥的行不参与三元组匹配：不能把它当成「无 key」而把无 key 的档案挂上去。 */
    @Test
    void tripleMatchingSkipsRowsWhoseKeyCannotBeDecrypted() {
        LlmProviderMapper mapper = inMemoryMapper(provider(1L, "broken", true, "BROKEN-CIPHERTEXT"));

        assertThat(service(mapper).attachOrCreateProvider("OPENAI_COMPATIBLE", BASE_URL, null, "nexus.bx9y.com.cn"))
                .as("解不开的行无法证明是同一个通道，必须跳过").isNotEqualTo(1L);
    }

    // ---------- §2.2 模型发现的三条边界 ----------

    @Test
    void discoverModelsFailsFastWithoutApiKey() {
        assertThatThrownBy(() -> service(inMemoryMapper(provider(1L, "keyless", false, null))).discoverModels(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置 API Key");
    }

    @Test
    void discoverModelsReportsUndecryptableKeysDistinctly() {
        assertThatThrownBy(() -> service(inMemoryMapper(provider(1L, "broken", true, "BROKEN-CIPHERTEXT")))
                .discoverModels(1L))
                .isInstanceOf(BusinessException.class)
                .as("必须与「未配置」区分开：两者的修复动作完全不同")
                .hasMessageContaining("无法解密");
    }

    @Test
    void discoverModelsRejectsMissingProvider() {
        assertThatThrownBy(() -> service(inMemoryMapper()).discoverModels(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商不存在");
    }

    // ---------- 脚手架 ----------

    private static LlmProviderService.ProviderRequest request(String name, String provider, String baseUrl,
                                                              String apiKey, Boolean clearApiKey, Boolean enabled) {
        return new LlmProviderService.ProviderRequest(name, provider, baseUrl, apiKey, clearApiKey, enabled);
    }

    private static LlmProvider provider(Long id, String name, boolean enabled, String apiKeyEncrypted) {
        LlmProvider provider = new LlmProvider();
        provider.setId(id);
        provider.setName(name);
        provider.setProvider("OPENAI_COMPATIBLE");
        provider.setBaseUrl(BASE_URL);
        provider.setApiKeyEncrypted(apiKeyEncrypted);
        provider.setEnabled(enabled);
        return provider;
    }

    private static LlmProviderService service(LlmProviderMapper mapper) {
        return service(mapper, profileCount(0L));
    }

    private static LlmProviderService service(LlmProviderMapper mapper, LlmProfileMapper profileMapper) {
        return new LlmProviderService(mapper, profileMapper, CRYPTO, new ModelDiscoveryClient());
    }

    private static LlmProfileMapper profileCount(long count) {
        LlmProfileMapper profileMapper = mock(LlmProfileMapper.class);
        when(profileMapper.count(any())).thenReturn(count);
        when(profileMapper.select(any())).thenReturn(List.of());
        return profileMapper;
    }

    /**
     * 预置「挂在某供应商上的档案」：用来打 B2 的改写护栏。
     *
     * @param isDefault 传 {@code null} 模拟存量脏行（{@code IS_DEFAULT} 为 NULL）
     */
    private static LlmProfileMapper profileRefs(Long providerId, Boolean isDefault, int count) {
        LlmProfileMapper profileMapper = mock(LlmProfileMapper.class);
        List<LlmProfile> attached = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            LlmProfile profile = new LlmProfile();
            profile.setId(100L + index);
            profile.setName("ref-" + index);
            profile.setProviderId(providerId);
            profile.setIsDefault(isDefault);
            attached.add(profile);
        }
        when(profileMapper.select(any())).thenReturn(attached);
        when(profileMapper.count(any())).thenReturn((long) count);
        return profileMapper;
    }

    /**
     * 内存表替身：insert 会真的加进列表并回填 id，于是「第二次调用会不会命中已有行」是可测的。
     *
     * <p>预置数据必须从 {@code preset} 传入，**不能**用 {@code mapper.insert(...)} 造：
     * 需要预置的用例紧接着就会 {@code verify(mapper, never()).insert(...)}（断言「复用了已有行、
     * 没有新建」），拿被监视的方法去造前提会把断言自己弄红。
     */
    private static LlmProviderMapper inMemoryMapper(LlmProvider... preset) {
        LlmProviderMapper mapper = mock(LlmProviderMapper.class);
        List<LlmProvider> rows = new ArrayList<>(List.of(preset));
        when(mapper.findAll()).thenAnswer(invocation -> List.copyOf(rows));
        when(mapper.findById(any())).thenAnswer(invocation -> rows.stream()
                .filter(row -> row.getId().equals(invocation.getArgument(0))).findFirst().orElse(null));
        when(mapper.findByName(any())).thenAnswer(invocation -> rows.stream()
                .filter(row -> row.getName().equals(invocation.getArgument(0))).findFirst().orElse(null));
        when(mapper.insert(any(LlmProvider.class))).thenAnswer(invocation -> {
            LlmProvider row = invocation.getArgument(0);
            // 显式给了 id 的（用例预置的行）保持原样，其余按插入顺序发号
            if (row.getId() == null) row.setId((long) (rows.size() + 1));
            rows.add(row);
            return 1;
        });
        when(mapper.updateById(any(LlmProvider.class))).thenReturn(1);
        when(mapper.deleteById(any())).thenReturn(1);
        return mapper;
    }
}
