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

package songscribe.ui.component.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.dom.ElementType;
import songscribe.dom.Lyric;
import songscribe.dom.StaffElement;
import songscribe.ui.edit.GraceModeManager;

/**
 * Tests for structural side-effects in {@link PreviewElementManager}:
 *
 * <ul>
 *   <li>{@code modifyExistingElement} grace-note cleanup (row 33): when the host of a
 *       paired grace note is replaced with a non-pitched type, the grace note is removed.</li>
 *   <li>{@code insertElement} syllable/extend adjustment (row 35): syllabic values on
 *       adjacent elements are corrected after a new element is inserted mid-line.</li>
 * </ul>
 */
class PreviewElementManagerModifyInsertTest extends PreviewElementManagerTestBase {

    private static final int VERSE = 1;
    private static final String SYLLABLE = "glo";
    private static final int GRACE_INDEX = 0;
    private static final int HOST_INDEX = 1;

    /** After a pitched insertion at {@link #HOST_INDEX}, the original host sits one slot later. */
    private static final int DISPLACED_HOST_INDEX = 2;

    /** The two notes {@link #addHyphenatedPair} puts on the line. */
    private static final int HYPHENATED_PAIR_COUNT = 2;

    /** Where the tests insert, splitting the hyphenated pair. */
    private static final int INSERTION_INDEX = 1;

    private static final int PREDECESSOR_INDEX = 0;

    /** After the insertion at {@link #INSERTION_INDEX}, the second syllable sits one slot later. */
    private static final int DISPLACED_SUCCESSOR_INDEX = 2;

    // -----------------------------------------------------------------------
    // Grace-host melisma helpers
    // -----------------------------------------------------------------------

    /**
     * Builds {@code [grace(glissando, SYLLABLE), host minim]} with the automatic
     * grace-host melisma established: {@link Lyric.Extend#START} on the grace and a
     * text-less {@link Lyric.Extend#STOP} carrier on the host.
     *
     * <p>The host is a MINIM so that a CROTCHET inserted between the two is
     * distinguishable from it by type.
     */
    private void addPairedGraceCarryingSyllable() {
        song.withoutMutationTracking(() -> {
            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setGlissando();
            grace.setLyricForVerse(VERSE, Lyric.Syllabic.SINGLE, false, SYLLABLE, Lyric.Extend.NONE);
            line.addElement(grace);
            line.addElement(ElementType.MINIM.newInstance());
            line.syncGraceHostMelisma(GRACE_INDEX);
        });
    }

    /** Builds {@code ["hel" BEGIN, "lo" END]} — one word split across two notes. */
    private void addHyphenatedPair() {
        song.withoutMutationTracking(() -> {
            var note0 = ElementType.CROTCHET.newInstance();
            note0.setLyricForVerse(VERSE, Lyric.Syllabic.BEGIN, false, "hel", Lyric.Extend.NONE);
            line.addElement(note0);

            var note1 = ElementType.CROTCHET.newInstance();
            note1.setLyricForVerse(VERSE, Lyric.Syllabic.END, false, "lo", Lyric.Extend.NONE);
            line.addElement(note1);
        });
    }

    /** Reads the verse lyric at {@code index}, failing the test when there is none. */
    private Lyric requireLyric(int index, int verse) {
        var lyric = line.getElement(index).getLyricForVerse(verse);

        if (lyric == null) {
            throw new AssertionError("expected a verse " + verse + " lyric at index " + index);
        }

        return lyric;
    }

    // -----------------------------------------------------------------------
    // modifyExistingElement — grace-note cleanup (row 33)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ModifyExistingElementGraceNoteCleanup {

        /**
         * Layout:  [grace(CONNECTED glissando)] [host note]
         *
         * Replacing the host note (index 1) with a rest removes the preceding grace note,
         * because a rest cannot be the target of a CONNECTED glissando.
         * Post-modification element count drops by 1 (grace removed).
         */
        @Test
        void testGraceNoteRemovedWhenHostReplacedWithRest() {
            song.withoutMutationTracking(() -> {
                var grace = ElementType.GRACE_QUAVER.newInstance();
                grace.setGlissando();
                line.addElement(grace);
                line.addElement(ElementType.CROTCHET.newInstance()); // host at index 1
            });

            assertThat(line.isHostOfPairedGraceNote(1))
                .as("pre-condition: index 1 is host of paired grace note")
                .isTrue();

            var countBefore = line.elementCount();

            setPreviewElement(ElementType.CROTCHET_REST.newInstance());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setXPosSsMatchesElement(true);
            PreviewElementManager.handleClick(lc);

            assertThat(line.elementCount())
                .as("element count decreases by 1 after grace note removed")
                .isEqualTo(countBefore - 1);

            assertThat(line.getElement(0).getType())
                .as("remaining element is the replacement rest, not the grace note")
                .isEqualTo(ElementType.CROTCHET_REST);
        }

