# #614 — Manual test matrix (paste span reconciliation)

Cases to exercise in the running app. The reconciliation logic itself is covered by
5553 unit tests; **what this matrix is for is everything those can't see** — rendering,
spacing after a span is discarded, dialogs, mode interaction, and whether the result
looks like correct notation rather than merely a correct data structure.

Each row is marked:

- **[R]** — *rendering/interaction only.* The decision is unit-tested; you're checking
  it draws and behaves correctly.
- **[U]** — *unverified by tests.* No unit test covers this; the app is the first check.

---

## 0. Conventions and fixtures

Menu/action labels below are the real strings: **Toggle Beam**, **Tuplet**,
**Add Crescendo**, **Trill**, **Make First-Second Ending**.

Two fixtures recur. Build them once per line and reuse:

- **F1 — beamed group.** 8 quavers on one line. Select notes 2–5, **Toggle Beam**.
- **F2 — beamed triplet.** 3 quavers, **Tuplet** (grade 3), then **Toggle Beam**.

"Paste-replace" = make a music selection, then Cmd+V.
"Paste mode" = no selection, Cmd+V, then click an insertion point (overlay reads
*"Placing pasted content — Click or Return to place, Escape to cancel"*).

Unless a row says otherwise, **undo once after every case** and confirm the score
returns exactly to its prior state, spans included. Undo is the single most likely
place for this change to have gone wrong, and it is cheap to check every time.

---

## 1. Headline cases from the issue

