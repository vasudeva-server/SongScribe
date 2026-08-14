# Pilot Retrospective — `undo`

Phase 13 of [`contract-driven-rollout.md`](./contract-driven-rollout.md), from the
record in [`pilot-undo-results.md`](./pilot-undo-results.md) and the branch's commit
history. Its job is to say what the pilot actually measured, what it did not measure,
and what that changes.

---

## 1. The numbers

Measured directly from the tree at each commit boundary, not from the running
narrative. Phase 11 entry is `7cbee463`, Phase 11 exit `45783029`, the triage commit
`2262492f`, and HEAD `66517f8a`.

| | Phase 11 entry | Phase 11 exit | Phase 12 triage | HEAD |
|---|---:|---:|---:|---:|
| Test cases (run) | 141 | 141 | 161 | **161** |
| Test methods (`@Test` + `@ParameterizedTest`) | 141 | 141 | 161 | **68** |
| Main LOC | 1,041 | 1,228 | 1,266 | **1,266** |
| Test LOC | 3,358 | 3,559 | 3,789 | **3,574** |
| Test/main ratio | 3.23 | 2.90 | 2.99 | **2.82** |

161 passing cases confirmed by running the package's ten test classes at HEAD.

**`pilot-undo-results.md`'s table is wrong in three cells** and is superseded by the
one above: it reports Phase 11 exit as 1,266 main / 3,574 test LOC, which are HEAD's
numbers, and a final test LOC of 3,565 against an actual 3,574. The direction of every
conclusion in that document survives the correction; the magnitudes shift slightly.

Derived:

- **Main LOC grew 21.6%** (+225), entirely contract Javadoc. No production logic
  changed except the three findings Phase 11 acted on.
- **Test LOC grew 6.4%** net (+216) — +431 for testing-approach Javadoc and 22 new
  cases, −215 given back by parameterization.
- **Test cases grew 14.2%** (+20 net: 22 added, 2 discarded).
- **Test methods fell 52%** (141 → 68) with no case lost. That is the parameterization
  pass, and it is the only reason the ratio moved.

**Elapsed:** 3h13m of wall clock across both phases (14:47 → 18:00 on 2026-08-11),
split 106 min for contracts and 87 min for triage and the parameterization follow-ups.
That is **324 main LOC/hour** for a package with a pre-existing `docs/undo.md` and no
domain judgment requiring confirmation.

Repo-wide, the pilot moved nothing: 121,684 main / 186,387 test LOC, ratio 1.53 against
the 1.54 the project opened with. `undo` is 0.9% of the application.

---

## 2. What a contract looks like in this codebase

The shape stabilized quickly and is worth stating, because it is what the remaining
packages should be measured against. `UndoController.songDidChange` is the exemplar:

```java
/**
 * Records a completed forward edit as one new undo step, so that a subsequent
 * {@link #undo()} reverses exactly the edit this notification describes.
 *
 * <p>Afterwards {@link #canUndo()} is true, {@link #canRedo()} is false — a forward
 * edit discards the redo branch, so redo is linear rather than a tree — and the
 * document is modified unless the pushed step happens to be the clean marker again.
 * The step's label follows from the notification's op-name; see {@link #undoLabel()}.
 *
 * <p>The stack retains at most {@value #UNDO_STACK_MAX_DEPTH} steps; a push past
 * that evicts the oldest, and if the evicted step was the clean marker the document
 * can no longer return to clean. Posts {@link UndoStateDidChangeNotification} so the
 * Edit menu follows.
 *
 * <p>A notification posted by the engine's own replay bracket is ignored: replaying a
 * step must not push a step, or undo could never empty the stack.
 *
 * <p>Runs at {@link Message#HIGH_PRIORITY} so that lower-priority subscribers reading
 * {@link #canUndo()} or {@link #undoLabel()} while handling the same notification see
 * the step already pushed.
 */
```

Five properties recur across all 26 contracts written in the pilot:

1. **The summary sentence is the promise in the caller's terms**, not a description of
   the body — "so that a subsequent `undo()` reverses exactly the edit this
   notification describes," not "pushes onto the undo stack."
2. **Postconditions are stated as observable state** through other contracts on the
   same class (`canUndo()`, `canRedo()`, the modified flag), which is what makes them
   testable without reaching past the public API.
3. **Every boundary carries its consequence**, not just its value: the depth limit is
   given with what eviction costs (the clean marker can be lost), which is the clause a
   test can be derived from.
4. **Rationale is included where the promise would otherwise read as arbitrary** — why
   replay-suppression exists, why the handler priority is what it is. This is the part
   a reader cannot reconstruct and the part most likely to be broken by a later change.
