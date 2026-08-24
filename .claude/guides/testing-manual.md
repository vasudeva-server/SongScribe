# Manual Verification Guide

Read `./testing-common.md` first for shared conventions, including
[Choosing the level](./testing-common.md#choosing-the-level-unit-vs-e2e-vs-manual-vs-none),
which decides what lands here rather than in a test. This guide is how a
checklist for it is written.

**The checklist is the durable artifact; a run is not.** The file states what must be
true of a surface. What was observed, on what date, against which build, belongs to
the plan that ordered the run.

## Where checklists live

`src/test/manual/<package path>/<Surface>.md`, mirroring the source package the way
tests do:

    src/test/manual/songscribe/ui/dialog/SongSettings.md
    src/test/manual/songscribe/ui/component/LyricEditor.md

Not under `src/test/java/`, which is a Java source root.

**One file per user-facing surface** — a window, a dialog, an editing mode — never one
per class. A single check routinely spans a dialog, its controller and the score, and
has nowhere to live in a per-class split.

## The `Exercises:` header

Every file opens with one, naming every class its checks touch, **each name in
backticks**:

```markdown
# Song Settings

Exercises: `SongSettingsDialog`, `SongSettingsController`, `SongSettingsTitleTab`,
`SongSettingsAttributionTab`, `SongSettingsInput`, `SongSettingsOutput`
```

The backticks are the mechanism, not decoration. A backticked name in markdown is a
tracked reference: `jet_brains_find_referencing_symbols` on the class returns this
file alongside the code that calls it, and renaming the class rewrites the header. A
name in plain prose, or inside a fenced block, is neither — it is text, invisible to
both the lookup and the rename.

So never fence the header and never strip the backticks to tidy the list; either edit
silently disconnects the file from the class it documents. The block above is fenced
because it is an example of a file, and a real header is plain markdown.

The header is a reference site, never a declaration site. Tools that start from a
declaration — `find_implementations`, `type_hierarchy`, `find_referencing_symbols`
itself — cannot be pointed at the checklist; reach the class with
`jet_brains_find_symbol` first and work from the file it names.

## Writing a check

One line, present tense, naming the gesture and the promised result:

> Emptying the subtitle collapses its preview and the window re-packs to fit.

A check states something a person observes at the running application. If it can be
asserted with collaborators mocked, it is a unit test and does not belong here —
*Choosing the level* in [testing-common.md](./testing-common.md#choosing-the-level-unit-vs-e2e-vs-manual-vs-none)
decides which.

Group checks under `##` headings naming the part of the surface they exercise
(`## Title tab`, `## Lifetime`), so a change to one area has an obvious set to reread.

Every check costs a person's attention on every run. That is the reason not to add one
for a promise a check already makes, and the reason a vague check is worse than none:
"the dialog works" cannot be failed.

**A check that a surface merely works is not a check.** "Opens and closes without
error", "the window still appears" and their kind verify a change rather than a
promise — they can only fail if something is badly broken, and they charge attention
on every run for the rest of the surface's life. Put those inline in the manual
verification phase of the plan that wants them, where they go when the plan is done. A
checklist carries what must stay true, not what was worth looking at once.

## Numbering

Checks are numbered within the file, and **a number is never reused**. A deleted check
leaves a gap. This is what lets a plan cite "checks 1–21" and still mean the same
twenty-one a year later.

## Running a checklist

**Ask the user for permission before running the application.** `./scripts/run.sh` is
never executed without it — see [development.md](../rules/development.md).

Record results in the plan phase that ordered the run: the date, the build, Pass or
Fail per number, and for any failure the gesture, what was expected and what was
observed. Never in the checklist file.

A plan names the checklist and the checks it wants run, and adds whatever new checks
the change calls for. The new checks are written into the checklist file; only the
results go in the plan.

## One behavior, one verification

A behavior is verified in exactly one place.

**Before writing a UI test, read the checklist for that surface.** If a check covers
the behavior, no test is written.

**When a behavior is promoted to a test, delete its check in the same change.** A
behavior verified twice costs a person's time on every run and gets its two
descriptions out of step.

## When the code changes

`jet_brains_find_referencing_symbols` on the changed members returns the affected
checklists along with the affected tests, because of the header. Then:

- **The promise moved** — rewrite the check to the new behavior.
- **The promise is gone** — delete the check, leaving its number unused.
- **The surface is gone** — delete the file.

A check that still reads plausibly but describes behavior the code no longer has is
the failure this guide cannot catch for you. Nothing compiles a checklist, so a change
that touches a surface reads its checklist rather than trusting it.