        /**
         * Same replacement as the sibling test above, but the pair carries a lyric: the
         * grace holds the syllable with the automatic melisma established, so the host holds
         * a text-less STOP carrier.
         *
         * <p>{@code setElement} copies the old host's lyrics onto the replacement rest, which
         * has no business holding a melisma carrier. {@code modifyExistingElement} therefore
         * strips the glissando and syncs — converging to teardown — before removing the grace
         * note. Per the plan, hand-back does <em>not</em> apply: the host is simultaneously
         * being replaced by a non-pitched element that cannot carry a lyric, so the syllable
         * dies with the grace.
         *
         * <p>The end state is a bare rest: no orphaned carrier on it, and no empty-text
         * phantom lyric anywhere on the line (an empty lyric still counts as lyric-bearing
         * for backward lyric navigation).
         */
        @Test
        void testMelismaFullyRemovedWhenHostCarryingItIsReplacedWithRest() {
            addPairedGraceCarryingSyllable();

            assertThat(requireLyric(GRACE_INDEX, VERSE).extend())
                .as("pre-condition: the melisma starts on the grace note")
                .isEqualTo(Lyric.Extend.START);
            assertThat(requireLyric(HOST_INDEX, VERSE).extend())
                .as("pre-condition: the host carries the melisma's STOP")
                .isEqualTo(Lyric.Extend.STOP);

            var countBefore = line.elementCount();

            setPreviewElement(ElementType.CROTCHET_REST.newInstance());
            PreviewElementManager.setCurrentXIndex(HOST_INDEX);
            PreviewElementManager.setXPosSsMatchesElement(true);
            PreviewElementManager.handleClick(lc);

            assertThat(line.elementCount())
                .as("element count decreases by 1 after the grace note is removed")
                .isEqualTo(countBefore - 1);

            assertThat(line.getElement(GRACE_INDEX).getType())
                .as("the replacement rest is all that remains of the pair")
                .isEqualTo(ElementType.CROTCHET_REST);

            for (var i = 0; i < line.elementCount(); i++) {
                assertThat(line.getElement(i).getLyricForVerse(VERSE))
                    .as("index %d must carry no verse %d lyric — neither an orphaned carrier "
                        + "nor an empty-text phantom", i, VERSE)
                    .isNull();
            }
        }

