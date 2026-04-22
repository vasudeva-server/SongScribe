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

import module java.desktop;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Layout information for a text section (title, attribution, lyrics block, etc.).
 * <p>
 * Sections are block-level elements that stack vertically with margin collapsing.
 * Each section has bounds and text content to render.
 */
public final class SectionLayout {

    private final ElementBoundsSs bounds;
    private final List<String> lines;
    private final @Nullable Font font;
    private final int baselineY;

    /**
     * Creates section layout for multi-line text.
     *
     * @param bounds    Element bounds with content/padding/margin
     * @param lines     Text lines to render
     * @param font      Font to use for rendering (may be null if section is empty)
     * @param baselineY Y coordinate of first text baseline
     */
    public SectionLayout(
        ElementBoundsSs bounds,
        List<String> lines,
        @Nullable Font font,
        int baselineY
    ) {
        this.bounds = bounds;
        this.lines = List.copyOf(lines);
        this.font = font;
        this.baselineY = baselineY;
    }

    /**
     * Creates section layout for single-line text.
     */
    public SectionLayout(
        ElementBoundsSs bounds,
        String text,
        @Nullable Font font,
        int baselineY
    ) {
        this(bounds, List.of(text), font, baselineY);
    }

    /**
     * Creates an empty section layout (zero bounds, no content).
     */
    public static SectionLayout empty() {
        var emptyBounds = ElementBoundsSs.contentOnly(
            new java.awt.geom.Rectangle2D.Double(0, 0, 0, 0)
        );
        return new SectionLayout(emptyBounds, List.of(), null, 0);
    }

    /**
     * Returns the element bounds.
     */
    public ElementBoundsSs getBounds() {
        return bounds;
    }

    /**
     * Returns the text lines.
     */
    public List<String> getLines() {
        return lines;
    }

    /**
     * Returns the first text line, or empty string if none.
     */
    public String getText() {
        return lines.isEmpty() ? "" : lines.get(0);
    }

    /**
     * Returns the font for rendering, or null if section is empty.
     */
    public @Nullable Font getFont() {
        return font;
    }

    /**
     * Returns the Y coordinate of the first text baseline.
     */
    public int getBaselineY() {
        return baselineY;
    }

    /**
     * Returns whether this section has any content.
     */
    public boolean hasContent() {
        return !lines.isEmpty() && !lines.get(0).isEmpty();
    }

    /**
     * Returns the height of the section content bounds.
     */
    public double getHeight() {
        return bounds.getContentBounds().getHeight();
    }

    @Override
    public String toString() {
        return "SectionLayout{" +
            "bounds=" + bounds +
            ", lines=" + lines.size() +
            ", hasContent=" + hasContent() +
            "}";
    }
}
