# DOM Line Parentage Consolidation
**Created:** 2026-08-01

Collapse the two containing-line pointers a `StaffElement` carries into one, then make that one pointer honest about detachment by maintaining it at a single attach/detach chokepoint in `Line`. The end state: `LineElement.getParentLine()` is the only answer to "which line am I in", it is `@Nullable`, and null means detached.
## Why
A `StaffElement` stores its owning line twice:

| Field | Declared | Meaning today |
| --- | --- | --- |
| `LineElement.parentLine` | `LineElement.java:51`, `@Nullable` | Set on add, nulled on removal **for range elements only** |
| `StaffElement.line` | `StaffElement.java:89`, non-null, `protected` | Set on add, never nulled |

They can never disagree. Every write site assigns both from the same source:

- `Line.addElement` — `setLine(this)` `:217`, `setParentLine(this)` `:218`
  
- `Line.addElement(int, StaffElement)` — `:253` / `:254`
  
- `Line.setElement` — `:331` / `:332`
  
- `StaffElement(ElementType, StaffElement)` — `line = source.line` `:122`, `setParentLine(source.getParentLine())` `:124`
  
- `StaffElement.copyStateFrom` — `:169` / `:177`
  

So there is one fact stored in two fields, and the duplication is what makes the fact unusable. `parentLine` is `@Nullable` but never nulled for staff elements, so it cannot say "detached"; `line` is typed non-null, so it is not allowed to say it. Neither pointer can answer the question both of them exist to answer.

Two consequences already visible in the tree:

- `StaffElement.line` **needs a NullAway escape hatch.** `setLine` carries `@Initializer` (`StaffElement.java:720`) because a non-null field is assigned after construction. Deleting the field deletes the exemption.
  
- **Liveness has to be asked elsewhere.** `SelectionCoordinator.isOnLine` (`:577-589`) cannot use `parentLine` for staff elements and falls back to a linear membership scan, with a Javadoc paragraph (`:564-576`) explaining why the obvious rule does not work.
  
## The ownership model this builds
`parentLine` is declared once on `LineElement` and answers for three kinds of element, which reach it three different ways. The end state:

```
                         Line
                          │
    ┌─────────────────────┼──────────────────────┐
    │  elements (List<StaffElement>)             │  rangeElements (List<RangeElement>)
    │                                            │
    ▼                                            ▼
  StaffElement                                RangeElement  (tie, beam, tuplet,
    parentLine ◄── Line.attach / Line.detach     parentLine      hairpin, ending, trill)
    │              ONLY writer  ── structural       ▲
    │                                               │
    │  children (List<LineElement>)          five hand-written setParentLine
    ▼                                        pairs at the rangeElements.add /
  Articulation, FermataAttachment, …         .remove sites ── by convention
    parentLine ◄── LineElement.addChild /     (out of scope — see issue #724)
                   removeChild, and
                   propagateParentLine
                   when the host attaches
                   or detaches

  Invariant, staff elements:  parentLine == L   ⟺   L.elements contains this
                              parentLine == null ⟺   this is in no line
  Holds at modification-bracket boundaries. Inside a bracket a re-parent may
  briefly have attached to B while A's list still holds the element; the
  `!= this` guard in detach is what makes that ordering-independent.
```

**Non-goal:** `RangeElement` parentage is already correct — all five `rangeElements.remove` sites null `parentLine`. This plan does not change span handling; it brings staff elements up to the same standard. Routing spans through `attach`/`detach` as well is tracked as **issue #724**.

> **Since superseded.** Issue #724 has landed: the five span add/remove pairs now
> route through `Line.attach`/`Line.detach` like staff elements, via the
> `appendChild`/`removeChild` helpers, and there are no hand-written
> `setParentLine` sites left. The diagram above and the non-goal describe the
> state this plan started from, not the current code.
## What makes this safe
Every mutation of `Line.elements` is already funnelled. There are exactly five, all in `Line.java`, all inside an `applyChange` mutator lambda:

```
  :228   elements.add(index, element)            addElement
  :300   elements.add(index, element)            addElement(int, StaffElement)
  :333   elements.set(index, element)            setElement
  :1508  elements.remove(index)                  removeElement
  :1551  elements.subList(from, to + 1).clear()  removeRange
```

No other class splices the list. Undo and redo do not either — `MutationReplayer` inverts element mutations by calling the same public API (`:94-101` undo, `:141-157` redo), e.g. `ElementDeletion` undoes as `line.addElement(index, deletedElement)`. So the entry points are already single; they just do not maintain the back-pointer on the way out.
## Phase dependency graph
```
  1 Collapse the duplicate pointer      (behavior-preserving)
  │
  ▼
  2 Attach/detach chokepoint            (makes parentLine honest)
  │
  ▼
  3 Consume it in selection             (delete the workaround)
```

