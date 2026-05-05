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

package songscribe.ui.platform.mac;

import org.rococoa.ObjCClass;
import org.rococoa.ObjCObject;
import org.rococoa.Rococoa;

/**
 * Minimal Rococoa wrapper for NSApplication — exposes only the methods
 * needed to access the native application menu.
 */
@SuppressWarnings("ALL")
public abstract class NSApplication implements ObjCObject {

    private static final _Class CLASS = Rococoa.createClass("NSApplication", _Class.class);

    public interface _Class extends ObjCClass {

        NSApplication sharedApplication();
    }

    public static NSApplication sharedApplication() {
        return CLASS.sharedApplication();
    }

    public abstract NSMenu mainMenu();
}
