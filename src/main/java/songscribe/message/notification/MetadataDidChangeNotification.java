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

package songscribe.message.notification;

import songscribe.dom.Song;
import songscribe.message.Message;

import org.jspecify.annotations.Nullable;

public class MetadataDidChangeNotification extends Message {

    @Nullable
    private final String title;
    @Nullable
    private final String place;
    @Nullable
    private final String year;
    @Nullable
    private final String number;
    @Nullable
    private final Integer month;
    @Nullable
    private final Integer day;
    @Nullable
    private final Boolean unofficialTranslation;
    @Nullable
    private final String composer;
    @Nullable
    private final String lyricist;
    private final Song.@Nullable LyricsSource lyricsSource;
    @Nullable
    private final Boolean arrangement;

    public MetadataDidChangeNotification(
        @Nullable String title,
        @Nullable String place,
        @Nullable String year,
        @Nullable String number,
        @Nullable Integer month,
        @Nullable Integer day,
        @Nullable Boolean unofficialTranslation,
        @Nullable String composer,
        @Nullable String lyricist,
        Song.@Nullable LyricsSource lyricsSource,
        @Nullable Boolean arrangement
    ) {
        this.title = title;
        this.place = place;
        this.year = year;
        this.number = number;
        this.month = month;
        this.day = day;
        this.unofficialTranslation = unofficialTranslation;
        this.composer = composer;
        this.lyricist = lyricist;
        this.lyricsSource = lyricsSource;
        this.arrangement = arrangement;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    @Nullable
    public String getPlace() {
        return place;
    }

    @Nullable
    public String getYear() {
        return year;
    }

    @Nullable
    public String getNumber() {
        return number;
    }

    @Nullable
    public Integer getMonth() {
        return month;
    }

    @Nullable
    public Integer getDay() {
        return day;
    }

    @Nullable
    public Boolean getUnofficialTranslation() {
        return unofficialTranslation;
    }

    @Nullable
    public String getComposer() {
        return composer;
    }

    @Nullable
    public String getLyricist() {
        return lyricist;
    }

    public Song.@Nullable LyricsSource getLyricsSource() {
        return lyricsSource;
    }

    @Nullable
    public Boolean getArrangement() {
        return arrangement;
    }
}
