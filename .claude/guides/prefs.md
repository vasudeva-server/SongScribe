# Preferences System

SongScribe stores user preferences as JSON. The `songscribe.prefs` package owns all read/write access.

## Core Classes

| Class | Role |
|-------|------|
| `Prefs` | Singleton — reads, writes, and persists preferences (all access via static methods) |
| `PrefsKey` | Enum — type-safe key for every **global** preference (persisted to `prefs.json`) |
| `SystemPrefsKey` | Enum — type-safe key for every **per-song** document setting (fonts); read-only, never persisted |
| `PrefsDidChangeNotification` | Message posted after any `put(...)`, `putStringList(...)`, or `reset(...)` call |

## Storage

Preferences are stored in `prefs.json` at a platform-specific location:

| Platform | Path |
|----------|------|
| macOS | `~/Library/Preferences/SongScribe/prefs.json` |
| Windows | `%APPDATA%\SongScribe\prefs.json` |
| Linux | `~/.songscribe/prefs.json` |

Default values are split across two bundled resources in `src/main/resources/conf/`:

- `user-defaults.json` — global preferences (appearance, playback, export, etc.). These are merged with user overrides when writing `prefs.json`.
- `system-defaults.json` — per-song document settings (font names and sizes). These provide defaults for new songs but are **never** written to the user's `prefs.json`.

Each enum maps to exactly one file: every `PrefsKey` constant must appear in `user-defaults.json`, every `SystemPrefsKey` constant in `system-defaults.json`. The two files' key sets must stay disjoint. `save()` merges user-default keys with user overrides; system-default keys are stripped from the output before writing.

## Reading Preferences

Always go through the `Prefs` singleton:

```java
String size    = Prefs.getString(PrefsKey.PAGE_SIZE);
int    dpi     = Prefs.getInt(PrefsKey.EXPORT_DPI);
long   version = Prefs.getLong(PrefsKey.LAST_SEEN_WHATS_NEW_VERSION);
boolean loop   = Prefs.getBoolean(PrefsKey.LOOP_PLAYBACK);
List<String> recent  = Prefs.getStringList(PrefsKey.RECENT_FILES);
Map<String, Object> g = Prefs.getMap(PrefsKey.DIALOG_GEOMETRY);
```

If the key has no user-set value the default from `user-defaults.json` is returned.

**Per-song document settings** (fonts) use `SystemPrefsKey` and are read-only — there is no `getString`/`getInt` overload for them. Resolve them through the system-default getters, which always return the bundled `system-defaults.json` value (no user override):

