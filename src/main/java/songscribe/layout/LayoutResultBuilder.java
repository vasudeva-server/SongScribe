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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Beam;
import songscribe.dom.Clef;
import songscribe.dom.KeySignature;
import songscribe.dom.LineElement;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.hit.HitRegistry;
import songscribe.layout.LayoutResult.BeamLayout;
import songscribe.layout.LayoutResult.DecorationLayout;
import songscribe.layout.LayoutResult.SlideLayout;
import songscribe.layout.LayoutResult.StemLayout;
import songscribe.layout.LayoutResult.TieLayout;

/**
 * Builder for creating {@link LayoutResult} instances incrementally.
 */
public class LayoutResultBuilder {

    private final Map<StaffElement, ElementColumn> elementColumns;
    private final Map<Beam, BeamLayout> beamLayouts;
    private final Map<StaffElement, StemLayout> stemLayouts;
    private final Map<Tie, TieLayout> tieLayouts;
    private final Map<LineElement, DecorationLayout> decorationLayouts;
    private final Map<StaffElement, SlideLayout> slideLayouts;
    @Nullable
    private Clef clef;
    @Nullable
    private KeySignature keySignature;
    private double contentAboveStaffSs = 0;
    private double contentBelowStaffSs = 0;
    private final Map<StaffElement, List<LyricBoxLayout>> lyricBoxes;
    private final List<LyricConnectorLayout> lyricConnectors;
    private boolean hasTrailingLyricContinuation = false;
    private boolean overflowsStaffWidth = false;

    public LayoutResultBuilder() {
        elementColumns = new HashMap<>();
        beamLayouts = new HashMap<>();
        stemLayouts = new HashMap<>();
        tieLayouts = new HashMap<>();
        decorationLayouts = new HashMap<>();
        slideLayouts = new HashMap<>();
        lyricBoxes = new HashMap<>();
        lyricConnectors = new ArrayList<>();
    }

    /**
     * Sets the clef element for this line.
     *
     * @param clef The clef element
     * @return This builder for chaining
     */
    public LayoutResultBuilder setClef(Clef clef) {
        this.clef = clef;
        return this;
    }

    /**
     * Sets the key signature element for this line.
     *
     * @param keySignature The key signature element
     * @return This builder for chaining
     */
    public LayoutResultBuilder setKeySignature(KeySignature keySignature) {
        this.keySignature = keySignature;
        return this;
    }

    /**
     * Adds an element column to the result.
     *
     * @param element The element
     * @param column  The element's column with position
     * @return This builder for chaining
     */
    public LayoutResultBuilder putElementColumn(StaffElement element, ElementColumn column) {
        elementColumns.put(element, column);
        return this;
    }

    /**
     * Sets the true extent of this line's content above the staff top.
     *
     * @param contentAboveStaffSs Distance above the staff top in staff-space units (i.e. the
     *                            staff top's Y position within the line's local coordinate frame)
     * @return This builder for chaining
     */
    public LayoutResultBuilder setContentAboveStaffSs(double contentAboveStaffSs) {
        this.contentAboveStaffSs = contentAboveStaffSs;
        return this;
    }

    /**
     * Sets the true extent of this line's content below the staff bottom.
     * <p>
     * Must never be negative. A line with nothing below its staff sets 0, which is what
     * {@link #lyricAnchorBelowStaffSs} keys off to inset the lyric row; a negative value
     * would slip past that check and place the row above the staff bottom.
     *
     * @param contentBelowStaffSs Distance below the staff bottom in staff-space units,
     *                            zero or greater
     * @return This builder for chaining
     */
    public LayoutResultBuilder setContentBelowStaffSs(double contentBelowStaffSs) {
        this.contentBelowStaffSs = contentBelowStaffSs;
        return this;
    }

    /**
     * Adds a lyric box for an element. One verse is laid out at a time, so an element gets at
     * most one box; insertion order is preserved.
     *
     * @param element the element the box is anchored to
     * @param box     the lyric box layout
     * @return this builder for chaining
     */
    public LayoutResultBuilder addLyricBox(StaffElement element, LyricBoxLayout box) {
        lyricBoxes.computeIfAbsent(element, e -> new ArrayList<>()).add(box);
        return this;
    }

    /**
     * Adds a lyric connector (hyphen or extender) to the line.
     *
     * @param connector the connector layout
     * @return this builder for chaining
     */
    public LayoutResultBuilder addLyricConnector(LyricConnectorLayout connector) {
        lyricConnectors.add(connector);
        return this;
    }

