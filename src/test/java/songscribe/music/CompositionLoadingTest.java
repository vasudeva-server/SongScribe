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

package songscribe.music;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.util.MyFontUtils;

class CompositionLoadingTest extends UnitTest {

    private Composition composition;

    @BeforeEach
    void setUp() {
        composition = new Composition();
        composition.setModified(false);
    }

    @Test
    void testSetAnnotationFontSetsModified() {
        var font = MyFontUtils.createFont("LatoPlus-Bold", 20);
        composition.setAnnotationFont(font);
        assertThat(composition.isModified()).isTrue();
    }

    @Test
    void testSetAttributionFontSetsModified() {
        var font = MyFontUtils.createFont("LatoPlus-Bold", 20);
        composition.setAttributionFont(font);
        assertThat(composition.isModified()).isTrue();
    }

    @Test
    void testSetLyricsFontSetsModified() {
        var font = MyFontUtils.createFont("LatoPlus-Bold", 20);
        composition.setLyricsFont(font);
        assertThat(composition.isModified()).isTrue();
    }

    @Test
    void testSetTitleFontSetsModified() {
        var font = MyFontUtils.createFont("LatoPlus-Bold", 20);
        composition.setTitleFont(font);
        assertThat(composition.isModified()).isTrue();
    }

    // T63: Loading a pre-2.4 file runs migrateFinalTerminal inside withoutMutationTracking,
    //      so the document is clean even though migration mutated elements.
    @Test
    void testLoadingLegacyCompositionDoesNotDirtyDocument() throws Exception {
        var loaded = loadFixture("full-line");
        assertThat(loaded.isModified()).isFalse();
    }

    // selection2.mssw has no dynamics — regression guard for older files without them.
    private static final int SELECTION2_ELEMENT_COUNT = 19;

    @Test
    void testFixtureWithoutDynamicsLoadsWithoutError() throws Exception {
        var loaded = loadFixture("selection2");
        assertThat(loaded.getLine(0).elementCount())
            .as("selection2 fixture element count")
            .isEqualTo(SELECTION2_ELEMENT_COUNT);
    }
}
