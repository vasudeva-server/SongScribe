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

import static songscribe.ui.renderer.GraphicsState.Property.FONT;
import static songscribe.ui.renderer.GraphicsState.Property.STROKE;

import module java.desktop;

import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.jetbrains.annotations.NotNull;

import songscribe.data.Interval;
import songscribe.data.IntervalSet;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.ui.Constants;
import songscribe.util.MyFontUtils;

/**
 * Renders per-note lyrics (syllables) beneath the staff.
 * <p>
 * Handles:
 * <ul>
 *   <li>Syllable text centered under each note</li>
 *   <li>Single hyphen between syllables of the same word (ONE_DASH relation)</li>
 *   <li>Extender lines for held syllables (EXTENDER relation)</li>
 *   <li>Begin-of-line continuation from previous line</li>
 * </ul>
 * <p>
 * Following Gould/Ross engraving rules:
 * <ul>
 *   <li>Hyphen = syllable division only (single hyphen)</li>
 *   <li>Extender = duration only (continuous line)</li>
 *   <li>Never use multiple hyphens to show duration</li>
 * </ul>
 */
public class LyricsRenderer {

    // ==========================================================================
    // Constants
    // ==========================================================================

    /**
     * Stroke for single hyphen between syllables.
     * Following Gould/Ross rules: hyphen indicates syllable division only.
     */
    private static final BasicStroke HYPHEN_STROKE = new BasicStroke(
        1f,
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER
    );

    /**
     * Width of the hyphen.
     */
    private static final float HYPHEN_WIDTH_PX = 7f;

    /**
     * Stroke for extender lines.
     * Following Gould/Ross rules: extender indicates duration only.
     */
    private static final BasicStroke EXTENDER_STROKE = new BasicStroke(
        0.836f,
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER
    );

    // Singleton instance
    private static final LyricsRenderer INSTANCE = new LyricsRenderer();

    // ==========================================================================
    // Instance State
    // ==========================================================================

    /**
     * Cached max descent from lyrics font metrics.
     * Initialized on first render call.
     */
    private int lyricsMaxDescentPx = 0;

    /**
     * Offset from lyrics baseline to dash Y position.
     * Calculated based on x-height of lyrics font.
     */
    private float dashOffsetPx = 0;

    // ==========================================================================
    // Constructor
    // ==========================================================================

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private LyricsRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static @NotNull LyricsRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Public API
    // ==========================================================================

    /**
     * Renders all lyrics for a line.
     * <p>
     * This method iterates through all notes in the line and renders:
     * <ul>
     *   <li>Syllable text under each note</li>
     *   <li>Dashes or extenders connecting syllables</li>
     * </ul>
     *
     * @param g2         Graphics context
     * @param line       The music line
     * @param ctx        Render context
     * @param isLastLine Whether this is the last line in the composition
     */
    public void renderLyrics(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx,
        boolean isLastLine
    ) {
        initializeMetrics(g2, ctx);

        try (var ignored = GraphicsState.save(g2, FONT)) {
            var composition = ctx.getComposition();
            var font = composition.getLyricsFont();
            g2.setFont(font);

            // Track which syllables have been drawn (for relation handling)
            var drawnIndex = 0;

            for (var noteIndex = 0; noteIndex < line.elementCount(); noteIndex++) {
                var note = line.getElement(noteIndex);
                drawnIndex = renderNoteLyrics(
                    g2, line, ctx, isLastLine, noteIndex, note, drawnIndex
                );
            }
        }
    }

    // ==========================================================================
    // Private Implementation
    // ==========================================================================

    /**
     * Gets the Y position for lyrics from layout result.
     */
    private int getEffectiveLyricsYPosPx(
        @NotNull Line line,
        @NotNull ElementRenderContext ctx
    ) {
        var layoutResult = ctx.getLayoutResult();

        if (layoutResult == null) {
            throw new IllegalStateException("Layout result must be available for rendering");
        }

        var bounds = layoutResult.getBounds(line);

        if (bounds == null) {
            throw new IllegalStateException("No bounds found for Line (lyrics)");
        }

        return (int) bounds.getTop();
    }

    /**
     * Initializes font metrics on first call.
     */
    private void initializeMetrics(@NotNull Graphics2D g2, @NotNull ElementRenderContext ctx) {
        if (lyricsMaxDescentPx != 0) {
            return;
        }

        try (var ignored = GraphicsState.save(g2, FONT)) {
            var composition = ctx.getComposition();
            var font = composition.getLyricsFont();
            g2.setFont(font);
            var metrics = g2.getFontMetrics(font);

            lyricsMaxDescentPx = metrics.getMaxDescent();

            // Calculate hyphen offset based on x-height
            var halfHyphenHeight = HYPHEN_STROKE.getLineWidth() / 2;
            dashOffsetPx = MyFontUtils.getXHeight(g2) - halfHyphenHeight;
        }
    }

