# Check findings — attribution double-click (working-tree diff)

Reviewed: the eleven uncommitted production files that add a double-click-to-edit gesture on the
attribution block (the composer/lyricist credit drawn above the first staff line). No test files
changed, so the Test Conformance axis did not run. Three axes ran: Design, Contract & API, and
Correctness & Efficiency.

The gesture itself works. The block is registered as clickable only on the first line, its
clickable rectangle matches the pixels actually painted, the two-staff-space blank margin below
the block is correctly excluded so it does not swallow clicks aimed at the music, and the dialog
cannot be opened during playback. What follows is one behavior change nobody asked for, four
structural findings, and a set of contracts and comments that now claim things that are not true.

---

## 1. Defect: shift + double-click on the credit block now opens a modal dialog

**Where** `LineComponent.java:805-826` (the block in `mouseClicked` that dispatches the
double-click gestures) and `LineComponent.java:855` (`allowsStaffEdit`).

**What the code does now.** Before this change one test admitted all three of the staff's
double-click-to-edit gestures — edit a lyric, edit an attachment, edit a key signature. That test
required four things at once: the left button, a click count of two, the shift key **not** held,
and "selection active" (which means SELECT mode, and not playing).

The change split that test in two. The left-button-and-count half moved out to the call site,
where it now admits all four gestures including the new attribution one. The other half — no
shift, plus selection active — stayed behind in the renamed `allowsStaffEdit` and now gates only
the three gestures that act on notation. The attribution gesture runs with neither.

**What's wrong with it.** Dropping the selection-active half for the attribution is deliberate and
documented: the credit should be editable in EDIT mode too, matching how the title and subtitle
open the same dialog from any mode. Dropping the **shift** half is neither documented nor, as far
as I can tell, intended.

The reason the shift exclusion exists is written in the predicate's own comment and is not about
mode at all: shift+click is the gesture that *extends* a selection, and these double-click probes
run before the selection handler ever sees the click, so a probe that claims the click takes it
away from the extend. That reasoning applies to the credit block exactly as it applies to a lyric.

What a user sees: with a range of notes selected, shift+double-clicking the credit block now opens
the modal Song Settings dialog. Before this change the click fell through and nothing happened. No
other gesture inside a staff line responds to shift+double-click by opening a modal dialog.

Both reviewing agents flagged this independently, and neither found anything stating it was meant.

**What to do.** Move `!e.isShiftDown()` up beside the left-button-and-count test in `mouseClicked`,
so it covers all four gestures the way it covered all three before. `allowsStaffEdit` then reduces
to a single call to `isSelectionActive`, at which point the wrapper method and its comment can be
deleted outright and the mode reasoning moved to the call-site comment that already explains the
split gate. This removes code rather than adding a condition, and it makes finding 9 below moot.

---

## 2. Design: the click vocabulary does not distinguish "what is under this point" from "what a click selects"

**Where** `HitTarget.java:182` (the new `Attribution` case), and the three switches it forced —
`LineSelectionHandler.java:309`, `SelectionCoordinator.java:512`, `ScoreViewController.java:~1105`.

**What the code does now.** `HitTarget` is a closed list of the kinds of thing a click on a staff
line can land on — a note head, a lyric syllable, a tie, a hairpin, and so on. Because the list is
closed, the compiler forces every switch over it to handle every kind. That is deliberate: each of
those three switches carries a comment saying an unhandled kind must fail to compile so a human
decides what it means rather than a default arm deciding silently.

Adding the credit block made all three fail to compile, and the answer given in all three was the
same — not applicable. Pressing on it selects nothing. Asking whether it is selected returns false
with a comment saying "nothing can make this true." Deleting it does nothing, joined onto an
existing empty branch.

**What's wrong with it.** Two of those three answers are for situations that can never arise.
Nothing ever puts the credit block into the selection, so the "is it selected?" query and the
delete handler can never receive one. The invariant "this is never selected" is now asserted in
three prose comments across three files, when the type system could carry it and the two branches
would not exist at all.

The codebase already made this argument against itself. `Selection.java` explains why the *range*
selection shape is composed into `Selection` rather than added as a `HitTarget` case:

> Adding it there would put an unreachable arm in every switch on the registry and renderer path,
> so it is composed in here instead.

That is precisely the cost this change just paid. The same paragraph's justification — that
`HitTarget#owner()` "answers for every variant of it" — is no longer true either (see finding 4).

The distinction is also already part of the project's vocabulary in prose: `docs/selection.md`
says of the three key-signature double-click targets, "These are edit targets, not selection
targets." It just is not a type.

