# ui/dialog — Decoupling Seam (D2, D4)

Executes the `ui/dialog` row of [`contract-driven-rollout.md`](./contract-driven-rollout.md).
It is the **last row the D10 freeze covers**; nothing outside the rollout resumes until
it is done.

This is an architectural track, not a contract pass. `/contract-pass ui.dialog` must
**not** be run against the package as it stands: `SongSettingsDialog.isValidData()`
returns a boolean *and* pops a modal, so there is no back-end API to contract until the
seam extracts one, and writing a contract for the fused method would document the defect
instead of removing it.

## The target

Every dialog is decoupled in both directions:

- a **record in** — the dialog never reaches for `Song`, `MainFrame` or the score;
- a **record out** — gathered from the widgets;
- a **back end passed in** — a small interface the dialog calls to validate and to
  apply, supplied already bound to whatever domain state it needs.

The dialog becomes a widget shell over `Input → Output` plus a callback, and knows
nothing about the domain.

**One dialog is not commit-on-OK and gets no back end at all.** `PreferencesDialog` is the
only non-modal `BaseDialog`; it has no OK or Cancel and applies each edit the moment it is
made. It has no input/modify/output cycle to put a record boundary across, so forcing one
onto it would invent machinery to model a cycle that does not exist. Its seam is
`PrefsDidChangeNotification`: the dialog reads and writes `Prefs` and knows nothing else,
and every other subsystem reacts to the notification `Prefs` posts. That satisfies this
section's rule as written — `Prefs` is a global store in `songscribe.prefs`, not `Song`,
`MainFrame` or the score — and the reacting half of it already exists. Phase 5 task 4
finishes it.

**The mechanical test, applied to every back-end signature:** it contains no Swing type.
`validate(Input) → ValidationResult` and `apply(Song, Input)` pass; anything naming a
`JComponent`, a `JTextField` or a `Font`-carrying widget fails. If the signature cannot
be stated that way, the logic is still entangled and *that is the finding*. A reviewer
applies this without judgment.

Under this rule a dialog's own three steps — gather, call validate, call apply — are
wiring, classified **none**, and most of the package's 267 test methods evaporate rather
than being rewritten.

## Current state (measured, not assumed)

| | |
|---|---:|
| Main | 10,010 LOC across 59 files |
| Test | 7,200 LOC across 33 test files + 2 helpers, 267 `@Test`/`@ParameterizedTest` |
| `BaseDialog` + `StandardDialog` | 1,114 + 161 = 1,275 LOC |

**Where the domain reach actually is.** `BaseDialog` supplies four accessors every
subclass inherits — `getMainFrame()`, `getScoreView()`, `requireScoreView()`,
`getSong()` — and that inheritance is why nothing currently forces the record boundary.
Removing the reach means removing them, not just not calling them.

Eleven classes reach past window-parenting into the model: `AttachmentDialog` and its
three subclasses (`AnnotationDialog`, `BeatChangeDialog`, `TempoChangeDialog`),
`KeyChangeDialog`, `ExportMidiDialog`, `ExportPDFDialog`, `PreferencesDialog`,
`ResolutionDialog`, `SongSettingsDialog`, and four `SongSettings*Tab` classes.

Seven take `MainFrame` for **window parenting only** and need no seam: `AboutDialog`,
`WhatsNewDialog`, `DoNotShowMessage`, `ProgressBarDialog`, `FontDialog`,
`ReportBugDialog`, `FontSettingRow`. The entire `fontchooser/` subpackage (17 files) has
zero domain reach.

