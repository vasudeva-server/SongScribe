#!/usr/bin/env bash
source "$(dirname "$0")/set-java-home.sh"
[[ "$1" == "clean" ]] && mvn clean -q

if [[ "$1" == "--test" ]]; then
  goals="test-compile"
else
  goals="generate-sources dependency:copy@copy-native-libs resources:resources kotlin:compile compiler:compile"
fi

# shellcheck disable=SC2086
if mvn $goals -q; then
  echo "SUCCESS"
else
  echo "FAILURE"
fi
