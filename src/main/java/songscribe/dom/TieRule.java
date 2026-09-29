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

import java.util.LinkedHashSet;
import java.util.List;

import songscribe.message.mutation.LineDeletion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.LineScopedMutation;
import songscribe.message.mutation.Mutation;

/**
 * The tie rule (see {@link NoteElement}), run over the lines an edit touched and over the line
 * before each of them, since a tie from that line can end in the touched one.
 * <p>
 * A touched line is a line a {@link LineScopedMutation} in the batch names, the line of a
 * {@link LineInsertion}, and, for a {@link LineDeletion}, the line that now sits at its
 * {@link LineDeletion#lineIndex()}, when there is one ({@link Song#touchedLine}); a named line
 * that is no longer one of the song's lines is not judged. Each line is judged once, through
 * {@link Line#applyTieRule}.
 * <p>
 * Its removals land after the changes that broke the ties. Reverse-order undo restores those
 * ties first, against the final state, where every anchor is still in its line.
 */
final class TieRule implements ModificationRule {

    /**
     * {@inheritDoc}
     *
     * @effects for each tie in the judged lines that does not hold, records a
     *     {@link songscribe.Strings#DIALOG_REPORT_TIES_REMOVED} change into {@code report} and posts a
     *     {@link songscribe.message.mutation.TieRemoval}
     */
    @Override
    public void apply(Song song, List<Mutation> batch, ChangeReport report) {
        var judged = new LinkedHashSet<Line>();

        for (var mutation : batch) {
            var touched = song.touchedLine(mutation);
            var touchedIndex = touched == null ? null : song.indexOfLine(touched);

            if (touched == null || touchedIndex == null) {
                continue;
            }

            if (touchedIndex > 0) {
                judged.add(song.getLine(touchedIndex - 1));
            }

            judged.add(touched);
        }

        for (var line : judged) {
            line.applyTieRule(report);
        }
    }
}
