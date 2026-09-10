package ink.icoding.wechat.article.skill;

/**
 * LAYOUT 维度排版引擎双轨（skills-agent-plan 5.10.2）。
 * 同一上下文最终生效的引擎以「排序最前的 LAYOUT Skill」为准，禁止双引擎混用——
 * 两个引擎的正文产物范式互斥：一个产内联样式 HTML、一个产 MarkFlow 语法 Markdown。
 */
public enum LayoutEngine {
    /** 指令式排版（默认，现状行为）：Skill 文本即版式指令，Agent 直接产出内联样式 HTML。 */
    PROMPT,
    /** 渲染式排版：Agent 产出 MarkFlow 扩展语法 Markdown，渲染发生在工具边界（服务端），LLM 上下文零 HTML。 */
    MARKFLOW
}
