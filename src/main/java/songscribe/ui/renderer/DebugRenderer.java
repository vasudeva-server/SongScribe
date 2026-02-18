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

import java.awt.*;
import java.awt.geom.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.ui.layout.AttachmentLayout;
import songscribe.ui.layout.ElementBounds;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.layout.LineLayout;
import songscribe.ui.layout.NoteBounds;
import songscribe.ui.layout.NoteLayout;
import songscribe.ui.layout.RangeLayout;
import songscribe.ui.layout.SectionLayout;
import songscribe.ui.menu.DebugState;

/**
 * Renders debug visualization for layout elements.
 * <p>
 * DebugRenderer provides visual debugging of the layout system by drawing:
 * <ul>
 *   <li><b>Layout boxes</b>: Section bounds (title, attribution, score, etc.)</li>
 *   <li><b>Bounding boxes</b>: Element bounds (notes, attachments, ranges)</li>
 *   <li><b>Margins</b>: Space between sections and elements</li>
 * </ul>
 * <p>
 * The box model visualization uses different colors for each layer:
 * <ul>
 *   <li><b>Content bounds</b>: Solid stroke showing actual drawn pixels</li>
 *   <li><b>Padding bounds</b>: Semi-transparent fill showing hit testing area</li>
 *   <li><b>Margin bounds</b>: Light fill or dashed stroke showing layout spacing</li>
 * </ul>
 * <p>
 * Each element type has a distinct color for easy identification:
 * <ul>
 *   <li>Sections: Blue</li>
 *   <li>Lines: Magenta</li>
 *   <li>Notes: Red</li>
 *   <li>Attachments: Green (tempo), Cyan (annotations), Orange (trills)</li>
 *   <li>Range elements: Purple</li>
 * </ul>
 */
public final class DebugRenderer {

    // -------------------------------------------------------------------------
    // Inspector colors (25% alpha)
    // -------------------------------------------------------------------------

    /** Blue fill for content area (#00a2ff at 75%) */
    private static final Color INSPECTOR_CONTENT_COLOR = new Color(0, 162, 255, 182);

    /** Green fill for padding area (#7ddb53 at 75%) */
    private static final Color INSPECTOR_PADDING_COLOR = new Color(125, 219, 83, 182);

    /** Orange fill for margin area (#f99e41 at 75%) */
    private static final Color INSPECTOR_MARGIN_COLOR = new Color(249, 158, 65, 182);

    // -------------------------------------------------------------------------
    // Legacy color scheme for element types (used by legacy methods)
    // -------------------------------------------------------------------------

    /** Section bounds color (blue) */
    private static final Color SECTION_COLOR = new Color(0, 0, 255, 180);

    /** Line bounds color (magenta) */
    private static final Color LINE_COLOR = new Color(255, 0, 255, 180);

    /** Note bounds color (red) */
    private static final Color NOTE_COLOR = new Color(255, 0, 0, 180);

    /** Tempo attachment color (green) */
    private static final Color TEMPO_COLOR = new Color(0, 200, 0, 180);

    /** Annotation attachment color (cyan) */
    private static final Color ANNOTATION_COLOR = new Color(0, 128, 255, 180);

    /** Trill/fermata attachment color (orange) */
    private static final Color TRILL_COLOR = new Color(255, 128, 0, 180);

    /** Range element color (purple) */
    private static final Color RANGE_COLOR = new Color(128, 0, 255, 180);

    /** Margin fill color (light gray) */
    private static final Color MARGIN_FILL_COLOR = new Color(192, 192, 192, 80);

    /** Margin stroke color (orange) */
    private static final Color MARGIN_STROKE_COLOR = new Color(255, 165, 0, 128);

    // -------------------------------------------------------------------------
    // Box model colors (applied to any element type)
    // -------------------------------------------------------------------------

    /** Alpha for content bounds stroke */
    private static final int CONTENT_ALPHA = 200;

    /** Alpha for padding bounds fill */
    private static final int PADDING_ALPHA = 40;

    /** Alpha for margin region fill */
    private static final int MARGIN_ALPHA = 25;

    // -------------------------------------------------------------------------
    // Stroke styles
    // -------------------------------------------------------------------------

