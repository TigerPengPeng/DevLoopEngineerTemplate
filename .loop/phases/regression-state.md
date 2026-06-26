# regression Loop State

> 阶段状态文件。每次运行更新。

Last run: 2026-06-26T14:36:00Z
Phase: blocked (verifier-rejected)

## Current
- (无)

## 回归报告（v1.2 降噪迭代 NR-1/NR-2/NR-3/NR-4/NR-5）

### 1. 全量回归测试（本次独立执行，非复用 test-state）
- `mvn test`: **91 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS**
- 分布: AsyncRequestBridge 7 / ReconnectLifecycle 5 / CrossoverDetector 8 / MACalculator 8 / MarketSessionService 15 / RiskAssessmentService 4 / TradingSignalService 5 / AlertCoordinator 6 / AlertNoiseFilter 8 / FluctuationAlertScheduler 4 / MABreakdownScanner 3 / TimeWindowFluctuationMonitor 6 / TradingSignalScanner 3 / EmailNotificationService 5 / NotificationTemplate 4
- 结论: 无功能回归。

### 2. 主链路完整性
- v1.2 改动隔离在告警分发层（AlertNoiseFilter + AlertCoordinator.recordAlert + FluctuationAlertScheduler/MABreakdownScanner/TradingSignalScanner 降噪接入 + AlertRecord.suppressed）。
- 连接管理 / 重连 / 订阅 / AsyncRequestBridge / MA 计算 / K 线缓存主链路 **未被触碰**，与回归核对一致。
- Spring DI 构造器注入无裸 new 受影响；AlertRecord.save() 失败 try/catch 降级不影响主流程；BF-3 首扫初始化分支未改变去重键格式与 ring buffer 语义。

### 3. 代码 vs PRD 一致性
- NR-1 统一闸门 ✓ AlertNoiseFilter 接入 MA/FLUCTUATION/SIGNAL/BREAKDOWN 四类。
- NR-2 波动按股票去重 ✓ FluctuationAlertScheduler dedupKey=stockKey:direction，全抑制不发空邮件。
- NR-3 MA 破位跨次去重 ✓ MABreakdownScanner dedupKey=stockKey，10:00/14:00/22:00 cron。
- NR-5 静默窗口可配置 ✓ FutuProperties(Ma/Fluctuation/Signal/BreakdownNoiseMinutes) + application.yml env 覆盖。
- BF-1/BF-2/BF-3 ✓ 前端三函数已提至全局作用域（行 593/608/616，refresh() 体为 575-588）；波段规则 DB 持久化；首扫 seed notifiedKeys。

### 4. 漂移项（drift）—— 文档落后于实现，非功能回归

- **D-1 [DESIGN.md 落后]**: DESIGN.md 仍为 v1.1(draft)，未覆盖 v1.2 NR-3 前端「已静音」视觉（renderAlerts: suppressed→opacity 0.5 + 灰色「已静音」pill，index.html:483/488 已实现）。PRD NR-4 验收要求「首页告警列表展示已抑制记录并与已发送视觉区分」，实现已满足但 DESIGN 未登记该状态。
- **D-2 [ARCHITECTURE.md 目录结构过期]**: 目录结构与真实 src 树严重不符。列出了不存在的 `model/PriceAlert.java`、`monitor/PriceFluctuationMonitor.java`；缺失 QuoteUpdateListener、6 个 market 服务、7 个 monitor 类（含 AlertNoiseFilter/TimeWindowFluctuationMonitor/MABreakdownScanner/FluctuationAlertScheduler/TradingSignalScanner/MACrossoverMonitor/SectorTrendReportScheduler/ErrorLogAppender）、EmailHistoryService、QuoteProcessor、4 个 entity（EmailRecord/ErrorLog/SectorReport/SignalRecord）、4 个 repository，以及整个 `web/` 包（6 个 Controller）；entity/repository 路径嵌套错误；test/ 仅列 4 类（实际 15）。
- **D-3 [ARCHITECTURE.md 变更说明缺失 NR-4/NR-5]**: v1.2 变更说明仅写 NR-1/NR-2/NR-3，未记录已实现的 NR-4（AlertRecord.suppressed 全量留痕）与 NR-5（按类型静默窗口）；NR-1 描述称冷却「复用 alert-cooldown-minutes 单一值」，与已实现的 per-type 覆盖（NR-5）不符。
- **D-4 [README.md 错误]**: 技术栈标注「Spring Boot 3.3.6 (非 Web...)」与实际矛盾——pom 含 `spring-boot-starter-web`，存在 6 个 REST Controller + 4 个静态页；应改为 Web 应用。环境变量表缺 NR-5 噪声变量（MA/FLUCTUATION/SIGNAL/BREAKDOWN_NOISE_MINUTES）及波段规则项。
- **D-5 [PRD NR-4 范围部分未达]**: PRD NR-4 字面要求「所有告警（含已抑制）写入持久化记录」。实际仅 MA 突破路径（AlertCoordinator）写入 alert_records；波动/破位/买卖点的被抑制告警仅记日志，未落库。ARCHITECTURE 数据流图与此一致（仅 MA 路径落库），且 PRD 待澄清项 #6（留痕表膨胀）曾将该决策推迟给 Arch Loop——属可辩护的设计取舍，但 PRD 字面范围未全覆盖。建议在 PRD NR-4 明确「持久化范围以 MA 突破告警为界」，或补齐批量/信号路径的留痕。

### 5. 阶段结论
- 功能层: **无回归**，v1.2 NR 全部实现并通过验收测试。
- 文档层: 存在 5 项漂移（D-1~D-5），均为文档落后/与实现矛盾，**不阻塞**当前代码可用性，但违反「文档与实现保持一致」约束。
- 建议: 触发一轮 **Design + Arch doc-refresh 子循环** 同步 D-1~D-4；D-5 需 PRD 确认留痕范围。

## Completed
- 全量回归测试独立执行: 91/91 通过
- 代码 vs PRD / DESIGN / ARCHITECTURE / README 一致性核对
- 5 项文档漂移已标记并定级（非功能性）

## Blocked
- (无)

## Acting On
- file: .loop/phases/regression-state.md, LOOP-STATE.md, loop-run-log.md

## Human Inbox
- D-5（PRD NR-4 留痕范围）需人工确认：是否将「持久化留痕」限定为 MA 突破告警，或补齐波动/破位/买卖点路径。
