/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.io.musicxml;

import static org.assertj.core.api.Assumptions.assumeThat;

import java.awt.Component;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Beam;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.Duration;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.KeyType;
import songscribe.dom.Lyric;
import songscribe.dom.Song;
import songscribe.dom.SongMetadata;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.io.SongIO;
import songscribe.layout.Ending;

/**
 * One-shot generator for the synthetic half of the MusicXML losslessness corpus.
 *
 * <p>Each {@code song*()} method builds a {@link Song} that exercises one slice of
 * the SongScribe → MusicXML mapping (see {@code plans/migrations/musicxml/musicxml.md}).
 * {@link #generateCorpus()} serializes every one through the <em>legacy</em>
 * {@link SongIO#writeSong} into {@code src/test/resources/corpus/synthetic/}, so the
 * committed {@code .mssw} files are real native documents that the legacy reader loads.
 *
 * <p>This is a build tool, not a regression test: it is skipped unless run with
 * {@code -Dcorpus.generate=true}. Regenerate the corpus with:
 *
 * <pre>{@code
 *   EXTRA_JVM_ARGS="-Dcorpus.generate=true" ./scripts/test.sh MusicXmlCorpusGenerator
 * }</pre>
 *
 * The losslessness gate that consumes the corpus is
 * {@link MusicXmlCorpusLosslessnessTest}.
 */
class MusicXmlCorpusGenerator extends MusicXmlRoundTripSupport {

    private static final Path SYNTHETIC_DIR = Path.of("src/test/resources/corpus/synthetic");

    // -- Shared pitch anchors (staffPosition 0 == B4, increasing downward) --
    private static final int B4  = 0;
    private static final int F4  = 3;
    private static final int C4  = C4_STAFF_POSITION;   // 6, middle C
    private static final int C5  = -1;
    private static final int A4  = 1;

    // -- Dot counts --
    private static final int ONE_DOT  = 1;
    private static final int TWO_DOTS = 2;

    // -- Tempi (BPM) --
    private static final int BASE_BPM        = 120;
    private static final int PER_NOTE_BPM    = 90;
    private static final int HIDDEN_BPM      = 100;
    private static final int DESCRIBED_BPM   = 138;

    // -- Key accidental counts --
    private static final int THREE_SHARPS = 3;
    private static final int TWO_FLATS    = 2;
    private static final int ONE_SHARP    = 1;

    // -- Tuplet grades --
    private static final int TRIPLET    = 3;
    private static final int QUINTUPLET = 5;

    // -- Span offsets (staff spaces; integer-valued for exact ss↔tenths) --
    private static final int    TUPLET_VERTICAL_SS = 2;
    private static final int    TRILL_Y_SS         = 3;
    private static final double HAIRPIN_X1_SS      = 2.0;
    private static final double HAIRPIN_X2_SS      = -1.0;
    private static final double HAIRPIN_Y_SS       = 3.0;

    // -- Verse numbers --
    private static final int VERSE_1 = 1;
    private static final int VERSE_2 = 2;

    // -- Header metadata --
    private static final String TITLE     = "Corpus Kitchen Sink";
    private static final String NUMBER    = "42";
    private static final String PLACE     = "Jamaica, New York";
    private static final String COMPOSER  = "Corpus Composer";
    private static final String LYRICIST  = "Corpus Lyricist";
    private static final String SUBTITLE  = "A Synthetic Subtitle";
    private static final String COMP_YEAR  = "1987";
    private static final int    COMP_MONTH = 12;
    private static final int    COMP_DAY   = 1;
    private static final String WORDS_YEAR  = "1990";
    private static final int    WORDS_MONTH = 5;
    private static final int    ABSENT_DATE_PART = 0;

    // -- Score-below text --
    private static final String UNDERLYRICS = "Corpus underlyrics line";
    private static final String BANGLA      = "Corpus Bangla lyrics line";
    private static final String TRANSLATION = "Corpus translated lyrics line";
    private static final String FOOTNOTES   = "Corpus footnote line";

