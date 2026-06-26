# ARCHITECTURE — 技术架构文档

> 状态: frozen | draft-frozen | **frozen**
> 版本: v1.2
> 最后更新: 2026-06-26
> 由 Arch Loop 产出，Code Loop 消费。

## v1.2 变更说明

本次为 **功能增强迭代**，对应 PRD v1.2 的 NR-1/NR-2/NR-3。技术改动：
- NR-1: 新增 `AlertNoiseFilter` 中央降噪组件，统一管理所有告警类型的短期去重（冷却期复用 `futu.monitor.alert-cooldown-minutes`，默认 15 分钟）。`AlertCoordinator`、`FluctuationAlertScheduler`、`MABreakdownScanner`、`TradingSignalScanner` 均接入该组件，在发送邮件前检查冷却状态。
- NR-2: `AlertCoordinator` 改为始终记录告警（ring buffer + `alert_records` 表），仅邮件发送受降噪控制。`AlertRecord` 实体新增 `suppressed` 字段标记被静音的告警。
- NR-3: 前端 `index.html` 中 `renderAlerts` 函数根据 `suppressed` 字段对被静音的告警灰显并标注「已静音」。

## v1.1 变更说明

本次为 **bugfix 迭代**，对应 PRD v1.1 的 BF-1/BF-2/BF-3。技术改动：
- BF-1: 纯前端修复，`index.html` 中邮件开关三个函数从 `refresh()` 局部作用域提到全局作用域。后端无改动。
- BF-2: 新增 `FluctuationConfigEntity` + `FluctuationConfigRepository` 持久化波段规则；`TimeWindowFluctuationMonitor` 启动从数据库加载、`updateConfig` 同步写库；默认规则改为 3 分钟>=3% OR 5 分钟>=5%。`application.yml` 静态值仅作首启种子。
- BF-3: `TradingSignalScanner` 新增首扫初始化标志，首次扫描用已知信号填充 `notifiedKeys`（仅入 ring buffer 展示、不发邮件），后续仅新增信号触发邮件。MA 告警路径 (`CrossoverDetector` 首次观测返回 null) 已正确处理首启抑制，无需改动。

## 技术栈

- 语言: Java 17
- 框架: Spring Boot 3.3.x (spring-boot-starter + spring-boot-starter-mail + spring-boot-starter-actuator)
- 构建工具: Maven 3.9.x
- Futu SDK: com.futunn.openapi:futu-api:9.3.5308 (Maven Central)
- 日志: SLF4J + Logback (Spring Boot 内置)
- 邮件: spring-boot-starter-mail (JavaMail)
- 部署: Docker (multi-stage build) + docker-compose

## 目录结构

```
AutoTradingSystem/
├── pom.xml                              # Maven 构建配置
├── Dockerfile                           # 多阶段 Docker 构建
├── docker-compose.yml                   # 编排配置
├── .env.example                         # 环境变量模板
├── src/
│   ├── main/
│   │   ├── java/com/autotrading/
│   │   │   ├── AutoTradingApplication.java
│   │   │   ├── config/
│   │   │   │   ├── FutuProperties.java
│   │   │   │   └── NotificationProperties.java
│   │   │   ├── futu/
│   │   │   │   ├── FutuConnectionManager.java
│   │   │   │   ├── FutuQuoteHandler.java
│   │   │   │   ├── FutuConnHandler.java
│   │   │   │   └── AsyncRequestBridge.java
│   │   │   ├── account/
│   │   │   │   └── StockGroupService.java
│   │   │   ├── market/
│   │   │   │   ├── QuoteSubscriptionService.java
│   │   │   │   ├── KLineService.java
│   │   │   │   └── MarketSessionService.java
│   │   │   ├── indicator/
│   │   │   │   ├── MACalculator.java
│   │   │   │   └── CrossoverDetector.java
│   │   │   ├── monitor/
│   │   │   │   ├── PriceFluctuationMonitor.java
│   │   │   │   ├── MACrossoverMonitor.java
│   │   │   │   └── AlertCoordinator.java
│   │   │   ├── notification/
│   │   │   │   ├── EmailNotificationService.java
│   │   │   │   └── NotificationTemplate.java
│   │   │   ├── startup/
│   │   │   │   └── ApplicationStartupRunner.java
│   │   │   └── model/
│   │   │       ├── TradingSession.java
│   │   │       ├── MAEvent.java
│   │   │       ├── PriceAlert.java
│   │   │       └── StockInfo.java
│   │   │       └── (Direction.java)
│   │   ├── entity/
│   │   │   ├── AlertRecord.java
│   │   │   └── FluctuationConfigEntity.java   # v1.1 BF-2 波段规则持久化
│   │   ├── repository/
│   │   │   ├── AlertRecordRepository.java
│   │   │   └── FluctuationConfigRepository.java # v1.1 BF-2
│   │   └── resources/
│   │       ├── application.yml
│   │       └── logback-spring.xml
│   └── test/
│       └── java/com/autotrading/
│           ├── indicator/
│           │   ├── MACalculatorTest.java
│           │   └── CrossoverDetectorTest.java
│           ├── market/
│           │   └── MarketSessionServiceTest.java
│           └── monitor/
│               └── AlertCoordinatorTest.java
└── docs/
    ├── PRD.md
    ├── DESIGN.md
    └── ARCHITECTURE.md
```

