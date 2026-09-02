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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import net.engio.mbassy.listener.Handler;
import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.io.SongIO;
import songscribe.lifecycle.Disposable;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.SongData;
import songscribe.message.mutation.ElementDeletion;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.ElementModification;
import songscribe.message.mutation.ElementRangeDeletion;
import songscribe.message.mutation.ElementReplacement;
import songscribe.message.mutation.LayoutChange;
import songscribe.message.mutation.LayoutField;
import songscribe.message.mutation.LineDeletion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.LineKeyChange;
import songscribe.message.mutation.LyricsChange;
import songscribe.message.mutation.LyricsField;
import songscribe.message.mutation.MetadataChange;
import songscribe.message.mutation.MetadataField;
import songscribe.message.mutation.Mutation;
import songscribe.message.notification.DocumentWasSavedNotification;
import songscribe.message.notification.LayoutDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.SongMetadataDidChangeNotification;
import songscribe.message.notification.TempoDidChangeNotification;
import songscribe.message.notification.TupletsWereRemovedNotification;
import songscribe.util.StringUtils;

/**
 * This class serves as the model for data that is read from and written to
 * SongScribe files.
 *
 * <h2>Lifecycle</h2>
 * A {@code Song} subscribes itself to the message bus in every constructor, so it can
 * handle broadcast command notifications (metadata/tempo/key, etc.) for as long as it is
 * the active document. {@link #dispose()} detaches it. MBassador holds listeners by weak
 * reference, so a discarded {@code Song} keeps responding to broadcasts — and posting
 * spurious undo steps against the dead document — until it is garbage-collected;
 * {@code dispose()} makes the detach deterministic rather than GC-timing-dependent.
 * {@code ScoreView.setSong} calls it when replacing the active {@code Song}.
 *
 * <h2>Key invariant</h2>
 * A song has no key of its own — its key is line 0's. Across the line list, two things always
 * hold: line 0 establishes a key of its own ({@link Line#getKey()} is non-null), and every other
 * line's inherited key equals the key in effect at the end of the line before it. A {@code Song}
 * is what maintains both — see {@link #applyChange} for the editing path and
 * {@link #rebuildInheritedKeysAfterParsing()} for the loading one — which is why the rule is
 * stated here rather than on {@link Line}, which can only state what one line promises.
 *
 * <p>The inherited half lives here too, in {@link #inheritedKeys}, for the same reason: what a line
 * inherits is a fact about where it sits in this list, so the class that owns the list is the one
 * that can keep it true. {@link #runningKeyAt} is the query, and it is total — nothing about the
 * key chain can be broken badly enough to have no answer.
 */
public final class Song implements Disposable {

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

    public static final String SRI_CHINMOY = "Sri Chinmoy";

    public enum LyricsSource {
        LYRICIST(" by ", Strings.DIALOG_SONG_SETTINGS_SOURCE_LYRICIST),
        TEXT(" from ", Strings.DIALOG_SONG_SETTINGS_SOURCE_TEXT),
        OTHER(": ", Strings.DIALOG_SONG_SETTINGS_SOURCE_OTHER);

        private final String connector;
        private final String labelKey;

        LyricsSource(String connector, String labelKey) {
            this.connector = connector;
            this.labelKey = labelKey;
        }

        public String getConnector() {
            return connector;
        }

        @Override
        public String toString() {
            return Strings.get(labelKey);
        }
    }

    /** The position the song-level tempo defines its beat at — the very start of the song. */
    private static final int FIRST_LINE_INDEX = 0;
    private static final int FIRST_ELEMENT_INDEX = 0;

    /**
     * Default line-wide rest length (delta-X Ss between adjacent column origins) from which every
     * gap's ideal spacing is derived. Matches the legacy default column gap; a song may override it
     * to loosen or tighten the whole score proportionally (#330).
     */
    public static final double DEFAULT_REST_LENGTH_SS = 2.0;
    /**
     * Lower clamp for {@link #defaultRestLengthSs}: no rest is ever derived from a line rest tighter
     * than this. Tunable first cut.
     */
    public static final double MIN_DEFAULT_REST_LENGTH_SS = 1.0;

    // Sentinel passed to addLine(int, Line) meaning "append after the last line"
    private static final int APPEND = -1;

    /**
     * Line width used until the UI layer installs the preference-aware provider.
     * <p>
     * The real default comes from {@code PageModel.getDefaultLineWidthSs()}, which needs
     * {@code Prefs} and the screen DPI and so is unreachable from the model layer. This
     * constant stands in for it wherever a Song is built without the app running — the
     * corpus generator and unit tests — so such a Song is never born with an unlayoutable
     * zero-width staff. It is the Letter content area (8.5" page less 0.5" margins per
     * side) at the standard 96-DPI headless resolution, expressed in staff spaces, which
     * also keeps it within the page on higher-DPI displays.
     */
    public static final double FALLBACK_LINE_WIDTH_SS = 90.0;

    // Provides the default line width for new songs; set by the UI layer at startup
    private static DoubleSupplier defaultLineWidthProvider = () -> FALLBACK_LINE_WIDTH_SS;

    public static void setDefaultLineWidthProvider(DoubleSupplier provider) {
        defaultLineWidthProvider = provider;
    }

    // The base tempo of the song. Every song has one — Tempo's defaults until the user
    // changes it — so this is never null. Whether the header mark depicting it is drawn is
    // decided by the mark's computed content width, never by nullness.
    private Tempo tempo = new Tempo();

    // The canonical attribution metadata record — single source of truth for all
    // 11 attribution fields (title, number, place, year, month, day, composer,
    // lyricist, lyricsSource, arrangement, unofficialTranslation).
    private SongMetadata metadata = new SongMetadata(
        Strings.get(Strings.DOCUMENT_UNTITLED),  // title
        Strings.get(Strings.SONG_DEFAULT_NUMBER), // number
        "",    // place
        "",    // year
        0,     // month
        0,     // day
        SRI_CHINMOY,           // composer
        SRI_CHINMOY,           // lyricist
        LyricsSource.LYRICIST, // lyricsSource
        false,  // arrangement
        false,  // unofficialTranslation
        "",     // subtitle
        "",     // wordsYear
        0,      // wordsMonth
        0       // wordsDay
    );

    // The language of the song
    private final LANGUAGE language = LANGUAGE.NONE;

    // The full native lyrics of the song, displayed under the music
    private String underLyrics = "";

    // If the song is in Bengali, the lyrics in Bengali script
    private String banglaLyrics = "";

    // If the song is in a language other than English, the translated lyrics (if available)
    private String translatedLyrics = "";

    // Additional info about the song
    private String footnotes = "";

    // Block element carrying the attribution pane geometry and user Y offset for stacking
    private final Attribution attributionElement = new Attribution();

    // Stable identity for the song tempo mark drawn at line 0's staff header. Carries no
    // tempo of its own — see SongTempoMark.
    private final SongTempoMark tempoMarkElement = new SongTempoMark();

    private double rowHeightAdjustmentSs = 0;

    // Line-wide rest length driving derived column spacing (#330); persisted in the MusicXML
    // header. Clamped to at least MIN_DEFAULT_REST_LENGTH_SS on every set.
    private double defaultRestLengthSs = DEFAULT_REST_LENGTH_SS;

    // The width of a staff line in staff-space units
    private double lineWidthSs = defaultLineWidthProvider.getAsDouble();

    // The lines of the score
    private final ArrayList<Line> lines = new ArrayList<>();

