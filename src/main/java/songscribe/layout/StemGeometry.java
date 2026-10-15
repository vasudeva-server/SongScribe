package songscribe.layout;

import java.awt.geom.Rectangle2D;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Direction;
import songscribe.dom.ElementType;
import songscribe.dom.NoteElement;
import songscribe.engraving.StemMetrics;
import songscribe.smufl.Anchor;
import songscribe.smufl.SMuFLMetadata;
import songscribe.smufl.StemmedNotehead;

/** Constants and stateless calculations for a note's stem and the flag at its tip. */
public final class StemGeometry {

    /** LilyPond's {@code shortening_step}, halved to ss. */
    private static final double FORCED_SHORTEN_PER_STEP_SS = 1.0 / 6.0;

    /** LilyPond's {@code 2·stem-shorten[0]}, halved to ss. */
    private static final double MAX_FORCED_SHORTEN_SS = 1.0;

    /** A forced stem never falls below this; for defensive flooring at the renderer. */
    public static final double FORCED_STEM_FLOOR_SS = StemMetrics.STEM_LENGTH_SS - MAX_FORCED_SHORTEN_SS;

    private static final Anchor GRACE_STEM_UP_SE = new Anchor(
        SMuFLMetadata.stemAnchors(StemmedNotehead.BLACK).stemUpSE().xSs()
            * ElementType.GRACE_NOTE_SCALE,
        SMuFLMetadata.stemAnchors(StemmedNotehead.BLACK).stemUpSE().ySs()
            * ElementType.GRACE_NOTE_SCALE
    );

    private StemGeometry() {}

    /** The <em>drawn</em> stem direction: a grace note is always drawn stem up, whatever it answers. */
    public static Direction effectiveDirection(NoteElement note) {
        return note.getType().isGraceNote() ? Direction.UP : note.direction();
    }

    /**
     * A stem is forced when the direction it answers opposes its
     * {@link NoteElement#naturalDirection()}, whoever turned it. An unbeamed auto stem is never
     * shortened, and neither is a grace stem.
     */
    public static double forcedShorteningSs(NoteElement note) {
        if (note.getType().isGraceNote() || note.direction() == note.naturalDirection()) {
            return 0.0;
        }

        var shortenSs = FORCED_SHORTEN_PER_STEP_SS * (1 + Math.abs(note.getStaffPosition().value()));
        return Math.min(MAX_FORCED_SHORTEN_SS, shortenSs);
    }

    /**
     * The length shared by the drawn stem ({@link #computeBaseStem}) and the column's baked
     * vertical extent ({@link ElementColumnBuilder#calculateStemTopSs}).
     */
    public static double stemLengthSs(ElementType noteType) {
        return noteType.isGraceNote()
            ? StemMetrics.GRACE_NOTE_STEM_LENGTH_SS
            : StemMetrics.STEM_LENGTH_SS;
    }

    /**
     * @param note must satisfy {@link ElementType#isNoteWithStem()}
     */
    public static BaseStem computeBaseStem(NoteElement note, Direction direction) {
        var noteType = note.getType();
        var isGrace = noteType.isGraceNote();
        var anchor = isGrace ? GRACE_STEM_UP_SE : stemSideAnchor(note.stemmedNotehead(), direction);
        var anchorXSs = anchor.xSs();

        // Stem left edge: the anchor marks the stem's RIGHT edge for up-stems (back off a full
        // stem width) and its LEFT edge for down-stems (use the anchor directly). Both leave the
        // stem's outer edge flush with the notehead's outer edge, overlapping inward by its width.
        var stemLeftXSs = direction.isUp() ? anchorXSs - StemMetrics.thicknessSs() : anchorXSs;

        return new BaseStem(stemLeftXSs, anchor.ySs(), stemLengthSs(noteType));
    }

    /** For a non-grace note; shared with {@link songscribe.ui.renderer.RenderingUtils#stemCenterXOffsetSs}. */
    public static Anchor stemSideAnchor(StemmedNotehead notehead, Direction direction) {
        var anchors = SMuFLMetadata.stemAnchors(notehead);

        return direction.isUp() ? anchors.stemUpSE() : anchors.stemDownNW();
    }

    /** Flag rendering and glissando column-extent computation must agree on where the grace flag sits. */
    public static double getGraceFlagOriginXSs(double stemLeftXSs) {
        return stemLeftXSs + StemMetrics.thicknessSs() * (1 - ElementType.GRACE_NOTE_SCALE) / 2;
    }

    /**
     * The single source of truth for where the flag sits, so spacing reservation,
     * augmentation-dot clearance and column geometry never drift apart. In the note's local
     * coordinate space.
     *
     * @return {@code null} when the note is beamed, stemless, or its type has no flag glyph
     */
    @Nullable
    public static Rectangle2D flagBBoxLocalSs(NoteElement note, Direction direction, Beaming beaming) {
        var noteType = note.getType();

        if (beaming == Beaming.BEAMED || !noteType.isNoteWithStem()) {
            return null;
        }

        var flagGlyph = noteType.getFlagGlyph(direction);

        if (flagGlyph == null) {
            return null;
        }

        var stemGeom = computeBaseStem(note, direction);
        var stemLeftXSs = stemGeom.stemLeftXSs();
        var stemTipYSs = stemGeom.stemTipYSs(direction);
        var flagBBox = SMuFLMetadata.bboxSs(flagGlyph);

        if (noteType.isGraceNote()) {
            var scale = (double) ElementType.GRACE_NOTE_SCALE;
            var flagOriginX = getGraceFlagOriginXSs(stemLeftXSs);

            return new Rectangle2D.Double(
                flagOriginX + flagBBox.leftSs() * scale,
                stemTipYSs + flagBBox.topSs() * scale,
                flagBBox.widthSs() * scale,
                flagBBox.heightSs() * scale);
        }

        return new Rectangle2D.Double(
            stemLeftXSs + flagBBox.leftSs(),
            stemTipYSs + flagBBox.topSs(),
            flagBBox.widthSs(),
            flagBBox.heightSs());
    }

    /**
     * @param stemLeftXSs relative to notehead origin
     * @param lengthSs    without beam lengthening
     */
    public record BaseStem(double stemLeftXSs, double anchorYSs, double lengthSs) {

        public double stemTipYSs(Direction direction) {
            return direction.isUp() ? anchorYSs - lengthSs : anchorYSs + lengthSs;
        }
    }
}
