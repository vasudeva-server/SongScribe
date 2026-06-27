/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.ui.renderer;

import module java.desktop;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.error.RuntimeError;
import songscribe.layout.NoteGeometry;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Computes the horizontal column extent of a note in staff spaces (note-local origin),
 * plus the flag's bounding box for conditional glissando endpoint adjustment.
 *
 * <p>Column extent diagram (note-local space, X=0 at notehead glyph origin):
 * <pre>
 *   Stem up:
 *
 *     accidental  notehead    stem-right
 *     |           |<-- notehead -->|stem|
 *     |←  left   →|               |←right
 *
 *   Stem down:
 *
 *     accidental  stem  notehead
 *     |           |stem |<-- notehead -->|
 *     |←  left   →|    ←left            →right
 * </pre>
 * Left extent is driven by: accidental (if present), stem left edge (stem-down),
 * notehead left edge, or ledger-line overhang.
 * Right extent is driven by: stem right edge (stem-up), notehead right edge,
 * augmentation dots, or ledger-line overhang.
 *
 * <p>The flag is deliberately excluded from the static extent because both glissando
 * endpoints are pinned at notehead-centre Y, so the flag (which lives near the stem
 * tip, far above/below) usually has no ink in the line's path. The result is
 * intentionally uncached — the computation is a handful of O(1) bbox lookups, far
 * cheaper than maintaining a cache (the cache it replaces was needed only because
 * the old Area build was expensive; refs #465).
 */
final class NoteColumnGeometry {

    /**
     * Horizontal column extent plus optional flag bbox for a single note.
     *
     * @param leftSs        left edge in staff spaces (note-local, ≤ 0)
     * @param rightSs       right edge in staff spaces (note-local, ≥ 0)
     * @param flagBBoxLocal flag glyph bbox in note-local space, or {@code null} when the
     *                      note is beamed, has no flag, or has no stem
     */
    record ColumnExtent(double leftSs, double rightSs, @Nullable Rectangle2D flagBBoxLocal) {}

    private NoteColumnGeometry() {}

    /**
     * Computes the column extent for {@code note} in note-local staff-space coordinates.
     *
     * @param note   the note whose column extent to compute
     * @param beamed whether the note belongs to a beam group (suppresses flag computation)
     * @return the column extent with an optional flag bbox
     */
    static ColumnExtent extentSs(StaffElement note, boolean beamed) {
        var noteType = note.getType();

        // Grace notes always stem up, mirroring buildNoteArea.
        var upper = note.isUpper();

        if (noteType.isGraceNote()) {
            upper = true;
        }

        // ---- notehead ----
        var glyph = noteType.getSMuFLGlyph();

        // No null-glyph fallback: a null glyph means the note is not a renderable note
        // type (e.g. a barline). Callers must not pass such types — this is a programming
        // error, so exit fatally. requireBBox is similarly fatal on a missing glyph by design.
        if (glyph == null) {
            throw RuntimeError.exit("extentSs called on non-renderable note type: " + noteType);
        }

        var noteheadBBox = SMuFLMetadata.requireBBox(glyph);
        var offsetX = NoteGeometry.getNoteheadXOffsetSs(noteType, upper);
        var leftSs = offsetX + noteheadBBox.left();
        var rightSs = NoteGeometry.getNoteheadRightEdgeSs(note);

        // ---- augmentation dots (extend right) ----
        if (note.getDotCount() > 0) {
            var dotBBox = SMuFLMetadata.requireBBox(SMuFLGlyph.AUGMENTATION_DOT);
            var maxDotRight = new double[]{rightSs};

            NoteRenderer.forEachDotPosition(note, beamed, upper, (dotX, yOffset) -> {
                var dotRight = dotX + dotBBox.right();

                if (dotRight > maxDotRight[0]) {
                    maxDotRight[0] = dotRight;
                }
            });

            rightSs = maxDotRight[0];
        }

        // ---- stem and flag ----
        @Nullable Rectangle2D flagBBoxLocal = null;

        if (noteType.isNoteWithStem()) {
            var stemGeom = NoteRenderer.computeBaseStemGeometry(noteType, upper);
            var stemLeftXSs = stemGeom.stemLeftXSs();

            if (upper) {
                // Stem-up: stem is to the right of the notehead.
                var stemRightXSs = stemLeftXSs + NoteGeometry.STEM_WIDTH_SS;

                if (stemRightXSs > rightSs) {
                    rightSs = stemRightXSs;
                }
            } else {
                // Stem-down: stem is to the left of the notehead.
                if (stemLeftXSs < leftSs) {
                    leftSs = stemLeftXSs;
                }
            }

            // ---- flag bbox (not beamed, flag glyph present) ----
            if (!beamed) {
                var flagGlyph = noteType.getFlagGlyph(upper);

                if (flagGlyph != null) {
                    var stemTipYSs = stemGeom.stemTipYSs(upper);
                    var flagBBox = SMuFLMetadata.requireBBox(flagGlyph);

                    double flagOriginX;

                    if (noteType.isGraceNote()) {
                        flagOriginX = NoteGeometry.getGraceFlagOriginXSs(stemLeftXSs);
                        var scale = (double) ElementType.GRACE_NOTE_SCALE;
                        flagBBoxLocal = new Rectangle2D.Double(
                            flagOriginX + flagBBox.left() * scale,
                            stemTipYSs + flagBBox.top() * scale,
                            flagBBox.width() * scale,
                            flagBBox.height() * scale
                        );
                    } else {
                        flagOriginX = stemLeftXSs;
                        flagBBoxLocal = new Rectangle2D.Double(
                            flagOriginX + flagBBox.left(),
                            stemTipYSs + flagBBox.top(),
                            flagBBox.width(),
                            flagBBox.height()
                        );
                    }
                }
            }
        }

        // ---- accidental (extend left only) ----
        if (note.getAccidental() != null) {
            var accidentalStartXSs = NoteGeometry.getAccidentalStartXSs(note);

            if (accidentalStartXSs < leftSs) {
                leftSs = accidentalStartXSs;
            }
        }

        // ---- ledger lines (extend both sides) ----
        // Use the base extent (no accidental shortening) — the accidental's own left
        // extent is already folded in above.
        if (NoteGeometry.noteNeedsLedgerLines(note)) {
            var extent = NoteGeometry.getLedgerLineBaseExtentSs(note);
            leftSs = Math.min(leftSs, extent.leftSs());
            rightSs = Math.max(rightSs, extent.rightSs());
        }

        return new ColumnExtent(leftSs, rightSs, flagBBoxLocal);
    }
}
