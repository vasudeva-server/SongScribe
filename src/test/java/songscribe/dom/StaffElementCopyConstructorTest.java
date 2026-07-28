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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class StaffElementCopyConstructorTest extends UnitTest {

    private StaffElement createFullyPopulatedElement() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.setDotCount(1);
        element.setAccidental(StaffElement.Accidental.SHARP);
        element.setAccidentalInParentheses(true);
        element.addAttachment(new FermataAttachment(element));
        element.setUpper(true);
        element.setStemDirectionAuto(false);
        element.setStaffPosition(-3);
        element.addArticulation(new Articulation(element, ArticulationType.STACCATO));
        element.addArticulation(new Articulation(element, ArticulationType.ACCENT));
        element.lyrics.add(new Lyric(1, "heart", Lyric.Extend.START, Lyric.Syllabic.BEGIN, false));
        element.lyrics.add(new Lyric(1, "ache", Lyric.Extend.NONE, Lyric.Syllabic.END, false));
        return element;
    }

    @Test
    void testNoteToNoteCopiesAllAttributes() {
        var source = createFullyPopulatedElement();
        var copy = new StaffElement(ElementType.QUAVER, source);

        assertThat(copy.getType()).isEqualTo(ElementType.QUAVER);

        // Always-copied attributes
        assertThat(copy.getDotCount()).isEqualTo(1);
        assertThat(copy.findAttachment(FermataAttachment.class)).isNotNull();

        // Note-only attributes (copied because target is a note)
        assertThat(copy.getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
        assertThat(copy.isAccidentalInParentheses()).isTrue();
        assertThat(copy.isUpper()).isTrue();
        assertThat(copy.isStemDirectionAuto()).isFalse();
        assertThat(copy.getStaffPosition()).isEqualTo(-3);

        // Deep-copied articulations
        assertThat(copy.getArticulations()).hasSize(2);
        assertThat(copy.getArticulations().get(0).getType()).isEqualTo(ArticulationType.STACCATO);
        assertThat(copy.getArticulations().get(1).getType()).isEqualTo(ArticulationType.ACCENT);
        assertThat(copy.getArticulations().get(0)).isNotSameAs(source.getArticulations().getFirst());
    }

    @Test
    void testNoteToRestClearsNoteOnlyAttributes() {
        var source = createFullyPopulatedElement();
        var copy = new StaffElement(ElementType.CROTCHET_REST, source);

        assertThat(copy.getType()).isEqualTo(ElementType.CROTCHET_REST);

        // Always-copied attributes
        assertThat(copy.getDotCount()).isEqualTo(1);
        assertThat(copy.findAttachment(FermataAttachment.class)).isNotNull();

        // Note-only attributes should be at defaults
        assertThat(copy.getAccidental()).isNull();
        assertThat(copy.isAccidentalInParentheses()).isFalse();
        assertThat(copy.isUpper()).isFalse();
        assertThat(copy.isStemDirectionAuto()).isTrue();
        assertThat(copy.getArticulations()).isEmpty();

        // Staff position should be the default for the rest type
        assertThat(copy.getStaffPosition()).isEqualTo(ElementType.CROTCHET_REST.getDefaultStaffPosition());
    }

    @Test
    void testRestToNoteCopiesApplicableAttributes() {
        var source = new StaffElement(ElementType.QUAVER_REST);
        source.setDotCount(2);
        source.addAttachment(new FermataAttachment(source));

        var copy = new StaffElement(ElementType.QUAVER, source);

        assertThat(copy.getType()).isEqualTo(ElementType.QUAVER);
        assertThat(copy.getDotCount()).isEqualTo(2);
        assertThat(copy.findAttachment(FermataAttachment.class)).isNotNull();

        // Note-only attributes copied from rest source (all at defaults)
        assertThat(copy.getAccidental()).isNull();
        assertThat(copy.getStaffPosition()).isEqualTo(0);
    }

    @Test
    void testRestToRestCopiesAllAttributes() {
        var source = new StaffElement(ElementType.MINIM_REST);
        source.setDotCount(1);
        source.addAttachment(new FermataAttachment(source));

        var copy = new StaffElement(ElementType.SEMIBREVE_REST, source);

        assertThat(copy.getType()).isEqualTo(ElementType.SEMIBREVE_REST);
        assertThat(copy.getDotCount()).isEqualTo(1);
        assertThat(copy.findAttachment(FermataAttachment.class)).isNotNull();
        assertThat(copy.getStaffPosition()).isEqualTo(ElementType.SEMIBREVE_REST.getDefaultStaffPosition());
    }

    @Test
    void testCloneCopyConstructorDeepCopiesArticulations() {
        var source = createFullyPopulatedElement();
        var clone = source.clone();

        // Articulation lists are equal in content
        assertThat(clone.getArticulations()).hasSize(source.getArticulations().size());

        // Instances are distinct objects — clone has its own copies
        assertThat(clone.getArticulations().get(0)).isNotSameAs(source.getArticulations().get(0));

        // Mutating clone's articulations does not affect the source
        clone.clearArticulations();
        assertThat(source.getArticulations()).hasSize(2);

        // Mutating source's articulations does not affect the clone
        var freshClone = source.clone();
        source.clearArticulations();
        assertThat(freshClone.getArticulations()).hasSize(2);
    }

    @Test
    void testCloneCopyConstructorDeepCopiesAttachments() {
        var source = createFullyPopulatedElement();
        var clone = source.clone();

        // Attachment lists are equal in content (same type)
        assertThat(clone.getAttachments()).hasSize(source.getAttachments().size());

        // Instances are distinct objects — clone has its own copies
        assertThat(clone.getAttachments().get(0)).isNotSameAs(source.getAttachments().get(0));

        // Mutating clone's attachments does not affect the source
        clone.clearAttachments();
        assertThat(source.getAttachments()).hasSize(1);

        // Mutating source's attachments does not affect the clone
        var freshClone = source.clone();
        source.clearAttachments();
        assertThat(freshClone.getAttachments()).hasSize(1);
    }

    @Test
    void testCloneCopyConstructorDeepCopiesLyrics() {
        var source = new StaffElement(ElementType.CROTCHET);
        source.lyrics.add(new Lyric(1, "heart", Lyric.Extend.START, Lyric.Syllabic.BEGIN, false));
        source.lyrics.add(new Lyric(1, "ache", Lyric.Extend.NONE, Lyric.Syllabic.END, false));

        var clone = source.clone();

        // Content is equal
        assertThat(clone.lyrics).isEqualTo(source.lyrics);

        // Reference is distinct — mutating the clone does not affect the source
        clone.lyrics.clear();
        assertThat(source.lyrics).hasSize(2);
    }

    @Test
    void testCrossTypeCopyConstructorDeepCopiesLyrics() {
        var source = new StaffElement(ElementType.CROTCHET);
        source.lyrics.add(new Lyric(1, "do", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));

        var copy = new StaffElement(ElementType.QUAVER, source);

        // Content is equal
        assertThat(copy.lyrics)
            .isEqualTo(List.of(new Lyric(1, "do", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false)));

        // Reference is distinct
        copy.lyrics.clear();
        assertThat(source.lyrics).hasSize(1);
    }

    @Test
    void testGetMainLyricReturnsFirstLyric() {
        var element = new StaffElement(ElementType.CROTCHET);

        assertThat(element.getLyricForVerse(Lyric.FIRST_VERSE)).isNull();

        var lyric = new Lyric(1, "heart", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);
        element.lyrics.add(lyric);

        assertThat(element.getLyricForVerse(Lyric.FIRST_VERSE)).isEqualTo(lyric);
    }
}
