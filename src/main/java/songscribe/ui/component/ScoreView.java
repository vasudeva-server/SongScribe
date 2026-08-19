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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import com.formdev.flatlaf.util.SystemInfo;
import net.engio.mbassy.listener.Handler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.FileExtensions;
import songscribe.Strings;
import songscribe.dom.DocPx;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.Ss;
import songscribe.dom.StaffElement;
import songscribe.dom.ViewPx;
import songscribe.error.RuntimeError;
import songscribe.export.ExportOptions;
import songscribe.export.ImageExporter;
import songscribe.export.SVGExporter;
import songscribe.font.DocumentFonts;
import songscribe.font.DocumentFontsHolder;
import songscribe.font.FontKey;
import songscribe.hit.HitTarget;
import songscribe.io.LoadWarning;
import songscribe.io.SongFileLoader;
import songscribe.io.SongLoadResult;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.LayoutResult;
import songscribe.layout.LineLayoutProvider;
import songscribe.layout.LyricRenderMetrics;
import songscribe.layout.PageModel;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.ZoomDidChangeNotification;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.Mode;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.OptionDialogs;
import songscribe.ui.ViewScale;
import songscribe.ui.ZoomController;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.LineOverlayComponent;
import songscribe.ui.component.score.MainPanel;
import songscribe.ui.component.score.OverlayHost;
import songscribe.ui.component.score.PreviewElementManager;
import songscribe.ui.component.score.ScorePanel;
import songscribe.ui.dialog.MigrationWindow;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.ScoreActions;
import songscribe.ui.platform.mac.PinchZoomGesture;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.renderer.RenderContext;
import songscribe.ui.selection.ElementSelection;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.util.FileUtils;
import songscribe.util.GraphicsState;
import songscribe.util.StringUtils;

/**
 * This class is responsible for managing and drawing the music score
 * and its lyrics. It also handles user input for editing the score.
 *
 * <p>A {@code ScorePanel} inside the scroll pane centers this view, which is sized to the full
 * page and bordered by the page margins. Its children are the {@code LyricEditor} (absolute
 * bounds, topmost, and present only while a lyric is being edited), the per-line
 * {@code LineOverlayComponent}s (absolute bounds, above the score and below the editor), and a
 * {@code MainPanel} that stacks the title, subtitle, margin strut, {@code StaffPanel} of
 * {@code LinePanel}s, text panel and footnotes down the Y axis.
 *
 * <p>See {@code docs/score-view-hierarchy.md} for the full containment tree.
 */

