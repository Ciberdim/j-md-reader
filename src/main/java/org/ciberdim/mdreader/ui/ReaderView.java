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

import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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

    private final Button searchTriggerButton;
    private final HBox searchBarPane;
    private final TextField searchField;
    private final Label matchCountLabel;

    private int totalMatches = 0;
    private int currentMatchIndex = 0;

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

        // 3. Search UI Components (Initialized early so referenced variables are non-null in load listener)
        searchTriggerButton = new Button();
        searchTriggerButton.getStyleClass().add("btn-search-trigger");
        searchTriggerButton.setGraphic(IconFactory.createIcon(IconFactory.PATH_SEARCH, "icon-primary"));
        searchTriggerButton.setTooltip(new Tooltip("Find in page (Ctrl+F)"));
        searchTriggerButton.setOnAction(e -> showSearch());
        searchTriggerButton.setVisible(false); // Only visible when document is loaded

        searchBarPane = new HBox(8);
        searchBarPane.getStyleClass().add("search-bar-pane");
        searchBarPane.setAlignment(Pos.CENTER_LEFT);
        searchBarPane.setMaxSize(javafx.scene.layout.Region.USE_PREF_SIZE, javafx.scene.layout.Region.USE_PREF_SIZE);
        searchBarPane.setVisible(false);
        searchBarPane.setManaged(false);

        SVGPath searchIcon = IconFactory.createIcon(IconFactory.PATH_SEARCH, "icon-muted");
        
        searchField = new TextField();
        searchField.setPromptText("Find...");
        searchField.setPrefWidth(150);
        searchField.getStyleClass().add("search-input");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> runSearch(newVal));
        searchField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                if (event.isShiftDown()) {
                    prevMatch();
                } else {
                    nextMatch();
                }
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                hideSearch();
                event.consume();
            }
        });

        matchCountLabel = new Label("0 / 0");
        matchCountLabel.getStyleClass().add("search-match-count");
        matchCountLabel.setMinWidth(45);
        matchCountLabel.setAlignment(Pos.CENTER);

        Button prevButton = new Button();
        prevButton.getStyleClass().add("button-secondary");
        prevButton.setGraphic(IconFactory.createIcon(IconFactory.PATH_UP, "icon-primary"));
        prevButton.setTooltip(new Tooltip("Previous Match (Shift+Enter)"));
        prevButton.setOnAction(e -> prevMatch());

        Button nextButton = new Button();
        nextButton.getStyleClass().add("button-secondary");
        nextButton.setGraphic(IconFactory.createIcon(IconFactory.PATH_DOWN, "icon-primary"));
        nextButton.setTooltip(new Tooltip("Next Match (Enter)"));
        nextButton.setOnAction(e -> nextMatch());

        Button closeSearchButton = new Button();
        closeSearchButton.getStyleClass().add("button-secondary");
        closeSearchButton.setGraphic(IconFactory.createIcon(IconFactory.PATH_CLOSE, "icon-primary"));
        closeSearchButton.setTooltip(new Tooltip("Close (Escape)"));
        closeSearchButton.setOnAction(e -> hideSearch());

        searchBarPane.getChildren().addAll(
            searchIcon,
            searchField,
            matchCountLabel,
            prevButton,
            nextButton,
            closeSearchButton
        );

        // Notify when loading completes
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                spinnerPane.setVisible(false);
                webView.setVisible(true);
                searchTriggerButton.setVisible(true);
                if (onLoadSucceeded != null) {
                    onLoadSucceeded.run();
                }
            } else if (newState == Worker.State.FAILED || newState == Worker.State.CANCELLED) {
                spinnerPane.setVisible(false);
            }
        });

        // Assemble
        this.getChildren().addAll(webView, placeholderPane, spinnerPane, searchTriggerButton, searchBarPane);
        StackPane.setAlignment(searchTriggerButton, Pos.TOP_RIGHT);
        StackPane.setMargin(searchTriggerButton, new Insets(15));

        StackPane.setAlignment(searchBarPane, Pos.TOP_RIGHT);
        StackPane.setMargin(searchBarPane, new Insets(15));

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
            searchTriggerButton.setVisible(false);
            hideSearch();
            webEngine.loadContent(htmlContent);
        });
    }

    /**
     * Updates the inner HTML of the document smoothly using Javascript,
     * maintaining scroll position and avoiding the WebView reload flash.
     * 
     * @param bodyHtml the inner HTML string to inject into the markdown body
     */
    public void updateContentSmoothly(String bodyHtml) {
        Platform.runLater(() -> {
            try {
                String base64 = java.util.Base64.getEncoder().encodeToString(bodyHtml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                String script = 
                    "var body = document.querySelector('.markdown-body');\n" +
                    "if (body) {\n" +
                    "  body.innerHTML = decodeURIComponent(escape(window.atob('" + base64 + "')));\n" +
                    "  var codeBlocks = document.querySelectorAll('pre code');\n" +
                    "  codeBlocks.forEach(block => {\n" +
                    "      if (block.classList.contains('language-mermaid') || block.classList.contains('language-sequence')) {\n" +
                    "          const pre = block.parentElement;\n" +
                    "          const div = document.createElement('div');\n" +
                    "          div.className = 'mermaid';\n" +
                    "          let content = block.textContent.trim();\n" +
                    "          if (block.classList.contains('language-sequence') && !content.startsWith('sequenceDiagram')) {\n" +
                    "              content = 'sequenceDiagram\\n' + content;\n" +
                    "          }\n" +
                    "          div.textContent = content;\n" +
                    "          pre.replaceWith(div);\n" +
                    "      }\n" +
                    "  });\n" +
                    "  try { mermaid.init(undefined, document.querySelectorAll('.mermaid')); } catch (e) {}\n" +
                    "  try { hljs.highlightAll(); } catch (e) {}\n" +
                    "}";
                webEngine.executeScript(script);
            } catch (Exception e) {
                logger.error("Failed to update content smoothly", e);
                // Fallback to standard render if script execution fails for some reason
                showContent("<html><body>" + bodyHtml + "</body></html>"); // Minimal fallback
            }
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
            searchTriggerButton.setVisible(false);
            hideSearch();
            webEngine.loadContent("<html></html>");
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

    public void showSearch() {
        if (!webView.isVisible()) return;
        searchTriggerButton.setVisible(false);
        searchBarPane.setVisible(true);
        searchBarPane.setManaged(true);
        searchField.requestFocus();
        runSearch(searchField.getText());
    }

    public void hideSearch() {
        searchBarPane.setVisible(false);
        searchBarPane.setManaged(false);
        if (webView.isVisible()) {
            searchTriggerButton.setVisible(true);
        }
        searchField.clear();
        try {
            webEngine.executeScript("clearSearchHighlights();");
        } catch (Exception e) {
            // Ignore if engine is not loaded
        }
    }

    private void runSearch(String query) {
        if (query == null || query.isBlank()) {
            try {
                webEngine.executeScript("clearSearchHighlights();");
            } catch (Exception e) {}
            totalMatches = 0;
            currentMatchIndex = 0;
            matchCountLabel.setText("0 / 0");
            return;
        }

        try {
            Object result = webEngine.executeScript("searchAndHighlight('" + query.replace("'", "\\'") + "');");
            if (result instanceof Number) {
                totalMatches = ((Number) result).intValue();
                currentMatchIndex = totalMatches > 0 ? 1 : 0;
                updateMatchLabel();
            }
        } catch (Exception e) {
            logger.error("Failed to execute search script", e);
        }
    }

    private void nextMatch() {
        if (totalMatches == 0) return;
        try {
            Object result = webEngine.executeScript("nextSearchMatch();");
            if (result instanceof Number) {
                currentMatchIndex = ((Number) result).intValue() + 1;
                updateMatchLabel();
            }
        } catch (Exception e) {
            logger.error("Failed to go to next match", e);
        }
    }

    private void prevMatch() {
        if (totalMatches == 0) return;
        try {
            Object result = webEngine.executeScript("prevSearchMatch();");
            if (result instanceof Number) {
                currentMatchIndex = ((Number) result).intValue() + 1;
                updateMatchLabel();
            }
        } catch (Exception e) {
            logger.error("Failed to go to prev match", e);
        }
    }

    private void updateMatchLabel() {
        matchCountLabel.setText(currentMatchIndex + " / " + totalMatches);
    }
}
