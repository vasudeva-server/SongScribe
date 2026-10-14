package songscribe.dom;

/**
 * Absence is expressed by a null reference to an {@code ElementLocation}, never by a sentinel
 * value here.
 */
public record ElementLocation(int lineIndex, int elementIndex) {}
