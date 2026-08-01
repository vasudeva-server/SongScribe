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
package songscribe.ui.dialog;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.export.PageLayoutData;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.util.GraphicUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.AdditionalAnswers.answerVoid;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * Tests for {@link PaperSizeStep}: unit conversion, template parsing,
 * template selection, and data commitment via {@link PaperSizeStep#end()}.
 */
class PaperSizeStepTest extends UnitTest {

    // A convenient single-inch value used for unit-conversion checks
    private static final double ONE_INCH = 1.0;

    private PageLayoutData pageLayoutData;
    private PaperSizeStep step;
    private MockedStatic<Prefs> prefsMock;

    @BeforeEach
    void setUp() {
        prefsMock = mockStatic(Prefs.class);
        // Default: non-metric (imperial)
        prefsMock.when(() -> Prefs.getBoolean(PrefsKey.METRIC)).thenReturn(false);
        // Suppress writes to the real prefs system
        prefsMock.when(() -> Prefs.put(any(PrefsKey.class), any(boolean.class)))
            .then(answerVoid((PrefsKey key, Boolean value) -> { }));

        pageLayoutData = new PageLayoutData();
        step = new PaperSizeStep(pageLayoutData);
    }

    @AfterEach
    void tearDown() {
        prefsMock.close();
    }

    // -- getValueInPixels --

    @Test
    void testGetValueInPixelsConvertsInchesToPixelsUsingCurrentUnit() {
        // After construction currentUnit is INCH
        var model = step.widthSpinnerModel;
        model.setValue(ONE_INCH);

        var expected = GraphicUtils.Unit.INCH.convertToPixels(ONE_INCH);
        assertThat(step.getValueInPixels(model))
            .as("getValueInPixels with unit=INCH and value=1.0 must return %d px", expected)
            .isEqualTo(expected);
    }

    // -- Unit switch: INCH → CM --

    @Test
    void testUnitSwitchInchToCmScalesAllSpinnersBy25_4() {
        // Prime each spinner to ONE_INCH so scaling is easy to verify
        step.widthSpinnerModel.setValue(ONE_INCH);
        step.heightSpinnerModel.setValue(ONE_INCH);
        step.leftInnerSpinnerModel.setValue(ONE_INCH);
        step.rightOuterSpinnerModel.setValue(ONE_INCH);
        step.topSpinnerModel.setValue(ONE_INCH);
        step.bottomSpinnerModel.setValue(ONE_INCH);

        // Selecting CM index triggers UnitAction (getValue() maps to combo index)
        step.unitsCombo.setSelectedIndex(GraphicUtils.Unit.CM.getValue());

        var expected = ONE_INCH * PaperSizeStep.MM_PER_IN;
        assertThat((Double) step.widthSpinnerModel.getValue())
            .as("width after INCH→CM switch")
            .isCloseTo(expected, offset(1e-9));
        assertThat((Double) step.heightSpinnerModel.getValue())
            .as("height after INCH→CM switch")
            .isCloseTo(expected, offset(1e-9));
        assertThat((Double) step.leftInnerSpinnerModel.getValue())
            .as("leftInner after INCH→CM switch")
            .isCloseTo(expected, offset(1e-9));
        assertThat((Double) step.rightOuterSpinnerModel.getValue())
            .as("rightOuter after INCH→CM switch")
            .isCloseTo(expected, offset(1e-9));
        assertThat((Double) step.topSpinnerModel.getValue())
            .as("top after INCH→CM switch")
            .isCloseTo(expected, offset(1e-9));
        assertThat((Double) step.bottomSpinnerModel.getValue())
            .as("bottom after INCH→CM switch")
            .isCloseTo(expected, offset(1e-9));
    }

    // -- Unit switch: CM → INCH --

    @Test
    void testUnitSwitchCmToInchScalesAllSpinnersByInverseOf25_4() {
        // First switch to CM so currentUnit becomes CM (getValue() maps to combo index)
        step.unitsCombo.setSelectedIndex(GraphicUtils.Unit.CM.getValue());

        // Prime spinners to the expected MM equivalent of ONE_INCH
        var cmValue = ONE_INCH * PaperSizeStep.MM_PER_IN;
        step.widthSpinnerModel.setValue(cmValue);
        step.heightSpinnerModel.setValue(cmValue);
        step.leftInnerSpinnerModel.setValue(cmValue);
        step.rightOuterSpinnerModel.setValue(cmValue);
        step.topSpinnerModel.setValue(cmValue);
        step.bottomSpinnerModel.setValue(cmValue);

        // Switch back to INCH (getValue() maps to combo index)
        step.unitsCombo.setSelectedIndex(GraphicUtils.Unit.INCH.getValue());

        var expected = cmValue / PaperSizeStep.MM_PER_IN;
        assertThat((Double) step.widthSpinnerModel.getValue())
            .as("width after CM→INCH switch")
            .isCloseTo(expected, offset(1e-9));
        assertThat((Double) step.heightSpinnerModel.getValue())
            .as("height after CM→INCH switch")
            .isCloseTo(expected, offset(1e-9));
        assertThat((Double) step.leftInnerSpinnerModel.getValue())
            .as("leftInner after CM→INCH switch")
            .isCloseTo(expected, offset(1e-9));
        assertThat((Double) step.rightOuterSpinnerModel.getValue())
            .as("rightOuter after CM→INCH switch")
            .isCloseTo(expected, offset(1e-9));
        assertThat((Double) step.topSpinnerModel.getValue())
            .as("top after CM→INCH switch")
            .isCloseTo(expected, offset(1e-9));
        assertThat((Double) step.bottomSpinnerModel.getValue())
            .as("bottom after CM→INCH switch")
            .isCloseTo(expected, offset(1e-9));
    }

    // -- TemplateObject parsing --

    @Nested
    class TemplateObjectParsing {

        @Test
        void testFullLineAssignsAllFields() {
            var to = new PaperSizeStep.TemplateObject("A4;210;297;20;mm;metric");

            assertThat(to.name).as("name").isEqualTo("A4");
            assertThat(to.width).as("width").isEqualTo(210.0);
            assertThat(to.height).as("height").isEqualTo(297.0);
            assertThat(to.margin).as("margin").isEqualTo(20.0);
            assertThat(to.unit).as("unit").isEqualTo("mm");
            assertThat(to.metric).as("metric flag").isTrue();
        }

        @Test
        void testPartialLineFewerThanSixFieldsUsesDefaults() {
            // Only name — all other fields fall back to defaults
            var to = new PaperSizeStep.TemplateObject("Custom");

            assertThat(to.name).as("name").isEqualTo("Custom");
            assertThat(to.width).as("width default").isEqualTo(0.0);
            assertThat(to.height).as("height default").isEqualTo(0.0);
            assertThat(to.margin).as("margin default").isEqualTo(0.0);
            assertThat(to.unit).as("unit default").isEmpty();
            assertThat(to.metric).as("metric default").isFalse();
        }
    }

    // -- Template selection populates spinners --

    @Test
    void testTemplateSelectionPopulatesAllSixSpinners() {
        // Select the A4 template via the template combo to fire TemplateAction
        var a4 = step.templates.getElementAt(0); // A4 is the first template loaded from the file
        step.templateCombo.setSelectedItem(a4);

        assertThat((Double) step.widthSpinnerModel.getValue())
            .as("width after A4 template selection")
            .isEqualTo(a4.width);
        assertThat((Double) step.heightSpinnerModel.getValue())
            .as("height after A4 template selection")
            .isEqualTo(a4.height);
        // All four margins are set to the template's margin
        assertThat((Double) step.leftInnerSpinnerModel.getValue())
            .as("leftInner after A4 template selection")
            .isEqualTo(a4.margin);
        assertThat((Double) step.rightOuterSpinnerModel.getValue())
            .as("rightOuter after A4 template selection")
            .isEqualTo(a4.margin);
        assertThat((Double) step.topSpinnerModel.getValue())
            .as("top after A4 template selection")
            .isEqualTo(a4.margin);
        assertThat((Double) step.bottomSpinnerModel.getValue())
            .as("bottom after A4 template selection")
            .isEqualTo(a4.margin);
    }

    // -- setValues() round-trip --

    @Test
    void testSetValuesConvertsPixelValuesToCurrentUnitForDisplay() {
        // Prefs.getBoolean(METRIC) → false → unit is INCH
        var widthPx = GraphicUtils.Unit.INCH.convertToPixels(8.5);
        var heightPx = GraphicUtils.Unit.INCH.convertToPixels(11.0);
        var marginPx = GraphicUtils.Unit.INCH.convertToPixels(0.75);

        step.setValues(widthPx, heightPx, marginPx, marginPx, marginPx, marginPx, false);

        var expectedWidth = GraphicUtils.convertFromPixels(widthPx, GraphicUtils.Unit.INCH);
        var expectedHeight = GraphicUtils.convertFromPixels(heightPx, GraphicUtils.Unit.INCH);
        var expectedMargin = GraphicUtils.convertFromPixels(marginPx, GraphicUtils.Unit.INCH);

        assertThat((Double) step.widthSpinnerModel.getValue())
            .as("widthSpinner after setValues in INCH")
            .isCloseTo(expectedWidth, offset(1e-9));
        assertThat((Double) step.heightSpinnerModel.getValue())
            .as("heightSpinner after setValues in INCH")
            .isCloseTo(expectedHeight, offset(1e-9));
        assertThat((Double) step.leftInnerSpinnerModel.getValue())
            .as("leftInnerSpinner after setValues in INCH")
            .isCloseTo(expectedMargin, offset(1e-9));
        assertThat((Double) step.rightOuterSpinnerModel.getValue())
            .as("rightOuterSpinner after setValues in INCH")
            .isCloseTo(expectedMargin, offset(1e-9));
        assertThat((Double) step.topSpinnerModel.getValue())
            .as("topSpinner after setValues in INCH")
            .isCloseTo(expectedMargin, offset(1e-9));
        assertThat((Double) step.bottomSpinnerModel.getValue())
            .as("bottomSpinner after setValues in INCH")
            .isCloseTo(expectedMargin, offset(1e-9));
    }

    // -- MirroredAction: labels switch --

    @Test
    void testMirroredActionSwitchesLabelsBetweenLeftRightAndInnerOuter() {
        // Initially mirroredCheck is deselected: labels should be Left/Right
        assertThat(step.leftInnerLabel.getText())
            .as("leftInnerLabel when not mirrored")
            .isEqualTo(Strings.get(Strings.DIALOG_PAPER_SIZE_LEFT));
        assertThat(step.rightOuterLabel.getText())
            .as("rightOuterLabel when not mirrored")
            .isEqualTo(Strings.get(Strings.DIALOG_PAPER_SIZE_RIGHT));

        // Select mirrored via doClick(): toggles state and fires all action listeners
        step.mirroredCheck.doClick();

        assertThat(step.leftInnerLabel.getText())
            .as("leftInnerLabel when mirrored")
            .isEqualTo(Strings.get(Strings.DIALOG_PAPER_SIZE_INNER));
        assertThat(step.rightOuterLabel.getText())
            .as("rightOuterLabel when mirrored")
            .isEqualTo(Strings.get(Strings.DIALOG_PAPER_SIZE_OUTER));

        // Deselect mirrored via doClick(): toggles back and fires all action listeners
        step.mirroredCheck.doClick();

        assertThat(step.leftInnerLabel.getText())
            .as("leftInnerLabel after deselecting mirrored")
            .isEqualTo(Strings.get(Strings.DIALOG_PAPER_SIZE_LEFT));
        assertThat(step.rightOuterLabel.getText())
            .as("rightOuterLabel after deselecting mirrored")
            .isEqualTo(Strings.get(Strings.DIALOG_PAPER_SIZE_RIGHT));
    }

    // -- start() selects first template matching metric pref --

    @Test
    void testStartSelectsFirstTemplateMatchingMetricPref() {
        // Prefs returns false (imperial) — start() should select US-Letter (first non-metric template)
        step.start();

        var selected = (PaperSizeStep.TemplateObject) step.templates.getSelectedItem();
        assertThat(selected).as("selected template").isNotNull();
        assertThat(selected.metric)
            .as("selected template must be non-metric when METRIC pref is false")
            .isFalse();
        assertThat(selected.name)
            .as("first imperial template is US-Letter")
            .isEqualTo("US-Letter");
    }

    // -- end() commits to pageLayoutData --

    @Test
    void testEndWritesAllSixPixelValuesAndMirroredFlagToPageLayoutData() {
        // Prime each spinner with a known inch value and ensure currentUnit is INCH
        step.currentUnit = GraphicUtils.Unit.INCH;
        step.widthSpinnerModel.setValue(8.5);
        step.heightSpinnerModel.setValue(11.0);
        step.leftInnerSpinnerModel.setValue(0.75);
        step.rightOuterSpinnerModel.setValue(0.75);
        step.topSpinnerModel.setValue(0.5);
        step.bottomSpinnerModel.setValue(0.5);
        step.mirroredCheck.setSelected(true);

        step.end();

        assertThat(pageLayoutData.paperWidthPx)
            .as("paperWidthPx")
            .isEqualTo(GraphicUtils.Unit.INCH.convertToPixels(8.5));
        assertThat(pageLayoutData.paperHeightPx)
            .as("paperHeightPx")
            .isEqualTo(GraphicUtils.Unit.INCH.convertToPixels(11.0));
        assertThat(pageLayoutData.leftInnerMarginPx)
            .as("leftInnerMarginPx")
            .isEqualTo(GraphicUtils.Unit.INCH.convertToPixels(0.75));
        assertThat(pageLayoutData.rightOuterMarginPx)
            .as("rightOuterMarginPx")
            .isEqualTo(GraphicUtils.Unit.INCH.convertToPixels(0.75));
        assertThat(pageLayoutData.topMarginPx)
            .as("topMarginPx")
            .isEqualTo(GraphicUtils.Unit.INCH.convertToPixels(0.5));
        assertThat(pageLayoutData.bottomMarginPx)
            .as("bottomMarginPx")
            .isEqualTo(GraphicUtils.Unit.INCH.convertToPixels(0.5));
        assertThat(pageLayoutData.mirrored)
            .as("mirrored flag")
            .isTrue();
    }
}
