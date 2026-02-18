# Phase 2: Horizontal Layout Implementation Plan

**Type:** Sub-plan
**Parent:** [line-engraving-rewrite.md](./line-engraving-rewrite.md) → Phase 2
**Created:** 2026-02-01
**Pre-planned:** No
**Status:** Completed
**Completed:** 2026-02-01

---

## Overview

Implement the `HorizontalSpacingCalculator` to calculate X positions for note columns following Gould/Ross principles of non-proportional, lyric-driven spacing.

## Phase 1 Foundation (Completed)

Phase 1 successfully created:
- `NoteColumn` - Immutable data class with note, extents, syllables, beam groups
- `LayoutConstants` - All spacing constants in MU (FIRST_NOTE_OFFSET=11.5, MIN_COLUMN_GAP=0.25, MIN_SYLLABLE_GAP=0.5, etc.)
- `NoteColumnBuilder` - Constructs columns from Line, measures syllable widths with FontMetrics

All Phase 1 files are well-structured and ready for Phase 2 integration.

## Phase 2 Requirements

Create `HorizontalSpacingCalculator` implementing:
1. Clef/key signature width measurement
2. First note positioning rule (11.5 MU from clef/key signature right extent)
3. Column-to-column spacing driven by lyric syllable widths
4. Accidental clearance rule (0.25 MU minimum)
5. Special beam group handling (tight internal spacing unless lyrics force expansion)

## Implementation Design

### File to Create

**src/main/java/songscribe/ui/layout2/HorizontalSpacingCalculator.java**

### Class Structure

```java
public class HorizontalSpacingCalculator {

    // Constructor
    public HorizontalSpacingCalculator() {}

    // Main entry point
    public void calculatePositions(List<NoteColumn> columns, Line line) {
        // Calculate first note position
        // Calculate subsequent column positions
    }

    // Helper methods
    private double calculateClefWidth()
    private double calculateKeySignatureWidth(Line line)
    private double calculateFirstNoteX(Line line)
    private double calculateMinimumColumnSpacing(NoteColumn prev, NoteColumn curr)
    private double calculateLyricSpacing(NoteColumn prev, NoteColumn curr)
    private boolean needsAccidentalPush(NoteColumn prev, NoteColumn curr, double spacing)
    private void handleBeamGroup(List<NoteColumn> columns, int startIndex, int endIndex)
}
```

### Algorithm Implementation

Following the plan document (lines 88-117):

#### 1. First Note Positioning

```
firstNoteX = clefWidth + keySignatureWidth + FIRST_NOTE_OFFSET(11.5 MU)

Where:
- clefWidth = 28.0 pixels (treble clef, always)
- keySignatureWidth = line.getKeyAccidentalCount() * 8.0 pixels
- FIRST_NOTE_OFFSET = 11.5 MU = 46 pixels
```

#### 2. Column-to-Column Spacing

For each subsequent column:

```
a. Calculate minimum spacing:
   minimumSpacing = previousColumn.getRightExtent() + MIN_COLUMN_GAP

b. Calculate lyric spacing requirement:
   lyricSpacing = (previousSyllableWidth / 2) + MIN_SYLLABLE_GAP + (currentSyllableWidth / 2)

c. Take maximum:
   requiredSpacing = max(minimumSpacing, lyricSpacing)

d. Check accidental clearance:
   if current note has accidental:
       accidentalLeftEdge = currentX + currentColumn.getLeftExtent()
       if previousColumn.getRightEdgeX() + ACCIDENTAL_CLEARANCE > accidentalLeftEdge:
           push currentX right to maintain ACCIDENTAL_CLEARANCE

e. Set position:
   currentColumn.setX(currentX)
```

#### 3. Beam Group Handling

Beam groups require special treatment (plan lines 112-117):

```
For each beam group:
   1. First pass: calculate tight internal spacing (ignore lyrics)
      - Use BEAM_GROUP_MIN_INTERNAL_GAP between beamed notes

   2. Check if lyrics under beam group require expansion:
      - Calculate total lyric width requirement
      - Compare with tight spacing total

   3. If lyrics need more space:
      - Calculate expansion needed
      - Distribute expansion evenly across beam group columns

   4. Maintain BEAM_GROUP_EXTERNAL_GAP from adjacent elements
```

### Beam Group Detection Strategy

Two approaches to consider:

**Option A: Single-pass with lookahead**
- Process columns sequentially
- When encountering first beamed note, lookahead to find group end
- Calculate beam group spacing in-place
- Continue from after beam group

**Option B: Pre-processing**
- First pass: identify all beam groups and mark column ranges
- Second pass: apply spacing with beam group awareness
- Simpler logic, clearer separation of concerns

**Recommendation:** Option B for clarity and maintainability.

### Constants to Use

