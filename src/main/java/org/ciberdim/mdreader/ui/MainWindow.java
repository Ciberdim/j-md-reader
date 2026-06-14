package org.ciberdim.mdreader.ui;

import org.ciberdim.mdreader.model.MarkdownDocument;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.scene.image.Image;

import java.io.File;

/**
 * Main application layout. Manages the orchestration between the
 * Sidebar outline navigation, the HTML Reader view, drag-and-drop actions,
 * native menu commands, and application theme styling.
 * Decoupled using WorkspaceController.
 */
public class MainWindow extends BorderPane {

    private final ThemeManager themeManager;
    private final WorkspaceController workspaceController;

    private final SidebarView sidebarView;
    private final ReaderView readerView;
    private final SplitPane splitPane;
    private Stage editStage;

    public MainWindow() {
        this.themeManager = new ThemeManager();
        this.workspaceController = new WorkspaceController();

        this.sidebarView = new SidebarView(workspaceController.getRecentFilesManager());
        this.readerView = new ReaderView();

        // 1. SplitPane Setup
        splitPane = new SplitPane();
        splitPane.getItems().addAll(sidebarView, readerView);
        splitPane.setDividerPositions(0.25);
        SplitPane.setResizableWithParent(sidebarView, false);

        // 2. Menu Bar Setup
        MenuBar menuBar = createMenuBar();

        this.setTop(menuBar);
        this.setCenter(splitPane);

        // 3. Register Callbacks and Bindings
        sidebarView.setOnFileOpener(this::openFile);
        sidebarView.setOnThemeToggler(this::toggleTheme);
        sidebarView.setOnTocNavigator(heading -> readerView.scrollToAnchor(heading.anchorId()));
        
        readerView.setOnLoadSucceeded(() -> themeManager.applyThemeToWebView(readerView.getWebView()));

        workspaceController.activeDocumentProperty().addListener((obs, oldDoc, newDoc) -> {
            Platform.runLater(() -> {
                if (newDoc != null) {
                    if (oldDoc != null && java.util.Objects.equals(oldDoc.getFile(), newDoc.getFile()) && readerView.getWebView().isVisible()) {
                        readerView.updateContentSmoothly(newDoc.getBodyHtml());
                        sidebarView.setOutline(newDoc.getHeadings());
                    } else {
                        readerView.showContent(newDoc.getHtmlContent());
                        sidebarView.setOutline(newDoc.getHeadings());
                        sidebarView.refreshRecentFiles();
                    }
                } else {
                    readerView.showPlaceholder();
                    sidebarView.setOutline(java.util.List.of());
                }
            });
        });

        // 4. Drag and Drop Support
        setupDragAndDrop();
    }

    public javafx.beans.property.StringProperty windowTitleProperty() {
        return workspaceController.windowTitleProperty();
    }

    /**
     * Opens a Markdown file in the application workspace.
     * 
     * @param file the Markdown file to load
     */
    public void openFile(File file) {
        workspaceController.openFile(file, null, this::showErrorAlertFromBackground);
    }

    private void closeFile() {
        workspaceController.closeFile();
    }

    private void saveFile() {
        MarkdownDocument doc = workspaceController.activeDocumentProperty().get();
        if (doc == null) return;
        if (doc.getFile() == null) {
            saveFileAs();
        } else {
            workspaceController.saveFile(doc.getFile(), null, this::showErrorAlertFromBackground);
        }
    }

    private void saveFileAs() {
        MarkdownDocument doc = workspaceController.activeDocumentProperty().get();
        if (doc == null) return;
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Markdown File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown Files (*.md)", "*.md"));
        
        File file = fileChooser.showSaveDialog(this.getScene().getWindow());
        if (file != null) {
            workspaceController.saveFile(file, null, this::showErrorAlertFromBackground);
        }
    }

