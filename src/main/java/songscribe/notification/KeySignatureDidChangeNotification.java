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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.KeyType;

public class KeySignatureDidChangeNotification extends Message {

    @Nullable
    private final Integer lineIndex;
    @NotNull
    private final KeyType keyType;
    private final int accidentalCount;

    public KeySignatureDidChangeNotification(
        @Nullable Integer lineIndex,
        @NotNull KeyType keyType,
        int accidentalCount
    ) {
        this.lineIndex = lineIndex;
        this.keyType = keyType;
        this.accidentalCount = accidentalCount;
    }

    @Nullable
    public Integer getLineIndex() {
        return lineIndex;
    }

    @NotNull
    public KeyType getKeyType() {
        return keyType;
    }

    public int getAccidentalCount() {
        return accidentalCount;
    }
}