5. **Cross-subsystem rules are linked, never restated.** The package invariants live in
   `package-info.java`, the engine-wide guarantees in `docs/undo.md`, and methods point
   at them.

The tier split held up without strain. Three tiers were genuinely needed and the
boundaries were not ambiguous in practice: what one method promises, what the package
guarantees across its three classes, and what the rest of the application may rely on.

Cost: **roughly 8–9 lines of contract per method** on nontrivial APIs, which is where
the 21.6% main-LOC growth comes from. Extrapolated across the ~118,000 main LOC still
listed in the rollout table, that is on the order of **+25,000 lines of Javadoc**.
Nothing in Phases 1–4 estimated this, and it should be stated up front rather than
discovered per package.

---

## 3. The survival rate, and why it does not generalize

Of the 141 tests inherited: **139 kept (98.6%), 2 discarded (1.4%), 1 renamed, 22
added.** Net case count up 14%.

The plan asked for a survival rate on the premise that contract-driven testing would
shrink the suite. In `undo` it did not, and the honest reading is that **the pilot
package was selected against the thesis it was meant to test.**

The bloat measurement that motivated the whole project came from `ui/dialog` — 7,200
test LOC across 257 tests, with `SongSettingsDialog` at 491 production lines against
1,346 test lines. `undo` was chosen (D16) for being small, real logic rather than
wiring, and already carrying a `docs/undo.md` to seed tier 3. Those are the properties
of a package whose tests were *already* contract-shaped. Finding that 98.6% of them
map to a real contract clause is a fact about `undo`, not a prediction about the suite.

What the pilot therefore measured accurately:

- **the cost of writing contracts** — 324 main LOC/hour, +21.6% main LOC;
- **the shape a contract takes here** — §2;
- **the gaps contract-writing exposes** — 22 new cases, §5.3.

What it did not measure at all:

- **deletion yield**, which is the number D1 and the 1.54× ratio were about. The one
  package where that number exists is `ui/dialog`, and it is also the package the plan
  already treats as an architectural track rather than a contract pass.

The 6.4% test-LOC *growth* also deserves naming plainly: contract-driven testing, in a
package whose tests were already sound, **adds** test LOC. The 13% ratio improvement
came from parameterization — a mechanical transformation that required no contracts,
no triage, and could be run over the entire suite independently of this project.

---

## 4. The rate, and what it means for D10

D10 froze all other work until the rollout completes, "revisited after the pilot
retrospective, when a rate exists." A rate now exists.

| | |
|---|---:|
| Pilot rate | 324 main LOC/hour |
| Main LOC remaining in the rollout table | ~117,958 |
| **Implied wall clock** | **~364 hours** |

At eight hours a day that is **45 working days — nine weeks** of continuous session
time, against D1's "I don't care if it takes a week."

The estimate's error bars are large and they are not symmetric:

- **Slower than 324 LOC/h:** `dom` (15,483) and `io`/`midi` (15,557) are the rows where
  §4.2 of the discussion doc says every contract is a musical judgment to be proposed
  and confirmed. `undo`'s contracts were mechanical enough to write straight through;
  those are round trips with a human in the loop, and the plan already marks `dom` as a
  pause point barred from parallel agents.
- **Faster than 324 LOC/h:** `layout`/`engraving` (19,856) is geometry — invariant-heavy
  and mechanical. `ui/platform` (590) is contract-only with no tests. The foundations
  row parallelizes one agent per package. The pilot also paid a one-time novelty cost
  that will not recur.

Even granting a generous 3× from parallelism and practice on the parallelizable rows,
the floor is on the order of **120 hours**. The freeze cannot be honored at that length
without the project becoming the only thing that happens for months.

Three ways out, in the order I would recommend them:

1. **Lift the freeze; keep the regime.** The rules, guides and `check` skill are done
   and in force as of Phases 1–7 — every new or changed API already gets a contract,
   and every new test is already derived from one. Normal work resumes under the new
   regime, and packages are audited in priority order rather than exhaustively. This
   captures nearly all of the benefit — the guides are what stop the debt growing —
   and pays for the back catalogue only where it hurts.
2. **Narrow the audit to where the measured problem is.** `ui/dialog` (10,010 main /
   257 tests) is the only place the 1.54× ratio was ever traced to, and it is an
   architectural fix (D2, D4) that stands on its own merits. Do that row, measure the
   deletion yield the pilot could not, and re-decide with two data points instead of
   one.
3. **Keep the freeze and the full audit.** Defensible only if nine weeks of exclusive
   focus is genuinely acceptable. Nothing in the pilot argues the work is wasted — the
   contracts are good and the 22 found gaps were real — only that it is long.

