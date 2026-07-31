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
import java.util.function.Supplier;

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
import songscribe.message.notification.SongMetadataDidChangeNotification;
import songscribe.message.notification.TempoDidChangeNotification;
import songscribe.message.notification.TupletsWereRemovedNotification;
import songscribe.undo.UndoController;
import songscribe.util.StringUtils;

/**
 * This class serves as the model for data that is read from and written to
 * SongScribe files.
 */
public final class Song {

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

    public static final int DEFAULT_KEY_ACCIDENTAL_COUNT = 5;
    public static final KeyType DEFAULT_KEY_TYPE = KeyType.FLATS;

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

    // The base tempo of the song; null means the song has no explicit tempo
    @Nullable
    private Tempo tempo;

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

    // Rendering surface for the attribution block — owned here so layout and render
    // both share the same cached measurement without LineComponent holding state.
    // setSong is called in the instance initializer below so every constructor path
    // wires the pane to this Song before any constructor body runs.
    private final AttributionPane attributionPane = new AttributionPane();

    // The number of accidentals in the key signature and the type of key (flats or sharps)
    private int defaultKeyAccidentalCount;
    private KeyType defaultKeyType = DEFAULT_KEY_TYPE;

    private double rowHeightAdjustmentSs = 0;

    // Line-wide rest length driving derived column spacing (#330); persisted in the MusicXML
    // header. Clamped to at least MIN_DEFAULT_REST_LENGTH_SS on every set.
    private double defaultRestLengthSs = DEFAULT_REST_LENGTH_SS;

    // The width of a staff line in staff-space units
    private double lineWidthSs = defaultLineWidthProvider.getAsDouble();

    // The lines of the score
    private final ArrayList<Line> lines = new ArrayList<>();

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

    // Replay depth counter. While > 0, undo/redo is mechanically re-applying a
    // recorded mutation batch: mutations are still recorded into the open
    // bracket, but companion side-work (auto-maintenance, span invalidation,
    // merging) is suppressed and the Line terminal guards are bypassed, because
    // the recorded batch already contains every change.
    private int replayDepth = 0;

    // Nesting depth of withBeatDefiningEdit, so that an inner beat-defining write that
    // does the removing is still reported by the outermost call, which is the one whose
    // return value the initiating UI reads.
    private int beatDefiningEditDepth = 0;

    // Whether the outermost in-flight beat-defining edit has removed any tuplet. Reset
    // when that outermost call unwinds.
    private boolean beatEditRemovedTuplets = false;

    // Why the in-flight edit removed tuplets, or null when it removed none. Armed by the
    // outermost beat-defining edit and by the paste path, and disarmed by endModification,
    // which posts TupletsWereRemovedNotification. Deferring the post to the outermost
    // bracket keeps a modal warning from going up while the score is still painted as it
    // was before the removals, and collapses an edit that nests several beat-defining
    // writes into the one report the user should see.
    private TupletsWereRemovedNotification.@Nullable Cause tupletsRemovedNoticeCause = null;

    // While positive, Line mutation guards are bypassed so Song can auto-maintain
    // the terminal invariant without triggering the guards that protect against
    // user-driven invariant violations. Depth-counted because the maintenance
    // paths nest: removeLine calls addLine to repopulate an emptied song, and the
    // inner call must not disarm the guards the outer one is still relying on.
    private int autoMaintenanceDepth = 0;

    @Nullable
    private ArrayList<Mutation> accumulatedMutations;

    // The op-name resolved when the outermost bracket opened (depth 0→1), held for
    // the lifetime of that bracket and shipped on the SongDidChangeNotification at
    // depth 1→0. Resolves an explicit bracket label, or the Tier-A pending op-name
    // held by UndoController. EDT-only, no synchronization.
    @Nullable
    private String capturedOpName;

    // Wire the pane to this Song instance — called at the start of every constructor.
    private void init() {
        attributionPane.setSong(this);
    }

