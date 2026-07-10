# Sub-plan: Phase 4 — Line-Level Range Spans
**Type:** Sub-plan  
**Parent:** [musicxml-conversion.md](./musicxml-conversion.md) → Phase 4  
**Created:** 2026-06-29  
**Status:** Complete  
**BlockedBy:** —

* * *
## Purpose
Add the six **line-level range spans** to both the MusicXML writer and reader, with per-span round-trip verification. SongScribe stores each span as an index-pair `RangeElement` on the `Line`; MusicXML distributes the same information per note (or per barline). This phase implements the **expand-on-write / collapse-on-read** machinery so a `Song` whose lines carry these spans survives `Song → MusicXML → Song` with the spans re-collapsing to identical index pairs.

**In scope** (musicxml.md § "Line-level range spans"):

- **Beaming** (`Beam`) → per-note `<beam>` values per beam level: the primary
  beam is `begin`/`continue`/`end`; secondary levels (16th, 32nd) add
  `forward hook` / `backward hook` for partial beams, all derived from note
  durations (see Phase 2 Task 2). Beam-value reference:
  https://www.w3.org/2021/06/musicxml40/musicxml-reference/data-types/beam-value/
  
- **Ties** (`Tie`) → `<tie>` (sound) + `<tied>` (notation) start/stop.
  
- **Tuplets** (`Tuplet`) → per-note `<time-modification>` + `<tuplet>` bracket start/stop, carrying the displayed `grade` and the user `verticalPositionSs`.
  
- **Crescendo / Diminuendo** (`Crescendo` / `Diminuendo`, both `Hairpin`) → `<direction><direction-type><wedge type="crescendo|diminuendo">` … `stop`, carrying the user `x1ShiftSs` / `x2ShiftSs` / `yShiftSs`.
  
- **Trills** (`Trill`) → `<notations><ornaments><trill-mark/>` + `<wavy-line>` start/stop, carrying the user `yPositionSs`.
  
- **First/second endings** (`Ending`) → `<barline><ending number type>`.
  

**Geometry fields are in scope.** Each span (except `Beam`, `Tie`, `Ending`) persists user-adjustable offsets via its `toIndexString()` override (`Tuplet.verticalPositionSs`, `Hairpin.x1ShiftSs`/`x2ShiftSs`/`yShiftSs`, `Trill.yPositionSs`). They must round-trip for losslessness and map to native `relative-x`/`relative-y` (ss → tenths) — the same offset treatment Phase 3 used for the note X offset. `Ending.yPositionSs` is **not** persisted (its `toIndexString()` is the un-overridden base `anchor,end;`), so endings carry only their index pair — no geometry to round-trip.

**Explicitly out of scope** (deferred — do not implement here):

- Per-measure key changes, tempo, metric modulation → **Phase 5**.
  
- Lyrics → **Phase 6**. Header / layout / credits / annotations → **Phase 7**.
  
- The standalone `ElementType.GLISSANDO` line notation (musicxml.md § Note, "standalone glissando element") is **not** a range span and is untouched here; per-note glissando via `StaffElement.slide` was already handled in Phase 3.
  
## Implementation Approach
Every span is an index-pair `RangeElement` (`dom/RangeElement.java`): an `anchorElement` + `endElement`, with `getAnchorElementIndex()` / `getEndElementIndex()` resolving live line indices. The writer already iterates each `Line`'s elements with their index `i` while segmenting measures (`writeLineDrivenMeasures`, `MusicXmlWriter.java:107-214`) and emits per-note content through `writeNote` (`:240`). Phase 4 hangs span markers off that same index walk:

- **Per-note spans** (beam, tie, tuplet, trill) are emitted **inside** `writeNote` in strict `<note>`/`<notations>` schema order, driven by a per-index span lookup precomputed once per line.
  
- **Measure-level / structural spans** (hairpin wedges, endings) are emitted in the `writeLineDrivenMeasures` element loop **outside** `writeNote` — wedges as `<direction>` siblings of notes, endings folded onto the `<barline>` elements Phase 2 already emits.
  

The reader mirrors this split: per-note markers parse inside `<note>` and collapse into `RangeElement`s; wedges and endings parse at measure level.
### Decomposition rationale
The hard, Opus-worthy work is **reversibility**, all on the read side: a maximal run of per-note markers must re-collapse to the _exact_ original anchor/end index pair, and one SongScribe `Ending` decomposes into **two** MusicXML voltas that must recombine into a single span. Writer emission is mechanical once a per-index span lookup exists, so the two writer phases are Sonnet-weighted (the endings writer is the exception — its two-volta expansion onto barlines is structural, so it pairs with the harder write work under Opus). The two reader phases (run collapse; wedge + ending collapse) are Opus. Tests are split into a per-note-span phase and a hairpin/ending/edge-case phase, both Sonnet.
### The expand/collapse pattern (shared by beam, tie, tuplet, trill)
All four per-note spans are **runs over consecutive note elements** in `[anchor, end]`. Write distributes a begin/continue/end (or start/stop) marker per note; read re-collapses a maximal marked run into one `RangeElement(anchorNote, endNote)`.

```
WRITE  span [n0..n3] over notes n0 n1 n2 n3:
  n0 → begin/start    n1,n2 → continue          n3 → end/stop
       (interior ties additionally emit stop+start to chain the pair)

READ   marker stream:  start … (continue)* … stop
  on "start": remember the start note (pending field, à la pendingSlideStart)
  on "stop":  build RangeElement(startNote, thisNote); clear pending
  single-note span (anchor == end): start+stop on the same note
```

`RangeElement.getElementCount()`/`getAnchorElementIndex()` resolve indices from the live line, so the reader only needs the anchor and end `StaffElement` references — it appends notes via the existing `appendToCurrentLine` (`MusicXmlReader.java:851`) pattern and pairs markers with per-span pending fields mirroring `pendingRepeatRight` (`:105`) and `pendingSlideStart` (`:184`).
### Endings: one span ↔ two voltas
A SongScribe `Ending` (`layout/Ending.java`) is a **single** range spanning the _whole_ volta structure. Its first/second split is **not stored** — it is recomputed live by scanning `[anchor+1, end)` for the `REPEAT_RIGHT` / `REPEAT_LEFT_RIGHT` that separates the two brackets (`Ending.findRepeatSplitIndex`, `:602`). All three structural points are barline-type elements that Phase 2 already emits as `<barline>`:

