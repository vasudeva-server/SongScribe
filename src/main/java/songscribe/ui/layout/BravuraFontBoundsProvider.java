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

package songscribe.ui.layout;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.smufl.StaffSpaces;
import songscribe.ui.layout2.LayoutConstants;
import songscribe.ui.renderer.RenderContext;

/**
 * Provides font-specific glyph bounds using SMuFL metadata from the Bravura font.
 * <p>
 * This class implements the {@link FontBoundsProvider} interface using SMuFL metadata
 * instead of direct font glyph measurement. It replaces Fughetta-era hardcoded values
 * with metadata-driven bounds computation.
 * <p>
 * Created as part of Phase 4 of the SMuFL migration to enable SMuFL-driven glyph bounds.
 */
public class BravuraFontBoundsProvider implements FontBoundsProvider {

    // Precomputed constants from SMuFL metadata
    private static final SMuFLMetadata METADATA = SMuFLMetadata.getInstance();
    private static final double CROTCHET_WIDTH = StaffSpaces.toPixels(
        METADATA.getBBox(SMuFLGlyph.NOTEHEAD_BLACK).width()
    );
    private static final double STEM_LENGTH = StaffSpaces.toPixels(
        LayoutConstants.STEM_LENGTH_SS
    );

    // Instance fields
    private final RenderContext context;

    /**
     * Creates a new BravuraFontBoundsProvider.
     *
     * @param context The render context for coordinate conversions
     */
    public BravuraFontBoundsProvider(@NotNull RenderContext context) {
        this.context = context;
    }

    /**
     * Calculate the bounding box for a tempo note in the local coordinate
     * system (before scaling and translation). This includes the note head,
     * stem, flags, and dots.
     *
     * @param frc  the font render context
     * @param font the Bravura font (unused, kept for API compatibility)
     * @param note the tempo note
     * @return the bounding rectangle, or null if the note type has no glyph
     */
    @Nullable
    public static Rectangle2D getTempoNoteBounds(
        FontRenderContext frc,
        Font font,
        Note note
    ) {
        var noteType = note.getNoteType();
        var glyph = noteType.getSMuFLNoteheadGlyph();

        if (glyph == null) {
            return null;
        }

        // Get note head bounds from SMuFL metadata
        var bbox = METADATA.getBBox(glyph);
        var bounds = new Rectangle2D.Double(
            StaffSpaces.toPixels(bbox.left()),
            StaffSpaces.toPixels(bbox.top()),
            StaffSpaces.toPixels(bbox.width()),
            StaffSpaces.toPixels(bbox.height())
        );

        // Add stem bounds if note has stem
        if (noteType.isNoteWithStem()) {
            var anchors = METADATA.getAnchors(glyph);

            if (anchors != null && anchors.stemUpSE() != null) {
                // Use stem-up anchor (tempo notes always use stem-up)
                var stemAnchor = anchors.stemUpSE();
                var stemX = StaffSpaces.toPixels(stemAnchor.x());
                var stemTop = StaffSpaces.toPixels(stemAnchor.y()) - STEM_LENGTH;
                var stemBottom = StaffSpaces.toPixels(stemAnchor.y());

                // Account for stem stroke width (1px)
                var stemBounds = new Rectangle2D.Double(
                    stemX - 0.5,
                    stemTop,
                    1.0,
                    stemBottom - stemTop
                );

                bounds.add(stemBounds);
            }
        }

        // Add flag bounds if note type has a flag
        var flagGlyph = noteType.getFlagGlyph(true);

        if (flagGlyph != null) {
            var flagBBox = METADATA.getBBox(flagGlyph);
            var anchors = METADATA.getAnchors(glyph);

            if (anchors != null && anchors.stemUpSE() != null) {
                var stemAnchor = anchors.stemUpSE();
                var stemX = StaffSpaces.toPixels(stemAnchor.x());
                var stemTopY = StaffSpaces.toPixels(stemAnchor.y()) - STEM_LENGTH;

                // Position flag at top of stem
                var flagBounds = new Rectangle2D.Double(
                    stemX + StaffSpaces.toPixels(flagBBox.left()),
                    stemTopY + StaffSpaces.toPixels(flagBBox.top()),
                    StaffSpaces.toPixels(flagBBox.width()),
                    StaffSpaces.toPixels(flagBBox.height())
                );

                bounds.add(flagBounds);
            }
        }

        // Add dot bounds if note has dots
        if (note.getDotCount() > 0) {
            var dotGlyph = SMuFLGlyph.AUGMENTATION_DOT;
            var dotBBox = METADATA.getBBox(dotGlyph);
            var dotWidth = StaffSpaces.toPixels(dotBBox.width());
            var dotHeight = StaffSpaces.toPixels(dotBBox.height());

            // Dots are positioned to the right of the note head with a small gap
            var dotGap = 2.0;
            var dotX = bounds.getMaxX() + dotGap;
            var dotY = -dotHeight / 2.0; // Centered vertically on staff line

            var dotBounds = new Rectangle2D.Double(
                dotX,
                dotY,
                note.getDotCount() * (dotWidth + dotGap),
                dotHeight
            );

            bounds.add(dotBounds);
        }

        return bounds;
    }

