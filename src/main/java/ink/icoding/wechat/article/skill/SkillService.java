package ink.icoding.wechat.article.skill;

import ink.icoding.wechat.article.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Skill CRUD + 校验 + 克隆（skills-agent-plan 5.6 / 4.1）。
 */
@Service
public class SkillService {
    private static final Set<String> ENGINES = Set.of("PROMPT", "MARKFLOW");
    private static final String DUPLICATE_SUFFIX = "副本";

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final SkillMapper mapper;

    public SkillService(SkillMapper mapper) {
        this.mapper = mapper;
    }

    public List<Skill> list(String dimension, Boolean enabled) {
        List<Skill> skills = mapper.findAll();
        return skills.stream()
                .filter(skill -> dimension == null || dimension.isBlank() || dimension.equals(skill.getDimension()))
                .filter(skill -> enabled == null || enabled.equals(skill.getEnabled()))
                .toList();
    }

    public Skill required(Long id) {
        Skill skill = mapper.findById(id);
        if (skill == null) throw new BusinessException("创作技能不存在");
        return skill;
    }

    @Transactional
    public Skill create(SkillRequest request, Long userId) {
        Skill skill = new Skill();
        apply(skill, request);
        validateNameUnique(skill.getName(), null);
        skill.setIsBuiltin(false);
        skill.setBuiltinKey(null);
        skill.setCreatedBy(userId);
        skill.setCreatedAt(LocalDateTime.now());
        skill.setUpdatedAt(LocalDateTime.now());
        mapper.insert(skill);
        return required(skill.getId());
    }

    @Transactional
    public Skill update(Long id, SkillRequest request) {
        Skill skill = required(id);
        apply(skill, request);
        validateNameUnique(skill.getName(), id);
        skill.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(skill);
        return required(id);
    }

    @Transactional
    public void delete(Long id) {
        Skill skill = required(id);
        if (Boolean.TRUE.equals(skill.getIsBuiltin())) {
            throw new BusinessException("内置技能不可删除，可克隆为自定义副本后修改");
        }
        mapper.deleteById(id);
    }

    /** 克隆为自定义副本：内置与非内置均可克隆；name 加「副本」后缀并保证唯一。 */
    @Transactional
    public Skill duplicate(Long id, Long userId) {
        Skill source = required(id);
        Skill copy = new Skill();
        copy.setName(uniqueDuplicateName(source.getName()));
        copy.setDimension(source.getDimension());
        copy.setDescription(source.getDescription());
        copy.setContent(source.getContent());
        copy.setEngine(source.getEngine());
        copy.setEngineConfig(source.getEngineConfig());
        copy.setEnabled(true);
        copy.setIsBuiltin(false);
        copy.setBuiltinKey(null);
        copy.setCreatedBy(userId);
        copy.setCreatedAt(LocalDateTime.now());
        copy.setUpdatedAt(LocalDateTime.now());
        mapper.insert(copy);
        return required(copy.getId());
    }

    private void apply(Skill skill, SkillRequest request) {
        if (request.name() == null || request.name().isBlank()) throw new BusinessException("请填写技能名称");
        if (!SkillDimensions.isValid(request.dimension())) {
            throw new BusinessException("不支持的技能维度：" + request.dimension());
        }
        String content = request.content() == null ? "" : request.content().strip();
        if (content.isBlank()) throw new BusinessException("请填写技能内容");
        if (content.length() > SkillPromptAssembler.MAX_SKILL_CONTENT_LENGTH) {
            throw new BusinessException("技能内容不能超过 " + SkillPromptAssembler.MAX_SKILL_CONTENT_LENGTH + " 字");
        }
        if (request.description() != null && request.description().length() > 500) {
            throw new BusinessException("技能描述不能超过 500 字");
        }
        skill.setName(request.name().strip());
        skill.setDimension(request.dimension());
        skill.setDescription(blankToNull(request.description()));
        skill.setContent(content);
        skill.setEnabled(request.enabled() == null || request.enabled());
        // engine/engine_config 仅 LAYOUT 维度生效；其他维度一律置空
        if ("LAYOUT".equals(request.dimension())) {
            String engine = request.engine() == null || request.engine().isBlank() ? "PROMPT" : request.engine();
            if (!ENGINES.contains(engine)) throw new BusinessException("不支持的排版引擎：" + engine);
            skill.setEngine(engine);
            skill.setEngineConfig(normalizeEngineConfig(engine, request.engineConfig()));
        } else {
            skill.setEngine(null);
            skill.setEngineConfig(null);
        }
    }

    /**
     * engine_config 以 JSON 字符串存储（TEXT 列存 JSON，5.7 风险对策⑤的既定方案）。
     * 入参容忍两种形态：JSON 对象（前端直接回传对象）与 JSON 字符串（脚本/API 传序列化结果），
     * 统一归一为紧凑 JSON 字符串，避免「前端发对象 → String 字段反序列化 400」的契约错位。
     */
    private String normalizeEngineConfig(String engine, Object engineConfig) {
        if (!"MARKFLOW".equals(engine)) return null;
        if (engineConfig == null) return null;
        if (engineConfig instanceof String text) {
            String trimmed = text.strip();
            if (trimmed.isEmpty()) return null;
            if (!trimmed.startsWith("{")) throw new BusinessException("引擎配置必须是 JSON 对象");
            return trimmed;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = MAPPER.valueToTree(engineConfig);
            if (node == null || !node.isObject()) throw new BusinessException("引擎配置必须是 JSON 对象");
            return node.toString();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("引擎配置必须是 JSON 对象");
        }
    }

    private void validateNameUnique(String name, Long excludeId) {
        Skill existing = mapper.findByName(name);
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw new BusinessException("技能名称已存在：" + name);
        }
    }

    private String uniqueDuplicateName(String sourceName) {
        String base = sourceName + DUPLICATE_SUFFIX;
        String candidate = base;
        int index = 2;
        while (mapper.findByName(candidate) != null) {
            candidate = base + index;
            index++;
        }
        return candidate;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** engineConfig 用 Object 接收：同时兼容 JSON 对象（前端）与 JSON 字符串（脚本）两种写法。 */
    public record SkillRequest(String name, String dimension, String description, String content,
                               String engine, Object engineConfig, Boolean enabled) {
    }
}
