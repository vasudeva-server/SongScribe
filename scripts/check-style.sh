#!/usr/bin/env bash
# Check Java/Kotlin source file for mechanical code style violations.
# Covers rules that can be verified without semantic understanding.
# See .claude/rules/code-styles/java-kotlin.md for full rules.
#
# Usage: check-style.sh <file>
# Exit 1 if violations found.

file="$1"
[[ -f "$file" ]] || exit 0

violations=""

check() {
    local label="$1"
    local result="$2"
    if [[ -n "$result" ]]; then
        violations+="  ${label}\n$(echo "$result" | sed 's/^/    /')\n"
    fi
}

# Tabs instead of spaces
check "Tabs (use 4 spaces):" "$(grep -n $'\t' "$file" | head -5)"

# Lines over 120 characters
check "Lines > 120 chars:" "$(awk 'length > 120 {printf "L%d: %d chars\n", NR, length}' "$file" | head -5)"

# Wildcard imports
check "Wildcard imports (not allowed):" "$(grep -n 'import .*\*;' "$file")"

# Single-statement control flow without braces (same-line body heuristic)
check "Missing braces on control flow:" "$(grep -En '^\s*(if|else if|for|while)\s*\(.*\)\s+[^{]' "$file" | grep -v '^\s*//' | head -5)"

if [[ -n "$violations" ]]; then
    echo "Style violations in $(basename "$file"):"
    echo -e "$violations"
    echo "Note: naming, structure, and annotation rules require Claude review."
    echo "See .claude/rules/code-styles/java-kotlin.md"
    exit 1
fi
