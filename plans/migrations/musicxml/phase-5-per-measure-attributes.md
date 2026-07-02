# Sub-plan: Phase 5 — Per-Measure Attributes (Key, Tempo)
**Type:** Sub-plan  
**Parent:** [musicxml-conversion.md](./musicxml-conversion.md) → Phase 5  
**Created:** 2026-06-30  
**Status:** Complete  
**BlockedBy:** —

* * *
## Purpose
Add measure-level **key signatures** and **tempo/metronome directions** to both the MusicXML writer and reader, with per-slice round-trip tests. Delivers:

- Per-line **key changes** → `<attributes><key><fifths>` at line-start measures.
  
- **Song / per-note tempo** → `<direction>` with `<metronome>` (beat-unit, per-minute, description `<words>`, hide via `print-object`) + write-forward `<sound tempo>`.
  
- **Metric modulation** (`BeatChange`) → `<metronome>` two-note-relation form.
  

**Explicitly out of scope** (deferred): lyrics (Phase 6); annotations / `<direction><words>` without a metronome (Phase 7); the full-corpus losslessness gate (Phase 8).
## Implementation Approach
### Decomposition rationale
- **Phase 1** is mechanical (a lookup table + rebase) → Sonnet/Haiku.
  
- **Phases 2–4** are reversible-boundary work in a hand-rolled SAX reader/writer, each with two dual-representation subtleties (song-default vs per-line key; song-base vs per-note tempo) → Opus.
  
- Tests are **not** a separate phase: each Opus phase ships its own round-trip test, matching the master plan's vertical-slice strategy and the Phase 2–4 precedent (one `MusicXml*RoundTripTest` per feature).
  
- Phase 4 extends the `MetronomeResolver` and `BeatUnitMapping` created in Phase 3, so it is sequenced after Phase 3 to avoid editing the same new files in parallel.
  
### Reader is now split (rebase required first)
`develop` HEAD `682ca08c` ("refactor: split MusicXmlReader into multiple classes") is **not** in `musicxml-phase-5` (branch merge-base is `db3ca4f4`). The reader is now a thin SAX dispatcher (`MusicXmlReader`, a `Where` state enum) delegating to focused helpers: `NoteAccumulator`, `WedgeResolver`, `RangeSpanResolver`, `BarlineParser`, `EndingResolver`. **Rebase onto** `develop` **before any code work** (Phase 1, task 1) so the read-side targets the split structure. Phase 5 adds a new sibling resolver, `MetronomeResolver`, alongside `WedgeResolver`.
### Key code touchpoints
Model (`src/main/java/songscribe/dom/`):

- `Tempo.java` — `getVisibleTempo()`, `getTempoType()` (a `Duration`), `getTempoDescription()`, `shouldShowTempo()`, `getRealTempo()` (playback BPM).
  
- `Duration.java` — enum: `SEMI_BREVE, MINIM_DOTTED, MINIM, CROTCHET_DOTTED, CROTCHET, QUAVER_DOTTED, QUAVER`. Used by both `Tempo.tempoType` and `BeatChange`.
  
- `BeatChange.java` — `record BeatChange(Duration duration, Duration beat)` (left side `equals` right side).
  
- `TempoChangeAttachment` / `BeatChangeAttachment` (extends `MetronomeAttachment`) — per-`StaffElement` attachments; find via `element.findAttachment(...)`.
  
- `Song.java` — `getTempo()`/`setTempo()` (nullable base tempo); `getDefaultKeyType()`/`getDefaultKeyAccidentalCount()` + `setDefaultKey*`; `Line.attachInitialTempoIfNeeded()` (Line.java:813) mirrors the base tempo onto the first element; `clearTempoIfOrphaned` is its inverse.
  
- `Line.java` — `getKeyType()` (nullable), `getKeyAccidentalCount()`, `setKeyType`/`setKeyAccidentalCount`. Every line is materialized from the song default on load (Song.java:354–355); `LineIO` (LineIO.java:76–89) writes a line's key only when it differs from the default.
  
- `KeyType.java` — `NONE, FLATS, SHARPS`.
  

Writer (`src/main/java/songscribe/io/musicxml/MusicXmlWriter.java`, **not** split):

- `writeAttributes()` (line 1325) — currently emits `<divisions>480`, song-default `<key><fifths>`, senza-misura `<time>`, treble `<clef>`. Extend for per-line key.
  
- `writeWedgeDirection()` (line 874) — the `<direction><direction-type>` envelope template to mirror for tempo/metric-modulation directions.
  
- `writeNote()` (line 378) and the measure element loop — emit each element's tempo / beat-change `<direction>` immediately **before** its `<note>`.
  
