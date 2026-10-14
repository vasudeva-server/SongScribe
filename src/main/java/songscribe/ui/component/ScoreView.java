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
import javax.swing.ToolTipManager;

import com.formdev.flatlaf.util.SystemInfo;
import net.engio.mbassy.listener.Handler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.FileExtensions;
import songscribe.Strings;
import songscribe.binding.Bindings;
import songscribe.binding.ObservableValue;
import songscribe.binding.ValueProperty;
import songscribe.dom.ChangeReport;
import songscribe.dom.Line;
import songscribe.dom.DocumentScale;
import songscribe.dom.Song;
import songscribe.dom.Ss;
import songscribe.dom.NoteElement;
import songscribe.dom.StaffElement;
import songscribe.dom.ViewPx;
import songscribe.error.RuntimeError;
import songscribe.font.DocumentFonts;
import songscribe.font.DocumentFontsHolder;
import songscribe.font.FontKey;
import songscribe.hit.HitTarget;
import songscribe.io.LoadWarning;
import songscribe.io.SongFileLoader;
import songscribe.io.SongLoadResult;
import songscribe.layout.LayoutResult;
import songscribe.layout.LineLayoutProvider;
import songscribe.layout.LyricRenderMetrics;
import songscribe.layout.PageModel;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.MessageSubscription;
import songscribe.message.notification.ActiveScriptDidChangeNotification;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.LintingNotification;
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
import songscribe.ui.component.score.LintCalloutController;
import songscribe.ui.component.score.MainPanel;
import songscribe.ui.component.score.OverlayHost;
import songscribe.ui.component.score.PreviewElementManager;
import songscribe.ui.component.score.ScorePanel;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.ScoreActions;
import songscribe.ui.platform.mac.PinchZoomGesture;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.renderer.RenderContext;
import songscribe.ui.selection.ElementSelection;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.undo.ReplayTarget;
import songscribe.util.FileUtils;
import songscribe.util.GraphicsState;
import songscribe.util.StringUtils;

