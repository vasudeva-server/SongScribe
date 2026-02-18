# Preferences Migration: Properties to Java Preferences API

## Overview

Migrate SongScribe's preference system from the flat `java.util.Properties` file-based storage (`~/.songscribe/props`) to the modern `java.util.prefs.Preferences` API with a typed singleton wrapper class.

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Create Prefs Class](#-phase-1-create-prefs-class) | ✅ Done | — |
| 2 | [Create defaults.json](#-phase-2-create-defaultsjson) | ✅ Done | — |
| 3 | [Update MainFrame Initialization](#-phase-3-update-mainframe-initialization) | ✅ Complete | — |
| 4 | [Migrate Call Sites](#-phase-4-migrate-call-sites) | ✅ Complete | — |
| 5 | [Update WhatsNew Logic](#-phase-5-update-whatsnew-logic) | ✅ Complete | — |
| 6 | [Clean Up Constants.java](#-phase-6-clean-up-constantsjava) | ✅ Complete | — |
| 7 | [Remove MainFrame Accessors](#-phase-7-remove-mainframe-accessors) | ✅ Complete | — |
| 8 | [Delete defprops](#-phase-8-delete-defprops) | ✅ Complete | — |
| 9 | [Test Migration Path](#-phase-9-test-migration-path) | ✅ Complete | — |

## Decisions

| Concern | Decision |
|---|---|
| Defaults source | Bundled JSON resource (`conf/defaults.json`), types inferred from JSON value types |
| Backing store | Platform-native `java.util.prefs.Preferences` (macOS plist, Windows registry) |
| Node path | Custom: `Preferences.userRoot().node("songscribe")` |
| Persistence | Immediate on each change (Preferences API default behavior) |
| Wrapper class | `songscribe.prefs.Prefs` (singleton) |
| API surface | Typed getters and setters (`getString`, `getInt`, `getLong`, `getBoolean`, and corresponding `put` overloads) |
| Key naming | Normalize all keys to camelCase; migration maps old names to new |
| Validation | None. Trust callers; UI controls already constrain input. |
| Change listeners | None. Components continue to manually react to preference changes. |
| Migration | One-time auto-migration from `~/.songscribe/props`, then delete old file |
| Dynamic keys | Replace `showwhatsnew<version>` with single `lastSeenWhatsNewVersion` string key |
| Constants cleanup | Remove preference key constants from `Constants.java` |
| Profiles | Out of scope. Composition profiles remain as `.properties` files managed by `ProfileManager`. |
| PropertiesStateStore | Out of scope. Already uses `java.util.prefs.Preferences` for file dialog state. |
| Old defprops file | Delete `src/main/resources/conf/defprops` once JSON replacement is in place |
| Dropped keys | `showMemoryUsage`, `showPublisherNewInfo`, `bookUploadUrl` — unused/dead code; not migrated and not added to `defaults.json` |
| `previousDirectory` | Dropped — superseded by `PropertiesStateStore`/`SystemFileChooser.StateStore`, which already persists the last file dialog directory via `java.util.prefs.Preferences`. Remove `recentFileDirectory` field and related code from `MainFrame` and `PlatformFileDialog`. |

## Architecture

### New Files

#### `src/main/resources/conf/defaults.json`

Replaces `defprops`. All preference keys with their default values, using JSON native types:

```json
{
  "playInsertingNote": false,
  "playWithRepeats": false,
  "defaultProfile": "Sri Chinmoy",
  "stripShortA": true,
  "autoSaveAfterStripShortA": true,
  "tipIndex": 0,
  "tempoChangePercent": 100,
  "exportDpi": 600,
  "showTips": true,
  "loopPlayback": false,
  "control": "MOUSE",
  "imageExportFilter": 1,
  "playbackNoteDuration": 78,
  "colorizeNote": true,
  "instrument": 0,
  "metric": false,
  "updateUrl": "http://www.songscribe.org/files/update/ss/",
  "lastAutoUpdate": 0,
  "autoUpdatePeriod": 604800000,
  "firstRun": true,
  "lastSeenWhatsNewVersion": ""
}
```

#### `src/main/java/songscribe/prefs/Prefs.java`

Singleton wrapper around `java.util.prefs.Preferences`.

```java
package songscribe.prefs;

public final class Prefs {

    private static final Prefs INSTANCE = new Prefs();
    private final Preferences prefs;
    private final Map<String, Object> defaults;

    public static Prefs getInstance() { return INSTANCE; }

    // Typed getters — fall back to defaults from JSON
    public String getString(String key) { ... }
    public int getInt(String key) { ... }
    public long getLong(String key) { ... }
    public boolean getBoolean(String key) { ... }

    // Typed setters — persist immediately
    public void put(String key, String value) { ... }
    public void put(String key, int value) { ... }
    public void put(String key, long value) { ... }
    public void put(String key, boolean value) { ... }

    // Reset a key to its default (removes from Preferences store)
    public void reset(String key) { ... }

    // Reset all keys to defaults
    public void resetAll() { ... }
}
```

**Initialization:**
1. Open `Preferences.userRoot().node("songscribe")`
2. Load `conf/defaults.json` from classpath into an in-memory `Map<String, Object>`
3. Check if migration is needed (see Migration section)

**Getter behavior:**
1. Read from `Preferences` node
2. If absent, return default from the JSON-derived map
3. If no default exists, throw `IllegalArgumentException` (indicates a programming error)

### Key Name Mapping (Old to New)

Keys dropped during migration (read from old file but not written to new store):
- `showmemusage` — unused
- `showpublishernewinfo` — unused
- `bookuploadurl` — unused
- `previousdirectory` — superseded by `SystemFileChooser.StateStore`

| Old Key | New Key |
|---|---|
| `playinsertingnote` | `playInsertingNote` |
| `withrepeat` | `playWithRepeats` |
| `defaultprofile` | `defaultProfile` |
| `strip-short-a` | `stripShortA` |
| `autosave-after-strip-short-a` | `autoSaveAfterStripShortA` |
| `tipindex` | `tipIndex` |
| `tempochange` | `tempoChangePercent` |
| `dpi` | `exportDpi` |
| `showtip` | `showTips` |
| `playcontinuously` | `loopPlayback` |
| `control` | `control` |
| `imageexportfilter` | `imageExportFilter` |
| `durationshortitude` | `playbackNoteDuration` |
| `colorizenote` | `colorizeNote` |
| `instrument` | `instrument` |
| `metric` | `metric` |
| `updateurl` | `updateUrl` |
| `lastautoupdate` | `lastAutoUpdate` |
| `autoupdateperiod` | `autoUpdatePeriod` |
| `firstrun` | `firstRun` |
| `showwhatsnew*` | `lastSeenWhatsNewVersion` |

## Migration

### One-Time Auto-Migration

On `Prefs` initialization:

1. Check if `~/.songscribe/props` exists
2. If yes:
   a. Load it as a `Properties` object
   b. For each old key in the migration map, write the mapped key to the `Preferences` node; silently skip dropped keys
   c. Special case: scan for `showwhatsnew*` keys, extract the highest version string, store as `lastSeenWhatsNewVersion`
   d. Delete `~/.songscribe/props`
3. If no: no migration needed (either fresh install or already migrated)

### Migration Map

A static `Map<String, String>` in `Prefs` maps old key names to new key names. This map is only used during migration and can be removed in a future version.

## Call Site Migration

### Before

```java
mainFrame.getProperties().getProperty(Constants.COLORIZE_NOTE)
mainFrame.getProperties().setProperty(Constants.COLORIZE_NOTE, Constants.TRUE_VALUE)
```

### After

```java
Prefs.getInstance().getBoolean("colorizeNote")
Prefs.getInstance().put("colorizeNote", true)
```

### MainFrame Changes

1. Remove `defaultProps` and `properties` fields
2. Remove `getProperties()` and `getDefaultProps()` methods
3. Remove properties file loading from constructor
4. Remove `properties.store()` from `handleQuit()` (persistence is now immediate)
5. Remove `PROPS_FILE` constant
6. Remove `recentFileDirectory` field, `getRecentFileDirectory()`, and `setRecentFileDirectory()` (superseded by `SystemFileChooser.StateStore`)

### PlatformFileDialog Changes

1. Remove `chooser.setCurrentDirectory(mainFrame.getRecentFileDirectory())` from the private constructor (FlatLaf handles directory persistence via `PropertiesStateStore`)
2. Remove `mainFrame.setRecentFileDirectory(...)` calls from `getFile()` and `getFiles()`
3. Remove the `mainFrame` field if it is no longer needed after the above removals

### Constants.java Changes

Remove all preference key constants:
- `PLAY_INSERTING_NOTE`, `INSTRUMENT_PROP`, `WITH_REPEAT_PROP`, `LOOP_PLAYBACK_PROP`,
  `TEMPO_CHANGE_PROP`, `CONTROL_PROP`, `COLORIZE_NOTE`, `TIP_INDEX`, `SHOW_TIP`,
  `SHOW_WHATS_NEW`, `TRUE_VALUE`, `FALSE_VALUE`, `DPI_PROP`, `PREVIOUS_DIRECTORY`,
  `SHOW_MEM_USAGE`, `DEFAULT_PROFILE_PROP`, `FIRST_RUN`, `IMAGE_EXPORT_FILTER_PROP`,
  `METRIC`, `PLAYBACK_NOTE_DURATION_PROP`, `SHOW_PUBLISHER_NEW_INFO`,
  `STRIP_SHORT_A_PROP`, `LAST_AUTO_UPDATE`, `AUTO_UPDATE_PERIOD`

### WhatsNew Logic Change

**Before:**
```java
var whatsNewProp = Constants.SHOW_WHATS_NEW + Version.PUBLIC_VERSION;
if (properties.getProperty(whatsNewProp) == null && ...) {
    properties.setProperty(whatsNewProp, Constants.TRUE_VALUE);
    new WhatsNewDialog().setVisible(true);
}
```

**After:**
```java
var prefs = Prefs.getInstance();
var lastSeen = prefs.getString("lastSeenWhatsNewVersion");
if (!Version.PUBLIC_VERSION.equals(lastSeen) && ...) {
    prefs.put("lastSeenWhatsNewVersion", Version.PUBLIC_VERSION);
    new WhatsNewDialog().setVisible(true);
}
```

## Files Deleted

- `src/main/resources/conf/defprops`

## Files Modified

- `MainFrame.java` — Remove properties fields, loading, saving, and accessor methods; remove `recentFileDirectory` and related methods
- `PlatformFileDialog.java` — Remove `setCurrentDirectory` call and `setRecentFileDirectory` calls
- `Constants.java` — Remove all preference key constants and `TRUE_VALUE`/`FALSE_VALUE`
- All dialogs and classes that call `mainFrame.getProperties()` — Switch to `Prefs.getInstance()`

## Files Created

- `src/main/resources/conf/defaults.json`
- `src/main/java/songscribe/prefs/Prefs.java`

---

## ✅ Phase 1: Create Prefs Class

Create `src/main/java/songscribe/prefs/Prefs.java` with:
- Singleton instance
- JSON defaults loading from `conf/defaults.json`
- Typed getters (`getString`, `getInt`, `getLong`, `getBoolean`) with fallback to defaults
- Typed `put` overloads for immediate persistence
- `reset(key)` and `resetAll()` methods
- One-time auto-migration logic (reads old props file, maps keys, skips dropped keys, deletes old file)
- Static migration map of old key names to new camelCase names

See [Architecture](#architecture) and [Migration](#migration) for full details.

## ✅ Phase 2: Create defaults.json

Create `src/main/resources/conf/defaults.json` with all 21 preference keys and their default values using JSON native types.

See [Architecture > defaults.json](#srcmainresourcesconfdefaultsjson) for the full JSON content.

## ✅ Phase 3: Update MainFrame Initialization

Update `MainFrame` to initialize the `Prefs` singleton at startup by calling `Prefs.getInstance()` early in the constructor, replacing the current `defaultProps`/`properties` loading block.

See [Call Site Migration > MainFrame Changes](#mainframe-changes) for the full list of removals.

## ✅ Phase 4: Migrate Call Sites

Update all dialogs and classes that call `mainFrame.getProperties()` to use `Prefs.getInstance()` with typed getters and setters.

Affected classes include: `PreferencesDialog`, `InstrumentDialog`, `ExportMidiDialog`, `ResolutionDialog`, `DoNotShowMessage`, `TipFrame`, and any other class accessing properties via `mainFrame`.

Also update `PlatformFileDialog` per [PlatformFileDialog Changes](#platformfiledialog-changes).

## ✅ Phase 5: Update WhatsNew Logic

Replace the `showwhatsnew<version>` key pattern in `MainFrame` with a comparison against `lastSeenWhatsNewVersion`.

See [Call Site Migration > WhatsNew Logic Change](#whatsnew-logic-change).

## ✅ Phase 6: Clean Up Constants.java

Remove all preference key string constants and `TRUE_VALUE`/`FALSE_VALUE` from `Constants.java`.

See [Call Site Migration > Constants.java Changes](#constantsjava-changes) for the full list.

## ✅ Phase 7: Remove MainFrame Accessors

Remove `getProperties()` and `getDefaultProps()` from `MainFrame` and the `IMainFrame` interface. By this phase, no call sites should remain.

## ✅ Phase 8: Delete defprops

Delete `src/main/resources/conf/defprops`. The JSON file is the sole source of defaults from this point on.

## ✅ Phase 9: Test Migration Path

Verify the one-time auto-migration works end-to-end:
- With an existing `~/.songscribe/props` file: confirm all values are correctly imported to the Preferences store and the old file is deleted
- Without an old file: confirm the app starts cleanly with JSON defaults
- Confirm `lastSeenWhatsNewVersion` is correctly populated from any `showwhatsnew*` keys found in the old file
- Confirm dropped keys (`showmemusage`, `showpublishernewinfo`, `bookuploadurl`, `previousdirectory`) are silently ignored during migration