    /** Solid stroke for content bounds */
    private static final Stroke CONTENT_STROKE = new BasicStroke(1.0f);

    /** Dashed stroke for margin bounds */
    private static final Stroke MARGIN_STROKE = new BasicStroke(
        1.0f,
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER,
        10.0f,
        new float[]{4.0f, 4.0f},
        0.0f
    );

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Draws inspector visualization for the hovered element.
     * <p>
     * When the inspector is enabled and an element is hovered, draws a
     * Chrome DevTools-style box model visualization showing content,
     * padding, and margin bounds with distinct colors.
     *
     * @param g2           Graphics context
     * @param layoutResult Layout result (may be null)
     * @param context      Render context
     */
    public void drawDebugVisualization(
        @NotNull Graphics2D g2,
        @Nullable LayoutResult layoutResult,
        @NotNull RenderContext context
    ) {
        if (!DebugState.isInspectorEnabled()) {
            return;
        }

        var hoveredElement = DebugState.getHoveredElement();

        if (hoveredElement == null) {
            return;
        }

        // Save graphics state
        var originalStroke = g2.getStroke();
        var originalColor = g2.getColor();

        drawElementBoxModel(g2, hoveredElement.getBounds());

        // Restore graphics state
        g2.setStroke(originalStroke);
        g2.setColor(originalColor);
    }

    /**
     * Draws box model visualization for an element.
     * <p>
     * Renders three filled regions from outermost to innermost:
     * <ul>
     *   <li>Margin region: Orange (area between margin and padding bounds)</li>
     *   <li>Padding region: Green (area between padding and content bounds)</li>
     *   <li>Content bounds: Blue</li>
     * </ul>
     *
     * @param g2     Graphics context
     * @param bounds Element bounds to visualize
     */
    private void drawElementBoxModel(@NotNull Graphics2D g2, @NotNull ElementBounds bounds) {
        var marginBounds = bounds.getMarginBounds();
        var paddingBounds = bounds.getPaddingBounds();
        var contentBounds = bounds.getContentBounds();

        // Draw margin region (margin bounds minus padding bounds)
        var marginArea = new Area(marginBounds);
        marginArea.subtract(new Area(paddingBounds));
        g2.setColor(INSPECTOR_MARGIN_COLOR);
        g2.fill(marginArea);

        // Draw padding region (padding bounds minus content bounds)
        var paddingArea = new Area(paddingBounds);
        paddingArea.subtract(new Area(contentBounds));
        g2.setColor(INSPECTOR_PADDING_COLOR);
        g2.fill(paddingArea);

        // Draw content bounds
        g2.setColor(INSPECTOR_CONTENT_COLOR);
        g2.fill(contentBounds);
    }

    // -------------------------------------------------------------------------
    // Layout boxes (sections)
    // -------------------------------------------------------------------------

    /**
     * Draws section bounds for high-level layout structure.
     */
    private void drawLayoutBoxes(@NotNull Graphics2D g2, @NotNull LayoutResult layout) {
        g2.setStroke(CONTENT_STROKE);

        drawSectionBounds(g2, layout.getTitle(), "title");
        drawSectionBounds(g2, layout.getAttribution(), "attribution");
        drawSectionBounds(g2, layout.getScore(), "score");
        drawSectionBounds(g2, layout.getLyrics(), "lyrics");
        drawSectionBounds(g2, layout.getBanglaLyrics(), "bangla");
        drawSectionBounds(g2, layout.getTranslation(), "translation");
        drawSectionBounds(g2, layout.getFootnotes(), "footnotes");

        // Draw line bounds within score section
        for (var line : layout.getLines()) {
            drawLineBoundsOutline(g2, line);
        }
    }

    /**
     * Draws bounds for a section with label.
     */
    private void drawSectionBounds(
        @NotNull Graphics2D g2,
        @NotNull SectionLayout section,
        @NotNull String label
    ) {
        if (!section.hasContent()) {
            return;
        }

        var bounds = section.getBounds();
        var contentBounds = bounds.getContentBounds();

        if (contentBounds.getWidth() <= 0 || contentBounds.getHeight() <= 0) {
            return;
        }

        // Draw content bounds
        g2.setColor(SECTION_COLOR);
        g2.draw(contentBounds);

        // Draw label in top-left corner
        var font = g2.getFont();
        g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
        g2.drawString(
            label,
            (float) (contentBounds.getX() + 2),
            (float) (contentBounds.getY() + 10)
        );
        g2.setFont(font);
    }

