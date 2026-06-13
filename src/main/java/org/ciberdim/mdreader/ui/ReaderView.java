package org.ciberdim.mdreader.ui;

import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.scene.input.ScrollEvent;
import javafx.print.PrinterJob;
import javafx.stage.Window;

/**
 * Handles the display area of the application. Integrates a WebView
 * for rendering parsed Markdown files, and overlays a modern placeholder
 * dashboard when no document is active.
 */
public class ReaderView extends StackPane {

    private final WebView webView;
    private final WebEngine webEngine;
    private final VBox placeholderPane;
    private final VBox spinnerPane;
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

        // 1.5. Spinner Pane
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(50, 50);

        Label loadingLabel = new Label("Loading document...");
        loadingLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: -color-text-muted;");

        spinnerPane = new VBox(12, progressIndicator, loadingLabel);
        spinnerPane.setAlignment(Pos.CENTER);
        spinnerPane.setPadding(new Insets(30));
        spinnerPane.setVisible(false);

        // 2. WebView Settings
        // Ensure webview matches context backgrounds
        webView.setContextMenuEnabled(false);

        // Zoom function via Ctrl + Mouse Wheel
        webView.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                double direction = event.getDeltaY();
                if (direction != 0) {
                    double currentZoom = webView.getZoom();
                    double zoomFactor = direction > 0 ? 1.1 : 0.9;
                    double newZoom = currentZoom * zoomFactor;
                    // Cap zoom between 0.5 (50%) and 3.0 (300%)
                    if (newZoom >= 0.5 && newZoom <= 3.0) {
                        webView.setZoom(newZoom);
                    }
                }
                event.consume();
            }
        });

        // Notify when loading completes
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                spinnerPane.setVisible(false);
                webView.setVisible(true);
                if (onLoadSucceeded != null) {
                    onLoadSucceeded.run();
                }
            } else if (newState == Worker.State.FAILED || newState == Worker.State.CANCELLED) {
                spinnerPane.setVisible(false);
            }
        });

        // Assemble: Stack WebView on bottom, placeholder and spinner on top
        this.getChildren().addAll(webView, placeholderPane, spinnerPane);
        showPlaceholder();
    }

    /**
     * Loads and displays the HTML document in the WebView, hiding the placeholder.
     * 
     * @param htmlContent the fully-formed HTML string to render
     */
    public void showContent(String htmlContent) {
        placeholderPane.setVisible(false);
        webView.setVisible(false);
        spinnerPane.setVisible(true);
        webEngine.loadContent(htmlContent);
    }

    /**
     * Clears the active document and displays the default welcome placeholder.
     */
    public void showPlaceholder() {
        webView.setVisible(false);
        spinnerPane.setVisible(false);
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

    /**
     * Prints the current document loaded in the WebView using the system printing dialog.
     * 
     * @param owner the owner window of the print dialog
     */
    public void print(Window owner) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null) {
            if (job.showPrintDialog(owner)) {
                webEngine.print(job);
                job.endJob();
            }
        }
    }
}
