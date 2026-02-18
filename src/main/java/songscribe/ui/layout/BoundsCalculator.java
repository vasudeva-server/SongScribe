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

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Composition;
import songscribe.music.DurationArticulation;
import songscribe.music.ForceArticulation;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.ui.component.Score;
import songscribe.ui.renderer.BaseElementRenderer;
import songscribe.ui.renderer.TempoRenderer;

/**
 * Calculates bounding boxes for musical elements.
 * <p>
 * This class separates bounds calculation logic from rendering logic.
 * It uses a {@link FontBoundsProvider} for font-specific glyph bounds
 * and Score for layout context.
 * <p>
 * All returned bounds are in absolute coordinates (page-relative).
 *
 * @deprecated Replaced by new ElementRenderer system (Phase 6). Scheduled for removal in Phase 8.
 */
@Deprecated
public class BoundsCalculator {

    // -------------------------------------------------------------------------
    // Bounding Box Constants
    // -------------------------------------------------------------------------

    // Articulation bounds
    private static final int ACCENT_X_OFFSET = 2;
    private static final int ACCENT_HALF_HEIGHT = 3;
    private static final int ACCENT_HEIGHT = 6;
    private static final int STACCATO_OFFSET = 2;
    private static final double STACCATO_SIZE = 3.5;
    private static final int ARTICULATION_STAFF_OFFSET_LOW = 6;
    private static final int ARTICULATION_STAFF_OFFSET_HIGH = 3;

    // Element bounds (approximate widths/heights for layout)
    private static final int BEAT_CHANGE_APPROX_WIDTH = 50;
    private static final int FIRST_SECOND_ENDING_HEIGHT = 15;
    private static final int TRILL_APPROX_WIDTH = 30;
    private static final int ANNOTATION_APPROX_WIDTH = 50;
    private static final int ANNOTATION_FALLBACK_HEIGHT = 15;
    private static final int ANNOTATION_FALLBACK_ASCENT = 12;
    private static final int LYRICS_APPROX_WIDTH = 30;
    private static final int LYRICS_FALLBACK_DESCENT = 8;

    // Tuplet bounds
    private static final int TUPLET_Y_OFFSET = 5;
    private static final int TUPLET_MARGIN = 13;
    private static final int TUPLET_HEIGHT = 20;

    // Tempo bounds
    private static final int TEMPO_TEXT_APPROX_WIDTH = 100;

    private final Score score;
    private final FontBoundsProvider fontBoundsProvider;

    /**
     * Creates a new BoundsCalculator.
     *
     * @param score              The score component for layout context
     * @param fontBoundsProvider Provider for font-specific glyph bounds
     */
    public BoundsCalculator(
        @NotNull Score score,
        @NotNull FontBoundsProvider fontBoundsProvider
    ) {
        this.score = score;
        this.fontBoundsProvider = fontBoundsProvider;
    }

    // -------------------------------------------------------------------------
    // Note Bounds Methods
    // -------------------------------------------------------------------------

    /**
     * Returns the complete bounding box for a note including all visual elements.
     * This is the union of note head, stem, and articulations.
     *
     * @param g2        Graphics context for font metrics
     * @param note      The note to measure
     * @param lineIndex The staff line index
     * @return Rectangle2D in absolute coordinates (page-relative)
     */
    @NotNull
    public Rectangle2D getNoteBounds(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        int lineIndex
    ) {
        var bounds = fontBoundsProvider.getNoteHeadStemBounds(g2, note, lineIndex);
        var articulationBounds = getArticulationBounds(note, lineIndex);

        if (articulationBounds != null) {
            bounds = bounds.createUnion(articulationBounds);
        }

        return bounds;
    }

