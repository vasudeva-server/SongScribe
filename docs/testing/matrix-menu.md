## 11. `ui/menu` + `ui/playback` + `ui/platform` + top-level `ui` (audited 2026-05-22)

Audited via six parallel production-first sub-audits across two waves — Wave 1: **11A** `ui/menu`; **11B** `ui/playback`; **11C** `ui/platform/mac` — Wave 2: **11D** `MusicEditOperations`; **11E** appearance & dialog helpers (`OptionDialogs`, `EndingConfirms`, `AppearanceManager`, `Appearance`, `LafOperations`); **11F** display & constants (`KeySignatureDisplay`, `Constants`, `Control`, `Mode`, `FlatLafProps`). 31 production classes (+5 `package-info`), matching the ~38 estimate. None of the top-level `ui` classes had its own audit row before this session — prior sessions referenced them only as collaborators. Read-only; e2e assessed from source only; coverage checked across unit (mirrored + cross-package) and e2e.


### 11A — `ui/menu` (Menu Construction & Controller)

| Class | Behavior | Required level | Existing test | Verdict | Action |
|---|---|---|---|---|---|
| `MenuController` | `buildLabels` — unique filenames: each path returns its filename as label | unit | none | missing | Add `MenuControllerTest.testBuildLabelsUniqueFilenames` — pass list of distinct filenames, assert each label equals the filename |
| `MenuController` | `buildLabels` — duplicate filenames: appends shortest unique parent suffix to disambiguate | unit | none | missing | Add `testBuildLabelsDuplicateFilenames` — two paths with same filename, different parent dirs; assert label = `filename — parentDir` |
| `MenuController` | `buildLabels` — duplicate filenames requiring multiple depth levels: falls back to deeper suffix when depth-1 parent is also identical | unit | none | missing | Add `testBuildLabelsTwoLevelDisambiguation` |
| `MenuController` | `buildLabels` — all-duplicate fallback: uses full path with `~` substitution when no depth resolves uniqueness | unit | none | missing | Add `testBuildLabelsFallbackToFullPath` |
| `MenuController` | `tildeSubstitute` — path under home directory replaced with `~/...` | unit | none | missing | Add `testTildeSubstituteUnderHome` |
| `MenuController` | `tildeSubstitute` — path outside home directory returned unchanged | unit | none | missing | Add `testTildeSubstituteOutsideHome` |
| `MenuController` | `tildeSubstitute` — path exactly equal to home directory returns `~` | unit | none | missing | Add `testTildeSubstituteExactlyHome` |
| `MenuController` | `rebuildOpenRecentMenu` — empty recents list: menu contains a single disabled "No recent documents" item | unit | none | missing | Add `testRebuildOpenRecentMenuEmpty` — call `rebuildOpenRecentMenu` via reflection (or extract to package-private); assert item count = 1, disabled |
| `MenuController` | `rebuildOpenRecentMenu` — non-empty recents list: menu contains one item per path + separator + Clear Recents action | unit | none | missing | Add `testRebuildOpenRecentMenuNonEmpty` |
| `MenuController` | `recentDocumentsDidChange` handler rebuilds the open-recent menu when the MBassador notification fires | unit | none | missing | Add `testRecentDocumentsDidChangeRebuildsMen` — post `RecentDocumentsDidChangeNotification` via `MessageCenter`, assert menu is updated |
| `MenuController` | `initFileMenu` — non-macOS: Quit action is present in file menu | unit | none | missing | Add `testQuitActionPresentOnNonMac` (mock `SystemInfo.isMacOS = false`) |
| `MenuController` | `initFileMenu` — macOS: Quit action is absent from file menu | unit | none | missing | Add `testQuitActionAbsentOnMac` (mock `SystemInfo.isMacOS = true`) |
| `MenuController` | `initEditMenu` — non-macOS: Preferences action is present in edit menu | unit | none | missing | Add `testPreferencesActionPresentOnNonMac` |
| `MenuController` | `initEditMenu` — macOS: Preferences action is absent from edit menu | unit | none | missing | Add `testPreferencesActionAbsentOnMac` |
| `MenuController` | `initMenus` — macOS: `setJMenuBar` is called on `mainFrame`; non-macOS: it is not | unit | none | missing | Add `testJMenuBarSetOnMacOnly` — two cases, mock `SystemInfo.isMacOS` |
| `MenuController` | `initHelpMenu` / `addCommonHelpItems` (dead — commented out in `initMenus`) | none | none | none | N/A — unreachable code |
| `MenuController` | `initLaunchMenu` (dead — referenced only in commented-out code) | none | none | none | N/A — unreachable code |
| `NotationMenu` | Constructor wires all action groups into submenus in the expected order | none | none | none | Pure declarative wiring; no branching |
| `NotationMenu` | `menuSelected` listener: when a `ScoreView` with a controller is present, `MAKE_ENDING_ACTION.validate(ctrl)` is called | unit | none | missing | Add `NotationMenuTest.testMenuSelectedCallsValidateWhenControllerPresent` — construct `NotationMenu` with a mock frame; fire the `menuSelected` event; verify `MAKE_ENDING_ACTION.isEnabled()` reflects validation result |
| `NotationMenu` | `menuSelected` listener: when `ScoreView` is null or has no controller, `MAKE_ENDING_ACTION` is disabled | unit | none | missing | Add `testMenuSelectedDisablesMakeEndingWhenNoController` |
| `NotationMenu` | `createTupletMenu` — separator separates tuplet add-actions from remove action | none | none | none | Pure layout wiring |
| `NotationMenu` | `createDynamicsMenu` — all dynamic marking radio items added from `DYNAMIC_MARKING_ACTION_GROUP` | none | none | none | Pure declarative wiring |
| `BarlineMenu` | `FinalTerminalAction.createFinalDoubleBarline` — action fires and replaces terminal to `FINAL_DOUBLE_BARLINE` without showing a confirm dialog | unit | `BarlineMenuTest.testFinalDoubleBarlineItemReplacesTerminalWithoutConfirm` | adequate | — |
| `BarlineMenu` | `FinalTerminalAction.createFinalRightRepeat` — action fires and replaces terminal to `REPEAT_RIGHT` without showing a confirm dialog | unit | `BarlineMenuTest.testFinalRightRepeatItemReplacesTerminalWithoutConfirm` | adequate | — |
| `BarlineMenu` | Radio selection reflects current terminal: `FINAL_DOUBLE_BARLINE` selected, right-repeat unselected | unit | `BarlineMenuTest.testRadioSelectionReflectsCurrentTerminalForFinalBarline` | adequate | — |
| `BarlineMenu` | Radio selection reflects current terminal: right-repeat selected, final-double unselected | unit | `BarlineMenuTest.testRadioSelectionReflectsCurrentTerminalForRightRepeat` | adequate | — |
| `BarlineMenu` | Terminal items are in the same `ButtonGroup` — selecting one deselects the other | unit | none | missing | Add `testTerminalItemsAreMutuallyExclusive` — check that when you set one selected, the other becomes deselected via the shared `ButtonGroup` |
| `BarlineMenu` | `BARLINE_ACTIONS` items are added as `JRadioButtonMenuItem`s before the separator | none | none | none | Pure declarative wiring |
| `FermataMenuItem` | Entire class — superseded by `FERMATA_ACTION` (`FermataAction`); has zero references in production code | none | none | none | Dead class — no tests warranted; should be deleted |
| `FermataMenuItem` | `actionPerformed` — selected: adds `FermataAttachment` to preview element | none | none | none | Logic duplicated by `FermataAction.applyToElement`; dead path |
| `FermataMenuItem` | `actionPerformed` — deselected: removes existing `FermataAttachment` from preview element | none | none | none | Logic duplicated by `FermataAction.applyToElement`; dead path |
| `AccidentalMenu` | Constructor: accidental radio items from `ACCIDENTAL_ACTION_GROUP` + separator + `ACCIDENTAL_IN_PARENS_ACTION` checkbox | none | none | none | Pure declarative wiring |
| `ArticulationMenu` | Constructor: `ACCENT_ACTION` checkbox first, then articulation radio items from `ARTICULATION_ACTION_GROUP` | none | none | none | Pure declarative wiring |
| `DotMenu` | Constructor: dot radio items from `DOT_ACTION_GROUP` | none | none | none | Pure declarative wiring |
| `DurationMenu` | Constructor: note duration radio items from `NOTE_DURATION_ACTIONS` | none | none | none | Pure declarative wiring |
| `GlissandoMenu` | Constructor: glissando + slide-out as radio items | none | none | none | Pure declarative wiring |
| `RepeatsMenu` | Constructor: repeat radio items from `REPEAT_ACTIONS` | none | none | none | Pure declarative wiring |

**Notes:**

The most critical gap in this package is the complete absence of tests for `MenuController.buildLabels` and its helpers `disambiguate` and `tildeSubstitute`. These are non-trivial pure-static methods with multiple branching paths (unique vs. duplicate filenames, iterative depth search, home-directory path substitution, full-path fallback) and no test coverage whatsoever. A bug here produces silently wrong menu labels for recently-opened files — a regression that would be invisible until a user notices duplicate labels in the Open Recent submenu.

