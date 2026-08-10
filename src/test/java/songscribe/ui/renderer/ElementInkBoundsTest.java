/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.ui.renderer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static songscribe.dom.StaffElementFactory.breathMark;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.repeatLeft;
import static songscribe.dom.StaffElementFactory.singleBarline;

import module java.desktop;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.engraving.LineThickness;
import songscribe.engraving.SMuFLConstants;
import songscribe.layout.NoteGeometry;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.util.GraphicUtils;

/**
 * Unit tests for the ink bounds produced by running the real renderers
 * ({@link NoteRenderer}, {@link ArticulationRenderer}, {@link FermataRenderer}) against a
 * {@link RecordingGraphics2D}, per {@code DisplayList#inkBoundsSs}.
 * <p>
 * Deliberately does not hand-compose expected geometry from scratch: the renderers are the only
 * source of truth for what ink they produce (see {@link RecordingGraphics2D}'s class doc), so
 * expectations here are derived from the same SMuFL metadata and geometry constants the
 * renderers themselves read, never from an independently re-derived formula.
 */
class ElementInkBoundsTest extends UnitTest {

    private static final double TOLERANCE = 1e-9;

    /** No X override translation — every recorded ink stays relative to the element's own origin. */
    private static final double NO_X_OVERRIDE_SS = 0.0;

    private static final int MIDDLE_STAFF_POSITION = 0;

    /** Above the outermost staff line ({@code |sp| > 5}), so ledger lines are required. */
    private static final int ABOVE_LEDGER_STAFF_POSITION = 12;

    @BeforeAll
    static void initAccidentalWidths() {
        // Required before any accidental query, per NoteGeometry's class doc.
        NoteGeometry.initializeAccidentalWidths();
    }

    /**
     * Runs the real renderers against a fresh {@link RecordingGraphics2D} for {@code element} —
     * {@link NoteRenderer} always, plus {@link ArticulationRenderer} and {@link FermataRenderer}
     * when the element carries the corresponding decoration — and returns the recorded ink.
     */
    private static DisplayList recordElement(StaffElement element) {
        var line = detachedLine();
        line.addElement(element);

        var invariants = RenderContextTestHelper.newContext(new Song())
            .setCurrentLine(line)
            .build();

        // currentElementIndex=0 lets RenderingUtils.getDecorationColor resolve without a laid-out
        // line; the X override keeps every recorded coordinate relative to the element's own origin.
        var frame = ElementFrame.LINE_LEVEL.withElement(0, NO_X_OVERRIDE_SS);

        var recorder = new RecordingGraphics2D();
        GraphicUtils.setRenderingHints(recorder);

        NoteRenderer.getInstance().render(invariants, frame, element, recorder);

        if (!element.getArticulations().isEmpty()) {
            ArticulationRenderer.getInstance().render(invariants, frame, element, recorder);
        }

        if (element.findAttachment(FermataAttachment.class) != null) {
            FermataRenderer.getInstance().render(invariants, frame, element, recorder);
        }

        return recorder.displayList();
    }

    private static Rectangle2D requireInkBounds(StaffElement element) {
        var bounds = recordElement(element).inkBoundsSs();

        assertThat(bounds).as("recordElement produced no ink for " + element.getType()).isNotNull();

        return bounds;
    }

    // ==========================================================================
    // T11 — note ink bounds
    // ==========================================================================

    @Nested
    class PlainNotehead {

        /**
         * A crotchet on the middle line, no accidental, no ledger lines: horizontal ink bounds
         * come from the notehead glyph's own SMuFL metadata alone — {@code getNoteheadXOffsetSs}
         * is 0 and the stem's outer edge is flush with the notehead's outer edge (never wider).
         */
        @Test
        void testHorizontalBoundsMatchNoteheadMetadata() {
            var note = crotchet();
            note.setStaffPosition(MIDDLE_STAFF_POSITION);
            note.setDirection(StaffElement.Direction.UP);

            var bounds = requireInkBounds(note);
            var noteheadBBox = SMuFLMetadata.requireBBox(SMuFLGlyph.NOTEHEAD_BLACK);

            assertThat(bounds.getMinX())
                .as("left edge matches the notehead glyph's own metadata")
                .isCloseTo(noteheadBBox.left(), within(TOLERANCE));
            assertThat(bounds.getMaxX())
                .as("right edge matches the notehead glyph's own metadata")
                .isCloseTo(noteheadBBox.right(), within(TOLERANCE));
        }
    }

    @Nested
    class StemDirection {

        /**
         * With no stem layout registered (forced-shortening and beam-lengthening both 0), the
         * rendered stem is exactly {@link SMuFLConstants#STEM_LENGTH_SS} long. An up-stem's tip
         * is therefore the topmost ink at exactly {@code -STEM_LENGTH_SS}.
         */
        @Test
        void testUpStemTopIsExactlyStemLength() {
            var note = crotchet();
            note.setStaffPosition(MIDDLE_STAFF_POSITION);
            note.setDirection(StaffElement.Direction.UP);

            var bounds = requireInkBounds(note);

            assertThat(bounds.getMinY())
                .as("up-stem tip is the topmost ink, at -STEM_LENGTH_SS")
                .isCloseTo(-SMuFLConstants.STEM_LENGTH_SS, within(TOLERANCE));
        }

