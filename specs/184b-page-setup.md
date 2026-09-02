# Page Setup

Move horizontal layout authority from line width to margins. Add a per-document Page Setup (paper size, per-edge margins, mirrored/spread margins) persisted in MusicXML with true `<page-layout>` semantics, a macOS-style Page Setup dialog, and mirrored-margin printing. Line width becomes a derived value.

**Depends on:** `specs/184-pagination.md` (issue #184) — pagination, page surfaces, and multi-page printing must be in place first. This spec changes what feeds `PageModel`; #184 changes what consumes it.

* * *
## Goals
1. **Per-document Page Setup** — paper size, per-edge margins, mirrored (inner/outer) margins with verso-first option; margins authoritative, line width derived; fully undoable; persisted with true MusicXML `<page-layout>` semantics

2. **Page Setup dialog** — new macOS-style dialog, the only UI for paper geometry

3. **Mirrored-margin printing** — inner/outer margin placement by page parity

* * *
## Current State (after #184 lands)
- `Song.lineWidthSs` is the canonical horizontal layout value, mutation-tracked via `LayoutField.LINE_WIDTH_SS`, applied through `ScoreView.updatePageLayout(int)`. **There is no UI that writes it.** `SongSettingsDialog`'s Music tab carries only a tempo section; the Line Width section this spec used to describe there is already gone (`ui-dialog-interface.md` Phase 4). This spec is now the *only* route to `Song.lineWidthSs`, not a replacement for an existing one.

- `ScoreView.openFile` rejects files whose stored line width exceeds `PageModel.MAX_LINE_WIDTH_INCHES = 7.77` (`SongLoadResult.LineWidthTooLarge`), validated against `PageModel.MIN_LINE_WIDTH_INCHES = 5.0`.

- `PageModel` is all-static, reads `PrefsKey.PAGE_SIZE` in `getSize()`, has fixed `VERTICAL_MARGIN_INCHES = 0.5`, and derives horizontal margins by centering the line width in `getHorizontalMarginPx(lineWidthPx)`.

- `<defaults><page-layout><page-width>` is **overloaded** to carry `lineWidthSs` (`HeaderBuilder.buildDefaults`, which sets `pageLayout.setPageWidth(...)` from the model line width; read by `HeaderMapper.mapDefaults`, which calls `song.setLineWidthSs`). `<page-height>` and `<scaling>` are fixed write-forward; `<page-margins>` is never written. SongScribe-specific scalars ride in `<miscellaneous-field>` entries.

* * *
## Design
### 1. Page setup document model

New per-document state on `Song` (all mutation-tracked, see §6):

| Field | Type | Default (new documents) |
|-------|------|------------------------|
| `paperSize` | `PaperSize` enum | from `PrefsKey.PAGE_SIZE` |
| `topMarginInches` | `double` | 0.5 |
| `bottomMarginInches` | `double` | 0.5 |
| `leftMarginInches` (inner when mirrored) | `double` | 0.5 |
| `rightMarginInches` (outer when mirrored) | `double` | 0.5 |
| `mirroredMargins` | `boolean` | false |
| `versoFirst` | `boolean` | false |

- **`PaperSize` must be declared in `songscribe.dom`, not `songscribe.layout`.** `PackageDependencyTest.domMustNotImportLayout` (`src/test/java/songscribe/PackageDependencyTest.java:63`) forbids `dom` → `layout`, and `Song` will hold a `PaperSize` field. `DocumentScale` already lives in `dom` for the same reason, so the inches → px conversion `Song` needs is available there.

- `PaperSize` replaces `PageModel.Size` and offers a curated portrait-only list: **Letter, Legal, Tabloid, A3, A4, A5, B4, B5** (widths/heights in inches, following the existing `Size(double widthInches, double heightInches)` shape).

- Physical units (inches) are the storage unit for paper geometry, matching the existing `*_INCHES` constants; `PageModel` is where physical units convert to document pixels, so conversion to `DocPx`/`Ss` happens there (`GraphicUtils.Unit.INCH`, `DocumentScale`).

- `PrefsKey.PAGE_SIZE` remains, but now means "paper size for **new** documents" (and legacy loads, §5). The `PreferencesDialog` radio group is unchanged apart from label wording if needed. Margins/mirrored get **no** prefs surface — new documents start from the fixed defaults above.

### 2. Derived line width

`Song.lineWidthSs` ceases to be stored or user-set:

```
lineWidthInches = paperWidthInches − leftMarginInches − rightMarginInches
lineWidthSs     = pxToSs(inchesToPx(lineWidthInches))
```

- `Song.getLineWidthSs()` / `getLineWidthPx()` remain (dozens of rendering and layout consumers) but compute from page setup. `Song.setLineWidthSs` and `Song.applyLineWidthSs` are removed; `ScoreView.updatePageLayout(int lineWidthDocPx)` loses its model-write role — page layout refresh is driven by mutations (§6).

- **Validation** (enforced in the Page Setup dialog, §4):

  - every margin ≥ `MIN_MARGIN_INCHES = 0.25` (renames `MIN_HORIZONTAL_MARGIN_INCHES`, now applied to all four edges)
  - derived line width ≥ `MIN_LINE_WIDTH_INCHES = 5.0`
  - `MAX_LINE_WIDTH_INCHES` is deleted — the maximum is now implied by paper width minus the margin floors.

**Removing `LayoutField.LINE_WIDTH_SS` has three call sites beyond `Song` itself.** All three must be handled or the build breaks:

| Site | Action |
|---|---|
| `undo/MutationReplayer.java:201` — `case LINE_WIDTH_SS -> song.setLineWidthSs(...)` in `applyLayoutField` | remove the case; add cases for the seven new fields (§6) |
| `message/notification/LayoutDidChangeNotification` — carries `lineWidthSs` | drop that payload field |
| `dom/Song.java:1557-1568` — `@Handler layoutDidChange` calls `setLineWidthSs(update.getLineWidthSs())` at line 1565 | remove that branch |
| `io/musicxml/HeaderMapper.java` (`mapDefaults`) | replaced by the new read path (§5) |

### 3. PageModel rework

`PageModel` stops reading prefs and becomes a view over the active `Song`'s page setup (instance created from a `Song`, or static methods taking one):

- `getPageWidthPx()` / `getPageHeightPx()` — from `paperSize`
- `getTopMarginPx()` / `getBottomMarginPx()` — from the document margins
- `getLeftMarginPx()` / `getRightMarginPx()` — from the document margins; when `mirroredMargins` is set these return the **centered equivalent** `(inner + outer) / 2` for screen layout; the true inner/outer values are exposed separately for print (§7)
- `getContentHeightPx()` — unchanged from #184; now sourced from document margins

`getHorizontalMarginPx(lineWidthPx)` (centering derivation) is removed.

**Screen never zigzags.** Content is placed at the left margin; when `mirroredMargins` is on, the screen uses the centered-equivalent margins. The mirrored shift is print-only.

### 4. Page Setup dialog

New `PageSetupDialog extends StandardDialog`, category `EXCLUSIVE` (precedent: `SongSettingsDialog`, constructor line 108), designed to read like a typical macOS Page Setup dialog:

- Paper size combo (`PaperSize` list, dimensions shown in the active unit)
- Four margin fields with unit label (inch/cm per `PrefsKey.METRIC`, decimal filtering via `InputUtils.addDecimalFilter`)
- "Mirrored margins" checkbox — relabels Left/Right ↔ Inner/Outer
- "First page is left-hand (verso)" checkbox — enabled only when mirrored
- Read-only derived line width display, updating live
- Validation per §2 in `isValidData()`, error alerts via `OptionDialogs` (`alert.*` keys); `setData()` commits (§6)
- String keys under `dialog.page.setup.*` per the strings taxonomy

Follow `StandardDialog`'s lifecycle contract: OK runs `verifyFocusedField()` → `isValidData()` → `setData()` → `repaintScore()`. Set `cancelButton.setVerifyInputWhenFocusTarget(false)` so an invalid margin field cannot block Cancel, mirroring `SongSettingsDialog` line 120.

**Menu/action**: `Actions.PAGE_SETUP_ACTION` — a `DialogOpenAction<>` opening `PageSetupDialog`, accelerator ⇧⌘P, flags `DISABLE_WHEN_PLAYING` + `OPENS_DIALOG`, inserted in `MenuController.initFileMenu()` directly above `Actions.PRINT_ACTION` (macOS convention).

### 5. MusicXML persistence

**New format** (written by `HeaderBuilder.buildDefaults`, read by `HeaderMapper.mapDefaults`):

```xml
<defaults>
  <scaling>…</scaling>                          <!-- unchanged, fixed -->
  <page-layout>
    <page-height>…</page-height>                <!-- real paper height, tenths -->
    <page-width>…</page-width>                  <!-- real paper width, tenths -->
    <page-margins type="both">                  <!-- non-mirrored -->
      <left-margin>…</left-margin> <right-margin>…</right-margin>
      <top-margin>…</top-margin> <bottom-margin>…</bottom-margin>
    </page-margins>
  </page-layout>
  …
</defaults>
```

- Mirrored margins write two `<page-margins>` elements with `type="odd"` and `type="even"` instead of one `type="both"`.
- All values in tenths via `MusicXmlUnits.ssAsTenths` (attribute) / `formatSsAsTenths` (element text). `paperSize` is recovered on read by matching width/height against the `PaperSize` table (nearest match).
- `versoFirst` has no native MusicXML slot → `<miscellaneous-field name="verso-first">` (written only when true), following the `MISC_SUB_ATTRIBUTION_FONT` pattern in `writeMiscellaneousFields` (lines 158-222) and `applyMiscField` (lines 548-597).
- Line width is **no longer stored** — it is derived on load from page width minus margins.
- The fixed copyright constant is written into `<identification><rights>` (currently write-forward anyway); the reader continues to ignore it.
- `<credit page>` attributes remain `1` — credits are display-only and re-derived from head data on read.
- Every emitted document must still pass `MusicXmlSchemaValidator`. The MusicXML 4.0 `page-layout` content model is `((page-height, page-width)?, page-margins*)`, so `<page-margins>` follows the height/width pair as written above.

**Legacy load** (both `.musicxml` without `<page-margins>` and `.mssw`):

- Detected by the **absence of** `<page-margins>` — no version marker.
- Old semantics apply: `<page-width>` is the line width. Recover:
  - `paperSize` ← `PrefsKey.PAGE_SIZE`
  - left = right = `(paperWidthInches − lineWidthInches) / 2`
  - top = bottom = 0.5″, mirrored/versoFirst off
- Rendering is pixel-identical to today (the same centering math). The file upgrades to the new format on next save.
- The `SongLoadResult.LineWidthTooLarge` guard is kept, now checking the legacy line width against `paperWidthInches − 2 × MIN_MARGIN_INCHES`.

### 6. Undo / mutations

New `LayoutField` entries: `PAPER_SIZE(PaperSize.class)`, `TOP_MARGIN_INCHES(Double.class)`, `BOTTOM_MARGIN_INCHES(Double.class)`, `LEFT_MARGIN_INCHES(Double.class)`, `RIGHT_MARGIN_INCHES(Double.class)`, `MIRRORED_MARGINS(Boolean.class)`, `VERSO_FIRST(Boolean.class)`. `LINE_WIDTH_SS` is removed.

`FieldTypeValidator` rejects primitive expected types, so all seven must use boxed classes. Add the matching cases to `MutationReplayer.applyLayoutField` (`undo/MutationReplayer.java:199`).

`PageSetupDialog.setData()` wraps all changed fields in **one** `Song.withModification(label, …)` bracket — one `SongDidChangeNotification`, one undo step reverting the whole Page Setup commit. Undo/redo of these mutations triggers repagination like any other layout change, through the #184 §8 path.

### 7. Mirrored-margin printing

**Mirrored margins apply in print only.** With `mirroredMargins` and more than one page: for each page, the content block is placed at the inner or outer margin according to page parity; `versoFirst` flips which parity is the left-hand (verso) page. A single-page document prints centered (the §3 centered-equivalent margins), matching the screen.

```
  versoFirst = false                 versoFirst = true
  ─────────────────                  ─────────────────
  p1 recto  [ outer │ content │ inner ]   p1 verso  [ inner │ content │ outer ]
  p2 verso  [ inner │ content │ outer ]   p2 recto  [ outer │ content │ inner ]
  p3 recto  [ outer │ content │ inner ]   p3 verso  [ inner │ content │ outer ]
```

`MainFrame.print`'s `PageFormat`/`Paper` derivation (#184 §6) gains the parity branch.

* * *
## What Is Removed
- `Song.setLineWidthSs` / `Song.applyLineWidthSs` / the stored `lineWidthSs` field (getter stays, derived)
- `LayoutField.LINE_WIDTH_SS` and its `MutationReplayer` case
- `LayoutDidChangeNotification`'s `lineWidthSs` payload and `Song.layoutDidChange`'s branch for it
- `PageModel.Size` (replaced by `PaperSize`), `MAX_LINE_WIDTH_INCHES`, `getHorizontalMarginPx`, pref-reading singleton behavior
- Legacy `<page-width> = line width` **write** semantics (read path kept for legacy files)

* * *
## New / Modified Files
| File | Change |
|------|--------|
| `dom/PaperSize.java` | **New** — curated paper-size enum (in `dom`, not `layout` — see §1) |
| `dom/Song.java` | **Modified** — page-setup fields, derived line width, mutation plumbing, `layoutDidChange` branch removed |
| `layout/PageModel.java` | **Modified** — sourced from Song page setup; centered-equivalent margins |
| `ui/dialog/PageSetupDialog.java` | **New** — macOS-style page setup (StandardDialog, EXCLUSIVE) |
| `ui/dialog/SongSettingsDialog.java` | **Modified** — Line Width section removed |
| `ui/action/Actions.java`, `ui/menu/MenuController.java` | **Modified** — `PAGE_SETUP_ACTION` in File menu |
| `ui/component/ScoreView.java` | **Modified** — `updatePageLayout` loses its model-write role |
| `ui/component/MainFrame.java` | **Modified** — mirrored parity branch in `print` |
| `message/mutation/LayoutField.java` | **Modified** — seven new fields, `LINE_WIDTH_SS` removed |
| `message/notification/LayoutDidChangeNotification.java` | **Modified** — `lineWidthSs` payload removed |
| `undo/MutationReplayer.java` | **Modified** — `applyLayoutField` cases updated |
| `io/musicxml/HeaderBuilder.java` / `HeaderMapper.java` / `MusicXmlTags.java` | **Modified** — real page-layout write/read, legacy fallback, verso-first misc-field |
| `io/SongLoader.java` (`.mssw` path) | **Modified** — center-derive margins on legacy import |
| `resources/songscribe/strings.properties` | **Modified** — dialog keys added; dead line-width keys removed |

* * *
## Testing

Unit tests only. Extend `MusicXmlDefaultsRoundTripTest`, which already covers the `<page-width>` line-width round trip and the write-forward-values-ignored-on-read case; its write/read plumbing comes from the static helpers in `MusicXmlRoundTripSupport`.

### Derivation math
- line width from paper size + margins, per `PaperSize` entry
- centered-equivalent margins under mirrored
- print parity placement with and without `versoFirst`
- single-page centered rule

### Validation
- margin floor (`MIN_MARGIN_INCHES`) on each of the four edges independently
- minimum derived line width rejected below `MIN_LINE_WIDTH_INCHES`
- per-unit (inch/cm) round-trips of the dialog values, both `PrefsKey.METRIC` states

### MusicXML round-trip
- `<page-layout>`/`<page-margins>` in both `type="both"` and `type="odd"`/`type="even"` forms
- verso-first misc-field written only when true, absent when false
- nearest-`PaperSize` recovery, including a width/height pair that matches no entry exactly
- emitted documents pass `MusicXmlSchemaValidator`

### Legacy load
- old `.musicxml` (no `<page-margins>`) and `.mssw`: center-derived margins reproduce the stored line width **exactly**
- `LineWidthTooLarge` guard against the new bound (`paperWidthInches − 2 × MIN_MARGIN_INCHES`)
- a legacy file re-saved emits the new format

### Mutations
- a Page Setup commit produces exactly one bracket and one `SongDidChangeNotification`
- undo restores all seven fields and the derived line width
- `FieldTypeValidator` coverage for the new `LayoutField` entries, including that `PAPER_SIZE` rejects a non-`PaperSize` value

* * *
## Non-Goals
- Preferences UI for default margins or default mirrored state
- Facing-pages (side-by-side spread) screen view
- Landscape orientation
- Third-party MusicXML import of page layout
- PDF, image, and SVG export
