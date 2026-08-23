#!/usr/bin/env bash
#
# Regenerate the synthetic half of the MusicXML losslessness corpus
# (src/test/resources/corpus/synthetic/) by running the generateCorpus Gradle
# task, then verify the whole corpus (synthetic + real) still round-trips
# losslessly through the gate.
#
# The real half (corpus/real/) is committed copies of examples/*.mssw and is not
# touched here.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SYNTHETIC_DIR="$PROJECT_DIR/src/test/resources/corpus/synthetic"

source "$SCRIPT_DIR/set-java-home.sh"
"$PROJECT_DIR/gradlew" -q generateCorpus

shopt -s nullglob
files=("$SYNTHETIC_DIR"/*.mssw)
echo "Generated ${#files[@]} synthetic corpus files in $SYNTHETIC_DIR"
