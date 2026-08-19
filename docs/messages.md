## Message Framework

MBassador event bus wrapped by `MessageCenter`. API:

```java
MessageCenter.post(message) // Synchronous; all handlers run on calling thread before returning
MessageCenter.subscribe(listener) // in constructor
```

**Weak references.** MBassador holds subscribers weakly. Every subscriber MUST be reachable via a strong reference (static field, instance field on a long-lived owner) for its intended lifetime. A subscriber retired before the process ends needs more than dropping that reference — see **Detaching** below.

### Bus scopes

`post`/`subscribe`/`unsubscribe` act on the bus *in force*. Outside any scope that is the application bus, a `static final` field whose publication-error handler treats a throwing `@Handler` as fatal. Scopes live in a separate stack, so nothing can discard the application bus.

A `MessageBusScope` pushes a bus of its own for the duration of a bounded piece of work:

```java
try (var scope = new MessageBusScope(error -> errors.add(MessageCenter.describe(error)))) {
    convert.accept(converter);
}
```

Three things follow, and each is a promise the scope makes:

- **It replaces, it does not layer.** While a scope is in force, a `post` reaches only what subscribed inside it. Whatever subscribed to the bus beneath hears nothing until the scope closes, and hears nothing about what happened while it was open.
- **Closing discards the scope's subscribers.** Everything subscribed inside the scope goes in one operation, with no per-subscriber bookkeeping. This is not a substitute for disposal, and for a process that is about to exit it buys nothing — see **Detaching** and [lifecycle.md](lifecycle.md). What consumes it is the unit test suite, where a scope per test is what keeps one test's subscribers out of the next one.
- **The error handler is the scope's own.** A headless process cannot show the fatal-error dialog the application bus's handler ends in, so it supplies one that reports to the log instead.

The scope stack is process-wide, not per-thread, because a bounded piece of work may hand parts of itself to other threads and they must post to the same bus. That is only coherent if scopes are pushed and popped where nothing else is running: open one when the work begins, close it when the work is finished, never around a section of a live application while other threads are still posting. Nesting is allowed; interleaving is not, and closing out of order is reported rather than let through — a scope holds the bus it pushed and `close()` refuses to discard any other.

The one production caller is `Converter.run`, shared by the four headless converters, and its reason is the error handler: a converter has no display for the fatal dialog. `MessageBusScope` is the only supported way to drive the stack — `MessageCenter.pushBus`/`popBus` are package-private, so the pop cannot be skipped.

**`unsubscribe` reaches only the bus in force.** A listener that subscribed to the application bus and unsubscribes while a scope is open matches nothing and stays subscribed. Disposal inside a scope is therefore not supported; dispose at a point where the scope that saw the subscription is still the bus in force.

### Detaching

Weak references are not a substitute for unsubscribing. Until the collector runs, a subscriber that has lost its last strong reference is still registered and still receives every message — and the collector may never run.

A subscriber whose lifetime is its owner's, where the owner lives as long as the process, never needs to detach. Two cases do:

- **A static field that gets reassigned.** `Actions.initialize` replaces every action constant; each replaced action stays subscribed. `initialize` retires the outgoing generation for exactly this reason.
- **An object retired before the process ends.** Loading a document replaces the `Song` in the `ScoreView`; the outgoing one is finished with, and `ScoreView.setSong` disposes it. Left subscribed, it keeps handling broadcast commands and posting undo steps against a document nobody has open.

**A class that calls `MessageCenter.subscribe(this)` in its constructor implements `songscribe.lifecycle.Disposable`.** That is the whole rule, and it is mechanical: the subscribe call is the trigger, the interface is the obligation. A class that does not subscribe does not implement it. See [lifecycle.md](lifecycle.md) for what disposal promises and who calls it.

**A bus scope does not discharge that obligation** — it covers the unsubscribe half and nothing else, so the rule above stands unchanged. See *Four things end a set of registrations* in [lifecycle.md](lifecycle.md).

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
