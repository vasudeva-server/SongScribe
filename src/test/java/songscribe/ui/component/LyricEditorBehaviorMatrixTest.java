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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;

import javax.swing.JPanel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.message.MessageCenter;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementModification;
import songscribe.music.Lyric;
import songscribe.music.Song;
import songscribe.ui.layout.LyricRenderMetrics;

class LyricEditorBehaviorMatrixTest extends LyricEditorTestSupport {

    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final LyricRenderMetrics LYRIC_METRICS =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0);

    private Song song;
    private Score score;

    @BeforeEach
    void setUp() {
        song = new Song();
        score = mock(Score.class);
        when(score.getLyricRenderMetrics()).thenReturn(LYRIC_METRICS);
        when(score.getSong()).thenReturn(song);
        when(score.getLineComponent(anyInt())).thenReturn(null);
        when(score.getLayout()).thenReturn(new BorderLayout());
    }

    @AfterEach
    void tearDown() {
        if (messageCenterMock != null) {
            messageCenterMock.close();
            messageCenterMock = null;
        }
    }

    @Test
    void w1_printableCharWithinCapInserts() throws Exception {
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        editor.setText("a");
        editor.attachListeners();

        editor.getDocument().insertString(editor.getDocument().getLength(), "b", null);

        assertThat(editor.getText()).isEqualTo("ab");
    }

    @Test
    void w2_printableCharExceedingCapBeepsAndRejects() throws Exception {
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        editor.setText("a".repeat(LyricEditor.MAX_LENGTH_CHARS));
        editor.attachListeners();

        var toolkitMock = mock(Toolkit.class);

        try (var toolkitStatic = mockStatic(Toolkit.class)) {
            toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);

            editor.getDocument().insertString(editor.getDocument().getLength(), "x", null);

            verify(toolkitMock).beep();
        }

        assertThat(editor.getText()).hasSize(LyricEditor.MAX_LENGTH_CHARS);
    }

    @Test
    void w3_pasteWithNewlineSilentlyDropped() throws Exception {
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        editor.setText("");
        editor.attachListeners();

        editor.getDocument().insertString(editor.getDocument().getLength(), "abc\ndef", null);

        assertThat(editor.getText()).isEqualTo("abcdef");
    }

    // -----------------------------------------------------------------------
    // K1–K2: Tab
    // -----------------------------------------------------------------------

    @Nested
    class TabKey {

        @Test
        void k1_changedTextCommitsWordFinalAndAdvances() {
            var element = crotchet();
            element.setLyricForVerse(1, Lyric.Syllabic.BEGIN, true, "ho", Lyric.Extend.START);
            var nextNote = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));
            song.withoutMutationTracking(() -> line.addElement(nextNote));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, element);
            editor.setText("ha");

            fireTab(editor);

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).hasSize(1);
            assertThat(((ElementModification) notification.getMutations().get(0)).fields())
                .containsExactly(ElementField.LYRIC);
            assertThat(element.getMainLyric())
                .extracting(Lyric::syllabic, Lyric::compound, Lyric::extend)
                .containsExactly(Lyric.Syllabic.SINGLE, false, Lyric.Extend.NONE);

            var captor = ArgumentCaptor.forClass(LyricEditor.class);
            verify(score, atLeastOnce()).addOverlay(captor.capture());
            assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(nextNote);
        }

        @Test
        void k2_unchangedTextPreservesShapeAndAdvances() {
            var element = crotchet();
            element.setLyricForVerse(1, Lyric.Syllabic.BEGIN, false, "ho", Lyric.Extend.NONE);
            var nextNote = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));
            song.withoutMutationTracking(() -> line.addElement(nextNote));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, element);
            assertThat(editor.getText()).isEqualTo("ho");

            fireTab(editor);

            verifyNoSongDidChange();
            assertThat(element.getMainLyric())
                .extracting(Lyric::syllabic, Lyric::compound, Lyric::extend)
                .containsExactly(Lyric.Syllabic.BEGIN, false, Lyric.Extend.NONE);

            var captor = ArgumentCaptor.forClass(LyricEditor.class);
            verify(score, atLeastOnce()).addOverlay(captor.capture());
            assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(nextNote);
        }
    }

    // -----------------------------------------------------------------------
    // K3: Shift+Tab
    // -----------------------------------------------------------------------

    @Nested
    class ShiftTabKey {

        @Test
        void k3_changedTextCommitsWordFinalAndRetreats() {
            var prevNote = crotchet();
            prevNote.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "do", Lyric.Extend.NONE);
            var element = crotchet();
            element.setLyricForVerse(1, Lyric.Syllabic.END, false, "re", Lyric.Extend.NONE);
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(prevNote));
            song.withoutMutationTracking(() -> line.addElement(element));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, element);
            editor.setText("mi");

            fireShiftTab(editor);

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).hasSize(1);
            assertThat(element.getMainLyric())
                .extracting(Lyric::syllabic, Lyric::compound, Lyric::extend)
                .containsExactly(Lyric.Syllabic.SINGLE, false, Lyric.Extend.NONE);

            var captor = ArgumentCaptor.forClass(LyricEditor.class);
            verify(score, atLeastOnce()).addOverlay(captor.capture());
            assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(prevNote);
        }
    }

    // -----------------------------------------------------------------------
    // K4: Enter
    // -----------------------------------------------------------------------

    @Nested
    class EnterKey {

        @Test
        void k4_unchangedTextOnBeginStartPreservesShape() {
            var element = crotchet();
            element.setLyricForVerse(1, Lyric.Syllabic.BEGIN, true, "ho", Lyric.Extend.START);
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, element);
            fireEnter(editor);

            verifyNoSongDidChange();
            assertThat(element.getMainLyric())
                .extracting(Lyric::syllabic, Lyric::compound, Lyric::extend)
                .containsExactly(Lyric.Syllabic.BEGIN, true, Lyric.Extend.START);
        }
    }

    // -----------------------------------------------------------------------
    // K5–K6: Escape
    // -----------------------------------------------------------------------

    @Nested
    class EscapeKey {

        @Test
        void k5_normalEscapeNoCommitDismiss() {
            var element = crotchet();
            setMainLyric(element, "ho");
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));

            var editor = new LyricEditor(score, line, element);
            editor.setText("ha");

            messageCenterMock = mockStatic(MessageCenter.class);
            fireEscape(editor);

            verifyNoSongDidChange();
            assertThat(element.getMainLyric()).extracting(Lyric::text).isEqualTo("ho");
        }

        @Test
        void k6_suppressDismissAdjustmentNoMutationsAtAll() {
            var predecessor = crotchet();
            predecessor.setLyricForVerse(1, Lyric.Syllabic.BEGIN, false, "Su", Lyric.Extend.NONE);
            var element = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(predecessor));
            song.withoutMutationTracking(() -> line.addElement(element));

            var editor = new LyricEditor(score, line, element);
            editor.setSuppressDismissAdjustmentForTesting(true);

            messageCenterMock = mockStatic(MessageCenter.class);
            fireEscape(editor);

            verifyNoSongDidChange();
        }
    }

    // -----------------------------------------------------------------------
    // S1–S8: Space
    // -----------------------------------------------------------------------

    @Nested
    class Space {

        @Test
        void s1_changedTextCommitsWordFinalAndOpensNext() {
            var element = crotchet();
            element.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "ho", Lyric.Extend.NONE);
            var nextNote = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));
            song.withoutMutationTracking(() -> line.addElement(nextNote));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, element);
            editor.setText("ha");
            editor.attachListeners();
            fireSpace(editor);

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).hasSize(1);
            assertThat(element.getMainLyric())
                .extracting(Lyric::text, Lyric::extend)
                .containsExactly("ha", Lyric.Extend.NONE);

            var captor = ArgumentCaptor.forClass(LyricEditor.class);
            verify(score, atLeastOnce()).addOverlay(captor.capture());
            assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(nextNote);
        }

        @Test
        void s2_changedTextNoNextEligibleCommitsAndDismisses() {
            var element = crotchet();
            element.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "ho", Lyric.Extend.NONE);
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, element);
            editor.setText("ha");
            editor.attachListeners();
            fireSpace(editor);

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).hasSize(1);
            assertThat(element.getMainLyric()).extracting(Lyric::text).isEqualTo("ha");
            verify(score, never()).addOverlay(any());
        }

        @Test
        void s3_unchangedTextNoneExtendNoMutationOpensNext() {
            var element = crotchet();
            element.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "Su", Lyric.Extend.NONE);
            var nextNote = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));
            song.withoutMutationTracking(() -> line.addElement(nextNote));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, element);
            assertThat(editor.getText()).isEqualTo("Su");
            editor.attachListeners();
            fireSpace(editor);

            verifyNoSongDidChange();

            var captor = ArgumentCaptor.forClass(LyricEditor.class);
            verify(score, atLeastOnce()).addOverlay(captor.capture());
            assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(nextNote);
        }

        @Test
        void s4_unchangedCarrierWithTextBreaksChainAndOpensNext() {
            // element has text + START extend — its forward CONTINUE carrier is cleared.
            // forwardCarrier is a rest so it is not eligible after its lyric is cleared.
            var element = crotchet();
            element.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "Su", Lyric.Extend.START);
            var forwardCarrier = crotchetRest();
            forwardCarrier.setLyricForVerse(1, null, false, null, Lyric.Extend.CONTINUE);
            var nextNote = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));
            song.withoutMutationTracking(() -> line.addElement(forwardCarrier));
            song.withoutMutationTracking(() -> line.addElement(nextNote));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, element);
            assertThat(editor.getText()).isEqualTo("Su");
            editor.attachListeners();
            fireSpace(editor);

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).hasSizeGreaterThanOrEqualTo(1);
            assertThat(element.getMainLyric())
                .extracting(Lyric::text, Lyric::extend)
                .containsExactly("Su", Lyric.Extend.NONE);
            assertThat(forwardCarrier.getLyricForVerse(1)).isNull();

            var captor = ArgumentCaptor.forClass(LyricEditor.class);
            verify(score, atLeastOnce()).addOverlay(captor.capture());
            assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(nextNote);
        }

        @Test
        void s5_emptyNullLyricOpensNextWithoutMutation() {
            var element = crotchet();
            var nextNote = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));
            song.withoutMutationTracking(() -> line.addElement(nextNote));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, element);
            assertThat(editor.getText()).isEmpty();
            editor.attachListeners();
            fireSpace(editor);

            verifyNoSongDidChange();

            var captor = ArgumentCaptor.forClass(LyricEditor.class);
            verify(score, atLeastOnce()).addOverlay(captor.capture());
            assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(nextNote);
        }

        @Test
        void s6_emptyNullLyricNoNextEligibleDismisses() {
            var element = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, element);
            assertThat(editor.getText()).isEmpty();
            editor.attachListeners();
            fireSpace(editor);

            verifyNoSongDidChange();
            verify(score, never()).addOverlay(any());
        }

        @Test
        void s7_emptyCarrierNextEligibleBreaksChainAndOpensNext() {
            // e0(START) → e1(CONTINUE, editor, empty) → e2(STOP, rest) → e3(next note)
            // clearForwardCarriers clears e2; e2 is a rest so it is not eligible after clearing.
            var e0 = crotchet();
            e0.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "Do", Lyric.Extend.START);
            var e1 = crotchet();
            e1.setLyricForVerse(1, null, false, null, Lyric.Extend.CONTINUE);
            var e2 = crotchetRest();
            e2.setLyricForVerse(1, null, false, null, Lyric.Extend.STOP);
            var e3 = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(e0));
            song.withoutMutationTracking(() -> line.addElement(e1));
            song.withoutMutationTracking(() -> line.addElement(e2));
            song.withoutMutationTracking(() -> line.addElement(e3));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, e1);
            assertThat(editor.getText()).isEmpty();
            editor.attachListeners();
            fireSpace(editor);

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).hasSizeGreaterThanOrEqualTo(1);
            assertThat(e2.getLyricForVerse(1)).isNull();

            var captor = ArgumentCaptor.forClass(LyricEditor.class);
            verify(score, atLeastOnce()).addOverlay(captor.capture());
            assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(e3);
        }

        @Test
        void s8_emptyCarrierNoNextEligibleBreaksChainAndDismisses() {
            // e0(START) → e1(CONTINUE, editor, empty) → e2(STOP, rest) — no eligible next
            var e0 = crotchet();
            e0.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "Do", Lyric.Extend.START);
            var e1 = crotchet();
            e1.setLyricForVerse(1, null, false, null, Lyric.Extend.CONTINUE);
            var e2 = crotchetRest();
            e2.setLyricForVerse(1, null, false, null, Lyric.Extend.STOP);
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(e0));
            song.withoutMutationTracking(() -> line.addElement(e1));
            song.withoutMutationTracking(() -> line.addElement(e2));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, e1);
            assertThat(editor.getText()).isEmpty();
            editor.attachListeners();
            fireSpace(editor);

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).hasSizeGreaterThanOrEqualTo(1);
            assertThat(e2.getLyricForVerse(1)).isNull();
            verify(score, never()).addOverlay(any());
        }
    }

    // -----------------------------------------------------------------------
    // H1–H8: Hyphen
    // -----------------------------------------------------------------------

    @Nested
    class Hyphen {

        @Test
        void h1_openedAsExtenderEmptyTextBeeps() {
            var element = crotchet();
            element.setLyricForVerse(1, null, false, null, Lyric.Extend.CONTINUE);
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));

            var editor = new LyricEditor(score, line, element);
            assertThat(editor.getText()).isEmpty();
            editor.attachListeners();

            var toolkitMock = mock(Toolkit.class);

            try (var toolkitStatic = mockStatic(Toolkit.class)) {
                toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
                fireHyphen(editor);
                verify(toolkitMock).beep();
            }

            verify(score, never()).addOverlay(any());
        }

        @Test
        void h2_nonEmptyTextNextEligibleCommitsAndAdvances() {
            var element = crotchet();
            var nextNote = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));
            song.withoutMutationTracking(() -> line.addElement(nextNote));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, element);
            editor.setText("Sup");
            editor.attachListeners();
            fireHyphen(editor);

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).hasSizeGreaterThanOrEqualTo(1);
            assertThat(element.getMainLyric())
                .extracting(Lyric::syllabic, Lyric::compound, Lyric::extend)
                .containsExactly(Lyric.Syllabic.BEGIN, false, Lyric.Extend.NONE);

            var captor = ArgumentCaptor.forClass(LyricEditor.class);
            verify(score, atLeastOnce()).addOverlay(captor.capture());
            assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(nextNote);
        }

        @Test
        void h3_nonEmptyTextNoNextEligibleBeeps() {
            var element = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));

            var editor = new LyricEditor(score, line, element);
            editor.setText("Sup");
            editor.attachListeners();

            var toolkitMock = mock(Toolkit.class);

            try (var toolkitStatic = mockStatic(Toolkit.class)) {
                toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
                fireHyphen(editor);
                verify(toolkitMock).beep();
            }

            verify(score, never()).addOverlay(any());
        }

        @Test
        void h4_emptyTextNonNullLyricBeeps() {
            var element = crotchet();
            setMainLyric(element, "do");
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));

            var editor = new LyricEditor(score, line, element);
            editor.setText("");
            editor.attachListeners();

            var toolkitMock = mock(Toolkit.class);

            try (var toolkitStatic = mockStatic(Toolkit.class)) {
                toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
                fireHyphen(editor);
                verify(toolkitMock).beep();
            }

            assertThat(element.getLyricForVerse(1)).extracting(Lyric::text).isEqualTo("do");
        }

        @Test
        void h5_emptyTextNullLyricNoPredecessorBeeps() {
            var element = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));

            var editor = new LyricEditor(score, line, element);
            assertThat(editor.getText()).isEmpty();
            editor.attachListeners();

            var toolkitMock = mock(Toolkit.class);

            try (var toolkitStatic = mockStatic(Toolkit.class)) {
                toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
                fireHyphen(editor);
                verify(toolkitMock).beep();
            }
        }

        @Test
        void h6_emptyTextNullLyricEndSinglePredecessorBeeps() {
            var predecessor = crotchet();
            predecessor.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "do", Lyric.Extend.NONE);
            var element = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(predecessor));
            song.withoutMutationTracking(() -> line.addElement(element));

            var editor = new LyricEditor(score, line, element);
            assertThat(editor.getText()).isEmpty();
            editor.attachListeners();

            var toolkitMock = mock(Toolkit.class);

            try (var toolkitStatic = mockStatic(Toolkit.class)) {
                toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
                fireHyphen(editor);
                verify(toolkitMock).beep();
            }
        }

        @Test
        void h7_emptyTextNullLyricBeginPredecessorNextEligibleAdvances() {
            var predecessor = crotchet();
            predecessor.setLyricForVerse(1, Lyric.Syllabic.BEGIN, false, "Sup", Lyric.Extend.NONE);
            var element = crotchet();
            var nextNote = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(predecessor));
            song.withoutMutationTracking(() -> line.addElement(element));
            song.withoutMutationTracking(() -> line.addElement(nextNote));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, element);
            assertThat(editor.getText()).isEmpty();
            editor.attachListeners();
            fireHyphen(editor);

            verifyNoSongDidChange();
            assertThat(element.getLyricForVerse(1)).isNull();

            var captor = ArgumentCaptor.forClass(LyricEditor.class);
            verify(score, atLeastOnce()).addOverlay(captor.capture());
            assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(nextNote);
        }

        @Test
        void h8_emptyTextNullLyricBeginPredecessorNoNextEligibleBeeps() {
            var predecessor = crotchet();
            predecessor.setLyricForVerse(1, Lyric.Syllabic.BEGIN, false, "Sup", Lyric.Extend.NONE);
            var element = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(predecessor));
            song.withoutMutationTracking(() -> line.addElement(element));

            var editor = new LyricEditor(score, line, element);
            assertThat(editor.getText()).isEmpty();
            editor.attachListeners();

            var toolkitMock = mock(Toolkit.class);

            try (var toolkitStatic = mockStatic(Toolkit.class)) {
                toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
                fireHyphen(editor);
                verify(toolkitMock).beep();
            }

            verify(score, never()).addOverlay(any());
        }
    }

    // -----------------------------------------------------------------------
    // E1–E6: Equals
    // -----------------------------------------------------------------------

    @Nested
    class Equals {

        @Test
        void e1_emptyTextBeeps() {
            var element = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));

            var editor = new LyricEditor(score, line, element);
            assertThat(editor.getText()).isEmpty();
            editor.attachListeners();

            var toolkitMock = mock(Toolkit.class);

            try (var toolkitStatic = mockStatic(Toolkit.class)) {
                toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
                fireEquals(editor);
                verify(toolkitMock).beep();
            }

            verify(score, never()).addOverlay(any());
        }

        @Test
        void e2_nonEmptyTextCaretMidBeeps() {
            var element = crotchet();
            var nextNote = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));
            song.withoutMutationTracking(() -> line.addElement(nextNote));

            var editor = new LyricEditor(score, line, element);
            editor.setText("Su");
            editor.setCaretPosition(1);
            editor.attachListeners();

            var toolkitMock = mock(Toolkit.class);

            try (var toolkitStatic = mockStatic(Toolkit.class)) {
                toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
                fireEquals(editor);
                verify(toolkitMock).beep();
            }

            verify(score, never()).addOverlay(any());
        }

        @Test
        void e3_nonEmptyCaretEndNextEligibleCommitsCompoundAndAdvances() {
            var element = crotchet();
            var nextNote = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));
            song.withoutMutationTracking(() -> line.addElement(nextNote));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, element);
            editor.setText("Su");
            editor.setCaretPosition(editor.getText().length());
            editor.attachListeners();
            fireEquals(editor);

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).hasSizeGreaterThanOrEqualTo(1);
            assertThat(element.getMainLyric())
                .extracting(Lyric::compound, Lyric::extend)
                .containsExactly(true, Lyric.Extend.NONE);

            var captor = ArgumentCaptor.forClass(LyricEditor.class);
            verify(score, atLeastOnce()).addOverlay(captor.capture());
            assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(nextNote);
        }

        @Test
        void e4_nonEmptyCaretEndNoNextEligibleBeeps() {
            var element = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));

            var editor = new LyricEditor(score, line, element);
            editor.setText("Su");
            editor.setCaretPosition(editor.getText().length());
            editor.attachListeners();

            var toolkitMock = mock(Toolkit.class);

            try (var toolkitStatic = mockStatic(Toolkit.class)) {
                toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
                fireEquals(editor);
                verify(toolkitMock).beep();
            }

            verify(score, never()).addOverlay(any());
        }

        @Test
        void e5_openedAsExtenderNextEligibleBreaksChainCommitsCompoundAndAdvances() {
            var e0 = crotchet();
            e0.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "Do", Lyric.Extend.START);
            var e1 = crotchet();
            e1.setLyricForVerse(1, null, false, null, Lyric.Extend.CONTINUE);
            var e2 = crotchetRest();
            e2.setLyricForVerse(1, null, false, null, Lyric.Extend.STOP);
            var e3 = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(e0));
            song.withoutMutationTracking(() -> line.addElement(e1));
            song.withoutMutationTracking(() -> line.addElement(e2));
            song.withoutMutationTracking(() -> line.addElement(e3));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, e1);
            editor.setText("Su");
            editor.setCaretPosition(editor.getText().length());
            editor.attachListeners();
            fireEquals(editor);

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).hasSizeGreaterThanOrEqualTo(1);
            assertThat(e1.getMainLyric())
                .extracting(Lyric::compound, Lyric::extend)
                .containsExactly(true, Lyric.Extend.NONE);
            assertThat(e2.getLyricForVerse(1)).isNull();

            var captor = ArgumentCaptor.forClass(LyricEditor.class);
            verify(score, atLeastOnce()).addOverlay(captor.capture());
            assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(e3);
        }

        @Test
        void e6_openedAsExtenderNoNextEligibleBeeps() {
            var e0 = crotchet();
            e0.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "Do", Lyric.Extend.START);
            var e1 = crotchet();
            e1.setLyricForVerse(1, null, false, null, Lyric.Extend.CONTINUE);
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(e0));
            song.withoutMutationTracking(() -> line.addElement(e1));

            var editor = new LyricEditor(score, line, e1);
            editor.setText("Su");
            editor.setCaretPosition(editor.getText().length());
            editor.attachListeners();

            var toolkitMock = mock(Toolkit.class);

            try (var toolkitStatic = mockStatic(Toolkit.class)) {
                toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
                fireEquals(editor);
                verify(toolkitMock).beep();
            }

            verify(score, never()).addOverlay(any());
        }
    }

    // -----------------------------------------------------------------------
    // U1–U11: Underscore
    // -----------------------------------------------------------------------

    @Nested
    class Underscore {

        // U1: Non-empty text, caret mid → beep, stay open
        @Test
        void u1_nonEmptyTextCaretMidBeeps() {
            var element = crotchet();
            var nextNote = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));
            song.withoutMutationTracking(() -> line.addElement(nextNote));

            var editor = new LyricEditor(score, line, element);
            editor.setText("do");
            editor.setCaretPosition(1);
            editor.attachListeners();

            var toolkitMock = mock(Toolkit.class);

            try (var toolkitStatic = mockStatic(Toolkit.class)) {
                toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
                fireUnderscore(editor);
                verify(toolkitMock).beep();
            }

            verify(score, never()).addOverlay(any());
        }

        // U2: Non-empty text, caret at end, not extender, next eligible → beep (Phase 2)
        @Test
        void u2_nonEmptyTextCaretEndNotExtenderBeeps() {
            var element = crotchet();
            var nextNote = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));
            song.withoutMutationTracking(() -> line.addElement(nextNote));

            var editor = new LyricEditor(score, line, element);
            editor.setText("do");
            editor.setCaretPosition(editor.getText().length());
            editor.attachListeners();

            var toolkitMock = mock(Toolkit.class);

            try (var toolkitStatic = mockStatic(Toolkit.class)) {
                toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
                fireUnderscore(editor);
                verify(toolkitMock).beep();
            }

            verify(score, never()).addOverlay(any());
        }

        // U3: Non-empty text, caret at end, not extender, no next eligible → beep
        @Test
        void u3_nonEmptyTextCaretEndNoNextEligibleBeeps() {
            var element = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));

            var editor = new LyricEditor(score, line, element);
            editor.setText("do");
            editor.setCaretPosition(editor.getText().length());
            editor.attachListeners();

            var toolkitMock = mock(Toolkit.class);

            try (var toolkitStatic = mockStatic(Toolkit.class)) {
                toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
                fireUnderscore(editor);
                verify(toolkitMock).beep();
            }

            verify(score, never()).addOverlay(any());
        }

        // U4: Non-empty text, caret at end, openedAsExtender, next eligible → beep (Phase 2)
        @Test
        void u4_nonEmptyTextCaretEndOpenedAsExtenderBeeps() {
            var e0 = crotchet();
            e0.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "Do", Lyric.Extend.START);
            var e1 = crotchet();
            e1.setLyricForVerse(1, null, false, null, Lyric.Extend.CONTINUE);
            var e2 = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(e0));
            song.withoutMutationTracking(() -> line.addElement(e1));
            song.withoutMutationTracking(() -> line.addElement(e2));

            // Opening on CONTINUE element sets openedAsExtender = true
            var editor = new LyricEditor(score, line, e1);
            editor.setText("do");
            editor.setCaretPosition(editor.getText().length());
            editor.attachListeners();

            var toolkitMock = mock(Toolkit.class);

            try (var toolkitStatic = mockStatic(Toolkit.class)) {
                toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
                fireUnderscore(editor);
                verify(toolkitMock).beep();
            }

            verify(score, never()).addOverlay(any());
        }

        // U5: Empty, no predecessor → beep, stay open
        @Test
        void u5_emptyNoPredecessorBeeps() {
            var element = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));

            var editor = new LyricEditor(score, line, element);
            assertThat(editor.getText()).isEmpty();
            editor.attachListeners();

            var toolkitMock = mock(Toolkit.class);

            try (var toolkitStatic = mockStatic(Toolkit.class)) {
                toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
                fireUnderscore(editor);
                verify(toolkitMock).beep();
            }

            verify(score, never()).addOverlay(any());
        }

        // U6: Empty, END/SINGLE predecessor, next eligible → rewrite predecessor extend to START,
        //     fill intervening CONTINUE, current CONTINUE, advance
        @Test
        void u6_emptySinglePredecessorNextEligibleRewritesAndAdvances() {
            var predecessor = crotchet();
            predecessor.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "do", Lyric.Extend.NONE);
            var element = crotchet();
            var nextNote = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(predecessor));
            song.withoutMutationTracking(() -> line.addElement(element));
            song.withoutMutationTracking(() -> line.addElement(nextNote));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, element);
            assertThat(editor.getText()).isEmpty();
            editor.attachListeners();
            fireUnderscore(editor);

            captureSingleDidChange();
            assertThat(predecessor.getMainLyric())
                .extracting(Lyric::syllabic, Lyric::compound, Lyric::text, Lyric::extend)
                .containsExactly(Lyric.Syllabic.SINGLE, false, "do", Lyric.Extend.START);
            assertThat(element.getLyricForVerse(1))
                .extracting(Lyric::extend)
                .isEqualTo(Lyric.Extend.STOP);

            var captor = ArgumentCaptor.forClass(LyricEditor.class);
            verify(score, atLeastOnce()).addOverlay(captor.capture());
            assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(nextNote);
        }

        // U7: Empty, END/SINGLE predecessor, no next eligible → build chain, dismiss
        @Test
        void u7_emptySinglePredecessorNoNextEligibleBuildsChainAndDismisses() {
            var predecessor = crotchet();
            predecessor.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "do", Lyric.Extend.NONE);
            var element = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(predecessor));
            song.withoutMutationTracking(() -> line.addElement(element));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, element);
            assertThat(editor.getText()).isEmpty();
            editor.attachListeners();
            fireUnderscore(editor);

            captureSingleDidChange();
            assertThat(predecessor.getMainLyric())
                .extracting(Lyric::syllabic, Lyric::compound, Lyric::text, Lyric::extend)
                .containsExactly(Lyric.Syllabic.SINGLE, false, "do", Lyric.Extend.START);
            assertThat(element.getLyricForVerse(1))
                .extracting(Lyric::extend)
                .isEqualTo(Lyric.Extend.STOP);
            verify(score, never()).addOverlay(any());
        }

        // U8: Empty, BEGIN/MIDDLE predecessor, next eligible → rewrite predecessor extend to START,
        //     fill intervening CONTINUE, current CONTINUE, advance
        @Test
        void u8_emptyBeginPredecessorNextEligibleRewritesAndAdvances() {
            var predecessor = crotchet();
            predecessor.setLyricForVerse(1, Lyric.Syllabic.BEGIN, false, "Su", Lyric.Extend.NONE);
            var element = crotchet();
            var nextNote = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(predecessor));
            song.withoutMutationTracking(() -> line.addElement(element));
            song.withoutMutationTracking(() -> line.addElement(nextNote));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, element);
            assertThat(editor.getText()).isEmpty();
            editor.attachListeners();
            fireUnderscore(editor);

            captureSingleDidChange();
            assertThat(predecessor.getMainLyric())
                .extracting(Lyric::syllabic, Lyric::compound, Lyric::text, Lyric::extend)
                .containsExactly(Lyric.Syllabic.BEGIN, false, "Su", Lyric.Extend.START);
            assertThat(element.getLyricForVerse(1))
                .extracting(Lyric::extend)
                .isEqualTo(Lyric.Extend.STOP);

            var captor = ArgumentCaptor.forClass(LyricEditor.class);
            verify(score, atLeastOnce()).addOverlay(captor.capture());
            assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(nextNote);
        }

        // U9: Empty, CONTINUE carrier predecessor, next eligible → leave predecessor unchanged,
        //     fill forward, current CONTINUE, advance
        @Test
        void u9_emptyContinueCarrierPredecessorNextEligibleAdvances() {
            var e0 = crotchet();
            e0.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "Do", Lyric.Extend.START);
            var predecessor = crotchet();
            predecessor.setLyricForVerse(1, null, false, null, Lyric.Extend.CONTINUE);
            var element = crotchet();
            var nextNote = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(e0));
            song.withoutMutationTracking(() -> line.addElement(predecessor));
            song.withoutMutationTracking(() -> line.addElement(element));
            song.withoutMutationTracking(() -> line.addElement(nextNote));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, element);
            assertThat(editor.getText()).isEmpty();
            editor.attachListeners();
            fireUnderscore(editor);

            captureSingleDidChange();
            assertThat(predecessor.getLyricForVerse(1))
                .extracting(Lyric::extend)
                .isEqualTo(Lyric.Extend.CONTINUE);
            assertThat(element.getLyricForVerse(1))
                .extracting(Lyric::extend)
                .isEqualTo(Lyric.Extend.STOP);

            var captor = ArgumentCaptor.forClass(LyricEditor.class);
            verify(score, atLeastOnce()).addOverlay(captor.capture());
            assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(nextNote);
        }

        // U10: Empty, STOP carrier predecessor, next eligible → flip predecessor STOP→CONTINUE,
        //      fill, advance
        @Test
        void u10_emptyStopCarrierPredecessorNextEligibleFlipsAndAdvances() {
            var e0 = crotchet();
            e0.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "Do", Lyric.Extend.START);
            var predecessor = crotchet();
            predecessor.setLyricForVerse(1, null, false, null, Lyric.Extend.STOP);
            var element = crotchet();
            var nextNote = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(e0));
            song.withoutMutationTracking(() -> line.addElement(predecessor));
            song.withoutMutationTracking(() -> line.addElement(element));
            song.withoutMutationTracking(() -> line.addElement(nextNote));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, element);
            assertThat(editor.getText()).isEmpty();
            editor.attachListeners();
            fireUnderscore(editor);

            captureSingleDidChange();
            assertThat(predecessor.getLyricForVerse(1))
                .extracting(Lyric::extend)
                .isEqualTo(Lyric.Extend.CONTINUE);
            assertThat(element.getLyricForVerse(1))
                .extracting(Lyric::extend)
                .isEqualTo(Lyric.Extend.STOP);

            var captor = ArgumentCaptor.forClass(LyricEditor.class);
            verify(score, atLeastOnce()).addOverlay(captor.capture());
            assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(nextNote);
        }

        // U11: Empty, any predecessor, no next eligible → build chain, dismiss
        @Test
        void u11_emptyAnyPredecessorNoNextEligibleBuildsChainAndDismisses() {
            var predecessor = crotchet();
            predecessor.setLyricForVerse(1, Lyric.Syllabic.BEGIN, false, "Su", Lyric.Extend.NONE);
            var element = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(predecessor));
            song.withoutMutationTracking(() -> line.addElement(element));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, element);
            assertThat(editor.getText()).isEmpty();
            editor.attachListeners();
            fireUnderscore(editor);

            captureSingleDidChange();
            assertThat(predecessor.getMainLyric())
                .extracting(Lyric::syllabic, Lyric::compound, Lyric::text, Lyric::extend)
                .containsExactly(Lyric.Syllabic.BEGIN, false, "Su", Lyric.Extend.START);
            assertThat(element.getLyricForVerse(1))
                .extracting(Lyric::extend)
                .isEqualTo(Lyric.Extend.STOP);
            verify(score, never()).addOverlay(any());
        }
    }

    // -----------------------------------------------------------------------
    // F1–F3: Focus loss
    // -----------------------------------------------------------------------

    @Nested
    class FocusLoss {

        @Test
        void f1_focusedFalseIsNoOp() {
            var element = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));

            var editor = new LyricEditor(score, line, element);
            editor.setText("ho");
            editor.attachListeners();
            // focused is false by default (focusGained never fired)

            messageCenterMock = mockStatic(MessageCenter.class);
            fireFocusLost(editor);

            verifyNoSongDidChange();
        }

        @Test
        void f2_focusedTrueCommitsAndDismisses() {
            var element = crotchet();
            element.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "ho", Lyric.Extend.NONE);
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));

            var editor = new LyricEditor(score, line, element);
            editor.setText("ha");
            var parent = new JPanel();
            parent.add(editor);
            editor.setFocusedForTesting(true);
            editor.attachListeners();

            messageCenterMock = mockStatic(MessageCenter.class);
            fireFocusLost(editor);

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).hasSize(1);
            assertThat(element.getMainLyric()).extracting(Lyric::text).isEqualTo("ha");
            assertThat(editor.getParent()).isNull();
        }

        @Test
        void f3_outsideMousePressedParentedAndFocusedCommitsAndDismisses() {
            var element = crotchet();
            element.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "ho", Lyric.Extend.NONE);
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));

            // Construct editor before mocking Toolkit — AWT init requires the real toolkit.
            var editor = new LyricEditor(score, line, element);
            editor.setText("ha");
            var parent = new JPanel();
            parent.add(editor);
            editor.setFocusedForTesting(true);

            var toolkitMock = mock(Toolkit.class);
            AWTEventListener[] capturedListener = {null};
            doAnswer(invocation -> {
                capturedListener[0] = invocation.getArgument(0);
                return null;
            }).when(toolkitMock).addAWTEventListener(any(AWTEventListener.class), anyLong());

            try (var toolkitStatic = mockStatic(Toolkit.class)) {
                toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);

                // attachListeners registers the outside-click listener via Toolkit.
                editor.attachListeners();
                assertThat(capturedListener[0]).isNotNull();

                messageCenterMock = mockStatic(MessageCenter.class);

                var externalComponent = new JPanel();
                var mouseEvent = new MouseEvent(
                    externalComponent, MouseEvent.MOUSE_PRESSED, 0L, 0, 0, 0, 1, false);
                capturedListener[0].eventDispatched(mouseEvent);

                var notification = captureSingleDidChange();
                assertThat(notification.getMutations()).hasSize(1);
                assertThat(element.getMainLyric()).extracting(Lyric::text).isEqualTo("ha");
                assertThat(editor.getParent()).isNull();
            }
        }
    }
}
