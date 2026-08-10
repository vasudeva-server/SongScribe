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
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementFactory;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.layout.LyricRenderMetrics;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.ViewScale;

@SuppressWarnings("resource")
abstract class LyricEditorTestSupport extends UnitTest {

    protected static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    /**
     * The staff-to-lyrics gap is 0 because nothing here asserts a verse baseline or a
     * lyrics band height; only syllable text handling is under test.
     */
    protected static final LyricRenderMetrics LYRIC_METRICS =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0, 0.0);

    /** What a US macOS layout types for Option-A and Option-Shift-A. */
    private static final char OPTION_A = 'å';
    private static final char OPTION_A_CAPITAL = 'Å';

    /**
     * Stands in for the key typed event a US macOS layout eventually delivers for Option-N,
     * a dead key that defers its own character rather than typing it immediately. The editor
     * does not model that deferral; it only needs the event to exist so the drop can be
     * exercised.
     */
    private static final char OPTION_N = '~';

    /** What every layout types for the N key on its own, with no modifier held. */
    private static final char PLAIN_N = 'n';

    /** What the same layout types for Option-C, an Option combination the editor leaves alone. */
    private static final char OPTION_C = 'ç';

    /**
     * Stands in for whatever an Alt-A combination the editor must ignore would produce — an
     * AltGr layout's accented letter, say. The editor decides on the key code and modifiers
     * alone and never reads this character, so its exact value does not matter.
     */
    private static final char EXCLUDED_COMBINATION_CHAR = 'ą';

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
        when(score.getViewScale()).thenReturn(new ViewScale());
        when(score.getSong()).thenReturn(song);
        when(score.getLineComponent(anyInt())).thenReturn(null);
        when(score.getLayout()).thenReturn(new BorderLayout());
    }

    @Nullable
    protected MockedStatic<MessageCenter> messageCenterMock;

    protected static StaffElement crotchet() {
        return StaffElementFactory.crotchet();
    }

    protected static StaffElement crotchetRest() {
        return StaffElementFactory.crotchetRest();
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

    /**
     * Simulates a keystroke the way the platform delivers it: a key pressed for {@code keyCode}
     * with {@code modifiers} held, followed by a key typed carrying {@code typedChar}, the
     * character the layout produces for that combination. Returns that key typed event so the
     * caller can assert whether the editor dropped it or left it for Swing to insert.
     */
    private static KeyEvent fireKeyCombination(
        LyricEditor editor,
        int keyCode,
        int modifiers,
        char typedChar
    ) {
        var pressed = new KeyEvent(editor, KeyEvent.KEY_PRESSED, 0L, modifiers, keyCode, typedChar);
        var typed =
            new KeyEvent(editor, KeyEvent.KEY_TYPED, 0L, modifiers, KeyEvent.VK_UNDEFINED, typedChar);

        for (var listener : editor.getKeyListeners()) {
            listener.keyPressed(pressed);
        }

        for (var listener : editor.getKeyListeners()) {
            listener.keyTyped(typed);
        }

        return typed;
    }

    /** Simulates Alt-A, or Alt-Shift-A when {@code shift} — the long-a mapping. */
    protected KeyEvent fireAltA(LyricEditor editor, boolean shift) {
        var modifiers = InputEvent.ALT_DOWN_MASK | (shift ? InputEvent.SHIFT_DOWN_MASK : 0);

        return fireKeyCombination(
            editor, KeyEvent.VK_A, modifiers, shift ? OPTION_A_CAPITAL : OPTION_A);
    }

    /** Simulates Alt-N, or Alt-Shift-N when {@code shift} — the n-with-tilde mapping. */
    protected KeyEvent fireAltN(LyricEditor editor, boolean shift) {
        var modifiers = InputEvent.ALT_DOWN_MASK | (shift ? InputEvent.SHIFT_DOWN_MASK : 0);

        return fireKeyCombination(editor, KeyEvent.VK_N, modifiers, OPTION_N);
    }

    /**
     * Simulates the N key pressed on its own, with no modifier held — the everyday keystroke
     * the n-with-tilde mapping must never claim. Returns the key typed event so the caller can
     * assert it was left alone.
     */
    protected KeyEvent firePlainN(LyricEditor editor) {
        return fireKeyCombination(editor, KeyEvent.VK_N, 0, PLAIN_N);
    }

    /** Simulates Option-C, an Option combination the editor does not map. */
    protected KeyEvent fireOptionC(LyricEditor editor) {
        return fireKeyCombination(editor, KeyEvent.VK_C, InputEvent.ALT_DOWN_MASK, OPTION_C);
    }

    /**
     * Simulates Alt-A with {@code extraModifier} also held — one of the combinations the
     * editor must not claim, so it never shadows a system shortcut or an AltGr character.
     * Returns the key typed event so the caller can assert it was left alone.
     */
    protected KeyEvent fireAltAWithExtraModifier(LyricEditor editor, int extraModifier) {
        return fireKeyCombination(
            editor,
            KeyEvent.VK_A,
            InputEvent.ALT_DOWN_MASK | extraModifier,
            EXCLUDED_COMBINATION_CHAR);
    }

    /**
     * Simulates Alt-N with {@code extraModifier} also held — one of the combinations the
     * editor must not claim, so it never shadows a system shortcut or an AltGr character.
     * Returns the key typed event so the caller can assert it was left alone.
     */
    protected KeyEvent fireAltNWithExtraModifier(LyricEditor editor, int extraModifier) {
        return fireKeyCombination(
            editor,
            KeyEvent.VK_N,
            InputEvent.ALT_DOWN_MASK | extraModifier,
            EXCLUDED_COMBINATION_CHAR);
    }

    protected void fireFocusLost(LyricEditor editor) {
        var focusEvent = new FocusEvent(editor, FocusEvent.FOCUS_LOST);

        for (var listener : editor.getFocusListeners()) {
            listener.focusLost(focusEvent);
        }
    }
}
