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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.text.AbstractDocument;

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
import songscribe.ui.OptionDialogs;
import songscribe.ui.ViewScale;
import songscribe.ui.component.score.LineComponent;

@SuppressWarnings({ "OverlyBroadThrowsClause", "DataFlowIssue" })
class LyricEditorTest extends LyricEditorTestSupport {

    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    /**
     * The staff-to-lyrics gap is 0 because nothing here asserts a verse baseline or a
     * lyrics band height; only syllable text handling is under test.
     */
    private static final LyricRenderMetrics LYRIC_METRICS =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0, 0.0);

    /** Narrow enough that a wide lyric overflows it but the bare notes still fit. */
    private static final double NARROW_LINE_WIDTH_SS = 40;

    /** A lyric wide enough to overflow {@link #NARROW_LINE_WIDTH_SS} once it must clear its neighbour. */
    private static final int WIDE_LYRIC_CHARS = 300;

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

    // -----------------------------------------------------------------------
    // Hyphen chain preview: opening and closing the editor reshapes an unclosed
    // chain that starts left of the editor, so the whole line must be repainted.
    // -----------------------------------------------------------------------

    @Test
    void testOpeningEditorRepaintsWholeLine() {
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var lineComponent = mock(LineComponent.class);
        when(score.getLineComponent(song.indexOfLine(line))).thenReturn(lineComponent);

        LyricEditor.openOn(score, line, element);

        // An unclosed hyphen chain is engraved up to the editor but starts to its left, outside the
        // editor-sized region openOn otherwise repaints.
        verify(lineComponent).repaint();
    }

    @Test
    void testDismissingEditorRepaintsWholeLine() {
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var lineComponent = mock(LineComponent.class);
        when(score.getLineComponent(song.indexOfLine(line))).thenReturn(lineComponent);

        var editor = new LyricEditor(score, line, element);
        new JPanel().add(editor);

        editor.dismiss(false);

        // Advancing with a lone hyphen writes nothing to the model, so no mutation-driven repaint
        // follows the dismiss — without this repaint the preview chain would linger on screen.
        verify(lineComponent).repaint();
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

    @Test
    void testCommitRefusesLyricTooWideForLine() {
        var element = crotchet();
        var neighbour = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> {
            line.addElement(element);
            line.addElement(neighbour);
            song.setLineWidthSs(NARROW_LINE_WIDTH_SS);
        });

        var editor = new LyricEditor(score, line, element);
        editor.setText("m".repeat(WIDE_LYRIC_CHARS));

        messageCenterMock = mockStatic(MessageCenter.class);

        try (var dialogs = mockStatic(OptionDialogs.class)) {
            editor.commit();
            dialogs.verify(() -> OptionDialogs.showErrorMessage(any(), any(), any()));
        }

        // The overflowing edit is refused: no mutation is emitted and the lyric is not written.
        verifyNoSongDidChange();
        assertThat(element.getMainLyric()).isNull();
    }

    @Test
    void testRefusedLyricShowsSingleAlertDespiteFocusReentry() {
        var element = crotchet();
        var neighbour = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> {
            line.addElement(element);
            line.addElement(neighbour);
            song.setLineWidthSs(NARROW_LINE_WIDTH_SS);
        });

        var editor = new LyricEditor(score, line, element);
        editor.setText("m".repeat(WIDE_LYRIC_CHARS));

        messageCenterMock = mockStatic(MessageCenter.class);

        try (var dialogs = mockStatic(OptionDialogs.class)) {
            // The real alert is modal and steals focus, firing focusLost, which re-enters the commit
            // path while the alert is up. Simulate that re-entry and assert only one alert is shown.
            dialogs.when(() -> OptionDialogs.showErrorMessage(any(), any(), any()))
                .thenAnswer(invocation -> {
                    editor.commit();
                    return null;
                });

            editor.commit();

            dialogs.verify(() -> OptionDialogs.showErrorMessage(any(), any(), any()), times(1));
        }
    }

    @Test
    void testAlreadyOverflowingLineDoesNotBlockFurtherEdit() {
        var element = crotchet();
        var neighbour = crotchet();
        var line = song.getLine(0);
        var overflowLyric = "m".repeat(WIDE_LYRIC_CHARS);
        song.withoutMutationTracking(() -> {
            line.addElement(element);
            line.addElement(neighbour);
            // Pre-fill a wide lyric so the line ALREADY overflows before this edit.
            setMainLyric(element, overflowLyric);
            song.setLineWidthSs(NARROW_LINE_WIDTH_SS);
        });

        var editor = new LyricEditor(score, line, element);
        // Still overflowing after the edit: an already-full line must not be blocked, so the user
        // can keep editing toward a shorter lyric that fits.
        editor.setText(overflowLyric + "x");

        messageCenterMock = mockStatic(MessageCenter.class);

        try (var dialogs = mockStatic(OptionDialogs.class)) {
            editor.commit();
            dialogs.verifyNoInteractions();
        }

        // The edit was written and a mutation emitted — the overflowing pre-state did not block it.
        captureSingleDidChange();
        assertThat(element.getMainLyric()).extracting(Lyric::text).isEqualTo(overflowLyric + "x");
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
    void testAdvanceWithChangedTextKeepsTheHyphenToTheFollowingSyllable() {
        var current = crotchet();
        current.setLyricForVerse(1, Lyric.Syllabic.BEGIN, false, "ho", Lyric.Extend.NONE);
        var nextNote = crotchet();
        nextNote.setLyricForVerse(1, Lyric.Syllabic.END, false, "ri", Lyric.Extend.NONE);
        var line = detachedLineWith(current, nextNote);

        var editor = new LyricEditor(score, line, current);
        editor.setText("ha");

        messageCenterMock = mockStatic(MessageCenter.class);
        editor.advance();

        assertThat(current.getMainLyric())
            .extracting(Lyric::text, Lyric::syllabic, Lyric::compound, Lyric::extend)
            .containsExactly("ha", Lyric.Syllabic.BEGIN, false, Lyric.Extend.NONE);
        assertThat(nextNote.getMainLyric())
            .extracting(Lyric::text, Lyric::syllabic)
            .containsExactly("ri", Lyric.Syllabic.END);

        var captor = ArgumentCaptor.forClass(LyricEditor.class);
        verify(score, atLeastOnce()).addOverlay(captor.capture());
        assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(nextNote);
    }

    @Test
    void testRetreatWithChangedTextKeepsTheHyphenToTheFollowingSyllable() {
        // The predecessor continues into the edited syllable, so the syllable's stored role is
        // MIDDLE, not BEGIN — retreating over a hyphen must leave that chain untouched.
        var previous = crotchet();
        previous.setLyricForVerse(1, Lyric.Syllabic.BEGIN, false, "do", Lyric.Extend.NONE);
        var current = crotchet();
        current.setLyricForVerse(1, Lyric.Syllabic.MIDDLE, false, "ho", Lyric.Extend.NONE);
        var nextNote = crotchet();
        nextNote.setLyricForVerse(1, Lyric.Syllabic.END, false, "ri", Lyric.Extend.NONE);
        var line = detachedLineWith(previous, current, nextNote);

        var editor = new LyricEditor(score, line, current);
        editor.setText("ha");

        messageCenterMock = mockStatic(MessageCenter.class);
        editor.retreat();

        assertThat(current.getMainLyric())
            .extracting(Lyric::text, Lyric::syllabic, Lyric::compound, Lyric::extend)
            .containsExactly("ha", Lyric.Syllabic.MIDDLE, false, Lyric.Extend.NONE);
        assertThat(previous.getMainLyric())
            .extracting(Lyric::text, Lyric::syllabic)
            .containsExactly("do", Lyric.Syllabic.BEGIN);
        assertThat(nextNote.getMainLyric())
            .extracting(Lyric::text, Lyric::syllabic)
            .containsExactly("ri", Lyric.Syllabic.END);

        // Without this, the test would pass just as well if retreat() moved forward.
        var captor = ArgumentCaptor.forClass(LyricEditor.class);
        verify(score, atLeastOnce()).addOverlay(captor.capture());
        assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(previous);
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

    /**
     * Cut/Copy/Delete are correctly greyed out while the lyric editor holds focus only
     * because opening the editor leaves no score selection behind. Nothing else locks
     * that down, so assert it here.
     */
    @Test
    void testDeselectAndOpenOnClearsScoreSelection() {
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        LyricEditor.deselectAndOpenOn(score, line, 0);

        verify(score).deselect();
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
        // openedAsExtender is true, so the editor holds the extender placeholder — dismiss
        // without touching it.
        assertThat(editor.getText()).isEqualTo("_");

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

    // Underscore at the end of unselected text is covered by
    // LyricEditorBehaviorMatrixTest.Underscore#u2_nonEmptyTextCaretEndNotExtenderStartsMelismaOnNext.

    @Test
    void testUnderscoreOnFullySelectedTextReplacesLyricWithMelisma() {
        var predecessor = crotchet();
        predecessor.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "do", Lyric.Extend.NONE);
        var element = crotchet();
        setMainLyric(element, "ho");
        var next = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(predecessor));
        song.withoutMutationTracking(() -> line.addElement(element));
        song.withoutMutationTracking(() -> line.addElement(next));

        messageCenterMock = mockStatic(MessageCenter.class);
        // The constructor prefills the existing lyric and selects it, which is the state
        // the user sees when the editor opens on a syllable.
        var editor = new LyricEditor(score, line, element);
        assertThat(editor.getText()).isEqualTo("ho");
        editor.attachListeners();
        fireUnderscore(editor);

        captureSingleDidChange();
        assertThat(predecessor.getMainLyric())
            .extracting(Lyric::text, Lyric::extend)
            .containsExactly("do", Lyric.Extend.START);
        assertThat(element.getLyricForVerse(1))
            .extracting(Lyric::text, Lyric::extend)
            .containsExactly("", Lyric.Extend.STOP);

        var captor = ArgumentCaptor.forClass(LyricEditor.class);
        verify(score, atLeastOnce()).addOverlay(captor.capture());
        assertThat(requireLastNonNull(captor).getActiveElement()).isSameAs(next);
    }

    @Test
    void testUnderscoreOnFullySelectedTextWithoutPredecessorBeepsAndKeepsText() {
        var element = crotchet();
        setMainLyric(element, "ho");
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

        assertThat(editor.getText()).isEqualTo("ho");
        assertThat(element.getMainLyric())
            .extracting(Lyric::text, Lyric::extend)
            .containsExactly("ho", Lyric.Extend.NONE);
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

    // -----------------------------------------------------------------------
    // Alt-A → long a
    // -----------------------------------------------------------------------

    @Test
    void testAltAInsertsLongAAndDropsTheOptionCharacter() {
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        editor.attachListeners();

        var typed = fireAltA(editor, false);

        assertThat(editor.getText()).isEqualTo(LyricEditor.LONG_A);
        assertThat(typed.isConsumed())
            .as("the Option-A character must not reach the document on top of the long a")
            .isTrue();
    }

    @Test
    void testAltShiftAInsertsCapitalLongA() {
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        editor.attachListeners();

        fireAltA(editor, true);

        assertThat(editor.getText()).isEqualTo(LyricEditor.LONG_A_CAPITAL);
    }

    @Test
    void testAltAReplacesTheSelectedText() {
        var element = crotchet();
        var existingLyric = "ho";
        setMainLyric(element, existingLyric);
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        // The constructor prefills and selects the existing lyric.
        var editor = new LyricEditor(score, line, element);
        editor.attachListeners();

        // Assert the precondition rather than trusting it: without a live selection this
        // would silently become an insert-at-caret test under a name promising a replace.
        assertThat(editor.getSelectedText())
            .as("the constructor must leave the existing lyric selected")
            .isEqualTo(existingLyric);

        fireAltA(editor, false);

        assertThat(editor.getText()).isEqualTo(LyricEditor.LONG_A);
    }

    @Test
    void testAltAObeysTheLengthLimit() {
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        var fullText = "a".repeat(LyricEditor.MAX_LENGTH_CHARS);
        editor.setText(fullText);
        editor.setCaretPosition(fullText.length());
        editor.attachListeners();

        var toolkitMock = mock(Toolkit.class);

        try (var toolkitStatic = mockStatic(Toolkit.class)) {
            toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkitMock);
            fireAltA(editor, false);
            verify(toolkitMock).beep();
        }

        assertThat(editor.getText()).isEqualTo(fullText);
    }

    /**
     * These tests drive the editor's key listeners directly, so Swing's own character
     * insertion never runs. Leaving a key typed event unconsumed is therefore the most a
     * unit test can check — whether the character then lands in the document is Swing's
     * part, and only using the app on a Mac confirms the whole path.
     */
    @Test
    void testOtherOptionCharactersAreNotSwallowed() {
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        editor.attachListeners();

        // The drop is scoped to the key typed event of the Alt-A press that set it up, so
        // an accented character typed right afterwards must be left for Swing to insert.
        fireAltA(editor, false);
        var typed = fireOptionC(editor);

        assertThat(typed.isConsumed())
            .as("Option-C must not be dropped by the preceding Alt-A")
            .isFalse();
        assertThat(editor.getText())
            .as("Option-C must not be mistaken for the shortcut and insert a second long a")
            .isEqualTo(LyricEditor.LONG_A);
    }

    @Test
    void testAltAInsertsAtTheCaretInsideExistingText() {
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        editor.setText("cat");
        // Caret between "c" and "at", nothing selected — the everyday case neither the
        // empty-field nor the fully-selected test covers.
        editor.setCaretPosition(1);
        editor.attachListeners();

        fireAltA(editor, false);

        assertThat(editor.getText()).isEqualTo("c" + LyricEditor.LONG_A + "at");
    }

    // Alt-A must not claim these combinations: doing so would swallow a system shortcut, or
    // turn an AltGr layout's accented letter into a long a.

    @Test
    void testControlAltAIsNotTheLongAShortcut() {
        assertAltACombinationIsIgnored(InputEvent.CTRL_DOWN_MASK);
    }

    @Test
    void testMetaAltAIsNotTheLongAShortcut() {
        assertAltACombinationIsIgnored(InputEvent.META_DOWN_MASK);
    }

    @Test
    void testAltGraphAIsNotTheLongAShortcut() {
        assertAltACombinationIsIgnored(InputEvent.ALT_GRAPH_DOWN_MASK);
    }

    /**
     * Fires Alt-A with {@code extraModifier} also held and asserts the editor ignored it
     * completely: no long a inserted, and the character the layout would produce left for
     * Swing rather than dropped.
     */
    private void assertAltACombinationIsIgnored(int extraModifier) {
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        editor.attachListeners();

        var typed = fireAltAWithExtraModifier(editor, extraModifier);

        assertThat(editor.getText()).as("no long a may be inserted").isEmpty();
        assertThat(typed.isConsumed()).as("the layout's own character must survive").isFalse();
    }

    // -----------------------------------------------------------------------
    // Alt-N → n with tilde
    // -----------------------------------------------------------------------

    @Test
    void testAltNInsertsNTildeAndDropsTheOptionCharacter() {
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        editor.attachListeners();

        var typed = fireAltN(editor, false);

        assertThat(editor.getText()).isEqualTo(LyricEditor.N_TILDE);
        assertThat(typed.isConsumed())
            .as("the Option-N character must not reach the document on top of the n with tilde")
            .isTrue();
    }

    @Test
    void testAltShiftNInsertsCapitalNTilde() {
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        editor.attachListeners();

        fireAltN(editor, true);

        assertThat(editor.getText()).isEqualTo(LyricEditor.N_TILDE_CAPITAL);
    }

    // Selection replacement, the length limit, and caret-position insertion all happen in code
    // the two shortcuts share, so the Alt-A tests above already cover them for Alt-N too. Only
    // what is specific to Alt-N — that the N key reaches the n-with-tilde constants, and that
    // the shared modifier guard is asked about the N key — is tested here.

    @Test
    void testAltAAndAltNDoNotInterfereWithEachOther() {
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        editor.attachListeners();

        fireAltA(editor, false);
        // Outside the EDT, Swing's caret does not auto-advance past an insertion, so the
        // caret is moved explicitly rather than relying on that EDT-only behavior.
        editor.setCaretPosition(editor.getText().length());
        fireAltN(editor, false);

        assertThat(editor.getText()).isEqualTo(LyricEditor.LONG_A + LyricEditor.N_TILDE);
    }

    /**
     * The Alt-A tests above already prove the shared guard rejects each of Ctrl, Meta, and
     * AltGraph, and that guard cannot behave differently per letter. One combination is enough
     * here to prove the remaining letter-specific thing: that the n-with-tilde check asks the
     * guard about the N key rather than a copy-pasted A.
     */
    @Test
    void testControlAltNIsNotTheNTildeShortcut() {
        assertAltNCombinationIsIgnored(InputEvent.CTRL_DOWN_MASK);
    }

    /**
     * Alt is what turns the N key into a shortcut, so the plain key must be left alone. Without
     * this, dropping the Alt check would make every typed {@code n} come out as an n with tilde
     * and no other test would notice.
     */
    @Test
    void testPlainNIsNotTheNTildeShortcut() {
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        editor.attachListeners();

        var typed = firePlainN(editor);

        assertThat(editor.getText()).as("no n with tilde may be inserted").isEmpty();
        assertThat(typed.isConsumed()).as("the plain n must be left for Swing to insert").isFalse();
    }

    /**
     * Fires Alt-N with {@code extraModifier} also held and asserts the editor ignored it
     * completely: no n with tilde inserted, and the character the layout would produce left
     * for Swing rather than dropped.
     */
    private void assertAltNCombinationIsIgnored(int extraModifier) {
        var element = crotchet();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        editor.attachListeners();

        var typed = fireAltNWithExtraModifier(editor, extraModifier);

        assertThat(editor.getText()).as("no n with tilde may be inserted").isEmpty();
        assertThat(typed.isConsumed()).as("the layout's own character must survive").isFalse();
    }

    /**
     * A composing dead key like Option-N clears the selection it is about to compose over by
     * routing a {@code null} replacement text through the installed
     * {@link javax.swing.text.DocumentFilter}, per {@link javax.swing.text.JTextComponent}'s
     * documented contract that a null text means "nothing to insert" — not "reject the whole
     * edit." The removal must still happen, or the old selection lingers and the composed
     * character lands appended after it instead of in its place. Reproduced here by driving
     * the document directly, since unit tests cannot simulate a real input method event.
     */
    @Test
    void testNullReplacementTextFromInputMethodStillRemovesTheSelection() {
        var element = crotchet();
        var existingLyric = "ho";
        setMainLyric(element, existingLyric);
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(element));

        var editor = new LyricEditor(score, line, element);
        editor.attachListeners();

        var document = (AbstractDocument) editor.getDocument();

        assertThatCode(() -> document.replace(0, document.getLength(), null, null))
            .doesNotThrowAnyException();
        assertThat(editor.getText()).isEmpty();
    }
}
