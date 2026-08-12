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

import java.util.Collections;
import java.util.stream.Stream;

import java.awt.Component;

import javax.swing.JRadioButton;
import javax.swing.text.JTextComponent;

import org.jspecify.annotations.Nullable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.dom.Annotation;
import songscribe.prefs.Prefs;
import songscribe.ui.dialog.backend.AnnotationBackEnd;
import songscribe.ui.dialog.backend.AttachmentTarget;
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static songscribe.dom.StaffElementFactory.crotchet;

/**
 * Exercises the contract of {@link AnnotationDialog}, which after the dialog seam is entirely about
 * controls: the text combo, the three alignment radios and the two placement radios.
 *
 * <p><b>Populating</b> — {@link AnnotationDialog#populateControls} maps an annotation onto the
 * radios. Both radio groups are enumerated: every alignment the dialog can show is asserted to
 * select its own radio, and both placements likewise, driven from
 * {@link Annotation.Placement#values()} so a new placement reaches this test on its own. The
 * alignment table names its rows explicitly, because the alignments are {@code float} constants on
 * {@code Component} rather than a type with a value list — the fall-through row is what pins the
 * contract's "left when it is neither of the other two".
 *
 * <p><b>Round trip</b> — the invariant that populating and then gathering returns an equivalent
 * {@link Annotation}, covering text, alignment and placement in one property rather than one
 * expected output per control.
 *
 * <p><b>Never blank</b> — the one way the user could reach a blank annotation, which is emptying
 * the field. The guard on the combo's editor puts the previous text back, so the commit never sees
 * it. The other direction cannot happen at all: {@link Annotation} refuses blank text outright,
 * and {@code AnnotationTest} asserts that.
 *
 * <p><b>Not tested here:</b> commit and removal, which are the back end's; and the Add/Modify
 * labelling, which the template owns for the whole family and {@code AttachmentDialogTest} asserts
 * once.
 */
class AnnotationDialogTest extends MainFrameMockTest {

    private MockedStatic<UIUtils> uiUtilsMock;
    private MockedStatic<Prefs> prefsMock;
    private AnnotationDialog dialog;

    @BeforeEach
    void setUp() {
        uiUtilsMock = mockStatic(UIUtils.class);
        prefsMock = mockStatic(Prefs.class);
        prefsMock.when(() -> Prefs.getMap(any())).thenReturn(Collections.emptyMap());
        BaseDialogTestHelper.configureMockFrame(mainFrame());
        BaseDialog.resetVisibleBlockingDialogCount();
        BaseDialog.resetSavedGeometry();

        var line = detachedLine();
        var element = crotchet();
        line.addElement(element);
        dialog = new AnnotationDialog(mainFrame(), new AnnotationBackEnd(new AttachmentTarget(line, element)));
    }

    @AfterEach
    void tearDown() {
        prefsMock.close();
        uiUtilsMock.close();
    }

    @Test
    void testPopulateControlsWithNoExistingAnnotationShowsTheDefault() {
        dialog.populateControls(null);

        assertThat(dialog.annotationCombo.getSelectedItem())
            .as("the combo starts at the default annotation text")
            .isEqualTo(AnnotationDialog.DEFAULT_ANNOTATION);
        assertThat(dialog.leftRadio.isSelected())
            .as("a new annotation starts left-aligned")
            .isTrue();
        assertThat(dialog.aboveRadio.isSelected())
            .as("a new annotation starts above the staff")
            .isTrue();
    }

    private record AlignmentCase(String description, float alignment) {}

    static Stream<AlignmentCase> alignmentCases() {
        return Stream.of(
            new AlignmentCase("centered", Component.CENTER_ALIGNMENT),
            new AlignmentCase("right-aligned", Component.RIGHT_ALIGNMENT),
            new AlignmentCase("left-aligned", Component.LEFT_ALIGNMENT),
            // Anything that is neither center nor right reads as left, which is the clause a
            // value outside the three constants is here to pin.
            new AlignmentCase("an alignment that is none of the three reads as left", 0.25f)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("alignmentCases")
    void testPopulateControlsSelectsTheRadioForTheAnnotationsAlignment(AlignmentCase testCase) {
        dialog.populateControls(new Annotation("dolce", testCase.alignment()));

        assertThat(selectedAlignmentRadio())
            .as("exactly the alignment radio the annotation calls for is selected")
            .isSameAs(radioFor(testCase.alignment()));
    }

    @ParameterizedTest
    @MethodSource("placements")
    void testPopulateControlsSelectsTheRadioForTheAnnotationsPlacement(Annotation.Placement placement) {
        var annotation = new Annotation("dolce");
        annotation.setPlacement(placement);

        dialog.populateControls(annotation);

        assertThat(placement == Annotation.Placement.ABOVE
            ? dialog.aboveRadio.isSelected()
            : dialog.belowRadio.isSelected())
            .as("the placement radio matching the annotation is the one selected")
            .isTrue();
    }

    static Stream<Annotation.Placement> placements() {
        return Stream.of(Annotation.Placement.values());
    }

    static Stream<Annotation> roundTripAnnotations() {
        return Stream.of(
            annotation("dolce", Component.CENTER_ALIGNMENT, Annotation.Placement.BELOW),
            annotation("cresc.", Component.RIGHT_ALIGNMENT, Annotation.Placement.ABOVE),
            annotation("fine", Component.LEFT_ALIGNMENT, Annotation.Placement.ABOVE)
        );
    }

    @ParameterizedTest
    @MethodSource("roundTripAnnotations")
    void testGatherChangeReturnsWhatPopulateControlsPutIn(Annotation original) {
        dialog.populateControls(original);

        var gathered = dialog.gatherChange();

        assertThat(gathered.getAnnotation())
            .as("the text survives the round trip")
            .isEqualTo(original.getAnnotation());
        assertThat(gathered.getXAlignment())
            .as("the alignment survives the round trip")
            .isEqualTo(original.getXAlignment());
        assertThat(gathered.getPlacement())
            .as("the placement survives the round trip")
            .isEqualTo(original.getPlacement());
    }

    @Test
    void testEmptyingTheTextPutsItBackSoNothingBlankCanBeGathered() {
        dialog.populateControls(new Annotation("dolce"));
        var editor = (JTextComponent) dialog.annotationCombo.getEditor().getEditorComponent();

        editor.setText("");
        var yielded = editor.getInputVerifier().shouldYieldFocus(editor, dialog.okButton);

        assertThat(editor.getText())
            .as("emptying the field puts text back, so OK can never commit a blank annotation")
            .isNotBlank();
        assertThat(yielded)
            .as("the caret is released rather than trapped in a field the user meant to leave")
            .isTrue();
    }

    private static Annotation annotation(String text, float alignment, Annotation.Placement placement) {
        var annotation = new Annotation(text, alignment);
        annotation.setPlacement(placement);

        return annotation;
    }

    private JRadioButton radioFor(float alignment) {
        if (alignment == Component.CENTER_ALIGNMENT) {
            return dialog.centerRadio;
        }

        if (alignment == Component.RIGHT_ALIGNMENT) {
            return dialog.rightRadio;
        }

        return dialog.leftRadio;
    }

    private @Nullable JRadioButton selectedAlignmentRadio() {
        for (var radio : new JRadioButton[] { dialog.leftRadio, dialog.centerRadio, dialog.rightRadio }) {
            if (radio.isSelected()) {
                return radio;
            }
        }

        return null;
    }
}
