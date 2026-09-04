# Design pass 4 — step 3: make illegal states unrepresentable

Target: `dom/Duration`, `ElementType`, `ElementLocation`, `ElementAppearance`, `NoteheadAppearance`, `GlyphAppearance`, `BarAppearance`, `KeySignatureAppearance`. Line numbers are against commit `48fde252`, the tree as it stands; step 2 changed nothing. Every item below is one of the ten proposals step 2 settled, taken to the concrete structure that carries it. All are approved; this is the specification the sweep implements.

---

## The yield

| | Count | Which |
|---|---|---|
| **Guards retired** | 7 | `ElementLocation` constructor L10–16 · `ElementType.categoryNameKey` throw L623 · `ElementType.validateElementBounds` L909–922 · `TupletValidator.buildNotatableByTicks` `getDefaultDuration() == 0` L683 · `StatusBar.getDurationText` `name == null` L239 · `DirectionBuilder.addMetronomeNote` `beatUnit == null` · `MetronomeContent.metronomeGlyphFor` default throw |
| **`@Nullable` and suppressions retired** | 6 | `ElementType.name` L154 · `ElementType.acceleratorKey` L156 · `ElementType.aliasOf` L160 · the constructor's `@Nullable String name` L173/L198 · the NullAway suppression on `ElementType.instance` L152 · `BeatUnitMapping.forDuration` |
| **Methods introduced** | 1 | `ElementType.label()`, the one place that composes a type's display words |
| **Sentinels retired** | 2 | `-1` in `PreviewElementManager.currentXIndex` (L86, L421, L897) · `0` as "no ticks" in `ElementType.defaultDuration` for every non-duration constant |
| **Types introduced** | 4 | `ElementType.Kind` · `NoteValue` · `ElementBounds` (a record replacing eight mutable fields) · `PreviewHover` (a record replacing five static fields) |
| **Types reshaped** | 2 | `Duration` → `BeatUnit`, an enum over `(NoteValue, dotted)` instead of a `StaffElement` prototype · `GlyphAppearance` gains its seat |
| **Types deleted from `values()`** | 14 | the IO alias constants |

Every guard retired is a runtime check the compiler makes instead, or a branch nothing could reach. `BarAppearance`'s constructor guard (L56–68) stays: a closed type over stroke sequences is not possible, and the guard is the constructor validation the order of attack allows last.

Two guards are **not** in the count because they are step 4's: `notehead()` L406 and `stemmedNotehead()` L417 are casts made on behalf of callers that already hold a note. They retire when the notehead moves to `NoteheadAppearance` and the four callers match the appearance — a placement, which step 4 proposes.

---

## 1 — Closed enums

### 3.1 `Kind` on the constant (P1, P2)

**Found.** Category is read off declaration order at L420–426, L436–441, L455–460, re-derived by if-chain at L224–233, L598–624 and `OpNames.categoryOf` L82–103, and listed by identity at L443–453, L494–500, L545–547, L642–644. The comment at L97–99 exists to defend `KEY_CHANGE`'s position. `isBarLine() || isRepeat()` is hand-written at 17 sites.

**Proposed.**

```java
public enum Kind {
    NOTE(Strings.LABEL_ELEMENT_CATEGORY_NOTE),
    GRACE_NOTE(Strings.LABEL_ELEMENT_CATEGORY_GRACE_NOTE),   // new key
    REST(Strings.LABEL_ELEMENT_CATEGORY_REST),
    BARLINE(Strings.LABEL_ELEMENT_CATEGORY_BARLINE),
    REPEAT(Strings.LABEL_ELEMENT_CATEGORY_REPEAT),
    BREATH_MARK(Strings.LABEL_ELEMENT_CATEGORY_BREATH_MARK),
    KEY_CHANGE(Strings.LABEL_ELEMENT_CATEGORY_KEY_CHANGE);

    private final String labelKey;
    public String labelKey() { ... }
}
```

Nested in `ElementType`, supplied through every constant's constructor as `ElementAppearance` already is, held in a `private final Kind kind`. Then:

