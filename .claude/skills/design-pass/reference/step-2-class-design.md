## Step 2: Class design

**Inline. Never delegated.** Step 1 may be handed to a subagent because it
reports facts; this step decides what exists, and a hierarchy a subagent decided
is one nobody can audit afterward.

Against step 1's inventory, three questions:

1. **Does each concept in the target have exactly one type?** A type that is two
   concepts splits; two types that are one concept dissolve into one; a concept
   with no type at all is named here. Dissolving `KeyChange` into `Key` is the
   pattern, and it deleted the whole of a later step's work rather than adding
   to it.
2. **What varies?** That is what a hierarchy or interface is shaped around. What
   was deliberately *not* abstracted is stated too — an absence cannot be
   approved unless it is named.
3. **Which type owns each invariant step 1 found uncarried?** That answer is
   step 3's input. Making an invariant unrepresentable on the wrong type moves
   the runtime check rather than retiring it.

**Where the answer is "the types are wrong", this is the architecture gate** in
`design.md`: a summary, a diagram sized to the work, and the two or three forks
with what each buys and costs. A single coherent proposal leaves the user
nothing to do but agree. The decision is theirs, and every step below is derived
from the answer — so a change here is made before step 3 begins, not alongside
it.

The three answers and the architecture gate go into
`plans/design-pass-<pass>/findings-1-2.md` beneath step 1's inventory, as *What
needs deciding*. **Then the review gate: open, review, resolve.**
No type is created, split, dissolved or renamed until it is through.
