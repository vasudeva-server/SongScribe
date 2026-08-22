# Zoom Architecture

Zoom is **per-view state**, not part of the document scale. Read this guide before touching anything under `ui/ViewScale.java`, the `Ss`/`DocPx`/`ViewPx` records in `songscribe.dom`, `ScoreView`'s zoom-apply path, `ZoomController`, `PageModel`'s page-dimension getters, or `LineComponent.paintComponent`'s scale transform.

## The model

- **`ScaleContext`** — a **fixed document scale**. `pixelsPerStaffSpace` is always `ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE` (8.0); `ssToPx`/`pxToSs` never vary with on-screen zoom. This is the document's authoring scale — the same regardless of what any view currently shows.
- **`ViewScale`** (`songscribe.ui.ViewScale`) — per-view zoom state, owned by a single `ScoreView`. Holds `zoomPercent` (default 100) and `factor()` (`zoomPercent / 100.0`). Applied **only at view boundaries**: the paint transform, component preferred sizes, mouse-input conversion, overlay bounds, and page sizing. Nothing else knows about zoom.

Because `ScaleContext` is fixed, `Ss` staff-space distances and `DocPx` document pixels are two names for values at the *same* underlying scale — `DocPx` is just `Ss` after applying `ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE`. A `ViewScale` is the only thing that folds the current zoom on top, producing `ViewPx`.

## The three unit types (`songscribe.dom`)

| Type | Regime | Produced by |
|---|---|---|
| `Ss` | Staff spaces — the zoom- and device-independent layout unit | Layout code, DOM element geometry |
| `DocPx` | Document pixels at the fixed 100%-zoom document scale | `ScaleContext`, `PageModel` |
| `ViewPx` | On-screen pixels at the *current* view zoom | `ViewScale` conversions, Swing component geometry, mouse events |

