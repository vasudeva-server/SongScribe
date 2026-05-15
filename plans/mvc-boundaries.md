# Plan: clarify Model / View / Controller boundaries

> **Status:** draft. Replaces `specs/song-score-architecture-epic.md`.

## Problem

`Song`, `Score`, and `ScoreMessageCoordinator` form an MVC triad in everything but name, but the responsibilities have leaked:

- **`Song` (model) carries AWT.** Twelve `java.awt.Font` / `FontMetrics` fields plus `initFontsFromPrefs`, which spins up a throwaway `BufferedImage` to derive `FontMetrics`. The file format already stores fonts as name+size strings (`ViewIO.writeView` → `getPSName()` + `getSize()`; `ViewIO.ViewReader.StringFont` reconstitutes via `MyFontUtils.createFont`), so the AWT objects on `Song` are derived rendering infrastructure, not document data.
- **`Score` (view) is its own controller.** Six `@Handler` methods (`musicSelectionDidChangeCacheTupletInfo`, `songDidChangeCacheTupletInfo`, `songDidChangeInvalidateLineLayouts`, `documentDidLoadCacheTupletInfo`, `prefsDidChange`, `textEditingDidChange`) and eight `canToggleBeaming` / `canToggleTie` / `canToggleTuplet` / `canRemoveDynamicsFromSelection` / `canMakeFirstSecondEnding` / `canChangeTempo` / `canToggleTrill` / `canFlipStemDirection` capability queries live on a `JComponent`. None are paint or geometry.
- **View-state is on the view.** `mode`, `control`, `horizontalAdjustment`, `verticalAdjustment` are fields on `Score`. Other consumers reach into `Score` to read them.
- **Duplicate subscription.** Both `Score` and `ScoreMessageCoordinator` subscribe to `SongDidChangeNotification`; together they traverse the line tree twice with overlapping invalidation.
- **`cachedTupletToggleInfo` is homeless.** It's view-state about model capability, cached on a `JComponent`, refreshed by three `@Handler` methods.
- **The coordinator is mis-named.** `ScoreMessageCoordinator` is already the controller (`handleToggleBeam`, `handleAddDynamics`, `handlePaste`, etc.); the name hides that.
- **The view is mis-named.** `Score` is a `JComponent` that paints. `ScoreView` would say so.

## Architectural stance

- **Top level: MVC.** `Song` is the model. `ScoreView` is the view. `ScoreViewController` is the controller. View-state lives in its own object that view and controller both read.
- **Inside the view: component tree.** `LineElement`, `StaffElement`, `Articulation`, `Line` and the rest remain a component tree, in the `JTable` / SwiftUI sense. Untouched by this plan.

The two patterns coexist at different scales.

## Out of scope

