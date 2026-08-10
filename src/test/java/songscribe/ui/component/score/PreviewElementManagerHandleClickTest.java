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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static songscribe.dom.StaffElementFactory.breathMark;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.quaver;

import javax.swing.JOptionPane;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.Strings;
import songscribe.dom.ElementType;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.ui.OptionDialogs;
import songscribe.ui.edit.EditModeManager;

/**
 * Tests for the routing branches inside {@link PreviewElementManager#handleClick}:
 *
 * <ul>
 *   <li>Force-insert path (row 30): {@code handleClick(lc, true)} always inserts a new
 *       element even when {@code xPosSsMatchesElement} is true, while
 *       {@code handleClick(lc, false)} modifies the existing element in-place.</li>
 * </ul>
 */
class PreviewElementManagerHandleClickTest extends PreviewElementManagerTestBase {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void addNotes(int count, ElementType type) {
        song.withoutMutationTracking(() -> {
            for (var i = 0; i < count; i++) {
                line.addElement(type.newInstance());
            }
        });
    }

    // -----------------------------------------------------------------------
    // forceInsert path (row 30)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ForceInsertPath {

        /**
         * {@code handleClick(lc, true)} with {@code xPosSsMatchesElement=true} inserts a
         * new element at the current index rather than modifying the existing one.
         * Element count must increase by 1.
         */
        @Test
        void testForceInsertInsertsEvenWhenXMatches() {
            // Set line width so the insertion check passes
            song.setLineWidthSs(WIDE_LINE_SS);
            addNotes(2, ElementType.CROTCHET);

            setPreviewElement(crotchet());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setXPosSsMatchesElement(true);

            var countBefore = line.elementCount();
            PreviewElementManager.handleClick(lc, true);

            assertThat(line.elementCount())
                .as("forceInsert=true inserts a new element (count increases by 1)")
                .isEqualTo(countBefore + 1);
        }

        /**
         * {@code handleClick(lc, false)} with {@code xPosSsMatchesElement=true} modifies the
         * element at the current index in-place. Element count must stay the same.
         */
        @Test
        void testNormalClickModifiesInPlaceWhenXMatches() {
            addNotes(2, ElementType.CROTCHET);

            setPreviewElement(quaver());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setXPosSsMatchesElement(true);

            var countBefore = line.elementCount();
            PreviewElementManager.handleClick(lc, false);

            assertThat(line.elementCount())
                .as("forceInsert=false modifies in-place (element count unchanged)")
                .isEqualTo(countBefore);

            assertThat(line.getElement(1).getType())
                .as("element at index 1 replaced with preview type")
                .isEqualTo(ElementType.QUAVER);
        }

