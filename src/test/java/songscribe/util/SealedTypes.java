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

package songscribe.util;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The leaves of a sealed hierarchy, for a test that must cover every one. */
public final class SealedTypes {

    private SealedTypes() {
    }

    /**
     * @return the classes {@code sealedRoot} bottoms out in, through every sealed level, or
     *     {@code sealedRoot} alone when it is not sealed
     */
    public static Set<Class<?>> leavesOf(Class<?> sealedRoot) {
        return leaves(sealedRoot).collect(Collectors.toSet());
    }

    private static Stream<Class<?>> leaves(Class<?> type) {
        var permitted = type.getPermittedSubclasses();

        return permitted == null ? Stream.of(type) : Stream.of(permitted).flatMap(SealedTypes::leaves);
    }
}
