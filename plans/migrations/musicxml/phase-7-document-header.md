# Sub-plan: Phase 7 — Document Header & Defaults

**Type:** Sub-plan  <br>
**Parent:** plans/migrations/musicxml/musicxml.md → Phase 7  <br>
**Created:** 2026-05-30  <br>
**Status:** Pending  <br>
**BlockedBy:** —

---

## Purpose

Emit everything that precedes `<part-list>` in every exported MusicXML document:
the `<movement-title>`, `<movement-number>`, `<identification>`, `<defaults>`,
and `<credit>` blocks, plus the staff-space ↔ tenths unit-conversion utilities
those blocks depend on, through the existing `XML`/`Indent` emission helpers.

**Delivered:**
- A `MusicXmlUtils` class with `ssToTenths`, `tenthsToSs`, the
  `TENTHS_PER_STAFF_SPACE` constant, and a tenths/decimal formatter.
- New `MusicXmlTags` vocabulary for the header, defaults, and credit
  elements/attributes.
- `MusicXmlWriter.writeSong` emitting the title, identification, defaults, and
  credit blocks, with its signature widened to accept the data those blocks need.
- Updated call sites and tests.

**Credits in scope (see musicxml.md § "Credits"):** the on-page display of the
title and every attribution role (composer, lyricist, arranger, composition date,
lyrics date, rights, place) as first-page `<credit>` elements, and the score-below
text blocks (underlyrics, Bangla lyrics, translated lyrics, footnotes) as
last-page `<credit page="N">` elements. The `TITLE` / `ATTRIBUTION` / `BANGLA` /
`FOOTNOTE` fonts ride in the `<credit-words>` attributes, so there are **no**
`font-...` misc-fields.

**Explicitly NOT in scope (deferred):**
- Any production export action / menu wiring. There is no production caller of
  `writeSong` yet; only tests call it. A future phase wires the UI and supplies
  `SongLayoutMetrics` from `ScoreView.getSongLayoutMetrics()`.
- `<part-name>` content and anything inside `<part>` (notes/measures are owned by
  other phases).

**Credit positions come from the rendered component geometry** (not from
`SongLayoutMetrics`). The header items are laid out as Swing components inside
`MainPanel`, so each item's on-page position is read from its rendered bounds —
see § "Credit positions" for the exact source per item and the px → tenths /
page-origin conversion. Two facts constrain this:

- **Single-page model.** SongScribe has no pagination — `ScoreView.updatePageLayout`
  (`ScoreView.java:900`) grows one scrollable page to fit all content; `PageModel`
  is one logical page. So every credit is on **page 1**, and "last page" = page 1
  today. The `page="N"` mechanism is emitted for forward-compatibility but is
  always `1` until a multi-page layout exists.
- **Attribution is still a single string.** `Song.getAttribution()` is one
  multi-line string with only a block-level `attributionStartYSs`
  (`Song.java:513`, in ss, historically right-aligned); it is not yet decomposed
  into composer/lyricist/arranger/… and is **not currently rendered**. Per-role
  attribution credits and their positions therefore cannot be emitted until the
  attribution rework lands — which is exactly what Phase 7 is **blocked** on.

## Implementation Approach

Insert the header blocks into `writeSong` between the `<score-partwise>` open tag
and the existing `<part-list>` emission, in MusicXML's required order:
`movement-title`, `movement-number`, `identification`, `defaults`, then the
`credit` elements, then the already-implemented `part-list`/`part`. (`<credit>`
must follow `<defaults>` and precede `<part-list>` per the schema.)

### Value mapping (element → source)

