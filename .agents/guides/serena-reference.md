# Serena `jet_brains_*` Reference

The `jet_brains_*` schemas load at session start and document their own parameters, name-path syntax and options. This file covers only what they can't: when to reach for them, and where they misbehave.

## Start here — you do not need the file first

`jet_brains_find_symbol`'s `relative_path` is optional. Omit it and it searches the whole project from a bare name, returning the defining file that `find_referencing_symbols` and `get_symbols_overview` both require:

```
find_symbol("myMethod")                          → the declaring file
find_referencing_symbols("Class/myMethod", file) → every call site
get_symbols_overview(file)                       → what that class contains
```

So there is never a reason to `rg` for a symbol's location first.

## Usages queries

For `extends`/`implements` relationships, use `jet_brains_type_hierarchy`. A hook warns if a Bash `rg`/`grep` call looks like a usages query instead; see that hook's message for the reasoning and exceptions (constructor-body reads, prose searches via `PROSE=1`, non-Java files).

## Reading constructor bodies

`find_symbol` with `include_body=true` does not return constructor bodies reliably. Instead:

1. `rg -n "ClassName\s*\(" path/to/ClassName.java`
2. `Read(file_path, offset: <line>, limit: 30)` — raise `limit` for a longer body.

For constructor *signatures*, `find_symbol` with `depth=1` and `include_info=true` lists the overloads as `ClassName[0]`, `ClassName[1]`, and so on.

## Refactoring catalog

Prefer `jet_brains_rename`, `jet_brains_move`, `jet_brains_safe_delete` and `jet_brains_inline_symbol` over manual edits — they update every reference atomically. Never move a Java file by hand.
