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
 * and fonts.
 *
 * <p>It holds no song and no score. What it shows arrives as a {@link SongSettingsInput} and
 * what OK commits leaves as a {@link SongSettingsOutput}; everything in between is controls. The
 * {@link DialogOps} it is constructed with does the reading and the writing, and decides what may
 * be committed.
 *
 * <p><strong>Two of its records span tabs, which is why they are assembled here.</strong> The
 * metadata is split between the Title and Attribution tabs and the fonts across three tabs, so
 * neither can be built from inside one of them — and a commit split across tabs would be
 * several undo steps for one press of OK.
 */
public class SongSettingsDialog extends StandardDialog<SongSettingsInput, SongSettingsOutput> {

    /**
     * What a caller wants to edit, for those that need the dialog opened somewhere
     * specific. Names a part of the song rather than a tab: {@link #TITLE} and
     * {@link #SUBTITLE} share one tab but land on different fields, and callers should not
     * have to know that. {@link #show(Section)} owns the mapping, and the constant order
     * here carries no meaning.
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

    /**
     * @param mainFrame the window this dialog parents itself to
     * @param ops       what this dialog may ask of the document, supplied by whoever opened it
     */
    public SongSettingsDialog(MainFrame mainFrame, DialogOps<SongSettingsInput, SongSettingsOutput> ops) {
        super(mainFrame, Strings.get(Strings.DIALOG_SONG_SETTINGS_TITLE), ops, DialogCategory.EXCLUSIVE);

        var tabbedContent = createTabbedContent();
        addTab(textTab);
        addTab(attributionTab);
        musicTab = new SongSettingsMusicTab(this);
        addTab(musicTab);
        addTab(fontTab);

        contentPanel.add(BorderLayout.CENTER, tabbedContent);
    }

    /**
     * Shows the dialog with {@code section}'s tab selected and, where the section names a
     * particular field, that field focused.
     * <p>
     * One switch maps each section to both, so a section cannot end up opening one tab
     * while focusing a control on another. It is exhaustive by enum, so a new
     * {@link Section} fails to compile here rather than silently opening the wrong tab.
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
     * Hands the same values to every tab, and keeps them for {@link #gather()}.
     *
     * <p>Tab order matters in one place: the Title tab is populated first because the Attribution
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
     * The metadata the Title and Attribution tabs together describe.
     *
     * <p>Every field goes in raw: {@link SongMetadata}'s constructor normalizes each one, and
     * it is the same constructor the Attribution tab's preview builds through, which is what
     * makes the preview show what the commit will store.
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
     * The document's fonts with the six roles this dialog edits replaced.
     *
     * <p>Each of those six is owned by one tab — title and subtitle by the Title tab,
     * attribution and sub-attribution by the Attribution tab, lyrics and annotation by the
     * Fonts tab — so no tab holds the whole set. The Bangla and footnote roles are
     * document-level but not surfaced here, and the copy carries them through unchanged.
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