    /**
     * Renders lyrics for a single note.
     *
     * @return Updated drawn index for tracking relation rendering
     */
    private int renderNoteLyrics(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx,
        boolean isLastLine,
        int noteIndex,
        @NotNull StaffElement note,
        int drawnIndex
    ) {
        var composition = ctx.getComposition();

        // Calculate lyrics Y position
        var lyricsY = getEffectiveLyricsYPosPx(line, ctx);

        var font = composition.getLyricsFont();
        g2.setFont(font);
        var metrics = g2.getFontMetrics(font);

        var syllableWidth = 0;
        var syllable = note.properties.syllable;
        var dashY = lyricsY - dashOffsetPx;

        // Draw syllable text (if not underscore placeholder)
        if (syllable != null && !syllable.equals(Constants.UNDERSCORE)) {
            syllableWidth = metrics.stringWidth(syllable);
            var layoutResult = ctx.getLayoutResult();
            var noteX = (layoutResult != null) ? layoutResult.getElementXSs(note) : note.getXPosSs();
            var lyricsX = (int) ((noteX + note.getContentCenterX()) -
                (syllableWidth / 2) +
                note.getSyllableMovement());

            if (!syllable.isEmpty()) {
                g2.drawString(syllable, lyricsX, lyricsY);
            }

            // Handle begin-of-line hyphen (continuation from previous line)
            if (noteIndex == 0 && line.beginRelation == StaffElement.SyllableRelation.ONE_DASH) {
                try (var ignored = GraphicsState.save(g2, STROKE)) {
                    g2.setStroke(HYPHEN_STROKE);
                    g2.draw(new Line2D.Float(
                        (float) (lyricsX - HYPHEN_WIDTH_PX - 10),
                        dashY,
                        (float) (lyricsX - 10),
                        dashY
                    ));
                }
            }
        }

        // Handle syllable relations (dashes, extenders)
        if (drawnIndex <= noteIndex &&
            (note.properties.syllableRelation != StaffElement.SyllableRelation.NO ||
                (noteIndex == 0 && line.beginRelation == StaffElement.SyllableRelation.EXTENDER))) {

            drawnIndex = renderSyllableRelation(
                g2, line, ctx, noteIndex, note, drawnIndex,
                syllableWidth, lyricsY, dashY
            );
        }

        return drawnIndex;
    }

    /**
     * Renders syllable relation (dash, extender, or single dash).
     *
     * @return Updated drawn index
     */
    private int renderSyllableRelation(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx,
        int noteIndex,
        @NotNull StaffElement note,
        int drawnIndex,
        int syllableWidth,
        float lyricsY,
        float dashY
    ) {
        var composition = ctx.getComposition();
        var metrics = g2.getFontMetrics();

        // Determine the relation type
        var relation = (note.properties.syllableRelation != StaffElement.SyllableRelation.NO)
            ? note.properties.syllableRelation
            : line.beginRelation;

        // Find the end note index for this relation
        int endIndex = findRelationEndIndex(line, noteIndex, relation);
        drawnIndex = endIndex;

        // Calculate start X position
        var startX = calculateRelationStartXPx(line, noteIndex, note, syllableWidth);

        // Calculate end X position
        var endX = calculateRelationEndXPx(
            g2, line, composition, noteIndex, endIndex, relation, startX
        );

        // Draw the relation
        drawRelation(g2, line, noteIndex, endIndex, relation, startX, endX, lyricsY, dashY, note);

        return drawnIndex;
    }

    /**
     * Finds the end note index for a syllable relation.
     */
    private int findRelationEndIndex(
        @NotNull Line line,
        int noteIndex,
        @NotNull StaffElement.SyllableRelation relation
    ) {
        int endIndex;

        if (relation == StaffElement.SyllableRelation.ONE_DASH) {
            // Find next note with actual syllable (not underscore or empty)
            endIndex = noteIndex + 1;

            while (endIndex < line.elementCount()) {
                var nextSyllable = line.getElement(endIndex).properties.syllable;

                if (!nextSyllable.equals(Constants.UNDERSCORE) && !nextSyllable.isEmpty()) {
                    break;
                }

                endIndex++;
            }
        } else {
            // For extender, continue while same relation or empty syllable
            endIndex = noteIndex;

            while (endIndex < line.elementCount()) {
                var nextElement = line.getElement(endIndex);

                if (nextElement.properties.syllableRelation != relation &&
                    !nextElement.properties.syllable.isEmpty()) {
                    break;
                }

                endIndex++;
            }
        }

        return endIndex;
    }

    /**
     * Calculates the start X position for a relation line.
     */
    private int calculateRelationStartXPx(
        @NotNull Line line,
        int noteIndex,
        @NotNull StaffElement note,
        int syllableWidth
    ) {
        // Begin-of-line extender starts before the note
        if (noteIndex == 0 && line.beginRelation == StaffElement.SyllableRelation.EXTENDER) {
            return note.getXPosSs() - 10;
        }

        // Normal case: start after the syllable
        return (int) (note.getXPosSs() +
            note.getContentCenterX() +
            (syllableWidth / 2) +
            note.getSyllableMovement() +
            2);
    }

