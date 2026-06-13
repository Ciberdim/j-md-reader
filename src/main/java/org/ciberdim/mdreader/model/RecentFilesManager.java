package org.ciberdim.mdreader.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the history of the last 10 opened Markdown files.
 * Handles persistence to a local file in the user's home directory.
 */
public class RecentFilesManager {
    private static final Logger logger = LoggerFactory.getLogger(RecentFilesManager.class);
    private static final String APP_DIR_NAME = ".j-md-reader";
    private static final String RECENT_FILES_FILE_NAME = "recent-files.txt";
    private static final int MAX_RECENT_FILES = 10;

    private final List<File> recentFiles = new ArrayList<>();
    private final Path storageFile;

    /**
     * Constructs a new RecentFilesManager and loads the history from disk.
     */
    public RecentFilesManager() {
        String userHome = System.getProperty("user.home");
        File appDir = new File(userHome, APP_DIR_NAME);
        if (!appDir.exists()) {
            appDir.mkdirs();
        }
        this.storageFile = new File(appDir, RECENT_FILES_FILE_NAME).toPath();
        load();
    }

    /**
     * Returns a copy of the current list of recent files.
     * 
     * @return a list of File objects representing recent files
     */
    public synchronized List<File> getRecentFiles() {
        return new ArrayList<>(recentFiles);
    }

    /**
     * Adds a file to the recent history. If the file is already in the list,
     * it is moved to the top. The list is capped at 10 items.
     * 
     * @param file the File to add
     */
    public synchronized void addFile(File file) {
        if (file == null) {
            return;
        }
        
        File absoluteFile = file.getAbsoluteFile();
        recentFiles.remove(absoluteFile);
        recentFiles.add(0, absoluteFile);

        if (recentFiles.size() > MAX_RECENT_FILES) {
            recentFiles.subList(MAX_RECENT_FILES, recentFiles.size()).clear();
        }

        save();
    }

    /**
     * Removes a file from the recent history.
     * 
     * @param file the File to remove
     */
    public synchronized void removeFile(File file) {
        if (file == null) {
            return;
        }
        
        File absoluteFile = file.getAbsoluteFile();
        if (recentFiles.remove(absoluteFile)) {
            save();
        }
    }

    /**
     * Loads the recent files list from the storage file on disk.
     * Only adds files that still exist on the filesystem.
     */
    private void load() {
        if (!Files.exists(storageFile)) {
            return;
        }

        recentFiles.clear();
        try {
            List<String> lines = Files.readAllLines(storageFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    File file = new File(trimmed);
                    if (file.exists() && !recentFiles.contains(file)) {
                        recentFiles.add(file);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Failed to load recent files", e);
        }
    }

    /**
     * Saves the current list of recent files to the storage file on disk.
     */
    private void save() {
        try {
            List<String> lines = new ArrayList<>();
            for (File file : recentFiles) {
                lines.add(file.getAbsolutePath());
            }
            Files.write(storageFile, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to save recent files", e);
        }
    }
}
