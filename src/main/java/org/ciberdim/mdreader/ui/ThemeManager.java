package org.ciberdim.mdreader.ui;

import javafx.scene.Scene;
import javafx.scene.web.WebView;

/**
 * Manages the application-wide theme (Light vs Dark mode).
 * Coordinates theme changes between the JavaFX scene stylesheet
 * and the rendered HTML document inside the WebView.
 */
public class ThemeManager {
    private boolean darkMode = false;

    /**
     * Constructs a ThemeManager with Light Mode as default.
     */
    public ThemeManager() {
        // Light mode is default
    }

    /**
     * Toggles the current theme.
     */
    public void toggleTheme() {
        this.darkMode = !this.darkMode;
    }

    /**
     * Checks if Dark Mode is currently active.
     * 
     * @return true if dark mode is active, false otherwise
     */
    public boolean isDarkMode() {
        return darkMode;
    }

    /**
     * Applies the current theme to the JavaFX scene and the active WebView.
     * 
     * @param scene the primary JavaFX scene
     * @param webView the WebView displaying the HTML content
     */
    public void applyTheme(Scene scene, WebView webView) {
        if (scene != null && scene.getRoot() != null) {
            scene.getRoot().getStyleClass().remove("dark-mode");
            if (darkMode) {
                scene.getRoot().getStyleClass().add("dark-mode");
            }
        }
        applyThemeToWebView(webView);
    }

    /**
     * Injects JavaScript into the WebView to update its classes and stylesheets
     * to match the current active theme.
     * 
     * @param webView the WebView to update
     */
    public void applyThemeToWebView(WebView webView) {
        if (webView == null) {
            return;
        }

        String bodyClass = darkMode ? "dark-theme" : "light-theme";
        String highlightCssUrl = darkMode 
            ? "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github-dark.min.css"
            : "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github.min.css";

        try {
            // Check if document is available
            Object bodyObj = webView.getEngine().executeScript("document.body");
            if (bodyObj != null) {
                webView.getEngine().executeScript("document.body.className = '" + bodyClass + "';");
                webView.getEngine().executeScript(
                    "var link = document.getElementById('highlight-style');" +
                    "if (link) { link.setAttribute('href', '" + highlightCssUrl + "'); }"
                );
            }
        } catch (Exception e) {
            // WebView might not have loaded a document yet; fail silently.
        }
    }
}
