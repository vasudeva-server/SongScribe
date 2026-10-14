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

/**
 * The set of integers a numeric input accepts, and whether it also accepts nothing
 * at all.
 *
 * <p>A rule about numbers rather than about Swing, so the field, a binding and a controller can
 * all ask it without holding a text component.
 *
 * <p>The two queries differ only in blank text: {@link #containsValue} says whether the text
 * names a number in the range, and blank names none; {@link #acceptsInput} says whether a field
 * should let the text stand, which for blank is what {@link Blank} says.
 */
public record NumericRange(int min, int max, Blank blank) {

    public enum Blank {
        ACCEPTED,

        REJECTED
    }

    /**
     * Surrounding whitespace is ignored. Blank text is never contained, whatever {@link #blank}
     * says: that policy is about what a field tolerates, not what the range holds.
     */
    public boolean containsValue(String text) {
        var stripped = text.strip();

        return !stripped.isEmpty() && parses(stripped);
    }

    /**
     * Blank text is accepted exactly when {@link #blank} is {@link Blank#ACCEPTED}. The
     * focus-time verifier asks this, so a field the user may leave empty does not trap them.
     */
    public boolean acceptsInput(String text) {
        if (text.isBlank()) {
            return blank == Blank.ACCEPTED;
        }

        return containsValue(text);
    }

    private boolean parses(String stripped) {
        try {
            var value = Integer.parseInt(stripped);

            return value >= min && value <= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
