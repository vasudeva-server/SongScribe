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

package songscribe.ui.component.score;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Objects;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.Line;
import songscribe.dom.DocumentScale;
import songscribe.dom.Ss;
import songscribe.dom.StaffElement;
import songscribe.engraving.Staff;
import songscribe.error.RuntimeError;
import songscribe.hit.HitRegion;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutEngine;
import songscribe.layout.LayoutEngine.TempoMark;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricRenderMetrics;
import songscribe.lint.LintMark;
import songscribe.lint.LintReport;
import songscribe.ui.Mode;
import songscribe.ui.ViewScale;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.Actions;
import songscribe.ui.component.ComponentNames;
import songscribe.ui.component.LyricEditor;
import songscribe.ui.component.LyricTargetResolver;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.ui.dialog.AttachmentDialogController;
import songscribe.ui.dialog.KeyChangeDialogController;
import songscribe.ui.dialog.SongSettingsDialog;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.GraceModeManager;
import songscribe.ui.edit.InsertionPointMode;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.renderer.ElementFrame;
import songscribe.ui.renderer.LineInvariants;
import songscribe.util.GraphicsState;
import songscribe.util.UIUtils;
import songscribe.util.UIUtils.ToolTipSection;

// Disambiguates from org.w3c.dom.events.MouseEvent (java.xml module)

/**
 * Component that renders a single staff line with its musical content.
 */
