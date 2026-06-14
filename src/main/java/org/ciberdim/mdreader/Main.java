package org.ciberdim.mdreader;

/**
 * Bootstrap launcher class. Calls the primary App launcher
 * to circumvent JavaFX runtime modules checks when running on classpath.
 */
public class Main {
    /**
     * Bootstrap main method.
     * 
     * @param args command line arguments
     */
    public static void main(String[] args) {
        App.main(args);
    }
}
