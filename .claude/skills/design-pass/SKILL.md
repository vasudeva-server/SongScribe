---
name: design-pass
description: Run the design pass over one system, named by its row number in the design pass register — fix what types can carry, contract what fan-in earns, keep only the tests the design cannot enforce
disable-model-invocation: true
---

## Design Pass

Take one target from *whatever it is now* to *illegal states unrepresentable,
contracts where fan-in earns them, tests only where the design cannot enforce the
promise*. One stash snapshot per step, resumable across context clears.

**A pass never commits.** It leaves its work in the tree and snapshots each step
to the stash. Committing is the user's, whenever they choose.

**A step that decides something is reviewed before it is executed.** It writes
what it found to a findings document, opens it for review, and stops — see *The
review gate*.

Paths in this file are relative to `.claude/skills/design-pass/`.

### The yardstick

**The design is the completeness criterion, not the contract and not coverage.**
A target is finished when the invariants it depends on are carried by types
rather than checked at runtime, the contracts that exist are the ones fan-in
earns, and every remaining test sits on the floor in `design.md`.

Steps run in the order of attack: what a type can carry never becomes a contract
clause, and what a contract can state never becomes a test. **Work that arrives
at the test step in volume means the earlier steps were skipped.**

### The review gate

**A step that decides something writes its findings, opens them for review, and
stops.** Nothing in the tree changes until that review is complete and every
question — the user's and yours — is resolved. A step that edits first and
reports afterwards has spent the decision the review exists to make.

**Unless the user says otherwise.** They can lift the gate — for a step, a class,
a single change, or the rest of the pass — and that is theirs to give, never
yours to assume or to infer from impatience. A lifting covers what they named and
nothing adjacent, and it expires with the thing they named: permission to rename
one method now is not permission to rename its siblings, and permission to skip
step 4's gate says nothing about step 5's. Where you think a gate is not worth its
cost, say so and let them lift it; do not decide it for them.

The documents live in one directory per pass, `plans/design-pass-<pass>/`, where
`<pass>` is the register row the pass took — the number with its suffix, so a
split row `14b` gets `plans/design-pass-14b/`. Inside it, one file per step: a
pass on row 3 writes `plans/design-pass-3/findings-1-2.md`, then
`plans/design-pass-3/findings-5.md`, and so on. The record lives there too, as
`record.md`; step 0 creates the directory.

**Steps 1 and 2 share one document**, because the inventory is what the
class-design argument is made from and neither is reviewable without the other.
Steps 3, 4, 5, 6, 7, 9 and 13 each have their own.

**Steps 8, 10, 11 and 12 have no findings document.** Steps 8, 10 and 11 run
something and report what it says; there is nothing in them for a reviewer to
decide. Step 12 is itself a review, and its marks come back on the code rather
than on a document.

**A findings document covers its own step's axis and nothing else.** Step 3 asks
what types can carry; step 5 asks what fan-in earns a contract; step 6 asks what
each test is for. A finding that belongs to a later step's axis is recorded in
the record's *Findings raised* and picked up by that step's document — never
argued early, because the earlier step's answer changes what the later question
even is. This is also why the gate is per step and not one document up front: the
tree step 5 reads is the tree step 3 left.

Each document states, for its step, two things and no more:

1. **What it found** — the facts the step's own reading produced, with file and
   line.
2. **What needs deciding** — one list holding everything that wants the user's
   confirmation or the user's decision, each item separately decidable, with the
   correct structure stated concretely. Never one coherent proposal the user can
   only agree to.

**A proposal and the question it turns on are one item.** Where an item cannot be
settled from the code — a domain promise you would be guessing at, a fork whose
answer changes which structure is right — the question is stated inside that
item, with what each answer makes the correct structure. It never becomes a
second list the user has to carry back to the proposal it decides. What you
could not decide, and what you decided but are not certain of, both belong here:
a question you did not ask is a decision you made silently.

Then:

- **Read `~/.claude/skills/roughdraft/SKILL.md`** before the first gate of a
  pass. It holds the roughdraft flavored markup (rfm) the review comes back in and the form your own
  replies must take, and a reply in the wrong form is one the user's tool will
  not thread.
- **Open it.** `roughdraft open "<absolute path to the document>"`. The command
  **blocks until the user closes the review session**, so the waiting is the tool
  call itself, not a turn you end and hope to be woken from. Nothing changes
  while it is open: no production file, no test file, no `docs/` file, no
  register row. **The document you open is plain Markdown**: never write a
  `<span id="rd-…">` or any other review anchor into it yourself — those are
  written by the reviewer's tool when a comment is placed, and an earlier
  findings document that carries them carries the marks of its review, not a
  format to copy.
- **Resolve.** When it returns, read the file back from disk. The review is
  rfm — comments, insertions, deletions, substitutions — and **every mark that
  asks or suggests something gets an answer written into the document**, as a
  reply whose `re:` names the id it answers. A suggestion you are applying is
  answered too; silently taking one leaves the user unable to tell agreement
  from oversight. **A mark that only approves — "ok", "agree", "yes" — is
  answered by executing it, not by a reply.**
- **Open it again only if you wrote a reply**, so they see the answers. **The
  gate opens on a close that leaves nothing asked unanswered**, and a close that
  asked nothing opens it at once. A mark you interpreted rather than asked about
  is unanswered. Rounds are cheap; a misread comment is what this whole gate
  exists to prevent.
- **Then execute the step**, and snapshot.

The findings documents are working memory on the same terms as the record, and
step 13 deletes them with it.

### Doctrine — read before step 2, do not paraphrase

