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
package songscribe.io.musicxml;

import java.awt.Font;
import java.io.PrintWriter;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import songscribe.Constants;
import songscribe.Version;
import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Beam;
import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Duration;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.font.DocumentFontsHolder;
import songscribe.font.FontKey;
import songscribe.io.XML;
import songscribe.layout.BeamMath;
import songscribe.layout.LineEndingSupport;
import songscribe.util.DateUtils;

public final class MusicXmlWriter {

    // DIVISIONS is defined in NoteTypeMapping (which owns the tick math).
    // DIVISIONS = 480 ensures that the smallest representable note fraction
    // — a double-dotted 32nd — produces an exact integer tick count:
    //   (480 / 8) × 7/4  =  60 × 7/4  =  105 ticks  (exact)
    private static final int DIVISIONS = NoteTypeMapping.DIVISIONS;

    // Measure numbering starts at 1 (MusicXML spec requires positive integers).
    private static final int FIRST_MEASURE_NUMBER = 1;

    // Each diatonic step = ½ staff space = 5 tenths.  Used to compute the
    // grace-note stem-tip Y: staffPosition × -5 gives tenths above the middle
    // staff line (positive = up in MusicXML; positions increase downward in
    // SongScribe, so the sign is negated).
    private static final int TENTHS_PER_STAFF_POSITION = 5;

    // Standard upward stem extension above a grace notehead: 3.5 staff spaces
    // = 35 tenths. Added to the note's tenths-from-middle-line to give the
    // stem-tip default-y.
    private static final int GRACE_STEM_EXTENSION_TENTHS = 35;

    // <staff-distance> is write-forward: SongScribe's single-staff model has no
    // inter-staff spacing, so a zero distance is emitted and ignored on read.
    private static final String STAFF_DISTANCE_TENTHS = "0";

    private MusicXmlWriter() {}

    // -------------------------------------------------------------------------
    // Per-element-index span precompute types
    //
    // IndexSpanMarkers holds all span activity for one element index.
    // The noteMarkers field (per-note spans) is threaded into NoteWriteContext
    // so writeNote/writeNotations can do O(1) lookups; the hairpin and ending
    // fields are consumed by the measure-level element loop (Phase 3).
    // -------------------------------------------------------------------------

    private record IndexSpanMarkers(
        NoteSpanMarkers noteMarkers,
        List<Hairpin> hairpinsStartingHere,
        List<Hairpin> hairpinsEndingHere,
        List<EndingMarker> endingLeftBarlineMarkers,
        List<EndingMarker> endingRightBarlineMarkers
    ) {}

    /**
     * One {@code <ending number type>} child to fold onto a {@code <barline>}.
     * The structural anchor/split/end → volta mapping is resolved once in
     * {@link #buildSpanIndex} and stored as left-barline / right-barline marker
     * lists per element index (see the ASCII diagram there).
     */
    private record EndingMarker(String number, String type) {}

    /**
     * Mutable builder for one element's {@link IndexSpanMarkers}. Created once
     * per element index during {@link #buildSpanIndex}, discarded after the
     * final records are assembled.
     */
    private static final class SpanBuilder {

        // Empty array = not in a beam group (matches NoteSpanMarkers sentinel).
        String[] beamLevelValues = new String[0];
        @Nullable Tie tieStart;
        @Nullable Tie tieStop;
        @Nullable Tuplet tuplet;
        boolean isTupletAnchor;
        boolean isTupletEnd;
        @Nullable Trill trill;
        boolean isTrillAnchor;
        boolean isTrillEnd;

        // Lazily allocated: most indices carry no hairpin or ending markers, so
        // these stay null (and resolve to a shared empty list in build()) until
        // the first marker is bucketed here. Crescendos and diminuendos share one
        // pair of buckets — the wedge type is recovered from the Hairpin subtype.
        @Nullable List<Hairpin> hairpinsStartingHere;
        @Nullable List<Hairpin> hairpinsEndingHere;
        @Nullable List<EndingMarker> endingLeftBarlineMarkers;
        @Nullable List<EndingMarker> endingRightBarlineMarkers;

        IndexSpanMarkers build() {
            var noteMarkers = new NoteSpanMarkers(
                beamLevelValues,
                tieStart, tieStop,
                tuplet, isTupletAnchor, isTupletEnd,
                trill, isTrillAnchor, isTrillEnd
            );

            return new IndexSpanMarkers(
                noteMarkers,
                orEmpty(hairpinsStartingHere), orEmpty(hairpinsEndingHere),
                orEmpty(endingLeftBarlineMarkers), orEmpty(endingRightBarlineMarkers)
            );
        }
    }

    /** Appends {@code value} to {@code list}, allocating the list on first use. */
    private static <T> List<T> appendLazily(@Nullable List<T> list, T value) {
        var target = list == null ? new ArrayList<T>() : list;
        target.add(value);
        return target;
    }

    /** Returns {@code list}, or a shared empty list when it was never allocated. */
    private static <T> List<T> orEmpty(@Nullable List<T> list) {
        return list == null ? List.of() : list;
    }

    /**
     * Writes {@code song} to {@code pw} as MusicXML, using {@code fonts} for the
     * document-level font roles. Uses the system-default {@link Clock} for the
     * write-forward {@code <rights>} year and {@code <encoding-date>}.
     *
     * @param song  the song to serialize
     * @param fonts the document fonts to emit under {@code <defaults>}/{@code <credit>}
     * @param pw    the writer to emit the MusicXML document to
     */
    public static void writeSong(Song song, DocumentFontsHolder fonts, PrintWriter pw) {
        writeSong(song, fonts, pw, Clock.systemDefaultZone());
    }

    /**
     * Writes {@code song} to {@code pw} as MusicXML. The {@code clock} is
     * injectable so the write-forward {@code <rights>} year and
     * {@code <encoding-date>} are deterministic under test.
     *
     * @param song  the song to serialize
     * @param fonts the document fonts to emit under {@code <defaults>}/{@code <credit>}
     * @param pw    the writer to emit the MusicXML document to
     * @param clock the clock supplying the current date for write-forward fields
     */
    public static void writeSong(Song song, DocumentFontsHolder fonts, PrintWriter pw, Clock clock) {
        pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");

        XML.resetIndent();
        XML.writeBeginTag(pw, MusicXmlTags.SCORE_PARTWISE, MusicXmlTags.ATTR_VERSION, MusicXmlTags.VERSION_VALUE);
        XML.indent();

        // Resolve every clock- and date-derived header value once, so the
        // <miscellaneous> block and the <credit> list share a single source for
        // the composition/lyrics dates (and the lyrics-date-equals-composition
        // dedup) and the <rights>/<encoding-date> strings.
        var headerText = HeaderText.of(song, clock);

        writeMovementInfo(song, pw);
        writeIdentification(song, fonts, headerText, pw);
        writeDefaults(song, fonts, pw);
        writeCredits(song, fonts, headerText, pw);

        XML.writeBeginTag(pw, MusicXmlTags.PART_LIST);
        XML.indent();

        // <score-part> and its child are emitted inline on one line.
        XML.printIndent(pw);
        pw.println("<" + MusicXmlTags.SCORE_PART + " " + MusicXmlTags.ATTR_ID + "=\"" + MusicXmlTags.PART_ID + "\"><part-name></part-name></" + MusicXmlTags.SCORE_PART + ">");

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.PART_LIST);

        XML.writeBeginTag(pw, MusicXmlTags.PART, MusicXmlTags.ATTR_ID, MusicXmlTags.PART_ID);
        XML.indent();

