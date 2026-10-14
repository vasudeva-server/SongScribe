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

import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Layout information for a text section (title, attribution, lyrics block, etc.).
 */
public record SectionLayout(ElementBoundsSs bounds, List<String> lines, @Nullable Font font, int baselineY) {

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

    public SectionLayout(
        ElementBoundsSs bounds,
        String text,
        @Nullable Font font,
        int baselineY
    ) {
        this(bounds, List.of(text), font, baselineY);
    }

    public static SectionLayout empty() {
        var emptyBounds = ElementBoundsSs.contentOnly(
            new Rectangle2D.Double(0, 0, 0, 0)
        );
        return new SectionLayout(emptyBounds, List.of(), null, 0);
    }

    @Override
    public ElementBoundsSs bounds() {
        return bounds;
    }

    @Override
    public List<String> lines() {
        return lines;
    }

    public String getText() {
        return lines.isEmpty() ? "" : lines.getFirst();
    }

    /** Null if the section is empty. */
    @Override
    public @Nullable Font font() {
        return font;
    }

    @Override
    public int baselineY() {
        return baselineY;
    }

    public boolean hasContent() {
        return !lines.isEmpty() && !lines.getFirst().isEmpty();
    }

    public double getHeight() {
        return bounds.getContentBounds().getHeight();
    }

    @Override
    public String toString() {
        return "SectionLayout{" +
            "bounds=" + bounds +
            ", lines=" + lines.size() +
            ", hasContent=" + hasContent() +
            '}';
    }
}
