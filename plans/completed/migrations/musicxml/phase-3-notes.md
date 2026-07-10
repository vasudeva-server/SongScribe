# Sub-plan: Phase 3 — Notes & Per-Note Attachments
**Type:** Sub-plan  
**Parent:** [musicxml-conversion.md](./musicxml-conversion.md) → Phase 3  
**Created:** 2026-05-29  
**Status:** Complete  
**BlockedBy:** —

---

## Purpose
Add core `<note>` content and everything attached to a single note to both the
MusicXML writer and reader, with per-note round-trip verification. After this
phase, a `Song` whose lines contain notes (not just barlines) survives
`Song → MusicXML → Song` with note-level fidelity.

**In scope** (musicxml.md § "Note / element → `<note>`"):

- Durations (`SEMIBREVE`…`DEMI_SEMIQUAVER`) → `<type>` + `<duration>`; rests
  (`*_REST`) → `<rest/>`; grace (`GRACE_QUAVER`) → `<grace/>`.

- Pitch: `staffPosition` + accidental + key → `<step>`/`<octave>`/`<alter>`,
  bijective with `getPitch()`'s inputs.

- Dot count → `<dot/>`×n; accidental glyph (incl. cautionary / parenthesized)
  → `<accidental>`; stem up/down + manual-override flag → `<stem>` (emitted
  only on manual override); X offset (px → tenths) → `<note relative-x>`, with
  the computed base X in write-forward-only `<note default-x>` (MusicXML
  renders `default-x` + `relative-x`, so the offset must live in `relative-x`;
  the reader ignores `default-x`).

- Per-note articulations / notations: accent, staccato, fermata, dynamics,
  breath mark, glissando carried on the note via the `StaffElement.slide` field
  (`hasGlissando()`) (→ `<slide>` start/stop), with each `<slide>` carrying
  **computed** endpoint `default-x`/`default-y` (write-forward only, for
  external rendering fidelity; the reader ignores them and re-renders); and fall
  living on the note via `hasFall()` — `<notations><articulations><falloff>`
  on that note; read: `<falloff>` → `setFall()`.

**Explicitly out of scope** (deferred to later phases — do not implement here):

- Line-level range spans: beaming, ties, tuplets, crescendo/diminuendo, trills,
  endings → **Phase 4**.

- Per-measure key changes and tempo / metric modulation → **Phase 5**
  (the song-level `<key>` already emitted in Phase 2 is untouched here).

- Lyrics → **Phase 6**. Header / layout / annotations → **Phase 7**.

Even though `StaffElementIO.writeElement` serializes lyrics, tempo, beat-change,
and annotation attachments alongside notes in the legacy `.mssw` path, those are
**out of scope** here — Phase 3 touches only the per-note fields listed above.

## Implementation Approach
The writer's note hook already exists as a stub:
`MusicXmlWriter.writeLineDrivenMeasures` iterates each `Line`'s elements and
currently emits nothing for non-barline elements (see the comment at
`MusicXmlWriter.java:158` — *"note content arrives in Phase 3"*). Phase 3 fills
that hook and adds the symmetric parse path to `MusicXmlReader`.

The single hard problem is the **pitch ↔ staff-position bijection**. Everything
else is table-driven emission/parsing in the established `XML` /
`MusicXmlTags` / `BarlineStyleMapping` idiom. The decomposition isolates that
bijection into its own helper + phase so the mechanical writer/reader phases can
lean on it.

### Key code touchpoints

- **Writer hook**: `MusicXmlWriter.writeLineDrivenMeasures` —
  `MusicXmlWriter.java:111-160` (the per-element loop; the stub comment is at
  `:158`). `DIVISIONS` constant + provisional comment: `MusicXmlWriter.java:33-34`.
- **Reader hook**: `MusicXmlReader.startElement`/`endElement`/`characters` and
  the `Where` enum — `MusicXmlReader.java:150-309`, `:456-468`. Mirror
  `appendToCurrentLine` (`:409-417`) for element creation via
  `ElementType.newInstance()`.
- **Constant/table idiom to mirror**: `MusicXmlTags`
  (`io/musicxml/MusicXmlTags.java`) and `BarlineStyleMapping`
  (`io/musicxml/BarlineStyleMapping.java`, a bidirectional `ElementType` ↔
  MusicXML lookup).
- **New classes (3):** `NoteTypeMapping` (owns both the `<type>` token map and
  the `ticks(type, dotCount)` duration math — both key off the same `ElementType`,
  so they live in one class rather than two parallel tables), `AccidentalMapping`,
  and `PitchSpelling` — each mirroring the existing `BarlineStyleMapping` idiom.
  No other new production classes are introduced.
