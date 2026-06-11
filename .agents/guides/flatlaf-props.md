## FlatLaf Properties

- Defs: `src/main/resources/songscribe/FlatLaf.properties`, keys prefixed `SongScribe.`. Dark overrides: `[dark]SongScribe.foo = ...`.
- Generated constants: `build/generated-sources/songscribe/ui/FlatLafKeys.java` (via `scripts/generate-flatlaf-keys.groovy`, run by `./scripts/compile.sh`).
- Read: `FlatLafProps.get(FlatLafKeys.X)` — return type inferred. Disambiguate with explicit type witness: `FlatLafProps.<T>get(...)`. Missing key → `RuntimeError.exit`.

### Usage

FlatLaf stores each property already typed (e.g. `... = 20` is an `Integer`, a color is a `Color`). `get` just returns that stored value — it does not convert. The type witness is only about giving Java a target type to infer `T` from.

```java
// Reference-typed target — no witness needed:
setBackground(FlatLafProps.get(FlatLafKeys.SCORE_PANEL_BACKGROUND));
int indent = FlatLafProps.get(FlatLafKeys.DIALOG_LABEL_INDENT);

// var with a reference type — use type witness:
var color = FlatLafProps.<Color>get(FlatLafKeys.SCORE_PANEL_BACKGROUND);

// var with a numeric/primitive type — use a primitive cast, not a witness:
var gap = (int) FlatLafProps.get(FlatLafKeys.DIALOG_COMPONENT_VERTICAL_EXTRA_GAP);

// primitive parameter — cast to unbox:
add(Box.createHorizontalStrut((int) FlatLafProps.get(FlatLafKeys.DIALOG_COMPONENT_HORIZONTAL_GAP)));
```

Use the witness only for reference-typed `var` targets. For numeric values, a primitive cast (`(int)`, `(float)`, etc.) unboxes naturally and is clearer than a witness. Never add a redundant witness when the target type already resolves it. The cast inside `get` is unchecked, so a wrong type throws `ClassCastException` at the use site, not in `get`.

### Adding / removing keys

- Add a key to `FlatLaf.properties` → run `./scripts/compile.sh` to regenerate `FlatLafKeys` → reference the new constant.
- The generator **fails the build** if any `SongScribe.*` key is unreferenced in `src/`. Consequences:
  - You cannot add a key speculatively — it must be used in the same change.
  - Removing the last usage of a key requires also removing the key from `FlatLaf.properties`.
- The generator also throws on constant-name collisions (suffix uppercased, dots → underscores).
