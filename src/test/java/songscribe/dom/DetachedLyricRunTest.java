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
import static org.assertj.core.api.Assertions.assertThatCode;
import static songscribe.dom.StaffElementFactory.graceQuaver;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * A {@link LyricRun} belonging to no {@link Line} — chiefly
 * {@link LyricRun#endDanglingChains()}, the repair a clipboard fragment gets when the
 * elements around it are no longer there.
 */
class DetachedLyricRunTest extends UnitTest {

    private static final int VERSE = Lyric.FIRST_VERSE;
    private static final int SECOND_VERSE = Lyric.FIRST_VERSE + 1;

    private static StaffElement note() {
        return new StaffElement(ElementType.QUAVER);
    }

    /** A note carrying a text-bearing syllable in {@link #VERSE}. */
    private static StaffElement syllable(String text, Lyric.Syllabic syllabic, Lyric.Extend extend) {
        return syllable(VERSE, text, syllabic, extend, false);
    }

    private static StaffElement syllable(int verse, String text, Lyric.Syllabic syllabic,
            Lyric.Extend extend, boolean compound) {
        var element = note();
        element.lyrics.add(new Lyric(verse, text, extend, syllabic, compound));
        return element;
    }

    /** A note carrying a text-less melisma carrier in {@link #VERSE}. */
    private static StaffElement carrier(Lyric.Extend extend) {
        var element = note();
        element.lyrics.add(new Lyric(VERSE, "", extend, null, false));
        return element;
    }

    private static void endDanglingChains(StaffElement... elements) {
        new DetachedLyricRun(List.of(elements)).endDanglingChains();
    }

    /** A grace note carrying the connecting glissando that pairs it with the next note. */
    private static StaffElement pairedGraceNote() {
        var grace = graceQuaver();
        grace.setGlissando();
        return grace;
    }

    /** The {@code verse} lyric on {@code element}, failing the test when there is none. */
    private static Lyric lyricOf(StaffElement element, int verse) {
        var lyric = element.getLyricForVerse(verse);
        assertThat(lyric).as("expected a verse %d lyric".formatted(verse)).isNotNull();
        return lyric;
    }

    // -----------------------------------------------------------------------
    // What a detached run answers about its own elements
    // -----------------------------------------------------------------------

    @Test
    void testPairedGraceNoteIsRecognizedAndBoundsChecked() {
        var run = new DetachedLyricRun(List.of(pairedGraceNote(), note()));

        assertThat(run.isPairedGraceNote(0)).isTrue();
        assertThat(run.isPairedGraceNote(1)).as("an ordinary note is not a grace note").isFalse();
        assertThat(run.isPairedGraceNote(-1)).as("before the run").isFalse();
        assertThat(run.isPairedGraceNote(run.elementCount())).as("past the run").isFalse();
    }

    @Test
    void testEachVerseIsRepairedIndependently() {
        // Verse 1's word runs across both notes; verse 2's hangs off the first one alone.
        var first = syllable(VERSE, "a", Lyric.Syllabic.BEGIN, Lyric.Extend.NONE, false);
        first.lyrics.add(
            new Lyric(SECOND_VERSE, "x", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
        var second = syllable(VERSE, "b", Lyric.Syllabic.END, Lyric.Extend.NONE, false);

        endDanglingChains(first, second);

        assertThat(lyricOf(first, VERSE).syllabic())
            .as("verse 1's word is whole inside the run")
            .isEqualTo(Lyric.Syllabic.BEGIN);
        assertThat(lyricOf(second, VERSE).syllabic()).isEqualTo(Lyric.Syllabic.END);
        assertThat(lyricOf(first, SECOND_VERSE).syllabic())
            .as("verse 2 has no second syllable in the run")
            .isEqualTo(Lyric.Syllabic.SINGLE);
    }

    // -----------------------------------------------------------------------
    // Syllabic chains — the hyphen that would reach a syllable outside the run
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SyllabicChains {

        @Test
        void testTrailingBeginEndsItsWord() {
            // "A-" copied out of "A-mi": nothing follows it now, so it is a whole word.
            var first = syllable("A", Lyric.Syllabic.BEGIN, Lyric.Extend.NONE);

            endDanglingChains(first);

            assertThat(lyricOf(first, VERSE).syllabic()).isEqualTo(Lyric.Syllabic.SINGLE);
        }

        @Test
        void testTrailingMiddleBecomesEndOfTheWordItContinues() {
            // "a-" + "-b-" with the third syllable left behind: the word ends at "b".
            var first = syllable("a", Lyric.Syllabic.BEGIN, Lyric.Extend.NONE);
            var second = syllable("b", Lyric.Syllabic.MIDDLE, Lyric.Extend.NONE);

            endDanglingChains(first, second);

            assertThat(lyricOf(first, VERSE).syllabic())
                .as("the word still begins inside the run")
                .isEqualTo(Lyric.Syllabic.BEGIN);
            assertThat(lyricOf(second, VERSE).syllabic()).isEqualTo(Lyric.Syllabic.END);
        }

        @Test
        void testTrailingCompoundMarkerIsCleared() {
            // A compound boundary joins this syllable to the next one, and there is no next
            // one. Lyric's own invariant forbids compound on anything but BEGIN/MIDDLE.
            var first = syllable(VERSE, "sun", Lyric.Syllabic.BEGIN, Lyric.Extend.NONE, true);

            endDanglingChains(first);

            var lyric = lyricOf(first, VERSE);
            assertThat(lyric.syllabic()).isEqualTo(Lyric.Syllabic.SINGLE);
            assertThat(lyric.compound()).isFalse();
        }

        @Test
        void testLeadingMiddleBeginsTheWordItNowStarts() {
            // "-b-" + "-c" lifted out of "a-b-c": "b" no longer has "a" in front of it.
            var second = syllable("b", Lyric.Syllabic.MIDDLE, Lyric.Extend.NONE);
            var third = syllable("c", Lyric.Syllabic.END, Lyric.Extend.NONE);

            endDanglingChains(second, third);

            assertThat(lyricOf(second, VERSE).syllabic()).isEqualTo(Lyric.Syllabic.BEGIN);
            assertThat(lyricOf(third, VERSE).syllabic())
                .as("the word still ends inside the run")
                .isEqualTo(Lyric.Syllabic.END);
        }

        @Test
        void testLeadingEndBecomesAWholeWord() {
            // "mi" copied out of "A-mi", alone: it ends a word that no longer begins.
            var second = syllable("mi", Lyric.Syllabic.END, Lyric.Extend.NONE);

            endDanglingChains(second);

            assertThat(lyricOf(second, VERSE).syllabic()).isEqualTo(Lyric.Syllabic.SINGLE);
        }

        @Test
        void testWholeWordInsideTheRunIsUntouched() {
            var first = syllable("a", Lyric.Syllabic.BEGIN, Lyric.Extend.NONE);
            var second = syllable("b", Lyric.Syllabic.MIDDLE, Lyric.Extend.NONE);
            var third = syllable("c", Lyric.Syllabic.END, Lyric.Extend.NONE);

            endDanglingChains(first, second, third);

            assertThat(lyricOf(first, VERSE).syllabic()).isEqualTo(Lyric.Syllabic.BEGIN);
            assertThat(lyricOf(second, VERSE).syllabic()).isEqualTo(Lyric.Syllabic.MIDDLE);
            assertThat(lyricOf(third, VERSE).syllabic()).isEqualTo(Lyric.Syllabic.END);
        }

        @Test
        void testSyllableEndsItsWordWhenOnlyAMelismaCarrierFollowsIt() {
            // A carrier has no syllable of its own, so the hyphen "la-" opens has nothing to
            // reach even though a lyric-bearing element follows.
            var first = syllable("la", Lyric.Syllabic.BEGIN, Lyric.Extend.START);
            var host = carrier(Lyric.Extend.STOP);

            endDanglingChains(first, host);

            assertThat(lyricOf(first, VERSE).syllabic()).isEqualTo(Lyric.Syllabic.SINGLE);
            assertThat(lyricOf(first, VERSE).extend())
                .as("the melisma is closed inside the run, so it survives")
                .isEqualTo(Lyric.Extend.START);
            assertThat(lyricOf(host, VERSE).extend()).isEqualTo(Lyric.Extend.STOP);
        }

        @Test
        void testRepairIsIdempotent() {
            var first = syllable("b", Lyric.Syllabic.MIDDLE, Lyric.Extend.NONE);
            var second = syllable("c", Lyric.Syllabic.MIDDLE, Lyric.Extend.NONE);

            endDanglingChains(first, second);
            var afterFirstPass = List.of(lyricOf(first, VERSE), lyricOf(second, VERSE));
            endDanglingChains(first, second);

            assertThat(List.of(lyricOf(first, VERSE), lyricOf(second, VERSE)))
                .isEqualTo(afterFirstPass);
            assertThat(lyricOf(first, VERSE).syllabic()).isEqualTo(Lyric.Syllabic.BEGIN);
            assertThat(lyricOf(second, VERSE).syllabic()).isEqualTo(Lyric.Syllabic.END);
        }
    }

    // -----------------------------------------------------------------------
    // Melisma chains — the extender running past either end of the run
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MelismaChains {

        @Test
        void testLeadingOrphanCarriersAreDropped() {
            // The middle of a melisma: every carrier here is sustained by a START that was
            // left behind, so none of them extends anything.
            var middle = carrier(Lyric.Extend.CONTINUE);
            var last = carrier(Lyric.Extend.STOP);
            var next = syllable("next", Lyric.Syllabic.SINGLE, Lyric.Extend.NONE);

            endDanglingChains(middle, last, next);

            assertThat(middle.getLyricForVerse(VERSE))
                .as("an orphan carrier must be removed, not blanked — an empty lyric still "
                    + "counts as lyric-bearing")
                .isNull();
            assertThat(last.getLyricForVerse(VERSE)).isNull();
            assertThat(lyricOf(next, VERSE).text()).isEqualTo("next");
        }

        @Test
        void testDroppingTheOrphanCarriersRelettersTheSyllableTheyExposed() {
            // Same shape as above, but the syllable behind the carriers was mid-word: the
            // word it continued began before the run, so once the carriers go it begins one.
            var middle = carrier(Lyric.Extend.CONTINUE);
            var last = carrier(Lyric.Extend.STOP);
            var second = syllable("ex", Lyric.Syllabic.MIDDLE, Lyric.Extend.NONE);
            var third = syllable("it", Lyric.Syllabic.END, Lyric.Extend.NONE);

            endDanglingChains(middle, last, second, third);

            assertThat(middle.getLyricForVerse(VERSE)).isNull();
            assertThat(last.getLyricForVerse(VERSE)).isNull();
            assertThat(lyricOf(second, VERSE).syllabic()).isEqualTo(Lyric.Syllabic.BEGIN);
            assertThat(lyricOf(third, VERSE).syllabic())
                .as("the rest of the word is still inside the run")
                .isEqualTo(Lyric.Syllabic.END);
        }

        @Test
        void testTailClosesItsMelismaAndEndsItsWordInOnePass() {
            // "la-" opens both a hyphen to a syllable outside the run and a melisma whose
            // STOP is outside it too, so both tail repairs have work to do.
            var start = syllable("la", Lyric.Syllabic.BEGIN, Lyric.Extend.START);
            var middle = carrier(Lyric.Extend.CONTINUE);

            endDanglingChains(start, middle);

            var startLyric = lyricOf(start, VERSE);
            assertThat(startLyric.syllabic())
                .as("the hyphen had nothing to reach — a carrier holds no syllable")
                .isEqualTo(Lyric.Syllabic.SINGLE);
            assertThat(startLyric.extend())
                .as("the melisma now ends inside the run, so it survives")
                .isEqualTo(Lyric.Extend.START);
            assertThat(lyricOf(middle, VERSE).extend()).isEqualTo(Lyric.Extend.STOP);
        }

        @Test
        void testAMelismaStartingAfterTheOrphansSurvives() {
            var orphan = carrier(Lyric.Extend.STOP);
            var start = syllable("la", Lyric.Syllabic.SINGLE, Lyric.Extend.START);
            var stop = carrier(Lyric.Extend.STOP);

            endDanglingChains(orphan, start, stop);

            assertThat(orphan.getLyricForVerse(VERSE)).isNull();
            assertThat(lyricOf(start, VERSE).extend()).isEqualTo(Lyric.Extend.START);
            assertThat(lyricOf(stop, VERSE).extend()).isEqualTo(Lyric.Extend.STOP);
        }

        @Test
        void testTrailingStartIsCleared() {
            // A melisma needs something to extend over, and its every carrier was left behind.
            var start = syllable("la", Lyric.Syllabic.SINGLE, Lyric.Extend.START);

            endDanglingChains(start);

            assertThat(lyricOf(start, VERSE).extend()).isEqualTo(Lyric.Extend.NONE);
            assertThat(lyricOf(start, VERSE).text()).isEqualTo("la");
        }

        @Test
        void testTrailingContinueClosesTheChain() {
            var start = syllable("la", Lyric.Syllabic.SINGLE, Lyric.Extend.START);
            var middle = carrier(Lyric.Extend.CONTINUE);

            endDanglingChains(start, middle);

            assertThat(lyricOf(start, VERSE).extend()).isEqualTo(Lyric.Extend.START);
            assertThat(lyricOf(middle, VERSE).extend())
                .as("the last carrier in the run becomes the melisma's terminus")
                .isEqualTo(Lyric.Extend.STOP);
        }

        @Test
        void testCompleteMelismaIsUntouched() {
            var start = syllable("la", Lyric.Syllabic.SINGLE, Lyric.Extend.START);
            var middle = carrier(Lyric.Extend.CONTINUE);
            var stop = carrier(Lyric.Extend.STOP);

            endDanglingChains(start, middle, stop);

            assertThat(lyricOf(start, VERSE).extend()).isEqualTo(Lyric.Extend.START);
            assertThat(lyricOf(middle, VERSE).extend()).isEqualTo(Lyric.Extend.CONTINUE);
            assertThat(lyricOf(stop, VERSE).extend()).isEqualTo(Lyric.Extend.STOP);
        }

        @Test
        void testRepairIsIdempotent() {
            // Orphan carriers at the head and an unclosed melisma at the tail, so both halves
            // have work to do on the first pass and none on the second. Unlike the syllabic
            // repairs, the head repair removes lyrics outright rather than rewriting them.
            var orphan = carrier(Lyric.Extend.CONTINUE);
            var orphanStop = carrier(Lyric.Extend.STOP);
            var start = syllable("la", Lyric.Syllabic.SINGLE, Lyric.Extend.START);
            var tail = carrier(Lyric.Extend.CONTINUE);

            endDanglingChains(orphan, orphanStop, start, tail);
            var afterFirstPass = List.of(lyricOf(start, VERSE), lyricOf(tail, VERSE));
            endDanglingChains(orphan, orphanStop, start, tail);

            assertThat(orphan.getLyricForVerse(VERSE)).isNull();
            assertThat(orphanStop.getLyricForVerse(VERSE)).isNull();
            assertThat(List.of(lyricOf(start, VERSE), lyricOf(tail, VERSE)))
                .isEqualTo(afterFirstPass);
            assertThat(lyricOf(tail, VERSE).extend()).isEqualTo(Lyric.Extend.STOP);
        }
    }

    // -----------------------------------------------------------------------
    // Runs with nothing to repair
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class NothingToRepair {

        @Test
        void testRunWithNoLyricsIsLeftAlone() {
            var first = note();
            var second = note();

            endDanglingChains(first, second);

            assertThat(first.getLyrics()).isEmpty();
            assertThat(second.getLyrics()).isEmpty();
        }

        @Test
        void testEmptyRunIsHarmless() {
            // Every scan in the repair walks from one end of the run to the other, so an
            // off-by-one in a loop bound would show up here as an exception.
            var run = new DetachedLyricRun(List.<StaffElement>of());

            assertThatCode(run::endDanglingChains).doesNotThrowAnyException();
        }
    }
}
