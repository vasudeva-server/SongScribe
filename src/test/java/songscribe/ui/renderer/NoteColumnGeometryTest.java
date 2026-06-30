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
        // A plain crotchet (stem up) has its right edge driven by the stem right edge.
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);

        var extent = NoteColumnGeometry.extentSs(note, false);
        var stemGeom = NoteGeometry.computeBaseStemGeometry(ElementType.CROTCHET, true);
        var expectedRight = stemGeom.stemLeftXSs() + NoteGeometry.STEM_WIDTH_SS;

        assertThat(extent.rightSs()).isCloseTo(expectedRight, within(TOLERANCE_SS));
    }

    // ======================================================================
    // No-stem and stem-without-flag cases
    // ======================================================================

    @Test
    void testSemibreve_noStem_noteheadDrivenRight() {
        // A semibreve has no stem and no flag: the right edge is the bare notehead right edge.
        var note = ElementType.SEMIBREVE.newInstance();
        note.setUpper(true);

        var extent = NoteColumnGeometry.extentSs(note, false);

        assertThat(extent.rightSs())
            .isCloseTo(NoteGeometry.getNoteheadRightEdgeSs(note), within(TOLERANCE_SS));
        assertThat(extent.leftSs()).isLessThan(extent.rightSs());
    }

    @Test
    void testMinim_hasStemButNoFlag_stemDrivenRight() {
        // A minim (half note) has a stem but no flag; the stem drives the right edge.
        var note = ElementType.MINIM.newInstance();
        note.setUpper(true);

        var extent = NoteColumnGeometry.extentSs(note, false);
        var stemGeom = NoteGeometry.computeBaseStemGeometry(ElementType.MINIM, true);
        var expectedRight = stemGeom.stemLeftXSs() + NoteGeometry.STEM_WIDTH_SS;

        assertThat(extent.rightSs()).isCloseTo(expectedRight, within(TOLERANCE_SS));
    }

    // ======================================================================
    // Stem-down case
    // ======================================================================

    @Test
    void testCrotchetDownStem_leftSsIsNegativeAndDistinctFromUpStem() {
        var upStemNote = ElementType.CROTCHET.newInstance();
        upStemNote.setUpper(true);

        var downStemNote = ElementType.CROTCHET.newInstance();
        downStemNote.setUpper(false);

        var upExtent = NoteColumnGeometry.extentSs(upStemNote, false);
        var downExtent = NoteColumnGeometry.extentSs(downStemNote, false);

        // Stem-down note: stem is to the left of the notehead, pushing leftSs further left.
        assertThat(downExtent.leftSs()).isLessThan(0.0);
        assertThat(downExtent.leftSs()).isLessThan(upExtent.leftSs());
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

        assertThat(attachExtent.rightSs())
            .isCloseTo(NoteGeometry.getNoteheadRightEdgeSs(note), within(TOLERANCE_SS));
    }

    @Test
    void testGlissandoAttachExtent_downStemCrotchet_leftSsExcludesStemAndIsNoteheadDriven() {
        // Stem-down crotchet: the stem extends leftward, pushing the full extent's left further
        // negative. The stem-free attach extent stops at the notehead left, so it must be (a) less
        // negative than the stem-full left and (b) exactly the notehead left edge (notehead X
        // offset + glyph left bbox). A partial (wrong) stem retraction would satisfy (a) but
        // fail (b).
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(false);

        var fullExtent = NoteColumnGeometry.extentSs(note, false);
        var attachExtent = NoteColumnGeometry.glissandoAttachExtentSs(note, false);

        var glyph = ElementType.CROTCHET.requireSMuFLGlyph();
        var noteheadLeftSs =
            NoteGeometry.getNoteheadXOffsetSs(ElementType.CROTCHET, false)
                + SMuFLMetadata.requireBBox(glyph).left();

        // (a) Stem-free left is less negative (closer to zero) than stem-full left.
        assertThat(attachExtent.leftSs()).isGreaterThan(fullExtent.leftSs());
        // (b) Stem-free left is exactly the notehead left edge.
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
