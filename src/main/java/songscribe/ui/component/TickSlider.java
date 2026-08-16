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

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import javax.swing.JLabel;
import javax.swing.JSlider;

import org.jspecify.annotations.Nullable;

/**
 * A {@link JSlider} subclass that fires {@link #tickDidChange(int)} only when
 * the slider value lands on one of the provided tick stops. This works during
 * drag (not just on mouse release), which avoids the Swing limitation where
 * {@code mouseReleased} is not delivered when the mouse is released outside
 * the parent window.
 *
 * <p>Subclasses must override {@link #tickDidChange(int)} to respond to
 * tick changes (e.g., update preferences, sync playback settings).</p>
 */
public abstract class TickSlider extends JSlider {

    private final Set<Integer> stopSet;
    private final int[] stops;
    private int lastCommittedValue;

    /**
     * Creates a new TickSlider.
     *
     * @param stops  valid tick positions, sorted in ascending order and evenly spaced
     * @param labels parallel array (same length as {@code stops}); non-null entries
     *               become labels at the corresponding stop, null entries have no label
     */
    protected TickSlider(int[] stops, @Nullable String[] labels) {
        super(stops[0], stops[stops.length - 1]);

        this.stops = stops;
        stopSet = new HashSet<>();

        for (var stop : stops) {
            stopSet.add(stop);
        }

        var tickSpacing = stops[1] - stops[0];
        setMajorTickSpacing(tickSpacing);
        setSnapToTicks(true);
        setPaintTicks(true);
        setPaintLabels(true);

        //noinspection UseOfObsoleteCollectionType -- Swing's setLabelTable uses Hashtable, so we have to use it too.
        var labelTable = new Hashtable<Integer, JLabel>();

        for (var i = 0; i < stops.length; i++) {
            if (labels[i] != null) {
                labelTable.put(stops[i], new JLabel(labels[i]));
            }
        }

        setLabelTable(labelTable);
        lastCommittedValue = getValue();

        addChangeListener(_ -> {
            var value = getValue();

            if (stopSet.contains(value) && value != lastCommittedValue) {
                lastCommittedValue = value;
                tickDidChange(value);
            }
        });
    }

    /**
     * Called when the slider lands on a new tick stop, either during drag
     * or on release. Subclasses override to respond to tick changes.
     *
     * @param tick the new tick stop value
     */
    protected abstract void tickDidChange(int tick);

    /**
     * Sets the slider value to the nearest valid stop. Use this when loading
     * a value from preferences that may not exactly match a stop.
     *
     * @param value the value to snap to the nearest stop
     */
    public void setSnappedValue(int value) {
        var closest = stops[nearestStopIndex(stops, value)];

        // Update lastCommittedValue to prevent a spurious tickDidChange
        // when the change listener fires from setValue.
        lastCommittedValue = closest;
        setValue(closest);
    }

    /**
     * The position in {@code stops} of the stop nearest {@code value}.
     *
     * <p>Static and taking its stops as an argument, because a slider is not the only thing that
     * has to answer this: a stored preference whose scale differs from the one the slider is built
     * over is mapped to a stop index without a slider being involved. It is the same snap
     * {@link #setSnappedValue} performs, which asks it too, so the rule below is stated once.
     *
     * <p>A value below the first stop or above the last answers that end's position, so the result
     * is always a real index and no caller range-checks first.
     *
     * <p><strong>A tie goes to the lower position.</strong> A value exactly between two stops is
     * an equally good answer either way, so what matters is that the choice is the same every
     * time rather than which way it goes.
     *
     * @param stops the stop values, ascending and non-empty
     * @param value the value to snap
     * @return the position of the nearest stop, from 0 to {@code stops.length - 1}
     */
    public static int nearestStopIndex(int[] stops, int value) {
        var closestIndex = 0;
        var minDist = Math.abs(value - stops[0]);

        for (var i = 1; i < stops.length; i++) {
            var dist = Math.abs(value - stops[i]);

            if (dist < minDist) {
                minDist = dist;
                closestIndex = i;
            }
        }

        return closestIndex;
    }
}
