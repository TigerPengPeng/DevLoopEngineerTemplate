# Futu 股票监听系统

> 基于 Java + Spring Boot 3 的 headless 股票监听服务，对接 Futu OpenD，实时监控美股和 A 股的价格波动与 MA 均线突破，事件触发时邮件告警。

## 项目简介

- 对接 Futu OpenD，拉取账户股票分组
- 订阅实时行情，计算 MA5/13/30/55 均线
- 监控价格突破/跌破 MA 均线
- 监控日内价格波动（可配置阈值，默认 ±2%）
- 美股支持盘前、盘中、盘后、夜盘四个时段
- A 股市场支持
- 连接池断线自动重连，无内存泄露
- 事件触发邮件通知，带冷却期防刷屏
- Docker 一键部署

## 技术栈

- Java 17
- Spring Boot 3.3.6 (非 Web, actuator 健康检查)
- Maven 3.9.x
- Futu OpenAPI SDK (`com.futunn.openapi:futu-api:9.3.5308`)
- Spring Boot Mail (JavaMail)
- Docker + docker-compose

## 快速开始

### 前置要求

- JDK 17+
- Maven 3.9+
- Futu OpenD 已运行（默认 127.0.0.1:11111）
- SMTP 邮件服务（Gmail / 企业邮箱等）

### 本地开发

```bash
# 克隆项目
git clone <repo-url>
cd AutoTradingSystem

# 配置环境变量（复制模板并编辑）
cp .env.example .env
# 编辑 .env 填入 SMTP 和 OpenD 配置

# 编译
mvn compile

# 运行测试
mvn test

# 启动服务
mvn spring-boot:run
```

### Docker 部署

```bash
# 配置环境变量
cp .env.example .env
# 编辑 .env

# 构建并启动
docker compose up -d

# 查看日志
docker compose logs -f

# 健康检查
curl http://localhost:8080/actuator/health
```

## 环境变量说明

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `OPEND_IP` | 127.0.0.1 | Futu OpenD 地址 |
| `OPEND_PORT` | 11111 | Futu OpenD 端口 |
| `OPEND_ENCRYPT` | false | 是否加密连接 |
| `FUTU_GROUP_NAME` | (空) | 股票分组名称，空则使用第一个分组 |
| `PRICE_CHANGE_THRESHOLD` | 2.0 | 价格波动阈值 (%) |
| `ALERT_COOLDOWN_MINUTES` | 15 | 告警冷却期 (分钟) |
| `KLINE_REFRESH_INTERVAL` | 60000 | K线刷新间隔 (毫秒) |
| `MARKET_STATE_POLL_INTERVAL` | 30000 | 市场状态轮询间隔 (毫秒) |
| `RECONNECT_INITIAL_DELAY_MS` | 5000 | 重连初始延迟 (毫秒) |
| `RECONNECT_MAX_DELAY_MS` | 60000 | 重连最大延迟 (毫秒) |
| `MAIL_HOST` | (空) | SMTP 服务器 |
| `MAIL_PORT` | 587 | SMTP 端口 |
| `MAIL_USERNAME` | (空) | SMTP 用户名 |
| `MAIL_PASSWORD` | (空) | SMTP 密码 |
| `MAIL_TO` | (空) | 收件人 (逗号分隔) |
| `MAIL_ENABLED` | true | 是否启用邮件通知 |

## 本地开发命令

```bash
mvn compile          # 编译
mvn test             # 运行测试
mvn package          # 打包
mvn spring-boot:run  # 启动服务
```

## 构建命令

```bash
mvn clean package -DskipTests    # 跳过测试打包
docker compose build              # 构建 Docker 镜像
```

## 部署方式

### Docker Compose (推荐)

```bash
docker compose up -d
```

服务暴露 actuator 健康检查端点: `http://localhost:8080/actuator/health`

### Java 直接运行

```bash
mvn package -DskipTests
java -jar target/futu-stock-monitor-1.0.0.jar
```

## 目录结构说明

```
AutoTradingSystem/
├── pom.xml                    # Maven 构建
├── Dockerfile                 # 多阶段 Docker 构建
├── docker-compose.yml         # 容器编排
├── .env.example               # 环境变量模板
├── docs/                      # 项目文档
│   ├── PRD.md                 # 产品需求
│   ├── DESIGN.md              # 设计规范
│   └── ARCHITECTURE.md        # 技术架构
├── src/main/java/com/autotrading/
│   ├── AutoTradingApplication.java   # Spring Boot 主类
│   ├── config/                       # 配置类
│   ├── futu/                         # Futu SDK 集成层
│   ├── account/                      # 账户与分组服务
│   ├── market/                       # 行情与市场时段
│   ├── indicator/                    # MA 计算与交叉检测
│   ├── monitor/                      # 监控与告警协调
│   ├── notification/                 # 邮件通知
│   ├── startup/                      # 启动编排
│   └── model/                        # 数据模型
└── src/test/                         # 单元测试
```

## Loop Engineering 系统

本项目使用 Loop Engineering 闭环系统管理开发流程:

- PRD Loop → Design Loop → Arch Loop → Code Loop → Test Loop → Regression Loop
- 每阶段独立验证 (maker/checker 分离)
- 详细文档见 `docs/` 目录

```bash
./scripts/loop-cli.sh status     # 查看状态
./scripts/loop-cli.sh help       # 帮助
```
