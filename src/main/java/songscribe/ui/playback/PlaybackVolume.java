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

package songscribe.ui.playback;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.prefs.PrefsValue;

/**
 * The volume playback runs at, as one of the five steps the user can choose.
 *
 * <p><b>The steps are not evenly spaced</b>, which is why a slider over them is built on
 * their {@linkplain #ordinal positions} rather than on their percentages.
 *
 * <p>Two steps have no label: the unmarked notches between the marked ones.
 */
public enum PlaybackVolume implements PrefsValue {

    PERCENT_50(50, Strings.LABEL_PREFS_SOFTER),
    PERCENT_63(63, null),
    PERCENT_75(75, Strings.LABEL_PREFS_SOFT),
    PERCENT_88(88, null),
    PERCENT_100(100, Strings.LABEL_PREFS_FULL);

    private final int percent;
    private final @Nullable String labelKey;

    PlaybackVolume(int percent, @Nullable String labelKey) {
        this.percent = percent;
        this.labelKey = labelKey;
    }

    public int percent() {
        return percent;
    }

    /** @return {@code null} for a step the product leaves unmarked */
    public @Nullable String label() {
        return labelKey == null ? null : Strings.get(labelKey);
    }

    @Override
    public String storedValue() {
        return Integer.toString(percent);
    }

    /** @param position the {@linkplain #ordinal position} of a step */
    public static PlaybackVolume atPosition(int position) {
        return values()[position];
    }

    public static int[] positions() {
        var positions = new int[values().length];

        for (var i = 0; i < positions.length; i++) {
            positions[i] = i;
        }

        return positions;
    }

    /** @return in declaration order, with {@code null} for an unmarked step */
    public static @Nullable String[] labels() {
        var values = values();
        @Nullable String[] labels = new String[values.length];

        for (var i = 0; i < values.length; i++) {
            labels[i] = values[i].label();
        }

        return labels;
    }
}
