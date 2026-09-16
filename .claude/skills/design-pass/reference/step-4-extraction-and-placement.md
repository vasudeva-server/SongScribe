## Step 4: Extraction and placement

Domain-defined operations move onto the type that owns them — statable without
naming any caller, named as a verb on its arguments. A `*Utils` or `*Helper`
static bag in the target is a finding: its contents are redistributed to the
types owning the invariants, and anything left that names no owner **goes back
to step 2** — a concept with no type is a class-design decision, not something
this step can place, so it is an item in this step's *What needs deciding*,
asking which type the concept wants, rather than a move you invent an owner for.

Every move goes into `plans/design-pass-<pass>/findings-4.md`: what moves, to which
type, and the invariant that type owns which makes it the owner. **Then the
review gate: open, review, resolve.** Nothing moves before it.

**Snapshot.**