    /**
     * Returns the bounding box for the note's articulations (accent, staccato, tenuto).
     * <p>
     * This method uses the same positioning formulas as drawArticulation().
     * The returned bounds are in absolute coordinates (page-relative).
     *
     * @param note      The note to measure
     * @param lineIndex The staff line index
     * @return Rectangle2D in absolute coordinates, or null if no articulation
     */
    @Nullable
    public Rectangle2D getArticulationBounds(@NotNull Note note, int lineIndex) {
        var forceArticulation = note.getForceArticulation();
        var durationArticulation = note.getDurationArticulation();

        if (forceArticulation == null && durationArticulation == null) {
            return null;
        }

        Rectangle2D bounds = null;
        var xPos = note.getXPos();
        var crotchetWidth = fontBoundsProvider.getCrotchetWidth();

        // Accent bounds (mirrors drawArticulation logic)
        if (forceArticulation == ForceArticulation.ACCENT) {
            var x2 = xPos + (int) crotchetWidth + ACCENT_X_OFFSET;
            var y = getArticulationY(note, lineIndex);

            // Accent spans +/- ACCENT_HALF_HEIGHT pixels from center Y
            bounds = new Rectangle2D.Double(xPos, y - ACCENT_HALF_HEIGHT, x2 - xPos, ACCENT_HEIGHT);
        }

        // Staccato/Tenuto bounds (mirrors drawArticulation logic)
        if (durationArticulation != null) {
            var dir = note.isUpper() ? 1 : -1;
            var durY = score.getNoteYPos(
                note.getYPos() + (dir * 2) + (dir * (1 - (note.getYPos() % 2))),
                lineIndex
            );

            if (durationArticulation == DurationArticulation.STACCATO) {
                // Staccato: ellipse STACCATO_SIZE x STACCATO_SIZE
                var staccatoX = (xPos + fontBoundsProvider.getHalfNoteWidthForTie(note)) - STACCATO_OFFSET;
                var durBounds = new Rectangle2D.Double(
                    staccatoX, durY - STACCATO_OFFSET, STACCATO_SIZE, STACCATO_SIZE
                );
                bounds = (bounds == null) ? durBounds : bounds.createUnion(durBounds);
            }
        }

        return bounds;
    }

    /**
     * Calculates the Y position for accent articulation.
     * This mirrors the getY() method used in drawArticulation().
     *
     * @param note      The note
     * @param lineIndex The staff line index
     * @return Y coordinate for accent center
     */
    private int getArticulationY(@NotNull Note note, int lineIndex) {
        int y;
        var noteYPos = note.getYPos();

        if (note.isUpper()) {
            // Stem up -> articulation below
            if (noteYPos <= ARTICULATION_STAFF_OFFSET_HIGH) {
                y = score.getNoteYPos(ARTICULATION_STAFF_OFFSET_LOW, lineIndex);
            } else {
                y = score.getNoteYPos(noteYPos + ARTICULATION_STAFF_OFFSET_HIGH, lineIndex);
            }
        } else {
            // Stem down -> articulation above
            if (noteYPos >= -ARTICULATION_STAFF_OFFSET_HIGH) {
                y = score.getNoteYPos(-ARTICULATION_STAFF_OFFSET_LOW, lineIndex);
            } else {
                y = score.getNoteYPos(noteYPos - ARTICULATION_STAFF_OFFSET_HIGH, lineIndex);
            }
        }

        return y;
    }

    // -------------------------------------------------------------------------
    // Line Bounds Methods
    // -------------------------------------------------------------------------

