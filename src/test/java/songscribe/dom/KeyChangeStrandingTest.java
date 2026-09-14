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

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.StaffElementRun.EffectiveRange;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SongFactory.notesAroundKeyChange;
import static songscribe.dom.StaffElementFactory.crotchet;

/**
 * What an edit that moves a key strands on the lines it reaches, computed before the edit
 * commits.
 *
 * <p>Every case runs against one song whose key changes each change the key where they stand,
 * so nothing is stranded until an edit moves a key: a first line in the document key, an
 * inheriting line changing to another key, an inheriting line changing back, a line with a key of
 * its own holding a key change that the wrong reach would strand, and a last inheriting line. See
 * {@code docs/key-changes.md}, <em>A mid-line change never restates the key already in effect</em>.
 */
class KeyChangeStrandingTest extends UnitTest {

    /** The key line 0 establishes, and the key the third line's key change returns to. */
    private static final Key DOCUMENT_KEY = Key.NO_ACCIDENTALS;

    /** The key the second line's key change establishes, and the key the edits move to. */
    private static final Key CHANGED_KEY = Key.TWO_SHARPS;

    /** The key the fourth line establishes for itself. */
    private static final Key OWN_KEY = Key.ONE_SHARP;

    private static final int FIRST_LINE = 0;
    private static final int SECOND_LINE = 1;
    private static final int THIRD_LINE = 2;

    /** Where {@link SongFactory#notesAroundKeyChange} puts the barline in front of its key change. */
    private static final int BARLINE_INDEX = 1;

    /** Where {@link SongFactory#notesAroundKeyChange} puts its key change. */
    private static final int KEY_CHANGE_INDEX = 2;

    /** The range a stranded key change goes with: itself and the barline in front of it. */
    private static final List<EffectiveRange> KEY_CHANGE_PAIR =
        List.of(new EffectiveRange(BARLINE_INDEX, KEY_CHANGE_INDEX));

    /**
     * One expected line of a stranding.
     *
     * @param lineIndex the line's index
     * @param ranges    the ranges the stranding must report on it
     */
    private record StrandedAt(int lineIndex, List<EffectiveRange> ranges) {}

    /**
     * One stranding query, and what it must report.
     *
     * @param description the case, which doubles as the parameterized display name
     * @param stranding   the query, asked of the song
     * @param expected    the lines reported, in song order
     */
    private record StrandingCase(
        String description, Function<Song, KeyChangeStranding> stranding, List<StrandedAt> expected) {

        @Override
        public String toString() {
            return description;
        }
    }

    private Song song;

    @BeforeAll
    void buildSongWithKeyChanges() {
        song = buildSong(
            line -> {
                line.setKey(DOCUMENT_KEY);
                line.addElement(crotchet());
            },
            line -> notesAroundKeyChange(line, CHANGED_KEY),
            line -> notesAroundKeyChange(line, DOCUMENT_KEY),
            line -> {
                line.setKey(OWN_KEY);
                notesAroundKeyChange(line, CHANGED_KEY);
            },
            line -> line.addElement(crotchet()));
    }

    static Stream<StrandingCase> strandingCases() {
        var strandedOnlyOnTheSecondLine = List.of(
            new StrandedAt(FIRST_LINE, List.of()),
            new StrandedAt(SECOND_LINE, KEY_CHANGE_PAIR),
            new StrandedAt(THIRD_LINE, List.of()));

        return Stream.of(
            new StrandingCase(
                "a line's own key changing strands what restates it on the lines inheriting from it, "
                    + "and nothing past a line with its own key",
                song -> KeyChangeStranding.ofLineKeyChange(song.getLine(FIRST_LINE), CHANGED_KEY),
                strandedOnlyOnTheSecondLine),
            new StrandingCase(
                "a mid-line edit strands what restates the key it leaves the line in on the lines "
                    + "inheriting from it, and nothing past a line with its own key",
                song -> KeyChangeStranding.ofMidLineEdit(song.getLine(FIRST_LINE), List.of(), CHANGED_KEY),
                strandedOnlyOnTheSecondLine),
            new StrandingCase(
                "deleting a line strands what restates the key the line before it leaves off in",
                song -> KeyChangeStranding.ofLineDeletion(song.getLine(SECOND_LINE)),
                List.of(new StrandedAt(THIRD_LINE, KEY_CHANGE_PAIR))),
            new StrandingCase(
                "deleting the first line moves no key and strands nothing",
                song -> KeyChangeStranding.ofLineDeletion(song.getLine(FIRST_LINE)),
                List.of(
                    new StrandedAt(SECOND_LINE, List.of()),
                    new StrandedAt(THIRD_LINE, List.of()))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("strandingCases")
    void testAStrandingReportsWhatTheEditLeavesRestatingTheKeyOnEveryLineItReaches(StrandingCase testCase) {
        var stranding = testCase.stranding().apply(song);

        assertThat(stranding.lines()).containsExactlyElementsOf(
            testCase.expected().stream()
                .map(at -> new KeyChangeStranding.Stranded(song.getLine(at.lineIndex()), at.ranges()))
                .toList());
    }
}
