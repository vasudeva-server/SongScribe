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
#   ./scripts/test.sh --debug e2e                              # e2e tests with debug pausing
#   ./scripts/test.sh SMuFLMetadataTest                        # Specific unit class (bare names use the unit task; prefix e2e classes with e2e)
#   ./scripts/test.sh BeamingTest.testFlipStemDirection        # Specific test method
#   ./scripts/test.sh 'NoteConnectionTest$Beaming'             # Nested class (single quotes prevent $ expansion)
#   ./scripts/test.sh 'NoteConnectionTest$Beaming.method'      # Method in nested class
#   ./scripts/test.sh SMuFLMetadataTest BeamingTest            # Multiple classes
#   ./scripts/test.sh -Dtest=*Test                             # Class name pattern

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

GRADLE_FLAGS=()
GRADLE_PROPS=()
TASKS=()
TEST_FILTERS=()

while [[ "${1:-}" == --* ]]; do
  case "$1" in
    --debug)      GRADLE_PROPS+=("-Pe2eDebug") ;;
    --keep-going) GRADLE_FLAGS+=("--continue"); GRADLE_PROPS+=("-PnoFailFast") ;;
    *)            GRADLE_FLAGS+=("$1") ;;
  esac
  shift
done

case "${1:-}" in
  e2e)  TASKS=("e2eTest"); shift ;;
  unit) TASKS=("test");    shift ;;
  "")   TASKS=("test" "e2eTest") ;;
esac

for arg in "$@"; do
  if [[ "$arg" == -Dtest=* ]]; then
    TEST_FILTERS+=("--tests" "${arg#-Dtest=}")
  else
    TEST_FILTERS+=("--tests" "*${arg}")
  fi
done

if [[ ${#TEST_FILTERS[@]} -gt 0 && ${#TASKS[@]} -eq 0 ]]; then
  TASKS=("test")
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
