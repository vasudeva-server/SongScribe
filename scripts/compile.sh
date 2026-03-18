#!/usr/bin/env bash
source "$(dirname "$0")/set-java-home.sh"
[[ "$1" == "clean" ]] && mvn clean -q

goals="generate-sources resources:resources kotlin:compile compiler:compile"
[[ "$1" == "--test" ]] && goals+=" test-compile"

mvn $goals -q
