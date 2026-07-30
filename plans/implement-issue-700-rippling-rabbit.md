# Issue 700: Clean up action classes

## Context

`ForceArticulationAction` and `DurationArticulationAction` are two `final` leaf
subclasses of the package-private abstract class `ArticulationAction`. Neither
has (or can have, being `final`) any subclass of its own — they exist only to
give the Accent and Staccato actions distinct type names, but they are
otherwise identical boilerplate: each has one factory method and a
constructor that forwards to `ArticulationAction` and sets an undo-op name
key. That type distinction is the only reason `ARTICULATION_ACTION_GROUP`
exists as an `ActionGroup<DurationArticulationAction>` — and it holds exactly
one member, `STACCATO_ACTION`. A one-member "group" provides no mutual
exclusion (there's nothing to exclude) and no other action group in the
codebase has only one member, so it's dead structure that other code has to
route through instead of just checking `STACCATO_ACTION` directly (which is
already how the sibling `ACCENT_ACTION` toggle is checked everywhere).

Separately, three per-note toggle actions — Accent, Breath Mark, and
Staccato — currently have no keyboard accelerator at all. The issue asks for
`>` (Accent), `,` (Breath Mark), and `<` (Staccato).

This plan removes the two subclasses (merging their factories directly onto
`ArticulationAction`), removes `ARTICULATION_ACTION_GROUP`, and adds the three
accelerators.

## 1. Merge `ForceArticulationAction` / `DurationArticulationAction` into `ArticulationAction`

File: `src/main/java/songscribe/ui/action/ArticulationAction.java`

- Change `abstract class ArticulationAction extends NoteOnlyAction` to
  `public final class ArticulationAction extends NoteOnlyAction`. It must be
  `public`: `SlideReflectionTest`, `SelectionApplyIntegrationTest`, and
  `ReflectionIntegrationTest` (all in `songscribe.ui.selection`) hold a field
  of this type directly.
- Add two `public static` factory methods, `createAccentAction(MainFrame)`
  and `createStaccatoAction(MainFrame)`, moved over from
  `ForceArticulationAction`/`DurationArticulationAction` respectively (same
  `ArticulationType`, name/icon/tooltip `Strings` keys, and action-command
  strings as today).
- Give the constructor two new parameters, `int virtualKey, int modifiers`,
  inserted before the existing `String undoOpNameKey`-setting logic, and
  forward them to `NoteOnlyAction`'s `virtualKey`/`modifiers` constructor
  overload (already present at `NoteOnlyAction.java:55-67`) instead of the
  no-accelerator overload it uses today. This is the same pattern
  `DotAction` and `ElementTypeAction` already use.
- Delete `src/main/java/songscribe/ui/action/ForceArticulationAction.java`
  and `src/main/java/songscribe/ui/action/DurationArticulationAction.java`.

## 2. Add the three accelerators

- Accent, in `ArticulationAction.createAccentAction`:
  `KeyEvent.VK_PERIOD, InputEvent.SHIFT_DOWN_MASK` (Shift+`.` = `>`).
- Staccato, in `ArticulationAction.createStaccatoAction`:
  `KeyEvent.VK_COMMA, InputEvent.SHIFT_DOWN_MASK` (Shift+`,` = `<`).
- Breath Mark, in `ElementTypeAction.createBreathMarkAction`
  (`src/main/java/songscribe/ui/action/ElementTypeAction.java:224-234`):
  change the existing `0, 0` accelerator args to `KeyEvent.VK_COMMA, 0`.

No conflicts: bare `.` is already `DotAction`'s single-dot accelerator and
Cmd+`.` is `RewindAction`'s, but Shift+`.` is unused; `VK_COMMA` (bare or
shifted) isn't used anywhere else in the codebase.

## 3. Remove `ARTICULATION_ACTION_GROUP`

File: `src/main/java/songscribe/ui/action/Actions.java`

- Field declarations (~line 123-125): change the types of `ACCENT_ACTION` and
  `STACCATO_ACTION` from `ForceArticulationAction`/`DurationArticulationAction`
  to `ArticulationAction`; delete the `ARTICULATION_ACTION_GROUP` field.
- `initialize()` (~line 279-281): change both factory calls to
  `ArticulationAction.createAccentAction(mainFrame)` /
  `ArticulationAction.createStaccatoAction(mainFrame)`; delete the
  `ARTICULATION_ACTION_GROUP = new ActionGroup<>(STACCATO_ACTION);` line.
- `clearNoteDecorations()` (~line 419-425): replace
  `ARTICULATION_ACTION_GROUP.clearSelection();` with
  `STACCATO_ACTION.setSelected(false);`, alongside the existing
  `ACCENT_ACTION.setSelected(false);`.

File: `src/main/java/songscribe/ui/edit/EditModeManager.java` (~line 280-288)

