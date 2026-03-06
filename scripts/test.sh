#!/usr/bin/env bash
# Run Maven tests with specified test class/pattern
# Usage: ./scripts/test.sh [--debug] [e2e|unit|test-pattern]
# Examples:
#   ./scripts/test.sh                     # Run all tests
#   ./scripts/test.sh e2e                 # Run only e2e tests
#   ./scripts/test.sh unit                # Run only unit tests (excludes e2e)
#   ./scripts/test.sh --debug e2e         # Run e2e tests, pausing between each test
#   ./scripts/test.sh SMuFLMetadataTest   # Run specific test class
#   ./scripts/test.sh -Dtest=*Test        # Run with Maven pattern

source "$(dirname "$0")/set-java-home.sh"

DEBUG_FLAG=""
if [[ "$1" == "--debug" ]]; then
    DEBUG_FLAG="-De2e.debug=true"
    shift
fi

if [ $# -eq 0 ]; then
    mvn test $DEBUG_FLAG
elif [[ "$1" == "e2e" ]]; then
    mvn test -Pe2e $DEBUG_FLAG
elif [[ "$1" == "unit" ]]; then
    mvn test -Punit $DEBUG_FLAG
elif [[ "$1" == -D* ]]; then
    mvn test "$@" $DEBUG_FLAG
else
    mvn test -Dtest="$@" $DEBUG_FLAG
fi
