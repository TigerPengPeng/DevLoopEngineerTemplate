# 专业 Agent 目录索引

> 阶段 agent 通过 TODO.md 任务标签 `[type: xxx]` 派发到对应专业 agent。
> 本文件由 registry.yaml 的 `agents` 和 `dispatch` 段映射，修改后须同步。

## Product

| Agent | dispatch_tag | 适用阶段 | 文件 |
|-------|-------------|---------|------|
| 产品经理 | `product` | prd | product/product-manager.md |
| 用户反馈综合分析师 | `feedback` | prd | product/feedback-synthesizer.md |
| Sprint 优先级规划师 | `sprint` | prd | product/sprint-prioritizer.md |
| 趋势研究员 | `trend` | prd | product/trend-researcher.md |
| 行为助推引擎 | `nudge` | prd | product/behavioral-nudge-engine.md |
| 技术文档工程师 | `docs` | arch, regression | product/technical-writer.md |

## Design

| Agent | dispatch_tag | 适用阶段 | 文件 |
|-------|-------------|---------|------|
| UI 设计师 | `ui` | design | design/ui-designer.md |
| 交互设计师 | `interaction` | design | design/interaction-designer.md |
| UX 研究员 | `ux-research` | design | design/ux-researcher.md |
| UX 架构师 | `ux-arch` | design | design/ux-architect.md |
| 品牌守护者 | `brand` | design | design/brand-guardian.md |

## Engineering

| Agent | dispatch_tag | 适用阶段 | 文件 |
|-------|-------------|---------|------|
| 后端架构师 | `backend` | arch, code | engineering/backend-architect.md |
| 软件架构师 | `architect` | arch | engineering/software-architect.md |
| AI 工程师 | `ai` | arch, code | engineering/ai-engineer.md |
| 数据工程师 | `data` | arch, code | engineering/data-engineer.md |
| 安全架构师 | `sec-arch` | arch, code | engineering/security-architect.md |
| DevOps 自动化工程师 | `devops` | arch, code | engineering/devops-automator.md |
| 数据库优化专家 | `database` | arch, code | engineering/database-optimizer.md |
| 前端开发专家 | `frontend` | code | engineering/frontend-developer.md |
| 安全工程师 | `security` | code | engineering/security-engineer.md |
| 代码审查员 | `review` | code | engineering/code-reviewer.md |
| 高级全栈开发 | `senior` | code | engineering/senior-developer.md |
| 最小变更工程师 | `minimal` | code | engineering/minimal-change-engineer.md |
| 快速原型师 | `proto` | code | engineering/rapid-prototyper.md |
| 移动应用开发专家 | `mobile` | code | engineering/mobile-app-builder.md |
| 提示词工程师 | `prompt` | code | engineering/prompt-engineer.md |
| 代码库上手工程师 | `onboard` | code | engineering/codebase-onboarding-engineer.md |
| SRE 站点可靠性工程师 | `sre` | regression | engineering/sre.md |
| 事件响应指挥官 | `incident` | regression | engineering/incident-response-commander.md |

## Testing

| Agent | dispatch_tag | 适用阶段 | 文件 |
|-------|-------------|---------|------|
| API 测试专家 | `api-test` | test | testing/api-tester.md |
| 可访问性审计师 | `a11y` | test | testing/accessibility-auditor.md |
| 性能基准测试师 | `perf` | test | testing/performance-benchmarker.md |
| 现实检验师 | `reality` | test | testing/reality-checker.md |
| 证据收集师 | `evidence` | test | testing/evidence-collector.md |

## 派发规则

1. Arch Loop 拆分 TODO.md 时根据任务性质自动标注 `[type: xxx]` 标签
2. Code/Design/Test Loop 读取任务标签，加载对应 agent 人格文件作为上下文采纳
3. 无标签任务使用阶段 agent 自身人格执行（向后兼容）
4. 标签与 agent 映射关系在 `registry.yaml` 的 `dispatch` 段集中维护
5. 新增 agent：按 `templates/AGENT.template.md` 创建文件，在此索引注册，在 registry.yaml 添加 dispatch 条目

## 部门概览

| 部门 | Agent 数量 | 覆盖阶段 |
|------|-----------|---------|
| Product | 6 | prd, arch, regression |
| Design | 5 | design |
| Engineering | 18 | arch, code, regression |
| Testing | 5 | test |
| **合计** | **34** | **全 6 阶段** |
