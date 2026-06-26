# DESIGN — 设计规范

> 状态: **draft** | draft | draft-frozen | frozen
> 版本: v1.1
> 最后更新: 2026-06-26
> 由 Design Loop 产出，Arch Loop / Code Loop 消费。

---

## v1.1 变更说明

v1.0 将产品定位为「运维型服务，无视觉界面」，**与实际实现矛盾**：v1.0 已交付 4 个暗色主题前端页面（`index.html` / `stock.html` / `error-logs.html` / `sector-trend.html`），而 v1.1 的 BF-1、BF-2 正是前端缺陷修复。本次 Design Loop 以**已实现的真实 UI**为准，重写为完整设计系统文档，覆盖：

- 完整 Design Token（色彩 / 字体 / 间距 / 圆角 / 阴影 / 动效）
- 组件规范（按钮 / 卡片 / 区块 / 表格 / 表单 / 徽标 / 导航 / 图表）
- 四个页面逐页视觉与布局规范
- 交互与动效规范（含 BF-1 开关、BF-2 规则保存的状态流转）
- 三大异常状态（loading / empty / error）的视觉与恢复路径
- 响应式断点策略
- 保留并更新服务端规范（日志 / 邮件模板 / 配置 / 异常行为）

> 本次为 **design-only**，不新增页面、不引入设计框架，只把既有实现固化成可被 Code Loop 直接引用的边界。

## 设计关键词

- **整体气质**：克制、信息密集、运维工具向——为「长时间盯盘、快速扫读」设计，不追求营销感。
- **视觉基调**：深色（GitHub dark 衍生），低对比的中性底 + 高对比的数据强调色。
- **信息层级**：数值与状态用等宽字体，标签与正文用无衬线；涨/跌/告警用语义色一以贯之。
- **可观测性**：结构化日志 + actuator + 事件审计（服务端），邮件告警关键信息一眼可见。
- **需要避免**：纯渐变/SVG 充当主视觉；单一色调独占界面；营销式大留白卡片；装饰性光晕/orb。

## 需要避免的问题

- 邮件内容冗长，关键信息被淹没。
- 日志噪音过大，淹没真正的事件信息。
- 配置项命名混乱，难以理解用途。
- 涨跌色不一致（前端绿涨红跌，邮件也必须一致语义）。
- 暗色界面使用过低对比的灰文字（低于 WCAG AA 会被驳回）。

---

## 一、Design Token

> 所有页面共用同一组 `:root` 变量。Code Loop 实现新元素时必须引用 Token，禁止硬编码裸色值。

### 1.1 色彩体系（暗色界面）

| Token | 值 | 语义 |
|-------|------|------|
| `--bg` | `#0d1117` | 页面底色 |
| `--surface` | `#161b22` | 卡片 / 区块 / 输入框底 |
| `--border` | `#30363d` | 描边、分隔线 |
| `--text` | `#e6edf3` | 主文字 |
| `--text-dim` | `#8b949e` | 次要文字、标签 |
| `--green` | `#3fb950` | 涨 / 连接正常 / 买入 / Active |
| `--red` | `#f85149` | 跌 / 断线 / 卖出 / 错误 / 未配置 |
| `--blue` | `#58a6ff` | 链接 / 焦点 / 主操作 hover |
| `--yellow` | `#d29922` | MA 告警 / 盘前 / 警告 |
| `--purple` | `#bc8cff` | 盘后 / 夜盘 |
| `--orange` | `#ea580c` | MA 破位（仅用于徽标） |

**语义色命名原则**：颜色绑定语义（涨/跌/告警），不绑定组件名。绿涨红跌为全站唯一约定。

**已登记的局部不一致（待 Code Loop 收敛，不阻塞本轮）**：
- 首页 `.tab-btn.active` 用了 `--accent, #3b82f6`（Tailwind blue），与 `--blue #58a6ff` 不统一，应改为 `--blue`。
- 首页「买卖点通知」标签使用 emoji `📈`（stock.html 信号面板标题）；按规范应去除 emoji，纯文字。

