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

package songscribe.ui.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.swing.JComponent;
import javax.swing.JOptionPane;

import net.engio.mbassy.listener.Handler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Attachment;
import songscribe.dom.AttachmentRemoval;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Hairpin;
import songscribe.dom.Key;
import songscribe.dom.KeyChangeStranding;
import songscribe.dom.Line;
import songscribe.dom.ProjectedEdit;
import songscribe.dom.RecordedChange;
import songscribe.dom.SongStart;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.Tie;
import songscribe.error.RuntimeError;
import songscribe.hit.HitTarget;
import songscribe.layout.AccidentalFitGate;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.MessageSubscription;
import songscribe.message.command.AddHairpinCommand;
import songscribe.message.command.AutoStemDirectionCommand;
import songscribe.message.command.DeselectCommand;
import songscribe.message.command.FirstSecondEndingCommand;
import songscribe.message.command.FlipStemDirectionCommand;
import songscribe.message.command.InsertLineCommand;
import songscribe.message.command.PasteboardOpCommand;
import songscribe.message.command.SelectAllElementsCommand;
import songscribe.message.command.ToggleBeamCommand;
import songscribe.message.command.ToggleBeamWithPreviousCommand;
import songscribe.message.command.ToggleFallCommand;
import songscribe.message.command.ToggleFallOnLastInsertionCommand;
import songscribe.message.command.ToggleGlissandoCommand;
import songscribe.message.command.ToggleGlissandoWithPreviousCommand;
import songscribe.message.command.ToggleTieCommand;
import songscribe.message.command.ToggleTieWithPreviousCommand;
import songscribe.message.command.ToggleTupletCommand;
import songscribe.message.command.UpdatePreviewElementCommand;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.FontChange;
import songscribe.message.mutation.LayoutChange;
import songscribe.message.mutation.LineDeletion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.LineScopedMutation;
import songscribe.message.mutation.MetadataChange;
import songscribe.message.mutation.MetadataField;
import songscribe.message.mutation.Mutation;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.ElementTypeWasSelectedNotification;
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.PlaybackStateDidChangeNotification;
import songscribe.message.notification.PrefsDidChangeNotification;
import songscribe.message.notification.PreviewElementDidChangeNotification;
import songscribe.message.notification.RestModeDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.TextEditingDidChangeNotification;
import songscribe.prefs.PrefsKey;
import songscribe.ui.EditResult;
import songscribe.ui.Mode;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.playback.PlaybackState;
import songscribe.ui.selection.RangeQueries.HairpinResolution;
import songscribe.ui.FitRefusals;
import songscribe.ui.OptionDialogs;
import songscribe.ui.SlideOperations;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.clipboard.Fragment;
import songscribe.ui.clipboard.FragmentPaste;
import songscribe.ui.component.score.MainPanel;
import songscribe.ui.component.score.PreviewElementManager;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.InsertionPointMode;
import songscribe.ui.edit.ScoreActions;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.ui.selection.TupletToggleInfo;
import songscribe.undo.OpNames;
import songscribe.undo.UndoController;
import songscribe.util.Debounce;
import songscribe.util.UIUtils;

/**
 * Coordinates message handling for the ScoreView component.
 */
public final class ScoreViewController {

    private static final Logger LOG = LoggerFactory.getLogger(ScoreViewController.class);

    private static final int REPAINT_DEBOUNCE_DELAY_MS = 300;

    // Runs before all HIGH_PRIORITY subscribers so the tuplet info cache is warm
    // by the time TupletAction handlers (HIGH_PRIORITY) read it.
    static final int TUPLET_INFO_CACHE_PRIORITY = Message.HIGH_PRIORITY + 100;

    private final ScoreView score;
    private final ScoreActions scoreActions;
    private final MusicEditOperations operations;
    private final SelectionCoordinator selectionCoordinator;
    private final ClipboardManager clipboardManager;

    // Cached per-notification-dispatch result of canToggleTuplet(), populated by
    // a TUPLET_INFO_CACHE_PRIORITY handler before any TupletAction handler reads it.
    @Nullable
    private TupletToggleInfo cachedTupletToggleInfo = null;

    final Debounce repaintDebounce;

    public ScoreViewController(
        ScoreView score,
        MusicEditOperations operations,
        SelectionCoordinator selectionCoordinator,
        ClipboardManager clipboardManager
    ) {
        this.score = score;
        scoreActions = score;
        this.operations = operations;
        this.selectionCoordinator = selectionCoordinator;
        this.clipboardManager = clipboardManager;

        repaintDebounce = Debounce.rescheduling(REPAINT_DEBOUNCE_DELAY_MS, score::repaint);

        MessageSubscription.addProcessListener(this);
    }

    private void warmTupletCache() {
        cachedTupletToggleInfo = operations.canToggleTuplet();
    }

    @Handler
    public void elementTypeWasSelected(ElementTypeWasSelectedNotification message) {
        score.setPreviewElement(EditModeManager.makePreviewElement(message.getNoteType()));
    }

    @Handler
    public void restModeDidChange(RestModeDidChangeNotification message) {
        score.setPreviewElement(EditModeManager.makePreviewElement());
    }

    @Handler
    public void handleUpdatePreviewElement(UpdatePreviewElementCommand message) {
        switch (message.getScope()) {
            case DECORATIONS -> updatePreviewElement();
            case ELEMENT -> syncPreviewElementWithSelectedDuration();
        }
    }

    /**
     * {@link EditModeManager#makePreviewElement()} must fall back to a default type when neither
     * action group has a selection: a delete can leave both deselected, and edit mode would
     * have no preview element and no way to recreate one.
     */
    private void syncPreviewElementWithSelectedDuration() {
        score.setPreviewElement(EditModeManager.makePreviewElement());
    }

