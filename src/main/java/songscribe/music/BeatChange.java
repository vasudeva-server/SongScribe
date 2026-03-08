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
package songscribe.music;

public enum BeatChange {
    QUAVER_EQUALS_QUAVER(
        ElementType.QUAVER.newInstance(),
        ElementType.QUAVER.newInstance(),
        1f
    ),
    DOTTED_CROCHET_EQUALS_MINIM(
        createDottedVersion(ElementType.CROTCHET.newInstance()),
        ElementType.MINIM.newInstance(),
        3f / 4f
    ),
    MINIM_EQUALS_DOTTED_CROCHET(
        ElementType.MINIM.newInstance(),
        createDottedVersion(ElementType.CROTCHET.newInstance()),
        4f / 3f
    ),
    CROTCHET_EQUALS_DOTTED_CROCHET(
        ElementType.CROTCHET.newInstance(),
        createDottedVersion(ElementType.CROTCHET.newInstance()),
        2f / 3f
    ),
    DOTTED_CROCHET_EQUALS_CROCHET(
        createDottedVersion(ElementType.CROTCHET.newInstance()),
        ElementType.CROTCHET.newInstance(),
        3f / 2f
    );

    private final StaffElement firstElement;
    private final StaffElement secondElement;
    private final float tempoChange;

    BeatChange(StaffElement firstElement, StaffElement secondElement, float tempoChange) {
        this.firstElement = firstElement;
        this.secondElement = secondElement;
        this.tempoChange = tempoChange;
    }

    private static StaffElement createDottedVersion(StaffElement element) {
        element.setDotCount(1);
        element.setStaffPosition(1);
        return element;
    }

    public StaffElement getFirstElement() {
        return firstElement;
    }

    public StaffElement getSecondElement() {
        return secondElement;
    }

    public float getTempoChange() {
        return tempoChange;
    }
}