### 1.2 徽标（pill）语义色映射

徽标 = `background: rgba(语义色, 0.15)` + `color: 语义色`，统一 `padding 2px 8px`、`radius 12px`、`font 11px/600`。

| 徽标类 | 语义 |
|--------|------|
| `.pill.green` | Active / 买入 / 成功 / 看多 |
| `.pill.red` | 卖出 / 失败 / 看空 / 跌破 |
| `.pill.blue` | 波动告警 / 行业趋势 / 链接态 |
| `.pill.yellow` | MA 告警 / 盘前 |
| `.pill.purple` | 盘后 / 夜盘 |
| `.pill.orange` | MA 破位 |
| `.pill.dim` | Standby / 休市 / 中性 |

**交易时段徽标映射**（首页 stocks 表 / 告警列表共用）：

| 时段 | 类 |
|------|----|
| 盘中 | `session-regular`（绿） |
| 盘前 | `session-pre`（黄） |
| 盘后 | `session-after`（紫） |
| 夜盘 | `session-overnight`（紫） |
| 休市/其他 | `session-closed`（灰） |

### 1.3 图表 MA 线色（独立语义集）

K 线图 MA 叠加线使用专属色，**不复用语义色 Token**（图表可读性优先）：

| MA | 颜色 |
|----|------|
| MA5 | `#f5a623`（橙） |
| MA13 | `#58a6ff`（蓝） |
| MA30 | `#bc8cff`（紫） |
| MA55 | `#3fb950`（绿） |

图例（`.ma-legend`）必须与此色映射一致。K 线涨绿跌红沿用 `--green/--red`。

### 1.4 邮件专用色（浅色上下文）

邮件在浅色邮件客户端渲染，使用**浅色背景配套的深色文字**，**不是**暗色界面色：

| 邮件色 | 值 | 语义 |
|--------|------|------|
| `GREEN` | `#16a34a` | 突破 / 涨 / 看多 |
| `RED` | `#dc2626` | 跌破 / 跌 / 高风险 |
| 辅助灰 | `#8b949e` | 副信息 |
| 表头底 | `#f3f4f6` | 风险报告表头 |
| 边框 | `#e5e7eb` | 邮件表格边框 |

> 涨跌语义在两套上下文中一致（绿涨红跌），仅明度适配载体。

### 1.5 字体

- 正文 / 标签：`-apple-system, system-ui, "Segoe UI", sans-serif`
- 数值 / 时间 / 代码 / 日志：`"SF Mono", "Cascadia Code", monospace`

| 用途 | 字号 | 字重 | 备注 |
|------|------|------|------|
| 页面主标题（header h1） | 18px | 600 | 含状态圆点 |
| 个股标题（stock-title） | 20px | 700 | 个股详情页 |
| 区块标题（section-header） | 13px | 600 | — |
| 卡片标题（card-title） | 11px | 400 | uppercase + letter-spacing 0.5px |
| 正文 / 单元格 | 13px | 400 | — |
| 数值（value / num） | 13px | 600 | 等宽 |
| 徽标 | 11px | 600 | — |
| 辅助说明 | 11–12px | 400 | `--text-dim` |

行高基准 1.5。**禁止**用负 letter-spacing。

### 1.6 间距 / 圆角 / 阴影

间距刻度（4 倍数体系）：`4 · 6 · 8 · 10 · 12 · 14 · 16 · 20 · 24`。

| 元素 | 圆角 |
|------|------|
| 输入框 / 小按钮 | 4px |
| 按钮（btn） | 6px |
| 卡片 / 区块（card / section / panel） | 8px |
| 徽标（pill） | 12px |

阴影：仅状态圆点 / 破位圆点使用 `box-shadow: 0 0 4~6px <语义色>`（glow，表示「活跃/告警」）。**不使用**装饰性阴影或光晕。

### 1.7 动效

