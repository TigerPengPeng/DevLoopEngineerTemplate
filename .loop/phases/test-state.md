# test Loop State

> 阶段状态文件。每次运行更新。

Last run: 2026-06-26T13:45:00Z
Phase: blocked (verifier-rejected)

## Current
- (无)

## Completed
- mvn compile 编译通过（含 MarketSessionService Clock 测试接缝，未改变生产行为）
- mvn test: **73 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS**
- 修复 2 个既有 flaky 测试 MarketSessionServiceTest（testDefaultSession / testIsTradingClosed）：
  - 根因：fallback 时间推断使用 ZonedDateTime.now()，运行于交易时段时返回 REGULAR 而非 CLOSED
  - 修复：注入 Clock 测试接缝（默认系统时钟，行为不变），测试固定为周六使推断确定性
  - 新增 4 个确定性时段推断测试（美股盘中/盘前、A 股盘中、周末全 CLOSED），覆盖 PRD「美股多时段支持」验收
- 新增 RiskAssessmentServiceTest（T14 风险评估，4 测试）：数据不足→LOW、空头排列→HIGH+跌破MA因子、多头排列→非HIGH+站上MA因子、assessAll 过滤 LOW 无风险股
- 新增 TimeWindowFluctuationMonitorTest BF-2 空规则持久化测试：删空保存为 []，重载不回退默认（对应 BF-2 验收「删空保存不回退默认」）
- 新增 TimeWindowFluctuationMonitorTest 波段评估逻辑测试：OR 任一匹配即触发、AND 需全部匹配否则 null

## 验收映射
- BF-1（邮件开关）：纯前端，单元测试不适用，需手动/集成验证
- BF-2（波段规则持久化）：TimeWindowFluctuationMonitorTest 4 测试 ✓
- BF-3（历史事件邮件抑制）：TradingSignalScannerTest 3 测试 ✓
- NR-1（统一降噪闸门）：AlertNoiseFilterTest 7 测试 ✓
- NR-2/NR-4（波动去重/全量留痕）：AlertCoordinatorTest（冷却仍记录 + suppressed 持久化）✓
- NR-3（MA 破位跨次去重）：AlertNoiseFilter 按 stockKey 去重 ✓
- v1.0 基线：MACalculator/CrossoverDetector/MarketSessionService/AsyncRequestBridge/ReconnectLifecycle/NotificationTemplate/AlertCoordinator ✓

## 状态覆盖
- 空数据/数据不足：RiskAssessmentServiceTest（数据不足）、TimeWindowFluctuationMonitor（空规则/deque 不足→null）
- 错误状态：AsyncRequestBridgeTest（超时/failAll）、ReconnectLifecycleTest（重连）、MarketSessionService（推断兜底）
- 加载状态：后端无异步加载态（前端关注点）

## Blocked
- (无)

## Acting On
- (无)

## Human Inbox
- (无)
