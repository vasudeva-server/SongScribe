## Dialogs (`songscribe.ui.dialog`)

`BaseDialog` (abstract) — does NOT extend `JDialog`. Creates a fresh `JDialog` on each `setVisible(true)`, disposes on `setVisible(false)`. Geometry persists per-class via static map + `Prefs` (survives restarts).

`StandardDialog` extends `BaseDialog` — adds **OK/Cancel** only (no Apply). Validate-then-commit lifecycle.

### DialogCategory (constructor arg, default OPERATIONAL)

- `INFORMATIONAL` — never blocked (About, Help, WhatsNew).
- `EXCLUSIVE` — modifies global state (Preferences, SongSettings).
- `OPERATIONAL` — scoped state or task.

Both EXCLUSIVE and OPERATIONAL are "blocking": a single counter means any blocking dialog blocks any other blocking-dialog action while visible (category doesn't pair them off). `OptionDialogs` doesn't participate. Actions opening blocking dialogs must set `UIAction.Flag.OPENS_DIALOG` (`DialogOpenAction` does NOT auto-set it).

Category precedent (pick by analogy):
- INFORMATIONAL — `HelpDialog`, `WhatsNewDialog`, `HTMLDialog`, `ProgressBarDialog`, `ReportBugDialog`, `DoNotShowMessage`
- EXCLUSIVE — `PreferencesDialog`, `SongSettingsDialog`
- OPERATIONAL — the default; everything else

### Deliberate non-`BaseDialog` windows

Two windows in this package extend `JDialog` directly. Both have a class comment
explaining why; don't "fix" either back into `BaseDialog`.

- `MigrationWindow` — a non-modal utility window the user leaves open beside the score, so it
  must stay out of the blocking-dialog counter and has no OK/Cancel lifecycle.
- `AboutDialog` — undecorated, so it can show the borderless splash pane
  (`SplashWindow.createContentPanel`) without a title bar. Non-modal because it dismisses on a
  click outside, which a modal window can never see (the modal event filter discards input
  aimed at blocked windows before any listener runs). Also dismisses on a click inside, any
  keypress, or the app going to the background.

  It is **unfocusable** (`setFocusableWindowState(false)`) and nothing in it reads window
  focus — a borderless window on macOS may never become the key window, so `windowLostFocus`
  is not a dismissal signal you can build on. Triggers are a global `AWTEventListener` for
  the outside press, a `KeyEventDispatcher` for keys, and
  `ApplicationDidEnterBackgroundNotification` for app switches. It holds
  `ActivationGate.armForOverlay()` while up so the dismissing click is swallowed rather than
  also landing on the score.

### BaseDialog API surface

Constructors: `(title)`, `(title, isModal)`, `(title, isModal, DialogCategory)`.

Fields: `contentPanel` (BorderLayout — add content to CENTER; `StandardDialog` attaches `buttonPanel` to SOUTH automatically).
Accessors: `getMainFrame()`, `getScore()` (nullable), `requireScore()` (throws), `getSong()` (requires score).

Static helpers:
- `addLabeledField(container, labelText, field, LabelPosition.LEFT|TOP)`
- `addLabelToBox(box, text, gapHeight)`
- `addSeparator(container)` / `addLargeSeparator(container)` — add a component-gap spacer strut. Orientation follows the container's layout (X-axis `BoxLayout` → horizontal strut; Y-axis box or a `Tab`'s `GridBagLayout` → vertical), and the container's own `add` applies any constraints. Use within a `TitledSection`.
- `addSectionSeparator(container)` — the larger `DIALOG_SECTION_GAP` vertical strut for spacing between stacked sections of a `Tab`. Pass `this` from the `Tab`.

Spacing comes from FlatLaf props / per-component struts.

Overridable hooks:
- `getDefaultButton()` → null
- `isResizable()` → false
- `getExtraWidth()` / `getExtraHeight()` → 0
- `isClosable()` → true (false blocks Esc/X/Cmd-W)
- `getWindow()` → current JDialog (override for parent of nested dialogs from bg thread)
- `getContentPaddingKey()` → FlatLaf key for content padding (Insets)
- `hasButtons()` → true if dialog renders a button row
- `getData()` → calls `tab.getData()` on each registered tab. Return false to cancel showing. Call `super` when overriding. (Resetting the tabbed pane to index 0 happens separately in `setVisible()`, not here.)

### StandardDialog

OK click lifecycle: `isValidData()` → if true: `setData()` → `repaintScore()` → close.

Override hooks: `isValidData()`, `setData()` — both iterate tabs by default; call `super` when adding dialog-level logic.

`modifyButtonPanel()` — called once on first `setVisible(true)`. Mutate `buttonPanel` in place (add/remove buttons) or reassign the field entirely. Return the `BorderLayout` constraint for attaching it (default `SOUTH`). Do NOT call `contentPanel.add(buttonPanel, ...)` manually.

Canonical small example: `FontDialog` — adds content to `contentPanel`, overrides `getData()`/`setData()` with `super` calls, overrides `isResizable()`/`getExtraWidth()`/`getExtraHeight()`/`modifyButtonPanel()`.

### Tab (BaseDialog inner class)

`extends JPanel` with GridBagLayout, top/left-aligned, horizontal fill default. Subclass constructor MUST end with `build()`.

Override `initContents()` to add components. `add(c)` auto-applies constraints. `addSectionSeparator(this)` (static on `BaseDialog`) adds the inter-section vertical strut. `addExpanding(c, HORIZONTAL|VERTICAL|BOTH)` — at most once per tab.

Lifecycle: `getData()` (populate, return false to cancel show), `setData()` (commit, StandardDialog only), `isValidData()`, `tabWillShow()`, `tabWillHide()`.

Registration: `addTab(tab)` (adds + registers) or `registerTab(tab)` (no pane). The `Tab` owns its own title now — pass it to `super(title)` (or `super(title, paddingKey)`) in the subclass constructor rather than supplying it at `addTab()` call sites.

### Tabbed dialogs

Build the container with `createTabbedContent()` — NOT `new JTabbedPane()`. It returns a sidebar-style `JComponent`: a `JList` of tab titles down the side driving a `CardLayout` of tab panels via a `ListSelectionListener`, which is what fires `tabWillShow()`/`tabWillHide()` on selection change. Only the first call registers the dialog's top-level container and attaches that listener; nested sub-panes call it again but don't overwrite that registration. A reviewer should flag any tabbed dialog that constructs `JTabbedPane` directly — its tab lifecycle callbacks won't fire.

Canonical examples: `PreferencesDialog`, `SongSettingsDialog`.

### TitledSection (BaseDialog inner class)

`JPanel` + BoxLayout + `StandardTitledBorder`. `new TitledSection(title)` (Y_AXIS) or `(title, BoxLayout.X_AXIS)`. Use `addSeparator(section)` / `addLargeSeparator(section)` (static on `BaseDialog`) for spacers. Auto LEFT_ALIGNMENT.

### Opening

```java
new DialogOpenAction<>(actionName, MyDialog.class)
```

Reflection-instantiates via no-arg ctor, lazy, cached per action.
