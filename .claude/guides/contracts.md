# Contracts

The Java form of the **Contracts** and **Contract-Driven Testing** sections in
`~/.claude/rules/development.md`. Those hold the principles and are always in
context; this guide is how they are written here, and is read when you are
writing one.

The starting point: 24 files in `src/main` carry an `@throws` tag at all. Most of
this codebase has no stated contract yet, so most of the time you are writing the
first one rather than editing an existing one.

## What belongs in a method contract

| Element | States | Javadoc form |
|---|---|---|
| Summary | what the method does, in the caller's vocabulary | first sentence |
| Preconditions | valid argument values and ranges, nullability, required receiver state | `@param`, prose |
| Postconditions | what is true of the return value and of the receiver afterwards | `@return`, prose |
| Boundary semantics | inclusive vs. exclusive, empty input, zero, negative, ties, equality | prose |
| Errors | every exception type and the exact condition that produces it | `@throws` |
| Result invariants | what holds of every result, whatever the input | prose |
| Side effects | mutation, messages posted, files written, threading requirement | prose |
| Relationships | guard methods, inverses, round-trips, sibling methods that must agree | `{@link}` |

Not every method has all eight. A method with none of them is trivial and needs
no contract — an accessor returning a field, a one-line delegation.

**`@return` is not one of the optional ones.** Any method whose return type is
not `void` carries an `@return` tag, whatever else its contract does or does not
have, and whatever the summary sentence already says. The one method that may
omit it is the one that has no doc comment at all — the trivial accessor above.
A contract that opens *"Returns the Edit-menu label for Undo…"* and then never
tags the return has stated the promise where the call site does not show it; say
it in the tag and let the body carry the rest.

**What does not belong:** how it does it. The algorithm, the data structure, the
iteration order (unless the order is promised), the fact that it caches. A caller
who relies on any of that is relying on something you did not promise, and a
contract that states it has given away the freedom to change it.

The first sentence is what appears in every summary table and every IDE tooltip.
It states the whole promise in one line where the promise fits in one line.

## The worked example

`NoteTypeMapping.ticks` — the MusicXML tick count for a note value and dot count.
Its contract as it stands:

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

### The tests it yields

