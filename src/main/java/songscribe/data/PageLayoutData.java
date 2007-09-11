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
package songscribe.data;

import java.io.File;
import java.util.ArrayList;

import songscribe.ui.component.IMainFrame;

public class PageLayoutData {

    public ArrayList<File> files = null;
    public int paperWidth = 0, paperHeight = 0, leftInnerMargin =
        0, rightOuterMargin = 0, topMargin = 0, bottomMargin = 0;
    public boolean mirrored = false;
    public int songsPerPage = 2;
    public IMainFrame mainFrame = null;
}