| Element | Source |
|---|---|
| `movement-title` | `song.getTitle()` |
| `movement-number` | `song.getNumber()` — omit element entirely if empty |
| `creator[@type="composer"]` | TBD — API not yet defined; emit empty element for now |
| `creator[@type="lyricist"]` | TBD — API not yet defined; emit empty element for now |
| `creator[@type="arranger"]` | TBD — API not yet defined; emit empty element for now |
| `rights` (copyright) | `String.format(COPYRIGHT, Year.now())` |
| `software` | `songscribe.Version.PUBLIC_VERSION` |
| `encoding-date` | current date (`java.time.LocalDate`, ISO_LOCAL_DATE) |
| `system-distance` | `formatTenths(ssToTenths(metrics.totalLineHeightSs() − StaffExtents.STAFF_HEIGHT_SS))` |
| `top-system-distance` | `formatTenths(ssToTenths(metrics.maxAboveStaffSs()))` |
| `page-height`, `page-width`, margins | derived from `PageModel` |
| `word-font@font-family` | `fonts.getFont(FontKey.ANNOTATION).getFamily()` |
| `word-font@font-size` | same font `.getSize()` |
| `lyric-font@font-family` | `fonts.getFont(FontKey.LYRICS).getFamily()` |
| `lyric-font@font-size` | same font `.getSize()` |
| `lyric-language@xml:lang` | hardcoded `"en"` |
| `<misc-field name="composition-date">` | ISO 8601 string assembled from `song` year/month/day (exact getters TBD; gate on `month > 0` / `day > 0` for partial forms: `"1987"`, `"1987-12"`, `"1987-12-01"`); omit element if year is absent |
| `<misc-field name="lyrics-date">` | same ISO 8601 assembly from lyrics date fields (exact getters TBD); omit entirely when equal to or absent from composition date |
| `<misc-field name="place">` | `XML_PLACE` field on song (exact getter TBD); omit if blank |
| `<misc-field name="unofficial-translation">` | `XML_UNOFFICIAL_TRANSLATION` flag on song; omit if false |

The score-below text blocks are **not** misc-fields — they are emitted as
last-page credits (see the credit table below).

### Credit mapping (element → source)

Each credit is emitted only when its source field is non-blank. `<credit-words>`
carries the font (`font-family`, `font-size`, `font-weight`), justification, and
position; the text is the rendered display string. See "Open design items" for
where positions and the last-page index come from.

| `<credit-type>` | Display text source | Font (`FontKey`) | Page |
|---|---|---|---|
| `title` | `song.getNumberedTitle()` | `TITLE` | 1 |
| `composer` | composer field (API TBD) | `ATTRIBUTION` | 1 |
| `lyricist` | lyricist field (API TBD) | `ATTRIBUTION` | 1 |
| `arranger` | arranger field (API TBD) | `ATTRIBUTION` | 1 |
| `composition date` | formatted date string (from y/m/d) | `ATTRIBUTION` | 1 |
| `lyrics date` | formatted lyrics-date string; omit when equal to composition date | `ATTRIBUTION` | 1 |
| `rights` | `String.format(COPYRIGHT, Year.now())` | `ATTRIBUTION` | 1 |
| `place` | `XML_PLACE` field | `ATTRIBUTION` | 1 |
| `underlyrics` | `XML_UNDERLYRICS` field | `LYRICS` | 1 |
| `bangla-lyrics` | `XML_BANGLA_LYRICS` field (+ `xml:lang` on `<credit-words>`) | `BANGLA` | 1 |
| `translation` | `XML_TRANSLATED_LYRICS` field | `LYRICS` | 1 |
| `footnotes` | `XML_FOOTNOTES` field | `FOOTNOTE` | 1 |

(All credits are page 1 — SongScribe renders a single page; see § "Credit positions".)

Static (constant for every document): `<scaling>` `millimeters=7` `tenths=40`;
`<system-layout>/<system-margins>` left/right `0`; `<staff-layout>/<staff-distance>`
`0`; `<music-font font-family="Bravura" font-size="32">`; the `<supports>` flags.
The XML declaration includes `standalone="no"` and a `<!DOCTYPE score-partwise ...>` line.

### Unit conversion + formatting

- `TENTHS_PER_STAFF_SPACE = 10` (a tenth is 1/10 of a staff space; the 4-staff-space
  staff height = 40 tenths).
- `ssToTenths(ss) = ss × TENTHS_PER_STAFF_SPACE`; `tenthsToSs(t) = t / TENTHS_PER_STAFF_SPACE`.
- Formatter: round to 2 decimal places, then strip trailing zeros and any bare
  trailing decimal point: `40.00 → "40"`, `12.50 → "12.5"`, `12.534 → "12.53"`.
  Used for `system-distance`, `top-system-distance`, and `<millimeters>`.

### Credit positions

A `<credit>`'s `default-x` / `default-y` are measured in tenths from the page's
**bottom-left** corner. The header items are Swing components in `MainPanel`, so
each item's page position is read from its rendered geometry and converted:

- **Pixel → tenths:** px → ss via `ScaleContext` (`px / pixelsPerStaffSpace`,
  default 8 px/ss — `ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE`), then
  `ssToTenths` (× 10). Add a `MusicXmlUtils.pxToTenths(px, scaleContext)` helper
  (or compose the two) rather than hardcoding the factor.
