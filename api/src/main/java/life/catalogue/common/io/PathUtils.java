package life.catalogue.common.io;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;

import com.google.common.io.Resources;

/**
 *
 */
public class PathUtils {
  
  public static String getFileExtension(Path p) {
    String name = p.toString();
    return name.substring(name.lastIndexOf(".") + 1).toLowerCase();
  }
  
  public static String getBasename(Path p) {
    return FilenameUtils.getBaseName(p.toString());
  }
  
  public static String getFilename(Path p) {
    return FilenameUtils.getName(p.toString());
  }

  public static Iterable<Path> listFiles(Path folder, final Set<String> allowedSuffices) throws IOException {
    if (folder == null || !Files.isDirectory(folder)) return Collections.emptyList();
    return Files.newDirectoryStream(folder, new DirectoryStream.Filter<Path>() {
      @Override
      public boolean accept(Path p) throws IOException {
        return Files.isRegularFile(p) && (allowedSuffices == null || allowedSuffices.contains(getFileExtension(p)));
      }
    });
  }

  public static void removeFileAndParentsIfEmpty(Path p) throws IOException {
    if (p == null) return;
    
    if (Files.isRegularFile(p)) {
      Files.deleteIfExists(p);
    } else if (Files.isDirectory(p)) {
      try {
        Files.delete(p);
      } catch (DirectoryNotEmptyException e) {
        return;
      }
    }
    removeFileAndParentsIfEmpty(p.getParent());
  }
  
  /**
   * Quietly deletes a file, returning true if successful
   */
  public static boolean deleteQuietly(Path p) {
    try {
      Files.delete(p);
      return true;
    } catch (IOException e) {
      return false;
    }
  }
  
  /**
   * Recursively delete a directory including the given root dir.
   * Does not following symbolic links.
   */
  public static void deleteRecursively(Path dir) throws IOException {
    if (Files.exists(dir)) {
      Files.walk(dir)
          .sorted(Comparator.reverseOrder())
          .forEach(PathUtils::deleteQuietly);
    }
  }
  
  /**
   * Recursively delete all content of a directory but keeps the given root dir.
   * Does not following symbolic links.
   */
  public static void cleanDirectory(Path dir) throws IOException {
    if (Files.exists(dir)) {
      Files.walk(dir)
          .sorted(Comparator.reverseOrder())
          .filter(p -> !p.equals(dir))
          .forEach(PathUtils::deleteQuietly);
    }
  }
  
  /**
   * Creates a new symlink to a given file/folder, deleting any previously existing link that might exist.
   * @param link the symlink file to create
   * @param target  the real file to link to
   */
  public static void symlink(File link, File target) throws IOException {
    symlink(link.toPath(), target.toPath());
  }

  /**
   * Creates a new symlink to a given file/folder, deleting any previously existing link that might exist.
   * @param link the symlink file to create
   * @param target  the real file to link to
   */
  public static void symlink(Path link, Path target) throws IOException {
    if (Files.isSymbolicLink(link)) {
      Files.delete(link);
    }
    Files.createSymbolicLink(link, target);
  }
}
