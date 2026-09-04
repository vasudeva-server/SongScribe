# Design pass 4 — steps 1 and 2: inventory and class design

Target: `dom/Duration`, `ElementType`, `ElementLocation`, `ElementAppearance`, `NoteheadAppearance`, `GlyphAppearance`, `BarAppearance`, `KeySignatureAppearance`. Line numbers are against commit `48fde252`.

---

## Step 1 — What it found

### Types and the invariants each fails to carry

`ElementType` (923 lines, 31 canonical constants + 14 aliases)

**Category is a concept with no type.** Seven kinds of element — note, grace note, rest, barline, repeat, breath mark, key change — are expressed as predicates, and three of them read declaration order:

- `isPitchedNote()` L420, `isRest()` L436, `isBarLine()` L455 are ordinal ranges. The comment at L97 exists to defend `KEY_CHANGE`'s position against them. Reordering the enum silently changes what an element is.
- `isRepeat()` L447, `isBeamable()` L443, `isKeyChange()` L494, `isGraceNote()` L498, `isBreathMark()` L545 are identity lists. `isNonContentElement()` L642 writes `this == BREATH_MARK` beside `isBreathMark()`.
- The category is re-derived by if-chain in three places: `categoryNameKey()` L598–623 (ending in a throw no canonical type reaches), `createDefaultInstance()` L224–233, and `OpNames.categoryOf` L82–103 outside the target.
- **"Bar" — barline or repeat — is a category with no predicate.** The pair `isBarLine() || isRepeat()` (or its negation) appears at 17 call sites: `Ending` ×5, `EndingBracketGeometry` ×4, `StaffElementRun` L149, `KeyChangeElement` L111, `TupletValidator` L585, `Line` L1853, `StaffElementRenderer` L104, `ElementTypeAction` ×2, `BarRenderer` L88, `LayoutEngine` L901, `MeasureMapper` L427, `MeasureBuilder` L307, `MusicEditOperations` L910, `CautionaryKeySignature` L161, and both tests that touch bars. `BarAppearance` is exactly this set's appearance, and `BarRenderer.render` L88 tests the pair and then L109 tests `appearance() instanceof BarAppearance` again.

**Note value is a concept with no type.** Whole, half, quarter, eighth, 16th and 32nd exist twice in the enum (as note and as rest) and are paired by hand:

- `toRest()` L662 and `toNote()` L678 are six-arm switches that answer `this` for a type with no counterpart.
- `EditModeManager.makePreviewElement` L316 pairs them by string: `ElementType.valueOf(elementType.name() + "_REST")`.
- `NoteTypeMapping` (`io/musicxml`) maps the same six values to MusicXML tokens and tick counts; `MetronomeContent.metronomeGlyphFor` L77 maps the same six to metronome glyphs with a `default -> throw`.
- `defaultDuration` (L158) is an `int` of PPQ ticks with `0` standing for "has no duration" (every rest has one; `GRACE_QUAVER` and every structural type have `0`). `TupletValidator.buildNotatableByTicks` L683 tests `== 0` after `isPitchedNote()`, which already excludes every zero — the test is dead.

**The 14 IO aliases (L103–116) are a legacy name table living in the domain enum.** Their only consumer is `ElementType.valueOf(type)` in `StaffElementIO` L234 and L272, reading `.mssw` files. They reach the rest of the program through `values()`: `TupletValidator` L682, `RestRenderer` static init L63, and `BarAppearanceTest.barAndRepeatTypes` L80 all iterate them. An alias answers `false` to every category predicate (its ordinal is past every range and it is identical to nothing), so `SEMIBREVEREST.isRest()` is `false`, and `categoryNameKey()` throws only for an alias. `newInstance()` on an alias clones the canonical instance, so an alias never becomes an element's type. Supporting them costs `aliasOf` (L161), the alias constructor (L206), two copy loops (L127–132, L783–788), the `aliasOf != null` skips at L122, L754, L911, and `copyBoundsFrom` (L868). `TempoIO.LEGACY_TEMPO_DURATION_NAMES` L46 already does the same job for `Duration` as a map in the reader.

**Swing lives in the model, and nothing reads it.** `getAcceleratorKey()` L248 has **zero callers**. So `keyCode`, `modifiers`, the `-1` sentinel meaning "menu shortcut mask" (L187), `getMenuShortcutKeyMask()` L146 (public static, called only from the constructor), the `@Nullable KeyStroke acceleratorKey` field, and the imports of `KeyEvent`, `KeyStroke`, `GraphicsEnvironment` and `UIUtils` are dead. The keystrokes are declared again, and actually used, on the actions: `ElementTypeAction` L63, L135, L147.

`name` **is** `@Nullable` **(L155) and never null.** Every canonical constructor receives a string literal; aliases copy one. `StatusBar.getDurationText` L239 guards `name == null` — dead. The value is an English literal ("Whole note", "Repeat left/right") that reaches the user through `StatusBar` L237, not through `Strings`; its other reader is `ElementTypeWasSelectedNotification.toString`.

**Bounds are eight mutable** `double` **fields (L163–170) filled after construction** by the static block at L142 → `computeElementBoundsSs()` L752, then checked at runtime by `validateElementBounds()` L909, which throws `missingResource` if a width or height is not positive. Every input is a `SMuFLMetadata` bounding box or stem anchor, which pass 2 made total ("a font that cannot answer fails the application"), or a `Staff`/`StemMetrics` constant. The `instance` field (L153) carries a NullAway suppression for the same reason: assigned in the static block.

`getInstance()` **L236 hands out the shared mutable prototype** that `newInstance()` clones. Two callers, both in `InsertionSpacingCalculator` (L667, L689), each with a comment asserting they only read. Any `set` on the result corrupts every later `newInstance()` of that type.

