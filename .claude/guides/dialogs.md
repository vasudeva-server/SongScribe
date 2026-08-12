## Dialogs (`songscribe.ui.dialog`)

`BaseDialog` (abstract) — does NOT extend `JDialog`. Creates a fresh `JDialog` on each `setVisible(true)`, disposes on `setVisible(false)`. Geometry persists per-class via static map + `Prefs` (survives restarts).

`StandardDialog` extends `BaseDialog` — adds **OK/Cancel** only (no Apply). OK verifies the focused field, runs `commitOnOk()`, and closes only if both accept.

`CommitDialog<I>` extends `StandardDialog` — for a dialog whose OK commits values read from its controls. It gathers once and hands the same values to `validate` and `commit`, and presents any failure itself.

### What a dialog may touch

**A dialog may not query or modify state outside itself. It is free to use the domain's static knowledge.**

That line, not a package line, is where the boundary runs. A `songscribe.dom` import in a dialog is fine when it names knowledge and wrong when it names the document:

- **Knowledge — allowed.** Enums (`Duration`, `Annotation.Placement`), value types (`BeatChange`, `Tempo`, `Annotation`), constants, and pure functions over them. Knowing what a crotchet is is not the same as holding a handle on a score.
- **State — forbidden.** `Song`, `Line`, `StaffElement`, `ScoreView`, and reaching through `MainFrame` for any of them. `getMainFrame()` is for **window parenting only**.

What the dialog needs arrives as values and leaves as values:

- **a record in** — what to show, handed over at construction;
- **a record out** — what the controls now say, gathered on OK;
- **a back end** — a `DialogBackEnd<I>` supplied already bound to the document state it acts on. The dialog calls `validate(I)` and `apply(I)` and knows nothing else. `AttachmentBackEnd` extends it for dialogs that also offer Remove.

**Whoever opens the dialog does the binding.** The free functions behind a back end are written domain-object-first, so they read and test as domain operations; a dialog calling one directly would need the `Song`, which is the coupling the back end removes. `AttachmentEditor` is the worked example — it resolves the element and line, builds the back end around them, and hands the dialog something that already holds them. Implementations live in `songscribe.ui.dialog.backend`.

**The mechanical test, applied to every back-end signature: it contains no Swing type.** `validate(BeatChange)` and `apply(Tempo)` pass; anything naming a `JComponent`, a `JTextField` or a `Font`-carrying widget fails, and means logic that has not finished moving out of the dialog. A reviewer applies it without judgment.

Under this rule a dialog's own three steps — gather, call validate, call apply — are wiring and carry no tests of their own.

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

Accessors: `getMainFrame()` — window parenting only.

There is **no inherited route to the score**. `getScoreView()`, `requireScoreView()` and `getSong()` are gone; a dialog takes what it needs as values and writes through a back end. Reaching the document through `getMainFrame()` is the same rule broken in a longer spelling.

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

OK click lifecycle: focused field's `InputVerifier` → `commitOnOk()` → if both accept, close.

Override hook: `commitOnOk()` → whether the dialog may close. Default commits nothing and returns true, which is the whole of OK for a dialog that gathers no values. A dialog that commits values does not override it — it extends `CommitDialog<I>`, where it is final.

Nothing here repaints the score. A commit that writes the document does so inside a modification bracket, and the bracket's `SongDidChangeNotification` is what re-lays out and repaints ([mutations](../docs/mutations.md)).

### CommitDialog&lt;I&gt;

OK reads the controls once and hands that one value to `validate` then `commit`, so **the values validated are the values committed** and nothing is committed when validation refuses.

Override hooks: `gather()` → `I` (read-only, total over every reachable control state), `validate(I)` → `ValidationResult` (decides, displays nothing; defaults to accepting everything), `commit(I)` (called only with values `validate` accepted).

`CommitDialog` presents failures itself, via `OptionDialogs` — **only the first**, since `ValidationResult` promises presentation order and stacking modal alerts is worse than under-reporting. A dialog that shows its own validation alert has re-fused deciding with displaying.

`modifyButtonPanel()` — called once on first `setVisible(true)`. Mutate `buttonPanel` in place (add/remove buttons) or reassign the field entirely. Return the `BorderLayout` constraint for attaching it (default `SOUTH`). Do NOT call `contentPanel.add(buttonPanel, ...)` manually.

