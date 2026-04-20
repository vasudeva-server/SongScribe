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


import songscribe.music.Span;
import songscribe.music.SpanSet;
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
    public static LyricsRenderer getInstance() {
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
        Graphics2D g2,
        Line line,
        ElementRenderContext ctx,
        boolean isLastLine
    ) {
        initializeMetrics(g2, ctx);

        try (var ignored = GraphicsState.save(g2, FONT)) {
            var composition = ctx.getComposition();
            var font = composition.getLyricsFont();
            g2.setFont(font);

            // Track which syllables have been drawn (for relation handling)
            var drawnIndex = 0;

            for (var noteIndex = 0; noteIndex < line.effectiveElementCount(); noteIndex++) {
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
        Line line,
        ElementRenderContext ctx
    ) {
        var bounds = ctx.getLayoutResult().getBounds(line);

        if (bounds == null) {
            throw new IllegalStateException("No bounds found for Line (lyrics)");
        }

        return (int) bounds.getTop();
    }

    /**
     * Initializes font metrics on first call.
     */
    private void initializeMetrics(Graphics2D g2, ElementRenderContext ctx) {
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
        Graphics2D g2,
        Line line,
        ElementRenderContext ctx,
        boolean isLastLine,
        int noteIndex,
        StaffElement note,
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
            var noteX = ctx.getLayoutResult().getElementXSs(note);
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
        Graphics2D g2,
        Line line,
        ElementRenderContext ctx,
        int noteIndex,
        StaffElement note,
        int drawnIndex,
        int syllableWidth,
        float lyricsY,
        float dashY
    ) {
        var composition = ctx.getComposition();
        var metrics = g2.getFontMetrics();

        // Determine the relation type
        var noteRelation = note.properties.syllableRelation;
        var relation = (noteRelation != null && noteRelation != StaffElement.SyllableRelation.NO)
            ? noteRelation
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
        Line line,
        int noteIndex,
        StaffElement.SyllableRelation relation
    ) {
        int endIndex;

        if (relation == StaffElement.SyllableRelation.ONE_DASH) {
            // Find next note with actual syllable (not underscore or empty)
            endIndex = noteIndex + 1;

            while (endIndex < line.effectiveElementCount()) {
                var nextSyllable = line.getElement(endIndex).properties.syllable;

                if (nextSyllable == null) {
                    endIndex++;
                    continue;
                }

                if (!nextSyllable.equals(Constants.UNDERSCORE) && !nextSyllable.isEmpty()) {
                    break;
                }

                endIndex++;
            }
        } else {
            // For extender, continue while same relation or empty syllable
            endIndex = noteIndex;

            while (endIndex < line.effectiveElementCount()) {
                var nextElement = line.getElement(endIndex);

                var nextSyllable = nextElement.properties.syllable;

                if (nextElement.properties.syllableRelation != relation &&
                    (nextSyllable == null || !nextSyllable.isEmpty())) {
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
        Line line,
        int noteIndex,
        StaffElement note,
        int syllableWidth
    ) {
        // Begin-of-line extender starts before the note
        if (noteIndex == 0 && line.beginRelation == StaffElement.SyllableRelation.EXTENDER) {
            return note.getXOffsetPx() - 10;
        }

        // Normal case: start after the syllable
        return (int) (note.getXOffsetPx() +
            note.getContentCenterX() +
            (syllableWidth / 2) +
            note.getSyllableMovement() +
            2);
    }

    /**
     * Calculates the end X position for a relation line.
     */
    private int calculateRelationEndXPx(
        Graphics2D g2,
        Line line,
        songscribe.music.Composition composition,
        int noteIndex,
        int endIndex,
        StaffElement.SyllableRelation relation,
        int startX
    ) {
        // At end of line
        if (endIndex == line.effectiveElementCount()) {
            return (relation == StaffElement.SyllableRelation.ONE_DASH)
                ? startX + (int) (HYPHEN_WIDTH_PX * 2f)
                : composition.getLineWidthPx();
        }

        var endNote = line.getElement(endIndex);

        if (relation == StaffElement.SyllableRelation.EXTENDER) {
            return endNote.getXOffsetPx() + 12;
        }

        var endNoteSyllable = endNote.properties.syllable;

        if (relation == StaffElement.SyllableRelation.ONE_DASH &&
            (endNoteSyllable == null || endNoteSyllable.isEmpty())) {
            return startX + (int) (HYPHEN_WIDTH_PX * 2f);
        }

        // End before the next syllable
        var nextSyllable = endNote.properties.syllable;
        var nextSyllableWidth = g2.getFontMetrics().stringWidth(nextSyllable);

        return (int) ((endNote.getXOffsetPx() + endNote.getContentCenterX()) -
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
        Graphics2D g2,
        Line line,
        int startIndex,
        int endIndex,
        StaffElement.SyllableRelation relation,
        int startX,
        int endX,
        float lyricsY,
        float dashY,
        StaffElement note
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
        Graphics2D g2,
        Line line,
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
        Graphics2D g2,
        StaffElement note,
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
                : note.getXOffsetPx() + note.getSyllableRelationMovement();

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
        Graphics2D g2,
        int x1,
        int y1,
        int x2,
        int y2,
        Line line,
        int startIndex,
        int endIndex
    ) {
        var end = Math.min(line.effectiveElementCount(), endIndex);

        // Find notes with empty syllables
        var emptySyllables = IntStream.range(startIndex, end)
            .filter(i -> {
                var s = line.getElement(i).properties.syllable;
                return s == null || s.isEmpty();
            })
            .boxed()
            .collect(Collectors.toCollection(ArrayList::new));

        if (emptySyllables.isEmpty()) {
            g2.drawLine(x1, y1, x2, y2);
            return;
        }

        // Create spans excluding empty syllables
        var spanSet = new SpanSet<Span>();
        spanSet.addSpan(startIndex, end);

        for (var i : emptySyllables) {
            spanSet.removeSpan(i, i + 1);
        }

        // Draw line segments for each span
        for (var iter = spanSet.listIterator(); iter.hasNext(); ) {
            var span = iter.next();

            var drawX1 = (span.getStart() == startIndex)
                ? x1
                : line.getElement(span.getStart()).getXOffsetPx();

            var drawX2 = (span.getEnd() == end)
                ? x2
                : line.getElement(span.getEnd() - 1).getXOffsetPx() + 12;

            g2.drawLine(drawX1, y1, drawX2, y2);
        }
    }
}
