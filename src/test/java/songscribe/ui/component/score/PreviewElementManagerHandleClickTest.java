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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.Strings;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.SlideZone;
import songscribe.ui.OptionDialogs;
import songscribe.ui.edit.EditModeManager;

/**
 * Tests for the two principal routing branches inside
 * {@link PreviewElementManager#handleClick}:
 *
 * <ul>
 *   <li>Glissando-placeholder path (row 28): clicking with a glissando preview element
 *       either applies the glissando to the source note or is a no-op, depending on
 *       whether {@code currentGlissandoZone} is set.</li>
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
    // Glissando-placeholder path (row 28)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GlissandoPlaceholderPath {

        /**
         * A valid zone (CONNECTED) applies the glissando to the source note at
         * {@code currentXIndex - 1}.
         */
        @Test
        void testValidZoneAppliesGlissandoToSourceNote() {
            addNotes(2, ElementType.CROTCHET);

            // Preview element is a SLIDE placeholder, zone is set
            setPreviewElement(ElementType.SLIDE.newInstance());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setCurrentSlideZone(SlideZone.GLISSANDO);

            PreviewElementManager.handleClick(lc);

            var sourceNote = line.getElement(0);
            var glissando = sourceNote.getGlissando();
            assertThat(glissando)
                .as("glissando applied to source note at xIndex-1")
                .isNotNull();
        }

        /**
         * A null zone (no valid glissando position) is a no-op: no glissando is set.
         */
        @Test
        void testNullZoneIsNoOp() {
            addNotes(2, ElementType.CROTCHET);

            setPreviewElement(ElementType.SLIDE.newInstance());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setCurrentSlideZone(null);

            PreviewElementManager.handleClick(lc);

            assertThat(line.getElement(0).getGlissando())
                .as("no glissando set when zone is null")
                .isNull();
        }
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

            setPreviewElement(ElementType.CROTCHET.newInstance());
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

            setPreviewElement(ElementType.QUAVER.newInstance());
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
         * so the click only shows the alert and leaves the line intact. The alert call is
         * verified through a static mock so that removing it would fail the test.
         */
        @Test
        void testBreathMarkOverExistingElementDoesNotReplaceOrInsert() {
            addNotes(2, ElementType.CROTCHET);

            setPreviewElement(ElementType.BREATH_MARK.newInstance());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setXPosSsMatchesElement(true);

            var countBefore = line.effectiveElementCount();

            try (MockedStatic<OptionDialogs> optionDialogsMock = mockStatic(OptionDialogs.class)) {
                PreviewElementManager.handleClick(lc);

                optionDialogsMock.verify(() -> OptionDialogs.showErrorMessage(
                    isNull(), eq(Strings.ALERT_TITLE_BREATH_MARK), eq(Strings.ALERT_BREATH_MARK_POSITION)));
            }

            assertThat(line.effectiveElementCount())
                .as("blocked position: no element inserted or removed")
                .isEqualTo(countBefore);
            assertThat(line.getElement(1).getType())
                .as("element under the cursor is not replaced by the breath mark")
                .isEqualTo(ElementType.CROTCHET);
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
        setPreviewElement(ElementType.QUAVER.newInstance());
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
