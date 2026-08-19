#!/usr/bin/env bash
# Deny any test run that is the suite rather than a check of what just changed.
# See .claude/guides/testing-common.md, "What runs".
#
# The rule this enforces: a test run names the classes covering the code just
# written or modified, and nothing else. The suite is not a thing to run after a
# compile, because a green suite says nothing about the design and re-verifies
# code nothing has touched.
#
# There is no escape hatch here on purpose. Every earlier attempt at one — a
# sentinel variable, a flag, a magic word — becomes a reflex prefix within a
# session or two, at which point the gate is decorative. The user runs the full
# suite by typing `!./scripts/test.sh` at the prompt: `!` commands are executed
# by the CLI and never become a Bash tool call, so no PreToolUse hook sees them.
# The channel is open to the user and closed to the model, which is the whole
# asymmetry this file relies on. Ask; do not work around.
#
# Four shapes are denied:
#
#   - no target at all (bare, or only flags) — the unit suite
#   - `unit` or `e2e` as the only target — the same thing said explicitly
#   - more than MAX_TARGETS classes in one command — the suite reassembled by hand
#   - a loop or xargs driving test.sh — the same, with the count hidden from view
#
# Patterns (`-Dtest=*Test`, `S*Test`) are not checked here. test.sh rejects them
# itself, which is the better place: it holds whether or not a hook is loaded.
#
# What is NOT denied is anything that merely names the script without running it
# — reading it, checking its syntax, editing it, or writing a document that
# quotes the commands above. Only a command position counts, which is why this
# parses the command rather than matching the text.

set -euo pipefail

# Four covers a change touching a handful of classes. Above that the run has
# stopped being a check of what changed.
readonly MAX_TARGETS=4

hook_input=$(cat)
cmd=$(jq -r '.tool_input.command // ""' <<< "$hook_input")

deny() {
  jq -n --arg reason "$1" \
    '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: $reason}}'
  exit 0
}

readonly ASK_INSTEAD='If the full suite genuinely needs to run, say so and let the user run it — they type `!./scripts/test.sh`, which no hook intercepts. Do not look for another way to run it yourself.'

# Drop the body of every quoted heredoc (`<<'EOF'`, `<<"EOF"`), keeping the line
# that opens it. A quoted body is literal data this shell never executes, so a
# command that writes documentation about these very commands would otherwise be
# read as running them — not hypothetical, since the guides describing this rule
# quote what it denies. The opener stays because the real command lives on it.
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

# Index of the command word within `words`, skipping what may legally precede one:
# shell keywords opening a compound command, and leading VAR=value assignments.
command_word_index() {
  local -n ref=$1
  local i=0

  while ((i < ${#ref[@]})); do
    case "${ref[$i]}" in
      do | then | else | elif | if | while | until | time | '!' | '{' | '(') i=$((i + 1)) ;;
      *=*)                                                                   i=$((i + 1)) ;;
      *)                                                                     break ;;
    esac
  done

  printf '%s' "$i"
}

runs=0
targets=0
bare=false

# Each separator starts a new command, so splitting on them puts every command on
# its own line and makes "first word" mean what it says.
while IFS= read -r segment; do
  read -ra words <<< "$segment"
  ((${#words[@]} > 0)) || continue

  # Quotes are shell syntax, not part of the word. Without this, `bash -c
  # './scripts/test.sh'` reads as a word ending in a quote and matches nothing.
  for i in "${!words[@]}"; do
    words[$i]="${words[$i]//[\'\"]/}"
  done

  start=$(command_word_index words)
  ((start < ${#words[@]})) || continue

  leader="${words[$start]}"
  args=("${words[@]:$((start + 1))}")

  # An interpreter or a dispatcher runs the script named among its arguments.
  # Which argument it is cannot be found by skipping flags, because a flag may
  # take a value (`xargs -n 1 …`) that would then be mistaken for the command —
  # so look for the script itself.
  if [[ "$leader" == bash || "$leader" == sh || "$leader" == zsh || "$leader" == xargs ]]; then
    # Syntax checking executes nothing.
    for arg in "${args[@]}"; do
      [[ "$arg" == -n && "$leader" != xargs ]] && continue 2
    done

    found=""

    for arg in "${args[@]}"; do
      if [[ "$arg" == *test.sh || "$arg" == *gradlew || "$arg" == gradle ]]; then
        found="$arg"
        break
      fi
    done

    [[ -n "$found" ]] || continue

    leader="$found"
    args=()
  fi

  if [[ "$leader" == *gradlew || "$leader" == gradle ]]; then
    for arg in "${args[@]}"; do
      case "$arg" in
        test | e2eTest | testClasses | check | build | :test | :e2eTest)
          deny "Gradle's test tasks run the whole suite and skip every check in scripts/test.sh. Use ./scripts/test.sh with the classes covering what changed.

$ASK_INSTEAD"
          ;;
      esac
    done

    continue
  fi

  [[ "$leader" == *test.sh ]] || continue

  runs=$((runs + 1))
  count=0

  # A leading `unit`/`e2e` selects the task rather than naming a class, and a
  # flag is not a target either.
  for arg in "${args[@]}"; do
    case "$arg" in
      -* | unit | e2e) continue ;;
      # Plain assignment, not (( )): an arithmetic expression evaluating to zero
      # reports failure, and under `set -e` that would end the script — silently
      # allowing the bare invocation this exists to catch.
      *)               count=$((count + 1)) ;;
    esac
  done

  if [[ $count -eq 0 ]]; then
    bare=true
  fi

  targets=$((targets + count))
done <<< "$(printf '%s' "$cmd" | sed -E 's/(\|\||&&|[;&|])/\n/g')"

((runs > 0)) || exit 0

# Checked only once a real run is present, so a document quoting a loop is left
# alone. Segment splitting puts the body on its own line, so the loop's own
# keyword is what identifies it.
if printf '%s' "$cmd" | grep -qE '\b(for|while|until|xargs)\b'; then
  deny "A loop driving ./scripts/test.sh runs the suite with the class count out of sight. Name the classes in one invocation instead — up to $MAX_TARGETS of them.

$ASK_INSTEAD"
fi

if [[ "$bare" == true ]]; then
  deny "./scripts/test.sh with no class named runs the entire suite. Name the test classes covering the code you just wrote or changed:

  ./scripts/test.sh SomeTest AnotherTest

To find them, run jet_brains_find_referencing_symbols on the members you changed and take the test classes among the results.

$ASK_INSTEAD"
fi

if ((targets > MAX_TARGETS)); then
  deny "$targets test classes in one command is the suite reassembled by hand; the limit is $MAX_TARGETS. Run the ones covering what you actually changed.

If a change really did touch more than $MAX_TARGETS classes' worth of contracts, that is worth saying out loud before running anything — it usually means the change is doing more than one thing.

$ASK_INSTEAD"
fi

exit 0
