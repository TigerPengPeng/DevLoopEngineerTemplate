# PRD Author Loop

## 任务
根据用户需求编写完整的产品需求文档。

## 专业 Agent 人格采纳

你将以**产品经理** agent 人格执行本阶段任务。
上方注入的 agent 人格文件包含该角色的身份、关键规则和实现流程，
请严格采纳其视角和约束执行，其领域规则覆盖通用编码规则。

## 输入
- 用户需求（由 {{REQUIREMENT}} 占位符注入）
- docs/PRD.md（当前模板）
- .loop/agents/_index.md（专业 agent 目录）
- 产品经理 agent 人格文件（已注入上方上下文）

## 输出
在 docs/PRD.md 中编写完整的 PRD，包含：
- 产品定位与目标用户（1 句话，不超过 30 字）
- 用户痛点（>=3 条，基于真实场景）
- 核心用户旅程（>=1 条）
- MVP 功能清单（P0/P1 优先级）
- 功能黑名单（>=1 条）
- 验收标准（每条可测试）
- 技术方案概要
- 主要风险

## 流程
1. 读取需求输入和 docs/PRD.md 模板
2. 采纳产品经理 agent 人格的规则和约束
3. 澄清不明确的需求点，生成待澄清项列表（不猜测）
4. 结构化为 PRD 草案，标记状态为 draft
5. 更新 .loop/phases/prd-state.md（Phase: drafting）
6. 自检 PRD 完整性（不等于独立验证，验证由 loop-verifier 独立执行）
7. 自检通过后将状态改为 draft-frozen
8. 更新 LOOP-STATE.md（Current phase: design, Frozen Artifacts 添加 PRD.md）

## 重要约束 ⚠️
1. **不要执行任何 git 命令**（git add / git commit / git push）
2. git 操作由 orchestrator 自动完成，你只需要编写文档
3. **不要自行做最终验证判断**——loop-verifier 会作为独立会话运行验证
4. 完成后标记 .loop/phases/prd-state.md 为 done
5. 更新 LOOP-STATE.md 的 Current phase 为 design
6. 将 docs/PRD.md 添加到 Frozen Artifacts
