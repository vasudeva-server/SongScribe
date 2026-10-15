package songscribe.layout;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Accidental;
import songscribe.dom.ElementType;
import songscribe.dom.NoteElement;
import songscribe.smufl.BBox;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/** Constants and stateless calculations for the accidental drawn left of a notehead. */
public final class AccidentalGeometry {

    /**
     * The accidental-to-notehead gap, shared by the renderer and the layout's reserved left extent
     * ({@code ElementColumnBuilder.calculateLeftExtentSs}).
     */
    public static final float ACCIDENTAL_PADDING_SS = 0.3375f; // 2.7px / 8 px/ss

    private static final float GRACE_ACCIDENTAL_PADDING_SS =
        ElementType.GRACE_NOTE_SCALE * ACCIDENTAL_PADDING_SS;

    private AccidentalGeometry() {}

    public static float getAccidentalWidthSs(NoteElement note) {
        var accidental = note.accidentalState().drawn();

        if (accidental == null) {
            return 0;
        }

        var widthSs = AccidentalTables.WIDTHS_SS[accidental.ordinal()];

        return note.getType().isGraceNote() ? ElementType.GRACE_NOTE_SCALE * widthSs : widthSs;
    }

    /** The renderer and {@code NoteColumnGeometry} must share this, or the drawn glyphs and the glissando clearance drift apart. */
    public static float getAccidentalStartXSs(NoteElement note) {
        var paddingSs = note.getType().isGraceNote()
            ? GRACE_ACCIDENTAL_PADDING_SS
            : ACCIDENTAL_PADDING_SS;

        return -paddingSs - getAccidentalWidthSs(note);
    }

    /**
     * Returns {@code null} when the note has no accidental, or when it is a grace note:
     * grace-note accidentals are deliberately not reserved into the structural stacking layer.
     */
    public static @Nullable BBox getAccidentalBoundsSs(NoteElement note) {
        var accidental = note.accidentalState().drawn();

        if (accidental == null) {
            return null;
        }

        // Grace-note accidentals do not reserve structural stacking space (by design).
        if (note.getType().isGraceNote()) {
            return null;
        }

        return AccidentalTables.BOUNDS_SS[accidental.ordinal()];
    }

    private static BBox computeAccidentalBounds(SMuFLGlyph accidentalGlyph, float totalWidthSs) {
        var startX = -ACCIDENTAL_PADDING_SS - totalWidthSs;

        return SMuFLMetadata.bboxSs(accidentalGlyph).translateXSs(startX);
    }

    private static final class AccidentalTables {
        static final float[] WIDTHS_SS = widthsSs();
        static final BBox[] BOUNDS_SS = boundsSs();

        private static float[] widthsSs() {
            var accidentals = Accidental.values();
            var widthsSs = new float[accidentals.length];

            for (var i = 0; i < accidentals.length; i++) {
                widthsSs[i] = (float) SMuFLMetadata.advanceWidthSs(accidentals[i].glyph());
            }

            return widthsSs;
        }

        private static BBox[] boundsSs() {
            var accidentals = Accidental.values();
            var boundsSs = new BBox[accidentals.length];

            for (var i = 0; i < accidentals.length; i++) {
                boundsSs[i] = computeAccidentalBounds(accidentals[i].glyph(), WIDTHS_SS[i]);
            }

            return boundsSs;
        }
    }
}
