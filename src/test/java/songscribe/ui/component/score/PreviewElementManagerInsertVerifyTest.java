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

package songscribe.ui.component.score;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.dom.Beam;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;

/**
 * Unit tests covering behaviors from e2e rows that exercise pure-model logic
 * in {@link PreviewElementManager}:
 *
 * <ul>
 *   <li>Row 16 ({@code InsertElementTypes.testVerifyElementTypesAndAutoBeam}):
 *       {@code insertElement} produces the correct element type/pitch, and
 *       {@code applyAutomaticBeaming} creates a beam span when two quavers are
 *       inserted in sequence.</li>
 *   <li>Row 17 ({@code InsertElementTypes.testInsertBetweenAndVerifyShift}):
 *       inserting an element before an existing element shifts subsequent elements
 *       to higher indices (insert, not replace).</li>
 *   <li>Row 18 ({@code InsertElementTypes.testClickOnNoteReplacesIt}):
 *       clicking on an existing element's X position replaces it with the preview
 *       element's type and pitch while leaving the element count unchanged.</li>
 * </ul>
 */
class PreviewElementManagerInsertVerifyTest extends PreviewElementManagerTestBase {

    // -----------------------------------------------------------------------
    // insertElement — type and pitch after insertion (row 16, partial)
    // -----------------------------------------------------------------------

    /**
     * Inserting a MINIM preview at staff position 2 produces a MINIM element
     * at the insertion index with staff position 2, and leaves the count increased
     * by 1.
     *
     * <p>{@code insertElement} inserts the preview element directly (no internal
     * {@code applyStaffPosition} call), so the staff position must be set on the
     * preview element before calling {@code handleClick}.
     */
    @Test
    void testInsertElementProducesCorrectTypeAndPitch() {
        song.setLineWidthSs(WIDE_LINE_SS);

        song.withoutMutationTracking(() -> line.addElement(ElementType.CROTCHET.newInstance()));

        var countBefore = line.effectiveElementCount();

        // Insert a MINIM at staff position 2 before the existing CROTCHET (index 0).
        // Staff position must be pre-set on the preview element; insertElement uses it directly.
        var preview = ElementType.MINIM.newInstance();
        preview.setStaffPosition(2);
        setPreviewElement(preview);
        PreviewElementManager.setCurrentXIndex(0);
        PreviewElementManager.setXPosSsMatchesElement(false);

        PreviewElementManager.handleClick(lc);

        assertThat(line.effectiveElementCount())
            .as("element count increases by 1 after insertion")
            .isEqualTo(countBefore + 1);
        assertThat(line.getElement(0).getType())
            .as("inserted element has the preview type (MINIM)")
            .isEqualTo(ElementType.MINIM);
        assertThat(line.getElement(0).getStaffPosition())
            .as("inserted element has the correct staff position")
            .isEqualTo(2);
    }

    // -----------------------------------------------------------------------
    // applyAutomaticBeaming — beam created for two consecutive quavers (row 16)
    // -----------------------------------------------------------------------