Deriving these without opening the method body is not a flourish of the example —
it is the rule the test author works under. The contract, the signature, and the
declaring class's public API are all they may derive a case from, so **a promise
you leave out of the contract is a case nobody can write.** See *Write from the
contract, not from the code* in
[testing-common.md](./testing-common.md#write-from-the-contract-not-from-the-code).

From the text above, then:

| Contract clause | Case |
|---|---|
| valid domain | every `ElementType` with a tick mapping × `dotCount` ∈ 0..`MAX_DOT_COUNT` — **enumerated**, not sampled |
| the dot formula | `ticks(t, 1) == ticks(t, 0) * 3 / 2` and `ticks(t, 2) == ticks(t, 0) * 7 / 4`, for every `t` |
| "as an exact integer" | no input in the valid domain throws `ArithmeticException` |
| the stated worst case | `ticks(DEMI_SEMIQUAVER, 2) == 2940` |
| `dotCount` lower boundary | `0` valid; `-1` throws `IllegalArgumentException` |
| `dotCount` upper boundary | `MAX_DOT_COUNT` valid; `MAX_DOT_COUNT + 1` throws `IllegalArgumentException` |
| "no tick mapping" | `GRACE_QUAVER` throws `IllegalArgumentException` |
| relationship to `hasDuration` | `ticks(t, d)` throws exactly when `!hasDuration(t)` |
| `@throws ArithmeticException` | **no test** — see below |

Three things this table demonstrates.

**Enumerate the finite domain.** The valid `type` domain is a subset of an enum
and `dotCount` runs 0..2. That is one `@ParameterizedTest` over a `@MethodSource`
of pairs, and it costs the same to write as picking two examples. Picking two is
what leaves the third one broken.

**Not every clause yields a test.** The `ArithmeticException` clause promises
something about a *constant* being wrong, and the contract's own text says no
valid input can reach it. There is no way to arrange it through the public API and
no test should try. A branch that the contract deliberately makes unreachable is
not a coverage hole — it is a guard, and reporting it as an untested line is the
coverage reflex the regime exists to remove.

**Writing the table found a defect.** The `hasDuration` row is derived straight
from *"Use `{@link #hasDuration(ElementType)}` to guard the call"*, and it is
false as written: `hasDuration` returns `type != GRACE_QUAVER`, so it answers
`true` for `SINGLE_BARLINE`, `BREATH_MARK` and every other non-durational
`ElementType`, while `ticks` throws `IllegalArgumentException` for all of them. A
caller who does exactly what the contract instructs is not guarded. No current
caller is affected — `NoteBuilder` is the only one and it passes note, rest and
grace types only — which is why nobody has noticed. This is what the global rules
mean by *the tell of a real contract is that the implementation could violate it*:
the promise reaches past the code, and writing the case down is what shows it.

### The invariant that is not a table

The class Javadoc adds a promise the method signature cannot express: `DIVISIONS`
must keep every duration an exact integer *after* a tuplet scales it by M/N, for
every ratio N in 2..7. That is not an input/output pair; it is a property over the
whole domain, and it is tested as one — `NoteTypeMappingTest`
`testTicksScaleExactlyForEveryTupletRatioAndDotCount` loops every type × dot count
× ratio and asserts exactness. One test, the entire promise.

## A contract that stops too early

Same file, `forTypeToken`:

```java
/**
 * Returns the {@link ElementType} for the given MusicXML {@code <type>} token
 * and note-shape flags, or {@code null} if the token is not recognised.
 *
 * @param typeToken the MusicXML {@code <type>} text content (e.g. {@code "quarter"})
 * @param isRest    {@code true} if a {@code <rest/>} child was present in the {@code <note>}
 * @param isGrace   {@code true} if a {@code <grace>} child was present in the {@code <note>}
 */
@Nullable
public static ElementType forTypeToken(String typeToken, boolean isRest, boolean isGrace)
```

It gets the hard part right — it says what `null` *means*. What it never says:

- **The return, in the tag.** Every parameter has an `@param`; the result has
  nothing. What `null` means is stated in the body only, which is the one place a
  caller reading the signature at a call site does not see it.
- **Precedence.** When `isGrace` is true the token is ignored entirely and the
  result is `GRACE_QUAVER`, recognised token or not. The contract does not
  mention it. `NoteTypeMappingTest` does, in an assertion description: *"grace
  flag forces GRACE_QUAVER irrespective of the `<type>` token"*.
- **Whether the token is trimmed.** Both production call sites call `.trim()`
  first. Neither the contract nor the parameter doc says whether that is the
  caller's job, so the third call site will guess.

**When the test knows more than the contract, the contract is the thing that is
wrong.** A promise discovered only by reading a test is a promise no caller can
find. Move it into the Javadoc; the test then asserts a stated case instead of
documenting an unstated one.

**The signature is part of the contract too.** `forTypeToken(token, false, false)`
is what a real call site reads like: two adjacent booleans, transposable in
silence, naming nothing. See *Signature quality is contract quality* in the global
rules — same-typed adjacent parameters take a parameter object or an enum,
regardless of the count.

## Class and package Javadoc

Tier 2 holds what would otherwise be repeated on several methods and eventually
contradict itself:

- **Object invariants** — what is always true of an instance between calls.
- **Ownership and lifecycle** — who creates it, who tears it down, what
  `initialize()` establishes and what survives teardown.
- **Threading** — `ViewScale` and `ZoomController` both state *EDT-only by
  contract; no locking is performed*, once, on the class.
- **Invariants a member's methods depend on** — `ZoomController` documents
  `ZOOM_LEVEL_PERCENTS` as *"in ascending order"* on the field. `nextLevelAbove`
  and `nextLevelBelow` are both correct only under that invariant, and neither
  restates it.

Package-level invariants go in `package-info.java`, whose Javadoc is otherwise
empty in this codebase — every one of them currently carries only `@NullMarked`
(the exception is `hit/package-info.java`, which documents its package's role).
Adding a package invariant means adding the first real prose to that file.

## When it belongs in `docs/` instead

Tier 3, a prose document, when the rule **spans subsystems** — when no single
class can state it because no single class owns it, and enforcing it is a
collaboration. *"Creating a tuplet preserves the absolute playback duration of the
enclosed passage"* is not a fact about one method.

The live examples: `docs/undo.md`, `docs/line-layout.md`,
`docs/musicxml-object-model.md`, `docs/span-invalidation.md`.

Method Javadoc then **links** to the document rather than paraphrasing it. A
paraphrase is a second copy, and the second copy is the one that goes stale.

## `@Nullable` returns

`Optional` is banned for fields and parameters and avoided for returns
(`null-handling.md`), so `@Nullable` is the whole vocabulary — and it says only
*this may be absent*. It never says what absence means, and absence is exactly
what the caller has to write code for.

```java
// Useless — restates the annotation
/** @return the element type, or null. */

// Contract — names the condition, so the caller knows which branch they are in
/**
 * Returns the {@link ElementType} for the given MusicXML {@code <type>} token.
 *
 * @return the matching type, or {@code null} if the token is not recognised
 */
```

The condition belongs in the `@return` tag itself, not only in the summary
sentence above it: a caller deciding whether they need a null check is reading
the tag.

The same applies to a `@Nullable` **parameter**: state what passing null selects.
`ZoomController.zoomByMagnification(double, @Nullable Point)` documents it —
*"around `viewAnchorPoint` (or the viewport center when null)"* — so null is a
documented choice rather than a hole.

## Constants and the contract

The mechanical test, which decides both visibility and whether a test may name
the constant:

> A constant is part of the contract **if a contract's Javadoc names it** — via
> `{@value #X}` or `{@link Class#X}`. Then its visibility follows the visibility
> of the contract that cites it. If no contract names it, it is implementation,
> and a test that needs it is testing implementation.

Never redeclare a constant's literal in test code. That is the one rule worth
keeping from the old *Testability Over Encapsulation* section: if a test needs the
value, the question is whether the contract should name it — not whether the field
should be widened.

For `{@value}` mechanics — which constants it is legal on, and when to use
`{@link}` instead — see **Javadoc References to Constants** in
`.claude/rules/java.md`. Do not restate them here.

### Two live cases

`NoteTypeMapping.MAX_DOT_COUNT` is package-private. `ticks`'s public contract
constrains its argument to it but spells the bound in prose — *"number of
augmentation dots (0, 1, or 2)"* — and two test files enumerate `0..MAX_DOT_COUNT`.
Every reference outside the declaring class is a test, which is what test-only
surface looks like from the outside. It is not: the bound genuinely is part of a
public promise. The contract should name it (`0–{@value #MAX_DOT_COUNT}`), the
constant should be public because the contract citing it is, and the tests'
enumeration becomes what it always should have been — reading the contract.

`ZoomController.WHEEL_ZOOM_FACTOR_PER_NOTCH` is the same shape with the opposite
label. Its Javadoc reads *"Package-private so `ZoomControllerTest` can derive
sub-step rotations without duplicating the literal."* Visibility justified by a
test is the finding, stated in the source. The question the comment skips is
whether `zoomByWheel` promises anything quantitative about the gesture — it
promises direction only, while an implementation comment describes a real promise
(*"the gesture is never dead"*) the contract never makes. Decide the promise
first; the constant's visibility follows from it.

## Writing order

1. **Contract first.** Before the implementation, and before the test.
2. **Then the code**, written to satisfy it. Code written against a stated promise
   is already being checked against that promise.
3. **Then the tests**, derived from the contract's clauses — the table above is
   the shape.

Changing an **existing** contract is a visible decision, because callers rely on
it. State it, get it agreed, and make it its own change. Never adjust one
mid-fix to get a test green — see *A failing test means one of three things* in
the global rules.

For contracts that encode a musical judgment rather than a mechanical fact —
tuplets, beaming, ties, melisma placement, key signatures — propose the promise
and get it confirmed before writing it. A confident, plausible, wrong contract is
worse than none, because every test downstream is then derived from it.
