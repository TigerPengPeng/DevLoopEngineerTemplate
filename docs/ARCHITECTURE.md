# ARCHITECTURE — 技术架构文档

> 状态: **frozen** | draft-frozen | frozen
> 版本: v1.0
> 最后更新: 2026-06-25
> 由 Arch Loop 产出，Code Loop 消费。

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

[告警分发] AlertCoordinator
  │
  ├─→ 检查冷却期 (每股每事件类型 15min)
  └─→ EmailNotificationService.send(事件)

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

- [ ] mvn compile 编译通过
- [ ] mvn test 单元测试通过
- [ ] 单元测试覆盖率: MA 计算、交叉检测、时段映射、告警节流
- [ ] 服务能连接 OpenD 并保持连接
- [ ] 断线重连机制正常工作
- [ ] Docker 镜像可构建并运行
