# Architecture Planner Loop

## 任务
根据 PRD 和 Design 编写技术架构文档。

## 输入
- docs/PRD.md
- docs/DESIGN.md

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

## 重要约束 ⚠️
1. **不要执行任何 git 命令**（git add / git commit / git push）
2. git 操作由 orchestrator 自动完成，你只需要编写文档
3. 完成后标记 .loop/phases/arch-state.md 为 done
4. 更新 LOOP-STATE.md 的 Current phase 为 code
5. 将 docs/ARCHITECTURE.md 添加到 Frozen Artifacts
