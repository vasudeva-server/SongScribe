---
description: Run test-remediation chunks as a workflow (up to 5 for a package number, 1 otherwise)
argument-hint: "[optional: package number (e.g. 3), class name, or section to target]"
---

Invoke the Workflow tool with `{name: "remediate", args: "<ARGUMENTS>"}`.

Pass `args` as the raw `$ARGUMENTS` string, or omit it (null) if no argument was given.
