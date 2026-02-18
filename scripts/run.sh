#!/usr/bin/env bash
java -cp "target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)" songscribe.SongScribe