        /**
         * Replacing the host note with another pitched note preserves the grace note
         * (the glissando reattaches automatically).
         */
        @Test
        void testGraceNotePreservedWhenHostReplacedWithPitchedNote() {
            song.withoutMutationTracking(() -> {
                var grace = ElementType.GRACE_QUAVER.newInstance();
                grace.setGlissando();
                line.addElement(grace);
                line.addElement(ElementType.CROTCHET.newInstance()); // host at index 1
            });

            var countBefore = line.elementCount();

            setPreviewElement(ElementType.QUAVER.newInstance());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setXPosSsMatchesElement(true);
            PreviewElementManager.handleClick(lc);

            assertThat(line.elementCount())
                .as("element count unchanged when host replaced with pitched note")
                .isEqualTo(countBefore);

            assertThat(line.getElement(0).getType())
                .as("grace note still present at index 0")
                .isEqualTo(ElementType.GRACE_QUAVER);

            assertThat(line.getElement(0).hasGlissando())
                .as("grace note's connected glissando is preserved after pitched replacement")
                .isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // insertElement — syllable/extend adjustment (row 35)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InsertElementSyllableAdjustment {

        /**
         * Layout before insertion:
         *   index 0: note, lyric verse 1 "hel" syllabic=BEGIN
         *   index 1: note, lyric verse 1 "lo"  syllabic=END
         *
         * Insert a new note at index 1 (between them). After insertion:
         *   index 0: "hel" syllabic must become SINGLE (chain broken by bare insert)
         *   index 1: new bare note (no lyric)
         *   index 2: "lo" syllabic must become SINGLE (no predecessor syllable)
         */
        @Test
        void testInsertionBreaksSyllableChainOnPredecessor() {
            song.setLineWidthSs(WIDE_LINE_SS);
            addHyphenatedPair();

            setPreviewElement(ElementType.CROTCHET.newInstance());
            PreviewElementManager.setCurrentXIndex(INSERTION_INDEX);
            PreviewElementManager.setXPosSsMatchesElement(false);
            PreviewElementManager.handleClick(lc);

            // effectiveElementCount excludes the auto-maintained FINAL_DOUBLE_BARLINE terminal
            assertThat(line.effectiveElementCount())
                .as("effective element count increases by the inserted note")
                .isEqualTo(HYPHENATED_PAIR_COUNT + 1);

            assertThat(requireLyric(PREDECESSOR_INDEX, VERSE).syllabic())
                .as("predecessor 'hel' syllabic downgraded to SINGLE by insertion")
                .isEqualTo(Lyric.Syllabic.SINGLE);
            assertThat(requireLyric(DISPLACED_SUCCESSOR_INDEX, VERSE).syllabic())
                .as("successor 'lo' syllabic changed to SINGLE (no preceding syllable)")
                .isEqualTo(Lyric.Syllabic.SINGLE);
        }

        /**
         * The same insertion with grace mode deferring the repairs (#659): grace mode inserts
         * its grace note with mutation tracking suspended, where a repair would be applied but
         * never recorded, so the chains must be left exactly as they were for the pairing
         * bracket to break them there.
         */
        @Test
        void testGraceModeDeferralLeavesTheSyllableChainUntouched() {
            song.setLineWidthSs(WIDE_LINE_SS);
            addHyphenatedPair();

            try (var graceModeMock = mockStatic(GraceModeManager.class)) {
                graceModeMock.when(GraceModeManager::deferInsertionRepairs).thenReturn(true);

                setPreviewElement(ElementType.GRACE_QUAVER.newInstance());
                PreviewElementManager.setCurrentXIndex(INSERTION_INDEX);
                PreviewElementManager.setXPosSsMatchesElement(false);
                PreviewElementManager.handleClick(lc);

                assertThat(line.effectiveElementCount())
                    .as("the grace note was inserted between the two syllables")
                    .isEqualTo(HYPHENATED_PAIR_COUNT + 1);
                assertThat(requireLyric(PREDECESSOR_INDEX, VERSE).syllabic())
                    .as("the predecessor keeps the hyphen for the pairing bracket to break")
                    .isEqualTo(Lyric.Syllabic.BEGIN);
                assertThat(requireLyric(DISPLACED_SUCCESSOR_INDEX, VERSE).syllabic())
                    .as("the displaced successor keeps its word ending")
                    .isEqualTo(Lyric.Syllabic.END);

                // The one call both asks to defer and records the debt, so having been called
                // is what proves the pairing bracket will run the repairs.
                graceModeMock.verify(GraceModeManager::deferInsertionRepairs);
            }
        }

        /**
         * A non-pitched insertion between a paired grace note and its host leaves the
         * glissando with nothing it may legally connect to, so the insertion takes it off.
         */
        @Test
        void testNonPitchedInsertionStripsTheGlissandoItOrphaned() {
            song.setLineWidthSs(WIDE_LINE_SS);
            addPairedGraceCarryingSyllable();

            setPreviewElement(ElementType.GRACE_QUAVER.newInstance());
            PreviewElementManager.setCurrentXIndex(HOST_INDEX);
            PreviewElementManager.setXPosSsMatchesElement(false);
            PreviewElementManager.handleClick(lc);

            assertThat(line.getElement(GRACE_INDEX).hasGlissando())
                .as("the grace note the insertion cut in front of is disconnected")
                .isFalse();
        }

        /**
         * The same insertion with grace mode deferring the repairs (#659). The strip is a
         * tracked modification like any other, so making it here — with tracking suspended —
         * would apply it where undo can never take it back.
         */
        @Test
        void testGraceModeDeferralLeavesTheGlissandoConnected() {
            song.setLineWidthSs(WIDE_LINE_SS);
            addPairedGraceCarryingSyllable();

            try (var graceModeMock = mockStatic(GraceModeManager.class)) {
                graceModeMock.when(GraceModeManager::deferInsertionRepairs).thenReturn(true);

                setPreviewElement(ElementType.GRACE_QUAVER.newInstance());
                PreviewElementManager.setCurrentXIndex(HOST_INDEX);
                PreviewElementManager.setXPosSsMatchesElement(false);
                PreviewElementManager.handleClick(lc);

                assertThat(line.getElement(GRACE_INDEX).hasGlissando())
                    .as("the glissando stays on for the pairing bracket to strip")
                    .isTrue();
            }
        }


        /**
         * Middle syllable promotion: inserting before a MIDDLE syllable promotes it to BEGIN.
         */
        @Test
        void testInsertionPromotesMiddleSyllableToBegin() {
            song.setLineWidthSs(WIDE_LINE_SS);

            song.withoutMutationTracking(() -> {
                var note0 = ElementType.CROTCHET.newInstance();
                note0.setLyricForVerse(1, Lyric.Syllabic.BEGIN, false, "a", Lyric.Extend.NONE);
                line.addElement(note0);

                var note1 = ElementType.CROTCHET.newInstance();
                note1.setLyricForVerse(1, Lyric.Syllabic.MIDDLE, false, "b", Lyric.Extend.NONE);
                line.addElement(note1);
            });

            setPreviewElement(ElementType.CROTCHET.newInstance());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setXPosSsMatchesElement(false);
            PreviewElementManager.handleClick(lc);

            // Successor (was index 1, now at index 2) must be promoted from MIDDLE to BEGIN
            var succ = line.getElement(2);
            var succLyric = succ.getLyricForVerse(1);
            assertThat(succLyric).as("successor still has a lyric").isNotNull();
            //noinspection ConstantValue -- need for NullAway
            assertThat(succLyric == null ? null : succLyric.syllabic())
                .as("MIDDLE syllabic promoted to BEGIN after insertion before it")
                .isEqualTo(Lyric.Syllabic.BEGIN);
        }

        /**
         * Layout before insertion (the automatic grace-host melisma):
         *   index 0: grace note + glissando, verse 1 "glo", Extend.START
         *   index 1: host minim, text-less Extend.STOP carrier
         *
         * <p>Inserting a PITCHED note at index 1 re-targets the glissando, so the inserted
         * note becomes the new host and the melisma re-points onto it (plan decision 3). The
         * original host is displaced to index 2 and is an ordinary note again.
         *
         * <p>Regression guard: {@code adjustExtendsForInsertion} used to merely <em>clear</em>
         * the old host's carrier extend, leaving a {@code Lyric("", NONE, SINGLE)} behind.
         * That empty-text residue still counts as lyric-bearing for lyric navigation — a
         * phantom backward target. The entry must be removed outright, so the displaced host
         * has no verse lyric at all.
         */
        @Test
        void testPitchedInsertionRePointsGraceHostMelismaAndLeavesNoPhantomLyric() {
            song.setLineWidthSs(WIDE_LINE_SS);
            addPairedGraceCarryingSyllable();

            assertThat(requireLyric(GRACE_INDEX, VERSE).extend())
                .as("pre-condition: the melisma starts on the grace note")
                .isEqualTo(Lyric.Extend.START);
            assertThat(requireLyric(HOST_INDEX, VERSE).extend())
                .as("pre-condition: the original host carries the melisma's STOP")
                .isEqualTo(Lyric.Extend.STOP);

            var countBefore = line.effectiveElementCount();

            setPreviewElement(ElementType.CROTCHET.newInstance());
            PreviewElementManager.setCurrentXIndex(HOST_INDEX);
            PreviewElementManager.setXPosSsMatchesElement(false);
            PreviewElementManager.handleClick(lc);

            assertThat(line.effectiveElementCount())
                .as("the pitched note was inserted between the grace and its old host")
                .isEqualTo(countBefore + 1);

            var graceLyric = requireLyric(GRACE_INDEX, VERSE);
            assertThat(graceLyric.text())
                .as("the grace note keeps its syllable")
                .isEqualTo(SYLLABLE);
            assertThat(graceLyric.extend())
                .as("the grace note still starts the melisma")
                .isEqualTo(Lyric.Extend.START);

            assertThat(line.getElement(HOST_INDEX).getType())
                .as("the inserted crotchet occupies the host slot")
                .isEqualTo(ElementType.CROTCHET);

            var newHostLyric = requireLyric(HOST_INDEX, VERSE);
            assertThat(newHostLyric.extend())
                .as("the melisma re-points onto the newly inserted host")
                .isEqualTo(Lyric.Extend.STOP);
            assertThat(newHostLyric.text())
                .as("the new host's STOP is a text-less carrier")
                .isEmpty();
            assertThat(newHostLyric.syllabic())
                .as("a carrier has no syllabic")
                .isNull();

            assertThat(line.getElement(DISPLACED_HOST_INDEX).getType())
                .as("the displaced old host is the original minim")
                .isEqualTo(ElementType.MINIM);
            assertThat(line.getElement(DISPLACED_HOST_INDEX).getLyricForVerse(VERSE))
                .as("the displaced old host has no lyric at all — not an empty-text residue")
                .isNull();
        }
    }
}
