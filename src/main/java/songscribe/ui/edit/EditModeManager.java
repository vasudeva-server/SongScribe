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

package songscribe.ui.edit;

import net.engio.mbassy.listener.Handler;
import org.jspecify.annotations.Nullable;

import songscribe.dom.AccidentalState;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.Dots;
import songscribe.dom.Dottable;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.NoteElement;
import songscribe.dom.StaffElement;
import songscribe.error.RuntimeError;
import songscribe.message.MessageCenter;
import songscribe.message.MessageSubscription;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.message.notification.PreviewElementDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.action.Actions;
import songscribe.ui.component.ScoreView;
import songscribe.ui.playback.PlayThread;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Manages edit mode state for the ScoreView.
 */
public final class EditModeManager {

    /** The line is part of the identity because an index alone is meaningless across lines. */
    public record Insertion(Line line, int elementIndex) {}

    @Nullable
    private static EditModeManager INSTANCE = null;

    /** Must be called exactly once, during ScoreView construction. */
    public static void init(
        SelectionCoordinator selectionCoordinator,
        ScoreActions scoreActions,
        ScoreView scoreView
    ) {
        INSTANCE = new EditModeManager(selectionCoordinator, scoreActions, scoreView);
    }

    private static EditModeManager instance() {
        if (INSTANCE == null) {
            throw RuntimeError.exit("EditModeManager not initialized — call init() first");
        }

        return INSTANCE;
    }

    private final ScoreActions scoreActions;
    private final GraceModeManager graceModeManager;
    private final InsertionPointMode insertionPointMode;
    private final PasteModeManager pasteModeManager;

    // Whether the preview element is visible (based on mouse position in mouse mode)
    private boolean previewElementIsVisible = false;

    // The current preview element that will be inserted
    @Nullable
    private StaffElement previewElement = null;

    // Whether to play a note sound when inserting elements
    private boolean playInsertedNote = true;

    // The insertion armed by the placement code, still inside the modification bracket.
    // It is promoted to lastInsertion when the bracket closes and the song change arrives.
    @Nullable
    private Insertion pendingInsertion = null;

    // The most recent song change that was a placement, or null if the most recent
    // song change was anything else.
    @Nullable
    private Insertion lastInsertion = null;

    private EditModeManager(
        SelectionCoordinator selectionCoordinator,
        ScoreActions scoreActions,
        ScoreView scoreView
    ) {
        this.scoreActions = scoreActions;
        graceModeManager = new GraceModeManager(selectionCoordinator);
        insertionPointMode = new InsertionPointMode(scoreView);
        pasteModeManager = new PasteModeManager(scoreView, insertionPointMode);
        MessageSubscription.addProcessListener(this);
    }

    public static GraceModeManager getGraceModeManager() {
        return instance().graceModeManager;
    }

    public static InsertionPointMode getInsertionPointMode() {
        return instance().insertionPointMode;
    }

    public static PasteModeManager getPasteModeManager() {
        return instance().pasteModeManager;
    }

    // -------------------------------------------------------------------------
    // Preview element accessors
    // -------------------------------------------------------------------------

    public static boolean isPreviewElementVisible() {
        return instance().previewElementIsVisible;
    }

    public static void setPreviewElementVisible(boolean visible) {
        var manager = instance();

        if (manager.previewElementIsVisible == visible) {
            return;
        }

        manager.previewElementIsVisible = visible;
        MessageCenter.post(new PreviewElementDidChangeNotification(visible ? manager.previewElement : null));
    }

    /** Null if no preview element is set. */
    @Nullable
    public static StaffElement getPreviewElement() {
        return instance().previewElement;
    }

    /** @param previewElement null to clear it */
    public static void setPreviewElement(@Nullable StaffElement previewElement) {
        instance().previewElement = previewElement;
        MessageCenter.post(new PreviewElementDidChangeNotification(previewElement));
    }

    public static boolean hasPreviewElement() {
        return instance().previewElement != null;
    }

    // -------------------------------------------------------------------------
    // Insertion target
    // -------------------------------------------------------------------------

    /** Null if the most recent song change was not a placement. */
    @Nullable
    public static Insertion getLastInsertion() {
        return instance().lastInsertion;
    }

    /**
     * For a caller that has already watched an edit commit, so the notification that cleared
     * the slot has been delivered. Placement goes through {@link #armInsertion} instead, because
     * it has to name its target from inside the bracket.
     */
    public static void setLastInsertion(Line line, int elementIndex) {
        instance().lastInsertion = new Insertion(line, elementIndex);
    }

    /**
     * Only call this on a path that is certain to commit: the next song change of any kind
     * promotes an unclaimed pending insertion, which would then resurface as a stale target.
     */
    private static void armInsertion(Line line, int elementIndex) {
        instance().pendingInsertion = new Insertion(line, elementIndex);
    }

    void resetInsertions() {
        pendingInsertion = null;
        lastInsertion = null;
    }

    public static boolean isPlayInsertedNote() {
        return instance().playInsertedNote;
    }

