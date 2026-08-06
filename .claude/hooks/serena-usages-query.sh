#!/usr/bin/env bash
# Deny rg/grep searches over Java source. Those are usages queries, and rg
# answers them incompletely. See .agents/rules/serena.md.
#
# Classification is on the search *target*, not the pattern shape: everything we
# want to catch (callers, field usages, symbol overviews, type relationships) is
# a search over .java, and everything we want to spare (docs, scripts, gradle,
# properties) is not.
#
# There is deliberately no attempt to tell a symbol from a phrase. Java
# declarations are multi-word too ("class Foo", "static final MAX_X"), so every
# heuristic that spared prose also spared declarations. Searching Java source
# for prose now requires saying so with the PROSE=1 prefix.

set -euo pipefail

readonly ESCAPE_HATCH_PREFIX='PROSE=1'

cmd=$(jq -r '.tool_input.command // ""')

matches() {
  local pattern="$1"
  printf '%s' "$cmd" | grep -qE "$pattern"
}

# Not a text search at all.
if ! matches '\b(rg|grep)\b'; then
  exit 0
fi

# Deliberate opt-out: the caller is asserting this searches prose, not symbols.
if [[ "$cmd" == "$ESCAPE_HATCH_PREFIX"* ]]; then
  exit 0
fi

# The constructor-body lookup documented in .agents/rules/serena.md.
if matches '\\s\*\\\('; then
  exit 0
fi

# Markers only ever appear in comments, and no symbolic tool finds them.
if matches '\b(TODO|FIXME|XXX|HACK)\b'; then
  exit 0
fi

# --- Does this search Java source? ---

# src/main/java, not src/main/resources — the latter holds Strings.properties.
readonly JAVA_TARGET='\.java\b|src/[^ ]*/java|-t[[:space:]]*java|--type[= ]java'
readonly NON_JAVA_TARGET='\.(md|txt|json|xml|gradle|properties|sh|yml|yaml|html|csv|log|groovy|py)\b|(^|[[:space:]])(docs|scripts|\.agents|\.claude|build|gradle)/'

# A search naming only non-Java paths is fine. A search naming no path at all
# defaults to the working directory, which is a Java repo — so it counts.
if ! matches "$JAVA_TARGET" && matches "$NON_JAVA_TARGET"; then
  exit 0
fi

read -r -d '' reason <<'EOF' || true
This searches Java source, where rg answers incompletely: it searches only the
paths you name, matches text rather than types, and counts comments and string
literals as code.

Use serena:
  find_symbol("Name")                            -> the declaring file (relative_path is optional)
  find_referencing_symbols("Class/member", file) -> every usage
  get_symbols_overview(file)                     -> what a class contains
  type_hierarchy("Class", file)                  -> extends/implements

If you are searching prose rather than symbols — comments, Javadoc, string
literals — prefix the command with PROSE=1 to say so explicitly.
EOF

jq -n --arg reason "$reason" \
  '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: $reason}}'
