# Preferences

Exercises: `PreferencesDialog`, `PreferencesOpenAction`, `Prefs`, `PrefsKey`, `PrefsValue`, `LengthUnit`, `StartupAction`, `Appearance`, `AppearanceManager`, `PageModel`, `PlaybackVolume`, `TickSlider`, `Controls`, `Bindings`, `MidiController`

## Units

1. Choosing centimetres or inches survives closing and reopening the dialog.
2. The chosen units survive a restart of the application.

## Opening and lifetime

3. Invoking Preferences from the menu while the window is already open brings the existing window forward rather than opening a second one.
4. Invoking Preferences from the macOS application menu while the window is already open does the same.
5. The dialog appears without a perceptible delay on the second and later openings.

## General tab

6. Changing the page size re-lays out the open score without the dialog being closed or reopened.
7. Changing the appearance retints the application immediately, and the chosen radio is the one that stays selected.
7a. Every radio on the tab selects on the first click — page size, units, appearance and startup action alike. A click never leaves the pair or trio with nothing selected, and never has to be repeated.
8. The chosen startup action is what the application does on its next launch with no file argument.
8a. A preference changed from outside the window while it is open moves the control that shows it, with no reopening: switching the appearance from the application menu moves the appearance radio.

## Play tab

9. Every checkbox and slider on the tab shows its stored value when the dialog is reopened.
10. Dragging a slider changes the setting as it lands on each tick stop, and not at positions between stops.
11. The volume slider's stops play at the volumes they are labelled with — a note at Softer is audibly quieter than the same note at Full.

## Instruments tab

12. Selecting an instrument previews a note on that instrument.
13. Clicking an instrument that is already selected previews it again.
14. Selecting a different instrument while the scale is playing restarts the scale on the newly chosen one.
15. Opening the Instruments tab shows the stored instrument selected and scrolled into view.
