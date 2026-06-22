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
grep -A20 '^Frozen Artifacts:' LOOP-STATE.md | grep -v '^Frozen Artifacts:' | grep -v '^$' | sed 's/^/  - /'

echo ""
echo "📊 Git Status:"
git status --short | sed 's/^/  /'
echo ""
echo "========================================"
