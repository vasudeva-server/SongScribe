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
 */
public class MainPanel extends JPanel {

    public static final Ss SCORE_MARGIN_TOP_SS = new Ss(1.5);

    private final TitleComponent titleComponent;

    private final TitleComponent subtitleComponent;

    private final StaffPanel staffPanel;

    private final TextPanel textPanel;

    private final FootnotesComponent footnotesComponent;

    private final ScoreView scoreView;

    public MainPanel(ScoreView scoreView) {
        this.scoreView = scoreView;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);

        // The title and subtitle size to their text and must be centered on the page.
        // BoxLayout aligns children relative to each other, so a single centered child
        // among left-aligned siblings would be misaligned rather than centered — every
        // child in this stack shares the same alignment.
        titleComponent = new TitleComponent(scoreView, TitleKind.TITLE);
        titleComponent.setAlignmentX(CENTER_ALIGNMENT);

        subtitleComponent = new TitleComponent(scoreView, TitleKind.SUBTITLE);
        subtitleComponent.setAlignmentX(CENTER_ALIGNMENT);

        staffPanel = new StaffPanel(scoreView);
        staffPanel.setAlignmentX(CENTER_ALIGNMENT);

        textPanel = new TextPanel(scoreView);
        textPanel.setAlignmentX(CENTER_ALIGNMENT);

        footnotesComponent = new FootnotesComponent(scoreView);
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

    private int scoreMarginTopPx() {
        return scoreView.getViewScale().toViewPx(SCORE_MARGIN_TOP_SS).positionPx();
    }

    /**
     * Reports its height on demand, not fixed at construction like {@link Box#createVerticalStrut}:
     * the height is zoom-dependent, and a fixed strut shifted the score block vertically.
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
     * Call after the score view installs a new song.
     *
     * @effects every line panel is rebuilt from the new song, and every child measures it
     *          at the next layout
     */
    public void songDidChange() {
        staffPanel.rebuildLayout();
        textPanel.songDidChange();
        titleComponent.invalidate();
        subtitleComponent.invalidate();
        footnotesComponent.invalidate();
        revalidate();
        repaint();
    }

    public TitleComponent getTitleComponent() {
        return titleComponent;
    }

    public TitleComponent getSubtitleComponent() {
        return subtitleComponent;
    }

    public StaffPanel getStaffPanel() {
        return staffPanel;
    }

    public TextPanel getTextPanel() {
        return textPanel;
    }

    public FootnotesComponent getFootnotesComponent() {
        return footnotesComponent;
    }

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

    @Nullable
    public LinePanel getLinePanel(int index) {
        return staffPanel.getLinePanel(index);
    }

    @Override
    public Dimension getPreferredSize() {
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
