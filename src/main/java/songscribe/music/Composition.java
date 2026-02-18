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
package songscribe.music;

import java.awt.*;
import java.awt.image.*;
import java.util.ArrayList;
import java.util.Properties;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;

import songscribe.ui.Constants;
import songscribe.ui.ProfileManager;
import songscribe.ui.action.InsertLineAction;
import songscribe.ui.component.IMainFrame;
import songscribe.ui.component.Score;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.message.LayoutChangeMessage;
import songscribe.ui.message.MessageCenter;
import songscribe.util.MyFontUtils;
import songscribe.util.StringUtils;
import songscribe.util.Utils;

/**
 * This class serves as the model for data that is read from and written to
 * SongScribe files.
 */
public final class Composition {

    public enum LANGUAGE {
        // Not used, the ordinals of the actual languages start at 1
        NONE,
        BENGALI, // 1
        ENGLISH, // 2
        BENGALI_ENGLISH, // 3
        ENGLISH_BENGALI, // 4
        SANSKRIT, // 5
        FRENCH, // 6
        SPANISH, // 7
        OTHER, // 8
    }

    // Used to replace the characters "ă" and "Ă" with "a" and "A" respectively
    private static final Pattern SHORT_A_PATTERN = Pattern.compile("[ăĂ]");

    // The base tempo of the composition
    private Tempo tempo;

    // The number of the song, can be empty
    private String number = "1";

    // The title of the song
    private String title = "Untitled";

    // Where the song was composed
    private String place = "";

    // The date the song was composed. The month is 1-based, i.e. January is 1.
    // If month/day/year are empty, they are not displayed.
    private int month = 0;
    private int day = 0;
    private String year = "";

    // The language of the song
    private LANGUAGE language = LANGUAGE.NONE;

    // The syllabified lyrics of the song displayed under the notes
    private String lyrics = "";

    // The full native lyrics of the song, displayed under the music
    private String underLyrics = "";

    // If the song is in Bengali, the lyrics in Bengali script
    private String banglaLyrics = "";

    // If the song is in a language other than English, the translated lyrics (if available)
    private String translatedLyrics = "";

    // The composer, date and place where the song was composed
    private String attribution;

    // Additional info about the song
    private String footnotes = "";

    // If true, the translation is unofficial (affects header text)
    private boolean unofficialTranslation = false;

    // The number of accidentals in the key signature and the type of key (flats or sharps)
    private int defaultKeyAccidentalCount;
    private KeyType defaultKeyType;

    // Fonts and their associated metrics used to display the song title, lyrics, info, etc.
    private Font titleFont;
    private FontMetrics titleFontMetrics;
    private Font lyricsFont;
    private FontMetrics lyricsFontMetrics;
    private Font banglaFont;
    private FontMetrics banglaFontMetrics;
    private Font attributionFont;
    private FontMetrics attributionFontMetrics;
    private Font annotationFont;
    private FontMetrics annotationFontMetrics;
    private Font footnoteFont;
    private FontMetrics footnoteFontMetrics;

    // When the title is set, it is wrapped into lines and stored here
    private final ArrayList<String> titleLines = new ArrayList<>();

    private int topPadding = 0;
    private boolean userSetTopPadding = false;
    private int attributionStartY;
    private int rowHeightAdjustment = 0;

    // The width of a staff line in pixels
    private int lineWidth = Score.PAGE_CONTENT_SIZE.width;

    // The lines of the score
    private final ArrayList<Line> lines = new ArrayList<>();

    // Dirty flag
    private boolean modified = false;

    /**
     * Indicates whether this composition has been dynamically laid out.
     * <p>
     * When reading a document:
     * - If false (legacy document): ignore xPos values (they were absolute positions)
     * - If true (new document): read xPos values as relative offsets
     * <p>
     * When saving: always set to true.
     */
    private boolean hasBeenDynamicallyLaidOut = false;

