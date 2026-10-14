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
import songscribe.message.mutation.LineDeletion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.LineScopedMutation;
import songscribe.message.mutation.Mutation;

/**
 * The tie rule: a tie stays only while the element its walk reaches is a note it can end on
 * ({@link Tie#between}) and the tie {@link Tie#holds() holds}. A tie that stays also removes any
 * beam that holds both of its notes, since the tie wins. It judges every tied note of the lines
 * an edit touched and of the line before each, since a tie from that line can end in the touched
 * one. A tie loaded from a file is trusted, because a load records nothing for the rule to judge.
 */
final class TieRule implements ModificationRule {

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
        if (mutation instanceof LineScopedMutation scoped) {
            return scoped.getLine();
        }

        if (mutation instanceof LineInsertion insertion) {
            return insertion.line();
        }

        if (mutation instanceof LineDeletion deletion && deletion.lineIndex() < song.lineCount()) {
            return song.getLine(deletion.lineIndex());
        }

        return null;
    }

    private static void judge(Line line, ChangeReport.Builder report) {
        var elements = line.getElements();

        // Removing a tie or a beam changes no element, so the walk over the live list is safe.
        for (var i = 0; i < elements.size(); i++) {
            if (!(elements.get(i) instanceof NoteElement anchor) || !anchor.isTiedToNext()) {
                continue;
            }

            var reached = Tie.endAfter(line, i);
            var tie = reached == null ? null : Tie.between(anchor, reached.element());

            if (tie != null && tie.holds()) {
                // The tie wins over a beam: tied notes are never beamed together.
                var beam = Tie.beamHoldingBoth(line, i);

                if (beam != null) {
                    beam.removeReported(Strings.REPORT_REASON_BEAM_TIED_NOTES);
                }

                continue;
            }

            var reasonKey = tie == null
                ? Strings.REPORT_REASON_TIE_NO_FOLLOWING_NOTE
                : Strings.REPORT_REASON_TIE_NOT_HELD;

            report.record(new RecordedChange(
                Strings.REPORT_TIES_REMOVED, Strings.get(reasonKey), changeLines(line, reached)));
            line.removeTie(anchor);
        }
    }

    private static ChangeLines changeLines(Line line, Tie.@Nullable Reached reached) {
        if (reached == null) {
            return ChangeLines.on(line);
        }

        return new ChangeLines(line, reached.line());
    }
}
