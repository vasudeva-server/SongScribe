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

package songscribe.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.awt.Font;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.Beam;
import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Crescendo;
import songscribe.dom.Duration;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.Ending;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Lyric;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.font.DocumentFonts;
import songscribe.hit.HitPriority;
import songscribe.hit.HitRegion;
import songscribe.hit.HitRegistry;
import songscribe.hit.HitTarget;

/**
 * Covers every kind of hit region: the six that existed before this phase — a note head, a lyric
 * box, a hairpin, an ending, a glissando strip, and the staff line — plus the ones this phase
 * adds: an articulation, the five concrete attachment subtypes, an accidental, a tie, a beam
 * group, a trill, and a tuplet.
 * <p>
 * Each test locates the region registered for a target and probes a point that is inside that
 * region's own shape, rather than asserting coordinates. The coordinates are layout details that
 * shift whenever spacing changes; what must hold is that the region exists, addresses the right
 * object, and wins the point it covers.
 */
class HitRegionBuilderTest extends UnitTest {

    /** Staff width available to the layout engine, in staff spaces. */
    private static final double STAFF_RIGHT_MARGIN_SS = 60.0;

    /** Point size of the lyric font the layout engine needs to measure syllables. */
    private static final int LYRIC_FONT_SIZE_PX = 12;

    /** Staff position of the glissando's source note: on the middle line. */
    private static final int SOURCE_SP = 0;

    /** Staff position of the glissando's target note, below the midline so the line slopes. */
    private static final int TARGET_SP = 4;

    private static final String LYRIC_TEXT = "la";

    /** Three notes in the time of two, undotted — the fixture's tuplets. */
    private static final int TRIPLET_GRADE = 3;
    private static final int TRIPLET_NORMAL_NOTES = 2;
    private static final int NO_DOTS = 0;

    /** The number's ink width is a font measurement, so compare it loosely. */
    private static final double INK_WIDTH_TOLERANCE_SS = 1e-6;

    /**
     * Staff positions of the pair the sloped-bracket test spans. Staff position decreases upward,
     * so this rises; both notes sit above the staff because the stacker floors each bracket
     * endpoint at the staff top, and a pair inside the staff comes out flat however it moves.
     */
    private static final int ASCENDING_START_SP = -4;
    private static final int ASCENDING_END_SP = -8;

    /**
     * How far inside a region's bounding box the sloped-bracket test probes its corners: enough
     * to clear the boundary itself, small enough to stay in the corner being probed.
     */
    private static final double CORNER_PROBE_INSET_SS = 0.1;

    /** A one-by-one square used to synthesize overlapping regions for the priority-order tests. */
    private static final Rectangle2D.Double UNIT_SQUARE_SS = new Rectangle2D.Double(0, 0, 1, 1);

    private StaffElement sourceNote;
    private Ending ending;
    private Crescendo hairpin;
    private Articulation articulation;
    private FermataAttachment fermata;
    private DynamicAttachment dynamic;
    private AnnotationAttachment annotation;
    private TempoChangeAttachment tempoChange;
    private BeatChangeAttachment beatChange;
    private StaffElement accidentalNote;
    private Tie tie;
    private Beam beam;
    private Trill trill;
    private Tuplet tuplet;
    private Tuplet numberOnlyTuplet;
    private HitRegistry registry;