    private void updatePreviewElement() {
        var previewElement = EditModeManager.getPreviewElement();

        if (previewElement != null) {
            EditModeManager.decorateElement(previewElement);
            MessageCenter.post(new PreviewElementDidChangeNotification(previewElement));

            // Decorations (accidental, dots, articulations) change the ink the preview overlay
            // has cached, so it has to re-record rather than merely repaint.
            PreviewElementManager.previewElementDidChange();
            score.repaint();
        } else {
            score.setPreviewElement(EditModeManager.makePreviewElement());
        }
    }

    /**
     * For a caller already inside a precondition that guarantees a line is selected.
     */
    private int requireSelectedLine() {
        var selectedLine = selectionCoordinator.getSelectedLine();

        if (selectedLine == null) {
            throw RuntimeError.exit("no line selected");
        }

        return selectedLine;
    }

    @Handler
    public void handleInsertLine(InsertLineCommand message) {
        var song = score.getSong();
        var line = new Line(song);

        // The two relative variants are disabled without a line selection,
        // so requireSelectedLine() is only called where it is known to be valid.
        switch (message.getType()) {
            case ADD_AT_END -> song.addLine(line);
            case INSERT_BEFORE -> song.addLine(requireSelectedLine(), line);
            case INSERT_AFTER -> song.addLine(requireSelectedLine() + 1, line);
        }

        score.deselect();
    }

    @Handler
    public void handleToggleBeam(ToggleBeamCommand message) {
        if (selectionCoordinator.getRange() == null) {
            return;
        }

        operations.toggleBeaming();
    }

    /**
     * {@code MODIFIED} re-points the key at the element just acted on, so a second press toggles
     * the same pair back off; {@code REFUSED} beeps; {@code REPORTED} is silent because an error
     * dialog is already on screen.
     *
     * <p>{@code targetAction} supplies the undo label, because these key bindings bypass
     * {@code UIAction} and would otherwise label the edit differently from the menu action that
     * also performs it.
     */
    private void handleLastInsertionCommand(
        UIAction targetAction,
        Function<EditModeManager.Insertion, EditResult> operation
    ) {
        // Stands in for the DISABLE_WHEN_PLAYING flag the toggle-beam action carries;
        // this command arrives from a key binding that no action's enabled state gates.
        if (PlaybackController.isPlaying()) {
            UIUtils.beep();
            return;
        }

        var insertion = EditModeManager.getLastInsertion();

        if (insertion == null) {
            UIUtils.beep();
            return;
        }

        var result = UndoController.withPendingOpNameResult(
            targetAction.getUndoOpName(), () -> operation.apply(insertion));

        switch (result) {
            case MODIFIED -> EditModeManager.setLastInsertion(insertion.line(), insertion.elementIndex());
            case REFUSED -> UIUtils.beep();
            case REPORTED -> { }
        }
    }

    @Handler
    public void handleToggleBeamWithPrevious(ToggleBeamWithPreviousCommand message) {
        handleLastInsertionCommand(
            Actions.TOGGLE_BEAM_ACTION,
            insertion -> EditResult.of(
                MusicEditOperations.toggleBeamWithPredecessor(insertion.line(), insertion.elementIndex())));
    }

    @Handler
    public void handleToggleTieWithPrevious(ToggleTieWithPreviousCommand message) {
        handleLastInsertionCommand(
            Actions.TOGGLE_TIE_ACTION,
            insertion -> EditResult.of(
                MusicEditOperations.toggleTieWithPredecessor(insertion.line(), insertion.elementIndex())));
    }

    @Handler
    public void handleToggleGlissando(ToggleGlissandoCommand message) {
        if (selectionCoordinator.getRange() == null) {
            return;
        }

        operations.toggleGlissando(score.getLyricRenderMetrics());
    }

    @Handler
    public void handleToggleFall(ToggleFallCommand message) {
        if (selectionCoordinator.getRange() == null) {
            return;
        }

        operations.toggleFall(score.getLyricRenderMetrics());
    }

    @Handler
    public void handleToggleGlissandoWithPrevious(ToggleGlissandoWithPreviousCommand message) {
        handleLastInsertionCommand(
            Actions.GLISSANDO_ACTION,
            insertion -> SlideOperations.toggleGlissandoWithPredecessor(
                insertion.line(), insertion.elementIndex(), score.getLyricRenderMetrics()));
    }

    @Handler
    public void handleToggleFallOnLastInsertion(ToggleFallOnLastInsertionCommand message) {
        handleLastInsertionCommand(
            Actions.FALL_ACTION,
            insertion -> SlideOperations.toggleFallOnLastInsertion(
                insertion.line(), insertion.elementIndex(), score.getLyricRenderMetrics()));
    }

    @Handler
    public void handleToggleTie(ToggleTieCommand message) {
        operations.toggleTie();
    }

    @Handler
    public void handleToggleTuplet(ToggleTupletCommand message) {
        operations.toggleTuplet(message.getGrade());
        score.selectionChanged();
    }

    @Handler
    public void handleAddHairpin(AddHairpinCommand message) {
        operations.addHairpinToSelection(message.kind());
    }

    @Handler
    public void handleFirstSecondEnding(FirstSecondEndingCommand message) {
        if (operations.makeFirstSecondEnding()) {
            MessageCenter.post(new DeselectCommand());
        }
    }

    @Handler
    public void handleFlipStemDirection(FlipStemDirectionCommand message) {
        operations.flipStemDirection();
    }

    @Handler
    public void handleAutoStemDirection(AutoStemDirectionCommand message) {
        operations.autoStemDirection();
    }

    public boolean canToggleBeaming() {
        return operations.canToggleBeaming();
    }

    public boolean canToggleTie() {
        return operations.canToggleTie();
    }

    public boolean canToggleGlissando() {
        return operations.canToggleGlissando();
    }

