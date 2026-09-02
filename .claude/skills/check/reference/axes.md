# The Four Review Axes

Four agents, one per axis. Design and Contract & API run on **fable at low
effort**; Correctness & Efficiency and Test Conformance run on **sonnet**.

The two fable axes are the ones where a smaller model reliably returns a
plausible-sounding workaround instead of the actual cause, or a contract
paraphrased out of the method body instead of a promise. Those are the two
failures this skill exists to avoid.

| Axis | Model | Input | Covers |
|---|---|---|---|
| Design | fable (low effort) | production scope, plus the tests when the test scope is non-empty | architecture, boundaries, reuse and duplication, wrong abstractions |
| Contract & API | fable (low effort) | production scope, plus the doc scope when it is non-empty | depth earned by fan-in; contract stated / complete / derived from the domain; contract duplicated into prose; naming; signature quality; test-only surface |
| Correctness & Efficiency | sonnet | production scope | real defects, null contracts, repeated or redundant work |
| Test Conformance | sonnet | test scope and its production counterparts | should each test exist at all, and does it exercise what the contract promises |

## The prompt preamble

Every agent this skill spawns gets this text at the top of its prompt, followed
by its own axis brief and the material it is reviewing. Reproduce it as written;
do not paraphrase it away.

> MANDATORY: Read `.claude/rules/serena.md` and follow it for all Java
> exploration.
>
> MANDATORY: Read these two files in full before reporting anything, and follow
> them for every finding:
>
> - `check-findings.md`
> - `.claude/skills/check/reference/design-flaws.md`
>
> MANDATORY: Read `~/.claude/guides/design.md` before reporting. Each of its
> rules carries the observable that flags its violation in existing code, and
> those observables are what this review looks for.
>
> **The yardstick of this review is the design, then the contract, never the
> implementation.** Ask first what a type could carry that the code checks at
> runtime, since an invariant the type carries needs no contract clause and no
> test. Then judge the code against what it promises, and the promise against
> what the domain requires — never either against the number of lines or branches
> that run.
>
> Your findings will be shown to a reader who has not read the code, the tests,
> or anything else in this repository. An agent that returns dense,
> jargon-filled findings has not done its job.
>
> `design-flaws.md` binds what you may propose, not just what you may report.
> When a finding is a symptom of something structural, your job is to say so and
> hand it up — never to design a neater version of the workaround. Proposing an
> extra parameter, flag, or cache to route around a structural problem in
> production code, or an extra mock, test-only accessor, or setup helper to route
> around one in tests, makes the codebase worse and fails the review.
>
> You are free to report defects you notice outside the files under review — a
> caller, a neighboring method, a bug in production code a test exercises, a
> misleading shared helper, a stale comment or guide. Do not filter those out
> for being out of scope.

Agents read the two doctrine files rather than receiving pasted copies: it costs
one tool call each and keeps a single copy authoritative, so an edit to the
doctrine reaches every agent on the next run with nothing to re-synchronize.

---

## Axis 1: Design — fable (low effort)

The other axes look for defects; this one looks for the reason the defects are
there. Give it the review target — and, when the test scope is non-empty, the
tests too, because a test that strains is evidence about the production design —
along with this question: *if you had to explain every awkward thing in this code
with one structural mistake, what would it be?*

### Structure

1. **Misplaced responsibility** — logic living in a class that has to reach for
   the data it needs, when the class that owns the data should own the logic.
2. **State that must be kept in sync** — two representations of one fact, where
   correctness depends on every writer remembering to update both.
3. **Control coupling** — flags, modes, or enum parameters that make one method
   behave as several; the caller is really selecting an implementation.
4. **Abstractions that leak by necessity** — a boundary whose callers cannot do
   their job without knowing what is behind it, so every new caller re-learns
   the internals and every internal change breaks callers.
5. **Growth by special case** — a structure where each new case was handled by
   adding a branch rather than by fitting the case into the model, so the next
   case costs the same again.
6. **Wrong seam** — units that do not match the way the code actually changes,
   so one conceptual change always touches several files in lockstep.
7. **Wrong representation** — a type whose shape does not fit the concept, so
   fields have to be filled with values that mean nothing, callers ignore parts
   of what they are handed, or "absent" is encoded as a value some legitimate
   state also produces. Look hardest where a general-purpose type from the
   platform (a rectangle, a point, a map, an array of two) is standing in for a
   domain idea that has a different shape.

### Invariants the types could carry

Run this before anything else in this axis. Each finding here deletes a guard, a
`@throws` clause and a test permanently, and none of them rot.

