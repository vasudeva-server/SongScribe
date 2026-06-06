/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Unit tests for {@link TickSlider} covering:
 * <ul>
 *   <li>Row 34 — Change listener: {@code tickDidChange} fired only when value is in
 *       {@code stopSet} AND differs from {@code lastCommittedValue}</li>
 *   <li>Row 35 — {@code setSnappedValue}: nearest stop on exact hit; no spurious callback</li>
 *   <li>Row 36 — {@code setSnappedValue}: nearest stop on off-stop value</li>
 *   <li>Row 37 — {@code setSnappedValue}: suppresses spurious {@code tickDidChange} after snap</li>
 * </ul>
 */
class TickSliderTest extends UnitTest {

    // Stop values for all tests
    private static final int FIRST_STOP = 10;
    private static final int SECOND_STOP = 20;
    private static final int THIRD_STOP = 30;
    private static final int LAST_STOP = 40;

    // Stops array built from the named constants to avoid duplication
    private static final int[] STOPS = { FIRST_STOP, SECOND_STOP, THIRD_STOP, LAST_STOP };

    // Labels must be non-null at endpoints so BasicSliderUI.calculateTrackBuffer()
    // can find a highLabel and lowLabel — an all-null table causes a NPE in headless Swing.
    private static final String[] LABELS = { String.valueOf(FIRST_STOP), null, null, String.valueOf(LAST_STOP) };

    // Off-stop value that is closer to SECOND_STOP than to FIRST_STOP
    private static final int OFF_STOP_NEAR_SECOND = 18;

    // Off-stop value that is closer to THIRD_STOP than to SECOND_STOP
    private static final int OFF_STOP_NEAR_THIRD = 27;

    /** Concrete subclass that records every call to {@code tickDidChange}. */
    private static class TestSlider extends TickSlider {

        final List<Integer> ticks = new ArrayList<>();

        TestSlider() {
            super(STOPS, LABELS);
        }

        @Override
        protected void tickDidChange(int tick) {
            ticks.add(tick);
        }

        int tickCount() {
            return ticks.size();
        }
    }

    private TestSlider slider;

    @BeforeEach
    void setUp() {
        slider = new TestSlider();
        // Clear any tick that may have fired during construction
        slider.ticks.clear();
    }

    // -----------------------------------------------------------------------
    // Row 34: tickDidChange fired only when value in stopSet AND differs from
    // lastCommittedValue
    // -----------------------------------------------------------------------

    @Test
    void testTickDidChangeFiresWhenValueIsAStopAndDiffersFromLastCommitted() {
        // Initial slider value is FIRST_STOP (set in constructor).
        // Move to a different stop — should fire once.
        slider.setValue(SECOND_STOP);

        assertThat(slider.tickCount()).isEqualTo(1);
        assertThat(slider.ticks.get(0)).isEqualTo(SECOND_STOP);
    }

    @Test
    void testTickDidChangeDoesNotFireWhenValueIsSameStop() {
        // Move to a new stop first so lastCommittedValue is updated.
        slider.setValue(SECOND_STOP);
        slider.ticks.clear();

        // Set the same stop again — change listener fires but condition fails.
        slider.setValue(SECOND_STOP);

        assertThat(slider.tickCount()).isEqualTo(0);
    }

    @Test
    void testTickDidChangeFiresOnceForEachDistinctStop() {
        // Move through three distinct stops — each should fire exactly once.
        slider.setValue(SECOND_STOP);
        slider.setValue(THIRD_STOP);
        slider.setValue(LAST_STOP);

        assertThat(slider.tickCount()).isEqualTo(3);
        assertThat(slider.ticks).containsExactly(SECOND_STOP, THIRD_STOP, LAST_STOP);
    }

    // -----------------------------------------------------------------------
    // Row 35: setSnappedValue selects nearest stop on exact-stop input;
    // no spurious tickDidChange fires
    // -----------------------------------------------------------------------

    @Test
    void testSetSnappedValueOnExactStopSelectsThatStop() {
        slider.setSnappedValue(THIRD_STOP);

        assertThat(slider.getValue()).isEqualTo(THIRD_STOP);
    }

    @Test
    void testSetSnappedValueOnExactStopDoesNotFireTickDidChange() {
        slider.setSnappedValue(SECOND_STOP);

        assertThat(slider.tickCount()).isEqualTo(0);
    }

    // -----------------------------------------------------------------------
    // Row 36: setSnappedValue selects nearest stop on off-stop input
    // -----------------------------------------------------------------------

    @Test
    void testSetSnappedValueNearSecondStopSnapsToSecondStop() {
        // OFF_STOP_NEAR_SECOND (18) is closer to SECOND_STOP (20) than to FIRST_STOP (10)
        slider.setSnappedValue(OFF_STOP_NEAR_SECOND);

        assertThat(slider.getValue()).isEqualTo(SECOND_STOP);
    }

    @Test
    void testSetSnappedValueNearThirdStopSnapsToThirdStop() {
        // OFF_STOP_NEAR_THIRD (27) is closer to THIRD_STOP (30) than to SECOND_STOP (20)
        slider.setSnappedValue(OFF_STOP_NEAR_THIRD);

        assertThat(slider.getValue()).isEqualTo(THIRD_STOP);
    }

    @Test
    void testSetSnappedValueBelowFirstStopSnapsToFirstStop() {
        slider.setSnappedValue(FIRST_STOP - 1);

        assertThat(slider.getValue()).isEqualTo(FIRST_STOP);
    }

    @Test
    void testSetSnappedValueAboveLastStopSnapsToLastStop() {
        slider.setSnappedValue(LAST_STOP + 1);

        assertThat(slider.getValue()).isEqualTo(LAST_STOP);
    }

    // -----------------------------------------------------------------------
    // Row 37: setSnappedValue updates lastCommittedValue so that the
    // subsequent setValue call inside setSnappedValue does NOT fire tickDidChange
    // -----------------------------------------------------------------------

    @Test
    void testSetSnappedValueSuppressesSpuriousTickDidChange() {
        // Snap to a stop that differs from the initial value.
        // If lastCommittedValue were NOT updated before setValue, the change
        // listener would fire tickDidChange — but it must not.
        slider.setSnappedValue(LAST_STOP);

        assertThat(slider.tickCount()).isEqualTo(0);
    }

    @Test
    void testSetSnappedValueThenNormalSetValueCanStillFireTickDidChange() {
        // After snapping to LAST_STOP, a normal setValue to a *different* stop
        // should still fire tickDidChange (lastCommittedValue is now LAST_STOP).
        slider.setSnappedValue(LAST_STOP);
        slider.ticks.clear();

        slider.setValue(FIRST_STOP);

        assertThat(slider.tickCount()).isEqualTo(1);
        assertThat(slider.ticks.get(0)).isEqualTo(FIRST_STOP);
    }
}
