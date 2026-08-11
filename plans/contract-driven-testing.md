# Contract-Driven Development and Testing
A design discussion covering the proposed shift from coverage-driven to contract-driven testing, what it changes in the guides, the architectural consequences, and the decisions still open.

> **Status.** Sections 1–6 are current and are cited by
> `contract-driven-rollout.md` — the philosophy (§2), the conflicts with the
> current guides and their line numbers (§3), the six guardrails (§4), the dialog
> policy and the two `SongSettingsDialog` defects (§5), and the test-only-surface
> inventory (§6).
>
> **Sections 7 and 8 are superseded** by `contract-driven-rollout.md` and must
> not be executed from. §7's document set was overridden — language-neutral
> principles now go to the *global* `~/.claude/rules/development.md`, not the
> project rules file. §8's decisions are all answered; the rollout's decision
> table is authoritative. Both are kept as the record of how the decisions were
> reached.

* * *
## 1. The problem, quantified
| Measure | Value |
| --- | --- |
| Test LOC | 186,135 |
| Main LOC | 121,224 |
| Ratio | **1.54× — the suite is half again the size of the application** |
| `@Test` methods | 7,186 across 479 files |
| Density | roughly one test per 17 lines of production code |
| Dialog tests | 7,200 LOC / 257 tests |
| `SongSettingsDialog` | 491 production lines → 1,346 test lines across 3 files + a fixture |

This is a direct product of the current guides. `testing-common.md` supplies a quality bar ("what bug would this catch?") and a level rubric (unit / e2e / none), but **no completeness criterion** — nothing that answers _when am I done testing this method?_ The only thing in the guides that could answer it was coverage, so coverage became the stopping rule by default and the suite grew to fill it.

Contract-driven testing supplies the missing criterion: the equivalence classes of the contract, not the lines of the implementation.

* * *
## 2. The philosophy
Restated compactly so it can be edited directly.
### 2.1 Tests are derived from contracts
{==For every nontrivial API, define its externally observable contract and test each materially distinct class of behavior it promises, including boundary cases and invariants that must hold across its valid input domain.==}{>>The corollary to this should be the first and foremost rule in development.md:

When writing a method, write its API contract *first*, then write the code to fulfill the contract. This provides several benefits:

- It forces you to think first about *what* the method should do, not *how* it should do it.
- It forces you to think in terms of callers: Is the signature unambiguous? Are there more than 4 parameters? If so, then a record would probably be a better API.
- It forces you to write the code to fulfill its API, which is itself a preliminary pass at testing.<<}{id="c4" by="user" at="2026-08-11T16:50:39.223Z"}

Given a contract stating `x < 0 → A`, `x == 0 → B`, `x > 0 → C`, all three domains are part of the contract. Testing `f(-3)` and declaring the method tested is not sufficient. You want a representative of each semantically distinct input class, plus the important boundaries.

Whether the implementation contains 5 branches or 25 is irrelevant, provided the cases characterize the contract.
### {==2.2==}{>>Before this:
Method names are the first level of its contract: they should state clearly and accurately what they do. Misnamed methods lead to misaligned expectations.<<}{id="c3" by="user" at="2026-08-11T16:48:28.671Z"} Invariants over examples, where the contract is an invariant

Some contracts are properties rather than a finite table of input/output pairs. A duration transformation might promise:

- `sum(output durations) == requested duration`
  
- all output durations `> 0`
  
- `output.size() == input.size()`
  

Testing those properties across many representative inputs is more valuable than enumerating expected outputs.
### 2.3 A representative set plus the extremes is sufficient
A representative set of realistic inputs and the extreme edge cases is sufficient to test adherence to a contract, and likewise for stated invariants. Nothing more is required, and volume beyond that is cost without safety.
### 2.4 A failing test does not necessarily mean the code is wrong
It may mean the contract is not sufficient. Diagnosis is three-way — code, test, or contract — not two-way.
### 2.5 Coverage is a sanity check, nothing more
If you have supposedly tested a contract and half the method never executed, that is worth investigating: it may reveal a contract case you forgot. But you do not then manufacture a test to turn the section green.
### 2.6 Contracts live in a three-tier hierarchy
| Tier | Holds |
| --- | --- |
| Method Javadoc | the local contract — preconditions, postconditions, `@throws`, boundary semantics, result invariants, side effects |
| Class / package Javadoc | object and subsystem invariants spanning several methods |
| `docs/*.md` | architectural and domain rules spanning subsystems |

