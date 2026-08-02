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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.function.Supplier;

import module java.desktop;

import org.assertj.swing.edt.GuiActionRunner;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.Strings;
import songscribe.dom.Hairpin;
import songscribe.dom.ScaleContext;
import songscribe.ui.Mode;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.renderer.HairpinRenderer;
import songscribe.hit.HitTarget;

/**
 * E2E tests for selecting, deleting and extending a hairpin through the real Swing
 * pipeline.
 *
 * <p>Every scenario here needs something a unit test cannot reach: the ordering inside
 * {@link LineComponent}'s press handler, where mode switching, lyric hits, the insertion
 * preview and note dragging all compete for one press; the action save/restore cycle
 * across a whole selection lifecycle; and the menu items re-labelling themselves as the
 * resolved hairpin state changes. The decision logic behind those states is unit-tested
 * in {@code HairpinActionStateTest} and is not re-tested here.
 *
 * <p>Tests run against the selection1.mssw fixture rather than a freshly built score:
 * inserting the first note into an empty song opens the automatic-tempo dialog, which
 * blocks the robot. The fixture is reloaded per test so a hairpin added by one test
 * cannot change the resolved state seen by the next.
 */
class HairpinSelectionTest extends E2ETest {

    // Three consecutive plain crotchets in selection1.mssw, clear of the fixture's
    // grace notes and of the note indices DynamicsMarkingTest attaches dynamics to.
    private static final int FIRST_NOTE_INDEX = 8;
    private static final int SECOND_NOTE_INDEX = 9;
    private static final int THIRD_NOTE_INDEX = 10;

    @BeforeEach
    void loadSelection1Fixture() {
        resetSong();
        loadFixture("selection1");
    }

    // =====================================================================
    // Selection
    // =====================================================================

    @Test
    void testClickInSelectModeSelectsHairpinAndDrawsItSelected() {
        addCrescendoOverFirstTwoNotes();
        var hairpin = soleHairpin();
        var hairpinPoint = hairpinScreenPosition(hairpin);

        enterSelectMode();
        deselectSelection();
        performLayout(0);
        var unselectedDistance = minSquaredDistanceToSelectionColor(renderLine());

        clickAt(hairpinPoint);
        performLayout(0);
        var selectedDistance = minSquaredDistanceToSelectionColor(renderLine());

        assertAll(
            () -> assertThat(selectedHairpin()).as("clicked hairpin is selected").isSameAs(hairpin),
            // The wedge is under a staff space thick, so antialiasing leaves no pixel at the
            // exact selection color; what the repaint has to show is ink that moved towards it.
            () -> assertThat(selectedDistance)
                .as("line repainted closer to the selection color once the hairpin is selected")
                .isLessThan(unselectedDistance)
        );
    }

    @Test
    void testEditModeClickInsertsWherePreviewShowsInsteadOfSelectingHairpin() {
        addCrescendoOverFirstTwoNotes();
        var hairpinPoint = hairpinScreenPosition(soleHairpin());

        enterEditMode();
        selectDuration(Actions.QUARTER_NOTE_ACTION);
        robot.moveMouse(hairpinPoint);
        pause();

        var elementCountBefore = elementCount();
        assertThat(previewShowing()).as("preview shows over the hairpin").isTrue();

        clickAt(hairpinPoint);
        performLayout(0);

        assertAll(
            () -> assertThat(elementCount()).as("preview click inserted an element")
                .isEqualTo(elementCountBefore + 1),
            () -> assertThat(selectedHairpin()).as("no hairpin selected").isNull()
        );
    }

