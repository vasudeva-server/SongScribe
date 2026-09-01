# Design Pass 3 — Step 9: Diagrams
## What it found
**The target holds no diagram.** All nine files in `src/main/java/songscribe/engraving/`, plus the two test classes and the test `package-info`, contain no `<pre>` block, no fenced block, no figure drawn in box or arrow characters. There is nothing in the target for this step to delete.

**No** `docs/*.md` **document is this subsystem's.** Step 1 found none, and the read confirms it: no document in `docs/` describes staff geometry or the pitch grid. What the package promises is stated in `engraving/package-info.java` and in the class Javadoc of `Staff` and `StaffPosition`.

**Two diagrams elsewhere describe geometry this pass moved.** Both are in `docs/layout-geometry.md`, which is row 18's document, not this target's:

| Diagram | Lines | What it draws | Where that code lives |
|---|---|---|---|
| Ledger-line extent | `docs/layout-geometry.md:7-26` | the horizontal run of one ledger line: `headLeft`/`headRight`, the overhang each side, and the accidental clamp | `NoteGeometry.getLedgerLineGeometry` and `NoteGeometry.LedgerLineGeometry.extentAtSs` |
| Stem-tip height | `docs/layout-geometry.md:54-67` | which edge of an element the optical stem-overlap correction measures from, stem up versus stem down | `layout`'s stem-overlap correction |

Neither states anything the pass changed. The pass re-pointed the constants they depend on — `LedgerLine.LENGTH_FRACTION` is now cited by name from `NoteGeometry.getLedgerLineGeometry`, and stem thickness and length are now on `StemMetrics` — while the arrangement each diagram draws is untouched.

**The ledger-line extent diagram states one thing the code contradicts.**`docs/layout-geometry.md:23-24` reads:

> accidental clamp midpoint = midway between the accidental's right edge and headLeft — i.e. right of ledgerLeft.

`NoteGeometry.java:586` is `Math.max(baseExtentSs.leftSs(), (accidentalRightSs + headLeftSs) / 2)`, and the comment two lines above it says so outright: _"The max is the only guard … there is no accRight > ledgerLeft precondition."_ The midpoint exceeds `ledgerLeft` only while `accRight > headLeft − ½ × notehead width`; an accidental sitting further left than that puts the midpoint left of `ledgerLeft`, and the `max` keeps the base extent. The clause asserts as unconditional a relation the code exists to not assume.
## What it proposes
Each item is separately decidable.

1. {==**Keep both diagrams**==}{>>ok<<}{id="c1" by="user" at="2026-08-28T23:15:36.295Z"}**.** Each draws a spatial arrangement — which edge is measured from which, and in which direction — that its prose states worse. That is the criterion, and it is the stated purpose of the document they sit in. Neither restates a contract, and neither walks through a sequence.
  
2. {==**Correct the false clause**==}{>>ok<<}{id="c2" by="user" at="2026-08-28T23:15:44.594Z"} at `docs/layout-geometry.md:24`, so the diagram states the clamp as the code applies it: the ledger's left end is pulled in to the midpoint where that midpoint lies inside the base extent, and the base extent stands otherwise. The document belongs to row 18, but this is a defect in reach of this pass, and it is one line.
  
3. {==**Add no diagram to the target**==}{>>ok<<}{id="c3" by="user" at="2026-08-28T23:15:51.113Z"}**.** Every claim the pitch grid makes — the line/space alternation, the Y-down sign, the outermost position a note head reaches without a ledger line, the `MIN_SP`..`MAX_SP` bounds — is stated in `StaffPosition`'s class and method Javadoc and is checkable there against the arithmetic beside it. A figure would repeat it in a second form that nothing compiles.
  
  The one place this is arguable is `LedgerLine.forEachOffsetSs`, whose promise is carried by a sentence rather than by arithmetic a reader can follow: _"a note in a space takes the same run starting from the line between it and the staff, which is why its offsets are half a staff space out of step with the note."_ See the open question.
  
## Open questions
{==**Does** `LedgerLine.forEachOffsetSs` **want a four-line figure in its Javadoc?**==}{>>no<<}{id="c4" by="user" at="2026-08-28T23:16:02.306Z"}

For: it is the only promise in the package a reader cannot check against the expression beside it — the half-space offset falls out of `lineSp = position + towardStaff` for a note in a space, and seeing one note on a line and one in a space with their ledger runs drawn settles it at a glance where the sentence has to be read twice.

Against: the figure would be the fourth statement of the same grid — `Staff`, `StaffPosition` and this method each already describe it in prose — and Javadoc art has no compile step, so it drifts silently while the arithmetic it draws stays right. `LedgerLineTest` already pins the offsets for a note on a line and a note in a space, which is the check the figure would only illustrate.

I have not decided this one; the sentence is correct as it stands, so this is a question about legibility rather than about accuracy.

---
comments:
  c5:
    body: >-
      Taken. Both diagrams stay as they are; the only edit to
      docs/layout-geometry.md is item 2's one line.
    by: AI
    at: "2026-08-28T23:16:27.000Z"
    re: c1
  c6:
    body: >-
      Applying. The clause "— i.e. right of ledgerLeft" goes, and the sentence
      that follows gains the condition the max enforces: the ledger's left end is
      pulled in to the midpoint unless that midpoint falls left of ledgerLeft, in
      which case the base extent stands.
    by: AI
    at: "2026-08-28T23:16:27.000Z"
    re: c2
  c7:
    body: Taken. No figure is added anywhere in engraving.
    by: AI
    at: "2026-08-28T23:16:27.000Z"
    re: c3
  c8:
    body: >-
      Taken as no. LedgerLine.forEachOffsetSs keeps its prose unchanged and gets
      no figure. The question is closed, not carried forward.
    by: AI
    at: "2026-08-28T23:16:27.000Z"
    re: c4

