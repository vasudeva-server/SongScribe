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

import songscribe.UnitTest;
import songscribe.music.NoteType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.ui.action.NoteTypeAction.Kind;

class NoteTypeActionTest extends UnitTest {

    private final NoteTypeAction durationAction = new NoteTypeAction(
            Kind.DURATION, NoteType.CROTCHET, "Quarter", null, 0, "quarter", "Quarter note", 0, 0
    );

    private final NoteTypeAction nonDurationAction = new NoteTypeAction(
            Kind.NON_DURATION, NoteType.SINGLE_BARLINE, "Barline", null, 0, "barline", "Single barline", 0, 0
    );

    // A1: DURATION action applies to notes
    @Test
    void testDurationAppliesToNote() {
        var note = NoteType.CROTCHET.newInstance();
        assertThat(durationAction.appliesTo(note)).isTrue();
    }

    // A2: DURATION action applies to rests
    @Test
    void testDurationAppliesToRest() {
        var note = NoteType.CROTCHET_REST.newInstance();
        assertThat(durationAction.appliesTo(note)).isTrue();
    }

    // A3: DURATION action does not apply to barlines
    @Test
    void testDurationDoesNotApplyToBarline() {
        var note = NoteType.SINGLE_BARLINE.newInstance();
        assertThat(durationAction.appliesTo(note)).isFalse();
    }

    // A3b: DURATION action does not apply to grace notes
    @Test
    void testDurationDoesNotApplyToGraceNote() {
        var note = NoteType.GRACE_QUAVER.newInstance();
        assertThat(durationAction.appliesTo(note)).isFalse();
    }

    // A4: NON_DURATION action does not apply to notes
    @Test
    void testNonDurationDoesNotApplyToNote() {
        var note = NoteType.CROTCHET.newInstance();
        assertThat(nonDurationAction.appliesTo(note)).isFalse();
    }

    // A5: NON_DURATION action does not apply to rests
    @Test
    void testNonDurationDoesNotApplyToRest() {
        var note = NoteType.CROTCHET_REST.newInstance();
        assertThat(nonDurationAction.appliesTo(note)).isFalse();
    }

    // A6: NON_DURATION action applies to barlines
    @Test
    void testNonDurationAppliesToBarline() {
        var note = NoteType.SINGLE_BARLINE.newInstance();
        assertThat(nonDurationAction.appliesTo(note)).isTrue();
    }

    // M1: matchesNote returns true when NoteType matches
    @Test
    void testMatchesNoteWhenTypeMatches() {
        var note = NoteType.CROTCHET.newInstance();
        assertThat(durationAction.matchesNote(note)).isTrue();
    }

    // M2: matchesNote returns false when NoteType differs
    @Test
    void testDoesNotMatchNoteWhenTypeDiffers() {
        var note = NoteType.MINIM.newInstance();
        assertThat(durationAction.matchesNote(note)).isFalse();
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
        var note = NoteType.CROTCHET.newInstance();
        assertThat(durationAction.createReplacement(note, false)).isNull();
    }

    // CR2: createReplacement preserves note kind (note stays note)
    @Test
    void testCreateReplacementPreservesNoteKind() {
        var note = NoteType.MINIM.newInstance();
        var replacement = durationAction.createReplacement(note, true);
        assertThat(replacement).isNotNull();
        assertThat(replacement.getNoteType()).isEqualTo(NoteType.CROTCHET);
    }

    // CR3: createReplacement preserves rest kind (rest stays rest)
    @Test
    void testCreateReplacementPreservesRestKind() {
        var note = NoteType.MINIM_REST.newInstance();
        var replacement = durationAction.createReplacement(note, true);
        assertThat(replacement).isNotNull();
        assertThat(replacement.getNoteType()).isEqualTo(NoteType.CROTCHET_REST);
    }

    // CR4: createReplacement with grace note (toNote/toRest returns this)
    @Test
    void testCreateReplacementWithGraceNote() {
        var graceAction = new NoteTypeAction(
            Kind.DURATION, NoteType.GRACE_QUAVER, "Grace", null, 0, "grace", "Grace note", 0, 0
        );
        var note = NoteType.CROTCHET.newInstance();
        var replacement = graceAction.createReplacement(note, true);
        assertThat(replacement).isNotNull();
        assertThat(replacement.getNoteType()).isEqualTo(NoteType.GRACE_QUAVER);
    }
}
