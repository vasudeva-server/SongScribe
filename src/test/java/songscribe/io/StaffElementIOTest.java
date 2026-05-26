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

package songscribe.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import songscribe.UnitTest;
import songscribe.message.MessageCenter;
import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Duration;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.DynamicAttachment.DynamicType;
import songscribe.dom.ElementType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElement.Accidental;
import songscribe.dom.StaffElement.Glissando;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;

@SuppressWarnings({ "SameReturnValue", "OverlyBroadThrowsClause" })
class StaffElementIOTest extends UnitTest {

    private Line line;
    private MockedStatic<MessageCenter> messageCenterMock;

    @BeforeEach
    void setUp() {
        messageCenterMock = mockStatic(MessageCenter.class);
        line = new Song().getLine(0);
    }

    @AfterEach
    void tearDown() {
        messageCenterMock.close();
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AccidentalMapSerialization {

        @Test
        void testDoubleSharpRoundTrips() throws Exception {
            var note = ElementType.CROTCHET.newInstance();
            note.setAccidental(Accidental.DOUBLE_SHARP);
            var output = writeNote(note);
            assertThat(output).contains("<prefix>DOUBLE_SHARP</prefix>");

            var song = parseXml(buildXmlWithAccidental("DOUBLE_SHARP"));
            var roundTripped = song.getLine(0).getElement(0).getAccidental();
            assertThat(roundTripped).isEqualTo(Accidental.DOUBLE_SHARP);
        }

        @Test
        void testDoubleSharpNoUnderscoreAliasRoundTrips() throws Exception {
            var song = parseXml(buildXmlWithAccidental("DOUBLESHARP"));
            var accidental = song.getLine(0).getElement(0).getAccidental();
            assertThat(accidental).isEqualTo(Accidental.DOUBLE_SHARP);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AnnotationSerialization {

        @Test
        void testAnnotationBlockAbsentWithoutAttachment() {
            var note = ElementType.CROTCHET.newInstance();

            var output = writeNote(note);

            assertThat(output).doesNotContain("<annotation>");
        }

        @Test
        void testAnnotationBlockPresentWithAttachment() {
            var note = ElementType.CROTCHET.newInstance();
            note.addAttachment(new AnnotationAttachment(new Annotation("dolce")));

            var output = writeNote(note);

            assertThat(output).contains("<annotation>");
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ArticulationSerialization {

        @Test
        void testAccentEmitsForceArticulationTag() {
            var note = ElementType.CROTCHET.newInstance();
            note.addArticulation(new Articulation(ArticulationType.ACCENT));

            var output = writeNote(note);

            assertThat(output).contains("<forcearticulation>ACCENT</forcearticulation>");
        }

        @Test
        void testStaccatoEmitsDurationArticulationTag() {
            var note = ElementType.CROTCHET.newInstance();
            note.addArticulation(new Articulation(ArticulationType.STACCATO));

            var output = writeNote(note);

            assertThat(output).contains("<durationarticulation>STACCATO</durationarticulation>");
        }

        @Test
        void testVolumeLouderYieldsAccentArticulation() throws Exception {
            var song = parseXml(buildXmlWithNoteContent("<volume>LOUDER</volume>"));
            var note = song.getLine(0).getElement(0);
            var articulations = note.getArticulations();
            assertThat(articulations).hasSize(1);
            assertThat(articulations.get(0).getType()).isEqualTo(ArticulationType.ACCENT);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class BeatChangeSerialization {

        @Test
        void testBeatChangeTagAbsentWithoutAttachment() {
            var note = ElementType.CROTCHET.newInstance();

            var output = writeNote(note);

            assertThat(output).doesNotContain("<beatchange");
        }

        @Test
        void testBeatChangeTagEmitsBothAttributes() {
            var note = ElementType.CROTCHET.newInstance();
            note.addAttachment(new BeatChangeAttachment(
                note,
                new BeatChange(Duration.CROTCHET, Duration.QUAVER)
            ));

            var output = writeNote(note);

            assertThat(output)
                .contains("duration=\"CROTCHET\"")
                .contains("beat=\"QUAVER\"");
        }

        // Row 42: legacy text-content format — each valid legacy name round-trips
        @Test
        void testLegacyQuaverEqualsQuaverCanonicalRoundTrips() throws Exception {
            var song = parseXml(buildXmlWithBeatChange("QUAVER_EQUALS_QUAVER"));
            var attachment = song.getLine(0).getElement(0).findAttachment(BeatChangeAttachment.class);
            assertThat(attachment).isNotNull();
            if (attachment == null) return;
            assertThat(attachment.getBeatChange()).isEqualTo(new BeatChange(Duration.QUAVER, Duration.QUAVER));
        }

        @Test
        void testLegacyQuaverEqualsQuaverNoUnderscoreRoundTrips() throws Exception {
            var song = parseXml(buildXmlWithBeatChange("QUAVEREQUALSQUAVER"));
            var attachment = song.getLine(0).getElement(0).findAttachment(BeatChangeAttachment.class);
            assertThat(attachment).isNotNull();
            if (attachment == null) return;
            assertThat(attachment.getBeatChange()).isEqualTo(new BeatChange(Duration.QUAVER, Duration.QUAVER));
        }

        @Test
        void testLegacyDottedCrotchetEqualsMinimCanonicalRoundTrips() throws Exception {
            var song = parseXml(buildXmlWithBeatChange("DOTTED_CROCHET_EQUALS_MINIM"));
            var attachment = song.getLine(0).getElement(0).findAttachment(BeatChangeAttachment.class);
            assertThat(attachment).isNotNull();
            if (attachment == null) return;
            assertThat(attachment.getBeatChange()).isEqualTo(new BeatChange(Duration.CROTCHET_DOTTED, Duration.MINIM));
        }

        @Test
        void testLegacyDottedCrotchetEqualsMinimNoUnderscoreRoundTrips() throws Exception {
            var song = parseXml(buildXmlWithBeatChange("DOTTEDCROCHETEQUALSMINIM"));
            var attachment = song.getLine(0).getElement(0).findAttachment(BeatChangeAttachment.class);
            assertThat(attachment).isNotNull();
            if (attachment == null) return;
            assertThat(attachment.getBeatChange()).isEqualTo(new BeatChange(Duration.CROTCHET_DOTTED, Duration.MINIM));
        }

        @Test
        void testLegacyMinimEqualsDottedCrotchetCanonicalRoundTrips() throws Exception {
            var song = parseXml(buildXmlWithBeatChange("MINIM_EQUALS_DOTTED_CROCHET"));
            var attachment = song.getLine(0).getElement(0).findAttachment(BeatChangeAttachment.class);
            assertThat(attachment).isNotNull();
            if (attachment == null) return;
            assertThat(attachment.getBeatChange()).isEqualTo(new BeatChange(Duration.MINIM, Duration.CROTCHET_DOTTED));
        }

        @Test
        void testLegacyMinimEqualsDottedCrotchetNoUnderscoreRoundTrips() throws Exception {
            var song = parseXml(buildXmlWithBeatChange("MINIMEQUALSDOTTEDCROCHET"));
            var attachment = song.getLine(0).getElement(0).findAttachment(BeatChangeAttachment.class);
            assertThat(attachment).isNotNull();
            if (attachment == null) return;
            assertThat(attachment.getBeatChange()).isEqualTo(new BeatChange(Duration.MINIM, Duration.CROTCHET_DOTTED));
        }

        @Test
        void testLegacyCrotchetEqualsDottedCrotchetCanonicalRoundTrips() throws Exception {
            var song = parseXml(buildXmlWithBeatChange("CROTCHET_EQUALS_DOTTED_CROCHET"));
            var attachment = song.getLine(0).getElement(0).findAttachment(BeatChangeAttachment.class);
            assertThat(attachment).isNotNull();
            if (attachment == null) return;
            assertThat(attachment.getBeatChange()).isEqualTo(new BeatChange(Duration.CROTCHET, Duration.CROTCHET_DOTTED));
        }

        @Test
        void testLegacyCrotchetEqualsDottedCrotchetNoUnderscoreRoundTrips() throws Exception {
            var song = parseXml(buildXmlWithBeatChange("CROTCHETQUALSDOTTEDCROCHET"));
            var attachment = song.getLine(0).getElement(0).findAttachment(BeatChangeAttachment.class);
            assertThat(attachment).isNotNull();
            if (attachment == null) return;
            assertThat(attachment.getBeatChange()).isEqualTo(new BeatChange(Duration.CROTCHET, Duration.CROTCHET_DOTTED));
        }

        @Test
        void testLegacyDottedCrotchetEqualsCrotchetCanonicalRoundTrips() throws Exception {
            var song = parseXml(buildXmlWithBeatChange("DOTTED_CROCHET_EQUALS_CROCHET"));
            var attachment = song.getLine(0).getElement(0).findAttachment(BeatChangeAttachment.class);
            assertThat(attachment).isNotNull();
            if (attachment == null) return;
            assertThat(attachment.getBeatChange()).isEqualTo(new BeatChange(Duration.CROTCHET_DOTTED, Duration.CROTCHET));
        }

        @Test
        void testLegacyDottedCrotchetEqualsCrotchetNoUnderscoreRoundTrips() throws Exception {
            var song = parseXml(buildXmlWithBeatChange("DOTTEDCROCHETQUALSCROCHET"));
            var attachment = song.getLine(0).getElement(0).findAttachment(BeatChangeAttachment.class);
            assertThat(attachment).isNotNull();
            if (attachment == null) return;
            assertThat(attachment.getBeatChange()).isEqualTo(new BeatChange(Duration.CROTCHET_DOTTED, Duration.CROTCHET));
        }

        // Row 43: new 2-attribute format (v2.5+) → BeatChangeAttachment created at startElement11
        @Test
        void testNewFormatTwoAttributesYieldsBeatChangeAttachment() throws Exception {
            var song = parseXml(buildXmlWithBeatChangeAttributes("CROTCHET", "QUAVER"));
            var attachment = song.getLine(0).getElement(0).findAttachment(BeatChangeAttachment.class);
            assertThat(attachment).isNotNull();
            if (attachment == null) return;
            assertThat(attachment.getBeatChange()).isEqualTo(new BeatChange(Duration.CROTCHET, Duration.QUAVER));
        }

        @Test
        void testNewFormatDottedDurationYieldsBeatChangeAttachment() throws Exception {
            var song = parseXml(buildXmlWithBeatChangeAttributes("CROTCHET_DOTTED", "MINIM"));
            var attachment = song.getLine(0).getElement(0).findAttachment(BeatChangeAttachment.class);
            assertThat(attachment).isNotNull();
            if (attachment == null) return;
            assertThat(attachment.getBeatChange()).isEqualTo(new BeatChange(Duration.CROTCHET_DOTTED, Duration.MINIM));
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DotCountSerialization {

        @Test
        void testDottedAbsentWhenZeroDots() {
            var note = ElementType.CROTCHET.newInstance();
            note.setDotCount(0);

            var output = writeNote(note);

            assertThat(output).doesNotContain("<dotted>");
        }

        @Test
        void testDottedPresentForOneDot() {
            var note = ElementType.CROTCHET.newInstance();
            note.setDotCount(1);

            var output = writeNote(note);

            assertThat(output).contains("<dotted>1</dotted>");
        }

        @Test
        void testDottedPresentForTwoDots() {
            var note = ElementType.CROTCHET.newInstance();
            note.setDotCount(2);

            var output = writeNote(note);

            assertThat(output).contains("<dotted>2</dotted>");
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DynamicSerialization {

        // T30: Write note with DynamicAttachment → XML contains <dynamic type="..."/>
        @Test
        void testWritesDynamicElement() {
            var note = ElementType.CROTCHET.newInstance();
            note.addAttachment(new DynamicAttachment(note, DynamicType.FORTE));

            var output = writeNote(note);

            assertThat(output).contains("<dynamic type=\"FORTE\" />");
        }

        // T31: Read <dynamic type="FORTE"/> → note has DynamicAttachment(FORTE)
        @Test
        void testReadsDynamicElement() throws Exception {
            var song = parseXml(buildXmlWithDynamic("FORTE"));
            var dynamic = song.getLine(0).getElement(0).findAttachment(DynamicAttachment.class);

            assertThat(dynamic).isNotNull();

            //noinspection ConstantValue -- need for NullAway
            if (dynamic == null) {
                return;
            }

            assertThat(dynamic.getType()).isEqualTo(DynamicType.FORTE);
        }

        // T32: Round-trip preserves dynamic type for all UI types, including SFORZANDO
        @ParameterizedTest
        @EnumSource(value = DynamicType.class, names = {"PIANISSIMO", "PIANO", "MEZZO_PIANO", "MEZZO_FORTE", "FORTE", "FORTISSIMO", "SFORZANDO", "FORTEPIANO"})
        void testRoundTripPreservesDynamicType(DynamicType dynamicType) throws Exception {
            var comp1 = parseXml(buildXmlWithDynamic(dynamicType.name()));
            var comp2 = roundTrip(comp1);
            var dynamic = comp2.getLine(0).getElement(0).findAttachment(DynamicAttachment.class);

            assertThat(dynamic).isNotNull();

            //noinspection ConstantValue -- need for NullAway
            if (dynamic == null) {
                return;
            }

            assertThat(dynamic.getType()).isEqualTo(dynamicType);
        }

        // T33: Read file without dynamics → no DynamicAttachment
        @Test
        void testNoDynamicWhenElementAbsent() throws Exception {
            var song = parseXml(buildXmlWithoutDynamic());
            var dynamic = song.getLine(0).getElement(0).findAttachment(DynamicAttachment.class);

            assertThat(dynamic).isNull();
        }

        // T34: Read <dynamic type="UNKNOWN"/> → logged warning, attachment skipped
        @Test
        void testUnknownDynamicTypeIsSkipped() {
            var xml = buildXmlWithDynamic("UNKNOWN_DYNAMIC");

            assertThatCode(() -> parseXml(xml)).doesNotThrowAnyException();
        }

        @Test
        void testUnknownDynamicTypeProducesNoAttachment() throws Exception {
            var song = parseXml(buildXmlWithDynamic("UNKNOWN_DYNAMIC"));
            var dynamic = song.getLine(0).getElement(0).findAttachment(DynamicAttachment.class);

            assertThat(dynamic).isNull();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ExtendTypeSerialization {

        @Test
        void testExtendTypeAttrNoneThrowsIllegalArgumentException() {
            assertThatThrownBy(() -> StaffElementIO.extendTypeAttr(Lyric.Extend.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void testParseExtendTypeContinueReturnsContinue() throws Exception {
            var song = parseXml(buildXmlWithExtend("continue"));
            var lyric = song.getLine(0).getElement(0).lyrics.get(0);
            assertThat(lyric.extend()).isEqualTo(Lyric.Extend.CONTINUE);
        }

        @Test
        void testParseExtendTypeUnknownReturnsStart() throws Exception {
            var song = parseXml(buildXmlWithExtend("bogus"));
            var lyric = song.getLine(0).getElement(0).lyrics.get(0);
            assertThat(lyric.extend()).isEqualTo(Lyric.Extend.START);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class FermataSerialization {

        @Test
        void testFermataTagAbsentWithoutAttachment() {
            var note = ElementType.CROTCHET.newInstance();

            var output = writeNote(note);

            assertThat(output).doesNotContain("<fermata");
        }

        @Test
        void testFermataTagPresentWithAttachment() {
            var note = ElementType.CROTCHET.newInstance();
            note.addAttachment(new FermataAttachment(note));

            var output = writeNote(note);

            assertThat(output).contains("<fermata");
        }

        @Test
        void testFermataTagYieldsFermataAttachment() throws Exception {
            var song = parseXml(buildXmlWithNoteContent("<fermata/>"));
            var attachment = song.getLine(0).getElement(0).findAttachment(FermataAttachment.class);
            assertThat(attachment).isNotNull();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GlissandoSerialization {

        @Test
        void testGlissandoTypeIsEmitted() {
            var note = ElementType.CROTCHET.newInstance();
            note.setGlissando(Glissando.Type.CONNECTED);

            var output = writeNote(note);

            assertThat(output).contains("<glissando>CONNECTED</glissando>");
        }

        @Test
        void testX1TranslateAbsentWhenZero() {
            var note = ElementType.CROTCHET.newInstance();
            note.setGlissando(Glissando.Type.CONNECTED);

            var output = writeNote(note);

            assertThat(output).doesNotContain("<glissandox1translate>");
        }

        @Test
        void testX1TranslatePresentWhenNonZero() {
            var note = ElementType.CROTCHET.newInstance();
            note.setGlissando(Glissando.Type.CONNECTED);
            var glissando = note.getGlissando();
            assertThat(glissando).isNotNull();
            if (glissando != null) {
                glissando.x1Translate = 5.0;
            }

            var output = writeNote(note);

            assertThat(output).contains("<glissandox1translate>5.0</glissandox1translate>");
        }

        @Test
        void testX2TranslateAbsentWhenZero() {
            var note = ElementType.CROTCHET.newInstance();
            note.setGlissando(Glissando.Type.CONNECTED);

            var output = writeNote(note);

            assertThat(output).doesNotContain("<glissandox2translate>");
        }

        @Test
        void testX2TranslatePresentWhenNonZero() {
            var note = ElementType.CROTCHET.newInstance();
            note.setGlissando(Glissando.Type.CONNECTED);
            var glissando = note.getGlissando();
            assertThat(glissando).isNotNull();
            if (glissando != null) {
                glissando.x2Translate = 3.5;
            }

            var output = writeNote(note);

            assertThat(output).contains("<glissandox2translate>3.5</glissandox2translate>");
        }

        @Test
        void testLegacyNumericGlissandoYieldsConnected() throws Exception {
            var song = parseXml(buildXmlWithNoteContent("<glissando>5</glissando>"));
            var note = song.getLine(0).getElement(0);
            var glissando = note.getGlissando();
            assertThat(glissando).isNotNull();
            if (glissando != null) {
                assertThat(glissando.type).isEqualTo(Glissando.Type.CONNECTED);
            }
        }

        @Test
        void testX1TranslateSurvivesWhenGlissandoPresent() throws Exception {
            var song = parseXml(buildXmlWithNoteContent(
                "<glissando>CONNECTED</glissando><glissandox1translate>7.5</glissandox1translate>"
            ));
            var glissando = song.getLine(0).getElement(0).getGlissando();
            assertThat(glissando).isNotNull();
            if (glissando != null) {
                assertThat(glissando.x1Translate).isNotZero();
            }
        }

        @Test
        void testX1TranslateWithoutGlissandoDoesNotCrash() {
            assertThatCode(() -> parseXml(buildXmlWithNoteContent(
                "<glissandox1translate>7.5</glissandox1translate>"
            ))).doesNotThrowAnyException();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InvalidMapLookups {

        @Test
        void testUnknownAccidentalThrowsMeaningfulError() {
            var xml = buildXmlWithAccidental("BOGUS_ACCIDENTAL");

            assertThatThrownBy(() -> parseXml(xml))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("Unknown accidental: BOGUS_ACCIDENTAL")
                .cause()
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void testUnknownBeatChangeThrowsMeaningfulError() {
            var xml = buildXmlWithBeatChange("BOGUS_BEAT_CHANGE");

            assertThatThrownBy(() -> parseXml(xml))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("unknown legacy beat change: BOGUS_BEAT_CHANGE")
                .cause()
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InvertFractionBeamOrientationSerialization {

        // Row 41: <invertfractionbeamorientation> silently ignored — no throw, no side-effect
        @Test
        void testInvertFractionBeamOrientationDoesNotThrow() {
            assertThatCode(() -> parseXml(buildXmlWithNoteContent("<invertfractionbeamorientation/>")))
                .doesNotThrowAnyException();
        }

        @Test
        void testInvertFractionBeamOrientationProducesNoFermataAttachment() throws Exception {
            // Verify the tag is truly ignored: it should not add a FermataAttachment
            // (or any other recognisable attachment type that could be confused with it).
            var song = parseXml(buildXmlWithNoteContent("<invertfractionbeamorientation/>"));
            var note = song.getLine(0).getElement(0);
            assertThat(note.findAttachment(FermataAttachment.class)).isNull();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LyricSyllabicSerialization {

        @Test
        void testSyllabicSingleEmitsSingle() {
            var note = ElementType.CROTCHET.newInstance();
            note.lyrics.add(new Lyric(1, "la", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));

            var output = writeNote(note);

            assertThat(output).contains("<syllabic>single</syllabic>");
        }

        @Test
        void testSyllabicBeginEmitsBegin() {
            var note = ElementType.CROTCHET.newInstance();
            note.lyrics.add(new Lyric(1, "la", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));

            var output = writeNote(note);

            assertThat(output).contains("<syllabic>begin</syllabic>");
        }

        @Test
        void testSyllabicMiddleEmitsMiddle() {
            var note = ElementType.CROTCHET.newInstance();
            note.lyrics.add(new Lyric(1, "la", Lyric.Extend.NONE, Lyric.Syllabic.MIDDLE, false));

            var output = writeNote(note);

            assertThat(output).contains("<syllabic>middle</syllabic>");
        }

        @Test
        void testSyllabicEndEmitsEnd() {
            var note = ElementType.CROTCHET.newInstance();
            note.lyrics.add(new Lyric(1, "la", Lyric.Extend.NONE, Lyric.Syllabic.END, false));

            var output = writeNote(note);

            assertThat(output).contains("<syllabic>end</syllabic>");
        }

        // Row 46: middle syllabic parses to MIDDLE before backfill
        @Test
        void testSyllabicMiddleParsesToMiddle() {
            var attrs = new AttributesImpl();
            attrs.addAttribute("", "type", "type", "CDATA", "CROTCHET");
            var reader = new StaffElementIO.StaffElementReader();
            reader.startElement11("note", attrs);

            var lyricAttrs = new AttributesImpl();
            lyricAttrs.addAttribute("", "number", "number", "CDATA", "1");
            reader.startElement11("lyric", lyricAttrs);
            reader.startElement11("syllabic", new AttributesImpl());
            reader.characters(new char[]{'m', 'i', 'd', 'd', 'l', 'e'}, 0, "middle".length());
            reader.endElement11("syllabic");
            reader.startElement11("text", new AttributesImpl());
            reader.characters(new char[]{'l', 'a'}, 0, 2);
            reader.endElement11("text");
            var element = reader.endElement11("lyric");
            assertThat(element).isNull(); // not yet closed <note>
            var note = reader.endElement11("note");

            assertThat(note).isNotNull();
            if (note == null) return;
            assertThat(note.lyrics).hasSize(1);
            assertThat(note.lyrics.get(0).syllabic()).isEqualTo(Lyric.Syllabic.MIDDLE);
        }

        // Row 46: absent <syllabic> defaults to SINGLE (empty lyricSyllabic hits the default branch)
        @Test
        void testAbsentSyllabicDefaultsToSingle() {
            var attrs = new AttributesImpl();
            attrs.addAttribute("", "type", "type", "CDATA", "CROTCHET");
            var reader = new StaffElementIO.StaffElementReader();
            reader.startElement11("note", attrs);

            var lyricAttrs = new AttributesImpl();
            lyricAttrs.addAttribute("", "number", "number", "CDATA", "1");
            reader.startElement11("lyric", lyricAttrs);
            // No <syllabic> child — lyricSyllabic stays ""
            reader.startElement11("text", new AttributesImpl());
            reader.characters(new char[]{'l', 'a'}, 0, 2);
            reader.endElement11("text");
            reader.endElement11("lyric");
            var note = reader.endElement11("note");

            assertThat(note).isNotNull();
            if (note == null) return;
            assertThat(note.lyrics).hasSize(1);
            assertThat(note.lyrics.get(0).syllabic()).isEqualTo(Lyric.Syllabic.SINGLE);
        }

    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LyricVerseSerialization {

        private static final int VERSE_ONE = 1;
        private static final int VERSE_TWO = 2;

        @Test
        void testVerseOneEmitsNumberAttributeOne() {
            var note = ElementType.CROTCHET.newInstance();
            note.lyrics.add(new Lyric(VERSE_ONE, "la", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));

            var output = writeNote(note);

            assertThat(output).contains("number=\"" + VERSE_ONE + "\"");
        }

        @Test
        void testVerseTwoEmitsNumberAttributeTwo() {
            var note = ElementType.CROTCHET.newInstance();
            note.lyrics.add(new Lyric(VERSE_ONE, "la", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));
            note.lyrics.add(new Lyric(VERSE_TWO, "la", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));

            var output = writeNote(note);

            assertThat(output).contains("number=\"" + VERSE_TWO + "\"");
        }

        @Test
        void testVerseTwoRoundTrips() throws Exception {
            var song = parseXml(buildXmlWithLyrics(VERSE_ONE, "la", VERSE_TWO, "mi"));
            var parsed = song.getLine(0).getElement(0);
            assertThat(parsed.lyrics).hasSize(VERSE_TWO);
            assertThat(parsed.lyrics.get(VERSE_TWO - 1).verse()).isEqualTo(VERSE_TWO);
            assertThat(parsed.lyrics.get(VERSE_TWO - 1).text()).isEqualTo("mi");
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class NewlineNullGuard {

        private static AttributesImpl attrs(String type) {
            var a = new AttributesImpl();
            a.addAttribute("", "type", "type", "CDATA", type);
            return a;
        }

        // Row 47: NEWLINE sets where=null; subsequent elements still parse without NPE
        @Test
        void testElementAfterNewlineDoesNotThrow() {
            var reader = new StaffElementIO.StaffElementReader();
            reader.startElement10("note", attrs("NEWLINE"));
            // where is now null; subsequent child tags must not NPE
            assertThatCode(() -> {
                reader.startElement10("staffposition", new AttributesImpl());
                reader.characters(new char[]{'0'}, 0, 1);
                reader.endElement10("staffposition");
            }).doesNotThrowAnyException();
        }

        @Test
        void testEndElementAfterNewlineReturnsNull() {
            var reader = new StaffElementIO.StaffElementReader();
            reader.startElement10("note", attrs("NEWLINE"));
            // endElement10 on the NEWLINE note tag returns null (no element created)
            var result = reader.endElement10("note");
            assertThat(result).isNull();
        }

        @Test
        void testCharactersAfterNewlineDoesNotThrow() {
            var reader = new StaffElementIO.StaffElementReader();
            reader.startElement10("note", attrs("NEWLINE"));
            assertThatCode(() -> reader.characters(new char[]{'x'}, 0, 1))
                .doesNotThrowAnyException();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PrefixInParenthesisSerialization {

        @Test
        void testPrefixInParenthesisAbsentWhenFalse() {
            var note = ElementType.CROTCHET.newInstance();
            note.setAccidental(Accidental.SHARP);
            note.setAccidentalInParentheses(false);

            var output = writeNote(note);

            assertThat(output).doesNotContain("<prefixinparenthesis");
        }

        @Test
        void testPrefixInParenthesisPresentWhenTrue() {
            var note = ElementType.CROTCHET.newInstance();
            note.setAccidental(Accidental.SHARP);
            note.setAccidentalInParentheses(true);

            var output = writeNote(note);

            assertThat(output).contains("<prefixinparenthesis");
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PrefixSerialization {

        @Test
        void testPrefixAbsentWhenAccidentalNull() {
            var note = ElementType.CROTCHET.newInstance();
            note.setAccidental(null);

            var output = writeNote(note);

            assertThat(output).doesNotContain("<prefix>");
        }

        @Test
        void testPrefixContainsAccidentalName() {
            var note = ElementType.CROTCHET.newInstance();
            note.setAccidental(Accidental.SHARP);

            var output = writeNote(note);

            assertThat(output).contains("<prefix>SHARP</prefix>");
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StaffPositionSerialization {

        @Test
        void testLegacyYposTagYieldsStaffPosition() throws Exception {
            var expectedStaffPosition = 3;
            var song = parseXml(buildXmlWithNoteContent("<ypos>" + expectedStaffPosition + "</ypos>"));
            var note = song.getLine(0).getElement(0);
            assertThat(note.getStaffPosition()).isEqualTo(expectedStaffPosition);
        }

        @Test
        void testStaffPositionAlwaysEmitted() {
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(4);

            var output = writeNote(note);

            assertThat(output).contains("<staffposition>4</staffposition>");
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StemDirectionAutoSerialization {

        @Test
        void testStemDirectionAutoTagAbsentWhenAutoTrue() {
            var note = ElementType.CROTCHET.newInstance();
            note.setStemDirectionAuto(true);

            var output = writeNote(note);

            assertThat(output).doesNotContain("<stemDirectionAuto");
        }

        @Test
        void testStemDirectionAutoTagPresentWhenAutoFalse() {
            var note = ElementType.CROTCHET.newInstance();
            note.setStemDirectionAuto(false);

            var output = writeNote(note);

            assertThat(output).contains("<stemDirectionAuto");
        }

        @Test
        void testStemDirectionAutoTagYieldsFalseAutoFlag() throws Exception {
            var song = parseXml(buildXmlWithNoteContent("<stemDirectionAuto/>"));
            var note = song.getLine(0).getElement(0);
            assertThat(note.isStemDirectionAuto()).isFalse();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TempoSerialization {

        @Test
        void testTempoBlockAbsentWithoutAttachment() {
            var note = ElementType.CROTCHET.newInstance();

            var output = writeNote(note);

            assertThat(output).doesNotContain("<tempo>");
        }

        @Test
        void testTempoBlockPresentWithAttachment() {
            var note = ElementType.CROTCHET.newInstance();
            note.addAttachment(new TempoChangeAttachment(note, new Tempo()));

            var output = writeNote(note);

            assertThat(output).contains("<tempo>");
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TrillSerialization {

        private static AttributesImpl noteAttrs() {
            var attrs = new AttributesImpl();
            attrs.addAttribute("", "type", "type", "CDATA", "CROTCHET");
            return attrs;
        }

        @Test
        void testTrillTagSetsTrillFlaggedTrue() {
            var reader = new StaffElementIO.StaffElementReader();
            reader.startElement11("note", noteAttrs());
            reader.startElement11("trill", new AttributesImpl());
            reader.endElement11("trill");
            var element = reader.endElement11("note");
            assertThat(element).isNotNull();
            assertThat(reader.isTrillFlagged()).isTrue();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TypeAlias10Serialization {

        private static AttributesImpl noteAttrs(String type) {
            var attrs = new AttributesImpl();
            attrs.addAttribute("", "type", "type", "CDATA", type);
            return attrs;
        }

        @Test
        void testNewlineTypeIgnoredAndReturnsTrue() {
            var reader = new StaffElementIO.StaffElementReader();
            var ignored = reader.startElement10("note", noteAttrs("NEWLINE"));
            assertThat(ignored).isTrue();
        }

        @Test
        void testLineTypeYieldsSingleBarline() {
            var reader = new StaffElementIO.StaffElementReader();
            reader.startElement10("note", noteAttrs("LINE"));
            var element = reader.endElement10("note");
            assertThat(element).isNotNull();
            if (element != null) {
                assertThat(element.getType()).isEqualTo(ElementType.SINGLE_BARLINE);
            }
        }

        @Test
        void testGraceSemiquaverTypeYieldsGraceQuaver() {
            var reader = new StaffElementIO.StaffElementReader();
            reader.startElement10("note", noteAttrs("GRACESEMIQUAVER"));
            var element = reader.endElement10("note");
            assertThat(element).isNotNull();
            if (element != null) {
                assertThat(element.getType()).isEqualTo(ElementType.GRACE_QUAVER);
            }
        }

        @Test
        void testGraceSemiquaverWithUnderscoreYieldsGraceQuaver() {
            var reader = new StaffElementIO.StaffElementReader();
            reader.startElement10("note", noteAttrs("GRACE_SEMIQUAVER"));
            var element = reader.endElement10("note");
            assertThat(element).isNotNull();
            if (element != null) {
                assertThat(element.getType()).isEqualTo(ElementType.GRACE_QUAVER);
            }
        }

        @Test
        void testGraceSemiquaverEditStep1YieldsGraceQuaver() {
            var reader = new StaffElementIO.StaffElementReader();
            reader.startElement10("note", noteAttrs("GRACE_SEMIQUAVER_EDIT_STEP1"));
            var element = reader.endElement10("note");
            assertThat(element).isNotNull();
            if (element != null) {
                assertThat(element.getType()).isEqualTo(ElementType.GRACE_QUAVER);
            }
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TypeAlias11Serialization {

        private static AttributesImpl noteAttrs(String type) {
            var attrs = new AttributesImpl();
            attrs.addAttribute("", "type", "type", "CDATA", type);
            return attrs;
        }

        @Test
        void testVerticalLineTypeYieldsSingleBarline() {
            var reader = new StaffElementIO.StaffElementReader();
            reader.startElement11("note", noteAttrs("VERTICALLINE"));
            var element = reader.endElement11("note");
            assertThat(element).isNotNull();
            if (element != null) {
                assertThat(element.getType()).isEqualTo(ElementType.SINGLE_BARLINE);
            }
        }

        @Test
        void testGraceSemiquaverWithUnderscoreYieldsGraceQuaver() {
            var reader = new StaffElementIO.StaffElementReader();
            reader.startElement11("note", noteAttrs("GRACE_SEMIQUAVER"));
            var element = reader.endElement11("note");
            assertThat(element).isNotNull();
            if (element != null) {
                assertThat(element.getType()).isEqualTo(ElementType.GRACE_QUAVER);
            }
        }

        @Test
        void testGraceSemiquaverEditStep1YieldsGraceQuaver() {
            var reader = new StaffElementIO.StaffElementReader();
            reader.startElement11("note", noteAttrs("GRACE_SEMIQUAVER_EDIT_STEP1"));
            var element = reader.endElement11("note");
            assertThat(element).isNotNull();
            if (element != null) {
                assertThat(element.getType()).isEqualTo(ElementType.GRACE_QUAVER);
            }
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class UpperSerialization {

        @Test
        void testUpperTagAbsentWhenFalse() {
            var note = ElementType.CROTCHET.newInstance();
            note.setUpper(false);

            var output = writeNote(note);

            assertThat(output).doesNotContain("<upper");
        }

        @Test
        void testUpperTagPresentWhenTrue() {
            var note = ElementType.CROTCHET.newInstance();
            note.setUpper(true);

            var output = writeNote(note);

            assertThat(output).contains("<upper");
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class XPosSerialization {

        @Test
        void testXPosAbsentWhenZero() {
            var note = ElementType.CROTCHET.newInstance();
            note.setXOffsetPx(0);

            var output = writeNote(note);

            assertThat(output).doesNotContain("<xpos>");
        }

        @Test
        void testXPosPresentWhenNonZero() {
            var note = ElementType.CROTCHET.newInstance();
            note.setXOffsetPx(10);

            var output = writeNote(note);

            assertThat(output).contains("<xpos>10</xpos>");
        }
    }

    // -- Helpers --

    private static String buildXmlWithNoteContent(String innerXml) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="1.1">
              <keys>0</keys>
              <keytype>SHARPS</keytype>
              <tempo>
                <visibletempo>120</visibletempo>
                <tempotype>CROTCHET</tempotype>
                <tempodescription></tempodescription>
                <showtempo>true</showtempo>
              </tempo>
              <songtitle>Test</songtitle>
              <lines>
                <line>
                  <keyCount>0</keyCount>
                  <keyType>SHARPS</keyType>
                  <tempoChangeYPos>-28</tempoChangeYPos>
                  <notes>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                      %s
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """.formatted(innerXml);
    }

    private String writeNote(StaffElement note) {
        var sw = new StringWriter();
        StaffElementIO.writeElement(note, new PrintWriter(sw), line, 0);
        return sw.toString();
    }

    private static String buildXmlWithDynamic(String dynamicType) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="1.1">
              <keys>0</keys>
              <keytype>SHARPS</keytype>
              <tempo>
                <visibletempo>120</visibletempo>
                <tempotype>CROTCHET</tempotype>
                <tempodescription></tempodescription>
                <showtempo>true</showtempo>
              </tempo>
              <songtitle>Test</songtitle>
              <lines>
                <line>
                  <keyCount>0</keyCount>
                  <keyType>SHARPS</keyType>
                  <tempoChangeYPos>-28</tempoChangeYPos>
                  <notes>
                    <note type="CROTCHET">
                      <yPos>0</yPos>
                      <xPos>80</xPos>
                      <dynamic type="%s" />
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """.formatted(dynamicType);
    }

    private static String buildXmlWithoutDynamic() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="1.1">
              <keys>0</keys>
              <keytype>SHARPS</keytype>
              <tempo>
                <visibletempo>120</visibletempo>
                <tempotype>CROTCHET</tempotype>
                <tempodescription></tempodescription>
                <showtempo>true</showtempo>
              </tempo>
              <songtitle>Test</songtitle>
              <lines>
                <line>
                  <keyCount>0</keyCount>
                  <keyType>SHARPS</keyType>
                  <tempoChangeYPos>-28</tempoChangeYPos>
                  <notes>
                    <note type="CROTCHET">
                      <yPos>0</yPos>
                      <xPos>80</xPos>
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """;
    }

    private static String buildXmlWithAccidental(String accidentalValue) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="1.1">
              <keys>0</keys>
              <keytype>SHARPS</keytype>
              <tempo>
                <visibletempo>120</visibletempo>
                <tempotype>CROTCHET</tempotype>
                <tempodescription></tempodescription>
                <showtempo>true</showtempo>
              </tempo>
              <songtitle>Test</songtitle>
              <lines>
                <line>
                  <keyCount>0</keyCount>
                  <keyType>SHARPS</keyType>
                  <tempoChangeYPos>-28</tempoChangeYPos>
                  <notes>
                    <note type="CROTCHET">
                      <yPos>0</yPos>
                      <xPos>80</xPos>
                      <prefix>%s</prefix>
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """.formatted(accidentalValue);
    }

    private static String buildXmlWithBeatChange(String beatChangeValue) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="1.1">
              <keys>0</keys>
              <keytype>SHARPS</keytype>
              <tempo>
                <visibletempo>120</visibletempo>
                <tempotype>CROTCHET</tempotype>
                <tempodescription></tempodescription>
                <showtempo>true</showtempo>
              </tempo>
              <songtitle>Test</songtitle>
              <lines>
                <line>
                  <keyCount>0</keyCount>
                  <keyType>SHARPS</keyType>
                  <tempoChangeYPos>-28</tempoChangeYPos>
                  <notes>
                    <note type="CROTCHET">
                      <yPos>0</yPos>
                      <xPos>80</xPos>
                      <beatchange>%s</beatchange>
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """.formatted(beatChangeValue);
    }

    private static String buildXmlWithBeatChangeAttributes(String duration, String beat) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="1.1">
              <keys>0</keys>
              <keytype>SHARPS</keytype>
              <tempo>
                <visibletempo>120</visibletempo>
                <tempotype>CROTCHET</tempotype>
                <tempodescription></tempodescription>
                <showtempo>true</showtempo>
              </tempo>
              <songtitle>Test</songtitle>
              <lines>
                <line>
                  <keyCount>0</keyCount>
                  <keyType>SHARPS</keyType>
                  <tempoChangeYPos>-28</tempoChangeYPos>
                  <notes>
                    <note type="CROTCHET">
                      <yPos>0</yPos>
                      <xPos>80</xPos>
                      <beatchange duration="%s" beat="%s" />
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """.formatted(duration, beat);
    }

    private static String buildXmlWithSyllabic(String syllabicValue) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="1.1">
              <keys>0</keys>
              <keytype>SHARPS</keytype>
              <tempo>
                <visibletempo>120</visibletempo>
                <tempotype>CROTCHET</tempotype>
                <tempodescription></tempodescription>
                <showtempo>true</showtempo>
              </tempo>
              <songtitle>Test</songtitle>
              <lines>
                <line>
                  <keyCount>0</keyCount>
                  <keyType>SHARPS</keyType>
                  <tempoChangeYPos>-28</tempoChangeYPos>
                  <notes>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                      <lyric number="1">
                        <syllabic>%s</syllabic>
                        <text>la</text>
                      </lyric>
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """.formatted(syllabicValue);
    }

    private static String buildXmlWithNoSyllabic() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="1.1">
              <keys>0</keys>
              <keytype>SHARPS</keytype>
              <tempo>
                <visibletempo>120</visibletempo>
                <tempotype>CROTCHET</tempotype>
                <tempodescription></tempodescription>
                <showtempo>true</showtempo>
              </tempo>
              <songtitle>Test</songtitle>
              <lines>
                <line>
                  <keyCount>0</keyCount>
                  <keyType>SHARPS</keyType>
                  <tempoChangeYPos>-28</tempoChangeYPos>
                  <notes>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                      <lyric number="1">
                        <text>la</text>
                      </lyric>
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """;
    }

    private static String buildXmlWithExtend(String extendType) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="1.1">
              <keys>0</keys>
              <keytype>SHARPS</keytype>
              <tempo>
                <visibletempo>120</visibletempo>
                <tempotype>CROTCHET</tempotype>
                <tempodescription></tempodescription>
                <showtempo>true</showtempo>
              </tempo>
              <songtitle>Test</songtitle>
              <lines>
                <line>
                  <keyCount>0</keyCount>
                  <keyType>SHARPS</keyType>
                  <tempoChangeYPos>-28</tempoChangeYPos>
                  <notes>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                      <lyric number="1">
                        <extend type="%s"/>
                      </lyric>
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """.formatted(extendType);
    }

    private static String buildXmlWithLyrics(int verse1, String text1, int verse2, String text2) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="1.1">
              <keys>0</keys>
              <keytype>SHARPS</keytype>
              <tempo>
                <visibletempo>120</visibletempo>
                <tempotype>CROTCHET</tempotype>
                <tempodescription></tempodescription>
                <showtempo>true</showtempo>
              </tempo>
              <songtitle>Test</songtitle>
              <lines>
                <line>
                  <keyCount>0</keyCount>
                  <keyType>SHARPS</keyType>
                  <tempoChangeYPos>-28</tempoChangeYPos>
                  <notes>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                      <lyric number="%d">
                        <syllabic>single</syllabic>
                        <text>%s</text>
                      </lyric>
                      <lyric number="%d">
                        <syllabic>single</syllabic>
                        <text>%s</text>
                      </lyric>
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """.formatted(verse1, text1, verse2, text2);
    }
}
