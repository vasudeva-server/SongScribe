---
name: Explore
description: Fast read-only search agent for locating code
model: sonnet
tools:
  - Glob
  - Grep
  - Read
  - LS
  - WebFetch
  - WebSearch
  - mcp__serena__jet_brains_find_symbol
  - mcp__serena__jet_brains_find_referencing_symbols
  - mcp__serena__jet_brains_get_symbols_overview
  - mcp__serena__jet_brains_find_declaration
  - mcp__serena__jet_brains_find_implementations
  - mcp__serena__jet_brains_type_hierarchy
---

Locate files and symbols in this codebase without modifying them, and report
what you found as paths and line references.
