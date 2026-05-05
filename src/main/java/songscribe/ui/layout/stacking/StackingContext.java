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

package songscribe.ui.layout.stacking;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.ui.layout.ElementColumn;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.layout.StaffExtents;

/**
 * Shared context passed to all stacking delegates.
 * <p>
 * Holds the column list, element-to-column map, line, and layout builder
 * that every stacker needs. {@code notesWithUpwardTie} is mutable: it is
 * populated during seeding (tier 0) and read by later tiers.
 */
public class StackingContext {

    private final List<ElementColumn> columns;
    private final Map<StaffElement, ElementColumn> columnsByElement;
    private final Line line;
    private final LayoutResult.Builder builder;
    private Set<StaffElement> notesWithUpwardTie = Set.of();
    private double lowestNoteBotSs = StaffExtents.STAFF_HEIGHT_SS;
    // True extent of staff-element content below the staff. Defaults to staff bottom
    // (STAFF_HALF_SS in middle-relative coordinates) so an empty line contributes 0
    // below-staff content. Used for lyric positioning.
    private double botContentExtentSs = StaffExtents.STAFF_HALF_SS;

    public StackingContext(
            List<ElementColumn> columns,
            Line line,
            LayoutResult.Builder builder) {
        this.columns = columns;
        this.line = line;
        this.builder = builder;
        columnsByElement = buildColumnMap(columns);
    }

    public List<ElementColumn> getColumns() {
        return columns;
    }

    public Map<StaffElement, ElementColumn> getColumnsByElement() {
        return columnsByElement;
    }

    public Line getLine() {
        return line;
    }

    public LayoutResult.Builder getBuilder() {
        return builder;
    }

    public Set<StaffElement> getNotesWithUpwardTie() {
        return notesWithUpwardTie;
    }

    public void setNotesWithUpwardTie(Set<StaffElement> notesWithUpwardTie) {
        this.notesWithUpwardTie = notesWithUpwardTie;
    }

    public double getLowestNoteBotSs() {
        return lowestNoteBotSs;
    }

    /**
     * Updates the lowest note bottom if the given value is further below staff center.
     */
    public void updateLowestNoteBotSs(double botSs) {
        if (botSs > lowestNoteBotSs) {
            lowestNoteBotSs = botSs;
        }
    }

    public double getBotContentExtentSs() {
        return botContentExtentSs;
    }

    /**
     * Updates the below-staff content extent if the given value is further below the staff.
     * Tracks the lowest visible Y across notes, stems, and downward-arcing ties.
     */
    public void updateBotContentExtentSs(double botSs) {
        if (botSs > botContentExtentSs) {
            botContentExtentSs = botSs;
        }
    }

    /**
     * Builds a map from StaffElement to its ElementColumn for range element lookups.
     */
    private static Map<StaffElement, ElementColumn> buildColumnMap(List<ElementColumn> columns) {
        var map = new HashMap<StaffElement, ElementColumn>(columns.size());

        for (var column : columns) {
            map.put(column.getElement(), column);
        }

        return map;
    }
}
