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
package songscribe.dom;

import org.jspecify.annotations.Nullable;

import songscribe.error.RuntimeError;

/**
 * Owns the song's terminal invariant: the last element of the last line is always a valid
 * terminal ({@code FINAL_DOUBLE_BARLINE} or {@code REPEAT_RIGHT}), and that element is the
 * one the user may not touch.
 *
 * <p>Three groups of responsibility:
 * <ul>
 *   <li><b>Maintenance</b> — {@link #maintainOnLastLineChange} restores the invariant after
 *       the last line of the song changes, and {@link #installAfterParsing} restores it in
 *       one pass at the end of a suspended file load.</li>
 *   <li><b>Recognition</b> — {@link #isAutoMaintainedTerminal} and {@link #isInteractable}
 *       tell the UI which element is the maintained terminal and therefore off limits.</li>
 *   <li><b>User replacement</b> — {@link #canReplaceTerminal} and {@link #replaceTerminal}
 *       let the user swap one valid terminal type for the other.</li>
 * </ul>
 *
 * <p>One is owned by each {@link Song}, which delegates its terminal API here.
 */
public final class TerminalMaintainer {

    private final Song song;

    TerminalMaintainer(Song song) {
        this.song = song;
    }

    /**
     * Returns a fresh element of the given terminal type. Throws
     * {@link IllegalArgumentException} if {@code type} is not a valid terminal
     * (i.e. {@link ElementType#isValidTerminal()} returns {@code false}).
     */
    public static StaffElement newTerminalElement(ElementType type) {
        if (!type.isValidTerminal()) {
            throw new IllegalArgumentException("Not a valid terminal type: " + type);
        }

        return type.newInstance();
    }

    /**
     * Maintains the terminal invariant after the last line of the song has
     * changed: the last element of the last line must be a valid terminal
     * ({@code FINAL_DOUBLE_BARLINE} or {@code REPEAT_RIGHT}). Must be called inside
     * an open modification bracket with {@link ModificationSession#withAutoMaintenance}
     * raised so the {@link Line} guards do not reject the internally-driven mutations.
     *
     * <p>Determines the terminal type to install via {@link #terminalTypeToInstall}
     * (carry over the outgoing terminal; else promote an existing {@code REPEAT_RIGHT}
     * on the new last line; else default to {@code FINAL_DOUBLE_BARLINE}). Strips the
     * terminal element — either type — off {@code previousLastLine}, then installs
     * the chosen type on {@code newLastLine}: no-op when {@code FINAL_DOUBLE_BARLINE}
     * is already in place, replacement when the existing last element is bar-like or
     * a {@code REPEAT_RIGHT} being promoted in place, append otherwise (including the
     * empty-line case).
     */
    public void maintainOnLastLineChange(@Nullable Line previousLastLine, Line newLastLine) {
        var outgoingTerminalType = outgoingTerminalType(previousLastLine, newLastLine);
        var typeToInstall = terminalTypeToInstall(outgoingTerminalType, newLastLine);

        //noinspection ConstantValue
        if (outgoingTerminalType != null && previousLastLine != null) {
            previousLastLine.removeElement(previousLastLine.elementCount() - 1);
        }

        var lastIdx = newLastLine.elementCount() - 1;

        if (lastIdx < 0) {
            newLastLine.addElement(newTerminalElement(typeToInstall));
            return;
        }

        var lastType = newLastLine.getElement(lastIdx).getType();

        // FINAL_DOUBLE_BARLINE is guard-locked to end-of-last-line, so when it is
        // already in place and is the type we want to install, no semantic change is
        // required. A REPEAT_RIGHT already sitting here, by contrast, is an interior
        // right-repeat being promoted to the terminal — fall through so the
        // replacement below emits an ElementReplacement for undo.
        if (lastType == ElementType.FINAL_DOUBLE_BARLINE
            && typeToInstall == ElementType.FINAL_DOUBLE_BARLINE) {
            return;
        }

        if (lastType.isReplaceableByTerminal()) {
            newLastLine.setElement(lastIdx, newTerminalElement(typeToInstall));
        } else {
            newLastLine.addElement(newTerminalElement(typeToInstall));
        }
    }

    /**
     * Restores the terminal invariant after a {@link Song#newParsingStub() parsing stub}
     * has been fully populated: ensures the song's last line ends with a valid
     * terminal ({@link ElementType#isValidTerminal()}). File readers suspend mutation
     * tracking while building lines, so the per-{@code addLine} maintenance is skipped;
     * this restores the invariant in one pass at the end of a load.
     *
     * <p>Must be called while mutation tracking is still suspended so the fix-up is
     * silent (no notification, no {@code modified} flag) and the {@link Line} terminal
     * guards stay bypassed. A no-op when the last line already ends with a valid
     * terminal, so a range span (e.g. an {@code Ending} ending on that barline) keeps
     * its exact element reference.
     */
    public void installAfterParsing() {
        var lines = song.getLines();

        if (lines.isEmpty()) {
            return;
        }

        var lastLine = lines.getLast();
        var lastIdx = lastLine.elementCount() - 1;

        if (lastIdx >= 0 && lastLine.getElement(lastIdx).getType().isValidTerminal()) {
            return;
        }

        maintainOnLastLineChange(null, lastLine);
    }

