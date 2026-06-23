# 专业 Agent 目录索引

> 阶段 agent 通过 TODO.md 任务标签 `[type: xxx]` 派发到对应专业 agent。
> 本文件由 registry.yaml 的 `agents` 和 `dispatch` 段映射，修改后须同步。

## Engineering

| Agent | dispatch_tag | 适用阶段 | 文件 |
|-------|-------------|---------|------|
| 前端开发专家 | `frontend` | code | engineering/frontend-developer.md |
| 后端架构师 | `backend` | arch, code | engineering/backend-architect.md |
| 安全工程师 | `security` | code | engineering/security-engineer.md |
| 代码审查员 | `review` | code | engineering/code-reviewer.md |
| DevOps 自动化工程师 | `devops` | arch, code | engineering/devops-automator.md |
| 数据库优化专家 | `database` | arch, code | engineering/database-optimizer.md |

## Design

| Agent | dispatch_tag | 适用阶段 | 文件 |
|-------|-------------|---------|------|
| UI 设计师 | `ui` | design | design/ui-designer.md |
| 交互设计师 | `interaction` | design | design/interaction-designer.md |

## Product

| Agent | dispatch_tag | 适用阶段 | 文件 |
|-------|-------------|---------|------|
| 产品经理 | `product` | prd | product/product-manager.md |
| 技术文档工程师 | `docs` | arch, regression | product/technical-writer.md |

## 派发规则

1. Arch Loop 拆分 TODO.md 时根据任务性质自动标注 `[type: xxx]` 标签
2. Code/Design Loop 读取任务标签，加载对应 agent 人格文件作为上下文采纳
3. 无标签任务使用阶段 agent 自身人格执行（向后兼容）
4. 标签与 agent 映射关系在 `registry.yaml` 的 `dispatch` 段集中维护
5. 新增 agent：按 `templates/AGENT.template.md` 创建文件，在此索引注册，在 registry.yaml 添加 dispatch 条目