- **anchor** — `SINGLE_BARLINE` or `REPEAT_LEFT` (per `checkReplacement` cond. 1) → its `<barline>` gets `<ending number="1" type="start">`.
  
- **split** — `REPEAT_RIGHT` / `REPEAT_LEFT_RIGHT` → `<ending number="1" type="stop">` **and** `<ending number="2" type="start">`.
  
- **end** — terminal barline / repeat → `<ending number="2" type="stop">` (use `discontinue` only if a future open ending needs it; SongScribe endings always close, so `stop`).
  

On read: collect ending markers per barline, pair `number="1" start` → anchor and `number="2" stop` → end, build one `Ending(anchorBarline, endBarline)`; the split is recomputed live, never stored. A degenerate ending with no internal split (one bracket) round-trips as a single `number="1"` start→stop pair.
### Tuplet `<time-modification>` ratio (write-forward)
SongScribe stores only the displayed `grade` (e.g. 3 for a triplet); playback derives timing from summed note durations, not a clean `actual:normal` ratio (`LineTrackBuilder.getTupletFactor`). MusicXML requires both `<actual-notes>` and `<normal-notes>`. Resolution: `<actual-notes>` = `grade`; `<normal-notes>` = the **largest power of two < grade** (3→2, 5→4, 6→4, 7→4) — the MusicXML convention. `<normal-notes>` is **write-forward only**: the reader recovers `grade` from `<actual-notes>` and ignores `<normal-notes>`, so it never affects round-trip. `<time-modification>` is emitted on **every** note in the tuplet (schema requires it for playback); `<tuplet>` bracket start/stop is emitted only on the anchor/end notes.
### Key code touchpoints
- **Writer**: `writeLineDrivenMeasures` (`MusicXmlWriter.java:107-214`, the per-element index loop) for wedges + endings; `writeNote` (`:240`) and its `<notations>` block (schema order in the comment at `:216-238`) for beam / tie / tuplet / trill. The existing `<notations>` body order is slide → fermata → articulations → dynamics; Phase 4 inserts `tied` / `tuplet` / `ornaments` in correct schema position (`tied, slur*, tuplet*, ornaments*, technical*, articulations*, dynamics*, fermata*`). `<beam>` and `<tie>` and `<time-modification>` are note-level (outside `<notations>`).
  
- **Reader**: the `Where` enum (`MusicXmlReader.java:913-949`), SAX `startElement`/`endElement`, `appendToCurrentLine` (`:851`), `startNote` (`:753`) / `finishNote` (`:788`), and the pending-pair idiom (`pendingRepeatRight:671`, `pendingSlideStart`/`resolveSlide` `:813`). Endings parse at the `<barline>` level alongside the Phase 2 barline handling.
  
- **Model — spans**: `dom/Beam`, `dom/Tie` (pure index pairs, no extra fields); `dom/Tuplet` (`grade`, `verticalPositionSs`; ctor `Tuplet(anchor, end, grade)`); `dom/Hairpin` sealed → `dom/Crescendo` / `dom/Diminuendo` (`x1ShiftSs`, `x2ShiftSs`, `yShiftSs`); `dom/Trill` (`yPositionSs`; ctor `Trill(anchor, end)` and single-note `Trill(anchor)`); `layout/Ending` (`anchor`, `end`; split via `findRepeatSplitElement`/`getSplitIndex`; `yPositionSs` **not** persisted).
  
- **Model — line accessors** (`dom/Line.java`): `findRangeElements(Beam.class)`, `findTies()`, `findRangeElements(Tuplet.class)`, `findRangeElements(Trill.class)`, `getCrescendos()`, `getDiminuendos()`, and `LineEndingSupport.findEndings(line)` for write enumeration; `addBeaming`/`addTie`/`addTuplet`/`addCrescendo`/ `addDiminuendo`/`addRangeElement` for read reconstruction. Index helpers: `getElementIndex`, `getElement`, `elementCount`.
  
- **Beam derivation — exact, model-only (no `LayoutResult`)**: hook direction
  is a **pure function of note durations + position in the beam group**, not a
  layout/pixel decision. Its real source of truth is
  `layout/LayoutEngine.java:537-562` (the `stubRight` computation, using
  `beamCount` at `:790`), **not** `BeamGroupRenderer.doDrawBeams` — the renderer
  merely *reads* the cached `stubRight()` back through `LayoutResult`, which the
  static writer cannot access. **Decision (1A):** extract the stub-direction rule
  + `beamCount` into a public pure helper in `layout/` (e.g. `BeamMath`), called
  by **both** `LayoutEngine` and the writer — one source of truth, no
  duplication. Beam *level* / secondary-run membership is also model-only
  (`getBeamLevel` = max depth from the shortest note; `isNoteTypeInLevel` = is a
  note short enough for level L, in `BeamGroupRenderer`); fold these into the
  same helper or mirror them. The writer derives all per-note, per-`number`
  `<beam>` values — primary run, secondary runs, and `forward hook`/`backward
  hook` — **exactly**, with no layout state.
- **Reference serialization** to mirror for data shape (NOT reuse): `io/LineIO.java` — `XML_BEAMINGS`/`XML_TIES`/`XML_TUPLETS`/`XML_CRESCENDO`/ `XML_DIMINUENDO`/`XML_TRILLS`/`XML_FSENDINGS` write (`:69-165`, `rangeElementsToString`) and the `LineReader` pending-pair read + `createXFromPending` reconstruction (`:194-678`). It shows each span's exact field layout and the pending-then-build idiom.
  
- **Mapping-class idiom** to mirror: `BarlineStyleMapping`, `NoteTypeMapping`, `AccidentalMapping` in `io/musicxml/`. New tiny helpers this phase may add: a `Hairpin` → wedge-type token and (optionally) an ending-type token — keep them as constants/small maps; no large table is warranted.
  
- **Unit conversion** (ss ↔ tenths, px ↔ ss): `MusicXmlTags.TENTHS_PER_STAFF_SPACE` (= 10); `ScaleContext.pxToSs` / `ScaleContext.ssToRoundedPx` (both static). Tenths = ss × 10; do not hardcode either factor. Hairpin/trill/tuplet offsets are already in ss, so they need only ×10.
  
