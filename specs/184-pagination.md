# Pagination Support
Introduce real pages: on-screen pagination with separate page surfaces, true multi-page printing, page numbers, and a bottom-of-last-page copyright line.

Page geometry stays exactly as it is today — paper size from `PrefsKey.PAGE_SIZE`, fixed 0.5″ vertical margins, horizontal margins centered on the stored `Song.lineWidthSs`. Making margins authoritative and adding a Page Setup dialog is a separate issue: see `specs/184b-page-setup.md`.

**Issue:** vasudeva-server/SongScribe#184

* * *
## Goals
1. **On-screen pagination** — separate white page surfaces stacked vertically with gaps, replacing the single ever-growing canvas
  
2. **Multi-page printing** — a real `Printable` implementation painting each page through a shared per-page paint method
  
3. **Page numbers** — plain numeral, bottom center, pages 2+ only, drawn inside the bottom margin
  
4. **Copyright line** — fixed text pinned at the bottom of the last page's content area, on screen and in print
  
5. **Status bar page indicator** — "Page N of M" with a click-to-jump popup
  

* * *
## Prerequisite
`plans/overlay-components.md` **must land first.** This spec assumes `LineOverlayComponent` and its subclasses exist and that `LineOverlayPainter` is gone.

That ordering shrinks the work here rather than adding to it:

- It deletes `LineOverlayPainter.paintOnLine`'s translate-and-scale and `LineComponent.repaintWithOverlayHeadroom`'s rectangle inflation, both of which would otherwise need auditing against page boundaries — a line near a page bottom inflates its dirty rect into the inter-page gap and onto the next page surface.
  
- It makes print-time overlay suppression (§6) "skip the overlay children" instead of threading a render-mode flag through paint code.
  
- Overlays become children of the page containing their target line, so overlay ink can never spill onto the gap or an adjacent page. `LineOverlayComponent` already holds its host as a `JComponent` and supports runtime re-homing for exactly this reason.
  

`plans/overlay-components.md` phase 1 task 1 (`ScoreView` → null layout with an explicit `doLayout()`) is discarded by §4 below; its own plan already records that.

* * *
## Current State
### Layout and rendering
- `ScoreView` (inside `JScrollPane` → `ScorePanel`) is a single white `JComponent` sized to one page width whose height grows without bound: `getSheetHeightPx()` (`ScoreView.java:932-950`) returns `max(pageHeightPx, contentHeight + margins)`. Margins are an `EmptyBorder` set in `layoutPage` (`ScoreView.java:1084-1123`).
  
- `MainPanel` (`BoxLayout.Y_AXIS`, `MainPanel.java:92`) stacks `TitleComponent` → `SubtitleComponent` → `ScoreMarginStrut` (`SCORE_MARGIN_TOP_SS = 1.5`) → `StaffPanel` → `TextPanel` → `FootnotesComponent`. `MainPanel.getPreferredSize()` (lines 276-303) manually sums child heights. No page-break concept exists anywhere.
  
- `StaffPanel` uses the custom `StaffLinesLayout` and holds `List<LinePanel> linePanels` (`StaffPanel.java:52`). `rebuildLayout()` (lines 116-139) does `removeAll()` + `linePanels.clear()` and constructs a fresh `LinePanel` per line — there is no incremental add/remove.
  
- `StaffPanel.ensureAllLineLayouts()` (lines 186-198) measures lines **sequentially, threading** `hasLeadingLyricContinuation` **across them** for melisma continuation. Line heights are therefore order-dependent, not independent.
  
- Hit-testing is `MainPanel.getLinePanelAt(Point)` (lines 250-263) → `StaffPanel.getLinePanelAt` (lines 169-178, linear bounds scan). `ScoreView` performs no mouse→model mapping of its own.
  
- `PageModel` (all-static, `layout/PageModel.java`) is the page-geometry authority: `Size` enum from `PrefsKey.PAGE_SIZE`, `VERTICAL_MARGIN_INCHES = 0.5`, and `getHorizontalMarginPx(lineWidthPx)` which centers the line width. `getPageWidthPx()`/`getPageHeightPx()` are documented as fixed document scale, independent of view zoom.
  
- Zoom is per-view via `ViewScale` (`Ss` / `DocPx` / `ViewPx`); `ViewScale.IDENTITY` serves off-screen consumers.
  
