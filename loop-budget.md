# Loop Budget — 项目名称

## 全局限制

- 日均 token 上限: 3M（根据计划调整）
- 超限动作: 暂停调度器，通知人工
- 每次运行最大子代理 spawn 数: 3
- 每个任务最大修复尝试: 3

## 各阶段预算分配

| Loop | 频率 | 预算/run | 说明 |
|------|------|---------|------|
| PRD Loop | on-demand | 100k | 低频，需求输入时触发 |
| Design Loop | on-demand | 100k | 低频，PRD frozen 后触发 |
| Arch Loop | on-demand | 150k | 低频，DESIGN frozen 后触发 |
| Code Loop | continuous | 200k | 高频，按 TODO 任务连续执行 |
| Test Loop | on-demand | 150k | 中频，Code 完成后触发 |
| Regression Loop | 1d | 100k | 中频，每日巡检 + 事件触发 |

## 预算规则

- 空闲退出: 如果没有高优先级项，立即退出（<5k tokens）
- 子代理按需 spawn: triage 先行，仅 actionable 项才 spawn 子代理
- 日预算 >80% 时: 暂停非关键 loop，通知人工
- 每次运行开始/结束时: `loop-budget` 技能检查剩余预算

## 估算命令

```bash
npx @cobusgreyling/loop-cost --pattern daily-triage --cadence 1d --level L1
```
