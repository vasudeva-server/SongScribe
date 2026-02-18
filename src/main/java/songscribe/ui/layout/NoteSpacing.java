package songscribe.ui.layout;

import java.util.EnumMap;
import org.jetbrains.annotations.NotNull;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.NoteType;

/**
 * Horizontal spacing utilities for note positioning.
 */
public final class NoteSpacing {

    public static final int ACCIDENTAL_WIDTH = 7;

    private static int firstNoteX = 100;

    private static final EnumMap<NoteType, Integer> NOTE_SPACING = new EnumMap<>(NoteType.class);

    static {
        NOTE_SPACING.put(NoteType.SEMIBREVE, 70);
        NOTE_SPACING.put(NoteType.MINIM, 50);
        NOTE_SPACING.put(NoteType.CROTCHET, 35);
        NOTE_SPACING.put(NoteType.QUAVER, 25);
        NOTE_SPACING.put(NoteType.SEMIQUAVER, 25);
        NOTE_SPACING.put(NoteType.DEMI_SEMIQUAVER, 25);
        NOTE_SPACING.put(NoteType.SEMIBREVE_REST, 70);
        NOTE_SPACING.put(NoteType.MINIM_REST, 50);
        NOTE_SPACING.put(NoteType.CROTCHET_REST, 35);
        NOTE_SPACING.put(NoteType.QUAVER_REST, 25);
        NOTE_SPACING.put(NoteType.SEMIQUAVER_REST, 25);
        NOTE_SPACING.put(NoteType.DEMI_SEMIQUAVER_REST, 25);
        NOTE_SPACING.put(NoteType.GRACE_QUAVER, 30);
        NOTE_SPACING.put(NoteType.GRACE_SEMIQUAVER, 50);
        NOTE_SPACING.put(NoteType.GLISSANDO, 0);
        NOTE_SPACING.put(NoteType.REPEAT_LEFT, 25);
        NOTE_SPACING.put(NoteType.REPEAT_RIGHT, 25);
        NOTE_SPACING.put(NoteType.REPEAT_LEFT_RIGHT, 25);
        NOTE_SPACING.put(NoteType.BREATH_MARK, 15);
        NOTE_SPACING.put(NoteType.SINGLE_BARLINE, 60);
        NOTE_SPACING.put(NoteType.DOUBLE_BARLINE, 60);
        NOTE_SPACING.put(NoteType.FINAL_DOUBLE_BARLINE, 60);
        NOTE_SPACING.put(NoteType.PASTE, 0);
    }

    private NoteSpacing() {}

    public static int calculateLastNoteXPos(@NotNull Line line, Note note) {
        if (line.noteCount() == 0) {
            return firstNoteX;
        }
        var lastNote = line.getNote(line.noteCount() - 1);
        return (
            lastNote.getXPos() +
            Math.round(
                (NOTE_SPACING.get(lastNote.getNoteType()) +
                    (note.getAccidental().getWidthFactor() * ACCIDENTAL_WIDTH) +
                    (note.isAccidentalInParentheses() ? 8 : 0)) *
                line.getNoteDistChangeRatio()
            )
        );
    }

    public static int getNoteSpacing(@NotNull NoteType noteType) {
        return NOTE_SPACING.getOrDefault(noteType, 0);
    }

    public static int getFirstNoteX() {
        return firstNoteX;
    }

    public static void setFirstNoteX(int x) {
        firstNoteX = x;
    }
}