    public boolean canToggleFall() {
        return operations.canToggleFall();
    }

    public TupletToggleInfo canToggleTuplet() {
        var cached = cachedTupletToggleInfo;

        if (cached != null) {
            return cached;
        }

        return operations.canToggleTuplet();
    }

    public HairpinResolution resolveHairpinAction(Hairpin.Kind kind) {
        return operations.resolveHairpinAction(kind);
    }

    public boolean canMakeFirstSecondEnding() {
        return operations.canMakeFirstSecondEnding();
    }

    public boolean canChangeTempo() {
        return operations.canChangeTempo();
    }

    public boolean canModifyStemDirection() {
        return operations.canModifyStemDirection();
    }

    @Handler(priority = TUPLET_INFO_CACHE_PRIORITY)
    public void musicSelectionDidChangeCacheTupletInfo(MusicSelectionDidChangeNotification message) {
        warmTupletCache();
    }

    @Handler(priority = TUPLET_INFO_CACHE_PRIORITY)
    public void documentDidLoadCacheTupletInfo(DocumentDidLoadNotification message) {
        warmTupletCache();
    }

    @Handler
    public void prefsDidChange(PrefsDidChangeNotification message) {
        // PrefsKey.ALL fires on resetAll() and is the only signal that any
        // specific key may have changed — handle it for every affected effect.
        var key = message.getKey();
        var all = key == PrefsKey.ALL;

        if (all || ScoreActions.PLAYBACK_SYNC_PREFS_KEYS.contains(key)) {
            scoreActions.syncPlaybackPrefs();
        }

        if ((all || key == PrefsKey.PAGE_SIZE) && score.isInitialized()) {
            scoreActions.updatePageLayout(score.getSong().getLineWidthSs());
        }
    }

    @Handler
    public void textEditingDidChange(TextEditingDidChangeNotification message) {
        scoreActions.setKeyBindingsEnabled(!message.isEditing());
    }

    @Handler(priority = TUPLET_INFO_CACHE_PRIORITY)
    public void songDidChange(SongDidChangeNotification message) {
        // The selected range is spliced through this batch of mutations here, before any
        // other reader of the range sees it — in particular ahead of warmTupletCache, the
        // first such reader below. That ordering is expressed as program order within this
        // one method rather than as a second priority constant that could drift out of step
        // with TUPLET_INFO_CACHE_PRIORITY.
        selectionCoordinator.revalidateElementSelection(message);

        warmTupletCache();

        var mainPanel = score.getMainPanel();

        if (mainPanel == null) {
            return;
        }

        // Three mutually exclusive cases for the staff, checked widest first: a line insert or
        // delete rebuilds the LinePanel list; a change that reaches every line invalidates every
        // LinePanel's layout; and a line-scoped change invalidates only the LinePanels the change
        // affects (one, or two for a tie crossing a line break).
        //
        // The rebuild is tested ahead of the every-line case because it subsumes it — it
        // recreates every LinePanel, layout and all — while the reverse is not true, and a batch
        // that changes fonts and inserts a line carries both.
        if (message.hasMutationOf(LineInsertion.class) || message.hasMutationOf(LineDeletion.class)) {
            // StaffPanel.rebuildLayout() is the only add/remove primitive for LinePanels;
            // per-panel invalidation cannot add a panel for an inserted line or remove one
            // for a deleted line. Coarse (recreates every LinePanel) but correct for both
            // forward edits and undo/redo replay, which funnel through this same handler.
            mainPanel.getStaffPanel().rebuildLayout();
            // rebuildLayout() creates fresh LineComponents with only song and line set;
            // re-wire the scoreView into each (as document load does) so their next
            // performLayout() has a live scoreView and does not produce a null layout.
            score.setupLineComponentState();
        } else if (invalidatesEveryLine(message)) {
            for (var linePanel : mainPanel.getStaffPanel().getLinePanels()) {
                linePanel.getLineComponent().invalidateLayout();
            }
        } else if (hasLineLayoutMutation(message)) {
            var staffPanel = mainPanel.getStaffPanel();
            var targetLine = message.getLine();
            var tieReach = linesReached(message, Tie::reach);
            var keyMoveReach = keyMoveReach(message);

            for (var linePanel : staffPanel.getLinePanels()) {
                var line = linePanel.getLine();

                if (targetLine == null
                    || line == targetLine
                    || tieReach.contains(line)
                    || keyMoveReach.contains(line)) {
                    linePanel.getLineComponent().invalidateLayout();

                    // With neither a tie across a line break nor a key move in the notification
                    // the named line is the only one that can match, so the panels after it have
                    // nothing left to be checked against.
                    if (targetLine != null && tieReach.isEmpty() && keyMoveReach.isEmpty()) {
                        break;
                    }
                }
            }
        }

        // Additive, not a fourth case in the chain: a Song Settings commit carries metadata
        // alongside fonts and tempo and can arrive in the same batch as a line-scoped mutation
        // elsewhere in the song, where the chain above would invalidate that line and leave
        // line 0 — which lays out the attribution and the tempo mark — holding its stale layout.
        invalidateMetadataViews(message, mainPanel);

        // Nothing re-fits the page here. Every branch above ends in a revalidate on the component
        // whose size changed, and ScoreView.getPreferredSize derives the page height from its
        // content, so the page follows on its own. A test of which mutations "move the page"
        // would have to name every mutation that can change a line's height — which is all of
        // them, as a key change that lifts an ending above a new accidental showed.

        repaintDebounce.trigger();
    }

