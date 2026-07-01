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
import songscribe.layout.Ending;

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
    //   1 stop  ─► tentative split-less end (overwritten by a later 2 stop)
    //   2 start ─► marks a second volta (distinguishes a partial two-bracket
    //             ending — dropped — from a complete split-less ending — built).
    //
    // A split-less ending (only 1 start ─► 1 stop) builds Ending(anchor, end)
    // directly. The build is deferred to the next anchor or to the line/part
    // flush so a trailing 2 start can still be observed.
    // -------------------------------------------------------------------------

    // The open ending's anchor barline (set on <ending number="1" type="start">).
    @Nullable
    private StaffElement pendingEndingAnchor = null;

    // The tentative end barline of a split-less ending (set on a number="1" stop),
    // overwritten by a number="2" stop in a two-bracket ending.
    @Nullable
    private StaffElement pendingEndingEnd = null;

    // True once a <ending number="2" type="start"> is seen: marks a two-bracket
    // ending, so a missing number="2" stop drops the ending instead of building a
    // (wrong) split-less one from the tentative end.
    private boolean pendingEndingSawSecondVolta = false;

    /**
     * Resolves the {@code <ending>} markers collected on one {@code <barline>}
     * against that barline's just-created {@link StaffElement}, advancing the
     * ending state machine.
     *
     * <p>{@code element} is null only for invisible barlines, which host at most a
     * volta-2 {@code number="2" type="start"} split marker — a no-op needing no
     * element.
     */
    void attachBarlineEndings(@Nullable StaffElement element, List<EndingMarker> endings) {
        for (var ending : endings) {
            var isOne = MusicXmlTags.NUMBER_1.equals(ending.number());
            var isTwo = MusicXmlTags.NUMBER_2.equals(ending.number());
            var isStart = MusicXmlTags.TYPE_START.equals(ending.type());
            var isStop = MusicXmlTags.TYPE_STOP.equals(ending.type())
                || MusicXmlTags.ENDING_DISCONTINUE.equals(ending.type());

            if (isOne && isStart) {
                // Anchor of a new ending. Finalize any still-open ending first
                // (build a complete split-less one, or drop a dangling/partial one).
                finalizeOrDropPendingEnding();

                if (element == null) {
                    LOG.warn("Ignoring <ending number=\"1\" type=\"start\"> on a barline with no element");
                    continue;
                }

                pendingEndingAnchor = element;
                pendingEndingEnd = null;
                pendingEndingSawSecondVolta = false;
            } else if (isTwo && isStop) {
                // Definite end of a two-bracket ending.
                if (pendingEndingAnchor != null && element != null) {
                    buildEnding(pendingEndingAnchor, element);
                    clearPendingEnding();
                } else {
                    LOG.warn("Ignoring <ending number=\"2\" type=\"stop\"> with no open ending");
                }
            } else if (isOne && isStop) {
                // Either a volta-1 split stop or a split-less end — tentative until a
                // number="2" start (split) or number="2" stop (two-bracket end) decides.
                if (pendingEndingAnchor != null && element != null) {
                    pendingEndingEnd = element;
                } else {
                    LOG.warn("Ignoring <ending number=\"1\" type=\"stop\"> with no open ending");
                }
            } else if (isTwo && isStart) {
                // Volta-2 split start — no stored value (the split is recomputed
                // live), but it marks the ending as two-bracket.
                pendingEndingSawSecondVolta = true;
            }
        }
    }

    /**
     * Completes a pending split-less ending (anchor + tentative end, no second
     * volta) by building it, or drops a dangling/partial one (no end, or a second
     * volta seen but no {@code number="2"} stop). Clears the pending ending state.
     */
    private void finalizeOrDropPendingEnding() {
        if (pendingEndingAnchor == null) {
            return;
        }

        if (pendingEndingEnd != null && !pendingEndingSawSecondVolta) {
            buildEnding(pendingEndingAnchor, pendingEndingEnd);
        } else {
            LOG.warn("Dropping incomplete <ending> with no number=\"2\" stop");
        }

        clearPendingEnding();
    }

    /**
     * Builds one {@link Ending} over [{@code anchor}, {@code end}] and adds it to
     * the current line. Both endpoints are barline {@link StaffElement}s already
     * appended to the line, so the ending's index pair recovers via
     * {@code getAnchorElementIndex()}/{@code getEndElementIndex()}.
     */
    private void buildEnding(StaffElement anchor, StaffElement end) {
        var currentLine = reader.getCurrentLine();

        if (currentLine == null) {
            return;
        }

        currentLine.addRangeElement(new Ending(anchor, end));
    }

    private void clearPendingEnding() {
        pendingEndingAnchor = null;
        pendingEndingEnd = null;
        pendingEndingSawSecondVolta = false;
    }

    /**
     * Builds or drops a pending ending still open at a line or part boundary.
     */
    void flushPendingEnding() {
        finalizeOrDropPendingEnding();
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