From `LayoutConstants`:
- `FIRST_NOTE_OFFSET = 11.5` MU
- `MIN_COLUMN_GAP = 0.25` MU
- `MIN_SYLLABLE_GAP = 0.5` MU
- `ACCIDENTAL_CLEARANCE = 0.25` MU
- `BEAM_GROUP_MIN_INTERNAL_GAP = 3.0` MU
- `BEAM_GROUP_EXTERNAL_GAP = 1.0` MU

Convert MU to pixels using `LayoutConstants.px(mu)`.

### Edge Cases to Handle

1. **Empty line** - Return immediately
2. **Single column** - Only apply first note positioning
3. **Columns without syllables** - Use minimum spacing only
4. **Beam group at start** - First note rules still apply to first beamed note
5. **Beam group at end** - No special handling needed
6. **Adjacent beam groups** - Ensure BEAM_GROUP_EXTERNAL_GAP between them
7. **Rests in beam groups** - Rests don't beam, but may appear between beam groups

### Testing Strategy

After implementation, verify with existing Line instances:

1. **Basic spacing**: Create line with evenly-spaced notes, no lyrics
   - Verify MIN_COLUMN_GAP is maintained
   - Verify first note offset is correct

2. **Lyric-driven spacing**: Line with varying syllable widths
   - Long syllables should push notes apart
   - Short syllables should use minimum spacing

3. **Accidental clearance**: Notes with accidentals after tight spacing
   - Verify accidentals don't collide with previous notes

4. **Beam groups**: Line with beamed eighth notes
   - Verify tight internal spacing
   - Verify expansion when lyrics require it

5. **Mixed content**: Line with rests, beamed notes, accidentals, lyrics
   - End-to-end verification of all rules working together

## Critical Files

**To create:**
- `src/main/java/songscribe/ui/layout2/HorizontalSpacingCalculator.java`

**To reference (read-only):**
- `src/main/java/songscribe/ui/layout2/NoteColumn.java` - Data structure we're positioning
- `src/main/java/songscribe/ui/layout2/LayoutConstants.java` - Spacing constants
- `src/main/java/songscribe/ui/layout2/NoteColumnBuilder.java` - How columns are constructed
- `src/main/java/songscribe/music/Line.java` - Source of key signature info
- `src/main/java/songscribe/ui/layout/BeamGroup.java` - Beam group structure

## Unit Testing

### Test File to Create

**src/test/java/songscribe/ui/layout2/HorizontalSpacingCalculatorTest.java**

### Testing Framework

- **JUnit 5** (Jupiter) - matches existing test structure
- Uses `@DisplayName` for readable test descriptions
- Uses `@Nested` classes to group related test scenarios
- Uses `@BeforeEach` for test setup

### Test Structure

```java
@DisplayName("HorizontalSpacingCalculator")
class HorizontalSpacingCalculatorTest {

    private HorizontalSpacingCalculator calculator;
    private Graphics2D g2;
    private Font lyricsFont;
    private Line line;

    @BeforeEach
    void setUp() {
        // Create calculator
        // Create mock Graphics2D and Font for NoteColumnBuilder
        // Create test Line
    }

    @Nested
    @DisplayName("calculatePositions")
    class CalculatePositions {
        // Test cases...
    }
}
```

### Test Cases to Implement

#### 1. Empty Line Tests
```java
@Test
@DisplayName("handles empty column list")
void emptyColumnList() {
    // Given: empty list
    // When: calculatePositions called
    // Then: no exception thrown
}
```

#### 2. Single Column Tests
```java
@Test
@DisplayName("positions single note at correct first note offset")
void singleNote() {
    // Given: line with 1 note, no key signature
    // When: calculatePositions called
    // Then: note X = clefWidth + 11.5 MU
}

@Test
@DisplayName("accounts for key signature width in first note position")
void singleNoteWithKeySignature() {
    // Given: line with 1 note, key signature with 3 sharps
    // When: calculatePositions called
    // Then: note X = clefWidth + (3 * 8.0) + 11.5 MU
}
```

#### 3. Basic Spacing Tests (No Lyrics)
```java
@Test
@DisplayName("maintains minimum column gap between notes")
void minimumColumnGap() {
    // Given: 2 notes without syllables
    // When: calculatePositions called
    // Then: gap = column1.rightExtent + MIN_COLUMN_GAP
}

@Test
@DisplayName("handles multiple notes with varying extents")
void multipleNotesVaryingExtents() {
    // Given: notes with different right extents (dotted, accidentals)
    // When: calculatePositions called
    // Then: each gap >= MIN_COLUMN_GAP
}
```

#### 4. Lyric-Driven Spacing Tests
```java
@Test
@DisplayName("expands spacing when syllables require it")
void lyricDrivenExpansion() {
    // Given: 2 notes with long syllables
    // When: calculatePositions called
    // Then: spacing = syllableWidth/2 + MIN_SYLLABLE_GAP + syllableWidth/2
}

@Test
@DisplayName("uses minimum spacing when syllables are short")
void shortSyllablesUseMinimum() {
    // Given: 2 notes with very short syllables
    // When: calculatePositions called
    // Then: spacing = MIN_COLUMN_GAP (not syllable-driven)
}

@Test
@DisplayName("handles mixed notes with and without syllables")
void mixedSyllables() {
    // Given: notes where some have syllables, some don't
    // When: calculatePositions called
    // Then: lyric spacing only applies where syllables exist
}
```

