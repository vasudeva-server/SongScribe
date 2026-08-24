## OptionDialogs

All user-facing `JOptionPane`-based dialogs go through `songscribe.ui.OptionDialogs`,
**NEVER** directly through `JOptionPane`. Going direct loses all of what the class adds:
suppression in headless and test contexts, a log line at the level matching the message
kind, the error beep, `Strings` resolution, the standard dialog key bindings, and
positioning.

### Strings keys, not raw strings

This is the most important rule and the easiest to get wrong. Titles and messages are
`Strings` **keys**, not literal text — the method resolves them itself.

```java
// CORRECT — pass keys
OptionDialogs.showErrorMessage(parent, Strings.ALERT_TITLE_FILE_ERROR, Strings.ERROR_FILE_OPEN);

// WRONG — passes literal text as if it were a key; Strings.get will not find it
OptionDialogs.showErrorMessage(parent, "File Error", "Could not open the file.");
```

Nothing catches the wrong form at compile time, because a key is a `String`. See
[Strings](strings.md).

The message-style methods take trailing `Object...` arguments, which are forwarded for
placeholder substitution.

**Two places take already-resolved text instead**, and both are deliberate:

- The error-with-string variant, for text that is not a key — an exception message.
- The `options` array of the multi-option dialog, which holds button **labels**. Its
  title and message are still keys; resolve the labels yourself.

### The answer

Compare a confirm result against the `JOptionPane` constants. **A closed dialog is
normalized for you** — a dismissed window answers `CANCEL_OPTION` where the dialog
offered Cancel and `NO_OPTION` otherwise, so a confirm dialog never hands you
`CLOSED_OPTION` to handle.

### Suppression

Dialogs are suppressed whenever `GraphicsEnvironment.isHeadless()` is true, and tests
suppress them explicitly. A suppressed call logs instead of blocking and returns a fixed
answer: the message methods return nothing, a multi-option dialog answers
`CLOSED_OPTION`, and a confirm or input prompt answers its **suppressed default** — `NO`
and `null` respectively unless the call names another.

**If a test exercises a path whose behavior depends on the answer, pass the explicit
suppressed default** for the value that path needs. Otherwise the test silently
exercises the "user said no" branch and nothing marks it as the reason.
