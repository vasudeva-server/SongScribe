### 11A — `ui/menu` (Menu Construction & Controller)

| Class | Behavior | Required level | Existing test | Verdict | Action | done |
|---|---|---|---|---|---|---|
| `MenuController` | `buildLabels` — unique filenames: each path returns its filename as label | unit | none | missing | Add `MenuControllerTest.testBuildLabelsUniqueFilenames` — pass list of distinct filenames, assert each label equals the filename | ✅ |
| `MenuController` | `buildLabels` — duplicate filenames: appends shortest unique parent suffix to disambiguate | unit | none | missing | Add `testBuildLabelsDuplicateFilenames` — two paths with same filename, different parent dirs; assert label = `filename — parentDir` | ✅ |
| `MenuController` | `buildLabels` — duplicate filenames requiring multiple depth levels: falls back to deeper suffix when depth-1 parent is also identical | unit | none | missing | Add `testBuildLabelsTwoLevelDisambiguation` | ✅ |
| `MenuController` | `buildLabels` — all-duplicate fallback: uses full path with `~` substitution when no depth resolves uniqueness | unit | none | missing | Add `testBuildLabelsFallbackToFullPath` | ✅ |
| `MenuController` | `tildeSubstitute` — path under home directory replaced with `~/...` | unit | none | missing | Add `testTildeSubstituteUnderHome` | ✅ |
| `MenuController` | `tildeSubstitute` — path outside home directory returned unchanged | unit | none | missing | Add `testTildeSubstituteOutsideHome` | ✅ |
| `MenuController` | `tildeSubstitute` — path exactly equal to home directory returns `~` | unit | none | missing | Add `testTildeSubstituteExactlyHome` | ✅ |
| `MenuController` | `rebuildOpenRecentMenu` — empty recents list: menu contains a single disabled "No recent documents" item | unit | none | missing | Add `testRebuildOpenRecentMenuEmpty` — call `rebuildOpenRecentMenu` via reflection (or extract to package-private); assert item count = 1, disabled | ✅ |
| `MenuController` | `rebuildOpenRecentMenu` — non-empty recents list: menu contains one item per path + separator + Clear Recents action | unit | none | missing | Add `testRebuildOpenRecentMenuNonEmpty` | ✅ |
| `MenuController` | `recentDocumentsDidChange` handler rebuilds the open-recent menu when the MBassador notification fires | unit | none | missing | Add `testRecentDocumentsDidChangeRebuildsMen` — post `RecentDocumentsDidChangeNotification` via `MessageCenter`, assert menu is updated | ✅ |
| `MenuController` | `initFileMenu` — non-macOS: Quit action is present in file menu | unit | none | missing | Add `testQuitActionPresentOnNonMac` (mock `SystemInfo.isMacOS = false`) | ✅ |
| `MenuController` | `initFileMenu` — macOS: Quit action is absent from file menu | unit | none | missing | Add `testQuitActionAbsentOnMac` (mock `SystemInfo.isMacOS = true`) | ✅ |
| `MenuController` | `initEditMenu` — non-macOS: Preferences action is present in edit menu | unit | none | missing | Add `testPreferencesActionPresentOnNonMac` | ✅ |
| `MenuController` | `initEditMenu` — macOS: Preferences action is absent from edit menu | unit | none | missing | Add `testPreferencesActionAbsentOnMac` | ✅ |
| `MenuController` | `initMenus` — macOS: `setJMenuBar` is called on `mainFrame`; non-macOS: it is not | unit | none | missing | Add `testJMenuBarSetOnMacOnly` — two cases, mock `SystemInfo.isMacOS` | ✅ |
| `MenuController` | `initHelpMenu` / `addCommonHelpItems` (dead — commented out in `initMenus`) | none | none | none | N/A — unreachable code | — |
| `MenuController` | `initLaunchMenu` (dead — referenced only in commented-out code) | none | none | none | N/A — unreachable code | — |
| `NotationMenu` | Constructor wires all action groups into submenus in the expected order | none | none | none | Pure declarative wiring; no branching | — |
| `NotationMenu` | `menuSelected` listener: when a `ScoreView` with a controller is present, `MAKE_ENDING_ACTION.validate(ctrl)` is called | unit | none | missing | Add `NotationMenuTest.testMenuSelectedCallsValidateWhenControllerPresent` — construct `NotationMenu` with a mock frame; fire the `menuSelected` event; verify `MAKE_ENDING_ACTION.isEnabled()` reflects validation result | ✅ |
| `NotationMenu` | `menuSelected` listener: when `ScoreView` is null or has no controller, `MAKE_ENDING_ACTION` is disabled | unit | none | missing | Add `testMenuSelectedDisablesMakeEndingWhenNoController` | ✅ |
| `NotationMenu` | `createTupletMenu` — separator separates tuplet add-actions from remove action | none | none | none | Pure layout wiring | — |
| `NotationMenu` | `createDynamicsMenu` — all dynamic marking radio items added from `DYNAMIC_MARKING_ACTION_GROUP` | none | none | none | Pure declarative wiring | — |
| `BarlineMenu` | `FinalTerminalAction.createFinalDoubleBarline` — action fires and replaces terminal to `FINAL_DOUBLE_BARLINE` without showing a confirm dialog | unit | `BarlineMenuTest.testFinalDoubleBarlineItemReplacesTerminalWithoutConfirm` | adequate | — | — |
| `BarlineMenu` | `FinalTerminalAction.createFinalRightRepeat` — action fires and replaces terminal to `REPEAT_RIGHT` without showing a confirm dialog | unit | `BarlineMenuTest.testFinalRightRepeatItemReplacesTerminalWithoutConfirm` | adequate | — | — |
| `BarlineMenu` | Radio selection reflects current terminal: `FINAL_DOUBLE_BARLINE` selected, right-repeat unselected | unit | `BarlineMenuTest.testRadioSelectionReflectsCurrentTerminalForFinalBarline` | adequate | — | — |
| `BarlineMenu` | Radio selection reflects current terminal: right-repeat selected, final-double unselected | unit | `BarlineMenuTest.testRadioSelectionReflectsCurrentTerminalForRightRepeat` | adequate | — | — |
| `BarlineMenu` | Terminal items are in the same `ButtonGroup` — selecting one deselects the other | unit | none | missing | Add `testTerminalItemsAreMutuallyExclusive` — check that when you set one selected, the other becomes deselected via the shared `ButtonGroup` | ✅ |
| `BarlineMenu` | `BARLINE_ACTIONS` items are added as `JRadioButtonMenuItem`s before the separator | none | none | none | Pure declarative wiring | — |
| `FermataMenuItem` | Entire class — superseded by `FERMATA_ACTION` (`FermataAction`); has zero references in production code | none | none | none | Dead class — no tests warranted; should be deleted | — |
| `FermataMenuItem` | `actionPerformed` — selected: adds `FermataAttachment` to preview element | none | none | none | Logic duplicated by `FermataAction.applyToElement`; dead path | — |
| `FermataMenuItem` | `actionPerformed` — deselected: removes existing `FermataAttachment` from preview element | none | none | none | Logic duplicated by `FermataAction.applyToElement`; dead path | — |
| `AccidentalMenu` | Constructor: accidental radio items from `ACCIDENTAL_ACTION_GROUP` + separator + `ACCIDENTAL_IN_PARENS_ACTION` checkbox | none | none | none | Pure declarative wiring | — |
| `ArticulationMenu` | Constructor: `ACCENT_ACTION` checkbox first, then articulation radio items from `ARTICULATION_ACTION_GROUP` | none | none | none | Pure declarative wiring | — |
| `DotMenu` | Constructor: dot radio items from `DOT_ACTION_GROUP` | none | none | none | Pure declarative wiring | — |
| `DurationMenu` | Constructor: note duration radio items from `NOTE_DURATION_ACTIONS` | none | none | none | Pure declarative wiring | — |
| `GlissandoMenu` | Constructor: glissando + slide-out as radio items | none | none | none | Pure declarative wiring | — |
| `RepeatsMenu` | Constructor: repeat radio items from `REPEAT_ACTIONS` | none | none | none | Pure declarative wiring | — |

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

