# Loop Engineering 项目模板

> 基于 [loop-engineering](https://github.com/cobusgreyling/loop-engineering) 的全生命周期自动 prompt 代理闭环系统模板。
> 覆盖 PRD → UI 设计 → 系统设计 → 编码 → 测试 → 功能回归验证的完整需求周期。
> 结合 [agency-agent](https://github.com/jnMetaCode/agency-agents-zh) 角色目录模式，支持任务标签派发专业 agent。

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

## 两层 Agent 体系

系统采用两层 agent 架构：

- **阶段 agent**（`.loop/skills/`）：9 个阶段编排技能，负责阶段内状态管理、worktree 隔离、handoff
- **专业 agent**（`.loop/agents/`）：10 个领域专家角色，由阶段 agent 通过任务标签派发加载

### 专业 Agent 目录

| 部门 | Agent | dispatch_tag | 适用阶段 |
|------|-------|-------------|---------|
| Engineering | 前端开发专家 | `frontend` | code |
| Engineering | 后端架构师 | `backend` | arch, code |
| Engineering | 安全工程师 | `security` | code |
| Engineering | 代码审查员 | `review` | code |
| Engineering | DevOps 自动化工程师 | `devops` | arch, code |
| Engineering | 数据库优化专家 | `database` | arch, code |
| Design | UI 设计师 | `ui` | design |
| Design | 交互设计师 | `interaction` | design |
| Product | 产品经理 | `product` | prd |
| Product | 技术文档工程师 | `docs` | arch, regression |

### 任务标签派发

Arch Loop 在拆分 TODO.md 任务时自动标注 `[type: xxx]` 标签。Code Loop 读取标签后加载对应专业 agent 人格执行：

```
- [ ] [type: frontend] 实现用户登录页面
- [ ] [type: backend] 实现用户认证 API
- [ ] [type: database] 设计用户表结构
```

无标签任务使用阶段 agent 默认人格执行（向后兼容）。标签与 agent 映射在 `registry.yaml` 的 `dispatch` 段集中维护。

## 目录结构

```
├── AGENTS.md              # 全局约定
├── LOOP.md                # 闭环系统总纲
├── LOOP-STATE.md          # 全局状态
├── loop-budget.md         # token 预算
├── loop-run-log.md        # 运行日志
├── TODO.md                # 任务清单（含 [type:xxx] 标签）
├── docs/
│   ├── PRD.md             # 产品需求文档
│   ├── DESIGN.md          # 视觉交互规范
│   └── ARCHITECTURE.md    # 技术架构文档
├── .loop/
│   ├── agents/            # 专业 agent 角色目录
│   │   ├── engineering/   # 6 个工程类 agent
│   │   ├── design/        # 2 个设计类 agent
│   │   ├── product/       # 2 个产品类 agent
│   │   └── _index.md      # 角色目录索引
│   ├── phases/            # 各阶段状态文件
│   ├── skills/            # 9 个阶段编排技能 SKILL.md
│   ├── templates/         # 文档模板（含 AGENT.template.md）
│   └── registry.yaml      # 机器可读注册表（loops + agents + dispatch）
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

闭环系统通过 `scripts/loop-orchestrator.sh` 实现阶段间自动链式触发。编排器在触发阶段 agent 时自动注入 `.loop/agents/_index.md` 作为上下文，让阶段 agent 知道可用的专业 agent 目录。

### 快速使用

```bash
# 查看当前系统状态（含 agent 目录概览）
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


---

## 🚀 Orchestrator 使用指南（方案 3：Git 外包模式）

### 核心架构

本模板实现了 **方案 3：Git 外包模式**，这是最安全的自动化架构：

| 职责 | Agent | Orchestrator |
|------|-------|--------------|
| 编写代码/文档 | ✅ | ❌ |
| 执行 git add/commit | ❌ | ✅ |
| 安全沙箱保护 | ✅ | — |
| 自动确认提示 | ✅（通过 yes） | — |

### 为什么这样设计？

1. **安全**：保留 sandbox 保护，不使用 `--dangerously-bypass-approvals-and-sandbox`
2. **可控**：所有 git 操作由 orchestrator 统一管理，可审计、可回滚
3. **零人工确认**：用 `yes | codex exec` 自动回答安全提示
4. **最小权限**：Agent 只拥有写代码的权限，不具备破坏性操作的能力

### 使用方式

```bash
# 1. 检查状态
./scripts/loop-status.sh

# 2. 单次执行（推荐用于调试）
./scripts/loop-orchestrator.sh --once

# 3. Watch 模式（持续轮询）
./scripts/loop-orchestrator.sh

# 4. 手动触发指定阶段
./scripts/loop-orchestrator.sh --once --phase design

# 5. 禁用自动提交（只写代码不 commit）
./scripts/loop-orchestrator.sh --once --no-auto-commit

# 6. 禁用自动确认（需要人工按 y）
./scripts/loop-orchestrator.sh --once --no-auto-confirm
```

### 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `POLL_INTERVAL` | 10 | Watch 模式轮询间隔（秒） |
| `AUTO_COMMIT` | true | 是否自动执行 git commit |
| `YES_AUTO_CONFIRM` | true | 是否用 yes 自动确认安全提示 |
| `CODEX_MODEL` | — | 指定使用的模型 |
| `REQUIREMENT` | — | PRD 阶段的需求输入 |

### 工作流

```
用户设置 Current phase → Orchestrator 触发 Agent
    ↓
Agent 编写代码/文档（不执行 git）
    ↓
Agent 标记 phase-state 为 done
    ↓
Orchestrator 检测到 done，自动执行 git add + commit
    ↓
Orchestrator 触发下一个 phase
```
