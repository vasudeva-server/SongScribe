# Open Recent Feature Spec

## Overview

Add an **Open Recent** submenu to the **File** menu, placed immediately after the
**Open** item. The submenu displays up to the last 10 recently-used documents in
most-recently-used (MRU) order and provides a **Clear Recents** action.

---

## Menu Structure

### When the list has items

```
File
  New
  Open...
  Open Recent  ▶  My Song.sng
                  Another Song.sng
                  Theme.sng — Folder A      ← disambiguated (collision)
                  Theme.sng — Other/Folder B ← disambiguated
                  …
                  ─────────────────────────
                  Clear Recents
  ─────────────
  Save
  …
```

### When the list is empty

```
File
  New
  Open...
  Open Recent  ▶  No Recent Documents   ← disabled (grayed out)
  ─────────────
  Save
  …
```

When the list is empty: the separator and **Clear Recents** item are **not shown**.
Only the single disabled placeholder "No Recent Documents" appears in the submenu.

---

## Entry Labels

Each entry displays the filename by default (e.g. `My Song.sng`).

**Disambiguation:** When two or more entries in the current list share the same
filename, path segments are added progressively (walking up from the immediate
parent) until each label is unique. If the entries are still not unique after
exhausting all path components, the full absolute path is shown.

**Home directory tilde substitution:** When displaying any path component (in
the disambiguated suffix), replace the user's home directory prefix with `~`.
Example: `/Users/alice/Documents/Songs` → `~/Documents/Songs`.

Paths are always stored as absolute paths internally.

---

## List Capacity and Ordering

- Maximum **10** entries (hardcoded constant).
- Ordered most-recently-used first (position 0 = most recent).
- **Duplicate handling:** If a file being added is already in the list, move it
  to the top. The list never contains duplicate paths.
- No keyboard accelerator shortcuts on submenu items.

---

## Trigger Events

An entry is added to (or promoted to the top of) the recents list on:

1. **File › Open** — successful open of an existing file.
2. **File › Save As** — successful save to any path, including the first explicit
   save of a new (previously unsaved) document. Both cases are treated identically.

"New" documents that have never been saved are never added to recents.

---

## Playback State

All items in the Open Recent submenu (including Clear Recents) are **disabled
during MIDI playback**, consistent with how `OpenAction` uses `DISABLE_WHEN_PLAYING`.

---

## Unsaved Changes

Opening a file via Open Recent follows the **same unsaved-changes prompt** as
File › Open. If the current document has unsaved changes, the existing save/discard
dialog is shown before the recent file is opened.

---

## Missing Files

When the user clicks a recent entry whose file no longer exists on disk
(deleted, renamed, or on an unmounted volume):

1. Show an error dialog using the existing application error dialog infrastructure.
2. Remove the entry from the recents list and persist the change.
3. Do not attempt to open a file.

The list is **not** validated proactively on menu build or application startup.
Entries are only removed on click.

---

## Clear Recents

- Clears the entire list immediately with **no confirmation dialog**.
- After clearing, the submenu updates to show the empty state (single disabled
  "No Recent Documents" item; separator and Clear Recents item hidden).

---

## Persistence

Recent file paths are stored in the existing `prefs.json` file under the key
`recentFiles` as a JSON array of absolute path strings.

This requires extending `Prefs` with a string-list API:

```java
public List<String> getStringList(@NotNull String key)
public void putStringList(@NotNull String key, @NotNull List<String> value)
```

The array is stored directly in the `JsonObject` that `Prefs` writes. The
`loadStore` and `save` methods must be updated to handle `JsonArray` values in
addition to `JsonPrimitive`. No entry is added to `defaults.json` for
`recentFiles`; an absent key is treated as an empty list.

---

## macOS System Integration

On macOS, after successfully adding a file to the in-app recents list, call
`Desktop.getDesktop().addRecentDocument(file)` to register the file with the
system-level Open Recent (visible in the Dock and the App menu).

This call is wrapped in a `try/catch` and gated behind `SystemInfo.isMacOS`.
Any failure (e.g. when running as an unbundled JAR) is logged at `FINE` level
and silently ignored. The in-app menu is unaffected by whether the OS call
succeeds.

---

## Architecture

### `RecentDocumentsManager` (`songscribe.prefs` package)

A new singleton class responsible for all recents state. It owns the list and is
the single point of mutation.

Public API:

```java
public static RecentDocumentsManager getInstance()
public List<Path> getRecents()          // returns unmodifiable snapshot
public void add(Path absolutePath)      // add or promote to top; persists; posts message
public void remove(Path absolutePath)   // remove entry; persists; posts message
public void clear()                     // clear all; persists; posts message
```

Internally, `add` deduplicates by normalized absolute path before inserting at
position 0, then trims the list to the 10-item maximum.

After any mutation, `RecentDocumentsManager` calls:
```java
MessageCenter.post(new RecentDocumentsChangedMessage());
```

### `RecentDocumentsChangedMessage` (`songscribe.ui.message` package)

A new `Message` subclass (Kotlin, following the existing `*Message` pattern)
with no payload. Posted whenever the list changes.

### `MenuController` changes

`MenuController` subscribes to `RecentDocumentsChangedMessage`. When received,
it rebuilds the Open Recent submenu from the current list returned by
`RecentDocumentsManager.getInstance().getRecents()`.

The submenu is rebuilt **eagerly** on every list-change event (open, save-as,
first save of new doc, clear, remove-on-missing). The `initFileMenu` method
holds a reference to the submenu (`JMenu`) so it can be rebuilt in-place without
reconstructing the entire File menu.

### `OpenRecentAction` (`songscribe.ui.action` package)

A per-item action constructed with a `Path`. On `actionPerformed`:

1. Check if the file exists; if not, show error dialog, call
   `RecentDocumentsManager.getInstance().remove(path)`, return.
2. Otherwise, post the appropriate open-file message (reusing the existing open
   flow, which handles the unsaved-changes prompt).
3. The action is flagged with `DISABLE_WHEN_PLAYING`.

### `ClearRecentsAction` (`songscribe.ui.action` package)

Calls `RecentDocumentsManager.getInstance().clear()` on `actionPerformed`.
Flagged with `DISABLE_WHEN_PLAYING`.

### Integration Points

- **`OpenAction` / open-file handler**: After a successful file open, call
  `RecentDocumentsManager.getInstance().add(openedPath)`.
- **Save As handler**: After a successful save-as, call
  `RecentDocumentsManager.getInstance().add(savedPath)`.
- **macOS call site**: Inside `RecentDocumentsManager.add()`, after updating the
  list, make the best-effort `Desktop.addRecentDocument()` call.

---

## Label Disambiguation Algorithm

```
1. Build label map: filename → list of paths that share that filename.
2. For each group with more than one path:
   a. Walk up path components (parent, grandparent, …) one level at a time.
   b. At each level, build a candidate suffix from that ancestor down
      (with ~ substitution for the home directory).
   c. Check if all candidates in the group are now unique.
   d. If yes, use "filename — suffix" as the label for each.
   e. If no candidate depth makes them unique, fall back to the full
      absolute path (with ~ substitution) as the label.
3. Paths with no collision continue to use the filename alone.
```

---

## Non-Goals

- Configurable list size (hardcoded at 10).
- Numbered keyboard shortcuts on submenu items.
- Displaying composition metadata (title, date accessed) as part of the label.
- Proactive validation of all paths on menu open or app startup.
- Tooltips showing full paths (may be added later without a spec change).
