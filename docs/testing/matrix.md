# Testing Matrix (disposable scaffolding)

> **Status: scaffolding.** This file drives a production-code-first test audit.
> It is **not** a living document — once the audit produces rewritten tests, the
> tests themselves carry the contract forward and this file is archived/deleted.
> The one durable output is the **rubric** below, which will be promoted into
> `.agents/guides/testing-common.md` at the end.

## Method

Audit proceeds **from production code, not from existing tests**. For each
production class in scope we enumerate its testable behaviors, classify each as
`unit` / `e2e` / `none` using the rubric, then check whether an adequate test
already exists (unit *or* e2e — both levels checked per behavior). No test code
is changed until the full matrix exists and a remediation order is approved.

E2E coverage is assessed by **reading e2e test source only**; the e2e suite is
never run during the audit.

---

## Rubric: unit vs. e2e vs. none

Derived from `testing-common.md`, `testing-unit.md`, `testing-e2e.md`. This is
the consistency anchor — every session classifies behaviors by these rules, not
by ad-hoc judgment.

> **Paramount: the Test Quality Principles in `testing-common.md` override
> everything else here.** The unit/e2e/none classification only decides *where*
> a behavior is tested; the Quality Principles decide whether a test is worth
> having at all. A test that cannot fail, asserts against a mock, has a
> name/behavior mismatch, or gives false confidence is a defect *regardless* of
> being at the correct level. When auditing each behavior, apply the Quality
> Principles (Correctness → Usefulness → Coverage) first; the level rubric
> second.

### Default: unit

Prefer a unit test. Unit tests are faster, run without approval, and localize
failures. A behavior is unit-testable if its risk is **logic, computation,
state, data transformation, or model mutation** — even when it requires:

- mocking the `MainFrame.getInstance()` singleton chain (see `testing-unit.md`),
- widening a member to package-private to test it directly (see *Testability
  Over Encapsulation* in `testing-common.md`), or
- constructing collaborators via `ReflectionTestHelper`.

Examples that are **unit**: format migration, serialization round-trips, layout
geometry/stacking math, MIDI generation, action enablement logic, selection
state machines, mutation records, derived model state, `@Nullable` contracts.

### Escalate to e2e ONLY when the risk *is* the integration

Use an e2e test only when the behavior **genuinely requires the real Swing
pipeline** and cannot be meaningfully verified with collaborators mocked:

- real mouse/keyboard event dispatch (click, drag, shift-click, type),
- cross-component integration where the bug lives in the wiring (action →
  model mutation → layout invalidation → repaint → selection reflection),
- behavior only observable after a real layout/repaint cycle,
- application lifecycle (boot, shutdown, file open/save through the UI).

If everything that matters can be asserted with the singleton mocked, it is
**not** an e2e case — putting it in e2e is the wrong level.

### Classify as none (no test warranted)

- trivial getters/setters with no logic,
- pure data holders (most `message.mutation` / `message.command` /
  `message.notification` records, unless they carry derivation logic),
- pure display/layout wiring with no branching logic (most dialogs, menus),
- framework behavior that cannot regress in our code,
- pure rendering to a `Graphics2D` with no computed geometry to assert
  (the geometry, if any, is unit-tested upstream).

### Verdict vocabulary (per existing test found)

- **adequate** — a test exists at the right level and can actually fail.
- **wrong-level** — covered, but as e2e what should be unit (or vice versa).
- **inadequate** — exists but can't fail / name-mismatch / asserts a mock /
  weak assertion (see *Correctness* + *Usefulness* in `testing-common.md`).
- **missing** — behavior warrants a test (unit or e2e) and none exists.
- **redundant** — duplicate coverage of a behavior already adequately tested.

---

## Existing test inventory (baseline)

- Unit: **1267** `@Test` methods across ~132 files (mirrors source packages).
- E2E: **79** `@Test` methods across 7 files in `songscribe/e2e/`
  (`ElementInsertionTest` 27, `SelectionTest` 24, `NoteConnectionTest` 14,
  `DynamicsMarkingTest` 5, `DialogsTest` 5, `ShutdownTest` 3, `E2ETest` 1).

---

## Audit progress

Risk-ordered. Data-loss / core-logic packages first; cosmetic rendering last.

| # | Package scope | Prod classes | Status |
|---|---------------|--------------|--------|
| 1 | `dom` | 39 | not yet audited |
| 2 | `io` | 16 | not yet audited |
| 3 | `layout` | 39 | not yet audited |
| 4 | `midi` + `converter` + `util` + `smufl` + `prefs` + `font` + `export` + `uiconverter` | ~58 | not yet audited |
| 5 | `ui/action` | 62 | not yet audited |
| 6 | `ui/selection` + `ui/edit` + `ui/adjustment` + `ui/clipboard` | 15 | not yet audited |
| 7 | `ui/component` | 65 | not yet audited |
| 8 | `message` (mutation/command/notification + core) | 86 | not yet audited |
| 9 | `ui/renderer` | 30 | not yet audited |
| 10 | `ui/dialog` | 53 | not yet audited |
| 11 | `ui/menu` + `ui/playback` + `ui/platform` + top-level `ui` | ~38 | not yet audited |
| 12 | `lifecycle` + `error` + top-level (`SongScribe`, `FileExtensions`) | 5 | not yet audited |
| 13 | e2e reconciliation (whole-suite redundancy / orphans) | — | not yet audited |

---

## Findings

> One section per audited package, appended as sessions complete. Schema per row:
> **class · behavior · required level · existing test · verdict · action**

<!-- Session findings appended below this line -->