- **Y origin flip:** Swing y is from the top; MusicXML `default-y` is from the
  bottom. `default-y = pxToTenths(pageHeightPx − itemTopPx)`, where
  `itemTopPx = PageModel.getTopMarginPx() + component.getY()`.
- **X:** `default-x = pxToTenths(itemLeftPx)`. Centered items derive
  `itemLeftPx = (lineWidthPx − itemWidthPx) / 2` (matching the render code).

Per-item source (read at export time from the laid-out `MainPanel`):

| Credit | Text | Position source | Page |
|---|---|---|---|
| `title` | `Song.getNumberedTitle()` | `TitleComponent` bounds — centered x (`TitleComponent.java`), top = `topMargin + getY()`; `justify="center"` | 1 |
| attribution roles | (await rework) | block start = `Song.getAttributionStartYSs()` (ss), right-aligned; **no per-role positions until the attribution rework** | 1 |
| `underlyrics` | `Song.getUnderLyrics()` | `UnderLyricsComponent` bounds (centered) | 1 |
| `bangla-lyrics` | `Song.getBanglaLyrics()` | `BanglaLyricsComponent` bounds (centered) | 1 |
| `translation` | `Song.getTranslatedLyrics()` | `TranslationComponent` bounds (centered) | 1 |
| `footnotes` | `Song.getFootnotes()` | `FootnotesComponent` bounds — centered (`FootnotesComponent.java:81`) | 1 |

Because positions live on the laid-out components (which `ScoreView`/`MainPanel`
own at render time), they are **not** derivable from `SongLayoutMetrics` alone.
The production caller — which owns the rendered components — must supply each
item's `(x, y, page)`; the simplest shape is a small per-item position record (or
list of credit descriptors) passed into `writeSong`. Since there is no production
caller yet (deferred — see § Purpose), Phase 4a's writer accepts these positions
as input and tests supply known values.

### Decomposition rationale

Most conceptual decisions (the system-distance span, the `× 10` anchor, the
formatter rules, font-family vs PostScript name, the `"en"` default, the writer
signature) were resolved before this plan and are fixed in the tables above, so
the identification/defaults phases are mechanical. Phases 1 and 2 are independent
foundations (util class, vocabulary) and can run in parallel. Phases 3–4 split
the writer emission into title/identification then defaults to keep each phase
small and its diff reviewable. Phase 4a adds the `<credit>` block; its per-credit
positions come from the rendered component geometry (§ "Credit positions"),
supplied to the writer by the caller, and all credits are page 1 (single-page
model). The attribution-role credits remain gated on the external attribution
rework. Phase 5 fixes the two existing test call sites for the new signature.
Phase 6 isolates all test writing (unit + output + schema) so the mechanical
emission phases are not blocked on test authoring.

### Key code touchpoints

- **Create:** `src/main/java/songscribe/io/musicxml/MusicXmlUtils.java`.
- **Edit:** `src/main/java/songscribe/io/musicxml/MusicXmlTags.java` — append
  element/attribute/value constants (mirror its existing grouping style).
- **Edit:** `src/main/java/songscribe/io/musicxml/MusicXmlWriter.java` —
  `writeSong` (currently `MusicXmlWriter.java:41`); insert header emission before
  the `<part-list>` block at `MusicXmlWriter.java:48`. Reuse `XML.writeBeginTag`,
  `XML.writeEndTag`, `XML.writeValue`, `XML.writeEmptyTag`, `XML.printIndent`,
  `XML.indent`/`dedent` exactly as the existing methods do.
- **Edit (tests):** `MusicXmlRoundTripTest.java:48` and
  `MusicXmlWriterSchemaTest.java:40` — the only two `writeSong` callers.
- **Read-only references (mirror, do not change):** `SongLayoutMetrics`
  (`totalLineHeightSs()`, `maxAboveStaffSs()`), `StaffExtents.STAFF_HEIGHT_SS`,
  `DocumentFontsHolder.getFont(FontKey)`, `FontKey.ANNOTATION` / `FontKey.LYRICS`
  (defaults block) and `FontKey.TITLE` / `FontKey.ATTRIBUTION` / `FontKey.BANGLA` /
  `FontKey.FOOTNOTE` (credit `<credit-words>` fonts),
  `songscribe.Version.PUBLIC_VERSION`, `Song.getTitle()` / `Song.getNumber()`,
  `PageModel` (page height, width, margins). For how production builds metrics,
  see `StaffPanel.updateSongMetrics`
  (`SongLayoutMetricsBuilder.build(layouts, lyricAscentSs)`).

