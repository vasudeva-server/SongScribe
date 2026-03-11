## Alerts, Confirms, and All User-Facing Dialogs

All user-facing dialogs must go through `songscribe.ui.Dialogs`, never directly through `JOptionPane`. This class:

- Suppresses dialogs in headless and test contexts
- Logs messages at the appropriate level
- Beeps on errors

### Available methods

| Method | Use for |
|---|---|
| `Dialogs.showInfoMessage(parent, title, message)` | Informational alerts |
| `Dialogs.showWarningMessage(parent, title, message)` | Warnings |
| `Dialogs.showErrorMessage(parent, title, message)` | Errors (also beeps) |
| `Dialogs.showConfirmDialog(parent, title, message, optionType, messageType)` | Yes/No confirmations |
| `Dialogs.showConfirmDialog(parent, title, message, optionType, messageType, suppressedDefault)` | Confirmations with explicit test default |
| `Dialogs.showInputDialog(parent, title, message)` | Text input prompts |
| `Dialogs.showOptionDialog(...)` | Multi-option dialogs |
| `Dialogs.confirmFileOverwrite(parent, title, file)` | Overwrite confirmation (convenience) |

### Never use JOptionPane directly

```java
// Bad
JOptionPane.showMessageDialog(parent, "Something happened");

// Good
Dialogs.showInfoMessage(parent, Strings.get(Strings.DIALOG_TITLE), Strings.get(Strings.MESSAGE_KEY));
```

### Test suppression

In tests, call `Dialogs.setSuppressDialogs(true)` to prevent dialogs from blocking. For confirm dialogs, use the overload with `suppressedDefault` to control what the suppressed call returns.