7a. **A validated record whose domain is a known finite set** should be a closed
    enum. The tell is a constructor rejecting values at runtime alongside a
    separate list somewhere enumerating the valid ones — one fact in two places.
7b. **Two interchangeable primitives a call site could transpose** should be
    wrapper types.
7c. **A mode-selecting boolean, or a bare int standing for a mode**, should be an
    enum.
7d. **A guard whose rejected value no caller can produce.** Name the callers that
    can reach it; if none can, the guard is dead. A dead guard that returns an
    arbitrary default is the worst form, because it masquerades as success.
7e. **A primitive crossing more than one layer, or the same range checked in two
    places.** The boundary should convert the value into a domain type rather
    than validate it and pass the raw one inward.
7f. **A `@Nullable` that should be total** — either always derivable, in which
    case the contract should expose the derived query rather than the raw field,
    or null until some `init()`, which is a lifecycle defect rather than a
    nullability one.

### Reuse and duplication

Duplication is not a separate axis because it is the most common single instance
of a structural fault: the same logic in two places is one concept that was never
given a home. Search the **entire codebase**, not just the review target.

8. **An existing utility already does this** — search other packages for the same
   operation before accepting a hand-rolled one. Hand-rolled string manipulation,
   manual path handling, ad-hoc type checks and custom environment probes are the
   usual candidates.
9. **The same logic in two packages** — report it as one concept with two homes
   and say which one should own it, rather than as two independent findings.
10. **Copy-paste with variation** — near-duplicate blocks differing in a value or
    a branch. The varying part is a parameter; the block is a function.

Per the global rules, when new code resembles existing code the existing code is
refactored first so both callers share one helper. A comment acknowledging
duplication is not a resolution.

### What the tests reveal about the design

Run these only when the test scope is non-empty. A test is a second consumer of
production code, so difficulty writing one is evidence about the code.

11. **Hidden dependencies** — the class under test obtains its collaborators
    itself (singletons, statics, global lookups, `new` in a constructor) instead
    of receiving them, so a test cannot substitute one without fighting the
    language.
12. **Unreachable behavior** — an error path no caller can provoke from outside.
    Distinguish two cases: a guard the contract deliberately makes unreachable is
    correct and needs no test; a behavior the contract promises but nothing can
    provoke is a seam in the wrong place.
13. **Doing too much** — a method whose test needs several unrelated fixtures,
    which means the method has several unrelated responsibilities.
14. **Interaction-only verification** — tests forced to assert on calls because
    the code returns nothing observable; usually the result should be a value
    rather than a side effect.
15. **Fixture gravity** — a shared setup every test in the file must inherit
    whether or not it needs it, so tests are coupled to each other through state
    the production design forced into existence.
16. **Tests that ratify a flaw** — the suite has absorbed a design mistake and
    now protects it: constants named for the fact that a value is ignored,
    assertions over whole composite values whose parts are not all real, an
    emptiness check standing in for a state the type cannot express. These tests
    make the mistake look intentional and make correcting it look expensive.
    Report the design flaw and count these tests as *removals*, not as breakage.

When the Contract & API axis reports a member reachable only from tests, its
structural diagnosis belongs here: the seam is in the wrong place, and that is
what needs saying.

### Before proposing anything

For every value the review target computes, establish **who reads it in
production** — per *Before proposing a fix, find out who reads the value* in
`design-flaws.md`. Trace it with `jet_brains_find_referencing_symbols`, not by
assumption. A value with no production reader is a finding on its own, and
usually the visible end of the structural mistake you are looking for. Tests are
not consumers.

Report the flaw, the symptoms it explains, the corrected design, and what the
change touches, per *Reporting a design finding*. Consult the design notes under
`docs/` first, and return no findings when the code is structurally sound, per
*Check the design notes before reporting*.

---

## Axis 2: Contract & API — fable (low effort)

An API that **fan-in earns a contract for** is judged against that contract, and
the contract is judged against the domain. Read `.claude/guides/contracts.md` and
the **Writing a Contract in Javadoc** and **Signature Rules for Contracts**
sections of `.claude/rules/java.md` before reporting.

Where a doc scope came with the code, it is part of this axis's material, not
background: a contract restated in prose is a second copy of the promise, and
rule 11 below is where that is judged. `docs/` and `plans/` are the only places
a stale contract can sit without the compiler or a reference lookup reaching it.

### Does it earn a contract, and does it have one?

