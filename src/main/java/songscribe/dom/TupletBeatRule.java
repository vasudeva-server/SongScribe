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

import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.message.mutation.Mutation;

/**
 * The beat rule of {@link Tuplet}: every tuplet validates under
 * {@link TupletValidator.Strictness#STRICT} against the beat in effect where it stands. An edit
 * that redefines the beat ({@link Song#beatMoveOf}) removes each tuplet from the earliest place
 * it redefines the beat onward that no longer validates.
 * <p>
 * Only tuplets from that place forward are judged, since a redefined beat reaches no element
 * before it, with one exception: a tuplet the place falls inside is anchored before it and is
 * judged too, since it is the one the edit is most likely to have broken.
 */
final class TupletBeatRule implements ModificationRule {

    /** Orders locations in song order: by line, then by element within the line. */
    private static final Comparator<ElementLocation> SONG_ORDER =
        Comparator.comparingInt(ElementLocation::lineIndex).thenComparingInt(ElementLocation::elementIndex);

    /**
     * {@inheritDoc}
     *
     * @effects when a mutation of {@code batch} redefines the beat, removes through
     *     {@link Span#removeReported} each tuplet anchored at or after the earliest such place,
     *     or enclosing it, that no longer validates, with the {@link TupletValidator.Reason} it
     *     fails for; otherwise nothing
     */
    @Override
    public void apply(Song song, List<Mutation> batch, ChangeReport report) {
        var from = earliestBeatMove(song, batch);

        if (from == null) {
            return;
        }

        for (var verdict : TupletValidator.validateFrom(
            song, startOfEnclosingTuplet(song, from), TupletValidator.Strictness.STRICT,
            TupletBeatRule::registryTupletAnchoredAt)
        ) {
            if (verdict.result() instanceof TupletValidator.Result.Invalid(var reason)) {
                verdict.tuplet().removeReported(song.getLine(verdict.lineIndex()), reason.messageKey());
            }
        }
    }

    /**
     * The earliest place in song order from which a mutation of {@code batch} redefines the
     * beat, or {@code null} when none does.
     */
    private static @Nullable ElementLocation earliestBeatMove(Song song, List<Mutation> batch) {
        ElementLocation earliest = null;

        for (var mutation : batch) {
            var move = song.beatMoveOf(mutation);

            if (move != null && (earliest == null || SONG_ORDER.compare(move, earliest) < 0)) {
                earliest = move;
            }
        }

        return earliest;
    }

    /**
     * Where to start judging for a beat redefined from {@code from}: the anchor of the tuplet
     * {@code from} falls inside, when there is one, since the forward walk opens a span only at
     * its anchor; otherwise {@code from} itself.
     *
     * @param from an element of {@code song}
     */
    private static ElementLocation startOfEnclosingTuplet(Song song, ElementLocation from) {
        var enclosing = song.getLine(from.lineIndex()).spanRegistry().findTupletAt(from.elementIndex());

        if (enclosing == null) {
            return from;
        }

        var anchorIndex = enclosing.requireAnchorElementIndex();

        return new ElementLocation(from.lineIndex(), Math.min(from.elementIndex(), anchorIndex));
    }

    /** The tuplet in {@code line}'s registry whose anchor is the element at {@code index}, if any. */
    private static TupletValidator.@Nullable Anchored<Tuplet> registryTupletAnchoredAt(Line line, int index) {
        var tuplet = line.spanRegistry().findFirstSpan(Tuplet.class, Span.anchoredAt(index));

        if (tuplet == null) {
            return null;
        }

        return new TupletValidator.Anchored<>(tuplet, tuplet.requireEndElementIndex(), tuplet.getGrade());
    }
}
