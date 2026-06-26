# Loop State — Futu 股票监听系统

> 全局协调状态文件。各阶段 loop 读取此文件获取全局上下文，写入跨阶段信号。

Last run: 2026-06-26T14:36:00Z
Current phase: idle
Paused: false

## Active Loops
- futu-stock-monitor: v1.2 noise-reduction cycle COMPLETE — 代码层已验证（regression: 91/91 测试通过，无功能回归）；文档层待 doc-refresh 子循环同步漂移 D-1~D-4

## Frozen Artifacts
- docs/PRD.md (v1.2 frozen — NR-1~NR-5；NR-4 留痕范围 D-5 待澄清)
- docs/DESIGN.md (v1.1 draft — 漂移 D-1: 缺 v1.2 NR-3「已静音」视觉规范，建议解冻做 doc-refresh)
- docs/ARCHITECTURE.md (v1.2 frozen — 漂移 D-2/D-3: 目录结构过期、缺 NR-4/NR-5 变更说明，建议解冻做 doc-refresh)
- TODO.md (T1~T22 全部完成)
- README.md (漂移 D-4: 技术栈「非 Web」错误、缺 NR-5 环境变量，建议解冻做 doc-refresh)

## Unfreeze Requests
- docs/DESIGN.md: 解冻做 doc-refresh，补 v1.2 NR-3「已静音」前端视觉规范 (D-1)
- docs/ARCHITECTURE.md: 解冻做 doc-refresh，同步真实目录结构 + 补 NR-4/NR-5 变更说明 (D-2/D-3)
- README.md: 解冻做 doc-refresh，修正技术栈为 Web 应用 + 补 NR-5 环境变量 (D-4)

## Human Inbox (ambiguous / cross-loop)
- (无)

## Regression Feedback → PRD
- D-5 [需 PRD 澄清]: NR-4「所有告警全量留痕」当前仅 MA 突破路径落库；波动/破位/买卖点被抑制告警仅记日志。请确认留痕范围——限定 MA 突破（更新 PRD 措辞）或补齐其余路径（新增 TODO 任务）。
- 代码层回归通过（91/91）；以上漂移均为文档/范围层面，不阻塞代码可用性。

## Kill Switch
- paused: false
- reason: —
