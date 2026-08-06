#!/usr/bin/env bash
# Run tests via Gradle
# Usage: ./scripts/test.sh [--debug] [--keep-going] [--<gradle-flag>...] [unit|e2e|ClassName|ClassName.method|...]
# Examples:
#   ./scripts/test.sh                                          # All tests (unit then e2e)
#   ./scripts/test.sh e2e                                      # e2e tests only
#   ./scripts/test.sh e2e FontChooserTest                      # Specific e2e class (e2e prefix required)
#   ./scripts/test.sh e2e FontChooserTest.testFoo              # Specific e2e method
#   ./scripts/test.sh unit                                     # Unit tests only
#   ./scripts/test.sh --keep-going                             # All tests, continue past failures
#   ./scripts/test.sh --debug e2e                              # e2e tests with debug pausing (e2e only)
#   ./scripts/test.sh SMuFLMetadataTest                        # Specific unit class
#   ./scripts/test.sh SMuFLMetadataTest.testNoteHeadWidthSsIsPositiveAndPlausible
#   ./scripts/test.sh 'MessageCenterTest$MessageToString'      # Nested class (single quotes prevent $ expansion)
#   ./scripts/test.sh 'MessageCenterTest$MessageToString.method'
#   ./scripts/test.sh SMuFLMetadataTest MessageCenterTest      # Multiple classes
#   ./scripts/test.sh -Dtest=*Test                             # Class name pattern
#
# Targets are checked against src/test/java before Gradle runs: an unknown class,
# an e2e class without the e2e prefix, a unit/e2e mix, or --debug outside e2e all
# fail immediately with the command to use instead.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TEST_SRC_DIR="$PROJECT_DIR/src/test/java"
E2E_SRC_DIR="$TEST_SRC_DIR/songscribe/e2e"

GRADLE_FLAGS=()
GRADLE_PROPS=()
TASKS=()
TEST_FILTERS=()
E2E_TARGETS=()
UNIT_TARGETS=()
UNKNOWN_TARGETS=()
EXPLICIT_TASK=""
DEBUG_REQUESTED=false

# Print each argument on its own line, then abort.
die() {
  printf '%s\n' "$@" >&2
  exit 1
}

# Extract the outer class name from a target such as `Foo`, `Foo.method`,
# `Foo$Nested.method` or `songscribe.e2e.Foo` — the first dot/dollar-separated
# component that starts with an uppercase letter.
outer_class() {
  local part
  local IFS='.$'

  # shellcheck disable=SC2086  # word splitting on IFS is the point
  for part in $1; do
    if [[ "$part" == [A-Z]* ]]; then
      echo "$part"
      return
    fi
  done
}

# Join targets into a runnable argument list, single-quoting the ones holding a
# `$` so the suggested command survives shell expansion.
quote_targets() {
  local target
  local quoted=()

  for target in "$@"; do
    if [[ "$target" == *'$'* ]]; then
      quoted+=("'$target'")
    else
      quoted+=("$target")
    fi
  done

  echo "${quoted[*]}"
}

# Echo `e2e`, `unit`, or nothing if no such test class exists.
classify_target() {
  local class
  class="$(outer_class "$1")"

  if [[ -z "$class" ]]; then
    return
  fi

  if [[ -f "$E2E_SRC_DIR/$class.java" ]]; then
    echo e2e
  elif [[ -n "$(find "$TEST_SRC_DIR" -name "$class.java" -print -quit)" ]]; then
    echo unit
  fi
}

while [[ "${1:-}" == --* ]]; do
  case "$1" in
    --debug)      GRADLE_PROPS+=("-Pe2eDebug"); DEBUG_REQUESTED=true ;;
    --keep-going) GRADLE_FLAGS+=("--continue"); GRADLE_PROPS+=("-PnoFailFast") ;;
    *)            GRADLE_FLAGS+=("$1") ;;
  esac
  shift
done

case "${1:-}" in
  e2e)  EXPLICIT_TASK="e2e";  TASKS=("e2eTest"); shift ;;
  unit) EXPLICIT_TASK="unit"; TASKS=("test");    shift ;;
  "")   TASKS=("test" "e2eTest") ;;
esac

