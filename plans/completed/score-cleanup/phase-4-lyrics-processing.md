# Implementation Plan: Phase 4 - Extract Lyrics Processing

**Master Plan:** [Score.java Legacy Code Cleanup](score-cleanup.md) | **Phase:** 4

## Overview

Extract lyrics processing methods from Score.java into a new dedicated `LyricsProcessor` utility class, following the same pattern established in Phase 3 (BeamCalculator extraction).

## Current State

Score.java contains three methods totaling ~160 lines that handle parsing lyrics text and assigning syllables to notes:

- `spellLyrics()` (lines 1643-1647) - processes all lines in composition
- `spellLyrics(Line line)` (lines 1649-1779) - processes a single line
- `setSyllableForNextNote()` (lines 1781-1804) - assigns syllable to next valid note

These methods are pure text processing logic with no UI dependencies, making them ideal candidates for extraction.

## Target Architecture

Create: `src/main/java/songscribe/music/LyricsProcessor.java`
- Utility class with static methods (like BeamCalculator)
- Private constructor to prevent instantiation
- JavaDoc comments for public methods
- All three methods extracted with same signatures

Update: `src/main/java/songscribe/ui/component/Score.java`
- Remove the three methods
- Add import for LyricsProcessor
- Update all 5 call sites to use `LyricsProcessor.spellLyrics()`

## Dependencies

The extracted methods depend on:
- `songscribe.music.Composition` - read lyrics and line count
- `songscribe.music.Line` - read/write note syllables and beginRelation
- `songscribe.music.Note.SyllableRelation` - enum for syllable relationships
- `songscribe.ui.Constants.UNDERSCORE` - string constant

## Implementation Steps

### 1. Create LyricsProcessor.java

Create new file at `src/main/java/songscribe/music/LyricsProcessor.java`:

```java
/*
 * This file is part of SongScribe.
 *
 * SongScribe is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with SongScribe.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2024 Aparajita
 */

package songscribe.music;

import org.jetbrains.annotations.NotNull;
import songscribe.ui.Constants;

/**
 * Processes lyrics text and assigns syllables to notes.
 *
 * This class handles parsing lyrics strings and mapping syllables to musical notes,
 * respecting syllable continuation markers (hyphens) and extenders (underscores).
 */
public class LyricsProcessor {

    private LyricsProcessor() {
        // Utility class
    }

    // Copy spellLyrics() method here with JavaDoc
    // Copy spellLyrics(Line) method here with JavaDoc
    // Copy setSyllableForNextNote() method here with JavaDoc (keep private)
}
```

### 2. Extract Methods to LyricsProcessor

Move the three methods from Score.java (lines 1643-1804) to LyricsProcessor:

- Make `spellLyrics()` public static
- Make `spellLyrics(Line line)` public static
- Keep `setSyllableForNextNote()` private static

Add JavaDoc to public methods:
- `spellLyrics()`: "Processes lyrics for all lines in a composition."
- `spellLyrics(Line line)`: "Processes lyrics for a single line, parsing the lyrics string and assigning syllables to notes."

### 3. Update Score.java

Remove methods:
- Delete lines 1643-1804 (all three methods)

Add import:
```java
import songscribe.music.LyricsProcessor;
```

Update 5 call sites to use LyricsProcessor:
1. Line 1017: `spellLyrics(line);` → `LyricsProcessor.spellLyrics(line);`
2. Line 1408: `spellLyrics(line);` → `LyricsProcessor.spellLyrics(line);`
3. Line 1629: `spellLyrics();` → `LyricsProcessor.spellLyrics(composition);`
4. Line 2624: `spellLyrics(line);` → `LyricsProcessor.spellLyrics(line);`
5. Line 2627: `spellLyrics();` → `LyricsProcessor.spellLyrics(composition);`

Note: The no-argument `spellLyrics()` calls need the composition passed explicitly.

### 4. Adjust Method Signatures

Since `spellLyrics()` currently accesses `this.composition`, we need to change its signature:

```java
// Before (in Score.java):
public void spellLyrics() {
    for (var l = 0; l < composition.lineCount(); l++) {
        spellLyrics(composition.getLine(l));
    }
}

// After (in LyricsProcessor.java):
public static void spellLyrics(@NotNull Composition composition) {
    for (var l = 0; l < composition.lineCount(); l++) {
        spellLyrics(composition.getLine(l));
    }
}
```

## Critical Files

- `/Users/aparajita/Developer/projects/SongScribe/src/main/java/songscribe/music/LyricsProcessor.java` (create)
- `/Users/aparajita/Developer/projects/SongScribe/src/main/java/songscribe/ui/component/Score.java` (update)

## Verification

After implementation:

1. Compile the code:
   ```bash
   ./scripts/compile.sh
   ```

2. Verify no compilation errors

3. Run the application:
   ```bash
   ./scripts/run-debug.sh
   ```

4. Test lyrics functionality:
   - Open or create a score with lyrics
   - Edit lyrics in lyrics mode
   - Verify syllables are correctly assigned to notes
   - Test hyphenation (single dash, double dash)
   - Test underscore extenders
   - Verify lyrics under rests toggle works
   - Insert/delete notes and verify lyrics update correctly

5. Check debug output if DEBUG env var is set (lyrics processing logs should still appear)

## Expected Results

- Score.java reduced by ~162 lines
- New LyricsProcessor.java containing ~170 lines (including header and JavaDoc)
- No functional changes - all lyrics processing works identically
- Clear separation: Score handles UI coordination, LyricsProcessor handles text parsing logic
