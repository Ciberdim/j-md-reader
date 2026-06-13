package org.ciberdim.mdreader.ui;

import org.ciberdim.mdreader.model.HeadingInfo;
import org.ciberdim.mdreader.model.MarkdownDocument;
import org.ciberdim.mdreader.model.RecentFilesManager;
import org.ciberdim.mdreader.parser.MarkdownParser;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.util.List;

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
            windowTitle.set("Markdown Reader - " + file.getName());
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
        windowTitle.set("Markdown Reader");
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

        MenuItem closeItem = new MenuItem("Close File");
        closeItem.setAccelerator(KeyCombination.keyCombination("Shortcut+W"));
        closeItem.setOnAction(e -> closeFile());

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> System.exit(0));

        fileMenu.getItems().addAll(openItem, closeItem, exitItem);

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
