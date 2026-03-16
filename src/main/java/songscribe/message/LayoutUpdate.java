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

package songscribe.message;

import org.jetbrains.annotations.Nullable;

public class LayoutUpdate extends Message {

    @Nullable private final Double topPadding;
    @Nullable private final Boolean topPaddingSetByUser;
    @Nullable private final Double rowHeightAdjustment;
    @Nullable private final Double lineWidth;
    @Nullable private final Double attributionStartY;

    public LayoutUpdate(
        @Nullable Double topPadding,
        @Nullable Boolean topPaddingSetByUser,
        @Nullable Double rowHeightAdjustment,
        @Nullable Double lineWidth,
        @Nullable Double attributionStartY
    ) {
        this.topPadding = topPadding;
        this.topPaddingSetByUser = topPaddingSetByUser;
        this.rowHeightAdjustment = rowHeightAdjustment;
        this.lineWidth = lineWidth;
        this.attributionStartY = attributionStartY;
    }

    @Nullable
    public Double getTopPadding() {
        return topPadding;
    }

    @Nullable
    public Boolean getTopPaddingSetByUser() {
        return topPaddingSetByUser;
    }

    @Nullable
    public Double getRowHeightAdjustment() {
        return rowHeightAdjustment;
    }

    @Nullable
    public Double getLineWidth() {
        return lineWidth;
    }

    @Nullable
    public Double getAttributionStartY() {
        return attributionStartY;
    }
}