- `isPitchedNote()` → `kind == NOTE`; `isGraceNote()` → `kind == GRACE_NOTE`; `isRest()` → `kind == REST`; `isBarLine()` → `kind == BARLINE`; `isRepeat()` → `kind == REPEAT`; `isBreathMark()` → `kind == BREATH_MARK`; `isKeyChange()` → `kind == KEY_CHANGE`. `isNote()` stays `isPitchedNote() || isGraceNote()`. The predicates remain the public vocabulary, so no caller of them changes.
- **New:** `isBar()` → `kind == BARLINE || kind == REPEAT`. The 17 hand-written pairs take it: `Ending` ×5, `EndingBracketGeometry` ×4, `StaffElementRun` L149, `KeyChangeElement` L111, `TupletValidator` L585, `Line` L1853, `StaffElementRenderer` L104, `ElementTypeAction.appliesTo` L272, `BarRenderer` L88, `LayoutEngine` L901, `MeasureMapper` L427, `MeasureBuilder` L307, `MusicEditOperations` L910, `CautionaryKeySignature` L161. `isNonDuration()` L466 becomes `isBar() || isBreathMark() || isKeyChange()`; `cancelsAccidentals()` L487 becomes `isBar() || isKeyChange()`. `isNonContentElement()` L642 writes `isBreathMark()` in place of `this == BREATH_MARK`.
- `categoryNameKey()` L598–624 becomes `kind.labelKey()`; the throw goes, and the `@throws` on it and on `categoryName()` L582 go with it.
- `createDefaultInstance()` L224–234 and `OpNames.categoryOf` L82–103 become exhaustive `switch (kind)` with no fall-through; `OpNames.categoryOf` stops being `@Nullable`, and its caller's null branch goes. Whether `OpNames.Category` survives as a separate enum or `OpNames` switches on `Kind` directly is step 4's placement question; here it is only made exhaustive.
- The comment at L97–99 is deleted; the constants stay in the order they are in, which now means nothing.

`ElementTypeAction` nests its own `Kind` (`DURATION`, `NON_DURATION`). The two names collide only in prose — one is `ElementType.Kind`, the other `ElementTypeAction.Kind` — and step 4 renames one.

### 3.2 `NoteValue` (P3)

**Found.** Whole through thirty-second exist twice in the enum, paired by hand at L662–689 (`toRest`/`toNote`) and by string at `EditModeManager.makePreviewElement` L316 (`valueOf(name() + "_REST")`). Ticks are a constructor argument per constant (L47–100), `0` for every non-duration type, and read back through `getDefaultDuration()` L380 whose two outside callers are `TupletValidator` L110 and L683 (the second guarding against a `0` that `isPitchedNote()` already excludes). `NoteTypeMapping.FORWARD_MAP` keys 13 `ElementType` constants to six tokens; `MetronomeContent.metronomeGlyphFor` switches on six constants and throws on the other 25; `DurationListCellRenderer.noteGlyphFor` L61 pairs `Duration` constants by hand.

**Proposed.** A top-level `dom.NoteValue`:

```java
public enum NoteValue {
    WHOLE(PPQ * 4, Strings.LABEL_NOTE_VALUE_WHOLE),
    HALF(PPQ * 2, ...), QUARTER(PPQ, ...), EIGHTH(PPQ / 2, ...),
    SIXTEENTH(PPQ / 4, ...), THIRTY_SECOND(PPQ / 8, ...);

    public int ticks();
    public String labelKey();
    public ElementType note();   // SEMIBREVE … DEMI_SEMIQUAVER
    public ElementType rest();   // SEMIBREVE_REST … DEMI_SEMIQUAVER_REST
}
```

`note()` and `rest()` are switches over `this`, exhaustive by construction; they live on `NoteValue` rather than as a map because the enum is the closed domain and a switch is the total function over it. Six new `Strings` keys, `label.note.value.whole` … `label.note.value.thirty.second`, serve `ElementType.label()` in 3.6 below.

