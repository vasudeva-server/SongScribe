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

package songscribe.dom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * The root of everything that lives in a staff line: a {@link StaffElement} occupying a column,
 * the children hung on one — an {@link Articulation}, an {@link Attachment} — and a
 * {@link Span} joining two columns. What they share is a place in the element tree and the
 * notator's manual offset from wherever layout puts them. An element's position itself lives
 * in the layout result, not on the element.
 *
 * <p><b>The parent line.</b> {@link #getParentLine()} is the authoritative answer to "is this
 * element live in the document?": {@code null} means in no line, whether never added or
 * detached by a removal, and no membership scan is needed to decide. Which line it answers
 * follows the element's kind:
 * <ul>
 *   <li>A {@code StaffElement} is in the line whose element list holds it, and that is a
 *       two-way tie holding at modification-bracket boundaries: {@code parentLine == L} exactly
 *       when {@code L}'s elements contain it. Inside a bracket a re-parent may briefly have
 *       attached the element to one line while another's list still holds it.</li>
 *   <li>A child shares its host's line, taking it when it is added to the host and whenever the
 *       host attaches or detaches, and losing it when removed from the host.</li>
 *   <li>A {@code Span} is never in a line this way: it answers {@code null} for its whole life,
 *       and {@code Span.isIn(Line)} derives its lines from its endpoints, which is what lets a
 *       cross-line span be in two lines at once. That derived relation is one-directional — a
 *       span in a line is in that line's span list, but a span whose endpoints are both
 *       detached is in no line and still sits in the list it was added to.</li>
 * </ul>
 *
 * <p><b>The parent element.</b> {@code null} for an element the line holds directly; the host
 * for a child. The element tree is built only from inside {@code dom}, through
 * {@link #addChild} and {@link #removeChild}, which own both pointers together.
 *
 * <p><b>The offsets.</b> Where the notator dragged the element from the position layout gave it,
 * in staff spaces: X positive to the right, Y positive downward, both 0 until adjusted. The
 * drawn position is the calculated position plus the offset.
 */
public abstract class LineElement {

    /**
     * {@code Line.attach} and {@code Line.detach} are the only writers for a staff element;
     * {@link #addChild}, {@link #removeChild} and {@link #propagateParentLine} the only ones
     * for a child. {@code Line.detach}'s {@code != this} guard is what makes the two-way tie in
     * the class Javadoc ordering-independent inside a bracket.
     */
    private @Nullable Line parentLine;

    private @Nullable LineElement parentElement;

    private double userXOffsetSs = 0;

    private double userYOffsetSs = 0;

    /** Child elements (for composite elements like notes with articulations). */
    private final List<LineElement> children = new ArrayList<>();

    // ========================================================================
    // Parent/Line Relationships
    // ========================================================================

    /**
     * Returns the line this element is in.
     *
     * @return the line, or {@code null} when the element is in no line — never added, detached
     *     by a removal, or a {@link Span}, which is in no line this way
     */
    public @Nullable Line getParentLine() {
        return parentLine;
    }

    /**
     * Sets the Line that contains this element. For an element in Line's {@code elements}
     * list the only callers are {@code Line.attach} and {@code Line.detach}; calling it
     * elsewhere breaks the two-way tie in the class Javadoc. Package-private
     * so the compiler enforces that, rather than leaving it to a reader of this comment.
     */
    void setParentLine(@Nullable Line parentLine) {
        this.parentLine = parentLine;
    }

    /** Pushes {@code line} down the child chain; sub-elements have no line of their own. */
    void propagateParentLine(@Nullable Line line) {
        for (var child : getChildren()) {
            child.setParentLine(line);
            child.propagateParentLine(line);
        }
    }

    /**
     * Returns the element this one is a child of.
     *
     * @return the host, or {@code null} for an element the line holds directly
     */
    public @Nullable LineElement getParentElement() {
        return parentElement;
    }

    /**
     * Sets the parent element. Package-private alongside {@link #setParentLine}: the
     * element tree is built by {@link #addChild} / {@link #removeChild}, which own both
     * pointers together.
     */
    void setParentElement(@Nullable LineElement parentElement) {
        this.parentElement = parentElement;
    }

    // ========================================================================
    // User Offsets
    // ========================================================================

    /**
     * Returns the notator's horizontal offset from the calculated position.
     *
     * @return the offset in staff spaces, positive to the right; 0 until adjusted
     */
    public double getUserXOffsetSs() {
        return userXOffsetSs;
    }

    /**
     * Sets the notator's horizontal offset from the calculated position.
     *
     * @param userXOffsetSs the offset in staff spaces, positive to the right
     */
    public void setUserXOffsetSs(double userXOffsetSs) {
        this.userXOffsetSs = userXOffsetSs;
    }

    /**
     * Returns the notator's vertical offset from the calculated position.
     *
     * @return the offset in staff spaces, positive downward; 0 until adjusted
     */
    public double getUserYOffsetSs() {
        return userYOffsetSs;
    }

    /**
     * Sets the notator's vertical offset from the calculated position.
     *
     * @param userYOffsetSs the offset in staff spaces, positive downward
     */
    public void setUserYOffsetSs(double userYOffsetSs) {
        this.userYOffsetSs = userYOffsetSs;
    }

    // ========================================================================
    // State Copying
    // ========================================================================

    /**
     * Copies this class's own state — the two user offsets — from {@code source} onto this
     * element.
     * <p>
     * Every route that copies one element's state onto another ends up here, so a field added
     * to this class is carried by all of them at once and no route can omit it. The routes are
     * {@link StaffElement#copyStateFrom}, {@link StaffElement#convertedFrom}, {@link Span#copy},
     * {@link Attachment#copy} and {@link Articulation#copy}. The
     * parent line, the parent element and the children are deliberately not copied: they are
     * the copy's place in the document, which its caller decides, not the state being copied.
     *
     * @param source the element whose base state to take
     * @effects replaces this element's base state; leaves its identity and its place in the
     *          document alone
     */
    protected final void copyBaseStateFrom(LineElement source) {
        userXOffsetSs = source.userXOffsetSs;
        userYOffsetSs = source.userYOffsetSs;
    }

    // ========================================================================
    // Child Management
    // ========================================================================

    /**
     * Returns an unmodifiable view of this element's children.
     */
    List<LineElement> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /**
     * Adds a child element. Package-private: the element tree is built only from inside
     * {@code dom}, which is what makes {@link Articulation#owner()}'s cast to
     * {@link NoteElement} sound.
     *
     * @param child The element to add
     */
    void addChild(LineElement child) {
        child.parentElement = this;
        child.parentLine = parentLine;
        children.add(child);
    }

    /**
     * Removes a child element.
     *
     * @param child The element to remove
     */
    void removeChild(LineElement child) {
        if (children.remove(child)) {
            child.parentElement = null;
            child.parentLine = null;
        }
    }
}
