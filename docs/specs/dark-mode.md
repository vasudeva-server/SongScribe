# Dark Mode Feature Spec

## Overview

Add dark mode support to SongScribe with dynamic switching based on system
appearance or user preference. FlatLaf provides the foundation for light/dark
theming, but several UI elements need adaptation — primarily toolbar buttons,
hardcoded colors, and SVG icons.

The score page (notation rendering area) never changes its appearance. Only
the application chrome (toolbar, menus, dialogs, score surround) adapts to the
selected theme.

---

## Appearance Preference

A new `appearance` preference is stored in `prefs.json`:

```json
{
  "appearance": "system"
}
```

**Values**: `"system"` | `"light"` | `"dark"`

**Default**: `"system"` — follows the OS theme. This applies to both new
installations and existing users upgrading to this version.

**Fallback**: On platforms where system theme detection is unavailable (some
Linux DEs), `"system"` falls back to `"light"`.

---

## AppearanceManager

A new dedicated class, `AppearanceManager`, encapsulates all theme management
logic:

- Determining which FlatLaf LAF to install based on preference and platform
- Listening for OS theme changes (when preference is `"system"`)
- Performing runtime theme switches with animated crossfade
- Reading and writing the `appearance` preference

### Platform-Specific LAF Selection

| Preference | macOS | Other platforms |
|---|---|---|
| light | `FlatMacLightLaf` | `FlatLightLaf` |
| dark | `FlatMacDarkLaf` | `FlatDarkLaf` |
| system | Resolved to light or dark based on OS, then as above | Same |

This mirrors the existing pattern where macOS uses `FlatMacLightLaf` and other
platforms use `FlatLightLaf`.

### OS Theme Change Listener

When `appearance` is `"system"`, `AppearanceManager` registers a listener via
`FlatLaf.setSystemColorChangeListener()` (or equivalent FlatLaf API) to detect
OS dark/light changes. When the OS theme changes, the app switches immediately
— no deferral during active editing, since theme changes are purely visual and
do not affect data.

When `appearance` is `"light"` or `"dark"`, the OS listener is not active.

### Runtime Theme Switching

Theme switches use `FlatAnimatedLafChange` for a smooth crossfade transition:

1. `FlatAnimatedLafChange.showSnapshot()`
2. Install the new LAF via `UIManager.setLookAndFeel()`
3. `FlatLaf.updateUI()`
4. `FlatAnimatedLafChange.hideSnapshotWithAnimation()`

`SwingUtilities.updateComponentTreeUI()` is called on all open windows to
propagate the new LAF to all Swing components.

### Initialization

`AppearanceManager` is initialized during app startup, called from
`UIUtils.initLaf()` (or replacing the LAF setup portion of it). It reads the
`appearance` preference from `Prefs`, resolves it to a concrete LAF class, and
installs it. If `"system"`, it also registers the OS change listener.

---

## FlatLaf Properties

All custom properties remain in a single `FlatLaf.properties` file, using
FlatLaf's `[dark]` and `[light]` conditional prefixes for theme-specific
values.

### Current Properties (Updated)

```properties
@borderColor = #c0c0c0
[dark]@borderColor = #505050

defaultFont = 14
[win]defaultFont = +2

OptionPane.buttonPadding = 13
OptionPane.border = 13,13,13,13
OptionPane.iconMessageGap = 13
OptionPane.maxCharactersPerLine = 50

TextField.margin = 2,4,2,4
TextArea.margin = 2,5,2,5

# Removed: TitlePane.foreground = #000000
# Let FlatLaf handle title text color automatically for both themes.

ToggleButton.toolbar.selectedBackground = #8bcdff
[dark]ToggleButton.toolbar.selectedBackground = <TBD: muted blue for dark>
ToggleButton.toolbar.disabledSelectedBackground = lighten($ToggleButton.toolbar.selectedBackground,13%)
ToggleButton.toolbar.hoverBackground = #e6e6e6
[dark]ToggleButton.toolbar.hoverBackground = <TBD: dark hover>

ToolBar.background = #f5f5f5
[dark]ToolBar.background = <TBD: dark toolbar>
ToolBar.separatorColor = @borderColor
ToolBar.hoverButtonGroupBackground =
```

