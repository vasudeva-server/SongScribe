# Issue 718 — Accelerators for insert line before/after

## Context

The Song ▸ Line submenu offers three commands, all built from
`songscribe.ui.action.InsertLineAction`:

| Command | Factory | `shift` | Accelerator today |
| --- | --- | --- | --- |
| Add line at end | `createAddLineAction` | `ADD` (-1) | Cmd/Ctrl-Return |
| Insert line before selected line | `createInsertLineBeforeAction` | `0` | none |
| Insert line after selected line | `createInsertLineAfterAction` | `1` | none |

Only "add line at end" is reachable from the keyboard. Issue #718 asks for the
other two to get accelerators in the same family:

- Cmd/Ctrl-**Shift**-Return → insert line **before** the selected line
- Cmd/Ctrl-**Alt**-Return → insert line **after** the selected line

Outcome: all three line-insertion commands are keyboard-reachable, and the menu
items display their shortcuts (Swing's `JMenu.add(Action)` reads
`ACCELERATOR_KEY` automatically, so `MenuController` needs no change).

## Change

Single production file: `src/main/java/songscribe/ui/action/InsertLineAction.java`.

The constructor currently passes the accelerator conditionally:

```java
(shift == ADD) ? KeyEvent.VK_ENTER : 0,
(shift == ADD) ? UIUtils.MENU_SHORTCUT_MASK : 0,
```

All three variants now use `KeyEvent.VK_ENTER`; only the modifier mask varies.
Replace those two arguments with `KeyEvent.VK_ENTER` and a call to a new private
static helper that mirrors the existing `getActionCommand(int shift)` shape:

```java
private static int getAcceleratorModifiers(int shift) {
    if (shift == ADD) {
        return UIUtils.MENU_SHORTCUT_MASK;
    }

    var extraModifier = (shift == 0)
        ? InputEvent.SHIFT_DOWN_MASK
        : InputEvent.ALT_DOWN_MASK;
    return UIUtils.MENU_SHORTCUT_MASK | extraModifier;
}
```

`InputEvent` comes in via the existing `import module java.desktop;`. No new
`Strings` entries, no `MenuController` change, no MusicXML/undo impact. There is
no keyboard-shortcut reference doc or shortcuts dialog in the repo, so nothing
else needs updating.

### Why this is safe

- Registration goes through the existing path: `UIAction`'s constructor calls
  `UIUtils.addAction(mainFrame.getRootPane(), this)` whenever `virtualKey != 0`,
  which binds the keystroke into the root pane's
  `WHEN_IN_FOCUSED_WINDOW` input map keyed by the action command
  (`UIUtils.registerActionKeystroke`, `src/main/java/songscribe/util/UIUtils.java:206`).
  The three action commands (`add-line`, `insert-line-before`,
  `insert-line-after`) are distinct, so nothing overwrites anything.
- The only other `VK_ENTER` bindings in the app are **plain** Enter (modifiers
  `0`) on the focused score component
  (`src/main/java/songscribe/ui/component/ScoreInputHandler.java:184,196-198`)
  and on the lyric editor (`src/main/java/songscribe/ui/component/LyricEditor.java:859`).
  Swing keystrokes match `(keyCode, modifiers)` exactly, so a Cmd-Shift-Return or
  Cmd-Alt-Return never resolves to either. Nothing else claims Enter with Shift
  or Alt.
- While the lyric editor is focused, `Flag.DISABLE_WHEN_EDITING_TEXT` (already on
  this action) disables all three variants, so the new shortcuts are inert there
  rather than inserting a line mid-lyric.
- Enablement is unchanged: `updateEnabledState()` already disables the
  before/after variants unless `hasLineSelection()`, and a disabled action bound
  in an input map is skipped by Swing, so the shortcut is inert without a line
  selection.

## Tests

Extend `src/test/java/songscribe/ui/action/InsertLineActionTest.java` with three
assertions on `getAccelerator()`, matching the existing per-variant test style:

- add-line → `KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, UIUtils.MENU_SHORTCUT_MASK)`
- before → `… | InputEvent.SHIFT_DOWN_MASK`
- after → `… | InputEvent.ALT_DOWN_MASK`

Referencing `UIUtils.MENU_SHORTCUT_MASK` in the test keeps it correct headless
(`CTRL_DOWN_MASK`) and on macOS (`META_DOWN_MASK`).

## Verification

1. `./scripts/compile.sh`
2. `./scripts/test.sh InsertLineActionTest`
3. Manual (needs approval to run the app): `./scripts/run.sh` — open a song,
   click a line to select the whole line, press Cmd-Shift-Return (line inserted
   above) and Cmd-Alt-Return (line inserted below). Confirm the Song ▸ Line
   submenu shows both shortcuts and that both items are greyed out with no line
   selected.
