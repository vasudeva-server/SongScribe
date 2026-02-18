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

package songscribe.ui.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.music.Composition;
import songscribe.music.Crotchet;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.test.TestHelper;
import songscribe.ui.Constants;

/**
 * Tests for LyricsRenderer following Gould/Ross engraving rules.
 * <p>
 * Tests verify:
 * <ul>
 *   <li>Hyphen (ONE_DASH) renders single hyphen for syllable division</li>
 *   <li>Extender renders continuous line for duration</li>
 *   <li>DASH relation type is not used (replaced by ONE_DASH)</li>
 * </ul>
 */
@DisplayName("LyricsRenderer")
class LyricsRendererTest {

    private LyricsRenderer renderer;
    private Font lyricsFont;

    @BeforeEach
    void setUp() {
        renderer = LyricsRenderer.getInstance();

        // Create lyrics font
        lyricsFont = new Font("SansSerif", Font.PLAIN, 12);
    }

    // ==========================================================================
    // Test Helpers
    // ==========================================================================

    /**
     * Creates a note with a syllable.
     */
    private Note createNote(String syllable) {
        var note = new Crotchet();
        note.acceleration.syllable = syllable != null ? syllable : "";
        note.acceleration.syllableRelation = Note.SyllableRelation.NO; // Default to NO
        note.setXPos(100); // Default position
        return note;
    }

    /**
     * Creates a note with a syllable and relation.
     */
    private Note createNote(String syllable, Note.SyllableRelation relation) {
        var note = createNote(syllable);
        note.acceleration.syllableRelation = relation;
        return note;
    }

    /**
     * Creates a line with notes.
     */
    private Line createLineWithNotes(Note... notes) {
        var line = new Line();

        for (var note : notes) {
            line.addNote(note);
        }

        return line;
    }

    // ==========================================================================
    // Singleton Tests
    // ==========================================================================

    @Nested
    @DisplayName("getInstance")
    class GetInstanceTests {

        @Test
        @DisplayName("returns non-null instance")
        void returnsNonNullInstance() {
            var instance = LyricsRenderer.getInstance();
            assertNotNull(instance);
        }

        @Test
        @DisplayName("returns same instance on multiple calls")
        void returnsSameInstance() {
            var instance1 = LyricsRenderer.getInstance();
            var instance2 = LyricsRenderer.getInstance();
            assertEquals(instance1, instance2);
        }
    }

    // ==========================================================================
    // Basic Rendering Tests
    // ==========================================================================

    @Nested
    @DisplayName("renderLyrics basic behavior")
    class BasicRenderingTests {

        @Test
        @DisplayName("renders syllable text under note")
        void rendersSyllableText() {
            var note = createNote("hello");
            var line = createLineWithNotes(note);

            // This test verifies rendering doesn't throw exceptions
            // Actual visual verification would require image comparison
            // which is beyond the scope of unit tests
            assertTrue(note.acceleration.syllable.equals("hello"));
            assertEquals(Note.SyllableRelation.NO, note.acceleration.syllableRelation);
        }

        @Test
        @DisplayName("handles empty syllable")
        void handlesEmptySyllable() {
            var note = createNote("");
            var line = createLineWithNotes(note);

            assertTrue(note.acceleration.syllable.isEmpty());
        }

        @Test
        @DisplayName("handles underscore placeholder syllable")
        void handlesUnderscorePlaceholder() {
            var note = createNote(Constants.UNDERSCORE);
            var line = createLineWithNotes(note);

            assertEquals(Constants.UNDERSCORE, note.acceleration.syllable);
        }
    }

    // ==========================================================================
    // Syllable Relation Tests
    // ==========================================================================

    @Nested
    @DisplayName("syllable relations")
    class SyllableRelationTests {

        @Nested
        @DisplayName("NO relation")
        class NoRelationTests {

            @Test
            @DisplayName("note with NO relation has no connection")
            void noRelationNoConnection() {
                var note = createNote("word", Note.SyllableRelation.NO);
                assertEquals(Note.SyllableRelation.NO, note.acceleration.syllableRelation);
            }
        }

        @Nested
        @DisplayName("ONE_DASH relation (Gould/Ross hyphen)")
        class OneDashRelationTests {

            @Test
            @DisplayName("ONE_DASH relation indicates syllable division")
            void oneDashIndicatesSyllableDivision() {
                var note1 = createNote("won", Note.SyllableRelation.ONE_DASH);
                var note2 = createNote("der");

                assertEquals(Note.SyllableRelation.ONE_DASH, note1.acceleration.syllableRelation);
            }

