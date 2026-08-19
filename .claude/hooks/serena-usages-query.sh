#!/usr/bin/env bash
# Deny rg/grep searches over Java source. Those are usages queries, and rg
# answers them incompletely. See .claude/rules/serena.md.
#
# Classification is on the search *target*, not the pattern shape: everything we
# want to catch (callers, field usages, symbol overviews, type relationships) is
# a search over .java, and everything we want to spare (docs, scripts, gradle,
# properties) is not.
#
# Three kinds of query get told apart, and they are gated differently:
#
#   - A search whose pattern is a single bare identifier (PascalCase,
#     camelCase, snake_case, UPPER_SNAKE — one token, no spaces) is a symbol
#     query by shape. PROSE=1 does not excuse it: it must show evidence that a
#     serena symbol tool was actually tried for that identifier recently. A
#     self-declared flag can be typed reflexively; a prior tool call in the
#     transcript cannot be faked the same way. If the identifier is genuinely
#     just an English word that happens to be one token, running find_symbol on
#     it and getting nothing back *is* the required evidence — that is exactly
#     the documented fallback condition in .claude/rules/serena.md ("reach for
#     rg only when a jet_brains_* tool returns no results").
#
#   - A search whose pattern is "new SomeType(" is a constructor call-site
#     query by shape, gated the same way as a bare identifier (evidence, not
#     PROSE=1) against the type name — Java constructors are named after their
#     class, so find_referencing_symbols on the class answers it directly. It
#     fails the bare-identifier shape (the space makes it multi-token) but is
#     no less a symbol query, and letting PROSE=1 through here would make every
#     constructor-usage search "add PROSE=1" instead of "use serena".
#
#   - A search whose pattern is neither of the above (a phrase, a regex with
#     alternation/spaces, a quoted sentence) cannot be a symbol query by shape
#     — Java declarations and English phrases don't collide here the way
#     identifiers and constructor calls do — and is gated the old way: it must
#     say PROSE=1.
#
# A PROSE=1 that appears on a command that isn't an rg/grep search at all does
# nothing (see the first check below) and is denied so it doesn't calcify into
# a reflex prefix.

set -euo pipefail

# Matched as a standalone word anywhere in the command, not only at the front:
# requiring it to lead means it cannot be written inside a loop or a pipeline, and
# `PROSE=1 bash -c '...'` is a wrapper nobody should have to think of.
readonly ESCAPE_HATCH='(^|[[:space:]])PROSE=1([[:space:]]|$)'

# How many trailing lines of the transcript JSONL to scan for a prior serena
# symbol query. Bounded for hook latency; "recently" is deliberately loose —
# the rule is "tried serena first this session for this name", not "in the
# immediately preceding tool call".
readonly TRANSCRIPT_TAIL=500

# Read stdin once — a second jq invocation against the same pipe would see
# EOF, since the first jq call already consumed it.
hook_input=$(cat)
cmd=$(jq -r '.tool_input.command // ""' <<< "$hook_input")
transcript=$(jq -r '.transcript_path // ""' <<< "$hook_input")

