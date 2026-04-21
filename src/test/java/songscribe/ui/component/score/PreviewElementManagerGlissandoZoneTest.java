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
import songscribe.music.Composition;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;

/**
 * Unit tests for {@link PreviewElementManager#computeGlissandoZone} after the
 * method was widened to package-private and had its {@code intendedType}
 * parameter extracted from the caller. All cases exercise pure line/index logic
 * with no UI or Actions dependency.
 */
class PreviewElementManagerGlissandoZoneTest extends UnitTest {

    private static final StaffElement.Glissando.Type CONNECTED = StaffElement.Glissando.Type.CONNECTED;
    private static final StaffElement.Glissando.Type SLIDE_OUT = StaffElement.Glissando.Type.SLIDE_OUT;

    // Default and alternative staff positions that yield different pitches
    private static final int DEFAULT_POSITION_SP = 0;
    private static final int ALT_POSITION_SP = 2;

    private Composition composition;
    private Line line;

    @BeforeEach
    void setUp() {
        composition = new Composition();
        line = composition.getLine(0);
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

    private void addElements(StaffElement... elements) {
        composition.withoutMutationTracking(() -> {
            for (var element : elements) {
                line.addElement(element);
            }
        });
    }

    private StaffElement.Glissando.@Nullable Type zone(
        int xIndex,
        StaffElement.Glissando.@Nullable Type intendedType
    ) {
        return PreviewElementManager.computeGlissandoZone(line, xIndex, intendedType);
    }

    // -----------------------------------------------------------------------
    // Source/target rest suppression
    // -----------------------------------------------------------------------

    @Test
    void testConnectedGlissandoSuppressedWhenTargetIsRest() {
        addElements(note(), rest());
        assertThat(zone(1, CONNECTED)).as("target is rest").isNull();
    }

    @Test
    void testConnectedGlissandoSuppressedWhenSourceIsRest() {
        addElements(rest(), note());
        assertThat(zone(1, CONNECTED)).as("source is rest").isNull();
    }

    @Test
    void testConnectedGlissandoSuppressedWhenBothRests() {
        addElements(rest(), rest());
        assertThat(zone(1, CONNECTED)).as("both rests").isNull();
    }

    @Test
    void testSlideOutSuppressedWhenSourceIsRest() {
        addElements(rest());
        assertThat(zone(1, SLIDE_OUT)).as("source is rest").isNull();
    }

    // -----------------------------------------------------------------------
    // Valid pair
    // -----------------------------------------------------------------------

    @Test
    void testConnectedGlissandoReturnsConnectedForValidPair() {
        addElements(note(DEFAULT_POSITION_SP), note(ALT_POSITION_SP));
        assertThat(zone(1, CONNECTED)).as("valid note pair").isEqualTo(CONNECTED);
    }

    @Test
    void testConnectedGlissandoSuppressedForSamePitch() {
        addElements(note(), note());
        assertThat(zone(1, CONNECTED)).as("same pitch").isNull();
    }

    // -----------------------------------------------------------------------
    // Boundary conditions
    // -----------------------------------------------------------------------

    @Test
    void testReturnsNullAtLineStart() {
        addElements(note());
        assertThat(zone(0, CONNECTED)).as("line start").isNull();
    }

    @Test
    void testReturnsNullAtLineEndForConnected() {
        addElements(note());
        // elementCount() includes the auto-maintained terminal; xIndex == elementCount
        // trips the "no element to the right" guard.
        assertThat(zone(line.elementCount(), CONNECTED)).as("past end, CONNECTED").isNull();
    }

    @Test
    void testSlideOutAllowedAtLineEnd() {
        addElements(note());
        // xIndex = 1: source is the only note; SLIDE_OUT has no right-element requirement.
        assertThat(zone(1, SLIDE_OUT)).as("at end of content, SLIDE_OUT").isEqualTo(SLIDE_OUT);
    }

    @Test
    void testReturnsNullWhenIntendedTypeIsNull() {
        addElements(note());
        assertThat(zone(1, null)).as("null intended type").isNull();
    }
}
