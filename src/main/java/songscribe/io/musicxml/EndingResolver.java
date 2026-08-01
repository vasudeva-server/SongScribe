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
package songscribe.io.musicxml;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.dom.StaffElement;
import songscribe.dom.Ending;

/**
 * Resolves the {@code <ending>} (volta) markers collected on barlines into
 * {@link Ending} range spans on the current line.
 *
 * <p>The caller ({@link MusicXmlReader}) owns the current {@code Line} the
 * markers attach to; this class reads it through the reader reference rather
 * than duplicating that ownership.
 */
final class EndingResolver {

    private static final Logger LOG = LoggerFactory.getLogger(EndingResolver.class);

    private final MusicXmlReader reader;

    EndingResolver(MusicXmlReader reader) {
        this.reader = reader;
    }

    // -------------------------------------------------------------------------
    // Endings (two voltas ─► one Ending — inverts the writer's split expansion):
    //
    //   anchor barline          split barline(s)            end barline
    //   <ending 1 start>        <ending 1 stop>             <ending 2 stop>
    //                           <ending 2 start>
    //        |                        |     |                    |
    //        '------- volta 1 -------'      '----- volta 2 ------'
    //
    //   1 start ─► pendingEndingAnchor (the barline StaffElement)
    //   2 stop  ─► Ending(anchor, this barline); the split is recomputed live
    //             via Ending.findRepeatSplitIndex — never stored.
    //   1 stop  ─► ignored (the volta-1 split close; the split is recomputed live)
    //   2 start ─► ignored (the volta-2 open; the split is recomputed live)
    //
    // Every ending must have a split. A split-less span — only a 1 start ─► 1 stop
    // from a foreign file, or a 1 start ─► 2 stop with no REPEAT between anchor and
    // end — is not a valid ending and is rejected on import: an anchor still open at
    // the next anchor or the line/part flush is dropped, and buildEnding discards any
    // span whose live split cannot be found.
    // -------------------------------------------------------------------------

    // The open ending's anchor barline (set on <ending number="1" type="start">).
    @Nullable
    private StaffElement pendingEndingAnchor = null;

    // True once a note-anchored <ending number="1" type="start"> is seen (issue
    // #306): it rides on an invisible left barline preceding the anchor note, so
    // the anchor binds to the next element appended to the line, not to a barline.
    private boolean pendingAnchorFromNextElement = false;

    /**
     * Resolves the {@code <ending>} markers collected on one {@code <barline>}
     * against that barline's just-created {@link StaffElement}, advancing the
     * ending state machine.
     *
     * <p>{@code element} is null for invisible barlines. Besides the volta-2
     * {@code number="2" type="start"} split marker (a no-op), these now also host
     * note-anchored markers (issue #306): a {@code number="1" type="start"} start,
     * deferred to the next appended element, and a {@code type="discontinue"} end,
     * bound to the current line's last element.
     */
    void attachBarlineEndings(@Nullable StaffElement element, List<EndingMarker> endings) {
        for (var ending : endings) {
            var isOne = MusicXmlTags.NUMBER_1.equals(ending.number());
            var isTwo = MusicXmlTags.NUMBER_2.equals(ending.number());
            var isStart = MusicXmlTags.TYPE_START.equals(ending.type());
            var isStop = MusicXmlTags.TYPE_STOP.equals(ending.type())
                || MusicXmlTags.ENDING_DISCONTINUE.equals(ending.type());

            if (isOne && isStart) {
                // Anchor of a new ending. Drop any still-open ending first: a complete
                // two-bracket ending was already built and cleared on its number="2"
                // stop, so anything still pending here is not a valid ending.
                dropPendingEnding();

                pendingAnchorFromNextElement = false;

                if (element == null) {
                    // Note-anchored start (issue #306): the invisible left barline
                    // precedes the anchor note, so bind the anchor to the next
                    // element appended to the line.
                    pendingAnchorFromNextElement = true;
                    pendingEndingAnchor = null;
                    continue;
                }

                pendingEndingAnchor = element;
            } else if (isTwo && isStop) {
                // Definite end of a two-bracket ending. A note-terminated end
                // (issue #306) rides on an invisible right barline (element == null)
                // emitted after the boundary note, so bind to the line's last
                // element in that case.
                var end = element != null ? element : lastElementOfCurrentLine();

                if (pendingEndingAnchor != null && end != null) {
                    buildEnding(pendingEndingAnchor, end);
                    clearPendingEnding();
                } else {
                    LOG.warn("Ignoring <ending number=\"2\" type=\"stop\"> with no open ending");
                }
            }
            // A number="1" stop (volta-1 split close) and a number="2" start (volta-2
            // open) carry no state: the split is recomputed live from the element types
            // between anchor and end, so only the number="1" start and number="2" stop
            // bounding the whole ending are tracked.
        }
    }