Canonical small example: `FontDialog` — adds content to `contentPanel`, overrides `getData()` with a `super` call and `gather()`/`commit()` for the chosen font, overrides `isResizable()`/`getExtraWidth()`/`getExtraHeight()`/`modifyButtonPanel()`.

### Tab (BaseDialog inner class)

`extends JPanel` with GridBagLayout, top/left-aligned, horizontal fill default. Subclass constructor MUST end with `build()`.

Override `initContents()` to add components. `add(c)` auto-applies constraints. `addSectionSeparator(this)` (static on `BaseDialog`) adds the inter-section vertical strut. `addExpanding(c, HORIZONTAL|VERTICAL|BOTH)` — at most once per tab.

Lifecycle: `getData()` (populate, return false to cancel show), `tabWillShow()`, `tabWillHide()`.

**A tab populates and displays; it does not commit and it does not validate.** Both belong to the dialog, because both are about the gathered values as a whole: a rule spanning tabs cannot be checked from inside one of them, and a commit split across tabs is several undo steps for one edit. A tab contributes what its controls say and stops there.

`getInitialFocus()` → null — override to name the control that should hold the caret **whenever this tab appears**: when the dialog opens on it, and when the user switches to it in a window that is already up. A standing property of the tab, asked for afresh each time. For a control wanted on one particular open only, see [Opening on a chosen tab](#opening-on-a-chosen-tab) below.

Registration: `addTab(tab)` (adds + registers) or `registerTab(tab)` (no pane). The `Tab` owns its own title now — pass it to `super(title)` (or `super(title, paddingKey)`) in the subclass constructor rather than supplying it at `addTab()` call sites.

### Tabbed dialogs

Build the container with `createTabbedContent()` — NOT `new JTabbedPane()`. It returns a sidebar-style `JComponent`: a `JList` of tab titles down the side driving a `CardLayout` of tab panels via a `ListSelectionListener`, which is what fires `tabWillShow()`/`tabWillHide()` on selection change. Only the first call registers the dialog's top-level container and attaches that listener; nested sub-panes call it again but don't overwrite that registration. A reviewer should flag any tabbed dialog that constructs `JTabbedPane` directly — its tab lifecycle callbacks won't fire.

Canonical examples: `PreferencesDialog`, `SongSettingsDialog`.

### Opening on a chosen tab

`showTab(Tab tab, @Nullable JComponent focus)` (protected on `BaseDialog`) shows the dialog with `tab` selected instead of the first, and `focus` holding the caret — pass null to leave the platform's default first focusable control in charge. Every tabbed dialog inherits it.

- **The tab is named by object, never by index.** `addTab` is the sole definition of tab order, so resolving through it is exact and no caller can pass an index that quietly means a different tab once one is inserted.
- **The caret target travels with the tab request**, so one read consumes the whole thing and no per-tab state leaks into a later open. `showTab`'s `focus` **outranks** the shown tab's `getInitialFocus()`: the caller asked for this control on this particular open.
- **The request is consumed once, at the very start of the show**, before anything that can abort it. A show cancelled by `getData()` still consumes it, so it cannot survive into an unrelated later open.
- **An unregistered tab falls back to the first tab** and drops the caret target with it — that control belongs to a tab this dialog is not going to show.
- The focus request is queued with `invokeLater`, so it lands once the window is actually up.

`showTab` is protected, so a dialog gives callers its own typed entry point rather than exposing tab objects. `SongSettingsDialog.show(Section)` is the canonical example: one exhaustive switch maps each `Section` to both a tab and a field, so a section cannot open one tab while focusing a control on another, and a new `Section` fails to compile rather than silently opening the wrong tab.

### TitledSection (BaseDialog inner class)

`JPanel` + BoxLayout + `StandardTitledBorder`. `new TitledSection(title)` (Y_AXIS) or `(title, BoxLayout.X_AXIS)`. Use `addSeparator(section)` / `addLargeSeparator(section)` (static on `BaseDialog`) for spacers. Auto LEFT_ALIGNMENT.

### Opening

```java
new DialogOpenAction<>(actionName, MyDialog.class)
```

Reflection-instantiates via no-arg ctor, lazy, cached per action.
