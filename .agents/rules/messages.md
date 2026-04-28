## Message System

SongScribe uses [MBassador](https://github.com/bennidi/mbassy) as its event bus, wrapped by `MessageCenter`.

### MessageCenter API

```java
MessageCenter.post(message)         // Dispatch a message synchronously to all subscribers
MessageCenter.subscribe(listener)   // Register an object as a message subscriber
MessageCenter.unsubscribe(listener) // Deregister a subscriber (short-lived objects only)
```

Subscribe in the constructor. Long-lived singletons (components, coordinators) subscribe once
and never unsubscribe. Short-lived objects (dialogs) must unsubscribe when they close.

**CRITICAL — MBassador holds weak references to subscribers.** If no strong reference to a subscriber exists outside the bus, GC will silently collect it and handlers will stop firing. Every subscriber MUST be reachable via a strong reference for its entire intended lifetime — a static field, an instance field on a long-lived object, etc. A bare `new Foo()` that only passes `this` to `MessageCenter.subscribe` is a bug; the object can be collected immediately.

### Message Types

All messages extend `songscribe.message.Message`, which provides priority constants:

```java
Message.HIGH_PRIORITY   // 27 — runs before normal handlers
Message.MEDIUM_PRIORITY // 13 — intermediate
Message.LOW_PRIORITY    // 0  — runs after all normal handlers
```

**Naming and package by kind:**

| Kind | Suffix | Package | Example |
|------|--------|---------|---------|
| Action request | `*Command` | `songscribe.message.command` | `AddDynamicsCommand` |
| State change | `*DidChangeNotification` or `*WasDoneNotification` | `songscribe.message.notification` | `CompositionDidChangeNotification`, `DocumentWasSavedNotification` |

Shared base classes and `MessageCenter` live in `songscribe.message`.

### Creating a message class

```java
// Notification (state change)
public class PrefsDidChangeNotification extends Message {
    private final PrefsKey key;

    public PrefsDidChangeNotification(PrefsKey key) { this.key = key; }
    public PrefsKey getKey() { return key; }
}

// Command (action request)
public class SaveCommand extends Message {}
```

Keep messages immutable — set all state in the constructor, expose via getters only.

### Subscribing and handling

```java
// In constructor — subscribe this object
MessageCenter.subscribe(this);

// Handler for a notification
@Handler
public void compositionDidChange(CompositionDidChangeNotification message) { ... }

// Handler for a command
@Handler
public void handleSave(SaveCommand message) { ... }
```

**Handler method naming rules** (from the `@Handler` methods code style rule):

- `*Notification` parameter → method name = class name minus `Notification`
  (`CompositionDidChangeNotification` → `compositionDidChange`)
- `*Command` parameter → method name = `handle` + class name minus `Command`
  (`SaveCommand` → `handleSave`)
- Catch-all handler → prefix with `on` (`onAnyMessage`)
- Same message, different priorities → append a purpose suffix:
  `musicSelectionDidChangeSaveRestoreActionStates` / `musicSelectionDidChangeReflectSelection`

### Priority

Default priority is `Message.MEDIUM_PRIORITY`. Specify explicitly only when ordering matters:

```java
@Handler(priority = Message.HIGH_PRIORITY)
public void musicSelectionDidChangeSaveRestoreActionStates(MusicSelectionDidChangeNotification n) {
    // runs before normal handlers
}

@Handler(priority = Message.LOW_PRIORITY)
public void musicSelectionDidChangeReflectSelection(MusicSelectionDidChangeNotification n) {
    // runs after all normal handlers
}
```

### Short-lived subscribers (dialogs)

Dialogs must unsubscribe when they close to avoid memory leaks and spurious updates:

```java
// On dialog open
MessageCenter.subscribe(this);

// On dialog close (e.g., windowClosed listener or dispose override)
MessageCenter.unsubscribe(this);
```

### Posting a message

```java
MessageCenter.post(new PrefsDidChangeNotification(key));
```

`CompositionDidChangeNotification` is posted automatically by `composition.withModification()` /
`composition.applyChange()` — never construct it directly.

Posting is synchronous — all handlers run before `post()` returns.
