package life.catalogue.common.io;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

import com.google.common.collect.Lists;

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

  /**
   * Lists all regular files of a folder, optionally filtered by their suffix.
   * The directory handle is released before returning, so the result can be iterated freely.
   * For very large directories prefer {@link #streamFiles(Path, Set)} which stays lazy.
   *
   * @return the matching files, empty if the folder is null or does not exist
   */
  public static List<Path> listFiles(Path folder, @Nullable final Set<String> allowedSuffices) throws IOException {
    if (folder == null || !Files.isDirectory(folder)) return Collections.emptyList();
    try (DirectoryStream<Path> dir = Files.newDirectoryStream(folder, filter(allowedSuffices))) {
      return Lists.newArrayList(dir);
    }
  }

  /**
   * Lazily streams all regular files of a folder, optionally filtered by their suffix.
   * The stream holds an open directory handle, so callers MUST close it, ideally with try-with-resources.
   *
   * @return the matching files, empty if the folder is null or does not exist
   */
  public static Stream<Path> streamFiles(Path folder, @Nullable final Set<String> allowedSuffices) throws IOException {
    if (folder == null || !Files.isDirectory(folder)) return Stream.empty();
    DirectoryStream<Path> dir = Files.newDirectoryStream(folder, filter(allowedSuffices));
    return StreamSupport.stream(dir.spliterator(), false).onClose(() -> {
      try {
        dir.close();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
  }

  private static DirectoryStream.Filter<Path> filter(@Nullable final Set<String> allowedSuffices) {
    return p -> Files.isRegularFile(p) && (allowedSuffices == null || allowedSuffices.contains(getFileExtension(p)));
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
     try (var paths = Files.walk(dir)) {
       paths.sorted(Comparator.reverseOrder())
            .forEach(PathUtils::deleteQuietly);
     }
    }
  }
  
  /**
   * Recursively delete all content of a directory but keeps the given root dir.
   * Does not following symbolic links.
   */
  public static void cleanDirectory(Path dir) throws IOException {
    if (Files.exists(dir)) {
      try (var paths = Files.walk(dir)) {
        paths
          .sorted(Comparator.reverseOrder())
          .filter(p -> !p.equals(dir))
          .forEach(PathUtils::deleteQuietly);
      }
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

  public static void setPermission777(Path path) throws IOException{
    Set<PosixFilePermission> perms = new HashSet<>();
    perms.add(PosixFilePermission.OWNER_READ);
    perms.add(PosixFilePermission.OWNER_WRITE);
    perms.add(PosixFilePermission.OWNER_EXECUTE);

    perms.add(PosixFilePermission.OTHERS_READ);
    perms.add(PosixFilePermission.OTHERS_WRITE);
    perms.add(PosixFilePermission.OTHERS_EXECUTE);

    perms.add(PosixFilePermission.GROUP_READ);
    perms.add(PosixFilePermission.GROUP_WRITE);
    perms.add(PosixFilePermission.GROUP_EXECUTE);

    Files.setPosixFilePermissions(path, perms);
  }

  public static void printDir(Path dir) {
    File dirFile = dir.toFile();
    for (File f : org.apache.commons.io.FileUtils.listFilesAndDirs(dirFile, TrueFileFilter.TRUE, TrueFileFilter.TRUE)) {
      if (!f.equals(dirFile)) {
        System.out.println(f.getAbsolutePath());
      }
    }
  }
}
