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
#   - 阶段级 agent 人格文件注入到 prompt 中
#   - loop-verifier 作为独立 codex exec 会话运行（maker/checker 分离）
#
# 用法:
#   ./scripts/loop-orchestrator.sh                  # watch 模式
#   ./scripts/loop-orchestrator.sh --once           # 单次检查（推进到下一个阻塞点）
#   ./scripts/loop-orchestrator.sh --check          # 仅检查状态
#   ./scripts/loop-orchestrator.sh --reset-from arch # 重置 arch 及下游全部阶段为 idle
#   AUTO_COMMIT=true ./scripts/loop-orchestrator.sh # 启用自动提交
# ============================================================

# ---------- 配置 ----------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PROMPTS_DIR="$SCRIPT_DIR/prompts"

POLL_INTERVAL="${POLL_INTERVAL:-30}"
CODEX_CMD="${CODEX_CMD:-codex}"
CODEX_MODEL="${CODEX_MODEL:-}"
SANDBOX_MODE="${SANDBOX_MODE:-workspace-write}"
AUTO_COMMIT="${AUTO_COMMIT:-true}"
YES_AUTO_CONFIRM="${YES_AUTO_CONFIRM:-true}"

# 阶段顺序（用于 reset-downstream 迭代）
ALL_PHASES=(prd design arch code test regression)

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

# 返回阶段的 phase-level agent 人格文件路径（空格分隔，相对于 PROJECT_ROOT）
# 阶段级 agent 直接注入 prompt；任务级派发（arch/code）通过 _index.md + [type:xxx] 动态加载
phase_agent_files() {
    case "$1" in
        prd)        echo ".loop/agents/product/product-manager.md" ;;
        design)     echo ".loop/agents/design/ui-designer.md .loop/agents/design/interaction-designer.md" ;;
        arch)       echo "" ;;
        code)       echo "" ;;
        test)       echo "" ;;
        regression) echo ".loop/agents/product/technical-writer.md" ;;
        *)          echo "" ;;
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

