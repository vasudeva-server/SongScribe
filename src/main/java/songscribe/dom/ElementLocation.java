package songscribe.dom;

import java.util.Comparator;

/**
 * Absence is expressed by a null reference to an {@code ElementLocation}, never by a sentinel
 * value here.
 */
public record ElementLocation(int lineIndex, int elementIndex) {

    /** By line, then by element within the line. */
    public static final Comparator<ElementLocation> SONG_ORDER =
        Comparator.comparingInt(ElementLocation::lineIndex).thenComparingInt(ElementLocation::elementIndex);
}