### New writer signature

```java
public static void writeSong(Song song,
                             DocumentFontsHolder fonts,
                             SongLayoutMetrics metrics,
                             PageModel pageModel,
                             PrintWriter pw)
```

The writer stays a pure serializer — it receives pre-built `SongLayoutMetrics` and
`PageModel`, it does not run layout. Resolve the exact `PageModel` access pattern
(constructor arg, factory, or extracted from `Song`) during implementation.

The credit block needs each item's `(x, y, page)`, which lives on the rendered
`MainPanel` components, not in `SongLayoutMetrics` (§ "Credit positions"). Phase 4a
widens the signature to accept these positions — a per-item position record or
list of credit descriptors — supplied by the production caller that owns the
laid-out components. `page` is always `1` (single-page model).

### Resolved decisions

1. **XML declaration / DOCTYPE.** Emit `<?xml version="1.0" encoding="UTF-8" standalone="no"?>` and a `<!DOCTYPE score-partwise PUBLIC "-//Recordare//DTD MusicXML 4.0 Partwise//EN" "http://www.musicxml.org/dtds/partwise.dtd">` line. Confirm the round-trip/schema tests accept the DOCTYPE.
2. **Current year/date source.** Read `Year.now()` / `LocalDate.now()` (ISO_LOCAL_DATE) directly in the static `writeSong`. Tests assert on format/pattern, not the exact value. Do not add an injectable clock now.
3. **`music-font font-size="32"`.** Emit as the fixed constant `"32"` (equals `RenderingUtils.FONT_SIZE × 8 px/ss`); do **not** pull it from `getMusicFont().getSize()` (which returns `4.0`).
4. **Empty `movement-number`.** Omit `<movement-number>` entirely if `song.getNumber()` is empty or blank; emit it normally otherwise.
5. **`page-layout` values.** Derive from `PageModel`, not fixed constants. The writer signature or a helper must provide access to `PageModel`; resolve the exact access path during implementation.
6. **Credit-type vocabulary.** Use the role names from the credit-mapping table as `<credit-type>` text: `title`, `composer`, `lyricist`, `arranger`, `composition date`, `lyrics date`, `rights`, `place`, `underlyrics`, `bangla-lyrics`, `translation`, `footnotes`. The standard MusicXML set covers title/composer/lyricist/arranger/rights; the rest are SongScribe-specific but valid (credit-type is free text).
7. **Credit fonts.** `<credit-words>` `font-family`/`font-size`/`font-weight` come from `fonts.getFont(FontKey)` for the role's `FontKey` (table above). This is the sole storage for the `TITLE`/`ATTRIBUTION`/`BANGLA`/`FOOTNOTE` view fonts — there are no `font-...` misc-fields.
8. **Credit positions.** `default-x`/`default-y` are read from the rendered `MainPanel` component geometry and converted px → tenths with a bottom-left page origin (§ "Credit positions"). The caller (which owns the laid-out components) supplies each item's `(x, y, page)` to `writeSong`; the writer does not run layout.
9. **Credit page index.** Always `1` — SongScribe renders a single page (`ScoreView.updatePageLayout`, `PageModel`). The `page="N"` attribute is emitted for forward-compatibility but never exceeds `1` until a multi-page layout exists.

### Still gated (external)

10. **Per-role attribution credits.** `composer` / `lyricist` / `arranger` / `composition date` / `lyrics date` / `rights` / `place` credits need discrete attribution fields and per-role positions. Today `Song.getAttribution()` is one multi-line string with only a block-level `attributionStartYSs`, and it is not rendered. These credits cannot be emitted until the external attribution rework lands — the same dependency that **blocks** Phase 7 overall.

## Dependencies

- **Internal:** none outside this sub-plan for Phases 1–2. Phases 3–6 depend only
  on earlier phases here (see dashboard).
- **External:** `songscribe.Version` is generated at build time
  (`build.gradle.kts` `generateVersion`); `./scripts/compile.sh` runs that task,
  so no manual step is needed.
- **Must not regress:** the existing structural output (`part-list`, `part`,
  measures, barlines) and the round-trip test must still pass. Header blocks are
  inserted *before* `part-list`; nothing in the existing emission changes.

