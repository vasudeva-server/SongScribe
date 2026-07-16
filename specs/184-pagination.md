# Pagination Support
Introduce real pages: on-screen pagination with separate page surfaces, true multi-page printing, page numbers, a bottom-of-last-page copyright line, and a per-document Page Setup (paper size, margins, mirrored/spread margins) persisted in MusicXML. Horizontal layout authority moves from line width to margins; line width becomes a derived value.

**Issue:** vasudeva-server/SongScribe#184

* * *
## Goals
1. **On-screen pagination** — separate white page surfaces stacked vertically with gaps, replacing the single ever-growing canvas
  
2. **Multi-page printing** — a real `Printable` implementation painting each page through a shared per-page paint method
  
3. **Page numbers** — plain numeral, bottom center, pages 2+ only, drawn inside the bottom margin
  
4. **Copyright line** — fixed text pinned at the bottom of the last page's content area, on screen and in print
  
5. **Per-document Page Setup** — paper size, per-edge margins, mirrored (inner/outer) margins with verso-first option; margins authoritative, line width derived; fully undoable; persisted with true MusicXML `<page-layout>` semantics
  
6. **Page Setup dialog** — new macOS-style dialog; retire the conflicting legacy `PaperSizeStep` UI and the Song Settings line-width section
  
7. **Status bar page indicator** — "Page N of M" with a click-to-jump popup
  

* * *
## Current State
### Layout and rendering
- `ScoreView` (inside `JScrollPane` → `ScorePanel`) is a single white `JComponent` sized to one page width whose height grows without bound: `getSheetHeightPx()` returns `max(pageHeightPx, contentHeight + margins)`. Margins are an `EmptyBorder`.
  
- `MainPanel` stacks `TitleComponent` → `SubtitleComponent` → strut → `StaffPanel` (one `LinePanel` per music line, `LINE_MARGIN_BOTTOM_SS = 2.0`) → `TextPanel` (under-lyrics/Bangla/translation) → `FootnotesComponent` via `BoxLayout.Y_AXIS`. No page-break concept exists anywhere.
  
- `PageModel` (singleton) is the only page-geometry authority: `Size` enum (LETTER, A4) read from `PrefsKey.PAGE_SIZE`, fixed `VERTICAL_MARGIN_INCHES = 0.5`, and `getHorizontalMarginPx(lineWidthPx)` which _derives_ margins by centering the line width.
  
- Zoom is per-view via `ViewScale` (`Ss` / `DocPx` / `ViewPx`); `ViewScale.IDENTITY` serves off-screen consumers.
  
### Line width
- `Song.lineWidthSs` is the canonical horizontal layout value, mutation-tracked via `LayoutField.LINE_WIDTH_SS`. The only user-facing writer is the Line Width section of `SongSettingsDialog`'s Music tab (`lineWidthField` + `LineWidthVerifier`, validated against `PageModel.MIN_LINE_WIDTH_INCHES = 5.0` / `MAX_LINE_WIDTH_INCHES = 7.77`), applied through `ScoreView.updatePageLayout(int)`.
  
- `ScoreView.openFile` rejects files whose stored line width exceeds `MAX_LINE_WIDTH_INCHES` (`SongLoadResult.LineWidthTooLarge`).
  
### Print and export
- `MainFrame implements Printable`; `print(...)` is a stub hardcoded to one page (`pageIndex >= 1 → NO_SUCH_PAGE`) drawing "not implemented" strings. `PDFExporter`, `ImageExporter`, `SVGExporter` are stubs. The old shared `ScoreRenderer` was deleted during the score-layout redesign. No PDF library is in `build.gradle.kts`.
  
- `PageLayoutData` (export-only, raw px) has `mirrored` / `songsPerPage` fields that are written by `PaperSizeStep` (inside `ExportPDFDialog`, mirrored checkbox hidden) but never read — dead scaffolding.
  
### Persistence
- Native format is MusicXML 4.0 (`.musicxml`), hand-rolled writer/reader (`MusicXmlWriter`/`MusicXmlReader` + header/measure/note/direction helpers). `.mssw` is a legacy read-only import.
  
