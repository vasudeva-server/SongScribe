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
package songscribe.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

import songscribe.ui.dialog.fontchooser.FontChooser;
import songscribe.ui.dialog.fontchooser.FontFamilies;
import songscribe.ui.dialog.fontchooser.FontFamily;

/**
 * Verifies {@link FontChooser#setSelectedFont(Font)}'s cross-component Swing wiring in the
 * real event pipeline — the contract that can only be observed when the family, style, and
 * size panes fire real {@code ListSelectionEvent}s.
 * <p>
 * {@code setSelectedFont} detaches the three {@code ListSelectionListener}s, updates the
 * model, then re-attaches them in {@code initPanes()} only after each pane's selection has
 * been set. The point is that no listener fires a re-entrant {@code setSelectedFont} while
 * the panes are half-updated (which would compose a font from a stale style/size). A
 * refactor that dropped the detachment would let the family listener fire mid-update and
 * mutate the model a second time — this test pins exactly one model change event.
 */
class FontChooserTest extends E2ETest {

    /**
     * A point size guaranteed to be present in {@code SizePane}'s size list (which runs
     * 6..14 by 1, then 16..28 by 2, ...), so selecting it actually moves the size-list
     * selection. Held constant across the before/after fonts so only family and style
     * change: this keeps the size pane's listener — which {@code initPanes()} re-attaches
     * before {@code setSelectedSize} — from firing, isolating the family/style path.
     */
    private static final int SELECTED_FONT_SIZE = 18;

    @Test
    void testSetSelectedFontUpdatesPanesWithoutListenerReentry() {
        var families = FontFamilies.getInstance();
        assumeTrue(families.size() >= 2, "needs at least two system font families");

        var familyList = new ArrayList<FontFamily>();
        for (var family : families) {
            familyList.add(family);
        }
        var familyBefore = familyList.get(0);
        var familyAfter = familyList.get(1);

        // Derive from real fonts in each family so StyleEntry's PSName-based equality matches
        // a list entry; constructed (logical) fonts would not.
        var fontBefore = familyBefore.getStyles().iterator().next().deriveFont((float) SELECTED_FONT_SIZE);
        var fontAfter = familyAfter.getStyles().iterator().next().deriveFont((float) SELECTED_FONT_SIZE);

        var modelChanges = new AtomicInteger();
        var chooser = GuiActionRunner.execute(() -> {
            var fc = new FontChooser(fontBefore);
            fc.addChangeListener(e -> modelChanges.incrementAndGet());
            return fc;
        });
        assertThat(chooser).isNotNull();
        if (chooser == null) {
            return; // unreachable — satisfies NullAway after the assertThat above
        }

        GuiActionRunner.execute(() -> chooser.setSelectedFont(fontAfter));

        var selectedFont = GuiActionRunner.execute(chooser::getSelectedFont);
        var selectedFamily = GuiActionRunner.execute(chooser::getSelectedFamily);
        var selectedSize = GuiActionRunner.execute(chooser::getSelectedSize);

        assertThat(selectedFont).isNotNull();
        if (selectedFont == null) {
            return; // unreachable — satisfies NullAway after the assertThat above
        }

        // The panes reflect the new font.
        assertThat(selectedFamily).isEqualTo(familyAfter.getName());
        assertThat(selectedSize).isEqualTo((float) SELECTED_FONT_SIZE);
        assertThat(selectedFont.getFamily()).isEqualTo(familyAfter.getName());
        assertThat(selectedFont.getSize()).isEqualTo(SELECTED_FONT_SIZE);

        // No listener-triggered re-entry: only the explicit set changed the model.
        assertThat(modelChanges.get()).isEqualTo(1);
    }
}
