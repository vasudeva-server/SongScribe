## Step 1: Inventory

**Delegatable when the row is a whole package** — one agent, or one per
subpackage if it is large. **A row naming a handful of types is never delegated**;
it is small enough to read directly, and a subagent's summary of it costs more
than the files. An *(undecomposed)* row is where delegation earns the most: the
read has to name the systems the directory holds before one of them can be taken.

Produce:

- **every type in the target and what invariant it currently fails to carry** —
  a validated record whose domain is a known finite set, a primitive that could
  be transposed with a sibling, a mode-selecting boolean, a constructor that can
  build an unusable instance;
- **every `@Nullable`**, with which of the three answers in `design.md` applies;
- **every guard**, with the callers that can actually produce the value it
  rejects;
- **fan-in per public method** — how many callers rely on it, which is what
  decides whether it earns a contract at all;
- which existing `docs/*.md` describe the subsystem this target sits in, since a
  method cites that overview rather than restating it;
- every test class, with its case count and what it appears to cover.

Subagent prompts must bound the search surface: absolute working directory,
repo-relative paths for every directory to examine, Serena's `jet_brains_*` tools
named explicitly, and an instruction to report and stop rather than widen the
search. Include *"Read `.claude/rules/serena.md` and follow it for all Java
exploration and refactoring."*

Write the inventory into `plans/design-pass-<pass>/findings-1-2.md` as *What it
found*. It is facts only — a type that fails to carry an invariant is named
here and argued in step 2. **Snapshot**, and go straight on to step 2; the gate
for both steps is at the end of step 2, since the inventory alone is not a
decision anyone can review.
