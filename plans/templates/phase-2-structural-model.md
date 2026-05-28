# Sub-plan: Phase 2 — Structural Model (Line ↔ Measure)

**Type:** Sub-plan  <br>
**Parent:** [master-plan.md](./master-plan.md) → Phase 2  <br>
**Created:** 2026-05-28  <br>
**Status:** In Progress  <br>
**BlockedBy:** —

---

## Purpose

Implement the reversible **line-centric ↔ measure-centric** transformation — the
conceptual core of the whole conversion. SongScribe is line-centric with inline
barline/repeat `ElementType`s; MusicXML is measure-centric with `<measure>`
containers. This phase teaches the writer to split each `Line` into one or more
`<measure>`s (at every barline element and every line break) and teaches the
reader to reassemble measures back into lines with the original barline elements
re-inserted.

**No note content yet.** Phase 2 carries only the structural skeleton: measures,
barlines/repeats, and system (line) breaks. Measures are emitted with zero
`<note>` children — which is schema-valid (`<measure>` content is a
zero-or-more choice). Notes arrive in Phase 3.

This is **not** a general MusicXML importer — the reader only parses
SongScribe's own output. The writer is held to strict MusicXML 4.0 schema
conformance.

## Implementation Approach

The transformation has one hard rule and one read-back rule (from
musicxml.md § "Structural model"):

- **Write:** insert a `<measure>` boundary at (a) every barline/repeat element
  and (b) every line break. The measure that *starts* a line carries
  `<print new-system="yes"/>`. A line break that does **not** coincide with a
  real barline closes its measure with an invisible
  `<barline><bar-style>none</bar-style></barline>`; a real barline closes its
  measure with a visible `<barline>` carrying the mapped `<bar-style>`
  (+ `<repeat>`).
- **Read:** a measure with `new-system="yes"` starts a new `Line`. A *visible*
  barline → re-insert a barline `StaffElement` of the reverse-mapped
  `ElementType` onto the current line; an *invisible* barline (`bar-style none`)
  → purely a line-break marker, insert nothing. The system break alone always
  triggers the new line, so a visible barline that also coincides with a line
  break round-trips correctly (barline element re-inserted **and** a new line
  begins).

### Decomposition rationale

The four internal phases isolate the hard conceptual work (writer/reader
transformation, Opus) from the mechanical pieces (the bar-style lookup table and
the test wiring, Sonnet/Haiku), and defer all round-trip verification to a
dedicated test phase — mirroring how the Phase 1 scaffold sub-plan split
writer / reader / harness. Writer and reader are separate phases because the
round-trip can only be exercised once both halves exist.

### Key code touchpoints

- **Writer:** `MusicXmlWriter.writeSong(Song, PrintWriter)` — currently emits one
  hardcoded `<measure number="1">`; this becomes line-driven.
- **Reader:** `MusicXmlReader` — `Where` state enum + `startElement`/`endElement`;
  add `print` / `barline` / `bar-style` / `repeat` handling.
- **Model reads:** `Song.getLines()` / `lineCount()`; `Line.getElements()` /
  `elementCount()` / `getElement(i)`; `ElementType.isBarLine()` / `isRepeat()`.
- **Reference IO (read-only, for pattern):** `io/LineIO.writeLine` /
  `LineIO.LineReader` (line→XML structure), `io/StaffElementIO.writeElement` /
  `StaffElementIO.StaffElementReader` (how a barline `StaffElement` is created —
  structural elements via `createDefaultInstance()` for `isNonDuration()` types).

### Bar-style / repeat mapping (used by writer and reader)

| `ElementType` | `<bar-style>` | `<repeat>` | `location` |
|---|---|---|---|
| `SINGLE_BARLINE` | `regular` | — | right |
| `DOUBLE_BARLINE` | `light-light` | — | right |
| `FINAL_DOUBLE_BARLINE` | `light-heavy` | — | right |
| `REPEAT_LEFT` | `heavy-light` | `direction="forward"` | left |
| `REPEAT_RIGHT` | `light-heavy` | `direction="backward"` | right |
| `REPEAT_LEFT_RIGHT` | *decomposes into a straddling pair* | backward (right of closing measure) **+** forward (left of opening measure) | — |

### Flagged uncertainties (resolve during implementation)

