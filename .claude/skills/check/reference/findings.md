# How to Write Findings

Applies to every phase of the `check` skill and to every agent it spawns. Agents
are given this file to read; the orchestrator applies it again when rewriting
agent output for the user.

The reader has not read the code you are reviewing — nor the tests, nor the code
those tests exercise — and does not remember how any of it works. Write every
finding, question, and summary so that person understands it without opening a
single file. This is a hard requirement, not a style preference: a finding the
reader cannot understand is a failed finding.

## Every finding uses this shape

1. **Where** — `file.java:123`, plus the method or class name in plain words
   ("in the method that draws the beam").
2. **What the code does now** — one or two plain sentences describing the
   current behavior. Never assume the reader knows.
3. **What's wrong with it** — in plain words, including what actually goes wrong
   for the user or the program. Not "violates encapsulation" but "any other
   class can change this value behind the object's back, so a bug here would be
   very hard to trace."
4. **What to do instead** — the concrete change, described so the reader can
   picture it.

**For a finding about a test**, steps 1–3 sharpen:

1. **Where** — `SomeTest.java:123`, plus the test's purpose in plain words ("the
   test that checks a rest gets the right vertical position").
2. **What the test does now** — what it sets up and what it checks.
3. **What's wrong with it** — stated as *a real bug this test would not catch*,
   described concretely: "if the code returned the wrong staff line for a rest,
   this test would still pass, because it only checks that the result isn't
   null."

## Rules for the writing itself

- No abbreviations or internal shorthand the reader has not seen spelled out.
- No bare symbol names as explanation. `ViewScale.applyZoom()` means nothing on
  its own — say what it does.
- Never report a finding purely as a category name. "Redundant cached field" is
  not a finding; "the class stores the note count in a field even though it
  already has the list of notes, so the two can disagree" is. "Tautological
  assertion" is not a finding; "this test asserts that the value it just set
  equals itself, so it passes no matter what the code does" is.
- Full sentences, not telegraphic notes. One idea per sentence. Short sentences
  beat dense ones.
- Always answer "so what?" — what breaks, when, and who notices. If nothing
  observable breaks, say that plainly ("nothing breaks today; this is about
  making it harder to break later"). For a test finding, name the bug that slips
  through, or say plainly that nothing slips through and this is about wasted
  effort or noise.
- Skip severity labels and grades unless they carry real information; state the
  consequence instead. Confidence (high / medium / low) is still reported, in
  plain words.
- Describe the size of a fix in counts — files, call sites, tests — never in
  adjectives, and never by comparison to the other findings. See *Hedging is not
  neutrality* in `.agents/skills/check/reference/design-flaws.md`, which applies
  to every finding, not only design ones.

### Additional rules for test, coverage, and mutation findings

- Coverage numbers are meaningless on their own. Never report a bare percentage;
  say which behavior is untested and what breaking it would look like.
- For mutation results, do not just name the mutator. Say in plain words what
  the mutation changed ("the tool flipped `<` to `<=`") and what that would mean
  if it were a real bug ("a note exactly on the boundary would be placed one
  line too high, and no test noticed").

## Questions follow the same standard

Before asking anything, give the reader the background needed to answer it: what
the code or test does, what you saw, why you are unsure, and what each answer
would lead you to do. Never ask a question that presumes the reader has the file
in mind. The answer options must be understandable without looking at the code.

Questions are further constrained by *Never offer a menu of workarounds* in
`.agents/skills/check/reference/design-flaws.md`.

# Findings Outside the Review Target

The review target bounds **what is examined**, never **what may be reported**. A
defect the review turns up in code the target does not contain — a neighboring
method, a caller, a stale comment or guide, a branch the change just made dead —
is a finding like any other and MUST be reported.

This applies with particular force to the test axes. Auditing a test means
reading the production code it exercises, so the audit routinely surfaces
defects that are in no `*Test.java` file in scope: a bug in the production method
under test, a shared test helper that misleads its callers, a neighboring test
the scope happened to exclude. Every one of those MUST be reported.

Within this skill there is no such thing as an out-of-scope defect.

Do not soften, bury, or pre-decline such a finding. Specifically, never:

- report it and then recommend against acting on it because it is "small",
  "harmless", "cheap", "pre-existing", "not a test problem", or "in a file this
  change doesn't touch";
- mention it as an aside in a summary instead of listing it with the others;
- decide on the user's behalf that it isn't worth the churn.

Low cost is a reason to say the cost is low, not a reason to withhold the fix.
Whether a fix is worth making is the user's call, and they cannot make it unless
you put the choice in front of them.

**A concrete production bug outranks everything else — report it first.** It is
the one finding already hurting users. When it came out of the test axes, say
plainly whether any test in scope would have caught it, since that is itself a
coverage finding.

Every such finding must reach the user as an explicit, actionable proposal: what
is wrong, and what the concrete change would be. In Path B, offer it for
approval alongside the in-target findings. In Path A (`--fix`), fix it with
everything else.

Two things this does **not** license:

- **Widening the review.** Report what the review surfaced on its way through
  the target. Do not go hunting through unrelated packages for improvements.
  Following a symptom to the design flaw that produced it — or a struggling test
  to the production design that makes it struggle — is *not* widening. That is
  the review working as intended; see
  `.agents/skills/check/reference/design-flaws.md`.
- **Editing unasked in Path B.** Report and offer; change nothing until approved.
