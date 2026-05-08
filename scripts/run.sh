#!/usr/bin/env bash
#
# Options:
#   --log-level=<level>   Override the log level (trace, debug, info, warn, error)
#   --truncate-log        Delete the log file before starting so the session begins fresh
#
# Any other arguments are passed through to the application.
# To enable UI debug features (FlatLaf inspector, debug drawing): DEBUG=1 ./scripts/run.sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

source "$SCRIPT_DIR/set-java-home.sh"
java_args=()

for arg in "$@"; do
  case "$arg" in
    --truncate-log) export TRUNCATE_LOG=1 ;;
    --log-level=*) export LOG_LEVEL="${arg#--log-level=}" ;;
    *) java_args+=("$arg") ;;
  esac
done

java --enable-native-access=ALL-UNNAMED \
  -XX:+UseZGC \
  -Djna.library.path="$PROJECT_DIR/build/native" \
  -cp "$("$SCRIPT_DIR/../gradlew" -q printClasspath --project-dir "$PROJECT_DIR")" \
  songscribe.SongScribe "${java_args[@]}"