#### 5. Accidental Clearance Tests
```java
@Test
@DisplayName("pushes note right when accidental would collide")
void accidentalClearance() {
    // Given: note with accidental after tight spacing
    // When: calculatePositions called
    // Then: note pushed right to maintain ACCIDENTAL_CLEARANCE
}

@Test
@DisplayName("no push needed when accidental has clearance")
void accidentalHasClearance() {
    // Given: note with accidental after wide spacing
    // When: calculatePositions called
    // Then: normal spacing applied, no extra push
}
```

#### 6. Beam Group Tests
```java
@Test
@DisplayName("applies tight spacing within beam group")
void beamGroupTightSpacing() {
    // Given: beam group with 4 eighth notes, no lyrics
    // When: calculatePositions called
    // Then: internal gaps = BEAM_GROUP_MIN_INTERNAL_GAP
}

@Test
@DisplayName("expands beam group when lyrics require space")
void beamGroupLyricExpansion() {
    // Given: beam group with wide syllables
    // When: calculatePositions called
    // Then: beam group expanded, spacing distributed evenly
}

@Test
@DisplayName("maintains external gap around beam groups")
void beamGroupExternalGaps() {
    // Given: beam group surrounded by non-beamed notes
    // When: calculatePositions called
    // Then: BEAM_GROUP_EXTERNAL_GAP before and after group
}

@Test
@DisplayName("handles adjacent beam groups")
void adjacentBeamGroups() {
    // Given: 2 beam groups with gap between
    // When: calculatePositions called
    // Then: BEAM_GROUP_EXTERNAL_GAP between groups
}
```

#### 7. Integration Tests
```java
@Test
@DisplayName("handles complex line with all features")
void complexLineWithAllFeatures() {
    // Given: line with rests, beamed notes, accidentals, lyrics, varying syllables
    // When: calculatePositions called
    // Then: all positioning rules applied correctly
}
```

### Test Helpers

```java
// Helper to create test Note with specified properties
private Note createNote(NoteType type, String syllable, Note.Accidental accidental, int dots)

// Helper to create test Line with key signature
private Line createLine(int keyAccidentalCount)

// Helper to create NoteColumn from note
private NoteColumn createColumn(Note note, Line line)

// Helper to verify spacing between columns
private void assertSpacingBetween(NoteColumn prev, NoteColumn curr, double expectedMin)

// Helper to create beam group
private BeamGroup createBeamGroup(Note... notes)
```

### Mocking Strategy

Since `HorizontalSpacingCalculator` needs real measurements:
- Use **real Graphics2D** from BufferedImage for FontMetrics
- Use **real Font** for accurate syllable width measurements
- Create **real Note, Line, NoteColumn** instances (they're data classes)

No mocking framework needed - use real objects for accurate unit tests.

### Running Tests

```bash
# Run all tests
mvn test

# Run just HorizontalSpacingCalculator tests
mvn test -Dtest=HorizontalSpacingCalculatorTest

# Run specific test method
mvn test -Dtest=HorizontalSpacingCalculatorTest#singleNote
```

## Implementation Steps

1. Create `HorizontalSpacingCalculator.java` with file header and package
2. Create `HorizontalSpacingCalculatorTest.java` with basic structure
3. Implement clef/key signature width calculation methods + tests
4. Implement first note positioning + tests
5. Implement basic column-to-column spacing (without beam groups) + tests
6. Add lyric spacing logic + tests
7. Add accidental clearance check + tests
8. Implement beam group detection (pre-processing approach) + tests
9. Implement beam group spacing with lyric expansion + tests
10. Add comprehensive JavaDoc comments
11. Run full test suite to verify all cases pass
12. Manual visual testing with real Line instances

## Open Questions

None - design is clear from Phase 1 foundation and plan document.

## Success Criteria

**Functionality:**
- `HorizontalSpacingCalculator` successfully sets X positions on all columns
- First note positioned 11.5 MU from clef/key signature right extent
- Syllable width drives spacing (non-proportional, per Gould/Ross)
- Accidentals maintain minimum clearance
- Beam groups have tight internal spacing unless lyrics force expansion
- All spacing constants from `LayoutConstants` are used correctly

**Code Quality:**
- Code is well-documented and follows project style guidelines
- All unit tests pass
- Test coverage includes edge cases (empty lines, single notes, complex scenarios)
- Tests are readable with descriptive names

**Integration:**
- Calculator works correctly with NoteColumn instances from NoteColumnBuilder
- Calculator correctly reads key signature info from Line
- Positioned columns are ready for use by future phases (vertical stacking, rendering)
