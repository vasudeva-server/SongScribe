#!/usr/bin/env bash
# Warn when reading a whole Java file large enough to be worth surveying
# symbolically first. Allows the read — this costs tokens, not correctness.
# See .agents/rules/serena.md.

set -euo pipefail

readonly MIN_LINES_TO_SURVEY=150

payload=$(cat)

field() {
  local name="$1"
  printf '%s' "$payload" | jq -r ".tool_input.${name} // \"\""
}

file_path=$(field file_path)
offset=$(field offset)
limit=$(field limit)

# A ranged read is already targeted.
if [[ -n "$offset" || -n "$limit" ]]; then
  exit 0
fi

if [[ "$file_path" != *.java || ! -f "$file_path" ]]; then
  exit 0
fi

line_count=$(wc -l < "$file_path" | tr -d '[:space:]')

if (( line_count < MIN_LINES_TO_SURVEY )); then
  exit 0
fi

read -r -d '' context <<EOF || true
This reads all ${line_count} lines of a Java file. If you need to know what the
class contains rather than its full source, survey it symbolically first:

  get_symbols_overview("${file_path}")
      top-level symbols; depth=1 also lists members

  find_symbol("Class/member", include_body=true)
      just the one you need

Read the whole file when you genuinely need the whole file.
EOF

jq -n --arg context "$context" \
  '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "allow", additionalContext: $context}}'
