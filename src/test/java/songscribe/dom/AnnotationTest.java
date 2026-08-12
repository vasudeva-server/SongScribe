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

import java.awt.Component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import songscribe.UnitTest;

/**
 * Tests default values and accessor contract for {@link Annotation}.
 *
 * <p><b>The class invariant</b> — the text is never blank — is asserted at every door into it:
 * both constructors and the setter. Each is covered for both forms of blank, empty and whitespace,
 * since a field holding only spaces draws nothing just as an empty one does. The setter case also
 * asserts that a refused write leaves the previous text in place, which is what makes the
 * invariant hold for the life of the object rather than only at construction.
 */
class AnnotationTest extends UnitTest {

    private static final String TEXT = "dolce";

    @Test
    void testPlacementDefaultsToAbove() {
        var annotation = new Annotation(TEXT);
        assertThat(annotation.getPlacement()).isEqualTo(Annotation.Placement.ABOVE);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   " })
    void testConstructingWithBlankTextIsRefused(String blank) {
        assertThatThrownBy(() -> new Annotation(blank))
            .as("an annotation with nothing to draw has no reason to exist")
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   " })
    void testConstructingWithBlankTextAndAnAlignmentIsRefused(String blank) {
        assertThatThrownBy(() -> new Annotation(blank, Component.CENTER_ALIGNMENT))
            .as("supplying an alignment does not make blank text usable")
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   " })
    void testSettingBlankTextIsRefusedAndLeavesTheTextAsItWas(String blank) {
        var annotation = new Annotation(TEXT);

        assertThatThrownBy(() -> annotation.setAnnotation(blank))
            .as("the invariant holds for the object's whole life, not just at construction")
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(annotation.getAnnotation())
            .as("a refused write leaves the annotation readable, not half-updated")
            .isEqualTo(TEXT);
    }
}
