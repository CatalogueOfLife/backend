package life.catalogue.common.io;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;

import com.google.common.base.Charsets;

/**
 * Common routines to access classpath resources via the system class loader.
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

  public static URI uri(String resourceName) {
    try {
      return ClassLoader.getSystemResource(resourceName).toURI();
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static boolean exists(String resourceName) {
    URL url = ClassLoader.getSystemResource(resourceName);
    return url != null;
  }
  
  /**
   * @return single string of the entire classpath resource encoded in UTF8
   */
  public static String toString(String resourceName) throws IOException {
    return UTF8IoUtils.readString(stream(resourceName));
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
  
  /**
   * Intended for tests only!!!
   * This relies on resources being available as files.
   * Don't use this in a real webapplication with containers!
   *
   * @param resourceName the resource name for the system classloader
   */
  public static File toFile(String resourceName) {
    return new File(ClassLoader.getSystemResource(resourceName).getFile());
  }
  
  /**
   * Copies a classpath resource to a tmp file
   * @return newly copied tmp file
   */
  public static File tmpCopy(String resourceName) throws IOException {
    File f = File.createTempFile("rescopy", "");
    OutputStream out = new FileOutputStream(f);
    IOUtils.copy(stream(resourceName), out);
    out.close();
    return f;
  }
  
}
