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

package songscribe.ui.component.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import songscribe.dom.ElementLocation;
import songscribe.dom.ElementType;

/**
 * Tests for {@link PreviewElementManager#getHoveredElementLocation}, which returns the element a
 * non-glissando preview would replace (so it can be highlighted), or null when nothing should be
 * highlighted: when the preview x-position matches no element, or when the active tool is a
 * glissando (which attaches to a note rather than replacing one).
 */
class PreviewElementManagerHoverTest extends PreviewElementManagerTestBase {

    private static final int LINE_INDEX = 0;
    private static final int X_INDEX = 1;

    @Test
    void testNullWhenXPositionMatchesNoElement() {
        PreviewElementManager.setXPosSsMatchesElement(false);
        PreviewElementManager.setCurrentXIndex(X_INDEX);

        assertThat(PreviewElementManager.getHoveredElementLocation())
            .as("no hovered element when the x-position matches nothing").isNull();
    }

    @Test
    void testNullForGlissandoPlaceholder() {
        PreviewElementManager.setXPosSsMatchesElement(true);
        PreviewElementManager.setCurrentXIndex(X_INDEX);
        setPreviewElement(ElementType.SLIDE.newInstance());

        assertThat(PreviewElementManager.getHoveredElementLocation())
            .as("a glissando tool never highlights a replacement target").isNull();
    }

    @Test
    void testReturnsLocationForReplaceableNote() {
        when(lc.getLineIndex()).thenReturn(LINE_INDEX);
        PreviewElementManager.setXPosSsMatchesElement(true);
        PreviewElementManager.setCurrentXIndex(X_INDEX);
        setPreviewElement(ElementType.CROTCHET.newInstance());

        assertThat(PreviewElementManager.getHoveredElementLocation())
            .as("a replaceable element is highlighted at its location")
            .isEqualTo(new ElementLocation(LINE_INDEX, X_INDEX));
    }
}