## 数据模型

### StockInfo
| 字段 | 类型 | 说明 |
|------|------|------|
| market | int | 市场代码 (1=美股, 2=港股, 3=A股) |
| code | String | 股票代码 |
| name | String | 股票名称 |

### KLine (日K缓存)
| 字段 | 类型 | 说明 |
|------|------|------|
| stockKey | String | market.code |
| closes | List\<Double\> | 收盘价序列 (按时间正序) |
| lastUpdate | long | 最后更新时间戳 |

### MAEvent (MA交叉事件)
| 字段 | 类型 | 说明 |
|------|------|------|
| stockKey | String | market.code |
| stockName | String | 名称 |
| maPeriod | int | MA 周期 |
| direction | Direction | BREAK_UP / BREAK_DOWN |
| price | double | 触发价格 |
| maValue | double | MA 值 |
| session | TradingSession | 触发时段 |
| timestamp | long | 事件时间 |

### PriceAlert (波动告警)
| 字段 | 类型 | 说明 |
|------|------|------|
| stockKey | String | market.code |
| stockName | String | 名称 |
| price | double | 当前价 |
| preClose | double | 前收盘价 |
| changePercent | double | 涨跌幅 |
| direction | Direction | UP / DOWN |
| threshold | double | 触发阈值 |
| session | TradingSession | 触发时段 |
| timestamp | long | 事件时间 |

### TradingSession
枚举值: `PRE_MARKET`, `REGULAR`, `AFTER_HOURS`, `OVERNIGHT`, `CLOSED`

### FluctuationConfigEntity (v1.1 BF-2)
单例行 (id 恒为 1)，持久化波段监控规则。
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 恒为 1（单例） |
| logic | String | 规则组合逻辑 OR / AND |
| rulesJson | String | 规则列表 JSON，如 `[{"windowMinutes":3,"thresholdPercent":3.0},{"windowMinutes":5,"thresholdPercent":5.0}]` |

启动时 `TimeWindowFluctuationMonitor` 从该行加载；库为空时写入默认种子 (3min>=3% OR 5min>=5%)；`updateConfig` 同步覆写该行。`GET/POST /api/fluctuation-config` 读写均经 monitor 走数据库，刷新/重启后页面显示库中最新值，删空保存后不回退默认。

## 服务层约定

### FutuConnectionManager (核心)
- 单例，持有 volatile `FTAPI_Conn_Qot` 和 `FTAPI_Conn_Trd`
- `@PostConstruct`: FTAPI.init() → connect()
- `connect()`: 创建新连接对象 → setConnSpi/setQotSpi → initConnect(ip, port, encrypt)
- `onDisconnect()`: 触发 ScheduledExecutorService 重连
- `reconnect()`: close 旧连接 → 置 null → 指数退避 → connect() → 通知订阅恢复
- `@PreDestroy`: close 连接 → FTAPI.unInit()
- 使用 ReentrantLock 防并发重连
- 对外暴露 `getConnQot()` / `isReady()`

### AsyncRequestBridge
- ConcurrentHashMap\<Integer serialNo, CompletableFuture\> pendingMap
- register(serialNo): 创建并返回 CompletableFuture (超时 10s)
- 在 onReply_* 中: complete/completeExceptionally
- reconnect 时: failAll() 清空 pendingMap + 所有 future completeExceptionally

### StockGroupService
- `getUserSecurityGroup()`: 拉取用户所有股票分组
- `getUserSecurity(groupName)`: 拉取指定分组的股票列表
- 按 futu.filter.group-name 配置过滤