    // -- Layout --
    private static final double LINE_WIDTH_SS         = 130.0;
    private static final double ROW_HEIGHT_ADJ_SS     = 2.5;
    private static final double ATTRIBUTION_Y_OFF_SS  = 4.0;

    // -- Annotation offsets --
    private static final double ANNOTATION_ABOVE_Y_SS = 2.0;
    private static final double ANNOTATION_BELOW_Y_SS = -2.0;

    // -- Custom fonts (kitchen-sink only; distinct sizes so a role mix-up shows) --
    private static final int TITLE_FONT_SIZE           = 28;
    private static final int LYRIC_FONT_SIZE           = 36;
    private static final int WORD_FONT_SIZE            = 34;
    private static final int SUB_ATTRIBUTION_FONT_SIZE = 38;

    private static final String NO_DESCRIPTION = "";

    /**
     * A named song plus the fonts to serialize it with. The fonts ride into the
     * {@code <view>} block of the {@code .mssw} and are recovered on load.
     */
    private record Entry(String name, Song song, DocumentFonts fonts) {}

    @Test
    void generateCorpus() throws Exception {
        assumeThat(Boolean.getBoolean("corpus.generate"))
            .as("corpus generation is a build tool; run with -Dcorpus.generate=true")
            .isTrue();

        Files.createDirectories(SYNTHETIC_DIR);

        for (var entry : corpus()) {
            var file = SYNTHETIC_DIR.resolve(entry.name() + ".mssw");

            try (var pw = new PrintWriter(Files.newBufferedWriter(file))) {
                SongIO.writeSong(entry.song(), entry.fonts(), pw);
            }
        }
    }

    private static List<Entry> corpus() {
        var entries = new ArrayList<Entry>();
        entries.add(defaultFontsEntry("structural-barlines", songStructuralBarlines()));
        entries.add(defaultFontsEntry("note-durations-rests", songDurationsAndRests()));
        entries.add(defaultFontsEntry("note-pitches-accidentals", songPitchesAndAccidentals()));
        entries.add(defaultFontsEntry("note-articulations", songArticulations()));
        entries.add(defaultFontsEntry("spans", songSpans()));
        entries.add(defaultFontsEntry("key-tempo", songKeyAndTempo()));
        entries.add(defaultFontsEntry("lyrics", songLyrics()));
        entries.add(defaultFontsEntry("header-credits", songHeaderAndCredits()));
        entries.add(defaultFontsEntry("annotations", songAnnotations()));
        entries.add(new Entry("kitchen-sink", songKitchenSink(), customFonts()));
        return entries;
    }

    private static Entry defaultFontsEntry(String name, Song song) {
        return new Entry(name, song, DocumentFonts.defaultFonts());
    }

    // -------------------------------------------------------------------------
    // Structural — barlines, repeats, line breaks (musicxml.md § Structural model)
    // -------------------------------------------------------------------------

    private static Song songStructuralBarlines() {
        return buildSong(
            // A bare line break: an empty line with no barline element.
            line -> line.addElement(ElementType.CROTCHET.newInstance()),
            // Single and double barlines mid-line.
            line -> {
                line.addElement(ElementType.CROTCHET.newInstance());
                line.addElement(ElementType.SINGLE_BARLINE.newInstance());
                line.addElement(ElementType.CROTCHET.newInstance());
                line.addElement(ElementType.DOUBLE_BARLINE.newInstance());
                line.addElement(ElementType.CROTCHET.newInstance());
            },
            // A straddling repeat, then a forward/backward repeat pair.
            line -> {
                line.addElement(ElementType.REPEAT_LEFT.newInstance());
                line.addElement(ElementType.CROTCHET.newInstance());
                line.addElement(ElementType.REPEAT_RIGHT.newInstance());
                line.addElement(ElementType.CROTCHET.newInstance());
                line.addElement(ElementType.REPEAT_LEFT_RIGHT.newInstance());
                line.addElement(ElementType.CROTCHET.newInstance());
            },
            // Terminal line ends in the final double barline (a valid terminal).
            line -> {
                line.addElement(ElementType.CROTCHET.newInstance());
                line.addElement(ElementType.FINAL_DOUBLE_BARLINE.newInstance());
            }
        );
    }