    private static Set<Line> linesReached(
        SongDidChangeNotification message, Function<Mutation, List<Line>> reachedBy) {
        Set<Line> reach = null;

        for (var mutation : message.getMutations()) {
            var lines = reachedBy.apply(mutation);

            if (!lines.isEmpty()) {
                if (reach == null) {
                    reach = Collections.newSetFromMap(new IdentityHashMap<>());
                }

                reach.addAll(lines);
            }
        }

        return reach == null ? Set.of() : reach;
    }

    /**
     * A key move's layout effect reaches forward through every line inheriting the moved key and
     * back one line, whose trailing space is solved from the cautionary it leads into; without
     * those, such lines keep stale spacing or the old key.
     */
    private static Set<Line> keyMoveReach(SongDidChangeNotification message) {
        return linesReached(message, message.getSong().keyInheritance()::keyMoveReach);
    }

    /**
     * Song-wide {@code LyricsChange} mutations target legacy text fields and do not affect
     * rendered layout.
     */
    private static boolean hasLineLayoutMutation(SongDidChangeNotification message) {
        for (var mutation : message.getMutations()) {
            if (mutation instanceof LineScopedMutation
                || mutation instanceof LineInsertion
                || mutation instanceof LineDeletion) {
                return true;
            }
        }

        return false;
    }

    /**
     * A metadata change reaches every line only when it redefined the song's beat; the rest is
     * drawn outside the staves or on line 0 alone (see {@link #invalidateMetadataViews}).
     */
    private static boolean invalidatesEveryLine(SongDidChangeNotification message) {
        for (var mutation : message.getMutations()) {
            if (mutation instanceof FontChange || mutation instanceof LayoutChange) {
                return true;
            }

            if (mutation instanceof MetadataChange metadataChange && redefinesBeat(metadataChange)) {
                return true;
            }
        }

        return false;
    }

    /**
     * {@link MetadataField#TEMPO} covers both the beat and its display, so the recorded values
     * decide. The casts hold because {@link MetadataChange}'s constructor validates every value
     * against {@link MetadataField#getExpectedType()}.
     */
    private static boolean redefinesBeat(MetadataChange change) {
        return change.field() == MetadataField.TEMPO
            && !Tempo.haveSameBeat((Tempo) change.oldValue(), (Tempo) change.newValue());
    }

    /**
     * Runs whatever the staff branch decided, since a batch can carry a metadata change beside a
     * line-scoped one.
     */
    private static void invalidateMetadataViews(SongDidChangeNotification message, MainPanel mainPanel) {
        var firstLineAffected = false;

        for (var mutation : message.getMutations()) {
            if (!(mutation instanceof MetadataChange metadataChange)) {
                continue;
            }

            switch (metadataChange.field()) {
                case ATTRIBUTION -> {
                    firstLineAffected = true;
                    invalidate(mainPanel.getTitleComponent());
                    invalidate(mainPanel.getSubtitleComponent());
                }

                // Reached only for a tempo edit that left the beat alone; one that redefined it
                // was answered by invalidatesEveryLine. Either way the marking is line 0's, so
                // invalidating that line is all the marking itself needs.
                case TEMPO -> firstLineAffected = true;

                case FOOTNOTES -> invalidate(mainPanel.getFootnotesComponent());
            }
        }

        if (firstLineAffected) {
            var linePanels = mainPanel.getStaffPanel().getLinePanels();

            // Empty while a document is being torn down or before its lines exist.
            if (!linePanels.isEmpty()) {
                linePanels.getFirst().getLineComponent().invalidateLayout();
            }
        }
    }

    private static void invalidate(JComponent component) {
        component.invalidate();
        component.repaint();
    }

    @Handler(priority = Message.HIGH_PRIORITY)
    public void modeDidChange(ModeDidChangeNotification message) {
        var mode = message.getMode();
        score.setMode(mode);

        // Entering edit mode drops any selection and syncs the preview element with the
        // currently selected duration button. Reflection may have changed the selected
        // button while in select mode without posting a DurationSelectedMessage.
        if (mode == Mode.EDIT) {
            score.clearSelection();
            syncPreviewElementWithSelectedDuration();
        }

        score.repaint();
    }

    @Handler
    public void playbackStateDidChange(
        PlaybackStateDidChangeNotification message
    ) {
        var state = message.getState();

        if (state == PlaybackState.PLAYING) {
            return;
        }

        // A rewind goes back to the top of the song, so the selection that would otherwise
        // anchor the next Play is dropped. The score owns its selection, so it drops it
        // here rather than being reached into from the playback controller. Guarded because
        // clearSelection() posts a MusicSelectionDidChangeNotification even when there was
        // nothing selected.
        if (state == PlaybackState.REWOUND && score.getSelection() != null) {
            score.clearSelection();
        }

        score.repaint();
    }

    @Handler
    public void musicSelectionDidChangeCancelRewind(MusicSelectionDidChangeNotification message) {
        // Selecting music after a rewind means the next Play should start at that selection
        // rather than at the top of the song. Ignore the cleared case, which is the rewind
        // itself reaching this handler via clearSelection() above.
        if (score.getSelection() != null) {
            PlaybackController.selectionDidChange();
        }
    }

    @Handler
    public void handlePasteboardOp(PasteboardOpCommand message) {
        // Belt-and-braces: while a placement is pending all pasteboard operations are
        // ignored. The action layer is already disabled via enableFromInsertionPointMode;
        // this covers any non-action dispatch path.
        if (InsertionPointMode.isActive()) {
            return;
        }

        if (!score.isFocusOwner()) {
            return;
        }

        switch (message.getOperation()) {
            case CUT -> handleCut();
            case COPY -> handleCopy();
            case DELETE -> handleDelete();
            case PASTE -> handlePaste();
        }
    }