The second significant gap is `rebuildOpenRecentMenu` and the `recentDocumentsDidChange` MBassador handler. The "empty recents" vs "non-empty recents" code paths and the notification-driven rebuild are entirely untested. These behaviors are straightforward to unit-test with a mocked `RecentDocumentsManager` and are the core runtime logic of the Open Recent submenu. The `NotationMenu` `menuSelected` dynamic enable/disable of `MAKE_ENDING_ACTION` is also missing a test, though it is lower risk since `validate()` itself is well-tested at the action level.

`FermataMenuItem` is dead code: it has zero references in `src/main` (confirmed by `find_referencing_symbols`) and its functionality is already covered by `FERMATA_ACTION` / `FermataAction`. `MenuController.initHelpMenu`, `addCommonHelpItems`, and `initLaunchMenu` are similarly unreachable (callers are commented out with no active path). `BarlineMenuTest` is a bright spot — it tests the only non-trivial wiring logic in `BarlineMenu` (action binding and radio state) at the right level with real assertions.

**Tally:** 37 rows — 4 adequate · 18 missing · 0 inadequate · 0 wrong-level · 15 none · 0 redundant.

**Dead code:**
- `FermataMenuItem` — zero references in `src/main` or `src/test`; superseded by `FermataAction`.
- `MenuController.initHelpMenu` — zero callers; its invocation in `initMenus` is commented out.
- `MenuController.addCommonHelpItems` — only called from `initHelpMenu` (itself dead).
- `MenuController.initLaunchMenu` — zero callers; its invocation in `initMenus` is commented out.

**Production observations:**
- `FermataMenuItem` (dead class) and the three commented-out methods in `MenuController` constitute accumulated dead code that should be deleted to avoid future confusion.
- `MenuController.buildLabels` is `private static` yet contains 30+ lines of complex path-disambiguation logic. Its access modifier prevents direct unit-testing without reflection or a package-private helper; widening to package-private would unblock tests without changing production behavior.

### 11B — `ui/playback` (Transport, MIDI Controller & Play Thread)

| Class | Behavior | Required level | Existing test | Verdict | Action |
|---|---|---|---|---|---|
| `PlaybackController` | `selectionDidChange` — does nothing when not paused (PLAYING state) | unit | `PlaybackControllerTest.testDoesNothingWhenPlaying` | adequate | — |
| `PlaybackController` | `selectionDidChange` — does nothing when not paused (STOPPED state) | unit | `PlaybackControllerTest.testDoesNothingWhenStopped` | adequate | — |
| `PlaybackController` | `selectionDidChange` — clears highlight and updates `activeSelection` when paused with new selection | unit | `PlaybackControllerTest.testClearsHighlightAndUpdatesSelectionWhenPausedWithSelection` | adequate | — |
| `PlaybackController` | `selectionDidChange` — stops when selection cleared (null) while paused | unit | `PlaybackControllerTest.testStopsWhenSelectionClearedWhilePaused` | adequate | — |
| `PlaybackController` | `togglePlayPause` — transitions STOPPED → PLAYING (calls `play(null)`) | unit | none | missing | Add unit test: mock sequencer, verify state becomes PLAYING and `PlaybackStateDidChangeNotification` posted |
| `PlaybackController` | `togglePlayPause` — transitions PLAYING → PAUSED (calls `playbackDidPause`) | unit | none | missing | Add unit test: mock sequencer, assert state becomes PAUSED |
| `PlaybackController` | `togglePlayPause` — PAUSED with same selection calls `resume()` | unit | none | missing | Add unit test: confirm resume path taken (tick position restored) |
| `PlaybackController` | `togglePlayPause` — PAUSED with changed selection calls `play(newSelection)` | unit | none | missing | Add unit test: verify `activeSelection` updated to new selection |
| `PlaybackController` | `playbackDidStart` — sets state to PLAYING and posts `PlaybackStateDidChangeNotification` | unit | none | missing | Add unit test: assert state and notification |
| `PlaybackController` | `playbackDidPause` — sets state to PAUSED, saves tick position, posts notification | unit | none | missing | Add unit test: mock sequencer tick, verify saved `pausedTickPosition` |
| `PlaybackController` | `stop` — sets state to STOPPED, clears `activeSelection` and `pausedTickPosition`, posts notification | unit | none | missing | Add unit test via `stop()` directly |
| `PlaybackController` | `rewindToBeginning` — while PLAYING: clears highlight and seeks sequencer to tick 0 | unit | none | missing | Add unit test: mock sequencer, verify `setTickPosition(0)` called |
| `PlaybackController` | `rewindToBeginning` — while PAUSED: calls stop (state becomes STOPPED) | unit | none | missing | Add unit test: set state PAUSED, assert state becomes STOPPED |
| `PlaybackController` | `rewindToBeginning` — while STOPPED: no-op | unit | none | missing | Add unit test: state remains STOPPED, no exceptions |
| `PlaybackController` | `handleMetaMessage` — SEQUENCE_NUMBER message decodes line/note indices and calls `updatePlayingNote` | unit | none | missing | Add unit test: construct a `MetaMessage` with packed line+note bytes, mock `ScoreView`, verify `setPlayingIndices` called correctly |
| `PlaybackController` | `handleMetaMessage` — END_OF_TRACK message calls `stop()` | unit | none | missing | Add unit test: assert state becomes STOPPED and notification posted |
| `PlaybackController` | `updatePlayingNote` — clears previous line highlight when line changes | unit | none | missing | Add unit test: set `previousPlayingLine`, call `updatePlayingNote` with different line, verify old `setPlayingIndices(-1,-1)` |
| `PlaybackController` | `updatePlayingNote` — does not clear previous line when line unchanged | unit | none | missing | Add unit test: same line index, verify previous line component NOT cleared |
| `PlaybackController` | `applyPrefsDuringPlayback` — does nothing when not PLAYING | unit | none | missing | Add unit test: set state STOPPED or PAUSED, assert no sequencer interaction |
| `PlaybackController` | `applyPrefsDuringPlayback` — while PLAYING: stops, rebuilds sequence, restores tick, restarts | unit | none | missing | Add unit test: mock sequencer, verify stop/setSequence/setTickPosition/start sequence |
| `PlaybackController` | `setLoopSequence` — sets loop continuously when pref LOOP_PLAYBACK=true and selection is not a single note | unit | none | missing | Add unit test: mock `Prefs.getBoolean`, verify `setLoopCount(Sequencer.LOOP_CONTINUOUSLY)` |
| `PlaybackController` | `setLoopSequence` — does not loop when selection is a single note (begin==end), even if pref is true | unit | none | missing | Add unit test: selection with begin==end, assert `setLoopCount(0)` |
| `PlaybackController` | `buildSequenceForSelection` — null selection builds full sequence | unit | none | missing | Add unit test: verify `MidiSequenceBuilder.buildFullSequence()` path |
| `PlaybackController` | `buildSequenceForSelection` — non-null selection builds from note to end | unit | none | missing | Add unit test: verify `buildFromNoteToEnd(lineIndex, begin)` path |
| `PlaybackController` | `getPlaybackSettings` / `applySettings` round-trip preserves all fields | unit | none | missing | Add unit test: set fields, `getPlaybackSettings()`, `applySettings()`, verify fields restored |
| `PlaybackController` | `applyVolumeFromPrefs` — delegates to `MidiController.setPlaybackVolume` with pref value | unit | none | missing | Add unit test: mock `Prefs.getInt` and `MidiController`, verify forwarding |
| `MidiController` | `setPlaybackVolume` — percent 50..100 linearly scales to MIDI CC7 values ~64..127 (boundary/midpoint values) | unit | none | missing | Pure arithmetic: add unit test for boundary values (50→64, 100→127, 75→~96) |
| `MidiController` | `setPlaybackVolume` — percent below 50 clamps to 50; above 100 clamps to 100 | unit | none | missing | Add unit test for out-of-range inputs |
| `MidiController` | `setPlaybackInstrument` — sends PROGRAM_CHANGE on channel 0 with clamped program number | unit | none | missing | Add unit test: mock `Receiver`, verify `ShortMessage.PROGRAM_CHANGE` with correct channel and data |
| `MidiController` | `isPlaying` — returns false when sequencer is null | unit | none | missing | Add unit test: null sequencer path |
| `MidiController` | `isPlaying` — delegates to `sequencer.isRunning()` when sequencer is non-null | unit | none | missing | Add unit test: mock sequencer |
| `MidiController` | `closeMidi` — idempotent: second call does not close resources again | unit | none | missing | Add unit test: call twice, verify `midiReceiver.close()` called exactly once |
| `MidiController` | `openMidi` / `openSynthesizerWithSoundbank` / `loadBundledSoundbank` / `extractSoundfontToTempFile` — full MIDI init path requires real MIDI hardware | none | — | none | Real hardware I/O; cannot be meaningfully mocked in unit or e2e context |
| `MidiController` | `initChannels` / `initChannel` / `reinitChannels` — GM reset + CC setup; all wired to real `Receiver` | none | — | none | Side-effect-only hardware output; no pure-logic testable path |
| `PlayThread` | `run` — when `playNoteOn=true` sends NOTE_ON, waits `NOTE_DURATION_MS`, sends NOTE_OFF | unit | none | missing | Add unit test: mock `MidiController.midiReceiver`, run thread, verify message sequence |
| `PlayThread` | `run` — when `playNoteOn=false` skips NOTE_ON but still sends NOTE_OFF after delay | unit | none | missing | Add unit test: same setup, verify only NOTE_OFF sent |
| `PlayThread` | `sendNoteOn` — no-op when `midiReceiver` is null | unit | none | missing | Add unit test: null receiver, no exception |
| `PlayThread` | `sendNoteOff` — no-op when `midiReceiver` is null | unit | none | missing | Add unit test: null receiver, no exception |
| `PlayThread` | `sendNoteOn` — sends bank-select + program-change + NOTE_ON messages with correct pitch and velocity | unit | none | missing | Add unit test: mock receiver, verify message types and values |
| `PlayThread` | `sendNoteOff` — sends NOTE_OFF with correct pitch | unit | none | missing | Add unit test: mock receiver, verify NOTE_OFF message |
| `PlayPauseAction` | `actionPerformed` — toggles action icon/name then calls `PlaybackController.togglePlayPause()` | unit | none | missing | Add unit test: verify both icon toggle and `togglePlayPause` called |
| `PlayPauseAction` | `playbackStateDidChange` (STOPPED) — calls `toggleToPlay` (sets play name/icon/tooltip) | unit | none | missing | Add unit test: set state to PAUSE name, post STOPPED notification, verify name reverts to PLAY_NAME |
| `PlayPauseAction` | `toggleAction` — when name is PLAY_NAME switches to pause labels; when pause name switches back | unit | none | missing | Add unit test: call toggleAction twice, verify round-trip |
| `PlayPauseAction` | `DISABLE_WHEN_PLAYING` flag not set — action stays enabled during playback (it is the pause button) | unit | `LyricEditorActionAuditTest.testAllToolbarActionsCarryDisableWhenEditingTextFlag` | inadequate | Audit test only checks `DISABLE_WHEN_EDITING_TEXT`; no test verifies the action remains enabled during PLAYING state |
| `RewindAction` | `actionPerformed` — calls `PlaybackController.rewindToBeginning()` (thin dispatcher) | unit | none | missing | Add unit test: mock `PlaybackController`, verify `rewindToBeginning()` called |
| `LoopPlaybackAction` | `actionPerformed` — posts `ToggleLoopPlaybackCommand` with `isSelected()` value | unit | none | missing | Add unit test: mock `MessageCenter`, invoke action, verify command posted with correct payload |
| `PlayWithRepeatsAction` | `actionPerformed` — posts `TogglePlayWithRepeatsCommand` with `isSelected()` value | unit | none | missing | Add unit test: same pattern |
| `SequencerAction` | constructor delegation to `UIAction` | none | — | none | Pure super-call delegation with no own logic |
| `MidiMetaMessageTypes` | Constants hold correct MIDI spec hex values | none | — | none | Pure constants holder; no logic |

