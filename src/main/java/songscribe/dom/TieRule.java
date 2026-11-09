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

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.message.mutation.FontChange;
import songscribe.message.mutation.LayoutChange;
import songscribe.message.mutation.LineDeletion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.LineScopedMutation;
import songscribe.message.mutation.LyricsChange;
import songscribe.message.mutation.MetadataChange;
import songscribe.message.mutation.Mutation;

/**
 * The modification rule that judges the ties an edit leaves.
 */
final class TieRule implements ModificationRule {

    /**
     * A line that {@code batch} touched is the line of each line-scoped mutation and each line
     * insertion, and, for a line deletion, the line that takes the deleted line's place.
     *
     * @effects afterwards, every tie with a note in a line that {@code batch} touched is one that
     *     {@link Tie#judge} finds to hold; each other such tie is removed
     * @effects records to {@code report} the {@link Tie.Judgment.Broken#entry() entry} of each
     *     tie removed, with its reason and its lines
     * @effects afterwards, no beam holds both notes of a tie that stays; each beam removed for
     *     that is reported with {@link Strings#REPORT_REASON_BEAM_TIED_NOTES}
     */
    @Override
    public void apply(Song song, List<Mutation> batch, ChangeReport.Builder report) {
        var judged = new LinkedHashSet<Line>();

        for (var mutation : batch) {
            var touched = touchedLine(song, mutation);
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
            judge(line, report);
        }
    }

    private static @Nullable Line touchedLine(Song song, Mutation mutation) {
        return switch (mutation) {
            case LineScopedMutation scoped -> scoped.getLine();
            case LineInsertion insertion -> insertion.line();
            case LineDeletion deletion -> deletion.lineIndex() < song.lineCount() ? song.getLine(deletion.lineIndex()) : null;
            case MetadataChange _, FontChange _, LayoutChange _, LyricsChange _ -> null;
        };
    }

    private static void judge(Line line, ChangeReport.Builder report) {
        // Removing a tie or a beam changes no element, so the walk over the live list is safe.
        for (var element : line.getElements()) {
            if (!(element instanceof NoteElement anchor) || !anchor.isTiedToNext()) {
                continue;
            }

            switch (Tie.judge(anchor)) {
                case Tie.Judgment.Holds(var tie) -> {
                    var beam = tie.beamHoldingBoth();

                    if (beam != null) {
                        beam.removeReported(Strings.REPORT_REASON_BEAM_TIED_NOTES);
                    }
                }
                case Tie.Judgment.Broken broken -> {
                    report.record(broken.entry());
                    line.removeTie(anchor);
                }
            }
        }
    }
}