Recommendation: **1 and 2 together.** Lift the freeze, run `ui/dialog` next as the
second measurement, and decide the remaining rows against a deletion yield that
actually exists.

---

## 5. What Phases 1–4 got wrong

Five findings. Two were caught and fixed mid-pilot; three are open and are what Phase
13 acts on.

### 5.1 `@return` was optional — fixed mid-pilot (`d3be8d3f`)

Phase 2's `contracts.md` and Phase 4's `java.md` both let a return promise live in the
summary sentence. Writing 26 real contracts made the defect obvious: the tag is what
the IDE surfaces at a call site, so a promise stated only in prose is a promise the
caller never reads. `contracts.md`'s own `@Nullable`-return example had the bug.

**The general lesson:** the guides were written before a single contract had been
written against them. Their first real use is the review, and it found something in the
first hour.

### 5.2 Parameterization was stated descriptively — fixed mid-pilot (`66517f8a`)

Phase 4 added the `@ParameterizedTest` section as the "normal shape." It did not fire.
Twenty-two new cases were hand-written as near-identical `@Test` methods in the same
session, in a file whose section had already been read, because the sibling test three
lines above the cursor is a stronger prompt than a guide section read once at the start
of a phase.

The fix was not stronger wording but **relocating the rule to the moment of the
decision**: a check that runs before writing any `@Test`, phrased around the
copy-paste reflex it has to interrupt. A secondary discovery came with it — a `record`
field can hold a lambda, so "same algorithm, different data" covers cases whose
*fixture* varies, not only cases whose *literal* varies. The guide's two original
examples both varied a single literal, and taught the narrower rule by omission.

**The general lesson:** a rule that fires at authoring time has to be phrased as a
trigger on the authoring action, not as a description of the preferred outcome.

### 5.3 A claim of enumeration is not self-enforcing — open

This is the finding that produced 22 of the 161 final cases, and **the defect is still
live in the tree.**

`OpNames`'s class Javadoc stated that three small closed domains belonged there
"enumerated in full" — the hairpin kinds, the articulation types, the attachment kinds.
None of the three was actually enumerated. Coverage found it; no amount of reading did,
because the claim read as its own evidence.

The gap was closed by *adding the missing rows*, which fixes the instance and not the
mechanism. `OpNamesTest`'s case tables are still hand-listed literals:

```java
static Stream<ArticulationLabelCase> cases() {
    return Stream.of(
        new ArticulationLabelCase("staccato", ArticulationType.STACCATO, …),
        new ArticulationLabelCase("accent",   ArticulationType.ACCENT,   …)
    );
}
```

`ArticulationType` has exactly those two constants today. Add a third and the Javadoc's
"enumerated in full" becomes false silently, the table still passes, and the next
coverage run is the only thing that will notice — which is the same failure, restarted.

**The rule that was missing:** a documented claim of full enumeration must be backed by
something that fails when the domain grows — driven from `EnumSource` /
`Type.values()` / a sealed hierarchy's permitted subclasses, or, where each row needs a
hand-built fixture, an explicit exhaustiveness assertion over the table.

Neither the guides nor `check` caught it. `check`'s Axis 4 item 6 catches a domain
*visibly* sampled; it does not catch a hand-listed table that happens to be complete on
the day it is read. `testing-common.md` uses "enumerated, not sampled" as its exemplar
Javadoc phrasing without requiring anything to back the phrase.

**A boundary the pilot also exposed:** self-enforcement is possible exactly when the
enumerated domain is public contract surface. `OpNames.Category` is a private enum, so
`addLabel`'s "one case per category" claim cannot be mechanically backed without
widening a private taxonomy — which the no-test-only-surface rule forbids. Where the
domain is private, the honest options are to stop claiming enumeration or to make the
taxonomy part of the contract. The rule has to say so, or it will be read as licensing
the widening.

### 5.4 The Phase 11 / Phase 12 split was not a real split — open

`pilot-undo-results.md` records that triage was "mostly confirmatory rather than
corrective" because the deciding had already happened while writing the
testing-approach Javadoc. That is not a scheduling accident: **writing an accurate "what
this class is responsible for" comment requires checking every test in it against the
contract, which is the triage.**

D6's three steps are two activities. Steps 3 and 4 of the per-area procedure should be
one step, and a package should be one phase rather than a contract phase followed by a
triage phase. Splitting them cost a context clear and a hand-off document between two
halves of a single pass.

### 5.5 Coverage's status was set too low — open

D3 and step 8 of the per-area procedure file coverage alongside mutation as an optional
diagnostic invoked deliberately. In the pilot one scoped coverage run produced **22 of
the 161 final cases — 13.7%** — and it was the only thing that produced them. Mutation
was never run at all.

