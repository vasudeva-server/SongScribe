## Serena Tool Usage (CRITICAL - READ THIS FIRST)

**MANDATORY: Always use Serena tools for Java/Kotlin code exploration and symbol renaming.**

The Serena MCP server provides semantic code understanding tools that are far more efficient and accurate than text-based searches. You MUST use these tools for code exploration and refactoring operations like renaming.

### When to Use Serena Tools

**Finding callers/usages of a method:**
- ❌ DON'T: `Grep` or `search_for_pattern` for method/class names
- ✅ DO: `jet_brains_find_referencing_symbols` with the symbol's name path (finds all places where a method is called)

**Example:** Finding all callers of `Composition.isEmpty()`:
```
jet_brains_find_referencing_symbols(name_path="Composition/isEmpty", relative_path="src/main/java/songscribe/music/Composition.java")
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

**Example:** Reading a specific method:
```
jet_brains_find_symbol(name_path_pattern="UIAction/updateEnabledState", include_body=true)
```

**Renaming symbols:**
- ❌ DON'T: Use `Edit` tool to manually rename
- ✅ DO: `mcp__serena__rename_symbol` (handles all references automatically)
- Note: This is the exception - use Serena for renaming because it updates all references atomically

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
5. **Edit** → Modify code: Use `Edit` tool (provides visible diff)

### When NOT to Use Serena

- Reading non-code files (markdown, config files, etc.)
- Editing code (use `Edit` tool so user can see the diff)

### Key Principle

**Always start with semantic tools, fall back to text tools only when necessary.**