    private void handleCut() {
        var range = selectionCoordinator.getRange();

        if (range == null) {
            return;
        }

        var line = range.line();

        // Widened once here and passed to the stranding and the deletion, so the range the key
        // changes are computed against is the range that goes.
        var bounds = line.effectiveRange(range.range());

        var stranding = deletionStranding(line, bounds);

        handleCopy();

        // Clear the selection before removing elements so that action handlers
        // reacting to SongDidChangeNotification (posted synchronously when the
        // modification bracket closes) don't query selection indices that no
        // longer exist on the shrunk line.
        selectionCoordinator.clearSelection();

        // One bracket for the deletion. The Cut action's op-name (Tier A) names this outermost
        // step.
        score.getSong().withModification(() -> deleteElementRange(line, bounds, stranding));

        // Discard saved action states — the song has changed, so restoring
        // pre-selection states would be stale. Individual action handlers will
        // re-evaluate their enabled state from the current context.
        selectionCoordinator.getActionReflector().clearSavedActionStates();
        score.deselect();
    }

    void handleCopy() {
        var range = selectionCoordinator.getRange();

        if (range != null) {
            clipboardManager.setFragment(
                Fragment.capture(range.line(), range.range())
            );
            score.deselect();
        }
    }

    /**
     * The two branches that move a key — the element range and the line — compute what they
     * strand before opening a bracket and sweep it inside, so the stranded key changes and the
     * deletion are one undo step.
     */
    void handleDelete() {
        var song = score.getSong();

        if (selectionCoordinator.getSelectedTarget() instanceof HitTarget.Lyric(var element, var script)) {
            // The selection layer clears a selection that is no longer live, so a selected
            // lyric's element is in a line.
            var line = element.requireParentLine();
            var index = line.getElementIndex(element);

            song.withModification(Strings.get(Strings.ACTION_EDIT_OP_DELETE_LYRIC), () -> {
                line.modifyElement(index, ElementField.LYRIC, () ->
                    line.getElement(index).removeLyricForScript(script));
                line.adjustNeighborsForLyricDeletion(index, script);
            });

            selectionCoordinator.getActionReflector().restoreSelectedActionStates();
            selectionCoordinator.clearSelection();
            score.selectionChanged();
            score.repaint();
            return;
        }

        var range = selectionCoordinator.getRange();

        // hasDecorationSelection() rather than a null check on the target: a whole-line
        // selection is also a target now, and it is deleted by the canDeleteLine branch
        // below rather than by deleteSelectedTarget.
        var selectedTarget = selectionCoordinator.hasDecorationSelection()
            ? selectionCoordinator.getSelectedTarget()
            : null;
        var targetLine = selectionCoordinator.getActiveLine();

        if (range != null) {
            var line = range.line();
            var selected = range.range();

            // Widened once here and passed to the stranding and the deletion, so the range the
            // key changes are computed against is the range that goes.
            var bounds = line.effectiveRange(selected);

            var stranding = deletionStranding(line, bounds);

            // Name the undo step from the categories of the user-selected elements
            // (computed before removal, while they are still present on the line).
            var selectedTypes = line.getElements(selected).stream()
                .map(StaffElement::getType)
                .toList();
            var deleteLabel = OpNames.deleteLabel(selectedTypes);

            // Clear the selection before removing elements so that action handlers
            // reacting to SongDidChangeNotification (posted synchronously when the
            // modification bracket closes) don't query selection indices that no
            // longer exist on the shrunk line.
            selectionCoordinator.clearSelection();

            // deleteElementRange's own bracket nests inside the one opened here, which names
            // the step: the op name is captured only at the outermost bracket.
            song.withModification(deleteLabel,
                () -> deleteElementRange(line, bounds, stranding));
        } else if (selectedTarget != null && targetLine != null) {
            deleteSelectedTarget(targetLine, selectedTarget);
        } else if (score.canDeleteLine()) {
            var lineIndex = requireSelectedLine();
            var deletedLine = song.getLine(lineIndex);

            // Computed against the lines as they stand, before the bracket opens. The stranding
            // never names the deleted line itself: the line deletion takes it away whole.
            var stranding = KeyChangeStranding.ofLineDeletion(deletedLine);

            song.withModification(OpNames.deleteLineLabel(), () -> {
                stranding.sweep();
                song.removeLine(lineIndex);
            });
        }

        // Restore the pre-selection selected states but not the enabled states — the song
        // has changed, so individual action handlers must re-evaluate enablement from the
        // current context, while the user's chosen duration button survives the delete.
        selectionCoordinator.getActionReflector().restoreSelectedActionStates();
        score.deselect();
    }

