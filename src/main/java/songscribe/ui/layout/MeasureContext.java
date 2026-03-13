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

import org.jetbrains.annotations.NotNull;

import songscribe.music.Composition;

/**
 * Context information for the measure pass of layout.
 * <p>
 * Provides access to graphics context, font metrics, and composition settings
 * needed to calculate element sizes.
 */
public record MeasureContext(
    @NotNull Graphics2D graphics,
    @NotNull Composition composition
) {

    /**
     * Returns the font metrics for measuring text.
     */
    public @NotNull java.awt.FontMetrics getFontMetrics() {
        return graphics.getFontMetrics();
    }
}