1. **Depth follows fan-in** — how many callers rely on the promise times how
   rarely it changes. A trivial accessor, a one-line delegation, and a private
   helper with one caller earn an accurate name and nothing more; a full contract
   on one of those is depth spent where nothing relies on it, and is a finding in
   its own right.
1a. **An API several callers rely on and that has no contract is a finding.**
    Scope this to what the review target adds or changes. Say what the contract
    should promise, not merely that one is missing.

### Is the contract complete?

Judge against the eight elements in `contracts.md`: summary, preconditions,
postconditions, boundary semantics, errors, result invariants, side effects,
relationships. Report the specific clause a caller cannot find:

2. **A documented method whose return type is not `void` and which has no
   `@return`.** Mechanical, no judgment: the tag is mandatory, and a summary
   sentence that already says what comes back does not excuse its absence. The
   same for a parameter with no `@param`.
3. **A `@throws` that does not name the exact condition.** "if invalid" is not a
   clause a test can be derived from.
4. **Boundary semantics unstated** — inclusive or exclusive, empty input, zero,
   negative, ties, equality.
5. **A `@Nullable` return whose contract never says what null *means*.** The
   project bans `Optional`, so the annotation is the whole vocabulary and it says
   only *this may be absent*. Absence is exactly what the caller must branch on.
   The same applies to a `@Nullable` parameter: state what passing null selects.
6. **Side effects unstated** — mutation, messages posted, files written,
   threading requirements.
7. **A promise stated somewhere that is not the contract** — in a test's
   assertion description, in an implementation comment, in a commit message.
   A promise a caller cannot find is a promise that is not being kept. Move it
   into the Javadoc.

### Is it derived from the domain?

8. **A contract that narrates the implementation is not a contract.** The tell of
   a real one is that **the current implementation could in principle violate
   it**. A promise the code could not possibly break is describing the code. When
   you find one, say what the domain actually requires instead.
9. **Where the promise is a musical or domain judgment** — tuplets, beaming,
   ties, melisma placement, key signatures — do not decide it. Report what the
   contract fails to state and what you believe it should promise, marked as
   needing confirmation. A confident, plausible, wrong contract is worse than
   none, because every test downstream is then derived from it.

### Is it at the right tier?

10. **A rule repeated on several methods belongs one tier up** — on the class or
   in `package-info.java`. Repeated contracts drift and eventually contradict one
   another.
11. **A rule spanning subsystems belongs in `docs/`**, with the method's Javadoc
    linking to it rather than paraphrasing it. The paraphrase is the copy that
    goes stale.

    The converse is the same finding, and is the one you will actually meet: a
    passage in `docs/` or `plans/` stating a promise about one member is that
    member's contract written where nothing can keep it honest, and a claim
    appearing in both a Javadoc and a doc is one copy too many whichever was
    written first. **Start from whichever side you were given.** A Javadoc naming
    no doc is not evidence that no second copy exists — paraphrasing *instead of*
    linking is precisely what leaves no link to follow, so this defect conceals
    itself from a search that begins at the link. Where the doc scope is
    non-empty, read each passage against the contracts of the members it
    discusses, matching on the claim rather than on the wording; a duplicate
    survives being reworded and a verbatim clause is the end of the range, not
    the test for it. The fix is to cut the prose back to what no contract can
    hold — which piece owns what, and why the set is complete — and have the
    Javadoc link to it.

### Names

12. **The name is the part of the contract every caller reads, and for most
    callers the only part.** A name that overstates, understates, or misdescribes
    what the method does produces expectations the doc comment does not repair,
    because the doc comment is not what the caller read. Report the accurate name
    concretely.
13. **Never soften a rename finding on the grounds of churn.** `jet_brains_rename`
    updates the call sites. There is never a reason to resist a rename once a
    better name is determined; say what the name should be.

### Signatures

14. **More than four parameters: a `record` parameter object is required.**
15. **Two or more same-typed parameters a call site could transpose: a `record`,**
    regardless of the total count. Adjacent booleans are the common case, and the
    compiler cannot catch a transposition.
16. **A boolean that selects a mode or a type: an enum.** A literal `true` at a
    call site names nothing.

### Test-only surface — a hard finding, not an advisory one

This check is mechanical and has no judgment call in it.

17. For each member the review target adds, changes, or widens, run
    `jet_brains_find_referencing_symbols`. **If every reference resolves under
    `src/test/`, it is test-only surface.** Report it. This includes methods,
    accessors, widened fields, and relaxed visibility of any kind, whatever the
    member is named.
