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

import javax.swing.DefaultComboBoxModel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Duration;
import songscribe.dom.ElementType;
import songscribe.prefs.Prefs;
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit tests for {@link BeatChangeDialog}: populateControls null/existing,
 * applyChange (null-guard, add vs update), and clearChange.
 */
class BeatChangeDialogTest extends MainFrameMockTest {

    private MockedStatic<UIUtils> uiUtilsMock;
    private MockedStatic<Prefs> prefsMock;
    private BeatChangeDialog dialog;

    @BeforeEach
    void setUp() {
        uiUtilsMock = mockStatic(UIUtils.class);
        prefsMock = mockStatic(Prefs.class);
        prefsMock.when(() -> Prefs.getMap(any())).thenReturn(Collections.emptyMap());
        BaseDialogTestHelper.configureMockFrame(mainFrame());
        BaseDialog.resetVisibleBlockingDialogCount();
        BaseDialog.resetSavedGeometry();
        dialog = new BeatChangeDialog(mainFrame());
    }

    @AfterEach
    void tearDown() {
        prefsMock.close();
        uiUtilsMock.close();
    }

    // ── Row 1: populateControls(null) — defaults to CROTCHET_DOTTED / CROTCHET ──

    @Test
    void testPopulateControlsNullDefaultsToCrotchetDottedAndCrotchet() {
        dialog.populateControls(null);

        assertThat(dialog.durationCombo.getSelectedItem())
            .as("durationCombo defaults to CROTCHET_DOTTED when change is null")
            .isEqualTo(Duration.CROTCHET_DOTTED);
        assertThat(dialog.beatCombo.getSelectedItem())
            .as("beatCombo defaults to CROTCHET when change is null")
            .isEqualTo(Duration.CROTCHET);
    }

    // ── Row 2: populateControls(existing) — sets both combos from BeatChange ──

    @Test
    void testPopulateControlsExistingSetsComboFromBeatChange() {
        var change = new BeatChange(Duration.MINIM, Duration.QUAVER);

        dialog.populateControls(change);

        assertThat(dialog.durationCombo.getSelectedItem())
            .as("durationCombo reflects BeatChange.duration()")
            .isEqualTo(Duration.MINIM);
        assertThat(dialog.beatCombo.getSelectedItem())
            .as("beatCombo reflects BeatChange.beat()")
            .isEqualTo(Duration.QUAVER);
    }

    // ── Row 3: applyChange — skips mutation when either combo is null ──

    @Test
    void testApplyChangeSkipsWhenDurationComboIsNull() {
        var element = ElementType.CROTCHET.newInstance();
        dialog.durationCombo.setModel(new DefaultComboBoxModel<>());

        dialog.applyChange(element);

        assertThat(element.findAttachment(BeatChangeAttachment.class))
            .as("no attachment added when durationCombo is null")
            .isNull();
    }

    @Test
    void testApplyChangeSkipsWhenBeatComboIsNull() {
        var element = ElementType.CROTCHET.newInstance();
        dialog.beatCombo.setModel(new DefaultComboBoxModel<>());

        dialog.applyChange(element);

        assertThat(element.findAttachment(BeatChangeAttachment.class))
            .as("no attachment added when beatCombo is null")
            .isNull();
    }

    // ── Row 4: applyChange — updates existing attachment vs adds new one ──

    @Test
    @SuppressWarnings("DataFlowIssue")
    void testApplyChangeUpdatesExistingAttachment() {
        var element = ElementType.CROTCHET.newInstance();
        var original = new BeatChangeAttachment(element, new BeatChange(Duration.CROTCHET, Duration.CROTCHET));
        element.addAttachment(original);

        dialog.durationCombo.setSelectedItem(Duration.MINIM);
        dialog.beatCombo.setSelectedItem(Duration.QUAVER);
        dialog.applyChange(element);

        var updated = element.findAttachment(BeatChangeAttachment.class);
        assertThat(updated)
            .as("existing attachment updated in-place, not replaced")
            .isSameAs(original);
        //noinspection ConstantValue -- needed for NullAway
        assertThat(updated == null ? null : updated.getBeatChange())
            .as("BeatChange updated to new duration and beat")
            .isEqualTo(new BeatChange(Duration.MINIM, Duration.QUAVER));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void testApplyChangeAddsNewAttachmentWhenNoneExists() {
        var element = ElementType.CROTCHET.newInstance();

        dialog.durationCombo.setSelectedItem(Duration.CROTCHET_DOTTED);
        dialog.beatCombo.setSelectedItem(Duration.CROTCHET);
        dialog.applyChange(element);

        var added = element.findAttachment(BeatChangeAttachment.class);
        assertThat(added)
            .as("new BeatChangeAttachment added when none existed")
            .isNotNull();
        //noinspection ConstantValue -- needed for NullAway
        assertThat(added == null ? null : added.getBeatChange())
            .as("new attachment has the selected duration and beat")
            .isEqualTo(new BeatChange(Duration.CROTCHET_DOTTED, Duration.CROTCHET));
    }

    // ── getExistingChange — returns BeatChange when present; null when absent ──

    @Test
    void testGetExistingChangeReturnsBeatChangeWhenAttachmentPresent() {
        var element = ElementType.CROTCHET.newInstance();
        var beatChange = new BeatChange(Duration.MINIM, Duration.CROTCHET);
        element.addAttachment(new BeatChangeAttachment(element, beatChange));

        assertThat(dialog.getExistingChange(element))
            .as("getExistingChange returns the BeatChange from the existing attachment")
            .isSameAs(beatChange);
    }

    @Test
    void testGetExistingChangeReturnsNullWhenNoAttachment() {
        var element = ElementType.CROTCHET.newInstance();

        assertThat(dialog.getExistingChange(element))
            .as("getExistingChange returns null when no BeatChangeAttachment is present")
            .isNull();
    }

    // ── Row 5: clearChange — removes attachment if present; no-op if absent ──

    @Test
    void testClearChangeRemovesExistingAttachment() {
        var element = ElementType.CROTCHET.newInstance();
        element.addAttachment(new BeatChangeAttachment(element, new BeatChange(Duration.MINIM, Duration.CROTCHET)));

        dialog.clearChange(element);

        assertThat(element.findAttachment(BeatChangeAttachment.class))
            .as("BeatChangeAttachment removed by clearChange")
            .isNull();
    }

    @Test
    void testClearChangeIsNoOpWhenNoAttachment() {
        var element = ElementType.CROTCHET.newInstance();

        dialog.clearChange(element);

        assertThat(element.findAttachment(BeatChangeAttachment.class))
            .as("no attachment present after clearChange on element with none")
            .isNull();
    }
}
