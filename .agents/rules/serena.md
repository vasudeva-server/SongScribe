MANDATORY: Use `serena` `jet_brains_*` tools for all Java exploration and refactoring. Fall back to Grep/Read/Glob only when a `jet_brains_*` tool returns no results, the file is not Java, or the IDE connection is unavailable.

Finding callers/usages is covered by this rule: any "who calls X", "where is X used", "find references to X" task MUST start with `jet_brains_find_referencing_symbols`, never `grep`/`rg`.

When spawning a fresh subagent (with `subagent_type`), add this at top of its prompt:

> MANDATORY: Read .agents/rules/serena.md

## Name paths

Symbols are addressed by **name path** + **relative_path** (the file).

- `MyClass` — the class
- `MyClass/myMethod` — method within class (relative suffix match)
- `/MyClass/myMethod` — leading `/` forces exact full-path match
- `MyClass/myMethod[0]` — append `[i]` (0-based) to pick a specific overload

## Exploration — pick the narrowest tool

- **`jet_brains_get_symbols_overview`** — top-level symbols of a file. Run this first when entering an unfamiliar file. Use `depth=1` to also list class members.
- **`jet_brains_find_symbol`** — locate a symbol by name path across the codebase (or one file via `relative_path`).
  - `depth=1` to list children (e.g. a class's methods) without bodies.
  - `include_body=true` only when you actually need the source — read symbol-by-symbol, not whole files.
  - `include_info=true` for signature/docstring without the body.
  - **To find constructor signatures:** use `name_path_pattern=ClassName` with `depth=1` (lists all constructors as `ClassName[0]`, `ClassName[1]`, etc.) and `include_info=true`. Do NOT use `include_body=true` for constructors — it does not return the body reliably. To read a constructor body, use `rg` to find its line number, then `Read` with `offset` and `limit` (see **Reading constructor bodies** below).
  - `max_matches=1` when expecting a unique symbol; raise it to refine a noisy search.
  - `search_deps=true` to inspect third-party/library code — prefer this over web search. Pass the returned `<ext...>` identifier as `relative_path` for follow-up queries.
- **`jet_brains_find_declaration`** — jump from a usage to its declaration. Takes a `regex` with one capture group around the symbol; include surrounding context so the match is unambiguous.
- **`jet_brains_find_referencing_symbols`** — the first-choice tool whenever you need a symbol's callers/usages (impact list, before a signature change, before deleting), not only "before changing." Requires `name_path` + the defining file. **Never** use `grep`/`rg "foo("` to find callers — it's not type-aware and counts comments/strings as calls. Caveat: it finds *code* references, not mentions in comments/Javadoc/string literals; when a refactor must update those too, run this **first** for the authoritative caller list, then a separate text search **only** for prose mentions.
- **`jet_brains_find_implementations`** — implementations of an interface/abstract method.
- **`jet_brains_type_hierarchy`** — supertypes/subtypes of a class. `hierarchy_type` = `super` | `sub` | `both`; `depth=0` for unlimited.
- **`search_for_pattern`** — use before Grep when the name is unknown.

## Reading constructor bodies

`jet_brains_find_symbol` with `include_body=true` does not return constructor bodies reliably. Use this two-step fallback instead:

1. Find the constructor's line number with `rg`:
   ```
   rg -n "ClassName\s*\(" path/to/ClassName.java
   ```
2. Read the file from that line with enough lines to cover the body:
   ```
   Read(file_path, offset: <line>, limit: 30)
   ```
   Increase `limit` if the body is longer than expected.

## Refactoring — always prefer these over manual edits

These update all references atomically; manual edits do not.

- **`jet_brains_rename`** — rename a symbol, file, or directory. Omit `name_path` to rename a file/dir.
- **`jet_brains_move`** — move a symbol or file/dir; references update automatically. Target is the new *parent* (Java: a directory = package). Never move Java files by hand.
- **`jet_brains_safe_delete`** — delete a symbol/file/dir; reports usages instead of deleting if any exist. `propagate=true` also removes call sites and newly-unused code — use with care. `delete_even_if_used=true` forces it.
- **`jet_brains_inline_symbol`** — inline a method/class into its call sites. `keep_definition=true` to retain the original.

## Editing bodies

Use the `Edit` tool for code changes — it shows the user a diff. Do NOT use `replace_symbol_body`, `insert_after_symbol`, or `insert_before_symbol` (no diff shown). Use `find_symbol` with `include_body=true` to get exact text to feed into `Edit`.
