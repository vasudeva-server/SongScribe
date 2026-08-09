#!/usr/bin/env bash
# Run PIT mutation testing via the Gradle plugin, optionally scoped to one class
# or package, then open the HTML report. Mutation testing reruns the covering
# unit tests against many mutated copies of the production code, so keep the
# scope narrow.
#
# Usage: ./scripts/mutation-test.sh [--open] [target]
#   --open  Open the HTML report in the browser when done. Optional.
#   target  Fully-qualified class or comma-separated package globs to mutate.
#           Optional; when omitted, the default logic-package scope in
#           build.gradle.kts is used.
#
# Examples:
#   ./scripts/mutation-test.sh                               # default logic-package scope
#   ./scripts/mutation-test.sh songscribe.util.StringUtils   # a single class
#   ./scripts/mutation-test.sh 'songscribe.smufl.*'          # a single package (quote the glob)
#   ./scripts/mutation-test.sh --open songscribe.util.StringUtils  # also open the report

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
GRADLEW="$PROJECT_DIR/gradlew"

source "$SCRIPT_DIR/set-java-home.sh"

OPEN_REPORT=0
ARGS=()

for arg in "$@"; do
  if [[ "$arg" == "--open" ]]; then
    OPEN_REPORT=1
  else
    ARGS+=("$arg")
  fi
done

GRADLE_ARGS=()

if [[ -n "${ARGS[0]:-}" ]]; then
  TARGET="${ARGS[0]}"
  GRADLE_ARGS+=("-PpitestTarget=$TARGET")

  # Derive the test-class scope from the target so the minion only runs tests
  # in the same package. Without this PIT sends every test class to the minion,
  # including tests that fail to initialise in the headless minion environment.
  if [[ "$TARGET" == *","* ]]; then
    # Multi-package target — keep all songscribe tests
    TARGET_TESTS="songscribe.*"
  elif [[ "$TARGET" == *".*" ]]; then
    # Package glob: songscribe.util.* → songscribe.util.*
    TARGET_TESTS="$TARGET"
  else
    # Fully-qualified class: songscribe.util.StringUtils → songscribe.util.*
    PACKAGE="${TARGET%.*}"
    TARGET_TESTS="${PACKAGE}.*"
  fi

  GRADLE_ARGS+=("-PpitestTargetTests=$TARGET_TESTS")
fi

echo "Running mutation testing..."
"$GRADLEW" pitest --info "${GRADLE_ARGS[@]}" --project-dir "$PROJECT_DIR"

REPORT="$PROJECT_DIR/build/reports/pitest/index.html"
echo ""
echo "Mutation report: $REPORT"
echo "Surviving mutants (machine-readable): $PROJECT_DIR/build/reports/pitest/mutations.xml"

if [[ "$OPEN_REPORT" -eq 1 ]]; then
  open "$REPORT" 2>/dev/null || true
fi