**Notes:**

The `PlaybackController` class is the highest-risk gap in the entire package. It is a static-method singleton implementing a multi-state transport machine (STOPPED/PAUSED/PLAYING) with six distinct state-transition paths (`togglePlayPause` alone has four branches) and a non-trivial meta-message callback that decodes packed binary data into line/note indices. Not one of these behaviors has a unit test. The four `selectionDidChange` tests that exist are the only coverage. Every state transition, every notification post, every highlight-coordination sequence, and the `applyPrefsDuringPlayback` spin-wait restart path are completely untested. Because all public methods are static and all dependencies (`MidiController.sequencer`, `registeredScore`) are settable via test-visible setters/statics, these are straightforward unit targets — no e2e or real hardware is required.

`MidiController.setPlaybackVolume` contains a concrete arithmetic formula (`Math.round(Math.clamp(percent, 50, 100) / 100f * 127)`) whose boundary behavior (50%→64, 100%→127) and clamping could silently regress. Similarly, `PlayThread.sendNoteOn`/`sendNoteOff` are static utility methods that can be unit-tested by injecting a mock `Receiver` into `MidiController.midiReceiver`. The action thin-dispatcher gap is present for all four `*Action` classes: `actionPerformed` on `PlayPauseAction`, `RewindAction`, `LoopPlaybackAction`, and `PlayWithRepeatsAction` each contain dispatch logic (icon toggle, command post, direct controller call) that is never exercised by any test.

The `LyricEditorActionAuditTest` (T25) provides coverage of `DISABLE_WHEN_EDITING_TEXT` for all four playback actions, which is a useful structural audit. `NoteDragHandlerTest` references `PlayThread` and `MidiController` only as mocked-out infrastructure to suppress side effects — no behavior of those classes is validated. The midi-package tests (`GlissandoMidiHelperTest`, `VelocityMapTest`, `GlissandoMidiIntegrationTest`) are well-structured and test adjacent MIDI logic adequately, but they do not touch any class in this package.

**Tally:** 49 rows — 4 adequate · 40 missing · 1 inadequate · 0 wrong-level · 4 none · 0 redundant.

**Dead code:** none found. All classes and public methods have verified callers in `src/main` or `src/test`.

**Production observations:** `PlaybackController.setSequenceToPlayFromSelection` uses identity comparison (`sequence != sequencer.getSequence()`) guarded by `//noinspection ObjectEquality` — this is correct for reference equality on `Sequence` objects but is easy to misread; worth a clarifying comment. `PlayThread` extends `Thread` directly rather than implementing `Runnable`; minor style issue but not a bug. The `setupInstrument()` method in `PlayThread` throws `RuntimeError.exit(...)` when `midiReceiver` is null, making it fatal in a path that `sendNoteOn` already guards with a null check and silent return — the guard in `sendNoteOn` makes the fatal path unreachable, but it is confusing.

### 11C — `ui/platform/mac` (Native macOS Menu Integration)

| Class | Behavior | Required level | Existing test | Verdict | Action |
|---|---|---|---|---|---|
| `MacNativeMenuController` | Constructor subscribes to `MessageCenter` and stores strong reference so the MBassador weak-ref rule is satisfied | unit | none | missing | Add unit test: construct, post `DialogVisibilityDidChangeNotification`, verify `setEnabled` called on each managed item — use Mockito `@Mock NSMenuItem` injected via a constructor overload or reflective field set |
| `MacNativeMenuController` | `dialogVisibilityDidChange` sets all `managedItems` to `!notification.isVisible()` (enables on hide, disables on show) | unit | none | missing | Test with a small list of mock `NSMenuItem`s: post notification with `isVisible=true` → verify `setEnabled(false)`; `isVisible=false` → verify `setEnabled(true)` |
| `MacNativeMenuController` | `dialogVisibilityDidChange` iterates all managed items, not just the first | unit | none | missing | Post one notification with two mock items in the list; verify both receive `setEnabled` |
| `MacNativeMenuController` | `discoverNativeItems` returns empty list and logs warning when `appMenuItem.hasSubmenu()` is false | unit | none | missing | Mock the Rococoa chain (or factor discovery behind an interface) to return a top-level item with `hasSubmenu()=false`; assert result is empty |
| `MacNativeMenuController` | `discoverNativeItems` matches each `AppMenuAction` by `startsWith` prefix against item titles; unmatched actions produce a warning log | unit | none | missing | Provide mock `NSMenuItem`s with known titles, one matching and one not; verify matched item is in result, unmatched triggers LOG.warn |
| `MacNativeMenuController` | `discoverNativeItems` wraps the whole native call sequence in a broad `try/catch(Exception)`; any exception returns empty list | unit | none | missing | Have `NSApplication.sharedApplication()` throw a `RuntimeException`; assert no exception propagates and the returned list is empty |
| `MacNativeMenuController` | `discoverNativeItems` calls `setAutoenablesItems(false)` on the app menu before iterating items | unit | none | missing | Verify this side-effect on the mock `NSMenu` |
| `MacNativeMenuController` | `Actions.getAppMenuActions()` result is fetched correctly (correct count, correct native titles) | unit | `ActionsAppMenuTest.testGetAppMenuActionsReturnsExpectedActions`, `testAppMenuActionsHaveCorrectNativeTitles` | adequate | — (already covered at the right level in `ActionsAppMenuTest`; not a `MacNativeMenuController` behavior per se, but the dependency is tested) |
| `NSApplication` | `sharedApplication()` delegates to `CLASS.sharedApplication()` (pure Rococoa pass-through) | none | none | none | Native JNI/Rococoa bridge — no assertable logic on our side without a live macOS runtime |
| `NSApplication` | `mainMenu()` abstract method (pure Rococoa pass-through) | none | none | none | Pure native pass-through |
| `NSMenu` | `numberOfItems()`, `itemAtIndex()`, `setAutoenablesItems()` abstract methods (pure Rococoa pass-through) | none | none | none | Pure native pass-through |
| `NSMenu` | `title()` abstract method | none | none | none | Pure native pass-through — `title()` has no callers in production code (see Dead code) |
| `NSMenu` | `itemWithTitle()` abstract method | none | none | none | Pure native pass-through — no callers in production code (see Dead code) |
| `NSMenuItem` | `title()`, `hasSubmenu()`, `submenu()`, `setEnabled()` abstract methods (pure Rococoa pass-through) | none | none | none | Pure native pass-through |
| `NSMenuItem` | `isEnabled()` abstract method | none | none | none | Pure native pass-through — `isEnabled()` has no callers in production code (see Dead code) |

**Notes:**

`NSApplication`, `NSMenu`, and `NSMenuItem` are thin Rococoa abstract-class wrappers. Every method on them is `abstract` and is dispatched directly to the native Objective-C runtime via the Rococoa/JNA bridge — there is no Java-side logic, no branching, and no transformation. These are correctly classified `none`: they cannot be unit-asserted without a live macOS runtime, and testing that Rococoa calls the right native method would be testing the Rococoa framework, not our code.

