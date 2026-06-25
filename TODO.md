# TODO — Futu 股票监听系统

> 任务清单。由 Arch Loop 产出，Code Loop 消费。
> 严格按顺序执行，一次只勾选一个任务。

## 任务列表

- [x] T1 [type:devops] Maven 骨架与项目基础
  - 修改目标: 建立 Maven 项目结构，可编译
  - 允许修改范围: pom.xml, .gitignore, src/main/resources
  - 不允许破坏的逻辑: loop 系统脚本 (scripts/, .loop/)
  - 验收标准: mvn compile 成功

- [x] T2 [type:backend] 配置层
  - 修改目标: FutuProperties + NotificationProperties + application.yml
  - 允许修改范围: src/main/java/.../config/, src/main/resources/
  - 不允许破坏的逻辑: T1 的 pom.xml
  - 验收标准: 配置类能注入，配置值正确绑定

- [x] T3 [type:backend] 数据模型
  - 修改目标: TradingSession, Direction, StockInfo, MAEvent, PriceAlert
  - 允许修改范围: src/main/java/.../model/
  - 不允许破坏的逻辑: 无（新模块）
  - 验收标准: 模型类编译通过，字段完整

- [x] T4 [type:backend] AsyncRequestBridge + SPI 回调分发
  - 修改目标: AsyncRequestBridge, FutuQuoteHandler, FutuConnHandler
  - 允许修改范围: src/main/java/.../futu/
  - 不允许破坏的逻辑: 无（新模块）
  - 验收标准: Bridge 注册/完成/超时/清空逻辑正确

- [x] T5 [type:backend] OpenD 连接管理器
  - 修改目标: FutuConnectionManager（重连 + 健康检查 + 资源清理）
  - 允许修改范围: src/main/java/.../futu/
  - 不允许破坏的逻辑: T4 的 SPI 回调接口
  - 验收标准: 连接/断线/重连/关闭流程完整，无内存泄露

- [x] T6 [type:backend] 账户与分组服务
  - 修改目标: StockGroupService
  - 允许修改范围: src/main/java/.../account/
  - 不允许破坏的逻辑: T4/T5 的连接管理接口
  - 验收标准: getUserSecurityGroup + getUserSecurity 正确返回

- [x] T7 [type:backend] K线服务与MA计算引擎
  - 修改目标: KLineService, MACalculator, CrossoverDetector
  - 允许修改范围: src/main/java/.../market/, .../indicator/
  - 不允许破坏的逻辑: T3 的数据模型
  - 验收标准: 日K拉取+缓存，MA计算正确，交叉检测正确

- [x] T8 [type:backend] 行情订阅与市场时段服务
  - 修改目标: QuoteSubscriptionService, MarketSessionService
  - 允许修改范围: src/main/java/.../market/
  - 不允许破坏的逻辑: T5 连接管理, T7 K线缓存
  - 验收标准: 订阅/重订阅正确，时段映射正确

- [x] T9 [type:backend] 监控与告警协调
  - 修改目标: PriceFluctuationMonitor, MACrossoverMonitor, AlertCoordinator
  - 允许修改范围: src/main/java/.../monitor/
  - 不允许破坏的逻辑: T3 数据模型, T7 MA计算
  - 验收标准: 波动/交叉检测正确，节流逻辑正确

- [x] T10 [type:backend] 邮件通知
  - 修改目标: EmailNotificationService, NotificationTemplate
  - 允许修改范围: src/main/java/.../notification/
  - 不允许破坏的逻辑: T3 事件模型
  - 验收标准: 邮件发送成功，模板内容完整

- [x] T11 [type:backend] 启动编排
  - 修改目标: ApplicationStartupRunner + AutoTradingApplication
  - 允许修改范围: src/main/java/.../startup/, 主类
  - 不允许破坏的逻辑: T5-T10 的全部服务接口
  - 验收标准: 启动流程完整串联，编译通过

- [x] T12 [type:devops] Docker 部署
  - 修改目标: Dockerfile, docker-compose.yml, .env.example
  - 允许修改范围: 项目根目录
  - 不允许破坏的逻辑: 无
  - 验收标准: docker compose up 可启动

- [x] T13 [type:test] 单元测试
  - 修改目标: MACalculator, CrossoverDetector, MarketSessionService, AlertCoordinator 测试
  - 允许修改范围: src/test/
  - 不允许破坏的逻辑: 无（测试不改动源码）
  - 验收标准: mvn test 全部通过

## 已完成

- (无)
