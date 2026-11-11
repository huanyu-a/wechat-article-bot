package ink.icoding.wechat.article.assistant.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.wechat.article.agent.LlmProfileService;
import ink.icoding.wechat.article.agent.LlmProviderService;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 全局助手的模型档案工具（docs/dev/global-assistant-plan.md §4.3 / §5.4）。
 *
 * <p><b>API Key 不在参数里，这是刻意的</b>（plan §5.4）：对话内容会落 {@code assistant_message} 表、
 * 进 SSE 帧、进模型上下文（第三方）。用户一旦在聊天框里贴出一个密钥，它就同时出现在三处，
 * 而其中两处不受本项目的密钥加密保护。因此 {@code create_llm_provider} / {@code update_llm_provider}
 * 只建/改「端点」，需要密钥时工具返回一句引导，让用户去「模型档案」页填写——
 * 那里走的是 {@code CryptoService} 加密的写入路径。
 *
 * <p>这条约束由 {@code AssistantModelToolsSecretTest} 用反射固化：任何人给参数类加回
 * {@code apiKey} 字段都会让测试变红，而不是让密钥悄悄流进对话。
 *
 * <p>读工具只返回 {@link LlmProviderService.ProviderView} / {@link LlmProfileService.ProfileView}：
 * 这两个视图里本来就只有 {@code apiKeyMasked}（{@code maskApiKey} 产出的掩码），
 * 没有明文字段可泄漏——工具的「不回显密钥」不是靠自觉，是靠类型里没有那个字段。
 */
public final class AssistantModelTools {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 可用的供应商类型（与 {@code LlmProviderService} 的校验清单同源，见 plan §4.3）。 */
    private static final Set<String> PROVIDERS = Set.of("OPENAI_COMPATIBLE", "OPENAI_RESPONSES", "ANTHROPIC");

    /** 模型清单回传上限：网关动辄数千个模型名，整段塞进上下文会挤掉真正的任务。 */
    private static final int MAX_MODELS = 200;

    /** 密钥必须由人去「模型档案」页填写的统一引导语（两个写工具共用，避免两处措辞漂移）。 */
    private static final String SECRET_GUIDANCE =
            "API Key 不在对话里传递：请到「模型档案 → 模型供应商」页填写密钥（那里会用服务端主密钥加密保存）。"
                    + "本工具只负责建/改供应商的名称与端点，建好后你可以继续为它创建模型档案。";

    private AssistantModelTools() {
    }

    /** 八个模型工具：两个列表 + 一个发现 + 五个写（不含删除）。 */
    public static List<Tool> all(LlmProviderService providerService, LlmProfileService profileService,
                                 Long userId) {
        return List.of(new ListLlmProvidersTool(providerService),
                new ListLlmProfilesTool(profileService),
                new DiscoverLlmModelsTool(providerService),
                new CreateLlmProviderTool(providerService, userId),
                new UpdateLlmProviderTool(providerService),
                new CreateLlmProfileTool(profileService, userId),
                new UpdateLlmProfileTool(profileService),
                new SetDefaultLlmProfileTool(profileService),
                new SetFallbackLlmProfileTool(profileService),
                new DeleteLlmProfileTool(profileService),
                new DeleteLlmProviderTool(providerService));
    }

    @ToolInfo(name = "list_llm_providers",
            description = "列出所有模型供应商（端点）。返回 id、名称、类型、baseUrl、是否已配置密钥（只给掩码）、"
                    + "启用状态与已建档案数。密钥本身不会返回。要建档案或拉模型清单先用它拿 id。")
    public static class ListLlmProvidersTool implements Tool<ListLlmProvidersParam> {
        private final LlmProviderService providerService;

        public ListLlmProvidersTool(LlmProviderService providerService) {
            this.providerService = providerService;
        }

