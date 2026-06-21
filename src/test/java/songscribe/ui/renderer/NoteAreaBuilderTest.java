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

import module java.desktop;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.layout.NoteGeometry;
import songscribe.dom.StaffElement;

class NoteAreaBuilderTest extends UnitTest {

    private static final NoteAreaBuilder BUILDER = new NoteAreaBuilder();

    // Staff position two ledger lines below the staff (|pos|=8 → (8-4)/2=2 ledger lines)
    private static final int TWO_LEDGERS_BELOW_SP = 8;

    // ======================================================================
    // getOrBuildArea cache tests
    // ======================================================================

    @Test
    void testAreaCacheRebuildsWhenAccidentalChanges() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);
        NoteGeometry.initializeAccidentalWidths();

        var entry1 = BUILDER.getOrBuildArea(note, false);
        note.setAccidental(StaffElement.Accidental.SHARP);
        var entry2 = BUILDER.getOrBuildArea(note, false);

        assertThat(entry2).isNotSameAs(entry1);
    }

    @Test
    void testAreaCacheRebuildsWhenBeamedStateChanges() {
        var note = ElementType.QUAVER.newInstance();
        note.setUpper(true);

        var entry1 = BUILDER.getOrBuildArea(note, false);
        var entry2 = BUILDER.getOrBuildArea(note, true);

        assertThat(entry2).isNotSameAs(entry1);
    }

    @Test
    void testAreaCacheRebuildsWhenDotCountChanges() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);

        var entry1 = BUILDER.getOrBuildArea(note, false);
        note.setDotCount(1);
        var entry2 = BUILDER.getOrBuildArea(note, false);

        assertThat(entry2).isNotSameAs(entry1);
    }

    @Test
    void testAreaCacheRebuildsWhenLedgerLineCountChanges() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);
        note.setStaffPosition(-8); // 2 ledger lines

        var entry1 = BUILDER.getOrBuildArea(note, false);
        note.setStaffPosition(-10); // 3 ledger lines — different count
        var entry2 = BUILDER.getOrBuildArea(note, false);

        assertThat(entry2).isNotSameAs(entry1);
    }

    @Test
    void testAreaCacheRebuildsWhenLedgerLineStatusChanges() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);
        note.setStaffPosition(0); // on staff

        var entry1 = BUILDER.getOrBuildArea(note, false);
        note.setStaffPosition(-8); // above staff, needs ledger lines
        var entry2 = BUILDER.getOrBuildArea(note, false);

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

        var entry1 = BUILDER.getOrBuildArea(note, false);
        note.setStaffPosition(-7); // ledger line at y=0.5 relative to notehead
        var entry2 = BUILDER.getOrBuildArea(note, false);

        assertThat(entry2).isNotSameAs(entry1);
    }

    @Test
    void testAreaCacheRebuildsWhenStemDirectionChanges() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);

        var entry1 = BUILDER.getOrBuildArea(note, false);
        note.setUpper(false);
        var entry2 = BUILDER.getOrBuildArea(note, false);

        assertThat(entry2).isNotSameAs(entry1);
    }

    @Test
    void testAreaCacheRetainsCacheWhenStaffPositionChangesWithinStaff() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);
        note.setStaffPosition(0);

        var entry1 = BUILDER.getOrBuildArea(note, false);
        note.setStaffPosition(4); // still on staff
        var entry2 = BUILDER.getOrBuildArea(note, false);

        assertThat(entry2).isSameAs(entry1);
    }

    @Test
    void testAreaCacheReturnsSameInstanceWhenNoteUnchanged() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);

        var entry1 = BUILDER.getOrBuildArea(note, false);
        var entry2 = BUILDER.getOrBuildArea(note, false);

        assertThat(entry2).isSameAs(entry1);
    }

    // ======================================================================
    // buildNoteArea tests
    // ======================================================================

    @Test
    void testBuildNoteAreaQuarterNoteNoExtras() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);
        var area = BUILDER.buildNoteArea(note, false);
        var bounds = area.getBounds2D();

        assertThat(area.isEmpty()).isFalse();
        assertThat(bounds.getWidth()).isGreaterThan(0.0);
        assertThat(bounds.getHeight()).isGreaterThan(0.0);
    }

    @Test
    void testBuildNoteAreaWithAccidentalExtendsLeft() {
        var noteNoAcc = ElementType.CROTCHET.newInstance();
        noteNoAcc.setUpper(true);
        var areaNoAcc = BUILDER.buildNoteArea(noteNoAcc, false);

        var noteWithAcc = ElementType.CROTCHET.newInstance();
        noteWithAcc.setUpper(true);
        noteWithAcc.setAccidental(StaffElement.Accidental.SHARP);
        // Accidental widths must be initialized before use
        NoteGeometry.initializeAccidentalWidths();
        var areaWithAcc = BUILDER.buildNoteArea(noteWithAcc, false);

        assertThat(areaWithAcc.getBounds2D().getMinX())
            .isLessThan(areaNoAcc.getBounds2D().getMinX());
    }

    @Test
    void testBuildNoteAreaPlacesAccidentalAtReservedLeftEdge() {
        NoteGeometry.initializeAccidentalWidths();

        var noteNoAcc = ElementType.CROTCHET.newInstance();
        noteNoAcc.setUpper(true);
        var leftWithoutAccidental = BUILDER.buildNoteArea(noteNoAcc, false).getBounds2D().getMinX();

        var noteWithAcc = ElementType.CROTCHET.newInstance();
        noteWithAcc.setUpper(true);
        noteWithAcc.setAccidental(StaffElement.Accidental.SHARP);
        var leftWithAccidental = BUILDER.buildNoteArea(noteWithAcc, false).getBounds2D().getMinX();

        var accWidthSs = (double) NoteGeometry.getAccidentalWidthSs(noteWithAcc);
        var expectedLeftSs = -NoteGeometry.ACCIDENTAL_PADDING_SS - accWidthSs;

        // The accidental glyph outline is placed with its box starting at -(padding + width); its
        // left ink edge sits within half a glyph-width of that origin. A regression in the X
        // positioning (wrong sign or magnitude) moves the ink away from this reserved edge, which
        // the earlier directional-only check ("extends left") would not catch.
        assertThat(leftWithAccidental)
            .as("accidental ink starts at its reserved left edge")
            .isCloseTo(expectedLeftSs, within(accWidthSs / 2));
        assertThat(leftWithAccidental)
            .as("accidental extends left of the bare notehead")
            .isLessThan(leftWithoutAccidental);
    }

    @Test
    void testBuildNoteAreaWithDotsIsWider() {
        var noteNoDots = ElementType.CROTCHET.newInstance();
        noteNoDots.setUpper(true);
        var areaNoDots = BUILDER.buildNoteArea(noteNoDots, false);

        var noteWithDots = ElementType.CROTCHET.newInstance();
        noteWithDots.setUpper(true);
        noteWithDots.setDotCount(1);
        var areaWithDots = BUILDER.buildNoteArea(noteWithDots, false);

        assertThat(areaWithDots.getBounds2D().getMaxX())
            .isGreaterThan(areaNoDots.getBounds2D().getMaxX());
    }

    @Test
    void testBuildNoteAreaWithLedgerLinesAboveStaff() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);
        note.setStaffPosition(-8); // above staff, needs ledger lines

        var area = BUILDER.buildNoteArea(note, false);

        // Area should include ledger line rects, making it wider than just the notehead
        var noteOnStaff = ElementType.CROTCHET.newInstance();
        noteOnStaff.setUpper(true);
        noteOnStaff.setStaffPosition(0); // on staff, no ledger lines

        var areaOnStaff = BUILDER.buildNoteArea(noteOnStaff, false);

        // Ledger lines extend beyond notehead on both sides
        assertThat(area.getBounds2D().getWidth())
            .isGreaterThan(areaOnStaff.getBounds2D().getWidth());
    }

    @Test
    void testBuildNoteAreaWithLedgerLinesBelowStaff() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(false);
        note.setStaffPosition(TWO_LEDGERS_BELOW_SP); // below staff, needs ledger lines

        var area = BUILDER.buildNoteArea(note, false);

        // Ledger lines extend beyond notehead on both sides, making the area wider
        var noteOnStaff = ElementType.CROTCHET.newInstance();
        noteOnStaff.setUpper(false);
        noteOnStaff.setStaffPosition(0); // on staff, no ledger lines

        var areaOnStaff = BUILDER.buildNoteArea(noteOnStaff, false);

        assertThat(area.getBounds2D().getWidth())
            .isGreaterThan(areaOnStaff.getBounds2D().getWidth());
    }

    @Test
    void testBuildNoteAreaSemibreveHasNonEmptyBounds() {
        // SEMIBREVE uses NOTEHEAD_WHOLE_SHAPE — verify a distinct (non-empty) area is produced
        var note = ElementType.SEMIBREVE.newInstance();
        note.setUpper(true);
        var area = BUILDER.buildNoteArea(note, false);
        var bounds = area.getBounds2D();

        assertThat(area.isEmpty()).isFalse();
        assertThat(bounds.getWidth()).isGreaterThan(0.0);
        assertThat(bounds.getHeight()).isGreaterThan(0.0);
    }

    @Test
    void testBuildNoteAreaMinimHasNonEmptyBounds() {
        // MINIM uses NOTEHEAD_HALF_SHAPE — verify a distinct (non-empty) area is produced
        var note = ElementType.MINIM.newInstance();
        note.setUpper(true);
        var area = BUILDER.buildNoteArea(note, false);
        var bounds = area.getBounds2D();

        assertThat(area.isEmpty()).isFalse();
        assertThat(bounds.getWidth()).isGreaterThan(0.0);
        assertThat(bounds.getHeight()).isGreaterThan(0.0);
    }

    @Test
    void testBuildNoteAreaGraceNoteHasNonEmptyBounds() {
        // GRACE_QUAVER uses NOTEHEAD_GRACE_SHAPE — verify a distinct (non-empty) area is produced
        var note = ElementType.GRACE_QUAVER.newInstance();
        note.setUpper(true);
        var area = BUILDER.buildNoteArea(note, false);
        var bounds = area.getBounds2D();

        assertThat(area.isEmpty()).isFalse();
        assertThat(bounds.getWidth()).isGreaterThan(0.0);
        assertThat(bounds.getHeight()).isGreaterThan(0.0);
    }

    @Test
    void testBuildNoteAreaBeamedQuaverSuppressesFlag() {
        // A non-beamed quaver stem-up has a flag that extends the area upward.
        // When beamed=true the flag is suppressed, so max-Y is less negative (closer to 0).
        var note = ElementType.QUAVER.newInstance();
        note.setUpper(true);

        var areaWithFlag = BUILDER.buildNoteArea(note, false);
        var areaBeamed = BUILDER.buildNoteArea(note, true);

        // With a flag the area reaches further in the stem-tip direction (smaller max-Y for stem-up)
        assertThat(areaWithFlag.getBounds2D().getMinY())
            .isLessThan(areaBeamed.getBounds2D().getMinY());
    }

    @Test
    void testBuildNoteAreaWithTwoDotsIsWiderThanOne() {
        var noteOneDot = ElementType.CROTCHET.newInstance();
        noteOneDot.setUpper(true);
        noteOneDot.setDotCount(1);
        var areaOneDot = BUILDER.buildNoteArea(noteOneDot, false);

        var noteTwoDots = ElementType.CROTCHET.newInstance();
        noteTwoDots.setUpper(true);
        noteTwoDots.setDotCount(2);
        var areaTwoDots = BUILDER.buildNoteArea(noteTwoDots, false);

        assertThat(areaTwoDots.getBounds2D().getMaxX())
            .isGreaterThan(areaOneDot.getBounds2D().getMaxX());
    }

    // ======================================================================
    // createOffsetArea tests
    // ======================================================================

    @Test
    void testCreateOffsetAreaContainsOriginal() {
        var square = new Rectangle2D.Double(-0.5, -0.5, 1.0, 1.0);
        var offsetSs = 0.3f;
        var offsetArea = NoteAreaBuilder.createOffsetArea(square, offsetSs);

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
        var offsetSs = 0.3f;
        var offsetArea = NoteAreaBuilder.createOffsetArea(square, offsetSs);
        var offsetBounds = offsetArea.getBounds2D();
        var origBounds = square.getBounds2D();

        // Offset bounds should be larger on each side by approximately offsetSs
        assertThat(offsetBounds.getMinX()).isLessThan(origBounds.getMinX() - offsetSs * 0.5);
        assertThat(offsetBounds.getMaxX()).isGreaterThan(origBounds.getMaxX() + offsetSs * 0.5);
        assertThat(offsetBounds.getMinY()).isLessThan(origBounds.getMinY() - offsetSs * 0.5);
        assertThat(offsetBounds.getMaxY()).isGreaterThan(origBounds.getMaxY() + offsetSs * 0.5);
    }

}
