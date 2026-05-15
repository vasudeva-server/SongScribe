## OptionDialogs Reference

All user-facing `JOptionPane`-based dialogs must go through `songscribe.ui.OptionDialogs`, NEVER directly through `JOptionPane`. This class:

- Suppresses dialogs in headless and test contexts (returns a caller-controlled default instead of blocking)
- Logs every message at the appropriate level (info → `trace`, warning → `warn`, error → `error`)
- Beeps on errors
- Resolves title/message keys through `Strings`, applies standard dialog key bindings, and positions the dialog

### Strings keys, not raw strings

This is the most important rule and the easiest to get wrong. Every method below — *except* `showOptionDialog` and `showErrorMessageWithString` — takes `Strings` **keys**, not literal text. The method resolves them via `Strings.get(...)` internally. See [Strings](strings.md).

```java
// CORRECT — pass keys
OptionDialogs.showErrorMessage(parent, Strings.ALERT_TITLE_FILE_ERROR, Strings.ERROR_FILE_OPEN);

// WRONG — passes literal text as if it were a key; Strings.get will not find it
OptionDialogs.showErrorMessage(parent, "File Error", "Could not open the file.");
```

The message-style methods (`showInfoMessage`, `showWarningMessage`, `showErrorMessage`) also accept trailing `Object... messageArgs` that are forwarded to `Strings.get(key, args)` for placeholder substitution.

### Available methods

| Method | Use for |
|---|---|
| `showInfoMessage(parent, titleKey, messageKey, args...)` | Informational alerts |
| `showWarningMessage(parent, titleKey, messageKey, args...)` | Warnings |
| `showErrorMessage(parent, titleKey, messageKey, args...)` | Errors (also beeps) |
| `showErrorMessageWithString(parent, title, message)` | Errors when the text is **already-resolved** (not a key) — e.g. an exception message |
| `showConfirmDialog(parent, titleKey, messageKey, optionType, messageType)` | Yes/No confirmations; suppressed default is `NO_OPTION` |
| `showConfirmDialog(parent, titleKey, messageKey, optionType, messageType, suppressedDefault)` | Confirmations with an explicit suppressed/headless return value |
| `showInputDialog(parent, titleKey, messageKey)` | Text input prompts; returns `@Nullable String`, suppressed default is `null` |
| `showInputDialog(parent, titleKey, messageKey, suppressedDefault)` | Input prompt with an explicit suppressed/headless return value |
| `showOptionDialog(parent, title, message, optionType, messageType, icon, options, initialValue)` | Multi-option dialogs. Note: `title` and `message` here are **raw** (not keys) — resolve them yourself with `Strings.get` |

`parent` is `@Nullable Component`; pass `null` when there is no owning window.

### Canonical example — confirm dialog

```java
var answer = OptionDialogs.showConfirmDialog(
    null,
    Strings.ALERT_TITLE_FILE_ERROR,
    Strings.CONFIRM_FILE_OPEN,
    JOptionPane.YES_NO_OPTION,
    JOptionPane.QUESTION_MESSAGE
);

if (answer == JOptionPane.YES_OPTION) {
    // ...
}
```

Compare the result against `JOptionPane` constants (`YES_OPTION`, `NO_OPTION`, `CANCEL_OPTION`). A closed dialog (window dismissed) is normalized for you: it returns `CANCEL_OPTION` for `YES_NO_CANCEL_OPTION`, otherwise `NO_OPTION` — so you never have to handle `CLOSED_OPTION` from a confirm dialog.

### Test suppression

In tests, call `OptionDialogs.setSuppressDialogs(true)` so dialog calls log instead of blocking on UI. Dialogs are also auto-suppressed whenever `GraphicsEnvironment.isHeadless()` is true.

When suppressed, each method returns a fixed default:

- `show*Message` — return `void`, just log
- `showConfirmDialog` — returns `suppressedDefault` (the 5-arg overload defaults this to `NO_OPTION`)
- `showInputDialog` — returns `suppressedDefault` (the 3-arg overload defaults this to `null`)
- `showOptionDialog` — returns `JOptionPane.CLOSED_OPTION`

If a test exercises a code path whose behavior depends on the dialog's answer, use the overload that takes an explicit `suppressedDefault` so the suppressed call returns the value that path needs.