Strictly sequential. Phase 2 before Phase 1 would make `getLine()` and `getParentLine()` disagree — the exact failure this plan exists to remove.
## Status Dashboard
| Phase | Description | Status | Sub-plan |
| --- | --- | --- | --- |
| 1   | [Collapse the Duplicate Pointer](#-phase-1-collapse-the-duplicate-pointer) | ✅ Complete | —   |
| 2   | [Attach/Detach Chokepoint](#-phase-2-attachdetach-chokepoint) | ✅ Complete | —   |
| 3   | [Consume It in Selection](#-phase-3-consume-it-in-selection) | ✅ Complete | —   |

* * *
## ✅ Phase 1: Collapse the Duplicate Pointer
**Status:** Complete **BlockedBy:** — **Files:** src/main/java/songscribe/dom/StaffElement.java, src/main/java/songscribe/dom/LineElement.java, src/main/java/songscribe/dom/Line.java, src/main/java/songscribe/dom/RangeElement.java, src/main/java/songscribe/dom/Song.java, src/main/java/songscribe/layout/LyricLayoutBuilder.java, src/main/java/songscribe/ui/dialog/TempoChangeDialog.java, src/main/java/songscribe/ui/selection/SelectionCoordinator.java, src/main/java/songscribe/ui/component/ScoreViewController.java, src/main/java/songscribe/ui/action/EditLyricAction.java, src/test/java/songscribe/dom/StaffElementTest.java, docs/clipboard.md **Recommended model/effort:** Opus 5, medium effort — mechanical, but the call sites span five subsystems and each one gains a null branch that is unreachable until Phase 2, so "what should this do when detached" has to be answered correctly now rather than discovered later.

**Behavior-preserving.** After this phase a staff element's `parentLine` is still never nulled and copies still inherit it; the point is that there is only one field left to make honest in Phase 2. Nothing in this phase may change _when_ or _whether_ a pointer is written — only which field holds it.
### Tasks
1. Create the two methods in their final homes, so Phase 2 has nothing to rename or relocate:
  

```java
// Line.java — private
private void attach(StaffElement element) {
    element.setParentLine(this);
    element.propagateParentLine(this);
}
```

```java
// LineElement.java — the class that owns `children`
/** Pushes {@code line} down the child chain; sub-elements have no line of their own. */
public void propagateParentLine(@Nullable Line line) {
    for (var child : getChildren()) {
        child.setParentLine(line);
        child.propagateParentLine(line);
    }
}
```

`propagateParentLine` replaces `setLine`'s two loops over `attachments` and `articulations` (`StaffElement.java:724-730`). `children` (`LineElement.java:84`) is exactly their union — `StaffElement:256` and `:345` both call `addChild` — so one recursive walk covers both lists and any future nesting.

2. Replace the paired `setLine(this)` / `setParentLine(this)` calls with a single `attach(element)` at the three add/set sites: `Line.java:217-218`, `:253-254`, `:331-332`. **Keep each call exactly where it sits today** — `addElement`'s before `applyChange`, `setElement`'s inside the lambda. Normalizing that placement is Phase 2's job, because it is a behavior change.
  
3. Delete the two `setParentLine` calls that `addChild` already performs: `StaffElement.java:254` (`articulation.setParentLine(getParentLine())`, redundant with `addChild` at `:256`) and `:343` (redundant with `addChild` at `:345`). `LineElement.addChild:365` sets `child.parentLine = parentLine` itself.
  
4. Delete `StaffElement.line` (`:89`), `getLine()` (`:716-718`), `setLine(Line)` (`:720-731`), the `@Initializer` annotation, and its `com.uber.nullaway.annotations.Initializer` import (`:29`).
  
5. Remove the two copy-constructor assignments of the deleted field — `line = source.line` at `:122` and `:169`. **Leave** `setParentLine(source.getParentLine())` **at** `:124` **and** `:177` **in place** — Phase 2 removes them, once a null `parentLine` is meaningful. Removing them here would make a clone report null in a phase that is supposed to change nothing.
  
6. Repoint every read of the deleted getter. Confirm the list with `jet_brains_find_referencing_symbols` on `StaffElement/getLine` before editing rather than trusting this table — there are nine.
  

  | File | Line | Method | Null default |
  | --- | --- | --- | --- |
  | `dom/StaffElement.java` | `:734` | `findLastAccidental` | **Return `null`.** See the note below — this one changes an exported value. |
  | `dom/RangeElement.java` | `:228` | `getAnchorElementIndex` | Return `-1`, with a comment that a detached anchor has no index |
  | `dom/RangeElement.java` | `:240` | `getEndElementIndex` | Return `-1`, same comment |
  | `dom/Song.java` | `:929` | `clearTempoIfOrphaned` | Early return — an element in no line can orphan nothing |
  | `dom/Song.java` | `:1706` | `withBeatDefiningEditOn` | No new branch: `owner != null ? owner.getParentLine() : null` already handles it |
  | `layout/LyricLayoutBuilder.java` | `:381` | `isHostOfPairedGraceColumn` | ~~Return `false`, falling back to `idealGraceHostUnionWidthSs`~~ — **superseded.** After `2cf80684` (`LyricRun`) moved the pairing rule onto `StaffElement.isPairedGraceNote()`, the method reads the pairing off the column's own element and needs no line, so there is no null branch and no fallback. Issue #723 is moot |
  | `ui/dialog/TempoChangeDialog.java` | `:121` | `clearChange` | Early return, skipping `clearTempoIfOrphaned` |
  | `ui/selection/SelectionCoordinator.java` | `:365` | `selectLyric` | Guard at the top and return before `clearSelection()` — selecting a lyric on an element in no line is meaningless, and `findLineIndex` would return `-1` |
  | `ui/component/ScoreViewController.java` | `:593` | `handleDelete` | Early return, matching the existing `index >= 0` guard at `:596` |
  | `ui/action/EditLyricAction.java` | `:81` | `performAction` | Early return |

Use a null guard, **not** `Objects.requireNonNull` — this project forbids it (`.agents/rules/development.md`, Null Handling).

**Reason each default out; do not test your way to it.** Until Phase 2 lands, no staff element ever has a null `parentLine`, so every guard added here is unreachable and the suite cannot tell you which default is right. Phase 2 brings all ten to life at once.

`findLastAccidental` **is the one that matters.** It feeds `getPitch()` (`StaffElement.java:641`) and MusicXML export (`io/musicxml/PitchSpelling.java:170`). Today a detached element scans a stale line and falls back to _that line's_ key signature; returning `null` means "no accidental found". The new answer is the correct one — an element in no line has no key context — but it is a different exported `<pitch>`, so state it in a comment at the call site. Phase 2 tests it. Get this one wrong and a wrong `<pitch>` is written into a saved MusicXML file with no error and no visible symptom — the only default in this plan that corrupts a document rather than the UI.

**Do not restructure** `RangeElement.getAnchorElementIndex` **/** `getEndElementIndex` **while you are in there.** Both resolve their endpoint with an `elements.indexOf` scan, and consolidating that across the ~14 call sites that repeat it is tracked as **issue #722**. Add the guard and nothing else.

7. Rework `StaffElementTest.testSetLinePropagatesLineToAllAttachmentsAndArticulations` (`:1130-1155`). Rename it — the method it names is gone — to something like `testAddElementPropagatesLineToAllAttachmentsAndArticulations`, and establish parentage the way production does, through `Line.addElement`, as `ParentLinePropagationTest.addToLine` (`:51`) already does. Do not add a test-only setter to keep the old call working.
  
8. Update `docs/clipboard.md:41` and `:139`, which describe re-parenting as `setLine`/`setParentLine`. Only `setParentLine` survives, and after Phase 2 the mechanism is named `Line.attach`.
  
9. Run `./scripts/compile.sh` (SUCCESS) and `./scripts/test.sh unit` (green). No behavior changed, so any failure here is a repoint that took the wrong default.
  

* * *
## ✅ Phase 2: Attach/Detach Chokepoint
**Status:** Complete **BlockedBy:** 1 **Files:** src/main/java/songscribe/dom/Line.java, src/main/java/songscribe/dom/LineElement.java, src/main/java/songscribe/dom/StaffElement.java, src/test/java/songscribe/dom/ParentLinePropagationTest.java, src/test/java/songscribe/dom/LineElementTest.java, src/test/java/songscribe/dom/StaffElementTest.java, src/test/java/songscribe/undo/MutationReplayerRoundTripTest.java, src/test/java/songscribe/undo/PasteReconciliationUndoTest.java **Recommended model/effort:** Opus 5, high effort — changes a DOM invariant that undo/redo replay and paste both ride on; a detach that fires on the wrong side of a re-parent silently orphans a live element.

Makes `parentLine` mean "the line I am in right now" for every `LineElement`, maintained in one pair of methods.
### Tasks
1. Add `detach` alongside the `attach` Phase 1 created:
  

```java
private void detach(StaffElement element) {
    // A re-parent that ran attach() first already owns this element — don't
    // clear a pointer that now names a different line.
    if (element.getParentLine() != this) {
        return;
    }

    element.setParentLine(null);
    element.propagateParentLine(null);
}
```

The `!= this` guard is what makes the invariant independent of call ordering: an add-then-remove re-parent is as safe as remove-then-add. Do not drop it on the grounds that no current path needs it — its value is that no future path has to know.

2. Call `attach`/`detach` from all five `elements` mutation sites, **inside** the existing `applyChange` mutator lambda, so parentage moves with the recorded change and not beside it.
  

  | Site | Method | Change |
  | --- | --- | --- |
  | `:228` | `addElement` | Move the `attach(element)` call from before `applyChange` into the lambda |
  | `:300` | `addElement(int, StaffElement)` | Same |
  | `:333` | `setElement` | `detach(oldElement)` → `elements.set(index, element)` → `attach(element)` |
  | `:1508` | `removeElement` | `detach(deleted)` |
  | `:1551` | `removeRange` | `detach` each of `deletedElements` before the `clear()` |

`setElement`**'s order is not interchangeable.** Detach must run first. If `element == oldElement` — a self-replace, reachable through the generic replacement paths at `PreviewElementManager:2037`, `Song:1255` and `Song:1455` — then attach-then-detach ends with a _live_ element holding a null `parentLine`, because the `!= this` guard sees `parentLine == this` and clears it. Detach-first is correct whether or not the two are the same object.

`removeRange` **already has the list it needs.** `Line.java:1529` builds `deletedElements = List.copyOf(elements.subList(from, to + 1))` before the mutation and passes it to `adjustHairpinsForDeletion`, the invalidation filter and the `ElementRangeDeletion` record. Detach over that list — do not build a second `subList` view of a list that is about to be structurally modified.

Moving `addElement`'s call inside the lambda is an intended behavior change: `applyChange` throws when no modification bracket is open (`Line.java:153-157`), and today the element is already parented by then, leaving it pointing at a line that never took it.

3. Make copies born detached. Delete `setParentLine(source.getParentLine())` from `StaffElement.java:124` (copy constructor) and `:177` (`copyStateFrom`), so `Line.attach` becomes the only writer of a staff element's `parentLine`.
  

Both matter, for different reasons. A clipboard clone or a `clone()` snapshot must not report itself live in a line it was never added to — Phase 3 rewrites liveness to trust exactly this field. And `copyStateFrom` is called by `MutationReplayer:108,149` on an element that **is** live, using a detached snapshot as the source; copying the snapshot's pointer over the live element's own is a no-op at best and a stale restore at worst.

Before deleting, run `jet_brains_find_referencing_symbols` on the copy constructor and on `clone()` and confirm no caller reads a copy's line before adding it. Known consumers: `ElementTypeAction:314`, `PreviewElementManager:1959`, `Fragment:171,250`, `InsertionSpacingCalculator:728`, `PitchShifter:325,379`, `NoteDragHandler:333`, `SelectionCoordinator:1070,1164`, `Line:373,375`.

4. Verify the undo/redo round trip by inspection before running tests, since a wrong answer here is silent. `MutationReplayer:94-101` and `:141-157` invert through `addElement` / `removeElement` / `setElement` / `removeRange`, so each inverse hits the mirrored attach or detach. `ElementModification` (`:106-108`, `:147-149`) deliberately preserves element identity via `copyStateFrom` and must **not** attach or detach.
  
5. Delete `LineElement.clearChildren` (`:384-390`) **and its test**, `LineElementTest.java:215-230` ("Row 30"). It has no production callers, and it clears `parentElement` without clearing `parentLine` — under the parent-chain walk that would leave a detached articulation as its own root, still pointing at the line, contradicting the invariant this phase establishes. `jet_brains_safe_delete` will report the test usage, so remove the test row in the same change.
  
6. Rewrite the `parentLine` Javadoc (`LineElement.java:46-50`). The current contract is the opposite of the new one:
  

> Null only during the construction window before the element is added to a line via `Line.addElement`. Code paths downstream of `Line.addElement` may treat this as non-null.

It becomes: null means the element is not in any line — either not yet added, or detached by a removal. Say that removals maintain it, that `Line.attach`/`detach` are its only writers for staff elements, and that it is the authoritative liveness answer. Reproduce the ownership diagram from the top of this plan as an ASCII comment above the field, and a short one above `Line.attach`/`detach` showing the five call sites feeding them.

7. Add tests pinning the invariant. Nothing else will catch a missed detach: no exception is thrown when a removed element keeps pointing at its old line, so the symptom is a stale selection that refuses to clear or a span index resolving against the wrong list, surfacing far from the cause. These tests are the only check on this phase. `ParentLinePropagationTest` already covers the attach direction (`testArticulationParentLineSetByAddElement`, `testAttachmentInheritsParentLineFromElement`, `testParentLineFollowsAttachmentToNewElement`) and is the natural home for the detach direction.
  

**In** `ParentLinePropagationTest`**:**

- `removeElement` detaches; `removeRange` detaches _every_ element in the range, not just the first.
  
- `setElement` detaches the replaced element and attaches the replacement.
  
- `setElement` replacing an element **with itself** leaves it attached (the detach-first ordering).
  
- Attaching to line B before detaching from line A leaves the element pointing at B (the `!= this` guard).
  
- A note's articulations and attachments follow the note's parentage in both directions.
  
- `clone()` yields a null `parentLine`, on the clone and on its children.
  
- `copyStateFrom` applied to a line-resident element leaves that element's own `parentLine` intact.
  

**In** `MutationReplayerRoundTripTest`**:** undoing each of `removeElement` / `removeRange` / `setElement` re-attaches, and redoing re-detaches; `ElementModification` replay leaves parentage untouched.

**In** `StaffElementTest`**,** against an element that has been removed from its line:

- `findLastAccidental()` returns `null`.
  
- `getPitch()` returns the value that follows from that, documented in the test.
  
- `RangeElement.getAnchorElementIndex()` returns `-1` for a detached anchor.
  

**In** `PasteReconciliationUndoTest`**:** after paste-then-undo, every pasted clone has a null `parentLine` and every surviving element points at its real line.

8. Run `./scripts/compile.sh` and `./scripts/test.sh unit`. Paste is the highest-risk consumer — `ui/clipboard/PasteSpanReconciliation.java` and `ScoreViewController.tryInsertFragment` add and remove spans and elements in the same operation — so treat any failure there as an ordering question, not a test to relax.
  

* * *
## ✅ Phase 3: Consume It in Selection
**Status:** Complete **BlockedBy:** 2 **Files:** src/main/java/songscribe/ui/selection/SelectionCoordinator.java, src/test/java/songscribe/ui/selection/SelectionCoordinatorTargetSelectionTest.java **Recommended model/effort:** Sonnet 5, low effort — deleting a workaround whose replacement is one line, against tests that already exist.
### Tasks
1. Collapse `SelectionCoordinator.isOnLine` (`:577-589`) to the single parent-chain rule. Delete the `StaffElement` membership arm (`:584-586`):
  

```java
private static boolean isOnLine(LineElement element, Line line) {
    var root = element;

    for (var parent = root.getParentElement(); parent != null; parent = root.getParentElement()) {
        root = parent;
    }

    return root.getParentLine() == line;
}
```

Keep the signature — it is `private static` and takes the line as a parameter.

2. Rewrite the two Javadoc blocks that describe the old mechanism, both of which are now wrong in opposite directions:
  

- `:564-576`, the `isOnLine` doc, whose second half explains why `parentLine` could not be trusted for a staff element. That paragraph goes; what remains is the parent-chain walk and why sub-elements need it.
  
- `:519-523`, inside `revalidateDecorationSelection`, which already asserts that _"an element that has been removed from the line has its_ `parentLine` _cleared"_ — a claim that is false today and becomes true only with Phase 2. It stays, but say plainly that `Line.detach` is what maintains it.
  

Record in the `isOnLine` doc that this replaces an O(n) `elements.indexOf` scan (`Line.java:1623-1625`) with a reference comparison, and that `revalidateDecorationSelection` runs after every mutation. Without that note, a future reader may "restore the safe membership check" and quietly reintroduce the scan.

3. Confirm `SelectionCoordinatorTargetSelectionTest.testRevalidateDecorationSelectionClearsSlideWhenOwningElementLeavesTheLine` (`:577`) still passes **unchanged**. It is the staff-element liveness case the membership arm existed to serve, it passes today via list membership, and it can only pass after this phase if Phase 2's detach is correct — making it the cross-phase regression check. If it needs edits, understand why before changing it.
  
4. Run `./scripts/compile.sh` and `./scripts/test.sh unit`.
