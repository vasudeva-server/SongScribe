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
  → `<accidental>`; stem up/down + manual-override flag → `<stem>` (emitted only on manual override);
  X offset (px → tenths) → `<note relative-x>`, with the computed base X in
  write-forward-only `<note default-x>` (MusicXML renders `default-x` +
  `relative-x`, so the offset must live in `relative-x`; the reader ignores
  `default-x`).
- Per-note articulations / notations: accent, staccato, fermata, dynamics,
  breath mark, and glissando carried on the note via the `StaffElement.glissando`
  field — `CONNECTED` (→ `<slide>` start/stop) and `SLIDE_OUT` (→ `<falloff>`).
  Each `<slide>`/`<falloff>` carries **computed** endpoint `default-x`/`default-y`
  (write-forward only, for external rendering fidelity; the reader ignores them
  and re-renders).

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
- **New classes (4):** `NoteTypeMapping`, `AccidentalMapping`, `PitchSpelling`,
  and `NoteDuration` — each a single-responsibility helper mirroring the existing
  `BarlineStyleMapping` idiom. No other new production classes are introduced.
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
  `BREATH_MARK`; `isBarLine()`/`isRepeat()` already used in Phase 2.
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
3. **Breath-mark re-association.** ⛔ **BlockedBy a model change.** Today
   `BREATH_MARK` is a *standalone* `ElementType` and the model permits it as the
   first element on a line (confirmed: `Line.addElement` enforces no
   "must-follow-a-note" invariant), so "attach to the preceding note" would
   migrate a line-leading breath mark onto the **previous line** and break the
   round-trip. The model is being changed so breath marks are *attached to a
   note* and can never be line-leading. Until that lands, the breath-mark
   write/read treatment here is **TBD**: if it becomes an attachment it is
   handled like other per-note attachments (no standalone re-collapse), and the
   `<other-direction>` fallback is no longer needed.
4. **Composite accidentals.** `NATURAL_FLAT` → `natural-flat` and
   `NATURAL_SHARP` → `natural-sharp` are valid native `<accidental>` tokens
   (`note.mod:268–269`). `DOUBLE_NATURAL` is **not supported** by SongScribe, so
   it needs no mapping. The simple cases (NATURAL/FLAT/SHARP/DOUBLE_FLAT/
   DOUBLE_SHARP) map natively. **Resolved.**
5. **Maximum dot count.** The maximum dot count is **2**. This fixes the smallest
   representable note fraction (a double-dotted 32nd) and therefore the
   `<divisions>` divisibility requirement in Phase 3. **Resolved.**
6. **Grace note shape.** `GRACE_QUAVER` emits, with **no** `<duration>`:
   `<grace slash="no" steal-time-following="N"/>`, `<pitch>`, `<type>eighth</type>`,
   and `<stem default-y="…">up</stem>` (grace stems are **always up**; `default-y`
   sets the stem length in tenths). The grace steals a 16th-note duration from
   its **host** (the following note), so `steal-time-following` is the 16th's
   percentage of the host's duration — 25 for a quarter, 13 for a half (round
   up), and so on, **accounting for the host's dots**. The writer peeks ahead to
   the host note to compute it. Grace notes never carry dots. **Resolved.**

## Dependencies

- **Phase 2 of the master plan (Structural Model)** — ✅ complete. Phase 3 plugs
  notes into the line-driven measure structure it built; the measure-segmentation
  and barline/repeat logic is untouched.
- **Must not regress:** the existing structural round-trip (multi-line songs,
  assorted barlines, `REPEAT_LEFT_RIGHT` pairs) and the empty-song fallback
  (`writeEmptySongMeasure`). The `MusicXmlRoundTripTest` and
  `MusicXmlWriterSchemaTest` suites must stay green.
