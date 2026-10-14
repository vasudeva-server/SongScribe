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

package songscribe.layout;

import songscribe.dom.NoteElement;
import songscribe.engraving.StemMetrics;
import songscribe.smufl.SMuFLMetadata;

/**
 * Horizontal extents of a note in staff spaces, for slide rendering: the full column extent
 * including the stem, and the stem-free extent where a glissando attaches. Extents are note-local,
 * X=0 at the notehead glyph origin. Ledger lines and the flag are excluded because a glissando,
 * pinned at notehead-center Y, never meets them.
 *
 * <p>See {@code docs/layout-geometry.md}.
 */
public final class NoteColumnGeometry {

    public record ColumnExtent(double leftSs, double rightSs) {}

    private NoteColumnGeometry() {}

    public static ColumnExtent glissandoAttachExtentSs(NoteElement note, Beaming beaming) {
        var noteType = note.getType();
        var direction = NoteGeometry.effectiveDirection(note);

        // ---- notehead ----
        var glyph = note.notehead().glyph();
        var noteheadBBox = SMuFLMetadata.bboxSs(glyph);
        var offsetX = NoteGeometry.getNoteheadXOffsetSs(noteType, direction);
        var leftSs = offsetX + noteheadBBox.leftSs();

        // ---- augmentation dots (extend right) ----
        var rightSs = NoteGeometry.dotsRightExtentSs(
            note, beaming, direction, noteType.getElementWidthSs());

        // ---- accidental (extend left only) ----
        if (note.drawnAccidental() != null) {
            var accidentalStartXSs = NoteGeometry.getAccidentalStartXSs(note);

            if (accidentalStartXSs < leftSs) {
                leftSs = accidentalStartXSs;
            }
        }

        return new ColumnExtent(leftSs, rightSs);
    }

    /**
     * @param stemFree the stem-free extent for the same note, from {@link #glissandoAttachExtentSs}
     */
    public static ColumnExtent extentSs(NoteElement note, ColumnExtent stemFree) {
        var leftSs = stemFree.leftSs();
        var rightSs = stemFree.rightSs();

        // The guards below widen the extent when a stem protrudes past the notehead's bbox, which
        // no Bravura notehead does: its stem anchors sit exactly on the outer bbox edges, and the
        // grace stem sits well inside.
        if (note.getType().isNoteWithStem()) {
            var direction = NoteGeometry.effectiveDirection(note);
            var stemGeom = NoteGeometry.computeBaseStemGeometry(note, direction);
            var stemLeftXSs = stemGeom.stemLeftXSs();

            if (direction.isUp()) {
                // Stem-up: stem is to the right of the notehead.
                var stemRightXSs = stemLeftXSs + StemMetrics.thicknessSs();

                if (stemRightXSs > rightSs) {
                    rightSs = stemRightXSs;
                }
            } else {
                // Stem-down: stem is to the left of the notehead.
                if (stemLeftXSs < leftSs) {
                    leftSs = stemLeftXSs;
                }
            }
        }

        return new ColumnExtent(leftSs, rightSs);
    }

}
