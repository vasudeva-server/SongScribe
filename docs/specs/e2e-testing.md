# E2E Testing Strategy: Rendering Rewrite Milestones 1-3

## Overview

End-to-end interaction tests for the rendering rewrite, covering milestones 1 (Coordinate System + Staff + Notes), 2 (Beams + Stems), and 3 (Ties + Glissandos). Tests verify that user interactions produce correct model state. Visual/rendering correctness is out of scope -- these tests assert on `Composition`, `Line`, `Note`, and related model objects after performing UI actions via AssertJ Swing's robot.

**Goal:** Catch interaction regressions during milestones 4-6 and beyond. Every manual verification item from milestones 1-3 that involves a user interaction or model state check gets an automated test.

**Test count:** 38 tests across 6 test classes.

---

## Framework & Dependencies

### Primary Stack

| Component | Version | Notes |
|-----------|---------|-------|
| JUnit 5 (Jupiter) | 5.11.4+ | Already in pom.xml |
| AssertJ Core | 3.27.3+ | Already in pom.xml. Bump to 3.27.7 if ByteBuddy issues arise on JDK 25 |
| AssertJ Swing | 3.17.1 | **New dependency.** Community-maintained, compatible with Java 25 via `--add-opens` |

### New pom.xml Dependency

```xml
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-swing-junit</artifactId>
    <version>3.17.1</version>
    <scope>test</scope>
</dependency>
```

### Surefire Configuration

Replace the existing `<argLine>` in surefire-plugin config:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.5.2</version>
    <configuration>
        <argLine>
            --add-opens java.desktop/javax.swing=ALL-UNNAMED
            --add-opens java.desktop/javax.swing.plaf.basic=ALL-UNNAMED
            --add-opens java.desktop/java.awt=ALL-UNNAMED
            --add-opens java.base/java.lang=ALL-UNNAMED
        </argLine>
    </configuration>
</plugin>
```

**Note:** The current `-Djava.awt.headless=true` must be **removed** -- AssertJ Swing requires a real display. For future CI on headless Linux, use `xvfb-run mvn test`.

---

## Test Organization

```
src/test/java/songscribe/e2e/
    BaseSwingTest.java          # Base class: robot lifecycle, app bootstrap, helpers
    NoteInsertionTest.java      # Milestone 1: note insertion, replacement, pitch drag
    SelectionTest.java          # Milestone 1: click-to-select, shift-click range, deselect
    SaveLoadRoundTripTest.java  # Milestone 1: programmatic save/load, model equality
    BeamingTest.java            # Milestone 2: automatic beaming, manual toggle, stem direction
    TieTest.java                # Milestone 3: tie creation, removal, selection semantics, drag
    GlissandoTest.java          # Milestone 3: insert, select, delete, highlight, persistence

src/test/resources/fixtures/
    round-trip.mssw             # Notes with various properties for save/load verification
