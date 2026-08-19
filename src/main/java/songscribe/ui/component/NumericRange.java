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
 * <p>A rule about numbers rather than about Swing. It exists as its own type so that
 * the same rule can be asked by the field that enforces it, by a binding that derives
 * from what the user has typed, and by a controller validating before it commits —
 * without any of them holding a reference to a text component. A rule reachable only
 * through a live control is a rule the other two have to copy, and the second copy is
 * what tells the user something different about one mistake.
 *
 * <p>The two queries differ only in what they make of blank text, which is the whole
 * reason {@link Blank} exists: {@link #containsValue} answers whether the text names
 * a number in the range, and blank names no number; {@link #acceptsInput} answers
 * whether a field carrying this range should let the text stand, which for blank text
 * is what {@link Blank} says.
 *
 * @param min the smallest accepted value, inclusive
 * @param max the largest accepted value, inclusive
 * @param blank what an empty or all-whitespace entry means for this range
 */
public record NumericRange(int min, int max, Blank blank) {

    /** Whether a field carrying a range may be left with nothing in it. */
    public enum Blank {
        /** An empty entry stands, and no range check is made. */
        ACCEPTED,

        /** An empty entry is refused, as an out-of-range one is. */
        REJECTED
    }

    /**
     * Returns whether {@code text} names an integer this range contains.
     *
     * <p>Surrounding whitespace is ignored, so text a user has typed can be passed
     * without being tidied first. Blank text names no integer and is therefore never
     * contained, whatever {@link #blank} says — that policy is about what a field
     * tolerates, not about what the range holds.
     *
     * @param text the text to test; it need not come from any particular control
     * @return {@code true} when {@code text} strips to a non-empty string that parses
     *     as an integer between {@link #min} and {@link #max} inclusive
     * @invariant blank text answers {@code false} for every range, including one
     *     whose {@link #blank} is {@link Blank#ACCEPTED}.
     */
    public boolean containsValue(String text) {
        var stripped = text.strip();

        return !stripped.isEmpty() && parses(stripped);
    }

    /**
     * Returns whether a field carrying this range should let {@code text} stand.
     *
     * <p>The same question as {@link #containsValue} except for blank text, which is
     * accepted exactly when {@link #blank} is {@link Blank#ACCEPTED}. This is the
     * form a focus-time verifier asks, because a field the user is allowed to leave
     * empty must not trap them in it.
     *
     * @param text the text to test
     * @return {@code true} when {@code text} is blank and {@link #blank} is
     *     {@link Blank#ACCEPTED}, or when {@link #containsValue} holds
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
