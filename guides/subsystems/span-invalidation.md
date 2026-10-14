# Span Invalidation

Read `LineSpans` first.

## Membership

- `Span`
- `SpanOutcome`
- `ElementChange`
- `LineSpans`
- `Displacement`
- `Spanning`
- `Beam`
- `Hairpin`
- `Ending`
- `Trill`
- `Tuplet`
- `SpanLoadPass`
- `PendingSpan`
- `ModificationRule`
- `TieRule`
- `EndingSectionRule`

## Rules

- Change a line's elements only through the `Line` primitives, never through its element list,
  because each primitive hands the pending change to `LineSpans.sweep` before it lands.
- Add a `Beam`, `Hairpin` or `Ending` to a line only from the span a `spanning` factory
  returned, because the factory judges the kind's whole invariant and the editor, the paste and
  the load must agree on it.
- Express a reshaped span as the removal of the old span and the addition of a copy, never as a
  new kind of record, because only one addition and one removal exist for every kind.
- Leave an ill-formed span to its own removal with a reason, never repair it with a second
  change to another element, because the user did not ask for that change.
- Judge a tie or an ending's repeat section in a `ModificationRule`, never in
  `Span.outcomeFor`, because neither can be decided from one line's projection.
- Remove a span the user did not ask to remove with `Span.removeReported`, because every such
  removal must reach the report.
