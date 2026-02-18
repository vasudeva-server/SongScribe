# Instructions for Sub-Agents

This file contains instructions specifically for sub-agents spawned via the Task tool. These instructions supplement the main CLAUDE.md configuration.

## Tool Usage

### Serena Semantic Tools (MANDATORY)

**For Java/Kotlin code exploration, you MUST use Serena semantic tools.**

See the complete guide: [Serena Tool Usage](./rules/serena.md)

**Quick reference:**
- `jet_brains_find_symbol` - locate specific symbols
- `jet_brains_get_symbols_overview` - understand file structure
- `jet_brains_find_referencing_symbols` - find usages
- `search_for_pattern` - semantic search when names unknown
- `mcp__serena__rename_symbol` - rename symbols (updates all references)

**Only fall back to Grep/Glob for:**
- Non-code files (markdown, config, etc.)
- When Serena tools fail or are unavailable

## Context Files

All sub-agents should also follow these project guidelines:
- [Git Conventions](./rules/git.md)
- [Development Guide](./rules/development.md)
