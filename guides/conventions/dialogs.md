# Dialogs

Rules for a `BaseDialog` or `StandardDialog` and for the `DialogController` behind it.

## Rules

- Give a dialog's constructor only `MainFrame`, a `DialogOps` and presentation constants, and name no `Song`, `Line`, `StaffElement`, `ScoreView` or controller in a dialog field, because a dialog is a widget shell over `I` to `O` with no collaborator it can query.
- Use `getMainFrame()` for window parenting only.
- Import `songscribe.dom` knowledge (enums, value types, constants, pure functions) into a dialog freely, and never the document's state.
- Put tests on the controller, not on a dialog's populate, gather and ops wiring.
- Make `I` implement `Copyable`, and wrap a JDK type that cannot, as `FontChoice` wraps `java.awt.Font`, because a bound that admits an escape hatch is the whole rule again as a convention.
- Make an immutable `I` answer `this` from `copy()`, because that is how it states it is immutable.
- Do not copy in `read()`; `DialogController.ops()` copies once for every dialog.
- Type `O` as `@Nullable Object` and gather `null` from a dialog that gathers nothing.
- Pass function references, never an interface or a controller, to a dialog.
- Extend `DocumentDialogController` only when the controller resolves the document itself; a controller that can be handed its subject extends `DialogController`, because extending it forces a test to stand up a mocked application.
- Have whoever opens the dialog construct the controller and pass `controller.ops()`. Construct a controller bound to a gesture per gesture.
- Take a dialog's input from `read` on each opening, never at construction; a dialog that seems to need no input is smuggling it in through the constructor.
- Build a new dialog for every opening, because the dialog is disposed on close and is not reusable.
- Own every dialog by the main frame, `MODELESS` ones included, and do not unown one to push it behind the score, because an unowned window carries no menu bar.
- Take all spacing from FlatLaf props and the framework's spacer struts; never write a pixel gap in a dialog. See [flatlaf-props](flatlaf-props.md).
- Make a dialog that applies each edit as it is made extend `BaseDialog` directly. A `StandardDialog` is always modal.
- Build an action that opens a `BaseDialog` with `DialogOpenAction` or set `UIAction.Flag.OPENS_DIALOG` on it, because the flag is what disables the action while another dialog is visible. `OptionDialogs` does not take part in that count.
- Write a subclass of `StandardDialog` with only `populate` and `gather`; the dialog does not validate or commit.
- State a rule on one control's own value, which the user can break while typing, as `requireValid` and not in `validate`, because OK is then unavailable the moment the rule breaks.
- Put in `validate` only a rule on the gathered values as a whole, such as one spanning tabs or needing the document to answer.
- Do not disable OK because nothing changed; override `dataWasModified` so that OK closes and writes nothing, because "commit" and "dismiss" would otherwise be two questions for one button.
- Have `dataWasModified` state only the comparison, never the skip and never the order, and put the comparison in the controller, because whether two values say the same thing is a question about the document's types.
- Give a family of controllers over one value one type parameter, as `AttachmentDialogController<T>` extends `DialogController<@Nullable T, T>`, because two independent parameters let a subclass name unrelated types and the comparison then always answers "changed".
- Ask a yes/no question from `commit` before its modification bracket opens, never inside it, and make a cancelled answer a no-op; the dialog still closes as for an ordinary OK.
- Write `values == null ? values : values.copy()` where a generic method passes a nullable type variable through, because NullAway reads a bare type variable as non-null and rejects a `null` literal against it.
- Name both type arguments in each subclass of a generic controller family, as `AnnotationController` extends `AttachmentDialogController<@Nullable Annotation, Annotation>`, and never supply one type and let an intermediate class wrap it in `@Nullable`, because NullAway does not compose the two substitutions and reports a false positive at every `ops()` call.
- Report a failure that a control checks before OK through `showFailure`, with the same function the controller's `validate` calls. See [bindings](bindings.md).
- Leave repainting to the `SongDidChangeNotification` of the modification bracket that the commit opens. See [undo](../subsystems/undo.md).
- Return where the button row should attach from the hook for a different button row, and never add the row to the content panel yourself, because the row then lands twice.

### Tabs

- Build the container of a tabbed dialog with `createTabbedContent()`, never `new JTabbedPane()`, because the tab lifecycle callbacks would not fire.
- End a `Tab` subclass constructor with `build()`.
- Pass the title to `super(title)` in the `Tab` constructor and not at the `addTab()` call site.
- Apply constraints through the tab's own `add`, never a `GridBagConstraints`, and declare at most one expanding component per tab.
- Reach the dialog from a tab only through `bindings()`, `getMainFrame()` for parenting, the re-pack, and `requireValid`; a tab does not hold its dialog.
- Use a plain `JButton` with a listener in a tab, never a `UIAction`.
- Set enablement that depends on the song once in `populate`, and put enablement that must follow main-window state in the dialog's bindings.
- Keep commit and validation out of a tab, because a rule spanning tabs cannot be checked from inside one and a commit split across tabs is several undo steps for one edit.
- Hand a tab the same named function the controller's `validate` calls, as a function reference, when a live-typed check must match it, and express the check as a binding on `bindings()`.
- Give a field that must never be left blank a `NonBlankTextField` or `NonBlankTextArea` in addition to the `requireValid` condition, because the condition speaks while the user types and the guard once they move on.
- Override `getInitialFocus()` for a control that should hold the caret whenever the tab appears, and use `showTab`'s `focus` for one particular opening.
- Name a tab to `showTab` by object, never by index.
- Give a dialog its own typed entry point over the protected `showTab`, with one exhaustive switch that maps each section to a tab and a field, as `SongSettingsDialog.show(Section)` does.