All testable behavior lives in `MacNativeMenuController`. The two most important gaps are (1) the `dialogVisibilityDidChange` handler — this is the entire runtime purpose of the controller and has zero test coverage — and (2) the `discoverNativeItems` discovery logic, which contains several distinct branches (no-submenu guard, prefix-`startsWith` matching, exception swallowing) that can all be exercised by mocking the NS* interfaces. The `MacNativeMenuController` constructor takes no parameters today, making injection of mock NS* objects awkward; the recommended approach is either a package-private constructor that accepts a pre-built `List<NSMenuItem>` for testing, or extracting the NS* chain calls behind a narrow functional interface. The OS-conditional path in `MenuController.initMenus` (wraps construction in `if (SystemInfo.isMacOS)`) is in a different class and out of scope here; note it also swallows the `Throwable` silently in headless mode, which means test runs on non-macOS CI will never exercise the controller at all — reinforcing the need for injectable mocking.

`BaseDialogCounterTest` exercises `DialogVisibilityDidChangeNotification` dispatch thoroughly at the sender side (verifying the message is posted on first-open and last-close). That is the upstream dependency of `dialogVisibilityDidChange`; what is missing is the handler side — verifying that the controller correctly reacts to those notifications by enabling/disabling the managed native items.

**Tally:** 15 rows — 1 adequate · 7 missing · 0 inadequate · 0 wrong-level · 7 none · 0 redundant.

**Dead code:**
- `NSMenu._Class.alloc()` — declared but never called anywhere in `src/main` or `src/test`. Likely copied from a template; the factory pattern is unused because `NSMenu` instances are obtained only via `NSApplication.mainMenu()` and `NSMenuItem.submenu()`.
- `NSMenuItem._Class.alloc()` — same situation; never called.
- `NSMenu.CLASS` field — never read (the `NSMenu._Class` factory is never invoked, so the Rococoa-registered class object is unused).
- `NSMenuItem.CLASS` field — same.
- `NSMenu.title()` — no callers in `src/main` or `src/test`.
- `NSMenu.itemWithTitle(String)` — no callers in `src/main` or `src/test`.
- `NSMenuItem.isEnabled()` — no callers in `src/main` or `src/test`.

Note: these symbols are in an OS-conditional package, but the dead-code determination is based on verified reference searches across all of `src/main` and `src/test`, not just conditional paths. There is no reflective usage of these specific methods found.

**Production observations:**
- `NSMenu.CLASS` is annotated `@SuppressWarnings("unused")` and `NSMenuItem.CLASS` likewise, indicating the authors are aware these fields have no callers — but the `_Class.alloc()` methods and the `title()`/`itemWithTitle()`/`isEnabled()` methods have no such suppression, suggesting they were included speculatively for future use.
- `MacNativeMenuController` is not a singleton per the project's `singletons.md` pattern (no `private static final INSTANCE`). It is instead held as a `@Nullable private static` field on `MenuController` with a `@SuppressWarnings({"FieldCanBeLocal", "unused"})` annotation to prevent GC — this is a deliberate strong-reference anchor. The pattern deviates from the singleton guide but is intentional (the field only exists to prevent the MBassador weak-reference from being collected).

### 11D — `MusicEditOperations` (top-level `ui`)

