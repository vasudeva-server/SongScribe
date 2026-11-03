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

package songscribe.layout;

import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Key;
import songscribe.dom.ProjectedEdit;
import songscribe.dom.StaffElement;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SongFactory.notesAroundKeyChange;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.keyChange;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * {@link InsertionSpacingCalculator#calculateFragmentInsertion} measures the line in the keys the
 * edit leaves it in: a key change already on the line is measured against the key a pasted key
 * change in front of it establishes, as the committed layout measures it.
 */
class InsertionSpacingCalculatorTest extends UnitTest {

    private static final Key LINE_KEY = Key.NO_ACCIDENTALS;
    private static final Key EXISTING_CHANGE_KEY = Key.TWO_SHARPS;
    private static final Key PASTED_CHANGE_KEY = Key.SEVEN_FLATS;

    /** Between the line's first note and the barline its key change stands behind. */
    private static final int INSERTION_INDEX = 1;

    /** Where {@link songscribe.dom.SongFactory#notesAroundKeyChange} puts its key change. */
    private static final int EXISTING_KEY_CHANGE_INDEX = 2;

    @Test
    void testAnExistingKeyChangeIsMeasuredAgainstThePastedKeyInFrontOfIt() {
        var song = buildSong(line -> {
            line.setKey(LINE_KEY);
            notesAroundKeyChange(line, EXISTING_CHANGE_KEY);
        });
        var line = song.getLine(0);
        var existingKeyChange = line.getElement(EXISTING_KEY_CHANGE_INDEX);
        var fragment = List.<StaffElement>of(singleBarline(), keyChange(PASTED_CHANGE_KEY), crotchet());

        var widthBeforePasteSs = ElementColumnBuilder.buildLightweightColumn(existingKeyChange).getWidthSs();
        var projectedColumns = InsertionSpacingCalculator
            .calculateFragmentInsertion(ProjectedEdit.inserting(line, INSERTION_INDEX, fragment), null)
            .projectedColumns();
        var projectedWidthSs = projectedColumns.get(EXISTING_KEY_CHANGE_INDEX + fragment.size()).getWidthSs();

        song.withModification(() -> line.insertRun(INSERTION_INDEX, fragment));

        var committedWidthSs = ElementColumnBuilder.buildLightweightColumn(existingKeyChange).getWidthSs();

        assertThat(committedWidthSs)
            .as("the fixture reaches the case only if the pasted key changes what the existing key change draws")
            .isNotEqualTo(widthBeforePasteSs);
        assertThat(projectedWidthSs).isEqualTo(committedWidthSs);
    }
}