- `<defaults><page-layout><page-width>` is **overloaded** to carry `lineWidthSs` (the sole canonical page-layout value); `<page-height>` and `<scaling>` are fixed write-forward; `<page-margins>` is never written. SongScribe-specific scalars ride in `<miscellaneous-field>` entries.
  
- `<print new-system="yes">` round-trips line breaks; `new-page` is unused.
  

* * *
## Design
### 1. Page setup document model
New per-document state on `Song` (all mutation-tracked, see §11):

| Field | Type | Default (new documents) |
|-------|------|------------------------|
| `paperSize` | `PaperSize` enum | from `PrefsKey.PAGE_SIZE` |
| `topMarginInches` | `double` | 0.5 |
| `bottomMarginInches` | `double` | 0.5 |
| `leftMarginInches` (inner when mirrored) | `double` | 0.5 |
| `rightMarginInches` (outer when mirrored) | `double` | 0.5 |
| `mirroredMargins` | `boolean` | false |
| `versoFirst` | `boolean` | false |

- `PaperSize` replaces `PageModel.Size` and offers a curated portrait-only list: **Letter, Legal, Tabloid, A3, A4, A5, B4, B5** (widths/heights in inches, following the existing `Size(double widthInches, double heightInches)` shape).
  
- Physical units (inches) are the storage unit for paper geometry, matching the existing `*_INCHES` constants; conversion to `DocPx`/`Ss` happens at the `PageModel` seam as today (`GraphicUtils.Unit.INCH`, `ScaleContext`).
  
- `PrefsKey.PAGE_SIZE` remains, but now means "paper size for **new** documents" (and legacy loads, §8). The `PreferencesDialog` radio group is unchanged apart from label wording if needed. Margins/mirrored get **no** prefs surface — new documents start from the fixed defaults above.
  
### 2. Derived line width
`Song.lineWidthSs` ceases to be stored or user-set:

```
lineWidthInches = paperWidthInches − leftMarginInches − rightMarginInches
lineWidthSs     = pxToSs(inchesToPx(lineWidthInches))
```

- `Song.getLineWidthSs()` / `getLineWidthPx()` remain (dozens of rendering and layout consumers) but compute from page setup. `Song.setLineWidthSs` and `LayoutField.LINE_WIDTH_SS` are removed; `ScoreView.updatePageLayout(int lineWidthDocPx)` loses its model-write role (page layout refresh is driven by mutations, §11).
  
- **Validation** (enforced in the Page Setup dialog, §9):
  
  - every margin ≥ `MIN_MARGIN_INCHES = 0.25` (renames `MIN_HORIZONTAL_MARGIN_INCHES`, now applied to all four edges)
    
  - derived line width ≥ `MIN_LINE_WIDTH_INCHES = 5.0`
    
  - `MAX_LINE_WIDTH_INCHES` is deleted — the maximum is now implied by paper width minus the margin floors.
    
### 3. PageModel rework
`PageModel` stops reading prefs and becomes a view over the active `Song`'s page setup (instance created from a `Song`, or static methods taking one):

- `getPageWidthPx()` / `getPageHeightPx()` — from `paperSize`
  
- `getTopMarginPx()` / `getBottomMarginPx()` — from the document margins
  
- `getLeftMarginPx()` / `getRightMarginPx()` — from the document margins; when `mirroredMargins` is set these return the **centered equivalent**`(inner + outer) / 2` for screen layout (§5); the true inner/outer values are exposed separately for print (§7)
  
- `getContentHeightPx()` — page height minus top/bottom margins (the pagination unit of measure)
  
### 4. Pagination engine
New class `Paginator` in `songscribe.layout`. Pure layout math — input is the ordered list of vertical blocks with their heights plus the page content height; output is a `Pagination` result: per-page block assignments and the total page count.

**Blocks, in document order:**

1. Title + subtitle (one block, page 1 only)
  
2. The `SCORE_MARGIN_TOP_SS` strut
  
3. Each music line (`LinePanel`) — individually placeable, never split
  