    /**
     * Data format version for the composition's internal representation.
     * <p>
     * <ul>
     *   <li>Version 1: Legacy format (IntervalSet ranges, inline Note attachments)</li>
     *   <li>Version 2: LineElement format (RangeElement objects, Attachment objects)</li>
     * </ul>
     * <p>
     * Note: This is distinct from the IO file format version (CompositionIO.IO_MAJOR_VERSION).
     * The file format may remain compatible while the in-memory representation is migrated.
     * <p>
     * Default is 1 for newly loaded compositions (before migration).
     * FormatMigrator sets this to 2 after migration.
     */
    private int formatVersion = 1;

    private final IMainFrame mainFrame;

    public Composition(@NotNull IMainFrame mainFrame) {
        this.mainFrame = mainFrame;

        var profile = mainFrame.getProfileManager();
        attribution = profile.getDefaultProperty(
            ProfileManager.ProfileKey.ATTRIBUTION
        );
        tempo = Tempo.getTempoFromProfile();
        defaultKeyAccidentalCount = Integer.parseInt(
            profile.getDefaultProperty(ProfileManager.ProfileKey.KEYS)
        );
        defaultKeyType = KeyType.valueOf(
            profile.getDefaultProperty(ProfileManager.ProfileKey.KEY_TYPE)
        );

        // Create a 1x1 image to get the graphics object
        var img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        var g = img.getGraphics();

        titleFont = MyFontUtils.getLocalFont("LatoPlus-Bold.otf", 30);
        titleFontMetrics = g.getFontMetrics(titleFont);

        lyricsFont = MyFontUtils.getLocalFont("LatoPlus-Regular.otf", 17);
        lyricsFontMetrics = g.getFontMetrics(lyricsFont);

        banglaFont = MyFontUtils.getLocalFont("TiroBangla-Regular.ttf", 17);
        banglaFontMetrics = g.getFontMetrics(banglaFont);

        attributionFont = MyFontUtils.getLocalFont("LatoPlus-Regular.otf", 15);
        attributionFontMetrics = g.getFontMetrics(attributionFont);

        annotationFont = MyFontUtils.getLocalFont("LatoPlus-Regular.otf", 15);
        annotationFontMetrics = g.getFontMetrics(annotationFont);

        footnoteFont = MyFontUtils.getLocalFont("LatoPlus-Italic.otf", 15);
        footnoteFontMetrics = g.getFontMetrics(footnoteFont);

        g.dispose();

        // Initial topPadding of 0 - LayoutManager.measure() will calculate the correct value
        // attributionStartY is calculated from title, will be recalculated on layout
        attributionStartY = calculateAttributionStartY();
        addLine(new Line());
    }

    public void musicChanged(@NotNull Properties props) {
        modified = true;
    }

    public Tempo getTempo() {
        return tempo;
    }

    public void setTempo(Tempo tempo) {
        this.tempo = tempo;
    }

    public Tempo getLastTempo(Line line, int noteIndex) {
        // find the last tempo change
        var lastLine = true;

        for (var lineIndex = lines.indexOf(line); lineIndex >= 0; lineIndex--) {
            var currentLine = lines.get(lineIndex);

            for (
                var n = lastLine ? noteIndex : (currentLine.noteCount() - 1);
                n >= 0;
                n--
            ) {
                if (currentLine.getNote(n).getTempoChange() != null) {
                    return currentLine.getNote(n).getTempoChange();
                }
            }

            lastLine = false;
        }

        return tempo;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String text) {
        if (title.equals(text)) {
            return;
        }

        var strippedTitle = StringUtils.collapseMultipleSpaces(
            StringUtils.stripLinefeeds(text)
        );
        title = processText(strippedTitle);
        // Note: attributionStartY recalculation removed - LayoutManager.measure()
        // handles this via the LayoutChangeMessage below

        MessageCenter.post(new LayoutChangeMessage(
            LayoutChangeMessage.Section.TITLE,
            LayoutChangeMessage.ChangeType.CONTENT,
            true
        ));
    }

    public String getPlace() {
        return place;
    }

    public void setPlace(@NotNull String place) {
        this.place = place.trim();
    }

    public String getYear() {
        return year;
    }

