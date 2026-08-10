# Design Flaws: Fix the Cause, Not the Symptom

Applies to every phase of the `check` skill and to every agent it spawns. Agents
are given this file to read. It governs what may be proposed, not merely what
may be reported.

Most findings a review turns up are not independent. Several awkward call sites,
a cached value that has to be invalidated in four places, a parameter threaded
through five methods that only the last one reads — these are usually one
structural mistake showing itself in several places.

Reporting the five symptoms and leaving the mistake in place is a **failed
review**. The fixes make the code longer, the flaw survives, and the next change
in that area pays for it again. This skill exists to fix the code at every
level, and the design level is the one that matters most.

The same holds for tests, in a sharper form. A test is a second consumer of
production code, so difficulty writing one is evidence about the code, not about
the test. When a test needs six mocks, or reaches into internals to arrange a
scenario, or asserts on an intermediate value because the real outcome is
unreachable, the honest finding is about the production design — not about the
test's assertions. An audit that reports "add an assertion here, split this test
there" while leaving the design that forced those tests into that shape
untouched has failed in exactly the same way.

## Tells: when a finding is a symptom

Treat a finding as a symptom, and go looking for its cause, whenever:

- two or more findings would be resolved by the same structural change;
- the obvious fix is to *add* something — another parameter, another boolean,
  another cache, another special case, another null check at a call site —
  rather than to remove or relocate something;
- the code has to keep two things in sync that could have been one thing;
- a class reaches through another class to get at what it actually needs;
- the fix would have to be repeated the next time someone adds a similar case;
- a value is computed only to satisfy a type or a signature, and nothing in
  production reads it;
- **the code names its own dead weight** — `IGNORED_*`, `UNUSED_*`, `dummy`, a
  comment or test constant explaining why a field is meaningless. A name like
  that is a previous author pointing at the design mistake; treat it as the
  strongest tell on this list, not as evidence the discard is fine;
- "absent" is encoded as a value some legitimate state could also produce, so
  the sentinel and the data share one channel;
- the type does not fit the concept — a four-field type carrying a two-field
  idea, a collection where there is only ever one, a string where an enum
  exists — so callers must fill in, ignore, or reinterpret parts of it.

### Tells that show up in the tests

- the setup is long or fragile because the code under test fetches its own
  dependencies out of singletons, statics, or global lookups;
- the test must mock a collaborator to reach the branch it cares about, and that
  branch is really a decision the caller should have made;
- several tests share a helper that exists only to work around an awkward
  constructor or an initialization order;
- the same behavior must be re-tested for every case, because the production
  code grew by special case rather than by fitting cases into a model;
- an error path is uncovered because nothing outside the class can provoke it;
- **a test names a value as meaningless** — a stub constant called `IGNORED_*`
  or `UNUSED_*`, a comment saying a field is not read. The suite is documenting
  a design mistake as intentional and, by asserting around it, holding it in
  place. Never treat this as evidence the discard is fine;
- a test asserts on a whole composite value when only part of it is real, so the
  meaningless parts acquire test coverage and become expensive to remove;
- a test uses an emptiness or default check to mean "nothing happened", where a
  legitimate result could also satisfy that check.

## Never propose a fix that preserves the flaw

A tidier workaround is still a workaround, and proposing one is worse than
reporting nothing, because it launders technical debt as an improvement.

In production code that means: another parameter, another flag, another cache,
another special case. In tests it means: another mock, another test-only setter,
another `@VisibleForTesting` accessor, another helper that hides the
awkwardness. Each makes the codebase bigger and the design worse.

If the only fix you can see is a workaround, say that outright and name the
design problem you could not see past. An honest "this needs a rethink I haven't
worked out" is a real finding.

## Before proposing a fix, find out who reads the value

A finding about *where* a computation belongs is not answerable until you know
*who consumes it*. Before proposing a placement, a merge, an extraction, or a
new assertion, trace every production reader of the value with
`jet_brains_find_referencing_symbols` — not by assumption — and state what you
found. The answer changes the finding's shape:

- **Several readers** — the question really is where the logic belongs, and a
  test should assert on what those readers depend on.
- **One reader** — ask whether the producer should be handing over this shape at
  all, or whether the single reader should own the derivation outright. Check
  that any test asserts the thing that reader actually uses, not a neighboring
  value that happens to be easier to reach.
- **No production reader** — the question is not "where should this live" but
  "why does this exist." A computation that exists only to fill out a return
  type is dead weight, and it is worth asking what the return type is wrong
  about. Do not write an assertion for it and do not strengthen the assertions
  that already exist; report that the code computes something nothing consumes,
  and that the tests are what keep it alive.

**Tests are not consumers.** A value that only tests read is unread. This
inversion matters: a heavily-asserted value can look load-bearing precisely
because of its tests, and that coverage is then an argument *for* removing it,
not against. Say plainly how many tests would be deleted rather than rewritten —
a fix that looks expensive in test count is often cheap in production risk.

## Never offer a menu of workarounds

Before asking the user to choose between options, check that **at least one
option leaves the code in a state you would defend.** If every option preserves
the flaw, the question is the wrong question, and asking it does real damage: it
launders a workaround as a decision the user made, and it spends their attention
on a choice that does not matter.

When you catch yourself about to ask "should A or B own this?" or "should this
test assert A or B?", stop and answer the prior question first — *should this
exist in this form at all?* Then either ask a question worth asking, or present
the design finding and let the user choose between fixing it and living with it.