for arg in "$@"; do
  if [[ "$arg" == -Dtest=* ]]; then
    TEST_FILTERS+=("--tests" "${arg#-Dtest=}")
    continue
  fi

  TEST_FILTERS+=("--tests" "*${arg}")

  # Wildcards can straddle both tasks, so they are left unclassified.
  if [[ "$arg" == *[\*\?]* ]]; then
    continue
  fi

  case "$(classify_target "$arg")" in
    e2e)  E2E_TARGETS+=("$arg") ;;
    unit) UNIT_TARGETS+=("$arg") ;;
    *)    UNKNOWN_TARGETS+=("$arg") ;;
  esac
done

if [[ ${#UNKNOWN_TARGETS[@]} -gt 0 ]]; then
  die "No test class found under src/test/java for: ${UNKNOWN_TARGETS[*]}"
fi

# The unit task excludes e2e and the e2e task includes only e2e, so a target on
# the wrong side would silently match nothing.
if [[ "$EXPLICIT_TASK" == "unit" && ${#E2E_TARGETS[@]} -gt 0 ]]; then
  die "e2e tests cannot run under the unit task: ${E2E_TARGETS[*]}" \
      "Run: ./scripts/test.sh e2e $(quote_targets "${E2E_TARGETS[@]}")"
fi

if [[ "$EXPLICIT_TASK" == "e2e" && ${#UNIT_TARGETS[@]} -gt 0 ]]; then
  die "Not e2e tests: ${UNIT_TARGETS[*]}" \
      "Run: ./scripts/test.sh unit $(quote_targets "${UNIT_TARGETS[@]}")"
fi

# Bare names default to the unit task, so e2e classes must say so explicitly.
# Requiring the prefix also keeps e2e runs behind their approval prompt.
if [[ "$EXPLICIT_TASK" == "" && ${#E2E_TARGETS[@]} -gt 0 ]]; then
  if [[ ${#UNIT_TARGETS[@]} -gt 0 ]]; then
    die "Unit and e2e tests must be run separately." \
        "Run: ./scripts/test.sh unit $(quote_targets "${UNIT_TARGETS[@]}")" \
        "Then: ./scripts/test.sh e2e $(quote_targets "${E2E_TARGETS[@]}")"
  fi

  die "e2e tests need the e2e prefix: ${E2E_TARGETS[*]}" \
      "Run: ./scripts/test.sh e2e $(quote_targets "${E2E_TARGETS[@]}")"
fi

if [[ ${#TEST_FILTERS[@]} -gt 0 && ${#TASKS[@]} -eq 0 ]]; then
  TASKS=("test")
fi

if [[ "$DEBUG_REQUESTED" == true && "$EXPLICIT_TASK" != "e2e" ]]; then
  die "--debug applies to e2e tests only. Run: ./scripts/test.sh --debug e2e [targets]"
fi

# Keep the Gradle daemon's display access in sync with this session. Test workers are
# forked by the daemon and inherit its login session, so a daemon born in a headless
# context (SSH / Background) keeps forking headless workers even after you return to a
# GUI session — silently skipping the @RequiresDisplay UI tests. When this shell is in
# a GUI (Aqua) session but the daemon reports headless, it is stale: stop it so the next
# task spawns a fresh, display-capable daemon. On non-macOS, launchctl is absent and the
# guard is a no-op.
if [[ "$(launchctl managername 2>/dev/null || true)" == "Aqua" ]]; then
  DAEMON_HEADLESS="$("$PROJECT_DIR/gradlew" -q daemonHeadless --project-dir "$PROJECT_DIR" 2>/dev/null || true)"

  if [[ "$DAEMON_HEADLESS" == "true" ]]; then
    echo "Restarting stale headless Gradle daemon so UI tests can run..."
    "$PROJECT_DIR/gradlew" --stop >/dev/null 2>&1 || true
  fi
fi

echo "Compiling tests..."

if ! "$PROJECT_DIR/gradlew" -q testClasses --project-dir "$PROJECT_DIR"; then
  echo "Compilation failed."
  exit 1
fi

echo -n "Running tests..."

"$PROJECT_DIR/gradlew" "${GRADLE_FLAGS[@]}" "${GRADLE_PROPS[@]}" \
  "${TASKS[@]}" "${TEST_FILTERS[@]}" \
  --project-dir "$PROJECT_DIR" \
  2> /dev/null | grep -E -v '^(> Task :|[0-9]+ actionable|Consider enabling configuration cache)'
