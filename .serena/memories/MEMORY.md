# SongScribe Project Memory

## MANDATORY: Read Before Any Java/Kotlin Implementation

**Before planning or implementing any Java/Kotlin changes**, always read:

```
.claude/rules/code-styles/java-kotlin.md
```

This file contains critical rules including no logic duplication, unit suffixes (Ss/Px), `var` usage, file headers, import ordering, nullability annotations, and formatting conventions. Do not skip this step — it is not optional.

## Key Project Rules (Summary)

- Serena tools for all Java/Kotlin code exploration (see `.claude/rules/serena.md`)
- `ScaleContext` is the authoritative pixel/staff-space converter (see `.claude/rules/unit-conversion.md`)
- Always use provided scripts (`./scripts/compile.sh`, `./scripts/run.sh`) — never raw `mvn` or `javac`
- Commit via `/commit-commands:commit` skill, not manual git commands
