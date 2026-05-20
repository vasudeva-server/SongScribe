#!/usr/bin/env bash
# Run tests with JaCoCo coverage instrumentation, then generate the HTML report.
# Usage: ./scripts/coverage.sh [test.sh arguments]
# Examples:
#   ./scripts/coverage.sh          # all tests
#   ./scripts/coverage.sh unit     # unit tests only
#   ./scripts/coverage.sh e2e      # e2e tests only

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

AGENT_JAR="$("$PROJECT_DIR/gradlew" -q printJacocoAgentPath --project-dir "$PROJECT_DIR")"
EXEC_FILE="${PROJECT_DIR}/build/jacoco/test.exec"

# Delete stale exec file so unit + e2e JVMs both append to a fresh file.
rm -f "$EXEC_FILE"

JACOCO_ARG="-javaagent:${AGENT_JAR}=destfile=${EXEC_FILE},append=true"

EXTRA_JVM_ARGS="${JACOCO_ARG}" "$SCRIPT_DIR/test.sh" "$@"

"$PROJECT_DIR/gradlew" -q jacocoTestReport --project-dir "$PROJECT_DIR"

REPORT="${PROJECT_DIR}/build/reports/jacoco/test/html/index.html"
echo ""
echo "Coverage report: $REPORT"
open "$REPORT" 2>/dev/null || true