    /**
     * Returns the bounding box for a complete line including all elements.
     * This is the single source of truth for line bounds calculations.
     * <p>
     * Includes: staff lines, tempo, beat changes, endings, trills, notes,
     * articulations, annotations, and lyrics.
     *
     * @param g2        Graphics context for font metrics
     * @param line      The musical line to measure
     * @param lineIndex The staff line index
     * @return Rectangle2D in absolute coordinates (page-relative)
     */
    @NotNull
    public Rectangle2D getLineBounds(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        int lineIndex
    ) {
        var composition = score.getComposition();
        var middleLineY = score.getNoteYPos(0, lineIndex);
        var frc = g2.getFontRenderContext();

        // Start with staff bounds (top and bottom staff lines, full width)
        var topStaffY = score.getNoteYPos(-4, lineIndex);
        var bottomStaffY = score.getNoteYPos(4, lineIndex);

        var bounds = new Rectangle2D.Double(
            0, topStaffY, composition.getLineWidth(), bottomStaffY - topStaffY
        );

        // Add tempo bounds
        if (line.getFirstTempoChange() >= 0) {
            var tempoBounds = getTempoBounds(g2, line, lineIndex);
            if (tempoBounds != null) {
                bounds.add(tempoBounds);
            }
        }

        // Add beat change bounds
        if (line.getFirstBeatChange() >= 0) {
            var beatChangeYPos = middleLineY + line.getBeatChangeYPos();
            var font = composition.getAttributionFont();
            var lineMetrics = font.getLineMetrics("M", frc);
            var beatChangeTop = beatChangeYPos - lineMetrics.getAscent();
            bounds.add(new Rectangle2D.Double(
                0, beatChangeTop, BEAT_CHANGE_APPROX_WIDTH, lineMetrics.getHeight()
            ));
        }

        // Add first/second endings bounds
        if (!line.getFirstSecondEndings().isEmpty()) {
            var endingYPos = middleLineY + line.getFirstSecondEndingYPos();
            bounds.add(new Rectangle2D.Double(
                0, endingYPos, composition.getLineWidth(), FIRST_SECOND_ENDING_HEIGHT
            ));
        }

        // Add trill bounds
        if (line.getFirstTrill() >= 0) {
            var trillYPos = middleLineY + line.getTrillYPos();
            var font = composition.getAttributionFont();
            var lineMetrics = font.getLineMetrics("tr", frc);
            var trillTop = trillYPos - lineMetrics.getAscent();
            bounds.add(new Rectangle2D.Double(0, trillTop, TRILL_APPROX_WIDTH, lineMetrics.getHeight()));
        }

        // Add all note bounds (includes heads, stems, flags, articulations)
        for (var note : line.getNotes()) {
            var noteBounds = getNoteBounds(g2, note, lineIndex);
            bounds.add(noteBounds);

            // Add annotation bounds
            var annotation = note.getAnnotation();
            if (annotation != null) {
                var annotationY = middleLineY + annotation.getYPos();
                var annotationMetrics = composition.getAnnotationFontMetrics();
                var annotationHeight = (annotationMetrics != null)
                    ? annotationMetrics.getHeight() : ANNOTATION_FALLBACK_HEIGHT;
                var annotationAscent = (annotationMetrics != null)
                    ? annotationMetrics.getAscent() : ANNOTATION_FALLBACK_ASCENT;
                bounds.add(new Rectangle2D.Double(
                    note.getXPos(), annotationY - annotationAscent,
                    ANNOTATION_APPROX_WIDTH, annotationHeight
                ));
            }

            // Track syllables for lyrics
            var syllable = note.acceleration.syllable;
            if (syllable != null && !syllable.isEmpty()) {
                var lyricsY = middleLineY + line.getLyricsYPos();
                var lyricsMetrics = composition.getLyricsFontMetrics();
                var lyricsDescent = (lyricsMetrics != null)
                    ? lyricsMetrics.getDescent() : LYRICS_FALLBACK_DESCENT;
                bounds.add(new Rectangle2D.Double(
                    note.getXPos(), lyricsY, LYRICS_APPROX_WIDTH, lyricsDescent
                ));
            }
        }

        // Add tuplet bounds
        addTupletBounds(bounds, line, lineIndex);

        return bounds;
    }