On `ElementType`: a `private final @Nullable NoteValue value`, supplied by the thirteen note, rest and grace-note constants and absent from the other twelve, read through `public @Nullable NoteValue noteValue()`. The nullability is **honestly partial** in `design.md`'s sense — a barline has no note value, and every caller below already knows which kind it holds. `GRACE_QUAVER` carries `EIGHTH`: that is what draws its flag (L741), what `NoteTypeMapping` writes for it (`TYPE_EIGHTH`), and what makes it a _quaver_; its ticks are a `Kind` fact, not a value fact.

- `getDefaultDuration()` L380 becomes: `value != null && kind != GRACE_NOTE ? value.ticks() : 0`. The `0` for a barline or a grace note is the domain answer — the element occupies no time — not a sentinel; `StaffElement.getDefaultDuration()` L261 and every MIDI and tuplet sum through it rely on exactly that. The `defaultDuration` field and its constructor argument go.
- `toRest()` / `toNote()` L662–689 become `value == null ? this : value.rest()` (resp. `note()`), keeping today's "or `this`" contract that `ElementTypeAction.matchesElement` L284 relies on.
- `EditModeManager.makePreviewElement` L316: `elementType.noteValue().rest()` inside the `isPitchedNote()` branch it already sits in — the string concatenation and the `valueOf` go.
- `TupletValidator.buildNotatableByTicks` L679–693 iterates `NoteValue.values()` instead of filtering `ElementType.values()`; the `== 0` guard goes. `SHORTEST_SPAN_TICKS` L110 becomes `NoteValue.THIRTY_SECOND.ticks()`.
- `NoteTypeMapping.FORWARD_MAP` keys on `NoteValue` (six rows, not thirteen), and `typeToken(ElementType)` becomes `typeToken(NoteValue)`; its `@Nullable` is then a decision for the callers that pass a type — they hold the `noteValue()` null instead, at the point where they already branch on kind. `REVERSE_NOTE_MAP` / `REVERSE_REST_MAP` become `forTypeToken(token).note()` / `.rest()` over one reverse map. This is pass 17's package; the change is confined to that class's tables.
- `MetronomeContent.metronomeGlyphFor(ElementType)` becomes `metronomeGlyphFor(NoteValue)`, an exhaustive switch with no default. `DurationListCellRenderer.noteGlyphFor(Duration)` becomes a switch over `NoteValue`. Two glyph tables keyed on the same six values remain — one chooses `MET_NOTE_*`, the other `NOTE_*`, and each is its renderer's presentation choice, not the value's.

**A name collision.** `TupletValidator` already nests a `private record NoteValue(ElementType type, int dots)` L116–119, L665, L679–698: a _notatable_ value — a note type at a dot count. It is renamed `NotatableValue`, matching its own tables (`NOTATABLE_BY_TICKS`, `TICKS_BY_NOTATABLE`), so the loop at L682 can name `dom.NoteValue`. The record's shape is pass 7's and is not touched.

### 3.3 `BeatUnit` (P4, fork B1)

**Found.** `Duration` L22–48: seven constants each holding a `StaffElement` prototype, three mutated in a static block, `getNote()` cloning on every call for four callers (`Tempo.realTempo` L70, `TupletValidator` L182 and L449, `MetronomeContent` L107/L109/L128, `DurationListCellRenderer` L139). `BeatUnitMapping.FORWARD_MAP` restates the seven `(token, dots)` pairs by hand and derives a reverse; `DurationListCellRenderer` L144 special-cases `QUAVER_DOTTED`.

**Proposed.** Renamed to `BeatUnit` through `jet_brains_rename`, then reshaped:

```java
public enum BeatUnit {
    SEMI_BREVE(NoteValue.WHOLE, false),
    MINIM_DOTTED(NoteValue.HALF, true),
    MINIM(NoteValue.HALF, false),
    CROTCHET_DOTTED(NoteValue.QUARTER, true),
    CROTCHET(NoteValue.QUARTER, false),
    QUAVER_DOTTED(NoteValue.EIGHTH, true),
    QUAVER(NoteValue.EIGHTH, false);

    public NoteValue noteValue();
    public boolean dotted();
    public int ticks();          // noteValue.ticks(), half again when dotted
}
```

