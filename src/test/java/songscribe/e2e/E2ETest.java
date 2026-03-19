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

import static org.assertj.swing.core.MouseButton.LEFT_BUTTON;

import module java.desktop;
// Disambiguates from org.w3c.dom.events.MouseEvent (java.xml module)
import java.awt.event.MouseEvent;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Objects;
import javax.xml.parsers.SAXParserFactory;

import org.jspecify.annotations.Nullable;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.GenericTypeMatcher;
import org.assertj.swing.core.Robot;
import org.assertj.swing.edt.FailOnThreadViolationRepaintManager;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.FrameFixture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.xml.sax.InputSource;

import songscribe.UnitTest;
import songscribe.io.CompositionIO;
import songscribe.music.Composition;
import songscribe.ui.Dialogs;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.Score;
import songscribe.ui.layout.ScaleContext;
import songscribe.util.UIUtils;

/**
 * Base class for E2E interaction tests using AssertJ Swing.
 * <p>
 * Boots the MainFrame singleton once per test class, resets composition
 * state before each test. Provides coordinate helpers for clicking on
 * notes and staff positions.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(E2ETest.ResultTracker.class)
public abstract class E2ETest {

    private static final boolean DEBUG_MODE = Boolean.getBoolean("e2e.debug");
    private static final boolean SLOW_MODE = Boolean.getBoolean("e2e.slow");
    private static final boolean FAIL_FAST = Boolean.getBoolean("e2e.failFast");

    /** Delay in ms between UI actions so you can watch what's happening. Set to 0 for full speed. */
    private static final int ACTION_DELAY_MS = SLOW_MODE ? 1000 : DEBUG_MODE ? 250 : 10;

    private static final String[] DEBUG_OPTIONS = {"OK", "Skip", "Stop"};
    private static final int DEBUG_OK = 0;
    private static final int DEBUG_SKIP = 1;
    private static final int DEBUG_STOP = 2;

    private static boolean frameInitialized = false;
    private static boolean skipClass = false;
    private static int testCounter = 0;
    private static int passCount = 0;
    private static int failCount = 0;
    @Nullable private static JWindow statusOverlay;
    @Nullable private static JLabel statusLabel;
    private String currentClassName;
    private String currentTestName;

    protected Robot robot;
    protected FrameFixture window;

    @BeforeAll
    protected void setUpOnce() {
        Dialogs.setSuppressDialogs(false);
        FailOnThreadViolationRepaintManager.install();
        robot = BasicRobot.robotWithCurrentAwtHierarchy();

        if (!frameInitialized) {
            GuiActionRunner.execute(() -> {
                UIUtils.initLaf();

                var mainFrame = MainFrame.getInstance();
                mainFrame.initFrame();
                return mainFrame;
            });

            frameInitialized = true;
        }

        window = new FrameFixture(robot, MainFrame.getInstance());
        window.show();

        GuiActionRunner.execute(this::createStatusOverlay);

        skipClass = false;
        debugConfirm("About to run: " + getClass().getSimpleName());
    }

    @BeforeEach
    protected void resetComposition(TestInfo testInfo) {
        Assumptions.assumeFalse(skipClass, "Skipping class");

        testCounter++;
        var rawClassName = getClass().getSimpleName().replaceAll("Test$", "");
        var classWords = rawClassName.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase();
        currentClassName = Character.toUpperCase(classWords.charAt(0)) + classWords.substring(1);
        var rawName = testInfo.getDisplayName().replaceAll("\\(\\)$", "");
        var camel = (rawName.startsWith("test") && rawName.length() > 4)
            ? Character.toLowerCase(rawName.charAt(4)) + rawName.substring(5)
            : rawName;
        var words = camel.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase();
        currentTestName = Character.toUpperCase(words.charAt(0)) + words.substring(1);

        enterEditMode();
        deselectRestMode();

        GuiActionRunner.execute(() -> {
            var composition = new Composition();
            score().setComposition(composition);
            updateStatusOverlay();
        });

        pause();
        debugConfirm("Test " + testCounter + " of " + E2ETestCountListener.getTotalTests() + ": " + currentTestName);
        Assumptions.assumeFalse(skipClass, "Skipping class");
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

    private void createStatusOverlay() {
        statusOverlay = new JWindow(MainFrame.getInstance());
        statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Source Sans 3", Font.BOLD, 16));
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        var panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(0, 0, 0, 180));
        panel.add(statusLabel);
        statusOverlay.setContentPane(panel);
        statusOverlay.setAlwaysOnTop(true);
        statusOverlay.setVisible(true);
    }

    private void updateStatusOverlay() {
        var sb = new StringBuilder("  ");
        sb.append(testCounter).append(" of ").append(E2ETestCountListener.getTotalTests());

        if (passCount > 0 || failCount > 0) {
            sb.append(" [+").append(passCount).append("/-").append(failCount).append("]");
        }

        sb.append("  —  ").append(currentClassName).append("  |  ").append(currentTestName).append("  ");
        Objects.requireNonNull(statusLabel).setText(sb.toString());
        Objects.requireNonNull(statusOverlay).pack();
        positionOverlay();
    }

    private void positionOverlay() {
        var overlay = Objects.requireNonNull(statusOverlay);
        var score = score();
        var scoreLoc = score.getLocationOnScreen();
        var scoreSize = score.getSize();
        var overlaySize = overlay.getPreferredSize();
        overlay.setLocation(
            scoreLoc.x + (scoreSize.width - overlaySize.width) / 2,
            scoreLoc.y + scoreSize.height - overlaySize.height - 8
        );
        overlay.setSize(overlaySize);
    }

    // -- Accessors --

    protected Score score() {
        return Objects.requireNonNull(MainFrame.getInstance().getScore());
    }

    protected Composition composition() {
        return score().getComposition();
    }

    // -- Toolbar button helpers --

    /**
     * Finds a toolbar button by its component name (set from the action command).
     */
    protected AbstractButton findButtonByName(String name) {
        return robot.finder().find(new GenericTypeMatcher<AbstractButton>(AbstractButton.class) {
            @Override
            protected boolean isMatching(AbstractButton b) {
                return name.equals(b.getName());
            }
        });
    }

    /**
     * Clicks a toolbar button by its action command (which is also its component name).
     */
    protected void clickToolbarButton(UIAction action) {
        var button = findButtonByName(action.getActionCommand());
        robot.click(button);
        pause();
    }

    /**
     * Clicks the mode cycle button. The button's component name is set to the
     * current mode's action command ("edit-mode" or "select-mode").
     */
    private void clickModeCycleButton() {
        var button = robot.finder().find(new GenericTypeMatcher<AbstractButton>(AbstractButton.class) {
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

    /**
     * Clicks the rest mode toggle button.
     */
    protected void enableRestMode() {
        clickToolbarButton(Actions.REST_ACTION);
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
            Actions.FLAT_ACTION, Actions.DOUBLE_FLAT_ACTION, Actions.NATURAL_FLAT_ACTION,
            Actions.NATURAL_ACTION, Actions.SHARP_ACTION, Actions.DOUBLE_SHARP_ACTION,
            Actions.NATURAL_SHARP_ACTION
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
            var menuBar = MainFrame.getInstance().getJMenuBar();
            var item = findMenuItemForAction(menuBar, action);

            if (item == null) {
                throw new IllegalArgumentException("No JMenuItem found for action: " + action.getName());
            }

            item.doClick();
        });
        pause();
    }

    @Nullable
    private JMenuItem findMenuItemForAction(MenuElement parent, UIAction action) {
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

    // -- Coordinate helpers --

    /**
     * Returns the screen-relative pixel position of an existing note,
     * suitable for robot clicks.
     */
    protected Point noteScreenPosition(int lineIndex, int noteIndex) {
        return Objects.requireNonNull(GuiActionRunner.execute(() -> {
            var lc = Objects.requireNonNull(score().getLineComponent(lineIndex));
            var line = Objects.requireNonNull(lc.getLine());
            var note = line.getElement(noteIndex);

            var layoutResult = lc.getLayoutResult();
            var noteXSs = layoutResult != null ? layoutResult.getElementXSs(note) : 0.0;
            var noteXPx = (int) Math.round(ScaleContext.getInstance().toPixels(noteXSs));
            var noteYPx = lc.staffPositionToYPx(note.getStaffPosition());

            var locationOnScreen = lc.getLocationOnScreen();
            return new Point(
                locationOnScreen.x + noteXPx + (int) Math.round(ScaleContext.getInstance().toPixels(note.getType().getNoteheadCenterXSs())),
                locationOnScreen.y + noteYPx
            );
        }));
    }

    /**
     * Returns a screen-relative pixel position for inserting a note at the
     * given staff position on the given line.
     * <p>
     * X is placed past the last note (or at a fixed offset if the line is empty).
     */
    protected Point insertionPoint(int lineIndex, int staffPositionSp) {
        return Objects.requireNonNull(GuiActionRunner.execute(() -> {
            var lc = Objects.requireNonNull(score().getLineComponent(lineIndex));
            var line = Objects.requireNonNull(lc.getLine());

            int xPx;

            if (line.elementCount() == 0) {
                // Fixed offset from left edge for empty lines
                xPx = 80;
            } else {
                var lastNote = line.getElement(line.elementCount() - 1);
                var layoutResult = lc.getLayoutResult();
                var lastXSs = layoutResult != null ? layoutResult.getElementXSs(lastNote) : 0.0;
                // Place 30px past the last note
                xPx = (int) Math.round(ScaleContext.getInstance().toPixels(lastXSs)) + 30;
            }

            var yPx = lc.staffPositionToYPx(staffPositionSp);

            var locationOnScreen = lc.getLocationOnScreen();
            return new Point(
                locationOnScreen.x + xPx,
                locationOnScreen.y + yPx
            );
        }));
    }

    /**
     * Computes a screen-coordinate insertion point just before the specified element.
     * The x coordinate is placed a few pixels to the left of the element's layout position.
     */
    protected Point insertionPointBefore(int lineIndex, int elementIndex, int staffPositionSp) {
        return Objects.requireNonNull(GuiActionRunner.execute(() -> {
            var lc = Objects.requireNonNull(score().getLineComponent(lineIndex));
            var line = Objects.requireNonNull(lc.getLine());
            var layoutResult = lc.getLayoutResult();

            var element = line.getElement(elementIndex);
            var elementXSs = layoutResult != null ? layoutResult.getElementXSs(element) : 0.0;
            int xPx = (int) Math.round(ScaleContext.getInstance().toPixels(elementXSs)) - 10;

            var yPx = lc.staffPositionToYPx(staffPositionSp);

            var locationOnScreen = lc.getLocationOnScreen();
            return new Point(
                locationOnScreen.x + xPx,
                locationOnScreen.y + yPx
            );
        }));
    }

    /**
     * Performs a robot drag from a note's current position to a new staff position.
     */
    protected void dragNote(int lineIndex, int noteIndex, int targetStaffPositionSp) {
        var startPoint = noteScreenPosition(lineIndex, noteIndex);

        var endPoint = Objects.requireNonNull(GuiActionRunner.execute(() -> {
            var lc = Objects.requireNonNull(score().getLineComponent(lineIndex));
            var endYPx = lc.staffPositionToYPx(targetStaffPositionSp);
            var locationOnScreen = lc.getLocationOnScreen();
            return new Point(startPoint.x, locationOnScreen.y + endYPx);
        }));

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
     * Shift-clicks at an absolute screen position.
     * <p>
     * Uses synthetic events because the robot does not reliably carry
     * keyboard modifiers on synthesized click events.
     */
    protected void shiftClickAt(Point screenPoint) {
        dispatchSyntheticClick(screenPoint, java.awt.event.InputEvent.SHIFT_DOWN_MASK);
    }

    /**
     * Alt-clicks at an absolute screen position.
     * <p>
     * Uses synthetic events because the robot does not reliably carry
     * keyboard modifiers on synthesized click events.
     */
    protected void altClickAt(Point screenPoint) {
        dispatchSyntheticClick(screenPoint, java.awt.event.InputEvent.ALT_DOWN_MASK);
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
            int modifiers = java.awt.event.InputEvent.ALT_DOWN_MASK
                | java.awt.event.InputEvent.BUTTON1_DOWN_MASK;
            long now = System.currentTimeMillis();

            // All events go to the component under the start point (drag capture)
            var component = UIUtils.getDeepestComponentAt(frame, startPoint);

            if (component == null) {
                return;
            }

            // Press at start
            var startLocal = SwingUtilities.convertPoint(frame,
                startPoint.x - frameLocation.x, startPoint.y - frameLocation.y,
                component);
            component.dispatchEvent(new java.awt.event.MouseEvent(
                component, java.awt.event.MouseEvent.MOUSE_PRESSED, now, modifiers,
                startLocal.x, startLocal.y, 1, false,
                java.awt.event.MouseEvent.BUTTON1));

            // Drag to end
            var endLocal = SwingUtilities.convertPoint(frame,
                endPoint.x - frameLocation.x, endPoint.y - frameLocation.y,
                component);
            component.dispatchEvent(new java.awt.event.MouseEvent(
                component, java.awt.event.MouseEvent.MOUSE_DRAGGED, now, modifiers,
                endLocal.x, endLocal.y, 0, false,
                java.awt.event.MouseEvent.BUTTON1));

            // Release at end
            component.dispatchEvent(new java.awt.event.MouseEvent(
                component, java.awt.event.MouseEvent.MOUSE_RELEASED, now, modifiers,
                endLocal.x, endLocal.y, 1, false,
                java.awt.event.MouseEvent.BUTTON1));
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
                int modifiers = modifierMask | java.awt.event.InputEvent.BUTTON1_DOWN_MASK;
                long now = System.currentTimeMillis();

                for (int id : new int[]{
                    java.awt.event.MouseEvent.MOUSE_PRESSED,
                    java.awt.event.MouseEvent.MOUSE_RELEASED,
                    java.awt.event.MouseEvent.MOUSE_CLICKED}) {
                    component.dispatchEvent(new java.awt.event.MouseEvent(
                        component, id, now, modifiers,
                        local.x, local.y, 1, false,
                        java.awt.event.MouseEvent.BUTTON1));
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
            var target = score();
            target.requestFocusInWindow();
            long now = System.currentTimeMillis();

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
            var lc = Objects.requireNonNull(score().getLineComponent(lineIndex));
            lc.invalidateLayout();
            lc.paintImmediately(lc.getBounds());
        });
        pause();
    }

    /**
     * Pauses for {@link #ACTION_DELAY_MS} so you can watch the test visually.
     */
    protected void pause() {
        if (ACTION_DELAY_MS > 0) {
            robot.waitForIdle();

            try {
                Thread.sleep(ACTION_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Functional interface for test steps that may throw checked exceptions.
     */
    @FunctionalInterface
    protected interface TestStep {
        void run(int step) throws Exception;
    }

    /**
     * Executes a logical step within a test. Updates the status overlay,
     * then in debug mode shows a confirm dialog: OK runs the step, Skip
     * skips it and continues to the next step, Stop exits the JVM.
     * In slow mode, adds extra delay before the step.
     *
     * @param stepNumber auto-incremented step number (pass {@code ++step})
     * @param description short label for the step (shown in overlay and dialog)
     * @param step the action and assertions to run
     */
    protected void debugStep(int stepNumber, String description, TestStep step) throws Exception {
        currentTestName = stepNumber + ": " + description;
        GuiActionRunner.execute(() -> updateStatusOverlay());

        if (SLOW_MODE && !DEBUG_MODE) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (DEBUG_MODE) {
            int result = showDebugConfirm(description);

            if (result == DEBUG_STOP) {
                System.exit(0);
            }

            if (result == DEBUG_SKIP) {
                return;
            }

            // Let the debug dialog fully dispose before running the step
            pause();
        }

        step.run(stepNumber);
    }

    /**
     * In debug mode, shows a confirm dialog with OK/Skip/Stop buttons.
     * Stop exits the JVM. Skip sets {@code skipClass = true}.
     */
    private void debugConfirm(String message) {
        if (!DEBUG_MODE) {
            return;
        }

        int result = showDebugConfirm(message);

        if (result == DEBUG_STOP) {
            System.exit(0);
        }

        if (result == DEBUG_SKIP) {
            skipClass = true;
        }
    }

    /**
     * Shows a confirm dialog with OK/Skip/Stop buttons and returns the chosen option.
     */
    private int showDebugConfirm(String message) {
        var result = new int[1];
        var latch = new java.util.concurrent.CountDownLatch(1);

        SwingUtilities.invokeLater(() -> {
            result[0] = Dialogs.showOptionDialog(
                MainFrame.getInstance(),
                "E2E Debug",
                message,
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                DEBUG_OPTIONS,
                DEBUG_OPTIONS[DEBUG_OK]
            );
            latch.countDown();
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return result[0];
    }

    // -- Model query helpers --

    protected boolean isBeamed(int lineIndex, int noteIndex) {
        return Boolean.TRUE.equals(GuiActionRunner.execute(() -> {
            var line = composition().getLine(lineIndex);
            return line.getBeamings().findInterval(noteIndex) != null;
        }));
    }

    protected boolean isTied(int lineIndex, int noteIndex) {
        return Boolean.TRUE.equals(GuiActionRunner.execute(() -> {
            var line = composition().getLine(lineIndex);
            return line.getTies().findInterval(noteIndex) != null;
        }));
    }

    // -- Fixture loading --

    /**
     * Loads a fixture file and replaces the current composition.
     * The fixture is deserialized via the same path as File > Open, then set
     * on the score and laid out.
     */
    protected Composition loadFixture(String fixtureName) throws Exception {
        var composition = Objects.requireNonNull(GuiActionRunner.execute(() -> {
            var loaded = UnitTest.loadFixture(fixtureName);
            score().setComposition(loaded);
            return loaded;
        }));
        performLayout(0);
        return composition;
    }

    // -- Save/load round-trip --

    protected Composition roundTrip(Composition original) throws Exception {
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        CompositionIO.writeComposition(original, pw);
        pw.flush();
        var xml = sw.toString();

        var factory = SAXParserFactory.newInstance();
        var parser = factory.newSAXParser();
        var reader = new CompositionIO.DocumentReader();
        parser.parse(new InputSource(new StringReader(xml)), reader);

        return reader.getComposition();
    }

    // -- Test result tracking --

    static class ResultTracker implements TestWatcher, ExecutionCondition {

        @Override
        public void testSuccessful(ExtensionContext context) {
            passCount++;
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            failCount++;
        }

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            if (FAIL_FAST && failCount > 0) {
                return ConditionEvaluationResult.disabled("Fail-fast: a previous test failed");
            }

            return ConditionEvaluationResult.enabled("");
        }
    }
}