A method's Javadoc should be a test specification: you should be able to read it and derive most of the tests without looking at the implementation.

Rules such as _"a score element may belong to only one tuplet"_, _"creating a tuplet preserves the absolute playback duration of the enclosed passage"_ are domain invariants. Repeating them on five methods risks contradictory documentation; they belong in class Javadoc or `docs/`, with methods referring to them.
### 2.7 Private helpers are tested through the contract they serve
Making a helper package-private _solely_ so tests can call it is a design smell. It weakens encapsulation and couples the suite to the current decomposition, so a later split, merge, or removal breaks tests even though observable behavior did not change.

The trigger for promotion is not _"this method is 80 lines long."_ It is **"this code now represents a distinct concept with a stable contract."** At that point, promote it to a genuine internal API — often a small dedicated class — rather than merely relaxing visibility.
### 2.8 No production surface exists solely for tests
- No method may be written that is used only by tests. This includes field accessors: **fields are an implementation detail**, and the contract is the only thing visible to tests.
  
- Corollary: fields must never be widened to package-private so tests can mutate them.
  
- A private method becomes package-private only when it has become a coherent unit with its own meaningful contract (2.7).
  
- A private constant becomes package-private only when its value is domain-relevant — as opposed to an implementation detail — and it is useful in testing.
  

* * *
## 3. How this compares with the current guides
### 3.1 Already aligned
- _"Coverage is necessary, not sufficient. Treat the percentage as a gap-finder, not a grade"_ (`testing-common.md:50`) and _"'Executed' is not 'verified'"_ (`:54`) match §2.5.
  
- _"Test behavior, not implementation"_ (`:24`) matches §2.1.
  
- The `check` skill's Testability agent already carries the architectural consequence: _"_**_Doing too much_** _— a method whose test needs several unrelated fixtures, which means the method has several unrelated responsibilities"_ (`agents-tests.md:87`).
  

The doctrine is not new. What is new is that the proposed version is _operational_ and the current one is not.
### 3.2 Absent entirely
- **A completeness criterion.** See §1.
  
- **Invariant / property testing.** Not mentioned in any of the three guides; the vocabulary is entirely example-based. `@ParameterizedTest` / `@MethodSource` appear nowhere, not even in the Frameworks list (`testing-common.md:130`), though JUnit 5 is in use.
  
- **Javadoc as specification.** `java.md` has exactly one Javadoc rule — `{@value}` for constants (`java.md:141`). Nothing requires preconditions, postconditions, `@throws`, or invariants anywhere in the codebase's rules.
  

Note that the third tier already exists: `docs/*.md` holds subsystem design notes and CLAUDE.md already directs agents to read them. The missing tiers are method and class Javadoc.
### 3.3 Direct conflicts
`Testability Over Encapsulation` **(**`testing-common.md:208-229`**) is the opposite of §2.7 and §2.8 and must be deleted.** It currently reads:

> A private method that is a self-contained unit worth testing directly → make it package-private and test it directly, rather than driving it through a public method that needs heavy setup.

Its trigger is ergonomic ("needs heavy setup") where §2.7's is conceptual. Its remedy is bare visibility relaxation where §2.7 requires promotion to a real internal API. `testing-common.md:86` reinforces it by listing _"widening a member to package-private to test it directly"_ as a sanctioned reason to keep a behavior at unit level.

Worse, `:225` instructs authors to add a package-private getter/setter to production for any private field a test needs — while the `check` skill's own Testability agent is instructed to report exactly that as a defect:

