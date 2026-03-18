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

import module java.desktop;

import java.util.ArrayList;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import net.engio.mbassy.listener.Handler;

import songscribe.Strings;
import songscribe.message.CompositionData;
import songscribe.message.MessageCenter;
import songscribe.notification.CompositionDidChangeNotification;
import songscribe.notification.CompositionDidChangeNotification.ChangeType;
import songscribe.notification.DocumentWasSavedNotification;
import songscribe.notification.FontDidChangeNotification;
import songscribe.notification.KeySignatureDidChangeNotification;
import songscribe.notification.LayoutDidChangeNotification;
import songscribe.notification.LyricsDidChangeNotification;
import songscribe.notification.MetadataDidChangeNotification;
import songscribe.notification.TempoDidChangeNotification;
import songscribe.prefs.Prefs;
import songscribe.ui.Dialogs;
import songscribe.ui.action.InsertLineAction;
import songscribe.ui.component.Score;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout2.ScaleContext;
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

    // Canonical defaults shared between Composition constructor and CompositionIO.DocumentReader
    public static final String DEFAULT_NUMBER = "1";
    public static final String DEFAULT_TITLE = "Untitled";
    public static final String DEFAULT_ATTRIBUTION = "Words and Music\nby Sri Chinmoy\n";
    public static final int DEFAULT_KEY_ACCIDENTAL_COUNT = 5;
    public static final KeyType DEFAULT_KEY_TYPE = KeyType.FLATS;

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
    @Nullable
    private String attribution;

    // Additional info about the song
    private String footnotes = "";

    // If true, the translation is unofficial (affects header text)
    private boolean unofficialTranslation = false;

    // The number of accidentals in the key signature and the type of key (flats or sharps)
    private int defaultKeyAccidentalCount;
    @Nullable
    private KeyType defaultKeyType;

    // Fonts and their associated metrics used to display the song title, lyrics, info, etc.
    private Font titleFont;
    private FontMetrics titleFontMetrics;
    private Font lyricsFont;
    private FontMetrics lyricsFontMetrics;
    @Nullable
    private Font banglaFont;
    @Nullable
    private FontMetrics banglaFontMetrics;
    private Font attributionFont;
    private FontMetrics attributionFontMetrics;
    private Font annotationFont;
    private FontMetrics annotationFontMetrics;
    @Nullable
    private Font footnoteFont;
    @Nullable
    private FontMetrics footnoteFontMetrics;

    // When the title is set, it is wrapped into lines and stored here
    private final ArrayList<String> titleLines = new ArrayList<>();

    private double topPaddingSs = 0;
    private boolean userSetTopPadding = false;
    private double attributionStartYSs;
    private double rowHeightAdjustmentSs = 0;

    // The width of a staff line in staff-space units
    private double lineWidthSs = ScaleContext.getInstance().fromPixels(Score.PAGE_CONTENT_SIZE.width);

    // The lines of the score
    private final ArrayList<Line> lines = new ArrayList<>();

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

    private boolean modified;

    /** Whether the user has already been notified about short-ă replacement in this session. */
    private boolean shortANotified;


    public Composition() {
        attribution = DEFAULT_ATTRIBUTION;
        tempo = new Tempo();
        defaultKeyAccidentalCount = DEFAULT_KEY_ACCIDENTAL_COUNT;
        defaultKeyType = DEFAULT_KEY_TYPE;
        initFontsFromPrefs();

        // Initial topPadding of 0 - layout calculation will set the correct value
        // attributionStartY is calculated from title, will be recalculated on layout
        attributionStartYSs = calculateAttributionStartY();

        // Add initial line directly (not via addLine) to avoid posting
        // a spurious STRUCTURE message before the composition is installed.
        var initialLine = new Line();
        lines.add(initialLine);
        initialLine.setComposition(this);
        initialLine.setKeyAccidentalCount(defaultKeyAccidentalCount);
        initialLine.setKeyType(defaultKeyType);
        initialLine.setTempoChangeYPosPx(
            ScaleContext.getInstance().toRoundedPixels(LayoutStylesheet.TEMPO_DEFAULT_Y_FIRST_LINE_SS)
        );

        MessageCenter.subscribe(this);
    }

    /**
     * Loading constructor. Initializes fonts from preferences, applies the
     * parsed data, and subscribes to the message bus. Avoids the wasted work
     * of the no-arg constructor (default line, attributionStartY calculation).
     */
    public Composition(CompositionData data) {
        initFontsFromPrefs();
        loadFrom(data);
        MessageCenter.subscribe(this);
    }

    private void initFontsFromPrefs() {
        var prefs = Prefs.getInstance();

        // Create a 1x1 image to get the graphics object
        var img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        var g = img.getGraphics();

        titleFont = MyFontUtils.createFont(prefs.getString("titleFont"), prefs.getInt("titleFontSize"));
        titleFontMetrics = g.getFontMetrics(titleFont);

        lyricsFont = MyFontUtils.createFont(prefs.getString("lyricsFont"), prefs.getInt("lyricsFontSize"));
        lyricsFontMetrics = g.getFontMetrics(lyricsFont);

        banglaFont = MyFontUtils.getLocalFont("TiroBangla-Regular.ttf", 17);
        banglaFontMetrics = g.getFontMetrics(banglaFont);

        attributionFont = MyFontUtils.createFont(prefs.getString("attributionFont"), prefs.getInt("attributionFontSize"));
        attributionFontMetrics = g.getFontMetrics(attributionFont);

        annotationFont = MyFontUtils.createFont(prefs.getString("annotationFont"), prefs.getInt("annotationFontSize"));
        annotationFontMetrics = g.getFontMetrics(annotationFont);

        footnoteFont = MyFontUtils.getLocalFont("LatoPlus-Italic.otf", 15);
        footnoteFontMetrics = g.getFontMetrics(footnoteFont);

        g.dispose();
    }

    @Handler
    public void documentWasSaved(DocumentWasSavedNotification message) {
        setModified(false);
    }

    /**
     * Applies all fields from a {@link CompositionData} snapshot atomically,
     * then posts a single {@link CompositionDidChangeNotification} with
     * {@code ChangeType.FULL}.
     * <p>
     * Called by {@link songscribe.io.CompositionIO.DocumentReader#getComposition()}
     * after parsing a file. This is a direct method call rather than a message
     * handler because loading targets a specific Composition instance.
     *
     * @param data the parsed composition data to apply
     */
    public void loadFrom(CompositionData data) {
        // Apply all scalar fields using apply methods (no individual message posting)
        this.tempo = data.tempo();
        applyNumber(data.number());
        applyTitle(data.title());
        applyPlace(data.place());
        applyMonth(data.month());
        applyDay(data.day());
        applyYear(data.year());
        applyLyrics(data.lyrics());
        applyUnderLyrics(data.underLyrics());
        applyBanglaLyrics(data.banglaLyrics());
        applyTranslatedLyrics(data.translatedLyrics());
        applyAttribution(data.attribution());
        applyFootnotes(data.footnotes());
        applyUnofficialTranslation(data.unofficialTranslation());
        applyDefaultKeyAccidentalCount(data.defaultKeyAccidentalCount());
        applyDefaultKeyType(data.defaultKeyType());

        // Apply fonts (null means keep defaults from constructor/prefs)
        if (data.titleFont() != null) {
            applyTitleFont(data.titleFont());
        }

        if (data.lyricsFont() != null) {
            applyLyricsFont(data.lyricsFont());
        }

        if (data.attributionFont() != null) {
            applyAttributionFont(data.attributionFont());
        }

        if (data.annotationFont() != null) {
            applyAnnotationFont(data.annotationFont());
        }

        // Apply layout
        applyTopPaddingSs(data.topPaddingSs(), false);
        applyAttributionStartYSs(data.attributionStartYSs());
        applyRowHeightAdjustmentSs(data.rowHeightAdjustmentSs());
        applyLineWidthSs(data.lineWidthSs());

        // Replace lines
        lines.clear();

        for (var line : data.lines()) {
            lines.add(line);
            line.setComposition(this);

            if (
                (line.getKeyAccidentalCount() == 0) && (line.getKeyType() == null)
            ) {
                line.setKeyAccidentalCount(defaultKeyAccidentalCount);
                line.setKeyType(defaultKeyType);
            }

            if (line.getTempoChangeYPosPx() == 0) {
                var lineIndex = lines.indexOf(line);
                line.setTempoChangeYPosPx(
                    (lineIndex == 0)
                        ? ScaleContext.getInstance().toRoundedPixels(LayoutStylesheet.TEMPO_DEFAULT_Y_FIRST_LINE_SS)
                        : ScaleContext.getInstance().toRoundedPixels(LayoutStylesheet.TEMPO_DEFAULT_Y_OTHER_LINES_SS)
                );
            }
        }

        this.hasBeenDynamicallyLaidOut = data.hasBeenDynamicallyLaidOut();
        this.formatVersion = data.formatVersion();

        // Loaded file starts unmodified
        modified = false;

        // Note: CompositionChanged(FULL) is NOT posted here because the
        // composition hasn't been installed into Score yet. Score.setComposition()
        // posts the FULL message after all state is consistent.
    }

    // ========== Getters (public, read-only API) ==========

    public Tempo getTempo() {
        return tempo;
    }

    /**
     * Returns the effective tempo at a given position in the composition.
     * Walks backwards through lines and notes to find the most recent tempo change,
     * or returns the default composition tempo if none found.
     *
     * @param lineIndex The index of the line
     * @param noteIndex The index of the note within the line
     * @return The effective tempo at this position
     */
    public Tempo getTempoAt(int lineIndex, int noteIndex) {
        // find the last tempo change
        var lastLine = true;

        for (var i = lineIndex; i >= 0; i--) {
            var currentLine = lines.get(i);

            for (
                var n = lastLine ? noteIndex : (currentLine.elementCount() - 1);
                n >= 0;
                n--
            ) {
                var tc = currentLine.getElement(n).getTempoChange();

                if (tc != null) {
                    return tc;
                }
            }

            lastLine = false;
        }

        return tempo;
    }

    public String getTitle() {
        return title;
    }

    public String getPlace() {
        return place;
    }

    public String getYear() {
        return year;
    }

    public int getMonth() {
        return month;
    }

    public int getDay() {
        return day;
    }

    public LANGUAGE getLanguage() {
        return language;
    }

    public String getLyrics() {
        return lyrics;
    }

    public String getUnderLyrics() {
        return underLyrics;
    }

    public String getBanglaLyrics() {
        return banglaLyrics;
    }

    public String getTranslatedLyrics() {
        return translatedLyrics;
    }

    public String getFootnotes() {
        return footnotes;
    }

    public boolean isUnofficialTranslation() {
        return unofficialTranslation;
    }

    public @Nullable String getAttribution() {
        return attribution;
    }

    public String getNumber() {
        return number;
    }

    public int getDefaultKeyAccidentalCount() {
        return defaultKeyAccidentalCount;
    }

    public @Nullable KeyType getDefaultKeyType() {
        return defaultKeyType;
    }

    public boolean isModified() {
        return modified;
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
        return lines.isEmpty() || lines.stream().allMatch(Line::isEmpty);
    }

    public Font getTitleFont() {
        return titleFont;
    }

    public FontMetrics getTitleFontMetrics() {
        return titleFontMetrics;
    }

    public Font getLyricsFont() {
        return lyricsFont;
    }

    public FontMetrics getLyricsFontMetrics() {
        return lyricsFontMetrics;
    }

    public Font getAttributionFont() {
        return attributionFont;
    }

    public FontMetrics getAttributionFontMetrics() {
        return attributionFontMetrics;
    }

    public Font getAnnotationFont() {
        return annotationFont;
    }

    public FontMetrics getAnnotationFontMetrics() {
        return annotationFontMetrics;
    }

    public @Nullable Font getBanglaFont() {
        return banglaFont;
    }

    public @Nullable FontMetrics getBanglaFontMetrics() {
        return banglaFontMetrics;
    }

    public @Nullable Font getFootnoteFont() {
        return footnoteFont;
    }

    public @Nullable FontMetrics getFootnoteFontMetrics() {
        return footnoteFontMetrics;
    }

    public double getTopPaddingSs() {
        return topPaddingSs;
    }

    public boolean userSetTopPadding() {
        return userSetTopPadding;
    }

    public double getAttributionStartYSs() {
        return attributionStartYSs;
    }

    public double getRowHeightAdjustmentSs() {
        return rowHeightAdjustmentSs;
    }

    public double getLineWidthSs() {
        return lineWidthSs;
    }

    public int getLineWidthPx() {
        return ScaleContext.getInstance().toRoundedPixels(lineWidthSs);
    }

    public boolean hasBeenDynamicallyLaidOut() {
        return hasBeenDynamicallyLaidOut;
    }

    public int getFormatVersion() {
        return formatVersion;
    }

    // ========== Setters (mutate + setModified + post) ==========

    public void setTempo(Tempo tempo) {
        mutateAndPost(ChangeType.CONTENT, () -> {this.tempo = tempo;});
    }

    public void setTitle(String text) {
        if (title.equals(text)) {
            return;
        }

        mutateAndPost(ChangeType.METADATA, () -> applyTitle(text));
    }

    public void setPlace(String place) {
        mutateAndPost(ChangeType.METADATA, () -> applyPlace(place));
    }

    public void setYear(String year) {
        mutateAndPost(ChangeType.METADATA, () -> applyYear(year));
    }

    public void setMonth(int month) {
        mutateAndPost(ChangeType.METADATA, () -> applyMonth(month));
    }

    public void setDay(int day) {
        mutateAndPost(ChangeType.METADATA, () -> applyDay(day));
    }

    public void setLyrics(String text) {
        mutateAndPost(ChangeType.LYRICS, () -> applyLyrics(text));
    }

    public void setUnderLyrics(String text) {
        var newLyrics = processText(text);

        if (underLyrics.equals(newLyrics)) {
            return;
        }

        mutateAndPost(ChangeType.LYRICS, () -> applyUnderLyrics(text));
    }

    public void setBanglaLyrics(String text) {
        var newLyrics = text.trim();

        if (banglaLyrics.equals(newLyrics)) {
            return;
        }

        mutateAndPost(ChangeType.LYRICS, () -> applyBanglaLyrics(text));
    }

    public void setTranslatedLyrics(String text) {
        var newLyrics = text.trim();

        if (translatedLyrics.equals(newLyrics)) {
            return;
        }

        mutateAndPost(ChangeType.LYRICS, () -> applyTranslatedLyrics(text));
    }

    public void setFootnotes(String text) {
        var newFootnotes = text.trim();

        if (footnotes.equals(newFootnotes)) {
            return;
        }

        mutateAndPost(ChangeType.METADATA, () -> applyFootnotes(text));
    }

    public void setUnofficialTranslation(boolean unofficial) {
        mutateAndPost(ChangeType.METADATA, () -> applyUnofficialTranslation(unofficial));
    }

    public void setAttribution(String text) {
        var newAttribution = text.trim();

        if (newAttribution.equals(attribution)) {
            return;
        }

        mutateAndPost(ChangeType.METADATA, () -> applyAttribution(text));
    }

    public void setNumber(String text) {
        mutateAndPost(ChangeType.METADATA, () -> applyNumber(text));
    }

    public void setDefaultKeyAccidentalCount(int defaultKeyAccidentalCount) {
        mutateAndPost(ChangeType.CONTENT, () -> applyDefaultKeyAccidentalCount(defaultKeyAccidentalCount));
    }

    public void setDefaultKeyType(KeyType defaultKeyType) {
        mutateAndPost(ChangeType.CONTENT, () -> applyDefaultKeyType(defaultKeyType));
    }

    // -- Font setters --

    public void setTitleFont(Font font) {
        mutateAndPost(ChangeType.FONT, () -> applyTitleFont(font));
    }

    public void setLyricsFont(Font font) {
        mutateAndPost(ChangeType.FONT, () -> applyLyricsFont(font));
    }

    public void setAttributionFont(Font font) {
        mutateAndPost(ChangeType.FONT, () -> applyAttributionFont(font));
    }

    public void setAnnotationFont(Font font) {
        mutateAndPost(ChangeType.FONT, () -> applyAnnotationFont(font));
    }

    public void setBanglaFont(Font font) {
        mutateAndPost(ChangeType.FONT, () -> applyBanglaFont(font));
    }

    public void setFootnoteFont(Font font) {
        mutateAndPost(ChangeType.FONT, () -> applyFootnoteFont(font));
    }

    // -- Layout setters --

    public void setTopPaddingSs(double padding, boolean setByUser) {
        mutateAndPost(ChangeType.LAYOUT, () -> applyTopPaddingSs(padding, setByUser));
    }

    public void setAttributionStartYSs(double attributionStartY) {
        mutateAndPost(ChangeType.LAYOUT, () -> applyAttributionStartYSs(attributionStartY));
    }

    public void setRowHeightAdjustmentSs(double rowHeightAdjustment) {
        mutateAndPost(ChangeType.LAYOUT, () -> applyRowHeightAdjustmentSs(rowHeightAdjustment));
    }

    /**
     * Do not call this directly unless you know what you are doing.
     * Instead, use score.setLineWidth.
     */
    public void setLineWidthSs(double lineWidth) {
        if (this.lineWidthSs == lineWidth) {
            return;
        }

        mutateAndPost(ChangeType.LAYOUT, () -> applyLineWidthSs(lineWidth));
    }

    // -- Structure setters --

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

        if (line.getTempoChangeYPosPx() == 0) {
            line.setTempoChangeYPosPx(
                (lineIndex == 0)
                    ? ScaleContext.getInstance().toRoundedPixels(LayoutStylesheet.TEMPO_DEFAULT_Y_FIRST_LINE_SS)
                    : ScaleContext.getInstance().toRoundedPixels(LayoutStylesheet.TEMPO_DEFAULT_Y_OTHER_LINES_SS)
            );
        }

        setModified(true);

        postChanged(ChangeType.STRUCTURE);
    }

    public void removeLine(int index) {
        lines.remove(index);
        setModified(true);

        postChanged(ChangeType.STRUCTURE);
    }

    // -- IO/internal setters (remain public, no message posting) --

    public void setModified(boolean modified) {
        if (modified == this.modified) {
            return;
        }

        this.modified = modified;
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
     * Sets the data format version of this composition.
     * <p>
     * This is typically called by FormatMigrator after migrating from legacy format.
     *
     * @param formatVersion the format version to set
     */
    public void setFormatVersion(int formatVersion) {
        this.formatVersion = formatVersion;
    }

    // ========== Update record handlers ==========

    @Handler
    public void lyricsDidChange(LyricsDidChangeNotification update) {
        mutateAndPost(ChangeType.LYRICS, () -> {
            if (update.getLyrics() != null) {
                applyLyrics(update.getLyrics());
            }

            if (update.getUnderLyrics() != null) {
                applyUnderLyrics(update.getUnderLyrics());
            }

            if (update.getTranslatedLyrics() != null) {
                applyTranslatedLyrics(update.getTranslatedLyrics());
            }

            if (update.getBanglaLyrics() != null) {
                applyBanglaLyrics(update.getBanglaLyrics());
            }
        });

        LyricsProcessor.spellLyrics(this);
    }

    @Handler
    public void metadataDidChange(MetadataDidChangeNotification update) {
        mutateAndPost(ChangeType.METADATA, () -> {
            if (update.getTitle() != null) {
                applyTitle(update.getTitle());
            }

            if (update.getPlace() != null) {
                applyPlace(update.getPlace());
            }

            if (update.getYear() != null) {
                applyYear(update.getYear());
            }

            if (update.getNumber() != null) {
                applyNumber(update.getNumber());
            }

            if (update.getAttribution() != null) {
                applyAttribution(update.getAttribution());
            }

            if (update.getMonth() != null) {
                applyMonth(update.getMonth());
            }

            if (update.getDay() != null) {
                applyDay(update.getDay());
            }

            if (update.getUnofficialTranslation() != null) {
                applyUnofficialTranslation(update.getUnofficialTranslation());
            }
        });
    }

    @Handler
    public void fontDidChange(FontDidChangeNotification update) {
        mutateAndPost(ChangeType.FONT, () -> {
            if (update.getTitleFont() != null) {
                applyTitleFont(update.getTitleFont());
            }

            if (update.getLyricsFont() != null) {
                applyLyricsFont(update.getLyricsFont());
            }

            if (update.getAttributionFont() != null) {
                applyAttributionFont(update.getAttributionFont());
            }

            if (update.getAnnotationFont() != null) {
                applyAnnotationFont(update.getAnnotationFont());
            }
        });
    }

    @Handler
    public void tempoDidChange(TempoDidChangeNotification update) {
        mutateAndPost(ChangeType.CONTENT, () -> {
            if (update.getTempoType() != null) {
                tempo.setTempoType(update.getTempoType());
            }

            if (update.getVisibleTempo() != null) {
                tempo.setVisibleTempo(update.getVisibleTempo());
            }

            if (update.getTempoDescription() != null) {
                tempo.setTempoDescription(update.getTempoDescription());
            }

            if (update.getShowTempo() != null) {
                tempo.setShowTempo(update.getShowTempo());
            }
        });
    }

    @Handler
    public void keySignatureDidChange(KeySignatureDidChangeNotification update) {
        mutateAndPost(ChangeType.CONTENT, () -> {
            if (update.getLineIndex() == null) {
                // Composition-level default with propagation to matching lines
                var oldKeyType = defaultKeyType;
                var oldAccidentalCount = defaultKeyAccidentalCount;
                applyDefaultKeyType(update.getKeyType());
                applyDefaultKeyAccidentalCount(update.getAccidentalCount());

                for (var i = 0; i < lineCount(); i++) {
                    var line = getLine(i);

                    if (
                        line.getKeyAccidentalCount() == oldAccidentalCount
                            && line.getKeyType() == oldKeyType
                    ) {
                        line.setKeyAccidentalCount(defaultKeyAccidentalCount);
                        line.setKeyType(defaultKeyType);
                    }
                }
            } else {
                // Per-line update
                var line = getLine(update.getLineIndex());
                line.setKeyType(update.getKeyType());
                line.setKeyAccidentalCount(update.getAccidentalCount());
            }
        });
    }

    @Handler
    public void layoutDidChange(LayoutDidChangeNotification update) {
        mutateAndPost(ChangeType.LAYOUT, () -> {
            if (update.getTopPaddingSs() != null) {
                applyTopPaddingSs(
                    update.getTopPaddingSs(),
                    update.getTopPaddingSetByUser() != null && update.getTopPaddingSetByUser()
                );
            }

            if (update.getRowHeightAdjustmentSs() != null) {
                applyRowHeightAdjustmentSs(update.getRowHeightAdjustmentSs());
            }

            if (update.getLineWidthSs() != null) {
                applyLineWidthSs(update.getLineWidthSs());
            }

            if (update.getAttributionStartYSs() != null) {
                applyAttributionStartYSs(update.getAttributionStartYSs());
            }
        });
    }

    // ========== Private helpers ==========

    private void mutateAndPost(ChangeType changeType, Runnable mutation) {
        mutation.run();
        setModified(true);
        postChanged(changeType);
    }

    // -- Apply methods (field mutation only, no side effects) --

    private void applyTitle(String text) {
        title = processText(StringUtils.collapseMultipleSpaces(
            StringUtils.stripLinefeeds(text)));
    }

    private void applyPlace(String place) {
        this.place = place.trim();
    }

    private void applyYear(String year) {
        this.year = year.trim();
    }

    private void applyMonth(int month) {
        this.month = month;
    }

    private void applyDay(int day) {
        this.day = day;
    }

    private void applyLyrics(String text) {
        lyrics = processText(text);
    }

    private void applyUnderLyrics(String text) {
        underLyrics = processText(text);
    }

    private void applyBanglaLyrics(String text) {
        banglaLyrics = text.trim();
    }

    private void applyTranslatedLyrics(String text) {
        translatedLyrics = text.trim();
    }

    private void applyFootnotes(String text) {
        footnotes = text.trim();
    }

    private void applyUnofficialTranslation(boolean unofficial) {
        unofficialTranslation = unofficial;
    }

    private void applyAttribution(String text) {
        attribution = text.trim();
    }

    private void applyNumber(String text) {
        number = text.trim();
    }

    private void applyDefaultKeyType(KeyType keyType) {
        this.defaultKeyType = keyType;
    }

    private void applyDefaultKeyAccidentalCount(int count) {
        this.defaultKeyAccidentalCount = count;
    }

    private void applyTitleFont(Font font) {
        titleFont = font;
        titleFontMetrics = MyFontUtils.getFontMetrics(titleFont);
    }

    private void applyLyricsFont(Font font) {
        lyricsFont = font;
        lyricsFontMetrics = MyFontUtils.getFontMetrics(lyricsFont);
    }

    private void applyAttributionFont(Font font) {
        attributionFont = font;
        attributionFontMetrics = MyFontUtils.getFontMetrics(attributionFont);
    }

    private void applyAnnotationFont(Font font) {
        annotationFont = font;
        annotationFontMetrics = MyFontUtils.getFontMetrics(annotationFont);
    }

    private void applyBanglaFont(Font font) {
        banglaFont = font;
        banglaFontMetrics = MyFontUtils.getFontMetrics(banglaFont);
    }

    private void applyFootnoteFont(Font font) {
        footnoteFont = font;
        footnoteFontMetrics = MyFontUtils.getFontMetrics(footnoteFont);
    }

    private void applyTopPaddingSs(double padding, boolean setByUser) {
        topPaddingSs = padding;
        userSetTopPadding = userSetTopPadding || setByUser;
    }

    private void applyAttributionStartYSs(double attributionStartY) {
        this.attributionStartYSs = attributionStartY;
    }

    private void applyRowHeightAdjustmentSs(double rowHeightAdjustment) {
        this.rowHeightAdjustmentSs = rowHeightAdjustment;
    }

    private void applyLineWidthSs(double lineWidth) {
        this.lineWidthSs = lineWidth;
    }

    private String processText(String text) {
        var strip = Prefs.getInstance().getBoolean("stripShortA");

        if (strip && SHORT_A_PATTERN.matcher(text).find()) {
            if (!shortANotified) {
                shortANotified = true;
                Dialogs.showInfoMessage(
                    null,
                    Strings.get(Strings.DIALOG_TITLE_INFORMATION),
                    Strings.get(Strings.INFO_CHARACTER_REPLACEMENT)
                );
            }

            setModified(true);
            return text.replace("ă", "a").replace("Ă", "A");
        }

        return text.trim();
    }

    /**
     * Calculates initial attributionStartY based on title font height.
     * <p>
     * This is only used in the constructor to provide an initial value.
     * Layout calculation determines the actual attribution position using
     * proper block flow layout. This method will be removed when attributionStartY
     * is migrated to an offset-based system.
     */
    private double calculateAttributionStartY() {
        // We want the attribution to start half of the song title font size below the song title
        var lineCount = Utils.lineCount(title);
        var lineHeight = MyFontUtils.getFontMetrics(titleFont).getHeight();
        return (lineHeight * lineCount) + (lineHeight / 2);
    }

    private void postChanged(ChangeType changeType) {
        MessageCenter.post(new CompositionDidChangeNotification(changeType, this));
    }
}
