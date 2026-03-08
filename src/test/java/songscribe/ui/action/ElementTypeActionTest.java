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
import static songscribe.ui.action.ElementTypeAction.Kind;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.ElementType;

class ElementTypeActionTest extends UnitTest {

    private final ElementTypeAction durationAction = new ElementTypeAction(
        Kind.DURATION, ElementType.CROTCHET, "Quarter", null, 0, "quarter", "Quarter note", 0, 0
    );

    private final ElementTypeAction nonDurationAction = new ElementTypeAction(
        Kind.NON_DURATION, ElementType.SINGLE_BARLINE, "Barline", null, 0, "barline", "Single barline", 0, 0
    );

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

    // A6: NON_DURATION action applies to barlines
    @Test
    void testNonDurationAppliesToBarline() {
        var element = ElementType.SINGLE_BARLINE.newInstance();
        assertThat(nonDurationAction.appliesTo(element)).isTrue();
    }

    // M1: matchesElement returns true when ElementType matches
    @Test
    void testMatchesElementWhenTypeMatches() {
        var element = ElementType.CROTCHET.newInstance();
        assertThat(durationAction.matchesElement(element)).isTrue();
    }

    // M2: matchesElement returns false when ElementType differs
    @Test
    void testDoesNotMatchElementWhenTypeDiffers() {
        var element = ElementType.MINIM.newInstance();
        assertThat(durationAction.matchesElement(element)).isFalse();
    }

    // T1: DURATION kind has correct flags (no DISABLE_IN_REST_MODE)
    @Test
    void testDurationKindHasCorrectFlags() {
        assertThat(durationAction.hasFlag(UIAction.Flag.DISABLE_IN_REST_MODE)).isFalse();
        assertThat(durationAction.hasFlag(UIAction.Flag.DISABLE_WHEN_PLAYING)).isTrue();
        assertThat(durationAction.hasFlag(UIAction.Flag.DISABLE_IN_ADJUSTMENT_MODE)).isTrue();
        assertThat(durationAction.hasFlag(UIAction.Flag.DISABLE_WHEN_EDITING_TEXT)).isTrue();
    }

    // T2: NON_DURATION kind has correct flags (includes DISABLE_IN_REST_MODE)
    @Test
    void testNonDurationKindHasCorrectFlags() {
        assertThat(nonDurationAction.hasFlag(UIAction.Flag.DISABLE_IN_REST_MODE)).isTrue();
        assertThat(nonDurationAction.hasFlag(UIAction.Flag.DISABLE_WHEN_PLAYING)).isTrue();
        assertThat(nonDurationAction.hasFlag(UIAction.Flag.DISABLE_IN_ADJUSTMENT_MODE)).isTrue();
        assertThat(nonDurationAction.hasFlag(UIAction.Flag.DISABLE_WHEN_EDITING_TEXT)).isTrue();
    }

    // CR1: createReplacement returns null when selected == false
    @Test
    void testCreateReplacementReturnsNullWhenNotSelected() {
        var element = ElementType.CROTCHET.newInstance();
        assertThat(durationAction.createReplacement(element, false)).isNull();
    }

    // CR2: createReplacement preserves note kind (note stays note)
    @Test
    void testCreateReplacementPreservesNoteKind() {
        var element = ElementType.MINIM.newInstance();
        var replacement = durationAction.createReplacement(element, true);
        assertThat(replacement).isNotNull();
        assertThat(replacement.getType()).isEqualTo(ElementType.CROTCHET);
    }

    // CR3: createReplacement preserves rest kind (rest stays rest)
    @Test
    void testCreateReplacementPreservesRestKind() {
        var element = ElementType.MINIM_REST.newInstance();
        var replacement = durationAction.createReplacement(element, true);
        assertThat(replacement).isNotNull();
        assertThat(replacement.getType()).isEqualTo(ElementType.CROTCHET_REST);
    }

    // CR4: createReplacement with grace note (toNote/toRest returns this)
    @Test
    void testCreateReplacementWithGraceNote() {
        var graceAction = new ElementTypeAction(
            Kind.DURATION, ElementType.GRACE_QUAVER, "Grace", null, 0, "grace", "Grace note", 0, 0
        );
        var element = ElementType.CROTCHET.newInstance();
        var replacement = graceAction.createReplacement(element, true);
        assertThat(replacement).isNotNull();
        assertThat(replacement.getType()).isEqualTo(ElementType.GRACE_QUAVER);
    }
}
