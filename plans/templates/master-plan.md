# MusicXML Conversion Plan

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Conversion Scaffold + Round-Trip Harness](#-phase-1-conversion-scaffold--round-trip-harness) | ✅ Complete | [phase-1-scaffold.md](./phase-1-scaffold.md) |
| 2 | [Structural Model (Line ↔ Measure)](#-phase-2-structural-model-line--measure) | 📋 Sub-plan | [phase-2-structural-model.md](./phase-2-structural-model.md) |
| 3 | [Notes & Per-Note Attachments](#-phase-3-notes--per-note-attachments) | ⏳ Pending | — |
| 4 | [Line-Level Range Spans](#-phase-4-line-level-range-spans) | ⏳ Pending | — |
| 5 | [Per-Measure Attributes (Key, Tempo)](#-phase-5-per-measure-attributes-key-tempo) | ⏳ Pending | — |
| 6 | [Lyrics](#-phase-6-lyrics) | ⏳ Pending | — |
| 7 | [Header, Layout & Extension Fields](#️-phase-7-header-layout--extension-fields) | ⏸️ Blocked | — |
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

- **Attribution rework (external).** The header mapping in musicxml.md assumes the
  *post-rework* attribution shape — discrete composer / lyricist / date / place
  fields replacing today's single attribution text blob (`XML_INFO`,
  `Song.getAttribution()`). That rework is **not yet done**. Phase 7 (header) is
  **blocked** on it: `<creator type="composer">` / `type="lyricist">` cannot be
  emitted natively until composer/lyricist are discrete fields. All other phases
  are independent of it.

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

## 📋 Phase 2: Structural Model (Line ↔ Measure)

**Status**: 📋 Sub-plan — see [phase-2-structural-model.md](./phase-2-structural-model.md)

**Goal**: The reversible line-centric ↔ measure-centric transformation — the
conceptual core. No note content yet (or minimal placeholder notes).

**Mapping** (musicxml.md § "Structural model"):
- Insert a `<measure>` boundary at (a) every SongScribe barline element and
  (b) every line break.
- Real barline element → `<barline>` with matching `<bar-style>` (+ `<repeat>`).
- Line break with no barline → invisible `<barline><bar-style>none</bar-style>`.
- Line (system) break → `<print new-system="yes"/>` on the measure starting the line.
- Read-back rule: invisible barline coinciding with a system break = line break;
  visible one = real barline element.

**Touches**: `MusicXmlWriter`/`MusicXmlReader`; reads `Song` lines + inline
barline `ElementType`s (`io/LineIO`, `io/StaffElementIO` for reference).

**Verify**: Round-trip a multi-line song with assorted barlines/repeats: line
count, line breaks, and barline positions/styles all preserved.

---

## ⏳ Phase 3: Notes & Per-Note Attachments

**Goal**: Core `<note>` content and everything attached to a single note.

**Mapping** (musicxml.md § "Note / element → `<note>`"):
- Durations → `<type>` + `<duration>`; rests → `<rest/>`; grace → `<grace/>`.
- Pitch via `getPitch()` → `<step>/<octave>/<alter>`.
- Dots, accidentals (incl. cautionary/parenthesized), stems (up/down + manual
  override flag → `<other-*>`), X offset (px → tenths).
- Per-note articulations/notations: accent, staccato, fermata, dynamics,
  breath mark, glissando/slide (+ translate offsets).
- Inline barlines/repeats already handled in Phase 2.

**Verify**: Round-trip notes across all durations, accidentals, articulations,
stem states; pitch and offsets bit-stable.

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

## ⏸️ Phase 7: Header, Layout & Extension Fields

**Status**: **Blocked** by the external attribution rework (see § Dependencies).
The layout and Ext-field portions are *not* blocked and could be split out if the
rework lags.

**Goal**: Score head, `<defaults>`/`<print>` layout, annotations, and all
`<miscellaneous>` extension fields.

**Mapping** (musicxml.md §§ "Song header", "Layout", "Annotations", "Losslessness"):
- Header: title → `<movement-title>`, number → `<movement-number>`,
  composer/lyricist → `<creator>` **(needs reworked attribution)**, date → ISO 8601
  `<misc-field>` (+ optional `<credit-words>`), place/underlyrics/Bangla/translated
  lyrics/footnotes → `<misc-field>`.
- Layout: line width → `<scaling>` + `<system-layout>`; fonts → `<music-font>` /
  `<lyric-font>` / `<word-font>` (extra roles → `<misc-field>`); paddings/distances
  → system-layout fields; spacing factors → `<misc-field>`.
- Annotations → `<direction><words>` with halign/justify, default-y, relative-y.

**Verify**: Round-trip a fully-populated header + layout + annotations; every
`<misc-field>` reloads verbatim.

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
  +--> Phase 7 (Header/Layout/Ext)   [BLOCKED: attribution rework]
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
