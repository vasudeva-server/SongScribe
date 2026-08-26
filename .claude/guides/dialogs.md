## Dialogs (`songscribe.ui.dialog`)

`BaseDialog` (abstract) — does NOT extend `JDialog`. Creates a fresh `JDialog` on each `setVisible(true)`, disposes on `setVisible(false)`. The `BaseDialog` itself is built per opening and disposed on close — an instance is spent once its window has gone away and is never shown again. Geometry persists per-class via static map + `Prefs` (survives restarts).

`StandardDialog<I, O>` extends `BaseDialog` — adds **OK/Cancel** (no Apply), plus **Remove** when its operations offer one. Always modal.

### The rule

**A dialog is a widget shell over `I → O`. It has no collaborator it can query.**

The mechanical test, applied to every dialog class without judgment:

> **A dialog's constructor takes `MainFrame`, a `DialogOps`, and presentation constants — nothing
> else. No dialog field names `Song`, `Line`, `StaffElement`, `ScoreView` or a controller.**

A dialog is still free to use the domain's *static knowledge*. That line, not a package line, is where the boundary runs: a `songscribe.dom` import is fine when it names knowledge and wrong when it names the document.

- **Knowledge — allowed.** Enums (`Duration`, `Key`), value types (`BeatChange`, `Tempo`, `Annotation`), constants, and pure functions over them. Knowing what a crotchet is is not the same as holding a handle on a score. A mutable value type reaches a dialog as a copy — see [`I` is `Copyable`](#i-is-copyable-and-the-copy-happens-in-one-place).
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
`FontDialog`. Wrapping is the only way a type we do not own crosses this boundary — the
alternative is an escape hatch, and an escape hatch is the whole rule again as a convention. A
wrapper is worth its keep for a second reason too: its name says which of a dialog's several
strings or values this one is, where the JDK type alone would not.

**`O` carries no bound.** It is built by `gather()` from the controls, so there is nothing of the
document's in it to alias.

**Function references, not an interface.** An interface — however narrow — is an object the dialog holds, and an object can be asked for whatever its type exposes, including whatever the next person adds to it. A bundle of references exposes exactly those calls and can never expose another.

The other end is a `DialogController<I, O>`. It holds the line, the element, the score and the view, and does whatever the four operations need. **The dialog never sees it.**

```java
abstract class DialogController<I extends @Nullable Copyable<I>, O> {
    protected abstract I                read()               // may answer the document's own object
    protected          ValidationResult validate(O values)   // default: accepts everything
    protected          boolean          dataWasModified(O values)  // default: true
    protected abstract void             commit(O values)
    protected          @Nullable Runnable removal()          // default: null

    public final DialogOps<I, O> ops()      // final: no subclass hands over a partial bundle,
}                                           // and the only place read()'s answer is copied

// The access dialogs gave up. Legitimate here — and only for a controller that has to
// resolve the open document rather than being handed what it edits.
abstract class DocumentDialogController<I, O> extends DialogController<I, O> {
    protected DocumentDialogController(MainFrame mainFrame)

    protected final MainFrame        getMainFrame()
    protected final ScoreView        requireScoreView()
    protected final Song             getSong()
    protected final void             withModification(String label, Runnable mutator)
}
```

**Extend `DocumentDialogController` only when the controller resolves the document itself.**
`SongSettingsController` and `KeyChangeDialogController` do; the other four are constructed around
the line and element they edit and would only be handed a window to ignore. Extending it is what
forces a test to stand up a mocked application, so it is a claim worth checking: a controller that
can be handed its subject is testable with nothing on screen, which is what
`AttachmentDialogControllerTest` relies on.

**`commit` may itself ask a yes/no question before it opens its bracket.**
`AccidentalRestatements.confirm` must run in an edit's decide phase, before any
modification bracket opens — the same rule the ending confirms follow.
`KeyChangeDialogController.changeLineKey`/`insertKeyChange` are two more
callers of that rule, alongside `ScoreViewController`, `PitchShifter`,
`PreviewElementInserter` and `SelectionActionApplier`. This is not the
`validate`/`showFailure` presentation path — nothing was rejected, and no
`ValidationFailure` is shown. It is a decide-phase question about a change
already accepted as valid, and a cancelled answer means `commit` does nothing:
the document stays untouched, and the dialog still closes exactly as an
ordinary OK would, because `StandardDialog` sees the commit run to
completion either way.

**Whoever opens the dialog constructs the controller** and passes `controller.ops()`. `ops()` is public because openers are not all in `ui.dialog` — `Actions` registers the cached menu actions from `ui.action`. `AttachmentDialogController` is the worked example: it resolves the element and line, builds the controller around them, and hands the dialog four references that already hold them.

**A controller bound to a gesture is constructed per gesture.** `removal()` is asked once, when `ops()` assembles the bundle, and decides whether a Remove button is *built* — not merely whether it is enabled. A controller serving a dialog reached from a cached action holds nothing of the document and resolves it in `read()`, which is what `DocumentDialogController` exists for.

**There is one input shape, not two.** Every dialog asks `read` on each opening; none takes its input at construction. A dialog that appears to need no input is one whose constructor is smuggling its input in — if `I` wants to be `Void`, look at the constructor.

### A nullable `I`, through a controller family

`I extends @Nullable Copyable<I>` on `DialogOps`, `DialogController` and `StandardDialog` is what lets a family like `AttachmentDialog<C extends Copyable<C>> extends StandardDialog<@Nullable C, C>` exist — the input is absent when there's nothing to edit yet (Add), present when there is (Modify).

**NullAway reads a bare type variable as non-null whatever its bound permits.** So `DialogController.ops()`'s copy step cannot be written as `values == null ? null : values.copy()` — the `null` literal is rejected against a return type of `I`. Written as `values == null ? values : values.copy()` it compiles and means the same thing, because the null then travels inside `I` rather than as a literal. Reach for the same shape anywhere a generic method has to pass a nullable type variable through unchanged; it is not a suppression and needs none.

**A subclass of a *generic controller family* names both type arguments itself; it never supplies one value type and lets an intermediate class wrap it in `@Nullable`.** `AttachmentDialogController<I extends @Nullable Object, O> extends DialogController<I, O>` is a straight pass-through for exactly this reason: NullAway does not compose a `@Nullable` wrap performed in one generic class's `extends` clause with a further type-argument substitution a concrete subclass performs one level down. `AttachmentDialogController<C> extends DialogController<@Nullable C, C>`, with `AnnotationController extends AttachmentDialogController<Annotation>`, type-checks the family's own file but fails at every call to `new AnnotationController(...).ops()`, which NullAway resolves to `DialogOps<Annotation, Annotation>` instead of `DialogOps<@Nullable Annotation, Annotation>` — a real false positive, not a real nullability gap, but one no amount of restating the wrap at the intermediate class fixes. `AnnotationController`, `BeatChangeController` and `TempoChangeController` each write `AttachmentDialogController<@Nullable Annotation, Annotation>` in full. Follow the same shape for the next generic controller family whose `I` is nullable.

### What a dialog inherits, and what it does not

Every dialog is owned by the main frame, `MODELESS` ones included. Ownership is not a free choice: an unowned window carries no menu bar, so the menus disappear for as long as it is frontmost. The price is that AWT keeps an owned window above its owner for as long as it exists, so a modeless dialog — `PreferencesDialog` is the only one — cannot be pushed behind the score, and closing it is the only way to see what it covers. That is accepted; do not unown a dialog to fix it.

There is **no inherited route to the score**. `getScoreView()`, `requireScoreView()` and `getSong()` are not on `BaseDialog`; they are on `DocumentDialogController`. Reaching the document through `getMainFrame()` is the same rule broken in a longer spelling. What `BaseDialog` does hand down is a window for parenting, the dialog's own `Bindings`, and the validity conjunction described below.

All spacing comes from FlatLaf props and the framework's own spacer struts — a dialog never writes a pixel gap of its own. A separator's orientation follows the container's layout rather than being chosen at the call site, so the same call is correct in a horizontal box and a vertical one.

### StandardDialog&lt;I, O&gt;

**There is no `Modality` parameter — a `StandardDialog` is always `MODAL`.** Cancel promises nothing happened, and that promise is worth something only if nothing could have happened meanwhile. A window that applies each edit as it is made extends `BaseDialog` directly — `PreferencesDialog` is the one such window.

```
show    →  populate(ops.read().get())
OK      →  gather() → ops.wasModified() → ops.validate() → ops.commit()
           [closes and writes nothing when wasModified says no]
           [commit only if valid]
           [OK is disabled entirely while requireValid conditions fail]
Remove  →  ops.remove().run()                          [button built iff non-null]
Cancel  →  nothing
```

### Validity, and why a rule belongs in front of OK

**A rule the user can break while typing is stated as a validity condition, not caught at OK.** `requireValid(ObservableValue<Boolean>)` — on `BaseDialog` and on `Tab` — adds a condition; `valid` is the conjunction of every condition added, and `StandardDialog` binds its OK button's enabled state to it. A dialog that adds none is always valid, so this costs nothing where there is no rule.

Conditions are `computed` values over the properties they read, so they answer to a paste and a cut as readily as to typing, and the user sees the commit become unavailable at the moment they make it unavailable — rather than being told after pressing OK, with the commit already done.

**"Nothing has changed" is deliberately not a validity condition.** OK stays enabled whether or not the notator changed anything; what changes is whether the commit writes. `DialogController.dataWasModified(O)` is a fifth operation in the bundle, and `StandardDialog` asks it **first** on OK — before `validate`, not between it and `commit`. An OK the notator changed nothing before therefore closes the window, writes nothing, records no undo step and leaves the document clean.

**It comes before `validate`, not after.** Values that change nothing are not a proposed change, so there is nothing to judge. Judging anyway lets a rule about a change the notator never made refuse to let them out of the dialog — `KeyChangeDialogController.validate` measures whether every re-keyed line still fits, and on a document that already overflows it would refuse a dismissal — and it runs that measurement on every OK press. The order lives in `StandardDialog.commitOnOk`, which is the one place that states what OK does; a controller states the comparison, never the skip and never the order.

**No dialog disables OK because nothing has changed.** A greyed-out OK makes "commit" and "dismiss" two different questions for one button, and it needs a comparison in front of every keystroke rather than one at OK. Refusing to write says the same thing for less.

The comparison belongs to the controller, not the dialog: whether two values say the same thing is a question about the document's types. That is also what makes it testable without a window — `AttachmentDialogControllerTest` asks all three attachment controllers with no window on screen. `dataWasModified` defaults to `true` — commit whatever the controls say — because a comparison is only meaningful where `I` and `O` describe the same thing; for a controller whose input and output are different shapes the default is the honest answer, and a partial no-op guard belongs in `commit` per write, as `SongSettingsController` does. `AttachmentDialogController` overrides it with `equals`, which is the whole of what its three subclasses need: every value type they carry — `Annotation`, `BeatChange`, `Tempo` — compares by value on its own, so none of them states a comparison of its own. It can state the comparison for the whole family because the family names **one** type parameter, `AttachmentDialogController<T>` over `DialogController<@Nullable T, T>` — two independent parameters would let a subclass name unrelated types, and the comparison would then silently answer "changed" every time.

`KeyChangeDialogController` overrides it too, comparing the chosen key against the key already in effect. It does not switch on the gesture to find that key: a `KeyChangeSite` — the line, the index, and which of the three places it is — answers `keyInEffect()`, so one comparison covers all four key-editing gestures and the controller needs no window to be asked.

This does not make `ops.validate()` redundant, and the two do not overlap. A validity condition is a rule about **one control's own value**, live and local. `validate` is for a rule about the **gathered values as a whole** — one spanning tabs, or needing the document to answer, like the lyrics-font rule that has to know which lines fit today. A rule that a control can answer for itself does not go in `validate`: the values reaching `validate` cannot break it, and a check there would be a guard on an impossible condition.

**A subclass writes only `populate(I)` and `gather() → O`.** There are no `validate` / `commit` hooks to override; the dialog is not the thing that validates or commits. `getData()` and the OK path are both closed:

- `getData()` is **final** — it runs the registered tabs, then reads and calls `populate`. Reading what to show is not a decision a dialog makes for itself. A tab may still cancel the show by returning false from its own `getData()`, in which case `populate` is not reached.
- OK's three steps are private. **The values validated are the values committed**: the controls are read exactly once and that one value goes to both. Nothing is committed when validation refuses; the dialog stays up with its controls untouched.

Only the **first** failure is shown — stacking modal alerts is worse than under-reporting, and `ValidationResult` promises presentation order.

`showFailure(ValidationFailure)` is that presentation, exposed so a control that checks a rule **before** OK — a field's `InputVerifier`, a focus listener — reports it the same way. Such a control asks the same function the controller's `validate` will, so the two routes cannot tell the user different things about one mistake. A `LocalizedMessage` argument that is itself a `LocalizedMessage` is resolved here, which is how a failure names a user-facing word — a unit abbreviation — without the controller having resolved it.

**Remove is a framework affordance**, not an attachment one: any dialog whose controller answers a non-null `removal()` gets the button, positioned left of Cancel, running the removal and closing. A dialog never builds one itself.

A dialog that needs a different button row overrides the hook for it and returns where the row should attach. **The framework attaches the row; the dialog never adds it to the content panel itself**, or the row lands twice.

Nothing here repaints the score. A commit that writes the document does so inside a modification bracket, and the bracket's `SongDidChangeNotification` is what re-lays out and repaints ([mutations](../../docs/mutations.md)).

### DialogCategory (constructor arg, default OPERATIONAL)

- `INFORMATIONAL` — never blocked (About, progress, suppressible messages).
- `EXCLUSIVE` — modifies global state (Preferences, SongSettings).
- `OPERATIONAL` — scoped state or task.

Both EXCLUSIVE and OPERATIONAL are "blocking": a single counter means any blocking dialog blocks any other blocking-dialog action while visible (category doesn't pair them off). `OptionDialogs` doesn't participate. Actions opening blocking dialogs must set `UIAction.Flag.OPENS_DIALOG` (`DialogOpenAction` does NOT auto-set it).

Category precedent (pick by analogy):
- INFORMATIONAL — no dialog carries it today. It is for a window that reports and never edits, so blocking it would hide the report.
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

A tab lays its own contents out in an overridable hook, and its **subclass constructor must end with `build()`** — the framework cannot call it for you, and a tab that omits it comes up empty. Constraints are applied by the tab's own `add`, so a tab never writes a `GridBagConstraints`. At most one component per tab may be declared expanding.

**A tab does not hold its dialog.** What it reaches instead is exactly four things, and that list is the whole of it: the owning dialog's `Bindings`, which is where the tab declares its bindings and effects and is torn down with the dialog (see [bindings](bindings.md)); a window for parenting, under the same rule as on `BaseDialog`; a re-pack, for a tab whose content changes height at runtime; and `requireValid`, which contributes a condition to the owning dialog's validity and is what disables OK while the tab's own values cannot be committed.

Lifecycle: `getData()` (populate, return false to cancel show), `tabWillShow()`, `tabWillHide()`, `dispose()`. A tab that owns a `Disposable` overrides `dispose()` to release it — a font row's `Choose`/`Reset` actions and any `UIAction` behind a button of the tab's own subscribe themselves to the message bus. A tab that owns none does not override it.

**A tab populates and displays; it does not commit and it does not validate.** Both belong to the dialog, because both are about the gathered values as a whole: a rule spanning tabs cannot be checked from inside one of them, and a commit split across tabs is several undo steps for one edit. A tab contributes what its controls say and stops there.

A tab in a record-boundary dialog takes its values as a parameter rather than reaching for them: `populate(Input)` in, a typed getter or a slice record out (`SongSettingsMusicTab.populate`/`gather`), both driven from the dialog's own `populate(I)` / `gather()`. `Tab.getData()` stays the generic hook for tabs that need no input.

A tab whose live-typed check must match a clause of the controller's `validate` asks that **same function**, handed to it as a function reference — never a second copy of the rule. Such a check expresses itself as a binding declared on `bindings()`, reading that same named function, so the live answer and the answer on OK cannot tell the user different things about one mistake.

A tab states a rule about one of its own controls with `requireValid` instead — see [Validity](#validity-and-why-a-rule-belongs-in-front-of-ok). `SongSettingsTitleTab` requires a non-blank title that way, so OK is unavailable while the field is empty. A field that must never be *left* blank carries a `NonBlankGuard`, which restores the previous text with an alert once focus leaves — `NonBlankTextField` for a one-row field, `NonBlankTextArea` for a taller one; the two are complementary, the condition speaking while the user types and the guard once they move on. A combo box offering a fixed list plus an `Other…` row states its non-blank rule in the prompt that row opens, which is an ordinary `StandardDialog` with a `NonBlankTextField` and a `requireValid` condition, so the rule is a validity condition like any other rather than a guard on an editor.

`getInitialFocus()` → null — override to name the control that should hold the caret **whenever this tab appears**: when the dialog opens on it, and when the user switches to it in a window that is already up. A standing property of the tab, asked for afresh each time. For a control wanted on one particular open only, see [Opening on a chosen tab](#opening-on-a-chosen-tab) below.

Registration: `addTab(tab)` (adds + registers) or `registerTab(tab)` (no pane). The `Tab` owns its own title — pass it to `super(title)` (or `super(title, paddingKey)`) in the subclass constructor rather than supplying it at `addTab()` call sites.

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

### Opening

```java
new DialogOpenAction<>(mainFrame, actionName, frame -> new MyDialog(frame, new MyController(frame).ops()))
```

The factory is a `Function<MainFrame, T>`, so the dialog's constructor arguments are checked at compile time. A fresh dialog is built for every opening, and `read` is asked on each opening because a dialog takes no input at construction. Building per opening is what lets the dialog be disposed on close, so what it owns stops handling messages when its window goes away.

A dialog opened per gesture is constructed at the gesture instead, by the controller that resolved what it edits — `AttachmentDialogController.edit` is the pattern.