**The constant names stay.** `TempoIO` L119 and `StaffElementIO` L319 resolve them from legacy files with `valueOf`, and the legacy path is read-only. `dotted()` is a boolean rather than a dot count because the domain admits exactly one dot: `TupletValidator` L183 asks `getDotCount() == 1`, `MetronomeContent` asks `> 0`, and MusicXML's `<beat-unit-dot/>` count is `dotted ? 1 : 0` at the one place that writes it.

- `getNote()` and the static block go. `Tempo.realTempo` L70 asks `tempoType.ticks()`; `TupletValidator.describeSpan` L182–184 and `OpenSpan` L449 ask `ticks()` and `dotted()`; `MetronomeContent.Builder.appendNote(StaffElement)` becomes `appendNote(BeatUnit)` reading `noteValue()` and `dotted()`; `DurationListCellRenderer` L139 asks `dotted()`, and its `QUAVER_DOTTED` case at L144 becomes the same `dotted()` test.
- `BeatUnitMapping`: `FORWARD_MAP` and `BeatUnitEntry` go. `forDuration(unit)` becomes a total derivation — `NoteTypeMapping.typeToken(unit.noteValue())` and `unit.dotted() ? 1 : 0` — so `DirectionBuilder.addMetronomeNote`'s null branch goes. `forBeatUnit(token, dots)` stays `@Nullable` — it reads file input, which is the one honest miss — and is built by iterating `BeatUnit.values()` rather than a hand table.

## 2 — Wrapper types

### 3.4 `ElementBounds` (the eight mutable fields)

**Found.** L163–170: eight `double` fields, non-final, written by three static computations (L793–849, L879–907) run from a static block (L142–144), copied for aliases (L868–877), and validated afterwards (L909–922). The invariant "bounds are computed once and never change" is held by nothing: any method in the enum can assign them.

**Proposed.** One `private final ElementBounds bounds`, a record of the eight measurements, computed in the constructor from `(kind, value, appearance)` and never reassigned:

```java
record ElementBounds(
    double fullWidthSs, double baseWidthSs, double fullElementHeightSs,
    double noteheadTopOffsetSs, double heightUpSs, double heightDownSs,
    double topOffsetUpSs, double topOffsetDownSs) {
    static ElementBounds symmetric(double widthSs, double heightSs, double topOffsetSs);
}
```

This is possible only after 3.1 and 3.2: today the computations read `type.isGraceNote()` L762 and `type.getFlagGlyph(UP)` L806, both of which compare `this` against constants that do not yet exist during an enum constructor. With `kind` and `value` as constructor parameters, the flag glyph is a function of `value` and the grace scaling a function of `kind`, and the whole computation is a pure function of the arguments — so the static block, `setSymmetricBounds` L855–866 and `copyBoundsFrom` L868–877 go. The eight accessors (L255–378) read the record.

`validateElementBounds` L909–922 goes with it. Every input is a `SMuFLMetadata` bbox (total, pass 2), a `StemMetrics` constant, or `Staff.STAFF_HEIGHT_SS`; a glyph with a zero-width box is a font-file defect that pass 2's registry owns, not something this enum can reach.

## 3 — Enums over booleans and bare ints

Nothing in the target selects a mode with a boolean or a bare int. `dotted()` in 3.3 is a genuine two-valued fact, not a mode.

## 4 — Boundary conversions

### 3.5 Legacy element-type names convert at the reader (P5)

**Found.** L102–116: fourteen alias constants, each an `ElementType` whose only purpose is that `valueOf` succeeds on a v1.0 file's spelling. They cost the `aliasOf` field L160, the alias constructor L206–213, two copy loops (L118–133, L783–788), `copyBoundsFrom`, three `aliasOf != null` skips (L122, L754, L911), and a `values()` that no category predicate is total over — `categoryNameKey` throws on them. `StaffElementIO` L222–235 and L262–277 already convert three other legacy spellings (`LINE`, `VERTICALLINE`, `GRACE_SEMIQUAVER`, `GRACE_SEMIQUAVER_EDIT_STEP1`, `GRACESEMIQUAVER`) by if-chain before `valueOf`.

