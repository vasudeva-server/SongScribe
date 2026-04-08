#!/usr/bin/env bash
#
# Options:
#   --log-level=<level>   Override the log level (trace, debug, info, warn, error)
#   --truncate-log        Delete the log file before starting so the session begins fresh
#
# Any other arguments are passed through to the application.
# To enable UI debug features (FlatLaf inspector, debug drawing): DEBUG=1 ./scripts/run.sh

source "$(dirname "$0")/set-java-home.sh"
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
  -Djna.library.path=target/native \
  -cp "target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)" \
  songscribe.SongScribe "${java_args[@]}"
