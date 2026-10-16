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
package songscribe.ui.component;

import java.util.function.BiFunction;
import javax.swing.Icon;

import songscribe.util.GraphicUtils;

/**
 * What the Messages panel lists, named by the most urgent kind of row it holds: a lint finding
 * outranks a change, and a change outranks an empty list.
 */
public enum ReportStatus {

    EMPTY("messages-empty.svg", GraphicUtils::getScaledThemedSVGIcon),

    CHANGES("messages-change-report.svg", GraphicUtils::getScaledThemedSVGIcon),

    // The SVG carries its own color, the red of the lint badge on the panel's rows.
    FINDINGS("messages-lint-error.svg", GraphicUtils::getScaledSVGIcon);

    private final String iconFile;
    private final BiFunction<String, Integer, ? extends Icon> iconLoader;

    ReportStatus(String iconFile, BiFunction<String, Integer, ? extends Icon> iconLoader) {
        this.iconFile = iconFile;
        this.iconLoader = iconLoader;
    }

    public Icon icon(int size) {
        return iconLoader.apply(iconFile, size);
    }
}
