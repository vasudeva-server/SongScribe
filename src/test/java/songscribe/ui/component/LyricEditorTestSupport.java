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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.*;

import javax.swing.JComponent;
import javax.swing.KeyStroke;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.layout.LyricRenderMetrics;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.notification.SongDidChangeNotification;

@SuppressWarnings("resource")
abstract class LyricEditorTestSupport extends UnitTest {

    protected static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    protected static final LyricRenderMetrics LYRIC_METRICS =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0);

    protected Song song;
    protected ScoreView score;

    @BeforeEach
    void setUpLyricEditorSupport() {
        song = new Song();
        score = mock(ScoreView.class);
        when(score.getLyricRenderMetrics()).thenReturn(LYRIC_METRICS);
        var documentFonts = new DocumentFonts();
        documentFonts.setFont(FontKey.LYRICS, LYRICS_FONT);
        when(score.getDocumentFonts()).thenReturn(documentFonts);
        when(score.getSong()).thenReturn(song);
        when(score.getLineComponent(anyInt())).thenReturn(null);
        when(score.getLayout()).thenReturn(new BorderLayout());
    }

    @Nullable
    protected MockedStatic<MessageCenter> messageCenterMock;

    protected static StaffElement crotchet() {
        return ElementType.CROTCHET.newInstance();
    }

    protected static StaffElement crotchetRest() {
        return ElementType.CROTCHET_REST.newInstance();
    }

    protected static void setMainLyric(StaffElement element, String text) {
        element.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, text, Lyric.Extend.NONE);
    }

    protected static Line detachedLineWith(StaffElement... elements) {
        var line = detachedLine();

        for (var element : elements) {
            line.addElement(element);
        }

        return line;
    }

    protected MockedStatic<MessageCenter> requireMessageCenterMock() {
        if (messageCenterMock == null) {
            throw new IllegalStateException("messageCenterMock must be initialized");
        }

        return messageCenterMock;
    }

    protected SongDidChangeNotification captureSingleDidChange() {
        var captor = ArgumentCaptor.forClass(Message.class);
        requireMessageCenterMock().verify(() -> MessageCenter.post(captor.capture()), atLeastOnce());

        var didChanges = captor.getAllValues().stream()
            .filter(m -> m instanceof SongDidChangeNotification)
            .map(m -> (SongDidChangeNotification) m)
            .toList();

        assertThat(didChanges)
            .as("expected exactly one SongDidChangeNotification, got: %s", didChanges)
            .hasSize(1);

        return didChanges.getFirst();
    }

    protected void verifyNoSongDidChange() {
        requireMessageCenterMock().verify(
            () -> MessageCenter.post(any(SongDidChangeNotification.class)),
            never()
        );
    }

    protected static <T> T requireLastNonNull(ArgumentCaptor<T> captor) {
        for (var i = captor.getAllValues().size() - 1; i >= 0; i--) {
            var value = captor.getAllValues().get(i);

            //noinspection ConstantValue -- need for NullAway
            if (value != null) {
                return value;
            }
        }

        throw new AssertionError("expected at least one non-null captured value");
    }

    protected void fireEscape(LyricEditor editor) {
        var escapeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        var escapeActionKey = editor.getInputMap(JComponent.WHEN_FOCUSED).get(escapeKeyStroke);
        assertThat(escapeActionKey).as("Escape must be bound in WHEN_FOCUSED map").isNotNull();
        editor.getActionMap().get(escapeActionKey).actionPerformed(
            new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, ""));
    }

    protected void fireHyphen(LyricEditor editor) {
        var hyphenEvent = new KeyEvent(editor, KeyEvent.KEY_TYPED, 0L, 0, KeyEvent.VK_UNDEFINED, '-');

        for (var listener : editor.getKeyListeners()) {
            listener.keyTyped(hyphenEvent);
        }
    }

    protected void fireSpace(LyricEditor editor) {
        var event = new KeyEvent(editor, KeyEvent.KEY_TYPED, 0L, 0, KeyEvent.VK_UNDEFINED, ' ');

        for (var listener : editor.getKeyListeners()) {
            listener.keyTyped(event);
        }
    }

    protected void fireEquals(LyricEditor editor) {
        var event = new KeyEvent(editor, KeyEvent.KEY_TYPED, 0L, 0, KeyEvent.VK_UNDEFINED, '=');

        for (var listener : editor.getKeyListeners()) {
            listener.keyTyped(event);
        }
    }

    protected void firePlus(LyricEditor editor) {
        var event = new KeyEvent(editor, KeyEvent.KEY_TYPED, 0L, 0, KeyEvent.VK_UNDEFINED, '+');

        for (var listener : editor.getKeyListeners()) {
            listener.keyTyped(event);
        }
    }

    protected void fireUnderscore(LyricEditor editor) {
        var event = new KeyEvent(editor, KeyEvent.KEY_TYPED, 0L, 0, KeyEvent.VK_UNDEFINED, '_');

        for (var listener : editor.getKeyListeners()) {
            listener.keyTyped(event);
        }
    }

    protected void fireTab(LyricEditor editor) {
        var tabKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0);
        var tabActionKey = editor.getInputMap(JComponent.WHEN_FOCUSED).get(tabKeyStroke);
        assertThat(tabActionKey).as("Tab must be bound in WHEN_FOCUSED map").isNotNull();
        editor.getActionMap().get(tabActionKey).actionPerformed(
            new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, ""));
    }

    protected void fireShiftTab(LyricEditor editor) {
        var shiftTabKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK);
        var shiftTabActionKey = editor.getInputMap(JComponent.WHEN_FOCUSED).get(shiftTabKeyStroke);
        assertThat(shiftTabActionKey).as("Shift+Tab must be bound in WHEN_FOCUSED map").isNotNull();
        editor.getActionMap().get(shiftTabActionKey).actionPerformed(
            new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, ""));
    }

    protected void fireEnter(LyricEditor editor) {
        var enterKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
        var enterActionKey = editor.getInputMap(JComponent.WHEN_FOCUSED).get(enterKeyStroke);
        assertThat(enterActionKey).as("Enter must be bound in WHEN_FOCUSED map").isNotNull();
        editor.getActionMap().get(enterActionKey).actionPerformed(
            new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, ""));
    }

    protected void fireWordChar(LyricEditor editor, char ch) {
        var event = new KeyEvent(editor, KeyEvent.KEY_TYPED, 0L, 0, KeyEvent.VK_UNDEFINED, ch);

        for (var listener : editor.getKeyListeners()) {
            listener.keyTyped(event);
        }
    }

    protected void fireFocusLost(LyricEditor editor) {
        var focusEvent = new FocusEvent(editor, FocusEvent.FOCUS_LOST);

        for (var listener : editor.getFocusListeners()) {
            listener.focusLost(focusEvent);
        }
    }
}
