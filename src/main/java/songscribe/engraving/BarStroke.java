package songscribe.engraving;

/**
 * One stroke a barline or repeat sign is built from, left to right — a thin line, a
 * thick line, or a repeat-dots pair — each carrying its own width in staff spaces.
 */
public enum BarStroke {
    THIN(LineThickness.THIN_BARLINE_SS),
    THICK(LineThickness.THICK_BARLINE_SS),
    DOTS(SMuFLConstants.REPEAT_DOTS_ADVANCE_WIDTH_SS);

    private final double widthSs;

    BarStroke(double widthSs) {
        this.widthSs = widthSs;
    }

    public double widthSs() {
        return widthSs;
    }
}
