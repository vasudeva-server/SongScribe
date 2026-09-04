# Design pass 4 — step 3 execution: make illegal states unrepresentable

The specification is `plans/design-pass-4/findings-3.md`; the record is `plans/design-pass-4/record.md`. Every phase below is one stage of one restructuring of `ElementType` and its vocabulary. Line numbers are against commit `48fde252`.

Every phase: read `.claude/rules/serena.md` and follow it for all Java exploration and refactoring. Every file change goes through `Edit` or `Write`. Do not run `./scripts/compile.sh` or `./scripts/test.sh` in any phase but the gate. Snapshot when the phase's files are written, with one Bash call:

```bash
git add -A && git stash store -m "design pass 4: step 3 phase N" "$(git stash create)"
```

## Status Dashboard

| Phase | Description | Status | Recommended model |
|-------|-------------|--------|-------------------|
| 1 | [The vocabulary types](#-phase-1-the-vocabulary-types) | ✅ Complete | Fable, low |
| 2 | [BeatUnit and its readers](#-phase-2-beatunit-and-its-readers) | ✅ Complete | Sonnet, medium |
| 3 | [isBar() sweep](#-phase-3-isbar-sweep) | ✅ Complete | Sonnet, low |
| 4 | [Callers of the retired members](#-phase-4-callers-of-the-retired-members) | ✅ Complete | Sonnet, medium |
| 5 | [NoteTypeMapping on NoteValue](#-phase-5-notetypemapping-on-notevalue) | ✅ Complete | Fable, low |
| 6 | [Legacy element-type names convert at the reader](#-phase-6-legacy-element-type-names-convert-at-the-reader) | ✅ Complete | Sonnet, low |
| 7 | [The hover record](#-phase-7-the-hover-record) | ✅ Complete | Fable, low |
| 8 | [Gate](#-phase-8-gate) | ✅ Complete | Fable, low |

## ✅ Phase 1: The vocabulary types

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/ElementType.java, src/main/java/songscribe/dom/NoteValue.java, src/main/java/songscribe/dom/ElementBounds.java, src/main/java/songscribe/dom/GlyphAppearance.java, src/main/java/songscribe/dom/ElementLocation.java, src/main/resources/songscribe/strings.properties  <br>
**Recommended model/effort:** Fable, low — the enum is rebuilt around two new types and a constructor-time bounds computation whose legality depends on what the constructor may reference

### Tasks

1. Add the seven `Strings` keys to `src/main/resources/songscribe/strings.properties`, each in sorted position inside its group, values lowercase: `label.element.category.grace.note = grace note`; `label.note.value.whole = whole`, `label.note.value.half = half`, `label.note.value.quarter = quarter`, `label.note.value.eighth = eighth`, `label.note.value.sixteenth = sixteenth`, `label.note.value.thirty.second = thirty-second`. The generated constants are `Strings.LABEL_ELEMENT_CATEGORY_GRACE_NOTE` and `Strings.LABEL_NOTE_VALUE_WHOLE` … `Strings.LABEL_NOTE_VALUE_THIRTY_SECOND`; each must appear literally in the code written below or the dead-key audit fails the build.
2. Create `src/main/java/songscribe/dom/NoteValue.java`, a public enum with the standard licence header: `WHOLE`, `HALF`, `QUARTER`, `EIGHTH`, `SIXTEENTH`, `THIRTY_SECOND`, each declared with its ticks in PPQ (`PPQ * 4`, `PPQ * 2`, `PPQ`, `PPQ / 2`, `PPQ / 4`, `PPQ / 8`, statically imported from `songscribe.midi.MidiSequenceBuilder.PPQ` as `ElementType` does today) and its `Strings` label key. Accessors `ticks()` and `labelKey()`. `note()` and `rest()` are switches over `this` with no default, returning the `ElementType` constants `SEMIBREVE`…`DEMI_SEMIQUAVER` and `SEMIBREVE_REST`…`DEMI_SEMIQUAVER_REST`.
3. Create `src/main/java/songscribe/dom/ElementBounds.java`, a package-private record `ElementBounds(double fullWidthSs, double baseWidthSs, double fullElementHeightSs, double noteheadTopOffsetSs, double heightUpSs, double heightDownSs, double topOffsetUpSs, double topOffsetDownSs)` with a static factory `symmetric(double widthSs, double heightSs, double topOffsetSs)` producing the shape `setSymmetricBounds` L855–866 produces today: both widths equal `widthSs`, all four heights and offsets equal `heightSs` / `topOffsetSs`.
4. Change `src/main/java/songscribe/dom/GlyphAppearance.java` to `record GlyphAppearance(SMuFLGlyph glyph, int seatSp)`. Its Javadoc gains `@param seatSp the staff position the glyph is drawn at and hit-tested at, in staff-position units from the middle line`. The class Javadoc keeps its present description.
5. Rewrite `src/main/java/songscribe/dom/ElementType.java`:
   - Nest `public enum Kind { NOTE, GRACE_NOTE, REST, BARLINE, REPEAT, BREATH_MARK, KEY_CHANGE }`, each constant holding its `Strings` label key (`LABEL_ELEMENT_CATEGORY_NOTE`, `LABEL_ELEMENT_CATEGORY_GRACE_NOTE`, `LABEL_ELEMENT_CATEGORY_REST`, `LABEL_ELEMENT_CATEGORY_BARLINE`, `LABEL_ELEMENT_CATEGORY_REPEAT`, `LABEL_ELEMENT_CATEGORY_BREATH_MARK`, `LABEL_ELEMENT_CATEGORY_KEY_CHANGE`) behind `labelKey()`.
   - Fields: `private final Kind kind`, `private final @Nullable NoteValue value`, `private final ElementAppearance appearance`, `private final ElementBounds bounds`. Nothing else. Delete `instance` and its NullAway suppression, `name`, `acceleratorKey`, `defaultDuration`, `defaultStaffPosition`, `aliasOf`, and the eight `double` fields.
   - Two constructors: `ElementType(Kind kind, NoteValue value, ElementAppearance appearance)` and `ElementType(Kind kind, ElementAppearance appearance)`; the second delegates to a private three-argument form with a null value so that no constant declaration writes `null`. The constructor computes `bounds` from `(kind, value, appearance)` — see the next task.
   - Delete both static blocks (L118–133, L142–144), `getMenuShortcutKeyMask()`, the alias constructor, the fourteen IO alias constants L102–116, the comment at L97–99 about `KEY_CHANGE`'s position, `getInstance()`, `getName()`, `getAcceleratorKey()`, `getFullElementCenterXSs()`, `snapToEnd()`, `getDefaultStaffPosition()`, `setSymmetricBounds`, `copyBoundsFrom`, `validateElementBounds`, and the `java.awt`, `javax.swing`, `songscribe.util.UIUtils` and `songscribe.error.RuntimeError` imports.
   - Constant declarations take the shape in findings-3 *The constant declarations, after*: notes `(Kind.NOTE, NoteValue.X, new NoteheadAppearance(…))`; rests `(Kind.REST, NoteValue.X, new GlyphAppearance(SMuFLGlyph.REST_X, seat))` with `SEMIBREVE_REST` seated at `-2` and the five other rests at `0`; `GRACE_QUAVER(Kind.GRACE_NOTE, NoteValue.EIGHTH, new NoteheadAppearance(StemmedNotehead.BLACK))`; repeats `(Kind.REPEAT, BarAppearance.of(…))`; `BREATH_MARK(Kind.BREATH_MARK, new GlyphAppearance(SMuFLGlyph.BREATH_MARK_COMMA, -5))` keeping the comment at L86–88 that states where a breath mark sits; barlines `(Kind.BARLINE, BarAppearance.of(…))`; `KEY_CHANGE(Kind.KEY_CHANGE, new KeySignatureAppearance())`. Existing notehead and stroke arguments are unchanged.
   - `newInstance()` becomes the one factory: a switch over `kind` with no default — `KEY_CHANGE` → `new KeyChangeElement(Key.NO_ACCIDENTALS)`, `REST`, `BARLINE`, `REPEAT`, `BREATH_MARK` → `new StructuralElement(this)`, `NOTE`, `GRACE_NOTE` → `new NoteElement(this)`. Delete `createDefaultInstance()`; move the part of its Javadoc that explains why a key change starts in C major onto `newInstance()`.
   - Predicates: `isPitchedNote()` → `kind == Kind.NOTE`; `isGraceNote()` → `kind == Kind.GRACE_NOTE`; `isRest()` → `kind == Kind.REST`; `isBarLine()` → `kind == Kind.BARLINE`; `isRepeat()` → `kind == Kind.REPEAT`; `isBreathMark()` → `kind == Kind.BREATH_MARK`; `isKeyChange()` → `kind == Kind.KEY_CHANGE`. New `public boolean isBar()` → `isBarLine() || isRepeat()`, with Javadoc stating it is the "structural marker that closes a measure" test. `isNonDuration()` → `isBar() || isBreathMark() || isKeyChange()`; `cancelsAccidentals()` → `isBar() || isKeyChange()`, and its Javadoc paragraph about the pair appearing elsewhere now names `isBar()`; `isNonContentElement()` → `isGraceNote() || isBreathMark() || isKeyChange()`. `isNote()`, `isNoteWithStem()`, `isBeamable()`, `isDuration()`, `isEndingTerminal()`, `isReplaceableByTerminal()`, `isValidSongTerminal()`, `drawStaveLongitude()`, `bearsSyllableText()`, `bearsLyricChain()`, `interruptsLyricChain()`, `canCarryAnnotation()` keep their bodies.
   - `public Kind kind()` returns `kind`; `public @Nullable NoteValue noteValue()` returns `value`. `getDefaultDuration()` → `value != null && kind != Kind.GRACE_NOTE ? value.ticks() : 0`. `toRest()` → `value == null ? this : value.rest()`; `toNote()` → `value == null ? this : value.note()`.
   - `categoryNameKey()` → `kind.labelKey()`; delete its `@throws` and the `@throws` on `categoryName()`.
   - Add `public String label()`: `Strings.get(kind.labelKey())` when `value == null` or `kind == Kind.GRACE_NOTE`, otherwise `Strings.get(value.labelKey()) + ' ' + Strings.get(kind.labelKey())`. Javadoc: the one place that composes a type's display words, lowercase; a grace note omits its value word because a grace note is named by its kind alone; dots are the element's, not the type's, so the caller adds them.
   - `getFlagGlyph(direction)` switches over `value`, not `this`: `EIGHTH` → `FLAG_8TH_UP`/`FLAG_8TH_DOWN`, `SIXTEENTH` → 16th pair, `THIRTY_SECOND` → 32nd pair, `null`/`WHOLE`/`HALF`/`QUARTER` → null. A grace note's flag is always `FLAG_8TH_UP` regardless of direction: test `kind == Kind.GRACE_NOTE` first. This form is legal inside the constructor, which is where the bounds computation now calls it.
   - Bounds: a private static `ElementBounds computeBounds(Kind kind, @Nullable NoteValue value, ElementAppearance appearance)` called from the constructor, switching over `appearance` exactly as `computeElementBoundsSs` L747–780 does today, with `computeStemmedNoteBoundsSs`, `computeGraceNoteBoundsSs`, `computeGlyphBoundsSs` and `computeKeySignatureBoundsSs` rewritten as pure functions returning an `ElementBounds` instead of assigning fields. The grace branch is chosen by `kind == Kind.GRACE_NOTE` and receives the `StemmedNotehead` from the pattern binding rather than through `stemmedNotehead()`. The flag glyph inside the stemmed computation comes from `value` through the same switch `getFlagGlyph` uses: extract a private static `flagGlyph(Kind, @Nullable NoteValue, Direction)` that both call. Nothing in the constructor may reference an enum constant of `ElementType`. Keep `GRACE_NOTE_SCALE`.
   - The eight bounds accessors L255–378 and `terminalFlushRightXSs` read from `bounds`.
   - `appearance()` Javadoc drops the clause about an alias.
   - Delete `ElementLocation`'s compact constructor and `matches` in `src/main/java/songscribe/dom/ElementLocation.java`; its Javadoc keeps the sentence that absence is a null reference and drops "Both indices are non-negative when the location is valid".
6. Snapshot.

## ✅ Phase 2: BeatUnit and its readers

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/BeatUnit.java, src/main/java/songscribe/dom/Duration.java, src/main/java/songscribe/dom/Tempo.java, src/main/java/songscribe/dom/TupletValidator.java, src/main/java/songscribe/layout/MetronomeContent.java, src/main/java/songscribe/ui/component/DurationListCellRenderer.java, src/main/java/songscribe/io/musicxml/BeatUnitMapping.java, src/main/java/songscribe/io/musicxml/DirectionBuilder.java, src/main/java/songscribe/dom/BeatAt.java, src/main/java/songscribe/dom/BeatChange.java, src/main/java/songscribe/dom/TempoResolver.java, src/main/java/songscribe/io/TempoIO.java, src/main/java/songscribe/io/StaffElementIO.java, src/main/java/songscribe/ui/dialog/BeatChangeController.java, src/main/java/songscribe/ui/dialog/BeatChangeDialog.java, src/main/java/songscribe/ui/dialog/SongSettingsMusicTab.java, src/main/java/songscribe/ui/dialog/TempoChangeDialog.java, src/main/java/songscribe/ui/dialog/TempoSection.java, src/test/java/songscribe/dom/AttachmentCopyTest.java, src/test/java/songscribe/io/TempoIOTest.java, src/test/java/songscribe/io/musicxml/MusicXmlTempoReadTest.java, src/test/java/songscribe/ui/dialog/AttachmentDialogControllerTest.java, src/test/java/songscribe/ui/dialog/TempoSectionTest.java  <br>
**Recommended model/effort:** Sonnet, medium — one IDE rename, then a reshape whose every accessor is specified, and mechanical re-pointing of seven readers

### Tasks

1. Rename `Duration` to `BeatUnit` with `jet_brains_rename` (`name_path: Duration`, `relative_path: src/main/java/songscribe/dom/Duration.java`, `rename_in_comments: false`, `rename_in_text_occurrences: false`). Then run `git status --porcelain | grep -v '\.java$'` and restore any Markdown under `plans/` the rename rewrote: those files record what was found and decided, and must keep the name `Duration` where they use it.
2. Reshape `src/main/java/songscribe/dom/BeatUnit.java`: constants `SEMI_BREVE(NoteValue.WHOLE, false)`, `MINIM_DOTTED(NoteValue.HALF, true)`, `MINIM(NoteValue.HALF, false)`, `CROTCHET_DOTTED(NoteValue.QUARTER, true)`, `CROTCHET(NoteValue.QUARTER, false)`, `QUAVER_DOTTED(NoteValue.EIGHTH, true)`, `QUAVER(NoteValue.EIGHTH, false)` — the names and order stay, because `TempoIO.resolveTempoDuration` and `StaffElementIO`'s beat-change reader resolve them with `valueOf` from legacy files. Fields `noteValue` and `dotted`; accessors `noteValue()`, `dotted()`, and `ticks()` returning `noteValue.ticks()` plus half again when dotted. Delete the `StaffElement` prototype field, the static block and `getNote()`. Class Javadoc: a beat unit is a note value with at most one dot, which is the whole domain a tempo or a beat change can name; `dotted` is a boolean rather than a count for that reason.
3. `src/main/java/songscribe/dom/Tempo.java` L70: `realTempo()` uses `tempoType.ticks()`.
4. `src/main/java/songscribe/dom/TupletValidator.java`:
   - Rename the nested `private record NoteValue(ElementType type, int dots)` (L116–119) to `NotatableValue` with `jet_brains_rename` (`name_path: TupletValidator/NoteValue`, same flags). Its shape is unchanged.
   - `SHORTEST_SPAN_TICKS` L110 → `NoteValue.THIRTY_SECOND.ticks()` (the top-level `songscribe.dom.NoteValue`).
   - `describeSpan` L182–184 and `OpenSpan` L449–452: replace `beat.getNote()` / `beatAtAnchor.getNote()` and the two reads on the note with `beat.ticks()` and `beat.dotted()`.
   - `buildNotatableByTicks` L679–693: iterate `NoteValue.values()`, take `value.note()` as the type, and drop the `getDefaultDuration() == 0` test; the dot loop and the probe stay.
   - L586 `isStructuralBoundary`: `type.isBar() || type.isBreathMark()`.
5. `src/main/java/songscribe/layout/MetronomeContent.java`: `metronomeGlyphFor(NoteValue value)` is an exhaustive switch over the six values (`WHOLE` → `MET_NOTE_WHOLE`, `HALF` → `MET_NOTE_HALF_UP`, `QUARTER` → `MET_NOTE_QUARTER_UP`, `EIGHTH` → `MET_NOTE_8TH_UP`, `SIXTEENTH` → `MET_NOTE_16TH_UP`, `THIRTY_SECOND` → `MET_NOTE_32ND_UP`) with no default and no `RuntimeError`. `Builder.appendNote(BeatUnit unit)` reads `unit.noteValue()` for the glyph and `unit.dotted()` where it read `note.getDotCount() > 0`. `forBeatChange` passes `beatChange.duration()` and `beatChange.beat()`; `forTempo` passes `tempo.tempoType()`. Drop the now-unused `ElementType`, `StaffElement` and `RuntimeError` imports.
6. `src/main/java/songscribe/ui/component/DurationListCellRenderer.java`: `noteGlyphFor(BeatUnit unit)` switches over `unit.noteValue()` (`WHOLE` → `NOTE_WHOLE`, `HALF` → `NOTE_HALF_UP`, `QUARTER` → `NOTE_QUARTER_UP`, `EIGHTH` → `NOTE_8TH_UP`, `SIXTEENTH` and `THIRTY_SECOND` → their `NOTE_*_UP` glyphs) with no default. In `paintComponent`, `duration.getNote().getDotCount() > 0` → `duration.dotted()`, and the `QUAVER_DOTTED` identity test at L144 becomes `duration.noteValue() == NoteValue.EIGHTH`.
7. `src/main/java/songscribe/io/musicxml/BeatUnitMapping.java`: delete `FORWARD_MAP`, `BeatUnitEntry`, `NO_DOT`, `ONE_DOT`. `forDuration(BeatUnit unit)` becomes non-null: it returns a `BeatUnitEntry`-shaped result built from `NoteTypeMapping.typeToken(unit.noteValue())` and `unit.dotted() ? 1 : 0` — keep the record `BeatUnitEntry(String token, int dotCount)` as the return type since `DirectionBuilder` reads both fields, but it is now constructed, never looked up. `REVERSE_MAP` is built by iterating `BeatUnit.values()` and keying each by that same derivation. `forBeatUnit(token, dotCount)` stays `@Nullable`. Replace the comment referencing `BeatUnitMappingTest` (no such class exists) with one sentence: the reverse map is derived from the same function the forward direction uses, so the two cannot disagree.
8. `src/main/java/songscribe/io/musicxml/DirectionBuilder.java` L94–100 and L243–249: delete the `beatUnit == null` branches; `forDuration` no longer returns null.
9. Snapshot.

## ✅ Phase 3: isBar() sweep

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/Ending.java, src/main/java/songscribe/dom/StaffElementRun.java, src/main/java/songscribe/dom/KeyChangeElement.java, src/main/java/songscribe/dom/Line.java, src/main/java/songscribe/layout/EndingBracketGeometry.java, src/main/java/songscribe/layout/LayoutEngine.java, src/main/java/songscribe/layout/CautionaryKeySignature.java, src/main/java/songscribe/io/musicxml/MeasureMapper.java, src/main/java/songscribe/io/musicxml/MeasureBuilder.java, src/main/java/songscribe/ui/renderer/StaffElementRenderer.java, src/main/java/songscribe/ui/renderer/BarRenderer.java, src/main/java/songscribe/ui/action/ElementTypeAction.java, src/main/java/songscribe/ui/MusicEditOperations.java  <br>
**Recommended model/effort:** Sonnet, low — a mechanical substitution at listed sites

### Tasks

1. `ElementType.isBar()` (written in phase 1) answers `isBarLine() || isRepeat()`. Replace every hand-written form of that disjunction — `x.isBarLine() || x.isRepeat()`, the reversed order, and the negated conjunction `!x.isBarLine() && !x.isRepeat()` — with `x.isBar()` / `!x.isBar()` at: `Ending` L221, L364 and its other three sites; `StaffElementRun` L150; `KeyChangeElement` L111; `Line` L1853; `EndingBracketGeometry` L137, L182, L190 and its fourth site; `LayoutEngine` L901; `CautionaryKeySignature` L162; `MeasureMapper` L427; `MeasureBuilder` L308; `StaffElementRenderer` L105; `BarRenderer` L88; `ElementTypeAction` L272 and L300; `MusicEditOperations` L911. Where the two calls are on `element.getType()` twice, hold the type in a local once.
2. Before finishing, run `PROSE=1 rg -n 'isRepeat\(\)' src/main/java` and check every hit: any remaining `isBarLine`/`isRepeat` pair on one receiver is a missed site. A lone `isRepeat()` with no `isBarLine()` beside it is not.
3. Snapshot.

## ✅ Phase 4: Callers of the retired members

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/StructuralElement.java, src/main/java/songscribe/dom/StaffElement.java, src/main/java/songscribe/ui/renderer/RestRenderer.java, src/main/java/songscribe/layout/InsertionSpacingCalculator.java, src/main/java/songscribe/ui/component/StatusBar.java, src/main/java/songscribe/message/notification/ElementTypeWasSelectedNotification.java, src/main/java/songscribe/ui/edit/EditModeManager.java, src/main/java/songscribe/undo/OpNames.java  <br>
**Recommended model/effort:** Sonnet, medium — each change is specified; the `StructuralElement` switch and the `OpNames` switch must be exhaustive with no default

### Tasks

1. `src/main/java/songscribe/dom/StructuralElement.java` L57–59: `getStaffPosition()` switches over `getType().appearance()` with no default: `GlyphAppearance` → its `seatSp()`; `BarAppearance` and `KeySignatureAppearance` → `0`, with a comment that a bar and a key signature span the staff so the middle line is their position by definition; `NoteheadAppearance` → `throw new IllegalStateException(...)` with a comment that a structural element is never built from a notehead type (`ElementType.newInstance()` is the only place that constructs one and it does so only for `REST`, `BARLINE`, `REPEAT`, `BREATH_MARK`).
2. `src/main/java/songscribe/dom/StaffElement.java` L169–172: delete the `else` branch of `applyConversionFrom` that writes `targetType.getDefaultStaffPosition()` into `staffPosition`, and its comment; `StructuralElement.getStaffPosition()` overrides the field, so the write was never read.
3. `src/main/java/songscribe/ui/renderer/RestRenderer.java`: delete `SEMIBREVE_REST_STAFF_POSITION_SP` and `MINIM_REST_STAFF_POSITION_SP` (L53–54); `calculateRestYSs` L150–170 becomes `middleLineYSs + StaffPosition.toSs(note.getStaffPosition())` for every rest, and its Javadoc says the rest's position comes from its type's glyph seat. The `instanceof GlyphAppearance(var glyph)` pattern in the static block (L66) becomes `GlyphAppearance(var glyph, var ignoredSeat)`.
4. `src/main/java/songscribe/layout/InsertionSpacingCalculator.java` L667 and L689: `getInstance()` → `newInstance()`; delete the two "Shared singleton is safe" comments.
5. `src/main/java/songscribe/ui/component/StatusBar.java` L236–243: `getDurationText` builds `getDotPrefix(element.getDotCount()) + element.getType().label()` and capitalises it as today; the `name == null` branch goes.
6. `src/main/java/songscribe/message/notification/ElementTypeWasSelectedNotification.java` L39: `noteType.getName()` → `noteType.label()`.
7. `src/main/java/songscribe/ui/edit/EditModeManager.java` L313–318: inside the `isPitchedNote()` branch, `type = elementType.toRest()`; delete the `valueOf(name() + "_REST")` expression.
8. `src/main/java/songscribe/undo/OpNames.java`: `categoryOf` returns a non-null `Category` by an exhaustive switch over `type.kind()` (`ElementType.kind()` exists once phase 1 has run). `Kind.NOTE` and `Kind.GRACE_NOTE` → `Category.NOTE`; the other five map by name. Delete the `null` return, the `@Nullable`, and the `if (category == null)` guard and `@throws` in `addLabel`. `deleteLabel`'s `Category common = null` stays: there null means "no category shared by every element", which is a different fact.
9. Snapshot.

## ✅ Phase 5: NoteTypeMapping on NoteValue

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/io/musicxml/NoteTypeMapping.java, src/main/java/songscribe/io/musicxml/NoteBuilder.java, .claude/guides/contracts.md  <br>
**Recommended model/effort:** Fable, low — the nullability moves from a table lookup to the caller's kind branch, and `hasDuration` carries a known defect that the reshaping must not preserve

### Tasks

1. `src/main/java/songscribe/io/musicxml/NoteTypeMapping.java`:
   - `FORWARD_MAP` and `BASE_TICKS_MAP` key on `NoteValue` (six rows each). `typeToken(NoteValue value)` returns non-null `String`. `ticks(NoteValue value, int dotCount)` keeps its dot-count guard and drops the "no tick mapping" branch and that `@throws`.
   - `REVERSE_NOTE_MAP` and `REVERSE_REST_MAP` become one `Map<String, NoteValue> REVERSE_MAP`; `forTypeToken(token, isRest, isGrace)` keeps its signature and returns `GRACE_QUAVER` for grace, else `value == null ? null : (isRest ? value.rest() : value.note())`.
   - `hasDuration(ElementType type)` → `type.isDuration()`. Its Javadoc states: a pitched note or a rest emits `<duration>`; a grace note does not. This corrects the defect `.claude/guides/contracts.md` L106–108 records against the old `type != GRACE_QUAVER` body; update that guide's paragraph to describe the method as it now is.
   - The class Javadoc's ASCII table keys rows by `NoteValue`, with one line that a rest and a grace note share the value of their pitched counterpart.
2. `src/main/java/songscribe/io/musicxml/NoteBuilder.java`:
   - `buildNote` L119–123: after the breath-mark fold, `var value = type.noteValue(); if (value == null) { return null; }` replaces the `typeToken == null` test; `typeToken` becomes `NoteTypeMapping.typeToken(value)`. Pass `value` into `performedTicks` in place of `type` (L166, L504), whose signature becomes `performedTicks(NoteValue value, int dotCount, @Nullable Tuplet tuplet)`.
   - `buildTimeModification` L442–443: `var noteValue = tuplet.getNoteValue(); var value = noteValue == null ? null : noteValue.noteValue(); var normalTypeToken = value == null ? null : NoteTypeMapping.typeToken(value);`.
3. Snapshot.

## ✅ Phase 6: Legacy element-type names convert at the reader

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Files:** src/main/java/songscribe/io/StaffElementIO.java  <br>
**Recommended model/effort:** Sonnet, low — one map, consulted before `valueOf` in two readers, on the pattern `TempoIO.LEGACY_TEMPO_DURATION_NAMES` already sets

### Tasks

1. In `src/main/java/songscribe/io/StaffElementIO.java` add `private static final Map<String, ElementType> LEGACY_ELEMENT_TYPE_NAMES` with nineteen entries: the fourteen alias spellings phase 1 deleted from `ElementType` (`SEMIBREVEREST` → `SEMIBREVE_REST`, `MINIMREST`, `CROTCHETREST`, `QUAVERREST`, `SEMIQUAVERREST`, `DEMISEMIQUAVERREST`, `GRACEQUAVER` → `GRACE_QUAVER`, `REPEATLEFT`, `REPEATRIGHT`, `REPEATLEFTRIGHT`, `BREATHMARK`, `SINGLEBARLINE`, `DOUBLEBARLINE`, `FINALDOUBLEBARLINE`, each to the underscored constant) and the five the if-chains at L222–235 and L262–277 handle (`LINE` → `SINGLE_BARLINE`, `VERTICALLINE` → `SINGLE_BARLINE`, `GRACE_SEMIQUAVER`, `GRACE_SEMIQUAVER_EDIT_STEP1`, `GRACESEMIQUAVER` → `GRACE_QUAVER`).
2. Add a private static `resolveElementType(String name)` that consults the map and falls back to `ElementType.valueOf`, throwing `DocumentValidation.corrupt(LOG, "Corrupt document: unknown element type: '{}'", name)` on `IllegalArgumentException`, mirroring `TempoIO.resolveTempoDuration`. Both `startElement10` and `startElement11` call it in place of their if-chains and `valueOf`; the `NEWLINE` early return in `startElement10` stays. The v1.1 `NullPointerException` catch goes with the raw `valueOf`.
3. Snapshot.

## ✅ Phase 7: The hover record

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/ui/component/score/PreviewElementManager.java, src/main/java/songscribe/ui/component/score/PreviewElementInserter.java, src/main/java/songscribe/ui/component/score/PreviewElementOverlay.java, src/main/java/songscribe/ui/component/score/LineComponent.java, src/main/java/songscribe/ui/edit/GraceModeManager.java, src/main/java/songscribe/ui/renderer/LineInvariants.java  <br>
**Recommended model/effort:** Fable, low — five fields written and invalidated on different paths collapse into one nullable value, and each caller's null branch is a behavior decision the specification settles

### Tasks

1. In `src/main/java/songscribe/ui/component/score/PreviewElementManager.java` declare `private record PreviewHover(LineComponent line, int insertionIndex, int staffPositionSp, boolean overElementX, boolean overElementHead) {}` and `private static @Nullable PreviewHover hover = null;` replacing `currentPreviewLine`, `currentXIndex`, `currentStaffPosition`, `xPosSsMatchesElement` and `yPosSpMatchesElement` (L79–99). `currentMouseLine` and `currentMouseXSs` are not hover state and stay.
2. `trackMouse` L555–722: build `var candidate = new PreviewHover(lc, xIndex, staffPosition, newXMatch, newYMatch)`. The early return `!xIndexChanged && !configurationChanged` becomes `candidate.equals(hover)`. `configurationChanged` is `hover == null || lc != hover.line() || staffPosition != hover.staffPositionSp() || newXMatch != hover.overElementX() || newYMatch != hover.overElementHead()`; keep the comment at L664–669 explaining why position alone needs only a translate. `previousPreviewLine` is `hover == null ? null : hover.line()` taken before `hover = candidate`. The layout-null return at L603–610 sets `hover = null` and keeps its comment, reworded to say a stale hover is no hover. `currentPreviewLine == lc` tests become `hover != null && hover.line() == lc`.
3. `clearPreviewElement` L417–436: `previousPreviewLine` from the record, then `hover = null`; the rest of the method is unchanged.
4. `restorePreviewElement` L880–905: replace `currentXIndex = -1` with `hover = null`, and before the synthetic event, repaint the line the old hover held when it is not `lc` (the repaint `previousPreviewLine` did in `trackMouse`); the comment at L894–897 is rewritten to say the hover is dropped so `trackMouse` recomputes it.
5. Accessors: delete `getCurrentXIndex()`, `getCurrentStaffPosition()`, `isXPosSsMatchesElement()`, `isHoveringOverElementHead()` (no callers). `getCurrentInsertionLine()` stays and returns `hover == null ? null : hover.line()`; it already answered null for no hover, and `PreviewOverlayRegistry` L88, L144 and `RecordedInkOverlay` L91 keep calling it. Add `static @Nullable PreviewHover hover()` returning the record, and make `PreviewHover` package-private so the two callers below can read it. `hasPreviewElement(lc)` and every other reader of the five fields inside the class reads the record with a null test.
6. `getHoveredElementLocation` L496–517: `var current = hover; if (current == null || !current.overElementX()) { return null; }`, then the breath-mark and `canReplaceElementAt(current.insertionIndex())` tests, then `new ElementLocation(current.line().getLineIndex(), current.insertionIndex())`.
7. `applyStaffPosition` L910–917: with `StructuralElement.getStaffPosition()` (phase 4) ignoring the field, the rest branch writes nothing that is read; reduce the method to `element.setStaffPosition(staffPositionSp)` for a pitched note only — `if (element.getType().isPitchedNote())` — and reword its Javadoc.
8. `src/main/java/songscribe/ui/component/score/PreviewElementInserter.java`: `handleClick` L70–150 takes `var hover = PreviewElementManager.hover(); if (hover == null) { return; }` after the `line == null` return, then reads `hover.insertionIndex()` and `hover.overElementX()` at the eight and five sites that called the accessors. `confirmReplacementRestatements` L383–384 and `modifyExistingElement` L437–438 receive the staff position as a parameter from `handleClick` (`hover.staffPositionSp()`) rather than reading a static.
9. `src/main/java/songscribe/ui/component/score/PreviewElementOverlay.java` L157–158 and L185: take the hover once; `calculateElementXSs` returns `0` for a null hover as it does for a null line, and `recordPreviewElement` returns `DisplayList.EMPTY` for a null hover as it does for null invariants.
10. `src/main/java/songscribe/ui/edit/GraceModeManager.java` L350 and L553: take the hover; at L350 a null hover returns `false` where `xIndex < 0` did, and the `xIndex < 0` test goes; at L553 a null hover takes the `abort()` path that a missing index takes.
11. `src/main/java/songscribe/ui/component/score/LineComponent.java` L614–626: delete the static delegators `getCurrentXIndex()` and `getCurrentStaffPosition()` (no callers).
12. `src/main/java/songscribe/ui/renderer/LineInvariants.java` L455: `new ElementLocation(lineIndex, elementIndex).equals(matched)`.
13. Snapshot.

## ✅ Phase 8: Gate

**Status:** Complete  <br>
**BlockedBy:** 1, 2, 3, 4, 5, 6, 7  <br>
**Files:** —  <br>
**Recommended model/effort:** Fable, low — a failure here is information about a call site the specification did not account for, and the fix is a design decision

### Tasks

1. `./scripts/compile.sh --test`. Fix every error in the phase that owns the file; a compile error in a file no phase names is a call site the specification missed — fix it on the same terms as the phase it belongs with, and list it in the record's *Findings raised* if it is anything but a mechanical re-point.
2. `./scripts/test.sh BarAppearanceTest EndingBracketGeometryTest TempoIOTest MusicXmlTempoReadTest`, then `./scripts/test.sh AttachmentCopyTest AttachmentDialogControllerTest TempoSectionTest`. A failure is code, test, or contract; never rerun with flags and never assume it pre-existing.
3. Snapshot: `git add -A && git stash store -m "design pass 4: step 3 complete" "$(git stash create)"`. Mark step 3 ✅ in `plans/design-pass-4/record.md`.
