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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.graceQuaver;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class LineGraceNotePairingTest extends UnitTest {

    private static final int VERSE = 1;
    private static final int SECOND_VERSE = 2;
    private static final int GRACE = 0;
    private static final int HOST = 1;
    private static final String SYLLABLE = "glo";
    private static final String OTHER_SYLLABLE = "ry";

    /** Indices in {@link #graceHostPairAfterANote()}: an ordinary note, then the pair. */
    private static final int LEADING_NOTE = 0;
    private static final int PAIRED_GRACE = 1;
    private static final int PAIRED_HOST = 2;

    /** The three syllables of one hyphenated word, spread across those three indices. */
    private static final String WORD_FIRST_SYLLABLE = "hal";
    private static final String WORD_MIDDLE_SYLLABLE = "le";
    private static final String WORD_LAST_SYLLABLE = "lu";

    /** Builds a line: [grace+CONNECTED glissando, host crotchet]. Index 0 is a paired grace. */
    private Line pairedGraceLine() {
        var line = detachedLine();
        var grace = graceQuaver();
        grace.setGlissando();
        line.addElement(grace);
        line.addElement(crotchet());
        return line;
    }

    /**
     * Builds a line: [crotchet, grace+CONNECTED glissando, host crotchet], so a lyric on the
     * leading note can take part in the syllabic chain that runs across the pair.
     */
    private Line graceHostPairAfterANote() {
        var line = detachedLine();
        line.addElement(crotchet());
        var grace = graceQuaver();
        grace.setGlissando();
        line.addElement(grace);
        line.addElement(crotchet());
        return line;
    }

    /** Builds a line: [grace (no glissando), crotchet]. Index 0 is an unpaired grace. */
    private Line unpairedGraceLine() {
        var line = detachedLine();
        line.addElement(graceQuaver());
        line.addElement(crotchet());
        return line;
    }

    @Test
    void testPairedGraceHostAtIndex1ReturnsTrue() {
        var line = pairedGraceLine();
        assertThat(line.isHostOfPairedGraceNote(1)).isTrue();
    }

    @Test
    void testPairedGraceItselfAtIndex0ReturnsFalse() {
        var line = pairedGraceLine();
        assertThat(line.isHostOfPairedGraceNote(0)).isFalse();
    }

    @Test
    void testUnpairedGraceHostAtIndex1ReturnsFalse() {
        var line = unpairedGraceLine();
        assertThat(line.isHostOfPairedGraceNote(1)).isFalse();
    }

    @Test
    void testNonGraceAtPrecedingIndexReturnsFalse() {
        var line = detachedLine();
        line.addElement(crotchet());
        line.addElement(crotchet());
        assertThat(line.isHostOfPairedGraceNote(1)).isFalse();
    }

    @Test
    void testIndexZeroReturnsFalse() {
        var line = pairedGraceLine();
        assertThat(line.isHostOfPairedGraceNote(0)).isFalse();
    }

    // -----------------------------------------------------------------------
    // isInsideGraceHostPair (Row 39)
    // -----------------------------------------------------------------------

    @Test
    void testIsInsideGraceHostPairAtPairedGraceIndexReturnsTrue() {
        // Index 0 is the paired grace note itself — clicking on it is inside the pair.
        var line = pairedGraceLine();
        assertThat(line.isInsideGraceHostPair(0)).isTrue();
    }

    @Test
    void testIsInsideGraceHostPairAtHostIndexReturnsTrue() {
        // Index 1 is the host — index-1 (the grace at 0) is a paired grace,
        // so inserting between the grace and the host is blocked.
        var line = pairedGraceLine();
        assertThat(line.isInsideGraceHostPair(1)).isTrue();
    }

    @Test
    void testIsInsideGraceHostPairAtIndexTwoReturnsFalse() {
        // Build: [grace+CONNECTED, host, crotchet]. Index 2 is past the pair.
        var line = pairedGraceLine();
        line.addElement(crotchet());
        assertThat(line.isInsideGraceHostPair(2)).isFalse();
    }

    @Test
    void testIsInsideGraceHostPairWithUnpairedGraceReturnsFalse() {
        // An unpaired grace (no CONNECTED glissando) does not block insertion.
        var line = unpairedGraceLine();
        assertThat(line.isInsideGraceHostPair(0)).isFalse();
        assertThat(line.isInsideGraceHostPair(1)).isFalse();
    }

    // -----------------------------------------------------------------------
    // isPairedGraceNote (Row 40)
    // -----------------------------------------------------------------------

    @Test
    void testIsPairedGraceNoteWithConnectedGlissandoReturnsTrue() {
        // Direct test: a grace note carrying a CONNECTED glissando is a paired grace.
        var line = pairedGraceLine();
        assertThat(line.isPairedGraceNote(0)).isTrue();
    }

    @Test
    void testIsPairedGraceNoteWithoutGlissandoReturnsFalse() {
        // A grace note without any glissando is not paired.
        var line = unpairedGraceLine();
        assertThat(line.isPairedGraceNote(0)).isFalse();
    }

    @Test
    void testIsPairedGraceNoteOnNonGraceReturnsFalse() {
        // A normal note (not a grace note) is never a paired grace.
        var line = pairedGraceLine();
        assertThat(line.isPairedGraceNote(1)).isFalse();
    }

    @Test
    void testIsPairedGraceNoteAtNegativeIndexReturnsFalse() {
        var line = pairedGraceLine();
        assertThat(line.isPairedGraceNote(-1)).isFalse();
    }

    @Test
    void testIsPairedGraceNoteAtOutOfBoundsIndexReturnsFalse() {
        var line = pairedGraceLine();
        assertThat(line.isPairedGraceNote(line.elementCount())).isFalse();
    }

    // -----------------------------------------------------------------------
    // precedingGraceNoteIndex (Row 41)
    // -----------------------------------------------------------------------

    @Test
    void testPrecedingGraceNoteIndexWhenPrecedingIsGraceReturnsIndex() {
        // Index 1 has a grace note at 0 immediately before it.
        var line = pairedGraceLine();
        assertThat(line.precedingGraceNoteIndex(1)).isEqualTo(0);
    }

    @Test
    void testPrecedingGraceNoteIndexWhenPrecedingIsNoteReturnsMinusOne() {
        // Build: [crotchet, crotchet]. Index 1 has a non-grace preceding it.
        var line = detachedLine();
        line.addElement(crotchet());
        line.addElement(crotchet());
        assertThat(line.precedingGraceNoteIndex(1)).isEqualTo(-1);
    }

    @Test
    void testPrecedingGraceNoteIndexAtIndexZeroReturnsMinusOne() {
        // No element precedes index 0.
        var line = pairedGraceLine();
        assertThat(line.precedingGraceNoteIndex(0)).isEqualTo(-1);
    }

    // -----------------------------------------------------------------------
    // syncGraceHostMelisma
    // -----------------------------------------------------------------------

    /** Writes a plain (no-melisma) syllable directly — detachedLine suspends mutation tracking. */
    private static void setSyllable(Line line, int index, int verse, String text) {
        setSyllable(line, index, verse, text, Lyric.Syllabic.SINGLE);
    }

    /** {@link #setSyllable} for a syllable that takes a position within a hyphenated word. */
    private static void setSyllable(Line line, int index, int verse, String text,
            Lyric.Syllabic syllabic) {
        line.getElement(index).setLyricForVerse(verse, syllabic, false, text, Lyric.Extend.NONE);
    }

    private static void setCarrier(Line line, int index, int verse, Lyric.Extend extend) {
        line.getElement(index).setLyricForVerse(verse, null, false, null, extend);
    }

    private static @Nullable Lyric lyricAt(Line line, int index, int verse) {
        return line.getElement(index).getLyricForVerse(verse);
    }

    /** {@link #lyricAt} for assertions that dereference the lyric — fails when there is none. */
    private static Lyric requireLyric(Line line, int index, int verse) {
        var lyric = lyricAt(line, index, verse);

        assertThat(lyric).as("expected a verse " + verse + " lyric at index " + index).isNotNull();

        return lyric;
    }

    @Test
    void testSyncEstablishesMelismaOnPairedGraceCarryingSyllable() {
        var line = pairedGraceLine();
        setSyllable(line, GRACE, VERSE, SYLLABLE);

        line.syncGraceHostMelisma(GRACE);

        assertThat(requireLyric(line, GRACE, VERSE).extend()).isEqualTo(Lyric.Extend.START);
        assertThat(requireLyric(line, GRACE, VERSE).text()).isEqualTo(SYLLABLE);

        var hostLyric = requireLyric(line, HOST, VERSE);
        assertThat(hostLyric.extend()).isEqualTo(Lyric.Extend.STOP);
        assertThat(hostLyric.text()).isEmpty();
        assertThat(hostLyric.syllabic()).isNull();
    }

    @Test
    void testSyncReplacesTheHostsOwnLyric() {
        // The host may not carry a syllable of its own — the grace's syllable wins.
        var line = pairedGraceLine();
        setSyllable(line, GRACE, VERSE, SYLLABLE);
        setSyllable(line, HOST, VERSE, OTHER_SYLLABLE);

        line.syncGraceHostMelisma(GRACE);

        var hostLyric = requireLyric(line, HOST, VERSE);
        assertThat(hostLyric.extend()).isEqualTo(Lyric.Extend.STOP);
        assertThat(hostLyric.text()).isEmpty();
    }

    @Test
    void testSyncHealsTheGracesSyllabicWhenItNoLongerContinuesIntoTheHost() {
        // "hal-le-lu" spread across [note, grace, host]. Establishing the melisma replaces
        // the host's "lu" with a carrier, so the grace's MIDDLE — which asserts that another
        // syllable of the same word follows — is now a lie and has to be repaired.
        //
        // The repair looks backwards from the host, and the nearest lyric-bearing element
        // behind it is always the grace itself (the establish branch requires the grace to
        // carry a syllable), so the grace is what gets healed. The leading note still
        // continues into the grace, so the corrected value is END, not SINGLE.
        var line = graceHostPairAfterANote();
        setSyllable(line, LEADING_NOTE, VERSE, WORD_FIRST_SYLLABLE, Lyric.Syllabic.BEGIN);
        setSyllable(line, PAIRED_GRACE, VERSE, WORD_MIDDLE_SYLLABLE, Lyric.Syllabic.MIDDLE);
        setSyllable(line, PAIRED_HOST, VERSE, WORD_LAST_SYLLABLE, Lyric.Syllabic.END);

        line.syncGraceHostMelisma(PAIRED_GRACE);

        var graceLyric = requireLyric(line, PAIRED_GRACE, VERSE);
        assertThat(graceLyric.syllabic()).isEqualTo(Lyric.Syllabic.END);
        assertThat(graceLyric.text()).isEqualTo(WORD_MIDDLE_SYLLABLE);
        assertThat(graceLyric.extend()).isEqualTo(Lyric.Extend.START);

        // The leading note's BEGIN still describes a real continuation into the grace.
        assertThat(requireLyric(line, LEADING_NOTE, VERSE).syllabic()).isEqualTo(Lyric.Syllabic.BEGIN);

        var hostLyric = requireLyric(line, PAIRED_HOST, VERSE);
        assertThat(hostLyric.extend()).isEqualTo(Lyric.Extend.STOP);
        assertThat(hostLyric.syllabic()).isNull();
    }

    @Test
    void testSyncIsIdempotent() {
        var line = pairedGraceLine();
        setSyllable(line, GRACE, VERSE, SYLLABLE);

        line.syncGraceHostMelisma(GRACE);
        var afterFirstSync = lyricAt(line, GRACE, VERSE);
        line.syncGraceHostMelisma(GRACE);
        line.syncGraceHostMelisma(GRACE);

        assertThat(lyricAt(line, GRACE, VERSE)).isEqualTo(afterFirstSync);
        assertThat(requireLyric(line, HOST, VERSE).extend()).isEqualTo(Lyric.Extend.STOP);
    }

    @Test
    void testSyncTearsDownMelismaWhenTheGraceIsUnpaired() {
        var line = pairedGraceLine();
        setSyllable(line, GRACE, VERSE, SYLLABLE);
        line.syncGraceHostMelisma(GRACE);

        // Deleting the slide decoration un-pairs the two; both elements survive.
        line.getElement(GRACE).removeSlide();
        line.syncGraceHostMelisma(GRACE);

        assertThat(requireLyric(line, GRACE, VERSE).extend()).isEqualTo(Lyric.Extend.NONE);
        assertThat(requireLyric(line, GRACE, VERSE).text()).isEqualTo(SYLLABLE);

        // The carrier must be gone outright: an empty-texted residue would still count as
        // lyric-bearing for backward lyric navigation.
        assertThat(lyricAt(line, HOST, VERSE)).isNull();
    }

    @Test
    void testTeardownIsIdempotent() {
        var line = unpairedGraceLine();
        setSyllable(line, GRACE, VERSE, SYLLABLE);

        line.syncGraceHostMelisma(GRACE);
        line.syncGraceHostMelisma(GRACE);

        assertThat(requireLyric(line, GRACE, VERSE).extend()).isEqualTo(Lyric.Extend.NONE);
        assertThat(lyricAt(line, HOST, VERSE)).isNull();
    }

    @Test
    void testSyncDoesNothingWhenTheGraceHasNoSyllable() {
        var line = pairedGraceLine();

        line.syncGraceHostMelisma(GRACE);

        assertThat(lyricAt(line, GRACE, VERSE)).isNull();
        assertThat(lyricAt(line, HOST, VERSE)).isNull();
    }

    @Test
    void testSyncRemovesAnOrphanedHostCarrier() {
        // The grace has no lyric at all, so a STOP on the host cannot belong to any chain.
        var line = pairedGraceLine();
        setCarrier(line, HOST, VERSE, Lyric.Extend.STOP);

        line.syncGraceHostMelisma(GRACE);

        assertThat(lyricAt(line, HOST, VERSE)).isNull();
    }

    @Test
    void testSyncLeavesAChainPassingThroughThePairAlone() {
        // The grace is itself a carrier, so the STOP on the host terminates a chain that
        // began before the grace — not this pair's melisma to tear down.
        var line = pairedGraceLine();
        setCarrier(line, GRACE, VERSE, Lyric.Extend.CONTINUE);
        setCarrier(line, HOST, VERSE, Lyric.Extend.STOP);

        line.syncGraceHostMelisma(GRACE);

        assertThat(requireLyric(line, GRACE, VERSE).extend()).isEqualTo(Lyric.Extend.CONTINUE);
        assertThat(requireLyric(line, HOST, VERSE).extend()).isEqualTo(Lyric.Extend.STOP);
    }

    @Test
    void testSyncLeavesAHostThatCarriesTheChainOnward() {
        // A longer melisma already runs across the host, so the host keeps its CONTINUE.
        var line = pairedGraceLine();
        line.addElement(crotchet());
        setSyllable(line, GRACE, VERSE, SYLLABLE);
        setCarrier(line, HOST, VERSE, Lyric.Extend.CONTINUE);
        setCarrier(line, HOST + 1, VERSE, Lyric.Extend.STOP);

        line.syncGraceHostMelisma(GRACE);

        assertThat(requireLyric(line, GRACE, VERSE).extend()).isEqualTo(Lyric.Extend.START);
        assertThat(requireLyric(line, HOST, VERSE).extend()).isEqualTo(Lyric.Extend.CONTINUE);
        assertThat(requireLyric(line, HOST + 1, VERSE).extend()).isEqualTo(Lyric.Extend.STOP);
    }

    @Test
    void testSyncHandlesEachVerseIndependently() {
        var line = pairedGraceLine();
        setSyllable(line, GRACE, VERSE, SYLLABLE);
        setSyllable(line, GRACE, SECOND_VERSE, OTHER_SYLLABLE);

        line.syncGraceHostMelisma(GRACE);

        assertThat(requireLyric(line, GRACE, VERSE).extend()).isEqualTo(Lyric.Extend.START);
        assertThat(requireLyric(line, GRACE, SECOND_VERSE).extend()).isEqualTo(Lyric.Extend.START);
        assertThat(requireLyric(line, HOST, VERSE).extend()).isEqualTo(Lyric.Extend.STOP);
        assertThat(requireLyric(line, HOST, SECOND_VERSE).extend()).isEqualTo(Lyric.Extend.STOP);
    }

    @Test
    void testSyncTearsDownOnlyTheVerseWithoutASyllable() {
        var line = pairedGraceLine();
        setSyllable(line, GRACE, VERSE, SYLLABLE);
        setSyllable(line, GRACE, SECOND_VERSE, OTHER_SYLLABLE);
        line.syncGraceHostMelisma(GRACE);

        line.removeLyricForVerse(GRACE, SECOND_VERSE);
        line.syncGraceHostMelisma(GRACE);

        assertThat(requireLyric(line, HOST, VERSE).extend()).isEqualTo(Lyric.Extend.STOP);
        assertThat(lyricAt(line, HOST, SECOND_VERSE)).isNull();
    }

    @Test
    void testSyncAtTheLastElementIsANoOp() {
        var line = detachedLine();
        var grace = graceQuaver();
        grace.setGlissando();
        line.addElement(grace);
        setSyllable(line, GRACE, VERSE, SYLLABLE);

        line.syncGraceHostMelisma(GRACE);

        assertThat(requireLyric(line, GRACE, VERSE).extend()).isEqualTo(Lyric.Extend.NONE);
    }

    // -----------------------------------------------------------------------
    // removeLyricForVerse / transferLyricForVerse
    // -----------------------------------------------------------------------

    @Test
    void testRemoveLyricForVerseRemovesTheEntryOutright() {
        var line = pairedGraceLine();
        setSyllable(line, HOST, VERSE, SYLLABLE);

        line.removeLyricForVerse(HOST, VERSE);

        assertThat(lyricAt(line, HOST, VERSE)).isNull();
    }

    @Test
    void testRemoveLyricForVerseWithNoLyricEmitsNoModification() {
        // The point of the no-lyric guard is not the (already absent) state but the spurious
        // ElementModification that would otherwise be recorded, so the state assertion alone
        // cannot catch its removal. Build the line with tracking suspended, then un-suspend
        // it so any emission reaches the song.
        var songMock = minimalSongMock();
        var line = new Line(songMock);
        var grace = graceQuaver();
        grace.setGlissando();
        line.addElement(grace);
        line.addElement(crotchet());

        when(songMock.isMutationTrackingSuspended()).thenReturn(false);
        when(songMock.isModifying()).thenReturn(true);

        line.removeLyricForVerse(HOST, VERSE);

        assertThat(lyricAt(line, HOST, VERSE)).isNull();
        verify(songMock, never()).applyChange(any(), any());

        // Removing a lyric that does exist must still record one, so the assertion above is
        // not vacuously true.
        setSyllable(line, HOST, VERSE, SYLLABLE);

        line.removeLyricForVerse(HOST, VERSE);

        verify(songMock).applyChange(any(), any());
        assertThat(lyricAt(line, HOST, VERSE)).isNull();
    }

    @Test
    void testTransferMovesTheSyllableAndClearsTheSource() {
        // #599's host→grace transfer: pairing hands the host's syllable to the grace.
        var line = pairedGraceLine();
        setSyllable(line, HOST, VERSE, SYLLABLE);

        line.transferLyricForVerse(HOST, GRACE, VERSE);

        assertThat(requireLyric(line, GRACE, VERSE).text()).isEqualTo(SYLLABLE);
        assertThat(requireLyric(line, GRACE, VERSE).extend()).isEqualTo(Lyric.Extend.NONE);
        assertThat(lyricAt(line, HOST, VERSE)).isNull();
    }

    @Test
    void testTransferIgnoresACarrierSource() {
        var line = pairedGraceLine();
        setCarrier(line, GRACE, VERSE, Lyric.Extend.CONTINUE);
        setSyllable(line, HOST, VERSE, SYLLABLE);

        line.transferLyricForVerse(GRACE, HOST, VERSE);

        assertThat(requireLyric(line, GRACE, VERSE).extend()).isEqualTo(Lyric.Extend.CONTINUE);
        assertThat(requireLyric(line, HOST, VERSE).text()).isEqualTo(SYLLABLE);
    }

    @Test
    void testHandBackRoundTripReturnsTheSyllableToTheHost() {
        // Pair a note carrying a lyric, then delete the grace alone: the syllable returns
        // to the host unchanged, and the host's STOP carrier is gone.
        var line = pairedGraceLine();
        setSyllable(line, HOST, VERSE, SYLLABLE);
        line.transferLyricForVerse(HOST, GRACE, VERSE);
        line.syncGraceHostMelisma(GRACE);

        line.transferLyricForVerse(GRACE, HOST, VERSE);
        line.removeElement(GRACE);

        var handedBack = requireLyric(line, GRACE, VERSE);
        assertThat(handedBack.text()).isEqualTo(SYLLABLE);
        assertThat(handedBack.extend()).isEqualTo(Lyric.Extend.NONE);
        assertThat(handedBack.syllabic()).isEqualTo(Lyric.Syllabic.SINGLE);
        assertThat(line.elementCount()).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // repairGraceHostMelismas — load-path normalization of imported pairs
    // -----------------------------------------------------------------------

    @Test
    void testRepairMovesTheHostsSyllableToTheGraceAndEstablishesTheMelisma() {
        // A file written before the melisma was automatic can put the syllable on the host.
        var line = pairedGraceLine();
        setSyllable(line, HOST, VERSE, SYLLABLE);

        line.repairGraceHostMelismas();

        var graceLyric = requireLyric(line, GRACE, VERSE);
        assertThat(graceLyric.text()).isEqualTo(SYLLABLE);
        assertThat(graceLyric.extend()).isEqualTo(Lyric.Extend.START);
        assertThat(requireLyric(line, HOST, VERSE).extend()).isEqualTo(Lyric.Extend.STOP);
        assertThat(requireLyric(line, HOST, VERSE).text()).isEmpty();
    }

    @Test
    void testRepairEstablishesTheMelismaForAGraceSyllableThatHasNone() {
        var line = pairedGraceLine();
        setSyllable(line, GRACE, VERSE, SYLLABLE);

        line.repairGraceHostMelismas();

        assertThat(requireLyric(line, GRACE, VERSE).extend()).isEqualTo(Lyric.Extend.START);
        assertThat(requireLyric(line, HOST, VERSE).extend()).isEqualTo(Lyric.Extend.STOP);
    }

    @Test
    void testRepairKeepsTheGracesSyllableWhenBothCarryOne() {
        // Nowhere left to put the host's syllable once the invariant holds — the grace's wins.
        var line = pairedGraceLine();
        setSyllable(line, GRACE, VERSE, SYLLABLE);
        setSyllable(line, HOST, VERSE, OTHER_SYLLABLE);

        line.repairGraceHostMelismas();

        var graceLyric = requireLyric(line, GRACE, VERSE);
        assertThat(graceLyric.text()).isEqualTo(SYLLABLE);

        // Rewriting the host is only half the repair — the surviving syllable has to open
        // the melisma, or the pair loads with a STOP that nothing starts.
        assertThat(graceLyric.extend()).isEqualTo(Lyric.Extend.START);

        var hostLyric = requireLyric(line, HOST, VERSE);
        assertThat(hostLyric.text()).isEmpty();
        assertThat(hostLyric.extend()).isEqualTo(Lyric.Extend.STOP);
    }

    @Test
    void testRepairIsStableAcrossASecondLoad() {
        var line = pairedGraceLine();
        setSyllable(line, HOST, VERSE, SYLLABLE);

        line.repairGraceHostMelismas();
        var graceAfterFirstRepair = lyricAt(line, GRACE, VERSE);
        var hostAfterFirstRepair = lyricAt(line, HOST, VERSE);
        line.repairGraceHostMelismas();

        assertThat(lyricAt(line, GRACE, VERSE)).isEqualTo(graceAfterFirstRepair);
        assertThat(lyricAt(line, HOST, VERSE)).isEqualTo(hostAfterFirstRepair);
    }

    @Test
    void testRepairMovesEachVersesHostSyllableIndependently() {
        var line = pairedGraceLine();
        setSyllable(line, GRACE, VERSE, SYLLABLE);
        setSyllable(line, HOST, SECOND_VERSE, OTHER_SYLLABLE);

        line.repairGraceHostMelismas();

        assertThat(requireLyric(line, GRACE, VERSE).text()).isEqualTo(SYLLABLE);
        assertThat(requireLyric(line, GRACE, SECOND_VERSE).text()).isEqualTo(OTHER_SYLLABLE);
        assertThat(requireLyric(line, GRACE, SECOND_VERSE).extend()).isEqualTo(Lyric.Extend.START);
        assertThat(requireLyric(line, HOST, VERSE).extend()).isEqualTo(Lyric.Extend.STOP);
        assertThat(requireLyric(line, HOST, SECOND_VERSE).extend()).isEqualTo(Lyric.Extend.STOP);
    }

    @Test
    void testRepairLeavesAMelismaThatMerelyPassesThroughThePair() {
        // A CONTINUE on the grace belongs to a melisma that began before the pair, so the
        // host's syllable is not the pair's to move and the chain is left intact.
        var line = pairedGraceLine();
        setCarrier(line, GRACE, VERSE, Lyric.Extend.CONTINUE);
        setCarrier(line, HOST, VERSE, Lyric.Extend.CONTINUE);

        line.repairGraceHostMelismas();

        assertThat(requireLyric(line, GRACE, VERSE).extend()).isEqualTo(Lyric.Extend.CONTINUE);
        assertThat(requireLyric(line, HOST, VERSE).extend()).isEqualTo(Lyric.Extend.CONTINUE);
    }

    @Test
    void testRepairLeavesAnUnpairedGraceAlone() {
        var line = unpairedGraceLine();
        setSyllable(line, HOST, VERSE, SYLLABLE);

        line.repairGraceHostMelismas();

        assertThat(lyricAt(line, GRACE, VERSE)).isNull();
        assertThat(requireLyric(line, HOST, VERSE).text()).isEqualTo(SYLLABLE);
        assertThat(requireLyric(line, HOST, VERSE).extend()).isEqualTo(Lyric.Extend.NONE);
    }

    @Test
    void testRepairLeavesAPairedGraceWithNoLyricsAlone() {
        var line = pairedGraceLine();

        line.repairGraceHostMelismas();

        assertThat(lyricAt(line, GRACE, VERSE)).isNull();
        assertThat(lyricAt(line, HOST, VERSE)).isNull();
    }

    @Test
    void testRepairIgnoresATrailingGraceWithNoHost() {
        var line = detachedLine();
        var grace = graceQuaver();
        grace.setGlissando();
        line.addElement(grace);
        setSyllable(line, GRACE, VERSE, SYLLABLE);

        line.repairGraceHostMelismas();

        assertThat(requireLyric(line, GRACE, VERSE).extend()).isEqualTo(Lyric.Extend.NONE);
    }
}