**Symptoms this one change accounts for:** the unreachable branch in `SelectionCoordinator.isSelected`;
the extra label on the do-nothing delete branch in `ScoreViewController` and the sentence added to
its four-sentence explanatory comment; finding 4's wrong `owner()` answer, which stops being
writable; finding 11's now-false sentence in the `HitTarget` class comment, which needs only its
scope narrowed rather than a caveat.

**The corrected design.** Add a sealed sub-interface inside `HitTarget` — `Selectable` — and move
`owner()` onto it. The twelve kinds that `LineSelectionHandler.handlePress` actually selects
(lyric, staff line, slide, hairpin, ending, articulation, attachment, accidental, tie, beam,
trill, tuplet) declare `implements HitTarget.Selectable`; the credit block implements plain
`HitTarget`. Then `Selection.Target` holds a `Selectable`, and `SelectionCoordinator`'s selection
methods and `ScoreViewController`'s delete path take one. Selecting or deleting the credit block
stops compiling instead of being ruled out by a comment.

`LineSelectionHandler.handlePress` keeps every kind, including the credit block. That switch is the
one place where "what does pressing on this kind do" is a real decision, and its `false` there
says something true.

Worth settling in the same pass: the note head is selected as an index range rather than as a
target, and the grace-note slide is refused with a warning. If neither is ever selected as a
target, both move out of `Selectable` too and two more dead branches go with them.

**What it touches.** Five files: `HitTarget.java` (one nested interface, twelve `implements`
clauses, one method moved), `Selection.java` (the `Target` record's component type and its
rationale paragraph), `SelectionCoordinator.java` (three signatures, one branch deleted),
`ScoreViewController.java` (one label deleted), `LineSelectionHandler.java` (one signature). No
call site changes behavior; anything that would pass an unselectable kind to the selection stops
compiling, which is the point. No tests change.

**Recommendation: do it.** It converts an invariant currently asserted in three separate comments
into a rule the compiler enforces, and it removes the per-addition cost this change just paid.
Leave it alone and the next addressable-but-unselectable thing — a tempo mark, a page credit, the
title block if it ever stops being a Swing component — pays the same three-file tax and adds three
more comments claiming unreachability, each of which a reader has to re-verify by hand.

---

## 3. Design: a region's priority and its hover flag are decided by its kind, yet every call site restates them

**Where** `HitRegionBuilder.java` — the fifteen calls to `addRegion` (the new one at line 479) and
`addRegion` itself at line 700.

**What the code does now.** Every clickable area is registered with five arguments: the builder,
the shape, what the area is, a priority number deciding who wins when two areas overlap, and a
boolean saying whether mouse-move should test it.

**What's wrong with it.** Across all fifteen registrations, the priority and the boolean are
completely determined by the third argument. A note head is always `ELEMENT` and never
hover-tested. A lyric is always `LYRIC` and is the only thing ever hover-tested. The new credit
block is always `ATTRIBUTION` and never hover-tested. So two of five arguments repeat what a third
already says, and nothing stops a future registration from repeating it wrongly — a mistyped
priority compiles and silently changes which thing the user selects when two areas overlap.

This also breaks two of the project's own Java signature rules, which the fifteenth call site has
now joined: more than four parameters requires a parameter object, and a boolean that selects a
mode requires an enum. At the call site the boolean is a bare `false` that names nothing.

The same fact is then written out in prose in three more places, and two of the three are wrong
today. `HitRegionBuilder`'s class comment carries a hand-drawn table mapping each layout source to
its target and priority; this change had to add a row to it, and the row's arrow column is one
character out of alignment. `HitPriority`'s class comment lists two ordering constraints that
"must not be reordered", enforced by nothing. And `HitRegistry`'s class comment lists the priority
order a third time — that list already omitted trills and tuplets before this change, and now
omits the credit block as well. A reader who trusts it gets the wrong answer.

**The corrected design.** Make `HitPriority` an enum rather than a bag of `int` constants,
declared in priority order so the ordering its comment insists on becomes the declaration order.
Give `HitTarget` two methods: the priority of the kind, and whether it is hover-testable.
`addRegion` then takes three arguments instead of five and reads both off the target. The table in
`HitRegionBuilder`'s comment loses its priority column, and `HitRegistry`'s prose list is deleted
in favour of pointing at the enum.

**What it touches.** Four files: `HitPriority.java` (int constants become enum constants),
`HitTarget.java` (each of sixteen kinds names its priority once), `HitRegionBuilder.java` (fifteen
call sites drop two arguments, two comment blocks shrink), `HitRegistry.java` (the comparison
compares enum constants, one stale prose list deleted). No tests change.