| Class | Behavior | Required level | Existing test | Verdict | Action |
|---|---|---|---|---|---|
| `MusicEditOperations` | `canToggleBeaming()` delegates to `state.canToggleBeaming()`; returns false when state is null | unit | none | missing | Add test: null state → false; non-null state delegates to `LineSelectionState` (the delegation itself is a one-liner but the null branch is dark) |
| `MusicEditOperations` | `toggleBeaming()` — null state guard (early return, no mutation) | unit | none | missing | Add test: invoking with no active selection emits no notification |
| `MusicEditOperations` | `toggleBeaming()` — add beam (no existing beam or split across different beams) | unit | `MusicEditOperationsMutationTest.testToggleBeamingAddEmitsBeamingAddition`, `BeamToggleTest.ToggleBeam.testToggleBeamOn` | adequate | None |
| `MusicEditOperations` | `toggleBeaming()` — remove beam (begin and end in same beam group) | unit | `MusicEditOperationsMutationTest.testToggleBeamingRemoveEmitsBeamingRemoval`, `BeamToggleTest.ToggleBeam.testToggleBeamOff` | adequate | None |
| `MusicEditOperations` | `canToggleTie()` delegates to `state.canToggleTie()`; returns false when state is null | unit | none | missing | Add test: null state → false |
| `MusicEditOperations` | `toggleTie()` — null state guard | unit | none | missing | Add test: no mutation emitted |
| `MusicEditOperations` | `toggleTie()` — add tie (no existing tie) | unit | `MusicEditOperationsMutationTest.testToggleTieAddEmitsTieAddition`, `TieToggleTest.testTieCreationAndRemoval` | adequate | None |
| `MusicEditOperations` | `toggleTie()` — remove tie (existing tie found) | unit | `MusicEditOperationsMutationTest.testToggleTieRemoveEmitsTieRemoval`, `TieToggleTest.testTieCreationAndRemoval` | adequate | None |
| `MusicEditOperations` | `canToggleTuplet()` — returns default `TupletToggleInfo(false, null, false)` when state is null | unit | none | missing | Add test: null state returns false-info |
| `MusicEditOperations` | `toggleTuplet()` — null state guard | unit | none | missing | Add test: no mutation emitted |
| `MusicEditOperations` | `toggleTuplet()` — guard: `info.canToggle() == false` throws `IllegalStateException` | unit | none | missing | Add test: throws with appropriate message |
| `MusicEditOperations` | `toggleTuplet(size=0, info)` — remove tuplet (existing tuplet, size==0 path) | unit | `MusicEditOperationsMutationTest.testToggleTupletRemoveEmitsTupletRemoval` (uses matching-grade, not size=0) | inadequate | Tests the same net effect via grade-match but does NOT exercise the `tupletSize == 0` branch. Add `toggleTuplet(REMOVE.getSize(), info)` test. |
| `MusicEditOperations` | `toggleTuplet(size=0, info)` — size=0 with no existing tuplet throws `IllegalStateException` | unit | none | missing | Add test: `toggleTuplet(0, infoWithNoExisting)` throws |
| `MusicEditOperations` | `toggleTuplet(size>0, null existing)` — add new tuplet | unit | `MusicEditOperationsMutationTest.testToggleTupletAddEmitsTupletAddition` | adequate | None |
| `MusicEditOperations` | `toggleTuplet(size>0, full-coverage same grade)` — remove only | unit | `MusicEditOperationsMutationTest.testToggleTupletMatchingGradeRemovesOnly` | adequate | None |
| `MusicEditOperations` | `toggleTuplet(size>0, full-coverage different grade)` — remove then add | unit | `MusicEditOperationsMutationTest.testToggleTupletGradeChangeEmitsRemovalThenAddition` | adequate | None |
| `MusicEditOperations` | `toggleTuplet()` — sub-range of existing tuplet throws `IllegalStateException` | unit | `MusicEditOperationsMutationTest.testToggleTupletPartialCoverageInExistingTupletThrows` | adequate | None |
| `MusicEditOperations` | `addDynamicsToSelection(true)` — adds crescendo | unit | `MusicEditOperationsMutationTest.testAddDynamicsEmitsOneAddition` (parameterized), `ScoreViewControllerCommandHandlerTest.testHandleAddDynamicsEmitsOneAddition` | adequate | None |
| `MusicEditOperations` | `addDynamicsToSelection(false)` — adds diminuendo | unit | `MusicEditOperationsMutationTest.testAddDynamicsEmitsOneAddition` (parameterized) | adequate | None |
| `MusicEditOperations` | `addDynamicsToSelection()` — null state guard | unit | none | missing | Add test: no mutation emitted |
| `MusicEditOperations` | `canRemoveDynamicsFromSelection()` — returns false when state null or no element selection | unit | none | missing | Add test for both null-state and hasElementSelection=false paths |
| `MusicEditOperations` | `canRemoveDynamicsFromSelection()` — returns false when selection has no dynamics | unit | none | missing | Add test: selection with only notes returns false |
| `MusicEditOperations` | `canRemoveDynamicsFromSelection()` — returns true when crescendo overlaps selection | unit | none | missing | Add test |
| `MusicEditOperations` | `canRemoveDynamicsFromSelection()` — returns true when diminuendo overlaps selection | unit | none | missing | Add test |
| `MusicEditOperations` | `removeDynamicsFromSelection()` — null state guard | unit | none | missing | Add test: no mutation emitted |
| `MusicEditOperations` | `removeDynamicsFromSelection()` — removes all overlapping crescendos and diminuendos | unit | `MusicEditOperationsMutationTest.testRemoveDynamicsEmitsRemovalPerSpan` | inadequate | Test asserts `isNotEmpty()` instead of exact counts; `ScoreViewControllerCommandHandlerTest.testHandleRemoveDynamicsEmitsRemovals` also uses `isNotEmpty()`. Neither pins the exact number of removals emitted. |
| `MusicEditOperations` | `getDynamicsFromSelection()` — partial overlap: span starting before selection and ending inside | unit | none | missing | Add test: hairpin whose anchor is before selectionBegin but end is within range is included |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — null/no-element-selection returns invalid | unit | none | missing | Add test |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — auto-maintained terminal extension | unit | `MusicEditOperationsMutationTest.CanMakeFirstSecondEndingAtSongEnd.testSelectionEndingBeforeAutoMaintainedTerminalIsValid` | adequate | None |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — validateEndingStructure: insufficient content (< MIN_CONTENT_ELEMENTS) returns invalid | unit | none | missing | Add test: selection with fewer than 4 content elements |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — validateEndingStructure: multiple right-repeats within selection returns invalid | unit | none | missing | Add test |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — validateEndingStructure: no right-repeat found returns invalid | unit | none | missing | Add test |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — validateEndingStructure: first ending region has barline/repeat → invalid | unit | none | missing | Add test |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — validateEndingStructure: first ending region empty → invalid | unit | none | missing | Add test |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — validateEndingStructure: second ending region has barline/repeat → invalid | unit | none | missing | Add test |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — validateEndingStructure: REPEAT_LEFT_RIGHT split, REPEAT_RIGHT terminal valid | unit | `MusicEditOperationsMutationTest.CanMakeFirstSecondEndingWithRepeatLeftRightSplit.testRepeatRightTerminalIsValid` | adequate | None |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — validateEndingStructure: REPEAT_LEFT_RIGHT split, REPEAT_LEFT_RIGHT terminal valid | unit | `MusicEditOperationsMutationTest.CanMakeFirstSecondEndingWithRepeatLeftRightSplit.testRepeatLeftRightTerminalIsValid` | adequate | None |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — validateEndingStructure: REPEAT_LEFT_RIGHT split, SINGLE_BARLINE terminal invalid | unit | `MusicEditOperationsMutationTest.CanMakeFirstSecondEndingWithRepeatLeftRightSplit.testSingleBarlineTerminalIsInvalid` | adequate | None |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — validateEndingStructure: REPEAT_LEFT_RIGHT split, REPEAT_LEFT terminal invalid | unit | `MusicEditOperationsMutationTest.CanMakeFirstSecondEndingWithRepeatLeftRightSplit.testRepeatLeftTerminalIsInvalid` | adequate | None |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — validateEndingStructure: SINGLE_BARLINE as leading element adjusts firstEndingStart | unit | `MusicEditOperationsMutationTest.testMakeFirstSecondEndingWithExistingLeadingBarlineEmitsOnlyRangeElementAddition` (exercises NONE path, line has SINGLE_BARLINE at start) | adequate | None |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — hasOverlap: existing ending in selection range returns invalid | unit | none | missing | Add test: selection overlapping an existing `Ending` span returns false |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — hasEnclosingRepeat: double barline blocks scan | unit | `MusicEditOperationsMutationTest.HasEnclosingRepeatRules.testDoubleBarlineBlocksScan` | adequate | None |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — hasEnclosingRepeat: FINAL_DOUBLE_BARLINE blocks scan | unit | `MusicEditOperationsMutationTest.HasEnclosingRepeatRules.testFinalDoubleBarlineBlocksScan` | adequate | None |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — hasEnclosingRepeat: first-line song-start is valid | unit | `MusicEditOperationsMutationTest.HasEnclosingRepeatRules.testFirstLineNoRepeatIsValid` | adequate | None |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — hasEnclosingRepeat: non-first line, no repeat found → invalid | unit | `MusicEditOperationsMutationTest.HasEnclosingRepeatRules.testNonFirstLineNoRepeatIsInvalid` | adequate | None |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — hasEnclosingRepeat: REPEAT_LEFT on previous line → valid | unit | `MusicEditOperationsMutationTest.HasEnclosingRepeatRules.testRepeatLeftOnPreviousLineIsValid` | adequate | None |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — checkPrecedingElement: beginning of song (no preceding element) → NONE, valid | unit | `MusicEditOperationsMutationTest.HasEnclosingRepeatRules.testFirstLineNoRepeatIsValid` (exercises song-start path indirectly) | adequate | None |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — checkPrecedingElement: preceding is content element, selection starts with SINGLE_BARLINE or REPEAT_LEFT → NONE | unit | none | missing | Add test: preceding content + selection starts with barline/repeat → NONE action |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — checkPrecedingElement: preceding is content element, selection starts with note → INSERT_BARLINE | unit | none (only tested via `makeFirstSecondEnding`) | inadequate | `canMakeFirstSecondEnding` predicate is never directly asserted to return `PrecedingAction.INSERT_BARLINE`; tests only pass a pre-built result to `makeFirstSecondEnding` |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — checkPrecedingElement: preceding is SINGLE_BARLINE or REPEAT_LEFT → EXTEND_SPAN | unit | none | missing | Add test |
| `MusicEditOperations` | `canMakeFirstSecondEnding()` — checkPrecedingElement: preceding is right-repeat/double-barline/final → invalid | unit | none | missing | Add test |
| `MusicEditOperations` | `makeFirstSecondEnding()` — null state guard | unit | none | missing | Add test: no mutation emitted |
| `MusicEditOperations` | `makeFirstSecondEnding()` — INSERT_BARLINE: inserts barline, adjusts span bounds, adds Ending | unit | `MusicEditOperationsMutationTest.testMakeFirstSecondEndingEmitsElementInsertionAndRangeElementAddition` | adequate | None |
| `MusicEditOperations` | `makeFirstSecondEnding()` — NONE: no barline inserted, adds Ending directly | unit | `MusicEditOperationsMutationTest.testMakeFirstSecondEndingWithExistingLeadingBarlineEmitsOnlyRangeElementAddition` | adequate | None |
| `MusicEditOperations` | `makeFirstSecondEnding()` — EXTEND_SPAN: span already extended (no insertion), adds Ending | unit | none | missing | Add test: verify only one `RangeElementAddition` emitted with correct span bounds when action is `EXTEND_SPAN` |
| `MusicEditOperations` | `canToggleTrill()` delegates to `state.canToggleTrill()`; returns false when state null | unit | none | missing | Add test: null state → false |
| `MusicEditOperations` | `toggleTrill()` — null state guard | unit | none | missing | Add test: no mutation emitted |
| `MusicEditOperations` | `toggleTrill()` — no existing trills → add single trill | unit | `MusicEditOperationsMutationTest.testToggleTrillEmitsRangeElementAddition` | adequate | None |
| `MusicEditOperations` | `toggleTrill()` — one overlapping trill exists → remove it | unit | `MusicEditOperationsMutationTest.testToggleTrillOffResultsInNoTrill` | adequate | None |
| `MusicEditOperations` | `toggleTrill()` — multiple overlapping trills exist → remove all in one bracket | unit | none | missing | Add test: two trills whose spans overlap the selection are both removed in a single notification |
| `MusicEditOperations` | `canFlipStemDirection()` delegates to `state.canFlipStemDirection()`; returns false when state null | unit | none | missing | Add test: null state → false |
| `MusicEditOperations` | `flipStemDirection()` — null state guard: shows info dialog | unit | none | missing | Add test: null selection shows `OptionDialogs.showInfoMessage` and emits no mutation |
| `MusicEditOperations` | `flipStemDirection()` — rest elements in selection are skipped (no mutation emitted for rests) | unit | none | missing | Add test: selection containing a rest; verify rest emits no `ElementModification`, only note does |
| `MusicEditOperations` | `flipStemDirection()` — unbeamed notes: flips each individually | unit | `MusicEditOperationsMutationTest.testFlipStemDirectionEmitsElementModificationPerAffectedIndex`, `BeamToggleTest.FlipStemDirection.testFlipStemUnbeamedWithPersistence` | adequate | None |
| `MusicEditOperations` | `flipStemDirection()` — beamed notes: flips whole beam group together (single pass per group) | unit | `BeamToggleTest.FlipStemDirection.testFlipStemWhileBeamedChangesDirection` | adequate | None |
| `MusicEditOperations` | `flipStemDirection()` — deduplication: beam group partially inside selection flipped only once | unit | none | missing | Add test: selection spanning only part of a beam group; group flipped once, not per-selected-note |
| `MusicEditOperations` | `flipStemDirection()` — tie partners outside selection are also flipped | unit | none | missing | Add test: note with a tie whose partner is outside the selection; partner's stem is also flipped |
| `MusicEditOperations` | `canChangeTempo()` delegates to `coordinator.canChangeTempo()` | unit | none | missing | Add test: verifies delegation; trivial but needed to guard the null state |
| `MusicEditOperations` | `setSong()` — replaces the song field (allows reuse across document loads) | none | `ScoreViewSetFontsTest` indirectly via `ScoreView.setSong()` (production path, not a direct test) | none | Trivial setter; no behavioral logic |

**Notes:**

The existing `MusicEditOperationsMutationTest` is the strongest part of the suite: it covers all five `toggleTuplet` branches correctly, the two beaming branches, both tie branches, both dynamics-add branches, and the main validation paths for `canMakeFirstSecondEnding`. The mutation record fields are asserted precisely (anchor/end index, grade, line identity), so these are genuinely adequate tests, not just type-checks.