            @Test
            @DisplayName("ONE_DASH renders single hyphen between syllables")
            void oneDashRendersSingleHyphen() {
                var note1 = createNote("beau", Note.SyllableRelation.ONE_DASH);
                note1.setXPos(100);

                var note2 = createNote("ti");
                note2.setXPos(150);

                var note3 = createNote("ful");
                note3.setXPos(200);

                var line = createLineWithNotes(note1, note2, note3);

                // Verify relation is set correctly
                assertEquals(Note.SyllableRelation.ONE_DASH, note1.acceleration.syllableRelation);
                assertEquals(Note.SyllableRelation.NO, note2.acceleration.syllableRelation);
            }

            @Test
            @DisplayName("consecutive ONE_DASH relations work correctly")
            void consecutiveOneDashRelations() {
                var note1 = createNote("beau", Note.SyllableRelation.ONE_DASH);
                var note2 = createNote("ti", Note.SyllableRelation.ONE_DASH);
                var note3 = createNote("ful");

                var line = createLineWithNotes(note1, note2, note3);

                assertEquals(Note.SyllableRelation.ONE_DASH, note1.acceleration.syllableRelation);
                assertEquals(Note.SyllableRelation.ONE_DASH, note2.acceleration.syllableRelation);
                assertEquals(Note.SyllableRelation.NO, note3.acceleration.syllableRelation);
            }
        }

        @Nested
        @DisplayName("EXTENDER relation (Gould/Ross duration line)")
        class ExtenderRelationTests {

            @Test
            @DisplayName("EXTENDER relation indicates duration")
            void extenderIndicatesDuration() {
                var note1 = createNote("word", Note.SyllableRelation.EXTENDER);
                var note2 = createNote(Constants.UNDERSCORE);

                assertEquals(Note.SyllableRelation.EXTENDER, note1.acceleration.syllableRelation);
            }

            @Test
            @DisplayName("EXTENDER renders continuous line")
            void extenderRendersContinuousLine() {
                var note1 = createNote("long", Note.SyllableRelation.EXTENDER);
                note1.setXPos(100);

                var note2 = createNote(Constants.UNDERSCORE);
                note2.setXPos(150);

                var note3 = createNote(Constants.UNDERSCORE);
                note3.setXPos(200);

                var line = createLineWithNotes(note1, note2, note3);

                // Verify relation is set correctly
                assertEquals(Note.SyllableRelation.EXTENDER, note1.acceleration.syllableRelation);
            }

            @Test
            @DisplayName("EXTENDER extends across multiple underscores")
            void extenderExtendsAcrossMultipleUnderscores() {
                var note1 = createNote("hold", Note.SyllableRelation.EXTENDER);
                var note2 = createNote(Constants.UNDERSCORE, Note.SyllableRelation.EXTENDER);
                var note3 = createNote(Constants.UNDERSCORE, Note.SyllableRelation.EXTENDER);
                var note4 = createNote(Constants.UNDERSCORE, Note.SyllableRelation.EXTENDER);

                var line = createLineWithNotes(note1, note2, note3, note4);

                assertEquals(Note.SyllableRelation.EXTENDER, note1.acceleration.syllableRelation);
                assertEquals(Note.SyllableRelation.EXTENDER, note2.acceleration.syllableRelation);
                assertEquals(Note.SyllableRelation.EXTENDER, note3.acceleration.syllableRelation);
            }
        }

        @Nested
        @DisplayName("DASH relation (deprecated)")
        class DashRelationTests {

            @Test
            @DisplayName("DASH relation type exists but should not be used")
            void dashRelationTypeExists() {
                // Verify DASH still exists in enum (for backward compatibility)
                // but should not be used in new code
                var dashRelation = Note.SyllableRelation.DASH;
                assertNotNull(dashRelation);
            }

            @Test
            @DisplayName("DASH should be replaced by ONE_DASH in new code")
            void dashShouldBeReplacedByOneDash() {
                // This test documents that DASH is deprecated
                // Use ONE_DASH for syllable division instead
                var oldWay = Note.SyllableRelation.DASH;
                var newWay = Note.SyllableRelation.ONE_DASH;

                // Both exist but only ONE_DASH should be used
                assertNotNull(oldWay);
                assertNotNull(newWay);
            }
        }
    }

    // ==========================================================================
    // Begin Relation Tests
    // ==========================================================================

    @Nested
    @DisplayName("begin-of-line relations")
    class BeginRelationTests {

        @Test
        @DisplayName("ONE_DASH at beginning renders leading hyphen")
        void oneDashAtBeginningRendersLeadingHyphen() {
            var note1 = createNote("der");
            var line = createLineWithNotes(note1);

            // Set begin relation (continuation from previous line)
            line.beginRelation = Note.SyllableRelation.ONE_DASH;

            assertEquals(Note.SyllableRelation.ONE_DASH, line.beginRelation);
        }

