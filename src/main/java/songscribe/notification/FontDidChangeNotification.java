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

package songscribe.notification;

import songscribe.message.Message;

import java.awt.*;

import org.jetbrains.annotations.Nullable;

public class FontDidChangeNotification extends Message {

    @Nullable
    private final Font titleFont;
    @Nullable
    private final Font lyricsFont;
    @Nullable
    private final Font attributionFont;
    @Nullable
    private final Font annotationFont;

    public FontDidChangeNotification(
        @Nullable Font titleFont,
        @Nullable Font lyricsFont,
        @Nullable Font attributionFont,
        @Nullable Font annotationFont
    ) {
        this.titleFont = titleFont;
        this.lyricsFont = lyricsFont;
        this.attributionFont = attributionFont;
        this.annotationFont = annotationFont;
    }

    @Nullable
    public Font getTitleFont() {
        return titleFont;
    }

    @Nullable
    public Font getLyricsFont() {
        return lyricsFont;
    }

    @Nullable
    public Font getAttributionFont() {
        return attributionFont;
    }

    @Nullable
    public Font getAnnotationFont() {
        return annotationFont;
    }
}
