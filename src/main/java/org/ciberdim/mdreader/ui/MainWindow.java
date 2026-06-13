package org.ciberdim.mdreader.ui;

import org.ciberdim.mdreader.model.HeadingInfo;
import org.ciberdim.mdreader.model.MarkdownDocument;
import org.ciberdim.mdreader.model.RecentFilesManager;
import org.ciberdim.mdreader.parser.MarkdownParser;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.List;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;

/**
 * Main application layout. Manages the orchestration between the
 * Sidebar outline navigation, the HTML Reader view, drag-and-drop actions,
 * native menu commands, and application theme styling.
 */
public class MainWindow extends BorderPane {

    private final ThemeManager themeManager;
    private final MarkdownParser markdownParser;
    private final RecentFilesManager recentFilesManager;

    private final SidebarView sidebarView;
    private final ReaderView readerView;
    private final SplitPane splitPane;

    private final StringProperty windowTitle = new SimpleStringProperty("Markdown Reader");
    private final BooleanProperty documentLoaded = new SimpleBooleanProperty(false);
    private final BooleanProperty isModified = new SimpleBooleanProperty(false);
    private MarkdownDocument activeDocument;

    /**
     * Constructs the MainWindow.
     */
    public MainWindow() {
        this.themeManager = new ThemeManager();
        this.markdownParser = new MarkdownParser();
        this.recentFilesManager = new RecentFilesManager();

        this.sidebarView = new SidebarView(recentFilesManager);
        this.readerView = new ReaderView();

        // 1. SplitPane Setup (Sidebar | Reader)
        splitPane = new SplitPane();
        splitPane.getItems().addAll(sidebarView, readerView);
        splitPane.setDividerPositions(0.25);
        SplitPane.setResizableWithParent(sidebarView, false);

        // 2. Menu Bar Setup
        MenuBar menuBar = createMenuBar();

        // Assemble Layout
        this.setTop(menuBar);
        this.setCenter(splitPane);

        // 3. Register Callbacks
        sidebarView.setOnFileOpener(this::openFile);
        sidebarView.setOnThemeToggler(this::toggleTheme);
        sidebarView.setOnTocNavigator(heading -> readerView.scrollToAnchor(heading.anchorId()));
        readerView.setOnLoadSucceeded(() -> themeManager.applyThemeToWebView(readerView.getWebView()));
        isModified.addListener((obs, oldVal, newVal) -> updateWindowTitle());

        // 4. Drag and Drop Support
        setupDragAndDrop();
    }

    /**
     * Exposes the window title property for stage binding.
     * 
     * @return the StringProperty window title
     */
    public StringProperty windowTitleProperty() {
        return windowTitle;
    }

    /**
     * Opens, parses, and displays a Markdown file.
     * Registers the file in the recent history on success.
     * If the file is missing, provides an interactive prompt to purge it from history.
     * 
     * @param file the File to open
     */
    public void openFile(File file) {
        if (file == null) {
            return;
        }

        if (!file.exists()) {
            handleMissingFile(file);
            return;
        }

        try {
            MarkdownDocument doc = markdownParser.parse(file);
            readerView.showContent(doc.getHtmlContent());
            sidebarView.setOutline(doc.getHeadings());
            recentFilesManager.addFile(file);
            sidebarView.refreshRecentFiles();
            this.activeDocument = doc;
            this.isModified.set(false);
            updateWindowTitle();
            documentLoaded.set(true);
        } catch (IOException e) {
            showErrorAlert("Read Error", "Failed to open document: " + file.getName(), e.getMessage());
        }
    }

    /**
     * Clears the current document view and resets outline/title.
     */
    private void closeFile() {
        readerView.showPlaceholder();
        sidebarView.setOutline(List.of());
        this.activeDocument = null;
        this.isModified.set(false);
        updateWindowTitle();
        documentLoaded.set(false);
    }

    private void updateWindowTitle() {
        if (activeDocument == null) {
            windowTitle.set("Markdown Reader");
        } else {
            String name = activeDocument.getFile() == null ? "Imported HTML" : activeDocument.getFile().getName();
            windowTitle.set("Markdown Reader - " + name + (isModified.get() ? "*" : ""));
        }
    }

    private void saveFile() {
        if (activeDocument == null) return;
        if (activeDocument.getFile() == null) {
            saveFileAs();
        } else {
            writeToFile(activeDocument.getFile(), activeDocument.getRawContent());
        }
    }

