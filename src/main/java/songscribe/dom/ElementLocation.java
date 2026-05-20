package songscribe.dom;

/**
 * Identifies a specific element by its line index within a song
 * and its element index within that line. Both indices are non-negative
 * when the location is valid; absence is expressed by a null reference
 * to an {@code ElementLocation}, never by a sentinel value here.
 */
public record ElementLocation(int lineIndex, int elementIndex) {
    public ElementLocation {
        if (lineIndex < 0 || elementIndex < 0) {
            throw new IllegalArgumentException(
                "ElementLocation indices must be non-negative; got ("
                    + lineIndex + ", " + elementIndex + ')');
        }
    }

    public boolean matches(int lineIndex, int elementIndex) {
        return this.lineIndex == lineIndex && this.elementIndex == elementIndex;
    }
}