### Print
- `MainFrame implements Printable`; `print(...)` (`MainFrame.java:952-990`) is a stub hardcoded to one page (`pageIndex >= 1 → NO_SUCH_PAGE`) drawing `Strings.ERROR_PRINT_NOT_IMPLEMENTED` at fixed coordinates. `PRINT_EXTRA_MARGIN = 0.25 * 72` (line 141) is a fudge inset.
  
- `handlePrint()` (lines 934-949) drives `PrinterJob` + the OS dialog and is unchanged by this issue.
  
- `PDFExporter`, `ImageExporter`, `SVGExporter` are stubs and stay stubs.
  
### Package boundaries (enforced by tests)
`PackageDependencyTest` (`src/test/java/songscribe/PackageDependencyTest.java`) asserts:

- `songscribe.dom` must not import `songscribe.layout` (line 63)
  
- `songscribe.dom` must not import `songscribe.ui` (line 70)
  
- `songscribe.layout` must not import `songscribe.ui` (line 79)
  

The third constrains `Paginator`'s input type (§2).

* * *
## Design
### 1. Page geometry
`PageModel` keeps its current shape and its current meaning. One addition:

- `getContentHeightPx()` — page height minus top and bottom margins, in `DocPx`. This is the pagination unit of measure.
  

Nothing else in `PageModel` changes. Paper size still comes from `PrefsKey.PAGE_SIZE`, vertical margins are still fixed at `VERTICAL_MARGIN_INCHES`, horizontal margins are still centered on `Song.lineWidthSs`, and `Song.lineWidthSs` remains stored and user-settable through `SongSettingsDialog`'s Music tab.
### 2. Pagination engine
New class `Paginator` in `songscribe.layout`. **Pure arithmetic — it must not import** `songscribe.ui`**.**

**Input:** an ordered `List<Block>`, where `Block` is a record declared in `songscribe.layout`:

```java
record Block(double heightPx, Kind kind) { }

enum Kind {
    PAGE_ONE_ONLY,       // title + subtitle
    NORMAL,              // strut, each LinePanel, TextPanel
    PINNED_LAST_PAGE     // footnotes, copyright
}
```

plus the page content height in `DocPx`.

**Output:** a `Pagination` result carrying, per block index, its page index; and the total page count.

Callers map indices back to components. No Swing type crosses this boundary — this keeps `Paginator` headless-testable and satisfies `layoutMustNotImportUi`.

**Block order:**

```
  index  kind              source
  ─────  ────────────────  ────────────────────────────────
   0     PAGE_ONE_ONLY     TitleComponent + SubtitleComponent (one block)
   1     NORMAL            ScoreMarginStrut (SCORE_MARGIN_TOP_SS)
   2..n  NORMAL            one per LinePanel — never split
   n+1   NORMAL            TextPanel (under-lyrics/Bangla/translation), atomic
   n+2   PINNED_LAST_PAGE  FootnotesComponent
   n+3   PINNED_LAST_PAGE  CopyrightComponent
```

**Pinned blocks are ordinary blocks.** Their heights come from `getPreferredSize().height` on the same components, measured the same way as every other block. Only their _placement rule_ differs. There is no separate band formula anywhere — a second source of truth for the copyright band height would drift silently against what `CopyrightComponent` actually renders.

**Algorithm — greedy fill, two passes, fully automatic:**

```
  PASS 1 ── place NORMAL and PAGE_ONE_ONLY blocks greedily
            capacity per page = getContentHeightPx()
            a block that does not fit moves WHOLE to the next page
            → yields provisional last page P

  PASS 2 ── reduce page P's capacity by the pinned-block total
            (footnotes + copyright, bottom-aligned in that order)
            re-run the greedy fill
            → if the last block is pushed off P, the last page becomes P+1
              and the pinned blocks go there instead

  STOP. Two passes, always. Pass 2's result is final.
```

The two-pass structure exists because the reservation is circular: the pinned blocks reduce the **last** page's capacity, but which page is last depends on capacity. A naive single pass oscillates:

```
  content exactly fills page 3's content height
  ├─ reserve pinned blocks on p3  → last block no longer fits → pushed to p4
  ├─ p4 is now last               → p3 needs no reservation → block fits on p3
  └─ ⟲ oscillates forever
```

Capping at two passes terminates by construction — the reservation only ever pushes content forward, and pass 2 is accepted as final. The cost is that page P may end with a small gap at its bottom. That is correct and stable output, and it is what the tests assert.

