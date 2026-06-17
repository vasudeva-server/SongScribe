# Sub-plan: Phase 3 — Notes & Per-Note Attachments

**Type:** Sub-plan  <br>
**Parent:** [musicxml-conversion.md](./musicxml-conversion.md) → Phase 3  <br>
**Created:** 2026-05-29  <br>
**Status:** In Progress  <br>
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
  → `<accidental>`; stem up/down + manual-override flag → `<stem>` (+ extension);
  X offset (px → tenths) → `<note default-x>`.
- Per-note articulations / notations: accent, staccato, fermata, dynamics,
  breath mark, glissando / slide (+ x1/x2 translate offsets), and the standalone
  `ElementType.GLISSANDO` element.

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

### Decomposition rationale

- **Phase 1 (tags + lookup tables)** is pure mechanical data entry mirroring
  `MusicXmlTags` and `BarlineStyleMapping` — Sonnet/Haiku.
- **Phase 2 (pitch bijection)** is the one genuinely reversible-boundary problem:
  diatonic step/octave from a staff position, sounding `<alter>` from the
  effective accidental/key, and the exact inverse. This is the Opus phase.
- **Phase 3 (divisions + durations)** is bounded arithmetic with one locked
  design decision (the `<divisions>` value) — Sonnet, given the divisibility
  rule stated below.
- **Phases 4 & 5 (writer / reader)** assemble the building blocks from 1–3 into
  schema-ordered emission and the symmetric SAX parse — mechanical Sonnet work,
  except the reader's structural re-collapse (Phase 5) which leans on the
  Phase 2 inverse and so is rated slightly higher.
- **Phase 6 (tests)** is isolated last so the bijection and round-trip are
  pinned by assertions rather than inline checks.

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
- **Note model**: `dom/StaffElement.java` — `getType`, `getStaffPosition`,
  `getDotCount`, `getAccidental`, `isAccidentalInParentheses`, `isUpper`,
  `isStemDirectionAuto`, `getXOffsetPx`, `getGlissando`, `getArticulations`,
  `findAttachment`, and the pitch internals `getPitch`/`calculatePitch`/
  `getPitchIndex`/`MIDI_PITCHES`/`MIDI_PITCH_ADJUSTMENT`/`findLastAccidental`.
  Inner enums: `StaffElement.Accidental` (NATURAL, FLAT, SHARP, DOUBLE_NATURAL,
  DOUBLE_FLAT, DOUBLE_SHARP, NATURAL_FLAT, NATURAL_SHARP) and
  `StaffElement.Glissando` (`type` ∈ CONNECTED/SLIDE_OUT, `x1Translate`,
  `x2Translate`).
- **Element types**: `dom/ElementType.java` note constants
  (`SEMIBREVE`…`DEMI_SEMIQUAVER`), rests (`*_REST`), `GRACE_QUAVER`,
  `GLISSANDO`, `BREATH_MARK`; `isBarLine()`/`isRepeat()` already used in Phase 2.
- **Articulations / attachments**: `dom/ArticulationType` (STACCATO, ACCENT),
  `dom/FermataAttachment`, `dom/DynamicAttachment` (`getType()`).
- **Reference serialization** to mirror (NOT reuse): `io/StaffElementIO.writeElement`
  and its `XML_*` field-by-field handling — shows exactly which fields the legacy
  format persists and their guards (e.g. emit X offset only when non-zero).
- **Unit conversion** (X offset px ↔ tenths): `ScaleContext.getInstance().pxToSs`
  / `ssToRoundedPx` (see `.agents/guides/unit-conversion.md`). Tenths = ss × 10;
  do not hardcode either factor. `StaffExtents.spToSs`/`ssToSp` handle staff
  positions if needed.
- **Schema** for write-side validation: `docs/musicxml-4.0-schema/`. The strict
  child order of `<note>` is defined there and **must** be honored
  (`grace?, chord?, (pitch|rest), duration?, tie*, …, type?, dot*, accidental?,
  …, stem?, …, notations*, lyric*`).

### Flagged uncertainties (resolve during implementation)

1. **Manual stem-override storage.** `<stem>` is optional in MusicXML, so emit
   it only when `isStemDirectionAuto() == false` (manual override). On read,
   absence of `<stem>` means `stemDirectionAuto = true`. No extension element
   needed. **Resolved.**
2. **Dynamics placement.** Use `<notations><dynamics>` — dynamics are attached
   to a specific note, so note-local notations is the correct placement.
   **Resolved.**
