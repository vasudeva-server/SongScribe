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

package songscribe.dom;

import java.util.ArrayList;
import java.util.List;

import net.engio.mbassy.listener.Handler;

import songscribe.message.MessageSubscription;
import songscribe.message.mutation.Mutation;
import songscribe.message.notification.ChangesWereReportedNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.component.ScoreView;
import songscribe.undo.MutationReplayer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Records the batches a song delivers and replays them the way the undo engine does: replay mode
 * entered first, with the modification bracket opened and closed inside it.
 */
final class BatchReplay {

    private BatchReplay() {
    }

    /**
     * Takes the batch of each modification bracket that closes on the class's bus. The caller
     * disposes it.
     */
    static final class BatchRecorder {
        private final List<List<Mutation>> batches = new ArrayList<>();
        private final MessageSubscription subscription = new MessageSubscription(this);

        @Handler
        void songDidChange(SongDidChangeNotification notification) {
            batches.add(List.copyOf(notification.getMutations()));
        }

        /** The batches delivered since this recorder was made, in delivery order. */
        List<List<Mutation>> batches() {
            return batches;
        }

        void dispose() {
            subscription.dispose();
        }
    }

    /**
     * Takes the entries of each change report that an edit delivers on the class's bus, in
     * delivery order. The caller disposes it.
     */
    static final class ReportRecorder {
        private final List<List<ReportedChange>> reports = new ArrayList<>();
        private final MessageSubscription subscription = new MessageSubscription(this);

        @Handler
        void changesWereReported(ChangesWereReportedNotification notification) {
            reports.add(List.copyOf(notification.getReport().changes()));
        }

        /** The entries of each report delivered since this recorder was made. */
        List<List<ReportedChange>> reports() {
            return reports;
        }

        void dispose() {
            subscription.dispose();
        }
    }

    /** Replays {@code batch} backwards, as the undo engine does. */
    static void undo(Song song, List<Mutation> batch) {
        var scoreView = scoreViewOf(song);

        song.withReplay(() -> song.withModification(() -> {
            for (var i = batch.size() - 1; i >= 0; i--) {
                MutationReplayer.applyUndo(scoreView, batch.get(i));
            }
        }));
    }

    /** Replays {@code batch} forwards, as the undo engine does. */
    static void redo(Song song, List<Mutation> batch) {
        var scoreView = scoreViewOf(song);

        song.withReplay(() -> song.withModification(() -> {
            for (var mutation : batch) {
                MutationReplayer.applyRedo(scoreView, mutation);
            }
        }));
    }

    private static ScoreView scoreViewOf(Song song) {
        var scoreView = mock(ScoreView.class);
        when(scoreView.getSong()).thenReturn(song);

        return scoreView;
    }
}
