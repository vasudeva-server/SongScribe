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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.io.musicxml.WedgeTypeMapping.WedgeKind;

/**
 * Measure-level hairpin wedge state machine. Binds each {@code <wedge>} to the
 * next {@code <note>}, opens/closes one hairpin at a time, and builds the
 * resulting {@link Crescendo}/{@link Diminuendo} on the current {@link Line}.
 */
final class WedgeResolver {

    private static final Logger LOG = LoggerFactory.getLogger(WedgeResolver.class);

    // -------------------------------------------------------------------------
    // Hairpin wedges (binding rule — inverts the writer's both-before-the-note
    // placement): the writer emits BOTH wedges of a hairpin immediately before
    // their bound <note>, so the reader uses one uniform rule — each wedge binds
    // to the NEXT <note> after it. The start wedge's next note is the hairpin
    // anchor; the stop wedge's next note is the hairpin end.
    //
    //   <wedge crescendo/diminuendo> ─► pendingStartWedge*  (awaits anchor note)
    //   anchor note finished         ─► pendingWedge* (open hairpin, awaits end)
    //   <wedge stop>                 ─► pendingStopWedge     (awaits end note)
    //   end note finished            ─► addCrescendo/addDiminuendo(anchor, end)
    //
    // A single pendingWedge assumes only one hairpin is ever open (the app must
    // not produce overlapping wedges — a pre-existing bug fixed later): a second
    // start while one is already open (and not in the process of closing) is
    // logged and dropped. A dangling start (no stop) is dropped at flush; an
    // orphan stop (no open hairpin) is logged and ignored.
    // -------------------------------------------------------------------------

    // A start <wedge> whose anchor note has not yet been seen.
    @Nullable
    private WedgeKind pendingStartWedgeKind = null;

    private int pendingStartWedgeX1Ss = 0;
    private int pendingStartWedgeYSs = 0;

    // The currently open hairpin: anchor note resolved, awaiting its end note.
    @Nullable
    private StaffElement pendingWedgeAnchor = null;

    @Nullable
    private WedgeKind pendingWedgeKind = null;

    private int pendingWedgeX1Ss = 0;
    private int pendingWedgeYSs = 0;

    // A stop <wedge> whose end note has not yet been seen.
    private boolean pendingStopWedge = false;
    private int pendingStopWedgeX2Ss = 0;

    // -------------------------------------------------------------------------
    // Package API
    // -------------------------------------------------------------------------

    /**
     * Handles a measure-level {@code <wedge>}. A start wedge
     * ({@code crescendo}/{@code diminuendo}) is held until the next note binds it
     * as the hairpin anchor; a stop wedge is held until the next note binds it as
     * the hairpin end (see the wedge-binding diagram on the pending-wedge fields).
     */
    void handleWedge(Attributes attributes) throws SAXException {
        var type = attributes.getValue(MusicXmlTags.ATTR_TYPE);

        if (MusicXmlTags.TYPE_STOP.equals(type)) {
            pendingStopWedge = true;
            pendingStopWedgeX2Ss = MusicXmlReader.optionalTenthsAttrToSs(attributes, MusicXmlTags.ATTR_RELATIVE_X);
            return;
        }

        var wedgeKind = WedgeTypeMapping.wedgeKind(type);

        if (wedgeKind == null) {
            // Not a crescendo/diminuendo start (nor a stop) — unrecognised; ignore.
            return;
        }

        // Defensive overlap drop: only one hairpin is ever open. A start while a
        // hairpin is already open (and not in the process of closing), or while an
        // earlier start has not yet bound to its anchor note, is logged and dropped.
        if (pendingStartWedgeKind != null
                || (pendingWedgeAnchor != null && !pendingStopWedge)) {
            LOG.warn("Dropping overlapping <wedge> start; a hairpin is already open");
            return;
        }

        pendingStartWedgeKind = wedgeKind;
        pendingStartWedgeX1Ss = MusicXmlReader.optionalTenthsAttrToSs(attributes, MusicXmlTags.ATTR_RELATIVE_X);
        pendingStartWedgeYSs = MusicXmlReader.optionalTenthsAttrToSs(attributes, MusicXmlTags.ATTR_RELATIVE_Y);
    }