    private void exportAsHtml() {
        MarkdownDocument doc = workspaceController.activeDocumentProperty().get();
        if (doc == null) return;
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export as HTML");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML Files (*.html)", "*.html"));
        
        File file = fileChooser.showSaveDialog(this.getScene().getWindow());
        if (file != null) {
            workspaceController.exportHtml(file, null, this::showErrorAlertFromBackground);
        }
    }

    private void importHtml() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import HTML File");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("HTML Files (*.html, *.htm)", "*.html", "*.htm"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File selectedFile = fileChooser.showOpenDialog(this.getScene().getWindow());
        if (selectedFile != null) {
            workspaceController.importHtml(selectedFile, null, this::showErrorAlertFromBackground);
        }
    }

    private void printDocument() {
        if (this.getScene() != null && this.getScene().getWindow() != null) {
            readerView.print(this.getScene().getWindow());
        }
    }

    private void showEditModal() {
        if (editStage != null && editStage.isShowing()) {
            editStage.toFront();
            return;
        }

        MarkdownDocument activeDocument = workspaceController.activeDocumentProperty().get();
        if (activeDocument == null) {
            return;
        }

        Stage modalStage = new Stage();
        editStage = modalStage;
        modalStage.initModality(Modality.NONE);
        modalStage.initOwner(this.getScene().getWindow());
        modalStage.setTitle("Edit Document");

        try {
            java.io.InputStream iconStream = getClass().getResourceAsStream("/images/mark_text.png");
            if (iconStream != null) {
                modalStage.getIcons().add(new Image(iconStream));
            }
        } catch (Exception e) {
            // Silently ignore loading errors
        }

        Label titleLabel = new Label("Raw Markdown Content");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: -color-text-main;");

        Button copyButton = new Button("Copy");
        copyButton.setGraphic(IconFactory.createIcon(IconFactory.PATH_COPY, null));

        copyButton.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(activeDocument.getRawContent());
            clipboard.setContent(content);

            copyButton.setText("Copied!");
            copyButton.setGraphic(IconFactory.createIcon(IconFactory.PATH_CHECK, null));

            Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(2), event -> {
                copyButton.setText("Copy");
                copyButton.setGraphic(IconFactory.createIcon(IconFactory.PATH_COPY, null));
            }));
            timeline.play();
        });

        Button resetButton = new Button("Reset Changes");
        resetButton.getStyleClass().add("button-danger");
        resetButton.setGraphic(IconFactory.createIcon(IconFactory.PATH_RELOAD, null));

        Button renderButton = new Button("Render Changes");
        renderButton.getStyleClass().add("button-success");
        renderButton.setGraphic(IconFactory.createIcon(IconFactory.PATH_PLAY, null));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label realTimeLabel = new Label("Real Time");
        realTimeLabel.setStyle("-fx-text-fill: -color-text-main;");
        ToggleSwitch realTimeToggle = new ToggleSwitch();
        HBox toggleContainer = new HBox(8, realTimeLabel, realTimeToggle);
        toggleContainer.setAlignment(Pos.CENTER);
        
        javafx.animation.PauseTransition renderPause = new javafx.animation.PauseTransition(Duration.seconds(4));

        renderButton.disableProperty().bind(realTimeToggle.selectedProperty());

        HBox header = new HBox(10, titleLabel, spacer, toggleContainer, resetButton, renderButton, copyButton);
        header.setAlignment(Pos.CENTER_LEFT);

        TextArea textArea = new TextArea(activeDocument.getRawContent());
        
        renderPause.setOnFinished(e -> workspaceController.updateDocumentContent(textArea.getText(), null, this::showErrorAlertFromBackground));
        
        realTimeToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                renderPause.stop();
            }
        });

        textArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (realTimeToggle.isSelected()) {
                renderPause.playFromStart();
            }
        });
        
        resetButton.setOnAction(e -> {
            textArea.setText(activeDocument.getRawContent());
            if (realTimeToggle.isSelected()) renderPause.playFromStart();
        });
        
        renderButton.setOnAction(e -> workspaceController.updateDocumentContent(textArea.getText(), null, this::showErrorAlertFromBackground));
        textArea.setWrapText(true);
        VBox.setVgrow(textArea, Priority.ALWAYS);

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

        Scene scene = new Scene(layout, 900, 650);
        java.net.URL cssResource = getClass().getResource("/css/styles.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }
        scene.getAccelerators().put(KeyCombination.keyCombination("Shortcut+W"), modalStage::close);

        modalStage.setScene(scene);
        modalStage.setMinWidth(600);
        modalStage.setMinHeight(450);
        modalStage.setOnHidden(e -> this.editStage = null);
        modalStage.show();
    }

    private void toggleTheme() {
        themeManager.toggleTheme();
        themeManager.applyTheme(this.getScene(), readerView.getWebView());
        sidebarView.updateThemeIcon(themeManager.isDarkMode());
    }

    private void toggleSidebar() {
        if (splitPane.getItems().contains(sidebarView)) {
            splitPane.getItems().remove(sidebarView);
        } else {
            splitPane.getItems().add(0, sidebarView);
            splitPane.setDividerPositions(0.25);
        }
    }

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

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();

        Menu fileMenu = new Menu("File");
        MenuItem openItem = new MenuItem("Open File...");
        openItem.setAccelerator(KeyCombination.keyCombination("Shortcut+O"));
        openItem.setOnAction(e -> handleOpenFileChooser());

        MenuItem saveItem = new MenuItem("Save");
        saveItem.setAccelerator(KeyCombination.keyCombination("Shortcut+S"));
        saveItem.setOnAction(e -> saveFile());
        saveItem.disableProperty().bind(workspaceController.documentLoadedProperty().not().or(workspaceController.isModifiedProperty().not()));

        MenuItem saveAsItem = new MenuItem("Save As...");
        saveAsItem.setOnAction(e -> saveFileAs());
        saveAsItem.disableProperty().bind(workspaceController.documentLoadedProperty().not());

        MenuItem closeItem = new MenuItem("Close File");
        closeItem.setAccelerator(KeyCombination.keyCombination("Shortcut+W"));
        closeItem.setOnAction(e -> closeFile());
        closeItem.disableProperty().bind(workspaceController.documentLoadedProperty().not());

        // Import / Export Submenu
        Menu importExportMenu = new Menu("Import / Export");

        MenuItem importHtmlItem = new MenuItem("Import HTML...");
        importHtmlItem.setOnAction(e -> importHtml());

        MenuItem exportHtmlItem = new MenuItem("Export as HTML...");
        exportHtmlItem.setOnAction(e -> exportAsHtml());
        exportHtmlItem.disableProperty().bind(workspaceController.documentLoadedProperty().not());

        importExportMenu.getItems().addAll(importHtmlItem, exportHtmlItem);

        MenuItem printItem = new MenuItem("Print...");
        printItem.setAccelerator(KeyCombination.keyCombination("Shortcut+P"));
        printItem.setOnAction(e -> printDocument());
        printItem.disableProperty().bind(workspaceController.documentLoadedProperty().not());

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> System.exit(0));

        fileMenu.getItems().addAll(openItem, saveItem, saveAsItem, closeItem, importExportMenu, printItem, exitItem);

        Menu editMenu = new Menu("Edit");
        MenuItem editDocumentItem = new MenuItem("Edit Document...");
        editDocumentItem.setAccelerator(KeyCombination.keyCombination("Shortcut+E"));
        editDocumentItem.setOnAction(e -> showEditModal());
        editDocumentItem.disableProperty().bind(workspaceController.documentLoadedProperty().not());

        MenuItem findItem = new MenuItem("Find...");
        findItem.setAccelerator(KeyCombination.keyCombination("Shortcut+F"));
        findItem.setOnAction(e -> readerView.showSearch());
        findItem.disableProperty().bind(workspaceController.documentLoadedProperty().not());
        
        editMenu.getItems().addAll(editDocumentItem, findItem);

        Menu viewMenu = new Menu("View");
        MenuItem toggleSidebarItem = new MenuItem("Toggle Sidebar");
        toggleSidebarItem.setAccelerator(KeyCombination.keyCombination("Shortcut+B"));
        toggleSidebarItem.setOnAction(e -> toggleSidebar());

        MenuItem toggleThemeItem = new MenuItem("Toggle Dark Mode");
        toggleThemeItem.setAccelerator(KeyCombination.keyCombination("Shortcut+T"));
        toggleThemeItem.setOnAction(e -> toggleTheme());

        viewMenu.getItems().addAll(toggleSidebarItem, toggleThemeItem);

        menuBar.getMenus().addAll(fileMenu, editMenu, viewMenu);
        return menuBar;
    }

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

    private void showErrorAlertFromBackground(Throwable t) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Operation Failed");
            alert.setHeaderText("An error occurred during file operation.");
            alert.setContentText(t.getMessage());
            alert.initOwner(this.getScene().getWindow());
            alert.showAndWait();
        });
    }
}
