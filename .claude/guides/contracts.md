# Contracts

What a Javadoc may state. Read before you write or change a Javadoc.

**A Javadoc states only what the code cannot say. It never describes what the
body does.** A reader can read the body. A clause that restates the body adds
nothing while it is correct. When the body changes, nothing makes the clause
change with it, and the reader trusts the wrong clause instead of the body.

The test for each clause: **can a reader learn this from the body of this
method?** If yes, delete the clause.

**Err on the side of conciseness.** The less a Javadoc says about the
implementation, the better. When you are not sure whether a clause restates the
body, delete it. A clause that stays states the reason or the promise in as few
words as it can, and never the mechanism that keeps it: no steps, no list of
fields or branches, no walk through what the method calls.

## The five clause types

A Javadoc holds only these clauses. A method with none of them has no Javadoc.

1. **A precondition that the code does not check.** Interior code relies on it
   and does not guard it (see *A stated precondition is the caller's
   obligation*). Write it in `@param` or in prose: *"`ticks` is not negative"*.
2. **What a null return or a null parameter means.** See *`@Nullable`* below.
3. **Why a constraint exists, and what breaks somewhere else if it changes.**
   *"Stays public because the undo replayer and the MusicXML header reader drive
   it from outside the editing UI."* *"Runs at `HIGH_PRIORITY` so that
   lower-priority subscribers that read `canUndo()` see the step already
   pushed."* Without the sentence, a reader takes the constraint for arbitrary
   and removes it. A sentence that explains a constraint stays; a sentence that
   describes the implementation goes.
4. **A class-level fact**, on the class Javadoc:
   - threading — `ViewScale` states *EDT-only by contract; no locking* once, on
     the class;
   - lifecycle obligations — who must dispose of the object, and what survives
     what;
   - an invariant that several methods depend on — `ZOOM_LEVEL_PERCENTS` is
     *"in ascending order"*; `nextLevelAbove` and `nextLevelBelow` are correct
     only under it, and neither restates it.

   What holds the object, and what the object holds, is structure, and the
   Javadoc does not state it. An invariant that spans the classes of a package
   goes in `package-info.java`.
5. **A promise that the code does not show**, for example a decision about
   music: *"creating a tuplet keeps the playback duration of the passage."* The
   body can satisfy the promise without stating it, and a later change can
   break it without a sign.

A type's Javadoc also states the purpose of the type.

A clause of type 3 or 5 can be the first sentence. No other first sentence is
written: a sentence that says what the method does restates the body.

Outside a full contract, `@throws`, `@effects` and `@invariant` are never
written. A `@return` appears only to state what a null return means. A `@param`
appears only to carry a clause of type 1 or 2.

## Full contracts

**A full contract stays only where a test is derived from it**, which means a
test of one of the three kinds that *The testing floor* in
[design.md](~/.claude/guides/design.md) allows. A test derives from a method when
it calls the method itself, or names the method as what it asserts — a sweep test
that drives `Hairpin.outcomeFor` through edits derives from it. A test that only
reaches the method on the way to something else does not. The full contract states every
clause that a test case comes from, so that the test can be written again
without the body. The five clause types do not limit it.

A full contract uses the standard tags and two custom tags: `@invariant` for a
property of every result, and `@effects` for a change to state outside the
return value. Each custom tag holds one clause and repeats, like `@throws`.

**The tell of a real full contract is that the implementation could break
it.** If you cannot imagine the code breaking the promise, you described the
code.

### The worked example

`Dots.augment` is arithmetic whose correctness the type system cannot carry,
so it has a full contract and tests derived from it.

```java
/**
 * Returns {@code ticks} lengthened by these dots, as an exact integer: each dot adds half of
 * what the previous one added, so the result is {@code ticks × (2^(d+1) − 1) / 2^d} where
 * {@code d} is {@link #count()}.
 *
 * <p>At both tick bases in use — {@link songscribe.midi.MidiSequenceBuilder#PPQ} in the
 * model and {@link songscribe.io.musicxml.NoteTypeMapping#DIVISIONS} in MusicXML — every
 * {@link NoteValue} augments exactly, so the exception is reachable only by a caller
 * supplying a base of its own.
 *
 * @param ticks the undotted duration
 * @return the dotted duration, in the same ticks
 * @invariant {@code augment(ticks) >= ticks}, with equality exactly for {@link #NONE}
 */
public int augment(int ticks)
```

Each clause yields a case, and each case is a property, not a pinned output:

| Contract clause | Case |
|---|---|
| valid domain | every `NoteValue` × `Dots` constant, **enumerated** from both enums, not sampled |
| the dot formula | `ONE.augment(base) == base * 3 / 2` and `TWO.augment(base) == base * 7 / 4`, for every `base` |
| the invariant | `augment(ticks) >= ticks` for every case, with equality only for `NONE` |

**A contract that names two tick bases is a contract about both.** A table
that exercises only the model's base leaves the MusicXML base on the promise
alone.

