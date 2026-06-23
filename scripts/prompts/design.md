# UI/UX Design Extractor Loop

## 任务
根据 PRD 编写设计规范文档。

## 专业 Agent 人格采纳

上方注入的 agent 人格文件包含 UI 设计师和交互设计师的角色规则。
请根据 PRD 页面类型采纳对应视角：
- 视觉为主（展示页、营销页）→ 重点采纳 UI 设计师规则
- 交互为主（表单流、操作面板）→ 重点采纳交互设计师规则
- 混合型 → 同时采纳两个 agent 视角

## 输入
- docs/PRD.md
- .loop/agents/_index.md（专业 agent 目录）

## 输出
在 docs/DESIGN.md 中编写完整的设计规范：
- 配色方案（主色/辅助色/中性色）
- 组件规范（按钮/卡片/表单/导航）
- 布局与栅格系统
- 交互动效规范
- 错误状态视觉
- 加载状态视觉
- 空数据状态视觉
- 响应式断点

## 流程
1. 读取 docs/PRD.md，理解产品定位和用户旅程
2. 采纳 UI 设计师和交互设计师 agent 人格规则
3. 按 PRD 页面清单逐个设计视觉和交互规范
4. 自检三大异常状态（loading/empty/error）是否有视觉规范
5. 更新 .loop/phases/design-state.md（Phase: drafting）
6. 自检通过后将状态改为 done
7. 更新 LOOP-STATE.md（Current phase: arch, Frozen Artifacts 添加 DESIGN.md）

## 重要约束 ⚠️
1. **不要执行任何 git 命令**（git add / git commit / git push）
2. git 操作由 orchestrator 自动完成，你只需要编写文档
3. **不要自行做最终验证判断**——loop-verifier 会作为独立会话运行验证
4. 完成后标记 .loop/phases/design-state.md 为 done
5. 更新 LOOP-STATE.md 的 Current phase 为 arch
6. 将 docs/DESIGN.md 添加到 Frozen Artifacts