> **Test-only surface** — production API that exists solely because a test needed it, which is the design admitting the seam is in the wrong place. (`agents-tests.md:89`)

**The guide currently instructs authors to create the precise defect the review skill is instructed to find.** That contradiction exists today, independent of this proposal. §2.8 resolves it in the review skill's favor.

The one part worth keeping is the ban on redeclaring a constant's literal in test code (`:223`) — see §4.5 for how it survives.
### 3.4 Process conflict in the `check` skill
Phase 3 runs coverage unconditionally whenever the test scope is non-empty (`check/SKILL.md:70`), and its output is a per-class list of uncovered branches and error paths ranked by criticality (`agents-tests.md:116-138`). Whatever the "gap-finder, not a grade" caveat says, a ranked list of uncovered regions handed to an agent told to _fix what it finds_ is a to-do list — it structurally produces the green-chasing §2.5 bans.

Under the new regime Phase 3 asks a different question of each uncovered region:

> Does this correspond to a contract case missing from the contract, or to implementation the contract promises nothing about?

First answer amends the contract and its tests. Second answer leaves it alone — and if nothing can reach it, that is a dead-code finding against production, not a test finding.

Mutation testing survives the change better than coverage, since a surviving mutant means real behavior went unobserved. But its status shifts too: a mutant surviving in code the contract makes no promise about is not automatically a hole.

* * *
## 4. Guardrails the philosophy needs
{==Six places where the philosophy as stated is correct but under-specified for handing to an agent.==}{>>Agreed on all counts.<<}{id="c5" by="user" at="2026-08-11T16:57:05.029Z"}
### 4.1 "A failing test may mean the contract is insufficient" is also an escape hatch
As written, it licenses: test fails → weaken contract → green. The rule needs an ordering. Changing the contract to match the code is legitimate **only when the contract was wrong about the domain** — a music-notation fact — never because it is the cheapest route to green. And since a contract is what callers rely on, changing one is a visible decision: stated explicitly, never made silently mid-fix.
### 4.2 The failure mode of contract-first is contracts written by reading the implementation
If the Javadoc is derived from what the code does, the derived tests pass by construction and verify nothing — coverage-chasing replaced by a more elegant emptiness. A contract states what a caller is _entitled to_ rely on, decided from the domain. The tell of a real one is that the current implementation could in principle violate it.

**Workflow consequence.** For mechanical APIs — geometry, unit conversion, collections, MusicXML round-tripping — contracts can be drafted and spot-checked. For domain APIs — tuplets, beaming, ties, melisma placement, key signatures — "what should this promise" is a musical judgment that must be proposed and confirmed, not decided unilaterally. Contract work in `dom/` and `layout/` will be a back-and-forth rather than something delivered finished.
### 4.3 "Representative" is under-determined for finite domains
For a parameter that is an enum, a boolean pair, or an `ElementType`, "representative" invites picking two. The honest answer is _all of them_, which `@ParameterizedTest` over `EnumSet.allOf` makes as cheap as one.

**Rule:** enumerate the domain when it is finite and small; sample representatively only when you cannot enumerate.
### 4.4 Reflection into production internals is the same violation as widening
`ReflectionTestHelper` (`src/test/java/songscribe/ui/selection/ReflectionTestHelper.java:228-236`) reaches into `ActionReflector` and writes two private fields:

```java
var reflField = ActionReflector.class.getDeclaredField("reflectableActions");
reflField.setAccessible(true);
reflField.set(reflector, new ArrayList<>(actions));
```

It adds **no** production surface, so it satisfies the letter of §2.8 while violating its point entirely — the tests are now coupled to private _field names_, and a rename fails at runtime with `RuntimeException("Failed to inject test actions")` rather than at compile time. That is strictly worse than the package-private setter §2.8 bans, because the honest version would at least break the build.

Without this clause the rule relocates the coupling somewhere the compiler cannot see it.

