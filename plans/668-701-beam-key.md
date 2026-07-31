# Replace auto-beaming with an explicit `b` key in edit mode

Refs #668.

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Remove auto-beaming](#-phase-1-remove-auto-beaming) | ✅ Complete | — |
| 2 | [Span-identity helpers](#-phase-2-span-identity-helpers) | ✅ Complete | — |
| 3 | [Insertion target lifecycle](#-phase-3-insertion-target-lifecycle) | ✅ Complete | — |
| 4 | [The toggle operation](#-phase-4-the-toggle-operation) | ✅ Complete | — |
| 5 | [Wire the key](#-phase-5-wire-the-key) | ✅ Complete | — |
| 6 | [Manual verification](#-phase-6-manual-verification) | ✅ Complete | — |
| 7 | [Model-layer tests](#-phase-7-model-layer-tests) | ✅ Complete | — |
| 8 | [Operation tests](#-phase-8-operation-tests) | ✅ Complete | — |
| 9 | [Lifecycle tests](#-phase-9-lifecycle-tests) | ✅ Complete | — |
| 10 | [Wiring tests](#-phase-10-wiring-tests) | ✅ Complete | — |
| 11 | [End-to-end tests](#-phase-11-end-to-end-tests) | ✅ Complete | — |

---

## Target behavior (reference for every phase)

In edit mode, `b` acts on the **last element placed** — not a selection, not the mouse
position. It adds a beam between that element and its nearest preceding non-grace
neighbor, or, when one beam already covers both, breaks that beam between them.
Anything else beeps.

| Placed, then `b` pressed | Result |
| --- | --- |
| quaver (first element in line) | beep — nothing before it |
| quaver | beams with previous |
| semiquaver | joins the existing beam, one span over all three |
| rest | beep — a rest is not beamable |
| crotchet | beep — a crotchet is not beamable |
| semiquaver after a crotchet | beep — the predecessor is not beamable |
| semiquaver after a quaver | beams with previous |

`b` stays armed across its own effect, so pressing it repeatedly toggles:

```
place q q, b   →  [q q]        add
place s,   b   →  [q q s]      extends into one span [0,2]
           b   →  [q q]s       breaks between 1 and 2, leaving [0,1]
           b   →  [q q s]      re-extends
```

Additional rules:

- Replacing an element (clicking an existing note to change its duration) counts as a
  placement and arms `b` exactly as inserting does.
- Grace notes are transparent at both ends. A grace note is never a beam member: `b`
  skips backward past grace notes to find the predecessor, and refuses outright when the
  *target* is a grace note. A skipped grace note sits inside the span without belonging
  to it, matching `SelectionCoordinator.java:1022` and `MusicEditOperations.java:99-101`
  (refs #592).
- The arm is dropped on any *other* song change (undo, redo, delete, any other edit), on
  mode switch, and on document load. Once dropped, `b` beeps until the next placement.
- No tuplet guard. Beaming inside a tuplet is musically normal and the select-mode toggle
  allows it.
- The existing beam-toggle action stays disabled in edit mode, so its toolbar button and
  menu item keep reporting the truth.

---

## ✅ Phase 1: Remove auto-beaming

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/ui/component/score/PreviewElementManager.java, src/test/java/songscribe/ui/component/score/PreviewElementManagerInsertVerifyTest.java  <br>
**Recommended model/effort:** Sonnet 4.6, medium effort — mechanical deletion with one comment rewrite; the compiler and existing suite gate it

Beams are currently created as a side effect of inserting a note, by a heuristic that has
no time signature to work from. It assumes every run of notes begins on a beat, ignores
dots, counts thirty-seconds as sixteenths, treats grace notes as run-breakers, and never
removes a beam it got wrong. This phase deletes it outright; nothing replaces it here.

Line numbers below are from the pre-edit file and shift as you delete. Work bottom-up, or
re-locate each site by symbol name before editing.

### Tasks

1. In `src/main/java/songscribe/ui/component/score/PreviewElementManager.java`, delete the
   private static method `applyAutomaticBeaming(Line, int)` — Javadoc and body, starting at
   `:1924`.

2. Delete its four call sites in the same file: `:1830` (append path), `:1911` (insert
   path), `:2114` (replace path), and the forward-neighbor block at `:2116-2120` (comment
   `// applyAutomaticBeaming only scans backward and misses the forward neighbor.` plus the
   guarded `applyAutomaticBeaming(line, elementIndex + 1)` call). The forward block existed
   only to compensate for the backward-only scan and goes with it. All four sites sit inside
   the single `line.withModification(...)` bracket opened in `handleClick` at `:1438`, so no
   bracket surgery is needed.

3. Remove the now-unused `import songscribe.dom.Beam;` at `:39`.

4. Rewrite the two bailout comments at `:1811-1814` and `:1842-1845`. They currently claim
   *"there is no guarantee a `SongDidChangeNotification` follows to drive a deferred setup —
   call synchronously."* That claim becomes false: a later phase of this plan makes
   `EditModeManager.previewElementDidChange` exactly such a deferred setup. Replace each with
   the real invariant: the branch is reached only after `elementWasModified` performed a
   `setElement` merging `REPEAT_LEFT` with `REPEAT_RIGHT`, so a commit always follows, and the
   element it arms is a `REPEAT_LEFT_RIGHT`, which is not beamable.

5. In `src/test/java/songscribe/ui/component/score/PreviewElementManagerInsertVerifyTest.java`,
   delete `testAutoBeamCreatedForTwoConsecutiveQuavers` (`:101-130`) and its section banner
   comment (`:93-95`), and rewrite the class Javadoc clause at `:37-40` that describes
   insertion-time beaming. Do **not** add replacement tests here — Phase 7 owns that.

6. Run `./scripts/compile.sh` (must report SUCCESS), then `./scripts/test.sh unit` (must be
   green). Six other `addBeaming`/`new Beam` sites are legitimate and must remain untouched:
   MusicXML load (`io/musicxml/RangeSpanResolver.java:150`), legacy load (`io/LineIO.java:319`),
   undo replay (`undo/MutationReplayer.java:118,158`), the manual toggle
   (`ui/MusicEditOperations.java:116`), and beam repair after span edits
   (`ui/selection/SelectionCoordinator.java:1046`).

---

## ✅ Phase 2: Span-identity helpers

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/Line.java, src/main/java/songscribe/ui/selection/LineSelectionState.java, src/main/java/songscribe/ui/MusicEditOperations.java  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — extract two helpers and adopt them at three call sites; behavior-preserving

The pattern `findXAt(i) → a; findXAt(j) → b; return a == null || a != b`, each carrying its
own `//noinspection ObjectEquality`, exists three times today and a later phase of this plan
would add more. Collapse it into two `Line` helpers. This phase changes no behavior.

### Tasks

1. In `src/main/java/songscribe/dom/Line.java`, add `public boolean sameBeamAt(int i, int j)`
   beside `findBeamAt` (`:1756`). It returns true when one and the same `Beam` covers both
   indices — i.e. `findBeamAt(i) != null && findBeamAt(i) == findBeamAt(j)`. Keep the single
   `//noinspection ObjectEquality` inside the helper. It must tolerate out-of-range indices and
   an empty line by returning false rather than throwing (check what `findBeamAt` already does
   with an out-of-range index and guard here if it does not already return null).

2. In the same file, add `public boolean sameTieAt(int i, int j)` beside `findTieAt` (`:1632`),
   with the same shape and the same out-of-range tolerance.

3. In `src/main/java/songscribe/ui/selection/LineSelectionState.java`, rewrite
   `shouldConnectBeamSelection` (`:701-707`) and `shouldConnectTieSelection` (`:713-719`) as a
   single negated call to the corresponding new helper each, and delete their now-unneeded
   `//noinspection ObjectEquality` suppressions. Note the sense: these methods return "should
   connect", which is the negation of "same span already covers both". Verify against the
   existing bodies rather than assuming; `canToggleBeaming` (`:563-564`) and `:620` depend on
   the exact polarity.

4. In `src/main/java/songscribe/ui/MusicEditOperations.java`, adopt `sameBeamAt` in the
   instance method `toggleBeaming` (`:108-120`): the `beginBeam`/`endBeam` pair plus the
   `beginBeam == null || beginBeam != endBeam` test collapses to `!line.sameBeamAt(beginIndex,
   endIndex)`, and the `//noinspection ObjectEquality` goes. The `else` branch still needs the
   `Beam` object for `removeBeaming`, so keep a `findBeamAt(beginIndex)` lookup there.

5. Run `./scripts/compile.sh` (must report SUCCESS), then `./scripts/test.sh unit` (must be
   green). Do not add tests for the new helpers here — Phase 7 owns that.

---

## ✅ Phase 3: Insertion target lifecycle

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/ui/edit/EditModeManager.java  <br>
**Recommended model/effort:** Opus 4.8, high effort — first message-bus subscription on a singleton, with an ordering invariant between a deferred arm and the commit notification

`b` needs to know which element was placed last. The genuine placement sites all sit inside a
single `line.withModification(...)` bracket opened in `PreviewElementManager.handleClick`
(`:1438`), and `SongDidChangeNotification` is posted by `Song.endModification`
(`dom/Song.java:1375-1389`) only when the outermost bracket closes — after the insertion code
has run. A handler that simply cleared the target on every song change would therefore wipe the
target it was just given. The fix is two slots and one promote-or-clear assignment.

```
 EDIT-MODE CLICK
      │
      ▼
 handleClick ─── line.withModification(…)   ◄── outer bracket (grace mode nests inside)
      │             │
      │             ├─ addPreviewElement       ─┐
      │             ├─ insertElement           ─┤
      │             ├─ modifyExistingElement    ─┼─► EditModeManager
      │             ├─ bailout (REPEAT merge)  ─┤    .previewElementDidChange(line, i)
      │             └─ bailout (REPEAT merge)  ─┘              │
      │                                                 pendingInsertion = (line, i)
      └─ bracket closes ─► Song.endModification ─► SongDidChangeNotification
                                                             │
                                   ┌─────────────────────────┘
                                   ▼
                     EditModeManager.songDidChange:
                        lastInsertion    = pendingInsertion    ── promote
                        pendingInsertion = null                ── and invalidate

 ANY OTHER SONG CHANGE ─► songDidChange sees pendingInsertion == null ─► lastInsertion = null
 MODE SWITCH / DOCUMENT LOAD ─────────────────────────────────────────► both slots = null
```

### Tasks

1. Read `.agents/guides/messages.md` before writing any `@Handler`. `EditModeManager`
   subscribes to nothing today, so this is its first subscription and the guide's conventions
   apply in full.

2. In `src/main/java/songscribe/ui/edit/EditModeManager.java`, add a public nested record
   `public record Insertion(Line line, int elementIndex) {}`. A bare index is meaningless once
   the user moves between lines, and one record makes a single null check cover both parts.

3. Add two `@Nullable` instance fields, `pendingInsertion` and `lastInsertion` (project rules
   ban `Optional`); a static `@Nullable Insertion getLastInsertion()`; a static
   `armInsertion(Line, int)` that sets the **pending** slot; and a package-private reset method
   for tests that nulls both slots. Follow the existing static-accessor style at `:156-171`.

4. Arm inside `previewElementDidChange(Line line, int elementIndex)` (`:334`), immediately
   after its existing `previewElement == null` early return:
   `inst.pendingInsertion = new Insertion(line, elementIndex);`
   This is the **only** arming site inside this class's own flow. Do not add arming calls beside
   the five callers in `PreviewElementManager` — that method is already called at every
   placement site with exactly these arguments, and at the replace site it is called after the
   `elementIndex--` correction, so it carries the corrected index for free.

5. Add three `@Handler` methods. `songDidChange` performs exactly the promote-or-clear pair:

   ```java
   lastInsertion = pendingInsertion;   // null on any change that wasn't a placement
   pendingInsertion = null;
   ```

   `modeDidChange` and `documentDidLoad` null both slots. Plain `@Handler` priority is fine: the
   only handler that can post a nested `SongDidChangeNotification` is
   `PreviewElementManager.songDidChange` (via the modal tempo dialog), and that fires only on the
   first note of a song, where `b` beeps regardless of ordering. Look up the exact notification
   class names for mode change and document load in the codebase rather than guessing.

6. Two constraints on the subscription, both mandatory:
   - `MessageCenter.subscribe(this)` goes at the **end** of the private constructor (`:98-107`),
     after the sub-managers are assigned.
   - The handlers must operate on `this`, never call `instance()` — a message arriving before
     `init()` completes would otherwise hit `RuntimeError.exit`.

7. Run `./scripts/compile.sh` — must report SUCCESS. Do not add tests here; Phase 9 owns them.

---

## ✅ Phase 4: The toggle operation

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Files:** src/main/java/songscribe/ui/MusicEditOperations.java  <br>
**Recommended model/effort:** Opus 4.8, high effort — branch-heavy span surgery with undo-tracking constraints and endpoint edge cases

Add the beam operation itself. Phase 2 has already added `Line.sameTieAt(int, int)` and
`Line.sameBeamAt(int, int)` to `src/main/java/songscribe/dom/Line.java` and adopted the latter
inside `toggleBeaming`; both helpers are available here.

### Tasks

1. In `src/main/java/songscribe/ui/MusicEditOperations.java`, directly after the instance method
   `toggleBeaming` (which ends at `:121`), add:

   ```java
   public static boolean toggleBeamWithPredecessor(Line line, int elementIndex)
   ```

   Static and taking `(Line, int)` rather than reading `EditModeManager` internally: the
   operation touches neither `coordinator` nor `song`, and this keeps it unit-testable against a
   bare `Song`/`Line` with no singleton graph. The class already has several private statics, so
   the shape is not new. The caller does the state lookup. It returns true only if it modified
   the line.

2. Implement the control flow exactly as follows, using the unlabeled `line.withModification(...)`
   overload (no explicit label — `UndoController.opNameKey` at `undo/UndoController.java:479-480`
   already maps both `BeamingAddition` and `BeamingRemoval` to `Strings.ACTION_EDIT_OP_BEAMING`,
   so undo reads "Undo Beaming" for add and break alike, and no new string key is needed):

   ```
    toggleBeamWithPredecessor(line, t)
      │
      ├─ t out of range for line.elementCount() ──────────────────► false
      ├─ element at t isGraceNote() ──────────────────────────────► false
      ├─ walk back past isGraceNote() elements → p
      │     └─ none remains ──────────────────────────────────────► false
      │
      ├─ beam = line.findBeamAt(p)
      │    beam != null && beam == line.findBeamAt(t)
      │       │
      │       └─ BREAK:  a = beam.getAnchorElementIndex()
      │                  e = beam.getEndElementIndex()
      │                  withModification:
      │                     removeBeaming(beam)
      │                     if (a < p) addBeaming(new Beam(elem(a), elem(p)))
      │                     if (t < e) addBeaming(new Beam(elem(t), elem(e)))
      │                                                            ──► true
      │
      └─ ADD:  element at t !isBeamable() ────────────────────────► false
               element at p !isBeamable() ────────────────────────► false
               line.sameTieAt(p, t) ──────────────────────────────► false
               withModification:
                  addBeaming(new Beam(elem(p), elem(t)))           ──► true
   ```

3. Break semantics: when one beam `[a, e]` covers both `p` and `t`, `b` breaks it *between them*
   rather than removing it wholesale, and a single-element remainder is dropped because a beam
   needs two members:

   ```
      a         p   t         e
      ├────────────────────────┤        before:  one beam [a,e]
      ├─────────┤   ├──────────┤        after:   [a,p] and [t,e]

      a=p       t             e
      ├────────────────────────┤   →    ├──────────┤        only [t,e] survives
      a         p           t=e
      ├────────────────────────┤   →    ├─────────┤         only [a,p] survives
      a=p     t=e
      ├─────────┤                  →    (no beam)
   ```

   It must be `removeBeaming(beam)` followed by up to two `addBeaming` calls, so every step is a
   tracked mutation and undo restores the original span. Endpoint mutation via
   `setAnchorElement`/`setEndElement` is **not** an option — it is untracked and would break undo.
   The two new spans do not re-merge: `addBeaming` (`dom/Line.java:1778`) calls
   `mergeOverlappingSpans` with `absorbAdjacent = false` (`:1786`), so a span merely touching
   another's endpoint is left alone. Grace notes between `p` and `t` fall in the gap and belong to
   neither span.

4. The break branch needs the `Beam` object itself, not just a boolean, so it uses the explicit
   `findBeamAt` pair rather than `Line.sameBeamAt`. That is deliberate — do not "simplify" it to
   `sameBeamAt` and then re-look-up.

5. The tie check applies to the **add** branch only; breaking a beam is never blocked by a tie.
   It is the same conflict rule as `LineSelectionState.canToggleBeaming` (`:563-564`): beaming may
   not connect what a tie already connects. There is deliberately **no** tuplet guard.

6. Give the method a Javadoc carrying the break diagram from task 3, plus a sentence recording
   that the add branch can fuse two adjacent beam groups into one. That fusion is accepted, not a
   bug: `addBeaming` widens at both ends independently (`dom/Line.java:1826-1833`), so with beams
   `[0,1]` and `[2,3]`, arming element 2 and pressing `b` yields a single `[0,3]`. The select-mode
   toggle does exactly the same thing from the same call, and making `b` disagree with it would
   undercut the reason the tie check exists.

7. Run `./scripts/compile.sh` — must report SUCCESS. Do not add tests here; Phase 8 owns them.

---

## ✅ Phase 5: Wire the key

**Status:** Complete  <br>
**BlockedBy:** 3, 4  <br>
**Files:** src/main/java/songscribe/message/command/ToggleBeamWithPreviousCommand.java, src/main/java/songscribe/ui/component/ScoreInputHandler.java, src/main/java/songscribe/ui/component/ScoreViewController.java, src/main/java/songscribe/ui/component/score/LineComponent.java  <br>
**Recommended model/effort:** Opus 4.8, medium effort — Swing input-map precedence plus a focus-ordering invariant that has to precede existing mode guards

Two APIs added by earlier phases are available and must be used as named:
`EditModeManager.getLastInsertion()` returning a `@Nullable EditModeManager.Insertion(Line line,
int elementIndex)`, `EditModeManager.armInsertion(Line, int)`, and
`MusicEditOperations.toggleBeamWithPredecessor(Line, int)` returning boolean.

`b` is already the accelerator for `TOGGLE_BEAM_ACTION` (`ui/action/ToggleNotationAction.java:54`),
registered on `MainFrame`'s root pane `WHEN_IN_FOCUSED_WINDOW` input map by
`UIUtils.registerActionKeystroke` (`util/UIUtils.java:192-201`). Swing skips a disabled action
bound there, and `UIAction`'s enabled state is one flag shared by button, menu item, and key — so
that path cannot both stay disabled *and* still beep. A `WHEN_FOCUSED` binding on `ScoreView`
resolves first regardless of the action's enabled state, which is why the key goes there.

### Tasks

1. Create `src/main/java/songscribe/message/command/ToggleBeamWithPreviousCommand.java` —
   payloadless, modeled exactly on the existing `ToggleBeamCommand.java` in the same package.

2. In `src/main/java/songscribe/ui/component/ScoreInputHandler.java`, register
   `KeyStroke.getKeyStroke(KeyEvent.VK_B, 0)` inside `installKeyBindings` (`:190-204`), putting it
   into the same returned `bindings` map, `inputMap` (`WHEN_FOCUSED`) and `actionMap` as the
   existing entries — being in that map is what lets the caller temporarily disable it during text
   editing. Give it its own `AbstractAction` rather than the arrow-key `KeyAction` class (`:219`),
   which is shaped for directional input and takes a keycode plus a shift flag. The action body
   guards on `callback.getMode() == Mode.EDIT` and on neither
   `EditModeManager.getGraceModeManager().isInProgress()` nor
   `EditModeManager.getPasteModeManager().isInProgress()` — matching the precedence the Escape
   branch establishes at `:149-154` — then posts `new ToggleBeamWithPreviousCommand()` via
   `MessageCenter.post`. Deliberately omit all three of: a modifier comparison, an `e.consume()`,
   and an `isEditingTextIn` guard. `KeyStroke(VK_B, 0)` matches only plain `b` — Cmd-B, Shift-B and
   Alt-B are different keystrokes and never reach it — and with a text component focused,
   `ScoreView` is not the focused component so the binding does not fire at all.

3. In `src/main/java/songscribe/ui/component/ScoreViewController.java`, add a `@Handler` beside
   `handleToggleBeam` (`:209`) for the new command. In order: while
   `PlaybackController.isPlaying()`, call `UIUtils.beep()` (`util/UIUtils.java:127`) and return —
   mirroring the `DISABLE_WHEN_PLAYING` flag the toggle action carries. Otherwise read
   `EditModeManager.getLastInsertion()`; beep and return on null. On a live target, call
   `EditModeManager.armInsertion(insertion.line(), insertion.elementIndex())` **before**
   `MusicEditOperations.toggleBeamWithPredecessor(insertion.line(), insertion.elementIndex())`,
   then beep if the operation returned false. The re-arm ordering is what makes the toggle
   reachable: the operation opens its own `withModification` bracket, whose commit notification
   would otherwise run `EditModeManager`'s promote-or-clear with an empty pending slot and disarm
   `b` the instant it succeeded. Arming first means that notification promotes the same target
   straight back.

4. In `src/main/java/songscribe/ui/component/score/LineComponent.java`, call
   `requestFocusInWindow()` on the `ScoreView` ancestor at the **top** of `mousePressed` (`:838`),
   immediately after the `BUTTON1` check and **before** the grace-mode (`:843`), paste-mode
   (`:852`) and playback (`:857`) early returns. `LineComponent` consumes its own clicks and Swing
   mouse events do not bubble, so `ScoreInputHandler.mouseClicked`'s existing
   `callback.requestFocusInWindow()` (`:78`) never runs for a click on a line — without this, a
   user who was typing in the lyric editor and clicks back onto a line to place a note would have
   `b` typed into the lyric instead. The invariant "a click on the score gives the score focus"
   has no exceptions, so it must precede the mode guards; otherwise `b` is dead after a grace-mode
   press. The lyric editor still wins focus on the double-click path, which opens later from
   `mouseClicked` → `LyricEditor.deselectAndOpenOn` (`:834`).

5. Change nothing in `ToggleNotationAction`, `Actions`, `NotationMenu`, or the toolbar. Leaving
   `Flag.REQUIRES_MULTIPLE_SELECTION` in place on `TOGGLE_BEAM_ACTION` is what satisfies the
   "stays disabled in edit mode" requirement. The command indirection in tasks 1–3 is required
   rather than a direct callback because `MessageCenter.post` returns `void`, so the decision to
   beep has to live on the handler side; it also keeps `ScoreInputHandler` a thin dispatcher, as it
   already is for `DeselectCommand` (`:75`).

6. Run `./scripts/compile.sh` — must report SUCCESS. Do not add tests here; Phases 10 and 11 own
   them.

---

## ✅ Phase 6: Manual verification

**Status:** Complete  <br>
**BlockedBy:** 1, 5  <br>
**Files:** —  <br>
**Recommended model/effort:** Opus 4.8, low effort — drive the app and report; the user makes the pass/fail call

No code changes. The feature must be confirmed working in the running app before any test is
written against it. Every test phase in this plan is blocked on this one.

### Tasks

1. Run `./scripts/compile.sh` (must report SUCCESS — it also runs the string-key audit) and
   `./scripts/test.sh unit` (must be green) before asking for anything.

2. Ask the user for permission, then launch the app with `./scripts/run.sh`. Never run it without
   explicit permission.

3. Ask the user to walk this checklist in edit mode and report each result:
   - the seven-row behavior table at the top of this plan — each beam and each beep;
   - pressing `b` a second time breaks the beam it just made, and a third re-adds it;
   - the toolbar beam button stays greyed out in edit mode throughout;
   - `b` still toggles beams normally in **select** mode with a multi-note selection;
   - undo after a `b` removes just the beam;
   - type in the lyric editor, click a line, place a note — `b` beams rather than typing a
     literal `b` into the lyric.

4. Report the outcome. If anything fails, fix it and re-verify with the user before marking this
   phase complete — do not let a test phase start against unverified behavior.

---

## ✅ Phase 7: Model-layer tests

**Status:** Complete  <br>
**BlockedBy:** 6  <br>
**Files:** src/test/java/songscribe/dom/LineBeamTest.java, src/test/java/songscribe/dom/LineTieTest.java, src/test/java/songscribe/ui/component/score/PreviewElementManagerInsertVerifyTest.java  <br>
**Recommended model/effort:** Sonnet 4.6, medium effort — straightforward assertions against existing test scaffolding

Read `.agents/guides/testing-unit.md` and `.agents/guides/testing-common.md` first. Two helpers
added earlier in this plan are under test here: `Line.sameBeamAt(int, int)` and
`Line.sameTieAt(int, int)`, both in `src/main/java/songscribe/dom/Line.java`, each returning true
when one and the same span covers both indices.

### Tasks

1. In `src/test/java/songscribe/dom/LineBeamTest.java`, add tests for `sameBeamAt`: both indices
   inside one beam → true; the two indices in *different* beams → false; one index beamed and the
   other not → false; neither beamed → false.

2. In the same file, cover the degenerate inputs for `sameBeamAt`: negative index, index past
   `elementCount()`, and both indices on an empty line. All must return false rather than throw.

3. In `src/test/java/songscribe/dom/LineTieTest.java`, add the mirror-image set for `sameTieAt`:
   both indices inside one tie → true; different ties → false; one tied and one not → false;
   plus the same out-of-range and empty-line cases returning false.

4. In `src/test/java/songscribe/ui/component/score/PreviewElementManagerInsertVerifyTest.java`,
   add `testNoBeamCreatedWhenTwoQuaversAreInserted`, asserting that after inserting two
   consecutive quavers `line.findRangeElements(Beam.class)` is empty. Insertion-time beaming was
   removed earlier in this plan; this pins that it stays removed.

5. In the same file, add a second case covering the **replace** path — change a crotchet sitting
   next to a quaver into a quaver — and assert no beam appears. Replace went through a separate
   auto-beaming call site with its own forward-neighbor scan, so it needs its own case.

6. Run `./scripts/test.sh unit` — must be green.

---

## ✅ Phase 8: Operation tests

**Status:** Complete  <br>
**BlockedBy:** 6  <br>
**Files:** src/test/java/songscribe/ui/MusicEditOperationsBeamWithPredecessorTest.java  <br>
**Recommended model/effort:** Opus 4.8, high effort — the behavioral core, including undo round-trips and span-endpoint edge cases

Read `.agents/guides/testing-unit.md`, `.agents/guides/testing-common.md` and
`.agents/guides/mutations.md` first. New file
`src/test/java/songscribe/ui/MusicEditOperationsBeamWithPredecessorTest.java`, testing
`MusicEditOperations.toggleBeamWithPredecessor(Line, int)` — a public static that returns true only
if it modified the line. Test against a real `Song`/`Line` with no mocks, which the static
signature makes possible. Model the mutation assertions on
`src/test/java/songscribe/dom/MusicEditOperationsMutationTest.java`.

### Tasks

1. One test per row of the behavior table at the top of this plan: first element in the line →
   false; two beamable neighbors → true and beamed; a rest as target → false; a crotchet as target
   → false; a beamable target after a crotchet → false.

2. The three-note join: with `[0,1]` already beamed, arming index 2 (a semiquaver) produces **one**
   span `[0,2]`, not two spans. Add an undo round-trip through `MutationReplayer` for this case —
   it is what proves the subsumed-span removal landed in the same undo batch.

3. Break cases: `[0,2]` armed at 2 → `[0,1]`; the two drop-a-single-member cases (breaking at the
   second element of a two-element beam, and breaking such that only the tail survives); a break in
   the middle of a longer beam yielding two spans; and an undo round-trip restoring the original
   single span.

4. Grace notes: `[quaver][grace][quaver]` armed at index 2 yields one span `[0,2]` with the grace
   note inside it but not a member; a grace note as the *target* returns false; a lone grace note as
   the only predecessor returns false.

5. Conflict and guard cases: a tie already connecting predecessor and target makes the **add**
   branch return false, while breaking an existing beam across a tie still succeeds; a tuplet beams
   normally (there is deliberately no tuplet guard); negative and past-the-end indices return false.

6. The accepted fusion case: with beams `[0,1]` and `[2,3]`, arming index 2 yields a single
   `[0,3]`. This is intended behavior, matching what the select-mode toggle does from the same
   `addBeaming` call — assert it rather than treating it as a bug.

7. Run `./scripts/test.sh unit` — must be green.

---

## ✅ Phase 9: Lifecycle tests

**Status:** Complete  <br>
**BlockedBy:** 6  <br>
**Files:** src/test/java/songscribe/ui/edit/EditModeManagerTest.java  <br>
**Recommended model/effort:** Opus 4.8, high effort — bracket/notification timing is the thing under test, so the tests must drive the real bus

Read `.agents/guides/testing-unit.md`, `.agents/guides/testing-common.md` and
`.agents/guides/mutations.md` first. Drive these with a real `Song` and the real message bus; do
**not** construct `SongDidChangeNotification` by hand — `mutations.md` forbids it.

Under test: `EditModeManager` holds `@Nullable pendingInsertion` and `@Nullable lastInsertion`
slots of type `EditModeManager.Insertion(Line line, int elementIndex)`. `previewElementDidChange`
sets the pending slot; the `songDidChange` handler promotes pending into last and nulls pending, so
any song change that was not a placement clears the target; mode change and document load null
both. A package-private reset method exists for tests.

Add a new `@Nested class LastInsertion` in
`src/test/java/songscribe/ui/edit/EditModeManagerTest.java`, alongside the existing
`PreviewElementDidChange` nested class (`:405`), and hook the reset into the existing `@AfterEach`
(`:81`).

### Tasks

1. `getLastInsertion()` is null before any insertion.

2. The arm is invisible *inside* the modification bracket and becomes visible only after the
   outermost bracket commits.

3. The arm survives its own insertion notification, for all three genuine placement paths: append,
   insert, and replace. The replace case must assert the **post-decrement** index — the replace site
   calls `previewElementDidChange` after its `elementIndex--` correction.

4. Any *other* song change clears the target; a mode change clears it; a document load clears it.

5. Deleting the line that holds the target clears it — this is the case where a surviving arm would
   silently mutate a detached `Line`.

6. A `REPEAT_LEFT` + `REPEAT_RIGHT` merge reaches the bailout path, arms the bailout index, and the
   armed element (a merged `REPEAT_LEFT_RIGHT`) is not beamable.

7. Run `./scripts/test.sh unit` — must be green.

---

## ✅ Phase 10: Wiring tests

**Status:** Complete  <br>
**BlockedBy:** 6  <br>
**Files:** src/test/java/songscribe/ui/component/ScoreInputHandlerTest.java, src/test/java/songscribe/ui/component/ScoreViewControllerCommandHandlerTest.java  <br>
**Recommended model/effort:** Sonnet 4.6, medium effort — follows two existing test idioms closely

Read `.agents/guides/testing-unit.md` and `.agents/guides/testing-common.md` first. Under test: a
`KeyStroke(VK_B, 0)` `WHEN_FOCUSED` binding installed by `ScoreInputHandler.installKeyBindings`,
whose action posts a payloadless `ToggleBeamWithPreviousCommand`, and the `ScoreViewController`
`@Handler` for that command.

### Tasks

1. New `@Nested class BeamKeyBinding` in
   `src/test/java/songscribe/ui/component/ScoreInputHandlerTest.java`, following the existing
   `InstallKeyBindings` idiom (`:1039`): assert `KeyStroke.getKeyStroke(KeyEvent.VK_B, 0)` is
   present in the map returned by `installKeyBindings`, and that the Cmd-B, Shift-B and Alt-B
   keystrokes are **not**.

2. In the same nested class, fire the bound action and assert dispatch: in edit mode it posts
   `ToggleBeamWithPreviousCommand`; in select mode it does not; while grace mode is in progress it
   does not; while paste mode is in progress it does not.

3. In `src/test/java/songscribe/ui/component/ScoreViewControllerCommandHandlerTest.java` (which
   already covers `handleToggleBeam`), add handler tests: it beeps and does nothing while playback
   is running; it beeps when `EditModeManager.getLastInsertion()` is null.

4. In the same file, assert the re-arm ordering: with a live target, the handler calls
   `EditModeManager.armInsertion` **before** `MusicEditOperations.toggleBeamWithPredecessor`, so
   the target is still armed after the operation commits and a second press is still live. This
   ordering is the whole reason the toggle is reachable, so assert it directly rather than
   inferring it.

5. Run `./scripts/test.sh unit` — must be green.

---

## ✅ Phase 11: End-to-end tests

**Status:** Complete (e2e run still pending user approval)  <br>
**BlockedBy:** 6  <br>
**Files:** src/test/java/songscribe/e2e/ElementInsertionTest.java  <br>
**Recommended model/effort:** Opus 4.8, high effort — real key dispatch and focus behavior; e2e is slow to debug and needs care to get right first time

Read `.agents/guides/testing-e2e.md` first. Two e2e tests are justified here because the risk *is*
the integration: real key dispatch has to reach a `WHEN_FOCUSED` binding on `ScoreView` even though
a **disabled** action owns `VK_B` in `MainFrame`'s root pane. Add both to
`src/test/java/songscribe/e2e/ElementInsertionTest.java`, using the existing helpers
`pressKey(int, int)` (`E2ETest.java:651`), `isBeamed(int, int)` (`:708` — currently uncalled) and
`loadFixture(String)` (`:741`).

### Tasks

1. First test: start from a `loadFixture(...)` non-empty song so the first-note tempo dialog never
   appears. Click two quavers, press `b`, assert both are beamed via `isBeamed`. Press `b` again and
   assert the beam is gone.

2. In the same test, at that moment assert `Actions.TOGGLE_BEAM_ACTION.isEnabled()` is false —
   direct proof of the "the beam action stays disabled in edit mode" requirement, which is exactly
   what makes the root-pane accelerator path unusable and the `WHEN_FOCUSED` binding necessary.

3. Second test, for the focus fix: open the lyric editor and type into it, click a line to place a
   note, press `b`, then assert both that the beam exists **and** that the lyric text is unchanged.
   This is the only test that proves the `requestFocusInWindow()` call added at the top of
   `LineComponent.mousePressed`, and it covers a failure that would otherwise be silent — the `b`
   landing in the lyric instead.

4. Run `./scripts/test.sh unit` first — must be green.

5. Ask the user for approval, then run `./scripts/test.sh e2e ElementInsertionTest`. **Never run an
   e2e test without explicit approval.** On failure, read the output for the error location and fix
   it; do not rerun with `--debug`/`--verbose` flags.

---

## Verification

1. `./scripts/compile.sh` — must report SUCCESS. It also runs the string-key audit, which catches
   any key left dead.
2. `./scripts/test.sh unit` — full unit suite, to catch anything else that depended on
   insertion-time beaming.
3. `./scripts/test.sh e2e ElementInsertionTest` — **requires user approval before running.**
4. Re-run the Phase 6 manual checklist in the running app (`./scripts/run.sh`, **requires user
   permission**) once all phases are complete.
