## Step 3: Make illegal states unrepresentable

**Inline. Before any contract is written**, because every invariant a type
carries is a contract clause that never has to be written and a test that never
has to exist.

From step 1's inventory, over the type set step 2 settled, in this order:

1. **Closed enums** where the domain is a known finite set currently guarded by a
   validating constructor.
2. **Wrapper types** for interchangeable primitives a call site could transpose.
3. **Enums** replacing mode-selecting booleans and bare ints.
4. **Boundary conversions** — a file, network or UI edge that validates and then
   passes a raw value inward converts instead, so no layer below can hold an
   unproven one.
5. **Constructor validation** only where a closed type is not possible.

Each of these is a design change, so every one of them goes into
`plans/design-pass-<pass>/findings-3.md` — file and line, the correct structure
concretely, the recommendation — **as a separately decidable item**. Whether to
do each is the user's call, and a proposal bundled so that one no takes the rest
with it has removed that call.

**The document leads with two counts.** How many guards the proposal retires —
`@Nullable` among them — and how many types it introduces to carry an invariant
the code currently checks at runtime. That pair is the yield the user is deciding
on: every guard retired is a runtime check the compiler now makes instead, and a
proposal that introduces types without retiring guards has added structure
without moving anything. Both come from step 1's inventory of `@Nullable`s and
guards, and both stand before the per-change detail.

**Then the review gate: open, review, resolve.** Only what survives it is built —
and then the guards it retired are deleted, with the tests that were pinned to
them.

**Snapshot per change or per coherent group.**
