## Undo/Mutation System

Structural changes to `Song` become typed `Mutation` records, batched into one
`SongDidChangeNotification`. All types in `songscribe.message.mutation`. `Mutation` is
sealed — its `permits` list is the inventory; new subtypes must be added there.

### Scope

Line-scoped mutations implement `LineScopedMutation` (exposes the affected line).
Song-scoped (no `Line`): `LineInsertion`, `LineDeletion`, `MetadataChange`, `FontChange`,
`LayoutChange`, `LyricsChange`.

### Field enums

Several mutations identify *which* field changed via a field enum. Three shapes exist
— don't assume every field enum implies validation:

- **Validated `Object` old/new values** — `LineKeyChange` (`KeyField`),
  `LineLayoutChange` (`LineLayoutField`), `MetadataChange` (`MetadataField`),
  `LayoutChange` (`LayoutField`). The record's canonical
  constructor calls `FieldTypeValidator.validate`, so a value whose runtime type does
  not match the field's `getExpectedType()` fails at construction rather than at cast
  time during undo replay or in a subscriber:

  ```java
  public record LineKeyChange(Line line, KeyField field,
                              @Nullable Object oldValue, @Nullable Object newValue)
      implements Mutation, LineScopedMutation {

      public LineKeyChange {
          FieldTypeValidator.validate("LineKeyChange", field, field.getExpectedType(),
                                      oldValue, newValue);
      }
      // ...
  }
  ```

  `expectedType` and the values must be **boxed reference types** (`Integer.class`,
  not `int.class`); `Class.isInstance` never matches primitives.

- **`EnumSet<ElementField>`** — `ElementModification` carries the *set* of changed
  fields, not old/new values; the before-state lives in `beforeElement`, the
  after-state in `afterElement` (both clones — undo/redo restore either one in
  place via `StaffElement.copyStateFrom`, preserving element identity). No
  `FieldTypeValidator`.

- **Typed values, no validation** — `LyricsChange` carries `String oldText/newText`
  with `LyricsField`, and `FontChange` carries full `DocumentFonts oldFonts/newFonts`
  snapshots (no field enum — a multi-role commit is one undoable group); concrete
  field types make a runtime validator unnecessary.

### Emitting

Mutations may only be recorded inside an open **modification bracket**. Brackets
**nest** — the notification fires once, when the outermost bracket closes, and only
if at least one mutation was accumulated (an empty bracket posts nothing and does not
set the modified flag).

**Complete-emission invariant:** *every* state change made inside a modification
bracket must be recorded as a `Mutation` in that bracket's batch. Undo replays the
recorded batch mechanically, so an untracked side change (e.g. a raw
`spans.removeIf`) is invisible to undo and makes the round-trip lossy. When
a helper must drop dependent state (invalidated endings, spans anchored to a deleted
element, spans subsumed by a merge), it routes each removal through the typed tracked
helper (`removeBeaming`/`removeTie`/… — `Line.removeInvalidatedSpan`
dispatches) so the proper removal mutation lands in the batch.

**Companion-ordering rule:** companion mutations that *remove* dependent state
(span removals, the initial-tempo displacement modification in
`Line.addElement(int, StaffElement)`) are emitted **before** the primary structural
mutation. Reverse-order undo then restores the primary element first, so span
re-additions find their anchor elements live. Line-terminal maintenance companions
are emitted **after** the primary `LineInsertion`/`LineDeletion` — reverse-order
undo handles them before the line op, which is correct because they target the
*other* line.

Bracket + record entry points exist on both `Song` and `Line` (`Line` delegates to
its `Song` — use whichever the call site already holds). `Song`'s are themselves
one-line delegates to its `ModificationSession`, which owns the depth counters and
the accumulated batch; read that class when you need the mechanism, not just the
contract:

- `withModification(Runnable)` — opens a bracket, runs the runnable, closes it
  (depth-balanced even if the runnable throws).
- `Song.postWithModification(Message)` — convenience for
  `withModification(() -> MessageCenter.post(message))`.