    /**
     * Adds tuplet bounds to the overall line bounds.
     */
    private void addTupletBounds(
        @NotNull Rectangle2D bounds,
        @NotNull Line line,
        int lineIndex
    ) {
        for (var tupletIter = line.getTuplets().listIterator(); tupletIter.hasNext(); ) {
            var tuplet = tupletIter.next();
            var firstNote = line.getNote(tuplet.getStart());
            var lastNote = line.getNote(tuplet.getEnd());
            var firstNoteY = score.getNoteYPos(firstNote.getYPos(), lineIndex);
            var lastNoteY = score.getNoteYPos(lastNote.getYPos(), lineIndex);

            if (firstNote.isUpper()) {
                // Tuplet above notes
                var firstY = firstNoteY - Note.HOT_SPOT.y - firstNote.acceleration.lengthening - TUPLET_Y_OFFSET;
                var lastY = lastNoteY - Note.HOT_SPOT.y - lastNote.acceleration.lengthening - TUPLET_Y_OFFSET;
                var tupletTop = Math.min(firstY, lastY) - TUPLET_MARGIN;
                bounds.add(new Rectangle2D.Double(
                    firstNote.getXPos(), tupletTop,
                    lastNote.getXPos() - firstNote.getXPos(), TUPLET_HEIGHT
                ));
            } else {
                // Tuplet below notes
                var firstY = firstNoteY + Note.HOT_SPOT.y - firstNote.acceleration.lengthening
                    + (Note.HOT_SPOT.y - ARTICULATION_STAFF_OFFSET_HIGH) - TUPLET_Y_OFFSET;
                var lastY = lastNoteY + Note.HOT_SPOT.y - lastNote.acceleration.lengthening
                    + (Note.HOT_SPOT.y - ARTICULATION_STAFF_OFFSET_HIGH) - TUPLET_Y_OFFSET;
                var tupletBottom = Math.max(firstY, lastY) + TUPLET_MARGIN;
                bounds.add(new Rectangle2D.Double(
                    firstNote.getXPos(), tupletBottom - TUPLET_HEIGHT,
                    lastNote.getXPos() - firstNote.getXPos(), TUPLET_HEIGHT
                ));
            }
        }
    }

    /**
     * Returns the bounding box for the tempo marking including note and text.
     *
     * @param g2        Graphics context for font metrics
     * @param line      The musical line
     * @param lineIndex The staff line index
     * @return Rectangle2D in absolute coordinates, or null if no tempo
     */
    @Nullable
    public Rectangle2D getTempoBounds(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        int lineIndex
    ) {
        if (line.getFirstTempoChange() < 0) {
            return null;
        }

        var firstTempoIndex = line.getFirstTempoChange();
        var tempoNote = line.getNote(firstTempoIndex);
        var tempo = tempoNote.getTempoChange();
        if (tempo == null) {
            return null;
        }

        var composition = score.getComposition();
        var middleLineY = score.getNoteYPos(0, lineIndex);
        var frc = g2.getFontRenderContext();

        // Calculate tempo baseline Y (matches drawTempoChange)
        var tempoYPos = middleLineY + getEffectiveTempoChangeYPos(g2, line, lineIndex);

        Rectangle2D bounds = null;

        // Add tempo note bounds if showing
        if (tempo.shouldShowTempo()) {
            var tempoTypeNote = tempo.getTempoType().getNote();
            var noteBounds = TempoRenderer.getTempoNoteBounds(frc, BaseElementRenderer.FUGHETTA, tempoTypeNote);
            if (noteBounds != null) {
                // Apply same transform as drawTempoChangeNote
                var noteYOffset = tempoYPos - ((BaseElementRenderer.NOTE_FONT_SIZE * BaseElementRenderer.TEMPO_CHANGE_ZOOM_Y) / 8.0);
                bounds = new Rectangle2D.Double(
                    tempoNote.getXPos() + (noteBounds.getX() * BaseElementRenderer.TEMPO_CHANGE_ZOOM_X),
                    noteYOffset + (noteBounds.getY() * BaseElementRenderer.TEMPO_CHANGE_ZOOM_Y),
                    noteBounds.getWidth() * BaseElementRenderer.TEMPO_CHANGE_ZOOM_X,
                    noteBounds.getHeight() * BaseElementRenderer.TEMPO_CHANGE_ZOOM_Y
                );
            }
        }

        // Add text bounds
        var font = composition.getAttributionFont();
        var lineMetrics = font.getLineMetrics("M", frc);
        var textTop = tempoYPos - lineMetrics.getAscent();
        var textBounds = new Rectangle2D.Double(
            tempoNote.getXPos(),
            textTop,
            TEMPO_TEXT_APPROX_WIDTH,
            lineMetrics.getAscent() + lineMetrics.getDescent()
        );

        if (bounds == null) {
            bounds = textBounds;
        } else {
            bounds.add(textBounds);
        }

        return bounds;
    }