4. `TextPanel` (under-lyrics/Bangla/translation) — one atomic block
  
5. Footnotes — one atomic block, **pinned** (see below)
  
6. Copyright line — **pinned band** on the last page (§6)
  

**Algorithm — greedy fill, fully automatic:**

- Place blocks in order onto page 1, 2, … Each page's capacity is `getContentHeightPx()`. A block that does not fit in the remaining space moves whole to the next page. No widow/orphan rules, no manual breaks.
  
- **Copyright band**: the last page always reserves `copyrightBandPx = gap + copyright line height` at the bottom of its content area (gap: a named `Ss` constant, reusing the footnote-gap scale of `FOOTNOTES_MIN_MARGIN_TOP_SS`). Because the band exists on screen and in print (§6), pagination is identical in both.
  
- **Footnotes pinning**: footnotes render at the bottom of the final page, directly above the copyright band, regardless of where content ends. If footnotes (+ band) don't fit below the last content block, they move to a new final page (still bottom-pinned).
  
- **Oversized atomic block**: a block taller than a full page's content area gets its own page and simply overflows (clipped at the page edge in print). Degenerate input, degenerate output.
  

Repagination runs whenever layout inputs change: any `SongDidChangeNotification` that affects content heights, page-setup mutations, and zoom changes.
### 5. On-screen page surfaces
The single-sheet structure is replaced by a page stack:

- `ScorePanel` (gray, existing) hosts one `PageComponent` per page — white, view-scaled page size, `EmptyBorder` margins — stacked vertically with a named gap constant (`PAGE_GAP_SS` or a view-px constant) between pages.
  
- The existing content components (`TitleComponent`, `SubtitleComponent`, `LinePanel`s, `TextPanel`, `FootnotesComponent`, new `CopyrightComponent`) are **distributed** across `PageComponent`s per the `Paginator` result and reparented on repagination. `MainPanel`/`StaffPanel`'s roles as the single vertical stack are absorbed by the per-page containers (exact decomposition is an implementation decision; the component classes themselves are reused).
  
- `PageComponent` draws its own page number (§6).
  
- **Horizontal placement**: content is placed at the left margin. When `mirroredMargins` is on, screen uses the centered equivalent margins (§3) — the mirrored shift is print-only, so the on-screen stack never zigzags.
  
- **Coordinate mapping**: `ScoreView`'s edit overlays, mouse handling, selection, and adjustment modes currently assume one canvas. A page-aware mapping layer (page origin offsets added to the existing `DocPx`/`ViewPx` conversions) is required. This is the highest-risk area of the change; every `ScoreView` coordinate conversion call site must be audited.
  
- **Zoom** continues to work through `ViewScale` exactly as today; page surfaces scale like the current sheet does. Scroll-anchoring in `applyZoomPercent` must account for page gaps.
  
### 6. Page numbers and copyright
**Page number**

- Pages 2+ only; page 1 (and therefore any single-page document) never shows a number.
  
- Plain numeral (`"2"`), horizontally centered, drawn **inside the bottom margin**, vertically centered in it. The content area is not reduced.
  
- Font: new `FontKey.PAGE_NUMBER` with `SystemPrefsKey.PAGE_NUMBER_FONT` / `PAGE_NUMBER_FONT_SIZE` defaults in `system-defaults.json`. Not surfaced in any dialog yet (same status as `FOOTNOTE` / `BANGLA`).
  
- Rendered on screen, in print, and (later) in exports.
  

**Copyright line**

- Fixed application constant (a `strings.properties` key, e.g. `song.copyright.notice`): `© Sri Chinmoy Songs. All rights reserved under CC BY-NC-ND 4.0`
  
- Pinned at the bottom of the **last page's content area**, horizontally centered; footnotes sit above it with the band gap (§4). Shown on screen and in print — identical pagination everywhere.
  
- Font: new `FontKey.COPYRIGHT`, small default (~8 pt), via `SystemPrefsKey.COPYRIGHT_FONT` / `COPYRIGHT_FONT_SIZE`. Not surfaced in UI.
  
