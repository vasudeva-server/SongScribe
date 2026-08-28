# UI Bindings

`songscribe.binding` is the property graph a component wires itself with: values
that can be read and observed, values that can be written, and declarative bindings
between them. Dialogs are its most common owner, but nothing about it is
dialog-specific — a long-lived view owns a `Bindings` the same way. It knows nothing about Swing; `songscribe.ui.binding` holds the views
over Swing controls, and a view over any other storage — a preference, a model
object — is a `ViewProperty` built the same way. The package Javadoc states the
design and each member's contract states its own promises. This guide states the
rules that hold across all of them.

## Values, sources and sinks

**A source is an `ObservableValue`, a sink is a `WritableValue`, and a control
the user edits is a `Property`.** Type every target as narrowly as the binding
allows: a target typed `Property` where `WritableValue` would do is a value that
can then be handed to somebody else as a source, and the compiler no longer
refuses the binding.

**A control the user edits is reached through `Controls`; presentation state a
dialog computes is written through `Widgets`.** `Controls` answers `Property`,
`Widgets` answers `WritableValue`, and those return types are the whole of the
boundary. Do not hand-wire a Swing listener for state a binding can carry.

**Properties are views onto controls, not a store.** The control remains the
storage; a property reads it on every `get` and writes straight through on
`set`. `ValueProperty` is the carveout, for state with no control behind it — a
chosen `Font` shown only as a description label. A value nothing observes is a
plain field, not a property.

**Values are replaced, never mutated.** An `ObservableValue<T>` observes
replacement of `T` and has no way to see a mutation made inside it. A mutable
value edited in place notifies nobody, and every binding and `computed` that
depends on it silently keeps what it last saw.

**Every call is on the EDT.** Nothing in the package is synchronized. A call
from a background thread can corrupt a dependency set or lose a notification,
and nothing reports it.

**A `Bindings` is disposed when its owner is**, which is what releases the
observations and everything they capture — including the observations each
`computed` holds on its dependencies, which is why a `computed` is created
through `Bindings` rather than standing free. For a dialog that is when it
closes; for a view that lives as long as the window, it is when the view is
disposed. See [lifecycle](../../docs/lifecycle.md).

## Deriving values

**A `computed` is created through its owner's `Bindings`, and its body reads its
inputs through `ObservableValue`s only.** A direct
control read — `field.getText()`, `combo.getSelectedItem()` — is invisible to
the dependency tracker, so the computed acquires no dependency on it, never
recomputes when it changes, and answers a stale value.

**There is no fluent predicate algebra.** A single-source transform is the
`Function` overload of `bind`; a value derived from more than one source is a
`bindings.computed`. `ObservableValue` has no `map`, no `combine` and no boolean
combinators, and a call site that wants one is asking for one of those two
constructs.

**An effect that must run on a real change goes through a `ValueProperty`.**
`Bindings.onNotify` runs its action whenever the source notifies, and a
`Computed` notifies whenever any dependency notifies — not when its value
changes. Bind a `ValueProperty` from the computed and call `onNotify` on the
`ValueProperty`, which notifies only on a transition. The method is named for
notification rather than change because that is what it delivers.

## A rule the dialog and the framework share

**A rule shared by a binding, an input guard and a controller's `validate` is a
named domain function all three call, never a method on the framework.** A rule
that lives inside `songscribe.binding` cannot be referenced by a controller,
so putting it there guarantees the second copy — and the second copy is what
tells the user something different about one mistake. See
[dialogs](dialogs.md).

## Adapters

**An adapter's contract names the Swing notification route it observes and
whether that route fires on a programmatic write.** Adding a factory to
`Controls` means writing that clause, because the wrong route loses writes and
nothing at runtime reports the loss.

| Route | Fires on a programmatic write |
|---|---|
| `DocumentListener` | yes |
| `ActionListener` on a combo box | yes |
| `ItemListener` on a button | yes |
| `ChangeListener` on a spinner model or a slider | yes |
| `ListSelectionListener` on a list | yes |
| `focusLost` | no |
| `ActionListener` on a button | no |
| `TickSlider` tick listener | no |

A button adapter therefore observes items and never actions.

**A radio group is read from its `ButtonGroup`, never by polling the buttons.** Swing turns the outgoing button off before it turns the incoming one on, and announces the first step in between — so there is a moment in every change when no button reports itself selected while the group already names the new one. `Controls.radioGroup` creates the group for this reason, and passes on only the selection half of the notification, since Swing announces both halves of one change.

## Text controls

**`Controls.text`'s `Timing` governs when the property notifies, and nothing
else.** A normalizer always runs on focus loss, whichever `Timing` was asked
for, and in the same listener — normalize, write back, then notify — so the
value notified is the normalized one.

**One focus loss notifies at most once.** The write-back goes through the
property's own write path rather than through the control, so a field this
repository owns does not also route that write back as a second notification.
Announcing it a second time in the listener would run every effect on the field
twice per edit.

**`MyJTextField` and `MyJTextArea` route `setText` into the associated
property**, so a direct write to a bound field of either class propagates
rather than being lost. A control this repository does not own has no such
delegation: an `ON_COMMIT` property over one goes stale on a programmatic write.
Bind such a field `WHILE_TYPING`, or write it through the property rather than
through the control.