    /**
     * Draws line bounds outline.
     */
    private void drawLineBoundsOutline(@NotNull Graphics2D g2, @NotNull LineLayout line) {
        var bounds = line.getLineBounds();
        var contentBounds = bounds.getContentBounds();

        if (contentBounds.getWidth() <= 0 || contentBounds.getHeight() <= 0) {
            return;
        }

        g2.setColor(LINE_COLOR);
        g2.draw(contentBounds);
    }

    // -------------------------------------------------------------------------
    // Section margins
    // -------------------------------------------------------------------------

    /**
     * Draws margins between consecutive sections.
     */
    private void drawSectionMargins(@NotNull Graphics2D g2, @NotNull LayoutResult layout) {
        // Collect non-empty sections in order
        var sections = new java.util.ArrayList<SectionLayout>();

        if (layout.getTitle().hasContent()) {
            sections.add(layout.getTitle());
        }

        if (layout.getAttribution().hasContent()) {
            sections.add(layout.getAttribution());
        }

        if (layout.getScore().hasContent()) {
            sections.add(layout.getScore());
        }

        if (layout.getLyrics().hasContent()) {
            sections.add(layout.getLyrics());
        }

        if (layout.getBanglaLyrics().hasContent()) {
            sections.add(layout.getBanglaLyrics());
        }

        if (layout.getTranslation().hasContent()) {
            sections.add(layout.getTranslation());
        }

        if (layout.getFootnotes().hasContent()) {
            sections.add(layout.getFootnotes());
        }

        // Draw margins between consecutive sections
        for (int i = 0; i < sections.size() - 1; i++) {
            var current = sections.get(i);
            var next = sections.get(i + 1);

            drawMarginBetween(g2, current.getBounds(), next.getBounds());
        }
    }

    /**
     * Draws the margin region between two element bounds.
     */
    private void drawMarginBetween(
        @NotNull Graphics2D g2,
        @NotNull ElementBounds current,
        @NotNull ElementBounds next
    ) {
        var currentBottom = current.getMarginBottom();
        var nextTop = next.getMarginTop();
        var marginHeight = nextTop - currentBottom;

        if (marginHeight <= 0) {
            return;
        }

        // Use the wider of the two elements for margin width
        var marginWidth = Math.max(
            current.getMarginBounds().getWidth(),
            next.getMarginBounds().getWidth()
        );

        var marginRect = new Rectangle2D.Double(
            0,
            currentBottom,
            marginWidth,
            marginHeight
        );

        // Fill with semi-transparent gray
        g2.setColor(MARGIN_FILL_COLOR);
        g2.fill(marginRect);

        // Stroke with orange
        g2.setColor(MARGIN_STROKE_COLOR);
        g2.setStroke(MARGIN_STROKE);
        g2.draw(marginRect);
        g2.setStroke(CONTENT_STROKE);
    }

    // -------------------------------------------------------------------------
    // Bounding boxes (elements)
    // -------------------------------------------------------------------------

    /**
     * Draws bounding boxes for all elements within the layout.
     */
    private void drawBoundingBoxes(@NotNull Graphics2D g2, @NotNull LayoutResult layout) {
        for (var line : layout.getLines()) {
            drawLineBoundingBoxes(g2, line);
        }
    }

    /**
     * Draws all bounding boxes within a line.
     */
    private void drawLineBoundingBoxes(@NotNull Graphics2D g2, @NotNull LineLayout line) {
        // Draw note bounds
        for (var note : line.getNotes()) {
            drawNoteBounds(g2, note);
        }

        // Draw attachment bounds
        for (var attachment : line.getAttachments()) {
            drawAttachmentBounds(g2, attachment);
        }

        // Draw range element bounds
        for (var range : line.getRangeElements()) {
            drawRangeBounds(g2, range);
        }
    }

