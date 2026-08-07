# Element Spacing Adjustability Constraints
Companion to issue #330 (spring-and-strut spacing). Defines, per element type, whether and how the user may adjust its position, and which tier each adjustment lives in. Built from the real `songscribe.dom` type inventory.
## Scope (current)
- **Horizontal adjustment only.** Vertical adjustment is deferred.

- **No spanner adjustment.** Slurs/ties/hairpins/tuplets/beams are not user-adjustable for now.

- **No lyric adjustment.** Syllable width feeds spacing, but lyrics are not independently positioned. (The legacy lyric-adjust mode existed only because the old app had no dynamic layout.)

## Tiers
- **Spring tier** — horizontal, column elements only. A drag edits the rest-length of the two adjacent springs (stored as a delta). Participates in the solver, ripples to the fit, scales with compression. Replaces `StaffElement.xOffset` (existing stored `xOffset` values are dropped on load). The delta is dropped whenever the element's neighborhood changes structurally (adjacent insert, neighbor delete, move to another line), and delta drags are recorded as `Mutation`s (undoable).

- **Raw tier** (`userXOffsetSs` on `LineElement`) — applied after the solve, no ripple. Home for horizontal nudges of attachments, which occupy no column. Applied by `VerticalStackingCalculator.applyDecorationOffsets()`.

## Global spacing
Per-song default spacing (loose ↔ tight over a range) is a **rest-length scalar** on the ideal component of every spring — LilyPond's `spacing-increment`. It scales the ideal _above the strut_ (never the strut itself), sets each line's natural width, and sums with per-element spring-tier deltas.
## Schema
| Field | Values |
| --- | --- |
| Class | the `dom` type |
| Selectable | `yes` / `no` / `with-parent` / `click-only` — can the user grab it independently |
| X model | `spring` / `raw` / `pinned` / `none` |
| Anchor | what the position is measured from |
| Bounds | struts / limits on movement |
| Group | `independent` / `propagates` (selection) / `linked` |

`none` = not user-adjustable (position fully derived — pitch, header layout, or computed geometry).

`click-only` = selectable by a click so it can be annotated or retyped, but never draggable and never swept into a multi-element selection.

`pinned` = position fully derived like `none`, but anchored to a line edge rather than to a header or a pitch.

* * *
## Column-defining types
Occupy a horizontal time-slice → live in an `ElementColumn` → **X is spring tier**.

| Class | Selectable | X model | Anchor | Bounds | Group |
| --- | --- | --- | --- | --- | --- |
| `StaffElement` (notes) | yes | spring | neighbor columns | adjacent struts | independent; Shift/Alt propagate |
| `StructuralElement` — rests | yes | spring | neighbor columns | adjacent struts | independent; Shift/Alt propagate |
| `StructuralElement` — barlines / repeats | yes | spring | neighbor columns | adjacent struts | independent; Shift/Alt propagate |
| `StructuralElement` — terminal barline | click-only | pinned | end of last line | pushed to line end, clamped at preceding strut | auto; line is not justified |
| `StructuralElement` — `BREATH_MARK` | yes | spring | neighbor columns | adjacent struts | independent; Shift/Alt propagate |
| `Clef` | no  | none | line start (header) | header layout | —   |
| `KeySignature` | no  | none | after clef (header) | header layout | —   |
| `Attribution` (block) | with-parent | none | staff right edge | —   | —   |

* * *
## Single-anchor attachments
Hang off a parent note; occupy no column → **X is raw** (never reflows notes). Horizontal `alignment` (LEFT/CENTER/RIGHT) sets the base anchor; `userXOffsetSs` nudges from there.

| Class | Selectable | X model | Anchor | Bounds | Group |
|---|---|---|---|---|---|
| `Articulation` (STACCATO, ACCENT) | yes | raw | owner note head; stem side | — | independent |
| `FermataAttachment` | yes | raw | owner note | — | independent |
| `DynamicAttachment` (pp…ff, sfz, fp) | yes | raw | owner note | — | independent |
| `AnnotationAttachment` | yes | raw (`xAlignment`) | owner note; `Placement` | — | independent |
| `TempoChangeAttachment` | yes | raw | owner note | — | independent |
| `BeatChangeAttachment` | yes | raw | owner note | — | independent |

* * *
## Spanners — no user adjustment (deferred)
Shaped by endpoint / control-point fields, not offsets. Not user-adjustable for now; existing shape fields listed for reference.

| Class | Existing shape fields | User-adjustable |
|---|---|---|
| `Tie` | none (placement from stem dir) | no |
| `Beam` | none (computed in `LayoutResult.BeamLayout`) | no |
| `Trill` | `yPositionSs` | no |
| `Tuplet` | `verticalPositionSs`, `grade` | no |
| `Hairpin` → `Crescendo` / `Diminuendo` | `x1ShiftSs`, `x2ShiftSs`, `yShiftSs` | no |

* * *
## Lyrics
`Lyric` is owned by its `StaffElement`, not a `LineElement`. Syllable width feeds the spring solver (widens the note gap); lyrics have no independent position and are not adjustable.

| Aspect | Model |
| --- | --- |
| Horizontal | spring, via owner note (width raises the adjacent spring's rest length) |
| Vertical | row-assigned (`verse`) |

* * *
## Slides
`Glissando` and `Fall` are sealed inner types of `StaffElement`, caching hit geometry. Not independently positioned; the note's spring spacing reserves glissando clearance (`ensureGlissandoSpacing`). No user offset.

* * *
## Decisions encoded
1. Adjustment is **horizontal only** for now; vertical is deferred.

2. Only **column-defining types** get spring-tier X; attachments that move horizontally are **raw** and never reflow notes.

3. **Spanners** are not user-adjustable (deferred).

4. **Lyrics** feed spacing but are not independently positioned or adjustable.

5. `StaffElement.xOffset` is retired; stored values are **dropped on load**.

6. There is **no justification.** Lines sit at natural width; the terminal barline is a special auto-maintained element pushed to the end of the last line during editing, and the rest of the line is not stretched.

7. Per-song default spacing is a **rest-length** scalar, not stiffness.
