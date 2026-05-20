# Sub-plan: Flatten `BaseElementRenderer` Hierarchy

**Type:** Sub-plan  <br>
**Parent:** [rendering-rewrite.md](rendering-rewrite.md) → Phase 9  <br>
**Created:** 2026-05-20  <br>
**Status:** ✅ Done  <br>
**BlockedBy:** —

**Spec:** [specs/rendering-rewrite.md](../../../docs/specs/rendering-rewrite.md) — read the spec before implementing tasks.

---

## Status Dashboard

| Phase | Description | Status |
|-------|-------------|--------|
| 1a | [Audit + delete dead members](#-phase-1a-audit--delete-dead-members) | ✅ Done |
| 1b | [Extract `RenderingUtils` + shim the base](#-phase-1b-extract-renderingutils--shim-the-base) | ✅ Done |
| 1c | [Repoint non-subclass external callers](#-phase-1c-repoint-non-subclass-external-callers) | ✅ Done |
| 2 | [Migrate note-family renderers](#-phase-2-migrate-note-family-renderers) | ✅ Done |
| 3 | [Migrate line-beginning + span renderers](#-phase-3-migrate-line-beginning--span-renderers) | ✅ Done |
| 4 | [Migrate glyph-decoration + lyric renderers](#-phase-4-migrate-glyph-decoration--lyric-renderers) | ✅ Done |
| 5 | [Migrate the `MetronomeRenderer` subtree](#-phase-5-migrate-the-metronomerenderer-subtree) | ✅ Done |
| 6 | [Delete `BaseElementRenderer`](#-phase-6-delete-baseelementrenderer) | ✅ Done |

## Purpose

After Phase 8 split the mutable `ElementRenderContext` into immutable `LineInvariants` + `ElementFrame` passed as explicit arguments, `BaseElementRenderer` no longer earns its keep. Concretely:

- There is **no polymorphic dispatch** through `ElementRenderer<T>` — `LineRenderer` calls every renderer through a concrete entry method (established in Phase 8). The `render`→`renderElement` template method therefore buys nothing; `render()` just calls `renderElement()`.
- Every "helper" the base provides is **either a constant or a method that uses no instance state** — already `static`, or a stateless instance method taking everything it needs as parameters (`inv`, `frame`, glyph, coordinates).

So the base is an abstract parent contributing no shared mutable state and no genuine polymorphism — a namespace for static utilities wearing an inheritance costume. Flattening makes each renderer a near-pure function of `(LineInvariants, ElementFrame, T, Graphics2D)`, with shared utilities living in an explicit `RenderingUtils` holder.

When done: `BaseElementRenderer` is deleted, the `ElementRenderer<T>` interface is retained, all concrete renderers implement it directly, and visual output is unchanged.

## Member audit + disposition

The master plan asks each protected helper to be categorized as **(a)** trivial accessor → inline, **(b)** drawing utility → extract to static `RenderingUtils`, or **(c)** genuinely-needed default behavior → fold into the interface as a `default` method. The audit finding is that **category (c) is empty**: the only default-behavior candidate is the trivial `render` template, which is removed (each renderer implements `render` directly). Everything else is a static-izable utility or a constant.

**Methods → `RenderingUtils` (static; bodies moved verbatim):**

| Member | Currently | Callers (subclasses unless noted) |
|--------|-----------|-----------------------------------|
| `getDecorationColor` | `protected static` | MetronomeRenderer, TrillRenderer |
| `applyDecorationColor` | `protected static` | Annotation, Articulation, DynamicMarking, Fermata |
| `layoutYToComponentYSs` | `protected static` | Annotation, Articulation, DynamicMarking, Dynamics, Ending, Fermata, Metronome, Trill, Tuplet |
| `drawBravuraGlyph` (×2 overloads) | `protected` instance (stateless) | Articulation, Clef, DynamicMarking, Fermata, Note, Trill |
| `centeredGlyphX` | `protected static` | Articulation, Fermata, Trill |
| `glyphOriginYFromLayoutTop` | `protected static` | Articulation, DynamicMarking, Fermata |
| `drawLedgerLine` | `protected` instance (stateless; reads `inv`) | NoteRenderer |
| `noteStaffPositionToCoordinateSs` | `public static` | NoteRenderer, GlissandoRenderer (non-subclass) |
| `forEachLedgerLineYSs` | package-private `static` | NoteRenderer, NoteAreaBuilder (non-subclass) |
| `stemCenterXOffsetSs` | `protected static` | BeamGroupRenderer |

**Constants → `RenderingUtils` (re-homed; used by non-renderer code, so they need a stable home):**

| Constant | External (non-subclass) callers |
|----------|---------------------------------|
| `MUSIC_FONT` | NoteAreaBuilder; (subclasses Bar, KeySignature, Note, Rest, Trill) |
| `GRACE_NOTE_FONT` | NoteAreaBuilder; (subclass Note) |
| `GRACE_NOTE_SCALE` | — only Note among renderers; **see note** |
| `FONT_SIZE` | (subclass Metronome) |
| `getMusicFont()` | DurationListCellRenderer, KeySignatureDisplay, UIUtils; (subclass Metronome) |
| `STAFF_LINE_COLOR` | LineRenderer (`ui.component.score`) |
| `ELEMENT_COLOR` | BeamGroup, Dynamics, Ending, KeySignature, Tie (non-subclass), Tuplet |

**Confirmed dead — delete in Phase 1 (referenced only inside `BaseElementRenderer`; verify with `find_referencing_symbols` first):**

- `layoutXToComponentXSs` — no caller.
- `staffLineToYSs` — no caller.
- `GLISSANDO`, `TRILL` (the Fughetta Private-Use-Area string constants — *not* the `ElementType`/`ElementField` enum constants of the same name) — no caller.
- `TEMPO_CHANGE_ZOOM_X`, `TEMPO_CHANGE_ZOOM_Y` — `TempoChangeRenderer` does not reference them.

> **Note (out of scope):** `GRACE_NOTE_SCALE = 0.75f` is duplicated three times — `BaseElementRenderer`, `dom.ElementType`, `layout.NoteGeometry`. This sub-plan re-homes the `BaseElementRenderer` copy into `RenderingUtils` without consolidating the three. Consolidation is a separate concern; do not fold it in.

## Hierarchy

```
BaseElementRenderer<T>  (abstract)
├── NoteRenderer, RestRenderer, BarRenderer
├── ClefRenderer, KeySignatureRenderer
├── BeamGroupRenderer, DynamicsRenderer, EndingRenderer, TupletRenderer
├── ArticulationRenderer, FermataRenderer, DynamicMarkingRenderer,
│   TrillRenderer, AnnotationRenderer, LyricTextRenderer
└── MetronomeRenderer  (abstract)
    ├── TempoChangeRenderer
    └── BeatChangeRenderer
```

`GlissandoRenderer`, `TieRenderer`, `LyricConnectorRenderer`, `NoteAreaBuilder` are **not** subclasses (they were already standalone after Phase 8); they reference base statics by qualified name and are handled as external callers in Phase 1.

## Implementation Approach

Mirror the Phase 8 "delegating shim" discipline so the build stays green at every phase:

1. **Phase 1a (Opus, judgment).** Audit each base member with `find_referencing_symbols`; confirm and delete the dead set. Self-contained and green.
2. **Phase 1b (Opus).** Create `RenderingUtils` with the surviving helpers (static, bodies moved) and the re-homed constants. Reback `BaseElementRenderer` so its remaining helpers are thin delegators to `RenderingUtils` and its constants re-declare `= RenderingUtils.X` — every subclass still compiles untouched.
3. **Phase 1c (Sonnet, mechanical).** Repoint non-subclass external callers to reference `RenderingUtils` directly so they no longer depend on the base.
4. **Phases 2–5 (Sonnet/Haiku, mechanical).** Migrate subclasses off the base one batch at a time. Per renderer: change `extends BaseElementRenderer<T>` → `implements ElementRenderer<T>`; change `protected void renderElement(...)` → `@Override public void render(...)`; qualify every inherited-helper call as `RenderingUtils.X(...)` and constant as `RenderingUtils.X`. Because the base shim survives until the last subclass migrates, each batch compiles in isolation. Batches are independent (no inheritance between renderers except the Metronome subtree, migrated together in Phase 5).
5. **Phase 6 (Sonnet).** With no subclasses left, `jet_brains_safe_delete` the base and remove residual shim cruft.

Each renderer migration is transparent to call sites: `LineRenderer` already calls the public interface method `render(inv, frame, element, g2)`; renaming the protected `renderElement` to the public `render` and dropping the template changes nothing observable.

## Dependencies

- Phase 8 complete: `ElementRenderContext` deleted; renderers take `(LineInvariants, ElementFrame, T, Graphics2D)`.
- Use `serena` `jet_brains_*` tools for Java exploration/refactoring (see `.agents/rules/serena.md`).
- Each phase ends with `./scripts/compile.sh`; rendering-affecting phases also run `./scripts/test.sh unit` and a manual visual check against the Phase 6 baseline.
- Snapshot at the end of each phase: `git stash push --include-untracked -m "Finished phase N" && git stash apply` as a single Bash invocation.

---

## ✅ Phase 1a: Audit + delete dead members

**Status:** Done  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Opus 4.7, low-medium effort — verifying the dead set against the full codebase needs care (the `GLISSANDO`/`TRILL` names collide with live enum constants elsewhere), but the change itself is small and self-contained.

Self-contained and green: only members with zero callers are removed, so no surviving code changes.

### Tasks

1. Audit each `BaseElementRenderer` member with `jet_brains_find_referencing_symbols` and record the (a)/(b)/(c) categorization from the member-audit table above; note any caller that contradicts the table before proceeding.
2. Confirm the dead set has zero callers: `layoutXToComponentXSs`, `staffLineToYSs`, the Fughetta string constants `GLISSANDO` / `TRILL` (distinct from the `ElementType` / `ElementField` enum constants of the same name), `TEMPO_CHANGE_ZOOM_X`, `TEMPO_CHANGE_ZOOM_Y`.
3. Delete the confirmed-dead members from `BaseElementRenderer`.
4. `./scripts/compile.sh && ./scripts/test.sh unit` — green; snapshot at end.

---

## ✅ Phase 1b: Extract `RenderingUtils` + shim the base

**Status:** Done  <br>
**BlockedBy:** 1a  <br>
**Recommended model/effort:** Opus 4.7, medium effort — the constant re-homing (including the font static-initializer) and the shim design carry the judgment; load-bearing for every mechanical phase that follows.

Non-destructive to behavior: helper bodies move, but `BaseElementRenderer` keeps an identical public surface via delegation so all 16 subclasses compile unchanged.

### Tasks

1. Create `RenderingUtils` (in `songscribe.ui.renderer`): move the surviving helper bodies in as `static` methods (`getDecorationColor`, `applyDecorationColor`, `layoutYToComponentYSs`, `drawBravuraGlyph`×2, `centeredGlyphX`, `glyphOriginYFromLayoutTop`, `drawLedgerLine`, `noteStaffPositionToCoordinateSs`, `forEachLedgerLineYSs`, `stemCenterXOffsetSs`).
2. Re-home the constants into `RenderingUtils` (`MUSIC_FONT`, `GRACE_NOTE_FONT`, `GRACE_NOTE_SCALE`, `FONT_SIZE`, `getMusicFont`, `STAFF_LINE_COLOR`, `ELEMENT_COLOR`) together with the Bravura font static-initializer block.
3. Reback `BaseElementRenderer`: replace each remaining helper body with a delegator to `RenderingUtils`, and re-declare each constant as `= RenderingUtils.X`. Keep `render` / `renderElement` as-is so subclasses are untouched.
4. `./scripts/compile.sh && ./scripts/test.sh unit` — green; all 16 subclasses compile against the unchanged base surface, zero behavior change.
5. Manual visual check against the Phase 6 baseline; snapshot at end.

---

## ✅ Phase 1c: Repoint non-subclass external callers

**Status:** Done  <br>
**BlockedBy:** 1b  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — mechanical reference swap from `BaseElementRenderer.X` to `RenderingUtils.X`; compile + visual gate correctness.

These callers reference base statics by qualified name but do not inherit from the base; repointing them to `RenderingUtils` removes their dependence on the base ahead of its deletion.

### Tasks

1. Repoint `DurationListCellRenderer`, `KeySignatureDisplay`, `UIUtils`, and `LineRenderer` (`STAFF_LINE_COLOR`) to `RenderingUtils`.
2. Repoint the renderer-package non-subclasses `NoteAreaBuilder`, `GlissandoRenderer`, `TieRenderer`, `LyricConnectorRenderer` to `RenderingUtils`.
3. Fix the `@link` to `BaseElementRenderer#forEachLedgerLineYSs` in `StaffElement`'s javadoc.
4. `./scripts/compile.sh && ./scripts/test.sh unit`; visual spot-check; snapshot at end.

---

## ✅ Phase 2: Migrate note-family renderers

**Status:** Done  <br>
**BlockedBy:** 1c  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — mechanical superclass→interface swap + helper qualification; compile + visual gate correctness. `NoteRenderer` is large, so the batch is small.

> Per renderer: `extends BaseElementRenderer<T>` → `implements ElementRenderer<T>`; `protected void renderElement(...)` → `@Override public void render(...)`; qualify inherited helper calls as `RenderingUtils.X(...)` and constants as `RenderingUtils.X`.

### Tasks

1. `NoteRenderer` (includes `render(g2, note, ...)` convenience overload, `resolveNoteXSs`, `drawLedgerLine`/`forEachLedgerLineYSs`/`noteStaffPositionToCoordinateSs`/`MUSIC_FONT`/`GRACE_NOTE_FONT`/`GRACE_NOTE_SCALE`/`drawBravuraGlyph` usages).
2. `RestRenderer` (`resolveRestXSs`, `MUSIC_FONT`).
3. `BarRenderer` (`resolveBarXSs`, `MUSIC_FONT`).
4. `./scripts/compile.sh && ./scripts/test.sh unit`; visual spot-check (notes, rests, bars, ledger lines, grace notes); snapshot at end.

---

## ✅ Phase 3: Migrate line-beginning + span renderers

**Status:** Done  <br>
**BlockedBy:** 2  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — mechanical swaps; independent renderers.

### Tasks

1. `ClefRenderer` (`drawBravuraGlyph`, `MUSIC_FONT`).
2. `KeySignatureRenderer` (includes `renderKeyChange`; `drawBravuraGlyph`, `MUSIC_FONT`, `ELEMENT_COLOR`).
3. `BeamGroupRenderer` (`stemCenterXOffsetSs`, `ELEMENT_COLOR`).
4. `DynamicsRenderer` (`layoutYToComponentYSs`, `ELEMENT_COLOR`).
5. `EndingRenderer` (`layoutYToComponentYSs`, `ELEMENT_COLOR`).
6. `TupletRenderer` (`layoutYToComponentYSs`, `ELEMENT_COLOR`).
7. `./scripts/compile.sh && ./scripts/test.sh unit`; visual spot-check (clefs, key sigs, beams, hairpins, endings, tuplets); snapshot at end.

---

## ✅ Phase 4: Migrate glyph-decoration + lyric renderers

**Status:** Done  <br>
**BlockedBy:** 3  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — mechanical swaps; independent renderers.

### Tasks

1. `ArticulationRenderer` (`applyDecorationColor`, `layoutYToComponentYSs`, `drawBravuraGlyph`, `centeredGlyphX`, `glyphOriginYFromLayoutTop`).
2. `FermataRenderer` (same helper set as Articulation).
3. `DynamicMarkingRenderer` (`applyDecorationColor`, `layoutYToComponentYSs`, `drawBravuraGlyph`, `glyphOriginYFromLayoutTop`).
4. `TrillRenderer` (`getDecorationColor`, `layoutYToComponentYSs`, `drawBravuraGlyph`, `centeredGlyphX`, `MUSIC_FONT`).
5. `AnnotationRenderer` (`applyDecorationColor`, `layoutYToComponentYSs`).
6. `LyricTextRenderer` (reads `frame.currentElementIndex()` for `inv.getLyricColor(...)`).
7. `./scripts/compile.sh && ./scripts/test.sh unit`; visual spot-check (articulations, fermatas, dynamics text, trills, annotations, lyrics); snapshot at end.

---

## ✅ Phase 5: Migrate the `MetronomeRenderer` subtree

**Status:** Done  <br>
**BlockedBy:** 4  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — `MetronomeRenderer` is the only class that changes parent; `TempoChangeRenderer`/`BeatChangeRenderer` extend `MetronomeRenderer` (legitimate shared behavior) and are left intact, only verified.

The Metronome subtree is migrated as a unit because `MetronomeRenderer` is an abstract intermediate. `MetronomeRenderer` stops extending `BaseElementRenderer` and implements `ElementRenderer<StaffElement>` directly; `TempoChangeRenderer`/`BeatChangeRenderer` keep extending `MetronomeRenderer` (that shared metronome-mark behavior is real and stays).

### Tasks

1. `MetronomeRenderer`: `extends BaseElementRenderer<StaffElement>` → `implements ElementRenderer<StaffElement>`; rename `renderElement` → `render` (`@Override public`); qualify `getMusicFont`/`FONT_SIZE`/`getDecorationColor`/`layoutYToComponentYSs` as `RenderingUtils.X`.
2. Confirm `TempoChangeRenderer` and `BeatChangeRenderer` still compile against the migrated `MetronomeRenderer` (they call inherited `render`/helpers via `MetronomeRenderer`, not `BaseElementRenderer`); adjust only if a reference resolved through the old base.
3. `./scripts/compile.sh && ./scripts/test.sh unit`; visual spot-check (tempo changes, beat changes, metronome marks); snapshot at end.

---

## ✅ Phase 6: Delete `BaseElementRenderer`

**Status:** Done  <br>
**BlockedBy:** 5  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — deletion plus residual-cruft cleanup; `safe_delete` reports any straggler usage and the test/visual gates confirm parity.

### Tasks

1. Confirm no production code references `BaseElementRenderer` (`jet_brains_find_referencing_symbols`); `jet_brains_safe_delete` it.
2. Remove any residual shim cruft and ensure `RenderingUtils` has no dead delegation paths; confirm every renderer implements `ElementRenderer<T>` directly.
3. Update/relocate any tests that referenced `BaseElementRenderer` (e.g. its static helpers) to `RenderingUtils`.
4. `./scripts/compile.sh && ./scripts/test.sh unit`.
5. Manual visual check against the Phase 6 baseline across a representative set of songs; snapshot at end.

## Verification

- `BaseElementRenderer` is deleted.
- All concrete renderers implement `ElementRenderer<T>` directly; the interface is retained. `MetronomeRenderer` remains an abstract intermediate for `TempoChangeRenderer`/`BeatChangeRenderer` but no longer extends a base renderer.
- Shared utilities and constants live in `RenderingUtils`; the confirmed-dead members are gone.
- Visual output is unchanged from the Phase 6 baseline.
</content>
</invoke>
