package org.col.common.io;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.common.base.Charsets;

public class Resources {
  private final static Pattern TAB_PAT = Pattern.compile("\t");

  /**
   * @return stream of lines from a classpath resource file encoded in UTF8
   */
  public static Stream<String> lines(String resourceName) {
     return new BufferedReader(new InputStreamReader(
         ClassLoader.getSystemResourceAsStream(resourceName), Charsets.UTF_8
     )).lines();
  }

  public static Stream<String[]> tabRows(String resourceName) {
    return lines(resourceName).map(TAB_PAT::split);
  }
}