1. **`new-system="yes"` on the first measure.** Recommend emitting it uniformly
   on every line-starting measure (including measure 1) so the reader has one
   rule: "new-system ⇒ new line." **Verify this is schema-valid and accepted by
   MuseScore/Finale.** If external tools object, suppress it on measure 1 and
   have the reader treat the first measure as an implicit line start.
2. **`REPEAT_LEFT_RIGHT` straddling pair.** MusicXML permits only one `<repeat>`
   per `<barline>`. A `REPEAT_LEFT_RIGHT` element at a measure boundary must be
   emitted as a backward-repeat `<barline location="right">` on the closing
   measure **plus** a forward-repeat `<barline location="left">` on the opening
   measure, and the reader must recognize that pair as a single
   `REPEAT_LEFT_RIGHT`. This is the trickiest case in both directions.
3. **`REPEAT_LEFT` opens a measure (left barline), not closes one.** A forward
   repeat sits at the *start* of the music it applies to, so encountering it
   while walking a line opens a new measure with a `location="left"` barline
   rather than closing the current one. All other barlines close a measure with
   `location="right"`.
4. **Empty measures.** A measure with only `<print>`/`<barline>` and zero
   `<note>`s is schema-valid and acceptable for this structural phase. Confirm
   the validator agrees before relying on it.

## Dependencies

- **Internal only.** Builds on the Phase 1 scaffold (`MusicXmlWriter`,
  `MusicXmlReader`, `MusicXmlSchemaValidator`, `MusicXmlRoundTripTest`).
- Independent of the blocked attribution rework.
- Must not regress the Phase 1 default-key round-trip or the empty-song tests.

## Plan

### Status Dashboard

