#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_ROOT"

current_phase=$(grep '^Current phase:' LOOP-STATE.md | sed 's/^Current phase: *//')

echo "========================================"
echo "  Loop Engineer Status"
echo "========================================"
echo ""
echo "📍 Current Phase: $current_phase"
echo ""
echo "📋 Phase States:"
echo ""

for phase in prd design arch code test regression; do
    state_file=".loop/phases/${phase}-state.md"
    if [[ -f "$state_file" ]]; then
        state=$(grep '^Phase:' "$state_file" | sed 's/^Phase: *//')
        last_run=$(grep '^Last run:' "$state_file" 2>/dev/null | sed 's/^Last run: *//' || echo "—")
        echo "  $phase: $state"
        echo "    └─ Last run: $last_run"
    else
        echo "  $phase: —"
    fi
done

echo ""
echo "📁 Frozen Artifacts:"
grep -A20 '^Frozen Artifacts:' LOOP-STATE.md | grep -v '^Frozen Artifacts:' | grep -v '^$' | sed 's/^/  - /' || true

echo ""
echo "👥 Professional Agents:"
agent_index=".loop/agents/_index.md"
if [[ -f "$agent_index" ]]; then
    # Count agent files
    agent_count=$(find .loop/agents -name '*.md' ! -name '_index.md' | wc -l | tr -d ' ')
    echo "  Total agents: $agent_count"
    for dept in engineering design product; do
        dept_dir=".loop/agents/$dept"
        if [[ -d "$dept_dir" ]]; then
            count=$(find "$dept_dir" -name '*.md' | wc -l | tr -d ' ')
            echo "  $dept: $count agents"
            for f in "$dept_dir"/*.md; do
                [[ -f "$f" ]] || continue
                name=$(grep '^name:' "$f" 2>/dev/null | head -1 | sed 's/^name: *//' || echo "$(basename "$f" .md)")
                tag=$(grep '^dispatch_tag:' "$f" 2>/dev/null | head -1 | sed 's/^dispatch_tag: *//' || echo "—")
                echo "    └─ $name [tag: $tag]"
            done
        fi
    done
else
    echo "  (agent directory not found)"
fi

echo ""
echo "📊 Git Status:"
git status --short | sed 's/^/  /'
echo ""
echo "========================================"
