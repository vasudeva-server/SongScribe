## FlatLaf Properties

- Defs: `src/main/resources/songscribe/FlatLaf.properties`, keys prefixed `SongScribe.`. Dark overrides: `[dark]SongScribe.foo = ...`.
- Generated constants: the `FlatLafKey` enum at `build/generated-sources/songscribe/ui/FlatLafKey.java` (via `scripts/generate-flatlaf-keys.groovy`, run by `./scripts/compile.sh`).
- Read: FlatLaf stores each property already typed (`... = 20` is an `Integer`, a color is a `Color`), so a typed getter retrieves, type-checks and returns in one call. One exists for every type FlatLaf supports, so no conversion belongs at a call site. Missing key or type mismatch → `RuntimeError.exit`.

### Adding / removing keys

- Add a key to `FlatLaf.properties` → run `./scripts/compile.sh` to regenerate `FlatLafKey` → reference the new constant.
- The generator **fails the build** if any `SongScribe.*` key is unreferenced in `src/`. Consequences:
  - You cannot add a key speculatively — it must be used in the same change.
  - Removing the last usage of a key requires also removing the key from `FlatLaf.properties`.
- The generator also throws on constant-name collisions (suffix uppercased, dots → underscores).
