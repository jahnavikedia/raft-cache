package com.distributed.cache.storage;

import com.distributed.cache.raft.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles persistence of the Raft log to disk.
 *
 * The log is stored as a simple append-only file with one JSON entry per line.
 * This allows for efficient appending and recovery on startup.
 */
public class LogPersistence {
    private static final Logger logger = LoggerFactory.getLogger(LogPersistence.class);

    private final Path logFilePath;
    private final Path dataDir;
    private BufferedWriter writer;

    /**
     * Create a new LogPersistence instance
     *
     * @param nodeId The ID of this node (used to create node-specific data directory)
     */
    public LogPersistence(String nodeId) {
        this.dataDir = Paths.get("data", "node-" + nodeId);
        this.logFilePath = dataDir.resolve("raft.log");

        try {
            // Create data directory if it doesn't exist
            Files.createDirectories(dataDir);
            logger.info("Log persistence initialized for node {} at {}", nodeId, logFilePath);

            // Open writer in append mode
            writer = new BufferedWriter(new FileWriter(logFilePath.toFile(), true));
        } catch (IOException e) {
            logger.error("Failed to initialize log persistence for node {}", nodeId, e);
            throw new RuntimeException("Failed to initialize log persistence", e);
        }
    }

    /**
     * Append a log entry to the persistent log file
     *
     * @param entry The log entry to append
     */
    public synchronized void appendEntry(LogEntry entry) {
        try {
            String json = entry.toJson();
            writer.write(json);
            writer.newLine();
            writer.flush(); // Ensure entry is written to disk immediately
            logger.debug("Appended log entry to disk: index={}, term={}", entry.getIndex(), entry.getTerm());
        } catch (IOException e) {
            logger.error("Failed to append log entry to disk: {}", entry, e);
            throw new RuntimeException("Failed to append log entry", e);
        }
    }

    /**
     * Load all log entries from disk
     *
     * @return List of all log entries, in order
     */
    public synchronized List<LogEntry> loadLog() {
        List<LogEntry> entries = new ArrayList<>();

        if (!Files.exists(logFilePath)) {
            logger.info("No existing log file found at {}", logFilePath);
            return entries;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(logFilePath.toFile()))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                try {
                    LogEntry entry = LogEntry.fromJson(line);
                    entries.add(entry);
                } catch (Exception e) {
                    logger.error("Failed to parse log entry at line {}: {}", lineNumber, line, e);
                    // Continue loading other entries
                }
            }
            logger.info("Loaded {} log entries from disk", entries.size());
        } catch (IOException e) {
            logger.error("Failed to load log from disk", e);
            throw new RuntimeException("Failed to load log", e);
        }

        return entries;
    }

    /**
     * Truncate the log file, keeping only entries before the given index
     * This is used when resolving log conflicts (follower has conflicting entries)
     *
     * @param fromIndex Delete all entries from this index onwards (inclusive)
     */
    public synchronized void truncate(long fromIndex) {
        logger.info("Truncating log from index {}", fromIndex);

        try {
            // Close current writer
            if (writer != null) {
                writer.close();
            }

            // Load all entries
            List<LogEntry> allEntries = loadLog();

            // Create temporary file
            Path tempFile = dataDir.resolve("raft.log.tmp");

            // Write entries before fromIndex to temp file
            try (BufferedWriter tempWriter = new BufferedWriter(new FileWriter(tempFile.toFile()))) {
                int kept = 0;
                for (LogEntry entry : allEntries) {
                    if (entry.getIndex() < fromIndex) {
                        tempWriter.write(entry.toJson());
                        tempWriter.newLine();
                        kept++;
                    }
                }
                tempWriter.flush();
                logger.info("Truncated log: kept {} entries, removed entries from index {}", kept, fromIndex);
            }

            // Replace original file with temp file
            Files.move(tempFile, logFilePath, StandardCopyOption.REPLACE_EXISTING);

            // Reopen writer in append mode
            writer = new BufferedWriter(new FileWriter(logFilePath.toFile(), true));

        } catch (IOException e) {
            logger.error("Failed to truncate log from index {}", fromIndex, e);
            throw new RuntimeException("Failed to truncate log", e);
        }
    }

    /**
     * Close the log file writer
     */
    public synchronized void close() {
        if (writer != null) {
            try {
                writer.close();
                logger.info("Closed log file");
            } catch (IOException e) {
                logger.error("Failed to close log file", e);
            }
        }
    }
}
