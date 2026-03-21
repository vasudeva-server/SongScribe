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

import java.util.Objects;

import module java.desktop;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;

class GlissandoRendererTest extends UnitTest {

    /** Step size used as both the search step parameter and assertion tolerance basis. */
    private static final double STEP_SS = 0.1;
    private static final GlissandoRenderer RENDERER = GlissandoRenderer.getInstance();

    // ======================================================================
    // getOrBuildArea cache tests
    // ======================================================================

    @Test
    void testAreaCacheRebuildsWhenAccidentalChanges() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);
        NoteRenderer.initializeAccidentalWidths(
            new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                .createGraphics());

        var entry1 = RENDERER.getOrBuildArea(note, false);
        note.setAccidental(StaffElement.Accidental.SHARP);
        var entry2 = RENDERER.getOrBuildArea(note, false);

        assertThat(entry2).isNotSameAs(entry1);
    }

    @Test
    void testAreaCacheRebuildsWhenBeamedStateChanges() {
        var note = ElementType.QUAVER.newInstance();
        note.setUpper(true);

        var entry1 = RENDERER.getOrBuildArea(note, false);
        var entry2 = RENDERER.getOrBuildArea(note, true);

        assertThat(entry2).isNotSameAs(entry1);
    }

    @Test
    void testAreaCacheRebuildsWhenDotCountChanges() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);

        var entry1 = RENDERER.getOrBuildArea(note, false);
        note.setDotCount(1);
        var entry2 = RENDERER.getOrBuildArea(note, false);

        assertThat(entry2).isNotSameAs(entry1);
    }

    @Test
    void testAreaCacheRebuildsWhenLedgerLineCountChanges() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);
        note.setStaffPosition(-8); // 2 ledger lines

        var entry1 = RENDERER.getOrBuildArea(note, false);
        note.setStaffPosition(-10); // 3 ledger lines — different count
        var entry2 = RENDERER.getOrBuildArea(note, false);

        assertThat(entry2).isNotSameAs(entry1);
    }

    @Test
    void testAreaCacheRebuildsWhenLedgerLineStatusChanges() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);
        note.setStaffPosition(0); // on staff

        var entry1 = RENDERER.getOrBuildArea(note, false);
        note.setStaffPosition(-8); // above staff, needs ledger lines
        var entry2 = RENDERER.getOrBuildArea(note, false);

        assertThat(entry2).isNotSameAs(entry1);
    }

    @Test
    void testAreaCacheRebuildsWhenStaffPositionChangesWithinLedgerTier() {
        // Staff positions -6 and -7 both have 1 ledger line, but the ledger line's
        // y-offset relative to the notehead differs (0.0 vs 0.5 ss), so the area
        // shape is different and the cache must rebuild.
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);
        note.setStaffPosition(-6); // ledger line at y=0.0 relative to notehead

        var entry1 = RENDERER.getOrBuildArea(note, false);
        note.setStaffPosition(-7); // ledger line at y=0.5 relative to notehead
        var entry2 = RENDERER.getOrBuildArea(note, false);

        assertThat(entry2).isNotSameAs(entry1);
    }

    @Test
    void testAreaCacheRebuildsWhenStemDirectionChanges() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);

        var entry1 = RENDERER.getOrBuildArea(note, false);
        note.setUpper(false);
        var entry2 = RENDERER.getOrBuildArea(note, false);

        assertThat(entry2).isNotSameAs(entry1);
    }

    @Test
    void testAreaCacheRetainsCacheWhenStaffPositionChangesWithinStaff() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);
        note.setStaffPosition(0);

        var entry1 = RENDERER.getOrBuildArea(note, false);
        note.setStaffPosition(4); // still on staff
        var entry2 = RENDERER.getOrBuildArea(note, false);

        assertThat(entry2).isSameAs(entry1);
    }

    @Test
    void testAreaCacheReturnsSameInstanceWhenNoteUnchanged() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);

        var entry1 = RENDERER.getOrBuildArea(note, false);
        var entry2 = RENDERER.getOrBuildArea(note, false);

        assertThat(entry2).isSameAs(entry1);
    }

    // ======================================================================
    // buildNoteArea tests
    // ======================================================================

    @Test
    void testBuildNoteAreaQuarterNoteNoExtras() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);
        var area = RENDERER.buildNoteArea(note, false);

        assertThat(area.isEmpty()).isFalse();
    }

    @Test
    void testBuildNoteAreaWithAccidentalExtendsLeft() {
        var noteNoAcc = ElementType.CROTCHET.newInstance();
        noteNoAcc.setUpper(true);
        var areaNoAcc = RENDERER.buildNoteArea(noteNoAcc, false);

        var noteWithAcc = ElementType.CROTCHET.newInstance();
        noteWithAcc.setUpper(true);
        noteWithAcc.setAccidental(StaffElement.Accidental.SHARP);
        // Accidental widths must be initialized before use
        NoteRenderer.initializeAccidentalWidths(
            new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                .createGraphics());
        var areaWithAcc = RENDERER.buildNoteArea(noteWithAcc, false);

        assertThat(areaWithAcc.getBounds2D().getMinX())
            .isLessThan(areaNoAcc.getBounds2D().getMinX());
    }

    @Test
    void testBuildNoteAreaWithDotsIsWider() {
        var noteNoDots = ElementType.CROTCHET.newInstance();
        noteNoDots.setUpper(true);
        var areaNoDots = RENDERER.buildNoteArea(noteNoDots, false);

        var noteWithDots = ElementType.CROTCHET.newInstance();
        noteWithDots.setUpper(true);
        noteWithDots.setDotCount(1);
        var areaWithDots = RENDERER.buildNoteArea(noteWithDots, false);

        assertThat(areaWithDots.getBounds2D().getMaxX())
            .isGreaterThan(areaNoDots.getBounds2D().getMaxX());
    }

    @Test
    void testBuildNoteAreaWithLedgerLinesAboveStaff() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);
        note.setStaffPosition(-8); // above staff, needs ledger lines

        var area = RENDERER.buildNoteArea(note, false);

        // Area should include ledger line rects, making it wider than just the notehead
        var noteOnStaff = ElementType.CROTCHET.newInstance();
        noteOnStaff.setUpper(true);
        noteOnStaff.setStaffPosition(0); // on staff, no ledger lines

        var areaOnStaff = RENDERER.buildNoteArea(noteOnStaff, false);

        // Ledger lines extend beyond notehead on both sides
        assertThat(area.getBounds2D().getWidth())
            .isGreaterThan(areaOnStaff.getBounds2D().getWidth());
    }

    @Test
    void testBuildNoteAreaWithLedgerLinesBelowStaff() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(false);
        note.setStaffPosition(8); // below staff, needs ledger lines

        var area = RENDERER.buildNoteArea(note, false);
        assertThat(area.isEmpty()).isFalse();
    }

    @Test
    void testBuildNoteAreaWithTwoDotsIsWiderThanOne() {
        var noteOneDot = ElementType.CROTCHET.newInstance();
        noteOneDot.setUpper(true);
        noteOneDot.setDotCount(1);
        var areaOneDot = RENDERER.buildNoteArea(noteOneDot, false);

        var noteTwoDots = ElementType.CROTCHET.newInstance();
        noteTwoDots.setUpper(true);
        noteTwoDots.setDotCount(2);
        var areaTwoDots = RENDERER.buildNoteArea(noteTwoDots, false);

        assertThat(areaTwoDots.getBounds2D().getMaxX())
            .isGreaterThan(areaOneDot.getBounds2D().getMaxX());
    }

    // ======================================================================
    // computeFarBoundsT tests
    // ======================================================================

    @Test
    void testComputeFarBoundsTDiagonal() {
        // Bounds from (0, 0) to (4, 2), center at (2, 1), going at 45° (nx=ny=1/√2)
        // tx = (4-2) / (1/√2) = 2√2 ≈ 2.83
        // ty = (2-1) / (1/√2) = √2 ≈ 1.41
        // Expected: min = ty ≈ 1.41
        var bounds = new Rectangle2D.Double(0, 0, 4, 2);
        double inv = 1.0 / Math.sqrt(2);
        double t = GlissandoRenderer.computeFarBoundsT(bounds, 2, 1, inv, inv);
        assertThat(t).isCloseTo(Math.sqrt(2), within(STEP_SS));
    }

    @Test
    void testComputeFarBoundsTRightward() {
        // Bounds from (-2, -1) to (3, 1), center at (0, 0), going right (nx=1, ny=0)
        // Expected: t = (maxX - cx) / nx = (3 - 0) / 1 = 3.0
        var bounds = new Rectangle2D.Double(-2, -1, 5, 2);
        double t = GlissandoRenderer.computeFarBoundsT(bounds, 0, 0, 1, 0);
        assertThat(t).isCloseTo(3.0, within(STEP_SS));
    }

    // ======================================================================
    // createOffsetArea tests
    // ======================================================================

    @Test
    void testCreateOffsetAreaContainsOriginal() {
        var square = new Rectangle2D.Double(-0.5, -0.5, 1.0, 1.0);
        float offsetSs = 0.3f;
        var offsetArea = GlissandoRenderer.createOffsetArea(square, offsetSs);

        // All corners of the original should be contained in the offset area
        assertThat(offsetArea.contains(-0.5, -0.5)).isTrue();
        assertThat(offsetArea.contains(0.5, -0.5)).isTrue();
        assertThat(offsetArea.contains(-0.5, 0.5)).isTrue();
        assertThat(offsetArea.contains(0.5, 0.5)).isTrue();
    }

    @Test
    void testCreateOffsetAreaExpandsShape() {
        // A unit square centered at origin
        var square = new Rectangle2D.Double(-0.5, -0.5, 1.0, 1.0);
        float offsetSs = 0.3f;
        var offsetArea = GlissandoRenderer.createOffsetArea(square, offsetSs);
        var offsetBounds = offsetArea.getBounds2D();
        var origBounds = square.getBounds2D();

        // Offset bounds should be larger on each side by approximately offsetSs
        assertThat(offsetBounds.getMinX()).isLessThan(origBounds.getMinX() - offsetSs * 0.5);
        assertThat(offsetBounds.getMaxX()).isGreaterThan(origBounds.getMaxX() + offsetSs * 0.5);
        assertThat(offsetBounds.getMinY()).isLessThan(origBounds.getMinY() - offsetSs * 0.5);
        assertThat(offsetBounds.getMaxY()).isGreaterThan(origBounds.getMaxY() + offsetSs * 0.5);
    }

    // ======================================================================
    // findNoteAreaEntryPoint tests
    // ======================================================================

    @Test
    void testFindEntryPoint_circle() {
        // Circle centered at (0, 0) with radius 2; offsetSs baked into offset area
        var circle = new Area(new Ellipse2D.Double(-2, -2, 4, 4));
        float offsetSs = 0.3f;
        var offsetArea = GlissandoRenderer.createOffsetArea(circle, offsetSs);
        var offsetBounds = offsetArea.getBounds2D();

        // Direction: right (nx=1, ny=0)
        var endpoint = GlissandoRenderer.findNoteAreaEntryPoint(
            offsetArea, offsetBounds, 0, 0, 1, 0, STEP_SS);

        // Endpoint should be just past the offset area boundary (radius + offsetSs)
        assertThat(endpoint.x).isGreaterThanOrEqualTo(2.0 + offsetSs - STEP_SS);
        assertThat(endpoint.y).isCloseTo(0.0, within(STEP_SS * 2));
    }

    @Test
    void testFindEntryPoint_compositeArea() {
        // Rectangle + circle union; endpoint should be past the furthest component
        var composite = new Area(new Rectangle2D.Double(0, 0, 4, 2));
        composite.add(new Area(new Ellipse2D.Double(3, -1, 4, 4)));
        float offsetSs = 0.3f;
        var offsetArea = GlissandoRenderer.createOffsetArea(composite, offsetSs);
        var offsetBounds = offsetArea.getBounds2D();

        // Direction: right from center (2, 1)
        var endpoint = GlissandoRenderer.findNoteAreaEntryPoint(
            offsetArea, offsetBounds, 2, 1, 1, 0, STEP_SS);

        // The circle extends to x=7, offset boundary ~7+offsetSs; endpoint just past that
        assertThat(endpoint.x).isGreaterThanOrEqualTo(7.0);
        assertThat(endpoint.y).isCloseTo(1.0, within(STEP_SS * 2));
    }

    @Test
    void testFindEntryPoint_fallback() {
        // Center is outside the offset area — should return center
        var farRect = new Area(new Rectangle2D.Double(5, 5, 2, 2));
        float offsetSs = 0.3f;
        var offsetArea = GlissandoRenderer.createOffsetArea(farRect, offsetSs);
        var offsetBounds = offsetArea.getBounds2D();

        var endpoint = GlissandoRenderer.findNoteAreaEntryPoint(
            offsetArea, offsetBounds, 0, 0, 1, 0, STEP_SS);

        // Fallback: center returned
        assertThat(endpoint.x).isCloseTo(0.0, within(STEP_SS));
        assertThat(endpoint.y).isCloseTo(0.0, within(STEP_SS));
    }

    @Test
    void testFindEntryPoint_zeroDirection() {
        var rect = new Area(new Rectangle2D.Double(-1, -1, 2, 2));
        float offsetSs = 0.3f;
        var offsetArea = GlissandoRenderer.createOffsetArea(rect, offsetSs);
        var offsetBounds = offsetArea.getBounds2D();

        // nx=ny=0 should trigger the zero-direction guard and return center
        var endpoint = GlissandoRenderer.findNoteAreaEntryPoint(
            offsetArea, offsetBounds, 0, 0, 0, 0, STEP_SS);

        assertThat(endpoint.x).isCloseTo(0.0, within(STEP_SS));
        assertThat(endpoint.y).isCloseTo(0.0, within(STEP_SS));
    }

    // ======================================================================
    // getLedgerLineCount() boundary tests
    // ======================================================================

    @Test
    void testGetLedgerLineCountAboveStaff() {
        var note = ElementType.CROTCHET.newInstance();

        // -6 → 1 ledger line, -7 → 1, -8 → 2, -10 → 3
        note.setStaffPosition(-6);
        assertThat(note.getLedgerLineCount()).isEqualTo(1);

        note.setStaffPosition(-7);
        assertThat(note.getLedgerLineCount()).isEqualTo(1);

        note.setStaffPosition(-8);
        assertThat(note.getLedgerLineCount()).isEqualTo(2);

        note.setStaffPosition(-10);
        assertThat(note.getLedgerLineCount()).isEqualTo(3);
    }

    @Test
    void testGetLedgerLineCountBelowStaff() {
        var note = ElementType.CROTCHET.newInstance();

        // +6 → 1 ledger line, +7 → 1, +8 → 2, +10 → 3
        note.setStaffPosition(6);
        assertThat(note.getLedgerLineCount()).isEqualTo(1);

        note.setStaffPosition(7);
        assertThat(note.getLedgerLineCount()).isEqualTo(1);

        note.setStaffPosition(8);
        assertThat(note.getLedgerLineCount()).isEqualTo(2);

        note.setStaffPosition(10);
        assertThat(note.getLedgerLineCount()).isEqualTo(3);
    }

    @Test
    void testGetLedgerLineCountOnStaff() {
        var note = ElementType.CROTCHET.newInstance();

        // Positions within the staff: 0, ±1, ±2, ±3, ±4, ±5
        for (int sp = -5; sp <= 5; sp++) {
            note.setStaffPosition(sp);
            assertThat(note.getLedgerLineCount())
                .as("staffPosition %d", sp)
                .isEqualTo(0);
        }
    }

    // ======================================================================
    // hitTestGlissando tests
    // ======================================================================

    /**
     * Injects synthetic cached geometry onto a glissando, bypassing the render pass.
     * Angle is in degrees for readability; the method converts to radians internally.
     */
    private static void setCachedGeometry(
        StaffElement.Glissando glissando,
        double startXSs, double startYSs,
        double angleDeg, double lengthSs) {
        var angleRad = Math.toRadians(angleDeg);
        glissando.cachedStartX = startXSs;
        glissando.cachedStartY = startYSs;
        glissando.cachedAngle = angleRad;
        glissando.cachedCos = Math.cos(angleRad);
        glissando.cachedSin = Math.sin(angleRad);
        glissando.cachedLength = lengthSs;
        glissando.hasCachedGeometry = true;
    }

    @Test
    void testHitTestGlissando_diagonalLine_returnsNoteIndex() {
        // 45° glissando from (0, 0), length 10; midpoint in world coords: (5·cos45°, 5·sin45°)
        var line = makeTwoNoteLineWithGlissando(0, StaffElement.Accidental.NONE, -2, StaffElement.Accidental.NONE);
        setCachedGeometry(Objects.requireNonNull(line.getElement(0).getGlissando()), 0.0, 0.0, 45.0, 10.0);

        double mid = 5.0 * Math.cos(Math.toRadians(45.0));
        assertThat(RENDERER.hitTestGlissando(mid, mid, line)).isEqualTo(0);
    }

    @Test
    void testHitTestGlissando_noCachedGeometry_skipped() {
        // Note has a Glissando object but hasCachedGeometry is false (default) — must be skipped
        var line = makeTwoNoteLineWithGlissando(0, StaffElement.Accidental.NONE, -2, StaffElement.Accidental.NONE);

        assertThat(RENDERER.hitTestGlissando(10.0, 3.0, line)).isEqualTo(-1);
    }

    @Test
    void testHitTestGlissando_pointAfterEnd_returnsMinusOne() {
        var line = makeTwoNoteLineWithGlissando(0, StaffElement.Accidental.NONE, -2, StaffElement.Accidental.NONE);
        setCachedGeometry(Objects.requireNonNull(line.getElement(0).getGlissando()), 5.0, 3.0, 0.0, 10.0);

        // localX = 15.1 - 5.0 = 10.1 > cachedLength (10.0)
        assertThat(RENDERER.hitTestGlissando(15.1, 3.0, line)).isEqualTo(-1);
    }

    @Test
    void testHitTestGlissando_pointBeforeStart_returnsMinusOne() {
        var line = makeTwoNoteLineWithGlissando(0, StaffElement.Accidental.NONE, -2, StaffElement.Accidental.NONE);
        setCachedGeometry(Objects.requireNonNull(line.getElement(0).getGlissando()), 5.0, 3.0, 0.0, 10.0);

        // localX = 4.9 - 5.0 = -0.1 < 0
        assertThat(RENDERER.hitTestGlissando(4.9, 3.0, line)).isEqualTo(-1);
    }

    @Test
    void testHitTestGlissando_pointBesideLine_returnsMinusOne() {
        // Same glissando, but click is 1.0 ss above (> halfHitSs = 0.5)
        var line = makeTwoNoteLineWithGlissando(0, StaffElement.Accidental.NONE, -2, StaffElement.Accidental.NONE);
        setCachedGeometry(Objects.requireNonNull(line.getElement(0).getGlissando()), 5.0, 3.0, 0.0, 10.0);

        assertThat(RENDERER.hitTestGlissando(10.0, 4.0, line)).isEqualTo(-1);
    }

    @Test
    void testHitTestGlissando_pointOnLine_returnsNoteIndex() {
        // Horizontal glissando (angle=0) from (5.0, 3.0) with length 10.0
        var line = makeTwoNoteLineWithGlissando(0, StaffElement.Accidental.NONE, -2, StaffElement.Accidental.NONE);
        setCachedGeometry(Objects.requireNonNull(line.getElement(0).getGlissando()), 5.0, 3.0, 0.0, 10.0);

        // Click at the midpoint: localX=5, localY=0 — well within hit bounds
        assertThat(RENDERER.hitTestGlissando(10.0, 3.0, line)).isEqualTo(0);
    }

    @Test
    void testHitTestGlissando_secondNoteGlissando_returnsCorrectIndex() {
        // Three-note line; only note at index 1 has a cached glissando
        var note0 = ElementType.CROTCHET.newInstance();
        note0.setUpper(true);
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setUpper(true);
        note1.setGlissando(StaffElement.Glissando.Type.CONNECTED);
        var note2 = ElementType.CROTCHET.newInstance();
        note2.setUpper(true);

        var line = new Line();
        line.addElement(note0);
        line.addElement(note1);
        line.addElement(note2);

        setCachedGeometry(Objects.requireNonNull(note1.getGlissando()), 5.0, 3.0, 0.0, 10.0);

        assertThat(RENDERER.hitTestGlissando(10.0, 3.0, line)).isEqualTo(1);
    }

    // ======================================================================
    // Unison connected glissando tests
    //
    // These verify that getPitch() (MIDI pitch) — not staff position —
    // governs whether a connected glissando is considered unison.
    // ======================================================================

    /**
     * Creates a line with two notes at the given staff positions and accidentals.
     */
    private static Line makeTwoNoteLineWithGlissando(
        int staffPos1, StaffElement.Accidental acc1,
        int staffPos2, StaffElement.Accidental acc2) {
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setUpper(true);
        note1.setStaffPosition(staffPos1);
        note1.setAccidental(acc1);
        note1.setGlissando(StaffElement.Glissando.Type.CONNECTED);

        var note2 = ElementType.CROTCHET.newInstance();
        note2.setUpper(true);
        note2.setStaffPosition(staffPos2);
        note2.setAccidental(acc2);

        var line = new Line();
        line.addElement(note1);
        line.addElement(note2);

        return line;
    }

    @Test
    void testNonUnisonConnectedGlissandoDifferentPosition() {
        // Two notes at different staff positions — different MIDI pitch
        var line = makeTwoNoteLineWithGlissando(0, StaffElement.Accidental.NONE, -2, StaffElement.Accidental.NONE);
        var note1 = line.getElement(0);
        var note2 = line.getElement(1);

        assertThat(note1.getPitch()).isNotEqualTo(note2.getPitch());
    }

    @Test
    void testNonUnisonConnectedGlissandoSamePositionDifferentAccidental() {
        // Same staff position but natural vs sharp — different MIDI pitch
        // This confirms getPitch() is used, not getStaffPosition()
        var line = makeTwoNoteLineWithGlissando(0, StaffElement.Accidental.NATURAL, 0, StaffElement.Accidental.SHARP);
        var note1 = line.getElement(0);
        var note2 = line.getElement(1);

        assertThat(note1.getStaffPosition()).isEqualTo(note2.getStaffPosition());
        assertThat(note1.getPitch()).isNotEqualTo(note2.getPitch());
    }

    @Test
    void testUnisonConnectedGlissandoSamePitch() {
        // Two notes at same staff position, no accidentals — same MIDI pitch
        var line = makeTwoNoteLineWithGlissando(0, StaffElement.Accidental.NONE, 0, StaffElement.Accidental.NONE);
        var note1 = line.getElement(0);
        var note2 = line.getElement(1);

        assertThat(note1.getPitch()).isEqualTo(note2.getPitch());
    }
}
