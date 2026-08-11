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

import java.awt.Font;

import javax.swing.JComponent;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import songscribe.MainFrameMockTest;
import songscribe.dom.Song;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link SongSettingsDialog#show(SongSettingsDialog.Section)} — which tab and
 * which control each section hands to {@code showTab}.
 * <p>
 * Covers all five {@link SongSettingsDialog.Section} values, not just the ones #756 and #757
 * use, because the exhaustive switch inside {@code show} catches a <em>missing</em> arm at
 * compile time but not a <em>mis-wired</em> one — nothing stops {@code MUSIC -> fontTab} from
 * compiling.
 * <p>
 * {@code showTab} is stubbed to a no-op on a spy of a real dialog rather than let it run: the
 * tab fields are private with no package-private accessor, and driving a real
 * {@code setVisible(true)} would only add JDialog-mocking overhead irrelevant to what is being
 * tested here. That the dialog then actually focuses what it was handed is
 * {@link BaseDialogTabsTest}'s job.
 */
class SongSettingsDialogShowTest extends MainFrameMockTest {

    private static final Font FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private SongSettingsDialog dialog;

    @BeforeEach
    void setUp() {
        dialog = spy(SongSettingsDialogFixture.build(mockEnv(), new Song(), FONT).dialog());
        doNothing().when(dialog).showTab(any(), any());
    }

    @ParameterizedTest
    @EnumSource(SongSettingsDialog.Section.class)
    void testShowSelectsTheTabTheSectionNames(SongSettingsDialog.Section section) {
        var expectedTabClass = switch (section) {
            // TITLE and SUBTITLE share the Text tab and are told apart by which
            // field they focus, which testShowFocusesTheFieldTheSectionNames covers.
            case TITLE, SUBTITLE -> SongSettingsTitleTab.class;
            case ATTRIBUTION -> SongSettingsAttributionTab.class;
            case MUSIC -> SongSettingsMusicTab.class;
            case FONT -> SongSettingsFontTab.class;
        };

        dialog.show(section);

        assertThat(capturedTab())
            .as("show(%s) must select the %s tab", section, expectedTabClass.getSimpleName())
            .isInstanceOf(expectedTabClass);
    }

    /**
     * The section-to-field wiring, which the exhaustive switch cannot police: mapping
     * SUBTITLE to the title field compiles. Asserted by identity against the tab's own
     * controls, so swapping the two arms fails.
     */
    @Test
    void testShowTitleFocusesTheTitleField() {
        dialog.show(SongSettingsDialog.Section.TITLE);

        var titleTab = (SongSettingsTitleTab) capturedTab();

        assertThat(capturedFocus())
            .as("show(TITLE) must focus the title field, not another field on the tab")
            .isSameAs(titleTab.getTitleField());
    }

    @Test
    void testShowSubtitleFocusesTheSubtitleField() {
        dialog.show(SongSettingsDialog.Section.SUBTITLE);

        var titleTab = (SongSettingsTitleTab) capturedTab();

        assertThat(capturedFocus())
            .as("show(SUBTITLE) must focus the subtitle field, not the title field")
            .isSameAs(titleTab.getSubtitleField());
    }

    /**
     * Sections whose tab has no single obvious landing control must leave focus to the
     * platform rather than naming one.
     */
    @ParameterizedTest
    @EnumSource(value = SongSettingsDialog.Section.class, names = { "ATTRIBUTION", "MUSIC", "FONT" })
    void testShowRequestsNoFocusForSectionsWithoutANamedField(SongSettingsDialog.Section section) {
        dialog.show(section);

        assertThat(capturedFocus())
            .as("show(%s) must not name any particular control to focus", section)
            .isNull();
    }

    private BaseDialog.Tab capturedTab() {
        var tabCaptor = ArgumentCaptor.forClass(BaseDialog.Tab.class);
        verify(dialog).showTab(tabCaptor.capture(), any());

        return tabCaptor.getValue();
    }

    private @Nullable JComponent capturedFocus() {
        var focusCaptor = ArgumentCaptor.forClass(JComponent.class);
        verify(dialog).showTab(any(), focusCaptor.capture());

        return focusCaptor.getValue();
    }
}