3. **Breath-mark re-association.** `BREATH_MARK` is a *standalone*
   `ElementType` element, but maps to `<breath-mark>` on the **preceding** note.
   *Recommended:* writer attaches the `<breath-mark>` articulation to the
   previous note's `<notations>`; reader re-inserts a standalone `BREATH_MARK`
   element immediately *after* the carrying note. Verify a `BREATH_MARK` can
   never be the first element on a line (no preceding note) — if it can, fall
   back to an `<other-direction>` so it still round-trips.
4. **Composite accidentals.** `NATURAL_FLAT`, `NATURAL_SHARP`, `DOUBLE_NATURAL`
   have no single MusicXML `<accidental>` enum value. *Recommended:* emit the
   closest native token plus a `smufl`/`<other-*>` attribute preserving the exact
   glyph; resolve by reading the schema's accidental-value enumeration before
   committing. The simple cases (NATURAL/FLAT/SHARP/DOUBLE_FLAT/DOUBLE_SHARP)
   map natively.
5. **Maximum dot count.** Confirm the model/UI bound on
   `StaffElement.getDotCount()` (inspect `setDotCount` call sites). This sets the
   smallest representable note fraction and therefore the `<divisions>`
   divisibility requirement in Phase 3.
6. **Grace note shape.** `GRACE_QUAVER` → `<grace/>` + `<type>eighth</type>` with
   **no** `<duration>` (MusicXML rule). Confirm grace notes never carry dots in
   the model; if they can, fold that into the duration helper.

## Dependencies

- **Phase 2 of the master plan (Structural Model)** — ✅ complete. Phase 3 plugs
  notes into the line-driven measure structure it built; the measure-segmentation
  and barline/repeat logic is untouched.
- **Must not regress:** the existing structural round-trip (multi-line songs,
  assorted barlines, `REPEAT_LEFT_RIGHT` pairs) and the empty-song fallback
  (`writeEmptySongMeasure`). The `MusicXmlRoundTripTest` and
  `MusicXmlWriterSchemaTest` suites must stay green.
- **No external blockers.**

## Plan