    /**
     * Returns {@code true} when {@code element} is the song's auto-maintained
     * terminal: it occupies the last position of the last line, and its type satisfies
     * {@link ElementType#isValidTerminal()}.
     *
     * <p>A valid terminal type that sits on any line other than the last, or at any
     * position other than the last, is treated as an ordinary (interactable) element.
     */
    public boolean isAutoMaintainedTerminal(StaffElement element, Line line) {
        var lines = song.getLines();
        var lastIdx = line.elementCount() - 1;
        return lastIdx >= 0
            && element.getType().isValidTerminal()
            && !lines.isEmpty()
            && lines.getLast() == line
            && line.getElement(lastIdx) == element;
    }

    /**
     * Returns {@code true} when the user may interact with {@code element} on {@code line}
     * (select, click, drag, delete, etc.). Returns {@code false} only for the
     * song's auto-maintained terminal — i.e., a {@link ElementType#isValidTerminal()
     * valid terminal} element that is the last element of the last line.
     */
    public boolean isInteractable(StaffElement element, Line line) {
        return !isAutoMaintainedTerminal(element, line);
    }

    /** Returns the type of the current auto-maintained terminal element. */
    public ElementType currentTerminalType() {
        var lastLine = song.getLines().getLast();
        var lastIdx = lastLine.elementCount() - 1;

        if (lastIdx < 0) {
            throw RuntimeError.exit("Terminal invariant violated: last line is empty");
        }

        return lastLine.getElement(lastIdx).getType();
    }

    /**
     * Returns {@code true} when the terminal may be replaced with an element of the given
     * type: {@code incomingType} must be a valid terminal and must differ from the type
     * currently occupying the terminal slot.
     */
    public boolean canReplaceTerminal(ElementType incomingType) {
        return incomingType.isValidTerminal() && incomingType != currentTerminalType();
    }

    /**
     * Replaces the terminal element with a fresh element of {@code incomingType}.
     * This is a user-driven mutation — no auto-maintenance increment. No-op when
     * {@code incomingType} already matches the current terminal type. Throws
     * {@link IllegalArgumentException} if {@code incomingType} is not a valid terminal.
     */
    public void replaceTerminal(ElementType incomingType) {
        if (!incomingType.isValidTerminal()) {
            throw new IllegalArgumentException("Not a valid terminal type: " + incomingType);
        }

        if (incomingType == currentTerminalType()) {
            return;
        }

        var lastLine = song.getLines().getLast();
        var lastIdx = lastLine.elementCount() - 1;

        song.withModification(() -> lastLine.setElement(lastIdx, newTerminalElement(incomingType)));
    }

    /**
     * Returns the terminal type at the end of {@code previousLastLine} if it is the
     * outgoing terminal (non-null, distinct from {@code newLastLine}, and ends in a
     * valid terminal). Returns {@code null} otherwise.
     */
    @Nullable
    private static ElementType outgoingTerminalType(
        @Nullable Line previousLastLine, Line newLastLine
    ) {
        if (previousLastLine == null || previousLastLine == newLastLine) {
            return null;
        }

        var prevLastIdx = previousLastLine.elementCount() - 1;

        if (prevLastIdx < 0) {
            return null;
        }

        var type = previousLastLine.getElement(prevLastIdx).getType();
        return type.isValidTerminal() ? type : null;
    }

    /**
     * Determines which terminal type {@link #maintainOnLastLineChange} should
     * install on the new last line. Decision tree:
     * <ol>
     *   <li>If {@code outgoingTerminalType} is non-null, carry it over — preserves
     *       user intent across {@code addLine} / {@code removeLine}.
     *   <li>Otherwise, if the new last line already ends in a {@code REPEAT_RIGHT}
     *       (user-placed interior right-repeat), promote it in place.
     *   <li>Otherwise, default to {@code FINAL_DOUBLE_BARLINE}.
     * </ol>
     */
    private static ElementType terminalTypeToInstall(
        @Nullable ElementType outgoingTerminalType, Line newLastLine
    ) {
        if (outgoingTerminalType != null) {
            return outgoingTerminalType;
        }

        var lastIdx = newLastLine.elementCount() - 1;

        if (lastIdx >= 0
            && newLastLine.getElement(lastIdx).getType() == ElementType.REPEAT_RIGHT) {
            return ElementType.REPEAT_RIGHT;
        }

        return ElementType.FINAL_DOUBLE_BARLINE;
    }
}
