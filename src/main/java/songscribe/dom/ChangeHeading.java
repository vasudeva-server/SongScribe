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
package songscribe.dom;

import songscribe.Strings;

/**
 * What kind of change the song made on its own, as the user is told about it.
 */
public enum ChangeHeading {
    ACCIDENTALS_CONVERTED(Strings.REPORT_ACCIDENTALS_CONVERTED),
    BEAMS_REMOVED(Strings.REPORT_BEAMS_REMOVED),
    BREATH_MARKS_REMOVED(Strings.REPORT_BREATH_MARKS_REMOVED),
    ENDINGS_REMOVED(Strings.REPORT_ENDINGS_REMOVED),
    FINAL_BARLINES_REMOVED(Strings.REPORT_FINAL_BARLINES_REMOVED),
    GLISSANDOS_REMOVED(Strings.REPORT_GLISSANDOS_REMOVED),
    HAIRPINS_REMOVED(Strings.REPORT_HAIRPINS_REMOVED),
    LEADING_BARS_REMOVED(Strings.REPORT_LEADING_BARS_REMOVED),
    LYRICS_REMOVED(Strings.REPORT_LYRICS_REMOVED),
    TIES_REMOVED(Strings.REPORT_TIES_REMOVED),
    TRILLS_REMOVED(Strings.REPORT_TRILLS_REMOVED),
    TUPLETS_REMOVED(Strings.REPORT_TUPLETS_REMOVED),
    TUPLETS_UPDATED(Strings.REPORT_TUPLETS_UPDATED);

    private final String key;

    ChangeHeading(String key) {
        this.key = key;
    }

    /**
     * @return plain text
     */
    public String text() {
        return Strings.get(key);
    }
}
