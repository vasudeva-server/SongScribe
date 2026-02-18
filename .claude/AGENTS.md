# Instructions for Sub-Agents

This file contains instructions specifically for sub-agents spawned via the Task tool. These instructions supplement the main CLAUDE.md configuration.

## Tool Usage - READ THIS FIRST

### PRIMARY TOOL REQUIREMENT: Start with Serena

**BEFORE using Grep, Glob, Search, or Read on Java/Kotlin files:**

1. **FIRST** use Serena semantic tools:
   - `jet_brains_get_symbols_overview` - get file/class structure
   - `jet_brains_find_symbol` - locate specific symbols by name path
   - `jet_brains_find_referencing_symbols` - find where symbols are used
   - `search_for_pattern` - search when exact names unknown

2. **ONLY THEN** use Grep/Glob/Search/Read if:
   - You need non-code files (markdown, XML, properties)
   - Serena search returned no results
   - You need file content around multiple symbols

### Why This Matters

Using text-based search tools (Grep/Search) on Java/Kotlin code wastes tokens and misses context. Serena provides semantic understanding that text search cannot match.

**❌ WRONG - wasteful and incomplete:**
```
Grep pattern="class Composition"
Read file_path="src/main/java/songscribe/music/Composition.java"
```

**✅ CORRECT - efficient and complete:**
```
jet_brains_get_symbols_overview(relative_path="src/main/java/songscribe/music/Composition.java", depth=2)
jet_brains_find_symbol(name_path_pattern="Composition/isEmpty", include_body=true)
```

### Complete Workflow

See the complete guide: [Serena Tool Usage](./rules/serena.md)

## Context Files

All sub-agents should also follow these project guidelines:
- [Git Conventions](./rules/git.md)
- [Development Guide](./rules/development.md)