- `openMeasure()` (1308) / `closeMeasure()` (1316); `XML` helper (`writeBeginTag`, `writeEmptyTag`, `writeValue`, `writeEndTag`, `indent`/`dedent`).
  

Reader (post-rebase, `MusicXmlReader.java` + resolvers):

- `Where` state enum + `startElement`/`endElement` dispatch. `ATTRIBUTES` → `KEY` → `FIFTHS` states exist; `FIFTHS` `endElement` already sets the **song default** (`keyTypeFromFifths`). `DIRECTION` / `DIRECTION_TYPE` states exist (drive `WedgeResolver`).
  
- Tags in `MusicXmlTags.java`: `DIRECTION`, `DIRECTION_TYPE`, `ATTRIBUTES`, `KEY`, `FIFTHS`, `ATTR_TYPE`. Add tempo/metronome tag constants here.
  

Tests (`src/test/java/songscribe/io/musicxml/`):

- Base `MusicXmlRoundTripSupport` — `writeToString(song)`, `parse(xml)`, `roundTrip(song)`, `buildSong(LineBuilder...)` (mutation tracking disabled).
  
- Mirror an existing per-feature test (e.g. `MusicXmlHairpinRoundTripTest`).
  
### Design decision — song base tempo (Phase 3)
Two representations must both survive: `Song.tempo` (base, nullable) and per-note `TempoChangeAttachment`. The base tempo is **anchored on the first element of the first line** (`attachInitialTempoIfNeeded`; `clearTempoIfOrphaned` is its inverse), so `firstElement.hasTempo ⟺ song.tempo != null` and they are equal.

- **Writer:** emit a tempo `<direction>` for every element carrying a `TempoChangeAttachment`, positioned before that element's `<note>`. Treat the first element specially: its tempo = its attachment if present, else `song.getTempo()` (mirrors `attachInitialTempoIfNeeded` at write time so a base tempo is always emitted even for a not-yet-materialized song).
  
- **Reader:** attach a `TempoChangeAttachment` for each tempo direction to its note; after assembly, if the first element of the first line carries one, call `song.setTempo(thatTempo)`. No separate song-level element or disambiguation marker is needed — the anchor invariant makes it unambiguous.
  
- The round-trip test must construct songs in **post-load (canonical) form** (base tempo mirrored onto the first element) and cover: base tempo only; base tempo + a distinct mid-song per-note tempo.
  
### Design decision — song-default vs per-line key (Phase 2)
- **Writer:** measure 1's `<attributes><key><fifths>` = song default (unchanged). For every later line whose effective key differs from the running key, emit a key-only `<attributes><key><fifths>` at that line's first measure.
  
- **Reader:** measure-1 `<key>` sets the song default (as today) and the running fifths; a `<key>` at any later measure updates the running fifths and is applied to the current line's `setKeyType`/`setKeyAccidentalCount`.
  
- This assumes **line 1's key equals the song default** — the model materializes every line from the default on load, so they coincide after any load. This holds for all corpus songs; it is verified by the Phase 8 gate, not worked around here.
  
### `fifths` conversion (no new mapping class — key is trivial)
`FLATS → −count`, `SHARPS → +count`, `NONE → 0`; reverse: `fifths < 0 → FLATS`, `> 0 → SHARPS`, `0 → NONE` (`abs` gives the count). Reuse the reader's existing `keyTypeFromFifths`; add the forward direction inline in the writer.
### Target output samples
Per-line key change (line starting at measure 5, two sharps):

```xml
<measure number="5">
  <attributes>
    <key><fifths>2</fifths></key>
  </attributes>
  ...
```

Tempo direction (visible, quarter = 120, description "Moderately"):

```xml
<direction>
  <direction-type>
    <metronome>
      <beat-unit>quarter</beat-unit>
      <per-minute>120</per-minute>
    </metronome>
  </direction-type>
  <direction-type>
    <words>Moderately</words>
  </direction-type>
  <sound tempo="120"/>
</direction>
```

Hidden tempo → `<metronome print-object="no">`. Dotted beat unit → `<beat-unit>quarter</beat-unit><beat-unit-dot/>`.

Metric modulation `BeatChange(CROTCHET_DOTTED, MINIM)` (dotted-quarter = half):

```xml
<direction>
  <direction-type>
    <metronome>
      <metronome-note>
        <metronome-type>quarter</metronome-type>
        <metronome-dot/>
      </metronome-note>
      <metronome-relation>equals</metronome-relation>
      <metronome-note>
        <metronome-type>half</metronome-type>
      </metronome-note>
    </metronome>
  </direction-type>
</direction>
```

`BeatUnitMapping` table (Phase 1) — `Duration` ↔ (type token, dot count):