| Token | 值 | 用途 |
|-------|------|------|
| `--transition-fast` | `0.15s` | hover 色变（tab、链接） |
| `--transition` | `0.2s` | 按钮 border/opacity 切换 |
| `--spin` | `0.6s linear infinite` | 加载 spinner |
| 轮询 | `5s`（首页）/ `10s`（错误日志） | 自动刷新 |

缓动默认 `ease`。所有动效须响应 `prefers-reduced-motion`：reduce 时禁用 spinner 动画与 glow 脉冲（当前实现需补此媒体查询，登记为待办，不阻塞）。

---

## 二、组件规范

### 2.1 按钮（btn）

```
inline-flex; gap 6px; padding 6px 14px;
border 1px var(--border); radius 6px;
background var(--surface); color var(--text); font 13px;
```

| 状态 | 视觉 |
|------|------|
| default | `border var(--border)` |
| hover | `border-color var(--blue)` |
| disabled | `opacity 0.5; cursor: wait`（用于加载中） |
| 加载中 | 文案替换为 `spinner`（12px，顶边透明）+ 文本，如 `Done` / `扫描中...` |

操作按钮采用「**文案即时反馈**」模式：点击后文案变为动作状态（`刷新中...` / `生成中...` / `Done` / `Error`），1.5–3s 后恢复原文案并解锁。详见各页面交互章节。

**导航链接**（行业趋势 / 错误日志 / 返回主页）复用 `.btn` 样式 + `text-decoration:none`。

### 2.2 卡片 / 区块 / 面板

三档容器，语义不同，不嵌套使用（卡片内不再套卡片）：

- **卡片（card）**：仪表盘顶部统计区，`grid auto-fit minmax(240px,1fr)`，gap 12px。内含 `card-title` + 若干 `card-row`（label 左 / value 右）。
- **区块（section）**：整宽带标题容器，`section-header`（标题左 + 操作右）+ 内容区。用于股票表、监控配置、告警标签区、板块表。
- **面板（panel）**：个股详情侧栏与图表区，`panel-header` + `panel-body`，圆角 8px。

### 2.3 表格（table）

- `width 100%`；`th` 11px uppercase `--text-dim`；`td` 13px；行分隔 `rgba(48,54,61,0.5)`。
- 数值列加 `.num`（右对齐 + 等宽）。涨跌文字加 `.up-text`（绿）/ `.down-text`（红）。
- 可点击行（如首页股票行跳详情）用 `cursor:pointer`，**不**用按钮化外观，整行可点。

### 2.4 表单控件

- **输入框（number/text）**：`background var(--surface); color var(--text); border 1px var(--border); radius 4px; padding 4px`。数值输入固定 `width 60px`。
- **下拉（select）**：与输入框同底，`focus` 时 `border-color var(--blue)`。
- **波段规则行**（BF-2 核心）：grid 行 = `[分钟数输入] [文字「分钟内波动 >=」] [百分比输入] [文字「%」] [删除按钮]`，行间距 6px。新增规则按钮 `+ 添加规则` 置于规则列表下方。

### 2.5 状态圆点（status-dot）

```
width 10px; height 10px; radius 50%;
```

| 状态 | 视觉 |
|------|------|
| up（连接正常） | `background var(--green); box-shadow 0 0 6px var(--green)` |
| down（断线） | `background var(--red); box-shadow 0 0 6px var(--red)` |

邮件开关圆点 8px，颜色随开关状态：开启绿+glow、关闭灰无 glow、未配置红。

### 2.6 导航与标签页

- **顶部导航**：header 右侧 `header-right`（flex, gap 16px），含跨页链接 + 操作按钮 + 更新时间。等宽字体显示时间。
- **标签页（tab-btn）**：下边框激活态，`active` 时 `border-bottom 2px var(--blue)`、文字 `--text`；非激活 `--text-dim`。首页告警区三标签：买卖点通知 / MA 告警 / 邮件通知。

### 2.7 图表