    /**
     * After inserting a second QUAVER immediately after an existing QUAVER,
     * a beam span is created linking the two.
     */
    @Test
    void testAutoBeamCreatedForTwoConsecutiveQuavers() {
        song.setLineWidthSs(WIDE_LINE_SS);

        // Existing QUAVER at effective index 0
        song.withoutMutationTracking(() -> line.addElement(ElementType.QUAVER.newInstance()));

        // No beam yet
        assertThat(line.findRangeElements(Beam.class))
            .as("pre-condition: no beam before insertion")
            .isEmpty();

        // Insert a second QUAVER immediately after the first (at index 1, before terminal)
        setPreviewElement(ElementType.QUAVER.newInstance());
        PreviewElementManager.setCurrentXIndex(1);
        PreviewElementManager.setXPosSsMatchesElement(false);

        PreviewElementManager.handleClick(lc);

        var beams = line.findRangeElements(Beam.class);
        assertThat(beams)
            .as("auto-beam: exactly one beam span created after inserting second quaver")
            .hasSize(1);
        assertThat(beams.getFirst().getAnchorElementIndex())
            .as("auto-beam: span starts at the first quaver (index 0)")
            .isEqualTo(0);
        assertThat(beams.getFirst().getEndElementIndex())
            .as("auto-beam: span ends at the second quaver (index 1)")
            .isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // insertElement — index shift after insert-before (row 17)
    // -----------------------------------------------------------------------

    /**
     * Inserting at an index that already holds an element shifts the existing
     * element to the next index.
     *
     * <p>Setup: [CROTCHET(sp=0), MINIM(sp=2)]. Insert CROTCHET(sp=4) at index 1 (before MINIM).
     * Post-insertion: [CROTCHET, new-CROTCHET(sp=4), MINIM(sp=2)].
     *
     * <p>{@code insertElement} inserts the preview element directly, so the staff
     * position must be set on the preview element before calling {@code handleClick}.
     */
    @Test
    void testInsertBeforeElementShiftsExistingToHigherIndex() {
        song.setLineWidthSs(WIDE_LINE_SS);

        song.withoutMutationTracking(() -> {
            line.addElement(ElementType.CROTCHET.newInstance());  // index 0
            var minim = ElementType.MINIM.newInstance();
            minim.setStaffPosition(2);
            line.addElement(minim);  // index 1
        });

        var countBefore = line.effectiveElementCount();

        // Insert a new CROTCHET at staff position 4 before the MINIM (index 1).
        // Staff position must be pre-set on the preview element.
        var preview = ElementType.CROTCHET.newInstance();
        preview.setStaffPosition(4);
        setPreviewElement(preview);
        PreviewElementManager.setCurrentXIndex(1);
        PreviewElementManager.setXPosSsMatchesElement(false);

        PreviewElementManager.handleClick(lc);

        assertThat(line.effectiveElementCount())
            .as("element count increases by 1 after insertion")
            .isEqualTo(countBefore + 1);
        assertThat(line.getElement(1).getType())
            .as("inserted element type is CROTCHET")
            .isEqualTo(ElementType.CROTCHET);
        assertThat(line.getElement(1).getStaffPosition())
            .as("inserted element has staff position 4")
            .isEqualTo(4);
        assertThat(line.getElement(2).getType())
            .as("previously-at-index-1 MINIM shifted to index 2")
            .isEqualTo(ElementType.MINIM);
    }

    // -----------------------------------------------------------------------
    // insertElement — connecting-glissando removal (issues #456, #457)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ConnectingGlissandoRemoval {

        /**
         * Inserts the given preview type at index 1 of a two-note line whose first note
         * carries a CONNECTED glissando, and returns that first note for assertions.
         */
        private StaffElement insertBetweenConnectedNotes(ElementType insertedType) {
            song.setLineWidthSs(WIDE_LINE_SS);

            song.withoutMutationTracking(() -> {
                var noteA = ElementType.CROTCHET.newInstance();
                noteA.setGlissando();
                line.addElement(noteA);  // index 0
                line.addElement(ElementType.CROTCHET.newInstance());  // index 1
            });

            var noteA = line.getElement(0);
            assertThat(noteA.getGlissando())
                .as("pre-condition: noteA has a connected glissando")
                .isNotNull();

            setPreviewElement(insertedType.newInstance());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setXPosSsMatchesElement(false);

            PreviewElementManager.handleClick(lc);

            assertThat(line.getElement(1).getType())
                .as("element inserted at index 1")
                .isEqualTo(insertedType);

            return noteA;
        }

        /** A breath mark between connected notes removes the glissando. */
        @Test
        void testInsertingBreathMarkRemovesConnectingGlissando() {
            var noteA = insertBetweenConnectedNotes(ElementType.BREATH_MARK);

            assertThat(noteA.getGlissando())
                .as("connected glissando removed when a breath mark is inserted")
                .isNull();
        }

        /** A rest between connected notes removes the glissando. */
        @Test
        void testInsertingRestRemovesConnectingGlissando() {
            var noteA = insertBetweenConnectedNotes(ElementType.CROTCHET_REST);

            assertThat(noteA.getGlissando())
                .as("connected glissando removed when a rest is inserted")
                .isNull();
        }

        /** A grace note between connected notes removes the glissando (issue #457). */
        @Test
        void testInsertingGraceNoteRemovesConnectingGlissando() {
            var noteA = insertBetweenConnectedNotes(ElementType.GRACE_QUAVER);

            assertThat(noteA.getGlissando())
                .as("connected glissando removed when a grace note is inserted")
                .isNull();
        }

        /** A bar line between connected notes removes the glissando (issue #457). */
        @Test
        void testInsertingBarLineRemovesConnectingGlissando() {
            var noteA = insertBetweenConnectedNotes(ElementType.SINGLE_BARLINE);

            assertThat(noteA.getGlissando())
                .as("connected glissando removed when a bar line is inserted")
                .isNull();
        }

        /** A pitched note between connected notes re-targets the glissando, leaving it intact. */
        @Test
        void testInsertingPitchedNoteKeepsConnectingGlissando() {
            var noteA = insertBetweenConnectedNotes(ElementType.MINIM);

            assertThat(noteA.getGlissando())
                .as("connected glissando retained when a pitched note is inserted")
                .isNotNull();
        }

        /**
         * A SLIDE_OUT glissando does not connect to the following note, so inserting a
         * breath mark after it must leave it intact.
         */
        @Test
        void testInsertingBreathMarkLeavesSlideOutGlissando() {
            song.setLineWidthSs(WIDE_LINE_SS);

            song.withoutMutationTracking(() -> {
                var noteA = ElementType.CROTCHET.newInstance();
                noteA.setFall();
                line.addElement(noteA);
                line.addElement(ElementType.CROTCHET.newInstance());
            });

            var noteA = line.getElement(0);

            setPreviewElement(ElementType.BREATH_MARK.newInstance());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setXPosSsMatchesElement(false);

            PreviewElementManager.handleClick(lc);

            assertThat(noteA.hasFall())
                .as("fall retained")
                .isTrue();
        }

        /**
         * Inserting a breath mark after a note that has no glissando is a no-op for the
         * glissando-removal branch: the null guard is exercised, no exception is thrown,
         * and the breath mark is still inserted.
         */
        @Test
        void testInsertingBreathMarkAfterNoteWithoutGlissandoIsNoOp() {
            song.setLineWidthSs(WIDE_LINE_SS);

            song.withoutMutationTracking(() -> {
                line.addElement(ElementType.CROTCHET.newInstance());
                line.addElement(ElementType.CROTCHET.newInstance());
            });

            var noteA = line.getElement(0);

            setPreviewElement(ElementType.BREATH_MARK.newInstance());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setXPosSsMatchesElement(false);

            PreviewElementManager.handleClick(lc);

            assertThat(noteA.getGlissando())
                .as("note without a glissando is left untouched")
                .isNull();
            assertThat(line.getElement(1).getType())
                .as("breath mark still inserted at index 1")
                .isEqualTo(ElementType.BREATH_MARK);
        }
    }