    // -------------------------------------------------------------------------
    // Notes — durations, rests, dots, grace (musicxml.md § Note / element)
    // -------------------------------------------------------------------------

    private static Song songDurationsAndRests() {
        var noteTypes = List.of(
            ElementType.SEMIBREVE, ElementType.MINIM, ElementType.CROTCHET,
            ElementType.QUAVER, ElementType.SEMIQUAVER, ElementType.DEMI_SEMIQUAVER);
        var restTypes = List.of(
            ElementType.SEMIBREVE_REST, ElementType.MINIM_REST, ElementType.CROTCHET_REST,
            ElementType.QUAVER_REST, ElementType.SEMIQUAVER_REST, ElementType.DEMI_SEMIQUAVER_REST);

        return buildSong(
            line -> {
                for (var type : noteTypes) {
                    var note = type.newInstance();
                    note.setStaffPosition(C4);
                    line.addElement(note);
                }
            },
            line -> {
                for (var type : restTypes) {
                    line.addElement(type.newInstance());
                }
            },
            line -> {
                var oneDot = ElementType.MINIM.newInstance();
                oneDot.setDotCount(ONE_DOT);
                line.addElement(oneDot);

                var twoDots = ElementType.MINIM.newInstance();
                twoDots.setDotCount(TWO_DOTS);
                line.addElement(twoDots);

                var grace = ElementType.GRACE_QUAVER.newInstance();
                grace.setUpper(true);
                grace.setStemDirectionAuto(false);
                line.addElement(grace);
                line.addElement(ElementType.CROTCHET.newInstance());
            }
        );
    }

    // -------------------------------------------------------------------------
    // Notes — pitches and accidentals (musicxml.md § Note / element)
    // -------------------------------------------------------------------------

    private static Song songPitchesAndAccidentals() {
        var accidentals = List.of(
            StaffElement.Accidental.SHARP, StaffElement.Accidental.FLAT,
            StaffElement.Accidental.NATURAL, StaffElement.Accidental.DOUBLE_SHARP,
            StaffElement.Accidental.DOUBLE_FLAT);
        var pitches = List.of(C5, B4, A4, F4, C4);

        return buildSong(
            line -> {
                for (var pitch : pitches) {
                    var note = ElementType.CROTCHET.newInstance();
                    note.setStaffPosition(pitch);
                    line.addElement(note);
                }
            },
            line -> {
                for (var accidental : accidentals) {
                    var note = ElementType.CROTCHET.newInstance();
                    note.setStaffPosition(C4);
                    note.setAccidental(accidental);
                    line.addElement(note);
                }
            },
            // Cautionary / parenthesized accidental (accidental must be set first).
            line -> {
                var note = ElementType.CROTCHET.newInstance();
                note.setStaffPosition(C4);
                note.setAccidental(StaffElement.Accidental.SHARP);
                note.setAccidentalInParentheses(true);
                line.addElement(note);
            }
        );
    }

    // -------------------------------------------------------------------------
    // Notes — articulations, dynamics, stems, offsets, slides (musicxml.md § Note)
    // -------------------------------------------------------------------------

