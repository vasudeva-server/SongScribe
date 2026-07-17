## Message System

MBassador event bus wrapped by `MessageCenter`. API:

```java
MessageCenter.post(message) // Synchronous; all handlers run on calling thread before returning
MessageCenter.subscribe(listener) // in constructor
```

**Weak references.** MBassador holds subscribers weakly. Every subscriber MUST be reachable via a strong reference (static field, instance field on a long-lived owner) for its intended lifetime. There is NO need to unsubscribe; when the subscriber is garbage collected, it will no longer receive messages.

### Message kinds

All messages extend `songscribe.message.Message`. Suffix + package:

| Kind | Suffix | Package |
|---|---|---|
| Action request | `*Command` | `songscribe.message.command` |
| State change | `*DidChangeNotification` | `songscribe.message.notification` |

Keep messages immutable (final fields, getters only). A payloadless message is just an empty subclass:

```java
// command/SaveCommand.java
public class SaveCommand extends Message {}
```

A message with payload takes its data through the constructor and exposes getters only:

```java
// command/AddDynamicsCommand.java
public class AddDynamicsCommand extends Message {
    private final boolean isCrescendo;

    public AddDynamicsCommand(boolean isCrescendo) {
        this.isCrescendo = isCrescendo;
    }

    public boolean isCrescendo() {
        return isCrescendo;
    }
}
```

### Construction

- `new SomethingDidChangeNotification(...)` / `new SomeCommand(...)`
- `SongDidChangeNotification` is posted automatically by `Song.withModification()` / `Song.applyChange()` — never construct directly.

### Priority

`@Handler(priority = N)` — higher runs first. The MBassador default is **0**, i.e. `Message.LOW_PRIORITY` — a bare `@Handler` runs *after* every handler with an explicit `MEDIUM_PRIORITY` or higher. Constants `HIGH_PRIORITY` (27), `MEDIUM_PRIORITY` (13), `LOW_PRIORITY` (0) are conventions only; any `int` (define as a constant) is valid. Specify only when multiple handlers for the same message class and ordering matters.

### Handler method naming

- `*Notification` → method = class name minus `Notification` (`SongDidChangeNotification` → `songDidChange`)
- `*Command` → `handle` + class name minus `Command` (`SaveCommand` → `handleSave`)
- Catch-all → `on*` prefix (`onAnyMessage`)
- Same message handled at multiple priorities in one class → append a purpose suffix to disambiguate (e.g. `musicSelectionDidChangeSaveRestoreActionStates` and `musicSelectionDidChangeReflectSelection` in `SelectionCoordinator`)

A handler class subscribes itself in its constructor (and must stay strongly reachable — here via the owning `Score`):

```java
public final class ScoreViewController {
    ScoreViewController(...) {
        ...
        MessageCenter.subscribe(this);
    }

    @Handler
    public void handleAddDynamics(AddDynamicsCommand message) {
        operations.addDynamicsToSelection(message.isCrescendo());
    }

    @Handler
    public void songDidChange(SongDidChangeNotification message) { ... }

    @Handler(priority = Message.HIGH_PRIORITY)
    public void modeDidChange(ModeDidChangeNotification message) { ... }
}
```

### Notes

- For `SongDidChangeNotification` semantics, see [mutations.md](mutations.md).
