# Loop Run Log — 项目名称

> Append-only 运行日志。每次 loop 运行追加一条记录。
> 机器可读格式见下方 JSON 示例；人类可读摘要写入各阶段 state 文件。

## 日志格式

```json
{
  "run_id": "2026-06-23T10:00:00Z",
  "loop": "code",
  "task": "TODO[3] 用户认证模块",
  "duration_s": 120,
  "sub_agents_spawned": 2,
  "verifier_result": "PASS",
  "tokens_estimate": 180000,
  "outcome": "committed: feat/auth-module"
}
```

## 运行记录

| 时间 | Loop | 任务 | 耗时 | 子代理 | Verifier | Tokens | 结果 |
|------|------|------|------|--------|----------|--------|------|
| — | — | — | — | — | — | — | — |
| 2026-06-26T06:09:22Z | prd | execute+verify | — | — | — | unknown | success |
| 2026-06-26T06:12:11Z | design | execute | — | — | — | unknown | failed |
| 2026-06-26T08:10:31Z | design | execute+verify | — | — | — | unknown | success |
