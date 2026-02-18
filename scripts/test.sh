#!/usr/bin/env bash
# Run Maven tests with specified test class/pattern
# Usage: ./scripts/test.sh [test-pattern]
# Examples:
#   ./scripts/test.sh                     # Run all tests
#   ./scripts/test.sh SMuFLMetadataTest   # Run specific test class
#   ./scripts/test.sh -Dtest=*Test        # Run with Maven pattern

source "$(dirname "$0")/set-java-home.sh"

if [ $# -eq 0 ]; then
    mvn test
else
    # If argument starts with -D, pass it as-is; otherwise wrap with -Dtest=
    if [[ "$1" == -D* ]]; then
        mvn test "$@"
    else
        mvn test -Dtest="$@"
    fi
fi