个股详情使用 `lightweight-charts@4.2.3`。画布配置必须与 Token 对齐：背景 `#161b22`、网格 `#21262d`、价格轴边框 `#30363d`、文字 `#8b949e`。K 线与 MA 线色见 1.3。成交量柱涨绿跌红半透明（`rgba(63,185,80,0.4)` / `rgba(248,81,73,0.4)`），独立 `priceScaleId` 贴底（`scaleMargins top 0.8`）。买卖信号用箭头 marker（买 `arrowUp` 绿下、卖 `arrowDown` 红上）。

---

## 三、页面规范

### 3.1 首页仪表盘（index.html）★ BF-1 / BF-2 主战场

布局自上而下：

1. **Header**：左侧 `[状态圆点] Futu Stock Monitor`；右侧 `[行业趋势] [错误日志] [邮件开关] [Refresh] [更新时间]`。
2. **统计卡片网格**：Connection / Monitoring / Config 三卡。
3. **Stocks 区块**：股票表（Code/Name/Market/Price/Change/Session），整行可点跳个股页。
4. **监控配置区块**（BF-2）：波段规则逻辑选择（OR/AND）+ 规则行列表 + `[+ 添加规则]` + `[保存规则]`；右上 `[刷新股票列表]` `[MA破位扫描]`。
5. **告警标签区块**：三标签（买卖点通知 / MA 告警 / 邮件通知）+ 列表。

**邮件开关按钮（BF-1）三态规范**（修复后须满足）：

| 状态 | 圆点 | 文案 | 边框 | 可点 |
|------|------|------|------|------|
| 开启 | 绿 + glow | `邮件开启` | `rgba(63,185,80,0.4)` | 是 |
| 关闭 | 灰 无 glow | `邮件关闭` | `rgba(139,148,158,0.4)` | 是 |
| 未配置（SMTP 空） | 红 无 glow | `邮件未配置` | 红 | **否**（disabled） |

切换语义：点击 = `GET` 取当前值 → 翻转 → `POST` → `refreshEmailToggle()` 重绘。**修复要点**：`toggleEmail/refreshEmailToggle/updateEmailToggle` 三个函数必须定义在全局作用域，**不能**嵌套在 `refresh()` 内（v1.1 的 bug 根因）。

**波段规则编辑器（BF-2）状态规范**：

- **加载中**：进入页面时 `GET /api/fluctuation-config`，期间规则区显示骨架（当前为空，可接受）；加载完成后 `renderFlucRules()`。
- **初始默认值**（数据库无历史时）：3 分钟 >= 3% **OR** 5 分钟 >= 5%。
- **保存**：`[保存规则]` → `POST /api/fluctuation-config`（logic + rules）。
- **持久化校验**：刷新页面 / 重启服务后，规则区显示数据库最新值，**不**回退默认值。
- **空状态**：删除全部规则并保存后，刷新页面规则区为空（不回退默认）。
- **数值约束**：`windowMinutes` 1–120；`thresholdPercent` ≥ 0.1，步进 0.1。

**轮询**：`setInterval(refresh, 5000)` 自动刷新状态/股票/告警/邮件历史；`refresh()` 内单次 `GET /api/status` 拉取全部面板数据。

### 3.2 个股详情（stock.html）

- **Header**：`[← Back]` + 个股名 + 代码（等宽）+ 时段徽标。
- **主体网格** `1fr 340px`，>1100px 双栏、≤1100px 单栏。
  - 左：K 线图区块（图例 MA5/13/30/55）→ MA Breakout Alerts 面板 → Buy/Sell Signals 面板（顶部 `latest-advice` 蓝色提示条 + 信号列表 + 买卖计数摘要）。
  - 右侧栏：Analysis Strategy 下拉（MA均线交叉/MACD/RSI/布林带/KDJ/量价）+ 结果框（bullish 绿 / bearish 红 / neutral 灰）+ MA Status 表。

### 3.3 错误日志（error-logs.html）

