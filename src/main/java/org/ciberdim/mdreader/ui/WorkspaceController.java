package org.ciberdim.mdreader.ui;

import org.ciberdim.mdreader.model.MarkdownDocument;
import org.ciberdim.mdreader.model.RecentFilesManager;
import org.ciberdim.mdreader.parser.MarkdownParser;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Acts as the ViewModel/Controller for the main workspace, decoupling
 * business logic, asynchronous file operations, and application state
 * from the JavaFX UI layouts.
 */
public class WorkspaceController {
    private static final Logger logger = LoggerFactory.getLogger(WorkspaceController.class);

    private final MarkdownParser markdownParser;
    private final RecentFilesManager recentFilesManager;
    private final FlexmarkHtmlConverter htmlConverter;
    private final ExecutorService executor;

    private final ObjectProperty<MarkdownDocument> activeDocument = new SimpleObjectProperty<>(null);
    private final BooleanProperty documentLoaded = new SimpleBooleanProperty(false);
    private final BooleanProperty isModified = new SimpleBooleanProperty(false);
    private final StringProperty windowTitle = new SimpleStringProperty("Markdown Reader");

    public WorkspaceController() {
        this.markdownParser = new MarkdownParser();
        this.recentFilesManager = new RecentFilesManager();
        this.htmlConverter = FlexmarkHtmlConverter.builder().build();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Workspace-IO-Thread");
            t.setDaemon(true);
            return t;
        });

        activeDocument.addListener((obs, oldVal, newVal) -> {
            documentLoaded.set(newVal != null);
            updateWindowTitle();
        });
        isModified.addListener((obs, oldVal, newVal) -> updateWindowTitle());
    }

    public RecentFilesManager getRecentFilesManager() {
        return recentFilesManager;
    }

    public ObjectProperty<MarkdownDocument> activeDocumentProperty() { return activeDocument; }
    public BooleanProperty documentLoadedProperty() { return documentLoaded; }
    public BooleanProperty isModifiedProperty() { return isModified; }
    public StringProperty windowTitleProperty() { return windowTitle; }

    private void updateWindowTitle() {
        MarkdownDocument doc = activeDocument.get();
        if (doc == null) {
            windowTitle.set("Markdown Reader");
        } else {
            String name = doc.getFile() == null ? "Imported HTML" : doc.getFile().getName();
            windowTitle.set("Markdown Reader - " + name + (isModified.get() ? "*" : ""));
        }
    }

    public void openFile(File file, Runnable onSuccess, java.util.function.Consumer<Throwable> onError) {
        if (file == null || !file.exists()) {
            if (onError != null) onError.accept(new IOException("File not found: " + (file != null ? file.getName() : "null")));
            return;
        }

        Task<MarkdownDocument> task = new Task<>() {
            @Override
            protected MarkdownDocument call() throws Exception {
                return markdownParser.parse(file);
            }
        };

        task.setOnSucceeded(e -> {
            recentFilesManager.addFile(file);
            activeDocument.set(task.getValue());
            isModified.set(false);
            if (onSuccess != null) onSuccess.run();
        });

        task.setOnFailed(e -> {
            logger.error("Failed to open file {}", file.getName(), task.getException());
            if (onError != null) onError.accept(task.getException());
        });

        executor.submit(task);
    }

    public void importHtml(File file, Runnable onSuccess, java.util.function.Consumer<Throwable> onError) {
        Task<MarkdownDocument> task = new Task<>() {
            @Override
            protected MarkdownDocument call() throws Exception {
                String htmlContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                String markdownContent = htmlConverter.convert(htmlContent);
                return markdownParser.parseRaw(markdownContent, null);
            }
        };

        task.setOnSucceeded(e -> {
            activeDocument.set(task.getValue());
            isModified.set(true); // Imported content needs to be saved
            if (onSuccess != null) onSuccess.run();
        });

        task.setOnFailed(e -> {
            logger.error("Failed to import HTML {}", file.getName(), task.getException());
            if (onError != null) onError.accept(task.getException());
        });

        executor.submit(task);
    }

    public void updateDocumentContent(String newContent, Runnable onSuccess, java.util.function.Consumer<Throwable> onError) {
        MarkdownDocument currentDoc = activeDocument.get();
        if (currentDoc == null) return;
        
        File currentFile = currentDoc.getFile();

        Task<MarkdownDocument> task = new Task<>() {
            @Override
            protected MarkdownDocument call() throws Exception {
                return markdownParser.parseRaw(newContent, currentFile);
            }
        };

        task.setOnSucceeded(e -> {
            activeDocument.set(task.getValue());
            isModified.set(true);
            if (onSuccess != null) onSuccess.run();
        });

        task.setOnFailed(e -> {
            logger.error("Failed to update document content", task.getException());
            if (onError != null) onError.accept(task.getException());
        });

        executor.submit(task);
    }

    public void saveFile(File file, Runnable onSuccess, java.util.function.Consumer<Throwable> onError) {
        MarkdownDocument doc = activeDocument.get();
        if (doc == null) return;

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Files.writeString(file.toPath(), doc.getRawContent(), StandardCharsets.UTF_8);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            Platform.runLater(() -> openFile(file, onSuccess, onError));
        });

        task.setOnFailed(e -> {
            logger.error("Failed to save file {}", file.getName(), task.getException());
            if (onError != null) onError.accept(task.getException());
        });

        executor.submit(task);
    }

    public void exportHtml(File file, Runnable onSuccess, java.util.function.Consumer<Throwable> onError) {
        MarkdownDocument doc = activeDocument.get();
        if (doc == null) return;

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Files.writeString(file.toPath(), doc.getHtmlContent(), StandardCharsets.UTF_8);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            if (onSuccess != null) onSuccess.run();
        });

        task.setOnFailed(e -> {
            logger.error("Failed to export HTML {}", file.getName(), task.getException());
            if (onError != null) onError.accept(task.getException());
        });

        executor.submit(task);
    }

    public void closeFile() {
        activeDocument.set(null);
        isModified.set(false);
    }
}
