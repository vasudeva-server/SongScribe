**Type:** Sub-plan
**Parent:** smufl-rewrite.md → Phase 2
**Pre-planned:** Yes
**Status:** Completed

---

# Phase 2: Collapse Note Subclass Hierarchy

## Context

SongScribe has 20+ Note subclasses that differ only in their `NoteType`, bounding rectangles, and default duration. This phase collapses them so that `Note` becomes concrete with a `NoteType noteType` field, and all per-type data (rectangles, durations) moves into `NoteType`. This is a prerequisite for Phase 4 (SMuFL-driven glyph bounds) which will replace the hardcoded rectangles with metadata-computed values.

## Key Decisions

- **Rests become `NonNote`** - NonNote already overrides yPos, dotCount, accidental, forceArticulation, durationArticulation correctly. No code uses `instanceof NonNote`, so this is safe.
- **Grace notes become plain `Note`** - Their dotCount=0 override was defensive (field default is already 0). Duration=0 handled via NoteType.
- **`GraceSemiQuaver` retained** - Has extra `y0Pos`/`x2DiffPos` state.
- **`GlissandoNote`/`PasteNote` folded** into NoteType singleton handling.
- **NonNote.getYPos()** changes from hardcoded `return 0` to `return getNoteType().getDefaultYPos()` to handle BreathMark (yPos=-7) and SemibreveRest (yPos=-1).

## Execution Steps

### Step 1: Add per-type data to `NoteType`
**File**: `src/main/java/songscribe/music/NoteType.java`

Add fields:
```java
private final Rectangle realUpNoteRect;
private final Rectangle realDownNoteRect;
private final int defaultDuration;
private final int defaultYPos;
```

Each enum constant passes its rect/duration/yPos data. The alias entries (SEMIBREVEREST, etc.) delegate via the existing `NoteType(NoteType)` constructor.

Add accessor methods: `getRealUpNoteRect()`, `getRealDownNoteRect()`, `getDefaultDuration()`, `getDefaultYPos()`.

Constructor changes: Primary constructor becomes `NoteType(Note instance, String name, int keyCode, int modifiers, Rectangle realUp, Rectangle realDown, int defaultDuration, int defaultYPos)`. Overloaded constructors for convenience.

The `instance` field creation will temporarily still use old subclasses (changed in Step 5).

### Step 2: Make `Note` concrete
**File**: `src/main/java/songscribe/music/Note.java`

- Remove `abstract` from class declaration
- Add `private final NoteType noteType` field
- Change constructors:
  - `Note(NoteType noteType)` - new primary constructor
  - `Note(Note note)` - copy constructor, copies `noteType` from source
  - Keep `Note()` temporarily (deprecated, for subclass compat during transition)
- Make these methods concrete (no longer abstract):
  - `getNoteType()` -> `return noteType`
  - `clone()` -> `return new Note(this)`
  - `getRealUpNoteRect()` -> `return noteType.getRealUpNoteRect()`
  - `getRealDownNoteRect()` -> `return noteType.getRealDownNoteRect()`
  - `getDefaultDuration()` -> `return noteType.getDefaultDuration()`
- Update `GLISSANDO_NOTE` and `PASTE_NOTE` (will be created as NonNote instances)

### Step 3: Update `NonNote`
**File**: `src/main/java/songscribe/music/NonNote.java`

- Change no-arg constructor to `NonNote(NoteType noteType)` calling `super(noteType)`
- Keep copy constructor: `NonNote(Note note)` calls `super(note)`
- Change `getYPos()` from `return 0` to `return getNoteType().getDefaultYPos()`
- Remove `getDefaultDuration()` override (NoteType returns 0 for all NonNote types)
- Keep other overrides: `getDotCount()`=0, `getAccidental()`=NONE, `getForceArticulation()`=null, `getDurationArticulation()`=null
- Make concrete (remove `abstract`) and add `clone()`: `return new NonNote(this)`

### Step 4: Update `GraceSemiQuaver` (retained)
**File**: `src/main/java/songscribe/music/GraceSemiQuaver.java`