- **`~/.claude/guides/design.md`** — the architecture gate and the order of
  attack, class design, types over tests, boundaries, guards, `@Nullable`,
  scoped globals, extraction, contract depth, the testing floor and the gate
  that proposes tests before they are written. Everything here serves it.
- **`.claude/guides/contracts.md`** — the Javadoc form of a contract.
- **`.claude/guides/testing-common.md`** — where cases come from, triage,
  diagnostics.
- **`.claude/guides/testing-unit.md`** — parameterized case tables and the
  exhaustiveness assertion.
- **`.claude/rules/java.md`** — Javadoc syntax and the signature rules.
- **`reference/classification.md`** — which contracts you may decide and which
  you must propose, and the checkpoint format.
- **`~/.claude/skills/roughdraft/SKILL.md`** — the review tool every gate runs
  through: how to open a document, that the command blocks until the user closes
  it, and the CriticMarkup both sides write in.

### Reading the steps

**Read step documents one at a time.** Read the step 0 document at invocation.
Read any other step's document only when the record names that step as the next
step to run. Never read ahead: each step reads the tree that the step before it
left.

## Step 0: Resolve, check, resume

See [reference/step-0-resolve-check-resume.md](reference/step-0-resolve-check-resume.md).

## Step 1: Inventory

See [reference/step-1-inventory.md](reference/step-1-inventory.md).

## Step 2: Class design

See [reference/step-2-class-design.md](reference/step-2-class-design.md).

## Step 3: Make illegal states unrepresentable

See [reference/step-3-unrepresentable-states.md](reference/step-3-unrepresentable-states.md).

## Step 4: Extraction and placement

See [reference/step-4-extraction-and-placement.md](reference/step-4-extraction-and-placement.md).

## Step 5: Write the contracts

See [reference/step-5-write-the-contracts.md](reference/step-5-write-the-contracts.md).

## Step 6: Triage the tests

See [reference/step-6-triage-the-tests.md](reference/step-6-triage-the-tests.md).

## Step 7: Fix test-only surface

See [reference/step-7-test-only-surface.md](reference/step-7-test-only-surface.md).

## Step 8: The suite gate and the visual gate

See [reference/step-8-suite-and-visual-gates.md](reference/step-8-suite-and-visual-gates.md).

## Step 9: Judge the diagrams

See [reference/step-9-judge-the-diagrams.md](reference/step-9-judge-the-diagrams.md).

## Step 10: Coverage — once, scoped

See [reference/step-10-coverage.md](reference/step-10-coverage.md).

## Step 11: Mutation — opportunistic

See [reference/step-11-mutation.md](reference/step-11-mutation.md).

## Step 12: Adversarial review

See [reference/step-12-adversarial-review.md](reference/step-12-adversarial-review.md).

## Step 13: Close out

See [reference/step-13-close-out.md](reference/step-13-close-out.md).

## Things that stay true throughout

- **No step changes anything ahead of its gate, unless the user says otherwise.**
  Not a rename, not a Javadoc line, not a "while I'm here" fix in a neighbouring
  file. The gate is what makes the pass's decisions the user's, and a change made
  before it has already taken one. Only they lift it, on the terms in *The review
  gate*.
- **Findings outside the target are still findings.** A design flaw, a
  duplication, a wrong abstraction or a misleading name in a caller, a neighbor,
  or a shared helper this work merely passes through gets raised with file and
  line references. Never call anything out of scope.
- **The correct structure is proposed whole, never narrowed because it reaches a
  later pass' package.** Where the right shape moves a type, redistributes a
  method, or edits a file that belongs to a target no pass has taken yet, that is
  what gets proposed — not an intermediate state, adapter, or partial version
  sized to stay inside the current target's files. Crossing the boundary is a
  fact about the design, not a reason to design around it. Whether to execute the
  cross-boundary part now or carry it forward, tagged to the pass that owns that
  package, is the user's call at the gate — the same choice *Findings outside the
  target are still findings* already gives them, applied to a change instead of
  an observation.
- **No production surface exists solely for tests** — no method, no accessor, no
  widened field, no relaxed visibility, no reflection.
- **Serena's `jet_brains_*` tools for all Java exploration and refactoring**, per
  `.claude/rules/serena.md`.
- **Never commit.** The pass leaves everything in the working tree; when to
  commit, and in what shape, is the user's. Do not stage, do not amend, and do
  not invoke `/commit-commands:commit`.
- **Snapshot per step**, each with the record's status change. One Bash call,
  which leaves the working tree untouched:

  ```bash
  git add -A && git stash store -m "design pass <target>: step N" "$(git stash create)"
  ```

  `git add -A` is required — `stash create` snapshots the index and tracked
  files only, so an untracked new file is otherwise lost. **Never snapshot with
  `git stash push`**, which empties the tree first. Restore with `git stash
  apply <sha>`, never `pop`: the stash stack is shared with every worktree and
  another session may be using it. An unsnapshotted step is a lost step.
- **Steps decompose; the record links where.** A step whose approved work spans
  more than one sitting gets an execution plan in `plans/` via
  `/make-plan`, and the record's row for that step links it. Step 3 over a
  package, and any step that turns out to touch tens of files, are the usual
  cases.

  The record holds *what was decided*; the plan holds *what is left to do*, one
  phase at a time, with a dashboard the implementer ticks. Neither restates the
  other — a plan that re-argues a decision drifts from the record, and a record
  that tracks progress drifts from the plan.

  **The plan is deleted with the record at step 13.** It is working memory on the
  same terms, and `plans/design-pass-register.md` states why under *The record is
  working memory*.

## The record template

Read only when step 0 creates a new record: [reference/record-template.md](reference/record-template.md).