    /**
     * The key in effect at the end of the line before each line — what that line inherits when it
     * establishes no key of its own. A line with no entry inherits nothing: line 0, and any line
     * that is not in {@link #lines}.
     *
     * <p>Keyed by identity because {@link Line} inherits {@code equals} from {@code Object} and two
     * distinct lines must never collide, however alike their contents.
     *
     * <p>This is state, not a cache: there is no invalidate-and-recompute path. Every mutation that
     * can move a key drives {@link #maintainKeyInvariant} instead, which patches the entries the
     * change actually reaches rather than rebuilding the whole map.
     */
    private final Map<Line, Key> inheritedKeys = new IdentityHashMap<>();

    // The verse the song is currently showing. A song's verses are the languages its lyrics are
    // written in, not stanzas to stack, so exactly one of them is laid out, painted and edited at
    // any moment. Deliberately not persisted: which language a reader last looked at is a property
    // of the session, not of the document, so every file opens on its first verse.
    private int activeVerse = Lyric.FIRST_VERSE;

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
     *   <li>Version 2: LineElement format (Span objects, Attachment objects)</li>
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

    // The mutation-recording state machine — every bracket, suspension, replay and
    // beat-defining-edit entry point on this class delegates here. Held rather than
    // inlined so the depth counters and the accumulated batch cannot be reached except
    // through the API that keeps them balanced.
    private final ModificationSession modifications = new ModificationSession(this);

    // Owns the terminal invariant (last element of the last line is a valid terminal).
    private final TerminalMaintainer terminal = new TerminalMaintainer(this);

    // Answers what tempo and what beat are in effect at a position.
    private final TempoResolver tempoResolver = new TempoResolver(this);

    public Song() {
        // Suspend mutation tracking so that setup changes don't post a spurious
        // SongDidChangeNotification to global subscribers before this Song is
        // installed in any ScoreView.
        withoutMutationTracking(() -> {
            var initialLine = new Line(this);
            initialLine.setKey(Key.DEFAULT);
            initialLine.addElement(newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE));
            lines.add(initialLine);
        });

