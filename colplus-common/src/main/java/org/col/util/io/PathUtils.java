package org.col.util.io;

import com.google.common.io.Resources;
import org.apache.commons.io.FilenameUtils;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

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