The defect underneath is the one the philosophy predicts: `ActionReflector` obtains its reflectable and managed action lists from the `Actions` singleton scan instead of receiving them. A constructor taking them removes the reflection, shrinks the helper's "bypasses the singleton graph" apparatus, and widens nothing.
### 4.5 The pressure to widen comes from _arranging_ state, not reading it
Reading results is the easy half — the contract must expose them or they are not observable and should not be tested. Arranging state is where the pressure actually comes from. So:

> When a test cannot arrange the state it needs, the answer is a constructor or factory that takes that state — which is real API, used by production too — never an accessor, and never reflection.
### 4.6 A mechanical test for "domain-relevant" constants
§2.8's constant rule ties widening to "useful to test those package-private methods," but a domain-relevant constant may equally be needed to test a public one. Dropping that tie leaves the question of how to judge domain-relevance, which has a mechanical answer that reuses a rule already in `java.md`:

> A constant is part of the contract **if a contract's Javadoc names it** via `{@value #X}` or `{@link Class#X}`. If it is cited in a documented promise ("clamped to `{@value #MAX_ZOOM_PERCENT}`"), it is contract, and its visibility follows the contract's. If no contract mentions it, it is implementation — and a test that needs it is testing implementation, which is the finding.

This preserves the old ban on redeclaring literals in test code without the old reflex of widening whatever a test happened to reach for.
### 4.7 The rule is mechanically enforceable
Run `jet_brains_find_referencing_symbols` on a member; if every reference resolves under `src/test/`, it is a violation. No judgment call, so it belongs in `check` as a hard finding rather than an advisory one.

* * *
## 5. The dialog policy
**Position:** no unit tests for dialogs unless they have significant internal functionality — and where they do, the response is to make that functionality testable _outside_ the UI with no part of the UI mocked, packaging dialog inputs into a record if that is what it takes.

This codebase is already half-shaped for it. `SongSettingsDialog.commitMetadata()` gathers widget values, builds a `SongMetadata` record, and hands it to the domain via `postWithModification`. That is the shape — it already exists. What is missing is that the _decisions_ still live on the UI side of the seam.
### 5.1 Two concrete defects in `SongSettingsDialog`
`isValidData()` **fuses validation with presentation.** It computes `lyricsFit(...)` and then calls `OptionDialogs.showErrorMessage(contentPanel, …)` inline. Because the method both decides and displays, nothing can call it without a live `contentPanel` — which is exactly why the tests mock UI. Validation should return a result and the dialog should present it. `lyricsFit(song, currentFont, newFont, lineWidthSs)` underneath is already pure and is the real contract worth testing.

`getLineWidthFieldForTest()` **(**`SongSettingsDialog.java:208`**)** is production API that exists only for tests — the artifact of `Testability Over Encapsulation`. It goes when that rule goes.
### 5.2 The rule to write into `dialogs.md`
> `StandardDialog`'s `getData()` / `isValidData()` / `setData()` move values across a record boundary and make no decisions. Validation and application are free functions whose signatures contain **no Swing types**: `validate(Input) → ValidationResult` and `apply(Song, Input)`. The dialog gathers widgets into `Input`, calls `validate`, presents whatever failures come back, and calls `apply`.

The no-Swing-types-in-the-signature test is the useful part: mechanical, and a reviewer can apply it without judgment. If you cannot state the signature that way, the logic is still entangled and _that_ is the finding.

Under this rule the dialog's own three steps are wiring, classified `none`, and most of the 257 dialog tests evaporate rather than being rewritten.

* * *
## 6. Inventory of existing test-only surface
A name-based sweep of `src/main` finds **31 members across 14 files**. That is only the ones _named_ for it; members widened without a telltale name are not in this count. They are not all the same violation, and the distinction decides how much work §2.8 actually implies.
### 6.1 Genuinely test-only — delete or restructure (~8)
| Member | Note |
|---|---|
| `SongSettingsDialog.getLineWidthFieldForTest()` | dies with the dialog policy |
| `FontDialog.java:37` | a widened **field**, commented "Widened to package-private for testing (FontDialogTest accesses it directly)" — the exact corollary being banned |
| `LyricEditor.setFocusedForTesting` / `setSuppressDismissAdjustmentForTesting` | test-only state mutators |
| `MessageCenter.setPublicationErrorProbeForTesting` / `setSubscriptionProbeForTesting` | seams in the wrong form |
| `RuntimeError.setExitHandlerForTesting` / `resetAlertShownForTesting` | an exit handler is a strategy belonging in a constructor or `initialize()` parameter |

