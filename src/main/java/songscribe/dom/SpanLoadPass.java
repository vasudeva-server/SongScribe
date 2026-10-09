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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.dom.StaffElementRun.ElementRange;

/**
 * Turns the endings and hairpins a reader found into the song's spans, or drops them.
 *
 * <p>This pass runs once the song is complete and is the one place a {@link PendingSpan} becomes a
 * span: it asks the kind's factory, the same rule the editor and the paste ask, so a span read
 * from a file is well formed by exactly the rule a span made in the editor is. A file an older
 * build saved can hold a span that rule refuses — a hairpin that ends on a rest, say. That span is
 * dropped — the bracket or the wedge goes, the elements stay — and nothing is repaired into
 * something the file did not say.
 */
final class SpanLoadPass {

    private static final Logger LOG = LoggerFactory.getLogger(SpanLoadPass.class);

    private SpanLoadPass() {
    }

    /**
     * Adds to {@code song} every pending span that is well formed, and records every one it
     * dropped.
     *
     * <p>Each pending span is judged in order by its kind's factory ({@link PendingSpan#spanning})
     * over the range from its anchor's index to its end's index in the anchor's line. A span the
     * factory returns takes the pending span's two offsets as its
     * {@link LineElement#getUserXOffsetSs()} and {@link LineElement#getUserYOffsetSs()}, and is
     * added to that line with {@link Span#addTo}, which merges a hairpin with each hairpin of its
     * kind already there that it overlaps or touches. One the factory refuses is dropped. Adding a
     * span changes no element, so every later pending span's indices are the ones the reader
     * built. Because the spans are judged in order and the factory sees the spans already added, of
     * two pending endings that overlap or sit back to back, and of two pending hairpins of opposite
     * kinds that share more than one element, the pass drops the later.
     *
     * <p>No reader records a span whose anchor and end are on different lines: the MusicXML reader
     * drops a span still open when its line ends, and the legacy format stores each span as a pair
     * of indices into one line, which the legacy reader rejects unless the first is at or before
     * the second. So an anchor and an end on different lines is a reader's bug, not file input, and
     * the pass rejects it rather than reporting it.
     *
     * @param song         the song being loaded, whose lines hold every anchor and end
     * @param pendingSpans the endings or hairpins the reader found in {@code song}. Each one's
     *                     anchor sits in a line of {@code song}, and its end sits in the same line
     *                     at or after the anchor
     * @param report       the report a drop is recorded into
     * @throws IllegalArgumentException if a pending span's anchor sits in no line of
     *     {@code song}, or its end does not sit in the anchor's line at or after the anchor
     * @effects adds a span to its anchor's line for every pending span not dropped; records to
     *     {@code report}, in the order of {@code pendingSpans}, every pending span the pass
     *     dropped, as {@link Spanning.Refused#reportedAt} states it over the span's range
     * @log WARN one line for each pending span dropped, naming its kind's heading, its line, its
     *     range and the reason
     */
    static void run(Song song, List<? extends PendingSpan<?>> pendingSpans, ChangeReport.Builder report) {
        for (var pending : pendingSpans) {
            place(song, pending, report);
        }
    }

    private static <S extends Span<?>> void place(Song song, PendingSpan<S> pending, ChangeReport.Builder report) {
        var anchor = pending.anchor();
        var line = anchor.getParentLine();
        var lineIndex = line == null ? null : song.indexOfLine(line);

        if (line == null || lineIndex == null) {
            throw new IllegalArgumentException("pending span's anchor is in no line of the song");
        }

        var range = new ElementRange(line.getElementIndex(anchor), line.getElementIndex(pending.end()));

        switch (pending.spanning(line, range)) {
            case Spanning.Spanned(var span, _) -> {
                span.setUserXOffsetSs(pending.xOffsetSs());
                span.setUserYOffsetSs(pending.yOffsetSs());
                span.addTo();
            }

            case Spanning.Refused<S> refused -> {
                LOG.warn("Load pass: dropped an ill-formed span ({}) over elements {}-{} of line {} ({})",
                    refused.headingKey(), range.begin(), range.end(), lineIndex, refused.reasonKey());

                report.record(refused.reportedAt(ElementLocationRange.onLine(lineIndex, range)));
            }
        }
    }
}