The two are not peers and should not be described as a pair:

- **Coverage** is the closing step of a package's contract pass. It answers a question
  reading cannot: *is the contract's stated enumeration true?* Its findings in the pilot
  were not "this line is red" but "this class's Javadoc claims a complete domain and is
  wrong by six cases."
- **Mutation** remains opportunistic, and the plan's own reasoning says why — under
  contract testing a high surviving-mutant count is the healthy state.

The plan's fear was that a ranked list of uncovered regions becomes a to-do list.
That fear was correct in the abstract and did not materialize here: every uncovered
region was triaged, and the majority — an unreachable branch given the current
`ElementType` enum, a case owned by another package's tests, the compiler's synthetic
exhaustiveness branch on sealed switches — was correctly left alone. The discipline
that made it safe is asking *of each region* which of the two answers applies, and that
discipline is already written into `testing-common.md`. Requiring the run does not
reintroduce the grade.

---

## 6. Revisions made in Phase 13

Applied from §5.3, §5.4 and §5.5. §5.1 and §5.2 were already fixed mid-pilot.

| File | Change |
|---|---|
| `~/.claude/rules/development.md` | Enumeration claims must be self-enforcing; the private-domain boundary. Coverage separated from mutation as the closing step of a contract pass rather than a peer diagnostic. |
| `.claude/guides/testing-common.md` | The same two rules in test-guide terms, on the "Enumerate a finite domain" bullet and in the testing-approach Javadoc section, which is where the false claim gets written. |
| `.claude/guides/testing-unit.md` | The exhaustiveness-assertion shape, for the case where each row needs a hand-built fixture and cannot be driven from `values()`. |
| `.claude/skills/check/reference/axes.md` | Axis 4 gains the check that Axis 4 item 6 misses: a documented enumeration backed by a hand-listed table with nothing asserting the table is complete. |
| `contract-driven-rollout.md` | Per-area procedure: steps 3–4 merged; coverage promoted to a required closing step and separated from mutation. D3 refined, D10 answered, `ui/dialog` recorded as next. |
| `OpNamesTest.java` | The live instance of §5.3 closed — see below. |

**`OpNamesTest`.** Five companion tests now pin each table's rows to its domain:
`SlideZone.values()` and `ArticulationType.values()` for the two plain enums,
`getPermittedSubclasses()` for `StaffElement.Slide` and `Hairpin`, and a recursive
leaf walk for `Attachment`, whose direct permitted set is four types but whose leaf set
— the domain a caller sees — is five, because `MetronomeAttachment` permits two more.
The leaf walk is local to the class at one call site; it moves to `UnitTest` when a
second package needs it, which `dom` will.

The two `OpNames.Category` tables could not be backed and were not made to look as if
they were: `Category` is private, asserting against it would mean widening it, and
widening it is the surface violation rather than the fix. Their Javadoc now says "one
case per category" and states plainly that a new category is caught by review rather
than by the class.

Verified by deletion, not by passing: dropping the `ACCENT` row makes
`testCasesEnumerateEveryArticulationType` fail and name the missing constant. Full unit
suite green at 7,532 passed / 1 skipped, up 5 from 7,527.

---

## 7. Decisions taken

- **D10 — the freeze holds through `ui/dialog` and no further.** Not lifted now, and
  not extended to the full table. After `ui/dialog`, D10 is decided again.

  **Corrected after this section was first written:** `ui/dialog` alone is not the
  second data point D10 needs. Its deletions come from the **restructure** (D2/D4 —
  the record-in/record-out seam), not from a contract pass, because the pass cannot
  run there first: `SongSettingsDialog.isValidData()` returns a boolean *and* pops a
  modal, so there is no back-end API to contract until the seam extracts one, and
  under D2 the dialog's own three steps are wiring, classified `none`. So
  `ui/dialog` measures what architectural correction buys — worth knowing, since it
  is where the 1.54× ratio came from — and says nothing about what a contract pass
  costs or yields on `dom`, `layout` or `ui/component`.

  A second **design-pass** measurement therefore runs alongside it, on
  `engraving`: 449 main LOC, 645 test LOC, 46 cases, ratio 1.44 against the repo's
  1.53, no `docs/` coverage and no `package-info` content, and untouched by Phases
  8–10. Every one of those removes a bias `undo` carried — chiefly that
  `docs/undo.md` already existed. It is also a scout for the 19,856-LOC
  `layout`/`engraving` row. Record: `plans/design-pass/engraving.md`; compare it
  against §1 above.
- **`ui/dialog` is the next row**, ahead of foundations, on that reasoning.
- **The remaining rows stay unplanned** until that re-decision, since rows that may not
  run should not have task lists written against them.