```

---

## Preparatory Work

### Production Code Changes

#### 1. Component Naming

AssertJ Swing locates components by their `name` property (set via `component.setName("...")`). No Swing components in SongScribe currently have names set.

**Toolbar buttons (automatic):** Add one line in `UIUtils.configureButtonFromAction()`:

```java
button.setName(action.getActionCommand());
```

This automatically names every toolbar button using its unique action command string. Covers all duration buttons, beam toggle, tie toggle, flip stem, glissando tools, etc. -- current and future.

**Non-action components (manual):**

| Component | Location | Name |
|-----------|----------|------|
| `Score` | `Score.init()` | `"score"` |
| `LineComponent` | `LineComponent` constructor | `"line-" + lineIndex` |
| `ModeCycleButton` | `ModeCycleButton` constructor | `"btn-mode-cycle"` |

#### 2. Extract `staffPositionToYPx()` onto `LineComponent`

`NoteHitTest.buildNoteHitRect()` computes note Y position with:

```java
var noteY = lc.getMiddleLineYPx() + (int)(note.getStaffPosition() * Score.NOTE_Y_OFFSET_PX);
```

This formula must be extracted into a shared method on `LineComponent` to avoid duplication between `NoteHitTest` and the test helpers:

```java
// In LineComponent:
public int staffPositionToYPx(int staffPosition) {
    return getMiddleLineYPx() + (int)(staffPosition * Score.NOTE_Y_OFFSET_PX);
}
```

`NoteHitTest.buildNoteHitRect()` is then updated to call `lc.staffPositionToYPx(note.getStaffPosition())`.

---

## Base Test Class

```
┌─────────────────────────────────────────────────────────┐
│ BaseSwingTest                                           │
│                                                         │
│  @BeforeAll (once per class)                            │
│  ├── Install FailOnThreadViolationRepaintManager        │
│  ├── Create Robot                                       │
│  ├── Boot MainFrame singleton                           │
│  └── Create + show FrameFixture                         │
│                                                         │
│  @BeforeEach (once per test)                            │
│  └── Reset composition (new Composition())              │
│                                                         │
│  @AfterAll (once per class)                             │
│  └── Clean up FrameFixture + Robot                      │
│                                                         │
│  Helpers:                                               │
│  ├── score()            → Score                         │
│  ├── composition()      → Composition                   │
│  ├── noteScreenPosition(lineIdx, noteIdx) → Point       │
│  ├── insertionPoint(lineIdx, staffPos)    → Point       │
│  ├── dragNote(lineIdx, noteIdx, targetSP) → void        │
│  ├── performLayout(lineIdx)               → void        │
│  ├── isBeamed(lineIdx, noteIdx)           → boolean     │
│  └── isTied(lineIdx, noteIdx)             → boolean     │
└─────────────────────────────────────────────────────────┘
```

### Lifecycle

Uses `@TestInstance(Lifecycle.PER_CLASS)` with `@BeforeAll`/`@AfterAll` for FrameFixture setup and cleanup. This avoids 38 show/cleanup cycles for the frame while still isolating model state via per-test composition reset in `@BeforeEach`.

The `MainFrame` singleton is shared across the JVM. Tests reset composition state, not the frame itself.

### Coordinate Helpers

**`noteScreenPosition(int lineIndex, int noteIndex)`** returns a `Point` in pixel coordinates suitable for robot clicks. Uses:
- X: `LayoutResult.getNoteXSs(note)` + `ScaleContext.toPixels()` (same as `NoteHitTest.buildNoteHitRect`)
- Y: `LineComponent.staffPositionToYPx(note.getStaffPosition())` (the extracted shared method)

**`insertionPoint(int lineIndex, int staffPosition)`** returns a `Point` for clicking to insert a note. X is past the last note on the line (or at a fixed offset if the line is empty). Y uses `LineComponent.staffPositionToYPx(staffPosition)`.

**`dragNote(int lineIndex, int noteIndex, int targetStaffPosition)`** performs a robot drag:

```java
protected void dragNote(int lineIndex, int noteIndex, int targetStaffPosition) {
    var lc = score().getLineComponent(lineIndex);
    var startPoint = noteScreenPosition(lineIndex, noteIndex);
    var endY = lc.staffPositionToYPx(targetStaffPosition);
    var endPoint = new Point(startPoint.x, endY);

    robot.pressMouse(lc, startPoint, MouseButton.LEFT_BUTTON);
    robot.moveMouse(lc, endPoint);
    robot.releaseMouse(MouseButton.LEFT_BUTTON);
}
```

### Layout Synchronization

After any model mutation (note insertion, composition set), explicitly call `performLayout()` before reading layout data for the next click:

```java
protected void performLayout(int lineIndex) {
    GuiActionRunner.execute(() -> score().getLineComponent(lineIndex).performLayout());
}
```

This is deterministic and avoids flaky timing from lazy paint-cycle layout.

---

## Test Cases by Milestone

### Milestone 1: Coordinate System + Staff + Notes

#### NoteInsertionTest (7 tests)

| Test | Steps | Assertions |
|------|-------|------------|
| `testInsertQuarterNote` | 1. Ensure edit mode. 2. Select quarter duration via toolbar. 3. Click on line 0 at a staff position. | Note count increased by 1. Last note has `NoteType.CROTCHET`. Last note has expected `staffPosition`. |
| `testInsertEighthNote` | Same flow with eighth note duration. | Note has `NoteType.QUAVER`. |
| `testInsertHalfNote` | Same flow with half note. | Note has `NoteType.MINIM`. |
| `testInsertNoteAtDifferentStaffPositions` | Insert notes at staff positions 0, -4, +4, -8. | Each note's `staffPosition` matches the click target. |
| `testInsertRest` | Switch to rest mode, click to insert. | Note has a rest NoteType. |
| `testReplaceNoteAtSameXDifferentPitch` | 1. Insert a quarter note. 2. Select a different duration. 3. Click at the same X but a different staff position. | Note count is unchanged. The note at that X has the new duration and new staff position. |
| `testDragNoteToNewStaffPosition` | 1. Insert a note. 2. Enter selection mode. 3. Click to select the note. 4. Drag it vertically to a different staff position. | The same note object has updated `staffPosition`. Note count unchanged. |

#### SelectionTest (6 tests)

| Test | Steps | Assertions |
|------|-------|------------|
| `testClickToSelectNote` | 1. Build composition with notes programmatically. 2. Alt-click on line to enter selection mode. 3. Click on a note's position. | `score().isNoteSelected()` returns true. `score().getSingleSelectedNote()` matches expected note. |
| `testClickEmptySpaceDeselects` | 1. Select a note. 2. Click on empty space (far from any note). | No note is selected. |
| `testModeToggle` | 1. Verify edit mode is active. 2. Alt-click. 3. Verify selection mode. 4. Click mode cycle button. 5. Verify edit mode restored. | `lineComponent.isEditMode()` reflects expected state after each action. |
| `testShiftClickExtendsSelection` | 1. Insert 3 notes. 2. Enter selection mode. 3. Select 1st note. 4. Shift-click 3rd note. | All 3 notes are selected. |
| `testShiftClickShrinksSelection` | Continuing from above: shift-click 2nd note. | 3rd note deselects. 1st and 2nd remain selected. |
| `testMetaDDeselectsAll` | 1. Select notes. 2. Press Meta+D (Cmd-D). | No notes selected. |

#### SaveLoadRoundTripTest (2 tests)

| Test | Steps | Assertions |
|------|-------|------------|
| `testSaveAndReloadPreservesNotes` | 1. Build composition programmatically. 2. Read all note staffPositions. 3. Write to temp file via `CompositionIO.writeComposition()`. 4. Read back. 5. Compare. | All `staffPosition` values match. Note count per line matches. NoteTypes match. |
| `testSaveAndReloadPreservesKeySignature` | 1. Set key signature on composition. 2. Round-trip. | Key type and number match after reload. |

### Milestone 2: Beams + Stems

#### BeamingTest (6 tests)

| Test | Steps | Assertions |
|------|-------|------------|
| `testAutoBeamingOnInsertion` | Insert two consecutive eighth notes. | Both notes in a beam group (`line.getBeamings()` contains interval covering both). |
| `testToggleBeamingOnSelection` | 1. Build composition with unbeamed eighth notes. 2. Select two adjacent notes. 3. Click beam toggle button. | Beam interval exists covering the selection. |
| `testToggleBeamingRemovesExistingBeam` | 1. Build composition with beamed notes. 2. Select the beamed notes. 3. Click beam toggle. | Beam interval no longer exists for those notes. |
| `testFlipStemDirection` | 1. Build composition with beamed notes. 2. Select one note in a beam group. 3. Click flip stem button. | `note.isStemDirectionAuto()` is false. `note.isUpper()` is opposite of before. |
| `testFlipStemDirectionUnbeamed` | Same flow with an unbeamed note. | Same assertions. |
| `testStemDirectionPersistsThroughSaveLoad` | 1. Flip a note's stem. 2. Round-trip save/load. | `note.isStemDirectionAuto()` still false. `note.isUpper()` matches flipped value. |

### Milestone 3: Ties + Glissandos

#### TieTest (5 tests)

| Test | Steps | Assertions |
|------|-------|------------|
| `testCreateTieViaSelection` | 1. Build composition with two adjacent notes. 2. Select both notes. 3. Click tie toggle button. | `line.getTies()` contains interval covering the two notes. |
| `testRemoveTieViaToggle` | 1. Build composition with tied notes. 2. Select the tied notes. 3. Click tie toggle. | Tie interval removed. |
| `testTiePersistsThroughSaveLoad` | 1. Create a tie. 2. Round-trip save/load. | Tie interval exists with same start/end indices. |
| `testSelectTieNoteSelectsTieNotOtherNote` | 1. Build composition with tied notes. 2. Select one note of the tie. | The tie is selected. The other note is NOT independently selected. Verify via `LineSelectionState`. |
| `testDragTiedNoteMovesOther` | 1. Build composition with tied notes. 2. Select one note. 3. Drag it to a different staff position. | Both notes have the new staff position. |

#### GlissandoTest (11 tests)

| Test | Steps | Assertions |
|------|-------|------------|
| `testInsertConnectedGlissando` | 1. Build composition with two adjacent notes. 2. Select connecting glissando tool (action: `"glissando"`). 3. Click on first note. | `note.getGlissando().type` is `CONNECTED`. |
| `testInsertSlideOutGlissando` | 1. Build composition with a note. 2. Select slide-out tool (action: `"slide-out"`). 3. Click on the note. | `note.getGlissando().type` is `SLIDE_OUT`. |
| `testSelectGlissandoByClick` | 1. Build composition with glissando. 2. Enter selection mode. 3. Click on the glissando line. | `selectionCoordinator.isGlissandoSelected()` returns true. |
| `testSelectSourceNoteHighlightsGlissando` | 1. Build composition with connected glissando. 2. Select the source note. | Source note is selected. Glissando exists on that note (model-level: `isNoteSelected(sourceIndex)` + `note.getGlissando() != NO_GLISSANDO`). |
| `testSelectTargetNoteHighlightsGlissando` | 1. Build composition with connected glissando. 2. Select the target note. | Target note is selected. Previous note has glissando pointing to it (model-level: `isNoteSelected(targetIndex)` + `prevNote.getGlissando().type == CONNECTED`). |
| `testDeleteSelectedGlissando` | 1. Build composition with glissando. 2. Select the glissando. 3. Press Delete. | `note.getGlissando() == NO_GLISSANDO`. |
| `testGlissandoPersistsThroughSaveLoad` | 1. Build composition with glissando. 2. Round-trip save/load. | Glissando exists on the note with same type. |
| `testDeleteSourceNoteRemovesConnectedGlissando` | 1. Build composition with connected glissando on note A -> note B. 2. Select note A. 3. Delete note A. | Note A removed. No orphaned glissando data. |
| `testDeleteSourceNoteRemovesSlideOut` | 1. Build composition with slide-out glissando on note. 2. Select the note. 3. Delete. | Note removed. No orphaned glissando. |
| `testDeleteTargetNoteRemovesConnectedGlissando` | 1. Build composition with connected glissando on note A -> note B. 2. Select note B. 3. Delete note B. | Note B removed. `noteA.getGlissando() == NO_GLISSANDO`. |
| `testDragToUnisonRemovesConnectedGlissando` | 1. Build composition with connected glissando between notes at different pitches. 2. Drag source note to same pitch as target. | `note.getGlissando() == NO_GLISSANDO` (unison connected glissando is musically meaningless). |

---

## Test Fixture Strategy

### Programmatic Construction (default)

Most tests build compositions in code. This is self-documenting, version-proof, and doesn't require manual recreation when the file format changes.

```java
var composition = new Composition();
var line = new Line();
var note = NoteType.QUAVER.newInstance();
note.setStaffPosition(-4);
line.addNote(note);
composition.addLine(line);

