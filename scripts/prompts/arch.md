# Architecture Planner Loop

## 任务
根据 PRD 和 Design 编写技术架构文档和任务清单。

## 专业 Agent 派发（任务级）

本阶段为任务级派发：在拆分 TODO.md 时，根据任务性质标注 [type:xxx] 标签，
并读取（cat）对应 agent 人格文件采纳其领域规则：
- 涉及 API/服务端逻辑 → [type: backend] → cat .loop/agents/engineering/backend-architect.md
- 涉及表结构/迁移/查询 → [type: database] → cat .loop/agents/engineering/database-optimizer.md
- 涉及 CI/CD/部署/基础设施 → [type: devops] → cat .loop/agents/engineering/devops-automator.md
- 涉及安全/鉴权/输入校验 → [type: security] → cat .loop/agents/engineering/security-engineer.md
- 涉及文档更新 → [type: docs] → cat .loop/agents/product/technical-writer.md
- 通用/不确定 → 不标注

在规划架构时，应先 cat backend-architect.md 和 devops-automator.md 人格文件，
采纳其领域规则来指导技术栈选型和架构设计。

## 输入
- docs/PRD.md
- docs/DESIGN.md
- .loop/agents/_index.md（专业 agent 目录）

## 输出
在 docs/ARCHITECTURE.md 中编写完整的技术架构：
- 技术栈选型（前端/后端/数据库/部署）
- 目录结构规范
- 数据模型设计
- API 接口约定
- 核心模块职责
- 数据流图
- 开发约束
- 禁止破坏的逻辑
- 验收标准

同时生成 TODO.md，为每个任务标注 [type: xxx] 标签。

## 任务标签格式
- [ ] [type: backend] 实现用户认证 API
- [ ] [type: frontend] 实现任务列表页
- [ ] [type: database] 创建任务表迁移

## 流程
1. 读取 docs/PRD.md 和 docs/DESIGN.md
2. cat backend-architect.md 和 devops-automator.md，采纳其领域视角
3. 设计技术架构，写入 docs/ARCHITECTURE.md
4. 拆分 TODO.md，为每个任务标注 [type:xxx] 标签
5. 建立 PRD MVP 功能 ↔ TODO 任务的追溯矩阵
6. 更新 .loop/phases/arch-state.md（Phase: drafting）
7. 自检通过后将状态改为 done
8. 更新 LOOP-STATE.md（Current phase: code, Frozen Artifacts 添加 ARCHITECTURE.md）

## 重要约束 ⚠️
1. **不要执行任何 git 命令**（git add / git commit / git push）
2. git 操作由 orchestrator 自动完成，你只需要编写文档
3. **不要自行做最终验证判断**——loop-verifier 会作为独立会话运行验证
4. 完成后标记 .loop/phases/arch-state.md 为 done
5. 更新 LOOP-STATE.md 的 Current phase 为 code
6. 将 docs/ARCHITECTURE.md 添加到 Frozen Artifacts
