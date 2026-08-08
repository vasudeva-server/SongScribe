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

package songscribe.layout.stacking;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.createNote;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.DynamicAttachment;

/**
 * Covers only {@link DynamicGrouper}'s grouping rule — which marks land in which group — never
 * the Y arithmetic {@link StructuralStacker} derives from a group afterward.
 */
class DynamicGrouperTest extends UnitTest {

    @Test
    void testLoneTextDynamicIsItsOwnGroup() {
        var note1 = createNote(0, false);
        var note2 = createNote(0, false);
        note2.addAttachment(new DynamicAttachment(note2, DynamicAttachment.DynamicType.FORTE));

        var line = detachedLine();
        line.addElement(note1);
        line.addElement(note2);

        var groups = DynamicGrouper.group(line);

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().members()).hasSize(1);
        assertThat(groups.getFirst().members().getFirst())
            .isInstanceOf(DynamicGrouper.Member.OfDynamic.class);
    }

    @Test
    void testDynamicImmediatelyBeforeHairpinAnchorJoinsItsGroup() {
        var dynamicNote = createNote(0, false);
        dynamicNote.addAttachment(
            new DynamicAttachment(dynamicNote, DynamicAttachment.DynamicType.MEZZO_FORTE));
        var anchorNote = createNote(0, false);
        var endNote = createNote(0, false);

        var line = detachedLine();
        line.addElement(dynamicNote);
        line.addElement(anchorNote);
        line.addElement(endNote);

        var crescendo = new Crescendo(anchorNote, endNote);
        line.addSpan(crescendo);

        var groups = DynamicGrouper.group(line);

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().members()).hasSize(2);
    }

    @Test
    void testDynamicImmediatelyAfterHairpinEndJoinsItsGroup() {
        var anchorNote = createNote(0, false);
        var endNote = createNote(0, false);
        var dynamicNote = createNote(0, false);
        dynamicNote.addAttachment(
            new DynamicAttachment(dynamicNote, DynamicAttachment.DynamicType.PIANO));

        var line = detachedLine();
        line.addElement(anchorNote);
        line.addElement(endNote);
        line.addElement(dynamicNote);

        var crescendo = new Crescendo(anchorNote, endNote);
        line.addSpan(crescendo);

        var groups = DynamicGrouper.group(line);

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().members()).hasSize(2);
    }

    @Test
    void testDynamicTwoElementsOutsideEitherBoundDoesNotJoin() {
        var anchorNote = createNote(0, false);
        var endNote = createNote(0, false);
        var gapNote = createNote(0, false);
        var dynamicNote = createNote(0, false);
        dynamicNote.addAttachment(
            new DynamicAttachment(dynamicNote, DynamicAttachment.DynamicType.FORTE));

        var line = detachedLine();
        line.addElement(anchorNote);
        line.addElement(endNote);
        line.addElement(gapNote);
        line.addElement(dynamicNote);

        var crescendo = new Crescendo(anchorNote, endNote);
        line.addSpan(crescendo);

        var groups = DynamicGrouper.group(line);

        assertThat(groups).hasSize(2);
        assertThat(groups).allSatisfy(group -> assertThat(group.members()).hasSize(1));
    }

    @Test
    void testTwoHairpinsSharingAnElementMergeIntoOneGroup() {
        var note1 = createNote(0, false);
        var note2 = createNote(0, false);
        var note3 = createNote(0, false);

        var line = detachedLine();
        line.addElement(note1);
        line.addElement(note2);
        line.addElement(note3);

        var crescendo = new Crescendo(note1, note2);
        var diminuendo = new Diminuendo(note2, note3);
        line.addSpan(crescendo);
        line.addSpan(diminuendo);

        var groups = DynamicGrouper.group(line);

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().members()).hasSize(2);
    }

    @Test
    void testTwoDisjointHairpinsStaySeparate() {
        var note1 = createNote(0, false);
        var note2 = createNote(0, false);
        var gapNote = createNote(0, false);
        var note3 = createNote(0, false);
        var note4 = createNote(0, false);

        var line = detachedLine();
        line.addElement(note1);
        line.addElement(note2);
        line.addElement(gapNote);
        line.addElement(note3);
        line.addElement(note4);

        var crescendo = new Crescendo(note1, note2);
        var diminuendo = new Diminuendo(note3, note4);
        line.addSpan(crescendo);
        line.addSpan(diminuendo);

        var groups = DynamicGrouper.group(line);

        assertThat(groups).hasSize(2);
        assertThat(groups).allSatisfy(group -> assertThat(group.members()).hasSize(1));
    }

    @Test
    void testGroupsComeBackInAscendingXOrderOfLeftmostMember() {
        // The dynamic sits left of the hairpin but is not adjacent to it, so the two never merge.
        // group() clusters hairpins first and only ever appends a standalone dynamic's cluster
        // after them, so the unsorted list would come back [hairpin(2), dynamic(0)] — the wrong
        // order. Only the explicit sort by leftmost member produces the order asserted here, which
        // is what makes this test able to fail if that sort is dropped.
        var leadingDynamicNote = createNote(0, false);
        leadingDynamicNote.addAttachment(
            new DynamicAttachment(leadingDynamicNote, DynamicAttachment.DynamicType.FORTE));
        var gapNote = createNote(0, false);
        var anchorNote = createNote(0, false);
        var endNote = createNote(0, false);

        var line = detachedLine();
        line.addElement(leadingDynamicNote);
        line.addElement(gapNote);
        line.addElement(anchorNote);
        line.addElement(endNote);

        var crescendo = new Crescendo(anchorNote, endNote);
        line.addSpan(crescendo);

        var groups = DynamicGrouper.group(line);

        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).members().getFirst())
            .isInstanceOf(DynamicGrouper.Member.OfDynamic.class);
        assertThat(groups.get(1).members().getFirst())
            .isInstanceOf(DynamicGrouper.Member.OfHairpin.class);
    }

    @Test
    void testDynamicBetweenTwoDisjointHairpinsMergesBothIntoOneGroup() {
        // The "< p >" case the shared baseline exists for: the dynamic abuts the first hairpin's
        // end and the second's anchor, so the two otherwise-separate hairpin clusters must be
        // absorbed into one and all three marks placed on a single reference line.
        var firstAnchor = createNote(0, false);
        var firstEnd = createNote(0, false);
        var dynamicNote = createNote(0, false);
        dynamicNote.addAttachment(
            new DynamicAttachment(dynamicNote, DynamicAttachment.DynamicType.PIANO));
        var secondAnchor = createNote(0, false);
        var secondEnd = createNote(0, false);

        var line = detachedLine();
        line.addElement(firstAnchor);
        line.addElement(firstEnd);
        line.addElement(dynamicNote);
        line.addElement(secondAnchor);
        line.addElement(secondEnd);

        line.addSpan(new Crescendo(firstAnchor, firstEnd));
        line.addSpan(new Diminuendo(secondAnchor, secondEnd));

        var groups = DynamicGrouper.group(line);

        assertThat(groups).hasSize(1);

        var members = groups.getFirst().members();
        assertThat(members).hasSize(3);
        // Ascending leftmost-element order: crescendo(0), dynamic(2), diminuendo(3).
        assertThat(members.get(0)).isInstanceOf(DynamicGrouper.Member.OfHairpin.class);
        assertThat(members.get(1)).isInstanceOf(DynamicGrouper.Member.OfDynamic.class);
        assertThat(members.get(2)).isInstanceOf(DynamicGrouper.Member.OfHairpin.class);
    }

    @Test
    void testHairpinWhoseEndpointsAreNotOnTheLineIsSkipped() {
        // A hairpin bound to elements of some other line has no geometry here. Including it would
        // put a member in a group whose element indices this line cannot resolve, so it must be
        // dropped rather than grouped with whatever happens to sit nearby.
        var foreignAnchor = createNote(0, false);
        var foreignEnd = createNote(0, false);
        var dynamicNote = createNote(0, false);
        dynamicNote.addAttachment(
            new DynamicAttachment(dynamicNote, DynamicAttachment.DynamicType.FORTE));

        var line = detachedLine();
        line.addElement(dynamicNote);
        line.addSpan(new Crescendo(foreignAnchor, foreignEnd));

        var groups = DynamicGrouper.group(line);

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().members())
            .as("only the dynamic on this line is grouped; the foreign hairpin is dropped")
            .singleElement()
            .isInstanceOf(DynamicGrouper.Member.OfDynamic.class);
    }
}