        /** Mirrors {@link #testUpStemTopIsExactlyStemLength} for a down stem. */
        @Test
        void testDownStemBottomIsExactlyStemLength() {
            var note = crotchet();
            note.setStaffPosition(MIDDLE_STAFF_POSITION);
            note.setDirection(StaffElement.Direction.DOWN);

            var bounds = requireInkBounds(note);

            assertThat(bounds.getMaxY())
                .as("down-stem tip is the bottommost ink, at +STEM_LENGTH_SS")
                .isCloseTo(SMuFLConstants.STEM_LENGTH_SS, within(TOLERANCE));
        }
    }

    @Nested
    class WithAccidental {

        /**
         * An accidental glyph is drawn to the left of the notehead, so a note carrying one has
         * ink extending further left than the same note without.
         */
        @Test
        void testAccidentalExtendsBoundsLeftOfPlainNote() {
            var plain = crotchet();
            plain.setStaffPosition(MIDDLE_STAFF_POSITION);
            plain.setDirection(StaffElement.Direction.UP);

            var withAccidental = crotchet();
            withAccidental.setStaffPosition(MIDDLE_STAFF_POSITION);
            withAccidental.setDirection(StaffElement.Direction.UP);
            withAccidental.setAccidental(StaffElement.Accidental.SHARP);

            var plainBounds = requireInkBounds(plain);
            var accidentalBounds = requireInkBounds(withAccidental);

            assertThat(accidentalBounds.getMinX())
                .as("accidental glyph pushes the left edge further left")
                .isLessThan(plainBounds.getMinX());
        }
    }

    @Nested
    class WithLedgerLines {

        /**
         * A note far enough from the staff to need ledger lines ({@code |sp| > 5}) has ledger
         * lines drawn {@link SMuFLConstants#LEDGER_LINE_LENGTH_FRACTION} of the notehead's own
         * width wider on each side, so the overall ink is strictly wider than the notehead alone.
         */
        @Test
        void testLedgerLinesWidenBoundsBeyondNoteheadWidth() {
            var note = crotchet();
            note.setStaffPosition(ABOVE_LEDGER_STAFF_POSITION);
            note.setDirection(StaffElement.Direction.DOWN);

            var bounds = requireInkBounds(note);
            var noteheadBBox = SMuFLMetadata.requireBBox(SMuFLGlyph.NOTEHEAD_BLACK);

            assertThat(bounds.getWidth())
                .as("ledger lines extend past the notehead's own width")
                .isGreaterThan(noteheadBBox.width());
        }

        /** A note within the staff (no ledger lines needed) has bounds no wider than the notehead. */
        @Test
        void testNoLedgerLinesWhenWithinStaff() {
            var note = crotchet();
            note.setStaffPosition(MIDDLE_STAFF_POSITION);
            note.setDirection(StaffElement.Direction.DOWN);

            var bounds = requireInkBounds(note);
            var noteheadBBox = SMuFLMetadata.requireBBox(SMuFLGlyph.NOTEHEAD_BLACK);

            assertThat(bounds.getWidth())
                .as("no ledger lines within the staff, so width matches the notehead exactly")
                .isCloseTo(noteheadBBox.width(), within(TOLERANCE));
        }
    }

    @Nested
    class GraceNote {

        /**
         * A grace note's stem is {@link SMuFLConstants#GRACE_NOTE_STEM_LENGTH_SS}, strictly
         * shorter than a full-size note's {@link SMuFLConstants#STEM_LENGTH_SS}. Grace notes are
         * always up-stemmed ({@code NoteGeometry.effectiveDirection}), so the topmost ink reaches
         * at least as far as the stem tip (the flag glyph drawn there extends slightly further),
         * but never as far as a full-size stem would reach.
         */
        @Test
        void testGraceStemIsShorterThanFullSizeStem() {
            var grace = graceQuaver();
            grace.setStaffPosition(MIDDLE_STAFF_POSITION);

            var bounds = requireInkBounds(grace);

            assertThat(bounds.getMinY())
                .as("topmost ink reaches at least to the grace stem tip")
                .isLessThanOrEqualTo(-SMuFLConstants.GRACE_NOTE_STEM_LENGTH_SS);
            assertThat(bounds.getMinY())
                .as("but the grace stem (plus flag) never reaches as far as a full-size stem")
                .isGreaterThan(-SMuFLConstants.STEM_LENGTH_SS);
        }
    }

    @Nested
    class WithArticulations {