    /**
     * A lyric is the only thing selectable in EDIT mode. A hairpin is not, even where no
     * insertion preview is in the way — the click simply does nothing, and selecting the hairpin
     * needs SELECT mode or an alt+click.
     */
    @Test
    void testEditModeClickDoesNotSelectHairpinEvenWithNoPreviewShowing() {
        addCrescendoOverFirstTwoNotes();
        var hairpinPoint = hairpinScreenPosition(soleHairpin());

        enterEditMode();
        selectDuration(Actions.QUARTER_NOTE_ACTION);

        // Clearing the preview outright stands in for every position where the preview is
        // suppressed (the clef/key column, the lyric band, an invalid staff position) without
        // tying the test to which of those geometries the hairpin happens to sit near. The
        // click is dispatched without pointer motion so it cannot re-establish the preview.
        GuiActionRunner.execute(LineComponent::clearPreviewElement);
        assertThat(previewShowing()).as("preview cleared").isFalse();

        var elementCountBefore = elementCount();
        clickWithoutMovingAt(hairpinPoint);
        performLayout(0);

        assertAll(
            () -> assertThat(selectedHairpin()).as("no hairpin selected in EDIT mode").isNull(),
            () -> assertThat(scoreView().getMode()).as("mode unchanged").isEqualTo(Mode.EDIT),
            () -> assertThat(elementCount()).as("nothing inserted").isEqualTo(elementCountBefore)
        );
    }

    /**
     * The alt+click escape hatch: it switches the score to SELECT mode and selects in the same
     * press, which is how a hairpin is reached without leaving EDIT mode by hand.
     */
    @Test
    void testAltClickInEditModeSelectsHairpin() {
        addCrescendoOverFirstTwoNotes();
        var hairpin = soleHairpin();

        enterEditMode();
        selectDuration(Actions.QUARTER_NOTE_ACTION);

        var elementCountBefore = elementCount();
        altClickAt(hairpinScreenPosition(hairpin));
        performLayout(0);

        assertAll(
            () -> assertThat(selectedHairpin()).as("alt+click selected the hairpin").isSameAs(hairpin),
            () -> assertThat(elementCount()).as("nothing inserted").isEqualTo(elementCountBefore)
        );
    }

    // =====================================================================
    // Delete
    // =====================================================================

    @Test
    void testDeleteRemovesSelectedHairpinAndClearsTheSelection() {
        addCrescendoOverFirstTwoNotes();
        var hairpin = soleHairpin();

        enterSelectMode();
        deselectSelection();
        clickAt(hairpinScreenPosition(hairpin));
        assertThat(selectedHairpin()).as("hairpin selected before delete").isSameAs(hairpin);

        pressKey(KeyEvent.VK_DELETE, 0);
        performLayout(0);

        assertAll(
            () -> assertThat(hairpins()).as("hairpin removed").isEmpty(),
            () -> assertThat(selectedHairpin()).as("selection cleared").isNull()
        );
    }

    // =====================================================================
    // Action state
    // =====================================================================

    @Test
    void testNoteActionsAreRestoredAfterTheHairpinSelectionClears() {
        addCrescendoOverFirstTwoNotes();
        var hairpinPoint = hairpinScreenPosition(soleHairpin());

        enterSelectMode();
        deselectSelection();
        clickAt(noteScreenPosition(0, THIRD_NOTE_INDEX));
        var enabledWithNoteSelected = isEnabled(Actions.FERMATA_ACTION);

        clickAt(hairpinPoint);
        var enabledWithHairpinSelected = isEnabled(Actions.FERMATA_ACTION);

        deselectSelection();
        clickAt(noteScreenPosition(0, THIRD_NOTE_INDEX));
        var enabledAfterReselectingNote = isEnabled(Actions.FERMATA_ACTION);

        assertAll(
            () -> assertThat(enabledWithNoteSelected)
                .as("note action enabled for a plain note selection").isTrue(),
            () -> assertThat(enabledWithHairpinSelected)
                .as("note action disabled while the hairpin is selected").isFalse(),
            // A save with no matching restore leaves the toolbar disabled for the rest of the
            // session, which nothing short of re-selecting a note afterwards can detect.
            () -> assertThat(enabledAfterReselectingNote)
                .as("note action enabled again once the hairpin selection is gone").isTrue()
        );
    }

    // =====================================================================
    // Menu round trip
    // =====================================================================

