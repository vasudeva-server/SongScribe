#!/usr/bin/env bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/set-java-home.sh"

if [[ "$1" == "clean" ]]; then
  "$SCRIPT_DIR/../gradlew" -q clean
fi

if [[ "$1" == "--test" ]]; then
  goal="testClasses"
else
  goal="classes"
fi

if "$SCRIPT_DIR/../gradlew" -q "$goal"; then
  echo "SUCCESS"
else
  echo "FAILURE"
fi
