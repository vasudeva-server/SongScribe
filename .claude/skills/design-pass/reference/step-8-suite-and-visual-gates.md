## Step 8: The suite gate and the visual gate

Every snapshot in steps 2 through 7 builds and runs the classes it touched, or
that state is unverified. This step is the **full-suite gate**: run
`./scripts/compile.sh --test`, then `./scripts/test.sh <the target's test
classes>`. A contract or type change here can break a caller in another package,
which is what the whole suite is for — and the whole suite is not yours to start.

**Ask the user to run it, and wait.** The whole suite is the user's to start.
State that the pass is at its gate, name what changed and which packages the
change can reach, and let the user decide when to spend the run. Never attempt
the suite yourself, in any form — not by naming many classes at once, not
through Gradle, not by any other spelling.

Never rerun a failure with extra flags, and never assume a failure is
pre-existing. **A failing test means one of three things — code, test, or
contract** — and weakening a contract to reach green is legitimate only when the
contract was wrong about the domain, stated explicitly, never decided silently.

**Then the visual gate.** A green suite says nothing about pixels. Where a pass
moved a measurement, a geometry, or a rendering call site, a regression shows as
text shifted by a fraction of a pixel, a box clipped at one edge, or a label that
stopped centring — and nothing in this repo asserts any of that.

**Derive the checklist from the call sites the pass re-pointed, never from a
stock list.** For each member the pass moved or changed, follow its fan-in from
step 1 out to the surface that draws it, and write one line naming three things:
what to open, what to look at, and what a regression looks like there. A pass
that re-points a glyph-ink query writes "volta bracket labels — the number sits
inside the bracket, not clipped at its right edge", not "check rendering". A line
a person cannot act on without reading the diff is not on the list.

**Ask the user to run it, and wait** — the application is never launched without
their permission. If the pass reached nothing a person can see, say so plainly
instead of emitting a checklist of things it cannot have broken.

**Snapshot after each gate**, and mark 8a and 8b separately. The visual gate can
sit open for a long time — it needs a person at the application — so a resume has
to be able to see that the suite is through and the eyes are not.
