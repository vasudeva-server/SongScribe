# Lifecycle

- Dispose a `Bindings` with its owner: a dialog's when the dialog is hidden, and a long-lived
  view's when the view is disposed, because every observation needs an owner whose disposal ends
  it.
- Do not unsubscribe on the way out of the process, and do not run the `Shutdown` sequence to
  discard a view, because disposal ends one object and the quit sequence ends the process.
