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

import songscribe.message.mutation.ElementField;

/**
 * Answers "what will be the song's first element after this pending edit?" — a lookahead the
 * DOM itself has no use for, because by the time a {@link Line} or {@link Song} mutator runs
 * the edit is already happening. UI code has to decide <em>before</em> mutating: whether the
 * starting tempo will be displaced, and whether its new home already carries a tempo change
 * the user must be asked about.
 *
 * <p>The one mutator here, {@link #replaceInitialTempo}, applies the answer the user gave.
 * Everything else is a pure query.
 *
 * <p>The queries assume the pending edit actually displaces the anchor — callers establish
 * that with {@link Line#isInitialTempoAnchor}, which is true exactly when the removal starts
 * at index 0 of the song's first non-empty line.
 */
public final class InitialTempoTransfer {

    private InitialTempoTransfer() {
    }

    /**
     * The song's starting tempo as it is attached right now, or null when the song has no
     * anchor element or that element carries no tempo change.
     */
    public static @Nullable TempoChangeAttachment currentInitialTempo(Song song) {
        var anchor = song.initialTempoAnchor();

        return anchor == null ? null : anchor.findAttachment(TempoChangeAttachment.class);
    }

    /**
     * The element that will be the song's first once {@code [begin, end]} is removed from
     * {@code line}, or null when the removal leaves the song with no elements at all.
     *
     * <p>The range is widened through {@link Line#effectiveDeleteRange} first: a deletion
     * takes a paired grace note before {@code begin} and a breath mark after {@code end}
     * along with it, so the raw selection is not the range that disappears.
     */
    public static @Nullable StaffElement anchorAfterRemoval(Song song, Line line,
                                                            int begin, int end) {
        var widened = line.effectiveDeleteRange(begin, end);
        var survivor = widened.end() + 1;

        if (survivor < line.elementCount()) {
            return line.getElement(survivor);
        }

        return firstElementAfterLine(song, song.indexOfLine(line));
    }

    /**
     * The element that will be the song's first once the whole line at {@code lineIndex} is
     * removed, or null when nothing with elements follows it.
     */
    public static @Nullable StaffElement anchorAfterLineRemoval(Song song, int lineIndex) {
        return firstElementAfterLine(song, lineIndex);
    }

    /** The first element of the first line after {@code lineIndex} that has any. */
    private static @Nullable StaffElement firstElementAfterLine(Song song, int lineIndex) {
        var nextNonEmpty = song.firstNonEmptyLineIndex(lineIndex + 1);

        return nextNonEmpty < 0 ? null : song.getLine(nextNonEmpty).getElement(0);
    }

    /**
     * Replaces whatever tempo change sits on the song's anchor element with one carrying
     * {@code tempo} — the mutation behind the user answering "yes, keep the original starting
     * tempo" when the new first element brought a tempo change of its own.
     *
     * <p>Routed through the anchor line's {@link Line#modifyElement} so undo sees the field
     * change, and through {@link Song#withBeatDefiningEditOn} because a tempo change defines
     * the beat and any tuplet the new beat invalidates has to go. No-op when the song has no
     * anchor element. Callers invoke this inside an already-open modification bracket.
     */
    public static void replaceInitialTempo(Song song, Tempo tempo) {
        var anchorLine = song.initialTempoAnchorLine();

        if (anchorLine == null) {
            return;
        }

        var anchor = anchorLine.getElement(0);

        anchorLine.modifyElement(0, ElementField.TEMPO_CHANGE,
            () -> Song.withBeatDefiningEditOn(anchor, () -> {
                var existing = anchor.findAttachment(TempoChangeAttachment.class);

                if (existing != null) {
                    anchor.removeAttachment(existing);
                }

                anchor.addAttachment(new TempoChangeAttachment(anchor, tempo));
            }));
    }
}
