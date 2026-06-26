# code Loop State

> 阶段状态文件。每次运行更新。

Last run: 2026-06-26T09:05:00Z
Phase: done

## Current
- (无)

## Completed
- T18 BF-1: index.html 中 toggleEmail/refreshEmailToggle/updateEmailToggle 从 refresh() 局部作用域提到全局作用域
- T19 BF-2: 新增 FluctuationConfigEntity + FluctuationConfigRepository；TimeWindowFluctuationMonitor 启动 loadFromDatabase、updateConfig 持久化；默认规则改为 3min>=3% OR 5min>=5%（FutuProperties + application.yml）
- T20 BF-3: TradingSignalScanner 首次扫描初始化 notifiedKeys（仅展示不发邮件），resetAll 重置初始化标志
- 新增单元测试：TimeWindowFluctuationMonitorTest (3) + TradingSignalScannerTest (3)

## Blocked
- (无)

## Acting On
- file: index.html, TimeWindowFluctuationMonitor.java, FluctuationConfigEntity.java, FluctuationConfigRepository.java, FutuProperties.java, application.yml, TradingSignalScanner.java

## Human Inbox
- (无)
