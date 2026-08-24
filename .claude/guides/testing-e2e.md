# E2E Test Guide

Read `./testing-common.md` first for shared conventions, including
[Choosing the level](./testing-common.md#choosing-the-level-unit-vs-e2e-vs-manual-vs-none),
which decides whether a behavior belongs here at all.

## What an e2e test is for

E2E proves **wiring** — that a real click or keystroke travels the full path
from event dispatch through action, model mutation, layout invalidation,
repaint, and selection reflection without a break. It is not where a contract's
cases are exercised; those are unit-tested. Write **one E2E test per path**, never
one per case: once a path is proven connected, every case along it is a unit-test
concern.

## Running E2E Tests

E2E tests run under a dedicated Gradle task, selected by the `e2e` target
keyword. The unit test task excludes them, so e2e classes are invisible to it.

**A bare class-name target does not run e2e tests.** Without the leading `e2e`
keyword, `test.sh` routes targets to the unit task. An e2e-only class then
matches nothing and is silently skipped; a name it shares with a unit class
(e.g. an `e2e.ShutdownTest` alongside a `lifecycle.ShutdownTest`) runs only the
unit one. Either way the runner still prints a passing count, which is easy to
mistake for a successful e2e run. Always prefix e2e targets with `e2e`.

Running e2e tests requires the user's approval, and they are named, never run as
a suite — see `.claude/rules/development.md`.

## Structure

All E2E tests extend `E2ETest` and live in `src/test/java/songscribe/e2e/`.
`E2ETest` is already annotated `@TestInstance(PER_CLASS)` — do not re-declare it
on subclasses. The base class handles per-class `MainFrame` boot, per-test song
reset, edit mode entry, and rest mode deselection.

`E2ETest` supplies the gestures (toolbar and menu clicks, duration selection,
clicks and drags at score coordinates), the coordinate lookups, and the model
queries. Read the class for what exists rather than a list here; what follows is
what the class cannot tell you.

## Not every action has a toolbar button

Before writing a test that clicks a toolbar button, confirm the action's
component name actually appears in the relevant `Toolbar` subclass. When it does
not, drive the action through its menu item instead.

The menu route finds the item bound to the action anywhere in the menu bar and
clicks it directly. This fires the full button-model state change — identical to
a real user click — without opening any parent menus, which is what avoids
AssertJ Swing's menu traversal waiting up to 10 s for the event queue to idle
after each menu level.

## EDT safety

Coordinate lookups read live layout state and must be called inside
`GuiActionRunner.execute()`, as must any assertion that reads a Swing component's
model.

Shift-click uses synthetic `MouseEvent`s rather than the robot, because the robot
does not reliably carry keyboard modifiers.

## Layout synchronization

**Always force a layout for the affected line after any model mutation, before
reading layout data or coordinates.** Nothing else invalidates the cache, so a
coordinate read without it answers from the state before the mutation — and the
assertion that follows passes or fails on a stale position.

The click and toolbar helpers pause on their own, so an explicit pause is needed
only for an async operation no helper covers.

## Building preconditions

Even when setting up state that is not the feature under test, insert notes with
real clicks rather than assembling a `Song` directly. The full event pipeline is
what keeps layout, selection state and rendering caches consistent; a
hand-assembled model skips the UI updates and produces both false positives and
false negatives.

## No round-trip helper

`E2ETest` has no save/load round-trip helper, and none should be added. Save and
load fidelity is a question about the MusicXML mapping, and it is asserted by the
unit tests in `songscribe.io.musicxml`.
