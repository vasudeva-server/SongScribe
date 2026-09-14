package songscribe.dom;

import org.jspecify.annotations.Nullable;

import songscribe.smufl.SMuFLGlyph;

/**
 * A notated pitch alteration, belonging to a key signature and to pitch spelling as much
 * as to a note.
 */
public enum Accidental {
    NATURAL(0, SMuFLGlyph.ACCIDENTAL_NATURAL),
    FLAT(-1, SMuFLGlyph.ACCIDENTAL_FLAT),
    SHARP(1, SMuFLGlyph.ACCIDENTAL_SHARP),
    DOUBLE_FLAT(-2, SMuFLGlyph.ACCIDENTAL_DOUBLE_FLAT),
    DOUBLE_SHARP(2, SMuFLGlyph.ACCIDENTAL_DOUBLE_SHARP);

    private final int semitones;
    private final SMuFLGlyph glyph;

    Accidental(int semitones, SMuFLGlyph glyph) {
        this.semitones = semitones;
        this.glyph = glyph;
    }

    private int semitones() {
        return semitones;
    }

    /**
     * Returns the alteration {@code accidental} applies to the natural pitch of a staff
     * position, in semitones, or 0 when there is no accidental at all.
     *
     * <p>Every caller that compares accidentals by how they <em>sound</em> rather than by
     * which constant they are goes through here, so that rule is stated once: no accidental
     * and {@link #NATURAL} both answer 0, while remaining distinct as notation — a natural
     * draws a glyph and an absent accidental draws nothing.
     *
     * @param accidental the accidental written on the note, or null when it carries none
     * @return the signed offset from the natural pitch, {@code -2} for {@link #DOUBLE_FLAT}
     *     through {@code 2} for {@link #DOUBLE_SHARP}
     */
    public static int semitoneAdjustmentOf(@Nullable Accidental accidental) {
        return (accidental == null) ? 0 : accidental.semitones();
    }

    /**
     * Returns the SMuFL glyph this accidental draws, at every size. Grace notes use this same
     * regular glyph; the grace size comes from drawing it with a scaled-down font
     * ({@link ElementType#GRACE_NOTE_SCALE}), mirroring how grace noteheads are rendered.
     * There is no separate small-glyph table: Bravura's "small" accidentals are a distinct
     * private-use set with their own (non-proportional) metrics, and have no
     * double-flat/double-sharp variant, so they cannot scale uniformly with the regular
     * glyphs.
     *
     * @return the glyph; one per constant, never null
     */
    public SMuFLGlyph glyph() {
        return glyph;
    }
}
