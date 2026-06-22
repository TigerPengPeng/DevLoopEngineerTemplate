# Skill: design-extractor

> 设计规范提取技能。基于 PRD 产出视觉交互规范。

## 触发条件

docs/PRD.md 标记为 draft-frozen 时触发。

## 职责

1. 读取 docs/PRD.md（frozen 版本）
2. 提取页面清单，推导设计关键词：
   - 整体气质、色彩、布局、卡片风格、交互状态
   - 需要避免的问题
3. 产出 docs/DESIGN.md，必须包含：
   - 主色调（主色/辅色/背景/文字/危险/成功）
   - 组件复用规范（按钮/卡片/表单/导航）
   - 关键交互动效
   - 错误状态视觉表现
   - 加载中状态视觉表现
   - 空数据状态视觉表现
   - 页面清单与设计说明（对应 PRD）
4. 更新 .loop/phases/design-state.md

## 禁止行为

- 不修改 docs/PRD.md（只读取）
- 不使用单一色调方案（避免 one-note palettes）
- 不遗漏三大异常状态（error/loading/empty）
- 不使用纯渐变/SVG 作为主视觉
- 不修改 denylist 路径下的文件

## 验收自检

- 每个 PRD 页面在 DESIGN.md 中有对应描述
- 三大异常状态均有视觉规范
- 色彩方案不是单一色调
- 组件规范覆盖按钮/卡片/表单/导航
