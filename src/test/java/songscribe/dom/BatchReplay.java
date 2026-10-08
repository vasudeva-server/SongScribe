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
import java.util.function.Consumer;

import net.engio.mbassy.listener.Handler;

import songscribe.message.MessageSubscription;
import songscribe.message.mutation.Mutation;
import songscribe.message.notification.ChangesWereReportedNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.undo.FontTarget;

/**
 * Records what a test's edits deliver on the class's bus, for replaying them through
 * {@link songscribe.undo.MutationReplayer}.
 */
final class BatchReplay {

    /**
     * The font target of a replay whose batch holds no font change. A test batch that does hold
     * one fails here, rather than being replayed into nothing.
     */
    static final FontTarget NO_FONT_CHANGES = fonts -> {
        throw new AssertionError("a replayed test batch set the document fonts");
    };

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

        /**
         * Runs {@code body} with a recorder made for it, and disposes the recorder when the body
         * returns or throws.
         *
         * @param body the edit and its assertions; reads the reports through the recorder it is
         *             handed
         */
        static void whileRecording(Consumer<ReportRecorder> body) {
            var recorder = new ReportRecorder();

            try {
                body.accept(recorder);
            } finally {
                recorder.dispose();
            }
        }
    }

}
