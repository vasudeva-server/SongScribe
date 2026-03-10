#!/usr/bin/env bash
# Run tests with JUnit Console Launcher (tree-style output)
# Usage: ./scripts/test.sh [--debug] [e2e|unit|test-pattern]
# Examples:
#   ./scripts/test.sh                                        # Run all tests
#   ./scripts/test.sh e2e                                    # Run only e2e tests
#   ./scripts/test.sh unit                                   # Run only unit tests (excludes e2e)
#   ./scripts/test.sh --debug e2e                            # Run e2e tests, pausing between each test
#   ./scripts/test.sh SMuFLMetadataTest                      # Run specific test class (multiple space-separated classes and/or methods allowed)
#   ./scripts/test.sh BeamingTest.testFlipStemDirection      # Run specific test method (multiple space-separated methods and/or classes allowed)
#   ./scripts/test.sh -Dtest=*Test                           # Run with Maven pattern

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

source "$SCRIPT_DIR/set-java-home.sh"

# Parse --debug flag
JVM_ARGS=()
if [[ "$1" == "--debug" ]]; then
    JVM_ARGS+=("-De2e.debug=true")
    shift
fi

# JVM args for module access (from surefire config) and agent loading (Mockito)
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

# Compile test code
echo "Compiling..."
if ! mvn -q test-compile -f "$PROJECT_DIR/pom.xml"; then
    echo "Compilation failed."
    exit 1
fi

# Build classpath
CP_FILE=$(mktemp)
trap 'rm -f "$CP_FILE"' EXIT
mvn -q dependency:build-classpath -f "$PROJECT_DIR/pom.xml" \
    -DincludeScope=test -Dmdep.outputFile="$CP_FILE" || exit 1
CLASSPATH="$PROJECT_DIR/target/classes:$PROJECT_DIR/target/test-classes:$(cat "$CP_FILE")"

# Build console launcher args
TEST_DIR="$PROJECT_DIR/target/test-classes"
LAUNCHER_ARGS=(
    "execute"
    "--disable-banner"
    "--details=tree"
    "--details-theme=unicode"
)

if [ $# -eq 0 ]; then
    LAUNCHER_ARGS+=("--scan-classpath=$TEST_DIR")
elif [[ "$1" == "e2e" ]]; then
    LAUNCHER_ARGS+=("--scan-classpath=$TEST_DIR" "--include-package=songscribe.e2e")
elif [[ "$1" == "unit" ]]; then
    LAUNCHER_ARGS+=("--scan-classpath=$TEST_DIR" "--exclude-package=songscribe.e2e")
elif [[ "$1" == -Dtest=* ]]; then
    # Convert Maven test pattern to classname regex
    PATTERN="${1#-Dtest=}"
    PATTERN="${PATTERN//\*/.*}"
    LAUNCHER_ARGS+=("--scan-classpath=$TEST_DIR" "--include-classname=$PATTERN")
else
    PATTERNS=()
    HAS_METHOD_SELECT=false
    for arg in "$@"; do
        if [[ "$arg" == *"."* ]]; then
            cls="${arg%%.*}"
            method="${arg#*.}"
            # Find the fully qualified class name from compiled test classes
            fqcn=$(find "$TEST_DIR" -name "${cls}.class" | head -1 \
                | sed "s|$TEST_DIR/||; s|\.class$||; s|/|.|g")
            if [ -z "$fqcn" ]; then
                echo "Error: could not find test class matching '$cls'"
                exit 1
            fi
            LAUNCHER_ARGS+=("--select-method=${fqcn}#${method}")
            PATTERNS+=(".*$cls.*")
            HAS_METHOD_SELECT=true
        else
            LAUNCHER_ARGS+=("--include-classname=.*$arg.*")
            PATTERNS+=(".*$arg.*")
        fi
    done
    if [ "$HAS_METHOD_SELECT" = false ]; then
        LAUNCHER_ARGS+=("--scan-classpath=$TEST_DIR")
    fi
fi

# Run tests
java "${JVM_ARGS[@]}" -cp "$CLASSPATH" \
    org.junit.platform.console.ConsoleLauncher "${LAUNCHER_ARGS[@]}"
