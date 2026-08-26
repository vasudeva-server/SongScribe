/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.undo;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.ElementType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * That an edit is named for what it acted on: the category every element shares, and how many
 * of them there were.
 *
 * <p>Both label methods are pure functions of {@link ElementType}, so every case here is a list
 * of types and the name it must produce — no document, no window, nothing arranged.
 *
 * <p><b>The expected values are built from the same {@code Strings} keys the production code
 * reads</b>, never from the English text, which would duplicate the resource file into the test
 * and pin a wording nobody promised. What each case actually claims is therefore the mapping —
 * that <em>this</em> list of types selects <em>that</em> key, and that the count reaching the
 * key is the number of elements rather than a constant. The plural assertions would pass
 * against a key whose choice pattern was broken in a way that moved both forms together, so
 * each also asserts that the two forms differ.
 *
 * <p>The table is hand-listed and no assertion claims it is complete. The categories are a
 * private enum inside {@code OpNames}, which nothing here can reach, and the domain cannot be
 * derived from {@link ElementType#values()} either: the legacy alias constants sort after
 * {@code KEY_CHANGE} and so answer {@code false} to the ordinal-range predicates the categories
 * are built on. Widening either to let a test see it is surface the production code does not
 * need.
 */
class OpNamesTest extends UnitTest {

    /** The count every plural case uses. Two is the smallest number that is not one. */
    private static final int SEVERAL = 2;

    /**
     * One category, named through a representative type rather than through the private enum.
     *
     * @param description the category's name, which doubles as the test's display name
     * @param deleteKey   the key {@code OpNames.deleteLabel} must select for these types
     * @param addKey      the key {@code OpNames.addLabel} must select for {@link #one}'s type
     * @param one         a single element of the category
     * @param several     more than one, all of the category
     */
    private record CategoryCase(
        String description,
        String deleteKey,
        String addKey,
        List<ElementType> one,
        List<ElementType> several
    ) {

        @Override
        public String toString() {
            return description;
        }
    }

    static Stream<CategoryCase> categoryCases() {
        return Stream.of(
            new CategoryCase("note",
                Strings.ACTION_EDIT_OP_DELETE_NOTE, Strings.ACTION_EDIT_OP_ADD_NOTE,
                List.of(ElementType.CROTCHET),
                List.of(ElementType.CROTCHET, ElementType.QUAVER)),

            new CategoryCase("rest",
                Strings.ACTION_EDIT_OP_DELETE_REST, Strings.ACTION_EDIT_OP_ADD_REST,
                List.of(ElementType.CROTCHET_REST),
                List.of(ElementType.CROTCHET_REST, ElementType.QUAVER_REST)),

            new CategoryCase("barline",
                Strings.ACTION_EDIT_OP_DELETE_BARLINE, Strings.ACTION_EDIT_OP_ADD_BARLINE,
                List.of(ElementType.SINGLE_BARLINE),
                List.of(ElementType.SINGLE_BARLINE, ElementType.DOUBLE_BARLINE)),

            new CategoryCase("repeat",
                Strings.ACTION_EDIT_OP_DELETE_REPEAT, Strings.ACTION_EDIT_OP_ADD_REPEAT,
                List.of(ElementType.REPEAT_LEFT),
                List.of(ElementType.REPEAT_LEFT, ElementType.REPEAT_RIGHT)),

            new CategoryCase("breath mark",
                Strings.ACTION_EDIT_OP_DELETE_BREATH_MARK, Strings.ACTION_EDIT_OP_ADD_BREATH_MARK,
                List.of(ElementType.BREATH_MARK),
                List.of(ElementType.BREATH_MARK, ElementType.BREATH_MARK)),

            new CategoryCase("key change",
                Strings.ACTION_EDIT_OP_DELETE_KEY_CHANGE, Strings.ACTION_EDIT_OP_ADD_KEY,
                List.of(ElementType.KEY_CHANGE),
                List.of(ElementType.KEY_CHANGE, ElementType.KEY_CHANGE))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("categoryCases")
    void testDeleteLabelNamesElementsByCategoryAndCount(CategoryCase testCase) {
        var singular = OpNames.deleteLabel(testCase.one());
        var plural = OpNames.deleteLabel(testCase.several());

        assertThat(singular).isEqualTo(Strings.get(testCase.deleteKey(), 1));
        assertThat(plural).isEqualTo(Strings.get(testCase.deleteKey(), SEVERAL));

        // The count has to reach the choice pattern, and the pattern has to distinguish on it.
        // Comparing the resolved forms is what catches a pattern whose branches say the same
        // thing, which both assertions above would accept.
        assertThat(plural).isNotEqualTo(singular);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("categoryCases")
    void testAddLabelNamesAnInsertionByItsCategory(CategoryCase testCase) {
        var type = testCase.one().getFirst();

        assertThat(OpNames.addLabel(type)).isEqualTo(Strings.get(testCase.addKey()));
    }

    @Test
    void testDeleteLabelFoldsAGraceNoteIntoItsHostsCategory() {
        var types = List.of(ElementType.CROTCHET, ElementType.GRACE_QUAVER);

        assertThat(OpNames.deleteLabel(types))
            .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_NOTE, types.size()));
    }

    @Test
    void testDeleteLabelIsGenericForAMixOfCategories() {
        // The pair a key change's deletion actually produces: Line.effectiveBegin drags the
        // barline the key change sits behind out with it, and the two are in different
        // categories.
        var types = List.of(ElementType.SINGLE_BARLINE, ElementType.KEY_CHANGE);

        assertThat(OpNames.deleteLabel(types))
            .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_ELEMENTS));
    }
}
