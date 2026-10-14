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

    public int semitones() {
        return semitones;
    }

    /**
     * Grace notes draw this glyph at {@link ElementType#GRACE_NOTE_SCALE}; Bravura's "small"
     * accidentals have non-proportional metrics and no double variants, so they cannot replace it.
     */
    public SMuFLGlyph glyph() {
        return glyph;
    }
}
