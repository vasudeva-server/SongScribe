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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;

import javax.swing.JComponent;
import javax.swing.KeyStroke;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementModification;
import songscribe.dom.Lyric;
import songscribe.dom.Song;
import songscribe.layout.LyricRenderMetrics;
import songscribe.ui.ViewScale;

@SuppressWarnings({ "OverlyBroadThrowsClause", "DataFlowIssue" })
class LyricEditorTest extends LyricEditorTestSupport {

    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final LyricRenderMetrics LYRIC_METRICS =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0);

    private Song song;
    private ScoreView score;

    @BeforeEach
    void setUp() {
        song = new Song();
        score = mock(ScoreView.class);
        when(score.getLyricRenderMetrics()).thenReturn(LYRIC_METRICS);
        var documentFonts = new DocumentFonts();
        documentFonts.setFont(FontKey.LYRICS, LYRICS_FONT);
        when(score.getDocumentFonts()).thenReturn(documentFonts);
        when(score.getViewScale()).thenReturn(new ViewScale());
        when(score.getSong()).thenReturn(song);
        when(score.getLineComponent(anyInt())).thenReturn(null);
        when(score.getLayout()).thenReturn(new BorderLayout());
    }

    // -----------------------------------------------------------------------
    // T10–T13: commit() semantics
    // -----------------------------------------------------------------------

    @AfterEach
    void tearDown() {
        if (messageCenterMock != null) {
            messageCenterMock.close();
            messageCenterMock = null;
        }
    }

    @Test
    void testCommitEmitsSingleElementModificationForNewText() {
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        editor.setText("ho");

        messageCenterMock = mockStatic(MessageCenter.class);
        editor.commit();

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(1);
        var modification = (ElementModification) notification.getMutations().getFirst();
        assertThat(modification.fields()).containsExactly(ElementField.LYRIC);
        assertThat(element.getMainLyric()).extracting(Lyric::text).isEqualTo("ho");
    }

    @Test
    void testCommitEmptyTextRemovesExistingLyric() {
        var element = crotchet();
        setMainLyric(element, "old");
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        editor.setText("");

        messageCenterMock = mockStatic(MessageCenter.class);
        editor.commit();

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(1);
        var modification = (ElementModification) notification.getMutations().getFirst();
        assertThat(modification.fields()).containsExactly(ElementField.LYRIC);
        assertThat(element.getMainLyric()).isNull();
    }

    @Test
    void testCommitEmptyTextOnEmptyElementEmitsNoMutations() {
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        editor.setText("");

        messageCenterMock = mockStatic(MessageCenter.class);
        editor.commit();

        verifyNoSongDidChange();
        assertThat(element.getMainLyric()).isNull();
    }

    @Test
    void testCommitSameTextEmitsNoMutations() {
        var element = crotchet();
        setMainLyric(element, "ho");
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        // The constructor prefills "ho" — leaving setText alone keeps the same text.
        assertThat(editor.getText()).isEqualTo("ho");

        messageCenterMock = mockStatic(MessageCenter.class);
        editor.commit();

        verifyNoSongDidChange();
        assertThat(element.getMainLyric()).extracting(Lyric::text).isEqualTo("ho");
    }

    // -----------------------------------------------------------------------
    // T14–T17: advance() eligibility scan
    // -----------------------------------------------------------------------

    @Test
    void testAdvanceSkipsRestsAndLandsOnNextNote() {
        var current = crotchet();
        var rest = crotchetRest();
        var nextNote = crotchet();
        var line = detachedLineWith(current, rest, nextNote);

        var editor = new LyricEditor(score, line, current);
        editor.advance();

        var captor = ArgumentCaptor.forClass(LyricEditor.class);
        verify(score, atLeastOnce()).addOverlay(captor.capture());
        var newEditor = requireLastNonNull(captor);
        assertThat(newEditor.getActiveElement()).isSameAs(nextNote);
    }

    @Test
    void testAdvanceTreatsRestWithExistingLyricAsEligible() {
        var current = crotchet();
        var restWithLyric = crotchetRest();
        setMainLyric(restWithLyric, "ah");
        var line = detachedLineWith(current, restWithLyric);

        var editor = new LyricEditor(score, line, current);
        editor.advance();

        var captor = ArgumentCaptor.forClass(LyricEditor.class);
        verify(score, atLeastOnce()).addOverlay(captor.capture());
        var newEditor = requireLastNonNull(captor);
        assertThat(newEditor.getActiveElement()).isSameAs(restWithLyric);
    }

    @Test
    void testAdvanceAtEndOfLineDismissesWithoutOpeningNewEditor() {
        var only = crotchet();
        var line = detachedLineWith(only);

        var editor = new LyricEditor(score, line, only);
        editor.advance();

        // dismiss() leaves the editor parentless, and no successor was opened (addOverlay
        // is the openOn entry point).
        assertThat(editor.getParent()).isNull();
        verify(score, never()).addOverlay(any(LyricEditor.class));
    }

    @Test
    void testAdvanceIntoPopulatedElementPrefillsTextAndSelectsAll() {
        var current = crotchet();
        var populated = crotchet();
        setMainLyric(populated, "do");
        var line = detachedLineWith(current, populated);

        var editor = new LyricEditor(score, line, current);
        editor.advance();

        var captor = ArgumentCaptor.forClass(LyricEditor.class);
        verify(score, atLeastOnce()).addOverlay(captor.capture());
        var newEditor = requireLastNonNull(captor);
        assertThat(newEditor.getActiveElement()).isSameAs(populated);
        assertThat(newEditor.getText()).isEqualTo("do");
        assertThat(newEditor.getCaret().getMark()).isZero();
        assertThat(newEditor.getCaret().getDot()).isEqualTo("do".length());
    }

    @Test
    void testAdvanceWithUnchangedTextPreservesExistingBoundaryAndExtend() {
        var current = crotchet();
        current.setLyricForVerse(1, Lyric.Syllabic.BEGIN, true, "ho", Lyric.Extend.START);
        var nextNote = crotchet();
        nextNote.setLyricForVerse(1, Lyric.Syllabic.END, false, "ri", Lyric.Extend.NONE);
        var line = detachedLineWith(current, nextNote);

        var editor = new LyricEditor(score, line, current);
        assertThat(editor.getText()).isEqualTo("ho");

        messageCenterMock = mockStatic(MessageCenter.class);
        editor.advance();

        assertThat(current.getMainLyric())
            .extracting(Lyric::text, Lyric::syllabic, Lyric::compound, Lyric::extend)
            .containsExactly("ho", Lyric.Syllabic.BEGIN, true, Lyric.Extend.START);

        var captor = ArgumentCaptor.forClass(LyricEditor.class);
        verify(score, atLeastOnce()).addOverlay(captor.capture());
        assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(nextNote);
    }

    @Test
    void testRetreatWithUnchangedTextPreservesExistingBoundary() {
        var previous = crotchet();
        previous.setLyricForVerse(1, Lyric.Syllabic.BEGIN, false, "ho", Lyric.Extend.NONE);
        var current = crotchet();
        current.setLyricForVerse(1, Lyric.Syllabic.END, false, "ri", Lyric.Extend.NONE);
        var line = detachedLineWith(previous, current);

        var editor = new LyricEditor(score, line, current);
        assertThat(editor.getText()).isEqualTo("ri");

        messageCenterMock = mockStatic(MessageCenter.class);
        editor.retreat();

        assertThat(current.getMainLyric())
            .extracting(Lyric::text, Lyric::syllabic, Lyric::compound, Lyric::extend)
            .containsExactly("ri", Lyric.Syllabic.END, false, Lyric.Extend.NONE);

        var captor = ArgumentCaptor.forClass(LyricEditor.class);
        verify(score, atLeastOnce()).addOverlay(captor.capture());
        assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(previous);
    }

    // -----------------------------------------------------------------------
    // T18–T24: input behavior and validation
    // -----------------------------------------------------------------------

    @Test
    void testTabKeyCommitsAndAdvancesWithoutInsertingTabCharacter() {
        var current = crotchet();
        var nextNote = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(current));
        song.withoutMutationTracking(() -> line.addElement(nextNote));

        var editor = new LyricEditor(score, line, current);
        editor.setText("ho");

        messageCenterMock = mockStatic(MessageCenter.class);

        var tabKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0);
        var tabActionKey = editor.getInputMap(JComponent.WHEN_FOCUSED).get(tabKeyStroke);
        assertThat(tabActionKey).as("Tab must be bound in WHEN_FOCUSED map").isNotNull();
        editor.getActionMap().get(tabActionKey).actionPerformed(
            new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, ""));

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(1);
        assertThat(((ElementModification) notification.getMutations().getFirst()).fields())
            .containsExactly(ElementField.LYRIC);

        var captor = ArgumentCaptor.forClass(LyricEditor.class);
        verify(score, atLeastOnce()).addOverlay(captor.capture());
        assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(nextNote);

        assertThat(editor.getText()).doesNotContain("\t");
    }

    @Test
    void testSpaceKeyCommitsAndAdvancesWithoutInsertingSpaceCharacter() {
        var current = crotchet();
        var nextNote = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(current));
        song.withoutMutationTracking(() -> line.addElement(nextNote));

        var editor = new LyricEditor(score, line, current);
        editor.setText("ho");
        editor.attachListeners();

        messageCenterMock = mockStatic(MessageCenter.class);

        var spaceEvent = new KeyEvent(editor, KeyEvent.KEY_TYPED, 0L, 0, KeyEvent.VK_UNDEFINED, ' ');

        for (var listener : editor.getKeyListeners()) {
            listener.keyTyped(spaceEvent);
        }

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(1);
        assertThat(((ElementModification) notification.getMutations().getFirst()).fields())
            .containsExactly(ElementField.LYRIC);

        var captor = ArgumentCaptor.forClass(LyricEditor.class);
        verify(score, atLeastOnce()).addOverlay(captor.capture());
        assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(nextNote);

        assertThat(editor.getText()).doesNotContain(" ");
    }

    @Test
    void testEnterKeyCommitsAndDismissesWithoutInsertingNewline() {
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        editor.setText("ho");

        messageCenterMock = mockStatic(MessageCenter.class);

        var enterKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
        var enterActionKey = editor.getInputMap(JComponent.WHEN_FOCUSED).get(enterKeyStroke);
        assertThat(enterActionKey).as("Enter must be bound in WHEN_FOCUSED map").isNotNull();
        editor.getActionMap().get(enterActionKey).actionPerformed(
            new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, ""));

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(1);
        assertThat(((ElementModification) notification.getMutations().getFirst()).fields())
            .containsExactly(ElementField.LYRIC);

        verify(score, never()).addOverlay(any(LyricEditor.class));

        assertThat(editor.getText()).doesNotContain("\n");
    }

    @Test
    void testEscapeKeyCancelsWithoutMutationOrAdvance() {
        var element = crotchet();
        setMainLyric(element, "ho");
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);

        messageCenterMock = mockStatic(MessageCenter.class);

        var escapeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        var escapeActionKey = editor.getInputMap(JComponent.WHEN_FOCUSED).get(escapeKeyStroke);
        assertThat(escapeActionKey).as("Escape must be bound in WHEN_FOCUSED map").isNotNull();
        editor.getActionMap().get(escapeActionKey).actionPerformed(
            new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, ""));

        verifyNoSongDidChange();
        verify(score, never()).addOverlay(any(LyricEditor.class));
    }

    @Test
    void testThirtyThirdCharacterBeepsAndIsNotInserted() throws Exception {
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
    void testFocusLostWithoutFocusIsNoOp() {
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        editor.setText("ho");
        editor.attachListeners();
        // focused is false by default (focusGained never fired)

        messageCenterMock = mockStatic(MessageCenter.class);

        var focusEvent = new FocusEvent(editor, FocusEvent.FOCUS_LOST);

        for (var listener : editor.getFocusListeners()) {
            listener.focusLost(focusEvent);
        }

        verifyNoSongDidChange();
    }

    // -----------------------------------------------------------------------
    // T25–T29: applyDismissAdjustment branches
    // -----------------------------------------------------------------------

    @Test
    void testSuppressedDismissAdjustmentEmitsNoMutations() {
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        editor.setSuppressDismissAdjustmentForTesting(true);

        messageCenterMock = mockStatic(MessageCenter.class);
        fireEscape(editor);

        verifyNoSongDidChange();
    }

    @Test
    void testOpenOnContinueCarrierEscWithoutTypingEmitsNoMutations() {
        var element = crotchet();
        element.setLyricForVerse(1, null, false, null, Lyric.Extend.CONTINUE);
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        // Text is empty — openedAsExtender is true — dismiss without typing.
        assertThat(editor.getText()).isEmpty();

        messageCenterMock = mockStatic(MessageCenter.class);
        fireEscape(editor);

        verifyNoSongDidChange();
    }

    @Test
    void testTypingIntoMidChainCarrierFlipsPredecessorAndClearsForwardCarriers() {
        var e0 = crotchet();
        var e1 = crotchet();
        var e2 = crotchet();
        var e3 = crotchet();
        e0.setLyricForVerse(1, Lyric.Syllabic.END, false, "Sure", Lyric.Extend.START);
        e1.setLyricForVerse(1, null, false, null, Lyric.Extend.CONTINUE);
        e2.setLyricForVerse(1, null, false, null, Lyric.Extend.CONTINUE);
        e3.setLyricForVerse(1, null, false, null, Lyric.Extend.STOP);
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> {
            line.addElement(e0);
            line.addElement(e1);
            line.addElement(e2);
            line.addElement(e3);
        });

        var editor = new LyricEditor(score, line, e2);
        editor.setText("abc");
        editor.attachListeners();

        messageCenterMock = mockStatic(MessageCenter.class);

        var spaceEvent = new KeyEvent(editor, KeyEvent.KEY_TYPED, 0L, 0, KeyEvent.VK_UNDEFINED, ' ');

        for (var listener : editor.getKeyListeners()) {
            listener.keyTyped(spaceEvent);
        }

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(3);

        for (var mutation : notification.getMutations()) {
            var modification = (ElementModification) mutation;
            assertThat(modification.fields()).containsExactly(ElementField.LYRIC);
            assertThat(modification.beforeElement()).isNotNull();
        }

        assertThat(e2.getLyricForVerse(1)).extracting(Lyric::text).isEqualTo("abc");
        assertThat(e1.getLyricForVerse(1)).extracting(Lyric::extend).isEqualTo(Lyric.Extend.STOP);
        assertThat(e3.getLyricForVerse(1)).isNull();
    }

    @Test
    void testDismissAdjustmentNoOpWhenPredecessorHasNoCarrier() {
        var e0 = crotchet();
        var e1 = crotchet();
        e0.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "Sure", Lyric.Extend.NONE);
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> {
            line.addElement(e0);
            line.addElement(e1);
        });

        var editor = new LyricEditor(score, line, e1);

        messageCenterMock = mockStatic(MessageCenter.class);
        fireEscape(editor);

        verifyNoSongDidChange();
        assertThat(e0.getLyricForVerse(1)).extracting(Lyric::syllabic).isEqualTo(Lyric.Syllabic.SINGLE);
    }

    @Test
    void testDismissDemotesDanglingBeginToSingle() {
        var e0 = crotchet();
        var e1 = crotchet();
        e0.setLyricForVerse(1, Lyric.Syllabic.BEGIN, false, "Su", Lyric.Extend.NONE);
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> {
            line.addElement(e0);
            line.addElement(e1);
        });

        // e0 is a dangling BEGIN with no following text-bearing element; e1 has no lyric.
        var editor = new LyricEditor(score, line, e1);

        messageCenterMock = mockStatic(MessageCenter.class);
        fireEscape(editor);

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(1);
        assertThat(((ElementModification) notification.getMutations().getFirst()).fields())
            .containsExactly(ElementField.LYRIC);
        assertThat(e0.getLyricForVerse(1)).extracting(Lyric::syllabic).isEqualTo(Lyric.Syllabic.SINGLE);
    }

    // -----------------------------------------------------------------------
    // T30–T35: handleHyphen behavior matrix
    // -----------------------------------------------------------------------

    // State 1: openedAsExtender = true → beep
    @Test
    void testHyphenOnExtenderCarrierBeeps() {
        var element = crotchet();
        element.setLyricForVerse(1, null, false, null, Lyric.Extend.CONTINUE);
        var line = detachedLineWith(element);
        var editor = new LyricEditor(score, line, element);
        editor.attachListeners();

        var toolkitMock = mock(Toolkit.class);

        try (var toolkitStatic = mockStatic(Toolkit.class)) {
            toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
            fireHyphen(editor);
            verify(toolkitMock).beep();
        }
    }

    // State 2: text non-empty → commit as BEGIN syllabic + advance to next note
    @Test
    void testHyphenOnNonEmptyTextCommitsAndAdvances() {
        var e0 = crotchet();
        var e1 = crotchet();
        var line = detachedLineWith(e0, e1);
        var editor = new LyricEditor(score, line, e0);
        editor.setText("Sup");
        editor.attachListeners();

        fireHyphen(editor);

        assertThat(e0.getLyricForVerse(1)).extracting(Lyric::text).isEqualTo("Sup");
        assertThat(e0.getLyricForVerse(1)).extracting(Lyric::syllabic).isEqualTo(Lyric.Syllabic.BEGIN);

        var captor = ArgumentCaptor.forClass(LyricEditor.class);
        verify(score, atLeastOnce()).addOverlay(captor.capture());
        assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(e1);
    }

    // State 3: text empty, element lyric non-null → beep (don't silently delete existing lyric)
    @Test
    void testHyphenOnEmptyEditorWithExistingLyricBeeps() {
        var element = crotchet();
        setMainLyric(element, "do");
        var line = detachedLineWith(element);
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

    // State 4: text empty, lyric null, no predecessor → beep
    @Test
    void testHyphenOnEmptyEditorWithNoPredecessorBeeps() {
        var element = crotchet();
        var line = detachedLineWith(element);
        var editor = new LyricEditor(score, line, element);
        editor.attachListeners();

        var toolkitMock = mock(Toolkit.class);

        try (var toolkitStatic = mockStatic(Toolkit.class)) {
            toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
            fireHyphen(editor);
            verify(toolkitMock).beep();
        }
    }

    // State 5: text empty, lyric null, predecessor END/SINGLE → beep
    @Test
    void testHyphenOnEmptyEditorWithWordFinalPredecessorBeeps() {
        var e0 = crotchet();
        var e1 = crotchet();
        e0.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "do", Lyric.Extend.NONE);
        var line = detachedLineWith(e0, e1);
        var editor = new LyricEditor(score, line, e1);
        editor.attachListeners();

        var toolkitMock = mock(Toolkit.class);

        try (var toolkitStatic = mockStatic(Toolkit.class)) {
            toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
            fireHyphen(editor);
            verify(toolkitMock).beep();
        }
    }

    // State 6: text empty, lyric null, predecessor BEGIN/MIDDLE → advance without mutating current element
    @Test
    void testHyphenOnEmptyEditorWithBeginPredecessorAdvancesWithoutMutation() {
        var e0 = crotchet();
        var e1 = crotchet();
        var e2 = crotchet();
        e0.setLyricForVerse(1, Lyric.Syllabic.BEGIN, false, "Sup", Lyric.Extend.NONE);
        var line = detachedLineWith(e0, e1, e2);
        var editor = new LyricEditor(score, line, e1);
        editor.attachListeners();

        fireHyphen(editor);

        assertThat(e1.getLyricForVerse(1)).isNull();

        var captor = ArgumentCaptor.forClass(LyricEditor.class);
        verify(score, atLeastOnce()).addOverlay(captor.capture());
        assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(e2);
    }

    // -----------------------------------------------------------------------
    // Phase 2: _ (underscore) overhaul
    // -----------------------------------------------------------------------

    @Test
    void testUnderscoreOnNonEmptyTextBeeps() {
        var element = crotchet();
        var next = crotchet();
        var line = detachedLineWith(element, next);
        var editor = new LyricEditor(score, line, element);
        editor.setText("ho");
        editor.attachListeners();

        var toolkitMock = mock(Toolkit.class);

        try (var toolkitStatic = mockStatic(Toolkit.class)) {
            toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
            fireUnderscore(editor);
            verify(toolkitMock).beep();
        }

        assertThat(editor.getText()).isEqualTo("ho");
    }

    @Test
    void testUnderscoreOnNonEmptyTextMidCaretBeeps() {
        var element = crotchet();
        var next = crotchet();
        var line = detachedLineWith(element, next);
        var editor = new LyricEditor(score, line, element);
        editor.setText("ho");
        editor.setCaretPosition(1);
        editor.attachListeners();

        var toolkitMock = mock(Toolkit.class);

        try (var toolkitStatic = mockStatic(Toolkit.class)) {
            toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
            fireUnderscore(editor);
            verify(toolkitMock).beep();
        }
    }

    @Test
    void testUnderscoreEmptyNoPredecessorBeeps() {
        var element = crotchet();
        var next = crotchet();
        var line = detachedLineWith(element, next);
        var editor = new LyricEditor(score, line, element);
        editor.attachListeners();

        var toolkitMock = mock(Toolkit.class);

        try (var toolkitStatic = mockStatic(Toolkit.class)) {
            toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
            fireUnderscore(editor);
            verify(toolkitMock).beep();
        }

        verify(score, never()).addOverlay(any(LyricEditor.class));
    }

    @Test
    void testUnderscoreEmptyNoNextEligibleBuildsChainAndDismisses() {
        var predecessor = crotchet();
        predecessor.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "do", Lyric.Extend.NONE);
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(predecessor));
        song.withoutMutationTracking(() -> line.addElement(element));
        messageCenterMock = mockStatic(MessageCenter.class);
        var editor = new LyricEditor(score, line, element);
        editor.attachListeners();
        fireUnderscore(editor);

        captureSingleDidChange();
        assertThat(predecessor.getMainLyric())
            .extracting(Lyric::syllabic, Lyric::compound, Lyric::text, Lyric::extend)
            .containsExactly(Lyric.Syllabic.SINGLE, false, "do", Lyric.Extend.START);
        assertThat(element.getLyricForVerse(1))
            .extracting(Lyric::extend)
            .isEqualTo(Lyric.Extend.STOP);
        verify(score, never()).addOverlay(any(LyricEditor.class));
    }

    @Test
    void testUnderscoreEmptyEndPredecessorRewritesToStart() {
        var predecessor = crotchet();
        predecessor.setLyricForVerse(1, Lyric.Syllabic.END, false, "ble", Lyric.Extend.NONE);
        var element = crotchet();
        var next = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(predecessor));
        song.withoutMutationTracking(() -> line.addElement(element));
        song.withoutMutationTracking(() -> line.addElement(next));

        messageCenterMock = mockStatic(MessageCenter.class);
        var editor = new LyricEditor(score, line, element);
        editor.attachListeners();
        fireUnderscore(editor);

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(2);
        assertThat(predecessor.getLyricForVerse(1)).extracting(Lyric::extend).isEqualTo(Lyric.Extend.START);
        assertThat(predecessor.getLyricForVerse(1)).extracting(Lyric::syllabic).isEqualTo(Lyric.Syllabic.END);
        assertThat(predecessor.getLyricForVerse(1)).extracting(Lyric::text).isEqualTo("ble");
        assertThat(element.getLyricForVerse(1)).extracting(Lyric::extend).isEqualTo(Lyric.Extend.STOP);
    }

    @Test
    void testUnderscoreEmptySinglePredecessorRewritesToStart() {
        var predecessor = crotchet();
        predecessor.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "do", Lyric.Extend.NONE);
        var element = crotchet();
        var next = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(predecessor));
        song.withoutMutationTracking(() -> line.addElement(element));
        song.withoutMutationTracking(() -> line.addElement(next));

        messageCenterMock = mockStatic(MessageCenter.class);
        var editor = new LyricEditor(score, line, element);
        editor.attachListeners();
        fireUnderscore(editor);

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(2);
        assertThat(predecessor.getLyricForVerse(1)).extracting(Lyric::extend).isEqualTo(Lyric.Extend.START);
        assertThat(element.getLyricForVerse(1)).extracting(Lyric::extend).isEqualTo(Lyric.Extend.STOP);
    }

    // -----------------------------------------------------------------------
    // Phase 3: - and = no-successor rules + carrier-with-text paths
    // -----------------------------------------------------------------------

    @Test
    void testHyphenNoNextEligibleBeeps() {
        var element = crotchet();
        var line = detachedLineWith(element);
        var editor = new LyricEditor(score, line, element);
        editor.setText("Sup");
        editor.attachListeners();

        var toolkitMock = mock(Toolkit.class);

        try (var toolkitStatic = mockStatic(Toolkit.class)) {
            toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
            fireHyphen(editor);
            verify(toolkitMock).beep();
        }

        verify(score, never()).addOverlay(any(LyricEditor.class));
    }

    @Test
    void testHyphenOnEmptyBeginPredecessorNoNextEligibleBeeps() {
        var predecessor = crotchet();
        predecessor.setLyricForVerse(1, Lyric.Syllabic.BEGIN, false, "Su", Lyric.Extend.NONE);
        var element = crotchet();
        var line = detachedLineWith(predecessor, element);
        var editor = new LyricEditor(score, line, element);
        editor.attachListeners();

        var toolkitMock = mock(Toolkit.class);

        try (var toolkitStatic = mockStatic(Toolkit.class)) {
            toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
            fireHyphen(editor);
            verify(toolkitMock).beep();
        }

        verify(score, never()).addOverlay(any(LyricEditor.class));
    }

    @Test
    void testHyphenOnCarrierWithTextBreaksPredecessorChain() {
        // e0(START chain root) → e1(CONTINUE, editor, typed "Re") → e2(STOP) → e3(next)
        // Breaking chain from e1: terminatePrecedingContinueChain sees e0=START (no flip),
        // clearForwardCarriers clears e2. commitInner writes e1 as new syllable.
        var e0 = crotchet();
        e0.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "Do", Lyric.Extend.START);
        var e1 = crotchet();
        e1.setLyricForVerse(1, null, false, null, Lyric.Extend.CONTINUE);
        var e2 = crotchet();
        e2.setLyricForVerse(1, null, false, null, Lyric.Extend.STOP);
        var e3 = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(e0));
        song.withoutMutationTracking(() -> line.addElement(e1));
        song.withoutMutationTracking(() -> line.addElement(e2));
        song.withoutMutationTracking(() -> line.addElement(e3));

        messageCenterMock = mockStatic(MessageCenter.class);
        var editor = new LyricEditor(score, line, e1);
        editor.setText("Re");
        editor.attachListeners();
        fireHyphen(editor);

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(e2.getLyricForVerse(1)).isNull();
        assertThat(e1.getLyricForVerse(1)).extracting(Lyric::text).isEqualTo("Re");
        assertThat(e1.getLyricForVerse(1)).extracting(Lyric::syllabic).isEqualTo(Lyric.Syllabic.BEGIN);
    }

    @Test
    void testEqualsNoNextEligibleBeeps() {
        var element = crotchet();
        var line = detachedLineWith(element);
        var editor = new LyricEditor(score, line, element);
        editor.setText("Do");
        editor.attachListeners();

        var toolkitMock = mock(Toolkit.class);

        try (var toolkitStatic = mockStatic(Toolkit.class)) {
            toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
            fireEquals(editor);
            verify(toolkitMock).beep();
        }

        verify(score, never()).addOverlay(any(LyricEditor.class));
    }

    @Test
    void testEqualsOnCarrierWithTextBreaksPredecessorChain() {
        // e0(START) → e1(CONTINUE, editor, typed "Re") → e2(next)
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
        var editor = new LyricEditor(score, line, e1);
        editor.setText("Re");
        editor.setCaretPosition(editor.getText().length());
        editor.attachListeners();
        fireEquals(editor);

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(e1.getLyricForVerse(1)).extracting(Lyric::text).isEqualTo("Re");
        assertThat(e1.getLyricForVerse(1)).extracting(Lyric::compound).isEqualTo(true);
    }

    // -----------------------------------------------------------------------
    // Phase 4: Space — always break chains
    // -----------------------------------------------------------------------

    @Test
    void testSpaceOnUnchangedBeginTextRewritesToSingle() {
        var element = crotchet();
        element.setLyricForVerse(1, Lyric.Syllabic.BEGIN, false, "Su", Lyric.Extend.NONE);
        var next = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));
        song.withoutMutationTracking(() -> line.addElement(next));

        messageCenterMock = mockStatic(MessageCenter.class);
        var editor = new LyricEditor(score, line, element);
        editor.attachListeners();
        fireSpace(editor);

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(element.getLyricForVerse(1)).extracting(Lyric::syllabic).isEqualTo(Lyric.Syllabic.SINGLE);
        assertThat(element.getLyricForVerse(1)).extracting(Lyric::extend).isEqualTo(Lyric.Extend.NONE);
    }

    @Test
    void testSpaceOnCarrierEmptyBreaksPredecessorChain() {
        // e0(START) → e1(CONTINUE, editor, empty) → e2(STOP) → e3(next)
        // Breaking from e1: terminatePrecedingContinueChain sees e0=START (no flip),
        // clearForwardCarriers clears e2. commitInner on empty carrier emits 1 mutation.
        var e0 = crotchet();
        e0.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "Do", Lyric.Extend.START);
        var e1 = crotchet();
        e1.setLyricForVerse(1, null, false, null, Lyric.Extend.CONTINUE);
        var e2 = crotchet();
        e2.setLyricForVerse(1, null, false, null, Lyric.Extend.STOP);
        var e3 = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(e0));
        song.withoutMutationTracking(() -> line.addElement(e1));
        song.withoutMutationTracking(() -> line.addElement(e2));
        song.withoutMutationTracking(() -> line.addElement(e3));

        messageCenterMock = mockStatic(MessageCenter.class);
        var editor = new LyricEditor(score, line, e1);
        editor.attachListeners();
        fireSpace(editor);

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(e2.getLyricForVerse(1)).isNull();
    }

    // -----------------------------------------------------------------------
    // Phase 5: Enter — preserve unchanged shape
    // -----------------------------------------------------------------------

    @Test
    void testEnterOnUnchangedTextPreservesShape() {
        var element = crotchet();
        element.setLyricForVerse(1, Lyric.Syllabic.BEGIN, true, "ho", Lyric.Extend.START);
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        messageCenterMock = mockStatic(MessageCenter.class);
        var editor = new LyricEditor(score, line, element);
        fireEnter(editor);

        verifyNoSongDidChange();
        assertThat(element.getLyricForVerse(1))
            .extracting(Lyric::syllabic, Lyric::compound, Lyric::extend)
            .containsExactly(Lyric.Syllabic.BEGIN, true, Lyric.Extend.START);
    }
}
