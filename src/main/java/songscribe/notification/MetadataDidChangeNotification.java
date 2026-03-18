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

import org.jetbrains.annotations.Nullable;

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
    private final String attribution;
    @Nullable
    private final Integer month;
    @Nullable
    private final Integer day;
    @Nullable
    private final Boolean unofficialTranslation;

    public MetadataDidChangeNotification(
        @Nullable String title,
        @Nullable String place,
        @Nullable String year,
        @Nullable String number,
        @Nullable String attribution,
        @Nullable Integer month,
        @Nullable Integer day,
        @Nullable Boolean unofficialTranslation
    ) {
        this.title = title;
        this.place = place;
        this.year = year;
        this.number = number;
        this.attribution = attribution;
        this.month = month;
        this.day = day;
        this.unofficialTranslation = unofficialTranslation;
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
    public String getAttribution() {
        return attribution;
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
}
