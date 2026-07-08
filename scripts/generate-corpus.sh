#!/usr/bin/env bash
#
# Regenerate the synthetic half of the MusicXML losslessness corpus
# (src/test/resources/corpus/synthetic/) by running the guarded
# MusicXmlCorpusGenerator test with -Dcorpus.generate=true, then verify the whole
# corpus (synthetic + real) still round-trips losslessly through the gate.
#
# The real half (corpus/real/) is committed copies of examples/*.mssw and is not
# touched here.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SYNTHETIC_DIR="$PROJECT_DIR/src/test/resources/corpus/synthetic"

EXTRA_JVM_ARGS="-Dcorpus.generate=true" "$SCRIPT_DIR/test.sh" MusicXmlCorpusGenerator

shopt -s nullglob
files=("$SYNTHETIC_DIR"/*.mssw)
echo "Generated ${#files[@]} synthetic corpus files in $SYNTHETIC_DIR"

echo "Verifying corpus round-trips losslessly..."
"$SCRIPT_DIR/test.sh" MusicXmlCorpusLosslessnessTest
