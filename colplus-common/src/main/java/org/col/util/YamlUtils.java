package org.col.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 *
 */
public class YamlUtils {
  private static final ObjectMapper OM = new ObjectMapper(new YAMLFactory());

  /**
   * Deserializes an object from a yaml resource located by the system classloader.
   */
  public static <T> T read(Class<T> objClass, String resourceFile) throws IOException {
    InputStream in = System.class.getResourceAsStream(resourceFile);
    return OM.readValue(in, objClass);
  }
}