| Duration | token | dots |
|---|---|---|
| `SEMI_BREVE` | `whole` | 0 |
| `MINIM` | `half` | 0 |
| `MINIM_DOTTED` | `half` | 1 |
| `CROTCHET` | `quarter` | 0 |
| `CROTCHET_DOTTED` | `quarter` | 1 |
| `QUAVER` | `eighth` | 0 |
| `QUAVER_DOTTED` | `eighth` | 1 |

The same token serves `<beat-unit>` (tempo) and `<metronome-type>` (modulation); the dot serves `<beat-unit-dot/>` and `<metronome-dot/>`.
## Dependencies
- **Rebase** `musicxml-phase-5` **onto** `develop` **@**`682ca08c` (Phase 1, task 1) — the reader split is a hard prerequisite for the read-side of Phases 2–4.
  
- Phase 3 and 4 depend on `BeatUnitMapping` (Phase 1).
  
- Phase 4 depends on the `MetronomeResolver` + tempo direction envelope from Phase 3.
  
- **Must not regress:** the Phase 1–4 round-trip suites; writer output must keep validating against `docs/musicxml-4.0-schema/` (`<direction-type>` order, `<metronome>` grammar, `print-object` placement).
  
## Plan
| Phase | Description | Status | Recommended model |
| --- | --- | --- | --- |
| 1   | [Rebase and Beat-Unit Mapping](#-phase-1-rebase-and-beat-unit-mapping) | ✅ Complete | Sonnet 4.6 / Haiku 4.5, low |
| 2   | [Key Signature Round-Trip](#-phase-2-key-signature-round-trip) | ✅ Complete | Opus 4.8, medium |
| 3   | [Tempo Directions Round-Trip](#-phase-3-tempo-directions-round-trip) | ✅ Complete | Opus 4.8, high |
| 4   | [Metric Modulation Round-Trip](#-phase-4-metric-modulation-round-trip) | ✅ Complete | Opus 4.8, medium |
## ✅ Phase 1: Rebase and Beat-Unit Mapping
**Status:** Complete  
**BlockedBy:** —  
**Recommended model/effort:** Sonnet 4.6 / Haiku 4.5, low — repo rebase plus a pure static lookup table with an obvious inverse; a unit test gates correctness.
### Tasks
1. Rebase `musicxml-phase-5` onto `develop` (`682ca08c`); resolve any conflicts (expect none in `musicxml/`), then run `./scripts/compile.sh` — SUCCESS — and `./scripts/test.sh unit` to confirm the Phase 1–4 suites stay green.
  
2. Add `BeatUnitMapping` in `songscribe.io.musicxml`: forward `Duration → BeatUnit` (type token + dot count) and reverse `(token, dotCount) → Duration`, per the table in § "Target output samples". Follow the static forward/reverse-map pattern of `BarlineStyleMapping` / `AccidentalMapping`; null-safe lookups.
  
3. Add a `BeatUnitMappingTest` unit test asserting round-trip `Duration → token/dot → Duration` for all seven values.
  
4. Gate: `./scripts/compile.sh` (SUCCESS) then `./scripts/test.sh BeatUnitMappingTest` (green).
  
## ✅ Phase 2: Key Signature Round-Trip
**Status:** Complete  
**BlockedBy:** 1  
**Recommended model/effort:** Opus 4.8, medium — reversible per-line boundary over the song-default baseline; small surface but two representations to keep in sync.
### Tasks
1. **Writer:** in `MusicXmlWriter`, add per-line key emission. Keep measure 1's song-default `<key><fifths>` in `writeAttributes()`. For each later line whose effective key (`getKeyType`/`getKeyAccidentalCount`, falling back to the song default) differs from the running key, emit a key-only `<attributes><key><fifths>` at that line's first measure. `FLATS → −n`, `SHARPS → +n`, `NONE → 0`.
  
2. **Reader:** in `MusicXmlReader`, extend `FIFTHS` handling so measure-1 fifths still sets the song default and seeds the running fifths, and a `<key>` at any later measure updates the running fifths and applies it to the current line (`setKeyType` + `setKeyAccidentalCount`). Reuse `keyTypeFromFifths`. Keep key parsing inline (no new resolver class — it is trivial).
  
3. Add `MusicXmlKeyRoundTripTest` (extends `MusicXmlRoundTripSupport`): build a multi-line song with a mid-song key change (e.g. `NONE` → 3 `SHARPS` → 2 `FLATS` across lines), round-trip, and assert each line's `getKeyType` / `getKeyAccidentalCount` and the song default survive.
  
4. Gate: `./scripts/compile.sh` (SUCCESS); confirm writer output validates against `docs/musicxml-4.0-schema/`; `./scripts/test.sh MusicXmlKeyRoundTripTest` (green).
  
## ✅ Phase 3: Tempo Directions Round-Trip
**Status:** Complete  
**BlockedBy:** 1  
**Recommended model/effort:** Opus 4.8, high — the hardest slice: the `<direction>`/`<metronome>` envelope in both directions, the song-base-vs-per-note anchor invariant, hide handling, and a new SAX resolver.
### Tasks
1. Add tempo/metronome tag constants to `MusicXmlTags` (`METRONOME`, `BEAT_UNIT`, `BEAT_UNIT_DOT`, `PER_MINUTE`, `WORDS`, `SOUND`, `ATTR_TEMPO`, `ATTR_PRINT_OBJECT`, etc.).
  
2. **Writer** `writeTempoDirection(Tempo)`: emit `<direction>` → `<direction-type><metronome>` with `BeatUnitMapping` beat-unit (+ `<beat-unit-dot/>`) and `<per-minute>` = `getVisibleTempo()`; `print-object="no"` on `<metronome>` when `!shouldShowTempo()`; a second `<direction-type><words>` when `getTempoDescription()` is non-empty; write-forward `<sound tempo="getRealTempo()"/>`. Emit it before each element's `<note>`, treating the first element's tempo as its attachment else `song.getTempo()` (see § "Design decision — song base tempo").
  
3. Add `MetronomeResolver` (sibling of `WedgeResolver`): accumulate a metronome direction (beat-unit/dot, per-minute, words, print-object) and, at `</direction>`, build a `Tempo` and hold it pending for the next element.
  
4. **Reader:** add the `<metronome>`/`<beat-unit>`/`<per-minute>`/`<words>`/`<sound>` states under `DIRECTION`/`DIRECTION_TYPE`, driving `MetronomeResolver`. Attach a `TempoChangeAttachment` to the next element; recover `setShowTempo(false)` from `print-object="no"` and `setTempoDescription` from `<words>`; ignore `<sound tempo>`. After assembly, set `song.setTempo(...)` from the first element's attachment. (Only metronome-bearing directions are handled; bare `<words>` directions are Phase 7 and not yet emitted.)
  
5. Add `MusicXmlTempoRoundTripTest`: cover base tempo only; base tempo + a distinct mid-song per-note tempo; a hidden tempo; a dotted beat-unit; a description. Construct songs in post-load (mirrored) form. Assert `visibleTempo`, `tempoType`, `tempoDescription`, `shouldShowTempo`, `song.getTempo()`, and per-note placement.
  
6. Gate: `./scripts/compile.sh` (SUCCESS); confirm schema validation; `./scripts/test.sh MusicXmlTempoRoundTripTest` (green).
  
## ✅ Phase 4: Metric Modulation Round-Trip
**Status:** Complete  
**BlockedBy:** 3  
**Recommended model/effort:** Opus 4.8, medium — reuses the Phase 3 envelope and `BeatUnitMapping`; the two-`<metronome-note>` relation form is well-defined.
### Tasks
1. **Writer** `writeMetricModulationDirection(BeatChange)`: emit `<direction>` → `<direction-type><metronome>` with `<metronome-note><metronome-type>…</metronome-type> [<metronome-dot/>]</metronome-note>` for `duration()`, then `<metronome-relation>equals</metronome-relation>`, then a second `<metronome-note>` for `beat()` (tokens/dots via `BeatUnitMapping`). Emit before the note carrying the `BeatChangeAttachment`.
  
2. **Reader:** extend `MetronomeResolver` to recognize the `<metronome-note>` + `<metronome-relation>` form (vs the beat-unit/per-minute tempo form) and build a `BeatChange(duration, beat)`; attach a `BeatChangeAttachment` to that element. Add the `<metronome-note>`/`<metronome-type>`/`<metronome-dot>`/`<metronome-relation>` states.
  
3. Add `MusicXmlMetricModulationRoundTripTest`: round-trip each `BeatChange` variant from `BeatChange.fromLegacyName` (e.g. `CROTCHET_DOTTED`↔`MINIM`, `QUAVER`↔`QUAVER`) and assert the attachment's `duration()`/`beat()` survive.
  
4. Gate: `./scripts/compile.sh` (SUCCESS); confirm schema validation; `./scripts/test.sh MusicXmlMetricModulationRoundTripTest` (green).
  
## Verification (whole sub-plan)
- `./scripts/compile.sh` → SUCCESS.
  
- Writer output for key changes, visible/hidden/described tempo, and metric modulation validates against `docs/musicxml-4.0-schema/`.
  
- `./scripts/test.sh unit` green, including all four new/extended round-trip tests and no regression in the Phase 1–4 suites.
  
- Round-trip is lossless for: mid-song key changes; song-base and per-note tempo (incl. hidden and described); dotted beat units; every `BeatChange` variant.