        MessageCenter.subscribe(this);
    }

    /**
     * Loading constructor. Applies the parsed data and subscribes to the message bus.
     * Avoids the wasted work of the no-arg constructor (default line initialization).
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

    /**
     * Detaches this {@code Song} from the message bus. Idempotent. See the class's
     * {@code Lifecycle} section.
     */
    @Override
    public void dispose() {
        MessageCenter.unsubscribe(this);
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
        // Build the normalized attribution metadata record from the parsed data.
        // Assigned directly (no mutation bracket) because loadFrom is always called
        // under suspended mutation tracking (decision 8A).
        metadata = new SongMetadata(
            data.title(),
            data.number(),
            data.place(),
            data.year(),
            data.month(),
            data.day(),
            data.composer(),
            data.lyricist(),
            data.lyricsSource(),
            data.arrangement(),
            data.unofficialTranslation(),
            data.subtitle(),
            data.wordsYear(),
            data.wordsMonth(),
            data.wordsDay()
        );

        // Apply remaining scalar fields. A legacy .mssw file may carry no <tempo> element at
        // all, so the nullable parsed value is coalesced at the boundary — the song itself
        // always has a tempo.
        var loadedTempo = data.tempo();
        tempo = loadedTempo != null ? loadedTempo : new Tempo();
        applyUnderLyrics(data.underLyrics());
        applyBanglaLyrics(data.banglaLyrics());
        applyTranslatedLyrics(data.translatedLyrics());
        applyFootnotes(data.footnotes());

        // Apply layout
        applyRowHeightAdjustmentSs(data.rowHeightAdjustmentSs());
        applyLineWidthSs(data.lineWidthSs());

        // Replace lines. Mutation tracking is suspended by the caller for the
        // duration of parsing and loadFrom, so applyChange does not post notifications.
        lines.clear();

        var loadedLines = data.lines();

        lines.addAll(loadedLines);

        // The per-mutation propagation hook never ran: parsing is done under suspended tracking,
        // so no line's key change reached applyChange. And a legacy file predates the rule that no
        // key change restates the key in effect before it, so that repair belongs to reading
        // rather than to any edit.
        settleKeysAfterParsing();

        hasBeenDynamicallyLaidOut = data.hasBeenDynamicallyLaidOut();
        formatVersion = data.formatVersion();

        // Loaded file starts unmodified
        modified = false;

        // Note: SongChanged(FULL) is NOT posted here because the
        // song hasn't been installed into ScoreView yet. ScoreView.setSong()
        // posts the FULL message after all state is consistent.
    }

    // ========== Getters (public, read-only API) ==========

    public Tempo getTempo() {
        return tempo;
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
        return tempoResolver.getTempoAt(lineIndex, noteIndex);
    }

    /**
     * Returns the beat in effect at a position, found by walking backward from that
     * position to the nearest preceding beat-defining event. The nearest event wins
     * whichever kind it is; with no event before the anchor the song tempo decides, and
     * failing that the quarter note.
     *
     * @param lineIndex    the index of the line
     * @param elementIndex the index of the anchor element within that line
     * @return the beat in effect at this position and the position that defined it
     * @see TempoResolver#resolveBeatAt
     */
    public BeatAt resolveBeatAt(int lineIndex, int elementIndex) {
        return tempoResolver.resolveBeatAt(lineIndex, elementIndex);
    }

    /** Returns the canonical attribution metadata record. */
    public SongMetadata getMetadata() {
        return metadata;
    }

    public String getTitle() {
        return metadata.title();
    }

    public String getSubtitle() {
        return metadata.subtitle();
    }

    /**
     * Returns the title prefixed with the song number and a separator
     * (e.g. {@code "5. My Song"}) when a number is present, or the bare title
     * when the number is empty.
     */
    public String getNumberedTitle() {
        return numberedTitle(metadata.number(), metadata.title());
    }

    /**
     * Composes a numbered title from a raw number and title: the title prefixed
     * with the number and a separator (e.g. {@code "5. My Song"}) when a number
     * is present, or the bare title when the number is empty. Exposed so callers
     * working with uncommitted values (e.g. the song settings dialog's title
     * preview) can produce the same string without duplicating the format.
     */
    public static String numberedTitle(String number, String title) {
        if (number.isEmpty()) {
            return title;
        }

        return number + ". " + title;
    }

    public String getPlace() {
        return metadata.place();
    }

    public String getYear() {
        return metadata.year();
    }

    public int getMonth() {
        return metadata.month();
    }

    public int getDay() {
        return metadata.day();
    }

    public String getWordsYear() {
        return metadata.wordsYear();
    }

    public int getWordsMonth() {
        return metadata.wordsMonth();
    }

    public int getWordsDay() {
        return metadata.wordsDay();
    }

    public LANGUAGE getLanguage() {
        return language;
    }

    /**
     * Returns a syllabified-style text assembled from the {@link #getActiveVerse() active verse}'s
     * per-note {@link Lyric} records. Returns an empty string when that verse has no lyrics.
     */
    public String getLyricsText() {
        var sb = new StringBuilder(1000);

        for (var i = 0; i < lines.size(); i++) {
            var line = lines.get(i);

            for (var j = 0; j < line.effectiveElementCount(); j++) {
                var lyric = line.getElement(j).getLyricForVerse(activeVerse);

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
                        sb.append(Lyric.syllabicContinues(lyric.syllabic()) ? '-' : ' ');
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
        return metadata.unofficialTranslation();
    }

    public String getComposer() {
        return metadata.composer();
    }

    public String getLyricist() {
        return metadata.lyricist();
    }

    public LyricsSource getLyricsSource() {
        return metadata.lyricsSource();
    }

    public boolean isArrangement() {
        return metadata.arrangement();
    }

    /**
     * Returns {@code true} when the song has a non-empty official translation.
     * Unofficial translations are not credited in the attribution.
     * <p>
     * This is the single authoritative derivation of the translation flag
     * (decision 4A); rendering and IO pass this to
     * {@link AttributionFormatter}.
     */
    public boolean showTranslation() {
        return !metadata.unofficialTranslation() && !translatedLyrics.isEmpty();
    }

    /** Returns the stable attribution block element that carries geometry and user Y offset. */
    public Attribution getAttributionElement() {
        return attributionElement;
    }

    /** Returns the stable song tempo mark element drawn at the first line's staff header. */
    public SongTempoMark getTempoMarkElement() {
        return tempoMarkElement;
    }

    public String getNumber() {
        return metadata.number();
    }

    /**
     * Returns the key this song starts in, which is line 0's own key — a song holds no key of its
     * own. Line 0 always establishes one, because it has nothing to inherit from.
     *
     * <p>This is the query every caller that used to ask the song for its key asks instead, so
     * that reaching for line 0 is written once rather than at each of them.
     *
     * @return the key in effect at the start of the song; {@link Key#DEFAULT} while the song holds
     *         no lines, which only a reader part-way through a load ever sees
     */
    public Key getStartingKey() {
        return lines.isEmpty() ? Key.DEFAULT : lines.getFirst().getRunningKey();
    }

    public boolean isModified() {
        return modified;
    }

    /**
     * Returns the 1-based verse this song is showing — the one verse that layout, painting and the
     * lyric editor all work on. Every other verse the song carries is a translation held in the
     * document but not displayed.
     */
    public int getActiveVerse() {
        return activeVerse;
    }

    /**
     * Selects the verse this song shows. Changing it changes what every line lays out, so the
     * caller is responsible for invalidating layout afterwards.
     *
     * @param activeVerse the 1-based verse to show
     * @throws IllegalArgumentException if {@code activeVerse} is below {@link Lyric#FIRST_VERSE}
     */
    public void setActiveVerse(int activeVerse) {
        if (activeVerse < Lyric.FIRST_VERSE) {
            throw new IllegalArgumentException("verse indices are 1-based, got " + activeVerse);
        }

        this.activeVerse = activeVerse;
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

    public double getRowHeightAdjustmentSs() {
        return rowHeightAdjustmentSs;
    }

    public double getDefaultRestLengthSs() {
        return defaultRestLengthSs;
    }

    /**
     * The width of a staff line, typed so callers are handed a staff-space value rather than a
     * bare number they must remember the unit of.
     * <p>
     * The stored field is a {@code double} because the width travels through the mutation
     * machinery — {@link songscribe.message.mutation.LayoutChange} and the loaded
     * {@link songscribe.message.SongData} record — as a boxed {@code Double}, so the wrapper is
     * put on here rather than held.
     *
     * @return the staff line width in staff spaces
     */
    public Ss getLineWidthSs() {
        return new Ss(lineWidthSs);
    }

    public int getLineWidthPx() {
        return DocumentScale.ssToPx(lineWidthSs).sizePx();
    }

    public boolean hasBeenDynamicallyLaidOut() {
        return hasBeenDynamicallyLaidOut;
    }

    public int getFormatVersion() {
        return formatVersion;
    }

    // ========== Setters (mutate + setModified + post) ==========

    /**
     * Sets the song's tempo, dropping any tuplet the new beat invalidates. No-ops when
     * {@code tempo} says what the current one says.
     *
     * <p>Stays public because the undo replayer and the MusicXML header reader both
     * legitimately drive it from outside the editing UI. Bypassing it is still not possible:
     * the write itself is routed through {@link #withBeatDefiningEdit}, which no-ops its
     * validation during replay and during a suspended load.
     *
     * @param tempo the tempo the song is to have
     * @effects replaces the song's tempo, records one undo step, and revalidates tuplets
     *          against the new beat
     */
    public void setTempo(Tempo tempo) {
        if (this.tempo.equals(tempo)) {
            return;
        }

        mutateMetadata(MetadataField.TEMPO, this.tempo, tempo,
            () -> withBeatDefiningEdit(FIRST_LINE_INDEX, FIRST_ELEMENT_INDEX,
                () -> this.tempo = tempo));
    }

    /**
     * The index of the first line that has any element, or -1 when the song has no lines
     * or every line is empty. A leading empty line is skipped, so deleting every element of
     * line 0 moves the answer down to the next line that still has elements.
     */
    public int firstNonEmptyLineIndex() {
        for (var lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            if (!lines.get(lineIndex).isEmpty()) {
                return lineIndex;
            }
        }

        return -1;
    }

    /**
     * The song's first element — element 0 of the first non-empty line — or null when every
     * line is empty or the song has no lines. A pure query, with no bearing on where any
     * tempo lives: the UI rule that forbids creating a tempo change on the song's first
     * element is its only caller.
     */
    public @Nullable StaffElement firstElement() {
        var lineIndex = firstNonEmptyLineIndex();

        return lineIndex < 0 ? null : lines.get(lineIndex).getElement(0);
    }

    /**
     * Sets the canonical attribution metadata record atomically. The record must
     * already be normalized — this method does not re-normalize (decision 7A).
     * Records the change as a single coarse {@link MetadataField#ATTRIBUTION} mutation.
     * No-op when {@code newMetadata} equals the current record.
     */
    public void setMetadata(SongMetadata newMetadata) {
        if (metadata.equals(newMetadata)) {
            return;
        }

        var oldMetadata = metadata;
        withModification(() -> applyChange(
            new MetadataChange(MetadataField.ATTRIBUTION, oldMetadata, newMetadata),
            () -> metadata = newMetadata
        ));
    }

    public void setUnderLyrics(String text) {
        var newLyrics = StringUtils.processText(text, true);
        mutateLyrics(LyricsField.UNDER, underLyrics, newLyrics, () -> underLyrics = newLyrics);
    }

    public void setBanglaLyrics(String text) {
        var newLyrics = text.trim();
        mutateLyrics(LyricsField.BANGLA, banglaLyrics, newLyrics, () -> banglaLyrics = newLyrics);
    }

    public void setTranslatedLyrics(String text) {
        var newLyrics = StringUtils.processText(text, false);
        mutateLyrics(
            LyricsField.TRANSLATED, translatedLyrics, newLyrics,
            () -> translatedLyrics = newLyrics);
    }

    public void setFootnotes(String text) {
        var newFootnotes = StringUtils.processText(text, false);
        mutateMetadata(MetadataField.FOOTNOTES, footnotes, newFootnotes, () -> footnotes = newFootnotes);
    }

    // -- Direct setters (bypass mutation tracking; for preview/scratch Song instances only) --

    /**
     * Trims the person name; if empty, returns {@link #SRI_CHINMOY}.
     * Still public because {@link SongIO} uses it when parsing legacy files.
     */
    public static String coercePerson(String text) {
        var trimmed = text.trim();
        return trimmed.isEmpty() ? SRI_CHINMOY : trimmed;
    }

    // -- Layout setters --

    public void setRowHeightAdjustmentSs(double rowHeightAdjustment) {
        mutateLayout(
            LayoutField.ROW_HEIGHT_ADJUSTMENT_SS, rowHeightAdjustmentSs, rowHeightAdjustment,
            () -> rowHeightAdjustmentSs = rowHeightAdjustment
        );
    }

    /**
     * Sets the line-wide rest length, clamped to at least {@link #MIN_DEFAULT_REST_LENGTH_SS}.
     * There is no mutation record: nothing edits this interactively yet (song-settings UI is #569),
     * so it is written only by document load.
     */
    public void setDefaultRestLengthSs(double defaultRestLength) {
        defaultRestLengthSs = Math.max(MIN_DEFAULT_REST_LENGTH_SS, defaultRestLength);
    }

    /**
     * Do not call this directly unless you know what you are doing. Instead, use
     * {@link songscribe.ui.component.ScoreView#updatePageLayout}, which stores the width
     * and re-lays out the page for it; writing the width here alone leaves the page laid
     * out for the old one.
     */
    public void setLineWidthSs(Ss lineWidth) {
        var lineWidthValueSs = lineWidth.value();
        mutateLayout(
            LayoutField.LINE_WIDTH_SS, lineWidthSs, lineWidthValueSs, () -> lineWidthSs = lineWidthValueSs);
    }

    // -- Setter helpers --

    /**
     * Early-returns if {@code current} and {@code newValue} are equal; otherwise
     * opens a bracket and emits a {@link MetadataChange} recording the change.
     * Autoboxing applies for primitive callers.
     *
     * <p>For {@link MetadataField#TEMPO} the {@code Objects.equals} guard is an
     * <em>identity</em> comparison: {@link Tempo} deliberately declares no
     * {@code equals}/{@code hashCode} because it is mutated in place. TEMPO's value guard
     * therefore lives upstream, in {@link #setTempo}.
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

        withModification(Strings.get(Strings.ACTION_EDIT_OP_CHANGE_LYRICS),
            () -> applyChange(new LyricsChange(field, current, newValue), apply));
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
     * <p>An insertion that lands <em>between</em> two lines pushes them apart, and a tie
     * that straddled that boundary can no longer be drawn — its two halves have nothing
     * left to meet across. Such a tie is removed by
     * {@link #removeSpansBetweenNonAdjacentLines}. That is skipped while replaying: undo
     * and redo replay the recorded span mutations themselves.
     *
     * <pre>
     *  withModification {
     *    withAutoMaintenance {
     *      applyChange(LineInsertion(index, line), …)
     *
     *      if (line landed between two existing lines)
     *        for each span of lines[index - 1] whose two lines are no longer adjacent
     *          applyChange(&lt;typed span removal&gt; …)
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

        withModification(() -> modifications.withAutoMaintenance(() -> {
            applyChange(new LineInsertion(lineIndex, line), () -> lines.add(lineIndex, line));

            // Only the pair the new line landed between can have been pulled apart; every
            // other pair shifts by the same amount and stays adjacent.
            if (lineIndex > 0 && lineIndex < lines.size() - 1 && !isReplaying()) {
                removeSpansBetweenNonAdjacentLines(lines.get(lineIndex - 1));
            }

            if (willBecomeNewLast && !isMutationTrackingSuspended() && !isReplaying()) {
                terminal.maintainOnLastLineChange(previousLastLine, line);
            }
        }));
    }

    /**
     * Removes the line at {@code index} and, when the removed line was the last line,
     * installs the terminal on the new last line so the invariant holds. When removing
     * the sole remaining line would leave the song with no lines, a fresh empty line is
     * inserted in its place instead, so a normally-tracked removal always leaves the
     * song with at least one line. All resulting mutations coalesce into a single
     * {@link SongDidChangeNotification}.
     *
     * <p>Both the invariant transfer and the sole-line replacement are skipped when
     * mutation tracking is suspended (see {@link #withoutMutationTracking}) or while
     * replaying — bulk-load paths and undo/redo drive the line list explicitly, and
     * both legitimately pass through a transient zero-line state.
     *
     * <p>The removed line keeps its elements, and they keep pointing at it — the
     * {@link LineDeletion} record holds the line intact so undo can put it back unchanged.
     * A span it shared with a surviving line therefore has to be taken out explicitly, or
     * that line would go on drawing half a tie to a line no longer in the song;
     * {@link #removeSpansBetweenNonAdjacentLines} does that. It is skipped while replaying:
     * undo and redo replay the recorded span mutations themselves.
     *
     * <pre>
     *  withModification {
     *    withAutoMaintenance {
     *      applyChange(LineDeletion(index, removed), …)
     *
     *      for each span the removed line shared with a surviving line
     *        applyChange(&lt;typed span removal&gt; …)
     *
     *      if (removed line was the last line) {
     *        if (lines is now empty) {
     *          applyChange(LineInsertion(0, new empty line), …)
     *          applyChange(ElementInsertion(new line, FINAL) …)
     *        } else {
     *          let penult = lines.last
     *          switch (penult.lastElement) {
     *            FINAL   → no-op
     *            barline → applyChange(ElementReplacement …)
     *            non-bar → applyChange(ElementInsertion …)
     *            empty   → applyChange(ElementInsertion …)
     *          }
     *        }
     *      }
     *    }
     *  }
     * </pre>
     */
    public void removeLine(int index) {
        var deletedLine = lines.get(index);
        var wasLast = index == lines.size() - 1;

        withModification(() -> modifications.withAutoMaintenance(() -> {
            applyChange(
                new LineDeletion(index, deletedLine),
                () -> lines.remove(index)
            );

            // The deleted line is now in no position at all, so every span it shares with
            // a line that survives it spans lines that are not adjacent.
            if (!isReplaying()) {
                removeSpansBetweenNonAdjacentLines(deletedLine);
            }

            if (wasLast && !isMutationTrackingSuspended() && !isReplaying()) {
                if (!lines.isEmpty()) {
                    terminal.maintainOnLastLineChange(null, lines.getLast());
                } else {
                    addLine(0, new Line(this));
                }
            }
        }));
    }

    /**
     * Removes from {@code line} every span whose two endpoints sit in lines that are no
     * longer adjacent.
     * <p>
     * Only a tie can straddle a line break, and only between consecutive lines: its anchor
     * is in one line and its end in the next. Once those two lines are no longer neighbors
     * the tie describes a jump nothing can draw, so it is
     * removed rather than left to render across a gap.
     * <p>
     * Both ways adjacency breaks come through here — a line inserted between the two, and
     * one of the two leaving the song, which leaves it with no position in {@code lines} at
     * all. A pair in the wrong order is caught the same way.
     * <p>
     * Spans with both endpoints in one line, and spans with an endpoint in no line, are
     * untouched: neither says anything about a relationship between two lines.
     * <p>
     * The removal goes through {@link Line#removeInvalidatedSpan}, so it emits its own
     * typed mutation and undo puts the span back into both lines' span lists. Call it from
     * inside an open modification bracket.
     */
    private void removeSpansBetweenNonAdjacentLines(Line line) {
        // Copied because each removal writes the list being read.
        for (var span : List.copyOf(line.getSpans())) {
            var anchorLine = span.getAnchorLine();
            var endLine = span.getEndLine();

            if (anchorLine == null || endLine == null || anchorLine == endLine) {
                continue;
            }

            var anchorLineIndex = indexOfLine(anchorLine);
            var endLineIndex = indexOfLine(endLine);

            if (anchorLineIndex < 0 || endLineIndex < 0 || endLineIndex - anchorLineIndex != 1) {
                line.removeInvalidatedSpan(span);
            }
        }
    }

    /**
     * Restores the terminal invariant after a {@link #newParsingStub() parsing stub}
     * has been fully populated: ensures the song's last line ends with a valid
     * terminal ({@link ElementType#isValidSongTerminal()}). File readers suspend mutation
     * tracking while building lines, so the per-{@code addLine} maintenance is skipped;
     * this restores the invariant in one pass at the end of a load.
     *
     * <p>Must be called while mutation tracking is still suspended so the fix-up is
     * silent (no notification, no {@code modified} flag) and the {@link Line} terminal
     * guards stay bypassed. A no-op when the last line already ends with a valid
     * terminal, so a range span (e.g. an {@code Ending} ending on that barline) keeps
     * its exact element reference.
     */
    public void installTerminalAfterParsing() {
        terminal.installAfterParsing();
    }

    /**
     * Returns a fresh element of the given terminal type. Throws
     * {@link IllegalArgumentException} if {@code type} is not a valid terminal
     * (i.e. {@link ElementType#isValidSongTerminal()} returns {@code false}).
     */
    static StaffElement newTerminalElement(ElementType type) {
        return TerminalMaintainer.newTerminalElement(type);
    }

    /**
     * Returns {@code true} when {@code element} is the song's auto-maintained
     * terminal: it occupies the last position of the last line, and its type satisfies
     * {@link ElementType#isValidSongTerminal()}.
     *
     * <p>A valid terminal type that sits on any line other than the last, or at any
     * position other than the last, is treated as an ordinary (interactable) element.
     */
    public boolean isAutoMaintainedTerminal(StaffElement element, Line line) {
        return terminal.isAutoMaintainedTerminal(element, line);
    }

    /**
     * Returns {@code true} when {@code element} is its song's auto-maintained terminal,
     * resolving the line and song from the element itself. For callers that have a
     * {@link StaffElement} but neither a {@link Line} nor a {@link Song} handle —
     * {@code UIAction.Reflectable.appliesTo}, in particular.
     * <p>
     * <strong>Contract:</strong> returns {@code false} for an element that belongs to no line.
     * That default is correct for an applicability predicate, where an unparented element simply
     * does not apply, but it is <em>wrong</em> for an invariant guard, which must not treat
     * "cannot tell" as "safe". A guard inside the DOM has a {@link Line} in hand and must use
     * {@link #isAutoMaintainedTerminal(StaffElement, Line)} instead — see
     * {@code Line.guardsTerminalAt}. The deliberately different name keeps the weaker predicate
     * from being reached for by muscle memory when writing such a guard.
     */
    public static boolean isAutoMaintainedTerminalOfItsSong(StaffElement element) {
        var line = element.getParentLine();
        return line != null && line.getSong().isAutoMaintainedTerminal(element, line);
    }

    /** Returns the type of the current auto-maintained terminal element. */
    public ElementType currentTerminalType() {
        return terminal.currentTerminalType();
    }

    /**
     * Returns {@code true} when the terminal may be replaced with an element of the given
     * type: {@code incomingType} must be a valid terminal and must differ from the type
     * currently occupying the terminal slot.
     */
    public boolean canReplaceTerminal(ElementType incomingType) {
        return terminal.canReplaceTerminal(incomingType);
    }

    /**
     * Replaces the terminal element with a fresh element of {@code incomingType}.
     * This is a user-driven mutation — no auto-maintenance increment. No-op when
     * {@code incomingType} already matches the current terminal type. Throws
     * {@link IllegalArgumentException} if {@code incomingType} is not a valid terminal.
     */
    public void replaceTerminal(ElementType incomingType) {
        terminal.replaceTerminal(incomingType);
    }

    // ========== Modification brackets ==========
    //
    // Every entry point below delegates to this song's ModificationSession, which owns
    // the depth counters and the accumulated mutation batch. See that class, and
    // docs/mutations.md, for the rules callers must honor.

    /** Returns {@code true} while a modification bracket is open. */
    public boolean isModifying() {
        return modifications.isModifying();
    }

    /** Returns {@code true} while mutation tracking is suspended. */
    public boolean isMutationTrackingSuspended() {
        return modifications.isMutationTrackingSuspended();
    }

    /**
     * Returns {@code true} while the {@link Line} mutation guards are bypassed for
     * internally-driven maintenance. Package-private: only {@code Line} asks.
     */
    boolean isInAutoMaintenance() {
        return modifications.isInAutoMaintenance();
    }

    /** Returns {@code true} while a recorded mutation batch is being replayed. */
    public boolean isReplaying() {
        return modifications.isReplaying();
    }

    /**
     * Runs {@code body} with mutation tracking suspended: mutations apply but nothing is
     * recorded — no notification, no undo entry, no {@code modified} flag. Intended for
     * test setup and file-load infrastructure; production editing code should use
     * {@link #withModification(Runnable)} instead.
     *
     * @see ModificationSession#withoutMutationTracking
     */
    public void withoutMutationTracking(Runnable body) {
        modifications.withoutMutationTracking(body);
    }

    /**
     * Runs {@code body} in replay mode: mutations are still recorded, but the helpers'
     * companion side-work is suppressed and the {@link Line} terminal guards are bypassed,
     * because the batch being replayed already carries every change. Nestable.
     *
     * @see ModificationSession#withReplay
     */
    public void withReplay(Runnable body) {
        modifications.withReplay(body);
    }

    /**
     * Suspends mutation tracking until the matching {@link #endSuspendMutationTracking()}.
     * Use {@link #withoutMutationTracking(Runnable)} when the suspended scope fits in a
     * single block; this pair exists for callers (e.g. SAX parsing) whose suspension
     * scope crosses multiple methods.
     */
    public void beginSuspendMutationTracking() {
        modifications.beginSuspendMutationTracking();
    }

    /**
     * Resumes mutation tracking. Must be paired with a prior
     * {@link #beginSuspendMutationTracking()} call; calls without a matching
     * begin are a programming error and throw immediately.
     */
    public void endSuspendMutationTracking() {
        modifications.endSuspendMutationTracking();
    }

    /**
     * Opens a modification bracket with no explicit label. Mutations accumulate while
     * the bracket is open. Brackets may be nested; the notification fires only when the
     * outermost bracket closes.
     */
    public void beginModification() {
        modifications.beginModification();
    }

    /**
     * Opens a modification bracket, declaring {@code explicitLabel} as the op-name
     * (Tier B) if this is the outermost bracket. The op-name is captured only at the
     * depth 0→1 transition, so a nested labeled bracket never re-captures.
     *
     * @see ModificationSession#beginModification(String)
     */
    public void beginModification(@Nullable String explicitLabel) {
        modifications.beginModification(explicitLabel);
    }

    /**
     * Closes a modification bracket. When the outermost bracket closes and at least one
     * mutation was accumulated, marks the song modified and posts a single
     * {@link SongDidChangeNotification} carrying all accumulated mutations.
     *
     * @see ModificationSession#endModification
     */
    public void endModification() {
        modifications.endModification();
    }

    /**
     * Executes {@code body} inside a modification bracket, then posts a single
     * {@link SongDidChangeNotification} with all accumulated mutations.
     * Prefer this over {@link #beginModification()} / {@link #endModification()} to ensure
     * the depth counter is always balanced even if {@code body} throws.
     *
     * <p>Every state change {@code body} makes must be recorded as a {@link Mutation} in this
     * bracket's batch, through {@link #applyChange} or a {@code Line} helper wrapping it. Undo
     * replays the recorded batch mechanically, so a change made outside that route — a raw
     * {@code spans.removeIf}, a field written directly — is invisible to it and makes the round
     * trip lossy. The failure is silent: the batch still replays cleanly, with the unrecorded
     * change simply missing. A helper that drops dependent state therefore routes each removal
     * through the typed tracked helper {@code Line.removeInvalidatedSpan} dispatches to, so the
     * proper removal mutation lands in the batch.
     *
     * @invariant every state change made inside the bracket is recorded as a mutation in its batch
     */
    public void withModification(Runnable body) {
        modifications.withModification(body);
    }

    /**
     * The value-returning form of {@link #withModification(Runnable)}, for a body
     * whose outcome the caller must inspect after the bracket closes.
     *
     * @param body The modification to run
     * @return Whatever {@code body} returns
     */
    public <T> T withModificationResult(Supplier<T> body) {
        return modifications.withModificationResult(body);
    }

    /**
     * Executes {@code body} inside a modification bracket that declares {@code label}
     * as its op-name (Tier B), then posts a single {@link SongDidChangeNotification}.
     * The label is captured only if this is the outermost bracket (see
     * {@link #beginModification(String)}). {@code body} carries the same recording
     * obligation stated on {@link #withModification(Runnable)}.
     */
    public void withModification(String label, Runnable body) {
        modifications.withModification(label, body);
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
        modifications.postWithModification(message);
    }

    /**
     * Like {@link #postWithModification(Message)} but declares an explicit undo
     * op-name {@code label} for the resulting batch (see
     * {@link #withModification(String, Runnable)}).
     */
    public void postWithModification(String label, Message message) {
        modifications.postWithModification(label, message);
    }

    /**
     * Applies a single mutation within an open modification bracket: runs {@code mutator},
     * then records {@code mutation} in the accumulated batch. Under suspended tracking the
     * mutator runs and nothing is recorded.
     *
     * <p>Every mutation that can move a key — a line's own key, a mid-line key change, or a
     * line insertion or deletion that shifts what a line inherits — brings the key invariant
     * back up to date here, after the mutator has run. This is the only place that does it, and
     * that is deliberate: the ten mutators that can carry a key each route through this one
     * method, undo and redo replay through it too, and an omission at any individual mutator
     * would be wrong pitches on every line downstream with no error and nothing visible to
     * notice. An ordinary note edit costs one type test and no walk.
     *
     * @throws IllegalStateException if called outside a modification bracket
     * @see ModificationSession#applyChange
     */
    public void applyChange(Mutation mutation, Runnable mutator) {
        // Captured before the mutator runs: a line insertion or deletion can promote a different
        // line to index 0, and only the key the song started in can tell the repair below what
        // that line should now establish. Null while a document is being built, where line 0 may
        // not be in a key yet — the load settles that at the end, in one pass.
        var startingKeyBefore = promotesLineZero(mutation) ? startingKey() : null;

        modifications.applyChange(mutation, mutator);

        maintainKeyInvariant(mutation, startingKeyBefore);
    }

    private static boolean promotesLineZero(Mutation mutation) {
        return mutation instanceof LineInsertion || mutation instanceof LineDeletion;
    }

    private @Nullable Key startingKey() {
        if (lines.isEmpty()) {
            return null;
        }

        var firstLine = lines.getFirst();
        var ownKey = firstLine.getKey();
        return ownKey != null ? ownKey : inheritedKeys.get(firstLine);
    }

    /**
     * Returns the key in effect at the <em>start</em> of {@code line}: its own key where it
     * establishes one, and otherwise the key in effect at the end of the line before it.
     *
     * <p>Total, and deliberately so — this is what {@link Line#getRunningKey()} bottoms out in, and
     * a model getter has no business terminating the application. A line that establishes no key
     * and inherits none is in {@link Key#DEFAULT}: that is the key a document naming no key
     * anywhere is in, not a stand-in for a broken invariant. It is reachable while a reader is
     * part-way through a load, and for a line that is not one of this song's lines.
     *
     * @param line the line to resolve, which need not be one of this song's lines
     * @return the key in effect at the start of {@code line}; never null
     */
    public Key runningKeyAt(Line line) {
        var ownKey = line.getKey();

        if (ownKey != null) {
            return ownKey;
        }

        var inherited = inheritedKeys.get(line);
        return inherited != null ? inherited : Key.DEFAULT;
    }

    /**
     * Returns the key {@code line} inherits — the key in effect at the end of the line before it —
     * regardless of whether the line establishes a key of its own and so overrides it.
     *
     * <p>Distinct from {@link #runningKeyAt} in exactly the case that matters to
     * {@link Line#setKey}'s no-op normalization: a line asking whether the key it is being given is
     * the one it would inherit anyway needs the inherited value, not its own.
     *
     * @param line the line to ask about
     * @return the key {@code line} inherits, or null when nothing precedes it — line 0, a line not
     *         in this song, or any line while a load is still in progress
     */
    @Nullable
    Key inheritedKeyOf(Line line) {
        return inheritedKeys.get(line);
    }

    /**
     * Restores the key invariant after {@code mutation} — line 0 establishing a key of its own,
     * and every later line's inherited key matching the key at the end of the line before it.
     *
     * <p>{@link #keyMoveOf} decides whether the mutation moves a key at all, so that an ordinary
     * note or lyric edit does not walk the line list. Only the line-list mutations are switched on
     * here, for the repair they owe on top of the propagation every key move owes.
     *
     * <p>Package-private rather than private because {@link Line#applyChange} owes the same
     * maintenance on its suspended-tracking branch, where the mutator runs without reaching
     * {@link #applyChange}. That caller passes a null {@code startingKeyBefore}: only a line
     * insertion or deletion can need it, and neither reaches this class through a line.
     */
    void maintainKeyInvariant(Mutation mutation, @Nullable Key startingKeyBefore) {
        switch (mutation) {
            case LineInsertion _ -> repairLineZeroKey(startingKeyBefore);
            case LineDeletion deletion -> {
                // A line out of the song inherits from nothing, so it must not go on answering
                // with what it inherited while it was in one. Dropping the entry also bounds the
                // line's lifetime: LineDeletion holds the line for undo, and without this the map
                // would keep it reachable for the song's whole life after that record is gone.
                inheritedKeys.remove(deletion.deletedLine());
                repairLineZeroKey(startingKeyBefore);
            }
            default -> {
                // Only a change to which lines exist can leave line 0 without a key of its own.
            }
        }

        var keyMove = keyMoveOf(mutation);

        if (keyMove != null) {
            propagateInheritedKeys(keyMove);
        }
    }

    /**
     * Where a mutation's key move begins, as the line indices the move is felt from, and where the
     * forward walk it starts is first allowed to stop.
     *
     * <p>The first two differ because a line's own key and the key it inherits are separate
     * storage: a line given a key of its own starts running in it immediately while still
     * inheriting what it always did, and a mid-line key change moves neither of that line's two
     * keys — only the key it leaves off in, which the line after it inherits.
     *
     * @param inheritedFromIndex the first line whose <em>inherited</em> key the move can change,
     *                           which is where {@link #propagateInheritedKeys} starts
     * @param runningFromIndex   the first line whose <em>running</em> key the move can change,
     *                           which is where {@link #keyMoveReach} starts drawing differently
     * @param firstStoppableIndex the lowest index at which a line establishing a key of its own
     *                           may stop the walk — see the stopping rule and the exception an
     *                           insertion makes to it in {@code docs/key-changes.md}
     */
    private record KeyMove(int inheritedFromIndex, int runningFromIndex, int firstStoppableIndex) {
        KeyMove(int inheritedFromIndex, int runningFromIndex) {
            this(inheritedFromIndex, runningFromIndex, inheritedFromIndex);
        }
    }

    /**
     * Returns where {@code mutation}'s key move begins, or null when it moves no key.
     *
     * <p>This is the one enumeration of the mutations that can move a key.
     * {@link #maintainKeyInvariant} reads it to bring the inherited keys back up to date, and
     * {@link #keyMoveReach} reads it to say which lines now draw something different. A second
     * enumeration would drift out of step with this one, and the drift would show as a line
     * rendering the key it was in before the edit, with nothing to explain it.
     *
     * @param mutation the mutation just applied, or about to be replayed
     * @return where the move begins, or {@code null} when {@code mutation} moves no key — which
     *     is every mutation not listed here, and every listed one that carried no key change
     *     or names a line no longer in this song
     */
    private @Nullable KeyMove keyMoveOf(Mutation mutation) {
        return switch (mutation) {
            // A line arriving at or leaving an index changes what the line now at that index
            // inherits, and therefore what it runs in: both start there. An arriving line also
            // gives the line behind it a new predecessor, which is why the walk may not stop until
            // one line further on; a departing one leaves every line behind the gap following what
            // it always followed.
            case LineInsertion insertion -> new KeyMove(
                insertion.lineIndex(), insertion.lineIndex(), insertion.lineIndex() + 1);
            case LineDeletion deletion -> new KeyMove(deletion.lineIndex(), deletion.lineIndex());
            case LineKeyChange change -> ownKeyMove(change.line());
            case ElementInsertion insertion -> midLineKeyMove(
                insertion.line(),
                changesKey(insertion.element()));
            case ElementDeletion deletion -> midLineKeyMove(
                deletion.line(),
                changesKey(deletion.deletedElement()));
            case ElementReplacement replacement -> midLineKeyMove(
                replacement.line(),
                changesKey(replacement.oldElement()) || changesKey(replacement.newElement()));
            case ElementRangeDeletion rangeDeletion -> midLineKeyMove(
                rangeDeletion.line(),
                rangeDeletion.deletedElements().stream().anyMatch(Song::changesKey));
            case ElementModification modification -> midLineKeyMove(
                modification.line(),
                changesKey(modification.beforeElement()) || changesKey(modification.afterElement()));
            default -> null;
        };
    }

    /**
     * The move a change to {@code line}'s own key makes: the line runs in the new key from its own
     * start, while what it inherits is untouched, so the inheritance walk begins after it.
     *
     * @param line the line whose own key changed
     * @return the move, or null when {@code line} is not in this song and so moves nothing
     */
    private @Nullable KeyMove ownKeyMove(Line line) {
        var lineIndex = indexOfLine(line);
        return lineIndex < 0 ? null : new KeyMove(lineIndex + 1, lineIndex);
    }

    /**
     * The move a mid-line key change written into or taken out of {@code line} makes: the line's
     * own two keys stand, and only the key it leaves off in moves, so both walks begin after it.
     *
     * @param line the line the mutation touched
     * @param movesKey whether the elements the mutation carried were key changes at all
     * @return the move, or null when the mutation carried no key change or {@code line} is not
     *         in this song
     */
    private @Nullable KeyMove midLineKeyMove(Line line, boolean movesKey) {
        if (!movesKey) {
            return null;
        }

        var lineIndex = indexOfLine(line);
        return lineIndex < 0 ? null : new KeyMove(lineIndex + 1, lineIndex + 1);
    }

    /**
     * Returns the lines {@code mutation}'s key move changes the drawn key content of: every line
     * whose running key it moved, and the line before the first of those, whose cautionary key
     * signature depicts the boundary the move created. Empty when the mutation moves no key.
     *
     * <p>A view holding per-line geometry needs this. A line's header signature and the spacing
     * around it are solved from the key that line runs in, and the room kept clear at its end is
     * solved from the cautionary it leads into — so a key move leaves stale geometry on lines no
     * mutation names, and the line ahead of the move is one of them. See
     * {@code docs/key-changes.md}.
     *
     * <p>Asked after the mutation has been applied, so the answer describes the document as it now
     * stands, which is what a stale view has to be brought up to.
     *
     * <p>A line insertion or deletion answers here too, but no view need ask: it changes which
     * lines exist, which costs a rebuild of every line rather than an invalidation of some.
     *
     * @param mutation the mutation to ask about
     * @return the lines, in song order, or empty when {@code mutation} moves no key
     */
    public List<Line> keyMoveReach(Mutation mutation) {
        var keyMove = keyMoveOf(mutation);

        if (keyMove == null) {
            return List.of();
        }

        var firstIndex = Math.max(keyMove.runningFromIndex() - 1, 0);
        var lastIndex = firstKeyedLineFrom(keyMove.firstStoppableIndex()) - 1;

        return firstIndex > lastIndex
            ? List.of()
            : List.copyOf(lines.subList(firstIndex, lastIndex + 1));
    }

    private static boolean changesKey(StaffElement element) {
        return element.getType().isKeyChange();
    }

    /**
     * Restores line 0's own key when a mutation has left it with none. Line 0 has nothing to
     * inherit from, so it must establish a key, and the only value that leaves what the user sees
     * unchanged is the key that line was already sounding: the key it inherited a moment ago, or —
     * for a line that has just been inserted at index 0 and so inherited nothing — the key the
     * song itself started in.
     *
     * <p>Recorded as a {@link LineKeyChange} in the same batch, so undo puts the line back to
     * inheriting rather than leaving it silently keyed to the value it was repaired with.
     *
     * <p>Skipped while mutation tracking is suspended. A file reader adds its lines one at a time,
     * so line 0 is briefly the only line and the key it will end up in is not yet known; guessing
     * one here would establish a key the document never had, and it would stand, because
     * {@link #rebuildInheritedKeysAfterParsing()} leaves a line that already has one alone.
     */
    private void repairLineZeroKey(@Nullable Key startingKeyBefore) {
        if (lines.isEmpty() || isMutationTrackingSuspended()) {
            return;
        }

        var firstLine = lines.getFirst();

        if (firstLine.getKey() != null) {
            inheritedKeys.remove(firstLine);
            return;
        }

        var inherited = inheritedKeys.get(firstLine);
        Key materialized;

        if (inherited != null) {
            materialized = inherited;
        } else if (startingKeyBefore != null) {
            materialized = startingKeyBefore;
        } else {
            materialized = Key.DEFAULT;
        }

        // Cleared first so setKey's no-op normalization cannot collapse the repair back to null.
        inheritedKeys.remove(firstLine);
        firstLine.setKey(materialized);
    }

    /**
     * Reassigns the inherited key of every line from {@code keyMove}'s inherited-from index
     * forward, stopping after the first line that establishes a key of its own — no earlier than
     * {@link KeyMove#firstStoppableIndex}. A song with a key on every line therefore costs one
     * step, or two where a line has just arrived.
     */
    private void propagateInheritedKeys(KeyMove keyMove) {
        // A document under construction may not have put line 0 in a key yet, and until it does
        // there is no key for any later line to be in either. The load settles the whole list in
        // one pass at the end.
        if (lines.isEmpty() || lines.getFirst().getKey() == null) {
            return;
        }

        // Line 0 inherits nothing, so propagation always starts at line 1 or later. The line the
        // walk stops at is reassigned before it stops: its own key overrides what it inherits, so
        // the entry is never read, but leaving it stale would leave the map disagreeing with the
        // document.
        var firstIndex = Math.max(keyMove.inheritedFromIndex(), 1);
        var lastIndex = Math.min(firstKeyedLineFrom(keyMove.firstStoppableIndex()), lines.size() - 1);

        for (var lineIndex = firstIndex; lineIndex <= lastIndex; lineIndex++) {
            inheritedKeys.put(lines.get(lineIndex), lines.get(lineIndex - 1).keyAtEndOfLine());
        }
    }

    /**
     * Returns the index of the first line at or after {@code fromIndex} that establishes a key of
     * its own, or {@link #lineCount()} when none does.
     *
     * <p>This is the stopping rule the whole feature runs on — <em>forward to the first line with
     * its own key</em> — in one place: that line's running key cannot have moved, so nothing past
     * it can either. The scan starts at line 1 whatever it is asked for, because line 0 always
     * establishes a key of its own and so is never the line a forward walk stops at.
     *
     * <p>See {@code docs/key-changes.md}.
     *
     * @param fromIndex where to start looking; clamped up to 1, since line 0 always establishes
     *                  its own key and so is never where a forward walk stops
     * @return that line's index, or {@link #lineCount()} when no line at or after
     *     {@code fromIndex} establishes a key of its own
     */
    private int firstKeyedLineFrom(int fromIndex) {
        for (var lineIndex = Math.max(fromIndex, 1); lineIndex < lines.size(); lineIndex++) {
            if (lines.get(lineIndex).getKey() != null) {
                return lineIndex;
            }
        }

        return lines.size();
    }

    /**
     * Settles the key invariant across every line in one forward pass, after a
     * {@link #newParsingStub() parsing stub} or a {@link #loadFrom(SongData)} has been fully
     * populated. File readers build lines under suspended mutation tracking, so the per-mutation
     * propagation in {@link #applyChange} never ran for any of them.
     *
     * <p>Must be called while mutation tracking is still suspended, so the fix-up is silent — no
     * notification, no undo entry and no {@code modified} flag.
     *
     * <p>Line 0 has nothing to inherit from, so a document whose first line establishes no key of
     * its own falls back to {@link Key#DEFAULT}. Every reader is expected to have put the key its
     * file carried on line 0 before calling this — the fallback is for a file that named no key
     * anywhere, not the route by which a stated one arrives.
     */
    public void rebuildInheritedKeysAfterParsing() {
        // Emptied rather than overwritten: Song.loadFrom replaces the line list wholesale, without
        // a LineDeletion per line, so entries for the lines it discarded have nothing else to take
        // them out and would keep those lines reachable for the rest of the song's life.
        inheritedKeys.clear();

        if (lines.isEmpty()) {
            return;
        }

        var firstLine = lines.getFirst();

        if (firstLine.getKey() == null) {
            firstLine.setKey(Key.DEFAULT);
        }

        for (var lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
            inheritedKeys.put(lines.get(lineIndex), lines.get(lineIndex - 1).keyAtEndOfLine());
        }
    }

    /**
     * Removes every mid-line key change that restates the key already in effect before it,
     * together with the element it is paired with, across every line of a freshly parsed song.
     *
     * <p>Reading is one of the two places that rule is enforced — {@code docs/key-changes.md}
     * says why it takes both. A file written before the rule existed can carry a stranding no edit
     * has reached, and nothing on screen says so: a key change that restates the running key draws
     * no accidentals and occupies no width, yet still refuses the two insertion indices flanking
     * it and still reaches MusicXML on the next save.
     *
     * <p>Removing a stranded key change steps the key to the value it already held, so the chain
     * {@link #rebuildInheritedKeysAfterParsing()} just settled survives the removal and no second
     * pass is owed.
     *
     * @effects Mutates every line carrying a stranded key change.
     */
    private void removeStrandedKeyChangesAfterParsing() {
        for (var line : lines) {
            line.deleteRanges(line.redundantKeyChangeRanges(line.getRunningKey()));
        }
    }

    /**
     * Puts a freshly parsed song's keys into the state every edit afterwards assumes: each line's
     * inherited key derived from the line before it, and no mid-line key change left restating the
     * key already in effect where it stands.
     *
     * <p>This is what a file reader calls once its lines are in place, and it is one call rather
     * than two because the order is not the reader's to choose — the stranding repair reads each
     * line's running key, which only the inheritance pass settles. A reader that ran them the
     * other way round, or dropped the second, would leave a document whose invisible key changes
     * survive every save, with nothing on screen and nothing in the build to say so.
     *
     * <p>Must be called while mutation tracking is still suspended, so the whole of it is silent —
     * no notification, no undo entry and no {@code modified} flag.
     *
     * @effects Mutates every line whose inherited key or stranded key changes need settling.
     */
    public void settleKeysAfterParsing() {
        rebuildInheritedKeysAfterParsing();
        removeStrandedKeyChangesAfterParsing();
    }

    /**
     * Applies a beat-defining state change and, inside the same modification bracket,
     * removes every tuplet at or after the edit position that the new beat context
     * invalidates, warning the user once when any were lost.
     *
     * <p>{@code edit} must perform the raw state change only, without recording its own
     * mutation. Nested calls aggregate, so the outermost caller gets a truthful answer.
     *
     * @param lineIndex    the index of the line the edit sits on
     * @param elementIndex the index of the element within that line
     * @param edit         the raw state change
     * @return {@code true} if at least one tuplet was removed
     * @see ModificationSession#withBeatDefiningEdit
     */
    public boolean withBeatDefiningEdit(int lineIndex, int elementIndex, Runnable edit) {
        return modifications.withBeatDefiningEdit(lineIndex, elementIndex, edit);
    }

    /**
     * {@link #withBeatDefiningEdit} for a write that hangs on an element rather than on the
     * song, locating the edit position from that element. An element that is not in a
     * document — a detached attachment owner, a clipboard fragment, a dialog test double —
     * has no position to validate forward from, so the edit simply runs.
     *
     * @param owner the element the edited attachment hangs on, or {@code null} if detached
     * @param edit  the raw state change
     * @return {@code true} if at least one tuplet was removed
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean withBeatDefiningEditOn(@Nullable StaffElement owner, Runnable edit) {
        return ModificationSession.withBeatDefiningEditOn(owner, edit);
    }

    /**
     * Records that the edit in flight cost the user one or more tuplets, so a single
     * {@link TupletsWereRemovedNotification} goes up once the outermost modification bracket
     * closes. The beat-edit chokepoint arms this itself; it is public for the paste path,
     * which drops tuplets outside {@code dom} and must warn on the same terms.
     *
     * @param cause what the user did that removed them
     * @see ModificationSession#noteTupletsWereRemoved
     */
    public void noteTupletsWereRemoved(TupletsWereRemovedNotification.Cause cause) {
        modifications.noteTupletsWereRemoved(cause);
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
    public void metadataDidChange(SongMetadataDidChangeNotification update) {
        setMetadata(update.getMetadata());
    }

    /**
     * Replaces the song's {@link Tempo}, recording the before and after values for undo.
     *
     * <p>A {@code Tempo} is a value, so the recorded "before" is simply the instance the song
     * held: nothing can rewrite it afterwards, and the undo step keeps the values it was
     * recorded with however many tempo edits follow.
     */
    @Handler
    public void tempoDidChange(TempoDidChangeNotification update) {
        // Capture in a local so the lambda below closes over what the song holds now rather
        // than re-reading a field the replayer may have reassigned.
        var currentTempo = tempo;

        // An update whose values already match must not dirty the undo step or run a
        // beat-defining edit — the settings dialog seeds its widgets from getTempo(), so
        // confirming it unedited resends exactly what is already there.
        var newTempo = update.getTempo();

        if (currentTempo.equals(newTempo)) {
            return;
        }

        // Only the tempo type is the song's beat. A BPM, description or show-tempo edit changes
        // how the marking reads and nothing about the notation, so it must not drag the whole
        // song through a tuplet revalidation — and must not be able to remove a tuplet.
        var redefinesBeat = !Tempo.haveSameBeat(currentTempo, newTempo);

        // The assignment happens inside the bracket because withBeatDefiningEdit must run the
        // change itself in order to invalidate tuplets against the new beat.
        withModification(() -> applyChange(
            new MetadataChange(MetadataField.TEMPO, currentTempo, newTempo),
            () -> {
                if (redefinesBeat) {
                    withBeatDefiningEdit(FIRST_LINE_INDEX, FIRST_ELEMENT_INDEX,
                        () -> tempo = newTempo);
                    return;
                }

                tempo = newTempo;
            }
        ));
    }

    @Handler
    public void layoutDidChange(LayoutDidChangeNotification update) {
        withModification(() -> {
            if (update.getRowHeightAdjustmentSs() != null) {
                setRowHeightAdjustmentSs(update.getRowHeightAdjustmentSs());
            }

            if (update.getLineWidthSs() != null) {
                setLineWidthSs(new Ss(update.getLineWidthSs()));
            }
        });
    }

    // ========== Private helpers ==========

    // -- Apply methods (field mutation only, no side effects) --

    private void applyUnderLyrics(String text) {
        underLyrics = StringUtils.processText(text, true);
    }

    private void applyBanglaLyrics(String text) {
        banglaLyrics = text.trim();
    }

    private void applyTranslatedLyrics(String text) {
        translatedLyrics = StringUtils.processText(text, false);
    }

    private void applyFootnotes(String text) {
        footnotes = StringUtils.processText(text, false);
    }

    private void applyRowHeightAdjustmentSs(double rowHeightAdjustment) {
        rowHeightAdjustmentSs = rowHeightAdjustment;
    }

    private void applyLineWidthSs(double lineWidth) {
        lineWidthSs = lineWidth;
    }

}