- **Note model**: `dom/StaffElement.java` — `getType`, `getStaffPosition`,
  `getDotCount`, `getAccidental`, `isAccidentalInParentheses`, `isUpper`,
  `isStemDirectionAuto`, `getXOffsetPx`, `getSlide`, `hasGlissando`,
  `setGlissando`, `hasFall`, `setFall`, `getArticulations`, `findAttachment`,
  and the pitch internals `getPitch`/`calculatePitch`/`getPitchIndex`/
  `MIDI_PITCHES`/`MIDI_PITCH_ADJUSTMENT`/`findLastAccidental`. Inner enum:
  `StaffElement.Accidental` (NATURAL, FLAT, SHARP, DOUBLE_NATURAL, DOUBLE_FLAT,
  DOUBLE_SHARP, NATURAL_FLAT, NATURAL_SHARP; `DOUBLE_NATURAL` has no MusicXML
  mapping). Inner class hierarchy: `StaffElement.Slide` (abstract sealed base),
  `StaffElement.Glissando extends Slide`, `StaffElement.Fall extends Slide`;
  stored in the `slide` field, tested via `hasGlissando()`/`hasFall()`. Note: a
  standalone `SLIDE` `ElementType` content element also exists — that is **not**
  what carries glissando here; Phase 3 uses only the per-note `slide` field.
- **Element types**: `dom/ElementType.java` note constants
  (`SEMIBREVE`…`DEMI_SEMIQUAVER`), rests (`*_REST`), `GRACE_QUAVER`,
  `BREATH_MARK`; `isBarLine()`/`isRepeat()` already used in Phase 2.
- **Articulations / attachments**: `dom/ArticulationType` (STACCATO, ACCENT),
  `dom/FermataAttachment`, `dom/DynamicAttachment` (`getType()`).
- **Reference serialization** to mirror (NOT reuse): `io/StaffElementIO.writeElement`
  and its `XML_*` field-by-field handling — shows exactly which fields the legacy
  format persists and their guards (e.g. emit X offset only when non-zero).
- **Unit conversion** (X offset px ↔ tenths): `ScaleContext.pxToSs` /
  `ScaleContext.ssToRoundedPx` — both **static** (there is no `getInstance()`)
  (see `.agents/guides/unit-conversion.md`). Tenths = ss × 10;
  do not hardcode either factor. `StaffExtents.spToSs`/`ssToSp` handle staff
  positions if needed.
- **Schema** for write-side validation: `docs/musicxml-4.0-schema/`. The strict
  child order of `<note>` is defined there and **must** be honored
  (`grace?, chord?, (pitch|rest), duration?, tie*, …, type?, dot*, accidental?,
  …, stem?, …, notations*, lyric*`).

## Dependencies

- **Phase 2 of the master plan (Structural Model)** — ✅ complete. Phase 3 plugs
  notes into the line-driven measure structure it built; the measure-segmentation
  and barline/repeat logic is untouched.
- **Must not regress:** the existing structural round-trip (multi-line songs,
  assorted barlines, `REPEAT_LEFT_RIGHT` pairs) and the empty-song fallback
  (`writeEmptySongMeasure`). The `MusicXmlRoundTripTest` suite (including its
  `MusicXmlSchemaValidator`-based `*WriterOutputIsSchemaValid` checks) must stay green.

## Plan

