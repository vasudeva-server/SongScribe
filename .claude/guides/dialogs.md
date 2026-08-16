## Dialogs (`songscribe.ui.dialog`)

`BaseDialog` (abstract) — does NOT extend `JDialog`. Creates a fresh `JDialog` on each `setVisible(true)`, disposes on `setVisible(false)`. Geometry persists per-class via static map + `Prefs` (survives restarts).

`StandardDialog<I, O>` extends `BaseDialog` — adds **OK/Cancel** (no Apply), plus **Remove** when its operations offer one. Always modal.

### The rule

**A dialog is a widget shell over `I → O`. It has no collaborator it can query.**

The mechanical test, applied to every dialog class without judgment:

> **A dialog's constructor takes `MainFrame`, a `DialogOps`, and presentation constants — nothing
> else. No dialog field names `Song`, `Line`, `StaffElement`, `ScoreView` or a controller.**

A dialog is still free to use the domain's *static knowledge*. That line, not a package line, is where the boundary runs: a `songscribe.dom` import is fine when it names knowledge and wrong when it names the document.

- **Knowledge — allowed.** Enums (`Duration`, `Annotation.Placement`), value types (`BeatChange`, `Tempo`, `Annotation`), constants, and pure functions over them. Knowing what a crotchet is is not the same as holding a handle on a score. A mutable value type reaches a dialog as a copy — see [`I` is `Copyable`](#i-is-copyable-and-the-copy-happens-in-one-place).
- **State — forbidden.** `Song`, `Line`, `StaffElement`, `ScoreView`, and reaching through `MainFrame` for any of them. `getMainFrame()` is for **window parenting only**.

Under this rule a dialog's own steps — populate, gather, call the ops — are wiring and carry no tests of their own. **What carries tests is the controller.**

### `DialogOps` and `DialogController`

Three pieces cross the boundary:

- **a record in** — `I`, what to show, asked on **each opening**, and always a **copy**;
- **a record out** — `O`, what the controls now say, gathered on OK;
- **a `DialogOps<I, O>`** — four function references: `read`, `validate`, `commit`, `remove`.

```java
record DialogOps<I extends @Nullable Copyable<I>, O>(
    Supplier<I>                    read,
    Function<O, ValidationResult>  validate,
    Consumer<O>                    commit,
    @Nullable Runnable             remove     // null = this dialog offers no Remove
)
```

### `I` is `Copyable`, and the copy happens in one place

**What a dialog is shown is never the document's own object.** `DialogController.ops()` calls
`Copyable.copy()` on whatever `read()` answered before putting it in the bundle, so a dialog
editing what it was given cannot reach the document through it. `read()` itself copies nothing —
it reads what the controller holds and hands it over.

Doing it in `ops()` rather than in each `read()` is the point: it is true of every dialog rather
than of the ones whose author remembered. `AnnotationController` and `TempoChangeController` both
handed out the document's own mutable object before the bound existed, and nothing failed, because
no dialog happened to mutate what it was given.

**The bound is what makes it total.** `I extends @Nullable Copyable<I>` means a type that has not
said how it copies cannot be a dialog's input, and the compiler is what says so. Implementing
`Copyable` is the claim that `copy()` is deep enough; an immutable type answers `this`, and that
declaration is how it states it is immutable.

**A JDK type cannot implement it, so it gets wrapped.** `FontChoice` wraps `java.awt.Font` for
`FontDialog` and `MessageText` wraps `String` for `DoNotShowMessage`. Wrapping is the only way a
type we do not own crosses this boundary — the alternative is an escape hatch, and an escape hatch
is the whole rule again as a convention. Both wrappers are worth their keep for a second reason:
`MessageText` says which of a dialog's several strings this one is.

**`O` carries no bound.** It is built by `gather()` from the controls, so there is nothing of the
document's in it to alias.

**Function references, not an interface.** An interface — however narrow — is an object the dialog holds, and an object can be asked for whatever its type exposes, including whatever the next person adds to it. Four references expose four calls and can never expose a fifth.

The other end is a `DialogController<I, O>`. It holds the line, the element, the score and the view, and does whatever the four operations need. **The dialog never sees it.**

```java
abstract class DialogController<I extends @Nullable Copyable<I>, O> {
    protected DialogController(MainFrame mainFrame)

    // The access dialogs gave up. Legitimate here.
    protected final MainFrame        getMainFrame()
    protected final ScoreView        requireScoreView()
    protected final Song             getSong()
    protected final void             withModification(String label, Runnable mutator)

    protected abstract I                read()               // may answer the document's own object
    protected          ValidationResult validate(O values)   // default: accepts everything
    protected abstract void             commit(O values)
    protected          @Nullable Runnable removal()          // default: null

    public final DialogOps<I, O> ops()      // final: no subclass hands over a partial bundle,
}                                           // and the only place read()'s answer is copied
```

**Whoever opens the dialog constructs the controller** and passes `controller.ops()`. `ops()` is public because openers are not all in `ui.dialog` — `Actions` registers the cached menu actions from `ui.action`. `AttachmentDialogController` is the worked example: it resolves the element and line, builds the controller around them, and hands the dialog four references that already hold them.

**A controller bound to a gesture is constructed per gesture.** `removal()` is asked once, when `ops()` assembles the bundle, and decides whether a Remove button is *built* — not merely whether it is enabled. A controller serving a dialog reached from a cached action holds only the `MainFrame` and resolves the document in `read()`.

**There is one input shape, not two.** Every dialog asks `read` on each opening; none takes its input at construction. A dialog that appears to need no input is one whose constructor is smuggling its input in — if `I` wants to be `Void`, look at the constructor.

### A nullable `I`, through a controller family

`I extends @Nullable Copyable<I>` on `DialogOps`, `DialogController` and `StandardDialog` is what lets a family like `AttachmentDialog<C extends Copyable<C>> extends StandardDialog<@Nullable C, C>` exist — the input is absent when there's nothing to edit yet (Add), present when there is (Modify).

**NullAway reads a bare type variable as non-null whatever its bound permits.** So `DialogController.ops()`'s copy step cannot be written as `values == null ? null : values.copy()` — the `null` literal is rejected against a return type of `I`. Written as `values == null ? values : values.copy()` it compiles and means the same thing, because the null then travels inside `I` rather than as a literal. Reach for the same shape anywhere a generic method has to pass a nullable type variable through unchanged; it is not a suppression and needs none.

**A subclass of a *generic controller family* names both type arguments itself; it never supplies one value type and lets an intermediate class wrap it in `@Nullable`.** `AttachmentDialogController<I extends @Nullable Object, O> extends DialogController<I, O>` is a straight pass-through for exactly this reason: NullAway does not compose a `@Nullable` wrap performed in one generic class's `extends` clause with a further type-argument substitution a concrete subclass performs one level down. `AttachmentDialogController<C> extends DialogController<@Nullable C, C>`, with `AnnotationController extends AttachmentDialogController<Annotation>`, type-checks the family's own file but fails at every call to `new AnnotationController(...).ops()`, which NullAway resolves to `DialogOps<Annotation, Annotation>` instead of `DialogOps<@Nullable Annotation, Annotation>` — a real false positive, not a real nullability gap, but one no amount of restating the wrap at the intermediate class fixes. `AnnotationController`, `BeatChangeController` and `TempoChangeController` each write `AttachmentDialogController<@Nullable Annotation, Annotation>` in full. Follow the same shape for the next generic controller family whose `I` is nullable.

### BaseDialog API surface

Constructors: `(mainFrame, title)`, `(mainFrame, title, isModal)`, `(mainFrame, title, isModal, DialogCategory)`.

Fields: `contentPanel` (BorderLayout — add content to CENTER; `StandardDialog` attaches `buttonPanel` to SOUTH automatically).

Accessors: `getMainFrame()` — window parenting only.

There is **no inherited route to the score**. `getScoreView()`, `requireScoreView()` and `getSong()` are not on `BaseDialog`; they are on `DialogController`. Reaching the document through `getMainFrame()` is the same rule broken in a longer spelling.

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
- `getData()` → calls `tab.getData()` on each registered tab. Return false to cancel showing. Call `super` when overriding. **Final in `StandardDialog`** — see below. (Resetting the tabbed pane to index 0 happens separately in `setVisible()`, not here.)

### StandardDialog&lt;I, O&gt;

Constructors: `(mainFrame, title, ops)`, `(mainFrame, title, ops, DialogCategory)`. **There is no `isModal` parameter.** Cancel promises nothing happened, and that promise is worth something only if nothing could have happened meanwhile. A window that applies each edit as it is made extends `BaseDialog` directly — `PreferencesDialog` is the one such window.

```
show    →  populate(ops.read().get())
OK      →  gather() → ops.validate() → ops.commit()   [commit only if valid]
Remove  →  ops.remove().run()                          [button built iff non-null]
Cancel  →  nothing
```

**A subclass writes only `populate(I)` and `gather() → O`.** There are no `validate` / `commit` hooks to override; the dialog is not the thing that validates or commits. `getData()` and the OK path are both closed:

- `getData()` is **final** — it runs the registered tabs, then reads and calls `populate`. Reading what to show is not a decision a dialog makes for itself. A tab may still cancel the show by returning false from its own `getData()`, in which case `populate` is not reached.
- OK's three steps are private. **The values validated are the values committed**: the controls are read exactly once and that one value goes to both. Nothing is committed when validation refuses; the dialog stays up with its controls untouched.

Only the **first** failure is shown — stacking modal alerts is worse than under-reporting, and `ValidationResult` promises presentation order.

`showFailure(ValidationFailure)` is that presentation, exposed so a control that checks a rule **before** OK — a field's `InputVerifier`, a focus listener — reports it the same way. Such a control asks the same function the controller's `validate` will, so the two routes cannot tell the user different things about one mistake. A `LocalizedMessage` argument that is itself a `LocalizedMessage` is resolved here, which is how a failure names a user-facing word — a unit abbreviation — without the controller having resolved it.

**Remove is a framework affordance**, not an attachment one: any dialog whose controller answers a non-null `removal()` gets the button, positioned left of Cancel, running the removal and closing. A dialog never builds one itself.

`modifyButtonPanel()` — called once on first `setVisible(true)`. Mutate `buttonPanel` in place (add/remove buttons) or reassign the field entirely. Return the `BorderLayout` constraint for attaching it (default `SOUTH`). Do NOT call `contentPanel.add(buttonPanel, ...)` manually.

Nothing here repaints the score. A commit that writes the document does so inside a modification bracket, and the bracket's `SongDidChangeNotification` is what re-lays out and repaints ([mutations](../../docs/mutations.md)).

### DialogCategory (constructor arg, default OPERATIONAL)

- `INFORMATIONAL` — never blocked (About, progress, suppressible messages).
- `EXCLUSIVE` — modifies global state (Preferences, SongSettings).
- `OPERATIONAL` — scoped state or task.

Both EXCLUSIVE and OPERATIONAL are "blocking": a single counter means any blocking dialog blocks any other blocking-dialog action while visible (category doesn't pair them off). `OptionDialogs` doesn't participate. Actions opening blocking dialogs must set `UIAction.Flag.OPENS_DIALOG` (`DialogOpenAction` does NOT auto-set it).

Category precedent (pick by analogy):
- INFORMATIONAL — `ProgressBarDialog`, `DoNotShowMessage`
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

### Tab (BaseDialog inner class)

`extends JPanel` with GridBagLayout, top/left-aligned, horizontal fill default. Subclass constructor MUST end with `build()`.

Override `initContents()` to add components. `add(c)` auto-applies constraints. `addSectionSeparator(this)` (static on `BaseDialog`) adds the inter-section vertical strut. `addExpanding(c, HORIZONTAL|VERTICAL|BOTH)` — at most once per tab.

Lifecycle: `getData()` (populate, return false to cancel show), `tabWillShow()`, `tabWillHide()`.

**A tab populates and displays; it does not commit and it does not validate.** Both belong to the dialog, because both are about the gathered values as a whole: a rule spanning tabs cannot be checked from inside one of them, and a commit split across tabs is several undo steps for one edit. A tab contributes what its controls say and stops there.

A tab in a record-boundary dialog takes its values as a parameter rather than reaching for them: `populate(Input)` in, a typed getter or a slice record out (`SongSettingsMusicTab.populate`/`gather`), both driven from the dialog's own `populate(I)` / `gather()`. `Tab.getData()` stays the generic hook for tabs that need no input.

A tab whose control checks a rule as the user types asks the **same function the controller's `validate` asks**, handed to it as a function reference. Two callers of one function, never two copies of one rule.

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
new DialogOpenAction<>(mainFrame, actionName, frame -> new MyDialog(frame, new MyController(frame).ops()))
```

The factory is a `Function<MainFrame, T>`, so the dialog's constructor arguments are checked at compile time. The dialog is built lazily on first use and cached per action, which is why `read` is asked on each opening rather than at construction.

A dialog opened per gesture is constructed at the gesture instead, by the controller that resolved what it edits — `AttachmentDialogController.edit` is the pattern.
