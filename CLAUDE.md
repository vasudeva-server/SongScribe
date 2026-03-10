# SongScribe Project Configuration

## Overview

SongScribe is a Java/Kotlin-based music notation application. Code style should emphasize clarity, maintainability, and consistency with the existing codebase patterns.

## Tool Usage

For semantic code exploration and refactoring, see [Serena Tool Usage](./.claude/rules/serena.md).

When you need API documentation for Java, Kotlin, or any third-party library (FlatLaf, Jackson, etc.), use context7 MCP tools instead of web search.

## Writing Tests

Before writing tests, read the appropriate guide (these are NOT auto-loaded):

- **Common conventions:** [testing-common.md](./.claude/testing-common.md)
- **Unit tests:** [testing-unit.md](./.claude/testing-unit.md) — mocking patterns, ReflectionTestHelper, MainFrame singleton mocking
- **E2E tests:** [testing-e2e.md](./.claude/testing-e2e.md) — user simulation helpers, coordinate conversion, layout sync

## SMuFL Reference

To look up SMuFL glyph names, codepoints, or ranges, use:
`https://w3c.github.io/smufl/latest/tables/common-ornaments.html?search=<search terms>`