    /**
     * Marks whether this line ends with an active melisma extender that continues onto
     * the next line.
     *
     * @param hasTrailingLyricContinuation true if continuation continues past the last note
     * @return this builder for chaining
     */
    public LayoutResultBuilder setHasTrailingLyricContinuation(boolean hasTrailingLyricContinuation) {
        this.hasTrailingLyricContinuation = hasTrailingLyricContinuation;
        return this;
    }

    /**
     * Marks whether the line was placed on its collision floors because its content could not
     * fit the staff width, so its tail runs past the staff and is clipped.
     *
     * @param overflowsStaffWidth true if the line overflows the staff width
     * @return this builder for chaining
     */
    public LayoutResultBuilder setOverflowsStaffWidth(boolean overflowsStaffWidth) {
        this.overflowsStaffWidth = overflowsStaffWidth;
        return this;
    }

    /**
     * Adds computed beam geometry for a beam span.
     *
     * @param span       The beam span
     * @param beamLayout The computed beam geometry
     * @return This builder for chaining
     */
    public LayoutResultBuilder putBeamLayout(Beam beam, BeamLayout beamLayout) {
        beamLayouts.put(beam, beamLayout);
        return this;
    }

    /**
     * Adds computed stem geometry for an unbeamed element.
     *
     * @param element    The element
     * @param stemLayout The computed stem geometry
     * @return This builder for chaining
     */
    public LayoutResultBuilder putStemLayout(StaffElement element, StemLayout stemLayout) {
        stemLayouts.put(element, stemLayout);
        return this;
    }

    /**
     * Adds computed tie geometry for a tie span.
     *
     * @param span      The tie span
     * @param tieLayout The computed tie geometry
     * @return This builder for chaining
     */
    public LayoutResultBuilder putTieLayout(Tie tie, TieLayout tieLayout) {
        tieLayouts.put(tie, tieLayout);
        return this;
    }

    /**
     * Adds computed slide geometry for a slide-owning note.
     *
     * @param note        The note owning the slide
     * @param slideLayout The computed slide geometry
     * @return This builder for chaining
     */
    public LayoutResultBuilder putSlideLayout(StaffElement note, SlideLayout slideLayout) {
        slideLayouts.put(note, slideLayout);
        return this;
    }

    /**
     * Adds computed decoration layout for an above-staff decoration element.
     *
     * @param element          The decoration element
     * @param decorationLayout The computed decoration geometry
     * @return This builder for chaining
     */
    public LayoutResultBuilder putDecorationLayout(
        LineElement element,
        DecorationLayout decorationLayout
    ) {
        decorationLayouts.put(element, decorationLayout);
        return this;
    }

    /**
     * Returns the decoration layout entries for iteration.
     * <p>
     * Used by the post-layout offset application pass to read and update
     * decoration positions with manual user offsets.
     *
     * @return The decoration layout entry set (mutable — callers may update via
     * {@link #putDecorationLayout})
     */
    public Set<Map.Entry<LineElement, DecorationLayout>> getDecorationLayoutEntries() {
        return decorationLayouts.entrySet();
    }

    /**
     * Returns the decoration layout for a specific element, or null if not yet computed.
     *
     * @param element The decoration element to look up
     * @return The decoration layout, or null if not present
     */
    public @Nullable DecorationLayout getDecorationLayout(LineElement element) {
        return decorationLayouts.get(element);
    }

    /**
     * Returns the tie layout for a tie span from the builder's accumulated data.
     *
     * @param span The tie span to look up
     * @return The tie layout, or null if not yet computed
     */
    public @Nullable TieLayout getTieLayout(Tie tie) {
        return tieLayouts.get(tie);
    }

    /**
     * Returns the stem layout for an element from the builder's accumulated data.
     * <p>
     * Checks beamed stem layouts first, then standalone stem layouts.
     * Used by the vertical stacking calculator to seed note bounding areas.
     *
     * @param element The element to look up
     * @return The stem layout, or null if not yet computed
     */
    public @Nullable StemLayout getStemLayout(StaffElement element) {
        for (var beamLayout : beamLayouts.values()) {
            var stemLayout = beamLayout.stems().get(element);

            if (stemLayout != null) {
                return stemLayout;
            }
        }

        return stemLayouts.get(element);
    }

    /**
     * Builds the immutable result.
     *
     * @return The layout result
     */
    public LayoutResult build() {
        return new LayoutResult(
            elementColumns,
            beamLayouts,
            stemLayouts,
            tieLayouts,
            decorationLayouts,
            slideLayouts,
            clef,
            keySignature,
            contentAboveStaffSs,
            contentBelowStaffSs,
            lyricBoxes,
            lyricConnectors,
            hasTrailingLyricContinuation,
            overflowsStaffWidth,
            // The registry is built from a finished result and attached with
            // withHitRegistry, so a freshly built one has nothing clickable yet.
            HitRegistry.EMPTY
        );
    }
}
