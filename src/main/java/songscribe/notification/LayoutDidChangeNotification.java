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

public class LayoutDidChangeNotification extends Message {

    @Nullable
    private final Double topPaddingSs;
    @Nullable
    private final Boolean topPaddingSetByUser;
    @Nullable
    private final Double rowHeightAdjustmentSs;
    @Nullable
    private final Double lineWidthSs;
    @Nullable
    private final Double attributionStartYSs;

    public LayoutDidChangeNotification(
        @Nullable Double topPadding,
        @Nullable Boolean topPaddingSetByUser,
        @Nullable Double rowHeightAdjustment,
        @Nullable Double lineWidth,
        @Nullable Double attributionStartY
    ) {
        this.topPaddingSs = topPadding;
        this.topPaddingSetByUser = topPaddingSetByUser;
        this.rowHeightAdjustmentSs = rowHeightAdjustment;
        this.lineWidthSs = lineWidth;
        this.attributionStartYSs = attributionStartY;
    }

    @Nullable
    public Double getTopPaddingSs() {
        return topPaddingSs;
    }

    @Nullable
    public Boolean getTopPaddingSetByUser() {
        return topPaddingSetByUser;
    }

    @Nullable
    public Double getRowHeightAdjustmentSs() {
        return rowHeightAdjustmentSs;
    }

    @Nullable
    public Double getLineWidthSs() {
        return lineWidthSs;
    }

    @Nullable
    public Double getAttributionStartYSs() {
        return attributionStartYSs;
    }
}
