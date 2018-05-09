package org.col.common.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 *
 */
public class YamlUtils {
  private static final ObjectMapper OM = new ObjectMapper(new YAMLFactory());
  static {
    OM.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    OM.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  /**
   * Deserializes an object from a yaml resource located by the system classloader.
   */
  public static <T> T read(Class<T> objClass, String resourceFile) throws IOException {
    InputStream in = System.class.getResourceAsStream(resourceFile);
    return OM.readValue(in, objClass);
  }

  /**
   * Serializes an object to a given yaml file
   */
  public static <T> void write(T obj, File file) throws IOException {
    OM.writeValue(file, obj);
  }
}
