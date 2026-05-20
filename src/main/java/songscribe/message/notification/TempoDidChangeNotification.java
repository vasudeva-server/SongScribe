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

import songscribe.message.Message;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Duration;

public class TempoDidChangeNotification extends Message {

    private final @Nullable Duration tempoType;
    @Nullable
    private final Integer visibleTempo;
    @Nullable
    private final String tempoDescription;
    @Nullable
    private final Boolean showTempo;

    public TempoDidChangeNotification(
        @Nullable Duration tempoType,
        @Nullable Integer visibleTempo,
        @Nullable String tempoDescription,
        @Nullable Boolean showTempo
    ) {
        this.tempoType = tempoType;
        this.visibleTempo = visibleTempo;
        this.tempoDescription = tempoDescription;
        this.showTempo = showTempo;
    }

    public @Nullable Duration getTempoType() {
        return tempoType;
    }

    @Nullable
    public Integer getVisibleTempo() {
        return visibleTempo;
    }

    @Nullable
    public String getTempoDescription() {
        return tempoDescription;
    }

    @Nullable
    public Boolean getShowTempo() {
        return showTempo;
    }
}
