#!/usr/bin/env bash
# Run tests with JUnit Console Launcher (tree-style output)
# Usage: ./scripts/test.sh [--debug] [--slow] [--keep-going] [--verbose] [e2e|unit|test-pattern]
# Examples:
#   ./scripts/test.sh                    # Run all tests (unit then e2e, stops on failure)
#   ./scripts/test.sh e2e                  # Run only e2e tests
#   ./scripts/test.sh unit                   # Run only unit tests (excludes e2e)
#   ./scripts/test.sh --verbose                 # Run all tests with tree-style output
#   ./scripts/test.sh --keep-going              # Run all tests, continue past failures
#   ./scripts/test.sh --debug e2e              # Run e2e tests, pausing between each test
#   ./scripts/test.sh --slow GlissandoTest           # Run with 1s pause between UI actions
#   ./scripts/test.sh SMuFLMetadataTest            # Run specific test class (multiple space-separated classes and/or methods allowed)
#   ./scripts/test.sh BeamingTest.testFlipStemDirection    # Run specific test method (multiple space-separated methods and/or classes allowed)
#   ./scripts/test.sh 'GraceNoteTest$EdgeCases'       # Run all tests in a @Nested inner class (use single quotes)
#   ./scripts/test.sh 'GraceNoteTest$EdgeCases.testFoo'   # Run specific method in a @Nested inner class
#   ./scripts/test.sh -Dtest=*Test               # Run with Maven pattern

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

source "$SCRIPT_DIR/set-java-home.sh"

JVM_ARGS=()
DETAILS="summary"
KEEP_GOING=false

while [[ "$1" == --* ]]; do
  case "$1" in
    --debug)   JVM_ARGS+=("-De2e.debug=true") ;;
    --slow)    JVM_ARGS+=("-De2e.slow=true") ;;
    --keep-going) KEEP_GOING=true ;;
    --verbose)   DETAILS="tree" ;;
    *)       break ;;
  esac
  shift
done

if [[ "$(uname)" == "Darwin" ]]; then
  JVM_ARGS+=(
    "-Dapple.laf.useScreenMenuBar=true"
    "-Dapple.awt.application.appearance=system"
  )
fi

if [ "$KEEP_GOING" = false ]; then
  JVM_ARGS+=("-De2e.failFast=true")
fi

JVM_ARGS+=(
  "--enable-native-access=ALL-UNNAMED"
  "-XX:+EnableDynamicAgentLoading"
  "-Xshare:off"
  "--add-opens" "java.desktop/javax.swing=ALL-UNNAMED"
  "--add-opens" "java.desktop/javax.swing.plaf.basic=ALL-UNNAMED"
  "--add-opens" "java.desktop/java.awt=ALL-UNNAMED"
  "--add-opens" "java.base/java.lang=ALL-UNNAMED"
  "--add-opens" "java.base/java.util=ALL-UNNAMED"
)

echo "Compiling..."

if ! mvn -q test-compile -f "$PROJECT_DIR/pom.xml"; then
  echo "Compilation failed."
  exit 1
fi

CP_FILE=$(mktemp)
trap 'rm -f "$CP_FILE"' EXIT
mvn -q dependency:build-classpath -f "$PROJECT_DIR/pom.xml" \
  -DincludeScope=test -Dmdep.outputFile="$CP_FILE" || exit 1
CLASSPATH="$PROJECT_DIR/target/classes:$PROJECT_DIR/target/test-classes:$(cat "$CP_FILE")"

TEST_DIR="$PROJECT_DIR/target/test-classes"
SCAN_CLASSPATH="--scan-classpath=$TEST_DIR"
UNIT_FILTER="--exclude-package=songscribe.e2e"
E2E_FILTER="--include-package=songscribe.e2e"
BASE_LAUNCHER_ARGS=(
  "execute"
  "--disable-banner"
  "--details=$DETAILS"
  "--details-theme=unicode"
)

run_tests() {
  local launcher_args=("${BASE_LAUNCHER_ARGS[@]}" "$@")
  java "${JVM_ARGS[@]}" -cp "$CLASSPATH" \
    org.junit.platform.console.ConsoleLauncher "${launcher_args[@]}"
}

# Resolves a simple class name to its FQCN from compiled test classes
resolve_fqcn() {
  local cls="$1"
  local fqcn

  fqcn=$(/usr/bin/find "$TEST_DIR" -name "${cls}.class" | head -1 \
    | sed "s|$TEST_DIR/||; s|\.class$||; s|/|.|g")

  if [ -z "$fqcn" ]; then
    echo "Error: could not find test class matching '$cls'" >&2
    exit 1
  fi

  echo "$fqcn"
}

if [ $# -eq 0 ]; then
  # Separate JVM invocations prevent e2e FlatLaf from contaminating unit tests
  run_tests "$SCAN_CLASSPATH" "$UNIT_FILTER"
  UNIT_EXIT=$?

  if [ $UNIT_EXIT -ne 0 ] && [ "$KEEP_GOING" = false ]; then
    exit $UNIT_EXIT
  fi

  run_tests "$SCAN_CLASSPATH" "$E2E_FILTER"
  E2E_EXIT=$?

  if [ $UNIT_EXIT -ne 0 ] || [ $E2E_EXIT -ne 0 ]; then
    exit 1
  fi
elif [[ "$1" == "e2e" ]]; then
  run_tests "$SCAN_CLASSPATH" "$E2E_FILTER"
elif [[ "$1" == "unit" ]]; then
  run_tests "$SCAN_CLASSPATH" "$UNIT_FILTER"
elif [[ "$1" == -Dtest=* ]]; then
  PATTERN="${1#-Dtest=}"
  PATTERN="${PATTERN//\*/.*}"
  run_tests "$SCAN_CLASSPATH" "--include-classname=$PATTERN"
else
  EXTRA_ARGS=()
  HAS_METHOD_SELECT=false

  for arg in "$@"; do
    if [[ "$arg" == *"."* ]]; then
      cls="${arg%%.*}"
      method="${arg#*.}"
      fqcn=$(resolve_fqcn "$cls")
      EXTRA_ARGS+=("--select-method=${fqcn}#${method}")
      HAS_METHOD_SELECT=true
    else
      EXTRA_ARGS+=("--include-classname=.*$arg.*")
    fi
  done

  if [ "$HAS_METHOD_SELECT" = false ]; then
    EXTRA_ARGS+=("$SCAN_CLASSPATH")
  fi

  run_tests "${EXTRA_ARGS[@]}"
fi
