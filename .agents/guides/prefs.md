# Preferences System

SongScribe stores user preferences as JSON. The `songscribe.prefs` package owns all read/write access.

## Core Classes

| Class | Role |
|-------|------|
| `Prefs` | Singleton — reads, writes, and persists preferences |
| `PrefsKey` | Enum — type-safe key for every preference value |
| `PrefsDidChangeNotification` | Message posted after any `put(...)`, `putStringList(...)`, or `reset(...)` call |

## Storage

Preferences are stored in `prefs.json` at a platform-specific location:

| Platform | Path |
|----------|------|
| macOS | `~/Library/Preferences/SongScribe/prefs.json` |
| Windows | `%APPDATA%\SongScribe\prefs.json` |
| Linux | `~/.songscribe/prefs.json` |

Default values are bundled in the JAR at `src/main/resources/conf/defaults.json`. Every key in `PrefsKey` must have a corresponding entry there. `save()` merges defaults with user overrides, so the written file always contains all keys.

## Reading Preferences

Always go through the `Prefs` singleton:

```java
var prefs = Prefs.getInstance();

String font    = prefs.getString(PrefsKey.TITLE_FONT);
int    dpi     = prefs.getInt(PrefsKey.EXPORT_DPI);
long   version = prefs.getLong(PrefsKey.LAST_SEEN_WHATS_NEW_VERSION);
boolean loop   = prefs.getBoolean(PrefsKey.LOOP_PLAYBACK);
List<String> recent  = prefs.getStringList(PrefsKey.RECENT_FILES);
Map<String, Object> g = prefs.getMap(PrefsKey.DIALOG_GEOMETRY);
```

If the key has no user-set value the default from `defaults.json` is returned.

Behavior when there is **no default either** depends on the getter:

| Getter | No user value *and* no default |
|--------|-------------------------------|
| `getString`, `getInt`, `getLong`, `getBoolean` | throws `IllegalArgumentException` |
| `getStringList` | returns an empty list |
| `getMap` | returns an empty map |

So the scalar getters treat a missing default as a programming error, while the collection getters degrade to an empty result. Do not write null guards around any getter — none of them return `null`.

### Reading Map preferences

`getMap` returns the JSON object stored at the key. Gson deserializes JSON numbers inside nested objects as `Double`, **not** `Integer` or `Long`, so cast numeric values through `Number`:

```java
Map<String, Object> geometry = prefs.getMap(PrefsKey.DIALOG_GEOMETRY);
int width = ((Number) geometry.get("width")).intValue();   // correct
int bad   = (Integer) geometry.get("width");               // ClassCastException
```

## Writing Preferences

Every mutation writes the full merged file to disk and posts `PrefsDidChangeNotification` synchronously before returning.

```java
prefs.put(PrefsKey.TITLE_FONT, "LatoPlus-Bold");    // String
prefs.put(PrefsKey.EXPORT_DPI, 300);                // int (stored as Long)
prefs.put(PrefsKey.LOOP_PLAYBACK, true);            // boolean
prefs.putStringList(PrefsKey.RECENT_FILES, paths);  // List<String> — replaces wholesale
prefs.putMap(PrefsKey.DIALOG_GEOMETRY, entries);    // Map<String, ?> — merges into existing
prefs.reset(PrefsKey.EXPORT_DPI);                   // removes override, notifies with EXPORT_DPI
prefs.resetAll();                                   // clears all overrides, notifies with ALL
```

Note the difference in collection semantics:

- `putStringList` **replaces** the stored list entirely.
- `putMap` **merges** the given entries into the existing map (existing keys not in `entries` are kept). To remove a key from a stored map you must read it, remove the key, and `putMap` the result — or `reset` the whole key.

## Reacting to Changes

Every mutation (`put`, `putStringList`, `putMap`, `reset`, `resetAll`) posts `PrefsDidChangeNotification` after a successful write. The notification carries the key that changed, or `PrefsKey.ALL` for `resetAll`. A `@Handler` that reacts to a specific key should always also check for `PrefsKey.ALL`, since `resetAll` will not name the individual key.

```java
// In constructor
MessageCenter.subscribe(this);

@Handler
public void prefsDidChange(PrefsDidChangeNotification notification) {
    if (notification.getKey() == PrefsKey.APPEARANCE || notification.getKey() == PrefsKey.ALL) {
        applyAppearance();
    }
}
```

## Adding a New Preference

A preference is one `PrefsKey` constant plus one matching entry in `defaults.json`. They must always be added together.

1. Add a camelCase entry to `src/main/resources/conf/defaults.json` with a sensible default value.
2. Add the matching constant to `PrefsKey`. The constant name is `UPPER_SNAKE_CASE`; its constructor argument must **exactly** match the JSON key string. Keep the enum in alphabetical order — the existing constants are sorted, so insert the new one in place.
3. Access via `Prefs.getInstance().get*(PrefsKey.YOUR_NEW_KEY)`.

```java
MY_NEW_PREF("myNewPref"),   // added to PrefsKey, in alphabetical position
```

```json
"myNewPref": "defaultValue"  // added to defaults.json
```

When reviewing a change that adds a `PrefsKey` constant, confirm the corresponding `defaults.json` entry exists — a scalar getter on a key with no default throws at runtime.

## Removing a Preference

Deleting a `PrefsKey` constant and its `defaults.json` entry is not enough — existing users' `prefs.json` files will still contain the old key. Add the JSON key string to the `OBSOLETE_KEYS` list in `Prefs`; it is stripped from the store on the next launch.