This applies to framing you inherit from the agents. An agent that closes with
"this needs a decision about X" has often already narrowed X to the wrong axis;
the agents see the review target, not the design. *How to Write Findings* makes
you rewrite an agent's prose — this makes you re-derive its question. If the
honest answer to "which side should own this" is "neither, in this form," that
is the finding.

## Check the design notes before reporting

Read whatever surrounding code is needed to judge structure — callers,
collaborators, the package's `package-info.java`, and any matching design note
under `docs/`. If `docs/` documents why the current structure is the way it is,
address that reasoning directly rather than ignoring it: either say why it no
longer holds, or drop the finding.

Never invent architecture work to justify an agent's existence. If the code has
no structural problem, say so plainly and return no findings — a speculative
redesign of working code is a worse outcome than silence.

## Reporting a design finding

A design finding is a finding in its own right. Report it **above the symptoms
it explains**, after any concrete production bug. It follows *How to Write
Findings* and adds three things:

1. **The symptoms it accounts for.** List them, so the reader can see that one
   change replaces several separate fixes. Where tests are involved, say which
   become simple, and which stop being needed at all, once it is done.
2. **The corrected design.** Concretely: which class or method takes on which
   responsibility, what collaborator gets passed in rather than fetched, what
   stops existing, and what the call sites and tests look like afterward. Not
   "introduce an abstraction" — say what the abstraction is, what it owns, and
   what it hides.
3. **What the change touches**, stated in counts, not adjectives. Which files,
   how many call sites, which tests, and what would break if done carelessly.
   This is information the user needs, not an argument against doing it.
4. **A recommendation.** Say whether you think it should be done. A finding that
   describes a flaw, prices it, and then declines to take a position leaves the
   user to infer your view from your tone — and the tone will be cautious. State
   the position so that declining is a decision against something, rather than
   the path of least resistance.

Then say what it costs to leave it alone — what the next change in this area
will run into, and what will keep going wrong — **in at least as much concrete
detail as you gave for what the change touches.** Three sentences of cost
against one clause of benefit is editorializing, whatever the sentences say.

Size is never a reason to withhold a design finding, and neither is age. "This
predates the code under review" or "the class was already like that" makes it
exactly the kind of finding this skill exists to surface, not one to skip.

## Hedging is not neutrality

The rule above bans arguing against a fix you just proposed. This section closes
the gap it leaves: the discouraging is usually done by *framing*, without any
argument ever being made. Every move below is technically accurate and still
puts a thumb on the scale.

**Never compare the design fix's size to the other findings.** "This is a bigger
change than the fixes I listed earlier" tells the user nothing they need — their
decision does not depend on how it ranks against the tidy-ups that happened to
be listed first. Its only function is to make the real fix look expensive next
to the cheap ones. State what the change touches on its own terms.

**Never use size adjectives.** "Big", "large", "substantial", "invasive",
"significant refactor" are verdicts wearing the clothes of description. Give the
counts and let the user judge. A change is large or small relative to the
codebase it lives in — in a project of this size, two production files, a test
file, and a moved test is a small change — and you are not the one who decides
where that line falls.

**Never close on the cost.** The last sentence is the one that lands. End a
design finding on what it fixes, or on the recommendation — not on what it will
disturb.

**Files touched is not the metric.** Whether a change should be made is a
question about correctness and about what the flaw will keep costing. Sizing it
by files-touched substitutes a number that feels objective for the judgment that
actually matters, and it reliably favors leaving two competing definitions of
the same thing in place because consolidating them shows up as more diff.

**Never reach for a damage metaphor.** "Blast radius", "footprint", "ripple
effects", "surface area", "invasive", "risky" — each describes a fix as though
it were an accident, and settles the question before a single number is given.
The framing is also backwards. A structural flaw left in place reaches much
further than any fix for it: it keeps charging every future change in that area,
it teaches every new caller the wrong shape, and it compounds for as long as the
code lives. The fix touches four files once. If a damage metaphor belongs
anywhere in a design finding it belongs on the status quo — so drop the metaphor
and say plainly which files change.

The underlying habit is worth naming, because it is easier to catch by its
motive than by its wording: **hedging is asymmetric insurance for the reviewer.**
If the user declines, the hedge cost nothing; if they accept, it reads as having
flagged the risk in advance. That is a sentence written to protect the reviewer,
not to inform the user, and in a review it is worse than useless — it argues
against fixing precisely what the review was run to find.

Before sending a design finding, reread it and ask of each sentence about cost:
*does this help the user decide, or does it cover me for having proposed this?*
Delete every sentence in the second category.

## Approval

**A design-level finding always requires explicit approval before any code
changes — in both paths, including `--fix`.** `--fix` suppresses questions about
ordinary findings; it does not authorize restructuring the user's architecture
unattended. Present the flaw, the corrected design, and what the change touches
via AskUserQuestion and wait for an answer. Ordinary findings are handled per the
active path in the meantime.

*Hedging is not neutrality* governs the question too, and option labels are
where the thumb hides most easily. Do not label the choice by size — "Larger
refactor" against "Just the quick fixes" decides the question in the labels,
before the user has read a word of either. Name what each option *does*: "Give
the interval type its own shape" against "Leave both definitions in place." Put
the fix first, mark it recommended when you recommend it, and make sure the
declining option states what it leaves behind rather than what it saves.

If the user declines the design fix, do **not** quietly fall back to papering
over its symptoms, or to writing the workaround tests anyway. Say which of the
remaining fixes are workarounds that will need redoing once the design is
corrected, and let the user decide about those too.