    @Override
    @NotNull
    public Rectangle2D getNoteHeadStemBounds(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        int lineIndex
    ) {
        var type = note.getNoteType();
        var glyph = type.getSMuFLNoteheadGlyph();

        // Note anchor position in absolute coordinates
        var noteX = note.getXPosSs();
        var noteY = context.getNoteYPosPx(note.getStaffPosition(), lineIndex);

        if (glyph == null) {
            // Return a minimal bounds at the note position
            return new Rectangle2D.Double(noteX, noteY, 1, 1);
        }

        // Get note head bounding box from SMuFL metadata
        var bbox = METADATA.getBBox(glyph);
        Rectangle2D relativeBounds = new Rectangle2D.Double(
            StaffSpaces.toPixels(bbox.left()),
            StaffSpaces.toPixels(bbox.top()),
            StaffSpaces.toPixels(bbox.width()),
            StaffSpaces.toPixels(bbox.height())
        );

        // Include stem in bounds if note has a stem
        if (type.isNoteWithStem()) {
            var anchors = METADATA.getAnchors(glyph);

            if (anchors != null) {
                var stemAnchor = note.isUpper()
                    ? anchors.stemUpSE()
                    : anchors.stemDownNW();

                if (stemAnchor != null) {
                    var stemX = StaffSpaces.toPixels(stemAnchor.x());
                    var stemY = StaffSpaces.toPixels(stemAnchor.y());

                    Rectangle2D stemBounds;

                    if (note.isUpper()) {
                        // Stem extends upward from stemUpSE anchor
                        stemBounds = new Rectangle2D.Double(
                            stemX - 0.5,
                            stemY - STEM_LENGTH,
                            1.0,
                            STEM_LENGTH
                        );
                    } else {
                        // Stem extends downward from stemDownNW anchor
                        stemBounds = new Rectangle2D.Double(
                            stemX - 0.5,
                            stemY,
                            1.0,
                            STEM_LENGTH
                        );
                    }

                    relativeBounds = relativeBounds.createUnion(stemBounds);
                }
            }
        }

        // Convert to absolute coordinates
        return new Rectangle2D.Double(
            noteX + relativeBounds.getX(),
            noteY + relativeBounds.getY(),
            relativeBounds.getWidth(),
            relativeBounds.getHeight()
        );
    }

    @Override
    public double getCrotchetWidth() {
        return CROTCHET_WIDTH;
    }

    @Override
    public double getHalfNoteWidthForTie(@NotNull Note note) {
        var noteType = note.getNoteType();

        // For whole and half notes, use their specific glyph widths
        if (noteType == NoteType.SEMIBREVE || noteType == NoteType.MINIM) {
            var glyph = noteType.getSMuFLNoteheadGlyph();

            if (glyph != null) {
                var bbox = METADATA.getBBox(glyph);
                return StaffSpaces.toPixels(bbox.width()) / 2.0;
            }
        }

        // For all other notes, use half the crotchet width
        return CROTCHET_WIDTH / 2.0;
    }
}