- **Header**：`系统错误日志` + `[返回主页] [清空日志] [刷新]`。
- **日志条目**：`--surface` 卡 + **红色左边框 3px**；`log-header`（时间 + `[thread] logger` 蓝等宽）→ `log-msg` → 可选 `log-trace`（`--bg` 底、等宽、最大高 200px 滚动）。
- **轮询**：`setInterval(loadLogs, 10000)`，`limit=100`。

### 3.4 行业趋势报告（sector-trend.html）

- **Header**：`行业趋势报告` + `[返回主页] [立即生成] [历史报告 下拉]`。
- **市场总览盒（sentiment-box）**：日期 + `overallSentiment`。
- **板块表**（板块/成分/看多/看空/5日%/20日%/情绪/风险分）；风险分加粗。
- **成分股明细**：每个板块一个可展开/收起 section，`stock-detail` 默认隐藏，`.visible` 显示。
- **情绪徽标**：含「多」→ 绿、「空」→ 红、其他 → 黄。

---

## 四、交互与状态流转规范

### 4.1 即时反馈（200ms 内）

所有按钮点击后**立即**有视觉反馈：文案切换 + disabled，无需等待网络返回。异步完成后文案转为结果（`Done` / 计数 / `Error`），定时器 1.5–3s 后恢复原状。

### 4.2 核心旅程状态流转

**旅程 4（v1.1）— 邮件开关**：`idle → [点击] → GET当前值 → POST翻转 → 重绘(idle')`。失败：`console.error`，按钮停留在当前态（不卡死），下一次轮询 `refresh()` 会重新 `updateEmailToggle` 修正。

**旅程 4（v1.1）— 规则保存**：`idle → [编辑] → [保存] POST → idle(已持久化)`。失败：静默（当前实现 catch 空体）；**建议** Code Loop 增加保存失败的内联提示，但非本轮必须。

**旅程 5（v1.1）— 重启不收历史噪音**：前端无专门交互；`买卖点通知` 列表正常展示历史信号（**仅展示、不发邮件**），BF-3 的抑制逻辑在服务端。UI 上历史信号与实时信号视觉一致，不加「已抑制」标记。

### 4.3 三大异常状态（每页必须有视觉规范）

#### 加载中（loading）

| 场景 | 视觉 |
|------|------|
| 全页初始化 | 首页状态圆点未上色前默认态；个股页 `Loading...` / `分析中...` 文案 |
| 局部异步（按钮） | spinner + 文案（`扫描中...` / `生成中...`） |
| 图表数据未到 | `.loading` 居中灰字 `Analyzing...` / `No K-line data available` |
| `latest-advice` 未就绪 | 蓝色提示条 `分析中...` |

#### 空数据（empty）

每处空状态都用 `.empty-state`（居中、`padding 40~60px`、`--text-dim`），且**引导下一步**，不只是一句「暂无数据」：

| 场景 | 文案 |
|------|------|
| 无股票 | `No stocks loaded. Check OpenD connection and stock group.` |
| 无告警 | `No alerts yet. Events will appear here when triggered.` |
| 无买卖信号 | `暂无买卖点信号。系统会自动扫描符合量价买卖策略的股票。` |
| 无邮件记录 | `暂无邮件记录。触发告警后发送的邮件会在此显示。` |
| 无错误日志 | `暂无错误日志` |
| 无报告 | `尚未生成报告，点击「立即生成」` |

#### 错误（error）

| 场景 | 视觉 / 恢复 |
|------|------------|
| `GET /api/status` 失败（首页） | 状态圆点变红（down）+ 更新时间显示 `Fetch failed: {msg}`；5s 后自动重试 |
| K 线加载失败 | 图表区 `.loading` `Failed to load: {msg}` |
| 信号加载失败 | `信号加载失败: {msg}` |
| 错误日志加载失败 | `加载失败: {msg}` |
| 报告加载失败 | `加载失败: {msg}` |
| 分析接口返回 `error` | `signal-box neutral` 显示 `Error` + 信息 |
| 按钮动作异常 | 按钮文案短暂显示 `Error` 后恢复，**不**中断主流程 |

恢复原则：错误不卡死界面，定时轮询自动收敛；无数据的区域保留空状态文案。