- New `CopyrightComponent` (a `ScoreComponent` sibling of `FootnotesComponent`).
  
- The attribution block above the first staff is untouched (it carries composer/lyricist/dates/place only — no copyright).
  

**Font persistence**: both new fonts round-trip through `<miscellaneous-field>` entries following the existing `MISC_SUB_ATTRIBUTION_FONT` pattern (name + size fields each).
### 7. Printing
- New shared entry point, e.g. `ScoreView.paintPage(Graphics2D g2, int pageIndex)`: paints one page's full content — music, text blocks, footnotes, copyright, page number — at document scale (`ViewScale.IDENTITY`), with edit-time decorations (selection highlights, edit overlays, insertion cursors) suppressed via a render-mode flag. This method is designed as the future entry point for `PDFExporter` (PDFBox + `pdfbox-graphics2d`, follow-up issue) and `ImageExporter`.
  
- `MainFrame.print(Graphics, PageFormat, int pageIndex)`:
  
  - `pageIndex >= pageCount → NO_SUCH_PAGE`
    
  - `PageFormat`/`Paper` derived from the document's `paperSize` and margins (replacing the `PRINT_EXTRA_MARGIN` fudge)
    
  - scale/translate document px → the imageable area, then delegate to `paintPage`
    
- **Mirrored margins apply here only.** With `mirroredMargins` and more than one page: for each page, the content block is placed at the inner or outer margin according to page parity; `versoFirst` flips which parity is the left-hand (verso) page. A single-page document prints centered (the §3 centered-equivalent margins), matching the screen.
  
- `handlePrint()` flow (`PrinterJob` + OS dialog) is unchanged; the macOS print dialog's built-in Save-as-PDF covers PDF output until `PDFExporter` is implemented.
  
### 8. MusicXML persistence
**New format** (written by `MusicXmlHeaderWriter.writeDefaults`, read by `MusicXmlHeaderReader`):

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

- All values in tenths via `MusicXmlUnits` (inches → tenths through the fixed scaling). `paperSize` is recovered on read by matching width/height against the `PaperSize` table (nearest match).
  
- `versoFirst` has no native MusicXML slot → `<miscellaneous-field name="verso-first">` (written only when true).
  
- Line width is **no longer stored** — it is derived on load from page width minus margins.
  
- The fixed copyright constant is written into `<identification><rights>` (currently write-forward anyway); the reader continues to ignore it.
  
- `<credit page>` attributes remain `1` — credits are display-only and re-derived from head data on read.
  

**Legacy load** (both `.musicxml` without `<page-margins>` and `.mssw`):

- Detected by the **absence of** `<page-margins>` — no version marker.
  
- Old semantics apply: `<page-width>` is the line width. Recover:
  
  - `paperSize` ← `PrefsKey.PAGE_SIZE`
    
  - left = right = `(paperWidthInches − lineWidthInches) / 2`
    
  - top = bottom = 0.5", mirrored/versoFirst off
    
- Rendering is pixel-identical to today (the same centering math). The file upgrades to the new format on next save.
  
- The `SongLoadResult.LineWidthTooLarge` guard is kept, now checking the legacy line width against `paperWidthInches − 2 × MIN_MARGIN_INCHES`.
  
### 9. Page Setup dialog
New `PageSetupDialog extends StandardDialog`, category `EXCLUSIVE` (precedent: `SongSettingsDialog`), designed to read like a typical macOS Page Setup dialog — **not** modeled on the legacy GUI-Designer `PaperSizeStep`:

- Paper size combo (`PaperSize` list, dimensions shown in the active unit)
  
- Four margin fields with unit label (inch/cm per `PrefsKey.METRIC`, decimal filtering via `InputUtils.addDecimalFilter`)
  
- "Mirrored margins" checkbox — relabels Left/Right ↔ Inner/Outer
  
- "First page is left-hand (verso)" checkbox — enabled only when mirrored
  
- Read-only derived line width display, updating live
  
