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

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.engraving.Staff;

/**
 * Unit tests for the pure-logic static methods in {@link PreviewElementManager}:
 * {@link PreviewElementManager#calculateStaffPositionFromMouse},
 * {@link PreviewElementManager#isValidStaffPosition},
 * {@link PreviewElementManager#applyStaffPosition}, and
 * {@link PreviewElementManager#isBreathMarkInsertionBlocked}.
 * None of these methods touch UI or messaging state.
 */
class PreviewElementManagerStaticMethodsTest extends UnitTest {

    // -----------------------------------------------------------------------
    // isBreathMarkInsertionBlocked
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class IsBreathMarkInsertionBlocked {

        /** BREATH_MARK preview at index 0 must be blocked — there is no preceding element. */
        @Test
        void testBreathMarkAtIndexZeroIsBlocked() {
            var breathMark = ElementType.BREATH_MARK.newInstance();
            var line = lineWith(ElementType.CROTCHET);

            assertThat(PreviewElementManager.isBreathMarkInsertionBlocked(breathMark, 0, line, false))
                .as("breath mark at xIndex 0 is blocked")
                .isTrue();
        }

        /** BREATH_MARK preview after a non-grace note is allowed. */
        @Test
        void testBreathMarkAfterNonGraceNoteIsNotBlocked() {
            var breathMark = ElementType.BREATH_MARK.newInstance();
            var line = lineWith(ElementType.CROTCHET);

            assertThat(PreviewElementManager.isBreathMarkInsertionBlocked(breathMark, 1, line, false))
                .as("breath mark after a non-grace note is not blocked")
                .isFalse();
        }

        /** BREATH_MARK preview after a rest is allowed. */
        @Test
        void testBreathMarkAfterRestIsNotBlocked() {
            var breathMark = ElementType.BREATH_MARK.newInstance();
            var line = lineWith(ElementType.CROTCHET_REST);

            assertThat(PreviewElementManager.isBreathMarkInsertionBlocked(breathMark, 1, line, false))
                .as("breath mark after a rest is not blocked")
                .isFalse();
        }

        /** BREATH_MARK preview directly after a grace note must be blocked. */
        @Test
        void testBreathMarkAfterGraceNoteIsBlocked() {
            var breathMark = ElementType.BREATH_MARK.newInstance();
            var line = lineWith(ElementType.GRACE_QUAVER);

            assertThat(PreviewElementManager.isBreathMarkInsertionBlocked(breathMark, 1, line, false))
                .as("breath mark directly after a grace note is blocked")
                .isTrue();
        }

        /** BREATH_MARK preview directly after another breath mark must be blocked. */
        @Test
        void testBreathMarkAfterBreathMarkIsBlocked() {
            var breathMark = ElementType.BREATH_MARK.newInstance();
            var line = lineWith(ElementType.CROTCHET, ElementType.BREATH_MARK);

            assertThat(PreviewElementManager.isBreathMarkInsertionBlocked(breathMark, 2, line, false))
                .as("breath mark directly after another breath mark is blocked")
                .isTrue();
        }

        /** BREATH_MARK preview directly before an existing breath mark must be blocked. */
        @Test
        void testBreathMarkBeforeBreathMarkIsBlocked() {
            var breathMark = ElementType.BREATH_MARK.newInstance();
            var line = lineWith(ElementType.CROTCHET, ElementType.BREATH_MARK);

            assertThat(PreviewElementManager.isBreathMarkInsertionBlocked(breathMark, 1, line, false))
                .as("breath mark directly before an existing breath mark is blocked")
                .isTrue();
        }

        /**
         * A breath mark over an existing element must be blocked even when the preceding
         * element is a valid note — a breath mark never replaces an element.
         */
        @Test
        void testBreathMarkOverExistingElementIsBlocked() {
            var breathMark = ElementType.BREATH_MARK.newInstance();
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);

            assertThat(PreviewElementManager.isBreathMarkInsertionBlocked(breathMark, 1, line, true))
                .as("breath mark over an existing element is blocked")
                .isTrue();
        }