    /**
     * Each variant is deleted in its own modification bracket so the undo step is named after
     * what was deleted, through the tracked removal API that owns it, so Delete and the
     * object's toolbar toggle cannot disagree. The two remaining selectable kinds are
     * deliberately a no-op rather than falling through to the whole-line delete.
     */
    private void deleteSelectedTarget(Line line, HitTarget.Selectable target) {
        switch (target) {
            case HitTarget.Slide(var slideElement) -> {
                var elementIndex = line.getElementIndex(slideElement);
                var slide = slideElement.getSlide();

                // A slide selection is only ever made on an element that carries a slide;
                // guard anyway so the @Nullable getSlide() result isn't passed on unchecked.
                if (slide != null) {
                    // Capture before the removal: stripping the glissando un-pairs the grace
                    // note, so by the time the sync runs there is no pairing left to read.
                    var wasPairedGraceNote = line.isPairedGraceNote(elementIndex);

                    line.getSong().withModification(OpNames.deleteSlideLabel(slide), () -> {
                        line.modifyElement(elementIndex, ElementField.SLIDE, slideElement::removeSlide);

                        // Un-pairing dissolves the automatic melisma. Both elements survive, so
                        // the syllable simply stays on the now-ordinary former grace note.
                        if (wasPairedGraceNote) {
                            line.syncGraceHostMelisma(elementIndex);
                        }
                    });
                }
            }

            case HitTarget.Ending(var ending) ->
                line.getSong().withModification(OpNames.deleteEndingLabel(), () -> ending.removeFrom());

            case HitTarget.Hairpin(var hairpin) ->
                line.getSong().withModification(OpNames.deleteHairpinLabel(hairpin), () -> hairpin.removeFrom());

            // A cross-line tie's anchor sits on only the first of its two lines (see
            // HitTarget.Tie), which is not necessarily the line the hit was made against, so
            // the removal is recorded on the anchor's own line.
            case HitTarget.Tie(var tie) -> {
                var anchor = tie.anchor();
                var anchorLine = anchor.requireParentLine();

                anchorLine.getSong().withModification(
                    OpNames.removeTieLabel(), () -> anchorLine.removeTie(anchor));
            }

            case HitTarget.Beam(var beam) ->
                line.getSong().withModification(OpNames.removeBeamLabel(), () -> beam.removeFrom());

            case HitTarget.Tuplet(var tuplet) ->
                line.getSong().withModification(OpNames.removeTupletLabel(), () -> tuplet.removeFrom());

            // The explicit label is not optional: the generic SpanRemoval's unlabeled fallback
            // op-name is the literal text "Ending", so an unlabeled trill removal would make
            // the undo menu read "Undo Ending".
            case HitTarget.Trill(var trill) ->
                line.getSong().withModification(OpNames.removeTrillLabel(), () -> trill.removeFrom());

            case HitTarget.Articulation(var articulation) -> {
                var owner = articulation.owner();
                var elementIndex = resolveOwnerIndex(line, owner);

                if (owner != null && elementIndex != null) {
                    line.getSong().withModification(OpNames.removeArticulationLabel(articulation.getType()), () ->
                        line.modifyElement(elementIndex, ElementField.ARTICULATION,
                            () -> owner.removeArticulation(articulation)));
                }
            }

            case HitTarget.Attachment(var attachment) -> deleteAttachment(line, attachment);

            // Nothing to delete. Listed one per kind rather than folded into a `default` arm so
            // that adding a selectable kind fails to compile here and forces the question of
            // what Delete should do with it. A `default` would answer "nothing" silently, which
            // is the failure mode SelectionCoordinator.isSelected avoids for the same reason.
            //
            // The staff line and a lyric are both selectable, and handleDelete owns each: it
            // deletes the whole line for the one and the syllable for the other before reaching
            // here. Every kind that is not selectable at all — a note head, which is selected as
            // an index range, a grace-note glissando and the attribution — is excluded by the
            // parameter type instead of by an arm.
            case HitTarget.StaffLine _, HitTarget.Lyric _ -> {
            }
        }
    }

    /**
     * Null when there is no owner or it is no longer on the line.
     */
    private static @Nullable Integer resolveOwnerIndex(Line line, @Nullable StaffElement owner) {
        if (owner == null) {
            LOG.debug("Cannot delete the selected target: it has no owner element");
            return null;
        }

        if (!line.contains(owner)) {
            LOG.debug("Cannot delete the selected target: its owner element ({}) is not on the active line",
                owner.getType());
            return null;
        }

        return line.getElementIndex(owner);
    }

    private record AttachmentRemovalPlan(ElementField field, Runnable mutator) {}

    private void deleteAttachment(Line line, Attachment attachment) {
        var element = attachment.owner();
        var elementIndex = resolveOwnerIndex(line, element);

        if (element == null || elementIndex == null) {
            return;
        }

        var plan = switch (attachment) {
            case FermataAttachment _ ->
                new AttachmentRemovalPlan(ElementField.FERMATA, () -> FermataAttachment.removeFermata(element));

            case DynamicAttachment dynamic ->
                new AttachmentRemovalPlan(ElementField.DYNAMIC_ATTACHMENT, () -> element.removeAttachment(dynamic));

            case AnnotationAttachment _ ->
                new AttachmentRemovalPlan(ElementField.ANNOTATION, () -> AttachmentRemoval.removeAnnotation(element));

            case BeatChangeAttachment _ ->
                new AttachmentRemovalPlan(ElementField.BEAT_CHANGE, () -> AttachmentRemoval.removeBeatChange(element));

            case TempoChangeAttachment _ ->
                new AttachmentRemovalPlan(ElementField.TEMPO_CHANGE, () -> AttachmentRemoval.removeTempoChange(element));
        };

        line.getSong().withModification(OpNames.removeAttachmentLabel(attachment), () ->
            line.modifyElement(elementIndex, plan.field(), plan.mutator()));
    }

    /**
     * Callers compute it <b>before</b> opening a modification bracket and hand it to
     * {@link #deleteElementRange}, which sweeps it inside.
     */
    private static KeyChangeStranding deletionStranding(Line line, StaffElementRun.EffectiveRange bounds) {
        var range = bounds.range();

        // A deletion that takes a key change with it leaves the key that was running before the
        // range running past it, which can leave a later key change on this same line restating
        // it.
        var keyAfterRemoval = line.keyBefore(range.begin());
        var strandedAfter = line.redundantKeyChangeRanges(range.end() + 1, keyAfterRemoval);

        return KeyChangeStranding.ofMidLineEdit(
            line, strandedAfter, line.keyAtEndOfLineAfterRemoving(bounds));
    }

    /**
     * Callers clear the selection first and open the outermost bracket, which names the undo
     * step; the bracket opened here only nests.
     * <p>
     * Not fit-gated: the range freed the head and accidental of the note after it, so the line
     * can never get wider.
     * <p>
     * {@code stranding} is computed by the caller before any bracket opens, against the
     * pre-removal line; its ranges name pre-removal indices, so it is swept before the range
     * goes.
     */
    private void deleteElementRange(
        Line line,
        StaffElementRun.EffectiveRange range,
        KeyChangeStranding stranding) {

        line.getSong().withModification(() -> {
            stranding.sweep();
            line.deleteRange(range);
        });
    }

