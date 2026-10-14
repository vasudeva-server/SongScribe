# Messages

## Naming

- Name an action request `*Command` and put it in `songscribe.message.command`.
- Name a state change `*DidChangeNotification` and put it in `songscribe.message.notification`.
- Make a message immutable: final fields, getters only, data through the constructor.
- Name a handler for a `*Notification` after the class minus `Notification`
  (`SongDidChangeNotification` becomes `songDidChange`).
- Name a handler for a `*Command` `handle` plus the class minus `Command` (`SaveCommand` becomes
  `handleSave`).
- Prefix a catch-all handler with `on`.
- Append a purpose suffix to each handler when one class handles the same message at two
  priorities, so that the two are told apart by name.

## Posting

- Never construct a change notification for the document directly, because it is posted
  automatically when the outermost modification bracket closes.

## Priority

- Specify a priority only where several handlers take the same message class and the order
  between them matters, because a bare handler runs after every handler that names a priority,
  and naming one on a single handler silently reorders it against every bare one.

## Handlers

- Give messages that share a reaction an abstract supertype, and handle the supertype once,
  because a handler also receives the subtypes of its parameter type.

## Subscribing

- Subscribe in the constructor, and implement `Disposable` in every class that does.
- Keep a strong reference to a subscriber for as long as it should receive messages, because the
  bus holds subscribers weakly.
- Annotate a process-lifetime subscriber that has no owner with
  `@Listener(references = References.Strong)` and drop the reference; a static field kept only to
  prevent collection means the annotation is missing.
- Dispose a subscriber to retire it, because it keeps receiving messages after its last
  reference is dropped until the collector runs.
