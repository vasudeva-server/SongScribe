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
import static org.mockito.Mockito.mockStatic;

import java.awt.Font;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.FontChange;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.model.Song;

/**
 * Verifies that {@link ScoreView#setFonts(DocumentFonts)} is the single, undo-bracketed
 * write entry for document fonts: equal snapshots are no-ops, distinct snapshots
 * produce exactly one {@link FontChange} mutation regardless of how many roles
 * differ, and {@code documentFonts} is the new instance afterwards.
 */
class ScoreViewSetFontsTest extends UnitTest {

    private static final String SERIF = "Serif";
    private static final String SANS = "SansSerif";
    private static final int TITLE_SIZE = 24;
    private static final int LYRICS_SIZE = 14;
    private static final int ATTR_SIZE = 12;
    private static final int ANNO_SIZE = 11;
    private static final int FOOT_SIZE = 10;
    private static final int BANGLA_SIZE = 16;

    private ScoreView scoreView;
    private Song song;
    private MockedStatic<MessageCenter> messageCenterMock;

    @BeforeEach
    void setUp() {
        // Construct before mocking so any bus interactions during setup
        // go to the real (unobserved) bus.
        scoreView = new ScoreView(null);
        song = new Song();
        scoreView.setSong(song);
        // Bootstrap documentFonts from prefs (mirrors the new-document path).
        scoreView.installDocumentFonts(DocumentFonts.defaultsFromPrefs());

        messageCenterMock = mockStatic(MessageCenter.class);
    }

    @AfterEach
    void tearDown() {
        messageCenterMock.close();
    }

    @Test
    void testSetFontsWithEqualSnapshotIsNoOp() {
        var before = scoreView.getDocumentFonts();
        var copy = new DocumentFonts(before);

        scoreView.setFonts(copy);

        messageCenterMock.verify(() -> MessageCenter.post(any()), org.mockito.Mockito.times(0));
        assertThat(scoreView.getDocumentFonts()).isSameAs(before);
        assertThat(song.isModified()).isFalse();
    }

    @Test
    void testSetFontsWithSingleRoleChangePostsOneFontChange() {
        var before = scoreView.getDocumentFonts();
        var newFonts = new DocumentFonts(before);
        newFonts.setFont(FontKey.TITLE, new Font(SERIF, Font.BOLD, TITLE_SIZE));

        scoreView.setFonts(newFonts);

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(1);
        var fc = (FontChange) notification.getMutations().getFirst();
        assertThat(fc.oldFonts()).isEqualTo(before);
        assertThat(fc.newFonts()).isEqualTo(newFonts);
        assertThat(scoreView.getDocumentFonts()).isSameAs(newFonts);
        assertThat(scoreView.getFont(FontKey.TITLE)).isEqualTo(newFonts.getFont(FontKey.TITLE));
        assertThat(song.isModified()).isTrue();
    }

    @Test
    void testSetFontsWithAllRolesChangedPostsExactlyOneFontChange() {
        var before = scoreView.getDocumentFonts();
        var newFonts = new DocumentFonts();
        newFonts.setFont(FontKey.TITLE,       new Font(SERIF, Font.BOLD,  TITLE_SIZE));
        newFonts.setFont(FontKey.LYRICS,      new Font(SERIF, Font.PLAIN, LYRICS_SIZE));
        newFonts.setFont(FontKey.ATTRIBUTION, new Font(SANS,  Font.ITALIC, ATTR_SIZE));
        newFonts.setFont(FontKey.ANNOTATION,  new Font(SANS,  Font.PLAIN, ANNO_SIZE));
        newFonts.setFont(FontKey.FOOTNOTE,    new Font(SERIF, Font.PLAIN, FOOT_SIZE));
        newFonts.setFont(FontKey.BANGLA,      new Font(SERIF, Font.PLAIN, BANGLA_SIZE));

        scoreView.setFonts(newFonts);

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations())
            .as("multi-role change must be one FontChange, not six")
            .hasSize(1);
        var fc = (FontChange) notification.getMutations().getFirst();
        assertThat(fc.oldFonts()).isEqualTo(before);
        assertThat(fc.newFonts()).isEqualTo(newFonts);
    }

    @Test
    void testReplayWithOldFontsRestoresPriorState() {
        var before = scoreView.getDocumentFonts();
        var snapshotBefore = new DocumentFonts(before);

        var newFonts = new DocumentFonts(before);
        newFonts.setFont(FontKey.TITLE, new Font(SERIF, Font.BOLD, TITLE_SIZE));
        scoreView.setFonts(newFonts);
        assertThat(scoreView.getDocumentFonts()).isSameAs(newFonts);

        // Simulates the bracket-driven undo path described in the plan: replaying
        // setFonts with the pre-change snapshot restores the prior state. The
        // mutation bracket records a second FontChange, which is the expected
        // behavior — undo replay is itself a recorded operation today.
        scoreView.setFonts(snapshotBefore);

        assertThat(scoreView.getDocumentFonts()).isSameAs(snapshotBefore);
        assertThat(scoreView.getFont(FontKey.TITLE))
            .isEqualTo(before.getFont(FontKey.TITLE));
    }

    private SongDidChangeNotification captureSingleDidChange() {
        var captor = ArgumentCaptor.forClass(Message.class);
        messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
        var didChanges = captor.getAllValues().stream()
            .filter(m -> m instanceof SongDidChangeNotification)
            .map(m -> (SongDidChangeNotification) m)
            .toList();

        assertThat(didChanges)
            .as("expected exactly one SongDidChangeNotification, got: %s", didChanges)
            .hasSize(1);

        return didChanges.getFirst();
    }
}
