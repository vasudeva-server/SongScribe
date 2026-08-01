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

import java.awt.Component;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.ElementType;
import songscribe.prefs.Prefs;
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit tests for {@link AnnotationDialog}: populateControls null/existing,
 * applyChange (empty text, alignment+position, add vs update), and clearChange.
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
        dialog = new AnnotationDialog(mainFrame());
    }

    @AfterEach
    void tearDown() {
        prefsMock.close();
        uiUtilsMock.close();
    }

    // ── Row 1: populateControls(null) — defaults to DEFAULT_ANNOTATION, left, above ──

    @Test
    void testPopulateControlsNullDefaultsToDefaultAnnotationLeftAlignmentAbove() {
        dialog.populateControls(null);

        assertThat(dialog.annotationCombo.getSelectedItem())
            .as("combo text defaults to DEFAULT_ANNOTATION")
            .isEqualTo(AnnotationDialog.DEFAULT_ANNOTATION);
        assertThat(dialog.leftRadio.isSelected())
            .as("left alignment selected by default")
            .isTrue();
        assertThat(dialog.centerRadio.isSelected())
            .as("center alignment not selected by default")
            .isFalse();
        assertThat(dialog.rightRadio.isSelected())
            .as("right alignment not selected by default")
            .isFalse();
        assertThat(dialog.aboveRadio.isSelected())
            .as("above position selected by default")
            .isTrue();
        assertThat(dialog.belowRadio.isSelected())
            .as("below position not selected by default")
            .isFalse();
    }

    // ── Row 2: populateControls(existing) — alignment and placement mapping ──

    @Test
    void testPopulateControlsExistingMapsCenterAlignmentToCenterRadio() {
        var annotation = new Annotation("dolce", Component.CENTER_ALIGNMENT);
        annotation.setPlacement(Annotation.Placement.BELOW);

        dialog.populateControls(annotation);

        assertThat(dialog.annotationCombo.getSelectedItem())
            .as("combo shows existing annotation text")
            .isEqualTo("dolce");
        assertThat(dialog.centerRadio.isSelected())
            .as("CENTER_ALIGNMENT maps to centerRadio")
            .isTrue();
        assertThat(dialog.belowRadio.isSelected())
            .as("BELOW placement maps to belowRadio")
            .isTrue();
    }

    @Test
    void testPopulateControlsExistingMapsRightAlignmentToRightRadio() {
        var annotation = new Annotation("cresc.", Component.RIGHT_ALIGNMENT);
        annotation.setPlacement(Annotation.Placement.ABOVE);

        dialog.populateControls(annotation);

        assertThat(dialog.rightRadio.isSelected())
            .as("RIGHT_ALIGNMENT maps to rightRadio")
            .isTrue();
        assertThat(dialog.aboveRadio.isSelected())
            .as("ABOVE placement maps to aboveRadio")
            .isTrue();
    }

    @Test
    void testPopulateControlsExistingMapsOtherAlignmentToLeftRadio() {
        // Any alignment that is neither CENTER nor RIGHT falls through to leftRadio
        var annotation = new Annotation("fine", Component.LEFT_ALIGNMENT);
        annotation.setPlacement(Annotation.Placement.ABOVE);

        dialog.populateControls(annotation);

        assertThat(dialog.leftRadio.isSelected())
            .as("LEFT_ALIGNMENT (other) maps to leftRadio")
            .isTrue();
    }

    // ── Row 3: applyChange — empty/null text removes existing attachment ──

    @Test
    void testApplyChangeEmptyTextRemovesExistingAttachment() {
        var element = ElementType.CROTCHET.newInstance();
        var attachment = new AnnotationAttachment(element, new Annotation("old"));
        element.addAttachment(attachment);

        dialog.annotationCombo.setSelectedItem("");
        dialog.applyChange(element);

        assertThat(element.findAttachment(AnnotationAttachment.class))
            .as("existing attachment removed when text is empty")
            .isNull();
    }

    @Test
    void testApplyChangeNullTextIsNoOpWhenNoAttachment() {
        var element = ElementType.CROTCHET.newInstance();

        dialog.annotationCombo.setSelectedItem(null);
        // no attachment present — should not throw
        dialog.applyChange(element);

        assertThat(element.findAttachment(AnnotationAttachment.class))
            .as("no attachment added when text is null and none existed")
            .isNull();
    }

    // ── Row 4: applyChange — builds Annotation with correct alignment float and placement ──

    @Test
    void testApplyChangeCenterRadioSelectedWritesCenterAlignment() {
        var element = ElementType.CROTCHET.newInstance();

        dialog.annotationCombo.setSelectedItem("test");
        dialog.centerRadio.setSelected(true);
        dialog.aboveRadio.setSelected(true);
        dialog.applyChange(element);

        var added = element.findAttachment(AnnotationAttachment.class);
        assertThat(added).as("attachment was added").isNotNull();
        assertThat(added.getAnnotation().getXAlignment())
            .as("CENTER_ALIGNMENT stored in annotation")
            .isEqualTo(Component.CENTER_ALIGNMENT);
        assertThat(added.getAnnotation().getPlacement())
            .as("ABOVE stored in annotation")
            .isEqualTo(Annotation.Placement.ABOVE);
    }

    @Test
    void testApplyChangeRightRadioSelectedWritesRightAlignment() {
        var element = ElementType.CROTCHET.newInstance();

        dialog.annotationCombo.setSelectedItem("test");
        dialog.rightRadio.setSelected(true);
        dialog.belowRadio.setSelected(true);
        dialog.applyChange(element);

        var added = element.findAttachment(AnnotationAttachment.class);
        assertThat(added).as("attachment was added").isNotNull();
        assertThat(added.getAnnotation().getXAlignment())
            .as("RIGHT_ALIGNMENT stored in annotation")
            .isEqualTo(Component.RIGHT_ALIGNMENT);
        assertThat(added.getAnnotation().getPlacement())
            .as("BELOW stored in annotation")
            .isEqualTo(Annotation.Placement.BELOW);
    }

    @Test
    void testApplyChangeLeftRadioSelectedWritesLeftAlignment() {
        var element = ElementType.CROTCHET.newInstance();

        dialog.annotationCombo.setSelectedItem("dolce");
        dialog.leftRadio.setSelected(true);
        dialog.aboveRadio.setSelected(true);
        dialog.applyChange(element);

        var added = element.findAttachment(AnnotationAttachment.class);
        assertThat(added).as("attachment was added").isNotNull();
        assertThat(added.getAnnotation().getXAlignment())
            .as("LEFT_ALIGNMENT stored in annotation")
            .isEqualTo(Component.LEFT_ALIGNMENT);
        assertThat(added.getAnnotation().getAnnotation())
            .as("annotation text preserved")
            .isEqualTo("dolce");
    }

    // ── Row 5: applyChange — updates existing attachment vs adds new one ──

    @Test
    void testApplyChangeUpdatesExistingAttachment() {
        var element = ElementType.CROTCHET.newInstance();
        var original = new AnnotationAttachment(element, new Annotation("old"));
        element.addAttachment(original);

        dialog.annotationCombo.setSelectedItem("new");
        dialog.leftRadio.setSelected(true);
        dialog.aboveRadio.setSelected(true);
        dialog.applyChange(element);

        // Still exactly one attachment (updated in-place)
        var updated = element.findAttachment(AnnotationAttachment.class);
        assertThat(updated).isNotNull();
        assertThat(updated)
            .as("existing attachment updated, not replaced")
            .isSameAs(original);
        assertThat(updated.getAnnotation().getAnnotation())
            .as("annotation text updated to new value")
            .isEqualTo("new");
    }

    @Test
    void testApplyChangeAddsNewAttachmentWhenNoneExists() {
        var element = ElementType.CROTCHET.newInstance();
        // no pre-existing attachment

        dialog.annotationCombo.setSelectedItem("fine");
        dialog.leftRadio.setSelected(true);
        dialog.aboveRadio.setSelected(true);
        dialog.applyChange(element);

        var added = element.findAttachment(AnnotationAttachment.class);
        assertThat(added)
            .as("new AnnotationAttachment added when none existed")
            .isNotNull();
        assertThat(added.getAnnotation().getAnnotation())
            .as("annotation text set on new attachment")
            .isEqualTo("fine");
    }

    // ── Row 6: clearChange — removes attachment if present; no-op if absent ──

    @Test
    void testClearChangeRemovesExistingAttachment() {
        var element = ElementType.CROTCHET.newInstance();
        element.addAttachment(new AnnotationAttachment(element, new Annotation("fine")));

        dialog.clearChange(element);

        assertThat(element.findAttachment(AnnotationAttachment.class))
            .as("attachment removed by clearChange")
            .isNull();
    }

    @Test
    void testClearChangeIsNoOpWhenNoAttachment() {
        var element = ElementType.CROTCHET.newInstance();
        // no attachment — clearChange should not throw

        dialog.clearChange(element);

        assertThat(element.findAttachment(AnnotationAttachment.class))
            .as("no attachment present after clearChange on element with none")
            .isNull();
    }

    // ── getExistingChange — returns annotation when present; null when absent ──

    @Test
    void testGetExistingChangeReturnsAnnotationWhenAttachmentPresent() {
        var element = ElementType.CROTCHET.newInstance();
        var annotation = new Annotation("forte");
        element.addAttachment(new AnnotationAttachment(element, annotation));

        assertThat(dialog.getExistingChange(element))
            .as("getExistingChange returns the annotation from the existing attachment")
            .isSameAs(annotation);
    }

    @Test
    void testGetExistingChangeReturnsNullWhenNoAttachment() {
        var element = ElementType.CROTCHET.newInstance();

        assertThat(dialog.getExistingChange(element))
            .as("getExistingChange returns null when no AnnotationAttachment is present")
            .isNull();
    }
}