    // -------------------------------------------------------------------------
    // Line Distance Methods
    // -------------------------------------------------------------------------

    /**
     * Returns the distance (pixels) from the top staff line to the highest element
     * in the specified line, including articulations.
     *
     * @param g2        Graphics context for font metrics
     * @param line      The musical line to measure
     * @param lineIndex The staff line index
     * @return Distance above top staff line in pixels, 0 if nothing extends above
     */
    public int getLineDistanceAboveTopLine(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        int lineIndex
    ) {
        var topStaffY = score.getNoteYPos(-4, lineIndex);
        var lineBounds = getLineBounds(g2, line, lineIndex);
        return Math.max(0, (int) (topStaffY - lineBounds.getMinY()));
    }

    /**
     * Returns the distance (pixels) from the bottom staff line to the lowest element
     * in the specified line, including articulations.
     *
     * @param g2        Graphics context for font metrics
     * @param line      The musical line to measure
     * @param lineIndex The staff line index
     * @return Distance below bottom staff line in pixels, 0 if nothing extends below
     */
    public int getLineDistanceBelowBottomLine(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        int lineIndex
    ) {
        var bottomStaffY = score.getNoteYPos(4, lineIndex);
        var lineBounds = getLineBounds(g2, line, lineIndex);
        return Math.max(0, (int) (lineBounds.getMaxY() - bottomStaffY));
    }

    /**
     * Returns the effective Y position for the tempo marking on this line,
     * accounting for notes and articulations that extend above the staff.
     * <p>
     * The tempo is positioned to maintain ANNOTATION_MARGIN (24px) above the
     * highest element (note or articulation).
     *
     * @param g2        Graphics context for font metrics
     * @param line      The musical line
     * @param lineIndex The staff line index
     * @return The effective Y position for the tempo marking (relative to middle line)
     */
    public int getEffectiveTempoChangeYPos(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        int lineIndex
    ) {
        // If no notes extend above staff and no articulations above, use the stored position
        int topLineY = (int) (-4 * Score.NOTE_Y_OFFSET); // -16
        int highestNoteOrArticulation = topLineY;

        // Get the middle line Y for coordinate conversion (absolute -> relative)
        int middleLineY = score.getNoteYPos(0, lineIndex);

        for (var note : line.getNotes()) {
            // Check full note bounds (includes head, stem, flags, and articulations)
            var noteBounds = getNoteBounds(g2, note, lineIndex);

            int noteTop = (int) noteBounds.getMinY() - middleLineY;
            highestNoteOrArticulation = Math.min(highestNoteOrArticulation, noteTop);
        }

        // Calculate required tempo position to maintain margin (3 staff lines = 24px)
        int annotationMargin = LayoutManager.ANNOTATION_MARGIN;
        int requiredTempoBottom = highestNoteOrArticulation - annotationMargin;

        // Use either stored position or calculated position, whichever is higher (more negative)
        int storedYPos = line.getTempoChangeYPos();
        return Math.min(storedYPos, requiredTempoBottom);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the score component.
     */
    @NotNull
    public Score getScore() {
        return score;
    }

    /**
     * Returns the font bounds provider.
     */
    @NotNull
    public FontBoundsProvider getFontBoundsProvider() {
        return fontBoundsProvider;
    }
}
