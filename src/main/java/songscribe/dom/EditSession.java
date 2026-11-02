/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.dom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.Mutation;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.undo.UndoController;

/**
 * The recording policy of a song open for editing: bracket, suspension and replay modes, each
 * depth-counted so nesting is safe. A replay judges nothing, so an undo or a redo always delivers
 * an empty report.
 *
 * <p>Suspension and replay are not interchangeable: suspension records nothing, so it leaves no
 * undo entry, no modified flag and no report, while a replay still records, and only stops the
 * companion side-work its batch already carries. The outermost bracket alone decides what counts
 * as one edit.
 *
 * <p>EDT-only; no synchronization.
 */
final class EditSession extends ModificationSession {

    private final Song song;

    // Modification bracket depth counter. Mutations and reported changes are accumulated
    // while > 0 and flushed as a single SongDidChangeNotification when depth returns to 0.
    private int modificationDepth = 0;

    // Suspension depth counter. While > 0, mutations apply but nothing is recorded:
    // applyChange runs the mutator without requiring an open bracket, and report records
    // nothing. Test setup and grace mode hold it to populate lines without recording undo
    // history.
    private int suspensionDepth = 0;

    // While on, undo/redo is mechanically re-applying a recorded mutation batch: mutations are
    // still recorded into the open bracket, but companion side-work (auto-maintenance, span
    // invalidation, merging) is suppressed and the Line terminal guards are bypassed, because
    // the recorded batch already contains every change.
    private final NestingCounter replayMode = new NestingCounter();

    // Non-null exactly while a bracket is open.
    private @Nullable OpenEdit openEdit = null;

    private final ModificationRules rules = new ModificationRules();

    private static final class OpenEdit {

        // The explicit bracket label, or the Tier-A pending op-name held by UndoController,
        // resolved when the outermost bracket opened; null selects the type-based label.
        private final @Nullable String opName;

        // Mutable: each removal a modification rule records is appended while the rules run.
        private final List<Mutation> batch = new ArrayList<>();

        private final ChangeReport.Builder report;

        OpenEdit(Song song, @Nullable String opName) {
            this.opName = opName;
            this.report = new ChangeReport.Builder(song);
        }
    }

    EditSession(Song song) {
        this.song = song;
    }

    @Override
    boolean isModifying() {
        return modificationDepth > 0;
    }

    @Override
    boolean isMutationTrackingSuspended() {
        return suspensionDepth > 0;
    }

    @Override
    boolean isReplaying() {
        return replayMode.isOn();
    }

    @Override
    boolean enforcesEditInvariants() {
        return !isMutationTrackingSuspended();
    }

    @Override
    void withoutMutationTracking(Runnable body) {
        withoutMutationTrackingResult(asSupplier(body));
    }

    @Override
    <T extends @Nullable Object> T withoutMutationTrackingResult(Supplier<T> body) {
        suspensionDepth++;

        try {
            return body.get();
        } finally {
            suspensionDepth--;
        }
    }

    /**
     * Companion side-work must not re-run, because the replayed batch already carries it, and
     * the {@link Line} terminal guards must accept the intermediate states a replay passes
     * through.
     */
    @Override
    void replay(Runnable body) {
        replayMode.during(() -> inBracket(null, asSupplier(body)));
    }

    private void beginModification(@Nullable String explicitLabel) {
        if (modificationDepth == 0) {
            // explicitLabel is the Tier-B name from the labeled withModification overload;
            // otherwise the Tier-A name that UIAction.actionPerformed left pending in UndoController.
            openEdit = new OpenEdit(song, explicitLabel != null ? explicitLabel : UndoController.getPendingOpName());
        }

        modificationDepth++;
    }

    /**
     * The one statement of when the modification rules ({@link ModificationRules}) run: once per
     * edit, just before the outermost bracket closes, so the removals they record undo with the
     * edit. They do not run on an empty batch (so never while tracking is suspended), inside a
     * replay, or when the body threw, because that edit stopped part way.
     */
    private void endModification() {
        var edit = requireOpenEdit("endModification");

        try {
            if (modificationDepth == 1 && !edit.batch.isEmpty() && owesRules()) {
                // A copy: each removal a rule records is appended to the live batch.
                rules.apply(song, List.copyOf(edit.batch), edit.report);
            }
        } finally {
            closeBracket();
        }
    }

    private void closeBracket() {
        var edit = requireOpenEdit("closeBracket");
        modificationDepth--;

        if (modificationDepth == 0) {
            openEdit = null;
            deliver(edit);
        }
    }

    /**
     * The one place an edit is delivered. A non-empty report never comes with an empty batch,
     * because the report travels with the batch. Runs even when the body or a rule throws, so no
     * batch or report outlives its bracket.
     */
    private void deliver(OpenEdit edit) {
        if (edit.batch.isEmpty()) {
            return;
        }

        song.setModified(true);
        // Wrap-and-transfer ownership: the notification constructor stores the list
        // directly, and the edit holding the only other reference has been dropped, so
        // wrapping once here spares the notification a defensive copy.
        var mutations = Collections.unmodifiableList(edit.batch);
        MessageCenter.post(new SongDidChangeNotification(mutations, song, edit.report.build(), edit.opName));
    }

    private boolean owesRules() {
        return !isReplaying();
    }

    private OpenEdit requireOpenEdit(String operation) {
        var edit = openEdit;

        if (edit == null) {
            throw new IllegalStateException(operation + " called outside a modification bracket");
        }

        return edit;
    }

    @Override
    void withModification(Runnable body) {
        inBracket(null, asSupplier(body));
    }

    @Override
    void withModification(String label, Runnable body) {
        inBracket(label, asSupplier(body));
    }

    @Override
    <T> T withModificationResult(Supplier<T> body) {
        return inBracket(null, body);
    }

    @Override
    <T> T withModificationResult(String label, Supplier<T> body) {
        return inBracket(label, body);
    }

    /**
     * A body that throws closes the bracket, and no rule can replace its exception with one of
     * its own.
     */
    private <T extends @Nullable Object> T inBracket(@Nullable String explicitLabel, Supplier<T> body) {
        beginModification(explicitLabel);
        T result;

        try {
            result = body.get();
        } catch (Throwable thrown) {
            closeBracket();
            throw thrown;
        }

        endModification();
        return result;
    }

    private static Supplier<@Nullable Void> asSupplier(Runnable body) {
        return () -> {
            body.run();
            return null;
        };
    }

    /**
     * The mutations that subscribers make in response coalesce into one
     * {@link SongDidChangeNotification}.
     */
    @Override
    void postWithModification(Message message) {
        withModification(() -> MessageCenter.post(message));
    }

    @Override
    void applyChange(Mutation mutation, Runnable mutator) {
        // Suspended tracking: the state change applies but nothing is recorded, so no
        // SongDidChangeNotification fires. Suspension also lifts the bracket requirement below.
        if (isMutationTrackingSuspended()) {
            mutator.run();
            return;
        }

        var edit = requireOpenEdit("applyChange");
        mutator.run();
        edit.batch.add(mutation);
    }

    @Override
    void report(RecordedChange change) {
        if (isMutationTrackingSuspended()) {
            return;
        }

        requireOpenEdit("report").report.record(change);
    }
}