    // -----------------------------------------------------------------------
    // modifyExistingElement — pitch-change path (row 18)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ModifyExistingElementPitchChange {

        /**
         * Clicking on the X position of an existing element with a different pitch
         * replaces the element in-place: the type and pitch change to the preview's
         * values, the element count stays the same.
         */
        @Test
        void testClickOnExistingNoteReplacesTypeAndPitch() {
            song.withoutMutationTracking(() -> {
                var note = ElementType.MINIM.newInstance();
                note.setStaffPosition(2);
                line.addElement(note);
            });

            var countBefore = line.effectiveElementCount();

            // Preview: CROTCHET at staff position -6 (different type and pitch)
            PreviewElementManager.setCurrentStaffPosition(-6);
            setPreviewElement(ElementType.CROTCHET.newInstance());
            PreviewElementManager.setCurrentXIndex(0);
            PreviewElementManager.setXPosSsMatchesElement(true);

            PreviewElementManager.handleClick(lc);

            assertThat(line.effectiveElementCount())
                .as("element count unchanged after modify-in-place")
                .isEqualTo(countBefore);
            assertThat(line.getElement(0).getType())
                .as("element replaced with preview type CROTCHET")
                .isEqualTo(ElementType.CROTCHET);
            assertThat(line.getElement(0).getStaffPosition())
                .as("element's staff position updated to the preview's position (-6)")
                .isEqualTo(-6);
        }

        /**
         * Replacing a note with one of the same type but different pitch leaves the
         * count unchanged and updates only the pitch.
         */
        @Test
        void testSameTypeClickUpdatesPitchOnly() {
            song.withoutMutationTracking(() -> {
                var note = ElementType.CROTCHET.newInstance();
                note.setStaffPosition(0);
                line.addElement(note);
            });

            var countBefore = line.effectiveElementCount();

            PreviewElementManager.setCurrentStaffPosition(-4);
            setPreviewElement(ElementType.CROTCHET.newInstance());
            PreviewElementManager.setCurrentXIndex(0);
            PreviewElementManager.setXPosSsMatchesElement(true);

            PreviewElementManager.handleClick(lc);

            assertThat(line.effectiveElementCount())
                .as("element count unchanged")
                .isEqualTo(countBefore);
            assertThat(line.getElement(0).getType())
                .as("type remains CROTCHET")
                .isEqualTo(ElementType.CROTCHET);
            assertThat(line.getElement(0).getStaffPosition())
                .as("pitch updated to -4")
                .isEqualTo(-4);
        }
    }
}