        if (song.lineCount() == 0) {
            writeEmptySongMeasure(song, pw);
        } else {
            writeLineDrivenMeasures(song, pw);
        }

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.PART);

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.SCORE_PARTWISE);
    }

    /**
     * The clock- and date-derived header strings, resolved once per write so the
     * {@code <miscellaneous>} block and the {@code <credit>} list agree on a
     * single value for each.
     *
     * @param compositionDate    the composition date as a reduced-precision ISO
     *                           string, or {@code ""} when the song has none
     * @param distinctLyricsDate the lyrics date, or {@code ""} when absent OR
     *                           equal to the composition date (redundant to emit
     *                           a second time)
     * @param rights             the {@code <rights>}/rights-credit copyright line
     * @param encodingDate       the {@code <encoding-date>} in ISO local-date form
     */
    private record HeaderText(String compositionDate, String distinctLyricsDate, String rights, String encodingDate) {

        static HeaderText of(Song song, Clock clock) {
            var compositionDate = DateUtils.toIsoDate(song.getYear(), song.getMonth(), song.getDay());
            var lyricsDate = DateUtils.toIsoDate(song.getWordsYear(), song.getWordsMonth(), song.getWordsDay());

            // A lyrics date equal to the composition date is redundant — drop it
            // here so neither the <miscellaneous> block nor the credit list emits
            // a duplicate value.
            var distinctLyricsDate = lyricsDate.equals(compositionDate) ? "" : lyricsDate;

            var currentDate = LocalDate.now(clock);
            var rights = String.format(MusicXmlTags.COPYRIGHT, currentDate.getYear());
            var encodingDate = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

            return new HeaderText(compositionDate, distinctLyricsDate, rights, encodingDate);
        }
    }

    /**
     * Writes {@code <movement-number>} (omitted when {@code getNumber()} is
     * blank) followed by {@code <movement-title>}, in schema order.
     */
    private static void writeMovementInfo(Song song, PrintWriter pw) {
        if (!song.getNumber().isEmpty()) {
            XML.writeValue(pw, MusicXmlTags.MOVEMENT_NUMBER, song.getNumber());
        }

        XML.writeValue(pw, MusicXmlTags.MOVEMENT_TITLE, song.getTitle());
    }

    /**
     * Writes {@code <identification>}: composer/lyricist/arranger
     * {@code <creator>}s, the write-forward {@code <rights>} and
     * {@code <encoding>} blocks (dated from {@code headerText} so writer-output
     * tests can pin a fixed date), and the residual {@code <miscellaneous>}
     * block.
     */
    private static void writeIdentification(Song song, DocumentFontsHolder fonts, HeaderText headerText, PrintWriter pw) {
        XML.writeBeginTag(pw, MusicXmlTags.IDENTIFICATION);
        XML.indent();

        XML.writeValue(
            pw,
            MusicXmlTags.CREATOR,
            song.getComposer(),
            MusicXmlTags.ATTR_TYPE, MusicXmlTags.CREATOR_COMPOSER
        );
        XML.writeValue(
            pw,
            MusicXmlTags.CREATOR,
            song.getLyricist(),
            MusicXmlTags.ATTR_TYPE, MusicXmlTags.CREATOR_LYRICIST
        );

        if (song.isArrangement()) {
            XML.writeValue(
                pw,
                MusicXmlTags.CREATOR,
                Song.SRI_CHINMOY,
                MusicXmlTags.ATTR_TYPE, MusicXmlTags.CREATOR_ARRANGER
            );
        }

        XML.writeValue(pw, MusicXmlTags.RIGHTS, headerText.rights());

        XML.writeBeginTag(pw, MusicXmlTags.ENCODING);
        XML.indent();
        XML.writeValue(pw, MusicXmlTags.SOFTWARE, Constants.PACKAGE_NAME + " " + Version.PUBLIC_VERSION);
        XML.writeValue(pw, MusicXmlTags.ENCODING_DATE, headerText.encodingDate());
        XML.writeEmptyTag(
            pw,
            MusicXmlTags.SUPPORTS,
            MusicXmlTags.ATTR_ELEMENT, MusicXmlTags.SUPPORTS_ACCIDENTAL,
            MusicXmlTags.ATTR_TYPE, MusicXmlTags.YES
        );
        XML.writeEmptyTag(
            pw,
            MusicXmlTags.SUPPORTS,
            MusicXmlTags.ATTR_ELEMENT, MusicXmlTags.SUPPORTS_BEAM,
            MusicXmlTags.ATTR_TYPE, MusicXmlTags.YES
        );
        XML.writeEmptyTag(
            pw,
            MusicXmlTags.SUPPORTS,
            MusicXmlTags.ATTR_ELEMENT, MusicXmlTags.SUPPORTS_STEM,
            MusicXmlTags.ATTR_TYPE, MusicXmlTags.YES
        );
        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.ENCODING);

        writeMiscellaneousFields(song, fonts, headerText, pw);

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.IDENTIFICATION);
    }

    /**
     * Writes the residual {@code <miscellaneous>} block (composition-date,
     * lyrics-date, composition-place, lyrics-source, unofficial-translation,
     * sub-attribution-font/-size, row-height-adjustment), omitting the whole
     * block when no field applies. Fields are collected into one insertion-ordered
     * name→value map so they emit in order in a single {@code <miscellaneous>}
     * block, with each name paired to its value at the point of insertion (no
     * index coupling between separate name/value lists).
     */
    private static void writeMiscellaneousFields(Song song, DocumentFontsHolder fonts, HeaderText headerText, PrintWriter pw) {
        var fields = new LinkedHashMap<String, String>();

        if (!headerText.compositionDate().isEmpty()) {
            fields.put(MusicXmlTags.MISC_COMPOSITION_DATE, headerText.compositionDate());
        }

        // The lyrics date is already dropped in HeaderText when equal to the
        // composition date, so a non-empty value here is genuinely distinct.
        if (!headerText.distinctLyricsDate().isEmpty()) {
            fields.put(MusicXmlTags.MISC_LYRICS_DATE, headerText.distinctLyricsDate());
        }

        if (!song.getPlace().isEmpty()) {
            fields.put(MusicXmlTags.MISC_COMPOSITION_PLACE, song.getPlace());
        }

        fields.put(MusicXmlTags.MISC_LYRICS_SOURCE, song.getLyricsSource().name());

        if (song.isUnofficialTranslation()) {
            fields.put(MusicXmlTags.MISC_UNOFFICIAL_TRANSLATION, "true");
        }

        // The sub-attribution font rides in the miscellaneous block (no
        // <defaults> element carries a sub-attribution role); it is always
        // emitted so the reader can recover it.
        var subAttributionFont = fonts.getFont(FontKey.SUB_ATTRIBUTION);
        fields.put(MusicXmlTags.MISC_SUB_ATTRIBUTION_FONT, subAttributionFont.getFamily());
        fields.put(MusicXmlTags.MISC_SUB_ATTRIBUTION_FONT_SIZE, String.valueOf(subAttributionFont.getSize()));

        // Row-height adjustment is a delta from the computed base; omit when 0.
        var rowHeightAdjustmentSs = song.getRowHeightAdjustmentSs();

        if (rowHeightAdjustmentSs != 0) {
            fields.put(MusicXmlTags.MISC_ROW_HEIGHT_ADJUSTMENT, String.valueOf(rowHeightAdjustmentSs));
        }

        if (fields.isEmpty()) {
            return;
        }

        XML.writeBeginTag(pw, MusicXmlTags.MISCELLANEOUS);
        XML.indent();

        for (var field : fields.entrySet()) {
            XML.writeValue(
                pw,
                MusicXmlTags.MISCELLANEOUS_FIELD,
                field.getValue(),
                MusicXmlTags.ATTR_NAME, field.getKey()
            );
        }

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.MISCELLANEOUS);
    }

    /**
     * Writes the {@code <defaults>} block: the fixed {@code <scaling>}, the
     * {@code <page-layout>} carrying the model line width as {@code <page-width>},
     * a zero {@code <staff-layout>}, and the document fonts. {@code <scaling>},
     * {@code <page-height>}, and {@code <music-font>} are write-forward (fixed)
     * and ignored on read; {@code <page-width>}, {@code <word-font>}, and
     * {@code <lyric-font>} round-trip (the sub-attribution font rides in the
     * {@code <miscellaneous>} block, as MusicXML has no sub-attribution role).
     */
    private static void writeDefaults(Song song, DocumentFontsHolder fonts, PrintWriter pw) {
        XML.writeBeginTag(pw, MusicXmlTags.DEFAULTS);
        XML.indent();

        // Fixed scaling: 7 mm per 40 tenths (one 4-space staff), the MusicXML
        // default. Write-forward — the reader ignores it.
        XML.writeBeginTag(pw, MusicXmlTags.SCALING);
        XML.indent();
        XML.writeValue(
            pw,
            MusicXmlTags.MILLIMETERS,
            String.format(Locale.ROOT, "%.0f", MusicXmlTags.SCALING_MILLIMETERS)
        );
        XML.writeValue(pw, MusicXmlTags.TENTHS, MusicXmlTags.SCALING_TENTHS);
        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.SCALING);

        // Page layout: page-height is a fixed write-forward value; page-width
        // carries the model line width — the one canonical page-layout field.
        XML.writeBeginTag(pw, MusicXmlTags.PAGE_LAYOUT);
        XML.indent();
        XML.writeValue(pw, MusicXmlTags.PAGE_HEIGHT, MusicXmlTags.PAGE_HEIGHT_TENTHS);
        XML.writeValue(pw, MusicXmlTags.PAGE_WIDTH, formatTenths(ssToTenths(song.getLineWidthSs())));
        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.PAGE_LAYOUT);

        XML.writeBeginTag(pw, MusicXmlTags.STAFF_LAYOUT);
        XML.indent();
        XML.writeValue(pw, MusicXmlTags.STAFF_DISTANCE, STAFF_DISTANCE_TENTHS);
        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.STAFF_LAYOUT);

        // Fixed music font (write-forward); the word/lyric fonts round-trip.
        XML.writeEmptyTag(
            pw,
            MusicXmlTags.MUSIC_FONT,
            MusicXmlTags.ATTR_FONT_FAMILY, MusicXmlTags.MUSIC_FONT_FAMILY,
            MusicXmlTags.ATTR_FONT_SIZE, MusicXmlTags.MUSIC_FONT_SIZE
        );
        writeDocumentFont(pw, MusicXmlTags.WORD_FONT, fonts.getFont(FontKey.ANNOTATION));
        writeDocumentFont(pw, MusicXmlTags.LYRIC_FONT, fonts.getFont(FontKey.LYRICS));
        XML.writeEmptyTag(
            pw,
            MusicXmlTags.LYRIC_LANGUAGE,
            MusicXmlTags.ATTR_XML_LANG, MusicXmlTags.LYRIC_LANGUAGE_DEFAULT
        );

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.DEFAULTS);
    }

    /**
     * Writes a self-closing document-font element ({@code <word-font>} /
     * {@code <lyric-font>}) carrying the role's {@code font-family} and
     * {@code font-size}. Weight/style are not emitted — the reader recovers only
     * family and size back into the {@code DocumentFonts} result.
     */
    private static void writeDocumentFont(PrintWriter pw, String tag, Font font) {
        XML.writeEmptyTag(
            pw,
            tag,
            MusicXmlTags.ATTR_FONT_FAMILY, font.getFamily(),
            MusicXmlTags.ATTR_FONT_SIZE, String.valueOf(font.getSize())
        );
    }

    // -------------------------------------------------------------------------
    // Credits
    //
    // Data-flow contract (see phase-7-document-header.md § Implementation
    // Approach → "Data-flow contract"): every credit emitted here is either
    // display-only (re-derived from the head on read — title, attribution
    // roles) or canonical (subtitle, the four score-below blocks — read back
    // into the model). The reader (Phase 8) must take the head/model value
    // and ignore a display-only credit's text, or a hand-edited credit would
    // corrupt the model on reload.
    // -------------------------------------------------------------------------

    /**
     * Writes the {@code <credit>} elements: title, subtitle, each attribution
     * role, and the score-below text blocks, in that order. Every credit is
     * page 1 (single-page model), so the {@code page} attribute is never
     * written (the schema default is already {@code 1}).
     */
    private static void writeCredits(Song song, DocumentFontsHolder fonts, HeaderText headerText, PrintWriter pw) {
        writeCredit(pw, fonts, MusicXmlTags.CREDIT_TITLE, FontKey.TITLE, song.getNumberedTitle(), MusicXmlTags.JUSTIFY_CENTER, null, null);
        writeCredit(pw, fonts, MusicXmlTags.CREDIT_SUBTITLE, FontKey.SUBTITLE, song.getSubtitle(), null, null, null);

        var attributionRelativeYSs = song.getAttributionElement().getUserYOffsetSs();

        writeAttributionCredit(pw, fonts, MusicXmlTags.CREDIT_COMPOSER, song.getComposer(), attributionRelativeYSs);
        writeAttributionCredit(pw, fonts, MusicXmlTags.CREDIT_LYRICIST, song.getLyricist(), attributionRelativeYSs);

        if (song.isArrangement()) {
            writeAttributionCredit(pw, fonts, MusicXmlTags.CREDIT_ARRANGER, Song.SRI_CHINMOY, attributionRelativeYSs);
        }

        writeAttributionCredit(pw, fonts, MusicXmlTags.CREDIT_COMPOSITION_DATE, headerText.compositionDate(), attributionRelativeYSs);

        // The lyrics date is emitted as its own credit only when distinct from the
        // composition date; HeaderText already blanks it when they are equal, so a
        // non-empty value here is genuinely distinct.
        if (!headerText.distinctLyricsDate().isEmpty()) {
            writeAttributionCredit(pw, fonts, MusicXmlTags.CREDIT_LYRICS_DATE, headerText.distinctLyricsDate(), attributionRelativeYSs);
        }

        writeAttributionCredit(pw, fonts, MusicXmlTags.CREDIT_RIGHTS, headerText.rights(), attributionRelativeYSs);
        writeAttributionCredit(pw, fonts, MusicXmlTags.CREDIT_PLACE, song.getPlace(), attributionRelativeYSs);

        writeCredit(pw, fonts, MusicXmlTags.CREDIT_UNDERLYRICS, FontKey.LYRICS, song.getUnderLyrics(), null, null, null);
        writeCredit(pw, fonts, MusicXmlTags.CREDIT_BANGLA_LYRICS, FontKey.BANGLA, song.getBanglaLyrics(), null, null, MusicXmlTags.CREDIT_LANGUAGE_BANGLA);
        writeCredit(pw, fonts, MusicXmlTags.CREDIT_TRANSLATION, FontKey.LYRICS, song.getTranslatedLyrics(), null, null, null);
        writeCredit(pw, fonts, MusicXmlTags.CREDIT_FOOTNOTES, FontKey.FOOTNOTE, song.getFootnotes(), null, null, null);
    }

    /**
     * Writes one attribution-role {@code <credit>} (composer, lyricist, arranger,
     * the two dates, rights, place): an {@code ATTRIBUTION}-font credit carrying
     * the shared attribution {@code relative-y} and no {@code justify}/
     * {@code xml:lang}. Convenience over {@link #writeCredit} for the seven
     * attribution calls, which are identical but for their type and text.
     */
    private static void writeAttributionCredit(PrintWriter pw, DocumentFontsHolder fonts, String creditType, String text, double relativeYSs) {
        writeCredit(pw, fonts, creditType, FontKey.ATTRIBUTION, text, null, relativeYSs, null);
    }

    /**
     * Writes one {@code <credit>} — {@code <credit-type>} then
     * {@code <credit-words>} — when {@code text} is non-blank; emits nothing
     * otherwise. {@code <credit-words>} always carries font-family/font-size/
     * font-weight/font-style from {@code fonts.getFont(fontKey)} (weight/style
     * via {@link java.awt.Font#isBold()}/{@link java.awt.Font#isItalic()});
     * {@code justify}/{@code xmlLang} are written only when non-null, and
     * {@code relativeYSs} (a staff-space offset, converted here via the shared
     * {@link #ssToTenths} / {@link #formatTenths}) only when non-null — the
     * attribution roles always pass a value (even {@code 0}) so the reader can
     * recover the offset; title/subtitle/score-below credits pass {@code null}
     * so no {@code relative-y} is written. {@code default-x}/{@code default-y}
     * are write-forward, external-renderer-only fields (see the sub-plan's
     * "Explicitly OUT of scope" list) and are never emitted here.
     */
    private static void writeCredit(
            PrintWriter pw,
            DocumentFontsHolder fonts,
            String creditType,
            FontKey fontKey,
            String text,
            @Nullable String justify,
            @Nullable Double relativeYSs,
            @Nullable String xmlLang) {
        if (text.isBlank()) {
            return;
        }

        XML.writeBeginTag(pw, MusicXmlTags.CREDIT);
        XML.indent();

        XML.writeValue(pw, MusicXmlTags.CREDIT_TYPE, creditType);

        var font = fonts.getFont(fontKey);
        var creditWordsAttrs = new ArrayList<String>();
        creditWordsAttrs.add(MusicXmlTags.ATTR_FONT_FAMILY);
        creditWordsAttrs.add(font.getFamily());
        creditWordsAttrs.add(MusicXmlTags.ATTR_FONT_SIZE);
        creditWordsAttrs.add(String.valueOf(font.getSize()));
        creditWordsAttrs.add(MusicXmlTags.ATTR_FONT_WEIGHT);
        creditWordsAttrs.add(font.isBold() ? MusicXmlTags.WEIGHT_BOLD : MusicXmlTags.WEIGHT_NORMAL);
        creditWordsAttrs.add(MusicXmlTags.ATTR_FONT_STYLE);
        creditWordsAttrs.add(font.isItalic() ? MusicXmlTags.STYLE_ITALIC : MusicXmlTags.STYLE_NORMAL);

        if (justify != null) {
            creditWordsAttrs.add(MusicXmlTags.ATTR_JUSTIFY);
            creditWordsAttrs.add(justify);
        }

        if (xmlLang != null) {
            creditWordsAttrs.add(MusicXmlTags.ATTR_XML_LANG);
            creditWordsAttrs.add(xmlLang);
        }

        if (relativeYSs != null) {
            creditWordsAttrs.add(MusicXmlTags.ATTR_RELATIVE_Y);
            creditWordsAttrs.add(formatTenths(ssToTenths(relativeYSs)));
        }

        XML.writeValue(pw, MusicXmlTags.CREDIT_WORDS, text, creditWordsAttrs.toArray(new String[0]));

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.CREDIT);
    }

    /**
     * Empty-song fallback: a single attributes-only measure with no
     * {@code <print>} and no {@code <barline>}, matching Phase 1 behavior.
     */
    private static void writeEmptySongMeasure(Song song, PrintWriter pw) {
        openMeasure(pw, FIRST_MEASURE_NUMBER);
        writeAttributes(song, pw);
        closeMeasure(pw);
    }

    /**
     * Line-driven emission: each {@link Line} contributes one or more
     * {@code <measure>}s, segmented at every barline/repeat element and at
     * every line break.
     */
    private static void writeLineDrivenMeasures(Song song, PrintWriter pw) {
        int measureNumber = 0;

        // The key signature currently in effect. Measure 1 emits the song default
        // (see writeAttributes); each later line whose effective key differs from
        // this running value emits a key-only <attributes> and advances it. A
        // MusicXML key persists until restated, so lines that keep the running key
        // emit nothing — the reader carries it forward.
        var runningFifths = KeySignatureMapping.toFifths(song.getDefaultKeyType(), song.getDefaultKeyAccidentalCount());

        // The first element of the first line anchors the song base tempo (mirrors
        // Line.attachInitialTempoIfNeeded): its emitted tempo is its own
        // TempoChangeAttachment if present, else song.getTempo().
        var firstSongElement = firstElementOfSong(song);

        for (Line line : song.getLines()) {
            // Glissandos are intra-line — they cannot span a system break.
            // Reset the pending-stop state at the start of each line so a
            // dangling glissando from a malformed song does not bleed across.
            StaffElement.@Nullable Glissando pendingGlissando = null;

            // Open the line-starting measure. Every such measure carries a system-
            // break marker so the reader has one uniform rule:
            // new-system="yes" always starts a new line.
            measureNumber++;
            openMeasure(pw, measureNumber);
            writePrintNewSystem(pw);

            if (measureNumber == FIRST_MEASURE_NUMBER) {
                writeAttributes(song, pw);
            } else {
                var lineFifths = effectiveKeyFifths(song, line);

                if (lineFifths != runningFifths) {
                    writeKeyOnlyAttributes(pw, lineFifths);
                    runningFifths = lineFifths;
                }
            }

            // measureOpen tracks whether the current measure tag is still open.
            // A measure is open after we write its opening tag and closed after
            // we write its closing tag.
            boolean measureOpen = true;

            var elements = line.getElements();
            var lastElement = elements.isEmpty() ? null : elements.getLast();

            // Build the per-element span index once per line. Anchor/end indices
            // for all six span types are resolved here so the element loop can do
            // O(1) lookups instead of calling getAnchorElementIndex() (ArrayList.indexOf,
            // O(n)) per element per span.
            var spanIndex = buildSpanIndex(line);

            for (int i = 0; i < elements.size(); i++) {
                var element = elements.get(i);
                var type = element.getType();
                var markers = spanIndex[i];

                if (type == ElementType.REPEAT_LEFT) {
                    // REPEAT_LEFT opens a new measure (the forward-repeat barline
                    // is a left barline, not a right barline). Close the current
                    // measure with an invisible right barline to preserve the
                    // line boundary, then open the new measure. An ending anchored
                    // (or ended) on the REPEAT_LEFT rides on the forward-left barline.
                    writeInvisibleRightBarline(pw);
                    measureNumber = openForwardRepeatMeasure(pw, measureNumber, markers.endingLeftBarlineMarkers());

                } else if (type == ElementType.REPEAT_LEFT_RIGHT) {
                    // REPEAT_LEFT_RIGHT straddles a measure boundary:
                    // - a backward-repeat right barline closes the current measure,
                    // - a forward-repeat left barline opens the next one.
                    // The reader reconstructs the REPEAT_LEFT_RIGHT from this pair.
                    // For an ending split here, <ending number="1" type="stop">
                    // rides on the backward-right barline and <ending number="2"
                    // type="start"> on the forward-left barline.
                    writeBackwardRepeatRightBarline(pw, markers.endingRightBarlineMarkers());
                    measureNumber = openForwardRepeatMeasure(pw, measureNumber, markers.endingLeftBarlineMarkers());

                } else if (type.isBarLine() || type.isRepeat()) {
                    // All other barline/repeat types close the current measure
                    // with a right barline. If this is not the last element on the
                    // line, a new measure is opened immediately for subsequent
                    // elements. If it is the last element, the outer end-of-line
                    // check will not emit a spurious empty measure.
                    var entry = BarlineStyleMapping.forElementType(type);
                    // entry is non-null here: REPEAT_LEFT and REPEAT_LEFT_RIGHT
                    // are handled in the branches above; all remaining barline/
                    // repeat types have forward-map entries.
                    if (entry == null) {
                        continue;
                    }

                    writeBarline(pw, entry, markers.endingRightBarlineMarkers());
                    closeMeasure(pw);
                    measureOpen = false;

                    // Peek ahead: if this barline is not the last element on the
                    // line, there are more elements to place, so open the next
                    // measure now. If it is the last element, measureOpen stays
                    // false and the end-of-line block below is skipped — no
                    // spurious empty measure is emitted.
                    if (element != lastElement) {
                        measureNumber++;
                        openMeasure(pw, measureNumber);
                        measureOpen = true;

                        // A REPEAT_RIGHT split (not its own dedicated branch) closes
                        // volta 1 on the right barline above; volta 2 begins in this
                        // new measure, so its <ending number="2" type="start"> rides
                        // on an invisible (style-none) left barline — the first child
                        // of the measure per the barline-location schema rule.
                        var leftBarlineMarkers = markers.endingLeftBarlineMarkers();

                        if (!leftBarlineMarkers.isEmpty()) {
                            writeInvisibleLeftBarline(pw, leftBarlineMarkers);
                        }
                    }

                } else if (type.isBreathMark()) {
                    // Already serialized inside the preceding note's <notations>.
                    // Skip here so the breath mark is not emitted a second time.

                } else {
                    // Note, rest, grace, or other element. Emit a <note> only when
                    // a <type> token exists; other types (SLIDE standalone, etc.)
                    // are silently skipped via the null-token guard.
                    var typeToken = NoteTypeMapping.typeToken(type);

                    if (typeToken != null) {
                        // A tempo <direction> precedes the note it marks. The first
                        // element of the first line carries the song base tempo; any
                        // element with its own TempoChangeAttachment carries a
                        // per-note tempo.
                        var tempo = tempoForElement(song, firstSongElement, element);

                        if (tempo != null) {
                            writeTempoDirection(pw, tempo);
                        }

                        // A metric-modulation <direction> also precedes the note it
                        // marks (the note carrying the BeatChangeAttachment), so the
                        // reader binds it to the next note with the same rule.
                        var beatChangeAttachment = element.findAttachment(BeatChangeAttachment.class);

                        if (beatChangeAttachment != null) {
                            writeMetricModulationDirection(pw, beatChangeAttachment.getBeatChange());
                        }

                        // Hairpin wedges bind to the next <note>: both the start
                        // wedge (on the anchor) and the stop wedge (on the end) are
                        // emitted as <direction> siblings immediately before their
                        // bound note, giving the reader one uniform look-ahead rule.
                        writeHairpinWedges(pw, markers);

                        // An annotation <direction placement="above|below"> also
                        // binds to the next note, so it too is emitted immediately
                        // before its <note> (after the tempo/wedge directions).
                        var annotationAttachment = element.findAttachment(AnnotationAttachment.class);

                        if (annotationAttachment != null) {
                            writeAnnotationDirection(pw, annotationAttachment.getAnnotation());
                        }

                        var nextElement = (i + 1 < elements.size()) ? elements.get(i + 1) : null;
                        var nextIsBreathMark = nextElement != null && nextElement.getType().isBreathMark();
                        var ctx = new NoteWriteContext(
                            element, typeToken, nextIsBreathMark,
                            pendingGlissando, markers.noteMarkers()
                        );
                        writeNote(pw, ctx);
                        // getGlissando() is null unless this note starts a glissando.
                        pendingGlissando = element.getGlissando();
                    }
                }
            }

            // If the current measure is still open at end of line, the line break
            // ends it. An invisible right barline marks the break so the reader can
            // reconstruct the line boundary without inserting a barline StaffElement.
            // If measureOpen is false, the last element was a real barline that
            // already closed its measure — no spurious empty measure is emitted.
            if (measureOpen) {
                writeInvisibleRightBarline(pw);
                closeMeasure(pw);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Note emission
    //
    // Strict <note> child-order pipeline (MusicXML 4.0, musicxml.xsd §5174–5223):
    //
    //   <note [default-x="…"] [relative-x="…"]>
    //     (<grace slash="no"/>)?             — grace notes only, no steal-time-following
    //     (<rest/>  |  <pitch>…</pitch>)    — rest has no pitch; grace has pitch
    //     (<duration>…</duration>)?         — omitted for grace (zero playback time)
    //     (<tie type="stop"/>)?             — sound tie stop; chained before start (not rests)
    //     (<tie type="start"/>)?            — sound tie start (not rests)
    //     <type>…</type>
    //     (<dot/>)*                          — dotCount times; grace: always 0
    //     (<accidental …>…</accidental>)?   — not for rests
    //     (<time-modification>              — every note in a tuplet (incl. rests)
    //       <actual-notes>grade</actual-notes>
    //       <normal-notes>largest-power-of-two-below-grade</normal-notes>
    //     </time-modification>)?
    //     (<stem [default-y="…"]>…</stem>)? — grace: always "up"; otherwise only when !auto
    //     (<beam number="N">…</beam>)*      — per beam level (Phase 2b)
    //     (<notations>                       — emitted only when non-empty
    //       (<tied type="stop"/>)?          — notation tie stop (first; chained before start)
    //       (<tied type="start"/>)?         — notation tie start
    //       (<slide …/>)*                   — stop slide before start slide
    //       (<tuplet type="start" number="1" [relative-y="…"]/>)?  — tuplet bracket open
    //       (<tuplet type="stop"  number="1"                  />)?  — tuplet bracket close
    //       (<ornaments>                    — omitted for rests; emitted when trill is active
    //         (<trill-mark/>)?              — anchor note (and end, for single-note trill)
    //         (<wavy-line type="start" number="1" [relative-y="…"]/>)?
    //         (<wavy-line type="stop"  number="1"                  />)?
    //       </ornaments>)?
    //       (<articulations>
    //         (<accent/>|<staccato/>|<falloff/>|<breath-mark/>)*
    //       </articulations>)?
    //       (<dynamics><…/></dynamics>)?
    //       (<fermata/>)?
    //     </notations>)?
    //   </note>
    // -------------------------------------------------------------------------

    private static void writeNote(PrintWriter pw, NoteWriteContext ctx) {
        var note = ctx.note();
        var typeToken = ctx.typeToken();
        var type = note.getType();
        var isGrace = type.isGraceNote();
        var isRest = type.isRest();
        var spanMarkers = ctx.spanMarkers();

        // Compute position in tenths.
        // default-x: the base layout position (getXSs() stores the layout-assigned
        //   position; for notes this is set per the new layout system).
        // relative-x: the user-set horizontal offset, emitted only when non-zero
        //   (mirrors the legacy writeElement guard).
        var baseXTenths = ssToTenths(note.getXSs());
        var xOffsetPx = note.getXOffsetPx();

        // Open <note> tag with optional position attributes. relative-x is emitted
        // only when the note carries a user offset (mirrors the legacy guard), so
        // its tenths conversion is computed only in that branch.
        if (xOffsetPx != 0) {
            var relativeXTenths = ssToTenths(ScaleContext.pxToSs(xOffsetPx));
            XML.writeBeginTag(pw, MusicXmlTags.NOTE,
                MusicXmlTags.ATTR_DEFAULT_X, formatTenths(baseXTenths),
                MusicXmlTags.ATTR_RELATIVE_X, formatTenths(relativeXTenths)
            );
        } else {
            XML.writeBeginTag(pw, MusicXmlTags.NOTE,
                MusicXmlTags.ATTR_DEFAULT_X, formatTenths(baseXTenths)
            );
        }

        XML.indent();

        // 1. <grace slash="no"/> — grace notes only.
        //    No steal-time-following: SongScribe gives grace notes zero playback
        //    duration and never shortens the host, so emitting a steal would
        //    misrepresent the song.
        if (isGrace) {
            XML.writeEmptyTag(pw, MusicXmlTags.GRACE, MusicXmlTags.ATTR_SLASH, MusicXmlTags.NO);
        }

        // 2. <rest/> | <pitch> — rest has no pitch; pitched and grace notes do.
        if (isRest) {
            XML.writeEmptyTag(pw, MusicXmlTags.REST);
        } else {
            writePitch(pw, note);
        }

        // 3. <duration> — omitted for grace notes (zero playback time).
        if (NoteTypeMapping.hasDuration(type)) {
            var ticks = NoteTypeMapping.ticks(type, note.getDotCount());
            XML.writeValue(pw, MusicXmlTags.DURATION, Integer.toString(ticks));
        }

        // 3a. <tie type="stop"/>? + <tie type="start"/>? — sound ties, note-level,
        //     after <duration> and before <type>.  Rests cannot be tied.
        //     Interior notes of a chain emit stop before start to form a closed loop.
        if (!isRest) {
            if (spanMarkers.tieStopsHere()) {
                XML.writeEmptyTag(pw, MusicXmlTags.TIE, MusicXmlTags.ATTR_TYPE, MusicXmlTags.TYPE_STOP);
            }

            if (spanMarkers.tieStartsHere()) {
                XML.writeEmptyTag(pw, MusicXmlTags.TIE, MusicXmlTags.ATTR_TYPE, MusicXmlTags.TYPE_START);
            }
        }

        // 4. <type>
        XML.writeValue(pw, MusicXmlTags.NOTE_TYPE, typeToken);

        // 5. <dot/>×n — grace notes never carry dots.
        var dotCount = isGrace ? 0 : note.getDotCount();

        for (var i = 0; i < dotCount; i++) {
            XML.writeEmptyTag(pw, MusicXmlTags.DOT);
        }

        // 6. <accidental> — not for rests.
        if (!isRest) {
            writeAccidental(pw, note);
        }

        // 6a. <time-modification> — emitted on every note in a tuplet span,
        //     including rests.  After <accidental>, before <stem> per schema.
        var tupletForTimeMod = spanMarkers.tuplet();

        if (tupletForTimeMod != null) {
            writeTimeModification(pw, tupletForTimeMod.getGrade());
        }

        // 7. <stem>
        writeStem(pw, note, isGrace);

        // 8. <beam number="N"> — one element per active beam level, note-level,
        //    after <stem> and before <notations> per MusicXML 4.0 schema.
        //    Pre-computed values are null for non-beamed notes and for levels
        //    where this note has no beam activity.
        writeBeamValues(pw, spanMarkers.beamLevelValues());

        // 9. <notations>
        writeNotations(pw, ctx);

        // 10. <lyric>×n — one per verse.
        writeLyrics(pw, note);

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.NOTE);
    }

    // -------------------------------------------------------------------------
    // <pitch>: step / alter (when non-zero) / octave via the Phase 2 helper.
    // -------------------------------------------------------------------------

    private static void writePitch(PrintWriter pw, StaffElement note) {
        var pitch = PitchSpelling.spell(note.getStaffPosition());
        var alterSemitones = PitchSpelling.soundingAlterFor(note);

        XML.writeBeginTag(pw, MusicXmlTags.PITCH);
        XML.indent();

        XML.writeValue(pw, MusicXmlTags.STEP, String.valueOf(pitch.step()));

        if (alterSemitones != 0) {
            XML.writeValue(pw, MusicXmlTags.ALTER, Integer.toString(alterSemitones));
        }

        XML.writeValue(pw, MusicXmlTags.OCTAVE, Integer.toString(pitch.octave()));

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.PITCH);
    }

    // -------------------------------------------------------------------------
    // <accidental>: emitted only when the note has an explicit accidental glyph.
    // cautionary="yes" parentheses="yes" are driven by isAccidentalInParentheses().
    // DOUBLE_NATURAL has no MusicXML mapping and is silently skipped.
    // -------------------------------------------------------------------------

    private static void writeAccidental(PrintWriter pw, StaffElement note) {
        var accidental = note.getAccidental();

        if (accidental == null) {
            return;
        }

        var entry = AccidentalMapping.forAccidental(accidental);

        if (entry == null) {
            // DOUBLE_NATURAL has no MusicXML representation — skip silently.
            return;
        }

        // A parenthesized accidental is a cautionary: cautionary="yes" parentheses="yes".
        if (note.isAccidentalInParentheses()) {
            XML.writeValue(pw, MusicXmlTags.ACCIDENTAL, entry.token(),
                MusicXmlTags.ATTR_CAUTIONARY, MusicXmlTags.YES,
                MusicXmlTags.ATTR_PARENTHESES, MusicXmlTags.YES
            );
        } else {
            XML.writeValue(pw, MusicXmlTags.ACCIDENTAL, entry.token());
        }
    }

    // -------------------------------------------------------------------------
    // <stem>: grace notes always emit "up" with a computed stem-tip default-y;
    // non-grace notes emit the direction only when stem direction is manual
    // (isStemDirectionAuto() == false).
    // -------------------------------------------------------------------------

    private static void writeStem(PrintWriter pw, StaffElement note, boolean isGrace) {
        if (isGrace) {
            // Grace-note stems always go up. Emit a computed stem-tip position so
            // external renderers can draw the stem without re-running layout.
            // Stem-tip tenths = note-head tenths above middle line + extension.
            //   noteHeadTenths = staffPosition × -TENTHS_PER_STAFF_POSITION
            //   (negative because staffPosition increases downward but MusicXML Y
            //    is positive upward, and origin B4 = staffPosition 0 = middle line)
            var stemTipTenths = note.getStaffPosition() * -TENTHS_PER_STAFF_POSITION + GRACE_STEM_EXTENSION_TENTHS;
            XML.writeValue(pw, MusicXmlTags.STEM, MusicXmlTags.STEM_UP,
                MusicXmlTags.ATTR_DEFAULT_Y, Integer.toString(stemTipTenths)
            );

        } else if (!note.isStemDirectionAuto()) {
            var stemDir = note.isUpper() ? MusicXmlTags.STEM_UP : MusicXmlTags.STEM_DOWN;
            XML.writeValue(pw, MusicXmlTags.STEM, stemDir);
        }
    }

    // -------------------------------------------------------------------------
    // <beam>: emits one <beam number="N"> element per active beam level.
    // Values are pre-computed by computeNoteBeamValues in the span precompute.
    // -------------------------------------------------------------------------

    private static void writeBeamValues(PrintWriter pw, String[] beamLevelValues) {
        // An empty array means the note is not part of any beam group — nothing to emit.
        if (beamLevelValues.length == 0) {
            return;
        }

        for (var level = 0; level < beamLevelValues.length; level++) {
            var value = beamLevelValues[level];

            // Empty-string entries are the sentinel for "no beam at this level".
            if (!value.isEmpty()) {
                XML.writeValue(pw, MusicXmlTags.BEAM, value,
                    MusicXmlTags.ATTR_NUMBER, Integer.toString(level + 1));
            }
        }
    }

    // -------------------------------------------------------------------------
    // <notations>: emitted only when at least one child will be written.
    //
    // Emission order within <notations> (xs:choice — any order is valid per
    // schema; the writer uses this consistent convention):
    //   <tied …/>*              — notation ties (stop before start on interior notes)
    //   <slide …/>*             — stop slide before start slide
    //   <tuplet …/>?            — tuplet bracket start/stop
    //   <ornaments>?            — trill-mark + wavy-line (not for rests)
    //   <articulations>?        — accent, staccato, falloff, breath-mark
    //   <dynamics>?
    //   <fermata/>?             — last per the full schema ordering
    // -------------------------------------------------------------------------

    /**
     * The write-forward {@code <tied orientation>} value for {@code tie}: {@code "over"} when the
     * tie arcs above its notes ({@link Tie#isAbove()}), else {@code "under"}. A null tie (which the
     * emit guards make unreachable) defaults to {@code "under"}.
     */
    private static String tiedOrientation(@Nullable Tie tie) {
        return tie != null && tie.isAbove()
            ? MusicXmlTags.ORIENTATION_OVER
            : MusicXmlTags.ORIENTATION_UNDER;
    }

    private static void writeNotations(PrintWriter pw, NoteWriteContext ctx) {
        var note = ctx.note();
        var nextIsBreathMark = ctx.nextIsBreathMark();
        var pendingStopGlissando = ctx.pendingStopGlissando();
        var type = note.getType();
        var isGrace = type.isGraceNote();
        var isRest = type.isRest();
        var spanMarkers = ctx.spanMarkers();

        // Glissando slides are not applicable to grace notes.
        var startGlissando = isGrace ? null : note.getGlissando();
        var hasSlideStop = !isGrace && pendingStopGlissando != null;
        var hasSlideStart = startGlissando != null;

        var articulations = note.getArticulations();
        var hasFermata = note.findAttachment(FermataAttachment.class) != null;
        var dynamic = note.findAttachment(DynamicAttachment.class);

        var hasArticulationsBlock = !articulations.isEmpty() || note.hasFall() || nextIsBreathMark;

        // Tied (notation ties): emitted for the same cases as the sound <tie>.
        var hasTiedStop = !isRest && spanMarkers.tieStopsHere();
        var hasTiedStart = !isRest && spanMarkers.tieStartsHere();

        // Tuplet bracket: start on the anchor, stop on the end note.
        var isTupletAnchor = spanMarkers.isTupletAnchor();
        var isTupletEnd = spanMarkers.isTupletEnd();

        // Ornaments (trill): not emitted for rests.
        // isTrillAnchor → emit <trill-mark/> + <wavy-line type="start">
        // isTrillEnd    → emit <wavy-line type="stop">
        // For a single-note trill anchor == end, so both flags are set on one note.
        var isTrillAnchor = !isRest && spanMarkers.isTrillAnchor();
        var isTrillEnd = !isRest && spanMarkers.isTrillEnd();
        var hasOrnaments = isTrillAnchor || isTrillEnd;

        var hasNotations = hasSlideStop || hasSlideStart || hasFermata
            || hasArticulationsBlock || dynamic != null
            || hasTiedStop || hasTiedStart
            || isTupletAnchor || isTupletEnd
            || hasOrnaments;

        if (!hasNotations) {
            return;
        }

        XML.writeBeginTag(pw, MusicXmlTags.NOTATIONS);
        XML.indent();

        // <tied> — notation counterpart of the sound <tie>, emitted first.
        // Interior notes of a chain emit stop before start. Each carries a write-forward
        // orientation ("over"/"under") from Tie.isAbove(); the reader ignores it (round-trip
        // loss stays benign while direction is fully deterministic from stems).
        if (hasTiedStop) {
            XML.writeEmptyTag(pw, MusicXmlTags.TIED, MusicXmlTags.ATTR_TYPE, MusicXmlTags.TYPE_STOP,
                MusicXmlTags.ATTR_ORIENTATION, tiedOrientation(spanMarkers.tieStop()));
        }

        if (hasTiedStart) {
            XML.writeEmptyTag(pw, MusicXmlTags.TIED, MusicXmlTags.ATTR_TYPE, MusicXmlTags.TYPE_START,
                MusicXmlTags.ATTR_ORIENTATION, tiedOrientation(spanMarkers.tieStart()));
        }

        // <slide type="stop"> on the destination note of a glissando.
        // Carries the computed end-point coordinates for external-renderer
        // fidelity (write-forward only; the reader ignores them).
        if (hasSlideStop) {
            writeSlide(pw, MusicXmlTags.SLIDE_STOP, pendingStopGlissando);
        }

        // <slide type="start"> on the source note of a glissando.
        // Carries the computed start-point coordinates (write-forward only).
        if (hasSlideStart) {
            writeSlide(pw, MusicXmlTags.SLIDE_START, startGlissando);
        }

        // <tuplet> bracket: start on the anchor, stop on the end note.
        // relative-y carries verticalPositionSs, only when non-zero.
        if (isTupletAnchor) {
            var tuplet = spanMarkers.tuplet();
            var verticalPositionSs = tuplet != null ? tuplet.getVerticalPositionSs() : 0;
            writeNumberedMarker(pw, MusicXmlTags.TUPLET, MusicXmlTags.TYPE_START, verticalPositionSs);
        }

        if (isTupletEnd) {
            writeNumberedMarker(pw, MusicXmlTags.TUPLET, MusicXmlTags.TYPE_STOP, 0);
        }

        // <ornaments>: trill-mark + wavy-line start/stop.
        // Only emitted when this note participates in a trill span.
        if (hasOrnaments) {
            XML.writeBeginTag(pw, MusicXmlTags.ORNAMENTS);
            XML.indent();

            // <trill-mark/> appears on the anchor (and on the end note of a
            // single-note trill, where anchor == end so isTrillAnchor is also true).
            if (isTrillAnchor) {
                XML.writeEmptyTag(pw, MusicXmlTags.TRILL_MARK);
            }

            // <wavy-line type="start"> on the anchor, carrying yPositionSs as
            // relative-y, only when non-zero.
            if (isTrillAnchor) {
                var trill = spanMarkers.trill();
                var yPositionSs = trill != null ? trill.getYPositionSs() : 0;
                writeNumberedMarker(pw, MusicXmlTags.WAVY_LINE, MusicXmlTags.TYPE_START, yPositionSs);
            }

            // <wavy-line type="stop"> on the end note.
            if (isTrillEnd) {
                writeNumberedMarker(pw, MusicXmlTags.WAVY_LINE, MusicXmlTags.TYPE_STOP, 0);
            }

            XML.dedent();
            XML.writeEndTag(pw, MusicXmlTags.ORNAMENTS);
        }

        // <articulations>: accent, staccato, fall (falloff), breath-mark.
        if (hasArticulationsBlock) {
            XML.writeBeginTag(pw, MusicXmlTags.ARTICULATIONS);
            XML.indent();

            for (var articulation : articulations) {
                switch (articulation.getType()) {
                    case ACCENT -> XML.writeEmptyTag(pw, MusicXmlTags.ACCENT);
                    case STACCATO -> XML.writeEmptyTag(pw, MusicXmlTags.STACCATO);
                }
            }

            if (note.hasFall()) {
                XML.writeEmptyTag(pw, MusicXmlTags.FALLOFF);
            }

            if (nextIsBreathMark) {
                XML.writeEmptyTag(pw, MusicXmlTags.BREATH_MARK);
            }

            XML.dedent();
            XML.writeEndTag(pw, MusicXmlTags.ARTICULATIONS);
        }

        // <dynamics>
        if (dynamic != null) {
            XML.writeBeginTag(pw, MusicXmlTags.DYNAMICS);
            XML.indent();
            XML.writeEmptyTag(pw, dynamic.getType().getSymbol());
            XML.dedent();
            XML.writeEndTag(pw, MusicXmlTags.DYNAMICS);
        }

        // <fermata/> — last per the schema ordering convention.
        if (hasFermata) {
            XML.writeEmptyTag(pw, MusicXmlTags.FERMATA);
        }

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.NOTATIONS);
    }

    // -------------------------------------------------------------------------
    // <lyric> emission: one child per verse, mirroring the legacy
    // StaffElementIO write loop. A carrier lyric (extend STOP/CONTINUE) has no
    // text of its own -- it only marks a melisma boundary -- so it emits only
    // <extend type="stop|continue"/>. Every other lyric emits <syllabic> and
    // <text> (with the compound-word marker appended when applicable), then an
    // <extend type="start"/> only when this syllable opens a melisma.
    // -------------------------------------------------------------------------

    private static void writeLyrics(PrintWriter pw, StaffElement note) {
        var lyrics = note.getLyrics();

        if (lyrics.isEmpty()) {
            return;
        }

        for (var lyric : lyrics) {
            var extend = lyric.extend();

            XML.writeBeginTag(pw, MusicXmlTags.LYRIC,
                MusicXmlTags.ATTR_NUMBER, Integer.toString(lyric.verse()));
            XML.indent();

            if (lyric.isCarrier()) {
                XML.writeEmptyTag(pw, MusicXmlTags.EXTEND,
                    MusicXmlTags.ATTR_TYPE, SyllabicMapping.forExtend(extend));
            } else {
                XML.writeValue(pw, MusicXmlTags.SYLLABIC, SyllabicMapping.forSyllabic(lyric.syllabic()));

                var text = lyric.text();
                var lyricText = lyric.compound() ? text + Lyric.COMPOUND_WORD_MARKER : text;
                XML.writeValue(pw, MusicXmlTags.LYRIC_TEXT, lyricText);

                if (extend == Lyric.Extend.START) {
                    XML.writeEmptyTag(pw, MusicXmlTags.EXTEND,
                        MusicXmlTags.ATTR_TYPE, SyllabicMapping.forExtend(extend));
                }
            }

            XML.dedent();
            XML.writeEndTag(pw, MusicXmlTags.LYRIC);
        }
    }

    // -------------------------------------------------------------------------
    // <slide> helper: emits a start or stop slide with optional endpoint
    // coordinates from the glissando's cached render geometry.
    //
    // The glissando lives on the *source* note via StaffElement.slide.
    // For the start slide: glissando.cachedStartX/Y is the endpoint.
    // For the stop slide: the end is cachedStartX + cachedLength × cos/sin.
    // Coordinates are in staff-space units; ssToTenths converts them to
    // MusicXML tenths.  These are write-forward only — the reader ignores them
    // and re-derives geometry from layout.
    // -------------------------------------------------------------------------

    private static void writeSlide(
            PrintWriter pw,
            String slideType,
            StaffElement.@Nullable Glissando glissando
    ) {
        if (glissando == null || !glissando.hasCachedGeometry) {
            // No cached geometry available — emit a minimal slide without coordinates.
            XML.writeEmptyTag(pw, MusicXmlTags.SLIDE,
                MusicXmlTags.ATTR_TYPE, slideType,
                MusicXmlTags.ATTR_LINE_TYPE, MusicXmlTags.LINE_SOLID
            );
            return;
        }

        double xSs;
        double ySs;

        // The stop slide's endpoint is the *end* of the glissando line; the start
        // slide's is the line's start.
        var isStop = MusicXmlTags.SLIDE_STOP.equals(slideType);

        if (isStop) {
            // The stop slide's default-x/y is the *end* of the glissando line,
            // computed from the start-note's cached geometry.
            xSs = glissando.cachedStartX + glissando.cachedLength * glissando.cachedCos;
            ySs = glissando.cachedStartY + glissando.cachedLength * glissando.cachedSin;
        } else {
            xSs = glissando.cachedStartX;
            ySs = glissando.cachedStartY;
        }

        XML.writeEmptyTag(pw, MusicXmlTags.SLIDE,
            MusicXmlTags.ATTR_TYPE, slideType,
            MusicXmlTags.ATTR_LINE_TYPE, MusicXmlTags.LINE_SOLID,
            MusicXmlTags.ATTR_DEFAULT_X, formatTenths(ssToTenths(xSs)),
            MusicXmlTags.ATTR_DEFAULT_Y, formatTenths(ssToTenths(ySs))
        );
    }

    // -------------------------------------------------------------------------
    // Hairpin wedges (measure-level <direction>)
    //
    // Both wedges of a hairpin are emitted immediately before their bound note:
    // the start wedge before the anchor note, the stop wedge before the end note.
    // This both-before-the-note placement lets the reader use one uniform rule —
    // each wedge binds to the next <note> after it.  number is always "1": the
    // app never produces overlapping wedges, so only one is ever open.
    // -------------------------------------------------------------------------

    /**
     * Emits the start and stop wedges bound to the note at this element index.
     * Stop wedges (closing an open hairpin) precede start wedges so a hairpin's
     * stop and the next hairpin's start on the same note keep a natural order.
     */
    private static void writeHairpinWedges(PrintWriter pw, IndexSpanMarkers markers) {
        for (var hairpin : markers.hairpinsEndingHere()) {
            writeStopWedge(pw, hairpin);
        }

        for (var hairpin : markers.hairpinsStartingHere()) {
            writeStartWedge(pw, hairpin);
        }
    }

    /**
     * Emits the start wedge for {@code hairpin}: {@code <wedge type="crescendo|
     * diminuendo" number="1">}, carrying {@code x1ShiftSs} as {@code relative-x}
     * and {@code yShiftSs} as {@code relative-y} (ss × 10 = tenths), each only
     * when non-zero.
     */
    private static void writeStartWedge(PrintWriter pw, Hairpin hairpin) {
        var wedgeType = WedgeTypeMapping.wedgeType(hairpin);

        if (wedgeType == null) {
            return;
        }

        var attrs = new ArrayList<String>();
        attrs.add(MusicXmlTags.ATTR_TYPE);
        attrs.add(wedgeType);
        attrs.add(MusicXmlTags.ATTR_NUMBER);
        attrs.add(MusicXmlTags.NUMBER_1);
        addShiftAttr(attrs, MusicXmlTags.ATTR_RELATIVE_X, hairpin.getX1ShiftSs());
        addShiftAttr(attrs, MusicXmlTags.ATTR_RELATIVE_Y, hairpin.getYShiftSs());

        writeWedgeDirection(pw, attrs);
    }

    /**
     * Emits the stop wedge for {@code hairpin}: {@code <wedge type="stop"
     * number="1">}, carrying {@code x2ShiftSs} as {@code relative-x}
     * (ss × 10 = tenths), only when non-zero.
     */
    private static void writeStopWedge(PrintWriter pw, Hairpin hairpin) {
        var attrs = new ArrayList<String>();
        attrs.add(MusicXmlTags.ATTR_TYPE);
        attrs.add(MusicXmlTags.TYPE_STOP);
        attrs.add(MusicXmlTags.ATTR_NUMBER);
        attrs.add(MusicXmlTags.NUMBER_1);
        addShiftAttr(attrs, MusicXmlTags.ATTR_RELATIVE_X, hairpin.getX2ShiftSs());

        writeWedgeDirection(pw, attrs);
    }

    /**
     * Wraps a {@code <wedge>} (built from {@code wedgeAttrs}, a flat alternating
     * key/value list) in its {@code <direction><direction-type>} envelope.
     */
    private static void writeWedgeDirection(PrintWriter pw, List<String> wedgeAttrs) {
        XML.writeBeginTag(pw, MusicXmlTags.DIRECTION);
        XML.indent();

        XML.writeBeginTag(pw, MusicXmlTags.DIRECTION_TYPE);
        XML.indent();

        XML.writeEmptyTag(pw, MusicXmlTags.WEDGE, wedgeAttrs.toArray(new String[0]));

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.DIRECTION_TYPE);

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.DIRECTION);
    }

    // -------------------------------------------------------------------------
    // Tempo direction
    // -------------------------------------------------------------------------

    /** Returns the first element of the song's first line, or null when empty. */
    private static @Nullable StaffElement firstElementOfSong(Song song) {
        var lines = song.getLines();

        if (lines.isEmpty()) {
            return null;
        }

        var firstLineElements = lines.get(0).getElements();
        return firstLineElements.isEmpty() ? null : firstLineElements.get(0);
    }

    /**
     * Resolves the tempo to emit before {@code element}, or null when it carries
     * none. An element's own {@link TempoChangeAttachment} is a per-note tempo;
     * the first element of the first line falls back to {@code song.getTempo()}
     * (mirroring {@code Line.attachInitialTempoIfNeeded} at write time so the base
     * tempo is emitted even for a not-yet-materialized song).
     */
    private static @Nullable Tempo tempoForElement(
            Song song, @Nullable StaffElement firstSongElement, StaffElement element) {
        var attachment = element.findAttachment(TempoChangeAttachment.class);

        if (attachment != null) {
            return attachment.getTempo();
        }

        if (element == firstSongElement) {
            return song.getTempo();
        }

        return null;
    }

    /**
     * Emits a tempo {@code <direction>}: a {@code <metronome>} beat-unit form
     * ({@code <beat-unit>} + any {@code <beat-unit-dot/>} + {@code <per-minute>}),
     * an optional {@code <words>} description direction-type, and a write-forward
     * {@code <sound tempo>}. A hidden tempo carries {@code print-object="no"} on
     * the {@code <metronome>}; the beat-unit/per-minute are still emitted so the
     * visible tempo survives.
     */
    private static void writeTempoDirection(PrintWriter pw, Tempo tempo) {
        var beatUnit = BeatUnitMapping.forDuration(tempo.getTempoType());

        if (beatUnit == null) {
            // Every Tempo.tempoType is one of the seven mapped Durations, so this
            // is unreachable; the guard keeps the writer null-safe.
            return;
        }

        XML.writeBeginTag(pw, MusicXmlTags.DIRECTION);
        XML.indent();

        writeMetronomeDirectionType(pw, tempo, beatUnit);

        var description = tempo.getTempoDescription();

        if (description != null && !description.isEmpty()) {
            writeWordsDirectionType(pw, description);
        }

        // <sound tempo> is write-forward only; the reader recovers the visible
        // tempo from <metronome>/<per-minute> and ignores this playback value.
        XML.writeEmptyTag(pw, MusicXmlTags.SOUND,
            MusicXmlTags.ATTR_TEMPO, Integer.toString(tempo.getRealTempo()));

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.DIRECTION);
    }

    /**
     * Emits the {@code <direction-type><metronome>} beat-unit form for
     * {@code tempo}, carrying {@code print-object="no"} when the tempo is hidden.
     */
    private static void writeMetronomeDirectionType(
            PrintWriter pw, Tempo tempo, BeatUnitMapping.BeatUnitEntry beatUnit) {
        XML.writeBeginTag(pw, MusicXmlTags.DIRECTION_TYPE);
        XML.indent();

        if (tempo.shouldShowTempo()) {
            XML.writeBeginTag(pw, MusicXmlTags.METRONOME);
        } else {
            XML.writeBeginTag(pw, MusicXmlTags.METRONOME,
                MusicXmlTags.ATTR_PRINT_OBJECT, MusicXmlTags.NO);
        }

        XML.indent();

        XML.writeValue(pw, MusicXmlTags.BEAT_UNIT, beatUnit.token());

        for (var dot = 0; dot < beatUnit.dotCount(); dot++) {
            XML.writeEmptyTag(pw, MusicXmlTags.BEAT_UNIT_DOT);
        }

        XML.writeValue(pw, MusicXmlTags.PER_MINUTE, Integer.toString(tempo.getVisibleTempo()));

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.METRONOME);

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.DIRECTION_TYPE);
    }

    /** Emits a {@code <direction-type><words>} carrying the tempo description. */
    private static void writeWordsDirectionType(PrintWriter pw, String description) {
        XML.writeBeginTag(pw, MusicXmlTags.DIRECTION_TYPE);
        XML.indent();

        XML.writeValue(pw, MusicXmlTags.WORDS, description);

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.DIRECTION_TYPE);
    }

    // -------------------------------------------------------------------------
    // Metric-modulation direction
    // -------------------------------------------------------------------------

    /**
     * Emits a metric-modulation {@code <direction>}: a {@code <metronome>} carrying
     * two {@code <metronome-note>}s related by {@code <metronome-relation>equals</metronome-relation>}
     * — the first for {@code beatChange.duration()} (the left note value), the second
     * for {@code beatChange.beat()} (the right). Tokens and dots come from
     * {@link BeatUnitMapping}. Reuses the same {@code <direction>} envelope as the
     * tempo form; the reader distinguishes the two by the presence of
     * {@code <metronome-note>} vs {@code <beat-unit>}.
     */
    private static void writeMetricModulationDirection(PrintWriter pw, BeatChange beatChange) {
        XML.writeBeginTag(pw, MusicXmlTags.DIRECTION);
        XML.indent();

        XML.writeBeginTag(pw, MusicXmlTags.DIRECTION_TYPE);
        XML.indent();

        XML.writeBeginTag(pw, MusicXmlTags.METRONOME);
        XML.indent();

        writeMetronomeNote(pw, beatChange.duration());
        XML.writeValue(pw, MusicXmlTags.METRONOME_RELATION, MusicXmlTags.RELATION_EQUALS);
        writeMetronomeNote(pw, beatChange.beat());

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.METRONOME);

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.DIRECTION_TYPE);

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.DIRECTION);
    }

    // -------------------------------------------------------------------------
    // Annotation direction
    // -------------------------------------------------------------------------

    /**
     * Emits an annotation {@code <direction placement="above|below">} immediately
     * before the annotated {@code <note>}: a single
     * {@code <direction-type><words halign="…" justify="…" relative-y="…">text</words>}
     * from {@code getAnnotation()} / {@code getXAlignment()} / {@code getUserYOffsetSs()}.
     * {@code halign} and {@code justify} share the one alignment token. {@code default-y}
     * (the computed base position) is write-forward only and intentionally omitted;
     * the reader recovers the annotation from {@code placement} + {@code halign} +
     * {@code relative-y} and binds it to the next note.
     */
    private static void writeAnnotationDirection(PrintWriter pw, Annotation annotation) {
        var placementToken = AnnotationResolver.placementToken(annotation.getPlacement());

        XML.writeBeginTag(pw, MusicXmlTags.DIRECTION, MusicXmlTags.ATTR_PLACEMENT, placementToken);
        XML.indent();

        XML.writeBeginTag(pw, MusicXmlTags.DIRECTION_TYPE);
        XML.indent();

        var alignToken = TextAlignmentMapping.alignToken(annotation.getXAlignment());

        XML.writeValue(pw, MusicXmlTags.WORDS, annotation.getAnnotation(),
            MusicXmlTags.ATTR_HALIGN, alignToken,
            MusicXmlTags.ATTR_JUSTIFY, alignToken,
            MusicXmlTags.ATTR_RELATIVE_Y, formatTenths(ssToTenths(annotation.getUserYOffsetSs()))
        );

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.DIRECTION_TYPE);

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.DIRECTION);
    }

    /**
     * Emits one {@code <metronome-note>}: a {@code <metronome-type>} token plus one
     * {@code <metronome-dot/>} per augmentation dot, both from {@link BeatUnitMapping}.
     */
    private static void writeMetronomeNote(PrintWriter pw, Duration duration) {
        var beatUnit = BeatUnitMapping.forDuration(duration);

        if (beatUnit == null) {
            // Every BeatChange Duration is one of the seven mapped values, so this
            // is unreachable; the guard keeps the writer null-safe.
            return;
        }

        XML.writeBeginTag(pw, MusicXmlTags.METRONOME_NOTE);
        XML.indent();

        XML.writeValue(pw, MusicXmlTags.METRONOME_TYPE, beatUnit.token());

        for (var dot = 0; dot < beatUnit.dotCount(); dot++) {
            XML.writeEmptyTag(pw, MusicXmlTags.METRONOME_DOT);
        }

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.METRONOME_NOTE);
    }

    /**
     * Emits an empty {@code <tag type=… number="1">} marker, adding a
     * {@code relative-y} attribute ({@code verticalShiftSs} → tenths) only when
     * non-zero. Shared by the tuplet-bracket and wavy-line start/stop emitters,
     * which differ only in the tag name, the type token, and whether a vertical
     * shift applies (stop markers always pass zero).
     */
    private static void writeNumberedMarker(PrintWriter pw, String tag, String type, double verticalShiftSs) {
        if (verticalShiftSs != 0) {
            XML.writeEmptyTag(pw, tag,
                MusicXmlTags.ATTR_TYPE, type,
                MusicXmlTags.ATTR_NUMBER, MusicXmlTags.NUMBER_1,
                MusicXmlTags.ATTR_RELATIVE_Y, formatTenths(ssToTenths(verticalShiftSs))
            );
        } else {
            XML.writeEmptyTag(pw, tag,
                MusicXmlTags.ATTR_TYPE, type,
                MusicXmlTags.ATTR_NUMBER, MusicXmlTags.NUMBER_1
            );
        }
    }

    /**
     * Appends an optional position-shift attribute to {@code attrs} (a flat
     * alternating key/value list): when {@code shiftSs} is non-zero, adds
     * {@code attrName} and the ss→tenths-formatted value; otherwise does nothing.
     */
    private static void addShiftAttr(List<String> attrs, String attrName, double shiftSs) {
        if (shiftSs != 0) {
            attrs.add(attrName);
            attrs.add(formatTenths(ssToTenths(shiftSs)));
        }
    }

    // -------------------------------------------------------------------------
    // <time-modification> helper
    // -------------------------------------------------------------------------

    /**
     * Emits a {@code <time-modification>} element with {@code <actual-notes>}
     * set to {@code grade} (the tuplet numerator) and {@code <normal-notes>}
     * set to the largest power of two strictly less than {@code grade}
     * (3→2, 5→4, 6→4, 7→4).
     *
     * <p>Emitted on every note in a tuplet span (including rests), after
     * {@code <accidental>} and before {@code <stem>} per the MusicXML schema.
     * {@code <normal-notes>} is write-forward only — the reader recovers the
     * grade from {@code <actual-notes>} and ignores {@code <normal-notes>}.
     */
    private static void writeTimeModification(PrintWriter pw, int grade) {
        var normalNotes = largestPowerOfTwoBelowGrade(grade);
        XML.writeBeginTag(pw, MusicXmlTags.TIME_MOD);
        XML.indent();
        XML.writeValue(pw, MusicXmlTags.ACTUAL_NOTES, Integer.toString(grade));
        XML.writeValue(pw, MusicXmlTags.NORMAL_NOTES, Integer.toString(normalNotes));
        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.TIME_MOD);
    }

    /**
     * Returns the largest power of two strictly less than {@code grade}.
     *
     * <p>Examples: 3→2, 5→4, 6→4, 7→4.
     * This is the MusicXML convention for {@code <normal-notes>} in a tuplet.
     */
    private static int largestPowerOfTwoBelowGrade(int grade) {
        var result = 1;

        while (result * 2 < grade) {
            result *= 2;
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Per-element span precompute
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when both span endpoints fall within the line's
     * element range — the guard every span loop in {@link #buildSpanIndex}
     * applies before bucketing a span (a malformed song can leave an anchor or
     * end index dangling at -1 or past the element count).
     */
    private static boolean indicesInRange(int anchorIdx, int endIdx, int count) {
        return anchorIdx >= 0 && endIdx >= 0 && anchorIdx < count && endIdx < count;
    }

    /**
     * Buckets one hairpin's start and end onto its anchor and end builders. A
     * span whose endpoints fall outside the line is silently skipped.
     */
    private static void bucketHairpin(SpanBuilder[] builders, Hairpin hairpin, int count) {
        var anchorIdx = hairpin.getAnchorElementIndex();
        var endIdx = hairpin.getEndElementIndex();

        if (!indicesInRange(anchorIdx, endIdx, count)) {
            return;
        }

        var anchorBuilder = builders[anchorIdx];
        var endBuilder = builders[endIdx];
        anchorBuilder.hairpinsStartingHere = appendLazily(anchorBuilder.hairpinsStartingHere, hairpin);
        endBuilder.hairpinsEndingHere = appendLazily(endBuilder.hairpinsEndingHere, hairpin);
    }

    /**
     * Builds the per-element-index span marker array for {@code line}.
     *
     * <p>For each of the six span types, this method calls the line accessor
     * once and resolves every span's anchor/end element index exactly once
     * via {@link songscribe.dom.RangeElement#getAnchorElementIndex()} /
     * {@link songscribe.dom.RangeElement#getEndElementIndex()}.
     * The element loop can then do O(1) lookups instead of calling
     * {@code indexOf} (O(n)) per element per span.
     *
     * <p>Bucket rules:
     * <ul>
     *   <li><b>Beam / Tuplet / Trill</b>: span reference set on anchor through
     *       end (inclusive); anchor and end flags set only at the endpoints.</li>
     *   <li><b>Tie</b>: {@code tieStart} reference set at anchor; {@code tieStop}
     *       set at end. Both may be non-null on the same note when it is the
     *       end of one tie and the anchor of the next in a chain.</li>
     *   <li><b>Crescendo / Diminuendo / Ending</b>: anchor and end indices only;
     *       Ending additionally records its split index when present.</li>
     * </ul>
     */
    private static IndexSpanMarkers[] buildSpanIndex(Line line) {
        var count = line.elementCount();
        var builders = new SpanBuilder[count];

        for (int i = 0; i < count; i++) {
            builders[i] = new SpanBuilder();
        }

        // Beams: pre-compute per-note, per-level beam values for every note
        // in the group [anchor, end].  A single-note beam (anchor == end)
        // is degenerate and produces no <beam> output.
        for (var beam : line.findRangeElements(Beam.class)) {
            var anchorIdx = beam.getAnchorElementIndex();
            var endIdx = beam.getEndElementIndex();

            if (!indicesInRange(anchorIdx, endIdx, count)) {
                continue;
            }

            // Skip degenerate single-note beams — they cannot be beamed.
            if (anchorIdx == endIdx) {
                continue;
            }

            for (int i = anchorIdx; i <= endIdx; i++) {
                builders[i].beamLevelValues = computeNoteBeamValues(line, i, anchorIdx, endIdx);
            }
        }

        // Ties: anchor and end flags are independent so a note that is the end
        // of one tie and the anchor of the next will have both flags set.
        for (var tie : line.findTies()) {
            var anchorIdx = tie.getAnchorElementIndex();
            var endIdx = tie.getEndElementIndex();

            if (!indicesInRange(anchorIdx, endIdx, count)) {
                continue;
            }

            builders[anchorIdx].tieStart = tie;
            builders[endIdx].tieStop = tie;
        }

        // Tuplets: set the span reference on every note in the group [anchor, end].
        for (var tuplet : line.findRangeElements(Tuplet.class)) {
            var anchorIdx = tuplet.getAnchorElementIndex();
            var endIdx = tuplet.getEndElementIndex();

            if (!indicesInRange(anchorIdx, endIdx, count)) {
                continue;
            }

            builders[anchorIdx].isTupletAnchor = true;
            builders[endIdx].isTupletEnd = true;

            for (int i = anchorIdx; i <= endIdx; i++) {
                builders[i].tuplet = tuplet;
            }
        }

        // Trills: set the span reference on every note in the group [anchor, end].
        for (var trill : line.findRangeElements(Trill.class)) {
            var anchorIdx = trill.getAnchorElementIndex();
            var endIdx = trill.getEndElementIndex();

            if (!indicesInRange(anchorIdx, endIdx, count)) {
                continue;
            }

            builders[anchorIdx].isTrillAnchor = true;
            builders[endIdx].isTrillEnd = true;

            for (int i = anchorIdx; i <= endIdx; i++) {
                builders[i].trill = trill;
            }
        }

        // Hairpins (measure-level): anchor and end indices only. Crescendos and
        // diminuendos share one pair of start/end buckets; the wedge type is
        // recovered from the Hairpin subtype at emission time.
        for (var crescendo : line.getCrescendos()) {
            bucketHairpin(builders, crescendo, count);
        }

        for (var diminuendo : line.getDiminuendos()) {
            bucketHairpin(builders, diminuendo, count);
        }

        // Endings (measure-level): one SongScribe Ending expands to one or two
        // MusicXML voltas, folded onto the <barline> elements Phase 2 emits.
        //
        //   anchor               split (REPEAT_RIGHT /         end
        //   (REPEAT_LEFT or       REPEAT_LEFT_RIGHT)           (terminal barline)
        //    SINGLE_BARLINE)
        //        |                      |                          |
        //   [1 start]            [1 stop] [2 start]            [2 stop]
        //        '------ volta 1 -------'  '------- volta 2 -------'
        //
        // A split-less ending (no REPEAT between anchor and end) is a single
        // bracket: [1 start] at the anchor → [1 stop] at the end.
        //
        // Markers are bucketed per element index as left-barline vs right-barline
        // children so the element loop can attach them to the correct <barline>
        // emission without re-deriving the structure:
        //   - REPEAT_LEFT anchor  → forward (left) barline   → left bucket
        //   - SINGLE_BARLINE anchor / terminal end / split stop → right barline
        //   - split [2 start]     → forward (left) barline    → left bucket
        // getSplitIndex() returns -1 for split-less single-bracket endings.
        for (var ending : LineEndingSupport.findEndings(line)) {
            var anchorIdx = ending.getAnchorElementIndex();
            var endIdx = ending.getEndElementIndex();

            if (!indicesInRange(anchorIdx, endIdx, count)) {
                continue;
            }

            var splitIdx = ending.getSplitIndex(line);
            var hasSplit = splitIdx >= 0 && splitIdx < count;

            // Anchor: <ending number="1" type="start">. A REPEAT_LEFT anchor is
            // written as a forward (left) barline; any other anchor (SINGLE_BARLINE)
            // closes the previous measure as a right barline.
            var anchorStart = new EndingMarker(MusicXmlTags.NUMBER_1, MusicXmlTags.TYPE_START);
            var anchorBuilder = builders[anchorIdx];

            if (line.getElement(anchorIdx).getType() == ElementType.REPEAT_LEFT) {
                anchorBuilder.endingLeftBarlineMarkers = appendLazily(anchorBuilder.endingLeftBarlineMarkers, anchorStart);
            } else {
                anchorBuilder.endingRightBarlineMarkers = appendLazily(anchorBuilder.endingRightBarlineMarkers, anchorStart);
            }

            // End: <ending number type="stop">. number is 2 for a two-bracket
            // ending (a split exists) and 1 for a split-less single bracket.
            var endNumber = hasSplit ? MusicXmlTags.NUMBER_2 : MusicXmlTags.NUMBER_1;
            var endStop = new EndingMarker(endNumber, MusicXmlTags.TYPE_STOP);
            var endBuilder = builders[endIdx];

            if (line.getElement(endIdx).getType() == ElementType.REPEAT_LEFT) {
                endBuilder.endingLeftBarlineMarkers = appendLazily(endBuilder.endingLeftBarlineMarkers, endStop);
            } else {
                endBuilder.endingRightBarlineMarkers = appendLazily(endBuilder.endingRightBarlineMarkers, endStop);
            }

            // Split: <ending number="1" type="stop"> closes volta 1 on the right
            // (backward) barline; <ending number="2" type="start"> opens volta 2
            // on the left (forward) barline.
            if (hasSplit) {
                var splitBuilder = builders[splitIdx];
                splitBuilder.endingRightBarlineMarkers = appendLazily(splitBuilder.endingRightBarlineMarkers,
                    new EndingMarker(MusicXmlTags.NUMBER_1, MusicXmlTags.TYPE_STOP));
                splitBuilder.endingLeftBarlineMarkers = appendLazily(splitBuilder.endingLeftBarlineMarkers,
                    new EndingMarker(MusicXmlTags.NUMBER_2, MusicXmlTags.TYPE_START));
            }
        }

        // Assemble the final immutable per-index records.
        var result = new IndexSpanMarkers[count];

        for (int i = 0; i < count; i++) {
            result[i] = builders[i].build();
        }

        return result;
    }

    /**
     * Empty-string sentinel stored in beam-level values for levels where the
     * note has no {@code <beam>} element. A level entry is empty when the note
     * is not short enough for that secondary beam level.
     */
    private static final String NO_BEAM_AT_LEVEL = "";

    /**
     * Computes the MusicXML {@code <beam>} text-content values for note
     * {@code noteIdx} within the beam group [{@code anchorIdx}, {@code endIdx}].
     *
     * <p>Returns an array indexed by {@code (number - 1)}: {@code result[0]}
     * holds the value for {@code <beam number="1">}, {@code result[1]} for
     * {@code number="2"}, etc. An empty-string entry means no {@code <beam>}
     * element should be emitted at that number for this note.
     *
     * <p>Level 0 (primary, {@code number="1"}): always {@code begin/continue/end}
     * for every note in the group — never a hook.
     *
     * <p>Secondary levels (1 = 16th, 2 = 32nd): for each level L, this method
     * finds the maximal contiguous run of notes at that level containing
     * {@code noteIdx}. A run of length ≥ 2 emits {@code begin/continue/end};
     * a run of length 1 is a partial-beam hook whose direction is determined
     * by {@link BeamMath#stubRight}.
     *
     * <p>Hook direction: {@code stubRight == true} → {@code "forward hook"};
     * {@code stubRight == false} → {@code "backward hook"}.
     */
    private static String[] computeNoteBeamValues(Line line, int noteIdx, int anchorIdx, int endIdx) {
        // Initialize all levels to the "no beam" sentinel; each slot is overwritten
        // if the note participates at that level. Sized by BeamMath.LEVEL_COUNT so
        // the array always matches the level loop below.
        var values = new String[BeamMath.LEVEL_COUNT];
        Arrays.fill(values, NO_BEAM_AT_LEVEL);

        // Level 0 (primary beam, number="1"): begin/continue/end — never a hook.
        if (noteIdx == anchorIdx) {
            values[0] = MusicXmlTags.BEAM_BEGIN;
        } else if (noteIdx == endIdx) {
            values[0] = MusicXmlTags.BEAM_END;
        } else {
            values[0] = MusicXmlTags.BEAM_CONTINUE;
        }

        // Secondary beam levels: level 1 = 16th (number="2"), level 2 = 32nd (number="3").
        for (var level = 1; level < BeamMath.LEVEL_COUNT; level++) {
            if (!BeamMath.noteTypeInLevel(line, noteIdx, level)) {
                // This note does not participate at this level; no <beam> element.
                continue;
            }

            // Find the maximal contiguous run at this level that contains noteIdx.
            var runStart = noteIdx;

            while (runStart > anchorIdx && BeamMath.noteTypeInLevel(line, runStart - 1, level)) {
                runStart--;
            }

            var runEnd = noteIdx;

            while (runEnd < endIdx && BeamMath.noteTypeInLevel(line, runEnd + 1, level)) {
                runEnd++;
            }

            if (runStart == runEnd) {
                // Single-note run: emit a partial-beam hook.  Direction is a pure
                // function of note durations + position in the beam group.
                values[level] = BeamMath.stubRight(line, noteIdx, anchorIdx, endIdx)
                    ? MusicXmlTags.BEAM_FORWARD_HOOK
                    : MusicXmlTags.BEAM_BACKWARD_HOOK;
            } else if (noteIdx == runStart) {
                values[level] = MusicXmlTags.BEAM_BEGIN;
            } else if (noteIdx == runEnd) {
                values[level] = MusicXmlTags.BEAM_END;
            } else {
                values[level] = MusicXmlTags.BEAM_CONTINUE;
            }
        }

        return values;
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /**
     * Formats a MusicXML tenths value as a two-decimal-place string.
     * MusicXML tenths are decimal numbers; two decimal places are sufficient
     * precision for all position values. {@link Locale#ROOT} forces a period
     * decimal separator so the output stays valid {@code xs:decimal} regardless
     * of the JVM default locale.
     */
    private static String formatTenths(double tenths) {
        return String.format(Locale.ROOT, "%.2f", tenths);
    }

    /**
     * Converts a staff-space measure to MusicXML tenths — the inverse of the
     * reader's {@code tenthsToSs}. All position values share this single
     * conversion so the scattered {@code × TENTHS_PER_STAFF_SPACE} arithmetic
     * has one source of truth.
     */
    private static double ssToTenths(double ss) {
        return ss * MusicXmlTags.TENTHS_PER_STAFF_SPACE;
    }

    /**
     * Closes the current measure, increments the measure counter, opens a new
     * measure, and writes the forward-repeat left barline into it. Returns the
     * updated measure number. {@code forwardLeftEndings} are folded onto the
     * forward-left {@code <barline>} (an ending anchor or volta-2 start).
     */
    private static int openForwardRepeatMeasure(PrintWriter pw, int measureNumber, List<EndingMarker> forwardLeftEndings) {
        closeMeasure(pw);
        measureNumber++;
        openMeasure(pw, measureNumber);
        writeForwardRepeatLeftBarline(pw, forwardLeftEndings);
        return measureNumber;
    }

    /**
     * Writes {@code <measure number="N">} at the current indent and pushes one
     * level so subsequent measure-body content is indented correctly.
     */
    private static void openMeasure(PrintWriter pw, int measureNumber) {
        XML.writeBeginTag(pw, MusicXmlTags.MEASURE, MusicXmlTags.ATTR_NUMBER, Integer.toString(measureNumber));
        XML.indent();
    }

    /**
     * Pops one indent level and writes the {@code </measure>} closing tag.
     */
    private static void closeMeasure(PrintWriter pw) {
        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.MEASURE);
    }

    // -------------------------------------------------------------------------
    // Attribute block
    // -------------------------------------------------------------------------

    private static void writeAttributes(Song song, PrintWriter pw) {
        XML.writeBeginTag(pw, MusicXmlTags.ATTRIBUTES);
        XML.indent();

        XML.writeValue(pw, "divisions", Integer.toString(DIVISIONS));

        // <key> with inline child <fifths> — measure 1 carries the song default.
        var fifths = KeySignatureMapping.toFifths(song.getDefaultKeyType(), song.getDefaultKeyAccidentalCount());
        XML.printIndent(pw);
        pw.println("<key><fifths>" + fifths + "</fifths></key>");

        // <time print-object="no"> with inline self-closing child <senza-misura/>
        XML.printIndent(pw);
        pw.println("<time print-object=\"no\"><senza-misura/></time>");

        // <clef> with inline children
        XML.printIndent(pw);
        pw.println("<clef><sign>G</sign><line>2</line></clef>");

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.ATTRIBUTES);
    }

    /**
     * Emits a key-only {@code <attributes>} block carrying just
     * {@code <key><fifths>}, used at a later line whose key differs from the
     * running key signature.
     */
    private static void writeKeyOnlyAttributes(PrintWriter pw, int fifths) {
        XML.writeBeginTag(pw, MusicXmlTags.ATTRIBUTES);
        XML.indent();

        XML.printIndent(pw);
        pw.println("<key><fifths>" + fifths + "</fifths></key>");

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.ATTRIBUTES);
    }

    /**
     * The signed-fifths encoding of {@code line}'s effective key: its own key when
     * set, otherwise the song default (matching how every line is materialized
     * from the default on load).
     */
    private static int effectiveKeyFifths(Song song, Line line) {
        var lineKeyType = line.getKeyType();

        if (lineKeyType == null) {
            return KeySignatureMapping.toFifths(song.getDefaultKeyType(), song.getDefaultKeyAccidentalCount());
        }

        return KeySignatureMapping.toFifths(lineKeyType, line.getKeyAccidentalCount());
    }

    // -------------------------------------------------------------------------
    // System-break marker
    // -------------------------------------------------------------------------

    private static void writePrintNewSystem(PrintWriter pw) {
        XML.writeEmptyTag(pw, MusicXmlTags.PRINT, MusicXmlTags.ATTR_NEW_SYSTEM, MusicXmlTags.YES);
    }

    // -------------------------------------------------------------------------
    // Barline helpers
    // -------------------------------------------------------------------------

    /**
     * Emits a forward-repeat left barline (heavy-light style, forward direction),
     * folding {@code endings} onto it.
     */
    private static void writeForwardRepeatLeftBarline(PrintWriter pw, List<EndingMarker> endings) {
        writeBarlineFor(pw, ElementType.REPEAT_LEFT, endings);
    }

    /**
     * Emits a backward-repeat right barline (light-heavy style, backward direction),
     * folding {@code endings} onto it.
     */
    private static void writeBackwardRepeatRightBarline(PrintWriter pw, List<EndingMarker> endings) {
        writeBarlineFor(pw, ElementType.REPEAT_RIGHT, endings);
    }

    /**
     * Looks up the {@link BarlineStyleMapping.BarlineEntry} for the given
     * {@link ElementType} and delegates to {@link #writeBarline(PrintWriter, BarlineStyleMapping.BarlineEntry, List)}.
     * The type must have a forward-map entry; types without one (e.g.
     * {@code REPEAT_LEFT_RIGHT}) are handled by their own callers before this
     * method is reached.
     */
    private static void writeBarlineFor(PrintWriter pw, ElementType type, List<EndingMarker> endings) {
        var entry = BarlineStyleMapping.forElementType(type);

        if (entry == null) {
            return;
        }

        writeBarline(pw, entry, endings);
    }

    /**
     * Emits a {@code <barline>} using the location stored in the
     * {@link BarlineStyleMapping.BarlineEntry}, folding {@code endings} onto it.
     */
    private static void writeBarline(PrintWriter pw, BarlineStyleMapping.BarlineEntry entry, List<EndingMarker> endings) {
        writeBarline(pw, entry.location(), entry.barStyle(), entry.repeatDirection(), endings);
    }

    /** Emits {@code <barline location="right"><bar-style>none</bar-style></barline>}. */
    private static void writeInvisibleRightBarline(PrintWriter pw) {
        writeBarline(pw, BarlineStyleMapping.LOCATION_RIGHT, BarlineStyleMapping.BAR_STYLE_NONE, null, List.of());
    }

    /**
     * Emits {@code <barline location="left"><bar-style>none</bar-style>…</barline>}
     * carrying {@code endings}. Used to host a volta-2 {@code <ending number="2"
     * type="start">} at the start of the measure following a REPEAT_RIGHT split,
     * where no real left barline element exists.
     */
    private static void writeInvisibleLeftBarline(PrintWriter pw, List<EndingMarker> endings) {
        writeBarline(pw, BarlineStyleMapping.LOCATION_LEFT, BarlineStyleMapping.BAR_STYLE_NONE, null, endings);
    }

    /**
     * Emits a full {@code <barline>} element with {@code <bar-style>}, any
     * {@code <ending>} children (which precede {@code <repeat>} per schema), and,
     * when non-null, a {@code <repeat direction="..."/>} child.
     */
    private static void writeBarline(PrintWriter pw, String location, String barStyle,
            @Nullable String repeatDirection, List<EndingMarker> endings) {
        XML.writeBeginTag(pw, MusicXmlTags.BARLINE, MusicXmlTags.ATTR_LOCATION, location);
        XML.indent();

        XML.writeValue(pw, MusicXmlTags.BAR_STYLE, barStyle);

        // <ending> precedes <repeat> inside <barline> per the MusicXML schema.
        for (var ending : endings) {
            XML.writeEmptyTag(pw, MusicXmlTags.ENDING,
                MusicXmlTags.ATTR_NUMBER, ending.number(),
                MusicXmlTags.ATTR_TYPE, ending.type()
            );
        }

        if (repeatDirection != null) {
            XML.writeEmptyTag(pw, MusicXmlTags.REPEAT, MusicXmlTags.ATTR_DIRECTION, repeatDirection);
        }

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.BARLINE);
    }
}