- Replace
  ```java
  var durationArticulationAction = Actions.ARTICULATION_ACTION_GROUP.getSelected();

  if (durationArticulationAction != null) {
      element.addArticulation(new Articulation(element, ArticulationType.STACCATO));
  }
  ```
  with the same direct-check style already used for Accent just above it:
  ```java
  if (Actions.STACCATO_ACTION.isSelected()) {
      element.addArticulation(new Articulation(element, ArticulationType.STACCATO));
  }
  ```

File: `src/main/java/songscribe/ui/menu/ArticulationMenu.java`

- Accent and Staccato are independent toggles (a note can carry both), not a
  mutually-exclusive choice, so the group's `JRadioButtonMenuItem` was never
  semantically correct — replace the loop over
  `Actions.ARTICULATION_ACTION_GROUP.getActions()` with a second
  `JCheckBoxMenuItem`, matching Accent:
  ```java
  add(new JCheckBoxMenuItem(Actions.ACCENT_ACTION));
  add(new JCheckBoxMenuItem(Actions.STACCATO_ACTION));
  ```

`ArticulationToolbar.java` needs no change — it already adds a
`ToolbarToggleButton` for each action directly, not through the group.

No other action group in `Actions.initialize()` has only one member (checked
`MODE_ACTION_GROUP` (2), `DURATION_ACTION_GROUP` (9), `DOT_ACTION_GROUP` (2),
`ACCIDENTAL_ACTION_GROUP` (5), `NON_DURATION_ACTION_GROUP` (6),
`DYNAMIC_MARKING_ACTION_GROUP` (6)), so no other group is in scope here.

## 4. Update tests

- Merge `src/test/java/songscribe/ui/action/ForceArticulationActionTest.java`
  and `.../DurationArticulationActionTest.java` into a single
  `src/test/java/songscribe/ui/action/ArticulationActionTest.java`, following
  the multi-variant convention already used by
  `src/test/java/songscribe/ui/action/DotActionTest.java` (one test class,
  one field per variant built in `@BeforeEach`/inline, flat test methods
  prefixed by variant name — `testAccent...` / `testStaccato...`). Covers all
  cases from both original files (apply/remove articulation, matches/doesn't
  match, and Staccato's duplicate-articulation-replacement case).
- Mechanical rename (class name only, behavior unchanged) in:
  - `src/test/java/songscribe/ui/action/EnableFromSelectionTest.java:171`
  - `src/test/java/songscribe/ui/action/LyricEditorActionAuditTest.java:91-92`
  - `src/test/java/songscribe/ui/selection/SlideReflectionTest.java:40,54,66`
  - `src/test/java/songscribe/ui/selection/SelectionApplyIntegrationTest.java:48,72,94`
  - `src/test/java/songscribe/ui/selection/ReflectionIntegrationTest.java:38,57,70`
- Replace group-based calls with direct-action calls (same semantics, since
  the group only ever wrapped `STACCATO_ACTION`) in:
  - `src/test/java/songscribe/ui/action/ActionsResetOnDocumentLoadTest.java:87,104`
  - `src/test/java/songscribe/ui/edit/EditModeManagerTest.java:90,229,265,294`
  - `src/test/java/songscribe/ui/edit/GraceModeManagerTest.java:1590,1620-1622,1649,1664`

  Pattern: `Actions.ARTICULATION_ACTION_GROUP.setSelected(Actions.STACCATO_ACTION, true)`
  → `Actions.STACCATO_ACTION.setSelected(true)`;
  `Actions.ARTICULATION_ACTION_GROUP.clearSelection()` →
  `Actions.STACCATO_ACTION.setSelected(false)`;
  `Actions.ARTICULATION_ACTION_GROUP.getSelected() == / != null` →
  `Actions.STACCATO_ACTION.isSelected() == / != true` (adjusted to
  `isSelected()`/`!isSelected()` or `.isFalse()`/`.isTrue()` assertions as
  appropriate at each call site).
- `src/test/java/songscribe/e2e/ElementInsertionTest.java:381` needs no
  change — `triggerAction` takes `UIAction`.

## Verification

1. `./scripts/compile.sh` — must succeed after all renames/removals.
2. `./scripts/test.sh unit` — confirms the merged `ArticulationActionTest`,
   and the updated `EditModeManagerTest`, `GraceModeManagerTest`,
   `ActionsResetOnDocumentLoadTest`, `EnableFromSelectionTest` all pass.
3. `./scripts/test.sh unit songscribe.ui.selection.SlideReflectionTest songscribe.ui.selection.SelectionApplyIntegrationTest songscribe.ui.selection.ReflectionIntegrationTest songscribe.ui.action.LyricEditorActionAuditTest` —
   confirms the renamed-type references compile and pass.
4. Manually run the app (`./scripts/run.sh`, with user permission) and, with
   a note selected, press `>` (accent toggles), `<` (staccato toggles), and
   `,` (breath mark inserts/toggles) to confirm the new accelerators fire the
   expected action and match the menu/toolbar state.