    /**
     * Only {@link #INSERTED} means the line was mutated.
     */
    public enum FragmentInsertOutcome {
        /** The paste happened, or the gesture was spent on a fragment that reduced to nothing. */
        INSERTED,
        LINE_FULL,
        EMPTY,
        CANCELLED,
        /** The user may pick another point. */
        DECLINED_BY_ALERT
    }

    private static final int REMOVE_AND_PASTE_OPTION = 0;

    private static boolean confirmRemoveLeadingBreathMark() {
        var cancelLabel = Strings.get(Strings.BUTTON_CANCEL);
        var removeLabel = Strings.get(Strings.BUTTON_REMOVE_AND_PASTE);
        var result = OptionDialogs.showOptionDialog(
            null,
            Strings.CONFIRM_TITLE_PASTE,
            Strings.CONFIRM_PASTE_LEADING_BREATH_MARK,
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            new Object[]{removeLabel, cancelLabel},
            removeLabel
        );
        return result == REMOVE_AND_PASTE_OPTION;
    }

    /**
     * Must be called inside a modification bracket, so paste-replace's delete and insert are one
     * undo step. A refusal mutates nothing, leaves the caller's bracket empty and posts no
     * notification; callers decide recovery.
     *
     * <p>A breath mark stands only immediately after a duration element, and no bar line stands
     * first in the song ({@link SongStart}); the paste enforces both on the fragment's ends
     * before the fit gate runs. At the song-start slot, leading bar lines are dropped, and the
     * key of the last key change dropped with them becomes {@code line}'s own key, inside the
     * caller's bracket.
     *
     * <p>A fragment carrying a key change re-keys the lines inheriting past it, so the paste
     * removes every key change it leaves restating the key in effect. A line a moved key leaves
     * overflowing is <b>not</b> fit-gated.
     *
     * <p>The paste reports every span it removes or leaves out into the open bracket's report.
     *
     * <p>Every question to the user is asked before the first change, so declining leaves the
     * bracket empty and records no undo step.
     *
     * @return {@code INSERTED} also when the fragment reduced to nothing (every key change it
     *     carried restates the running key, or it held only bar lines dropped at the start of
     *     the song): the gesture is spent and the caller should complete it. Only
     *     {@code INSERTED} means the line was mutated
     */
    public FragmentInsertOutcome tryInsertFragment(Line line, StaffElementRun.InsertionTarget target) {
        var fragment = clipboardManager.getFragment();

        if (fragment == null || fragment.elements().isEmpty()) {
            return FragmentInsertOutcome.EMPTY;
        }

        var insertIndex = target.insertIndex();
        var deleteRange = target.deleteRange();

        // The elements the paste-replace removes, for the projections and the spacing
        // calculation, which read the raw range. The paired grace note before the caller's
        // selection is already inside it, so the clones are positioned against a predecessor
        // that survives.
        var rawDeleteRange = deleteRange == null ? null : deleteRange.range();

        var keyAtInsertion = line.keyBefore(insertIndex);

        // Fresh clones every paste — the stored fragment is never itself inserted. The clones
        // carry no line back-reference until the paste inserts them, so building them early
        // touches nothing — which is also why the fit gate can measure these clones rather than
        // the stored fragment's elements.
        //
        // A fragment carries the key it was copied under, so one of its own key changes can
        // arrive restating the key already running where it lands. It is dropped here rather
        // than deleted after the insertion, so the chain walk, the fit measurement and the
        // span reconciliation all see the run that actually lands.
        var reduction = fragment.instantiate().withoutRedundantKeyChanges(keyAtInsertion);

        // No bar line stands first in the song, so at the song-start slot the bar lines that
        // start the fragment are dropped — each with the key change it may carry, which cannot
        // outlive it — and the paste goes ahead with the rest, as one more thing the song changed
        // on its own. Dropped here rather than after the insertion, for the same reason the
        // redundant key changes are.
        //
        // A key change dropped with its bar line set the key the rest of the fragment sounds in,
        // so that key becomes the line's own key when the paste lands.
        var songChanges = new ArrayList<RecordedChange>();
        @Nullable Key movedKey = null;

        if (line.isSongStartAt(insertIndex) && leadsWithBar(reduction.fragment())) {
            var drop = reduction.withoutLeadingBars();
            reduction = drop.reduction();
            movedKey = drop.movedKey();
            songChanges.add(SongStart.removedEntry(line, movedKey));
        }

        var instantiated = reduction.fragment();

        // A fragment of nothing but a key change restating the key already running here, and the
        // barline behind it, or of nothing but bar lines dropped at the start of the song, leaves
        // nothing to place. What is left of the paste is the deletion a paste-replace owes, and
        // the key a dropped key change moves; a pure insertion moving no key has nothing left to
        // do at all. Either way the gesture is spent, not declined.
        if (instantiated.elements().isEmpty()) {
            landFragment(line, target, reduction, new SongChanges(songChanges, movedKey));
            return FragmentInsertOutcome.INSERTED;
        }

        // A breath mark stands only immediately after a duration element, so the fragment is
        // tested at both ends before anything is measured or mutated. A paste-replace deletes
        // from insertIndex, so the predecessor the leading test reads is the same before and
        // after the deletion; its trailing test can never fail, because the effective range
        // already takes a breath mark after the range into the deletion.
        var leadsWithBreathMark = instantiated.elements().getFirst().getType().isBreathMark();

        if (leadsWithBreathMark && !line.canLeadRunAt(insertIndex)) {
            if (instantiated.elements().size() == 1) {
                OptionDialogs.showErrorMessage(
                    null, Strings.ALERT_TITLE_PASTE, Strings.ALERT_PASTE_BREATH_MARK_NOT_HERE);
                return FragmentInsertOutcome.DECLINED_BY_ALERT;
            }

            if (!confirmRemoveLeadingBreathMark()) {
                return FragmentInsertOutcome.CANCELLED;
            }

            // The leading breath mark is dropped here rather than after the insertion, so the
            // chain walk, the fit measurement and the span reconciliation below all see the run
            // that actually lands. A breath mark anchors no span, so every fragment span survives.
            var elements = instantiated.elements();
            instantiated = new Fragment(elements.subList(1, elements.size()), instantiated.spans());
            reduction = new Fragment.Reduction(instantiated, reduction.strandedSpans());
        }

        var clones = instantiated.elements();

        if (!line.canTrailRunAt(insertIndex, clones.getLast().getType())) {
            OptionDialogs.showErrorMessage(
                null, Strings.ALERT_TITLE_PASTE, Strings.ALERT_PASTE_NOT_BEFORE_BREATH_MARK);
            return FragmentInsertOutcome.DECLINED_BY_ALERT;
        }

        // The line as the paste will leave it: the deleted range swapped for the clones, starting
        // in the key a dropped key change moves onto it. The key moves only once the paste lands,
        // so the walk and the fit measurement read it from the projection.
        var startKey = (movedKey != null) ? movedKey : line.getRunningKey();
        var edit = (rawDeleteRange == null)
            ? ProjectedEdit.inserting(line, insertIndex, clones, startKey)
            : ProjectedEdit.replacing(line, rawDeleteRange, clones, startKey);
        var restamps = edit.walk();

        // The LINE_FULL refusal leaves the line exactly as it was (C1), so it lives inside the
        // fit gate: it stamps the walk's accidentals, runs this check with them in place so the
        // projection measures the right widths, and then puts every live note back as it was,
        // leaving only the clones stamped.
        var lyricRenderMetrics = score.getLyricRenderMetrics();

        var verdict = AccidentalFitGate.judge(
            line, restamps, clones, lyricRenderMetrics,
            () -> InsertionSpacingCalculator.calculateFragmentInsertion(edit, lyricRenderMetrics)
                .fitsWithinLine(line.getSong().getLineWidthSs().value()));

        var committed = FitRefusals.accepts(
            verdict, null,
            () -> OptionDialogs.showErrorMessage(
                null, Strings.ALERT_TITLE_INSERT_ERROR, Strings.ERROR_LINE_FULL_PASTE));

        if (!committed) {
            return FragmentInsertOutcome.LINE_FULL;
        }

        landFragment(line, target, reduction, new SongChanges(songChanges, movedKey));

        return FragmentInsertOutcome.INSERTED;
    }

