MANDATORY: Use `serena` `jet_brains_*` tools for all Java exploration and refactoring. `rg`, `grep`, `Read` and `Glob` are the fallbacks — reach for them only when a `jet_brains_*` tool returns no results, the file is not Java, or the IDE connection is unavailable.

Whenever you are about to determine the set of places a symbol is used — method, field, or class — start with `jet_brains_find_referencing_symbols`.

Use `Edit` for code changes so the user sees a diff. Do not use `replace_symbol_body`, `insert_after_symbol` or `insert_before_symbol`. To get exact text for an `Edit`, read it with `find_symbol` and `include_body=true`.

For name-path syntax, per-tool parameters, the constructor-body workaround, and the refactoring tool catalog: [Serena Reference](../guides/serena-reference.md).
