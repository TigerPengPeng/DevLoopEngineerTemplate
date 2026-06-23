# ARCHITECTURE — {{PRODUCT_NAME}}

> 状态: **draft** | draft-frozen | frozen
> 版本: v0.1

## 技术栈
- 语言: ...
- 框架: ...

## 目录结构
```
项目根/
├── src/
└── ...
```

## 数据模型
### {{ENTITY_NAME}}
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|

## 服务层约定
- ...

## API 层约定
- ...

## AI 引用机制
- 编码前必须读取 docs/ 三份文档
- 按 TODO.md 顺序执行
- Code Loop 读取任务 `[type: xxx]` 标签，加载对应专业 agent 人格（见 .loop/agents/_index.md）

## 任务标签约定
- Arch Loop 拆分 TODO.md 时为每个任务标注 `[type: xxx]` 标签
- 标签映射见 registry.yaml 的 dispatch 段和 .loop/agents/_index.md
- 无标签任务用阶段 agent 默认人格执行（向后兼容）

## 开发约束
- 每次只处理一个任务
- 只修改必需文件

## 禁止破坏的逻辑
- ...

## 验收标准
- [ ] ...
