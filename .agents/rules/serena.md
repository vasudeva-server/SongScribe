## Serena Tool Usage (CRITICAL - READ THIS FIRST)

**MANDATORY: Always use Serena tools for Java code exploration and refactoring.**

The Serena MCP server provides semantic code understanding tools that are far more efficient and accurate than text-based searches. You MUST use these tools for code exploration and all refactoring operations (rename, move, delete, inline).

### When to Use Serena Tools

**Finding callers/usages of a method:**
- ❌ DON'T: `Grep` or `search_for_pattern` for method/class names
- ✅ DO: `jet_brains_find_referencing_symbols` with the symbol's name path

**Example:** Finding all callers of `Composition.isEmpty()`:
```
jet_brains_find_referencing_symbols(name_path="Composition/isEmpty", relative_path="src/main/java/songscribe/music/Composition.java")
```

**Finding a declaration (go-to-definition):**
- ❌ DON'T: `Grep` for the class or method name
- ✅ DO: `jet_brains_find_declaration` with a regex that identifies the usage site

**Example:** Finding where `process` is declared when called as `obj.process()`:
```
jet_brains_find_declaration(relative_path="src/…/Foo.java", regex="obj\.(process)\(\)")
```

**Finding implementations of an interface or abstract method:**
- ❌ DON'T: `Grep` for class names or `implements` keyword
- ✅ DO: `jet_brains_find_implementations`

**Example:** Finding all implementations of `Renderer.render()`:
```
jet_brains_find_implementations(name_path="Renderer/render", relative_path="src/main/java/songscribe/ui/renderer/Renderer.java")
```

**Exploring type hierarchy (supertypes / subtypes):**
- ❌ DON'T: `Grep` for `extends` or `implements`
- ✅ DO: `jet_brains_type_hierarchy`

Parameters: `hierarchy_type="super"|"sub"|"both"` (default `"sub"`), `depth` (default 1; use `0` for unlimited).

**Example:** Get all subtypes of `StaffElement`:
```
jet_brains_type_hierarchy(name_path="StaffElement", relative_path="src/main/java/songscribe/music/StaffElement.java", hierarchy_type="sub")
```

**Exploring code structure:**
- ❌ DON'T: `Read` entire files to see what's in them
- ✅ DO: `jet_brains_get_symbols_overview` to see class/method structure first

**Example:** Understanding what's in a class:
```
jet_brains_get_symbols_overview(relative_path="src/main/java/songscribe/ui/action/UIAction.java", depth=2)
```

**Reading specific symbols:**
- ❌ DON'T: `Read` whole file then scroll to find the method
- ✅ DO: `jet_brains_find_symbol` with `include_body=true`

Key parameters:
- `include_body=true` — include full source. Use `include_info=true` instead when you only need the signature/docstring.
- `search_deps=true` — also search project dependencies (prefer this over web search when analyzing third-party APIs).
- `max_matches=1` — when you know there is exactly one result.

**Example:** Reading a specific method:
```
jet_brains_find_symbol(name_path_pattern="UIAction/updateEnabledState", include_body=true)
```

**Example:** Checking a library method signature without reading its body:
```
jet_brains_find_symbol(name_path_pattern="Graphics2D/drawString", include_info=true, search_deps=true)
```

**Renaming symbols, files, or directories:**
- ❌ DON'T: Use `Edit` tool to manually rename
- ✅ DO: `jet_brains_rename` (handles all references automatically)

Optional: pass `rename_in_comments=true` and/or `rename_in_text_occurrences=true` to also rename in comments and string literals (best-effort; verify afterwards if the name is non-unique).

**Example:** Renaming `getBar` to `getMeasure` in `Line.java`:
```
jet_brains_rename(relative_path="src/main/java/songscribe/music/Line.java", name_path="Line/getBar", new_name="getMeasure")
```

**Moving symbols, files, or directories:**
- ❌ DON'T: Use file system operations or `Edit` to move code
- ✅ DO: `jet_brains_move` (updates all references automatically)

Two forms:
- **File or directory move:** omit `name_path`; set `target_relative_path` to the destination directory.
- **Symbol move:** provide `name_path` for the symbol to move. Use `target_relative_path` to move it to the top level of a target file/directory, or use `target_parent_name_path` (with `target_relative_path`) to move it inside a specific parent symbol (e.g., into a different class).

**Example:** Moving class `BeatChangeRenderer` to a different package directory:
```
jet_brains_move(relative_path="src/main/java/songscribe/ui/renderer/BeatChangeRenderer.java", target_relative_path="src/main/java/songscribe/ui/component/")
```

**Example:** Moving method `helper` from `Foo` into class `Bar`:
```
jet_brains_move(relative_path="src/main/java/songscribe/Foo.java", name_path="Foo/helper", target_relative_path="src/main/java/songscribe/Bar.java", target_parent_name_path="Bar")
```

**Deleting symbols, files, or directories:**
- ❌ DON'T: Delete via file system or `Edit` without checking usages
- ✅ DO: `jet_brains_safe_delete` (checks for usages before deleting; use `delete_even_if_used=true` only when intentional)

Optional: `propagate=true` cascades the deletion — also removes call sites and any symbols that become unused as a result. Powerful for deep cleanup; use with care and verify results.

**Example:** Safely deleting an unused helper method:
```
jet_brains_safe_delete(relative_path="src/main/java/songscribe/music/Line.java", name_path="Line/unusedHelper")
```

**Inlining a method (replacing all call sites with its body):**
- ❌ DON'T: Manually edit each call site
- ✅ DO: `jet_brains_inline_symbol`

Optional: `keep_definition=true` retains the original method definition after all call sites are replaced (useful when the method is part of a public API).

**Example:** Inlining `computeFoo()` into all its callers:
```
jet_brains_inline_symbol(relative_path="src/main/java/songscribe/music/Line.java", name_path="Line/computeFoo")
```

**Editing whole methods/classes:**
- ❌ DON'T: Use `replace_symbol_body`, `insert_after_symbol`, or `insert_before_symbol` — these bypass the diff view, so the user cannot see what changed
- ✅ DO: Use `Edit` tool for all code edits, including entire method bodies (produces a visible diff)

**Finding code when you don't know exact names:**
- ❌ DON'T: `Grep` for keywords as first step
- ✅ DO: `search_for_pattern` (semantic search across codebase)

### Standard Workflow Pattern

1. **Overview** → Get symbols in a file: `jet_brains_get_symbols_overview`
2. **Find** → Locate specific symbol: `jet_brains_find_symbol`
3. **Read** → Read body if needed: `include_body=true` or `Read` for non-code content
4. **References** → Understand usage: `jet_brains_find_referencing_symbols`
5. **Hierarchy** → Explore supertypes/subtypes: `jet_brains_type_hierarchy`
6. **Implementations** → Find concrete implementors: `jet_brains_find_implementations`
7. **Edit** → Modify code: Use `Edit` tool (provides visible diff)
8. **Refactor** → Rename/move/delete/inline: `jet_brains_rename`, `jet_brains_move`, `jet_brains_safe_delete`, `jet_brains_inline_symbol`

### When NOT to Use Serena

- Reading non-code files (markdown, config files, etc.)
- Editing code (use `Edit` tool so user can see the diff)

### Key Principle

**Always start with semantic tools, fall back to text tools only when necessary.**