    /**
     * Lays out one line carrying every kind of region this phase registers: an ending over the
     * whole line, a hairpin between the first two notes, a glissando from the first note to the
     * second, a lyric under the first note, an articulation on its own note, all five attachment
     * subtypes stacked on one note, an accidental on its own note, a tie between the two notes
     * inside the repeat, and a beam over a trailing pair of quavers.
     * <p>
     * The element sequence mirrors {@link EndingLineFixture}: an ending is only laid out when a
     * repeat splits its two brackets, so the line needs the barline / notes / repeat / notes /
     * barline shape rather than a bare pair of notes. This phase's new elements are appended
     * inside that same repeat, after the existing pair, so the ending's two-bracket shape is
     * unaffected.
     */
    @BeforeEach
    void setUp() {
        var song = new Song();
        song.setLineWidthSs(UNCONSTRAINED_LINE_WIDTH_SS);
        var line = song.getLine(0);

        var anchorBarline = ElementType.SINGLE_BARLINE.newInstance();
        var split = ElementType.REPEAT_RIGHT.newInstance();
        var endBarline = ElementType.SINGLE_BARLINE.newInstance();
        sourceNote = noteAt(SOURCE_SP);
        var targetNote = noteAt(TARGET_SP);
        var thirdNote = noteAt(SOURCE_SP);
        var fourthNote = noteAt(SOURCE_SP);
        var articulationNote = noteAt(SOURCE_SP);
        var attachmentNote = noteAt(SOURCE_SP);
        accidentalNote = noteAt(SOURCE_SP);
        // Below the midline, so the stems resolve upward and the tuplet over this pair draws as a
        // bare number (Tuplet.isNumberOnly requires beamed-at-both-ends and stems up).
        var beamNote1 = quaverAt(TARGET_SP);
        var beamNote2 = quaverAt(TARGET_SP);
        var trillNote1 = noteAt(SOURCE_SP);
        var trillNote2 = noteAt(SOURCE_SP);
        var tupletNote1 = noteAt(SOURCE_SP);
        var tupletNote2 = noteAt(SOURCE_SP);

        ending = new Ending(anchorBarline, endBarline);
        hairpin = new Crescendo(sourceNote, targetNote);
        articulation = new Articulation(ArticulationType.STACCATO);
        fermata = new FermataAttachment();
        dynamic = new DynamicAttachment(DynamicAttachment.DynamicType.FORTE);
        annotation = new AnnotationAttachment("dolce");
        tempoChange = new TempoChangeAttachment(new Tempo());
        beatChange = new BeatChangeAttachment(new BeatChange(Duration.CROTCHET, Duration.CROTCHET));
        tie = new Tie(thirdNote, fourthNote);
        beam = new Beam(beamNote1, beamNote2);
        trill = new Trill(trillNote1, trillNote2);
        tuplet = new Tuplet(tupletNote1, tupletNote2, TRIPLET_GRADE, TRIPLET_NORMAL_NOTES,
            ElementType.CROTCHET, NO_DOTS);

        // Over the beamed, stem-up pair, so it draws as a bare number with no bracket — the other
        // of the two shapes a tuplet's hit region can take.
        numberOnlyTuplet = new Tuplet(beamNote1, beamNote2, TRIPLET_GRADE, TRIPLET_NORMAL_NOTES,
            ElementType.QUAVER, NO_DOTS);

        song.withoutMutationTracking(() -> {
            line.addElement(anchorBarline);
            line.addElement(sourceNote);
            line.addElement(targetNote);
            line.addElement(split);
            line.addElement(thirdNote);
            line.addElement(fourthNote);
            line.addElement(articulationNote);
            line.addElement(attachmentNote);
            line.addElement(accidentalNote);
            line.addElement(beamNote1);
            line.addElement(beamNote2);
            line.addElement(trillNote1);
            line.addElement(trillNote2);
            line.addElement(tupletNote1);
            line.addElement(tupletNote2);
            line.addElement(endBarline);
            line.addRangeElement(ending);
            line.addRangeElement(hairpin);
            line.addTie(tie);
            line.addBeaming(beam);
            line.addTrill(trill);
            line.addTuplet(tuplet);
            line.addTuplet(numberOnlyTuplet);
            sourceNote.setGlissando();
            sourceNote.setLyricForVerse(
                Lyric.FIRST_VERSE, Lyric.Syllabic.SINGLE, false, LYRIC_TEXT, Lyric.Extend.NONE);
            articulationNote.addArticulation(articulation);
            attachmentNote.addAttachment(fermata);
            attachmentNote.addAttachment(dynamic);
            attachmentNote.addAttachment(annotation);
            attachmentNote.addAttachment(tempoChange);
            attachmentNote.addAttachment(beatChange);
            accidentalNote.setAccidental(StaffElement.Accidental.SHARP);
        });

        registry = engine().layout(line).getHitRegistry();
    }

    /** A stem-up crotchet at the given staff position, so its column extents are deterministic. */
    private static StaffElement noteAt(int staffPosition) {
        return noteAt(staffPosition, true);
    }

    private static StaffElement noteAt(int staffPosition, boolean upper) {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(upper);
        note.setStaffPosition(staffPosition);
        return note;
    }

    /** A stem-up quaver at the given staff position — beamable, unlike {@link #noteAt}. */
    private static StaffElement quaverAt(int staffPosition) {
        var note = ElementType.QUAVER.newInstance();
        note.setUpper(true);
        note.setStaffPosition(staffPosition);
        return note;
    }

