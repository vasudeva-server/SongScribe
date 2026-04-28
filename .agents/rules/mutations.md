## Mutation System

Every structural change to a `Composition` is recorded as a typed `Mutation` record and
delivered to subscribers inside a single `CompositionDidChangeNotification`. This replaces
the old `ChangeType` enum — subscribers use `instanceof` / pattern matching to filter
instead of switching on enum values.

All mutation types live in `songscribe.message.mutation`. `Mutation` itself is a sealed
interface and the single source of truth for the full set:

```java
public sealed interface Mutation permits
    ElementInsertion, ElementDeletion, ElementRangeDeletion, ElementModification, ElementReplacement,
    LineInsertion, LineDeletion,
    LineKeyChange, LineLayoutChange,
    RangeElementAddition, RangeElementRemoval,
    BeamingAddition, BeamingRemoval,
    TieAddition, TieRemoval,
    TupletAddition, TupletRemoval,
    CrescendoAddition, CrescendoRemoval,
    DiminuendoAddition, DiminuendoRemoval,
    MetadataChange, FontChange, LayoutChange, LyricsChange { }
```

### Line-scoped vs. composition-scoped

Mutations that target a specific `Line` also implement `LineScopedMutation` (returns the
affected line). Composition-scoped mutations (`LineInsertion`, `LineDeletion`,
`MetadataChange`, `FontChange`, `LayoutChange`, `LyricsChange`) do not.

| Group | Types |
|---|---|
| Element (line-scoped) | `ElementInsertion`, `ElementDeletion`, `ElementRangeDeletion`, `ElementModification`, `ElementReplacement` |
| Line structure | `LineInsertion`, `LineDeletion` |
| Line properties (line-scoped) | `LineKeyChange`, `LineLayoutChange` |
| Range elements (line-scoped) | `RangeElementAddition`, `RangeElementRemoval` |
| Intervals (line-scoped) | `BeamingAddition/Removal`, `TieAddition/Removal`, `TupletAddition/Removal`, `CrescendoAddition/Removal`, `DiminuendoAddition/Removal` |
| Composition-wide | `MetadataChange`, `FontChange`, `LayoutChange`, `LyricsChange` |

### Field enums with validated types

Mutations that carry `Object` old/new values reference a field enum that declares the
expected runtime type. `FieldTypeValidator.validate` is called from the record's canonical
constructor so a type mismatch fails loudly at construction, not at cast time during undo
replay.

| Mutation | Field enum | Example |
|---|---|---|
| `ElementModification` | `ElementField` (e.g. `PITCH`, `FERMATA`, `ACCIDENTAL`) | — |
| `LineKeyChange` | `KeyField` (`ACCIDENTAL_COUNT: Integer`, `KEY_TYPE: KeyType`) | — |
| `LineLayoutChange` | `LineLayoutField` (`LYRICS_Y_POS_SS: Double`, etc.) | — |
| `MetadataChange` | `MetadataField` (`TITLE: String`, `TEMPO: Tempo`, ...) | — |
| `LayoutChange` | `LayoutField` (all `Double`, staff-space) | — |
| `FontChange` | `FontField` (value type is always `java.awt.Font`) | — |
| `LyricsChange` | `LyricsField` (value type is always `String`) | — |

When adding a new field enum value, set `expectedType` to the concrete class (e.g.
`Integer.class`, not `int.class`) and emit the mutation with a value of that type.

### Emitting mutations

Mutations must be accumulated inside a **modification bracket**. Prefer the high-level
helpers on `Line` — they wrap `composition.applyChange` with the correct mutation type,
the clone-before-mutate snapshot for `ElementModification`, and any bookkeeping (interval
shifting, range-element invalidation, initial-tempo attachment).

**`Line` helpers that emit mutations:**

| Helper | Emits |
|---|---|
| `Line.addElement(index, element)` | `ElementInsertion` |
| `Line.removeElement(index)` | `ElementDeletion` |
| `Line.removeRange(from, to)` | `ElementRangeDeletion` |
| `Line.replaceElement(index, element)` | `ElementReplacement` |
| `Line.modifyElement(index, field(s), mutator)` | `ElementModification` (clones automatically) |
| `Line.addBeaming/Tie/Tuplet/Crescendo/Diminuendo(interval)` + `remove…` | `*Addition` / `*Removal` |
| `Line.addRangeElement(el)` / `removeRangeElement(el)` | `RangeElementAddition/Removal` |

