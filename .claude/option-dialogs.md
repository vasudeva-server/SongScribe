## OptionDialogs Reference

All user-facing JOptionPane-based dialogs must go through `songscribe.ui.OptionDialogs`, never directly through `JOptionPane`. This class:

- Suppresses dialogs in headless and test contexts
- Logs messages at the appropriate level
- Beeps on errors

### Available methods

| Method | Use for |
|---|---|
| `OptionDialogs.showInfoMessage(parent, title, message)` | Informational alerts |
| `OptionDialogs.showWarningMessage(parent, title, message)` | Warnings |
| `OptionDialogs.showErrorMessage(parent, title, message)` | Errors (also beeps) |
| `OptionDialogs.showConfirmDialog(parent, title, message, optionType, messageType)` | Yes/No confirmations |
| `OptionDialogs.showConfirmDialog(parent, title, message, optionType, messageType, suppressedDefault)` | Confirmations with explicit test default |
| `OptionDialogs.showInputDialog(parent, title, message)` | Text input prompts |
| `OptionDialogs.showOptionDialog(...)` | Multi-option dialogs |
| `OptionDialogs.confirmFileOverwrite(parent, title, file)` | Overwrite confirmation (convenience) |

### Never use JOptionPane directly

```java
// Bad
JOptionPane.showMessageDialog(parent, "Something happened");

// Good
OptionDialogs.showInfoMessage(parent, Strings.get(Strings.DIALOG_TITLE), Strings.get(Strings.MESSAGE_KEY));
```

### Test suppression

In tests, call `OptionDialogs.setSuppressDialogs(true)` to prevent dialogs from blocking. For confirm dialogs, use the overload with `suppressedDefault` to control what the suppressed call returns.