**Proposed.** The fourteen constants and everything listed go. `StaffElementIO` gains one `Map<String, ElementType> LEGACY_ELEMENT_TYPE_NAMES` holding the fourteen alias spellings **and** the five the if-chains already handle, consulted before `valueOf` in both readers; the if-chains go into the map. `TempoIO.LEGACY_TEMPO_DURATION_NAMES` is the pattern. `values()` then holds 31 real types and every switch and predicate is total over it.

This is pass 17's package. The change is in the one method that resolves the names, and it is the same kind of change that method already makes.

### 3.6 The `name` field goes; the type composes its label (P9)

**Found.** L154–155 `@Nullable String name`, an English literal per constant, read by `getName()` L244 for two callers: `StatusBar.getDurationText` L237–243 (lower-cased, prefixed with the dot word, re-capitalised) and `ElementTypeWasSelectedNotification.toString` L39. `StatusBar` reaches it only for notes and rests (`setContent` L173, `setNoteContent` L215); no bar's name is ever shown.

**Proposed.** The field, both constructor parameters and `getName()` go. `ElementType` gains `public String label()`, the one place that composes a type's display words: the value word, then the kind word, joined with a space, the value word absent when the type has none. `GRACE_NOTE` is the one special case, inside this method and nowhere else: its value word is omitted, because a grace note is named by its kind alone. So `CROTCHET` reads "quarter note", `SEMIBREVE_REST` "whole rest", `GRACE_QUAVER` "grace note", `SINGLE_BARLINE` "barline", `REPEAT_LEFT` "repeat". The method's Javadoc states why the grace note omits its value word.

Dots are a `StaffElement` fact, so `label()` describes the type alone. `StatusBar.getDurationText` prefixes the dot word and capitalises, as today, so a dotted quarter note still reads "Dotted quarter note". `ElementTypeWasSelectedNotification.toString` uses `label()`. Seven new keys in `strings.properties`: six `label.note.value.*` and `label.element.category.grace.note`.

### 3.7 The glyph's seat lives on `GlyphAppearance` (P11)

**Found.** `defaultStaffPosition` L159 is declared on all 31 constants and read, for a structural element, through `StructuralElement.getStaffPosition()` L58 — every rest, bar, breath mark and key change. Twelve note constants pass `0` that nothing reads. Two writes into the field that override ignores are dead: `StaffElement.applyConversionFrom` L171 and `PreviewElementManager.applyStaffPosition` L914. `RestRenderer` L53–54 declares its own positions for the whole and half rest and branches on identity at L157–164, so **the whole rest is drawn at** `-2` **and hit-tested and stacked at** `-1` (L61) — the defect step 1 found.

**Proposed.** `GlyphAppearance(SMuFLGlyph glyph, int seatSp)`. The seven glyph constants declare their seat there: `SEMIBREVE_REST` at `-2`, the five other rests at `0`, `BREATH_MARK` at `-5` (the comment at L86–88 moves with it). `StructuralElement.getStaffPosition()` becomes a switch over `getType().appearance()`: a `GlyphAppearance`'s seat, `0` for a bar or key signature (they span the staff, so the center is their position by definition), and a `NoteheadAppearance` arm that is unreachable for a structural element and says so. `defaultStaffPosition`, its constructor argument and `getDefaultStaffPosition()` go; the two dead writes go, so `applyStaffPosition` L910–917 reduces to writing the mouse position for a pitched note. `RestRenderer.calculateRestYSs` L150–170 draws every rest from `note.getStaffPosition()`; its two constants and two identity branches go.

**Seat type.** The seat is `int seatSp`, the representation `StaffElement.staffPosition` and every consumer use. `engraving.StaffPosition` stays a utility class over `int`. A staff-position value type, if one comes, is pass 5's: it must decide whether a position is always on the grid or admits the off-grid intermediates `LedgerLine`, `StackingUtils` and `BeamMath` compute with, and those are pass 5's consumers. `GlyphAppearance.seatSp` is one more producer for that pass to type.

