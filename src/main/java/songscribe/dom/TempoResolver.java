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
 * Answers "what is in effect here?" for the tempo and the beat. Reads the score and never writes
 * to it.
 */
public final class TempoResolver {

    private final Song song;

    TempoResolver(Song song) {
        this.song = song;
    }

    public Tempo getTempoAt(int lineIndex, int noteIndex) {
        var found = walkBackFrom(lineIndex, noteIndex,
            element -> element.findAttachment(TempoChangeAttachment.class));

        if (found == null) {
            return song.getTempo();
        }

        return found.value().getTempo();
    }

    /**
     * Not cached: a stale beat would be a silent wrong-beat failure.
     */
    public BeatUnit beatUnitAt(int lineIndex, int elementIndex) {
        var found = walkBackFrom(lineIndex, elementIndex, TempoResolver::beatDefinedAt);

        if (found != null) {
            return found.value();
        }

        return song.getTempo().tempoType();
    }

    /**
     * Null when {@code element} defines no beat. When one element carries both kinds of marking
     * the metric modulation wins, being the more specific statement about the beat. The single
     * definition of that precedence, shared with {@link #isBeatChange}.
     */
    static @Nullable BeatUnit beatDefinedAt(StaffElement element) {
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
     * Whether {@code element} redefines the beat in a way that splits a span running across it: a
     * tempo marking does so only when its note value differs from {@code runningBeat}.
     */
    static boolean isBeatChange(StaffElement element, BeatUnit runningBeat) {
        if (element.findAttachment(BeatChangeAttachment.class) != null) {
            return true;
        }

        var definedBeat = beatDefinedAt(element);

        return definedBeat != null && definedBeat != runningBeat;
    }

    /**
     * @return null when the walk reached the start of the song without a hit
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

    private record Found<T>(T value, int lineIndex, int elementIndex) {}

    @FunctionalInterface
    private interface BackwardProbe<T> {
        @Nullable T examine(StaffElement element);
    }
}
