# Code Implementer Loop

## 任务
按照架构文档实现代码。

## 输入
- docs/PRD.md
- docs/DESIGN.md
- docs/ARCHITECTURE.md

## 输出
按照 TODO.md 逐个任务实现：
- 严格遵循 ARCHITECTURE.md 的目录结构和约束
- 严格遵循 DESIGN.md 的视觉规范
- 严格遵循 PRD.md 的功能范围
- 一次只完成一个任务
- 不添加未要求的功能
- 不重构无关代码

## 重要约束 ⚠️
1. **不要执行任何 git 命令**（git add / git commit / git push）
2. git 操作由 orchestrator 自动完成，你只需要编写代码
3. 完成后标记 .loop/phases/code-state.md 为 done
4. 更新 LOOP-STATE.md 的 Current phase 为 test
5. 不要修改已冻结的文档（Frozen Artifacts）
