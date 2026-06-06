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
import songscribe.dom.StaffElement;
import songscribe.layout.StaffExtents;

/**
 * Unit tests for the pure-logic static methods in {@link PreviewElementManager}:
 * {@link PreviewElementManager#calculateStaffPositionFromMouse},
 * {@link PreviewElementManager#isValidStaffPosition}, and
 * {@link PreviewElementManager#applyStaffPosition}.
 * None of these methods touch UI or messaging state.
 */
class PreviewElementManagerStaticMethodsTest extends UnitTest {

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
            var mouseYSs = middleYSs + StaffExtents.STAFF_POSITION_OFFSET_SS * 2;
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
            assertThat(PreviewElementManager.isValidStaffPosition(StaffExtents.MIN_STAFF_POSITION_SP))
                .as("MIN_STAFF_POSITION_SP is valid")
                .isTrue();
        }

        /** Maximum bound is inclusive. */
        @Test
        void testMaxBoundIsValid() {
            assertThat(PreviewElementManager.isValidStaffPosition(StaffExtents.MAX_STAFF_POSITION_SP))
                .as("MAX_STAFF_POSITION_SP is valid")
                .isTrue();
        }

        /** One below the minimum is invalid. */
        @Test
        void testOneBelowMinIsInvalid() {
            assertThat(PreviewElementManager.isValidStaffPosition(StaffExtents.MIN_STAFF_POSITION_SP - 1))
                .as("MIN-1 is invalid")
                .isFalse();
        }

        /** One above the maximum is invalid. */
        @Test
        void testOneAboveMaxIsInvalid() {
            assertThat(PreviewElementManager.isValidStaffPosition(StaffExtents.MAX_STAFF_POSITION_SP + 1))
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
