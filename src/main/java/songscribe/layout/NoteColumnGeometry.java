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

import songscribe.dom.StaffElement;
import songscribe.smufl.SMuFLMetadata;

/**
 * Computes horizontal extents of a note in staff spaces (note-local origin) for slide
 * rendering. Two extents are exposed:
 *
 * <ul>
 *   <li>{@link #extentSs} — the full column extent including the stem, used to anchor a
 *       trailing fall glyph off the note's right edge.
 *   <li>{@link #glissandoAttachExtentSs} — the stem-free extent (notehead, augmentation
 *       dots, accidental), used as the attachment points of a connecting glissando.
 * </ul>
 *
 * <p>Extents are in note-local space, X=0 at the notehead glyph origin. Full-extent left is driven
 * by the accidental if present, else the stem's left edge when the stem points down, else the
 * notehead's left edge. Full-extent right is driven by the stem's right edge when the stem points
 * up, else the notehead's right edge or the augmentation dots.
 * The stem-free glissando-attach extent drops the stem contribution entirely, so its
 * left/right are driven only by the accidental, notehead, and augmentation dots.
 * Ledger lines are excluded — they are reference lines, not ink the glissando must avoid.
 *
 * <p>See {@code docs/layout-geometry.md} for the annotated diagram.
 *
 * <p>The flag is deliberately excluded from both extents because both glissando endpoints
 * are pinned at notehead-center Y, so the flag (which lives near the stem tip, far
 * above/below) has no ink in the line's path. The results are intentionally uncached —
 * each computation is a handful of O(1) bbox lookups, far cheaper than maintaining a
 * cache (the cache it replaces was needed only because the old Area build was expensive;
 * refs #465).
 */
public final class NoteColumnGeometry {

    /**
     * Horizontal extent of a single note in note-local staff spaces.
     *
     * @param leftSs  left edge in staff spaces (note-local, ≤ 0)
     * @param rightSs right edge in staff spaces (note-local, ≥ 0)
     */
    public record ColumnExtent(double leftSs, double rightSs) {}

    private NoteColumnGeometry() {}

    /**
     * Computes the stem-free extent used for connecting-glissando attachment points, in
     * note-local staff-space coordinates. The stem and flag are excluded; the extent is
     * driven only by the notehead, augmentation dots (right), and accidental (left).
     *
     * @param note   the note whose attach extent to compute
     * @param beamed whether the note belongs to a beam group (affects dot placement)
     * @return the stem-free column extent
     */
    public static ColumnExtent glissandoAttachExtentSs(StaffElement note, boolean beamed) {
        var noteType = note.getType();
        var direction = NoteGeometry.effectiveDirection(note);

        // ---- notehead ----
        var glyph = noteType.requireSMuFLGlyph();
        var noteheadBBox = SMuFLMetadata.requireBBox(glyph);
        var offsetX = NoteGeometry.getNoteheadXOffsetSs(noteType, direction);
        var leftSs = offsetX + noteheadBBox.left();

        // ---- augmentation dots (extend right) ----
        var rightSs = NoteGeometry.dotsRightExtentSs(
            note, beamed, direction, noteType.getElementWidthSs());

        // ---- accidental (extend left only) ----
        if (note.getAccidental() != null) {
            var accidentalStartXSs = NoteGeometry.getAccidentalStartXSs(note);

            if (accidentalStartXSs < leftSs) {
                leftSs = accidentalStartXSs;
            }
        }

        return new ColumnExtent(leftSs, rightSs);
    }

    /**
     * Computes the full column extent for {@code note} in note-local staff-space
     * coordinates, including the stem. Used to anchor a trailing fall glyph.
     *
     * @param note   the note whose column extent to compute
     * @param beamed whether the note belongs to a beam group (affects dot placement)
     * @return the full column extent
     */
    public static ColumnExtent extentSs(StaffElement note, boolean beamed) {
        return extentSs(note, glissandoAttachExtentSs(note, beamed));
    }

    /**
     * Widens an already-computed stem-free extent into the full column extent by folding in the
     * stem. Lets callers that already hold the stem-free attach extent (see
     * {@link #glissandoAttachExtentSs}) reuse it instead of recomputing the notehead/dots/accidental
     * geometry.
     *
     * @param note     the note whose full extent to compute
     * @param stemFree the stem-free extent for the same note, from {@link #glissandoAttachExtentSs}
     * @return the full column extent
     */
    public static ColumnExtent extentSs(StaffElement note, ColumnExtent stemFree) {
        var noteType = note.getType();
        var leftSs = stemFree.leftSs();
        var rightSs = stemFree.rightSs();

        // Both widening guards below are inert with Bravura: its stem anchors sit exactly on the
        // notehead's outer bbox edges (grace stems sit well inside), so a stem never protrudes.
        // They are kept for fonts that place the anchors differently. NoteColumnGeometryTest pins
        // that premise, so a font change making these guards live is reported rather than
        // silently altering layout.
        if (noteType.isNoteWithStem()) {
            var direction = NoteGeometry.effectiveDirection(note);
            var stemGeom = NoteGeometry.computeBaseStemGeometry(noteType, direction);
            var stemLeftXSs = stemGeom.stemLeftXSs();

            if (direction.isUp()) {
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
        }

        return new ColumnExtent(leftSs, rightSs);
    }

}