18. **Reflection into production internals from a test is the same violation.**
    `getDeclaredField`, `setAccessible`, or any write to a private field from a
    test adds no production surface, so it satisfies the letter of the rule while
    defeating its point: the suite is now coupled to private names, and a rename
    fails at runtime instead of at compile time.
19. Classify every hit into one of three categories, because the fix differs:
    - **Genuinely test-only** — delete it, or restructure so production supplies
      what the test needs. When a test cannot arrange the state it needs, the
      answer is a constructor or factory that takes that state, used by
      production too.
    - **A misnamed internal API** — it takes explicit arguments and returns a
      value, so it is already a coherent unit with its own contract. Rename it to
      the concept and write the contract.
    - **An incomplete lifecycle contract** — a class with `initialize()` and no
      way back has a missing half, tests or no tests. The fix is to name and
      document the teardown, not to delete the member.
20. **A constant is part of the contract if a contract's Javadoc names it** via
    `{@value #X}` or `{@link Class#X}`; its visibility then follows the contract
    that cites it. If no contract names it, it is implementation, and a test that
    needs it is testing implementation — which is the finding. Visibility
    justified in a comment by a test ("package-private so the test can…") is the
    finding stated in the source.

---

## Axis 3: Correctness & Efficiency — sonnet

Real defects in the production scope. This axis surfaces candidates that the
orchestrator re-validates before fixing, so state confidence plainly.

**A concrete production bug outranks every other finding in this review.** Report
it first and say what a user would see.

### Correctness

1. **Logic errors** — wrong operator, wrong boundary (`<` where `<=` belongs),
   inverted condition, off-by-one, a loop that skips the last element, integer
   division where a rounding rule was intended.
2. **Null contracts** — a `@Nullable` value dereferenced without a check; a
   `@NonNull` parameter that some call site can reach with null; a null return
   the caller treats as a value. Recall that the project bans `Optional` and see
   `.claude/guides/null-handling.md`.
3. **The code disagrees with its own contract** — the Javadoc promises one thing
   and the body does another. Say which of the two is wrong about the domain; if
   it is the contract, that is a contract finding and must be stated explicitly
   rather than resolved by quietly rewriting the promise.
4. **Swallowed failures** — an exception caught and discarded, an error path that
   returns a plausible-looking default, a failure logged where the caller needed
   to know.
5. **Unsafe state** — shared mutable state touched from more than one thread, a
   collection published while still being built, an EDT-only object used off the
   EDT.
6. **Stringly-typed code** — a raw string where a constant or enum already
   exists. A typo in a string is a runtime defect the compiler would have caught.

### Efficiency

7. **Unnecessary work** — redundant computation, repeated file reads, the same
   value derived twice, N+1 patterns.
8. **Hot-path bloat** — new blocking work on startup, or in a per-render or
   per-event path.
9. **Unnecessary existence checks** — pre-checking that a file or resource exists
   before operating on it; operate directly and handle the failure.
10. **Memory and leaks** — unbounded structures, listeners and subscribers never
    removed, caches with no eviction.
11. **Overly broad operations** — reading a whole file when a portion is needed,
    loading every item to find one.
12. **Missed concurrency** — independent operations run sequentially where the
    surrounding code already runs work in parallel.

Redundant *state* — a cached value that could be derived, two fields that must
agree — is a Design finding, not one for this axis. Report it as a symptom and
say so.

---

## Axis 4: Test Conformance — sonnet

Given the test scope and its production counterparts. Read
`.claude/guides/testing-common.md` in full, and
`.claude/guides/testing-unit.md` for the conventions; both are the source of
truth for what this axis enforces.

**This axis never counts branches, never asks what fraction of a method ran, and
never proposes a test in order to reach a line.** It asks two questions of the
suite, in this order: *should this test exist at all?* and only then *does it
exercise what the contract promises?*

A test earns its place only where the design cannot enforce the promise — a real
algorithm with logic worth checking, an invariant spanning several calls, or
behavior with a known-correct corpus. **A test of a guard no caller can reach, a
test of a state a type could make unrepresentable, and a permanent pin on one
historical bug input are all discard findings**, and the first two are handed up
to Design as the more useful form of the same finding.

If a method's contract is fully exercised and half the method never runs, that is
a question for the contract or a dead-code finding against production — never a
test finding.

### Mapping: does each test assert a contract case?

1. **Name the clause.** For every test in scope, identify the contract clause it
   asserts. A test that maps to no clause is a **discard** finding — including a
   test of a private helper's decomposition, a test pinning an implementation
   detail the contract promises nothing about, and a duplicate of a case already
   asserted elsewhere.
