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

```bash
npm test           # 全量测试
npm run test:unit  # 单元测试
npm run test:integration  # 集成测试
npm run test:regression   # 回归测试
```