Fixes the whole-rest defect: hit rect and stacking extents move to where the glyph is drawn. Visual gate: the whole rest's clickable area and its collision with lyrics and annotations shift half a staff space up.

### 3.8 The hover is one nullable record (P12)

**Found.** `PreviewElementManager` L83–92: five static fields — `currentPreviewLine`, `currentXIndex`, `currentStaffPosition`, `xPosSsMatchesElement`, `yPosSpMatchesElement` — written together at L689–693, compared field by field at L676–680, cleared together at L419–425, and invalidated one at a time: `restorePreviewElement` L897 sets `currentXIndex = -1` alone, and the layout-null return at L607–609 clears only the two flags. `getHoveredElementLocation` L496–517 then passes `-1` through `canReplaceElementAt` (which answers `true` out of range) and constructs `ElementLocation(lineIndex, -1)` during paint — the only producer of the value `ElementLocation`'s guard rejects.

**Proposed.**

```java
private record PreviewHover(
    LineComponent line, int insertionIndex, int staffPositionSp,
    boolean overElementX, boolean overElementHead) {}

private static @Nullable PreviewHover hover = null;
```

Null is "no hover". `trackMouse` builds a candidate and compares it with `equals` — the "position only moved" case (`xIndexChanged && !configurationChanged`) is the one place that still compares two fields, and it compares them on the two records. `clearPreviewElement` and `restorePreviewElement` set it to null; `restorePreviewElement` repaints the line it held when that is not the line it is restoring on, which is the repaint the old `previousPreviewLine` path did. The layout-null return at L603–610 sets it to null too: the comment at L605–606 calls that state stale, and a stale hover is what null means. This is a visible behavior — during a mid-drag relayout the overlay disappears until the next mouse move, where today it stays put with only the red highlight suppressed — and it is checked at the visual gate. `getHoveredElementLocation` answers null for a null hover and otherwise builds a location from an index that was never negative, so `-1` has no field to live in.

**The accessors.** `getCurrentXIndex()` (10 callers: `PreviewElementInserter` ×8, `GraceModeManager` ×2, `LineComponent` L618), `getCurrentStaffPosition()` (4), `isXPosSsMatchesElement()` (5), `getCurrentInsertionLine()` (3), `isHoveringOverElementHead()` all read a field that today holds a sentinel when there is no hover. They become one `static @Nullable PreviewHover hover()`; each caller takes the record once and null-checks once, at the top, where today it proceeds on `-1`. `PreviewElementInserter.handleClick` L84–150 is the caller that changes shape: it reads the index eight times and the flag five, and with no hover it returns rather than inserting at `-1`. `LineComponent.getCurrentXIndex` / `getCurrentStaffPosition` L617–626 are static delegators; whether anything calls them is checked at execution and they go if nothing does.

`ElementLocation` then carries no guard (P10): its compact constructor L10–16 and `matches` L18–20 go; `LineInvariants.isElementHovered` L455 compares `new ElementLocation(lineIndex, elementIndex).equals(matched)`.

## 5 — Constructor validation

`BarAppearance` L56–68 stays as it is. The domain — a non-empty stroke sequence with at least one line — is not a closed type, and the guard is reached only by a declaration typo at class load. Nothing else in the target needs one after the items above.

## 6 — What goes with the prototype (P6, P7, P8)

**Found.** `instance` L152–153 is assigned by a static block because `createDefaultInstance` L224–234 reads predicates that compare `this` against constants; `getInstance()` L236 hands the shared prototype out to `InsertionSpacingCalculator` L667 and L689, each with a comment asserting nothing mutates it. `NoteElement(type)`, `StructuralElement(type)` and `KeyChangeElement(key)` are bare constructors (`NoteElement` L37–39, `StructuralElement` L40–42, `StaffElement` L110–112), and `clone()` is "construct bare, then `copyStateFrom`" — so a clone of a fresh prototype is a fresh construction.

