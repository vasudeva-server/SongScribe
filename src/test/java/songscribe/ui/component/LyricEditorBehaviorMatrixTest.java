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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;

import javax.swing.JPanel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.dom.Lyric;
import songscribe.dom.StaffElement;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementModification;

@SuppressWarnings("OverlyBroadThrowsClause")
class LyricEditorBehaviorMatrixTest extends LyricEditorTestSupport {

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

    @Test
    void w4_replaceWithinCapAllowed() throws Exception {
        // currentLength=5, replacedLength=3, text.length=3 → net 5 ≤ 32
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        editor.setText("hello");
        editor.attachListeners();

        // Replace "hel" (positions 0..2, length 3) with "Hey" — net length stays at 5.
        ((javax.swing.text.AbstractDocument) editor.getDocument()).replace(0, 3, "Hey", null);

        assertThat(editor.getText()).isEqualTo("Heylo");
    }

    @Test
    void w5_replaceExceedingCapBeepsAndRejects() throws Exception {
        // currentLength=30, replacedLength=2, text.length=5 → net 33 > 32 → beep + reject
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        editor.setText("a".repeat(30));
        editor.attachListeners();

        var toolkitMock = mock(Toolkit.class);

        try (var toolkitStatic = mockStatic(Toolkit.class)) {
            toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);

            // Replace first 2 chars with 5 chars — would push length to 33.
            ((javax.swing.text.AbstractDocument) editor.getDocument()).replace(0, 2, "abcde", null);

            verify(toolkitMock).beep();
        }