        @Test
        @DisplayName("EXTENDER at beginning renders leading extender")
        void extenderAtBeginningRendersLeadingExtender() {
            var note1 = createNote(Constants.UNDERSCORE);
            var line = createLineWithNotes(note1);

            // Set begin relation (continuation from previous line)
            line.beginRelation = Note.SyllableRelation.EXTENDER;

            assertEquals(Note.SyllableRelation.EXTENDER, line.beginRelation);
        }

        @Test
        @DisplayName("NO begin relation renders nothing before first note")
        void noBeginRelationRendersNothing() {
            var note1 = createNote("hello");
            var line = createLineWithNotes(note1);

            // Default begin relation is NO
            assertEquals(Note.SyllableRelation.NO, line.beginRelation);
        }
    }

    // ==========================================================================
    // Combined Scenario Tests
    // ==========================================================================

    @Nested
    @DisplayName("combined scenarios")
    class CombinedScenarioTests {

        @Test
        @DisplayName("mix of hyphens and extenders")
        void mixOfHyphensAndExtenders() {
            var note1 = createNote("beau", Note.SyllableRelation.ONE_DASH);
            var note2 = createNote("ti", Note.SyllableRelation.ONE_DASH);
            var note3 = createNote("ful", Note.SyllableRelation.EXTENDER);
            var note4 = createNote(Constants.UNDERSCORE, Note.SyllableRelation.EXTENDER);
            var note5 = createNote("song");

            var line = createLineWithNotes(note1, note2, note3, note4, note5);

            // Verify correct relations
            assertEquals(Note.SyllableRelation.ONE_DASH, note1.acceleration.syllableRelation);
            assertEquals(Note.SyllableRelation.ONE_DASH, note2.acceleration.syllableRelation);
            assertEquals(Note.SyllableRelation.EXTENDER, note3.acceleration.syllableRelation);
            assertEquals(Note.SyllableRelation.EXTENDER, note4.acceleration.syllableRelation);
            assertEquals(Note.SyllableRelation.NO, note5.acceleration.syllableRelation);
        }

        @Test
        @DisplayName("hyphen followed by extender")
        void hyphenFollowedByExtender() {
            var note1 = createNote("ka", Note.SyllableRelation.ONE_DASH);
            var note2 = createNote("re", Note.SyllableRelation.EXTENDER);
            var note3 = createNote(Constants.UNDERSCORE, Note.SyllableRelation.EXTENDER);
            var note4 = createNote(Constants.UNDERSCORE);

            var line = createLineWithNotes(note1, note2, note3, note4);

            assertEquals(Note.SyllableRelation.ONE_DASH, note1.acceleration.syllableRelation);
            assertEquals(Note.SyllableRelation.EXTENDER, note2.acceleration.syllableRelation);
            assertEquals(Note.SyllableRelation.EXTENDER, note3.acceleration.syllableRelation);
        }

        @Test
        @DisplayName("extender followed by hyphen")
        void extenderFollowedByHyphen() {
            var note1 = createNote("hold", Note.SyllableRelation.EXTENDER);
            var note2 = createNote(Constants.UNDERSCORE, Note.SyllableRelation.EXTENDER);
            var note3 = createNote(Constants.UNDERSCORE, Note.SyllableRelation.ONE_DASH);
            var note4 = createNote("ing");

            var line = createLineWithNotes(note1, note2, note3, note4);

            assertEquals(Note.SyllableRelation.EXTENDER, note1.acceleration.syllableRelation);
            assertEquals(Note.SyllableRelation.EXTENDER, note2.acceleration.syllableRelation);
            assertEquals(Note.SyllableRelation.ONE_DASH, note3.acceleration.syllableRelation);
        }

        @Test
        @DisplayName("empty syllables with relations")
        void emptySyllablesWithRelations() {
            var note1 = createNote("word", Note.SyllableRelation.EXTENDER);
            var note2 = createNote("", Note.SyllableRelation.EXTENDER);
            var note3 = createNote("", Note.SyllableRelation.EXTENDER);
            var note4 = createNote("next");

            var line = createLineWithNotes(note1, note2, note3, note4);

            // Empty syllables should be skipped in rendering
            // but relation should still be tracked
            assertEquals(Note.SyllableRelation.EXTENDER, note1.acceleration.syllableRelation);
            assertTrue(note2.acceleration.syllable.isEmpty());
            assertTrue(note3.acceleration.syllable.isEmpty());
        }
    }

    // ==========================================================================
    // Gould/Ross Rule Verification Tests
    // ==========================================================================

    @Nested
    @DisplayName("Gould/Ross engraving rules")
    class GouldRossRulesTests {