    /**
     * Calculates the end X position for a relation line.
     */
    private int calculateRelationEndXPx(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull songscribe.music.Composition composition,
        int noteIndex,
        int endIndex,
        @NotNull StaffElement.SyllableRelation relation,
        int startX
    ) {
        // At end of line
        if (endIndex == line.elementCount()) {
            return (relation == StaffElement.SyllableRelation.ONE_DASH)
                ? startX + (int) (HYPHEN_WIDTH_PX * 2f)
                : (int) composition.getLineWidth();
        }

        var endNote = line.getElement(endIndex);

        if (relation == StaffElement.SyllableRelation.EXTENDER) {
            return endNote.getXPosSs() + 12;
        }

        if (relation == StaffElement.SyllableRelation.ONE_DASH &&
            endNote.properties.syllable.isEmpty()) {
            return startX + (int) (HYPHEN_WIDTH_PX * 2f);
        }

        // End before the next syllable
        var nextSyllable = endNote.properties.syllable;
        var nextSyllableWidth = g2.getFontMetrics().stringWidth(nextSyllable);

        return (int) ((endNote.getXPosSs() + endNote.getContentCenterX()) -
            (nextSyllableWidth / 2) +
            endNote.getSyllableMovement() -
            2);
    }

    /**
     * Draws the relation line (hyphen or extender).
     * Following Gould/Ross rules:
     * - Hyphen (ONE_DASH) = syllable division only
     * - Extender = duration only
     */
    private void drawRelation(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        int startIndex,
        int endIndex,
        @NotNull StaffElement.SyllableRelation relation,
        int startX,
        int endX,
        float lyricsY,
        float dashY,
        @NotNull StaffElement note
    ) {
        switch (relation) {
            case EXTENDER -> drawExtender(g2, line, startIndex, endIndex, startX, endX, (int) lyricsY);

            case ONE_DASH -> drawHyphen(g2, note, startX, endX, dashY);

            default -> {
                // NO relation - nothing to draw
            }
        }
    }

    /**
     * Draws extender line (continuous line) for held syllables.
     * Following Gould/Ross: extender indicates duration only.
     */
    private void drawExtender(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        int startIndex,
        int endIndex,
        int startX,
        int endX,
        int lyricsY
    ) {
        try (var ignored = GraphicsState.save(g2, STROKE)) {
            g2.setStroke(EXTENDER_STROKE);
            drawWithEmptySyllablesExclusion(
                g2,
                startX,
                lyricsY,
                endX,
                lyricsY,
                line,
                startIndex,
                endIndex + 1
            );
        }
    }

    /**
     * Draws a single hyphen between syllables.
     * Following Gould/Ross: hyphen indicates syllable division only.
     */
    private void drawHyphen(
        @NotNull Graphics2D g2,
        @NotNull StaffElement note,
        int startX,
        int endX,
        float dashY
    ) {
        try (var ignored = GraphicsState.save(g2, STROKE)) {
            g2.setStroke(HYPHEN_STROKE);

            // Store the position for potential adjustment
            note.properties.longDashPosition = ((endX - startX) / 2f) + startX;

            // Use adjusted position if set, otherwise use calculated center
            var centerX = (note.getSyllableRelationMovement() == 0)
                ? note.properties.longDashPosition
                : note.getXPosSs() + note.getSyllableRelationMovement();

            g2.draw(new Line2D.Float(
                centerX - (HYPHEN_WIDTH_PX / 2f),
                dashY,
                centerX + (HYPHEN_WIDTH_PX / 2f),
                dashY
            ));
        }
    }

    /**
     * Draws a line, splitting around notes with empty syllables.
     * <p>
     * Empty syllables indicate "breathing room" where the line shouldn't be drawn.
     */
    private void drawWithEmptySyllablesExclusion(
        @NotNull Graphics2D g2,
        int x1,
        int y1,
        int x2,
        int y2,
        @NotNull Line line,
        int startIndex,
        int endIndex
    ) {
        var end = Math.min(line.elementCount(), endIndex);

        // Find notes with empty syllables
        var emptySyllables = IntStream.range(startIndex, end)
            .filter(i -> line.getElement(i).properties.syllable.isEmpty())
            .boxed()
            .collect(Collectors.toCollection(ArrayList::new));

        if (emptySyllables.isEmpty()) {
            g2.drawLine(x1, y1, x2, y2);
            return;
        }

        // Create intervals excluding empty syllables
        var intervalSet = new IntervalSet<Interval>();
        intervalSet.addInterval(startIndex, end);

        for (var i : emptySyllables) {
            intervalSet.removeInterval(i, i + 1);
        }

        // Draw line segments for each interval
        for (var iter = intervalSet.listIterator(); iter.hasNext(); ) {
            var interval = iter.next();

            var drawX1 = (interval.getStart() == startIndex)
                ? x1
                : line.getElement(interval.getStart()).getXPosSs();

            var drawX2 = (interval.getEnd() == end)
                ? x2
                : line.getElement(interval.getEnd() - 1).getXPosSs() + 12;

            g2.drawLine(drawX1, y1, drawX2, y2);
        }
    }
}
