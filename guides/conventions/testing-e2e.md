# E2E Test Guide

Read [testing-common.md](./testing-common.md) first; its
[Choosing the level](./testing-common.md#choosing-the-level) decides whether a behavior belongs
here.

## Rules

- Write one e2e test per path, never one per case, because e2e proves wiring and the cases along a
  connected path are unit-test concerns.
- Extend `E2ETest` and place the test in `src/test/java/songscribe/e2e/`.
- Prefix every e2e target with `e2e` when running (`./scripts/test.sh e2e ...`), because a bare class
  name routes to the unit task, silently skips an e2e-only class, and still prints a passing count.
- Confirm that the action's component name appears in the relevant `Toolbar` subclass before
  clicking a toolbar button, and drive the action through its menu item when it does not.
- Call coordinate lookups, and any assertion that reads a Swing component's model, inside
  `GuiActionRunner.execute()`.
- Use synthetic `MouseEvent`s for shift-click, because the robot does not reliably carry keyboard
  modifiers.
- Force a layout for the affected line after any model mutation and before reading layout data or
  coordinates, because nothing else invalidates the cache and the read answers from the state before
  the mutation.
- Insert notes with real clicks to build preconditions, never by assembling a `Song` directly,
  because a hand-assembled model skips the UI updates and gives false positives and negatives.
- Add no save/load round-trip helper to `E2ETest`; assert save and load fidelity in the unit tests
  in `songscribe.io.musicxml`.
