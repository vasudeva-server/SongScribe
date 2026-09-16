## Step 13: Close out

**Harvest, and delete the record** — `plans/design-pass-register.md` states
why under *The record is working memory*. Two things leave the record first:

1. **Anything a later pass owns** goes to the register's *Carry-forward
   findings*, tagged with the row that owns it — `**→ Pass 7.**` — with file and
   line, and stated so a reader who never saw this pass can act on it. Where the
   owning row may never be scheduled, tag the track that will actually reach the
   code instead. Nothing speculative, and nothing this pass could have fixed
   itself: a finding inside your own reach is fixed here, while the reason for it
   is still in hand.
2. **Anything about the system's shape** goes to `docs/`, written in this pass
   rather than promised to a later one.

Both, plus any row rewrite a split forces, go into
`plans/design-pass-<pass>/findings-13.md` first. **Then the review gate: open,
review, resolve.** Deletion is the one step nothing recovers from, and a
carry-forward item is written for a reader who will never be able to ask what it
meant.

Then flip the register row to ✅ and **delete `plans/design-pass-<pass>/`
entire** — the record and every findings document go together, they are working
memory on the same terms, and everything in them that outlives the pass has just
been harvested. Any execution plan a step took goes with them, from wherever
`/make-plan` put it. The row, the findings and the deletion are one
change, and they land in the tree beside the pass's own work for the user to
commit.

**A row you split rewrites itself here.** Where step 1 read an *(undecomposed)*
directory and named the systems it holds, replace that single row with one row
per system, ordered by what each takes its types from, and mark ✅ only the one
this pass took. The names come from the read, so this is the one place the
register learns something it could not have predicted.

**A split suffixes; it never renumbers.** Row `14` becomes `14a`, `14b`, `14c`,
and every row below keeps the number it had. A row number is permanent identity —
carry-forward findings are tagged with it, and those tags are written by passes
that are over and cannot be asked what they meant. Renumbering would silently
re-point every one of them at the wrong system. A suffixed row that splits again
suffixes again: `14b1`, `14b2`.

Then report: what the types now carry that runtime checks used to — with step 3's
two counts as they actually landed — what the contracts turned out to promise,
what the reviews changed about what you proposed, and anything you surfaced that
is not yours to decide.