    /**
     * Binds this note to any pending wedge. Each wedge binds to the next note: a
     * start wedge makes this note the hairpin anchor; a stop wedge makes it the
     * end.
     *
     * <p>When this note opens a hairpin (a start is pending and none is open), the
     * start is applied first so a same-note stop closes a single-note hairpin.
     * Otherwise the stop is applied first so a hairpin already open is closed
     * before this note opens the next (back-to-back hairpins sharing a note).
     */
    void resolveWedge(Line line, StaffElement element) {
        var openingHere = pendingWedgeAnchor == null && pendingStartWedgeKind != null;

        if (openingHere) {
            applyPendingStartWedge(element);
            applyPendingStopWedge(line, element);
        } else {
            applyPendingStopWedge(line, element);
            applyPendingStartWedge(element);
        }
    }

    /**
     * Promotes a pending start wedge into the open hairpin, with {@code note} as
     * its anchor. No-op when no start wedge is pending.
     */
    private void applyPendingStartWedge(StaffElement note) {
        if (pendingStartWedgeKind == null) {
            return;
        }

        pendingWedgeAnchor = note;
        pendingWedgeKind = pendingStartWedgeKind;
        pendingWedgeX1Ss = pendingStartWedgeX1Ss;
        pendingWedgeYSs = pendingStartWedgeYSs;
        pendingStartWedgeKind = null;
        pendingStartWedgeX1Ss = 0;
        pendingStartWedgeYSs = 0;
    }

    /**
     * Closes the open hairpin with {@code note} as its end, building the
     * {@link Crescendo}/{@link Diminuendo}. An orphan stop (no open hairpin) is
     * logged and ignored. No-op when no stop wedge is pending.
     */
    private void applyPendingStopWedge(Line line, StaffElement note) {
        if (!pendingStopWedge) {
            return;
        }

        if (pendingWedgeAnchor != null && pendingWedgeKind != null) {
            buildHairpin(line, pendingWedgeAnchor, pendingWedgeKind, note);
            clearPendingWedge();
        } else {
            LOG.warn("Ignoring <wedge type=\"stop\"> with no open hairpin");
        }

        pendingStopWedge = false;
        pendingStopWedgeX2Ss = 0;
    }

    /**
     * Builds a {@link Crescendo} or {@link Diminuendo} over [{@code anchor},
     * {@code end}] and adds it to {@code line}. {@code x1ShiftSs}/{@code yShiftSs}
     * come from the start wedge, {@code x2ShiftSs} from the stop wedge — each set
     * only when non-zero.
     */
    private void buildHairpin(
        Line line,
        StaffElement anchor,
        WedgeKind kind,
        StaffElement end
    ) {
        if (kind == WedgeKind.CRESCENDO) {
            var crescendo = new Crescendo(anchor, end);
            applyHairpinShifts(crescendo);
            line.addCrescendo(crescendo);
        } else {
            var diminuendo = new Diminuendo(anchor, end);
            applyHairpinShifts(diminuendo);
            line.addDiminuendo(diminuendo);
        }
    }

    /**
     * Restores the user-adjustable hairpin offsets from the pending wedge state.
     * Reads the start-wedge shifts ({@link #pendingWedgeX1Ss}/{@link #pendingWedgeYSs})
     * and the stop-wedge shift ({@link #pendingStopWedgeX2Ss}), which are still set
     * when this runs (cleared after the build).
     */
    private void applyHairpinShifts(Hairpin hairpin) {
        if (pendingWedgeX1Ss != 0) {
            hairpin.setX1ShiftSs(pendingWedgeX1Ss);
        }

        if (pendingStopWedgeX2Ss != 0) {
            hairpin.setX2ShiftSs(pendingStopWedgeX2Ss);
        }

        if (pendingWedgeYSs != 0) {
            hairpin.setYShiftSs(pendingWedgeYSs);
        }
    }

    private void clearPendingWedge() {
        pendingWedgeAnchor = null;
        pendingWedgeKind = null;
        pendingWedgeX1Ss = 0;
        pendingWedgeYSs = 0;
    }

    /**
     * Drops any wedge state still open at a line or part boundary: a start wedge
     * with no bound note, a dangling hairpin with no stop, or a stop wedge with no
     * bound note. A hairpin needs both endpoints, so an incomplete one builds nothing.
     */
    void flushPendingWedge() {
        if (pendingStartWedgeKind != null) {
            LOG.warn("Dropping <wedge> start with no bound note");
            pendingStartWedgeKind = null;
            pendingStartWedgeX1Ss = 0;
            pendingStartWedgeYSs = 0;
        }

        if (pendingWedgeAnchor != null) {
            LOG.warn("Dropping dangling hairpin with no stop wedge");
            clearPendingWedge();
        }

        if (pendingStopWedge) {
            LOG.warn("Dropping <wedge type=\"stop\"> with no bound note");
            pendingStopWedge = false;
            pendingStopWedgeX2Ss = 0;
        }
    }
}
