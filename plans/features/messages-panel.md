# Messages Panel and Window Menu (issue #842)

The change report moves from a modal dialog into a Messages panel in the main window. The
main window's content becomes a vertical `JSplitPane`: the score is on top and the Messages
panel is below. The panel always shows the report of the most recent change to the song. A
Window menu is added at the end of the menu bar.

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Report Type and Delivery](#-phase-1-report-type-and-delivery) | ✅ Complete | — |
| 2 | [Report Call Sites and Strings](#-phase-2-report-call-sites-and-strings) | ✅ Complete | — |
| 3 | [Messages Panel and Window Actions](#-phase-3-messages-panel-and-window-actions) | ✅ Complete | — |
| 4 | [Window Menu Wiring](#-phase-4-window-menu-wiring) | ✅ Complete | — |
| 5 | [Design Docs and Guides](#-phase-5-design-docs-and-guides) | ✅ Complete | — |
| 6 | [Dialog Category Removal](#-phase-6-dialog-category-removal) | ✅ Complete | — |
| 7 | [Tests](#-phase-7-tests) | ✅ Complete | — |
| 8 | [Compile and Test Gate](#-phase-8-compile-and-test-gate) | ✅ Complete | — |
| 9 | [Manual UI Verification](#-phase-9-manual-ui-verification) | ⏳ Pending | — |

## ✅ Phase 1: Report Type and Delivery

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/ChangeReport.java, src/main/java/songscribe/dom/ModificationSession.java, src/main/java/songscribe/dom/Song.java, src/main/java/songscribe/message/notification/SongDidChangeNotification.java, src/main/java/songscribe/message/notification/DocumentDidLoadNotification.java, src/main/java/songscribe/message/notification/ChangesWereReportedNotification.java, src/main/java/songscribe/undo/UndoController.java, src/main/java/songscribe/undo/MutationReplayer.java, src/main/java/songscribe/ui/component/ScoreView.java  <br>
**Recommended model/effort:** Opus, medium — new type split that carries an invariant, and contract text for the edit delivery, the replay and the load notification

Read `docs/change-report.md`, `docs/mutations.md`, `docs/undo.md` and `.claude/guides/contracts.md` first. Use the Serena `jet_brains_*` tools for Java lookups, as `.claude/rules/serena.md` says.

### Tasks

1. **Split `ChangeReport` into a collector and an immutable report** (`src/main/java/songscribe/dom/ChangeReport.java`).
   - `ChangeReport` becomes `public record ChangeReport(List<ReportedChange> changes)`. Its compact constructor stores `List.copyOf(changes)`. Keep `isEmpty()` and `byHeading()` with their present contracts. Add `public static final ChangeReport EMPTY`.
   - Remove `implements Copyable<ChangeReport>` and `copy()`. An immutable record needs no copy.
   - Add the nested `public static final class Builder` with `record(ReportedChange)`, `isEmpty()` and `build()`. `build()` returns a `ChangeReport` that later `record` calls do not change. Add a `Builder(ChangeReport seed)` constructor that starts from the entries of `seed`.
   - Write the class Javadoc so that it states the invariant: a `ChangeReport` never changes after `build()`, so every holder (the notification, the undo step, the panel) can keep it. State that only a `Builder` records.
2. **Write the new contract of `ModificationSession.deliver`** (`ModificationSession.java:317`), before you change the code. When the edit's batch is not empty, it marks the song modified and posts one `SongDidChangeNotification` that carries the batch and the built report. The report can be empty. It posts nothing when the batch is empty. State in the contract that every reporter records a change in the same batch as a mutation, so a non-empty report never comes with an empty batch.
3. **Change `ModificationSession`** to agree with that contract:
   - `OpenEdit.report` becomes a `ChangeReport.Builder`.
   - `deliver` builds the report and passes it to the `SongDidChangeNotification` constructor. It no longer posts `ChangesWereReportedNotification`.
   - `report(ReportedChange)` records into the builder, as now.
   - `replay(Runnable)` becomes `replay(ChangeReport report, Runnable body)`. The outermost bracket that the replay opens starts its `OpenEdit` with `new ChangeReport.Builder(report)`. Thus the replay's `SongDidChangeNotification` carries `report`. Give `beginModification` the seed it needs for this. A non-replay bracket starts with an empty builder.
   - Update the class comment and the field comments that name `ChangesWereReportedNotification`.
4. **Change `Song.replay`** (`Song.java`) to `replay(ChangeReport report, Runnable body)`, which delegates to `ModificationSession.replay`. Rewrite its `@effects`: the posted `SongDidChangeNotification` carries `report`, and no modification rule runs.
5. **Add the report to `SongDidChangeNotification`.** Add a `ChangeReport report` constructor parameter and `getReport()`. Javadoc: the changes the song made on its own during this edit; empty when it made none, and for an undo; for a redo, the report of the edit that the redo applies again. Update both constructors (`SongDidChangeNotification[0]` and `[1]`).
6. **Delete `ChangesWereReportedNotification.java`.**
7. **Carry the load report on `DocumentDidLoadNotification`.** Replace the `LoadChange loadChange` field and constructor parameter with `ChangeReport report`. Add `getReport()`. Keep `getLoadChange()`, which now returns `LoadChange.of(report)`. Update the class Javadoc: it is posted for File > Open, File > New and Revert to Saved, and the report is empty for File > New.
8. **Change `ScoreView.setSong`** to `setSong(Song song, ChangeReport report)`. It posts `new DocumentDidLoadNotification(song, report)`. Update its `@param`.
9. **Store the report in the undo step** (`UndoController.java:132`). `UndoStep` becomes `UndoStep(List<Mutation> mutations, ChangeReport report, @Nullable String opName)`. `songDidChange` stores `message.getReport()`. `BASELINE` uses `ChangeReport.EMPTY`.
10. **Pass the report through the replay.** `StepReplay.replay` and `MutationReplayer.undoBatch` / `redoBatch` take a `ChangeReport` and pass it to `Song.replay`. `performUndo` passes `ChangeReport.EMPTY`. `performRedo` passes `step.report()`. Update the Javadoc of `undo()` and `redo()`: undo posts an empty report, and redo posts the report of the edit it applies again.

## ✅ Phase 2: Report Call Sites and Strings

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/dom/LoadPasses.java, src/main/java/songscribe/dom/TupletLoadPass.java, src/main/java/songscribe/dom/SpanLoadPass.java, src/main/java/songscribe/dom/BreathMarkLoadPass.java, src/main/java/songscribe/dom/ParsedSong.java, src/main/java/songscribe/dom/ModificationRules.java, src/main/java/songscribe/dom/ModificationRule.java, src/main/java/songscribe/dom/TieRule.java, src/main/java/songscribe/dom/EndingSectionRule.java, src/main/java/songscribe/dom/, src/main/java/songscribe/io/SongLoadResult.java, src/main/java/songscribe/ui/component/MainFrame.java, src/main/java/songscribe/ui/component/ScoreView.java, src/main/java/songscribe/ui/component/ScoreViewController.java, src/main/java/songscribe/ui/dialog/ReportDialog.java, src/main/java/songscribe/ui/dialog/ReportDialogController.java, src/main/java/songscribe/ui/, src/main/resources/songscribe/strings.properties, src/test/java/songscribe/dom/BatchReplay.java, src/test/java/songscribe/ui/clipboard/PasteSpanReconciliationTest.java, src/test/java/songscribe/dom/TupletLoadPassTest.java, src/test/java/songscribe/dom/HairpinLoadPassTest.java, src/test/java/songscribe/e2e/E2ETest.java  <br>
**Recommended model/effort:** Sonnet, low — call-site and signature updates whose shape Phase 1 decided, deletions, and a key rename

Use the Serena `jet_brains_*` tools for Java lookups, as `.claude/rules/serena.md` says. Phase 1 made `ChangeReport` an immutable record with a nested `ChangeReport.Builder` (`record`, `isEmpty`, `build`), put the report on `SongDidChangeNotification.getReport()`, deleted `ChangesWereReportedNotification`, and changed `ScoreView.setSong` to `setSong(Song, ChangeReport)`.

### Tasks

1. **Collectors take a builder.** Change every parameter that records into a report from `ChangeReport` to `ChangeReport.Builder`: `LoadPasses.LoadRun.report`, `LoadPasses.PendingPass.run`, `TupletLoadPass.run` / `placeTuplets` / `place`, `SpanLoadPass.run` / `place`, `BreathMarkLoadPass.run` / `removeUnhostedBreathMarks`, `ModificationRule.apply`, `ModificationRules.apply`, `TieRule.apply` / `judge`, `EndingSectionRule.apply`. `LoadPasses.run` creates a `ChangeReport.Builder` and returns `build()`. `ParsedSong.settle()` and `SongLoadResult.Success.report` keep the type `ChangeReport`.
2. **Load call sites.** In `ScoreView.openFile` (`ScoreView.java:461`), call `setSong(success.song(), report)`. Delete the block that shows `ReportDialogController` (lines 466–471) and the comment above `setSong` about the undo engine, which `DocumentDidLoadNotification` now covers. In `MainFrame.handleNewFile` (`MainFrame.java:895`), call `scoreView.setSong(new Song(), ChangeReport.EMPTY)`.
3. **Delete the dialog.** Delete `ui/dialog/ReportDialog.java` and `ui/dialog/ReportDialogController.java`. Delete `ScoreViewController.changesWereReported` (`ScoreViewController.java:380–392`) and its imports of `ReportDialog`, `ReportDialogController` and `ChangesWereReportedNotification`.
4. **Rename the report string keys.** `.claude/guides/strings.md` reserves `dialog.*` for `BaseDialog` windows, and no dialog shows the report now. In `src/main/resources/songscribe/strings.properties`, rename every `dialog.report.*` key to `report.*`. Move the group to its sorted position in the file. Then rename every Java constant `Strings.DIALOG_REPORT_*` to `Strings.REPORT_*` under `src/`. Use Serena `replace_in_files` with `dry_run: true` first, then apply.
5. **Remove the dead title keys.** Delete `dialog.report.edit.title` and `dialog.report.load.title` (after task 4, `report.edit.title` and `report.load.title`). Nothing reads them after task 3.
6. **Tests that read the report.** `BatchReplay` and `PasteSpanReconciliationTest` subscribe to `ChangesWereReportedNotification`. Change them to read `SongDidChangeNotification.getReport()`. In `TupletLoadPassTest.settle` and `HairpinLoadPassTest.settle`, create a `ChangeReport.Builder` where they create a `ChangeReport`, and return `build()` where they return the report. In `E2ETest.resetSong` (`E2ETest.java:186`), pass `ChangeReport.EMPTY`. In `E2ETest.loadFixture` (`E2ETest.java:854`), pass `result.report()`. Do not change `ChangeReportDeliveryTest`; Phase 7 rewrites it.

## ✅ Phase 3: Messages Panel and Window Actions

**Status:** Complete  <br>
**BlockedBy:** 1, 2  <br>
**Files:** src/main/java/songscribe/ui/component/ReportPanel.java, src/main/java/songscribe/ui/component/MainFrame.java, src/main/java/songscribe/ui/action/MinimizeWindowAction.java, src/main/java/songscribe/ui/action/ZoomWindowAction.java, src/main/java/songscribe/ui/action/FocusScoreAction.java, src/main/java/songscribe/ui/action/ToggleMessagesAction.java, src/main/java/songscribe/prefs/PrefsKey.java, src/main/resources/conf/user-defaults.json  <br>
**Recommended model/effort:** Opus, medium — new panel with its own state, the split-pane visibility and persistence, focus tracking, and the toggle behavior of the Window actions

Read `.claude/guides/prefs.md`, `.claude/guides/messages.md`, `.claude/guides/strings.md` and `docs/lifecycle.md` first. Use the Serena `jet_brains_*` tools for Java lookups.

Phase 2 deleted `ReportDialog` and `ReportDialogController`. These facts from Phase 1 apply: `SongDidChangeNotification.getReport()` gives the report of every edit, and it is empty when the edit made no change on its own and for an undo. `DocumentDidLoadNotification.getReport()` gives the report of a load, and it is empty for File > New. `ChangeReport` is an immutable record with `isEmpty()` and `byHeading()`. After Phase 2 the string keys of the report start with `report.` and the constants with `Strings.REPORT_`.

### Tasks

1. **Create `ReportPanel`** (`src/main/java/songscribe/ui/component/ReportPanel.java`), a `JPanel` that shows the latest report.
   - Move the tree from the deleted `ReportDialog` into it: the `JTree` with the hidden root, `SeparatorTreeUI`, `CellRenderer`, `leafText`, `location`, and the population from `ChangeReport.byHeading()` with every group expanded. Get the deleted code with `git show HEAD:src/main/java/songscribe/ui/dialog/ReportDialog.java`. Use the `Strings.REPORT_*` constants.
   - Move the `Occasion` enum (`EDIT`, `LOAD`) into `ReportPanel` with the message key only. The title keys no longer exist.
   - Above the tree, a label shows the occasion's sentence (`Strings.REPORT_EDIT_MESSAGE` or `Strings.REPORT_LOAD_MESSAGE`). When the report is empty, the tree is empty and the label shows `Strings.REPORT_NONE` (Phase 4 adds the key with the text "No changes.").
   - The panel lives for the process. It subscribes with `MessageSubscription.addProcessListener(this)`, as `MenuController` does, and it is not `Disposable`.
   - `@Handler songDidChange(SongDidChangeNotification)`: show `message.getReport()` with `Occasion.EDIT`. An empty report clears the list.
   - `@Handler documentDidLoad(DocumentDidLoadNotification)`: show `message.getReport()` with `Occasion.LOAD`. When the report is not empty, ask `MainFrame` to show the Messages panel without moving focus (task 3).
2. **Extract the zoom toggle.** Move `MainFrame.TitlePanel.toggleZoom` (`MainFrame.java:1107`) to a public `MainFrame.toggleZoom()` that acts on the main frame. `TitlePanel`'s double-click calls it. `ZoomWindowAction` (task 5) calls the same method.
3. **Split the main window's content** in `MainFrame.initContent` (`MainFrame.java`).
   - Put the score scroll pane (`requireScoreView().requireScrollPane()`) on top and the `ReportPanel` below, in a `JSplitPane(JSplitPane.VERTICAL_SPLIT)`. The split pane goes in `BorderLayout.CENTER`. Set `resizeWeight` to 1, so a window resize changes the score area and keeps the panel height.
   - Add the global prefs `PrefsKey.MESSAGES_PANEL_VISIBLE` (boolean, default `false`) and `PrefsKey.MESSAGES_PANEL_HEIGHT` (int, the panel height in pixels; give it a named default in `user-defaults.json`). Insert each constant and each JSON entry in sorted position, as `prefs.md` says.
   - At startup, the panel is visible when `MESSAGES_PANEL_VISIBLE` is true, at the height in `MESSAGES_PANEL_HEIGHT`.
   - Hidden means: the panel is not visible and the divider has size 0, so the score fills the content. Showing restores the divider size and sets the divider location from `MESSAGES_PANEL_HEIGHT`.
   - Write `MESSAGES_PANEL_HEIGHT` when the user releases the divider after a drag. Do not write it on each `DIVIDER_LOCATION_PROPERTY` change, because each `Prefs` write saves the whole file to disk. Write `MESSAGES_PANEL_VISIBLE` each time the panel is shown or hidden.
   - Give `MainFrame` the operations the actions and the panel need, named for what they do: show the messages panel without focus, show it and focus its tree, hide it and focus the score, focus the score, and answer whether the focus owner is in the messages panel.
4. **Track which area has focus.** Add one `KeyboardFocusManager` listener for `"permanentFocusOwner"` in `MainFrame`. When the focus owner is in the `ReportPanel`, the Messages action is selected and the Score action is not. Otherwise the Score action is selected and the Messages action is not. Put this in one method, so the actions in task 5 can call it after they act. A `JCheckBoxMenuItem` changes its own selected state when it is clicked, and the click does not always move focus. Thus each action must call this method at the end of `performAction`.
5. **Create the four Window actions** in `src/main/java/songscribe/ui/action/`, each with a `createAction(MainFrame)` factory, as `CloseWindowAction` has:
   - `MinimizeWindowAction`: iconifies the main frame. Meta-M.
   - `ZoomWindowAction`: calls `MainFrame.toggleZoom()`. Meta-Alt-Z. The name `ZoomAction` is already the score zoom.
   - `FocusScoreAction` extends `SelectableUIAction`: moves keyboard focus to the score. Meta-0.
   - `ToggleMessagesAction` extends `SelectableUIAction`: when the panel is hidden, or visible without focus, it shows the panel and focuses its tree. When the tree already has focus, it hides the panel and focuses the score. Meta-Shift-M.
   - Use `Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()`-based modifiers as the other actions do (`MENU_SHORTCUT_MASK` in `Actions`).
   - Use the name keys `Strings.ACTION_WINDOW_MINIMIZE`, `Strings.ACTION_WINDOW_ZOOM`, `Strings.ACTION_WINDOW_SCORE` and `Strings.ACTION_WINDOW_MESSAGES`. Phase 4 adds them.
   - Give each action `Flag.ENABLED_IN_GRACE_MODE`, with a comment that states why: it does not touch the document.
   - Before you set the flags of `FocusScoreAction` and `ToggleMessagesAction`, read what an in-place text edit does when it loses focus (the lyric and text editors that post `TextEditingDidChangeNotification`). If the edit commits or cancels cleanly on focus loss, give the action no text-editing flag. Otherwise give it `Flag.DISABLE_WHEN_EDITING_TEXT`. State the reason in a comment beside the flag.

## ✅ Phase 4: Window Menu Wiring

**Status:** Complete  <br>
**BlockedBy:** 2, 3  <br>
**Files:** src/main/java/songscribe/ui/menu/MenuController.java, src/main/java/songscribe/ui/action/Actions.java, src/main/resources/songscribe/strings.properties  <br>
**Recommended model/effort:** Sonnet, low — menu assembly and string entries for actions that Phase 3 created

Phase 3 created `MinimizeWindowAction`, `ZoomWindowAction`, `FocusScoreAction` and `ToggleMessagesAction` in `src/main/java/songscribe/ui/action/`, each with `createAction(MainFrame)`. `MainFrame` has the focus-sync method that sets their selected states. Read `.claude/guides/strings.md` before you add keys.

### Tasks

1. **Add the strings** to `src/main/resources/songscribe/strings.properties`, each in sorted position in its group: `menu.window = Window`, `action.window.minimize = Minimize`, `action.window.zoom = Zoom`, `action.window.score = Score`, `action.window.messages = Messages`, `report.none = No changes.`
2. **Register the Score and Messages actions** in `Actions` the same way the other cached actions are registered, if `MainFrame`'s focus-sync method reaches them through `Actions`. Otherwise keep the instances that `MenuController` creates and give them to `MainFrame`. Use the shape that the focus-sync method in `MainFrame` expects.
3. **Add `MenuController.initWindowMenu()`** and add its menu last in `initMenus()`, after `NotationMenu`. Items, in order: Minimize, Zoom, separator, Score, Messages. Score and Messages are `JCheckBoxMenuItem`s bound to their actions. On each of these two items, set the selected icon to the native check mark:

   ```java
   Icon checkmark = new ImageIcon(
       Toolkit.getDefaultToolkit().createImage("NSImage://NSMenuOnStateTemplate"));
   item.setSelectedIcon(checkmark);
   ```

   Create the icon once and use it for both items. The menu bar is installed on macOS only (`MenuController.java:101`), so the `NSImage://` URL is always available where the menu shows.

## ✅ Phase 5: Design Docs and Guides

**Status:** Complete  <br>
**BlockedBy:** 1, 3, 6  <br>
**Files:** docs/change-report.md, docs/undo.md, docs/mutations.md, .claude/guides/dialogs.md, .claude/guides/strings.md, .claude/guides/vocabulary.md, CLAUDE.md  <br>
**Recommended model/effort:** Sonnet, medium — prose that states a design Phases 1 and 3 decided

Read `~/.claude/guides/documents.md` and the "Design Docs" paragraph of `CLAUDE.md` first. A doc states concepts, not class or method names, and states the current design with no history.

These facts are the design to describe: the report of every edit travels with the edit's change notification, and it is empty when the edit changed nothing on its own. Undo shows an empty report. Redo shows again the report of the edit it applies. A finished report never changes; only a collector records. A load's report travels with the load notification. The report is shown in a Messages panel at the bottom of the main window, which the user can keep open. The panel replaces its list after each edit, undo, redo and load. It opens by itself only after a load that changed the song. The Window menu's Messages item shows the panel and focuses it, or hides it when it already has focus.

### Tasks

1. **`docs/change-report.md`.** Rewrite "One way to tell the user" and "Two routes to one dialog" for the panel. Remove the paragraph about deferring a modal dialog. State the undo and redo behavior. Replace every "dialog" that means the report view with "panel".
2. **`docs/undo.md` and `docs/mutations.md`.** Where they describe what the close of an edit posts, or what a replay posts, state that the change notification carries the edit's report, and that a redo carries the report of the edit it applies again.
3. **`.claude/guides/dialogs.md`.** Line 66 names `ReportDialog` as the one dialog with `@Nullable Void` output. Remove that sentence and state what is true now. Delete the "DialogCategory" section (lines 181–192). In its place, state that every visible `BaseDialog` is counted, and that an action with `UIAction.Flag.OPENS_DIALOG` is disabled while `BaseDialog.isAnyDialogVisible()` is true. Keep the two rules from line 187 that stay true: `OptionDialogs` does not take part in the count, and an action that opens a `BaseDialog` must set `UIAction.Flag.OPENS_DIALOG` (`DialogOpenAction` does not set it).
4. **`.claude/guides/strings.md`.** Add `report.*` to the prefix taxonomy as the group of the change report: its headings, reasons, locations and the panel's sentences. In line 80, replace the example "whose heading key reaches `ReportDialog` as a value" with `ReportPanel`.
5. **`.claude/guides/vocabulary.md`.** Lines 37–38 list "dialog categories and the blocking counter" as parts of the dialog framework. Change this to "the visible-dialog counter", because the categories do not exist and the counter counts every visible dialog.
6. **`CLAUDE.md`.** In the "change report" bullet of "Required Reading by Task", replace "the report dialog" with "the Messages panel".

## ✅ Phase 6: Dialog Category Removal

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Files:** src/main/java/songscribe/ui/dialog/DialogCategory.java, src/main/java/songscribe/ui/dialog/BaseDialog.java, src/main/java/songscribe/ui/dialog/StandardDialog.java, src/main/java/songscribe/ui/dialog/SongSettingsDialog.java, src/main/java/songscribe/ui/dialog/PreferencesDialog.java, src/main/java/songscribe/ui/dialog/OtherValueDialog.java, src/main/java/songscribe/ui/action/UIAction.java  <br>
**Recommended model/effort:** Sonnet, low — deletion of a parameter and an enum whose every constant now selects the same behavior, and renames that follow from it

Phase 2 deleted `ReportDialog`, which was the only user of `DialogCategory.INFORMATIONAL`. The two constants that stay, `EXCLUSIVE` and `OPERATIONAL`, both answer `true` from `isBlocking()`. Thus every `BaseDialog` is a blocking dialog, and the category selects nothing. Use the Serena `jet_brains_*` tools for Java lookups. Run `jet_brains_find_referencing_symbols` on `DialogCategory` before you start, and stop and report if it finds a use that this list does not name.

### Tasks

1. **`BaseDialog`** (`ui/dialog/BaseDialog.java`): delete the `category` field, the constructor `BaseDialog(MainFrame, String, Modality, DialogCategory)` and `getCategory()`, which has no callers. Move the body of the 4-argument constructor into `BaseDialog(MainFrame, String, Modality)`. In `setVisible`, call `incrementBlockingCount()` and `decrementBlockingCount()` without the `category.isBlocking()` checks (lines 816, 830 and 853). Keep the order and the comments about `dispose()` before the decrement.
2. **`StandardDialog`** (`ui/dialog/StandardDialog.java`): delete the constructor `StandardDialog(MainFrame, String, DialogOps, DialogCategory)`. Move its body into `StandardDialog(MainFrame, String, DialogOps)`, which calls `super(mainFrame, title, Modality.MODAL)`.
3. **Callers:** `SongSettingsDialog` (`SongSettingsDialog.java:81`) calls `super(mainFrame, Strings.get(Strings.DIALOG_SONG_SETTINGS_TITLE), ops)`. `PreferencesDialog` (`PreferencesDialog.java:112`) calls `super(mainFrame, Strings.get(Strings.DIALOG_PREFERENCES_TITLE), Modality.MODELESS)`.
4. **`OtherValueDialog`**: delete the Javadoc sentence at lines 45–46 that says the dialog needs no `DialogCategory` override.
5. **Delete `ui/dialog/DialogCategory.java`.**
6. **Rename the counter members of `BaseDialog`.** After task 1 every visible `BaseDialog` is counted, so "blocking" no longer tells one kind of dialog from another. Rename each member with `jet_brains_rename` and `rename_in_comments: false`, as `.claude/rules/serena.md` says:

   | Now | New name |
   |---|---|
   | `visibleBlockingDialogCount` (`BaseDialog.java:124`) | `visibleDialogCount` |
   | `isAnyBlockingDialogVisible()` (`BaseDialog.java:277`) | `isAnyDialogVisible()` |
   | `incrementBlockingCount()` (`BaseDialog.java:286`) | `incrementVisibleDialogCount()` |
   | `decrementBlockingCount()` (`BaseDialog.java:294`) | `decrementVisibleDialogCount()` |

   The one caller outside `BaseDialog` is `UIAction.enableFromDialogVisibility` (`UIAction.java:806`), and the rename updates it.

   Before the renames, delete `resetVisibleBlockingDialogCount()` (`BaseDialog.java:281–283`) with `jet_brains_safe_delete`. Nothing in `src/main` or `src/test` calls it. If the safe delete reports a usage, stop and report it.
7. **Update the comments that name the old members.** The comments in `BaseDialog.setVisible` (near lines 826 and 847) name `decrementBlockingCount()` in plain text, so the rename does not change them. Change them to `decrementVisibleDialogCount()`. After all renames, run `git status --porcelain | grep -v '\.java$'` and read the diff of each prose file that the renames changed. Restore any Markdown sentence that a rename made wrong.

## ✅ Phase 7: Tests

**Status:** Complete  <br>
**BlockedBy:** 1, 2  <br>
**Files:** src/test/java/songscribe/dom/ChangeReportDeliveryTest.java, src/test/java/songscribe/undo/, src/test/java/songscribe/dom/TieRuleTest.java, src/test/java/songscribe/dom/EndingSectionRuleTest.java, src/test/java/songscribe/dom/HairpinTest.java, src/test/java/songscribe/dom/SpanTest.java, src/test/java/songscribe/dom/ModificationSessionTest.java, src/test/java/songscribe/dom/BatchReplay.java, src/main/java/songscribe/undo/UndoController.java, src/main/java/songscribe/undo/ReplayTarget.java, src/main/java/songscribe/ui/component/ScoreView.java, src/main/java/songscribe/ui/action/Actions.java, docs/undo.md  <br>
**Recommended model/effort:** Sonnet, medium — tests written to contracts that Phase 1 wrote

Read `.claude/guides/testing-common.md` and `.claude/guides/testing-unit.md` first. Write each test against the contract of `ModificationSession.deliver`, `Song.replay`, `UndoController.undo` and `UndoController.redo` from Phase 1, not against their code. Before you write each test method, check whether it sits beside a test of the same shape. If it does, make both rows of one parameterized table.

| Test | Kind |
|---|---|
| One edit posts one `SongDidChangeNotification` whose report holds every change the edit made on its own, in recorded order (rewrite of the existing `ChangeReportDeliveryTest` cases) | Invariant spanning several calls |
| An edit that changes nothing on its own posts a `SongDidChangeNotification` with an empty report | Invariant spanning several calls |
| After an edit that reported changes: undo posts an empty report, and redo posts a report equal to the edit's report | Invariant spanning several calls |

### Tasks

1. Rewrite `ChangeReportDeliveryTest` (`src/test/java/songscribe/dom/ChangeReportDeliveryTest.java`) so that it reads the report from `SongDidChangeNotification.getReport()`. Delete each case that asserts something about `ChangesWereReportedNotification` that the new contract does not promise. Add the empty-report case.
2. **Make `UndoController` independent of the main window.** It now calls `MainFrame.getInstance().getScoreView()` in `replayTop` and `documentWasSaved`, and it uses only the view's `getSong()` and the view as a `FontTarget`.
   - Create `src/main/java/songscribe/undo/ReplayTarget.java`: `public interface ReplayTarget extends FontTarget` with `Song getSong()`. Its Javadoc: the open document as the undo engine sees it, which is the song and the place where a replay applies fonts.
   - `ScoreView` implements `ReplayTarget` in place of `FontTarget`.
   - `UndoController.initialize()` becomes `initialize(Supplier<@Nullable ReplayTarget> target)`. The supplier gives the open document, or `null` when no document is open. Store it in a field. Its value before `initialize` is a supplier that gives `null`. `Actions.initialize` (`Actions.java:214`) passes `mainFrame::getScoreView`.
   - `replayTop` and `documentWasSaved` get the song and the font target from the supplier. Remove the import of `MainFrame`.
   - Add a package-private constructor `UndoController(Supplier<@Nullable ReplayTarget> target)` for tests. Make `performUndo` and `performRedo` package-private. `undo()` and `redo()` stay as calls to `INSTANCE`.
   - Update the class Javadoc ("Lifecycle") and the `initialize` contract. If `docs/undo.md` names where the engine gets the open document, update it.
3. **Create `src/test/java/songscribe/undo/UndoControllerTest.java`** with the undo/redo row of the table above, and no other test. The test makes its own `UndoController` with a `ReplayTarget` over its own song and subscribes it to the test bus. It makes an edit that a rule reports, for example a delete that breaks a tie. It calls `performUndo` and `performRedo`, and reads `getReport()` from each `SongDidChangeNotification`.
4. **One report recorder.** `BatchReplay.ReportRecorder` keeps every report it receives, empty ones included. `reports()` gives the non-empty reports, as now, so its present callers do not change. Add `allReports()`, which gives every report in delivery order. `ChangeReportDeliveryTest` uses `ReportRecorder` and has no recorder class of its own.
5. **Recorded order.** In `ChangeReportDeliveryTest`, assert the changes of one edit in the order the edit records them. Get that order from the order in which the rules run in `ModificationSession` and `ModificationRules`, and assert with `containsExactly`.
6. `MutationReplayer.undoBatch` and `redoBatch` take a fourth `ChangeReport` argument. Pass `ChangeReport.EMPTY` at each call in `TieRuleTest.testOneUndoRestoresTheEditAndTheTieItRemoved`, `EndingSectionRuleTest.testUndoRestoresTheEndingAndRedoRemovesItAgain`, `HairpinTest.testAnEditThatBringsAStructuralBarInsideAHairpinRemovesItReportedAndUndoRestoresIt`, `SpanTest.testAnAdditionsDisplacementsAreAppliedOnceAcrossUndoAndRedo`, `SpanTest.testADeletionAndTheSpanItRemovesAreUndoneAndRedoneTogether`, `ModificationSessionTest.testNoRuleRunsWhileABatchIsReplayed` and `ModificationSessionTest.testUndoAndRedoDeliverNoReportEvenWhenARuleWouldRemoveSomething`. None of these tests is about the report that a redo carries; task 3 tests that.

## ✅ Phase 8: Compile and Test Gate

**Status:** Complete  <br>
**BlockedBy:** 1, 2, 3, 4, 5, 6, 7  <br>
**Files:** —  <br>
**Recommended model/effort:** Sonnet, low — run the build and the named tests, and fix what they report

### Tasks

1. Run `./scripts/compile.sh --test`. It must report SUCCESS. Fix every error at its cause.
2. Run `./scripts/test.sh ChangeReportDeliveryTest UndoControllerTest TupletLoadPassTest HairpinLoadPassTest PasteSpanReconciliationTest ModificationSessionTest TieRuleTest EndingSectionRuleTest HairpinTest SpanTest TupletTest LineSpansTest BeamTest HairpinSweepTest EndingTest`. All must pass. Do not run the whole suite.

## ⏳ Phase 9: Manual UI Verification

**Status:** Pending  <br>
**BlockedBy:** 8  <br>
**Files:** —  <br>
**Recommended model/effort:** — (the user does this phase)

### Tasks

The user runs the app on macOS and checks each item:

1. At first launch the Messages panel is hidden, and the score fills the window.
2. An edit that removes a span (for example, a delete that breaks a tie) puts the change in the panel. The panel does not open by itself.
3. A later edit with no automatic change clears the list and shows "No messages" at the center of the list area. The semibold label "Messages:" stays above the list at all times.
4. Undo clears the list. Redo shows the original changes again.
5. Opening a file that the load changes opens the panel with the list. Focus stays on the score.
6. File > New clears the list.
7. Meta-Shift-M with the panel hidden shows it. Meta-Shift-M again hides it. Neither moves keyboard focus, and a click on the list does not take focus.
8. Meta-0 focuses the score.
9. The Window menu shows the native check mark on Messages while the panel is shown, and on Score while the score has focus. Clicking the checked Score item does not remove its check mark. Clicking the checked Messages item hides the panel and removes its check mark.
10. Meta-M minimizes the window. Meta-Alt-Z zooms it, the same as a double-click on the title bar.
11. Drag the divider, quit, and launch again. The panel height and the panel visibility are the same as at quit.
12. Resize the window. The panel height stays the same, and the score area changes.
13. Open Song Settings, then Preferences. While each is open, the actions that open a dialog are disabled, as before.
