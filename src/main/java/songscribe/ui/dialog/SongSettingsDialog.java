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

import java.awt.BorderLayout;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.SongMetadata;
import songscribe.error.RuntimeError;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.ui.component.MainFrame;

/**
 * The Song Settings dialog: a widget shell over the song's title, attribution, music settings
 * and fonts. It holds no song and no score.
 *
 * <p><strong>Two of its records span tabs, which is why they are assembled here.</strong> The
 * metadata is split between the Title and Attribution tabs and the fonts across three tabs.
 */
public class SongSettingsDialog extends StandardDialog<SongSettingsInput, SongSettingsOutput> {

    /**
     * Names a part of the song rather than a tab: {@link #TITLE} and {@link #SUBTITLE} share one
     * tab but land on different fields. {@link #show(Section)} owns the mapping; the constant
     * order carries no meaning.
     */
    public enum Section {
        TITLE,
        SUBTITLE,
        ATTRIBUTION,
        MUSIC,
        FONT,
    }

    private final SongSettingsFontTab fontTab = new SongSettingsFontTab(this);
    private final SongSettingsTitleTab textTab = new SongSettingsTitleTab(this);
    private final SongSettingsAttributionTab attributionTab = new SongSettingsAttributionTab(this);

    // Built in the constructor rather than here, so its construction order relative to the
    // tabbed container is unchanged.
    private final SongSettingsMusicTab musicTab;

    // What this opening is showing, kept because the commit needs the two font roles no tab
    // edits — Bangla and footnote — carried through untouched. Read only between populate()
    // and the window closing, which is the only span in which it means anything.
    private @Nullable SongSettingsInput input = null;

    public SongSettingsDialog(MainFrame mainFrame, DialogOps<SongSettingsInput, SongSettingsOutput> ops) {
        super(mainFrame, Strings.get(Strings.DIALOG_SONG_SETTINGS_TITLE), ops);

        var tabbedContent = createTabbedContent();
        addTab(textTab);
        addTab(attributionTab);
        musicTab = new SongSettingsMusicTab(this);
        addTab(musicTab);
        addTab(fontTab);

        contentPanel.add(BorderLayout.CENTER, tabbedContent);
    }

    /**
     * One switch maps each section to both its tab and its control, so they cannot disagree.
     */
    public void show(Section section) {
        switch (section) {
            case TITLE -> showTab(textTab, textTab.getTitleField());
            case SUBTITLE -> showTab(textTab, textTab.getSubtitleField());

            // These tabs have no single obvious field to land on, so the platform's
            // default first-focusable control is left in charge.
            case ATTRIBUTION -> showTab(attributionTab, null);
            case MUSIC -> showTab(musicTab, null);
            case FONT -> showTab(fontTab, null);
        }
    }

    /**
     * Tab order matters in one place: the Title tab is populated first because the Attribution
     * tab's preview reads the title and number out of it while building the attribution block.
     */
    @Override
    protected void populate(SongSettingsInput values) {
        input = values;

        textTab.populate(values);
        attributionTab.populate(values);
        musicTab.populate(values);
        fontTab.populate(values);
    }

    @Override
    protected SongSettingsOutput gather() {
        return new SongSettingsOutput(gatherMetadata(), gatherFonts(), musicTab.gather());
    }

    /**
     * Every field goes in raw: {@link SongMetadata}'s constructor normalizes each one, and the
     * Attribution tab's preview builds through the same constructor.
     */
    private SongMetadata gatherMetadata() {
        return new SongMetadata(
            textTab.getTitleText(),
            textTab.getNumberText(),
            attributionTab.getPlaceText(),
            attributionTab.getDate(),
            attributionTab.getComposerText(),
            attributionTab.getLyricistText(),
            attributionTab.getLyricsSource(),
            attributionTab.isArrangement(),
            attributionTab.isUnofficialTranslation(),
            textTab.getSubtitleText(),
            attributionTab.getWordsDate()
        );
    }

    /**
     * Each of the six edited roles is owned by one tab, so no tab holds the whole set. The
     * Bangla and footnote roles are carried through unchanged.
     */
    private DocumentFonts gatherFonts() {
        var fonts = new DocumentFonts(requireInput().fonts());
        fonts.setFont(FontKey.TITLE,           textTab.getTitleFont());
        fonts.setFont(FontKey.SUBTITLE,        textTab.getSubtitleFont());
        fonts.setFont(FontKey.ATTRIBUTION,     attributionTab.getAttributionFont());
        fonts.setFont(FontKey.SUB_ATTRIBUTION, attributionTab.getSubAttributionFont());
        fonts.setFont(FontKey.LYRICS,          fontTab.getLyricsFont());
        fonts.setFont(FontKey.ANNOTATION,      fontTab.getAnnotationFont());

        return fonts;
    }

    private SongSettingsInput requireInput() {
        var result = input;

        if (result == null) {
            throw RuntimeError.exit("song settings dialog gathered before it was populated");
        }

        return result;
    }
}
