# test Loop State

> 阶段状态文件。每次运行更新。

Last run: 2026-06-26T14:15:00Z
Phase: done

## Current
- (无)

## Completed
- mvn compile 编译通过（含 MarketSessionService Clock 测试接缝，未改变生产行为）
- mvn test（本轮）: **91 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS**
- 修复 2 个既有 flaky 测试 MarketSessionServiceTest（testDefaultSession / testIsTradingClosed）：
  - 根因：fallback 时间推断使用 ZonedDateTime.now()，运行于交易时段时返回 REGULAR 而非 CLOSED
  - 修复：注入 Clock 测试接缝（默认系统时钟，行为不变），测试固定为周六使推断确定性
  - 新增 4 个确定性时段推断测试（美股盘中/盘前、A 股盘中、周末全 CLOSED），覆盖 PRD「美股多时段支持」验收
- 新增 RiskAssessmentServiceTest（T14 风险评估，4 测试）：数据不足→LOW、空头排列→HIGH+跌破MA因子、多头排列→非HIGH+站上MA因子、assessAll 过滤 LOW 无风险股
- 新增 TimeWindowFluctuationMonitorTest BF-2 空规则持久化测试：删空保存为 []，重载不回退默认（对应 BF-2 验收「删空保存不回退默认」）
- 新增 TimeWindowFluctuationMonitorTest 波段评估逻辑测试：OR 任一匹配即触发、AND 需全部匹配否则 null
- 新增 TradingSignalServiceTest（T16 买卖点检测，5 测试）：空数据→空、不足25根K线→空、放量大跌→单条 SELL（放量大跌）、缩量下跌→单条 BUY（缩量下跌）、平盘无信号；测试数据构造为单检测器触发，断言信号类型/价格/原因
- 新增 EmailNotificationServiceTest（BF-1 邮件开关后端，5 测试）：配置禁用→isConfigured=false、无收件人→未配置、完整配置→configured+enabled、运行时 toggleEmail 关/开往返、关闭时 sendMAEventAlert 不触发 mailSender 与 history（gating 生效）

## 验收映射
- BF-1（邮件开关）：后端 toggle/configured/gating 由 EmailNotificationServiceTest 5 测试 ✓；前端 onclick 作用域修复仍需手动/集成验证
- BF-2（波段规则持久化）：TimeWindowFluctuationMonitorTest 6 测试 ✓
- BF-3（历史事件邮件抑制）：TradingSignalScannerTest 3 测试 ✓
- NR-1（统一降噪闸门）：AlertNoiseFilterTest 8 测试 ✓
- NR-2/NR-4（波动去重/全量留痕）：AlertCoordinatorTest（冷却仍记录 + suppressed 持久化）✓
- NR-3（MA 破位跨次去重）：AlertNoiseFilter 按 stockKey 去重 + MABreakdownScannerTest ✓
- T16（买卖点信号服务）：TradingSignalServiceTest 5 测试 ✓
- v1.0 基线：MACalculator/CrossoverDetector/MarketSessionService/AsyncRequestBridge/ReconnectLifecycle/NotificationTemplate/AlertCoordinator ✓

## 状态覆盖
- 空数据/数据不足：RiskAssessmentServiceTest（数据不足）、TimeWindowFluctuationMonitor（空规则/deque 不足→null）、TradingSignalServiceTest（空/不足25根→空）
- 错误状态：AsyncRequestBridgeTest（超时/failAll）、ReconnectLifecycleTest（重连）、MarketSessionService（推断兜底）、EmailNotificationService（禁用→send 不触发）
- 加载状态：后端无异步加载态（前端关注点）

## Blocked
- (无)

## Acting On
- (无)

## Human Inbox
- (无)