    private static Song songArticulations() {
        var dynamics = List.of(
            DynamicAttachment.DynamicType.PIANISSIMO, DynamicAttachment.DynamicType.MEZZO_FORTE,
            DynamicAttachment.DynamicType.FORTE, DynamicAttachment.DynamicType.SFORZANDO);

        return buildSong(
            line -> {
                var accented = ElementType.CROTCHET.newInstance();
                line.addElement(accented);
                accented.addArticulation(new Articulation(accented, ArticulationType.ACCENT));

                var staccato = ElementType.CROTCHET.newInstance();
                line.addElement(staccato);
                staccato.addArticulation(new Articulation(staccato, ArticulationType.STACCATO));

                var fermata = ElementType.CROTCHET.newInstance();
                line.addElement(fermata);
                fermata.addAttachment(new FermataAttachment(fermata));
            },
            line -> {
                for (var dynamic : dynamics) {
                    var note = ElementType.CROTCHET.newInstance();
                    line.addElement(note);
                    note.addAttachment(new DynamicAttachment(note, dynamic));
                }
            },
            line -> {
                var up = ElementType.CROTCHET.newInstance();
                up.setUpper(true);
                up.setStemDirectionAuto(false);
                line.addElement(up);

                var down = ElementType.CROTCHET.newInstance();
                down.setUpper(false);
                down.setStemDirectionAuto(false);
                line.addElement(down);

                // Auto stem: leave a fresh instance untouched.
                line.addElement(ElementType.CROTCHET.newInstance());

                var shifted = ElementType.CROTCHET.newInstance();
                shifted.setXOffsetPx(X_OFFSET_PX);
                line.addElement(shifted);
            },
            line -> {
                // Connected glissando slide: stored on the start note only.
                var glissStart = ElementType.CROTCHET.newInstance();
                glissStart.setStaffPosition(C5);
                glissStart.setGlissando();
                line.addElement(glissStart);
                var glissEnd = ElementType.CROTCHET.newInstance();
                glissEnd.setStaffPosition(C4);
                line.addElement(glissEnd);

                // Fall on a single note.
                var falling = ElementType.CROTCHET.newInstance();
                falling.setFall();
                line.addElement(falling);

                // Breath mark is a trailing line element after the note it follows.
                line.addElement(ElementType.CROTCHET.newInstance());
                line.addElement(ElementType.BREATH_MARK.newInstance());

                // Standalone slide element.
                line.addElement(ElementType.CROTCHET.newInstance());
                line.addElement(ElementType.SLIDE.newInstance());
                line.addElement(ElementType.CROTCHET.newInstance());
            }
        );
    }

    // -------------------------------------------------------------------------
    // Line-level range spans (musicxml.md § Line-level range spans)
    // -------------------------------------------------------------------------

    private static Song songSpans() {
        return buildSong(
            // Beaming across a group of quavers.
            line -> {
                var beamNotes = addNotes(line, ElementType.QUAVER, 4);
                line.addBeaming(new Beam(beamNotes.get(0), beamNotes.get(beamNotes.size() - 1)));
            },
            // A tie chain across three crotchets, stored as two adjacent spans.
            line -> {
                var tied = addNotes(line, ElementType.CROTCHET, 3);
                line.addTie(new Tie(tied.get(0), tied.get(1)));
                line.addTie(new Tie(tied.get(1), tied.get(2)));
            },
            // A triplet with a vertical offset and a quintuplet.
            line -> {
                var triplet = addNotes(line, ElementType.QUAVER, TRIPLET);
                var tupletSpan = new Tuplet(triplet.get(0), triplet.get(TRIPLET - 1), TRIPLET);
                tupletSpan.setVerticalPositionSs(TUPLET_VERTICAL_SS);
                line.addTuplet(tupletSpan);

                var quintuplet = addNotes(line, ElementType.SEMIQUAVER, QUINTUPLET);
                line.addTuplet(new Tuplet(quintuplet.get(0), quintuplet.get(QUINTUPLET - 1), QUINTUPLET));
            },
            // Crescendo and diminuendo with user offsets.
            line -> {
                var crescNotes = addNotes(line, ElementType.CROTCHET, 2);
                var crescendo = new Crescendo(crescNotes.get(0), crescNotes.get(1));
                crescendo.setX1ShiftSs(HAIRPIN_X1_SS);
                crescendo.setX2ShiftSs(HAIRPIN_X2_SS);
                crescendo.setYShiftSs(HAIRPIN_Y_SS);
                line.addCrescendo(crescendo);

                var dimNotes = addNotes(line, ElementType.CROTCHET, 2);
                var diminuendo = new Diminuendo(dimNotes.get(0), dimNotes.get(1));
                diminuendo.setX1ShiftSs(HAIRPIN_X1_SS);
                diminuendo.setX2ShiftSs(HAIRPIN_X2_SS);
                diminuendo.setYShiftSs(HAIRPIN_Y_SS);
                line.addDiminuendo(diminuendo);
            },
            // A single-note trill and a multi-note trill with a y offset.
            line -> {
                var single = ElementType.CROTCHET.newInstance();
                line.addElement(single);
                line.addRangeElement(new Trill(single));

                var multi = addNotes(line, ElementType.CROTCHET, 2);
                var trill = new Trill(multi.get(0), multi.get(1));
                trill.setYPositionSs(TRILL_Y_SS);
                line.addRangeElement(trill);
            },
            // First/second endings around a repeat.
            line -> {
                var anchor = ElementType.REPEAT_LEFT.newInstance();
                var split = ElementType.REPEAT_RIGHT.newInstance();
                var end = ElementType.FINAL_DOUBLE_BARLINE.newInstance();
                line.addElement(anchor);
                line.addElement(ElementType.CROTCHET.newInstance());
                line.addElement(split);
                line.addElement(ElementType.CROTCHET.newInstance());
                line.addElement(end);
                line.addRangeElement(new Ending(anchor, end));
            }
        );
    }