The highest-risk gaps cluster in two areas. First, every operation's null-state guard is untested — all six operations silently return or show a dialog when `state == null`, but no test ever exercises this. The `flipStemDirection` null path is especially risky because it invokes `OptionDialogs.showInfoMessage`, a side effect that is invisible if the test never runs. Second, the `canMakeFirstSecondEnding` predicate's four `checkPrecedingElement` branches — EXTEND_SPAN, NONE with barline at start, INSERT_BARLINE (directly asserted), and invalid preceding element — are dark: the test suite only passes pre-built `EndingValidationResult` objects into `makeFirstSecondEnding`, so the predicate's logic is tested only in the `HasEnclosingRepeatRules` / `CanMakeFirstSecondEndingWithRepeatLeftRightSplit` groups, not for all `checkPrecedingElement` outcomes. The `makeFirstSecondEnding` EXTEND_SPAN branch is also completely untested.

Three tests are marked inadequate for weak assertions: `testRemoveDynamicsEmitsRemovalPerSpan` uses `isNotEmpty()` when it should assert exact counts (one crescendo removal, one diminuendo removal); `testHandleRemoveDynamicsEmitsRemovals` in `ScoreViewControllerCommandHandlerTest` also only checks `isNotEmpty()`; and the INSERT_BARLINE path of `checkPrecedingElement` is never directly asserted — the `canMakeFirstSecondEnding()` return value is not examined in those tests, only `makeFirstSecondEnding()` is called with a hand-crafted result.

**Tally:** 69 rows — 28 adequate · 37 missing · 3 inadequate · 0 wrong-level · 1 none · 0 redundant. (Null-state guards are classified `unit`/`missing` in the table — each is a real guard branch whose removal would NPE — not `none`.)

**Dead code:** none found. All private helpers (`validateEndingStructure`, `validateEndingRegionContent`, `hasOverlap`, `hasEnclosingRepeat`, `checkPrecedingElement`, `getDynamicsFromSelection`) are referenced from `canMakeFirstSecondEnding` and `removeDynamicsFromSelection`/`canRemoveDynamicsFromSelection` respectively. All public methods are referenced from `ScoreViewController` or tests.

**Production observations:** `flipStemDirection()` shows a dialog (`OptionDialogs.showInfoMessage`) when `state == null`, while every other operation silently returns. This inconsistency suggests either the dialog branch is dead in practice (the UI gate should prevent invoking the method with no selection) or the dialog is intentional UX feedback, but the asymmetry with all sibling operations is a smell worth reviewing.

### 11E — Appearance & Dialog Helpers (top-level `ui`)

| Class | Behavior | Required level | Existing test | Verdict | Action |
|---|---|---|---|---|---|
| `OptionDialogs` | `showInfoMessage` — pure pass-through to `showMessageDialog`; no return value, no branching | none | `DialogsTest.testShowInfoMessageDelegatesToJOptionPane` | none | no action |
| `OptionDialogs` | `showWarningMessage` — pure pass-through to `showMessageDialog`; no return value, no branching | none | none | none | no action |
| `OptionDialogs` | `showErrorMessage` — pass-through; beep before display (error path) | none | `DialogsTest.testShowErrorMessageDelegatesToJOptionPane` | none | no action |
| `OptionDialogs` | `showErrorMessageWithString` — pure pass-through; no return mapping | none | none (covered by delegation from `showErrorMessage`) | none | no action |
| `OptionDialogs` | `showConfirmDialog` — maps `CLOSED_OPTION` to `CANCEL_OPTION` when `optionType == YES_NO_CANCEL_OPTION` | unit | `DialogsTest.testShowConfirmDialogTranslatesClosedOptionToCancelForYesNoCancelOption` (unit + e2e `DialogsTest.testCloseWithYesNoCancelOptionReturnsCancelOption`) | adequate | — |
| `OptionDialogs` | `showConfirmDialog` — maps `CLOSED_OPTION` to `NO_OPTION` when `optionType == YES_NO_OPTION` | unit | `DialogsTest.testShowConfirmDialogTranslatesClosedOptionToNoForYesNoOption` (unit + e2e `DialogsTest.testCloseWithYesNoOptionReturnsNoOption`) | adequate | — |
| `OptionDialogs` | `showConfirmDialog` (5-arg) — suppressed default is `NO_OPTION` | unit | `DialogsTest.testShowConfirmDialogReturnsNoOptionByDefault` | adequate | — |
| `OptionDialogs` | `showConfirmDialog` (6-arg) — suppressed default is caller-supplied value | unit | `DialogsTest.testShowConfirmDialogReturnsSuppressedDefault` | adequate | — |
| `OptionDialogs` | `showInputDialog` — returns user-typed string | unit | `DialogsTest.testShowInputDialogReturnsUserInput` (unit + e2e `DialogsTest.testReturnsTypedText`) | adequate | — |
| `OptionDialogs` | `showInputDialog` — `UNINITIALIZED_VALUE` maps to `null` (cancel) | unit | `DialogsTest.testShowInputDialogReturnsCancelAsNull` | adequate | — |
| `OptionDialogs` | `showInputDialog` (3-arg) — suppressed default is `null` | unit | `DialogsTest.testShowInputDialogReturnsNullByDefault` | adequate | — |
| `OptionDialogs` | `showInputDialog` (4-arg) — suppressed default is caller-supplied string | unit | `DialogsTest.testShowInputDialogReturnsSuppressedDefault` | adequate | — |
| `OptionDialogs` | `showOptionDialog` — `getOptionPaneResult` with options array: returns array index of clicked option, `CLOSED_OPTION` when no match | unit | `DialogsTest` (WhenSuppressed only asserts `CLOSED_OPTION` on suppressed path; e2e `DialogsTest.testReturnsIndexOfClickedOption` covers the live index-mapping path) | adequate | — |
| `OptionDialogs` | `showOptionDialog` — suppressed returns `CLOSED_OPTION` | unit | `DialogsTest.testShowOptionDialogReturnsClosedOption` — passes raw strings `"Title"`/`"Message"` instead of `Strings.*` keys; harmless since suppressed path returns before `Strings.get()`, but violates project convention | inadequate | Replace `"Title"` / `"Message"` with valid `Strings.*` key constants |
| `OptionDialogs` | Suppression side-effect: `show*Message` does not construct `JOptionPane` when suppressed | unit | `DialogsTest.testShowErrorMessageDoesNotShowDialog`, `testShowInfoMessageDoesNotShowDialog` | adequate | — |
| `EndingConfirms` | `confirmInvalidation` — returns `true` (proceed) when user clicks Yes (index 0), `false` when dialog returns anything else | unit | `EndingConfirmsTest` — covered indirectly through full `SelectionCoordinator`/`ScoreViewController` integration; `simulateYes()` stubs `showOptionDialog` returning 0, default (no stub) returns 0 from suppressed `CLOSED_OPTION` which ≠ 0, so false branch covered | adequate | — |
| `EndingConfirms` | `confirmCompensateEnd` — selects `CONFIRM_ENDING_SPLIT_RIGHT_TO_LEFT_RIGHT` key when `newEndType == REPEAT_RIGHT`, otherwise `CONFIRM_ENDING_SPLIT_LEFT_RIGHT_TO_RIGHT` | unit | `EndingConfirmsTest` exercises both branches indirectly (primary line → REPEAT_RIGHT path; secondary line → other path) | adequate | — |
| `EndingConfirms` | `confirmCompensateSplit` — selects `CONFIRM_ENDING_END_TO_REPEAT_REQUIRES_LEFT_RIGHT_SPLIT` key when `newSplitType == REPEAT_LEFT_RIGHT`, otherwise `CONFIRM_ENDING_END_TO_BARLINE_REQUIRES_RIGHT_SPLIT` | unit | `EndingConfirmsTest` exercises both branches indirectly | adequate | — |
| `EndingConfirms` | `applyCompensatingEndChange` / `applyCompensatingSplitChange` — applies element substitution; null guard on `targetEl` skips silently | unit | `EndingConfirmsTest` exercises happy path; null `targetEl` guard is never directly tested | missing | Add a unit test for the `targetEl == null` early-return in `applyCompensatingChange` |
| `EndingConfirms` | `typeNameFor` — maps `ElementType` to display string key; four branches (`REPEAT_RIGHT`, `REPEAT_LEFT_RIGHT`, `REPEAT_LEFT`, default=barline) | unit | Exercised implicitly via `confirmCompensateSplit` during `EndingConfirmsTest`, but which branch fires depends on fixture — `REPEAT_LEFT` branch may not be reachable via current fixtures | missing | Add a direct unit test for `typeNameFor` covering all four `ElementType` branches |
| `AppearanceManager` | `createLaf(true)` — returns `FlatMacDarkLaf` on macOS, `FlatDarkLaf` elsewhere | unit | `AppearanceManagerTest.CreateLaf.testDarkReturnsCorrectLafClass` | adequate | — |
| `AppearanceManager` | `createLaf(false)` — returns `FlatMacLightLaf` on macOS, `FlatLightLaf` elsewhere | unit | `AppearanceManagerTest.CreateLaf.testLightReturnsCorrectLafClass` | adequate | — |
| `AppearanceManager` | `resolveIsDark(DARK)` → `true` | unit | `AppearanceManagerTest.ResolveIsDark.testDarkPreferenceReturnsTrue` | adequate | — |
| `AppearanceManager` | `resolveIsDark(LIGHT)` → `false` | unit | `AppearanceManagerTest.ResolveIsDark.testLightPreferenceReturnsFalse` | adequate | — |
| `AppearanceManager` | `resolveIsDark(SYSTEM)` — delegates to `OsThemeDetector.isDark()` | unit | `AppearanceManagerTest.ResolveIsDark.testSystemPreferenceDelegatesToOsDetector` | adequate | — |
| `AppearanceManager` | `resolveIsDark(SYSTEM)` — falls back to `false` when detector throws | unit | `AppearanceManagerTest.ResolveIsDark.testSystemPreferenceFallsBackToLightOnDetectorFailure` | adequate | — |
| `AppearanceManager` | `init` — installs LAF from preference and registers OS listener when `SYSTEM` | unit | `AppearanceManagerTest.Init.*` (three tests) | adequate | — |
| `AppearanceManager` | `init` — throws `IllegalStateException` when `installLaf` fails | unit | none | missing | Add test: stub `installLaf` to throw `UnsupportedLookAndFeelException`; assert `init()` throws `IllegalStateException` |
| `AppearanceManager` | `switchTheme` — no-op when new preference equals current | unit | `AppearanceManagerTest.SwitchTheme.testNoOpWhenPreferenceUnchanged` | adequate | — |
| `AppearanceManager` | `switchTheme` — calls LAF ops in order: `showSnapshot → installLaf → updateUI → hideSnapshotWithAnimation` | unit | `AppearanceManagerTest.SwitchTheme.testSwitchCallsLafOpsInOrder` | adequate | — |
| `AppearanceManager` | `switchTheme` — saves new preference before attempting the switch | unit | `AppearanceManagerTest.SwitchTheme.testSwitchSavesNewPreference` | adequate | — |
| `AppearanceManager` | `switchTheme` — reverts preference when `installLaf` throws | unit | none | missing | Add test: stub `installLaf` to throw; assert that `Prefs.put` is called a second time with the *old* preference key to revert |
| `AppearanceManager` | `switchTheme` — registers OS listener when switching to `SYSTEM` | unit | `AppearanceManagerTest.SwitchTheme.testSwitchToSystemRegistersOsListener` | adequate | — |
| `AppearanceManager` | `switchTheme` — unregisters OS listener when switching away from `SYSTEM` | unit | `AppearanceManagerTest.SwitchTheme.testSwitchFromSystemUnregistersOsListener` | adequate | — |
| `AppearanceManager` | `registerOsListener` — guard against double-registration (`listenerRegistered` flag) | unit | none | missing | Add test: call `switchTheme(SYSTEM)` twice; verify `registerListener` is called only once |
| `AppearanceManager` | `getPreference` — reads `PrefsKey.APPEARANCE` and delegates to `Appearance.fromKey` | unit | covered implicitly by all `SwitchTheme` tests that stub `Prefs.getString` | adequate | — |
| `Appearance` | `fromKey` — returns matching enum constant for each valid key | unit | none (only used indirectly inside `AppearanceManagerTest` via `AppearanceManager.getPreference`) | missing | Add a focused unit test for `Appearance.fromKey` covering all three valid keys and the unknown-key fallback to `SYSTEM` |
| `Appearance` | `fromKey` — unknown key falls back to `SYSTEM` | unit | none | missing | (same test as above — covered as one row) |
| `Appearance` | `key()` — returns the string key for each enum constant | none | — | none | Pure data accessor |
| `LafOperations` | Interface definition — no logic | none | — | none | Package-private interface; only behavior is in `DefaultLafOperations` inside `AppearanceManager`, exercised through `AppearanceManagerTest` via mock injection |

