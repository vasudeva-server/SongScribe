# Contracts

The Javadoc form of a contract. Read when writing one. **How much contract a
method earns is decided by fan-in, not by this guide** — see *Contract depth
follows fan-in* in [design.md](/Users/aparajita/.claude/guides/design.md). Most methods here earn a name and
nothing else; this guide is for the ones that earn more.

## What belongs in a method contract

| Element | States | Javadoc form |
|---|---|---|
| Summary | what the method does, in the caller's vocabulary | first sentence |
| Preconditions | valid argument values and ranges, nullability, required receiver state | `@param`, prose |
| Postconditions | what is true of the return value and of the receiver afterwards | `@return`, prose |
| Boundary semantics | inclusive vs. exclusive, empty input, zero, negative, ties, equality | `@invariant` |
| Errors | every exception type and the exact condition that produces it | `@throws` |
| Result invariants | what holds of every result, whatever the input | `@invariant` |
| Side effects | mutation, messages posted, files written, threading requirement | `@effects` |
| Relationships | guard methods, inverses, round-trips, sibling methods that must agree | `{@link}` |

Few methods have all eight. A method with none is trivial and needs no contract.

**`@return` is not optional.** Any method whose return type is not `void` carries
the tag, whatever the summary sentence already says, because the tag is what the
IDE shows at the call site. Only a method with no doc comment at all may omit it.

**`@invariant` is singular and repeatable.** One clause per tag, the way `@param`
and `@throws` repeat, so adding an invariant is a one-line diff rather than a
rewrite of a paragraph, and so a reader can count the promises. `@effects`
carries the whole of a method's effects.

**Both tags are required on a contract a test is derived from**, because a clause
buried in prose is a clause the test author reads past. Elsewhere they are
adopted as contracts are touched; there is no retrofit pass.

**What does not belong:** how it does it. The algorithm, the data structure, the
iteration order unless the order is promised, the fact that it caches. A contract
that states those has given away the freedom to change them.

**Why the promise is what it is does belong.** "Runs at `HIGH_PRIORITY` so
lower-priority subscribers reading `canUndo()` see the step already pushed"
explains a promise a reader would otherwise take for arbitrary and delete. The
test: does the sentence constrain the implementation (out) or explain the
constraint (in)?

## The worked example

`NoteTypeMapping.ticks` — high fan-in arithmetic whose correctness nothing in the
type system can carry, so it earns a full contract and real tests.

```java
/**
 * Returns the {@code <duration>} tick count for the given {@link ElementType}
 * and dot count, as an exact integer.
 *
 * <p>The formula is {@code baseTicks × (2^(d+1) − 1) / 2^d} where {@code d}
 * is {@code dotCount} (0, 1, or 2). {@link #DIVISIONS} is chosen so that this
 * always produces an exact integer: the worst case is a double-dotted 32nd,
 * which yields {@code 1680 × 7 / 4 = 2940} ticks.
 *
 * <p>Do not call this for {@link ElementType#GRACE_QUAVER}; grace notes carry
 * no {@code <duration>}. Use {@link #hasDuration(ElementType)} to guard the call.
 *
 * @param type     the note/rest {@link ElementType} (not {@code GRACE_QUAVER})
 * @param dotCount number of augmentation dots (0, 1, or 2)
 * @return the exact integer tick count
 * @throws IllegalArgumentException if {@code type} has no tick mapping or
 *                                  {@code dotCount} is out of range
 * @throws ArithmeticException      if the result is not an exact integer
 *                                  (indicates {@link #DIVISIONS} is wrong)
 */
public static int ticks(ElementType type, int dotCount)
```

### What it yields as tests

Three cases, all of them properties rather than pinned outputs:

| Contract clause | Case |
|---|---|
| valid domain | every `ElementType` with a mapping × `dotCount` ∈ 0..`MAX_DOT_COUNT`, **enumerated** from the enum itself, not sampled |
| the dot formula | `ticks(t, 1) == ticks(t, 0) * 3 / 2` and `ticks(t, 2) == ticks(t, 0) * 7 / 4`, for every `t` |
| exactness under tuplets | the class invariant: every duration stays an exact integer after scaling by M/N for every ratio N in 2..7 |

**Both `@throws` clauses yield nothing.** `dotCount` out of range and
`GRACE_QUAVER` are guards, reached only by a caller that does not exist, and the
`ArithmeticException` clause promises something about a constant rather than
about any input. Deriving a case per branch here is how a three-case contract
turns into a nine-case test class. See *The testing floor* in
[design.md](/Users/aparajita/.claude/guides/design.md).

