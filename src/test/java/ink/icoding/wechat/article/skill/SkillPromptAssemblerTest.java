package ink.icoding.wechat.article.skill;

import ink.icoding.wechat.article.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SkillPromptAssembler 单测（skills-agent-plan 5.2 / 8.1）：
 * 优先级并集、维度分组顺序、LAYOUT 单注入+ignored 返回、保底 default_layout、
 * EDITOR 过滤 MARKFLOW、defaultStyle 注入、60000 上限。
 */
class SkillPromptAssemblerTest {
    private SkillMapper skillMapper;
    private MarkFlowRenderService renderService;
    private SkillPromptAssembler assembler;

    @BeforeEach
    void setUp() {
        skillMapper = mock(SkillMapper.class);
        renderService = mock(MarkFlowRenderService.class);
        assembler = new SkillPromptAssembler(skillMapper, renderService);
    }

    private Skill skill(long id, String name, String dimension, String content) {
        return skill(id, name, dimension, content, "PROMPT");
    }

    private Skill skill(long id, String name, String dimension, String content, String engine) {
        Skill skill = new Skill();
        skill.setId(id);
        skill.setName(name);
        skill.setDimension(dimension);
        skill.setContent(content);
        skill.setEngine(engine);
        skill.setEnabled(true);
        return skill;
    }

    private void stubDb(List<Skill> skills) {
        when(skillMapper.findByIds(anyList())).thenReturn(skills);
        when(skillMapper.findByBuiltinKey(anyString())).thenAnswer(
                invocation -> SkillSeeder.seeds().stream()
                        .filter(seed -> seed.builtinKey().equals(invocation.getArgument(0, String.class)))
                        .findFirst()
                        .map(seed -> skill(-1, seed.name(), seed.dimension(), seed.content(), seed.engine())));
    }

    private SkillContext scheduledContext(List<Long> taskIds) {
        return new SkillContext(null, taskIds, null, null, null, SkillContext.Scene.SCHEDULED);
    }

    @Test
    void mergesSkillIdsWithPriorityOrder() {
        // agent < account < task 顺序并集
        when(skillMapper.findByIds(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            return List.of(
                    skill(ids.get(0), "智能体技能", "WRITING", "agent 内容"),
                    skill(ids.get(1), "账号技能", "LANGUAGE", "account 内容"),
                    skill(ids.get(2), "任务技能", "TOPIC", "task 内容"));
        });
        SkillContext context = new SkillContext(null, List.of(30L), List.of(20L), List.of(10L),
                null, SkillContext.Scene.SCHEDULED);
        SkillPromptResult result = assembler.assemble(context);
        // 维度注入顺序按 3.3 白名单：TOPIC 在 WRITING 之前，WRITING 在 LANGUAGE 之前
        assertThat(result.prompt().indexOf("【选题策略】")).isLessThan(result.prompt().indexOf("【写作风格】"));
        assertThat(result.prompt().indexOf("【写作风格】")).isLessThan(result.prompt().indexOf("【语言习惯】"));
        assertThat(result.prompt()).contains("agent 内容").contains("account 内容").contains("task 内容");
    }

    @Test
    void duplicateIdsAreDeduplicated() {
        when(skillMapper.findByIds(anyList())).thenReturn(List.of(
                skill(7L, "任务技能", "WRITING", "task 内容")));
        SkillContext context = new SkillContext(null, List.of(7L), List.of(7L), List.of(7L),
                null, SkillContext.Scene.SCHEDULED);
        SkillPromptResult result = assembler.assemble(context);
        assertThat(result.prompt().split("task 内容").length - 1).isEqualTo(1);
    }

    @Test
    void layoutSingleInjectionAndIgnoredReport() {
        // article > task > account：account LAYOUT 与 task LAYOUT 均被丢弃，仅保留优先级最高的 article 级
        when(skillMapper.findByIds(anyList())).thenReturn(List.of(
                skill(20L, "账号排版", "LAYOUT", "账号版式"),
                skill(30L, "任务排版", "LAYOUT", "任务版式"),
                skill(40L, "文章排版", "LAYOUT", "文章版式")));
        SkillContext context = new SkillContext(List.of(40L), List.of(30L), List.of(20L), null, null,
                SkillContext.Scene.EDITOR);
        SkillPromptResult result = assembler.assemble(context);
        assertThat(result.prompt()).contains("文章版式");
        assertThat(result.prompt()).doesNotContain("任务版式").doesNotContain("账号版式");
        assertThat(result.ignoredLayoutSkills()).extracting(Skill::getName)
                .containsExactly("任务排版", "账号排版");
    }

