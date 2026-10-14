# Preferences

Rules for code that reads, writes or adds a user preference through `Prefs`.

## Rules

- Reach preferences only through `Prefs`, never through the file.
- Keep global preferences in `PrefsKey` and `user-defaults.json`, and per-song document settings in `SystemPrefsKey` and `system-defaults.json`.
- Give every constant in an enum an entry in that enum's own defaults file, and never put a key in both files, because nothing in the build checks this and a scalar read of a key with no default throws at runtime.
- Do not write a null guard around a getter, because none returns `null`.
- Cast a number read from a map preference through `Number`, because Gson deserializes numbers inside nested objects as `Double`.

  ```java
  int width = ((Number) geometry.get("width")).intValue();
  ```

- Remove a key from a stored map by reading the map, removing the key and writing it back, or by resetting the whole key, because `putMap` merges.
- Write preferences on the EDT only.
- Bind a control to the `Property` that `Prefs` hands out, and do not subscribe to the notification for it. See [bindings](bindings.md).
- Store an enum-valued preference through `PrefsValue` and read it with the choice getter; do not convert at the bind site and do not give the enum its own `fromKey`.
- Test for `PrefsKey.ALL` in a `@Handler` of `PrefsDidChangeNotification` that reacts to one key, because a reset of everything does not name the key.

### Adding a preference

1. Decide whether it is global or a per-song document setting; that decides the enum and the defaults file.
2. Add a camelCase entry with a sensible default to the defaults file.
3. Add the `UPPER_SNAKE_CASE` constant to the matching enum, with a constructor argument that exactly matches the JSON key, in alphabetical position.

### Removing a preference

1. Delete the `PrefsKey` constant and its `user-defaults.json` entry.
2. Add the JSON key to `PrefsUpgrade.OBSOLETE_KEYS`, because the old key stays in existing users' `prefs.json`.

Deleting a `SystemPrefsKey` constant and its `system-defaults.json` entry is the whole removal, because system keys are never written to `prefs.json`.