```java
String titleFont = Prefs.getDefaultString(SystemPrefsKey.TITLE_FONT);
int    titleSize = Prefs.getDefaultInt(SystemPrefsKey.TITLE_FONT_SIZE);
```

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
Map<String, Object> geometry = Prefs.getMap(PrefsKey.DIALOG_GEOMETRY);
int width = ((Number) geometry.get("width")).intValue();   // correct
int bad   = (Integer) geometry.get("width");               // ClassCastException
```

## Writing Preferences

Every mutation writes the full merged file to disk and posts `PrefsDidChangeNotification` synchronously before returning.

Only global keys (`PrefsKey`) can be written — the `put*`/`reset` methods do not accept a `SystemPrefsKey`, so per-song font settings cannot be persisted by mistake.

```java
Prefs.put(PrefsKey.PAGE_SIZE, "letter");           // String
Prefs.put(PrefsKey.EXPORT_DPI, 300);               // int (stored as Long)
Prefs.put(PrefsKey.LOOP_PLAYBACK, true);           // boolean
Prefs.putStringList(PrefsKey.RECENT_FILES, paths); // List<String> — replaces wholesale
Prefs.putMap(PrefsKey.DIALOG_GEOMETRY, entries);   // Map<String, ?> — merges into existing
Prefs.reset(PrefsKey.EXPORT_DPI);                  // removes override, notifies with EXPORT_DPI
Prefs.resetAll();                                  // clears all overrides, notifies with ALL
```

Note the difference in collection semantics:

- `putStringList` **replaces** the stored list entirely.
- `putMap` **merges** the given entries into the existing map (existing keys not in `entries` are kept). To remove a key from a stored map you must read it, remove the key, and `putMap` the result — or `reset` the whole key.

## Reacting to Changes

There are two ways, and they are not alternatives — pick by what you are reacting *with*.

### A bound control: use an observable view

`Prefs` hands out a `Property` per key — `intProperty`, `booleanProperty`, `choiceProperty` — which reads and writes through the store and notifies whenever its key changes, whoever changed it. Bind a control to one and the control follows the store in both directions; there is nothing to subscribe and nothing to unregister.

```java
bindings().bindBidirectional(
    Prefs.choiceProperty(PrefsKey.PAGE_SIZE, PageModel.Size.class),
    Controls.radioGroup(buttonsByPageSize)
);
```

An enum-valued preference implements `PrefsValue`, which supplies the string each constant is stored as; `Prefs.getChoice` decodes it, case-insensitively, falling back to the key's default when the stored value names no constant. Do not write a conversion at the bind site and do not give an enum its own `fromKey`.

Views are created per call and cost nothing to drop; the observations a caller takes on one are cancelled by `Bindings.dispose`. Because they feed the binding framework, which is unsynchronized by design, **every `put`, `reset` and `resetAll` is EDT-only**. Reads are unrestricted.

### A consequence that is not a value: use the message

Every mutation (`put`, `putStringList`, `putMap`, `reset`, `resetAll`) posts `PrefsDidChangeNotification` after a successful write. The notification carries the key that changed, or `PrefsKey.ALL` for `resetAll`. A `@Handler` that reacts to a specific key should always also check for `PrefsKey.ALL`, since `resetAll` will not name the individual key.

**Views are notified before the message is posted**, so a handler runs with the bound controls already settled.

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

A preference is one enum constant plus one matching entry in the appropriate defaults file. They must always be added together.

Decide which file — and which enum — the new key belongs in:

- **Per-song document setting** (e.g. a font name or size that belongs to the song, not the user's global config): add to `src/main/resources/conf/system-defaults.json` and add the constant to `SystemPrefsKey`. Keys in this file are never written to the user's `prefs.json`; they are read-only and resolved only through `Prefs.getDefaultString` / `Prefs.getDefaultInt`. The persisting API (`Prefs.put`, `Prefs.getString`, etc.) does not accept a `SystemPrefsKey`, so a system key cannot be written by mistake — the compiler rejects it.
- **Global preference** (e.g. a playback option, UI setting, or export parameter): add to `src/main/resources/conf/user-defaults.json` and add the constant to `PrefsKey`. Keys in this file are merged into the user's `prefs.json` on every save.

Steps:

1. Add a camelCase entry to the appropriate defaults file with a sensible default value.
2. Add the matching constant to `PrefsKey` (global) or `SystemPrefsKey` (per-song). The constant name is `UPPER_SNAKE_CASE`; its constructor argument must **exactly** match the JSON key string. Keep the enum in alphabetical order — the existing constants are sorted, so insert the new one in place.
3. Access a global key via `Prefs.get*(PrefsKey.YOUR_NEW_KEY)`; a system key via `Prefs.getDefault*(SystemPrefsKey.YOUR_NEW_KEY)`.

```java
MY_NEW_PREF("myNewPref"),   // added to PrefsKey, in alphabetical position
```

```json
"myNewPref": "defaultValue"  // added to the appropriate defaults file
```

When reviewing a change that adds an enum constant, confirm the corresponding entry exists in the matching defaults file — `PrefsKey` ↔ `user-defaults.json`, `SystemPrefsKey` ↔ `system-defaults.json`. The two files' key sets must stay disjoint (a scalar getter on a key with no default throws at runtime).

## Removing a Preference

Deleting a `PrefsKey` constant and its `user-defaults.json` entry is not enough — existing users' `prefs.json` files will still contain the old key. Add the JSON key string to the `OBSOLETE_KEYS` list in `PrefsUpgrade`; it is stripped from the store on the next launch.

Removing a `SystemPrefsKey` constant needs no such cleanup: system keys are never written to `prefs.json`, so deleting the constant and its `system-defaults.json` entry is sufficient.
