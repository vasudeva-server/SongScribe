# Production Review Agents

Four agents, spawned against the **production scope**. Spawn Reuse, Quality, and
Efficiency with `model: "sonnet"` — they surface candidate findings that the
orchestrator re-validates before fixing. Spawn **Architecture with
`model: "opus"`**: root-cause design analysis is the one axis where a smaller
model reliably returns a plausible-sounding workaround instead of the actual
cause, which is the exact failure this phase exists to avoid.

See `.agents/skills/check/reference/agent-preamble.md` for the text every agent
prompt must open with.

## Agent 1: Code Reuse

IMPORTANT: This agent must search the **entire codebase**, not just the review
target. The goal is to find reuse opportunities between the reviewed code and
the rest of the project.

For each piece of code under review:

1. **Search the rest of the codebase for existing utilities and helpers** that
   could replace code in the review target. Use Grep and Serena tools to find
   similar patterns in other packages — common locations are utility
   directories, shared modules, and files adjacent to the reviewed ones.
2. **Search for duplicate logic across packages.** If the review target contains
   logic that is duplicated (or near-duplicated) in other packages, flag it and
   suggest extracting a shared utility or using the existing copy.
3. **Flag any inline logic that could use an existing utility** — hand-rolled
   string manipulation, manual path handling, custom environment checks, ad-hoc
   type guards, and similar patterns are common candidates.

## Agent 2: Code Quality

Review the same code for hacky patterns:

1. **Redundant state** — state that duplicates existing state, cached values
   that could be derived, observers or effects that could be direct calls.
2. **Parameter sprawl** — adding new parameters to a function instead of
   generalizing or restructuring existing ones.
3. **Copy-paste with slight variation** — near-duplicate code blocks that should
   be unified with a shared abstraction.
4. **Leaky abstractions** — exposing internal details that should be
   encapsulated, or breaking existing abstraction boundaries.
5. **Stringly-typed code** — raw strings where constants, enums, or branded
   types already exist in the codebase.

## Agent 3: Efficiency

Review the same code for efficiency:

1. **Unnecessary work** — redundant computations, repeated file reads, duplicate
   network or API calls, N+1 patterns.
2. **Missed concurrency** — independent operations run sequentially when they
   could run in parallel.
3. **Hot-path bloat** — new blocking work added to startup or to per-request /
   per-render hot paths.
4. **Unnecessary existence checks** — pre-checking file or resource existence
   before operating (TOCTOU anti-pattern); operate directly and handle the error.
5. **Memory** — unbounded data structures, missing cleanup, listener leaks.
6. **Overly broad operations** — reading entire files when only a portion is
   needed, loading all items when filtering for one.

## Agent 4: Architecture

Spawn with `model: "opus"`. Its mandate is different in kind from the other
three: they look for defects, it looks for the reason the defects are there.
Give it the review target and this question — *if you had to explain every
awkward thing in this code with one structural mistake, what would it be?*

1. **Misplaced responsibility** — logic living in a class that has to reach for
   the data it needs, when the class that owns the data should own the logic.
2. **State that must be kept in sync** — two representations of one fact, where
   correctness depends on every writer remembering to update both.
3. **Control coupling** — flags, modes, or enum parameters that make one method
   behave as several; the caller is really selecting an implementation.
4. **Abstractions that leak by necessity** — a boundary whose callers cannot do
   their job without knowing what is behind it, so every new caller re-learns
   the internals and every internal change breaks callers.
5. **Growth by special case** — a structure where each new case was handled by
   adding a branch rather than by fitting the case into the model, so the next
   case costs the same again.
6. **Wrong seam** — units that do not match the way the code actually changes,
   so one conceptual change always touches several files in lockstep.
7. **Wrong representation** — a type whose shape does not fit the concept, so
   fields have to be filled with values that mean nothing, callers ignore parts
   of what they are handed, or "absent" is encoded as a value some legitimate
   state also produces. Look hardest where a general-purpose type from the
   platform (a rectangle, a point, a map, an array of two) is standing in for a
   domain idea that has a different shape.

For every value the review target computes, establish **who reads it in
production** before judging where it belongs — per *Before proposing a fix, find
out who reads the value* in `design-flaws.md`. A value with no production reader
is a finding on its own, and usually the visible end of the structural mistake
you are looking for.

Report the flaw, the symptoms it explains, the corrected design, and what the
change touches, per *Reporting a design finding*. Consult the design notes
first, and
return no findings when the code is structurally sound, per *Check the design
notes before reporting*.
