package songscribe.ui.message;

import org.jetbrains.annotations.Nullable;

import songscribe.music.Line;

public class LayoutChangeMessage extends Message {

    public enum ChangeType {
        CONTENT,
        FONT,
        SIZE
    }

    public enum Section {
        TITLE,
        ATTRIBUTION,
        SCORE,
        LYRICS,
        BANGLA_LYRICS,
        TRANSLATION,
        FOOTNOTES
    }

    private final Section section;
    private final ChangeType changeType;
    private final boolean heightChanged;
    @Nullable private final Line line;

    public LayoutChangeMessage(Section section, ChangeType changeType, boolean heightChanged) {
        this(section, changeType, heightChanged, null);
    }

    public LayoutChangeMessage(Section section, ChangeType changeType, boolean heightChanged, @Nullable Line line) {
        this.section = section;
        this.changeType = changeType;
        this.heightChanged = heightChanged;
        this.line = line;
    }

    public static LayoutChangeMessage scoreContent() {
        return scoreContent(null);
    }

    public static LayoutChangeMessage scoreContent(@Nullable Line line) {
        return new LayoutChangeMessage(Section.SCORE, ChangeType.CONTENT, false, line);
    }

    public Section getSection() {
        return section;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public boolean isHeightChanged() {
        return heightChanged;
    }

    @Nullable
    public Line getLine() {
        return line;
    }

    @Override
    public String toString() {
        return super.toString() + "(section=" + section + ", changeType=" + changeType
            + ", heightChanged=" + heightChanged + ", line=" + line + ")";
    }
}
