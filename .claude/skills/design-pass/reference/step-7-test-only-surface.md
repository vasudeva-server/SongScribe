## Step 7: Fix test-only surface

Each finding is one of four kinds, and the kind decides the fix:

- **Genuinely test-only** — delete it, or restructure the code so that production
  uses it too.
- **A replaceable process-global** — a setter, a probe or a widened field that
  lets a test swap something process-wide. The first architecture to consider is
  a stack of instances plus an `AutoCloseable` scope, per *Scope a global before
  you swap it* in `design.md`, and **naming the production caller that justifies
  it** is what tells a real scope from a test-only injection point in better
  clothes. When the whole set of conditions there does not point at a stack, a
  setter is the answer instead, and it states its restore obligation in its
  contract — `MessageCenter.setErrorHandler` is the worked example: the policy
  is process-wide, has no bounded lifetime, and has no owner to move to.
- **Misnamed internal API** — it takes arguments and returns a value and is
  already a coherent unit. Rename it to its concept and write its contract.
- **Lifecycle** — a class with `initialize()` and no way back has an incomplete
  lifecycle contract, tests or no tests. Follow `guides/conventions/lifecycle.md`
  and the `Disposable` interface.

**Reflection into production internals is the same violation**, not an escape
from it. When a test cannot arrange the state it needs, the answer is a
constructor or factory taking that state, used by production too.

`plans/design-pass-<pass>/findings-7.md` lists each finding with the kind it was
judged to be and the fix that kind decides — the kind is the argument, so a fix
stated without it is asking to be taken on trust. For a scope, name the
production caller that justifies it. **Then the review gate: open, review,
resolve.**

**Snapshot.**
