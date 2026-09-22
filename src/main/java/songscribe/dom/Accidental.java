package songscribe.dom;

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

    /**
     * Returns the alteration this accidental applies to the natural pitch of a staff position.
     *
     * @return the signed offset from the natural pitch in semitones, {@code -2} for
     *     {@link #DOUBLE_FLAT} through {@code 2} for {@link #DOUBLE_SHARP}
     */
    public int semitones() {
        return semitones;
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
