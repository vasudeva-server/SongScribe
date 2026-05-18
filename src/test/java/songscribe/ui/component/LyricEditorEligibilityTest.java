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

package songscribe.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.model.ElementType;
import songscribe.model.Line;
import songscribe.model.StaffElement;

/**
 * Tests for {@link LyricEditor#isLyricTargetEligible} and the package-private
 * navigation helpers {@code findNextEligibleIndex} / {@code findPreviousEligibleIndex}.
 * <p>
 * Line layout used throughout: [normal(0), grace(1)+CONNECTED, host(2), normal(3)]
 */
class LyricEditorEligibilityTest extends UnitTest {

    private static final int VERSE = 1;

    // Indices within the shared test line
    private static final int INDEX_NORMAL_BEFORE = 0;
    private static final int INDEX_GRACE = 1;
    private static final int INDEX_HOST = 2;
    private static final int INDEX_NORMAL_AFTER = 3;

    private Line line;

    @BeforeEach
    void setUp() {
        line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());

        var grace = ElementType.GRACE_QUAVER.newInstance();
        grace.setGlissando(StaffElement.Glissando.Type.CONNECTED);
        line.addElement(grace);

        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
    }

    // ---- isLyricTargetEligible ----

    @Test
    void testEligibleForGraceNote() {
        assertThat(LyricEditor.isLyricTargetEligible(line, INDEX_GRACE)).isTrue();
    }

    @Test
    void testEligibleForNormalNoteAfterHost() {
        assertThat(LyricEditor.isLyricTargetEligible(line, INDEX_NORMAL_AFTER)).isTrue();
    }

    @Test
    void testEligibleForNormalNoteBeforeGrace() {
        assertThat(LyricEditor.isLyricTargetEligible(line, INDEX_NORMAL_BEFORE)).isTrue();
    }

    @Test
    void testNotEligibleForHostOfPairedGrace() {
        // The host must always be blocked — the grace carries the lyric for the pair.
        assertThat(LyricEditor.isLyricTargetEligible(line, INDEX_HOST)).isFalse();
    }

    // ---- findNextEligibleIndex ----

    @Test
    void testFindNextFromBeforeGraceLandsOnGrace() {
        var next = LyricEditor.findNextEligibleIndex(line, INDEX_NORMAL_BEFORE, VERSE);
        assertThat(next).isEqualTo(INDEX_GRACE);
    }

    @Test
    void testFindNextFromGraceSkipsHostLandsOnNormalAfter() {
        // Advancing from the grace must skip the host and land on the next normal note.
        var next = LyricEditor.findNextEligibleIndex(line, INDEX_GRACE, VERSE);
        assertThat(next).isEqualTo(INDEX_NORMAL_AFTER);
    }

    // ---- findPreviousEligibleIndex ----

    @Test
    void testFindPreviousFromNormalAfterSkipsHostLandsOnGrace() {
        // Retreating from the note after the host must skip the host and land on the grace.
        var previous = LyricEditor.findPreviousEligibleIndex(line, INDEX_NORMAL_AFTER, VERSE);
        assertThat(previous).isEqualTo(INDEX_GRACE);
    }

    @Test
    void testFindPreviousFromHostLandsOnGrace() {
        // Retreating from the host itself must land on the grace (host is skipped for navigation).
        var previous = LyricEditor.findPreviousEligibleIndex(line, INDEX_HOST, VERSE);
        assertThat(previous).isEqualTo(INDEX_GRACE);
    }
}
