# Sub-plan: Phase 7 — Header, Layout & Extension Fields

**Type:** Sub-plan  <br>
**Parent:** [musicxml-conversion.md](./musicxml-conversion.md) → Phase 7  <br>
**Created:** 2026-07-04  <br>
**Status:** Complete  <br>
**BlockedBy:** —

---

## Purpose

Add the everything-before-`<part-list>` document head plus in-measure
**annotations** to *both* the MusicXML writer and reader, round-tripping
losslessly with schema-valid output. This closes every remaining persisted field
except the per-line layout residuals noted below (deferred to Phase 8's gate).

**Delivered (each round-trips through writer + reader):**

- **Head metadata** — `<movement-title>` / `<movement-number>`, `<identification>`
  (`<creator>` composer/lyricist/arranger, `<rights>`, `<encoding>`), and the
  residual `<miscellaneous>` fields (composition-date, lyrics-date,
  composition-place, lyrics-source, unofficial-translation, sub-attribution-font,
  row-height-adjustment).
- **`<defaults>`** — `<scaling>`, `<page-layout>` carrying line width, and the three
  document fonts (`<music-font>` / `<word-font>` / `<lyric-font>`). **DocumentFonts
  is threaded through the writer/reader** so fonts round-trip.
- **`<credit>` elements** — title, **subtitle** (canonical), each attribution role,
  and the score-below text blocks (underlyrics / Bangla / translation / footnotes,
  canonical). Fonts ride in `<credit-words>` attributes; the attribution user
  Y-offset rides on `relative-y`.
- **Annotations** — `<direction placement="above|below"><direction-type><words>`
  with halign/justify, `userYOffsetSs` → `relative-y`.

**Explicitly OUT of scope (deferred):**

- **External-renderer absolute geometry (Option A).** Computed absolute positions —
  credit `default-x`/`default-y` and page/system distances — are
  **write-forward-only** (best-effort or omitted) and are **not** read back.
  SongScribe recomputes them via layout. Only the model *deltas* (`relative-y`,
  misc-fields) round-trip. Wiring `SongLayoutMetrics` / `PageModel` / rendered
  `MainPanel` geometry for pixel-accurate external placement is a later task, not
  this phase. Tracked: [#512](https://github.com/vasudeva-server/SongScribe/issues/512).
- **Per-line layout residuals** — `XML_LYRICS_YPOS` (per-line lyric Y) and
  `XML_NOTE_DIST_CHANGE` (per-line spacing factor). These live on `Line`, not
  `Song`, and need a per-measure `<print>` home out of proportion to this phase.
  Called out here so Phase 8's corpus gate can force them if the corpus uses them.
- **Production save/open wiring.** No production caller invokes the MusicXML
  writer yet; tests drive it. The UI/save-path cutover is Phase 8.
- **`dynamic-layout` flag** — always `true`, carries no information; dropped.
- **Annotation `BELOW` *rendering*.** `SystemStacker.stackAnnotations` still stacks
  every annotation above the staff (it never reads `getPlacement()`). That is a
  layout bug, not a serialization concern; the `placement` value round-trips
  regardless. Not in scope. Tracked:
  [#511](https://github.com/vasudeva-server/SongScribe/issues/511).
- **Model-layer words-date normalization.** Decision 1B normalizes words-date→empty
  (when it equals composition-date) in the **writer only**, to keep the model
  read-only this phase. Pushing the invariant down to the model/UI so the workaround
  can be removed is deferred. Tracked:
  [#513](https://github.com/vasudeva-server/SongScribe/issues/513).

## Implementation Approach

The head blocks insert into `MusicXmlWriter.writeSong` **between the
`<score-partwise>` open tag and `<part-list>`**, in MusicXML 4.0 content order:
`<movement-number>` → `<movement-title>` → `<identification>` → `<defaults>` →
`<credit>*` → `<part-list>`. The reader attaches new `Where` states under
`case SCORE_PARTWISE` in `startElement`/`endElement`. Annotations live inside
`<part>` as measure-level `<direction>` siblings of `<note>`, so they hook the
existing measure/note writer and the reader's `DIRECTION` subtree.

Every slice mirrors the Phase 3–6 vertical-slice pattern: writer + reader +
per-slice round-trip test, writer output validated against
`docs/musicxml-4.0-schema/`.

### Data-flow contract (canonical / display-only / write-forward)

Every field routes into exactly one of three classes; the reader treats each
differently. This is the core correctness contract of the whole phase — the same
value (composer, dates, place) is emitted in **both** head (canonical) and a
credit (display-only), and the reader **must** take head and ignore the credit or
a hand-edited credit corrupts the model. An inline copy of this diagram belongs
above the reader's `</credit>` dispatch method.

```
                         WRITER                          READER
  Song field ─────────────┬─────────────────┐
                          │                 │
   ┌──────────────────────▼───┐   ┌─────────▼────────────┐
   │ HEAD (identification/     │   │ CREDIT (<credit>)    │
   │  movement/miscellaneous)  │   │  fonts + positions   │
   └──────────┬────────────────┘   └───┬──────────────┬───┘
              │                         │              │
   ┌──────────▼──────────┐  ┌───────────▼───┐  ┌───────▼─────────────┐
   │ CANONICAL           │  │ DISPLAY-ONLY  │  │ WRITE-FORWARD       │
   │ read → model        │  │ ignored;      │  │ ignored;            │
   │                     │  │ re-derived    │  │ recomputed/constant │
   ├─────────────────────┤  ├───────────────┤  ├─────────────────────┤
   │ movement-title/num  │  │ title credit  │  │ rights, software,   │
   │ creators→arrangement│  │ composer/     │  │ encoding-date,      │
   │ misc-fields (dates, │  │  lyricist/    │  │ supports, scaling,  │
   │  place, source, …)  │  │  arranger/    │  │ music-font,         │
   │ line-width,rowheight│  │  date/rights/ │  │ page-height,        │
   │ word/lyric fonts    │  │  place credits│  │ default-x/default-y │
   │ subtitle credit ◄───┼──┤ (re-derived   │  │ (external renderer) │
   │ 4 score-below credit│  │  from HEAD)   │  │                     │
   │ attribution rel-y   │  └───────────────┘  └─────────────────────┘
   │ annotations         │
   └─────────────────────┘
   INVARIANT: for composer/lyricist/dates/place the SAME value is emitted in
   BOTH head (canonical) and a credit (display-only). The reader MUST take head
   and ignore the credit, or a hand-edited credit corrupts the model.
```

### Resolved layout-fidelity decision (Option A)

Manual adjustments are stored in the model as **deltas from a computed base**, not
absolute coordinates, so they round-trip without any rendered-geometry coupling:

- Attribution nudge → `Attribution.getUserYOffsetSs()` → `relative-y` on the
  attribution `<credit-words>` (read back).
- Row-height adjustment → `rowHeightAdjustmentSs` (a delta) → `<misc-field
  name="row-height-adjustment">` (read back exactly; schema accepts the
  misc-field).
- Line width → `lineWidthSs` (a real value) → `<page-layout><page-width>` (read
  back).

`default-x`/`default-y` and computed distances carry the absolute base for
external renderers only and are **ignored on read** (same treatment as note
`default-x` and glissando endpoints from Phase 3–4).

### Serialization conventions (resolved)

- **No DOCTYPE, no `standalone`.** Keep the writer's existing
  `<?xml version="1.0" encoding="UTF-8"?>` declaration unchanged. The reader sets
  `disallow-doctype-decl=true` (`MusicXmlReader.java:66`), so any emitted DOCTYPE
  would make the reader throw on its own output.
- **`<supports>` — always emit** these three fixed tags inside `<encoding>`
  (write-forward, ignored on read): `<supports element="accidental" type="yes"/>`,
  `<supports element="beam" type="yes"/>`, `<supports element="stem" type="yes"/>`.
- **Fonts** come from a `DocumentFontsHolder` threaded into the writer
  (`ScoreView` is the production holder; tests pass `DocumentFonts.defaultFonts()`)
  and are returned as a `DocumentFonts` from the reader. Each `FontKey` maps to a
  `java.awt.Font`; emit `font-family` = `getFamily()`, `font-size` = `getSize()`,
  `font-weight` = `isBold() ? "bold" : "normal"`, `font-style` =
  `isItalic() ? "italic" : "normal"` (`java.awt.Font` has **no** weight accessor).

### Head metadata mapping (element → source)

| MusicXML | Source | Direction |
|---|---|---|
| `<movement-title>` | `song.getTitle()` (bare) | round-trip |
| `<movement-number>` | `song.getNumber()` — omit element if empty | round-trip |
| `<creator type="composer">` | `song.getComposer()` (never empty — coerced to `SRI_CHINMOY`) | round-trip |
| `<creator type="lyricist">` | `song.getLyricist()` | round-trip |
| `<creator type="arranger">` | `Song.SRI_CHINMOY`, only when `song.isArrangement()`; omit otherwise | round-trip (the flag) |
| `<rights>` | `String.format(COPYRIGHT, currentYear)` — **no model field**; constant | write-forward (ignored on read) |
| `<encoding><software>` | `"SongScribe " + Version.PUBLIC_VERSION` (String, e.g. `"2.0.0"`) | write-forward |
| `<encoding><encoding-date>` | `LocalDate.now()` ISO_LOCAL_DATE | write-forward |
| `<encoding><supports>` ×3 | fixed `element="accidental"` / `"beam"` / `"stem"`, each `type="yes"` (always emitted) | write-forward |

### Miscellaneous-field mapping (`<miscellaneous><miscellaneous-field name="…">`)

All round-trip (read back verbatim). Emit each only when populated; omit the whole
`<miscellaneous>` block when none apply.

| `name` | Source | Notes |
|---|---|---|
| `composition-date` | `getYear()`/`getMonth()`/`getDay()` via `DateUtils.toIsoDate` | omit if year blank; partial forms `1987` / `1987-12` / `1987-12-01` |
| `lyrics-date` | `getWordsYear()`/`getWordsMonth()`/`getWordsDay()` via `DateUtils.toIsoDate` | writer-prep normalizes words-date→empty when it equals composition-date, then omit when empty (1B) |
| `composition-place` | `song.getPlace()` | omit if blank |
| `lyrics-source` | `song.getLyricsSource().name()` (`LYRICIST`/`TEXT`/`OTHER`) | |
| `unofficial-translation` | `"true"` when `song.isUnofficialTranslation()` | omit when false |
| `sub-attribution-font` / `sub-attribution-font-size` | `fonts.getFont(FontKey.SUB_ATTRIBUTION)` family / size | needs DocumentFonts (Defaults phase) |
| `row-height-adjustment` | `rowHeightAdjustmentSs` | omit when 0 (Defaults phase) |

Dates go through the shared `songscribe.util.DateUtils` (Phase 1) — never a
MusicXML-local copy.

### Credit mapping (`<credit>` after `<defaults>`, before `<part-list>`)

`<credit-words>` carries `font-family`/`font-size`/`font-weight`/`font-style` (from
the role's `FontKey` `java.awt.Font`, per Serialization conventions), `justify`,
and — write-forward only — `default-x`/`default-y`. Emit a credit only when its
text is non-blank. `page` is always `1` (single-page model); omit the attribute
when `1`.

**Single routing table (resolved 6B).** A single static table/enum — keyed by
credit-type — carries each credit's `FontKey`, routing class (canonical /
display-only), `xml:lang`, and a reference to its text getter. Both the writer
(`writeCredits`, Phase 7) and the reader (`</credit>` dispatch, Phase 8) drive off
this one table so the two sides cannot drift. A test asserts every credit-type the
writer emits is handled by the reader.

| `<credit-type>` | Text source | `FontKey` | Read behavior |
|---|---|---|---|
| `title` | `song.getNumberedTitle()` | `TITLE` | display-only (re-derived from `<movement-*>`) |
| `subtitle` | `song.getSubtitle()` | `SUBTITLE` | **canonical — read back into metadata** |
| `composer` | `song.getComposer()` | `ATTRIBUTION` | display-only (re-derived from `<creator>`) |
| `lyricist` | `song.getLyricist()` | `ATTRIBUTION` | display-only |
| `arranger` | `Song.SRI_CHINMOY` (only when `isArrangement()`) | `ATTRIBUTION` | display-only |
| `composition date` | formatted date string | `ATTRIBUTION` | display-only (re-derived from misc-field) |
| `lyrics date` | formatted lyrics-date string (only when distinct) | `ATTRIBUTION` | display-only |
| `rights` | `String.format(COPYRIGHT, currentYear)` | `ATTRIBUTION` | display-only |
| `place` | `song.getPlace()` | `ATTRIBUTION` | display-only |
| `underlyrics` | `song.getUnderLyrics()` | `LYRICS` | **canonical — read back** |
| `bangla-lyrics` | `song.getBanglaLyrics()` (+ `xml:lang` on words) | `BANGLA` | **canonical — read back** |
| `translation` | `song.getTranslatedLyrics()` | `LYRICS` | **canonical — read back** |
| `footnotes` | `song.getFootnotes()` | `FOOTNOTE` | **canonical — read back** |

Every attribution `<credit-words>` carries the same `relative-y` =
`ssToTenths(attributionElement.getUserYOffsetSs())`; on read the offset is read
back **once** into `attributionElement.setUserYOffsetSs(...)`.

### Annotation mapping (`<direction>` inside a measure)

| MusicXML | Source |
|---|---|
| `<direction placement="above\|below">` | `annotation.getPlacement()` (`ABOVE`/`BELOW`) |
| `<direction-type><words halign="…" justify="…">text</words>` | `getAnnotation()`; halign/justify from `getXAlignment()` (0.0→left, 0.5→center, 1.0→right) |
| `relative-y` on `<words>` | `ssToTenths(getUserYOffsetSs())` |
| `default-y` on `<words>` | computed base — write-forward, ignored on read |

Annotations attach per note via `AnnotationAttachment`
(`note.findAttachment(AnnotationAttachment.class)`). **Writer:** emit the
`<direction>` immediately **before** the annotated note's `<note>`, in the existing
`writeLineDrivenMeasures` note-branch seam (order: `writeTempoDirection` →
`writeMetricModulationDirection` → `writeHairpinWedges` → **annotation** →
`writeNote`). **Reader discriminator (unambiguous):** a `<direction>` that is
words-only — no `<metronome>` (tempo/metric-mod always carry it), no `<wedge>`
(hairpins always carry it), no `<dynamics>` (Phase 3 emits those inside `<note>`,
never as directions) — and carries a `placement` attribute is an annotation. **Note
the current gap:** today a words-only direction routes its text to
`MetronomeResolver.setWords` and is then discarded at `endDirection` (no beat
token) — Phase 8 intercepts these (by the `placement`/no-`metronome` signal) into
an `Annotation` on the next note instead of letting the resolver drop them.

### Decomposition rationale

- **Phase 1 (shared DateUtils)** extracts the existing SongIO ISO-date logic into
  `songscribe.util.DateUtils` so SongIO and the MusicXML code share one
  implementation — mechanical refactor gated by SongIO's existing date tests →
  Sonnet.
- **Phase 2 (vocab)** is pure mechanical constant-adding → Sonnet.
- **Phase 3 (fonts plumbing)** is the one cross-cutting architectural change —
  widen `writeSong`/`read`/the harness to carry fonts while keeping every existing
  caller green via `DocumentFonts.defaultFonts()`. Isolated so it compiles and all
  Phase 1–6 tests still pass **before** any font is emitted → Opus (signature +
  harness reversibility).
- **Head (4 write / 5 read+round-trip)**, **Defaults (6)**, **Credits (7 write / 8
  read+round-trip)**, **Annotations (9)** are the vertical slices. Writer halves of
  the larger slices are mechanical (Sonnet); the reader halves are the reversible
  SAX-accumulator work (Opus), matching the Phase 6 split.
- **Phase 10** is the fully-populated round-trip + regression gate → isolated last.

### Key code touchpoints

Model (read-only unless noted):

- `dom/Song.java` — `getTitle()`:428, `getNumber()`:588, `getSubtitle()`:432,
  `getNumberedTitle()`:441, `getComposer()`:550, `getLyricist()`:554,
  `getLyricsSource()`:558, `isArrangement()`:562, `isUnofficialTranslation()`:546,
  `getPlace()`:460, `getYear()`:464/`getMonth()`:468/`getDay()`:472,
  `getWordsYear()`:476/`getWordsMonth()`:480/`getWordsDay()`:484,
  `getUnderLyrics()`:530, `getBanglaLyrics()`:534, `getTranslatedLyrics()`:538,
  `getFootnotes()`:542, `getAttributionElement()`:579, `SRI_CHINMOY`:74,
  `setMetadata(SongMetadata)`:677, `LyricsSource` enum (nested).
- `dom/SongMetadata.java` — the immutable record holding all head fields;
  reader rebuilds it and calls `setMetadata`.
- `dom/Attribution.java` extends `LineElement`; `getUserYOffsetSs()` /
  `setUserYOffsetSs()` at `LineElement.java:214/221`.
- `dom/Annotation.java` — `Placement { ABOVE, BELOW }`:26, `placement`:28,
  `getAnnotation()`:50, `getXAlignment()`:58, `getUserYOffsetSs()`:74.
- `dom/AnnotationAttachment.java`; `note.findAttachment(AnnotationAttachment.class)`;
  `Line.isAnnotation()`:1483.
- `font/DocumentFonts` (concrete, `implements DocumentFontsHolder`,
  `defaultFonts()`:93, value `equals`:114) / `font/DocumentFontsHolder` (interface);
  `getFont(FontKey)` → `java.awt.Font`. `FontKey`: TITLE, SUBTITLE, LYRICS,
  ATTRIBUTION, SUB_ATTRIBUTION, ANNOTATION, BANGLA, FOOTNOTE.
- `Version.PUBLIC_VERSION` (`build/generated-sources/songscribe/Version.java`,
  String).

Legacy IO to refactor / mirror (`io/`):

- `SongIO.java` — `toIsoDate`:113 (private static, **move to DateUtils**),
  `parseLyricsDate`:977 (private instance on `DocumentReader`, **delegate to
  DateUtils**), constants `MAX_MONTH`:273 / `MAX_DAY`:274 / `ISO_DATE_PATTERN`:277
  (**move to DateUtils**; also referenced at :624/:632/:947). `writeSong` emission
  (129–…) and `DocumentReader.endElement12` (~574–689) are the head reference shapes.
- `AnnotationIO.java` — `writeAnnotation` (47–66) / `AnnotationReader` (68–159):
  `XML_NAME`/`XML_ALIGNMENT`/`XML_PLACEMENT`/`XML_USER_Y_OFFSET`.
- `ViewIO.java` — `FONT_TAGS` / `FontKey` enumeration for the fonts.
- `XML.java` — `writeBeginTag`/`writeEmptyTag`/`writeValue`/`writeEndTag`/
  `escapeXML`/`indent`/`dedent`.

MusicXML IO (`io/musicxml/`, edit):

- `MusicXmlWriter.java` — `writeSong`:153 (insert head before `<part-list>`);
  existing `ssToTenths`:1557 / `formatTenths`:1547 / `TENTHS_PER_STAFF_SPACE`
  (`MusicXmlTags`:41) — **already present, reuse; do not recreate**. Measure/note
  loop (`writeLineDrivenMeasures`:201, `writeNote`:420) for annotation directions;
  existing `writeTempoDirection`/`writeHairpinWedges` mark the direction seam.
- `MusicXmlReader.java` — `Where` enum:1088, `startElement`:199 / `endElement`:488
  `case SCORE_PARTWISE`; XXE hardening at :59–74 (`disallow-doctype-decl`); the
  `DIRECTION`/`DIRECTION_TYPE`/`WORDS`/`METRONOME` subtree and `MetronomeResolver`
  binding at `finishNote` (~:976).
- `MusicXmlTags.java` — append head/defaults/credit/miscellaneous/direction-words
  constants.
- `MusicXmlSchemaValidator.validate(String)` (test scope; does not disallow DOCTYPE,
  so the reader is the binding constraint).
- Test support: `MusicXmlRoundTripSupport` (`writeToString`/`parse`/`roundTrip`/
  `buildSong`), `MusicXmlWriterOutputTest`, per-feature round-trip test pattern
  (`MusicXmlTempoRoundTripTest`, `MusicXmlLyricRoundTripTest`).

## Dependencies

- **Rebase:** none — `musicxml-phase-7` branches from current `develop`.
- **Prerequisite satisfied:** the annotation `Placement` enum refactor is already
  committed (`e5219b50`); no model change needed here.
- **Internal ordering:** see each phase's `BlockedBy` field below (the parent
  dashboard tracks dependencies in its Phase Dependencies graph, not a column).
- **Must not regress:** the Phase 1–6 round-trip suites; SongScribe's existing
  `.mssw` lyrics-date read/write tests (Phase 1 refactors their shared code);
  writer output must keep validating against `docs/musicxml-4.0-schema/`; the
  fonts-plumbing signature change (Phase 3) must leave every existing
  writer/reader/harness caller green.

## Plan

| Phase | Description | Status | Recommended model |
|-------|-------------|--------|-------------------|
| 1 | [Shared DateUtils](#-phase-1-shared-dateutils) | ✅ Complete | Sonnet 4.6, low |
| 2 | [MusicXML Vocabulary](#-phase-2-musicxml-vocabulary) | ✅ Complete | Sonnet 4.6, low |
| 3 | [Fonts Plumbing](#-phase-3-fonts-plumbing) | ✅ Complete | Opus 4.8, medium |
| 4 | [Writer: Head Metadata](#-phase-4-writer-head-metadata) | ✅ Complete | Sonnet 4.6, low |
| 5 | [Reader: Head Metadata + Round-Trip](#-phase-5-reader-head-metadata--round-trip) | ✅ Complete | Opus 4.8, medium |
| 6 | [Defaults: Writer + Reader + Round-Trip](#-phase-6-defaults-writer--reader--round-trip) | ✅ Complete | Opus 4.8, medium |
| 7 | [Writer: Credits](#-phase-7-writer-credits) | ✅ Complete | Sonnet 4.6, medium |
| 8 | [Reader: Credits + Round-Trip](#-phase-8-reader-credits--round-trip) | ✅ Complete | Opus 4.8, medium |
| 9 | [Annotations: Writer + Reader + Round-Trip](#-phase-9-annotations-writer--reader--round-trip) | ✅ Complete | Opus 4.8, medium |
| 10 | [Full Round-Trip + Regression](#-phase-10-full-round-trip--regression) | ✅ Complete | Sonnet 4.6, medium |

## ✅ Phase 1: Shared DateUtils

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Sonnet 4.6, low — extract-and-delegate refactor of existing, test-covered SongIO date logic into a shared util; no new behavior.

### Tasks
1. Create `songscribe.util.DateUtils` (`final`, private constructor, standard file
   header): move `MAX_MONTH` / `MAX_DAY` / `ISO_DATE_PATTERN` (from
   `SongIO`/`DocumentReader`) as `public static final`; move `toIsoDate(String
   year, int month, int day)` verbatim (public static); add
   `public static @Nullable DateParts parseIsoDate(String str)` extracted from
   `parseLyricsDate`'s parse + range-validate core — the `ISO_DATE_PATTERN`
   (2-digit month/day only) plus **both bounds** (`1 ≤ month ≤ MAX_MONTH`,
   `1 ≤ day ≤ MAX_DAY`); returns `null` on malformed or out-of-range input (no
   logging, no side effects). This must reproduce **all nine** existing
   `LyricsDateIO` rejections, including the lower-bound cases `1984-00` (month 0),
   `1984-06-00` (day 0), and the single-digit `1984-6` (resolved 5A). Add
   `public record DateParts(String year, int month, int day)`.
2. Refactor `SongIO`: delete its private `toIsoDate` and call
   `DateUtils.toIsoDate(...)` at `SongIO.java:185`; replace `parseLyricsDate`'s body
   to call `DateUtils.parseIsoDate(str)` — on `null` log the existing warning and
   set `invalidLyricsDate = str`, otherwise assign `wordsYear/wordsMonth/wordsDay`
   from the `DateParts` (the per-field month-vs-day warning granularity collapses to
   one "malformed lyricsDate" warning — acceptable). Replace SongIO's remaining
   `MAX_MONTH`/`MAX_DAY` references (:624, :632, :947) with `DateUtils.MAX_MONTH` /
   `DateUtils.MAX_DAY` and delete the private copies + `ISO_DATE_PATTERN`.
3. Tests: add `DateUtilsTest` — `toIsoDate` full/partial/absent forms; `parseIsoDate`
   valid `YYYY`/`YYYY-MM`/`YYYY-MM-DD`, and the nine existing `LyricsDateIO`
   malformed/out-of-range inputs (`1984-13`, `1984--6`, `1984-6-`, `1984-06-27-01`,
   `1984-XX`, `1984-06-32`, `1984-00`, `1984-06-00`, `1984-6`) → `null` (resolved 5A),
   and the `toIsoDate`∘`parseIsoDate` round-trip. Identify and re-run the existing
   SongIO lyrics-date tests to confirm no regression (they assert model state +
   `invalidLyricsDate`, not log strings, so collapsing the three warnings to one is
   safe — but the `invalidLyricsDate = str` assignment on the `null` path must be
   preserved in Phase 1 task 2).
4. Gate: `./scripts/compile.sh` (SUCCESS); `./scripts/test.sh DateUtilsTest` plus the
   existing SongIO lyrics-date test class.

## ✅ Phase 2: MusicXML Vocabulary

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Sonnet 4.6, low — appending named constants mirroring `MusicXmlTags`' grouping.

### Tasks
1. In `MusicXmlTags`, add **head/identification** element constants: `MOVEMENT_TITLE`,
   `MOVEMENT_NUMBER`, `IDENTIFICATION`, `CREATOR`, `RIGHTS`, `ENCODING`, `SOFTWARE`,
   `ENCODING_DATE`, `SUPPORTS`; **defaults** constants: `DEFAULTS`, `SCALING`, `MILLIMETERS`,
   `TENTHS`, `PAGE_LAYOUT`, `PAGE_HEIGHT`, `PAGE_WIDTH`, `STAFF_LAYOUT`,
   `STAFF_DISTANCE`, `MUSIC_FONT`, `WORD_FONT`, `LYRIC_FONT`, `LYRIC_LANGUAGE`;
   **credit** constants: `CREDIT`, `CREDIT_TYPE`, `CREDIT_WORDS`; **miscellaneous**
   constants: `MISCELLANEOUS`, `MISCELLANEOUS_FIELD`.
2. Add attribute constants (reuse existing `ATTR_TYPE`, `ATTR_DEFAULT_X`,
   `ATTR_DEFAULT_Y`, `ATTR_RELATIVE_Y`, `ATTR_NUMBER`): `ATTR_NAME`,
   `ATTR_FONT_FAMILY`, `ATTR_FONT_SIZE`, `ATTR_FONT_WEIGHT`, `ATTR_FONT_STYLE`,
   `ATTR_JUSTIFY`, `ATTR_HALIGN`, `ATTR_XML_LANG`, `ATTR_PAGE`, `ATTR_PLACEMENT`,
   `ATTR_ELEMENT` (reuse the existing `YES` value constant for `type="yes"`).
3. Add value/literal constants: `MUSIC_FONT_FAMILY = "Bravura"`,
   `MUSIC_FONT_SIZE = "32"`, `SCALING_MILLIMETERS = 7.0`, `SCALING_TENTHS = "40"`,
   `PAGE_HEIGHT_TENTHS` (a fixed page-height in tenths — write-forward, ignored on
   read), `LYRIC_LANGUAGE_DEFAULT = "en"`, `COPYRIGHT` (copyright format string,
   mirror the old draft's value), the `creator`/`credit` type tokens
   (`CREATOR_COMPOSER` = `"composer"`, `CREATOR_LYRICIST` = `"lyricist"`,
   `CREATOR_ARRANGER` = `"arranger"`; `CREDIT_TITLE`, `CREDIT_SUBTITLE`,
   `CREDIT_COMPOSER`, `CREDIT_LYRICIST`, `CREDIT_ARRANGER`, `CREDIT_COMPOSITION_DATE`
   = `"composition date"`, `CREDIT_LYRICS_DATE` = `"lyrics date"`, `CREDIT_RIGHTS`,
   `CREDIT_PLACE`, `CREDIT_UNDERLYRICS`, `CREDIT_BANGLA_LYRICS` = `"bangla-lyrics"`,
   `CREDIT_TRANSLATION` = `"translation"`, `CREDIT_FOOTNOTES`), `JUSTIFY_CENTER`,
   `HALIGN_LEFT`/`HALIGN_CENTER`/`HALIGN_RIGHT`, `WEIGHT_BOLD`/`WEIGHT_NORMAL`,
   `STYLE_ITALIC`/`STYLE_NORMAL`, `PLACEMENT_ABOVE`/`PLACEMENT_BELOW`, the
   `<supports>` element tokens (`SUPPORTS_ACCIDENTAL` = `"accidental"`,
   `SUPPORTS_BEAM` = `"beam"`, `SUPPORTS_STEM` = `"stem"`), and the
   misc-field names (`MISC_COMPOSITION_DATE` = `"composition-date"`,
   `MISC_LYRICS_DATE` = `"lyrics-date"`, `MISC_COMPOSITION_PLACE` =
   `"composition-place"`, `MISC_LYRICS_SOURCE` = `"lyrics-source"`,
   `MISC_UNOFFICIAL_TRANSLATION` = `"unofficial-translation"`,
   `MISC_SUB_ATTRIBUTION_FONT` = `"sub-attribution-font"`,
   `MISC_SUB_ATTRIBUTION_FONT_SIZE` = `"sub-attribution-font-size"`,
   `MISC_ROW_HEIGHT_ADJUSTMENT` = `"row-height-adjustment"`).
4. Gate: `./scripts/compile.sh` (SUCCESS).

## ✅ Phase 3: Fonts Plumbing

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Opus 4.8, medium — the one cross-cutting signature change; must keep every existing writer/reader/harness caller green with zero behavior change before any font is emitted.

### Tasks
1. Widen `MusicXmlWriter.writeSong` to accept a `DocumentFontsHolder` parameter
   (mirroring legacy `SongIO.writeSong(Song, DocumentFontsHolder, PrintWriter)`) plus
   an injectable `Clock` (default `Clock.systemDefaultZone()`) so the write-forward
   `<rights>` year, `<encoding-date>`, and copyright credit are deterministic under
   test (resolved 8B). `MusicXmlReader.read` returns a small immutable result record
   **`MusicXmlReadResult(Song song, DocumentFonts fonts)`** (resolved 3A — `read` is
   `static`, so a return record is the only clean shape; **no** stateful
   `getDocumentFonts()` accessor), defaulting `fonts` to `DocumentFonts.defaultFonts()`
   when `<defaults>` fonts are absent (mirror
   `SongIO.DocumentReader.getDocumentFonts()`).
2. Thread fonts through `MusicXmlRoundTripSupport`: add font-aware
   `writeToString(Song, DocumentFontsHolder)` / `parse` / `roundTrip(Song,
   DocumentFontsHolder)` overloads returning the `MusicXmlReadResult` (`Song` +
   `DocumentFonts`); keep
   the existing `Song`-only signatures delegating with `DocumentFonts.defaultFonts()`
   so Phase 1–6 tests compile unchanged. Fonts compare by value via
   `DocumentFonts.equals` (`assertEquals`).
3. Update the existing `writeSong`/`read` call sites (entry points and any
   non-harness test callers) to the new signatures with default fonts. Emit
   **nothing** new yet — this phase only widens the seam.
4. Gate: `./scripts/compile.sh` (SUCCESS); `./scripts/test.sh unit` — the full
   Phase 1–6 suite stays green (no output change).

## ✅ Phase 4: Writer: Head Metadata

**Status:** Complete  <br>
**BlockedBy:** 1, 2  <br>
**Recommended model/effort:** Sonnet 4.6, low — mechanical tag emission mirroring existing `writeSong` helpers, inserted at a single seam; values come straight from the mapping tables.

### Tasks
1. In `writeSong`, after the `<score-partwise>` open (and `indent()`) and before
   `<part-list>`, call new private helpers in schema order: `writeMovementInfo` →
   `writeIdentification`. Leave the existing `<?xml version="1.0"
   encoding="UTF-8"?>` declaration unchanged — **no DOCTYPE, no `standalone`**.
2. `writeMovementInfo`: `<movement-number>` (only when `getNumber()` non-empty) then
   `<movement-title>` (`getTitle()`).
3. `writeIdentification`: `<identification>` → `<creator type="composer">` /
   `type="lyricist">` from the discrete getters; `<creator type="arranger">` =
   `SRI_CHINMOY` only when `isArrangement()`; `<rights>` from
   `String.format(COPYRIGHT, currentYear)` where `currentYear` and the
   `<encoding-date>` derive from the injected `Clock` (resolved 8B — **not** a bare
   `LocalDate.now()`), so writer-output tests pin a fixed date; `<encoding>` →
   `<software>` = `"SongScribe " + Version.PUBLIC_VERSION` + `<encoding-date>`
   (`LocalDate.now(clock)`, ISO_LOCAL_DATE), then the always-emitted fixed
   `<supports>` set —
   `<supports element="accidental" type="yes"/>`,
   `<supports element="beam" type="yes"/>`,
   `<supports element="stem" type="yes"/>` (write-forward).
4. Close `<identification>` with a `<miscellaneous>` block per the misc-field
   mapping (composition-date, lyrics-date, composition-place, lyrics-source,
   unofficial-translation) using `DateUtils.toIsoDate` for the two dates.
   **lyrics-date (resolved 1B):** a writer-side metadata-prep step normalizes the
   words-date to *empty* when it equals the composition-date, then emits `lyrics-date`
   only when the (normalized) words-date is non-empty — mirroring the legacy rule at
   `SongIO.java:185-189` (a bare `equal → omit` on populated fields is model-lossy:
   `SongMetadata` has component-wise `equals`, so a populated words-date equal to
   composition would reload as empty and break round-trip). Normalization lives in
   writer-prep **only, not the model** (keeps the model read-only). Build the
   misc-field list into a collection emitted in one pass so Phase 6 can append its
   fields without reopening the block. **Defer `sub-attribution-font*` and
   `row-height-adjustment` to Phase 6** (need fonts / defaults). Omit the whole
   `<miscellaneous>` when no field applies.
5. Gate: `./scripts/compile.sh` (SUCCESS).

## ✅ Phase 5: Reader: Head Metadata + Round-Trip

**Status:** Complete  <br>
**BlockedBy:** 4  <br>
**Recommended model/effort:** Opus 4.8, medium — reversible SAX accumulation into the immutable `SongMetadata` record; subtree consumption + ISO-date inverse.

### Tasks
1. Add `Where` states under `SCORE_PARTWISE`: `MOVEMENT_TITLE`, `MOVEMENT_NUMBER`,
   `IDENTIFICATION`, `CREATOR`, `RIGHTS`, `ENCODING`, `SOFTWARE`, `ENCODING_DATE`,
   `MISCELLANEOUS`, `MISCELLANEOUS_FIELD`, with matching `endElement` cases
   returning to their parent. Dispatch is single-level, so each container needs its
   own start+end cases to consume its subtree cleanly and skip unknown leaves.
2. Accumulate head fields into a mutable scratch (title, number, composer,
   lyricist, arranger-seen→`arrangement`, and each misc-field). `<creator>` routes
   by its `type` attribute. Ignore write-forward-only elements on read (`<rights>`,
   `<software>`, `<encoding-date>`, `<supports>`).
3. Parse misc-fields back: `composition-date`/`lyrics-date` via
   `DateUtils.parseIsoDate` into year/month/day + words y/m/d (null → treat as
   absent); `composition-place` → place; `lyrics-source` → an **enum-or-throw helper**
   wrapping `LyricsSource.valueOf` that raises `SAXException("Corrupt document:
   malformed <lyrics-source>…")` on an unknown token (resolved 7A — matches the
   reader's `parseIntOrThrow`/`parseDoubleOrThrow` fail-hard convention; the reader
   has no raw `valueOf` today); `unofficial-translation` → boolean. **Do not** rebuild
   metadata at `</identification>`; accumulate every field (title, number, composer,
   lyricist, arrangement, dates, place, source, unofficial-translation, **and the
   subtitle** from the Phase 8 credit) into reader-local scratch and build
   `SongMetadata` **once** at the terminal `SCORE_PARTWISE` fix-up (resolved: mirror
   `SongIO.DocumentReader` → `Song.loadFrom`, which assembles the record in one
   all-args construction). This dissolves the `</identification>`-before-`</credit>`
   ordering hazard and needs **no** `setSubtitle`/`withSubtitle` mutator (neither
   exists).
4. Add `MusicXmlHeaderRoundTripTest` (extends `MusicXmlRoundTripSupport`): a song
   with title, number, distinct composer/lyricist, `isArrangement()=true`,
   `lyricsSource`, full + partial composition/lyrics dates, place, and
   unofficial-translation round-trips with all metadata equal; empty number omits
   `<movement-number>` and reloads as empty; a `headerRoundTripIsSchemaValid` case
   runs `MusicXmlSchemaValidator.validate`.
5. Gate: `./scripts/compile.sh`; `./scripts/test.sh MusicXmlHeaderRoundTripTest`.

## ✅ Phase 6: Defaults: Writer + Reader + Round-Trip

**Status:** Complete  <br>
**BlockedBy:** 3, 4  <br>
**Recommended model/effort:** Opus 4.8, medium — fonts round-trip (write + parse) plus the line-width / row-height delta homes and their inverses.

### Tasks
1. `writeDefaults` (after `writeIdentification`, before credits): `<scaling>`
   (`<millimeters>7</millimeters><tenths>40</tenths>`); `<page-layout>` with
   `<page-height>` = `PAGE_HEIGHT_TENTHS` (fixed) and `<page-width>` =
   `formatTenths(ssToTenths(lineWidthSs))`;
   `<staff-layout><staff-distance>0</staff-distance></staff-layout>`.
2. Append the deferred misc-fields to the Phase 4 `<miscellaneous>` block
   (coordinate so all misc-fields live in one block): `sub-attribution-font` /
   `sub-attribution-font-size` from `fonts.getFont(FontKey.SUB_ATTRIBUTION)`
   (family / size), and `row-height-adjustment` = `rowHeightAdjustmentSs` when
   non-zero.
3. Emit fonts: `<music-font font-family="Bravura" font-size="32"/>`; `<word-font>`
   from `fonts.getFont(FontKey.ANNOTATION)`; `<lyric-font>` from
   `fonts.getFont(FontKey.LYRICS)` (`getFamily()` / `getSize()`);
   `<lyric-language xml:lang="en"/>`.
4. Reader: `DEFAULTS` subtree states; parse `<page-width>` → `lineWidthSs`
   (`tenthsToSs`) via `parseDoubleOrThrow`, the `row-height-adjustment` misc-field →
   `rowHeightAdjustmentSs`, and `<word-font>`/`<lyric-font>`/sub-attribution
   misc-fields (font-size via `parseDoubleOrThrow`/`parseIntOrThrow`, resolved 7A)
   back into the `DocumentFonts` result. Ignore
   `<scaling>`/`<music-font>`/`<page-height>` on read (fixed/write-forward).
5. Add `MusicXmlDefaultsRoundTripTest`: line width, row-height adjustment, and the
   annotation/lyric/sub-attribution fonts survive round-trip (`DocumentFonts.equals`);
   a document with **no `<defaults>` fonts reads back `DocumentFonts.defaultFonts()`**
   (resolved 3A); a **hand-edited write-forward value** (`<page-height>` /
   `<scaling>`) leaves the reloaded model unchanged (resolved 3A — proves
   ignored-on-read); a `defaultsRoundTripIsSchemaValid` case.
6. Gate: `./scripts/compile.sh`; `./scripts/test.sh MusicXmlDefaultsRoundTripTest`.

## ✅ Phase 7: Writer: Credits

**Status:** Complete  <br>
**BlockedBy:** 2, 3  <br>
**Recommended model/effort:** Sonnet 4.6, medium — mechanical `<credit>` emission from the credit table; positions are best-effort/omitted (write-forward), so no layout coupling.

### Tasks
1. `writeCredits` (after `writeDefaults`, before `<part-list>`) with a private
   `writeCredit(creditType, fontKey, text, justify, relativeYSs, xmlLang)` helper:
   `<credit>` (omit `page` when 1) → `<credit-type>` → `<credit-words>` with
   `font-family`/`font-size`/`font-weight`/`font-style` from the role's
   `fonts.getFont(fontKey)` (weight/style via `isBold()`/`isItalic()`), optional
   `justify`/`xml:lang`/`relative-y`. Emit a credit only when its text is non-blank.
   `default-x`/`default-y` omitted (write-forward, deferred).
2. Emit `title` (`getNumberedTitle()`, `TITLE`, `justify="center"`) and `subtitle`
   (`getSubtitle()`, `SUBTITLE`) credits.
3. Emit attribution-role credits (`composer`, `lyricist`, `arranger` when
   `isArrangement()`, `composition date`, `lyrics date` when distinct, `rights`,
   `place`), each `ATTRIBUTION`, each carrying `relative-y` =
   `ssToTenths(attributionElement.getUserYOffsetSs())`.
4. Emit score-below credits (`underlyrics` `LYRICS`, `bangla-lyrics` `BANGLA` +
   `xml:lang`, `translation` `LYRICS`, `footnotes` `FOOTNOTE`), page 1.
5. Gate: `./scripts/compile.sh`; add raw-output assertions to
   `MusicXmlWriterOutputTest` (title/subtitle/attribution/score-below shapes, fonts
   present, blank fields emit no credit) + a `creditsWriterOutputIsSchemaValid` case;
   `./scripts/test.sh MusicXmlWriterOutputTest`.

## ✅ Phase 8: Reader: Credits + Round-Trip

**Status:** Complete  <br>
**BlockedBy:** 7  <br>
**Recommended model/effort:** Opus 4.8, medium — the reversible slice: read the canonical credits back, re-derive the display-only ones, recover the attribution offset once.

### Tasks
1. Add `CREDIT` / `CREDIT_TYPE` / `CREDIT_WORDS` `Where` states under
   `SCORE_PARTWISE`; accumulate `<credit-type>` + `<credit-words>` text + its
   attributes per credit (own start+end cases for clean subtree consumption).
   **P-1:** credit-words / misc-field text must accumulate across SAX `characters()`
   chunks — append to the existing `value` `StringBuilder` and read only at the end
   element (long footnotes/underlyrics arrive in multiple chunks and would otherwise
   truncate).
2. On each `</credit>`, dispatch by credit-type via the single routing table (6B):
   **canonical** → subtitle accumulates into the head scratch (assembled with the
   rest of `SongMetadata` at the terminal `SCORE_PARTWISE`, per Phase 5 — **no**
   `setSubtitle`, which does not exist); the four score-below blocks →
   `setUnderLyrics`/`setBanglaLyrics`/`setTranslatedLyrics`/`setFootnotes` (standalone
   `Song` fields); **display-only** (title, composer, lyricist, arranger,
   composition/lyrics date, rights, place) → ignore text (re-derived from head). Add
   an inline copy of the canonical/display-only/write-forward diagram (see
   Implementation Approach → Data-flow contract) as a comment above this dispatch
   method (resolved 4A).
3. Read the attribution `relative-y` back **once** into
   `attributionElement.setUserYOffsetSs(tenthsToSs(...))` (first attribution credit
   that carries it).
4. Add `MusicXmlCreditRoundTripTest`: subtitle + all four score-below blocks reload
   verbatim; a populated attribution Y-offset survives; title/attribution credits do
   **not** corrupt head metadata (canonical head still wins); a blank subtitle
   reloads blank; `creditRoundTripIsSchemaValid`.
5. Gate: `./scripts/compile.sh`; `./scripts/test.sh MusicXmlCreditRoundTripTest`.

## ✅ Phase 9: Annotations: Writer + Reader + Round-Trip

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Recommended model/effort:** Opus 4.8, medium — measure-level `<direction>` emission plus the reader discriminator separating annotation directions from tempo/wedge directions, and intercepting the currently-dropped words-only direction.

### Tasks
1. Writer: in the `writeLineDrivenMeasures` note-branch seam, for a note carrying an
   `AnnotationAttachment` emit a `<direction placement="above|below">` immediately
   before its `<note>` (after the tempo/wedge directions):
   `<direction-type><words halign="…" justify="…" relative-y="…">text</words>` from
   `getAnnotation()` / `getXAlignment()` / `getUserYOffsetSs()`. `default-y` omitted
   (write-forward).
2. Reader: read the `placement` attribute on `<direction>` start. **Discriminator
   (resolved 2A — single rule, not "and/or"):** a `<direction>` is an annotation iff
   it carries **no `<metronome>` and has a `placement` attribute**. (Verify the
   writer's tempo/metric-mod directions never emit `placement`, so the signal is
   unambiguous.) Route such a direction's `<words>` text + halign + relative-y into an
   annotation accumulator instead of `MetronomeResolver` (which currently drops
   words-only directions), and build an `Annotation`
   (`setAnnotation`/`setXAlignment`/`setPlacement`/`setUserYOffsetSs`) into an
   `AnnotationAttachment` on the next note. Tempo directions (carry `<metronome>`)
   still route to `MetronomeResolver` unchanged; a words-only direction with **no**
   `placement` is left to the resolver (dropped), never promoted to a phantom
   annotation.
3. Map `xAlignment` ↔ halign/justify (0.0↔left, 0.5↔center, 1.0↔right) — a small
   symmetric lookup in both directions.
4. Add `MusicXmlAnnotationRoundTripTest`: an ABOVE and a BELOW annotation with
   distinct text, each of left/center/right alignment, and a non-zero
   `userYOffsetSs`, all round-trip; a note carrying **both** a tempo direction and an
   annotation keeps both distinct (tempo still parsed, annotation attached);
   **two annotations on consecutive notes round-trip in order and stay attached to
   the correct notes** (resolved 2A — exercises attach-to-next-note); **a words-only
   `<direction>` with no `placement` (and no `<metronome>`) is NOT promoted to an
   annotation** (resolved 2A — negative discriminator case);
   `annotationRoundTripIsSchemaValid`.
5. Gate: `./scripts/compile.sh`; `./scripts/test.sh MusicXmlAnnotationRoundTripTest`.

## ✅ Phase 10: Full Round-Trip + Regression

**Status:** Complete  <br>
**BlockedBy:** 5, 6, 8, 9  <br>
**Recommended model/effort:** Sonnet 4.6, medium — one integration test over a fully-populated song plus the cumulative regression gate.

### Tasks
1. Add `MusicXmlDocumentRoundTripTest`: build a song populated across **every**
   Phase 7 area — full head metadata, distinct dates, all misc-fields, custom
   fonts, line width + row-height adjustment, title/subtitle/attribution/score-below
   credits with an attribution offset, and both ABOVE/BELOW annotations — plus
   notes/lyrics/tempo/key from earlier phases. Assert full equality after
   `roundTrip` (including `DocumentFonts.equals`), that every residual `<misc-field>`
   reloads verbatim, and schema validity of the whole document.
2. Assert the display-only credits (title, attribution roles) re-derive from head
   metadata rather than being read from the credit text (mutating a credit's text in
   hand-written XML must not change the reloaded model).
3. Gate: `./scripts/compile.sh`; `./scripts/test.sh unit` — the full suite is green
   with no Phase 1–6 regression.

## Verification (whole sub-plan)

- `./scripts/compile.sh` → SUCCESS after every phase.
- Writer output for head + `<defaults>` + credits + annotations validates against
  `docs/musicxml-4.0-schema/` via explicit `MusicXmlSchemaValidator` assertions.
- `./scripts/test.sh unit` green, including `DateUtilsTest`, and the header /
  defaults / credit / annotation / full-document round-trip suites, with no
  Phase 1–6 regression and no regression in SongScribe's existing `.mssw`
  lyrics-date tests (Phase 1 refactor).
- Round-trips losslessly: all head metadata and misc-fields; the three document
  fonts; line width and row-height adjustment; the subtitle and all four
  score-below blocks (verbatim); the attribution Y-offset; and ABOVE/BELOW
  annotations with alignment + offset. Display-only credits (title, attribution
  roles) re-derive from head metadata and never corrupt it on read.
- Write-forward-only data (`<rights>`, `<software>`, `<encoding-date>`,
  `<supports>`, `<page-height>`, credit `default-x`/`default-y`) is emitted
  schema-valid and ignored on read.
