package ink.icoding.wechat.article.agent;

import ink.icoding.wechat.article.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 智能体定义 CRUD（skills-agent-plan 4.2 / 5.6）。
 * 内置 agent 可改 persona/toolKeys/skillIds/档案，但 code/stage 不可改、不可删（可克隆）。
 */
@Service
public class AgentDefinitionService {
    private static final Set<String> STAGES = Set.of(
            "EDITOR", "SCHEDULED_SINGLE", "RESEARCH", "WRITING", "ILLUSTRATION", "REVIEW", "COORDINATE");
    private static final String DUPLICATE_SUFFIX = "副本";

    private final AgentDefinitionMapper mapper;
    private final ToolRegistry toolRegistry;
    private final LlmProfileService llmProfileService;

    public AgentDefinitionService(AgentDefinitionMapper mapper, ToolRegistry toolRegistry,
                                  LlmProfileService llmProfileService) {
        this.mapper = mapper;
        this.toolRegistry = toolRegistry;
        this.llmProfileService = llmProfileService;
    }

    public List<AgentDefinition> list(String stage, Boolean enabled) {
        return mapper.findAll().stream()
                .filter(agent -> stage == null || stage.isBlank() || stage.equals(agent.getStage()))
                .filter(agent -> enabled == null || enabled.equals(agent.getEnabled()))
                .toList();
    }

    public AgentDefinition required(Long id) {
        AgentDefinition agent = mapper.findById(id);
        if (agent == null) throw new BusinessException("智能体不存在");
        return agent;
    }

    /** 按 code 取启用的内置定义（两链路默认装配用）；缺失返回 null。 */
    public AgentDefinition findByCode(String code) {
        return mapper.findByCode(code);
    }

    @Transactional
    public AgentDefinition create(AgentRequest request, Long userId) {
        AgentDefinition agent = new AgentDefinition();
        apply(agent, request, false);
        validateCodeUnique(agent.getCode(), null);
        validateNameUnique(agent.getName(), null);
        agent.setIsBuiltin(false);
        agent.setBuiltinKey(null);
        agent.setCreatedBy(userId);
        agent.setCreatedAt(java.time.LocalDateTime.now());
        agent.setUpdatedAt(java.time.LocalDateTime.now());
        mapper.insert(agent);
        return required(agent.getId());
    }

    @Transactional
    public AgentDefinition update(Long id, AgentRequest request) {
        AgentDefinition agent = required(id);
        apply(agent, request, Boolean.TRUE.equals(agent.getIsBuiltin()));
        validateNameUnique(agent.getName(), id);
        agent.setUpdatedAt(java.time.LocalDateTime.now());
        mapper.updateById(agent);
        return required(id);
    }

    @Transactional
    public void delete(Long id) {
        AgentDefinition agent = required(id);
        if (Boolean.TRUE.equals(agent.getIsBuiltin())) {
            throw new BusinessException("内置智能体不可删除，可克隆为自定义副本后修改");
        }
        mapper.deleteById(id);
    }

    @Transactional
    public AgentDefinition duplicate(Long id, Long userId) {
        AgentDefinition source = required(id);
        AgentDefinition copy = new AgentDefinition();
        copy.setCode(uniqueDuplicateCode(source.getCode()));
        copy.setName(uniqueDuplicateName(source.getName()));
        copy.setStage(source.getStage());
        copy.setPersona(source.getPersona());
        copy.setToolKeys(source.getToolKeys());
        copy.setSkillIds(source.getSkillIds());
        copy.setLlmProfileId(source.getLlmProfileId());
        copy.setTemperature(source.getTemperature());
        copy.setMaxTokens(source.getMaxTokens());
        copy.setEnabled(true);
        copy.setIsBuiltin(false);
        copy.setBuiltinKey(null);
        copy.setCreatedBy(userId);
        copy.setCreatedAt(java.time.LocalDateTime.now());
        copy.setUpdatedAt(java.time.LocalDateTime.now());
        mapper.insert(copy);
        return required(copy.getId());
    }

