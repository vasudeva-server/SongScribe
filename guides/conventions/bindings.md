# Bindings

Rules for code that wires a component with `Bindings`, in a dialog or in a long-lived view.

## Rules

- Type every bind target as narrowly as the binding allows, because a target typed `Property` where `WritableValue` would do can be handed on as a source, and the compiler then accepts a binding that never fires.
- Reach a control the user edits through `Controls`, and write presentation state a dialog computes through `Widgets`.
- Do not hand-wire a Swing listener for state a binding can carry.
- Keep storage in the control, and use a `ValueProperty` only for state with no control behind it, because a value nothing observes is a plain field.
- Declare absence with a `@Nullable` type argument, never with an empty string, a placeholder object or a separate "is set" flag.
- Replace a bound value, never mutate it, because a mutation inside the value notifies nobody and every binding that depends on it keeps what it last saw.
- Call the binding framework on the EDT only, because nothing in it is synchronized and a background call can lose a notification without any report.
- Create a `Bindings` with its owner and call `dispose` when the owner is disposed. See [lifecycle](lifecycle.md).
- Create a `computed` through the owner's `Bindings`, and read its inputs through `ObservableValue`s only, because a direct control read is invisible to the dependency tracker.
- Write a single-source transform as the `Function` overload of `bind`, and a value derived from several sources as `bindings.computed`; `ObservableValue` has no `map`, `combine` or boolean combinators.
- Run an effect that must fire only on a real change through `onNotify` on a `ValueProperty` bound from the `Computed`, because a `Computed` notifies whenever any dependency notifies, not when its value changes.
- State a rule shared by a binding, an input guard and a controller's `validate` as one named domain function that all three call, because a rule inside `songscribe.binding` cannot be reached from a controller and the second copy tells the user something different about one mistake. See [dialogs](dialogs.md).
- Observe a `ButtonGroup` through `Controls.radioGroup` and never by polling the buttons, because Swing turns the outgoing button off before it turns the incoming one on and announces the step in between.
- Bind a button adapter on item events and never on action events, because an action listener does not fire on a programmatic write.
- Write the Swing notification route and whether it fires on a programmatic write into the contract of every new `Controls` factory, because the wrong route loses writes silently.
- Bind a text field that this repository does not own with `Timing.WHILE_TYPING` or write it through the property, because its `setText` does not reach an `ON_COMMIT` property.