    @Test
    void testMenuAddsThenExtendsCrescendoAndDisablesTheDiminuendoItem() {
        addCrescendoOverFirstTwoNotes();
        var addedHairpin = soleHairpin();

        assertAll(
            () -> assertThat(addedHairpin.getAnchorElementIndex()).as("added span begin")
                .isEqualTo(FIRST_NOTE_INDEX),
            () -> assertThat(addedHairpin.getEndElementIndex()).as("added span end")
                .isEqualTo(SECOND_NOTE_INDEX)
        );

        clickAt(noteScreenPosition(0, THIRD_NOTE_INDEX));

        var crescendoItem = requireMenuItem(Actions.HAIRPIN_CRESCENDO_ACTION);
        var diminuendoItem = requireMenuItem(Actions.HAIRPIN_DIMINUENDO_ACTION);

        assertAll(
            () -> assertThat(crescendoItem.getText()).as("crescendo item relabelled")
                .isEqualTo(Strings.get(Strings.ACTION_HAIRPIN_CRESCENDO_EXTEND)),
            () -> assertThat(crescendoItem.isEnabled()).as("crescendo item enabled").isTrue(),
            // The opposite-type item stays in the menu so the menu does not reshuffle under
            // the pointer; only its enablement says the extend cannot apply to it.
            () -> assertThat(diminuendoItem.isVisible()).as("diminuendo item still visible").isTrue(),
            () -> assertThat(diminuendoItem.isEnabled()).as("diminuendo item disabled").isFalse(),
            () -> assertThat(diminuendoItem.getText()).as("diminuendo item still reads Add")
                .isEqualTo(Strings.get(Strings.ACTION_HAIRPIN_DIMINUENDO))
        );

        clickMenuItem(Actions.HAIRPIN_CRESCENDO_ACTION);
        performLayout(0);

        var extendedHairpin = soleHairpin();

        assertAll(
            () -> assertThat(extendedHairpin.getAnchorElementIndex()).as("extended span begin")
                .isEqualTo(FIRST_NOTE_INDEX),
            () -> assertThat(extendedHairpin.getEndElementIndex()).as("extended span end")
                .isEqualTo(THIRD_NOTE_INDEX)
        );
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /**
     * Runs {@code supplier} on the EDT and fails the test if it yields null, so callers
     * get a non-null value without a null-check at every call site.
     */
    private static <T> T onEdt(Supplier<T> supplier) {
        var result = GuiActionRunner.execute(supplier::get);

        assertThat(result).as("Expected a value from the EDT but got null").isNotNull();

        return result;
    }

    /**
     * Selects the first two notes and adds a crescendo over them through the menu.
     */
    private void addCrescendoOverFirstTwoNotes() {
        enterSelectMode();
        deselectSelection();
        clickAt(noteScreenPosition(0, FIRST_NOTE_INDEX));
        shiftClickAt(noteScreenPosition(0, SECOND_NOTE_INDEX));
        clickMenuItem(Actions.HAIRPIN_CRESCENDO_ACTION);
        performLayout(0);
    }

    private LineComponent lineComponent() {
        var lineComponent = scoreView().getLineComponent(0);

        assertThat(lineComponent).as("Line 0 has no LineComponent").isNotNull();

        return lineComponent;
    }

    private List<Hairpin> hairpins() {
        return onEdt(() -> song().getLine(0).getSpans().stream()
            .filter(Hairpin.class::isInstance)
            .map(Hairpin.class::cast)
            .toList());
    }

    /**
     * Returns the line's only hairpin, failing the test if there is not exactly one.
     */
    private Hairpin soleHairpin() {
        var hairpins = hairpins();

        if (hairpins.size() != 1) {
            throw new AssertionError("Expected exactly one hairpin but found " + hairpins.size());
        }

        return hairpins.getFirst();
    }

    private @Nullable Hairpin selectedHairpin() {
        return GuiActionRunner.execute(() -> {
            if (scoreView().getSelectionCoordinator().getSelectedTarget()
                    instanceof HitTarget.Hairpin(var hairpin)) {
                return hairpin;
            }

            return null;
        });
    }

    private int elementCount() {
        return onEdt(() -> song().getLine(0).elementCount());
    }

    private boolean previewShowing() {
        return onEdt(() -> lineComponent().hasPreviewElement());
    }

    private static boolean isEnabled(UIAction action) {
        return onEdt(action::isEnabled);
    }

    private JMenuItem requireMenuItem(UIAction action) {
        return onEdt(() -> findMenuItemForAction(null, action));
    }

    /**
     * Returns the screen position at the center of a hairpin's drawn box, failing the test
     * if the point does not in fact hit that hairpin.
     * <p>
     * The box comes from the same {@code DecorationLayout} the renderer draws from and the
     * hit test reads, so the point cannot drift from the wedge on screen.
     */
    private Point hairpinScreenPosition(Hairpin hairpin) {
        var point = onEdt(() -> {
            var lineComponent = lineComponent();
            var layoutResult = lineComponent.getLayoutResult();

            assertThat(layoutResult).as("Line 0 has no layout result").isNotNull();

            var layout = layoutResult.getDecorationLayout(hairpin);

            assertThat(layout).as("Hairpin has no decoration layout").isNotNull();

            var centerXSs = layout.xSs() + layout.widthSs() / 2;
            var centerYSs = lineComponent.getMiddleLineYSs() + layout.ySs()
                + (layout.heightSs() + layout.marginSs()) / 2;
            var locationOnScreen = lineComponent.getLocationOnScreen();

            return new Point(
                locationOnScreen.x + ScaleContext.ssToRoundedPx(centerXSs),
                locationOnScreen.y + ScaleContext.ssToRoundedPx(centerYSs)
            );
        });

        assertHitsHairpin(point, hairpin);
        return point;
    }

    /**
     * Fails the test unless {@code screenPoint} hit-tests to {@code hairpin}, so a test that
     * later clicks there reports a bad coordinate rather than a missing feature.
     */
    private void assertHitsHairpin(Point screenPoint, Hairpin hairpin) {
        var hit = GuiActionRunner.execute(() -> {
            var lineComponent = lineComponent();
            var locationOnScreen = lineComponent.getLocationOnScreen();

            return HairpinRenderer.getInstance().hitTestHairpin(
                ScaleContext.pxToSs(screenPoint.x - locationOnScreen.x),
                ScaleContext.pxToSs(screenPoint.y - locationOnScreen.y),
                song().getLine(0),
                lineComponent.getLayoutResult(),
                lineComponent.getMiddleLineYSs()
            );
        });

        assertThat(hit).as("point %s hit-tests to the hairpin", screenPoint).isSameAs(hairpin);
    }

    /**
     * Paints line 0 into an offscreen image, so a test can assert on what was actually drawn.
     */
    private BufferedImage renderLine() {
        return onEdt(() -> {
            var lineComponent = lineComponent();
            var image = new BufferedImage(
                lineComponent.getWidth(), lineComponent.getHeight(), BufferedImage.TYPE_INT_RGB);
            var g2 = image.createGraphics();
            lineComponent.paint(g2);
            g2.dispose();
            return image;
        });
    }

    /**
     * Returns the smallest squared RGB distance between any pixel of {@code image} and the
     * selection color.
     */
    private static int minSquaredDistanceToSelectionColor(BufferedImage image) {
        var selectionColor = ScoreView.getSelectionColor();
        var minDistance = Integer.MAX_VALUE;

        for (var y = 0; y < image.getHeight(); y++) {
            for (var x = 0; x < image.getWidth(); x++) {
                var pixel = new Color(image.getRGB(x, y));
                var redDelta = pixel.getRed() - selectionColor.getRed();
                var greenDelta = pixel.getGreen() - selectionColor.getGreen();
                var blueDelta = pixel.getBlue() - selectionColor.getBlue();
                var distance = redDelta * redDelta + greenDelta * greenDelta + blueDelta * blueDelta;

                if (distance < minDistance) {
                    minDistance = distance;
                }
            }
        }

        return minDistance;
    }
}
