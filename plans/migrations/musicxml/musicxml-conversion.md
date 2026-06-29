# MusicXML Conversion Plan

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Conversion Scaffold + Round-Trip Harness](#-phase-1-conversion-scaffold--round-trip-harness) | ✅ Complete | [phase-1-scaffold.md](./phase-1-scaffold.md) |
| 2 | [Structural Model (Line ↔ Measure)](#-phase-2-structural-model-line--measure) | ✅ Complete | [phase-2-structural-model.md](./phase-2-structural-model.md) |
| 3 | [Notes & Per-Note Attachments](#-phase-3-notes--per-note-attachments) | ✅ Complete | [phase-3-notes.md](./phase-3-notes.md) |
| 4 | [Line-Level Range Spans](#-phase-4-line-level-range-spans) | ⏳ Pending | — |
| 5 | [Per-Measure Attributes (Key, Tempo)](#-phase-5-per-measure-attributes-key-tempo) | ⏳ Pending | — |
| 6 | [Lyrics](#-phase-6-lyrics) | ⏳ Pending | — |
| 7 | [Header, Layout & Extension Fields](#️-phase-7-header-layout--extension-fields) | ⏳ Pending | — |
| 8 | [Losslessness Gate & Cutover](#-phase-8-losslessness-gate--cutover) | ⏳ Pending | — |

## Context

SongScribe persists documents as `.mssw` (hand-rolled custom XML, written via
`SongIO.writeSong` + the `XML` PrintWriter helper, read via `SongIO.DocumentReader`).
Issue [#288](https://github.com/vasudeva-server/SongScribe/issues/288) calls for
converting to **MusicXML 4.0 (`score-partwise`)** for interoperability and archival,
with **MusicXML as the canonical on-disk format and zero information loss**.

The field-by-field mapping is the companion reference:
[musicxml.md](musicxml.md). This plan turns that mapping into an ordered,
incrementally-verifiable build.

**Strategy — vertical slices that each round-trip.** Each phase adds a slice of
the mapping to *both* the writer and the reader, plus round-trip assertions for
that slice. This catches losslessness violations per-feature instead of deferring
all round-trip verification to a single write-then-read split at the end. The
cost is repeated stubbing of writer/reader as layers are added; the benefit is a
working, testable converter from Phase 1 onward.

Phases are ordered by structural dependency: the line↔measure transformation
(Phase 2) is the hardest conceptual piece and everything hangs off it, so it
comes first after the scaffold.

### Dependencies

- **Attribution rework (external).** ✅ Complete (commit `692b527a`). Discrete
  composer / lyricist / `lyricsSource` / `isArrangement()` / `attributionYOffset`
  fields replaced the single attribution text blob; `SUB_ATTRIBUTION` font role
  added. Phase 7 is no longer blocked.

### Legacy fields

The legacy read-only `*IO` constants (`XML_VOLUME`, per-element `XML_TRILL`,
`XML_SYLLABLE_MOVEMENT`, etc., and the line-level Y-position fields) exist only to
read old `.mssw` files and are **not** part of the write schema. They need no
MusicXML mapping — they vanish on migration. See musicxml.md § "Legacy read-only
fields".

## Architectural Decisions

- **Format**: MusicXML 4.0, `score-partwise`, single `<part>` (one `<score-part>`
  in `<part-list>`).
- **Clef**: Treble only. Emit `<clef><sign>G</sign><line>2</line></clef>`; ignore on read.
- **Meter**: Meterless → `<time print-object="no"><senza-misura/></time>`.
- **Coordinate conversions** (bijective): staff-space ↔ tenths (×10); pixels ↔
  tenths. Reuse the project's unit-conversion utilities (see
  `.agents/guides/unit-conversion.md`); do not hardcode the factor.
- **Pitch**: `StaffElement.getPitch()` already yields a concrete pitch from staff
  position + accidental + key, so `<step>/<octave>/<alter>` is a clean equivalent.
- **Extension data**: Genuinely SongScribe-specific score-level fields go in
  `<miscellaneous><miscellaneous-field>` / `<other-*>`, stored verbatim.
- **XML library — hand-rolled, no JAXB.** Match the existing `.mssw` IO machinery:
  `PrintWriter` + the `XML` helper for write, a SAX-style reader (mirroring
  `SongIO.DocumentReader`) for read. No new dependencies, one IO paradigm.
  Rationale: SongScribe is the **sole writer** and the only MusicXML it reads is
  its **own output** (no third-party import is planned), so a JAXB binding's full
  object model (e.g. `proxymusic`) buys nothing here — SongScribe emits only a
  constrained subset (single part, treble clef, senza-misura). JAXB would also add
  2–3 runtime dependencies that must be Java 25-clean (JAXB left the JDK at 11).
  - **Write path is held to strict schema conformance** so external consumers
    (MuseScore, Finale, etc.) accept the output: every phase validates writer
    output against `docs/musicxml-4.0-schema/`.
  - **Read path only parses SongScribe's own well-formed output** — it is not a
    general-purpose MusicXML importer.

---

## ✅ Phase 1: Conversion Scaffold + Round-Trip Harness

**Status**: ✅ Complete — see [phase-1-scaffold.md](./phase-1-scaffold.md)

Created `MusicXmlWriter`, `MusicXmlReader`, `MusicXmlSchemaValidator`, and round-trip test harness in `songscribe.io.musicxml`. Writer output validates against the MusicXML 4.0 XSD; default-song round-trip is lossless. SAX parser hardened against XXE.

---

## ✅ Phase 2: Structural Model (Line ↔ Measure)

**Status**: ✅ Complete — see [phase-2-structural-model.md](./phase-2-structural-model.md)

Implemented the reversible line-centric ↔ measure-centric transformation. `BarlineStyleMapping` provides the `ElementType` ↔ `<bar-style>`/`<repeat>` lookup table; `MusicXmlWriter` splits each `Line` into `<measure>` containers at every barline element and line break; `MusicXmlReader` reassembles measures back into lines with barline `StaffElement`s re-inserted. `REPEAT_LEFT_RIGHT` decomposes into a straddling backward/forward `<barline>` pair on write and recomposes on read.

See [phase-2-structural-model.md](./phase-2-structural-model.md) for the detailed implementation plan.

---

## ✅ Phase 3: Notes & Per-Note Attachments

**Status**: ✅ Complete — see [phase-3-notes.md](./phase-3-notes.md)

Implemented full `<note>` content and per-note attachments in both directions.
`NoteTypeMapping` owns the `ElementType` ↔ `<type>` token table and the exact
`<duration>` tick math (DIVISIONS = 480); `PitchSpelling` provides the bijective
`staffPosition` ↔ `<step>`/`<octave>` conversion plus the sounding `<alter>`
(displayed glyph stays independent, recovered from `<accidental>`);
`AccidentalMapping` covers the `Accidental` ↔ `<accidental>` glyph token
(incl. cautionary/parenthesized). The writer emits rests (`<rest/>`), grace notes
(`<grace slash="no"/>`, no `<duration>`), dots, stems (manual override + grace
default), X offset (px ↔ tenths), and per-note `<notations>`: accent, staccato,
fermata, dynamics, breath-mark, glissando (`<slide>` start/stop pairing), and fall
(`<falloff>`). The reader reassembles each `<note>` back into a `StaffElement` with
its attachments.

See [phase-3-notes.md](./phase-3-notes.md) for the detailed implementation plan.

---

## ⏳ Phase 4: Line-Level Range Spans

**Goal**: The index-pair spans stored on `Line` ↔ per-note MusicXML markers. The
expand-on-write / collapse-on-read machinery. Bijective.

**Mapping** (musicxml.md § "Line-level range spans"):
- Beaming → per-note `<beam>`; ties → `<tie>` + `<tied>`; tuplets →
  `<time-modification>` + `<tuplet>` bracket; crescendo/diminuendo → `<wedge>`;
  trills → `<trill-mark>` + `<wavy-line>`; first/second endings →
  `<barline><ending>`.

**Verify**: Round-trip spans that start/stop mid-line and span multiple notes;
confirm spans re-collapse to identical index pairs.

---

## ⏳ Phase 5: Per-Measure Attributes (Key, Tempo)

**Goal**: Measure-level attributes and tempo directions.

**Mapping** (musicxml.md § "Per-measure attributes"; `io/TempoIO`):
- Key accidental count + type → `<key><fifths>` (FLATS→−n, SHARPS→+n, NONE→0).
- Song / per-note tempo → `<sound tempo>` + `<direction><metronome>`: visible BPM,
  beat unit, description (`<words>`), hide-tempo (`print-object="no"`).
- Metric modulation (`BeatChange`) → `<metronome>` with two `<metronome-note>` +
  `<metronome-relation>`.

**Verify**: Round-trip key changes, song-level and per-note tempo, hidden tempo,
and a metric modulation.

---

## ⏳ Phase 6: Lyrics

**Goal**: Per-verse lyrics.

**Mapping** (musicxml.md § "Lyrics"):
- Verse number → `<lyric number>`; syllabic → `<syllabic>`; text → `<text>`;
  compound-word marker → `<elision>`; extender → `<extend type>`.

**Verify**: Round-trip multi-verse songs with syllabic breaks, compound words,
and extenders.

---

## ⏳ Phase 7: Header, Layout & Extension Fields

**Goal**: Score head, `<defaults>`/`<print>` layout, `<credit>` elements,
annotations, and the residual `<miscellaneous>` extension fields.

**Mapping** (musicxml.md §§ "Song header", "Layout", "Credits", "Annotations", "Losslessness"):
- Header (metadata): title → `<movement-title>`, number → `<movement-number>`,
  composer/lyricist → `<creator type="composer|lyricist">`, `isArrangement()=true`
  → `<creator type="arranger">Sri Chinmoy</creator>` (omit when false), rights →
  `<rights>`, date → ISO 8601 `<misc-field name="composition-date">`, lyrics date
  (when distinct) → `<misc-field name="lyrics-date">`, place →
  `<misc-field name="composition-place">`, `lyricsSource` (LYRICIST/TEXT/OTHER) →
  `<misc-field name="lyrics-source">`, unofficial-translation flag → `<misc-field>`.
- Credits (after `<defaults>`): title (text from `getNumberedTitle()` — title
  with movement-number prefix) + subtitle (`getSubtitle()`, `<credit-type>` =
  `subtitle`, font role `SUBTITLE`, **canonical/read-write** — no
  `<movement-*>` equivalent exists, so the credit is the source of truth;
  emitted only when non-empty) + each attribution role (composer, lyricist,
  arranger when `isArrangement()=true`, composition date, lyrics date, rights,
  place) → first-page `<credit>` with `<credit-type>` + `<credit-words>` (font
  + position attributes); underlyrics/Bangla/translated lyrics/footnotes →
  last-page `<credit page="N">` (their canonical home). The `TITLE` / `SUBTITLE`
  / `ATTRIBUTION` / `BANGLA` / `FOOTNOTE` fonts ride in the `<credit-words>`
  attributes — no `font-...` misc-fields. `SUB_ATTRIBUTION` font stored as
  `<misc-field name="sub-attribution-font">` / `<misc-field name="sub-attribution-font-size">`.
- Layout: line width → `<scaling>` + `<system-layout>`; fonts → `<music-font>` /
  `<lyric-font>` / `<word-font>`; paddings/distances → system-layout fields;
  attribution user Y offset (`XML_ATTRIBUTION_Y_OFFSET`) → `relative-y` on
  attribution `<credit-words>` (ss → tenths); spacing factors → `<misc-field>`.
- Annotations → `<direction placement="above|below">` (the `Placement` enum — see
  [annotation-placement-refactor.md](./annotation-placement-refactor.md), a
  prerequisite model change) `<direction-type><words>` with halign/justify;
  `userYOffsetSs` → `relative-y`; computed base Y → `default-y` (write-forward,
  ignored on read).

**Verify**: Round-trip a fully-populated header + layout + credits + annotations;
attribution credits re-derive from head metadata, score-below credits reload
verbatim, and every residual `<misc-field>` reloads verbatim.

---

## ⏳ Phase 8: Losslessness Gate & Cutover

**Goal**: Prove zero information loss across a real corpus, then make MusicXML the
canonical on-disk format with `.mssw` as read-only legacy import.

**Tasks**:
- Round-trip every `.mssw` in the test corpus through `Song → MusicXML → Song` and
  assert full model equality (the losslessness gate).
- Wire MusicXML into the save/open path as the new canonical format; keep the
  existing `.mssw` `DocumentReader` for one-way legacy import.
- Update `FileExtensions` / file filters as needed.

**Verify**: Corpus round-trip is lossless. Opening a legacy `.mssw` and saving
produces a faithful MusicXML document.

---

## Phase Dependencies

```
Phase 1 (Scaffold + Harness)
  |
  v
Phase 2 (Line <-> Measure)
  |
  +--> Phase 3 (Notes) --> Phase 4 (Range Spans)
  |
  +--> Phase 5 (Key/Tempo)
  |
  +--> Phase 6 (Lyrics)
  |
  +--> Phase 7 (Header/Layout/Ext)
  |
  v
Phase 8 (Losslessness Gate + Cutover)   [requires 2-7 complete]
```

Phases 3–7 build on the Phase 2 structure and are largely independent of each
other (3→4 is the one hard ordering). Phase 8 gates the whole effort.

## Verification

After each phase: `./scripts/compile.sh` succeeds; the phase's writer output
validates against `docs/musicxml-4.0-schema/`; the round-trip harness passes for
the cumulative populated subset. Final gate (Phase 8): lossless round-trip across
the full `.mssw` corpus.