    @Test
    void fallsBackToDefaultLayoutWhenNoLayoutSkill() {
        when(skillMapper.findByIds(anyList())).thenReturn(List.of(
                skill(1L, "教程体", "WRITING", "教程内容")));
        SkillContext context = scheduledContext(List.of(1L));
        SkillPromptResult result = assembler.assemble(context);
        assertThat(result.prompt()).contains("【排版模板】");
        // 保底取 DB 内置 default_layout（stub 返回 seed 内容，其中含视觉模板标题）
        assertThat(result.prompt()).contains("公众号正文视觉模板");
        assertThat(result.engine()).isEqualTo(LayoutEngine.PROMPT);
        assertThat(result.ignoredLayoutSkills()).isEmpty();
        // 不需要渲染服务
        verify(renderService, never()).fetchSyntaxGuide();
    }

    @Test
    void fallsBackToCodeConstantWhenDbUnavailable() {
        when(skillMapper.findByIds(anyList())).thenThrow(new RuntimeException("db down"));
        when(skillMapper.findByBuiltinKey(anyString())).thenThrow(new RuntimeException("db down"));
        SkillPromptResult result = assembler.assemble(scheduledContext(List.of(1L)));
        assertThat(result.prompt()).contains("【排版模板】");
        assertThat(result.prompt()).contains("不要使用 ul、ol、dl、table");
        assertThat(result.engine()).isEqualTo(LayoutEngine.PROMPT);
    }

    @Test
    void editorSceneKeepsMarkflowSkillsAfterPhase4() {
        // 第④期起：编辑器链路支持 render_markflow，EDITOR 场景不再过滤 MARKFLOW 技能
        when(skillMapper.findByIds(anyList())).thenReturn(List.of(
                skill(1L, "教程体", "WRITING", "教程内容"),
                skill(2L, "MarkFlow 版式", "LAYOUT", "渲染式版式", "MARKFLOW")));
        when(renderService.fetchSyntaxGuide()).thenReturn("MarkFlow 语法指令全文");
        SkillContext context = new SkillContext(List.of(1L, 2L), null, null, null, null,
                SkillContext.Scene.EDITOR);
        SkillPromptResult result = assembler.assemble(context);
        assertThat(result.prompt()).contains("教程内容").contains("渲染式版式")
                .contains("MarkFlow 语法指令全文");
        assertThat(result.engine()).isEqualTo(LayoutEngine.MARKFLOW);
        assertThat(result.ignoredLayoutSkills()).isEmpty();
    }

    @Test
    void scheduledSceneKeepsMarkflowAndFetchesSyntaxGuide() {
        when(skillMapper.findByIds(anyList())).thenReturn(List.of(
                skill(2L, "MarkFlow 版式", "LAYOUT", "渲染式版式说明", "MARKFLOW")));
        when(renderService.fetchSyntaxGuide()).thenReturn("MarkFlow 语法指令全文");
        SkillPromptResult result = assembler.assemble(scheduledContext(List.of(2L)));
        assertThat(result.prompt()).contains("渲染式版式说明").contains("MarkFlow 语法指令全文");
        assertThat(result.engine()).isEqualTo(LayoutEngine.MARKFLOW);
        assertThat(result.ignoredLayoutSkills()).isEmpty();
        verify(renderService).fetchSyntaxGuide();
    }

