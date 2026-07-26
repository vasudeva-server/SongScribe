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

package songscribe.ui.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.StaffElement;
import songscribe.message.MessageCenter;

/**
 * Covers the interaction between the automatic grace-host melisma and the chain-repair
 * helpers that run when an extender chain is broken or retroactively extended.
 * <p>
 * Line layout used throughout: [normal(0), grace(1)+CONNECTED, host(2), normal(3)],
 * with a melisma chain from element 0 running through the pair.
 */
class LyricEditorGraceMelismaTest extends LyricEditorTestSupport {

    private static final int VERSE = 1;

    private Line line;
    private StaffElement chainRoot;
    private StaffElement grace;
    private StaffElement host;
    private StaffElement noteAfter;

    @BeforeEach
    void setUpLine() {
        chainRoot = crotchet();
        chainRoot.setLyricForVerse(VERSE, Lyric.Syllabic.SINGLE, false, "Do", Lyric.Extend.START);

        grace = ElementType.GRACE_QUAVER.newInstance();
        grace.setGlissando();
        grace.setLyricForVerse(VERSE, null, false, null, Lyric.Extend.CONTINUE);

        host = crotchet();
        host.setLyricForVerse(VERSE, null, false, null, Lyric.Extend.STOP);

        noteAfter = crotchet();

        line = song.getLine(0);
        song.withoutMutationTracking(() -> {
            line.addElement(chainRoot);
            line.addElement(grace);
            line.addElement(host);
            line.addElement(noteAfter);
        });

        messageCenterMock = mockStatic(MessageCenter.class);
    }

    @AfterEach
    void tearDown() {
        if (messageCenterMock != null) {
            messageCenterMock.close();
            messageCenterMock = null;
        }
    }

    /** Fails the test when the element has no verse lyric, rather than dereferencing null. */
    private static Lyric requireLyric(StaffElement element) {
        var lyric = element.getLyricForVerse(VERSE);

        if (lyric == null) {
            throw new AssertionError("expected a verse " + VERSE + " lyric on " + element.getType());
        }

        return lyric;
    }

    /**
     * Typing over a carrier on a paired grace breaks the chain that passed through the pair,
     * which clears the host's carrier. The pair's own melisma must be re-established, not left
     * as an orphaned {@code START} on the grace.
     */
    @Test
    void testChainBreakOnPairedGraceRebuildsTheHostCarrier() {
        var editor = new LyricEditor(score, line, grace);
        editor.setText("Re");
        editor.attachListeners();
        fireTab(editor);

        var graceLyric = requireLyric(grace);
        assertThat(graceLyric.text()).isEqualTo("Re");
        assertThat(graceLyric.extend()).isEqualTo(Lyric.Extend.START);

        var hostLyric = requireLyric(host);
        assertThat(hostLyric.isCarrier()).as("the pair's melisma must still close on the host").isTrue();
        assertThat(hostLyric.extend()).isEqualTo(Lyric.Extend.STOP);

        // The old chain root no longer reaches the grace, so its melisma collapses.
        assertThat(requireLyric(chainRoot).extend()).isEqualTo(Lyric.Extend.NONE);
    }

    /**
     * The plain establish case, driven through the real commit path: a paired grace with no lyric
     * of its own and no melisma anywhere near it. Committing a syllable onto it must produce the
     * pair's melisma on its own — no chain break is involved here, so the outcome is the work of
     * the commit path's sync call alone.
     */
    @Test
    void testCommittingASyllableOntoAVirginPairedGraceEstablishesTheMelisma() {
        // Strip the shared chain fixture: this case starts from a pair with no lyric at all.
        chainRoot.lyrics.clear();
        grace.lyrics.clear();
        host.lyrics.clear();

        var editor = new LyricEditor(score, line, grace);
        editor.setText("Re");
        editor.attachListeners();
        fireTab(editor);

        var graceLyric = requireLyric(grace);
        assertThat(graceLyric.text()).isEqualTo("Re");
        assertThat(graceLyric.syllabic()).isEqualTo(Lyric.Syllabic.SINGLE);
        assertThat(graceLyric.extend())
            .as("a syllable on a paired grace starts the pair's melisma").isEqualTo(Lyric.Extend.START);

        var hostLyric = requireLyric(host);
        assertThat(hostLyric.isCarrier()).as("the host closes the melisma as a text-less carrier").isTrue();
        assertThat(hostLyric.text()).isEmpty();
        assertThat(hostLyric.extend()).isEqualTo(Lyric.Extend.STOP);

        // The melisma spans the pair and nothing else, so it must not reach past the host.
        assertThat(noteAfter.getLyricForVerse(VERSE))
            .as("the melisma must stop at the host").isNull();
    }

    /**
     * The host carrier is an ordinary melisma carrier to the backward scan, never a syllable
     * target: {@code _} on the element after the host extends the pair's chain through it
     * rather than stopping at it.
     */
    @Test
    void testUnderscoreAfterHostExtendsTheChainThroughThePair() {
        // Give the grace the pair's syllable so the melisma starts there.
        song.withoutMutationTracking(() -> {
            grace.setLyricForVerse(VERSE, Lyric.Syllabic.SINGLE, false, "Re", Lyric.Extend.START);
            chainRoot.setLyricForVerse(VERSE, Lyric.Syllabic.SINGLE, false, "Do", Lyric.Extend.NONE);
        });

        var editor = new LyricEditor(score, line, noteAfter);
        editor.attachListeners();
        fireUnderscore(editor);

        var graceLyric = requireLyric(grace);
        assertThat(graceLyric.text()).as("the pair's syllable stays on the grace").isEqualTo("Re");
        assertThat(graceLyric.extend()).isEqualTo(Lyric.Extend.START);

        assertThat(requireLyric(host).extend())
            .as("the host now carries the chain onward").isEqualTo(Lyric.Extend.CONTINUE);
        assertThat(requireLyric(noteAfter).extend()).isEqualTo(Lyric.Extend.STOP);
    }

    /**
     * {@code _} typed after a syllable on a paired grace runs the melisma past the host, whose
     * own {@code STOP} — established by the syllable's commit — must give way to a
     * {@code CONTINUE} so the chain closes on the next eligible element instead.
     */
    @Test
    void testUnderscoreOnAPairedGraceSyllableExtendsThroughTheHost() {
        // Strip the shared chain fixture: this case starts from a pair with no lyric at all.
        chainRoot.lyrics.clear();
        grace.lyrics.clear();
        host.lyrics.clear();

        var editor = new LyricEditor(score, line, grace);
        editor.setText("Re");
        editor.setCaretPosition(editor.getText().length());
        editor.attachListeners();
        fireUnderscore(editor);

        var graceLyric = requireLyric(grace);
        assertThat(graceLyric.text()).isEqualTo("Re");
        assertThat(graceLyric.extend()).isEqualTo(Lyric.Extend.START);

        assertThat(requireLyric(host).extend())
            .as("the host carries the chain past the pair").isEqualTo(Lyric.Extend.CONTINUE);
        assertThat(requireLyric(noteAfter).extend()).isEqualTo(Lyric.Extend.STOP);
    }
}