- **BlockedBy — breath-mark model change:** breath marks must become
  note-attached (never line-leading) before the breath-mark write/read path can
  be finalized (see Uncertainty #3).
- **Coordinated with — `Glissando` field cleanup:** `x1Translate`/`x2Translate`
  are being removed from the model separately; Phase 3 ignores them entirely and
  emits computed slide endpoint coordinates instead.
- **Sequenced before — annotation `Placement` enum refactor:** the model cleanup
  in [annotation-placement-refactor.md](./annotation-placement-refactor.md) lands
  before Phase 3 implementation begins (sequencing, not a code dependency —
  Phase 3 does not touch annotations), so the position-offset convention is
  established on a clean model across the codebase.

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
   `step`, `alter`, `octave`, `rest`, `grace` (+ `slash` / `steal-time-following`
   attrs), `duration`, `type`, `dot`, `accidental` (+ `cautionary`/`parentheses`
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
   the `<note>` (shape per Uncertainty #6). Forward (write) and inverse (read)
   lookups.
3. Create `AccidentalMapping` (own class, mirroring `BarlineStyleMapping`): an
   `Accidental` ↔ MusicXML mapping (token + `<alter>` semitone): `NATURAL`
   →`natural`/0, `FLAT`→`flat`/−1, `SHARP`→`sharp`/+1, `DOUBLE_FLAT`→`flat-flat`/
   −2, `DOUBLE_SHARP`→`double-sharp`/+2, `NATURAL_FLAT`→`natural-flat`/−1,
   `NATURAL_SHARP`→`natural-sharp`/+1. `DOUBLE_NATURAL` is not supported by
   SongScribe and needs no mapping.
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

## ⏳ Phase 3: Divisions and Duration Mapping

**Status:** Pending  <br>
**BlockedBy:** 1  <br>
**Recommended model/effort:** Sonnet 4.6, medium — bounded arithmetic with one
locked decision (`<divisions>`); the divisibility rule below removes the design
guesswork.

### Tasks

1. The maximum dot count is **2** (Uncertainty #5), so the smallest representable
   note fraction is a double-dotted 32nd.
2. Lock the `<divisions>` value: the requirement is that `DIVISIONS` integer-
   divides the tick value of the smallest supported (double-dotted) note. Verify
   the current `480` satisfies it for a double-dotted 32nd note; replace the
   provisional comment at `MusicXmlWriter.java:33-34` with the justification, or
   change the value if the dot analysis demands it.
3. Implement `<duration>` tick computation in the `NoteDuration` helper as
   `NoteDuration.ticks(type, dotCount)`: base ticks from the `ElementType` note
   value (whole = 4×`DIVISIONS`, half = 2×, quarter = 1×, eighth = ½, 16th = ¼,
   32nd = ⅛) × dot augmentation (`×(2 − 2^−dotCount)`), as an exact integer.
   Assert exact division (no truncation) — a non-integer result means `DIVISIONS`
   is wrong. Also expose `SIXTEENTH_TICKS = DIVISIONS / 4` so Phase 4's grace
   `steal-time-following` reuses this single function (no parallel duration math).
4. Encode the special-shape rules: grace notes emit `<type>` but **no**
   `<duration>`; rests emit `<duration>` + `<type>` + `<rest/>`.
5. Run `./scripts/compile.sh` → must report SUCCESS.

---

## ⏳ Phase 4: Writer Note Emission

**Status:** Pending  <br>
**BlockedBy:** 1, 2, 3  <br>
**Recommended model/effort:** Sonnet 4.6, medium — schema-ordered assembly of the
Phase 1–3 building blocks into the existing `XML`/`MusicXmlWriter` idiom; the only
subtlety is the breath-mark re-mapping and the grace-note steal-time-following
peek-ahead, whose rules are fixed in the Flagged Uncertainties.

### Tasks

1. In `writeLineDrivenMeasures`, replace the "note content arrives in Phase 3"
   stub (`MusicXmlWriter.java:158`) with a `writeNote(...)` call for note / rest /
   grace elements (leave the barline/repeat branches untouched).
2. Emit `<note>` children in **strict schema order**: `<grace>` → `<rest/>` |
   `<pitch>`(step/alter/octave) → `<duration>` → `<type>` → `<dot/>`×n →
   `<accidental>` → `<stem>` → `<notations>`. For grace, emit
   `<grace slash="no" steal-time-following="N"/>` (no `<duration>`) and
   `<stem default-y="…">up</stem>`. Compute `N` =
   `round(SIXTEENTH_TICKS / NoteDuration.ticks(hostType, hostDots) × PERCENT_SCALE)`
   with `HALF_UP` rounding (so `12.5 → 13`), where `SIXTEENTH_TICKS = DIVISIONS / 4`
   and `PERCENT_SCALE = 100` are named constants; the host is the following note
   (the UI guarantees a grace always has a host, so no no-host branch is needed).
   Validate ordering against `docs/musicxml-4.0-schema/`.
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
   `FermataAttachment`; dynamics from `DynamicAttachment` (per Uncertainty #2).
6. Glissando / breath-mark. The glissando lives on the note via the
   `StaffElement.glissando` field: `CONNECTED` → `<slide type="start"
   line-shape="straight" line-type="solid"/>` on the first note and
   `<slide type="stop" …/>` on the second; `SLIDE_OUT` → `<falloff
   line-length="short" line-shape="straight" line-type="solid"/>` on the single
   note. Each `<slide>`/`<falloff>` carries **computed** endpoint
   `default-x`/`default-y` (in tenths) for external rendering fidelity — these are
   write-forward only and not read back; layout always runs before export, so the
   coordinates are valid. (`x1Translate`/`x2Translate` are ignored — being removed
   from the model separately.) Breath-mark handling is **BlockedBy** the model
   change (Uncertainty #3) and is finalized once that lands.
7. Run `./scripts/compile.sh` → SUCCESS; spot-check one populated sample validates
   against `docs/musicxml-4.0-schema/`.

---

## ⏳ Phase 5: Reader Note Parsing

**Status:** Pending  <br>
**BlockedBy:** 1, 2, 3  <br>
**Recommended model/effort:** Opus 4.8, medium — symmetric SAX parse; rated above
the writer because the structural re-collapse (breath-mark) and the pitch inverse
must reproduce the writer's choices exactly.

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
   (`addAttachment`), and the `StaffElement.glissando` field via `setGlissando`:
   pair a `<slide type="start">` with the **next** note's `<slide type="stop">`
   using a `pendingSlideStart` field (mirroring `pendingRepeatRight`; handle a
   dangling start) → `CONNECTED`; `<falloff>` → `SLIDE_OUT`. **Ignore** each
   slide/falloff's `default-x`/`default-y` (SongScribe re-renders the geometry).
   Restore `xOffsetPx` from the **note's** `relative-x` tenths → ss → px
   (`ScaleContext.ssToRoundedPx`); **ignore** the note's `default-x` (the
   write-forward computed base, recomputed by layout on load).
6. Breath-mark read path is **BlockedBy** the model change (Uncertainty #3). Once
   breath marks become note-attached, restore them as a per-note attachment (no
   standalone re-collapse); the exact handling is finalized when that change lands.
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
   `KeyType` values, exercising the `<alter>` ↔ sounding-alteration mapping in
   **both** directions.
2. Extend `MusicXmlRoundTripTest` with songs exercising every duration
   (whole→32nd), rests, grace, each dot count (0/1/2), all native accidentals
   incl. cautionary/parenthesized, both stem directions with auto/manual override,
   and a non-zero X offset. Assert equality with a **new test-side helper**
   `assertNoteEquals(expected, actual)` (field-by-field: type, staffPosition,
   dotCount, accidental, parens, upper, stemDirectionAuto, xOffset, glissando
   type, articulations, fermata, dynamics, breath-mark). Do **not** add
   `equals()`/`hashCode()` to `StaffElement` — it breaks `Line.getElementIndex`
   (accidental lookup + tie/beam/tuplet/hairpin anchors), the layout/area-cache
   hash collections, and recurses via the `line`/parent back-references.
3. Add round-trip cases for fermata, dynamics, accent, staccato, and the
   glissando field — `CONNECTED` (slide start/stop across two notes) and
   `SLIDE_OUT` (falloff); assert exact reload via `assertNoteEquals`. (Breath-mark
   round-trip is deferred until its model change lands — see Uncertainty #3.)
4. Add a `NoteDuration.ticks` unit test over all 6 note types × 3 dot counts,
   asserting exact-integer results (no truncation) and the expected tick values.
5. Add a writer-output test asserting `steal-time-following` for representative
   hosts — quarter→25, half→13, dotted-quarter→17, eighth→50 — by parsing the
   emitted attribute (round-trip can't catch it: the value is derived on both
   sides, never compared). In the same spirit, assert by parsing the emitted
   note that a non-zero X offset is written as `relative-x` (not `default-x`)
   and that `default-x` equals the computed base (laid-out X − offset), so
   `default-x` + `relative-x` reproduces the laid-out X — round-trip can't catch
   this either, since the reader ignores `default-x`.
6. Add explicit round-trip cases for the two `<alter>`↔`<accidental>` divergence
   paths: (a) a note altered **by key** with no glyph (`<alter>≠0`, no
   `<accidental>`), and (b) a **cautionary natural** (`<accidental>natural`,
   `<alter>=0`); assert staffPosition, pitch, and the displayed accidental all
   survive.
7. Extend `MusicXmlWriterSchemaTest` so populated note output validates against
   `docs/musicxml-4.0-schema/`.
8. Ensure per-test isolation — fresh `Song`, no leaked static state between cases
   (prior phases hit shared-state test bugs; see the "missing reset before test"
   fix). Then run `./scripts/compile.sh`, then `./scripts/test.sh unit` → both
   must be green before the phase is done.

---

## Verification (whole sub-plan)

- `./scripts/compile.sh` reports SUCCESS after every phase.
- Writer output for a note-populated song validates against
  `docs/musicxml-4.0-schema/`.
- `Song → MusicXML → Song` is lossless (verified via `assertNoteEquals`) for every
  duration, rest, grace, dot count, native accidental (incl.
  cautionary/parenthesized), stem state (incl. manual override), X offset,
  articulation (accent/staccato), fermata, dynamics, and glissando
  (`CONNECTED`/`SLIDE_OUT`). Breath-mark losslessness is deferred until its model
  change lands (Uncertainty #3).
- The `PitchSpelling` forward∘inverse identity holds across the full staff range.
- The Phase 2 structural round-trip (multi-line, barlines, repeats) and the
  empty-song fallback still pass — no regression.

---
comments:
  c1:
    by: user
    at: "2026-06-17T16:11:57.169Z"
  c3:
    by: user
    at: "2026-06-17T16:17:40.736Z"
  c4:
    by: user
    at: "2026-06-17T16:23:31.507Z"
  c5:
    by: user
    at: "2026-06-17T16:33:29.182Z"
  c6:
    by: user
    at: "2026-06-17T21:40:00.694Z"
  c7:
    by: user
    at: "2026-06-17T21:41:07.834Z"
  c8:
    by: user
    at: "2026-06-17T21:42:28.839Z"
  c9:
    by: user
    at: "2026-06-17T22:11:13.313Z"
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
    body: >-
      Applied. Dropped the `glissando` tag. CONNECTED -> <slide type="start">
      on the first note and <slide type="stop"> on the second; SLIDE_OUT ->
      <falloff>, both with line-shape="straight" line-type="solid". Phase 1
      tags and Phases 4-6 updated.
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
    body: >-
      Fixed. *_REST keeps the same <type> token; a rest is marked by a <rest/>
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
