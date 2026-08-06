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

import java.util.List;

import songscribe.Strings;
import songscribe.font.FontKey;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.util.GraphicUtils;
import songscribe.util.UIUtils;

/**
 * The {@link SongSettingsDialog} Fonts tab: the fonts that have no dedicated
 * contextual tab — lyrics and annotation — each with a rendered sample.
 */
final class SongSettingsFontTab extends BaseDialog.Tab {

    private final SongSettingsDialog dialog;

    // The Fonts tab owns the fonts that have no dedicated contextual tab:
    // lyrics and annotation. Title, attribution, and sub-attribution fonts
    // are owned by the Title and Attribution tabs respectively.
    private final JLabel lyricsFontLabel = FontSettingRow.createFontDescriptionLabel();
    private final ScoreTextPreview lyricsFontPreview = new ScoreTextPreview(
        "I shall bind myself at Your Feet.",
        "With this hope I have come to You",
        "   With tear-filled eyes.",
        "I shall worship You within the tumult",
        "   Of this life.",
        "I shall satisfy You on the strength",
        "   Of my surrender."
    );

    private final JLabel annotationFontLabel = FontSettingRow.createFontDescriptionLabel();
    private final ScoreTextPreview annotationFontPreview = new ScoreTextPreview(
        "D.C. al fine (a tempo)"
    );

    SongSettingsFontTab(SongSettingsDialog dialog) {
        dialog.super(
            Strings.get(Strings.DIALOG_SONG_SETTINGS_TAB_FONTS),
            FlatLafKey.DIALOG_SONG_SETTINGS_FONT_PADDING
        );
        this.dialog = dialog;

        var pageBackground = FlatLafProps.getColor(FlatLafKey.SCORE_PAGE_SCREEN_BACKGROUND);

        for (var preview : new ScoreTextPreview[]{ lyricsFontPreview, annotationFontPreview }) {
            preview.setBackground(pageBackground);
            preview.setForeground(Color.BLACK);
            preview.setOpaque(true);
        }

        build();
    }

    @Override
    protected void initContents() {
        var mainFrame = dialog.getMainFrame();

        var previewPadding = FlatLafProps.getInsets(FlatLafKey.DIALOG_SONG_SETTINGS_FONT_PREVIEW_PADDING);

        var lyricsSection = new BaseDialog.TitledSection(
            Strings.get(Strings.DIALOG_SONG_SETTINGS_SECTION_LYRICS_TRANSLATION)
        );
        lyricsSection.add(FontSettingRow.create(
            mainFrame, lyricsFontLabel, FontKey.LYRICS, lyricsFontPreview::getFont, lyricsFontPreview::setFont
        ));
        BaseDialog.addLargeSeparator(lyricsSection);
        lyricsSection.add(SongSettingsLayout.createPreviewWrapper(lyricsFontPreview, previewPadding));
        UIUtils.setFlexibleWidth(lyricsSection);
        add(lyricsSection);

        BaseDialog.addSectionSeparator(this);

        var annotationSection = new BaseDialog.TitledSection(
            Strings.get(Strings.DIALOG_SONG_SETTINGS_SECTION_ANNOTATION)
        );
        annotationSection.add(FontSettingRow.create(
            mainFrame, annotationFontLabel, FontKey.ANNOTATION, annotationFontPreview::getFont, annotationFontPreview::setFont
        ));
        BaseDialog.addLargeSeparator(annotationSection);
        annotationSection.add(SongSettingsLayout.createPreviewWrapper(annotationFontPreview, previewPadding));
        UIUtils.setFlexibleWidth(annotationSection);
        add(annotationSection);
    }

    /**
     * Renders sample lines by hand with {@link GraphicUtils#setRenderingHints},
     * the same way the score draws its text. A {@link JLabel} can't be used
     * here: its UI re-imposes the platform text-antialiasing hint (LCD
     * subpixel on macOS) over any hint set on the graphics, which fringes
     * against the off-white page background and looks unlike the score.
     */
    private static final class ScoreTextPreview extends JComponent {

        private final List<String> lines;

        private ScoreTextPreview(String... lines) {
            this.lines = List.of(lines);
        }

        @Override
        protected void paintComponent(Graphics g) {
            var g2 = (Graphics2D) g;

            if (isOpaque()) {
                g2.setColor(getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());
            }

            GraphicUtils.setRenderingHints(g2);
            g2.setFont(getFont());
            g2.setColor(getForeground());

            var metrics = g2.getFontMetrics();
            var lineHeight = metrics.getHeight();
            var baselineY = metrics.getAscent();

            for (var line : lines) {
                g2.drawString(line, 0, baselineY);
                baselineY += lineHeight;
            }
        }

        @Override
        public void setFont(Font font) {
            super.setFont(font);
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            var font = getFont();

            if (font == null) {
                return new Dimension(0, 0);
            }

            var metrics = getFontMetrics(font);
            var width = 0;

            for (var line : lines) {
                width = Math.max(width, metrics.stringWidth(line));
            }

            var height = GraphicUtils.getTextBlockHeight(metrics, lines.size());

            return new Dimension(width, height);
        }
    }

    @Override
    protected boolean getData() {
        var fonts = dialog.requireScoreView().getDocumentFonts();
        FontSettingRow.applyFont(fonts.getFont(FontKey.LYRICS),     lyricsFontLabel,     lyricsFontPreview::setFont);
        FontSettingRow.applyFont(fonts.getFont(FontKey.ANNOTATION), annotationFontLabel, annotationFontPreview::setFont);
        return true;
    }

    Font getLyricsFont() {
        return lyricsFontPreview.getFont();
    }

    Font getAnnotationFont() {
        return annotationFontPreview.getFont();
    }
}