        /**
         * When an insertion would not fit, the note path shows the line-full error with the note
         * variant and inserts nothing. The alert is verified through a static mock so removing it
         * fails the test.
         */
        @Test
        void testNoteInsertBlockedWhenLineFull() {
            song.setLineWidthSs(0);
            addNotes(2, ElementType.CROTCHET);

            setPreviewElement(crotchet());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setXPosSsMatchesElement(true);

            var countBefore = line.elementCount();

            try (var optionDialogsMock = mockStatic(OptionDialogs.class)) {
                PreviewElementManager.handleClick(lc, true);

                optionDialogsMock.verify(() -> OptionDialogs.showErrorMessage(
                    isNull(), eq(Strings.ALERT_TITLE_INSERT_ERROR), eq(Strings.ERROR_LINE_FULL_ELEMENT),
                    eq(ElementType.CROTCHET.categoryName())));
            }

            assertThat(line.elementCount())
                .as("note not inserted when the line is full")
                .isEqualTo(countBefore);
        }
    }

    // -----------------------------------------------------------------------
    // Breath mark over an existing element — blocked (issue #456)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class BreathMarkOverExistingElement {

        /**
         * Clicking a breath mark over an existing element must neither replace nor insert:
         * a breath mark never replaces an element, and being over one is a blocked position,
         * so the click leaves the line intact.
         */
        @Test
        void testBreathMarkOverExistingElementDoesNotReplaceOrInsert() {
            addNotes(2, ElementType.CROTCHET);

            setPreviewElement(breathMark());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setXPosSsMatchesElement(true);

            var countBefore = line.effectiveElementCount();

            PreviewElementManager.handleClick(lc);

            assertThat(line.effectiveElementCount())
                .as("blocked position: no element inserted or removed")
                .isEqualTo(countBefore);
            assertThat(line.getElement(1).getType())
                .as("element under the cursor is not replaced by the breath mark")
                .isEqualTo(ElementType.CROTCHET);
        }
    }

    // -----------------------------------------------------------------------
    // Grace note over the cursor — clicks are ignored (issue #360)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GraceNoteOverExistingElement {

        /**
         * A grace note may never be replaced. Clicking over one with a different preview
         * element must leave it in place and not insert anything either.
         */
        @Test
        void testClickOverGraceNoteIsIgnored() {
            song.withoutMutationTracking(() -> line.addElement(graceQuaver()));

            setPreviewElement(crotchet());
            PreviewElementManager.setCurrentXIndex(0);
            PreviewElementManager.setXPosSsMatchesElement(true);

            var countBefore = line.elementCount();
            PreviewElementManager.handleClick(lc);

            assertThat(line.elementCount())
                .as("no element inserted or removed")
                .isEqualTo(countBefore);
            assertThat(line.getElement(0).getType())
                .as("the grace note is not replaced")
                .isEqualTo(ElementType.GRACE_QUAVER);
        }
    }

    // -----------------------------------------------------------------------
    // Replacing a note removes its accidental, so the restatement prompt runs (#681)
    // -----------------------------------------------------------------------

    /**
     * Replacing a note takes its explicit accidental away, so this path asks the same restatement
     * question every other removal path does. The fixture is the #681 worked example, in D♭ major
     * — five flats, so F is unaltered by the key and every flat on an F is explicit:
     *
     * <pre>
     * line 1:  F♭  G   F♭
     * line 2:  F♭  A   F   F♮
     * </pre>
     *
     * Replacing {@code 1:0} with a plain G finds {@code 1:2} and {@code 2:0}.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ReplacementRestatements {

        // Staff position 0 is B4 and positions grow downwards, so each step down is one letter back.
        private static final int A_STAFF_POSITION = 1;
        private static final int G_STAFF_POSITION = 2;
        private static final int F_STAFF_POSITION = 3;

        /** D♭ major: B E A D G are flattened, so an F needs an explicit flat to sound flat. */
        private static final int FIVE_FLATS = 5;

        private static final int THIRD_NOTE = 2;
        private static final int FOURTH_NOTE = 3;

        private Line secondLine = new Line(new Song());

        @BeforeEach
        void buildWorkedExample() {
            song.setLineWidthSs(WIDE_LINE_SS);

            song.withoutMutationTracking(() -> {
                flatKey(line);
                line.addElement(note(F_STAFF_POSITION, StaffElement.Accidental.FLAT));
                line.addElement(note(G_STAFF_POSITION, null));
                line.addElement(note(F_STAFF_POSITION, StaffElement.Accidental.FLAT));

                secondLine = new Line(song);
                flatKey(secondLine);
                secondLine.addElement(note(F_STAFF_POSITION, StaffElement.Accidental.FLAT));
                secondLine.addElement(note(A_STAFF_POSITION, null));
                secondLine.addElement(note(F_STAFF_POSITION, null));
                secondLine.addElement(note(F_STAFF_POSITION, StaffElement.Accidental.NATURAL));
                song.addLine(secondLine);
            });
        }

        private void flatKey(Line target) {
            target.setKeyType(KeyType.FLATS);
            target.setKeyAccidentalCount(FIVE_FLATS);
        }

        private StaffElement note(int staffPosition, StaffElement.@Nullable Accidental accidental) {
            var element = crotchet();
            element.setStaffPosition(staffPosition);
            element.setAccidental(accidental);
            return element;
        }

        /** Clicks a plain G onto {@code 1:0}, replacing the F♭ that is there. */
        private void replaceTheFirstFlatWithAPlainG(int answer) {
            setPreviewElement(note(G_STAFF_POSITION, null));
            PreviewElementManager.setCurrentStaffPosition(G_STAFF_POSITION);
            PreviewElementManager.setCurrentXIndex(0);
            PreviewElementManager.setXPosSsMatchesElement(true);

            try (var optionDialogs = mockStatic(OptionDialogs.class)) {
                optionDialogs.when(() -> OptionDialogs.showConfirmDialog(
                    any(), any(), any(), anyInt(), anyInt())).thenReturn(answer);

                PreviewElementManager.handleClick(lc);
            }
        }

        @Test
        void testYesClearsEveryRestatementAcrossLinesAndLetsThePitchChangePropagate() {
            replaceTheFirstFlatWithAPlainG(JOptionPane.YES_OPTION);

            assertThat(line.getElement(0).getStaffPosition())
                .as("the replacement landed where the click was")
                .isEqualTo(G_STAFF_POSITION);
            assertThat(line.getElement(THIRD_NOTE).getAccidental())
                .as("the restatement on the edited line is cleared")
                .isNull();
            assertThat(secondLine.getElement(0).getAccidental())
                .as("the restatement on the next line is cleared")
                .isNull();

            // Suppression at the F position is what lets 2:2 change pitch instead of being handed
            // the flat straight back, and 2:3's natural, no longer cancelling anything, goes the
            // ordinary way: the mirror rule takes it.
            assertThat(secondLine.getElement(THIRD_NOTE).getAccidental()).isNull();
            assertThat(secondLine.getElement(FOURTH_NOTE).getAccidental()).isNull();
        }

        @Test
        void testNoReplacesTheNoteAndLeavesEveryRestatementAlone() {
            replaceTheFirstFlatWithAPlainG(JOptionPane.NO_OPTION);

            assertThat(line.getElement(0).getStaffPosition()).isEqualTo(G_STAFF_POSITION);
            assertThat(line.getElement(THIRD_NOTE).getAccidental())
                .isEqualTo(StaffElement.Accidental.FLAT);
            assertThat(secondLine.getElement(0).getAccidental())
                .isEqualTo(StaffElement.Accidental.FLAT);
        }

        @Test
        void testCancelAbandonsTheReplacementItself() {
            replaceTheFirstFlatWithAPlainG(JOptionPane.CANCEL_OPTION);

            assertThat(line.getElement(0).getStaffPosition())
                .as("the click did nothing at all")
                .isEqualTo(F_STAFF_POSITION);
            assertThat(line.getElement(0).getAccidental())
                .isEqualTo(StaffElement.Accidental.FLAT);
            assertThat(line.getElement(THIRD_NOTE).getAccidental())
                .isEqualTo(StaffElement.Accidental.FLAT);
            assertThat(secondLine.getElement(0).getAccidental())
                .isEqualTo(StaffElement.Accidental.FLAT);
        }

        @Test
        void testNothingIsAskedWhenTheReplacementKeepsTheAccidental() {
            // Same staff position, same flat: the accidental survives the replacement, so there is
            // nothing to consent to.
            setPreviewElement(note(F_STAFF_POSITION, StaffElement.Accidental.FLAT));
            PreviewElementManager.setCurrentStaffPosition(F_STAFF_POSITION);
            PreviewElementManager.setCurrentXIndex(0);
            PreviewElementManager.setXPosSsMatchesElement(true);

            try (var optionDialogs = mockStatic(OptionDialogs.class)) {
                PreviewElementManager.handleClick(lc);
                optionDialogs.verifyNoInteractions();
            }

            assertThat(line.getElement(THIRD_NOTE).getAccidental())
                .isEqualTo(StaffElement.Accidental.FLAT);
        }

        @Test
        void testNothingIsAskedWhenTheReplacedNoteHasNoAccidental() {
            // The everyday case — replacing a plain note. Nothing is taken away, so the prompt must
            // stay out of the way; were this branch lost, every ordinary click would raise it.
            setPreviewElement(note(A_STAFF_POSITION, null));
            PreviewElementManager.setCurrentStaffPosition(A_STAFF_POSITION);
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setXPosSsMatchesElement(true);

            try (var optionDialogs = mockStatic(OptionDialogs.class)) {
                PreviewElementManager.handleClick(lc);
                optionDialogs.verifyNoInteractions();
            }

            assertThat(line.getElement(1).getStaffPosition()).isEqualTo(A_STAFF_POSITION);
        }

        @Test
        void testReplacingANoteWithOneCarryingAnAccidentalAsksNothing() {
            // The plain G at 1:1 rewritten as a natural G at the same staff position: an accidental
            // is added rather than taken away, so nothing was removed and nothing is asked.
            setPreviewElement(note(G_STAFF_POSITION, StaffElement.Accidental.NATURAL));
            PreviewElementManager.setCurrentStaffPosition(G_STAFF_POSITION);
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setXPosSsMatchesElement(true);

            try (var optionDialogs = mockStatic(OptionDialogs.class)) {
                PreviewElementManager.handleClick(lc);
                optionDialogs.verifyNoInteractions();
            }

            assertThat(line.getElement(1).getAccidental())
                .isEqualTo(StaffElement.Accidental.NATURAL);
        }
    }

    // -----------------------------------------------------------------------
    // validateAndGetPreviewElement stale-preview guard (row 37)
    // -----------------------------------------------------------------------

    /**
     * When {@code EditModeManager.elementWasModified()} returns {@code true} for the
     * current element, {@code validateAndGetPreviewElement} returns {@code null}, causing
     * {@code modifyExistingElement} to bail out without changing the element. The stale-
     * preview notification is still fired via {@code previewElementDidChange}.
     */
    @Test
    void testElementWasModifiedCausesEarlyReturnAndFiresChangeNotification() {
        addNotes(1, ElementType.CROTCHET);

        // Preview element is a different type; clicking on the existing note would
        // normally replace it — but elementWasModified will block that.
        setPreviewElement(quaver());
        PreviewElementManager.setCurrentXIndex(0);
        PreviewElementManager.setXPosSsMatchesElement(true);

        // Override the base stub: mark the element as already-modified (stale preview).
        editModeManagerMock
            .when(() -> EditModeManager.elementWasModified(any(Line.class), anyInt()))
            .thenReturn(true);

        var typeBefore = line.getElement(0).getType();

        PreviewElementManager.handleClick(lc);

        // Element must NOT have been replaced (modifyExistingElement returned early).
        assertThat(line.getElement(0).getType())
            .as("element type unchanged: stale-preview guard prevented replacement")
            .isEqualTo(typeBefore);

        // previewElementDidChange must have been called to notify that the preview
        // is stale and should be refreshed.
        editModeManagerMock.verify(
            () -> EditModeManager.previewElementDidChange(any(Line.class), eq(0))
        );
    }

}
