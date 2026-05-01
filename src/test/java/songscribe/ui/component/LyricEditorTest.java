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
import org.jspecify.annotations.Nullable;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementModification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.Lyric;
import songscribe.music.Song;
import songscribe.music.StaffElement;
import songscribe.ui.layout.LyricRenderMetrics;

class LyricEditorTest extends UnitTest {

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

    private static StaffElement crotchet() {
        return ElementType.CROTCHET.newInstance();
    }

    private static StaffElement crotchetRest() {
        return ElementType.CROTCHET_REST.newInstance();
    }

    private static void setMainLyric(StaffElement element, String text) {
        element.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, text, Lyric.Extend.NONE);
    }

    // -----------------------------------------------------------------------
    // T10–T13: commit() semantics
    // -----------------------------------------------------------------------

    @Nullable
    private MockedStatic<MessageCenter> messageCenterMock;

    @AfterEach
    void tearDown() {
        if (messageCenterMock != null) {
            messageCenterMock.close();
            messageCenterMock = null;
        }
    }

    private MockedStatic<MessageCenter> requireMessageCenterMock() {
        if (messageCenterMock == null) {
            throw new IllegalStateException("messageCenterMock must be initialized");
        }

        return messageCenterMock;
    }

    private SongDidChangeNotification captureSingleDidChange() {
        var captor = ArgumentCaptor.forClass(Message.class);
        requireMessageCenterMock().verify(() -> MessageCenter.post(captor.capture()), atLeastOnce());

        var didChanges = captor.getAllValues().stream()
            .filter(m -> m instanceof SongDidChangeNotification)
            .map(m -> (SongDidChangeNotification) m)
            .toList();

        assertThat(didChanges)
            .as("expected exactly one SongDidChangeNotification, got: %s", didChanges)
            .hasSize(1);

        return didChanges.get(0);
    }

    private void verifyNoSongDidChange() {
        requireMessageCenterMock().verify(
            () -> MessageCenter.post(any(SongDidChangeNotification.class)),
            never()
        );
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
        var modification = (ElementModification) notification.getMutations().get(0);
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
        var modification = (ElementModification) notification.getMutations().get(0);
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

    /**
     * Creates a fresh {@link Line} unattached to {@link #song}. The advance() tests need
     * an empty line so {@code isEligibleForLyric} only sees the elements added here —
     * {@code song.getLine(0)} returns a line populated with default clef/key elements that
     * would falsely match as eligible.
     */
    private Line detachedLineWith(StaffElement... elements) {
        var line = new Line();

        for (var element : elements) {
            line.addElement(element);
        }

        return line;
    }

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
        assertThat(((ElementModification) notification.getMutations().get(0)).fields())
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
        assertThat(((ElementModification) notification.getMutations().get(0)).fields())
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
        assertThat(((ElementModification) notification.getMutations().get(0)).fields())
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

    private void fireEscape(LyricEditor editor) {
        var escapeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        var escapeActionKey = editor.getInputMap(JComponent.WHEN_FOCUSED).get(escapeKeyStroke);
        assertThat(escapeActionKey).as("Escape must be bound in WHEN_FOCUSED map").isNotNull();
        editor.getActionMap().get(escapeActionKey).actionPerformed(
            new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, ""));
    }

    private void fireHyphen(LyricEditor editor) {
        var hyphenEvent = new KeyEvent(editor, KeyEvent.KEY_TYPED, 0L, 0, KeyEvent.VK_UNDEFINED, '-');

        for (var listener : editor.getKeyListeners()) {
            listener.keyTyped(hyphenEvent);
        }
    }

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
        assertThat(((ElementModification) notification.getMutations().get(0)).fields())
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

    private static <T> T requireLastNonNull(ArgumentCaptor<T> captor) {
        for (var i = captor.getAllValues().size() - 1; i >= 0; i--) {
            var value = captor.getAllValues().get(i);

            if (value != null) {
                return value;
            }
        }

        throw new AssertionError("expected at least one non-null captured value");
    }
}
