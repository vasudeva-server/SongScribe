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
 * <p>The terminal policy: a {@code FINAL_DOUBLE_BARLINE} exists only in the terminal position,
 * and the auto-maintained terminal may not be removed, only replaced by another valid terminal
 * type (the exemption {@link #replaceTerminal} relies on). The policy is not enforced while
 * mutation tracking is suspended, while this class is maintaining the invariant, or during a
 * replay, because a load and a replay pass through states that violate the invariant on the way
 * to one that holds.
 */
public final class TerminalMaintainer {

    private final Song song;

    TerminalMaintainer(Song song) {
        this.song = song;
    }

    public static StaffElement newTerminalElement(ElementType type) {
        requireValidTerminal(type);
        return type.newInstance();
    }

    private static StaffElement terminalElementReplacing(ElementType type, StaffElement displaced) {
        requireValidTerminal(type);
        return StaffElement.convertedFrom(type, displaced);
    }

    private static void requireValidTerminal(ElementType type) {
        if (!type.isValidSongTerminal()) {
            throw new IllegalArgumentException("Not a valid terminal type: " + type);
        }
    }

    /**
     * Must be called inside an open modification bracket with
     * {@link ModificationSession#withAutoMaintenance} raised so the terminal policy does not
     * reject the internally-driven mutations.
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
            newLastLine.setElement(
                lastIdx,
                terminalElementReplacing(typeToInstall, newLastLine.getElement(lastIdx)));
        } else {
            newLastLine.addElement(newTerminalElement(typeToInstall));
        }
    }

    /**
     * Must be called while mutation tracking is still suspended, so the fix-up is silent. A no-op
     * when the last line already ends with a valid terminal, so a span ending on that barline
     * keeps its exact element reference.
     */
    public void installAfterParsing() {
        var lines = song.getLines();

        if (lines.isEmpty()) {
            return;
        }

        var lastLine = lines.getLast();
        var lastIdx = lastLine.elementCount() - 1;

        if (lastIdx >= 0 && lastLine.getElement(lastIdx).getType().isValidSongTerminal()) {
            return;
        }

        maintainOnLastLineChange(null, lastLine);
    }

    public boolean isTerminal(StaffElement element, Line line) {
        var lines = song.getLines();
        var lastIdx = line.elementCount() - 1;
        return lastIdx >= 0
            && element.getType().isValidSongTerminal()
            && !lines.isEmpty()
            && lines.getLast() == line
            && line.getElement(lastIdx) == element;
    }

    public void checkTerminalPolicyForInsertion(Line line, int index, ElementType incoming) {
        if (isPolicyBypassed() || incoming != ElementType.FINAL_DOUBLE_BARLINE) {
            return;
        }

        if (!line.isLast() || index != line.elementCount()) {
            throw new IllegalStateException(
                "FINAL_DOUBLE_BARLINE may only be appended to the last line");
        }
    }

    public void checkTerminalPolicyForReplacement(Line line, int index, ElementType incoming) {
        if (isPolicyBypassed()) {
            return;
        }

        if (incoming == ElementType.FINAL_DOUBLE_BARLINE
            && (!line.isLast() || index != line.elementCount() - 1)) {
            throw new IllegalStateException(
                "FINAL_DOUBLE_BARLINE may only replace the last element on the last line");
        }

        if (isTerminal(line.getElement(index), line) && !incoming.isValidSongTerminal()) {
            throw new IllegalStateException(
                "The auto-maintained terminal may only be replaced by a valid terminal");
        }
    }

    public void checkTerminalPolicyForRemoval(Line line, StaffElementRun.ElementRange range) {
        if (isPolicyBypassed()) {
            return;
        }

        for (var index = range.begin(); index <= range.end(); index++) {
            if (isTerminal(line.getElement(index), line)) {
                throw new IllegalStateException("The auto-maintained terminal may not be removed");
            }
        }
    }

    private boolean isPolicyBypassed() {
        return song.isMutationTrackingSuspended()
            || song.isInAutoMaintenance()
            || song.isReplaying();
    }

    public ElementType currentTerminalType() {
        var lastLine = song.getLines().getLast();
        var lastIdx = lastLine.elementCount() - 1;

        if (lastIdx < 0) {
            throw RuntimeError.exit("Terminal invariant violated: last line is empty");
        }

        return lastLine.getElement(lastIdx).getType();
    }

    public boolean canReplaceTerminal(ElementType incomingType) {
        return incomingType.isValidSongTerminal() && incomingType != currentTerminalType();
    }

    public void replaceTerminal(ElementType incomingType) {
        if (!incomingType.isValidSongTerminal()) {
            throw new IllegalArgumentException("Not a valid terminal type: " + incomingType);
        }

        if (incomingType == currentTerminalType()) {
            return;
        }

        var lastLine = song.getLines().getLast();
        var lastIdx = lastLine.elementCount() - 1;

        // Copied from the outgoing terminal rather than built bare, so an annotation on it
        // survives the retype — retyping an ordinary barline already preserves attachments.
        var replacement = terminalElementReplacing(incomingType, lastLine.getElement(lastIdx));

        song.withModification(() -> lastLine.setElement(lastIdx, replacement));
    }

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
        return type.isValidSongTerminal() ? type : null;
    }

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