---

## 五、响应式策略

移动优先思维，当前已实现的断点（须保持）：

| 断点 | 行为 |
|------|------|
| `max-width: 640px` | 首页统计卡片变单列；告警项隐藏 `alert-price` 与 `alert-session` 列（`grid 60px 1fr`） |
| `max-width: 1100px` | 个股详情双栏网格塌缩为单栏（图表在上、侧栏在下） |

- 数值列在窄屏隐藏属于「可接受的密度降级」，不算信息丢失。
- 文字不随视口缩放（禁止 `vw` 字号）；超长词用容器约束，不溢出。
- 导航栏在窄屏保持单行（按钮数量少），不引入汉堡菜单（页面少）。

---

## 六、可访问性

- **对比度**：`--text #e6edf3` / `--bg #0d1117` 远超 WCAG AA；`--text-dim #8b949e` 用于次要文字处于 AA 边界，仅用于非关键信息（标签、时间）。
- **徽标对比度风险**：11px 徽标文字在 `rgba(语义色,0.15)` 底上对部分色（黄/橙）可能不达 4.5:1。验收时以浏览器对比度检查为准；不达标处可加深底色透明度至 0.2。此为已知风险，登记给 Test Loop 的 a11y 审计。
- **键盘**：按钮/链接/输入原生可聚焦；`select:focus` 有 `--blue` 边框反馈。
- **色觉不依赖单一颜色**：涨跌除颜色外，个股页用箭头 marker（▲买/▼卖）、MA 用文字「+/%」与 above/below，不仅靠红绿。
- **reduced-motion**：spinner/glow 须在 `prefers-reduced-motion: reduce` 下停用（当前缺，登记待办）。

---

## 七、服务端规范（保留并更新）

### 7.1 日志输出规范

| 级别 | 用途 |
|------|------|
| ERROR | 连接失败、重连失败、邮件发送失败、严重异常 |
| WARN | 断线检测、订阅额度警告、请求超时、配置缺失告警 |
| INFO | 启动、连接成功、分组拉取、订阅成功、事件触发、邮件发送 |
| DEBUG | 行情推送明细、MA 计算过程、内部状态变化 |

- 框架 SLF4J + Logback；输出控制台 + 文件。
- 文件 `logs/autotrading.log`，按天滚动，保留 30 天，单文件最大 100MB。
- 格式 `%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n`。

关键事件模板：

```
连接成功:  INFO  Futu OpenD 连接成功 (connID={})
断线检测:  WARN  OpenD 连接断开 (connID={}, errCode={}), 启动重连
重连成功:  INFO  重连成功, 恢复 {} 只股票订阅
订阅成功:  INFO  订阅成功 (stock={}, subType=Basic)
MA 突破:    INFO  MA 突破事件 (stock={}, MA{}, price={}, direction=BREAK_UP)
MA 跌破:    INFO  MA 跌破事件 (stock={}, MA{}, price={}, direction=BREAK_DOWN)
波动告警:  INFO  波动告警 (stock={}, change={}, threshold={})
邮件发送:  INFO  邮件已发送 (to={}, subject={})
邮件失败:  ERROR 邮件发送失败 (to={}, error={})
```

### 7.2 邮件模板

由 `NotificationTemplate.java` 生成 HTML，使用浅色配套色（见 1.4）。

**MA 突破/跌破**：主题 `[告警] {名称}({代码}) {突破/跌破} MA{周期}`；正文 H2（突破绿/跌破红）+ 信息表（股票/事件/当前价/MA值/时段/市场/时间），价格与方向同色。

**当日风险报告**：`{市场} 当日风险股票汇总`；空数据时显示「今日无高风险股票，市场整体平稳」居中灰盒；有数据时表格（股票/风险分/等级/涨跌%/风险因素），60+ 高风险红、30–59 中等黄。

**盘中波动汇总**（BF-2 相关）：`盘中波动汇总: {N} 只股票触发规则`，含 logic 文案（AND→全部满足 / OR→任一满足）。

