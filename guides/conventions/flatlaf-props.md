# FlatLaf Properties

Rules for adding or removing a custom UI property in `FlatLaf.properties`.

## Rules

- Prefix every custom key with `SongScribe.`, and write a dark override as `[dark]SongScribe.foo`.
- Reference a key through a `FlatLafKey` constant, and read it through the typed getter of `FlatLafProps`; do not convert at the call site.
- Use a new key in the same change that adds it, because the generator fails the build on an unreferenced `SongScribe.*` key.
- Remove a key from `FlatLaf.properties` when its last usage goes.
- Name keys so that no two produce the same constant name,, because the generator fails on a collision.
- Run `./scripts/compile.sh` after editing the file to regenerate `FlatLafKey`.
