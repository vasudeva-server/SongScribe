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
package songscribe.dom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import net.engio.mbassy.listener.Handler;

import songscribe.Strings;
import songscribe.error.RuntimeError;
import songscribe.io.SongIO;
import songscribe.message.SongData;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.LayoutChange;
import songscribe.message.mutation.LayoutField;
import songscribe.message.mutation.LineDeletion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.LyricsChange;
import songscribe.message.mutation.LyricsField;
import songscribe.message.mutation.MetadataChange;
import songscribe.message.mutation.MetadataField;
import songscribe.message.mutation.Mutation;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.DocumentWasSavedNotification;
import songscribe.message.notification.KeySignatureDidChangeNotification;
import songscribe.message.notification.LayoutDidChangeNotification;
import songscribe.message.notification.MetadataDidChangeNotification;
import songscribe.message.notification.TempoDidChangeNotification;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.util.MyFontUtils;
import songscribe.util.StringUtils;
import songscribe.util.Utils;

/**
 * This class serves as the model for data that is read from and written to
 * SongScribe files.
 */
public final class Song {

    /**
     * Default tempo Y for first line (-5 staff spaces above middle)
     */
    public static final double TEMPO_DEFAULT_Y_FIRST_LINE_SS = -5.0;  // -40px
    /**
     * Default tempo Y for subsequent lines (-3 staff spaces above middle)
     */
    public static final double TEMPO_DEFAULT_Y_OTHER_LINES_SS = -3.0;  // -24px

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

    public static final int DEFAULT_KEY_ACCIDENTAL_COUNT = 5;
    public static final KeyType DEFAULT_KEY_TYPE = KeyType.FLATS;

    // Sentinel passed to addLine(int, Line) meaning "append after the last line"
    private static final int APPEND = -1;

    // Provides the default line width for new songs; set by the UI layer at startup
    private static DoubleSupplier defaultLineWidthProvider = () -> 0.0;

    public static void setDefaultLineWidthProvider(DoubleSupplier provider) {
        defaultLineWidthProvider = provider;
    }

    // The base tempo of the song; null means the song has no explicit tempo
    @Nullable
    private Tempo tempo;

    // The number of the song, can be empty
    private String number = Strings.get(Strings.SONG_DEFAULT_NUMBER);

    // The title of the song
    private String title = Strings.get(Strings.DOCUMENT_UNTITLED);

    // Where the song was composed
    private String place = "";

    // The date the song was composed. The month is 1-based, i.e. January is 1.
    // If month/day/year are empty, they are not displayed.
    private int month = 0;
    private int day = 0;
    private String year = "";

    // The language of the song
    private final LANGUAGE language = LANGUAGE.NONE;

    // The full native lyrics of the song, displayed under the music
    private String underLyrics = "";

    // If the song is in Bengali, the lyrics in Bengali script
    private String banglaLyrics = "";

    // If the song is in a language other than English, the translated lyrics (if available)
    private String translatedLyrics = "";

    // The composer, date and place where the song was composed
    private String attribution = "";

    // Additional info about the song
    private String footnotes = "";

    // If true, the translation is unofficial (affects header text)
    private boolean unofficialTranslation = false;

    // The number of accidentals in the key signature and the type of key (flats or sharps)
    private int defaultKeyAccidentalCount;
    private KeyType defaultKeyType = DEFAULT_KEY_TYPE;

    // When the title is set, it is wrapped into lines and stored here
    private final ArrayList<String> titleLines = new ArrayList<>();

    private double topPaddingSs = 0;
    private boolean userSetTopPadding = false;
    private double attributionStartYSs;
    private double rowHeightAdjustmentSs = 0;

    // The width of a staff line in staff-space units
    private double lineWidthSs = defaultLineWidthProvider.getAsDouble();

    // The lines of the score
    private final ArrayList<Line> lines = new ArrayList<>();

    /**
     * Indicates whether this song has been dynamically laid out.
     * <p>
     * When reading a document:
     * - If false (legacy document): ignore xPos values (they were absolute positions)
     * - If true (new document): read xPos values as relative offsets
     * <p>
     * When saving: always set to true.
     */
    private boolean hasBeenDynamicallyLaidOut = false;

    /**
     * Data format version for the song's internal representation.
     * <p>
     * <ul>
     *   <li>Version 1: Legacy format (inline range data, inline Note attachments)</li>
     *   <li>Version 2: LineElement format (RangeElement objects, Attachment objects)</li>
     * </ul>
     * <p>
     * Note: This is distinct from the IO file format version (SongIO.IO_MAJOR_VERSION).
     * The file format may remain compatible while the in-memory representation is migrated.
     * <p>
     * Default is 1 for newly loaded songs (before migration).
     * FormatMigrator sets this to 2 after migration.
     */
    private int formatVersion = 1;

    private boolean modified;

    // Modification bracket depth counter. Mutations are accumulated while > 0 and
    // flushed as a single SongDidChangeNotification when depth returns to 0.
    private int modificationDepth = 0;

    // Suspension depth counter. While > 0, Line.applyChange bypasses the strict
    // bracket check and runs the mutator directly. Used by test setup that
    // populates lines without emitting notifications or recording undo history.
    private int suspensionDepth = 0;

    // While true, Line mutation guards are bypassed so Song can auto-maintain
    // the terminal invariant without triggering the guards that protect against
    // user-driven invariant violations.
    private boolean autoMaintenance;