| Phase | Description | Status | Recommended model |
|-------|-------------|--------|-------------------|
| 1 | [Tags and Lookup Tables](#-phase-1-tags-and-lookup-tables) | ✅ Complete | Sonnet 4.6 / Haiku 4.5, low |
| 2 | [Pitch and Staff Position Bijection](#-phase-2-pitch-and-staff-position-bijection) | ✅ Complete | Opus 4.8, high |
| 3 | [Divisions and Duration Mapping](#-phase-3-divisions-and-duration-mapping) | ✅ Complete | Sonnet 4.6, medium |
| 4 | [Writer Note Emission](#-phase-4-writer-note-emission) | ✅ Complete | Sonnet 4.6, medium |
| 5 | [Reader Note Parsing](#-phase-5-reader-note-parsing) | ✅ Complete | Opus 4.8, medium |
| 6a | [Round-Trip Tests: Helpers and Infrastructure](#-phase-6a-round-trip-tests-helpers-and-infrastructure) | ✅ Complete | Sonnet 4.6, low |
| 6b | [Round-Trip Tests: Note Cases](#-phase-6b-round-trip-tests-note-cases) | ✅ Complete | Sonnet 4.6, medium |
| 6c | [Round-Trip Tests: Edge Cases and Final Verification](#-phase-6c-round-trip-tests-edge-cases-and-final-verification) | ✅ Complete | Sonnet 4.6, low |

---

## ✅ Phase 1: Tags and Lookup Tables

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Sonnet 4.6 / Haiku 4.5, low — mechanical constant
and lookup-table entry mirroring `MusicXmlTags` and `BarlineStyleMapping`; no
control flow.

### Tasks

1. Add the `<note>`-subtree element, attribute, and value name constants to
   `MusicXmlTags`, grouped with comments in the existing style: `note`, `pitch`,
   `step`, `alter`, `octave`, `rest`, `grace` (+ `slash` attr; **no**
   `steal-time-following` — see Phase 4 Task 2), `duration`, `type`, `dot`,
   `accidental` (+ `cautionary`/`parentheses`
   attrs), `stem` (+ `default-y` attr), `notations`, `articulations`, `accent`,
   `staccato`, `fermata`, `dynamics`, `breath-mark`, `slide` (+ `type` /
   `line-shape` / `line-type` / `default-x` / `default-y` attrs), `falloff`
   (+ `line-length` / `line-shape` / `line-type` / `default-x` / `default-y`
   attrs), and `default-x` (on `<note>`).
2. Create `NoteTypeMapping` (mirroring `BarlineStyleMapping`): a bidirectional
   map between `ElementType` note/rest/grace constants and the MusicXML `<type>`
   token — `SEMIBREVE`→`whole`, `MINIM`→`half`, `CROTCHET`→`quarter`,
   `QUAVER`→`eighth`, `SEMIQUAVER`→`16th`, `DEMI_SEMIQUAVER`→`32nd`. Each
   `*_REST` maps to the **same** `<type>` token; a rest is distinguished by a
   `<rest/>` child *within* the `<note>`, not by a separate token or flag.
   `GRACE_QUAVER`→`eighth`; a grace is distinguished by a `<grace>` child within
   the `<note>`. Grace notes emit `<grace slash="no"/>` — **no
   `steal-time-following`** (SongScribe playback gives grace notes zero duration
   and never shortens the host, so emitting a steal would misrepresent the song;
   see Phase 4 Task 2), no `<duration>`, `<type>eighth</type>`, and
   `<stem default-y="…">up</stem>` (grace stems are always up). Grace notes never
   carry dots. This class **also owns the duration math** added in Phase 3
   (`ticks(type, dotCount)`): the `<type>` token and the tick count both key off
   the same `ElementType`, so they share one class. Forward (write) and inverse
   (read) lookups. Add an inline ASCII table-comment showing the single
   `ElementType → {<type> token, base-tick factor}` table plus the dot
   augmentation, so the merge is visible at a glance.
3. Create `AccidentalMapping` (own class, mirroring `BarlineStyleMapping`): an
   `Accidental` ↔ MusicXML mapping (token + `<alter>` semitone): `NATURAL`
   →`natural`/0, `FLAT`→`flat`/−1, `SHARP`→`sharp`/+1, `DOUBLE_FLAT`→`flat-flat`/
   −2, `DOUBLE_SHARP`→`double-sharp`/+2, `NATURAL_FLAT`→`natural-flat`/−1,
   `NATURAL_SHARP`→`natural-sharp`/+1. `DOUBLE_NATURAL` is not supported by
   SongScribe and needs no mapping.
4. Run `./scripts/compile.sh` → must report SUCCESS.

---

## ✅ Phase 2: Pitch and Staff Position Bijection

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Opus 4.8, high — the one reversible-boundary
problem in this phase; correctness of the whole round-trip rests on forward∘inverse
being exact identity over the full staff range.

### Tasks

1. Study `StaffElement.calculatePitch`, `getPitchIndex`, `MIDI_PITCHES`,
   `MIDI_PITCH_ADJUSTMENT`, `findLastAccidental`, and `KeySignature`/`KeyType` to
   confirm how `staffPosition` maps to a diatonic step+octave and how the
   accidental/key produce the chromatic alteration.
2. Create a `PitchSpelling` helper. Forward: `staffPositionSp` → (`<step>` letter,
   `<octave>`), diatonic and independent of accidental. **Reuse
   `StaffElement.getPitchIndex()`** for the staffPosition→diatonic-step
   decomposition rather than re-deriving the lattice — it already owns the B4 = 0
   origin (`MIDI_PITCHES`), so a single source of truth avoids silent divergence
   if that origin ever changes. Map the returned step index to the `<step>` letter
   and mirror the octave arithmetic. Document the origin (B4 = staffPosition 0,
   increasing downward). Add an inline ASCII diagram of the
   staffPosition↔(step, octave) lattice here — this is the crux of the round-trip.
3. Implement `<alter>` derivation: the **sounding** alteration from the explicit
   accidental when present, else from the effective key (`findLastAccidental`),
   so the emitted `<pitch>` sounds correct even with no visible accidental.
4. Implement the inverse for read and provide **both directions** of the
   `<alter>` ↔ sounding-alteration mapping. `<step>` + `<octave>` + `<alter>` are
   **authoritative for pitch** (key-independent): set `staffPositionSp` from
   `<step>`/`<octave>`. The *displayed* accidental glyph is recovered separately
   from `<accidental>` (Phase 5), **not** from `<alter>` — pitch and displayed
   glyph stay distinct paths.
5. Resolve and document the independence of sounding `<alter>` vs displayed
   `<accidental>`: a note can sound altered via key with no glyph, and a
   cautionary natural can show a glyph with no key alteration.
6. Hand-verify (reasoning + a scratch check) that forward∘inverse is identity
   across the full staff-position range and accidental set; the formal assertion
   lands in Phase 6.
7. Run `./scripts/compile.sh` → must report SUCCESS.

---

## ✅ Phase 3: Divisions and Duration Mapping

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Recommended model/effort:** Sonnet 4.6, medium — bounded arithmetic with one
locked decision (`<divisions>`); the divisibility rule below removes the design
guesswork.

### Tasks

1. The maximum dot count is **2**, so the smallest representable note fraction
   is a double-dotted 32nd.
2. `DIVISIONS = 480` is a global constant (not per-song). Verify it satisfies
   the divisibility requirement: a double-dotted 32nd = `DIVISIONS/8 × 7/4` =
   `60 × 7/4 = 105` ticks — an exact integer, so 480 is correct. Replace the
   provisional comment at `MusicXmlWriter.java:33-34` with this justification.
3. Implement `<duration>` tick computation as `NoteTypeMapping.ticks(type,
   dotCount)` (the same merged class from Phase 1 — token map and tick math share
   one `ElementType` table): base ticks from the `ElementType` note value
   (whole = 4×`DIVISIONS`, half = 2×, quarter = 1×, eighth = ½, 16th = ¼,
   32nd = ⅛) × dot augmentation (`×(2 − 2^−dotCount)`), as an exact integer.
   Assert exact division (no truncation) — a non-integer result means `DIVISIONS`
   is wrong. (No `SIXTEENTH_TICKS` / steal-time helper is needed — grace notes
   carry no `steal-time-following`; see Phase 1 Task 2 / Phase 4 Task 2.)
4. Encode the special-shape rules: grace notes emit `<type>` but **no**
   `<duration>`; rests emit `<duration>` + `<type>` + `<rest/>`.
5. Run `./scripts/compile.sh` → must report SUCCESS.

---

## ✅ Phase 4: Writer Note Emission

**Status:** Complete  <br>
**BlockedBy:** 1, 2, 3  <br>
**Recommended model/effort:** Sonnet 4.6, medium — schema-ordered assembly of the
Phase 1–3 building blocks into the existing `XML`/`MusicXmlWriter` idiom; the
only subtlety is the breath-mark serialization: when writing a note/rest, peek at
the next element — if it is `BREATH_MARK`, emit `<breath-mark/>` in
`<notations><articulations>` for that note and skip the `BREATH_MARK` when the
outer loop reaches it.

### Tasks

1. In `writeLineDrivenMeasures`, replace the "note content arrives in Phase 3"
   stub (`MusicXmlWriter.java:158`) with a `writeNote(...)` call for note / rest /
   grace elements (leave the barline/repeat branches untouched). When the outer
   loop reaches a `BREATH_MARK` element, skip it — it was already serialized with
   its preceding note.
2. Emit `<note>` children in **strict schema order**: `<grace>` → `<rest/>` |
   `<pitch>`(step/alter/octave) → `<duration>` → `<type>` → `<dot/>`×n →
   `<accidental>` → `<stem>` → `<notations>`. For grace, emit `<grace slash="no"/>`
   (no `<duration>`, **no `steal-time-following`** — SongScribe playback gives
   grace notes zero duration and never steals from the host, so emitting a steal
   would misrepresent the song; grace export needs no host lookup at all) and
   `<stem default-y="…">up</stem>`. Add an inline ASCII diagram of the strict
   `<note>` child-order pipeline. Validate ordering against
   `docs/musicxml-4.0-schema/`.
3. Pitch via the Phase 2 helper; accidental glyph via the Phase 1 table with
   `cautionary="yes"` / `parentheses="yes"` driven by `isAccidentalInParentheses()`.
   Emit `<stem>up|down` from `isUpper()` only when `isStemDirectionAuto() == false`; omit otherwise.
4. Position. Emit the user offset `getXOffsetPx()` (via `ScaleContext.pxToSs`
   → ×10 tenths) as `<note relative-x>`, only when non-zero (mirror the legacy
   `writeElement` guard). MusicXML renders `default-x` + `relative-x`, so the
   offset belongs in `relative-x`, not `default-x`. Also emit `<note default-x>`
   = the note's computed base X within the measure in tenths (the laid-out X
   *minus* the offset) for external-renderer fidelity; this is write-forward
   only and the reader ignores it. (Layout always runs before export, so the
   base X is available — the same precondition as the glissando endpoint
   coordinates.)
5. `<notations>` body: accent / staccato from `getArticulations()`; fermata from
   `FermataAttachment`; dynamics from `DynamicAttachment` in `<notations><dynamics>`.
6. Glissando / fall / breath-mark. The glissando lives on the note via the
   `StaffElement.slide` field: when `hasGlissando()` is true, emit
   `<slide type="start" line-shape="straight" line-type="solid"/>` on the first
   note and `<slide type="stop" …/>` on the second. Each `<slide>` carries
   **computed** endpoint `default-x`/`default-y` (in tenths) for external
   rendering fidelity — these are write-forward only and not read back; layout
   always runs before export, so the coordinates are valid. Fall lives on the
   note via `hasFall()`: emit `<notations><articulations><falloff/>` when
   `hasFall()` is true. Breath-mark: peek at the next element — if it is
   `BREATH_MARK`, emit `<breath-mark/>` in `<notations><articulations>` for
   this note.
7. Run `./scripts/compile.sh` → SUCCESS; spot-check one populated sample validates
   against `docs/musicxml-4.0-schema/`.

---

## ✅ Phase 5: Reader Note Parsing

**Status:** Complete  <br>
**BlockedBy:** 1, 2, 3  <br>
**Recommended model/effort:** Opus 4.8, medium — symmetric SAX parse; rated above
the writer because the structural re-collapse and the pitch inverse must reproduce
the writer's choices exactly.

### Tasks

1. Extend the `Where` enum and the SAX handlers with `NOTE` and its child states
   (PITCH/STEP/ALTER/OCTAVE, REST, GRACE, TYPE, DOT, ACCIDENTAL, STEM, NOTATIONS
   and its articulation / fermata / dynamics / slide / falloff / breath-mark
   children). Drop the `where`-based guard in `characters()`: accumulate
   character data **unconditionally** and read the buffer in each leaf element's
   `endElement` (the existing clear-on-`startElement` keeps each leaf's text
   fresh). This removes the silent-empty-value risk of forgetting to add a new
   text-bearing state to the guard.
2. Resolve each note's `ElementType` from the `<type>` token + `<rest/>`/`<grace>`
   presence (Phase 1 inverse table); create via `ElementType.newInstance()` and
   append using the `appendToCurrentLine` pattern (`MusicXmlReader.java:409`).
3. Invert pitch: `<step>` + `<octave>` + `<alter>` are **authoritative for pitch**
   (key-independent). Set `staffPositionSp` from (`<step>`,`<octave>`) via the
   Phase 2 helper → `setStaffPosition`. Set the *displayed* accidental glyph from
   `<accidental>` (**not** `<alter>`) + `setAccidentalInParentheses` from the
   cautionary/parentheses attrs — pitch and glyph stay distinct.
4. Set `dotCount` from `<dot/>` count; when `<stem>` is present set `upper` from
   its value and `stemDirectionAuto = false`; when absent `stemDirectionAuto = true`.
5. Rebuild articulations (`addArticulation`), fermata / dynamic attachments
   (`addAttachment`), the `StaffElement.slide` field via `setGlissando()`, and
   fall via `setFall()`: pair a `<slide type="start">` with the **next** note's
   `<slide type="stop">` using a `pendingSlideStart` field (mirroring
   `pendingRepeatRight`) — call `setGlissando()` on the start note when the stop
   is seen. **Dangling start** (a `<slide type="start">` whose `<stop>` never
   arrives, e.g. a truncated file): **drop it** — do not call `setGlissando()`
   (a glissando needs two notes) — and log at the part-end flush. `<falloff>` →
   `setFall()` on that note. Add an inline ASCII diagram of the new `Where`
   states and the `pendingSlideStart` start/stop pairing.
   **Ignore** each slide's `default-x`/`default-y` (SongScribe re-renders the
   geometry). Restore `xOffsetPx` from the **note's** `relative-x` tenths → ss
   → px (`ScaleContext.ssToRoundedPx`); **ignore** the note's `default-x` (the
   write-forward computed base, recomputed by layout on load).
6. Breath-mark read path: when `<breath-mark>` appears inside `<notations>`,
   append a `BREATH_MARK` element to the current line immediately after the
   current note. `BREATH_MARK` remains a standalone `ElementType` — no attachment
   needed.
7. Run `./scripts/compile.sh` → must report SUCCESS.

---

## ✅ Phase 6a: Round-Trip Tests: Helpers and Infrastructure

**Status:** Complete  <br>
**BlockedBy:** 4, 5  <br>
**Recommended model/effort:** Sonnet 4.6, low — stand-alone unit tests for the
mapping helpers and one-time schema validator setup; no round-trip harness needed.

### Tasks

1. Add a unit test for the Phase 2 `PitchSpelling` bijection: forward∘inverse is
   identity across the full staff-position range × accidental set, for both
   `KeyType` values, exercising the `<alter>` ↔ sounding-alteration mapping in
   **both** directions.
2. Add a `NoteTypeMapping.ticks` unit test over all 6 note types × 3 dot counts,
   asserting exact-integer results (no truncation) and the expected tick values.
3. **Make `MusicXmlSchemaValidator` compile the XSD once** — hold the compiled
   `Schema` in a `static` field rather than recompiling per validator instance,
   since the subsequent phases add many schema-valid note cases and the MusicXML
   4.0 XSD (with its `.mod`/`.ent` imports) is expensive to parse. Add
   `*WriterOutputIsSchemaValid` tests in `MusicXmlRoundTripTest` (using the
   `MusicXmlSchemaValidator` helper — there is **no** separate
   `MusicXmlWriterSchemaTest` class) so populated note output validates against
   `docs/musicxml-4.0-schema/`.
4. Run `./scripts/compile.sh` → must report SUCCESS.

---

## ✅ Phase 6b: Round-Trip Tests: Note Cases

**Status:** Complete  <br>
**BlockedBy:** 4, 5, 6a  <br>
**Recommended model/effort:** Sonnet 4.6, medium — the bulk of the round-trip
coverage; builds the `assertNoteEquals` helper and exercises every note-level
field through the full write→read cycle.

### Tasks

1. Extend `MusicXmlRoundTripTest` with songs exercising every duration
   (whole→32nd), rests, grace, each dot count (0/1/2), all native accidentals
   incl. cautionary/parenthesized, both stem directions with auto/manual override,
   and a non-zero X offset. Assert equality with a **new test-side helper**
   `assertNoteEquals(expected, actual)` (field-by-field: type, staffPosition,
   dotCount, accidental, parens, upper, stemDirectionAuto, xOffset, glissando,
   fall, articulations, fermata, dynamics, breath-mark). Do **not** add
   `equals()`/`hashCode()` to `StaffElement` — it breaks `Line.getElementIndex`
   (accidental lookup + tie/beam/tuplet/hairpin anchors), the layout/area-cache
   hash collections, and recurses via the `line`/parent back-references.
2. Add round-trip cases for fermata, dynamics, accent, staccato, the
   `slide` field — glissando (slide start/stop across two notes) and fall
   (`<falloff>` and back); assert exact reload via `assertNoteEquals`. Include a
   breath-mark round-trip case: a note followed by `BREATH_MARK` → serialized
   as `<breath-mark/>` in the note's notations → reloaded as a `BREATH_MARK`
   element after the note.
3. Add explicit round-trip cases for the two `<alter>`↔`<accidental>` divergence
   paths: (a) a note altered **by key** with no glyph (`<alter>≠0`, no
   `<accidental>`), and (b) a **cautionary natural** (`<accidental>natural`,
   `<alter>=0`); assert staffPosition, pitch, and the displayed accidental all
   survive. **Additionally**, since the reader ignores `<alter>` for pitch (so
   round-trip cannot catch a wrong `<alter>`), add a **writer-output assertion**
   parsing the emitted `<alter>` value for both cases — this is the only test that
   protects external-renderer sounding fidelity.
4. Run `./scripts/compile.sh`, then `./scripts/test.sh unit` → both must be green.

---

## ✅ Phase 6c: Round-Trip Tests: Edge Cases and Final Verification

**Status:** Complete  <br>
**BlockedBy:** 6a, 6b  <br>
**Recommended model/effort:** Sonnet 4.6, low — write-only and reader edge-case
tests that the round-trip harness cannot catch; completes with a full isolation
and green-suite check.

### Tasks

1. Add a writer-output test asserting grace export by parsing the emitted XML:
   `<grace>` is present with **no** `steal-time-following` attribute (and no
   `<duration>`) — round-trip can't catch this, since the reader derives grace
   from `<grace>`/`<type>` and never compares a steal value. In the same spirit,
   assert by parsing the emitted note that a non-zero X offset is written as
   `relative-x` (not `default-x`) and that `default-x` equals the computed base
   (laid-out X − offset), so `default-x` + `relative-x` reproduces the laid-out X
   — round-trip can't catch this either, since the reader ignores `default-x`.
   **Also assert the emitted `<slide>` endpoint `default-x`/`default-y` equal the
   computed endpoints** (write-forward only, reader-ignored — round-trip can't
   catch them).
2. Add a reader test for a **dangling `<slide type="start">`** (no matching
   `<stop>`, e.g. truncated input): the start note has **no** glissando
   (`hasGlissando()` is false) and parsing completes without error (the drop + log
   path).
3. Add a `characters()`-isolation test: with the reader accumulating character
   data unconditionally, verify text does **not** bleed across adjacent leaf
   elements — e.g. a note whose populated text children surround an empty/absent
   one parses to the correct per-leaf values (the clear-on-`startElement` reset
   keeps each leaf fresh).
4. Ensure per-test isolation — fresh `Song`, no leaked static state between cases
   (prior phases hit shared-state test bugs; see the "missing reset before test"
   fix).
5. Run `./scripts/compile.sh`, then `./scripts/test.sh unit` → both must be green
   before the phase is done.

---

## Verification (whole sub-plan)

- `./scripts/compile.sh` reports SUCCESS after every phase.
- Writer output for a note-populated song validates against
  `docs/musicxml-4.0-schema/`.
- `Song → MusicXML → Song` is lossless (verified via `assertNoteEquals`) for every
  duration, rest, grace, dot count, native accidental (incl.
  cautionary/parenthesized), stem state (incl. manual override), X offset,
  articulation (accent/staccato), fermata, dynamics, glissando, fall, and
  breath-mark.
- The `PitchSpelling` forward∘inverse identity holds across the full staff range.
- The Phase 2 structural round-trip (multi-line, barlines, repeats) and the
  empty-song fallback still pass — no regression.

---
comments:
  c10:
    body: Any questions about my comments?
    by: user
    at: "2026-06-17T22:20:56.915Z"
  c11:
    body: >-
      Confirmed against note.mod:268-269 — natural-flat and natural-sharp are
      native accidental tokens, so NATURAL_FLAT/NATURAL_SHARP map directly.
      Updated Uncertainty #4 and Phase 1 Task 3.
    by: AI
    at: "2026-06-17T22:30:00.000Z"
    re: c1
  c12:
    body: >-
      Removed. DOUBLE_NATURAL dropped from Uncertainty #4 and Phase 1 Task 3 —
      no mapping, no other+smufl TODO.
    by: AI
    at: "2026-06-17T22:30:00.000Z"
    re: c3
  c13:
    body: >-
      Recorded. Uncertainty #5 resolved to max 2 dots; smallest fraction is a
      double-dotted 32nd. Phase 3 Tasks 1-2 updated to use it directly.
    by: AI
    at: "2026-06-17T22:30:00.000Z"
    re: c4
  c14:
    body: Applied. Dropped the `glissando` tag. CONNECTED -> <slide type="start">
      on the first note and <slide type="stop"> on the second; SLIDE_OUT ->
      <falloff>, both with line-shape="straight" line-type="solid". Phase 1 tags
      and Phases 4-6 updated.
    by: AI
    at: "2026-06-17T22:30:00.000Z"
    re: c5
  c15:
    body: Done. No DOUBLE_NATURAL mapping and no other+smufl handling.
    by: AI
    at: "2026-06-17T22:30:00.000Z"
    re: c6
  c16:
    body: Removed the other+smufl TODO entirely.
    by: AI
    at: "2026-06-17T22:30:00.000Z"
    re: c7
  c17:
    body: Fixed. *_REST keeps the same <type> token; a rest is marked by a <rest/>
      child within <note>, not a flag. Phase 1 Task 2 updated.
    by: AI
    at: "2026-06-17T22:30:00.000Z"
    re: c8
  c18:
    body: >-
      Captured in Uncertainty #6 and Phase 1 Task 2 / Phase 4 Task 2:
      <grace slash="no" steal-time-following="N"/>, no <duration>, stem always
      up via default-y. N is the 16th's percentage of the host (following)
      note's duration (25 quarter, 13 half rounded up, dots accounted for); the
      writer peeks ahead to the host.
    by: AI
    at: "2026-06-17T22:30:00.000Z"
    re: c9
  c19:
    body: >-
      Asked two clarifying questions; you confirmed peek-ahead for
      steal-time-following and that glissandos are carried on notes via
      StaffElement.glissando (GLISSANDO never persisted standalone). All
      comments resolved and the plan updated.
    by: AI
    at: "2026-06-17T22:30:00.000Z"
    re: c10
  c20:
    body: >-
      Updated. Field is `slide`, test via `hasGlissando()`. All references in
      In scope, Key touchpoints, Phase 4 Task 6, Phase 5 Task 5, and Phase 6
      updated accordingly.
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c20
  c21:
    body: >-
      Removed. Decomposition rationale was redundant with the Plan table's
      Recommended model column.
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c21
  c22:
    body: >-
      Kept. The Purpose section orients implementers to phase scope and success
      criteria — it is the single authoritative statement of what this plan
      delivers.
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c22
  c23:
    body: >-
      Removed. Flagged uncertainties section dropped; all resolved content folded
      into the relevant phase tasks.
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c23
  c24:
    body: >-
      Resolved. Breath-mark is guaranteed non-line-leading. Writer peeks ahead;
      if next element is BREATH_MARK, emits <breath-mark/> in notations and
      skips the element in the outer loop. Reader appends a BREATH_MARK element
      after the current note. Folded into Phase 4 Task 6 and Phase 5 Task 6.
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c24
  c25:
    body: Removed. BlockedBy breath-mark dependency dropped from Dependencies.
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c25
  c26:
    body: >-
      Confirmed — x1Translate/x2Translate are gone; Glissando now holds only
      transient cached geometry (cachedStartX/Y, angle, etc.). Coordination item
      removed from Dependencies.
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c26
  c27:
    body: >-
      Annotation.Placement enum already exists in the codebase; the refactor plan
      file was never created. Sequencing dependency removed from Dependencies.
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c27
  c29:
    body: Removed "(Uncertainty #5)" reference from Phase 3 Task 1.
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c29
  c30:
    body: >-
      Resolved as global. DIVISIONS = 480 is a global constant: double-dotted
      32nd = 480/8 × 7/4 = 105 ticks (exact integer). Phase 3 Task 2 updated
      with the arithmetic justification.
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c30
  c31:
    body: >-
      Updated. Phase 4 description now states the breath-mark peek-ahead rule
      and grace steal-time-following reference inline; Flagged Uncertainties
      cross-reference removed.
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c31
  c32:
    body: Updated Phase 4 Task 6 to use hasGlissando().
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c32
  c33:
    body: Removed x1Translate/x2Translate parenthetical from Phase 4 Task 6.
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c33
  c34:
    body: Updated Phase 4 Task 6 — fall is hasFall() / setFall(); no boolean argument.
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c34
  c35:
    body: Resolved in Phase 4 Task 6 — breath-mark peek-ahead approach described inline.
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c35
  c36:
    body: Updated Phase 5 Task 5 — StaffElement.slide field, setGlissando().
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c36
  c37:
    body: Removed CONNECTED from Phase 5 Task 5; replaced with setGlissando() call pattern.
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c37
  c38:
    body: Resolved in Phase 5 Task 6 — BREATH_MARK appended as standalone element after current note.
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c38
  c39:
    body: Updated assertNoteEquals field list to glissando, fall, (separate fields).
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c39
  c40:
    body: Updated Phase 6 Task 3 — "slide field" instead of "glissando".
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c40
  c41:
    body: Updated Phase 6 Task 3 — "glissando" instead of "CONNECTED".
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c41
  c42:
    body: Resolved — breath-mark round-trip case added to Phase 6 Task 3.
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c42
  c43:
    body: Removed "(CONNECTED)" from Verification.
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c43
  c44:
    body: Removed "(note attribute)" from Verification.
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c44
  c45:
    body: Updated Verification — breath-mark now included in losslessness list.
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c45
  c46:
    body: >-
      Plan review (/review-plan) amendments applied. (1) Merged NoteTypeMapping +
      NoteDuration into one class — token map and ticks() share one ElementType
      table. (2) PitchSpelling now reuses StaffElement.getPitchIndex() for the
      diatonic lattice (single source of truth). (3) Grace export made faithful:
      playback gives grace notes zero duration and never steals from the host, so
      the steal-time-following formula, host peek-ahead, and SIXTEENTH_TICKS were
      dropped — grace emits <grace slash="no"/> with no steal (this also removed
      the grace no-host NPE edge case). (4) Fixed three stale code references:
      ScaleContext is static (no getInstance()); there is no MusicXmlWriterSchemaTest
      (schema checks use the MusicXmlSchemaValidator helper inside
      MusicXmlRoundTripTest); and SLIDE is a distinct standalone ElementType, not
      the per-note slide field. (5) Dangling <slide type="start"> on read now
      drops + logs (lenient read). (6) Added write-side tests for the
      round-trip-invisible values: emitted <alter> (key-altered + cautionary),
      <slide>/<note> default-x/default-y, plus a dangling-slide reader test and a
      characters() no-bleed test. (7) MusicXmlSchemaValidator to compile the XSD
      once via a static Schema field. (8) Inline ASCII diagrams called out for
      NoteTypeMapping, PitchSpelling, writeNote child-order, and the reader Where
      states. Kept write-forward-only default-x/default-y (external-renderer
      fidelity). Left findLastAccidental's O(n^2) write path as-is (small lines;
      reusing it keeps <alter> consistent with playback).
    by: AI
    at: "2026-06-28T00:00:00.000Z"
    re: c46
