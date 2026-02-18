#!/usr/bin/env bash
source "$(dirname "$0")/set-java-home.sh"
[[ "$1" == "clean" ]] && mvn clean -q
mvn resources:resources kotlin:compile compiler:compile -q