    /**
     * Drops a still-open pending ending. A complete ending is built and its state is
     * cleared on its {@code number="2"} stop, so anything still pending here is either
     * incomplete (no {@code number="2"} stop) or a split-less single bracket — neither
     * is a valid ending, so it is discarded.
     */
    private void dropPendingEnding() {
        if (pendingEndingAnchor == null) {
            return;
        }

        LOG.warn("Dropping incomplete <ending> with no number=\"2\" stop");
        clearPendingEnding();
    }

    /**
     * Builds one {@link Ending} over [{@code anchor}, {@code end}] and adds it to the
     * current line, unless it is split-less. Both endpoints are {@link StaffElement}s
     * already appended to the line, so the ending's index pair recovers via
     * {@code getAnchorElementIndex()}/{@code getEndElementIndex()} and its split can be
     * found live. Every ending must have a REPEAT splitting its two brackets; a span with
     * no such element is not a valid ending and is dropped on import.
     */
    private void buildEnding(StaffElement anchor, StaffElement end) {
        var currentLine = reader.getCurrentLine();

        if (currentLine == null) {
            return;
        }

        var ending = new Ending(anchor, end);

        if (ending.findRepeatSplitElement(currentLine) == null) {
            LOG.warn("Dropping split-less <ending> on import (no repeat between anchor and end)");
            return;
        }

        currentLine.addRangeElement(ending);
    }

    private void clearPendingEnding() {
        pendingEndingAnchor = null;
        pendingAnchorFromNextElement = false;
    }

    /**
     * Binds a note-anchored ending start (issue #306) to the just-appended
     * {@code element}. The start marker was hosted on an invisible left barline
     * preceding the anchor note, deferring the anchor to this next element.
     */
    void resolvePendingNextAnchor(StaffElement element) {
        if (pendingAnchorFromNextElement) {
            pendingEndingAnchor = element;
            pendingAnchorFromNextElement = false;
        }
    }

    /**
     * Returns the last element appended to the current line, or null if there is
     * no current line or it is empty. Used to bind a note-terminated ending end.
     */
    @Nullable
    private StaffElement lastElementOfCurrentLine() {
        var currentLine = reader.getCurrentLine();

        if (currentLine == null || currentLine.elementCount() == 0) {
            return null;
        }

        return currentLine.getElement(currentLine.elementCount() - 1);
    }

    /**
     * Drops a pending ending still open at a line or part boundary (no valid ending can
     * remain open across the boundary).
     */
    void flushPendingEnding() {
        dropPendingEnding();
    }

    /**
     * One {@code <ending number type>} child parsed from a {@code <barline>},
     * collected during barline parsing and resolved to the barline's
     * {@link StaffElement} once it is appended. {@code number} and {@code type}
     * may be null for malformed input; the consumers compare with null-safe
     * {@code equals}.
     */
    record EndingMarker(@Nullable String number, @Nullable String type) {}
}