2. **A test whose name does not name its clause** is a rewrite finding. The name
   must state the condition and the promised outcome, not the method called and
   not the setup performed. A test that cannot be triaged later against a changed
   contract is a test nobody will dare delete.

### Derivation: was this written from the contract or from the code?

This is a distinct fault from the one above. A test can be aimed at a real
contract case and still assert a detail its author took from the method body. The
mechanical form of the check:

3. **Does every assertion correspond to a clause of the contract?** An assertion
   with no clause is one of two things: a promise missing from the contract, or a
   test written from the code. Say which you believe it is. See *Write from the
   contract, not from the code* in `.claude/guides/testing-common.md`.
4. **Tells of a test written from the body** — it pins an ordering, a format, a
   collection type, or an intermediate value the contract never mentions; it
   asserts the exact number of times a collaborator was called; it breaks on a
   pure refactor that changed no promise; it reproduces the implementation's
   arithmetic rather than the promised relationship.
5. **Tells in the arrangement** — the test reaches past the declaring class's
   public API to set up: reflection, a widened member, a package-private
   constructor that exists for it. Reaching past the public API to arrange is the
   same violation as writing the test from the body, and it is where the pressure
   actually comes from. Hand it to Design and to Contract & API rather than
   proposing a tidier accessor.

### Completeness: which promised cases have no test?

Judged against the contract's clauses, never against the implementation.

6. **A finite domain sampled instead of enumerated** — an enum, a small set of
   states, a pair of flags. All of them, via `@ParameterizedTest` with
   `@MethodSource` or `@EnumSource`, costs what picking two costs, and picking
   two is what leaves the third one broken.
6a. **A claimed enumeration nothing keeps true.** Distinct from 6, and invisible
    to it: the table is complete today, so nothing looks sampled, but the rows are
    hand-written literals, so adding a constant leaves the domain uncovered and
    the suite green. Wherever a test treats a domain as complete, the cases must
    come from `@EnumSource` / `values()` / a sealed hierarchy's permitted
    subclasses, or a separate assertion must pin the table's rows to the domain.
    Neither present is a finding against the test, not the contract. Where the
    domain is private, widening it is test-only surface and not the fix.
7. **A missing input class or extreme** — the contract names classes of behavior
   and their boundaries; each needs a representative, and the extremes need one
   each. Volume past that is cost without safety, so absence of a *third* example
   of the same class is not a finding.
8. **An invariant pinned as a table** — where the contract promises a property
   ("the outputs sum to the requested total", "every result is positive"), the
   test should assert the property across many representative inputs, not one
   expected output per input.
9. **A clause with no test is not automatically a gap.** Ask first whether the
   design already enforces it and whether any caller can reach it. Report it as
   missing only when the answer to both is no.

### Trustworthiness

10. **It can fail** — no assertions, assertions only against mocks, a tautology,
    `isNotNull()` on a value that cannot be null, or an assertion on a stubbed
    return value all pass regardless of the production code. A misconfigured
    fixture does the same thing more quietly: check that the arrangement actually
    reaches the case the test claims.
11. **No flakiness** — no dependence on timing, execution order, shared mutable
    state, the real clock, or the real filesystem.
12. **No order dependence**, except a class that shares one cumulative fixture
    and pins its order explicitly.
13. **Diagnostics** — a bare boolean assertion where an AssertJ matcher would
    localize the failure.
14. **MBassador subscribers** — a test that registers a non-persistent subscriber
    must unsubscribe it in an `@AfterEach` or a finally block, not at the end of
    a happy path. Weak references make the leak intermittent, which is worse.

### Level

17. **Wrong level** — the rubric is in `testing-common.md`. E2E proves *wiring*,
    one test per path, never per case; a behavior that can be asserted with the
    singleton mocked is not an e2e case. A trivial getter, a pure data holder, or
    pure display wiring is **none**.
18. This axis audits unit tests and never runs the e2e suite.

### Constants

19. **A production constant's literal redeclared in test code** is a finding. The
    question it raises is whether the contract should name the constant — not
    whether the field should be widened. See the constants rule in Axis 2.

### When a test strains

The honest finding is often about the production design, not the test. When a
test needs six mocks, or reaches into internals to arrange a scenario, or asserts
an intermediate value because the real outcome is unreachable, report it and hand
it up to Design. Never propose one more mock, one more accessor, or one more
setup helper.

And when the contract does not answer the question the test is trying to ask,
**the finding is against the contract** — not a licence to open the body.
