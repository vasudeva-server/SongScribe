package songscribe.ui.dialog;

/**
 * Whether a dialog blocks input to the rest of the application while it is up.
 * <p>
 * This says nothing about window ownership: every dialog is owned by the main frame,
 * because an unowned window carries no menu bar.
 */
public enum Modality {

    /** Blocks the rest of the application. */
    MODAL,

    /** Leaves the rest of the application usable while it is up. */
    MODELESS;

    /**
     * @return {@code true} for {@link #MODAL}, which is the form Swing's window
     *     constructors take modality in
     */
    public boolean isModal() {
        return this == MODAL;
    }
}
