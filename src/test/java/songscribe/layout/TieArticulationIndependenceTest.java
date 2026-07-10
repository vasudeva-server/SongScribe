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

package songscribe.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import module java.desktop;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.ElementType;
import songscribe.dom.ScaleContext;
import songscribe.dom.Tie;
import songscribe.font.DocumentFonts;

/**
 * A tie moves a script only where the arc physically overlaps it, exactly as LilyPond's
 * {@code Skyline::distance} does — never merely by existing.
 * <p>
 * Both attachment modes are pinned, because they are the two halves of one rule. LilyPond's
 * {@code Script_engraver::acknowledge_tie} adds every tie as a script side-support, yet the tie
 * changes nothing for an edge-attached tie (which begins a {@code NOTE_HEAD_GAP_SS} beyond the
 * notehead, clear of the script) and pushes hard for a centre-attached one (whose endpoint recedes
 * to the notehead centre, landing on top of the script). Verified against LilyPond 2.24: tied and
 * untied {@code d'}, {@code g'} place their accent at staff-position -8.289 and -6.399 respectively,
 * unchanged; centre-attached {@code a'} moves its staccato by a full staff space.
 * <p>
 * These use the full layout pipeline on purpose. The unit fixtures in {@code NoteAttachedStackerTest}
 * build ties from {@code flatTieLayout}, whose control points all share one Y, so they cannot
 * distinguish a reservation that tracks the arc from one stamped across the notehead.
 */
@SuppressWarnings({"DataFlowIssue", "NullAway"})
class TieArticulationIndependenceTest extends UnitTest {

    private static final double STAFF_RIGHT_MARGIN_SS = 60.0;
    private static final double TOLERANCE = 0.001;

    // A ledger line below the staff: the tie seats within the head box, so its endpoints attach at
    // the notehead's facing edge — clear of the scripts (LayoutEngine.tieSeatSs edge branch). Chosen
    // below the staff so the scripts clear the staff-padding clamp and the tie is the only support
    // that can move them; a within-staff note would pass this test even with a phantom reservation,
    // because the clamp, not the tie, would be deciding.
    private static final int EDGE_ATTACH_SP = 6;

    // A space note whose arc-side row is a staff line: the seat is pushed past the head box, so the
    // endpoints recede to the notehead centre and the arc covers the scripts (centre attach).
    private static final int CENTRE_ATTACH_SP = 1;

    // Below-staff scripts (sp > 0 → stem up) are pushed to larger Y, since Y increases downward.
    private static final double MIN_CENTRE_ATTACH_PUSH_SS = 0.1;

    private record ScriptYSs(double staccatoYSs, double accentYSs) {}

    private static LayoutEngine engine() {
        var lyricsFont = new Font("Dialog", Font.PLAIN, 12);
        var hyphenWidthSs = ScaleContext.textWidthSs(lyricsFont, "-");
        var spaceWidthSs = ScaleContext.textWidthSs(lyricsFont, " ");
        var metrics = new LyricRenderMetrics(
            lyricsFont, ScaleContext.scaleFont(lyricsFont), hyphenWidthSs, spaceWidthSs);
        return new LayoutEngine(metrics, STAFF_RIGHT_MARGIN_SS, DocumentFonts.defaultFonts());
    }

    /** Lays out two same-pitch notes, the first carrying a staccato and an accent. */
    private static ScriptYSs scriptYSs(int staffPosition, boolean tied) {
        var line = detachedLine();
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setStaffPosition(staffPosition);
        var note2 = ElementType.CROTCHET.newInstance();
        note2.setStaffPosition(staffPosition);
        line.addElement(note1);
        line.addElement(note2);

        var staccato = new Articulation(ArticulationType.STACCATO);
        var accent = new Articulation(ArticulationType.ACCENT);
        note1.addArticulation(staccato);
        note1.addArticulation(accent);

        if (tied) {
            line.addRangeElement(new Tie(note1, note2));
        }

        var result = require(engine().layout(line), "LayoutResult");
        var staccatoLayout = require(result.getDecorationLayout(staccato), "staccato layout");
        var accentLayout = require(result.getDecorationLayout(accent), "accent layout");

        return new ScriptYSs(staccatoLayout.ySs(), accentLayout.ySs());
    }

    @SuppressWarnings("NullAway")
    private static <T> T require(@Nullable T value, String description) {
        assertThat(value).describedAs(description).isNotNull();
        return value;
    }

    @Test
    void testEdgeAttachedTieLeavesScriptsWhereTheyWouldSitUntied() {
        var untied = scriptYSs(EDGE_ATTACH_SP, false);
        var tied = scriptYSs(EDGE_ATTACH_SP, true);

        assertThat(tied.staccatoYSs())
            .describedAs("edge-attached tie clears the notehead, so the staccato must not move")
            .isCloseTo(untied.staccatoYSs(), within(TOLERANCE));

        assertThat(tied.accentYSs())
            .describedAs("edge-attached tie clears the notehead, so the accent must not move")
            .isCloseTo(untied.accentYSs(), within(TOLERANCE));
    }

    @Test
    void testCentreAttachedTiePushesScriptsOutward() {
        var untied = scriptYSs(CENTRE_ATTACH_SP, false);
        var tied = scriptYSs(CENTRE_ATTACH_SP, true);

        assertThat(tied.staccatoYSs() - untied.staccatoYSs())
            .describedAs("centre-attached tie covers the notehead, so it must push the staccato down")
            .isGreaterThan(MIN_CENTRE_ATTACH_PUSH_SS);

        assertThat(tied.accentYSs() - untied.accentYSs())
            .describedAs("centre-attached tie covers the notehead, so it must push the accent down")
            .isGreaterThan(MIN_CENTRE_ATTACH_PUSH_SS);
    }
}
