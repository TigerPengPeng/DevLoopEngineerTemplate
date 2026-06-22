#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# loop-orchestrator.sh
# Loop Engineering 闭环系统调度器 - 方案 3（Git 外包模式）
#
# 核心架构：
#   - Agent 只写代码，不执行 git commit
#   - Orchestrator 检测到 phase 完成后自动提交
#   - 保留 sandbox 安全机制，用 yes 自动确认安全提示
#
# 用法:
#   ./scripts/loop-orchestrator.sh                  # watch 模式
#   ./scripts/loop-orchestrator.sh --once           # 单次检查
#   ./scripts/loop-orchestrator.sh --check          # 仅检查状态
#   AUTO_COMMIT=true ./scripts/loop-orchestrator.sh # 启用自动提交
# ============================================================

# ---------- 配置 ----------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PROMPTS_DIR="$SCRIPT_DIR/prompts"

POLL_INTERVAL="${POLL_INTERVAL:-10}"
CODEX_CMD="${CODEX_CMD:-codex}"
CODEX_MODEL="${CODEX_MODEL:-}"
SANDBOX_MODE="${SANDBOX_MODE:-workspace-write}"
AUTO_COMMIT="${AUTO_COMMIT:-true}"           # 是否自动 git commit
YES_AUTO_CONFIRM="${YES_AUTO_CONFIRM:-true}" # 是否用 yes 自动确认安全提示

# ---------- 阶段流转 ----------

phase_next() {
    case "$1" in
        prd)        echo "design" ;;
        design)     echo "arch" ;;
        arch)       echo "code" ;;
        code)       echo "test" ;;
        test)       echo "regression" ;;
        regression) echo "" ;;
        *)          echo "" ;;
    esac
}

phase_skill() {
    case "$1" in
        prd)        echo "prd-author" ;;
        design)     echo "design-extractor" ;;
        arch)       echo "arch-planner" ;;
        code)       echo "code-implementer" ;;
        test)       echo "test-runner" ;;
        regression) echo "regression-verifier" ;;
        *)          echo "" ;;
    esac
}

phase_commit_prefix() {
    case "$1" in
        prd)        echo "docs" ;;
        design)     echo "docs" ;;
        arch)       echo "docs" ;;
        code)       echo "feat" ;;
        test)       echo "test" ;;
        regression) echo "fix" ;;
        *)          echo "chore" ;;
    esac
}

# ---------- 工具函数 ----------

log() {
    local ts
    ts="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    echo "[$ts] $*"
}

get_current_phase() {
    grep '^Current phase:' "$PROJECT_ROOT/LOOP-STATE.md" 2>/dev/null \
        | head -1 \
        | sed 's/^Current phase: *//' \
        | tr -d ' \r' \
        || echo "idle"
}

is_paused() {
    grep -q 'paused: true' "$PROJECT_ROOT/LOOP-STATE.md" 2>/dev/null
}

is_phase_done() {
    local phase="$1"
    local state_file="$PROJECT_ROOT/.loop/phases/${phase}-state.md"
    [[ -f "$state_file" ]] || return 1
    grep -qE 'Phase: *(done|draft-frozen|frozen)' "$state_file" 2>/dev/null
}

is_phase_idle() {
    local phase="$1"
    local state_file="$PROJECT_ROOT/.loop/phases/${phase}-state.md"
    [[ -f "$state_file" ]] || return 0
    grep -qE 'Phase: *idle' "$state_file" 2>/dev/null
}

get_phase_last_run() {
    local phase="$1"
    local state_file="$PROJECT_ROOT/.loop/phases/${phase}-state.md"
    grep '^Last run:' "$state_file" 2>/dev/null | sed 's/^Last run: *//' || echo "—"
}

append_run_log() {
    local loop="$1"
    local task="$2"
    local result="$3"
    local tokens="${4:-unknown}"
    local ts
    ts="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    local log_file="$PROJECT_ROOT/loop-run-log.md"
    echo "| $ts | $loop | $task | — | — | — | $tokens | $result |" >> "$log_file"
}

# ========== Git 自动提交（方案 3 核心） ==========
# Agent 只写代码，git 操作由 orchestrator 统一管理

