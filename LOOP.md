# LOOP.md — 闭环系统总纲

> 基于 loop-engineering 的全生命周期自动 prompt 代理闭环系统。
> 此文件既是文档也是维护本系统的 loop 的种子。

## 闭环目标

通过六个阶段 loop 的自动调度与协调，实现从需求到回归验证的全生命周期自动化，
并以回归反馈驱动下一轮迭代，形成持续收敛的螺旋闭环。

```
① PRD Loop ──→ ② Design Loop ──→ ③ Arch Loop
     ↑                                  │
     │                                  ↓
⑥ Regression ←── ⑤ Test Loop ←── ④ Code Loop
     │
     └──── 反馈信号（缺陷、偏差、新需求）──────┐
```

## 活跃 Loop

### PRD Loop（L1 — 报告模式）
- 触发: 事件驱动（用户输入需求 / 回归反馈 drift 信号）
- 技能: `prd-author` + `loop-verifier`
- 状态: `.loop/phases/prd-state.md`
- 产出: `docs/PRD.md`
- 冻结条件: verifier 通过 + 人工确认 MVP 范围
- 下游: Design Loop

### Design Loop（L1 — 报告模式）
- 触发: PRD.md `draft-frozen`
- 技能: `design-extractor` + `loop-verifier`
- 状态: `.loop/phases/design-state.md`
- 产出: `docs/DESIGN.md`
- 冻结条件: verifier 通过 + 人工确认设计风格
- 下游: Arch Loop

### Architecture Loop（L1 — 报告模式）
- 触发: DESIGN.md `draft-frozen`
- 技能: `arch-planner` + `loop-verifier`
- 状态: `.loop/phases/arch-state.md`
- 产出: `docs/ARCHITECTURE.md` + `TODO.md`
- 冻结条件: verifier 通过 + 人工确认技术栈和数据模型
- 下游: Code Loop

### Code Loop（L2 — 辅助模式）
- 触发: TODO.md `frozen` / 上一个 TODO 任务完成
- 技能: `code-implementer` + `minimal-fix` + `loop-verifier`
- 状态: `.loop/phases/code-state.md`
- 产出: 代码提交（worktree → PR）
- 完成条件: TODO.md 所有任务完成 + 单元测试通过
- 下游: Test Loop
- 安全: worktree 隔离；denylist 内置；最多 3 次修复

### Test Loop（L2 — 辅助模式）
- 触发: Code Loop 所有任务完成
- 技能: `test-runner` + `loop-verifier`
- 状态: `.loop/phases/test-state.md`
- 产出: 测试报告 + 缺陷清单
- 完成条件: 所有 PRD 验收标准有测试覆盖 + 主流程端到端通过
- 下游: Regression Loop

### Regression Loop（L1 → L2）
- 触发: Test Loop 通过 / 版本发布前 / 合并到 main 后定时巡检
- 技能: `regression-verifier` + `minimal-fix` + `loop-verifier`
- 状态: `.loop/phases/regression-state.md`
- 产出: 回归报告 + drift/new-need 反馈信号
- 完成条件: 回归全通过 → 标记版本 `regression-passed`
- 反馈: drift → PRD Loop；regression → Code Loop；new-need → Human Inbox

## 多 Loop 协调

优先级: Regression > Test > Code > Arch > Design > PRD

冲突检测: 每个 loop 启动前读取所有其他 state 文件的 `acting_on`，冲突则 skip。

冻结/解冻: 产出物 frozen 后下游可消费；下游发现问题时发 `unfreeze` 请求到 `LOOP-STATE.md`。

调度表:
```markdown
## 多 loop 调度
- PRD Loop: 事件驱动（需求输入 / drift 反馈）
- Design Loop: 事件驱动（PRD frozen）
- Arch Loop: 事件驱动（DESIGN frozen）
- Code Loop: 连续（TODO 任务顺序执行）
- Test Loop: 事件驱动（Code 完成）
- Regression Loop: 1d 巡检 + 事件驱动（Test 通过 / pre-release）
```

## 工作树

所有 Code/Regression 的代码修改在隔离 git worktree 中执行。
一个任务一个 worktree；verifier REJECT 或人工升级后丢弃。

## 预算与可观测性

- Token 预算: `loop-budget.md`（按阶段分配）
- 运行日志: `loop-run-log.md`（append-only）
- Kill switch: `LOOP-STATE.md` 中的 `paused: true` 或 `loop-pause-all` 标签

## 安全门禁

- 路径 denylist: `.env*`, `**/secrets/**`, `auth/**`, `payments/**`, `**/migrations/**`
- 人工门禁: auth/payments/infra/依赖升级/>10文件/3次失败/PRD定位变更/unfreeze
- 自动合并: 默认关闭；L3 可开严格 allowlist
- 熔断: 预算>80% / 同缺陷升级2+次/48h / 生产事故 / 破坏性迁移


## Orchestrator（自动调度器）

闭环系统通过 `scripts/loop-orchestrator.sh` 实现阶段间自动链式触发。

### 工作原理

1. Orchestrator 轮询 `LOOP-STATE.md` 的 `Current phase` 字段
2. 当当前阶段的 state 文件标记为 done/draft-frozen/frozen 时
3. 自动触发下一阶段（读取对应 prompt 模板，调用 `codex exec`）
4. Agent 完成后更新 state 文件和 LOOP-STATE.md
5. Orchestrator 检测到新阶段完成，继续触发下游

### 使用方式

```bash
# 查看当前状态
./scripts/loop-status.sh

# 单次检查并触发下一阶段（如果上游已完成）
./scripts/loop-orchestrator.sh --once

# 持续监听模式（每 10 秒轮询一次）
./scripts/loop-orchestrator.sh

# 手动触发指定阶段
./scripts/loop-orchestrator.sh --once --phase design

# 启动 PRD Loop（输入需求）
REQUIREMENT='你的产品需求描述' ./scripts/loop-orchestrator.sh --once --phase prd

# 自定义轮询间隔
POLL_INTERVAL=30 ./scripts/loop-orchestrator.sh
```

### 阶段流转

```
PRD (frozen) → orchestrator 检测 → 触发 Design
Design (frozen) → orchestrator 检测 → 触发 Arch
Arch (frozen) → orchestrator 检测 → 触发 Code
Code (done) → orchestrator 检测 → 触发 Test
Test (done) → orchestrator 检测 → 触发 Regression
Regression (done) → 检查反馈信号:
  - drift → Current phase 回到 prd → 触发 PRD Loop（闭环）
  - all pass → Current phase 回到 idle → 等待新需求
```

### Kill Switch

在 `LOOP-STATE.md` 中设置 `paused: true` 可暂停 orchestrator 的所有触发。

## 升级路径

```
L0(文档) → L1(报告) → L2(辅助) → L3(无人值守)
```
每级稳定 2 周后再升级。永远不要为新项目跳过 L1。