**Proposed.** `newInstance()` L240 becomes `createDefaultInstance()`, which after 3.1 is a switch on `kind` and legal in a constructor context or anywhere else. The `instance` field, its suppression, `getInstance()` and the two static blocks (L118–133, L142–144) go; `InsertionSpacingCalculator` L667 and L689 take `newInstance()` and their two "shared singleton is safe" comments go. Swing goes with it: `keyCode`, `modifiers`, `acceleratorKey` L156–157, `getAcceleratorKey()` L248, `getMenuShortcutKeyMask()` L146–150, the six-argument constructor L172–195 and the `java.awt` / `javax.swing` / `UIUtils` imports L23–25, L38. `snapToEnd()` L695–702 and `getFullElementCenterXSs()` L272–274 (fan-in 0) go.

After 3.1–3.8 the constructors are `ElementType(Kind kind, NoteValue value, ElementAppearance appearance)` for the thirteen types that have a value and `ElementType(Kind kind, ElementAppearance appearance)` for the twelve that do not; the second delegates to the first with a null value. No declaration passes `null`, which would obscure what the parameter means.

---

## The constant declarations, after

One of each kind reads as:

```java
CROTCHET(Kind.NOTE, NoteValue.QUARTER, new NoteheadAppearance(StemmedNotehead.BLACK)),
SEMIBREVE_REST(Kind.REST, NoteValue.WHOLE, new GlyphAppearance(SMuFLGlyph.REST_WHOLE, -2)),
GRACE_QUAVER(Kind.GRACE_NOTE, NoteValue.EIGHTH, new NoteheadAppearance(StemmedNotehead.BLACK)),
REPEAT_LEFT(Kind.REPEAT, BarAppearance.of(THICK, THIN, DOTS)),
BREATH_MARK(Kind.BREATH_MARK, new GlyphAppearance(SMuFLGlyph.BREATH_MARK_COMMA, -5)),
SINGLE_BARLINE(Kind.BARLINE, BarAppearance.of(THIN)),
KEY_CHANGE(Kind.KEY_CHANGE, new KeySignatureAppearance()),
```

---

## Reach

Files outside the target that the items above touch, so the suite gate knows where to look: `StructuralElement`, `StaffElement`, `TupletValidator`, `Tempo`, `BeatChange`, `BeatAt`, `TempoResolver` (rename only), `KeyChangeElement`, `Line`, `Ending`, `StaffElementRun` (`isBar()`), `io/StaffElementIO`, `io/TempoIO`, `io/musicxml/NoteTypeMapping`, `BeatUnitMapping`, `DirectionBuilder`, `MeasureMapper`, `MeasureBuilder`, `layout/MetronomeContent`, `InsertionSpacingCalculator`, `EndingBracketGeometry`, `LayoutEngine`, `ui/renderer/RestRenderer`, `BarRenderer`, `StaffElementRenderer`, `LineInvariants`, `ui/component/StatusBar`, `DurationListCellRenderer`, `ui/component/score/PreviewElementManager`, `PreviewElementInserter`, `PreviewElementOverlay`, `LineComponent`, `ui/edit/EditModeManager`, `GraceModeManager`, `ui/action/ElementTypeAction`, `undo/OpNames`, `message/notification/ElementTypeWasSelectedNotification`, `CautionaryKeySignature`, `MusicEditOperations`, the four tempo dialogs, `strings.properties`, and the test classes `BarAppearanceTest`, `BeatUnitMappingTest`, `EndingBracketGeometryTest` and whichever of the five `Duration` test classes name it. Step 6 triages the tests; this step only re-points what the rename and the deletions force.

This step runs as one sweep and compiles once at the end, per the development rules: an intermediate tree with `Kind` but not `NoteValue` has no value to certify.

---

## Visual gate

Two behaviors change on screen and are checked when the app runs:

1. The whole rest's clickable area and its collision with lyrics and annotations shift half a staff space up (3.7).
2. During a mid-drag relayout the preview overlay disappears until the next mouse move (3.8).
