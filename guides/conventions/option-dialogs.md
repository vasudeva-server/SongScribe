# OptionDialogs

Rules for a user-facing alert, confirm or input prompt.

## Rules

- Show every `JOptionPane`-based dialog through `OptionDialogs`, never through `JOptionPane` directly, because going direct loses suppression in headless and test contexts, the log line, the error beep, `Strings` resolution, the standard key bindings and positioning.
- Pass `Strings` keys as titles and messages, never literal text, because a key is a `String` and nothing at compile time catches the wrong form. See [strings](strings.md).

  ```java
  OptionDialogs.showErrorMessage(parent, Strings.ALERT_TITLE_FILE_ERROR, Strings.ERROR_FILE_OPEN);
  ```

- Resolve the text yourself in the two places that take resolved text: the error variant that takes a string, and the button labels of the multi-option dialog.
- Compare a confirm result against the `JOptionPane` constants; do not handle `CLOSED_OPTION`, because `OptionDialogs` never returns it.
- Pass the suppressed default that the path needs, in a test that exercises a path whose behavior depends on the answer, because otherwise the test silently takes the "user said no" branch.