    // -------------------------------------------------------------------------
    // Key + tempo + metric modulation (musicxml.md § Per-measure attributes)
    // -------------------------------------------------------------------------

    private static Song songKeyAndTempo() {
        var baseTempo = new Tempo(BASE_BPM, Duration.CROTCHET, NO_DESCRIPTION, true);
        var perNoteTempo = new Tempo(PER_NOTE_BPM, Duration.MINIM, NO_DESCRIPTION, true);
        var describedTempo = new Tempo(DESCRIBED_BPM, Duration.CROTCHET, "Allegro vivace", true);
        var hiddenTempo = new Tempo(HIDDEN_BPM, Duration.CROTCHET, NO_DESCRIPTION, false);
        var metricModulation = new BeatChange(Duration.CROTCHET_DOTTED, Duration.MINIM);

        var song = buildSong(
            line -> {
                line.setKeyType(Song.DEFAULT_KEY_TYPE);
                line.setKeyAccidentalCount(Song.DEFAULT_KEY_ACCIDENTAL_COUNT);

                var first = ElementType.CROTCHET.newInstance();
                line.addElement(first);
                first.addAttachment(new TempoChangeAttachment(first, baseTempo));

                var perNote = ElementType.CROTCHET.newInstance();
                line.addElement(perNote);
                perNote.addAttachment(new TempoChangeAttachment(perNote, perNoteTempo));
            },
            line -> {
                line.setKeyType(KeyType.SHARPS);
                line.setKeyAccidentalCount(THREE_SHARPS);

                var described = ElementType.CROTCHET.newInstance();
                line.addElement(described);
                described.addAttachment(new TempoChangeAttachment(described, describedTempo));

                var modulated = ElementType.CROTCHET.newInstance();
                line.addElement(modulated);
                modulated.addAttachment(new BeatChangeAttachment(modulated, metricModulation));
            },
            line -> {
                line.setKeyType(KeyType.NONE);
                line.setKeyAccidentalCount(ABSENT_DATE_PART);

                var hidden = ElementType.CROTCHET.newInstance();
                line.addElement(hidden);
                hidden.addAttachment(new TempoChangeAttachment(hidden, hiddenTempo));
            },
            line -> {
                line.setKeyType(KeyType.FLATS);
                line.setKeyAccidentalCount(TWO_FLATS);
                line.addElement(ElementType.CROTCHET.newInstance());
            }
        );

        song.withoutMutationTracking(() -> song.setTempo(baseTempo));
        return song;
    }

