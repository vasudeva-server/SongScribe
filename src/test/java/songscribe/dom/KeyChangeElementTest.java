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

package songscribe.dom;

import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.keyChange;

/**
 * {@link KeyChangeElement}'s promise that the key it establishes survives every copy route.
 *
 * <p>Both routes are asserted, because they are the same promise reached two ways:
 * {@link KeyChangeElement#clone()} builds a new element, and {@link StaffElement#copyStateFrom}
 * restores one in place. The second is the route undo replay of an {@code ElementModification}
 * takes, so a key that does not travel it is a key edit that cannot be undone.
 */
class KeyChangeElementTest extends UnitTest {

    /** The key the copied element establishes. */
    private static final Key SOURCE_KEY = Key.TWO_SHARPS;

    /**
     * The key the copy target establishes before the copy. Differs from {@link #SOURCE_KEY} so
     * that an unchanged target fails the assertion rather than passing by coincidence.
     */
    private static final Key TARGET_KEY = Key.THREE_FLATS;

    /** One way of copying a key signature, named for the assertion failure message. */
    private record CopyRoute(String description, UnaryOperator<KeyChangeElement> copy) {}

    static Stream<CopyRoute> copyRoutes() {
        return Stream.of(
            new CopyRoute("clone", KeyChangeElement::clone),
            new CopyRoute("copyStateFrom onto a live element", source -> {
                var target = keyChange(TARGET_KEY);
                target.copyStateFrom(source);
                return target;
            }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("copyRoutes")
    void testCopyCarriesTheKey(CopyRoute route) {
        var copy = route.copy().apply(keyChange(SOURCE_KEY));

        assertThat(copy.getKey()).isEqualTo(SOURCE_KEY);
    }

}
