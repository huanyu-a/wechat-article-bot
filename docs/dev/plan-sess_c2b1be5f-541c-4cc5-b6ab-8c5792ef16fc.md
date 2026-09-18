# 计划：保存方案文档（不实施）

## 批准后仅执行以下动作，然后停止

1. 创建 `docs/dev/skills-agent-plan.md`，写入《墨舟 Skills + 多 Agent 架构开发方案》完整文档，包含：
   - 背景与目标
   - 现状分析（探索确认的关键结论，含代码位置引用）
   - 总体架构设计（Skill / Agent 定义 / 执行策略三层概念与架构图）
   - 数据模型设计（skill、agent_definition、llm_profile 三张新表 + 四张表加列的完整字段说明）
   - 后端详细设计（新增包结构、SkillPromptAssembler 组装算法、ToolRegistry 工具注册表、AgentFactory、核心协议与人设分离、三种执行策略 SINGLE/PIPELINE/COORDINATOR、TaskWorkspace 共享工作区、权限矩阵）
   - 前端设计（SkillsView / AgentsView / TasksView / AccountsView / ArticleEditorView / SettingsView 改动点）
   - 四期交付计划（各期范围、工作量估算、验收标准）
   - 测试与质量门禁
   - 风险与对策表
   - 附录：内置 Skill 与内置 Agent 种子清单

2. 按 Handoff 协议更新 `.zcode/handoff/latest.json`（status=completed，summary=方案文档已保存，等待「开始第 N 期」指示）。

## 明确不做的事
- 不修改任何 Java/Vue 代码，不改数据库，不加依赖
- 不开始任何一期的实施工作
- 后续动工以你明确指示（如「开始第①期」）为准