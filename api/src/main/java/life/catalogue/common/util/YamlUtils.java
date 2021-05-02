package life.catalogue.common.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 *
 */
public class YamlUtils {
  private static final YAMLMapper MAPPER = new YAMLMapper();

  static {
    MAPPER.disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID);
    MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  /**
   * Deserializes an object from a yaml input stream.
   */
  public static <T> T read(Class<T> objClass, InputStream configStream) throws IOException {
    return MAPPER.readValue(configStream, objClass);
  }

  /**
   * Deserializes an object from a yaml resource located by the system classloader.
   */
  public static <T> T read(Class<T> objClass, String resourceFile) throws IOException {
    return read(objClass, YamlUtils.class.getResourceAsStream(resourceFile));
  }

  /**
   * Deserializes an object from a yaml resource located by the system classloader.
   */
  public static <T> T read(Class<T> objClass, File configFile) throws IOException {
    return read(objClass, new FileInputStream(configFile));
  }

  /**
   * Serializes an object to a given yaml file
   */
  public static <T> void write(T obj, File file) throws IOException {
    MAPPER.writeValue(file, obj);
  }
}