    // ======================================================================
    // The six kinds
    // ======================================================================

    @Test
    void testNoteHeadRegistersAnElementTarget() {
        assertRegionWinsItsOwnCenter(new HitTarget.Element(sourceNote));
    }

    @Test
    void testLyricSyllableRegistersALyricTargetForItsVerse() {
        assertRegionWinsItsOwnCenter(new HitTarget.Lyric(sourceNote, Lyric.FIRST_VERSE));
    }

    @Test
    void testHairpinRegistersAHairpinTarget() {
        assertRegionWinsItsOwnCenter(new HitTarget.Hairpin(hairpin));
    }

    @Test
    void testEndingRegistersAnEndingTarget() {
        assertRegionWinsItsOwnCenter(new HitTarget.Ending(ending));
    }

    @Test
    void testGlissandoRegistersASlideTargetForItsOwningNote() {
        assertRegionWinsItsOwnCenter(new HitTarget.Slide(sourceNote));
    }

    @Test
    void testStaffLineRegistersAStaffLineTargetInTheHeader() {
        assertRegionWinsItsOwnCenter(new HitTarget.StaffLine());
    }

    // ======================================================================
    // The five new kinds
    // ======================================================================

    @Test
    void testArticulationRegistersAnArticulationTarget() {
        assertRegionWinsItsOwnCenter(new HitTarget.Articulation(articulation));
    }

    // Each of the five concrete Attachment subtypes is registered explicitly (see
    // HitRegionBuilder.addAttachments), so each needs its own test: a passing test for one
    // subtype says nothing about whether another was wired up at all.

    @Test
    void testFermataRegistersAnAttachmentTarget() {
        assertRegionWinsItsOwnCenter(new HitTarget.Attachment(fermata));
    }

    @Test
    void testDynamicRegistersAnAttachmentTarget() {
        assertRegionWinsItsOwnCenter(new HitTarget.Attachment(dynamic));
    }

    @Test
    void testAnnotationRegistersAnAttachmentTarget() {
        assertRegionWinsItsOwnCenter(new HitTarget.Attachment(annotation));
    }

    @Test
    void testTempoChangeRegistersAnAttachmentTarget() {
        assertRegionWinsItsOwnCenter(new HitTarget.Attachment(tempoChange));
    }

    @Test
    void testBeatChangeRegistersAnAttachmentTarget() {
        assertRegionWinsItsOwnCenter(new HitTarget.Attachment(beatChange));
    }

    @Test
    void testAccidentalRegistersAnAccidentalTargetForItsOwningNote() {
        assertRegionWinsItsOwnCenter(new HitTarget.Accidental(accidentalNote));
    }

    @Test
    void testTieRegistersATieTarget() {
        assertRegionWinsItsOwnCenter(new HitTarget.Tie(tie));
    }

    @Test
    void testBeamRegistersABeamTargetForTheWholeGroup() {
        assertRegionWinsItsOwnCenter(new HitTarget.Beam(beam));
    }

    @Test
    void testTrillRegistersATrillTarget() {
        assertRegionWinsItsOwnCenter(new HitTarget.Trill(trill));
    }

    @Test
    void testTupletRegistersATupletTarget() {
        assertRegionWinsItsOwnCenter(new HitTarget.Tuplet(tuplet));
    }

    @Test
    void testNumberOnlyTupletRegistersATupletTarget() {
        assertRegionWinsItsOwnCenter(new HitTarget.Tuplet(numberOnlyTuplet));
    }

    // A tuplet with no bracket is drawn as a bare number, and only the number is clickable. The
    // reserved box it comes from spans the whole anchor-to-end run, so registering that box
    // unchanged would hand every click in the empty space above the beam to the tuplet.
    @Test
    void testNumberOnlyTupletRegionIsTheNumbersInkAlone() {
        var boundsSs = regionFor(new HitTarget.Tuplet(numberOnlyTuplet)).shapeSs().getBounds2D();

        assertThat(boundsSs.getWidth())
            .isCloseTo(Tuplet.numberInkWidthSs(TRIPLET_GRADE), within(INK_WIDTH_TOLERANCE_SS));
        assertThat(boundsSs.getHeight())
            .isCloseTo(Tuplet.TUPLET_NUMBER_INK_HEIGHT_SS, within(INK_WIDTH_TOLERANCE_SS));
    }