    /** builtin=true 时 code/stage 锁定（方案 5.6）。 */
    private void apply(AgentDefinition agent, AgentRequest request, boolean builtin) {
        if (request.name() == null || request.name().isBlank()) throw new BusinessException("请填写智能体名称");
        if (request.persona() == null || request.persona().isBlank()) throw new BusinessException("请填写人设");
        List<String> groups = request.toolKeys() == null ? List.of() : request.toolKeys();
        toolRegistry.validate(groups);
        if (groups.isEmpty()) {
            // 无工具 = 该智能体什么都做不了（工具是唯一产出通道），直接拒绝，避免配置出「空转智能体」
            throw new BusinessException("请至少勾选一个工具组，否则该智能体无法产出任何结果");
        }
        if (!builtin) {
            if (request.code() == null || request.code().isBlank()) throw new BusinessException("请填写智能体标识");
            String code = request.code().strip();
            if (!code.matches("[a-z0-9_]{3,50}")) {
                throw new BusinessException("智能体标识只能使用小写字母、数字和下划线（3-50 位）");
            }
            // 注意：Set.of(...) 的 contains(null) 会抛 NPE，必须先判空，否则缺 stage 的请求变成 500
            if (request.stage() == null || !STAGES.contains(request.stage())) {
                throw new BusinessException("不支持的智能体阶段：" + request.stage());
            }
            agent.setCode(code);
            agent.setStage(request.stage());
        }
        if (request.llmProfileId() != null) llmProfileService.required(request.llmProfileId());
        agent.setName(request.name().strip());
        agent.setPersona(request.persona().strip());
        agent.setToolKeys(groups.isEmpty() ? null : toJson(groups));
        agent.setSkillIds(normalizeSkillIds(request.skillIds()));
        agent.setLlmProfileId(request.llmProfileId());
        agent.setTemperature(request.temperature());
        agent.setMaxTokens(request.maxTokens());
        agent.setEnabled(request.enabled() == null || request.enabled());
    }

    private void validateCodeUnique(String code, Long excludeId) {
        AgentDefinition existing = mapper.findByCode(code);
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw new BusinessException("智能体标识已存在：" + code);
        }
    }

    private void validateNameUnique(String name, Long excludeId) {
        for (AgentDefinition agent : mapper.findAll()) {
            // 脏数据容错：库里 name 可能为 null，用 Objects.equals 避免 NPE
            if (java.util.Objects.equals(name, agent.getName())
                    && !java.util.Objects.equals(agent.getId(), excludeId)) {
                throw new BusinessException("智能体名称已存在：" + name);
            }
        }
    }

    private String uniqueDuplicateCode(String sourceCode) {
        String base = sourceCode + "_copy";
        String candidate = base;
        int index = 2;
        while (mapper.findByCode(candidate) != null) {
            candidate = base + index;
            index++;
        }
        return candidate;
    }

    private String uniqueDuplicateName(String sourceName) {
        String base = sourceName + DUPLICATE_SUFFIX;
        String candidate = base;
        int index = 2;
        while (existsByName(candidate)) {
            candidate = base + index;
            index++;
        }
        return candidate;
    }

    private boolean existsByName(String name) {
        for (AgentDefinition agent : mapper.findAll()) {
            if (java.util.Objects.equals(name, agent.getName())) return true;
        }
        return false;
    }

    /** skill_ids 列表归一为逗号分隔字符串（与任务/账号/文章 skillIds 同格式）。 */
    static String normalizeSkillIds(List<Long> skillIds) {
        return ink.icoding.wechat.article.account.WechatAccountService.skillIdsOrNull(skillIds);
    }

    static String toJson(List<String> values) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(values);
        } catch (Exception exception) {
            throw new BusinessException("工具组序列化失败");
        }
    }

    /** 解析 tool_keys JSON 数组字符串；空/非法返回空列表（容错）。 */
    public static List<String> parseToolKeys(String toolKeysJson) {
        if (toolKeysJson == null || toolKeysJson.isBlank()) return List.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(toolKeysJson, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    public record AgentRequest(String code, String name, String stage, String persona,
                               List<String> toolKeys, List<Long> skillIds, Long llmProfileId,
                               java.math.BigDecimal temperature, Integer maxTokens, Boolean enabled) {
    }
}
