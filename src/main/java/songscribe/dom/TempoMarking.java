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

/**
 * What a tempo draws: either a metronome marking, or a description on its own.
 *
 * <p>These are the only two shapes a marking takes, so the type is sealed over them. The pair
 * that draws nothing at all — no metronome and no text — is not one of the two, which is why
 * nothing anywhere has to ask whether a tempo is visible. {@link Metronome} always draws a
 * glyph. {@link TextOnly} always draws text, because it refuses text that is blank.
 *
 * <p>A marking is a value. It is immutable, it compares by value, and {@link Tempo} replaces its
 * own rather than changing one in place.
 *
 * <p>{@code docs/song-tempo.md} carries the reasoning, and describes what each case looks like
 * on the page.
 */
public sealed interface TempoMarking {

    /**
     * @return the text this marking draws, which is {@code ""} only in the {@link Metronome}
     *         case
     */
    String description();

    /**
     * The metronome glyph, an {@code =}, the speed in beats per minute, and then the description
     * when the marking carries one.
     *
     * @param description the text drawn after the speed, {@code ""} for none
     */
    record Metronome(String description) implements TempoMarking {}

    /**
     * The description on its own, with no glyph and no speed.
     *
     * <p>The speed and the beat unit still live on the {@link Tempo}. They are not drawn here,
     * and they still drive beaming and playback.
     *
     * @param description the text drawn, which is the whole marking
     */
    record TextOnly(String description) implements TempoMarking {

        /**
         * @throws IllegalArgumentException when {@code description} is blank, because a marking
         *                                  that draws no glyph and no text would be invisible on
         *                                  the page and unreachable by a click
         */
        public TextOnly {
            if (description.isBlank()) {
                throw new IllegalArgumentException("A text-only tempo marking needs text");
            }
        }
    }

    /**
     * What {@link #fromFile} read.
     *
     * @param marking  the marking the file states, repaired where it had to be
     * @param repaired whether the file stated the pair that draws nothing, so that a caller can
     *                 report the repair in its own words
     */
    record FromFile(TempoMarking marking, boolean repaired) {}

    /**
     * The marking a file states, with the pair that draws nothing repaired to a metronome.
     *
     * <p>This is the conversion every file reader makes, and the point at which the file's two
     * separate values become one marking. It repairs rather than discards, because only the hide
     * flag is wrong: the beat unit and the speed beside it are good, and they drive beaming and
     * playback. A discarded song tempo would silently revert to the defaults, and a discarded
     * tempo change would vanish.
     *
     * <p>The description is stripped, so text of whitespace alone counts as no text at all and
     * is repaired the same way an absent description is.
     *
     * @param description   the description the file states, {@code ""} when it states none
     * @param hideMetronome whether the file asks for the glyph and the speed to be left out
     * @return the marking, and whether it repaired the pair
     * @invariant the marking is never a {@link TextOnly} carrying blank text
     */
    static FromFile fromFile(String description, boolean hideMetronome) {
        var text = description.strip();

        if (hideMetronome && !text.isEmpty()) {
            return new FromFile(new TextOnly(text), false);
        }

        // Reached either because the file shows the metronome, or because it hides one that
        // carries no text — which is the pair that would draw nothing.
        return new FromFile(new Metronome(text), hideMetronome);
    }
}
