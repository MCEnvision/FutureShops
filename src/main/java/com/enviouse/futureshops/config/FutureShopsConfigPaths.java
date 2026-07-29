package com.enviouse.futureshops.config;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public final class FutureShopsConfigPaths {
    public static final String DIRECTORY_NAME = "futureshops";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MIGRATION_BACKUP_DIRECTORY = "migration-backups";
    private static final Pattern CONFIG_FILE_NAME =
            Pattern.compile("futureshops-[a-z0-9][a-z0-9-]*\\.toml");
    private static final Pattern LEGACY_CONFIG_FILE_NAME =
            Pattern.compile("futureshops-[a-z0-9][a-z0-9-]*(?:\\.toml(?:\\.bak)?|-[1-9][0-9]*\\.toml\\.bak)");

    private FutureShopsConfigPaths() {
    }

    public static String registeredFile(String fileName) {
        if (fileName == null || !CONFIG_FILE_NAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException("FutureShops config file name is invalid");
        }
        return DIRECTORY_NAME + "/" + fileName;
    }

    public static void prepareAndMigrateLegacyFiles() {
        prepareAndMigrateLegacyFiles(FMLPaths.CONFIGDIR.get());
    }

    static void prepareAndMigrateLegacyFiles(Path configDirectory) {
        Path root = configDirectory.toAbsolutePath().normalize();
        Path destination = root.resolve(DIRECTORY_NAME);
        try {
            Files.createDirectories(root);
            requireDirectory(root, "Configuration root");
            prepareDirectory(destination, "FutureShops configuration directory");

            for (Path source : legacyConfigFiles(root)) {
                migrate(source, destination);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to prepare the FutureShops configuration directory", exception);
        }
    }

    private static List<Path> legacyConfigFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (LEGACY_CONFIG_FILE_NAME.matcher(name).matches()) {
                    if (Files.isSymbolicLink(entry)
                            || !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException(
                                "Unsafe legacy FutureShops configuration path " + entry);
                    }
                    files.add(entry);
                }
            }
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return files;
    }

    private static void migrate(Path source, Path destination) throws IOException {
        Path target = destination.resolve(source.getFileName().toString());
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            move(source, target);
            LOGGER.info("Migrated FutureShops configuration {} to {}",
                    source.getFileName(), target);
            return;
        }

        requireRegularFile(target, "Existing FutureShops configuration");
        Path backupDirectory = destination.resolve(MIGRATION_BACKUP_DIRECTORY);
        prepareDirectory(backupDirectory, "FutureShops migration backup directory");
        Path backup = uniqueBackupPath(backupDirectory, source.getFileName().toString());
        move(source, backup);
        LOGGER.warn(
                "Kept existing FutureShops configuration {} and archived conflicting legacy file as {}",
                target, backup);
    }

    private static Path uniqueBackupPath(Path backupDirectory, String sourceFileName)
            throws IOException {
        Path candidate = backupDirectory.resolve(sourceFileName + ".legacy");
        int suffix = 1;
        while (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(candidate)
                    || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException(
                        "Unsafe FutureShops migration backup path " + candidate);
            }
            candidate = backupDirectory.resolve(
                    sourceFileName + ".legacy." + suffix);
            suffix++;
        }
        return candidate;
    }

    private static void prepareDirectory(Path directory, String description)
            throws IOException {
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            requireDirectory(directory, description);
            return;
        }
        Files.createDirectories(directory);
        requireDirectory(directory, description);
    }

    private static void requireDirectory(Path directory, String description)
            throws IOException {
        if (Files.isSymbolicLink(directory)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(description + " is not a safe directory " + directory);
        }
    }

    private static void requireRegularFile(Path file, String description)
            throws IOException {
        if (Files.isSymbolicLink(file)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(description + " is not a safe file " + file);
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }
}
