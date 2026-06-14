package org.ciberdim.mdreader.ui;

import org.ciberdim.mdreader.model.HeadingInfo;
import org.ciberdim.mdreader.model.RecentFilesManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Sidebar component containing the search box, the list of recent files,
 * the active document outline (Table of Contents), and control buttons.
 */
public class SidebarView extends VBox {
    private static final Logger logger = LoggerFactory.getLogger(SidebarView.class);

    private final RecentFilesManager recentFilesManager;
    private final ObservableList<File> recentFilesList;
    private final FilteredList<File> filteredRecentFiles;
    
    private final TextField searchField;
    private final ListView<File> recentListView;
    private final ListView<HeadingInfo> tocListView;
    
    private final Button openFileButton;
    private final Button themeToggleButton;

    private Consumer<File> fileOpener;
    private Runnable themeToggler;
    private Consumer<HeadingInfo> tocNavigator;

    /**
     * Constructs the SidebarView.
     * 
     * @param recentFilesManager the manager for retrieving and saving recent files
     */
    public SidebarView(RecentFilesManager recentFilesManager) {
        this.recentFilesManager = recentFilesManager;
        this.getStyleClass().add("sidebar");
        this.setPadding(new Insets(15));
        this.setSpacing(10);
        this.setPrefWidth(280);
        this.setMinWidth(220);
        this.setMaxWidth(400);

        // 1. Search Bar
        searchField = new TextField();
        searchField.setPromptText("Search recent files...");
        
        // 2. Recent Files Section Header
        Label recentHeader = new Label("RECENT FILES");
        recentHeader.getStyleClass().add("sidebar-header");

        // 3. Recent Files ListView
        recentFilesList = FXCollections.observableArrayList(recentFilesManager.getRecentFiles());
        filteredRecentFiles = new FilteredList<>(recentFilesList, file -> true);
        recentListView = new ListView<>(filteredRecentFiles);
        recentListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        recentListView.setPrefHeight(180);
        recentListView.setMinHeight(100);
        recentListView.setCellFactory(lv -> new RecentFileCell());

        // Wire search filter
        searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            filteredRecentFiles.setPredicate(file -> {
                if (newValue == null || newValue.isBlank()) {
                    return true;
                }
                String lower = newValue.toLowerCase();
                return file.getName().toLowerCase().contains(lower) 
                    || file.getAbsolutePath().toLowerCase().contains(lower);
            });
        });

        // Event: select recent file
        recentListView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                openSelectedRecentFile();
            }
        });
        recentListView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                openSelectedRecentFile();
            }
        });

        // 4. Outline (TOC) Section Header
        Label tocHeader = new Label("DOCUMENT OUTLINE");
        tocHeader.getStyleClass().add("sidebar-header");

        // 5. Outline ListView
        tocListView = new ListView<>();
        tocListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        tocListView.setCellFactory(lv -> new HeadingCell());
        VBox.setVgrow(tocListView, Priority.ALWAYS);

        // Event: select outline item
        tocListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && tocNavigator != null) {
                tocNavigator.accept(newVal);
            }
        });

        // 6. Footer Toolbar
        openFileButton = new Button("Open File...");
        openFileButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(openFileButton, Priority.ALWAYS);
        
        openFileButton.setGraphic(IconFactory.createIcon(IconFactory.PATH_FOLDER, null));
        openFileButton.setOnAction(e -> handleOpenFileChooser());

        themeToggleButton = new Button();
        themeToggleButton.getStyleClass().add("button-secondary");
        themeToggleButton.setPadding(new Insets(8, 10, 8, 10));
        themeToggleButton.setGraphic(IconFactory.createIcon(IconFactory.PATH_SUN, "icon-primary"));
        
        themeToggleButton.setOnAction(e -> {
            if (themeToggler != null) {
                themeToggler.run();
            }
        });

        HBox footerToolbar = new HBox(8, openFileButton, themeToggleButton);
        footerToolbar.setAlignment(Pos.CENTER);

        // Assembly
        this.getChildren().addAll(
            searchField, 
            recentHeader, 
            recentListView, 
            new Separator(),
            tocHeader, 
            tocListView, 
            footerToolbar
        );
    }

    /**
     * Updates the Table of Contents outline view with new headings.
     * 
     * @param headings the new list of HeadingInfo headings
     */
    public void setOutline(List<HeadingInfo> headings) {
        Platform.runLater(() -> tocListView.getItems().setAll(headings));
    }

    /**
     * Refresh the recent files list view from the manager.
     */
    public void refreshRecentFiles() {
        Platform.runLater(() -> recentFilesList.setAll(recentFilesManager.getRecentFiles()));
    }

    /**
     * Sets the theme toggle button graphic based on active theme.
     * 
     * @param isDark true if the current theme is dark, false for light
     */
    public void updateThemeIcon(boolean isDark) {
        String path = isDark ? IconFactory.PATH_SUN : IconFactory.PATH_MOON;
        themeToggleButton.setGraphic(IconFactory.createIcon(path, "icon-primary"));
    }

    public void setOnFileOpener(Consumer<File> fileOpener) {
        this.fileOpener = fileOpener;
    }

    public void setOnThemeToggler(Runnable themeToggler) {
        this.themeToggler = themeToggler;
    }

    public void setOnTocNavigator(Consumer<HeadingInfo> tocNavigator) {
        this.tocNavigator = tocNavigator;
    }

    private void handleOpenFileChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Markdown File");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Markdown Files (*.md, *.markdown)", "*.md", "*.markdown"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File selectedFile = fileChooser.showOpenDialog(this.getScene().getWindow());
        if (selectedFile != null && fileOpener != null) {
            fileOpener.accept(selectedFile);
        }
    }

    private void openSelectedRecentFile() {
        File file = recentListView.getSelectionModel().getSelectedItem();
        if (file != null && fileOpener != null) {
            fileOpener.accept(file);
        }
    }

    private class RecentFileCell extends ListCell<File> {
        private final VBox cellLayout;
        private final Label titleLabel;
        private final Label pathLabel;

        public RecentFileCell() {
            titleLabel = new Label();
            titleLabel.getStyleClass().add("recent-cell-title");

            pathLabel = new Label();
            pathLabel.getStyleClass().add("recent-cell-path");

            cellLayout = new VBox(2, titleLabel, pathLabel);
            cellLayout.setPadding(new Insets(2, 0, 2, 0));

            // Context Menu
            ContextMenu contextMenu = new ContextMenu();
            MenuItem removeMenu = new MenuItem("Remove from History");
            removeMenu.setOnAction(e -> {
                File file = getItem();
                if (file != null) {
                    recentFilesManager.removeFile(file);
                    refreshRecentFiles();
                }
            });

            MenuItem revealMenu = new MenuItem("Reveal in Explorer");
            revealMenu.setOnAction(e -> {
                File file = getItem();
                if (file != null) {
                    try {
                        Desktop.getDesktop().open(file.getParentFile());
                    } catch (IOException ex) {
                        logger.error("Failed to open folder in Explorer", ex);
                    }
                }
            });

            contextMenu.getItems().addAll(revealMenu, removeMenu);
            setContextMenu(contextMenu);
        }

        @Override
        protected void updateItem(File file, boolean empty) {
            super.updateItem(file, empty);
            if (empty || file == null) {
                setText(null);
                setGraphic(null);
            } else {
                titleLabel.setText(file.getName());
                pathLabel.setText(file.getParent());
                setGraphic(cellLayout);
            }
        }
    }

    private static class HeadingCell extends ListCell<HeadingInfo> {
        @Override
        protected void updateItem(HeadingInfo item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll("toc-cell-h1", "toc-cell-h2", "toc-cell-h3");
            
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item.text());
                
                switch (item.level()) {
                    case 1 -> getStyleClass().add("toc-cell-h1");
                    case 2 -> getStyleClass().add("toc-cell-h2");
                    default -> getStyleClass().add("toc-cell-h3");
                }
            }
        }
    }
}
