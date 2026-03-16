package songscribe.message;

public class Message {
    public static final int HIGH_PRIORITY = 27;
    public static final int MEDIUM_PRIORITY = 13;
    public static final int LOW_PRIORITY = 0;

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }
}
