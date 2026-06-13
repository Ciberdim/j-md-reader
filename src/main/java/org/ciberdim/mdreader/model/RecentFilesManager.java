package org.ciberdim.mdreader.model;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the history of the last 10 opened Markdown files.
 * Handles persistence to a local file in the user's home directory.
 */
public class RecentFilesManager {
    private static final String APP_DIR_NAME = ".j-md-reader";
    private static final String RECENT_FILES_FILE_NAME = "recent-files.txt";
    private static final int MAX_RECENT_FILES = 10;

    private final List<File> recentFiles = new ArrayList<>();
    private final File storageFile;

    /**
     * Constructs a new RecentFilesManager and loads the history from disk.
     */
    public RecentFilesManager() {
        String userHome = System.getProperty("user.home");
        File appDir = new File(userHome, APP_DIR_NAME);
        if (!appDir.exists()) {
            appDir.mkdirs();
        }
        this.storageFile = new File(appDir, RECENT_FILES_FILE_NAME);
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
        if (!storageFile.exists()) {
            return;
        }

        recentFiles.clear();
        try (BufferedReader reader = new BufferedReader(new FileReader(storageFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    File file = new File(trimmed);
                    if (file.exists() && !recentFiles.contains(file)) {
                        recentFiles.add(file);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load recent files: " + e.getMessage());
        }
    }

    /**
     * Saves the current list of recent files to the storage file on disk.
     */
    private void save() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(storageFile))) {
            for (File file : recentFiles) {
                writer.write(file.getAbsolutePath());
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Failed to save recent files: " + e.getMessage());
        }
    }
}
