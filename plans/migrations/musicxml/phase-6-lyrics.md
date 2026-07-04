# Sub-plan: Phase 6 — Lyrics

**Type:** Sub-plan  <br>
**Parent:** [musicxml-conversion.md](./musicxml-conversion.md) → Phase 6  <br>
**Created:** 2026-07-03  <br>
**Status:** Complete  <br>
**BlockedBy:** —

---

## Purpose

Add per-verse **lyrics** to both the MusicXML writer and reader, round-tripping
losslessly with schema-valid output. Delivers per-note `<lyric>` children:

- Verse number → `<lyric number="N">`.
- Syllabic (SINGLE/BEGIN/MIDDLE/END) → `<syllabic>`.
- Syllable text → `<text>`.
- Compound-word boundary → non-breaking hyphen (`U+2011`) appended inside `<text>`.
- Melisma extender (START/STOP/CONTINUE) → `<extend type="...">`, including
  text-less STOP/CONTINUE **carrier** lyrics.

**Explicitly out of scope** (deferred): the score-below text blocks —
underlyrics, Bangla lyrics, translated lyrics (Phase 7 credits); per-`<lyric>`
`default-y` positioning (`lyricsYPosSs`, Phase 7 layout); the `<lyric-font>`
(Phase 7); the full-corpus losslessness gate (Phase 8). This phase covers only
the per-note `<lyric>` content itself.

## Implementation Approach

The `<lyric>` grammar is **native MusicXML** and nearly identical to what the
legacy `.mssw` writer in `StaffElementIO` already emits, so this is largely a
mechanical port. The only reversible-boundary subtlety is the reader's SAX
state machine (multi-verse accumulation, text-less extender carriers, and
compound-marker stripping), which mirrors `StaffElementIO.StaffElementReader`.

### Decomposition rationale

- **Phase 1** splits into three independent slices → Sonnet:
  - **1a** (model hardening) adds one correctness guard on the shared `Lyric`
    constructor (mirroring its existing carrier checks) that establishes the
    round-trip invariant Phases 2–3 rely on.
  - **1b** (syllabic mapping + tags) is a static lookup table with an obvious
    inverse, plus the `<lyric>` element/attr constants.
- **Phase 2** (writer) is a mechanical mirror of the existing `StaffElementIO`
  lyric write loop → Sonnet, split into:
  - **2a** (writer) — the `writeLyrics` emission itself.
  - **2b** (writer tests) — raw-output assertions plus a schema-valid gate.
- **Phase 3** (reader + round-trip) is the reversible slice: a SAX accumulator
  handling multi-verse, carriers, and compound stripping → Opus. It ships its
  own round-trip test (vertical-slice precedent from Phases 2–5).
