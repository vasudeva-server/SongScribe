#!/usr/bin/env bash
#
# Options:
#   --log-level=<level>   Override the log level (trace, debug, info, warn, error)
#   --truncate-log        Delete the log file before starting so the session begins fresh
#   --profile             Attach async-profiler in wall-clock mode; the flame graph
#                         is written to build/songscribe-profile.html when you quit
#                         (requires: brew install async-profiler)
#   -f <file>             Open <file> on startup
#
# Any other arguments are passed through to the application.
# To enable UI debug features (FlatLaf inspector, debug drawing): DEBUG=1 ./scripts/run.sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

source "$SCRIPT_DIR/set-java-home.sh"
java_args=()
jvm_args=()
file_arg=""

while [[ $# -gt 0 ]]; do
  arg="$1"

  case "$arg" in
    --truncate-log) export TRUNCATE_LOG=1 ;;
    --log-level=*) export LOG_LEVEL="${arg#--log-level=}" ;;
    -f)
      shift
      file_arg="$1"
      ;;
    --profile)
      profiler_lib="$(brew --prefix async-profiler 2>/dev/null)/lib/libasyncProfiler.dylib"

      if [[ ! -f "$profiler_lib" ]]; then
        echo "error: async-profiler not found. Install it with: brew install async-profiler" >&2
        exit 1
      fi

      profile_output="$PROJECT_DIR/build/songscribe-profile.html"
      # Attach to the app JVM only — not the gradlew classpath subprocess below.
      jvm_args+=("-agentpath:$profiler_lib=start,event=wall,interval=1ms,file=$profile_output,title=SongScribe")
      echo "Profiling (wall-clock); flame graph written to $profile_output on quit"
      ;;
    *) java_args+=("$arg") ;;
  esac

  shift
done

if [[ -n "$file_arg" ]]; then
  # SongScribe.main treats a lone non-converter positional argument as a file
  # to open on startup, so it must be args[0] — ahead of any other pass-through args.
  java_args=("$file_arg" "${java_args[@]}")
fi

java --enable-native-access=ALL-UNNAMED \
  --add-opens java.desktop/com.apple.eawt.event=ALL-UNNAMED \
  -XX:+UseZGC \
  "${jvm_args[@]}" \
  -cp "$("$SCRIPT_DIR/../gradlew" -q printClasspath --project-dir "$PROJECT_DIR")" \
  songscribe.SongScribe "${java_args[@]}"
