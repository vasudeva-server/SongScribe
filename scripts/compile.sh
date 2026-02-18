#!/usr/bin/env bash
source "$(dirname "$0")/set-java-home.sh"
mvn kotlin:compile compiler:compile -q
