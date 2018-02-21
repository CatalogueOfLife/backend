package org.col.util.io;

import org.apache.commons.io.FilenameUtils;

import java.nio.file.Path;

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
}
