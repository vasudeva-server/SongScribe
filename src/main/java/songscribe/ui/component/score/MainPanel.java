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

import java.awt.Dimension;
import java.awt.Point;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.jspecify.annotations.Nullable;

import songscribe.dom.DocumentScale;
import songscribe.dom.Song;
import songscribe.dom.Ss;
import songscribe.error.RuntimeError;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;

/**
 * Top-level panel for the score component hierarchy.
 * <p>
 * Contains:
 * <ul>
 *   <li>{@link TitleComponent} - song title</li>
 *   <li>{@link SubtitleComponent} - song subtitle (collapses when empty)</li>
 *   <li>{@link StaffPanel} - all staff lines</li>
 *   <li>{@link TextPanel} - under-lyrics sections (lyrics, Bangla, translation)</li>
 *   <li>{@link FootnotesComponent} - footnotes</li>
 * </ul>
 * <p>
 * This panel serves as the entry point for the new JComponent-based
 * rendering system. It is initially embedded alongside the existing
 * ScoreView rendering to allow gradual migration.
 */
public class MainPanel extends JPanel {

    /**
     * Margin from previous section to score top
     */
    public static final Ss SCORE_MARGIN_TOP_SS = new Ss(1.5);

    // BoxLayout Y_AXIS sibling stack order (top to bottom):
    //   titleComponent
    //   subtitleComponent   (zero height when subtitle is empty)
    //   scoreMarginTop strut
    //   staffPanel
    //   textPanel
    //   footnotesComponent

    /** Title component. */
    private final TitleComponent titleComponent;

    /** Subtitle component (collapses to (0,0) when subtitle is empty). */
    private final SubtitleComponent subtitleComponent;

    /** Staff panel containing all staff lines. */
    private final StaffPanel staffPanel;

    /** Text panel containing under-lyrics sections. */
    private final TextPanel textPanel;

    /** Footnotes component. */
    private final FootnotesComponent footnotesComponent;

    /** The song model. */
    @Nullable
    private Song song;

    /** The owning score view, or null when detached (tests). Supplies the view zoom. */
    @Nullable
    private ScoreView scoreView;

    /**
     * Creates a new MainPanel.
     */
    public MainPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);

        // The title and subtitle size to their text and must be centered on the page.
        // BoxLayout aligns children relative to each other, so a single centered child
        // among left-aligned siblings would be misaligned rather than centered — every
        // child in this stack shares the same alignment.
        titleComponent = new TitleComponent();
        titleComponent.setAlignmentX(CENTER_ALIGNMENT);

        subtitleComponent = new SubtitleComponent();
        subtitleComponent.setAlignmentX(CENTER_ALIGNMENT);

        staffPanel = new StaffPanel();
        staffPanel.setAlignmentX(CENTER_ALIGNMENT);

        textPanel = new TextPanel();
        textPanel.setAlignmentX(CENTER_ALIGNMENT);

        footnotesComponent = new FootnotesComponent();
        footnotesComponent.setAlignmentX(CENTER_ALIGNMENT);

        add(titleComponent);
        add(subtitleComponent);
        var scoreMarginTop = new ScoreMarginStrut();
        scoreMarginTop.setAlignmentX(CENTER_ALIGNMENT);

        add(scoreMarginTop);
        add(staffPanel);
        add(textPanel);
        add(footnotesComponent);
    }

    /**
     * Sets the owning {@link ScoreView} and fans it out to the once-created leaf
     * components and the child panels so every on-score component reads the same
     * view zoom on demand.
     */
    public void setScoreView(ScoreView scoreView) {
        this.scoreView = scoreView;
        titleComponent.setScoreView(scoreView);
        subtitleComponent.setScoreView(scoreView);
        footnotesComponent.setScoreView(scoreView);
        staffPanel.setScoreView(scoreView);
        textPanel.setScoreView(scoreView);
    }

    /** The view zoom, or {@link ViewScale#IDENTITY} when detached. */
    private ViewScale viewScale() {
        return scoreView != null ? scoreView.getViewScale() : ViewScale.IDENTITY;
    }

    /** Spacing between title and score, scaled to the current view zoom. */
    private int scoreMarginTopPx() {
        return viewScale().toViewPx(SCORE_MARGIN_TOP_SS).positionPx();
    }

    /**
     * The gap between the title block and the score, as a BoxLayout sibling.
     * <p>
     * Reports its height on demand instead of baking it in at construction the way
     * {@link Box#createVerticalStrut} does. The height is zoom-dependent, so a fixed strut
     * held the zoom in force when this panel was built while {@link #getPreferredSize} used
     * the current one — and the score block shifted vertically by the difference.
     */
    private final class ScoreMarginStrut extends JComponent {

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(0, scoreMarginTopPx());
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }
    }

    /**
     * Sets the song and updates all child components.
     *
     * @param song The song
     */
    public void setSong(Song song) {
        this.song = song;
        titleComponent.setSong(song);
        subtitleComponent.setSong(song);
        staffPanel.setSong(song);
        textPanel.setSong(song);
        footnotesComponent.setSong(song);
        revalidate();
        repaint();
    }

    /**
     * Returns the song.
     */
    public Song getSong() {
        if (song == null) {
            throw RuntimeError.exit("song not initialized");
        }

        return song;
    }

    /**
     * Returns the title component.
     */
    public TitleComponent getTitleComponent() {
        return titleComponent;
    }

    /**
     * Returns the subtitle component.
     */
    public SubtitleComponent getSubtitleComponent() {
        return subtitleComponent;
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
     * Rebuilds the layout when the song structure changes.
     * <p>
     * Call this after lines are added or removed from the song.
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
        if (song == null) {
            return new Dimension(0, 0);
        }

        var titleSize = titleComponent.getPreferredSize();
        var subtitleSize = subtitleComponent.getPreferredSize();
        var scoreSize = staffPanel.getPreferredSize();
        var textSize = textPanel.getPreferredSize();
        var footnotesSize = footnotesComponent.getPreferredSize();

        // Every child, subtitle included: BoxLayout gives this panel's width to each child, so a
        // child left out of this maximum is one the panel can be too narrow to hold.
        var width = Math.max(
            Math.max(titleSize.width, subtitleSize.width),
            Math.max(scoreSize.width, Math.max(textSize.width, footnotesSize.width))
        );
        var height = titleSize.height + subtitleSize.height;

        if (height > 0 && scoreSize.height > 0) {
            height += scoreMarginTopPx();
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
