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

import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.dom.StaffElementRun.ElementRange;

/**
 * Places one span a reader found on its anchor's line, or drops it and reports the drop. This is
 * the shape every load pass whose span kind answers with a {@link Spanning} shares:
 * {@link EndingLoadPass} and {@link HairpinLoadPass} each describe a pending span as a
 * {@link Placement} and hand it here.
 */
final class SpanLoadPass {

    private static final Logger LOG = LoggerFactory.getLogger(SpanLoadPass.class);

    private SpanLoadPass() {
    }

    /**
     * One pending span, as {@link #place} places it.
     *
     * @param anchor            the span's first element, in a line of the song being loaded
     * @param end               the span's last element, in the anchor's line at or after the anchor
     * @param spanning          the kind's factory: the span over a range of a line, or the reason
     *                          there is none
     * @param restore           applies to a spanned span what the file said beyond its range —
     *                          its offsets — before the span is added
     * @param droppedHeadingKey the {@code Strings} key of the report heading a dropped span is
     *                          recorded under
     * @param noun              what the span is, as the log names it
     * @param <S>               the kind of span
     */
    record Placement<S extends Span<?>>(
        StaffElement anchor,
        StaffElement end,
        BiFunction<Line, ElementRange, Spanning<S>> spanning,
        Consumer<? super S> restore,
        String droppedHeadingKey,
        String noun
    ) {
    }

    /**
     * Adds {@code placement}'s span to its anchor's line when its factory spans the range from the
     * anchor's index to the end's index there, or drops it and records the drop.
     *
     * @param song      the song being loaded, whose lines hold the anchor and the end
     * @param placement the pending span
     * @param report    the report a drop is recorded into
     * @param <S>       the kind of span
     * @throws IllegalArgumentException if the anchor sits in no line of {@code song}, or the end
     *     does not sit in the anchor's line at or after the anchor
     * @effects adds the spanned span to the anchor's line, after {@code placement.restore()};
     *     or records to {@code report} a {@link ReportedChange} under
     *     {@code placement.droppedHeadingKey()}, with the refusal's reason as its detail and the
     *     range as its location
     * @log WARN one line when the span is dropped, naming its line, its range and the reason
     */
    static <S extends Span<?>> void place(Song song, Placement<S> placement, ChangeReport report) {
        var anchor = placement.anchor();
        var line = anchor.getParentLine();
        var lineIndex = line == null ? null : song.indexOfLine(line);

        if (line == null || lineIndex == null) {
            throw new IllegalArgumentException(
                "pending " + placement.noun() + "'s anchor is in no line of the song");
        }

        var range = new ElementRange(line.getElementIndex(anchor), line.getElementIndex(placement.end()));

        switch (placement.spanning().apply(line, range)) {
            case Spanning.Spanned(var span) -> {
                placement.restore().accept(span);
                span.addTo(line);
            }

            case Spanning.Refused(var reasonKey, _) -> {
                LOG.warn("Load pass: dropped an ill-formed {} over elements {}-{} of line {} ({})",
                    placement.noun(), range.begin(), range.end(), lineIndex, reasonKey);

                report.record(new ReportedChange(
                    placement.droppedHeadingKey(),
                    Strings.get(reasonKey),
                    ElementLocationRange.onLine(lineIndex, range)));
            }
        }
    }
}