    @Test
    void markflowGuideFetchFailurePropagates() {
        when(skillMapper.findByIds(anyList())).thenReturn(List.of(
                skill(2L, "MarkFlow 版式", "LAYOUT", "渲染式版式说明", "MARKFLOW")));
        when(renderService.fetchSyntaxGuide()).thenThrow(new BusinessException("渲染服务不可用"));
        assertThatThrownBy(() -> assembler.assemble(scheduledContext(List.of(2L))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("渲染服务不可用");
    }

    @Test
    void markflowFixedThemeInjectedIntoPrompt() {
        Skill markflow = skill(2L, "固定主题", "LAYOUT", "渲染式版式", "MARKFLOW");
        markflow.setEngineConfig("{\"accentMode\":\"FIXED\",\"accent\":\"#e74c3c\",\"dark\":\"#c0392b\"}");
        when(skillMapper.findByIds(anyList())).thenReturn(List.of(markflow));
        when(renderService.fetchSyntaxGuide()).thenReturn("语法指令");
        SkillPromptResult result = assembler.assemble(scheduledContext(List.of(2L)));

        assertThat(result.prompt()).contains("主题色策略：FIXED")
                .contains("accent=#e74c3c").contains("dark=#c0392b");
    }

    @Test
    void markflowAutoThemeInjectedIntoPrompt() {
        Skill markflow = skill(2L, "自动主题", "LAYOUT", "渲染式版式", "MARKFLOW");
        markflow.setEngineConfig("{\"accentMode\":\"AUTO\"}");
        when(skillMapper.findByIds(anyList())).thenReturn(List.of(markflow));
        when(renderService.fetchSyntaxGuide()).thenReturn("语法指令");
        SkillPromptResult result = assembler.assemble(scheduledContext(List.of(2L)));

        assertThat(result.prompt()).contains("主题色策略：AUTO");
    }

    @Test
    void malformedEngineConfigIgnored() {
        Skill markflow = skill(2L, "坏配置", "LAYOUT", "渲染式版式", "MARKFLOW");
        markflow.setEngineConfig("not-json");
        when(skillMapper.findByIds(anyList())).thenReturn(List.of(markflow));
        when(renderService.fetchSyntaxGuide()).thenReturn("语法指令");
        SkillPromptResult result = assembler.assemble(scheduledContext(List.of(2L)));

        assertThat(result.prompt()).doesNotContain("主题色策略");
        assertThat(result.engine()).isEqualTo(LayoutEngine.MARKFLOW);
    }

    @Test
    void accountDefaultStyleInjected() {
        when(skillMapper.findByIds(anyList())).thenReturn(List.of());
        SkillContext context = new SkillContext(null, null, null, null, "清爽专业", SkillContext.Scene.SCHEDULED);
        SkillPromptResult result = assembler.assemble(context);
        assertThat(result.prompt()).contains("账号默认风格：清爽专业");
    }

    @Test
    void totalLengthOverLimitThrows() {
        // content 合计 > 60000：单条 30001 × 2 = 60002（60000 恰好等于上限不抛）
        String big = "x".repeat(30001);
        when(skillMapper.findByIds(anyList())).thenReturn(List.of(
                skill(1L, "大技能A", "WRITING", big),
                skill(2L, "大技能B", "TOPIC", big)));
        assertThatThrownBy(() -> assembler.assemble(scheduledContext(List.of(1L, 2L))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("60000");
    }

    @Test
    void lengthJustUnderLimitPasses() {
        String big = "x".repeat(59000);
        when(skillMapper.findByIds(anyList())).thenReturn(List.of(
                skill(1L, "大技能", "WRITING", big)));
        SkillPromptResult result = assembler.assemble(scheduledContext(List.of(1L)));
        assertThat(result.prompt()).contains(big);
    }

    @Test
    void dimensionOrderFollowsWhitelistWithLayoutLast() {
        when(skillMapper.findByIds(anyList())).thenReturn(List.of(
                skill(1L, "排版", "LAYOUT", "版式内容"),
                skill(2L, "核查", "FACT_CHECK", "核查内容"),
                skill(3L, "其他", "OTHER", "其他内容"),
                skill(4L, "读者", "AUDIENCE", "读者内容")));
        SkillPromptResult result = assembler.assemble(scheduledContext(List.of(1L, 2L, 3L, 4L)));
        String prompt = result.prompt();
        assertThat(prompt.indexOf("【目标读者】")).isLessThan(prompt.indexOf("【事实核查】"));
        assertThat(prompt.indexOf("【事实核查】")).isLessThan(prompt.indexOf("【其他】"));
        assertThat(prompt.indexOf("【其他】")).isLessThan(prompt.indexOf("【排版模板】"));
        assertThat(prompt).endsWith("\n");
    }

    @Test
    void disabledAndMissingSkillsAreSilentlySkipped() {
        Skill disabled = skill(1L, "停用技能", "WRITING", "停用内容");
        disabled.setEnabled(false);
        when(skillMapper.findByIds(anyList())).thenReturn(List.of(disabled));
        SkillPromptResult result = assembler.assemble(scheduledContext(List.of(1L, 99L)));
        assertThat(result.prompt()).doesNotContain("停用内容");
        assertThat(result.prompt()).contains("公众号正文视觉模板");
    }
}
