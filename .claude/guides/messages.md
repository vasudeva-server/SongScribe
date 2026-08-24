# Messages

Conventions for the application message bus. For how the bus behaves — delivery,
subscriber lifetime, scopes — see [messages.md](../../docs/messages.md).

## Kinds

Every message extends the common base type. Which kind it is shows in both its
suffix and its package:

| Kind | Suffix | Package |
|---|---|---|
| action request | `*Command` | `songscribe.message.command` |
| state change | `*DidChangeNotification` | `songscribe.message.notification` |

Keep messages immutable — final fields, getters only. A message with no payload
is an empty subclass; one with a payload takes its data through the constructor
and exposes getters only.

Never construct a change notification for the document directly. It is posted
automatically when the outermost modification bracket closes.

## Handler naming

- `*Notification` → the class name minus `Notification`
  (`SongDidChangeNotification` → `songDidChange`)
- `*Command` → `handle` + the class name minus `Command`
  (`SaveCommand` → `handleSave`)
- a catch-all → an `on*` prefix
- the same message handled at two priorities in one class → append a purpose
  suffix to each, so the two are told apart by name rather than by reading the
  annotations

## Priority

Higher priority runs first. The framework default is the lowest of the three
named constants, so a bare handler runs *after* every handler that names a
priority explicitly — which is the trap: adding a priority to one handler
silently reorders it against every bare one.

Specify a priority only where several handlers take the same message class and
the order between them matters. Any integer is valid; define one as a constant
rather than writing a bare number.

## Subscribing

Subscribe in the constructor. A class that does so **implements the disposable
interface** — that is the whole rule, and it is mechanical: the subscribe call is
the trigger, the interface is the obligation. A class that does not subscribe does
not implement it. See [lifecycle.md](../../docs/lifecycle.md) for what disposal
owes and who calls it.
