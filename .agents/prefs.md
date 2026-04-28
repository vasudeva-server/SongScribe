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
List<String> recent = prefs.getStringList(PrefsKey.RECENT_FILES);
```

If the key has no user-set value the default from `defaults.json` is returned. Requesting a key with no default throws `IllegalArgumentException`.

## Writing Preferences

Every mutation writes to disk and posts `PrefsDidChangeNotification` synchronously before returning.

```java
prefs.put(PrefsKey.TITLE_FONT, "LatoPlus-Bold");   // String
prefs.put(PrefsKey.EXPORT_DPI, 300);               // int (stored as Long)
prefs.put(PrefsKey.LOOP_PLAYBACK, true);           // boolean
prefs.putStringList(PrefsKey.RECENT_FILES, paths);  // List<String>
prefs.reset(PrefsKey.EXPORT_DPI);                   // removes override, notifies with EXPORT_DPI
prefs.resetAll();                                   // clears all overrides, notifies with ALL
```

## Reacting to Changes

Every mutation (`put`, `putStringList`, `reset`, `resetAll`) posts `PrefsDidChangeNotification` after a successful write. The notification carries the key that changed, or `PrefsKey.ALL` for `resetAll`.

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

1. Add a camelCase entry to `src/main/resources/conf/defaults.json` with a sensible default value.
2. Add the matching constant to `PrefsKey`, mapping to the same camelCase key string.
3. Access via `Prefs.getInstance().get*(PrefsKey.YOUR_NEW_KEY)`.

## Adding a New PrefsKey Constant

The enum constant name follows `UPPER_SNAKE_CASE`. The constructor argument must exactly match the JSON key in `defaults.json`:

```java
MY_NEW_PREF("myNewPref"),   // added to PrefsKey
```

```json
"myNewPref": "defaultValue"  // added to defaults.json
```
