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

package songscribe.e2e;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JWindow;
import javax.swing.MenuElement;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.GenericTypeMatcher;
import org.assertj.swing.core.Robot;
import org.assertj.swing.edt.FailOnThreadViolationRepaintManager;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.exception.ComponentLookupException;
import org.assertj.swing.exception.UnexpectedException;
import org.assertj.swing.fixture.FrameFixture;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import songscribe.SongScribe;
import songscribe.UnitTest;
import songscribe.dom.DocumentScale;
import songscribe.dom.Song;
import songscribe.font.DocumentFonts;
import songscribe.layout.ElementHitGeometry;
import songscribe.layout.PageModel;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.BasePopupButton;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.swing.core.MouseButton.LEFT_BUTTON;

// Disambiguates from org.w3c.dom.events.MouseEvent (java.xml module)

/**
 * Base class for E2E interaction tests using AssertJ Swing.
 * <p>
 * Boots the MainFrame singleton once per test class, resets song
 * state before each test. Provides coordinate helpers for clicking on
 * notes and staff positions.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(E2ETest.ResultTracker.class)
public abstract class E2ETest {

    private static final boolean DEBUG_MODE = Boolean.getBoolean("e2e.debug");
    private static boolean slowMode = Boolean.getBoolean("e2e.slow");
    private static final boolean FAIL_FAST = Boolean.getBoolean("e2e.failFast");

    /** Base delay in ms between UI actions (before slow mode is considered). */
    private static final int BASE_ACTION_DELAY_MS = DEBUG_MODE ? 250 : 10;

    /** Corner arc radius for the status overlay background. */
    private static final int OVERLAY_ARC_PX = 4;

    /** Margin between overlay bottom and scoreView bottom when scoreView fits on screen. */
    private static final int OVERLAY_SCORE_MARGIN_PX = 8;

    /** Margin between overlay bottom and screen bottom when scoreView extends below the screen. */
    private static final int OVERLAY_SCREEN_MARGIN_PX = 13;

    private static final Object INIT_LOCK = new Object();
    private static boolean frameInitialized = false;
    private static int passCount = 0;
    private static int failCount = 0;
    @Nullable private static JWindow statusOverlay = null;
    @Nullable private static JLabel statusLabel = null;

    protected Robot robot;
    protected FrameFixture window;

    @BeforeAll
    protected void setUpOnce() {
        synchronized (INIT_LOCK) {
            initMainFrame();
        }

        robot = BasicRobot.robotWithCurrentAwtHierarchy();
        window = new FrameFixture(robot, MainFrame.getInstance());
        window.show();

        GuiActionRunner.execute(E2ETest::createStatusOverlay);
    }

    private static void initMainFrame() {
        if (!frameInitialized) {
            OptionDialogs.setSuppressDialogs(false);
            FailOnThreadViolationRepaintManager.install();
            SongScribe.logBanner("SongScribe (E2E Tests)");

            GuiActionRunner.execute(() -> {
                UIUtils.initMinimalTheme();
                UIUtils.installEagerFonts();

                // Production does this in MainFrame.main() before initFrame(). Without it
                // the provider stays at its 0.0 fallback, so every `new Song()` gets a zero
                // line width, no content can ever fit the staff, and layout raises the
                // lines-do-not-fit warning on each reset/fixture load.
                Song.setDefaultLineWidthProvider(PageModel::getDefaultLineWidthSs);

                var mainFrame = MainFrame.getInstance();
                mainFrame.initFrame();
                return mainFrame;
            });

            frameInitialized = true;
        }
    }

    protected void resetSong() {
        enterEditMode();
        deselectRestMode();

        GuiActionRunner.execute(() -> {
            var song = new Song();
            scoreView().setSong(song);
            scoreView().installDocumentFonts(DocumentFonts.defaultFonts());
        });
    }

    @AfterAll
    protected void tearDownOnce() {
        GuiActionRunner.execute(() -> {
            if (statusOverlay != null) {
                statusOverlay.dispose();
            }
        });

        window.cleanUp();
    }

    // -- Status overlay --

    public static void createStatusOverlay() {
        statusOverlay = new JWindow(MainFrame.getInstance());
        statusOverlay.setBackground(new Color(0, 0, 0, 0));
        statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Source Sans 3", Font.BOLD, 16));
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        var overlayColor = new Color(0, 0, 0, 180);
        var panel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                var g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(overlayColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), OVERLAY_ARC_PX * 2, OVERLAY_ARC_PX * 2);
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.add(statusLabel);
        statusOverlay.setContentPane(panel);
        statusOverlay.setAlwaysOnTop(true);
        statusOverlay.setVisible(true);
    }

    private static void positionOverlay() {
        var overlay = statusOverlay;
        assertThat(overlay).isNotNull();
        var scoreView = MainFrame.getInstance().getScoreView();
        assertThat(scoreView).isNotNull();
        var scoreLoc = scoreView.getLocationOnScreen();
        var scoreSize = scoreView.getSize();
        var overlaySize = overlay.getPreferredSize();
        var screenBounds = overlay.getGraphicsConfiguration().getBounds();

        int overlayY;

        if (screenBounds.height < scoreSize.height) {
            overlayY = screenBounds.y + screenBounds.height - overlaySize.height - OVERLAY_SCREEN_MARGIN_PX;
        } else {
            overlayY = scoreLoc.y + scoreSize.height - overlaySize.height - OVERLAY_SCORE_MARGIN_PX;
        }

        overlay.setLocation(
            scoreLoc.x + (scoreSize.width - overlaySize.width) / 2,
            overlayY
        );
        overlay.setSize(overlaySize);
    }

    // -- Accessors --

    protected ScoreView scoreView() {
        var scoreView = MainFrame.getInstance().getScoreView();
        assertThat(scoreView).isNotNull();

        return scoreView;
    }

    protected Song song() {
        return scoreView().getSong();
    }

    // -- Toolbar button helpers --

    /**
     * Finds a toolbar button by its component name (set from the action command).
     */
    protected AbstractButton findButtonByName(String name) {
        return robot.finder().find(new GenericTypeMatcher<>(AbstractButton.class) {
            @Override
            protected boolean isMatching(AbstractButton b) {
                return name.equals(b.getName());
            }
        });
    }

    /**
     * Clicks a toolbar button by its action command (which is also its component name).
     * <p>
     * An action whose button lives in a popup panel rather than directly on a toolbar cannot be
     * found while the popup is closed — {@code robot.finder()} matches only the realized, showing
     * component tree. When the direct lookup fails, the popup hosting the action is opened and the
     * lookup retried.
     */
    protected void clickToolbarButton(UIAction action) {
        AbstractButton button;

        try {
            button = findButtonByName(action.getActionCommand());
        } catch (ComponentLookupException e) {
            openPopupHostingAction(action, e);
            button = findButtonByName(action.getActionCommand());
        }

        robot.click(button);
        pause();
    }

    /**
     * Opens the popup of the {@link BasePopupButton} that hosts {@code action}, so that the
     * action's own button joins the showing component tree and becomes findable.
     *
     * @param lookupFailure the failure that prompted this search. It is rethrown when no popup
     *     hosts the action, so a button that is genuinely absent reports its own absence instead
     *     of being masked by a confusing secondary failure about a missing popup.
     */
    private void openPopupHostingAction(UIAction action, ComponentLookupException lookupFailure) {
        BasePopupButton popupButton;

        try {
            popupButton = robot.finder().find(new GenericTypeMatcher<>(BasePopupButton.class) {
                @Override
                protected boolean isMatching(BasePopupButton candidate) {
                    return candidate.hostsAction(action);
                }
            });
        } catch (ComponentLookupException noPopupHostsAction) {
            throw lookupFailure;
        }

        robot.click(popupButton);
        pause();
    }

    /**
     * Clicks an action's UI control — tries the toolbar button first, falls back to the menu item.
     */
    protected void clickAction(UIAction action) {
        try {
            clickToolbarButton(action);
        } catch (ComponentLookupException e) {
            clickMenuItem(action);
        }
    }

    /**
     * Clicks the mode cycle button. The button's component name is set to the
     * current mode's action command ("edit-mode" or "select-mode").
     */
    private void clickModeCycleButton() {
        var button = robot.finder().find(new GenericTypeMatcher<>(AbstractButton.class) {
            @Override
            protected boolean isMatching(AbstractButton b) {
                var name = b.getName();
                return "edit-mode".equals(name) || "select-mode".equals(name);
            }
        });
        robot.click(button);
        pause();
    }

    /**
     * Switches to edit mode by clicking the mode cycle button if not already in edit mode.
     */
    protected void enterEditMode() {
        if (Actions.CYCLE_MODE_ACTION.getCurrentAction() != Actions.EDIT_MODE_ACTION) {
            clickModeCycleButton();
        }
    }

    /**
     * Deselects rest mode by clicking the toolbar button if it is currently active.
     */
    protected static void deselectSelection() {
        GuiActionRunner.execute(() -> Actions.DESELECT_ACTION.actionPerformed(
            new ActionEvent(Actions.DESELECT_ACTION, ActionEvent.ACTION_PERFORMED, "")));
    }

    protected void deselectRestMode() {
        if (Actions.REST_ACTION.isSelected()) {
            clickToolbarButton(Actions.REST_ACTION);
        }
    }

    /**
     * Switches to select mode by clicking the mode cycle button if not already in select mode.
     */
    protected void enterSelectMode() {
        if (Actions.CYCLE_MODE_ACTION.getCurrentAction() != Actions.SELECT_MODE_ACTION) {
            clickModeCycleButton();
        }
    }

    /**
     * Clicks the toolbar button for a duration action (e.g. quarter note, eighth note).
     */
    protected void selectDuration(UIAction action) {
        clickToolbarButton(action);
    }

    /**
     * Clicks a toolbar action button (e.g. toggle beam, toggle tie, flip stem).
     */
    protected void triggerAction(UIAction action) {
        clickToolbarButton(action);
    }

    protected void enableRestMode() {
        if (!Actions.REST_ACTION.isSelected()) {
            clickToolbarButton(Actions.REST_ACTION);
        }
    }

    /**
     * Selects the given duration and inserts notes at the given staff positions on line 0.
     */
    protected void buildNotes(UIAction durationAction, int... staffPositions) {
        selectDuration(durationAction);

        for (var sp : staffPositions) {
            clickAt(insertionPoint(0, sp));
            performLayout(0);
        }
    }

    // -- Accidental helpers --

    protected UIAction[] accidentalActions() {
        return new UIAction[]{
            Actions.FLAT_ACTION, Actions.DOUBLE_FLAT_ACTION,
            Actions.NATURAL_ACTION, Actions.SHARP_ACTION, Actions.DOUBLE_SHARP_ACTION
        };
    }

    // -- Menu helpers --

    /**
     * Triggers a menu-only action by finding its {@link JMenuItem} and calling
     * {@link JMenuItem#doClick()}, bypassing AssertJ Swing's menu traversal
     * (which waits up to 10 s for the AWT event queue to idle after each menu level).
     * {@code doClick()} fires the full button-model state change — identical to a real
     * click — without opening any parent menus.
     */
    protected void clickMenuItem(UIAction action) {
        GuiActionRunner.execute(() -> {
            var item = findMenuItemForAction(null, action);

            if (item == null) {
                throw new IllegalArgumentException("No JMenuItem found for action: " + action.getName());
            }

            item.doClick();
        });
        pause();
    }

    @Nullable
    protected JMenuItem findMenuItemForAction(@Nullable MenuElement parent, UIAction action) {
        if (parent == null) {
            parent = MainFrame.getInstance().getJMenuBar();
        }

        for (var element : parent.getSubElements()) {
            if (element instanceof JMenuItem item && item.getAction() == action) {
                return item;
            }

            var found = findMenuItemForAction(element, action);

            if (found != null) {
                return found;
            }
        }

        return null;
    }

    /**
     * Returns the innermost {@link JMenu} that directly contains a {@link JMenuItem}
     * bound to {@code action}, or {@code null} if no such menu exists.
     */
    @Nullable
    protected JMenu findMenuContaining(UIAction action) {
        var item = findMenuItemForAction(null, action);

        if (item == null) {
            return null;
        }

        return (JMenu) ((JPopupMenu) item.getParent()).getInvoker();
    }

    // -- Coordinate helpers --

    /**
     * Returns the screen-relative pixel position of an existing note,
     * suitable for robot clicks.
     */
    protected Point noteScreenPosition(int lineIndex, int noteIndex) {
        var result = GuiActionRunner.execute(() -> {
            var lc = scoreView().getLineComponent(lineIndex);
            assertThat(lc).isNotNull();
            var line = lc.getLine();
            assertThat(line).isNotNull();
            var note = line.getElement(noteIndex);

            var layoutResult = lc.getLayoutResult();
            assertThat(layoutResult).isNotNull();

            var hitRect = new Rectangle2D.Double();
            ElementHitGeometry.elementHitRectSs(
                layoutResult.getElementXSs(note), note, hitRect, true);

            // The hit rect is in layout space, whose Y origin is the staff midline; a click
            // position is relative to the top of the component, so put the origin back.
            var centerYSs = hitRect.y + (hitRect.height / 2) + lc.getMiddleLineYSs();

            var locationOnScreen = lc.getLocationOnScreen();
            return new Point(
                locationOnScreen.x + DocumentScale.ssToRoundedPx(hitRect.x + hitRect.width / 2),
                locationOnScreen.y + DocumentScale.ssToRoundedPx(centerYSs)
            );
        });
        assertThat(result).isNotNull();

        return result;
    }

    /**
     * Returns a screen-relative pixel position for inserting a note at the
     * given staff position on the given line.
     * <p>
     * X is placed past the last note (or at a fixed offset if the line is empty).
     */
    protected Point insertionPoint(int lineIndex, int staffPositionSp) {
        var result = GuiActionRunner.execute(() -> {
            var lc = scoreView().getLineComponent(lineIndex);
            assertThat(lc).isNotNull();
            var line = lc.getLine();
            assertThat(line).isNotNull();

            int xPx;

            if (line.effectiveElementCount() == 0) {
                // Fixed offset from left edge for empty lines
                xPx = 80;
            } else {
                var lastNote = line.getElement(line.effectiveElementCount() - 1);
                var layoutResult = lc.getLayoutResult();
                var lastXSs = layoutResult != null ? layoutResult.getElementXSs(lastNote) : 0.0;
                // Place 30px past the last note
                xPx = (int) Math.round(DocumentScale.ssToPx(lastXSs)) + 30;
            }

            var yPx = lc.staffPositionToYPx(staffPositionSp);

            var locationOnScreen = lc.getLocationOnScreen();
            return new Point(
                locationOnScreen.x + xPx,
                locationOnScreen.y + yPx
            );
        });
        assertThat(result).isNotNull();

        return result;
    }

    /**
     * Computes a screen-coordinate insertion point just before the specified element.
     * The x coordinate is placed a few pixels to the left of the element's layout position.
     */
    protected Point insertionPointBefore(int lineIndex, int elementIndex, int staffPositionSp) {
        var result = GuiActionRunner.execute(() -> {
            var lc = scoreView().getLineComponent(lineIndex);
            assertThat(lc).isNotNull();
            var line = lc.getLine();
            assertThat(line).isNotNull();
            var layoutResult = lc.getLayoutResult();

            var element = line.getElement(elementIndex);
            var elementXSs = layoutResult != null ? layoutResult.getElementXSs(element) : 0.0;
            var xPx = (int) Math.round(DocumentScale.ssToPx(elementXSs)) - 10;

            var yPx = lc.staffPositionToYPx(staffPositionSp);

            var locationOnScreen = lc.getLocationOnScreen();
            return new Point(
                locationOnScreen.x + xPx,
                locationOnScreen.y + yPx
            );
        });
        assertThat(result).isNotNull();

        return result;
    }

    /**
     * Performs a robot drag from a note's current position to a new staff position.
     */
    protected void dragNote(int lineIndex, int noteIndex, int targetStaffPositionSp) {
        var startPoint = noteScreenPosition(lineIndex, noteIndex);

        var endPoint = GuiActionRunner.execute(() -> {
            var lc = scoreView().getLineComponent(lineIndex);
            assertThat(lc).isNotNull();
            var endYPx = lc.staffPositionToYPx(targetStaffPositionSp);
            var locationOnScreen = lc.getLocationOnScreen();
            return new Point(startPoint.x, locationOnScreen.y + endYPx);
        });
        assertThat(endPoint).isNotNull();

        robot.pressMouse(startPoint, LEFT_BUTTON);
        pause();
        robot.moveMouse(endPoint);
        pause();
        robot.releaseMouseButtons();
        pause();
    }

    // -- Click helpers --

    /**
     * Clicks at an absolute screen position using press/release.
     */
    protected void clickAt(Point screenPoint) {
        robot.pressMouse(screenPoint, LEFT_BUTTON);
        pause();
        robot.releaseMouseButtons();
        pause();
    }

    /**
     * Clicks at an absolute screen position without moving the pointer there first.
     * <p>
     * {@link #clickAt} moves the robot's pointer to the point, and in EDIT mode that
     * motion re-establishes the insertion preview. Use this when the test has
     * deliberately put the preview into a particular state and the click must not
     * disturb it.
     */
    protected void clickWithoutMovingAt(Point screenPoint) {
        dispatchSyntheticClick(screenPoint, 0);
    }

    /**
     * Shift-clicks at an absolute screen position.
     * <p>
     * Uses synthetic events because the robot does not reliably carry
     * keyboard modifiers on synthesized click events.
     */
    protected void shiftClickAt(Point screenPoint) {
        dispatchSyntheticClick(screenPoint, InputEvent.SHIFT_DOWN_MASK);
    }

    /**
     * Alt-clicks at an absolute screen position.
     * <p>
     * Uses synthetic events because the robot does not reliably carry
     * keyboard modifiers on synthesized click events.
     */
    protected void altClickAt(Point screenPoint) {
        dispatchSyntheticClick(screenPoint, InputEvent.ALT_DOWN_MASK);
    }

    /**
     * Alt-drags from one screen point to another.
     * <p>
     * Dispatches synthetic MOUSE_PRESSED at the start point, MOUSE_DRAGGED
     * at the end point, and MOUSE_RELEASED at the end point, all with the
     * alt modifier set. Uses synthetic events because the robot does not
     * reliably carry keyboard modifiers.
     */
    protected void altDrag(Point startPoint, Point endPoint) {
        GuiActionRunner.execute(() -> {
            var frame = MainFrame.getInstance();
            var frameLocation = frame.getLocationOnScreen();
            var modifiers = InputEvent.ALT_DOWN_MASK
                | InputEvent.BUTTON1_DOWN_MASK;
            var now = System.currentTimeMillis();

            // All events go to the component under the start point (drag capture)
            var component = UIUtils.getDeepestComponentAt(frame, startPoint);

            if (component == null) {
                return;
            }

            // Press at start
            var startLocal = SwingUtilities.convertPoint(frame,
                startPoint.x - frameLocation.x, startPoint.y - frameLocation.y,
                component);
            component.dispatchEvent(new MouseEvent(
                component, MouseEvent.MOUSE_PRESSED, now, modifiers,
                startLocal.x, startLocal.y, 1, false,
                MouseEvent.BUTTON1));

            // Drag to end
            var endLocal = SwingUtilities.convertPoint(frame,
                endPoint.x - frameLocation.x, endPoint.y - frameLocation.y,
                component);
            component.dispatchEvent(new MouseEvent(
                component, MouseEvent.MOUSE_DRAGGED, now, modifiers,
                endLocal.x, endLocal.y, 0, false,
                MouseEvent.BUTTON1));

            // Release at end
            component.dispatchEvent(new MouseEvent(
                component, MouseEvent.MOUSE_RELEASED, now, modifiers,
                endLocal.x, endLocal.y, 1, false,
                MouseEvent.BUTTON1));
        });

        pause();
    }

    /**
     * Dispatches synthetic MOUSE_PRESSED, MOUSE_RELEASED, and MOUSE_CLICKED
     * events with the given modifier mask at the given screen point.
     */
    private void dispatchSyntheticClick(Point screenPoint, int modifierMask) {
        GuiActionRunner.execute(() -> {
            var frame = MainFrame.getInstance();
            var frameLocation = frame.getLocationOnScreen();
            var component = UIUtils.getDeepestComponentAt(frame, screenPoint);

            if (component != null) {
                var local = SwingUtilities.convertPoint(frame,
                    screenPoint.x - frameLocation.x, screenPoint.y - frameLocation.y,
                    component);
                var modifiers = modifierMask | InputEvent.BUTTON1_DOWN_MASK;
                var now = System.currentTimeMillis();

                for (var id : new int[]{
                    MouseEvent.MOUSE_PRESSED,
                    MouseEvent.MOUSE_RELEASED,
                    MouseEvent.MOUSE_CLICKED}) {
                    component.dispatchEvent(new MouseEvent(
                        component, id, now, modifiers,
                        local.x, local.y, 1, false,
                        MouseEvent.BUTTON1));
                }
            }
        });

        pause();
    }


    // -- Key event helpers --

    /**
     * Dispatches a key press+release directly to the focused component on the EDT.
     * <p>
     * The robot's {@code pressAndReleaseKey} maps virtual key codes to physical keys,
     * which produces wrong characters on non-QWERTY keyboard layouts (e.g. Dvorak).
     * This method dispatches synthetic {@link KeyEvent}s with the logical key code,
     * bypassing the physical layout mapping.
     */
    protected void pressKey(int keyCode, int modifiers) {
        GuiActionRunner.execute(() -> {
            var target = scoreView();
            target.requestFocusInWindow();
            var now = System.currentTimeMillis();

            target.dispatchEvent(new KeyEvent(
                target, KeyEvent.KEY_PRESSED, now, modifiers,
                keyCode, KeyEvent.CHAR_UNDEFINED));
            target.dispatchEvent(new KeyEvent(
                target, KeyEvent.KEY_RELEASED, now, modifiers,
                keyCode, KeyEvent.CHAR_UNDEFINED));
        });
        pause();
    }

    // -- Layout synchronization --

    /**
     * Forces layout computation for a line by painting it immediately.
     * Call this after any model mutation before reading layout data.
     */
    protected void performLayout(int lineIndex) {
        GuiActionRunner.execute(() -> {
            var lc = scoreView().getLineComponent(lineIndex);
            assertThat(lc).isNotNull();
            lc.invalidateLayout();
            lc.paintImmediately(lc.getBounds());
        });
        pause();
    }

    /**
     * Returns the current delay in ms between UI actions, accounting for slow mode.
     */
    private static int getActionDelayMs() {
        return slowMode ? 1000 : BASE_ACTION_DELAY_MS;
    }

    /**
     * Pauses for {@link #getActionDelayMs()} so you can watch the test visually.
     */
    protected void pause() {
        var delayMs = getActionDelayMs();

        if (delayMs > 0) {
            robot.waitForIdle();

            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // -- Model query helpers --

    protected boolean isBeamed(int lineIndex, int noteIndex) {
        return Boolean.TRUE.equals(GuiActionRunner.execute(() -> {
            var line = song().getLine(lineIndex);
            return line.findBeamAt(noteIndex) != null;
        }));
    }

    protected boolean isTied(int lineIndex, int noteIndex) {
        return Boolean.TRUE.equals(GuiActionRunner.execute(() -> {
            var line = song().getLine(lineIndex);
            return line.findTieAt(noteIndex) != null;
        }));
    }

    // -- Coordinate helpers --

    /**
     * Returns the screen midpoint between two elements on a line,
     * suitable for clicking on a glissando line or inserting between notes.
     */
    protected Point midpoint(int lineIndex, int index1, int index2) {
        var p1 = noteScreenPosition(lineIndex, index1);
        var p2 = noteScreenPosition(lineIndex, index2);
        return new Point((p1.x + p2.x) / 2, (p1.y + p2.y) / 2);
    }

    // -- Fixture loading --

    /**
     * Loads a fixture file and replaces the current song.
     * The fixture is deserialized via the same path as File > Open, then set
     * on the scoreView and laid out.
     */
    protected void loadFixture(String fixtureName) throws UnexpectedException {
        var song = GuiActionRunner.execute(() -> {
            var result = UnitTest.loadFixtureResult(fixtureName);
            scoreView().setSong(result.song());
            scoreView().installDocumentFonts(result.fonts());
            return result.song();
        });
        assertThat(song).isNotNull();

        performLayout(0);
    }

    // -- Test result tracking --

    @SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod", "PackageVisibleInnerClass"})
    static class ResultTracker implements TestWatcher, ExecutionCondition, BeforeTestExecutionCallback {

        private boolean continueMode = false;

        @Override
        public void beforeTestExecution(ExtensionContext context) {
            var testIdentity = buildTestIdentity(context);
            GuiActionRunner.execute(() -> updateStatusOverlay(testIdentity));

            if (DEBUG_MODE && !continueMode) {
                showDebugDialog(testIdentity);
            }

            if (slowMode) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public void testSuccessful(ExtensionContext context) {
            passCount++;
            var testIdentity = buildTestIdentity(context);
            GuiActionRunner.execute(() -> updateStatusOverlay(testIdentity));
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            failCount++;
            var testIdentity = buildTestIdentity(context);
            GuiActionRunner.execute(() -> updateStatusOverlay(testIdentity));
        }

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            if (FAIL_FAST && failCount > 0) {
                return ConditionEvaluationResult.disabled("Fail-fast: a previous test failed");
            }

            return ConditionEvaluationResult.enabled("");
        }

        /**
         * Builds a display string like "OuterClass > NestedClass > methodName"
         * from the ExtensionContext parent chain.
         */
        private static String buildTestIdentity(ExtensionContext context) {
            var parts = new ArrayList<String>();
            var current = context;

            while (current != null) {
                var displayName = current.getDisplayName();

                // Skip the engine-level context (e.g. "JUnit Jupiter")
                if (current.getParent().isEmpty()) {
                    break;
                }

                // Strip trailing "()" from method display names
                if (displayName.endsWith("()")) {
                    displayName = displayName.substring(0, displayName.length() - 2);
                }

                parts.addFirst(displayName);
                current = current.getParent().orElse(null);
            }

            return String.join(" > ", parts);
        }

        private static void updateStatusOverlay(String testIdentity) {
            if (statusLabel == null || statusOverlay == null) {
                return;
            }

            var text = String.format(
                "<html>&nbsp;&nbsp;<font color='#4caf50'>+%d</font>&nbsp;<font color='#f44336'>-%d</font>"
                    + "&nbsp;&nbsp;&mdash;&nbsp;&nbsp;%s&nbsp;&nbsp;</html>",
                passCount,
                failCount,
                testIdentity.replace(">", "&gt;")
            );

            statusLabel.setText(text);
            statusOverlay.pack();
            positionOverlay();
        }

        private enum DebugAction { OK, CONTINUE, STOP }

        /**
         * Shows a custom 4-button debug dialog: OK / Slow / Continue / Stop.
         * OK runs the test and pauses at the next one.
         * Slow toggles slow mode on/off (dialog stays open).
         * Continue sets continueMode and dismisses.
         * Stop exits the JVM.
         */
        private void showDebugDialog(String testIdentity) {
            var result = new DebugAction[]{DebugAction.OK};
            var latch = new CountDownLatch(1);

            SwingUtilities.invokeLater(() -> {
                var dialog = new JDialog(MainFrame.getInstance(), "E2E Debug", true);
                dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

                var messageLabel = new JLabel(testIdentity);
                messageLabel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

                var okButton = new JButton("OK");
                var slowButton = new JButton(slowMode ? "Slow Off" : "Slow On");
                var continueButton = new JButton("Continue");
                var stopButton = new JButton("Stop");

                okButton.addActionListener(_ -> {
                    result[0] = DebugAction.OK;
                    dialog.dispose();
                    latch.countDown();
                });

                slowButton.addActionListener(_ -> {
                    slowMode = !slowMode;
                    slowButton.setText(slowMode ? "Slow Off" : "Slow On");
                });

                continueButton.addActionListener(_ -> {
                    result[0] = DebugAction.CONTINUE;
                    dialog.dispose();
                    latch.countDown();
                });

                stopButton.addActionListener(_ -> {
                    result[0] = DebugAction.STOP;
                    dialog.dispose();
                    latch.countDown();
                });

                var buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 13, 0));
                buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 16, 13, 16));
                buttonPanel.add(stopButton);
                buttonPanel.add(continueButton);
                buttonPanel.add(slowButton);
                buttonPanel.add(okButton);

                dialog.getRootPane().setDefaultButton(okButton);
                dialog.setLayout(new BorderLayout());
                dialog.add(messageLabel, BorderLayout.CENTER);
                dialog.add(buttonPanel, BorderLayout.SOUTH);
                dialog.pack();
                dialog.setLocationRelativeTo(MainFrame.getInstance());
                dialog.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowOpened(WindowEvent e) {
                        okButton.requestFocusInWindow();
                    }
                });
                dialog.setVisible(true);
            });

            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (result[0] == DebugAction.STOP) {
                System.exit(0);
            }

            if (result[0] == DebugAction.CONTINUE) {
                continueMode = true;
            }
        }
    }
}
