## Step 5: Write the contracts

**Inline. Never delegated.** A subagent guessing at a music-notation promise
produces confident, plausible, wrong Javadoc — worse than none, because
everything downstream then tests against it.

**Depth follows fan-in.** A method nothing outside its class calls gets an
accurate name and no contract. Do not write a contract to be thorough; write it
where callers rely on the promise.

`plans/design-pass-<pass>/findings-5.md` is **a summary of the work, not a draft
of it.** No Javadoc text goes in it; the contracts are written after the gate,
against what the gate settled.

**Three counts open it**, in methods:

- **write** — no Javadoc exists and fan-in earns one;
- **update** — one exists, but not in the form `.claude/guides/contracts.md`
  states;
- **remove** — one exists that fan-in does not earn, or that describes the code
  instead of promising anything.

Then **one row per class**: its three counts, and a line on what the class
promises that makes them come out that way. A class whose count is `0 / 0 / 0` is
still a row — that it earns nothing is the finding.

Then *What needs deciding*, holding two kinds of item:

- **Signature changes the rules below force** — a `record` replacing transposable
  parameters, an enum replacing a mode-selecting `boolean`, a rename. These are
  code changes, so they are proposed here and not performed.
- **Every domain promise you cannot settle from the code**, in the checkpoint
  form `reference/classification.md` specifies. Nothing mechanical is stated as a
  question; a promise about notation you guessed at reads exactly like one you
  knew, which is the whole reason these are asked.

**Then the review gate: open, review, resolve.** No Javadoc is written and no
signature changed before it. Then write, in this order: the method contracts as
the reviewer settled them, the class Javadoc — invariants spanning several of its
methods — then `package-info.java` for invariants spanning the classes. **Anything
spanning subsystems goes to `docs/`;** if a `docs/` document already states it,
cite rather than restate.

Three things that are not optional:

- **`@return` on every method whose return type is not `void`.** It is what the
  IDE shows at the call site.
- **The signature rules apply as you go** — more than four parameters or two
  transposable same-typed parameters take a `record`; a mode-selecting `boolean`
  takes an enum; a misdescribing name gets renamed via `jet_brains_rename`.
- **The tell of a real contract is that the implementation could in principle
  violate it.** If you cannot imagine the code breaking the promise, you have
  described the code.

**Do not document a guard no caller can reach.** A `@throws` for an impossible
condition propagates outward into callers that handle it.

**Snapshot per class or per coherent group.**