**Placement rules:**

- `PAGE_ONE_ONLY` blocks are placed on page 1 and nowhere else.
  
- `PINNED_LAST_PAGE` blocks are bottom-aligned on the final page in list order (footnotes above copyright, separated by a named `Ss` gap constant reusing the scale of `FOOTNOTES_MIN_MARGIN_TOP_SS`), regardless of where content ends.
  
- **Oversized atomic block** — a block taller than a full page's content area gets its own page and overflows. Because a page surface is a fixed size (§4), the page edge _is_ the clip; no extra code is required. Degenerate input, degenerate output.
  
- No widow/orphan rules, no manual breaks.
  

Give `Paginator` a class-level ASCII comment carrying the two-pass diagram above.
### 3. Height measurement ownership
**Measurement stays centralized.** `PageComponent`**s never measure.**

Line heights are order-dependent: `StaffPanel.ensureAllLineLayouts()` threads `hasLeadingLyricContinuation` across lines in sequence. If each page measured its own lines, continuation state would reset at every page boundary and produce heights different from the ones pagination was computed from — a silent wrong-height bug with no visible cause.

```
  MEASURE (unchanged)              PAGINATE                POSITION
  ───────────────────              ────────                ────────
  one owner holds the       →      Paginator        →      PageComponents
  ordered List<LinePanel>          (pure math over         place the
  and runs ensureAll-              heights, §2)            existing
  LineLayouts() over the                                   instances
  WHOLE song, in order
```

One component keeps the ordered `LinePanel` list and runs the full-song measurement pass before pagination, exactly as `StaffPanel` does today. `PageComponent`s are pure positioning containers. This makes pagination a _re-slice_ of an unchanged measurement pass and is what allows `Paginator` to be a pure function.

`MainPanel`/`StaffPanel`'s role as the single vertical stack is absorbed by the per-page containers, but the measurement pass itself does not move.
### 4. On-screen page surfaces
```
  BEFORE                              AFTER
  ──────                              ─────
  JScrollPane                         JScrollPane
   └ ScorePanel (gray)                 └ ScorePanel (gray)
      └ ScoreView (white, 1 sheet)        ├ PageComponent 1 (white)
         └ MainPanel [BoxLayout]          │   ├ content children
            ├ TitleComponent              │   └ LineOverlayComponent(s)
            ├ SubtitleComponent           ├ ── gap ──
            ├ ScoreMarginStrut            ├ PageComponent 2 (white)
            ├ StaffPanel                  │   └ …
            │  └ LinePanel×N              ├ ── gap ──
            ├ TextPanel                   └ PageComponent 3 (white)
            └ FootnotesComponent              └ …, CopyrightComponent
```

- `ScorePanel` (gray, existing) hosts one `PageComponent` per page, stacked vertically with a named gap constant between them.
  
- `PageComponent` is white, view-scaled to the page size, with `EmptyBorder` margins. It draws its own page number (§5).
  
- **Sizing is O(1).** `PageComponent.getPreferredSize()` is computed from `PageModel` + `ViewScale` alone — a page's size is fixed by paper size × zoom and is completely independent of its children. Do **not** copy `MainPanel.getPreferredSize()`'s child-summing pattern (`MainPanel.java:276-303`); that is correct for an unbounded sheet and wrong for a fixed page. `ScorePanel`'s preferred size is `pageCount × (pageHeight + gap)` — also O(1).
  
- `PageComponent` must return `false` from `isOptimizedDrawingEnabled()`. It has overlapping children (overlays), and the honest declaration is what lets Swing compute their dirty regions. `ScoreView` returns `false` today for the same reason (`ScoreView.java:660-663`).
  
- **Rebuild and repaginate are distinct operations.**
  
  - **Rebuild** — the song's structure changed. The measurement owner recreates the ordered `LinePanel` list, as `StaffPanel.rebuildLayout()` does today.
    
  - **Repaginate** — the page assignment of _existing_ `LinePanel` instances changed. Repagination **reparents**; it never constructs a component.
    
  
  This is not only a cost question. Overlay re-homing is defined as moving an overlay to its target line's new page; if repagination destroyed and recreated the `LinePanel`, there would be no line to re-home to and `LineOverlayComponent`'s `isDescendingFrom` guard would simply hide the overlay every time.
  
