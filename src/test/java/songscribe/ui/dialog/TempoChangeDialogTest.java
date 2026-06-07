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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.dom.Duration;
import songscribe.dom.ElementType;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.prefs.Prefs;
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit tests for {@link TempoChangeDialog}: populateControls null/existing
 * and applyChange (Tempo construction + show-flag inversion, add vs update).
 */
class TempoChangeDialogTest extends MainFrameMockTest {

    private static final int DEFAULT_BPM = 120;
    private static final String DEFAULT_DESCRIPTION = "Moderate";

    private MockedStatic<UIUtils> uiUtilsMock;
    private MockedStatic<Prefs> prefsMock;
    private TempoChangeDialog dialog;

    @BeforeEach
    void setUp() {
        uiUtilsMock = mockStatic(UIUtils.class);
        prefsMock = mockStatic(Prefs.class);
        prefsMock.when(() -> Prefs.getMap(any())).thenReturn(Collections.emptyMap());
        BaseDialogTestHelper.configureMockFrame(mainFrame());
        BaseDialog.resetVisibleBlockingDialogCount();
        BaseDialog.resetSavedGeometry();
        dialog = new TempoChangeDialog();
    }

    @AfterEach
    void tearDown() {
        prefsMock.close();
        uiUtilsMock.close();
    }

    // ── Row 25: populateControls(null) — default Tempo values ──

    @Test
    void testPopulateControlsNullSetsBpm120CrotchetModerateShowTempo() {
        dialog.populateControls(null);

        assertThat(dialog.tempoSection.getVisibleTempo())
            .as("default BPM is 120")
            .isEqualTo(DEFAULT_BPM);
        assertThat(dialog.tempoSection.getTempoType())
            .as("default tempo type is CROTCHET")
            .isEqualTo(Duration.CROTCHET);
        assertThat(dialog.tempoSection.getTempoDescription())
            .as("default description is Moderate")
            .isEqualTo(DEFAULT_DESCRIPTION);
        assertThat(dialog.tempoSection.isShowOnlyDescription())
            .as("showTempo=true means showOnlyDescription=false")
            .isFalse();
    }

    // ── Row 26: populateControls(existing) — forwards attachment's Tempo to section ──

    @Test
    void testPopulateControlsExistingForwardsTempoToSection() {
        var element = ElementType.CROTCHET.newInstance();
        var tempo = new Tempo(96, Duration.QUAVER, "Allegretto", false);
        var attachment = new TempoChangeAttachment(element, tempo);

        dialog.populateControls(attachment);

        assertThat(dialog.tempoSection.getVisibleTempo())
            .as("BPM forwarded from existing attachment")
            .isEqualTo(96);
        assertThat(dialog.tempoSection.getTempoType())
            .as("tempo type forwarded from existing attachment")
            .isEqualTo(Duration.QUAVER);
        assertThat(dialog.tempoSection.getTempoDescription())
            .as("description forwarded from existing attachment")
            .isEqualTo("Allegretto");
        // shouldShowTempo=false → showOnlyDescription=true
        assertThat(dialog.tempoSection.isShowOnlyDescription())
            .as("showOnlyDescription reflects !shouldShowTempo from existing attachment")
            .isTrue();
    }

    // ── Row 27: applyChange — Tempo built from section getters; showTempo = !isShowOnlyDescription() ──

    @Test
    @SuppressWarnings("DataFlowIssue")
    void testApplyChangeBuildsTempoCombiningShowFlagInversion() {
        var element = ElementType.CROTCHET.newInstance();

        // Configure section: 80 BPM, MINIM, "Largo", showOnlyDescription=false → showTempo=true
        dialog.tempoSection.setTempo(new Tempo(80, Duration.MINIM, "Largo", true));
        dialog.applyChange(element);

        var added = element.findAttachment(TempoChangeAttachment.class);
        assertThat(added)
            .as("TempoChangeAttachment was added")
            .isNotNull();

        //noinspection ConstantValue -- NullAway guard
        var tempo = added == null ? null : added.getTempo();
        assertThat(tempo == null ? 0 : tempo.getVisibleTempo())
            .as("BPM written from section")
            .isEqualTo(80);
        assertThat(tempo == null ? null : tempo.getTempoType())
            .as("tempo type written from section")
            .isEqualTo(Duration.MINIM);
        assertThat(tempo == null ? null : tempo.getTempoDescription())
            .as("description written from section")
            .isEqualTo("Largo");
        // isShowOnlyDescription()=false → !false = true → shouldShowTempo=true
        assertThat(tempo == null ? false : tempo.shouldShowTempo())
            .as("showTempo is the inverse of isShowOnlyDescription")
            .isTrue();
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void testApplyChangeShowTempoFalseWhenShowOnlyDescriptionTrue() {
        var element = ElementType.CROTCHET.newInstance();

        // shouldShowTempo=false → section's showOnlyDescription=true → showTempo=false
        dialog.tempoSection.setTempo(new Tempo(100, Duration.CROTCHET, "Andante", false));
        dialog.applyChange(element);

        var added = element.findAttachment(TempoChangeAttachment.class);
        assertThat(added).as("attachment was added").isNotNull();
        //noinspection ConstantValue -- NullAway guard
        assertThat(added == null ? true : added.getTempo().shouldShowTempo())
            .as("showTempo=false when isShowOnlyDescription=true")
            .isFalse();
    }

    // ── Row 28: applyChange — updates existing attachment vs adds new one ──

    @Test
    @SuppressWarnings("DataFlowIssue")
    void testApplyChangeUpdatesExistingAttachmentInPlace() {
        var element = ElementType.CROTCHET.newInstance();
        var original = new TempoChangeAttachment(element, new Tempo(60, Duration.CROTCHET, "Largo", true));
        element.addAttachment(original);

        dialog.tempoSection.setTempo(new Tempo(140, Duration.QUAVER, "Presto", true));
        dialog.applyChange(element);

        var updated = element.findAttachment(TempoChangeAttachment.class);
        assertThat(updated)
            .as("existing attachment updated in-place, not replaced")
            .isSameAs(original);
        //noinspection ConstantValue -- NullAway guard
        assertThat(updated == null ? 0 : updated.getTempo().getVisibleTempo())
            .as("BPM updated in existing attachment")
            .isEqualTo(140);
        //noinspection ConstantValue -- NullAway guard
        assertThat(updated == null ? null : updated.getTempo().getTempoType())
            .as("tempo type updated in existing attachment")
            .isEqualTo(Duration.QUAVER);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void testApplyChangeAddsNewAttachmentWhenNoneExists() {
        var element = ElementType.CROTCHET.newInstance();

        dialog.tempoSection.setTempo(new Tempo(DEFAULT_BPM, Duration.CROTCHET, DEFAULT_DESCRIPTION, true));
        dialog.applyChange(element);

        var added = element.findAttachment(TempoChangeAttachment.class);
        assertThat(added)
            .as("new TempoChangeAttachment added when none existed")
            .isNotNull();
        //noinspection ConstantValue -- NullAway guard
        assertThat(added == null ? 0 : added.getTempo().getVisibleTempo())
            .as("new attachment has the configured BPM")
            .isEqualTo(DEFAULT_BPM);
    }
}