    private void saveFileAs() {
        if (activeDocument == null) return;
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Markdown File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown Files (*.md)", "*.md"));
        
        File file = fileChooser.showSaveDialog(this.getScene().getWindow());
        if (file != null) {
            writeToFile(file, activeDocument.getRawContent());
            openFile(file); // Reload to update document references
        }
    }

    private void writeToFile(File file, String content) {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
            this.isModified.set(false);
            updateWindowTitle();
        } catch (IOException e) {
            showErrorAlert("Save Error", "Failed to save document: " + file.getName(), e.getMessage());
        }
    }

    /**
     * Prints the currently loaded document using the native printer job.
     */
    private void printDocument() {
        if (this.getScene() != null && this.getScene().getWindow() != null) {
            readerView.print(this.getScene().getWindow());
        }
    }

    /**
     * Opens a modal dialog showing the raw markdown content of the current document,
     * with an option to copy the content to the system clipboard.
     */
    private void showTextExportModal() {
        if (activeDocument == null) {
            return;
        }

        Stage modalStage = new Stage();
        modalStage.initModality(Modality.APPLICATION_MODAL);
        modalStage.initOwner(this.getScene().getWindow());
        modalStage.setTitle("Export Document for LLM");

        // Header Title
        Label titleLabel = new Label("Raw Markdown Content");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: -color-text-main;");

        // Copy button with clipboard icon
        Button copyButton = new Button("Copy to Clipboard");
        SVGPath copyIcon = new SVGPath();
        copyIcon.setContent("M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z");
        copyIcon.setStyle("-fx-fill: white;");
        copyButton.setGraphic(copyIcon);

        copyButton.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(activeDocument.getRawContent());
            clipboard.setContent(content);

            // Change to "Copied!" and show a checkmark icon temporarily
            copyButton.setText("Copied!");
            SVGPath checkIcon = new SVGPath();
            checkIcon.setContent("M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z");
            checkIcon.setStyle("-fx-fill: white;");
            copyButton.setGraphic(checkIcon);

            Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(2), event -> {
                copyButton.setText("Copy to Clipboard");
                copyButton.setGraphic(copyIcon);
            }));
            timeline.play();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(10, titleLabel, spacer, copyButton);
        header.setAlignment(Pos.CENTER_LEFT);

        // Center TextArea displaying content
        TextArea textArea = new TextArea(activeDocument.getRawContent());
        textArea.setWrapText(true);
        VBox.setVgrow(textArea, Priority.ALWAYS);

        // Footer close button
        Button closeButton = new Button("Close");
        closeButton.getStyleClass().add("button-secondary");
        closeButton.setOnAction(e -> modalStage.close());

        HBox footer = new HBox(closeButton);
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox layout = new VBox(15, header, textArea, footer);
        layout.setPadding(new Insets(15));
        layout.getStyleClass().add("root");
        if (themeManager.isDarkMode()) {
            layout.getStyleClass().add("dark-mode");
        }

        Scene scene = new Scene(layout, 650, 480);
        // Load same stylesheet
        java.net.URL cssResource = getClass().getResource("/css/styles.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }

        modalStage.setScene(scene);
        modalStage.setMinWidth(400);
        modalStage.setMinHeight(300);
        modalStage.showAndWait();
    }

    /**
     * Handles theme toggle changes for both JavaFX CSS styles and Webview HTML.
     */
    private void toggleTheme() {
        themeManager.toggleTheme();
        themeManager.applyTheme(this.getScene(), readerView.getWebView());
        sidebarView.updateThemeIcon(themeManager.isDarkMode());
    }

    /**
     * Collapses or displays the sidebar component.
     */
    private void toggleSidebar() {
        if (splitPane.getItems().contains(sidebarView)) {
            splitPane.getItems().remove(sidebarView);
        } else {
            splitPane.getItems().add(0, sidebarView);
            splitPane.setDividerPositions(0.25);
        }
    }

    /**
     * Sets up file drop listeners on the layout to support drag-and-drop imports.
     */
    private void setupDragAndDrop() {
        this.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        this.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles() && !db.getFiles().isEmpty()) {
                openFile(db.getFiles().get(0));
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    /**
     * Builds the main window native menu bar.
     */
    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();

        // File Menu
        Menu fileMenu = new Menu("File");
        MenuItem openItem = new MenuItem("Open File...");
        openItem.setAccelerator(KeyCombination.keyCombination("Shortcut+O"));
        openItem.setOnAction(e -> handleOpenFileChooser());

        MenuItem importHtmlItem = new MenuItem("Import HTML...");
        importHtmlItem.setOnAction(e -> handleImportHtmlChooser());

        MenuItem saveItem = new MenuItem("Save");
        saveItem.setAccelerator(KeyCombination.keyCombination("Shortcut+S"));
        saveItem.setOnAction(e -> saveFile());
        saveItem.disableProperty().bind(documentLoaded.not().or(isModified.not()));

        MenuItem saveAsItem = new MenuItem("Save As...");
        saveAsItem.setOnAction(e -> saveFileAs());
        saveAsItem.disableProperty().bind(documentLoaded.not());

        MenuItem closeItem = new MenuItem("Close File");
        closeItem.setAccelerator(KeyCombination.keyCombination("Shortcut+W"));
        closeItem.setOnAction(e -> closeFile());
        closeItem.disableProperty().bind(documentLoaded.not());

        MenuItem printItem = new MenuItem("Print...");
        printItem.setAccelerator(KeyCombination.keyCombination("Shortcut+P"));
        printItem.setOnAction(e -> printDocument());
        printItem.disableProperty().bind(documentLoaded.not());

        MenuItem exportItem = new MenuItem("Export as TXT (LLM)...");
        exportItem.setAccelerator(KeyCombination.keyCombination("Shortcut+E"));
        exportItem.setOnAction(e -> showTextExportModal());
        exportItem.disableProperty().bind(documentLoaded.not());

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> System.exit(0));

        fileMenu.getItems().addAll(openItem, importHtmlItem, saveItem, saveAsItem, closeItem, printItem, exportItem, exitItem);

        // View Menu
        Menu viewMenu = new Menu("View");
        MenuItem toggleSidebarItem = new MenuItem("Toggle Sidebar");
        toggleSidebarItem.setAccelerator(KeyCombination.keyCombination("Shortcut+B"));
        toggleSidebarItem.setOnAction(e -> toggleSidebar());

        MenuItem toggleThemeItem = new MenuItem("Toggle Dark Mode");
        toggleThemeItem.setAccelerator(KeyCombination.keyCombination("Shortcut+T"));
        toggleThemeItem.setOnAction(e -> toggleTheme());

        viewMenu.getItems().addAll(toggleSidebarItem, toggleThemeItem);

        menuBar.getMenus().addAll(fileMenu, viewMenu);
        return menuBar;
    }

    /**
     * Launches FileChooser in case native menu is used instead of sidebar button.
     */
    private void handleOpenFileChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Markdown File");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Markdown Files (*.md, *.markdown)", "*.md", "*.markdown"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File selectedFile = fileChooser.showOpenDialog(this.getScene().getWindow());
        if (selectedFile != null) {
            openFile(selectedFile);
        }
    }

    private void handleImportHtmlChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import HTML File");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("HTML Files (*.html, *.htm)", "*.html", "*.htm"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File selectedFile = fileChooser.showOpenDialog(this.getScene().getWindow());
        if (selectedFile != null) {
            importHtml(selectedFile);
        }
    }

    private void importHtml(File file) {
        try {
            String htmlContent = Files.readString(file.toPath());
            String markdownContent = FlexmarkHtmlConverter.builder().build().convert(htmlContent);
            
            MarkdownDocument doc = markdownParser.parseRaw(markdownContent, null);
            readerView.showContent(doc.getHtmlContent());
            sidebarView.setOutline(doc.getHeadings());
            
            this.activeDocument = doc;
            this.isModified.set(true); // Mark as unsaved
            updateWindowTitle();
            documentLoaded.set(true);
        } catch (IOException e) {
            showErrorAlert("Import Error", "Failed to import HTML: " + file.getName(), e.getMessage());
        }
    }

    /**
     * Prompts the user to delete a missing file reference from the recent list.
     */
    private void handleMissingFile(File file) {
        Alert alert = new Alert(
            Alert.AlertType.WARNING,
            "The file could not be found:\n" + file.getAbsolutePath() + "\n\nWould you like to remove it from history?",
            ButtonType.YES,
            ButtonType.NO
        );
        alert.setHeaderText("File Not Found");
        alert.setTitle("Warning");
        alert.initOwner(this.getScene().getWindow());

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                recentFilesManager.removeFile(file);
                sidebarView.refreshRecentFiles();
            }
        });
    }

    /**
     * Shows a standard warning alert with details.
     */
    private void showErrorAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.initOwner(this.getScene().getWindow());
        alert.showAndWait();
    }
}