The exact dark mode color values for toggle button selection, hover, and
toolbar background will be determined during implementation through visual
testing. The `[dark]` prefix ensures these only apply when a dark LAF is
active.

**TitlePane.foreground**: Removed entirely. FlatLaf handles title bar text
color automatically for both light and dark themes, including when
`apple.awt.transparentTitleBar` is active on macOS.

---

## Score Page and Surround

### Score Page Background

The score page (margin panel) background uses two constants in
`LayoutStylesheet`:

```java
public static final Color SCORE_BACKGROUND_LIGHT = new Color(0xF9, 0xF9, 0xF9);
public static final Color SCORE_BACKGROUND_DARK = new Color(/* TBD — a slightly different white/off-white */);
```

The appropriate constant is selected based on the current theme. The exact dark
mode page color will be determined during implementation — it remains a light
color (the page always looks like paper) but may be a slightly different shade
for optimal contrast against the dark surround.

### Score Surround (ScorePanel, Viewport)

The area surrounding the score page (ScorePanel background and scrollpane
viewport background) adapts to the theme via UIManager keys defined in
`FlatLaf.properties`:

```properties
# Custom keys for score surround areas
SongScribe.scorePanel.background = #c0c0c0
[dark]SongScribe.scorePanel.background = <TBD: dark gray>
```

ScorePanel and the scrollpane viewport read their background from these
UIManager keys. When `updateComponentTreeUI()` is called during a theme switch,
these components pick up the new values automatically — no explicit listener
needed.

**Implementation note**: ScorePanel should not set its background in the
constructor with a hardcoded color. Instead, it should read from UIManager,
and its `updateUI()` method (called automatically by Swing during LAF changes)
handles the refresh.

---

## SVG Icons

All toolbar SVG icons are converted to use `currentColor` instead of hardcoded
black fills. FlatLaf's `FlatSVGIcon` automatically maps `currentColor` to the
current theme's foreground color, so icons adapt to light/dark themes without
any programmatic color filtering.

### Icons to Convert

All SVGs in `src/main/resources/icons/` that use hardcoded fills:

- `beam.svg` — hardcoded black fill → `currentColor`
- `double-chevron-down.svg` — check and convert if needed
- `double-chevron-up.svg` — check and convert if needed
- `glissando.svg` — check and convert if needed
- `grace-eighth.svg` — hardcoded `stroke:black` → `stroke:currentColor`
- `grace-sixteenth.svg` — check and convert if needed
- `mode-lyrics-adjustment.svg` — hardcoded black fill → `currentColor`
- `mode-note-adjustment.svg` — hardcoded black fill → `currentColor`
- `mode-vertical-adjustment.svg` — hardcoded black fill → `currentColor`
- `plus.svg` — check and convert if needed
- `pointer.svg` — already uses `currentColor` (no change)

### Font-Based Icons

Toolbar buttons using font icons (MusescoreIcon, Bravura) require no changes.
FlatLaf handles foreground color for enabled/disabled states automatically, so
font-rendered glyphs adapt to the theme.

---

## Toolbar Borders

Toolbar separator borders are currently created at construction time using
`UIManager.getColor("ToolBar.separatorColor")`, which bakes in the color.
After a runtime theme switch, these borders would retain stale colors.

**Fix**: Override `paintBorder()` to query `UIManager.getColor()` lazily on
each paint call instead of caching the color at construction time. This ensures
borders always reflect the current theme with minimal code change.

---

## Preferences Dialog

### Tab-Based Reorganization

The Preferences dialog is reorganized into tabs to accommodate the new
appearance section and allow for future expansion:

