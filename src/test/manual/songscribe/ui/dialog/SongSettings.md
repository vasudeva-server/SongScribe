# Song Settings

Exercises: `SongSettingsDialog`, `SongSettingsController`, `SongSettingsTitleTab`,
`SongSettingsAttributionTab`, `SongSettingsDateInputRow`, `SongSettingsInput`,
`SongSettingsOutput`, `FontSettingRow`, `SongSettingsOpenAction`, `DialogGeometry`

## Title tab

1. Typing in the title field updates the title preview live.
2. Typing in the number field updates the title preview live.
3. The title preview shows typographic substitution — curly quotes — as typed, matching what OK saves.
4. Leaving the title field replaces its text with the normalised version.
5. Leaving the subtitle field replaces its text with the normalised version.
6. Emptying the subtitle collapses its preview and the window re-packs to fit.
7. Typing a subtitle back expands the preview and the window re-packs again.
8. Choosing a title font updates both the font description label and the preview.
9. Reset on the title font row updates both the font description label and the preview.
10. The Take button is disabled for a song with no lyrics and enabled for one with lyrics.
11. Take fills the title from the lyrics, and the preview follows.
12. Blanking the title field disables OK.
13. Tabbing out of a blanked title field restores the previous title and alerts.
14. A title left padded with spaces has them stripped on leaving the field.

## Attribution tab

15. The month combo is disabled until a valid year is entered.
16. The day combo is disabled until a month is chosen.
17. Clearing the year disables both combos and resets their selections.
18. Changing any date field refreshes the attribution preview.

## Commit and cancel

19. OK commits every field on both tabs, and the score reflects it.
20. Cancel discards every change on both tabs.

## Opening and lifetime

21. Reopening after a close shows the current document's values, not the previous opening's.
22. The dialog reopens at the size and position it was closed at.
23. Double-clicking a title on the score opens the dialog on the Title tab, with the caret in the field for the title that was clicked.
24. The dialog appears without a perceptible delay on the second and later openings.
