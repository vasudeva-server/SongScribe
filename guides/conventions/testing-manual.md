# Manual Verification Guide

Read [testing-common.md](./testing-common.md) first; its
[Choosing the level](./testing-common.md#choosing-the-level) decides what lands here rather than in
a test.

## Rules

- Put a checklist at `src/test/manual/<package path>/<Surface>.md`, mirroring the source package,
  never under `src/test/java/`.
- Write one file per user-facing surface (a window, a dialog, an editing mode), never one per class.
- Open every file with an `Exercises:` header naming every class its checks touch, each name in
  backticks and the header in plain markdown, never fenced, because a backticked name is a tracked
  reference that `jet_brains_find_referencing_symbols` and a rename follow.
- Reach the checklist from a class by `jet_brains_find_referencing_symbols`, never from
  `find_implementations` or `type_hierarchy`, because the header is a reference site, not a
  declaration.
- Write a check as one present-tense line naming the gesture and the promised result.
- Write only a check that a person observes at the running application; a behavior assertable with
  collaborators mocked is a unit test.
- Group checks under `##` headings naming the part of the surface they exercise.
- Write no check that a surface merely works ("opens and closes without error"), because it can
  only fail when something is badly broken; put such a check inline in the plan's manual
  verification phase.
- Number checks within the file and never reuse a number; a deleted check leaves a gap.
- Record results in the plan phase that ordered the run (date, build, Pass or Fail per number, and
  for a failure the gesture, what was expected and what was observed), never in the checklist file.
- Write the new checks a plan calls for into the checklist file, and put only the results in the
  plan.
- Read the surface's checklist before writing a UI test, and write no test when a check covers the
  behavior.
- Delete a behavior's check in the same change that promotes the behavior to a test.

## When the code changes

1. Find the affected checklists with `jet_brains_find_referencing_symbols` on the changed members.
2. Rewrite a check whose promise moved, delete a check whose promise is gone (leaving its number
  unused), and delete the file when the surface is gone.
3. Read the checklist of every surface a change touches rather than trusting it, because nothing
  compiles a checklist.
