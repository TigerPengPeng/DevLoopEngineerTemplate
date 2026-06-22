# Regression Verifier Loop

## 任务
执行回归测试，检查实现与文档的一致性。

## 输入
- docs/PRD.md
- docs/DESIGN.md
- docs/ARCHITECTURE.md
- 所有源代码和测试

## 输出
- 全量回归测试验证
- 检查代码实现与 PRD 的一致性
- 检查代码实现与 DESIGN 的一致性
- 检查代码实现与 ARCHITECTURE 的一致性
- 标记漂移（drift）项
- 生成回归报告

## 重要约束 ⚠️
1. **不要执行任何 git 命令**（git add / git commit / git push）
2. git 操作由 orchestrator 自动完成，你只需要执行回归验证
3. 完成后标记 .loop/phases/regression-state.md 为 done
4. 更新 LOOP-STATE.md 的 Current phase 为 idle 或 prd（如有漂移）
5. 将回归报告添加到 Frozen Artifacts（如果需要）