All three are plain records (`record Ss(double value)`, etc.) — thin typed wrappers, not full value types. `DocPx` and `ViewPx` each expose two int accessors mirroring the crossing rules from [unit-conversion.md](unit-conversion.md#rounding-when-crossing-to-px):

- **`roundedPx()`** — nearest integer. Use for **positions** (coordinates, margins) so placement stays centered.
- **`ceilPx()`** — rounds up. Use for **sizes** (widths, heights) so content is never clipped at high zoom.

There is deliberately no single ambiguous `rounded()` — every call site must say which rule it means.

`Ss` has no int accessor: staff-space values stay in `double` until they cross into a pixel regime through `ScaleContext` or `ViewScale`.

## `ViewScale` conversions

```
ViewPx toViewPx(Ss ss)        Ss → ViewPx, folding in ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE and factor()
Ss     toSs(ViewPx viewPx)    inverse
ViewPx toViewPx(DocPx docPx)  DocPx → ViewPx, folding in factor() only
DocPx  toDocPx(ViewPx viewPx) inverse
Font   zoomedFont(Font base)  scales a font's point size by factor(); identity shortcut at 100%
```

`ViewScale.IDENTITY` is a shared, **read-only** instance (100% zoom) — see "Read-on-demand channel" below. Never call `setZoomPercent` on it.

`ViewScale` is EDT-only by contract: all reads and writes must happen on the AWT event-dispatch thread. No locking is performed.

## ViewScale data flow

```
                 ZoomController (static, stateless orchestrator)
                        │ zoomIn/out/reset/setZoomPercent
                        │   → reads active view's current percent; no-op when
                        │     no active view or the percent is unchanged
                        ▼
        MessageCenter.post(ZoomDidChangeNotification(old, new, anchor))
                        │
     ┌──────────────────┼──────────────────────────────────────────────────┐
     │ @Handler(priority = HIGH_PRIORITY)                                  │ @Handler (default priority)
     ▼                                                                     ▼
 ScoreView.zoomDidChangeApplyZoom                          ZoomAction enabled-state, ZoomStatusBarPanel,
   → applyZoomPercent(newPercent, anchor):                 ScoreView.zoomDidChangeRefreshOverlayBounds,
       capture anchor → viewScale.setZoomPercent           LyricEditor.zoomDidChange (if active)
       → layoutPage → invalidate tree → scrollPane.validate()
       → computeAnchoredViewPosition → setViewPosition
       → repaint
                        │
                        ▼
          score tree reads viewScale ON DEMAND:
   ScoreComponent.getViewScale() =
     scoreView != null ? scoreView.getViewScale() : ViewScale.IDENTITY
   ├─ LineComponent          — has scoreView backref (set in LinePanel ctor)
   ├─ LineInvariants         — captures score.getViewScale() per render
   ├─ header/lyric leaves    — inherit scoreView backref from ScoreComponent
   └─ containers (MainPanel, StaffPanel, ComponentHierarchyNavigator) — ctor-passed ScoreView/ViewScale
   detached previews (SongSettingsDialog) — no scoreView → IDENTITY (natural size)
```

`ScoreView.viewScale` is the **sole source of truth** for a view's zoom. `ZoomController` is a static, stateless orchestrator — it holds no zoom state of its own; it resolves the active `ScoreView` via the `MainFrame` singleton accessor and no-ops when there is none. It never calls `ScoreView` directly: every reactor to a zoom change — applying it, the status bar, action enablement, the active `LyricEditor`, overlay bounds — is a `@Handler` of the single `ZoomDidChangeNotification` `ZoomController` posts, sequenced by priority rather than split across direct calls and the bus. `ScoreView.zoomDidChangeApplyZoom` is the one handler that actually performs the change, at `Message.HIGH_PRIORITY`; **any new listener to `ZoomDidChangeNotification` MUST use a handler priority strictly less than `Message.HIGH_PRIORITY`** (a bare `@Handler`, priority 0, satisfies this) so it always observes the zoom as already applied — see the priority requirement documented on `ZoomDidChangeNotification` itself.

## Read-on-demand channel, not field propagation

`ViewScale` is never pushed into components as a mutable field. Instead, `ScoreComponent` holds a `@Nullable ScoreView scoreView` back-reference and exposes:

```java
public ViewScale getViewScale() {
    return scoreView != null ? scoreView.getViewScale() : ViewScale.IDENTITY;
}
```

Every on-score consumer reads the current zoom through this accessor at the moment it needs it, rather than caching a factor that could go stale. This matters because `LineComponent`s are created in `StaffPanel.rebuildLayout` → `new LinePanel` → `new LineComponent`, **not** through the `setSong` fan-out — a `setViewScale`-style push would leave a freshly rebuilt line rendering at 100% while the rest of the tree stayed zoomed. Read-on-demand makes that class of staleness structurally impossible.

Off-score consumers with no `ScoreView` (dialog previews, exporters) get `ViewScale.IDENTITY` and render at natural (document) size regardless of any live view's zoom — e.g. `SongSettingsDialog`'s title/subtitle previews are never given a `ScoreView`.

## The paint-pipeline: single factor application

```
LineComponent.paintComponent
    scale = ScaleContext.getPixelsPerStaffSpace() * getViewScale().factor()
    g2.scale(scale, scale)
        │
        ▼
    renderers draw entirely in Ss — they never re-multiply by factor()
        │
        ▼
    exception 1: the stripped-transform lyric path (LyricTextRenderer) draws
    outside the Ss transform, in pixel space, so it reads
    LineInvariants.getViewPixelsPerStaffSpace() (= pxPerSs × factor) directly

    exception 2: the attribution block (AttributionPane.render) also draws outside
    the Ss transform, in pixel space, and takes the factor as an explicit
    `zoomFactor` parameter
```

The zoom factor is applied **once**, at the `LineComponent` paint transform. Everything drawn inside that transform (via any renderer) works in `Ss` and must never multiply by a zoom factor again — doing so double-scales. There are exactly **two** sanctioned exceptions, both of which draw *outside* the `Ss` transform in pixel space and therefore have to reintroduce the factor by name:

1. **`LyricTextRenderer`** — strips the transform so painted lyrics land on the same baseline as the inline lyric editor, which is a Swing text component overlaid on the same spot. Drawing through the `Ss` scale takes a different float-ascent rounding path and shifts the baseline by up to one device pixel, so text would visibly jump on entering and leaving edit mode. Stripping the transform puts this renderer on the rasterization path `JTextField` uses — a font drawn at integer logical-pixel coordinates rather than through an arbitrary affine scale. It reads `LineInvariants.getViewPixelsPerStaffSpace()` (= `pxPerSs × factor`) to re-derive those coordinates. This is editor parity, **not** a general legibility win: no renderer without an overlaid editor has any reason to copy it.
2. **`AttributionPane.render`** — `AttributionPane` is not a `Component` at all (it is a plain class in `songscribe.dom`), so unlike `LineComponent`/`TranslationComponent`/`TextPanel` it never gets a graphics context of its own; whoever paints it decides what coordinate space it is handed. It has two consumers that agree on pixels: `LineComponent.render` calls it by hand after restoring the staff-space transform, and the Song Settings preview calls it from `AttributionPaneWidget.paintComponent`, which has no staff-space transform to work in. One pixel-based `render` therefore serves both. It receives the factor as an explicit `zoomFactor` parameter and applies it to its own staff-space spacing constants (`LEADING_SS`, `SUB_ATTRIBUTION_GAP_SS`) and margins, while the caller passes fonts already scaled through `zoomedFont`.

In both cases the carrier is *named* for what it is (`getViewPixelsPerStaffSpace`, `zoomFactor`), so it cannot be mistaken for a document-scale value.

`AttributionPane` lives in `songscribe.dom` and so must not reach for `ViewScale` itself — the factor is pushed in by the caller. Its **measurement** API is deliberately zoom-free: `getContentSizePx`/`getContentWidthPx`/`getContentHeightPx` always measure at `AttributionPane.NATURAL_ZOOM_FACTOR`, so the staff-space dimensions the layout reserves are zoom-invariant by construction. Only the render pass is zoomed. Off-score callers with no `ScoreView` (the settings-dialog preview) pass `NATURAL_ZOOM_FACTOR` explicitly.

## No typed units inside `paintComponent`

Typed units (`Ss`, `DocPx`, `ViewPx`) exist to guard the points where one unit regime **converts** into another — layout, measurement, mouse, and page boundaries — where getting the wrong unit is a real, easy-to-make bug. They are deliberately **not** used inside the per-frame paint path:

- `LineComponent.paintComponent` and everything it calls stay on plain-`double` `ssToPx`/`pxToSs` arithmetic.
- `ScaleContext`'s measurement helpers (`textWidthSs`, `fontAscentSs`, etc.) do return typed `Ss` at their own boundary, but callers inside or adjacent to the paint path unwrap immediately with `.value()` rather than threading the typed value further into paint-time math (see `AnnotationRenderer`, `MetronomeRenderer`).
- `scaleFont` keeps returning a plain `Font` — no typed wrapper.

This is a deliberate performance boundary: typed records are cheap but not free, and the paint path runs every frame. Introducing a typed unit there would be a correctness no-op with a needless allocation cost — flag it in review.

## Mouse input: converting out of view pixels at the entry point

Mouse events arrive in `ViewPx` (on-screen coordinates). Convert **once**, at the event entry point, so nothing downstream re-derives the zoom factor. Which regime you convert *to* depends on what the consumer works in:

- **Staff spaces** — `getViewScale().toSs(new ViewPx(...))`. This is the path for anything that answers questions against layout geometry, and it is the common case: `LineSelectionHandler`'s hit-testing entry points and its rubber-band sweep, `PreviewElementManager.trackMouse`, `InsertionPointMode.updateTarget`, and `NoteDragHandler.handleDrag` (which converts a screen-Y *delta* rather than an absolute point, but through the same call). Do not route these through document pixels first — an intermediate whole-document-pixel step rounds to the nearest pixel, and since the destination is a `double` staff-space value that rounding buys nothing and costs up to half a document pixel, which the zoom then magnifies on screen.
- **Document pixels** — `getViewScale().toDocPx(new ViewPx(...))`, for the rarer consumers that genuinely work in the DOM's `*Px` regime: `GraceModeManager`'s host-note lookup, and `ScoreView`'s page/export sizing.

## `roundedPx()` vs `ceilPx()` at the page seam

`PageModel`'s page-dimension getters (`getPageWidthPx`, `getPageHeightPx`, `getTopMarginPx`, `getBottomMarginPx`, `getHorizontalMarginPx`) return `DocPx`. `ScoreView.layoutPage` converts each through the view's `ViewScale`:

- Widths/heights (`pageWidthPx`, `pageHeightPx`) are **sizes** → `viewScale.toViewPx(...).ceilPx()`.
- Margins and positions (`topMarginPx`, `bottomMarginPx`, `horizontalMarginPx`) → `viewScale.toViewPx(...).roundedPx()`.

## Export sizing is zoom-independent by construction

`ScoreView.getSheetWidthPx()` reads `ScaleContext.ssToRoundedPx(getSong().getLineWidthSs())` — pure document-scale, never touches `ViewScale`. `getSheetHeightPx()` must likewise never derive its answer by dividing a zoomed, already-rounded/clamped on-screen value (e.g. `getHeight()`) back down by `factor()` — that round-trip loses information at the max-clamp in `layoutPage` and accumulates rounding error. Instead it reproduces the page/content-height arithmetic directly against `PageModel`'s `DocPx` getters, converting only the one still-necessary view-scaled read (the live content's preferred height) back to document space via the view's own `ViewScale.toDocPx`, before any clamping — never after. This keeps future exporters immune to whatever zoom level the view happens to be showing.

## See also

- [unit-conversion.md](unit-conversion.md) — the `Ss`/`Px`/`Sp` suffix conventions, `ScaleContext` converters, and the general staff-space-first authoring discipline. This guide covers the zoom layer that sits on top of it.
