package org.col.common.io;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.common.base.Charsets;

/**
 * Common routines to access classpath resources.
 * If a character encoding is need UTF8 is expected!
 */
public class Resources {
  private final static Pattern TAB_PAT = Pattern.compile("\t");
  
  /**
   * @return stream of lines from a classpath resource file encoded in UTF8
   */
  public static InputStream stream(String resourceName) {
    return ClassLoader.getSystemResourceAsStream(resourceName);
  }
  
  /**
   * @return stream of lines from a classpath resource file encoded in UTF8
   */
  public static BufferedReader reader(String resourceName) {
    return new BufferedReader(new InputStreamReader(stream(resourceName), Charsets.UTF_8));
  }
  
  /**
   * @return stream of lines from a classpath resource file encoded in UTF8
   */
  public static Stream<String> lines(String resourceName) {
    return reader(resourceName).lines();
  }
  
  public static Stream<String[]> tabRows(String resourceName) {
    return lines(resourceName).map(TAB_PAT::split);
  }
}
