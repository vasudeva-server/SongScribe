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
package songscribe.io;

/**
 * Builds XML tag text for hand-written test fixtures.
 *
 * <p>Fixtures that spell their tags out as literals drift silently: rename an
 * element in the codec's vocabulary and the fixture keeps its old spelling, the
 * reader stops recognising that element, and the test carries on passing while no
 * longer exercising what it names. Composing the tags from the same constants the
 * codec uses removes that gap.
 *
 * <p>Public so fixtures outside this package can use it too.
 */
public final class XmlFixtures {

    private XmlFixtures() {}

    public static String openTag(String name) {
        return '<' + name + '>';
    }

    public static String closeTag(String name) {
        return "</" + name + '>';
    }

    public static String element(String name, String content) {
        return openTag(name) + content + closeTag(name);
    }

    /** An {@code name="value"} attribute pair, for splicing into an opening tag. */
    public static String attr(String name, String value) {
        return name + "=\"" + value + '"';
    }

    public static String openTag(String name, String attrName, String attrValue) {
        return '<' + name + ' ' + attr(attrName, attrValue) + '>';
    }

    public static String emptyTag(String name, String attrName, String attrValue) {
        return '<' + name + ' ' + attr(attrName, attrValue) + "/>";
    }
}
