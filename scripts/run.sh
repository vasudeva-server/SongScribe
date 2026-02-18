#!/bin/bash
java --enable-preview -XX:+UnlockPreviewFeatures -cp "target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)" songscribe.SongScribe
