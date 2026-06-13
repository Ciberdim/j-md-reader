package org.ciberdim.mdreader.ui;

import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

/**
 * Handles the display area of the application. Integrates a WebView
 * for rendering parsed Markdown files, and overlays a modern placeholder
 * dashboard when no document is active.
 */
public class ReaderView extends StackPane {

    private final WebView webView;
    private final WebEngine webEngine;
    private final VBox placeholderPane;
    private Runnable onLoadSucceeded;

    /**
     * Constructs the ReaderView and its nested components.
     */
    public ReaderView() {
        this.webView = new WebView();
        this.webEngine = webView.getEngine();

        // 1. Placeholder Pane
        placeholderPane = new VBox(15);
        placeholderPane.setAlignment(Pos.CENTER);
        placeholderPane.setPadding(new Insets(30));
        
        // Beautiful logo/icon using SVG
        SVGPath logo = new SVGPath();
        logo.setContent("M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-5 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V7h10v2z");
        logo.setScaleX(2.5);
        logo.setScaleY(2.5);
        logo.setStyle("-fx-fill: -color-text-muted;");

        Label titleLabel = new Label("Markdown Reader");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: -color-text-main;");

        Label descLabel = new Label("Drag and drop a Markdown file here, or select a file to view it.");
        descLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: -color-text-muted; -fx-text-alignment: center;");
        descLabel.setWrapText(true);

        placeholderPane.getChildren().addAll(logo, titleLabel, descLabel);

        // 2. WebView Settings
        // Ensure webview matches context backgrounds
        webView.setContextMenuEnabled(false);

        // Notify when loading completes
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                if (onLoadSucceeded != null) {
                    onLoadSucceeded.run();
                }
            }
        });

        // Assemble: Stack WebView on bottom, placeholder on top
        this.getChildren().addAll(webView, placeholderPane);
        showPlaceholder();
    }

    /**
     * Loads and displays the HTML document in the WebView, hiding the placeholder.
     * 
     * @param htmlContent the fully-formed HTML string to render
     */
    public void showContent(String htmlContent) {
        placeholderPane.setVisible(false);
        webView.setVisible(true);
        webEngine.loadContent(htmlContent);
    }

    /**
     * Clears the active document and displays the default welcome placeholder.
     */
    public void showPlaceholder() {
        webView.setVisible(false);
        placeholderPane.setVisible(true);
        webEngine.loadContent("about:blank");
    }

    /**
     * Executes JavaScript inside the WebView to smoothly scroll to a specific heading anchor.
     * 
     * @param anchorId the HTML id attribute of the target element
     */
    public void scrollToAnchor(String anchorId) {
        if (anchorId == null || anchorId.isBlank()) {
            return;
        }
        
        try {
            webEngine.executeScript(
                "var el = document.getElementById('" + anchorId + "');" +
                "if (el) { el.scrollIntoView({behavior: 'smooth', block: 'start'}); }"
            );
        } catch (Exception e) {
            System.err.println("Failed to scroll to anchor: " + e.getMessage());
        }
    }

    /**
     * Registers a callback that runs when the WebView finishes rendering.
     * 
     * @param onLoadSucceeded the Runnable callback
     */
    public void setOnLoadSucceeded(Runnable onLoadSucceeded) {
        this.onLoadSucceeded = onLoadSucceeded;
    }

    /**
     * Gets the WebView component.
     * 
     * @return the active WebView instance
     */
    public WebView getWebView() {
        return webView;
    }
}
