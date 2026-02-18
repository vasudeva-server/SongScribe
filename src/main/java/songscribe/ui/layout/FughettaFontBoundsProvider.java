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

package songscribe.ui.layout;

import java.awt.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.util.EnumMap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.ui.renderer.BaseElementRenderer;
import songscribe.ui.renderer.RenderContext;

/**
 * Provides font-specific glyph bounds for the Fughetta music font.
 * <p>
 * This class implements the {@link FontBoundsProvider} interface using the Fughetta
 * font metrics. It was extracted from FughettaRenderer to enable clean separation
 * of font measurement from rendering logic.
 * <p>
 * Created as part of Phase 9.6 to enable removal of the legacy Renderer system.
 */
public class FughettaFontBoundsProvider implements FontBoundsProvider {

    // The font size of the notes using Fughetta
    public static final float NOTE_FONT_SIZE = 32;

    // Note head Unicode character mappings for Fughetta font
    private static final EnumMap<NoteType, String> noteHead = new EnumMap<>(
        NoteType.class
    );

    static {
        noteHead.put(NoteType.SEMIBREVE, "\uf077");
        noteHead.put(NoteType.MINIM, "\uf0cd");
        noteHead.put(NoteType.CROTCHET, "\uf0cf");
        noteHead.put(NoteType.QUAVER, "\uf0cf");
        noteHead.put(NoteType.SEMIQUAVER, "\uf0cf");
        noteHead.put(NoteType.DEMI_SEMIQUAVER, "\uf0cf");
        noteHead.put(NoteType.SEMIBREVE_REST, "\uf0ee");
        noteHead.put(NoteType.MINIM_REST, "\uf0ee");
        noteHead.put(NoteType.CROTCHET_REST, "\uf0ce");
        noteHead.put(NoteType.QUAVER_REST, "\uf0e4");
        noteHead.put(NoteType.SEMIQUAVER_REST, "\uf0c5");
        noteHead.put(NoteType.DEMI_SEMIQUAVER_REST, "\uf0a8");
    }

    // Stem positioning constants
    private static final double upperCrotchetStemX =
        NOTE_FONT_SIZE / 3.6056337d;
    private static final double upperMinimStemX = NOTE_FONT_SIZE / 3.1411042f;
    private static final double tempoStemShortening = 2;

    // Stem geometry (for bounds calculation)
    private static final Line2D.Float upperStem = new Line2D.Float(
        0f,
        -NOTE_FONT_SIZE / 32f,
        0f,
        -NOTE_FONT_SIZE / 1.1429f
    );
    private static final Line2D.Float lowerStem = new Line2D.Float(
        0f,
        NOTE_FONT_SIZE / 60f,
        0f,
        NOTE_FONT_SIZE / 1.1429f
    );

    // Flag positioning (used by getTempoNoteBounds)
    private static final double upperFlagX = NOTE_FONT_SIZE / 3.6834533d;
    private static final double upperFlagY = -NOTE_FONT_SIZE / 1.6623377d;

    // The Fughetta font
    private static final Font FUGHETTA = BaseElementRenderer.MUSIC_FONT;

    // Instance fields
    private final RenderContext context;
    private final double crotchetWidth;

    /**
     * Creates a new FughettaFontBoundsProvider.
     *
     * @param context The render context for coordinate conversions
     */
    public FughettaFontBoundsProvider(@NotNull RenderContext context) {
        this.context = context;
        this.crotchetWidth = upperCrotchetStemX;
    }

    /**
     * Returns the Unicode character for a note head in the Fughetta font.
     *
     * @param noteType The note type
     * @return The Unicode character, or null if not found
     */
    @Nullable
    public static String getNoteHeadChar(NoteType noteType) {
        return noteHead.get(noteType);
    }

    /**
     * Calculate the bounding box for a tempo note in the local coordinate
     * system (before scaling and translation). This includes the note head,
     * stem, flags, and dots.
     *
     * @param frc  the font render context
     * @param font the Fughetta font
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
        var noteHeadChar = getNoteHeadChar(noteType);

        if (noteHeadChar == null) {
            return null;
        }

        // Get note head bounds
        var noteGv = font.createGlyphVector(frc, noteHeadChar);
        var bounds = new Rectangle2D.Double();
        bounds.setRect(noteGv.getVisualBounds());

        // Add stem bounds if note has stem
        if (noteType.isNoteWithStem()) {
            var stemX = (noteType == NoteType.MINIM)
                ? upperMinimStemX
                : upperCrotchetStemX;
            var stemY1 = upperStem.getY1();
            var stemY2 = upperStem.getY2() + tempoStemShortening;

            // Account for stem stroke width (1px) and cap
            // Use min/max to handle both upper and lower stems
            var stemTop = Math.min(stemY1, stemY2);
            var stemBottom = Math.max(stemY1, stemY2);
            var stemBounds = new Rectangle2D.Double(
                stemX - 0.5,
                stemTop,
                1.0,
                stemBottom - stemTop
            );

            bounds.add(stemBounds);
        }

        // Add flag bounds if note is beamable (quaver, semiquaver, etc.)
        if (noteType.isBeamable()) {
            // Approximate flag bounds - flags extend upward from the stem
            var flagBounds = new Rectangle2D.Double(
                upperFlagX,
                upperFlagY + tempoStemShortening,
                5.0, // approximate flag width
                10.0 // approximate flag height
            );

            bounds.add(flagBounds);
        }

        // Add dot bounds if note has dots
        if (note.getDotCount() > 0) {
            // Dots are positioned to the right of the note head
            var dotX = bounds.getMaxX() + 2;
            var dotY = 0;
            var dotBounds = new Rectangle2D.Double(
                dotX,
                dotY - 2,
                note.getDotCount() * 4.0,
                4.0
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
        var headStr = noteHead.get(type);

        // Note anchor position in absolute coordinates
        var noteX = note.getXPos();
        var noteY = context.getNoteYPos(note.getYPos(), lineIndex);

        if (headStr == null) {
            // Return a minimal bounds at the note position
            return new Rectangle2D.Double(noteX, noteY, 1, 1);
        }

        var frc = g2.getFontRenderContext();
        var gv = FUGHETTA.createGlyphVector(frc, headStr);
        var relativeBounds = gv.getVisualBounds();

        // Include stem in bounds if note has a stem
        if (type.isNoteWithStem()) {
            var stemBounds = note.isUpper()
                ? upperStem.getBounds2D()
                : lowerStem.getBounds2D();

            if (note.isUpper()) {
                stemBounds = new Rectangle2D.Double(
                    upperCrotchetStemX + stemBounds.getX(),
                    stemBounds.getY(),
                    stemBounds.getWidth(),
                    stemBounds.getHeight()
                );
            }

            relativeBounds = relativeBounds.createUnion(stemBounds);
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
        return crotchetWidth;
    }

    @Override
    public double getHalfNoteWidthForTie(@NotNull Note note) {
        if (
            (note.getNoteType() == NoteType.SEMIBREVE) ||
                (note.getNoteType() == NoteType.MINIM)
        ) {
            return note.getRealUpNoteRect().width / 2.0;
        }

        return crotchetWidth / 2.0;
    }
}
