# Findings — step 5, pass 6b, _Accidentals_

Paths are under `src/main/java/songscribe/dom/`.

## The counts

**Write: 0. Update: 2. Remove: 0.**

Every public method in the target already carries a contract in the form `.claude/guides/contracts.md` states, with `@return` on every non-void method. The methods steps 2–4 added were written with theirs. What is left is one restatement to cut and one summary to shorten.

## What it found

| Class | Write / Update / Remove | What it promises |
|---|---|---|
| `Accidental` | 0 / 0 / 0 | Five alterations, each with its semitone offset and glyph. `semitones()` and `glyph()` state their return. |
| `AccidentalState` | 0 / 0 / 0 | The class Javadoc carries the lifecycle invariant: a note on a line of a song holds a `ChainedAccidental` except during load and inside the one `applyChange` that set its intent. The two records carry `@param`. |
| `ChainedAccidental` | 0 / 1 / 0 | The class Javadoc states the five states and their conditions, which is the contract of `ChainType.decide`; `decide`'s summary (`:79–84`) restates the same rule in its own words. Update: `decide` cites the class Javadoc and keeps its `@param`, `@return` and `@invariant`. |
| `AccidentalChain` | 0 / 1 / 0 | `walk(Song, ChainEdit)` (four callers) and `walkSong` carry `@invariant`s the implementation could violate. The class Javadoc's *The policy* section (`:40–66`) restates `ChainedAccidental`'s class Javadoc a second time in code. Update: cut it, cite `ChainedAccidental` and `docs/accidentals.md`. The walk's algorithm is implementation prose and sits on the private `Walk` class, not on the public class. Private `Walk` members keep names only; `inForceBeforeStart` and `existingAtOrAfter` keep their one-line summaries. |
| `ChainEdit` | 0 / 0 / 0 | Each factory's `@return` states what its edit covers, vacates and reaches — the meaning the walk depends on. `of` states which mutations map and which answer null. `Reach`'s two constants state the stopping rule. |
| `Cursor` | 0 / 0 / 0 | Song order, `plus`, `next`. Written at step 2. |
| `ProjectedEdit` | 0 / 0 / 0 | Two factories with ranges stated; `elements()` unmodifiable; `walk()` cites `AccidentalChain.walk`. |
| `AccidentalScanView` | 0 / 0 / 0 | Every method states its index domain; `ownKey` names the null condition. Package-private since step 2. |

Written outside the target at step 4, each with its contract: `Line.keyBefore`, `Key.alterationAt`, `ElementType.isTransparentInChain`, `AccidentalChain.Restamp.stamp`.

## What needs deciding

### 1. The policy has two homes, not three

`docs/accidentals.md` states the concepts — what is in force, what intervenes, the five states — as the subsystem's shape. `ChainedAccidental`'s class Javadoc states the same five conditions as the contract of `ChainType.decide`, which `contracts.md` puts on the member. `AccidentalChain`'s class Javadoc (`:40–66`) is the third statement.

<span id="rd-c1">**Option A.** Cut _The policy_ from `AccidentalChain`</span>; its first paragraph cites `ChainedAccidental` for the states and `docs/accidentals.md` for the concepts. `ChainType.decide`'s summary becomes one sentence citing the class Javadoc.

- Pro: one contract statement in code, one concept statement in `docs/`.
- Con: none real.

**Option B.** Leave all three.

- Con: three statements of one rule, and nothing keeps them equal.

### 2. Domain promises

None to checkpoint. Every promise in the target is either mechanical or is already stated in `docs/accidentals.md`, which pass 6 and the user settled; the methods added in this pass restate those promises for a new signature and add no notation judgment. If the review finds a promise it reads as a judgment, it is named here and checkpointed before it is written.

### 3. Signature rules: nothing in the target

`ChainType.decide`'s three adjacent `Accidental`s were decided at the step 2 gate (item 7, keep). Outside the target, `layout/AccidentalFitGate.fits` (`:114–119`) takes five parameters; the rule asks for a `record`. It is row 18's, and goes to the record's _Findings raised_.

---
roughdraft: "1.0"
comments:
  rd-c1:
    body: ok
    by: user
    at: "2026-09-22T01:38:23.763Z"
