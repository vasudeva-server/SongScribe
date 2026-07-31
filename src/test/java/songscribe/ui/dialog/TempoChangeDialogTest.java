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
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.dom.Duration;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.prefs.Prefs;
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link TempoChangeDialog}: populateControls null/existing,
 * applyChange (Tempo construction + show-flag inversion, add vs update),
 * clearChange (removeAttachment + clearTempoIfOrphaned), and showForElement
 * (static factory pre-sets element/line before show).
 */
class TempoChangeDialogTest extends MainFrameMockTest {

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
        dialog = new TempoChangeDialog(mainFrame());
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
            .as("BPM matches the Tempo default")
            .isEqualTo(Tempo.DEFAULT_BPM);
        assertThat(dialog.tempoSection.getTempoType())
            .as("tempo type matches the Tempo default")
            .isEqualTo(Tempo.DEFAULT_TYPE);
        assertThat(dialog.tempoSection.getTempoDescription())
            .as("description matches the Tempo default")
            .isEqualTo(Tempo.DEFAULT_DESCRIPTION);
        assertThat(dialog.tempoSection.isShowOnlyDescription())
            .as("DEFAULT_SHOW_TEMPO=true means showOnlyDescription=false")
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
    void testApplyChangeAddsNewAttachmentWhenNoneExists() {
        var element = ElementType.CROTCHET.newInstance();

        dialog.tempoSection.setTempo(new Tempo());
        dialog.applyChange(element);

        var added = element.findAttachment(TempoChangeAttachment.class);
        assertThat(added)
            .as("new TempoChangeAttachment added when none existed")
            .isNotNull();
        //noinspection ConstantValue -- NullAway guard
        assertThat(added == null ? 0 : added.getTempo().getVisibleTempo())
            .as("new attachment has the configured BPM")
            .isEqualTo(Tempo.DEFAULT_BPM);
    }

    // ── Row 29: clearChange — removes attachment, then calls clearTempoIfOrphaned ──

    @Test
    void testClearChangeRemovesAttachmentAndCallsClearTempoIfOrphaned() {
        // A real Line with its backing Song mock is needed so element.getLine() works.
        var song = minimalSongMock();
        var line = new Line(song);
        var element = ElementType.CROTCHET.newInstance();
        line.addElement(element);

        var attachment = new TempoChangeAttachment(element, new Tempo(80, Duration.CROTCHET, "Largo", true));
        element.addAttachment(attachment);

        dialog.clearChange(element);

        assertThat(element.findAttachment(TempoChangeAttachment.class))
            .as("TempoChangeAttachment removed by clearChange")
            .isNull();
        verify(song).clearTempoIfOrphaned(element);
    }

    @Test
    void testClearChangeCallsClearTempoIfOrphanedEvenWhenNoAttachment() {
        var song = minimalSongMock();
        var line = new Line(song);
        var element = ElementType.CROTCHET.newInstance();
        line.addElement(element);

        // No attachment present — clearChange should still call clearTempoIfOrphaned
        dialog.clearChange(element);

        assertThat(element.findAttachment(TempoChangeAttachment.class))
            .as("no attachment present either before or after clearChange")
            .isNull();
        verify(song).clearTempoIfOrphaned(element);
    }

    // ── Row 30: showForElement — static factory pre-sets selectedElement/selectedLine before showing ──

    @Test
    void testShowForElementSetsElementAndLineBeforeShow() {
        var element = ElementType.CROTCHET.newInstance();
        var line = detachedLine();

        try (MockedConstruction<TempoChangeDialog> construction = mockConstruction(TempoChangeDialog.class)) {
            TempoChangeDialog.showForElement(mainFrame(), element, line);

            assertThat(construction.constructed())
                .as("exactly one TempoChangeDialog was created")
                .hasSize(1);
            var captured = construction.constructed().get(0);

            assertThat(captured.selectedElement)
                .as("selectedElement set to the provided element before setVisible")
                .isSameAs(element);
            assertThat(captured.selectedLine)
                .as("selectedLine set to the provided line before setVisible")
                .isSameAs(line);
            verify(captured).setVisible(true);
        }
    }
}