**价格波动告警**：主题 `[告警] {名称}({代码}) 日内波动 {涨/跌} {幅度}%`；表含当前价/前收盘/波动幅度/阈值/时段/市场/时间，涨幅绿跌幅红。

### 7.3 配置项

经 `application.yml` + 环境变量覆盖。关键项：

```yaml
futu:
  opend: { ip: 127.0.0.1, port: 11111, encrypt: false, rsa-key: "" }
  filter: { group-name: "" }
  monitor:
    ma-periods: [5, 13, 30, 55]
    price-change-threshold: 2.0
    alert-cooldown-minutes: 15
    kline-refresh-interval: 60000
    market-state-poll-interval: 30000
  reconnect: { initial-delay-ms: 5000, max-delay-ms: 60000, multiplier: 2.0 }

spring.mail: { host, port: 587, username, password, starttls: true }
notification.mail: { to, enabled: true }
```

> **BF-2 注意**：波段规则不再从 `application.yml` 静态读取，改为数据库持久化（默认 3min≥3% OR 5min≥5%）。`application.yml` 中的静态波段值仅作首启种子，不再作为运行时来源。

### 7.4 异常状态行为

| 异常 | 日志 | 行为 |
|------|------|------|
| 连接断开 | WARN + connID + errCode | 指数退避重连，重连后恢复全部订阅；期间暂停事件检测 |
| OpenD 未响应 | WARN + 请求类型 + 超时 | AsyncRequestBridge 超时（默认 10s）返回失败，业务层跳过本次、下次重试 |
| 空分组 / 无持仓 | WARN | 服务正常启动，无股票监控，等待用户在 Futu 端添加 |
| 订阅额度不足 | WARN + 剩余额度 | 分批订阅，超限股票跳过 |
| 邮件发送失败 | ERROR + 原因 | 不影响主流程；事件已记录不重复触发 |
| 无行情推送（非交易时段） | DEBUG | 等待市场开放 |
| 启动初始化（加载中） | INFO 分阶段 | 初始化完成前不开始事件检测 |
| 重启后历史信号（BF-3） | INFO | 首次扫描初始化去重集合，**不补发**历史邮件；仅实时新事件触发邮件 |

---

## 八、与 PRD/ARCHITECTURE 的一致性核对

- **PRD BF-1**：邮件开关三态视觉 + 全局作用域要求 → 已在 3.1 落规范。
- **PRD BF-2**：规则持久化、默认值、空状态 → 已在 3.1 / 7.3 落规范。
- **PRD BF-3**：历史信号仅展示不发送 → 4.2 / 7.4 落规范，UI 不加抑制标记。
- **PRD 功能黑名单**：不迁移前端框架、不新增波段以外持久化配置、不做多用户规则 → 本规范未引入任何框架或新配置面。
- **待澄清项移交**：BF-2 数据模型、BF-3「当前事件」定义、MA 破位扫描抑制范围，属 Arch Loop 决策，本规范不预设实现。

---

## 九、自检清单（Design Loop 产出前）

- [x] 配色体系完整（主/辅/中性/语义），无 one-note 单色调，无纯渐变主视觉
- [x] 组件规范覆盖按钮/卡片/表单/导航/表格/图表，含 hover/active/disabled 状态
- [x] 排版层级有明确字号与字重
- [x] error / loading / empty 三态每页均有视觉规范与恢复路径
- [x] PRD 四个页面（仪表盘/个股/日志/趋势）逐页有设计描述
- [x] 响应式断点覆盖
- [x] 可访问性：对比度、键盘、色觉不依赖单一颜色已覆盖（边界项已登记）
- [x] 前端涨跌色与邮件涨跌色语义一致（绿涨红跌）
- [x] v1.1 三项 bugfix 的交互状态流转已落规范
- [x] 已知不一致点（tab accent 色、emoji）已登记，未擅自在本轮改动实现

> 本文档为 draft，最终冻结状态由 loop-verifier 独立验证后决定。
