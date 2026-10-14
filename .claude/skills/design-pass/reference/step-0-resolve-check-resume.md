## Step 0: Resolve, check, resume

`$ARGUMENTS` is a **row number in the register**, `plans/design-pass-register.md`
— `0`, `7`, `18`, or a suffixed row a split produced, `14a`. Nothing else is a
target: the register decides what a pass covers and in what order, so work that
is not on it gets a row before it gets a pass.

**A number whose row has been split is not a target.** If `14` now reads `14a`,
`14b`, `14c`, say so and ask which; taking one on the user's behalf picks a
system for them.

**With no argument, take the work already open**, in this order:

1. **A row marked 🔄 resumes.** Its record holds the state; open it and continue
   at the first step not marked ✅. Where more than one row is 🔄, ask which —
   two passes open at once is a split the register does not intend.
2. **Otherwise the first ⏳ row in table order starts.** The ordering is a
   dependency fact, not a preference, so nothing further down is ready before it.
   Suffixed rows sort inside their number — `14a`, `14b`, then `15`.
3. **No ⏳ row left means the regime is done.** Say so; do not invent a target.

Say which row you took, and whether you resumed it or started it, before touching
anything.

Read that row. Its *System* column names the pass; its *Where* column names the
target, which is **a set of types, not a directory**. The register's *The unit is
a system, not a package* says why: `dom` is one directory and about ten systems,
so a row reading `dom`: `Ss`, `DocPx`, `ViewPx`, `DocumentScale` is four files in
a package whose other files belong to other passes.

Resolve *Where* into two lists of paths and confirm every one exists before going
further. A bare class name is located with `jet_brains_find_symbol`. Where a row
names a whole package, each list is that one directory.

| Row says | Production | Tests |
|---|---|---|
| `ui/selection` | `src/main/java/songscribe/ui/selection/` | `src/test/java/songscribe/ui/selection/` |
| `dom`: `Key`, `KeySignature` | `…/songscribe/dom/Key.java`, `…/dom/KeySignature.java` | `…/test/…/dom/KeyTest.java`, `…/dom/KeySignatureTest.java` |

A row marked *(undecomposed)* is a directory nobody has read yet. **Split it on
the read**, never before: step 1 names the systems the directory actually holds,
you take one of them, and step 12 rewrites the register's single row into those
systems. Splitting from a directory listing predicts boundaries instead of
finding them, which is the error the register exists to avoid.

Everything below calls the resolved target `<target>`, and everything the pass
writes lives in `plans/design-pass-<pass>/` — the record and the findings
documents together, named by the row rather than by the system, so nothing has
to be translated into a filename to be found again.

1. **Open the record.** `plans/design-pass-<pass>/record.md`. If it exists, read
   it and resume at the first step not marked ✅ — it is the only memory across a
   context clear. If not, create the directory and the record from the template
   at [record-template.md](record-template.md).

   **The *Gate* column decides where a resume lands.** A step marked 📖 stopped
   at its review: open `plans/design-pass-<pass>/findings-<step>.md`, read what
   came back, and resolve it — the step has changed nothing in the tree, so
   there is nothing there to infer its state from.

   **Otherwise resume inside the step, not at the top of it.** A step's row
   carries a *Plan* link once the step has one (see *Steps decompose; the record links
   where*). Open that plan and take the first phase its dashboard does not mark
   ✅. Restarting a step whose first three phases are already done redoes work
   the tree already holds, and the redo is invisible until it conflicts.
2. **Claim what the register holds for this pass.** Open
   `plans/design-pass-register.md` and read *Carry-forward findings*. Every item
   tagged for this pass **is this pass's work**, established by an earlier pass
   that could not act on it: copy each into the record's *Findings claimed*, and
   **delete the line from the register**. One list owns a finding at a time, so
   neither copy can drift from the other.

   Skip this on a resume; a record that already exists has already claimed.

   An item you conclude this pass should not act on goes **back** to the register
   with what you learned, retagged to whatever will actually reach the code. It is
   never dropped, and whether to act is the user's call, not yours.
3. **Confirm the tree is clean.** `git status`. The pass leaves its own work
   uncommitted, so anything already in the tree becomes indistinguishable from
   it; stop and say so.
4. **Verify the branch, and stop if it is a base branch.** A pass runs on its
   own branch, off `develop`. If `git rev-parse --abbrev-ref HEAD` says
   `develop` or `main`, **say so and stop** — do not offer to branch, and do not
   proceed while waiting for an answer. A pass leaves a large uncommitted tree,
   and on a base branch that tree is in everyone's way and cannot be set aside
   without taking the branch with it.
5. **Capture the baseline into the record's *Before* column**, before touching
   anything. `MAIN` and `TEST` are the two paths from the table, so the same
   commands serve a whole package and a handful of named types alike:

   ```bash
   MAIN=(<resolved production paths>)   # files, a directory, or a mix
   TEST=(<resolved test paths>)
   git rev-parse --short HEAD           # the commit the pass starts from
   find "${MAIN[@]}" -name '*.java' | xargs cat | wc -l
   find "${TEST[@]}" -name '*.java' | xargs cat | wc -l
   ```

   Then `./scripts/test.sh <the target's test classes>` and record the **passing
   count**, not the number of `@Test` methods.