The probes and the exit handler become legitimate once injected the way production injects them.
### 6.2 Misnamed internal API — rename and document (~4)
`SMuFLMetadata.requireMapValueForTesting`, `getAdvanceWidthForTesting`, `getAdvanceWidthOrZeroForTesting`, `Prefs.parseJsonValueForTest`.

These take explicit arguments and return values — they are §2.7's "coherent unit with its own meaningful contract" already. The only thing wrong is the name, which records that the author thought of them as scaffolding. Rename to the concept, write the contract, done. Cheapest wins in the list.
### 6.3 Lifecycle — the large category (~11)
`Actions.resetForTest` / `unsubscribeForTest`, `UndoController.resetForTest` / `unsubscribeForTest`, `PlaybackController.unsubscribeForTest`, `SelectionCoordinator.unsubscribeForTest` (public, incidentally), `Prefs.removeObsoleteKeysForTest` and four siblings, `RecentDocumentsManager.resetForTest` / `reloadForTest`, `PreviewElementManager.resetOverlaysForTest`, `MainFrame.clearStartupErrorsForTest`, `PreferencesDialog.resetInstrumentsForTesting`.

These exist because singletons hold static mutable state and MBassador subscriptions that production never tears down. Banning them outright leaves tests either leaking state into each other — which Test Independence forbids — or requires making the singletons injectable, which is a very large change.

**Proposed resolution, inside the philosophy rather than as an exception:** a class with `initialize()` and no way back has an **incomplete lifecycle contract**, tests or no tests. `Actions`, `Prefs`, and `UndoController` all initialize; none can be torn down. That is a gap in the contract, not a concession to testing. Rename them for what they do (`shutdown()`, `reset()`, `unsubscribe()`), document the lifecycle contract on the class, and they stop being test-only surface and become the other half of an API that was always missing one. §2.8 then applies with no carve-out, and the sweep improves the singletons instead of demolishing their tests.

* * *
## 7. Proposed document set — SUPERSEDED

> Superseded by `contract-driven-rollout.md` Phases 1–7. Do not execute from
> this table: D7 moved the language-neutral principles to the global
> `~/.claude/rules/development.md`, which this section predates. Kept as record.
| Document | Change |
|---|---|
| `.claude/rules/development.md` | **New Contracts section** — what a contract is, the three tiers, the rule that you write the contract before changing or testing an API that lacks one. Always-loaded context, so terse and normative. |
| `.claude/guides/contracts.md` | **New.** The worked form: preconditions, postconditions, `@throws`, boundary semantics, invariants, side effects, with real SongScribe examples. Loaded on demand. |
| `.claude/guides/testing-common.md` | **Rewritten** around contract-derived cases. The Correctness/Usefulness/Coverage triad collapses into: derive cases from the contract; a short trustworthiness list (can it fail, does the name name the contract case, no flakiness, no order dependence); coverage demoted to occasional sanity check with no place in routine review. `Testability Over Encapsulation` deleted. |
| `.claude/guides/testing-unit.md` | Keeps mechanics (fixtures, `StaffElementFactory`, FlatLaf, `ReflectionTestHelper`). Adds `@ParameterizedTest` / `@MethodSource` as the normal shape for equivalence classes and invariants. Demotes the singleton-mocking recipes from first resort. |
| `.claude/guides/testing-e2e.md` | Mechanics stay. Core Principle rewritten: e2e proves *wiring*, one test per path, never per case. |
| `.claude/guides/dialogs.md` | Gains the record-based validate/apply lifecycle (§5.2). |
| `.claude/rules/java.md` | Javadoc section extended to point at the contract rules; `{@value}` rule gains its role in §4.6. |
| `.claude/skills/check/` | Phase 3 (coverage) leaves the automatic path. The three test agents collapse into one contract-conformance axis. `reference/agents-tests.md` rewritten. |

