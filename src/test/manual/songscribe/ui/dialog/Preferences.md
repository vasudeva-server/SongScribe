# Preferences

Exercises: `PreferencesDialog`, `PreferencesOpenAction`, `Prefs`, `PrefsKey`, `LengthUnit`

## Units

1. Choosing centimetres or inches survives closing and reopening the dialog.
2. The chosen units survive a restart of the application.

## Opening and lifetime

3. Invoking Preferences from the menu while the window is already open brings the existing window forward rather than opening a second one.
4. Invoking Preferences from the macOS application menu while the window is already open does the same.
5. The dialog appears without a perceptible delay on the second and later openings.