- **Schema** for write-side validation: `docs/musicxml-4.0-schema/`. Honor the strict child order of `<note>`, `<notations>`, and `<barline>` (`<ending>` precedes `<repeat>` inside `<barline>`).
  
## Dependencies
- **Phase 3 (Notes & Per-Note Attachments)** — ✅ complete. Phase 4 plugs span markers into the `writeNote`/`<notations>` infrastructure and the measure-segmentation loop Phase 3/Phase 2 built.
  
- **Must not regress:** the Phase 2 structural round-trip (`MusicXmlBarlineRoundTripTest` — multi-line, barlines, `REPEAT_LEFT_RIGHT`) and the Phase 3 note-level round-trip (`MusicXmlNoteRoundTripTest` / `MusicXmlWriterOutputTest`, incl. the `*WriterOutputIsSchemaValid` schema checks) must stay green. The endings writer touches the barline-emission path Phase 2 owns — it must add `<ending>` children without altering barline segmentation.
  
## Plan
| Phase | Description | Status | Recommended model |
| --- | --- | --- | --- |
| 1   | [Tags and Mapping Helpers](#-phase-1-tags-and-mapping-helpers) | ✅ Complete | Sonnet 4.6 / Haiku 4.5, low |
| 2a  | [Writer Infra: NoteWriteContext + Per-Index Precompute](#-phase-2a-writer-infra-notewritecontext--per-index-precompute) | ✅ Complete | Sonnet 4.6, medium |
| 2b  | [Writer: Beam](#-phase-2b-writer-beam) | ✅ Complete | Sonnet 4.6, medium |
| 2c  | [Writer: Tie, Tuplet, Trill](#-phase-2c-writer-tie-tuplet-trill) | ✅ Complete | Sonnet 4.6, medium |
| 3   | [Writer: Hairpins and Endings](#-phase-3-writer-hairpins-and-endings) | ✅ Complete | Opus 4.8, high |
| 4   | [Reader: Per-Note Run Collapse](#-phase-4-reader-per-note-run-collapse) | ✅ Complete | Opus 4.8, high |
| 5   | [Reader: Hairpins and Endings Collapse](#-phase-5-reader-hairpins-and-endings-collapse) | ✅ Complete | Opus 4.8, high |
| 6a  | [Round-Trip Tests: Beam](#-phase-6a-round-trip-tests-beam) | ✅ Complete | Sonnet 4.6, medium |
| 6b  | [Round-Trip Tests: Tie, Tuplet, Trill](#-phase-6b-round-trip-tests-tie-tuplet-trill) | ✅ Complete | Sonnet 4.6, medium |
| 7a  | [Round-Trip Tests: Hairpins](#-phase-7a-round-trip-tests-hairpins) | ✅ Complete | Sonnet 4.6, medium |
| 7b  | [Round-Trip Tests: Endings](#-phase-7b-round-trip-tests-endings) | ✅ Complete | Sonnet 4.6, medium |
| 7c  | [Round-Trip Tests: Edge Cases & Schema Gate](#-phase-7c-round-trip-tests-edge-cases--schema-gate) | ✅ Complete | Sonnet 4.6, medium |

> **Test-file layout (post-split).** The former monolithic `MusicXmlRoundTripTest` was split into one abstract base + eight focused classes, all in `src/test/java/songscribe/io/musicxml/`. New tests in the remaining phases go into the matching class:
> - `MusicXmlRoundTripSupport` — abstract base: `writeToString` / `parse` / `roundTrip` / `buildSong` / `LineBuilder` plus the four `assertRangeElementEquals` overloads and the shared `X_OFFSET_PX` / `C4_STAFF_POSITION` constants.
> - `MusicXmlBarlineRoundTripTest` — barlines, repeats, key signatures, empty/default song (holds `barlineTypesOf` / `assertPopulatedSubsetEquals`).
> - `MusicXmlNoteRoundTripTest` — note durations, accidentals, stems, attachments (holds `assertNoteEquals`).
> - `MusicXmlWriterOutputTest` — write-forward fidelity + `*WriterOutputIsSchemaValid` cases.
> - `MusicXmlReaderLenienceTest` — reader robustness on hand-crafted XML (holds `scoreWithMeasureBody`).
> - `MusicXmlBeamRoundTripTest` — beam round-trip + writer-output (holds `beamValue`).
> - `MusicXmlSpanRoundTripTest` — tie / tuplet / trill + mid-line / measure-boundary.
> - `MusicXmlHairpinRoundTripTest` — crescendo / diminuendo + wedge edge cases (holds `assertHairpinEquals` / `wedgeAttribute`).
> - `MusicXmlEndingRoundTripTest` — first/second endings.

* * *
## ✅ Phase 1: Tags and Mapping Helpers
**Status:** Complete  
**BlockedBy:** —  
**Recommended model/effort:** Sonnet 4.6, low–medium — the tag constants and `WedgeTypeMapping` are mechanical mirroring of `MusicXmlTags` / `BarlineStyleMapping`; the `BeamMath` extraction (task 3) is a small pure refactor that touches `LayoutEngine`/`BeamGroupRenderer` and must keep layout/render tests green.
### Tasks
1. Add the span element/attribute/value constants to `MusicXmlTags`, grouped with comments in the existing style: `beam` (+ `number` attr; values `begin`/`continue`/`end`), `tie` and `tied` (+ `type` attr; values `start`/`stop`), `time-modification` / `actual-notes` / `normal-notes`, `tuplet` (+ `type`/`number` attrs), `direction` / `direction-type` / `wedge` (+ `type` attr; values `crescendo`/`diminuendo`/`stop`), `ornaments` / `trill-mark` / `wavy-line` (+ `type` attr), `ending` (+ `number`/`type` attrs; values `start`/`stop`/`discontinue`), and the `relative-x`/`relative-y` attribute names if not already present from Phase 3.
  
2. Add a small `Hairpin` → wedge-type-token mapping (forward `Crescendo` → `crescendo`, `Diminuendo` → `diminuendo`; inverse token → which `Hairpin` subclass to build). Keep it as a tiny helper (static methods on a new `WedgeTypeMapping` mirroring the mapping-class idiom, or a constant pair) — no large table.
  
3. **Extract a shared pure `BeamMath` helper in `layout/`** (Issue 1A) so beam derivation has **one source of truth** and the writer needs no `LayoutResult`. Move the `stubRight` rule (`LayoutEngine.java:537-562`) and `beamCount` (`:790`) — and, to avoid a second copy, the beam-level/run logic `getBeamLevel`/`isNoteTypeInLevel` (currently in `BeamGroupRenderer`) — into pure static methods keyed only off `Line` + indices. Update `LayoutEngine` (and `BeamGroupRenderer` if the level/run methods move) to call the helper; behavior must be identical. The existing layout/render tests must stay green (this is a pure refactor). The writer (Phase 2) and reader consume the helper without touching layout state.
  
4. Run `./scripts/compile.sh` → must report SUCCESS.
  

* * *
## ✅ Phase 2a: Writer Infra: NoteWriteContext + Per-Index Precompute
**Status:** Complete  
**BlockedBy:** 1  
**Recommended model/effort:** Sonnet 4.6, medium — a contained `writeNote` signature refactor plus the resolve-once per-index precompute that every later writer phase consumes. No span emission yet; this is the shared plumbing.
### Tasks
1. **Refactor `writeNote` to take a context object** (do this before threading any span data). Introduce a small `NoteWriteContext` record bundling the per-note write inputs — `note`, `typeToken`, `nextIsBreathMark`, `pendingStopGlissando`, and (added in 2b/2c) the per-note span markers — and convert `writeNote(pw, ctx)` and `writeNotations(pw, ctx)` to take it. Destructure at the top of each body (`var note = ctx.note();` …) so the bodies barely change. Surface is contained: one call site each (`MusicXmlWriter.java:197`, `:315`). This caps the parameter-list growth that Phases 2b–7 would otherwise compound.

2. **Unified per-index span precompute (one pass, resolve-once).** Once per line (before the element loop in `writeLineDrivenMeasures`), build a **single** per-element-index marker structure (a plain record/array — not a framework) covering **all six** span types: `Beam`, `Tie`, `Tuplet`, `Trill` (per-note, consumed in `writeNote`) **and** the `Crescendo`/`Diminuendo` wedge + `Ending` markers (measure-level, consumed in the element loop — see Phase 3). For each span returned by the `Line` accessors, **resolve its anchor/end index exactly once** via `getAnchorElementIndex()`/`getEndElementIndex()` and bucket roles (**anchor** / **interior** / **end**) into the per-index entry — do **not** call `getAnchorElementIndex()` inside the per-element loop (it is `ArrayList.indexOf`, O(n); calling it per-element-per-span is an accidental O(spans·n²)). The element loop then does O(1) lookups. Thread the per-note slice of this lookup into `writeNote` via the `NoteWriteContext` from task 1.

3. Run `./scripts/compile.sh` → must report SUCCESS.


* * *
## ✅ Phase 2b: Writer: Beam
**Status:** Complete  
**BlockedBy:** 2a  
**Recommended model/effort:** Sonnet 4.6, medium — the per-level beam-value emission, derived **exactly** from the shared `BeamMath` helper extracted in Phase 1 (no `LayoutResult`, no new algorithm): primary run, secondary runs, and hooks. Isolated from the other per-note spans because the hook/level logic is the densest part of the writer.
### Tasks
1. **Beam** (note-level, outside `<notations>`): emit per-note `<beam number="N">`
   values for every beam level, derived **exactly** from note durations + position
   via the shared `BeamMath` helper extracted in Phase 1 — **no `LayoutResult`**.
   Use `getBeamLevel` (max depth from the shortest note), `isNoteTypeInLevel` (is a
   note short enough for level L), and the extracted `stubRight` rule
   (`LayoutEngine.java:537-562` + `beamCount` `:790`). Consume the per-note beam
   markers from the unified per-index precompute (Phase 2a Task 2).
   - **Primary beam** (`number="1"`): always `begin` on the anchor, `continue` on
     interior notes, `end` on the end note. The level-1 beam connects the whole
     group, so it is never a hook.
   - **Secondary beams** (`number="2"` = 16th, `number="3"` = 32nd, per
     `BEAM_LEVELS`): within the group find each maximal contiguous run of notes
     `isNoteTypeInLevel` for that level. A run of length ≥ 2 emits
     `begin`/`continue`/`end` at that `number`; a run of length 1 is a **partial
     beam (hook)** whose direction is the helper's `stubRight` boolean:
     `stubRight == true` → `forward hook`; `stubRight == false` → `backward hook`.
     (This is exact, not best-effort: `stubRight` is computed purely from
     `beamCount` of the note and its neighbors + group start/end — zero pixel
     geometry.)
   - The reader recovers the span from the `number="1"` `begin … end` run only and
     ignores secondary beams/hooks (layout re-derives them on load), so the
     secondary values are **write-forward** external-renderer fidelity — exact, but
     not round-trip data. Round-trip cannot catch them, so they get explicit
     writer-output assertions (Phase 6a).
   - Skip a degenerate single-note beam (anchor == end cannot be beamed).

2. Run `./scripts/compile.sh` → must report SUCCESS.


* * *
## ✅ Phase 2c: Writer: Tie, Tuplet, Trill
**Status:** Complete  
**BlockedBy:** 2a  
**Recommended model/effort:** Sonnet 4.6, medium — schema-ordered emission of start/stop markers for the three remaining per-note spans, plus the single canonical `<notations>` child-order comment update. Independent of 2b; both consume the Phase 2a precompute.
### Tasks
1. **Tie**: emit `<tie type="start">` (sound, note-level, before `<notations>`) and `<tied type="start">` (notation, inside `<notations>`) on the anchor; `<tie type="stop">`+`<tied type="stop">` on the end; interior notes emit **both** stop then start (chaining the pair). Honor `<note>` child order (`<tie>` after duration, before `<type>`) and `<notations>` order (`<tied>` first).
  
2. **Tuplet**: emit `<time-modification>` (`<actual-notes>`grade`</actual-notes>` + `<normal-notes>`largest-power-of-two-below-grade`</normal-notes>`) on **every** note in the span (note-level, after `<dot>`, before `<accidental>` per schema); emit `<tuplet type="start" number="1">` inside `<notations>` on the anchor and `type="stop"` on the end. Carry `verticalPositionSs` (ss → ×10 tenths) as `relative-y` on the start `<tuplet>`, only when non-zero.
    
3. **Trill** (inside `<notations><ornaments>`): emit `<trill-mark/>` on the anchor; `<wavy-line type="start">` on the anchor and `type="stop">` on the end. For a single-note trill (anchor == end) emit `<trill-mark/>` plus `<wavy-line>` start and stop on that one note. Carry `yPositionSs` (ss → ×10 tenths) as `relative-y` on the start `<wavy-line>`, only when non-zero.
  
4. **Update the existing `<notations>` child-order comment in place** at `MusicXmlWriter.java:216-238` so it documents the full new order (`tied, slur*, tuplet*, ornaments*, technical*, articulations*, dynamics*, fermata*`, with `<tie>`/`<time-modification>`/`<beam>` at note level) — this is the single canonical order diagram; do **not** add a second copy elsewhere. The same diagram covers the extended `<note>`/`<notations>` pipeline showing where `<tie>`, `<time-modification>`, `<tied>`, `<tuplet>`, `<ornaments>`(`<trill-mark>`/`<wavy-line>`), and `<beam>` slot in. Validate one populated sample against `docs/musicxml-4.0-schema/`.
  
5. Run `./scripts/compile.sh` → must report SUCCESS.
  

* * *
## ✅ Phase 3: Writer: Hairpins and Endings
**Status:** Complete  
**BlockedBy:** 1, 2a  
**Recommended model/effort:** Opus 4.8, high — the endings' one-span-to-two-voltas expansion threads through the Phase 2 barline-segmentation loop without disturbing it; the wedge directions are moderate but share that loop.
### Tasks
1. **Hairpin wedges** (measure-level `<direction>`, in the `writeLineDrivenMeasures` element loop, not `writeNote`): for each `Crescendo` / `Diminuendo`, emit **both** wedges immediately **before** their bound note's `<note>` — start wedge `<direction><direction-type><wedge type="crescendo|diminuendo" number="1"/></direction-type></direction>` before the **anchor** note, and `<wedge type="stop" number="1"/>` before the **end** note. (Both-before-the-note is deliberate: it lets the reader use one uniform "bind to the next `<note>`" look-ahead rule — see Phase 5. The earlier draft placing the stop wedge *after* the end note is superseded.) Always `number="1"`: the app must not produce overlapping wedges (a pre-existing bug fixed in a later plan), so only one wedge is ever open. Use the Phase 1 wedge-type helper, and the wedge markers from the unified per-index precompute (Phase 2a Task 2).
  
2. **Hairpin geometry**: carry `x1ShiftSs` (ss → ×10 tenths) as `relative-x` on the **start** wedge, `x2ShiftSs` as `relative-x` on the **stop** wedge, and `yShiftSs` as `relative-y` on the start wedge — each only when non-zero.
  
3. **Endings**: fold `<ending>` children onto the `<barline>` elements Phase 2 emits. For each `Ending` (`LineEndingSupport.findEndings(line)`): compute its live split index (`Ending.getSplitIndex(line)` / `findRepeatSplitElement(line)`). At the anchor barline emit `<ending number="1" type="start">`; at the split barline emit `<ending number="1" type="stop">` **and** `<ending number="2" type="start">`; at the end barline emit `<ending number="2" type="stop">`. A split-less ending emits a single `number="1"` start (anchor) → stop (end). `<ending>` precedes `<repeat>` inside `<barline>` per schema.
  
4. Integrate the ending emission into the existing barline-writing helpers (`writeBarline`, `writeBackwardRepeatRightBarline`, `openForwardRepeatMeasure`, `writeInvisibleRightBarline`) so `<ending>` children attach to the right `<barline>` without changing measure segmentation. The per-index ending markers (which `(number, type)` pairs attach at each barline index) come from the unified per-index precompute (Phase 2a Task 2). Add an inline ASCII diagram of the anchor / split / end → volta mapping.
  
5. Run `./scripts/compile.sh` → SUCCESS; validate a song with a hairpin and a two-bracket ending against `docs/musicxml-4.0-schema/`.
  

* * *
## ✅ Phase 4: Reader: Per-Note Run Collapse
**Status:** Complete  
**BlockedBy:** 2a, 2b, 2c  
**Recommended model/effort:** Opus 4.8, high — the reversibility core: maximal marker runs must re-collapse to the exact original anchor/end index pairs across beam, tie, tuplet, and trill.
### Tasks
1. Extend the `Where` enum and SAX handlers with the per-note span states: `BEAM`, `TIE`/`TIED`, `TIME_MODIFICATION`/`ACTUAL_NOTES`/`NORMAL_NOTES`, `TUPLET`, `ORNAMENTS`/`TRILL_MARK`/`WAVY_LINE` (inside `NOTE`/`NOTATIONS`). Accumulate character data for `<actual-notes>` as in the Phase 3 unconditional-`characters` approach.
  
2. Add per-span pending-start fields mirroring `pendingSlideStart`: a pending anchor `StaffElement` for the active beam, tie, tuplet, and trill run. On a `start`/`begin` marker, record the current note as the run's anchor; on `end`/`stop`, build the `RangeElement(anchorNote, currentNote)` and add it to the line (`addBeaming` / `addTie` / `addTuplet` / `addRangeElement` for the `Trill`); clear the pending field. Interior `continue` markers need no action beyond keeping the run open.
  
3. **Tie** chaining: collapse on the **`<tied>`** (notation) stream **only** — `<tie>` (sound) is write-forward/sound-only and is **ignored** for span reconstruction, so there is a single source of truth and no double-open/close. A multi-note tie emits stop+start on interior notes — treat a `<tied>` `stop` immediately followed by a `<tied>` `start` on the same note as continuing the run (close the pair, the new start re-opens), so the whole chain collapses to one `Tie(firstAnchor, lastEnd)`. Match the legacy `createTiesFromPendingPairs` merge semantics.
  
4. **Tuplet**: `<time-modification>` repeats on every note, so pin the capture point — at `<tuplet type="start">` (the anchor) capture `grade` into the pending-tuplet state from the `<actual-notes>` value already parsed for that note (in `<note>` child order `<time-modification>` precedes `<notations>`, so it is available). Ignore the repeated per-note `<time-modification>` and ignore `<normal-notes>`. On `<tuplet type="stop">` build `Tuplet(anchor, end, grade)` from the pending state; restore `verticalPositionSs` from the start `<tuplet>` `relative-y` (tenths → ss, ÷10) when present. **Trill**: build `Trill(anchor, end)` (or single-note `Trill(anchor)` when start and stop land on one note); restore `yPositionSs` from the start `<wavy-line>` `relative-y`.
  
5. **Beam**: collapse a `begin … (continue)* … end` run to `Beam(anchor, end)`; ignore the `number` attribute beyond level 1.
  
6. **Lenient read — both directions.** Handle a **dangling start** (a `begin`/`start` whose matching `end`/`stop` never arrives, e.g. truncated input) the same lenient way as Phase 3's dangling slide: drop the span (build nothing) and log at line/part-end flush — a range needs both endpoints. **Symmetrically**, handle an **orphan stop/end** (a `stop`/`end` arriving with no pending anchor, e.g. malformed/foreign XML): ignore it + log, mirroring `resolveSlide`'s orphan-stop branch (`MusicXmlReader.java:818`). Apply both to all four per-note spans. Add an inline ASCII diagram of the new `Where` states and the per-span pending-anchor pairing.
  
7. Run `./scripts/compile.sh` → must report SUCCESS.
  

* * *
## ✅ Phase 5: Reader: Hairpins and Endings Collapse
**Status:** Complete  
**BlockedBy:** 3  
**Recommended model/effort:** Opus 4.8, high — wedge start/stop pairing plus the two-volta → single-`Ending` recombination, the structurally hardest read piece; the split must recompute live and match the writer's anchor/end choices.
### Tasks
1. Extend the `Where` enum / handlers with `DIRECTION`/`DIRECTION_TYPE`/`WEDGE` (measure-level) and the `<ending>` child of `BARLINE` (alongside the Phase 2 barline handling).
  
2. **Hairpin wedges**: pair a `<wedge type="crescendo|diminuendo">` with the next `<wedge type="stop">` using a **single** `pendingWedge` field (anchor note + which subclass from the Phase 1 helper). Because the writer places **both** wedges immediately before their bound note, the reader uses **one uniform rule: each wedge binds to the next `<note>` after it** — so anchor = the note following the start wedge, end = the note following the stop wedge. Document this binding precisely; it inverts the writer's both-before-the-note placement. On `stop`, build `Crescendo` / `Diminuendo` via `addCrescendo` / `addDiminuendo`; restore `x1ShiftSs` from the start wedge `relative-x`, `x2ShiftSs` from the stop wedge `relative-x`, `yShiftSs` from the start wedge `relative-y` (tenths → ss).
   - **Defensive overlap drop:** a single `pendingWedge` assumes only one wedge is ever open (the app must not produce overlapping wedges — pre-existing bug, fixed later). If a *second* start arrives while `pendingWedge` is non-null, **log + drop** the new start rather than silently mispairing — cheap insurance against round-tripping a today-buggy song.
  
3. **Endings**: collect `<ending>` markers per barline. Maintain pending state keyed by `number`: on `number="1" type="start"` record the anchor barline element; on `number="2" type="stop"` (or `discontinue`) record the end barline element and build one `Ending(anchorBarline, endBarline)` via `addRangeElement`. The `number="1" stop` / `number="2" start` on the split barline need no stored value — the split recomputes live via `findRepeatSplitIndex`. A split-less ending (only `number="1"` start→stop) builds `Ending(anchor, end)` directly.
  
4. **Barline-element identity invariant.** The anchor/end of an `Ending` are **barline** `StaffElement`**s**. Reversibility requires the reader to create **exactly one line element per emitted `<barline>`, at a stable index** — explicitly verify this holds for the two risky cases: an ending anchored on a `SINGLE_BARLINE` (a plain barline must still produce a line element, not be folded into a measure boundary) and the `REPEAT_LEFT_RIGHT` split barline (one element). Ensure the ending markers resolve to those exact element instances (the barline created when the `<barline>` is parsed), so `getAnchorElementIndex()`/`getEndElementIndex()` recover the original indices. Phase 7 asserts the recovered **indices**, not just span existence. Add an inline ASCII diagram of the two-volta → single-span collapse.
  
5. **Lenient read — both directions** (mirror Phase 4 Task 6). Drop + log a dangling/partial ending (missing `number="2" stop`) and an unpaired wedge start. **Symmetrically**, ignore + log an **orphan stop** with no pending anchor — an `<ending>` stop with no matching start, or a `<wedge type="stop">` with `pendingWedge` null.
  
6. Run `./scripts/compile.sh` → must report SUCCESS.
  

* * *
## ✅ Phase 6a: Round-Trip Tests: Beam
**Status:** Complete  
**BlockedBy:** 4  
**Recommended model/effort:** Sonnet 4.6, medium — builds the shared span-assertion helper and exercises beams through the full write→read cycle, including the write-forward secondary/hook assertions round-trip cannot catch.
### Tasks
1. Add a test-side `assertRangeElementEquals` helper (or per-type helpers) comparing a reloaded span to the expected one field-by-field: anchor index, end index, plus `Tuplet.grade`/`verticalPositionSs` and `Trill.yPositionSs` (the per-type fields are exercised in 6b but the helper lives here, shared). Do **not** add `equals()`/`hashCode()` to the span classes or `StaffElement` (same constraint as Phase 3 — it breaks line index lookups and layout caches).
  
2. Round-trip **beams**: a 2-note beam, a 4-note beam, and two disjoint beams on one line; assert each re-collapses to the identical `(anchor, end)` index pair. Add **writer-output assertions** for the `number="1"` `begin/continue/end` run plus the `number="2"` secondary-beam values — round-trip can't catch these (the reader ignores secondary beams). **Cover every `stubRight` branch** for the hook direction (not just one config): a hook at the **group start** (`stubRight` true → `forward hook`), a hook at the **group end** (`stubRight` false → `backward hook`), and an **interior hook at a beam break** — so all branches of the extracted `BeamMath` stub rule are exercised.
  
3. Run `./scripts/compile.sh`, then `./scripts/test.sh unit` → both must be green.
  

* * *
## ✅ Phase 6b: Round-Trip Tests: Tie, Tuplet, Trill
**Status:** Complete  
**BlockedBy:** 6a  
**Recommended model/effort:** Sonnet 4.6, medium — exercises the three remaining per-note spans plus the mid-line and measure-boundary-crossing cases through the write→read cycle, reusing the 6a assertion helper.
### Tasks
1. Round-trip **ties**: a 2-note tie and a 3+-note tie chain (exercising the interior stop+start chaining); assert the chain re-collapses to one `Tie`.
  
2. Round-trip **tuplets**: a triplet (grade 3) and a quintuplet (grade 5), each with and without a non-zero `verticalPositionSs`; assert `grade` and `verticalPositionSs` survive. Add a **writer-output assertion** parsing the emitted `<time-modification>` to confirm `<actual-notes>` = grade and `<normal-notes>` = the expected power of two (round-trip can't catch `<normal-notes>`, which the reader ignores).
  
3. Round-trip **trills**: a single-note trill (anchor == end) and a multi-note trill, with and without `yPositionSs`; assert the span and offset survive.
  
4. Add a **mid-line span** case: a span that starts and stops mid-line (notes before and after it) plus a span crossing a measure boundary (a barline between anchor and end), confirming markers re-collapse correctly across the measure split.
  
5. Run `./scripts/compile.sh`, then `./scripts/test.sh unit` → both must be green.
  

* * *
## ✅ Phase 7a: Round-Trip Tests: Hairpins
**Status:** Complete  
**BlockedBy:** 5, 6b  
**Recommended model/effort:** Sonnet 4.6, medium — crescendo/diminuendo round-trips plus the wedge edge cases (back-to-back, measure-boundary, measure-start, overlap drop) the round-trip harness cannot otherwise force.
### Tasks
1. Round-trip **crescendo** and **diminuendo**, each with and without non-zero `x1ShiftSs`/`x2ShiftSs`/`yShiftSs`; assert subclass, `(anchor, end)`, and all three shifts survive. Add a writer-output assertion that `x1` rides on the start wedge `relative-x`, `x2` on the stop wedge `relative-x`, and `y` on the start wedge `relative-y`. **Wedge edge cases:** back-to-back wedges (one span's stop adjacent to the next span's start), a wedge spanning a **measure boundary** (start and stop in different measures — a long crescendo), a wedge whose anchor is the **first note of a measure**, and the **defensive overlap drop** (a second start while one is pending is logged and dropped, leaving exactly one span).
  
2. Run `./scripts/compile.sh`, then `./scripts/test.sh unit` → both must be green.
  

* * *
## ✅ Phase 7b: Round-Trip Tests: Endings
**Status:** Complete  
**BlockedBy:** 5, 6b  
**Recommended model/effort:** Sonnet 4.6, medium — ending round-trips with explicit recovered-index assertions enforcing the one-element-per-barline invariant, including the `SINGLE_BARLINE`-anchored and split-less cases.

> **Outcome / deviation.** The four ending tests had been drafted with the
> structural barlines *adjacent* (no notes between anchor/split/end) — not valid
> music, and they failed. Rewritten with a one-note volta between each pair of
> barlines (minimal valid endings), and the recovered indices shifted to
> anchor 0 / split 2 / end 4 (split-less: 0 / — / 2). Surfacing realistic
> content exposed **two pre-existing read-side ordering bugs**, both around a
> deferred `REPEAT_RIGHT`:
> 1. **Terminal-slot reinsertion during load.** The reader added `currentLine`
>    to the song *before* appending its elements, so a valid-terminal barline
>    (`REPEAT_RIGHT` / `FINAL_DOUBLE_BARLINE`) landing mid-line was treated as
>    the auto-maintained terminal and subsequent elements were inserted *before*
>    it. Fix: the reader now builds each line **detached** and commits it via
>    `commitCurrentLine()` at the next line break / `</part>` (mirroring the
>    `buildSong` build-then-add order). Guarding `Line.addElement` itself was
>    tried first and rejected — it broke ~many tests that rely on reinsertion
>    during suspended tracking, so the fix is reader-local.
> 2. **`finishNote` flush gap.** A `REPEAT_RIGHT` held pending for
>    `REPEAT_LEFT_RIGHT` detection was not flushed before a following note, so any
>    backward repeat with content after it (no intervening barline) mis-ordered.
>    Fix: `finishNote` flushes `pendingRepeatRight` before appending the note.
>
> **Post-load terminal pass.** Because the reader now appends faithfully (and no
> longer incidentally pulls a valid-terminal barline to the line end), a loaded
> song whose last line does not end in a valid terminal would violate the model's
> terminal invariant. New `Song.installTerminalAfterParsing()` restores it in one
> pass at `</score-partwise>` while tracking is still suspended (silent: no
> notification, no `modified` flag). It is a no-op when the last line already ends
> in a valid terminal, so the four ending cases (and all real songs, whose writer
> always emits a closing terminal) round-trip losslessly; only terminal-less
> fixtures are normalized.
>
> `MusicXmlBarlineRoundTripTest` TU5 had codified bug 1's reordered output as
> "expected"; its assertion + comment were corrected to the faithful order. Eight
> further round-trip/lenience fixtures that built terminal-less songs were made
> valid music (a closing `FINAL_DOUBLE_BARLINE`) so the post-load pass is a no-op
> for them.
### Tasks
> **Target file:** `MusicXmlEndingRoundTripTest` — the ending round-trip tests live here after the test-file split. Shared `buildSong` / `roundTrip` plumbing and the `assertRangeElementEquals(RangeElement, …)` helper come from the `MusicXmlRoundTripSupport` base.

1. Round-trip **endings**: a full two-bracket ending (anchor `REPEAT_LEFT`, split `REPEAT_RIGHT`, end terminal), a `REPEAT_LEFT_RIGHT`-split ending, and a **`SINGLE_BARLINE`-anchored** ending (exercises the plain-barline-creates-an-element path). For each, **assert the recovered anchor/end element indices explicitly** (not just that an `Ending` exists), enforcing the one-element-per-barline invariant from Phase 5 Task 4; assert the live split recomputes correctly. Add a split-less single-bracket ending case.
  
2. Run `./scripts/compile.sh`, then `./scripts/test.sh unit` → both must be green.
  

* * *
## ✅ Phase 7c: Round-Trip Tests: Edge Cases & Schema Gate
**Status:** Complete  
**BlockedBy:** 7a, 7b  
**Recommended model/effort:** Sonnet 4.6, medium — the lenient-read edge cases the round-trip can't produce, the all-span schema-validation case, and the final no-regression green-suite gate for the whole sub-plan.
### Tasks
> **Target files** (after the test-file split): the lenient-read cases → `MusicXmlReaderLenienceTest` (it owns the hand-crafted-XML reader-robustness tests and the `scoreWithMeasureBody` helper); the all-span schema-validity case → `MusicXmlWriterOutputTest` (home of the `*WriterOutputIsSchemaValid` cases). The no-regression gate spans `MusicXmlBarlineRoundTripTest` (Phase 2 structural + empty/default song) and `MusicXmlNoteRoundTripTest` (Phase 3 note-level). Shared plumbing/assertions live in the `MusicXmlRoundTripSupport` base.

1. Add **lenient-read** edge cases the round-trip can't produce, **both directions** (in `MusicXmlReaderLenienceTest`):
   - **Dangling starts:** a beam/tie/tuplet/trill start (truncated, no stop), an unpaired wedge start, and a partial ending (missing `number="2"` stop) — each drops the span and logs.
   - **Orphan stops:** a `stop`/`end` arriving with no pending anchor — for each per-note span, a `<wedge type="stop">` with no pending wedge, and an `<ending>` stop with no matching start — each is ignored + logged.
   Verify parsing completes and no partial span is created in either case.
  
2. Add an all-span `*WriterOutputIsSchemaValid` case (in `MusicXmlWriterOutputTest`) for a song populated with all six span types so the output validates against `docs/musicxml-4.0-schema/` (note, notations, direction, and barline child order all exercised).
  
3. Confirm per-test isolation (fresh `Song`, no leaked static state) and that the Phase 2/3 round-trips (`MusicXmlBarlineRoundTripTest`, `MusicXmlNoteRoundTripTest`) and the empty-song fallback still pass — no regression.
  
4. Run `./scripts/compile.sh`, then `./scripts/test.sh unit` → both must be green before the phase is done.
  

* * *
## Verification (whole sub-plan)
- `./scripts/compile.sh` reports SUCCESS after every phase.
  
- Writer output for a song populated with all six span types validates against `docs/musicxml-4.0-schema/`.
  
- `Song → MusicXML → Song` is lossless for every span: beaming, ties (incl. multi-note chains), tuplets (grade + `verticalPositionSs`), crescendo and diminuendo (+ `x1`/`x2`/`y` shifts), trills (single- and multi-note + `yPositionSs`), and first/second endings (two-bracket, `REPEAT_LEFT_RIGHT` split, and split-less) — every span re-collapses to its identical index pair.
  
- Mid-line and measure-boundary-crossing spans re-collapse correctly.
  
- Lenient read drops dangling/partial spans without error.
  
- The Phase 2 structural round-trip and the Phase 3 note-level round-trip still pass — no regression.

---
comments:
  c4:
    body: You will have to add something to Phase 2, task 2 about how to determine
      if `forward hook|backward hook` should be used.
    by: user
    at: 2026-06-29T22:32:24.504Z
  c5:
    body: >-
      Applied. The In-scope beam bullet now lists primary begin/continue/end plus
      forward hook / backward hook at secondary levels, and keeps the beam-value
      reference URL.
    by: AI
    at: "2026-06-29T23:10:00.000Z"
    re: c1
  c6:
    body: >-
      Resolved together with c3 and c4. Rewrote Phase 2 Task 2: the primary beam
      (number="1") is always begin/continue/end and never a hook; secondary
      levels add forward/backward hooks. Hook direction is the BeamGroupRenderer
      doDrawBeams leftOriented rule (stubRight()): leftOriented = ATTACH_RIGHT →
      backward hook, else ATTACH_LEFT → forward hook.
    by: AI
    at: "2026-06-29T23:10:00.000Z"
    re: c2
  c7:
    body: >-
      Resolved in the Phase 2 Task 2 rewrite (see c6).
    by: AI
    at: "2026-06-29T23:10:00.000Z"
    re: c3
  c8:
    body: >-
      Done. Phase 2 Task 2 now derives all beam values by mirroring
      BeamGroupRenderer (getBeamLevel / isNoteTypeInLevel / doDrawBeams) rather
      than inventing the algorithm, and spells out the hook-direction rule. Added
      BeamGroupRenderer to Key code touchpoints, a writer-output hook assertion to
      Phase 6 Task 2 (hooks are write-forward; the reader recovers the span from
      the number="1" run only), and noted beam-value derivation in the Phase 2
      effort rationale.
    by: AI
    at: "2026-06-29T23:10:00.000Z"
    re: c4
  c9:
    body: >-
      Plan review round (BIG CHANGE mode). Applied 9 resolved decisions + writeNote
      refactor. Architecture: (1A) hook direction is EXACT, a pure function of
      durations+position — extract LayoutEngine's stubRight rule (:537-562) +
      beamCount (:790) into a shared pure BeamMath helper in layout/ (corrects the
      prior pointer at BeamGroupRenderer.doDrawBeams/stubRight()-via-LayoutResult,
      which the static writer cannot reach); (2A) one-line-element-per-<barline>
      invariant + index-level ending test; (Issue 3) both wedges emitted BEFORE
      their bound note (stop moved from after→before end note) with number="1" and
      a uniform reader "bind to next <note>" look-ahead + single pendingWedge +
      overlap log-drop (overlap is a pre-existing bug fixed in a later plan);
      (4A) reader collapses ties on <tied> only, <tie> sound-only. Code quality:
      (5A) one unified per-index precompute for all six spans; (6A) symmetric
      orphan-stop/end lenient handling; (7A) update the :216-238 notations-order
      comment in place; (8A) capture tuplet grade at <tuplet type=start>. Tests:
      T1A stub-branch coverage, T2A SINGLE_BARLINE anchor + index assert, T3A
      wedge edge set (back-to-back, measure-cross, measure-start, overlap-drop),
      T4A orphan-stop. Perf: (9A) resolve span endpoints once in the precompute.
      Also folded a writeNote→NoteWriteContext refactor into Phase 2 (first step)
      to cap parameter-list growth. No TODOS captured (overlap fix tracked in a
      later plan). No unresolved decisions.
    by: AI
    at: "2026-06-29T23:55:00.000Z"
