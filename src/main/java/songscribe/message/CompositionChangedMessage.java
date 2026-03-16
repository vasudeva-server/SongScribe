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

import java.util.EnumSet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Composition;
import songscribe.music.Line;

public class CompositionChangedMessage extends Message {

    public enum ChangeType {
        CONTENT,
        LYRICS,
        METADATA,
        FONT,
        LAYOUT,
        STRUCTURE,
        FULL
    }

    private final EnumSet<ChangeType> changeTypes;
    private final Composition composition;
    @Nullable private final Line line;

    public CompositionChangedMessage(
        @NotNull ChangeType changeType,
        @NotNull Composition composition
    ) {
        this(changeType, composition, null);
    }

    public CompositionChangedMessage(
        @NotNull ChangeType changeType,
        @NotNull Composition composition,
        @Nullable Line line
    ) {
        this.changeTypes = EnumSet.of(changeType);
        this.composition = composition;
        this.line = line;
    }

    public CompositionChangedMessage(
        @NotNull EnumSet<ChangeType> changeTypes,
        @NotNull Composition composition
    ) {
        this.changeTypes = changeTypes;
        this.composition = composition;
        this.line = null;
    }

    @NotNull
    public EnumSet<ChangeType> getChangeTypes() {
        return changeTypes;
    }

    public boolean hasChangeType(@NotNull ChangeType type) {
        return changeTypes.contains(type);
    }

    @NotNull
    public Composition getComposition() {
        return composition;
    }

    @Nullable
    public Line getLine() {
        return line;
    }

    @Override
    public String toString() {
        return super.toString() + "(changeTypes=" + changeTypes
            + ", line=" + line + ")";
    }
}