- **Reparenting is batched.** Perform all `remove`/`add` calls first, then a **single** `revalidate()` + `repaint()` on `ScorePanel`. Never revalidate inside the loop. Re-home overlays once after the loop completes, not per hierarchy event.
  
- **Coordinate mapping uses ancestor-agnostic Swing conversion — no page-origin arithmetic anywhere.** Hit-testing goes through `SwingUtilities.convertPoint` / `Container.findComponentAt` from `ScorePanel`; overlays already convert via `SwingUtilities.convertPoint(line, 0, 0, host)`, which is valid at any ancestor depth. Introducing a page-origin offset layer would create exactly the class of bug that is silent and untestable headlessly.
  
  Three sites are replaced wholesale and are the real work here:
  
  | Site | Disposition |
  | --- | --- |
  | `ScoreView.getSheetHeightPx()` (`932-950`, already TODO'd) | replaced by the page stack's O(1) size |
  | `ScoreView.layoutPage` (`1084-1123`) | replaced by per-page layout |
  | `ScoreView.applyZoomPercent` scroll anchoring (`1188-1220`) | must survive page gaps **and page-count change** |
  
  The remaining `DocumentScale.ssToPx(lineWidthSs)` conversions in `ScoreView` are pure scale, page-independent, and untouched.
  
- **Scroll anchoring** currently converts a viewport anchor against a single canvas (`convertPoint(viewport, anchorViewportOffset, this)`). It must handle the case where the page **count** changed between anchor capture and restore, not merely the presence of gaps — otherwise the view resolves the anchor against a stale stack and jumps to an unrelated page.
  
- **Zoom** continues to work through `ViewScale` exactly as today; page surfaces scale like the current sheet does.
  
- The class-level hierarchy comment at `ScoreView.java:99-111` documents the old tree and is invalidated by this change. Update it in the same commit.
  
### 5. Page numbers and copyright
**Page number**

- Pages 2+ only; page 1 — and therefore any single-page document — never shows a number.
  
- Plain numeral (`"2"`), horizontally centered, drawn **inside the bottom margin**, vertically centered in it. The content area is not reduced.
  
- Font: new `FontKey.PAGE_NUMBER` with `SystemPrefsKey.PAGE_NUMBER_FONT` / `PAGE_NUMBER_FONT_SIZE` defaults in `system-defaults.json`.
  
- Rendered on screen and in print.
  

**Copyright line**

- Fixed application constant (a `strings.properties` key, e.g. `song.copyright.notice`): `© Sri Chinmoy Songs. All rights reserved under CC BY-NC-ND 4.0`
  
- New `CopyrightComponent` (a `ScoreComponent` sibling of `FootnotesComponent`), pinned at the bottom of the **last page's content area**, horizontally centered, with footnotes above it (§2).
  
- Font: new `FontKey.COPYRIGHT`, small default (~8 pt), via `SystemPrefsKey.COPYRIGHT_FONT` / `COPYRIGHT_FONT_SIZE`.
  
- Shown on screen and in print — pagination is identical in both.
  
- The attribution block above the first staff is untouched; it carries composer/lyricist/dates/place only.
  

**Neither font is persisted per document.** Neither is surfaced in any dialog, so there is no way for a document to differ from the system default — round-tripping them through `<miscellaneous-field>` would serialize a constant and add round-trip surface guarding nothing. `system-defaults.json` alone. Persistence is added when the fonts become editable (tracked separately).
### 6. Printing
- New shared entry point, e.g. `ScoreView.paintPage(Graphics2D g2, int pageIndex)`: paints one page's full content — music, text blocks, footnotes, copyright, page number — at document scale (`ViewScale.IDENTITY`).
  
  Edit-time decorations are suppressed by **painting the page's content children and skipping its overlay children**. Because the overlays are separate child components (see Prerequisite), no render-mode flag needs to reach inside them. Selection highlights and insertion cursors drawn by `drawEditElements` are suppressed by not invoking that path.
  
  This method is the designed entry point for `PDFExporter` and `ImageExporter` when those are implemented.
  
- `MainFrame.print(Graphics, PageFormat, int pageIndex)`:
  
  - `pageIndex >= pageCount → NO_SUCH_PAGE`
    
  - `PageFormat`/`Paper` derived from `PageModel`'s page size and margins, replacing the `PRINT_EXTRA_MARGIN` fudge
    
  - scale/translate document px → the imageable area, then delegate to `paintPage`
    
- `handlePrint()` (`MainFrame.java:934-949`) is unchanged; the macOS print dialog's built-in Save-as-PDF covers PDF output until `PDFExporter` is implemented.
  
### 7. Status bar page indicator
- New `PageStatusBarPanel` (pattern: `ZoomStatusBarPanel`), added as a **center cell** in `StatusBar`'s `GridBagLayout`. The note preview stays at `LINE_START` (`gridx=0`) and the zoom cluster at `LINE_END`; the new panel is horizontally centered between them.
  
- Shows "Page N of M" where N is the page under the viewport center. Updates on scroll (viewport listener), on repagination, and on zoom — zoom moves the viewport center over a different page, which is a scroll concern, not a pagination one.
  
- Clickable: opens a `JPopupMenu` listing pages 1…M; selecting one scrolls that page into view (pattern: the zoom `percentButton` popup).
  
- New `PaginationDidChangeNotification` (`songscribe.message.notification`, carries the page count) posted after a repagination **that changed something** (§8). The panel subscribes via `MessageCenter.subscribe(this)` and stays strongly reachable from `StatusBar`.
  
- Shows "Page 1 of 1" for single-page documents, which keeps status bar layout stable.
  
### 8. Repagination triggers
**Repaginate on every** `SongDidChangeNotification`**, then diff and no-op if nothing moved.**

Frequency is already bounded: `Song.endModification()` posts exactly one notification per outermost `withModification` bracket — one per user operation. `Paginator` itself is O(blocks) arithmetic over tens of blocks. The cost is reparenting, so that is what the guard protects:

```
  SongDidChangeNotification
        │
        ▼
  measure (§3) ──► Paginator (§2) ──► diff vs previous assignment
                                            │
                        ┌───────────────────┴───────────────────┐
                        ▼                                       ▼
                  UNCHANGED                                  CHANGED
                  do nothing:                          batch reparent (§4),
                  no reparent,                         single revalidate,
                  no notification                      re-home overlays,
                                                       post PaginationDidChange
```

Do **not** allow-list "mutation types that affect content heights". Such a list rots silently the day a new mutation type is added, and the symptom is stale pagination with no error.

**Zoom does not trigger repagination.** Page content height and block heights are both document-space quantities — `PageModel.getPageWidthPx()/getPageHeightPx()` are documented as independent of view zoom — so zoom scales content and page uniformly and the page assignment is mathematically invariant under it. Repaginating on zoom would additionally fire on every tick of `ZoomStatusBarPanel`'s slider, which drives `ZoomController.setZoomPercent` continuously during drag. Zoom re-lays-out and re-sizes the existing page stack; it never repaginates.
### 9. What is not changed
Explicitly out of scope for this issue, to keep the diff bounded:

- `Song.lineWidthSs` — still stored, still mutation-tracked via `LayoutField.LINE_WIDTH_SS`, still written by `SongSettingsDialog`'s Line Width section through `ScoreView.updatePageLayout(int)`.
  
- All MusicXML read/write paths.
  
- `LayoutField`, `LayoutChange`, `MutationReplayer`, `LayoutDidChangeNotification`.
  
- `PageModel.Size`, `MIN_LINE_WIDTH_INCHES`, `MAX_LINE_WIDTH_INCHES`, `getHorizontalMarginPx`.
  
- `PaperSizeStep`, `ExportPDFDialog`, `PageLayoutData`.
  
- Stub exporters and their menu items.
  

* * *
## What Is Removed
- `MainFrame.PRINT_EXTRA_MARGIN` and the print stub body
  
- `ScoreView.getSheetHeightPx()` and `getSheetHeightPx(ExportOptions)` (both replaced by the page stack's size)
  
- `ScoreView.layoutPage(int)`'s single-sheet sizing and `EmptyBorder` margin logic
  
- `MainPanel`/`StaffPanel`'s role as the single vertical stack (the component classes and the measurement pass survive; see §3)
  

* * *
## New / Modified Files
| File | Change |
|------|--------|
| `layout/Paginator.java` | **New** — pure page-break engine; `Block` record + `Kind` enum; two-pass algorithm |
| `layout/PageModel.java` | **Modified** — add `getContentHeightPx()` |
| `ui/component/score/PageComponent.java` | **New** — one page surface; O(1) size; draws page number; hosts overlays; `isOptimizedDrawingEnabled()` returns `false` |
| `ui/component/score/CopyrightComponent.java` | **New** — pinned copyright line |
| `ui/component/ScoreView.java` | **Modified** — page stack, `paintPage`, repagination, batched reparenting; update the hierarchy comment at lines 99-111 |
| `ui/component/score/MainPanel.java` / `StaffPanel.java` | **Modified** — single-stack role absorbed by per-page containers; measurement pass unchanged |
| `ui/component/score/LineOverlayComponent.java` and subclasses | **Modified** — re-home from `ScoreView` to the owning `PageComponent`; re-home on repagination |
| `ui/component/MainFrame.java` | **Modified** — real multi-page `print(...)`; `PRINT_EXTRA_MARGIN` removed |
| `ui/component/StatusBar.java` | **Modified** — centered middle cell |
| `ui/component/PageStatusBarPanel.java` | **New** — indicator + jump popup |
| `message/notification/PaginationDidChangeNotification.java` | **New** |
| `font/FontKey.java`, `font/DocumentFonts.java`, `prefs/SystemPrefsKey.java`, `conf/system-defaults.json` | **Modified** — `PAGE_NUMBER`, `COPYRIGHT` fonts (system defaults only, not persisted) |
| `resources/songscribe/strings.properties` | **Modified** — copyright notice + page indicator keys |

* * *
## Testing
Unit tests only. Everything below runs headless — `JComponent` subclasses construct, size, and lay out without a display; only `JFrame`/`Window`/visible dialogs require one. `@RequiresDisplay` is reserved for the four items in the manual list.
### `Paginator` — pure math
- greedy fill, blocks placed in order
  
- a block that overflows moves **whole** to the next page
  
- `PINNED_LAST_PAGE` blocks bottom-aligned on the final page, footnotes above copyright
  
- **two-pass fixpoint**: content that exactly fills page N without the pinned blocks — assert the result is the pushed-to-N+1 layout, and that a third pass would change nothing
  
- **exact-fit boundary**: block height exactly equals remaining capacity (the `<=` vs `<` case)
  
- oversized block gets its own page and overflows
  
- `PAGE_ONE_ONLY` block on page 1 only
  
- empty song and single-block song each yield exactly 1 page — no page 2, no page number
  
- `PageModel.getContentHeightPx()` = page height − top − bottom, in `DocPx`
  
### Print — `Printable` against a `BufferedImage`
- `pageIndex >= pageCount → NO_SUCH_PAGE`, and `pageIndex < pageCount → PAGE_EXISTS`
  
- `PageFormat`/`Paper` imageable area derived from `PageModel`
  
- page N paints page N's blocks (recording `Graphics2D`, or a coarse ink check)
  
- `paintPage` skips overlay children
  

These are the highest-consequence branches in the change: a wrong page-count bound produces a runaway print job or a silently truncated score, and a broken overlay skip prints edit decorations onto paper. Neither is caught by eyeballing a test print.
### Component tree — headless `JComponent`
- repagination **reparents the same instances** — capture `LinePanel` identity before and after and assert it is unchanged (guards §4's rebuild/repaginate split)
  
- the pinned-block height `Paginator` reserved equals `CopyrightComponent.getPreferredSize().height` (guards §2's single measurement path against silent drift)
  
### Messaging
- `PaginationDidChangeNotification` fires **exactly once** per repagination that changed the assignment, and **zero** times when the assignment is unchanged
  
- it does **not** fire on zoom (guards §8's invariant)
  
### Manual / `@RequiresDisplay`
- scroll anchoring across page gaps, including when the page count changes
  
- indicator tracks the page under the viewport center
  
- jump popup scrolls the selected page into view
  
- visual fidelity of printed output
  

* * *
## Non-Goals (explicit follow-ups)
- Page Setup: paper size, authoritative margins, derived line width, mirrored/verso margins, the Page Setup dialog, `<page-margins>` persistence — `specs/184b-page-setup.md`
  
- Per-document persistence of the page-number and copyright fonts, and surfacing them for editing
  
- PDF, image, and SVG export (PDFBox + `pdfbox-graphics2d`; `paintPage` is their designed entry point)
  
- Fit-page / fit-width zoom modes
  
- Facing-pages (side-by-side spread) screen view
  
- Manual page breaks and widow/orphan control
  
- Landscape orientation
  
- Running headers / top-of-page titles
