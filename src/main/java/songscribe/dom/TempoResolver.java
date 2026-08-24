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

import org.jspecify.annotations.Nullable;

/**
 * Answers "what is in effect here?" for the two things a preceding event can define —
 * the tempo and the beat — by walking backward from a position to the nearest event
 * that defines one.
 *
 * <p>Pure query logic over a {@link Song}'s lines: it reads the score and never writes
 * to it. One is owned by each {@code Song}, which delegates {@link Song#getTempoAt} and
 * {@link Song#resolveBeatAt} here.
 */
public final class TempoResolver {

    private final Song song;

    TempoResolver(Song song) {
        this.song = song;
    }

    /**
     * Returns the effective tempo at a given position in the song.
     * Walks backwards through lines and notes to find the most recent tempo change,
     * or returns the effective song tempo if none found.
     *
     * @param lineIndex The index of the line
     * @param noteIndex The index of the note within the line
     * @return The effective tempo at this position
     */
    public Tempo getTempoAt(int lineIndex, int noteIndex) {
        var found = walkBackFrom(lineIndex, noteIndex,
            element -> element.findAttachment(TempoChangeAttachment.class));

        if (found == null) {
            return song.getTempo();
        }

        return found.value().getTempo();
    }

    /**
     * Returns the beat in effect at a position, found by walking backward from that
     * position to the nearest preceding beat-defining event.
     *
     * <p>The walk starts at the anchor element and runs backward through its line, then through
     * every earlier line in full. The first hit wins, whichever kind it is: a
     * {@code BeatChangeAttachment} yields {@code beatChange().beat()} and a
     * {@code TempoChangeAttachment} yields {@code tempo().tempoType()}. With no hit at all the
     * result falls back to the song tempo, which every song has.
     *
     * <p>Precedence is positional, not by type: the nearest preceding beat-defining event
     * wins regardless of which kind it is. When one element carries both, the
     * {@link BeatChangeAttachment} wins, because a metric-modulation marking is the more
     * specific statement about the beat than a tempo marking's note value.
     *
     * <p>Cost is O(elements before the anchor), with no cache. No attachment is involved in
     * the fallback: the song tempo defines the beat at the start of the song directly, so a
     * walk that reaches the front of the score always has an answer waiting.
     * A maintained beat index on {@code Song} was rejected: it would trade microseconds for
     * an invalidation invariant that every structural mutation would have to honor, and a
     * stale index produces exactly the silent wrong-beat failure this method exists to
     * prevent.
     *
     * @param lineIndex    the index of the line
     * @param elementIndex the index of the anchor element within that line
     * @return the beat in effect at this position and the position that defined it
     */
    public BeatAt resolveBeatAt(int lineIndex, int elementIndex) {
        var found = walkBackFrom(lineIndex, elementIndex, TempoResolver::beatDefinedAt);

        if (found != null) {
            return new BeatAt(found.value(), found.lineIndex(), found.elementIndex());
        }

        return new BeatAt(
            song.getTempo().tempoType(), BeatAt.NO_DEFINING_EVENT, BeatAt.NO_DEFINING_EVENT);
    }

    /**
     * The beat {@code element} defines, or null when it defines none.
     *
     * <p>When one element carries both kinds of marking the metric modulation wins, because
     * it is the more specific statement about the beat than a tempo marking's note value.
     * This is the single definition of that precedence: {@code TupletValidator}'s forward
     * walk calls it too, so the two cannot answer differently for the same element.
     */
    static @Nullable Duration beatDefinedAt(StaffElement element) {
        var beatChange = element.findAttachment(BeatChangeAttachment.class);

        if (beatChange != null) {
            return beatChange.getBeatChange().beat();
        }

        var tempoChange = element.findAttachment(TempoChangeAttachment.class);

        if (tempoChange != null) {
            return tempoChange.getTempo().tempoType();
        }

        return null;
    }

    /**
     * Walks backward from a position — through the rest of that line, then through every
     * earlier line in full — and returns the first element for which {@code probe} produces
     * a non-null value, together with where it was found.
     *
     * <p>The start element's own line is scanned backward from that element only; every earlier
     * line is scanned backward in full.
     *
     * <p>Cost is O(elements before the start), with no cache. Both callers — the tempo
     * lookup and the beat lookup — share this walk so that a change to what "before this
     * position" means cannot be fixed in one and forgotten in the other.
     *
     * @return the first hit, or null when the walk reached the start of the song without one
     */
    private <T> @Nullable Found<T> walkBackFrom(
        int lineIndex, int elementIndex, BackwardProbe<? extends T> probe
    ) {
        var lastLine = true;

        for (var i = lineIndex; i >= 0; i--) {
            var currentLine = song.getLine(i);

            for (
                var index = lastLine ? elementIndex : (currentLine.elementCount() - 1);
                index >= 0;
                index--
            ) {
                var value = probe.examine(currentLine.getElement(index));

                if (value != null) {
                    return new Found<>(value, i, index);
                }
            }

            lastLine = false;
        }

        return null;
    }

    /** Where a backward walk stopped, and what it found there. */
    private record Found<T>(T value, int lineIndex, int elementIndex) {}

    /**
     * Examines one element during a backward walk, returning what it defines or null when
     * it defines nothing. A dedicated interface rather than {@code Function} so the nullable
     * return is part of the contract.
     */
    @FunctionalInterface
    private interface BackwardProbe<T> {
        @Nullable T examine(StaffElement element);
    }
}