### Postconditions go through the public API

A full contract on `UndoController.songDidChange` would promise that afterwards
`canUndo()` is true and `canRedo()` is false, not that `undoStack` gained an
element. The stack is implementation; the guard methods are the promise.

If you cannot state a postcondition through the public API, you found either a
missing query method or a promise that is not yours to make. Both are findings.
Neither is solved by widening a field.

### When the test knows more than the contract, the contract is wrong

A promise that only a test states is a promise that no test author can derive
again. Move it into the full contract.

## Every boundary gets its consequence

This applies to a full contract and to a clause of type 4 or 5. A boundary with
only its value is half a clause. *"The stack retains at most
{@value #UNDO_STACK_MAX_DEPTH} steps"* says nothing about what happens at that
number. *"…a push past that evicts the oldest, and if the evicted step was the
clean marker the document can no longer return to clean"* is the whole promise,
and it is the half that a caller writes code against.

## A stated precondition is the caller's obligation

A clause of type 1 makes the caller responsible for the precondition. A caller
that violates it has the bug, and the result is undefined.

**Guards belong at the point of entry and nowhere else.** The entry points are
the two boundaries in *Boundaries convert, they do not check* in
[design.md](~/.claude/guides/design.md): file and network input, and the human
at the UI. Those convert an untrusted value into a domain value that satisfies
the precondition. Every layer below them relies on the precondition and checks
nothing again.

Interior code therefore never defends against a state that a precondition
forbids:

- The check reads as evidence that the state can occur, so the next reader
  cannot tell which belief is current — the precondition's, or the guard's.
- A guard that returns early or substitutes a default converts a caller's bug
  into a wrong answer with nothing that marks it wrong.
- The precondition now lives in two places.

**The tell** is a check whose condition a precondition already forbids, in a
method that no untrusted value reaches. Delete it, and make sure the Javadoc
states the precondition.

This does not license removing a check at a boundary. It also does not license
removing a check whose condition a caller can legitimately produce. That
condition is part of the domain, and the body handles it in the open.

## Signatures

The signature carries what the Javadoc does not. `forTypeToken(token, false,
false)` is two adjacent booleans that a call site can transpose, and that name
nothing. Same-typed adjacent parameters take a parameter object or an enum,
whatever the count. Whether the caller must trim `token` first is a
precondition of type 1, and the Javadoc states it.

## `@Nullable`

`@Nullable` says only *this may be absent*. It never says what absence means,
and absence is what the caller branches on.

```java
// Restates the annotation — not written
/** @return the element type, or null. */

// States the condition, so the caller knows which branch it is in
/** @return the matching type, or {@code null} if the token is not recognised */
```

For a `@Nullable` **parameter**, state what passing null selects: *"around
`viewAnchorPoint` (or the viewport center when null)"*.

Before you write either, check that the nullable should exist at all — see
*`@Nullable` is a design flag* in [design.md](~/.claude/guides/design.md).

## What does not go in `docs/`

**A promise is never a document.** However many subsystems a promise touches, it
is a promise about some method, class or lifetime, and it belongs on that member
in Javadoc. *"Creating a tuplet preserves the absolute playback duration of the
enclosed passage"* reads like a cross-cutting rule, and it is not one. It is a
clause of type 5 on the method that creates a tuplet. A statement of it in prose
somewhere else is a second copy that nothing keeps correct.

`docs/` is for the shape a reader cannot recover from any one class: what the
pieces are, how work flows between them, and which piece decides what. See the
`docs/` paragraph in [CLAUDE.md](../../CLAUDE.md). Where a doc carries the
shape, a Javadoc **links** to it and does not paraphrase it.

## Constants and the contract

> A constant is part of the contract **if a Javadoc names it**, via
> `{@value #X}` or `{@link Class#X}`. Its visibility then follows the visibility
> of the member whose Javadoc names it. If no Javadoc names it, it is
> implementation.

Never declare a constant's literal again in test code. If a test needs the
value, the question is whether the full contract should name it, never whether
the field should be widened. Visibility justified by a test is a finding, not a
design.

For `{@value}` mechanics see **Javadoc References to Constants** in
`.claude/rules/java.md`.

## Writing order

A clause is written from the domain, never from the body. The body cannot say
what the code *should* promise.

A full contract is written before the code it describes, but after the design
decisions that settle whether the method exists and what shape it has. It is
step 4 of the order in [design.md](~/.claude/guides/design.md), not step 1.

A change to an **existing** clause of type 1, 2, 4 or 5, or to a full contract,
is a visible decision, because callers rely on it. State it, get it agreed, and
make it its own change. Never adjust one mid-fix to turn a test green.

For a clause of type 5 or a full contract that encodes a judgment about music —
tuplets, beaming, ties, melisma placement, key changes — propose the promise
and get it confirmed before you write it. A confident, plausible, wrong promise
is worse than none, because everything downstream derives from it.
