# Test-Only Production Surface

No production member exists to serve a test. A member whose only callers are
tests is deleted, or the code is restructured so production supplies what the
test needed. Widening visibility, adding a `*ForTest` accessor, or branching
production code on a test flag are all the same violation.

`ui/dialog/AttachmentDialogController.java:55` cites this file as the rule it
obeys.

## What checks the work

`src/test/` holds six test classes — `BindingsTest`, `OtherValueComboBoxTest`,
`StringUtilsTest`, `PackageDependencyTest`, `MainFrameMockTest`, `E2ETest` —
plus base classes and helpers. Test packages exist only for `songscribe`,
`dom`, `e2e`, `error`, `io`, `io/musicxml`, `message`, `ui/action`,
`ui/binding`, `ui/dialog` and `util`. **A flagged member in any other package
cannot have a test caller**, so if it has no production caller it is simply
dead, and deletion is the whole fix.

- `jet_brains_find_referencing_symbols` on the member is the check. A member
  with only test callers is the finding; a member with none is dead either way.
- `./scripts/compile.sh --test` catches a removal something still depends on.
- Deletions cascade. `PreviewElementManager.resetOverlaysForTest` was one member
  in the first sweep and turned out to be eight, which then orphaned two more on
  `PreviewOverlayRegistry`. After deleting a member, run
  `find_referencing_symbols` on everything it called.

Judge a restructured member on whether it is a coherent unit with its own
contract, never on whether it is convenient to call from a test.

## The remaining work: members widened silently

The name sweep and the comment sweep are both exhausted. What neither finds is a
member widened with no naming convention and no comment admitting why —

```bash
PROSE=1 rg -n "ForTest|ForTesting|VisibleForTesting" src/main/java/
PROSE=1 rg -ni "for test|for tests|in unit tests|so a test|that a test|test setup|test teardown|test isolation|test injection|test inspection|for testability|used by tests|exposed for" src/main/java/
```

— and that is the whole remaining category. `ActionReflector.setManagedActions`
was one: `public`, undocumented, zero callers, found only by asking of a member
that looked ordinary **which production code calls this?** Nothing but that
question over every package-private and non-private member will find the rest.

Work package by package with `get_symbols_overview`, then
`find_referencing_symbols` on each member that is not private. Packages with no
test package at all are the cheapest ground, because there a zero-caller member
needs no judgement — it is dead.

## What the comment sweep taught

Most of what the comment search returns is a **stale comment on a legitimate
member**: real production callers, wrong comment. Verified live and corrected in
place rather than deleted — `FootnotesComponent.calculateRenderX`,
`LineComponent.readyLayout`/`layoutDirty`/`layoutResult`,
`LineRenderer.drawStaffLines`/`getElementColor`/`computeOverrideXSs`/`renderKeyChanges`/`renderWithPreviewShiftIfNeeded`,
`TextPanel.calculateUnionWidth`, `StaffPanel.ensureAllLineLayouts`/`layOutLines`,
`LineSelectionHandler.HEADER_GAP_PX`, `ScoreView.scoreKeyBindings`,
`MeasureBuilder.buildMeasure`,
`AttributionPane.LINE_BOX_REFERENCE`/`MeasuredCache`/`measure`,
`PlayStopAction.PLAY_ICON`, `ActionReflector.triggerReflection`, all four
flagged `PlaybackController` members, and the four `SongScribe` and four
`SMuFLMetadata` overloads that take their inputs explicitly and are called by
their own no-argument wrappers.

Never delete on the strength of the comment. Run the reference check first.

Comments deliberately left as they are: `ModificationSession`, `Song`,
`StaffElement` and `Line` describe a **mutation contract** in which bypassing a
bracket is permitted for setup that mirrors `withoutMutationTracking` — that is
contract language, matching `docs/mutations.md` and `docs/undo.md`, not a
justification for a member's existence.

## What the register owns

`plans/design-pass-register.md` carries the findings whose fix belongs to a
numbered pass, not here: the `StartupErrorQueue` and overlay-ownership
extractions (pass 25), the `PreferencesDialog` instrument cache (passes 26/23),
`RecentDocumentsManager`'s `readRecents` shaping (pass 30), and the fatal-error
path across the seven entry points (pass 30, needing pass 26) — of which
`OptionDialogs.setSuppressDialogs` is one third. Do not duplicate them here.

## The pattern to follow

Three worked examples, for the three shapes a real fix takes.

**Extract a type constructed with the state it works on.** `prefs/PrefsUpgrade`
came from removing seven such members from `Prefs`: `getRawStored` ×2,
`putRawStored`, `removeObsoleteKeysForTest`,
`removeSystemDefaultKeysFromStoreForTest`, `writeTypedForTest`, `migrateForTest`.
All seven existed because the startup transformations were private methods on a
singleton mutating its private store, and nothing could obtain a `Prefs` whose
store it chose. The four transformations moved to a package-private class taking
the store, the defaults and the system-default keys, with one `apply(oldPropsFile)`
running them in order and reporting whether the store changed. Keep the steps
private: `apply` is the promise, and exposing the four so a test can drive each
one is the same mistake one level out.

**Make the thing the probe observed into a parameter.** `MessageCenter` had two
observation hooks — `setPublicationErrorProbeForTesting`,
`setSubscriptionProbeForTesting` — plus null checks in the hot `subscribe` and
error paths, and a test helper that tracked every listener so teardown could
unsubscribe them. All of it existed because the bus was a `static final`
singleton with no lifecycle. It is now a stack, with `MessageBusScope`
(`AutoCloseable`) pushing a bus whose publication-error handler is a constructor
parameter. Closing the scope discards the bus and everything subscribed to it in
one operation, so there is nothing to track. The production caller is
`Converter.run`, which gives each headless conversion its own bus and an error
handler that logs rather than showing a dialog no headless process can display.
See `docs/messages.md`.

**Delete the abstraction when the injection point goes.** `ui/LafOperations` was
an interface with one implementation, `AppearanceManager.DefaultLafOperations`,
and a Javadoc that said what it was for: "Abstraction over static FlatLaf
operations to enable test mocking." Once `setLafOperations` was deleted nothing
could supply another, so the interface could no longer vary. Each of its four
methods was a single static call, so both types went and the calls inlined.