auto_git_commit() {
    local phase="$1"
    local prefix
    prefix="$(phase_commit_prefix "$phase")"

    if [[ "$AUTO_COMMIT" != "true" ]]; then
        log "Auto-commit disabled, skipping git commit"
        return 0
    fi

    cd "$PROJECT_ROOT"

    # 检查是否有改动
    if git diff --quiet && git diff --cached --quiet; then
        log "No changes to commit"
        return 0
    fi

    log "Auto-committing changes for $phase phase..."

    # 添加所有改动（包括新增文件）
    git add -A

    # 生成 commit message
    local commit_msg="${prefix}: ${phase} Loop completed - $(date '+%H:%M:%S')"

    # 执行提交（在 orchestrator 中执行，不需要 sandbox 确认）
    if git commit -m "$commit_msg"; then
        log "✅ Committed: $commit_msg"
        return 0
    else
        log "⚠️ Git commit failed (empty changes or error)"
        return 0
    fi
}

# ---------- 触发 agent ----------

trigger_phase() {
    local phase="$1"
    local skill
    skill="$(phase_skill "$phase")"
    local prompt_file="$PROMPTS_DIR/${phase}.md"

    if [[ ! -f "$prompt_file" ]]; then
        log "ERROR: prompt template not found: $prompt_file"
        return 1
    fi

    log "Triggering $phase Loop (skill: $skill)"
    log "  Auto-commit: $AUTO_COMMIT"
    log "  Yes auto-confirm: $YES_AUTO_CONFIRM"

    # 标记阶段为 running
    local state_file="$PROJECT_ROOT/.loop/phases/${phase}-state.md"
    local ts
    ts="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    sed -i '' "s/^Last run: .*/Last run: $ts/" "$state_file" 2>/dev/null || true
    sed -i '' "s/^Phase: .*/Phase: running/" "$state_file" 2>/dev/null || true

    # 构建 codex exec 命令
    # 注意：不使用 --dangerously-bypass，保留 sandbox 安全
    local cmd=("$CODEX_CMD" "exec")
    cmd+=("-C" "$PROJECT_ROOT")
    cmd+=("-s" "$SANDBOX_MODE")
    cmd+=("--skip-git-repo-check")

    if [[ -n "$CODEX_MODEL" ]]; then
        cmd+=("-m" "$CODEX_MODEL")
    fi

    local output_file="/tmp/loop-${phase}-$(date +%s).out"
    cmd+=("-o" "$output_file")

    # 读取 prompt 模板
    local prompt
    prompt="$(cat "$prompt_file")"

    # PRD 阶段替换需求占位符
    if [[ "$phase" == "prd" ]] && [[ -n "${REQUIREMENT:-}" ]]; then
        prompt="${prompt//\{\{REQUIREMENT\}\}/$REQUIREMENT}"
    fi

    log "Invoking: ${cmd[*]}"

    # 执行 agent
    # 方案 3：用 yes 自动回答安全确认，但保留 sandbox 保护
    local exit_code=0

    if [[ "$YES_AUTO_CONFIRM" == "true" ]]; then
        # yes | 自动输入 y 回答所有确认提示
        { yes | head -20; echo "$prompt"; } | "${cmd[@]}" 2>&1 | tee /tmp/loop-${phase}-console.log || exit_code=$?
    else
        # 不自动确认，需要人工干预
        echo "$prompt" | "${cmd[@]}" 2>&1 | tee /tmp/loop-${phase}-console.log || exit_code=$?
    fi

    if [[ $exit_code -eq 0 ]]; then
        log "✅ Agent completed successfully"

        # 方案 3 核心：Orchestrator 统一执行 git commit
        auto_git_commit "$phase"

        # 标记阶段为 done
        sed -i '' "s/^Phase: .*/Phase: done/" "$state_file" 2>/dev/null || true
        append_run_log "$phase" "execute" "success" "unknown"
        return 0
    else
        log "❌ Agent failed with exit code $exit_code"
        sed -i '' "s/^Phase: .*/Phase: blocked (exit $exit_code)/" "$state_file" 2>/dev/null || true
        append_run_log "$phase" "execute" "failed" "unknown"
        return $exit_code
    fi
}

# ---------- 检查并触发 ----------

