# Test Runner Loop

## 任务
为已实现的代码编写测试并验证。

## 输入
- docs/PRD.md
- docs/DESIGN.md
- docs/ARCHITECTURE.md
- 已实现的源代码

## 输出
- 编写单元测试/集成测试
- 运行测试并验证通过
- 验证主流程正常
- 验证加载状态/空状态/错误状态
- 验证符合 PRD 验收标准

## 重要约束 ⚠️
1. **不要执行任何 git 命令**（git add / git commit / git push）
2. git 操作由 orchestrator 自动完成，你只需要编写和运行测试
3. 完成后标记 .loop/phases/test-state.md 为 done
4. 更新 LOOP-STATE.md 的 Current phase 为 regression
5. 不要修改已冻结的文档（Frozen Artifacts）
