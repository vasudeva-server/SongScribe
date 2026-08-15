## Message Framework

MBassador event bus wrapped by `MessageCenter`. API:

```java
MessageCenter.post(message) // Synchronous; all handlers run on calling thread before returning
MessageCenter.subscribe(listener) // in constructor
```

**Weak references.** MBassador holds subscribers weakly. Every subscriber MUST be reachable via a strong reference (static field, instance field on a long-lived owner) for its intended lifetime. A subscriber retired before the process ends needs more than dropping that reference — see **Detaching** below.

### Detaching

Weak references are not a substitute for unsubscribing. Until the collector runs, a subscriber that has lost its last strong reference is still registered and still receives every message — and the collector may never run.

A subscriber whose lifetime is its owner's, where the owner lives as long as the process, never needs to detach. Two cases do:

- **A static field that gets reassigned.** `Actions.initialize` replaces every action constant; each replaced action stays subscribed. `initialize` retires the outgoing generation for exactly this reason.
- **An object retired before the process ends.** Loading a document replaces the `Song` in the `ScoreView`; the outgoing one is finished with, and `ScoreView.setSong` disposes it. Left subscribed, it keeps handling broadcast commands and posting undo steps against a document nobody has open.

**A class that calls `MessageCenter.subscribe(this)` in its constructor implements `songscribe.lifecycle.Disposable`.** That is the whole rule, and it is mechanical: the subscribe call is the trigger, the interface is the obligation. A class that does not subscribe does not implement it. See [lifecycle.md](lifecycle.md) for what disposal promises and who calls it.

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
// command/AddHairpinCommand.java
public class AddHairpinCommand extends Message {
    private final Hairpin.Kind kind;

    public AddHairpinCommand(Hairpin.Kind kind) {
        this.kind = kind;
    }

    public Hairpin.Kind kind() {
        return kind;
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
    public void handleAddHairpin(AddHairpinCommand message) {
        operations.addHairpinToSelection(message.kind());
    }

    @Handler
    public void songDidChange(SongDidChangeNotification message) { ... }

    @Handler(priority = Message.HIGH_PRIORITY)
    public void modeDidChange(ModeDidChangeNotification message) { ... }
}
```

### Notes

- For `SongDidChangeNotification` semantics, see [mutations.md](mutations.md).