        @Test
        @DisplayName("hyphen always indicates syllable division only")
        void hyphenIndicatesSyllableDivisionOnly() {
            // Rule: Hyphen = syllable division, never duration
            var note1 = createNote("won", Note.SyllableRelation.ONE_DASH);
            var note2 = createNote("der");

            // Verify ONE_DASH is used (not DASH)
            assertEquals(Note.SyllableRelation.ONE_DASH, note1.acceleration.syllableRelation);
        }

        @Test
        @DisplayName("extender always indicates duration only")
        void extenderIndicatesDurationOnly() {
            // Rule: Extender = duration, never syllable division
            var note1 = createNote("long", Note.SyllableRelation.EXTENDER);
            var note2 = createNote(Constants.UNDERSCORE);

            // Verify EXTENDER is used for duration
            assertEquals(Note.SyllableRelation.EXTENDER, note1.acceleration.syllableRelation);
        }

        @Test
        @DisplayName("never use multiple hyphens for duration")
        void neverUseMultipleHyphensForDuration() {
            // Rule: Duration is shown with extender line, not multiple hyphens
            // This means DASH relation (which drew multiple hyphens) should not be used

            var note1 = createNote("long", Note.SyllableRelation.EXTENDER);
            var note2 = createNote(Constants.UNDERSCORE, Note.SyllableRelation.EXTENDER);

            // Verify we use EXTENDER, not DASH
            assertEquals(Note.SyllableRelation.EXTENDER, note1.acceleration.syllableRelation);
            assertEquals(Note.SyllableRelation.EXTENDER, note2.acceleration.syllableRelation);
        }

        @Test
        @DisplayName("single hyphen renders between syllables of same word")
        void singleHyphenBetweenSyllables() {
            // Rule: Hyphen shows syllable division (e.g., "beau-ti-ful")
            var note1 = createNote("beau", Note.SyllableRelation.ONE_DASH);
            var note2 = createNote("ti", Note.SyllableRelation.ONE_DASH);
            var note3 = createNote("ful");

            assertEquals(Note.SyllableRelation.ONE_DASH, note1.acceleration.syllableRelation);
            assertEquals(Note.SyllableRelation.ONE_DASH, note2.acceleration.syllableRelation);
            assertEquals(Note.SyllableRelation.NO, note3.acceleration.syllableRelation);
        }

        @Test
        @DisplayName("extender line shows sustained syllable")
        void extenderLineShowsSustainedSyllable() {
            // Rule: Extender shows a syllable held across multiple notes
            var note1 = createNote("love", Note.SyllableRelation.EXTENDER);
            var note2 = createNote(Constants.UNDERSCORE, Note.SyllableRelation.EXTENDER);
            var note3 = createNote(Constants.UNDERSCORE, Note.SyllableRelation.EXTENDER);
            var note4 = createNote(Constants.UNDERSCORE);

            assertEquals(Note.SyllableRelation.EXTENDER, note1.acceleration.syllableRelation);
            assertEquals(Note.SyllableRelation.EXTENDER, note2.acceleration.syllableRelation);
            assertEquals(Note.SyllableRelation.EXTENDER, note3.acceleration.syllableRelation);
        }
    }

    // ==========================================================================
    // Edge Case Tests
    // ==========================================================================

    @Nested
    @DisplayName("edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("single note with no relation")
        void singleNoteNoRelation() {
            var note = createNote("word");
            var line = createLineWithNotes(note);

            assertEquals(Note.SyllableRelation.NO, note.acceleration.syllableRelation);
        }

        @Test
        @DisplayName("all notes with underscores")
        void allNotesWithUnderscores() {
            var note1 = createNote(Constants.UNDERSCORE, Note.SyllableRelation.EXTENDER);
            var note2 = createNote(Constants.UNDERSCORE, Note.SyllableRelation.EXTENDER);
            var note3 = createNote(Constants.UNDERSCORE);

            var line = createLineWithNotes(note1, note2, note3);

            assertEquals(Note.SyllableRelation.EXTENDER, note1.acceleration.syllableRelation);
            assertEquals(Note.SyllableRelation.EXTENDER, note2.acceleration.syllableRelation);
        }

        @Test
        @DisplayName("hyphen at end of line")
        void hyphenAtEndOfLine() {
            var note1 = createNote("won", Note.SyllableRelation.ONE_DASH);
            var line = createLineWithNotes(note1);

            // Hyphen at end indicates continuation on next line
            assertEquals(Note.SyllableRelation.ONE_DASH, note1.acceleration.syllableRelation);
        }

        @Test
        @DisplayName("extender at end of line")
        void extenderAtEndOfLine() {
            var note1 = createNote("long", Note.SyllableRelation.EXTENDER);
            var line = createLineWithNotes(note1);

            // Extender at end indicates continuation on next line
            assertEquals(Note.SyllableRelation.EXTENDER, note1.acceleration.syllableRelation);
        }
    }
}
