# Loop Engineering 项目模板

> 基于 [loop-engineering](https://github.com/cobusgreyling/loop-engineering) 的全生命周期自动 prompt 代理闭环系统模板。
> 覆盖 PRD → UI 设计 → 系统设计 → 编码 → 测试 → 功能回归验证的完整需求周期。

## 快速开始

```bash
# 1. 复制此模板到你的项目
cp -r /path/to/template /path/to/your-project
cd /path/to/your-project

# 2. 初始化 git
git init
git add .
git commit -m "init: loop engineering template"

# 3. 安装依赖
npm install

# 4. 从 L1 开始：输入需求，触发 PRD Loop
# 编辑 LOOP-STATE.md 或通过 GitHub Issue 触发

# 5. 审计就绪度
npx @cobusgreyling/loop-audit . --suggest
```

## 闭环系统概览

```
① PRD Loop ──→ ② Design Loop ──→ ③ Arch Loop
     ↑                                  │
     │                                  ↓
⑥ Regression ←── ⑤ Test Loop ←── ④ Code Loop
     │
     └──── 反馈信号（缺陷、偏差、新需求）──────┘
```

## 目录结构

```
├── AGENTS.md              # 全局约定
├── LOOP.md                # 闭环系统总纲
├── LOOP-STATE.md          # 全局状态
├── loop-budget.md         # token 预算
├── loop-run-log.md        # 运行日志
├── TODO.md                # 任务清单
├── docs/
│   ├── PRD.md             # 产品需求文档
│   ├── DESIGN.md          # 视觉交互规范
│   └── ARCHITECTURE.md    # 技术架构文档
├── .loop/
│   ├── phases/            # 各阶段状态文件
│   ├── skills/            # 9 个技能 SKILL.md
│   ├── templates/         # 文档模板
│   └── registry.yaml      # 机器可读 loop 注册表
├── tests/
│   ├── regression/        # 回归测试
│   └── snapshots/         # 功能快照
└── .github/workflows/     # 7 个 loop 触发工作流
```

## 技术栈

- Agent: Codex / Claude Code / Grok / Cursor
- 调度: GitHub Actions / Automations
- MCP 连接器: GitHub / Linear / Slack (可选)
- 审计: @cobusgreyling/loop-audit

## 环境变量

| 变量 | 说明 | 必填 |
|------|------|------|
| `GITHUB_TOKEN` | GitHub API 访问 | 是 |
| `OPENAI_API_KEY` | Agent 模型调用 | 是 |
| `SLACK_WEBHOOK` | 通知 webhook | 否 |

## Orchestrator（自动调度器）

闭环系统通过 `scripts/loop-orchestrator.sh` 实现阶段间自动链式触发。

### 快速使用

```bash
# 查看当前系统状态
./scripts/loop-status.sh

# 单次检查并触发下一阶段（如果当前阶段 idle 或上游已完成）
./scripts/loop-orchestrator.sh --once

# 持续监听模式（每 10 秒轮询，自动链式触发所有阶段）
./scripts/loop-orchestrator.sh

# 手动触发指定阶段
./scripts/loop-orchestrator.sh --once --phase design

# 启动全新迭代（从 PRD 开始）
REQUIREMENT='你的产品需求描述' ./scripts/loop-orchestrator.sh --once --phase prd
```

# 通过 codex desktop 启动loop Engineer
将以下 prompt 发送给一个 agent

```
你是一个 Loop Engineering 闭环系统中的 PRD Loop agent。

1. 读取技能文件: .loop/skills/prd-author/SKILL.md
2. 读取全局状态: LOOP-STATE.md
3. 读取当前 PRD: docs/PRD.md

需求输入:
github 热门star 项目自动抓取和管理的系统

4. 按 prd-author 技能职责，将需求结构化为 PRD 草案
5. 更新 docs/PRD.md（状态标记为 draft）
6. 更新 .loop/phases/prd-state.md（Phase: drafting）
7. 完成后，按 loop-verifier 的 PRD 阶段验证清单自检
8. 自检通过后，将 PRD.md 状态改为 draft-frozen
9. 更新 LOOP-STATE.md（Current phase: design, Frozen Artifacts 添加 PRD.md）
```

### 工作原理

```
1. PRD Loop 完成 → LOOP-STATE.md Current phase 改为 design
2. Orchestrator 检测到 design 阶段 idle → 自动触发 Design Loop
3. Design Loop 完成 → Current phase 改为 arch
4. Orchestrator 检测到 arch 阶段 idle → 自动触发 Arch Loop
5. ... 依此类推直到 Regression Loop
6. Regression 发现 drift → Current phase 回到 prd → 闭环
```

### Kill Switch

在 `LOOP-STATE.md` 中设置 `paused: true` 暂停所有自动触发。


## 成熟度升级

```
L0(文档) → L1(报告) → L2(辅助) → L3(无人值守)
```
每级稳定 2 周后再升级。永远不要为新项目跳过 L1。