- Validation per §2 in `isValidData()`, error alerts via `OptionDialogs` (`alert.*` keys); `setData()` commits (§11)
  
- String keys under `dialog.page.setup.*` per the strings taxonomy
  

**Menu/action**: `Actions.PAGE_SETUP_ACTION` — a `DialogOpenAction<>` opening `PageSetupDialog`, accelerator ⇧⌘P, flags `DISABLE_WHEN_PLAYING` + `OPENS_DIALOG`, inserted in `MenuController.initFileMenu()` directly above `Actions.PRINT_ACTION` (macOS convention).
### 10. Status bar page indicator
- New `PageStatusBarPanel` (pattern: `ZoomStatusBarPanel`), added as a **center cell** in `StatusBar`'s `GridBagLayout`, horizontally centered in the status bar (note preview stays at `LINE_START`, zoom cluster at `LINE_END`).
  
- Shows "Page N of M" where N is the page under the viewport center; updates on scroll (viewport listener), repagination, and zoom.
  
- Clickable: opens a `JPopupMenu` listing pages 1…M; selecting one scrolls that page into view (pattern: the zoom `percentButton` popup).
  
- New `PaginationDidChangeNotification` (`songscribe.message.notification`, carries the page count) posted after repagination; the panel subscribes via `MessageCenter.subscribe(this)` and stays strongly reachable from `StatusBar`.
  
- Hidden or "Page 1 of 1" for single-page documents (implementation choice; showing it keeps layout stable).
  
### 11. Undo / mutations
New `LayoutField` entries (all validated `Object` old/new via the existing `LayoutChange` mutation): `PAPER_SIZE`, `TOP_MARGIN_INCHES`, `BOTTOM_MARGIN_INCHES`, `LEFT_MARGIN_INCHES`, `RIGHT_MARGIN_INCHES`, `MIRRORED_MARGINS`, `VERSO_FIRST`. (`LINE_WIDTH_SS` is removed.)

`PageSetupDialog.setData()` wraps all changed fields in **one**`Song.withModification(...)` bracket — one `SongDidChangeNotification`, one undo step reverting the whole Page Setup commit. Undo/redo of these mutations triggers repagination like any other layout change.
### 12. Cleanup
- `SongSettingsDialog` Music tab: the Line Width section is removed — `lineWidthField`, `unitLabel`, `LineWidthVerifier`, `validateLineWidth`/`validateLineWidthText`, `revertLineWidthField`, `showLineWidthError`, and the now-dead string keys (`dialog.song.settings.section.line.width`, `error.line.width.*`, `alert.title.line.width.error`; `label.width` only if unreferenced elsewhere).
  
- `PaperSizeStep` is removed from `ExportPDFDialog` (and deleted along with its strings if nothing else references it). `PageLayoutData` drops the dead `mirrored` and `songsPerPage` fields; when PDF export is implemented it will read the document's page setup instead.
  
- Stub exporters and their menu items are left untouched.
  

* * *
## What Is Removed
- `Song.setLineWidthSs` / stored `lineWidthSs` field (getter stays, derived)
  
- `LayoutField.LINE_WIDTH_SS`
  
- `PageModel.Size` (replaced by `PaperSize`), `MAX_LINE_WIDTH_INCHES`, pref-reading singleton behavior, margin-by-centering derivation
  
- `SongSettingsDialog` Line Width section + its strings
  
- `PaperSizeStep` (from the export flow; deleted if unreferenced)
  
- `PageLayoutData.mirrored`, `PageLayoutData.songsPerPage`
  
- `MainFrame.PRINT_EXTRA_MARGIN` and the print stub body
  
- Legacy `<page-width> = line width` **write** semantics (read path kept for legacy files)
  