| Phase | Description | Status | Recommended model |
|-------|-------------|--------|-------------------|
| 1 | [Tags and Lookup Tables](#-phase-1-tags-and-lookup-tables) | ⏳ Pending | Sonnet 4.6 / Haiku 4.5, low |
| 2 | [Pitch and Staff Position Bijection](#-phase-2-pitch-and-staff-position-bijection) | ⏳ Pending | Opus 4.8, high |
| 3 | [Divisions and Duration Mapping](#-phase-3-divisions-and-duration-mapping) | ⏳ Pending | Sonnet 4.6, medium |
| 4 | [Writer Note Emission](#-phase-4-writer-note-emission) | ⏳ Pending | Sonnet 4.6, medium |
| 5 | [Reader Note Parsing](#-phase-5-reader-note-parsing) | ⏳ Pending | Opus 4.8, medium |
| 6 | [Round-Trip Tests](#-phase-6-round-trip-tests) | ⏳ Pending | Sonnet 4.6, low |

---

## ⏳ Phase 1: Tags and Lookup Tables

**Status:** Pending  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Sonnet 4.6 / Haiku 4.5, low — mechanical constant
and lookup-table entry mirroring `MusicXmlTags` and `BarlineStyleMapping`; no
control flow.

### Tasks

1. Add the `<note>`-subtree element, attribute, and value name constants to
   `MusicXmlTags`, grouped with comments in the existing style: `note`, `pitch`,
   `step`, `alter`, `octave`, `rest`, `grace`, `duration`, `type`, `dot`,
   `accidental` (+ `cautionary`/`parentheses` attrs), `stem`, `notations`,
   `articulations`, `accent`, `staccato`, `fermata`, `dynamics`, `breath-mark`,
   `slide`, `glissando`, `ornaments`, `other-notation`, and `default-x`.
2. Create `NoteTypeMapping` (mirroring `BarlineStyleMapping`): a bidirectional
   map between `ElementType` note/rest/grace constants and the MusicXML `<type>`
   token — `SEMIBREVE`→`whole`, `MINIM`→`half`, `CROTCHET`→`quarter`,
   `QUAVER`→`eighth`, `SEMIQUAVER`→`16th`, `DEMI_SEMIQUAVER`→`32nd`. Each
   `*_REST` maps to the same token plus a "is rest" flag; `GRACE_QUAVER`→`eighth`
   plus a "is grace" flag. Forward (write) and inverse (read) lookups.
3. Add an `Accidental` ↔ MusicXML mapping (token + `<alter>` semitone): `NATURAL`
   →`natural`/0, `FLAT`→`flat`/−1, `SHARP`→`sharp`/+1, `DOUBLE_FLAT`→`flat-flat`/
   −2, `DOUBLE_SHARP`→`double-sharp`/+2. Leave composite entries
   (`NATURAL_FLAT`/`NATURAL_SHARP`/`DOUBLE_NATURAL`) as a documented TODO resolved
   per Flagged Uncertainty #4 — do not guess a token here.
4. Run `./scripts/compile.sh` → must report SUCCESS.

---

## ⏳ Phase 2: Pitch and Staff Position Bijection

**Status:** Pending  <br>
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
   `<octave>`), diatonic and independent of accidental. Document which
   `staffPosition` value corresponds to which pitch (the origin).
3. Implement `<alter>` derivation: the **sounding** alteration from the explicit
   accidental when present, else from the effective key (`findLastAccidental`),
   so the emitted `<pitch>` sounds correct even with no visible accidental.
4. Implement the inverse: (`<step>`, `<octave>`) → `staffPositionSp`. The
   *displayed* accidental is recovered separately from `<accidental>` (Phase 5),
   **not** from `<alter>` — keep the two paths distinct.
5. Resolve and document the independence of sounding `<alter>` vs displayed
   `<accidental>`: a note can sound altered via key with no glyph, and a
   cautionary natural can show a glyph with no key alteration.
6. Hand-verify (reasoning + a scratch check) that forward∘inverse is identity
   across the full staff-position range and accidental set; the formal assertion
   lands in Phase 6.
7. Run `./scripts/compile.sh` → must report SUCCESS.

---

## ⏳ Phase 3: Divisions and Duration Mapping

**Status:** Pending  <br>
**BlockedBy:** 1  <br>
**Recommended model/effort:** Sonnet 4.6, medium — bounded arithmetic with one
locked decision (`<divisions>`); the divisibility rule below removes the design
guesswork.

### Tasks

1. Determine the maximum dot count a `StaffElement` can carry (inspect
   `setDotCount` call sites / UI) — this fixes the smallest representable note
   fraction (Flagged Uncertainty #5).
2. Lock the `<divisions>` value: the requirement is that `DIVISIONS` integer-
   divides the tick value of the smallest supported (possibly multi-dotted) note.
   Verify the current `480` satisfies it for a 32nd note at the established max
   dot count; replace the provisional comment at `MusicXmlWriter.java:33-34` with
   the justification, or change the value if the dot analysis demands it.
3. Implement `<duration>` tick computation in a helper: base ticks from the
   `ElementType` note value (whole = 4×`DIVISIONS`, half = 2×, quarter = 1×,
   eighth = ½, 16th = ¼, 32nd = ⅛) × dot augmentation
   (`×(2 − 2^−dotCount)`), as an exact integer. Assert exact division (no
   truncation) — a non-integer result means `DIVISIONS` is wrong.
4. Encode the special-shape rules: grace notes emit `<type>` but **no**
   `<duration>`; rests emit `<duration>` + `<type>` + `<rest/>`.
5. Run `./scripts/compile.sh` → must report SUCCESS.

---

## ⏳ Phase 4: Writer Note Emission

**Status:** Pending  <br>
**BlockedBy:** 1, 2, 3  <br>
**Recommended model/effort:** Sonnet 4.6, medium — schema-ordered assembly of the
Phase 1–3 building blocks into the existing `XML`/`MusicXmlWriter` idiom; the only
subtlety is the breath-mark/standalone-glissando re-mapping, whose rule is fixed
in the Flagged Uncertainties.

### Tasks

1. In `writeLineDrivenMeasures`, replace the "note content arrives in Phase 3"
   stub (`MusicXmlWriter.java:158`) with a `writeNote(...)` call for note / rest /
   grace elements (leave the barline/repeat branches untouched).
2. Emit `<note>` children in **strict schema order**: `<grace/>` → `<rest/>` |
   `<pitch>`(step/alter/octave) → `<duration>` → `<type>` → `<dot/>`×n →
   `<accidental>` → `<stem>` → `<notations>`. Validate ordering against
   `docs/musicxml-4.0-schema/`.
3. Pitch via the Phase 2 helper; accidental glyph via the Phase 1 table with
   `cautionary="yes"` / `parentheses="yes"` driven by `isAccidentalInParentheses()`.
   Emit `<stem>up|down` from `isUpper()` only when `isStemDirectionAuto() == false`; omit otherwise.
4. `<note default-x>` from `getXOffsetPx()` via `ScaleContext.pxToSs` → ×10
   tenths; emit only when non-zero (mirror the legacy `writeElement` guard).
5. `<notations>` body: accent / staccato from `getArticulations()`; fermata from
   `FermataAttachment`; dynamics from `DynamicAttachment` (per Uncertainty #2).
6. Glissando / breath-mark: per-note `Glissando` attachment (CONNECTED/SLIDE_OUT)
   → `<slide>`/`<glissando>` start/stop with x1/x2 translate → `default-x`;
   standalone `ElementType.GLISSANDO` → `<glissando>` line; `ElementType.BREATH_MARK`
   → `<breath-mark>` on the preceding note (per Uncertainty #3).
7. Run `./scripts/compile.sh` → SUCCESS; spot-check one populated sample validates
   against `docs/musicxml-4.0-schema/`.

---

## ⏳ Phase 5: Reader Note Parsing

**Status:** Pending  <br>
**BlockedBy:** 1, 2, 3  <br>
**Recommended model/effort:** Opus 4.8, medium — symmetric SAX parse; rated above
the writer because the structural re-collapse (breath-mark / standalone glissando)
and the pitch inverse must reproduce the writer's choices exactly.

### Tasks

1. Extend the `Where` enum and the SAX handlers with `NOTE` and its child states
   (PITCH/STEP/ALTER/OCTAVE, REST, GRACE, TYPE, DOT, ACCIDENTAL, STEM, NOTATIONS
   and its articulation / fermata / dynamics / slide / glissando / breath-mark /
   other-notation children).
2. Resolve each note's `ElementType` from the `<type>` token + `<rest/>`/`<grace/>`
   presence (Phase 1 inverse table); create via `ElementType.newInstance()` and
   append using the `appendToCurrentLine` pattern (`MusicXmlReader.java:409`).
3. Invert pitch: (`<step>`,`<octave>`) → `staffPositionSp` via the Phase 2 helper
   → `setStaffPosition`. Set the *displayed* accidental from `<accidental>` (not
   `<alter>`) + `setAccidentalInParentheses` from the cautionary/parentheses attrs.
4. Set `dotCount` from `<dot/>` count; when `<stem>` is present set `upper` from
   its value and `stemDirectionAuto = false`; when absent `stemDirectionAuto = true`.
5. Rebuild articulations (`addArticulation`), fermata / dynamic / glissando
   attachments (`addAttachment`), and `xOffsetPx` from `default-x` tenths → ss →
   px (`ScaleContext.ssToRoundedPx`).
6. Re-collapse `<breath-mark>` and the standalone `<glissando>` line back into the
   standalone `ElementType.BREATH_MARK` / `GLISSANDO` elements at the correct
   position (inverse of Phase 4 / Uncertainty #3).
7. Run `./scripts/compile.sh` → must report SUCCESS.

---

## ⏳ Phase 6: Round-Trip Tests

**Status:** Pending  <br>
**BlockedBy:** 4, 5  <br>
**Recommended model/effort:** Sonnet 4.6, low — extend the existing
`MusicXmlRoundTripTest` / `MusicXmlWriterSchemaTest` harness with note cases; no
new infrastructure.

### Tasks

1. Add a unit test for the Phase 2 `PitchSpelling` bijection: forward∘inverse is
   identity across the full staff-position range × accidental set, for both
   `KeyType` values.
2. Extend `MusicXmlRoundTripTest` with songs exercising every duration
   (whole→32nd), rests, grace, each dot count, all native accidentals incl.
   cautionary/parenthesized, both stem directions with auto/manual override, and
   a non-zero X offset; assert `StaffElement`-level equality.
3. Add round-trip cases for fermata, dynamics, accent, staccato, breath-mark,
   the glissando attachment (CONNECTED/SLIDE_OUT) with translate offsets, and the
   standalone `GLISSANDO` element; assert exact reload.
4. Extend `MusicXmlWriterSchemaTest` so populated note output validates against
   `docs/musicxml-4.0-schema/`.
5. Run `./scripts/compile.sh`, then `./scripts/test.sh unit` → both must be
   green before the phase is done.

---

## Verification (whole sub-plan)

- `./scripts/compile.sh` reports SUCCESS after every phase.
- Writer output for a note-populated song validates against
  `docs/musicxml-4.0-schema/`.
- `Song → MusicXML → Song` is lossless at `StaffElement` level for every duration,
  rest, grace, dot count, native accidental (incl. cautionary/parenthesized),
  stem state (incl. manual override), X offset, articulation (accent/staccato),
  fermata, dynamics, breath mark, and glissando (attachment + standalone).
- The `PitchSpelling` forward∘inverse identity holds across the full staff range.
- The Phase 2 structural round-trip (multi-line, barlines, repeats) and the
  empty-song fallback still pass — no regression.
