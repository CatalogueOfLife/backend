package org.col.common.io;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.common.io.Resources;
import org.apache.commons.io.FilenameUtils;

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

  public static void removeFileAndParentsIfEmpty(Path p) throws IOException {
    if (p == null) return;

    if (Files.isRegularFile(p)) {
      Files.deleteIfExists(p);
    } else if(Files.isDirectory(p)) {
      try {
        Files.delete(p);
      } catch(DirectoryNotEmptyException e) {
        return;
      }
    }
    removeFileAndParentsIfEmpty(p.getParent());
  }

  /**
   * Read a classpath resource at test time as a Path.
   * Not that this requires actual files and does NOT work with classpath resources from jar files!
   */
  public static Path classPathTestRes(String resource) {
    URL url = Resources.getResource(resource);
    try {
      return Paths.get(url.toURI());
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}