        assertThat(editor.getText()).hasSize(30);
    }

    // -----------------------------------------------------------------------
    // K1–K2: Tab
    // -----------------------------------------------------------------------

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
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
            assertThat(((ElementModification) notification.getMutations().getFirst()).fields())
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

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
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

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
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

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
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

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
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
            assertThat(editor.getText()).isEqualTo("_");
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
            assertThat(editor.getText()).isEqualTo("_");
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

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class Hyphen {

        @Test
        void h1_openedAsExtenderEmptyTextBeeps() {
            var element = crotchet();
            element.setLyricForVerse(1, null, false, null, Lyric.Extend.CONTINUE);
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));

            var editor = new LyricEditor(score, line, element);
            assertThat(editor.getText()).isEqualTo("_");
            editor.attachListeners();

            var toolkitMock = mock(Toolkit.class);

            try (var toolkitStatic = mockStatic(Toolkit.class)) {
                toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
                fireHyphen(editor);
                verify(toolkitMock).beep();
            }

            verify(score, never()).addOverlay(any());
        }

        // openedAsExtender=true, text non-empty, no next eligible → beep, stay open
        @Test
        void h_openedAsExtenderNonEmptyNoNextEligibleBeeps() {
            var element = crotchet();
            element.setLyricForVerse(1, null, false, null, Lyric.Extend.CONTINUE);
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));

            var editor = new LyricEditor(score, line, element);
            editor.setText("Re");
            editor.attachListeners();

            var toolkitMock = mock(Toolkit.class);

            try (var toolkitStatic = mockStatic(Toolkit.class)) {
                toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
                fireHyphen(editor);
                verify(toolkitMock).beep();
            }

            // No advance, no mutation — editor stays open on the same element.
            verify(score, never()).addOverlay(any());
            assertThat(editor.getText()).isEqualTo("Re");
        }

        // openedAsExtender=true, text non-empty, next eligible → breakChainCommitAndAdvance
        @Test
        void h_openedAsExtenderNonEmptyNextEligibleBreaksChainAndAdvances() {
            // e0(START chain root) → e1(CONTINUE, editor, typed "Re") → e2(STOP carrier) → e3(rest)
            // findNextEligibleIndex() from e1 finds e2 (a crotchet, always eligible).
            // breakChainAtCurrentElement clears e2's lyric; commitInner writes e1 as BEGIN;
            // openIndexOrDismiss opens the new editor on e2 (now lyric-free).
            var e0 = crotchet();
            e0.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "Do", Lyric.Extend.START);
            var e1 = crotchet();
            e1.setLyricForVerse(1, null, false, null, Lyric.Extend.CONTINUE);
            var e2 = crotchet();
            e2.setLyricForVerse(1, null, false, null, Lyric.Extend.STOP);
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(e0));
            song.withoutMutationTracking(() -> line.addElement(e1));
            song.withoutMutationTracking(() -> line.addElement(e2));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, e1);
            editor.setText("Re");
            editor.attachListeners();
            fireHyphen(editor);

            var notification = captureSingleDidChange();
            // breakChainAtCurrentElement clears e2; commitInner writes e1 as BEGIN
            assertThat(notification.getMutations()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(e1.getLyricForVerse(1)).extracting(Lyric::text).isEqualTo("Re");
            assertThat(e1.getLyricForVerse(1)).extracting(Lyric::syllabic).isEqualTo(Lyric.Syllabic.BEGIN);
            assertThat(e2.getLyricForVerse(1)).isNull();

            // A new editor opens on e2 (the next eligible element; its lyric was just cleared).
            var captor = ArgumentCaptor.forClass(LyricEditor.class);
            verify(score, atLeastOnce()).addOverlay(captor.capture());
            assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(e2);
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

        // Hyphenating the syllable that started a melisma drops the melisma, so the carriers
        // it fed are stale — the editor must advance onto a cleared element, not one that
        // still looks like a carrier.
        @Test
        void h_hyphenOnMelismaStartClearsForwardCarriers() {
            var e0 = crotchet();
            e0.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "a", Lyric.Extend.START);
            var e1 = crotchet();
            e1.setLyricForVerse(1, null, false, null, Lyric.Extend.STOP);
            var e2 = crotchet();
            e2.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "b", Lyric.Extend.NONE);
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(e0));
            song.withoutMutationTracking(() -> line.addElement(e1));
            song.withoutMutationTracking(() -> line.addElement(e2));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, e0);
            assertThat(editor.getText()).isEqualTo("a");
            editor.attachListeners();
            fireHyphen(editor);

            captureSingleDidChange();
            assertThat(e0.getMainLyric())
                .extracting(Lyric::text, Lyric::syllabic, Lyric::extend)
                .containsExactly("a", Lyric.Syllabic.BEGIN, Lyric.Extend.NONE);
            assertThat(e1.getLyricForVerse(1)).isNull();
            assertThat(e2.getMainLyric())
                .extracting(Lyric::text, Lyric::syllabic)
                .containsExactly("b", Lyric.Syllabic.END);

            var captor = ArgumentCaptor.forClass(LyricEditor.class);
            verify(score, atLeastOnce()).addOverlay(captor.capture());
            var nextEditor = requireLastNonNull(captor);
            assertThat(nextEditor.getActiveElement()).isSameAs(e1);
            assertThat(nextEditor.getText()).isEqualTo("-");
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

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
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

        // The '+' key is an alias for '=': it commits the same compound boundary
        // and advances, so a hyphenated word can be entered with either key.
        @Test
        void e7_plusKeyCommitsCompoundLikeEquals() {
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
            firePlus(editor);

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).hasSizeGreaterThanOrEqualTo(1);
            assertThat(element.getMainLyric())
                .extracting(Lyric::compound, Lyric::extend)
                .containsExactly(true, Lyric.Extend.NONE);

            var captor = ArgumentCaptor.forClass(LyricEditor.class);
            verify(score, atLeastOnce()).addOverlay(captor.capture());
            assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(nextNote);
        }
    }

    // -----------------------------------------------------------------------
    // U1–U11: Underscore
    // -----------------------------------------------------------------------

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
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

        // U2: Non-empty text, caret at end, not extender, next eligible has no syllable →
        //     commit as START, next becomes the STOP carrier, advance past it
        @Test
        void u2_nonEmptyTextCaretEndNotExtenderStartsMelismaOnNext() {
            var element = crotchet();
            var nextNote = crotchet();
            var afterNext = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));
            song.withoutMutationTracking(() -> line.addElement(nextNote));
            song.withoutMutationTracking(() -> line.addElement(afterNext));

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, line, element);
            editor.setText("do");
            editor.setCaretPosition(editor.getText().length());
            editor.attachListeners();
            fireUnderscore(editor);

            captureSingleDidChange();
            assertThat(element.getMainLyric())
                .extracting(Lyric::syllabic, Lyric::compound, Lyric::text, Lyric::extend)
                .containsExactly(Lyric.Syllabic.SINGLE, false, "do", Lyric.Extend.START);
            assertThat(nextNote.getLyricForVerse(1))
                .extracting(Lyric::text, Lyric::extend)
                .containsExactly("", Lyric.Extend.STOP);

            var captor = ArgumentCaptor.forClass(LyricEditor.class);
            verify(score, atLeastOnce()).addOverlay(captor.capture());
            assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(afterNext);
        }

        // U2b: Non-empty text, caret at end, next eligible already has a syllable → beep
        @Test
        void u2b_nonEmptyTextCaretEndNextHasSyllableBeeps() {
            var element = crotchet();
            var nextNote = crotchet();
            nextNote.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "re", Lyric.Extend.NONE);
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

            assertThat(element.getLyricForVerse(1)).isNull();
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

        // U4: Non-empty text, caret at end, openedAsExtender, next eligible → break the incoming
        //     chain, commit as START, next becomes the STOP carrier
        @Test
        void u4_nonEmptyTextCaretEndOpenedAsExtenderStartsNewMelisma() {
            var e0 = crotchet();
            e0.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "Do", Lyric.Extend.START);
            var e1 = crotchet();
            e1.setLyricForVerse(1, null, false, null, Lyric.Extend.CONTINUE);
            var e2 = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(e0));
            song.withoutMutationTracking(() -> line.addElement(e1));
            song.withoutMutationTracking(() -> line.addElement(e2));

            messageCenterMock = mockStatic(MessageCenter.class);
            // Opening on CONTINUE element sets openedAsExtender = true
            var editor = new LyricEditor(score, line, e1);
            editor.setText("do");
            editor.setCaretPosition(editor.getText().length());
            editor.attachListeners();
            fireUnderscore(editor);

            captureSingleDidChange();
            assertThat(e0.getMainLyric())
                .extracting(Lyric::text, Lyric::extend)
                .containsExactly("Do", Lyric.Extend.NONE);
            assertThat(e1.getMainLyric())
                .extracting(Lyric::syllabic, Lyric::compound, Lyric::text, Lyric::extend)
                .containsExactly(Lyric.Syllabic.SINGLE, false, "do", Lyric.Extend.START);
            assertThat(e2.getLyricForVerse(1))
                .extracting(Lyric::extend)
                .isEqualTo(Lyric.Extend.STOP);
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

        // U8: Empty, BEGIN/MIDDLE predecessor, next eligible → rewrite predecessor extend to START
        //     and end its word (a melisma start is never hyphenated), fill intervening CONTINUE,
        //     current CONTINUE, advance
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
                .containsExactly(Lyric.Syllabic.SINGLE, false, "Su", Lyric.Extend.START);
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
                .containsExactly(Lyric.Syllabic.SINGLE, false, "Su", Lyric.Extend.START);
            assertThat(element.getLyricForVerse(1))
                .extracting(Lyric::extend)
                .isEqualTo(Lyric.Extend.STOP);
            verify(score, never()).addOverlay(any());
        }
    }

    // -----------------------------------------------------------------------
    // F1–F3: Focus loss
    // -----------------------------------------------------------------------

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
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
        void f4_focusedTrueButParentNullIsNoOp() {
            var element = crotchet();
            element.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "ho", Lyric.Extend.NONE);
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));

            // No parent added — commitAndDismiss sees getParent()==null and returns immediately.
            var editor = new LyricEditor(score, line, element);
            editor.setText("ha");
            editor.setFocusedForTesting(true);
            // Do not add to a parent (parent remains null).

            messageCenterMock = mockStatic(MessageCenter.class);
            fireFocusLost(editor);

            verifyNoSongDidChange();
            assertThat(element.getMainLyric()).extracting(Lyric::text).isEqualTo("ho");
        }

        @SuppressWarnings("ReturnOfNull")
        @Test
        void f5_outsideClickOnEditorItselfIsNoOp() {
            var element = crotchet();
            element.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "ho", Lyric.Extend.NONE);
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));

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
                editor.attachListeners();
                assertThat(capturedListener[0]).isNotNull();

                messageCenterMock = mockStatic(MessageCenter.class);

                // Source is the editor itself — the self-click guard returns immediately.
                var selfClickEvent = new MouseEvent(
                    editor, MouseEvent.MOUSE_PRESSED, 0L, 0, 0, 0, 1, false);
                capturedListener[0].eventDispatched(selfClickEvent);

                verifyNoSongDidChange();
                assertThat(element.getMainLyric()).extracting(Lyric::text).isEqualTo("ho");
                assertThat(editor.getParent()).isNotNull();
            }
        }

        @SuppressWarnings("ReturnOfNull")
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

    // -----------------------------------------------------------------------
    // P1–P9: hyphen placeholder ("a" - "mi", editor opened on the middle note)
    // M1–M5: melisma placeholder (editor opened on a carrier)
    // -----------------------------------------------------------------------

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class Placeholders {

        /** Builds "a" (BEGIN) — gap — "mi" (END) and returns the gap element. */
        private StaffElement chainWithGap(StaffElement first, StaffElement gap, StaffElement last) {
            first.setLyricForVerse(1, Lyric.Syllabic.BEGIN, false, "a", Lyric.Extend.NONE);
            last.setLyricForVerse(1, Lyric.Syllabic.END, false, "mi", Lyric.Extend.NONE);
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(first));
            song.withoutMutationTracking(() -> line.addElement(gap));
            song.withoutMutationTracking(() -> line.addElement(last));
            return gap;
        }

        // P1: gap spanned by a word's hyphen → prefilled with a selected "-"
        @Test
        void p1_gapInsideChainOpensWithSelectedPlaceholder() {
            var gap = chainWithGap(crotchet(), crotchet(), crotchet());

            var editor = new LyricEditor(score, song.getLine(0), gap);

            assertThat(editor.getText()).isEqualTo("-");
            assertThat(editor.getSelectedText()).isEqualTo("-");
        }

        // P2: no syllable after the gap — the chain dangles, so no placeholder
        @Test
        void p2_gapWithNoFollowingSyllableOpensEmpty() {
            var first = crotchet();
            first.setLyricForVerse(1, Lyric.Syllabic.BEGIN, false, "a", Lyric.Extend.NONE);
            var gap = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(first));
            song.withoutMutationTracking(() -> line.addElement(gap));

            var editor = new LyricEditor(score, line, gap);

            assertThat(editor.getText()).isEmpty();
        }

        // P3: predecessor ends its word — the gap is not inside a chain
        @Test
        void p3_gapAfterWordFinalSyllableOpensEmpty() {
            var first = crotchet();
            first.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "do", Lyric.Extend.NONE);
            var gap = crotchet();
            var last = crotchet();
            last.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "mi", Lyric.Extend.NONE);
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(first));
            song.withoutMutationTracking(() -> line.addElement(gap));
            song.withoutMutationTracking(() -> line.addElement(last));

            var editor = new LyricEditor(score, line, gap);

            assertThat(editor.getText()).isEmpty();
        }

        // P4: a note with no chain role at all opens empty
        @Test
        void p4_unrelatedNoteOpensEmpty() {
            var element = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));

            var editor = new LyricEditor(score, line, element);

            assertThat(editor.getText()).isEmpty();
        }

        // P5: placeholder left intact — Tab commits nothing and the chain survives
        @Test
        void p5_tabWithPlaceholderIntactLeavesChainUnchanged() {
            var first = crotchet();
            var last = crotchet();
            var gap = chainWithGap(first, crotchet(), last);

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, song.getLine(0), gap);
            editor.attachListeners();
            fireTab(editor);

            verifyNoSongDidChange();
            assertThat(gap.getLyricForVerse(1)).isNull();
            assertThat(first.getMainLyric()).extracting(Lyric::syllabic).isEqualTo(Lyric.Syllabic.BEGIN);
            assertThat(last.getMainLyric()).extracting(Lyric::syllabic).isEqualTo(Lyric.Syllabic.END);
        }

        // P6: placeholder deleted, then committed → the word ends at the predecessor
        @Test
        void p6_clearedPlaceholderCommitBreaksChain() {
            var first = crotchet();
            var last = crotchet();
            var gap = chainWithGap(first, crotchet(), last);

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, song.getLine(0), gap);
            editor.attachListeners();
            editor.setText("");
            fireEnter(editor);

            captureSingleDidChange();
            assertThat(gap.getLyricForVerse(1)).isNull();
            assertThat(first.getMainLyric()).extracting(Lyric::syllabic).isEqualTo(Lyric.Syllabic.SINGLE);
            assertThat(last.getMainLyric()).extracting(Lyric::syllabic).isEqualTo(Lyric.Syllabic.SINGLE);
        }

        // P7: Space wipes out the placeholder, which breaks the chain, then advances
        @Test
        void p7_spaceBreaksChainAndAdvances() {
            var first = crotchet();
            var last = crotchet();
            var gap = chainWithGap(first, crotchet(), last);

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, song.getLine(0), gap);
            editor.attachListeners();
            fireSpace(editor);

            captureSingleDidChange();
            assertThat(gap.getLyricForVerse(1)).isNull();
            assertThat(first.getMainLyric()).extracting(Lyric::syllabic).isEqualTo(Lyric.Syllabic.SINGLE);
            assertThat(last.getMainLyric()).extracting(Lyric::syllabic).isEqualTo(Lyric.Syllabic.SINGLE);

            var captor = ArgumentCaptor.forClass(LyricEditor.class);
            verify(score, atLeastOnce()).addOverlay(captor.capture());
            assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(last);
        }

        // P8: typing over the placeholder commits an ordinary syllable inside the word
        @Test
        void p8_textTypedOverPlaceholderCommitsSyllable() {
            var first = crotchet();
            var last = crotchet();
            var gap = chainWithGap(first, crotchet(), last);

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, song.getLine(0), gap);
            editor.attachListeners();
            editor.setText("la");
            fireHyphen(editor);

            captureSingleDidChange();
            assertThat(gap.getMainLyric())
                .extracting(Lyric::text, Lyric::syllabic)
                .containsExactly("la", Lyric.Syllabic.MIDDLE);
            assertThat(first.getMainLyric()).extracting(Lyric::syllabic).isEqualTo(Lyric.Syllabic.BEGIN);
            assertThat(last.getMainLyric()).extracting(Lyric::syllabic).isEqualTo(Lyric.Syllabic.END);
        }

        // P9: Escape after clearing the placeholder cancels — the chain is left alone
        @Test
        void p9_escapeAfterClearingPlaceholderLeavesChainIntact() {
            var first = crotchet();
            var last = crotchet();
            var gap = chainWithGap(first, crotchet(), last);

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, song.getLine(0), gap);
            editor.attachListeners();
            editor.setText("");
            fireEscape(editor);

            verifyNoSongDidChange();
            assertThat(gap.getLyricForVerse(1)).isNull();
            assertThat(first.getMainLyric()).extracting(Lyric::syllabic).isEqualTo(Lyric.Syllabic.BEGIN);
            assertThat(last.getMainLyric()).extracting(Lyric::syllabic).isEqualTo(Lyric.Syllabic.END);
        }

        /** Builds "Do" (START) — CONTINUE — STOP and returns the middle carrier. */
        private StaffElement melismaWithCarrier(StaffElement first, StaffElement middle, StaffElement last) {
            first.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "Do", Lyric.Extend.START);
            middle.setLyricForVerse(1, null, false, null, Lyric.Extend.CONTINUE);
            last.setLyricForVerse(1, null, false, null, Lyric.Extend.STOP);
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(first));
            song.withoutMutationTracking(() -> line.addElement(middle));
            song.withoutMutationTracking(() -> line.addElement(last));
            return middle;
        }

        // M1: a CONTINUE carrier opens with a selected extender placeholder
        @Test
        void m1_continueCarrierOpensWithSelectedPlaceholder() {
            var carrier = melismaWithCarrier(crotchet(), crotchet(), crotchet());

            var editor = new LyricEditor(score, song.getLine(0), carrier);

            assertThat(editor.getText()).isEqualTo("_");
            assertThat(editor.getSelectedText()).isEqualTo("_");
        }

        // M2: the STOP carrier that ends a melisma gets the placeholder too
        @Test
        void m2_stopCarrierOpensWithPlaceholder() {
            var first = crotchet();
            first.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "Do", Lyric.Extend.START);
            var carrier = crotchet();
            carrier.setLyricForVerse(1, null, false, null, Lyric.Extend.STOP);
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(first));
            song.withoutMutationTracking(() -> line.addElement(carrier));

            var editor = new LyricEditor(score, line, carrier);

            assertThat(editor.getText()).isEqualTo("_");
        }

        // M3: placeholder left intact — Tab commits nothing and the melisma survives
        @Test
        void m3_tabWithPlaceholderIntactLeavesMelismaUnchanged() {
            var first = crotchet();
            var last = crotchet();
            var carrier = melismaWithCarrier(first, crotchet(), last);

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, song.getLine(0), carrier);
            editor.attachListeners();
            fireTab(editor);

            verifyNoSongDidChange();
            assertThat(first.getMainLyric()).extracting(Lyric::extend).isEqualTo(Lyric.Extend.START);
            assertThat(carrier.getMainLyric()).extracting(Lyric::extend).isEqualTo(Lyric.Extend.CONTINUE);
            assertThat(last.getMainLyric()).extracting(Lyric::extend).isEqualTo(Lyric.Extend.STOP);
        }

        // M4: placeholder deleted, then committed → the carrier is dropped and the melisma
        //     collapses, since only this note and the STOP sustained it
        @Test
        void m4_clearedPlaceholderCommitBreaksMelisma() {
            var first = crotchet();
            var last = crotchet();
            var carrier = melismaWithCarrier(first, crotchet(), last);

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, song.getLine(0), carrier);
            editor.attachListeners();
            editor.setText("");
            fireEnter(editor);

            captureSingleDidChange();
            assertThat(first.getMainLyric())
                .extracting(Lyric::text, Lyric::extend)
                .containsExactly("Do", Lyric.Extend.NONE);
            assertThat(carrier.getLyricForVerse(1)).isNull();
            assertThat(last.getLyricForVerse(1)).isNull();
        }

        // M5: Escape after clearing the placeholder cancels — the melisma is left alone
        @Test
        void m5_escapeAfterClearingPlaceholderLeavesMelismaIntact() {
            var first = crotchet();
            var last = crotchet();
            var carrier = melismaWithCarrier(first, crotchet(), last);

            messageCenterMock = mockStatic(MessageCenter.class);
            var editor = new LyricEditor(score, song.getLine(0), carrier);
            editor.attachListeners();
            editor.setText("");
            fireEscape(editor);

            verifyNoSongDidChange();
            assertThat(first.getMainLyric()).extracting(Lyric::extend).isEqualTo(Lyric.Extend.START);
            assertThat(carrier.getMainLyric()).extracting(Lyric::extend).isEqualTo(Lyric.Extend.CONTINUE);
            assertThat(last.getMainLyric()).extracting(Lyric::extend).isEqualTo(Lyric.Extend.STOP);
        }
    }
}