| # | Setup | Action | Expected |   |
|---|---|---|---|---|
| 1.1 [R] | F2 copied. Destination: another F2. | Paste mode, click between notes 1 and 2 of the destination triplet | **Both tuplet brackets and both beams gone.** Six bare quavers. This is the issue's literal example. | x |
| 1.2 [R] | F2 copied. Destination: F2. | Select the destination's *entire* triplet, paste | Destination triplet replaced wholesale; **pasted tuplet bracket and beam survive intact** | x |
| 1.3 [R] | F1 copied (notes 2–5 with beam). Destination: F1. | Select destination notes 3–4 only, paste | Destination beam gone; **pasted beam gone**; all notes bare | x |
| 1.4 [U] | F1 copied. Destination: F1. | Select destination notes 4–7 (clips the beam's tail), paste | Destination beam gone (endpoint deleted); **pasted beam survives** — this is the deliberate narrowing, confirm it looks acceptable in practice | x |
| 1.5 [R] | Crescendo over destination notes 2–5. Fragment contains its own crescendo. | Paste mode, click inside the destination hairpin | **Destination hairpin kept and widened** over the pasted notes; pasted hairpin dropped. One hairpin, not two. | x |
| 1.6 [R] | Crescendo over destination notes 2–5. Fragment contains a crescendo. | Select notes 2–5 exactly, paste | Destination hairpin replaced by the **pasted** hairpin, covering only the pasted notes | x |

---

## 2. Span kind × placement

For each kind, build the span over destination notes 2–5 and paste a 2-note fragment.
Rows marked *(same-kind)* mean the copied fragment also carries a span of that kind.

| # | Kind | Placement | Expected destination | Expected pasted *(same-kind)* |   |
|---|---|---|---|---|---|
| 2.1 [R] | Tuplet | strictly inside | removed | dropped | x |
| 2.2 [R] | Tuplet | at the anchor (before note 2) | **kept**, shifted right | kept | x |
| 2.3 [R] | Tuplet | just past the end | kept | kept | x |
| 2.4 [R] | Tuplet | replace exactly | gone with deletion | kept | x |
| 2.5 [R] | Beam | strictly inside | removed | dropped | x |
| 2.6 [R] | Beam | at the anchor | **kept**, shifted right | kept | x |
| 2.7 [R] | Beam | replace interior only | removed | dropped | x |
| 2.8 [R] | Tie | strictly inside | **removed** | kept | x |
| 2.9 [U] | Tie | at the anchor | kept | kept — *check the tie still draws to the right note* | x |
| 2.10 [R] | Trill | strictly inside | **removed** | kept | x |
| 2.11 [U] | Trill | replace interior only | removed | kept — *check no orphaned trill squiggle remains* | x |
| 2.12 [R] | Crescendo | strictly inside | **kept, widened** | dropped | x |
| 2.13 [U] | Diminuendo dest. / Crescendo frag. | strictly inside | **removed** — the fragment contradicts it | **kept**, nested where the destination's was | x |
| 2.14 [U] | Crescendo | at the anchor | **merged** into the fragment's | kept, widened to cover both — *one wedge, not two abutting ones* | x |
| 2.15 [U] | Crescendo | just past the end | **merged** into the fragment's | kept, widened to cover both | x |
| 2.16 [U] | Diminuendo dest. / Crescendo frag. | at the anchor | kept | kept — *two abutting hairpins of opposite type must both draw* | x |
| 2.17 [U] | Crescendo, fragment's hairpin **not** reaching the fragment edge | at the anchor | kept | kept — *a plain note between the two, so no merge* | x |

**2.2 / 2.6 are the ones to look at hardest.** Inserting *at* a span's anchor keeps
the span and pushes it right. That's correct by the rules, but it's the case where
"correct data structure" and "looks right on the page" are most likely to diverge —
check the bracket/beam actually redraws over the intended notes and the pasted notes
sit outside it. Hairpins are the exception: at an anchor they *merge* rather than
shift (2.14–2.15), so there the thing to check is that one continuous wedge is drawn
across the whole span, with no seam where the two used to meet.

**2.14–2.17 also test plain editing**, not just paste — the merge is
`Line.addCrescendo`'s, the same one that fires when you draw a hairpin flush against
an existing one. Worth confirming that directly: draw a crescendo over notes 1–2,
then another over 3–4, and expect a single wedge over 1–4.

---

## 3. Endings

The confirm dialog is titled **First-Second Ending** and reads
*"This will invalidate and remove the first-second ending. Continue?"*

| # | Setup | Action | Expected |   |
|---|---|---|---|---|
| 3.1 [R] | First-second ending; fragment is plain notes | Paste mode, click inside the ending | Ending **kept**, bracket widens over the pasted notes | x |
| 3.2 [R] | First-second ending; fragment contains a barline or repeat | Paste mode, click inside the ending's interior | **Confirm dialog appears.** Accept → paste proceeds, ending removed (the pasted barline invalidates it) | x |
| 3.2a [R] | Same as 3.2 | Decline the confirm | **Nothing pasted, ending intact**, paste mode exits, clipboard retained | x |
| 3.3 [R] | First-second ending; fragment itself contains a full ending | Paste mode, click inside the destination ending | **No nested brackets.** The pasted ending is always dropped. A fragment carrying a whole ending also carries its barlines, so in practice the 3.2 confirm fires and the destination ending goes too — accept it and expect **no ending bracket at all** | x |
| 3.4 [R] | First-second ending | Select a range whose deletion invalidates the ending, paste | **Confirm dialog appears.** Accept → paste proceeds, ending gone | x |
| 3.5 [R] | Same as 3.4 | Decline the confirm | **Nothing happens at all** — score unchanged, selection still active, clipboard still holds the fragment (paste again to prove it) | x |
| 3.6 [R] | Ending with a REPEAT_RIGHT split between first and second | Paste a fragment containing a barline/repeat at the split boundary | **Confirm, then ending removed.** The split boundary is not exempt — a barline landing there sits *before* the split, leaving the first span with two. Plain notes there still paste silently | x |
| 3.7 [U] | Ending with a split | Paste strictly inside the *second* sub-span | Confirm, then ending removed, if the pasted content contains a barline/repeat; kept silently otherwise | x |

Rows 3.6/3.7 were the biggest known test gap, and the gap hid a real bug: pasting a
fragment ending in a right repeat at the split boundary left the ending in place with
two adjacent repeats in its first span. `Ending.isInvalidatedByInsertion` no longer
exempts the split boundary, and the paste tests now run against
`EndingLineFixture.primary()` rather than a plain-note stand-in.

**3.6 is worth checking by hand-editing too**, since the rule is shared: inserting a
barline just before the split with the mouse should now prompt where it used to
silently produce a double repeat.

---

## 4. Paste mode (no selection)

| # | Action | Expected | |
|---|---|---|---|
| 4.1 [R] | Cmd+V with no selection | Overlay pill appears; **all menus and toolbar buttons disabled** | ☐ |
| 4.2 [R] | Move the mouse along a line | Insertion marker tracks between elements, never on top of one | ☐ |
| 4.3 [R] | Click inside a beam group | Places, beam removed, **mode exits** | ☐ |
| 4.4 [R] | Return with a tracked point | Same as a click | ☐ |
| 4.5 [R] | Escape | Mode exits, nothing pasted, **clipboard retained** | ☐ |
| 4.6 [R] | Click outside any line | Mode exits, nothing pasted | ☐ |
| 4.7 [U] | Background the app while in paste mode | Mode exits cleanly; **return and confirm no stray overlay and no leaked listener** (resize the window a few times) | ☐ |
| 4.8 [U] | Enter paste mode, exit, re-enter — 5× | Overlay still sized correctly after each; resize the window at the end | ☐ |
| 4.9 [R] | Paste into a nearly-full line | *"There isn't enough room on this line for the pasted elements."* — **mode stays active**, try again elsewhere | ☐ |

---

## 5. Undo

| # | Setup | Action | Expected | |
|---|---|---|---|---|
| 5.1 [R] | Case 1.1 (both groups destroyed) | One undo | **Everything back in one step** — both tuplets, both beams, all notes | ☐ |
| 5.2 [R] | Case 1.3 | One undo | Destination beam restored over its original notes | ☐ |
| 5.3 [R] | Case 1.5 (hairpin widened) | One undo | Hairpin back to its original span, not left widened | ☐ |
| 5.4 [R] | Case 3.4 (ending destroyed, confirmed) | One undo | Ending restored | ☐ |
| 5.7 [U] | Case 2.14 (hairpins merged) | One undo | **Both** hairpins back as they were — the destination's over its own notes, the pasted one gone; not one merged wedge left behind | ☐ |
| 5.8 [U] | Draw a crescendo over 1–2, then one over 3–4 (they merge) | One undo | The second crescendo gone and the first restored over 1–2, in one step | ☐ |
| 5.5 [U] | Any of the above | Undo then **redo** | Returns to the pasted state, spans included | ☐ |
| 5.6 [U] | Case 1.1 | Undo, then check the undo menu item's label | Should read **Paste**, not "Add Note" | ☐ |

---

## 6. Interaction with existing paste behaviour

These aren't new in #614 but share the code path and are worth a pass.

| # | Case | Expected | |
|---|---|---|---|
| 6.1 [R] | Copy a range ending just before a breath mark | Breath mark comes along | ☐ |
| 6.2 [R] | Copy a range ending on a paired grace note whose host is outside | Grace note dropped from the copy, and any span touching it | ☐ |
| 6.3 [R] | Copy a range containing the final double barline, paste mid-line | Becomes a plain double barline — never a second "final" | ☐ |
| 6.4 [R] | Paste the same fragment 3× at different points | Three independent results; **spans on each anchor to their own notes** | ☐ |
| 6.5 [R] | Paste into a hyphenated word / melisma | Lyric chains stay valid on both seams | ☐ |
| 6.6 [U] | Copy in document A, paste into document B, inside a beam group in B | Reconciliation applies normally against B's spans | ☐ |
| 6.7 [U] | Cut (not copy) from inside a beam group, then paste elsewhere | Cut's own ending confirm still behaves; pasted content is clean | ☐ |

---

## 7. Rendering and spacing

The reconciliation deletes spans mid-paste, which is exactly when a stale layout cache
would show. Worth a deliberate look rather than trusting the earlier rows.

| # | Check | |
|---|---|---|
| 7.1 [U] | After a beam is discarded, the affected notes' **stems and flags** redraw correctly (flags return where the beam was) | ☐ |
| 7.2 [U] | After a tuplet bracket is discarded, **no ghost bracket or stray "3"** remains | ☐ |
| 7.3 [U] | A widened hairpin (1.5) draws as **one** wedge across the full span, not two abutting ones | ☐ |
| 7.4 [U] | Horizontal spacing after the paste looks even — no double gap where a span was removed | ☐ |
| 7.5 [U] | Scroll away and back / force a repaint after each of the above; nothing changes | ☐ |
| 7.6 [U] | Zoom in and out after a reconciled paste; spans still align to their notes | ☐ |
| 7.7 [U] | Save, close, reopen a file containing a reconciled paste; **spans persist as displayed** (MusicXML round trip) | ☐ |

7.7 is worth doing at least once — nothing in this work touched the MusicXML writer,
but it's the cheapest way to confirm the resulting span set is actually well-formed.

---

## Priority if time is short

1. **1.1, 1.2, 1.3** — the issue's own examples
2. **3.3, 3.4, 3.5** — nested endings and the new confirm, the two riskiest additions
3. **5.1** — one undo restores everything
4. **2.2 / 2.6** — insert-at-anchor, where correct-but-looks-wrong is most likely
5. **7.1, 7.2** — stale rendering after a span is discarded
