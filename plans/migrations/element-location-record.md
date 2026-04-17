# ElementLocation Record Migration

**Type:** Master Plan  <br>
**Created:** 2026-04-17  <br>
**Status:** Pending  <br>
**BlockedBy:** —

---

## Purpose

Replace the ad-hoc `(lineIndex, elementIndex)` sentinel-pair pattern used to identify a specific element within a composition with a typed `ElementLocation` record. The `-1 / -1` "no match" sentinel lives in multiple places today; a nullable record makes absence explicit, eliminates the risk of desynchronized halves, and creates one reusable representation that future call sites can adopt.

## Motivation

Current sentinel-pair occurrences:

| Location | Fields / return | Notes |
|---|---|---|
| `PreviewElementManager.getXMatchedElementLineIndex()` / `getXMatchedElementIndex()` | Two int getters, each with `-1` sentinel | Called from `LineRenderer` and `BeamGroupRenderer` |
| `ElementRenderContext.replacedElementLineIndex` / `replacedElementIndex` | Two int fields, `(-1, -1)` cleared | Introduced in branch `292-beat-change` |
| `BeamGroupRenderer.java:136-137` | Two local `hoveredLineIndex` / `hoveredNoteIndex` vars | Direct copy of PreviewElementManager sentinel semantics |

Problems:
- The "cleared" contract lives only in javadoc; nothing prevents `(5, -1)` or `(-1, 3)`.
- Every call site re-codes the same pair-equality check.
- No shared vocabulary when a future feature (e.g., hit-test, focus, bookmark) needs the same (line, element) identifier.

## Scope

**In scope**
- Introduce one record and migrate the three sites above to use it.
- Update `ElementRenderContext` to store `@Nullable ElementLocation` (or remove the field entirely in favor of direct `PreviewElementManager` reads — see Phase 3).
- Preserve existing behavior (no color, priority, or timing changes).