**Dead members:** `snapToEnd()` L695 (0 callers), `getFullElementCenterXSs()` L272 (0), `getAcceleratorKey()` L248 (0).

**Unchecked narrowing:** `notehead()` L405 casts `appearance` to `NoteheadAppearance`; `stemmedNotehead()` L416 casts again to `StemmedNotehead`. Both state their precondition in prose. Callers: `NoteGeometry` L118, L623, L268, L93/95; `StaffElementRenderer` L188; `NoteColumnGeometry` L80; self L827. Outside `ElementType`, no caller pattern-matches `NoteheadAppearance`; the accessor does the cast for them.

**Volta anchors** (`voltaOpeningXOffsetSs` L319, `voltaClosingXOffsetSs` L334) unwrap `BarAppearance` and answer `0` for any other appearance, with a Javadoc saying a volta opens on the element's own X there. An ending may open or close on a note, but no caller takes that `0`: `EndingBracketGeometry` L136 and L189 test `isBarLine() || isRepeat()` and give a note its own formula (left edge minus accidental padding to open; right edge plus a dot advance to close, after running on to a following bar), and L145 and L169 call on the split element, which `Ending.findRepeatSplitIndex` restricts to `REPEAT_RIGHT` or `REPEAT_LEFT_RIGHT`. The fallback describes a behavior nothing has.

**Two bar categories have no predicate.** A bar that *closes a repeat* — `REPEAT_RIGHT`, `REPEAT_LEFT_RIGHT` — is what `Ending.findRepeatSplitIndex` scans for by identity. A bar that *ends the section a volta bounds* — those two plus `FINAL_DOUBLE_BARLINE`, which nothing can follow and so can only close a bracket — is what `EndingBracketGeometry` L191 lists by identity to decide `hasClosingStroke`, and `EndingBracketGeometryTest` L485 restates as `closesRepeatedSection`. Neither is a stroke-sequence fact (`REPEAT_LEFT_RIGHT` ends its closing side on a thin line), so both are category predicates on `ElementType`, the second containing the first.

**Grace geometry is derived twice.** `computeGraceNoteBoundsSs` L827 scales a `StemmedNotehead.BLACK` anchor by `GRACE_NOTE_SCALE`; `NoteGeometry.GRACE_STEM_UP_SE` L92 does the same arithmetic. Already a register carry-forward to pass 18.

`defaultStaffPosition` **L159 is a structural-element concept declared on every constant.** It is a raw `int` beside `engraving.StaffPosition`, which carries the range. `StructuralElement.getStaffPosition()` L58 answers it for every rest, bar, breath mark and key change and ignores the `staffPosition` field; a note's position comes from its pitch, so the twelve note constants pass a `0` nothing reads. Its other two callers, `StaffElement.applyConversionFrom` L171 and `PreviewElementManager.applyStaffPosition` L914, write it into the field that override never reads — both writes are dead. What reads the value through `getStaffPosition()` for a structural element: `ElementHitGeometry.elementHitRectSs` L80 (every hit rect), `StaffElementRenderer.renderBreathMark` L165, `RestRenderer.calculateRestYSs` L167 (quarter and shorter rests only), `NoteAttachedStacker.seedNoteBounds` L369/L386 and `computeNoteBounds` L337, `ElementColumn.getPositionSs` L436 (and `OpticalSpacing` L224 through it), `SystemStacker.stackAnnotations` L228 and `stackMetronomeAttachment` L261, `StructuralStacker` L485 (a tuplet endpoint that is a rest), `PreviewElementManager.trackMouse` L656.

**Defect: the whole rest has two staff positions.** `SEMIBREVE_REST` declares `-1`; `RestRenderer.calculateRestYSs` L157 draws it at its own `SEMIBREVE_REST_STAFF_POSITION_SP = -2` (the fourth line, where a whole rest hangs) and draws the half rest at `MINIM_REST_STAFF_POSITION_SP = 0`. The glyph is drawn at `-2` while the hit rect and every stacking extent are built at `-1`, half a staff space lower. The comment above `BREATH_MARK` L91 states the single-source rule this breaks; for the breath mark it holds.

`StaffElement.Direction` is nested in a pass-5 type and appears in three `ElementType` signatures (L264, L376, L738). Pass 5 owns where it lives.

`Duration` (49 lines)

Seven constants — whole, dotted half, half, dotted quarter, quarter, dotted eighth, eighth. **The concept is a beat unit: a note value and whether it is dotted.** The representation is a `StaffElement` prototype per constant, built with `ElementType.X.newInstance()` and, for the dotted three, mutated in a static block (L33–40) with `setDotCount(1)` and `setStaffPosition(1)`. `getNote()` L46 clones the prototype on every call.

What consumers actually read off the clone:

| Caller | Reads |
|---|---|
| `Tempo.realTempo` L70 | `getDuration()` — ticks |
| `TupletValidator` L183, L449 | `getDefaultDurationWithDots()`, `getDotCount() == 1` |
| `MetronomeContent.appendNote` | `getType()` → `metronomeGlyphFor`, `getDotCount()` |
| `DurationListCellRenderer` L140 | `getDotCount() > 0` |

Nobody reads the staff position the static block sets. Three tables restate the (value, dotted) pair: the constructor arguments here, `BeatUnitMapping.FORWARD_MAP` (seven rows, `Duration` → MusicXML token + dot count, with a `@Nullable` reverse lookup whose nullity a comment in `DirectionBuilder.addMetronomeNote` calls unreachable), and `DurationListCellRenderer.noteGlyphFor` L61 plus its `duration == QUAVER_DOTTED` special case at L144.

