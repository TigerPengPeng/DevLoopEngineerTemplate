# Skill: code-implementer

> 编码实现技能。按 TODO.md 单任务实现。

## 触发条件

TODO.md 标记为 frozen，或上一个 TODO 任务完成时触发。

## 职责

1. 读取 docs/PRD.md + docs/DESIGN.md + docs/ARCHITECTURE.md
2. 读取 TODO.md + .loop/phases/code-state.md
3. 选取下一个未完成任务（严格按顺序）
4. 创建隔离 worktree
5. 仅修改完成当前任务所必需的文件
6. 编写单元测试
7. 提交到 worktree 分支（不直接 merge）
8. 更新 .loop/phases/code-state.md（标记 acting_on）

## 编码规则

- 每次只处理一个任务
- 只修改完成当前任务所必需的文件
- 不擅自扩大功能范围
- 不擅自引入新依赖
- 不擅自重构无关代码
- 不擅自删除文件
- 不擅自修改已有测试，除非任务明确要求
- 不为未来可能性添加额外抽象
- 不添加未要求的配置项或灵活性
- 匹配项目现有代码风格
- 删除因改动产生的孤儿导入/变量/函数

## 禁止行为

- 不修改 denylist 路径: `.env*`, `**/secrets/**`, `auth/**`, `payments/**`, `**/migrations/**`
- 不标记自己的工作完成——交由 loop-verifier
- 不在 worktree 外修改代码
- 不同时做多个不相关改动
- 不提交未完成或未验证的代码

## 验收自检（交由 loop-verifier 独立验证）

- 单元测试通过
- 修改范围不超出任务声明
- 符合 PRD/DESIGN/ARCHITECTURE
- 加载中/空数据/错误状态已实现
- 文档未过期（如过期则同步更新）