- Ordering: 1a/1b are independent; 2a needs 1b's mapping and tags; 2b needs 2a's
  writer; 3's round-trip test needs 2a's writer (and 1a's constructor guard),
  matching the sequential execute-plan model.

### Key code touchpoints

Model (`src/main/java/songscribe/dom/`):

- `Lyric.java` — `record Lyric(int verse, String text, Extend extend,
  @Nullable Syllabic syllabic, boolean compound)`. Enums:
  `Extend { NONE, START, STOP, CONTINUE }`,
  `Syllabic { SINGLE, BEGIN, MIDDLE, END }`. Static
  `Lyric.syllabicContinues(@Nullable Syllabic)` → true for BEGIN/MIDDLE. Compact
  constructor **currently** validates only: carrier (STOP/CONTINUE) ⇒ null
  syllabic **and** non-compound; non-carrier ⇒ non-null syllabic. It does **not**
  yet enforce `compound ⇒ syllabic ∈ {BEGIN, MIDDLE}`, nor empty text for
  carriers. **Phase 1 adds the `compound ⇒ BEGIN/MIDDLE` guard** (see the
  Compound-asymmetry note below), which makes lossless round-trip correct *by
  construction* rather than by convention.

  > **Compound-asymmetry (why the guard is needed):** the writer appends the
  > `U+2011` marker for **any** `compound()` lyric, but every reader (both the
  > legacy `.mssw` reader and this phase's MusicXML reader) strips the marker
  > **only** for BEGIN/MIDDLE syllabics. A `compound=true` lyric on a SINGLE/END
  > syllabic would therefore write `text‑` and read back as literal `text‑` with
  > `compound=false` — a silent round-trip loss. Production code never creates
  > such a lyric today (verified: the `=` operator, file loaders, and all
  > `setLyricForVerse` callers pair compound with BEGIN/MIDDLE), so the guard
  > breaks no production path; only one existing test builds an invalid
  > SINGLE+compound lyric and must be updated (see Phase 1, task 4).
- `StaffElement.java:67` — `public final List<Lyric> lyrics`. Accessors
  (~521–560): `getLyrics()` (unmodifiable view), `getLyricForVerse(int)`,
  `getMainLyric()`, `setLyricForVerse(int, Lyric)`. **Verse count is not stored**
  anywhere — iterate `getLyrics()`; verse identity is `Lyric.verse()` only.

Legacy IO to mirror (`src/main/java/songscribe/io/StaffElementIO.java`):

- Element/attr constants (95–114): `XML_LYRIC="lyric"`, `XML_LYRIC_NUMBER="number"`,
  `XML_SYLLABIC="syllabic"`, `XML_LYRIC_TEXT="text"`, `XML_EXTEND_TAG="extend"`.
- `COMPOUND_WORD_MARKER` (= `Constants.NON_BREAKING_HYPHEN`, `U+2011`),
  `LEGACY_COMPOUND_WORD_MARKER` (`​`), `COMPOUND_WORD_MARKERS` list (107–114).
- Write loop (269–305): the exact `<lyric>` emission shape to copy.
- Read: `startElement` (419–427), in-LYRIC handling (459–467), `endElement11`
  (502–539) with compound-strip + `element.lyrics.add(new Lyric(...))`; helpers
  `parseExtendType` / `extendTypeAttr` (141–160): absent/unknown type → START,
  `"stop"`→STOP, `"continue"`→CONTINUE; inverse throws on NONE.
- `Constants.NON_BREAKING_HYPHEN` at `src/main/java/songscribe/ui/Constants.java:27`.

Writer (`src/main/java/songscribe/io/musicxml/MusicXmlWriter.java`, not split):

- `writeNote(PrintWriter, NoteWriteContext)` emits numbered steps 1–9; step 9 is
  `writeNotations(pw, ctx)`, then `XML.dedent()` + `XML.writeEndTag(pw, NOTE)`.
  **Lyrics slot in as a new step after `writeNotations` and before the `</note>`
  end tag** (MusicXML 4.0 content order: notations → lyric). Data source:
  `ctx.note().getLyrics()`. Add a private `writeLyrics(pw, note)` helper mirroring
  `writeNotations`.
- `XML` helper: `writeBeginTag`, `writeEmptyTag`, `writeValue`, `writeEndTag`,
  `escapeXML`, `indent`/`dedent`.

Reader (`src/main/java/songscribe/io/musicxml/`):

- `MusicXmlReader.java` — SAX handler with a `Where` state enum; a
  `NoteAccumulator note` field; `startNote(Attributes)` → `note.reset()`;
  `finishNote()` → `note.appendStaffElement(currentLine)` then attachment
  resolution. Add `LYRIC` / `SYLLABIC` / `LYRIC_TEXT` / `EXTEND` states under the
  note subtree.
- `NoteAccumulator.java` — accumulates per-note state (e.g. `markFermata`).
  Add lyric accumulation (a `List<Lyric>` under construction, one entry per
  `<lyric number>`), populated onto `element.lyrics` in `appendStaffElement`.
- `MusicXmlTags.java` — has no lyric constants yet; add `LYRIC`, `SYLLABIC`,
  `LYRIC_TEXT` (`"text"`), `EXTEND`, `ATTR_NUMBER`, `ATTR_TYPE` (reuse if present).

Mapping helper (new, `songscribe.io.musicxml.SyllabicMapping`):

- Mirror `NoteTypeMapping` / `AccidentalMapping`: final class, static
  forward/reverse maps. `Syllabic` ↔ `single|begin|middle|end`; `Extend` ↔
  `start|stop|continue` (NONE has no token — absence of `<extend>`). Null-safe.

Tests (`src/test/java/songscribe/io/musicxml/`):

- `MusicXmlRoundTripSupport` — `writeToString`, `parse`, `roundTrip`,
  `buildSong(LineBuilder)`. Mirror an existing per-feature test
  (e.g. `MusicXmlTempoRoundTripTest`).
- `MusicXmlWriterOutputTest` — raw-output assertions on emitted XML.
- Mapping unit tests mirror `NoteTypeMappingTest`.

### Target output samples

Plain syllable (verse 1, begin of a two-syllable word):

```xml
<lyric number="1">
  <syllabic>begin</syllabic>
  <text>Ky</text>
</lyric>
```

Compound-word boundary (marker `U+2011` appended inside `<text>`):

```xml
<lyric number="1">
  <syllabic>begin</syllabic>
  <text>self&#x2011;</text>
</lyric>
```

Extender start (melisma opens on this note):

```xml
<lyric number="1">
  <syllabic>single</syllabic>
  <text>oh</text>
  <extend type="start"/>
</lyric>
```

Text-less extender carrier (mid/end of a melisma — no syllabic/text):

```xml
<lyric number="1">
  <extend type="stop"/>
</lyric>
```

Multiple verses on one note → one `<lyric>` per verse, `number="1"`, `"2"`, …

## Dependencies

- No rebase required: `musicxml-phase-6` is current with `develop`
  (merge-base == HEAD).
- Phase 2a depends on 1b (`SyllabicMapping` + tag constants).
- Phase 2b depends on 2a (tests need the writer).
- Phase 3 depends on 2a (round-trip test needs the writer) and 1a (constructor
  guard).
- **Must not regress:** the Phase 1–5 round-trip suites; writer output must keep
  validating against `docs/musicxml-4.0-schema/` (`<lyric>` content model, its
  position within `<note>`).

## Plan

| Phase | Description | Status | Recommended model |
|-------|-------------|--------|-------------------|
| 1a | [Model Hardening](#-phase-1a-model-hardening) | ✅ Complete | Sonnet 4.6, low |
| 1b | [Syllabic Mapping + Tags](#-phase-1b-syllabic-mapping--tags) | ✅ Complete | Sonnet 4.6, low |
| 2a | [Writer](#-phase-2a-writer) | ✅ Complete | Sonnet 4.6, low |
| 2b | [Writer Tests](#-phase-2b-writer-tests) | ✅ Complete | Sonnet 4.6, low |
| 3 | [Reader + Round-Trip](#-phase-3-reader--round-trip) | ✅ Complete | Opus 4.8, medium |

## ✅ Phase 1a: Model Hardening

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Sonnet 4.6, low — one guard added to the `Lyric` compact constructor (mirroring its existing carrier checks). Unit tests gate correctness.

### Tasks

1. **Harden the `Lyric` compact constructor** (`songscribe.dom.Lyric`) to enforce
   the round-trip invariant `compound ⇒ syllabic ∈ {BEGIN, MIDDLE}`: in the
   non-carrier branch (which already requires non-null syllabic), throw
   `IllegalStateException` when `compound` is true and `syllabic` is neither
   `BEGIN` nor `MIDDLE` (reuse `Lyric.syllabicContinues(syllabic)` for the test).
   This closes the writer/reader compound asymmetry at the model level — no
   `Lyric` that would round-trip lossily can be constructed. **Audit note:** no
   production caller violates this; the only offender is the existing test
   `SongLineManagementTest.testAppendsDoubleDash_WhenCompound`
   (`src/test/java/songscribe/dom/SongLineManagementTest.java:158`), which passes
   `SINGLE + compound=true` — update it to `BEGIN` (task 2) so it still exercises
   the `--` append behavior with a valid syllabic.
2. Tests:
   - Add a `Lyric` constructor-validation test (in `LyricTest`, alongside the
     existing carrier-throws case): assert `compound=true` with `SINGLE`, `END`,
     and `null` syllabic each throws `IllegalStateException`, and that
     `compound=true` with `BEGIN`/`MIDDLE` is accepted.
   - Update `SongLineManagementTest.testAppendsDoubleDash_WhenCompound` (line 158)
     to use `Lyric.Syllabic.BEGIN` instead of `SINGLE`.
3. Gate: `./scripts/compile.sh` (SUCCESS) then
   `./scripts/test.sh LyricTest SongLineManagementTest` (green).

## ✅ Phase 1b: Syllabic Mapping + Tags

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Sonnet 4.6, low — a static lookup table with an obvious inverse plus a handful of tag constants. Unit tests gate correctness.

### Tasks

1. Add `SyllabicMapping` in `songscribe.io.musicxml` (mirror `NoteTypeMapping` /
   `AccidentalMapping`: final class, static forward/reverse maps, null-safe
   methods). Cover `Lyric.Syllabic` ↔ `single|begin|middle|end` and
   `Lyric.Extend` ↔ `start|stop|continue`. `Extend.NONE` has **no** token
   (represents the absence of an `<extend>` element) — the reverse lookup maps an
   absent/unknown extend type to `START`, matching `StaffElementIO.parseExtendType`.
   Decide and document the **unknown-`<syllabic>`-token** behavior (e.g. an
   unrecognized token → `SINGLE`, matching the writer's null-syllabic default, or
   throw as `NoteTypeMapping` does) — the reader (Phase 3) depends on it.
2. Add lyric tag constants to `MusicXmlTags`: `LYRIC`, `SYLLABIC`,
   `LYRIC_TEXT` (`"text"`), and `EXTEND`. **Reuse the existing `ATTR_NUMBER`
   (`"number"`, line 113) and `ATTR_TYPE` (`"type"`, line 124) attribute
   constants — both already exist; do not duplicate them.**
3. Tests:
   - Add `SyllabicMappingTest` (mirror `NoteTypeMappingTest`): assert round-trip
     for all four `Syllabic` values and all three tokened `Extend` values, the
     absent-token → `START` reverse behavior, and the chosen unknown-syllabic
     behavior from task 1.
4. Gate: `./scripts/compile.sh` (SUCCESS) then
   `./scripts/test.sh SyllabicMappingTest` (green).

## ✅ Phase 2a: Writer

**Status:** Complete  <br>
**BlockedBy:** 1b  <br>
**Recommended model/effort:** Sonnet 4.6, low — mechanical mirror of the existing `StaffElementIO` lyric write loop into the new writer.

### Tasks

1. Add a private `writeLyrics(PrintWriter pw, StaffElement note)` in
   `MusicXmlWriter`, called from `writeNote` **after** `writeNotations(pw, ctx)`
   and **before** the `</note>` end tag. Iterate `note.getLyrics()`; skip when
   empty.
2. For each `Lyric`, emit `<lyric number="verse()">`:
   - **Carrier** (`extend()` is STOP or CONTINUE): emit only
     `<extend type="stop|continue"/>` (via `SyllabicMapping`), then close — no
     syllabic/text (mirror `StaffElementIO` lines 269–305).
   - **Otherwise**: `<syllabic>` (`SyllabicMapping`, defaulting a null syllabic to
     `single` as `StaffElementIO` does); `<text>` with `XML.escapeXML` of
     `compound() ? text() + Constants.NON_BREAKING_HYPHEN : text()`; then
     `<extend type="start"/>` only when `extend()` is START.
3. Gate: `./scripts/compile.sh` (SUCCESS).

## ✅ Phase 2b: Writer Tests

**Status:** Complete  <br>
**BlockedBy:** 2a  <br>
**Recommended model/effort:** Sonnet 4.6, low — a raw-output test plus schema validation gate the writer.

### Tasks

1. Add a raw-output case to `MusicXmlWriterOutputTest` asserting the emitted
   `<lyric>` structure for: a plain syllable, a compound syllable (marker present
   in `<text>`), an extender-start, a text-less **STOP** carrier, a text-less
   **CONTINUE** carrier, and a **multi-verse** note (two `<lyric>` children with
   `number="1"` and `number="2"`).
2. Add a `lyricsWriterOutputIsSchemaValid` test method (mirror the existing
   `*WriterOutputIsSchemaValid` tests) that writes lyric-bearing output and runs
   `new MusicXmlSchemaValidator().validate(xml)` inside
   `assertThatCode(...).doesNotThrowAnyException()` — `roundTrip()` does **not**
   auto-validate, so schema conformance (lyric content model + position after
   `<notations>` within `<note>`) needs its own assertion.
3. Gate: `./scripts/compile.sh` (SUCCESS);
   `./scripts/test.sh MusicXmlWriterOutputTest` (green, including the new
   schema-valid method).

## ✅ Phase 3: Reader + Round-Trip

**Status:** Complete  <br>
**BlockedBy:** 2a  <br>
**Recommended model/effort:** Opus 4.8, medium — the reversible slice: a SAX accumulator handling multi-verse `<lyric>` children, text-less extender carriers, and compound-marker stripping, mirroring `StaffElementIO.StaffElementReader`.

### Tasks

1. Add `LYRIC` / `SYLLABIC` / `LYRIC_TEXT` / `EXTEND` states to the
   `MusicXmlReader` `Where` enum under the note subtree, dispatched from
   `startElement` / `endElement`. On `<lyric>` start, read `number` (default 1)
   and begin a fresh pending lyric; reset syllabic/text/extend.
2. Accumulate lyric fields in `NoteAccumulator` (a list of pending lyrics, one per
   `<lyric>`): `<syllabic>` text → `Syllabic` via `SyllabicMapping`; `<text>`
   character data → text; `<extend>` → `Extend` from its `type` attr (absent →
   START, per `SyllabicMapping`). On `</lyric>`, finalize the entry: when syllabic
   is BEGIN/MIDDLE and text ends with a marker in `COMPOUND_WORD_MARKERS`, strip
   it and set `compound=true` (mirror `StaffElementIO.endElement11`, 502–539);
   text-less STOP/CONTINUE carriers get null syllabic + empty text.
   **`NoteAccumulator.reset()` MUST clear this pending-lyric list** (as it clears
   every other per-note scratch field). Because `reset()` runs at each `<note>`
   open and the accumulated `List<Lyric>` is note-scoped, omitting this would
   silently carry note A's lyrics onto note B with no exception and no schema
   error — a pure stale-data corruption. This is the one non-obvious safety point
   of the reader; it has a dedicated regression test (task 4).
3. In `appendStaffElement` (or `finishNote`), populate `element.lyrics` from the
   accumulated entries (mirror `StaffElementIO`'s `element.lyrics.add(...)`;
   construct each via `new Lyric(verse, text, extend, syllabic, compound)` so the
   record's compact-constructor validation runs).
4. Add `MusicXmlLyricRoundTripTest` (extends `MusicXmlRoundTripSupport`) covering,
   with `getLyricForVerse` / `getLyrics()` equality (verse, text, syllabic,
   extend, compound) asserted after `roundTrip`:
   - a single plain syllable;
   - a multi-syllable word with BEGIN/MIDDLE/END syllabic breaks;
   - a compound-word boundary (marker stripped, `compound=true` restored);
   - an extender START + text-less **STOP** carrier;
   - a text-less **CONTINUE** carrier (only STOP was in the original matrix);
   - a two-verse note;
   - **cross-note reset regression:** two consecutive notes each carrying a
     *different* lyric — assert note B has only its own lyric and none of note
     A's (guards the `reset()` requirement in task 2);
   - **missing-`number` lenience:** feed hand-written XML (via `parse(...)`) with a
     `<lyric>` that omits the `number` attribute and assert it loads as verse 1
     (mirrors `MusicXmlReaderLenienceTest`);
   - a `lyricsRoundTripIsSchemaValid` method running
     `new MusicXmlSchemaValidator().validate(xml)` on lyric-bearing output.
5. Gate: `./scripts/compile.sh` (SUCCESS);
   `./scripts/test.sh MusicXmlLyricRoundTripTest` (green, including the
   cross-note, lenience, and schema-valid cases); then
   `./scripts/test.sh unit` to confirm no Phase 1–5 regression (and that the
   Phase 1 `Lyric`-constructor change did not break any existing suite).

## Verification (whole sub-plan)

- `./scripts/compile.sh` → SUCCESS.
- The `Lyric` constructor rejects `compound=true` with a non-BEGIN/MIDDLE
  syllabic (invariant established in Phase 1), and `SongLineManagementTest` is
  updated to the valid BEGIN form.
- Writer output for plain, compound, extender, STOP/CONTINUE carrier, and
  multi-verse lyrics validates against `docs/musicxml-4.0-schema/` via an
  explicit `SchemaValidator` assertion (not relied on implicitly through
  `roundTrip`).
- `./scripts/test.sh unit` green, including `SyllabicMappingTest`, the new
  `LyricTest` constructor cases, the new `MusicXmlWriterOutputTest` cases, and
  `MusicXmlLyricRoundTripTest`, with no regression in the Phase 1–5 suites.
- Round-trip is lossless for: single syllables; multi-syllable words with
  syllabic breaks; compound words; extenders (START + text-less STOP/CONTINUE
  carriers); multi-verse notes; and **consecutive notes with distinct lyrics**
  (no cross-note bleed). A `<lyric>` with no `number` attribute loads as verse 1.
