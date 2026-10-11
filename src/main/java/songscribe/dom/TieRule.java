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
 * The tie rule: a tie stays only while, first, the element its walk reaches is a note it can end
 * on ({@link Tie#between}), and second, the tie {@link Tie#holds() holds}. A tie that stays also
 * removes any beam that holds both of its notes ({@link Tie#beamHoldingBoth}), since tied notes
 * are never beamed together: an insertion
 * or a paste can put a tied note inside a beam, and the tie wins. It judges every tied note of the
 * lines an edit touched and of the line before each of them, since a tie from that line can end
 * in the touched one. {@code docs/span-invalidation.md} gives the arrangement it sits in.
 * <p>
 * A touched line is a line a {@link LineScopedMutation} in the batch names, the line of a
 * {@link LineInsertion}, and, for a {@link LineDeletion}, the line that now sits at its
 * {@link LineDeletion#lineIndex()}, when there is one ({@link #touchedLine}); a named line
 * that is no longer one of the song's lines is not judged. Each line is judged once.
 */
final class TieRule implements ModificationRule {

    /**
     * {@inheritDoc}
     *
     * @effects applies the rule the class Javadoc states to every tie of the judged lines. For
     *     each tie the rule removes, records into {@code report} a change headed
     *     {@link Strings#REPORT_TIES_REMOVED}, naming the anchor's line through the line of
     *     the element the walk reaches, or the anchor's line alone when it reaches none, then
     *     posts a
     *     {@link songscribe.message.mutation.TieRemoval} through {@link Line#removeTie}; the
     *     change's reason is {@link Strings#REPORT_REASON_TIE_NO_FOLLOWING_NOTE} when the
     *     tie fails the rule's first condition and {@link Strings#REPORT_REASON_TIE_NOT_HELD}
     *     when it fails the second. Each beam the rule removes goes through
     *     {@link Span#removeReported} with {@link Strings#REPORT_REASON_BEAM_TIED_NOTES}
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

    /**
     * The line {@code mutation} touches: the line a {@link LineScopedMutation} names, the line of
     * a {@link LineInsertion}, or the line that now sits at a {@link LineDeletion}'s index.
     *
     * @param song     the song the batch was recorded on
     * @param mutation a mutation of that batch
     * @return the line, which need not still be one of {@code song}'s lines, or {@code null} for
     *     a song-wide mutation, or for a {@link LineDeletion} whose index no line now sits at
     */
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

    /**
     * Judges every tied note of {@code line}, one of its song's lines, and removes and reports
     * each tie that does not hold, as {@link #apply} states.
     */
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

    /**
     * The lines a tie anchored on {@code line} lies on: the anchor's line, through the line of the
     * element its walk reaches when that element is on the next line.
     *
     * @param line    the anchor's line
     * @param reached where the tie's walk stopped, or {@code null} when it reaches no element
     * @return {@code line} through {@code reached}'s line, or {@code line} alone when
     *     {@code reached} is {@code null} or on {@code line}
     */
    private static ChangeLines changeLines(Line line, Tie.@Nullable Reached reached) {
        if (reached == null) {
            return ChangeLines.on(line);
        }

        return new ChangeLines(line, reached.line());
    }
}
