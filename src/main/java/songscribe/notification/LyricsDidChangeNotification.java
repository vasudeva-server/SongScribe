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

import org.jspecify.annotations.Nullable;

public class LyricsDidChangeNotification extends Message {

    @Nullable
    private final String lyrics;
    @Nullable
    private final String underLyrics;
    @Nullable
    private final String translatedLyrics;
    @Nullable
    private final String banglaLyrics;

    public LyricsDidChangeNotification(
        @Nullable String lyrics,
        @Nullable String underLyrics,
        @Nullable String translatedLyrics,
        @Nullable String banglaLyrics
    ) {
        this.lyrics = lyrics;
        this.underLyrics = underLyrics;
        this.translatedLyrics = translatedLyrics;
        this.banglaLyrics = banglaLyrics;
    }

    @Nullable
    public String getLyrics() {
        return lyrics;
    }

    @Nullable
    public String getUnderLyrics() {
        return underLyrics;
    }

    @Nullable
    public String getTranslatedLyrics() {
        return translatedLyrics;
    }

    @Nullable
    public String getBanglaLyrics() {
        return banglaLyrics;
    }
}