* * *
## New / Modified Files
| File | Change |
|------|--------|
| `dom/Song.java` | **Modified** — page-setup fields, derived line width, mutation plumbing |
| `layout/PaperSize.java` | **New** — curated paper-size enum |
| `layout/Paginator.java` | **New** — page-break assignment engine |
| `layout/PageModel.java` | **Modified** — sourced from Song page setup; centered-equivalent margins |
| `ui/component/score/PageComponent.java` | **New** — one page surface; draws page number |
| `ui/component/score/CopyrightComponent.java` | **New** — pinned copyright line |
| `ui/component/ScoreView.java` | **Modified** — page stack, coordinate mapping, `paintPage`, repagination |
| `ui/component/score/MainPanel.java` / `StaffPanel.java` | **Modified/absorbed** — single-stack role replaced by per-page containers |
| `ui/component/MainFrame.java` | **Modified** — real multi-page `print(...)` |
| `ui/dialog/PageSetupDialog.java` | **New** — macOS-style page setup (StandardDialog, EXCLUSIVE) |
| `ui/dialog/SongSettingsDialog.java` | **Modified** — Line Width section removed |
| `ui/dialog/ExportPDFDialog.java` | **Modified** — PaperSizeStep removed |
| `ui/action/Actions.java`, `ui/menu/MenuController.java` | **Modified** — `PAGE_SETUP_ACTION` in File menu |
| `ui/component/StatusBar.java` | **Modified** — centered middle cell |
| `ui/component/PageStatusBarPanel.java` | **New** — indicator + jump popup |
| `message/notification/PaginationDidChangeNotification.java` | **New** |
| `message/mutation/LayoutField.java` | **Modified** — new fields, `LINE_WIDTH_SS` removed |
| `font/FontKey.java`, `font/DocumentFonts.java`, `prefs/SystemPrefsKey.java`, `conf/system-defaults.json` | **Modified** — `PAGE_NUMBER`, `COPYRIGHT` fonts |
| `io/musicxml/MusicXmlHeaderWriter.java` / `MusicXmlHeaderReader.java` / `MusicXmlTags.java` | **Modified** — real page-layout write/read, legacy fallback, font misc-fields, verso-first |
| `io/SongLoader.java` (`.mssw` path) | **Modified** — center-derive margins on legacy import |
| `export/PageLayoutData.java` | **Modified** — dead fields removed |
| `resources/songscribe/strings.properties` | **Modified** — dialog/copyright/indicator keys added; dead keys removed |

* * *
## Testing
Unit tests only (no new e2e in this issue; on-screen behavior and printing are verified manually):

- **Paginator** — break assignment across page counts; atomic `TextPanel` / footnotes push; footnote pinning above the copyright band; copyright band reservation on the last page; oversized-block own-page overflow; title block on page 1 only
  
- **Derivation math** — line width from paper size + margins; centered -equivalent margins under mirrored; print parity placement with and without `versoFirst`; single-page centered rule
  
- **Validation** — margin floor, minimum derived line width, per-unit (inch/cm) round-trips of the dialog values
  
- **MusicXML round-trip** — new `<page-layout>`/`<page-margins>` (both and odd/even forms), verso-first misc-field, page-number/copyright font misc-fields; nearest-`PaperSize` recovery
  
- **Legacy load** — old `.musicxml` (no `<page-margins>`) and `.mssw`: center-derived margins reproduce the stored line width exactly; `LineWidthTooLarge` guard against the new bound
  
- **Mutations** — Page Setup commit produces one bracket/notification; undo restores all fields and line width; `FieldTypeValidator` coverage for the new `LayoutField` entries
  

* * *
## Non-Goals (explicit follow-ups)
- PDF, image, and SVG export (PDFBox + `pdfbox-graphics2d`; `paintPage` is their designed entry point)
  
- Fit-page / fit-width zoom modes
  
- Facing-pages (side-by-side spread) screen view
  
- Manual page breaks and widow/orphan control
  
- Landscape orientation
  
- Third-party MusicXML import of page layout
  
- Running headers / top-of-page titles
  
- Preferences UI for default margins or default mirrored state
  
- Surfacing the page-number and copyright fonts for editing

---
comments:
  c2:
    body: Done — the indicator is now specified as a horizontally centered middle cell in StatusBar's GridBagLayout, with the note preview and zoom cluster unchanged at the edges.
    by: AI
    at: "2026-07-16T16:45:00.000Z"
    re: c1
