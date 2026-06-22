# Skill: regression-verifier

> 回归验证技能。回归测试 + 快照对比 + 偏差分类。

## 触发条件

Test Loop 通过 / 版本发布前 / 合并到 main 后定时巡检。

## 职责

1. 读取 tests/regression/（回归测试套件）+ tests/snapshots/（功能快照）+ LOOP-STATE.md
2. 执行完整回归测试套件
3. 对比 UI/接口/数据模型快照
4. 对照 PRD 验收标准逐项验证
5. 分类发现：
   - regression（回归缺陷）→ Code Loop（minimal-fix）
   - drift（需求偏差）→ PRD Loop（反馈信号）
   - new-need（新需求信号）→ Human Inbox
6. 产出回归报告
7. 更新 .loop/phases/regression-state.md + LOOP-STATE.md

## 偏差分类规则

- **regression**: 之前通过的功能现在失败 → 代码缺陷
- **drift**: 实现与 PRD 描述不一致 → 需求修正
- **new-need**: 回归中发现 PRD 未覆盖但用户需要的行为 → 新需求

## 反馈信号格式

写入 LOOP-STATE.md ## Regression Feedback → PRD 区：

```markdown
- [ ] drift: [偏差描述]
  - PRD 原文: ...
  - 实际实现: ...
  - 建议修正: ...
```

## 禁止行为

- 不修改 denylist 路径下的文件
- 不自己修复回归缺陷——交由 minimal-fix + loop-verifier
- 不忽略回归失败
- 不跳过快照对比

## 验收自检

- 回归测试覆盖所有已发布核心功能
- 快照对比检测非预期变化
- PRD 验收标准逐项回归
- 回归报告完整：通过/失败/修复/升级
