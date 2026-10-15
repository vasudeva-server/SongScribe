package songscribe.layout;

import java.util.function.BiConsumer;

import songscribe.dom.Direction;
import songscribe.dom.Dots;
import songscribe.dom.NoteElement;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/** Constants and stateless calculations for a note's augmentation dots. */
public final class DotGeometry {

    public static final double DOT_ON_LINE_Y_SHIFT_SS = -0.5;

    // Center-to-center spacing between consecutive augmentation dots (in ss): one dot glyph
    // plus a one-dot-width gap, following LilyPond's stack-by-one-dot-width convention.
    public static final double DOT_SPACING_SS = SMuFLMetadata.advanceWidthSs(SMuFLGlyph.AUGMENTATION_DOT) * 2;

    private DotGeometry() {}

    /**
     * The dot sits one augmentation-dot width right of the note's right-most obstruction: the
     * notehead, unless an unbeamed up-stem flag extends further right.
     */
    public static double firstDotXSs(NoteElement note, Beaming beaming, Direction direction) {
        var obstructionRightSs = SMuFLMetadata.bboxSs(note.notehead().glyph()).rightSs();

        if (direction.isUp()) {
            var flagBBox = StemGeometry.flagBBoxLocalSs(note, Direction.UP, beaming);

            if (flagBBox != null) {
                obstructionRightSs = Math.max(obstructionRightSs, flagBBox.getMaxX());
            }
        }

        return obstructionRightSs + SMuFLMetadata.advanceWidthSs(SMuFLGlyph.AUGMENTATION_DOT);
    }

    public static void forEachDotPosition(
        NoteElement note, Beaming beaming, Direction direction,
        BiConsumer<? super Double, ? super Double> consumer
    ) {
        if (note.dots() == Dots.NONE) {
            return;
        }

        var yOffset = note.getStaffPosition().isOnLine() ? DOT_ON_LINE_Y_SHIFT_SS : 0.0;

        var dotX = firstDotXSs(note, beaming, direction);

        for (var i = 0; i < note.dots().count(); i++) {
            consumer.accept(dotX, yOffset);
            dotX += DOT_SPACING_SS;
        }
    }

    /** Derived from the dot positions {@link #forEachDotPosition} feeds the renderer, so reserved space matches the ink. */
    public static double dotsRightExtentSs(
        NoteElement note, Beaming beaming, Direction direction, double baseRightSs
    ) {
        if (note.dots() == Dots.NONE) {
            return baseRightSs;
        }

        var dotRightSs = SMuFLMetadata.bboxSs(SMuFLGlyph.AUGMENTATION_DOT).rightSs();
        var maxRightSs = new double[]{baseRightSs};

        forEachDotPosition(note, beaming, direction, (dotX, yOffset) ->
            maxRightSs[0] = Math.max(maxRightSs[0], dotX + dotRightSs));

        return maxRightSs[0];
    }
}
