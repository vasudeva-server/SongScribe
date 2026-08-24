# Preferences System

SongScribe stores user preferences as JSON. The `songscribe.prefs` package owns all
read/write access, reached through the `Prefs` singleton — never by touching the file.

## Two key enums, two defaults files, and they must stay disjoint

There are two kinds of preference and the distinction is load-bearing:

- **Global preferences** — appearance, playback, export, and the rest of the user's
  configuration. Their keys are `PrefsKey` constants, their defaults live in
  `user-defaults.json`, and they are merged into the user's `prefs.json` on every save.
- **Per-song document settings** — the fonts that belong to the song rather than to the
  user. Their keys are `SystemPrefsKey` constants, their defaults live in
  `system-defaults.json`, and they are **never** persisted: they are read-only, and
  `save()` strips them from the output.

Every constant in one enum must have an entry in that enum's own defaults file, and no
key may appear in both files. Nothing in the build checks this, and the failure is at
runtime: a scalar read of a key with no default throws. When reviewing a change that
adds an enum constant, confirm the matching defaults entry exists.

The compiler enforces the read-only half: the persisting API does not accept a
`SystemPrefsKey`, so a per-song font setting cannot be written by mistake. System keys
resolve through the system-default getters, which always answer the bundled value.

`prefs.json` lives at `~/Library/Preferences/SongScribe/` on macOS,
`%APPDATA%\SongScribe\` on Windows, `~/.songscribe/` on Linux. Both defaults files are
bundled resources in `src/main/resources/conf/`.

## What a getter does when there is no default

**Never write a null guard around a getter — none of them return `null`.** What they do
instead splits by kind:

- **Scalar getters** (string, int, long, boolean) **throw** when there is neither a user
  value nor a default. A missing default is a programming error, and they say so.
- **Collection getters** (list, map) return an **empty** list or map.

### Reading a map preference

Gson deserializes JSON numbers inside nested objects as `Double`, **not** `Integer` or
`Long`. Cast numeric values through `Number`:

```java
int width = ((Number) geometry.get("width")).intValue();   // correct
int bad   = (Integer) geometry.get("width");               // ClassCastException
```

## Writing

Every mutation writes the full merged file to disk and posts
`PrefsDidChangeNotification` synchronously before returning.

**The two collection writers do not agree, and the difference is silent.**
`putStringList` **replaces** the stored list wholesale; `putMap` **merges** the given
entries into the existing map, keeping existing keys the call did not name. To remove a
key from a stored map, read it, remove the key, and write the result back — or reset the
whole key.

**Every write is EDT-only.** Reads are unrestricted. Writes are restricted because they
notify the observable views described below, and the binding framework is unsynchronized
by design.

## Reacting to changes

There are two ways, and they are not alternatives — pick by what you are reacting *with*.

### A bound control: use an observable view

`Prefs` hands out a `Property` per key, which reads and writes through the store and
notifies whenever its key changes, whoever changed it. Bind a control to one and the
control follows the store in both directions; there is nothing to subscribe and nothing
to unregister.

```java
bindings().bindBidirectional(
    Prefs.choiceProperty(PrefsKey.PAGE_SIZE, PageModel.Size.class),
    Controls.radioGroup(buttonsByPageSize)
);
```

An enum-valued preference implements `PrefsValue`, which supplies the string each
constant is stored as; the choice getter decodes it case-insensitively, falling back to
the key's default when the stored value names no constant. **Do not write a conversion at
the bind site and do not give an enum its own `fromKey`.**

Views are created per call and cost nothing to drop; the observations a caller takes on
one are cancelled by `Bindings.dispose`.

### A consequence that is not a value: use the message

Every mutation posts `PrefsDidChangeNotification` after a successful write, carrying the
key that changed — or `PrefsKey.ALL` when everything was reset. **A `@Handler` reacting to
a specific key must also test for `ALL`**, since a reset-everything will not name the
individual key.

**Views are notified before the message is posted**, so a handler runs with the bound
controls already settled.

## Adding a preference

A preference is one enum constant plus one matching entry in that enum's defaults file.
**They are always added together.**

1. Decide which kind it is — global or per-song document setting — using the split above.
   That decides both the enum and the file.
2. Add a camelCase entry to the defaults file with a sensible default value.
3. Add the `UPPER_SNAKE_CASE` constant to the matching enum. Its constructor argument
   must **exactly** match the JSON key string. Keep the enum alphabetical; the existing
   constants are sorted, so insert in place.

## Removing a preference

Deleting a `PrefsKey` constant and its `user-defaults.json` entry is **not enough** —
existing users' `prefs.json` files still contain the old key. Add the JSON key string to
`PrefsUpgrade.OBSOLETE_KEYS`, which strips it from the store on the next launch.

A `SystemPrefsKey` needs no such cleanup: system keys are never written to `prefs.json`,
so deleting the constant and its `system-defaults.json` entry is the whole of it.
