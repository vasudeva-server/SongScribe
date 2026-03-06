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

import java.awt.*;

import javax.swing.*;

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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;

import songscribe.io.CompositionIO;
import songscribe.music.Composition;
import songscribe.music.Note;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.Score;
import songscribe.ui.layout2.ScaleContext;
import songscribe.util.UIUtils;

import static org.assertj.swing.core.MouseButton.LEFT_BUTTON;

/**
 * Base class for E2E interaction tests using AssertJ Swing.
 * <p>
 * Boots the MainFrame singleton once per test class, resets composition
 * state before each test. Provides coordinate helpers for clicking on
 * notes and staff positions.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(BaseSwingTest.ResultTracker.class)
abstract class BaseSwingTest {

    private static final boolean DEBUG_MODE = Boolean.getBoolean("e2e.debug");

    /** Delay in ms between UI actions so you can watch what's happening. Set to 0 for full speed. */
    private static final int ACTION_DELAY_MS = DEBUG_MODE ? 250 : 100;

    private static final String[] DEBUG_OPTIONS = {"Stop", "Skip", "OK"};
    private static final int DEBUG_STOP = 0;
    private static final int DEBUG_SKIP = 1;
    private static final int DEBUG_OK = 2;

    private static boolean frameInitialized = false;
    private static boolean skipClass = false;
    private static int testCounter = 0;
    private static int passCount = 0;
    private static int failCount = 0;
    private static final int TOTAL_E2E_TESTS = 37;

    private static JWindow statusOverlay;
    private static JLabel statusLabel;
    private String currentTestName;

    protected Robot robot;
    protected FrameFixture window;

    @BeforeAll
    void setUpOnce() {
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
    void resetComposition(TestInfo testInfo) {
        Assumptions.assumeFalse(skipClass, "Skipping class");

        testCounter++;
        currentTestName = testInfo.getDisplayName().replaceAll("\\(\\)$", "");

        deselectRestMode();

        GuiActionRunner.execute(() -> {
            var composition = new Composition(MainFrame.getInstance());
            score().setComposition(composition);
            updateStatusOverlay();
        });

        pause();
        debugConfirm("Test " + testCounter + " of " + TOTAL_E2E_TESTS + ": " + currentTestName);
    }

    @AfterAll
    void tearDownOnce() {
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
        sb.append(testCounter).append(" of ").append(TOTAL_E2E_TESTS);

        if (passCount > 0 || failCount > 0) {
            sb.append(" [+").append(passCount).append("/-").append(failCount).append("]");
        }

        sb.append("  —  ").append(currentTestName).append("  ");
        statusLabel.setText(sb.toString());
        statusOverlay.pack();
        positionOverlay();
    }

    private void positionOverlay() {
        var score = score();
        var scoreLoc = score.getLocationOnScreen();
        var scoreSize = score.getSize();
        var overlaySize = statusOverlay.getPreferredSize();
        statusOverlay.setLocation(
            scoreLoc.x + (scoreSize.width - overlaySize.width) / 2,
            scoreLoc.y + scoreSize.height - overlaySize.height - 8
        );
        statusOverlay.setSize(overlaySize);
    }


    // -- Accessors --

    protected Score score() {
        return MainFrame.getInstance().getScore();
    }

    protected Composition composition() {
        return score().getComposition();
    }


    // -- Toolbar button helpers --

    /**
     * Finds a toolbar button by its component name (set from the action command).
     */
    private AbstractButton findButtonByName(String name) {
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


    // -- Coordinate helpers --

    /**
     * Returns the screen-relative pixel position of an existing note,
     * suitable for robot clicks.
     */
    protected Point noteScreenPosition(int lineIndex, int noteIndex) {
        return GuiActionRunner.execute(() -> {
            var lc = score().getLineComponent(lineIndex);
            var line = lc.getLine();
            var note = line.getNote(noteIndex);

            var layoutResult = lc.getLayoutResult();
            var noteXSs = layoutResult != null ? layoutResult.getNoteXSs(note) : 0.0;
            var noteXPx = (int) Math.round(ScaleContext.getInstance().toPixels(noteXSs));
            var noteYPx = lc.staffPositionToYPx(note.getStaffPosition());

            var locationOnScreen = lc.getLocationOnScreen();
            return new Point(
                locationOnScreen.x + noteXPx + Note.HOT_SPOT.x,
                locationOnScreen.y + noteYPx
            );
        });
    }

    /**
     * Returns a screen-relative pixel position for inserting a note at the
     * given staff position on the given line.
     * <p>
     * X is placed past the last note (or at a fixed offset if the line is empty).
     */
    protected Point insertionPoint(int lineIndex, int staffPositionSp) {
        return GuiActionRunner.execute(() -> {
            var lc = score().getLineComponent(lineIndex);
            var line = lc.getLine();

            int xPx;

            if (line.noteCount() == 0) {
                // Fixed offset from left edge for empty lines
                xPx = 80;
            } else {
                var lastNote = line.getNote(line.noteCount() - 1);
                var layoutResult = lc.getLayoutResult();
                var lastXSs = layoutResult != null ? layoutResult.getNoteXSs(lastNote) : 0.0;
                // Place 30px past the last note
                xPx = (int) Math.round(ScaleContext.getInstance().toPixels(lastXSs)) + 30;
            }

            var yPx = lc.staffPositionToYPx(staffPositionSp);

            var locationOnScreen = lc.getLocationOnScreen();
            return new Point(
                locationOnScreen.x + xPx,
                locationOnScreen.y + yPx
            );
        });
    }

    /**
     * Performs a robot drag from a note's current position to a new staff position.
     */
    protected void dragNote(int lineIndex, int noteIndex, int targetStaffPositionSp) {
        var startPoint = noteScreenPosition(lineIndex, noteIndex);

        var endPoint = GuiActionRunner.execute(() -> {
            var lc = score().getLineComponent(lineIndex);
            var endYPx = lc.staffPositionToYPx(targetStaffPositionSp);
            var locationOnScreen = lc.getLocationOnScreen();
            return new Point(startPoint.x, locationOnScreen.y + endYPx);
        });

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
     * Dispatches synthetic MOUSE_PRESSED, MOUSE_RELEASED, and MOUSE_CLICKED
     * events with the shift modifier set. The robot is not used because it
     * does not reliably carry keyboard modifiers on synthesized click events.
     */
    protected void shiftClickAt(Point screenPoint) {
        GuiActionRunner.execute(() -> {
            var frame = MainFrame.getInstance();
            var frameLocation = frame.getLocationOnScreen();
            int relX = screenPoint.x - frameLocation.x;
            int relY = screenPoint.y - frameLocation.y;

            var component = SwingUtilities.getDeepestComponentAt(frame, relX, relY);

            if (component != null) {
                var local = SwingUtilities.convertPoint(frame, relX, relY, component);
                int modifiers = java.awt.event.InputEvent.SHIFT_DOWN_MASK
                        | java.awt.event.InputEvent.BUTTON1_DOWN_MASK;
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


    // -- Layout synchronization --

    /**
     * Forces layout computation for a line by painting it immediately.
     * Call this after any model mutation before reading layout data.
     */
    protected void performLayout(int lineIndex) {
        GuiActionRunner.execute(() -> {
            var lc = score().getLineComponent(lineIndex);
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
     * In debug mode, shows a confirm dialog with Stop/Skip/OK buttons.
     * Stop aborts the entire test run, Skip skips the remaining tests in the current class.
     */
    private void debugConfirm(String message) {
        if (!DEBUG_MODE) {
            return;
        }

        var result = new int[1];
        var latch = new java.util.concurrent.CountDownLatch(1);

        SwingUtilities.invokeLater(() -> {
            result[0] = JOptionPane.showOptionDialog(
                MainFrame.getInstance(),
                message,
                "E2E Debug",
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

        switch (result[0]) {
            case DEBUG_STOP -> System.exit(0);
            case DEBUG_SKIP -> {
                skipClass = true;
                Assumptions.assumeFalse(true, "Skipping class");
            }
        }
    }


    // -- Model query helpers --

    protected boolean isBeamed(int lineIndex, int noteIndex) {
        return GuiActionRunner.execute(() -> {
            var line = composition().getLine(lineIndex);
            return line.getBeamings().findInterval(noteIndex) != null;
        });
    }

    protected boolean isTied(int lineIndex, int noteIndex) {
        return GuiActionRunner.execute(() -> {
            var line = composition().getLine(lineIndex);
            return line.getTies().findInterval(noteIndex) != null;
        });
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
        var reader = new CompositionIO.DocumentReader(MainFrame.getInstance());
        parser.parse(new InputSource(new StringReader(xml)), reader);

        return reader.getComposition();
    }


    // -- Test result tracking --

    static class ResultTracker implements TestWatcher {

        @Override
        public void testSuccessful(ExtensionContext context) {
            passCount++;
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            failCount++;
        }
    }
}
