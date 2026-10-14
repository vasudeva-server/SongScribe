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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Beam;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.hit.HitRegistry;
import songscribe.layout.LayoutResult.BeamLayout;
import songscribe.layout.LayoutResult.SlideLayout;
import songscribe.layout.LayoutResult.StemLayout;
import songscribe.layout.LayoutResult.TieLayout;

/**
 * Builder for creating {@link LayoutResult} instances incrementally. The staff header is taken at
 * construction, so a builder cannot exist without it.
 */
public class LayoutResultBuilder {

    private final Map<StaffElement, ElementColumn> elementColumns;
    private final Map<Beam, BeamLayout> beamLayouts;
    private final Map<StaffElement, StemLayout> stemLayouts;
    private final List<TieLayout> tieLayouts;
    private final DecorationLayouts.Builder decorationLayouts;
    private final Map<StaffElement, SlideLayout> slideLayouts;
    private final StaffHeader staffHeader;
    private double contentAboveStaffSs = 0;
    private double contentBelowStaffSs = 0;
    private final Map<StaffElement, List<LyricBoxLayout>> lyricBoxes;
    private final Map<StaffElement, LyricBoxLayout> lyricPlaceholders;
    private final List<LyricConnectorLayout> lyricConnectors;
    private boolean hasTrailingLyricContinuation = false;
    private boolean overflowsStaffWidth = false;

    LayoutResultBuilder(StaffHeader staffHeader) {
        this.staffHeader = staffHeader;
        elementColumns = new HashMap<>();
        beamLayouts = new HashMap<>();
        stemLayouts = new HashMap<>();
        tieLayouts = new ArrayList<>();
        decorationLayouts = new DecorationLayouts.Builder();
        slideLayouts = new HashMap<>();
        lyricBoxes = new HashMap<>();
        lyricPlaceholders = new HashMap<>();
        lyricConnectors = new ArrayList<>();
    }

    public LayoutResultBuilder putElementColumn(StaffElement element, ElementColumn column) {
        elementColumns.put(element, column);
        return this;
    }

    public LayoutResultBuilder setContentAboveStaffSs(double contentAboveStaffSs) {
        this.contentAboveStaffSs = contentAboveStaffSs;
        return this;
    }

    /**
     * Must never be negative: 0 means nothing below the staff, which {@link #lyricAnchorBelowStaffSs}
     * keys off.
     */
    public LayoutResultBuilder setContentBelowStaffSs(double contentBelowStaffSs) {
        this.contentBelowStaffSs = contentBelowStaffSs;
        return this;
    }

    public LayoutResultBuilder addLyricBox(StaffElement element, LyricBoxLayout box) {
        lyricBoxes.computeIfAbsent(element, e -> new ArrayList<>()).add(box);
        return this;
    }

    public LayoutResultBuilder addLyricPlaceholder(StaffElement element, LyricBoxLayout placeholder) {
        lyricPlaceholders.put(element, placeholder);
        return this;
    }

    public LayoutResultBuilder addLyricConnector(LyricConnectorLayout connector) {
        lyricConnectors.add(connector);
        return this;
    }

    public LayoutResultBuilder setHasTrailingLyricContinuation(boolean hasTrailingLyricContinuation) {
        this.hasTrailingLyricContinuation = hasTrailingLyricContinuation;
        return this;
    }

    public LayoutResultBuilder setOverflowsStaffWidth(boolean overflowsStaffWidth) {
        this.overflowsStaffWidth = overflowsStaffWidth;
        return this;
    }

    public LayoutResultBuilder putBeamLayout(Beam beam, BeamLayout beamLayout) {
        beamLayouts.put(beam, beamLayout);
        return this;
    }

    public LayoutResultBuilder putStemLayout(StaffElement element, StemLayout stemLayout) {
        stemLayouts.put(element, stemLayout);
        return this;
    }

    /**
     * @param tieLayout a line adds at most one per tie it draws
     */
    public LayoutResultBuilder addTieLayout(TieLayout tieLayout) {
        tieLayouts.add(tieLayout);
        return this;
    }

    public LayoutResultBuilder putSlideLayout(StaffElement note, SlideLayout slideLayout) {
        slideLayouts.put(note, slideLayout);
        return this;
    }

    public DecorationLayouts.Builder decorationLayouts() {
        return decorationLayouts;
    }

    public List<TieLayout> tieLayouts() {
        return Collections.unmodifiableList(tieLayouts);
    }

    /**
     * @return null if not yet computed
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

    public LayoutResult build() {
        return new LayoutResult(
            elementColumns,
            beamLayouts,
            stemLayouts,
            tieLayouts,
            decorationLayouts.build(),
            slideLayouts,
            staffHeader,
            contentAboveStaffSs,
            contentBelowStaffSs,
            lyricBoxes,
            lyricPlaceholders,
            lyricConnectors,
            hasTrailingLyricContinuation,
            overflowsStaffWidth,
            // The registry is built from a finished result and attached with
            // withHitRegistry, so a freshly built one has nothing clickable yet.
            HitRegistry.EMPTY
        );
    }
}
