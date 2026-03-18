package songscribe.ui;

import module java.desktop;


/**
 * Abstraction over static FlatLaf operations to enable test mocking.
 */
interface LafOperations {
    void installLaf(LookAndFeel laf) throws UnsupportedLookAndFeelException;

    void showSnapshot();

    void updateUI();

    void hideSnapshotWithAnimation();
}
