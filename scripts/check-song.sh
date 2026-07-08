#!/usr/bin/env bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [[ "$1" == "--quiet" ]]; then
  QUIET=true
  shift
else
  QUIET=false
fi

RESULT=$(/usr/bin/xmllint --noout --nonet --schema "$SCRIPT_DIR"/../docs/musicxml-4.0-schema/musicxml.xsd "$1" 2>&1 >/dev/null)
STATUS=$?

if [[ "$QUIET" == false ]]; then
  echo "$RESULT"
fi

exit $STATUS
