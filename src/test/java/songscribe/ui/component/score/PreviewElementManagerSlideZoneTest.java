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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Song;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.SlideZone;
import songscribe.dom.StaffElement;

/**
 * Unit tests for {@link PreviewElementManager#computeSlideZone} after the
 * method was widened to package-private and had its {@code intendedType}
 * parameter extracted from the caller. All cases exercise pure line/index logic
 * with no UI or Actions dependency.
 */
class PreviewElementManagerSlideZoneTest extends UnitTest {

    private static final SlideZone CONNECTING = SlideZone.GLISSANDO;
    private static final SlideZone FALL = SlideZone.FALL;

    // Default and alternative staff positions that yield different pitches
    private static final int DEFAULT_POSITION_SP = 0;
    private static final int ALT_POSITION_SP = 2;

    private Song song;
    private Line line;

    @BeforeEach
    void setUp() {
        song = new Song();
        line = song.getLine(0);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private StaffElement note(int staffPositionSp) {
        var note = ElementType.CROTCHET.newInstance();
        note.setStaffPosition(staffPositionSp);
        return note;
    }

    private StaffElement note() {
        return note(DEFAULT_POSITION_SP);
    }

    private StaffElement rest() {
        return ElementType.CROTCHET_REST.newInstance();
    }

    private StaffElement barLine() {
        return ElementType.SINGLE_BARLINE.newInstance();
    }

    private StaffElement graceNote() {
        return ElementType.GRACE_QUAVER.newInstance();
    }

    private void addElements(StaffElement... elements) {
        song.withoutMutationTracking(() -> {
            for (var element : elements) {
                line.addElement(element);
            }
        });
    }

    private @Nullable SlideZone zone(
        int xIndex,
        @Nullable SlideZone intendedZone
    ) {
        return PreviewElementManager.computeSlideZone(line, xIndex, intendedZone);
    }

    // -----------------------------------------------------------------------
    // Source/target rest suppression
    // -----------------------------------------------------------------------

    @Test
    void testConnectedGlissandoSuppressedWhenTargetIsRest() {
        addElements(note(), rest());
        assertThat(zone(1, CONNECTING)).as("target is rest").isNull();
    }

    @Test
    void testConnectedGlissandoSuppressedWhenSourceIsRest() {
        addElements(rest(), note());
        assertThat(zone(1, CONNECTING)).as("source is rest").isNull();
    }

    @Test
    void testConnectedGlissandoSuppressedWhenBothRests() {
        addElements(rest(), rest());
        assertThat(zone(1, CONNECTING)).as("both rests").isNull();
    }

    @Test
    void testSlideOutSuppressedWhenSourceIsRest() {
        addElements(rest());
        assertThat(zone(1, FALL)).as("source is rest").isNull();
    }

    // -----------------------------------------------------------------------
    // Source/target non-pitched suppression (issue #464)
    // -----------------------------------------------------------------------

    @Test
    void testConnectedGlissandoSuppressedWhenTargetIsBarLine() {
        addElements(note(), barLine());
        assertThat(zone(1, CONNECTING)).as("target is bar line").isNull();
    }

    @Test
    void testConnectedGlissandoSuppressedWhenTargetIsGraceNote() {
        addElements(note(), graceNote());
        assertThat(zone(1, CONNECTING)).as("target is grace note").isNull();
    }

    @Test
    void testConnectedGlissandoSuppressedWhenSourceIsBarLine() {
        addElements(barLine(), note());
        assertThat(zone(1, CONNECTING)).as("source is bar line").isNull();
    }

    @Test
    void testSlideOutSuppressedWhenSourceIsBarLine() {
        addElements(barLine());
        assertThat(zone(1, FALL)).as("source is bar line").isNull();
    }

    // -----------------------------------------------------------------------
    // Valid pair
    // -----------------------------------------------------------------------

    @Test
    void testConnectedGlissandoReturnsConnectedForValidPair() {
        addElements(note(DEFAULT_POSITION_SP), note(ALT_POSITION_SP));
        assertThat(zone(1, CONNECTING)).as("valid note pair").isEqualTo(CONNECTING);
    }

    @Test
    void testConnectedGlissandoSuppressedForSamePitch() {
        addElements(note(), note());
        assertThat(zone(1, CONNECTING)).as("same pitch").isNull();
    }

    // -----------------------------------------------------------------------
    // Boundary conditions
    // -----------------------------------------------------------------------

    @Test
    void testReturnsNullAtLineStart() {
        addElements(note());
        assertThat(zone(0, CONNECTING)).as("line start").isNull();
    }

    @Test
    void testReturnsNullAtLineEndForConnected() {
        addElements(note());
        // elementCount() includes the auto-maintained terminal; xIndex == elementCount
        // trips the "no element to the right" guard.
        assertThat(zone(line.elementCount(), CONNECTING)).as("past end, CONNECTING").isNull();
    }

    @Test
    void testSlideOutAllowedAtLineEnd() {
        addElements(note());
        // xIndex = 1: source is the only note; FALL has no right-element requirement.
        assertThat(zone(1, FALL)).as("at end of content, FALL").isEqualTo(FALL);
    }

    @Test
    void testReturnsNullWhenIntendedTypeIsNull() {
        addElements(note());
        assertThat(zone(1, null)).as("null intended type").isNull();
    }
}
