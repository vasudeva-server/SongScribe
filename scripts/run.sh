#!/usr/bin/env bash
java --enable-native-access=ALL-UNNAMED -cp "target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)" songscribe.SongScribe
