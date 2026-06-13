package org.ciberdim.mdreader.ui;

import javafx.application.Platform;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Handles the display area of the application. Integrates a WebView
 * for rendering parsed Markdown files, and overlays a modern placeholder
 * dashboard when no document is active.
 */
public class ReaderView extends StackPane {

    private static final Logger logger = LoggerFactory.getLogger(ReaderView.class);
    private static final double[] ZOOM_LEVELS = {0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 3.0};

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
        
        SVGPath logo = IconFactory.createIcon(IconFactory.PATH_LOGO, null);
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
        webView.setContextMenuEnabled(false);

        // Zoom function via Ctrl + Mouse Wheel (using predefined steps)
        webView.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                double direction = event.getDeltaY();
                if (direction != 0) {
                    double currentZoom = webView.getZoom();
                    double newZoom = getNearestZoomLevel(currentZoom, direction > 0);
                    webView.setZoom(newZoom);
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

        // Assemble
        this.getChildren().addAll(webView, placeholderPane, spinnerPane);
        showPlaceholder();
    }

    private double getNearestZoomLevel(double currentZoom, boolean zoomIn) {
        if (zoomIn) {
            for (double level : ZOOM_LEVELS) {
                if (level > currentZoom + 0.01) return level;
            }
            return ZOOM_LEVELS[ZOOM_LEVELS.length - 1];
        } else {
            for (int i = ZOOM_LEVELS.length - 1; i >= 0; i--) {
                if (ZOOM_LEVELS[i] < currentZoom - 0.01) return ZOOM_LEVELS[i];
            }
            return ZOOM_LEVELS[0];
        }
    }

    /**
     * Loads and displays the HTML document in the WebView, hiding the placeholder.
     * 
     * @param htmlContent the fully-formed HTML string to render
     */
    public void showContent(String htmlContent) {
        Platform.runLater(() -> {
            placeholderPane.setVisible(false);
            webView.setVisible(false);
            spinnerPane.setVisible(true);
            webEngine.loadContent(htmlContent);
        });
    }

    /**
     * Clears the active document and displays the default welcome placeholder.
     */
    public void showPlaceholder() {
        Platform.runLater(() -> {
            webView.setVisible(false);
            spinnerPane.setVisible(false);
            placeholderPane.setVisible(true);
            webEngine.loadContent("about:blank");
        });
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
            logger.error("Failed to scroll to anchor", e);
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