# Drop the body of every quoted heredoc (`<<'EOF'`, `<<"EOF"`), keeping the line
# that opens it. A quoted body is literal data this shell never executes, so a
# command writing a document that happens to name a .java path would otherwise be
# classified as a search over Java source. The opener stays because the real
# command lives on it.
strip_heredoc_bodies() {
  local line out="" delimiter=""

  while IFS= read -r line; do
    if [[ -n "$delimiter" ]]; then
      if [[ "$line" =~ ^[[:space:]]*"$delimiter"[[:space:]]*$ ]]; then
        delimiter=""
      fi

      continue
    fi

    if [[ "$line" =~ \<\<-?[[:space:]]*[\'\"]([A-Za-z_][A-Za-z0-9_]*)[\'\"] ]]; then
      delimiter="${BASH_REMATCH[1]}"
    fi

    out+="$line"$'\n'
  done <<< "$1"

  printf '%s' "$out"
}

cmd=$(strip_heredoc_bodies "$cmd")

matches() {
  local pattern="$1"
  printf '%s' "$cmd" | grep -qE "$pattern"
}

deny() {
  local reason="$1"
  jq -n --arg reason "$reason" \
    '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: $reason}}'
  exit 0
}

# Not a text search at all. A stray PROSE=1 here has no effect anywhere below,
# so catch it rather than let it silently pass as a habit.
if ! matches '\b(rg|grep)\b'; then
  if matches "$ESCAPE_HATCH"; then
    deny 'PROSE=1 has no effect on a command that is not an rg/grep search — it only ever excuses a text search over Java source. Drop it.'
  fi
  exit 0
fi

# The constructor-body lookup documented in .claude/rules/serena.md.
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
readonly NON_JAVA_TARGET='\.(md|txt|json|xml|gradle|properties|sh|yml|yaml|html|csv|log|groovy|py)\b|(^|[[:space:]])(docs|scripts|\.claude|build|gradle)/'

# A search naming only non-Java paths is fine. A search naming no path at all
# defaults to the working directory, which is a Java repo — so it counts.
if ! matches "$JAVA_TARGET" && matches "$NON_JAVA_TARGET"; then
  exit 0
fi

# --- Extract the search pattern (best-effort, not a shell parser) ---
#
# Grab from the rg/grep invocation up to the next pipe or semicolon. A quoted
# argument is taken whole — a quoted multi-word phrase must stay one candidate
# token, or naive whitespace-splitting would peel off its first word and that
# first word alone can look identifier-shaped even though the phrase it came
# from plainly isn't. Only when nothing is quoted do we fall back to word
# splitting to find the first non-flag token.

segment=$(printf '%s' "$cmd" | grep -oE '\b(rg|grep)\b[^|;]*' | head -1)

extract_query() {
  local seg="$1"

  if [[ "$seg" =~ \"([^\"]*)\" ]]; then
    printf '%s' "${BASH_REMATCH[1]}"
    return 0
  fi

  if [[ "$seg" =~ \'([^\']*)\' ]]; then
    printf '%s' "${BASH_REMATCH[1]}"
    return 0
  fi

  local -a words
  read -ra words <<< "$seg"
  local skip_next=0
  local i w
  for ((i = 1; i < ${#words[@]}; i++)); do
    w="${words[$i]}"

    if ((skip_next)); then
      skip_next=0
      continue
    fi

    if [[ "$w" == -* ]]; then
      if [[ "$w" =~ ^-(e|m|A|B|C|t|g|-type|-glob|-context)$ ]]; then
        skip_next=1
      fi
      continue
    fi

    printf '%s' "$w"
    return 0
  done
}

query=$(extract_query "$segment")

serena_evidence() {
  local ident="$1"
  [[ -f "$transcript" ]] || return 1
  tail -n "$TRANSCRIPT_TAIL" "$transcript" 2>/dev/null | jq -e -r \
    --arg ident "$ident" \
    'select(.type == "assistant") | .message.content[]?
     | select(.type == "tool_use")
     | select(.name | test("jet_brains_(find_symbol|find_referencing_symbols)$"))
     | (.input | tostring)
     | select(test("\\b" + $ident + "\\b"))' \
    >/dev/null 2>&1
}

readonly IDENT_SHAPE='^[A-Za-z_][A-Za-z0-9_]*$'

if [[ -n "$query" && "$query" =~ $IDENT_SHAPE ]]; then
  if serena_evidence "$query"; then
    exit 0
  fi

  deny "\"$query\" looks like a single symbol name, not prose. PROSE=1 does not excuse this — it only covers phrase/text search, and a bare identifier is a symbol query by shape.

Try serena first:
  find_symbol(\"$query\")
  find_referencing_symbols(\"Class/$query\", file)

If \"$query\" is genuinely just a word, not a Java symbol, running find_symbol on it and getting nothing back is itself the evidence this hook looks for — run it, then retry the rg/grep command."
fi

# "new SomeException(" and friends: a constructor call-site search. This is
# multi-token (fails IDENT_SHAPE) but is still a symbol query, not prose — it
# asks where a type is constructed, which find_referencing_symbols answers
# directly by pointing at the type's own name (Java constructors are named
# after their class). PROSE=1 must not excuse it, or every usages query for a
# constructor becomes "add PROSE=1" instead of "use serena".
readonly CTOR_CALL_SHAPE='^new[[:space:]]+([A-Za-z_][A-Za-z0-9_]*)'

if [[ -n "$query" && "$query" =~ $CTOR_CALL_SHAPE ]]; then
  class_name="${BASH_REMATCH[1]}"

  if serena_evidence "$class_name"; then
    exit 0
  fi

  deny "\"$query\" is a constructor call-site search for $class_name, not prose. PROSE=1 does not excuse this — it only covers phrase/text search, and a constructor call is a symbol query by shape.

Try serena first:
  find_symbol(\"$class_name\")
  find_referencing_symbols(\"$class_name/$class_name\", file)   # Java constructors share the class's name

If \"$class_name\" is genuinely not a Java type, running find_symbol on it and getting nothing back is itself the evidence this hook looks for — run it, then retry the rg/grep command."
fi

# Not identifier-shaped: a real phrase/regex search. This is the case PROSE=1
# exists for.
if matches "$ESCAPE_HATCH"; then
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
.claude/, or a .md/.json/.xml/.sh/.gradle/... file) — those already pass
untouched. A search naming no path at all defaults to this Java repo, so it counts
as searching Java.
EOF

deny "$reason"
