## Step 6: Triage the tests

**Inline.** Each existing test resolves against the floor in `design.md`, not
against a clause count:

- **keep** — it exercises a real algorithm, an invariant spanning several calls,
  or behavior with a known-correct corpus, and it can fail.
- **rewrite** — the case is real but the test is wrong about it: wrong level, a
  name that does not name the promise, assertions against mocks, an arrangement
  that does not reach the case, or several tests pinning one case a parameterized
  test states once.
- **discard** — a test of a guard no caller can reach, of a state a type now
  makes unrepresentable, of a private helper's decomposition, of an
  implementation detail the contract promises nothing about, or a duplicate.

Then **add only the cases the floor calls for and nothing asserts.** A clause
with no case is not automatically a gap; ask first whether the design already
enforces it.

**One table, and it is the body of `plans/design-pass-<pass>/findings-6.md`.**
Triage and proposal are one review: a test kept, a test rewritten, a test
discarded and a case added are four dispositions of one list, and splitting them
across two documents hides that a discard and an addition are usually the same
case moving.

| Test | Kind | Disposition | Why |
|---|---|---|---|
| the promise being checked, in one line | which of the three kinds in `design.md` | keep · rewrite · discard · add | one line |

**A discard is a row whose *Kind* cannot be filled.** It fits none of the three
kinds, which is why it is going — so the column is the table's own check. A row
you can justify only by naming a branch, a guard or a private helper is a
discard, whatever you meant to put in it.

**Then the review gate: open, review, resolve.** No `@Test` is written and none is
deleted until the table has been through it.

Two traps, both here:

- **Before writing any `@Test`, ask whether it will sit beside a same-shape
  sibling.** If yes, both are rows in one `record` case table from the first case.
  A varying lambda does not disqualify a case; only a varying *assertion* does.
- **Do not claim "enumerated in full" unless something fails when the domain
  grows.** Drive cases from `@EnumSource` / `values()` / a sealed hierarchy, or
  assert that the table's rows are exactly the domain — a hand-listed table can
  carry that claim for domains it never actually checks.

**Snapshot per test class or per coherent group.**