## Plan

| Phase | Description | Status | Recommended model |
|-------|-------------|--------|-------------------|
| 1 | [Unit Conversion Utilities](#-phase-1-unit-conversion-utilities) | ⏳ Pending | Haiku 4.5 or Sonnet 4.6, low |
| 2 | [MusicXML Vocabulary Additions](#-phase-2-musicxml-vocabulary-additions) | ⏳ Pending | Haiku 4.5, low |
| 3 | [Writer: Title and Identification](#-phase-3-writer-title-and-identification) | ⏳ Pending | Sonnet 4.6, low |
| 4 | [Writer: Defaults Block](#-phase-4-writer-defaults-block) | ⏳ Pending | Sonnet 4.6, medium |
| 4a | [Writer: Credit Block](#-phase-4a-writer-credit-block) | ⏳ Pending | Sonnet 4.6, medium |
| 5 | [Update Writer Call Sites](#-phase-5-update-writer-call-sites) | ⏳ Pending | Haiku 4.5 or Sonnet 4.6, low |
| 6 | [Tests](#-phase-6-tests) | ⏳ Pending | Sonnet 4.6, medium |

## ⏳ Phase 1: Unit Conversion Utilities

**Status:** Pending  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Haiku 4.5 or Sonnet 4.6, low — single new class with fully-specified arithmetic and a deterministic string formatter; no design choices remain.

### Tasks
1. Create `MusicXmlUtils` (package-private `final` class, private constructor,
   standard file header) in `songscribe.io.musicxml`.
2. Add `static final double TENTHS_PER_STAFF_SPACE = 10;` with a doc comment
   explaining the 40-tenths = 4-staff-space anchor.
3. Add `static double ssToTenths(double ss)` and `static double tenthsToSs(double tenths)`.
4. Add `static String formatTenths(double value)` — round to 2 dp, strip trailing
   zeros and a bare trailing decimal point (`40.00 → "40"`, `12.50 → "12.5"`,
   `12.534 → "12.53"`). Use this same method for `<millimeters>`.
5. Run `./scripts/compile.sh` — must report SUCCESS.

## ⏳ Phase 2: MusicXML Vocabulary Additions

**Status:** Pending  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Haiku 4.5, low — appending named string constants to an existing file, mirroring its established grouping.

### Tasks
1. In `MusicXmlTags`, add element-name constants: `MOVEMENT_TITLE`,
   `MOVEMENT_NUMBER`, `IDENTIFICATION`, `CREATOR`, `RIGHTS`, `ENCODING`, `SOFTWARE`,
   `ENCODING_DATE`, `SUPPORTS`, `DEFAULTS`, `SCALING`, `MILLIMETERS`, `TENTHS`,
   `PAGE_LAYOUT`, `PAGE_HEIGHT`, `PAGE_WIDTH`, `PAGE_MARGINS`, `LEFT_MARGIN`,
   `RIGHT_MARGIN`, `TOP_MARGIN`, `BOTTOM_MARGIN`, `SYSTEM_LAYOUT`,
   `SYSTEM_MARGINS`, `SYSTEM_DISTANCE`, `TOP_SYSTEM_DISTANCE`, `STAFF_LAYOUT`,
   `STAFF_DISTANCE`, `MUSIC_FONT`, `WORD_FONT`, `LYRIC_FONT`, `LYRIC_LANGUAGE`.
2. Add attribute-name constants: `ATTR_FONT_FAMILY`, `ATTR_FONT_SIZE`,
   `ATTR_XML_LANG`, plus the `<supports>` attribute names (`ATTR_ELEMENT`,
   `ATTR_ATTRIBUTE`, `ATTR_TYPE`, `ATTR_VALUE`) and `ATTR_PAGE_MARGINS_TYPE`.
3. Add value/literal constants: `MUSIC_FONT_FAMILY = "Bravura"`,
   `MUSIC_FONT_SIZE = "32"`, `LYRIC_LANGUAGE_DEFAULT = "en"`, the fixed
   `<scaling>` values (`SCALING_TENTHS = "40"`, `SCALING_MILLIMETERS = 7.0`),
   fixed system/staff numeric values (`SYSTEM_MARGINS_LEFT = 0`,
   `SYSTEM_MARGINS_RIGHT = 0`, `STAFF_DISTANCE = 0`), and
   `COPYRIGHT = "Copyright © %d Sri Chinmoy Centre, Creative Commons BY-NC-ND 4.0"`,
   `CREATOR_COMPOSER = "composer"`, `CREATOR_LYRICIST = "lyricist"`, and
   `CREATOR_ARRANGER = "arranger"`.
   Do **not** add fixed page-layout dimension constants — those come from `PageModel`.
4. Add element-name constants for the miscellaneous block: `MISCELLANEOUS`,
   `MISCELLANEOUS_FIELD`.
5. Add attribute-name constant `ATTR_NAME` (used as the `name` attribute on
   `<miscellaneous-field>`).
6. Add misc-field name string constants for the fields that **stay** in
   `<miscellaneous>`: `MISC_COMPOSITION_DATE = "composition-date"`,
   `MISC_LYRICS_DATE = "lyrics-date"`, `MISC_PLACE = "place"`,
   `MISC_UNOFFICIAL_TRANSLATION = "unofficial-translation"`. Do **not** add
   misc-field constants for underlyrics / bangla / translated lyrics / footnotes —
   those are credits now.
7. Add credit element-name constants: `CREDIT`, `CREDIT_TYPE`, `CREDIT_WORDS`.
8. Add credit attribute-name constants: `ATTR_PAGE`, `ATTR_FONT_WEIGHT`,
   `ATTR_JUSTIFY`, `ATTR_DEFAULT_X`, `ATTR_DEFAULT_Y` (reuse `ATTR_FONT_FAMILY`,
   `ATTR_FONT_SIZE`, `ATTR_XML_LANG` from step 2).
9. Add credit-type value constants: `CREDIT_TITLE = "title"`,
   `CREDIT_COMPOSER = "composer"`, `CREDIT_LYRICIST = "lyricist"`,
   `CREDIT_ARRANGER = "arranger"`, `CREDIT_COMPOSITION_DATE = "composition date"`,
   `CREDIT_LYRICS_DATE = "lyrics date"`, `CREDIT_RIGHTS = "rights"`,
   `CREDIT_PLACE = "place"`, `CREDIT_UNDERLYRICS = "underlyrics"`,
   `CREDIT_BANGLA_LYRICS = "bangla-lyrics"`, `CREDIT_TRANSLATION = "translation"`,
   `CREDIT_FOOTNOTES = "footnotes"`, plus `JUSTIFY_CENTER = "center"`.
10. Run `./scripts/compile.sh` — must report SUCCESS.

## ⏳ Phase 3: Writer: Title and Identification

**Status:** Pending  <br>
**BlockedBy:** 1, 2  <br>
**Recommended model/effort:** Sonnet 4.6, low — mechanical tag emission mirroring existing `writeSong` helpers; signature widening with a single insertion point.

### Tasks
1. Widen `writeSong` to `(Song, DocumentFontsHolder, SongLayoutMetrics, PageModel, PrintWriter)` (do not yet use `fonts`/`metrics`/`pageModel` beyond compiling — they are consumed in Phase 4). Resolve how to obtain `PageModel` for the new parameter (see Resolved decisions #5). Update the XML declaration: emit `<?xml version="1.0" encoding="UTF-8" standalone="no"?>` and a `<!DOCTYPE score-partwise PUBLIC "-//Recordare//DTD MusicXML 4.0 Partwise//EN" "http://www.musicxml.org/dtds/partwise.dtd">` line.
2. Add a private `writeMovementInfo` that emits `<movement-title>` (`song.getTitle()`) immediately after the `<score-partwise>` open tag, then emits `<movement-number>` only if `song.getNumber()` is non-empty.
3. Add a private `writeIdentification` emitting `<identification>` → `<creator type="composer"/>` (`CREATOR_COMPOSER`), `<creator type="lyricist"/>` (`CREATOR_LYRICIST`), and `<creator type="arranger"/>` (`CREATOR_ARRANGER`) (empty, source API TBD), then `<rights>` with content `String.format(COPYRIGHT, Year.now())`, `<encoding>` → `<software>SongScribe v{version}</software>`, `<encoding-date>` (from `LocalDate.now()` in ISO_LOCAL_DATE format), and the `<supports>` flags.
4. Extend `writeIdentification` to close `<identification>` with a
   `<miscellaneous>` block. Emit one `<miscellaneous-field name="...">` element
   for each non-blank / non-false field that **stays** in `<miscellaneous>`,
   using the constants from Phase 2: `composition-date` (ISO 8601 assembled from
   year/month/day, exact getters TBD), `lyrics-date` (same assembly, omit when
   equal to or absent from composition date), `place`, `unofficial-translation`
   (emit `"true"` only when flag is set). The score-below text blocks
   (underlyrics, bangla, translated lyrics, footnotes) are **not** emitted here —
   they are credits (Phase 4a). Omit the entire `<miscellaneous>` element if no
   fields have values.
5. Call both new methods in order before the existing `<part-list>` emission.
6. Run `./scripts/compile.sh` — must report SUCCESS.

## ⏳ Phase 4: Writer: Defaults Block

**Status:** Pending  <br>
**BlockedBy:** 3  <br>
**Recommended model/effort:** Sonnet 4.6, medium — more sub-elements and the two computed distances plus font lookups; still mechanical given the value-mapping table.

### Tasks
1. Add a private `writeDefaults` that emits `<defaults>` and its children in order, called after `writeIdentification` and before `<part-list>`.
2. Emit `<scaling>` (`<millimeters>` via `MusicXmlUtils.formatTenths`,
   `<tenths>40</tenths>`), `<page-layout>` with height, width, and all four
   margins derived from `pageModel`, and
   `<staff-layout><staff-distance>0</staff-distance></staff-layout>`.
3. Emit `<system-layout>` with `<system-margins>` (0/0), `<system-distance>` =
   `formatTenths(ssToTenths(metrics.totalLineHeightSs() − StaffExtents.STAFF_HEIGHT_SS))`,
   and `<top-system-distance>` = `formatTenths(ssToTenths(metrics.maxAboveStaffSs()))`.
4. Emit `<music-font font-family="Bravura" font-size="32"/>`.
5. Emit `<word-font>` from `fonts.getFont(FontKey.ANNOTATION)` (`getFamily()`,
   `getSize()`) and `<lyric-font>` from `fonts.getFont(FontKey.LYRICS)`.
6. Emit `<lyric-language xml:lang="en"/>`.
7. Run `./scripts/compile.sh` — must report SUCCESS.

## ⏳ Phase 4a: Writer: Credit Block

**Status:** Pending  <br>
**BlockedBy:** 4 (attribution-role credits additionally gated on the external attribution rework — Resolved decisions #10)  <br>
**Recommended model/effort:** Sonnet 4.6, medium — mechanical tag emission; positions come from the rendered geometry passed in by the caller (§ "Credit positions").

> **Positions:** each credit's `(x, y, page)` is supplied by the caller from the
> laid-out `MainPanel` components and converted px → tenths with a bottom-left
> page origin (§ "Credit positions"). Widen the signature to accept these
> positions (a per-item record / credit-descriptor list). `page` is always `1`.
> The attribution-role credits stay deferred until the attribution rework.

### Tasks
1. Add a `pxToTenths` helper to `MusicXmlUtils` (px → ss via `ScaleContext`, then
   `ssToTenths`); use it for all credit positions. Widen `writeSong` (or add a
   credit-descriptor parameter) to receive each item's `(x, y, page)`.
2. Add a private `writeCredits` that emits the `<credit>` elements after
   `writeDefaults` and before the `<part-list>` emission.
3. Add a private helper (e.g. `writeCredit(creditType, fontKey, page, text, xPx, yTopPx, …)`)
   emitting `<credit page="N">` (omit `page` when 1) → `<credit-type>` → and
   `<credit-words>` with `font-family`/`font-size`/`font-weight` from
   `fonts.getFont(fontKey)`, optional `justify="center"`, and `default-x` /
   `default-y` (px → tenths; `default-y` flipped to a bottom-left origin via the
   page height). The element text is the rendered display string.
4. Emit the title credit from `TitleComponent` geometry (`FontKey.TITLE`,
   `justify="center"`), only when the title is non-blank. The `<credit-words>`
   text is `song.getNumberedTitle()` (title prefixed with the movement number
   when present).
5. Emit the score-below credits, each only when non-blank, on page 1, from their
   component geometry: `underlyrics` (`FontKey.LYRICS`), `bangla-lyrics`
   (`FontKey.BANGLA`, add `xml:lang`), `translation` (`FontKey.LYRICS`),
   `footnotes` (`FontKey.FOOTNOTE`).
6. Leave the attribution-role credits (`composer` … `place`) **unimplemented**
   with a clear TODO referencing the attribution rework (Resolved decisions #10):
   the discrete fields and per-role positions do not exist yet.
7. Run `./scripts/compile.sh` — must report SUCCESS.

## ⏳ Phase 5: Update Writer Call Sites

**Status:** Pending  <br>
**BlockedBy:** 3  <br>
**Recommended model/effort:** Haiku 4.5 or Sonnet 4.6, low — two mechanical call-site edits to satisfy the new signature.

### Tasks
1. In `MusicXmlRoundTripTest.writeToString` (`MusicXmlRoundTripTest.java:48`),
   pass a `DocumentFonts` (use `DocumentFonts.defaultsFromPrefs()` or an explicit
   test instance), a `SongLayoutMetrics` built via
   `SongLayoutMetricsBuilder.build(List.of(), 0.0)` (or from the song's layout if
   the test already has results), and a `PageModel` test instance.
2. In `MusicXmlWriterSchemaTest.testEmptyDefaultSongIsSchemaValid`
   (`MusicXmlWriterSchemaTest.java:40`), apply the same call-site update.
3. Run `./scripts/compile.sh` — must report SUCCESS.

## ⏳ Phase 6: Tests

**Status:** Pending  <br>
**BlockedBy:** 4, 4a, 5  <br>
**Recommended model/effort:** Sonnet 4.6, medium — new unit tests plus assertions over emitted XML and schema validation.

### Tasks
1. Create `MusicXmlUtilsTest`: `ssToTenths`/`tenthsToSs` round-trip, the
   `TENTHS_PER_STAFF_SPACE` anchor (4 ss → 40 tenths), and `formatTenths` cases
   (`40.00 → "40"`, `12.50 → "12.5"`, `12.534 → "12.53"`).
2. Add a writer test asserting the title/identification block: `movement-title`
   present; `movement-number` present when `song.getNumber()` is non-empty and
   absent when empty; `software` contains `Version.PUBLIC_VERSION`; the
   `<supports>` flags present; `encoding-date` matches ISO date pattern.
3. Add a writer test for the `<miscellaneous>` block: a song populated with the
   residual misc-field values (`composition-date`, `lyrics-date`, `place`,
   `unofficial-translation`) emits a `<miscellaneous>` element with the correct
   `<miscellaneous-field name="...">` entries; a song with none emits no
   `<miscellaneous>` element at all; `lyrics-date` is omitted when it equals
   `composition-date` and present when distinct; `unofficial-translation` appears
   only when the flag is true. Confirm there are **no** `<miscellaneous-field>`
   entries for underlyrics / bangla / translated lyrics / footnotes (those are
   credits).
4. Add a writer test asserting the defaults block from a known `SongLayoutMetrics`,
   known `DocumentFonts`, and known `PageModel`: `page-height`/`page-width`/margins
   match PageModel values; `system-distance`/`top-system-distance` numeric values;
   `word-font`/`lyric-font` family+size; `music-font` size `32`; and
   `lyric-language="en"`.
5. Add a writer test for the `<credit>` block: the title and each populated
   attribution role emit a `<credit>` (page 1) with the right `<credit-type>` and
   `<credit-words>` font (family/size/weight from the role's `FontKey`); blank
   roles emit no credit; `lyrics date` credit is omitted when equal to the
   composition date. The score-below blocks (underlyrics, bangla-lyrics,
   translation, footnotes) emit `<credit page="N">` on the last page, with
   `xml:lang` on the bangla credit; absent blocks emit no credit.
6. Extend `MusicXmlWriterSchemaTest` so the header + defaults + credits output
   validates against the MusicXML 4.0 schema in `docs/musicxml-4.0-schema/`.
7. Run `./scripts/compile.sh`, then `./scripts/test.sh MusicXmlUtilsTest
   MusicXmlWriterSchemaTest MusicXmlRoundTripTest` (and the new writer tests) —
   all must be green.

## Verification (whole sub-plan)

- `./scripts/compile.sh` reports SUCCESS after each phase.
- Unit tests green: `MusicXmlUtilsTest`, the writer
  header/identification/misc-field/defaults/credit tests,
  `MusicXmlWriterSchemaTest`, `MusicXmlRoundTripTest`.
- Emitted output for a representative song contains the correct element set and
  ordering (title, identification with `<miscellaneous>`, defaults, credits) with
  all values matching the element-to-source and credit tables above.
- Header + defaults + credits output validates against the MusicXML 4.0 schema.
- The pre-existing structural output (part-list, measures, barlines) is unchanged
  and its round-trip test still passes.