**Recommendation: do it.** It is the reason this one feature had to record a single fact — "the
credit block ranks here" — in four places, and it is why two of those four are already wrong. It
also satisfies both signature rules at once instead of introducing a parameter object and an enum
separately.

---

## 4. Design: the new hit target carries a value nothing reads, and its one method answers wrongly

**Where** `HitTarget.java:182-188`.

**What the code does now.** The new `Attribution` case holds a reference to the song's attribution
object, and implements the interface's `owner()` method by returning it.

**What's wrong with it.** Two separate things.

First, nothing reads the reference. The double-click handler tests only *that* the click was the
credit block and never looks inside; the selection code answers false; the delete handler does
nothing. The reference also carries no information a reader could not get anyway — there is
exactly one attribution object in a song, held in a permanent field on `Song`, so any code wanting
it would ask the song. `StaffLine` is the existing precedent for this shape: a case with no
payload at all, for the thing that is not hung off any element.

Second, `owner()` gives an answer that is wrong by the contract it fills. That method exists for
one purpose, spelled out in the interface: after an undo or a deletion, the selection code takes
the selected thing's owner, walks up its parent chain, and checks it is still attached to a line —
if not, the selection is stale and is cleared. The attribution object is never attached to any
line; it lives on the `Song`. So the answer this method gives is the one the liveness rule reads as
"stale". Nothing breaks today because nothing selects the credit block. It breaks the day someone
makes it selectable — to drag its vertical offset, which the object already supports — and the
selection clears itself the instant it is made, for a reason nobody will find quickly.

**What to do instead.** Make it `record Attribution() implements HitTarget` with `owner()`
returning `null`, exactly like `StaffLine`. The interface already documents `null` as "this cannot
go stale", which is precisely true of a song-owned block. That deletes a value with no reader and
makes the liveness answer correct rather than accidentally harmless.

**What it touches.** Two files: `HitTarget.java` (the record), `HitRegionBuilder.java` (the
construction site drops its argument, and the loop no longer needs the map key). One file if
finding 2 is taken at the same time, since `owner()` moves off this case entirely.

**Recommendation: do it**, and do it whether or not finding 2 is taken — it is a latent wrong
answer sitting behind a value nobody uses.

---

## 5. Design: two ways to open one dialog, and the new one skips the bookkeeping the old one's contract says every caller keeps

**Where** `SongSettingsOpenAction.java:63` (the new `openAt`), against `DialogOpenAction.open()`.

**What the code does now.** The base class for dialog-opening actions has an `open()` method that
builds the dialog, remembers it while it is on screen, shows it, and lets go of it afterwards. Its
contract is explicit that the remembering is "what makes this, rather than `newDialog()`, the
entry point every caller uses" — without it, a second invocation of a non-modal dialog puts a
second window beside the first. The new `openAt(section)` builds and shows by a different route
and never records anything.

**What's wrong with it.** Nothing breaks today. Song Settings is modal, so the show blocks the
event thread and a second one cannot be reached — which `open()`'s own contract names as the
exempt case. But `SongSettingsOpenAction` now publicly offers two ways to open its dialog, one
class apart, with comments giving different accounts of why each is safe, and only one keeping the
bookkeeping. The next person adding an "open on tab X" convenience to a *non-modal* action will
copy this one and get a second window. "Build it, show it, drop it" is also now written twice.

To be clear about what this change did and did not introduce: `BaseTitleComponent` already
bypassed `open()` this way before the diff. The change extracted the bypass into a public method
on the action, which is what turns a one-off into a pattern with a name.

**The corrected design.** Give the base class one opening path that takes how to show as a
parameter — a protected `open(Consumer<T> show)` where the public `open()` passes
`d -> d.setVisible(true)` and `openAt` passes `d -> d.show(section)`. The already-open check, the
remembering and the release then happen once, in the class that owns them, and `openAt` becomes a
one-line call into it.

**What it touches.** Two files: `DialogOpenAction.java` (one method gains an overload, the existing
body moves into it), `SongSettingsOpenAction.java` (`openAt` delegates). No call site changes, no
tests change.

**Recommendation: do it.** It leaves one entry point rather than two, so the exemption that makes
the second one safe today does not have to be re-derived by whoever copies it next.

---

## Contract findings

### 6. The new preview-ownership query promises something the code it fronts does not deliver

**Where** `PreviewElementManager.java:874` (`isPreviewClickTarget`), and its two callers at
`PreviewElementInserter.java:69` and `LineComponent.java:937`.