**Out of scope**
- Migrating unrelated `-1` sentinels (`playingNoteIndex`, `currentElementIndex`, etc.). Those identify a single element within a known line and are not pair-sentinels.
- Changing `LayoutResult.ElementLocation` (if it exists as a local concept) unless it collides by name.
- Any visual or UX change.

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Introduce `ElementLocation` record](#-phase-1-introduce-elementlocation-record) | ⏳ Pending | — |
| 2 | [Migrate `PreviewElementManager` API](#-phase-2-migrate-previewelementmanager-api) | ⏳ Pending | — |
| 3 | [Migrate `ElementRenderContext`](#-phase-3-migrate-elementrendercontext) | ⏳ Pending | — |
| 4 | [Migrate `BeamGroupRenderer`](#-phase-4-migrate-beamgrouprenderer) | ⏳ Pending | — |
| 5 | [Verify and clean up](#-phase-5-verify-and-clean-up) | ⏳ Pending | — |

---

## ⏳ Phase 1: Introduce `ElementLocation` record

**Status:** Pending  <br>
**BlockedBy:** —

### Tasks

1. Create `src/main/java/songscribe/music/ElementLocation.java`:

   ```java
   package songscribe.music;

   /**
    * Identifies a specific element by its line index within a composition
    * and its element index within that line. Both indices are non-negative
    * when the location is valid; absence is expressed by a null reference
    * to an {@code ElementLocation}, never by a sentinel value here.
    */
   public record ElementLocation(int lineIndex, int elementIndex) {
       public ElementLocation {
           if (lineIndex < 0 || elementIndex < 0) {
               throw new IllegalArgumentException(
                   "ElementLocation indices must be non-negative; got ("
                       + lineIndex + ", " + elementIndex + ")");
           }
       }
   }
   ```

2. Confirm there is no existing class named `ElementLocation` anywhere in the codebase. A grep returned only `LayoutResult`-internal names in earlier search; re-verify before creating the file.
3. Run `./scripts/compile.sh`. Expect SUCCESS.

### Decision points

- **Package placement.** `songscribe.music` hosts `Composition` / `Line` and is the natural model-level home. Alternatives: `songscribe.ui.layout` (rejected — not UI-specific); `songscribe.util` (rejected — model concept, not a utility).
- **Validation.** Constructor rejects negatives to enforce the "null means absent" invariant. If a call site needs to carry a provisional/invalid location, that is a signal it should hold `@Nullable ElementLocation` instead.

---

## ⏳ Phase 2: Migrate `PreviewElementManager` API

**Status:** Pending  <br>
**BlockedBy:** 1

### Tasks

1. Add a new getter to `PreviewElementManager`:

   ```java
   @Nullable
   public static ElementLocation getXMatchedElement() {
       return (xPosSsMatchesElement && currentPreviewLine != null)
           ? new ElementLocation(currentPreviewLine.getLineIndex(), currentXIndex)
           : null;
   }
   ```

2. Remove the two old getters `getXMatchedElementLineIndex()` and `getXMatchedElementIndex()`. Their only callers are `LineRenderer` and `BeamGroupRenderer`, which Phases 3 and 4 update in lockstep.
3. Compile. Expect compile errors at the two call sites; those are fixed in Phases 3 and 4.

### Notes

- The getter allocates one record per call. Called twice per render in `LineRenderer` / `BeamGroupRenderer`. Negligible versus the rest of the render pass.
- Naming: `getXMatchedElement()` mirrors the existing "xMatched" terminology. An alternative (`getHoveredElement()`) would read more naturally but diverges from established vocabulary — defer a rename.

---

## ⏳ Phase 3: Migrate `ElementRenderContext`

**Status:** Pending  <br>
**BlockedBy:** 2

### Decision

**Chosen: 3A — remove the field entirely.** `getElementColor` reads `PreviewElementManager.getXMatchedElement()` directly. This also resolves the "redundant state + leaky abstraction" finding from the `/check` review on branch `292-beat-change`. `LineRenderer` no longer calls `ctx.setReplacedElement(...)`.

(Rejected alternative 3B: keep a single `@Nullable ElementLocation replacedElement` field and setter — preserves the "ctx owns color resolution" boundary but keeps the redundant state.)

### Tasks

1. In `ElementRenderContext`:
   - Remove fields `replacedElementLineIndex` and `replacedElementIndex`.
   - Remove method `setReplacedElement(int, int)`.
   - In `getElementColor(int elementIndex)`, replace the sentinel comparison with:

     ```java
     var matched = PreviewElementManager.getXMatchedElement();
     if (matched != null && matched.matches(lineIndex, elementIndex)) {
         return REPLACED_ELEMENT_COLOR;
     }
     ```

   - Verify constants `REPLACED_ELEMENT_ALPHA` / `REPLACED_ELEMENT_COLOR` are still needed (yes) and tighten their access to `private static final`.
2. In `LineRenderer.render(Graphics2D)` (lines ~122-125), remove the `ctx.setReplacedElement(...)` call. The hover state is now read lazily during color resolution.
3. Compile. Verify `LineRenderer.getElementColor` still delegates to `ctx.getElementColor` and the `Color.BLACK` short-circuit path is intact.

### Verification

- Launch the app (`./scripts/crun.sh`), enter note-entry (edit) mode, move the cursor so the preview element aligns with an existing note, and confirm the replaced-element highlight still renders in the expected red.
- Confirm selection and playback highlights still take priority over the replaced-element highlight (they short-circuit earlier in `getElementColor`).

---

## ⏳ Phase 4: Migrate `BeamGroupRenderer`

**Status:** Pending  <br>
**BlockedBy:** 2

### Decision

**Chosen: add `matches(int, int)` helper to `ElementLocation`.** Both `ElementRenderContext.getElementColor` and `BeamGroupRenderer` simplify to `matched != null && matched.matches(lineIndex, i)`. This removes the last remnants of the pair comparison from call sites.

### Tasks

1. Add the helper to `ElementLocation` (introduced in Phase 1):

   ```java
   public boolean matches(int lineIndex, int elementIndex) {
       return this.lineIndex == lineIndex && this.elementIndex == elementIndex;
   }
   ```

2. In `BeamGroupRenderer.java` around lines 135-144, replace:

   ```java
   var hoveredLineIndex = PreviewElementManager.getXMatchedElementLineIndex();
   var hoveredNoteIndex = PreviewElementManager.getXMatchedElementIndex();
   ...
   var isHovered = hoveredLineIndex == lineIndex && i == hoveredNoteIndex;
   ```

   with:

   ```java
   var matched = PreviewElementManager.getXMatchedElement();
   ...
   var isHovered = matched != null && matched.matches(lineIndex, i);
   ```

3. Compile.

### Verification

- In note-entry mode, hover the preview element over a beamed note and confirm the beam color still changes (hover coloring).
- Selection of a beamed note continues to override hover coloring.

---

## ⏳ Phase 5: Verify and clean up

**Status:** Pending  <br>
**BlockedBy:** 1, 2, 3, 4

### Tasks

1. `./scripts/compile.sh` — must SUCCEED.
2. `./scripts/test.sh unit` — must pass. No test changes expected from this refactor.
3. Re-grep the codebase to confirm no call sites remain for `getXMatchedElementLineIndex` / `getXMatchedElementIndex`:

   ```text
   rg 'getXMatchedElement(LineIndex|Index)\b'
   ```

   Expect zero matches.
4. Re-grep for any other `== -1 && .* == -1` or `replacedElementLineIndex` stragglers. Expect zero.
5. Commit as a single `refactor:` commit summarizing the migration. Body bullets per the project commit-message rules.

### Exit criteria

- All three original call sites use `ElementLocation`.
- `PreviewElementManager` exposes only the record-returning getter.
- No new field duplication in `ElementRenderContext`.
- Unit tests pass; manual verification of hover + beam-hover + selection + playback priority confirmed.

---

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Hidden call sites outside the three identified | Phase 5 re-greps before marking complete. |
| Record allocation in a hot path | The getter is called ~twice per line render; allocation pressure is negligible versus Graphics2D work. If profiling ever flags it, switch to caching the record in `PreviewElementManager` and invalidating it when `xPosSsMatchesElement` / `currentPreviewLine` / `currentXIndex` change. |
| Naming collision with `LayoutResult` internals | Resolved at Phase 1 start via grep. Rename to `ElementRef` if collision found. |
| Behavior drift in edit-mode gate for hover | The 292-beat-change refactor already gated hover on edit mode; confirmed intentional. This plan preserves that gate. |

## Follow-ups

- Could `Composition.getElement(ElementLocation)` replace the current `getLine(i).getElement(j)` pattern in some call sites? Out of scope for this plan; flagged as a follow-up idea only.
- Rename `getXMatchedElement()` → `getHoveredElement()` to align with natural vocabulary. Deferred to keep this migration scoped.
