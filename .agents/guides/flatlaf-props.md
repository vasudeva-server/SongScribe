## FlatLaf Properties

- Defs: `src/main/resources/songscribe/FlatLaf.properties`, keys prefixed `SongScribe.`. Dark overrides: `[dark]SongScribe.foo = ...`.
- Generated constants: `build/generated-sources/songscribe/ui/FlatLafKeys.java` (via `scripts/generate-flatlaf-keys.groovy`, run by `./scripts/compile.sh`).
- Read: use typed getters — one exists for every type FlatLaf supports. Missing key or type mismatch → `RuntimeError.exit`.

### Usage

FlatLaf stores each property already typed (e.g. `... = 20` is an `Integer`, a color is a `Color`). The typed getters retrieve, type-check, and return in one call.

```java
setBackground(FlatLafProps.getColor(FlatLafKeys.SCORE_PANEL_BACKGROUND));
var color = FlatLafProps.getColor(FlatLafKeys.SCORE_PANEL_BACKGROUND);
var indent = FlatLafProps.getInt(FlatLafKeys.DIALOG_LABEL_INDENT);
var gap = FlatLafProps.getInt(FlatLafKeys.DIALOG_COMPONENT_VERTICAL_EXTRA_GAP);
```

Available typed getters: `getBoolean`, `getInt`, `getFloat`, `getString`, `getColor`, `getFont`, `getInsets`, `getDimension`, `getBorder`, `getIcon`, `getGrayFilter`.

### Adding / removing keys

- Add a key to `FlatLaf.properties` → run `./scripts/compile.sh` to regenerate `FlatLafKeys` → reference the new constant.
- The generator **fails the build** if any `SongScribe.*` key is unreferenced in `src/`. Consequences:
  - You cannot add a key speculatively — it must be used in the same change.
  - Removing the last usage of a key requires also removing the key from `FlatLaf.properties`.
- The generator also throws on constant-name collisions (suffix uppercased, dots → underscores).