`AboutDialog` and `MigrationWindow` extend `JDialog` deliberately and are documented as
such in `.agents/guides/dialogs.md`. Do not convert either.

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Design the Seam and Prove It on the AttachmentDialog Family](#-phase-1-design-the-seam-and-prove-it-on-the-attachmentdialog-family) | ✅ Complete | — |
| 2 | [Commit the Seam to BaseDialog and StandardDialog](#-phase-2-commit-the-seam-to-basedialog-and-standarddialog) | ✅ Complete | — |
| 3 | [SongSettingsDialog and Its Tabs](#-phase-3-songsettingsdialog-and-its-tabs) | ✅ Complete | — |
| 4 | [KeySignatureChangeDialog](#-phase-4-keysignaturechangedialog) | ⏸️ Blocked by external | — |
| 5 | [Export, Preferences and Resolution](#-phase-5-export-preferences-and-resolution) | ⏳ Pending | — |
| 6 | [Parent-Window-Only Dialogs](#-phase-6-parent-window-only-dialogs) | ⏳ Pending | — |
| 7 | [Back-End Contracts and Their Tests](#-phase-7-back-end-contracts-and-their-tests) | ⏸️ Blocked by 3, 5 | — |
| 8 | [Triage the Existing Test Suite](#-phase-8-triage-the-existing-test-suite) | ⏸️ Blocked by 7 | — |
| 9 | [E2E Wiring Set](#-phase-9-e2e-wiring-set) | ⏸️ Blocked by 8 | — |
| 10 | [Rewrite dialogs.md](#-phase-10-rewrite-dialogsmd) | ⏸️ Blocked by 9 | — |
| 11 | [Manual UI Verification](#-phase-11-manual-ui-verification) | ⏸️ Blocked by 10 | — |

---

## ✅ Phase 1: Design the Seam and Prove It on the AttachmentDialog Family

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/ui/dialog/ValidationResult.java, src/main/java/songscribe/ui/dialog/ValidationFailure.java, src/main/java/songscribe/ui/dialog/DialogBackEnd.java, src/main/java/songscribe/ui/dialog/AttachmentDialog.java, src/main/java/songscribe/ui/dialog/BeatChangeDialog.java, src/main/java/songscribe/ui/dialog/TempoChangeDialog.java, src/main/java/songscribe/ui/dialog/AnnotationDialog.java, src/main/java/songscribe/ui/dialog/AttachmentEditor.java, src/test/java/songscribe/ui/dialog/BeatChangeDialogTest.java  <br>
**Recommended model/effort:** Opus, high — this decides the shape every other phase applies; a wrong seam here is paid for by every class that follows.

**The prototype is the `AttachmentDialog` family, worked `BeatChangeDialog` first.**
`AttachmentDialog` (155 lines, extends `StandardDialog`) defines the template — abstract
`getExistingChange`, `populateControls`, `applyChange`, `clearChange`, `canClearChange`,
`opLabel`, `getElementField` — and `BeatChangeDialog` (121), `TempoChangeDialog` (106) and
`AnnotationDialog` (196) fill it in.

**All the domain reach is in the template, none of it in the leaves.** `AttachmentDialog.getData()`
calls `requireScoreView()` and resolves the selected element and active line from the score;
`setData()` resolves the element index and opens the modification bracket. The three
subclasses contain zero `requireScoreView()` and zero `getSong()`. That is why the family
is one unit: changing the template's shape breaks all three leaves at compile time, and
this phase must end green.

**Prove it on `BeatChangeDialog` first.** It is the self-contained leaf — two
`JComboBox<Duration>` and nothing else — where `TempoChangeDialog` delegates to a
`TempoSection` collaborator that would muddy the first pass. It also demonstrates the seam
paying for itself: `applyChange` guards `if (duration == null || beat == null) { return; }`,
a silent no-op on OK that exists only because `getSelectedItem()` is nullable. An input
record carrying non-null `Duration`s deletes the guard rather than relocating it.

**Not `KeyChangeDialog`,** which was the original prototype. It currently crashes
and is about to gain enablement rules, so its behavior is changing underneath. Writing a
contract against behavior that does not exist yet would produce a confident, plausible,
wrong contract — the worst outcome the rules name. It is Phase 4.

### Tasks

1. Read `.agents/guides/contracts.md`, `.agents/rules/java.md` (Javadoc contract syntax
   and the signature rules), and `.agents/guides/dialogs.md` (the current architecture —
   `BaseDialog` lifecycle, `Tab`, `DialogCategory`, the OK lifecycle
   `isValidData()` → `setData()` → `repaintScore()` → close).
2. Read `src/main/java/songscribe/ui/OptionDialogs.java` — it is how failures reach the
   user today and is the presentation side the seam separates out. Note that it lives in
   `songscribe.ui`, not `songscribe.ui.dialog`.
3. **Write the contracts for the three new types before implementing any of them.**
   Method Javadoc with preconditions, postconditions, `@throws`, boundary semantics,
   result invariants and side effects; `@return` on every non-void method without
   exception. Class Javadoc for invariants spanning methods.
   - `ValidationResult` — carries whether the input is acceptable and, when not, the
     failures. State explicitly whether a valid result may carry failures (it may not)
     and what an empty failure list means.
   - `ValidationFailure` — one failure. It must carry a `Strings` **key plus format
     arguments**, not resolved text: resolving in the back end would put a locale
     concern on the domain side of the seam, and `SongSettingsDialog` today needs both a
     title (`Strings.ALERT_TITLE_LINES_DO_NOT_FIT`) and a message
     (`Strings.ALERT_FONT_CHANGE_INVALID`). Read `.agents/guides/strings.md` before
     choosing the representation.
   - `DialogBackEnd<I>` — `ValidationResult validate(I input)` and `void apply(I input)`.
     A single interface is right: every dialog that takes a back end at all commits on OK
     and can fail validation. `PreferencesDialog` is the one dialog that does neither, and
     Phase 5 task 4 gives it no back end rather than an apply-only variant of this one.
4. **Resolve and record this design question, which the source documents leave open.**
   `contract-driven-testing.md` §5.2 states the free-function form as
   `apply(Song, Input)`. If the dialog calls that, the dialog needs the `Song`, which
   defeats the decoupling. The resolution to implement: the free function keeps the
   `apply(Song, Input)` shape, and the **caller that opens the dialog** binds the song,
   handing the dialog a `DialogBackEnd<I>` that already holds it. Both statements are
   then true and the dialog sees no domain type. Record this in the `DialogBackEnd`
   class Javadoc so the next reader does not re-derive it.
5. **Write the contract for each of the seven `AttachmentDialog` template methods before
   changing any of them.** They are the abstract contract three subclasses implement, so
   they are a genuine internal API, and none of them states a promise today.
6. **Move the template's domain reach to the back end.** `getData()`'s resolution of the
   selected element and active line from the score is real logic and belongs there; the
   dialog receives the resolved element in its input record. `setData()`'s element-index
   lookup and modification bracket go the same way — read `docs/mutations.md` first, and
   note that `Song.withBeatDefiningEditOn` is a domain entry point that stays on the
   domain side.
7. `getData()` also picks the Add-vs-Modify button label from whether a change already
   exists. That is a presentation decision driven by a domain fact: the fact goes in the
   input record, the label choice stays in the dialog.
8. Define the input record for `BeatChangeDialog` and the output record it gathers. Apply
   the signature rules: more than four components or two transposable same-typed
   components means decomposing further, and a mode-selecting boolean is an enum. Note
   that `duration` and `beat` are two adjacent `Duration`s a call site could transpose —
   that is the rule's own example, so they need a record rather than a parameter pair.
9. Rewrite `BeatChangeDialog` against the seam — it receives an input record and a
   `DialogBackEnd`, gathers widgets into the output record, and calls validate then apply.
   Verify by inspection that no `songscribe.dom` type, `Song`, `ScoreView` or
   `MainFrame`-derived model access remains in the class other than window parenting.
   Then apply the same shape to `TempoChangeDialog` and `AnnotationDialog`; their
   add-vs-update branching in `applyChange` moves to free functions with no Swing type in
   the signature, contract written before the body moves.
10. `AttachmentEditor` is a static entry point (`edit(MainFrame, Attachment, Line)`) and is
    the natural place for the caller-side binding task 4 describes. Update it to construct
    the bound `DialogBackEnd` rather than letting the dialog reach.
11. Update `BeatChangeDialogTest` (211 lines) to the new shape. Before writing each test
    method, check whether it will sit beside a sibling exercising the same method the same
    way with only the data differing — if so, both are rows in one `record` case table
    driven by `@ParameterizedTest`, from the first such case rather than after several
    accumulate. Leave `TempoChangeDialogTest`, `AnnotationDialogTest` and
    `AttachmentDialogTest` failing; Phase 8 triages them and repairing them here would
    pre-empt that triage.
12. Run `./scripts/compile.sh` (must print SUCCESS) and
    `./scripts/test.sh BeatChangeDialogTest` (must be green).
13. **Checkpoint — stop and present the seam to the user before any rollout.** Show the
    three types' contracts, `BeatChangeDialog`'s before/after, the template's new
    contracts, and the signature test applied to every extracted function. Do not start
    Phase 2 until the shape is agreed: the rest of the inventory is about to be rewritten
    against it.

---

## ✅ Phase 2: Commit the Seam to BaseDialog and StandardDialog

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/ui/dialog/CommitDialog.java, src/main/java/songscribe/ui/dialog/StandardDialog.java, src/main/java/songscribe/ui/dialog/BaseDialog.java, src/main/java/songscribe/ui/dialog/AttachmentDialog.java, src/main/java/songscribe/ui/dialog/BeatChangeDialog.java, src/main/java/songscribe/ui/dialog/TempoChangeDialog.java, src/main/java/songscribe/ui/dialog/AnnotationDialog.java, src/main/java/songscribe/ui/dialog/FontDialog.java, src/main/java/songscribe/ui/dialog/DoNotShowMessage.java, src/main/java/songscribe/ui/dialog/WhatsNewDialog.java, src/main/java/songscribe/ui/dialog/ReportBugDialog.java  <br>
**Recommended model/effort:** Opus, high — 1,275 lines with an inherited-accessor removal that every subclass depends on.

### What it did

**The lifecycle split in two classes rather than one.** `StandardDialog` keeps OK/Cancel and
gains one hook, `commitOnOk()` → whether the dialog may close, defaulting to committing nothing.
The new `CommitDialog<I>` owns the seam: `gather()` once, `validate(I)`, present, `commit(I)`,
with `commitOnOk()` final so no dialog can reorder the three or let them see different values.
A generic `StandardDialog<I>` was rejected because the two dialogs whose OK commits nothing
(`WhatsNewDialog`, `ReportBugDialog`) would have needed a placeholder type argument to say so.

**`isValidData()` is gone from both `StandardDialog` and `Tab`, and so is `Tab.setData()`.**
Leaving a commit hook nothing calls would have failed silently; removing it fails at compile time
instead, which is how `SongSettingsMusicTab` reaches Phase 3.

**`repaintScore()` is deleted, not relocated.** Every commit that writes the document does so in a
modification bracket, and the bracket's `SongDidChangeNotification` already re-lays out and
repaints. `StandardDialog`'s class contract states that, so a commit reaching the screen some
other way knows it must arrange the refresh itself.

**Phase 6's four `StandardDialog` subclasses were fixed here, not deferred.** They broke on the
lifecycle change rather than on domain reach — which Phase 6 removes none of — so leaving them
broken would have split one change across two phases. `FontDialog` (`CommitDialog<Font>`) and
`DoNotShowMessage` (`CommitDialog<Boolean>`) commit values and now say so; `WhatsNewDialog` and
`ReportBugDialog` lost empty `setData()` overrides. Phase 6 keeps its real task — the widened
field at `FontDialog.java:37` — and its class-by-class confirmation.

**`DoNotShowMessage` stopped bypassing `Prefs`.** It wrote its suppression flag with raw
`java.util.prefs.Preferences` under a hardcoded `"songscribe"` node, so `Prefs.resetAll()` could
not clear it and no `PrefsDidChangeNotification` was posted for it. Its constructor now takes a
`PrefsKey` rather than a `String propName`, reads through `Prefs.getBoolean` and writes through
`Prefs.put`. Its test was rewritten against the mocked `Prefs` and parameterized — it no longer
writes to the developer's real preference store to run.

**The class has no production caller and never has** (`git log -S "new DoNotShowMessage"` finds
only the test). Keeping it was a deliberate call: it is a facility for suppressible messages, and
its only caller being a test is a finding to revisit, not something this track settles.

### The work list Phase 2 handed on

`./scripts/compile.sh` ends on **38 errors, every one inside
`src/main/java/songscribe/ui/dialog/`**:

| Phase | Class | Errors | Lines |
|---|---|--:|---|
| 3 | `SongSettingsDialog` | 10 | 127, 129, 134, 135, 218, 220, 221, 236, 243, 251 |
| 3 | `SongSettingsMusicTab` | 6 | 154, 161, 163, 173, 176, 205 |
| 3 | `SongSettingsAttributionTab` | 5 | 108, 254, 434, 530, 547 |
| 3 | `SongSettingsTitleTab` | 4 | 378, 379, 418, 425 |
| 3 | `SongSettingsFontTab` | 1 | 177 |
| 4 | `KeyChangeDialog` | 3 | 71, 81, 89 |
| 5 | `ExportMidiDialog` | 4 | 65, 68, 84 (×2) |
| 5 | `ExportPDFDialog` | 2 | 39, 51 |
| 5 | `ResolutionDialog` | 2 | 95, 128 |
| 5 | `PreferencesDialog` | 1 | 172 |

The `AttachmentDialog` family does not appear, as Phase 2 task 3 required.

**The tree does not compile until Phases 3–5 land, so no test can run until then.**
`BeatChangeDialogTest` (re-pointed at the renamed `gather()`) and `DoNotShowMessageTest` (rewritten
against the mocked `Prefs`) are unverified rather than failing, and are the first two to run once
main compiles.

### Tasks

0. **Thread the gathered values through the OK lifecycle** (agreed at the Phase 1 checkpoint).
   Today OK runs `isValidData()` then `setData()` with no value passed between them, so a dialog
   using the seam has to gather twice — `AttachmentDialog` calls `gatherChange()` in each. Gather
   once and hand the same values to validation and to the commit, so the two cannot see different
   values and the second read cannot be forgotten. State in the contract that the values validated
   are the values applied; that is the promise the current shape cannot make.
1. Write the contract for the new `StandardDialog` validation hook **before**
   implementing it. Today `StandardDialog.isValidData()` iterates
   `getTabs()` calling `tab.isValidData()` and returns a bare boolean, and each tab
   presents its own errors. The new hook takes the `ValidationResult` from the back end
   and **`StandardDialog` performs the presentation** via `OptionDialogs`; tabs stop
   presenting entirely. State in the contract what happens with multiple failures, with
   an empty result, and where in the OK lifecycle
   (`isValidData()` → `setData()` → `repaintScore()` → close) the hook sits.
2. Implement the hook on `StandardDialog`.
3. **Remove the inherited domain reach from `BaseDialog`:** `getSong()`,
   `getScoreView()`, `requireScoreView()`. These are why nothing currently forces the
   record boundary — a dialog that can call `getSong()` will. `getMainFrame()` stays;
   window parenting is a legitimate need and is not domain reach.
   - Removing them breaks every remaining model-reaching class at compile time, which is
     the point: Phases 3–5 fix them and the compiler enumerates the work. The
     `AttachmentDialog` family is already clean — Phase 1 did it — so it must not appear
     in the failure list. If it does, Phase 1 left reach behind.
   - If a staged removal is needed to keep the tree compiling between phases, mark them
     `@Deprecated` with a Javadoc pointer to this plan in this phase and delete them in
     Phase 7. State which route you took in the commit message.
4. Update `BaseDialog.Tab`'s `isValidData` contract, or remove it, per the Phase 1
   design — a tab that no longer presents errors may not need the hook at all.
5. Do **not** change `BaseDialog`'s blocking-dialog counter, tab selection or geometry
   logic. Those are real logic but are not part of this seam; leave them for the contract
   pass that follows this track.
6. Run `./scripts/compile.sh`. It will fail in the classes Phases 3–5 own if you
   took the removal route — record the exact failing call sites in the commit message as
   the work list, and confirm no failure falls outside `src/main/java/songscribe/ui/dialog/`.

---

## ✅ Phase 3: SongSettingsDialog and Its Tabs

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/ui/dialog/SongSettingsDialog.java, src/main/java/songscribe/ui/dialog/SongSettingsMusicTab.java, src/main/java/songscribe/ui/dialog/SongSettingsTitleTab.java, src/main/java/songscribe/ui/dialog/SongSettingsAttributionTab.java, src/main/java/songscribe/ui/dialog/SongSettingsFontTab.java, src/main/java/songscribe/ui/dialog/SongSettingsDateInputRow.java  <br>
**Recommended model/effort:** Opus, high — the hardest case: 491 lines plus five collaborators, cross-tab validation ordering, and ten pieces of real logic to extract.

This is the dialog the whole policy was written against — 491 production lines against
1,346 test lines across three files plus a fixture.

### What it did

**`SongSettingsDialog` is a `CommitDialog<SongSettingsOutput>` holding no song and no score.**
`SongSettingsInput` in, `SongSettingsOutput` out, `SongSettingsBackEnd` between —
implemented by `backend/ScoreSongSettingsBackEnd` over a new `SongSettingsTarget` (four
methods; `ScoreView` already had all four and now implements it, which is what keeps
`JComponent` out of the package's signatures). `Actions.initialize` does the binding, so the
opener binds and the dialog never reaches.

**The back end reads as well as writes, which `AttachmentBackEnd` does not have to.**
`SongSettingsDialog` is reached through a cached `DialogOpenAction` and outlives every
document, so the input cannot be handed over at construction; `SongSettingsBackEnd.read()`
answers it on each opening. `.agents/guides/dialogs.md` now states both shapes.

**The extracted rules and where they went:**

| Was | Now |
|---|---|
| `SongSettingsDialog.lyricsFit(Song, Font, Font, double)` | `backend/SongSettingsRules.lyricsFit(Song, LyricsFontChange, double)` — the two adjacent `Font`s were the signature rule's own example |
| `canonicalKeySelectionFrom` / `applyMusicTabChanges` | `backend/SongSettingsRules`; the latter renamed `applyMusicSettings` and now takes a `Tempo`, so its four field comparisons became `Tempo.haveSameValue` |
| `validateLineWidthText(String, boolean)` + `pendingLineWidthSs(...)` + `SongSettingsMusicTab.validateLineWidth` / `...OrShowError` / `getPendingLineWidthSs` | `backend/LineWidthRules.validate(LineWidthEntry) → ValidationResult` and `resolveSs(Song, LineWidthEntry)` |
| `extractLyricsTitle(String, int)` | `SongMetadata.titleFromLyrics` — it derives a title, and `SongMetadata` is what owns titles |
| `gatedWordsDate(boolean, …)` | did **not** survive as a free function — see below |
| `SongSettingsAttributionTab.resolveLyricistText` | private on that tab; it is two lines used twice inside it |
| `SongSettingsDateInputRow.dayEnabled` | unchanged; it was already a static predicate with no reach |

**`gatedWordsDate` collapsed rather than moving.** Its four call arguments were four getters on
the Attribution tab, all of which the dialog was reaching through. With the widget reads
consolidated into `SongSettingsAttributionTab.getWordsDate()` — the tab's only words-date
surface, used by both the commit and the preview — the gate is a single `if` beside the
checkbox it is about, and its promise is that method's contract. Four getters left the tab's
surface with it.

**The `-1` sentinel is gone, and with it a double parse.** `validateLineWidthText` collapsed
"unparseable" and "out of range" into `-1`, so `validateLineWidthOrShowError` parsed a second
time to work out which message to show. `LineWidthRules.validate` answers a `ValidationResult`
that already carries the right failure, and the field's `InputVerifier` and the back end now
ask that one function. The `boolean isMetric` became `songscribe.util.LengthUnit`, which owns
the inch/cm conversion and its own label key.

**`CommitDialog.showFailure(ValidationFailure)`** is the single presentation path, so the
verifier's pre-OK report and OK's own report cannot differ. `LocalizedMessage` gained one
documented rule to make that work without resolving text in the back end: an argument may
itself be a `LocalizedMessage`, resolved by the presenter — which is how the range message
names `cm` or `inches`.

### Three findings acted on, each outside `ui/dialog`

1. **`BaseTitleComponent` handed the previews a whole `Song` for one number.**
   `titlePreview.setSong(song)` existed so `lineWidthPx()` could read
   `song.getLineWidthSs()` as a wrap constraint; the text always came from `setPreviewText`.
   `setPreviewText(String)` is now `setPreview(@Nullable Preview)`, where
   `Preview(String text, double wrapWidthSs)` supplies both — completing a preview seam whose
   class doc already claimed previews needed no song. The `song == null` early return in
   `render()` went with it; `textToRenderOrNull()` already answered that question, and it
   answers it after the background fill rather than before.
2. **The unofficial-translation checkbox was decided at construction and never revisited.**
   `createMusicSection()` added it only `if (!song.getTranslatedLyrics().isEmpty())`, in a
   dialog `DialogOpenAction` caches — so a song opened after a translation-less one never got
   the checkbox. It is now always built, inside a `translationRow` whose visibility
   `populate` sets. A live bug, fixed rather than reproduced.
3. **`extractLyricsTitle` was a title-derivation rule parked on a dialog.** Moved to
   `SongMetadata` beside `normalizeTitle`, and its contract written out — the melisma
   underscore, the single-versus-double hyphen, and the empty result. Phase 7 task 2 already
   lists that hyphen and melisma handling as a **domain** contract needing confirmation; it
   is written as a proposal and is not yet confirmed.

### The work list Phase 3 handed on

`./scripts/compile.sh` ends on **12 errors, all in Phases 4 and 5's classes** — exactly the
subset of Phase 2's list that Phase 3 did not own:

| Phase | Class | Errors | Lines |
|---|---|--:|---|
| 4 | `KeyChangeDialog` | 3 | 71, 81, 89 |
| 5 | `ExportMidiDialog` | 4 | 65, 68, 84 (×2) |
| 5 | `ExportPDFDialog` | 2 | 39, 51 |
| 5 | `ResolutionDialog` | 2 | 95, 128 |
| 5 | `PreferencesDialog` | 1 | 172 |

**Main was verified to compile clean.** The five classes above were temporarily scaffolded —
`getSong`/`getScoreView`/`requireScoreView` back on `BaseDialog`, `isValidData`/`setData` back
on `StandardDialog` — `./scripts/compile.sh` printed SUCCESS with NullAway and the dead-key
audit running over every new file, and the scaffold was then removed. Without it nothing after
the resolution errors runs, so "the tree does not compile" would otherwise have hidden whether
this phase's own code does.

**Test-tree breakage, for Phase 8** (`./scripts/compile.sh --test`, 45 errors, scaffold in
place). Phase 3 owns 39 of them:

| File | Errors | Cause |
|---|--:|---|
| `SongSettingsDialogTest` | 37 | the five statics it drives all moved or changed shape |
| `SongSettingsDialogFixture` | 1 | `new SongSettingsDialog(frame)` — the constructor takes a back end. Cascades to `SongSettingsDialogShowTest` |
| `SongSettingsDialogValidationTest` | 1 | `getLineWidthFieldForTest()`, deleted |
| `TempoChangeDialogTest`, `AnnotationDialogTest`, `AttachmentDialogTest`, `StandardDialogTest` | 6 | Phases 1 and 2 left these; unchanged here |

Nothing was repaired, per task 6.

### Tasks

1. **Fix the two named defects.**
   - `isValidData()` (`SongSettingsDialog.java`) computes `lyricsFit(...)` and then calls
     `OptionDialogs.showErrorMessage(contentPanel, …)` inline. Because it both decides
     and displays, nothing can call it without a live `contentPanel` — which is exactly
     why its tests mock the UI. Split the decision from the presentation: the decision
     returns a `ValidationResult`, and `StandardDialog` presents it.
   - `getLineWidthFieldForTest()` at `SongSettingsDialog.java:208` returns
     `musicTab.getLineWidthField()` and is production API that exists only for tests.
     Delete it. Do not replace it with anything; if a test needs the state it exposed,
     that state belongs in the input or output record.
2. **Extract each of these into a free function whose signature contains no Swing type,
   writing its contract before moving the body.** Every one is real logic today:
   - `lyricsFit(Song, Font, Font, double)` — whether every line still fits under a
     candidate lyrics font and line width. Already pure; it is the real contract worth
     testing and mostly needs its contract written.
   - `extractLyricsTitle(String, int)` — derives a title from the first N words, handling
     melisma underscores, hyphen word-breaks and capitalization.
   - `validateLineWidthText(String, boolean)` — parses and range-validates against
     `PageModel` min/max. The `boolean` selects a mode; replace it with an enum per the
     signature rules.
   - `pendingLineWidthSs(...)` — whether to return the loaded width or a freshly parsed
     one, to avoid quantization drift.
   - `canonicalKeySelectionFrom(Song)` — maps a stored key to its canonical combo entry
     (0 accidentals always canonicalizes to FLATS).
   - `gatedWordsDate(boolean, ...)` — whether a words-date contributes to commit and
     preview.
   - `applyMusicTabChanges(Song, ...)` — diffs tempo and key against current state and
     decides which notifications to post, coalesced into one bracket. Read
     `docs/mutations.md` before touching this; the one-bracket property is a stated
     invariant, not an implementation detail.
   - `SongSettingsMusicTab.validateLineWidth` / `validateLineWidthOrShowError` /
     `getPendingLineWidthSs` / `getKeyTypeAndCountFromCombo`. The
     `...OrShowError` name states the fusion outright — it does not survive the split.
   - `SongSettingsAttributionTab.resolveLyricistText` (falls back to composer when blank)
     and `buildPreviewLines` (assembles a `SongMetadata` from live uncommitted widget
     state).
   - `SongSettingsDateInputRow.dayEnabled(boolean, int)` — whether the day combo is
     enabled, derived from year validity and month selection.
3. Define the input and output records. `commitMetadata()` already builds a
   `SongMetadata` and hands it to the domain via `postWithModification` — that is the
   shape the seam wants and it already exists; extend it rather than inventing a parallel
   one. `commitFonts()` is the same move for `DocumentFonts`.
4. State the cross-tab validation ordering in the new contract. Today `isValidData()`
   relies on `super.isValidData()` running the Music tab's line-width validation first so
   the width read afterwards is always parseable. That ordering is a real precondition
   and must survive the split as a stated one, not as an accident of call order.
5. Remove every `dialog.getSong()`, `dialog.requireScoreView()` and
   `requireScoreView().getDocumentFonts()` call from all five tab classes. The tabs
   receive what they need in the input record.
6. Run `./scripts/compile.sh` (SUCCESS) and `./scripts/test.sh` for the whole unit suite.
   Existing `SongSettings*` tests will fail; that is expected and Phase 8 triages them.
   Record which fail and why in the commit message rather than repairing them here.

---

## ⏸️ Phase 4: KeySignatureChangeDialog

**Status:** Blocked by external  <br>
**BlockedBy:** the owner's crash fix and enablement rules  <br>
**Files:** src/main/java/songscribe/ui/dialog/KeySignatureChangeDialog.java, src/test/java/songscribe/ui/dialog/KeySignatureChangeDialogTest.java  <br>
**Recommended model/effort:** Opus, medium — 98 lines applying a decided shape, but against behavior that changed after this plan was written.

This was the original Phase 1 prototype. It is not, because the dialog **currently crashes
and is gaining rules about when it is enabled**. A contract written against behavior that
does not exist yet is the failure mode the rules name explicitly: confident, plausible and
wrong, with every test downstream derived from it.

### Tasks

1. **Do not start until the crash fix and the enablement rules have landed.** If they have
   not, say so and stop; do not infer the intended behavior from the broken code.
2. Read the enablement rules as they were actually implemented, and treat them as part of
   the contract this phase writes — when the dialog may open is a precondition, not a
   caller's concern.
3. Apply the Phase 1 shape. `getData()` calls `requireScoreView()`,
   `score.getSong().getLine(...)` and `score.getSong().indexOfLine(...)`; `setData()`
   calls `requireScoreView()` and `score.getSong().postWithModification(...)`. All of it
   moves behind the back end, with the caller binding the song.
4. Define `KeySignatureChangeInput` as a record carrying exactly what the dialog gathers.
   Apply the signature rules: more than four components or two transposable same-typed
   components means decomposing further, and a mode-selecting boolean is an enum.
5. Move the extracted validate/apply logic to a free function whose signature contains no
   Swing type, and write that function's contract before moving the body into it.
6. Update `KeyChangeDialogTest` (182 lines, 5 tests) to the new shape, adding the
   enablement rules' cases. Before writing each test method, check whether it will sit
   beside a sibling exercising the same method the same way with only the data differing —
   if so, both are rows in one `record` case table driven by `@ParameterizedTest`.
7. Run `./scripts/compile.sh` (SUCCESS) and
   `./scripts/test.sh KeySignatureChangeDialogTest` (green).

**Phase 7 does not wait on this phase.** Its external gate is outside the track's control,
and blocking the contract-and-test phase on it would stall Phases 8–11 behind work nobody
here owns. If this phase runs after Phase 7, it carries its own contracts and tests, and
its numbers are folded into the Phase 8 report rather than reported separately.

---

## ⏳ Phase 5: Export, Preferences and Resolution

**Status:** Pending  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/ui/dialog/ExportMidiDialog.java, src/main/java/songscribe/ui/dialog/ExportPDFDialog.java, src/main/java/songscribe/ui/dialog/PreferencesDialog.java, src/main/java/songscribe/ui/dialog/ResolutionDialog.java, src/main/java/songscribe/ui/dialog/PaperSizeStep.java, src/main/java/songscribe/ui/component/ScoreViewController.java, src/main/java/songscribe/layout/PageModel.java  <br>
**Recommended model/effort:** Sonnet, medium — four independent dialogs applying a decided shape; `ResolutionDialog` and `PaperSizeStep` carry the only non-trivial arithmetic, and `PreferencesDialog` takes no back end at all — it is decoupled through `PrefsDidChangeNotification`, which reaches outside `ui/dialog` into `ScoreViewController`.

### Tasks

1. `ExportMidiDialog.getData()` calls `requireScoreView()` and
   `PlaybackController.buildSequence(scoreView.getSong())`. Building the sequence is
   domain work that belongs on the back-end side; the dialog receives the result.
2. `ExportPDFDialog`'s constructor calls `requireScoreView()` to fill
   `PageLayoutData.scoreView`. Establish what the dialog actually needs from the score
   and pass that in the input record instead of the `ScoreView` itself.
3. `ResolutionDialog` (662 lines) — extract `handleResolutionChange`, the export
   dimension computation in `getData`, and `stateChanged`, each of which computes export
   pixel dimensions from a DPI scale and the border/lyrics/title exclusion checkboxes.
   Write each function's contract before moving its body. Its `getData()` also reads
   `song.getUnderLyrics()`, `getTranslatedLyrics()` and `getTitle()` — those become input
   record fields.
4. **`PreferencesDialog` (879 lines) gets no input record and no back end. Its seam is
   `PrefsDidChangeNotification`.** It is the only non-modal `BaseDialog`
   (`BaseDialog(…, false, EXCLUSIVE)`) — no OK, no Cancel, no `setData`, each edit applied
   the moment it is made. There is no input/modify/output cycle to put a record boundary
   across, so a record and a per-edit applier would be machinery modelling a cycle that
   does not exist. **The dialog reads and writes `Prefs`, and that is all it knows;** every
   other subsystem reacts to the notification `Prefs` posts on each write. `Prefs` is a
   global store in `songscribe.prefs`, not `Song`, `MainFrame` or the score, so this
   satisfies the track's rule as written. Read `.agents/guides/prefs.md` first.
   - **Delete `PreferencesDialog.syncPlaybackPrefs()` (`PreferencesDialog.java:171`) and
     its four call sites.** It is the class's only `getScoreView()` and the only thing
     standing between this dialog and zero domain reach. `getMainFrame()` then remains
     solely to parent the MIDI error dialog.
   - **The reacting half already exists but is short by four keys, and that is why the
     dialog calls the score directly.** `ScoreViewController.prefsDidChange` fires
     `syncPlaybackPrefs()` on `LOOP_PLAYBACK` and `PLAY_WITH_REPEATS`, and
     `updatePageLayout` on `PAGE_SIZE`. The keys this dialog writes that need a sync —
     `PLAY_INSERTED_NOTE`, `INSTRUMENT`, `PLAYBACK_NOTE_DURATION`,
     `TEMPO_CHANGE_PERCENT` — are absent from that condition. Widen it to the keys
     `ScoreView.syncPlaybackPrefs()` actually reads, then delete the dialog's copy.
   - **Fix the drift that gap came from.** The handler's `if` condition and
     `syncPlaybackPrefs()`'s reads are the same key set written twice with nothing
     connecting them; they have already diverged once. Derive the trigger set from one
     place so adding a key to the sync cannot silently skip the handler. Read
     `docs/messages.md` before changing the handler.
   - **Leave `AppearanceManager.switchTheme` as the appearance write.** It writes
     `PrefsKey.APPEARANCE` itself and rolls the write back when `applyTheme` fails, so it
     is the pref's owner rather than a bypass of `Prefs`. A bare `Prefs.put` plus a handler
     could not perform that rollback without re-entering the notification.
   - **Remove the duplicated page-size encoding.** The listener hardcodes
     `a4Radio.isSelected() ? "a4" : "letter"` while the read side goes through
     `PageModel.getSize()`, which parses the same two strings — one mapping written twice.
     Give `PageModel.Size` a `key()` and have the listener write `size.key()`.
     `Appearance.key()` already exists and is the pattern to match.
   - **The audition stays in the dialog.** Playing a note on instrument select and playing
     the scale are the dialog's own knowledge of its affordance, not domain work, and the
     mechanical test does not reach them. Both `PlaybackController.stop()` calls stay with
     it too — `tabWillShow`'s and `ScaleAction.play()`'s `stopAndAwaitSequencer()`.
   - Extract `programToIndex` (MIDI program number to combo index),
     `volumeToSliderIndex` (nearest-stop snap) and `buildScaleSequence(int program)` from
     the inline sequence construction in `ScaleAction.play()` (`PreferencesDialog.java`
     ~802–835: program change, tempo event, note-on/note-off pairs across `SCALE`). All
     three have no Swing type in the signature; write each contract before moving the
     body. This is independent of the seam question and matches what task 1 does with
     `PlaybackController.buildSequence`.
   - **State the no-revert semantics in the class contract.** Closing the window keeps
     every change and there is nothing to undo — a real promise, currently implied only by
     the absence of a Cancel button. State also that the dialog does not subscribe to
     `PrefsDidChangeNotification`: it writes prefs but does not observe them, so a
     `resetAll` from elsewhere while it is open leaves its widgets stale. That is true
     today and this task does not change it; it becomes a stated limitation rather than an
     unexamined one.
5. **Record, do not perform, the `PreferencesDialog` instrument-registry move.** The
   static `instrumentStrings` / `instrumentPrograms` / `instrumentsLoaded` cache,
   `ensureInstrumentsLoaded()`, `getInstrumentStrings()`, `getInstrumentPrograms()` and
   `resetInstrumentsForTesting()` are a MIDI service living on a dialog —
   `ExportMidiDialog.java:47,59,73` calls three of them, one dialog using another as a
   library, and `resetInstrumentsForTesting()` is production surface that exists only for
   `PreferencesDialogTest:55,62`.
   **This move is already owned and already designed elsewhere:**
   `plans/singleton-lifecycle-contracts.md` §6 specifies `MidiController` exposing
   `List<Instrument> getInstruments()` over a `record Instrument(String name, int
   program)`, populated when the synthesizer opens and cleared when MIDI closes, and
   `plans/test-only-surface.md:481` assigns `resetInstrumentsForTesting` to the
   `ui/playback`/`MidiController` phase. Do **not** implement another track's design here.
   Confirm both entries still name it, note in the commit message that
   `PreferencesDialog` finishes this track still holding the registry, and leave it.
   The seam does not depend on the move: the statics are the dialog's own, not reach
   inherited from `BaseDialog`, so they do not fail the mechanical test.
6. `PaperSizeStep` (390 lines, extends `Step`, not a dialog) — extract the unit
   conversion (mm/in/px) in `setValues`/`getValueInPixels`/`end` and the
   loaded-page-size-against-templates matching. Read `docs/unit-conversion.md` first;
   unit conversion has stated project-wide rules that override any local convention, and
   a conversion written against a local convention is a defect even when it computes the
   right number.
7. Run `./scripts/compile.sh` (SUCCESS). Record failing tests without repairing them.

---

## ⏳ Phase 6: Parent-Window-Only Dialogs

**Status:** Pending  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/ui/dialog/FontDialog.java, src/main/java/songscribe/ui/dialog/AboutDialog.java, src/main/java/songscribe/ui/dialog/WhatsNewDialog.java, src/main/java/songscribe/ui/dialog/DoNotShowMessage.java, src/main/java/songscribe/ui/dialog/ProgressBarDialog.java, src/main/java/songscribe/ui/dialog/ReportBugDialog.java, src/main/java/songscribe/ui/dialog/FontSettingRow.java  <br>
**Recommended model/effort:** Sonnet, low — these need no seam; the phase exists to confirm that and to kill one known defect.

These seven take `MainFrame` for window parenting only and have zero `getSong()` /
`getScoreView()` / `songscribe.dom` reach. They need no input record and no back end.

### Tasks

1. **Delete the widened field at `FontDialog.java:37`**, commented "Widened to
   package-private for testing (FontDialogTest accesses it directly)". It is the exact
   corollary the no-test-only-surface rule bans, and it is named in
   `plans/test-only-surface.md`. If `FontDialogTest` needs the state, it comes from the
   dialog's public API or the test goes.
2. Confirm by inspection, class by class, that each of the seven still has zero domain reach
   against the Phase 2 `BaseDialog` with `getSong()`/`getScoreView()`/
   `requireScoreView()` gone. Report any that do not — that would mean domain reach the
   inventory missed, and it belongs in Phase 3, 4 or 5 rather than being patched here.
   - **Four of them already changed in Phase 2**, on the lifecycle rather than on domain reach:
     `FontDialog` is now `CommitDialog<Font>`, `DoNotShowMessage` is `CommitDialog<Boolean>`, and
     `WhatsNewDialog` and `ReportBugDialog` lost empty `setData()` overrides. Confirm those; do
     not redo them.
   - `DoNotShowMessage`'s `Prefs` bypass was **fixed in Phase 2**, not deferred: its constructor
     now takes a `PrefsKey` instead of a `String propName`, and it reads through
     `Prefs.getBoolean` and writes through `Prefs.put`. Nothing to do here.
3. Do **not** convert `AboutDialog` to `BaseDialog`. It extends `JDialog` deliberately —
   undecorated so it can show the borderless splash pane, non-modal so it can see the
   outside click that dismisses it, and unfocusable because a borderless macOS window may
   never become key. Its class doc explains this; `.agents/guides/dialogs.md` repeats it.
4. `FontSettingRow.defaultFont` lazy-caches `DocumentFonts.defaultFonts()`. Leave it;
   it is a caching decision, not domain reach.
5. Run `./scripts/compile.sh` (SUCCESS).

---

## ⏸️ Phase 7: Back-End Contracts and Their Tests

**Status:** Blocked by 3, 5  <br>
**BlockedBy:** 3, 4, 5  <br>
**Files:** src/test/java/songscribe/ui/dialog/backend/  <br>
**Recommended model/effort:** Opus, high — deciding what each extracted function promises is contract judgment, and several are music-notation judgments that must be confirmed rather than decided.

Phases 1 and 3–5 each wrote a contract before moving a body. This phase derives the tests
from those contracts and closes what the extraction left open. Phase 1's own contracts —
the seven `AttachmentDialog` template methods and the functions extracted from the three
subclasses — are in scope here alongside the rest; the checkpoint agreed their shape, not
their tests.

### Tasks

1. Re-read every contract Phases 1 and 3–5 wrote and check each against the rule that **the
   tell of a real contract is that the implementation could in principle violate it.** A
   contract that merely restates the body it was extracted from describes the code and
   promises nothing; rewrite it as what the domain requires.
2. **Classify each contract as mechanical or domain** per
   `.agents/skills/contract-pass/reference/classification.md`. Unit conversion, parsing,
   range validation, index mapping and template matching are mechanical. `lyricsFit`,
   `canonicalKeySelectionFrom` (0 accidentals canonicalizing to FLATS is a
   music-notation judgment), `extractLyricsTitle`'s melisma and hyphen handling, and
   `applyMusicTabChanges`'s notification decisions are domain. **Batch the domain ones
   into one checkpoint and present them for confirmation before writing tests against
   them** — a confident, plausible, wrong contract is worse than none, because every
   test downstream is then derived from it.
3. Write the testing-approach Javadoc on each new test class, stating which equivalence
   classes, boundaries and invariants it exercises — as the contract's clauses, not as a
   list of inputs.
4. Derive the cases from the contracts. Before writing each test method, check whether it
   will sit beside a sibling exercising the same function the same way with only the data
   differing; if so both are rows in one `record` case table from the first such case.
   A varying lambda does not disqualify a case — only a varying assertion does.
5. Where a test's domain is finite and small — an enum, a closed set of states — do not
   write "enumerated in full" in the Javadoc unless something fails when the domain
   grows. Drive the cases from `@EnumSource` / `values()` / a sealed hierarchy's
   permitted subclasses, or assert separately that the table's rows are exactly the
   domain.
6. Nothing to do: Phase 2 deleted `BaseDialog.getSong()`, `getScoreView()` and
   `requireScoreView()` outright rather than deprecating them.
7. Run `./scripts/compile.sh` (SUCCESS) and `./scripts/test.sh` (green).

---

## ⏸️ Phase 8: Triage the Existing Test Suite

**Status:** Blocked by 7  <br>
**BlockedBy:** 7  <br>
**Files:** src/test/java/songscribe/ui/dialog/  <br>
**Recommended model/effort:** Sonnet, high — 33 files and 267 test methods against contracts that now exist; mechanical per test, large in volume.

### Tasks

1. Triage every test against the contracts, one of three outcomes: **keep** (asserts a
   contract case, right level, can fail), **rewrite** (real case, wrong test), or
   **discard** (maps to no contract case). A test mapping to no clause is discarded, not
   kept on the theory it might catch something.
2. Under D2 a dialog's own three steps — gather, validate, apply — are **wiring,
   classified none**. A test asserting that a widget value reached a record field, or
   that clicking OK called `setData`, is testing wiring and is discarded rather than
   rewritten. Phase 9 covers those paths once each.
3. The largest files, so the volume is visible up front: `SongSettingsDialogTest`
   (889 lines, 45 tests), `BaseDialogTabsTest` (699/23), `AttachmentDialogTest`
   (449/14), `BaseDialogCounterTest` (419/17), `BaseDialogPositionTest` (349/12),
   `PaperSizeStepTest` (345/10), `PlatformFileDialogTest` (325/15),
   `StandardDialogTest` (320/10), `AnnotationDialogTest` (320/15),
   `SongSettingsDialogValidationTest` (315/7).
4. **`SongSettingsDialogTest` is the likeliest survivor and should be triaged first.**
   Its own class doc records that it deliberately extends `UnitTest` rather than
   `MainFrameMockTest` — it already tests pure logic rather than the UI, so most of its
   45 tests should re-point at the extracted free functions rather than being deleted.
   If that turns out to be wrong, say so; it changes the phase's shape.
5. `BaseDialogCounterTest`, `BaseDialogPositionTest` and `BaseDialogTabsTest` (52 tests
   between them) cover the blocking counter, geometry and tab selection — real logic
   Phase 2 deliberately did not touch. They are **kept**; they are not front-end wiring
   tests and this track does not contract that logic.
6. The `fontchooser/` tests (7 files, 27 tests) have no domain reach and are untouched by
   this track. Leave them.
7. `PreferencesDialogTest` tests `programToIndex`, `ensureInstrumentsLoaded` and
   `volumeToSliderIndex` — all static, none of them front-end wiring. The two extracted
   in Phase 5 re-point at the free functions and are **kept**. The
   `ensureInstrumentsLoaded` tests and the `resetInstrumentsForTesting` calls at lines 55
   and 62 stay as they are: Phase 5 task 5 deliberately leaves that registry on the
   dialog, and it moves under the `ui/playback` track.
8. `ScoreViewControllerTest.PrefsDidChange` already covers the handler Phase 5 task 4
   widens. Add the four new keys as cases **driven from the derived trigger set**, not
   from a hand-written list — the drift that gap came from is exactly a hand-written list
   that nothing checked, and a second one in the test would reproduce it.
9. `StandardDialogTest`'s ten tests all drive `isValidData()`/`setData()` and the tab iteration
   behind them — hooks Phase 2 removed. Triage them against `commitOnOk()` and `CommitDialog`'s
   contracts rather than translating them one for one; most assert wiring and are discards.
10. Delete `SongSettingsDialogFixture.java` and `BaseDialogTestHelper.java` if nothing
    surviving uses them; a helper kept for deleted tests is dead code.
11. Report the four counts — kept, rewritten, discarded, added — and the test LOC before
    and after. These are the numbers `plans/pilot-retrospective.md` §3 says the pilot
    could not produce, and they are what D10 is re-decided against.
12. Run `./scripts/compile.sh` (SUCCESS) and `./scripts/test.sh` (green).

---

## ⏸️ Phase 9: E2E Wiring Set

**Status:** Blocked by 8  <br>
**BlockedBy:** 8  <br>
**Files:** src/test/java/songscribe/e2e/DialogsTest.java  <br>
**Recommended model/effort:** Sonnet, medium — mechanical against existing infrastructure, but each test drives a real GUI and needs care.

E2E infrastructure already exists — `src/test/java/songscribe/e2e/` holds 14 files, 11 of
which use AssertJ-Swing, including `DialogsTest.java` and `E2ETest.java`. Extend it; do
not build a parallel harness.

### Tasks

1. Read `.agents/guides/testing-e2e.md` first — runner flags, helpers, and coordinate and
   layout synchronization.
2. Add one test per dialog path: open → edit → OK → the model changed. **One test per
   path, never per case.** E2E proves the wiring; the contract's cases are already
   exercised at unit level by Phase 7. A second e2e test for a second input value is the
   error this phase must not make.
3. Per D2, this set is run when a dialog is created or gains a feature — not on every
   change. Say so in the class Javadoc so a later reader does not fold it into the
   routine suite.
4. E2E tests drive a real GUI and are slow. **They require explicit user approval before
   running and there is no form of `./scripts/test.sh` that runs both suites.** Ask
   before running `./scripts/test.sh e2e DialogsTest`; do not run it unprompted.

---

## ⏸️ Phase 10: Rewrite dialogs.md

**Status:** Blocked by 9  <br>
**BlockedBy:** 9  <br>
**Files:** .claude/guides/dialogs.md  <br>
**Recommended model/effort:** Sonnet, medium — writing down a design that now exists rather than deciding one.

Written **last, from what the design turned out to be** — not from what this plan
predicted it would be.

**Phase 2 already corrected the sections it made false** — the `BaseDialog` accessor list, the
`StandardDialog` OK lifecycle, the `Tab` lifecycle list — and added a `CommitDialog<I>` section,
rather than leaving the guide lying for eight phases. Read what is there before rewriting; tasks
3–5 below are checks now, not blank-page work.

### Tasks

1. Add the seam rule as the guide's governing statement:
   > `StandardDialog`'s lifecycle moves values across a record boundary and makes no
   > decisions. Validation and application are free functions whose signatures contain
   > **no Swing types**: `validate(Input) → ValidationResult` and `apply(Song, Input)`.
   > The dialog gathers widgets into `Input`, calls `validate`, presents whatever
   > failures come back, and calls `apply`.

   Keep the no-Swing-types-in-the-signature test prominent — it is mechanical and a
   reviewer can apply it without judgment.
2. **Document the non-modal exception beside it**, so the next reader does not force the
   record boundary onto a live editor. A dialog with no OK has no input/modify/output
   cycle; it reads and writes `Prefs` directly and is decoupled by the
   `PrefsDidChangeNotification` its writes post. It takes no input record and no back end.
   `PreferencesDialog` is the sole example — say so, and say that a second one would need
   this decision revisited rather than copied. State the rule that falls out of it:
   closing keeps every change, because each was already applied.
3. Update the **BaseDialog API surface** section. It currently lists `getScore()`,
   `requireScore()` and `getSong()` as accessors; those are gone and the section is
   actively misleading until it is corrected.
4. Update the **StandardDialog** section, whose OK lifecycle
   (`isValidData()` → `setData()` → `repaintScore()` → close) and override hooks changed
   in Phase 2.
5. Update the **Tab** section's lifecycle list if Phase 2 changed or removed
   `Tab.isValidData`.
6. Keep unchanged, because this track did not touch them: `DialogCategory` and the
   blocking-counter rules, the deliberate non-`BaseDialog` windows, `TitledSection`,
   tabbed-dialog construction via `createTabbedContent()`, and `showTab`.
7. This file is a **guide** — it states conventions, not promises. Anything that turned
   out to be a system invariant spanning subsystems belongs in `docs/` instead. Check
   each addition against that line before writing it.

---

## ⏸️ Phase 11: Manual UI Verification

**Status:** Blocked by 10  <br>
**BlockedBy:** 10  <br>
**Files:** —  <br>
**Recommended model/effort:** Sonnet, low — the agent prepares and reports; the user drives the app.

Unit and e2e tests do not catch a dialog that opens at the wrong size, focuses the wrong
field, or shows a validation message that reads wrong. Eleven dialogs were rewritten.

### Tasks

1. Ask the user for permission before running the app; `./scripts/run.sh` must never be
   executed without it.
2. Give the user a checklist covering, for each rewritten dialog: it opens, geometry and
   tab selection are as before, OK commits, Cancel discards, and a deliberately invalid
   input produces the right message.
3. Include the cases the seam most likely broke: `SongSettingsDialog`'s cross-tab
   lyrics-fit failure (the message that used to come from `isValidData` now comes from
   `StandardDialog`), the Add-vs-Modify button label on each of the three attachment
   dialogs, and `SongSettingsDialog.show(Section)` opening the right tab with the right
   field focused.
4. `PreferencesDialog` needs its own items. Its live side effects now travel through
   `PrefsDidChangeNotification` instead of a direct call, and nothing in the unit or e2e
   suite watches one land: switching appearance retints the app immediately, changing page
   size or units takes effect without reopening, the three Play sliders still snap and
   change playback, selecting an instrument auditions a note and changes what the score
   plays back with, the Scale button toggles and restarts on a new selection, and leaving
   the Instruments tab stops the scale. **Play the score after changing instrument, tempo
   and note duration** — those are the four keys the widened handler now carries, and a
   handler that misses one fails silently.
5. Record the result. Anything that fails is a defect in this track, not a new finding.

---

## Verification (whole plan)

1. `./scripts/compile.sh` prints SUCCESS.
2. `./scripts/test.sh` is green, with the unit-suite total reported before and after.
3. `./scripts/test.sh e2e DialogsTest` is green — **user approval required first**.
4. No signature in any extracted back-end function names a Swing type. This is the
   mechanical acceptance test for the whole track.
5. `BaseDialog` no longer exposes `getSong()`, `getScoreView()` or `requireScoreView()`.
6. `SongSettingsDialog.getLineWidthFieldForTest()` and the widened field at
   `FontDialog.java:37` are gone, resolving `plans/test-only-surface.md`'s two
   dialog-decoupling entries. Its third `ui/dialog` entry,
   `PreferencesDialog.resetInstrumentsForTesting` (line 481), is **not** resolved here —
   it is assigned to the `ui/playback`/`MidiController` phase and Phase 5 task 5 leaves it
   deliberately. Report it as outstanding rather than as done.
7. `PreferencesDialog` contains no `getScoreView()` call and `syncPlaybackPrefs()` is gone
   from it. It still uses `Prefs` and `PrefsKey` by design — that is its seam, not a
   violation of item 5.
8. Report for D10: main and test LOC before and after, test methods before and after, and
   the four triage counts. Per `plans/pilot-retrospective.md`, these measure what
   **architectural correction** buys, not what a contract pass buys — the `engraving`
   contract pass measures the latter. Do not present either number as the other.
