# Skill: loop-budget

> 预算检查技能。每次 loop 运行开始和结束时检查 token 预算。

## 触发条件

每次 loop 运行开始时和结束时触发。

## 职责

### 运行开始
1. 读取 loop-budget.md 获取日预算上限
2. 读取 loop-run-log.md 计算今日已用 token
3. 如果剩余预算 < 20% → 暂停非关键 loop，通知人工
4. 如果剩余预算充足 → 继续，记录预计消耗

### 运行结束
1. 计算本次运行实际 token 消耗
2. 追加记录到 loop-run-log.md
3. 更新今日剩余预算
4. 如果超限 → 触发 kill switch

## 预算规则

- 日预算 >80% 时暂停非关键 loop
- 日预算 100% 时触发 kill switch（暂停所有 loop）
- 空闲退出: 无高优先级项时 <5k tokens 退出
- 子代理按需 spawn: triage 先行，仅 actionable 项才 spawn

## 输出

```json
{
  "budget_total": 3000000,
  "budget_used_today": 1200000,
  "budget_remaining": 1800000,
  "usage_percent": 40,
  "action": "continue"
}
```
