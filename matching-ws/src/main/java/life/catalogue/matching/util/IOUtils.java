package life.catalogue.matching.util;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to read files and streams into various data structures.
 */
@Slf4j
public class IOUtils {

  public static final Pattern TAB_DELIMITED = Pattern.compile("\t");

  public static InputStream classpathStream(String path) {
    InputStream in = null;
    // relative path. Use classpath instead
    URL url = IOUtils.class.getClassLoader().getResource(path);
    if (url != null) {
      try {
        in = url.openStream();
      } catch (IOException e) {
        log.warn("Cant open classpath input stream " + path, e);
      }
    }
    return in;
  }

  public static Map<String, String> streamToMap(InputStream source) {
    LineIterator lines = getLineIterator(source);
    Map<String, String> result = new HashMap<>();
    while (lines.hasNext()) {
      String line = lines.nextLine();
      // ignore comments
      if (!ignore(line)) {
        String[] parts = TAB_DELIMITED.split(line);
        if (parts.length >= 2) {
          result.put(StringUtils.trimToNull(parts[0]), StringUtils.trimToNull(parts[1]));
        }
      }
    }
    return result;
  }

  private static boolean ignore(String line) {
    return StringUtils.trimToNull(line) == null || line.startsWith("#");
  }

  /** @param source the source input stream encoded in UTF-8 */
  private static LineIterator getLineIterator(InputStream source) {
    return getLineIterator(source, StandardCharsets.UTF_8.name());
  }

  /**
   * @param source the source input stream
   * @param encoding the encoding used by the input stream
   */
  private static LineIterator getLineIterator(InputStream source, String encoding) {
    try {
      return new LineIterator(new BufferedReader(new InputStreamReader(source, encoding)));
    } catch (UnsupportedEncodingException e) {
      throw new IllegalArgumentException("Unsupported encoding" + encoding, e);
    }
  }

  public static Set<String> streamToSet(InputStream source) throws IOException {
    return streamToSet(source, new HashSet<>());
  }

  /**
   * Reads a file and returns a unique set of all lines which are no comments (starting with #) and
   * trims whitespace.
   *
   * @param source the UTF-8 encoded text file to read
   * @param resultSet the set implementation to be used. Will not be cleared before reading!
   * @return set of unique lines
   */
  private static Set<String> streamToSet(InputStream source, Set<String> resultSet) {
    LineIterator lines = getLineIterator(source);
    while (lines.hasNext()) {
      String line = lines.nextLine().trim();
      // ignore comments
      if (!ignore(line)) {
        resultSet.add(line);
      }
    }
    return resultSet;
  }
}
