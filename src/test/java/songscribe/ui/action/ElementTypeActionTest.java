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

package songscribe.ui.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.ElementType;

class ElementTypeActionTest extends UnitTest {

    private final ElementTypeAction durationAction =
        ElementTypeAction.createQuarterNoteAction();

    private final ElementTypeAction nonDurationAction =
        ElementTypeAction.createSingleBarlineAction();

    // CR2: createReplacement preserves note kind (note stays note)
    @Test
    void testCreateReplacementPreservesNoteKind() {
        var element = ElementType.MINIM.newInstance();
        var replacement = Objects.requireNonNull(durationAction.createReplacement(element, true));
        assertThat(replacement.getType()).isEqualTo(ElementType.CROTCHET);
    }

    // CR3: createReplacement preserves rest kind (rest stays rest)
    @Test
    void testCreateReplacementPreservesRestKind() {
        var element = ElementType.MINIM_REST.newInstance();
        var replacement = Objects.requireNonNull(durationAction.createReplacement(element, true));
        assertThat(replacement.getType()).isEqualTo(ElementType.CROTCHET_REST);
    }

    // CR4: createReplacement with grace note (toNote/toRest returns this)
    @Test
    void testCreateReplacementWithGraceNote() {
        var graceAction = ElementTypeAction.createGraceEighthNoteAction();
        var element = ElementType.CROTCHET.newInstance();
        var replacement = Objects.requireNonNull(graceAction.createReplacement(element, true));
        assertThat(replacement.getType()).isEqualTo(ElementType.GRACE_QUAVER);
    }

    // M2: matchesElement returns false when ElementType differs
    @Test
    void testDoesNotMatchElementWhenTypeDiffers() {
        var element = ElementType.MINIM.newInstance();
        assertThat(durationAction.matchesElement(element)).isFalse();
    }

    // A1: DURATION action applies to notes
    @Test
    void testDurationAppliesToNote() {
        var element = ElementType.CROTCHET.newInstance();
        assertThat(durationAction.appliesTo(element)).isTrue();
    }

    // A2: DURATION action applies to rests
    @Test
    void testDurationAppliesToRest() {
        var element = ElementType.CROTCHET_REST.newInstance();
        assertThat(durationAction.appliesTo(element)).isTrue();
    }

    // A3: DURATION action does not apply to barlines
    @Test
    void testDurationDoesNotApplyToBarline() {
        var element = ElementType.SINGLE_BARLINE.newInstance();
        assertThat(durationAction.appliesTo(element)).isFalse();
    }

    // A3b: DURATION action does not apply to grace notes
    @Test
    void testDurationDoesNotApplyToGraceNote() {
        var element = ElementType.GRACE_QUAVER.newInstance();
        assertThat(durationAction.appliesTo(element)).isFalse();
    }

    // T1: DURATION kind has correct flags (no DISABLE_IN_REST_MODE)
    @Test
    void testDurationKindHasCorrectFlags() {
        assertThat(durationAction.hasFlag(UIAction.Flag.DISABLE_IN_REST_MODE)).isFalse();
        assertThat(durationAction.hasFlag(UIAction.Flag.DISABLE_WHEN_PLAYING)).isTrue();
        assertThat(durationAction.hasFlag(UIAction.Flag.DISABLE_IN_ADJUSTMENT_MODE)).isTrue();
        assertThat(durationAction.hasFlag(UIAction.Flag.DISABLE_WHEN_EDITING_TEXT)).isTrue();
    }

    // M1: matchesElement returns true when ElementType matches
    @Test
    void testMatchesElementWhenTypeMatches() {
        var element = ElementType.CROTCHET.newInstance();
        assertThat(durationAction.matchesElement(element)).isTrue();
    }

    // A6: NON_DURATION action applies to barlines
    @Test
    void testNonDurationAppliesToBarline() {
        var element = ElementType.SINGLE_BARLINE.newInstance();
        assertThat(nonDurationAction.appliesTo(element)).isTrue();
    }

    // A4: NON_DURATION action does not apply to notes
    @Test
    void testNonDurationDoesNotApplyToNote() {
        var element = ElementType.CROTCHET.newInstance();
        assertThat(nonDurationAction.appliesTo(element)).isFalse();
    }

    // A5: NON_DURATION action does not apply to rests
    @Test
    void testNonDurationDoesNotApplyToRest() {
        var element = ElementType.CROTCHET_REST.newInstance();
        assertThat(nonDurationAction.appliesTo(element)).isFalse();
    }

    // T2: NON_DURATION kind has correct flags (includes DISABLE_IN_REST_MODE)
    @Test
    void testNonDurationKindHasCorrectFlags() {
        assertThat(nonDurationAction.hasFlag(UIAction.Flag.DISABLE_IN_REST_MODE)).isTrue();
        assertThat(nonDurationAction.hasFlag(UIAction.Flag.DISABLE_WHEN_PLAYING)).isTrue();
        assertThat(nonDurationAction.hasFlag(UIAction.Flag.DISABLE_IN_ADJUSTMENT_MODE)).isTrue();
        assertThat(nonDurationAction.hasFlag(UIAction.Flag.DISABLE_WHEN_EDITING_TEXT)).isTrue();
    }
}