- Package reorganization (#377). Filed separately. The boundary cleanup is self-justifying.
- Moving `Song`'s `@Handler` methods off the model. They legitimately operate on Song's own line collection; subscribing to bus events is the input mechanism, not a smell.
- `SelectionCoordinator` changes. Selection already lives there; `inSelectMode` already lives there; no work needed.
- `previewElement` movement. Already lives on `editModeManager`; `Score`'s `setPreviewElement` is a pass-through.
- `withModification` / mutation-tracking redesign. Stays on `Song` as model bookkeeping.
- File format changes. The schema already stores name+size strings.
- Element tree (`LineElement` and subclasses).

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Rename Score → ScoreView and ScoreMessageCoordinator → ScoreViewController](#-phase-1-rename-score--scoreview-and-scoremessagecoordinator--scoreviewcontroller) | ✅ Done | — |
| 2 | Extract RenderResources — strip AWT off Song | — | split into 2a–2d below |
| 2a | [Introduce RenderResources as a parallel facade](#-phase-2a-introduce-renderresources-as-a-parallel-facade) | ⏳ Pending | — |
| 2b | [Migrate font / font-metrics callers to RenderResources](#-phase-2b-migrate-font--font-metrics-callers-to-renderresources) | ⏳ Pending | — |
| 2c | [Convert Song document fonts to name+size strings](#-phase-2c-convert-song-document-fonts-to-namesize-strings) | ⏳ Pending | — |
| 2d | [Delete residual AWT from Song](#-phase-2d-delete-residual-awt-from-song) | ⏳ Pending | — |
| 3 | [Extract ScoreViewState](#-phase-3-extract-scoreviewstate) | ⏳ Pending | — |
| 4 | [Move ScoreView's @Handlers and capability queries to ScoreViewController](#-phase-4-move-scoreviews-handlers-and-capability-queries-to-scoreviewcontroller) | ⏳ Pending | — |
| 5 | [Resolve duplicate SongDidChangeNotification subscription](#-phase-5-resolve-duplicate-songdidchangenotification-subscription) | ⏳ Pending | — |

Phases are independent except where noted under **BlockedBy**. Each ships as its own PR.

---

## ✅ Phase 1: Rename Score → ScoreView and ScoreMessageCoordinator → ScoreViewController

**Status:** Done  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — two atomic IDE renames, no semantics change.

### Purpose

Make the names tell the truth before any logic moves. All subsequent phases reference the new names.

### Tasks

1. `jet_brains_rename` on the `Score` class symbol → `ScoreView`. Updates all references (main + tests) atomically.
2. `jet_brains_rename` on the `ScoreMessageCoordinator` class symbol → `ScoreViewController`. Same.
3. Spot-check for stragglers that serena couldn't reach (Javadoc text, `.md` files referencing the class names, log messages, property keys). Update by hand.
4. Variable names like `score` and `messageCoordinator` are not renamed in this phase. If a callsite reads worse after the class rename, fix it locally in a later phase where you're already editing the file.

### Acceptance criteria

- No remaining `class Score` or `class ScoreMessageCoordinator` declarations.
- `./scripts/compile.sh` green.
- `./scripts/test.sh unit` green.
- No `.md` file in the repo (excluding this plan and the superseded spec) still uses the old names in a non-historical sense.

### Risks

Minimal. IntelliJ handles Java references including reflection-safe ones; the spot-check covers the rest.

---

## Phase 2 overview: Extract RenderResources — strip AWT off Song

`Song` should hold only what the document persists. Today the document persists font *name* and *size* strings (`ViewIO.writeView`, `ViewIO.ViewReader`), but `Song` holds resolved `java.awt.Font` and derived `java.awt.FontMetrics` instances. Move both off the model.

### Inventory (verified against source)

On `Song` today:

- Fields: `titleFont`, `titleFontMetrics`, `lyricsFont`, `lyricsFontMetrics`, `banglaFont`, `banglaFontMetrics`, `attributionFont`, `attributionFontMetrics`, `annotationFont`, `annotationFontMetrics`, `footnoteFont`, `footnoteFontMetrics`.
- Methods: `initFontsFromPrefs`, `getXFont()` × 6, `getXFontMetrics()` × 6, `setXFont(Font)` × 4 (title/lyrics/attribution/annotation only — bangla and footnote are hardcoded), `applyXFont(Font)` × 4, `mutateFont(FontField, Font, Font, …)`, the `fontDidChange` `@Handler`.

Two distinct categories:

1. **Document fonts** (title, lyrics, attribution, annotation). Persisted in `.mssw` as name+size strings. Can change at runtime via `FontDidChangeNotification`. Participate in mutation tracking.
2. **Hardcoded fonts** (bangla, footnote). `MyFontUtils.getLocalFont("TiroBangla-Regular.ttf", 17)` etc. Never persisted, never preference-driven, never mutated. Pure rendering infrastructure that happens to sit on `Song`.

The work splits into four sub-phases. Each ships as its own PR. The split isolates one Opus-grade design step (2a) and one Opus-grade semantic change (2c); the bookend sub-phases (2b, 2d) are mechanical and suited to Sonnet.

---

## ⏳ Phase 2a: Introduce RenderResources as a parallel facade

**Status:** Pending  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Opus 4.7, medium effort — one new class, ownership and lifetime decisions, MBassador subscription with weak-reference hazards. No existing callers move yet.

### Purpose

Stand up `RenderResources` alongside `Song` with a functionally equivalent API for all six fonts. After this sub-phase the codebase has two sources of truth for `Font` / `FontMetrics`; that duplication is temporary and goes away in 2d.

### Tasks

1. Define `RenderResources` under `songscribe.ui.render` (new package). Owns the six `Font` objects and six `FontMetrics` objects, including the two hardcoded ones (`bangla`, `footnote`) which never existed in `SongData`.
2. Bootstrap parallels `Song.initFontsFromPrefs`: read prefs for the four document fonts, hardcode the two non-document fonts, derive `FontMetrics` via a single static `BufferedImage`.
3. Subscribe to `FontDidChangeNotification`. On notification, rebuild the affected `Font` + `FontMetrics`.
4. Ownership: per-`MainFrame`, lifetime matches the open document. Stored as a field on `MainFrame`. Expose via `MainFrame.getRenderResources()`. Document the lifetime in the class Javadoc.
5. Verify strong reachability from `MainFrame` so MBassador's weak references do not silently drop the subscription. Add an integration-style smoke check if practical, but at minimum trace the reference chain by hand.
6. No callers change in this sub-phase. `Song` still owns its Font fields; `RenderResources` is dormant infrastructure until 2b wires it in.

### Acceptance criteria

- `RenderResources` class exists with `getXFont()` / `getXFontMetrics()` for all six fonts.
- `MainFrame` constructs one `RenderResources` per editor window and exposes it.
- `RenderResources` subscribes to `FontDidChangeNotification` and refreshes on receipt; reachability chain documented.
- `./scripts/compile.sh` green.
- `./scripts/test.sh unit` green.
- **Manual smoke test:** open a document, change a font preference, attach a debugger / log line in `RenderResources` to confirm its handler fires. Rendering still uses `Song`'s fonts at this point — that is expected.

### Risks

- **Subscriber lifetime.** MBassador holds weak references; verify the chain from `MainFrame` is strong. Silent failure mode: handler stops firing, no exception.
- **Drift between Song and RenderResources.** Until 2b lands, the two caches can disagree if a font changes and only one rebuilds. Acceptable for the duration of one sub-phase, but do not linger here.

---

## ⏳ Phase 2b: Migrate font / font-metrics callers to RenderResources

**Status:** Pending  <br>
**BlockedBy:** Phase 2a (`RenderResources.getXFont()` / `getXFontMetrics()` must exist).  <br>
**Recommended model/effort:** Sonnet 4.6, medium effort — mechanical find-and-replace driven by `jet_brains_find_referencing_symbols`. No semantic decisions.

### Purpose

Move every reader of `song.getXFont()` / `song.getXFontMetrics()` (all six fonts) to read from `RenderResources` instead. This is a pure caller migration; the methods on `Song` stay in place as no-longer-used accessors until 2d removes them.

### Tasks

1. For each of the six `Font` accessors and six `FontMetrics` accessors on `Song`, run `jet_brains_find_referencing_symbols` to enumerate callers.
2. For each caller, pass through a `RenderResources` reference (constructor injection where the wiring is local; `mainFrame.getRenderResources()` where the wiring would balloon — match what was decided in 2a's Javadoc).
3. Replace `song.getXFont()` → `renderResources.getXFont()` and `song.getXFontMetrics()` → `renderResources.getXFontMetrics()`.
4. Do not touch writers (`setXFont`, `applyXFont`, `mutateFont`) in this sub-phase — those stay on `Song` and flip in 2c.
5. Do not delete the now-unused accessors on `Song` in this sub-phase — that is 2d's job, after 2c has changed Song's field types.

### Acceptance criteria

- `jet_brains_find_referencing_symbols` on each of the six `getXFont()` and six `getXFontMetrics()` methods returns zero hits outside `Song.java` itself and any internal helpers.
- No behavioral change. Same fonts, same metrics, same rendering.
- `./scripts/compile.sh` green.
- `./scripts/test.sh unit` green.
- **Manual smoke test:** open a non-trivial `.mssw`, verify rendering is visually identical to pre-migration. Change a font preference, verify rendering updates (now driven by `RenderResources`'s subscription rather than `Song`'s).

### Risks

- **Missed caller.** A reference the IDE cannot reach (reflection, generated code) keeps reading from `Song` and silently disagrees with `RenderResources` after a font change. Mitigate with a final `search_for_pattern` for `getTitleFont`, `getLyricsFont`, etc., across all source.
- **Wiring churn.** If too many callers need `renderResources` plumbed in, prefer `MainFrame.getRenderResources()` over deep constructor threading. Stay consistent with the 2a decision.

---

## ⏳ Phase 2c: Convert Song document fonts to name+size strings

**Status:** Pending  <br>
**BlockedBy:** Phase 2b (Song's `Font` accessors must have zero external readers; only writers remain).  <br>
**Recommended model/effort:** Opus 4.7, high effort — touches `Song`, `SongData`, `SongIO`, `ViewIO`, the mutation pipeline, and `Song.fontDidChange`. File-format compatibility is at stake.

### Purpose

Replace `Song`'s four document-font `Font` fields with name+size string pairs, propagating through `SongData`, the file IO layer, the mutation pipeline, and the `fontDidChange` handler. The hardcoded bangla/footnote fields stay on `Song` for now — they have no callers (2b moved them) but their removal is mechanical and belongs in 2d.

### Tasks

1. **Change `Song` to store strings for document fonts.** Replace `Font titleFont` with `String titleFontName` and `int titleFontSize`; same for lyrics, attribution, annotation. Expose `getXFontName()` / `getXFontSize()`.
2. **Update `SongData`.** Change `@Nullable Font titleFont` → `@Nullable String titleFontName` and `@Nullable Integer titleFontSize`; same for the other three. Keep `@Nullable` so v1.0 files (no `<View>` section) still load.
3. **Update `SongIO`.** `defaultFontFromPrefs` returns a name+size pair instead of a `Font`. The four `viewReader.getXFont()` calls return name+size pairs; rename them to `getXFontName()` / `getXFontSize()` and stop eagerly resolving `StringFont` to `Font` via `getFont()`.
4. **Update `ViewIO.writeView`** to read name+size from `Song` directly. Eliminates the round-trip through `Font.getPSName()` / `Font.getSize()`.
5. **Migrate the mutation pipeline.** `setTitleFont(Font)` → `setTitleFont(String name, int size)`. `mutateFont(FontField, ...)` operates on name+size pairs. `applyTitleFont` writes the two string fields. `FontField` enum stays. `Song.fontDidChange` writes name+size instead of resolved `Font`.
6. Audit every `mutateFont` caller and confirm the new signature drops no information. `Font` has style flags (bold/italic); confirm none are in use beyond what `getPSName()` already encodes.

### Acceptance criteria

- `Song`'s four document-font fields are `String`/`int` pairs; `getXFontName()` / `getXFontSize()` are the public read API.
- `SongData` carries name+size pairs, not `Font` objects, for the four document fonts.
- The mutation pipeline (`setXFont`, `applyXFont`, `mutateFont`, `FontField`, `fontDidChange`) operates on name+size.
- v1.0 `.mssw` files (no `<View>` section) still load — `@Nullable` defaults fall through to prefs.
- Round-trip on a v1.1+ `.mssw` file with non-default fonts produces a byte-identical file (or differs only in whitespace/ordering; verify).
- `./scripts/compile.sh` green.
- `./scripts/test.sh unit` green.
- **Manual smoke test:** open a non-trivial `.mssw`, change a font preference, verify rendering updates immediately (driven by `RenderResources` from 2a/2b). Save. Reopen. Verify the changed font persists.
- **Manual smoke test:** load a v1.0 `.mssw` (if one exists in test fixtures), verify it still opens and renders.

### Risks

- **`mutateFont` semantics change.** Every call site flips from `Font` to strings. Inspect each one. Confirm undo/redo behavior survives — the `Mutation` record sees different types.
- **`Font.getPSName()` round-trip.** `writeView` currently writes the PostScript name; `MyFontUtils.createFont` accepts that. Confirm that going name → `Font` → name through `getPSName()` is idempotent for the fonts users pick in the UI. If not, the new direct-from-strings path may produce different bytes from the old persistence by accident.
- **File-format regressions.** This sub-phase changes what gets written to disk. Round-trip equivalence is the acceptance bar; do not merge without verifying it.

---

## ⏳ Phase 2d: Delete residual AWT from Song

**Status:** Pending  <br>
**BlockedBy:** Phase 2c (Song's document-font field types must already be strings).  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — mechanical deletion of now-unused fields, methods, and imports.

### Purpose

Strip the dead AWT off `Song`: the old `Font` and `FontMetrics` fields that survived as compatibility shims through 2b–2c, the six `getXFont()` / `getXFontMetrics()` accessors (now zero-caller after 2b), `initFontsFromPrefs`, and the two hardcoded bangla/footnote fields (already migrated functionally in 2a; their fields on `Song` are now unreferenced).

### Tasks

1. Run `jet_brains_find_referencing_symbols` on each of the six `getXFont()` and six `getXFontMetrics()` methods. Confirm zero external hits. `jet_brains_safe_delete` each one.
2. Delete the twelve AWT fields on `Song` (`titleFont`, `titleFontMetrics`, `banglaFont`, `banglaFontMetrics`, …).
3. Delete `Song.initFontsFromPrefs`.
4. Remove `java.awt.Font`, `java.awt.FontMetrics`, and `java.awt.image.BufferedImage` imports from `Song.java`.
5. Final sweep: `search_for_pattern` for `java.awt` inside `Song.java`. Should match nothing.

### Acceptance criteria

- `Song.java` has zero `java.awt.*` field types and zero `java.awt.*` imports.
- `Song.java` exposes `getXFontName()` / `getXFontSize()` for the four document fonts; the six `getXFont()` / `getXFontMetrics()` methods are gone.
- `RenderResources` is the sole owner of all six `Font` + `FontMetrics` instances.
- `./scripts/compile.sh` green.
- `./scripts/test.sh unit` green.
- **Manual smoke test:** sanity-render a document; nothing should look different from the end of 2c.

### Risks

Low. The deletions are mechanical because 2b removed external readers and 2c flipped the field types. The only hazard is a stray reflection-based reader; `jet_brains_safe_delete` reports usages instead of deleting if any remain.

---

## ⏳ Phase 3: Extract ScoreViewState

**Status:** Pending  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Sonnet 4.6, medium effort — small field extraction, but `ScoreActions` shrinks and callers update.

### Purpose

Give the editor's view-state a real home so the view and the controller can both read it without circular dependencies.

### Inventory (verified against source)

On `ScoreView` today:

- `mode` (`Mode`)
- `control` (`Control`)
- `horizontalAdjustment` (`int`)
- `verticalAdjustment` (`int`)

Already off `ScoreView`:

- `inSelectMode` → on `SelectionCoordinator`. No work needed.
- `previewElement` → on `editModeManager`. No work needed.

`ScoreActions` exposes `getControl` / `setControl` / `setPreviewElement` / `clearSelection` / `repaint` / `drawWidthIfWiderLine`.

### Tasks

1. Define `ScoreViewState` under `songscribe.ui.component`. Holds the four fields above with getters and setters. No bus subscriptions; no behavior beyond storage.
2. `MainFrame` owns the `ScoreViewState` instance (one per editor window).
3. `ScoreView`, `ScoreViewController`, and any other consumer hold a reference to `ScoreViewState`.
4. Remove `mode`, `control`, `horizontalAdjustment`, `verticalAdjustment` fields from `ScoreView`. The existing getters (`getMode` / `getControl` / `getHorizontalAdjustment` / `getVerticalAdjustment`) become pass-throughs to `ScoreViewState`, or callers move to read `ScoreViewState` directly.
5. Remove `getControl` and `setControl` from `ScoreActions`. Callers read `ScoreViewState` directly. `setPreviewElement`, `clearSelection`, `repaint`, `drawWidthIfWiderLine` stay (they're view-side actions, not state reads).

### Acceptance criteria

- `ScoreView.java` no longer has `mode`, `control`, `horizontalAdjustment`, or `verticalAdjustment` fields.
- `ScoreViewState` is the single source of truth for those four fields.
- `ScoreActions` is down to four methods: `clearSelection`, `repaint`, `setPreviewElement`, `drawWidthIfWiderLine`.
- `./scripts/compile.sh` green.
- `./scripts/test.sh unit` green.

### Risks

Low. Pure field extraction. The main hazard is forgetting a caller that reads `score.getMode()` — `jet_brains_find_referencing_symbols` on each accessor catches them.

---

## ⏳ Phase 4: Move ScoreView's @Handlers and capability queries to ScoreViewController

**Status:** Pending  <br>
**BlockedBy:** Phase 3 (`ScoreViewController` needs `ScoreViewState` to read the same view-state).  <br>
**Recommended model/effort:** Opus 4.7, medium-to-high effort — many references, but well-bounded. Subscriber lifetime and `cachedTupletToggleInfo` ownership require care.

### Purpose

Return `ScoreView` to its paint / geometry / export / `JComponent` lifecycle role. Move everything that's actually controller behavior to `ScoreViewController`.

### Inventory (verified against source)

On `ScoreView` today, moves to `ScoreViewController`:

- `@Handler` methods (six): `musicSelectionDidChangeCacheTupletInfo`, `songDidChangeCacheTupletInfo`, `songDidChangeInvalidateLineLayouts`, `documentDidLoadCacheTupletInfo`, `prefsDidChange`, `textEditingDidChange`.
- Capability methods (eight): `canToggleBeaming`, `canToggleTie`, `canToggleTuplet`, `canRemoveDynamicsFromSelection`, `canMakeFirstSecondEnding`, `canChangeTempo`, `canToggleTrill`, `canFlipStemDirection`.
- Field: `cachedTupletToggleInfo` (refreshed by three of the `@Handler` methods above; read by `canToggleTuplet`).
- Helper: `requireOperations()` if it's only used by the moved methods. Inspect before moving.

Stays on `ScoreView`:

- All paint / geometry / SVG export / `JComponent` lifecycle.
- `getPreviewElement` / `setPreviewElement` (pass-throughs to `editModeManager`; the field doesn't live on `ScoreView`).
- `clearSelection` / `deselect` (pass-throughs to `SelectionCoordinator`).
- The `selectionChanged` callback if it's purely repaint-driven.

### Tasks

1. Move the six `@Handler` methods to `ScoreViewController`. They already had access to `score` (now `scoreView`); update field types as needed. The two `cacheTupletToggleInfo` refreshers and the one `cacheTupletToggleInfo` invalidator move together with the `cachedTupletToggleInfo` field.
2. Move the field `cachedTupletToggleInfo` and the constant `TUPLET_INFO_CACHE_PRIORITY` to `ScoreViewController`.
3. Move the eight `canX` capability methods. They read selection (via `SelectionCoordinator`), the operations facade, and `cachedTupletToggleInfo` — all already reachable from `ScoreViewController`.
4. Update menu-enable callers (Edit menu actions, status-bar consumers) to query `ScoreViewController.canX()` instead of `ScoreView.canX()`. `jet_brains_find_referencing_symbols` on each method enumerates them.
5. Verify `ScoreViewController`'s lifetime: it must be strongly reachable from `MainFrame` (or `ScoreView`) for the duration of the open document, or MBassador's weak references will silently drop the new subscriptions.
6. If any `@Handler` method needs to mutate state on `ScoreView` (e.g. trigger `rebuildLyricRenderMetrics`, request a repaint), invoke it via `ScoreActions` rather than calling `scoreView` methods directly. This is what `ScoreActions` is for.

### Acceptance criteria

- `ScoreView.java` has zero `@Handler` annotations.
- `ScoreView.java` has zero `can*` capability methods.
- `cachedTupletToggleInfo` field is gone from `ScoreView.java`.
- All eight menu-enable callers have updated their query target.
- `./scripts/compile.sh` green.
- `./scripts/test.sh unit` green.
- **Manual smoke test:** select a note that supports beaming/tying/tuplet; verify the corresponding Edit menu items enable. Select a note that doesn't; verify they disable. Same for dynamics removal, first/second ending, tempo change, trill, stem flip.
- **Manual smoke test:** change a font preference; verify the score relayouts. Open a different document; verify capability queries refresh.

### Risks

- **Silent handler loss.** If `ScoreViewController` is reachable only via a weak collection or only via `score.messageCoordinator`, MBassador may GC it. Verify a strong reference chain from `MainFrame`.
- **Cache invalidation timing.** `cachedTupletToggleInfo` is refreshed by three different events; moving them all together preserves the contract, but watch for any caller of `canToggleTuplet` that previously relied on the field being eagerly populated before the `@Handler` fired.
- **`rebuildLyricRenderMetrics` and `viewChanged` couplings.** `songDidChangeInvalidateLineLayouts` calls `rebuildLyricRenderMetrics()` directly; once moved off `ScoreView`, the controller calls it through `ScoreActions` or a similar narrow seam. Confirm the seam exists or extend `ScoreActions`.

---

## ⏳ Phase 5: Resolve duplicate SongDidChangeNotification subscription

**Status:** Pending  <br>
**BlockedBy:** Phase 4 (the duplication disappears as a side effect; this phase is verification + any residual cleanup).  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — mostly verification of what Phase 4 already accomplished, plus any consolidation that didn't fall out cleanly.

### Purpose

Reduce `SongDidChangeNotification` to a single owner. Today both subscribers do partial line-layout invalidation:

- `ScoreView.songDidChangeInvalidateLineLayouts` — fires only on `FontChange`; calls `rebuildLyricRenderMetrics()` then iterates `getLineComponent(i)` to invalidate.
- `ScoreMessageCoordinator.songDidChange` — handles `hasLineLayoutMutation` (selective per-line) and `hasFullRelayoutMutation` (full `viewChanged()`); debounces repaints.

After Phase 4 the `ScoreView` handler is already moved. This phase confirms coverage.

### Tasks

1. Inspect the post-Phase-4 `ScoreViewController.songDidChange`. Confirm it handles the `FontChange` case: rebuilds lyric render metrics, invalidates line components.
2. If `FontChange` was not previously handled by `ScoreViewController.songDidChange`, fold its handling in. The shape: if `message.hasMutationOf(FontChange.class)`, call `scoreActions.rebuildLyricRenderMetrics()` (extend `ScoreActions` if needed) before invalidating line layouts.
3. Confirm exactly one `@Handler` for `SongDidChangeNotification` remains in the `ScoreView` / `ScoreViewController` cluster. `RenderResources` subscribes to `FontDidChangeNotification`, not `SongDidChangeNotification` — these are different events; do not conflate.
4. Audit the debounce timer (`repaintDebounceTimer`) — it lives on `ScoreViewController`; confirm it still fires after the move.

### Acceptance criteria

- Exactly one `@Handler` for `SongDidChangeNotification` in the `ScoreView` / `ScoreViewController` cluster.
- Font-change layout updates still work (the historically most fragile path).
- `./scripts/compile.sh` green.
- `./scripts/test.sh unit` green.
- **Manual smoke test:** change a font preference; verify the layout updates (not just the font, the line breaks should reflow if widths changed).

### Risks

- **Lost behavior.** The two handlers do subtly different things on overlapping events. Phase 4 moves one wholesale; if its `FontChange` branch isn't replicated in the surviving handler, font changes silently stop relayouting.
- **Debounce window changes.** Combining handlers may cause one rapid sequence of mutations to fire `viewChanged()` more often. Watch for paint thrash during font-preference scrubbing.

---

## Verification

Each phase ships its own PR, gated on `./scripts/compile.sh` green and `./scripts/test.sh unit` green. Manual smoke tests are called out per phase. Do not merge a phase whose manual smoke test failed; weak references in MBassador mean a regression here is silent.

## Notes for reviewers

- Each phase's PR diff should be readable in one sitting. If a phase produces a diff that doesn't, split inside the phase.
- `MBassador` weak references are the dominant silent-failure mode in this refactor. When a class stops subscribing or starts subscribing, audit its strong-reachability chain.
- `Song.withModification` and the mutation API stay on `Song`. Resist moving them.
- `Song`'s `@Handler` methods stay on `Song`. They operate on `Song`'s own line collection; that's model behavior, not controller behavior.