**Notes:**

The highest-risk gap is the `switchTheme` preference-revert path: when `installLaf` throws, the production code calls `Prefs.put(currentPreference.key())` to roll back the optimistic write, but no test verifies this. A silent regression here would leave the pref file permanently out of sync with the actually-installed LAF. The missing `registerOsListener` double-registration guard test is lower risk but still behaviorally important — repeated calls to `switchTheme(SYSTEM)` would silently register multiple listeners without it. The `init` failure path (throws `IllegalStateException`) is also untested; callers that expect a well-defined error contract would be surprised if the message changed.

`AppearanceManagerTest` is largely adequate for the happy path and covers the LAF-ops ordering, OS listener registration/unregistration, and `resolveIsDark` fallback. Its main weakness is that it never exercises the error-recovery branches (installLaf failure in both `init` and `switchTheme`). The missing `Appearance.fromKey` test is minor but easy to add; the enum is only a few lines and the fallback-to-`SYSTEM` behavior is load-bearing for the prefs system.

`OptionDialogs` methods that are pure `void` pass-throughs (`showInfoMessage`, `showWarningMessage`) carry no return-value mapping and correctly receive `none` verdicts. All the `showConfirmDialog` and `showInputDialog` return-mapping behaviors are well covered at both unit and e2e levels. The one inadequacy is `testShowOptionDialogReturnsClosedOption`, which passes raw string literals `"Title"`/`"Message"` instead of `Strings.*` keys — the test passes only because suppression fires before `Strings.get()` is reached, masking the convention violation. `EndingConfirms` is adequately covered at the integration level by `EndingConfirmsTest`; the missing items are isolated unit-level gaps (`typeNameFor` branches, `targetEl == null` null-guard) rather than whole-behavior holes.

**Tally:** 40 rows — 26 adequate · 7 missing · 1 inadequate · 0 wrong-level · 6 none · 0 redundant.

**Dead code:** none found. `LafOperations` is package-private and used by `AppearanceManager` and `AppearanceManagerTest`; all methods in all five classes have callers in `src/main` or `src/test`.

**Production observations:** In `DialogsTest.WhenSuppressed.testShowOptionDialogReturnsClosedOption`, the test passes raw string literals `"Title"` and `"Message"` as `titleKey`/`messageKey` arguments to `showOptionDialog`, which expects `Strings.*` key constants. The test works because the suppressed path returns before `Strings.get()` is called, but the convention violation is a latent correctness risk if suppression is ever removed or refactored. This is the only instance of the anti-pattern in the test suite.

### 11F — Display & Constants (top-level `ui`)

| Class | Behavior | Required level | Existing test | Verdict | Action |
|---|---|---|---|---|---|
| `KeySignatureDisplay` | `tonicFor`: returns correct tonic string for each SHARPS key (0–7) | unit | none | missing | Test all 8 SHARPS entries against `SHARP_TONICS` table |
| `KeySignatureDisplay` | `tonicFor`: returns correct tonic string for each FLATS key (0–7) | unit | none | missing | Test all 8 FLATS entries against `FLAT_TONICS` table |
| `KeySignatureDisplay` | `suffixFor`: returns empty string when `KeyType.NONE` or count == 0 | unit | none | missing | Verify both `NONE`-type and zero-count paths return `""` |
| `KeySignatureDisplay` | `suffixFor`: returns non-empty suffix containing count for SHARPS | unit | none | missing | Check suffix for SHARPS count > 0 contains the count and right plural form |
| `KeySignatureDisplay` | `suffixFor`: returns non-empty suffix containing count for FLATS | unit | none | missing | Check suffix for FLATS count > 0 contains the count and right plural form |
| `KeySignatureDisplay` | `tonicHasAccidental`: returns false for FLATS count < 2, true for count >= 2 | unit | none | missing | Boundary at `MIN_FLAT_COUNT_WITH_ACCIDENTAL` = 2 |
| `KeySignatureDisplay` | `tonicHasAccidental`: returns false for SHARPS count < 6, true for count >= 6 | unit | none | missing | Boundary at `MIN_SHARP_COUNT_WITH_ACCIDENTAL` = 6 |
| `KeySignatureDisplay` | `tonicHasAccidental`: returns false for `KeyType.NONE` regardless of count | unit | none | missing | NONE branch must return false even with a nonzero count |
| `KeySignatureDisplay` | `getDisplayName` with count == 0 / NONE type: returns `AttributedString` over empty string | unit | none | missing | Empty-string guard path (lines 61–63) |
| `KeySignatureDisplay` | `getDisplayName` with a key that has NO tonic accidental: applies single label font only | unit | none | missing | E.g. SHARPS/1 (G major) — no secondary font attribute ranges |
| `KeySignatureDisplay` | `getDisplayName` with a key that HAS a tonic accidental: applies letter-gap tracking + glyph font at correct indices | unit | none | missing | E.g. FLATS/3 (E♭ major) — verify font attribute ranges on the correct character positions |
| `Constants` | All fields are pure compile-time string/value constants (no logic) | none | none | none | Pure constants holder — no testable behavior |
| `Control` | `MOUSE.getDescription()` returns the string for `ACTION_CONTROL_MOUSE` | unit | none | missing | Needs `installFlatLafDefaults`; assert description is non-blank and matches Strings key |
| `Control` | `KEYBOARD.getDescription()` returns the string for `ACTION_CONTROL_KEYBOARD` | unit | none | missing | Parallel to MOUSE case |
| `Mode` | `isAdjustmentMode()` returns true for `ADJUSTMENT` and `VERTICAL_ADJUSTMENT` | unit | none | missing | Both adjustment variants must satisfy predicate |
| `Mode` | `isAdjustmentMode()` returns false for `SELECT` and `EDIT` | unit | none | missing | Non-adjustment variants must not satisfy predicate |
| `FlatLafProps` | `get`: throws `RuntimeError` when key is absent from UIManager | unit | none | missing | Set up a mock UIManager or install FlatLaf without the key; assert exit is called |
| `FlatLafProps` | `get`: returns typed value when key is present | unit | none | missing | Install a known property; assert returned value equals expected with correct type |

**Notes:**

