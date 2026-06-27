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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.layout.NoteGeometry;

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
        var stemGeom = NoteRenderer.computeBaseStemGeometry(ElementType.CROTCHET, true);
        var expectedRight = stemGeom.stemLeftXSs() + NoteGeometry.STEM_WIDTH_SS;

        assertThat(extent.rightSs()).isCloseTo(expectedRight, within(TOLERANCE_SS));
    }

    // ======================================================================
    // No-stem and stem-without-flag cases
    // ======================================================================

    @Test
    void testSemibreve_noStem_hasNullFlagBBoxAndNoteheadDrivenRight() {
        // A semibreve has no stem and no flag: the right edge is the bare notehead right edge
        // and there is no flag bbox.
        var note = ElementType.SEMIBREVE.newInstance();
        note.setUpper(true);

        var extent = NoteColumnGeometry.extentSs(note, false);

        assertThat(extent.flagBBoxLocal()).isNull();
        assertThat(extent.rightSs())
            .isCloseTo(NoteGeometry.getNoteheadRightEdgeSs(note), within(TOLERANCE_SS));
        assertThat(extent.leftSs()).isLessThan(extent.rightSs());
    }

    @Test
    void testMinim_hasStemButNoFlag_nullFlagBBoxWithStemDrivenRight() {
        // A minim (half note) has a stem but no flag: flagBBoxLocal stays null, yet the stem
        // still drives the right edge as for any up-stem note.
        var note = ElementType.MINIM.newInstance();
        note.setUpper(true);

        var extent = NoteColumnGeometry.extentSs(note, false);
        var stemGeom = NoteRenderer.computeBaseStemGeometry(ElementType.MINIM, true);
        var expectedRight = stemGeom.stemLeftXSs() + NoteGeometry.STEM_WIDTH_SS;

        assertThat(extent.flagBBoxLocal()).isNull();
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
    void testLedgerNote_widerThanOnStaff_onRelevantSide() {
        // A note on the staff (staffPosition = 0): no ledger lines, narrower.
        var onStaff = ElementType.CROTCHET.newInstance();
        onStaff.setUpper(true);
        onStaff.setStaffPosition(0);

        // A note with ledger lines below the staff.
        var ledger = ElementType.CROTCHET.newInstance();
        ledger.setUpper(true);
        ledger.setStaffPosition(TWO_LEDGERS_BELOW_SP);

        var onStaffExtent = NoteColumnGeometry.extentSs(onStaff, false);
        var ledgerExtent = NoteColumnGeometry.extentSs(ledger, false);

        // Ledger lines extend both left and right of the notehead, making the column wider.
        assertThat(ledgerExtent.leftSs()).isLessThan(onStaffExtent.leftSs());
        assertThat(ledgerExtent.rightSs()).isGreaterThan(onStaffExtent.rightSs());
    }

    @Test
    void testLedgerNote_extentMatchesProportionalBaseExtent() {
        // The column extent for a ledger note must exactly reflect getLedgerLineBaseExtentSs, not
        // the old fixed 0.25 ss overhang. Verified for both stem-up and stem-down configurations.
        var stemUpNote = ElementType.CROTCHET.newInstance();
        stemUpNote.setUpper(true);
        stemUpNote.setStaffPosition(TWO_LEDGERS_BELOW_SP);

        var stemUpExtent = NoteColumnGeometry.extentSs(stemUpNote, false);
        var stemUpLedger = NoteGeometry.getLedgerLineBaseExtentSs(stemUpNote);

        assertThat(stemUpExtent.leftSs()).isCloseTo(stemUpLedger.leftSs(), within(TOLERANCE_SS));
        assertThat(stemUpExtent.rightSs()).isCloseTo(stemUpLedger.rightSs(), within(TOLERANCE_SS));

        var stemDownNote = ElementType.CROTCHET.newInstance();
        stemDownNote.setUpper(false);
        stemDownNote.setStaffPosition(TWO_LEDGERS_BELOW_SP);

        var stemDownExtent = NoteColumnGeometry.extentSs(stemDownNote, false);
        var stemDownLedger = NoteGeometry.getLedgerLineBaseExtentSs(stemDownNote);

        assertThat(stemDownExtent.leftSs()).isCloseTo(stemDownLedger.leftSs(), within(TOLERANCE_SS));
        assertThat(stemDownExtent.rightSs()).isCloseTo(stemDownLedger.rightSs(), within(TOLERANCE_SS));
    }

    @Test
    void testGraceNote_flagBBoxNarrowerThanFullNote() {
        // The grace flag bbox is scaled by GRACE_NOTE_SCALE; a full quaver's flag is at full size.
        var full = ElementType.QUAVER.newInstance();
        full.setUpper(true);

        var grace = ElementType.GRACE_QUAVER.newInstance();
        // Grace notes always stem up (enforced inside extentSs)

        var fullExtent = NoteColumnGeometry.extentSs(full, false);
        var graceExtent = NoteColumnGeometry.extentSs(grace, false);

        // Both unbeamed notes have flags, but the grace flag is scaled smaller.
        assertThat(graceExtent.flagBBoxLocal()).isNotNull();
        assertThat(fullExtent.flagBBoxLocal()).isNotNull();
        var graceFlagWidth = Objects.requireNonNull(graceExtent.flagBBoxLocal()).getWidth();
        var fullFlagWidth = Objects.requireNonNull(fullExtent.flagBBoxLocal()).getWidth();
        assertThat(graceFlagWidth).isLessThan(fullFlagWidth);
    }

    // ======================================================================
    // Flag-split invariant
    // ======================================================================

    @Test
    void testUnbeamedQuaver_hasNonNullFlagBBox() {
        var note = ElementType.QUAVER.newInstance();
        note.setUpper(true);

        var extent = NoteColumnGeometry.extentSs(note, false);

        assertThat(extent.flagBBoxLocal()).isNotNull();
    }

    @Test
    void testBeamedQuaver_hasNullFlagBBox() {
        var note = ElementType.QUAVER.newInstance();
        note.setUpper(true);

        var extent = NoteColumnGeometry.extentSs(note, true);

        assertThat(extent.flagBBoxLocal()).isNull();
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
}