**Bracketing.** The bracket must be open when any of these run. The standard entry
points are:

```java
composition.withModification(() -> {
    line.addElement(index, note);
    line.modifyElement(index, ElementField.PITCH, () -> note.setA1(pitch));
});
```

For messages that propagate into `Composition`'s own `@Handler` methods, use
`composition.postWithModification(command)` — it opens the bracket, posts the message,
and closes the bracket so every mutation the handlers apply coalesces into a single
notification.

**Raw `applyChange`.** If no `Line` helper fits, call `composition.applyChange(mutation,
mutator)` directly. The mutator runs first, then the mutation record is appended to the
accumulated list. Must be inside a modification bracket — throws `IllegalStateException`
otherwise.

```java
composition.applyChange(
    new MetadataChange(MetadataField.TITLE, oldTitle, newTitle),
    () -> composition.setTitle(newTitle)
);
```

**`ElementModification` clone contract.** The `beforeElement` field must be a clone
captured **before** the mutator runs. `Line.modifyElement` does this for you. If you
somehow need to emit `ElementModification` manually, clone first:

```java
var beforeClone = line.getElement(index).clone();
composition.applyChange(
    new ElementModification(line, index, EnumSet.of(ElementField.PITCH), beforeClone),
    () -> { /* mutate element */ }
);
```

**Test-only suspension.** `composition.withoutMutationTracking(Runnable)` runs `body`
with mutation tracking suspended — no notification, no undo, no modified flag. Use only
for test setup that populates lines outside a user-driven bracket.

### Subscribing

`CompositionDidChangeNotification` carries the ordered list of mutations accumulated in
the bracket plus the source `Composition`. Use the `@Handler` naming rule from
`messages.md`: `compositionDidChange`.

Helpful APIs on the notification:

- `getMutations()` — `List<Mutation>`, immutable.
- `getComposition()` — the source composition.
- `getLine()` — the single line targeted by all line-scoped mutations, or `null` if
  there are none or they target different lines. **EDT-only** (lazily cached without
  synchronization). Composition-scoped mutations are ignored for this check.
- `hasMutationOf(Class<? extends Mutation>)` — true if any mutation in the list is an
  instance of the given subclass.

**Filtering patterns.** Prefer pattern matching / `instanceof` over enum switches:

```java
@Handler
public void compositionDidChange(CompositionDidChangeNotification n) {
    if (n.hasMutationOf(LyricsChange.class)) { /* refresh lyrics */ }

    for (var mutation : n.getMutations()) {
        if (mutation instanceof LineScopedMutation
            || mutation instanceof LineInsertion
            || mutation instanceof LineDeletion) {
            // needs line-level layout invalidation
        }

        switch (mutation) {
            case ElementModification em when em.fields().contains(ElementField.PITCH) ->
                // react to pitch change; em.beforeElement() has the pre-mutation snapshot
            case ElementInsertion ei -> /* … */ ;
            default -> { }
        }
    }
}
```

`ScoreMessageCoordinator.compositionDidChange` is the canonical large-scale example.

### Rules

- **Never construct `CompositionDidChangeNotification` directly.** It is posted by
  `Composition.endModification` after the outermost bracket closes.
- **Never create a new `Mutation` subtype without adding it to `Mutation`'s `permits`
  list.** The sealed interface is the inventory.
- **Line helpers > raw `applyChange`.** Reach for `Line.addElement` etc. before hand-rolling
  an `applyChange` call. If you need a new helper, add it to `Line` so every caller gets the
  same bracket/clone/bookkeeping semantics.
- **Field-enum values must be boxed reference types.** `FieldTypeValidator` compares via
  `Class.isInstance`, which never matches primitives.
- **Don't suppress mutation tracking in production.** `withoutMutationTracking` is for
  tests only.