| Phase | Description | Status | Recommended model |
|-------|-------------|--------|-------------------|
| 1 | [Barline/Repeat ↔ Bar-Style Mapping](#-phase-1-barlinerepeat--bar-style-mapping) | ✅ Complete | Sonnet 4.6 / Haiku 4.5, low |
| 2 | [Writer — Line→Measure Transformation](#-phase-2-writer--linemeasure-transformation) | ✅ Complete | Opus 4.8, high |
| 3 | [Reader — Measure→Line Reconstruction](#-phase-3-reader--measureline-reconstruction) | ✅ Complete | Opus 4.8, high |
| 4 | [Round-Trip & Schema Tests](#-phase-4-round-trip--schema-tests) | ✅ Complete | Sonnet 4.6 / Haiku 4.5, low |

---

## ✅ Phase 1: Barline/Repeat ↔ Bar-Style Mapping

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Sonnet 4.6 or Haiku 4.5, low effort — a
self-contained, mechanical lookup table with no control-flow complexity; the only
judgment is naming the constants.

### Tasks
1. Create a helper class in `src/main/java/songscribe/io/musicxml/` (e.g.
   `BarlineStyleMapping`) holding the MusicXML bar-style strings, repeat
   directions, and barline locations as **named constants** (no magic strings).
2. Forward map: given a barline/repeat `ElementType`, return its `<bar-style>`
   value, optional `<repeat direction>`, and `location` — for `SINGLE_BARLINE`,
   `DOUBLE_BARLINE`, `FINAL_DOUBLE_BARLINE`, `REPEAT_LEFT`, `REPEAT_RIGHT`
   (per the mapping table in § Implementation Approach).
3. Reverse map: given a `<bar-style>` (+ optional `<repeat direction>`/location),
   return the matching `ElementType`. `REPEAT_LEFT_RIGHT` is **not** handled here
   — it is decomposed/recomposed at the writer/reader level (see Phases 2–3).
4. `./scripts/compile.sh` succeeds.

---

## ✅ Phase 2: Writer — Line→Measure Transformation

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Recommended model/effort:** Opus 4.8, high effort — the reversible
segmentation (where measures begin/end, the invisible-barline vs. visible-barline
decision, the `REPEAT_LEFT`/`REPEAT_LEFT_RIGHT` special cases) is the conceptual
core and demands careful boundary reasoning.

### Tasks
1. Replace the single hardcoded `<measure>` in `MusicXmlWriter.writeSong` with
   line-driven emission: iterate `song.getLines()`, and within each line walk
   `line.getElements()`, segmenting into measures at every barline/repeat element
   and at end-of-line.
2. Emit `<print new-system="yes"/>` as the first child of each line-starting
   measure (see flagged uncertainty #1 re: measure 1).
3. Keep the `<attributes>` block (divisions / key / time / clef) on the **first
   measure only**, reading the default key from `Song` as today — no regression
   of the Phase 1 key round-trip.
4. At each real barline/repeat element, close the measure with a visible
   `<barline>` (location + `<bar-style>` + optional `<repeat>`) via the Phase 1
   mapping. Handle `REPEAT_LEFT` as a `location="left"` forward barline that
   *opens* a measure, and `REPEAT_LEFT_RIGHT` as the straddling backward+forward
   pair (flagged uncertainties #2, #3).
5. At a line break that does not coincide with a real barline, close the measure
   with an invisible `<barline><bar-style>none</bar-style></barline>`; suppress
   the spurious empty measure when a line ends exactly on a barline.
6. Preserve the empty-song fallback: `lineCount() == 0` still emits the single
   attributes-only measure with no `<print>` and no `<barline>`.
7. `./scripts/compile.sh` succeeds; eyeball writer output for a 2–3 line sample
   with assorted barlines (inspect only — committed verification is Phase 4).

---

## ✅ Phase 3: Reader — Measure→Line Reconstruction

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Recommended model/effort:** Opus 4.8, high effort (Sonnet 4.6 high is viable
once Phase 2's design is settled) — mirrors the writer's boundary logic in
reverse, including the `REPEAT_LEFT_RIGHT` pair reassembly.

### Tasks
1. Extend `MusicXmlReader.Where` and the SAX handlers to recognize `<print>`
   (and its `new-system` attribute), `<barline>` (and `location`), `<bar-style>`,
   and `<repeat>` (and `direction`).
2. On a measure with `new-system="yes"`, start a new `Line` and add it to the
   song; otherwise keep appending to the current line. Handle the first
   line-start cleanly.
3. On a visible barline, reverse-map (Phase 1) to an `ElementType` and append a
   barline `StaffElement` to the current line — mirror how
   `StaffElementIO.StaffElementReader` constructs a structural element. On an
   invisible barline (`bar-style none`), insert nothing.
4. Reassemble `REPEAT_LEFT_RIGHT` from the straddling backward+forward barline
   pair (flagged uncertainty #2).
5. Keep ignoring `<divisions>` / `<clef>` / `<time>`; keep reading the default
   key from the measure-1 `<attributes>` — no Phase 1 regression.
6. Ensure the empty-song case (single attributes-only measure, no `new-system`)
   yields `lineCount() == 0`.
7. `./scripts/compile.sh` succeeds.

---

## ✅ Phase 4: Round-Trip & Schema Tests

**Status:** Complete  <br>
**BlockedBy:** 2, 3  <br>
**Recommended model/effort:** Sonnet 4.6 or Haiku 4.5, low effort — mechanical
test wiring extending the existing `MusicXmlRoundTripTest` helpers.

### Tasks
1. Extend `assertPopulatedSubsetEquals` to also compare `lineCount()` and, per
   line, the count / types / order of barline & repeat `StaffElement`s.
2. Add a bijection unit test for the Phase 1 mapping: forward-then-reverse
   round-trips every supported barline/repeat `ElementType`.
3. Round-trip test: a multi-line song with single / double / final barlines at
   various positions — line count, line breaks, and barline positions/styles all
   preserved.
4. Round-trip test: repeats (`REPEAT_LEFT`, `REPEAT_RIGHT`, `REPEAT_LEFT_RIGHT`)
   preserved with correct positions and directions.
5. Round-trip test: an intermediate line ending with **no** barline (and an empty
   line) — reconstructed as line breaks with no spurious barline element.
6. Schema-validate the writer output for each multi-line / repeat sample via
   `MusicXmlSchemaValidator`.
7. `./scripts/compile.sh` then `./scripts/test.sh` (unit target) is green;
   existing empty-song and default-key tests still pass.

---

## Verification (whole sub-plan)

- `./scripts/compile.sh` reports SUCCESS after each phase.
- Writer output for multi-line songs with assorted barlines and repeats validates
  against `docs/musicxml-4.0-schema/musicxml.xsd`.
- `Song → write → read → Song'` is lossless for the cumulative populated subset:
  default key (Phase 1) plus line count, line breaks, and barline/repeat
  positions and styles (Phase 2).
- Existing empty-song and default-key round-trip tests continue to pass.
- New unit tests pass via `./scripts/test.sh` (no e2e in this sub-plan).