public class LineComponent extends ScoreComponent
    implements MouseMotionListener {

    private static final int LINT_MARK_MARGIN = 10;

    /**
     * Allows LineComponent to check selection without coupling to ScoreView.
     */
    public interface SelectionProvider {

        boolean isSelected(HitTarget target, int lineIndex);

        /**
         * An index range has no {@link HitTarget}; routing the question through
         * {@link #isSelected} would scan the line once per drawn element on every repaint.
         */
        boolean isElementRangeSelected(int elementIndex, int lineIndex);
    }

    @Nullable
    private Line line;

    private int lineIndex;

    private double middleLineYSs;

    private boolean middleLineYSsValid;

    @Nullable
    private SelectionProvider selectionProvider;

    /** {@code null} if none is playing. */
    private LineInvariants.@Nullable Playing playing;

    @Nullable
    private LayoutResult layoutResult;

    private boolean layoutDirty = true;

    /**
     * EDT-only. One flag for every line in the process, correct only because the app holds one
     * document at a time and every document install goes through {@link ScoreView#setSong},
     * which calls {@link #resetOverflowWarning}. A second window, or an install that bypasses
     * {@code setSong}, would have to scope it per document.
     */
    private static boolean overflowWarningShown = false;

    private boolean hasLeadingLyricContinuation;

    /**
     * {@code null} when the most recent press hit no element.
     */
    private @Nullable Integer pressHitIndex;

    private final LineSelectionHandler selectionHandler = new LineSelectionHandler(this);

    private final NoteDragHandler noteDragHandler = new NoteDragHandler(this);

    private final LineRenderer lineRenderer = new LineRenderer(this);

    /**
     * The stored mark answers for a point only while the point equals {@code viewPoint} and the
     * lint report, line layout and zoom are the very instances stored. Each is replaced, never
     * mutated, so any edit, script change or zoom forces a new lookup.
     *
     * @param viewPoint a copy, so later changes to the event's point do not reach it
     * @param hit       {@code null} when the point is on no mark
     */
    private record PointerMark(
        Point viewPoint,
        LintReport report,
        LayoutResult layoutResult,
        ViewScale viewScale,
        LineSelectionHandler.@Nullable LintMarkHit hit
    ) { }

    /**
     * {@code null} before the first lookup and after the pointer leaves this component.
     */
    private @Nullable PointerMark pointerMark = null;

    public LineComponent() {
        // This listener must be added before the tooltip manager registers below, and the order
        // must stay: Swing calls motion listeners in the order they were added, so mouseMoved
        // stores the mark under the pointer (pointerMark) before the manager's listener asks
        // the tooltip methods, which read it.
        addMouseMotionListener(this);

        // getToolTipText(MouseEvent) answers per point, so no tooltip text is set; registering
        // is what makes the tooltip manager ask. It must stay after addMouseMotionListener above.
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    public void setLine(Line line, int lineIndex) {
        this.line = line;
        this.lineIndex = lineIndex;
        setName(ComponentNames.line(lineIndex));
        layoutDirty = true;
        layoutResult = null;
        middleLineYSsValid = false;

        // Register with coordinator if score is available. The selection itself is the
        // coordinator's and is untouched by this rebuild — all that is registered here is
        // which line this index now holds.
        if (scoreView != null) {
            scoreView.getSelectionCoordinator().registerLine(lineIndex, line);
        }

        revalidate();
        repaint();
    }

    public @Nullable Line getLine() {
        return line;
    }

    public int getLineIndex() {
        return lineIndex;
    }

    public void setMiddleLineYSs(double middleLineYSs) {
        this.middleLineYSs = middleLineYSs;
    }

    public double getMiddleLineYSs() {
        if (!middleLineYSsValid && song != null) {
            middleLineYSs = calculateMiddleLineYSs();
            middleLineYSsValid = true;
        }

        return middleLineYSs;
    }

    public Ss getStaffTopYSs() {
        return new Ss(getMiddleLineYSs() - Staff.STAFF_HALF_SS);
    }

    public Ss getStaffBottomYSs() {
        return new Ss(getMiddleLineYSs() + Staff.STAFF_HALF_SS);
    }

    public double getViewPixelsPerStaffSpace() {
        return DocumentScale.PIXELS_PER_STAFF_SPACE * getViewScale().factor();
    }

    public void setSelectionProvider(@Nullable SelectionProvider selectionProvider) {
        this.selectionProvider = selectionProvider;
    }

    @Override
    public void setScoreView(ScoreView scoreView) {
        super.setScoreView(scoreView);

        // Register the line with the coordinator when the score arrives after setLine.
        if (line != null) {
            scoreView.getSelectionCoordinator().registerLine(lineIndex, line);
        }
    }

    public void setPlaying(LineInvariants.@Nullable Playing playing) {
        if (!Objects.equals(this.playing, playing)) {
            this.playing = playing;
            repaint();
        }
    }

    public LineInvariants.@Nullable Playing getPlaying() {
        return playing;
    }

    public void invalidateLayout() {
        layoutDirty = true;
        layoutResult = null;
        middleLineYSsValid = false;
        revalidate();
        repaint();
    }

    /** Null if layout hasn't been performed yet. */
    @Nullable
    public LayoutResult getLayoutResult() {
        return layoutResult;
    }

    private boolean layoutMissing() {
        return layoutResult == null;
    }

    private boolean needsLayout() {
        return layoutDirty || layoutMissing();
    }

    public void ensureLayout() {
        if (song != null && line != null && needsLayout()) {
            performLayout();
        }
    }

    public LayoutResult requireLayoutResult() {
        ensureLayout();

        var result = getLayoutResult();

        if (result == null) {
            throw unexpectedNullLayout();
        }

        return result;
    }

    public void setHasLeadingLyricContinuation(boolean hasLeadingLyricContinuation) {
        if (this.hasLeadingLyricContinuation != hasLeadingLyricContinuation) {
            this.hasLeadingLyricContinuation = hasLeadingLyricContinuation;
            layoutDirty = true;
            middleLineYSsValid = false;
        }
    }

    private void performLayout() {
        if (song == null || line == null || scoreView == null) {
            return;
        }

        var staffRightMarginSs = song.getLineWidthSs().value();
        var view = scoreView;
        var lyricRenderMetrics = view.getLyricRenderMetrics();
        var layoutEngine = new LayoutEngine(lyricRenderMetrics, staffRightMarginSs, view);
        var result = layoutEngine.layout(line, hasLeadingLyricContinuation, TempoMark.STACKED);

        layoutResult = result;
        layoutDirty = false;

        if (result.overflowsStaffWidth()) {
            warnLineOverflows();
        }
    }

    public static void resetOverflowWarning() {
        overflowWarningShown = false;
    }

    /**
     * Deferred to a later EDT cycle: layout runs during paint, and a modal dialog shown
     * synchronously would re-enter painting.
     */
    private static void warnLineOverflows() {
        if (overflowWarningShown) {
            return;
        }

        overflowWarningShown = true;

        SwingUtilities.invokeLater(() -> OptionDialogs.showWarningMessage(
            null, Strings.ALERT_TITLE_LINES_DO_NOT_FIT, Strings.ALERT_LINES_DO_NOT_FIT));
    }

    @Override
    protected void render(Graphics2D g2) {
        if (song == null || line == null) {
            return;
        }

        if (needsLayout()) {
            performLayout();
        }

        middleLineYSs = calculateMiddleLineYSs();
        middleLineYSsValid = true;

        // Paint pipeline: the zoom factor is applied EXACTLY ONCE, by the g2.scale(pxPerSs ×
        // factor) below.
        //
        //   LineRenderer and all element renderers draw in Ss; they NEVER re-multiply by pxPerSs
        //   or factor (a ssToPx inside a renderer is a bug). The one exception is
        //   LyricTextRenderer, which strips this transform and re-derives its own integer-pixel
        //   coords from LineInvariants.getViewPixelsPerStaffSpace() (= pxPerSs × factor) — the
        //   only reader of the zoomed scale inside a render pass.
        //
        //   "EXACTLY ONCE" is per paint pass, not per process. LineOverlayComponent is a
        //   sibling component that paints the line's overlays into its OWN graphics, so it
        //   legitimately establishes a second, parallel scale-and-translate of the same
        //   factor — see LineOverlayComponent.paintComponent. It reads the zoomed scale from
        //   getViewPixelsPerStaffSpace() below rather than re-multiplying inline, and the rule
        //   still holds within each pass: no renderer applies the factor a second time.
        var scale = getViewPixelsPerStaffSpace();

        // Layout always produces a result — an over-full line is placed on its collision floors
        // and drawn clipped, not abandoned — so a null here is a broken invariant,
        // not a line that would not fit.
        var result = layoutResult;

        if (result == null) {
            throw unexpectedNullLayout();
        }

        try (var _ = GraphicsState.save(g2, GraphicsState.Property.TRANSFORM)) {
            g2.scale(scale, scale);
            lineRenderer.render(g2);
        }

        // Drag rectangle is a pixel-space UI overlay — render after restoring the transform
        // so it is not affected by the staff-space scale.
        lineRenderer.renderSelectionBand(g2);
    }

    @Override
    public Dimension getPreferredSize() {
        if (song == null || line == null) {
            return new Dimension(0, 0);
        }

        if (needsLayout()) {
            performLayout();
        }

        var result = layoutResult;

        if (result == null) {
            // Layout always produces a result, so a null one means the pass never ran — it
            // returns early without a score view. The throw also guards the @Nullable
            // getScoreView() dereference below.
            throw unexpectedNullLayout();
        }

        // A line always spans the full document width, never just the extent of its last
        // element: LineRenderer draws the staff lines out to song.getLineWidthSs(), and
        // a narrower component would clip them, and an element-less line would collapse to zero
        // width and vanish.
        //
        // The width is the staff width even for a line whose content overflows it: the overflow
        // is meant to be clipped at the end of the staff, and these bounds are what clip it.
        //
        // The painted height, so the bounds hold everything this line draws — the measured
        // lineHeightSs is the spacing input and can be shorter than the staff surround.
        var heightSs = result.paintLineHeightSs(getScoreView().getLyricRenderMetrics());

        return new Dimension(
            toViewPx(song.getLineWidthSs()).sizePx(),
            toViewPx(heightSs).sizePx());
    }

    private double calculateMiddleLineYSs() {
        if (needsLayout()) {
            performLayout();
        }

        var result = layoutResult;

        if (result == null) {
            throw unexpectedNullLayout();
        }

        // The painted extent: the staff must sit where StaffLinesLayout reserved room for it,
        // and that reservation is floored. Using the measured content here would draw the staff
        // above the component's own top on any line whose ink stops at the staff, clipping it and
        // overlapping the line above.
        return result.paintAboveMidlineSs();
    }

    private RuntimeException unexpectedNullLayout() {
        return RuntimeError.exit("layout result is null after layout for line " + lineIndex);
    }

    public static void clearPreviewElement() {
        PreviewElementManager.clearPreviewElement();
    }

    public static void setAltPressed(boolean pressed) {
        PreviewElementManager.setAltPressed(pressed);
    }

    public boolean hasPreviewElement() {
        return PreviewElementManager.hasPreviewElement(this);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        // Before every early return: the tooltip methods read the mark stored here in every mode.
        var point = e.getPoint();
        var pointerHit = lookUpPointer(point);

        if (getGraceModeManager().mouseMoved(this, e)) {
            return;
        }

        // A pending placement tracks the insertion point under the mouse and suppresses
        // the normal preview element. Returns true to consume the event.
        if (getInsertionPointMode().mouseMoved(this, e)) {
            return;
        }

        // No preview anywhere in the clef/key signature column or over the lyrics, since a click
        // there selects rather than inserting an element. The hit tests ran once, above, and their
        // result serves here too.
        //
        // This is what makes a lyric outrank the preview rather than the other way round. The lyric
        // row is not out of the preview's reach: the row sits just below the line's laid-out content,
        // so on a line whose content stays high, a preview for a low note would be drawn right on top
        // of the lyric text. Clearing it here means the staff positions a lyric box covers cannot be
        // clicked to insert a note — the accepted price of leaving lyric text clickable wherever it
        // is drawn, and the reason a lyric is the one kind selectable in EDIT mode at all (see
        // handleEditModePress). A "?" drawn where a lyric is missing counts as lyric text: a
        // double-click on it opens the lyric editor there.
        var region = pointerHit.region();

        if (getScoreView().getMode() == Mode.EDIT
            && (selectionHandler.isWithinHeaderX(point)
                || (region != null && region.target() instanceof HitTarget.Lyric)
                || pointerHit.missingLyric() != null)) {
            clearPreviewElement();
            return;
        }

        PreviewElementManager.trackMouse(this, e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (getGraceModeManager().mouseDragged(this, e)) {
            return;
        }

        // While a placement is pending, a drag must not rubber-band a new selection. The press
        // that would start one is already suppressed (see pressed), so a drag here would
        // band from a stale press point, and any selection it made would be replaced by the
        // placement that follows anyway.
        if (getInsertionPointMode().isInProgress()) {
            return;
        }

        if (noteDragHandler.isDragActive()) {
            noteDragHandler.handleDrag(e);
            return;
        }

        if (selectionHandler.isDragging() || selectionHandler.isSelectionActive(e)) {
            selectionHandler.handleDrag(e);
        }
    }

    @Override
    protected void clicked(MouseEvent e, ScoreView view) {
        // Grace mode handles its own click logic. Returns true to consume the event.
        if (getGraceModeManager().mouseClicked(this, e)) {
            return;
        }

        // A pending placement lands at the clicked insertion point.
        // Returns true to consume the event.
        if (getInsertionPointMode().mouseClicked(this, e)) {
            return;
        }

        // A click does nothing at all while the song is playing, mirroring the guard in
        // pressed: everything below opens an editor, opens a modal dialog or inserts an
        // element, and DISABLE_WHEN_PLAYING does not reach a mouse handler.
        //
        // Read from PlaybackController rather than the sequencer, for the reason pressed
        // gives. The only playback check below is the selection handler's, which asks whether
        // the sequencer object is currently running — a different question during the window
        // between a playback transition and the sequencer following it, and one that gates
        // a modal dialog rather than just a selection change.
        //
        // This guard is the one that protects the lyric branch, handleClick, isWithinHeaderX
        // and PreviewElementManager.handleClick — none of which go near the double-click
        // gesture. isSelectionActive's own playback test, which gates the three notation
        // gestures below, asks the sequencer a different question, as that method's Javadoc
        // explains; the two reads look like duplicates, but this is the load-bearing one.
        if (PlaybackController.isPlaying()) {
            return;
        }

        // The same cascade the press path runs, so both agree on what is under the pointer, and
        // every test below shares its one result, as hitTestViewPoint's own contract asks. A lyric
        // outranks every other target. A "?" drawn where a lyric is missing stands where its
        // syllable would, so it is tested next and handled as one.
        var clickHit = selectionHandler.hitTestViewPoint(e.getPoint());
        var lyricHit = clickHit instanceof HitTarget.Lyric lyric ? lyric : null;
        var missingLyric = lyricHit == null
            ? selectionHandler.hitTestMissingLyricViewPoint(e.getPoint())
            : null;
        var missingLyricElement = missingLyric == null ? null : missingLyric.mark().element();

        if (lyricHit != null || missingLyricElement != null) {
            // Consumed either way: the press already selected the lyric, or mouseMoved hid the
            // preview over the "?", and nothing below this should insert an element or
            // re-resolve the click to a note.
            if (UIUtils.isLeftDoubleClick(e)
                && line != null
                && view.getActiveLyricEditor() == null) {
                var element = lyricHit != null ? editableLyricElement(lyricHit) : missingLyricElement;

                if (element != null) {
                    LyricEditor.deselectAndOpenOn(view, line, line.getElementIndex(element));
                }
            }

            return;
        }

        // All four double-click-to-edit gestures ask about the same point, so they share the
        // cascade result above. Pinning line to a local here is what lets the three that act on
        // notation take it as a plain non-null argument.
        //
        // No modifier gates this: a double-click always means "edit this," regardless of
        // which keys happen to be down, so shift+double-click on a selectable element opens
        // its editor exactly like a plain double-click would.
        //
        // isSelectionActive gates only the three that act on notation. It requires SELECT mode —
        // a plain double-click in SELECT mode, or one in EDIT mode only once pressed has
        // already switched permanently to SELECT, which Alt does anywhere and a plain click does
        // on the staff lines in the clef/key signature column — and it additionally rules out
        // playback. The attribution runs outside it on purpose; see its own method.
        var clickedLine = line;

        if (UIUtils.isLeftDoubleClick(e) && clickedLine != null) {
            if (editDoubleClickedAttribution(clickHit)) {
                return;
            }

            if (selectionHandler.isSelectionActive(e)) {
                if (!e.isShiftDown() && editLyricOnDoubleClickedElement(clickHit, clickedLine)) {
                    return;
                }

                if (editDoubleClickedAttachment(clickHit, clickedLine)) {
                    return;
                }

                if (editDoubleClickedKeyChange(e, clickedLine)) {
                    return;
                }
            }
        }

        if (selectionHandler.handleClick(e)) {
            return;
        }

        // Nothing is inserted anywhere in the clef/key signature column. mouseMoved
        // normally clears the preview there, but it cannot when the header widens under
        // a stationary mouse — a key change leaves a stale preview inside the
        // header with no mouse movement to re-clear it.
        if (selectionHandler.isWithinHeaderX(e.getPoint())) {
            return;
        }

        PreviewElementManager.handleClick(this);
    }

    /** Null when the syllable has no text to edit. */
    private static @Nullable StaffElement editableLyricElement(HitTarget.Lyric lyricHit) {
        var element = lyricHit.element();
        var lyric = element.getLyricForScript(lyricHit.script());
        return lyric != null && !lyric.text().isBlank() ? element : null;
    }

    /**
     * {@code null} when the click landed on anything other than an element. Package-private so
     * {@link NoteDragHandler} and {@link LineSelectionHandler} share this one definition of a
     * hit element.
     */
    static @Nullable Integer elementIndexOf(@Nullable HitTarget clickHit, Line line) {
        if (clickHit instanceof HitTarget.Element(var element)) {
            return line.getElementIndex(element);
        }

        return null;
    }

    private boolean editLyricOnDoubleClickedElement(@Nullable HitTarget clickHit, Line line) {
        var view = getScoreView();

        if (view.getActiveLyricEditor() != null) {
            return false;
        }

        // The press already hit-tested this point for every element the drag handler
        // admits; only the cases it declines (a rest, say) still need the resolved hit.
        var hitIndex = pressHitIndex != null ? pressHitIndex : elementIndexOf(clickHit, line);

        if (hitIndex == null) {
            return false;
        }

        var targetIndex = LyricTargetResolver.resolveLyricTarget(line, hitIndex);

        if (targetIndex == null) {
            return false;
        }

        LyricEditor.deselectAndOpenOn(view, line, targetIndex);
        return true;
    }

    /**
     * Runs outside the {@code isSelectionActive} test on purpose, so the gesture works in EDIT
     * mode as well as SELECT. The preview element takes precedence: the attribution's bottom
     * edge can reach into the insertable pitch range, and claiming the click there would make
     * those staff positions unreachable in EDIT mode.
     */
    private boolean editDoubleClickedAttribution(@Nullable HitTarget clickHit) {
        if (!(clickHit instanceof HitTarget.Attribution)) {
            return false;
        }

        if (PreviewElementManager.isPreviewClickTarget(this)) {
            return false;
        }

        Actions.SONG_SETTINGS_ACTION.openAt(SongSettingsDialog.Section.ATTRIBUTION);
        return true;
    }

    /**
     * A lyric editor open elsewhere on the line must not block editing an attachment. Answering
     * false is safe: {@code handleClick} consumes every click while selection is active.
     */
    private boolean editDoubleClickedAttachment(@Nullable HitTarget clickHit, Line line) {
        if (!(clickHit instanceof HitTarget.Attachment(var attachment))) {
            return false;
        }

        return AttachmentDialogController.edit(MainFrame.getInstance(), attachment, line);
    }

    /**
     * The cautionary drawn at the line's end edits the <em>next</em> line's key, because that is
     * the change it renders. Answering false is safe for the reason
     * {@link #editDoubleClickedAttachment} gives.
     */
    private boolean editDoubleClickedKeyChange(MouseEvent e, Line line) {
        var ready = readyLayout();

        if (ready == null) {
            return false;
        }

        var layoutResult = ready.layoutResult();
        var mouseXSs = selectionHandler.layoutXSs(e.getPoint());
        var mainFrame = MainFrame.getInstance();
        var headerTarget = layoutResult.hitTestHeaderKeyEdit(mouseXSs, line);

        if (headerTarget != null) {
            KeyChangeDialogController.editLineKey(mainFrame, headerTarget);
            return true;
        }

        var midLineKeyChange = layoutResult.hitTestMidLineKeyEdit(mouseXSs, line);

        if (midLineKeyChange != null) {
            KeyChangeDialogController.editKeyChange(mainFrame, line, midLineKeyChange);
            return true;
        }

        var cautionaryTarget = layoutResult.hitTestCautionaryKeyEdit(mouseXSs, line);

        if (cautionaryTarget != null) {
            KeyChangeDialogController.editLineKey(mainFrame, cautionaryTarget);
            return true;
        }

        return false;
    }

    @Override
    protected void pressed(MouseEvent e, ScoreView view) {
        if (getGraceModeManager().mousePressed(this, e)) {
            return;
        }

        // While a placement is pending, a press must not change the selection — it must be
        // left for the click that follows to resolve as a placement or a cancel. Without
        // this guard, a press on the staff lines in the header flips EDIT to SELECT and
        // selects the whole line, leaving the placement stuck pending with nothing placed.
        // Replacing a line means selecting it before Cmd+V.
        if (getInsertionPointMode().isInProgress()) {
            return;
        }

        // A press does nothing at all while the song is playing.
        //
        // This is a guard, not only an optimization: invoking an action programmatically does not
        // consult its enabled state, so DISABLE_WHEN_PLAYING on the mode actions — which stops the
        // menu item, the keyboard shortcut and the toolbar button — does not stop the mode
        // switch below. Without this guard a staff-line press in the header, or any alt+click,
        // would flip EDIT to SELECT mid-playback.
        //
        // Everything else below already refuses: the EDIT-mode selection path, the pitch-drag
        // handler and the selection handler each check playback themselves. Returning here also
        // keeps the cascade — which walks every element in the line — from running for an answer
        // none of them will read.
        //
        // Read from PlaybackController rather than the sequencer, since this stands in for
        // DISABLE_WHEN_PLAYING and that flag resolves against this same state. The grace-mode and
        // paste-mode guards stay above it so their behavior is untouched.
        if (PlaybackController.isPlaying()) {
            return;
        }

        // In EDIT mode, alt+click anywhere and a plain click on the staff lines in the clef/key
        // signature area both switch to SELECT mode, then fall through to normal handling so the
        // rest of this method acts as if we had been in SELECT mode all along.
        //
        // The cascade walks every element in the line, so it runs exactly once per press: this one
        // result feeds the mode switch, the EDIT-mode selection path, the pitch-drag handler and
        // the press dispatch, all below.
        var pressHit = selectionHandler.hitTestViewPoint(e.getPoint());

        if (view.getMode() == Mode.EDIT
            && (e.isAltDown() || pressHit instanceof HitTarget.StaffLine)) {
            Actions.SELECT_MODE_ACTION.perform(this);
        }

        // A lyric is selectable in EDIT mode too, in place and without switching modes; nothing else
        // is. The mode is re-checked because an alt+click or a staff-line hit above may have just
        // switched us to SELECT, where the selection handler below handles lyrics along with
        // everything else.
        if (view.getMode() == Mode.EDIT
            && selectionHandler.handleEditModePress(pressHit)) {
            return;
        }

        // In SELECT mode, a note head press starts a pitch drag. The drag handler reads this same
        // cascade result rather than hit-testing again, so a lyric that outranked an element
        // rectangle reaching into the lyric row can never be mistaken for a note to drag.
        if (noteDragHandler.handlePress(e, pressHit)) {
            return;
        }

        if (selectionHandler.isSelectionActive(e)) {
            selectionHandler.handlePress(e, pressHit);
        }
    }

    @Override
    protected void released(MouseEvent e, ScoreView view) {
        // Capture before handleRelease() clears it. Refreshed on every release, so
        // clicked can never read a value left over from an earlier press.
        pressHitIndex = noteDragHandler.getDragElementIndex();

        if (getGraceModeManager().mouseReleased(this, e)) {
            return;
        }

        if (noteDragHandler.isDragActive()) {
            noteDragHandler.handleRelease();
            return;
        }

        selectionHandler.handleRelease();
    }

    @Override
    protected void entered(MouseEvent e, ScoreView view) {
        PreviewElementManager.mouseEnteredLine(this);
    }

    @Override
    protected void exited(MouseEvent e, ScoreView view) {
        pointerMark = null;
        getInsertionPointMode().mouseExited(this);
        PreviewElementManager.mouseExitedLine(this);
    }

    /**
     * @return {@code null} when the pointer is on no mark, so no tooltip shows
     */
    @Override
    public @Nullable String getToolTipText(MouseEvent event) {
        var lintMarkHit = lintMarkAtPointer(event.getPoint());
        return lintMarkHit == null ? null : lintToolTipText(lintMarkHit.mark());
    }

    /**
     * @return {@code null} when the pointer is on no mark
     */
    @Override
    public @Nullable Point getToolTipLocation(MouseEvent event) {
        var lintMarkHit = lintMarkAtPointer(event.getPoint());
        return lintMarkHit == null ? null : placeLintToolTip(lintMarkHit).locationIn(this);
    }

    /**
     * @param region       {@code null} when a click there gets none
     * @param missingLyric {@code null} when the point is on no "?"
     */
    private record PointerHit(@Nullable HitRegion region, LineSelectionHandler.@Nullable MissingLyricHit missingLyric) {

        static final PointerHit NONE = new PointerHit(null, null);
    }

    private PointerHit lookUpPointer(Point viewPoint) {
        var ready = readyLayout();

        if (ready == null) {
            pointerMark = null;
            return PointerHit.NONE;
        }

        var report = ready.line().getSong().getLinter().getReport();
        var layout = ready.layoutResult();
        var region = selectionHandler.hitRegionAtViewPoint(viewPoint);
        var missingLyric = selectionHandler.hitTestMissingLyricViewPoint(viewPoint);
        var hit = selectionHandler.lintMarkAt(region, missingLyric, report, layout);
        var previous = pointerMark;
        var previousHit = previous == null ? null : previous.hit();

        pointerMark = new PointerMark(new Point(viewPoint), report, layout, getViewScale(), hit);

        if (hit != null && (previousHit == null || !previousHit.mark().equals(hit.mark()))) {
            getScoreView().requireLintCalloutController().hoverDidEnterMark();
        }

        return new PointerHit(region, missingLyric);
    }

    /**
     * @return {@code null} when the point is on no mark or this component has no line laid out
     */
    private LineSelectionHandler.@Nullable LintMarkHit lintMarkAtPointer(Point viewPoint) {
        var stored = pointerMark;
        var ready = readyLayout();

        if (stored != null
            && ready != null
            && stored.viewPoint().equals(viewPoint)
            && stored.report() == ready.line().getSong().getLinter().getReport()
            && stored.layoutResult() == ready.layoutResult()
            && stored.viewScale() == getViewScale()) {
            return stored.hit();
        }

        lookUpPointer(viewPoint);

        var looked = pointerMark;
        return looked == null ? null : looked.hit();
    }

    LintToolTipPlacement.Placed revealLintMark(LintMark mark) {
        var lintMarkHit = selectionHandler.lintMarkHit(mark);
        var markBounds = viewBounds(lintMarkHit.boundsSs());

        markBounds.grow(LINT_MARK_MARGIN, 0);

        // A line is usually wider than the viewport, which then shows only the left end of a
        // rectangle that is the whole line, so the rectangle spans the mark's item across.
        scrollRectToVisible(new Rectangle(markBounds.x, 0, markBounds.width, getHeight()));

        // The placement reads where the item is on the screen, so it follows the scroll.
        return placeLintToolTip(lintMarkHit);
    }

    private LintToolTipPlacement.Placed placeLintToolTip(LineSelectionHandler.LintMarkHit lintMarkHit) {
        return LintToolTipPlacement.place(
            viewBounds(lintMarkHit.boundsSs()), this, lintToolTipText(lintMarkHit.mark()));
    }

    private Rectangle viewBounds(Rectangle2D boundsSs) {
        var viewScale = getViewScale();
        var middleLineYSs = getMiddleLineYSs();
        var leftXViewPx = viewScale.toViewPx(new Ss(boundsSs.getMinX())).positionPx();
        var rightXViewPx = viewScale.toViewPx(new Ss(boundsSs.getMaxX())).positionPx();
        var topYViewPx = viewScale.toViewPx(new Ss(boundsSs.getMinY() + middleLineYSs)).positionPx();
        var bottomYViewPx = viewScale.toViewPx(new Ss(boundsSs.getMaxY() + middleLineYSs)).positionPx();
        return new Rectangle(leftXViewPx, topYViewPx, rightXViewPx - leftXViewPx, bottomYViewPx - topYViewPx);
    }

    private String lintToolTipText(LintMark mark) {
        var lineModel = getLine();

        if (lineModel == null) {
            throw RuntimeError.exit("lint tooltip requested for a line component with no line");
        }

        var sections = new ArrayList<ToolTipSection>();

        for (var finding : lineModel.getSong().getLinter().getReport().findingsAt(mark)) {
            sections.add(new ToolTipSection(finding.heading(), null, finding.description()));
        }

        return UIUtils.toolTipHtml(sections);
    }

    /** A live line component always has one. */
    @Override
    public ScoreView getScoreView() {
        if (scoreView == null) {
            throw RuntimeError.exit("ScoreView reference not set on LineComponent");
        }

        return scoreView;
    }

    /**
     * Populated before any line is laid out, so a live line component always has them.
     */
    public LyricRenderMetrics getLyricRenderMetrics() {
        return getScoreView().getLyricRenderMetrics();
    }

    /**
     * Null when the view has not been populated with them yet.
     */
    @Nullable LyricRenderMetrics findLyricRenderMetrics() {
        return getScoreView().findLyricRenderMetrics();
    }

    private GraceModeManager getGraceModeManager() {
        return EditModeManager.getGraceModeManager();
    }

    private InsertionPointMode getInsertionPointMode() {
        return EditModeManager.getInsertionPointMode();
    }

    /** Null if unavailable. */
    @Nullable StaffElement getPreviewElement() {
        return EditModeManager.getPreviewElement();
    }

    boolean isPreviewElementVisible() {
        return EditModeManager.isPreviewElementVisible();
    }

    boolean isGraceModeInProgress() {
        return EditModeManager.getGraceModeManager().isInProgress();
    }

    /** Only valid when {@link #isGraceModeInProgress()} is true. */
    double getGraceModeLockedXSs() {
        return getGraceModeManager().getLockedInsertionXSs();
    }

    boolean isPendingCancelElement(StaffElement element) {
        return GraceModeManager.isPendingCancel(element);
    }

    boolean isPendingConnectElement(StaffElement element) {
        return GraceModeManager.isPendingConnect(element);
    }

    ElementFrame gracePreviewLineFrame() {
        var graceModeManager = EditModeManager.getGraceModeManager();

        if (graceModeManager.getGraceLineComponent() == this) {
            var preview = graceModeManager.getHostInsertionPreview();

            if (preview != null) {
                return ElementFrame.lineLevelWithPreviewShift(
                    graceModeManager.getHostInsertionIndex(),
                    preview.shiftForSubsequentElementsSs()
                );
            }
        }

        return ElementFrame.LINE_LEVEL;
    }

    /**
     * Null when this line has no layout yet.
     */
    @Nullable
    LineInvariants previewInvariants() {
        if (layoutResult == null) {
            return null;
        }

        return lineRenderer.buildInvariants();
    }

    @Nullable SelectionProvider getSelectionProvider() {
        return selectionProvider;
    }

    record ReadyLayout(Line line, LayoutResult layoutResult) {}

    @Nullable ReadyLayout readyLayout() {
        if (line == null) {
            return null;
        }

        ensureLayout();

        if (layoutResult == null) {
            return null;
        }

        return new ReadyLayout(line, layoutResult);
    }

    public boolean isDraggingSelection() {
        return selectionHandler.isDragging();
    }

    public void clearSelectionBand() {
        selectionHandler.handleRelease();
    }

    NoteDragHandler getNoteDragHandler() {
        return noteDragHandler;
    }

    LineSelectionHandler getSelectionHandler() {
        return selectionHandler;
    }

    /** {@code null} when no drag is in progress. */
    LineSelectionHandler.@Nullable SelectionBand getSelectionBand() {
        return selectionHandler.getSelectionBand();
    }
}