    /**
     * Draws bounding boxes for a note with hierarchical bounds visualization.
     */
    private void drawNoteBounds(@NotNull Graphics2D g2, @NotNull NoteLayout note) {
        var noteBounds = note.getBounds();
        var elementBounds = note.getElementBounds();

        // Draw the hierarchical note bounds
        drawHierarchicalNoteBounds(g2, noteBounds);

        // Draw element bounds (padding/margin for hit testing)
        drawElementBoundsLayers(g2, elementBounds, NOTE_COLOR);
    }

    /**
     * Draws the hierarchical note bounds (head, stem, articulations).
     */
    private void drawHierarchicalNoteBounds(@NotNull Graphics2D g2, @NotNull NoteBounds bounds) {
        g2.setStroke(CONTENT_STROKE);

        // Note head bounds (innermost) - darker red
        g2.setColor(new Color(255, 0, 0, 255));
        g2.draw(bounds.getNoteHeadBounds());

        // Note with stem bounds - medium red
        var stemBounds = bounds.getNoteWithStemBounds();

        if (!stemBounds.equals(bounds.getNoteHeadBounds())) {
            g2.setColor(new Color(255, 100, 100, 200));
            g2.draw(stemBounds);
        }

        // Note with articulations bounds (outermost) - light red
        var articulationBounds = bounds.getNoteWithArticulationsBounds();

        if (!articulationBounds.equals(stemBounds)) {
            g2.setColor(new Color(255, 150, 150, 150));
            g2.draw(articulationBounds);
        }
    }

    /**
     * Draws bounding boxes for an attachment.
     */
    private void drawAttachmentBounds(@NotNull Graphics2D g2, @NotNull AttachmentLayout attachment) {
        var color = getAttachmentColor(attachment.getType());
        drawElementBoundsLayers(g2, attachment.getBounds(), color);
    }

    /**
     * Returns the appropriate color for an attachment type.
     */
    private Color getAttachmentColor(@NotNull AttachmentLayout.Type type) {
        return switch (type) {
            case TEMPO, BEAT_CHANGE -> TEMPO_COLOR;
            case ANNOTATION_ABOVE, ANNOTATION_BELOW -> ANNOTATION_COLOR;
            case TRILL, FERMATA -> TRILL_COLOR;
            case DYNAMIC, CRESCENDO, DIMINUENDO -> ANNOTATION_COLOR;
        };
    }

    /**
     * Draws bounding boxes for a range element.
     */
    private void drawRangeBounds(@NotNull Graphics2D g2, @NotNull RangeLayout range) {
        drawElementBoundsLayers(g2, range.getBounds(), RANGE_COLOR);
    }

    // -------------------------------------------------------------------------
    // ElementBounds visualization
    // -------------------------------------------------------------------------

    /**
     * Draws all layers of an ElementBounds with the given base color.
     *
     * @param g2        Graphics context
     * @param bounds    Element bounds to visualize
     * @param baseColor Base color (alpha will be adjusted per layer)
     */
    private void drawElementBoundsLayers(
        @NotNull Graphics2D g2,
        @NotNull ElementBounds bounds,
        @NotNull Color baseColor
    ) {
        var content = bounds.getContentBounds();
        var padding = bounds.getPaddingBounds();
        var margin = bounds.getMarginBounds();

        // Draw margin region (outermost, lightest)
        if (!margin.equals(padding)) {
            var marginColor = new Color(
                baseColor.getRed(),
                baseColor.getGreen(),
                baseColor.getBlue(),
                MARGIN_ALPHA
            );
            g2.setColor(marginColor);
            g2.fill(margin);
        }

        // Draw padding region
        if (!padding.equals(content)) {
            var paddingColor = new Color(
                baseColor.getRed(),
                baseColor.getGreen(),
                baseColor.getBlue(),
                PADDING_ALPHA
            );
            g2.setColor(paddingColor);
            g2.fill(padding);
        }

        // Draw content bounds stroke (innermost, darkest)
        var contentColor = new Color(
            baseColor.getRed(),
            baseColor.getGreen(),
            baseColor.getBlue(),
            CONTENT_ALPHA
        );
        g2.setColor(contentColor);
        g2.setStroke(CONTENT_STROKE);
        g2.draw(content);
    }
}
