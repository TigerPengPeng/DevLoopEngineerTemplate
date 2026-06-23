# Code Implementer Loop

## 任务
按照架构文档实现代码。

## 输入
- docs/PRD.md
- docs/DESIGN.md
- docs/ARCHITECTURE.md
- .loop/agents/_index.md（专业 agent 目录）

## 输出
按照 TODO.md 逐个任务实现：
- 严格遵循 ARCHITECTURE.md 的目录结构和约束
- 严格遵循 DESIGN.md 的视觉规范
- 严格遵循 PRD.md 的功能范围
- 一次只完成一个任务
- 不添加未要求的功能
- 不重构无关代码

## 任务标签派发 ⚠️

1. 读取 TODO.md 下一个未完成任务
2. 检查任务标题是否含 `[type: xxx]` 标签
3. 若有标签：从 `.loop/agents/_index.md` 查找对应 agent 文件，读取并采纳其人格和专业规则执行
4. 若无标签：以通用 code-implementer 人格执行
5. 专业 agent 的领域规则覆盖通用编码规则，但 Loop 安全约束不变

## 重要约束 ⚠️
1. **不要执行任何 git 命令**（git add / git commit / git push）
2. git 操作由 orchestrator 自动完成，你只需要编写代码
3. 完成后标记 .loop/phases/code-state.md 为 done
4. 更新 LOOP-STATE.md 的 Current phase 为 test
5. 不要修改已冻结的文档（Frozen Artifacts）
