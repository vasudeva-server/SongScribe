#!/usr/bin/env bash
# Sets JAVA_HOME to Java 25 with fallback to default JDK
# Usage: source scripts/set-java-home.sh
# Or:    $(scripts/set-java-home.sh)

export JAVA_HOME=$(/usr/libexec/java_home -v 25 2>/dev/null || /usr/libexec/java_home)
