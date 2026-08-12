/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.ui.dialog.backend;

import songscribe.dom.Song;
import songscribe.font.DocumentFonts;

/**
 * The open document as {@link ScoreSongSettingsBackEnd} needs it: the song, and the two
 * document-wide settings that are written through the view rather than onto the song directly.
 *
 * <p><strong>Why an interface and not the view itself.</strong> {@code ScoreView} has all four
 * of these methods already and is the only implementation. It is also a {@code JComponent},
 * and naming it here would put a Swing type in this package's signatures — the one thing the
 * package promises not to do. Narrowing it to the four methods keeps that promise and makes
 * the back end answerable without a window.
 *
 * <p>The two writes are on the view because both have to re-lay out the score: fonts change
 * every measurement that depends on text, and the line width is the page's own geometry.
 * Both still record themselves on the song, so both are undoable in the ordinary way.
 */
public interface SongSettingsTarget {

    /**
     * @return the song being edited, which is a different song each time a document is opened
     */
    Song getSong();

    /**
     * @return the document's fonts as they now stand — the live holder, not a copy, so a
     *         caller that intends to change one copies it first
     */
    DocumentFonts getDocumentFonts();

    /**
     * Replaces the document's fonts and re-lays out everything measured with them.
     *
     * <p>Does nothing at all when {@code fonts} equals the current set, so an OK that changed
     * no font records no mutation. Otherwise records one font mutation however many roles
     * differ, so a commit that changes several is one undo step.
     *
     * @param fonts the complete set of eight roles, replacing the current set
     */
    void setFonts(DocumentFonts fonts);

    /**
     * Stores {@code lineWidthSs} on the song and re-lays out the page for it.
     *
     * <p>The width is stored exactly as given rather than by way of a pixel count, so a width
     * the user did not change survives the round trip unquantized.
     *
     * @param lineWidthSs the line width in staff spaces, within the page's supported range
     */
    void updatePageLayout(double lineWidthSs);
}