**The name collides with "duration" meaning ticks.** `StaffElement.getDuration()`, `getDefaultDuration()`, `getDefaultDurationWithDots()` are all `int` ticks; `BeatChange(Duration duration, Duration beat)` is two adjacent same-typed components whose first is named for the other meaning.

Fan-in: `Tempo` (record component, `DEFAULT_TYPE`), `BeatChange` (two components, `fromLegacyName`), `BeatAt`, `TempoResolver.beatDefinedAt`, `TupletValidator` (3 signatures), `BeatUnitMapping`, `DirectionBuilder`, `TempoIO` (legacy names, `valueOf`), `StaffElementIO` L318 (`valueOf` ×2), `BeatChangeDialog`, `TempoSection`, `SongSettingsMusicTab`, `TempoChangeDialog`, `DurationListCellRenderer`; five test classes. `getNote()` has 4 production callers listed above.

`ElementLocation` (21 lines)

`record(int lineIndex, int elementIndex)` with a compact constructor rejecting negatives (L10–16) and `matches(int lineIndex, int elementIndex)` L18 — two adjacent same-typed parameters (the register's carry-forward).

- **One producer:** `PreviewElementManager.getHoveredElementLocation` L496–517, from `currentPreviewLine.getLineIndex()` and `currentXIndex`. `currentXIndex` is initialised to and reset to `-1` (L86, L422, L898) — a sentinel. The producer returns `null` first when `!xPosSsMatchesElement`, and `Line.canReplaceElementAt(-1)` answers `true` for an out-of-range index, so the constructor guard is what stands between the `-1` sentinel and a location.
- **One consumer:** `LineInvariants.isElementHovered` L455, which holds `lineIndex` and `elementIndex` as two `int`s and calls `matches`.
- `KeyChangeSite` (pass 0) is `(Line line, int elementIndex, Binding)` — it holds the line itself, not its index, and exists to answer `keyInEffect()`. It is a resolved site, not an address.

`ElementAppearance` and its four arms

Sealed over `NoteheadAppearance(Notehead)`, `GlyphAppearance(SMuFLGlyph)`, `BarAppearance(List<BarStroke>)`, `KeySignatureAppearance()`. Every constant supplies one through the constructor, so the mapping is total (the register's second carry-forward, confirmed).

Consumers outside `ElementType`: `BarRenderer` L109 (`instanceof BarAppearance`) and L132 (a cast on `SINGLE_BARLINE.appearance()`), `RestRenderer` L67 and L139 (`instanceof GlyphAppearance`, after filtering `isRest()` — the breath mark is also a `GlyphAppearance`), `BarAppearanceTest`. `NoteheadAppearance` and `KeySignatureAppearance` are matched only inside `ElementType`.

`BarAppearance`:

- Compact constructor (L56–68) rejects an empty sequence and a dots-only sequence. **Callers:** the six `BarAppearance.of(...)` declarations at L79–95 and the two rejection cases in `BarAppearanceTest`. No runtime input reaches it; the guard catches a typo in an enum declaration, at class-load time.
- `widthSs()` — 1 production caller (`ElementType` L776). `openingAnchorSs()` / `closingAnchorSs()` — 1 each (`ElementType` L320, L335). `strokes()` — 1 (`BarRenderer.drawStrokes` L155). `of(...)` — 6 declarations.
- Its class Javadoc states the volta-anchor rule (dots skipped at both ends, LilyPond's thick-line alignment) and carries an `@invariant` and `@throws`.

### Every `@Nullable`, with its `design.md` answer

| Where | Answer |
|---|---|
| `ElementType.name` L155 | **Never null.** Every constant supplies one. Retire the annotation and the `StatusBar` L239 guard. |
| `ElementType.acceleratorKey` L157 | Honestly partial — but **dead**, with its accessor. Goes with the Swing removal. |
| `ElementType.aliasOf` L161 | Honestly partial, and exists only because aliases exist. Goes with them. |
| `ElementType.instance` L153 (NullAway suppression) | **Constructible before usable.** Assigned by a static block because `createDefaultInstance` reads `this` predicates. |
| `ElementType.getFlagGlyph` L737 | **Honestly partial** by type: whole, half and quarter notes carry no flag. Its two callers already branch on `isNoteWithStem()` / beaming. |
| `ElementType(@Nullable String name, …)` L173, L198 | Constructor parameter; never receives null. Same retirement as the field. |
| `BeatUnitMapping.forDuration` / `forBeatUnit` (outside, `io/musicxml`) | The forward map is total over `Duration` by inspection, and a comment says so; the type does not. Follows from `Duration`'s shape. |

### Every guard, with the callers that can reach it

| Guard | Callers that produce the rejected value |
|---|---|
| `ElementLocation` constructor, negatives L11 | `getHoveredElementLocation` when `currentXIndex == -1` **and** `xPosSsMatchesElement` is true — a state-consistency bug in `PreviewElementManager`, not a legitimate input. |
| `ElementType.categoryNameKey` throw L623 | Only an alias constant. No element's type is ever an alias. |
| `ElementType.validateElementBounds` L909 | None: every input is a total glyph metric or a positive constant. |
| `ElementType.notehead()` cast L406 | A caller that skipped `isNote()`; none does. |
| `ElementType.stemmedNotehead()` cast L417 | A caller that skipped `isNoteWithStem()`; none does. |
| `BarAppearance` constructor L59, L63 | An enum declaration typo, at class load. |
| `TupletValidator` L683 `getDefaultDuration() == 0` (outside) | Nothing: `isPitchedNote()` already excludes every zero. |
| `StatusBar` L239 `name == null` (outside) | Nothing. |
| `DirectionBuilder.addMetronomeNote` `beatUnit == null` (outside) | Nothing, and its own comment says so. |
| `MetronomeContent.metronomeGlyphFor` default throw (outside) | Any non-note type; its one caller passes `Duration.getNote().getType()`, always a note. |

### Fan-in per public member

Production call sites, excluding Javadoc links and `ElementType`'s own body.

| Member | Fan-in | Where |
|---|---|---|
| `newInstance()` | ~30 | `dom` ×6, `io` ×7, `layout` ×1, `ui` ×8, test helpers |
| `getInstance()` | 2 | `InsertionSpacingCalculator` |
| `isGraceNote()` | ~50 | everywhere |
| `isRepeat()` / `isBarLine()` | 27 / 26 | 17 of them the pair `isBarLine() \|\| isRepeat()` |
| `isPitchedNote()` | 24 | `dom` spans, selection, layout, midi |
| `isRest()` | 20 | |
| `isNote()` | 19 | |
| `isDuration()` | 16 | `Ending` ×6, actions, `MeasureBuilder` |
| `isNoteWithStem()` | 15 | layout, renderer, `Tie` |
| `isValidSongTerminal()` | 11 | `TerminalMaintainer` ×6, `Line`, `FormatMigrator`, `ElementTypeAction` ×2, `LayoutEngine` |
| `isBreathMark()` | 9 | |
| `isKeyChange()` | 8 | |
| `GRACE_NOTE_SCALE` | 8 | `NoteGeometry` ×6, `ElementColumnBuilder`, `RenderingUtils` |
| `isBeamable()` | 7 | |
| `getElementWidthSs()` | 13 | layout ×9, renderer ×3, `StaffElement` |
| `getNoteheadTopOffsetSs()` | 6 | |
| `getNoteheadBottomOffsetSs()` | 4 | |
| `appearance()` | 3 | `BarRenderer` ×2, `RestRenderer` ×2 |
| `notehead()` / `stemmedNotehead()` | 4 / 2 | `NoteGeometry`, `StaffElementRenderer`, `NoteColumnGeometry` |
| `isNonContentElement()` | 3 | `Ending`, `MusicEditOperations` ×2 |
| `getDefaultDuration()` / `getDefaultStaffPosition()` | 3 / 3 | |
| `getFlagGlyph()` | 2 | `NoteGeometry`, `StaffElementRenderer` |
| `isNonDuration()`, `cancelsAccidentals()`, `bearsSyllableText()`, `isEndingTerminal()`, `isReplaceableByTerminal()`, `categoryName()`, `toNote()`, `voltaOpeningXOffsetSs()`, `voltaClosingXOffsetSs()` | 2 each | |
| `interruptsLyricChain()`, `canCarryAnnotation()`, `categoryNameKey()`, `toRest()`, `drawStaveLongitude()`, `getElementHeightSs()`, `getTopYOffsetSs()`, `getElementCenterXSs()`, `getFullElementHeightSs()`, `getFullElementWidthSs()`, `terminalFlushRightXSs()`, `getName()` | 1 each | |
| `snapToEnd()`, `getFullElementCenterXSs()`, `getAcceleratorKey()`, `getMenuShortcutKeyMask()` | **0** | |
| `Duration.getNote()` | 4 | table above |
| `ElementLocation.matches` | 1 | `LineInvariants` |
| `BarAppearance.widthSs/openingAnchorSs/closingAnchorSs/strokes` | 1 each | |

### Existing `docs/*.md` that cover this target

None describes it. What exists around it:

- `docs/layout-geometry.md` — _Column extent_ and _Stem-tip height_ describe what the bounds fields feed, in layout's terms.
- `smufl/package-info.java` (pass 2) — `Notehead`, `StemmedNotehead`, `WholeNotehead`: what `NoteheadAppearance` carries.
- `engraving/package-info.java` (pass 3) — `BarStroke`: what `BarAppearance` carries.
- `docs/lyrics.md` — the promise `interruptsLyricChain()` and `bearsSyllableText()` serve.
- `docs/key-changes.md` — `KEY_CHANGE` and `KeySignatureAppearance`.
- `docs/song-tempo.md` — what `Duration` is for.

### Test classes

| Class | Cases | Covers |
|---|---|---|
| `BarAppearanceTest` (the only one over the target) | 9 | 1 exhaustiveness check (rows = every bar/repeat type); 6 rows of declared stroke sequence → width, opening anchor, closing anchor; 2 rows pinning the constructor guard. |
| `KeySignatureExtentTest` (pass 0) | 1 of its cases | `KEY_CHANGE.getFullElementWidthSs()` is the narrowest accidental. |
| `EndingBracketGeometryTest` (pass 18) | via helpers | volta offsets through `ElementType`. |
| `LyricRunTest` (pass 9) | class-level | `interruptsLyricChain()` through `LyricRun`. |

---

## Step 2 — What it proposes

### Question 1: does each concept have exactly one type?

Three concepts have none, and one type is two concepts.

**P1 —** <span id="rd-c1">**Category gets a type**</span>**.** A `Kind` enum — `NOTE`, `GRACE_NOTE`, `REST`, `BARLINE`, `REPEAT`, `BREATH_MARK`, `KEY_CHANGE` — supplied through every constant's constructor the way `ElementAppearance` already is. The ordinal-range predicates and the identity lists become `kind == X`; `categoryNameKey()`, `createDefaultInstance()` and `OpNames.categoryOf` become exhaustive switches over it with no throw and no fall-through; the L97 comment defending `KEY_CHANGE`'s position is deleted because position stops meaning anything. The existing predicates stay as the public vocabulary (50 callers of `isGraceNote()` do not change), but each is one comparison, and a new kind fails every switch that has to account for it. Recommended.

**P2 —** <span id="rd-c2">**"Bar" gets a predicate**</span>**.** `isBar()` — barline or repeat — replaces the 17 hand-written pairs, and it is the same set `BarAppearance` names, so a caller that needs the strokes matches the appearance and one that needs only the category asks `isBar()`. Recommended.

**P3 —** <span id="rd-c3">**Note value gets a type**</span>**.** A `NoteValue` enum — whole, half, quarter, eighth, sixteenth, thirty-second — that each note and rest constant carries, with `note()` and `rest()` on it. That makes `toRest()` / `toNote()` one lookup instead of two switches, deletes the `name() + "_REST"` string pairing in `EditModeManager`, and gives `NoteTypeMapping`, `metronomeGlyphFor` and `Duration` a key that is exactly their domain rather than a 31-value enum they each throw on. Ticks belong here: `defaultDuration` and its `0` sentinel retire, and `getDefaultDuration()` answers from the value for a type that has one. Recommended; it is what P4 is built on.

**P4 —** <span id="rd-c4">`Duration` **is a beat unit, and stops being a prototype element**</span>**.** Its seven constants become `(NoteValue, dotted)` — an enum still, since the domain is closed, with `noteValue()`, `dotted()` (or `dotCount()`) and `ticks()` on it. `getNote()` and its clone-per-call go: `Tempo.realTempo` asks `ticks()`, `TupletValidator` asks `ticks()` and `dotted()`, `MetronomeContent` and `DurationListCellRenderer` ask `noteValue()` and `dotted()`. `BeatUnitMapping`'s seven-row table and its `@Nullable` reverse become a derivation from the value and the dot count through `NoteTypeMapping`, which already maps a value to its token. `DurationListCellRenderer.noteGlyphFor` and its `QUAVER_DOTTED` special case become a lookup on the value. Recommended. **Its name is an open question** (below).

**P5 —** <span id="rd-c5">**the IO aliases leave the enum**</span>**.** The 14 alias constants, `aliasOf`, the alias constructor, both copy loops, `copyBoundsFrom` and the three `aliasOf != null` skips are deleted. `StaffElementIO` gains a `Map<String, ElementType>` of legacy names, on the pattern `TempoIO.LEGACY_TEMPO_DURATION_NAMES` already sets, consulted before `valueOf`. `values()` then holds only real types, and every category predicate is total over it. Recommended. This touches pass 17's package (`io`), in the one method that reads the names; the map is the same kind of change that method already makes for `VERTICALLINE` and `GRACE_SEMIQUAVER`.

**P6 —** <span id="rd-c6">**Swing leaves the model**</span>**.** `keyCode`, `modifiers`, `acceleratorKey`, `getAcceleratorKey()`, `getMenuShortcutKeyMask()`, the four-argument-longer constructor overload and the four imports are deleted; the actions already declare the keystrokes they use. Recommended.

**P7 —** <span id="rd-c7">**the three dead members are deleted**</span>**:** `snapToEnd()`, `getFullElementCenterXSs()`, and `getAcceleratorKey()` (covered by P6). Recommended.

**P8 —** <span id="rd-c8">`getInstance()` **is retired**</span>**.** Its two callers take `newInstance()`; the shared prototype stops being reachable from outside the enum, so nothing can mutate what every later element is cloned from. Recommended.

**P9 —** <span id="rd-c9">`name` **stops being** `@Nullable`</span>, and the `StatusBar` guard goes with it. Whether the literal should be a `Strings` key is an open question (below).

<span id="rd-c10">**P10**</span> **—** `ElementLocation` **keeps its concept and loses** `matches`**.** It is an address — line index, element index — and `KeyChangeSite` is not: it holds the `Line` and a binding, and exists to answer a key. They do not merge. `LineInvariants.isElementHovered` builds an `ElementLocation` from its two ints and compares with `equals`, so the transposable pair is gone and the record's own equality is the comparison. The constructor guard goes with P12, which removes the only producer of a negative index.

**P12 — the preview hover is one value.** `PreviewElementManager` holds the hover as five static fields — `currentPreviewLine`, `currentXIndex`, `currentStaffPosition`, `xPosSsMatchesElement`, `yPosSpMatchesElement` — written together in `trackMouse` (L689–693), compared field by field (L676–680), and invalidated one at a time. `restorePreviewElement` L897 sets `currentXIndex = -1` alone, to defeat the no-change return at L676, and leaves the other four standing; `trackMouse` then has two early returns (no resolvable staff position, `canInsertElementAt` refused) that clear state only when `currentPreviewLine == lc`, and `restorePreviewElement` is called with other lines (`GraceModeManager.enterGraceNoteInsert`, `NoteDragHandler.handleRelease`). In that state `getHoveredElementLocation` L512 passes `-1` through `canReplaceElementAt`, which answers true out of range, and L516 constructs `ElementLocation(lineIndex, -1)` during paint. The five fields become one `@Nullable` record — line component, insertion index, staff position, the two match flags — where null is "no hover": `restorePreviewElement` and `clearPreviewElement` set it to null, the no-change test is one `equals`, `getHoveredElementLocation` answers null for a null hover and otherwise builds a location from an index that was never negative, and `-1` has no field to live in. `ElementLocation` then carries no guard: its producer is the invariant's owner. Fixed in this pass.

**P11 — a glyph's seat lives on** `GlyphAppearance`**.** `GlyphAppearance` becomes `(SMuFLGlyph glyph, StaffPosition seat)`: a lone glyph is drawn at one staff position, and the seven glyph constants — six rests and the breath mark — are exactly the ones whose position is data (the whole rest at `-2`, the breath mark at `-5`, the rest at the middle line). A bar and a key signature span the staff, so their position is the staff center by definition; a notehead's comes from the element. `StructuralElement.getStaffPosition()` becomes a switch over the appearance — the seat for a glyph, the center otherwise. `defaultStaffPosition` and `getDefaultStaffPosition()` leave `ElementType`, the two dead writes in `applyConversionFrom` and `applyStaffPosition` go, and `RestRenderer` draws every rest from `getStaffPosition()`, deleting its two constants and two identity branches. `engraving.StaffPosition` types the seat; this is the first producer of it, ahead of pass 5's `StaffElement.staffPosition`.

### Question 2: <span id="rd-c11">what varies, and what is deliberately not abstracted</span>

- **How a type is drawn, and for a lone glyph where** — varies over four shapes — notehead, lone glyph, stroke sequence, key-dependent — and `ElementAppearance` is shaped around it. The glyph arm gains its seat (P11); the other three are unchanged.
- **What category a type is** varies over seven kinds (P1), and **which note value it has** over six (P3). Both become closed enums carried by the constant.
- **Not abstracted:** `ElementType` **stays one flat enum.** A sealed interface over per-kind enums was considered (fork C below) and is not proposed. `StaffElement. getType()` is the identity some 200 call sites switch on, `EnumMap` and `values()` are used over it, and it is what MusicXML and the legacy reader resolve to. `Kind` and `NoteValue` give every switch its exhaustiveness without changing what an element's type is.
- **Not abstracted: no hierarchy over** `ElementType`**'s predicates.** The behavioural predicates (`cancelsAccidentals`, `interruptsLyricChain`, `isEndingTerminal`, `isValidSongTerminal`, …) remain predicates on the one enum, each defined once. They are the domain's rules about categories, and a `Kind` switch inside each is the right shape for them.

### Question 3: which type owns each uncarried invariant

| Invariant | Owner after this step | Step 3 does |
|---|---|---|
| A type is exactly one of seven categories | `ElementType.Kind` | closed enum (P1) |
| A note and its rest share one value | `NoteValue` | closed enum (P3) |
| A beat unit is a value and a dot | `Duration` (renamed?) | enum over `(NoteValue, dotted)` (P4) |
| A type has ticks only when it has a value | `NoteValue.ticks()` | retires the `0` sentinel |
| Bounds are positive | `SMuFLMetadata` totality (pass 2) | retires `validateElementBounds` |
| Bounds are computed once and never change | an immutable bounds value on the constant | replaces eight mutable fields |
| `name` is present | the field's type | retires `@Nullable` |
| A location's indices are non-negative | `PreviewElementManager`'s hover value | nullable hover record replaces the five fields and the `-1` sentinel (P12); `ElementLocation`'s guard retires |
| A bar draws at least one line | `BarAppearance` constructor | stays: a closed type over stroke sequences is not possible |
| A glyph element is drawn, hit-tested and stacked at one staff position | `GlyphAppearance.seat` | the seat on the appearance (P11); `RestRenderer`'s own constants go |

### The architecture gate

Two structural changes are large enough to deserve a fork each.

**Fork A — where category lives. Resolved: A1.**

- _A1 —_ `Kind` _enum on the constant (P1)._ Buys: every category switch exhaustive; declaration order meaningless; 50-caller predicates unchanged. Costs: the predicates remain a parallel vocabulary to the enum, and a caller can still compare against one member of a multi-member category (`type == SEMIBREVE_REST` where `isRest()` was meant). No shape closes that while the constants are nameable, and they must be: `KeyChangeElement`'s constructor, `NoteTypeMapping`'s map keys, `MetronomeContent`'s switch arms, the action declarations and every `newInstance()` site name one. A facade class over a private enum publishes the same named values and loses `switch` exhaustiveness, `EnumMap` and `valueOf` at those sites. The one production identity comparison today, `ElementTypeAction.appliesTo` L262, is against a single-member category and is the same test as `isBreathMark()`. `ElementTypeAction` already nests its own `Kind` (`DURATION`, `NON_DURATION`); one of the two is renamed in step 4.
- _A2 — keep predicates, fix only the ordinal ranges._ Buys: the smallest change. Costs: category is still re-derived by if-chain in three places, and the next kind of element is a fourth chain and a reachable throw.

<span id="rd-c13">**Fork B — what** `Duration` **becomes**</span>**.**

- _B1 — enum over_ `(NoteValue, dotted)` _(P4)._ Buys: closed domain, `valueOf` still works for both file readers, `EnumMap` keys still work, combos still take `values()`. Costs: seven constants still declared by hand; a dotted 16th is a new constant rather than a new combination.
- _B2 —_ `record BeatUnit(NoteValue value, int dotCount)`_._ Buys: any beat unit is expressible. Costs: the file readers, `TempoIO`'s legacy table and `BeatChange.fromLegacyName` all resolve a _name_, and the combos enumerate a closed list, so the record needs a static list of the seven and a name mapping beside it — the enum, reconstructed by hand.

**Fork C — considered, not proposed:** `ElementType` **as a sealed interface over per-kind enums.** Buys: a rest type and a note type stop sharing a method namespace, so `notehead()` cannot be called on a rest. Not proposed for two reasons, neither of them the number of sites the change touches. First, an element's type is one closed identity, and an enum is the type for that: under a sealed interface "every type" becomes a computed union, a name resolves through two lookups, and a switch is exhaustive over kinds but never over constants. That is what the shape gives up permanently, and `Kind` and `NoteValue` (P1, P3) recover the exhaustiveness without giving it up. Second, what C prevents is a placement fault, not an identity fault: `notehead()` is callable on a rest because the accessor sits on `ElementType` and casts, and moving it to `NoteheadAppearance` (open question 5) removes the cast without changing what an element's type is. Stated so its absence can be approved.

```
ElementType ──kind──▶ Kind {NOTE, GRACE_NOTE, REST, BARLINE, REPEAT, BREATH_MARK, KEY_CHANGE}
     │
     ├──value──▶ NoteValue {WHOLE … THIRTY_SECOND}   (note and rest constants only)
     │               ▲   note() / rest() / ticks()
     │               │
     │           Duration (beat unit) = NoteValue × dotted
     │
     └──appearance──▶ ElementAppearance = Notehead | Glyph | Bar(strokes) | KeySignature
                                              ▲
                                    BarRenderer, RestRenderer match here
```

### Open questions

1. <span id="rd-c14">**What is** `Duration` **called**</span>**?** Its meaning is a beat unit — the note value a tempo counts in and a metric modulation relates. `Duration` collides with `getDuration()` (ticks) throughout `StaffElement`, and `BeatChange(Duration duration, Duration beat)` names its first component for the wrong meaning. `BeatUnit` is the MusicXML word (`<beat-unit>`) and `docs/song-tempo.md`'s word; `NoteValue` is taken by P3. The rename reaches pass 11's types (`Tempo`, `BeatChange`, `BeatAt`, `TempoResolver`), `io`, and four dialogs. I lean to `BeatUnit`; this is yours.
2. <span id="rd-c15">**Is** `name` **a** `Strings` **key**</span>**?** "Whole note" reaches the user through the status bar, lower-cased and capitalised by `StatusBar.getDurationText`. The strings guide puts user-facing text in `strings.properties`. The alternative is to derive the label from `Kind` and `NoteValue` (both of which would have a label key) and delete the field. I lean to deleting the field; this is a strings question you own.
3. <span id="rd-c16">**Does** `Kind` **distinguish grace note from note**</span>**?** `GRACE_QUAVER` is a `NoteElement` with no ticks; some callers treat it as a note (`isNote()`), most treat it separately. I propose `GRACE_NOTE` as its own kind with `isNote()` answering true for both. Once ticks move to `NoteValue` and the glyph seat to `GlyphAppearance` (P11), no per-constant data remains on the constructor beyond name, kind, value and appearance.
4. **The** `-1` **sentinel on** `PreviewElementManager.currentXIndex`**. Resolved: P12, in this pass.** The hover becomes one nullable record and `ElementLocation` carries no guard.
5. **The volta anchors. Resolved: they do not belong on** `ElementType`**.** An ending may open or close on a note, but each call site that asks for an anchor has already established it holds a bar — by testing the category, or by holding the split, which is always a repeat — and a note anchor is computed by a different formula in layout. The anchors are a function of the stroke sequence — where the last drawn line is, how thick it is — which is `BarAppearance`'s own data. The two `ElementType` wrappers and their `0` fallback are deleted, and `EndingBracketGeometry` reads the anchors off the `BarAppearance` it matches. `notehead()` and `stemmedNotehead()` are the same fault: each casts `appearance` on behalf of a caller that already knows it holds a note, so the notehead belongs on `NoteheadAppearance` and the four callers (`NoteGeometry`, `StaffElementRenderer`, `NoteColumnGeometry`) match the appearance the way `BarRenderer` does, which retires both casts. Step 4 places both; neither changes the class set.

### Findings raised for later steps or other passes

- **Pass 5:** `StaffElement.staffPosition` is a raw `int` beside `engraving.StaffPosition`, and after P11 the `GlyphAppearance` seat is already typed; `StaffElement.Direction` is used by three `ElementType` signatures and is not pass 5's to keep nested if pass 4 states contracts against it.
- **Pass 11:** `BeatChange(Duration duration, Duration beat)` — two adjacent same-typed components; resolved in part by open question 1's rename, but the record's own shape is pass 11's.
- **Pass 17:** `StaffElementIO.startElement11` catches `NullPointerException` around `valueOf` to report a corrupt document; P5's legacy-name map lands in that method.
- **Pass 18:** `metronomeGlyphFor(ElementType)` in `MetronomeContent` throws for a non-note; with P3 it takes a `NoteValue` and the throw goes.
- **Pass 28 / strings:** `ElementType.name` literals (open question 2).
- **Step 3:** the guards table above; the whole-rest position defect (drawn at `-2`, hit-tested and stacked at `-1`), fixed by P11; the stale-hover defect in `PreviewElementManager` (a negative index reaching `ElementLocation` during paint), fixed by P12.
- **Step 4:** the volta anchors (open question 5); the two bar-category identity lists — closes a repeat (`Ending.findRepeatSplitIndex`) and ends a volta-bounded section (`EndingBracketGeometry` L191, `EndingBracketGeometryTest` L485) — become two predicates on `ElementType` under P1; `GRACE_NOTE_SCALE`'s eight `NoteGeometry` uses and the duplicated grace-anchor scaling (already pass 18's).
- **Step 6:** `BarAppearanceTest`'s two rejection rows pin a guard reached only by a declaration typo.

---
roughdraft: "1.0"
comments:
  rd-c1:
    body: Agreed
    by: user
    at: "2026-09-04T04:33:21.929Z"
  rd-c2:
    body: agreed
    by: user
    at: "2026-09-04T04:34:02.712Z"
  rd-c3:
    body: agreed
    by: user
    at: "2026-09-04T04:34:36.009Z"
  rd-c4:
    body: agreed
    by: user
    at: "2026-09-04T04:50:57.216Z"
  rd-c5:
    body: agreed
    by: user
    at: "2026-09-04T04:51:21.399Z"
  rd-c6:
    body: agreed
    by: user
    at: "2026-09-04T04:51:47.640Z"
  rd-c7:
    body: agreed
    by: user
    at: "2026-09-04T04:51:59.003Z"
  rd-c8:
    body: agreed
    by: user
    at: "2026-09-04T04:52:21.522Z"
  rd-c9:
    body: agreed
    by: user
    at: "2026-09-04T04:52:53.111Z"
  rd-c10:
    body: agreed
    by: user
    at: "2026-09-04T04:53:57.468Z"
  rd-c11:
    body: agree with all
    by: user
    at: "2026-09-04T04:55:18.210Z"
  rd-c13:
    body: B1
    by: user
    at: "2026-09-04T05:17:45.519Z"
  rd-c14:
    body: BeatUnit
    by: user
    at: "2026-09-04T05:24:34.815Z"
  rd-c15:
    body: yes, it should be
    by: user
    at: "2026-09-04T05:25:18.332Z"
  rd-c16:
    body: yes
    by: user
    at: "2026-09-04T05:26:33.023Z"
  rd-c17:
    body: Noted. Step 3 builds `Kind` on the constant (A1 as resolved above).
    by: AI
    at: "2026-09-04T06:05:53.000Z"
    re: rd-c1
  rd-c18:
    body: Noted. `isBar()` lands in step 3 with `Kind`; the 17 call sites are re-pointed there.
    by: AI
    at: "2026-09-04T06:05:53.000Z"
    re: rd-c2
  rd-c19:
    body: Noted. `NoteValue` lands in step 3, carrying ticks; the `0` sentinel and both `toRest()`/`toNote()` switches go with it.
    by: AI
    at: "2026-09-04T06:05:53.000Z"
    re: rd-c3
  rd-c20:
    body: Noted. Built as B1, under the name `BeatUnit` (rd-c14).
    by: AI
    at: "2026-09-04T06:05:53.000Z"
    re: rd-c4
  rd-c21:
    body: Noted. The legacy-name map lands in `StaffElementIO`, beside its existing `VERTICALLINE` / `GRACE_SEMIQUAVER` handling.
    by: AI
    at: "2026-09-04T06:05:53.000Z"
    re: rd-c5
  rd-c22:
    body: Noted. Before deleting, step 3 confirms each action declares its own keystroke and none reads `getAcceleratorKey()`; fan-in already says 0.
    by: AI
    at: "2026-09-04T06:05:53.000Z"
    re: rd-c6
  rd-c23:
    body: Noted.
    by: AI
    at: "2026-09-04T06:05:53.000Z"
    re: rd-c7
  rd-c24:
    body: Noted. `InsertionSpacingCalculator`'s two calls take `newInstance()`.
    by: AI
    at: "2026-09-04T06:05:53.000Z"
    re: rd-c8
  rd-c25:
    body: Noted. The field's shape follows rd-c15.
    by: AI
    at: "2026-09-04T06:05:53.000Z"
    re: rd-c9
  rd-c26:
    body: Noted. `matches` goes; `LineInvariants.isElementHovered` compares with `equals`. The guard retires with P12.
    by: AI
    at: "2026-09-04T06:05:53.000Z"
    re: rd-c10
  rd-c27:
    body: Noted. `ElementType` stays one flat enum; no hierarchy over its predicates.
    by: AI
    at: "2026-09-04T06:05:53.000Z"
    re: rd-c11
  rd-c28:
    body: B1 it is — an enum over `(NoteValue, dotted)`.
    by: AI
    at: "2026-09-04T06:05:53.000Z"
    re: rd-c13
  rd-c29:
    body: "`BeatUnit` it is. The rename goes through `jet_brains_rename` in step 3 and reaches `Tempo`, `BeatChange`, `BeatAt`, `TempoResolver`, `TupletValidator`, `BeatUnitMapping`, `DirectionBuilder`, `TempoIO`, `StaffElementIO`, the four dialogs and `DurationListCellRenderer`. Prose in `plans/` and `docs/` that names the old name is checked afterwards and restored where it records history."
    by: AI
    at: "2026-09-04T06:05:53.000Z"
    re: rd-c14
  rd-c30:
    body: "I read this as: keep a label on each constant, and make it a `Strings` key rather than a literal — not derive it from `Kind` and `NoteValue`. So the field becomes non-null, holds a key, and `getName()` resolves it through `Strings`. Please confirm that reading, or say if the derived form was meant."
    by: AI
    at: "2026-09-04T06:05:53.000Z"
    re: rd-c15
  rd-c31:
    body: Noted. `Kind.GRACE_NOTE` is its own member; `isNote()` answers true for `NOTE` and `GRACE_NOTE`, `isPitchedNote()` for `NOTE` only.
    by: AI
    at: "2026-09-04T06:05:53.000Z"
    re: rd-c16
  rd-c32:
    body: "P11 (glyph seat on `GlyphAppearance`), P12 (one nullable hover record in `PreviewElementManager`), Fork C's absence and open questions 4 and 5 carry no mark. I am reading them as accepted as written. Say so if any of them is not."
    by: AI
    at: "2026-09-04T06:05:53.000Z"
    scope: document
  rd-c33:
    body: "Settled at the terminal: derive the label. The `name` field and `getName()` are deleted; `Kind` and `NoteValue` each carry a `Strings` key and the status-bar label is composed from them."
    by: AI
    at: "2026-09-04T06:20:00.000Z"
    re: rd-c30
  rd-c34:
    body: "Settled at the terminal: all accepted as written."
    by: AI
    at: "2026-09-04T06:20:00.000Z"
    re: rd-c32
