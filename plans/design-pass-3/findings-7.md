# Design Pass 3 — Step 7: Test-Only Surface
Every production member the pass's tests reach was checked for a production caller. All but one has one. The exception is below, and reading it turned up that it is the smaller half of a dead abstraction.
## What it found
`Span.getSpanWidthSs(double, double)` is abstract, and **nothing dispatches it through a** `Span` **reference.** The only two calls in the program name a concrete type:

- `src/main/java/songscribe/layout/stacking/NoteAttachedStacker.java:867` — `trill.getSpanWidthSs(…)`
  
- `src/main/java/songscribe/layout/stacking/StructuralStacker.java:181` — `tuplet.getSpanWidthSs(…)`
  

Six classes implement it. Four have no caller at all:

| Implementation | Body | Production caller |
|---|---|---|
| `Trill:127` | `max(TRILL_GLYPH_WIDTH_SS, endXSs - anchorXSs + TRILL_GLYPH_WIDTH_SS)` | `NoteAttachedStacker:867` |
| `Tuplet:334` | `max(1.0, endXSs - anchorXSs)` | `StructuralStacker:181` |
| `Ending:295` | `endXSs - anchorXSs + getEndElementWidthSs()` | **none — one test** |
| `Hairpin:459` | `max(HAIRPIN_OPENING_HEIGHT_SS, endXSs - anchorXSs + getEndElementWidthSs())` | **none** |
| `Tie:59` | `max(1.0, endXSs - anchorXSs)` | **none** |
| `Beam:46` | `max(1.0, endXSs - anchorXSs)` | **none** |

`Hairpin`'s own Javadoc already says it: _"…but_ `Span#getSpanWidthSs` _is abstract, so every span must answer it."_ An abstract method four of whose six answers nobody asks for is not an abstraction; it is a question the base class makes its subclasses keep answering.

An ending's collision geometry does not go through it either — that runs on `Ending.BracketRange.widthSs()`, read by `EndingBracketGeometry.computeCollisionRegions`.
## What it proposes
**Kind: genuinely test-only** — for `Ending.getSpanWidthSs`, which exactly one test reaches and no production code does. Step 7 gives that kind two fixes: delete it, or restructure so production uses it too. Production has a working answer already, so delete.

The other three uncalled implementations are not test-only; they are dead, and the same cut removes them. Each item below is separately decidable.

1. **Delete the abstract** `Span.getSpanWidthSs`, and the four implementations with no caller — `Ending`, `Hairpin`, `Tie`, `Beam`. `Crescendo` and `Diminuendo` inherit `Hairpin`'s and need no change.
  
2. **Keep** `getSpanWidthSs` **on** `Trill` **and** `Tuplet` as ordinary public methods, no longer overrides, each keeping its contract. Their two callers hold concrete types already, so nothing at either call site changes.
  
3. **Delete** `Span.getEndElementWidthSs()`**.** It is `protected`, and `Ending` and `Hairpin` were its only two users; both go with item 1.
  
4. **Name** `Tuplet`**'s** `1.0` **floor.** `HitRegionBuilder:557` reasons about it in a comment — _"widthSs is always positive (Tuplet.getSpanWidthSs floors it at one staff space)"_ — so a caller depends on a bare literal. A named constant on `Tuplet`, cited from that comment.
  
5. **Delete** `EndingBracketGeometryTest`**'s collision-span test.** Step 6 kept it as a rewrite; item 1 retires the method it asserts, so it goes instead. The rewrite is what made the test-only-ness visible — a test that could not fail was hiding a member nothing calls.
  
## Open questions
1. {==**Is any of the four uncalled implementations a reservation for work in flight**==}{>>No<<}{id="c1" by="user" at="2026-08-28T13:40:12.428Z"}**?** `Tie` and `Beam` return the same `max(1.0, endXSs - anchorXSs)` as `Tuplet`, which reads as a default written to satisfy the abstract method rather than a promise about ties or beams. Nothing in the tree suggests a pending caller, but a plan I cannot see would change item 1.

---
comments:
  c2:
    body: >-
      Understood — nothing is reserved, so item 1 proceeds as written: the abstract
      declaration goes, and with it the four implementations no caller reaches.
      Items 2 to 5 drew no marks and are taken as accepted.
    by: AI
    at: "2026-08-28T13:45:00.000Z"
    re: c1
