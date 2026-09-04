package songscribe.dom;

/**
 * Identifies a specific element by its line index within a song
 * and its element index within that line. Absence is expressed by a null reference
 * to an {@code ElementLocation}, never by a sentinel value here.
 */
public record ElementLocation(int lineIndex, int elementIndex) {}
