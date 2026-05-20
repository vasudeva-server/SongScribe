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

import module java.desktop;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;

class NoteAreaBuilderTest extends UnitTest {

    private static final NoteAreaBuilder BUILDER = new NoteAreaBuilder();

    // ======================================================================
    // getOrBuildArea cache tests
    // ======================================================================

    @Test
    void testAreaCacheRebuildsWhenAccidentalChanges() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);
        NoteRenderer.initializeAccidentalWidths();

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

        assertThat(area.isEmpty()).isFalse();
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
        NoteRenderer.initializeAccidentalWidths();
        var areaWithAcc = BUILDER.buildNoteArea(noteWithAcc, false);

        assertThat(areaWithAcc.getBounds2D().getMinX())
            .isLessThan(areaNoAcc.getBounds2D().getMinX());
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
        note.setStaffPosition(8); // below staff, needs ledger lines

        var area = BUILDER.buildNoteArea(note, false);
        assertThat(area.isEmpty()).isFalse();
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
        for (var sp = -5; sp <= 5; sp++) {
            note.setStaffPosition(sp);
            assertThat(note.getLedgerLineCount())
                .as("staffPosition %d", sp)
                .isEqualTo(0);
        }
    }

}
