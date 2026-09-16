## Step 9: Judge the diagrams

Any diagram in the target's `docs/` document or Javadoc. Keep only what shows
what prose cannot — a topology, a state machine, a sequence with genuine
concurrency. A diagram walking through a sequence the contracts already state is
the contract drawn a second time, and the second copy goes stale.

Each diagram, its verdict and the reason go into
`plans/design-pass-<pass>/findings-9.md`. **Then the review gate: open, review,
resolve.** A diagram is someone's explanation of the system and deleting one is
not yours to decide alone.

**Snapshot** if anything changed.