set_current_phase() {
    local phase="$1"
    sed -i '' "s/^Current phase: .*/Current phase: $phase/" "$PROJECT_ROOT/LOOP-STATE.md" 2>/dev/null || true
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

# 重置单个阶段为 idle
reset_phase() {
    local phase="$1"
    local state_file="$PROJECT_ROOT/.loop/phases/${phase}-state.md"
    if [[ -f "$state_file" ]]; then
        sed -i '' "s/^Phase: .*/Phase: idle/" "$state_file" 2>/dev/null || true
        log "  Reset $phase -> idle"
    fi
}

# 重置指定阶段及其全部下游阶段为 idle
reset_downstream() {
    local from_phase="$1"
    local resetting=false
    for p in "${ALL_PHASES[@]}"; do
        if [[ "$p" == "$from_phase" ]]; then
            resetting=true
        fi
        if $resetting; then
            reset_phase "$p"
        fi
    done
    # 同时更新 LOOP-STATE.md 的 Current phase
    set_current_phase "$from_phase"
    log "Current phase set to: $from_phase"
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

# ========== Git 自动提交 ==========

auto_git_commit() {
    local phase="$1"
    local prefix
    prefix="$(phase_commit_prefix "$phase")"

    if [[ "$AUTO_COMMIT" != "true" ]]; then
        log "Auto-commit disabled, skipping git commit"
        return 0
    fi

    cd "$PROJECT_ROOT"

    if git diff --quiet && git diff --cached --quiet; then
        log "No changes to commit"
        return 0
    fi

    log "Auto-committing changes for $phase phase..."

    git add -A
    local commit_msg="${prefix}: ${phase} Loop completed - $(date '+%H:%M:%S')"

    if git commit -m "$commit_msg"; then
        log "✅ Committed: $commit_msg"
        return 0
    else
        log "⚠️ Git commit failed (empty changes or error)"
        return 0
    fi
}

# ---------- 构建 prompt（注入 agent 人格） ----------

build_prompt() {
    local phase="$1"
    local prompt_file="$PROMPTS_DIR/${phase}.md"

    if [[ ! -f "$prompt_file" ]]; then
        log "ERROR: prompt template not found: $prompt_file"
        return 1
    fi

    local prompt
    prompt="$(cat "$prompt_file")"

    # 阶段级 agent 人格文件注入（prd/design/regression）
    local agent_files
    agent_files="$(phase_agent_files "$phase")"
    if [[ -n "$agent_files" ]]; then
        for agent_file in $agent_files; do
            local full_path="$PROJECT_ROOT/$agent_file"
            if [[ -f "$full_path" ]]; then
                local agent_content
                agent_content=$(cat "$full_path")
                prompt="${prompt}

---
## 专业 Agent 人格文件: ${agent_file}
请严格采纳以下 agent 人格的视角、规则和约束执行本阶段任务。其领域规则覆盖通用规则。

${agent_content}"
                log "  Injected agent: $agent_file"
            else
                log "  WARNING: agent file not found: $full_path"
            fi
        done
    fi

    # 任务级派发阶段（arch/code）注入 agent 目录索引，Codex 运行时按 [type:xxx] 动态 cat 对应文件
    local agent_index="$PROJECT_ROOT/.loop/agents/_index.md"
    if [[ -f "$agent_index" ]] && [[ -z "$agent_files" ]]; then
        local agent_context
        agent_context=$(cat "$agent_index")
        prompt="${prompt}

---
## 专业 Agent 目录上下文（任务级派发）
以下为可用专业 agent 目录。若任务含 [type: xxx] 标签，请读取（cat）对应 agent 人格文件并采纳其规则执行。

${agent_context}"
    fi

    # PRD 阶段注入需求文本
    if [[ "$phase" == "prd" ]] && [[ -n "${REQUIREMENT:-}" ]]; then
        prompt="${prompt//\{\{REQUIREMENT\}\}/$REQUIREMENT}"
    fi

    echo "$prompt"
}

# ---------- 触发 agent（主阶段） ----------

trigger_phase() {
    local phase="$1"
    local skill
    skill="$(phase_skill "$phase")"

    log "Triggering $phase Loop (skill: $skill)"
    log "  Auto-commit: $AUTO_COMMIT"
    log "  Yes auto-confirm: $YES_AUTO_CONFIRM"

    local state_file="$PROJECT_ROOT/.loop/phases/${phase}-state.md"
    local ts
    ts="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    sed -i '' "s/^Last run: .*/Last run: $ts/" "$state_file" 2>/dev/null || true
    sed -i '' "s/^Phase: .*/Phase: running/" "$state_file" 2>/dev/null || true

    local prompt
    prompt="$(build_prompt "$phase")"

    local cmd=("$CODEX_CMD" "exec")
    cmd+=("-C" "$PROJECT_ROOT")
    cmd+=("-s" "$SANDBOX_MODE")
    cmd+=("--skip-git-repo-check")

    if [[ -n "$CODEX_MODEL" ]]; then
        cmd+=("-m" "$CODEX_MODEL")
    fi

    local output_file="/tmp/loop-${phase}-$(date +%s).out"
    cmd+=("-o" "$output_file")

    log "Invoking: ${cmd[*]}"

    local exit_code=0

    if [[ "$YES_AUTO_CONFIRM" == "true" ]]; then
        { yes | head -20; echo "$prompt"; } | "${cmd[@]}" 2>&1 | tee /tmp/loop-${phase}-console.log || exit_code=$?
    else
        echo "$prompt" | "${cmd[@]}" 2>&1 | tee /tmp/loop-${phase}-console.log || exit_code=$?
    fi

    if [[ $exit_code -eq 0 ]]; then
        log "✅ Main agent completed, running independent verifier..."

        # loop-verifier 作为独立 codex exec 会话运行（maker/checker 分离）
        if trigger_verifier "$phase"; then
            log "✅ Verifier PASSED, finalizing phase"
            auto_git_commit "$phase"
            sed -i '' "s/^Phase: .*/Phase: done/" "$state_file" 2>/dev/null || true
            append_run_log "$phase" "execute+verify" "success" "unknown"
            return 0
        else
            log "❌ Verifier REJECTED, phase remains blocked"
            sed -i '' "s/^Phase: .*/Phase: blocked (verifier-rejected)/" "$state_file" 2>/dev/null || true
            append_run_log "$phase" "execute+verify" "rejected" "unknown"
            return 1
        fi
    else
        log "❌ Agent failed with exit code $exit_code"
        sed -i '' "s/^Phase: .*/Phase: blocked (exit $exit_code)/" "$state_file" 2>/dev/null || true
        append_run_log "$phase" "execute" "failed" "unknown"
        return $exit_code
    fi
}

# ---------- 独立验证器（maker/checker 分离） ----------

trigger_verifier() {
    local phase="$1"
    local verifier_skill="$PROJECT_ROOT/.loop/skills/loop-verifier/SKILL.md"

    if [[ ! -f "$verifier_skill" ]]; then
        log "  WARNING: loop-verifier SKILL.md not found, skipping verification"
        return 0
    fi

    local skill_content
    skill_content=$(cat "$verifier_skill")

    local verifier_prompt="# Loop Verifier — 独立验证（${phase} 阶段）

你是 Loop 验证器，独立验证者。你不信任实现者的自述，只相信你自己运行验证后看到的原始输出。你的默认倾向是 REJECT，只有找不到拒绝理由时才 PASS。

## 验证技能

${skill_content}

## 当前验证阶段: ${phase}

请按照上述 SKILL.md 中「${phase} 阶段」的验证清单逐项检查。

## 验证步骤

1. 读取本阶段产出物（docs/PRD.md, docs/DESIGN.md, docs/ARCHITECTURE.md, 或源代码，取决于阶段）
2. 逐项检查验证清单
3. 运行可执行的验证命令（测试/lint/build）
4. 检查 diff 范围是否超出任务声明
5. 检查是否符合三份文档（PRD/DESIGN/ARCHITECTURE）

## 输出格式（必须严格遵循）

\`\`\`
RESULT: PASS | REJECT
REASON: [具体原因]
EVIDENCE: [验证输出/diff 分析/文档对比]
SUGGESTION: [建议修正方向（若 REJECT）]
\`\`\`

只输出上述格式，不要输出其他内容。"

    local cmd=("$CODEX_CMD" "exec")
    cmd+=("-C" "$PROJECT_ROOT")
    cmd+=("-s" "$SANDBOX_MODE")
    cmd+=("--skip-git-repo-check")

    if [[ -n "$CODEX_MODEL" ]]; then
        cmd+=("-m" "$CODEX_MODEL")
    fi

    local verifier_output="/tmp/loop-${phase}-verify-$(date +%s).out"
    cmd+=("-o" "$verifier_output")

    log "  Running independent verifier (separate session)..."

    local exit_code=0
    if [[ "$YES_AUTO_CONFIRM" == "true" ]]; then
        { yes | head -20; echo "$verifier_prompt"; } | "${cmd[@]}" 2>&1 | tee /tmp/loop-${phase}-verify-console.log || exit_code=$?
    else
        echo "$verifier_prompt" | "${cmd[@]}" 2>&1 | tee /tmp/loop-${phase}-verify-console.log || exit_code=$?
    fi

    # 从 verifier 输出中解析 PASS/REJECT
    local verifier_result
    verifier_result=$(cat "$verifier_output" 2>/dev/null || cat /tmp/loop-${phase}-verify-console.log 2>/dev/null || echo "")
    if echo "$verifier_result" | grep -qiE '^RESULT:\s*PASS'; then
        log "  Verifier result: PASS"
        return 0
    else
        log "  Verifier result: REJECT (or parse failure)"
        log "  Verifier output saved to: $verifier_output"
        return 1
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

    # 情况 1: 当前阶段已完成 → 推进到下一阶段
    if is_phase_done "$current_phase"; then
        local next_phase
        next_phase="$(phase_next "$current_phase")"

        if [[ -z "$next_phase" ]]; then
            log "Phase $current_phase is the last phase (regression done), setting idle"
            set_current_phase "idle"
            return 0
        fi

        if is_phase_idle "$next_phase"; then
            log "Phase $current_phase done, triggering next phase: $next_phase"
            set_current_phase "$next_phase"
            trigger_phase "$next_phase"
            return 0
        fi

        # 下一阶段非 idle（done/running/blocked）
        # 如果下一阶段也是 done，说明是需求变更后的重跑场景
        # 自动重置下一阶段为 idle 并触发
        if is_phase_done "$next_phase"; then
            log "Phase $current_phase done, $next_phase was done (re-run), resetting and triggering"
            reset_phase "$next_phase"
            set_current_phase "$next_phase"
            trigger_phase "$next_phase"
            return 0
        fi

        # 下一阶段正在运行或阻塞
        log "Phase $current_phase done, but $next_phase in progress (running/blocked), waiting"
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
    local reset_from=""

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
            --reset-from)
                reset_from="$2"
                mode="reset"
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
  ./scripts/loop-orchestrator.sh --once             # 单次检查（推进到下一个阻塞点）
  ./scripts/loop-orchestrator.sh --check            # 仅检查状态
  ./scripts/loop-orchestrator.sh --reset-from arch  # 重置 arch 及下游全部阶段为 idle
  ./scripts/loop-orchestrator.sh --phase design     # 强制触发单个阶段
  ./scripts/loop-orchestrator.sh --no-auto-commit   # 禁用自动 git commit
  ./scripts/loop-orchestrator.sh --no-auto-confirm  # 禁用 yes 自动确认

环境变量:
  POLL_INTERVAL     轮询间隔秒数（默认 30）
  CODEX_CMD         codex 命令路径（默认 codex）
  CODEX_MODEL       模型名称
  AUTO_COMMIT       是否自动 git commit（默认 true）
  YES_AUTO_CONFIRM  是否用 yes 自动确认安全提示（默认 true）
  REQUIREMENT       PRD 阶段的需求输入文本

架构说明（方案 3）:
  ✅ Agent 只写代码，不执行 git commit
  ✅ Orchestrator 统一管理 git 操作
  ✅ 保留 sandbox 安全保护
  ✅ done→done 自动重置重跑（需求变更不再卡死）
  ✅ --reset-from 一键重置下游阶段
  ✅ 阶段级 agent 人格文件注入到 prompt
  ✅ loop-verifier 独立会话运行（maker/checker 分离）

需求变更工作流:
  1. 修改 docs/PRD.md（版本号递增）
  2. ./scripts/loop-orchestrator.sh --reset-from <phase>
     例如 PRD 变更影响架构: --reset-from design
     例如只影响代码: --reset-from code
  3. ./scripts/loop-orchestrator.sh  (watch 自动推进)
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
            for p in "${ALL_PHASES[@]}"; do
                local done_status idle_status
                is_phase_done "$p" && done_status="done" || done_status="-"
                is_phase_idle "$p" && idle_status="idle" || idle_status="-"
                log "  $p: done=$done_status idle=$idle_status last_run=$(get_phase_last_run $p)"
            done
            ;;
        reset)
            if [[ -z "$reset_from" ]]; then
                log "ERROR: --reset-from requires a phase name (prd/design/arch/code/test/regression)"
                exit 1
            fi
            log "Resetting from phase: $reset_from (including all downstream phases)"
            reset_downstream "$reset_from"
            log "Reset complete. Run ./scripts/loop-orchestrator.sh to resume."
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
