# DESIGN — 服务设计规范

> 状态: **frozen** | draft-frozen | frozen
> 版本: v1.0
> 最后更新: 2026-06-25
> 由 Design Loop 产出，Arch Loop 消费。

## 设计关键词

- 整体气质: 稳健、可靠、低调（运维型服务，无视觉界面）
- 可观测性: 结构化日志 + actuator 健康检查 + 事件审计
- 邮件风格: 简洁清晰，关键信息一眼可见，涨跌方向用颜色区分

## 需要避免的问题

- 邮件内容过于冗长，关键信息被淹没
- 日志噪音过大，淹没真正的事件信息
- 配置项命名混乱，难以理解用途

## 日志输出规范

### 日志级别

| 级别 | 用途 |
|------|------|
| ERROR | 连接失败、重连失败、邮件发送失败、严重异常 |
| WARN | 断线检测、订阅额度警告、请求超时、配置缺失告警 |
| INFO | 启动、连接成功、分组拉取、订阅成功、事件触发、邮件发送 |
| DEBUG | 行情推送明细、MA 计算过程、内部状态变化 |

### 日志格式

- 框架: SLF4J + Logback
- 输出: 控制台（开发） + 文件（生产）
- 文件路径: logs/autotrading.log
- 滚动策略: 按天滚动，保留 30 天，单文件最大 100MB
- 格式: `%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n`

### 关键事件日志模板

```
连接成功:     INFO  Futu OpenD 连接成功 (connID={})
断线检测:     WARN  OpenD 连接断开 (connID={}, errCode={}), 启动重连
重连成功:     INFO  重连成功, 恢复 {} 只股票订阅
订阅成功:     INFO  订阅成功 (stock={}, subType=Basic)
MA 突破:       INFO  MA 突破事件 (stock={}, MA{}, price={}, direction=BREAK_UP)
MA 跌破:       INFO  MA 跌破事件 (stock={}, MA{}, price={}, direction=BREAK_DOWN)
波动告警:     INFO  波动告警 (stock={}, change={}, threshold={})
邮件发送:     INFO  邮件已发送 (to={}, subject={})
邮件失败:     ERROR 邮件发送失败 (to={}, error={})
```

## 邮件模板设计

### 突破/跌破 MA 告警邮件

**主题**: `[告警] {股票名称}({代码}) {突破/跌破} MA{周期}`

**正文** (HTML):

```
| 项目 | 内容 |
|------|------|
| 股票 | {名称} ({代码}) |
| 事件 | 突破 / 跌破 MA{周期} |
| 当前价 | {价格} |
| MA{周期} | {MA值} |
| 交易时段 | {盘前/盘中/盘后/夜盘} |
| 市场 | {美股/A股} |
| 时间 | {yyyy-MM-dd HH:mm:ss} |
```

- 突破(BREAK_UP): 用绿色标记价格和方向
- 跌破(BREAK_DOWN): 用红色标记价格和方向

### 价格波动告警邮件

**主题**: `[告警] {股票名称}({代码}) 日内波动 {涨/跌} {幅度}%`

**正文** (HTML):

```
| 项目 | 内容 |
|------|------|
| 股票 | {名称} ({代码}) |
| 当前价 | {价格} |
| 前收盘 | {前收盘价} |
| 波动幅度 | {涨/跌} {幅度}% |
| 阈值 | ±{阈值}% |
| 交易时段 | {时段} |
| 市场 | {市场} |
| 时间 | {yyyy-MM-dd HH:mm:ss} |
```

- 涨幅超限: 绿色
- 跌幅超限: 红色

## 配置项设计

所有配置通过 `application.yml` + 环境变量覆盖。

### Futu OpenD 配置

```yaml
futu:
  opend:
    ip: 127.0.0.1
    port: 11111
    encrypt: false
    rsa-key: ""           # 加密连接时使用
  filter:
    group-name: ""        # 股票分组名称，空则拉取全部
  monitor:
    ma-periods: [5, 13, 30, 55]
    price-change-threshold: 2.0   # 百分比
    alert-cooldown-minutes: 15
    kline-refresh-interval: 60000  # 毫秒
    market-state-poll-interval: 30000  # 毫秒
  reconnect:
    initial-delay-ms: 5000
    max-delay-ms: 60000
    multiplier: 2.0
```

### 邮件配置

```yaml
spring:
  mail:
    host: ${MAIL_HOST:}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true

notification:
  mail:
    to: ${MAIL_TO:}       # 逗号分隔多个收件人
    enabled: true
```

## 异常状态行为

### 连接断开
- 日志: WARN + connID + errCode
- 行为: 启动重连流程（指数退避），重连后恢复全部订阅
- 期间: 暂停事件检测（无行情数据）

### OpenD 未响应
- 日志: WARN + 请求类型 + 超时
- 行为: AsyncRequestBridge 超时（默认 10s），返回失败
- 业务层: 跳过本次操作，下次定时任务重试

### 空分组 / 无持仓
- 日志: WARN
- 行为: 服务正常启动，无股票监控，等待用户在 Futu 端添加股票

### 订阅额度不足
- 日志: WARN + 剩余额度
- 行为: 分批订阅，超出额度的股票记录跳过

### 邮件发送失败
- 日志: ERROR + 原因
- 行为: 不影响主流程，下次事件仍会触发告警
- 冷却: 告警事件已被记录，不会因邮件失败而重复触发

### 空数据状态（无行情推送）
- 日志: DEBUG（正常，非交易时段无推送）
- 行为: 等待市场开放

### 加载中状态（启动初始化）
- 日志: INFO，分阶段输出进度
- 行为: 初始化完成前不开始事件检测
