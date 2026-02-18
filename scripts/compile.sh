#!/usr/bin/env bash
JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home) mvn compiler:compile kotlin:compile -q