- `applyChange(Mutation, Runnable mutator)` — must be inside a bracket; runs `mutator`,
  then appends the record. Call directly only when the caller must supply a specialized
  pre-mutation snapshot (e.g. a press-time clone in a drag handler); otherwise use a
  `Line` helper. `Line.applyChange` additionally runs the mutator *without* recording
  when `withoutMutationTracking` is active.

Canonical direct-`applyChange` example — `PitchShifter.commitPitchShift` (uses the
`Line` variants; pitch already changed during the drag, so each `ElementModification`
carries an empty mutator, a press-time before clone, and a current after clone):

```java
line.withModification(() -> {
    for (var entry : group) {
        line.applyChange(
            new ElementModification(line, entry.index(), EnumSet.of(ElementField.PITCH),
                                    entry.beforeClone(), line.getElement(entry.index()).clone()),
            () -> {});
    }
    // follow-up cleanup (glissando/grace-note removal) emits into the same bracket
});
```

Canonical simple case — a `Song` field setter wraps both calls in one statement:

```java
withModification(() -> applyChange(new MetadataChange(field, current, newValue), apply));
```

`Line` helpers (`addElement`, `removeElement`, `removeRange`,
`replaceElement`, `modifyElement`, `add/remove{Beaming,Tie,Tuplet,Crescendo,Diminuendo}`,
`add/removeSpan`) wrap `applyChange` with clone snapshots and
bookkeeping (interval shifting, range-element invalidation, initial-tempo attachment).
The span helpers are the thinnest pattern to copy when adding one. Note that the
mutator both attaches parentage and mutates the list. For the elements held in
`Line`'s two lists, `Line.attach`/`Line.detach` are the only writers of
`parentLine`, and running them inside the mutator is what makes parentage move
with the recorded change. `appendChild`/`removeChild` pair the two steps for the
`spans` list:

```java
applyChange(new BeamingAddition(this, beam), () -> appendChild(beam));
```

(Attachments — articulations, fermatas — are in neither list, so this does not
apply to them; `LineElement.addChild`/`removeChild` own their `parentLine`.)

For any new mutation pattern that doesn't require a caller-supplied snapshot, add a
helper to `Line`.

**`ElementModification.beforeElement`** must be a clone captured *before* the mutator
runs, and **`afterElement`** a clone captured *after* it runs. `Line.modifyElement`
does both automatically.

**`Song.withoutMutationTracking(Runnable)`** — full suspension: records nothing (no
notification, no undo, no modified flag). Used by test setup and by production
file-load infrastructure (`MusicXmlReader`, `SongIO`, `ScoreView.setSong`).

**`Song.withReplay(Runnable)` / `isReplaying()`** — replay mode, used by the undo
engine while it re-applies a recorded batch inside an open bracket. Unlike
suspension, mutations ARE still recorded into the bracket; what replay changes is
that the helpers apply raw state only — companion side-work (terminal maintenance,
`applyLineDefaults`, span invalidation, tuplet auto-removal, span merging) is
suppressed and the `Line` terminal guards are bypassed, because the recorded batch
already contains every change and mid-replay intermediate states legitimately
violate the guards. The anchor re-pointing in `Line.setElement` is NOT suppressed
(self-inverting, required for span references to stay valid). Nestable
(depth-counted).

### Subscribing

`SongDidChangeNotification` — never construct directly; posted by `Song.endModification`
after the outermost bracket closes. Handler name: `songDidChange`.

API:
- `getMutations()` — immutable ordered list.
- `getSong()`.
- `getLine()` **EDT only** (lazy cache, unsynchronized):
  - `null` if no line-scoped mutations or they target different lines
  - Otherwise the `Line` shared by every `LineScopedMutation` in the list (ignoring song-scoped mutations)
- `hasMutationOf(Class<? extends Mutation>)` — `true` if the list contains at least one instance of the given subclass.

Filter with `instanceof` / pattern switches, not enum. Canonical large example:
`ScoreViewController.songDidChange`.
