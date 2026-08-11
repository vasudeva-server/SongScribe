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

# Matched as a standalone word anywhere in the command, not only at the front:
# requiring it to lead means it cannot be written inside a loop or a pipeline, and
# `PROSE=1 bash -c '...'` is a wrapper nobody should have to think of.
readonly ESCAPE_HATCH='(^|[[:space:]])PROSE=1([[:space:]]|$)'

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
if matches "$ESCAPE_HATCH"; then
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

PROSE=1 is not a way around this rule — it is for the questions serena cannot
answer, because they are about text rather than about symbols:
  - does this literal / comment / Javadoc phrase appear anywhere
  - what does this file's raw text look like at these lines
Put PROSE=1 anywhere in the command as its own word; it need not come first, so
it works inside a loop or a pipeline.

You do not need it for a command that names only non-Java paths (docs/, scripts/,
.agents/, .claude/, or a .md/.json/.xml/.sh/.gradle/... file) — those already pass
untouched. A search naming no path at all defaults to this Java repo, so it counts
as searching Java.
EOF

jq -n --arg reason "$reason" \
  '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: $reason}}'