        /** A non-breath-mark type at index 0 must not be blocked. */
        @Test
        void testNonBreathMarkAtIndexZeroIsNotBlocked() {
            var crotchet = ElementType.CROTCHET.newInstance();
            var line = lineWith(ElementType.CROTCHET);

            assertThat(PreviewElementManager.isBreathMarkInsertionBlocked(crotchet, 0, line, false))
                .as("non-breath-mark at xIndex 0 is not blocked")
                .isFalse();
        }

        /** A non-breath-mark type over an existing element is not blocked (it may replace). */
        @Test
        void testNonBreathMarkOverExistingElementIsNotBlocked() {
            var crotchet = ElementType.CROTCHET.newInstance();
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);

            assertThat(PreviewElementManager.isBreathMarkInsertionBlocked(crotchet, 1, line, true))
                .as("non-breath-mark over an existing element is not blocked")
                .isFalse();
        }

        /** A null preview element must never be blocked regardless of index. */
        @Test
        void testNullPreviewElementIsNotBlocked() {
            var line = lineWith(ElementType.CROTCHET);

            assertThat(PreviewElementManager.isBreathMarkInsertionBlocked(null, 0, line, false))
                .as("null preview element is not blocked")
                .isFalse();
        }

        /**
         * A breath mark directly after a note that is glissando-connected to the
         * following note must be blocked — it would unexpectedly break the glissando.
         */
        @Test
        void testBreathMarkAfterGlissandoConnectedNoteIsBlocked() {
            var breathMark = ElementType.BREATH_MARK.newInstance();
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);
            line.getElement(0).setGlissando();

            assertThat(PreviewElementManager.isBreathMarkInsertionBlocked(breathMark, 1, line, false))
                .as("breath mark after a glissando-connected note is blocked")
                .isTrue();
        }

        /**
         * A breath mark inserted between two notes — preceding element valid, the
         * following element is not a breath mark — is the allowed happy path.
         */
        @Test
        void testBreathMarkBetweenTwoNotesIsNotBlocked() {
            var breathMark = ElementType.BREATH_MARK.newInstance();
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);

            assertThat(PreviewElementManager.isBreathMarkInsertionBlocked(breathMark, 1, line, false))
                .as("breath mark between two notes is not blocked")
                .isFalse();
        }

        /**
         * A breath mark appended at the very end of the line ({@code xIndex} equals the
         * effective element count) is not blocked: the consecutive-breath-mark guard
         * short-circuits on the bounds check rather than reading past the end.
         */
        @Test
        void testBreathMarkAppendedAtEndIsNotBlocked() {
            var breathMark = ElementType.BREATH_MARK.newInstance();
            var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);

            assertThat(PreviewElementManager.isBreathMarkInsertionBlocked(
                breathMark, line.effectiveElementCount(), line, false))
                .as("breath mark appended at the end of the line is not blocked")
                .isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // calculateStaffPositionFromMouse
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CalculateStaffPositionFromMouse {

        /**
         * Mouse at middle line → staff position 0 (no offset).
         */
        @Test
        void testMouseAtMiddleLineReturnsZero() {
            var middleYSs = 5.0;
            assertThat(PreviewElementManager.calculateStaffPositionFromMouse(middleYSs, middleYSs))
                .as("mouse on middle line yields sp 0")
                .isEqualTo(0);
        }

        /**
         * Mouse one staff-space below the middle line (offset = +1.0 ss) → sp +2
         * (one ss == 2 half-staff-spaces).
         */
        @Test
        void testMouseOneStaffSpaceBelowReturnsPositiveSp() {
            // ssToSp(1.0) = round(1.0 / 0.5) = 2
            var middleYSs = 5.0;
            var mouseYSs = middleYSs + Staff.STAFF_POSITION_OFFSET_SS * 2;
            assertThat(PreviewElementManager.calculateStaffPositionFromMouse(mouseYSs, middleYSs))
                .as("one staff-space below middle yields sp +2")
                .isEqualTo(2);
        }

        /**
         * Rounding at the half-way point: offset = 0.25 ss above middle (negative direction)
         * rounds to the nearest half-staff-space position.
         * ssToSp(-0.25) = round(-0.25 / 0.5) = round(-0.5) = 0 (Java rounds half-up toward
         * positive infinity, so Math.round(-0.5) = 0).
         */
        @Test
        void testHalfStepBoundaryRoundsCorrectly() {
            // offset = -0.25 ss → ssToSp(-0.25) = round(-0.5) = 0
            var middleYSs = 4.0;
            var mouseYSs = middleYSs - 0.25;
            assertThat(PreviewElementManager.calculateStaffPositionFromMouse(mouseYSs, middleYSs))
                .as("0.25ss above middle rounds to sp 0")
                .isEqualTo(0);
        }
    }

    // -----------------------------------------------------------------------
    // isValidStaffPosition
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class IsValidStaffPosition {

        /** Minimum bound is inclusive. */
        @Test
        void testMinBoundIsValid() {
            assertThat(PreviewElementManager.isValidStaffPosition(Staff.MIN_STAFF_POSITION_SP))
                .as("MIN_STAFF_POSITION_SP is valid")
                .isTrue();
        }

        /** Maximum bound is inclusive. */
        @Test
        void testMaxBoundIsValid() {
            assertThat(PreviewElementManager.isValidStaffPosition(Staff.MAX_STAFF_POSITION_SP))
                .as("MAX_STAFF_POSITION_SP is valid")
                .isTrue();
        }

        /** One below the minimum is invalid. */
        @Test
        void testOneBelowMinIsInvalid() {
            assertThat(PreviewElementManager.isValidStaffPosition(Staff.MIN_STAFF_POSITION_SP - 1))
                .as("MIN-1 is invalid")
                .isFalse();
        }

        /** One above the maximum is invalid. */
        @Test
        void testOneAboveMaxIsInvalid() {
            assertThat(PreviewElementManager.isValidStaffPosition(Staff.MAX_STAFF_POSITION_SP + 1))
                .as("MAX+1 is invalid")
                .isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // applyStaffPosition
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ApplyStaffPosition {

        private static final int MOUSE_SP = 4;

        /**
         * A rest element snaps to its type's default staff position, ignoring the
         * mouse-derived argument.
         */
        @Test
        void testRestSnapsToDefaultPosition() {
            var rest = ElementType.CROTCHET_REST.newInstance();
            var defaultSp = rest.getType().getDefaultStaffPosition();

            PreviewElementManager.applyStaffPosition(rest, MOUSE_SP);

            assertThat(rest.getStaffPosition())
                .as("rest staff position equals type default, not mouse position")
                .isEqualTo(defaultSp);
        }

        /**
         * A pitched note uses the exact mouse-derived staff position.
         */
        @Test
        void testPitchedNoteUsesMousePosition() {
            var note = ElementType.CROTCHET.newInstance();

            PreviewElementManager.applyStaffPosition(note, MOUSE_SP);

            assertThat(note.getStaffPosition())
                .as("pitched note staff position equals mouse-derived sp")
                .isEqualTo(MOUSE_SP);
        }

        /**
         * Verify that rest's default position is actually different from the mouse position
         * used in the test, so the rest-snap test is meaningful rather than trivially green.
         */
        @Test
        void testRestDefaultPositionDiffersFromMousePosition() {
            var rest = ElementType.CROTCHET_REST.newInstance();
            assertThat(rest.getType().getDefaultStaffPosition())
                .as("test pre-condition: rest default sp differs from mouse sp used in other tests")
                .isNotEqualTo(MOUSE_SP);
        }
    }
}
