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
package songscribe.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.*;

class SpanInvalidationTest extends UnitTest {

    // -----------------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @FunctionalInterface
    interface SpanFactory {

        Span create(StaffElement anchor, StaffElement end);
    }

    static Stream<Arguments> spanFactories() {
        return Stream.of(
            Arguments.of("Tie",        (SpanFactory) Tie::new),
            Arguments.of("Trill",      (SpanFactory) Trill::new),
            Arguments.of("Tuplet",     (SpanFactory) (a, e) -> Tuplet.withUnresolvedRatio(a, e, 3)),
            Arguments.of("Crescendo",  (SpanFactory) Crescendo::new),
            Arguments.of("Diminuendo", (SpanFactory) Diminuendo::new),
            Arguments.of("Ending",     (SpanFactory) Ending::new)
        );
    }

    // -----------------------------------------------------------------------
    // isInvalidatedBy — all concrete subtypes
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "{0}: anchor deleted → invalidated")
    @MethodSource("spanFactories")
    void testAnchorDeletedInvalidates(String name, SpanFactory factory) {
        var anchor = new StaffElement(ElementType.QUAVER);
        var end = new StaffElement(ElementType.QUAVER);
        var element = factory.create(anchor, end);

        assertThat(element.isInvalidatedBy(List.of(anchor))).isTrue();
    }

    @ParameterizedTest(name = "{0}: end deleted → invalidated")
    @MethodSource("spanFactories")
    void testEndDeletedInvalidates(String name, SpanFactory factory) {
        var anchor = new StaffElement(ElementType.QUAVER);
        var end = new StaffElement(ElementType.QUAVER);
        var element = factory.create(anchor, end);

        assertThat(element.isInvalidatedBy(List.of(end))).isTrue();
    }

    @ParameterizedTest(name = "{0}: both endpoints deleted → invalidated")
    @MethodSource("spanFactories")
    void testBothDeletedInvalidates(String name, SpanFactory factory) {
        var anchor = new StaffElement(ElementType.QUAVER);
        var end = new StaffElement(ElementType.QUAVER);
        var element = factory.create(anchor, end);

        assertThat(element.isInvalidatedBy(List.of(anchor, end))).isTrue();
    }

    @ParameterizedTest(name = "{0}: only middle elements deleted → not invalidated")
    @MethodSource("spanFactories")
    void testMiddleDeletedNotInvalidates(String name, SpanFactory factory) {
        var anchor = new StaffElement(ElementType.QUAVER);
        var middle1 = new StaffElement(ElementType.QUAVER);
        var middle2 = new StaffElement(ElementType.QUAVER);
        var end = new StaffElement(ElementType.QUAVER);
        var element = factory.create(anchor, end);

        assertThat(element.isInvalidatedBy(List.of(middle1, middle2))).isFalse();
    }

    @ParameterizedTest(name = "{0}: unrelated element deleted → not invalidated")
    @MethodSource("spanFactories")
    void testExternalDeletedNotInvalidates(String name, SpanFactory factory) {
        var anchor = new StaffElement(ElementType.QUAVER);
        var end = new StaffElement(ElementType.QUAVER);
        var external = new StaffElement(ElementType.QUAVER);
        var element = factory.create(anchor, end);

        assertThat(element.isInvalidatedBy(List.of(external))).isFalse();
    }
}