/**
 * Manages and draws the music score and its lyrics, and handles input for editing it. See
 * {@code docs/score-view-hierarchy.md}.
 *
 * <h2>Lifecycle</h2>
 * Process-lifetime: {@link #init()} subscribes the view to the message bus and nothing ever
 * unsubscribes it. The class is deliberately not {@code songscribe.lifecycle.Disposable}; there is
 * nothing to release (the long-lived-views case in {@code docs/lifecycle.md}).
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
    ReplayTarget,
    ScoreActions {

    private static final Logger LOG = LoggerFactory.getLogger(ScoreView.class);

    /**
     * Z-order index of the active lyric editor, the topmost child. See {@link #addOverlay}.
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

    /**
     * Deliberately the overflow color: a lint error and an overflowing line both mean the score
     * shows something wrong with the document.
     */
    public static Color getLintColor() {
        return FlatLafProps.getColor(FlatLafKey.SCORE_OVERFLOW_COLOR);
    }

    private final Dimension sheetSize = new Dimension();

    // Per-view zoom state — the sole source of truth for this view's zoom.
    // On-score components read it on demand via getViewScale(). ViewScale is a value,
    // so a zoom change replaces this instance rather than mutating it.
    private ViewScale viewScale = new ViewScale();

    // The zoom percentage as an observable, for what has to react to a zoom change
    // rather than read the zoom when it happens to paint. Kept equal to
    // viewScale.getZoomPercent() by applyZoomPercent, which is the only writer.
    private final ValueProperty<Integer> zoomPercent = new ValueProperty<>(viewScale.getZoomPercent());

    // Effects this view declares on its own state. Never disposed: every ScoreView lives
    // for the process (see docs/lifecycle.md), so there is no retirement to release them at.
    private final Bindings bindings = new Bindings();

    // Called when a file is successfully opened (e.g. to update the window title)
    private final Consumer<? super File> onFileOpened;

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

    // Manages clipboard state for copy/paste operations
    private final ClipboardManager clipboardManager;

    // JComponent-based score panel
    @Nullable
    private MainPanel mainPanel = null;

    // Coordinates message handling
    @Nullable
    private ScoreViewController controller = null;

    // Navigates the component hierarchy
    private final ComponentHierarchyNavigator hierarchyNavigator;

    // Handles mouse and keyboard input
    private final ScoreInputHandler inputHandler;

    // Owns the document fonts and the lyric render metrics derived from them.
    private final DocumentFontManager documentFontManager;

    // The currently-open lyric editor overlay, if any. Set by LyricEditor.openOn /
    // dismiss so getActiveLyricEditor() doesn't have to scan getComponents() per paint.
    @Nullable private LyricEditor activeLyricEditor;

    // Created by init(), after the scroll pane whose viewport it listens to.
    @Nullable
    private LintCalloutController lintCalloutController = null;

    // Maps each registered KeyStroke to its action key so bindings can be toggled.
    private final Map<KeyStroke, Object> scoreKeyBindings = new LinkedHashMap<>();

    public ScoreView(Consumer<? super File> onFileOpened) {
        this.onFileOpened = onFileOpened;
        viewState = new ScoreViewState();

        selectionCoordinator = new SelectionCoordinator(this);
        clipboardManager = new ClipboardManager();
        documentFontManager = new DocumentFontManager(this);
        EditModeManager.init(selectionCoordinator, this, this);

        hierarchyNavigator = new ComponentHierarchyNavigator(this);
        hierarchyNavigator.setScoreView(this);
        inputHandler = new ScoreInputHandler(this);
        setLayout(new BorderLayout());
        setFocusable(true);
        addKeyListener(inputHandler);
        bindings.onNotify(zoomPercent, this::refreshOverlayBounds);
    }

    /** Must be called exactly once after construction. */
    public void init() {
        setName(ComponentNames.SCORE);

        documentFontManager.installFonts(DocumentFonts.defaultFonts());

        // Initialize UI components
        initScorePanel();
        initMainPanel();
        lintCalloutController = new LintCalloutController(this);

        // The install above could not cascade — there was no main panel yet.
        documentFontManager.applyFonts();

        addMouseMotionListener(inputHandler);
        addMouseListener(inputHandler);
        addMouseWheelListener(inputHandler);

        if (SystemInfo.isMacOS) {
            PinchZoomGesture.installOn(this);
        }

        selectionChanged();
        initKeys();

        // One preview overlay for this view's lifetime; it is retargeted, never recreated.
        PreviewElementManager.installOverlay(this);
        EditModeManager.getInsertionPointMode().installOverlay();

        // Initialize insertion note with default type
        setPreviewElement(EditModeManager.makePreviewElement());

        MessageSubscription.addProcessListener(this);
        syncPlaybackPrefs();

        // Create operations and message coordinator (requires mainPanel to be set)
        var operations = new MusicEditOperations(selectionCoordinator);
        controller = new ScoreViewController(
            this,
            operations,
            selectionCoordinator,
            clipboardManager
        );

        // Last, so the controller and every other subscriber sees the first song as it sees any
        // other.
        setSong(new Song(), ChangeReport.EMPTY);
    }

    private void initKeys() {
        scoreKeyBindings.putAll(inputHandler.installKeyBindings(this));
    }


    private void initScorePanel() {
        scorePanel = new ScorePanel(this);
        scrollPane = new JScrollPane(scorePanel);
        // No lines of its own: the toolbar draws the line below itself, and the status bar the
        // line above itself. An empty border, not null, keeps the theme's scroll pane border off.
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
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
        mainPanel.setVisible(true);
        add(mainPanel, BorderLayout.CENTER);
        setupLineComponentState();
    }

    void setupLineComponentState() {
        hierarchyNavigator.setupLineComponentState(this, this);

        // The line components the overlays point at may have just been recreated.
        retargetOverlays();
    }

    @Override
    public @Nullable MainPanel getMainPanel() {
        return mainPanel;
    }

    private MainPanel requireMainPanel() {
        var result = mainPanel;

        if (result == null) {
            throw RuntimeError.exit("mainPanel not initialized");
        }

        return result;
    }

    public boolean isDragDisabled() {
        return dragDisabled;
    }

    /** Null if not found. */
    @Nullable
    public LineComponent getLineComponent(int lineIndex) {
        return hierarchyNavigator.getLineComponent(lineIndex);
    }

    /**
     * The line must be one the score draws; a caller that may hold a line without a component
     * asks {@link #getLineComponent} instead.
     */
    public LineComponent requireLineComponent(Line line) {
        var lineIndex = line.index();
        var lineComponent = getLineComponent(lineIndex);

        if (lineComponent == null) {
            throw RuntimeError.exit("no line component for line " + lineIndex);
        }

        return lineComponent;
    }

    /**
     * Returns the line's live layout, the one it is painted from, so a saved file's coordinates
     * match the score on screen.
     */
    @Override
    public @Nullable LayoutResult layoutFor(Line line) {
        var lineComponent = getLineComponent(line.index());

        if (lineComponent == null) {
            return null;
        }

        lineComponent.ensureLayout();

        return lineComponent.getLayoutResult();
    }

    public boolean openFile(File file) {
        var result = SongFileLoader.load(file);

        if (result instanceof SongLoadResult.Success success) {
            var lineWidthInches = DocumentScale.ssToInches(success.song().getLineWidthSs().value());

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

                setSong(success.song(), success.report());

                onFileOpened.accept(FileUtils.hasExtension(file, FileExtensions.SONGWRITER) ? null : file);

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
            case SongLoadResult.InvalidFileFormat e -> {
                var fileName = file.getName();
                OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_FILE_ERROR, Strings.ALERT_MUSICXML_INVALID, fileName);
                LOG.error("Refused to open '{}': invalid MusicXML ({})", fileName, e.detail());
                yield false;
            }
            case SongLoadResult.UnsupportedFileFormat e -> {
                var fileName = file.getName();
                OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_FILE_ERROR, Strings.ALERT_FILE_TYPE_UNSUPPORTED, fileName);
                LOG.error("Refused to open '{}': unsupported file type ({})", fileName, e.extension());
                yield false;
            }
            case SongLoadResult.UnsupportedVersion e -> {
                var fileName = file.getName();
                OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_LOAD_ERROR, Strings.ALERT_MUSICXML_UNSUPPORTED_VERSION, fileName);
                LOG.error("Refused to open '{}': unsupported SongScribe version '{}'", fileName, e.version());
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
     * The line overlays are siblings that deliberately paint over the score content, as in
     * {@link JLayeredPane}; any container hosting overlays must return {@code false} too. Swing
     * then repaints every child intersecting the damaged rectangle, including the
     * {@link LineComponent} beneath an overlay.
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
                if (element instanceof NoteElement note
                    && currentPreviewElement instanceof NoteElement currentNote) {
                    note.setStaffPosition(currentNote.getStaffPosition());
                }
            } else {
                EditModeManager.setPreviewElement(element);
            }
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
        var activeLineIndex = selectionCoordinator.getActiveLineIndex();
        var deselectedLine = activeLineIndex == null ? null : getLineComponent(activeLineIndex);

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
     * A tie can cross a line break, half drawn by each line, and
     * {@link SelectionCoordinator#isSelected} answers "selected" for both; the far half would
     * keep the stale color. Both lines are repainted so no caller has to work out which half
     * it holds.
     */
    public void repaintTieHalves(@Nullable HitTarget target) {
        if (!(target instanceof HitTarget.Tie(var tie))) {
            return;
        }

        repaintLine(tie.anchor().requireParentLine());
        repaintLine(tie.end().requireParentLine());
    }

    private void repaintLine(@Nullable Line line) {
        if (line == null || !getSong().contains(line)) {
            return;
        }

        var lineComponent = getLineComponent(line.index());

        if (lineComponent != null) {
            lineComponent.repaint();
        }
    }

    public void deselect() {
        clearSelection();
        repaint();
    }

    @Override
    public void takeFocus() {
        requestFocusInWindow();
    }

    @Override
    public void cancelPlacementAndDeselect() {
        var insertionPointMode = EditModeManager.getInsertionPointMode();

        if (insertionPointMode.isInProgress()) {
            insertionPointMode.cancel();
        }

        if (selectionCoordinator.isInSelectMode()) {
            deselect();
        }
    }

    @Override
    public void selectionChanged() {
        MessageCenter.post(new MusicSelectionDidChangeNotification(
            getSelectionSize(), selectionCoordinator.getSelectedTarget()));
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

        var bounds = selection.range();

        // EditLyricAction is disabled unless exactly one element is selected, and a
        // selected staff line reports itself as a selection spanning the whole line.
        // Requiring a single element keeps Return in step with the menu command rather
        // than silently picking one note out of a range the user never singled out.
        if (bounds.size() != 1) {
            return;
        }

        var line = selection.line();
        var targetIndex = LyricTargetResolver.resolveLyricTarget(line, bounds.begin());

        if (targetIndex == null) {
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
        // A title may hold one line break, and a filename may not, so the break
        // becomes a space here.
        var title = StringUtils.stripLinefeeds(theSong.getTitle());
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

    /**
     * @param report {@link ChangeReport#EMPTY} for a song no file holds
     */
    public void setSong(Song song, ChangeReport report) {
        // MBassador holds Song subscribers by weak reference, so the outgoing Song
        // keeps handling broadcast commands — spawning spurious undo steps against
        // the dead document — until GC clears it. Detach it deterministically here.
        if (this.song != null && this.song != song) {
            this.song.dispose();
        }

        this.song = song;

        // A new document gets its own chance to warn that a line does not fit; the alert is shown
        // once per document.
        LineComponent.resetOverflowWarning();

        // Reset zoom to 100% for the new/opened song before laying out at the old zoom.
        ZoomController.resetZoom();

        // Passing the song's own width leaves the model untouched — setLineWidthSs early-returns
        // on an equal value.
        updatePageLayout(song.getLineWidthSs());

        song.setModified(false);

        PlaybackController.stop();
        selectionCoordinator.clearSelection();

        // init() builds the main panel before it installs the first song.
        requireMainPanel().setSong(song);
        setupLineComponentState();

        syncPlaybackPrefs();

        // Notify all subscribers (LyricsPanel, ScoreViewController, UIActions, etc.)
        // that the song has been fully replaced. This must happen after all
        // ScoreView state is consistent. Building the notification lints the song, so every
        // subscriber to the load reads the new song's lint report.
        MessageCenter.post(new DocumentDidLoadNotification(song, report));

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
        return DocumentScale.ssToPx(getSong().getLineWidthSs().value()).sizePx();
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
     * Takes staff spaces, not pixels: a quantized pixel count would snap the model's width to a
     * {@code 1/DEFAULT_PIXELS_PER_STAFF_SPACE} grid, including on file open.
     */
    public void updatePageLayout(Ss lineWidthSs) {
        getSong().setLineWidthSs(lineWidthSs);
        relayoutPage();
    }

    /**
     * Writes nothing to the model. Only a change of line width or zoom needs this; content
     * changes re-fit the page through {@link #getPreferredSize}.
     */
    public void relayoutPage() {
        // layoutPage expects view px, so fold in the current zoom. Skipping this left the page
        // centered for the wrong width at any zoom other than 100% (content against the left
        // edge when zoomed out, clipped on the right when zoomed in).
        layoutPage(viewScale.toViewPx(getSong().getLineWidthSs()).positionPx());
    }

    /**
     * Derived on every ask, never stored through {@code setPreferredSize}: this view cannot be
     * told about every mutation that changes the content's height.
     */
    @Override
    public Dimension getPreferredSize() {
        // Sizes round up via sizePx() so content is never clipped at high zoom; margins round
        // to nearest via positionPx(), matching the border layoutPage sets from the same values.
        var contentHeightPx = (mainPanel != null) ? mainPanel.getPreferredSize().height : 0;

        return new Dimension(
            viewScale.toViewPx(PageModel.getPageWidthPx()).sizePx(),
            (int) pageHeightForContent(
                viewScale.toViewPx(PageModel.getPageHeightPx()).sizePx(),
                contentHeightPx,
                topMarginPx(),
                bottomMarginPx()
            )
        );
    }

    private static double pageHeightForContent(
        double pageHeight,
        double contentHeight,
        double topMargin,
        double bottomMargin) {

        return Math.max(pageHeight, contentHeight + topMargin + bottomMargin);
    }

    private int topMarginPx() {
        return viewScale.toViewPx(PageModel.getTopMarginPx()).positionPx();
    }

    private int bottomMarginPx() {
        return viewScale.toViewPx(PageModel.getBottomMarginPx()).positionPx();
    }

    /**
     * Does not mutate the song's stored line width, so a pure view change such as zoom never
     * records a document mutation or undo entry.
     */
    private void layoutPage(int lineWidthPx) {
        var topMarginPx = topMarginPx();
        var bottomMarginPx = bottomMarginPx();

        var horizontalMarginPx = viewScale
            .toViewPx(PageModel.getHorizontalMarginPx(viewScale.toDocPx(new ViewPx(lineWidthPx))))
            .positionPx();
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
     * A zoom change alters every pixel dimension without changing any component's state, so
     * nothing below this view is marked invalid and {@code StaffPanel} keeps its cached
     * preferred size; lines then paint at the new zoom inside bounds measured at the old one.
     */
    private static void invalidateTree(Component component) {
        component.invalidate();

        if (component instanceof Container container) {
            for (var child : container.getComponents()) {
                invalidateTree(child);
            }
        }
    }

    /**
     * Never store the result: a zoom change replaces the instance.
     */
    public ViewScale getViewScale() {
        return viewScale;
    }

    /**
     * Notifies after the zoom is applied and the view re-laid out; its value equals
     * {@code getViewScale().getZoomPercent()} wherever an observer runs.
     */
    public ObservableValue<Integer> zoomPercent() {
        return zoomPercent;
    }

    /**
     * The sole handler that performs a zoom change. Runs at
     * {@link ZoomDidChangeNotification#APPLY_PRIORITY} so the zoom is applied before other
     * handlers react.
     */
    @Handler(priority = ZoomDidChangeNotification.APPLY_PRIORITY)
    public void zoomDidChangeApplyZoom(ZoomDidChangeNotification message) {
        applyZoomPercent(message.getNewZoomPercent(), message.getAnchorPoint());
    }

    @Handler
    public void activeScriptDidChange(ActiveScriptDidChangeNotification message) {
        for (var linePanel : requireMainPanel().getStaffPanel().getLinePanels()) {
            linePanel.getLineComponent().invalidateLayout();
        }

        repaint();
    }

    @Handler
    public void linting(LintingNotification message) {
        repaint();
    }

    public LintCalloutController requireLintCalloutController() {
        var result = lintCalloutController;

        if (result == null) {
            throw RuntimeError.exit("lint callout controller not initialized");
        }

        return result;
    }

    /**
     * Overlay bounds are pixel-cached ({@link LineOverlayComponent#updateBounds()}), so without
     * this an overlay stays at its pre-zoom position until the mouse moves. The lyric editor is
     * absolutely positioned, so a zoom moves it only through here; its font is re-derived first
     * because its bounds come from that font.
     */
    private void refreshOverlayBounds() {
        forEachLineOverlay(LineOverlayComponent::updateBounds);

        if (activeLyricEditor != null) {
            activeLyricEditor.refreshFont();
            activeLyricEditor.recomputeBounds();
        }
    }

    /**
     * {@code anchorPoint} is in this view's content coordinates; when null, anchors at the
     * viewport's horizontal center and top edge.
     * <p>
     * Called only from {@link #zoomDidChangeApplyZoom}. EDT-only, per the {@code ZoomController}
     * contract.
     */
    public void applyZoomPercent(int newPercent, @Nullable Point anchorPoint) {
        var scoreScrollPane = getScoreScrollPane();

        if (scoreScrollPane == null) {
            applyZoomPercentAndRelayout(newPercent);
        } else {
            runWithoutViewportBlit(
                pane -> applyZoomPercentAndReanchor(pane, newPercent, anchorPoint)
            );
        }

        repaint();
        zoomPercent.set(viewScale.getZoomPercent());
    }

    /**
     * Leaves the scroll position alone.
     */
    private void applyZoomPercentAndRelayout(int newPercent) {
        viewScale = viewScale.withZoomPercent(newPercent);

        // Drop every cached pixel size in the score tree first: layoutPage reads
        // mainPanel's preferred size, and that read must already reflect the new zoom.
        invalidateTree(this);

        // Recompute the canvas's preferred size at the new zoom before re-layout. Go through
        // relayoutPage (not updatePageLayout) because zooming is a view-only change with no
        // business writing to the model at all — the width it lays out for is the model's own.
        relayoutPage();

        // Force synchronous re-layout so the new (post-zoom) sizes are realized before
        // anything reads them; plain revalidate() is async and would leave stale sizes.
        if (scorePanel != null) {
            scorePanel.invalidate();
        }
    }

    /**
     * Safe to nest — each level restores the mode it found.
     * <p>
     * A relayout invalidates every viewport pixel, but blit scrolling copies the stale
     * on-screen pixels to the new offset first, flashing a shifted frame of the previous
     * rendering on a large zoom jump.
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
     * The anchor is captured before {@link #applyZoomPercentAndRelayout}, while bounds still
     * describe the old zoom.
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
        applyZoomPercentAndRelayout(newPercent);

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
     * {@code anchorContentPoint} is in old-zoom content coordinates, {@code anchorViewportOffset}
     * in viewport-local coordinates.
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

    @Nullable
    public ElementSelection getSelection() {
        return selectionCoordinator.getSelection();
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

    public @Nullable LyricRenderMetrics findLyricRenderMetrics() {
        return documentFontManager.findLyricRenderMetrics();
    }

    public LyricRenderMetrics getLyricRenderMetrics() {
        return documentFontManager.getLyricRenderMetrics();
    }

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

    public DocumentFonts getDocumentFonts() {
        return documentFontManager.getFonts();
    }

    @Override
    public Font getFont(FontKey key) {
        return documentFontManager.getFont(key);
    }

    public void installDocumentFonts(DocumentFonts fonts) {
        documentFontManager.installFonts(fonts);
    }

    @Override
    public void setFonts(DocumentFonts newFonts) {
        documentFontManager.setFonts(newFonts);
    }

    /**
     * <h2>Z-order contract</h2>
     * Overlays paint above the score content and <b>below</b> the active lyric editor; an overlay
     * painting over the editor is a defect. The ordering belongs to this container, not to
     * registration order: every overlay sits directly beneath {@link #LYRIC_EDITOR_Z_ORDER},
     * and {@link LyricEditor#openOn} reclaims that index, pushing the overlays down by one.
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
     * Overlay bounds go stale whenever a line moves or resizes, by causes that share no
     * mechanism; validating the tree first makes every descendant's bounds final before the
     * overlays read them.
     * <p>
     * Only {@code setBounds}/{@code setLocation} may be called on the overlays from here, never
     * {@code revalidate()}, which would re-enter layout.
     */
    @Override
    protected void validateTree() {
        super.validateTree();

        forEachLineOverlay(LineOverlayComponent::updateBounds);
    }

    /**
     * A rebuilt layout discards the {@link LineComponent}s the overlays target, and managers
     * retarget on mouse motion only; without this the preview hides until the pointer moves.
     */
    private void retargetOverlays() {
        // retarget() ends in inkDidChange(), which recomputes the bounds itself.
        forEachLineOverlay(LineOverlayComponent::retarget);
    }

    private void forEachLineOverlay(Consumer<? super LineOverlayComponent> action) {
        for (var i = 0; i < getComponentCount(); i++) {
            if (getComponent(i) instanceof LineOverlayComponent overlay) {
                action.accept(overlay);
            }
        }
    }

}