**What the code does now.** In EDIT mode a ghost note follows the pointer, showing where a click
would insert. The ghost is not a registered clickable area, so the new attribution gesture cannot
ask the hit registry whether the ghost owns the click — this new method answers instead, and the
attribution gesture stands down whenever it says yes. Its contract promises:

> When this returns `true`, `PreviewElementInserter.handleClick` acts on the click, so a competing
> gesture must decline it.

**What's wrong with it.** `handleClick` does not keep that promise. After the gate this query
fronts, it has six further ways to return having done nothing: the line is null; the position is
blocked by the auto-maintained terminal; a direct click on the terminal the current ghost may not
replace; a blocked breath-mark insertion; `canReplaceElementAt` refusing a grace note, key
signature or barline; and the user cancelling the accidental-restatement confirmation. I read all
six in the body.

So a caller who reads this clause and reasons "the click is handled either way" is reasoning from
a false premise. Nothing misbehaves today — in the strip where the two gestures overlap, a
double-click that the inserter then refuses does nothing, but it did nothing before this change
too, so no behavior regressed. The cost is that the clause is the wrong kind of promise: it
promises an *action* by another class, which this class cannot guarantee, when what the caller
needs is a statement of *ownership*.

**What to do.** Restate the promise as ownership: when this returns true the inserter owns a click
on this line right now, so a competing gesture must decline it — without claiming what the
inserter will then do with it. Then fix the same overclaim where it is repeated from the other
side, in `editDoubleClickedAttribution`'s contract at `LineComponent.java:932`, which says "Within
that band both clicks of the pair insert an element". When the insert is refused, neither does.

### 7. That same query documents neither what it returns nor what it is asked about

**Where** `PreviewElementManager.java:874`.

It has a four-sentence doc comment, a `boolean` return and a `LineComponent` parameter, and carries
neither `@return` nor `@param`. The `@return` tag is what a caller sees at the call site, and both
callers' correctness turns on which way this answers. Add `@return` saying what `true` means and
`@param lc` saying which line is being asked about.

### 8. The new dialog-opening method never says the caller must have ruled out playback

**Where** `SongSettingsOpenAction.java:63`.

The action it lives on is built with a "disable while playing" flag, which greys out the *menu
item* during playback. `openAt` does not consult that flag — it goes straight to building and
showing. The neighbouring code already knows this: `BaseTitleComponent.openEditor`'s contract says
"The gesture's playback condition is applied by the caller and is deliberately not re-derived
here." That is a precondition, and it is written on the caller instead of on the method that
requires it, so a third caller has no way to discover the obligation. State it on `openAt`.

### 9. `allowsStaffEdit` no longer answers on its own, and nothing says so

**Where** `LineComponent.java:855`.

It used to test three things and now tests two, because the left-button-and-count test moved out to
the caller. So it is meaningful only once the caller has already established a left double-click —
call it on a single click and it answers `true`. Its opening sentence still reads as though it
decides the whole question, and so does its name.

If finding 1 is fixed as recommended, this method collapses to a single call and is deleted, and
this finding goes away with it. If it survives in some other form, it needs a sentence saying the
caller must already have established a left double-click.

### 10. The new priority constant describes itself two ways, both inaccurate

**Where** `HitPriority.java:94`.

Its comment says the credit block ranks "Below every notation kind" and "last among the notation
kinds". Neither holds: the staff-line fallback ranks below it, and the staff line is notation. And
the target's own comment at `HitTarget.java:182` says the credit block "is the one target in this
interface that is not notation" — so calling it one of the notation kinds contradicts the type it
describes.

Nothing misbehaves; the two regions do not overlap. The cost is to the next person choosing a
number, who reads the ordering rule as stricter than it is. Restate it as what is true: it
outranks only the staff-line fallback, and ranks below every kind that names a specific piece of
notation, so any future overlap resolves to the more precise target.

### 11. The `HitTarget` class contract no longer describes all its cases

**Where** `HitTarget.java`, the interface's class-level comment.

It opens: "Every target names what it selects **by object reference**". The credit block names
nothing it selects — that is the whole point of the case. The same paragraph's reasoning about
index-versus-identity addressing is about selection staleness, which does not apply to it either.

If finding 2 is taken, the type states this and the paragraph only needs its scope narrowed to the
selectable half. If it is not, the class comment has to say in prose that the vocabulary now holds
two kinds of target — those a press selects and those that exist only so a gesture can resolve to
them — and which methods each kind reaches.

---

## Correctness & Efficiency findings