- Change constructor to `super(NoteType.GRACE_SEMIQUAVER)`
- Remove overrides now handled by NoteType: `getNoteType()`, `getRealUpNoteRect()`, `getRealDownNoteRect()`, `getDefaultDuration()`, `getDotCount()`
- Keep `y0Pos`/`x2DiffPos` fields and accessors
- Keep `clone()` (needs to copy extra state via copy constructor)

### Step 5: Change `NoteType` instance creation
**File**: `src/main/java/songscribe/music/NoteType.java`

Replace subclass instantiation in enum constants:
- Pitched notes: `new Crotchet()` -> will create instance in `static {}` block as `new Note(NoteType.CROTCHET)`
- Rests: `new CrotchetRest()` -> `new NonNote(NoteType.CROTCHET_REST)`
- Grace quaver: `new GraceQuaver()` -> `new Note(NoteType.GRACE_QUAVER)`
- Grace semiquaver: `new GraceSemiQuaver()` -> stays as `new GraceSemiQuaver()`
- Grace semiquaver edit step 1: `new GraceSemiQuaverEditStep1()` -> `new Note(NoteType.GRACE_SEMIQUAVER_EDIT_STEP1)`
- NonNote types: `new SingleBarLine()` -> `new NonNote(NoteType.SINGLE_BARLINE)`
- GLISSANDO/PASTE: Handle as singletons (clone returns `this`), instances created as NonNote with null rects

**Forward-reference problem**: Enum constants can't reference their own NoteType during construction. Solution: Pass `null` for instance in enum constructors, create instances in a `static {}` block:
```java
static {
    for (var type : values()) {
        if (type.instance == null) {
            type.instance = type.createDefaultInstance();
        }
    }
}
```
The `GLISSANDO` and `PASTE` constants currently pass `Note.GLISSANDO_NOTE` and `Note.PASTE_NOTE` - these can keep working since they're already created before NoteType loads.

### Step 6: Update external references

**`SelectionHandler.java`** (line 224):
- `Crotchet.REAL_UP_NOTE_RECT` -> `NoteType.CROTCHET.getRealUpNoteRect()`
- `Crotchet.REAL_DOWN_NOTE_RECT` -> `NoteType.CROTCHET.getRealDownNoteRect()`

**`EditModeManager.java`** (lines 325, 336):
- `new RepeatLeftRight()` -> `NoteType.REPEAT_LEFT_RIGHT.newInstance()`

**`ExportABCAction.java`** (lines 389, 398-399):
- `new Semiquaver().getDefaultDuration()` -> `NoteType.SEMIQUAVER.getDefaultDuration()`
- `new Quaver().getDefaultDuration()` -> `NoteType.QUAVER.getDefaultDuration()`
- Keep `GraceSemiQuaver` cast (retained class)

**Test files** (8 files) - replace `new Crotchet()` / `new Quaver()` with `NoteType.CROTCHET.newInstance()` / `NoteType.QUAVER.newInstance()`:
- `VerticalStackingCalculatorTest.java`
- `LineJustificationCalculatorTest.java`
- `LayoutEngineTest.java`
- `HorizontalSpacingCalculatorTest.java`
- `LayoutResultTest.java`
- `LyricsRendererTest.java`
- `ScoreLyricParsingTest.java`
- `LineComponentLayoutTest.java`

### Step 7: Delete subclass files (23 files)
All in `src/main/java/songscribe/music/`:
- `Crotchet.java`, `Minim.java`, `Quaver.java`, `Semiquaver.java`, `Demisemiquaver.java`, `Semibreve.java`
- `SemibreveRest.java`, `MinimRest.java`, `CrotchetRest.java`, `QuaverRest.java`, `SemiquaverRest.java`, `DemisemiquaverRest.java`
- `GraceQuaver.java`, `GraceSemiQuaverEditStep1.java`
- `GlissandoNote.java`, `PasteNote.java`
- `SingleBarLine.java`, `DoubleBarLine.java`, `FinalDoubleBarLine.java`
- `RepeatLeft.java`, `RepeatRight.java`, `RepeatLeftRight.java`
- `BreathMark.java`

**Retained**: `GraceSemiQuaver.java`, `NonNote.java`, `Note.java`

## Verification

1. `./scripts/compile.sh` succeeds
2. Run tests: `mvn test`
3. User loads an existing `.songscribe` file and visually confirms rendering is identical
