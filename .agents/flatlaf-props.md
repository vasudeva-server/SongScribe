## FlatLaf Properties

Custom UI constants (spacing, colors, sizes) live in `src/main/resources/songscribe/FlatLaf.properties` and are accessed via two generated/utility classes.

### Defining Properties

Add entries to `FlatLaf.properties` using the `SongScribe.` prefix:

```properties
SongScribe.score.tempo.note.scale = 0.65
SongScribe.dialog.component.vertical.gap = 5
SongScribe.score.playingNote.color = #1fcc00
```

Dark-mode overrides use the `[dark]` prefix:

```properties
SongScribe.scorePanel.background = #c0c0c0
[dark]SongScribe.scorePanel.background = #1c1c1f
```

### Accessing Properties

A build plugin generates `target/generated-sources/ui/FlatLafKeys.java` with a `public static final String` constant per property. Run `./scripts/compile.sh` after adding new properties.

Read values with `FlatLafProps.get(key)` — the return type is inferred from the assignment target:

```java
int gap = FlatLafProps.get(FlatLafKeys.DIALOG_COMPONENT_VERTICAL_GAP);
Insets padding = FlatLafProps.get(FlatLafKeys.DIALOG_TAB_PADDING);
float scale = FlatLafProps.get(FlatLafKeys.SCORE_TEMPO_NOTE_SCALE);
```

When the target type is ambiguous, use an explicit type parameter:

```java
scaleButton.setFont(font.deriveFont(FlatLafProps.<Float>get(FlatLafKeys.SOME_FLOAT_KEY)));
```

`FlatLafProps.get()` throws `RuntimeError.exit()` if the key is missing — missing properties are treated as a fatal installation error.