        /**
         * The accent wedge ({@code ArticulationRenderer.drawAccentGlyph}) is a hand-authored
         * {@code Path2D} with no SMuFL metadata at all — the exact path {@code NoteRenderer.render}
         * would miss if a hand composer were used instead of running the real renderer. A
         * down-stemmed note draws its accent above the staff, extending the topmost ink well
         * past the up-stem-free notehead-only top.
         */
        @Test
        void testAccentPathExtendsBoundsBeyondNoteAlone() {
            var noteOnly = crotchet();
            noteOnly.setStaffPosition(MIDDLE_STAFF_POSITION);
            noteOnly.setDirection(StaffElement.Direction.DOWN);

            var withAccent = crotchet();
            withAccent.setStaffPosition(MIDDLE_STAFF_POSITION);
            withAccent.setDirection(StaffElement.Direction.DOWN);
            withAccent.addArticulation(new Articulation(ArticulationType.ACCENT));

            var noteOnlyBounds = requireInkBounds(noteOnly);
            var accentBounds = requireInkBounds(withAccent);

            assertThat(accentBounds.getMinY())
                .as("the accent wedge, drawn opposite a down stem, extends ink upward")
                .isLessThan(noteOnlyBounds.getMinY());
        }
    }

    @Nested
    class WithFermata {

        /** A fermata is drawn above the note, extending the topmost ink upward. */
        @Test
        void testFermataExtendsBoundsAboveNoteAlone() {
            var noteOnly = crotchet();
            noteOnly.setStaffPosition(MIDDLE_STAFF_POSITION);
            noteOnly.setDirection(StaffElement.Direction.DOWN);

            var withFermata = crotchet();
            withFermata.setStaffPosition(MIDDLE_STAFF_POSITION);
            withFermata.setDirection(StaffElement.Direction.DOWN);
            withFermata.addAttachment(new FermataAttachment());

            var noteOnlyBounds = requireInkBounds(noteOnly);
            var fermataBounds = requireInkBounds(withFermata);

            assertThat(fermataBounds.getMinY())
                .as("the fermata glyph, drawn above the note, extends ink upward")
                .isLessThan(noteOnlyBounds.getMinY());
        }
    }

    // ==========================================================================
    // T12 — non-note branches: NoteRenderer.render delegates and returns early
    // ==========================================================================

    @Nested
    class NonNoteBranches {

        /**
         * A rest delegates to {@link RestRenderer}, which draws only the rest glyph — its ink
         * bounds are exactly the glyph's own SMuFL metadata (no notehead, no stem).
         */
        @Test
        void testRestBoundsMatchRestGlyphMetadata() {
            var rest = crotchetRest();

            var bounds = requireInkBounds(rest);
            var restBBox = SMuFLMetadata.requireBBox(SMuFLGlyph.REST_QUARTER);

            assertThat(bounds.getWidth())
                .as("rest ink width matches the rest glyph's own metadata, not a notehead's")
                .isCloseTo(restBBox.width(), within(TOLERANCE));
            assertThat(bounds.getHeight())
                .as("rest ink height matches the rest glyph's own metadata")
                .isCloseTo(restBBox.height(), within(TOLERANCE));
        }

        /**
         * A single bar line delegates to {@link BarRenderer}, which draws one filled bar of
         * exactly {@link LineThickness#THIN_BARLINE_SS} width — no notehead geometry at all.
         */
        @Test
        void testSingleBarLineBoundsMatchThinBarlineThickness() {
            var barLine = singleBarline();

            var bounds = requireInkBounds(barLine);

            assertThat(bounds.getWidth())
                .as("a single bar line is exactly THIN_BARLINE_SS wide")
                .isCloseTo(LineThickness.THIN_BARLINE_SS, within(TOLERANCE));
        }

        /**
         * A left repeat also delegates to {@link BarRenderer}, drawing a thick bar, a thin bar,
         * and repeat dots — strictly wider than a lone thin bar line, proving the dots (a
         * non-notehead glyph) actually contributed ink.
         */
        @Test
        void testRepeatBoundsAreWiderThanSingleBarLine() {
            var barLine = singleBarline();
            var repeat = repeatLeft();

            var barLineBounds = requireInkBounds(barLine);
            var repeatBounds = requireInkBounds(repeat);

            assertThat(repeatBounds.getWidth())
                .as("thick bar + thin bar + repeat dots is wider than a lone thin bar")
                .isGreaterThan(barLineBounds.getWidth());
        }

        /**
         * A breath mark delegates to a private renderer method that draws only the breath-mark
         * comma glyph — its ink dimensions match that glyph's own SMuFL metadata exactly.
         */
        @Test
        void testBreathMarkBoundsMatchGlyphMetadata() {
            var breathMark = breathMark();

            var bounds = requireInkBounds(breathMark);
            var breathBBox = SMuFLMetadata.requireBBox(SMuFLGlyph.BREATH_MARK_COMMA);

            assertThat(bounds.getWidth())
                .as("breath mark ink width matches its glyph's own metadata")
                .isCloseTo(breathBBox.width(), within(TOLERANCE));
            assertThat(bounds.getHeight())
                .as("breath mark ink height matches its glyph's own metadata")
                .isCloseTo(breathBBox.height(), within(TOLERANCE));
        }
    }
}
