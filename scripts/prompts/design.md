# UI/UX Design Extractor Loop

## 任务
根据 PRD 编写设计规范文档。

## 输入
- docs/PRD.md
- .loop/agents/_index.md（专业 agent 目录）

## 派发视角

按 PRD 页面类型采纳专业 agent 视角：
- 视觉为主（展示页、营销页）→ 采纳 .loop/agents/design/ui-designer.md 视角
- 交互为主（表单流、操作面板）→ 采纳 .loop/agents/design/interaction-designer.md 视角
- 混合型 → 同时采纳两个 agent 视角

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

## 重要约束 ⚠️
1. **不要执行任何 git 命令**（git add / git commit / git push）
2. git 操作由 orchestrator 自动完成，你只需要编写文档
3. 完成后标记 .loop/phases/design-state.md 为 done
4. 更新 LOOP-STATE.md 的 Current phase 为 arch
5. 将 docs/DESIGN.md 添加到 Frozen Artifacts
