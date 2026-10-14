# Serena `jet_brains_*` Reference

This file covers the Serena `jet_brains_*` tools and holds only what their own schemas do not give.

## Finding a symbol

`jet_brains_find_symbol` accepts a bare name without `relative_path` and returns the defining file,
which `jet_brains_find_referencing_symbols` and `jet_brains_get_symbols_overview` both require:

```
find_symbol("myMethod")                          → the declaring file
find_referencing_symbols("Class/myMethod", file) → every call site
get_symbols_overview(file)                       → what that class contains
```

## Subtypes and supertypes

Use `jet_brains_type_hierarchy` for `extends` and `implements` relationships, not
`jet_brains_find_referencing_symbols`.

## Reading constructor bodies

`jet_brains_find_symbol` with `include_body=true` does not return constructor bodies reliably.
Instead:

1. `rg -n "ClassName\s*\(" path/to/ClassName.java`
2. `Read(file_path, offset: <line>, limit: <lines>)`, raising `limit` for a longer body.

`jet_brains_find_symbol` with `depth=1` and `include_info=true` lists constructor signatures as
`ClassName[0]`, `ClassName[1]`, and so on.

## Looking up a dependency's API

`jet_brains_find_symbol` with `search_deps: true` searches project dependencies, so use it instead
of decompiling a library JAR or searching the web. A result from a dependency has a `relative_path`
that starts with `<ext`; pass that exact identifier to a further lookup inside the dependency, and
never guess a path.
