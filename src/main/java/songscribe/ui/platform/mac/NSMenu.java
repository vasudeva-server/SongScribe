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
import org.rococoa.cocoa.foundation.NSInteger;

/**
 * Minimal Rococoa wrapper for NSMenu — exposes only the methods
 * needed to locate menu items by title or index.
 */
public abstract class NSMenu implements ObjCObject {

    @SuppressWarnings("unused")
    private static final _Class CLASS = Rococoa.createClass("NSMenu", _Class.class);

    public interface _Class extends ObjCClass {

        NSMenu alloc();
    }

    public abstract String title();

    public abstract NSInteger numberOfItems();

    public abstract NSMenuItem itemAtIndex(NSInteger index);

    public abstract NSMenuItem itemWithTitle(String title);

    public abstract void setAutoenablesItems(boolean flag);
}
