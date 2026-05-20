# Sub-plan: Split `ElementRenderContext` by Lifetime

**Type:** Sub-plan  <br>
**Parent:** [rendering-rewrite.md](rendering-rewrite.md) → Phase 8  <br>
**Created:** 2026-05-19  <br>
**Status:** Done  <br>
**BlockedBy:** —

**Spec:** [specs/rendering-rewrite.md](../../../docs/specs/rendering-rewrite.md) — read the spec before implementing tasks.
**Issue:** [#369](https://github.com/vasudeva-server/SongScribe/issues/369)

---

## Status Dashboard

| Phase | Description | Status |
|-------|-------------|--------|
| 1a | [Create `LineInvariants` + `ElementFrame` (new types + tests)](#-phase-1a-create-lineinvariants--elementframe) | ✅ Done |
| 1b | [Reback `ElementRenderContext` as a delegating view](#-phase-1b-reback-elementrendercontext-as-a-delegating-view) | ✅ Done |
| 2a | [Build `LineInvariants` once in `LineRenderer.render()`](#-phase-2a-build-lineinvariants-once-in-render) | ✅ Done |
| 2b | [Build per-element `ElementFrame`s in `render()`](#-phase-2b-build-per-element-elementframes) | ✅ Done |
| 3 | [Migrate standalone (`*FromLine` / bespoke) renderers](#-phase-3-migrate-standalone-renderers) | ✅ Done |
| 4a | [Flip the interface + base template + helpers](#-phase-4a-flip-the-interface--base-template--helpers) | ✅ Done |
| 4b | [Migrate note-like renderers](#-phase-4b-migrate-note-like-renderers) | ✅ Done |
| 4c | [Migrate line-beginning renderers](#-phase-4c-migrate-line-beginning-renderers) | ✅ Done |
| 4d | [Migrate near-note tier](#-phase-4d-migrate-near-note-tier) | ✅ Done |
| 4e | [Migrate system tier](#-phase-4e-migrate-system-tier) | ✅ Done |
| 4f | [Migrate `LyricTextRenderer` + finish call sites + compile](#-phase-4f-migrate-lyrictextrenderer--finish-call-sites--compile) | ✅ Done |
| 5 | [Delete `ElementRenderContext`; migrate tests](#-phase-5-delete-elementrendercontext--migrate-tests) | ✅ Done |

## Purpose

`ElementRenderContext` (ECx) is a 20-field grab bag mutated through ~12 ordered setter calls at the top of `LineRenderer.render()`, plus per-element setters (`setCurrentElementIndex`, `setOverrideElementXSs`/`clear`, `setPreviewShift`) toggled mid-loop. This undocumented setup protocol is a source of stale-state bugs. Split it by lifetime so that forgetting a setter becomes impossible by construction:

- **`LineInvariants`** — immutable, built once per `LineRenderer.render()` call. Carries the 14 per-line fields plus all the color-resolution and playing-state logic that depends only on per-line state.
- **`ElementFrame`** — immutable record, built fresh per element (and for the line-level passes). Carries `currentElementIndex`, `overrideElementXSs`, and the preview-shift pair.

After this milestone, renderers receive `(LineInvariants inv, ElementFrame frame, T element, Graphics2D g2)` and `ElementRenderContext` is deleted. This is load-bearing for Phase 9 (flatten `BaseElementRenderer`).

## Field / responsibility partition

**`LineInvariants` (immutable; built once per render call):**

| Member | Notes |
|--------|-------|
| `song`, `fonts` | already ctor-final on ECx |
| `currentLine`, `lineIndex`, `middleLineYSs` | per-line position |
| `layoutResult`, `songLayoutMetrics`, `lyricRenderMetrics` | per-line/song layout |
| `activelyEditedElement`, `selectionProvider`, `editMode` | edit state |
| `selectionColor` | default `ScoreView.getSelectionColor()`; pitch-drag override resolved up front |
| `playingNoteIndex`, `playingGraceNoteIndex` | playback highlight |
| **methods** | `getElementColor`, `getLyricColor`, `getLyricConnectorColor`, private `colorFor`, `isElementPlaying`, `isElementInPlayingTie`, private `isLyricSpanPlaying` |
| **convenience** | `getAttributionFont`, `getAnnotationFont`, `getPixelsPerStaffSpace`, `getLineThickness` |

**`ElementFrame` (immutable record; built per element):**

| Member | Replaces |
|--------|----------|
| `currentElementIndex` | `setCurrentElementIndex` / `getCurrentElementIndex` |
| `overrideElementXSs` (NaN ⇒ none) | `setOverrideElementXSs` / `hasOverrideElementX` / `getOverrideElementXSs` / `clearOverrideElementX` |
| `previewShiftFromIndex` (−1 ⇒ none), `previewShiftSs` | `setPreviewShift` / `hasPreviewShift` / `getPreviewShiftFromIndex` / `getPreviewShiftSs` / `clearPreviewShift` |
| **predicates** | `hasOverrideElementX()`, `hasPreviewShift()` as record methods |
| **constants** | a canonical line-level / "no override, no shift" instance |

> Note: the preview shift is line-scoped (set once per paint) but lives on `ElementFrame` for uniformity — `LineRenderer` threads the same shift values into every per-element frame. `currentElementIndex` is the only genuinely per-element field.

## Implementation Approach

The `ElementRenderer<T>` interface is referenced **only** by `BaseElementRenderer` — there is no polymorphic dispatch; `LineRenderer` calls every renderer through concrete entry methods. This means:

- The ~8 **standalone renderers** (bespoke `*FromLine` / `renderTie` / `renderEndings` / `renderKeyChange` entry methods) can each migrate independently (Phase 3).
- The ~13 **template-method renderers** share `BaseElementRenderer`'s `render`→`renderElement` template and must migrate as one coordinated set with the base (Phase 4).

To keep every phase compileable, `ElementRenderContext` survives as a **delegating view** through Phases 1–4. Phase 1a adds the two new immutable types (with the color/playing logic living in `LineInvariants`) alongside an untouched `ElementRenderContext`; Phase 1b then rebacks `ElementRenderContext` so it gains a `(LineInvariants, ElementFrame)` constructor and `invariants()`/`frame()` accessors while retaining its full public getter/setter API (so renderers and tests are untouched). Phase 2 rebuilds `LineRenderer.render()` to construct the new immutable types and wrap a fresh ECx view per renderer call — split into 2a (build `LineInvariants` once) and 2b (build per-element `ElementFrame`s). Phases 3–4 migrate renderers to take `(inv, frame)` directly, dropping the wraps one batch at a time. Phase 5 deletes ECx once only tests remain.

## Dependencies

- Phase 7 (package restructure) complete: `dom/`, `layout/`, `ui.*` in place.
- Each phase ends with `./scripts/compile.sh`; phases that change rendering behavior also run `./scripts/test.sh unit` and a manual visual check against the Phase 6 baseline.
- Snapshot at the end of each phase: `git stash push --include-untracked -m "Finished phase N" && git stash apply` as a single Bash invocation.

---

## ✅ Phase 1a: Create `LineInvariants` + `ElementFrame`

**Status:** Done  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Opus 4.7, high effort — partitioning the central context and porting the color/playing logic into an immutable type requires judgment; load-bearing for the rest of the milestone.

This subphase is purely **additive**: the two new types land alongside an untouched `ElementRenderContext`. The build stays green because nothing existing changes — `LineInvariants` carries its own copy of the color/playing logic, which `ElementRenderContext`'s now-duplicate methods are deleted against in Phase 1b.

### Tasks

1. Grep all references to `ElementRenderContext` to confirm the only callers are the renderer package, `LineRenderer`, `LineComponent.applyGracePreviewShift`, and tests (`ElementRenderContextTest`, `RenderContextTestHelper`). Note any surprise caller in this section before proceeding.
2. Create `ElementFrame` (renderer package): immutable record with `currentElementIndex`, `overrideElementXSs`, `previewShiftFromIndex`, `previewShiftSs`; add `hasOverrideElementX()` / `hasPreviewShift()` predicates and a canonical "no override, no shift, index −1" line-level instance.
3. Create `LineInvariants` (renderer package): immutable class with the 14 per-line fields and a `Builder` (it is assembled from ~12 sources in `LineRenderer`). Port the **bodies** of `getElementColor`, `getLyricColor`, `getLyricConnectorColor`, `colorFor`, `isElementPlaying`, `isElementInPlayingTie`, `isLyricSpanPlaying` and the convenience accessors (`getAttributionFont`, `getAnnotationFont`, `getPixelsPerStaffSpace`, `getLineThickness`) into it. `ElementRenderContext` is left untouched (it keeps its own copies for now) so the build stays green.
4. Add `LineInvariantsTest` (color + playing-state assertions ported from `ElementRenderContextTest`) and `ElementFrameTest` (predicate edges: NaN override, −1 shift index).
5. `./scripts/compile.sh && ./scripts/test.sh unit` — all green, zero behavior change.

---

## ✅ Phase 1b: Reback `ElementRenderContext` as a delegating view

**Status:** Done  <br>
**BlockedBy:** 1a  <br>
**Recommended model/effort:** Opus 4.7, medium-high effort — rewiring the central context to delegate while preserving its full public API is correctness-sensitive; the existing test suite gates behavior parity.

### Tasks

1. Add a `(LineInvariants, ElementFrame)` constructor plus `invariants()` / `frame()` accessors to `ElementRenderContext`.
2. Keep every existing getter and setter so no renderer or test changes yet: setters accumulate into an internal `LineInvariants.Builder` / replace the held `ElementFrame`.
3. Delete the duplicated color/playing method bodies (`getElementColor`, `getLyricColor`, `getLyricConnectorColor`, `colorFor`, `isElementPlaying`, `isElementInPlayingTie`, `isLyricSpanPlaying`) and convenience accessors from `ElementRenderContext`; delegate to the built `LineInvariants` instead — no logic remains duplicated.
4. `./scripts/compile.sh && ./scripts/test.sh unit` — all green, zero behavior change.

---

## ✅ Phase 2a: Build `LineInvariants` once in `render()`

**Status:** Done  <br>
**BlockedBy:** 1b  <br>
**Recommended model/effort:** Opus 4.7, medium effort — the `LineInvariants.Builder` assembly and up-front `selectionColor` resolution carry the judgment; per-element state is left untouched, so the change is contained to the top of `render()`.

This subphase converts only the **line-invariant half**. The per-element setter mutations and the grace-preview-shift path keep flowing through the ECx view's setters, so the render loop's control flow is unchanged.

### Tasks

1. In `LineRenderer.render()`, replace the ~12 sequential ECx setter calls with a single `LineInvariants` build via `LineInvariants.Builder`, sourced from `lc` / `score`. Resolve the `selectionColor` (default vs. pitch-drag override) up front into the builder.
2. Construct one `ElementRenderContext` view from the built `LineInvariants` plus the held (still-mutable) `ElementFrame`. The per-element setters (`setCurrentElementIndex`, `setOverrideElementXSs`/`clear`) and `applyGracePreviewShift` keep mutating the frame through that view for now — only the line-invariant half is converted here.
3. `./scripts/compile.sh && ./scripts/test.sh unit` — green; line invariants now flow through the builder while per-element state is unchanged. Snapshot at end.

---

## ✅ Phase 2b: Build per-element `ElementFrame`s

**Status:** Done  <br>
**BlockedBy:** 2a  <br>
**Recommended model/effort:** Opus 4.7, medium effort — reworks the grace-preview-shift path to return data and converts the render loop to fresh `ElementFrame`s per iteration; correctness-sensitive control-flow change, but no renderer signatures change yet.

### Tasks

1. Rework `LineComponent.applyGracePreviewShift` to **return** the preview-shift data (from-index + shift) instead of mutating a context; `LineRenderer` folds it into the per-element `ElementFrame`s.
2. Replace the per-element mutations in `renderElements` and `renderAttachments` (`setOverrideElementXSs`/`clear`, `setCurrentElementIndex`) with freshly-built `ElementFrame` instances per iteration; line-level passes use the canonical line-level frame (carrying any active preview shift).
3. At each renderer call site, construct a fresh `new ElementRenderContext(inv, frame)` view to pass to the (still unmigrated) renderers. Confirm `render()` contains **no** ECx setter calls afterward.
4. `./scripts/compile.sh && ./scripts/test.sh unit`.
5. Manual visual check: open a representative song; verify staff, notes, beams, ties, tuplets, dynamics, endings, attachments, lyrics, and the grace-note insertion preview all render identically to the Phase 6 baseline. Snapshot at end.

---

## ✅ Phase 3: Migrate standalone renderers

**Status:** Done  <br>
**BlockedBy:** 2b  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — independent, mechanical signature swaps (`ctx` → `inv`, `frame`) on renderers that do not share the `BaseElementRenderer` template; compile + visual check gate correctness.

> For each renderer below: change the bespoke entry-method signature from `(… , ElementRenderContext ctx)` to `(LineInvariants inv, ElementFrame frame, …)`, replace `ctx.X()` reads with `inv.X()` / `frame.X()`, and update the call site in `LineRenderer` to pass `inv, frame` (dropping the per-call ECx wrap).

### Tasks

1. `GlissandoRenderer` (`renderGlissandosFromLine`, `renderGlissando`, `renderPreviewGlissando`) — includes the preview-shift read now sourced from `frame`.
2. `BeamGroupRenderer` (`renderBeams`).
3. `TieRenderer` (`renderTie`).
4. `TupletRenderer` (`renderTupletsFromLine`).
5. `DynamicsRenderer` (`renderHairpinsFromLine`).
6. `EndingRenderer` (`renderEndings`).
7. `TrillRenderer` (`renderTrillsFromLine`) and `LyricConnectorRenderer` (`render`); then `./scripts/compile.sh` and a visual spot-check.

---

## Phase 4 (split): Migrate `BaseElementRenderer` template + its renderers

Phase 4 flips a **shared** template (`render`→`renderElement`), so the interface, the base, and all ~13 subclasses must flip together and compile **atomically**. The split below is for tracking and reviewability only: **subphases 4a–4e do not compile in isolation** — once 4a flips the abstract `renderElement` signature, every subclass is broken until 4f. Compilation and the unit/visual gates run **only at the end of subphase 4f** (folding in the old Phase 4 task 7). Snapshot at the end of 4f, after the build is green.

---

## ✅ Phase 4a: Flip the interface + base template + helpers

**Status:** Done  <br>
**BlockedBy:** 3  <br>
**Recommended model/effort:** Opus 4.7, medium effort — the signature flip is load-bearing for every subclass migration that follows.

### Tasks

1. Flip `ElementRenderer<T>.render` and `BaseElementRenderer`'s `render` / `renderElement` to `(LineInvariants inv, ElementFrame frame, T element, Graphics2D g2)`; update base helpers that read context (`getDecorationColor` reads `frame.currentElementIndex()`; `applyDecorationColor` and friends read `inv`).

> Already complete on disk: `ElementRenderer.render`, `BaseElementRenderer.render` / `renderElement`, and `getDecorationColor` / `applyDecorationColor` all take `(LineInvariants inv, ElementFrame frame, …)`. No compile here — the abstract signature is flipped but subclasses still override the old one.

---

## ✅ Phase 4b: Migrate note-like renderers

**Status:** Done  <br>
**BlockedBy:** 4a  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — mechanical `ctx` → `inv` / `frame` signature swaps. Does not compile in isolation (see Phase 4 note).

### Tasks

1. Migrate note-like renderers: `NoteRenderer` (including the `render(g2, note, ctx)` convenience overload and `resolveNoteXSs`), `RestRenderer` (`resolveRestXSs`), `BarRenderer` (`resolveBarXSs`).

---

## ✅ Phase 4c: Migrate line-beginning renderers

**Status:** Done  <br>
**BlockedBy:** 4b  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — mechanical signature swaps. Does not compile in isolation (see Phase 4 note).

### Tasks

1. Migrate line-beginning renderers: `ClefRenderer`, `KeySignatureRenderer` (including `renderKeyChange`).

---

## ✅ Phase 4d: Migrate near-note tier

**Status:** Done  <br>
**BlockedBy:** 4c  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — mechanical signature swaps. Does not compile in isolation (see Phase 4 note).

### Tasks

1. Migrate near-note tier: `ArticulationRenderer`, `FermataRenderer`, `DynamicMarkingRenderer`.

---

## ✅ Phase 4e: Migrate system tier

**Status:** Done  <br>
**BlockedBy:** 4d  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — mechanical signature swaps. Does not compile in isolation (see Phase 4 note).

### Tasks

1. Migrate system tier: `TempoChangeRenderer`, `BeatChangeRenderer`, `AnnotationRenderer`, `MetronomeRenderer`.

---

## ✅ Phase 4f: Migrate `LyricTextRenderer` + finish call sites + compile

**Status:** Done  <br>
**BlockedBy:** 4e  <br>
**Recommended model/effort:** Opus 4.7, medium effort — the final call-site rewiring closes the atomic flip; this is the first subphase that compiles.

### Tasks

1. Migrate `LyricTextRenderer` (`renderElement` reads `frame.currentElementIndex()` for `inv.getLyricColor(...)`); update every remaining `LineRenderer` call site to pass `(inv, frame)` and delete the last ECx wraps.
2. `./scripts/compile.sh && ./scripts/test.sh unit`; visual check + snapshot.

---

## ✅ Phase 5: Delete `ElementRenderContext` + migrate tests

**Status:** Done  <br>
**BlockedBy:** 4  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — deletion plus mechanical test migration; the existing test suite and a visual check gate correctness.

### Tasks

1. Confirm no production code references `ElementRenderContext` (only tests should remain); `jet_brains_safe_delete` it.
2. Remove any builder-accumulation cruft left in `LineRenderer` from the transitional wrapping; ensure `LineInvariants` is built exactly once per `render()` and `ElementFrame`s are the only per-element state.
3. Migrate `ElementRenderContextTest` → fold into `LineInvariantsTest` / `ElementFrameTest` (already seeded in Phase 1); migrate `RenderContextTestHelper` to build `LineInvariants` + `ElementFrame`.
4. Update any other tests that constructed `ElementRenderContext` to use the new types.
5. `./scripts/compile.sh && ./scripts/test.sh unit`.
6. Manual visual check against the Phase 6 baseline across a representative set of songs; snapshot at end.

## Verification

- `ElementRenderContext` is deleted.
- `LineRenderer.render()` has no setter calls during the render loop: `LineInvariants` is built once; `ElementFrame`s are built per element.
- Every renderer takes `(LineInvariants, ElementFrame, T, Graphics2D)`; the `ElementRenderer<T>` interface is retained (flattening it is Phase 9).
- Renderer unit tests construct a valid `LineInvariants` + `ElementFrame` without reproducing a setter sequence.
- Visual output is unchanged from the Phase 6 baseline.