- **General** tab: Contains the existing playback controls (note duration
  slider, play-on-insert checkbox)
- **Appearance** tab: Contains the new appearance preference

### Appearance Tab

Three radio buttons with icons, arranged in a group:

```
( ) System   [auto icon]
( ) Light    [sun icon]
( ) Dark     [moon icon]
```

The user will provide appropriate icons for each option.

### Live Preview

When the user clicks a radio button, the theme switches immediately (with
animated crossfade). This gives an instant preview of the selected theme.

If the user clicks **Cancel**, the theme reverts to whatever it was before the
dialog was opened. If the user clicks **OK**, the new preference is saved to
`prefs.json`.

---

## Splash Screen

The splash screen (`SplashWindow`) always uses a light appearance regardless of
the selected theme. It is branding and should remain consistent. No changes
needed.

---

## Score Rendering

All score rendering (staff lines, notes, beams, ties, text, lyrics, etc.)
remains unchanged. These elements always render in black on the light page
background, consistent with sheet music conventions.

The following colors remain as-is and do not adapt to theme:

| Color | Usage | Value |
|---|---|---|
| `STAFF_LINE_COLOR` | Staff lines | `Color.BLACK` |
| `NOTE_COLOR` | Notes | `Color.BLACK` |
| `EDIT_NOTE_COLOR` | Insertion cursor note | `#0388FF` (blue) |
| `PLAYING_NOTE_COLOR` | Currently playing note | `#1FCC00` (green) |
| `SELECTION_STROKE_COLOR` | Selection highlight | `Color.MAGENTA` |

These highlight colors are vivid enough to work on both the light and dark mode
page backgrounds.

---

## What Does NOT Change

- Score page rendering (notation, staff lines, text — always black on light)
- Highlight colors (edit note blue, playing note green, selection magenta)
- Export output (PDF, SVG, image exports are unaffected by theme)
- Splash screen appearance
- Font registration and font preferences
- Any musical data or document state

---

## Implementation Phases

### Phase 1 — AppearanceManager and LAF Switching

1. Create `AppearanceManager` class with theme resolution, LAF installation,
   and OS listener logic
2. Add `appearance` preference key to `defaults.json` with value `"system"`
3. Refactor `UIUtils.initLaf()` to delegate LAF selection to
   `AppearanceManager`
4. Implement runtime theme switching with `FlatAnimatedLafChange`
5. Verify theme switching works on macOS (FlatMacLightLaf/FlatMacDarkLaf)

### Phase 2 — FlatLaf Properties and UI Adaptation

1. Update `FlatLaf.properties` with `[dark]` conditional values
2. Remove `TitlePane.foreground` hardcoding
3. Add custom UIManager keys for score surround colors
4. Update `ScorePanel` and scrollpane viewport to use UIManager keys
5. Add dark mode page background constant to `LayoutStylesheet`
6. Fix toolbar border painting to query colors lazily

### Phase 3 — SVG Icon Conversion

1. Convert all SVG icons from hardcoded fills to `currentColor`
2. Verify all icons render correctly in both light and dark themes
3. Verify disabled state rendering for both themes

### Phase 4 — Preferences Dialog

1. Reorganize `PreferencesDialog` into tabbed layout (General, Appearance)
2. Implement appearance radio button group with icons
3. Wire radio buttons to `AppearanceManager` for live preview
4. Implement cancel-reverts-theme behavior
5. Save preference on OK

---

## Open Questions / To Determine During Implementation

- Exact dark mode colors for `ToggleButton.toolbar.selectedBackground`,
  `ToggleButton.toolbar.hoverBackground`, `ToolBar.background`
- Exact dark mode score page background color (`SCORE_BACKGROUND_DARK`)
- Exact dark mode score surround color (`SongScribe.scorePanel.background`)
- Whether any other UI elements surface with hardcoded colors during testing
- Appearance tab icons (user will provide)
