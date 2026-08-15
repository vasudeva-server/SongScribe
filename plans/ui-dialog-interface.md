# ui/dialog — The Dialog Interface

Executes the `ui/dialog` row of [`contract-driven-rollout.md`](./contract-driven-rollout.md).
It is the **last row the D10 freeze covers**; nothing outside the rollout resumes until
it is done.

## Two terms

- **The dialog framework** — the whole of `songscribe.ui.dialog`: `BaseDialog`'s
  lifecycle and geometry, `StandardDialog`'s button row, `Tab`, `DialogCategory` and
  the blocking counter. Named the way `docs/messages.md` and `docs/mutations.md` name
  theirs.
- **The dialog interface** — how data crosses between a dialog and the rest of the
  application: a record in, a record out, and a `DialogOps` bundle of function
  references supplied by the controller that opened it. This is what the track exists
  to establish.

## The target

**A dialog is a widget shell over `I → O`. It has no collaborator it can query.**

- **a record in** — `I`, what to show;
- **a record out** — `O`, what the controls now say, gathered on OK;
- **a `DialogOps<I, O>`** — four function references: read, validate, commit, remove.

The controller on the other end of those references holds the line, the element, the
score and the view, and does whatever the operation needs. **The dialog never sees it.**
Handing over an object — even one narrowed to an interface — leaves the dialog able to
call whatever that object exposes; handing over four function references does not.

### The mechanical test

**A dialog's constructor takes `MainFrame`, a `DialogOps`, and presentation constants —
nothing else. No dialog field names `Song`, `Line`, `StaffElement`, `ScoreView` or a
controller.**

A reviewer applies this without judgment. Under it a dialog's own steps — populate,
gather, call the ops — are wiring, classified **none**, and carry no tests of their own.

## The test tree is empty

`src/test` holds 21 files and **not one `@Test` method** — base classes and helpers
only (`UnitTest`, `MainFrameMockTest`, `RequiresDisplay`, `CompactTestReporter`,
`StaffElementFactory`, `XmlFixtures`, `MockEnvHelper`, `MessageCenterTestHelper`,
`RuntimeErrorTestHelper`, `UncaughtExceptionTestHelper`, `MusicXmlRoundTripSupport`,
`MusicXmlCorpusGenerator`, `E2ETest`, `package-info`s).

**Every test in this track is written from scratch, and the test-list gate applies to
all of it.** Nothing is triaged, re-pointed or repaired; there is nothing to repair.

## The dialogs that exist

```
BaseDialog ......................... window, geometry, blocking counter, tabs
├── PreferencesDialog .............. 899 LOC, non-modal, no button row
├── ProgressBarDialog .............. no button row
└── StandardDialog ................. OK / Cancel
    ├── ReportBugDialog ............ deleted by Phase 2
    ├── WhatsNewDialog ............. deleted by Phase 2
    ├── KeyChangeDialog ............ 449 LOC, Phase 5
    └── CommitDialog<I> ............ merged into StandardDialog by Phase 1
        ├── SongSettingsDialog ...... 230 LOC + 4 tabs + 2 rows + TempoSection
        ├── FontDialog .............. CommitDialog<Font>
        ├── DoNotShowMessage ........ CommitDialog<Boolean>
        └── AttachmentDialog<C> ..... 136 LOC, + Remove button
            ├── BeatChangeDialog
            ├── TempoChangeDialog
            └── AnnotationDialog

outside the hierarchy, deliberately: AboutDialog (JDialog), MigrationWindow (JDialog)
not dialogs: PlatformFileDialog, FontSettingRow, the fontchooser/ subpackage
```

