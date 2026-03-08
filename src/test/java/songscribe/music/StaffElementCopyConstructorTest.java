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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.ui.layout.Articulation;

class StaffElementCopyConstructorTest extends UnitTest {

    private StaffElement createFullyPopulatedElement() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.setDotCount(1);
        element.setAccidental(StaffElement.Accidental.SHARP);
        element.setAccidentalInParentheses(true);
        element.setFermata(true);
        element.setTrill(true);
        element.setUpper(true);
        element.setForceArticulation(ForceArticulation.ACCENT);
        element.setDurationArticulation(DurationArticulation.STACCATO);
        element.setStemDirectionAuto(false);
        element.setStaffPosition(-3);
        element.setSyllableMovement(2);
        element.setSyllableRelationMovement(1);
        element.setForceSyllable(true);
        element.addArticulation(new Articulation(element, ArticulationType.STACCATO));
        element.addArticulation(new Articulation(element, ArticulationType.ACCENT));
        return element;
    }

    @Test
    void testNoteToNoteCopiesAllAttributes() {
        var source = createFullyPopulatedElement();
        var copy = new StaffElement(ElementType.QUAVER, source);

        assertThat(copy.getType()).isEqualTo(ElementType.QUAVER);

        // Always-copied attributes
        assertThat(copy.getDotCount()).isEqualTo(1);
        assertThat(copy.isFermata()).isTrue();
        assertThat(copy.getSyllableMovement()).isEqualTo(2);
        assertThat(copy.getSyllableRelationMovement()).isEqualTo(1);
        assertThat(copy.isForceSyllable()).isTrue();

        // Note-only attributes (copied because target is a note)
        assertThat(copy.getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
        assertThat(copy.isAccidentalInParentheses()).isTrue();
        assertThat(copy.isTrill()).isTrue();
        assertThat(copy.isUpper()).isTrue();
        assertThat(copy.getForceArticulation()).isEqualTo(ForceArticulation.ACCENT);
        assertThat(copy.getDurationArticulation()).isEqualTo(DurationArticulation.STACCATO);
        assertThat(copy.isStemDirectionAuto()).isFalse();
        assertThat(copy.getStaffPosition()).isEqualTo(-3);

        // Deep-copied articulations
        assertThat(copy.getArticulations()).hasSize(2);
        assertThat(copy.getArticulations().get(0).getType()).isEqualTo(ArticulationType.STACCATO);
        assertThat(copy.getArticulations().get(1).getType()).isEqualTo(ArticulationType.ACCENT);
        assertThat(copy.getArticulations().get(0)).isNotSameAs(source.getArticulations().get(0));
    }

    @Test
    void testNoteToRestClearsNoteOnlyAttributes() {
        var source = createFullyPopulatedElement();
        var copy = new StaffElement(ElementType.CROTCHET_REST, source);

        assertThat(copy.getType()).isEqualTo(ElementType.CROTCHET_REST);

        // Always-copied attributes
        assertThat(copy.getDotCount()).isEqualTo(1);
        assertThat(copy.isFermata()).isTrue();
        assertThat(copy.getSyllableMovement()).isEqualTo(2);
        assertThat(copy.getSyllableRelationMovement()).isEqualTo(1);
        assertThat(copy.isForceSyllable()).isTrue();

        // Note-only attributes should be at defaults
        assertThat(copy.getAccidental()).isEqualTo(StaffElement.Accidental.NONE);
        assertThat(copy.isAccidentalInParentheses()).isFalse();
        assertThat(copy.isTrill()).isFalse();
        assertThat(copy.isUpper()).isFalse();
        assertThat(copy.getForceArticulation()).isNull();
        assertThat(copy.getDurationArticulation()).isNull();
        assertThat(copy.isStemDirectionAuto()).isTrue();
        assertThat(copy.getArticulations()).isEmpty();

        // Staff position should be the default for the rest type
        assertThat(copy.getStaffPosition()).isEqualTo(ElementType.CROTCHET_REST.getDefaultStaffPosition());
    }

    @Test
    void testRestToNoteCopiesApplicableAttributes() {
        var source = new StaffElement(ElementType.QUAVER_REST);
        source.setDotCount(2);
        source.setFermata(true);
        source.setSyllableMovement(3);

        var copy = new StaffElement(ElementType.QUAVER, source);

        assertThat(copy.getType()).isEqualTo(ElementType.QUAVER);
        assertThat(copy.getDotCount()).isEqualTo(2);
        assertThat(copy.isFermata()).isTrue();
        assertThat(copy.getSyllableMovement()).isEqualTo(3);

        // Note-only attributes copied from rest source (all at defaults)
        assertThat(copy.getAccidental()).isEqualTo(StaffElement.Accidental.NONE);
        assertThat(copy.getStaffPosition()).isEqualTo(0);
    }

    @Test
    void testRestToRestCopiesAllAttributes() {
        var source = new StaffElement(ElementType.MINIM_REST);
        source.setDotCount(1);
        source.setFermata(true);

        var copy = new StaffElement(ElementType.SEMIBREVE_REST, source);

        assertThat(copy.getType()).isEqualTo(ElementType.SEMIBREVE_REST);
        assertThat(copy.getDotCount()).isEqualTo(1);
        assertThat(copy.isFermata()).isTrue();
        assertThat(copy.getStaffPosition()).isEqualTo(ElementType.SEMIBREVE_REST.getDefaultStaffPosition());
    }
}