    // -------------------------------------------------------------------------
    // Lyrics (musicxml.md § Lyrics)
    // -------------------------------------------------------------------------

    private static Song songLyrics() {
        return buildSong(
            // A hyphenated word split BEGIN / MIDDLE / END, verse 1, plus a verse-2 line.
            line -> {
                var begin = ElementType.CROTCHET.newInstance();
                line.addElement(begin);
                begin.lyrics.add(new Lyric(VERSE_1, "Ky", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
                begin.lyrics.add(new Lyric(VERSE_2, "one", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));

                var middle = ElementType.CROTCHET.newInstance();
                line.addElement(middle);
                middle.lyrics.add(new Lyric(VERSE_1, "ri", Lyric.Extend.NONE, Lyric.Syllabic.MIDDLE, false));
                middle.lyrics.add(new Lyric(VERSE_2, "two", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));

                var end = ElementType.CROTCHET.newInstance();
                line.addElement(end);
                end.lyrics.add(new Lyric(VERSE_1, "e", Lyric.Extend.NONE, Lyric.Syllabic.END, false));
            },
            // A compound-word boundary (compound requires BEGIN/MIDDLE).
            line -> {
                var compound = ElementType.CROTCHET.newInstance();
                line.addElement(compound);
                compound.lyrics.add(new Lyric(VERSE_1, "self", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, true));

                var rest = ElementType.CROTCHET.newInstance();
                line.addElement(rest);
                rest.lyrics.add(new Lyric(VERSE_1, "giving", Lyric.Extend.NONE, Lyric.Syllabic.END, false));
            },
            // A melisma: START carries text, STOP/CONTINUE are text-less carriers.
            line -> {
                var start = ElementType.CROTCHET.newInstance();
                line.addElement(start);
                start.lyrics.add(new Lyric(VERSE_1, "oh", Lyric.Extend.START, Lyric.Syllabic.SINGLE, false));

                var cont = ElementType.CROTCHET.newInstance();
                line.addElement(cont);
                cont.lyrics.add(new Lyric(VERSE_1, "", Lyric.Extend.CONTINUE, null, false));

                var stop = ElementType.CROTCHET.newInstance();
                line.addElement(stop);
                stop.lyrics.add(new Lyric(VERSE_1, "", Lyric.Extend.STOP, null, false));
            }
        );
    }

    // -------------------------------------------------------------------------
    // Header + credits + score-below + layout (musicxml.md §§ Song header, Credits, Layout)
    // -------------------------------------------------------------------------

    private static Song songHeaderAndCredits() {
        var song = buildSong(line -> line.addElement(ElementType.CROTCHET.newInstance()));
        populateHeader(song);
        return song;
    }

    private static void populateHeader(Song song) {
        song.setMetadata(new SongMetadata(
            TITLE, NUMBER, PLACE,
            COMP_YEAR, COMP_MONTH, COMP_DAY,
            COMPOSER, LYRICIST,
            Song.LyricsSource.TEXT,
            true,   // arrangement
            true,   // unofficialTranslation
            SUBTITLE,
            WORDS_YEAR, WORDS_MONTH, ABSENT_DATE_PART
        ));

        song.getAttributionElement().setUserYOffsetSs(ATTRIBUTION_Y_OFF_SS);
        song.setUnderLyrics(UNDERLYRICS);
        song.setBanglaLyrics(BANGLA);
        song.setTranslatedLyrics(TRANSLATION);
        song.setFootnotes(FOOTNOTES);

        song.withoutMutationTracking(() -> {
            song.setLineWidthSs(LINE_WIDTH_SS);
            song.setRowHeightAdjustmentSs(ROW_HEIGHT_ADJ_SS);
        });
    }

    // -------------------------------------------------------------------------
    // Annotations (musicxml.md § Annotations)
    // -------------------------------------------------------------------------

    private static Song songAnnotations() {
        return buildSong(
            line -> {
                var note = ElementType.CROTCHET.newInstance();
                line.addElement(note);
                note.addAttachment(new AnnotationAttachment(note,
                    annotation("dolce", Component.LEFT_ALIGNMENT, Annotation.Placement.ABOVE, ANNOTATION_ABOVE_Y_SS)));
            },
            line -> {
                var note = ElementType.CROTCHET.newInstance();
                line.addElement(note);
                note.addAttachment(new AnnotationAttachment(note,
                    annotation("rit.", Component.CENTER_ALIGNMENT, Annotation.Placement.BELOW, ANNOTATION_BELOW_Y_SS)));
            },
            line -> {
                var note = ElementType.CROTCHET.newInstance();
                line.addElement(note);
                note.addAttachment(new AnnotationAttachment(note,
                    annotation("fine", Component.RIGHT_ALIGNMENT, Annotation.Placement.ABOVE, ANNOTATION_ABOVE_Y_SS)));
            }
        );
    }

    private static Annotation annotation(String text, float alignment, Annotation.Placement placement, double yOffsetSs) {
        var annotation = new Annotation(text);
        annotation.setXAlignment(alignment);
        annotation.setPlacement(placement);
        annotation.setUserYOffsetSs(yOffsetSs);
        return annotation;
    }

    // -------------------------------------------------------------------------
    // Kitchen sink — header + notes + lyrics + tempo + key + annotations + fonts
    // -------------------------------------------------------------------------

    private static Song songKitchenSink() {
        var baseTempo = new Tempo(BASE_BPM, Duration.CROTCHET, NO_DESCRIPTION, true);

        var song = buildSong(
            line -> {
                line.setKeyType(Song.DEFAULT_KEY_TYPE);
                line.setKeyAccidentalCount(Song.DEFAULT_KEY_ACCIDENTAL_COUNT);

                var note = ElementType.CROTCHET.newInstance();
                note.setStaffPosition(C4);
                line.addElement(note);
                note.addAttachment(new TempoChangeAttachment(note, baseTempo));
                note.lyrics.add(new Lyric(VERSE_1, "Full", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));
                note.addAttachment(new AnnotationAttachment(note,
                    annotation("dolce", Component.CENTER_ALIGNMENT, Annotation.Placement.ABOVE, ANNOTATION_ABOVE_Y_SS)));
            },
            line -> {
                line.setKeyType(KeyType.SHARPS);
                line.setKeyAccidentalCount(ONE_SHARP);

                var note = ElementType.CROTCHET.newInstance();
                note.setStaffPosition(F4);
                line.addElement(note);
                note.lyrics.add(new Lyric(VERSE_1, "Song", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));
                note.addAttachment(new AnnotationAttachment(note,
                    annotation("rit.", Component.CENTER_ALIGNMENT, Annotation.Placement.BELOW, ANNOTATION_BELOW_Y_SS)));
                line.addElement(ElementType.FINAL_DOUBLE_BARLINE.newInstance());
            }
        );

        song.withoutMutationTracking(() -> song.setTempo(baseTempo));
        populateHeader(song);
        return song;
    }

    private static DocumentFonts customFonts() {
        var fonts = DocumentFonts.defaultFonts();
        fonts.setFont(FontKey.TITLE, "Corpus Title Family", TITLE_FONT_SIZE);
        fonts.setFont(FontKey.LYRICS, "Corpus Lyric Family", LYRIC_FONT_SIZE);
        fonts.setFont(FontKey.ANNOTATION, "Corpus Word Family", WORD_FONT_SIZE);
        fonts.setFont(FontKey.SUB_ATTRIBUTION, "Corpus Sub-Attribution Family", SUB_ATTRIBUTION_FONT_SIZE);
        return fonts;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Adds {@code count} fresh notes of {@code type} to {@code line}, returning the handles. */
    private static List<StaffElement> addNotes(songscribe.dom.Line line, ElementType type, int count) {
        var notes = new ArrayList<StaffElement>();

        for (int i = 0; i < count; i++) {
            var note = type.newInstance();
            line.addElement(note);
            notes.add(note);
        }

        return notes;
    }
}