The Correctness axis found no production bug. It traced and cleared all three areas it was asked
about: the inserter's rewritten entry guard is exactly equivalent to what it replaced; the
reshaped double-click gate leaves the other three gestures' combined condition identical; and the
first-line-only assumption behind the new registration holds, because only line zero's layout ever
contains a credit-block entry. It also confirmed the gesture cannot fire during playback, since
`mouseClicked` returns early on playback well before the gate.

I separately confirmed that `getCurrentInsertionLine()` still has three production callers, so the
plan's instruction to delete it if orphaned correctly did not apply.

### 12. A press on the credit block is the one press that repaints nothing

**Where** `LineSelectionHandler.java:309`.

**What the code does now.** The new branch answers `false` for a press on the credit block, under a
comment saying "a press over the attribution does exactly what a press over the empty space above
the staff does."

**What's wrong with it.** It does not do exactly that. The empty-space case — a press hitting no
registered area at all — repaints the line before answering false, and has since the hit registry
was introduced. The new branch does not, and the repaint at the end of the method only fires when
the press *was* handled. Registering the credit block as clickable converted what used to be a
miss into a hit, and the repaint was lost in the conversion. I could not identify a specific stale
pixel this produces, since selection clearing does its own repainting, so the visible consequence
may well be nothing. But the comment asserts a parity the code does not have, and a reader will
rely on it.

**What to do.** Move the repaint out of the miss branch to after the switch, so "the press selected
nothing" repaints once regardless of which branch produced it, and delete the repaint from the
miss branch. The comment's claim is then true by construction.

### 13. The new query re-spells a comparison the class already has a name for

**Where** `PreviewElementManager.java:874`.

`isPreviewClickTarget` is written as `shouldHandlePreviewElement(lc) && currentPreviewLine == lc`.
The second half is character-for-character what the existing `hasPreviewElement(lc)` already is. It
should call it. As it stands there are now three names in this class for one fact — "the line the
ghost is on" — reached through `getCurrentInsertionLine()`, `hasPreviewElement(...)` and
`isPreviewClickTarget(...)`.

---

## Comments and docs that now say something untrue

Each is small and concrete.

14. **`PreviewElementInserter.java:118`** — "shouldHandlePreviewElement (checked at entry)
    guarantees a preview element". That is no longer what is checked at entry; the new query is.
    The claim survives only because the new query calls the old one, but the comment names a call
    that is not there.

15. **`ScoreViewController.java:~1102`** — the extended comment sends the reader to
    `SelectionCoordinator.isSelected` for why the credit block is never selected. That method just
    returns `false`; it restates the claim rather than establishing it. The invariant actually
    lives in `LineSelectionHandler.handlePress`, which is where the neighbouring comment in
    `SelectionCoordinator` correctly points.

16. **`HitRegistry.java:~40`** — the class comment's priority list, highest first, omits trills and
    tuplets (already wrong before this change) and now omits the credit block too. Covered by
    finding 3; listed separately because it is wrong today regardless of whether finding 3 is
    taken.

17. **`docs/selection.md`**, closing section — the only place the codebase documents double-click
    edit targets, ending with the distinction this change leans on: "These are edit targets, not
    selection targets." The credit block is now a fourth such target, and the first reached through
    the hit registry rather than through a dedicated layout query — which is exactly what forced
    the three switch branches in finding 2. The doc should state the rule in concepts: a registry
    target may be an edit target without being a selection target, and the selection and delete
    paths never see one.

18. **`HitRegionBuilder.java:~475`** — the new comment writes `{@link
    Attribution#ATTRIBUTION_MARGIN_BOTTOM_SS} of blank air`, which renders as the constant's name
    where the sentence is quoting its amount. The project's Java rules ask for `{@value}` in
    exactly this case and reserve `{@link}` for referring to the constant as a thing. The constant
    is a `static final double`, so `{@value}` is legal here.

19. **`HitRegionBuilder.java:~86`** — the row this change added to the class comment's hand-drawn
    table has its arrow column one character out of alignment with its neighbours.

---

## Examined and judged not to be a finding

**Clipping the credit block's clickable rectangle to avoid the overlap with note insertion.** One
agent proposed shrinking the block's clickable area by one staff space at the bottom, so it stops
where the insertable pitch range begins and the two gestures never compete. I verified the geometry
it rests on — the insertable range does reach three staff spaces above the top staff line while the
block's drawn box stops two above it, so there is a one-staff-space overlap. I am not recommending
the clip. The overlap only *matters* in EDIT mode, where the ghost note is live; in SELECT mode
there is no contention at all, and clipping would make the bottom staff space of the credit text
un-double-clickable there too, for no gain. The arbitration the change chose — the ghost wins
wherever it is live — is the right rule. What was wrong was only how the query stating that rule
was worded, which is finding 6.
