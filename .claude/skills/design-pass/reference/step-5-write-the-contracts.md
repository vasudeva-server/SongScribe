## Step 5: Write the contracts

**Inline. Never delegated.** A subagent guessing at a music-notation promise
produces confident, plausible, wrong Javadoc — worse than none, because
everything downstream then tests against it.

**A Javadoc states only what the code cannot say.** `guides/conventions/contracts.md`
lists the five clause types. A method with none of them has no Javadoc. A full
contract exists only on a method that a test derives from.

`plans/design-pass-<pass>/findings-5.md` is **a summary of the work, not a draft
of it.** No Javadoc text goes in it; the Javadoc is written after the gate,
against what the gate settled.

**Three counts open it**, in methods:

- **write** — a clause of the five types, or a full contract that a test
  derives from, is missing;
- **update** — a clause or a full contract exists, but it is wrong or not in the
  form `contracts.md` states;
- **remove** — a Javadoc holds a clause that is none of the five types, or a
  full contract that no test derives from.

Then **one row per class**: its three counts, and a line on what the class
promises that makes them come out that way. A class whose count is `0 / 0 / 0` is
still a row — that it needs nothing is the finding.

Then *What needs deciding*, holding two kinds of item:

- **Signature changes the rules below force** — a `record` replacing transposable
  parameters, an enum replacing a mode-selecting `boolean`, a rename. These are
  code changes, so they are proposed here and not performed.
- **Every domain promise you cannot settle from the code**, in the checkpoint
  form `reference/classification.md` specifies. Nothing mechanical is stated as a
  question; a promise about notation you guessed at reads exactly like one you
  knew, which is the whole reason these are asked.

**Then the review gate: open, review, resolve.** No Javadoc is written and no
signature changed before it. Then write, in this order: the method clauses and
full contracts as the reviewer settled them, the class Javadoc — purpose,
threading, lifecycle obligations, invariants several of its methods depend on —
then `package-info.java` for invariants spanning the classes. **A rule that
no single type can enforce goes to `guides/subsystems/`;** if a guide already
states it, the Javadoc does not restate it and does not name the guide.

Two things that are not optional:

- **The signature rules apply as you go** — more than four parameters or two
  transposable same-typed parameters take a `record`; a mode-selecting `boolean`
  takes an enum; a misdescribing name gets renamed via `jet_brains_rename`. The
  name is the only statement of what a method does.
- **The tell of a real full contract or a clause of type 5 is that the
  implementation could in principle violate it.** If you cannot imagine the code
  breaking the promise, you have described the code.

**Snapshot per class or per coherent group.**
