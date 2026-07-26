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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.layout.NoteGeometry;
import songscribe.smufl.SMuFLMetadata;

class NoteColumnGeometryTest extends UnitTest {

    /** Floating-point tolerance for staff-space comparisons. */
    private static final double TOLERANCE_SS = 0.001;

    /** Staff position two ledger lines below the staff (|pos| = 8 → 2 ledger lines). */
    private static final int TWO_LEDGERS_BELOW_SP = 8;

    @BeforeAll
    static void setUpAccidentals() {
        NoteGeometry.initializeAccidentalWidths();
    }

    // ======================================================================
    // Concrete-value anchor — stem-up crotchet
    // ======================================================================

    @Test
    void testCrotchetUpStem_rightSsEqualsStemRightEdge() {
        // A plain crotchet (stem up) ends at the stem's right edge, which Bravura places flush
        // with the notehead's right edge (see testStemOuterEdgesAreFlushWithNoteheadOuterEdges).
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);

        var extent = NoteColumnGeometry.extentSs(note, false);
        var stemGeom = NoteGeometry.computeBaseStemGeometry(ElementType.CROTCHET, StaffElement.Direction.UP);
        var expectedRight = stemGeom.stemLeftXSs() + NoteGeometry.STEM_WIDTH_SS;

        assertThat(extent.rightSs()).isCloseTo(expectedRight, within(TOLERANCE_SS));
    }

    // ======================================================================
    // No-stem and stem-without-flag cases
    // ======================================================================

    @Test
    void testSemibreve_noStem_noteheadDrivenRight() {
        // A semibreve has no stem and no flag: the right edge is the bare notehead right edge.
        // The expectation is read straight from the font metadata rather than from
        // getGlyphRightEdgeSs, which is the very lookup the production path uses — deriving both
        // sides from it would hide a whole-note-specific error in that lookup.
        var note = ElementType.SEMIBREVE.newInstance();
        note.setUpper(true);

        var extent = NoteColumnGeometry.extentSs(note, false);
        var noteheadBBox = SMuFLMetadata.requireBBox(ElementType.SEMIBREVE.requireSMuFLGlyph());

        assertThat(extent.rightSs()).isCloseTo(noteheadBBox.right(), within(TOLERANCE_SS));
        assertThat(extent.leftSs()).isLessThan(extent.rightSs());
    }

    @Test
    void testMinim_hasStemButNoFlag_rightSsEqualsStemRightEdge() {
        // A minim (half note) has a stem but no flag. The stem's right edge is flush with the
        // notehead's (see testStemOuterEdgesAreFlushWithNoteheadOuterEdges), so this pins the
        // shared value rather than proving the stem widened anything.
        var note = ElementType.MINIM.newInstance();
        note.setUpper(true);

        var extent = NoteColumnGeometry.extentSs(note, false);
        var stemGeom = NoteGeometry.computeBaseStemGeometry(ElementType.MINIM, StaffElement.Direction.UP);
        var expectedRight = stemGeom.stemLeftXSs() + NoteGeometry.STEM_WIDTH_SS;

        assertThat(extent.rightSs()).isCloseTo(expectedRight, within(TOLERANCE_SS));
    }

    // ======================================================================
    // Stem-down case
    // ======================================================================

    @Test
    void testCrotchetDownStem_leftSsMatchesUpStemAtNoteheadLeft() {
        var upStemNote = ElementType.CROTCHET.newInstance();
        upStemNote.setUpper(true);

        var downStemNote = ElementType.CROTCHET.newInstance();
        downStemNote.setUpper(false);

        var upExtent = NoteColumnGeometry.extentSs(upStemNote, false);
        var downExtent = NoteColumnGeometry.extentSs(downStemNote, false);

        // Noteheads align across stem directions and neither stem protrudes past the notehead's
        // outer edge, so both left extents sit at the notehead left edge (0).
        assertThat(downExtent.leftSs()).isCloseTo(0.0, within(TOLERANCE_SS));
        assertThat(downExtent.leftSs()).isCloseTo(upExtent.leftSs(), within(TOLERANCE_SS));
    }

    // ======================================================================
    // The font premise the stem-widening guards rest on
    // ======================================================================

    @Test
    void testStemOuterEdgesAreFlushWithNoteheadOuterEdges() {
        // extentSs widens the column only when a stem sticks out past the notehead. With Bravura
        // no stem ever does, so both guards are inert and the tests above that name the stem as
        // the driving edge are really pinning a value the notehead already reached. Pinning the
        // premise here means a font or anchor change that lets a stem protrude fails loudly, at
        // which point the widening path becomes live and deserves its own test.
        for (var noteType : ElementType.values()) {
            if (!noteType.isNoteWithStem()) {
                continue;
            }

            var noteheadBBox = SMuFLMetadata.requireBBox(noteType.requireSMuFLGlyph());
            var upStemRightSs =
                NoteGeometry.computeBaseStemGeometry(noteType, StaffElement.Direction.UP).stemLeftXSs()
                    + NoteGeometry.STEM_WIDTH_SS;
            var downStemLeftSs =
                NoteGeometry.computeBaseStemGeometry(noteType, StaffElement.Direction.DOWN).stemLeftXSs();

            // Neither stem reaches past the notehead bbox that drives the column, in either
            // direction — this is what keeps the widening guards from firing.
            assertThat(upStemRightSs)
                .as("%s up-stem right edge within notehead right edge", noteType)
                .isLessThanOrEqualTo(noteheadBBox.right() + TOLERANCE_SS);
            assertThat(downStemLeftSs)
                .as("%s down-stem left edge within notehead left edge", noteType)
                .isGreaterThanOrEqualTo(noteheadBBox.left() - TOLERANCE_SS);

            // Grace notes are the one type where the two do not merely fail to protrude but sit
            // far apart: their stem anchors are scaled to the drawn small notehead while the
            // column still measures the full-size noteheadBlack bbox.
            if (noteType.isGraceNote()) {
                continue;
            }

            assertThat(upStemRightSs)
                .as("%s up-stem right edge flush with notehead right edge", noteType)
                .isCloseTo(noteheadBBox.right(), within(TOLERANCE_SS));
            assertThat(downStemLeftSs)
                .as("%s down-stem left edge flush with notehead left edge", noteType)
                .isCloseTo(noteheadBBox.left(), within(TOLERANCE_SS));
        }
    }

    // ======================================================================
    // Ordering invariants
    // ======================================================================

    @Test
    void testDottedNote_rightSsGreaterThanPlain() {
        var plain = ElementType.CROTCHET.newInstance();
        plain.setUpper(true);

        var dotted = ElementType.CROTCHET.newInstance();
        dotted.setUpper(true);
        dotted.setDotCount(1);

        var plainExtent = NoteColumnGeometry.extentSs(plain, false);
        var dottedExtent = NoteColumnGeometry.extentSs(dotted, false);

        assertThat(dottedExtent.rightSs()).isGreaterThan(plainExtent.rightSs());
    }

    @Test
    void testAccidentalNote_leftSsLessThanPlain() {
        var plain = ElementType.CROTCHET.newInstance();
        plain.setUpper(true);

        var withAccidental = ElementType.CROTCHET.newInstance();
        withAccidental.setUpper(true);
        withAccidental.setAccidental(StaffElement.Accidental.SHARP);

        var plainExtent = NoteColumnGeometry.extentSs(plain, false);
        var accidentalExtent = NoteColumnGeometry.extentSs(withAccidental, false);

        assertThat(accidentalExtent.leftSs()).isLessThan(plainExtent.leftSs());
    }

    @Test
    void testLedgerNote_extentIgnoresLedgerLines() {
        // Ledger lines are excluded from the column extent; a ledger note and an on-staff note
        // of the same type and stem direction must produce identical extents.
        var onStaff = ElementType.CROTCHET.newInstance();
        onStaff.setUpper(true);
        onStaff.setStaffPosition(0);

        var ledger = ElementType.CROTCHET.newInstance();
        ledger.setUpper(true);
        ledger.setStaffPosition(TWO_LEDGERS_BELOW_SP);

        var onStaffExtent = NoteColumnGeometry.extentSs(onStaff, false);
        var ledgerExtent = NoteColumnGeometry.extentSs(ledger, false);

        assertThat(ledgerExtent.leftSs()).isCloseTo(onStaffExtent.leftSs(), within(TOLERANCE_SS));
        assertThat(ledgerExtent.rightSs()).isCloseTo(onStaffExtent.rightSs(), within(TOLERANCE_SS));
    }

    @Test
    void testLedgerNote_extentMatchesNoteheadStemExtentForBothStemDirections() {
        // With ledger lines excluded, the extent matches the plain notehead/stem geometry for
        // both stem-up and stem-down, and does not equal getLedgerLineBaseExtentSs.
        var stemUpNote = ElementType.CROTCHET.newInstance();
        stemUpNote.setUpper(true);
        stemUpNote.setStaffPosition(TWO_LEDGERS_BELOW_SP);

        var stemUpOnStaff = ElementType.CROTCHET.newInstance();
        stemUpOnStaff.setUpper(true);
        stemUpOnStaff.setStaffPosition(0);

        var stemUpExtent = NoteColumnGeometry.extentSs(stemUpNote, false);
        var stemUpOnStaffExtent = NoteColumnGeometry.extentSs(stemUpOnStaff, false);

        assertThat(stemUpExtent.leftSs()).isCloseTo(stemUpOnStaffExtent.leftSs(), within(TOLERANCE_SS));
        assertThat(stemUpExtent.rightSs()).isCloseTo(stemUpOnStaffExtent.rightSs(), within(TOLERANCE_SS));

        // The column extent must NOT equal the ledger base extent: ledger lines overhang the
        // notehead on the left, so the base left is strictly more negative than the column left.
        var stemUpLedgerBase = NoteGeometry.getLedgerLineBaseExtentSs(stemUpNote);
        assertThat(stemUpExtent.leftSs())
            .as("ledger-excluded extent must not include the ledger overhang")
            .isGreaterThan(stemUpLedgerBase.leftSs());

        var stemDownNote = ElementType.CROTCHET.newInstance();
        stemDownNote.setUpper(false);
        stemDownNote.setStaffPosition(TWO_LEDGERS_BELOW_SP);

        var stemDownOnStaff = ElementType.CROTCHET.newInstance();
        stemDownOnStaff.setUpper(false);
        stemDownOnStaff.setStaffPosition(0);

        var stemDownExtent = NoteColumnGeometry.extentSs(stemDownNote, false);
        var stemDownOnStaffExtent = NoteColumnGeometry.extentSs(stemDownOnStaff, false);

        assertThat(stemDownExtent.leftSs()).isCloseTo(stemDownOnStaffExtent.leftSs(), within(TOLERANCE_SS));
        assertThat(stemDownExtent.rightSs()).isCloseTo(stemDownOnStaffExtent.rightSs(), within(TOLERANCE_SS));
    }

    @Test
    void testFlag_doesNotChangeRightSs_betweenUnbeamedAndBeamed() {
        // The flag is excluded from the static column extent; rightSs is the same
        // whether the note is beamed or not.
        var note = ElementType.QUAVER.newInstance();
        note.setUpper(true);

        var unbeamedExtent = NoteColumnGeometry.extentSs(note, false);
        var beamedExtent = NoteColumnGeometry.extentSs(note, true);

        assertThat(unbeamedExtent.rightSs()).isCloseTo(beamedExtent.rightSs(), within(TOLERANCE_SS));
    }

    // ======================================================================
    // glissandoAttachExtentSs — stem-free attach extents
    // ======================================================================

    @Test
    void testGlissandoAttachExtent_upStemCrotchet_rightSsEqualsNoteheadRight() {
        // The stem-free attach extent's right edge is the notehead right (augmentation dots
        // aside, not the stem). The stem is excluded regardless of whether it adds visual width.
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);

        var attachExtent = NoteColumnGeometry.glissandoAttachExtentSs(note, false);
        var noteheadBBox = SMuFLMetadata.requireBBox(ElementType.CROTCHET.requireSMuFLGlyph());

        // Read from the font metadata, not from getGlyphRightEdgeSs: that is the lookup the
        // production path forwards, so using it here would compare a value against itself.
        assertThat(attachExtent.rightSs()).isCloseTo(noteheadBBox.right(), within(TOLERANCE_SS));
    }

    @Test
    void testGlissandoAttachExtent_downStemCrotchet_leftSsIsNoteheadLeftEdge() {
        // Stem-down crotchet: the down-stem sits flush with the notehead's left edge and overlaps
        // inward, so it no longer protrudes past the notehead. The stem-free attach extent's left is
        // the notehead left edge (notehead X offset + glyph left bbox), and (a) the full extent's
        // left coincides with it because the stem adds no leftward width.
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(false);

        var fullExtent = NoteColumnGeometry.extentSs(note, false);
        var attachExtent = NoteColumnGeometry.glissandoAttachExtentSs(note, false);

        var glyph = ElementType.CROTCHET.requireSMuFLGlyph();
        var noteheadLeftSs =
            NoteGeometry.getNoteheadXOffsetSs(ElementType.CROTCHET, StaffElement.Direction.DOWN)
                + SMuFLMetadata.requireBBox(glyph).left();

        // (a) The stem no longer widens the extent leftward: full and attach lefts coincide.
        assertThat(attachExtent.leftSs()).isCloseTo(fullExtent.leftSs(), within(TOLERANCE_SS));
        // (b) Attach left is exactly the notehead left edge.
        assertThat(attachExtent.leftSs()).isCloseTo(noteheadLeftSs, within(TOLERANCE_SS));
    }

    @Test
    void testGlissandoAttachExtent_dottedNote_rightSsGreaterThanPlain() {
        // Augmentation dots extend the right attach edge, just as they extend the full extent.
        var plain = ElementType.CROTCHET.newInstance();
        plain.setUpper(true);

        var dotted = ElementType.CROTCHET.newInstance();
        dotted.setUpper(true);
        dotted.setDotCount(1);

        var plainAttach = NoteColumnGeometry.glissandoAttachExtentSs(plain, false);
        var dottedAttach = NoteColumnGeometry.glissandoAttachExtentSs(dotted, false);

        assertThat(dottedAttach.rightSs()).isGreaterThan(plainAttach.rightSs());
    }

    @Test
    void testGlissandoAttachExtent_accidentalNote_leftSsLessThanPlain() {
        // An accidental extends the left attach edge leftward (more negative).
        var plain = ElementType.CROTCHET.newInstance();
        plain.setUpper(true);

        var withAccidental = ElementType.CROTCHET.newInstance();
        withAccidental.setUpper(true);
        withAccidental.setAccidental(StaffElement.Accidental.SHARP);

        var plainAttach = NoteColumnGeometry.glissandoAttachExtentSs(plain, false);
        var accidentalAttach = NoteColumnGeometry.glissandoAttachExtentSs(withAccidental, false);

        assertThat(accidentalAttach.leftSs()).isLessThan(plainAttach.leftSs());
    }
}