check_and_trigger() {
    local current_phase
    current_phase="$(get_current_phase)"
    log "Current phase: $current_phase"

    if [[ "$current_phase" == "idle" ]]; then
        log "System idle, nothing to trigger"
        return 0
    fi

    # 情况 1: 当前阶段已完成 → 触发下一阶段
    if is_phase_done "$current_phase"; then
        local next_phase
        next_phase="$(phase_next "$current_phase")"

        if [[ -z "$next_phase" ]]; then
            log "Phase $current_phase is the last phase (regression done)"
            return 0
        fi

        if is_phase_idle "$next_phase"; then
            log "Phase $current_phase done, triggering next phase: $next_phase"
            trigger_phase "$next_phase"
        else
            log "Phase $current_phase done, but $next_phase already started, skipping"
        fi
        return 0
    fi

    # 情况 2: 当前阶段尚未开始 → 触发当前阶段
    if is_phase_idle "$current_phase"; then
        log "Phase $current_phase is idle, triggering it"
        trigger_phase "$current_phase"
        return 0
    fi

    # 情况 3: 当前阶段正在运行或阻塞 → 等待
    log "Phase $current_phase in progress (running/blocked), waiting"
}

# ---------- 主逻辑 ----------

main() {
    local mode="watch"
    local force_phase=""

    while [[ $# -gt 0 ]]; do
        case "$1" in
            --once)
                mode="once"
                shift
                ;;
            --check)
                mode="check"
                shift
                ;;
            --phase)
                force_phase="$2"
                mode="force"
                shift 2
                ;;
            --no-auto-commit)
                AUTO_COMMIT="false"
                shift
                ;;
            --no-auto-confirm)
                YES_AUTO_CONFIRM="false"
                shift
                ;;
            --help|-h)
                cat << 'HELP'
loop-orchestrator.sh — Loop Engineering 闭环调度器（方案 3）

用法:
  ./scripts/loop-orchestrator.sh                    # watch 模式
  ./scripts/loop-orchestrator.sh --once             # 单次检查
  ./scripts/loop-orchestrator.sh --check            # 仅检查状态
  ./scripts/loop-orchestrator.sh --once --phase design  # 手动触发
  ./scripts/loop-orchestrator.sh --no-auto-commit   # 禁用自动 git commit
  ./scripts/loop-orchestrator.sh --no-auto-confirm  # 禁用 yes 自动确认

环境变量:
  POLL_INTERVAL     轮询间隔秒数（默认 10）
  CODEX_CMD         codex 命令路径（默认 codex）
  CODEX_MODEL       模型名称
  AUTO_COMMIT       是否自动 git commit（默认 true）
  YES_AUTO_CONFIRM  是否用 yes 自动确认安全提示（默认 true）
  REQUIREMENT       PRD 阶段的需求输入文本

架构说明（方案 3）:
  ✅ Agent 只写代码，不执行 git commit
  ✅ Orchestrator 统一管理 git 操作
  ✅ 保留 sandbox 安全保护
  ✅ 用 yes 自动回答安全确认提示
HELP
                exit 0
                ;;
            *)
                log "Unknown option: $1"
                exit 1
                ;;
        esac
    done

    if [[ ! -f "$PROJECT_ROOT/LOOP-STATE.md" ]]; then
        log "ERROR: LOOP-STATE.md not found at $PROJECT_ROOT"
        exit 1
    fi

    log "Loop Orchestrator started (Scheme 3: Git Outsourced)"
    log "Project root: $PROJECT_ROOT"
    log "Mode: $mode"
    log "Auto-commit: $AUTO_COMMIT"
    log "Yes auto-confirm: $YES_AUTO_CONFIRM"

    case "$mode" in
        check)
            local phase
            phase="$(get_current_phase)"
            log "Current phase: $phase"
            log "Paused: $(is_paused && echo 'yes' || echo 'no')"
            for p in prd design arch code test regression; do
                local done_status idle_status
                is_phase_done "$p" && done_status="done" || done_status="-"
                is_phase_idle "$p" && idle_status="idle" || idle_status="-"
                log "  $p: done=$done_status idle=$idle_status last_run=$(get_phase_last_run $p)"
            done
            ;;
        force)
            if [[ -z "$force_phase" ]]; then
                log "ERROR: --phase requires a phase name"
                exit 1
            fi
            log "Force triggering phase: $force_phase"
            trigger_phase "$force_phase"
            ;;
        once)
            if is_paused; then
                log "Kill switch active (paused: true), skipping"
                exit 0
            fi
            check_and_trigger
            ;;
        watch)
            log "Watch mode (poll every ${POLL_INTERVAL}s). Press Ctrl+C to stop."
            log "---"
            while true; do
                if is_paused; then
                    log "Kill switch active, sleeping..."
                    sleep "$POLL_INTERVAL"
                    continue
                fi
                check_and_trigger
                sleep "$POLL_INTERVAL"
            done
            ;;
    esac

    log "Orchestrator finished"
}

main "$@"
