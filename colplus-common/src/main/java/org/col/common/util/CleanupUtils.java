package org.col.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class CleanupUtils {
  private static final Logger LOG = LoggerFactory.getLogger(CleanupUtils.class);

  public static void registerCleanupHook(final File f) {
    Runtime.getRuntime().addShutdownHook(new Thread() {

      public void run() {
        if (f.exists()) {
          LOG.debug("Deleting file {}", f.getAbsolutePath());
          removeQuietly(f.toPath());
        }
      }
    });
  }

  /**
   * Deletes a file, never throwing an exception. If file is a directory, delete it and all sub-directories.
   *
   * @param file file or directory to delete, can be {@code null}
   * @return {@code true} if the file or directory was deleted, otherwise
   * {@code false}
   *
   * @since 1.4
   */
  public static boolean removeQuietly(Path file) {
    if (file == null) return false;
    try {
      Files.walkFileTree(file, new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
          }
        });
      return true;

    } catch (IOException e) {
      LOG.warn("Failed to delete {}", file, e);
      return false;
    }
  }


}