GuiActionRunner.execute(() -> score().setComposition(composition));
performLayout(0);  // Ensure layout is computed before reading positions
```

### File-Based Fixtures

Only used for `SaveLoadRoundTripTest`, where testing actual file I/O is the point. Stored in `src/test/resources/fixtures/`.

| Fixture | Contents | Used By |
|---------|----------|---------|
| `round-trip.mssw` | 1 line, notes with various properties (durations, key sig) | SaveLoadRoundTripTest |

Created by saving a composition from the running app in v2.2 format.

---

## Implementation Phases

### Phase 1: Infrastructure

- [ ] Add `assertj-swing-junit` dependency to pom.xml
- [ ] Update surefire `<argLine>` (replace headless flag with `--add-opens`)
- [ ] Add `button.setName(action.getActionCommand())` in `UIUtils.configureButtonFromAction()`
- [ ] Add `setName()` to `Score.init()`, `LineComponent` constructor, `ModeCycleButton` constructor
- [ ] Extract `staffPositionToYPx()` onto `LineComponent`, update `NoteHitTest.buildNoteHitRect()` to call it
- [ ] Create `BaseSwingTest.java` with lifecycle, coordinate helpers, and drag helper
- [ ] Write one smoke test: boot app, verify `score()` is not null, verify line count > 0

### Phase 2: Milestone 1 Tests

- [ ] Implement `NoteInsertionTest` (7 tests)
- [ ] Implement `SelectionTest` (6 tests)
- [ ] Create `round-trip.mssw` fixture
- [ ] Implement `SaveLoadRoundTripTest` (2 tests)

### Phase 3: Milestone 2 Tests

- [ ] Implement `BeamingTest` (6 tests)

### Phase 4: Milestone 3 Tests

- [ ] Implement `TieTest` (5 tests)
- [ ] Implement `GlissandoTest` (11 tests)

---

## Resolved Design Decisions

These were open questions that have been resolved during plan review.

1. **MainFrame singleton isolation.** Use per-class `FrameFixture` (`@TestInstance(Lifecycle.PER_CLASS)`) with per-test composition reset via `score().setComposition(new Composition())` in `@BeforeEach`. No need for `MainFrame.resetForTesting()`.

2. **Robot timing.** After any model mutation, explicitly call `performLayout()` on the affected `LineComponent` before reading layout data. This is deterministic and avoids flaky paint-cycle timing.

3. **EDT safety.** All model reads in assertions should happen on the EDT via `GuiActionRunner.execute()` to avoid threading issues, unless the model objects are immutable or effectively single-threaded.

4. **Coordinate conversion.** Use pixel-space formulas matching `NoteHitTest.buildNoteHitRect()` (the actual hit-test code). The Y formula is extracted into `LineComponent.staffPositionToYPx()` to avoid duplication. The X coordinate uses `LayoutResult.getNoteXSs()` + `ScaleContext.toPixels()`.

5. **Component naming.** Toolbar buttons are named automatically via `UIUtils.configureButtonFromAction()` using `action.getActionCommand()`. Non-action components (`Score`, `LineComponent`, `ModeCycleButton`) are named manually in their constructors.

6. **Fixture strategy.** Programmatic construction is the default. File-based fixtures only for `SaveLoadRoundTripTest`.

---

## NOT in Scope

| Item | Rationale |
|------|-----------|
| Visual/rendering correctness tests | Would need image comparison infrastructure |
| CI headless runner setup (xvfb) | Future work, not blocking local development |
| v2.1 legacy format loading tests | Known infinite loop bug in older format parsing; deferred to comprehensive I/O test suite |
| Playback interaction tests | Not part of milestones 1-3 |
| Lyrics interaction tests | Not part of milestones 1-3 |
| Multi-line interactions (line insertion, cross-line selection) | Not covered in milestones 1-3 |
