#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# loop-cli.sh
# Loop Orchestrator 命令行包装工具
#
# 用法: ./scripts/loop-cli.sh <command> [args]
#
# 命令:
#   status                查看状态
#   start "<需求>"        从头启动 Loop
#   once                  单次触发当前阶段
#   phase <阶段名>        手动触发指定阶段
#   watch                 持续监听模式
#   pause                 暂停 Loop
#   resume                恢复 Loop
#   help                  显示帮助
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

ORCHESTRATOR="$SCRIPT_DIR/loop-orchestrator.sh"
STATUS="$SCRIPT_DIR/loop-status.sh"

# 确保在项目根目录执行
cd "$PROJECT_ROOT"

# 默认启用自动确认和自动提交
# 默认启用自动提交
export AUTO_COMMIT="${AUTO_COMMIT:-true}"

cmd_help() {
    cat << 'HELP'
🚀 Loop Orchestrator CLI

用法:
  ./scripts/loop-cli.sh status                查看当前状态
  ./scripts/loop-cli.sh start "<需求>"        从头启动完整 Loop
  ./scripts/loop-cli.sh once                  单次触发当前阶段
  ./scripts/loop-cli.sh phase <阶段名>        手动触发指定阶段 (prd/design/arch/code/test/regression)
  ./scripts/loop-cli.sh watch                 启动持续监听模式
  ./scripts/loop-cli.sh pause                 暂停 Loop
  ./scripts/loop-cli.sh resume                恢复 Loop
  ./scripts/loop-cli.sh help                  显示此帮助

环境变量:
  YES_AUTO_CONFIRM=true/false  是否自动确认安全提示 (默认 true)
  AUTO_COMMIT=true/false        是否自动 git commit (默认 true)
  POLL_INTERVAL=N               watch 模式轮询间隔秒数 (默认 30)
HELP
}

cmd_status() {
    echo "📊 Loop 状态检查"
    echo "=================================="
    "$STATUS"
}

cmd_once() {
    echo "🚀 单次触发当前阶段..."
    echo "=================================="
    "$ORCHESTRATOR" --once
}

cmd_phase() {
    local phase="$1"
    if [[ -z "$phase" ]]; then
        echo "❌ 请指定阶段名: prd/design/arch/code/test/regression"
        exit 1
    fi
    echo "🎯 手动触发阶段: $phase"
    echo "=================================="
    "$ORCHESTRATOR" --once --phase "$phase"
}

cmd_start() {
    local requirement="$1"
    if [[ -z "$requirement" ]]; then
        echo "❌ 请输入需求描述"
        exit 1
    fi
    
    echo "🚀 启动完整 Loop"
    echo "=================================="
    echo "📝 需求: $requirement"
    echo ""
    
    # 重置当前阶段为 prd
    echo "🔧 重置阶段状态..."
    sed -i '' 's/^Current phase: .*/Current phase: prd/' LOOP-STATE.md
    
    # 重置所有阶段状态为 idle
    for f in .loop/phases/*-state.md; do
        sed -i '' 's/^Phase: .*/Phase: idle/' "$f"
    done
    
    # 启动 orchestrator
    echo "▶️  启动 Orchestrator..."
    REQUIREMENT="$requirement" "$ORCHESTRATOR" --once
}

cmd_watch() {
    echo "👁️  启动持续监听模式 (每 ${POLL_INTERVAL:-30} 秒轮询)"
    echo "=================================="
    echo "按 Ctrl+C 停止"
    echo ""
    "$ORCHESTRATOR"
}

cmd_pause() {
    echo "⏸️  暂停 Loop..."
    if grep -q 'paused:' LOOP-STATE.md; then
        sed -i '' 's/paused: .*/paused: true/' LOOP-STATE.md
    else
        echo "paused: true" >> LOOP-STATE.md
    fi
    echo "✅ Loop 已暂停 (设置 paused: true)"
}

cmd_resume() {
    echo "▶️  恢复 Loop..."
    sed -i '' 's/paused: true/paused: false/' LOOP-STATE.md
    echo "✅ Loop 已恢复 (设置 paused: false)"
}

# 主命令分发
case "${1:-}" in
    status)
        cmd_status
        ;;
    once)
        cmd_once
        ;;
    phase)
        cmd_phase "${2:-}"
        ;;
    start)
        cmd_start "${2:-}"
        ;;
    watch)
        cmd_watch
        ;;
    pause)
        cmd_pause
        ;;
    resume)
        cmd_resume
        ;;
    help|-h|--help)
        cmd_help
        ;;
    "")
        cmd_help
        ;;
    *)
        echo "❌ 未知命令: $1"
        echo ""
        cmd_help
        exit 1
        ;;
esac