### KLineService
- `fetchDailyKLines(stock)`: 拉取最近 100 根日 K (KLType_Day)
- 缓存收盘价序列到 ConcurrentHashMap
- `@Scheduled`: 定时刷新 (60s)

### QuoteSubscriptionService
- `subscribeAll(stocks)`: 批量订阅 SubType_Basic
- `resubscribeAll()`: 重连后恢复订阅
- 内部记录已订阅股票列表

### MarketSessionService
- `@Scheduled`: 轮询 getMarketState (30s)
- 映射 QotMarketState → TradingSession (分美股/A股)
- 对外暴露 `getSession(market)` / `isTrading(market)`

## 核心数据流

```
[启动] ApplicationStartupRunner
  │
  ├─→ FutuConnectionManager.connect()
  ├─→ StockGroupService.fetchStocks(groupName)
  ├─→ KLineService.fetchDailyKLines(每股)
  ├─→ MACalculator.init(收盘价序列)
  └─→ QuoteSubscriptionService.subscribeAll()

[实时] onPush_UpdateBasicQuote
  │
  ├─→ PriceFluctuationMonitor.check(股票, 当前价, 前收盘)
  │     └─→ AlertCoordinator.onEvent(PriceAlert)
  └─→ MACrossoverMonitor.check(股票, 当前价, MA值)
        └─→ AlertCoordinator.onEvent(MAEvent)

[告警分发] AlertCoordinator (NR-1/NR-2)
  │
  ├─→ AlertNoiseFilter.shouldSendEmail(type, dedupKey, timestamp)
  │     └─→ true: EmailNotificationService.sendMAEventAlert(event)
  │     └─→ false: 不发邮件
  ├─→ 始终 recordAlert() → ring buffer + alert_records 表 (suppressed 标记)
  │
[波段波动] FluctuationAlertScheduler (NR-1)
  │
  ├─→ AlertNoiseFilter.shouldSendEmail("FLUCTUATION", stockKey:direction)
  └─→ 仅通过降噪的股票进入批量邮件

[MA破位] MABreakdownScanner (NR-1)
  │
  ├─→ AlertNoiseFilter.shouldSendEmail("BREAKDOWN", stockKey)
  └─→ 仅通过降噪的股票进入批量邮件

[买卖点] TradingSignalScanner (NR-1)
  │
  ├─→ notifiedKeys 去重 (永久, 会话级)
  ├─→ AlertNoiseFilter.shouldSendEmail("SIGNAL", stockKey:type) (短期去重)
  └─→ 仅通过两层去重的信号触发邮件

[断线重连] onDisconnect
  │
  ├─→ 关闭旧连接 (close + null)
  ├─→ 指数退避等待
  ├─→ FutuConnectionManager.connect()
  └─→ QuoteSubscriptionService.resubscribeAll()
```

## 开发约束

- 每次只处理一个任务
- 只修改完成当前任务所必需的文件
- 不擅自引入新依赖
- 不擅自重构无关代码
- 匹配项目现有代码风格
- 编码前必须读取 docs/PRD.md、docs/DESIGN.md、docs/ARCHITECTURE.md

## 禁止破坏的逻辑

- 连接管理器的重连逻辑（close → null → reconnect → resubscribe）
- AsyncRequestBridge 的 pendingMap 清理（reconnect 时必须 failAll）
- AlertCoordinator 的冷却期逻辑（不能因邮件失败而跳过冷却记录）
- MA 计算的收盘价序列顺序（必须按时间正序）
- 分组拉取的股票过滤逻辑（按 group-name 配置）

## 验收标准

### v1.1 Bugfix 验收（BF-1/BF-2/BF-3）
- [ ] BF-1: 点击首页邮件开关可切换开启/关闭三态；未配置时不可点
- [ ] BF-2: 首启默认 3min>=3% OR 5min>=5%；保存入库；刷新/重启后显示库中最新值；删空保存不回退默认
- [ ] BF-3: 首次扫描不发买卖点邮件；仅实时新信号触发邮件；重启不补发历史 MA 告警
- [ ] v1.1 新增/修改代码不影响 v1.0 主流程（连接/重连/订阅/MA计算）

- [ ] mvn compile 编译通过
- [ ] mvn test 单元测试通过
- [ ] 单元测试覆盖率: MA 计算、交叉检测、时段映射、告警节流
- [ ] 服务能连接 OpenD 并保持连接
- [ ] 断线重连机制正常工作
- [ ] Docker 镜像可构建并运行
