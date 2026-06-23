# AGENTS.md — Loop Engineering 项目模板

本文件为人类和 loop 维护此项目提供全局约定。

## 构建与验证

```bash
# 安装依赖
npm ci

# 运行测试
npm test

# 运行 lint
npm run lint

# 构建检查
npm run build

# Loop 就绪度审计
npx @cobusgreyling/loop-audit . --suggest
```

## 评审规范

- 所有阶段产出物必须通过 loop-verifier 子代理验证后才能冻结
- 实现者不能给自己的作业打分（maker/checker 分离）
- 失败记录在 stories/ 中应包含 token 成本、根因、修复措施
- 新增 pattern 需要在 .loop/registry.yaml 中注册

## Loop 操作

- **PRD Loop**: `prd-author` 技能 → `docs/PRD.md`（L1 报告模式）
- **Design Loop**: `design-extractor` 技能 → `docs/DESIGN.md`（L1 报告模式）
- **Arch Loop**: `arch-planner` 技能 → `docs/ARCHITECTURE.md` + `TODO.md`（L1 报告模式）
- **Code Loop**: `code-implementer` 技能 → worktree 代码提交（L2 辅助模式）
- **Test Loop**: `test-runner` 技能 → 测试报告（L2 辅助模式）
- **Regression Loop**: `regression-verifier` 技能 → 回归报告 + 反馈信号（L1→L2）

## 编码约束

- 每次只处理 TODO.md 中的一个任务
- 只修改完成当前任务所必需的文件
- 不擅自扩大功能范围
- 不擅自引入新依赖
- 不擅自重构无关代码
- 匹配项目现有代码风格
- 修改前必须读取 docs/PRD.md、docs/DESIGN.md、docs/ARCHITECTURE.md
- Code Loop 派发时须读取 TODO 任务 [type:xxx] 标签对应的专业 agent 人格（见 .loop/agents/_index.md）
- Arch Loop 拆分 TODO.md 时须为每个任务标注 [type:xxx] 标签

## 安全门禁

- 路径 denylist: `.env*`, `**/secrets/**`, `auth/**`, `payments/**`, `**/migrations/**`
- 默认不自动合并
- 人工门禁: auth/payments/infra/依赖升级/>10文件/3次失败/PRD定位变更
- Kill switch: `LOOP-STATE.md` 中 `paused: true` 或 `loop-pause-all` 标签

## 测试命令
## 测试命令

```bash
npm test           # 全量测试
npm run test:unit  # 单元测试
npm run test:integration  # 集成测试
npm run test:regression   # 回归测试
```


## 🚀 Loop Orchestrator 命令系统



---

## 🚀 Loop Orchestrator 命令系统

### 重要：当你在聊天中看到以下 `/loop` 开头的命令时，**必须**按照本规范执行。

### 命令列表

| 命令 | 说明 | 示例 |
|------|------|------|
| `/loop status` | 查看当前 Loop 状态 | `/loop status` |
| `/loop start "需求描述"` | 从 PRD 阶段开始完整 Loop | `/loop start "实现一个定时任务调度管理服务..."` |
| `/loop once` | 单次触发当前阶段 | `/loop once` |
| `/loop phase <阶段名>` | 手动触发指定阶段 | `/loop phase design` |
| `/loop watch` | 启动持续监听模式 | `/loop watch` |
| `/loop pause` | 暂停 Loop | `/loop pause` |
| `/loop resume` | 恢复 Loop | `/loop resume` |
| `/loop help` | 显示帮助信息 | `/loop help` |

### 执行规则（**严格遵守**）

1. **识别命令**：用户输入以 `/loop` 开头时，识别为 Loop 命令，**不要**当作普通聊天处理
2. **解析参数**：提取子命令和参数
3. **工作目录**：所有命令都在项目根目录执行
4. **输出格式**：实时显示脚本执行输出
5. **完成后**：自动调用 `/loop status` 显示最新状态

---

### 各命令实现方式

| 命令 | 执行脚本 |
|------|----------|
| `/loop status` | `./scripts/loop-cli.sh status` |
| `/loop start "需求"` | `./scripts/loop-cli.sh start '需求'` |
| `/loop once` | `./scripts/loop-cli.sh once` |
| `/loop phase <名>` | `./scripts/loop-cli.sh phase <名>` |
| `/loop watch` | `./scripts/loop-cli.sh watch` |
| `/loop pause` | `./scripts/loop-cli.sh pause` |
| `/loop resume` | `./scripts/loop-cli.sh resume` |
| `/loop help` | `./scripts/loop-cli.sh help` |

---

### 通用执行规范

1. **所有命令执行前**：先 `cd` 到项目根目录
2. **超时设置**：
   - 单次执行: `yield_time_ms: 60000` (60秒)
   - watch 模式: `yield_time_ms: 30000` (30秒)
3. **错误处理**：清晰显示错误信息，给出原因和建议
4. **执行后**：自动调用 `/loop status` 显示最新状态
