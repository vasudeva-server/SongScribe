# Lint

Read `LintRule` first.

## Membership

- `LintRule`
- `LintCheck`
- `Violation`
- `LintFinding`
- `LintMark`
- `LintReport`
- `Linter`
- `LintingNotification`
- `ReportPanel`
- `LintCallout`
- `LintCalloutController`
- `LintToolTipPlacement`

## Rules

- Keep the lint report out of layout, because whether an edit fits must never depend on a
  finding.
- Make every song state a `LintCheck` reads announce its change with a `LintingNotification`,
  because the song lints only when one is built.
- Keep the play-with-repeats setting unchangeable while playback runs, because
  `LintRule.Flag.BLOCKS_PLAYBACK` never stops a running playback.