    @Nullable
    private ArrayList<Mutation> accumulatedMutations;


    public Song() {
        attribution = Strings.get(Strings.SONG_DEFAULT_ATTRIBUTION);
        tempo = new Tempo();
        defaultKeyAccidentalCount = DEFAULT_KEY_ACCIDENTAL_COUNT;

        // Initial topPadding of 0 - layout calculation will set the correct value
        // attributionStartY is calculated from title, will be recalculated on layout
        attributionStartYSs = calculateAttributionStartY();

        // Suspend mutation tracking so that setup changes don't post a spurious
        // SongDidChangeNotification to global subscribers before this Song is
        // installed in any ScoreView.
        withoutMutationTracking(() -> {
            var initialLine = new Line(this);
            initialLine.setKeyAccidentalCount(defaultKeyAccidentalCount);
            initialLine.setKeyType(defaultKeyType);
            initialLine.setTempoChangeYPosPx(
                ScaleContext.ssToRoundedPx(TEMPO_DEFAULT_Y_FIRST_LINE_SS)
            );
            initialLine.addElement(newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE));
            lines.add(initialLine);
        });

        MessageCenter.subscribe(this);
    }

    /**
     * Loading constructor. Applies the parsed data and subscribes to the message bus.
     * Avoids the wasted work of the no-arg constructor (default line, attributionStartY calculation).
     */
    public Song(SongData data) {
        loadFrom(data);
        MessageCenter.subscribe(this);
    }

    /**
     * Creates a minimal stub Song for use during file parsing. All fields stay at defaults
     * until {@link #loadFrom(SongData)} is called with the real parsed data.
     * This avoids the double {@code loadFrom} that would result from using
     * {@link #Song(SongData)} with an empty snapshot.
     */
    public static Song newParsingStub() {
        return new Song(Stub.INSTANCE);
    }

    private enum Stub { INSTANCE }

    private Song(Stub ignored) {
        MessageCenter.subscribe(this);
    }

    @Handler
    public void documentWasSaved(DocumentWasSavedNotification message) {
        modified = false;
    }

    /**
     * Applies all fields from a {@link SongData} snapshot atomically.
     * Does not post any notification — the caller ({@code ScoreView.setSong})
     * posts a {@link songscribe.message.notification.DocumentDidLoadNotification}
     * after the song is fully installed.
     * <p>
     * Called by {@link SongIO.DocumentReader#getSong()}
     * after parsing a file. This is a direct method call rather than a message
     * handler because loading targets a specific Song instance.
     *
     * @param data the parsed song data to apply
     */
    @SuppressWarnings("CallToSimpleSetterFromWithinClass")
    public void loadFrom(SongData data) {
        // Apply all scalar fields using apply methods (no individual message posting)
        tempo = data.tempo();
        applyNumber(data.number());
        applyTitle(data.title());
        applyPlace(data.place());
        applyMonth(data.month());
        applyDay(data.day());
        applyYear(data.year());
        applyUnderLyrics(data.underLyrics());
        applyBanglaLyrics(data.banglaLyrics());
        applyTranslatedLyrics(data.translatedLyrics());
        applyAttribution(data.attribution());
        applyFootnotes(data.footnotes());
        applyUnofficialTranslation(data.unofficialTranslation());
        applyDefaultKeyAccidentalCount(data.defaultKeyAccidentalCount());
        applyDefaultKeyType(data.defaultKeyType());

        // Apply layout
        applyTopPaddingSs(data.topPaddingSs(), false);
        applyAttributionStartYSs(data.attributionStartYSs());
        applyRowHeightAdjustmentSs(data.rowHeightAdjustmentSs());
        applyLineWidthSs(data.lineWidthSs());

        // Replace lines. Mutation tracking is suspended by the caller for the
        // duration of parsing and loadFrom, so applyChange does not post notifications.
        lines.clear();

        var loadedLines = data.lines();

        for (var lineIndex = 0; lineIndex < loadedLines.size(); lineIndex++) {
            lines.add(getLine(loadedLines, lineIndex));
        }

        // Loading bypasses the addElement path that normally handles this.
        if (!lines.isEmpty()) {
            lines.getFirst().attachInitialTempoIfNeeded();
        }

        hasBeenDynamicallyLaidOut = data.hasBeenDynamicallyLaidOut();
        formatVersion = data.formatVersion();

        // Loaded file starts unmodified
        modified = false;

        // Note: SongChanged(FULL) is NOT posted here because the
        // song hasn't been installed into ScoreView yet. ScoreView.setSong()
        // posts the FULL message after all state is consistent.
    }

    private Line getLine(List<? extends Line> loadedLines, int lineIndex) {
        var line = loadedLines.get(lineIndex);
        applyLineDefaults(line, lineIndex);
        return line;
    }

    private void applyLineDefaults(Line line, int lineIndex) {
        if ((line.getKeyAccidentalCount() == 0) && (line.getKeyType() == null)) {
            line.setKeyAccidentalCount(defaultKeyAccidentalCount);
            line.setKeyType(defaultKeyType);
        }

        if (line.getTempoChangeYPosPx() == 0) {
            line.setTempoChangeYPosPx(
                (lineIndex == 0)
                    ? ScaleContext.ssToRoundedPx(TEMPO_DEFAULT_Y_FIRST_LINE_SS)
                    : ScaleContext.ssToRoundedPx(TEMPO_DEFAULT_Y_OTHER_LINES_SS)
            );
        }
    }

    // ========== Getters (public, read-only API) ==========

    @Nullable
    public Tempo getTempo() {
        return tempo;
    }

    /**
     * Returns the song's tempo, or a default 120bpm crotchet tempo if none is set.
     * Use this in playback and export contexts where a non-null tempo is required.
     */
    public Tempo getEffectiveTempo() {
        return tempo != null ? tempo : new Tempo();
    }

    /**
     * Returns the effective tempo at a given position in the song.
     * Walks backwards through lines and notes to find the most recent tempo change,
     * or returns the effective song tempo if none found.
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
                var attachment = currentLine.getElement(n).findAttachment(TempoChangeAttachment.class);

                if (attachment != null) {
                    return attachment.getTempo();
                }
            }

            lastLine = false;
        }

        return getEffectiveTempo();
    }

    /**
     * Returns true if any element anywhere in the song carries a tempo change.
     */
    public boolean hasAnyTempoChange() {
        for (var line : lines) {
            for (var i = 0; i < line.elementCount(); i++) {
                if (line.getElement(i).findAttachment(TempoChangeAttachment.class) != null) {
                    return true;
                }
            }
        }

        return false;
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

    /**
     * Returns a syllabified-style text assembled from all per-note {@link Lyric} records.
     * Returns an empty string when no per-note lyrics are set.
     */
    public String getLyricsText() {
        var sb = new StringBuilder(1000);

        for (var i = 0; i < lines.size(); i++) {
            var line = lines.get(i);

            for (var j = 0; j < line.effectiveElementCount(); j++) {
                var lyric = line.getElement(j).getMainLyric();

                if (lyric == null) {
                    continue;
                }

                sb.append(lyric.text());

                if (lyric.extend() != Lyric.Extend.NONE) {
                    sb.append('_');
                } else {
                    if (lyric.compound()) {
                        sb.append("--");
                    } else {
                        var syllabic = lyric.syllabic();
                        sb.append(syllabic == Lyric.Syllabic.BEGIN || syllabic == Lyric.Syllabic.MIDDLE
                            ? '-' : ' ');
                    }
                }
            }

            if (i < lines.size() - 1 && !sb.isEmpty()) {
                sb.append('\n');
            }
        }

        return sb.toString();
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

    public String getAttribution() {
        return attribution;
    }

    public String getNumber() {
        return number;
    }

    public int getDefaultKeyAccidentalCount() {
        return defaultKeyAccidentalCount;
    }

    public KeyType getDefaultKeyType() {
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
        return ScaleContext.ssToRoundedPx(lineWidthSs);
    }

    public boolean hasBeenDynamicallyLaidOut() {
        return hasBeenDynamicallyLaidOut;
    }

    public int getFormatVersion() {
        return formatVersion;
    }

    // ========== Setters (mutate + setModified + post) ==========

    public void setTempo(@Nullable Tempo tempo) {
        mutateMetadata(MetadataField.TEMPO, this.tempo, tempo, () -> this.tempo = tempo);
    }

    /**
     * Clears the song-level initial tempo if {@code element} losing its tempo change
     * would orphan it. The song-level tempo is mirrored onto the first note of the
     * first line on every reload by {@code attachInitialTempoIfNeeded}, so it must be
     * cleared when:
     * <ul>
     *   <li>{@code element} is the first element of the first line (the only place the
     *       initial tempo is anchored), or</li>
     *   <li>no per-note tempo changes remain anywhere — otherwise the song-level tempo
     *       would be re-attached to the first note on the next reload.</li>
     * </ul>
     */
    public void clearTempoIfOrphaned(StaffElement element) {
        var line = element.getLine();
        var isFirstElement = indexOfLine(line) == 0 && line.getElementIndex(element) == 0;

        if (isFirstElement || !hasAnyTempoChange()) {
            setTempo(null);
        }
    }

    public void setTitle(String text) {
        var newTitle = normalizeTitle(text);
        mutateMetadata(MetadataField.TITLE, title, newTitle, () -> title = newTitle);
    }

    public void setPlace(String text) {
        var newPlace = text.trim();
        mutateMetadata(MetadataField.PLACE, place, newPlace, () -> place = newPlace);
    }

    public void setYear(String text) {
        var newYear = text.trim();
        mutateMetadata(MetadataField.YEAR, year, newYear, () -> year = newYear);
    }

    public void setMonth(int month) {
        mutateMetadata(MetadataField.MONTH, this.month, month, () -> this.month = month);
    }

    public void setDay(int day) {
        mutateMetadata(MetadataField.DAY, this.day, day, () -> this.day = day);
    }

    public void setUnderLyrics(String text) {
        var newLyrics = processText(text);
        mutateLyrics(LyricsField.UNDER, underLyrics, newLyrics, () -> underLyrics = newLyrics);
    }

    public void setBanglaLyrics(String text) {
        var newLyrics = text.trim();
        mutateLyrics(LyricsField.BANGLA, banglaLyrics, newLyrics, () -> banglaLyrics = newLyrics);
    }

    public void setTranslatedLyrics(String text) {
        var newLyrics = text.trim();
        mutateLyrics(LyricsField.TRANSLATED, translatedLyrics, newLyrics, () -> translatedLyrics = newLyrics);
    }

    public void setFootnotes(String text) {
        var newFootnotes = text.trim();
        mutateMetadata(MetadataField.FOOTNOTES, footnotes, newFootnotes, () -> footnotes = newFootnotes);
    }

    public void setUnofficialTranslation(boolean unofficial) {
        mutateMetadata(
            MetadataField.UNOFFICIAL_TRANSLATION, unofficialTranslation, unofficial,
            () -> unofficialTranslation = unofficial
        );
    }

    public void setAttribution(String text) {
        var newAttribution = text.trim();
        mutateMetadata(MetadataField.ATTRIBUTION, attribution, newAttribution, () -> attribution = newAttribution);
    }

    public void setNumber(String text) {
        var newNumber = text.trim();
        mutateMetadata(MetadataField.NUMBER, number, newNumber, () -> number = newNumber);
    }

    public void setDefaultKeyAccidentalCount(int defaultKeyAccidentalCount) {
        mutateMetadata(
            MetadataField.DEFAULT_KEY_ACCIDENTAL_COUNT, this.defaultKeyAccidentalCount, defaultKeyAccidentalCount,
            () -> this.defaultKeyAccidentalCount = defaultKeyAccidentalCount
        );
    }

    public void setDefaultKeyType(KeyType defaultKeyType) {
        mutateMetadata(
            MetadataField.DEFAULT_KEY_TYPE, this.defaultKeyType, defaultKeyType,
            () -> this.defaultKeyType = defaultKeyType
        );
    }

    // -- Layout setters --

    /**
     * Unlike the other layout setters, this always runs the apply block because
     * {@code setByUser} flows into the sticky {@code userSetTopPadding} flag —
     * a unchanged-padding update from user still needs to set that flag.
     */
    public void setTopPaddingSs(double padding, boolean setByUser) {
        var old = topPaddingSs;
        withModification(() -> applyChange(
            new LayoutChange(LayoutField.TOP_PADDING_SS, old, padding),
            () -> applyTopPaddingSs(padding, setByUser)
        ));
    }

    public void setAttributionStartYSs(double attributionStartY) {
        mutateLayout(
            LayoutField.ATTRIBUTION_START_Y_SS, attributionStartYSs, attributionStartY,
            () -> attributionStartYSs = attributionStartY
        );
    }

    public void setRowHeightAdjustmentSs(double rowHeightAdjustment) {
        mutateLayout(
            LayoutField.ROW_HEIGHT_ADJUSTMENT_SS, rowHeightAdjustmentSs, rowHeightAdjustment,
            () -> rowHeightAdjustmentSs = rowHeightAdjustment
        );
    }

    /**
     * Do not call this directly unless you know what you are doing.
     * Instead, use scoreView.setLineWidth.
     */
    public void setLineWidthSs(double lineWidth) {
        mutateLayout(LayoutField.LINE_WIDTH_SS, lineWidthSs, lineWidth, () -> lineWidthSs = lineWidth);
    }

    // -- Setter helpers --

    /**
     * Early-returns if {@code current} and {@code newValue} are equal; otherwise
     * opens a bracket and emits a {@link MetadataChange} recording the change.
     * Autoboxing applies for primitive callers.
     */
    private void mutateMetadata(
        MetadataField field, @Nullable Object current, @Nullable Object newValue, Runnable apply
    ) {
        if (Objects.equals(current, newValue)) {
            return;
        }

        withModification(() -> applyChange(new MetadataChange(field, current, newValue), apply));
    }

    private void mutateLayout(LayoutField field, double current, double newValue, Runnable apply) {
        if (current == newValue) {
            return;
        }

        withModification(() -> applyChange(new LayoutChange(field, current, newValue), apply));
    }

    private void mutateLyrics(LyricsField field, String current, String newValue, Runnable apply) {
        if (current.equals(newValue)) {
            return;
        }

        withModification(() -> applyChange(new LyricsChange(field, current, newValue), apply));
    }

    // -- Structure setters --

    public void addLine(Line line) {
        addLine(APPEND, line);
    }

    /**
     * Inserts {@code line} at {@code index} and, when the insertion makes {@code line}
     * the new last line, transfers the terminal invariant so the last element of
     * the last line is always a valid terminal ({@code FINAL_DOUBLE_BARLINE} or
     * {@code REPEAT_RIGHT}). All resulting mutations
     * coalesce into a single {@link SongDidChangeNotification}.
     *
     * <p>The invariant transfer is skipped when mutation tracking is suspended
     * (see {@link #withoutMutationTracking}) — test setup can install lines with
     * arbitrary terminal elements.
     *
     * <pre>
     *  withModification {
     *    incrementAutoMaintenance {
     *      applyChange(LineInsertion(index, line), …)
     *
     *      if (line became the new last line) {
     *        if (prevLast.lastElement == FINAL_DOUBLE_BARLINE)
     *          applyChange(ElementDeletion on prevLast, …)
     *
     *        switch (line.lastElement) {
     *          FINAL   → no-op
     *          barline → applyChange(ElementReplacement …)
     *          non-bar → applyChange(ElementInsertion …)
     *          empty   → applyChange(ElementInsertion …)
     *        }
     *      }
     *    }
     *  }
     * </pre>
     */
    public void addLine(int index, Line line) {
        if (line.getSong() != this) {
            throw new IllegalArgumentException("Line must be constructed with this Song");
        }

        var lineIndex = (index == APPEND) ? lines.size() : index;
        var willBecomeNewLast = lineIndex == lines.size();
        var previousLastLine = lines.isEmpty() ? null : lines.getLast();

        withModification(() -> incrementAutoMaintenance(() -> {
            applyChange(new LineInsertion(lineIndex, line), () -> {
                lines.add(lineIndex, line);

                applyLineDefaults(line, lineIndex);
            });

            if (willBecomeNewLast && !isMutationTrackingSuspended()) {
                maintainTerminalOnLastLineChange(previousLastLine, line);
            }
        }));
    }

    /**
     * Removes the line at {@code index} and, when the removed line was the last line,
     * installs the terminal on the new last line so the invariant holds. All
     * resulting mutations coalesce into a single {@link SongDidChangeNotification}.
     *
     * <p>The invariant transfer is skipped when mutation tracking is suspended
     * (see {@link #withoutMutationTracking}).
     *
     * <pre>
     *  withModification {
     *    incrementAutoMaintenance {
     *      applyChange(LineDeletion(index, removed), …)
     *
     *      if (removed line was the last line) {
     *        let penult = lines.last
     *        switch (penult.lastElement) {
     *          FINAL   → no-op
     *          barline → applyChange(ElementReplacement …)
     *          non-bar → applyChange(ElementInsertion …)
     *          empty   → applyChange(ElementInsertion …)
     *        }
     *      }
     *    }
     *  }
     * </pre>
     */
    public void removeLine(int index) {
        var deletedLine = lines.get(index);
        var wasLast = index == lines.size() - 1;

        withModification(() -> incrementAutoMaintenance(() -> {
            applyChange(
                new LineDeletion(index, deletedLine),
                () -> lines.remove(index)
            );

            if (wasLast && !lines.isEmpty() && !isMutationTrackingSuspended()) {
                maintainTerminalOnLastLineChange(null, lines.getLast());
            }
        }));
    }

    /**
     * Maintains the terminal invariant after the last line of the song has
     * changed: the last element of the last line must be a valid terminal
     * ({@code FINAL_DOUBLE_BARLINE} or {@code REPEAT_RIGHT}). Must be called inside
     * an open modification bracket with {@link #incrementAutoMaintenance} raised so
     * the {@link Line} guards do not reject the internally-driven mutations.
     *
     * <p>Determines the terminal type to install via {@link #terminalTypeToInstall}
     * (carry over the outgoing terminal; else promote an existing {@code REPEAT_RIGHT}
     * on the new last line; else default to {@code FINAL_DOUBLE_BARLINE}). Strips the
     * terminal element — either type — off {@code previousLastLine}, then installs
     * the chosen type on {@code newLastLine}: no-op when {@code FINAL_DOUBLE_BARLINE}
     * is already in place, replacement when the existing last element is bar-like or
     * a {@code REPEAT_RIGHT} being promoted in place, append otherwise (including the
     * empty-line case).
     */
    private void maintainTerminalOnLastLineChange(
        @Nullable Line previousLastLine, Line newLastLine
    ) {
        var outgoingTerminalType = outgoingTerminalType(previousLastLine, newLastLine);
        var typeToInstall = terminalTypeToInstall(outgoingTerminalType, newLastLine);

        //noinspection ConstantValue
        if (outgoingTerminalType != null && previousLastLine != null) {
            previousLastLine.removeElement(previousLastLine.elementCount() - 1);
        }

        var lastIdx = newLastLine.elementCount() - 1;

        if (lastIdx < 0) {
            newLastLine.addElement(newTerminalElement(typeToInstall));
            return;
        }

        var lastType = newLastLine.getElement(lastIdx).getType();

        // FINAL_DOUBLE_BARLINE is guard-locked to end-of-last-line, so when it is
        // already in place and is the type we want to install, no semantic change is
        // required. A REPEAT_RIGHT already sitting here, by contrast, is an interior
        // right-repeat being promoted to the terminal — fall through so the
        // replacement below emits an ElementReplacement for undo.
        if (lastType == ElementType.FINAL_DOUBLE_BARLINE
            && typeToInstall == ElementType.FINAL_DOUBLE_BARLINE) {
            return;
        }

        if (lastType.isReplaceableByTerminal()) {
            newLastLine.setElement(lastIdx, newTerminalElement(typeToInstall));
        } else {
            newLastLine.addElement(newTerminalElement(typeToInstall));
        }
    }

    /**
     * Returns the terminal type at the end of {@code previousLastLine} if it is the
     * outgoing terminal (non-null, distinct from {@code newLastLine}, and ends in a
     * valid terminal). Returns {@code null} otherwise.
     */
    @Nullable
    private static ElementType outgoingTerminalType(
        @Nullable Line previousLastLine, Line newLastLine
    ) {
        if (previousLastLine == null || previousLastLine == newLastLine) {
            return null;
        }

        var prevLastIdx = previousLastLine.elementCount() - 1;

        if (prevLastIdx < 0) {
            return null;
        }

        var type = previousLastLine.getElement(prevLastIdx).getType();
        return type.isValidTerminal() ? type : null;
    }

    /**
     * Determines which terminal type {@link #maintainTerminalOnLastLineChange} should
     * install on the new last line. Decision tree:
     * <ol>
     *   <li>If {@code outgoingTerminalType} is non-null, carry it over — preserves
     *       user intent across {@code addLine} / {@code removeLine}.
     *   <li>Otherwise, if the new last line already ends in a {@code REPEAT_RIGHT}
     *       (user-placed interior right-repeat), promote it in place.
     *   <li>Otherwise, default to {@code FINAL_DOUBLE_BARLINE}.
     * </ol>
     */
    private static ElementType terminalTypeToInstall(
        @Nullable ElementType outgoingTerminalType, Line newLastLine
    ) {
        if (outgoingTerminalType != null) {
            return outgoingTerminalType;
        }

        var lastIdx = newLastLine.elementCount() - 1;

        if (lastIdx >= 0
            && newLastLine.getElement(lastIdx).getType() == ElementType.REPEAT_RIGHT) {
            return ElementType.REPEAT_RIGHT;
        }

        return ElementType.FINAL_DOUBLE_BARLINE;
    }

    /**
     * Returns {@code true} while a modification bracket is open.
     * Package-private so {@link Line#applyChange} can check without exposing the depth counter.
     */
    public boolean isModifying() {
        return modificationDepth > 0;
    }

    /** Returns {@code true} while mutation tracking is suspended. */
    public boolean isMutationTrackingSuspended() {
        return suspensionDepth > 0;
    }

    boolean isInAutoMaintenance() {
        return autoMaintenance;
    }

    /**
     * Runs {@code body} with the auto-maintenance flag raised so that the terminal
     * guards in {@link Line} are bypassed for the duration. Used by {@link #addLine} and
     * {@link #removeLine} to transfer the terminal without triggering the guard.
     */
    private void incrementAutoMaintenance(Runnable body) {
        autoMaintenance = true;

        try {
            body.run();
        } finally {
            autoMaintenance = false;
        }
    }

    /**
     * Returns a fresh element of the given terminal type. Throws
     * {@link IllegalArgumentException} if {@code type} is not a valid terminal
     * (i.e. {@link ElementType#isValidTerminal()} returns {@code false}).
     */
    static StaffElement newTerminalElement(ElementType type) {
        if (!type.isValidTerminal()) {
            throw new IllegalArgumentException("Not a valid terminal type: " + type);
        }

        return type.newInstance();
    }

    /**
     * Returns {@code true} when {@code element} is the song's auto-maintained
     * terminal: it occupies the last position of the last line, and its type satisfies
     * {@link ElementType#isValidTerminal()}.
     *
     * <p>A valid terminal type that sits on any line other than the last, or at any
     * position other than the last, is treated as an ordinary (interactable) element.
     */
    public boolean isAutoMaintainedTerminal(StaffElement element, Line line) {
        var lastIdx = line.elementCount() - 1;
        return lastIdx >= 0
            && element.getType().isValidTerminal()
            && !lines.isEmpty()
            && lines.getLast() == line
            && line.getElement(lastIdx) == element;
    }

    /**
     * Returns {@code true} when the user may interact with {@code element} on {@code line}
     * (select, click, drag, delete, etc.). Returns {@code false} only for the
     * song's auto-maintained terminal — i.e., a {@link ElementType#isValidTerminal()
     * valid terminal} element that is the last element of the last line.
     */
    public boolean isInteractable(StaffElement element, Line line) {
        return !isAutoMaintainedTerminal(element, line);
    }

    /** Returns the type of the current auto-maintained terminal element. */
    public ElementType currentTerminalType() {
        var lastLine = lines.getLast();
        var lastIdx = lastLine.elementCount() - 1;

        if (lastIdx < 0) {
            throw RuntimeError.exit("Terminal invariant violated: last line is empty");
        }

        return lastLine.getElement(lastIdx).getType();
    }

    /**
     * Returns {@code true} when the terminal may be replaced with an element of the given
     * type: {@code incomingType} must be a valid terminal and must differ from the type
     * currently occupying the terminal slot.
     */
    public boolean canReplaceTerminal(ElementType incomingType) {
        return incomingType.isValidTerminal() && incomingType != currentTerminalType();
    }

    /**
     * Replaces the terminal element with a fresh element of {@code incomingType}.
     * This is a user-driven mutation — no auto-maintenance increment. No-op when
     * {@code incomingType} already matches the current terminal type. Throws
     * {@link IllegalArgumentException} if {@code incomingType} is not a valid terminal.
     */
    public void replaceTerminal(ElementType incomingType) {
        if (!incomingType.isValidTerminal()) {
            throw new IllegalArgumentException("Not a valid terminal type: " + incomingType);
        }

        if (incomingType == currentTerminalType()) {
            return;
        }

        var lastLine = lines.getLast();
        var lastIdx = lastLine.elementCount() - 1;

        withModification(() -> lastLine.setElement(lastIdx, newTerminalElement(incomingType)));
    }

    /**
     * Runs {@code body} with mutation tracking suspended. Line-level mutations
     * invoked during {@code body} run silently: no notification is posted, no
     * undo entry is recorded, and the song's {@code modified} flag is
     * not set.
     * <p>
     * Intended for test setup that populates lines outside a user-driven
     * modification bracket. Production code should use
     * {@link #withModification(Runnable)} instead.
     */
    public void withoutMutationTracking(Runnable body) {
        beginSuspendMutationTracking();

        try {
            body.run();
        } finally {
            endSuspendMutationTracking();
        }
    }

    /**
     * Suspends mutation tracking until the matching {@link #endSuspendMutationTracking()}.
     * Use {@link #withoutMutationTracking(Runnable)} when the suspended scope fits in a
     * single block; this pair exists for callers (e.g. SAX parsing) whose suspension
     * scope crosses multiple methods.
     */
    public void beginSuspendMutationTracking() {
        suspensionDepth++;
    }

    /**
     * Resumes mutation tracking. Must be paired with a prior
     * {@link #beginSuspendMutationTracking()} call; calls without a matching
     * begin are a programming error and throw immediately.
     */
    public void endSuspendMutationTracking() {
        if (suspensionDepth <= 0) {
            throw new IllegalStateException("No matching beginSuspendMutationTracking");
        }

        suspensionDepth--;
    }

    /**
     * Opens a modification bracket. Mutations accumulate while the bracket is open.
     * Brackets may be nested; the notification fires only when the outermost bracket closes.
     */
    public void beginModification() {
        // TODO: snapshot song state here for undo grouping (#14)
        modificationDepth++;
    }

    /**
     * Closes a modification bracket. When the outermost bracket closes and at least one
     * mutation was accumulated, marks the song modified and posts a single
     * {@link SongDidChangeNotification} carrying all accumulated mutations.
     */
    public void endModification() {
        modificationDepth--;

        if (modificationDepth == 0 && accumulatedMutations != null) {
            modified = true;
            // Wrap-and-transfer ownership: the notification constructor stores the
            // list directly, so we wrap once here instead of letting it defensively
            // copy a list whose only reference is about to be dropped.
            var mutations = Collections.unmodifiableList(accumulatedMutations);
            accumulatedMutations = null;
            MessageCenter.post(new SongDidChangeNotification(mutations, this));
        }
    }

    /**
     * Executes {@code body} inside a modification bracket, then posts a single
     * {@link SongDidChangeNotification} with all accumulated mutations.
     * Prefer this over {@link #beginModification()} / {@link #endModification()} to ensure
     * the depth counter is always balanced even if {@code body} throws.
     */
    public void withModification(Runnable body) {
        beginModification();

        try {
            body.run();
        } finally {
            endModification();
        }
    }

    /**
     * Posts {@code message} to the message bus inside a modification bracket so
     * that the resulting mutations (from subscribers like {@code Song}'s
     * own {@code @Handler} methods) coalesce into a single
     * {@link SongDidChangeNotification}. Equivalent to
     * {@code withModification(() -> MessageCenter.post(message))} but cleaner
     * at the call site.
     */
    public void postWithModification(Message message) {
        withModification(() -> MessageCenter.post(message));
    }

    /**
     * Applies a single mutation within an open modification bracket.
     * <p>
     * Runs {@code mutator}, then records {@code mutation} in the accumulated list.
     * <p>
     * <pre>
     * withModification(() -&gt; {                              ┐
     *   │                                                    │
     *   ├─ applyChange(mutation₁, mutator₁)                 │ caller's
     *   │     ├─ throws if depth == 0                       │ bracket
     *   │     ├─ mutator₁.run()                             │
     *   │     └─ accumulatedMutations.add(mutation₁)        │
     *   │                                                    │
     *   ├─ applyChange(mutation₂, mutator₂)                 │
     *   │     └─ ...                                         │
     *   │                                                    │
     * })  // bracket closes                                  │
     *   ├─ depth → 0                                         │
     *   ├─ push undo entry (future, #14)                     │
     *   └─ post SongDidChangeNotification(accumulated)┘
     * </pre>
     *
     * @throws IllegalStateException if called outside a modification bracket
     */
    public void applyChange(Mutation mutation, Runnable mutator) {
        if (modificationDepth == 0) {
            throw new IllegalStateException("applyChange called outside a modification bracket");
        }

        mutator.run();

        if (accumulatedMutations == null) {
            accumulatedMutations = new ArrayList<>();
        }

        accumulatedMutations.add(mutation);
    }

    // -- IO/internal setters (remain public, no message posting) --

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    /**
     * Sets whether this song has been dynamically laid out.
     * <p>
     * Should be set to true when saving.
     *
     * @param hasBeenDynamicallyLaidOut true if dynamically laid out
     */
    public void setHasBeenDynamicallyLaidOut(boolean hasBeenDynamicallyLaidOut) {
        this.hasBeenDynamicallyLaidOut = hasBeenDynamicallyLaidOut;
    }

    /**
     * Sets the data format version of this song.
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
    public void metadataDidChange(MetadataDidChangeNotification update) {
        withModification(() -> {
            if (update.getTitle() != null) {
                setTitle(update.getTitle());
            }

            if (update.getPlace() != null) {
                setPlace(update.getPlace());
            }

            if (update.getYear() != null) {
                setYear(update.getYear());
            }

            if (update.getNumber() != null) {
                setNumber(update.getNumber());
            }

            if (update.getAttribution() != null) {
                setAttribution(update.getAttribution());
            }

            if (update.getMonth() != null) {
                setMonth(update.getMonth());
            }

            if (update.getDay() != null) {
                setDay(update.getDay());
            }

            if (update.getUnofficialTranslation() != null) {
                setUnofficialTranslation(update.getUnofficialTranslation());
            }
        });
    }

    @Handler
    public void tempoDidChange(TempoDidChangeNotification update) {
        // Skip the clone+mutation when the update record carries no actual fields.
        if (update.getTempoType() == null
            && update.getVisibleTempo() == null
            && update.getTempoDescription() == null
            && update.getShowTempo() == null) {
            return;
        }

        // If the song has no tempo yet, initialize one so the handler can mutate it.
        if (tempo == null) {
            tempo = new Tempo();
        }

        // Capture in a local so the lambda can reference it without NullAway complaints
        // (NullAway cannot track @Nullable field non-nullness across lambda boundaries).
        var currentTempo = tempo;

        // Clone the old tempo before mutating it in place so the mutation record
        // carries a stable before-state (option (a) from the Phase 3a audit).
        var oldTempo = new Tempo(
            currentTempo.getVisibleTempo(),
            currentTempo.getTempoType(),
            currentTempo.getTempoDescription(),
            currentTempo.shouldShowTempo()
        );
        withModification(() -> applyChange(
            new MetadataChange(MetadataField.TEMPO, oldTempo, currentTempo),
            () -> {
                if (update.getTempoType() != null) {
                    currentTempo.setTempoType(update.getTempoType());
                }

                if (update.getVisibleTempo() != null) {
                    currentTempo.setVisibleTempo(update.getVisibleTempo());
                }

                if (update.getTempoDescription() != null) {
                    currentTempo.setTempoDescription(update.getTempoDescription());
                }

                if (update.getShowTempo() != null) {
                    currentTempo.setShowTempo(update.getShowTempo());
                }
            }
        ));
    }

    @Handler
    public void keySignatureDidChange(KeySignatureDidChangeNotification update) {
        withModification(() -> {
            if (update.getLineIndex() == null) {
                // Song-level default with propagation to matching lines.
                var oldKeyType = defaultKeyType;
                var oldAccidentalCount = defaultKeyAccidentalCount;

                setDefaultKeyType(update.getKeyType());
                setDefaultKeyAccidentalCount(update.getAccidentalCount());

                for (var i = 0; i < lineCount(); i++) {
                    var line = getLine(i);

                    if (line.getKeyAccidentalCount() == oldAccidentalCount
                        && line.getKeyType() == oldKeyType) {
                        line.setKeyAccidentalCount(defaultKeyAccidentalCount);
                        line.setKeyType(defaultKeyType);
                    }
                }
            } else {
                // Per-line update.
                var line = getLine(update.getLineIndex());
                line.setKeyType(update.getKeyType());
                line.setKeyAccidentalCount(update.getAccidentalCount());
            }
        });
    }

    @Handler
    public void layoutDidChange(LayoutDidChangeNotification update) {
        withModification(() -> {
            if (update.getTopPaddingSs() != null) {
                var setByUser = update.getTopPaddingSetByUser() != null && update.getTopPaddingSetByUser();
                setTopPaddingSs(update.getTopPaddingSs(), setByUser);
            }

            if (update.getRowHeightAdjustmentSs() != null) {
                setRowHeightAdjustmentSs(update.getRowHeightAdjustmentSs());
            }

            if (update.getLineWidthSs() != null) {
                setLineWidthSs(update.getLineWidthSs());
            }

            if (update.getAttributionStartYSs() != null) {
                setAttributionStartYSs(update.getAttributionStartYSs());
            }
        });
    }

    // ========== Private helpers ==========

    // -- Apply methods (field mutation only, no side effects) --

    private void applyTitle(String text) {
        title = normalizeTitle(text);
    }

    /** Normalizes a title: strips linefeeds, collapses spaces, applies short-ă replacement. */
    private String normalizeTitle(String text) {
        return processText(StringUtils.collapseMultipleSpaces(StringUtils.stripLinefeeds(text)));
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
        defaultKeyType = keyType;
    }

    private void applyDefaultKeyAccidentalCount(int count) {
        defaultKeyAccidentalCount = count;
    }

    private void applyTopPaddingSs(double padding, boolean setByUser) {
        topPaddingSs = padding;
        userSetTopPadding = userSetTopPadding || setByUser;
    }

    private void applyAttributionStartYSs(double attributionStartY) {
        attributionStartYSs = attributionStartY;
    }

    private void applyRowHeightAdjustmentSs(double rowHeightAdjustment) {
        rowHeightAdjustmentSs = rowHeightAdjustment;
    }

    private void applyLineWidthSs(double lineWidth) {
        lineWidthSs = lineWidth;
    }

    private String processText(String text) {
        var strip = Prefs.getBoolean(PrefsKey.STRIP_SHORT_A);

        if (strip && SHORT_A_PATTERN.matcher(text).find()) {
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
        // We want the attribution to start half of the song title font size below the song title.
        // Uses prefs defaults since this is only called during Song construction, before any
        // document fonts are available on ScoreView.
        var lineCount = Utils.lineCount(title);
        var titleFont = MyFontUtils.createFont(Prefs.getString(PrefsKey.TITLE_FONT), Prefs.getInt(PrefsKey.TITLE_FONT_SIZE));
        var lineHeight = MyFontUtils.getFontMetrics(titleFont).getHeight();
        return (lineHeight * lineCount) + (lineHeight / 2.0);
    }

}