    // A bracket reaches past the notes it spans at both ends, so its region is wider than the
    // anchor-to-end run — the quantity the reservation records.
    @Test
    void testBracketedTupletRegionSpansTheWholeBracket() {
        var region = regionFor(new HitTarget.Tuplet(tuplet));

        assertThat(region.shapeSs().getBounds2D().getWidth())
            .isGreaterThan(Tuplet.numberInkWidthSs(TRIPLET_GRADE));
    }

    // The bracket over a rising contour tilts, and its region is the quad through the four arm
    // ends, not a rect around them. The difference is the two empty corners a rect would take in:
    // the one above the bracket's low end is nowhere near the tuplet's ink, and handing it to the
    // tuplet would steal clicks meant for whatever is stacked above.
    @Test
    void testBracketedTupletRegionSkewsWithTheBracketSlope() {
        var ascendingTuplet = layOutAscendingTuplet();
        var shapeSs = ascendingTuplet.region().shapeSs();
        var boundsSs = shapeSs.getBounds2D();

        // Guards the fixture: over a flat bracket every assertion below holds vacuously.
        assertThat(ascendingTuplet.dySs()).isNegative();

        assertThat(shapeSs.contains(
            boundsSs.getMinX() + CORNER_PROBE_INSET_SS,
            boundsSs.getMinY() + CORNER_PROBE_INSET_SS))
            .as("the empty corner above the bracket's low left end")
            .isFalse();

        assertThat(shapeSs.contains(
            boundsSs.getMinX() + CORNER_PROBE_INSET_SS,
            boundsSs.getMaxY() - CORNER_PROBE_INSET_SS))
            .as("the bottom of the left arm, which reaches the region's lowest point")
            .isTrue();
    }

    // ======================================================================
    // Priority order (regression)
    // ======================================================================

    // The six original kinds occupy genuinely different areas of a real line — header,
    // above-staff, below-staff, notehead — so no real layout puts all six under one point,
    // and probing one wouldn't pin down the *order* they resolve in anyway. Built directly
    // against HitRegistry instead: what must not silently break is the priority ordering
    // itself (HitPriority.LYRIC > ELEMENT > SLIDE > HAIRPIN > ENDING > STAFF_LINE), which
    // reproduces the old LineSelectionHandler hit-tester cascade verbatim.
    @Test
    void testSixOriginalKindsResolveInPriorityOrder() {
        var overlapRegistry = HitRegistry.builder()
            .add(UNIT_SQUARE_SS, new HitTarget.StaffLine(), HitPriority.STAFF_LINE, false)
            .add(UNIT_SQUARE_SS, new HitTarget.Ending(ending), HitPriority.ENDING, false)
            .add(UNIT_SQUARE_SS, new HitTarget.Hairpin(hairpin), HitPriority.HAIRPIN, false)
            .add(UNIT_SQUARE_SS, new HitTarget.Slide(sourceNote), HitPriority.SLIDE, false)
            .add(UNIT_SQUARE_SS, new HitTarget.Element(sourceNote), HitPriority.ELEMENT, false)
            .add(UNIT_SQUARE_SS, new HitTarget.Lyric(sourceNote, Lyric.FIRST_VERSE), HitPriority.LYRIC, false)
            .build();
        var pointInsideAllSix = centerOf(UNIT_SQUARE_SS);

        assertThat(overlapRegistry.hitTest(pointInsideAllSix.getX(), pointInsideAllSix.getY()))
            .isEqualTo(new HitTarget.Lyric(sourceNote, Lyric.FIRST_VERSE));
    }

    // ARTICULATION and ATTACHMENT deliberately outrank TIE (see HitPriority), because a tie's
    // hit shape is its bounding box and is expected to over-cover the notes and markings it
    // spans. This pins that down directly, for the same reason as the test above.
    @Test
    void testArticulationOverlappingATieBoundingBoxResolvesToTheArticulation() {
        var overlapRegistry = HitRegistry.builder()
            .add(UNIT_SQUARE_SS, new HitTarget.Tie(tie), HitPriority.TIE, false)
            .add(UNIT_SQUARE_SS, new HitTarget.Articulation(articulation), HitPriority.ARTICULATION, false)
            .build();
        var pointInsideBoth = centerOf(UNIT_SQUARE_SS);

        assertThat(overlapRegistry.hitTest(pointInsideBoth.getX(), pointInsideBoth.getY()))
            .isEqualTo(new HitTarget.Articulation(articulation));
    }