`KeySignatureDisplay` is the highest-risk gap. It contains two parallel lookup tables (`FLAT_TONICS`, `SHARP_TONICS`), two threshold constants (`MIN_FLAT_COUNT_WITH_ACCIDENTAL` = 2, `MIN_SHARP_COUNT_WITH_ACCIDENTAL` = 6), and `AttributedString` font-attribute range logic — all pure computation with zero test coverage. An off-by-one in either threshold or a wrong glyph index in the accidental-font assignment would be invisible until the key-signature picker renders incorrectly on screen. `tonicFor` and `tonicHasAccidental` are `private` static methods, but they are fully exercisable through the public `getDisplayName` method — the private helpers are the real test targets, accessed indirectly. The `getDisplayName` tests that inspect `AttributedString` attribute ranges will need `installFlatLafDefaults()` (from `UnitTest`) because the method calls `MyFontUtils.getUIFont("Label.font")` and `RenderingUtils.getMusicFont()`.

`Mode.isAdjustmentMode()` is used in at least four production call sites across `LineComponent`, `ModeCycleButton`, `UIAction`, and `CycleModeAction`, yet has no direct unit test. The logic is a two-constant OR (`this == ADJUSTMENT || this == VERTICAL_ADJUSTMENT`) and is trivially testable; omitting a test means the method could silently be broken by an enum refactor that renames or adds values. `Control.getDescription()` likewise dispatches a `switch` over two constants to `Strings.get()`; a straightforward two-case test suffices.

`Constants` is a pure string-constants holder (`none`). `FlatLafProps` contains a single method with real logic — a null guard and a typed unchecked cast — which warrants two unit tests. The class is referenced across 66 production call sites, so silent misbehavior (wrong null-check path, or a cast exception from a wrong witness) would be broadly impactful. The missing-key throw path in particular is untested. `FlatLafProps` is not a constants holder in the rubric sense: it has a method body with branching, so `none` would be wrong.

**Tally:** 18 rows — 0 adequate · 17 missing · 0 inadequate · 0 wrong-level · 1 none · 0 redundant.

**Dead code:** `Constants.ACCELERATOR_KEYS` and `Constants.SONG_SCRIBE_JAR` have zero references outside their own definition file in both `src/main` and `src/test`.

**Production observations:** `Constants.NON_BREAKING_HYPHEN` is assigned `Character.toString('­')`, which is U+00AD SOFT HYPHEN — a zero-width formatting character that browsers and many renderers treat as invisible. The true NON-BREAKING HYPHEN is U+2011. This naming/value mismatch may cause ABC export (`ExportABCAction`) to silently fail to replace what it believes are non-breaking hyphens in lyric syllables, since any lyrics actually containing U+2011 would not match the constant. Whether lyrics in practice ever contain U+00AD vs U+2011 determines the real-world impact.

### §11 summary

**228 behavior rows: 194 testable / 34 none; of 194 testable, 63 adequate · 126 missing · 5 inadequate · 0 wrong-level · 0 redundant (~68% dark).** Two well-covered islands sit in an otherwise dark periphery: §11E (appearance/dialog helpers — `OptionDialogs` return-mapping via `DialogsTest` at unit *and* e2e, plus `AppearanceManager` LAF/theme state via `AppearanceManagerTest`, 26/40 adequate) and the mutation core of §11D (`MusicEditOperations` — `MusicEditOperationsMutationTest` asserts mutation-record fields precisely, 28/69 adequate). Everything else is largely untested.

**Defining gap — null-state guards and thin-dispatcher action bodies, recurring from Sessions 5/6.** In `MusicEditOperations` every operation's null-`activeSelection` guard is dark (six operations). In `ui/playback` all four `*Action.actionPerformed` bodies (icon toggle / `Command` post / direct controller call) are untested — the same "action posts a `Command`, only the downstream handler is tested" pattern flagged across §5. The lesson holds: the dispatch itself is never exercised.

**Riskiest dark computation:** (a) `PlaybackController` transport state machine — STOPPED/PAUSED/PLAYING, six transition paths (`togglePlayPause` alone has four), and `handleMetaMessage` binary line/note index decoding — covered only by its four `selectionDidChange` tests; (b) `MusicEditOperations.canMakeFirstSecondEnding`'s `checkPrecedingElement` branches (INSERT_BARLINE / EXTEND_SPAN / NONE / invalid) are never asserted at the predicate level — tests only feed pre-built `EndingValidationResult`s into `makeFirstSecondEnding`, leaving the `EXTEND_SPAN` makeFirstSecondEnding arm dark too; (c) `MenuController.buildLabels`/`disambiguate`/`tildeSubstitute` Open-Recent label disambiguation (multi-branch, zero coverage); (d) `KeySignatureDisplay` parallel tonic tables + accidental-count thresholds (2 flats / 6 sharps) + `AttributedString` font-attribute ranges; (e) `MidiController.setPlaybackVolume` 50–100→64–127 scaling and clamps, and `PlayThread.sendNoteOn`/`sendNoteOff`.

**Menu/platform are mostly wiring (`none`), as predicted.** §11A is 15/37 `none` (declarative submenu construction) with the real logic concentrated in `MenuController`; §11C is 7/15 `none` (the NS* classes are pure Rococoa native pass-throughs that cannot be unit-asserted without a live macOS runtime), with all real logic in `MacNativeMenuController` — the `dialogVisibilityDidChange` receiver (the controller's whole runtime purpose) and the 3-branch `discoverNativeItems`, both dark. `BaseDialogCounterTest` covers the *sender* side of `DialogVisibilityDidChangeNotification`; the *receiver* side here is untested.

**inadequate (5):** `PlayPauseAction` DISABLE_WHEN_PLAYING (the audit test only checks `DISABLE_WHEN_EDITING_TEXT`, never that the pause button stays enabled during playback); `MusicEditOperations` ×3 — `toggleTuplet` `size==0` removal branch never exercised (only the grade-match path), and `removeDynamicsFromSelection` asserted with `isNotEmpty()` instead of exact removal counts in both `MusicEditOperationsMutationTest` and `ScoreViewControllerCommandHandlerTest`, and the `checkPrecedingElement`→INSERT_BARLINE result never directly asserted via `canMakeFirstSecondEnding`; plus a test-side convention violation in `DialogsTest.testShowOptionDialogReturnsClosedOption` (raw `"Title"`/`"Message"` literals instead of `Strings.*` keys, masked by the suppressed early return).

**Dead code (verified zero refs):** §11A — `FermataMenuItem` (whole class, superseded by `FermataAction`) and `MenuController.initHelpMenu`/`addCommonHelpItems`/`initLaunchMenu` (call sites commented out). §11C — `NSMenu._Class.alloc()`, `NSMenuItem._Class.alloc()`, `NSMenu.CLASS`, `NSMenuItem.CLASS`, `NSMenu.title()`, `NSMenu.itemWithTitle()`, `NSMenuItem.isEnabled()` (speculative Rococoa scaffolding). §11F — `Constants.ACCELERATOR_KEYS`, `Constants.SONG_SCRIBE_JAR` (dead fields). §11B/D/E — none.

### §11 production observations (filed as GitHub issue #416)

1. **(real bug — highest severity)** `Constants.NON_BREAKING_HYPHEN` is assigned `Character.toString('­')` = U+00AD SOFT HYPHEN, a zero-width formatting character, not the true NON-BREAKING HYPHEN U+2011. `ExportABCAction` may silently fail to escape genuine U+2011 hyphens in lyric syllables (and conversely treats soft hyphens as non-breaking). Real-world impact depends on whether lyrics ever contain U+2011 vs U+00AD. (11F)
2. **(dead code → delete in remediation)** `FermataMenuItem` (superseded by `FermataAction`) and the three never-called `MenuController` methods `initHelpMenu`/`addCommonHelpItems`/`initLaunchMenu` (their call sites in `initMenus` are commented out). (11A)
3. **(testability)** `MenuController.buildLabels` is `private static` despite 30+ lines of path-disambiguation logic; widen to package-private to unit-test the Open-Recent label logic without reflection. (11A)
4. **(design smell)** `MusicEditOperations.flipStemDirection` shows a user-facing info dialog when `state == null`, while every sibling operation silently returns on the same condition. Either the dialog branch is effectively dead (the UI gate prevents calling with no selection) or it is intentional UX feedback — the inconsistency warrants review. (11D)
5. **(speculative Rococoa scaffolding)** The NS* unused members in observation/dead-code above appear copied from a Rococoa template (`CLASS` fields carry `@SuppressWarnings("unused")` but the `_Class.alloc()`/`title()`/`itemWithTitle()`/`isEnabled()` members do not). Separately, `MacNativeMenuController`'s `@Nullable private static` strong-reference anchor on `MenuController` is an *intentional* deviation from the singleton guide to satisfy MBassador's weak-reference rule — not a bug. (11C)
6. **(readability / style)** `PlaybackController.setSequenceToPlayFromSelection` uses a `//noinspection ObjectEquality` identity comparison on `Sequence` (correct but easy to misread — add a clarifying comment); `PlayThread` extends `Thread` rather than implementing `Runnable`; `PlayThread.setupInstrument` throws `RuntimeError.exit` on a null receiver in a path that `sendNoteOn` already guards with a silent null return, making the fatal branch unreachable but confusing. (11B)
7. **(dead fields)** `Constants.ACCELERATOR_KEYS` and `Constants.SONG_SCRIBE_JAR` have zero references in `src/main` or `src/test`. (11F)

