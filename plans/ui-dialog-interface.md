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
`SongSettingsInput`, `SongSettingsOutput`, `LyricsContext`, `WordsDate`; and
`BaseDialog`'s lack of any route to the score.

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [The Controller Framework](#-phase-1-the-controller-framework) | ✅ Done | — |
| 2 | [Delete the Legacy Dialogs](#-phase-2-delete-the-legacy-dialogs) | ✅ Done | — |
| 3 | [The Attachment Family](#-phase-3-the-attachment-family) | ✅ Done | — |
| 4 | [SongSettingsDialog and Its Tabs](#-phase-4-songsettingsdialog-and-its-tabs) | ✅ Done | — |
| 5 | [KeyChangeDialog](#-phase-5-keychangedialog) | ✅ Done | — |
| 6 | [PreferencesDialog](#-phase-6-preferencesdialog) | ✅ Done | — |
| 7 | [The Remaining Dialogs](#-phase-7-the-remaining-dialogs) | ✅ Done | — |
| 8 | [Controller Contracts and Their Tests](#-phase-8-controller-contracts-and-their-tests) | ✅ Done | — |
| 9 | [Documentation Consistency Pass](#-phase-9-documentation-consistency-pass) | ⏳ Pending | — |
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

## ✅ Phase 1: The Controller Framework

**Status:** Done  <br>
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

    /** Assembled once here so no subclass wires it by hand. Public: openers are not all
     *  in ui.dialog — Actions registers the cached menu actions from ui.action. */
    public final DialogOps<I, O> ops() {
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

## ✅ Phase 2: Delete the Legacy Dialogs

**Status:** Done  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/ui/dialog/WhatsNewDialog.java, src/main/java/songscribe/ui/dialog/ReportBugDialog.java, src/main/java/songscribe/ui/component/MainFrame.java, src/main/resources/songscribe/strings.properties  <br>
**Recommended model/effort:** Sonnet, low — two deletions and one caller.

`ReportBugDialog`, `WhatsNewDialog` and `MainFrame.maybeShowWhatsNew()` are gone, together
with the seven `dialog.bug.report.*` / `dialog.whats.new.title` / `error.email.open.report`
keys, which the dead-key audit refuses once nothing references them.

**`PrefsKey.LAST_SEEN_WHATS_NEW_VERSION` stays**, for a replacement to use; nothing reads it
today. **There are no `help/release-notes-*.html` resources** — they went in
`694058b4 chore: remove legacy help, tutorial, tips`, before this track — so a replacement
writes its own release notes rather than finding them in the tree.

---

## ✅ Phase 3: The Attachment Family

**Status:** Done  <br>
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

## ✅ Phase 4: SongSettingsDialog and Its Tabs

**Status:** Done  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/ui/dialog/SongSettingsDialog.java, src/main/java/songscribe/ui/dialog/SongSettingsController.java, src/main/java/songscribe/ui/dialog/SongSettingsMusicTab.java, src/main/java/songscribe/ui/dialog/SongSettingsTitleTab.java, src/main/java/songscribe/ui/dialog/SongSettingsInput.java, src/main/java/songscribe/ui/dialog/SongSettingsOutput.java, src/main/java/songscribe/ui/dialog/DialogController.java, src/main/java/songscribe/ui/component/ScoreView.java, src/main/java/songscribe/ui/action/Actions.java, src/main/java/songscribe/ui/dialog/backend/  <br>
**Recommended model/effort:** Opus, high — the largest controller in the track, and the one place where a decided relocation could quietly turn into a redesign.

`SongSettingsDialog` is `StandardDialog<SongSettingsInput, SongSettingsOutput>`.
`ScoreSongSettingsBackEnd` is `SongSettingsController`.

### The line width is not edited here

The Music tab carries a tempo and nothing else. The line width belongs to page setup
(`specs/184b-page-setup.md`), so `LineWidthRules`, `LineWidthEntry`, `MusicChoices` and
`MusicSettings` do not exist, and neither do the five string keys they used. **Until page
setup is built there is no UI that writes `Song.lineWidthSs`** — `ScoreView.updatePageLayout`
survives, called only on open and on `setSong` to re-lay out at the stored width.

`SongSettingsInput` still carries `lineWidthSs`: the Title tab's previews wrap at it so they
break where the score does. It is read and never written back.

### What the controller holds

| Was | Is |
|---|---|
| `SongSettingsRules.lyricsFit(Song, LyricsFontChange, double)` | `SongSettingsController.lyricsFit(Song, LyricsFontChange)`, private static — both fonts measured at the song's stored width, which this dialog cannot change |
| `SongSettingsRules.applyMusicSettings(Song, Tempo)` | `applyTempo(Song, Tempo)`, private static — posts `TempoDidChangeNotification` inside the commit's bracket, and nothing when unchanged |
| `LyricsFontChange` | a private nested record on the controller |
| `SongSettingsTarget` | gone; the controller reaches the view through `DialogController.requireScoreView()` |

`DialogController.ops()` is **public**, not protected: `Actions` opens this dialog from
`songscribe.ui.action`, and Phase 3's openers were the only ones inside `ui.dialog`.

### Newly dead, left standing

`Utils.roundToTwoDecimalPlaces`, `InputUtils.addDecimalFilter` (both overloads) and its
`DecimalDocumentFilter` lost their only caller with the line-width field. Page setup is the
natural next caller of all three.

---

## ✅ Phase 5: KeyChangeDialog

**Status:** Done  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/ui/dialog/KeyChangeDialog.java, src/main/java/songscribe/ui/dialog/KeyChangeDialogController.java, src/main/java/songscribe/dom/ElementType.java, src/main/java/songscribe/ui/component/score/LineComponent.java, src/main/java/songscribe/ui/action/KeyChangeAction.java, src/main/java/songscribe/ui/component/ScoreViewController.java  <br>
**Recommended model/effort:** Opus, high — four gestures resolving to two commit routes, and it is what unblocks the `keys` pass.

### The dialog

**`KeyChangeDialog extends StandardDialog<Key, Key>`.** Non-null both ways. `I` is the
key in effect where the change is bound: what the combo opens on, and the one entry OK
refuses. `O` is the key the notator picked. The whole of it is a `JComboBox<Key>` over
`Key.allSignatures()`, `KeyCellRenderer`, `populate(Key)`, `gather() → Key`, and the
listener that enables OK when the selection differs.

**There is no inherit entry.** `InheritChoice`, `KeyChoiceRenderer` and
`Strings.LABEL_KEY_INHERIT` do not exist. Whether a line holds its own key or inherits one
is an internal representation the notator never sees — all they see is a key signature on
the score — and `Line.setKey` normalizes a key equal to the inherited one back to null, so
nothing in the model needs the distinction spelled at the UI either.

### The controller resolves the gesture

**Four gestures, three entry points on `KeyChangeDialogController`, which is the only
thing that reads the insertion index.** Each caller hands over what it already holds, so
none states its binding twice and none constructs the dialog.

| # | Gesture | Entry point | Opening `Key` | Commit |
|---|---|---|---|---|
| 1 | header double-click | `editLineKey(frame, line)` | `line.getRunningKey()` | `changeLineKey` |
| 2 | cautionary double-click | `editLineKey(frame, nextLine)` | same, on the **next** line | `changeLineKey` |
| 3 | mid-line signature double-click | `editKeyChange(frame, line, signature)` | the clicked `KeyChangeElement`'s own key, read off the element | `insertKeyChange` ⚠ |
| 4 | `KeyChangeAction.insertionPointChosen` | `addKeyChange(frame, line, index)` | `line.keyAt(index)` | `insertKeyChange` |

Inside, a private `Binding` enum — `LINE_KEY`, `EXISTING_SIGNATURE`, `NEW_POSITION` —
carries which entry point built the controller. `read()` switches on all three;
`validate` and `commit` branch on line-key against mid-line, which is where the fit
function, the refusal message and the commit method vary together.
`changeLineKey` and `insertKeyChange` are private methods of this class:
`ScoreViewController` declares neither, and `AccidentalRestatements.confirm` is parented
on `getMainFrame()`. Both are `void` — the boolean saying "the notator cancelled at the
restatement prompt" had no reader once `commit` returned void; what it said is in their
contracts (see Phase 9 task 3).

**⚠ Gesture 3 is a live bug this phase does not fix.** Double-clicking a mid-line key
signature *inserts a second one in front of the one that was clicked* instead of changing
it. The score reads `♯♯♯ ♭♭`, and since the second signature has the last word the music
from there on is still in the old key — the edit appears to do nothing but add clutter. No
stray barline appears: `insertKeyChange` adds one only where the position does not
already follow a barline, and an existing signature always does. The dialog nevertheless
*looks* like an editor because the controller opens it on the clicked signature's own key.
**Nothing in the program can change an existing mid-line key signature's key**;
`KeyChangeElement.setKey` has no caller outside its own class. Fixing it needs a third
commit route that does not exist — `plans/design-pass/keys.md` group C item 6 carries
what that route costs, and `editKeyChange` is the entry point it attaches to.

### This unblocks `keys` group C5

No caller relies on `Line.keyAt`'s **inclusive** bound any more. `keyAt` is asked at one
place in this package — gesture 4's opening key, at a position the insertion predicate has
already refused to place beside a key signature, so the two bounds agree there. Gesture 3
reads the element. See `plans/design-pass/keys.md`, group C item 5.

**Do not expect C5 to fix gesture 3.** `keyAt`'s bound decides what the combo shows;
`insertKeyChange` decides what OK writes. C5 touches only the first.

### The refusals

Both are a `ValidationFailure` under `ALERT_TITLE_LINE_TOO_FULL`, presented by
`StandardDialog.showFailure`. The mid-line one names the category through a **nested**
`LocalizedMessage` built from `ElementType.KEY_CHANGE.categoryNameKey()`, which this phase
added beside `categoryName()` — the latter resolves through it, so its other two callers
are unchanged and the controller states what is wrong without producing text.

### Handed to Phase 9

**The restatement-prompt rule gap.** Both commit routes raise
`AccidentalRestatements.confirm` before opening their bracket, but the framework's rule is
that the domain side displays nothing. It is not a violation to fix here — the prompt
belongs to every pitch-moving edit, and an inserted barline raises it with no dialog
involved — but the rule does not describe it. Behaviour is unchanged: cancelling the prompt
abandons the change and closes the dialog, as it did before.

---

## ✅ Phase 6: PreferencesDialog

**Status:** Done  <br>
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
5. **`PrefsKey.METRIC` is now write-only.** The units radio writes it and reads it back to
   set its own initial state; nothing else consumes it, because the line-width field that
   displayed inches or centimetres is gone (Phase 4). `LengthUnit` has no production caller
   either. Report it — the radio currently changes nothing the user can see, and page setup
   (`specs/184b-page-setup.md`) is what makes it mean something again. **Do not delete the
   key or the enum**, and say so rather than leaving the dead-key audit to raise it.
6. **Remove the duplicated page-size encoding.** The listener hardcodes
   `a4Radio.isSelected() ? "a4" : "letter"` while the read side goes through
   `PageModel.getSize()`, which parses the same two strings. Give `PageModel.Size` a
   `key()` and have the listener write `size.key()`; `Appearance.key()` is the pattern.
7. **The audition stays in the dialog.** Playing a note on instrument select and playing
   the scale are the dialog's own knowledge of its affordance. Both
   `PlaybackController.stop()` calls stay too.
8. Extract `programToIndex`, `volumeToSliderIndex` and `buildScaleSequence(int program)`
   from the inline sequence construction in `ScaleAction.play()`. Contract before body.
9. **State the no-revert semantics in the class contract.** Closing the window keeps
   every change and there is nothing to undo. State also that the dialog does not
   subscribe to `PrefsDidChangeNotification`, so a `resetAll` from elsewhere while it is
   open leaves its widgets stale — true today, a stated limitation rather than an
   unexamined one.
10. **Record, do not perform, the instrument-registry move.** The static
   `instrumentStrings` / `instrumentPrograms` / `instrumentsLoaded` cache and its
   accessors are a MIDI service living on a dialog. The move is already designed in
   `plans/singleton-lifecycle-contracts.md` §6 and assigned in
   `plans/test-only-surface.md:481` to the `ui/playback`/`MidiController` phase. Confirm
   both entries still name it and leave it. **Note that `ExportMidiDialog`, which
   `test-only-surface.md` cites as the second caller, no longer exists** — check whether
   that changes the other plan's reasoning and report if it does.

---

## ✅ Phase 7: The Remaining Dialogs

**Status:** Done  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/ui/dialog/FontDialog.java, src/main/java/songscribe/ui/dialog/FontDialogController.java, src/main/java/songscribe/ui/dialog/DoNotShowMessage.java, src/main/java/songscribe/ui/dialog/DoNotShowMessageController.java, src/main/java/songscribe/ui/dialog/ProgressBarDialog.java, src/main/java/songscribe/ui/dialog/AboutDialog.java, src/main/java/songscribe/ui/dialog/FontSettingRow.java, src/main/java/songscribe/ui/dialog/DialogOps.java, src/main/java/songscribe/ui/dialog/DialogController.java, src/main/java/songscribe/ui/dialog/StandardDialog.java, src/main/java/songscribe/ui/dialog/AttachmentDialogController.java, src/main/java/songscribe/ui/dialog/AnnotationController.java, src/main/java/songscribe/ui/dialog/BeatChangeController.java, src/main/java/songscribe/ui/dialog/TempoChangeController.java  <br>
**Recommended model/effort:** Sonnet, low — two small controllers and a confirmation sweep.

`FontDialog` is `StandardDialog<Font, Font>` with `FontDialogController`; `DoNotShowMessage` is
`StandardDialog<String, Boolean>` with `DoNotShowMessageController`, its message text now `I`
rather than a constructor argument. `DoNotShowMessage` has no production caller — kept as a
facility for suppressible messages. `ProgressBarDialog`, `AboutDialog`, `MigrationWindow`,
`FontSettingRow` and `fontchooser/` are unchanged.

**`DialogOps<I, O>`, `DialogController<I, O>` and `StandardDialog<I, O>` declare
`I extends @Nullable Object`.** Compiling this phase's own files first surfaced it: `I` carried
the default non-null bound, so `AttachmentDialog<C> extends StandardDialog<@Nullable C, C>`
(Phase 3) could not typecheck under NullAway. Widening `I`'s bound fixed the three attachment
dialog classes, but not `AttachmentDialogController`'s own `.ops()` call sites — NullAway does
not compose a `@Nullable` wrap performed in one generic class's `extends` clause with a further
substitution a concrete subclass performs one level down, so `new AnnotationController(...).ops()`
resolved to `DialogOps<Annotation, Annotation>` rather than `DialogOps<@Nullable Annotation,
Annotation>`. `AttachmentDialogController<C> extends DialogController<@Nullable C, C>` is now
`AttachmentDialogController<I extends @Nullable Object, O> extends DialogController<I, O>` — a
straight pass-through — and `AnnotationController`, `BeatChangeController` and
`TempoChangeController` each name both type arguments directly, e.g.
`AttachmentDialogController<@Nullable Annotation, Annotation>`, rather than supplying one value
type and letting an intermediate class wrap it.

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

## ✅ Phase 8: Controller Contracts and Their Tests

**Status:** Done  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/util/Copyable.java, src/main/java/songscribe/ui/dialog/, src/main/java/songscribe/dom/, src/main/java/songscribe/ui/component/TickSlider.java, .claude/guides/dialogs.md, .claude/guides/testing-common.md  <br>
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
   music-notation judgment), and `applyTempo`'s notification decision are
   domain. **`lyricsFit` and `applyTempo` are private on `SongSettingsController`.**
   Widening either to test it directly is a decision to take here, against the
   no-test-only-surface rule — the alternative is reaching them through `validate` and
   `commit`, which needs a `MainFrame`. **Batch the domain ones into one checkpoint and present them for confirmation
   before writing tests against them** — a confident, plausible, wrong contract is worse
   than none, because every test downstream is derived from it.
3. **Propose the test list and wait.** No test exists for any of this. The list goes to
   the user before anything is written. Candidate contracts: the two key-change fit
   refusals, the key controller's route choice, `lyricsFit`'s already-overflowing
   exemption and its unchanged-font short circuit, `applyTempo`'s post-only-when-changed
   decision, the attachment controllers' one-bracket guarantee and undo-step naming, and
   `programToIndex` / `volumeToSliderIndex`.
4. **Do not derive tests from a dialog's populate-gather-ops wiring.** Per this track
   that is classified none, and nothing else covers it either — Phase 10's manual pass
   is where wiring is confirmed.
   - **No test for key-change gesture 3.** Its commit route is known wrong — see the ⚠ in
     Phase 5 — and a test over it would either assert the defect or assert something so
     weak it proves nothing. Pinning a known defect is not one of the three kinds *The
     testing floor* allows. What guards it is `insertKeyChange`'s contract, which
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

### What this phase produced

**Two design fixes, and no surviving tests.**

The contract audit found the track's central promise false in two places.
`DialogController.read()` promised that what a dialog is shown "holds no live handle" on
the document, and `AnnotationController` and `TempoChangeController` both handed over the
attachment's own mutable value — no live defect, because neither dialog mutated what it
was given, but nothing stopped the next edit from doing so.

The fix is structural rather than two `.copy()` calls: **`songscribe.util.Copyable<T>`, with
`I extends @Nullable Copyable<I>` bounding `DialogOps`, `DialogController`,
`StandardDialog`, `AttachmentDialog` and `AttachmentDialogController`, and the copy
performed once in `DialogController.ops()`.** `read()` now reads and hands over what it
holds. `Annotation`, `Tempo`, `BeatChange`, `Key` and `SongSettingsInput` implement it;
`FontChoice` and `MessageText` wrap the two JDK-typed inputs, which cannot. The rule and
its NullAway wrinkle are in `.claude/guides/dialogs.md`.

Second fix: `PreferencesDialog.volumeToSliderIndex` duplicated `TickSlider.setSnappedValue`'s
nearest-stop loop. Extracted as **`TickSlider.nearestStopIndex`**, which also retired the
`// exposed for testing` widening on `VALID_VOLUME_STOPS` — both it and `volumeToSliderIndex`
are private now.

**Nine tests were written, run green, and deleted in the same change.** Per
*One-time is the default* in `.claude/guides/testing-common.md`, which this phase added:
the contract is the durable artifact, and a test is written again if and when the contract
or the implementation changes. What they confirmed, once:

| Subject | Confirmed |
|---|---|
| all three attachment controllers | one commit posts exactly one `SongDidChangeNotification`, including the two that nest `Song.withBeatDefiningEditOn`; the undo step is named Add / Change / Remove per the document's state; a second commit replaces the attachment rather than adding one beside it; `ops().remove()` is null exactly when there is nothing to remove |
| `SongSettingsController` | the lyrics-font rule across all three of its classes, including that an already-overflowing line never refuses; the tempo is announced only when it changed |
| `AttachmentTarget` | `elementIndex()` moves with an insertion ahead of the element; the constructor refuses an element the line does not hold and `forElement` answers null for the same cases |
| `DialogController.ops()` | the input a dialog is shown is a copy — mutating it leaves the element's annotation, the element's tempo, and the song's tempo and fonts untouched |
| `TickSlider.nearestStopIndex` | outside the range answers the nearer end, between two stops the nearer, an exact tie the lower position |

**Writing them found a fixture trap worth recording: no fixture in
`src/test/resources/fixtures/` carries syllables on notes.** The two lyric-width cases
first passed against `insertion` and `overflowing-lines` while measuring nothing at all —
widening a lyrics font over a document with no lyrics changes no line. They were rewritten
to build syllables through `setLyricForVerse`, in a pair that asserts opposite verdicts
under the same font change, so an arrangement that did not take fails one of them instead
of passing both. Any future lyric-layout test needs its own syllables or a new fixture.

**Not tested, and why.** `KeyChangeDialogController` is not constructible from a test: its
constructor is private and each of its three entry points constructs the controller and
opens a modal window in one call. Its own logic is route choice — a switch on a private
enum and a branch on the same enum — and the algorithms it calls live in `layout/`.
`PreferencesDialog.programToIndex` needs a MIDI synthesizer. `DoNotShowMessageController`
is one `if` with no production caller.

**Outstanding findings**, for Phase 9 to place:

- `PreferencesDialog.programToIndex` returns `0` for an unknown program — an arbitrary
  default that is also a legitimate answer (instrument 0), which *Guards* in
  `~/.claude/guides/design.md` says must never be written. It is also `public` with one
  caller, inside `PreferencesDialog`.
- This plan's Phase 8 task 2 named `canonicalKeySelectionFrom`, which does not exist —
  Phase 5 dropped the inherit entry, so nothing canonicalizes a key selection. Phase 6
  task 8 named `buildScaleSequence(int program)` as an extraction; it exists, but as a
  private method of `ScaleAction` rather than a class-level static.
- `UnitTest.java:246` and `.claude/guides/testing-unit.md` both reference
  `songscribe.ui.selection.ReflectionTestHelper`, and `.claude/guides/testing-common.md`
  cites `ElementInsertionTest` and `OpNamesTest`. None of the three exists.

---

## ⏳ Phase 9: Documentation Consistency Pass

**Status:** Pending  <br>
**BlockedBy:** —  <br>
**Files:** .claude/guides/dialogs.md, docs/key-signatures.md, plans/test-only-surface.md, plans/singleton-lifecycle-contracts.md, plans/design-pass/keys.md  <br>
**Recommended model/effort:** Sonnet, medium — checking a written guide against what the work turned out to be.

### Tasks

1. Check `dialogs.md`, which Phase 1 rewrote, against what Phases 2–7 actually produced.
   One item is known: the `Tab` section says a tab whose control checks a rule as the user
   types asks the same function the controller's `validate` asks, handed to it as a
   function reference. **No tab does this any more** — the line-width verifier was the only
   one, and the title field's `NonEmptyGuard` is a UI-only rule the controller never sees.
   Either state the convention as one nothing currently exercises, or drop it.
2. **Update `specs/184b-page-setup.md`.** Its Current State section describes a line-width
   field in `SongSettingsDialog` that is gone, and its Removed list carries work Phase 4
   already did. State what page setup still has to build: it is now the *only* route to
   `Song.lineWidthSs`, not a replacement for an existing one.
3. **Settle the restatement-prompt rule** Phase 5 hands over. Either the rule
   gains a stated exception for a prompt the domain owns, as against a validation
   message the dialog owns, or `commit` gains a return value meaning "the commit did not
   happen". Say which dialogs it could ever apply to; today it is `KeyChangeDialog`
   alone.
4. Update `plans/test-only-surface.md` — `SongSettingsDialog.getLineWidthFieldForTest()`
   and `FontDialog.java:37` are resolved; `PreferencesDialog.resetInstrumentsForTesting`
   (line 481) is **not**, and its cited second caller `ExportMidiDialog` no longer
   exists.
5. Update `plans/design-pass/keys.md` group C4 to done and C5 to unblocked.
6. **Confirm the duplicate mid-line key signature is recorded** — gesture 3, the ⚠ in
   Phase 5. It is the one live defect this track knowingly leaves standing and it
   silently damages documents, so check rather than assume. It is
   `plans/design-pass/keys.md` group C item 6, which carries the defect, the three
   pieces the fix needs and its ~200 lines of domain work. Check that item still matches
   what Phase 5 actually built; do not restate it here.
7. Confirm D2 and the `ui/dialog` scope paragraph in
   `plans/contract-driven-rollout.md` still match what the track produced.
8. `dialogs.md` is a **guide** — it states conventions, not promises. Anything that
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
2. `./scripts/test.sh` succeeds, reporting **`0 passed`**, because the suite holds no
   `@Test` methods. That is the pre-existing state — `src/test` held base classes and
   helpers only before this track and still does — and Phase 8's tests were one-time,
   deleted in the change that produced them. **This step cannot be read as coverage.** What
   confirms the dialogs is Phase 10.

   Phase 8 set `failOnNoDiscoveredTests = false` in `build.gradle.kts` for this reason:
   Gradle 9 fails a task that discovers nothing, treating it as a misconfiguration, which
   is the wrong reading in a project where an empty suite is the normal resting state. A
   failing test still fails the build.
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
10. `ScoreViewController` declares neither key-change commit route; both are private
    on `KeyChangeDialogController`, as `changeLineKey` and `insertKeyChange`.
