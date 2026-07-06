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

package songscribe.message.notification;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.message.Message;

public class PreviewElementDidChangeNotification extends Message {

    @Nullable
    private final StaffElement previewElement;

    @Nullable
    private final Line line;

    private final int index;

    public PreviewElementDidChangeNotification(@Nullable StaffElement previewElement) {
        this(previewElement, null, 0);
    }

    /**
     * @param line  The line the preview element would be inserted into, or null if not yet known
     * @param index The insertion index within {@code line}; meaningless when {@code line} is null
     */
    public PreviewElementDidChangeNotification(@Nullable StaffElement previewElement, @Nullable Line line, int index) {
        this.previewElement = previewElement;
        this.line = line;
        this.index = index;
    }

    @Nullable
    public StaffElement getPreviewElement() {
        return previewElement;
    }

    @Nullable
    public Line getLine() {
        return line;
    }

    public int getIndex() {
        return index;
    }
}