    public static void setPlayInsertedNote(boolean playInsertedNote) {
        instance().playInsertedNote = playInsertedNote;
    }

    // -------------------------------------------------------------------------
    // Preview element creation and decoration
    // -------------------------------------------------------------------------

    public static StaffElement makePreviewElement() {
        var elementType = ElementType.CROTCHET;
        var durationAction = Actions.DURATION_ACTION_GROUP.getSelected();

        if (durationAction != null) {
            elementType = durationAction.getType();
        } else {
            var barAction = Actions.NON_DURATION_ACTION_GROUP.getSelected();

            if (barAction != null) {
                elementType = barAction.getType();
            }
        }

        return makePreviewElement(elementType);
    }

    public static StaffElement makePreviewElement(ElementType elementType) {
        // Make a new element or rest of the given type
        var type = elementType;

        if (Actions.REST_ACTION.isSelected() && elementType.isPitchedNote()) {
            type = elementType.toRest();
        }

        var element = type.newInstance();
        decorateElement(element);
        return element;
    }

    public static void decorateElement(StaffElement element) {
        // Only a note and a rest can be decorated; a barline, a repeat, a breath mark and a key
        // change carry no dots, accidentals or articulations.
        if (element instanceof Dottable dottable) {
            var dotAction = Actions.DOT_ACTION_GROUP.getSelected();
            dottable.setDots((dotAction != null) ? dotAction.dots() : Dots.NONE);
        }

        // A rest takes no other decoration
        if (!(element instanceof NoteElement note)) {
            return;
        }

        var accidentalAction = Actions.ACCIDENTAL_ACTION_GROUP.getSelected();
        if (accidentalAction != null) {
            note.choose(accidentalAction.getAccidental());
        } else {
            note.adopt();
        }

        note.clearArticulations();

        if (Actions.ACCENT_ACTION.isSelected()) {
            note.addArticulation(
                new Articulation(ArticulationType.ACCENT)
            );
        }

        if (Actions.STACCATO_ACTION.isSelected()) {
            note.addArticulation(
                new Articulation(ArticulationType.STACCATO)
            );
        }
    }

    // -------------------------------------------------------------------------
    // Element modification operations
    // -------------------------------------------------------------------------

    public static boolean elementWasModified(Line line, int elementIndex) {
        var inst = instance();

        if (inst.previewElement == null) {
            return false;
        }

        inst.scoreActions.clearSelection();

        if (inst.previewElement.getType() == ElementType.REPEAT_LEFT && (elementIndex - 1) >= 0) {
            var prevElement = line.getElement(elementIndex - 1);

            if (prevElement.getType() == ElementType.REPEAT_RIGHT) {
                var repeatLeftRight = ElementType.REPEAT_LEFT_RIGHT.newInstance();
                line.setElement(elementIndex - 1, repeatLeftRight);
                return true;
            }
        }

        if (inst.previewElement.getType() == ElementType.REPEAT_RIGHT && elementIndex < line.elementCount()) {
            var nextElement = line.getElement(elementIndex);

            if (nextElement.getType() == ElementType.REPEAT_LEFT) {
                var repeatLeftRight = ElementType.REPEAT_LEFT_RIGHT.newInstance();
                line.setElement(elementIndex, repeatLeftRight);
                return true;
            }
        }

        return false;
    }

    public static void previewElementDidChange(Line line, int elementIndex) {
        var inst = instance();

        if (inst.previewElement == null) {
            return;
        }

        // Arming is only safe when a commit will follow to consume it. Nothing is posted
        // while mutation tracking is suspended — grace-note placement inserts that way,
        // since a lone grace note is not undoable — so an arm made here would sit unclaimed
        // until some later, unrelated edit adopted it, long after the grace note it names
        // may have been cancelled away.
        if (!line.getSong().isMutationTrackingSuspended()) {
            armInsertion(line, elementIndex);
        }

        // Capture the inserted element before previewElement is updated for the next insertion.
        var insertedElement = line.getElement(elementIndex);
        var playedNote = inst.playInsertedNote && insertedElement instanceof NoteElement note ? note : null;

        var nextElement = inst.previewElement.getType().newInstance();

        // Add any other element decorations
        decorateElement(nextElement);
        inst.scoreActions.setPreviewElement(nextElement);
        inst.scoreActions.repaint();

        if (playedNote != null) {
            new PlayThread(playedNote.getPitch()).start();
        }
    }

    // -------------------------------------------------------------------------
    // Message handlers
    //
    // These must operate on `this` and never call instance(): a message can arrive
    // before init() has assigned INSTANCE, which would abort the application.
    // -------------------------------------------------------------------------

    @Handler
    public void songDidChange(SongDidChangeNotification message) {
        lastInsertion = pendingInsertion;
        pendingInsertion = null;
    }

    @Handler
    public void modeDidChange(ModeDidChangeNotification message) {
        resetInsertions();
    }

    @Handler
    public void documentDidLoad(DocumentDidLoadNotification message) {
        resetInsertions();
    }

}
