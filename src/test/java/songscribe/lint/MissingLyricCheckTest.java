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

package songscribe.lint;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.Lyric;
import songscribe.dom.Song;
import songscribe.dom.SongFactory;
import songscribe.dom.StaffElement;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.repeatRight;
import static songscribe.dom.StaffElementFactory.singleBarline;
import static songscribe.lint.LintSongs.carrier;
import static songscribe.lint.LintSongs.pairedGraceNote;
import static songscribe.lint.LintSongs.sung;

/**
 * Which slots {@link MissingLyricCheck} reports: a slot no syllable of the active script is sung
 * on, and no slot that a melisma, a word, a tie or a paired grace note covers, or that only
 * another script leaves bare.
 */
class MissingLyricCheckTest extends UnitTest {

    /** A song and the marks the check must report on it, in song order. */
    private record Scenario(Song song, List<LintMark.MissingLyric> expected) {
    }

    private record LyricCase(String description, Supplier<Scenario> scenario) {

        @Override
        public String toString() {
            return description;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("lyricCases")
    void testReportsExactlyTheSlotsNoSyllableIsSungOn(LyricCase testCase) {
        var scenario = testCase.scenario().get();

        var violations = new MissingLyricCheck().check(scenario.song());

        assertThat(violations).extracting(Violation::mark).containsExactlyElementsOf(scenario.expected());
        assertThat(violations).allSatisfy(violation -> {
            assertThat(violation.heading()).isEqualTo(Strings.get(Strings.LINT_LYRICS_MISSING));
            assertThat(violation.description()).isEqualTo(Strings.get(Strings.LINT_REASON_LYRIC_MISSING));
        });
    }

    static Stream<LyricCase> lyricCases() {
        return Stream.of(
            new LyricCase("a slot with no lyric and no open chain is flagged with its script",
                MissingLyricCheckTest::bareSlotAfterSyllable),
            new LyricCase("the inner notes of a melisma are not flagged", MissingLyricCheckTest::melisma),
            new LyricCase("the inner notes of a hyphen run are not flagged", MissingLyricCheckTest::hyphenRun),
            new LyricCase("a chain across a line break is not flagged", MissingLyricCheckTest::chainAcrossLineBreak),
            new LyricCase("the host of a paired grace note is not flagged",
                MissingLyricCheckTest::pairedGraceNoteHost),
            new LyricCase("the note a tie from the note before it reaches is not flagged",
                MissingLyricCheckTest::tiedNote),
            new LyricCase("rests and barlines are not flagged", MissingLyricCheckTest::restAndBarline),
            new LyricCase("with the Latin script active, a slot only the Bengali script leaves bare is not flagged",
                MissingLyricCheckTest::secondScriptMissingOnOneNote),
            new LyricCase("with the Bengali script active, a slot the Bengali lyrics leave bare is flagged for Bengali",
                MissingLyricCheckTest::bengaliActiveBengaliBare),
            new LyricCase("with the Bengali script active, a slot only the Latin lyrics leave bare is not flagged",
                MissingLyricCheckTest::bengaliActiveLatinBare),
            new LyricCase("a song with no lyrics at all flags every slot",
                MissingLyricCheckTest::noLyricsAtAll),
            new LyricCase("a song with lyrics only in the other script flags every slot",
                MissingLyricCheckTest::lyricsOnlyInOtherScript),
            new LyricCase("a hyphenated word stops at a repeat", MissingLyricCheckTest::hyphenRunStopsAtRepeat),
            new LyricCase("a melisma stops at a repeat", MissingLyricCheckTest::melismaStopsAtRepeat),
            new LyricCase("an unpaired grace note is a slot", MissingLyricCheckTest::unpairedGraceNote),
            new LyricCase("a melisma never closed covers to the end of the line",
                MissingLyricCheckTest::unclosedMelisma)
        );
    }

    private static Scenario bengaliActiveBengaliBare() {
        var complete = sung(Song.Script.BENGALI, "da", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE);
        var onlyLatin = sung(Song.Script.LATIN, "la", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE);
        var song = songOfLine(complete, onlyLatin);
        song.setActiveScript(Song.Script.BENGALI);

        return new Scenario(song, List.of(new LintMark.MissingLyric(onlyLatin, Song.Script.BENGALI)));
    }

    private static Scenario bengaliActiveLatinBare() {
        var complete = sung(Song.Script.LATIN, "la", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE);
        complete.putLyric(new Lyric(Song.Script.BENGALI, "da", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));

        var onlyBengali = sung(Song.Script.BENGALI, "de", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE);
        var song = songOfLine(complete, onlyBengali);
        song.setActiveScript(Song.Script.BENGALI);

        return new Scenario(song, List.of());
    }

    private static Scenario noLyricsAtAll() {
        var first = crotchet();
        var second = crotchet();

        return new Scenario(songOfLine(first, second),
                            List.of(new LintMark.MissingLyric(first, Song.Script.LATIN),
                                    new LintMark.MissingLyric(second, Song.Script.LATIN)));
    }

    private static Scenario lyricsOnlyInOtherScript() {
        var first = sung(Song.Script.BENGALI, "da", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE);
        var second = sung(Song.Script.BENGALI, "de", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE);

        return new Scenario(songOfLine(first, second),
                            List.of(new LintMark.MissingLyric(first, Song.Script.LATIN),
                                    new LintMark.MissingLyric(second, Song.Script.LATIN)));
    }

    private static Scenario hyphenRunStopsAtRepeat() {
        var afterRepeat = crotchet();
        var song = songOfLine(
            sung(Song.Script.LATIN, "ha", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN),
            crotchet(),
            repeatRight(),
            afterRepeat);

        return new Scenario(song, List.of(new LintMark.MissingLyric(afterRepeat, Song.Script.LATIN)));
    }

    private static Scenario melismaStopsAtRepeat() {
        var afterRepeat = crotchet();
        var song = songOfLine(
            sung(Song.Script.LATIN, "la", Lyric.Extend.START, Lyric.Syllabic.SINGLE),
            crotchet(),
            repeatRight(),
            afterRepeat);

        return new Scenario(song, List.of(new LintMark.MissingLyric(afterRepeat, Song.Script.LATIN)));
    }

    private static Scenario unpairedGraceNote() {
        var grace = graceQuaver();
        var song = songOfLine(grace, sung(Song.Script.LATIN, "la", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE));

        return new Scenario(song, List.of(new LintMark.MissingLyric(grace, Song.Script.LATIN)));
    }

    private static Scenario unclosedMelisma() {
        return new Scenario(
            songOfLine(
                sung(Song.Script.LATIN, "la", Lyric.Extend.START, Lyric.Syllabic.SINGLE),
                crotchet(),
                carrier(Song.Script.LATIN, Lyric.Extend.CONTINUE),
                crotchet()),
            List.of());
    }

    private static Scenario bareSlotAfterSyllable() {
        var bare = crotchet();
        var song = songOfLine(sung(Song.Script.LATIN, "la", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE), bare);

        return new Scenario(song, List.of(new LintMark.MissingLyric(bare, Song.Script.LATIN)));
    }

    private static Scenario melisma() {
        return new Scenario(
            songOfLine(
                sung(Song.Script.LATIN, "la", Lyric.Extend.START, Lyric.Syllabic.SINGLE),
                crotchet(),
                carrier(Song.Script.LATIN, Lyric.Extend.STOP)),
            List.of());
    }

    private static Scenario hyphenRun() {
        return new Scenario(
            songOfLine(
                sung(Song.Script.LATIN, "ha", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN),
                crotchet(),
                sung(Song.Script.LATIN, "le", Lyric.Extend.NONE, Lyric.Syllabic.END)),
            List.of());
    }

    private static Scenario chainAcrossLineBreak() {
        var firstLine = List.<StaffElement>of(sung(Song.Script.LATIN, "ha", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN));
        var secondLine = List.<StaffElement>of(crotchet(), sung(Song.Script.LATIN, "le", Lyric.Extend.NONE, Lyric.Syllabic.END));

        return new Scenario(songOfLines(firstLine, secondLine), List.of());
    }

    private static Scenario pairedGraceNoteHost() {
        return new Scenario(songOfLine(pairedGraceNote(Song.Script.LATIN, "la"), crotchet()), List.of());
    }

    private static Scenario tiedNote() {
        var anchor = sung(Song.Script.LATIN, "la", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE);
        var song = songOfLine(anchor, crotchet());

        song.withoutMutationTracking(() -> song.getLine(0).addTie(anchor));

        return new Scenario(song, List.of());
    }

    private static Scenario restAndBarline() {
        return new Scenario(
            songOfLine(
                sung(Song.Script.LATIN, "la", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE),
                crotchetRest(),
                singleBarline(),
                sung(Song.Script.LATIN, "le", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE)),
            List.of());
    }

    private static Scenario secondScriptMissingOnOneNote() {
        var complete = sung(Song.Script.LATIN, "la", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE);
        complete.putLyric(new Lyric(Song.Script.BENGALI, "da", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));

        var onlyFirstScript = sung(Song.Script.LATIN, "le", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE);

        return new Scenario(songOfLine(complete, onlyFirstScript), List.of());
    }

    private static Song songOfLine(StaffElement... elements) {
        return songOfLines(List.of(elements));
    }

    @SafeVarargs
    private static Song songOfLines(List<StaffElement>... lines) {
        var builders = Stream.of(lines)
                             .map(elements -> (SongFactory.LineBuilder) line -> elements.forEach(line::addElement))
                             .toArray(SongFactory.LineBuilder[]::new);

        return SongFactory.buildSong(builders);
    }
}
