# The Change Report

Read `ChangeReport` first.

## Membership

- `ChangeReport`
- `ChangeReport.Builder`
- `RecordedChange`
- `ChangeLines`
- `ReportedChange`
- `LineRange`
- `ModificationRule`
- `ReportPanel`

## Rules

- Tell the user about a change the song makes on its own only through the report of the edit or
  load that made it, never through a confirmation or an alert of its own, because the Messages
  panel is the one place the user learns what the song changed.
- Remove a span that the user did not directly request with `Span.removeReported`.
- Report no removal that loses no information, such as a stranded key change or a span merged
  into a neighbor of the same kind.