**Writing the table found a defect, which is the point of writing it.** The
contract says to guard with `hasDuration`, but `hasDuration` returns
`type != GRACE_QUAVER`, so it answers `true` for `SINGLE_BARLINE`, `BREATH_MARK`
and every other non-durational type, all of which make `ticks` throw. A caller
doing exactly what the contract instructs is not guarded. No current caller is
affected, which is why nobody has noticed. A contract the implementation could
violate is the only kind worth writing.

## Postconditions go through the public API

`UndoController.songDidChange` promises that afterwards `canUndo()` is true and
`canRedo()` is false, not that `undoStack` gained an element. The stack is
implementation; the guard methods are the promise.

If you cannot state a postcondition through the public API, you have found either
a missing query method or a promise that is not yours to make. Both are findings.
Neither is solved by widening a field.

## Every boundary gets its consequence

A boundary with only its value is half a clause. *"The stack retains at most
{@value #UNDO_STACK_MAX_DEPTH} steps"* says nothing about what happens when you
reach the number. *"…a push past that evicts the oldest, and if the evicted step
was the clean marker the document can no longer return to clean"* is the whole
promise, and it is the half that a caller has to write code against.

## What a contract leaves out

`forTypeToken(String typeToken, boolean isRest, boolean isGrace)` documents every
parameter and never states the return, so what `null` means lives only where a
call site cannot see it. It also never states that `isGrace` overrides the token
entirely, nor whether trimming is the caller's job — both are known only to the
tests and to the two call sites that happen to call `.trim()` first.

**When the test knows more than the contract, the contract is what is wrong.** A
promise discoverable only by reading a test is a promise no caller can find.

The signature is part of the contract: `forTypeToken(token, false, false)` is two
adjacent transposable booleans naming nothing. Same-typed adjacent parameters
take a parameter object or an enum, regardless of count.

## Class and package Javadoc

Tier 2 holds what would otherwise be repeated on several methods and eventually
contradict itself:

- **Object invariants** — what is always true of an instance between calls
- **Ownership and lifecycle** — who creates it, who tears it down, what survives
- **Threading** — `ViewScale` states *EDT-only by contract; no locking* once, on
  the class
- **Invariants the methods depend on** — `ZOOM_LEVEL_PERCENTS` is documented
  *"in ascending order"* on the field; `nextLevelAbove` and `nextLevelBelow` are
  correct only under it and neither restates it

Package invariants go in `package-info.java`, which otherwise carries only
`@NullMarked` here.

## When it belongs in `docs/` instead

Tier 3, a prose document, when the rule **spans subsystems** and no single class
owns it. *"Creating a tuplet preserves the absolute playback duration of the
enclosed passage"* is not a fact about one method. Live examples: `docs/undo.md`,
`docs/line-layout.md`, `docs/key-signatures.md`.

Method Javadoc then **links** to the document. A paraphrase is a second copy, and
the second copy is the one that goes stale.

## `@Nullable` returns

`@Nullable` says only *this may be absent*. It never says what absence means, and
absence is what the caller has to branch on.

```java
// Useless — restates the annotation
/** @return the element type, or null. */

// Contract — names the condition, so the caller knows which branch they are in
/** @return the matching type, or {@code null} if the token is not recognised */
```

The condition goes in the `@return` tag, not only in the summary above it. For a
`@Nullable` **parameter**, state what passing null selects:
*"around `viewAnchorPoint` (or the viewport center when null)"*.

Before writing either, check that the nullable should exist at all — see
*`@Nullable` is a design flag* in [design.md](/Users/aparajita/.claude/guides/design.md).

## Constants and the contract

> A constant is part of the contract **if a contract's Javadoc names it**, via
> `{@value #X}` or `{@link Class#X}`. Its visibility then follows the visibility
> of the contract citing it. If no contract names it, it is implementation.

Never redeclare a constant's literal in test code. If a test needs the value, the
question is whether the contract should name it, never whether the field should
be widened. Visibility justified by a test is a finding, not a design.

For `{@value}` mechanics see **Javadoc References to Constants** in
`.claude/rules/java.md`.

## Writing order

The contract is written before the code it describes, but after the design
decisions that determine whether the method should exist and what shape it has.
It is step 4 of the order in [design.md](/Users/aparajita/.claude/guides/design.md), not step 1.

When the code already exists, write the contract from the domain, never from the
body. The body is the strongest available temptation and the one thing that
cannot say what the code *should* promise.

Changing an **existing** contract is a visible decision, because callers rely on
it. State it, get it agreed, make it its own change. Never adjust one mid-fix to
turn a test green.

For contracts encoding a musical judgment rather than a mechanical fact —
tuplets, beaming, ties, melisma placement, key signatures — propose the promise
and get it confirmed before writing it. A confident, plausible, wrong contract is
worse than none, because everything downstream derives from it.
