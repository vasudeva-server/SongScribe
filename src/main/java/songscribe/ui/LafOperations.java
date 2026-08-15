package songscribe.ui;

import javax.swing.LookAndFeel;
import javax.swing.UnsupportedLookAndFeelException;

/**
 * Abstraction over static FlatLaf operations to enable test mocking.
 */
interface LafOperations {
    void installLaf(LookAndFeel laf) throws UnsupportedLookAndFeelException;

    void showSnapshot();

    void updateUI();

    void hideSnapshotWithAnimation();
}
