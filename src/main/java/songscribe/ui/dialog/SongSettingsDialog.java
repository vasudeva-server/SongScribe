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

import module java.desktop;

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
 * what OK commits leaves as a {@link SongSettingsOutput}; everything in between is controls.
 * Its {@link SongSettingsBackEnd} does the reading and the writing, and decides what may be
 * committed.
 *
 * <p><strong>Two of its records span tabs, which is why they are assembled here.</strong> The
 * metadata is split between the Title and Attribution tabs and the fonts across three tabs, so
 * neither can be built from inside one of them — and a commit split across tabs would be
 * several undo steps for one press of OK.
 */
public class SongSettingsDialog extends CommitDialog<SongSettingsOutput> {

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

    private final SongSettingsBackEnd backEnd;

    private final SongSettingsFontTab fontTab = new SongSettingsFontTab(this);
    private final SongSettingsTitleTab textTab = new SongSettingsTitleTab(this);
    private final SongSettingsAttributionTab attributionTab =
        new SongSettingsAttributionTab(this, textTab);

    // Built in the constructor rather than here, so its construction order relative to the
    // tabbed container is unchanged.
    private final SongSettingsMusicTab musicTab;

    // What this opening is showing, kept because the commit needs the two font roles no tab
    // edits — Bangla and footnote — carried through untouched. Read only between getData()
    // and the window closing, which is the only span in which it means anything.
    private @Nullable SongSettingsInput input = null;

    /**
     * @param mainFrame the window this dialog parents itself to
     * @param backEnd   the domain half, bound to whatever document is open
     */
    public SongSettingsDialog(MainFrame mainFrame, SongSettingsBackEnd backEnd) {
        super(mainFrame, Strings.get(Strings.DIALOG_SONG_SETTINGS_TITLE), true, DialogCategory.EXCLUSIVE);
        this.backEnd = backEnd;

        var tabbedContent = createTabbedContent();
        addTab(textTab);
        addTab(attributionTab);
        musicTab = new SongSettingsMusicTab(this);
        addTab(musicTab);
        addTab(fontTab);

        contentPanel.add(BorderLayout.CENTER, tabbedContent);

        // Let Cancel bypass the range-validating fields' InputVerifiers so the
        // user can always dismiss the dialog without first fixing the value.
        cancelButton.setVerifyInputWhenFocusTarget(false);
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
     * Reads the document once and hands the same values to every tab.
     *
     * <p>Once per opening, so a dialog reopened on a different song shows that song — this one
     * is reached through a cached menu action and outlives many documents.
     *
     * @return always {@code true}; there is no state in which this dialog declines to open
     */
    @Override
    protected boolean getData() {
        if (!super.getData()) {
            return false;
        }

        var opened = backEnd.read();
        input = opened;

        // The Title tab first: the Attribution tab's preview reads the title and number out
        // of it while building the attribution block.
        textTab.populate(opened);
        attributionTab.populate(opened);
        musicTab.populate(opened);
        fontTab.populate(opened);

        return true;
    }

    @Override
    protected SongSettingsOutput gather() {
        return new SongSettingsOutput(gatherMetadata(), gatherFonts(), musicTab.gather());
    }

    /**
     * {@inheritDoc}
     *
     * <p>What may be committed is a judgment about the document — whether the lines still fit,
     * whether the width is one the page supports — so it is the back end's, made against the
     * song this dialog cannot see.
     */
    @Override
    protected ValidationResult validate(SongSettingsOutput values) {
        return backEnd.validate(values);
    }

    @Override
    protected void commit(SongSettingsOutput values) {
        backEnd.apply(values);
    }

    /**
     * The metadata the Title and Attribution tabs together describe.
     *
     * <p>Every field goes in raw: {@link SongMetadata}'s constructor normalizes each one, and
     * it is the same constructor the Attribution tab's preview builds through, which is what
     * makes the preview show what the commit will store.
     */
    private SongMetadata gatherMetadata() {
        var wordsDate = attributionTab.getWordsDate();

        return new SongMetadata(
            textTab.getTitleText(),
            textTab.getNumberText(),
            attributionTab.getPlaceText(),
            attributionTab.getYearText(),
            attributionTab.getMonth(),
            attributionTab.getDay(),
            attributionTab.getComposerText(),
            attributionTab.getLyricistText(),
            attributionTab.getLyricsSource(),
            attributionTab.isArrangement(),
            attributionTab.isUnofficialTranslation(),
            textTab.getSubtitleText(),
            wordsDate.year(),
            wordsDate.month(),
            wordsDate.day()
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