    private static boolean leadsWithBar(Fragment fragment) {
        var elements = fragment.elements();

        return !elements.isEmpty() && elements.getFirst().getType().isBar();
    }

    /**
     * @param movedKey {@code null} when the paste moves none
     */
    private record SongChanges(List<RecordedChange> entries, @Nullable Key movedKey) {
    }

    /**
     * The changes are made only once the paste is certain to happen, so a refused paste leaves
     * the bracket empty. The key is set before the fragment lands, so the stranded key changes
     * are measured against the key the line now starts in.
     */
    private static void landFragment(
        Line line, StaffElementRun.InsertionTarget target, Fragment.Reduction reduction,
        SongChanges songChanges
    ) {
        var song = line.getSong();
        songChanges.entries().forEach(song::report);

        var movedKey = songChanges.movedKey();

        if (movedKey != null) {
            line.setKey(movedKey);
        }

        FragmentPaste.paste(line, target, reduction);
    }

    private void handlePaste() {
        var fragment = clipboardManager.getFragment();

        if (fragment == null || fragment.elements().isEmpty()) {
            return;
        }

        var range = selectionCoordinator.getRange();

        if (range == null) {
            // No selection: enter paste mode to place the fragment by clicking an
            // insertion point. The score already has focus (handlePasteboardOp
            // requires it) and the fragment is already known non-empty above.
            EditModeManager.getPasteModeManager().enter();
            return;
        }

        var line = range.line();

        // The whole range the replace really removes, widened at both ends exactly as a plain
        // deletion is: the deletion this authorizes also takes the barline in front of a key
        // change at begin, which the raw begin would leave out of the spacing measurement.
        var effective = line.effectiveRange(range.range());

        // One bracket for the whole replace — delete + insert is a single undo
        // step. On LINE_FULL tryInsertFragment mutates nothing, so the bracket
        // closes empty, posts no notification, and the selection stays intact.
        var outcome = score.getSong().withModificationResult(() -> {
            var result = tryInsertFragment(line, new StaffElementRun.InsertionTarget.Replacing(effective));

            if (result == FragmentInsertOutcome.INSERTED) {
                // Clear the selection before the bracket closes so action handlers
                // reacting to SongDidChangeNotification don't query selection
                // indices that no longer exist on the reshaped line.
                selectionCoordinator.clearSelection();
            }

            return result;
        });

        if (outcome == FragmentInsertOutcome.INSERTED) {
            // Discard saved action states — the song has changed, so restoring
            // pre-selection states would be stale. Individual action handlers will
            // re-evaluate their enabled state from the current context.
            selectionCoordinator.getActionReflector().clearSavedActionStates();
            score.deselect();
        }
    }

    @Handler
    public void handleDeselect(DeselectCommand message) {
        if (score.isFocusOwner()) {
            score.deselect();
        }
    }

    @Handler
    public void handleSelectAllElements(SelectAllElementsCommand message) {
        if (selectionCoordinator.getActiveLine() != null) {
            selectionCoordinator.selectAll();
            score.selectionChanged();
            score.repaint();
        }
    }
}
