# Skill: arch-planner

> 架构规划技能。基于 PRD + DESIGN 产出技术架构和任务清单。

## 触发条件

docs/DESIGN.md 标记为 draft-frozen 时触发。

## 职责

1. 读取 docs/PRD.md + docs/DESIGN.md（frozen 版本）
2. 产出 docs/ARCHITECTURE.md，必须包含：
   - 技术栈
   - 目录结构
   - 数据模型（覆盖 PRD 所有核心实体）
   - 服务层约定
   - API 层约定
   - AI 引用机制（agent 如何读取 docs/）
   - 开发约束
   - 禁止破坏的逻辑
   - 验收标准
3. 产出 TODO.md，每个任务包含：
   - 修改目标
   - 允许修改范围
   - 不允许破坏的逻辑
   - 验收标准
4. 构建 PRD MVP 功能 <-> TODO 任务的追溯矩阵
5. 更新 .loop/phases/arch-state.md

## 禁止行为

- 不修改 docs/PRD.md 或 docs/DESIGN.md（只读取）
- 不拆分过粗或过细的任务（每个任务应可独立执行和验证）
- 不遗漏 PRD 中任何 MVP 功能的对应任务
- 不引入未在技术栈中声明的依赖
- 不修改 denylist 路径下的文件

## 验收自检

- ARCHITECTURE.md 覆盖所有必填字段
- TODO.md 任务有序、可独立执行
- PRD 每个 MVP 功能在 TODO.md 中有对应任务
- 数据模型覆盖 PRD 所有核心实体