**Note on paths:** `.agents` is a symlink to `.claude`; `.claude` is the real directory. Every `.agents/...` path in the guides resolves to the same file.

**Note on splitting the contract material:** the rule in `development.md` is in context for every session; the worked examples are not needed until you are writing one. Hence rule + guide rather than everything in the rule.

* * *
## 8. Open decisions — ANSWERED

> Every decision below is settled; the answers are in the comments and are
> carried into `contract-driven-rollout.md`'s decision table, which is
> authoritative. Nothing here is still open. Kept as record.
### D1. Disposition of the 7,186 existing tests
This is the difference between a documentation change and a multi-week project.

| Option | Consequence |
|---|---|
| **Docs only** | New guides govern new and modified tests; the existing suite stays and decays. Cheapest, but the suite keeps costing what it costs and continues to model the old regime to anyone reading it. |
| **Docs + the `ui/dialog` sweep** | Rewrite the guides, then apply them to `ui/dialog` first: write contracts, restructure the validate/apply seam, delete the wiring tests. Bounded, and it is the area named. **Recommended.** |
| **Docs + full package-by-package audit** | Every package gets contracts written, then its tests re-derived. Months, and better decided after seeing how the dialog sweep goes. |

{==**Decision:**==}{>>I don't care if it takes a week to properly document all non-trivial methods and rewrite the tests accordingly — and throw out the ones that don't fit in the new policy. There is enormous tech debt that is becoming a major drag on development and leading to repeated coding errors.

Come up with a plan on how to split up the work.<<}{id="c6" by="user" at="2026-08-11T16:57:51.229Z"}
### D2. E2E for dialogs
After dialog unit tests go, does each dialog get one e2e confirming open → edit → OK → model changed, or none at all?

{==**Decision:**==}{>>Dialog unit tests per se will go, but restructuring their code to front end/back end — where the back end is completely UI-independent, and thus unit-testable — will not go.

Otherwise, a separate set of dialog e2e tests along the line you suggested is fine. Those should only be necessary to run when the dialog is first created or a new feature is added, and only to confirm the wiring to the back end.<<}{id="c7" by="user" at="2026-08-11T17:00:52.537Z"}
### D3. Coverage and mutation tooling
Do `coverage.sh` and `mutation-test.sh` stay as tools invoked deliberately while leaving `check`'s automatic phases (recommended), or come out entirely?

{==**Decision:**==}{>>Yes. Only invoked deliberately.<<}{id="c8" by="user" at="2026-08-11T17:04:29.728Z"}
### D4. `StandardDialog` validation seam
Does `StandardDialog` grow a formal `ValidationResult`-returning hook so every dialog inherits the seam, or does each dialog implement it ad hoc? The base-class hook is the sounder option — otherwise the boundary is a convention that erodes — but it changes `BaseDialog` / `StandardDialog` (1,275 lines) and every dialog overriding `isValidData()`.

{==**Decision:**==}{>>Yes, explore if ValidationResult or something similar will work.<<}{id="c9" by="user" at="2026-08-11T17:04:53.774Z"}
### D5. The ~11 lifecycle hooks
Reconceived as proper singleton teardown per §6.3, or simply removed?

{==**Decision:**==}{>>I agree with 6.3.<<}{id="c10" by="user" at="2026-08-11T17:06:42.152Z"}
### D6. Deletion protocol
Deleting tests at this scale wants a bound. Proposed: per-package, on a branch, **contract written first** so there is something to check the deletion against — never delete before the contract exists.

{==**Decision:**==}{>>1. Write API contracts.
2. For each API that will be tested, write a detailed javadoc describing what the testing approach is.
3. Modify, rewrite or discard existing tests accordingly.<<}{id="c11" by="user" at="2026-08-11T17:08:37.456Z"}
