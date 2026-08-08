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

package songscribe.undo;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import net.engio.mbassy.listener.Handler;

import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.font.DocumentFonts;
import songscribe.io.SongIO;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.Mutation;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.component.ScoreView;
import songscribe.undo.MutationReplayer;

/**
 * Shared helpers for the undo/redo test classes.
 *
 * <p>The central pattern is: build a {@link Song} in a known state, drive a
 * <em>real</em> edit inside a modification bracket, and capture the exact
 * {@link SongDidChangeNotification} batch the edit posted — companion mutations
 * included. That captured batch is what the engine would push and later replay,
 * so replaying it is a faithful test of undo/redo (a hand-built lone {@link Mutation}
 * would miss the companion removals that real edits emit).
 *
 * <p>Deep equality of two {@link Song}s is compared via their native serialization:
 * the save format is value-based (no element identity, no timestamps), so two songs
 * that serialize identically hold the same document state.
 */
public final class UndoTestSupport {

    private UndoTestSupport() {
    }

    /** Serializes a song to its native XML form for value-based deep comparison. */
    public static String serialize(Song song) {
        var stringWriter = new StringWriter();
        var printWriter = new PrintWriter(stringWriter);
        SongIO.writeSong(song, DocumentFonts.defaultFonts(), printWriter);
        printWriter.flush();
        return stringWriter.toString();
    }

    /**
     * Runs {@code edit} inside one modification bracket and returns the recorded
     * batch from the resulting notification. Throws if the edit recorded nothing
     * (which would indicate a broken test setup, not a passing no-op).
     *
     * <p>For a raw DOM mutator, which opens no bracket of its own. The bracket opened here
     * swallows any bracket {@code edit} does open, so this cannot tell one undo step from
     * several — for an edit that brackets itself, use {@link #captureSingleBatch}.
     */
    public static List<Mutation> captureBatch(Song song, Runnable edit) {
        return onlyBatchOf(song, recordNotifications(() -> song.withModification(edit)));
    }

    /**
     * Runs {@code edit} with no bracket around it and returns the batch from the single
     * notification it must have posted — the assertion {@link #captureBatch} structurally
     * cannot make. Use this for anything that brackets itself, such as a controller action.
     *
     * <p>An edit that applies a companion mutation in a <em>second</em> bracket opened after
     * its own has closed posts two notifications, and two notifications are two undo steps:
     * the user has to press Undo twice and passes through an intermediate state they never
     * asked for. Wrapped in an outer bracket that difference is invisible, because only the
     * outermost bracket publishes — which is exactly why such a bug survives a
     * {@code captureBatch}-based undo/redo round trip.
     */
    public static List<Mutation> captureSingleBatch(Song song, Runnable edit) {
        var captured = recordNotifications(edit);

        if (captured.size() > 1) {
            throw new IllegalStateException(
                "The edit posted " + captured.size() + " SongDidChangeNotifications; a single"
                    + " logical action must be a single undo step");
        }

        return onlyBatchOf(song, captured);
    }

    /** Every {@link SongDidChangeNotification} batch {@code action} posts, in order. */
    private static List<List<Mutation>> recordNotifications(Runnable action) {
        var captured = new ArrayList<List<Mutation>>();

        // Highest priority so this capture handler runs before any other subscriber on
        // the shared bus: a co-subscriber left alive by an earlier test class could throw
        // and, under the project's publication-error handling, stop delivery to
        // lower-priority handlers — losing the capture. Running first side-steps that.
        var listener = new Object() {
            @Handler(priority = Integer.MAX_VALUE)
            void onSongDidChange(SongDidChangeNotification notification) {
                captured.add(notification.getMutations());
            }
        };

        MessageCenter.subscribe(listener);

        try {
            action.run();
        } finally {
            MessageCenter.unsubscribe(listener);
        }

        return captured;
    }

    private static List<Mutation> onlyBatchOf(Song song, List<List<Mutation>> captured) {
        if (captured.isEmpty()) {
            throw new IllegalStateException(
                "The edit posted no SongDidChangeNotification (trackingSuspended="
                    + song.isMutationTrackingSuspended() + ", replaying=" + song.isReplaying() + ')');
        }

        return captured.getFirst();
    }

    /** A mock {@link ScoreView} whose {@code getSong()} returns {@code song}. */
    public static ScoreView scoreViewFor(Song song) {
        var scoreView = mock(ScoreView.class);
        when(scoreView.getSong()).thenReturn(song);
        return scoreView;
    }

    /**
     * Replays {@code batch} in reverse (undo) order inside a bracket and replay mode,
     * exactly as {@link UndoController#undo()} does.
     */
    public static void replayUndo(ScoreView scoreView, List<? extends Mutation> batch) {
        var song = scoreView.getSong();
        song.withModification(() -> song.withReplay(() -> {
            for (var i = batch.size() - 1; i >= 0; i--) {
                MutationReplayer.applyUndo(scoreView, batch.get(i));
            }
        }));
    }

    /**
     * Replays {@code batch} in forward (redo) order inside a bracket and replay mode,
     * exactly as {@link UndoController#redo()} does.
     */
    public static void replayRedo(ScoreView scoreView, List<? extends Mutation> batch) {
        var song = scoreView.getSong();
        song.withModification(() -> song.withReplay(() -> {
            for (var mutation : batch) {
                MutationReplayer.applyRedo(scoreView, mutation);
            }
        }));
    }

    /** Adds {@code count} crotchets to {@code line} without recording mutations. */
    public static void addCrotchets(Song song, Line line, int count) {
        song.withoutMutationTracking(() -> {
            for (var i = 0; i < count; i++) {
                line.addElement(ElementType.CROTCHET.newInstance());
            }
        });
    }

    static StaffElement crotchet() {
        return ElementType.CROTCHET.newInstance();
    }
}