    // ======================================================================
    // Hover
    // ======================================================================

    // Lyrics are the only kind the mouse-move path may scan, because that path runs on every
    // pixel of pointer motion. Any other kind opting in would put the whole cascade back there.
    @Test
    void testOnlyLyricRegionsAreHoverTestable() {
        assertThat(registry.regions())
            .as("the fixture must register at least one lyric, or this test asserts nothing")
            .anyMatch(region -> region.target() instanceof HitTarget.Lyric);

        for (var region : registry.regions()) {
            assertThat(region.hoverTestable())
                .as("hoverTestable for %s", region.target())
                .isEqualTo(region.target() instanceof HitTarget.Lyric);
        }
    }

    // ======================================================================
    // Helpers
    // ======================================================================

    /**
     * Asserts that {@code target} has a region and that a point inside that region's own shape
     * resolves back to it — so the region is both present and reachable, not shadowed by a
     * higher-priority neighbour.
     */
    private void assertRegionWinsItsOwnCenter(HitTarget target) {
        var center = centerOf(regionFor(target));
        assertThat(registry.hitTest(center.getX(), center.getY())).isEqualTo(target);
    }

    private HitRegion regionFor(HitTarget target) {
        return regionFor(registry, target);
    }

    private static HitRegion regionFor(HitRegistry hitRegistry, HitTarget target) {
        for (var region : hitRegistry.regions()) {
            if (region.target().equals(target)) {
                return region;
            }
        }

        throw new AssertionError("no hit region was registered for " + target);
    }

    /** The bracket slope and hit region of the tuplet {@link #layOutAscendingTuplet} lays out. */
    private record AscendingTuplet(double dySs, HitRegion region) {}

    /**
     * Lays out a line whose one tuplet spans a rising pair, so its bracket tilts.
     * <p>
     * A line of its own rather than two more notes in the shared fixture: that fixture's tuplet
     * spans two notes at the same pitch, and a flat bracket cannot tell a skewed region from the
     * rect this replaced.
     */
    private static AscendingTuplet layOutAscendingTuplet() {
        var song = new Song();
        song.setLineWidthSs(UNCONSTRAINED_LINE_WIDTH_SS);
        var line = song.getLine(0);
        // Stem-down, so the edge the bracket clears is each note head's own top and tracks pitch.
        // Stem-up, both tips would clear the staff by more than the pitch difference between them.
        var startNote = noteAt(ASCENDING_START_SP, false);
        var endNote = noteAt(ASCENDING_END_SP, false);
        var ascendingTuplet = new Tuplet(startNote, endNote, TRIPLET_GRADE, TRIPLET_NORMAL_NOTES,
            ElementType.CROTCHET, NO_DOTS);

        song.withoutMutationTracking(() -> {
            line.addElement(startNote);
            line.addElement(endNote);
            line.addTuplet(ascendingTuplet);
        });

        var layoutResult = engine().layout(line);
        var decorationLayout = layoutResult.getDecorationLayout(ascendingTuplet);

        if (decorationLayout == null) {
            throw new AssertionError("the ascending tuplet was not laid out");
        }

        return new AscendingTuplet(
            decorationLayout.dySs(),
            regionFor(layoutResult.getHitRegistry(), new HitTarget.Tuplet(ascendingTuplet)));
    }

    /**
     * The center of a region's bounding box. It is inside every shape registered here: the
     * rectangles trivially, the glissando strip because its bounding box is centered on the
     * segment's midpoint, which lies on the segment itself, and the bracketed tuplet because its
     * number's box straddles the bracket line at the center X the two arms bracket.
     */
    private static Point2D.Double centerOf(HitRegion region) {
        return centerOf(region.shapeSs().getBounds2D());
    }

    /** The center of a synthetic shape used directly by the priority-order tests. */
    private static Point2D.Double centerOf(Rectangle2D shapeSs) {
        var bounds = shapeSs.getBounds2D();
        return new Point2D.Double(bounds.getCenterX(), bounds.getCenterY());
    }

    private static LayoutEngine engine() {
        return new LayoutEngine(
            LyricRenderMetrics.forFont(new Font(Font.DIALOG, Font.PLAIN, LYRIC_FONT_SIZE_PX)),
            STAFF_RIGHT_MARGIN_SS,
            DocumentFonts.defaultFonts());
    }
}