**Do not touch these; they are correct as they stand.** `ValidationResult`,
`ValidationFailure`, `LocalizedMessage` and its nested-argument rule;
`SongSettingsInput`, `SongSettingsOutput`, `MusicSettings`, `LyricsContext`,
`LineWidthEntry`, `MusicChoices`, `WordsDate`; and `BaseDialog`'s lack of any route to
the score.

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [The Controller Framework](#-phase-1-the-controller-framework) | ⏳ Pending | — |
| 2 | [Delete the Legacy Dialogs](#-phase-2-delete-the-legacy-dialogs) | ⏳ Pending | — |
| 3 | [The Attachment Family](#-phase-3-the-attachment-family) | ⏸️ Blocked by 1 | — |
| 4 | [SongSettingsDialog and Its Tabs](#-phase-4-songsettingsdialog-and-its-tabs) | ⏸️ Blocked by 1 | — |
| 5 | [KeyChangeDialog](#-phase-5-keychangedialog) | ⏸️ Blocked by 1 | — |
| 6 | [PreferencesDialog](#-phase-6-preferencesdialog) | ⏸️ Blocked by 1 | — |
| 7 | [The Remaining Dialogs](#-phase-7-the-remaining-dialogs) | ⏸️ Blocked by 1 | — |
| 8 | [Controller Contracts and Their Tests](#-phase-8-controller-contracts-and-their-tests) | ⏸️ Blocked by 3, 4, 5, 6, 7 | — |
| 9 | [Documentation Consistency Pass](#-phase-9-documentation-consistency-pass) | ⏸️ Blocked by 8 | — |
| 10 | [Manual UI Verification](#-phase-10-manual-ui-verification) | ⏸️ Blocked by 9 | — |

**There is no e2e phase.** A dialog's populate-gather-ops path either works or it does
not, which one look at the running app settles; an e2e test over it is one-time at best,
and *The testing floor* in `~/.claude/guides/design.md` says a one-time test is deleted
in the change that produced it. **What needs testing is the controller**, which Phase 8
covers; the wiring is confirmed by Phase 10's manual pass. D2 in
[`contract-driven-rollout.md`](./contract-driven-rollout.md) states the same.

**The tree does not compile between Phase 1 and Phase 7.** That is deliberate: the
framework change breaks every dialog at once and the compiler enumerates the work. Do
not add transitional scaffolding to keep it green — see *Complete the subsystem; do not
stage it* in `~/.claude/rules/development.md`. Snapshot each phase with
`git add -A && git stash store -m "Finished phase N" "$(git stash create)"`.

---

## ⏳ Phase 1: The Controller Framework

**Status:** Pending  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/ui/dialog/DialogOps.java, src/main/java/songscribe/ui/dialog/DialogController.java, src/main/java/songscribe/ui/dialog/StandardDialog.java, src/main/java/songscribe/ui/dialog/CommitDialog.java, src/main/java/songscribe/ui/dialog/BaseDialog.java, src/main/java/songscribe/ui/dialog/DialogBackEnd.java, src/main/java/songscribe/ui/dialog/AttachmentBackEnd.java, src/main/java/songscribe/ui/dialog/SongSettingsBackEnd.java, .claude/guides/dialogs.md  <br>
**Recommended model/effort:** Opus, high — this decides the shape every other phase applies; a wrong shape here is paid for by every class that follows.

### The two new types

```java
/**
 * Everything a dialog may ask of the world outside itself.
 */
record DialogOps<I, O>(
    Supplier<I>                    read,
    Function<O, ValidationResult>  validate,
    Consumer<O>                    commit,
    @Nullable Runnable             remove     // null = this dialog offers no Remove
) {}
```

```java
abstract class DialogController<I, O> {

    protected DialogController(MainFrame mainFrame)

    // The access dialogs gave up. Legitimate here.
    protected final MainFrame        getMainFrame()
    protected final ScoreView        requireScoreView()
    protected final Song             getSong()
    protected final void             withModification(String label, Runnable mutator)

    protected abstract I                read()
    protected          ValidationResult validate(O values)   // default: accepts everything
    protected abstract void             commit(O values)
    protected          @Nullable Runnable removal()          // default: null

    /** Assembled once here so no subclass wires it by hand. */
    protected final DialogOps<I, O> ops() {
        return new DialogOps<>(this::read, this::validate, this::commit, removal());
    }
}
```

### `StandardDialog<I, O>` absorbs `CommitDialog`

```
show    →  populate(ops.read().get())
OK      →  gather() → ops.validate() → ops.commit()   [commit only if valid]
Remove  →  ops.remove().run()                          [button rendered iff non-null]
Cancel  →  nothing
```

**A subclass writes only `populate(I)` and `gather() → O`.** There are no `validate` /
`commit` hooks to delegate; the dialog calls the ops directly.

**There is no `Void` input.** A dialog that appears to need none is one whose
constructor is smuggling its input in — `DoNotShowMessage` takes its message text as a
constructor argument today, and that text is its input. If `I` ever wants to be `Void`,
look at the constructor.

### Tasks

1. Read `.claude/guides/contracts.md`, `.claude/rules/java.md` (Javadoc contract syntax
   and the signature rules) and `.claude/guides/dialogs.md`.
2. **Write the contracts for `DialogOps` and `DialogController` before implementing
   either.** State on `DialogOps` why it is a record of function references rather than
   an interface — that an interface is an object the dialog can call other things on,
   and this is not. State on `DialogController` that `ops()` is final so no subclass can
   hand over a partial or rewired bundle.
3. State the ordering promise on `StandardDialog`: **the values validated are the values
   committed**, `gather()` runs exactly once per OK, and nothing is committed when
   validation refuses.
4. Merge `CommitDialog` into `StandardDialog<I, O>` and delete `CommitDialog.java`.
   `showFailure(ValidationFailure)` stays and stays the single presentation path, so a
   control checking a rule before OK — a field's `InputVerifier` — reports it the same
   way. Only the first failure is shown; `ValidationResult` promises presentation order.
5. Move the Remove button into `StandardDialog`, rendered iff `ops.remove() != null`.
   Today it is `AttachmentDialog.modifyButtonPanel`'s. **Remove is a framework
   affordance, not an attachment one** — the next dialog that needs it gets it free.
6. Delete `DialogBackEnd`, `AttachmentBackEnd`, `SongSettingsBackEnd` and
   `BaseDialog.DialogOp`. `DialogOp` (singular, `ADD`/`EDIT`/`REMOVE`) is inherited by
   every dialog and used by exactly one — `KeyChangeDialog`, for its undo-step label —
   which Phase 5 moves onto its controller. It does not coexist with `DialogOps`.
7. Do **not** change `BaseDialog`'s blocking-dialog counter, tab selection or geometry
   logic. Those are real logic but are not part of the dialog interface; leave them for
   the contract pass that follows this track.
8. **Rewrite `.claude/guides/dialogs.md` in this phase.** Everything it says about back
   ends becomes false the moment this lands, and six phases run before the
   documentation pass. Specifically:
   - the governing statement becomes the mechanical test in *The target* above;
   - **collapse the two input shapes to one.** The guide documents a dialog constructed
     per gesture taking its input at construction, versus a cached dialog asking its
     back end on each opening. With `read` in `DialogOps` every dialog asks on each
     opening;
   - rewrite the `BaseDialog` API surface, `StandardDialog` and `Tab` sections, and drop
     the `CommitDialog` section;
   - **the category-precedent list names two classes that do not exist** — `HelpDialog`
     and `HTMLDialog`. Delete both, and `WhatsNewDialog`/`ReportBugDialog` with them.
9. Do not compile. Phases 2–7 fix what this breaks and the compiler enumerates them.

---

## ⏳ Phase 2: Delete the Legacy Dialogs

**Status:** Pending  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/ui/dialog/WhatsNewDialog.java, src/main/java/songscribe/ui/dialog/ReportBugDialog.java, src/main/java/songscribe/ui/component/MainFrame.java  <br>
**Recommended model/effort:** Sonnet, low — two deletions and one caller.

Independent of Phase 1 and can run before it.

### Tasks

1. **Delete `ReportBugDialog`.** It has no production caller.
2. **Delete `WhatsNewDialog` and `MainFrame.maybeShowWhatsNew()`
   (`MainFrame.java:422–435`).** That method is the dialog's only caller — it compares
   `PrefsKey.LAST_SEEN_WHATS_NEW_VERSION` against `Version.PUBLIC_VERSION` and shows the
   release notes once per version — so it goes with the dialog rather than being left
   calling nothing.
3. **Keep `PrefsKey.LAST_SEEN_WHATS_NEW_VERSION` and the `help/release-notes-*.html`
   resource**, for a replacement to use. If the dead-key audit objects to a pref nothing
   reads, report it rather than deleting the key.

---

## ⏸️ Phase 3: The Attachment Family

**Status:** Blocked by 1  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/ui/dialog/AttachmentDialog.java, src/main/java/songscribe/ui/dialog/BeatChangeDialog.java, src/main/java/songscribe/ui/dialog/TempoChangeDialog.java, src/main/java/songscribe/ui/dialog/AnnotationDialog.java, src/main/java/songscribe/ui/dialog/AttachmentDialogController.java, src/main/java/songscribe/ui/dialog/AttachmentTarget.java, src/main/java/songscribe/ui/dialog/backend/  <br>
**Recommended model/effort:** Sonnet, medium — a mechanical re-homing of code whose shape is already right.

**Move this code, do not redesign it.** The back ends here are already the correct
logic in the wrong container.

### Tasks

1. `AttachmentDialog<C>` becomes `StandardDialog<@Nullable C, C>`. The nullable input is
   the change the element already carries; it is also what decides whether OK says Add
   or Modify, and — through `ops.remove()` being null or not — whether Remove appears.
   Do not wrap it in a record; the nullable value carries everything the dialog needs.
2. `AttachmentBackEndBase` becomes `AttachmentDialogController<C> extends
   DialogController<@Nullable C, C>`. Its `modifyTarget` and `final remove()` carry the
   one-undo-step guarantee — **that guarantee moves intact**, and stays enforced by
   `modifyTarget` being the only route to the element. Getting it wrong is invisible
   until someone presses Undo.
3. `AnnotationBackEnd`, `BeatChangeBackEnd` and `TempoChangeBackEnd` become
   `AnnotationController`, `BeatChangeController` and `TempoChangeController`.
4. `AttachmentTarget` moves from `backend` to `ui.dialog`, unchanged. Its constructor
   invariant and `forElement` factory stay exactly as they are.
5. The existing static `AttachmentDialogController.edit` /
   `editXxxOnSelection` entry points stay as the two ways in. They now construct a
   controller instance and pass `controller.ops()` to the dialog.
6. Apply the mechanical test to all four dialog classes by inspection.

---

## ⏸️ Phase 4: SongSettingsDialog and Its Tabs

**Status:** Blocked by 1  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/ui/dialog/SongSettingsDialog.java, src/main/java/songscribe/ui/dialog/SongSettingsController.java, src/main/java/songscribe/ui/dialog/SongSettingsMusicTab.java, src/main/java/songscribe/ui/dialog/SongSettingsTitleTab.java, src/main/java/songscribe/ui/dialog/SongSettingsAttributionTab.java, src/main/java/songscribe/ui/dialog/SongSettingsFontTab.java, src/main/java/songscribe/ui/dialog/backend/  <br>
**Recommended model/effort:** Opus, high — the largest controller in the track, and the one place where a decided relocation could quietly turn into a redesign.

`SongSettingsDialog` becomes `StandardDialog<SongSettingsInput, SongSettingsOutput>`.
`ScoreSongSettingsBackEnd` becomes `SongSettingsController`.

### The rules classes are absorbed, not relocated

All three move onto `SongSettingsController`:

| Class | What it holds | Becomes |
|---|---|---|
| `LineWidthRules` (137 LOC) | `validate(LineWidthEntry) → ValidationResult` parses the typed text and range-checks it against `PageModel.MIN/MAX_LINE_WIDTH_INCHES`; `resolveSs(Song, LineWidthEntry)` answers the width to store, returning the song's existing width untouched when the field was never edited | methods on the controller |
| `SongSettingsRules` (127 LOC) | `lyricsFit(Song, LyricsFontChange, double)` — whether every line that fits today still fits under a candidate lyrics font and width; `applyMusicSettings(Song, Tempo)` — posts `TempoDidChangeNotification` inside a bracket, and nothing when unchanged | methods on the controller |
| `LyricsFontChange` (39 LOC) | two adjacent `Font`s the signature rules forbid passing loose | a nested record on the controller |

**`SongSettingsMusicTab`'s `InputVerifier` is the wrinkle.** It calls
`LineWidthRules.validate` today so the field's own focus check and OK cannot disagree.
That property must survive: the controller exposes `validateLineWidth(LineWidthEntry)`
publicly, the tab asks it, and the controller's own `validate` asks the same method.
**Two callers of one function, not two copies of one rule.**

### Tasks

1. Read `docs/mutations.md` before touching the commit. The one-bracket property is a
   stated invariant, not an implementation detail.
2. Write `SongSettingsController`'s contracts before moving any body. The cross-tab
   validation ordering is a real precondition and must stay stated: the line width is
   settled first, because the fit check needs a width to measure against and a width
   that does not parse has none.
3. Move the three classes' bodies onto the controller. Delete `SongSettingsTarget` — the
   controller holds the `ScoreView` directly.
4. Re-point `SongSettingsMusicTab`'s verifier at the controller.
5. `SongSettingsDialog.show(Section)` and the tab/focus mapping are untouched.
6. Delete the now-empty `songscribe/ui/dialog/backend/` package.
7. Apply the mechanical test to the dialog and all five tab classes.

---

## ⏸️ Phase 5: KeyChangeDialog

**Status:** Blocked by 1  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/ui/dialog/KeyChangeDialog.java, src/main/java/songscribe/ui/dialog/KeyChangeDialogController.java, src/main/java/songscribe/ui/component/score/LineComponent.java, src/main/java/songscribe/ui/action/KeyChangeAction.java, src/main/java/songscribe/ui/component/ScoreViewController.java  <br>
**Recommended model/effort:** Opus, high — four gestures resolving to two commit routes, and it is what unblocks the `keys` pass.

### The dialog

**`KeyChangeDialog extends StandardDialog<Key, Key>`.** Non-null both ways. `I` is the
key in effect where the change is bound: what the combo opens on, and the one entry OK
refuses. `O` is the key the notator picked.

**The inherit entry is deleted.** `InheritChoice`, `KeyChoiceRenderer` and
`Strings.LABEL_KEY_INHERIT` all go. Whether a line holds its own key or inherits one is
an internal representation the notator never sees — all they see is a key signature on
the score. `Line.setKey` already normalizes a key equal to the inherited one back to
null, so nothing in the model needs the distinction spelled at the UI either.

What remains of the dialog: a `JComboBox<Key>`, `KeyCellRenderer`, `populate(Key)`,
`gather() → Key`, and the listener that enables OK when the selection differs.

### The controller resolves the gesture

**Four gestures, resolved by `KeyChangeDialogController`, which is the only thing that
reads the insertion index.** The class is currently dead code whose Javadoc claims two
callers it does not have; this phase gives it a body and both openers route through it.

| # | Gesture | Opening `Key` | Commit |
|---|---|---|---|
| 1 | header double-click (`LineComponent.java:957`) | `line.getKey()`, else the running key | `changeLineKey` |
| 2 | cautionary double-click (`LineComponent.java:971`) | same, on the **next** line | `changeLineKey` |
| 3 | mid-line signature double-click (`LineComponent.java:964`) | the clicked `KeyChangeElement`'s own key, read directly | `insertKeySignature` ⚠ |
| 4 | `KeyChangeAction.insertionPointChosen` | `line.keyAt(index)` | `insertKeySignature` |

**⚠ Gesture 3 is a live bug this phase does not fix.** Double-clicking a mid-line key
signature *inserts a second one in front of the one that was clicked* instead of changing
it. The score reads `♯♯♯ ♭♭`, and since the second signature has the last word the music
from there on is still in the old key — the edit appears to do nothing but add clutter. No
stray barline appears: `insertKeySignature` adds one only where the position does not
already follow a barline, and an existing signature always does. The dialog nevertheless
*looks* like an editor because `Line.keyAt` is inclusive, so asked at the clicked
signature's own index it returns that signature's key and the combo opens on it.
**Nothing in the program can change an existing mid-line key signature's key**;
`KeyChangeElement.setKey` has no caller outside its own class. Fixing it needs a third
commit route that does not exist — Phase 9 task 5 states what that route costs.

### This unblocks `keys` group C5

`KeyChangeDialog.currentChoiceFor` is the only caller relying on `Line.keyAt`'s
**inclusive** bound, and it relies on it to serve gestures 3 and 4 without telling them
apart. Once the controller resolves the opening key as a value — reading the clicked
element directly for gesture 3 — the dialog calls `keyAt` nowhere and `keyAt` can become
exclusive. See `plans/design-pass/keys.md`, group C item 5.

**Do not expect C5 to fix gesture 3.** `keyAt`'s bound decides what the combo shows;
`insertKeySignature` decides what OK writes. C5 touches only the first.

### Tasks

1. Read `docs/key-signatures.md` and `docs/mutations.md` before touching either commit
   route.
2. Write the contracts for `KeyChangeDialogController`'s entry points and its
   `read`/`validate`/`commit` before moving any body.
3. Two entry points on the controller, because only the caller can tell gestures 3 and 4
   apart: one for a line's own key, one for a mid-line signature carrying the index and
   whether it names an existing element. `LINE_OWN_KEY_INDEX` moves here.
4. **One controller with a `Route` enum, not two controllers.** The routes share the
   line, the metrics, the alert title and the whole gather-and-hand-over path, and
   differ in three places: which fit function runs, which message key the refusal
   carries, and which commit method is called. If that turns out uglier than two
   classes, say so and split.
5. Turn the two refusals into `ValidationResult`s. Each is a `ValidationFailure`
   carrying `ALERT_TITLE_LINE_TOO_FULL` and its own `LocalizedMessage` — the mid-line
   one with `ElementType.KEY_CHANGE.categoryName()` as an unresolved argument.
   `StandardDialog.showFailure` presents. **The comment explaining why the line-key
   message deliberately does not say "this line" moves with the message it is about.**
6. **Move `changeLineKey` and `insertKeySignature` off `ScoreViewController` onto
   `KeyChangeDialogController`.** They are two ~130-line domain methods with **exactly
   one caller each — `KeyChangeDialog.setData`** (`KeyChangeDialog.java:331` and `:334`),
   sitting on a 70-method view controller for no reason but that the dialog could reach
   it. The controller is that caller, holds the `MainFrame` and `ScoreView` they need,
   and already owns the fit check and the route choice.
   `AccidentalRestatements.confirm` takes `@Nullable Component parent`, which
   `DialogController.getMainFrame()` supplies, so nothing pins them where they are. Their
   contracts move with them — `insertKeySignature`'s statement of what it does and does
   not do is what guards gesture 3.
7. **Tighten `changeLineKey(Line, @Nullable Key, String)` to a non-null `Key`.** With the
   inherit entry gone the dialog always names a key, which makes dead both its
   `IllegalArgumentException` guard for a null key on line 0 and its
   `key != null ? key : previous.keyAtEndOfLine()` branch. `runningKeyAfterChange` goes
   with them: the chosen key *is* the running key.
8. Delete from `KeyChangeDialog`: `requireScoreView`, `requireController`,
   `requireInput`, `commitOnOk`, `isValidData`, `setData`, `chosenKey`,
   `requireChosenKey`, `runningKeyAfterChange`, `opLabel`, `KeyChangeInput`,
   `isMidLineInsertion`, `currentChoiceFor`, `InheritChoice` and `KeyChoiceRenderer`.
9. Route `LineComponent.openKeySignatureDialog` and
   `KeyChangeAction.insertionPointChosen` through the controller. Neither may construct
   `KeyChangeDialog` after this phase.
10. Delete the two Javadoc comments in `KeyChangeDialog` citing "Phase 2 of
    `plans/776-design-pass.md`" — they describe scaffolding this phase removes.
11. **Hand the restatement-prompt rule gap to Phase 9 task 2.** Both commit routes raise
    `AccidentalRestatements.confirm` before opening their bracket and return `false` when
    the notator cancels, but `commit` returns `void` and the framework's rule is that the
    domain side displays nothing. This is not a violation to fix here — the prompt
    belongs to every pitch-moving edit, and an inserted barline raises it with no dialog
    involved — but the rule does not describe it. Behaviour is unchanged either way:
    cancelling the prompt abandons the change and closes the dialog, as it does today.

---

## ⏸️ Phase 6: PreferencesDialog

**Status:** Blocked by 1  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/ui/dialog/PreferencesDialog.java, src/main/java/songscribe/ui/component/ScoreViewController.java, src/main/java/songscribe/layout/PageModel.java  <br>
**Recommended model/effort:** Sonnet, medium — 899 lines, no controller to build, but a real notification gap to close.

**`PreferencesDialog` gets no `DialogOps` and no controller.** It is the only non-modal
`BaseDialog` (`BaseDialog(…, false, EXCLUSIVE)`) — no OK, no Cancel, each edit applied
the moment it is made. There is no `I → O` cycle to put a boundary across. **It reads
and writes `Prefs`, and that is all it knows;** every other subsystem reacts to the
notification each write posts. Read `.claude/guides/prefs.md` first.

### Tasks

1. **Delete `PreferencesDialog.syncPlaybackPrefs()` and its four call sites.** It is the
   class's only `getScoreView()` and the only thing standing between this dialog and
   zero domain reach. `getMainFrame()` then remains solely to parent the MIDI error
   dialog.
2. **The reacting half already exists but is short by four keys, which is why the dialog
   calls the score directly.** `ScoreViewController.prefsDidChange` fires
   `syncPlaybackPrefs()` on `LOOP_PLAYBACK` and `PLAY_WITH_REPEATS`, and
   `updatePageLayout` on `PAGE_SIZE`. The keys this dialog writes that need a sync —
   `PLAY_INSERTED_NOTE`, `INSTRUMENT`, `PLAYBACK_NOTE_DURATION`, `TEMPO_CHANGE_PERCENT`
   — are absent. Widen it to the keys `ScoreView.syncPlaybackPrefs()` actually reads,
   then delete the dialog's copy.
3. **Fix the drift that gap came from.** The handler's `if` condition and
   `syncPlaybackPrefs()`'s reads are the same key set written twice with nothing
   connecting them; they have already diverged once. Derive the trigger set from one
   place. Read `docs/messages.md` before changing the handler.
4. **Leave `AppearanceManager.switchTheme` as the appearance write.** It writes
   `PrefsKey.APPEARANCE` itself and rolls the write back when `applyTheme` fails, so it
   owns the pref rather than bypassing `Prefs`.
5. **Remove the duplicated page-size encoding.** The listener hardcodes
   `a4Radio.isSelected() ? "a4" : "letter"` while the read side goes through
   `PageModel.getSize()`, which parses the same two strings. Give `PageModel.Size` a
   `key()` and have the listener write `size.key()`; `Appearance.key()` is the pattern.
6. **The audition stays in the dialog.** Playing a note on instrument select and playing
   the scale are the dialog's own knowledge of its affordance. Both
   `PlaybackController.stop()` calls stay too.
7. Extract `programToIndex`, `volumeToSliderIndex` and `buildScaleSequence(int program)`
   from the inline sequence construction in `ScaleAction.play()`. Contract before body.
8. **State the no-revert semantics in the class contract.** Closing the window keeps
   every change and there is nothing to undo. State also that the dialog does not
   subscribe to `PrefsDidChangeNotification`, so a `resetAll` from elsewhere while it is
   open leaves its widgets stale — true today, a stated limitation rather than an
   unexamined one.
9. **Record, do not perform, the instrument-registry move.** The static
   `instrumentStrings` / `instrumentPrograms` / `instrumentsLoaded` cache and its
   accessors are a MIDI service living on a dialog. The move is already designed in
   `plans/singleton-lifecycle-contracts.md` §6 and assigned in
   `plans/test-only-surface.md:481` to the `ui/playback`/`MidiController` phase. Confirm
   both entries still name it and leave it. **Note that `ExportMidiDialog`, which
   `test-only-surface.md` cites as the second caller, no longer exists** — check whether
   that changes the other plan's reasoning and report if it does.

---

## ⏸️ Phase 7: The Remaining Dialogs

**Status:** Blocked by 1  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/ui/dialog/FontDialog.java, src/main/java/songscribe/ui/dialog/DoNotShowMessage.java, src/main/java/songscribe/ui/dialog/ProgressBarDialog.java, src/main/java/songscribe/ui/dialog/AboutDialog.java, src/main/java/songscribe/ui/dialog/FontSettingRow.java  <br>
**Recommended model/effort:** Sonnet, low — two small controllers and a confirmation sweep.

### Tasks

1. **`FontDialog` becomes `StandardDialog<Font, Font>` with a `FontDialogController`.**
   Its commit stores the pick on the controller and the caller asks the controller for
   it. Today `FontDialog.getSelectedFont()` makes the window the holder of the result,
   which is the same coupling in the other direction.
2. **Delete the widened field at `FontDialog.java:37`**, commented "Widened to
   package-private for testing". It is the exact corollary the no-test-only-surface rule
   bans and it is named in `plans/test-only-surface.md`. Nothing reads it now that the
   test suite is gone.
3. **`DoNotShowMessage` becomes `StandardDialog<String, Boolean>` with a
   `DoNotShowMessageController`** holding the `PrefsKey`. Its input is the message text,
   which is a constructor argument today. Leave its `Prefs` routing alone — it reads
   through `Prefs.getBoolean` and writes through `Prefs.put`, which is correct.
   - **It has no production caller.** Keep it — it is a facility for suppressible
     messages — and say so in the report, so the decision is visible rather than
     assumed.
4. **`ProgressBarDialog` gets no controller.** It has no button row. It gains one when
   it gains Cancel, and not before.
5. Do **not** convert `AboutDialog` or `MigrationWindow` to `BaseDialog`. Both extend
   `JDialog` deliberately and both class docs explain why.
6. `FontSettingRow.defaultFont` lazy-caches `DocumentFonts.defaultFonts()`. Leave it; it
   is a caching decision, not domain reach.
7. The `fontchooser/` subpackage (22 files, 1,658 LOC) has zero domain reach and is
   untouched by this track.
8. **Run `./scripts/compile.sh`. This is the first compile since Phase 1** and the first
   point at which the end state is fully expressed. A failure here is information about
   the design; record what it says.

---

## ⏸️ Phase 8: Controller Contracts and Their Tests

**Status:** Blocked by 3, 4, 5, 6, 7  <br>
**BlockedBy:** 3, 4, 5, 6, 7  <br>
**Files:** src/test/java/songscribe/ui/dialog/  <br>
**Recommended model/effort:** Opus, high — deciding what each controller promises is contract judgment, and several are music-notation judgments that must be confirmed rather than decided.

**The controllers are the subject, and the only subject.** They hold every decision and
every write in this track, and are unit-testable without a window because nothing was
handed to them that needs one. A dialog's populate-gather-ops path is wiring; Phase 10
confirms it.

### Tasks

1. Re-read every contract Phases 1 and 3–7 wrote and check each against the rule that
   **the tell of a real contract is that the implementation could in principle violate
   it.** A contract that merely restates the body it was extracted from describes the
   code and promises nothing.
2. **Classify each contract as mechanical or domain** per
   `.claude/skills/contract-pass/reference/classification.md`. Parsing, range
   validation, unit conversion and index mapping are mechanical. `lyricsFit`,
   `canonicalKeySelectionFrom` (0 accidentals canonicalizing to FLATS is a
   music-notation judgment), and `applyMusicSettings`'s notification decision are
   domain. **Batch the domain ones into one checkpoint and present them for confirmation
   before writing tests against them** — a confident, plausible, wrong contract is worse
   than none, because every test downstream is derived from it.
3. **Propose the test list and wait.** No test exists for any of this. The list goes to
   the user before anything is written. Candidate contracts: the two key-change fit
   refusals, the key controller's route choice, line-width parsing and range validation,
   `lyricsFit`'s asymmetric measurement and its already-overflowing exemption, the
   attachment controllers' one-bracket guarantee and undo-step naming, and
   `programToIndex` / `volumeToSliderIndex`.
4. **Do not derive tests from a dialog's populate-gather-ops wiring.** Per this track
   that is classified none, and nothing else covers it either — Phase 10's manual pass
   is where wiring is confirmed.
   - **No test for key-change gesture 3.** Its commit route is known wrong — see the ⚠ in
     Phase 5 — and a test over it would either assert the defect or assert something so
     weak it proves nothing. Pinning a known defect is not one of the three kinds *The
     testing floor* allows. What guards it is `insertKeySignature`'s contract, which
     states that it inserts.
5. Write the testing-approach Javadoc on each new test class, stating which equivalence
   classes, boundaries and invariants it exercises — as the contract's clauses, not as a
   list of inputs.
6. Before writing each test method, check whether it will sit beside a sibling
   exercising the same function the same way with only the data differing; if so both
   are rows in one `record` case table from the first such case. A varying lambda does
   not disqualify a case — only a varying assertion does.
7. Where a test's domain is finite and small, drive the cases from `@EnumSource` /
   `values()` / a sealed hierarchy's permitted subclasses rather than writing
   "enumerated in full" in the Javadoc.
8. Run `./scripts/compile.sh` (SUCCESS) and `./scripts/test.sh` (green).

---

## ⏸️ Phase 9: Documentation Consistency Pass

**Status:** Blocked by 8  <br>
**BlockedBy:** 8  <br>
**Files:** .claude/guides/dialogs.md, docs/key-signatures.md, plans/test-only-surface.md, plans/singleton-lifecycle-contracts.md, plans/design-pass/keys.md  <br>
**Recommended model/effort:** Sonnet, medium — checking a written guide against what the work turned out to be.

### Tasks

1. Check `dialogs.md`, which Phase 1 rewrote, against what Phases 2–7 actually produced.
2. **Settle the restatement-prompt rule** Phase 5 task 11 hands over. Either the rule
   gains a stated exception for a prompt the domain owns, as against a validation
   message the dialog owns, or `commit` gains a return value meaning "the commit did not
   happen". Say which dialogs it could ever apply to; today it is `KeyChangeDialog`
   alone.
3. Update `plans/test-only-surface.md` — `SongSettingsDialog.getLineWidthFieldForTest()`
   and `FontDialog.java:37` are resolved; `PreferencesDialog.resetInstrumentsForTesting`
   (line 481) is **not**, and its cited second caller `ExportMidiDialog` no longer
   exists.
4. Update `plans/design-pass/keys.md` group C4 to done and C5 to unblocked.
5. **Open an issue or plan for the duplicate mid-line key signature** — gesture 3, the ⚠
   in Phase 5. It is the one live defect this track knowingly leaves standing, and it
   silently damages documents. What it needs, roughly parallel to `insertKeySignature`
   and, after Phase 5 task 6, alongside it on `KeyChangeDialogController`:
   - `changeKeySignature(Line, int elementIndex, Key)` — reconcile the accidentals the
     key move affects, raise the one restatement prompt, change the element's key in
     place inside one modification bracket, and **re-space the line**, because the new
     signature may be wider or narrower than the old one;
   - a `KeyEditFitCalculator.keySignatureChangeFits(…)` variant. The existing
     `keySignatureFits` measures a line with a column *added*, which is the wrong
     measurement for a swap;
   - a third route on the controller, and a third entry point, since only the caller can
     tell an existing signature from a new one.

   Call it ~200 lines of new domain code, none of it a variant of what Phase 5 wrote.
6. Confirm D2 and the `ui/dialog` scope paragraph in
   `plans/contract-driven-rollout.md` still match what the track produced.
7. `dialogs.md` is a **guide** — it states conventions, not promises. Anything that
   turned out to be a system invariant spanning subsystems goes in `docs/` instead.

---

## ⏸️ Phase 10: Manual UI Verification

**Status:** Blocked by 9  <br>
**BlockedBy:** 9  <br>
**Files:** —  <br>
**Recommended model/effort:** Sonnet, low — the agent prepares and reports; the user drives the app.

**This is where every dialog's wiring is confirmed, and the only place** — no test
covers a populate-gather-ops path. Unit tests also do not catch a dialog that opens at
the wrong size, focuses the wrong field, or shows a validation message that reads wrong.
**Nothing in this track or the `keys` pass before it has been seen on screen.**

### Tasks

1. Ask the user for permission before running the app; `./scripts/run.sh` must never be
   executed without it.
2. Give the user a checklist covering, for each dialog in the package: it opens,
   geometry and tab selection are as before, **OK commits and the model changes**,
   Cancel discards, and a deliberately invalid input produces the right message. Every
   dialog appears, not only the ones this track rewrote — this is the wiring pass.
3. Include the cases most at risk: `SongSettingsDialog`'s cross-tab lyrics-fit failure,
   the Add-vs-Modify button label and the Remove button on each of the three attachment
   dialogs (Remove is rendered by `StandardDialog` now), and
   `SongSettingsDialog.show(Section)` opening the right tab with the right field focused.
4. **All four key-change gestures**, and the key combo's fifths ordering and glyphs.
   **Gesture 3 — a double-click on a mid-line key signature — will produce two key
   signatures.** That is the ⚠ in Phase 5 and is expected; it is the one item on this
   checklist whose failure is not a defect in this track.
5. `PreferencesDialog` needs its own items. Its live side effects travel through
   `PrefsDidChangeNotification` rather than a direct call: switching appearance retints
   immediately, page size and units take effect without reopening, the three Play
   sliders snap, selecting an instrument auditions a note, the Scale button toggles and
   restarts on a new selection, leaving the Instruments tab stops the scale. **Play the
   score after changing instrument, tempo and note duration** — those are the four keys
   the widened handler now carries, and a handler that misses one fails silently.
6. Record the result. Anything that fails is a defect in this track, not a new finding.

---

## Verification (whole plan)

1. `./scripts/compile.sh` prints SUCCESS.
2. `./scripts/test.sh` is green.
3. **Every dialog's constructor takes `MainFrame`, a `DialogOps`, and presentation
   constants, and nothing else.** No dialog field names `Song`, `Line`, `StaffElement`,
   `ScoreView` or a controller. This is the mechanical acceptance test for the track.
4. `songscribe.ui.dialog.backend` does not exist. Neither does `DialogBackEnd`,
   `AttachmentBackEnd`, `SongSettingsBackEnd`, `CommitDialog` or `BaseDialog.DialogOp`.
5. `BaseDialog` exposes no route to the score.
6. `PreferencesDialog` contains no `getScoreView()` call and `syncPlaybackPrefs()` is
   gone from it. It still uses `Prefs` and `PrefsKey` by design.
7. `plans/test-only-surface.md`'s two dialog-decoupling entries are resolved;
   `resetInstrumentsForTesting` is reported as outstanding rather than as done.
8. `contract-driven-rollout.md`'s D2 and `ui/dialog` scope paragraph still match what
   the track produced.
9. **Two things are knowingly left undone and must be reported as such:** the duplicate
   mid-line key signature on gesture 3, and the restatement-prompt rule gap if Phase 9
   defers it.
10. `ScoreViewController` no longer declares `changeLineKey` or `insertKeySignature`;
    both are on `KeyChangeDialogController`.