    public void setYear(@NotNull String year) {
        this.year = year.trim();
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public int getDay() {
        return day;
    }

    public void setDay(int day) {
        this.day = day;
    }

    public LANGUAGE getLanguage() {
        return language;
    }

    public void setLanguage(LANGUAGE language) {
        this.language = language;
    }

    public String getLyrics() {
        return lyrics;
    }

    public void setLyrics(@NotNull String text) {
        lyrics = processText(text);
    }

    public String getUnderLyrics() {
        return underLyrics;
    }

    public void setUnderLyrics(@NotNull String text) {
        var newLyrics = processText(text);

        if (underLyrics.equals(newLyrics)) {
            return;
        }

        underLyrics = newLyrics;

        MessageCenter.post(new LayoutChangeMessage(
            LayoutChangeMessage.Section.LYRICS,
            LayoutChangeMessage.ChangeType.CONTENT,
            true
        ));
    }

    public String getBanglaLyrics() {
        return banglaLyrics;
    }

    public void setBanglaLyrics(@NotNull String text) {
        var newLyrics = text.trim();

        if (banglaLyrics.equals(newLyrics)) {
            return;
        }

        banglaLyrics = newLyrics;

        MessageCenter.post(new LayoutChangeMessage(
            LayoutChangeMessage.Section.BANGLA_LYRICS,
            LayoutChangeMessage.ChangeType.CONTENT,
            true
        ));
    }

    public String getTranslatedLyrics() {
        return translatedLyrics;
    }

    // Convert ă => a and Ă => A and trim
    public void setTranslatedLyrics(@NotNull String text) {
        var newLyrics = text.trim();

        if (translatedLyrics.equals(newLyrics)) {
            return;
        }

        translatedLyrics = newLyrics;

        MessageCenter.post(new LayoutChangeMessage(
            LayoutChangeMessage.Section.TRANSLATION,
            LayoutChangeMessage.ChangeType.CONTENT,
            true
        ));
    }

    public String getFootnotes() {
        return footnotes;
    }

    public void setFootnotes(@NotNull String text) {
        var newFootnotes = text.trim();

        if (footnotes.equals(newFootnotes)) {
            return;
        }

        footnotes = newFootnotes;

        MessageCenter.post(new LayoutChangeMessage(
            LayoutChangeMessage.Section.FOOTNOTES,
            LayoutChangeMessage.ChangeType.CONTENT,
            true
        ));
    }

    public boolean isUnofficialTranslation() {
        return unofficialTranslation;
    }

    public void setUnofficialTranslation(boolean unofficial) {
        unofficialTranslation = unofficial;
    }

    public String getAttribution() {
        return attribution;
    }

    public void setAttribution(@NotNull String text) {
        var newAttribution = text.trim();

        if (attribution.equals(newAttribution)) {
            return;
        }

        attribution = newAttribution;

        MessageCenter.post(new LayoutChangeMessage(
            LayoutChangeMessage.Section.ATTRIBUTION,
            LayoutChangeMessage.ChangeType.CONTENT,
            true
        ));
    }

    @NotNull
    private String processText(@NotNull String text) {
        var strip = mainFrame
            .getProperties()
            .getProperty(Constants.STRIP_SHORT_A_PROP)
            .equals(Constants.TRUE_VALUE);

        if (strip && SHORT_A_PATTERN.matcher(text).find()) {
            mainFrame.showInfoMessage(
                "The characters “ă” and “Ă” have been replaced with “a” and “A”."
            );

            setModified(true);
            return text.replace("ă", "a").replace("Ă", "A");
        }

        return text.trim();
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(@NotNull String text) {
        number = text.trim();
    }

    public int getDefaultKeyAccidentalCount() {
        return defaultKeyAccidentalCount;
    }

    public void setDefaultKeyAccidentalCount(int defaultKeyAccidentalCount) {
        this.defaultKeyAccidentalCount = defaultKeyAccidentalCount;
    }

    public KeyType getDefaultKeyType() {
        return defaultKeyType;
    }

    public void setDefaultKeyType(KeyType defaultKeyType) {
        this.defaultKeyType = defaultKeyType;
    }

    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
        mainFrame.setDocumentModified(modified);
    }

    public void addLine(Line line) {
        addLine(InsertLineAction.ADD, line);
    }

    public void addLine(int index, Line line) {
        var lineIndex = index;

        if (lineIndex == InsertLineAction.ADD) {
            lineIndex = lines.size();
        }

        lines.add(lineIndex, line);
        line.setComposition(this);

        if (
            (line.getKeyAccidentalCount() == 0) && (line.getKeyType() == null)
        ) {
            line.setKeyAccidentalCount(defaultKeyAccidentalCount);
            line.setKeyType(defaultKeyType);
        }

        if (line.getTempoChangeYPos() == 0) {
            line.setTempoChangeYPos(
                (lineIndex == 0)
                    ? LayoutStylesheet.TEMPO_DEFAULT_Y_FIRST_LINE
                    : LayoutStylesheet.TEMPO_DEFAULT_Y_OTHER_LINES
            );
        }

        setModified(true);
    }

    public void removeLine(int index) {
        lines.remove(index);
        setModified(true);
    }

    public Line getLine(int index) {
        return lines.get(index);
    }

    public ArrayList<Line> getLines() {
        return lines;
    }

    public int lineCount() {
        return lines.size();
    }

    public int indexOfLine(Line line) {
        return lines.indexOf(line);
    }

    public boolean isEmpty() {
        return lines.isEmpty() || lines.stream().anyMatch(Line::isEmpty);
    }

    public Font getTitleFont() {
        return titleFont;
    }

    public void setTitleFont(Font font) {
        titleFont = font;
        titleFontMetrics = MyFontUtils.getFontMetrics(titleFont);
        // Note: attributionStartY recalculation removed - LayoutManager.measure()
        // handles this via the LayoutChangeMessage below
        setModified(true);

        MessageCenter.post(new LayoutChangeMessage(
            LayoutChangeMessage.Section.TITLE,
            LayoutChangeMessage.ChangeType.FONT,
            true
        ));
    }

    public FontMetrics getTitleFontMetrics() {
        return titleFontMetrics;
    }

    public Font getLyricsFont() {
        return lyricsFont;
    }

    public void setLyricsFont(Font font) {
        lyricsFont = font;
        lyricsFontMetrics = MyFontUtils.getFontMetrics(lyricsFont);
        setModified(true);

        MessageCenter.post(new LayoutChangeMessage(
            LayoutChangeMessage.Section.LYRICS,
            LayoutChangeMessage.ChangeType.FONT,
            true
        ));
    }

    public FontMetrics getLyricsFontMetrics() {
        return lyricsFontMetrics;
    }

    public Font getAttributionFont() {
        return attributionFont;
    }

    public void setAttributionFont(Font font) {
        attributionFont = font;
        attributionFontMetrics = MyFontUtils.getFontMetrics(attributionFont);
        setModified(true);

        MessageCenter.post(new LayoutChangeMessage(
            LayoutChangeMessage.Section.ATTRIBUTION,
            LayoutChangeMessage.ChangeType.FONT,
            true
        ));
    }

    public FontMetrics getAttributionFontMetrics() {
        return attributionFontMetrics;
    }

    public Font getAnnotationFont() {
        return annotationFont;
    }

    public void setAnnotationFont(Font font) {
        annotationFont = font;
        annotationFontMetrics = MyFontUtils.getFontMetrics(annotationFont);
        setModified(true);
    }

    public FontMetrics getAnnotationFontMetrics() {
        return annotationFontMetrics;
    }

    public Font getBanglaFont() {
        return banglaFont;
    }

    public FontMetrics getBanglaFontMetrics() {
        return banglaFontMetrics;
    }

    public void setBanglaFont(Font font) {
        banglaFont = font;
        banglaFontMetrics = MyFontUtils.getFontMetrics(banglaFont);
        setModified(true);

        MessageCenter.post(new LayoutChangeMessage(
            LayoutChangeMessage.Section.BANGLA_LYRICS,
            LayoutChangeMessage.ChangeType.FONT,
            true
        ));
    }

    public Font getFootnoteFont() {
        return footnoteFont;
    }

    public FontMetrics getFootnoteFontMetrics() {
        return footnoteFontMetrics;
    }

    public void setFootnoteFont(Font font) {
        footnoteFont = font;
        footnoteFontMetrics = MyFontUtils.getFontMetrics(footnoteFont);
        setModified(true);

        MessageCenter.post(new LayoutChangeMessage(
            LayoutChangeMessage.Section.FOOTNOTES,
            LayoutChangeMessage.ChangeType.FONT,
            true
        ));
    }

    public void setTopPadding(int padding, boolean setByUser) {
        topPadding = padding;
        userSetTopPadding = userSetTopPadding || setByUser;
        setModified(true);
    }

    public int getTopPadding() {
        return topPadding;
    }

    public int getAttributionStartY() {
        return attributionStartY;
    }

    public void setAttributionStartY(int attributionStartY) {
        this.attributionStartY = attributionStartY;
    }

    /**
     * Calculates initial attributionStartY based on title font height.
     * <p>
     * This is only used in the constructor to provide an initial value.
     * LayoutManager.measure() calculates the actual attribution position using
     * proper block flow layout. This method will be removed when attributionStartY
     * is migrated to an offset-based system.
     */
    private int calculateAttributionStartY() {
        // We want the attribution to start half of the song title font size below the song title
        var lineCount = Utils.lineCount(title);
        var lineHeight = MyFontUtils.getFontMetrics(titleFont).getHeight();
        return (lineHeight * lineCount) + (lineHeight / 2);
    }

    /**
     * Recalculates topPadding based on font sizes.
     * <p>
     * @deprecated This method is deprecated and will be removed in a future version.
     * LayoutManager.measure() now handles topPadding calculation. This method is only
     * called by CompositionIO.getComposition() for legacy file handling.
     */
    @Deprecated
    public void recalcTopPadding() {
        if (!userSetTopPadding) {
            topPadding = (((2 * titleFont.getSize()) +
                    (Utils.lineCount(attribution) * attributionFont.getSize())) -
                (2 * Score.STAFF_LINE_Y_OFFSET));
        }
    }

    public boolean userSetTopPadding() {
        return userSetTopPadding;
    }

    public int getRowHeightAdjustment() {
        return rowHeightAdjustment;
    }

    public void setRowHeightAdjustment(int rowHeightAdjustment) {
        this.rowHeightAdjustment = rowHeightAdjustment;
        setModified(true);
    }

    public int getLineWidth() {
        return lineWidth;
    }

    /**
     * Do not call this directly unless you know what you are doing.
     * Instead, use score.setLineWidth.
     */
    public void setLineWidth(int lineWidth) {
        if (this.lineWidth == lineWidth) {
            return;
        }

        this.lineWidth = lineWidth;
    }

    /**
     * Returns whether this composition has been dynamically laid out.
     * <p>
     * When false (legacy file), xPos values should be ignored on load.
     * When true, xPos values are relative offsets from calculated positions.
     *
     * @return true if this composition has been dynamically laid out
     */
    public boolean hasBeenDynamicallyLaidOut() {
        return hasBeenDynamicallyLaidOut;
    }

    /**
     * Sets whether this composition has been dynamically laid out.
     * <p>
     * Should be set to true when saving.
     *
     * @param hasBeenDynamicallyLaidOut true if dynamically laid out
     */
    public void setHasBeenDynamicallyLaidOut(boolean hasBeenDynamicallyLaidOut) {
        this.hasBeenDynamicallyLaidOut = hasBeenDynamicallyLaidOut;
    }

    /**
     * Returns the data format version of this composition.
     * <p>
     * Version 1 indicates legacy format (IntervalSet ranges, inline Note attachments).
     * Version 2 indicates LineElement format (RangeElement objects, Attachment objects).
     *
     * @return the format version (1 or 2)
     */
    public int getFormatVersion() {
        return formatVersion;
    }

    /**
     * Sets the data format version of this composition.
     * <p>
     * This is typically called by FormatMigrator after migrating from legacy format.
     *
     * @param formatVersion the format version to set
     */
    public void setFormatVersion(int formatVersion) {
        this.formatVersion = formatVersion;
    }
}