    public Song() {
        init();
        defaultKeyAccidentalCount = DEFAULT_KEY_ACCIDENTAL_COUNT;

        // Suspend mutation tracking so that setup changes don't post a spurious
        // SongDidChangeNotification to global subscribers before this Song is
        // installed in any ScoreView.
        withoutMutationTracking(() -> {
            var initialLine = new Line(this);
            initialLine.setKeyAccidentalCount(defaultKeyAccidentalCount);
            initialLine.setKeyType(defaultKeyType);
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
        init();
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
        init();
        MessageCenter.subscribe(this);
    }

    /**
     * Detaches this Song from the message bus so it stops handling broadcast
     * command notifications (metadata/tempo/key, etc.). MBassador holds listeners
     * by weak reference, so a discarded Song keeps responding to broadcasts — and
     * posting spurious undo steps against the dead document — until it is
     * garbage-collected. Call this when replacing the active Song to make the
     * detach deterministic rather than GC-timing-dependent.
     */
    public void unsubscribeFromBus() {
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

        // Apply remaining scalar fields
        tempo = data.tempo();
        applyUnderLyrics(data.underLyrics());
        applyBanglaLyrics(data.banglaLyrics());
        applyTranslatedLyrics(data.translatedLyrics());
        applyFootnotes(data.footnotes());
        applyDefaultKeyAccidentalCount(data.defaultKeyAccidentalCount());
        applyDefaultKeyType(data.defaultKeyType());

        // Apply layout
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
        applyLineDefaults(line);
        return line;
    }

    private void applyLineDefaults(Line line) {
        if ((line.getKeyAccidentalCount() == 0) && (line.getKeyType() == null)) {
            line.setKeyAccidentalCount(defaultKeyAccidentalCount);
            line.setKeyType(defaultKeyType);
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
     *
     * <p>When no tempo is set, each call returns a <em>fresh, unattached</em>
     * {@link Tempo}: the identity is not stable across calls, and mutating the
     * returned instance silently discards the edit rather than changing the song.
     * To modify the song's tempo, use {@link #setTempo} instead.
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
        var found = walkBackFrom(lineIndex, noteIndex,
            element -> element.findAttachment(TempoChangeAttachment.class));

        if (found == null) {
            return getEffectiveTempo();
        }

        return found.value().getTempo();
    }

    /** Where a backward walk stopped, and what it found there. */
    private record Found<T>(T value, int lineIndex, int elementIndex) {}

    /**
     * Examines one element during a backward walk, returning what it defines or null when
     * it defines nothing. A dedicated interface rather than {@code Function} so the nullable
     * return is part of the contract.
     */
    @FunctionalInterface
    private interface BackwardProbe<T> {
        @Nullable T examine(StaffElement element);
    }

    /**
     * Walks backward from a position — through the rest of that line, then through every
     * earlier line in full — and returns the first element for which {@code probe} produces
     * a non-null value, together with where it was found.
     *
     * <pre>
     * walkBackFrom(line 3, element 5)
     *
     *   line 0   [e0 e1 e2 ... eN]   &lt;- scanned in full, backward
     *   line 1   [e0 e1 e2 ... eN]   &lt;- scanned in full, backward
     *   line 2   [e0 e1 e2 ... eN]   &lt;- scanned in full, backward
     *   line 3   [e0 e1 e2 e3 e4 e5] &lt;- scanned backward from e5 only
     *                             ^
     *                             start
     * </pre>
     *
     * <p>Cost is O(elements before the start), with no cache. Both callers — the tempo
     * lookup and the beat lookup — share this walk so that a change to what "before this
     * position" means cannot be fixed in one and forgotten in the other.
     *
     * @return the first hit, or null when the walk reached the start of the song without one
     */
    private <T> @Nullable Found<T> walkBackFrom(
        int lineIndex, int elementIndex, BackwardProbe<? extends T> probe
    ) {
        var lastLine = true;

        for (var i = lineIndex; i >= 0; i--) {
            var currentLine = lines.get(i);

            for (
                var index = lastLine ? elementIndex : (currentLine.elementCount() - 1);
                index >= 0;
                index--
            ) {
                var value = probe.examine(currentLine.getElement(index));

                if (value != null) {
                    return new Found<>(value, i, index);
                }
            }

            lastLine = false;
        }

        return null;
    }

    /**
     * The beat in effect at a position, together with the position of the event that
     * defined it. Callers use {@code lineIndex}/{@code elementIndex} to recognize a beat
     * barrier — an element that redefines the beat — inside a span they are examining.
     *
     * <p>When the beat comes from the song's own tempo or from the quarter-note default,
     * no element defined it and both indexes are {@link #NO_DEFINING_EVENT}.
     */
    public record BeatAt(Duration beat, int lineIndex, int elementIndex) {

        /** Index value meaning "no element defined this beat". */
        public static final int NO_DEFINING_EVENT = -1;
    }

    /**
     * Returns the beat in effect at a position, found by walking backward from that
     * position to the nearest preceding beat-defining event.
     *
     * <pre>
     * resolveBeatAt(line 3, element 5)
     *
     *   line 0   [e0 e1 e2 ... eN]   &lt;- scanned in full, backward
     *   line 1   [e0 e1 e2 ... eN]   &lt;- scanned in full, backward
     *   line 2   [e0 e1 e2 ... eN]   &lt;- scanned in full, backward
     *   line 3   [e0 e1 e2 e3 e4 e5] &lt;- scanned backward from e5 only
     *                             ^
     *                             anchor
     *
     *   first hit wins, whichever kind it is:
     *       BeatChangeAttachment  -&gt; beatChange().beat()
     *       TempoChangeAttachment -&gt; tempo().tempoType()
     *   no hit -&gt; song tempo -&gt; quarter note
     * </pre>
     *
     * <p>Precedence is positional, not by type: the nearest preceding beat-defining event
     * wins regardless of which kind it is. When one element carries both, the
     * {@link BeatChangeAttachment} wins, because a metric-modulation marking is the more
     * specific statement about the beat than a tempo marking's note value.
     *
     * <p>Cost is O(elements before the anchor), with no cache.
     * {@code Line.attachInitialTempoIfNeeded} guarantees a beat-defining event at line 0,
     * element 0 whenever the song has a tempo, so the walk terminates quickly in practice.
     * A maintained beat index on {@code Song} was rejected: it would trade microseconds for
     * an invalidation invariant that every structural mutation would have to honor, and a
     * stale index produces exactly the silent wrong-beat failure this method exists to
     * prevent.
     *
     * @param lineIndex    the index of the line
     * @param elementIndex the index of the anchor element within that line
     * @return the beat in effect at this position and the position that defined it
     */
    public BeatAt resolveBeatAt(int lineIndex, int elementIndex) {
        var found = walkBackFrom(lineIndex, elementIndex, Song::beatDefinedAt);

        if (found != null) {
            return new BeatAt(found.value(), found.lineIndex(), found.elementIndex());
        }

        if (tempo == null) {
            return new BeatAt(Duration.CROTCHET, BeatAt.NO_DEFINING_EVENT, BeatAt.NO_DEFINING_EVENT);
        }

        return new BeatAt(tempo.getTempoType(), BeatAt.NO_DEFINING_EVENT, BeatAt.NO_DEFINING_EVENT);
    }

    /**
     * The beat {@code element} defines, or null when it defines none.
     *
     * <p>When one element carries both kinds of marking the metric modulation wins, because
     * it is the more specific statement about the beat than a tempo marking's note value.
     * This is the single definition of that precedence: {@code TupletValidator}'s forward
     * walk calls it too, so the two cannot answer differently for the same element.
     */
    static @Nullable Duration beatDefinedAt(StaffElement element) {
        var beatChange = element.findAttachment(BeatChangeAttachment.class);

        if (beatChange != null) {
            return beatChange.getBeatChange().beat();
        }

        var tempoChange = element.findAttachment(TempoChangeAttachment.class);

        if (tempoChange != null) {
            return tempoChange.getTempo().getTempoType();
        }

        return null;
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

    /** Returns the attribution rendering surface owned by this Song. */
    public AttributionPane getAttributionPane() {
        return attributionPane;
    }

    public String getNumber() {
        return metadata.number();
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

    /**
     * Sets the song's initial tempo, dropping any tuplet the new beat invalidates.
     *
     * <p>Stays public because the undo replayer and the MusicXML header reader both
     * legitimately drive it from outside the editing UI. Bypassing it is still not possible:
     * the write itself is routed through {@link #withBeatDefiningEdit}, which no-ops its
     * validation during replay and during a suspended load.
     */
    public void setTempo(@Nullable Tempo tempo) {
        mutateMetadata(MetadataField.TEMPO, this.tempo, tempo,
            () -> withBeatDefiningEdit(FIRST_LINE_INDEX, FIRST_ELEMENT_INDEX, () -> this.tempo = tempo));
    }

    /**
     * Clears the song-level initial tempo if {@code element} losing its tempo change
     * would orphan it. The song-level tempo is mirrored onto the first element of the
     * first line on every reload by {@code attachInitialTempoIfNeeded}, so it must be
     * cleared when:
     * <ul>
     *   <li>{@code element} is the first element of the first line (the only place the
     *       initial tempo is anchored), or</li>
     *   <li>no per-note tempo changes remain anywhere — otherwise the song-level tempo
     *       would be re-attached to the first element on the next reload.</li>
     * </ul>
     */
    public void clearTempoIfOrphaned(StaffElement element) {
        var line = element.getLine();

        if (line.isInitialTempoAnchor(line.getElementIndex(element)) || !hasAnyTempoChange()) {
            setTempo(null);
        }
    }

    /**
     * The element the song's initial tempo is anchored on — the first element of the first
     * line, per {@link Line#isInitialTempoAnchor} — or null when the song has no lines or
     * its first line is empty.
     */
    public @Nullable StaffElement initialTempoAnchor() {
        if (lines.isEmpty()) {
            return null;
        }

        var firstLineElements = lines.getFirst().getElements();

        return firstLineElements.isEmpty() ? null : firstLineElements.getFirst();
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
            () -> {
                metadata = newMetadata;
                // Invalidate the attribution pane's measure cache so the next
                // layout/render picks up the updated metadata.
                attributionPane.invalidateCache();
            }
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
        // showTranslation() — and thus the attribution's translation credit —
        // is derived from translatedLyrics, so invalidate the pane's measure
        // cache when the value actually changes.
        mutateLyrics(LyricsField.TRANSLATED, translatedLyrics, newLyrics, () -> {
            translatedLyrics = newLyrics;
            attributionPane.invalidateCache();
        });
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

                // A replayed line (undo of a deletion) carries its own key
                // state — a keyless (0, null) line would otherwise be
                // clobbered with the document default.
                if (!isReplaying()) {
                    applyLineDefaults(line);
                }
            });

            if (willBecomeNewLast && !isMutationTrackingSuspended() && !isReplaying()) {
                maintainTerminalOnLastLineChange(previousLastLine, line);
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
     * <pre>
     *  withModification {
     *    incrementAutoMaintenance {
     *      applyChange(LineDeletion(index, removed), …)
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

        withModification(() -> incrementAutoMaintenance(() -> {
            applyChange(
                new LineDeletion(index, deletedLine),
                () -> lines.remove(index)
            );

            if (wasLast && !isMutationTrackingSuspended() && !isReplaying()) {
                if (!lines.isEmpty()) {
                    maintainTerminalOnLastLineChange(null, lines.getLast());
                } else {
                    addLine(0, new Line(this));
                }
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
     * Restores the terminal invariant after a {@link #newParsingStub() parsing stub}
     * has been fully populated: ensures the song's last line ends with a valid
     * terminal ({@link ElementType#isValidTerminal()}). File readers suspend mutation
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
        if (lines.isEmpty()) {
            return;
        }

        var lastLine = lines.getLast();
        var lastIdx = lastLine.elementCount() - 1;

        if (lastIdx >= 0 && lastLine.getElement(lastIdx).getType().isValidTerminal()) {
            return;
        }

        maintainTerminalOnLastLineChange(null, lastLine);
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
        return autoMaintenanceDepth > 0;
    }

    /**
     * Runs {@code body} with the auto-maintenance depth raised so that the terminal
     * guards in {@link Line} are bypassed for the duration. Used by {@link #addLine} and
     * {@link #removeLine} to transfer the terminal without triggering the guard.
     *
     * <p>Re-entrant: {@link #removeLine} nests an {@link #addLine} call to repopulate a
     * song emptied by removing its sole line, so the guards stay bypassed until the
     * outermost call unwinds.
     */
    private void incrementAutoMaintenance(Runnable body) {
        autoMaintenanceDepth++;

        try {
            body.run();
        } finally {
            autoMaintenanceDepth--;
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
     * Runs {@code body} in replay mode. Used by the undo engine while it
     * mechanically re-applies a recorded mutation batch inside an open
     * modification bracket: the batch already contains every change, so the
     * helpers' companion side-work (terminal maintenance, line defaults, span
     * invalidation, tuplet auto-removal, span merging) must not re-run — it
     * would double-apply changes the batch carries — and the {@link Line}
     * terminal guards must accept the legitimate intermediate states that
     * arise mid-replay. Mutations are still recorded into the bracket, unlike
     * {@link #withoutMutationTracking(Runnable)}. Nestable.
     */
    public void withReplay(Runnable body) {
        replayDepth++;

        try {
            body.run();
        } finally {
            replayDepth--;
        }
    }

    /** Returns {@code true} while a recorded mutation batch is being replayed. */
    public boolean isReplaying() {
        return replayDepth > 0;
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
     * Opens a modification bracket with no explicit label. Mutations accumulate while
     * the bracket is open. Brackets may be nested; the notification fires only when the
     * outermost bracket closes.
     */
    public void beginModification() {
        beginModification(null);
    }

    /**
     * Opens a modification bracket, declaring {@code explicitLabel} as the op-name
     * (Tier B) if this is the outermost bracket. Mutations accumulate while the bracket
     * is open; the notification fires only when the outermost bracket closes.
     *
     * <p>The op-name is captured only at the depth 0→1 transition, resolving as
     * {@code explicitLabel != null ? explicitLabel : pendingOpName}. A nested labeled
     * bracket inside an already-open bracket never re-captures.
     */
    public void beginModification(@Nullable String explicitLabel) {
        if (modificationDepth == 0) {
            // UndoController holds the Tier-A pending op-name, set by the UIAction
            // template around synchronous action dispatch (see UIAction.actionPerformed);
            // explicitLabel is the Tier-B name from the labeled withModification overload.
            capturedOpName = explicitLabel != null ? explicitLabel : UndoController.getPendingOpName();
        }

        modificationDepth++;
    }

    /**
     * Closes a modification bracket. When the outermost bracket closes and at least one
     * mutation was accumulated, marks the song modified and posts a single
     * {@link SongDidChangeNotification} carrying all accumulated mutations.
     *
     * <p>A beat-defining edit that removed tuplets also reports itself here, after the
     * song notification — this is the only point that knows the whole edit is over, and a
     * warning shown any earlier would sit in front of a score not yet relaid out.
     */
    public void endModification() {
        modificationDepth--;

        if (modificationDepth != 0) {
            return;
        }

        if (accumulatedMutations != null) {
            modified = true;
            // Wrap-and-transfer ownership: the notification constructor stores the
            // list directly, so we wrap once here instead of letting it defensively
            // copy a list whose only reference is about to be dropped.
            var mutations = Collections.unmodifiableList(accumulatedMutations);
            accumulatedMutations = null;
            var opName = capturedOpName;
            capturedOpName = null;
            MessageCenter.post(new SongDidChangeNotification(mutations, this, opName));
        }

        if (tupletsRemovedNoticeCause != null) {
            var cause = tupletsRemovedNoticeCause;
            tupletsRemovedNoticeCause = null;
            MessageCenter.post(new TupletsWereRemovedNotification(cause));
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
     * The value-returning form of {@link #withModification(Runnable)}, for a body
     * whose outcome the caller must inspect after the bracket closes.
     *
     * @param body The modification to run
     * @return Whatever {@code body} returns
     */
    public <T> T withModificationResult(Supplier<T> body) {
        beginModification();

        try {
            return body.get();
        } finally {
            endModification();
        }
    }

    /**
     * Applies a beat-defining state change and, inside the same modification bracket,
     * removes every tuplet at or after the edit position that the new beat context
     * invalidates.
     *
     * <p>A tuplet's validity is defined relative to the beat in effect at its anchor, so a
     * write that defines a beat can invalidate a tuplet that nothing went near: changing the
     * song's own tempo, or an earlier tempo change, can turn an attachment already sitting
     * inside a span into a beat barrier. That is why this is a chokepoint rather than a
     * hand-maintained list of call sites, and why it is not a
     * {@code SongDidChangeNotification} subscriber — the notification fires after the
     * outermost bracket closes, so the removals would land in a second undo step.
     *
     * <p>{@code edit} must perform the raw state change only, without recording its own
     * mutation. Callers that do record one invoke this from inside their
     * {@link #applyChange} mutator: the removals are then recorded while the mutator runs
     * and land ahead of the primary mutation, which is the companion ordering reverse-order
     * undo needs (see {@code .agents/guides/mutations.md}).
     *
     * <p>Nothing is validated during replay — the recorded batch already carries the
     * removals — or while mutation tracking is suspended, since a file load judges its
     * tuplets in the load pass under {@link TupletValidator.Strictness#LENIENT} instead.
     *
     * <p>Nested calls aggregate: an inner beat-defining write does the removing, and the
     * outermost call still reports it, so a caller that wraps a self-routing setter gets a
     * truthful answer.
     *
     * <p>When anything was removed, a single {@link TupletsWereRemovedNotification} is
     * posted once the outermost modification bracket closes, so every route into a
     * beat-defining edit warns the user on the same terms without {@code dom} knowing what
     * a dialog is. The return value is for callers that need the fact locally; nobody has
     * to read it to get the warning.
     *
     * @param lineIndex    the index of the line the edit sits on
     * @param elementIndex the index of the element within that line
     * @param edit         the raw state change
     * @return {@code true} if at least one tuplet was removed
     */
    public boolean withBeatDefiningEdit(int lineIndex, int elementIndex, Runnable edit) {
        beatDefiningEditDepth++;

        try {
            return withModificationResult(() -> {
                edit.run();

                if (!isReplaying()
                    && !isMutationTrackingSuspended()
                    && removeTupletsInvalidatedFrom(lineIndex, elementIndex)) {
                    beatEditRemovedTuplets = true;
                }

                // Arm from the outermost beat-defining edit only — an inner write that did
                // the removing is reported by its outermost caller, not on its own. Armed
                // here rather than after the bracket closes because this call may itself be
                // the outermost bracket, in which case endModification has already run by
                // the time this method unwinds.
                if (beatDefiningEditDepth == 1 && beatEditRemovedTuplets) {
                    noteTupletsWereRemoved(TupletsWereRemovedNotification.Cause.BEAT_EDIT);
                }

                return beatEditRemovedTuplets;
            });
        } finally {
            beatDefiningEditDepth--;

            if (beatDefiningEditDepth == 0) {
                beatEditRemovedTuplets = false;
            }
        }
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
    public static boolean withBeatDefiningEditOn(@Nullable StaffElement owner, Runnable edit) {
        var line = owner != null ? owner.getLine() : null;

        //noinspection ConstantValue — the line field is unset on a detached element.
        if (owner == null || line == null) {
            edit.run();
            return false;
        }

        var song = line.getSong();
        var lineIndex = song.indexOfLine(line);
        var elementIndex = line.getElementIndex(owner);

        if (lineIndex < 0 || elementIndex < 0) {
            edit.run();
            return false;
        }

        return song.withBeatDefiningEdit(lineIndex, elementIndex, edit);
    }

    /**
     * Records that the edit in flight cost the user one or more tuplets, so a single
     * {@link TupletsWereRemovedNotification} goes up once the outermost modification bracket
     * closes. Calling it more than once within one bracket still yields one notification;
     * the first cause recorded is the one reported, since it names the action the user took.
     *
     * <p>The beat-edit chokepoint arms this itself. It is public for the paste path, which
     * drops tuplets outside {@code dom} and must warn on the same terms — the alternative
     * being a second, differently-timed warning the user would have no way to relate to the
     * first.
     *
     * <p>Does nothing during undo/redo replay or while mutation tracking is suspended:
     * neither re-derives the removals, so neither should re-announce them.
     *
     * @param cause what the user did that removed them
     */
    public void noteTupletsWereRemoved(TupletsWereRemovedNotification.Cause cause) {
        if (isReplaying() || isMutationTrackingSuspended() || tupletsRemovedNoticeCause != null) {
            return;
        }

        tupletsRemovedNoticeCause = cause;
    }

    /**
     * Removes every tuplet anchored at or after the given position that no longer validates
     * under {@link TupletValidator.Strictness#STRICT}. Only positions from the edit forward
     * are walked: a beat-defining event cannot reach backwards.
     *
     * @return {@code true} if at least one tuplet was removed
     */
    private boolean removeTupletsInvalidatedFrom(int lineIndex, int elementIndex) {
        if (lineIndex < 0 || lineIndex >= lineCount()) {
            return false;
        }

        var editLine = getLine(lineIndex);

        // An edit position outside the line — the song tempo's nominal position in a song
        // with no notes yet — has no beat to resolve and no span to reach.
        if (elementIndex < 0 || elementIndex >= editLine.elementCount()) {
            return false;
        }

        var startIndex = elementIndex;
        var enclosingTuplet = editLine.findTupletAt(elementIndex);

        // A tuplet the edit landed inside is anchored before the edit, and the forward walk
        // only opens a span at its anchor — so back the start up to the anchor, or the one
        // tuplet the edit is most likely to have broken would never be judged.
        if (enclosingTuplet != null && enclosingTuplet.getAnchorElementIndex() >= 0) {
            startIndex = Math.min(startIndex, enclosingTuplet.getAnchorElementIndex());
        }

        var removedAny = false;

        for (var verdict : TupletValidator.validateFrom(
            this, lineIndex, startIndex, TupletValidator.Strictness.STRICT)
        ) {
            if (!verdict.result().valid()) {
                getLine(verdict.lineIndex()).removeTuplet(verdict.tuplet());
                removedAny = true;
            }
        }

        return removedAny;
    }

    /**
     * Executes {@code body} inside a modification bracket that declares {@code label}
     * as its op-name (Tier B), then posts a single {@link SongDidChangeNotification}.
     * The label is captured only if this is the outermost bracket (see
     * {@link #beginModification(String)}).
     */
    public void withModification(String label, Runnable body) {
        beginModification(label);

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
     * Like {@link #postWithModification(Message)} but declares an explicit undo
     * op-name {@code label} for the resulting batch (see
     * {@link #withModification(String, Runnable)}).
     */
    public void postWithModification(String label, Message message) {
        withModification(label, () -> MessageCenter.post(message));
    }

    /**
     * Applies a single mutation within an open modification bracket.
     * <p>
     * Runs {@code mutator}, then records {@code mutation} in the accumulated list.
     * <p>
     * <pre>
     * withModification([label], () -&gt; {                     ┐
     *   ├─ depth 0 → 1: capturedOpName =                    │
     *   │     label != null ? label : pendingOpName         │
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
     *   └─ post SongDidChangeNotification(                   │
     *          accumulated, capturedOpName)          ────────┘
     * </pre>
     *
     * @throws IllegalStateException if called outside a modification bracket
     */
    public void applyChange(Mutation mutation, Runnable mutator) {
        // Suspended tracking (e.g. SAX file load): apply the state change but
        // record nothing, so no SongDidChangeNotification fires mid-load. Mirrors
        // the guard in Line.applyChange for song-scoped setters that call here
        // directly (e.g. setMetadata).
        if (isMutationTrackingSuspended()) {
            mutator.run();
            return;
        }

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
    public void metadataDidChange(SongMetadataDidChangeNotification update) {
        setMetadata(update.getMetadata());
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
            // The tempo type is the song's beat, so this in-place update is a
            // beat-defining write even though only a field of an existing Tempo changes.
            () -> withBeatDefiningEdit(FIRST_LINE_INDEX, FIRST_ELEMENT_INDEX, () -> {
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
            })
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
            if (update.getRowHeightAdjustmentSs() != null) {
                setRowHeightAdjustmentSs(update.getRowHeightAdjustmentSs());
            }

            if (update.getLineWidthSs() != null) {
                setLineWidthSs(update.getLineWidthSs());
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

    private void applyDefaultKeyType(KeyType keyType) {
        defaultKeyType = keyType;
    }

    private void applyDefaultKeyAccidentalCount(int count) {
        defaultKeyAccidentalCount = count;
    }

    private void applyRowHeightAdjustmentSs(double rowHeightAdjustment) {
        rowHeightAdjustmentSs = rowHeightAdjustment;
    }

    private void applyLineWidthSs(double lineWidth) {
        lineWidthSs = lineWidth;
    }

}
