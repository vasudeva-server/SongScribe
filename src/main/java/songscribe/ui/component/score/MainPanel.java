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

package songscribe.ui.component.score;

import module java.desktop;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import songscribe.music.Composition;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout2.ScaleContext;

/**
 * Top-level panel for the score component hierarchy.
 * <p>
 * Contains:
 * <ul>
 *   <li>{@link TitleComponent} - composition title</li>
 *   <li>{@link StaffPanel} - all staff lines</li>
 *   <li>{@link TextPanel} - under-lyrics sections (lyrics, Bangla, translation)</li>
 *   <li>{@link FootnotesComponent} - footnotes</li>
 * </ul>
 * <p>
 * This panel serves as the entry point for the new JComponent-based
 * rendering system. It is initially embedded alongside the existing
 * Score rendering to allow gradual migration.
 */
public class MainPanel extends JPanel {

    /** Title component. */
    private final TitleComponent titleComponent;

    /** Staff panel containing all staff lines. */
    private final StaffPanel staffPanel;

    /** Text panel containing under-lyrics sections. */
    private final TextPanel textPanel;

    /** Footnotes component. */
    private final FootnotesComponent footnotesComponent;

    /** The composition model. */
    @Nullable
    private Composition composition;

    /** Spacing between title and score. */
    private final int scoreMarginTop;

    /**
     * Creates a new MainPanel.
     */
    public MainPanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);

        scoreMarginTop = ScaleContext.getInstance().toRoundedPixels(LayoutStylesheet.SCORE_MARGIN_TOP_SS);

        titleComponent = new TitleComponent();
        titleComponent.setAlignmentX(LEFT_ALIGNMENT);

        staffPanel = new StaffPanel();
        staffPanel.setAlignmentX(LEFT_ALIGNMENT);

        textPanel = new TextPanel();
        textPanel.setAlignmentX(LEFT_ALIGNMENT);

        footnotesComponent = new FootnotesComponent();
        footnotesComponent.setAlignmentX(LEFT_ALIGNMENT);

        add(titleComponent);
        add(Box.createVerticalStrut(scoreMarginTop));
        add(staffPanel);
        add(textPanel);
        add(footnotesComponent);
    }

    /**
     * Sets the composition and updates all child components.
     *
     * @param composition The composition
     */
    public void setComposition(Composition composition) {
        this.composition = composition;
        titleComponent.setComposition(composition);
        staffPanel.setComposition(composition);
        textPanel.setComposition(composition);
        footnotesComponent.setComposition(composition);
        revalidate();
        repaint();
    }

    /**
     * Returns the composition.
     */
    public Composition getComposition() {
        return Objects.requireNonNull(composition, "composition not initialized");
    }

    /**
     * Returns the title component.
     */
    public TitleComponent getTitleComponent() {
        return titleComponent;
    }

    /**
     * Returns the staff panel.
     */
    public StaffPanel getStaffPanel() {
        return staffPanel;
    }

    /**
     * Returns the text panel.
     */
    public TextPanel getTextPanel() {
        return textPanel;
    }

    /**
     * Returns the footnotes component.
     */
    public FootnotesComponent getFootnotesComponent() {
        return footnotesComponent;
    }

    /**
     * Rebuilds the layout when the composition structure changes.
     * <p>
     * Call this after lines are added or removed from the composition.
     */
    public void rebuildLayout() {
        staffPanel.rebuildLayout();
        revalidate();
        repaint();
    }

    /**
     * Returns the line panel at the given point.
     *
     * @param point Point in panel coordinates
     * @return The line panel, or null
     */
    @Nullable
    public LinePanel getLinePanelAt(Point point) {
        var scoreBounds = staffPanel.getBounds();

        if (scoreBounds.contains(point)) {
            var localPoint = new Point(
                point.x - scoreBounds.x,
                point.y - scoreBounds.y
            );
            return staffPanel.getLinePanelAt(localPoint);
        }

        return null;
    }

    /**
     * Returns the line panel at the given index.
     *
     * @param index Line index
     * @return The line panel, or null
     */
    @Nullable
    public LinePanel getLinePanel(int index) {
        return staffPanel.getLinePanel(index);
    }

    @Override
    public Dimension getPreferredSize() {
        if (composition == null) {
            return new Dimension(0, 0);
        }

        var titleSize = titleComponent.getPreferredSize();
        var scoreSize = staffPanel.getPreferredSize();
        var textSize = textPanel.getPreferredSize();
        var footnotesSize = footnotesComponent.getPreferredSize();

        var width = Math.max(
            titleSize.width,
            Math.max(scoreSize.width, Math.max(textSize.width, footnotesSize.width))
        );
        var height = titleSize.height;

        if (titleSize.height > 0 && scoreSize.height > 0) {
            height += scoreMarginTop;
        }

        height += scoreSize.height;
        height += textSize.height;
        height += footnotesSize.height;

        return new Dimension(width, height);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }
}
