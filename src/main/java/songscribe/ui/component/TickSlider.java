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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import javax.swing.JLabel;
import javax.swing.JSlider;

import org.jspecify.annotations.Nullable;

/**
 * A {@link JSlider} that reports a change only when its value lands on a tick stop, during drag
 * too, since {@code mouseReleased} is not delivered when the mouse is released outside the
 * parent window.
 *
 * <p>The reporting route is {@link #addTickListener}, which a {@code Controls.tick} view
 * observes. A programmatic {@link #setSnappedValue} does <b>not</b> report.
 */
public class TickSlider extends JSlider {

    private final Set<Integer> stopSet;
    private final int[] stops;
    private final List<Runnable> tickListeners = new ArrayList<>();
    private int lastCommittedValue;

    /**
     * @param stops valid tick positions: at least two, ascending, and evenly spaced.
     *     None of that is checked, and a violation misdraws the slider rather than
     *     failing. A domain with unevenly spaced values is bound through the stop
     *     <i>positions</i> and a conversion.
     * @param labels null entries have no label
     */
    public TickSlider(int[] stops, @Nullable String[] labels) {
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

                for (var listener : List.copyOf(tickListeners)) {
                    listener.run();
                }
            }
        });
    }

    /**
     * The listener is told that a landing happened, not what it landed on; consumers read
     * {@link #getValue}.
     */
    public void addTickListener(Runnable listener) {
        tickListeners.add(listener);
    }

    /**
     * <b>This does not report a tick</b>: the value arrives from wherever the slider's value is
     * kept, and reporting it back would be a write nobody asked for. A caller that needs the
     * change announced announces it itself.
     */
    public void setSnappedValue(int value) {
        var closest = stops[nearestStopIndex(stops, value)];

        // Suppresses the report the change listener would otherwise make when setValue
        // moves the slider.
        lastCommittedValue = closest;
        setValue(closest);
    }

    /**
     * Static because a stored preference on a different scale is mapped to a stop index without
     * a slider; {@link #setSnappedValue} uses the same snap.
     *
     * <p>A value below the first stop or above the last answers that end's position, so the
     * result is always a real index.
     *
     * <p><strong>A tie goes to the lower position.</strong>
     *
     * @param stops ascending and non-empty
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