        @Override
        public String execute(ListLlmProvidersParam param) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (LlmProviderService.ProviderView view : providerService.list()) {
                if (param.getEnabled() != null && !param.getEnabled().equals(view.enabled())) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", view.id());
                row.put("name", view.name());
                row.put("provider", view.provider());
                row.put("baseUrl", view.baseUrl());
                row.put("hasApiKey", view.hasApiKey());
                // 只回传掩码字段（类型里没有明文可比）
                row.put("apiKeyMasked", view.apiKeyMasked());
                row.put("enabled", view.enabled());
                row.put("modelCount", view.modelCount());
                row.put("updatedAt", view.updatedAt() == null ? null : view.updatedAt().toString());
                items.add(row);
            }
            return json(Map.of("count", items.size(), "items", items));
        }
    }

    @Data
    public static class ListLlmProvidersParam extends ToolParam {
        @Param(required = false, description = "true 只看已启用、false 只看已停用，留空全部")
        private Boolean enabled;
    }

    @ToolInfo(name = "list_llm_profiles",
            description = "列出模型档案（哪个模型走哪个供应商），默认返回前 " + AssistantToolBudget.DEFAULT_LIMIT
                    + " 个，返回 total（总数）与 returned（本次条数），可按 enabled 过滤。"
                    + "返回 id、名称、供应商、模型名、是否默认/兜底、启用状态与运行时是否可用。密钥不会返回。"
                    + "条数很多时请带过滤条件或调大 limit，不要把整张表读进来。"
                    + "要切换默认或兜底档案前先用它确认 id。")
    public static class ListLlmProfilesTool implements Tool<ListLlmProfilesParam> {
        private final LlmProfileService profileService;

        public ListLlmProfilesTool(LlmProfileService profileService) {
            this.profileService = profileService;
        }

        @Override
        public String execute(ListLlmProfilesParam param) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (LlmProfileService.ProfileView view : profileService.list()) {
                if (param.getEnabled() != null && !param.getEnabled().equals(view.enabled())) continue;
                // 字段只留「选档案要用的」：apiKeyMasked / providerEnabled / updatedAt 对模型决策没帮助，
                // 却是这行 315 字节里的一半（实测 47 条 14.8 KB）。密钥掩码尤其没必要——
                // 它既不能用于判断可用性（那看 runtimeAvailable），又让人条件反射地想追密钥。
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", view.id());
                row.put("name", view.name());
                row.put("provider", view.providerName());
                row.put("model", view.modelName());
                row.put("default", view.isDefault());
                row.put("fallback", view.isFallback());
                row.put("enabled", view.enabled());
                // runtimeAvailable 是「现在真能发一次请求吗」，比 enabled 更有信息量：
                // 档案启用但供应商没配 key 时它是 false，这解释了「为什么它没被选中」
                row.put("usable", view.runtimeAvailable());
                items.add(row);
            }
            return json(AssistantToolBudget.page(items, param.getLimit(), "模型档案"));
        }
    }

    @Data
    public static class ListLlmProfilesParam extends ToolParam {
        @Param(required = false, description = "true 只看已启用、false 只看已停用，留空全部")
        private Boolean enabled;
        @Param(required = false, description = "返回条数，1 到 " + AssistantToolBudget.MAX_LIMIT
                + "（默认 " + AssistantToolBudget.DEFAULT_LIMIT + "）")
        private Integer limit;
    }

    @ToolInfo(name = "discover_llm_models",
            description = "向供应商拉取它当前提供的模型清单（真实网络请求，可能较慢）。"
                    + "返回最多 " + MAX_MODELS + " 个模型名与总数。建档案时用这里的模型名，不要凭空编造。")
    public static class DiscoverLlmModelsTool implements Tool<DiscoverLlmModelsParam> {
        private final LlmProviderService providerService;

        public DiscoverLlmModelsTool(LlmProviderService providerService) {
            this.providerService = providerService;
        }

        @Override
        public String execute(DiscoverLlmModelsParam param) {
            if (param.getProviderId() == null) {
                throw new IllegalArgumentException("请给出供应商 id（可先用 list_llm_providers 查找）");
            }
            List<String> models = providerService.discoverModels(param.getProviderId());
            if (models == null) models = List.of();
            List<String> capped = models.size() > MAX_MODELS ? models.subList(0, MAX_MODELS) : models;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("providerId", param.getProviderId());
            result.put("total", models.size());
            result.put("returned", capped.size());
            result.put("truncated", models.size() > MAX_MODELS);
            result.put("models", capped);
            return json(result);
        }
    }

    @Data
    public static class DiscoverLlmModelsParam extends ToolParam {
        @Param(description = "供应商 id")
        private Long providerId;
    }

    @ToolInfo(name = "create_llm_provider",
            description = "新建模型供应商（只建端点，不含密钥）：name、provider（OPENAI_COMPATIBLE / OPENAI_RESPONSES / "
                    + "ANTHROPIC）、baseUrl。建好之后必须提示用户去「模型档案」页填 API Key，否则该供应商不可用。"
                    + "本工具不接收密钥，也不要在回答里询问或回显密钥。")
    public static class CreateLlmProviderTool implements Tool<CreateLlmProviderParam> {
        private final LlmProviderService providerService;
        private final Long userId;

        public CreateLlmProviderTool(LlmProviderService providerService, Long userId) {
            this.providerService = providerService;
            this.userId = userId;
        }

        @Override
        public String execute(CreateLlmProviderParam param) {
            String name = blankToNull(param.getName());
            String type = blankToNull(param.getProvider());
            String baseUrl = blankToNull(param.getBaseUrl());
            if (name == null) throw new IllegalArgumentException("请给出供应商名称");
            if (type == null) throw new IllegalArgumentException("请给出供应商类型（" + String.join(" / ", PROVIDERS) + "）");
            if (!PROVIDERS.contains(type)) {
                throw new IllegalArgumentException("不支持的供应商类型：" + type
                        + "，只能是 " + String.join(" / ", PROVIDERS));
            }
            if (baseUrl == null) throw new IllegalArgumentException("请给出 baseUrl（如 https://api.example.com/v1）");
            // apiKey / clearApiKey 传 null：本工具**刻意**不接收密钥（见类注释与 plan §5.4）。
            // enabled 也原样透传 null（不兜底成 true）：LlmProviderService.applyEnableFlag 把 null 视为
            // 「不动这一位」，新建行落成**停用**——这正是「先存端点、再由人补 key」两步流程的前提。
            // 若这里填 true，无 key 的新建必然撞上「启用供应商前必须配置 API Key」，助手就永远建不出
            // 一个等着补 key 的供应商（该服务注释 M-1 记的正是这个历史坑）。
            LlmProviderService.ProviderView created = providerService.create(
                    new LlmProviderService.ProviderRequest(name, type, baseUrl, null, null, param.getEnabled()), userId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("provider", providerRow(created));
            result.put("nextStep", SECRET_GUIDANCE);
            return json(result);
        }
    }

    @Data
    public static class CreateLlmProviderParam extends ToolParam {
        @Param(description = "供应商名称（便于人识别，如「公司网关」）")
        private String name;
        @Param(description = "供应商类型：OPENAI_COMPATIBLE / OPENAI_RESPONSES / ANTHROPIC")
        private String provider;
        @Param(description = "接口地址，如 https://api.example.com/v1")
        private String baseUrl;
        @Param(required = false, description = "是否启用；留空为停用——没有密钥的供应商不能启用，先建端点再由人补 key")
        private Boolean enabled;
    }

    @ToolInfo(name = "update_llm_provider",
            description = "修改已有供应商的名称、类型、baseUrl 或启用状态。未传的字段保持原值。"
                    + "**不接收也不修改 API Key**：密钥只能由用户在「模型档案」页填写。")
    public static class UpdateLlmProviderTool implements Tool<UpdateLlmProviderParam> {
        private final LlmProviderService providerService;

        public UpdateLlmProviderTool(LlmProviderService providerService) {
            this.providerService = providerService;
        }

        @Override
        public String execute(UpdateLlmProviderParam param) {
            if (param.getProviderId() == null) {
                throw new IllegalArgumentException("请给出供应商 id（可先用 list_llm_providers 查找）");
            }
            // 先读再改：ProviderRequest 是**全量覆盖**，未传字段必须由当前值补齐，
            // 否则一次「只改名字」会把 baseUrl 清空——那会让整条链上的档案一起失效。
            LlmProviderService.ProviderView current = findProvider(providerService, param.getProviderId());
            String name = param.getName() == null ? current.name() : blankToNull(param.getName());
            String type = param.getProvider() == null ? current.provider() : blankToNull(param.getProvider());
            String baseUrl = param.getBaseUrl() == null ? current.baseUrl() : blankToNull(param.getBaseUrl());
            Boolean enabled = param.getEnabled() == null ? current.enabled() : param.getEnabled();
            if (name == null) throw new IllegalArgumentException("供应商名称不能为空");
            if (baseUrl == null) throw new IllegalArgumentException("baseUrl 不能为空");
            if (type != null && !PROVIDERS.contains(type)) {
                throw new IllegalArgumentException("不支持的供应商类型：" + type
                        + "，只能是 " + String.join(" / ", PROVIDERS));
            }
            // apiKey=null 且 clearApiKey=null ⇒ LlmProviderService 保留原密钥不动（见其 update 实现）
            LlmProviderService.ProviderView updated = providerService.update(param.getProviderId(),
                    new LlmProviderService.ProviderRequest(name, type, baseUrl, null, null, enabled));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("provider", providerRow(updated));
            result.put("apiKey", "未改动（本工具不接触密钥）");
            return json(result);
        }
    }

    @Data
    public static class UpdateLlmProviderParam extends ToolParam {
        @Param(description = "要修改的供应商 id")
        private Long providerId;
        @Param(required = false, description = "新名称，留空保持原值")
        private String name;
        @Param(required = false, description = "新类型（OPENAI_COMPATIBLE / OPENAI_RESPONSES / ANTHROPIC），留空保持原值")
        private String provider;
        @Param(required = false, description = "新 baseUrl，留空保持原值")
        private String baseUrl;
        @Param(required = false, description = "启用或停用，留空保持原值")
        private Boolean enabled;
    }

    @ToolInfo(name = "create_llm_profile",
            description = "新建模型档案：name、providerId、modelName。供应商必须已存在且已配置密钥，"
                    + "否则运行时不可用。这是「把某个模型接到某个供应商」的绑定，不涉及密钥。")
    public static class CreateLlmProfileTool implements Tool<CreateLlmProfileParam> {
        private final LlmProfileService profileService;
        private final Long userId;

        public CreateLlmProfileTool(LlmProfileService profileService, Long userId) {
            this.profileService = profileService;
            this.userId = userId;
        }

        @Override
        public String execute(CreateLlmProfileParam param) {
            String name = blankToNull(param.getName());
            String modelName = blankToNull(param.getModelName());
            if (name == null) throw new IllegalArgumentException("请给出档案名称");
            if (param.getProviderId() == null) {
                throw new IllegalArgumentException("请给出供应商 id（可先用 list_llm_providers 查找）");
            }
            if (modelName == null) {
                throw new IllegalArgumentException("请给出模型名（可先用 discover_llm_models 查看该供应商有哪些模型）");
            }
            // temperature / maxTokens 传 null：走服务端默认值，不在对话里替用户决定采样参数
            LlmProfileService.ProfileView created = profileService.create(
                    new LlmProfileService.ProfileRequest(name, param.getProviderId(), modelName,
                            param.getEnabled() == null ? Boolean.TRUE : param.getEnabled(),
                            null, null, blankToNull(param.getImageModelName()),
                            param.getIsImageModel() == null ? Boolean.FALSE : param.getIsImageModel()), userId);
            return json(Map.of("profile", profileRow(created)));
        }
    }

    @Data
    public static class CreateLlmProfileParam extends ToolParam {
        @Param(description = "档案名称（如「网关 · deepseek-flash」）")
        private String name;
        @Param(description = "供应商 id（可先用 list_llm_providers 查找）")
        private Long providerId;
        @Param(description = "模型名（如 deepseek-flash；可先用 discover_llm_models 确认）")
        private String modelName;
        @Param(required = false, description = "是否启用，默认 true")
        private Boolean enabled;
        @Param(required = false, description = "若这是图片模型，填它的图片模型名；非图片模型留空")
        private String imageModelName;
        @Param(required = false, description = "true 表示这条档案用于生图/改图，默认 false")
        private Boolean isImageModel;
    }

    @ToolInfo(name = "set_default_llm_profile",
            description = "把某个模型档案设为**默认**档案：所有未绑定档案的智能体都会用它。"
                    + "影响面很大（全站出稿），设置前先与用户确认。")
    public static class SetDefaultLlmProfileTool implements Tool<SetDefaultLlmProfileParam> {
        private final LlmProfileService profileService;

        public SetDefaultLlmProfileTool(LlmProfileService profileService) {
            this.profileService = profileService;
        }

        @Override
        public String execute(SetDefaultLlmProfileParam param) {
            if (param.getProfileId() == null) {
                throw new IllegalArgumentException("请给出档案 id（可先用 list_llm_profiles 查找）");
            }
            return json(Map.of("profile", profileRow(profileService.setDefault(param.getProfileId()))));
        }
    }

    @Data
    public static class SetDefaultLlmProfileParam extends ToolParam {
        @Param(description = "要设为默认的模型档案 id")
        private Long profileId;
    }

    @ToolInfo(name = "set_fallback_llm_profile",
            description = "把某个模型档案设为**兜底**档案:默认档案不可用时自动切到它。设置前先与用户确认。")
    public static class SetFallbackLlmProfileTool implements Tool<SetFallbackLlmProfileParam> {
        private final LlmProfileService profileService;

        public SetFallbackLlmProfileTool(LlmProfileService profileService) {
            this.profileService = profileService;
        }

        @Override
        public String execute(SetFallbackLlmProfileParam param) {
            if (param.getProfileId() == null) {
                throw new IllegalArgumentException("请给出档案 id（可先用 list_llm_profiles 查找）");
            }
            return json(Map.of("profile", profileRow(profileService.setFallback(param.getProfileId()))));
        }
    }

    @Data
    public static class SetFallbackLlmProfileParam extends ToolParam {
        @Param(description = "要设为兜底的模型档案 id")
        private Long profileId;
    }

    @ToolInfo(name = "update_llm_profile",
            description = "修改模型档案：只传要改的字段，未传的保持原值（例如只改 modelName 就只传它）。"
                    + "名称、供应商、模型名、温度、maxTokens、是否图像模型都能改；密钥属于供应商，不在这里。")
    public static class UpdateLlmProfileTool implements Tool<UpdateLlmProfileParam> {
        private final LlmProfileService profileService;

        public UpdateLlmProfileTool(LlmProfileService profileService) {
            this.profileService = profileService;
        }

        @Override
        public String execute(UpdateLlmProfileParam param) {
            if (param.getProfileId() == null) {
                throw new IllegalArgumentException("请给出档案 id（可先用 list_llm_profiles 查找）");
            }
            // 服务层要求整份 ProfileRequest：先取当前值，只覆盖调用方给的字段
            LlmProfileService.ProfileView current = findProfile(profileService, param.getProfileId());
            LlmProfileService.ProfileRequest request = new LlmProfileService.ProfileRequest(
                    param.getName() != null ? param.getName() : current.name(),
                    param.getProviderId() != null ? param.getProviderId() : current.providerId(),
                    param.getModelName() != null ? param.getModelName() : current.modelName(),
                    param.getEnabled() != null ? param.getEnabled() : current.enabled(),
                    param.getTemperature() != null ? param.getTemperature() : current.temperature(),
                    param.getMaxTokens() != null ? param.getMaxTokens() : current.maxTokens(),
                    param.getImageModelName() != null ? param.getImageModelName() : current.imageModelName(),
                    param.getIsImageModel() != null ? param.getIsImageModel() : current.isImageModel());
            return json(profileRow(profileService.update(param.getProfileId(), request)));
        }
    }

    @Data
    public static class UpdateLlmProfileParam extends ToolParam {
        @Param(description = "要修改的档案 id")
        private Long profileId;
        @Param(required = false, description = "新名称")
        private String name;
        @Param(required = false, description = "改挂到哪个供应商 id")
        private Long providerId;
        @Param(required = false, description = "新模型名，如 deepseek-chat")
        private String modelName;
        @Param(required = false, description = "启用/停用")
        private Boolean enabled;
        @Param(required = false, description = "温度 0.0-2.0")
        private java.math.BigDecimal temperature;
        @Param(required = false, description = "maxTokens 256-32768")
        private Integer maxTokens;
        @Param(required = false, description = "图像模型名（该档案用于生图时填）")
        private String imageModelName;
        @Param(required = false, description = "该档案是否为图像模型")
        private Boolean isImageModel;
    }

    @ToolInfo(name = "delete_llm_profile",
            description = "删除模型档案。默认档案不可删除（先把别的档案设为默认）；"
                    + "仍有智能体或任务绑定它时也会被拒绝——那说明它在用，不该删。")
    public static class DeleteLlmProfileTool implements Tool<DeleteLlmProfileParam> {
        private final LlmProfileService profileService;

        public DeleteLlmProfileTool(LlmProfileService profileService) {
            this.profileService = profileService;
        }

        @Override
        public String execute(DeleteLlmProfileParam param) {
            if (param.getProfileId() == null) {
                throw new IllegalArgumentException("请给出档案 id（可先用 list_llm_profiles 查找）");
            }
            LlmProfileService.ProfileView profile = findProfile(profileService, param.getProfileId());
            profileService.delete(param.getProfileId());
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("deleted", true);
            row.put("profileId", profile.id());
            row.put("name", profile.name());
            return json(row);
        }
    }

    @Data
    public static class DeleteLlmProfileParam extends ToolParam {
        @Param(description = "要删除的档案 id")
        private Long profileId;
    }

    @ToolInfo(name = "delete_llm_provider",
            description = "删除模型供应商（端点）。它下面还有档案时会被拒绝——请先删档案或改挂到别处。"
                    + "密钥随供应商一起删除，不可恢复。")
    public static class DeleteLlmProviderTool implements Tool<DeleteLlmProviderParam> {
        private final LlmProviderService providerService;

        public DeleteLlmProviderTool(LlmProviderService providerService) {
            this.providerService = providerService;
        }

        @Override
        public String execute(DeleteLlmProviderParam param) {
            if (param.getProviderId() == null) {
                throw new IllegalArgumentException("请给出供应商 id（可先用 list_llm_providers 查找）");
            }
            LlmProviderService.ProviderView provider = findProvider(providerService, param.getProviderId());
            providerService.delete(param.getProviderId());
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("deleted", true);
            row.put("providerId", provider.id());
            row.put("name", provider.name());
            return json(row);
        }
    }

    @Data
    public static class DeleteLlmProviderParam extends ToolParam {
        @Param(description = "要删除的供应商 id")
        private Long providerId;
    }

    /** 供应商视图 → 上下文友好的一行（不含明文密钥；视图类型里也没有）。 */
    private static Map<String, Object> providerRow(LlmProviderService.ProviderView view) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", view.id());
        row.put("name", view.name());
        row.put("provider", view.provider());
        row.put("baseUrl", view.baseUrl());
        row.put("hasApiKey", view.hasApiKey());
        row.put("apiKeyMasked", view.apiKeyMasked());
        row.put("enabled", view.enabled());
        row.put("modelCount", view.modelCount());
        return row;
    }

    /** 档案视图 → 上下文友好的一行。 */
    private static Map<String, Object> profileRow(LlmProfileService.ProfileView view) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", view.id());
        row.put("name", view.name());
        row.put("providerId", view.providerId());
        row.put("providerName", view.providerName());
        row.put("modelName", view.modelName());
        row.put("isDefault", view.isDefault());
        row.put("isFallback", view.isFallback());
        row.put("enabled", view.enabled());
        row.put("runtimeAvailable", view.runtimeAvailable());
        return row;
    }

    /** 从列表里找出档案视图（{@code LlmProfileService} 没有单行 view 方法，list() 是几十行级别）。 */
    private static LlmProfileService.ProfileView findProfile(LlmProfileService profileService, Long id) {
        for (LlmProfileService.ProfileView view : profileService.list()) {
            if (id.equals(view.id())) return view;
        }
        throw new IllegalArgumentException("模型档案不存在：id=" + id + "（可先用 list_llm_profiles 重新核对）");
    }

    /** 从列表里找出供应商视图（{@code LlmProviderService} 没有单行 view 方法，list() 是个位数行）。 */
    private static LlmProviderService.ProviderView findProvider(LlmProviderService providerService, Long id) {
        for (LlmProviderService.ProviderView view : providerService.list()) {
            if (id.equals(view.id())) return view;
        }
        throw new IllegalArgumentException("供应商不存在：id=" + id + "（可先用 list_llm_providers 重新核对）");
    }

    private static String json(Map<String, Object> value) {
        try {
            // 统一兜底：即便某个工具忘了用 AssistantToolBudget.page，输出也不会撑爆上下文
            return AssistantToolBudget.cap(MAPPER.writeValueAsString(value));
        } catch (Exception exception) {
            throw new IllegalStateException("序列化工具结果失败：" + exception.getMessage(), exception);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}