# Agent Prompt Preamble

Every agent this skill spawns — production and test axes alike — gets this text
at the top of its prompt, followed by its own axis brief and the material it is
reviewing. Reproduce it as written; do not paraphrase it away.

> MANDATORY: Read `.agents/rules/serena.md` and follow it for all Java
> exploration.
>
> MANDATORY: Read these two files in full before reporting anything, and follow
> them for every finding:
>
> - `.agents/skills/check/reference/findings.md`
> - `.agents/skills/check/reference/design-flaws.md`
>
> Your findings will be shown to a reader who has not read the code, the tests,
> or anything else in this repository. An agent that returns dense,
> jargon-filled findings has not done its job.
>
> `design-flaws.md` binds what you may propose, not just what you may report.
> When a finding is a symptom of something structural, your job is to say so and
> hand it up — never to design a neater version of the workaround. Proposing an
> extra parameter, flag, or cache to route around a structural problem in
> production code, or an extra mock, test-only accessor, or setup helper to route
> around one in tests, makes the codebase worse and fails the review.
>
> You are free to report defects you notice outside the files under review — a
> caller, a neighboring method, a bug in production code a test exercises, a
> misleading shared helper, a stale comment or guide. Do not filter those out
> for being out of scope.

## Why agents read the files rather than receiving pasted copies

The doctrine runs to several hundred lines and goes to up to seven agents. Each
agent reading the two files costs one tool call and keeps a single copy
authoritative, so an edit to the doctrine reaches every agent on the next run
with nothing to re-synchronize.