public final class ScoreView
    extends JComponent
    implements
    ComponentHierarchyProvider,
    DocumentFontsHolder,
    InputHandlerCallback,
    LineComponent.SelectionProvider,
    LineLayoutProvider,
    OverlayHost,
    RenderContext,
    ScoreActions {

    private static final Logger LOG = LoggerFactory.getLogger(ScoreView.class);

    /**
     * Z-order index of the active lyric editor: the topmost child of this view. Overlays
     * registered through {@link #addOverlay} sit directly beneath it — see that method's
     * z-order contract.
     */
    public static final int LYRIC_EDITOR_Z_ORDER = 0;

    private static final String DISABLED_KEY_BINDING = "none";

    // Colors used to draw the music score in various states — read from UIManager for theming.
    // Callers should not cache these values; read at render time.
    public static Color getPlayingNoteColor() {
        return FlatLafProps.getColor(FlatLafKey.SCORE_PLAYING_NOTE_COLOR);
    }

    public static Color getPreviewElementColor() {
        return FlatLafProps.getColor(FlatLafKey.SCORE_PREVIEW_ELEMENT_COLOR);
    }

    public static Color getSelectionColor() {
        return FlatLafProps.getColor(FlatLafKey.SCORE_SELECTION_COLOR);
    }

    private final Dimension sheetSize = new Dimension();

    // Per-view zoom state — the sole source of truth for this view's zoom.
    // On-score components read it on demand via getViewScale().
    private final ViewScale viewScale = new ViewScale();

    // Called when a file is successfully opened (e.g. to update the window title)
    private final @Nullable Consumer<? super File> onFileOpened;

    private final ScoreViewState viewState;

    // In some contexts (such as playback), we don't want to allow dragging
    private boolean dragDisabled = false;

    // The model for the score
    @Nullable
    private Song song = null;

    // The scroll pane that contains the score + margin
    @Nullable
    private JScrollPane scrollPane = null;

    // The score itself
    @Nullable
    private JPanel scorePanel = null;

    // TODO: Not sure why this is here. It really should be in the renderer, and should be based
    //  on the advance of the clef.
    private int leadingKeysPosPx = 32;

    // Coordinates selection state across lines
    private final SelectionCoordinator selectionCoordinator;

    // Handles music editing operations
    @Nullable
    private MusicEditOperations operations = null;

    // Manages clipboard state for copy/paste operations
    private final ClipboardManager clipboardManager;

    // New JComponent-based score panel (Phase 2 hierarchy)
    @Nullable
    private MainPanel mainPanel = null;

    // Coordinates message handling
    @Nullable
    private ScoreViewController controller = null;

    // Navigates the component hierarchy (null in headless mode)
    private final @Nullable ComponentHierarchyNavigator hierarchyNavigator;

    // Handles mouse and keyboard input (null in headless mode)
    private final @Nullable ScoreInputHandler inputHandler;

    // True after init() has been called (interactive mode only)
    private boolean initialized = false;

    // Owns the document fonts and the lyric render metrics derived from them.
    private final DocumentFontManager documentFontManager;

    // The currently-open lyric editor overlay, if any. Set by LyricEditor.openOn /
    // dismiss so getActiveLyricEditor() doesn't have to scan getComponents() per paint.
    @Nullable private LyricEditor activeLyricEditor;

    // Maps each registered KeyStroke to its action key so bindings can be toggled.
    private final Map<KeyStroke, Object> scoreKeyBindings = new LinkedHashMap<>();

    /**
     * Creates a ScoreView with core infrastructure (SAX parser, selection, clipboard,
     * edit mode). This is sufficient for headless use (converters pass {@code null}).
     * <p>
     * For interactive use, call {@link #init()} after construction to create the
     * UI components (view, panels, message coordinator) and the initial Song.
     *
     * @param onFileOpened callback invoked when a file is successfully opened,
     *                     or {@code null} for headless (converter) use
     */
    public ScoreView(@Nullable Consumer<? super File> onFileOpened) {
        this.onFileOpened = onFileOpened;
        var headless = onFileOpened == null;
        viewState = new ScoreViewState();

        selectionCoordinator = new SelectionCoordinator(this);
        clipboardManager = new ClipboardManager();
        documentFontManager = new DocumentFontManager(this);
        EditModeManager.init(selectionCoordinator, this, this);

        if (headless) {
            hierarchyNavigator = null;
            inputHandler = null;
        } else {
            hierarchyNavigator = new ComponentHierarchyNavigator(this);
            hierarchyNavigator.setScoreView(this);
            var handler = new ScoreInputHandler(this);
            inputHandler = handler;
            setLayout(new BorderLayout());
            setFocusable(true);
            addKeyListener(handler);
        }
    }

    public void createSVG(File outputFile) {
        SVGExporter.createSVG(outputFile);
    }

    /**
     * Initializes the interactive UI: view, panels, message coordinator,
     * and the initial Song. Must be called exactly once after construction
     * for interactive (non-converter) use. Not needed for headless converters.
     */
    public void init() {
        setName(ComponentNames.SCORE);

        // Create initial song
        song = new Song();
        documentFontManager.installFonts(DocumentFonts.defaultFonts());

        // Initialize UI components
        initScorePanel();
        initMainPanel();

        // The install above could not cascade — there was no main panel yet.
        documentFontManager.applyFonts();

        updatePageLayout(song.getLineWidthSs());

        if (inputHandler != null) {
            addMouseMotionListener(inputHandler);
            addMouseListener(inputHandler);
            addMouseWheelListener(inputHandler);

            if (SystemInfo.isMacOS) {
                PinchZoomGesture.installOn(this);
            }
        }

        selectionChanged();
        initKeys();

        // One preview overlay for this view's lifetime; it is retargeted, never recreated.
        PreviewElementManager.installOverlay(this);
        EditModeManager.getInsertionPointMode().installOverlay();

        // Initialize insertion note with default type
        setPreviewElement(EditModeManager.makePreviewElement());

        MessageCenter.subscribe(this);
        syncPlaybackPrefs();
        song.setModified(false);

        // Create operations and message coordinator (requires mainPanel to be set)
        operations = new MusicEditOperations(song, selectionCoordinator);
        controller = new ScoreViewController(
            this,
            operations,
            selectionCoordinator,
            clipboardManager
        );

        initialized = true;
    }

    private void initKeys() {
        if (inputHandler != null) {
            scoreKeyBindings.putAll(inputHandler.installKeyBindings(this));
        }
    }


    private void initScorePanel() {
        scorePanel = new ScorePanel(this);
        scrollPane = new JScrollPane(scorePanel);
        scrollPane.setBorder(new ThemeAwareMatteBorder(1, 0, 1, 0, "ToolBar.separatorColor"));
        updateScoreSurroundBackground();
    }

    @Override
    public void updateUI() {
        super.updateUI();

        if (scrollPane != null) {
            updateScoreSurroundBackground();
        }
    }

    private void updateScoreSurroundBackground() {
        if (scrollPane == null) {
            return;
        }

        var color = FlatLafProps.getColor(FlatLafKey.SCORE_PANEL_BACKGROUND);
        scrollPane.setBackground(color);
        scrollPane.getViewport().setBackground(color);
    }

    private void initMainPanel() {
        mainPanel = new MainPanel();
        mainPanel.setScoreView(this);
        mainPanel.setSong(getSong());
        mainPanel.setVisible(true);
        add(mainPanel, BorderLayout.CENTER);
        setupLineComponentState();
    }

    /**
     * Sets up selection provider and initial state for all LineComponents.
     * <p>
     * This wires up the selection checking from ScoreView to enable
     * note coloring in the component-based rendering.
     */
    void setupLineComponentState() {
        if (hierarchyNavigator != null) {
            hierarchyNavigator.setupLineComponentState(this, this);
        }

        // The line components the overlays point at may have just been recreated.
        retargetOverlays();
    }

    /**
     * Returns the MainPanel for the new component hierarchy.
     * <p>
     * This is the top-level panel for the Phase 2 JComponent-based rendering.
     */
    @Override
    public @Nullable MainPanel getMainPanel() {
        return mainPanel;
    }

    void setMainPanel(MainPanel mainPanel) {
        this.mainPanel = mainPanel;
    }

    void setScorePanel(JPanel scorePanel) {
        this.scorePanel = scorePanel;
    }

    void setScrollPane(JScrollPane scrollPane) {
        this.scrollPane = scrollPane;
    }

    public boolean isDragDisabled() {
        return dragDisabled;
    }

    /**
     * Returns the LineComponent for the given line index, or null if not found.
     */
    @Nullable
    public LineComponent getLineComponent(int lineIndex) {
        return hierarchyNavigator != null ? hierarchyNavigator.getLineComponent(lineIndex) : null;
    }

    /**
     * Returns the line's live layout — the very one it is painted from — so a saved file's
     * coordinates match the score on screen by construction rather than by re-derivation.
     * <p>
     * Nearly free: a line whose layout is clean is returned as is, and only the few dirty lines
     * are laid out. Saving is user-initiated and there is no autosave timer, so even a full pass
     * costs nothing the user would notice.
     */
    @Override
    public @Nullable LayoutResult layoutFor(Line line, int lineIndex) {
        var lineComponent = getLineComponent(lineIndex);

        if (lineComponent == null) {
            return null;
        }

        lineComponent.ensureLayout();

        return lineComponent.getLayoutResult();
    }

    public boolean openFile(File file, boolean updateCurrentFile) {
        var result = SongFileLoader.load(file);

        if (result instanceof SongLoadResult.Success success) {
            var lineWidthInches = ScaleContext.ssToInches(success.song().getLineWidthSs());

            if (lineWidthInches > PageModel.MAX_LINE_WIDTH_INCHES) {
                result = new SongLoadResult.LineWidthTooLarge(file, lineWidthInches, PageModel.MAX_LINE_WIDTH_INCHES);
            }
        }

        return switch (result) {
            case SongLoadResult.Success success -> {
                // Install the document's fonts before setSong so the initial layout
                // pass uses them; otherwise the line is laid out with the previous
                // defaults-from-prefs attribution font, and the first edit shifts
                // attachments (tempo, etc.) once layout reruns with the doc font.
                installDocumentFonts(success.fonts());
                setSong(success.song());

                var tupletReport = success.tupletReport();
                var accidentalsConverted = success.accidentalsConverted();

                if (!tupletReport.isEmpty() || accidentalsConverted) {
                    // setSong clears the modified flag internally; a migration that
                    // happens silently on load must still prompt the user to save the
                    // migrated version, so re-mark modified after installing the song.
                    success.song().setModified(true);
                    MigrationWindow.show(
                        SwingUtilities.getWindowAncestor(this), tupletReport, accidentalsConverted);
                }

                if (updateCurrentFile && onFileOpened != null) {
                    onFileOpened.accept(FileUtils.hasExtension(file, FileExtensions.SONGWRITER) ? null : file);
                }

                for (var warning : success.warnings()) {
                    if (warning.type() == LoadWarning.Type.INVALID_LYRICS_DATE) {
                        OptionDialogs.showWarningMessage(
                            null,
                            Strings.ALERT_TITLE_LYRICS_DATE,
                            Strings.ALERT_LYRICS_DATE_INVALID,
                            warning.description()
                        );
                    }
                }

                LOG.info("Song loaded: {}", file.getName());
                yield true;
            }
            case SongLoadResult.NewerVersion e -> {
                OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_FILE_ERROR, Strings.ERROR_FILE_OPEN_NEWER_VERSION);
                LOG.error("Could not open '{}': document version is newer than the application supports", file.getName(), e.cause());
                yield false;
            }
            case SongLoadResult.ParseError e -> {
                OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_FILE_ERROR, Strings.ERROR_FILE_OPEN_DAMAGED, file.getName());
                LOG.error("Could not open damaged file '{}'", file.getName(), e.cause());
                yield false;
            }
            case SongLoadResult.IoError e -> {
                OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_FILE_ERROR, Strings.ERROR_FILE_OPEN_NO_PERMISSION, file.getName());
                LOG.error("Could not open file '{}': permission error", file.getName(), e.cause());
                yield false;
            }
            case SongLoadResult.LineWidthTooLarge e -> {
                OptionDialogs.showErrorMessage(
                    null,
                    Strings.ALERT_TITLE_FILE_ERROR,
                    Strings.ERROR_LINE_WIDTH_TOO_LARGE
                );
                LOG.error(
                    "Refused to open {}: line width {} inches exceeds maximum {}",
                    file.getName(),
                    e.actualInches(),
                    e.maxInches()
                );
                yield false;
            }
            case SongLoadResult.WrongSoftware e -> {
                var software = e.software();
                var name = (software != null && !software.isBlank())
                    ? software
                    : Strings.get(Strings.ALERT_MUSICXML_FOREIGN_OTHER);
                OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_FILE_ERROR, Strings.ALERT_MUSICXML_FOREIGN, name);
                LOG.error("Refused to open '{}': not created by SongScribe (software: {})", file.getName(), software);
                yield false;
            }
            case SongLoadResult.UnsupportedFileFormat e -> {
                var fileName = file.getName();
                OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_FILE_ERROR, Strings.ALERT_MUSICXML_UNSUPPORTED, fileName);
                LOG.error("Refused to open '{}': unsupported file format ({})", fileName, e.detail());
                yield false;
            }
        };
    }

    @Override
    public void syncPlaybackPrefs() {
        EditModeManager.setPlayInsertedNote(Prefs.getBoolean(PrefsKey.PLAY_INSERTED_NOTE));
        // If true, the score is played with repeats
        var playWithRepeats = Prefs.getBoolean(PrefsKey.PLAY_WITH_REPEATS);

        // Delegate playback settings to PlaybackController
        PlaybackController.setInstrument(Prefs.getInt(PrefsKey.INSTRUMENT));
        PlaybackController.setTempoChangePercent(Prefs.getInt(PrefsKey.TEMPO_CHANGE_PERCENT));
        PlaybackController.setNoteDurationPercent(Prefs.getInt(PrefsKey.PLAYBACK_NOTE_DURATION));
        PlaybackController.setPlayWithRepeats(playWithRepeats);
        PlaybackController.applyVolumeFromPrefs();
        PlaybackController.applyPrefsDuringPlayback();
    }

    @Override
    public void setKeyBindingsEnabled(boolean enabled) {
        var inputMap = getInputMap(JComponent.WHEN_FOCUSED);

        if (enabled) {
            scoreKeyBindings.forEach(inputMap::put);
        } else {
            scoreKeyBindings.keySet().forEach(keyStroke -> inputMap.put(keyStroke, DISABLED_KEY_BINDING));
        }
    }

    public @Nullable JScrollPane getScoreScrollPane() {
        return scrollPane;
    }

    public JScrollPane requireScrollPane() {
        if (scrollPane == null) {
            throw RuntimeError.exit("score scroll pane not initialized");
        }
        return scrollPane;
    }

    /**
     * Implements {@link songscribe.ui.renderer.RenderContext}, which still asks the question by
     * index. Renderers ask {@link #isSelected(HitTarget, int)} instead.
     */
    @Override
    public boolean isElementSelected(int elementIndex, int lineIndex) {
        return selectionCoordinator.isElementSelected(elementIndex, lineIndex);
    }

    @Override
    public boolean isSelected(HitTarget target, int lineIndex) {
        return selectionCoordinator.isSelected(target, lineIndex);
    }

    @Override
    public boolean isElementRangeSelected(int elementIndex, int lineIndex) {
        return selectionCoordinator.isElementRangeSelected(elementIndex, lineIndex);
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        var graphics2d = (Graphics2D) g;

        try (var _ = GraphicsState.save(
            graphics2d,
            GraphicsState.Property.COLOR
        )) {
            graphics2d.setColor(FlatLafProps.getColor(FlatLafKey.SCORE_PAGE_SCREEN_BACKGROUND));
            graphics2d.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    /**
     * Declares that this container has overlapping children: the line overlays are siblings of
     * the score content and deliberately paint on top of it.
     * <p>
     * This is a statement of fact, not a workaround. {@link JLayeredPane} returns {@code false}
     * for exactly the same reason, and any future container that hosts overlays — the page
     * components under {@code specs/184-pagination.md} — must too.
     * <p>
     * The consequence is that Swing cannot resolve a repaint to a single child: this view
     * repaints <b>every</b> child intersecting the damaged rectangle, including the
     * {@link LineComponent} beneath an overlay, which runs the full line render pipeline.
     * Sizing overlays to exact ink <b>shrinks</b> that rectangle — it used to be inflated by
     * 1.5 staff spaces of slop — but it does not remove the underlying repaint. The win is
     * correctness (no stale ink, no clipped glyphs), not a reduction in repaint cost.
     */
    @Override
    public boolean isOptimizedDrawingEnabled() {
        return false;
    }

    @Nullable
    public StaffElement getPreviewElement() {
        return EditModeManager.getPreviewElement();
    }

    @Override
    public void setPreviewElement(@Nullable StaffElement element) {
        if (element != null) {
            var currentPreviewElement = EditModeManager.getPreviewElement();

            if (currentPreviewElement != null) {
                element.setStaffPosition(currentPreviewElement.getStaffPosition());
                element.setXOffsetPx(currentPreviewElement.getXOffsetPx());
            } else {
                EditModeManager.setPreviewElement(element);
            }

            element.setDirection(StaffElement.defaultDirection(element));
        }

        EditModeManager.setPreviewElement(element);

        // The preview overlay caches the ink the renderers produced for the previous element,
        // so a new element is a rebuild, not merely a repaint. It must also re-anchor to the
        // real mouse position rather than whatever insertion index/staff position was last
        // tracked, or the preview draws in the wrong spot until the mouse actually moves.
        PreviewElementManager.previewElementTypeDidChange();
        repaint();
    }

    @Override
    public void clearSelection() {
        // Callers repaint the line they are acting on, not the one losing its selection,
        // so the outgoing line would keep painting a stale highlight without this.
        var deselectedLine = getLineComponent(selectionCoordinator.getActiveLineIndex());

        // Read before the clear: afterwards there is no selection left to ask which lines
        // were drawing it.
        var deselectedTarget = selectionCoordinator.getSelectedTarget();

        selectionCoordinator.clearSelection();
        selectionChanged();

        if (deselectedLine != null) {
            deselectedLine.repaint();
        }

        repaintTieHalves(deselectedTarget);
    }

    /**
     * Repaints both lines a cross-line tie is drawn on; does nothing for any other target.
     * <p>
     * Every {@link HitTarget} kind but one is drawn wholly by the line component the user
     * clicked, so that component repainting itself is enough. A tie can straddle a line
     * boundary (#493): one {@link songscribe.dom.Tie} sits in both lines' span lists with half
     * drawn by each, and {@link SelectionCoordinator#isSelected} answers "selected" for both.
     * Without this the far half keeps painting the stale color until something unrelated
     * happens to repaint it.
     * <p>
     * Both halves are repainted rather than only the far one, so no caller has to work out
     * which half it is holding. Repainting the near one again costs nothing — Swing coalesces
     * it with the repaint that line already asked for.
     */
    public void repaintTieHalves(@Nullable HitTarget target) {
        if (!(target instanceof HitTarget.Tie(var tie))) {
            return;
        }

        repaintLine(tie.getAnchorLine());
        repaintLine(tie.getEndLine());
    }

    /**
     * Repaints the component drawing {@code line}, if there is one. A line the song no longer
     * holds, or one whose component has not been built, has nothing to repaint —
     * {@link Song#indexOfLine} answers −1 and no component carries that index.
     */
    private void repaintLine(@Nullable Line line) {
        if (line == null) {
            return;
        }

        var lineComponent = getLineComponent(getSong().indexOfLine(line));

        if (lineComponent != null) {
            lineComponent.repaint();
        }
    }

    public void deselect() {
        clearSelection();
        repaint();
    }

    @Override
    public void selectionChanged() {
        MessageCenter.post(new MusicSelectionDidChangeNotification(this));
    }

    @Override
    public void extendSelectionTo(int targetIndex) {
        // A target selection has no anchor to extend from, and extendSelectionTo no-ops on it.
        selectionCoordinator.extendSelectionTo(targetIndex);
        selectionChanged();
        repaint();
    }

    @Override
    public void editLyricOnSelection() {
        // Leaving SELECT mode clears the selection, so a live selection already implies
        // the mode gate that EditLyricAction spells out; playback does not clear it.
        if (PlaybackController.isPlaying()) {
            return;
        }

        var selection = selectionCoordinator.getSelection();

        if (selection == null) {
            return;
        }

        // EditLyricAction is disabled unless exactly one element is selected, and a
        // selected staff line reports itself as a selection spanning the whole line.
        // Requiring a single element keeps Return in step with the menu command rather
        // than silently picking one note out of a range the user never singled out.
        if (selection.begin() != selection.end()) {
            return;
        }

        var line = selection.line();
        var targetIndex = LyricTargetResolver.resolveLyricTarget(line, selection.begin());

        if (targetIndex < 0) {
            return;
        }

        LyricEditor.deselectAndOpenOn(this, line, targetIndex);
    }

    @Override
    public void zoomByWheel(double preciseWheelRotation, Point viewPoint) {
        ZoomController.zoomByWheel(preciseWheelRotation, viewPoint);
    }

    @Override
    public void zoomByMagnification(double magnification, @Nullable Point viewPoint) {
        ZoomController.zoomByMagnification(magnification, viewPoint);
    }

    @Override
    public void forwardWheelScroll(MouseWheelEvent e) {
        if (scrollPane == null) {
            return;
        }

        // Rebuild the event with the scroll pane as source/target so it is delivered
        // straight to JScrollPane's own listeners, bypassing AWT's ancestor search
        // (which would otherwise stop at this view, since it has its own wheel listener).
        var pointInScrollPane = SwingUtilities.convertPoint(this, e.getPoint(), scrollPane);
        var forwardedEvent = new MouseWheelEvent(
            scrollPane,
            e.getID(),
            e.getWhen(),
            e.getModifiersEx(),
            pointInScrollPane.x,
            pointInScrollPane.y,
            e.getXOnScreen(),
            e.getYOnScreen(),
            e.getClickCount(),
            e.isPopupTrigger(),
            e.getScrollType(),
            e.getScrollAmount(),
            e.getWheelRotation(),
            e.getPreciseWheelRotation()
        );

        scrollPane.dispatchEvent(forwardedEvent);
    }

    public int getSelectionSize() {
        return selectionCoordinator.getSelectionSize();
    }

    public boolean isInitialized() {
        return song != null;
    }

    public @Nullable ScoreViewController getController() {
        return controller;
    }

    @Override
    public Song getSong() {
        if (song == null) {
            throw RuntimeError.exit("song not initialized");
        }

        return song;
    }

    public String getSuggestedFileName() {
        var theSong = getSong();
        var title = theSong.getTitle();
        var numberStr = theSong.getNumber();
        var stringBuilder = new StringBuilder(title.length() + 10);

        try {
            var number = Integer.parseInt(numberStr);
            stringBuilder.append(String.format("%03d", number));
        } catch (NumberFormatException nfe) {
            stringBuilder.append(numberStr);
        }

        if (!numberStr.isEmpty()) {
            stringBuilder.append(' ');
        }

        stringBuilder.append(StringUtils.stripDiacritics(title));
        return stringBuilder.toString();
    }

    public void setSong(Song song) {
        // MBassador holds Song subscribers by weak reference, so the outgoing Song
        // keeps handling broadcast commands — spawning spurious undo steps against
        // the dead document — until GC clears it. Detach it deterministically here.
        if (this.song != null && this.song != song) {
            this.song.dispose();
        }

        this.song = song;

        // A new document gets its own chance to warn that a line does not fit; the alert is shown
        // once per document (refs #696).
        LineComponent.resetOverflowWarning();

        // Reset zoom to 100% for the new/opened song before laying out at the old zoom.
        ZoomController.resetZoom();

        // Core setup needed for both headless and interactive modes. Passing the song's own
        // width leaves the model untouched — setLineWidthSs early-returns on an equal value.
        updatePageLayout(song.getLineWidthSs());

        // The fit pass records ELEMENT_SPACING_RATIO changes through a
        // mutation-tracked setter. On load there is no open modification bracket,
        // and this normalization is not a user edit — suspend tracking so it
        // neither throws nor pollutes undo history or the modified flag.
        song.withoutMutationTracking(() -> {
            for (var i = 0; i < song.lineCount(); i++) {
                drawWidthIfWiderLine(song.getLine(i), true);
            }
        });

        song.setModified(false);

        if (!initialized) {
            return;
        }

        // Interactive-only setup below

        if (operations == null) {
            operations = new MusicEditOperations(song, selectionCoordinator);
        } else {
            operations.setSong(song);
        }

        if (controller == null) {
            controller = new ScoreViewController(
                this,
                operations,
                selectionCoordinator,
                clipboardManager
            );
        }

        PlaybackController.stop();
        selectionCoordinator.clearSelection();

        if (mainPanel == null) {
            throw RuntimeError.exit("mainPanel not initialized");
        }

        mainPanel.setSong(getSong());
        setupLineComponentState();

        syncPlaybackPrefs();

        // Notify all subscribers (LyricsPanel, ScoreViewController, UIActions, etc.)
        // that the song has been fully replaced. This must happen after all
        // ScoreView state is consistent.
        MessageCenter.post(new DocumentDidLoadNotification(song));

        // Reset scroll position to top-left for the new/opened song
        if (scrollPane != null) {
            scrollPane.getViewport().setViewPosition(new Point(0, 0));
        }

        repaint();
    }

    @Nullable
    public StaffElement getSingleSelectedElement() {
        return selectionCoordinator.getSingleSelectedElement();
    }

    public Dimension getSheetSize() {
        return sheetSize;
    }

    public int getSheetWidthPx() {
        return ScaleContext.ssToRoundedPx(getSong().getLineWidthSs());
    }

    public int getSheetHeightPx() {
        // TODO: Calculate from component hierarchy
        //
        // Zoom-independent by construction: unlike ScoreView's own getHeight() (which is
        // clamped against the page height and rounded at the view scale in layoutPage),
        // this reproduces that max/margin arithmetic entirely in document space so future
        // exporters never inherit view zoom. mainPanel's preferred height is the one
        // remaining view-scaled read; it is converted back to document px via the view's
        // own ViewScale rather than a raw factor() division, and is not first clamped or
        // rounded against the page height, so no page-height information is lost in the
        // round trip.
        var contentHeightViewPx = (mainPanel != null) ? mainPanel.getPreferredSize().height : 0;
        var contentHeightDocPx = viewScale.toDocPx(new ViewPx(contentHeightViewPx));
        return new DocPx(pageHeightForContent(
            PageModel.getPageHeightPx().value(),
            contentHeightDocPx.value(),
            PageModel.getTopMarginPx().value(),
            PageModel.getBottomMarginPx().value()
        )).roundedPx();
    }

    /**
     * Returns the sheet height in pixels, adjusted for the given export options.
     * When the rendering pipeline is fully implemented, this will calculate
     * the height without including excluded sections (lyrics, title, attribution).
     *
     * @param options controls which content sections to include in the measurement
     * @return the sheet height in pixels
     */
    public int getSheetHeightPx(ExportOptions options) {
        // TODO: Calculate height based on options without relying on component layout.
        // For now, returns full height since rendering is not yet implemented.
        return getSheetHeightPx();
    }

    @Override
    public void drawWidthIfWiderLine(Line line, boolean revalidateOnly) {
        // Exclude the auto-maintained FINAL_DOUBLE_BARLINE from stretch calculations:
        // its position is fixed at the line's right edge and must not be treated as
        // the end note when computing the ratio.
        var effectiveCount = line.effectiveElementCount();

        if (effectiveCount > 1) {
            var endNote = line.getElement(effectiveCount - 1);
            float idealSpace;

            if (revalidateOnly) {
                idealSpace = (float) endNote.getContentWidthPx();
            } else {
                idealSpace = (float) ScaleContext.ssToPx(HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS) + 20;
            }

            var lineWidthPx = getSong().getLineWidthPx();

            if (endNote.getXOffsetPx() > (lineWidthPx - idealSpace)) {
                var firstX = line.getElement(0).getXOffsetPx();
                var ratio =
                    (lineWidthPx - idealSpace - firstX) /
                        (endNote.getXOffsetPx() - firstX);

                for (var i = 1; i < effectiveCount; i++) {
                    var note = line.getElement(i);
                    note.setXOffsetPx(
                        firstX + Math.round((note.getXOffsetPx() - firstX) * ratio)
                    );
                }

                line.changeElementSpacingRatio(ratio);
            }
        }
    }

    @Override
    public SelectionCoordinator getSelectionCoordinator() {
        return selectionCoordinator;
    }

    public ScoreViewState getViewState() {
        return viewState;
    }

    @Override
    public Mode getMode() {
        return viewState.getMode();
    }

    @Override
    public @Nullable Window getWindow() {
        return SwingUtilities.getWindowAncestor(this);
    }

    public void setMode(Mode mode) {
        viewState.setMode(mode);
    }

    @Override
    public int getLeadingKeysPosPx() {
        return leadingKeysPosPx;
    }

    public void setLeadingKeysPosPx(int leadingKeysPosPx) {
        this.leadingKeysPosPx = leadingKeysPosPx;
    }


    public void setDragDisabled(boolean dragDisabled) {
        this.dragDisabled = dragDisabled;
    }

    /**
     * Stores {@code lineWidthSs} on the song and re-lays out the page for it.
     * <p>
     * The width is taken in staff spaces, not pixels, so the stored value is exactly the one
     * passed in. Taking int pixels here meant the model's width was reconstructed from a
     * quantized pixel count, which snapped it to a {@code 1/DEFAULT_PIXELS_PER_STAFF_SPACE}
     * grid — including on file open, where the callers below pass the song's own width purely
     * to re-lay out the page. Pixels are still where this ends up, but only inside
     * {@link #layoutPage}, at the Swing boundary that actually needs an int.
     */
    public void updatePageLayout(double lineWidthSs) {
        getSong().setLineWidthSs(lineWidthSs);
        relayoutPage();
    }

    /**
     * Re-centers the page for the current zoom and the song's stored line width, writing
     * nothing to the model.
     * <p>
     * Only a change of the line width or the zoom needs this. Content that grows or shrinks
     * needs nothing: {@link #getPreferredSize} derives the page height from the content on
     * every ask, so the ordinary {@code revalidate} a changed component already fires re-fits
     * the page.
     * <p>
     * {@link #updatePageLayout} is the entry point for a change <em>of</em> the line width; it
     * stores the width and then comes here.
     */
    public void relayoutPage() {
        // layoutPage expects view px, so fold in the current zoom. Skipping this left the page
        // centered for the wrong width at any zoom other than 100% (content against the left
        // edge when zoomed out, clipped on the right when zoomed in).
        layoutPage(viewScale.toViewPx(new Ss(getSong().getLineWidthSs())).roundedPx());
    }

    /**
     * The page canvas: a full page, or taller when the score's content outgrows one.
     * <p>
     * Derived on every ask rather than stored through {@code setPreferredSize}, because the
     * content's height is not this view's to know about. A line gains an accidental that
     * lifts an ending above it, a title wraps to a second line, an attribution block grows —
     * each changes what the page must hold, and none of them is a thing this view can be told
     * about without a list of change kinds that would have to name every mutation there is.
     * The {@code revalidate} that the changed component itself fires reaches here and asks
     * again, which is the whole mechanism.
     *
     * @return the page size in view pixels at the current zoom
     * @invariant never shorter than a full page, and never shorter than the content plus the
     *     top and bottom margins
     */
    @Override
    public Dimension getPreferredSize() {
        // Sizes round up via ceilPx() so content is never clipped at high zoom; margins round
        // to nearest via roundedPx(), matching the border layoutPage sets from the same values.
        var contentHeightPx = (mainPanel != null) ? mainPanel.getPreferredSize().height : 0;

        return new Dimension(
            viewScale.toViewPx(PageModel.getPageWidthPx()).ceilPx(),
            (int) pageHeightForContent(
                viewScale.toViewPx(PageModel.getPageHeightPx()).ceilPx(),
                contentHeightPx,
                topMarginPx(),
                bottomMarginPx()
            )
        );
    }

    /**
     * The page policy both {@link #getPreferredSize} and {@link #getSheetHeightPx} answer with:
     * a page never shrinks below a full page, and never crops its content.
     * <p>
     * Unit-agnostic on purpose — the two callers work in different spaces, view pixels and
     * document pixels, and the policy is the same in both. Each converts before calling.
     *
     * @param pageHeight the full page height
     * @param contentHeight the height of what the page holds, excluding margins
     * @param topMargin the page's top margin
     * @param bottomMargin the page's bottom margin
     * @return the greater of {@code pageHeight} and the content plus both margins
     */
    private static double pageHeightForContent(
        double pageHeight,
        double contentHeight,
        double topMargin,
        double bottomMargin) {

        return Math.max(pageHeight, contentHeight + topMargin + bottomMargin);
    }

    /** The page's top margin in view pixels at the current zoom. */
    private int topMarginPx() {
        return viewScale.toViewPx(PageModel.getTopMarginPx()).roundedPx();
    }

    /** The page's bottom margin in view pixels at the current zoom. */
    private int bottomMarginPx() {
        return viewScale.toViewPx(PageModel.getBottomMarginPx()).roundedPx();
    }

    /**
     * Re-centers the content for {@code lineWidthPx} at the current zoom, <em>without</em>
     * mutating the song's stored line width. {@link #updatePageLayout} writes the width to the
     * model first and then calls this; the zoom handler calls it directly so a pure view change
     * never records a document mutation or undo entry.
     * <p>
     * Sizing the canvas is not part of this — {@link #getPreferredSize} derives it.
     */
    private void layoutPage(int lineWidthPx) {
        var topMarginPx = topMarginPx();
        var bottomMarginPx = bottomMarginPx();

        // getHorizontalMarginPx operates in document space; convert lineWidthPx (view px) to
        // document px before the call, then convert the resulting margin back to view px.
        var lineWidthDocPx = viewScale.toDocPx(new ViewPx(lineWidthPx)).roundedPx();
        var horizontalMarginPx =
            viewScale.toViewPx(PageModel.getHorizontalMarginPx(lineWidthDocPx)).roundedPx();
        setBorder(BorderFactory.createEmptyBorder(
            topMarginPx,
            horizontalMarginPx,
            bottomMarginPx,
            horizontalMarginPx
        ));
        invalidate();

        if (scorePanel != null) {
            scorePanel.invalidate();
        }

        runWithoutViewportBlit(JScrollPane::validate);

        repaint();
    }

    /**
     * Invalidates {@code component} and every descendant.
     * <p>
     * {@link Container#invalidate} propagates <em>upward</em> only, and a still-valid
     * container serves cached geometry: {@link Container#getPreferredSize} reuses its last
     * answer, and a {@code LayoutManager2} is told to discard its own cache only when the
     * container it manages is invalidated. Every other trigger for that — a mutation, a
     * resize — originates inside the tree, so the normal upward propagation suffices.
     * <p>
     * A zoom change is the one input that comes from outside it: it alters every pixel
     * dimension in the tree while changing no component's own state, so nothing below this
     * view is ever marked invalid. Without this walk {@code StaffPanel} kept returning the
     * previous zoom's cached preferred size, was therefore never resized, was therefore
     * never invalidated, and {@code StaffLinesLayout} never ran again — leaving every line
     * painting at the new zoom inside bounds measured at the old one, so the lines
     * overlapped and clipped (issue #591).
     */
    private static void invalidateTree(Component component) {
        component.invalidate();

        if (component instanceof Container container) {
            for (var child : container.getComponents()) {
                invalidateTree(child);
            }
        }
    }

    /** The per-view zoom state. On-score components read it on demand. */
    public ViewScale getViewScale() {
        return viewScale;
    }

    /**
     * Runs before any other {@link ZoomDidChangeNotification} handler so the zoom is already
     * applied by the time they react — see the priority requirement documented on {@link
     * ZoomDidChangeNotification}.
     */
    private static final int ZOOM_APPLY_PRIORITY = Message.HIGH_PRIORITY;

    /**
     * Applies a {@link ZoomDidChangeNotification} to this view. The sole handler responsible
     * for actually performing a zoom change — see {@link #applyZoomPercent}.
     */
    @Handler(priority = ZOOM_APPLY_PRIORITY)
    public void zoomDidChangeApplyZoom(ZoomDidChangeNotification message) {
        applyZoomPercent(message.getNewZoomPercent(), message.getAnchorPoint());
    }

    /**
     * Refreshes every hosted overlay's on-screen bounds against the now-current zoom.
     * <p>
     * Overlay bounds are pixel-cached ({@link LineOverlayComponent#updateBounds()}) and
     * otherwise only refreshed on the next validation pass or mouse-driven update; without
     * this an overlay (e.g. the hover preview) stays at its pre-zoom screen position until
     * the mouse moves. Runs after {@link #zoomDidChangeApplyZoom} (default priority is lower
     * than {@link #ZOOM_APPLY_PRIORITY}), so the zoom is already applied.
     */
    @Handler
    public void zoomDidChangeRefreshOverlayBounds(ZoomDidChangeNotification message) {
        forEachLineOverlay(LineOverlayComponent::updateBounds);
    }

    /**
     * Applies {@code newPercent} to this view's {@link ViewScale} and re-anchors
     * the viewport around {@code anchorPoint}, a point in this ScoreView's local
     * (content) coordinate space — e.g. the cursor position for wheel/pinch zoom.
     * When {@code anchorPoint} is null, anchors at the viewport's horizontal
     * center and top edge instead (menu/keyboard zoom).
     * <p>
     * Called only from {@link #zoomDidChangeApplyZoom}, which is this view's own
     * high-priority handler of {@link ZoomDidChangeNotification} — see that message's
     * class doc for why every other reactor to a zoom change goes through the same
     * notification rather than a direct call. On-score components read
     * {@link #getViewScale()} on demand, so nothing is pushed into the tree here.
     * EDT-only, per the {@code ZoomController} contract.
     */
    public void applyZoomPercent(int newPercent, @Nullable Point anchorPoint) {
        runWithoutViewportBlit(
            scoreScrollPane -> applyZoomPercentAndReanchor(scoreScrollPane, newPercent, anchorPoint)
        );

        repaint();
    }

    /**
     * Runs {@code body} against this view's scroll pane with the viewport's blit
     * optimization disabled, restoring the previous scroll mode afterward. Does nothing
     * when this view has no scroll pane yet. Safe to nest — each level restores the mode
     * it found.
     * <p>
     * A relayout that resizes the content or moves the scroll position invalidates every
     * pixel in the viewport, but JViewport's blit optimization cannot know that. In
     * {@code BLIT_SCROLL_MODE} every scroll-position change — an explicit
     * {@code setViewPosition}, and the clamping one {@code ViewportLayout} performs from
     * inside {@code validate()} — synchronously copies the pixels already on screen to the
     * new offset and repaints only the newly exposed strip. That puts a frame of the
     * <em>previous</em> rendering, shifted, on screen before the queued repaint draws the
     * score afresh: glaring on a large jump such as 800% to 400% zoom, imperceptible when
     * the per-step delta is tiny, as during a pinch or slider drag.
     * {@code SIMPLE_SCROLL_MODE} paints from scratch instead, so no stale pixels ever reach
     * the screen; the previous mode is restored so ordinary scrolling keeps the blit.
     * Refs #651.
     */
    private void runWithoutViewportBlit(Consumer<? super JScrollPane> body) {
        if (scrollPane == null) {
            return;
        }

        var viewport = scrollPane.getViewport();
        var previousScrollMode = viewport.getScrollMode();
        viewport.setScrollMode(JViewport.SIMPLE_SCROLL_MODE);

        try {
            body.accept(scrollPane);
        } finally {
            viewport.setScrollMode(previousScrollMode);
        }
    }

    /**
     * Applies {@code newPercent} to this view's {@link ViewScale}, re-lays out the page at
     * the new zoom, and scrolls so that {@code anchorPoint} — in this view's local (content)
     * coordinate space, or the viewport's horizontal center and top edge when null — stays
     * put on screen. The body of {@link #applyZoomPercent}.
     */
    private void applyZoomPercentAndReanchor(
        JScrollPane scoreScrollPane,
        int newPercent,
        @Nullable Point anchorPoint
    ) {
        var viewport = scoreScrollPane.getViewport();
        var extentSize = viewport.getExtentSize();

        Point anchorViewportOffset;
        Point anchorContentPoint;

        if (anchorPoint == null) {
            // Horizontally anchor at the viewport's center; vertically anchor at the
            // current scroll position (the viewport's top edge).
            anchorViewportOffset = new Point(extentSize.width / 2, 0);

            // Convert the anchor to ScoreView-content coordinates using the pre-revalidate
            // bounds — component bounds still reflect the old zoom at this point.
            anchorContentPoint = SwingUtilities.convertPoint(viewport, anchorViewportOffset, this);
        } else {
            // anchorPoint already arrives in ScoreView-content coordinates (it comes
            // from a MouseEvent whose listener is registered directly on this view).
            anchorContentPoint = anchorPoint;
            anchorViewportOffset = SwingUtilities.convertPoint(this, anchorPoint, viewport);
        }

        var oldPercent = viewScale.getZoomPercent();
        viewScale.setZoomPercent(newPercent);

        // Drop every cached pixel size in the score tree first: layoutPage reads
        // mainPanel's preferred size, and that read must already reflect the new zoom.
        invalidateTree(this);

        // Recompute the canvas's preferred size at the new zoom before re-layout. Go through
        // relayoutPage (not updatePageLayout) because zooming is a view-only change with no
        // business writing to the model at all — the width it lays out for is the model's own.
        relayoutPage();

        // Force synchronous re-layout so the new (post-zoom) sizes are realized before
        // we read them below; plain revalidate() is async and would leave stale sizes.
        if (scorePanel != null) {
            scorePanel.invalidate();
        }

        scoreScrollPane.validate();

        var zoomRatio = newPercent / (double) oldPercent;
        var view = viewport.getView();

        // ScoreView's origin within the scrolled view (ScorePanel), post-validate.
        var contentOrigin = SwingUtilities.convertPoint(this, 0, 0, view);

        var newViewPosition = computeAnchoredViewPosition(
            anchorContentPoint,
            zoomRatio,
            contentOrigin,
            anchorViewportOffset,
            viewport.getViewSize(),
            extentSize
        );

        viewport.setViewPosition(newViewPosition);
    }

    /**
     * Computes the post-zoom scroll position that keeps {@code anchorContentPoint}
     * (a point in old-zoom ScoreView-content coordinates) under
     * {@code anchorViewportOffset} (a point in viewport-local coordinates).
     * <p>
     * Pure function of its arguments — no Swing component access — so it can be
     * unit-tested. Scales the content anchor by {@code zoomRatio}, offsets it by the
     * post-validate content origin within the scrolled view, subtracts the viewport
     * anchor offset, and clamps into {@code [0, viewSize - extentSize]} per axis.
     */
    static Point computeAnchoredViewPosition(
        Point anchorContentPoint,
        double zoomRatio,
        Point contentOrigin,
        Point anchorViewportOffset,
        Dimension viewSize,
        Dimension extentSize
    ) {
        var scaledAnchorX = anchorContentPoint.x * zoomRatio;
        var scaledAnchorY = anchorContentPoint.y * zoomRatio;

        var targetX = contentOrigin.x + scaledAnchorX - anchorViewportOffset.x;
        var targetY = contentOrigin.y + scaledAnchorY - anchorViewportOffset.y;

        var maxX = Math.max(0, viewSize.width - extentSize.width);
        var maxY = Math.max(0, viewSize.height - extentSize.height);

        var clampedX = (int) Math.round(Math.clamp(targetX, 0, maxX));
        var clampedY = (int) Math.round(Math.clamp(targetY, 0, maxY));

        return new Point(clampedX, clampedY);
    }

    @Override
    public int getSelectedLine() {
        return selectionCoordinator.getSelectedLine();
    }

    @Override
    public int getPlayingLine() {
        // Playback state is now managed by PlaybackController
        // For now, return -1 (not playing) since LineComponents handle their own state
        return -1;
    }

    @Override
    public int getPlayingNote() {
        // Playback state is now managed by PlaybackController
        // For now, return -1 (not playing) since LineComponents handle their own state
        return -1;
    }

    @Nullable
    public ElementSelection getSelection() {
        return selectionCoordinator.getSelection();
    }

    public BufferedImage createImageForExport(
        Color background,
        double scale,
        MyBorder border,
        ExportOptions options
    ) {
        return ImageExporter.createImageForExport(
            this,
            background,
            scale,
            border,
            options
        );
    }

    public enum ConnectionType {
        BEAM,
        TIE,
        SLUR,
    }

    public int getPasteboardSize() {
        return clipboardManager.getSize();
    }

    public boolean canDeleteLine() {
        return selectionCoordinator.canDeleteLine();
    }

    /** @see DocumentFontManager#findLyricRenderMetrics() */
    public @Nullable LyricRenderMetrics findLyricRenderMetrics() {
        return documentFontManager.findLyricRenderMetrics();
    }

    /** @see DocumentFontManager#getLyricRenderMetrics() */
    public LyricRenderMetrics getLyricRenderMetrics() {
        return documentFontManager.getLyricRenderMetrics();
    }

    /** @see DocumentFontManager#rebuildLyricRenderMetrics() */
    public void rebuildLyricRenderMetrics() {
        documentFontManager.rebuildLyricRenderMetrics();
    }

    @Nullable
    public LyricEditor getActiveLyricEditor() {
        return activeLyricEditor;
    }

    public void setActiveLyricEditor(@Nullable LyricEditor editor) {
        activeLyricEditor = editor;
    }

    // -------------------------------------------------------------------------
    // Document fonts
    // -------------------------------------------------------------------------

    /** @see DocumentFontManager#getFonts() */
    public DocumentFonts getDocumentFonts() {
        return documentFontManager.getFonts();
    }

    /** {@inheritDoc} */
    @Override
    public Font getFont(FontKey key) {
        return documentFontManager.getFont(key);
    }

    /** @see DocumentFontManager#installFonts(DocumentFonts) */
    public void installDocumentFonts(DocumentFonts fonts) {
        documentFontManager.installFonts(fonts);
    }

    /** @see DocumentFontManager#setFonts(DocumentFonts) */
    public void setFonts(DocumentFonts newFonts) {
        documentFontManager.setFonts(newFonts);
    }

    /**
     * Adds {@code overlay} as a free-floating absolute-bounds child. ScoreView uses BorderLayout
     * for its main panel, so we must register the overlay with null constraints (so the
     * layout manager ignores it) and restore the main panel's center mapping that
     * {@link #add(Component)} silently overwrote.
     *
     * <h2>Z-order contract</h2>
     * Overlays paint above the score content and <b>below</b> the active lyric editor: a lyric
     * editor is a focused text field with a caret, and an overlay painting over it is a defect.
     * The ordering is a property of this container, not of the order things were registered in
     * — this method places every overlay directly beneath {@link #LYRIC_EDITOR_Z_ORDER}, and
     * {@link LyricEditor#openOn} reclaims that index when an editor opens, pushing the existing
     * overlays down by one.
     */
    @Override
    public void addOverlay(JComponent overlay) {
        add(overlay);

        var layout = (BorderLayout) getLayout();
        layout.removeLayoutComponent(overlay);

        if (mainPanel != null) {
            layout.addLayoutComponent(mainPanel, BorderLayout.CENTER);
        }

        // Index 0 is the topmost child; an open lyric editor owns it, so overlays start
        // immediately below it and above everything else.
        var occupiedByEditor = activeLyricEditor != null && activeLyricEditor != overlay;
        setComponentZOrder(overlay, occupiedByEditor ? LYRIC_EDITOR_Z_ORDER + 1 : LYRIC_EDITOR_Z_ORDER);
    }

    @Override
    public void removeOverlay(JComponent overlay) {
        var boundsPx = overlay.getBounds();
        remove(overlay);
        repaint(boundsPx);
    }

    @Override
    public JComponent getHostComponent() {
        return this;
    }

    /**
     * Notifies the overlays once this host's subtree is laid out.
     * <p>
     * Overlay bounds are fixed in staff spaces but not in pixels, so they go stale whenever a
     * line moves or resizes. The causes do not share a mechanism: a title or subtitle height
     * change moves StaffPanel without re-running its layout manager, while a line height change
     * re-runs it. {@code validateTree()} sits above both and is cause-independent by
     * construction. {@link Container#validateTree()} lays out this container and then recurses
     * into its children, so calling it first guarantees every descendant's bounds are final
     * before the overlays read them.
     * <p>
     * Only {@code setBounds}/{@code setLocation} may be called on the overlays from here —
     * never {@code revalidate()}, which would re-enter layout.
     */
    @Override
    protected void validateTree() {
        super.validateTree();

        forEachLineOverlay(LineOverlayComponent::updateBounds);
    }

    /**
     * Re-resolves every overlay's target line after the line components have been recreated.
     * <p>
     * A rebuilt layout discards the {@link LineComponent}s the overlays point at, and an
     * overlay holding a stale target hides itself. Managers retarget on mouse motion only, so
     * without this the preview would visibly wink out on every edit that rebuilds the layout
     * and stay hidden until the pointer moved.
     */
    private void retargetOverlays() {
        // retarget() ends in inkDidChange(), which recomputes the bounds itself.
        forEachLineOverlay(LineOverlayComponent::retarget);
    }

    /**
     * Applies {@code action} to every {@link LineOverlayComponent} currently parented to this
     * view.
     * <p>
     * Derived from the children rather than from a list maintained by
     * {@link #addOverlay}/{@link #removeOverlay}, so it cannot disagree with the component
     * hierarchy — an overlay removed by any route simply stops being visited. Indexes rather
     * than {@link Container#getComponents()} because that returns a defensive copy, and this
     * runs from {@link #validateTree()} on every layout pass.
     */
    private void forEachLineOverlay(Consumer<? super LineOverlayComponent> action) {
        for (var i = 0; i < getComponentCount(); i++) {
            if (getComponent(i) instanceof LineOverlayComponent overlay) {
                action.accept(overlay);
            }
        }
    }

}
